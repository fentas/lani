// Voice store: natural Slovene audio for the app, made once on the node and cached.
// Engines: ElevenLabs (eleven_v3, the only model with Slovenian; quota-guarded) and the local
// Gepard worker on 127.0.0.1:8795. Clips live in the voice cache, {voice.db,files/<sha1>.mp3}: $LANI_VOICE_CACHE
// (~/.cache/lani/voice after lani-setup), else <data>/app/voice (paths.ts voiceDir).
// A clip is keyed by its speaker from the voice cast (cast.ts): the narrators female/male or a
// character voice ("grandma"). A narrator says a lone word inside a carrier sentence, cut out by its
// timestamps (CARRIERS); their clips from before that are made again as the app asks for them. A voice the cast
// marks `denoise` (a designed voice that hisses) has its clips filtered before they are stored (DENOISE), and every
// clip is brought to one loudness (LEVEL).
import { Database } from 'bun:sqlite'
import { createHash, randomUUID } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, renameSync, unlinkSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { renderDeep, type Addressee } from './addressee'
import { archetypeOf, carrierVoice, cast, castInfo, denoiseOf, denoiseVoices, elevenlabsOf, fallbacks, genderOf, isNarrator, isVoice, personVoice, speakerFor, type DenoisePreset } from './cast'
import { drillTexts, type Drill } from './drills'
import { normalizeText, phrases } from './family'
import { ispyTexts, type ISpyBook, type ISpyLines } from './ispy'
import type { GrammarPage } from './grammar'
import { said } from './langs'
import { exampleOf, wordOf, type Pack } from './packs'
import type { Scenario } from './scenarios'
import { spriteVoice, type ResolvedScene } from './scenes'
import type { ModuleSpec } from './spec'
import { chaptersOf, LEVELS, type Story } from './stories'
import { allLines, type Villager } from './villagers'

/** A speaker of the voice cast: the narrators "female" and "male", or a character voice ("grandma"). */
export type VoiceName = string
export type EngineName = 'elevenlabs' | 'gepard'
/**
 * How a clip was made, besides plainly: 'carrier', cut out of its carrier sentence; 'plain', asked plainly because the
 * word could not be cut out of it (not tried again). Null: plainly, as before the carrier sentence or where it doesn't apply.
 */
export type ClipMethod = 'carrier' | 'plain'

export type Clip = {
  key: string
  norm: string
  text: string
  voice: VoiceName
  engine: EngineName
  file: string
  /** The characters it cost (a carrier clip: its carrier sentence). */
  chars: number
  created_at: string
  source: string
  /** Absent in a store from before the carrier sentence that is only read (voice-build's dry run). */
  method?: ClipMethod | null
  /** How often the app asked for it (downloaded it, or /voice/say gave it). */
  hits?: number
  /** The filter it went through before it was stored (the cast's `denoise`: "hiss"); null or absent: none. */
  denoise?: DenoisePreset | null
  /** The loudness it was brought to (LEVEL.target, LUFS); null or absent: not levelled yet. */
  level?: number | null
  /** The gain that took it there, in all, from the clip as it was made (dB; 0: kept as it was). */
  gain?: number | null
}

/** A text to voice ahead of time; lower [priority] goes first. */
export type CorpusItem = { text: string; voice: VoiceName; source: string; priority: number }

export const FILE = /^[a-f0-9]{40}\.mp3$/
/** Today on the node's clock, "2026-09-26": the daily cap's day. */
const localDay = (d = new Date()) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
export const clipKey = (norm: string, voice: VoiceName) => `${voice}:${norm}`
export const fileUrl = (file: string) => `/voice/file/${file}`

/** MP3 from the first bytes: an ID3 tag or an MPEG frame sync. */
export function isMp3(b: Uint8Array): boolean {
  if (b.length < 4) return false
  if (b[0] === 0x49 && b[1] === 0x44 && b[2] === 0x33) return true
  return b[0] === 0xff && (b[1] & 0xe0) === 0xe0
}

type Log = (...a: unknown[]) => void

// --- a lone word in a carrier sentence -------------------------------------------------------
//
// An English library voice (the narrators) says a lone Slovene word the English way: eleven_v3 takes language_code
// only as a hint, and previous_text/next_text are not supported for it. Inside a Slovene sentence the word comes out
// Slovene, so a short text is asked for inside its language's carrier sentence through /with-timestamps and cut out
// by the timestamps of its characters (docs/plans/03-voice-service.md).

/**
 * The carrier sentence of a language ({w}: the word), for each language a village can speak (cultures.ts,
 * APP_LANGUAGES); the language is the village's, and ElevenLabs gets it as language_code. Another language adds its
 * own here.
 */
export const CARRIERS: Record<string, string> = {
  sl: 'Beseda je: {w}.',
  it: 'La parola è: {w}.',
  de: 'Das Wort ist: {w}.',
  en: 'The word is: {w}.',
}

/** Seconds kept before the word and after it, and the fades in and out (the cut sounds clean with these). */
export const CUT = { before: 0.06, after: 0.12, fadeIn: 0.02, fadeOut: 0.05 }

/**
 * A word or a short phrase, which a carrier voice says inside its carrier sentence: one to three words and no sentence
 * punctuation (a sentence, a question or an exclamation keeps its own intonation and is asked for as it is).
 */
export function isShortText(text: string): boolean {
  const t = text.trim()
  if (!t || /[.!?…:;]/.test(t)) return false
  const words = normalizeText(t).split(' ').filter(Boolean)
  return words.length >= 1 && words.length <= 3
}

/** An ElevenLabs alignment: each character of the text said, and when it starts and ends (seconds). */
export type Alignment = { characters?: string[]; character_start_times_seconds?: number[]; character_end_times_seconds?: number[] }

/**
 * When [word] is said in a take, from its [alignment]: its last occurrence (the carrier comes before it), which must
 * end within [tail] characters of the end (the carrier's closing "."). Undefined when it isn't there.
 */
export function wordSpan(alignment: Alignment | undefined, word: string, tail = 3): { start: number; end: number } | undefined {
  const ch = alignment?.characters
  const starts = alignment?.character_start_times_seconds
  const ends = alignment?.character_end_times_seconds
  if (!ch?.length || starts?.length !== ch.length || ends?.length !== ch.length) return undefined
  const low = (s: string) => s.normalize('NFC').toLocaleLowerCase('sl')
  // one entry per character, but map each code unit to its entry all the same
  let said = ''
  const entry: number[] = []
  ch.forEach((c, k) => {
    const l = low(String(c))
    said += l
    for (let n = 0; n < l.length; n++) entry.push(k)
  })
  const w = low(word.trim())
  const at = w ? said.lastIndexOf(w) : -1
  if (at < 0 || said.length - (at + w.length) > tail) return undefined
  const start = starts[entry[at]]
  const end = ends[entry[at + w.length - 1]]
  return Number.isFinite(start) && Number.isFinite(end) && start >= 0 && end > start ? { start, end } : undefined
}

/** Cuts [from]–[to] seconds out of an MP3, with short fades: the clip, or null. */
export type Cutter = (mp3: Uint8Array, from: number, to: number) => Promise<Uint8Array | null>

/** The node's ffmpeg: [bin], else LANI_FFMPEG, else ffmpeg on the PATH or /usr/bin/ffmpeg; undefined for none ("off"). */
function ffmpegPath(bin?: string): string | undefined {
  const name = bin ?? (process.env.LANI_FFMPEG || Bun.which('ffmpeg') || '/usr/bin/ffmpeg')
  if (!name || name === 'off') return undefined
  return name.includes('/') ? (existsSync(name) ? name : undefined) : (Bun.which(name) ?? undefined)
}

/**
 * An MP3 through ffmpeg's audio filter [af], as an MP3 again ([kbps] kbit/s, the sample rate kept): the result, or
 * null. [files]: through temporary files, not pipes, so the clip keeps its exact length: ffmpeg then reads the MP3's
 * gapless header (the encoder's delay and padding, which players skip; ElevenLabs' clips have one) and writes a new
 * one. Through pipes the clip comes out 25 ms later and 21 ms longer.
 */
async function ffmpegMp3(path: string, mp3: Uint8Array, af: string, files = false, kbps = 128): Promise<Uint8Array | null> {
  const tmp = files ? join(tmpdir(), `lani-ffmpeg-${randomUUID()}`) : undefined
  try {
    if (tmp) writeFileSync(`${tmp}.in.mp3`, mp3)
    const input = tmp ? ['-i', `${tmp}.in.mp3`] : ['-f', 'mp3', '-i', 'pipe:0']
    const p = Bun.spawn([path, '-hide_banner', '-loglevel', 'error', ...input, '-af', af, '-c:a', 'libmp3lame', '-b:a', `${kbps}k`, '-f', 'mp3', ...(tmp ? ['-y', `${tmp}.out.mp3`] : ['pipe:1'])], {
      stdin: tmp ? 'ignore' : mp3,
      stdout: 'pipe',
      stderr: 'pipe',
    })
    const timer = setTimeout(() => p.kill(), 30_000)
    const [out, , code] = await Promise.all([new Response(p.stdout).arrayBuffer(), new Response(p.stderr).text(), p.exited]).finally(() => clearTimeout(timer))
    const bytes = tmp ? (code === 0 && existsSync(`${tmp}.out.mp3`) ? new Uint8Array(readFileSync(`${tmp}.out.mp3`)) : new Uint8Array()) : new Uint8Array(out)
    return code === 0 && isMp3(bytes) ? bytes : null
  } catch {
    return null
  } finally {
    for (const f of tmp ? [`${tmp}.in.mp3`, `${tmp}.out.mp3`] : []) {
      try {
        unlinkSync(f)
      } catch {}
    }
  }
}

/**
 * The cut with ffmpeg ([bin], else LANI_FFMPEG, else ffmpeg on the PATH or /usr/bin/ffmpeg): undefined when there
 * is none ("off" turns it off). atrim, then the fades on the cut's own time (an output -ss with the fades would fade
 * on the take's time and leave the cut silent).
 */
export function ffmpegCutter(bin?: string): Cutter | undefined {
  const path = ffmpegPath(bin)
  if (!path) return undefined
  return async (mp3, from, to) => {
    const len = to - from
    const af = [
      `atrim=start=${from.toFixed(3)}:end=${to.toFixed(3)}`,
      'asetpts=PTS-STARTPTS',
      `afade=t=in:d=${CUT.fadeIn}`,
      `afade=t=out:st=${Math.max(0, len - CUT.fadeOut).toFixed(3)}:d=${CUT.fadeOut}`,
    ].join(',')
    return ffmpegMp3(path, mp3, af)
  }
}

// --- a voice that hisses: the denoise filter ---------------------------------------------------
//
// Stari Janez's designed voice (@janez) carries a steady broadband hiss up to the MP3's 15 kHz (pauses at about
// −35 dBFS; the other voices −45 to −80) and a rumble below 30 Hz. The cast's `denoise` marks such a voice, and its
// ElevenLabs clips go through one of these ffmpeg chains before they are stored (README, "Voice"):
// - highpass at 60 Hz: the rumble goes (an old man's voice starts at about 90 Hz), and the gate below no longer
//   opens on it in the pauses;
// - afftdn, spectral noise reduction: by nr dB below the noise floor nf (dBFS). A fixed floor, not tracked (tn=1):
//   with tracking, one clip's hiss went 8 dB down where the fixed floor takes it 32. afftdn delays the sound by 25 ms
//   and keeps the length, which would drop the clip's last 25 ms: the pad before and the trim after put it back;
// - agate: below −37 dBFS (0.0141) the level falls 3:1, at most 24 dB (the pauses); 5 ms attack, so no word start is
//   cut, 120 ms release, so tails fade naturally.
// Measured on all 39 of Janez's clips and on word cuts (hiss): the hiss band (4–15 kHz) in the pauses 12 dB down, the
// pauses −40 → −50 dB, astats' noise floor −33 → −62 dB; speech level, fricatives, word starts and tails within 1 dB
// on average; the local Whisper's word match 0.82 → 0.79 (a few words heard differently, as many better as worse).
// hiss-strong takes the hiss band 19 dB down, at more risk of a watery sound.

/** A denoise preset as an ffmpeg audio filter chain: [nr] dB off below the floor [nf] dBFS. */
const denoiseChain = (nr: number, nf: number) =>
  `highpass=f=60,apad=pad_dur=0.025,afftdn=nr=${nr}:nf=${nf},atrim=start=0.025,asetpts=PTS-STARTPTS,agate=threshold=0.0141:ratio=3:attack=5:release=120`

/** The denoise presets' ffmpeg chains (the cast's `denoise` names one). */
export const DENOISE: Record<DenoisePreset, string> = { hiss: denoiseChain(12, -40), 'hiss-strong': denoiseChain(20, -35) }

/** An MP3 through a denoise preset: the filtered MP3, or null. */
export type Denoiser = (mp3: Uint8Array, preset: DenoisePreset) => Promise<Uint8Array | null>

/** The denoise filter with ffmpeg (found as for the cut: [bin], LANI_FFMPEG, the PATH): undefined when there is none. */
export function ffmpegDenoiser(bin?: string): Denoiser | undefined {
  const path = ffmpegPath(bin)
  return path ? (mp3, preset) => ffmpegMp3(path, mp3, DENOISE[preset], true) : undefined
}

/** The new file name of an existing clip once filtered (voice-build --denoise): its name and the preset. */
export const denoisedFile = (file: string, preset: DenoisePreset) => processedFile(file, { denoise: preset })

/** An MP3's length in seconds, from its MPEG Layer III frames (0 when it has none). */
export const mp3Duration = (b: Uint8Array): number => mp3Frames(b).secs

