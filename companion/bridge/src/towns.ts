// Towns that know each other (companion/README.md, "Towns"; plan 2, §3–§5). Shared by the bridge
// (features/towns.ts) and the CLI (cli/town.ts):
// - a town's id: its bridge key's fingerprint (pairing.ts), so a town proves who it is by signing;
// - links: the towns this one knows, in <data>/app/towns.json (their id, public key and URL; no secrets);
// - invitations: one-time codes like the pairing codes (hashed at rest, short-lived), as a
//   lani://town?… link carrying the inviter's URL and fingerprint;
// - signed requests between bridges: method, path, body hash, time, nonce, sender and recipient, signed with
//   the sender's key and checked with the key of the link; answers are signed back;
// - a town's public state: what a linked town may see (the map, the people, the day), nothing private.
//
// Written as if the other town were remote: on one node (option A) a peer's URL is http://127.0.0.1:<port>, and
// federation (option B) only changes the URLs.
import { createHash, randomBytes, sign, verify } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { setting } from './env'
import { CULTURE_ID, tutorOf, type Manifest } from './cultures'
import { learnerFacts } from './learners'
import { createOneTimeCode, fingerprintOf, spendOneTimeCode, withLock, writeAtomic, type BridgeKey } from './pairing'

/** The version of the invitation link, of the signatures and of the public state. */
export const TOWN_VERSION = 1
/** An invitation works once, for this long. */
export const INVITE_TTL_MS = 30 * 60_000
/** A signed request older or newer than this is refused (clocks of two nodes differ a little). */
export const MAX_SKEW_MS = 5 * 60_000
export const TOWN_ID = /^[0-9a-f]{32}$/
const LANGUAGE = /^[a-z]{2,3}$/

const sha256hex = (b: string | Uint8Array) => createHash('sha256').update(b).digest('hex')

/** How a town shows itself: its village's name, its learner's first name, its culture pack and language. */
export type TownDisplay = { name: string; learner: string; culture: string; language: string }

/**
 * A link to another town: its [id] (fingerprint), [public_key] (SubjectPublicKeyInfo DER, base64), the [url] its bridge
 * answers on, when it was linked, [by] whom (the learner's app, or the CLI on the node) and [via] which way (they
 * accepted our invitation, we accepted theirs, or the node linked two of its own towns), and how it showed itself
 * last ([display]).
 */
export type TownLink = {
  id: string
  public_key: string
  url: string
  linked_at: string
  by: 'app' | 'cli'
  via: 'invite' | 'accept' | 'link'
  display?: TownDisplay
}

export type TownsFile = { schema: 'lani.towns/v1'; name?: string; links: TownLink[] }

/** A text another town sent: printable, one line, at most [max] characters. It is shown as plain text only. */
export const cleanText = (s: unknown, max = 60) =>
  (typeof s === 'string' ? s : '').replace(/[\u0000-\u001f\u007f<>]/g, ' ').replace(/\s+/g, ' ').trim().slice(0, max)

/** A display another town sent, cleaned: the names as plain short texts, the culture and language as ids. */
export function cleanDisplay(d: unknown): TownDisplay {
  const o = (d && typeof d === 'object' ? d : {}) as Record<string, unknown>
  return {
    name: cleanText(o.name) || '?',
    learner: cleanText(o.learner, 40),
    culture: typeof o.culture === 'string' && CULTURE_ID.test(o.culture) ? o.culture : '',
    language: typeof o.language === 'string' && LANGUAGE.test(o.language) ? o.language : '',
  }
}

/**
 * How this town shows itself to others: the name the learner chose for the village, else its culture pack's
 * ("Moja vas", "Il mio villaggio"); the learner's first name; the culture pack and its language.
 */
export function selfDisplay(o: { dataDir: string; profile: { id: string; isDefault: boolean }; culture: string; manifest?: Manifest; towns: Towns }): TownDisplay {
  const first = learnerFacts(o.dataDir).name.split(/\s+/)[0] ?? ''
  return {
    name: o.towns.name() || tutorOf(o.manifest).village,
    learner: cleanText(first, 40) || (o.profile.isDefault ? 'Jan' : o.profile.id),
    culture: o.culture,
    language: o.manifest?.language ?? 'sl',
  }
}

