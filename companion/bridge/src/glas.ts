// The hosted voice service (glas, docs/plans/03-voice-service.md, "How the bridge changes") as an engine of the voice store: asked
// first, its clips kept in the store's cache like any other; when it can't, the store goes on to its local engines and then the
// phone. Off unless GLAS_URL and GLAS_KEY are both set: then nothing here runs and nothing else changes.
//
// What glas gets: the text of a line and the speaker. It decides where a line belongs: a line of our content goes to the library
// for every node, any other to the key's private namespace. So the node never marks a line as shared, and a line with a learner's
// name in it, which isn't in the corpus, can't reach the library from here. What the key may do decides the rest: with `generate`
// a missing line is made, on glas's account, within the key's daily quota and monthly cap; without it, the library is read and the
// node voices the rest itself. Someone's own designed voice ("@micka") is the node's: glas is never asked for it.
//
// A failure is never a learner's problem: whatever goes wrong is a null, and the next engine has its turn. glas out of reach, or
// a quota used up, keeps the store from asking for a while, so no line waits on a timeout.
import { baseVoice, carrierVoice, elevenlabsOf, personVoice } from './cast'
import { isMp3, isShortText, type Engine, type Take, type VoiceName } from './voice'

type Log = (m: string) => void

/** How long a live line may wait for glas: under the app's own 8 s, so a miss still leaves the phone time to speak. */
export const GLAS_WAIT_SECONDS = 6
/** How long a request to glas may take, beyond the wait; a download of a clip too. */
const REQUEST_MS = 4_000
/** glas out of reach, or answering 5xx: not asked again for this long. */
export const GLAS_BACKOFF_MS = 60_000
/** How long what glas says of its voices and of the key holds. */
const LIBRARY_MS = 3_600_000

/** A clip as POST /v1/clips/resolve and /say answer. */
type Result = {
  status: 'ready' | 'pending' | 'missing' | 'failed' | 'rejected'
  url?: string
  clip?: { hash: string; namespace: string; voice: string; engine?: string; model?: string }
  error?: { code: string; message: string }
}

export type GlasNameAnswer = {
  index: number
  status: 'ready' | 'pending' | 'missing' | 'rejected'
  clip?: { name: string; kind: string; phrase?: string; file: string; ms: number | null; engine_voice: string; model: string; url: string; expires_at: string }
  error?: { code: string; message: string }
}
export type GlasLineAnswer = {
  index: number
  status: 'ready' | 'missing' | 'rejected'
  manifest?: Record<string, unknown>
  pieces?: { part: string; text: string; file: string; ms: number; engine_voice: string; model: string; url: string; expires_at: string }[]
  error?: { code: string; message: string }
}

type Library = { at: number; speakers: Map<string, string>; generate: boolean }

export type GlasOptions = { url?: string; key?: string; language?: string; log?: Log; fetch?: typeof fetch; backoffMs?: number }

/** The start of the next UTC day, or the next UTC month: when a used-up quota comes back. */
const nextDay = (d = new Date()) => Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate() + 1)
const nextMonth = (d = new Date()) => Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 1)

export class Glas implements Engine {
  readonly name = 'glas' as const
  private readonly url?: string
  private readonly key?: string
  private readonly language: string
  private readonly get: typeof fetch
  private library?: Library
  /** Not asked until then, and why (for the status). */
  private pause?: { until: number; why: string }

  constructor(private readonly o: GlasOptions = {}) {
    this.url = o.url?.trim().replace(/\/+$/, '') || undefined
    this.key = o.key?.trim() || undefined
    this.language = o.language ?? 'sl'
    this.get = o.fetch ?? fetch
  }

  get configured(): boolean {
    return !!this.url && !!this.key
  }

  private stop(ms: number, why: string) {
    if (!this.pause || this.pause.why !== why) this.o.log?.(`voice: glas: ${why}: not asked until ${new Date(Date.now() + ms).toISOString()}`)
    this.pause = { until: Date.now() + ms, why }
  }

