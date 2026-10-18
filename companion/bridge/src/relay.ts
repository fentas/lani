// The bridge's relay client (companion/relay; docs/plans/05-built/relay.md). With LANI_RELAY_URL set, this town:
// - publishes its public state there for the towns it is linked with (towns.ts publicState), and its family token's
//   SHA-256 when the family page goes through the relay;
// - polls its mailbox there (a long poll) and answers what comes through its own router, as if it came over HTTP: other
//   towns' signed requests (/town/…) and the family page's (/family/api/…, /audio…), never the app's routes;
// - is reachable at <relay>/v1/towns/<its id>, the URL other towns know it by when they can't reach it directly.
// Without LANI_RELAY_URL nothing here runs, and the bridge behaves as before.
//
// Reaching another town through the relay needs no relay of one's own: towns.ts townRequest signs a request to a relay
// URL with this town's key twice, inside for the town (x-lani-town-*) and outside for the relay (X-Lani-*: relayHeaders).
//
// Only node:crypto and fetch: this runs on a node (Bun) and on the phone (QuickJS) alike.
import { createHash, createPublicKey, sign, verify } from 'node:crypto'
import type { BridgeKey } from './pairing'

type Log = (...a: unknown[]) => void

/** A town's address on a relay: <relay>/v1/towns/<its id>. */
const RELAY_TOWN = /^https?:\/\/[^?#\s]+\/v1\/towns\/([0-9a-f]{32})$/

/** The host of a request that came through the relay (the bridge's own router sees it so). */
export const RELAYED_HOST = 'relay.lani.invalid'

/** Whether [url] is a town's address on a relay. */
export const isRelayTownUrl = (url: unknown): url is string => typeof url === 'string' && RELAY_TOWN.test(url)

/** [url] when it is town [id]'s address on a relay (what another town says its relay is); undefined otherwise. */
export function relayTownOf(url: unknown, id: string): string | undefined {
  if (typeof url !== 'string' || url.length > 300) return undefined
  const m = RELAY_TOWN.exec(url.trim())
  if (!m || m[1] !== id) return undefined
  try {
    const u = new URL(url.trim())
    return u.username || u.password || u.search || u.hash ? undefined : url.trim()
  } catch {
    return undefined
  }
}

/** Town [id]'s address on the relay at [relay]. */
export const relayTownUrl = (relay: string, id: string) => `${relay.replace(/\/+$/, '')}/v1/towns/${id}`

/** The relay this node uses: LANI_RELAY_URL, an http(s) URL without credentials, query or fragment; undefined when unset (off). */
export function relayUrlFrom(env: Record<string, string | undefined> = process.env): string | undefined {
  const v = env.LANI_RELAY_URL?.trim()
  if (!v) return undefined
  try {
    const u = new URL(v)
    if (!['http:', 'https:'].includes(u.protocol) || u.username || u.password || u.search || u.hash) return undefined
    return u.toString().replace(/\/+$/, '')
  } catch {
    return undefined
  }
}

/** The relay address of a link: its own relay, or its URL when that is one. */
export const relayOf = (l: { url: string; relay?: string }): string | undefined => l.relay ?? (isRelayTownUrl(l.url) ? l.url : undefined)

// --- signatures (the shared contract) ---------------------------------------------------------------------------------

const sha256hex = (b: string | Uint8Array) => createHash('sha256').update(b).digest('hex')

/** What a signed request to the relay covers. */
export const relayMessage = (method: string, pathWithQuery: string, time: number | string, bodySha256: string) => `${method.toUpperCase()}\n${pathWithQuery}\n${time}\n${bodySha256}`

let lastTime = 0

/**
 * X-Lani-Key, X-Lani-Time and X-Lani-Sig for a request of [key] to [url] (the relay takes each signed text once, so two
 * requests of one millisecond get times one apart).
 */
export function relayHeaders(key: BridgeKey, method: string, url: string | URL, body: string | Uint8Array = '', now = Date.now()): Record<string, string> {
  const u = typeof url === 'string' ? new URL(url) : url
  const time = Math.max(now, lastTime + 1)
  lastTime = time
  const message = relayMessage(method, u.pathname + u.search, time, sha256hex(body))
  return { 'x-lani-key': key.spki.toString('base64'), 'x-lani-time': String(time), 'x-lani-sig': sign('sha256', Buffer.from(message), key.privateKey).toString('base64') }
}

function verifiedBy(publicKey: string, message: string, signature: string): boolean {
  try {
    return verify('sha256', Buffer.from(message), createPublicKey({ key: Buffer.from(publicKey, 'base64'), format: 'der', type: 'spki' }), Buffer.from(signature, 'base64'))
  } catch {
    return false
  }
}

/**
 * Town [l]'s public state as it last published it on its relay, checked with its key (its own signature of what it
 * published, so the relay can't make one up); undefined when it has none there, or this town may not read it.
 */
export async function publishedState(key: BridgeKey, l: { id: string; public_key: string; url: string; relay?: string }, timeoutMs = 5_000): Promise<{ body: unknown; at: number } | undefined> {
  const relay = relayOf(l)
  if (!relay) return undefined
  try {
    const url = `${relay}/public`
    const r = await fetch(url, { headers: relayHeaders(key, 'GET', url), signal: AbortSignal.timeout(timeoutMs), redirect: 'manual' })
    if (r.status !== 200) return undefined
    const text = await r.text()
    if (text.length > 512 * 1024) return undefined
    const path = r.headers.get('x-lani-publisher-path') ?? ''
    const time = r.headers.get('x-lani-publisher-time') ?? ''
    const sig = r.headers.get('x-lani-publisher-sig') ?? ''
    if (!path.endsWith(`/v1/towns/${l.id}/public`) || !verifiedBy(l.public_key, relayMessage('PUT', path, time, sha256hex(text)), sig)) return undefined
    const at = Date.parse(r.headers.get('x-lani-published-at') ?? '') || Number(time) || Date.now()
    return { body: JSON.parse(text), at }
  } catch {
    return undefined
  }
}

// --- the client ------------------------------------------------------------------------------------------------------

/** A town's own routes through the relay: other towns' signed requests, and the family page's. */
const TOWN_PATH = /^\/town\/[A-Za-z0-9_/-]{1,80}(\?.*)?$/
const FAMILY_PATH = /^\/(family\/api\/[A-Za-z0-9_/-]{1,80}|audio(\/[A-Za-z0-9_./-]{1,80})?)(\?.*)?$/
const TOWN_HEADERS = /^(content-type|x-lani-town-[a-z]+|x-fluent-town-[a-z]+)$/
const FAMILY_HEADERS = /^(content-type|authorization)$/
/** What of an answer goes back through the relay. */
const ANSWER_HEADERS = ['content-type', 'x-lani-town-signature', 'x-fluent-town-signature', 'retry-after', 'cache-control', 'content-security-policy', 'x-content-type-options']
/** The relay's limit on an answer (RELAY_REPLY_MAX_BYTES). */
const MAX_ANSWER = 8 * 1024 * 1024
/** At most this many requests answered at once; the mailbox waits beyond that. */
const MAX_INFLIGHT = 16
/** A request's handler gets this long (one that asks another town back waits for its answer). */
const HANDLE_MS = 30_000

export type RelayStatus = {
  url: string
  town: string
  connected: boolean
  last_poll?: string
  last_error?: string
  published_at?: string
  family: boolean
  handled: number
}

/** A message of the mailbox, as the relay lists it (companion/relay/src/relay.ts view). */
type Mail = {
  id: string
  kind: string
  scope: string
  from: string
  request: { method: string; path: string; headers: Record<string, string> }
  body_size: number
  body?: string
  body_url?: string
}

/** The relay client of one town ([key]) on the relay at [url]. */
export class RelayClient {
  /** This town's address on the relay. */
  readonly townUrl: string
  private dispatch?: (req: Request) => Promise<Response>
  private town?: { state: () => string; readers: () => string[] }
  private familySha: () => string | null = () => null
  private running = false
  private stopper = new AbortController()
  private inflight = 0
  private published = { state: '', readers: '', family: undefined as string | null | undefined, at: 0 }
  private publishTimer?: ReturnType<typeof setTimeout>
  private readonly st: RelayStatus

  constructor(
    readonly url: string,
    private readonly key: BridgeKey,
    private readonly log: Log,
    private readonly o: { pollWaitS?: number; publishMs?: number } = {},
  ) {
    this.townUrl = relayTownUrl(url, key.fingerprint)
    this.st = { url, town: this.townUrl, connected: false, family: false, handled: 0 }
  }

  /** The router that answers what comes through the relay (the bridge's routes; the family's token, never the app's). */
  attach(dispatch: (req: Request) => Promise<Response>) {
    this.dispatch = dispatch
  }

  /** What this town publishes: its public state (JSON text) and the towns that may read it. */
  provideTown(p: { state: () => string; readers: () => string[] }) {
    this.town = p
  }

  /** The family token's SHA-256 when the family page goes through the relay (null: it doesn't). */
  provideFamily(sha: () => string | null) {
    this.familySha = sha
  }

  status(): RelayStatus {
    return { ...this.st }
  }

  /** A signed request to the relay ([path]: from the relay's root). */
  async request(method: string, path: string, body?: string | Uint8Array, o: { timeoutMs?: number; headers?: Record<string, string> } = {}): Promise<Response> {
    const url = this.url + path
    // its own controller (not AbortSignal.any: the phone's runtime may lack it), aborted by its time or by stop()
    const ctl = new AbortController()
    const timer = setTimeout(() => ctl.abort(new Error(`no answer from the relay in ${(o.timeoutMs ?? 15_000) / 1000} s`)), o.timeoutMs ?? 15_000)
    const stopped = () => ctl.abort(new Error('stopped'))
    this.stopper.signal.addEventListener('abort', stopped, { once: true })
    try {
      return await fetch(url, {
        method,
        headers: { ...relayHeaders(this.key, method, url, body ?? ''), ...(o.headers ?? {}) },
        ...(body === undefined ? {} : { body: body as BodyInit }),
        signal: ctl.signal,
        redirect: 'manual',
      })
    } finally {
      clearTimeout(timer)
      this.stopper.signal.removeEventListener('abort', stopped)
    }
  }

  /** Starts polling the mailbox and publishing (once). */
  start() {
    if (this.running) return
    if (!this.dispatch) throw new Error('relay: nothing to answer with (attach a router first)')
    this.running = true
    this.log(`relay: ${this.url}, this town at ${this.townUrl}`)
    void this.loop()
    void this.publishLoop()
  }

  stop() {
    this.running = false
    this.stopper.abort()
    clearTimeout(this.publishTimer)
  }

  /** Publishes soon (a link came or went, so its readers changed). */
  nudge() {
    if (!this.running) return
    clearTimeout(this.publishTimer)
    this.publishTimer = setTimeout(() => void this.publishLoop(), 300)
  }

  private sleep(ms: number) {
    return new Promise<void>(resolve => {
      const done = () => {
        clearTimeout(t)
        this.stopper.signal.removeEventListener('abort', done)
        resolve()
      }
      const t = setTimeout(done, ms)
      this.stopper.signal.addEventListener('abort', done)
    })
  }

  private async loop() {
    let backoff = 1_000
    const wait = this.o.pollWaitS ?? 25
    while (this.running) {
      while (this.inflight >= MAX_INFLIGHT && this.running) await this.sleep(50)
      try {
        const r = await this.request('GET', `/v1/mailbox?wait=${wait}&limit=20`, undefined, { timeoutMs: (wait + 15) * 1000 })
        const text = await r.text()
        if (r.status !== 200) throw new Error(`the relay answered ${r.status}: ${text.slice(0, 200)}`)
        const mail: Mail[] = JSON.parse(text).mail ?? []
        if (!this.st.connected) this.log('relay: connected')
        this.st.connected = true
        this.st.last_poll = new Date().toISOString()
        backoff = 1_000
        // each answered on its own: a request that asks another town back doesn't hold up the mailbox
        for (const m of mail) void this.take(m)
      } catch (e) {
        if (!this.running) break
        const why = (e as Error).message
        if (this.st.connected || this.st.last_error !== why) this.log(`relay: ${why}; trying again in ${backoff / 1000} s`)
        this.st.connected = false
        this.st.last_error = why
        await this.sleep(backoff)
        backoff = Math.min(60_000, backoff * 2)
      }
    }
  }

  /** One message: answered through the router, the answer sent back. */
  private async take(m: Mail) {
    this.inflight++
    try {
      let res: Response
      try {
        const req = await this.toRequest(m)
        let timer: ReturnType<typeof setTimeout> | undefined
        const late = new Promise<Response>(resolve => {
          timer = setTimeout(() => resolve(Response.json({ error: 'the town took too long', code: 'timeout' }, { status: 504 })), HANDLE_MS)
        })
        res = req ? await Promise.race([this.dispatch!(req), late]) : Response.json({ error: 'not through the relay', code: 'forbidden' }, { status: 403 })
        clearTimeout(timer)
      } catch (e) {
        this.log(`relay: ${m.request?.method} ${m.request?.path}: ${(e as Error).message}`)
        res = Response.json({ error: 'internal error' }, { status: 500 })
      }
      await this.answer(m.id, res)
      this.st.handled++
    } catch (e) {
      if (this.running) this.log(`relay: the answer to ${m.request?.path} wasn't sent: ${(e as Error).message}`)
    } finally {
      this.inflight--
    }
  }

  /** The message as a request for the router: only a town's routes or the family page's, with only their headers. */
  private async toRequest(m: Mail): Promise<Request | undefined> {
    const r = m.request
    if (m.kind !== 'http' || !r || !['GET', 'POST', 'PUT', 'DELETE'].includes(r.method) || typeof r.path !== 'string') return undefined
    const family = m.scope === 'family'
    if (!(family ? FAMILY_PATH : TOWN_PATH).test(r.path) || r.path.includes('..') || (!family && m.scope !== 'town')) return undefined
    const allowed = family ? FAMILY_HEADERS : TOWN_HEADERS
    const headers: Record<string, string> = {}
    for (const [k, v] of Object.entries(r.headers ?? {})) if (allowed.test(k.toLowerCase()) && typeof v === 'string') headers[k.toLowerCase()] = v
    let body: Uint8Array | undefined
    if (m.body !== undefined) body = Buffer.from(m.body, 'base64')
    else if (m.body_url && m.body_size > 0) {
      const b = await this.request('GET', m.body_url, undefined, { timeoutMs: 30_000 })
      if (b.status !== 200) throw new Error(`its body: the relay answered ${b.status}`)
      body = new Uint8Array(await b.arrayBuffer())
    }
    const hasBody = r.method !== 'GET' && body !== undefined
    return new Request(`http://${RELAYED_HOST}${r.path}`, { method: r.method, headers, ...(hasBody ? { body: body as BodyInit } : {}) })
  }

  private async answer(id: string, res: Response) {
    let bytes = new Uint8Array(await res.arrayBuffer())
    let status = res.status
    const headers: Record<string, string> = {}
    for (const k of ANSWER_HEADERS) {
      const v = res.headers.get(k)
      if (v !== null) headers[k] = v
    }
    if (bytes.length > MAX_ANSWER) {
      bytes = new TextEncoder().encode(JSON.stringify({ error: 'the answer is too large for the relay', code: 'too_large' }))
      status = 502
      for (const k of Object.keys(headers)) delete headers[k]
      headers['content-type'] = 'application/json'
    }
    const r = await this.request('POST', `/v1/mail/${id}/reply`, bytes, { timeoutMs: 60_000, headers: { 'x-lani-reply-status': String(status), 'x-lani-reply-headers': JSON.stringify(headers), 'content-type': 'application/octet-stream' } })
    if (r.status !== 200 && r.status !== 404) throw new Error(`the relay answered ${r.status}: ${(await r.text()).slice(0, 200)}`)
    await r.body?.cancel().catch(() => {})
  }

  /** Publishes what changed (the public state, its readers, the family token's hash), then again in a while. */
  private async publishLoop() {
    clearTimeout(this.publishTimer)
    try {
      await this.publish()
    } catch (e) {
      if (this.running) this.log(`relay: publishing: ${(e as Error).message}`)
    }
    if (this.running) this.publishTimer = setTimeout(() => void this.publishLoop(), this.o.publishMs ?? 120_000)
  }

  async publish() {
    const id = this.key.fingerprint
    const put = async (what: string, body: string) => {
      const r = await this.request('PUT', `/v1/towns/${id}/${what}`, body, { headers: { 'content-type': 'application/json' } })
      const text = await r.text()
      if (r.status !== 200) throw new Error(`${what}: the relay answered ${r.status}: ${text.slice(0, 200)}`)
    }
    if (this.town) {
      const state = this.town.state()
      // a fresh copy at least every few hours, so readers see the day it was made
      if (state !== this.published.state || Date.now() - this.published.at > 6 * 3_600_000) {
        await put('public', state)
        this.published.state = state
        this.published.at = Date.now()
        this.st.published_at = new Date().toISOString()
      }
      const readers = JSON.stringify([...new Set(this.town.readers())].sort())
      if (readers !== this.published.readers) {
        await put('readers', JSON.stringify({ readers: JSON.parse(readers) }))
        this.published.readers = readers
      }
    }
    const family = this.familySha()
    if (family !== this.published.family) {
      await put('family', JSON.stringify({ token_sha256: family }))
      this.published.family = family
      this.st.family = family !== null
      this.log(`relay: the family page ${family ? `goes through the relay: ${this.url}/family/${id}/` : "doesn't go through the relay"}`)
    }
  }
}

/** The relay client for this town when LANI_RELAY_URL is set; undefined (off) otherwise. */
export function relayFromEnv(env: Record<string, string | undefined>, key: BridgeKey, log: Log): RelayClient | undefined {
  const url = relayUrlFrom(env)
  if (env.LANI_RELAY_URL?.trim() && !url) log(`relay: LANI_RELAY_URL isn't an http(s) URL: ${env.LANI_RELAY_URL}; off`)
  if (!url) return undefined
  const n = (v: string | undefined) => (v && Number(v) > 0 ? Number(v) : undefined)
  return new RelayClient(url, key, log, { pollWaitS: n(env.LANI_RELAY_POLL_S), publishMs: n(env.LANI_RELAY_PUBLISH_MS) })
}