/** Where this town's bridge answers other towns: $LANI_TOWN_URL (lani.env's for the default learner), else its own port on this node. */
export const selfUrl = (port: number, env = process.env) => townUrl(setting('LANI_TOWN_URL', env)) ?? `http://127.0.0.1:${port}`

/** An http(s) URL without credentials, query or fragment, without a trailing slash; undefined otherwise. */
export function townUrl(s: unknown): string | undefined {
  if (typeof s !== 'string' || s.length > 300) return undefined
  try {
    const u = new URL(s.trim())
    if (!['http:', 'https:'].includes(u.protocol) || !u.hostname || u.username || u.password || u.search || u.hash) return undefined
    return u.toString().replace(/\/+$/, '')
  } catch {
    return undefined
  }
}

// --- links ---------------------------------------------------------------------------------------------

/**
 * <appDir>/towns.json: this town's links (and the name its learner chose for the village, if any). The bridge and
 * the CLI both change it, under a lock; the bridge reads it again whenever it changed.
 */
export class Towns {
  readonly file: string
  private stamp = ''
  private doc: TownsFile = { schema: 'lani.towns/v1', links: [] }

  constructor(
    appDir: string,
    private readonly log?: (...a: unknown[]) => void,
  ) {
    mkdirSync(appDir, { recursive: true })
    this.file = join(appDir, 'towns.json')
  }

  private read(): TownsFile {
    if (!existsSync(this.file)) return { schema: 'lani.towns/v1', links: [] }
    try {
      const d = JSON.parse(readFileSync(this.file, 'utf8'))
      const links = (Array.isArray(d?.links) ? d.links : []).filter(
        (l: TownLink) => l && typeof l.id === 'string' && TOWN_ID.test(l.id) && typeof l.public_key === 'string' && typeof l.url === 'string',
      )
      return { schema: 'lani.towns/v1', ...(typeof d?.name === 'string' && d.name.trim() ? { name: cleanText(d.name) } : {}), links }
    } catch (e) {
      this.log?.(`towns: ${this.file} unreadable: ${(e as Error).message}`)
      return { schema: 'lani.towns/v1', links: [] }
    }
  }

  private refresh(): TownsFile {
    let stamp = 'none'
    try {
      const st = statSync(this.file)
      stamp = `${st.ino}:${st.mtimeMs}:${st.size}`
    } catch {}
    if (stamp !== this.stamp) {
      this.stamp = stamp
      this.doc = this.read()
    }
    return this.doc
  }

  /** Changes the file under its lock, from what is on disk now. */
  private change(fn: (d: TownsFile) => TownsFile) {
    withLock(`${this.file}.lock`, () => writeAtomic(this.file, JSON.stringify(fn(this.read()), null, 2) + '\n'))
    this.refresh()
  }

  /** The name the learner chose for their village, if any. */
  name(): string | undefined {
    return this.refresh().name
  }

  setName(name: string | undefined) {
    const n = cleanText(name ?? '')
    this.change(({ name: _, ...d }) => (n ? { ...d, name: n } : d))
  }

  links(): TownLink[] {
    return this.refresh().links
  }

  get(id: string): TownLink | undefined {
    return this.refresh().links.find(l => l.id === id)
  }

  /** Adds [link], or replaces the one with its id (linked again: a new URL or key). */
  upsert(link: TownLink) {
    this.change(d => ({ ...d, links: [...d.links.filter(l => l.id !== link.id), link] }))
  }

  /** Removes the link [id]; the link it was, if there was one. */
  remove(id: string): TownLink | undefined {
    const gone = this.get(id)
    if (gone) this.change(d => ({ ...d, links: d.links.filter(l => l.id !== id) }))
    return gone
  }

  /** Keeps how [id] showed itself last, when that changed. */
  setDisplay(id: string, display: TownDisplay) {
    const l = this.get(id)
    if (!l || JSON.stringify(l.display) === JSON.stringify(display)) return
    this.change(d => ({ ...d, links: d.links.map(x => (x.id === id ? { ...x, display } : x)) }))
  }
}

// --- invitations -------------------------------------------------------------------------------------------

const invitesDir = (appDir: string) => join(appDir, 'town-invites')

/** A new invitation to this town, made by the learner's app or by the CLI on the node. */
export const createInvite = (appDir: string, o: { by: 'app' | 'cli'; ttlMs?: number; now?: number }) =>
  createOneTimeCode(invitesDir(appDir), { ttlMs: o.ttlMs ?? INVITE_TTL_MS, now: o.now, extra: { by: o.by } })

