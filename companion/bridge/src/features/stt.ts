// Speech recognition for the app: POST /stt sends a short recording to the local Whisper worker
// (companion/stt-local, 127.0.0.1:8796) and returns its transcript with word probabilities. The app
// asks in its target language (?language=it; the village's when it doesn't say), and the worker picks
// the model for it; GET /stt/status?language=it says whether the worker has one.
// App token only (the family token gets 403). The audio stays in memory for the one request;
// nothing is written to disk here.
import type { Log } from '../config'
import type { FeatureFactory } from '../feature'
import { HttpError, json, readBody } from '../http'

export type SttWord = { word: string; start: number | null; end: number | null; prob: number | null }
export type SttSegment = { start: number | null; end: number | null; text: string; words: SttWord[] }
export type SttResult = { text: string; segments: SttSegment[]; language: string; duration: number | null; took?: number; model?: string }

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
      })),
    language: typeof raw.language === 'string' ? raw.language : 'sl',
    duration: num(raw.duration),
    ...(num(raw.took) !== null ? { took: raw.took } : {}),
    ...(typeof raw.model === 'string' ? { model: raw.model } : {}),
  }
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

  /** POST /stt?language=it&prompt=<expected>: the recording in, the transcript out. [fallback]: the language when none is asked. */
  async transcribe(req: Request, url: URL, fallback = 'sl'): Promise<Response> {
    const max = this.o.maxBytes ?? STT_MAX_BYTES
    const language = (url.searchParams.get('language') ?? fallback).trim().toLowerCase() || fallback
    if (!/^[a-z]{2,3}$/.test(language)) throw new HttpError(400, 'language must be a code like "sl"')
    const prompt = (url.searchParams.get('prompt') ?? '').replace(/\s+/g, ' ').trim().slice(0, MAX_PROMPT)
    const audio = await readBody(req, max, `audio larger than ${max} bytes`)
    if (!audio.length) throw new HttpError(400, 'no audio')
    if (!sniffSpeech(audio)) throw new HttpError(415, 'not an audio recording (m4a, ogg, webm or wav)')

    const target = new URL(`${this.url}/transcribe`)
    target.searchParams.set('language', language)
    if (prompt) target.searchParams.set('prompt', prompt)
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
    return json(out)
  }
}

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
  return {
    routes: [
      { method: 'POST', path: '/stt', handle: ({ req, url }) => worker.transcribe(req, url, village) },
      { method: 'GET', path: '/stt/status', handle: async ({ url }) => json(await worker.status(languageOf(url))) },
    ],
  }
}