/** The bitrates LAME encodes at (kbit/s): an MP3's average is taken to the nearest. */
const KBPS = [8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 192, 224, 256, 320]

/** An MP3's average bitrate (kbit/s, one LAME encodes at; 128 when it has no frames): ElevenLabs' 128, Gepard's 64. */
export function mp3Kbps(b: Uint8Array): number {
  const { secs, bytes } = mp3Frames(b)
  if (!secs) return 128
  const avg = (bytes * 8) / secs / 1000
  return KBPS.reduce((best, k) => (Math.abs(k - avg) < Math.abs(best - avg) ? k : best))
}

/** An MP3's MPEG Layer III frames: their length in seconds and their bytes. */
function mp3Frames(b: Uint8Array): { secs: number; bytes: number } {
  let i = 0
  if (b.length >= 10 && b[0] === 0x49 && b[1] === 0x44 && b[2] === 0x33) {
    i = 10 + (((b[6] & 0x7f) << 21) | ((b[7] & 0x7f) << 14) | ((b[8] & 0x7f) << 7) | (b[9] & 0x7f)) + (b[5] & 0x10 ? 10 : 0)
  }
  const V1 = [0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320]
  const V2 = [0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160]
  const RATES: Record<number, number[]> = { 0: [11025, 12000, 8000], 2: [22050, 24000, 16000], 3: [44100, 48000, 32000] }
  let secs = 0
  let bytes = 0
  while (i + 4 <= b.length) {
    const ver = (b[i + 1] >> 3) & 3
    const layer = (b[i + 1] >> 1) & 3
    const br = b[i + 2] >> 4
    const sr = (b[i + 2] >> 2) & 3
    if (b[i] !== 0xff || (b[i + 1] & 0xe0) !== 0xe0 || ver === 1 || layer !== 1 || br === 0 || br === 15 || sr === 3) {
      i++
      continue
    }
    const v1 = ver === 3
    const rate = RATES[ver][sr]
    const len = Math.floor(((v1 ? 144 : 72) * (v1 ? V1 : V2)[br] * 1000) / rate) + ((b[i + 2] >> 1) & 1)
    secs += (v1 ? 1152 : 576) / rate
    bytes += len
    i += len
  }
  return { secs, bytes }
}

// --- every clip at one loudness: the level -------------------------------------------------------------------
//
// The voices came out of ElevenLabs up to 10 dB apart (integrated loudness, the median of each voice: the girl voice
// −10 LUFS, grandma −12.8, the female narrator −17.4, grandpa −19.4; the local Gepard clips −17.1 to −19), and the
// girl's clips peaked at 0 dBFS. So every clip is brought to one loudness before it is stored (README, "Voice"):
// - the measure: ffmpeg's ebur128, the integrated loudness (EBU R128, LUFS) and the true peak. It gates in 400 ms
//   blocks: a clip under 0.4 s has none (−70), so it is measured looped, which gives its mean loudness. From 0.4 s on,
//   single words measure like the same voice's sentences (the female narrator's 0.6–0.9 s words −17.7 LUFS, her
//   1.5–3 s lines −17.3), so the integrated value holds for carrier cuts too;
// - a plain gain (volume), which keeps the sound (loudnorm's dynamic mode changes short clips). Where the gain would
//   take the true peak over LEVEL.peak, a limiter holds the peaks (alimiter: 5 ms lookahead, its delay put back, no
//   auto level);
// - the target, −18 LUFS: speech peaks about 15 dB over its loudness (p90 18 dB; a voiced vowel's glottal pulses sit
//   11–12 dB over its RMS), so a louder target limits most clips, and a limiter that holds a vowel's pulses works as a
//   compressor on the whole vowel. At −16 LUFS 58% of the clips needed it (3.4 dB deep, p90 6.5) and the deepest
//   stayed up to 4 dB short of the target; at −18 a third, by 1–2 dB (each voice's median). The female narrator,
//   half of the clips, was at −17.4 already;
// - the encode itself takes about 0.4 dB off (LAME at a constant bitrate, even a plain tone), so the result is
//   measured and, when it misses by more than a quarter dB, encoded again from the clip as it was: one generation;
// - a clip within LEVEL.tolerance of the target, its peak under the ceiling, is kept as it is (no re-encode);
// - a voice's denoise filter runs in the same encode, before the measure (one generation of MP3, not two). The clip
//   keeps its encoding (its sample rate and bitrate: ElevenLabs 44.1 kHz 128 kbit/s, Gepard 22.05 kHz 64 kbit/s,
//   mono) and its timing (through temporary files, as for DENOISE); a clip without a gapless header (Gepard's, a
//   carrier cut) ends 47 samples (1–2 ms) later.

/**
 * The level: the loudness every clip is brought to (integrated, LUFS), the true peak it may reach (dBTP), how near the
 * target a clip may stay as it is (LU), and the most gain a clip gets (a clip of near silence isn't pumped up).
 */
export const LEVEL = { target: -18, peak: -1.5, tolerance: 0.5, maxGain: 15 }
export type LevelSpec = typeof LEVEL

/** The limiter holds the sample peaks this much under the ceiling, for the true peaks between samples and the MP3 encode's overshoot. */
export const LIMIT_MARGIN = 0.5

/** A clip's loudness: integrated (LUFS; −70 or less for silence) and true peak (dBTP). */
export type Loudness = { lufs: number; peak: number }

/**
 * What the level does with a clip of loudness [m]: the gain to the target (dB, between −30 and maxGain; 0 for
 * silence), whether the limiter must hold its peaks, and whether the clip can stay as it is (within the tolerance, its
 * peak under the ceiling).
 */
export function levelGain(m: Loudness, spec: LevelSpec = LEVEL): { gain: number; limit: boolean; keep: boolean } {
  if (!(m.lufs > -70)) return { gain: 0, limit: false, keep: true }
  const gain = Math.round(Math.min(spec.maxGain, Math.max(-30, spec.target - m.lufs)) * 100) / 100
  return { gain, limit: m.peak + gain > spec.peak - LIMIT_MARGIN, keep: Math.abs(gain) <= spec.tolerance && m.peak <= spec.peak }
}

/** The limiter that holds the peaks under [peak] dBTP (see LIMIT_MARGIN); auto level off, or it would raise the clip to 0 dBFS. */
const limiter = (peak: number) => `alimiter=limit=${(10 ** ((peak - LIMIT_MARGIN) / 20)).toFixed(4)}:attack=5:release=50:level=0:latency=1`

/** ffmpeg's EBU R128 measure of the audio file [file], through [af] first and [loop] more times over: null when ffmpeg failed. */
async function ebur128(path: string, file: string, af?: string, loop = 0): Promise<Loudness | null> {
  try {
    const p = Bun.spawn([path, '-hide_banner', '-nostats', ...(loop ? ['-stream_loop', String(loop)] : []), '-i', file, '-af', `${af ? `${af},` : ''}ebur128=peak=true:framelog=quiet`, '-f', 'null', '-'], {
      stdin: 'ignore',
      stdout: 'ignore',
      stderr: 'pipe',
    })
    const timer = setTimeout(() => p.kill(), 30_000)
    const [err, code] = await Promise.all([new Response(p.stderr).text(), p.exited]).finally(() => clearTimeout(timer))
    const sum = err.slice(err.lastIndexOf('Summary:'))
    const lufs = /I:\s*(-?[\d.]+|-inf) LUFS/.exec(sum)?.[1]
    const peak = /True peak:\s*Peak:\s*(-?[\d.]+|-inf) dBFS/.exec(sum)?.[1]
    if (code !== 0 || !sum.startsWith('Summary:') || lufs === undefined || peak === undefined) return null
    const n = (s: string) => (s === '-inf' ? -Infinity : Number(s))
    return { lufs: n(lufs), peak: n(peak) }
  } catch {
    return null
  }
}

/** The loudness of [file]: a clip under 0.4 s (no 400 ms block) measured 8 more times over, which gives its mean loudness. */
async function loudnessOf(path: string, file: string, af?: string): Promise<Loudness | null> {
  const m = await ebur128(path, file, af)
  if (!m || m.lufs > -70) return m
  const looped = await ebur128(path, file, af, 8)
  return looped ? { lufs: looped.lufs, peak: m.peak } : m
}

/** [bytes] in a temporary file [ext] for [f], removed after. */
async function inTemp<T>(bytes: Uint8Array, ext: string, f: (file: string, tmp: string) => Promise<T>): Promise<T> {
  const tmp = join(tmpdir(), `lani-level-${randomUUID()}`)
  try {
    writeFileSync(`${tmp}.in.${ext}`, bytes)
    return await f(`${tmp}.in.${ext}`, tmp)
  } finally {
    for (const x of [`${tmp}.in.${ext}`, `${tmp}.out.${ext}`]) {
      try {
        unlinkSync(x)
      } catch {}
    }
  }
}

/** An MP3's loudness (through the denoise preset [pre] first, as it would be levelled); null when ffmpeg failed. */
export type Measure = (mp3: Uint8Array, pre?: DenoisePreset) => Promise<Loudness | null>

/** The measure with ffmpeg (found as for the cut: [bin], LANI_FFMPEG, the PATH): undefined when there is none. */
export function ffmpegLoudness(bin?: string): Measure | undefined {
  const path = ffmpegPath(bin)
  if (!path) return undefined
  return (mp3, pre) => (isMp3(mp3) ? inTemp(mp3, 'mp3', file => loudnessOf(path, file, pre ? DENOISE[pre] : undefined)).catch(() => null) : Promise.resolve(null))
}

/**
 * A clip brought to the level: the MP3 (the same bytes when it was kept as it was), the gain applied (dB; 0 when
 * kept), whether the limiter held its peaks, and its loudness before (after the denoise filter, when one ran).
 */
export type Levelled = { bytes: Uint8Array; gain: number; limited: boolean; before: Loudness }

/** Brings MP3s to the loudness [target] (LUFS): [level] an MP3, through a denoise preset [pre] first in the same encode. */
export type Leveller = { readonly target: number; level(mp3: Uint8Array, pre?: DenoisePreset): Promise<Levelled | null> }

/** The level with ffmpeg (found as for the cut), to [spec]: undefined when there is no ffmpeg. */
export function ffmpegLeveller(bin?: string, spec: LevelSpec = LEVEL): Leveller | undefined {
  const path = ffmpegPath(bin)
  if (!path) return undefined
  const measure = (b: Uint8Array, af?: string) => inTemp(b, 'mp3', file => loudnessOf(path, file, af))
  const encode = (mp3: Uint8Array, denoise: string | undefined, gain: number, limit: boolean) =>
    ffmpegMp3(path, mp3, [denoise, `volume=${gain.toFixed(2)}dB`, limit ? limiter(spec.peak) : undefined].filter(Boolean).join(','), true, mp3Kbps(mp3))
  const level = async (mp3: Uint8Array, pre?: DenoisePreset): Promise<Levelled | null> => {
    if (!isMp3(mp3) || !mp3Duration(mp3)) return null
    const denoise = pre ? DENOISE[pre] : undefined
    const before = await measure(mp3, denoise)
    if (!before) return null
    const g = levelGain(before, spec)
    if (g.keep && !denoise) return { bytes: mp3, gain: 0, limited: false, before }
    const out = await encode(mp3, denoise, g.gain, g.limit)
    if (!out || !(before.lufs > -70)) return out ? { bytes: out, gain: g.gain, limited: g.limit, before } : null
    // The encode itself takes about 0.4 dB off (LAME at a constant bitrate), and the limiter some more: measured, and
    // when that's more than a quarter dB off, encoded again from the clip as it was (not from the encode) with the
    // difference (at most 2 dB more), so the clip goes through one encode all the same.
    const after = await measure(out)
    const miss = after ? spec.target - after.lufs : 0
    if (Math.abs(miss) <= 0.25) return { bytes: out, gain: g.gain, limited: g.limit, before }
    const gain = Math.round(Math.min(spec.maxGain, g.gain + Math.max(-2, Math.min(2, miss))) * 100) / 100
    const limit = before.peak + gain > spec.peak - LIMIT_MARGIN
    const again = await encode(mp3, denoise, gain, limit)
    return again ? { bytes: again, gain, limited: limit, before } : { bytes: out, gain: g.gain, limited: g.limit, before }
  }
  return { target: spec.target, level: (mp3, pre) => level(mp3, pre).catch(() => null) }
}

/** The new file name of an existing clip once levelled to [level] (and filtered with [denoise]): voice-build --level, --denoise. */
export const processedFile = (file: string, o: { denoise?: DenoisePreset | null; level?: number | null }) =>
  `${createHash('sha1').update(`${file}${o.denoise ? `|denoise:${o.denoise}` : ''}${o.level != null ? `|level:${o.level}` : ''}`).digest('hex')}.mp3`

/** A family recording's container: its codec and bitrate as the recorders make it (the app: AAC 96 kbit/s; the family page: Opus 64 kbit/s). */
const RECORDING: Record<'m4a' | 'ogg' | 'webm', string[]> = {
  m4a: ['-c:a', 'aac', '-b:a', '96k', '-f', 'mp4', '-movflags', '+faststart'],
  ogg: ['-c:a', 'libopus', '-b:a', '64k', '-f', 'ogg'],
  webm: ['-c:a', 'libopus', '-b:a', '64k', '-f', 'webm'],
}

/** A family recording (m4a, ogg, webm) brought to the level, in its own container; null when it can't be. */
export type RecordingLeveller = (audio: Uint8Array, ext: 'm4a' | 'ogg' | 'webm') => Promise<{ bytes: Uint8Array; gain: number } | null>