/** Spends an invitation: 'ok' once, while it is valid, with who made it. */
export function spendInvite(appDir: string, code: string, now = Date.now()): { status: 'ok' | 'invalid' | 'expired'; by?: 'app' | 'cli' } {
  const r = spendOneTimeCode(invitesDir(appDir), code, now)
  return r.status === 'ok' ? { status: 'ok', by: r.data?.by === 'cli' ? 'cli' : 'app' } : { status: r.status }
}

/** The invitation's text and QR code: `lani://town?v=1&u=<the inviter's URL>&c=<code>&f=<its fingerprint>`. */
export const inviteUri = (o: { url: string; code: string; fingerprint: string }) =>
  `lani://town?v=${TOWN_VERSION}&u=${encodeURIComponent(o.url)}&c=${o.code}&f=${o.fingerprint}`

/** The schemes of an invitation: lani:, and fluent: as the bridges wrote it before the project was renamed. */
const INVITE_PROTOCOLS = ['lani:', 'fluent:']

export type Invite = { url: string; code: string; fingerprint: string }

/** An invitation link, read back; why not when it isn't one this bridge can use. */
export function parseInvite(text: string): { ok: true; invite: Invite } | { ok: false; error: string } {
  let u: URL
  try {
    u = new URL(text.trim())
  } catch {
    return { ok: false, error: 'not an invitation (lani://town?…)' }
  }
  if (!INVITE_PROTOCOLS.includes(u.protocol) || u.host !== 'town') return { ok: false, error: 'not an invitation (lani://town?…)' }
  const v = Number(u.searchParams.get('v'))
  if (!Number.isInteger(v) || v < 1) return { ok: false, error: 'the invitation is incomplete' }
  if (v > TOWN_VERSION) return { ok: false, error: 'the invitation needs a newer bridge' }
  const url = townUrl(u.searchParams.get('u'))
  const code = (u.searchParams.get('c') ?? '').toUpperCase().replace(/[\s-]/g, '')
  const fingerprint = (u.searchParams.get('f') ?? '').toLowerCase()
  if (!url) return { ok: false, error: "the invitation's address isn't valid" }
  if (!/^[A-Z0-9]{6,32}$/.test(code) || !TOWN_ID.test(fingerprint)) return { ok: false, error: 'the invitation is incomplete' }
  return { ok: true, invite: { url, code, fingerprint } }
}

// --- signatures --------------------------------------------------------------------------------------------

export const H = {
  from: 'x-lani-town-from',
  to: 'x-lani-town-to',
  time: 'x-lani-town-time',
  nonce: 'x-lani-town-nonce',
  signature: 'x-lani-town-signature',
} as const

/**
 * Before the project was renamed from Fluent, the towns' headers were x-fluent-town-* and their signatures covered
 * fluent-town/1 and fluent-town-response/1. A town that still runs that code is understood (its headers read, its
 * signatures checked under the old names), and understands this one: it signs under both (signRequest, signedJson).
 * Once every town runs Lani, the old names can go.
 */
export const LEGACY_H: Record<keyof typeof H, string> = {
  from: 'x-fluent-town-from',
  to: 'x-fluent-town-to',
  time: 'x-fluent-town-time',
  nonce: 'x-fluent-town-nonce',
  signature: 'x-fluent-town-signature',
}
const DOMAINS = ['lani', 'fluent'] as const
type Domain = (typeof DOMAINS)[number]

/** Header [k] of [headers], under its name of today or of before the rename. */
export const townHeader = (headers: Headers, k: keyof typeof H) => headers.get(H[k]) ?? headers.get(LEGACY_H[k])

/** What a request's signature covers ([domain] "fluent": as a town from before the rename signs it). */
export const requestMessage = (o: { method: string; path: string; body: string; time: number | string; nonce: string; from: string; to: string }, domain: Domain = 'lani') =>
  [`${domain}-town/${TOWN_VERSION}`, o.method.toUpperCase(), o.path, sha256hex(o.body), String(o.time), o.nonce, o.from, o.to].join('\n')

/** What an answer's signature covers: the request's nonce (so it answers that request) and the body. */
export const responseMessage = (nonce: string, body: string, domain: Domain = 'lani') => `${domain}-town-response/${TOWN_VERSION}\n${nonce}\n${sha256hex(body)}`

