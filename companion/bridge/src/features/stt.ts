// Speech recognition for the app: POST /stt sends a short recording to the local Whisper worker
// (companion/stt-local, 127.0.0.1:8796) and returns its transcript with word probabilities. The app
// asks in its target language (?language=it; the village's when it doesn't say), and the worker picks
// the model for it; GET /stt/status?language=it says whether the worker has one.
// A talk's take (?talk=1 with the conversation: said, words, names) is heard twice, with the conversation as the prompt
// and plain, and the readings weighed (stt-talk.ts, docs/plans/05-built/stt-talk.md).
// App token only (the family token gets 403). The audio stays in memory for the one request;
// nothing is written to disk here.
import type { Log } from '../config'
import type { FeatureFactory } from '../feature'
import { HttpError, json, readBody } from '../http'
import { normalizeForm } from '../lexicon'
import { meanProb, MIN_LEMMAS, talkPrompt, weigh, type Knows, type Snap, type TalkContext, type Voice } from '../stt-talk'

export type SttWord = { word: string; start: number | null; end: number | null; prob: number | null }
export type SttSegment = { start: number | null; end: number | null; text: string; words: SttWord[]; avg_logprob?: number; no_speech_prob?: number }
export type SttResult = { text: string; segments: SttSegment[]; language: string; duration: number | null; took?: number; model?: string; voice?: Voice }

export const STT_MAX_BYTES = 2 * 1024 * 1024
const MAX_PROMPT = 300

/** Container from the first bytes: m4a (MediaRecorder), ogg, webm, wav. */
export function sniffSpeech(b: Uint8Array): 'm4a' | 'ogg' | 'webm' | 'wav' | null {
  const ascii = (from: number, n: number) => String.fromCharCode(...b.subarray(from, from + n))
  if (b.length >= 12 && ascii(4, 4) === 'ftyp') return 'm4a'
  if (b.length >= 4 && ascii(0, 4) === 'OggS') return 'ogg'
  if (b.length >= 4 && b[0] === 0x1a && b[1] === 0x45 && b[2] === 0xdf && b[3] === 0xa3) return 'webm'
  if (b.length >= 12 && ascii(0, 4) === 'RIFF' && ascii(8, 4) === 'WAVE') return 'wav'
  return null
}

const num = (v: unknown) => (typeof v === 'number' && Number.isFinite(v) ? v : null)

/** Only the fields the app needs, in a fixed shape, whatever the worker sends. */
export function cleanResult(raw: any): SttResult | null {
  if (!raw || typeof raw.text !== 'string' || !Array.isArray(raw.segments)) return null
  return {
    text: raw.text.trim(),
    segments: raw.segments
      .filter((s: any) => s && typeof s === 'object')
      .map((s: any) => ({
        start: num(s.start),
        end: num(s.end),
        text: typeof s.text === 'string' ? s.text.trim() : '',
        words: (Array.isArray(s.words) ? s.words : [])
          .filter((w: any) => w && typeof w.word === 'string' && w.word.trim())
          .map((w: any) => ({ word: w.word.trim(), start: num(w.start), end: num(w.end), prob: num(w.prob) })),
        ...(num(s.avg_logprob) !== null ? { avg_logprob: s.avg_logprob } : {}),
        ...(num(s.no_speech_prob) !== null ? { no_speech_prob: s.no_speech_prob } : {}),
      })),
    language: typeof raw.language === 'string' ? raw.language : 'sl',
    duration: num(raw.duration),
    ...(num(raw.took) !== null ? { took: raw.took } : {}),
    ...(typeof raw.model === 'string' ? { model: raw.model } : {}),
    ...(raw.voice && num(raw.voice.voiced) !== null && num(raw.voice.loud) !== null ? { voice: { voiced: raw.voice.voiced, loud: raw.voice.loud } } : {}),
  }
}

/**
 * A talk's context from the query (the app's ?talk=1&said=…&words=…&names=…, each repeated): the character's last lines
 * (at most 3, 300 characters each), the scenario's phrases (12, 80 each) and the names (8, 40 each); null when the
 * request isn't a talk's.
 */
export function talkContextOf(url: URL): TalkContext | null {
  const q = url.searchParams
  if (q.get('talk') !== '1') return null
  const all = (k: string, n: number, len: number, last = false) => {
    const v = q.getAll(k).map(s => s.replace(/\s+/g, ' ').trim().slice(0, len)).filter(Boolean)
    return last ? v.slice(-n) : v.slice(0, n)
  }
  return { said: all('said', 3, 300, true), words: all('words', 12, 80), names: all('names', 8, 40) }
}