/** The level for family recordings with ffmpeg (found as for the cut), to [spec]: undefined when there is no ffmpeg. */
export function ffmpegRecordingLeveller(bin?: string, spec: LevelSpec = LEVEL): RecordingLeveller | undefined {
  const path = ffmpegPath(bin)
  if (!path) return undefined
  return (audio, ext) =>
    inTemp(audio, ext, async (file, tmp) => {
      const before = await loudnessOf(path, file)
      if (!before) return null
      const g = levelGain(before, spec)
      if (g.keep) return { bytes: audio, gain: 0 }
      const af = [`volume=${g.gain.toFixed(2)}dB`, g.limit ? limiter(spec.peak) : undefined].filter(Boolean).join(',')
      const out = `${tmp}.out.${ext}`
      const p = Bun.spawn([path, '-hide_banner', '-loglevel', 'error', '-i', file, '-vn', '-af', af, ...RECORDING[ext], '-y', out], { stdin: 'ignore', stdout: 'ignore', stderr: 'pipe' })
      const timer = setTimeout(() => p.kill(), 30_000)
      const code = await Promise.all([new Response(p.stderr).text(), p.exited]).then(([, c]) => c).finally(() => clearTimeout(timer))
      return code === 0 && existsSync(out) ? { bytes: new Uint8Array(readFileSync(out)), gain: g.gain } : null
    }).catch(() => null)
}

// --- engines --------------------------------------------------------------------------------

/** What an engine made: the MP3, how (a carrier cut), and the characters it cost. */
export type Take = { bytes: Uint8Array; method?: ClipMethod; chars?: number }

export interface Engine {
  readonly name: EngineName
  /** Whether a request of [chars] may go to this engine now (configured, up, within quota). */
  ready(chars: number, live: boolean): Promise<boolean>
  /** The characters a request for [text] in [voice] costs (ElevenLabs: a short word's carrier sentence); else its length. */
  cost?(text: string, voice: VoiceName): number
  /** The clip, or null when the engine can't make it now. Never throws. [live]: a live line, which may use the reserve. */
  synth(text: string, voice: VoiceName, o?: { live?: boolean }): Promise<Take | null>
  status(): Promise<Record<string, unknown>>
}

export type Quota = {
  used: number
  limit: number
  resets_at?: number
  checked_at: number
  /** Voices of one's own the plan allows, and in use (designed, cloned); library voices don't count. */
  voice_limit?: number
  voice_slots_used?: number
  /** Voices added or edited this month, of how many. */
  voice_edits?: number
  voice_edits_max?: number
}

/** One of Voice Design's takes: its id (to save it as a voice) and how it sounds. */
export type DesignTake = { id: string; audio: Uint8Array }

/** Characters kept for live lines (the tutor's role-play lines, voiced as they come). */
export const DEFAULT_RESERVE = 5_000

/**
 * ElevenLabs eleven_v3 with a quota guard. The subscription is read at most every 10 minutes and
 * counted forward locally in between. Prebuilds stop at [reserve] characters left; live dialogue
 * lines may use the reserve. Each speaker's voice id comes from the cast ([voices] overrides it).
 * A carrier voice says a short text inside its language's carrier sentence, cut out with [cut].
 */
export class ElevenLabs implements Engine {
  readonly name = 'elevenlabs' as const
  private quota?: Quota
  private checking?: Promise<Quota | undefined>
  private pausedUntil = 0
  private lastError?: string
  private cutter?: Cutter | null
  private noCutterLogged = false

  constructor(
    private readonly o: {
      key?: string
      base?: string
      reserve?: number
      refreshMs?: number
      voices?: Record<VoiceName, string>
      /** The language the clips speak (the village's): Slovene unless the culture pack is in another. */
      language?: string
      /** The carrier cut: ffmpeg when the node has it (undefined), none (null: short words are asked for plainly), or another. */
      cut?: Cutter | null
      /** The carrier sentences by language (CARRIERS). */
      carriers?: Record<string, string>
      log?: Log
    } = {},
  ) {}

  private get base() { return (this.o.base ?? 'https://api.elevenlabs.io').replace(/\/$/, '') }
  get reserve() { return this.o.reserve ?? DEFAULT_RESERVE }
  get configured() { return !!this.o.key }

  private headers() { return { 'xi-api-key': this.o.key!, 'content-type': 'application/json' } }

  /** Error text as it may be logged and shown in the status: never with the key in it. */
  private redact(s: string): string { return this.o.key ? s.split(this.o.key).join('[redacted]') : s }

  /** The subscription's character counts, cached for refreshMs (10 min). */
  async subscription(force = false): Promise<Quota | undefined> {
    if (!this.o.key) return undefined
    const fresh = this.quota && Date.now() - this.quota.checked_at < (this.o.refreshMs ?? 600_000)
    if (fresh && !force) return this.quota
    this.checking ??= (async () => {
      try {
        const r = await fetch(`${this.base}/v1/user/subscription`, { headers: this.headers(), signal: AbortSignal.timeout(10_000) })
        if (!r.ok) throw new Error(`subscription: HTTP ${r.status}`)
        const s = (await r.json()) as {
          character_count?: number; character_limit?: number; next_character_count_reset_unix?: number
          voice_limit?: number; voice_slots_used?: number; voice_add_edit_counter?: number; max_voice_add_edits?: number
        }
        if (typeof s.character_count !== 'number' || typeof s.character_limit !== 'number') throw new Error('subscription: no character counts')
        this.quota = {
          used: s.character_count, limit: s.character_limit, resets_at: s.next_character_count_reset_unix, checked_at: Date.now(),
          voice_limit: s.voice_limit, voice_slots_used: s.voice_slots_used, voice_edits: s.voice_add_edit_counter, voice_edits_max: s.max_voice_add_edits,
        }
        this.lastError = undefined
      } catch (e) {
        this.lastError = this.redact((e as Error).message)
        this.o.log?.(`elevenlabs: ${this.lastError}`)
        // Keep the last known counts, but don't ask again at once.
        if (this.quota) this.quota.checked_at = Date.now()
        else this.pausedUntil = Math.max(this.pausedUntil, Date.now() + 60_000)
      }
      return this.quota
    })().finally(() => (this.checking = undefined))
    return this.checking
  }

  remaining(): number | undefined {
    return this.quota ? Math.max(0, this.quota.limit - this.quota.used) : undefined
  }

  async ready(chars: number, live: boolean): Promise<boolean> {
    if (!this.o.key || Date.now() < this.pausedUntil) return false
    const q = await this.subscription()
    if (!q) return false
    return q.limit - q.used - chars >= (live ? 0 : this.reserve)
  }

  /** The carrier cut, when there is one (ffmpeg, looked for once). */
  get cut(): Cutter | undefined {
    if (this.cutter === undefined) this.cutter = this.o.cut === undefined ? (ffmpegCutter() ?? null) : this.o.cut
    return this.cutter ?? undefined
  }

  /**
   * The carrier sentence [text] is said in by [voice]: for a short text (a word, up to three) in a voice marked
   * `carrier` in the cast, in a language with a carrier sentence; else undefined.
   */
  carrierFor(text: string, voice: VoiceName): string | undefined {
    const template = (this.o.carriers ?? CARRIERS)[this.o.language ?? 'sl']
    if (!template || !carrierVoice(voice) || !isShortText(text)) return undefined
    return template.replace('{w}', text.trim())
  }

  /** Whether [text] in [voice] is said in its carrier sentence now: it applies, and the node can cut (ffmpeg). */
  usesCarrier(text: string, voice: VoiceName): boolean {
    if (!this.carrierFor(text, voice)) return false
    if (this.cut) return true
    if (!this.noCutterLogged) {
      this.noCutterLogged = true
      this.o.log?.('elevenlabs: no ffmpeg: short words are asked for plainly, without their carrier sentence')
    }
    return false
  }

  /** The characters a request for [text] in [voice] costs: its carrier sentence when it is said in one, else the text. */
  cost(text: string, voice: VoiceName): number {
    return this.usesCarrier(text, voice) ? this.carrierFor(text, voice)!.length : text.trim().length
  }

  private voiceId(voice: VoiceName): string | undefined {
    const id = this.o.voices?.[voice] ?? elevenlabsOf(voice)
    if (!id) this.lastError = `tts: no ElevenLabs voice for speaker "${voice}"`
    return id
  }

  /** A failed request: logged without the key; quota, auth, a rate limit or a server error pause asking for a while. */
  private async failed(r: Response, what: string) {
    this.lastError = `${what}: HTTP ${r.status} ${this.redact(await r.text()).slice(0, 160)}`
    this.o.log?.(`elevenlabs: ${this.lastError}`)
    // Quota, auth or rate limit: stop asking for a while and re-read the subscription then.
    if ([401, 402, 429].includes(r.status) || r.status >= 500) {
      this.pausedUntil = Date.now() + (r.status === 429 ? 60_000 : 600_000)
      if (this.quota) this.quota.checked_at = 0
    }
  }

  /**
   * [text] in [voice]: a short text of a carrier voice inside its carrier sentence, cut out; when the word can't be cut
   * out of it, or the request fails (not for the quota, auth or a rate limit), asked for plainly, and logged.
   */
  async synth(text: string, voice: VoiceName, o: { live?: boolean } = {}): Promise<Take | null> {
    if (!this.o.key) return null
    const said = this.usesCarrier(text, voice) ? this.carrierFor(text, voice) : undefined
    if (!said) {
      const bytes = await this.plain(text, voice)
      return bytes ? { bytes } : null
    }
    const r = await this.carrierTake(text, voice)
    if (r instanceof Uint8Array) return { bytes: r, method: 'carrier', chars: said.length }
    if (!r && Date.now() < this.pausedUntil) return null
    this.o.log?.(`elevenlabs: "${text}" (${voice}) without its carrier sentence: ${r ? r.miss : this.lastError}; asked for plainly`)
    // the plain take on top of a paid carrier take: the guard is asked again
    if (!(await this.ready(text.trim().length, !!o.live))) return null
    const bytes = await this.plain(text, voice)
    // a take the word couldn't be cut out of: 'plain', not tried again; a failed request or cut: tried again when re-voicing
    return bytes ? { bytes, method: r && !r.retry ? 'plain' : undefined, chars: (r ? said.length : 0) + text.trim().length } : null
  }

  /** [text] asked for as it is. */
  private async plain(text: string, voice: VoiceName): Promise<Uint8Array | null> {
    const id = this.voiceId(voice)
    if (!id) return null
    try {
      const r = await fetch(`${this.base}/v1/text-to-speech/${id}?output_format=mp3_44100_128`, {
        method: 'POST',
        headers: { ...this.headers(), accept: 'audio/mpeg' },
        body: JSON.stringify({ text, model_id: 'eleven_v3', language_code: this.o.language ?? 'sl' }),
        signal: AbortSignal.timeout(60_000),
      })
      if (!r.ok) {
        await this.failed(r, 'tts')
        return null
      }
      const bytes = new Uint8Array(await r.arrayBuffer())
      if (!isMp3(bytes)) {
        this.lastError = 'tts: response is not MP3'
        return null
      }
      if (this.quota) this.quota.used += text.length
      return bytes
    } catch (e) {
      this.lastError = this.redact(`tts: ${(e as Error).message}`)
      this.o.log?.(`elevenlabs: ${this.lastError}`)
      return null
    }
  }

  /**
   * [text] said by [voice] inside its carrier sentence (/with-timestamps) and cut out by its characters' timestamps:
   * the clip; {miss} when the take is paid for but gives no clip: the word isn't in the timestamps or its cut is no
   * word ([retry]: ffmpeg failed, not the word; the carrier sentence is then off until a restart, so no more takes
   * are paid for nothing); null when ElevenLabs can't now or the request failed (the last error says why).
   */
  async carrierTake(text: string, voice: VoiceName): Promise<Uint8Array | { miss: string; retry?: boolean } | null> {
    const said = this.carrierFor(text, voice)
    const cut = this.cut
    if (!this.o.key || !said || !cut) return null
    const id = this.voiceId(voice)
    if (!id) return null
    let d: { audio_base64?: string; alignment?: Alignment; normalized_alignment?: Alignment }
    try {
      const r = await fetch(`${this.base}/v1/text-to-speech/${id}/with-timestamps?output_format=mp3_44100_128`, {
        method: 'POST',
        headers: this.headers(),
        body: JSON.stringify({ text: said, model_id: 'eleven_v3', language_code: this.o.language ?? 'sl' }),
        signal: AbortSignal.timeout(60_000),
      })
      if (!r.ok) {
        await this.failed(r, 'tts (carrier)')
        return null
      }
      d = (await r.json()) as typeof d
    } catch (e) {
      this.lastError = this.redact(`tts (carrier): ${(e as Error).message}`)
      this.o.log?.(`elevenlabs: ${this.lastError}`)
      return null
    }
    if (this.quota) this.quota.used += said.length
    const take = d.audio_base64 ? new Uint8Array(Buffer.from(d.audio_base64, 'base64')) : new Uint8Array()
    if (!isMp3(take)) {
      this.lastError = 'tts (carrier): no MP3 in the response'
      return null
    }
    const tail = said.length - said.lastIndexOf(text.trim()) - text.trim().length + 2
    const span = wordSpan(d.normalized_alignment, text, tail) ?? wordSpan(d.alignment, text, tail)
    if (!span) return { miss: 'the word is not in the timestamps' }
    const clip = await cut(take, Math.max(0, span.start - CUT.before), span.end + CUT.after)
    if (!clip) {
      this.cutter = null
      this.o.log?.('elevenlabs: ffmpeg could not cut a take: short words are asked for plainly until the bridge restarts')
      return { miss: 'the cut failed', retry: true }
    }
    const [len, whole] = [mp3Duration(clip), mp3Duration(take)]
    if (len < 0.1 || (whole > 0 && len >= whole)) return { miss: `the cut is ${len.toFixed(2)} s of the take's ${whole.toFixed(2)} s` }
    return clip
  }