/** Whether [signature] is [message] (made for each domain) signed by [publicKey]: today's names, or those from before the rename. */
const verifiedAny = (publicKey: string, message: (d: Domain) => string, signature: string) => DOMAINS.some(d => verifySigned(publicKey, message(d), signature))

const signText = (key: BridgeKey, message: string) => sign('sha256', Buffer.from(message), key.privateKey).toString('base64')

/** Whether [signature] (base64 DER) is [message] signed by [publicKey] (SubjectPublicKeyInfo DER, base64). */
export function verifySigned(publicKey: string, message: string, signature: string): boolean {
  try {
    return verify('sha256', Buffer.from(message), { key: Buffer.from(publicKey, 'base64'), format: 'der', type: 'spki' }, Buffer.from(signature, 'base64'))
  } catch {
    return false
  }
}

/**
 * The headers that sign a request from this town ([key]) to town [to]: today's, and the same under the names from before
 * the rename (LEGACY_H, signed as fluent-town/1), so a town that still runs that code takes it too.
 */
export function signRequest(key: BridgeKey, o: { method: string; path: string; body?: string; to: string; now?: number; nonce?: string }): Record<string, string> {
  const time = o.now ?? Date.now()
  const nonce = o.nonce ?? randomBytes(16).toString('base64url')
  const m = { method: o.method, path: o.path, body: o.body ?? '', time, nonce, from: key.fingerprint, to: o.to }
  const fields = { from: key.fingerprint, to: o.to, time: String(time), nonce }
  return {
    ...Object.fromEntries(Object.entries(fields).map(([k, v]) => [H[k as keyof typeof H], v])),
    [H.signature]: signText(key, requestMessage(m)),
    ...Object.fromEntries(Object.entries(fields).map(([k, v]) => [LEGACY_H[k as keyof typeof H], v])),
    [LEGACY_H.signature]: signText(key, requestMessage(m, 'fluent')),
  }
}

/** An answer's body, signed for the request with [nonce] (also under the old name, for a town from before the rename). */
export function signedJson(key: BridgeKey, nonce: string, body: unknown, status = 200): Response {
  const text = JSON.stringify(body)
  return new Response(text, {
    status,
    headers: {
      'content-type': 'application/json',
      [H.signature]: signText(key, responseMessage(nonce, text)),
      [LEGACY_H.signature]: signText(key, responseMessage(nonce, text, 'fluent')),
    },
  })
}

/** The nonces seen lately, per sender: a request is taken once. Forgotten once their time is out of the window. */
export class Nonces {
  private seen = new Map<string, number>()

  /** Whether [nonce] from [from] came before; records it otherwise. */
  replayed(from: string, nonce: string, now = Date.now()): boolean {
    if (this.seen.size > 10_000) for (const [k, t] of this.seen) if (t < now) this.seen.delete(k)
    const k = `${from}:${nonce}`
    const t = this.seen.get(k)
    if (t !== undefined && t >= now) return true
    this.seen.set(k, now + 2 * MAX_SKEW_MS)
    return false
  }
}

export type SignedCheck =
  | { ok: true; from: string; nonce: string; publicKey: string }
  | { ok: false; code: 'unsigned' | 'misaddressed' | 'stale' | 'unlinked' | 'bad_signature' | 'replayed' }

/**
 * Checks a signed request to town [self]: addressed to it, recent, from a town whose key [keyOf] knows (a link; for
 * a join, the key the request brings), signed by that key, and not seen before.
 */
export function checkSigned(
  headers: Headers,
  o: { method: string; path: string; body: string; self: string; keyOf: (id: string) => string | undefined; nonces: Nonces; now?: number },
): SignedCheck {
  const now = o.now ?? Date.now()
  const [from, to, time, nonce, signature] = (['from', 'to', 'time', 'nonce', 'signature'] as const).map(k => townHeader(headers, k) ?? '')
  if (!TOWN_ID.test(from) || !to || !/^\d{1,16}$/.test(time) || !/^[\w-]{16,64}$/.test(nonce) || !signature) return { ok: false, code: 'unsigned' }
  if (to !== o.self) return { ok: false, code: 'misaddressed' }
  if (Math.abs(now - Number(time)) > MAX_SKEW_MS) return { ok: false, code: 'stale' }
  const publicKey = o.keyOf(from)
  if (!publicKey) return { ok: false, code: 'unlinked' }
  if (!verifiedAny(publicKey, d => requestMessage({ method: o.method, path: o.path, body: o.body, time, nonce, from, to }, d), signature)) return { ok: false, code: 'bad_signature' }
  if (o.nonces.replayed(from, nonce, now)) return { ok: false, code: 'replayed' }
  return { ok: true, from, nonce, publicKey }
}