  private async api(method: string, path: string, body?: unknown, waitSeconds = 0): Promise<Response> {
    return this.get(`${this.url}${path}`, {
      method,
      headers: { authorization: `Bearer ${this.key}`, ...(body !== undefined ? { 'content-type': 'application/json' } : {}) },
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(REQUEST_MS + waitSeconds * 1000),
    })
  }

  /** What glas says of the speakers of the language (speaker → the ElevenLabs voice it is in) and of the key. */
  private async known(lang: string): Promise<Library | null> {
    if (this.library && lang === this.language && Date.now() - this.library.at < LIBRARY_MS) return this.library
    const [voices, characters, usage] = await Promise.all([this.api('GET', '/v1/voices'), this.api('GET', `/v1/characters?lang=${lang}`), this.api('GET', '/v1/usage')])
    if (!voices.ok || !characters.ok || !usage.ok) {
      this.failed(voices.ok ? (characters.ok ? usage : characters) : voices)
      return null
    }
    const ids = new Map(((await voices.json()) as { voices: { id: string; provider_id: string }[] }).voices.map(v => [v.id, v.provider_id]))
    const speakers = new Map(((await characters.json()) as { characters: { archetype: string; voice: string }[] }).characters.map(c => [c.archetype, ids.get(c.voice) ?? '']))
    const generate = ((await usage.json()) as { key: { scopes: string[] } }).key.scopes.includes('generate')
    const library = { at: Date.now(), speakers, generate }
    if (lang === this.language) this.library = library
    return library
  }

  private failed(r: Response) {
    if (r.status === 401) this.stop(LIBRARY_MS, 'the key is refused (GLAS_KEY)')
    else this.stop(this.o.backoffMs ?? GLAS_BACKOFF_MS, `HTTP ${r.status}`)
  }

  /** Whether it may be asked now. Nothing is sent: a line never waits for this. */
  async ready(_chars: number, _live: boolean): Promise<boolean> {
    if (!this.configured) return false
    if (this.pause && Date.now() >= this.pause.until) this.pause = undefined
    return !this.pause
  }

  /** The speaker in glas for [voice]: the store's "female.sl" is glas's "female" in Slovene; none for someone's own voice. */
  speakerOf(voice: VoiceName): string | undefined {
    return personVoice(voice) ? undefined : baseVoice(voice)
  }

  async synth(text: string, voice: VoiceName, o: { live?: boolean; lang?: string; urgent?: boolean; carrier?: boolean } = {}): Promise<Take | null> {
    const speaker = this.speakerOf(voice)
    if (!speaker || !this.configured) return null
    const lang = o.lang ?? this.language
    try {
      const lib = await this.known(lang)
      if (!lib) return null
      // the voice this node's cast has for the speaker, and glas's, must be one: a line is never said by someone else
      const theirs = lib.speakers.get(speaker)
      if (!theirs || theirs !== elevenlabsOf(voice)) return null
      // a lone word is said right only inside a carrier sentence, which glas doesn't make: it gives one it has, and the node's
      // own engines make the rest
      const make = lib.generate && !(isShortText(text) && (o.carrier || carrierVoice(voice)))
      const item = { lang, text, voice: speaker, ...(make ? {} : { make: false }) }
      const wait = o.live && make ? GLAS_WAIT_SECONDS : 0
      const r = await this.ask(item, wait)
      if (r?.status === 'rejected') this.refused(r)
      if (r?.status !== 'ready' || !r.url) return null
      return await this.take(r, theirs)
    } catch (e) {
      this.stop(this.o.backoffMs ?? GLAS_BACKOFF_MS, `unreachable (${(e as Error).name})`)
      return null
    }
  }