  /**
   * Voice Design (eleven_ttv_v3): three takes of a new voice for [description], speaking [text] (100–1000
   * characters, in Slovene so the voice is made for it), the same ones again for the same [seed]. Costs about
   * the text's length in characters. Null when it can't now.
   */
  async design(description: string, text: string, seed: number, reference?: Uint8Array): Promise<DesignTake[] | null> {
    if (!this.o.key || Date.now() < this.pausedUntil) return null
    try {
      const r = await fetch(`${this.base}/v1/text-to-voice/design?output_format=mp3_44100_128`, {
        method: 'POST',
        headers: this.headers(),
        body: JSON.stringify({
          voice_description: description, model_id: 'eleven_ttv_v3', text, seed, loudness: 0.5, guidance_scale: 5,
          // a voice to sound like: its timbre from the recording, its Slovene from the text and the description
          ...(reference ? { reference_audio_base64: Buffer.from(reference).toString('base64'), prompt_strength: 0.4 } : {}),
        }),
        signal: AbortSignal.timeout(120_000),
      })
      if (!r.ok) {
        this.lastError = `design: HTTP ${r.status} ${this.redact(await r.text()).slice(0, 160)}`
        this.o.log?.(`elevenlabs: ${this.lastError}`)
        return null
      }
      const d = (await r.json()) as { previews?: { generated_voice_id?: string; audio_base_64?: string }[] }
      if (this.quota) this.quota.used += text.length
      const takes = (d.previews ?? [])
        .filter(p => p.generated_voice_id && p.audio_base_64)
        .map(p => ({ id: p.generated_voice_id!, audio: new Uint8Array(Buffer.from(p.audio_base_64!, 'base64')) }))
      return takes.length ? takes : null
    } catch (e) {
      this.lastError = this.redact(`design: ${(e as Error).message}`)
      this.o.log?.(`elevenlabs: ${this.lastError}`)
      return null
    }
  }

  /** A voice's sample recording (its library preview): what a design can be asked to sound like. Null when there is none. */
  async preview(id: string): Promise<Uint8Array | null> {
    if (!this.o.key) return null
    try {
      const r = await fetch(`${this.base}/v1/voices/${encodeURIComponent(id)}`, { headers: this.headers(), signal: AbortSignal.timeout(15_000) })
      if (!r.ok) return null
      const url = ((await r.json()) as { preview_url?: string }).preview_url
      if (!url || !/^https?:\/\//.test(url)) return null
      const a = await fetch(url, { signal: AbortSignal.timeout(30_000) })
      if (!a.ok) return null
      const bytes = new Uint8Array(await a.arrayBuffer())
      return bytes.length > 1_000 && bytes.length < 5_000_000 ? bytes : null
    } catch {
      return null
    }
  }

  /** Saves a designed take as a voice of the account (it takes a voice slot): its voice id, or null. */
  async createVoice(name: string, description: string, take: string, others: string[] = []): Promise<string | null> {
    if (!this.o.key) return null
    try {
      const r = await fetch(`${this.base}/v1/text-to-voice`, {
        method: 'POST',
        headers: this.headers(),
        body: JSON.stringify({ voice_name: name, voice_description: description, generated_voice_id: take, played_not_selected_voice_ids: others, labels: { app: 'lani' } }),
        signal: AbortSignal.timeout(60_000),
      })
      if (!r.ok) {
        this.lastError = `create voice: HTTP ${r.status} ${this.redact(await r.text()).slice(0, 160)}`
        this.o.log?.(`elevenlabs: ${this.lastError}`)
        return null
      }
      const v = (await r.json()) as { voice_id?: string }
      if (this.quota && this.quota.voice_slots_used !== undefined) this.quota.voice_slots_used++
      return v.voice_id ?? null
    } catch (e) {
      this.lastError = this.redact(`create voice: ${(e as Error).message}`)
      this.o.log?.(`elevenlabs: ${this.lastError}`)
      return null
    }
  }

  /** Removes a voice this app made from the account, freeing its slot. */
  async deleteVoice(id: string): Promise<boolean> {
    if (!this.o.key) return false
    try {
      const r = await fetch(`${this.base}/v1/voices/${encodeURIComponent(id)}`, { method: 'DELETE', headers: this.headers(), signal: AbortSignal.timeout(30_000) })
      if (r.ok && this.quota && this.quota.voice_slots_used) this.quota.voice_slots_used--
      return r.ok
    } catch {
      return false
    }
  }

  async status() {
    const q = await this.subscription()
    return {
      configured: this.configured,
      up: this.configured && !!q && Date.now() >= this.pausedUntil,
      model: 'eleven_v3',
      quota: q ? { used: q.used, limit: q.limit, remaining: this.remaining(), reserve: this.reserve, resets_at: q.resets_at } : null,
      voices: q && q.voice_limit !== undefined ? { slots: q.voice_limit, used: q.voice_slots_used, edits: q.voice_edits, edits_max: q.voice_edits_max } : null,
      ...(this.lastError ? { last_error: this.lastError } : {}),
    }
  }
}

/**
 * The local Gepard worker: GET /health, POST /synth {text, speaker?} → MP3, 503 while busy. A cast
 * speaker's worker voice comes from the cast ([speakers] overrides it).
 */
export class Gepard implements Engine {
  readonly name = 'gepard' as const
  private health?: { up: boolean; at: number }

  constructor(
    private readonly o: { url?: string; speakers?: Partial<Record<VoiceName, string>>; timeoutMs?: number; log?: Log } = {},
  ) {}

  /** The worker's voice for [voice]: the override, else the cast's, else the gender narrator's. */
  speakerOf(voice: VoiceName): string | undefined {
    const c = cast()
    const a = archetypeOf(voice)
    return this.o.speakers?.[a] ?? c[a]?.gepard ?? this.o.speakers?.[genderOf(voice)] ?? c[genderOf(voice)]?.gepard
  }

  private get url() { return (this.o.url ?? 'http://127.0.0.1:8795').replace(/\/$/, '') }

  async up(): Promise<boolean> {
    if (this.health && Date.now() - this.health.at < 30_000) return this.health.up
    let up = false
    try {
      const r = await fetch(`${this.url}/health`, { signal: AbortSignal.timeout(2_000) })
      up = r.ok && (await r.json().catch(() => null))?.ok === true
    } catch {}
    this.health = { up, at: Date.now() }
    return up
  }

  ready(): Promise<boolean> { return this.up() }

  async synth(text: string, voice: VoiceName): Promise<Take | null> {
    try {
      const speaker = this.speakerOf(voice)
      const r = await fetch(`${this.url}/synth`, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(speaker ? { text, speaker } : { text }),
        signal: AbortSignal.timeout(this.o.timeoutMs ?? 120_000),
      })
      if (!r.ok) {
        if (r.status !== 503) this.o.log?.(`gepard: HTTP ${r.status}`)
        return null
      }
      const bytes = new Uint8Array(await r.arrayBuffer())
      return isMp3(bytes) ? { bytes } : null
    } catch (e) {
      this.health = { up: false, at: Date.now() } // unreachable: treat as down until the next health check
      this.o.log?.(`gepard: ${(e as Error).message}`)
      return null
    }
  }

  async status() { return { up: await this.up(), url: this.url } }
}

/**
 * Engines from the environment: ELEVENLABS_API_KEY (+ ELEVENLABS_BASE_URL), LANI_GEPARD_URL, LANI_FFMPEG (the carrier
 * cut's ffmpeg, "off" for none; else the one on the PATH); clips in [language].
 */
export function enginesFromEnv(env: Record<string, string | undefined>, log?: Log, language = 'sl'): { eleven: ElevenLabs; gepard: Gepard } {
  // The worker's voices (companion/voice-local/voices.json) come from the cast; these override the narrators'.
  const speakers: Partial<Record<VoiceName, string>> = {
    ...(env.LANI_GEPARD_SPEAKER_FEMALE ? { female: env.LANI_GEPARD_SPEAKER_FEMALE } : {}),
    ...(env.LANI_GEPARD_SPEAKER_MALE ? { male: env.LANI_GEPARD_SPEAKER_MALE } : {}),
  }
  return {
    eleven: new ElevenLabs({
      key: env.ELEVENLABS_API_KEY || undefined,
      base: env.ELEVENLABS_BASE_URL,
      reserve: env.LANI_VOICE_RESERVE ? Number(env.LANI_VOICE_RESERVE) : undefined,
      language,
      cut: env.LANI_FFMPEG ? (ffmpegCutter(env.LANI_FFMPEG) ?? null) : undefined,
      log,
    }),
    gepard: new Gepard({ url: env.LANI_GEPARD_URL, speakers, log }),
  }
}

// --- corpus ---------------------------------------------------------------------------------

/**
 * The Slovene forms of the learner's own review cards (spaced repetition): vocabulary items only.
 * Error patterns hold the learner's wrong answers and rules/placeholders aren't speakable, so they're skipped;
 * "Kako ste? / Kako si?" and "hvala (lepa)" give each form, like the app's ReviewPlanner.accepted.
 */
export function cardTexts(items: Record<string, { type?: string; content?: string }>): string[] {
  return Object.values(items).flatMap(i => {
    const c = i.content
    if (i.type !== 'vocabulary' || !c || /[→♂♀…]|\.\.\./.test(c)) return []
    return c.split(' / ').flatMap(alt => {
      const bare = alt.replace(/\s*\([^)]*\)/g, '').trim()
      const full = alt.replace(/\s*\(([^)]*)\)/g, ' $1').replace(/\s+/g, ' ').trim()
      return [...new Set([bare, full])].filter(t => t && t.length <= 60)
    })
  })
}

/** The corpus priorities, in prebuild order (CorpusItem.priority is the index). */
export const PRIORITY_LABELS = ['villagers', 'pack words', 'examples', 'modules', 'scenarios', 'stories A1', 'drills A1', 'scenes', 'ispy', 'stories A2', 'drills A2+', 'stories B1+']

/** The scenes' dialogs. */
const SCENES = PRIORITY_LABELS.indexOf('scenes')
/** What a child says playing «Vidim, vidim» in the scenes (ispy.ts): after the scenes' dialogs. */
const ISPY = PRIORITY_LABELS.indexOf('ispy')
/**
 * The storyteller's tellings at [level]: the A1 ones before the scenes (the learner's level: the car plays a telling
 * only when its clips exist), A2 after them, then B1 and up.
 */
const storyPriority = (level: (typeof LEVELS)[number]) =>
  PRIORITY_LABELS.indexOf(level === 'A1' ? 'stories A1' : level === 'A2' ? 'stories A2' : 'stories B1+')
/**
 * The car's drills at [level]: the A1 ones after the A1 tellings and before the scenes (car content: the car plays
 * a drill's item only when its clips exist, where the app voices a scene's line when it is played), the others after
 * the A2 tellings.
 */
const drillPriority = (level: (typeof LEVELS)[number]) => PRIORITY_LABELS.indexOf(level === 'A1' ? 'drills A1' : 'drills A2+')

/**
 * Every text the app speaks from curated and published content (in the village's [language]: Slovene unless its culture
 * pack says otherwise; a pack's words in the pack's), deduplicated per voice,
 * in prebuild order: the villagers' lines in their speaker's voice (remember lines carry a memory
 * and are voiced live), the review cards and pack words (and scene objects), examples (the packs', the scenes',
 * the grammar book's), modules,
 * scenarios, the storyteller's stories told at A1 ([storyTexts]), the car's drills at A1 ([drillTexts]: in the
 * narrator's voice, a riddle in its teller's), then the scenes' dialogs (each person's lines in their villager's speaker,
 * else their sprite's voice; the learner's choices in the default voice) and the keepers' talks (the charcoal burner's
 * at his pile: his meeting and his story, every telling at every level, as a scene's dialog in his speaker's voice), then
 * the stories told at A2, the drills above A1, the stories at B1 and up.
 */