/** A talk's take as the app gets it: the reading for the draft, and what the tutor and the app's log get besides. */
export type TalkResult = SttResult & {
  alternatives: string[]
  /** Heard non-words one edit from a word of the conversation ({from, to}): an alternative has them matched. */
  near?: Snap[]
  sure: boolean
  noise: boolean
  by: 'both' | 'context' | 'plain' | null
  /** Both readings with their mean word probability, for the app's log. */
  readings: { by: 'context' | 'plain'; text: string; prob: number | null }[]
}

export type SttOptions = { url?: string; timeoutMs?: number; maxBytes?: number; log?: Log }

/** Client for the Whisper worker: GET /health, POST /transcribe. */
export class Stt {
  constructor(private readonly o: SttOptions = {}) {}

  get url() {
    return (this.o.url ?? 'http://127.0.0.1:8796').replace(/\/$/, '')
  }

  /**
   * The worker's /health, or why it can't be reached. With a [language]: whether the worker has a model for it (it
   * then loads it in the background, so the first recognition doesn't wait); a worker from before languages has one
   * multilingual model and answers for every language.
   */
  async status(language?: string): Promise<Record<string, unknown>> {
    try {
      const q = language ? `?language=${encodeURIComponent(language)}` : ''
      const r = await fetch(`${this.url}/health${q}`, { signal: AbortSignal.timeout(2_000) })
      const h = (await r.json().catch(() => null)) as Record<string, any> | null
      if (r.ok && h?.ok === true) {
        const lang = h.language && typeof h.language === 'object' ? h.language : null
        const out: Record<string, unknown> = { up: true, model: h.model, device: h.device, busy: h.busy === true, url: this.url }
        if (Array.isArray(h.models)) out.models = h.models
        if (!language) return out
        if (lang?.available === false) return { ...out, up: false, language, error: `no model for ${language} on the node` }
        return { ...out, language, ...(lang ? { language_model: lang.model, loaded: lang.loaded === true } : {}) }
      }
      return { up: false, loading: h?.loading === true, error: h?.error ?? `HTTP ${r.status}`, url: this.url }
    } catch {
      return { up: false, error: 'not running', url: this.url }
    }
  }

  /**
   * POST /stt?language=it&prompt=<expected>: the recording in, the transcript out. [fallback]: the language when none is
   * asked. A talk's take (?talk=1, [talkContextOf]) is heard with the conversation and plain ([talk]); [knows]: the
   * language's dictionary for its near-misses, when the bridge has one.
   */
  async transcribe(req: Request, url: URL, fallback = 'sl', knows: (language: string) => Knows = () => null): Promise<Response> {
    const max = this.o.maxBytes ?? STT_MAX_BYTES
    const language = (url.searchParams.get('language') ?? fallback).trim().toLowerCase() || fallback
    if (!/^[a-z]{2,3}$/.test(language)) throw new HttpError(400, 'language must be a code like "sl"')
    const prompt = (url.searchParams.get('prompt') ?? '').replace(/\s+/g, ' ').trim().slice(0, MAX_PROMPT)
    const audio = await readBody(req, max, `audio larger than ${max} bytes`)
    if (!audio.length) throw new HttpError(400, 'no audio')
    if (!sniffSpeech(audio)) throw new HttpError(415, 'not an audio recording (m4a, ogg, webm or wav)')
    const talk = talkContextOf(url)
    if (talk) return this.talk(audio, language, talk, knows(language))
    const r = await this.hear(audio, language, prompt)
    return r instanceof Response ? r : json(r.result)
  }

  /**
   * A talk's take ([TalkResult]): heard with the conversation as the prompt and plain, in one request to a worker that
   * takes plain=1 (the audio encoded once), else in two; the second hearing failing leaves the first alone. Then the
   * readings are weighed (stt-talk.ts weigh).
   */
  async talk(audio: Uint8Array<ArrayBuffer>, language: string, c: TalkContext, knows: Knows): Promise<Response> {
    const prompt = talkPrompt(c)
    const t0 = performance.now()
    const first = await this.hear(audio, language, prompt, !!prompt)
    if (first instanceof Response) return first
    let plain: SttResult | null = first.plain
    if (!plain && prompt) {
      const second = await this.hear(audio, language, '')
      plain = second instanceof Response ? null : second.result
    }
    if (!prompt) plain = first.result
    const h = weigh(first.result, plain, c, prompt, language, knows, first.result.voice ?? null)
    const { voice, ...result } = h.result
    const out: TalkResult = {
      ...result,
      text: h.text,
      took: Math.round(performance.now() - t0) / 1000,
      ...(voice ? { voice } : {}),
      alternatives: h.alternatives,
      ...(h.near.length ? { near: h.near } : {}),
      sure: h.sure,
      noise: h.noise,
      by: h.by,
      readings: [
        { by: 'context', text: first.result.text, prob: round(meanProb(first.result)) },
        ...(plain && prompt ? [{ by: 'plain' as const, text: plain.text, prob: round(meanProb(plain)) }] : []),
      ],
    }
    return json(out)
  }

