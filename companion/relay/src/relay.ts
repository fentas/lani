// The relay (plan 5, phase D; docs/plans/05-built/relay.md): towns and family through one small service, for a town
// with no node (the phone's bridge) and a self-hosted town that sits in a private tailnet. Identity is a town's own key,
// with no accounts: every write is signed with it (sign.ts).
//
// - A town's public state: the town publishes its last one (PUT /v1/towns/{id}/public) and says who may read it (its
//   linked towns, PUT …/readers); a reader fetches it with the town's own signature of it (GET …/public).
// - Mailboxes: a request delivered to a town (POST /v1/towns/{id}/mail) waits in its mailbox; the town fetches it (GET
//   /v1/mailbox, a long poll), answers it (POST /v1/mail/{mid}/reply) or acks it (POST /v1/mailbox/ack); the sender gets
//   the answer (GET /v1/mail/{mid}/reply).
// - The tunnel on top: any request to /v1/towns/{id}/town/… is a bridge-to-bridge request for that town (signed by the
//   sender's town key inside, x-lani-town-*, and by its key outside, X-Lani-*): delivered to the town's mailbox, held
//   until the town answers, and answered with the town's own answer. So a bridge reaches a town that is only here by its
//   URL <relay>/v1/towns/{id}, as it reaches any other. GET …/town/hello answers for the town (its key, as it signed).
// - The family page: static at /family/{id}/, its API at /v1/family/{id}/… through the town's mailbox (family.ts).
// - Backups: /v1/backup (backup.ts). Health: /health.
import { randomBytes, timingSafeEqual } from 'node:crypto'
import { backups } from './backup'
import type { Config } from './config'
import { familyPage, familyRoute } from './family'
import { HttpError, RELAY_HEADER, asBody, fail, json, parseJson, readBody } from './http'
import { RateLimiter, Waiters } from './limits'
import { MAX_SKEW_MS, TOWN_ID, checkSigned, sha256hex, type Signed } from './sign'
import { Store, type MailRow, type ReplyRow } from './store'

export const VERSION = 1

/** The headers of a tunnelled request that reach the town: the towns' signatures (today's and from before the rename). */
const TOWN_HEADERS = /^(content-type|x-lani-town-[a-z]+|x-fluent-town-[a-z]+)$/
/** The family page's: its token (the town checks it again) and the body's type. */
const FAMILY_HEADERS = /^(content-type|authorization)$/
/** What of a town's answer goes back to the sender. */
const REPLY_HEADERS = new Set(['content-type', 'x-lani-town-signature', 'x-fluent-town-signature', 'retry-after', 'cache-control', 'content-security-policy', 'x-content-type-options'])
const NULL_BODY = new Set([101, 204, 205, 304])
const MAIL_ID = /^m_[A-Za-z0-9_-]{16,40}$/

const pick = (headers: Headers, allowed: RegExp) => {
  const out: Record<string, string> = {}
  headers.forEach((v, k) => {
    if (allowed.test(k)) out[k] = v
  })
  return out
}

const b64 = (b: Uint8Array) => Buffer.from(b).toString('base64')
const iso = (ms: number) => new Date(ms).toISOString()

export type Relay = {
  /** Answers one request; [ip]: the client's address (for the limits on unsigned requests). */
  fetch: (req: Request, ip?: string) => Promise<Response>
  /** Removes what ran out (the server runs it every minute). */
  sweep: () => ReturnType<Store['sweep']>
  close: () => void
  store: Store
}