// --- asking another town -------------------------------------------------------------------------------------

export type Peer = { id: string; public_key: string; url: string }

export class TownError extends Error {
  constructor(
    readonly code: 'unreachable' | 'bad_answer',
    message: string,
  ) {
    super(message)
  }
}

/**
 * A signed request to [peer]: its status and JSON body. A successful answer must be signed by the peer's key for
 * this request (else 'bad_answer'); a peer that doesn't answer is 'unreachable'. An answer larger than [o.maxBytes]
 * (512 KB unless said) is a 'bad_answer'.
 */
export async function townRequest(
  key: BridgeKey,
  peer: Peer,
  method: 'GET' | 'POST',
  path: string,
  body?: unknown,
  o: { timeoutMs?: number; maxBytes?: number } = {},
): Promise<{ status: number; body: any }> {
  const text = body === undefined ? '' : JSON.stringify(body)
  const headers = signRequest(key, { method, path, body: text, to: peer.id })
  let res: Response
  let answer: string
  try {
    res = await fetch(peer.url + path, {
      method,
      headers: { ...headers, ...(body === undefined ? {} : { 'content-type': 'application/json' }) },
      ...(body === undefined ? {} : { body: text }),
      signal: AbortSignal.timeout(o.timeoutMs ?? 5_000),
      redirect: 'manual',
    })
    answer = await res.text()
  } catch (e) {
    throw new TownError('unreachable', `${peer.url}: ${(e as Error).message}`)
  }
  if (answer.length > (o.maxBytes ?? 512 * 1024)) throw new TownError('bad_answer', `${peer.url}: the answer is too large`)
  let parsed: any = null
  try {
    parsed = JSON.parse(answer)
  } catch {}
  if (res.ok) {
    const sig = townHeader(res.headers, 'signature') ?? ''
    if (!verifiedAny(peer.public_key, d => responseMessage(headers[H.nonce], answer, d), sig)) throw new TownError('bad_answer', `${peer.url}: the answer isn't signed by the town's key`)
    if (parsed === null) throw new TownError('bad_answer', `${peer.url}: the answer isn't JSON`)
  }
  return { status: res.status, body: parsed }
}

export type AcceptResult = { ok: true; link: TownLink } | { ok: false; status: number; code: string; error: string }

/**
 * Accepts an invitation for the town of [key]: asks the inviter who it is and checks its key against the
 * invitation's fingerprint, then joins (a signed request with this town's key, URL and display, and the code). The
 * inviter links this town, answers with its own, signed, and this town links it: both ways.
 */
export async function acceptInvite(o: {
  key: BridgeKey
  towns: Towns
  invite: Invite
  self: { url: string; display: TownDisplay }
  by: 'app' | 'cli'
  timeoutMs?: number
}): Promise<AcceptResult> {
  const { invite, key } = o
  const fail = (status: number, code: string, error: string): AcceptResult => ({ ok: false, status, code, error })
  if (invite.fingerprint === key.fingerprint) return fail(400, 'self', 'this is an invitation to this town itself')
  let hello: any
  try {
    const r = await fetch(`${invite.url}/town/hello`, { signal: AbortSignal.timeout(o.timeoutMs ?? 5_000), redirect: 'manual' })
    if (!r.ok) return fail(502, 'not_a_town', `${invite.url} answered ${r.status}: not a town that takes invitations`)
    hello = await r.json()
  } catch (e) {
    return fail(502, 'unreachable', `${invite.url} doesn't answer: ${(e as Error).message}`)
  }
  const publicKey = typeof hello?.public_key === 'string' ? hello.public_key : ''
  let fingerprint = ''
  try {
    fingerprint = fingerprintOf(Buffer.from(publicKey, 'base64'))
  } catch {}
  if (!publicKey || fingerprint !== invite.fingerprint || hello.id !== invite.fingerprint) {
    return fail(409, 'not_that_town', `the town at ${invite.url} isn't the one the invitation names`)
  }
  const peer: Peer = { id: invite.fingerprint, public_key: publicKey, url: invite.url }
  let r: { status: number; body: any }
  try {
    r = await townRequest(key, peer, 'POST', '/town/join', {
      v: TOWN_VERSION,
      code: invite.code,
      town: { id: key.fingerprint, public_key: key.spki.toString('base64'), url: o.self.url, display: o.self.display },
    }, { timeoutMs: o.timeoutMs })
  } catch (e) {
    const te = e as TownError
    return fail(502, te.code ?? 'unreachable', te.message)
  }
  if (r.status !== 200) {
    const code = typeof r.body?.code === 'string' ? r.body.code : 'refused'
    return fail([403, 410, 429].includes(r.status) ? r.status : 502, code, typeof r.body?.error === 'string' ? r.body.error : `the town answered ${r.status}`)
  }
  if (r.body?.town?.id !== peer.id || r.body.town.public_key !== publicKey) return fail(502, 'bad_answer', "the town's answer names another town")
  const link: TownLink = {
    id: peer.id,
    public_key: publicKey,
    url: invite.url,
    linked_at: new Date().toISOString(),
    by: o.by,
    via: 'accept',
    display: cleanDisplay(r.body.town.display),
  }
  o.towns.upsert(link)
  return { ok: true, link }
}

