// The tutor's outside dictionaries (docs/plans/05-built/tutor-reach.md): when Lani's lexicon has nothing on a word, or the
// tutor asks for more (dictionary_lookup's `deep`, GET /dictionary), the bridge asks that language's sources, listed in
// SOURCES below: the one place to add one. Each site's terms were read first; the doc quotes them. A site whose terms
// forbid automated lookups isn't here (Treccani, Duden, Cambridge), and a row that needs the site's written consent
// (`consent`) stays off until the node names its host in LANI_DICTIONARY_CONSENT.
//
// What goes out is the word and nothing else: no line, no learner, no cookie, and a thing that isn't a word stays home
// (sendable). Only GETs, only to a host of the table over https (a redirect elsewhere is refused), one request at a time
// per host and seconds apart across all of the node's bridges (usage.json, under a lock), a daily cap per host, a timeout
// and a size limit. A word is cached on the node per language and source, so it is fetched once for all its learners.
// A failure comes back as a plain reason, in words of our own, and the turn goes on.
//
// A page is untrusted: `clean` takes out every angle bracket and invisible character, so nothing in it can close the
// `<untrusted_dictionary_content>` block the app wraps it in, and nothing here acts on what a page says.
//
// LANI_DICTIONARY_SOURCE: "off" for the lexicon alone, or a loopback URL for a fake (test/fakes/dictionary.ts): the smoke
// and the dev bridge use these, and never reach a real site.
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import type { Log } from './config'
import { lexiconCacheDir } from './lexicon-download'
import { withLock } from './pairing'

/** One outside source of a language. [request] and [page] are paths on [host]; [read] gives the entry's text, or null for no entry. */
export type Source = {
  /** What the tutor cites. */
  name: string
  host: string
  /** The licence of what it returns, cited with it. */
  licence: string
  /** Asked only once the node lists [host] in LANI_DICTIONARY_CONSENT: the site's terms want its written consent. */
  consent?: boolean
  /** A day's requests to [host] from this node, and the seconds' gap between two. */
  perDay: number
  gapMs: number
  request: (word: string) => string
  page: (word: string) => string
  read: (body: string, language: string) => string | null
}

/** The name of a language's section on English Wiktionary. */
const WIKTIONARY_SECTIONS: Record<string, string> = { sl: 'Slovene', it: 'Italian', de: 'German', en: 'English' }

/** What a Wiktionary section keeps out: not the word's meaning or class. */
const WIKTIONARY_DROPS = /^(etymology|pronunciation|references|further reading|anagrams|see also|descendants|translations)\b/i

/** [language]'s section of a Wiktionary page as plain text (`exsectionformat=wiki`), its headings as plain lines; null when the page has none. */
export function wiktionarySection(extract: string, language: string): string | null {
  const name = WIKTIONARY_SECTIONS[language]
  if (!name) return null
  const lines = extract.split('\n').map(l => l.trim())
  const start = lines.indexOf(`== ${name} ==`)
  if (start < 0) return null
  const out: string[] = []
  let dropped = false
  let title: string | undefined // a heading waits for its text: a table's section has none left
  for (const line of lines.slice(start + 1)) {
    if (/^==[^=].*==$/.test(line)) break // the next language
    const heading = /^={3,}\s*(.+?)\s*={3,}$/.exec(line)?.[1]
    if (heading) {
      dropped = WIKTIONARY_DROPS.test(heading)
      title = dropped ? undefined : `${heading}:`
    } else if (!dropped && line) {
      if (title) out.push(title)
      title = undefined
      out.push(line)
    }
  }
  return out.length ? out.join('\n') : null
}