export function createRelay(cfg: Config): Relay {
  const store = new Store(cfg.dataDir)
  const now = cfg.now
  const keyLimit = new RateLimiter(cfg.ratePerMin, cfg.rateBurst, now)
  const ipLimit = new RateLimiter(cfg.ipRatePerMin, Math.max(60, cfg.ipRatePerMin / 4), now)
  const familyLimit = new RateLimiter(cfg.familyRatePerMin, Math.max(10, cfg.familyRatePerMin / 2), now)
  const waiters = new Waiters()
  const page = familyPage(cfg.webDir)

  const limited = (wait: number, what: string) => new HttpError(429, 'rate_limited', `too many requests ${what}: try again later`, { 'retry-after': String(Math.ceil(wait / 1000)) })

  /** A signed request's signer, once its signature is fresh, its rate allows it and it wasn't seen before. */
  function admit(signed: Signed) {
    const wait = keyLimit.take(signed.id)
    if (wait) throw limited(wait, 'from this key')
    const until = Math.max(now(), Number(signed.time)) + MAX_SKEW_MS + 60_000
    if (!store.firstTime(`${signed.id}:${sha256hex(signed.message)}`, until)) throw new HttpError(401, 'replayed', 'this request was seen before')
  }

  /** A signed request's body (at most [max] bytes) and signer. */
  async function signedBody(req: Request, url: URL, max: number): Promise<{ signed: Signed; body: Uint8Array }> {
    const body = await readBody(req, max)
    const check = checkSigned(req.headers, { method: req.method, pathWithQuery: url.pathname + url.search, bodySha256: sha256hex(body), now: now() })
    if (!check.ok) throw new HttpError(401, check.code, 'the request is not signed by a town key')
    admit(check.signed)
    return { signed: check.signed, body }
  }

  /** [signed] is town [id] itself (a town's own routes): it is known from now on. */
  function owner(signed: Signed, id: string) {
    if (signed.id !== id) throw new HttpError(403, 'not_yours', 'only the town itself does this')
    store.seeTown(id, signed.publicKey, now())
  }

  const knownTown = (id: string) => store.town(id) ?? (() => { throw new HttpError(404, 'unknown_town', 'no such town on this relay') })()

  /** Whether town [id] is there now: polling its mailbox, or it did lately. */
  const online = (id: string) => waiters.count(`town:${id}`) > 0 || (store.town(id)?.last_poll ?? 0) > now() - cfg.onlineMs

  const away = (detail: string) =>
    fail(504, 'away', `Vas zdaj ni dosegljiva, poskusi pozneje · The town is away right now; try again later (${detail})`, { [RELAY_HEADER]: 'away' })

  // --- mail ------------------------------------------------------------------------------------------------------------

  /**
   * A request for town [town]'s bridge into its mailbox: [scope] "town" (from another town's bridge) or "family" (the
   * family page), kept until [expires]; the town hears of it at once when it polls.
   */
  function deliver(o: { town: string; scope: 'town' | 'family'; sender: string; signed?: Signed; signedPath?: string; method: string; path: string; headers: Record<string, string>; body: Uint8Array; expires: number; async?: boolean }): string {
    const t = now()
    const box = store.mailbox(o.town, t)
    if (box.count >= cfg.mailboxMax || box.bytes + o.body.length > cfg.mailboxBytes) throw new HttpError(507, 'mailbox_full', "the town's mailbox is full: try again later")
    if (store.senderBytes(o.sender, t) + o.body.length > cfg.senderBytes) throw new HttpError(507, 'quota', 'too much of your mail waits: try again later')
    if (store.totalBytes() + o.body.length > cfg.storeMaxBytes) throw new HttpError(507, 'full', 'the relay is full: try again later')
    const id = `m_${randomBytes(16).toString('base64url')}`
    const meta = {
      ...(o.async ? { async: true } : {}),
      method: o.method,
      path: o.path,
      headers: o.headers,
      // the sender's own signature of what it sent the relay: the town can check it (the relay can't make one up)
      sender: o.signed ? { key: o.signed.publicKey, time: o.signed.time, sig: o.signed.sig, path: o.signedPath } : null,
    }
    const row: MailRow = { id, town: o.town, kind: 'http', scope: o.scope, sender: o.sender, at: t, expires: o.expires, leased_until: 0, meta: JSON.stringify(meta), size: o.body.length }
    store.addMail(row, o.body.length ? o.body : null)
    waiters.notify(`town:${o.town}`)
    return id
  }

  /** The answer to message [mid], once there, waiting at most [ms] (or until [signal] aborts). */
  async function awaitReply(mid: string, ms: number, signal?: AbortSignal) {
    const deadline = Date.now() + ms
    for (;;) {
      const r = store.reply(mid)
      if (r) return r
      const left = deadline - Date.now()
      if (left <= 0 || signal?.aborted) return undefined
      await waiters.wait(`reply:${mid}`, left, signal)
    }
  }

  /** A town's answer, as the sender gets it. */
  function replyResponse(r: ReplyRow & { body: Uint8Array | null }, extra: Record<string, string> = {}): Response {
    const headers = new Headers()
    let given: Record<string, unknown> = {}
    try {
      given = JSON.parse(r.headers)
    } catch {}
    for (const [k, v] of Object.entries(given)) if (REPLY_HEADERS.has(k.toLowerCase()) && typeof v === 'string') headers.set(k, v)
    headers.set(RELAY_HEADER, 'tunnel')
    for (const [k, v] of Object.entries(extra)) headers.set(k, v)
    return new Response(NULL_BODY.has(r.status) ? null : asBody(r.body), { status: r.status, headers })
  }

  /** How long a sender waits for a tunnelled answer: what it asks (x-lani-relay-wait, ms) within the relay's limit. */
  function waitOf(req: Request, dflt = cfg.tunnelWaitDefaultMs): number {
    const asked = Number(req.headers.get('x-lani-relay-wait'))
    return Math.min(cfg.tunnelWaitMaxMs, Math.max(500, Number.isFinite(asked) && asked > 0 ? asked : dflt))
  }

  /**
   * Delivers a request and holds the sender until the town answers: its answer as it is; when the town isn't there, a
   * 504 (away) at once; when it doesn't answer in time, the request taken back (504) or, when [queue], kept for when it
   * is back (202: queued).
   */
  async function relayed(req: Request, o: Parameters<typeof deliver>[0] & { wait: number; queue?: boolean }, extra: Record<string, string> = {}): Promise<Response> {
    const queued = (mid: string) => json({ queued: true, code: 'queued', id: mid }, 202, { ...extra, 'x-lani-mail': mid })
    // before delivering: delivering wakes the town's poll, which then isn't waiting any more
    const there = online(o.town)
    if (!there && !o.queue) return away('not connected')
    const mid = deliver(o)
    const r = await awaitReply(mid, there ? o.wait : 0, req.signal)
    if (r) {
      store.dropReply(mid) // delivered: nobody fetches it again
      return replyResponse(r, { ...extra, 'x-lani-mail': mid })
    }
    if (o.queue) return queued(mid)
    if (store.withdraw(mid)) return away('no answer')
    // fetched but not answered in time: the answer is kept for the sender (GET /v1/mail/{mid}/reply)
    return fail(504, 'timeout', 'the town took the request but did not answer in time', { [RELAY_HEADER]: 'away', 'x-lani-mail': mid })
  }

  // --- the routes ----------------------------------------------------------------------------------------------------------

  const backup = backups(cfg, store, admit)

  /** The inner request of a town's tunnelled or delivered request: from the signer, for town [id]. */
  function innerFrom(headers: Headers | Record<string, string>, signed: Signed, id: string) {
    const get = (k: string) => (headers instanceof Headers ? headers.get(k) : headers[k]) ?? undefined
    const from = get('x-lani-town-from') ?? get('x-fluent-town-from')
    const to = get('x-lani-town-to') ?? get('x-fluent-town-to')
    if (from !== signed.id) throw new HttpError(403, 'not_yours', "the request inside isn't the signer's (x-lani-town-from)")
    if (to !== id) throw new HttpError(400, 'misaddressed', "the request inside isn't for this town (x-lani-town-to)")
  }

  /** ANY /v1/towns/{id}/town/…: a bridge-to-bridge request for town [id], through its mailbox. */
  async function tunnel(req: Request, url: URL, id: string, inner: string): Promise<Response> {
    if (req.method !== 'GET' && req.method !== 'POST') throw new HttpError(405, 'method', 'GET or POST')
    const { signed, body } = await signedBody(req, url, cfg.mailMaxBytes)
    knownTown(id)
    innerFrom(req.headers, signed, id)
    const wait = waitOf(req)
    return relayed(req, {
      town: id,
      scope: 'town',
      sender: signed.id,
      signed,
      signedPath: url.pathname + url.search,
      method: req.method,
      path: inner + url.search,
      headers: pick(req.headers, TOWN_HEADERS),
      body,
      expires: now() + wait + cfg.leaseMs,
      wait,
    })
  }

  /** POST /v1/towns/{id}/mail: a request for town [id] kept in its mailbox ({request: {method, path, headers, body?}, ttl_s?}). */
  async function mail(req: Request, url: URL, id: string): Promise<Response> {
    const { signed, body } = await signedBody(req, url, cfg.mailMaxBytes + 8 * 1024)
    knownTown(id)
    const m = parseJson(body)
    const r = m?.request
    const headers: Record<string, string> = {}
    for (const [k, v] of Object.entries(r?.headers && typeof r.headers === 'object' ? r.headers : {})) if (TOWN_HEADERS.test(k.toLowerCase()) && typeof v === 'string') headers[k.toLowerCase()] = v
    if (!r || !['GET', 'POST'].includes(r.method) || typeof r.path !== 'string' || !/^\/town\/[A-Za-z0-9_/-]{1,80}(\?[\w=&%.-]{0,200})?$/.test(r.path) || (r.body !== undefined && typeof r.body !== 'string')) {
      throw new HttpError(400, 'bad_request', 'expected {request: {method: GET|POST, path: "/town/…", headers, body?: text}, ttl_s?}')
    }
    innerFrom(headers, signed, id)
    const inner = new TextEncoder().encode(r.body ?? '')
    if (inner.length > cfg.mailMaxBytes) throw new HttpError(413, 'too_large', `a request is at most ${cfg.mailMaxBytes} bytes`)
    const days = cfg.mailDays * 86_400_000
    const ttl = Number.isFinite(m.ttl_s) && m.ttl_s > 0 ? Math.min(days, m.ttl_s * 1000) : days
    const mid = deliver({ town: id, scope: 'town', sender: signed.id, signed, signedPath: url.pathname + url.search, method: r.method, path: r.path, headers, body: inner, expires: now() + Math.max(60_000, ttl), async: true })
    return json({ id: mid, expires: iso(now() + Math.max(60_000, ttl)) }, 202)
  }

  /** A message as its town gets it from its mailbox. */
  function view(m: MailRow & { body: Uint8Array | null }) {
    let meta: any = {}
    try {
      meta = JSON.parse(m.meta)
    } catch {}
    const body = m.size ? (m.size <= cfg.inlineMaxBytes ? { body: b64(m.body ?? new Uint8Array()) } : { body_url: `/v1/mail/${m.id}/body` }) : {}
    return { id: m.id, kind: m.kind, scope: m.scope, from: m.sender, at: iso(m.at), expires: iso(m.expires), request: { method: meta.method, path: meta.path, headers: meta.headers ?? {} }, sender: meta.sender ?? null, body_size: m.size, ...body }
  }

  /** GET /v1/mailbox?wait=<s>&limit=<n>: the signer's waiting messages, at once or as soon as one comes (a long poll). */
  async function poll(req: Request, url: URL): Promise<Response> {
    const { signed } = await signedBody(req, url, 0)
    owner(signed, signed.id)
    const id = signed.id
    const limit = Math.max(1, Math.min(50, Number(url.searchParams.get('limit')) || 20))
    const wait = Math.max(0, Math.min(cfg.pollWaitMaxMs, (Number(url.searchParams.get('wait')) || 0) * 1000))
    store.polled(id, now())
    let got = store.lease(id, limit, now(), now() + cfg.leaseMs, cfg.inlineMaxBytes)
    if (!got.length && wait > 0) {
      await waiters.wait(`town:${id}`, wait, req.signal)
      // a poll whose connection closed takes nothing: what came stays for the next one
      if (req.signal.aborted) return json({ mail: [] })
      got = store.lease(id, limit, now(), now() + cfg.leaseMs, cfg.inlineMaxBytes)
    }
    store.polled(id, now())
    return json({ mail: got.map(view) })
  }

  /** The routes of /v1/towns/{id}/(public|readers|family|mail). */
  async function townRoute(req: Request, url: URL, id: string, what: string): Promise<Response> {
    if (what === 'public' && req.method === 'PUT') {
      const { signed, body } = await signedBody(req, url, cfg.publicMaxBytes)
      owner(signed, id)
      parseJson(body)
      store.putPublic(id, body, { path: url.pathname + url.search, time: signed.time, sig: signed.sig }, now())
      return json({ ok: true, at: iso(now()) })
    }
    if (what === 'public' && req.method === 'GET') {
      const { signed } = await signedBody(req, url, 0)
      const town = knownTown(id)
      let readers: string[] = []
      try {
        readers = JSON.parse(town.readers)
      } catch {}
      if (signed.id !== id && !readers.includes(signed.id)) throw new HttpError(403, 'not_a_reader', "only the towns this town is linked with read its state")
      const p = store.getPublic(id) ?? (() => { throw new HttpError(404, 'none', 'the town has published no state yet') })()
      // the town's own signature of what it published: the reader checks it with the key it knows
      return new Response(asBody(p.body), {
        headers: { 'content-type': 'application/json', [RELAY_HEADER]: 'ok', 'x-lani-publisher-path': p.path, 'x-lani-publisher-time': p.time, 'x-lani-publisher-sig': p.sig, 'x-lani-published-at': iso(p.at) },
      })
    }
    if (what === 'readers' && req.method === 'PUT') {
      const { signed, body } = await signedBody(req, url, 64 * 1024)
      owner(signed, id)
      const readers = parseJson(body)?.readers
      if (!Array.isArray(readers) || readers.length > 1000 || !readers.every(r => typeof r === 'string' && TOWN_ID.test(r))) throw new HttpError(400, 'bad_request', 'expected {readers: [town ids]}')
      store.setReaders(id, [...new Set(readers as string[])])
      return json({ ok: true, readers: readers.length })
    }
    if (what === 'family' && req.method === 'PUT') {
      const { signed, body } = await signedBody(req, url, 4 * 1024)
      owner(signed, id)
      const sha = parseJson(body)?.token_sha256
      if (sha !== null && !(typeof sha === 'string' && /^[0-9a-f]{64}$/.test(sha))) throw new HttpError(400, 'bad_request', 'expected {token_sha256: <hex> | null}')
      store.setFamily(id, sha)
      return json({ ok: true, family: sha !== null })
    }
    if (what === 'mail' && req.method === 'POST') return mail(req, url, id)
    throw new HttpError(405, 'method', 'not here')
  }

  /** /v1/mail/{mid}/(body|reply): the town fetches a large body, answers; the sender fetches the answer. */
  async function mailRoute(req: Request, url: URL, mid: string, what: string): Promise<Response> {
    if (what === 'body' && req.method === 'GET') {
      const { signed } = await signedBody(req, url, 0)
      const m = store.mail(mid)
      if (!m || m.town !== signed.id) throw new HttpError(404, 'unknown_mail', 'no such message')
      return new Response(asBody(m.body), { headers: { 'content-type': 'application/octet-stream', [RELAY_HEADER]: 'ok' } })
    }
    if (what === 'reply' && req.method === 'POST') {
      const { signed, body } = await signedBody(req, url, cfg.replyMaxBytes)
      const m = store.mail(mid)
      if (!m || m.town !== signed.id) throw new HttpError(404, 'unknown_mail', 'no such message (answered already, or gone)')
      store.seeTown(signed.id, signed.publicKey, now())
      const status = Number(req.headers.get('x-lani-reply-status') ?? 200)
      if (!Number.isInteger(status) || status < 200 || status > 599) throw new HttpError(400, 'bad_request', 'x-lani-reply-status: 200-599')
      const given = req.headers.get('x-lani-reply-headers') ?? '{}'
      let headers: Record<string, string> = {}
      try {
        const h = given.length <= 8192 ? JSON.parse(given) : {}
        for (const [k, v] of Object.entries(h && typeof h === 'object' ? h : {})) if (REPLY_HEADERS.has(k.toLowerCase()) && typeof v === 'string') headers[k.toLowerCase()] = v
      } catch {
        headers = {}
      }
      let meta: any = {}
      try {
        meta = JSON.parse(m.meta)
      } catch {}
      // nobody waits for it (the sender of a tunnelled request gave up, the family page doesn't ask again): not kept
      if (!meta.async && waiters.count(`reply:${mid}`) === 0) {
        store.ack(signed.id, [mid])
        return json({ ok: true, kept: false })
      }
      if (store.replyBytes(signed.id, now()) + body.length > cfg.replyBytes) throw new HttpError(507, 'quota', 'too many answers wait to be picked up')
      if (store.totalBytes() + body.length > cfg.storeMaxBytes) throw new HttpError(507, 'full', 'the relay is full: try again later')
      store.putReply({ mail: mid, sender: m.sender, town: m.town, status, headers: JSON.stringify(headers), size: body.length, at: now(), expires: now() + cfg.replyHours * 3_600_000 }, body)
      waiters.notify(`reply:${mid}`)
      return json({ ok: true })
    }
    if (what === 'reply' && req.method === 'GET') {
      const { signed } = await signedBody(req, url, 0)
      const wait = Math.max(0, Math.min(cfg.tunnelWaitMaxMs, (Number(url.searchParams.get('wait')) || 0) * 1000))
      const r = store.reply(mid)
      if (r && r.sender !== signed.id) throw new HttpError(404, 'unknown_mail', 'no such message')
      if (!r) {
        const m = store.mail(mid)
        if (!m || m.sender !== signed.id) throw new HttpError(404, 'unknown_mail', 'no such message (or its answer ran out)')
      }
      const got = r ?? (await awaitReply(mid, wait, req.signal))
      if (!got) return json({ pending: true, code: 'pending' }, 202)
      return new Response(NULL_BODY.has(got.status) ? null : asBody(got.body), {
        headers: { [RELAY_HEADER]: 'ok', 'content-type': 'application/octet-stream', 'x-lani-reply-status': String(got.status), 'x-lani-reply-headers': got.headers },
      })
    }
    throw new HttpError(405, 'method', 'not here')
  }

  /** The family page's API for town [id]: its token checked, the call tunnelled to the town. */
  async function familyApi(req: Request, url: URL, id: string, rest: string, ip: string): Promise<Response> {
    const origin = req.headers.get('origin')?.replace(/\/+$/, '')
    const cors: Record<string, string> = origin && cfg.familyOrigins.includes(origin)
      ? { 'access-control-allow-origin': origin, vary: 'origin', 'access-control-allow-headers': 'authorization, content-type', 'access-control-allow-methods': 'GET, POST, PUT, DELETE', 'access-control-max-age': '600' }
      : {}
    try {
      if (req.method === 'OPTIONS') return new Response(null, { status: cors['access-control-allow-origin'] ? 204 : 403, headers: { ...cors, [RELAY_HEADER]: 'ok' } })
      const route = familyRoute(req.method, rest)
      if (!route) throw new HttpError(rest.startsWith('family/') || rest.startsWith('audio') ? 403 : 404, 'forbidden', 'the family page does not do this')
      const town = store.town(id)
      if (!town?.family_sha256) throw new HttpError(404, 'no_family', "this town's family page isn't on the relay")
      const given = (req.headers.get('authorization') ?? '').replace(/^Bearer /, '')
      if (!given || !timingSafeEqual(Buffer.from(sha256hex(given), 'hex'), Buffer.from(town.family_sha256, 'hex'))) throw new HttpError(401, 'unauthorized', 'unauthorized')
      // after the token: a stranger can't use up the family's own rate
      const wait = familyLimit.take(`family:${id}`)
      if (wait) throw limited(wait, "for this town's family page")
      const body = await readBody(req, route.max === 'audio' ? cfg.familyAudioMaxBytes : route.max === 'json' ? cfg.familyJsonMaxBytes : 0)
      const waitMs = waitOf(req, 20_000)
      return await relayed(
        req,
        {
          town: id,
          scope: 'family',
          sender: `family:${id}`,
          method: req.method,
          path: `/${rest}${url.search}`,
          headers: pick(req.headers, FAMILY_HEADERS),
          body,
          expires: now() + (route.queue ? cfg.mailDays * 86_400_000 : waitMs + cfg.leaseMs),
          wait: waitMs,
          queue: route.queue,
        },
        cors,
      )
    } catch (e) {
      if (e instanceof HttpError) return fail(e.status, e.code, e.message, { ...e.headers, ...cors })
      throw e
    }
  }

  async function route(req: Request, ip: string): Promise<Response> {
    const url = new URL(req.url)
    const path = url.pathname
    const wait = ipLimit.take(`ip:${ip}`)
    if (wait) throw limited(wait, 'from this address')
    let m: RegExpExecArray | null
    if (path === '/health') return json({ ok: true, service: 'lani-relay', version: VERSION, time: iso(now()) })
    if ((m = /^\/family\/([0-9a-f]{32})(\/|\/app\.js|\/style\.css)?$/.exec(path)) && req.method === 'GET') return page(m[1], m[2] ?? '') ?? fail(404, 'not_found', 'not found')
    if ((m = /^\/v1\/family\/([0-9a-f]{32})\/([A-Za-z0-9_./-]{1,120})$/.exec(path)) && !path.includes('..')) return familyApi(req, url, m[1], m[2], ip)
    if ((m = /^\/v1\/towns\/([0-9a-f]{32})\/town\/hello$/.exec(path)) && req.method === 'GET') {
      // as the town's bridge answers it (features/towns.ts): the inviting town's id and key, which the invitation's fingerprint checks
      const t = knownTown(m[1])
      return json({ v: 1, id: t.id, public_key: t.public_key, relay: true })
    }
    if ((m = /^\/v1\/towns\/([0-9a-f]{32})(\/town\/[A-Za-z0-9_/-]{1,80})$/.exec(path))) return tunnel(req, url, m[1], m[2])
    if ((m = /^\/v1\/towns\/([0-9a-f]{32})\/(public|readers|family|mail)$/.exec(path))) return townRoute(req, url, m[1], m[2])
    if (path === '/v1/mailbox' && req.method === 'GET') return poll(req, url)
    if (path === '/v1/mailbox/ack' && req.method === 'POST') {
      const { signed, body } = await signedBody(req, url, 64 * 1024)
      owner(signed, signed.id)
      const ids = parseJson(body)?.ids
      if (!Array.isArray(ids) || ids.length > 200 || !ids.every(i => typeof i === 'string' && MAIL_ID.test(i))) throw new HttpError(400, 'bad_request', 'expected {ids: [mail ids]}')
      return json({ ok: true, acked: store.ack(signed.id, ids) })
    }
    if ((m = /^\/v1\/mail\/(m_[A-Za-z0-9_-]{16,40})\/(body|reply)$/.exec(path))) return mailRoute(req, url, m[1], m[2])
    const b = await backup(req, url)
    if (b) return b
    return fail(404, 'not_found', 'not found')
  }

  return {
    store,
    async fetch(req, ip = 'local') {
      try {
        return await route(req, ip)
      } catch (e) {
        if (e instanceof HttpError) return fail(e.status, e.code, e.message, e.headers)
        console.error(`[lani-relay] ${req.method} ${new URL(req.url).pathname}: ${(e as Error)?.stack ?? e}`)
        return fail(500, 'internal', 'internal error')
      }
    },
    sweep: () => store.sweep(now()),
    close: () => store.close(),
  }
}