  /** One request to the worker: its result (and its plain hearing, asked with [plain]), or the error response for the app. */
  private async hear(audio: Uint8Array<ArrayBuffer>, language: string, prompt: string, plain = false): Promise<{ result: SttResult; plain: SttResult | null } | Response> {
    const target = new URL(`${this.url}/transcribe`)
    target.searchParams.set('language', language)
    if (prompt) target.searchParams.set('prompt', prompt)
    if (plain) target.searchParams.set('plain', '1')
    let r: Response
    try {
      r = await fetch(target, {
        method: 'POST',
        headers: { 'content-type': 'application/octet-stream' },
        body: audio,
        signal: AbortSignal.timeout(this.o.timeoutMs ?? 30_000),
      })
    } catch (e) {
      const timeout = (e as Error).name === 'TimeoutError'
      this.o.log?.(`stt: ${timeout ? 'worker timed out' : `worker unreachable: ${(e as Error).message}`}`)
      return timeout ? json({ error: 'speech recognition timed out' }, 504) : json({ error: 'speech recognition is not running on the node', up: false }, 503)
    }
    const body = (await r.json().catch(() => null)) as any
    if (r.status === 503 && body?.error === 'not available') {
      return json({ error: `speech recognition has no model for ${language} on the node`, up: false, language }, 503)
    }
    if (r.status === 503) {
      return Response.json(
        { error: `speech recognition is ${body?.error === 'busy' ? 'busy' : 'starting'}`, up: false },
        { status: 503, headers: { 'retry-after': r.headers.get('retry-after') ?? '5' } },
      )
    }
    if (r.status >= 400 && r.status < 500) return json({ error: String(body?.error ?? `HTTP ${r.status}`) }, r.status)
    const out = r.ok ? cleanResult(body) : null
    if (!out) {
      this.o.log?.(`stt: worker answered HTTP ${r.status}`)
      return json({ error: 'speech recognition failed' }, 502)
    }
    // a worker that took plain=1 sends the second hearing along (its text and segments)
    const second = plain && body?.plain ? cleanResult({ ...body.plain, language: out.language, duration: out.duration, model: out.model }) : null
    return { result: out, plain: second }
  }
}

const round = (x: number | null) => (x === null ? null : Math.round(x * 1000) / 1000)

/** LANI_STT_URL (default http://127.0.0.1:8796), LANI_STT_TIMEOUT_MS (default 30000). */
export function sttFromEnv(env: Record<string, string | undefined>, log?: Log): Stt {
  return new Stt({ url: env.LANI_STT_URL || undefined, timeoutMs: env.LANI_STT_TIMEOUT_MS ? Number(env.LANI_STT_TIMEOUT_MS) : undefined, log })
}

/** A language code as the app sends it ("it"), or undefined. */
const languageOf = (url: URL) => {
  const l = url.searchParams.get('language')?.trim().toLowerCase()
  return l && /^[a-z]{2,3}$/.test(l) ? l : undefined
}

export const stt: FeatureFactory = ctx => {
  const worker = sttFromEnv(process.env, ctx.log)
  // The village's language when the app doesn't say (an app from before languages asks in Slovene).
  const village = ctx.culture.manifest?.language ?? 'sl'
  // a talk's near-misses are matched only with a whole dictionary of the language (Wiktionary's, MIN_LEMMAS or more:
  // Italian's 201k); with a small one (Slovene's 5.8k) a real word it lacks would look like a near-miss
  const knows = (language: string): Knows => {
    const d = ctx.dictionary
    if (!d || d.language !== language || d.dict.size < MIN_LEMMAS) return null
    const k = d.dict.knows()
    return w => k(normalizeForm(w, language))
  }
  return {
    routes: [
      { method: 'POST', path: '/stt', handle: ({ req, url }) => worker.transcribe(req, url, village, knows) },
      { method: 'GET', path: '/stt/status', handle: async ({ url }) => json(await worker.status(languageOf(url))) },
    ],
  }
}