/** A page's readable text: without scripts, styles, navigation and tags, its entities decoded (before `clean` runs: "&lt;" is a bracket too). */
export function pageText(html: string): string {
  const body = /<body[^>]*>([\s\S]*)<\/body>/i.exec(html)?.[1] ?? html
  const main = /<main[^>]*>([\s\S]*)<\/main>/i.exec(body)?.[1] ?? body
  return main
    .replace(/<(script|style|nav|header|footer|form|noscript)\b[^>]*>[\s\S]*?<\/\1>/gi, ' ')
    .replace(/<br\s*\/?>|<\/(p|div|li|h\d|tr)>/gi, '\n')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/g, ' ').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'")
    .replace(/&#x([0-9a-f]+);/gi, (_, h: string) => codePoint(Number.parseInt(h, 16)))
    .replace(/&#(\d+);/g, (_, d: string) => codePoint(Number.parseInt(d, 10)))
    .replace(/&amp;/g, '&')
}

const codePoint = (n: number) => (n > 0 && n <= 0x10ffff ? String.fromCodePoint(n) : '')

const WIKTIONARY: Source = {
  name: 'Wiktionary',
  host: 'en.wiktionary.org',
  licence: 'CC BY-SA 4.0',
  perDay: 200,
  gapMs: 3_000,
  request: w => `/w/api.php?action=query&format=json&formatversion=2&prop=extracts&explaintext=1&exsectionformat=wiki&redirects=1&titles=${encodeURIComponent(w)}`,
  page: w => `/wiki/${encodeURIComponent(w)}`,
  read: (body, language) => {
    const page = (JSON.parse(body) as { query?: { pages?: { missing?: boolean; extract?: unknown }[] } }).query?.pages?.[0]
    return page && !page.missing && typeof page.extract === 'string' ? wiktionarySection(page.extract, language) : null
  },
}

const DWDS: Source = {
  name: 'DWDS',
  host: 'www.dwds.de',
  licence: 'BBAW, cited as its terms ask',
  perDay: 100,
  gapMs: 3_000,
  // the published API only (https://www.dwds.de/d/api): an entry's existence, its spelling and word class; never its pages
  request: w => `/api/wb/snippet/?q=${encodeURIComponent(w)}`,
  page: w => `/wb/${encodeURIComponent(w)}`,
  read: body => {
    const list = JSON.parse(body) as { lemma?: unknown; wortart?: unknown }[]
    const lines = Array.isArray(list) ? list.filter(e => typeof e?.lemma === 'string').map(e => `${e.lemma}: ${typeof e.wortart === 'string' ? e.wortart : 'word class not given'}`) : []
    return lines.length ? lines.join('\n') : null
  },
}

const FRAN: Source = {
  name: 'Fran (fran.si)',
  host: 'fran.si',
  licence: 'ZRC SAZU, all rights reserved',
  consent: true,
  perDay: 30,
  gapMs: 5_000,
  request: w => `/iskanje?View=1&Query=${encodeURIComponent(w)}`,
  page: w => `/iskanje?View=1&Query=${encodeURIComponent(w)}`,
  read: body => pageText(body).trim() || null,
}

/** The sources per language, asked together (each host keeps its own pace). A language not here has the lexicon alone. */
export const SOURCES: Record<string, Source[]> = {
  sl: [FRAN, WIKTIONARY],
  it: [WIKTIONARY],
  de: [DWDS, WIKTIONARY],
  en: [WIKTIONARY],
}

/** The hosts a request may go to: the table's, nothing else. */
const TABLE_HOSTS = new Set(Object.values(SOURCES).flat().map(s => s.host))

export const USER_AGENT = 'LaniDictionaryBot/0.1 (a language tutor looking up one word at a time; https://github.com/fentas/lani)'

/** How long a request may take, how long a turn waits for its turn at a host, what a page may weigh, how long an entry's text may be. */
export const TIMEOUT_MS = 8_000
export const WAIT_MS = 10_000
export const MAX_BYTES = 1_000_000
export const MAX_TEXT = 3_000
const MAX_REDIRECTS = 2
/** How long an entry is kept, and an entry not found: a dictionary changes slowly, a missing word may come. */
const KEEP_MS = 180 * 86_400_000
const KEEP_MISSING_MS = 7 * 86_400_000
/** A host that says it's overloaded (429, 503) rests at least this long. */
const REST_MS = 10 * 60_000

/** A word or a short set phrase: letters and marks, at most four parts. A line of text, a number or an address stays home. */
const WORD = /^[\p{L}\p{M}]+(?:['’. -][\p{L}\p{M}]+){0,3}$/u

/** Why [word] doesn't go out, or null when it may: the only thing that leaves is a word, never the learner's name. */
export function unsendable(word: string, learner?: string): string | null {
  if (word.length > 40 || !WORD.test(word)) return 'not sent: only a single word or a short set phrase goes out'
  const names = new Set(learner?.toLocaleLowerCase().split(/\s+/).filter(n => n.length > 1))
  if (word.toLocaleLowerCase().split(/[' -]/).some(w => names.has(w))) return "not sent: that is the learner's name"
  return null
}

/**
 * [text] made safe to read inside a marked block, at most [max] characters: no angle bracket (no tag can open or close),
 * no control, invisible or direction character, no empty line.
 */
export function clean(text: string, max = MAX_TEXT): string {
  const out = text
    .replace(/</g, '‹')
    .replace(/>/g, '›')
    .replace(/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/gu, c => (c === '\n' || c === '\t' ? c : ''))
    .split('\n')
    .map(l => l.replace(/\s+/g, ' ').trim())
    .filter(Boolean)
    .join('\n')
  return out.length > max ? `${out.slice(0, max)} …` : out
}

export type Found = { source: string; url: string; licence: string; text: string; cached: boolean; at: string }
export type OutsideAnswer = {
  language: string
  /** What the sources have. */
  found: Found[]
  /** The sources that were asked and have no entry. */
  none: { source: string; url: string }[]
  /** The sources that couldn't be asked, and why, in our own words. */
  failed: { source: string; why: string }[]
  /** Why nothing was asked at all (no source for the language, a thing that isn't a word, lookups off). */
  note?: string
}

type Cached = { source: string; word: string; url: string; text: string | null; at: number }
type Usage = Record<string, { day: string; count: number; next: number }>
type Asked = { found?: Found; none?: { source: string; url: string }; failed?: { source: string; why: string } }
type Fetcher = (url: string, init: RequestInit & { maxBytes?: number }) => Promise<Response>

/** A request the bridge won't make, or a page it won't take: the reason is ours, never the site's text. */
class Refused extends Error {}

export type DictionaryOptions = {
  /** The node's cache and its usage. */
  dir: string
  log?: Log
  /** "off": nothing is asked. A loopback URL: a fake, in place of every host. Else the table's hosts. */
  source?: string
  /** The hosts of `consent` rows the node has written consent for. */
  consent?: Iterable<string>
  fetcher?: Fetcher
  now?: () => number
  sleep?: (ms: number) => Promise<unknown>
  table?: Record<string, Source[]>
}

/** Where the node keeps what it fetched: LANI_DICTIONARY_CACHE, else beside the lexicon's cache: for all its learners. */
export const dictionaryDir = (env: Record<string, string | undefined> = process.env) => env.LANI_DICTIONARY_CACHE || join(lexiconCacheDir(env), 'outside')

export class OutsideDictionaries {
  private readonly table: Record<string, Source[]>
  private readonly now: () => number
  private readonly sleep: (ms: number) => Promise<unknown>
  private readonly fetcher: Fetcher
  private readonly consent: Set<string>
  private readonly off: boolean
  private readonly fake?: URL
  /** Per host, the end of the queue: one request at a time in this process. */
  private readonly queues = new Map<string, Promise<unknown>>()
  /** A word being fetched: the same word asked again waits for that answer. */
  private readonly flying = new Map<string, Promise<Asked>>()

  constructor(private readonly o: DictionaryOptions) {
    this.table = o.table ?? SOURCES
    this.now = o.now ?? Date.now
    this.sleep = o.sleep ?? (ms => new Promise(r => setTimeout(r, ms)))
    this.fetcher = o.fetcher ?? ((url, init) => fetch(url, init))
    this.consent = new Set(o.consent ?? [])
    const fake = o.source && o.source !== 'off' ? loopback(o.source) : undefined
    if (o.source && o.source !== 'off' && !fake) o.log?.('LANI_DICTIONARY_SOURCE: neither "off" nor a loopback URL: outside lookups are off')
    this.fake = fake
    this.off = !!o.source && !fake
  }

  /** The node's dictionaries, as its environment sets them. */
  static fromEnv(env: Record<string, string | undefined> = process.env, log?: Log): OutsideDictionaries {
    const consent = (env.LANI_DICTIONARY_CONSENT ?? '').split(',').map(h => h.trim().toLowerCase()).filter(Boolean)
    return new OutsideDictionaries({ dir: dictionaryDir(env), log, source: env.LANI_DICTIONARY_SOURCE?.trim() || undefined, consent })
  }

  /** The sources [language] is asked here: the table's, without those still waiting for their consent. */
  sourcesOf(language: string): Source[] {
    return (this.table[language] ?? []).filter(s => !s.consent || this.consent.has(s.host))
  }

  /** What [language]'s sources say of [word]; never throws: what goes wrong is in the answer. */
  async look(word: string, language: string, o: { learner?: string } = {}): Promise<OutsideAnswer> {
    const empty = { language, found: [], none: [], failed: [] }
    if (this.off) return { ...empty, note: 'outside dictionaries are off on this node' }
    const w = normal(word, language)
    const why = unsendable(w, o.learner)
    if (why) return { ...empty, note: why }
    const sources = this.sourcesOf(language)
    if (!sources.length) return { ...empty, note: `no outside dictionary for this language` }
    const asked = await Promise.all(sources.map(s => this.ask(s, w, language)))
    return {
      language,
      found: asked.flatMap(a => (a.found ? [a.found] : [])),
      none: asked.flatMap(a => (a.none ? [a.none] : [])),
      failed: asked.flatMap(a => (a.failed ? [a.failed] : [])),
    }
  }

  private ask(s: Source, word: string, language: string): Promise<Asked> {
    const held = this.fromCache(s, word, language)
    if (held) return Promise.resolve(held)
    const key = `${language}\u0000${s.host}\u0000${word}`
    const going = this.flying.get(key)
    if (going) return going
    const p = this.inTurn(s.host, () => this.fetched(s, word, language)).finally(() => this.flying.delete(key))
    this.flying.set(key, p)
    return p
  }

  /** [f] once the queue of [host] has gone by: one at a time, whatever the others' outcome. */
  private inTurn<T>(host: string, f: () => Promise<T>): Promise<T> {
    const turn = (this.queues.get(host) ?? Promise.resolve()).then(f, f)
    this.queues.set(host, turn.catch(() => undefined))
    return turn
  }

  private async fetched(s: Source, word: string, language: string): Promise<Asked> {
    const failed = (why: string): Asked => ({ failed: { source: s.name, why } })
    const url = `https://${s.host}${s.page(word)}`
    let claimed: Claim
    try {
      claimed = await this.claim(s)
    } catch (e) {
      this.o.log?.(`dictionary: ${s.host}: ${(e as Error).message}`)
      return failed('busy just now: try again later')
    }
    if (claimed !== 'go') return failed(claimed === 'cap' ? "the day's limit for this source is reached: try again tomorrow" : 'busy just now: try again later')
    // another bridge of the node may have fetched it while this one waited
    const held = this.fromCache(s, word, language)
    if (held) {
      this.release(s)
      return held
    }
    let rest = 0
    try {
      const r = await this.get(s, s.request(word))
      if (r.status === 429 || r.status === 503) {
        rest = Math.max(REST_MS, retryAfter(r.retryAfter))
        return failed(`it asks for a rest (HTTP ${r.status})`)
      }
      if (r.status === 404) return this.keep(s, word, language, url, null)
      if (r.status < 200 || r.status > 299) return failed(`HTTP ${r.status}`)
      return this.keep(s, word, language, url, s.read(r.body, language))
    } catch (e) {
      if (e instanceof Refused) return failed(e.message)
      if ((e as Error).name === 'TimeoutError' || (e as Error).name === 'AbortError') return failed('timed out')
      if (/over \d+ bytes/.test((e as Error).message)) return failed('its answer is too large') // the phone's fetch stopped reading
      if (e instanceof SyntaxError) return failed("its answer couldn't be read")
      return failed('it could not be reached')
    } finally {
      this.release(s, rest)
    }
  }

  /** Keeps what was found ([text], null: no entry) and says so. */
  private keep(s: Source, word: string, language: string, url: string, text: string | null): Asked {
    const said = text === null ? null : clean(text)
    const at = this.now()
    const c: Cached = { source: s.name, word, url, text: said || null, at }
    this.write(this.cacheFile(s, word, language), c)
    return answered(c, s, false)
  }

  private fromCache(s: Source, word: string, language: string): Asked | null {
    const c = this.read<Cached>(this.cacheFile(s, word, language))
    if (!c || this.now() - c.at > (c.text === null ? KEEP_MISSING_MS : KEEP_MS)) return null
    return answered(c, s, true)
  }

  private cacheFile(s: Source, word: string, language: string): string {
    return join(this.o.dir, language, `${createHash('sha256').update(`${s.host}\u0000${word}`).digest('hex')}.json`)
  }

  /**
   * The next request to [s]'s host, across the node's bridges: its place taken (`go`, until [release] or a timeout's
   * length), the day's cap reached, or `busy` when a turn would wait too long.
   */
  private async claim(s: Source): Promise<Claim> {
    const until = this.now() + WAIT_MS
    for (;;) {
      const wait = this.locked(u => {
        const day = new Date(this.now()).toISOString().slice(0, 10)
        const h = u[s.host]?.day === day ? u[s.host] : { day, count: 0, next: u[s.host]?.next ?? 0 }
        if (h.count >= s.perDay) return 'cap' as const
        if (this.now() < h.next) return h.next - this.now()
        u[s.host] = { day, count: h.count + 1, next: this.now() + TIMEOUT_MS + s.gapMs }
        return 'go' as const
      })
      if (typeof wait !== 'number') return wait
      if (this.now() + wait > until) return 'busy'
      await this.sleep(wait)
    }
  }

  /** The request is over: the host's next one [rest] or its gap from now. */
  private release(s: Source, rest = 0) {
    try {
      this.locked(u => {
        if (u[s.host]) u[s.host].next = this.now() + Math.max(rest, s.gapMs)
      })
    } catch (e) {
      this.o.log?.(`dictionary: ${s.host}: ${(e as Error).message}`)
    }
  }

  private locked<T>(f: (u: Usage) => T): T {
    const file = join(this.o.dir, 'usage.json')
    mkdirSync(this.o.dir, { recursive: true })
    return withLock(`${file}.lock`, () => {
      const u = this.read<Usage>(file) ?? {}
      const r = f(u)
      this.write(file, u)
      return r
    })
  }

  /** The one place a request is made: a GET, to a host of the table, with nothing of the learner's. */
  private async get(s: Source, path: string): Promise<{ status: number; body: string; retryAfter: string | null }> {
    let url = this.fake ? new URL(`/${s.host}${path}`, this.fake) : new URL(`https://${s.host}${path}`)
    for (let hops = 0; ; hops++) {
      if (!this.reachable(url)) throw new Refused(`refused: ${url.hostname} is not one of the table's hosts`)
      const r = await this.fetcher(url.href, {
        method: 'GET',
        redirect: 'manual',
        headers: { 'user-agent': USER_AGENT, accept: 'application/json, text/html;q=0.9' },
        signal: AbortSignal.timeout(TIMEOUT_MS),
        maxBytes: MAX_BYTES,
      })
      if (r.status < 300 || r.status > 399) return { status: r.status, body: await capped(r), retryAfter: r.headers.get('retry-after') }
      const to = r.headers.get('location')
      if (!to || hops >= MAX_REDIRECTS) throw new Refused('too many redirects')
      url = new URL(to, url)
    }
  }

  /** Only the table's hosts over https, no credentials, no other port; with a fake, only the fake. */
  private reachable(url: URL): boolean {
    if (this.fake) return url.origin === this.fake.origin
    return url.protocol === 'https:' && TABLE_HOSTS.has(url.hostname) && !url.port && !url.username && !url.password
  }

  private read<T>(file: string): T | null {
    try {
      return existsSync(file) ? (JSON.parse(readFileSync(file, 'utf8')) as T) : null
    } catch {
      return null
    }
  }

  private write(file: string, value: unknown) {
    try {
      mkdirSync(dirname(file), { recursive: true })
      writeFileSync(`${file}.part`, JSON.stringify(value))
      renameSync(`${file}.part`, file)
    } catch (e) {
      this.o.log?.(`dictionary: not kept: ${(e as Error).message}`) // the answer still goes back, the word is fetched again next time
    }
  }
}

type Claim = 'go' | 'cap' | 'busy'

/** [word] as it's sent and kept: one form of its letters, its spaces single; a capital of a sentence's start gone, but in German. */
function normal(word: string, language: string): string {
  const w = word.normalize('NFC').trim().replace(/\s+/g, ' ')
  return language !== 'de' && /^\p{Lu}\p{Ll}*$/u.test(w) ? w.toLocaleLowerCase() : w
}

function answered(c: Cached, s: Source, cached: boolean): Asked {
  if (c.text === null) return { none: { source: s.name, url: c.url } }
  return { found: { source: s.name, url: c.url, licence: s.licence, text: c.text, cached, at: new Date(c.at).toISOString().slice(0, 10) } }
}

function loopback(s: string): URL | undefined {
  try {
    const u = new URL(s)
    return u.protocol === 'http:' && (u.hostname === '127.0.0.1' || u.hostname === 'localhost') ? u : undefined
  } catch {
    return undefined
  }
}

/** A Retry-After in seconds, as milliseconds, at most a day; a date or nothing: 0. */
function retryAfter(v: string | null): number {
  const s = Number(v)
  return Number.isFinite(s) && s > 0 ? Math.min(s, 86_400) * 1000 : 0
}

/** [r]'s body as text, refused past [MAX_BYTES] without reading the rest. */
async function capped(r: Response): Promise<string> {
  if (Number(r.headers.get('content-length') ?? 0) > MAX_BYTES) {
    await r.body?.cancel?.().catch(() => undefined)
    throw new Refused('its answer is too large')
  }
  const reader = r.body?.getReader?.()
  if (!reader) {
    // the phone's fetch has the answer whole, cut at [MAX_BYTES] by the host (maxBytes)
    const b = new Uint8Array(await r.arrayBuffer())
    if (b.length > MAX_BYTES) throw new Refused('its answer is too large')
    return new TextDecoder().decode(b)
  }
  const parts: Uint8Array[] = []
  let size = 0
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    size += value.length
    if (size > MAX_BYTES) {
      await reader.cancel().catch(() => undefined)
      throw new Refused('its answer is too large')
    }
    parts.push(value)
  }
  const all = new Uint8Array(size)
  let at = 0
  for (const p of parts) {
    all.set(p, at)
    at += p.length
  }
  return new TextDecoder().decode(all)
}