export function corpus(input: {
  packs: (Pick<Pack, 'id' | 'words'> & { language?: Pack['language'] })[]
  modules: ModuleSpec[]
  scenarios: Scenario[]
  scenes?: ResolvedScene[]
  /**
   * Who is at a spot of the landscape now and then (a culture's world.json `spots`: the charcoal burner), each with every
   * telling of his talks (cultures.ts keeperTellings): the app plays them as a scene's dialog, in his speaker's voice.
   */
  keepers?: { id: string; voice: 'female' | 'male'; speaker?: string; tellings: { lines: ResolvedScene['dialogs'][number]['lines'] }[] }[]
  villagers?: (Pick<Villager, 'id' | 'voice' | 'speaker' | 'lines'> & { art?: string })[]
  cards?: string[]
  /**
   * «Vidim, vidim» (ispy.ts, companion/ispy): the child's lines by language and the scenes' clues; a scene's are voiced in
   * the voice of each child who may play there (its own children, else every child of the cast: one comes by).
   */
  ispy?: { lines: Record<string, ISpyLines>; books: Record<string, ISpyBook> }
  /** The grammar book's pages: their examples in the village's language (grammar.ts). */
  grammar?: Pick<GrammarPage, 'id' | 'language' | 'examples'>[]
  /** The storyteller's stories (stories.ts): the curated ones of the village's culture pack, and the tutor's. */
  stories?: Pick<Story, 'id' | 'language' | 'teller' | 'teaser' | 'levels' | 'chapters'>[]
  /** The car's audio drills (drills.ts): the curated ones of the village's language. */
  drills?: Drill[]
  /**
   * Whether a villager has a voice of their own that is ready (profiles.ts; `personVoice("@id")` once the profiles are
   * registered): a storyteller who has one tells in it ("@janez"), as the app asks for it by the fire.
   */
  ownVoice?: (villager: string) => boolean
  language?: string
  /**
   * Whom the content speaks to (addressee.ts): every text is rendered for them first ({learner}, {m:…|f:…}), as the app
   * gets it, so a clip is keyed by what the app says. Without, a text with such a placeholder is voiced when played.
   */
  learner?: Addressee
}): CorpusItem[] {
  if (input.learner) {
    const { ownVoice, learner, ...content } = input
    input = { ...renderDeep(content, learner), ownVoice }
  }
  const c = input
  const language = c.language ?? 'sl'
  const villagerVoice = new Map((c.villagers ?? []).map(v => [v.id, speakerFor(v)]))
  const out: CorpusItem[] = []
  const seen = new Set<string>()
  const add = (text: string | undefined, source: string, priority: number, voice: VoiceName = 'female') => {
    if (!text) return
    for (const p of phrases(text)) {
      const key = clipKey(normalizeText(p), voice)
      if (seen.has(key)) continue
      seen.add(key)
      out.push({ text: p, voice, source, priority })
    }
  }
  // The villagers first: their own voices are what a new speaker changes most.
  for (const v of c.villagers ?? []) {
    const voice = speakerFor(v)
    // every kind's lines and their gift lines (lines.gift: liked, ordinary, rare)
    for (const l of allLines(v.lines)) {
      const t = said(l, language)
      if (!t.includes('{')) add(t, `villager:${v.id}`, 0, voice)
    }
  }
  for (const t of c.cards ?? []) add(t, 'cards', 1) // what the learner reviews every day
  const lang = (p: { language?: Pack['language'] }) => ({ language: p.language ?? 'sl' })
  for (const p of c.packs) for (const w of p.words) add(wordOf(lang(p), w), `pack:${p.id}`, 1)
  // a scene's texts are in its language (Slovene, or its village's: an Italian scene's "it")
  const inScene = (s: ResolvedScene, t: Partial<Record<string, unknown>> | undefined, prefix = '') => t?.[prefix + (s.language ?? 'sl')] as string | undefined
  for (const s of c.scenes ?? []) for (const o of s.objects) add(inScene(s, o), `scene:${s.id}`, 1) // pack words too; a dedupe when the pack is here
  for (const p of c.packs) for (const w of p.words) add(exampleOf(lang(p), w), `pack:${p.id}`, 2)
  for (const s of c.scenes ?? []) for (const o of s.objects) add(inScene(s, o, 'example_'), `scene:${s.id}`, 2)
  for (const g of c.grammar ?? []) if (g.language === language) for (const e of g.examples) add(e[language] as string | undefined, `grammar:${g.id}`, 2)
  for (const m of c.modules) {
    const src = `module:${m.id}`
    for (const e of m.exercises) {
      switch (e.type) {
        case 'flashcard':
          if (e.speak) add(e.front, src, 3)
          break
        case 'choice':
          add(e.say, src, 3)
          add(e.audio, src, 3)
          if (e.say_options) e.options.forEach(o => add(o, src, 3))
          break
        case 'multi':
          if (e.say_options) e.options.forEach(o => add(o, src, 3))
          break
        case 'reorder':
        case 'translate':
          add(e.say, src, 3)
          break
        case 'scenario':
          add(e.line, src, 3)
          break
        case 'dictation':
          add(e.audio, src, 3)
          break
        case 'speak':
          add(e.say, src, 3)
          break
      }
    }
  }
  for (const s of c.scenarios) {
    add(s.opener_sl, `scenario:${s.id}`, 4, speakerFor({ voice: s.voice ?? 'female', speaker: s.speaker }))
    for (const h of s.vocabulary_hints ?? []) add(h.sl, `scenario:${s.id}`, 4)
  }
  // the stories: the A1 tellings before the scenes' dialogs (a text of both is the A1 telling's), the others after them
  const tellerVoice = (id: string) => (c.ownVoice?.(id) ? `@${id}` : villagerVoice.get(id))
  const stories = storyTexts(c.stories ?? [], language, tellerVoice)
  for (const t of stories) if (t.priority < SCENES) add(t.text, t.source, t.priority, t.voice)
  // the car's drills: the A1 ones before the scenes' dialogs too, in the narrator's voice (a riddle in its teller's, as
  // the stories), the others after the A2 tellings
  const drills = drillTexts(c.drills ?? [], language).map(t => ({
    ...t,
    priority: drillPriority(t.level),
    voice: (t.teller && tellerVoice(t.teller)) || 'female',
  }))
  for (const t of drills) if (t.priority < SCENES) add(t.text, t.source, t.priority, t.voice)
  for (const s of c.scenes ?? []) {
    const src = `scene:${s.id}`
    const voices = new Map(s.people.map(p => [p.id, (p.villager && villagerVoice.get(p.villager)) || spriteVoice(p.art)]))
    // a counting dialog's texts with the number in ({n}, its forms) are voiced when played, with the village's number
    const said = (t: Partial<Record<string, unknown>> | undefined) => {
      const x = inScene(s, t)
      return x?.includes('{') ? undefined : x
    }
    for (const d of s.dialogs) {
      let speaker: VoiceName = 'female' // a reply comes from whoever spoke last
      for (const l of d.lines) {
        if (l.who) {
          speaker = voices.get(l.who) ?? 'female'
          add(said(l), src, SCENES, speaker)
        }
        for (const ch of l.choices) {
          add(said(ch), src, SCENES)
          add(said(ch.reply), src, SCENES, speaker)
        }
      }
    }
  }
  // «Vidim, vidim»: the child's lines, each thing's clues, its find and its reveal, in every child's voice who plays there
  if (c.ispy) {
    const children = (c.villagers ?? []).filter(v => v.art?.startsWith('child'))
    for (const s of c.scenes ?? []) {
      const lang = s.language ?? 'sl'
      const lines = c.ispy.lines[lang]
      if (!lines) continue
      const own = s.people.filter(p => p.art.startsWith('child')).map(p => (p.villager && villagerVoice.get(p.villager)) || spriteVoice(p.art))
      const voices = [...new Set(own.length ? own : children.map(v => villagerVoice.get(v.id) ?? speakerFor(v)))]
      const words: Record<string, string> = {}
      const plural = new Set<string>()
      for (const o of s.objects) {
        const w = inScene(s, o)
        if (!w) continue
        words[o.slot] = w
        if (o.plural === w) plural.add(o.slot)
      }
      for (const t of ispyTexts(lines, c.ispy.books[s.id], lang, words, plural)) for (const v of voices) add(t, `ispy:${s.id}`, ISPY, v)
    }
  }
  // the keepers' talks, as a scene's dialog: his lines and his replies in his speaker's voice, the learner's choices in
  // the default one
  for (const k of c.keepers ?? []) {
    const src = `keeper:${k.id}`
    const voice = speakerFor(k)
    const say = (t: Partial<Record<string, unknown>> | undefined) => {
      const x = t?.[language]
      return typeof x === 'string' && x && !x.includes('{') ? x : undefined
    }
    for (const t of k.tellings) for (const l of t.lines) {
      if (l.who) add(say(l), src, SCENES, voice)
      for (const ch of l.choices) {
        add(say(ch), src, SCENES)
        add(say(ch.reply), src, SCENES, voice)
      }
    }
  }
  const later = drillPriority('A2')
  for (const t of stories) if (t.priority > SCENES && t.priority < later) add(t.text, t.source, t.priority, t.voice)
  for (const t of drills) if (t.priority > SCENES) add(t.text, t.source, t.priority, t.voice)
  for (const t of stories) if (t.priority > later) add(t.text, t.source, t.priority, t.voice)
  return out.sort((a, b) => a.priority - b.priority)
}

/**
 * The texts of the storyteller's stories told in [language], as the app and the car play them: every evening (chapter)
 * at every level it is told at, the learner's level first ('stories A1', before the scenes; 'stories A2' and
 * 'stories B1+' after them), so a budget that runs out has the easiest tellings. The teller's lines and the replies to the learner's choices in the teller's
 * voice ([tellerVoice] of their villager id: their own designed voice when it is ready, "@janez", which the app asks for
 * first by the fire and the car tries first; else their speaker, the scenes' mapping; the female narrator for a teller
 * not in the cast, as the car has it), the learner's right choices in the default voice, as the scenes' choices. A level's
 * tellings come first as the car plays them (the lines, the right choices and their replies), then the replies to the
 * wrong choices (heard in the app only). Texts with a placeholder ({…}) are voiced when played, as the scenes'.
 */
export function storyTexts(
  stories: Pick<Story, 'id' | 'language' | 'teller' | 'teaser' | 'levels' | 'chapters'>[],
  language: string,
  tellerVoice: (villager: string) => VoiceName | undefined,
): CorpusItem[] {
  const out: CorpusItem[] = []
  const told = stories.filter(s => (s.language ?? 'sl') === language)
  for (const level of LEVELS) {
    const priority = storyPriority(level)
    const wrong: CorpusItem[] = []
    for (const s of told) {
      const source = `story:${s.id}`
      const voice = tellerVoice(s.teller) ?? 'female'
      const said = (t: Partial<Record<string, unknown>> | undefined) => {
        const x = t?.[language]
        return typeof x === 'string' && x && !x.includes('{') ? x : undefined
      }
      const push = (to: CorpusItem[], t: Partial<Record<string, unknown>> | undefined, v: VoiceName) => {
        const text = said(t)
        if (text) to.push({ text, voice: v, source, priority })
      }
      for (const ch of chaptersOf(s)) {
        for (const l of ch.levels[level]?.lines ?? []) {
          if (!l.choices.length) push(out, l, voice)
          for (const c of l.choices) {
            if (c.ok) {
              push(out, c, 'female')
              push(out, c.reply, voice)
            } else push(wrong, c.reply, voice)
          }
        }
      }
    }
    out.push(...wrong)
  }
  return out
}

// --- store ------------------------------------------------------------------------------------

export type VoiceOptions = {
  /** Live dialogue lines may use the ElevenLabs reserve. */
  live?: boolean
  /** Only these engines (e.g. ['elevenlabs'] to replace local clips). */
  engines?: EngineName[]
  /** Replace an existing clip made by another engine. */
  replace?: boolean
  /** Replace an existing clip whatever made it (a speaker's voice was swapped: voice-build --revoice). */
  force?: boolean
}

/** Carrier re-voices a day, by default (about 360 subscription characters: eleven_v3 counts about half of 720 sent). */
export const DEFAULT_REVOICE_DAILY = 40

/**
 * Of the day's cap, what the lazy re-voice leaves for Jan's re-records on request (the app's long press on 🔊): a
 * quarter, at most 10. So a re-record goes first even on a day the lazy re-voice has much to do.
 */
export const redoKept = (daily: number) => Math.min(10, Math.floor(Math.max(0, daily) / 4))

/** What re-voicing one clip in its carrier sentence came to. */
export type Revoiced = 'made' | 'plain' | 'stop'

/**
 * What a re-record on request (POST /voice/redo) came to: 'done', the new clip; 'not_needed', the clip was re-recorded
 * on request today already (once a clip a day): this is that take; 'limit', not now: the day's cap is spent ('cap') or
 * the characters left are the reserve for live lines ('reserve'); 'failed', ElevenLabs can't now (not set up, down, an error).
 */
export type Redo =
  | { status: 'done'; clip: Clip }
  | { status: 'not_needed'; clip: Clip; message: string }
  | { status: 'limit'; reason: 'cap' | 'reserve'; message: string }
  | { status: 'failed'; message: string }

/**
 * What voice-build --level --run (or --denoise --run) came to: clips done (of them [kept] as they were, only recorded,
 * and [limited]), left as they were (gone, busy, or changed meanwhile), failed; [left]: still waiting.
 */
export type Processed = { done: number; kept: number; limited: number; skipped: number; failed: number; left: number }
export type Denoised = Processed

export class VoiceStore {
  readonly dir: string
  readonly filesDir: string
  private readonly db: Database
  private readonly running = new Map<string, Promise<Clip | null>>()
  private queue: CorpusItem[] = []
  private draining = false
  private liveCalls: number[] = []
  private revoicing = false
  private revoiceSoon?: ReturnType<typeof setTimeout>
  private denoiser?: Denoiser | null
  private noDenoiserLogged = false
  private levelling?: Leveller | null
  private noLevelLogged = false
  /** Every clip's file, and the database's version, when last looked at: see [checkExternal]. */
  private seen?: { version: number; files: Map<string, string> }
  private watching?: ReturnType<typeof setInterval>