  private async ask(item: { lang: string; text: string; voice: string; make?: boolean }, wait: number): Promise<Result | null> {
    const res = wait ? await this.api('POST', '/v1/clips/say', { ...item, wait_seconds: wait }, wait) : await this.api('POST', '/v1/clips/resolve', { items: [item] })
    if (!res.ok) {
      this.failed(res)
      return null
    }
    const body = (await res.json()) as Result | { items: Result[] }
    return 'items' in body ? (body.items[0] ?? null) : body
  }

  private refused(r: Result) {
    if (r.error?.code === 'quota_exceeded') this.stop(nextDay() - Date.now(), "the key's daily quota is used up")
    else if (r.error?.code === 'monthly_quota_exceeded') this.stop(nextMonth() - Date.now(), "the key's monthly cap is used up")
    else this.o.log?.(`voice: glas refused a line: ${r.error?.code ?? 'no reason given'}`)
  }

  /** The clip's bytes, for the store's cache. Costs the node nothing, so it counts no characters. */
  private async take(r: Result, voice: string): Promise<Take | null> {
    const res = await this.get(r.url!, { signal: AbortSignal.timeout(REQUEST_MS) })
    if (!res.ok) return null
    const bytes = new Uint8Array(await res.arrayBuffer())
    if (!isMp3(bytes)) return null
    return { bytes, chars: 0, voice, model: r.clip?.model, method: 'plain', raw: r.clip?.engine !== 'import' }
  }

  // --- the shared name library (docs/plans/05-built/audio-names.md, "The contract"): what a node reads and adds -----------------
  //
  // The whole key of a name clip is (language, voice, name, kind), so what is sent here says who asks no more than the key does.
  // Someone's own voice stays on the node: glas refuses it.

  private async shelf<T>(path: string, body: unknown): Promise<T | null> {
    if (!(await this.ready(0, false))) return null
    try {
      const r = await this.api('POST', path, body)
      if (r.ok) return (await r.json()) as T
      if (r.status >= 500) this.failed(r)
      else this.o.log?.(`voice: glas: ${path}: HTTP ${r.status}`)
    } catch (e) {
      this.stop(this.o.backoffMs ?? GLAS_BACKOFF_MS, `unreachable (${(e as Error).name})`)
    }
    return null
  }

  /** Name clips, each ready with a signed URL, or why not; null when glas can't be asked now. */
  nameClips(items: { language: string; voice: string; name: string; kind: string; make?: boolean }[]) {
    return this.shelf<{ items: GlasNameAnswer[] }>('/v1/names/resolve', { items })
  }

  /** Lines' shared manifests and pieces; null when glas can't be asked now. */
  lines(items: { language: string; voice: string; line: string }[]) {
    return this.shelf<{ items: GlasLineAnswer[] }>('/v1/lines/resolve', { items })
  }

  /** A name clip made here, for everyone of that name (a key with `publish`). */
  addNameClip(clip: { language: string; voice: string; name: string; kind: string; phrase?: string; ms: number; engine_voice: string; model: string }, audio: Uint8Array) {
    return this.shelf<{ status: 'added' | 'kept' }>('/v1/names', { ...clip, audio: Buffer.from(audio).toString('base64') })
  }

  /** A line's manifest and pieces made here, for everyone (a key with `publish`). */
  addLine(manifest: unknown, pieces: { part: 'head' | 'tail'; text: string; file: string; ms: number; engine_voice: string; model: string; audio: Uint8Array }[]) {
    return this.shelf<{ status: 'added' }>('/v1/lines', { manifest, pieces: pieces.map(p => ({ ...p, audio: Buffer.from(p.audio).toString('base64') })) })
  }

  async status() {
    return { configured: this.configured, url: this.url ?? null, ...(this.pause ? { paused_until: new Date(this.pause.until).toISOString(), why: this.pause.why } : {}) }
  }
}

/** The engine from the environment: GLAS_URL and GLAS_KEY, both or it stays off. */
export function glasFromEnv(env: Record<string, string | undefined>, log?: Log, language = 'sl'): Glas {
  return new Glas({ url: env.GLAS_URL, key: env.GLAS_KEY, language, log })
}