// --- a town's public state ----------------------------------------------------------------------------------

/** "2026-09-26": the node's local date. */
export function localDate(now = Date.now()): string {
  const d = new Date(now)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** A date's day number (days since 1970-01-01), from "YYYY-MM-DD"; NaN when it isn't one. */
const dayOf = (iso: unknown) => {
  const m = typeof iso === 'string' ? /^(\d{4})-(\d{2})-(\d{2})/.exec(iso) : null
  return m ? Date.UTC(+m[1], +m[2] - 1, +m[3]) / 86_400_000 : NaN
}
const isoOf = (day: number) => new Date(day * 86_400_000).toISOString().slice(0, 10)

/** A festival of a culture pack (festivals.json), as far as the public state needs it. */
export type FestivalRule = { id: string; emoji: string; name: Record<string, string>; date: { month?: number; day?: number; easter?: number; last?: string } }

/** Easter Sunday's day number (the Gregorian computus, as the app's Calendar.easter). */
function easter(year: number): number {
  const a = year % 19, b = Math.floor(year / 100), c = year % 100, d = Math.floor(b / 4), e = b % 4
  const f = Math.floor((b + 8) / 25), g = Math.floor((b - f + 1) / 3), h = (19 * a + b - d - g + 15) % 30
  const i = Math.floor(c / 4), k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7, m = Math.floor((a + 11 * h + 22 * l) / 451)
  const month = Math.floor((h + l - 7 * m + 114) / 31), day = ((h + l - 7 * m + 114) % 31) + 1
  return Date.UTC(year, month - 1, day) / 86_400_000
}

const WEEKDAYS = ['sunday', 'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday']

/** [f]'s day in [year], as a day number; NaN when its rule isn't one the app knows. */
export function festivalDay(f: FestivalRule, year: number): number {
  const d = f.date
  if (d.easter !== undefined) return easter(year) + d.easter
  if (d.month && d.last) {
    const w = WEEKDAYS.indexOf(d.last)
    if (w < 0) return NaN
    let day = Date.UTC(year, d.month, 0) / 86_400_000 // the month's last day
    while (new Date(day * 86_400_000).getUTCDay() !== w) day--
    return day
  }
  return d.month && d.day ? Date.UTC(year, d.month - 1, d.day) / 86_400_000 : NaN
}

/** Days a festival can still be celebrated after its day (the app's Calendar.GRACE_DAYS). */
export const FESTIVAL_GRACE_DAYS = 2

/** How old a child born in the village is (the app's Residents.Stage: 0, 45, 240, 420 days). */
export function stageOf(born: unknown, today: string): 'baby' | 'child' | 'youth' | 'adult' {
  const days = dayOf(today) - dayOf(born)
  if (!(days >= 0)) return 'adult'
  return days < 45 ? 'baby' : days < 240 ? 'child' : days < 420 ? 'youth' : 'adult'
}

/** A cast member's stage by their picture: the children of the cast stay children. */
const stageOfArt = (art: string | undefined) => (art === 'baby' ? 'baby' : art?.startsWith('child') ? 'child' : 'adult')

const AGES = ['OGENJ', 'TABOR', 'ZASELEK', 'VAS', 'TRG', 'MESTO']
const unit = (v: unknown) => (typeof v === 'number' && Number.isFinite(v) ? Math.min(1, Math.max(0, v)) : 0)
const int = (v: unknown, dflt: number) => (typeof v === 'number' && Number.isInteger(v) ? v : dflt)
const token = (v: unknown, re = /^[A-Za-z0-9_:-]{1,64}$/) => (typeof v === 'string' && re.test(v) ? v : undefined)

export type PublicState = {
  v: number
  town: TownDisplay & { id: string }
  date: string
  age: string | null
  founded_on: string | null
  villagers: number
  buildings: { type: string; plot: number; level: number; damaged: boolean }[]
  projects: Record<string, number>
  residents: { id: string; name: string; stage: string; emoji?: string; art?: string }[]
  visitor: { id: string; name: string } | null
  sky: { scene: string; rain: number; snow: number; fog: number; wind: number; gloom: number; lightning: boolean } | null
  festival: { id: string; emoji: string; name: Record<string, string>; day: string; late: number; done: boolean } | null
  /** The land the village stands on (the app's Land): its landscape and the number its map is made from, not the village's seed. */
  land: { kind: string; map: number } | null
}

/** A land's landscape ('valley', 'hills', …, 'classic'). */
const LANDSCAPE = /^[a-z]{1,24}$/

/** [v]'s land as a town shows it: its landscape and its map's number ([seed] in the app's state, [map] passed on). */
const landOf = (v: any, seed: 'seed' | 'map'): PublicState['land'] => (v && typeof v === 'object' && token(v.kind, LANDSCAPE) ? { kind: v.kind, map: int(v[seed], 0) } : null)

/**
 * What a linked town may see of this one: who it is, the day's date, the age, the buildings (type, plot, level,
 * damaged), the projects' landmarks, who lives here (id, name, stage), today's visitor and sky, the festival that is
 * on, the land it stands on (its landscape and its map's own number). Built field by field from the village's state, so nothing else of it ever leaves: no resources, requests,
 * chest, friendships, chronicle or statistics, and nothing of the learner's chat, mistakes, progress, words, tokens,
 * devices or family.
 */
export function publicState(o: {
  state: Record<string, any> | undefined
  town: TownDisplay & { id: string }
  /** A cast member's name and picture (the villager store). */
  person: (id: string) => { name: string; art?: string } | undefined
  festivals: FestivalRule[]
  now?: number
}): PublicState {
  const today = localDate(o.now)
  const s = o.state && typeof o.state === 'object' ? o.state : undefined
  const list = (v: unknown) => (Array.isArray(v) ? v : [])
  const buildings = list(s?.buildings).flatMap((b: any) => {
    const type = token(b?.type, /^[A-Z_]{1,32}$/)
    return type ? [{ type, plot: int(b.plot, -1), level: int(b.level, 1), damaged: b.damaged === true }] : []
  })
  const projects: Record<string, number> = {}
  for (const [id, n] of Object.entries(s?.projects && typeof s.projects === 'object' ? s.projects : {})) {
    if (token(id) && typeof n === 'number' && Number.isInteger(n) && n > 0) projects[id] = n
  }
  const residents = list(s?.residents).flatMap((r: any) => {
    const id = token(r?.id)
    if (!id) return []
    const cast = o.person(id)
    const name = cleanText(r.name ?? cast?.name ?? '')
    if (!name) return []
    const generated = typeof r.name === 'string'
    return [{
      id,
      name,
      stage: typeof r.born === 'string' ? stageOf(r.born, today) : stageOfArt(cast?.art),
      ...(generated && typeof r.emoji === 'string' ? { emoji: cleanText(r.emoji, 16) } : {}),
      ...(generated && token(r.art) ? { art: r.art as string } : {}),
    }]
  })
  const visit = s?.visitor
  const visitorName = visit?.on === today && token(visit.id) ? o.person(visit.id)?.name : undefined
  const sky = s?.sky?.on === today && s.sky.sky && typeof s.sky.sky === 'object' ? s.sky : undefined
  const todayNum = dayOf(today)
  const year = Number(today.slice(0, 4))
  const festival = s
    ? o.festivals.flatMap(f => {
        const day = [year, year - 1].map(y => festivalDay(f, y)).find(d => todayNum >= d && todayNum <= d + FESTIVAL_GRACE_DAYS)
        if (day === undefined) return []
        const iso = isoOf(day)
        return [{ id: f.id, emoji: f.emoji, name: f.name, day: iso, late: todayNum - day, done: s.festivals?.[f.id] === iso }]
      })[0] ?? null
    : null
  return {
    v: TOWN_VERSION,
    town: o.town,
    date: today,
    age: s ? (AGES.includes(s.age) ? s.age : 'OGENJ') : null,
    founded_on: typeof s?.foundedOn === 'string' && !Number.isNaN(dayOf(s.foundedOn)) ? s.foundedOn.slice(0, 10) : null,
    villagers: s ? Math.max(0, int(s.villagers, 1)) : 0,
    buildings,
    projects,
    residents,
    visitor: visitorName ? { id: visit.id, name: visitorName } : null,
    sky: sky
      ? { scene: token(sky.scene) ?? '', rain: unit(sky.sky.rain), snow: unit(sky.sky.snow), fog: unit(sky.sky.fog), wind: unit(sky.sky.wind), gloom: unit(sky.sky.gloom), lightning: sky.sky.lightning === true }
      : null,
    festival,
    land: landOf(s?.land, 'seed'),
  }
}

/** Another town's public state as this bridge passes it on to its app: every field checked and cleaned again. */
export function cleanPublicState(p: any, id: string): PublicState | undefined {
  if (!p || typeof p !== 'object' || p.town?.id !== id) return undefined
  const list = (v: unknown) => (Array.isArray(v) ? v.slice(0, 200) : [])
  const today = typeof p.date === 'string' && !Number.isNaN(dayOf(p.date)) ? p.date.slice(0, 10) : ''
  const name = (n: unknown) => {
    const out: Record<string, string> = {}
    for (const [k, v] of Object.entries(n && typeof n === 'object' ? n : {})) if (LANGUAGE.test(k) && cleanText(v)) out[k] = cleanText(v)
    return out
  }
  const f = p.festival
  const sky = p.sky
  return {
    v: int(p.v, TOWN_VERSION),
    town: { id, ...cleanDisplay(p.town) },
    date: today,
    age: AGES.includes(p.age) ? p.age : null,
    founded_on: typeof p.founded_on === 'string' && !Number.isNaN(dayOf(p.founded_on)) ? p.founded_on.slice(0, 10) : null,
    villagers: Math.max(0, int(p.villagers, 0)),
    buildings: list(p.buildings).flatMap((b: any) => {
      const type = token(b?.type, /^[A-Z_]{1,32}$/)
      return type ? [{ type, plot: int(b.plot, -1), level: int(b.level, 1), damaged: b.damaged === true }] : []
    }),
    projects: Object.fromEntries(Object.entries(p.projects && typeof p.projects === 'object' ? p.projects : {}).filter(([k, n]) => token(k) && typeof n === 'number' && Number.isInteger(n) && n > 0).slice(0, 50)) as Record<string, number>,
    residents: list(p.residents).flatMap((r: any) => {
      const rid = token(r?.id)
      const n = cleanText(r?.name)
      if (!rid || !n) return []
      const stage = ['baby', 'child', 'youth', 'adult'].includes(r.stage) ? r.stage : 'adult'
      return [{ id: rid, name: n, stage, ...(typeof r.emoji === 'string' && cleanText(r.emoji, 16) ? { emoji: cleanText(r.emoji, 16) } : {}), ...(token(r.art) ? { art: r.art } : {}) }]
    }),
    visitor: token(p.visitor?.id) && cleanText(p.visitor?.name) ? { id: p.visitor.id, name: cleanText(p.visitor.name) } : null,
    sky: sky && typeof sky === 'object'
      ? { scene: token(sky.scene) ?? '', rain: unit(sky.rain), snow: unit(sky.snow), fog: unit(sky.fog), wind: unit(sky.wind), gloom: unit(sky.gloom), lightning: sky.lightning === true }
      : null,
    festival: f && token(f.id) && typeof f.day === 'string' && !Number.isNaN(dayOf(f.day))
      ? { id: f.id, emoji: cleanText(f.emoji, 16), name: name(f.name), day: f.day.slice(0, 10), late: Math.max(0, int(f.late, 0)), done: f.done === true }
      : null,
    land: landOf(p.land, 'map'),
  }
}