  constructor(
    private readonly o: {
      appDir: string
      /** The store's directory: <appDir>/voice when not given (the bridge passes paths.ts voiceDir: the voice cache). */
      dir?: string
      engines: Engine[]
      log?: Log
      /** Dry runs: read an existing DB without creating anything. */
      readonly?: boolean
      queueDelayMs?: number
      /** Called when a background batch made new clips, or another process changed them ([watch]). */
      onUpdated?: (made: number) => void
      /**
       * The filter for the voices the cast marks `denoise`: ffmpeg when the node has it (undefined), none (null: their
       * clips are stored as they are, and logged), or another.
       */
      denoise?: Denoiser | null
      /**
       * The level every clip is brought to before it is stored: ffmpeg to LEVEL when the node has it (undefined), none
       * (null: clips are stored as they are, and logged), or another.
       */
      level?: Leveller | null
      liveLimit?: { max: number; windowMs: number }
      /**
       * Clips re-voiced a day (LANI_VOICE_REVOICE_DAILY; DEFAULT_REVOICE_DAILY; 0: none): old-style short narrator
       * clips in their carrier sentence as the app asks for them, and Jan's re-records on request (see [redoKept]).
       */
      revoiceDaily?: number
    },
  ) {
    this.dir = o.dir ?? join(o.appDir, 'voice')
    this.filesDir = join(this.dir, 'files')
    const path = join(this.dir, 'voice.db')
    if (o.readonly && !existsSync(path)) {
      this.db = new Database(':memory:')
    } else {
      if (!o.readonly) mkdirSync(this.filesDir, { recursive: true })
      this.db = new Database(path, o.readonly ? { readonly: true } : { create: true })
    }
    if (!o.readonly || !existsSync(path)) {
      this.db.exec('PRAGMA journal_mode = WAL')
      this.db.exec(`CREATE TABLE IF NOT EXISTS clips (
        key TEXT PRIMARY KEY,
        norm TEXT NOT NULL,
        text TEXT NOT NULL,
        voice TEXT NOT NULL,
        engine TEXT NOT NULL,
        file TEXT NOT NULL,
        chars INTEGER NOT NULL,
        created_at TEXT NOT NULL,
        source TEXT NOT NULL,
        method TEXT,
        hits INTEGER NOT NULL DEFAULT 0,
        denoise TEXT,
        level REAL,
        gain REAL
      )`)
      // a store from before the carrier sentence: how a clip was made, and how often the app asked for it; from before
      // the denoise filter: the filter it went through; from before the level: the loudness it is at, and the gain
      const cols = new Set((this.db.query('PRAGMA table_info(clips)').all() as { name: string }[]).map(c => c.name))
      for (const [col, spec] of [['method', 'TEXT'], ['hits', 'INTEGER NOT NULL DEFAULT 0'], ['denoise', 'TEXT'], ['level', 'REAL'], ['gain', 'REAL']]) {
        try {
          if (!cols.has(col)) this.db.exec(`ALTER TABLE clips ADD COLUMN ${col} ${spec}`)
        } catch {} // another process (voice-build) added it meanwhile
      }
      this.db.exec('CREATE INDEX IF NOT EXISTS clips_file ON clips (file)')
      // carrier re-voices and re-records per day (local date), for the daily cap
      this.db.exec('CREATE TABLE IF NOT EXISTS revoices (day TEXT PRIMARY KEY, n INTEGER NOT NULL)')
      // the clips re-recorded on request, per day: once a clip a day
      this.db.exec('CREATE TABLE IF NOT EXISTS redos (key TEXT NOT NULL, day TEXT NOT NULL, PRIMARY KEY (key, day))')
    }
    this.db.exec('PRAGMA busy_timeout = 5000')
  }

  get engines() { return this.o.engines }

  /** The ElevenLabs engine, when the store has one. */
  private get eleven(): ElevenLabs | undefined {
    return this.o.engines.find((e): e is ElevenLabs => e instanceof ElevenLabs)
  }

  get revoiceDaily(): number { return this.o.revoiceDaily ?? DEFAULT_REVOICE_DAILY }

  /** Of the day's cap, what the lazy re-voice may use: the rest is kept for re-records on request ([redoKept]). */
  get lazyDaily(): number { return this.revoiceDaily - redoKept(this.revoiceDaily) }

  /** The characters ElevenLabs would be sent for [text] in [voice] (a short word's carrier sentence). */
  cost(text: string, voice: VoiceName): number {
    return this.eleven?.cost(text, voice) ?? text.trim().length
  }

  /** The store's database, which the voice profiles share. */
  get database(): Database { return this.db }

  /** Forgets every clip in [voice] (someone's own voice was replaced): they are made again as they are heard. */
  dropVoice(voice: VoiceName) {
    for (const c of this.db.query('SELECT file FROM clips WHERE voice = ?').all(voice) as { file: string }[]) {
      try {
        unlinkSync(join(this.filesDir, c.file))
      } catch {}
    }
    this.db.query('DELETE FROM clips WHERE voice = ?').run(voice)
    for (const key of this.seen?.files.keys() ?? []) if (key.startsWith(`${voice}:`)) this.seen?.files.delete(key)
  }

  get(norm: string, voice: VoiceName): Clip | undefined {
    return (this.db.query('SELECT * FROM clips WHERE key = ?').get(clipKey(norm, voice)) as Clip | null) ?? undefined
  }

  all(): Clip[] {
    return this.db.query('SELECT * FROM clips ORDER BY created_at').all() as Clip[]
  }

  /** normalized text → speaker (female, male, grandma, …) → file URL. */
  index(): Record<string, Record<VoiceName, string>> {
    const out: Record<string, Record<VoiceName, string>> = {}
    for (const c of this.db.query('SELECT norm, voice, file FROM clips').all() as Pick<Clip, 'norm' | 'voice' | 'file'>[]) {
      ;(out[c.norm] ??= {})[c.voice] = fileUrl(c.file)
    }
    return out
  }

  path(file: string): string | undefined {
    if (!FILE.test(file)) return undefined
    const p = join(this.filesDir, file)
    return existsSync(p) ? p : undefined
  }

  /** The denoise filter, when there is one (ffmpeg, looked for once). */
  private get denoise(): Denoiser | undefined {
    if (this.denoiser === undefined) this.denoiser = this.o.denoise === undefined ? (ffmpegDenoiser() ?? null) : this.o.denoise
    return this.denoiser ?? undefined
  }

  /**
   * [bytes] through the filter the cast gives [voice] (`denoise`), for an ElevenLabs clip (the hiss is in the ElevenLabs
   * voice; the local worker's clips are clean): the filtered bytes and the preset. When it can't be filtered now (no
   * ffmpeg, ffmpeg failed), the bytes as they are, logged: voice-build --denoise filters it later.
   */
  private async filtered(bytes: Uint8Array, voice: VoiceName, engine: EngineName, text: string): Promise<{ bytes: Uint8Array; denoise: DenoisePreset | null }> {
    const preset = engine === 'elevenlabs' ? denoiseOf(voice) : undefined
    if (!preset) return { bytes, denoise: null }
    const f = this.denoise
    const out = f ? await f(bytes, preset).catch(() => null) : null
    if (out) return { bytes: out, denoise: preset }
    if (f || !this.noDenoiserLogged) {
      this.noDenoiserLogged ||= !f
      this.o.log?.(`voice: "${text}" (${voice}) stored without its filter (${preset}): ${f ? 'ffmpeg failed' : 'no ffmpeg'}; voice-build --denoise filters it later`)
    }
    return { bytes, denoise: null }
  }

  /** The level, when there is one (ffmpeg, looked for once). */
  private get leveller(): Leveller | undefined {
    if (this.levelling === undefined) this.levelling = this.o.level === undefined ? (ffmpegLeveller() ?? null) : this.o.level
    return this.levelling ?? undefined
  }

  /** The loudness clips are brought to (LUFS): the level's target. */
  get levelTarget(): number { return this.leveller?.target ?? LEVEL.target }

  /**
   * [bytes] as they are stored: through [voice]'s filter ([filtered]) and brought to the level, in one encode when both
   * are ffmpeg's. What can't be done now (no ffmpeg, ffmpeg failed) is left out and logged once: voice-build --level
   * (and --denoise) does it later.
   */
  private async processed(bytes: Uint8Array, voice: VoiceName, engine: EngineName, text: string): Promise<{ bytes: Uint8Array; denoise: DenoisePreset | null; level: number | null; gain: number | null }> {
    const preset = engine === 'elevenlabs' ? denoiseOf(voice) : undefined
    const lv = this.leveller
    if (preset && lv && this.o.denoise === undefined) {
      const r = await lv.level(bytes, preset)
      if (r) return { bytes: r.bytes, denoise: preset, level: lv.target, gain: r.gain }
    }
    const f = await this.filtered(bytes, voice, engine, text)
    const r = lv ? await lv.level(f.bytes) : null
    if (r) return { ...f, bytes: r.bytes, level: lv!.target, gain: r.gain }
    if (!this.noLevelLogged) {
      this.noLevelLogged = true
      this.o.log?.(`voice: "${text}" (${voice}) stored without the level: ${lv ? 'ffmpeg failed' : 'no ffmpeg'}; voice-build --level levels it later (logged once)`)
    }
    return { ...f, level: null, gain: null }
  }

  private async save(
    norm: string, text: string, voice: VoiceName, engine: EngineName, bytes: Uint8Array, source: string,
    take: Omit<Take, 'bytes'> & { /** Another take of the same (a re-record): a name of its own. */ salt?: string } = {},
  ): Promise<Clip> {
    const key = clipKey(norm, voice)
    // a voice that hisses (the cast's `denoise`): filtered; every clip: brought to the level
    const f = await this.processed(bytes, voice, engine, text)
    // An ElevenLabs clip's name carries the voice id, so a swapped voice's clips get new names (the app caches by name);
    // a carrier cut is a new take of the same text, so it gets a new name too and the app downloads it; so does a re-record,
    // and so does a filtered or levelled clip.
    const tag = engine === 'elevenlabs' ? `|${elevenlabsOf(voice) ?? ''}` : ''
    const how = take.method === 'carrier' ? '|carrier' : ''
    const salt = take.salt ? `|${take.salt}` : ''
    const filter = f.denoise ? `|denoise:${f.denoise}` : ''
    const level = f.level !== null ? `|level:${f.level}` : ''
    const file = `${createHash('sha1').update(`${key}|${engine}${tag}${how}${salt}${filter}${level}`).digest('hex')}.mp3`
    const p = join(this.filesDir, file)
    writeFileSync(`${p}.tmp`, f.bytes)
    renameSync(`${p}.tmp`, p)
    const old = this.get(norm, voice)
    const clip: Clip = {
      key, norm, text, voice, engine, file, chars: take.chars ?? text.length, created_at: new Date().toISOString(), source,
      method: take.method ?? null, hits: old?.hits ?? 0, denoise: f.denoise, level: f.level, gain: f.gain,
    }
    this.db
      .query('INSERT OR REPLACE INTO clips (key, norm, text, voice, engine, file, chars, created_at, source, method, hits, denoise, level, gain) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)')
      .run(key, norm, text, voice, engine, file, clip.chars, clip.created_at, source, clip.method ?? null, clip.hits ?? 0, clip.denoise ?? null, clip.level ?? null, clip.gain ?? null)
    this.seen?.files.set(key, file) // the bridge's own clip: not another process's change
    if (old && old.file !== file) {
      try {
        unlinkSync(join(this.filesDir, old.file))
      } catch {}
    }
    return clip
  }

  /**
   * A character's clip waits for ElevenLabs while their gender's clip exists: the app plays that one
   * meanwhile, and a local clip in a character key would be no better.
   */
  waitsForElevenLabs(norm: string, voice: VoiceName): boolean {
    // someone's own voice is only ever theirs: a local clip in it would be someone else speaking
    if (personVoice(voice)) return true
    return !isNarrator(voice) && !!this.get(norm, genderOf(voice))
  }

  /**
   * The clip for [text] in [voice] (a cast speaker): an existing one, else ElevenLabs if the quota
   * allows, else the local worker if it is up (not for a character whose gender clip exists, see
   * [waitsForElevenLabs]). Null when no engine can make it now, or for a speaker not in the cast;
   * never throws.
   */
  async voice(text: string, voice: VoiceName, source: string, opt: VoiceOptions = {}): Promise<Clip | null> {
    const norm = normalizeText(text)
    if (!norm || !isVoice(voice)) return null
    const have = this.get(norm, voice)
    if (have && !opt.force && !(opt.replace && opt.engines && !opt.engines.includes(have.engine))) return have
    const waits = this.waitsForElevenLabs(norm, voice)
    const key = clipKey(norm, voice)
    const busy = this.running.get(key)
    if (busy) return busy
    const job = (async () => {
      const t = text.trim()
      for (const e of this.o.engines) {
        if (opt.engines && !opt.engines.includes(e.name)) continue
        if (waits && e.name !== 'elevenlabs') continue
        try {
          // a short narrator word costs its carrier sentence
          if (!(await e.ready(e.cost?.(t, voice) ?? t.length, !!opt.live))) continue
          const take = await e.synth(t, voice, { live: !!opt.live })
          if (take) return await this.save(norm, t, voice, e.name, take.bytes, source, take)
        } catch (err) {
          this.o.log?.(`voice: ${e.name} failed: ${(err as Error).message}`)
        }
      }
      return have ?? null
    })().finally(() => this.running.delete(key))
    this.running.set(key, job)
    return job
  }

  /**
   * A live line from the app: rate-limited, may use the reserve; the speaker's gender clip, then the
   * other narrator's, beats nothing.
   */
  async say(text: string, voice: VoiceName): Promise<Clip | null> {
    const norm = normalizeText(text)
    const have = this.get(norm, voice)
    if (have) return this.served(have)
    const other = () => {
      const c = fallbacks(voice).map(v => this.get(norm, v)).find(Boolean)
      return c ? this.served(c) : null
    }
    if (!this.running.has(clipKey(norm, voice))) {
      const lim = this.o.liveLimit ?? { max: 60, windowMs: 600_000 }
      const now = Date.now()
      this.liveCalls = this.liveCalls.filter(t => now - t < lim.windowMs)
      if (this.liveCalls.length >= lim.max) return other()
      this.liveCalls.push(now)
    }
    return (await this.voice(text, voice, 'live', { live: true })) ?? other()
  }

  // --- the carrier re-voice: short narrator words from before the carrier sentence ---------------------------------

  /** The app downloaded [file]: counted, and an old-style short narrator clip is re-voiced soon (see [served]). */
  requested(file: string) {
    if (this.o.readonly || !FILE.test(file)) return
    const c = this.db.query('SELECT * FROM clips WHERE file = ?').get(file) as Clip | null
    if (c) this.served(c)
  }

  /**
   * A clip the app is given (a download, /voice/say): its request is counted, and when it is old-style (a short
   * narrator word said plainly) it is served as it is now and re-voiced in its carrier sentence soon.
   */
  private served(c: Clip): Clip {
    if (this.o.readonly) return c
    try {
      this.db.query('UPDATE clips SET hits = hits + 1 WHERE key = ?').run(c.key)
    } catch {}
    if (this.oldStyle(c)) this.scheduleRevoice()
    return c
  }

  /** A clip of a short text in a carrier voice that ElevenLabs made plainly, before the carrier sentence: it may sound English. */
  oldStyle(c: Clip): boolean {
    return c.engine === 'elevenlabs' && !c.method && !!this.eleven?.carrierFor(c.text, c.voice)
  }

  /** The old-style clips, most asked for first, then the oldest; [asked]: only those the app has asked for. */
  revoiceQueue(asked = true): Clip[] {
    const narrators = Object.keys(cast()).filter(carrierVoice)
    if (!this.eleven || !narrators.length) return []
    const rows = this.db
      .query(`SELECT * FROM clips WHERE engine = 'elevenlabs' AND voice IN (${narrators.map(() => '?').join(', ')})`)
      .all(...narrators) as Clip[]
    return rows
      .filter(c => (!asked || (c.hits ?? 0) > 0) && this.oldStyle(c))
      .sort((a, b) => (b.hits ?? 0) - (a.hits ?? 0) || a.created_at.localeCompare(b.created_at))
  }

  /** Carrier re-voices today (the node's local date), the bridge's and voice-build's. */
  revoicedToday(): number {
    try {
      return (this.db.query('SELECT n FROM revoices WHERE day = ?').get(localDay()) as { n: number } | null)?.n ?? 0
    } catch {
      return 0 // a store from before, only read
    }
  }

  /**
   * [c] again, said in its carrier sentence: 'made' (under a new file name, so the app downloads it); 'plain' when the
   * word can't be cut out of the take (it keeps its clip and isn't tried again); 'stop' when it can't be now: no ffmpeg,
   * the quota down to the reserve (kept for live lines), an error. Both paid outcomes count for the day.
   */
  async revoiceOne(c: Clip): Promise<Revoiced> {
    const el = this.eleven
    if (this.o.readonly || !el || !el.usesCarrier(c.text, c.voice) || this.running.has(c.key)) return 'stop'
    const cost = el.cost(c.text, c.voice)
    if (!(await el.ready(cost, false))) return 'stop'
    // [paid]: the take counts for the day whatever came of it
    const job = (async (): Promise<[Revoiced, Clip | null, boolean]> => {
      const r = await el.carrierTake(c.text, c.voice)
      if (r instanceof Uint8Array) return ['made', await this.save(c.norm, c.text, c.voice, 'elevenlabs', r, c.source, { method: 'carrier', chars: cost }), true]
      if (r?.retry) return ['stop', c, true] // ffmpeg failed, not the word: it stays old-style
      if (r) {
        this.o.log?.(`voice: "${c.text}" (${c.voice}) keeps its plain clip: ${r.miss}`)
        this.db.query("UPDATE clips SET method = 'plain' WHERE key = ?").run(c.key)
        return ['plain', this.get(c.norm, c.voice) ?? null, true]
      }
      return ['stop', c, false]
    })()
    this.running.set(c.key, job.then(([, clip]) => clip).finally(() => this.running.delete(c.key)))
    const [out, , paid] = await job
    if (paid) this.countForToday()
    return out
  }

  /** One more re-voice (or re-record) in today's cap. */
  private countForToday() {
    this.db.query('INSERT INTO revoices (day, n) VALUES (?, 1) ON CONFLICT (day) DO UPDATE SET n = n + 1').run(localDay())
  }

  /** Re-records on request today (the node's local date). */
  redosToday(): number {
    try {
      return (this.db.query('SELECT COUNT(*) AS n FROM redos WHERE day = ?').get(localDay()) as { n: number }).n
    } catch {
      return 0 // a store from before, only read
    }
  }

  /** Whether the clip [key] was re-recorded on request today. */
  redoneToday(key: string): boolean {
    try {
      return !!this.db.query('SELECT 1 FROM redos WHERE key = ? AND day = ?').get(key, localDay())
    } catch {
      return false
    }
  }

  /**
   * Jan's re-record of [text] in [voice] (the app's long press on 🔊, POST /voice/redo): made again now with ElevenLabs,
   * ahead of the lazy re-voice. A short text in a carrier voice is said in its carrier sentence (like the lazy re-voice),
   * anything else is a plain re-take (eleven_v3 never says a text the same way twice); a clip the local worker made is
   * made with ElevenLabs. The new take gets a new file name, the old file goes, and the app is told (voice_updated).
   * Once a clip a day; each counts in the day's cap ([revoiceDaily], shared with the lazy re-voice, which leaves
   * [redoKept] of it for these) and never goes into the reserve for live lines. A text without a clip is voiced as a
   * live line is (/voice/say), not counted. Never throws.
   */
  async redo(text: string, voice: VoiceName): Promise<Redo> {
    const norm = normalizeText(text)
    if (this.o.readonly || !norm || !isVoice(voice)) return { status: 'failed', message: 'nothing to re-record' }
    const key = clipKey(norm, voice)
    try {
      // being made right now (the lazy re-voice, a live line): that take is the new one
      const busy = this.running.get(key)
      if (busy) {
        const had = this.get(norm, voice)?.file
        const c = await busy
        if (c && c.voice === voice && c.file !== had) return { status: 'done', clip: c }
      }
      const have = this.get(norm, voice)
      if (!have) {
        const c = await this.say(text, voice)
        return c ? { status: 'done', clip: c } : { status: 'failed', message: 'no engine can voice it now' }
      }
      if (this.redoneToday(key)) return { status: 'not_needed', clip: have, message: `"${have.text}" was re-recorded today already (once a clip a day)` }
      const el = this.eleven
      if (!el?.configured) return { status: 'failed', message: 'ElevenLabs is not set up on the node' }
      const today = this.revoicedToday()
      if (today >= this.revoiceDaily) return { status: 'limit', reason: 'cap', message: `no re-recordings left today (${today} of ${this.revoiceDaily})` }
      const cost = el.cost(have.text, voice)
      if (!(await el.ready(cost, false))) {
        const left = el.remaining()
        return left !== undefined && left - cost < el.reserve
          ? { status: 'limit', reason: 'reserve', message: `the ${left} ElevenLabs characters left are kept for live lines (the reserve is ${el.reserve})` }
          : { status: 'failed', message: 'ElevenLabs cannot make it now' }
      }
      const before = el.remaining()
      const job = (async (): Promise<Clip | null> => {
        const take = await el.synth(have.text, voice)
        return take ? await this.save(norm, have.text, voice, 'elevenlabs', take.bytes, have.source, { ...take, salt: `redo ${new Date().toISOString()}` }) : null
      })()
      this.running.set(key, job.catch(() => null).finally(() => this.running.delete(key)))
      const c = await job
      // a take paid for counts, whatever came of it
      if (c || el.remaining() !== before) this.countForToday()
      if (!c) return { status: 'failed', message: 'ElevenLabs could not make it now' }
      this.db.query('INSERT OR IGNORE INTO redos (key, day) VALUES (?, ?)').run(key, localDay())
      this.o.log?.(`voice: "${c.text}" (${voice}) re-recorded on request, ${c.method ?? 'plainly'} (${this.revoicedToday()}/${this.revoiceDaily} today)`)
      this.o.onUpdated?.(1)
      return { status: 'done', clip: c }
    } catch (e) {
      this.o.log?.(`voice: re-recording "${text}" (${voice}) failed: ${(e as Error).message}`)
      return { status: 'failed', message: 'the re-record failed' }
    }
  }

  /** The old-style clips the app asked for and that wait (the cap or the reserve stopped them): re-voiced soon. */
  resumeRevoice() {
    if (this.revoiceQueue().length) this.scheduleRevoice()
  }

  private scheduleRevoice() {
    if (this.o.readonly || this.revoicing || this.revoiceSoon || this.lazyDaily <= 0) return
    // a moment later: a screen's requests are counted first, so the most asked for go first
    this.revoiceSoon = setTimeout(() => {
      this.revoiceSoon = undefined
      void this.revoiceDrain()
    }, this.o.queueDelayMs ?? 1500)
    this.revoiceSoon.unref?.()
  }

  /**
   * Re-voices the old-style clips the app asked for, one at a time, most asked for first, up to the daily cap (less what
   * is kept for re-records on request) and never into the reserve; then the app is told (voice_updated) and fetches the
   * new files.
   */
  private async revoiceDrain() {
    if (this.revoicing) return
    this.revoicing = true
    let made = 0
    try {
      while (this.revoicedToday() < this.lazyDaily) {
        // a clip being re-recorded on request right now is not taken up again
        const c = this.revoiceQueue().find(q => !this.running.has(q.key))
        if (!c) break
        const r = await this.revoiceOne(c)
        if (r === 'stop') break
        if (r === 'made') made++
        if (this.o.queueDelayMs !== 0) await Bun.sleep(this.o.queueDelayMs ?? 1500)
      }
    } catch (e) {
      this.o.log?.(`voice: re-voicing failed: ${(e as Error).message}`)
    } finally {
      this.revoicing = false
      if (made) {
        this.o.log?.(`voice: ${made} short word(s) re-voiced in their carrier sentence (${this.revoicedToday()}/${this.lazyDaily} today)`)
        this.o.onUpdated?.(made)
      }
    }
  }

  /**
   * voice-build --revoice-words: the old-style clips again in their carrier sentence, most asked for first, at most
   * [max] clips and [maxChars] characters, within the reserve. Not held to the daily cap (it's asked for), but counted
   * in it, so the bridge adds no day's worth on top.
   */
  async revoiceWords(o: { max?: number; maxChars?: number; out?: (line: string) => void } = {}) {
    let made = 0
    let plain = 0
    let chars = 0
    const list = this.revoiceQueue(false)
    for (const c of list) {
      if (o.max !== undefined && made + plain >= o.max) break
      const cost = this.cost(c.text, c.voice)
      if (o.maxChars !== undefined && chars + cost > o.maxChars) continue
      const r = await this.revoiceOne(c)
      if (r === 'stop') break
      chars += cost
      if (r === 'made') made++
      else plain++
      o.out?.(`  ${r === 'made' ? '✓ carrier' : '· plain  '} ${c.voice.padEnd(7)} ${c.text}`)
    }
    return { made, plain, chars, left: list.length - made - plain }
  }

  /**
   * The short narrator clips: said in their carrier sentence, kept plain, old-style (and of those, asked for by the
   * app), the characters re-voicing the old-style ones would send, today's re-voices of the daily cap (and of those,
   * re-records on request; the part of the cap kept for them), and whether the node can cut (ffmpeg).
   */
  carrierStats() {
    const el = this.eleven
    const old = this.revoiceQueue(false)
    const by: Record<string, number> = {}
    try {
      for (const r of this.db.query('SELECT method, COUNT(*) AS n FROM clips WHERE method IS NOT NULL GROUP BY method').all() as { method: string; n: number }[]) by[r.method] = r.n
    } catch {} // a store from before, only read: none yet
    return {
      carrier: by.carrier ?? 0,
      plain: by.plain ?? 0,
      old: old.length,
      old_chars: old.reduce((n, c) => n + (el?.carrierFor(c.text, c.voice)?.length ?? 0), 0),
      asked: old.filter(c => (c.hits ?? 0) > 0).length,
      today: this.revoicedToday(),
      daily: this.revoiceDaily,
      redos: this.redosToday(),
      kept: redoKept(this.revoiceDaily),
      cut: !!el?.cut,
    }
  }

  // --- a voice that hisses: the clips from before its filter ----------------------------------------------------

  /** The clips waiting for their voice's filter: ElevenLabs clips of a voice the cast marks `denoise`, not filtered yet, oldest first. */
  denoiseQueue(): (Clip & { preset: DenoisePreset })[] {
    const voices = denoiseVoices()
    const names = Object.keys(voices)
    if (!names.length) return []
    const rows = this.db
      .query(`SELECT * FROM clips WHERE engine = 'elevenlabs' AND voice IN (${names.map(() => '?').join(', ')}) ORDER BY created_at`)
      .all(...names) as Clip[]
    // a store from before the filter has no denoise column: every clip of the voice waits
    return rows.filter(c => !c.denoise).map(c => ({ ...c, preset: voices[c.voice] }))
  }

  /**
   * voice-build --denoise --run: each clip waiting for its voice's filter ([denoiseQueue], at most [max]), filtered and
   * brought to the level in one encode ([processClips]). No ElevenLabs, no characters.
   */
  async denoiseClips(o: { max?: number; out?: (line: string) => void } = {}): Promise<Denoised> {
    const list = this.denoiseQueue()
    if (!this.denoise) return { done: 0, kept: 0, limited: 0, skipped: 0, failed: 0, left: list.length }
    return this.processClips(list, 'filtered (denoise)', o)
  }

  /**
   * Each clip of [list] (at most [max]; a few at a time) through what it waits for, in one encode: its voice's denoise
   * filter (an ElevenLabs clip of a voice the cast marks, not filtered yet) and the level (not at the level's target:
   * never levelled, or levelled to another target, which is re-levelled from the file as it is now). Written under a
   * new file name (the app caches by name), recorded, and only then the old file removed; a clip the level keeps as it
   * is (within the tolerance) keeps its file and is only recorded. A clip the bridge made again meanwhile (a re-record)
   * keeps that take. The app is told ([onUpdated]; when this runs in voice-build, the bridge's [watch] tells it).
   */
  private async processClips(list: Clip[], what: string, o: { max?: number; out?: (line: string) => void }): Promise<Processed> {
    const r: Processed = { done: 0, kept: 0, limited: 0, skipped: 0, failed: 0, left: list.length }
    const lv = this.leveller
    const f = this.denoise
    if (this.o.readonly) return r
    const todo = list.slice(0, o.max ?? list.length)
    const one = async (c: Clip) => {
      const cut = (t: string) => (t.length > 56 ? `${t.slice(0, 55)}…` : t)
      const line = (how: string) => o.out?.(`  ${how.padEnd(26)} ${c.voice.padEnd(10)} ${cut(c.text)}`)
      const src = join(this.filesDir, c.file)
      if (!FILE.test(c.file) || !existsSync(src) || this.running.has(c.key)) {
        r.skipped++
        return line(this.running.has(c.key) ? '· busy' : '· no file')
      }
      const preset = c.engine === 'elevenlabs' && !c.denoise ? denoiseVoices()[c.voice] : undefined
      const toLevel = !!lv && c.level !== lv.target
      const bytes: Uint8Array = new Uint8Array(readFileSync(src))
      let out = bytes
      let levelled: Levelled | null = null
      if (toLevel && (!preset || this.o.denoise === undefined)) {
        // ffmpeg's own filter and the level: one encode
        levelled = await lv!.level(bytes, preset)
        if (!levelled) {
          r.failed++
          return line('✗ failed')
        }
        out = levelled.bytes
      } else {
        // another filter (a stand-in): the filter, then the level
        const d = preset ? await f?.(bytes, preset).catch(() => null) : bytes
        if (!d) {
          r.failed++
          return line('✗ failed')
        }
        out = d
        levelled = toLevel ? await lv!.level(d) : null
        if (levelled) out = levelled.bytes
      }
      const denoise = preset ?? c.denoise ?? null
      const level = levelled ? lv!.target : (c.level ?? null)
      const gain = levelled ? Math.round(((c.gain ?? 0) + levelled.gain) * 100) / 100 : (c.gain ?? null)
      const how = `${preset ? `${preset} ` : ''}${levelled ? (out === bytes ? `= kept (${levelled.before.lufs.toFixed(1)} LUFS)` : `${levelled.before.lufs.toFixed(1)} ${levelled.gain >= 0 ? '+' : ''}${levelled.gain.toFixed(1)} dB${levelled.limited ? ' limited' : ''}`) : ''}`
      // kept as it is: only recorded, while it is still the clip that was measured
      if (out === bytes) {
        if (!this.db.query('UPDATE clips SET level = ?, gain = ? WHERE key = ? AND file = ?').run(level, gain, c.key, c.file).changes) {
          r.skipped++
          return line('· changed')
        }
        r.done++
        r.kept++
        return line(`✓ ${how}`)
      }
      const file = processedFile(c.file, { denoise: preset, level: levelled ? level : null })
      const p = join(this.filesDir, file)
      writeFileSync(`${p}.tmp`, out)
      renameSync(`${p}.tmp`, p)
      // only while it is still the clip that was processed
      if (!this.db.query('UPDATE clips SET file = ?, denoise = ?, level = ?, gain = ? WHERE key = ? AND file = ?').run(file, denoise, level, gain, c.key, c.file).changes) {
        try {
          unlinkSync(p)
        } catch {}
        r.skipped++
        return line('· changed')
      }
      this.seen?.files.set(c.key, file)
      try {
        unlinkSync(src)
      } catch {}
      r.done++
      if (levelled?.limited) r.limited++
      line(`✓ ${how}`)
    }
    let next = 0
    await Promise.all(Array.from({ length: Math.min(4, todo.length) }, async () => {
      while (next < todo.length) await one(todo[next++])
    }))
    r.left = list.length - r.done
    // the clips with a new file: the app downloads those
    const changed = r.done - r.kept
    if (r.done) this.o.log?.(`voice: ${r.done} clip(s) ${what}${r.kept ? `, ${r.kept} of them kept as they were` : ''}`)
    if (changed) this.o.onUpdated?.(changed)
    return r
  }

  // --- every clip at one loudness: the clips from before the level ------------------------------------------------

  /** The clips waiting for the level: never levelled, or levelled to another target; oldest first. */
  levelQueue(): Clip[] {
    const target = this.levelTarget
    // a store from before the level has no level column: every clip waits
    return (this.db.query('SELECT * FROM clips ORDER BY created_at').all() as Clip[]).filter(c => c.level !== target)
  }

  /**
   * voice-build --level --run: each clip waiting for the level ([levelQueue], at most [max]) brought to it, and filtered
   * in the same encode where its voice's filter is due ([processClips]). No ElevenLabs, no characters.
   */
  async levelClips(o: { max?: number; out?: (line: string) => void } = {}): Promise<Processed> {
    const list = this.levelQueue()
    if (!this.leveller) return { done: 0, kept: 0, limited: 0, skipped: 0, failed: 0, left: list.length }
    return this.processClips(list, 'levelled', o)
  }

  /** The level: its target and peak, the clips at it and waiting, and whether the node can level (ffmpeg). */
  levelStats() {
    let levelled = 0
    try {
      levelled = (this.db.query('SELECT COUNT(*) AS n FROM clips WHERE level = ?').get(this.levelTarget) as { n: number }).n
    } catch {} // a store from before the level, only read: none yet
    return { target: this.levelTarget, peak: LEVEL.peak, levelled, waiting: this.levelQueue().length, ffmpeg: !!this.leveller }
  }

  /** The voices the cast filters (`denoise`): each one's preset and its clips filtered and waiting; whether the node can filter (ffmpeg). */
  denoiseStats() {
    const by: Record<string, { preset: DenoisePreset; filtered: number; waiting: number }> = {}
    for (const [voice, preset] of Object.entries(denoiseVoices())) by[voice] = { preset, filtered: 0, waiting: 0 }
    try {
      for (const r of this.db.query('SELECT voice, COUNT(*) AS n FROM clips WHERE denoise IS NOT NULL GROUP BY voice').all() as { voice: string; n: number }[]) {
        if (by[r.voice]) by[r.voice].filtered = r.n
      }
    } catch {} // a store from before the filter, only read: none yet
    for (const c of this.denoiseQueue()) by[c.voice].waiting++
    return { voices: by, ffmpeg: !!this.denoise }
  }

  // --- clips changed by another process ------------------------------------------------------------------------

  /**
   * Whether another process changed the clips since the last look (voice-build: --run, --revoice-words, --denoise); then
   * the app is told ([onUpdated]: voice_updated), reloads its index and downloads the new files as it plays them. SQLite's
   * data_version moves only with other connections' writes, so the bridge's own clips don't count (it tells the app
   * about those itself). The number of clips that changed.
   */
  checkExternal(): number {
    if (this.o.readonly) return 0
    const version = (this.db.query('PRAGMA data_version').get() as { data_version: number }).data_version
    if (this.seen?.version === version) return 0
    const files = new Map((this.db.query('SELECT key, file FROM clips').all() as { key: string; file: string }[]).map(c => [c.key, c.file]))
    const before = this.seen?.files
    this.seen = { version, files }
    if (!before) return 0
    // clips new or made again, and clips gone (the app's index would point at nothing)
    let n = 0
    for (const [key, file] of files) if (before.get(key) !== file) n++
    for (const key of before.keys()) if (!files.has(key)) n++
    if (n) {
      this.o.log?.(`voice: ${n} clip(s) changed by another process (voice-build); the app is told`)
      this.o.onUpdated?.(n)
    }
    return n
  }

  /** From now on, looks every [ms] (a minute) for clips another process changed ([checkExternal]). */
  watch(ms = 60_000) {
    if (this.o.readonly || this.watching) return
    this.checkExternal() // what counts as changed starts here
    this.watching = setInterval(() => {
      try {
        this.checkExternal()
      } catch (e) {
        this.o.log?.(`voice: looking for changed clips failed: ${(e as Error).message}`)
      }
    }, ms)
    this.watching.unref?.()
  }

  missing(items: CorpusItem[]): CorpusItem[] {
    return items.filter(i => !this.get(normalizeText(i.text), i.voice))
  }

  /** How much of [items] is voiced, and by which engine. */
  coverage(items: CorpusItem[]) {
    const by: Record<string, { texts: number; chars: number; cached: number; missing: number; missing_chars: number }> = {}
    const engines: Record<string, number> = {}
    for (const i of items) {
      const kind = i.source.split(':')[0]
      const b = (by[kind] ??= { texts: 0, chars: 0, cached: 0, missing: 0, missing_chars: 0 })
      b.texts++
      b.chars += i.text.length
      const c = this.get(normalizeText(i.text), i.voice)
      if (c) {
        b.cached++
        engines[c.engine] = (engines[c.engine] ?? 0) + 1
      } else {
        b.missing++
        b.missing_chars += i.text.length
      }
    }
    const sum = (k: keyof (typeof by)[string]) => Object.values(by).reduce((n, b) => n + b[k], 0)
    return { texts: items.length, chars: sum('chars'), cached: sum('cached'), missing: sum('missing'), missing_chars: sum('missing_chars'), by_engine: engines, by_source: by }
  }

  /** Voices [items] in the background, one at a time (new content after a publish). */
  enqueue(items: CorpusItem[]) {
    const queued = new Set(this.queue.map(i => clipKey(normalizeText(i.text), i.voice)))
    for (const i of this.missing(items)) {
      const k = clipKey(normalizeText(i.text), i.voice)
      if (!queued.has(k)) {
        queued.add(k)
        this.queue.push(i)
      }
    }
    this.queue.sort((a, b) => a.priority - b.priority)
    void this.drain()
  }

  get queued() { return this.queue.length }

  private async drain() {
    if (this.draining) return
    this.draining = true
    let made = 0
    try {
      while (this.queue.length) {
        const i = this.queue.shift()!
        const before = this.get(normalizeText(i.text), i.voice)
        const c = await this.voice(i.text, i.voice, i.source)
        if (!c) {
          this.o.log?.(`voice: no engine available; ${this.queue.length + 1} text(s) left unvoiced`)
          this.queue = []
          break
        }
        if (!before) made++
        if (this.o.queueDelayMs !== 0) await Bun.sleep(this.o.queueDelayMs ?? 1500)
      }
    } finally {
      this.draining = false
      if (made) {
        this.o.log?.(`voice: ${made} new clip(s)`)
        this.o.onUpdated?.(made)
      }
    }
  }

  async status(items?: CorpusItem[]) {
    const clips = this.db.query('SELECT voice, engine, COUNT(*) AS n, SUM(chars) AS chars FROM clips GROUP BY voice, engine').all() as {
      voice: string
      engine: string
      n: number
      chars: number
    }[]
    const engines: Record<string, unknown> = {}
    for (const e of this.o.engines) engines[e.name] = await e.status().catch(err => ({ up: false, error: (err as Error).message }))
    return {
      clips: {
        total: clips.reduce((n, c) => n + c.n, 0),
        chars: clips.reduce((n, c) => n + (c.chars ?? 0), 0),
        by: clips.map(c => ({ voice: c.voice, engine: c.engine, clips: c.n, chars: c.chars })),
      },
      engines,
      cast: castInfo(),
      queued: this.queue.length,
      ...(this.eleven ? { carrier: this.carrierStats() } : {}),
      ...(Object.keys(denoiseVoices()).length ? { denoise: this.denoiseStats() } : {}),
      level: this.levelStats(),
      ...(items ? { corpus: this.coverage(items) } : {}),
    }
  }

  close() {
    clearInterval(this.watching)
    this.db.close()
  }
}

export const voiceInstructions = `
Audio is automatic: every Slovene word, example, module line, scenario opener, scene dialog line,
villager line and role-play line is voiced by the bridge (ElevenLabs, or the local Gepard model, cached on the node) and played by the
app, which falls back to phone TTS. Don't add audio files or TTS notes yourself; voice_status shows
coverage, quota, which engines are up and the voice cast. Generic texts use the narrators (female,
male); each villager speaks with their speaker from the cast (grandma, grandpa, young-man, gruff,
woman, teacher, young-woman, girl, boy), set in the villager's "speaker" field ("voice" stays their
gender). A scenario may set "speaker" the same way.
One voice per person: everyone who speaks has a voice profile (voice_profiles). The cast who live in the
village get a voice of their own, designed from a Slovene sample of their lines, while the ElevenLabs
account has voice slots (the rest speak with their archetype at their own pitch and pace); their lines
are voiced in it as they are first heard. When Jan says a voice doesn't suit someone, voice_redesign
gives them a new one (optionally described: age, timbre, manner).
When Jan says a word or line sounds wrong (English sounds, a bad take), they can have it recorded again themselves: a long
press on its 🔊 in the app (once a clip a day, within the day's re-voice cap; voice_status shows today's count).
A voice that hisses (Stari Janez's own) has its clips filtered: "denoise" in companion/voice-cast.json (voice_status
shows the voices filtered and their clips still waiting).
Every clip is brought to one loudness (−18 LUFS) when it is stored, so no voice is louder than the others; voice_status
shows the clips still waiting (level), which companion/bin/voice-build --level levels.
`.trim()
