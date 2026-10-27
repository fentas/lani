// Voice store: natural Slovene audio for the app, made once on the node and cached.
// Engines: ElevenLabs (LANI_ELEVENLABS_MODEL, eleven_v4_turbo by default: v3 and v4 speak Slovenian; quota-guarded) and the local
// Gepard worker on 127.0.0.1:8795; for batch clips only, the local Drama 3 worker (companion/voice-drama3, 127.0.0.1:8785:
// voice-build --drama3 remakes a speaker's Gepard clips with it; far too slow for live lines). A clip is replaced only by an
// engine ranked as high or higher: ElevenLabs, then Drama 3, then Gepard (ENGINE_RANK). Clips live in the voice cache, {voice.db,files/<sha1>.mp3}: $LANI_VOICE_CACHE
// (~/.cache/lani/voice after lani-setup), else <data>/app/voice (paths.ts voiceDir).
// A clip is keyed by its speaker from the voice cast (cast.ts): the narrators female/male or a
// character voice ("grandma"). An English narrator says a lone word inside a carrier sentence, cut out by its
// timestamps (CARRIERS); their clips from before that are made again as the app asks for them. In a Slovene village the
// narrators speak with their native Slovene ElevenLabs voices (Nina, Luka: the voices "female.sl", "male.sl"; a new
// lone word inside its carrier sentence too since 2026-10-26, their plain ones from before kept), the local
// voice (ana and nina, marko) as their fallback, never the English library voices (sloveneNarrators). A voice the cast
// marks `denoise` (a designed voice that hisses) has its clips filtered before they are stored (DENOISE), a voice it gives
// a `tempo` (one that speaks slowly) has them sped up (clipPace), every clip's silence at its edges is trimmed (EDGES), and
// every clip is brought to one loudness (LEVEL).
import { Database } from 'bun:sqlite'
import { createHash, randomUUID } from 'node:crypto'
import { copyFileSync, existsSync, mkdirSync, readFileSync, renameSync, unlinkSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { renderDeep, type Addressee } from './addressee'
import { archetypeOf, carrierVoice, cast, castInfo, playerVoices, denoiseOf, denoiseVoices, elevenlabsOf, fallbacks, genderOf, GENDERS, isNarrator, isVoice, LANGUAGE_VOICE, languageVoice, languageVoiceName, personVoice, speakerFor, tempoOf, tempoVoices, voiceSince, type DenoisePreset, type Gender } from './cast'
import { courseTexts, teacherVoice, type CourseInput } from './course-speech'
import type { ArrivalBody } from './arrivals'
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
export type EngineName = 'elevenlabs' | 'gepard' | 'drama3'
/**
 * The engines in the order their clips are preferred: ElevenLabs, then Drama 3 (batch clips, voice-build --drama3), then
 * Gepard. An existing clip is replaced only by a take of an engine ranked as high or higher: a Gepard take never replaces
 * a Drama 3 clip, and ElevenLabs replaces either (--upgrade, --elevenlabs, a re-record).
 */
export const ENGINE_RANK: Record<EngineName, number> = { elevenlabs: 3, drama3: 2, gepard: 1 }
/** Whether a take of [engine] may replace [have], a clip there (ENGINE_RANK; an engine from another version ranks lowest). */
export const outranks = (engine: string, have: { engine: string }) => (ENGINE_RANK[engine as EngineName] ?? 0) >= (ENGINE_RANK[have.engine as EngineName] ?? 0)
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
  /**
   * Its pace (clipPace): the factor it was sped up by, in all, from the clip as it was made: its voice's `tempo` in the
   * cast (by its engine) times, for a local (Gepard) clip, the local voice's pace (PACE, when one is set; 1: only its
   * pauses shortened); null or absent: at its own pace, as the engine made it.
   */
  tempo?: number | null
  /** 1 once its silence at the edges was trimmed to EDGES (or found short enough); null or absent: not yet. */
  edges?: number | null
  /**
   * The engine's own voice that said it: the ElevenLabs voice id, the Gepard voice ("nina"); null or absent: from before it
   * was recorded (an ElevenLabs clip's file name has its voice id; a female narrator's Gepard clip was ana's).
   */
  engine_voice?: string | null
  /**
   * The ElevenLabs model that made it ("eleven_v4_turbo"); null or absent: from before it was recorded (eleven_v3: every
   * ElevenLabs clip until October 2026), or another engine's.
   */
  model?: string | null
}

/** The model of an ElevenLabs clip ([Clip.model]; one from before it was recorded: eleven_v3); undefined for another engine's. */
export const modelOf = (c: Pick<Clip, 'engine' | 'model'>): string | undefined => (c.engine === 'elevenlabs' ? (c.model ?? OLD_MODEL) : undefined)

/**
 * A text to voice ahead of time; lower [priority] goes first. [lang]: an explanation in the learner's own language (a
 * school intro's step: the base's code), kept under its own key ([explanationNorm]) and made by ElevenLabs only, plainly.
 */
export type CorpusItem = { text: string; voice: VoiceName; source: string; priority: number; lang?: string }

export const FILE = /^[a-f0-9]{40}\.mp3$/
/** An ElevenLabs voice id, as the cast writes them. */
export const ELEVENLABS_ID = /^[A-Za-z0-9]{10,40}$/
/** Today on the node's clock, "2026-09-26": the daily cap's day. */
const localDay = (d = new Date()) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
export const clipKey = (norm: string, voice: VoiceName) => `${voice}:${norm}`
export const fileUrl = (file: string) => `/voice/file/${file}`

// --- an explanation in the learner's own language -----------------------------------------------
//
// A step of a school unit's guided introduction is read out as one clip (companion/COURSE.md, "The introduction"): the
// teacher's lines in the learner's base language (en, de, it), the Slovene forms left inside as they are ("Biti (to be)
// has a short form for each person … jaz sem (I am) …"), said by ElevenLabs in the teacher's voice in one go (its model
// copes with the mixed text). Its clips have a key space of their own, the language before the text: normalizeText takes
// any "|" out of a text, so an explanation never meets a line of the village's language with the same words.

/** The longest explanation the node voices (a line of the village's language: [LINE_CHARS]). */
export const EXPLANATION_CHARS = 1_500
/** The longest line the node voices. */
export const LINE_CHARS = 300

/** An explanation's norm: its language, then its text normalized ("en|biti to be has a short form …"). */
export const explanationNorm = (lang: string, text: string) => `${lang}|${normalizeText(text)}`

/** The language of an explanation's clip ([explanationNorm]); undefined for a line of the village's language. */
export const explanationLang = (norm: string): string | undefined => /^([a-z]{2})\|/.exec(norm)?.[1]

/** MP3 from the first bytes: an ID3 tag or an MPEG frame sync. */
export function isMp3(b: Uint8Array): boolean {
  if (b.length < 4) return false
  if (b[0] === 0x49 && b[1] === 0x44 && b[2] === 0x33) return true
  return b[0] === 0xff && (b[1] & 0xe0) === 0xe0
}

type Log = (...a: unknown[]) => void

// --- a lone word in a carrier sentence -------------------------------------------------------
//
// An English library voice (the narrators) says a lone Slovene word the English way: eleven_v3 (and v4) take language_code
// only as a hint, and previous_text/next_text are not supported for eleven_v3. Inside a Slovene sentence the word comes out
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

/** One word alone, without sentence punctuation ("kosilo"): a short text of one word. */
export const isOneWord = (text: string): boolean => isShortText(text) && normalizeText(text).split(' ').filter(Boolean).length === 1

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

// --- the silence at a clip's edges, and the local voice's pace -----------------------------------------------------
//
// The listening exercise's words came late: a narrator's lone word, cut out of its carrier sentence, often had 0.2 to
// 0.7 s of silence before it (Jan's store, October 2026: 933 of the 1,163 carrier words over 0.15 s, the median 0.26 s),
// the local Gepard worker's clips about 1.2 s around them, ElevenLabs' other clips 0.04 s before and up to 0.3 s after
// (p90). So every clip's silence at its edges is trimmed before it is stored (EDGES): 0.05 s kept before the sound, 0.1 s
// after it.
// - The sound starts where a 20 ms window first passes −45 dBFS RMS, measured as the clip will be levelled (27 dB under
//   the level's −18 LUFS; the voices' silence is at −61 to −84), and reaches back as long as the level stays over −60: a
//   soft h, f or v (hrbet, februar, včeraj) rises from −60 for up to 0.08 s before it passes −45, and a trim at −45 alone
//   would have cut into it on 14 of the 1,163 carrier words (none with the floor). A lower threshold alone takes the end
//   of the carrier sentence's "je:" at the cut's start for sound and trims nothing there. The same at the end.
// - The cut has a short fade (5 ms in, 10 ms out). It runs in the level's encode, after a voice's filter.
//
// The local voice keeps its own pace. Paced to ElevenLabs' characters a second (×1.05, the pauses at most 0.4 s: October
// 2026), its clips sounded too fast to Jan. The pace stays settable, off by default (PACE: LANI_VOICE_GEPARD_TEMPO,
// LANI_VOICE_GEPARD_PAUSES).
//
// One word alone from ElevenLabs said plainly, without a carrier sentence (Nina's and Luka's until 2026-10-26, a word
// that couldn't be cut out of its take, a node without ffmpeg) sometimes has something
// after it: in the check of October 2026 (Nina and Luka, 8 words, 24 plain takes), 5 takes had a breath or the start of
// another sound 0.38 to 1 s after the word, louder than the threshold, so the trim kept it (clips of 1.2–1.75 s). So a
// single word's sound ends at its first pause of [wordGap] (0.25 s) or more: no word has one inside (a stop's closure is
// 0.05–0.15 s). On those takes it cut the five to the word (0.46–0.64 s of sound) and left the other 19 as the trim had them.

/**
 * The edges of every clip: the silence kept before the sound and after it (s); what is sound (a 20 ms window over
 * [threshold] dBFS RMS, as the clip is levelled), reaching back and on while the level stays over [floor]; the silence
 * before or after the sound over which a clip is trimmed even when it isn't encoded for anything else ([slack]: twice what
 * is kept; a clip encoded anyway is trimmed to [lead] and [tail]); and the pause that ends a single word's sound
 * ([wordGap], s: what comes after it is cut off).
 */
export const EDGES = { lead: 0.05, tail: 0.1, threshold: -45, floor: -60, slack: { lead: 0.1, tail: 0.2 }, wordGap: 0.25 }
export type EdgeSpec = typeof EDGES

/** A clip's edges: its length, and the silence before its sound and after it (s); no sound: [lead] is the whole clip. */
export type Edges = { secs: number; lead: number; tail: number }

/**
 * The edges of [x] (mono samples at [rate] Hz), measured [shift] dB louder (the gain the clip will get): the first 20 ms
 * window over the threshold, back while the windows before it stay over the floor; the same from the end. [word]: a single
 * word, whose sound ends at its first pause of [spec].wordGap or more (what follows is no part of it).
 */
export function edgesOf(x: Float32Array, rate: number, shift = 0, spec: EdgeSpec = EDGES, word = false): Edges {
  const n = Math.max(1, Math.round(rate * 0.02))
  const db: number[] = []
  for (let i = 0; i + n <= x.length; i += n) {
    let e = 0
    for (let k = i; k < i + n; k++) e += x[k] * x[k]
    db.push(10 * Math.log10(e / n + 1e-12) + shift)
  }
  const r3 = (v: number) => Math.round(v * 1000) / 1000
  const secs = x.length / rate
  const w = n / rate
  let a = db.findIndex(v => v > spec.threshold)
  if (a < 0) return { secs: r3(secs), lead: r3(secs), tail: 0 }
  let b = db.length - 1
  while (db[b] <= spec.threshold) b--
  if (word) {
    // the first pause of wordGap or more after the sound started ends the word
    const gap = Math.max(1, Math.round(spec.wordGap / w))
    let quiet = 0
    for (let i = a; i <= b; i++) {
      quiet = db[i] > spec.threshold ? 0 : quiet + 1
      if (quiet >= gap) {
        b = i - quiet
        break
      }
    }
  }
  while (a > 0 && db[a - 1] > spec.floor) a--
  while (b < db.length - 1 && db[b + 1] > spec.floor) b++
  return { secs: r3(secs), lead: r3(a * w), tail: r3(Math.max(0, secs - (b + 1) * w)) }
}

/** Whether a clip of edges [e] has silence to trim: more than [spec].lead before its sound or .tail after it; [slack]: more than .slack's. */
export const trimmable = (e: Edges, slack = false, spec: EdgeSpec = EDGES) =>
  e.lead < e.secs && (e.lead > (slack ? spec.slack.lead : spec.lead) + 0.001 || e.tail > (slack ? spec.slack.tail : spec.tail) + 0.001)

/**
 * The ffmpeg chain that trims a clip of edges [e] to [spec]'s silence before and after its sound, with a short fade where
 * it cuts: '' when there is nothing to trim (no sound, or no more silence than that).
 */
export function trimChain(e: Edges, spec: EdgeSpec = EDGES): string {
  if (e.lead >= e.secs) return ''
  const from = Math.max(0, e.lead - spec.lead)
  const to = Math.min(e.secs, e.secs - e.tail + spec.tail)
  const head = from > 0.001
  const end = to < e.secs - 0.001
  if (!head && !end) return ''
  return [
    `atrim=${[head ? `start=${from.toFixed(3)}` : '', end ? `end=${to.toFixed(3)}` : ''].filter(Boolean).join(':')}`,
    'asetpts=PTS-STARTPTS',
    head ? 'afade=t=in:d=0.005' : '',
    end ? `afade=t=out:st=${Math.max(0, to - from - 0.01).toFixed(3)}:d=0.01` : '',
  ].filter(Boolean).join(',')
}

/** The rate the edges and the pace are measured at: 32 kHz, so a sibilant's sound up to 16 kHz counts. */
const MEASURE_RATE = 32_000

/** The audio file [file] (through the audio filter [af] first) as mono samples at MEASURE_RATE; null when ffmpeg failed. */
function decodePcm(path: string, file: string, af?: string): Promise<Float32Array | null> {
  return new Promise<Float32Array | null>(done => {
    const p = Bun.spawn([path, '-hide_banner', '-loglevel', 'error', '-i', file, ...(af ? ['-af', af] : []), '-ac', '1', '-ar', String(MEASURE_RATE), '-f', 'f32le', 'pipe:1'], { stdin: 'ignore', stdout: 'pipe', stderr: 'ignore' })
    const timer = setTimeout(() => p.kill(), 30_000)
    Promise.all([new Response(p.stdout).arrayBuffer(), p.exited])
      .then(([b, code]) => done(code === 0 ? new Float32Array(b, 0, b.byteLength >> 2) : null))
      .catch(() => done(null))
      .finally(() => clearTimeout(timer))
  })
}

/** An MP3's edges (through the audio filter [af] first, measured [shift] dB louder; [word]: a single word's); null when ffmpeg failed. */
export type EdgeMeasure = (mp3: Uint8Array, af?: string, shift?: number, word?: boolean) => Promise<Edges | null>

/** The edges with ffmpeg (found as for the cut: [bin], LANI_FFMPEG, the PATH): undefined when there is none. */
export function ffmpegEdges(bin?: string): EdgeMeasure | undefined {
  const path = ffmpegPath(bin)
  if (!path) return undefined
  return (mp3, af, shift = 0, word = false) =>
    isMp3(mp3) ? inTemp(mp3, 'mp3', async file => {
      const x = await decodePcm(path, file, af)
      return x ? edgesOf(x, MEASURE_RATE, shift, EDGES, word) : null
    }).catch(() => null) : Promise.resolve(null)
}

/** The edges of an audio file as it is ([word]: a single word's), with ffmpeg (found as for the cut): undefined when there is none. voice-build --edges' dry run. */
export function ffmpegFileEdges(bin?: string): ((file: string, word?: boolean) => Promise<Edges | null>) | undefined {
  const path = ffmpegPath(bin)
  if (!path) return undefined
  return (file, word = false) => decodePcm(path, file).then(x => (x ? edgesOf(x, MEASURE_RATE, 0, EDGES, word) : null)).catch(() => null)
}

/**
 * The local voice's pace, when one is set (none by default: the worker's own): the factor its clips are sped up by
 * (atempo, the pitch kept; 1: not), and the longest pause kept inside a clip (s; null: as the worker makes them).
 */
export type GepardPace = { tempo: number; pause: number | null }

/** What a pause is: under [silence] dBFS RMS in 20 ms windows (the worker's silence is at −66). */
export const PACE = { silence: -50 }

/**
 * The local voice's pace from the environment: LANI_VOICE_GEPARD_TEMPO, a factor from 0.5 to 2 (default 1, its own
 * speed; "off" or 0 the same), and LANI_VOICE_GEPARD_PAUSES, the longest pause kept, 0.1 to 2 s (default off). Null when
 * neither changes anything: the clips keep the worker's pace (their edges trimmed, as every clip's). A wrong value is
 * logged and left out.
 */
export function gepardPace(env: Record<string, string | undefined> = process.env, log?: Log): GepardPace | null {
  const read = (name: string, min: number, max: number, what: string): number | null => {
    const v = env[name]?.trim()
    if (!v || v === 'off' || v === '0') return null
    const n = Number(v)
    if (Number.isFinite(n) && n >= min && n <= max) return Math.round(n * 100) / 100
    log?.(`voice: ${name}=${v} is not ${what} (or off): the local voice keeps its own`)
    return null
  }
  const tempo = read('LANI_VOICE_GEPARD_TEMPO', 0.5, 2, 'a factor from 0.5 to 2') ?? 1
  const pause = read('LANI_VOICE_GEPARD_PAUSES', 0.1, 2, 'a pause from 0.1 to 2 s')
  return tempo === 1 && pause === null ? null : { tempo, pause }
}

/**
 * The pace a clip of [voice] by [engine] is stored at: its voice's own `tempo` in the cast (cast.ts tempoOf: a voice that
 * speaks slowly, by engine: the girl's Piku ×1.1 on ElevenLabs), times the local voice's pace [local] for a local
 * (Gepard) clip, with its longest pause; null when it keeps its own (as the engine made it). It runs after a carrier
 * sentence's cut (on the word cut out), after a voice's denoise filter and before the edges are measured and the level:
 * in the level's encode (preChain). Its clips record the factor (Clip.tempo), so voice-build --pace paces each clip once.
 */
export function clipPace(voice: VoiceName, engine: EngineName, local: GepardPace | null): GepardPace | null {
  const own = engine === 'gepard' ? local : null
  const tempo = Math.round(tempoOf(voice, engine) * (own?.tempo ?? 1) * 10_000) / 10_000
  const pause = own?.pause ?? null
  return tempo === 1 && pause === null ? null : { tempo, pause }
}

/**
 * The ffmpeg chain of the pace [p]: the pauses longer than .pause down to it (silenceremove keeps a pause of its
 * stop_duration and stop_silence together, so each is half), then sped up by .tempo, the pitch kept; '' for neither.
 */
export function paceChain(p: GepardPace, spec = PACE): string {
  const pauses = p.pause ? `silenceremove=stop_periods=-1:stop_threshold=${spec.silence}dB:stop_duration=${p.pause / 2}:stop_silence=${p.pause / 2}` : ''
  return [pauses, p.tempo !== 1 ? `atempo=${p.tempo}` : ''].filter(Boolean).join(',')
}

/**
 * A clip's pace: its length; the silence before and after its sound; the sound's span (from its start to its end) and
 * the time with sound in it; its pauses of 0.1 s or more within the span (how many, how long in all, the longest). Seconds.
 */
export type Pace = { secs: number; lead: number; tail: number; span: number; sound: number; pauses: number; paused: number; longest: number }

/** An MP3's pace (through the audio filter [af] first: what that would make of it, nothing written); null when ffmpeg failed. */
export type PaceMeasure = (mp3: Uint8Array, af?: string) => Promise<Pace | null>

/** Characters a second: [text] said in [secs]. */
export const charsPerSecond = (text: string, secs: number) => (secs > 0 ? text.trim().length / secs : 0)

/**
 * The pace measure with ffmpeg (found as for the cut: [bin], LANI_FFMPEG, the PATH): undefined when there is none. A 20
 * ms window over −45 dBFS RMS has sound (speech's quiet consonants are over it, the voices' silence under it: Gepard's at
 * −66, ElevenLabs' at −61). voice-build --tempo's measure.
 */
export function ffmpegPace(bin?: string): PaceMeasure | undefined {
  const path = ffmpegPath(bin)
  if (!path) return undefined
  return (mp3, af) =>
    isMp3(mp3)
      ? inTemp(mp3, 'mp3', async file => {
          const x = await decodePcm(path, file, af)
          if (!x) return null
          const n = MEASURE_RATE / 50 // 20 ms
          const sound: boolean[] = []
          for (let i = 0; i + n <= x.length; i += n) {
            let e = 0
            for (let k = i; k < i + n; k++) e += x[k] * x[k]
            sound.push(10 * Math.log10(e / n + 1e-12) > -45)
          }
          const w = n / MEASURE_RATE
          const a = sound.indexOf(true)
          const b = sound.lastIndexOf(true)
          const secs = x.length / MEASURE_RATE
          if (a < 0) return { secs, lead: secs, tail: 0, span: 0, sound: 0, pauses: 0, paused: 0, longest: 0 }
          let pauses = 0
          let paused = 0
          let longest = 0
          let run = 0
          for (let i = a; i <= b; i++) {
            if (!sound[i]) run++
            else {
              if (run * w >= 0.1) {
                pauses++
                paused += run * w
              }
              longest = Math.max(longest, run * w)
              run = 0
            }
          }
          const r3 = (v: number) => Math.round(v * 1000) / 1000
          return { secs: r3(secs), lead: r3(a * w), tail: r3(secs - (b + 1) * w), span: r3((b - a + 1) * w), sound: r3(sound.filter(Boolean).length * w), pauses, paused: r3(paused), longest: r3(longest) }
        }).catch(() => null)
      : Promise.resolve(null)
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

/**
 * What a clip goes through before it is levelled, in the level's encode: a voice's denoise filter, the local voice's pace,
 * and its edges trimmed ([word]: a single word's, which ends at its first long pause: EDGES.wordGap). [levelled]: the clip
 * is at the level already (voice-build --edges on a levelled clip): it keeps its loudness (one the limiter held short of
 * the target stays so), is encoded only to trim it, and then only made up for what the encode takes off.
 */
export type LevelOptions = { denoise?: DenoisePreset; pace?: GepardPace; edges?: boolean; word?: boolean; levelled?: boolean }

/** What runs before the gain, in the same encode: a voice's denoise preset, then the local voice's pace (PACE). */
const preChain = (o: LevelOptions = {}) => [o.denoise ? DENOISE[o.denoise] : '', o.pace ? paceChain(o.pace) : ''].filter(Boolean).join(',') || undefined

/** An MP3's loudness (through the denoise preset and the pace first, as it would be levelled); null when ffmpeg failed. */
export type Measure = (mp3: Uint8Array, o?: LevelOptions) => Promise<Loudness | null>

/** The measure with ffmpeg (found as for the cut: [bin], LANI_FFMPEG, the PATH): undefined when there is none. */
export function ffmpegLoudness(bin?: string): Measure | undefined {
  const path = ffmpegPath(bin)
  if (!path) return undefined
  return (mp3, o) => (isMp3(mp3) ? inTemp(mp3, 'mp3', file => loudnessOf(path, file, preChain(o))).catch(() => null) : Promise.resolve(null))
}

/**
 * A clip brought to the level: the MP3 (the same bytes when it was kept as it was), the gain applied (dB; 0 when
 * kept), whether the limiter held its peaks, and its loudness before (after the denoise filter and the pace, when they
 * ran); with [edges] asked for, its edges as they were (measured as it is levelled) and whether they were trimmed.
 */
export type Levelled = { bytes: Uint8Array; gain: number; limited: boolean; before: Loudness; edges?: Edges; trimmed?: boolean }

/**
 * Brings MP3s to the loudness [target] (LUFS): [level] an MP3, through a voice's denoise preset and the local voice's pace
 * first and its edges trimmed (EDGES), in the same encode.
 */
export type Leveller = { readonly target: number; level(mp3: Uint8Array, o?: LevelOptions): Promise<Levelled | null> }

/**
 * The level with ffmpeg (found as for the cut), to [spec]: undefined when there is no ffmpeg. The edges ([o].edges) are
 * measured through the filter and the pace, as loud as the gain will make them; a clip encoded anyway is trimmed to EDGES
 * where it has more silence, one that would be kept as it is only where it has more than EDGES.slack.
 */
export function ffmpegLeveller(bin?: string, spec: LevelSpec = LEVEL): Leveller | undefined {
  const path = ffmpegPath(bin)
  if (!path) return undefined
  const measure = (b: Uint8Array, af?: string) => inTemp(b, 'mp3', file => loudnessOf(path, file, af))
  const edgesOfClip = ffmpegEdges(bin)!
  const encode = (mp3: Uint8Array, pre: string | undefined, gain: number, limit: boolean) =>
    ffmpegMp3(path, mp3, [pre, `volume=${gain.toFixed(2)}dB`, limit ? limiter(spec.peak) : undefined].filter(Boolean).join(','), true, mp3Kbps(mp3))
  const level = async (mp3: Uint8Array, o: LevelOptions = {}): Promise<Levelled | null> => {
    if (!isMp3(mp3) || !mp3Duration(mp3)) return null
    const pre = preChain(o)
    const before = await measure(mp3, pre)
    if (!before) return null
    // at the level already: its own loudness is the one to keep
    const to = o.levelled && before.lufs > -70 ? { ...spec, target: before.lufs } : spec
    const g = o.levelled ? { gain: 0, limit: false, keep: true } : levelGain(before, spec)
    // the edges as the clip will be: through the filter and the pace, the gain added
    const edges = o.edges ? await edgesOfClip(mp3, pre, before.lufs > -70 ? g.gain : 0, o.word) : undefined
    if (o.edges && !edges) return null
    const trim = edges && trimmable(edges, g.keep && !pre) ? trimChain(edges) : ''
    const chain = [pre, trim].filter(Boolean).join(',') || undefined
    const done = (bytes: Uint8Array, gain: number, limited: boolean): Levelled => ({ bytes, gain, limited, before, ...(edges ? { edges, trimmed: !!trim } : {}) })
    if (g.keep && !chain) return done(mp3, 0, false)
    const out = await encode(mp3, chain, g.gain, g.limit)
    // paced or trimmed to nothing (a clip of silence): not stored that way
    if (out && (o.pace || trim) && mp3Duration(out) < 0.1) return null
    if (!out || !(before.lufs > -70)) return out ? done(out, g.gain, g.limit) : null
    // The encode itself takes about 0.4 dB off (LAME at a constant bitrate), and the limiter some more: measured, and
    // when that's more than a quarter dB off, encoded again from the clip as it was (not from the encode) with the
    // difference (at most 2 dB more), so the clip goes through one encode all the same.
    const after = await measure(out)
    const miss = after ? to.target - after.lufs : 0
    if (Math.abs(miss) <= 0.25) return done(out, g.gain, g.limit)
    const gain = Math.round(Math.min(spec.maxGain, g.gain + Math.max(-2, Math.min(2, miss))) * 100) / 100
    const limit = before.peak + gain > spec.peak - LIMIT_MARGIN
    const again = await encode(mp3, chain, gain, limit)
    return again ? done(again, gain, limit) : done(out, g.gain, g.limit)
  }
  return { target: spec.target, level: (mp3, o) => level(mp3, o).catch(() => null) }
}

/**
 * The new file name of an existing clip once levelled to [level] (filtered with [denoise], paced at [tempo], its
 * [edges] trimmed): voice-build --level, --denoise, --tempo, --edges.
 */
export const processedFile = (file: string, o: { denoise?: DenoisePreset | null; level?: number | null; tempo?: number | null; edges?: boolean }) =>
  `${createHash('sha1').update(`${file}${o.denoise ? `|denoise:${o.denoise}` : ''}${o.level != null ? `|level:${o.level}` : ''}${o.tempo != null ? `|tempo:${o.tempo}` : ''}${o.edges ? '|edges' : ''}`).digest('hex')}.mp3`

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

/**
 * What an engine made: the MP3, how (a carrier cut), the characters it cost, and the engine's own voice that said it (the
 * ElevenLabs voice id, the Gepard voice: "nina").
 */
export type Take = { bytes: Uint8Array; method?: ClipMethod; chars?: number; voice?: string; model?: string }

export interface Engine {
  readonly name: EngineName
  /** Whether a request of [chars] may go to this engine now (configured, up, within quota). */
  ready(chars: number, live: boolean): Promise<boolean>
  /**
   * The characters a request for [text] in [voice] costs (ElevenLabs: a short word's carrier sentence); else its length.
   * [lang]: an explanation in that language, said plainly; [carrier]: a short text in its carrier sentence in any voice.
   */
  cost?(text: string, voice: VoiceName, lang?: string, carrier?: boolean): number
  /**
   * The clip, or null when the engine can't make it now. Never throws. [live]: a live line, which may use the reserve;
   * [lang]: the language of the text when it isn't the village's (an explanation in the learner's own language);
   * [urgent]: someone waits for it (a live line is): it goes ahead of the background work waiting its turn ([Gate]);
   * [carrier]: a short text said inside its carrier sentence whatever the voice (ElevenLabs; a re-record, the learner's
   * own line).
   */
  synth(text: string, voice: VoiceName, o?: { live?: boolean; lang?: string; urgent?: boolean; carrier?: boolean }): Promise<Take | null>
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
 * The ElevenLabs model the clips are made with, by default (LANI_ELEVENLABS_MODEL): Jan, comparing samples in October
 * 2026, heard v4 better than v3 and no difference between v4 and v4 Turbo, which costs half. Every text-to-speech request
 * (a line, a carrier sentence with its timestamps, an explanation) asks for it; Voice Design keeps its own (eleven_ttv_v3).
 */
export const DEFAULT_ELEVENLABS_MODEL = 'eleven_v4_turbo'

/** The model of the clips made before the model was recorded ([Clip.model] null). */
export const OLD_MODEL = 'eleven_v3'

/**
 * The credits a character costs on the subscription, by model (the account's /v1/models, character_cost_multiplier,
 * October 2026); another model: 1. In September 2026 eleven_v3 was seen to count about half (34,100 characters for about
 * 17,000), its price while it was new. The quota guard counts characters (it stops early rather than late).
 */
export const MODEL_CREDITS: Record<string, number> = { eleven_v3: 1, eleven_v4: 1, eleven_v4_turbo: 0.5 }

/** The credits [chars] characters of [model] cost on the subscription, about ([MODEL_CREDITS]). */
export const creditsFor = (chars: number, model: string) => Math.round(chars * (MODEL_CREDITS[model] ?? 1))

/**
 * The text-to-speech requests this bridge has at ElevenLabs at once, by default (LANI_ELEVENLABS_CONCURRENCY): below
 * the plans' own limits (Starter 3, Creator 5 at once), so a burst (a school intro's step asks for its explanation and its
 * Slovene forms together) waits its turn instead of being refused over the limit (HTTP 429), which paused ElevenLabs for
 * a minute and left the explanation unmade; one or two left for voice-build.
 */
export const DEFAULT_ELEVENLABS_CONCURRENCY = 3

/** ElevenLabs' answer when too much is at work at once (the plan's concurrency, or its system busy): asked again soon. */
const BUSY = /too_many_concurrent_requests|concurren|system_busy/i

/** How often a request ElevenLabs refused as busy ([BUSY]) is asked again, and the wait before each (times its try). */
const BUSY_RETRIES = 2
const BUSY_WAIT_MS = 1_000

/**
 * At most [size] at a time ([run]); the rest wait their turn, first come first served, but an [urgent] one (someone in
 * the app waits for it: a live line, a re-record) ahead of all that aren't: the lazy re-voice and the prebuilds don't
 * keep the learner waiting past the app's few seconds (its WAIT_MS), which it would then speak with the phone's voice.
 */
export class Gate {
  private busy = 0
  private readonly waiting: { go: () => void; urgent: boolean }[] = []
  constructor(readonly size: number) {}

  /** At work now. */
  get running(): number { return this.busy }

  /** Waiting their turn. */
  get queued(): number { return this.waiting.length }

  async run<T>(f: () => Promise<T>, urgent = false): Promise<T> {
    if (this.busy < this.size) this.busy++
    // the one done before hands its place over
    else
      await new Promise<void>(go => {
        const at = urgent ? this.waiting.findIndex(w => !w.urgent) : -1
        if (at < 0) this.waiting.push({ go, urgent })
        else this.waiting.splice(at, 0, { go, urgent })
      })
    try {
      return await f()
    } finally {
      const next = this.waiting.shift()
      if (next) next.go()
      else this.busy--
    }
  }
}

/** A request's answer read within its turn: the value, or the error's status and text. */
type Answer<T> = { ok: true; value: T } | { ok: false; status: number; text: string }

/**
 * ElevenLabs ([model]: eleven_v4_turbo by default) with a quota guard. The subscription is read at most every 10 minutes and
 * counted forward locally in between. Prebuilds stop at [reserve] characters left; live dialogue
 * lines may use the reserve. Each speaker's voice id comes from the cast ([voices] overrides it).
 * A carrier voice says a short text inside its language's carrier sentence, cut out with [cut].
 * At most [concurrency] text-to-speech requests at once ([Gate]), the rest in turn; one refused as busy is asked again.
 */
export class ElevenLabs implements Engine {
  readonly name = 'elevenlabs' as const
  private quota?: Quota
  private checking?: Promise<Quota | undefined>
  private pausedUntil = 0
  private lastError?: string
  private cutter?: Cutter | null
  private noCutterLogged = false
  private readonly gate: Gate
  /** Requests ElevenLabs refused as busy and that were asked again, since the start. */
  private busyAgain = 0

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
      /** Text-to-speech requests at once (DEFAULT_ELEVENLABS_CONCURRENCY); the rest wait their turn. */
      concurrency?: number
      /** The model every text-to-speech request asks for (DEFAULT_ELEVENLABS_MODEL). */
      model?: string
      /** The wait before a request refused as busy is asked again (BUSY_WAIT_MS, times its try). */
      busyWaitMs?: number
      log?: Log
    } = {},
  ) {
    this.gate = new Gate(Math.max(1, Math.floor(o.concurrency ?? DEFAULT_ELEVENLABS_CONCURRENCY)))
  }

  private get base() { return (this.o.base ?? 'https://api.elevenlabs.io').replace(/\/$/, '') }
  get reserve() { return this.o.reserve ?? DEFAULT_RESERVE }
  /** The model the clips are made with ([DEFAULT_ELEVENLABS_MODEL]; LANI_ELEVENLABS_MODEL). */
  get model() { return this.o.model || DEFAULT_ELEVENLABS_MODEL }
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
   * `carrier` in the cast ([force]: in any voice, a re-record), in a language with a carrier sentence; else undefined.
   */
  carrierFor(text: string, voice: VoiceName, force = false): string | undefined {
    return this.carrierIn(text, this.o.language ?? 'sl', force || carrierVoice(voice))
  }

  /**
   * The carrier sentence a short [text] (a word, up to three) is said in, in [language] (CARRIERS), when its voice says a
   * lone word in one ([carrier]); else undefined.
   */
  carrierIn(text: string, language: string, carrier: boolean): string | undefined {
    const template = (this.o.carriers ?? CARRIERS)[language]
    if (!template || !carrier || !isShortText(text)) return undefined
    return template.replace('{w}', text.trim())
  }

  /** Whether [lang] is another language than the clips' (the village's): an explanation in the learner's own. */
  private explains(lang?: string): boolean {
    return !!lang && lang !== (this.o.language ?? 'sl')
  }

  /**
   * Whether [text] in [voice] is said in its carrier sentence now: it applies, and the node can cut (ffmpeg). Never an
   * explanation in another language ([lang]): it is said plainly, however short.
   */
  usesCarrier(text: string, voice: VoiceName, lang?: string, force = false): boolean {
    if (this.explains(lang) || !this.carrierFor(text, voice, force)) return false
    if (this.cut) return true
    if (!this.noCutterLogged) {
      this.noCutterLogged = true
      this.o.log?.('elevenlabs: no ffmpeg: short words are asked for plainly, without their carrier sentence')
    }
    return false
  }

  /** The characters a request for [text] in [voice] costs: its carrier sentence when it is said in one, else the text. */
  cost(text: string, voice: VoiceName, lang?: string, force = false): number {
    return this.usesCarrier(text, voice, lang, force) ? this.carrierFor(text, voice, force)!.length : text.trim().length
  }

  /** The carrier sentence [text] is said in by [sayAs] ([o]: the language and whether the voice says a lone word in one), when the node can cut. */
  private carrierAs(text: string, o: { language: string; carrier?: boolean }): string | undefined {
    const said = this.carrierIn(text, o.language, !!o.carrier)
    return said && this.cut ? said : undefined
  }

  /** The characters [sayAs] sends for [text] ([o] as there): its carrier sentence when it is said in one, else the text. */
  costAs(text: string, o: { language: string; carrier?: boolean }): number {
    return this.carrierAs(text, o)?.length ?? text.trim().length
  }

  private voiceId(voice: VoiceName): string | undefined {
    const id = this.o.voices?.[voice] ?? elevenlabsOf(voice)
    if (!id) this.lastError = `tts: no ElevenLabs voice for speaker "${voice}"`
    return id
  }

  /** A failed request: logged without the key; quota, auth, a rate limit or a server error pause asking for a while. */
  private failed(status: number, text: string, what: string) {
    this.lastError = `${what}: HTTP ${status} ${this.redact(text).slice(0, 160)}`
    this.o.log?.(`elevenlabs: ${this.lastError}`)
    // Quota, auth or rate limit: stop asking for a while and re-read the subscription then.
    if ([401, 402, 429].includes(status) || status >= 500) {
      this.pausedUntil = Date.now() + (status === 429 ? 60_000 : 600_000)
      if (this.quota) this.quota.checked_at = 0
    }
  }

  /**
   * A text-to-speech POST of [body] to [url] in its turn ([gate]: at most [concurrency] at once from here), its answer read
   * within it ([read]: the request is at work until its audio is in). ElevenLabs refusing it as too much at once ([BUSY]:
   * another process's requests, voice-build's) asks it again a moment later, up to BUSY_RETRIES times, without pausing
   * the engine; any other error is the caller's. Throws as fetch does (the network, the timeout).
   */
  private async tts<T>(url: string, body: unknown, accept: string | undefined, read: (r: Response) => Promise<T>, urgent = false): Promise<Answer<T>> {
    for (let attempt = 0; ; attempt++) {
      const a: Answer<T> = await this.gate.run(async () => {
        const r = await fetch(url, {
          method: 'POST',
          headers: { ...this.headers(), ...(accept ? { accept } : {}) },
          body: JSON.stringify(body),
          signal: AbortSignal.timeout(60_000),
        })
        return r.ok ? { ok: true as const, value: await read(r) } : { ok: false as const, status: r.status, text: await r.text() }
      }, urgent)
      if (a.ok || a.status !== 429 || !BUSY.test(a.text) || attempt >= BUSY_RETRIES) return a
      const wait = (this.o.busyWaitMs ?? BUSY_WAIT_MS) * (attempt + 1)
      this.busyAgain++
      this.o.log?.(`elevenlabs: busy (HTTP 429 ${this.redact(a.text).slice(0, 80)}): asked again in ${wait} ms`)
      await Bun.sleep(wait)
    }
  }

  /**
   * [text] in [voice]: a short text of a carrier voice inside its carrier sentence, cut out; when the word can't be cut
   * out of it, or the request fails (not for the quota, auth or a rate limit), asked for plainly, and logged. An
   * explanation in another language than the village's ([lang]) is asked for plainly and without a language_code, so
   * the model says the learner's language as it is and the Slovene forms inside it as Slovene.
   */
  async synth(text: string, voice: VoiceName, o: { live?: boolean; lang?: string; urgent?: boolean; carrier?: boolean } = {}): Promise<Take | null> {
    if (!this.o.key) return null
    const urgent = !!(o.live || o.urgent)
    const said = this.usesCarrier(text, voice, o.lang, o.carrier) ? this.carrierFor(text, voice, o.carrier) : undefined
    const id = this.o.voices?.[voice] ?? elevenlabsOf(voice)
    if (!said) {
      const bytes = await this.plain(text, voice, this.explains(o.lang), urgent)
      return bytes ? { bytes, voice: id, model: this.model } : null
    }
    const r = await this.carrierTake(text, voice, urgent, o.carrier)
    if (r instanceof Uint8Array) return { bytes: r, method: 'carrier', chars: said.length, voice: id, model: this.model }
    if (!r && Date.now() < this.pausedUntil) return null
    this.o.log?.(`elevenlabs: "${text}" (${voice}) without its carrier sentence: ${r ? r.miss : this.lastError}; asked for plainly`)
    // the plain take on top of a paid carrier take: the guard is asked again
    if (!(await this.ready(text.trim().length, !!o.live))) return null
    const bytes = await this.plain(text, voice, false, urgent)
    // a take the word couldn't be cut out of: 'plain', not tried again; a failed request or cut: tried again when re-voicing
    return bytes ? { bytes, method: r && !r.retry ? 'plain' : undefined, chars: (r ? said.length : 0) + text.trim().length, voice: id, model: this.model } : null
  }

  /**
   * [text] in a voice that isn't this village's cast's ([o].id: an ElevenLabs voice id) and in [o].language, which may be
   * another than the village's: a visited town's people in its own cast's voices (visit-voices.ts). A short text of a
   * voice that says a lone word in a carrier sentence ([o].carrier) is said inside the language's one and cut out, as
   * [synth] does; when the word can't be cut out of it, it is asked for plainly. [o].live: a live line, which may use the
   * reserve; [o].urgent: someone waits for it ([Gate]). Null when ElevenLabs can't now; never throws.
   */
  async sayAs(text: string, o: { id: string; language: string; carrier?: boolean; live?: boolean; urgent?: boolean }): Promise<Take | null> {
    if (!this.o.key || !ELEVENLABS_ID.test(o.id)) return null
    const urgent = !!(o.live || o.urgent)
    const said = this.carrierAs(text, o)
    if (!said) {
      const bytes = await this.plainWith(text, o.id, o.language, urgent)
      return bytes ? { bytes, voice: o.id, model: this.model } : null
    }
    const r = await this.carrierWith(text, said, o.id, o.language, urgent)
    if (r instanceof Uint8Array) return { bytes: r, method: 'carrier', chars: said.length, voice: o.id, model: this.model }
    if (!r && Date.now() < this.pausedUntil) return null
    this.o.log?.(`elevenlabs: "${text}" (${o.id}, ${o.language}) without its carrier sentence: ${r ? r.miss : this.lastError}; asked for plainly`)
    if (!(await this.ready(text.trim().length, !!o.live))) return null
    const bytes = await this.plainWith(text, o.id, o.language, urgent)
    return bytes ? { bytes, method: r && !r.retry ? 'plain' : undefined, chars: (r ? said.length : 0) + text.trim().length, voice: o.id, model: this.model } : null
  }

  /** [text] asked for as it is; [mixed]: an explanation, its language left to the model (no language_code); [urgent]: [Gate]. */
  private async plain(text: string, voice: VoiceName, mixed = false, urgent = false): Promise<Uint8Array | null> {
    const id = this.voiceId(voice)
    if (!id) return null
    return this.plainWith(text, id, mixed ? undefined : (this.o.language ?? 'sl'), urgent)
  }

  /** [text] asked for as it is in the voice [id], with [language] as its language_code (none: the model's own guess). */
  private async plainWith(text: string, id: string, language: string | undefined, urgent: boolean): Promise<Uint8Array | null> {
    try {
      const r = await this.tts(
        `${this.base}/v1/text-to-speech/${id}?output_format=mp3_44100_128`,
        language === undefined ? { text, model_id: this.model } : { text, model_id: this.model, language_code: language },
        'audio/mpeg',
        async r => new Uint8Array(await r.arrayBuffer()),
        urgent,
      )
      if (!r.ok) {
        this.failed(r.status, r.text, 'tts')
        return null
      }
      const bytes = r.value
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
  async carrierTake(text: string, voice: VoiceName, urgent = false, force = false): Promise<Uint8Array | { miss: string; retry?: boolean } | null> {
    const said = this.carrierFor(text, voice, force)
    if (!this.o.key || !said || !this.cut) return null
    const id = this.voiceId(voice)
    if (!id) return null
    return this.carrierWith(text, said, id, this.o.language ?? 'sl', urgent)
  }

  /** [carrierTake] of [text] inside [said] (its carrier sentence), in the voice [id], with [language] as its language_code. */
  private async carrierWith(text: string, said: string, id: string, language: string, urgent: boolean): Promise<Uint8Array | { miss: string; retry?: boolean } | null> {
    const cut = this.cut
    if (!this.o.key || !cut) return null
    let d: { audio_base64?: string; alignment?: Alignment; normalized_alignment?: Alignment }
    try {
      const r = await this.tts(
        `${this.base}/v1/text-to-speech/${id}/with-timestamps?output_format=mp3_44100_128`,
        { text: said, model_id: this.model, language_code: language },
        undefined,
        async r => (await r.json()) as typeof d,
        urgent,
      )
      if (!r.ok) {
        this.failed(r.status, r.text, 'tts (carrier)')
        return null
      }
      d = r.value
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
      model: this.model,
      quota: q ? { used: q.used, limit: q.limit, remaining: this.remaining(), reserve: this.reserve, resets_at: q.resets_at } : null,
      voices: q && q.voice_limit !== undefined ? { slots: q.voice_limit, used: q.voice_slots_used, edits: q.voice_edits, edits_max: q.voice_edits_max } : null,
      // the requests at once from here, at work and waiting their turn; those refused as busy and asked again
      concurrency: { limit: this.gate.size, running: this.gate.running, waiting: this.gate.queued, busy_again: this.busyAgain },
      ...(this.lastError ? { last_error: this.lastError } : {}),
    }
  }
}

/**
 * The local Gepard worker: GET /health, POST /synth {text, speaker?} → MP3, 503 while busy. A cast
 * speaker's worker voice comes from the cast ([speakers] overrides it); a lone word's from its `gepard_words` when it
 * has one (the female narrator: ana for words, nina for sentences).
 */
export class Gepard implements Engine {
  readonly name = 'gepard' as const
  private health?: { up: boolean; at: number }

  constructor(
    private readonly o: { url?: string; speakers?: Partial<Record<VoiceName, string>>; timeoutMs?: number; log?: Log } = {},
  ) {}

  /**
   * The worker's voice for [voice] (saying [text]): the override, else the cast's (a lone word: its `gepard_words` when
   * it has them), else the gender narrator's.
   */
  speakerOf(voice: VoiceName, text?: string): string | undefined {
    const c = cast()
    const word = text !== undefined && isShortText(text)
    const of = (name: string) => (word && c[name]?.gepard ? (c[name].gepard_words ?? c[name].gepard) : c[name]?.gepard)
    const a = archetypeOf(voice)
    const g = genderOf(voice)
    return this.o.speakers?.[a] ?? of(a) ?? this.o.speakers?.[g] ?? of(g)
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
    return this.request(text, this.speakerOf(voice, text))
  }

  /**
   * [text] (Slovene: the worker speaks nothing else) in the worker's voice [speaker] ("nina", "marko"), not a speaker of
   * this village's cast: a visited Slovene town's people in its own cast's local voices (visit-voices.ts).
   */
  async sayAs(text: string, speaker: string): Promise<Take | null> {
    return /^[a-z0-9-]{1,32}$/.test(speaker) ? this.request(text, speaker) : null
  }

  private async request(text: string, speaker: string | undefined): Promise<Take | null> {
    try {
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
      return isMp3(bytes) ? { bytes, voice: r.headers.get('x-voice') || speaker } : null
    } catch (e) {
      this.health = { up: false, at: Date.now() } // unreachable: treat as down until the next health check
      this.o.log?.(`gepard: ${(e as Error).message}`)
      return null
    }
  }

  async status() { return { up: await this.up(), url: this.url } }
}

/** The Drama 3 worker's address by default (companion/voice-drama3; LANI_DRAMA3_URL). */
export const DRAMA3_URL = 'http://127.0.0.1:8785'

/** Why the Drama 3 worker made no clip of a line: its quality guard's reasons for each take, or the HTTP error. */
export type Drama3Rejection = { status: number; error: string; attempts?: { seed?: number; reasons: string[] }[] }

/**
 * The local Drama 3 worker (Fish Audio Drama 3, companion/voice-drama3): GET /health, POST /synth {text, speaker} → MP3,
 * the Gepard worker's contract. For batch clips only (voice-build --drama3): about 10 s of compute a second of audio,
 * far too slow for a live line, so the bridge's own store never has it. Its voices are the cast's speakers, each a
 * reference made by Drama 3 itself (voices.json). Every take passes its quality guard (the gender by pitch, noise, the
 * text heard by Whisper, length and silence) or is made again with another seed; a line no take passes comes back as
 * 422, and [lastRejection] holds why. Built with Fish Audio.
 */
export class Drama3 implements Engine {
  readonly name = 'drama3' as const
  /** Why the last [synth] made no clip (the quality guard's reasons, an HTTP error); undefined after a clip. */
  lastRejection?: Drama3Rejection

  constructor(private readonly o: { url?: string; timeoutMs?: number; log?: Log } = {}) {}

  get url() { return (this.o.url ?? DRAMA3_URL).replace(/\/$/, '') }

  /** The worker's voice for [voice]: the cast speaker (a person's own voice: their archetype), else the gender narrator's. */
  speakerOf(voice: VoiceName): string {
    const a = archetypeOf(voice)
    return Object.hasOwn(cast(), a) ? a : genderOf(voice)
  }

  /** The worker's /health: its JSON (ok, or loading and what it waits for), or null when nothing answers. */
  async health(): Promise<Record<string, unknown> | null> {
    try {
      const r = await fetch(`${this.url}/health`, { signal: AbortSignal.timeout(3_000) })
      return ((await r.json().catch(() => null)) as Record<string, unknown> | null) ?? { ok: false, status: r.status }
    } catch {
      return null
    }
  }

  async up(): Promise<boolean> { return (await this.health())?.ok === true }

  ready(): Promise<boolean> { return this.up() }

  async synth(text: string, voice: VoiceName): Promise<Take | null> {
    const r = await this.make(text, voice)
    this.lastRejection = r.rejection
    return r.take ?? null
  }

  /** [synth] with the reason when there is no clip, for each call apart (voice-build has two in flight). */
  async make(text: string, voice: VoiceName): Promise<{ take?: Take; rejection?: Drama3Rejection }> {
    try {
      const speaker = this.speakerOf(voice)
      const r = await fetch(`${this.url}/synth`, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ text, speaker }),
        // up to four takes of 20 to 60 s each, and the other request in flight
        signal: AbortSignal.timeout(this.o.timeoutMs ?? 900_000),
      })
      if (!r.ok) {
        const body = (await r.json().catch(() => ({}))) as { error?: string; attempts?: { seed?: number; reasons?: string[] }[] }
        const rejection: Drama3Rejection = { status: r.status, error: body.error ?? `HTTP ${r.status}`, ...(body.attempts ? { attempts: body.attempts.map(a => ({ seed: a.seed, reasons: a.reasons ?? [] })) } : {}) }
        if (r.status !== 503 && r.status !== 422) this.o.log?.(`drama3: HTTP ${r.status} ${rejection.error}`)
        return { rejection }
      }
      const bytes = new Uint8Array(await r.arrayBuffer())
      if (!isMp3(bytes)) return { rejection: { status: r.status, error: 'not an MP3' } }
      return { take: { bytes, voice: r.headers.get('x-voice') || speaker } }
    } catch (e) {
      this.o.log?.(`drama3: ${(e as Error).message}`)
      return { rejection: { status: 0, error: (e as Error).message } }
    }
  }

  async status() {
    const h = await this.health()
    return { up: h?.ok === true, url: this.url, ...(h && h.ok !== true ? { waiting: h.waiting ?? h.error ?? null } : {}) }
  }
}

/**
 * Engines from the environment: ELEVENLABS_API_KEY (+ ELEVENLABS_BASE_URL, LANI_ELEVENLABS_CONCURRENCY: its requests at
 * once, DEFAULT_ELEVENLABS_CONCURRENCY; LANI_ELEVENLABS_MODEL: its model, DEFAULT_ELEVENLABS_MODEL), LANI_GEPARD_URL, LANI_FFMPEG (the carrier cut's ffmpeg, "off" for none; else the
 * one on the PATH); clips in [language].
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
      concurrency: dailySetting(env, 'LANI_ELEVENLABS_CONCURRENCY') || undefined,
      model: env.LANI_ELEVENLABS_MODEL?.trim() || undefined,
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
export const PRIORITY_LABELS = ['villagers', 'pack words', 'examples', 'modules', 'scenarios', 'course', 'stories A1', 'drills A1', 'scenes', 'ispy', 'stories A2', 'drills A2+', 'stories B1+', 'explanations']

/** The school's course in the teacher's voice (course-speech.ts): what the app reads aloud in the language learned. */
const COURSE = PRIORITY_LABELS.indexOf('course')
/**
 * The course's explanations in the learner's own language (one clip a step, a base each): last, and voice-build makes
 * them only when asked to (--explanations); the app asks for them as an intro opens.
 */
export const EXPLANATIONS = PRIORITY_LABELS.indexOf('explanations')

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
 * scenarios, the school's course in the teacher's voice ([courseTexts]: its examples, quick questions and answers, the
 * Slovene of its explanations), the storyteller's stories told at A1 ([storyTexts]), the car's drills at A1 ([drillTexts]: in the
 * narrator's voice, a riddle in its teller's), then the scenes' dialogs (each person's lines in their villager's speaker,
 * else their sprite's voice; the learner's choices in the default voice), the cast's arrivals and the keepers' talks (the charcoal burner's
 * at his pile: his meeting and his story, every telling at every level, as a scene's dialog in his speaker's voice) and
 * the village projects' steps (every telling, each line in its speaker's voice), then
 * the stories told at A2, the drills above A1, the stories at B1 and up; last the course's explanations in the learners'
 * own languages ([EXPLANATIONS], with their [CorpusItem.lang]).
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
  /**
   * The village projects' steps as short scenes (project-steps.ts, stepTellings): every telling of every step, its lines
   * said by the project's leader and helpers (each in their villager's speaker voice), as a scene's dialog.
   */
  steps?: { project: string; tellings: { lines: ResolvedScene['dialogs'][number]['lines'] }[] }[]
  villagers?: (Pick<Villager, 'id' | 'voice' | 'speaker' | 'lines'> & Partial<Pick<Villager, 'home' | 'since' | 'extra' | 'order'>> & { art?: string })[]
  /**
   * The school's course as the app reads it aloud (course-speech.ts): its units, the grammar pages for their summaries,
   * the learners' bases and the app's check for the language learned; read in the teacher's voice (the school's person
   * of [villagers]: her own voice when it is ready, else her speaker).
   */
  course?: CourseInput
  /**
   * How the cast are introduced as they arrive (arrivals.ts: the culture pack's, the tutor's for one of them): every
   * level's late line and lines as a scene's dialog, each line in its speaker's voice (a reply in the voice of whoever
   * spoke last, the learner's choices in the default one).
   */
  arrivals?: { id: string; levels: ArrivalBody['levels'] }[]
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
  // an explanation in [lang]: one clip, under its own key, never cut into phrases (the app asks for it whole)
  const explain = (text: string, lang: string, source: string, voice: VoiceName) => {
    const key = clipKey(explanationNorm(lang, text), voice)
    if (!normalizeText(text) || seen.has(key)) return
    seen.add(key)
    out.push({ text, voice, source, priority: EXPLANATIONS, lang })
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
  // the school's course in the teacher's voice, as the app reads it (course-speech.ts): every step's examples, its quick
  // question and the sentence of its answer, the Slovene of its explanation and of the summary (read without their one
  // clip); the explanations themselves come last ([EXPLANATIONS])
  const teacher = teacherVoice(c.villagers ?? [], v => speakerFor(v as { voice: Gender; speaker?: string }), id => !!c.ownVoice?.(id))
  const course = c.course ? courseTexts(c.course, language) : undefined
  for (const t of course?.lines ?? []) add(t.text, t.source, COURSE, teacher)
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
  // the cast's arrivals, as a scene's dialog: each line (the late one too, said instead of the first) in its speaker's
  // voice, a reply in the voice of whoever spoke last, the learner's choices in the default one; a line of someone not in
  // the cast (a resident in a tutor's arrival) is voiced when played
  for (const a of c.arrivals ?? []) {
    const src = `arrival:${a.id}`
    const say = (t: Partial<Record<string, unknown>> | undefined) => {
      const x = t?.[language]
      return typeof x === 'string' && x && !x.includes('{') ? x : undefined
    }
    for (const v of Object.values(a.levels)) {
      if (!v) continue
      let speaker: VoiceName | undefined = villagerVoice.get(a.id)
      for (const l of [...(v.late ? [v.late] : []), ...v.lines]) {
        if (l.who) {
          speaker = villagerVoice.get(l.who)
          if (speaker) add(say(l), src, SCENES, speaker)
        }
        for (const ch of l.choices ?? []) {
          add(say(ch), src, SCENES)
          if (speaker) add(say(ch.reply), src, SCENES, speaker)
        }
      }
    }
  }
  // the projects' steps, as a scene's dialog: each line in its speaker's voice (the leader's, a helper's), a reply in the
  // voice of whoever spoke last, the learner's choices in the default one
  for (const p of c.steps ?? []) {
    const src = `project:${p.project}`
    const say = (t: Partial<Record<string, unknown>> | undefined) => {
      const x = t?.[language]
      return typeof x === 'string' && x && !x.includes('{') ? x : undefined
    }
    for (const t of p.tellings) {
      let speaker: VoiceName = 'female'
      for (const l of t.lines) {
        if (l.who) {
          speaker = villagerVoice.get(l.who) ?? 'female'
          add(say(l), src, SCENES, speaker)
        }
        for (const ch of l.choices) {
          add(say(ch), src, SCENES)
          add(say(ch.reply), src, SCENES, speaker)
        }
      }
    }
  }
  const later = drillPriority('A2')
  for (const t of stories) if (t.priority > SCENES && t.priority < later) add(t.text, t.source, t.priority, t.voice)
  for (const t of drills) if (t.priority > SCENES) add(t.text, t.source, t.priority, t.voice)
  for (const t of stories) if (t.priority > later) add(t.text, t.source, t.priority, t.voice)
  for (const t of course?.explanations ?? []) explain(t.text, t.lang!, t.source, teacher)
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
  /**
   * The text's language when it isn't the village's: an explanation in the learner's own (en, de, it), under its own
   * key ([explanationNorm]), made by ElevenLabs only (the local worker speaks only Slovene), plainly.
   */
  lang?: string
  /**
   * A short text (a word, up to three) made new is said inside its carrier sentence in any voice, as a re-record says it
   * (a voice the cast doesn't mark `carrier`, a villager's, says a lone word plainly otherwise): the learner's own line
   * (cast.ts PLAYER).
   */
  carrier?: boolean
}

/** Carrier re-voices a day, by default (about 720 characters sent: about 360 credits with eleven_v4_turbo, MODEL_CREDITS). */
export const DEFAULT_REVOICE_DAILY = 40

/** Re-records on request a day (the app's long press on 🔊), by default: on their own count, not the lazy re-voice's. */
export const DEFAULT_REDO_DAILY = 30

/** A whole number the node set in [name] (0 too), else undefined: the default. */
function dailySetting(env: Record<string, string | undefined>, name: string): number | undefined {
  const v = Number(env[name]?.trim() || NaN)
  return Number.isInteger(v) && v >= 0 ? v : undefined
}

/** The day's re-voice cap the node set (LANI_VOICE_REVOICE_DAILY, a whole number, 0: none), else undefined: the default. */
export const revoiceDaily = (env: Record<string, string | undefined> = process.env) => dailySetting(env, 'LANI_VOICE_REVOICE_DAILY')

/** The day's re-records on request the node allows (LANI_VOICE_REDO_DAILY, a whole number, 0: none), else undefined: the default. */
export const redoDaily = (env: Record<string, string | undefined> = process.env) => dailySetting(env, 'LANI_VOICE_REDO_DAILY')

/**
 * Whether a clip of an older ElevenLabs model than LANI_ELEVENLABS_MODEL is made again as the app asks for it, after the
 * clips of a swapped voice, within the same day's cap (LANI_VOICE_UPGRADE_MODEL_LAZY: on unless "off", "0", "false" or "no").
 */
export const lazyModelUpgrade = (env: Record<string, string | undefined> = process.env) => !/^(off|0|false|no)$/i.test(env.LANI_VOICE_UPGRADE_MODEL_LAZY?.trim() ?? '')

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
 * What voice-build --level --run (or --denoise, --tempo, --edges --run) came to: clips done (of them [kept] as they were,
 * only recorded, and [limited]; [trimmed] at their edges), left as they were (gone, busy, or changed meanwhile), failed;
 * [left]: still waiting.
 */
export type Processed = { done: number; kept: number; limited: number; trimmed: number; skipped: number; failed: number; left: number }
export type Denoised = Processed

/**
 * How a Slovene village's narrators speak (never with their English library voices, Matilda and Daniel): 'elevenlabs',
 * their Slovene ElevenLabs voices first (the cast's `languages` `sl`: Nina, Luka), the local Gepard voice when ElevenLabs
 * can't; 'gepard', the local voice first, their Slovene ElevenLabs voices when it can't (as from 10 October 2026).
 */
export type NarratorVoices = 'elevenlabs' | 'gepard'

/**
 * How a village in [language] has its narrators speak: in Slovene, LANI_VOICE_NARRATOR_SL ('elevenlabs' by default, or
 * 'gepard'); undefined for a village in another language, which keeps the narrators its cast gives it (the worker speaks
 * only Slovene). A wrong value is logged, and the default is taken.
 */
export function sloveneNarrators(language: string, env: Record<string, string | undefined> = process.env, log?: Log): NarratorVoices | undefined {
  if (language !== 'sl') return undefined
  const v = env.LANI_VOICE_NARRATOR_SL?.trim()
  if (v === 'gepard' || v === 'elevenlabs') return v
  if (v) log?.(`voice: LANI_VOICE_NARRATOR_SL=${v} is not elevenlabs or gepard: elevenlabs it is`)
  return 'elevenlabs'
}

/** The local voice a narrator's Gepard clip from before the engine's voice was recorded was made with: the female narrator's was ana. */
const GEPARD_BEFORE: Record<string, string> = { female: 'ana' }

/** What restoring the local clips paced before came to (voice-build --restore-gepard). */
export type Restored = {
  /** The paced local clips (tempo set), and of them: restored (a dry run: to restore), and not restorable (why). */
  paced: number
  restored: number
  missing: { key: string; why: string }[]
  /** Restored, their files byte for byte the snapshot's. */
  verified: number
  /** Changed meanwhile, failed; the local clips still paced after (a dry run: all). */
  skipped: number
  failed: number
  left: number
}

/** What re-voicing the narrators' clips in the local voice came to (voice-build --narrators --run). */
export type NarratorsRevoiced = { made: number; failed: number; skipped: number; left: number; stopped?: string }

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
  private pacing?: GepardPace | null
  private noPaceLogged = false
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
      /**
       * The local voice's pace (PACE), in the level's encode: undefined, from the environment (gepardPace: none unless
       * LANI_VOICE_GEPARD_TEMPO or LANI_VOICE_GEPARD_PAUSES says); null: none, the worker's own pace.
       */
      pace?: GepardPace | null
      /**
       * How a Slovene village's narrators (female, male) speak, never with the English library narrators
       * (sloveneNarrators): 'elevenlabs', their Slovene ElevenLabs voices (the cast's `languages` `sl`: Nina, Luka;
       * their clips are the voices "female.sl", "male.sl"), the local voice as their fallback; 'gepard', the local voice
       * first, their Slovene ElevenLabs voices when it can't; else none: the phone speaks. Absent: the narrators speak as
       * the cast has them (a village in another language).
       */
      narrators?: NarratorVoices
      /** The village's language, the clips' ('sl' when absent). */
      language?: string
      liveLimit?: { max: number; windowMs: number }
      /**
       * Clips re-voiced a day by the lazy re-voice (LANI_VOICE_REVOICE_DAILY; DEFAULT_REVOICE_DAILY; 0: none): old-style
       * short narrator clips in their carrier sentence and the Slovene narrators' clips in their ElevenLabs voices as the
       * app asks for them.
       */
      revoiceDaily?: number
      /**
       * Re-records on request a day (LANI_VOICE_REDO_DAILY; DEFAULT_REDO_DAILY; 0: none): Jan's long press on 🔊, on their
       * own count, so the lazy re-voice never spends them.
       */
      redoDaily?: number
      /**
       * A clip of an older ElevenLabs model made again in the current one as the app asks for it (lazyModelUpgrade:
       * LANI_VOICE_UPGRADE_MODEL_LAZY, on by default), after the clips of a swapped voice, within the day's cap.
       */
      lazyModel?: boolean
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
    // before anything else: the bridge and voice-build open this store at once (a bridge starting while voice-build
    // runs, two voice-builds), and the one that makes the file, turns it to WAL or recovers its WAL holds it alone for a
    // moment; without the timeout the other failed at once with "database is locked" (SQLITE_BUSY, CI's smoke 2026-10-12)
    this.db.exec('PRAGMA busy_timeout = 5000')
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
        gain REAL,
        tempo REAL,
        edges INTEGER,
        engine_voice TEXT,
        model TEXT
      )`)
      // a store from before the carrier sentence: how a clip was made, and how often the app asked for it; from before
      // the denoise filter: the filter it went through; from before the level: the loudness it is at, and the gain; from
      // before the local voice's pace: the factor a local clip was sped up by; from before the edges: whether they were
      // trimmed, and the engine's own voice; from before the model was recorded: the ElevenLabs model (null: eleven_v3)
      const cols = new Set((this.db.query('PRAGMA table_info(clips)').all() as { name: string }[]).map(c => c.name))
      for (const [col, spec] of [['method', 'TEXT'], ['hits', 'INTEGER NOT NULL DEFAULT 0'], ['denoise', 'TEXT'], ['level', 'REAL'], ['gain', 'REAL'], ['tempo', 'REAL'], ['edges', 'INTEGER'], ['engine_voice', 'TEXT'], ['model', 'TEXT']]) {
        try {
          if (!cols.has(col)) this.db.exec(`ALTER TABLE clips ADD COLUMN ${col} ${spec}`)
        } catch {} // another process (voice-build) added it meanwhile
      }
      this.db.exec('CREATE INDEX IF NOT EXISTS clips_file ON clips (file)')
      // carrier re-voices and re-records per day (local date), for the daily cap
      this.db.exec('CREATE TABLE IF NOT EXISTS revoices (day TEXT PRIMARY KEY, n INTEGER NOT NULL)')
      // the clips re-recorded on request, per day: once a clip a day
      this.db.exec('CREATE TABLE IF NOT EXISTS redos (key TEXT NOT NULL, day TEXT NOT NULL, PRIMARY KEY (key, day))')
      // the stale clips the app asked for (a swapped voice, an older model), how often and when first: the lazy re-voice
      // makes them again (an old clip played before isn't re-voiced until the app asks for it again)
      this.db.exec('CREATE TABLE IF NOT EXISTS stale_asks (key TEXT PRIMARY KEY, n INTEGER NOT NULL, first TEXT NOT NULL)')
      // the lines Drama 3 could not make (no take passed its checks), and why: voice-build --drama3 reports them and
      // leaves them for the next run only with --retry-failed; a line whose text changed has another key
      this.db.exec('CREATE TABLE IF NOT EXISTS drama3_failed (key TEXT PRIMARY KEY, voice TEXT NOT NULL, text TEXT NOT NULL, reasons TEXT NOT NULL, at TEXT NOT NULL, tries INTEGER NOT NULL DEFAULT 1)')
    }
  }

  get engines() { return this.o.engines }

  /** The ElevenLabs engine, when the store has one. */
  private get eleven(): ElevenLabs | undefined {
    return this.o.engines.find((e): e is ElevenLabs => e instanceof ElevenLabs)
  }

  /** The local Gepard worker, when the store has one. */
  private get gepard(): Gepard | undefined {
    return this.o.engines.find((e): e is Gepard => e instanceof Gepard)
  }

  /** The village's language, the clips'. */
  get language(): string { return this.o.language ?? 'sl' }

  /** Whether a text in [lang] is an explanation: a language given, and not the village's (an absent one is the village's). */
  explains(lang?: string): lang is string {
    return !!lang && lang !== this.language
  }

  /** The norm a text in [lang] is kept under: an explanation's ([explanationNorm]), else the text normalized. */
  normOf(text: string, lang?: string): string {
    return this.explains(lang) ? explanationNorm(lang, text) : normalizeText(text)
  }

  // --- the narrators in Slovene ------------------------------------------------------------------------------------
  //
  // A Slovene narrator's line has two clips, each a row of its own: in the narrator's Slovene ElevenLabs voice (Nina,
  // Luka: the voice "female.sl", key "female.sl:<norm>") and in the local Gepard voice (the voice "female", key
  // "female:<norm>": ana for a lone word, nina for a sentence; marko). The app knows only "female": the index gives it
  // the first there of the two, the ElevenLabs one first ('elevenlabs', the default) or the local one ('gepard'). A
  // local clip stays when the ElevenLabs one is made: it is the fallback, and what LANI_VOICE_NARRATOR_SL=gepard plays.

  /** How the narrators speak here ([sloveneNarrators]: a Slovene village), if they don't speak as the cast has them. */
  get narrators(): NarratorVoices | undefined { return this.o.narrators }

  /** Whether [voice] is a narrator of this Slovene village: its Slovene ElevenLabs voice and the local voice, never its English one. */
  sloveneNarrator(voice: VoiceName): boolean {
    return !!this.o.narrators && isNarrator(voice)
  }

  /** The voice a Slovene narrator's ElevenLabs clips are in ("female.sl": the cast's `languages` `sl`, Nina), if the cast gives it one. */
  slVoiceOf(voice: VoiceName): VoiceName | undefined {
    return this.sloveneNarrator(voice) ? languageVoice(voice, this.language) : undefined
  }

  /** Whether a Slovene narrator's clip is wanted in its Slovene ElevenLabs voice first: 'elevenlabs', with a voice in the cast. */
  elevenlabsFirst(voice: VoiceName): boolean {
    return this.o.narrators === 'elevenlabs' && !!this.slVoiceOf(voice)
  }

  /**
   * The local voice a narrator speaks [text] with here: the worker's voice for it (the cast's `gepard`, a lone word's
   * `gepard_words`: female ana for a word, nina for a sentence; male marko).
   */
  localVoiceOf(voice: VoiceName, text?: string): string | undefined {
    return this.gepard?.speakerOf(voice, text) ?? new Gepard().speakerOf(voice, text)
  }

  /**
   * Whether [c], a narrator's clip, is in the narrator's local voice now: a Gepard clip of that worker voice for its text
   * (one from before the engine's voice was recorded: the female narrator's was ana's, GEPARD_BEFORE).
   */
  inLocalVoice(c: Clip): boolean {
    if (c.engine !== 'gepard') return false
    const want = this.localVoiceOf(c.voice, c.text)
    return !want || (c.engine_voice ?? GEPARD_BEFORE[c.voice] ?? want) === want
  }

  /**
   * The clip the app gets for [norm] in [voice]: a Slovene narrator's in its Slovene ElevenLabs voice or in the local
   * voice, whichever is there first in the order the setting gives ('elevenlabs': ElevenLabs first); anyone else's own.
   */
  best(norm: string, voice: VoiceName): Clip | undefined {
    const sl = this.slVoiceOf(voice)
    if (!sl) return this.get(norm, voice)
    return this.o.narrators === 'elevenlabs' ? (this.get(norm, sl) ?? this.get(norm, voice)) : (this.get(norm, voice) ?? this.get(norm, sl))
  }

  /** Whether a Slovene narrator's line [norm] still wants its clip in the narrator's Slovene ElevenLabs voice (ElevenLabs first, none yet). */
  wantsElevenLabs(norm: string, voice: VoiceName): boolean {
    return this.elevenlabsFirst(voice) && !this.get(norm, this.slVoiceOf(voice)!)
  }

  get revoiceDaily(): number { return this.o.revoiceDaily ?? DEFAULT_REVOICE_DAILY }

  get redoDaily(): number { return this.o.redoDaily ?? DEFAULT_REDO_DAILY }

  /**
   * The characters ElevenLabs would be sent for [text] in [voice] (a short word's carrier sentence); a Slovene narrator's:
   * in its Slovene ElevenLabs voice where that comes first (Nina, Luka: a short word's carrier sentence too), else the
   * text. An explanation in another language ([lang]): its text, said plainly.
   */
  cost(text: string, voice: VoiceName, lang?: string): number {
    if (this.explains(lang)) return text.trim().length
    if (this.sloveneNarrator(voice)) return (this.elevenlabsFirst(voice) && this.eleven?.cost(text, this.slVoiceOf(voice)!)) || text.trim().length
    return this.eleven?.cost(text, voice) || text.trim().length
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

  /**
   * normalized text → speaker (female, male, grandma, …) → file URL. A Slovene narrator's entry is its clip in the
   * narrator's Slovene ElevenLabs voice or in the local voice, the first there in the setting's order ([best]); the app
   * knows the narrator by its own name.
   */
  index(): Record<string, Record<VoiceName, string>> {
    const out: Record<string, Record<VoiceName, string>> = {}
    // the Slovene narrators' voices: "female.sl" → female
    const sl = new Map(GENDERS.map(v => [this.slVoiceOf(v), v] as const).filter((e): e is [string, Gender] => !!e[0]))
    const later: Pick<Clip, 'norm' | 'voice' | 'file'>[] = []
    for (const c of this.db.query('SELECT norm, voice, file FROM clips').all() as Pick<Clip, 'norm' | 'voice' | 'file'>[]) {
      if (sl.has(c.voice)) later.push(c)
      else if (!LANGUAGE_VOICE.test(c.voice)) (out[c.norm] ??= {})[c.voice] = fileUrl(c.file)
    }
    for (const c of later) {
      const as = sl.get(c.voice)!
      const at = (out[c.norm] ??= {})
      if (this.o.narrators === 'elevenlabs' || !at[as]) at[as] = fileUrl(c.file)
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

  /** The local voice's pace (PACE; LANI_VOICE_GEPARD_TEMPO, LANI_VOICE_GEPARD_PAUSES); null: none, the worker's own. */
  get pace(): GepardPace | null {
    if (this.pacing === undefined) this.pacing = this.o.pace === undefined ? gepardPace(process.env, this.o.log) : this.o.pace
    return this.pacing
  }

  /**
   * [bytes] as they are stored: through the filter of [voice] ([filtered]), paced at its voice's tempo and a local clip
   * at the local voice's pace when one is set (clipPace), its edges trimmed (EDGES; a single word's ends at its first
   * long pause, [word]) and brought to the level, in one encode when they are ffmpeg's. What can't be done now (no
   * ffmpeg, ffmpeg failed) is left out and logged (no ffmpeg: once): voice-build --level (and --denoise, --pace, --edges)
   * does it later.
   */
  private async processed(bytes: Uint8Array, voice: VoiceName, engine: EngineName, text: string, word = false): Promise<{ bytes: Uint8Array; denoise: DenoisePreset | null; level: number | null; gain: number | null; tempo: number | null; edges: number | null }> {
    const preset = engine === 'elevenlabs' ? denoiseOf(voice) : undefined
    const pace = clipPace(voice, engine, this.pace) ?? undefined
    const lv = this.leveller
    // ffmpeg's own filter runs in the level's encode; another one (a stand-in) before it
    if (lv && (!preset || this.o.denoise === undefined)) {
      const r = await lv.level(bytes, { denoise: preset, pace, edges: true, word })
      if (r) return { bytes: r.bytes, denoise: preset ?? null, level: lv.target, gain: r.gain, tempo: pace?.tempo ?? null, edges: r.edges ? 1 : null }
    }
    const f = await this.filtered(bytes, voice, engine, text)
    // after a stand-in's filter: the pace, the edges and the level in the level's encode
    const after = !!lv && !!preset && this.o.denoise !== undefined ? pace : undefined
    if (pace && !after && (lv || !this.noPaceLogged)) {
      this.noPaceLogged ||= !lv
      this.o.log?.(`voice: "${text}" (${voice}) stored at its own pace, not ×${pace.tempo}: ${lv ? 'ffmpeg failed' : 'no ffmpeg'}; voice-build --pace paces it later`)
    }
    const r = lv ? await lv.level(f.bytes, { pace: after, edges: true, word }) : null
    if (r) return { ...f, bytes: r.bytes, level: lv!.target, gain: r.gain, tempo: after?.tempo ?? null, edges: r.edges ? 1 : null }
    if (!this.noLevelLogged) {
      this.noLevelLogged = true
      this.o.log?.(`voice: "${text}" (${voice}) stored without the level and its edges trimmed: ${lv ? 'ffmpeg failed' : 'no ffmpeg'}; voice-build --level levels it later (logged once)`)
    }
    return { ...f, level: null, gain: null, tempo: null, edges: null }
  }

  private async save(
    norm: string, text: string, voice: VoiceName, engine: EngineName, bytes: Uint8Array, source: string,
    take: Omit<Take, 'bytes'> & {
      /** Another take of the same (a re-record): a name of its own. */
      salt?: string
    } = {},
  ): Promise<Clip> {
    const key = clipKey(norm, voice)
    // a voice that hisses (the cast's `denoise`): filtered; a local clip: paced when a pace is set; every clip: its edges
    // trimmed (a single word asked for as it is: up to its first long pause; a carrier cut is the word already; never an
    // explanation, which may well be one word) and brought to the level
    const f = await this.processed(bytes, voice, engine, text, take.method !== 'carrier' && !explanationLang(norm) && isOneWord(text))
    // A clip's name carries the engine's voice (the ElevenLabs voice id, the Gepard voice), so a swapped voice's clips get
    // new names (the app caches by name); a carrier cut is a new take of the same text, so it gets a new name too and the
    // app downloads it; so does a re-record, and so does a filtered, paced, trimmed or levelled clip; and a take by another
    // ElevenLabs model (one from before the model was recorded keeps its name).
    const own = take.voice ?? (engine === 'elevenlabs' ? (elevenlabsOf(voice) ?? '') : undefined)
    const model = engine === 'elevenlabs' ? (take.model ?? null) : null
    const tag = own !== undefined ? `|${own}` : ''
    const how = take.method === 'carrier' ? '|carrier' : ''
    const salt = take.salt ? `|${take.salt}` : ''
    const filter = f.denoise ? `|denoise:${f.denoise}` : ''
    const level = f.level !== null ? `|level:${f.level}` : ''
    const pace = f.tempo !== null ? `|tempo:${f.tempo}` : ''
    const edges = f.edges ? '|edges' : ''
    const by = model ? `|model:${model}` : ''
    const file = `${createHash('sha1').update(`${key}|${engine}${tag}${how}${salt}${filter}${level}${pace}${edges}${by}`).digest('hex')}.mp3`
    const p = join(this.filesDir, file)
    writeFileSync(`${p}.tmp`, f.bytes)
    renameSync(`${p}.tmp`, p)
    const old = this.get(norm, voice)
    const clip: Clip = {
      key, norm, text, voice, engine, file, chars: take.chars ?? text.length, created_at: new Date().toISOString(), source,
      method: take.method ?? null, hits: old?.hits ?? 0, denoise: f.denoise, level: f.level, gain: f.gain, tempo: f.tempo,
      edges: f.edges, engine_voice: own || null, model,
    }
    this.db
      .query('INSERT OR REPLACE INTO clips (key, norm, text, voice, engine, file, chars, created_at, source, method, hits, denoise, level, gain, tempo, edges, engine_voice, model) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)')
      .run(key, norm, text, voice, engine, file, clip.chars, clip.created_at, source, clip.method ?? null, clip.hits ?? 0, clip.denoise ?? null, clip.level ?? null, clip.gain ?? null, clip.tempo ?? null, clip.edges ?? null, clip.engine_voice ?? null, model)
    this.seen?.files.set(key, file) // the bridge's own clip: not another process's change
    // a new take: whatever made the old one stale, the app's asks for it are answered
    try {
      this.db.query('DELETE FROM stale_asks WHERE key = ?').run(key)
    } catch {} // a store from before, only read
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
    return !isNarrator(voice) && !!this.best(norm, genderOf(voice))
  }

  /**
   * The clip for [text] in [voice] (a cast speaker): an existing one, else ElevenLabs if the quota
   * allows, else the local worker if it is up (not for a character whose gender clip exists, see
   * [waitsForElevenLabs]). A Slovene narrator ([sloveneNarrator]) never speaks with its English voice: see
   * [narratorVoice]. Null when no engine can make it now, or for a speaker not in the cast; never throws. An explanation
   * in another language ([opt].lang) is kept under its own key ([explanationNorm]) and made by ElevenLabs only, plainly.
   */
  async voice(text: string, voice: VoiceName, source: string, opt: VoiceOptions = {}): Promise<Clip | null> {
    const lang = this.explains(opt.lang) ? opt.lang : undefined
    if (!normalizeText(text) || !isVoice(voice)) return null
    const norm = this.normOf(text, lang)
    // the local worker speaks only Slovene
    if (lang) opt = { ...opt, lang, engines: (opt.engines ?? ['elevenlabs']).filter(e => e === 'elevenlabs') }
    if (this.sloveneNarrator(voice)) return this.narratorVoice(norm, text, voice, source, opt)
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
        // a clip there is replaced only by an engine ranked as high: never a Drama 3 clip by a Gepard take
        if (have && !outranks(e.name, have)) continue
        try {
          // a short narrator word costs its carrier sentence
          if (!(await e.ready(e.cost?.(t, voice, lang, opt.carrier) ?? t.length, !!opt.live))) continue
          const take = await e.synth(t, voice, { live: !!opt.live, lang, carrier: opt.carrier })
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
   * A Slovene narrator's clip ([voice]: female, male): the one there ([best]), else made: in its Slovene ElevenLabs voice
   * (the cast's `languages` `sl`: Nina, Luka; stored as that voice, "female.sl") and in the local voice (stored as the
   * narrator: ana for a lone word, nina for a sentence; marko), in the setting's order ('elevenlabs': ElevenLabs first),
   * never in the English library voice; else none, and the phone speaks. [opt].engines makes only those clips (an upgrade
   * to ElevenLabs, a re-voice in the local voice), and the one there of those is the one unless [opt].force. Each clip
   * is a row of its own, so a local clip stays when the ElevenLabs one is made.
   */
  private narratorVoice(norm: string, text: string, voice: VoiceName, source: string, opt: VoiceOptions): Promise<Clip | null> | Clip | null {
    const have = this.best(norm, voice)
    if (have && !opt.force && !opt.engines) return have
    const sl = this.slVoiceOf(voice)
    // each engine's clip: ElevenLabs' in the narrator's Slovene voice, the worker's in the narrator's key
    const as = (e: EngineName): VoiceName | undefined => (e === 'gepard' ? voice : sl)
    const order: EngineName[] = this.o.narrators === 'elevenlabs' ? ['elevenlabs', 'gepard'] : ['gepard', 'elevenlabs']
    const engines = order.filter(e => (!opt.engines || opt.engines.includes(e)) && !!as(e))
    if (!opt.force) {
      for (const e of engines) {
        const c = this.get(norm, as(e)!)
        if (c) return c
      }
    }
    const keys = engines.map(e => clipKey(norm, as(e)!))
    const busy = keys.map(k => this.running.get(k)).find(Boolean)
    if (busy) return busy
    const job = (async () => {
      const t = text.trim()
      for (const name of engines) {
        const e = this.o.engines.find(x => x.name === name)
        if (!e) continue
        const v = as(name)!
        try {
          // a lone word costs its carrier sentence (Nina and Luka say it in one since 2026-10-26; [opt].carrier: in any
          // voice, the learner's own line); an explanation in another language, said plainly, its text
          if (!(await e.ready(e.cost?.(t, v, opt.lang, opt.carrier) ?? t.length, !!opt.live))) continue
          const take = await e.synth(t, v, { live: !!opt.live, lang: opt.lang, carrier: opt.carrier })
          if (take) return await this.save(norm, t, v, name, take.bytes, source, take)
        } catch (err) {
          this.o.log?.(`voice: ${name} failed: ${(err as Error).message}`)
        }
      }
      return have ?? null
    })().finally(() => keys.forEach(k => this.running.delete(k)))
    for (const k of keys) this.running.set(k, job)
    return job
  }

  /**
   * A live line from the app: rate-limited, may use the reserve; the speaker's gender clip, then the
   * other narrator's, beats nothing. An explanation in the learner's own language ([lang]: a school intro's step) is
   * rate-limited the same but is no live line: it never goes into the reserve, and nobody else's voice stands in for the
   * teacher's (the app reads it with the phone's voice and the Slovene clips meanwhile). [o].carrier: a short text made new
   * is said in its carrier sentence ([VoiceOptions].carrier); [o].alone: nobody else's clip stands in either (the
   * learner's own line, in their voice or not at all). A character's local clip there is made in the speaker's ElevenLabs
   * voice first when it can be ([upgradeLocal]); the local clip is served when it can't.
   */
  async say(text: string, voice: VoiceName, lang?: string, o: { carrier?: boolean; alone?: boolean } = {}): Promise<Clip | null> {
    const explanation = this.explains(lang)
    const norm = this.normOf(text, lang)
    const have = this.best(norm, voice)
    // a character's local clip: in the speaker's ElevenLabs voice now when it can be (the lazy re-voice's cap, never the
    // reserve, within the live lines' rate limit), the local clip only when it can't
    if (have && this.wantsElevenLabsVoice(have) && this.lazyToday() < this.revoiceDaily && this.liveSlot()) {
      const up = await this.upgradeLocal(have)
      if (up) return this.served(up)
    }
    if (have) return this.served(have)
    const other = () => {
      if (explanation || o.alone) return null
      const c = fallbacks(voice).map(v => this.best(norm, v)).find(Boolean)
      return c ? this.served(c) : null
    }
    if (!this.running.has(clipKey(norm, voice)) && !this.liveSlot()) return other()
    if (explanation) return this.voice(text, voice, 'explanation', { lang })
    return (await this.voice(text, voice, 'live', { live: true, carrier: o.carrier })) ?? other()
  }

  /**
   * A live line may be voiced now: within the rate limit of the app's live lines ([liveLimit]: 60 in 10 minutes unless
   * set), which this takes a place of; false over it. A visit's lines voiced on this node count in it too
   * (visit-voices.ts).
   */
  liveSlot(): boolean {
    const lim = this.o.liveLimit ?? { max: 60, windowMs: 600_000 }
    const now = Date.now()
    this.liveCalls = this.liveCalls.filter(t => now - t < lim.windowMs)
    if (this.liveCalls.length >= lim.max) return false
    this.liveCalls.push(now)
    return true
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
   * narrator word said plainly) it is served as it is now and re-voiced in its carrier sentence soon; a Slovene narrator's
   * local clip whose ElevenLabs one is wanted ([wantsUpgrade]) is served as it is now, and the ElevenLabs one made soon.
   */
  private served(c: Clip): Clip {
    if (this.o.readonly) return c
    try {
      this.db.query('UPDATE clips SET hits = hits + 1 WHERE key = ?').run(c.key)
    } catch {}
    const stale = this.stale(c)
    if (stale) {
      try {
        this.db.query('INSERT INTO stale_asks (key, n, first) VALUES (?, 1, ?) ON CONFLICT (key) DO UPDATE SET n = n + 1').run(c.key, new Date().toISOString())
      } catch {}
    }
    if (stale || this.oldStyle(c) || this.wantsUpgrade(c) || this.wantsElevenLabsVoice(c)) this.scheduleRevoice()
    return c
  }

  /**
   * A clip of a short text in a carrier voice that ElevenLabs made plainly, before the carrier sentence: it may sound
   * English. Not a Slovene narrator's ([sloveneNarrator]): those never speak with the English voice. Never a clip of
   * one's voice in a language ("female.sl", "male.sl": Nina, Luka), though the cast marks it `carrier` since
   * 2026-10-26: its lone words from before were said plainly on purpose by a native voice and are kept (a long press
   * re-records one). Never an explanation in the learner's own language ([explanationLang]), however short: it was
   * said plainly on purpose.
   */
  oldStyle(c: Clip): boolean {
    return c.engine === 'elevenlabs' && !c.method && !explanationLang(c.norm) && !this.sloveneNarrator(c.voice) && !LANGUAGE_VOICE.test(c.voice) &&
      !!this.eleven?.carrierFor(c.text, c.voice)
  }

  // --- the Slovene narrators in their ElevenLabs voices, as the app asks for them --------------------------------------

  /**
   * Whether [c] is a Slovene narrator's clip in the narrator's key (the local voice's, or one from before) whose clip in
   * the narrator's Slovene ElevenLabs voice is wanted first and missing, with ElevenLabs set up: the app plays [c]
   * meanwhile, and the bridge makes the other one soon, within the day's cap and above the reserve. Never an explanation
   * ([explanationLang]): its text isn't Slovene, and it is only ever ElevenLabs'.
   */
  wantsUpgrade(c: Clip): boolean {
    return !this.o.readonly && !!this.eleven?.configured && !explanationLang(c.norm) && this.sloveneNarrator(c.voice) && this.wantsElevenLabs(c.norm, c.voice)
  }

  // --- the characters' local clips, in their speaker's ElevenLabs voice as the app asks for them ------------------------
  //
  // Jan (2026-10-27): "there are some gepard recordings which should be replaced". A character's line the local worker
  // said while ElevenLabs couldn't (the quota, an error; voice-build over its budget) is made in the speaker's ElevenLabs
  // voice when the app asks for it: a download of its file (the day's clips, got ready a day ahead, so it is mostly done
  // before the line is heard), /voice/say and /voice/prepare handing it over. Within the lazy re-voice's daily cap
  // ([revoiceDaily], shared with the carrier words, the narrators' Slovene voices and the stale clips) and never into the
  // reserve; /voice/say and /voice/prepare make it at once when they can and serve the local clip only when ElevenLabs
  // can't, a download is served as it is and the clip made soon. Never a narrator's (their local clips are the fallback
  // of their Slovene ElevenLabs voices, by design), nor Drama 3's (made on purpose, ranked above Gepard's).

  /**
   * Whether [c] is a character's local (Gepard) clip whose speaker has an ElevenLabs voice now, with ElevenLabs set up:
   * the lazy re-voice makes it in that voice as the app asks for it ([upgradeLocal]).
   */
  wantsElevenLabsVoice(c: Clip): boolean {
    return !this.o.readonly && !!this.eleven?.configured && c.engine === 'gepard' && !isNarrator(c.voice) && !LANGUAGE_VOICE.test(c.voice) &&
      !explanationLang(c.norm) && !!elevenlabsOf(c.voice)
  }

  /** The characters' local clips waiting for their ElevenLabs voice ([wantsElevenLabsVoice]), most asked for first; [asked]: only those the app asked for. */
  localUpgradeQueue(asked = true): Clip[] {
    if (!this.eleven?.configured) return []
    return (this.db.query("SELECT * FROM clips WHERE engine = 'gepard' AND voice NOT IN ('female', 'male')").all() as Clip[])
      .filter(c => (!asked || (c.hits ?? 0) > 0) && this.wantsElevenLabsVoice(c))
      .sort((a, b) => (b.hits ?? 0) - (a.hits ?? 0) || a.created_at.localeCompare(b.created_at))
  }

  /** The characters' local clips waiting for their ElevenLabs voice: all, those the app asked for, and what making them all costs. */
  localStats() {
    const all = this.localUpgradeQueue(false)
    const chars = all.reduce((n, c) => n + (this.eleven?.cost(c.text, c.voice) ?? c.text.length), 0)
    return { clips: all.length, asked: all.filter(c => (c.hits ?? 0) > 0).length, chars, credits: creditsFor(chars, this.model ?? DEFAULT_ELEVENLABS_MODEL) }
  }

  /**
   * [c], a character's local clip ([wantsElevenLabsVoice]), made in its speaker's ElevenLabs voice now when the day's cap
   * has room and the quota is above the reserve ([remake]: its filter, edges and level, a new file name, its hits kept,
   * the local file removed; the app is told): the new clip, else undefined (it is served as it is, and the lazy re-voice
   * makes it when it can).
   */
  async upgradeLocal(c: Clip): Promise<Clip | undefined> {
    if (!this.wantsElevenLabsVoice(c) || this.revoiceDaily <= 0 || this.lazyToday() >= this.revoiceDaily) return undefined
    if ((await this.remake(c, true)) !== 'made') return undefined
    const now = this.get(c.norm, c.voice)
    if (!now || now.engine !== 'elevenlabs') return undefined
    this.o.log?.(`voice: "${c.text}" (${c.voice}) made in its ElevenLabs voice as the app asked for it, the local clip replaced (${this.lazyToday()}/${this.revoiceDaily} today)`)
    this.o.onUpdated?.(1)
    return now
  }

  /** The Slovene narrators' clips waiting for their ElevenLabs one ([wantsUpgrade]), most asked for first; [asked]: only those the app asked for. */
  upgradeQueue(asked = true): Clip[] {
    if (!this.o.narrators || !this.eleven?.configured) return []
    return (this.db.query("SELECT * FROM clips WHERE voice IN ('female', 'male')").all() as Clip[])
      .filter(c => (!asked || (c.hits ?? 0) > 0) && this.wantsUpgrade(c))
      .sort((a, b) => (b.hits ?? 0) - (a.hits ?? 0) || a.created_at.localeCompare(b.created_at))
  }

  // --- stale clips: a swapped voice, an older model, made again as the app asks for them ---------------------------------
  //
  // Jan: "we can regenerate on demand". A clip of a voice that was swapped (the cast's speaker, or someone's own voice)
  // or of an older ElevenLabs model is served as it is when the app asks for it, and made again soon in the current
  // voice and model (the lazy re-voice: the day's cap, never the reserve; the swapped voices first), stored as every new
  // clip is (the cast's filter for the voice now, its edges, the level) under a new file name; the app is told.

  /**
   * Whether [c] is an ElevenLabs clip not in its speaker's ElevenLabs voice now ([elevenlabsOf]: the cast's, one's voice
   * in a language, someone's own): its recorded voice another, or, unrecorded (a clip from before October 11, 2026),
   * made before the day the voice took effect (the cast's `since`). Not a Slovene narrator's from before in its English
   * voice (never asked for again), nor a voice ElevenLabs has none for now.
   */
  staleVoice(c: Clip): boolean {
    if (c.engine !== 'elevenlabs' || this.sloveneNarrator(c.voice)) return false
    const now = elevenlabsOf(c.voice)
    if (!now) return false
    if (c.engine_voice) return c.engine_voice !== now
    const since = voiceSince(c.voice)
    return !!since && c.created_at.slice(0, 10) < since
  }

  /** Whether [c] is an ElevenLabs clip of an older model than new clips are made with ([modelOf], [model]), in a voice ElevenLabs has. */
  staleModel(c: Clip): boolean {
    const now = this.model
    return !!now && c.engine === 'elevenlabs' && !this.sloveneNarrator(c.voice) && !!elevenlabsOf(c.voice) && modelOf(c) !== now
  }

  /** Whether the lazy re-voice makes [c] again when the app asks for it: a swapped voice, or an older model (with lazyModel). */
  stale(c: Clip): 'voice' | 'model' | undefined {
    if (this.o.readonly || !this.eleven?.configured) return undefined
    if (this.staleVoice(c)) return 'voice'
    if (this.lazyModel && this.staleModel(c)) return 'model'
    return undefined
  }

  /** Whether an older model's clips are made again as the app asks for them (LANI_VOICE_UPGRADE_MODEL_LAZY). */
  get lazyModel(): boolean {
    return this.o.lazyModel ?? true
  }

  /**
   * The stale clips the lazy re-voice makes again, most asked for first (hits), then the oldest: a swapped voice's
   * ([kind] 'voice') the app asked for since it went stale or played before (the phone keeps the clips it has and doesn't
   * ask for them again: a voice swapped under a line the learner has heard would stay two voices); an older model's
   * ('model') only those the app asked for since (the asks recorded as they come: a better take, not a wrong one, so
   * the quota isn't spent on clips nobody asks for).
   */
  staleQueue(kind: 'voice' | 'model'): Clip[] {
    if (!this.eleven?.configured) return []
    let rows: (Clip & { asked: number | null })[]
    try {
      rows = this.db.query("SELECT c.*, s.n AS asked FROM clips c LEFT JOIN stale_asks s ON s.key = c.key WHERE c.engine = 'elevenlabs' AND (s.n IS NOT NULL OR c.hits > 0)").all() as (Clip & { asked: number | null })[]
    } catch {
      return [] // a store from before, only read
    }
    return rows
      .filter(c => this.stale(c) === kind && (c.asked !== null || kind === 'voice'))
      .map(({ asked: _, ...c }) => c as Clip)
      .sort((a, b) => (b.hits ?? 0) - (a.hits ?? 0) || a.created_at.localeCompare(b.created_at))
  }

  /**
   * The stale clips in the store: of a swapped voice and of an older model, all and those the app asked for since
   * ([staleQueue]), and what making them all again costs (characters sent, credits with the model now: MODEL_CREDITS).
   */
  staleStats() {
    const el = this.eleven
    const all = (this.db.query("SELECT * FROM clips WHERE engine = 'elevenlabs'").all() as Clip[])
    const voice = all.filter(c => this.staleVoice(c))
    const model = all.filter(c => !this.staleVoice(c) && this.staleModel(c))
    const cost = (cs: Clip[]) => cs.reduce((n, c) => n + (el?.cost(c.text, c.voice, explanationLang(c.norm)) ?? c.text.length), 0)
    const chars = cost(voice) + cost(model)
    const m = this.model ?? DEFAULT_ELEVENLABS_MODEL
    return {
      voice: { clips: voice.length, asked: this.staleQueue('voice').length, chars: cost(voice) },
      model: { clips: model.length, asked: this.staleQueue('model').length, chars: cost(model), lazy: this.lazyModel, now: m },
      all: { chars, credits: creditsFor(chars, m) },
    }
  }

  /**
   * [c] made again in its speaker's ElevenLabs voice and the model now, as voice-build --upgrade-model and the lazy
   * re-voice do: its voice's filter (the cast's now), its edges and the level as every new clip, a new file name, its hits
   * kept, the old file removed: 'made'; 'skip' for a voice ElevenLabs has none for now; 'stop' when it can't now (the quota
   * down to the reserve, kept for live lines; an error; a read-only store). [count]: a take paid for counts for the day.
   */
  private async remake(c: Clip, count: boolean): Promise<'made' | 'skip' | 'stop'> {
    const el = this.eleven
    if (this.o.readonly || !el || this.running.has(c.key)) return 'stop'
    if (!elevenlabsOf(c.voice)) return 'skip'
    const lang = explanationLang(c.norm)
    if (!(await el.ready(el.cost(c.text, c.voice, lang), false))) return 'stop'
    const before = el.remaining()
    const job = (async () => {
      const take = await el.synth(c.text, c.voice, { lang })
      return take ? await this.save(c.norm, c.text, c.voice, 'elevenlabs', take.bytes, c.source, take) : null
    })()
    this.running.set(c.key, job.catch(() => null).finally(() => this.running.delete(c.key)))
    const made = await job.catch(() => null)
    if (count && (made || el.remaining() !== before)) this.countForToday()
    return made ? 'made' : 'stop'
  }

  /**
   * What the lazy re-voice takes up: old-style words, the Slovene narrators' upgrades, the clips of a swapped voice and the
   * characters' local clips ([localUpgradeQueue]) the app asked for, most asked for first, then the oldest; after them the
   * older model's ([lazyModel]).
   */
  lazyQueue(asked = true): Clip[] {
    const first = [...this.revoiceQueue(asked), ...this.upgradeQueue(asked), ...this.staleQueue('voice'), ...this.localUpgradeQueue(asked)]
      .sort((a, b) => (b.hits ?? 0) - (a.hits ?? 0) || a.created_at.localeCompare(b.created_at))
    const seen = new Set(first.map(c => c.key))
    return [...first, ...(this.lazyModel ? this.staleQueue('model').filter(c => !seen.has(c.key)) : [])]
  }

  /**
   * [c], a Slovene narrator's clip in the narrator's key, made in the narrator's Slovene ElevenLabs voice too (Nina, Luka;
   * a lone word inside its carrier sentence), the clip there kept as its fallback: 'made'; 'stop' when it can't be now
   * (the quota down to the reserve, kept for live lines; an error). A take paid for counts for the day.
   */
  async upgradeOne(c: Clip): Promise<Revoiced> {
    const el = this.eleven
    const sl = this.slVoiceOf(c.voice)
    if (this.o.readonly || !el || !sl || this.running.has(clipKey(c.norm, sl))) return 'stop'
    if (!(await el.ready(el.cost(c.text, sl), false))) return 'stop'
    const before = el.remaining()
    const job = (async (): Promise<Clip | null> => {
      const take = await el.synth(c.text, sl)
      return take ? await this.save(c.norm, c.text, sl, 'elevenlabs', take.bytes, c.source, take) : null
    })()
    this.running.set(clipKey(c.norm, sl), job.catch(() => null).finally(() => this.running.delete(clipKey(c.norm, sl))))
    const made = await job.catch(() => null)
    if (made || el.remaining() !== before) this.countForToday()
    return made ? 'made' : 'stop'
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
      if (r instanceof Uint8Array) return ['made', await this.save(c.norm, c.text, c.voice, 'elevenlabs', r, c.source, { method: 'carrier', chars: cost, model: el.model }), true]
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

  /** One more re-voice (or re-record: [lazyToday] leaves those out) for today. */
  private countForToday() {
    this.db.query('INSERT INTO revoices (day, n) VALUES (?, 1) ON CONFLICT (day) DO UPDATE SET n = n + 1').run(localDay())
  }

  /**
   * Today's re-voices of the lazy re-voice's cap ([revoiceDaily]; voice-build's counted in it too): the day's re-voices
   * less the re-records on request, which have their own ([redoDaily]).
   */
  lazyToday(): number {
    return Math.max(0, this.revoicedToday() - this.redosToday())
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
   * anything else is a plain re-take (ElevenLabs never says a text the same way twice); a clip the local worker made is
   * made with ElevenLabs. The new take gets a new file name, the old file goes, and the app is told (voice_updated).
   * Once a clip a day; each counts in the day's re-records ([redoDaily], their own: the lazy re-voice doesn't spend
   * them) and never goes into the reserve for live lines. A text without a clip is voiced as a
   * live line is (/voice/say), not counted. A Slovene narrator ([sloveneNarrator]) is re-recorded in its Slovene
   * ElevenLabs voice (Nina, Luka) where that comes first: a re-take, or its first clip in it when only the local one is
   * there, counted as any re-record; the local clip stays. Where the local voice comes first (or ElevenLabs isn't set up),
   * a clip from before is made in the local voice, not counted; one in it is the one (not_needed: the local voice says a
   * text the same way every time). Never throws.
   */
  async redo(text: string, voice: VoiceName): Promise<Redo> {
    const norm = normalizeText(text)
    if (this.o.readonly || !norm || !isVoice(voice)) return { status: 'failed', message: 'nothing to re-record' }
    const narrator = this.sloveneNarrator(voice)
    // the clip re-recorded: a Slovene narrator's in its Slovene ElevenLabs voice ("female.sl") where that comes first
    const target = narrator && this.elevenlabsFirst(voice) && this.eleven?.configured ? this.slVoiceOf(voice)! : voice
    const key = clipKey(norm, target)
    try {
      // being made right now (the lazy re-voice, a live line): that take is the new one
      const busy = this.running.get(key)
      if (busy) {
        const had = this.get(norm, target)?.file
        const c = await busy
        if (c && c.voice === target && c.file !== had) return { status: 'done', clip: c }
      }
      const have = narrator ? this.best(norm, voice) : this.get(norm, voice)
      if (!have) {
        const c = await this.say(text, voice)
        return c ? { status: 'done', clip: c } : { status: 'failed', message: 'no engine can voice it now' }
      }
      // a Slovene narrator in the local voice: it says a text the same way every time, so a clip in it is the one; a clip
      // from before (an English narrator's, the female narrator's other local voice) is made in it now
      if (narrator && target === voice) {
        if (this.inLocalVoice(have)) return { status: 'not_needed', clip: have, message: `"${have.text}" is in the local voice, which says it the same way every time` }
        const c = await this.voice(have.text, voice, have.source, { force: true })
        if (!c || c.file === have.file) return { status: 'failed', message: 'the local voice cannot make it now' }
        this.o.log?.(`voice: "${c.text}" (${voice}) re-recorded on request in the local voice (${c.engine_voice ?? c.engine})`)
        this.o.onUpdated?.(1)
        return { status: 'done', clip: c }
      }
      if (this.redoneToday(key)) return { status: 'not_needed', clip: this.get(norm, target) ?? have, message: `"${have.text}" was re-recorded today already (once a clip a day)` }
      const el = this.eleven
      if (!el?.configured) return { status: 'failed', message: 'ElevenLabs is not set up on the node' }
      const today = this.redosToday()
      if (today >= this.redoDaily) return { status: 'limit', reason: 'cap', message: `no re-recordings left today (${today} of ${this.redoDaily})` }
      // a word alone: said inside its carrier sentence, in every voice. Said plainly, a word alone can come out in
      // another language's sound even in a native voice (Nina): ElevenLabs takes language_code as a hint only
      // ("učiteljica" sounded Italian to Jan, 2026-10-19, and a re-take the same); inside "Beseda je: …" it is Slovene.
      // So since 2026-10-26 Nina and Luka say every new lone word in it; a re-record is the way to one from before
      const cost = el.cost(have.text, target, undefined, true)
      if (!(await el.ready(cost, false))) {
        const left = el.remaining()
        return left !== undefined && left - cost < el.reserve
          ? { status: 'limit', reason: 'reserve', message: `the ${left} ElevenLabs characters left are kept for live lines (the reserve is ${el.reserve})` }
          : { status: 'failed', message: 'ElevenLabs cannot make it now' }
      }
      const before = el.remaining()
      const job = (async (): Promise<Clip | null> => {
        // the learner waits for it (a long press on 🔊): ahead of the background work, but not from the live reserve
        const take = await el.synth(have.text, target, { urgent: true, carrier: true })
        return take ? await this.save(norm, have.text, target, 'elevenlabs', take.bytes, have.source, { ...take, salt: `redo ${new Date().toISOString()}` }) : null
      })()
      this.running.set(key, job.catch(() => null).finally(() => this.running.delete(key)))
      const c = await job
      // a take paid for counts, whatever came of it
      if (c || el.remaining() !== before) this.countForToday()
      if (!c) return { status: 'failed', message: 'ElevenLabs could not make it now' }
      this.db.query('INSERT OR IGNORE INTO redos (key, day) VALUES (?, ?)').run(key, localDay())
      this.o.log?.(`voice: "${c.text}" (${target}) re-recorded on request, ${c.method ?? 'plainly'} (${this.redosToday()}/${this.redoDaily} today)`)
      this.o.onUpdated?.(1)
      return { status: 'done', clip: c }
    } catch (e) {
      this.o.log?.(`voice: re-recording "${text}" (${voice}) failed: ${(e as Error).message}`)
      return { status: 'failed', message: 'the re-record failed' }
    }
  }

  /**
   * The old-style clips and the Slovene narrators' upgrades the app asked for and that wait (the cap or the reserve
   * stopped them): taken up soon.
   */
  resumeRevoice() {
    if (this.lazyQueue().length) this.scheduleRevoice()
  }

  private scheduleRevoice() {
    if (this.o.readonly || this.revoicing || this.revoiceSoon || this.revoiceDaily <= 0) return
    // a moment later: a screen's requests are counted first, so the most asked for go first
    this.revoiceSoon = setTimeout(() => {
      this.revoiceSoon = undefined
      void this.revoiceDrain()
    }, this.o.queueDelayMs ?? 1500)
    this.revoiceSoon.unref?.()
  }

  /**
   * Takes up what the app asked for ([lazyQueue]), one at a time, most asked for first, up to the daily cap ([lazyToday]
   * of [revoiceDaily]; re-records on request have their own) and never into the reserve: an old-style word again in its carrier sentence, a
   * Slovene narrator's line in its Slovene ElevenLabs voice (its local clip kept), a stale clip again, a character's local
   * clip in its speaker's ElevenLabs voice (the local clip replaced); then the app is told (voice_updated) and fetches the
   * new files.
   */
  private async revoiceDrain() {
    if (this.revoicing) return
    this.revoicing = true
    let words = 0
    let narrators = 0
    let voices = 0
    let models = 0
    let locals = 0
    const skipped = new Set<string>()
    try {
      while (this.lazyToday() < this.revoiceDaily) {
        // a clip being re-recorded on request right now is not taken up again
        const c = this.lazyQueue().find(q => !skipped.has(q.key) && !this.running.has(q.key) && !this.running.has(clipKey(q.norm, this.slVoiceOf(q.voice) ?? q.voice)))
        if (!c) break
        // an old-style word in its carrier sentence (in the voice and model now); a Slovene narrator's line in its Slovene
        // ElevenLabs voice; a swapped voice's or an older model's clip again in the voice and model now
        // a character's local clip in the speaker's ElevenLabs voice
        const stale = this.oldStyle(c) ? undefined : this.stale(c)
        const local = !stale && this.wantsElevenLabsVoice(c)
        const r = this.oldStyle(c) ? await this.revoiceOne(c) : stale || local ? await this.remake(c, true) : await this.upgradeOne(c)
        if (r === 'stop') break
        if (r === 'skip') skipped.add(c.key)
        if (r === 'made') {
          if (this.oldStyle(c)) words++
          else if (stale === 'voice') voices++
          else if (stale === 'model') models++
          else if (local) locals++
          else narrators++
        }
        if (this.o.queueDelayMs !== 0) await Bun.sleep(this.o.queueDelayMs ?? 1500)
      }
    } catch (e) {
      this.o.log?.(`voice: re-voicing failed: ${(e as Error).message}`)
    } finally {
      this.revoicing = false
      if (words) this.o.log?.(`voice: ${words} short word(s) re-voiced in their carrier sentence (${this.lazyToday()}/${this.revoiceDaily} today)`)
      if (narrators) this.o.log?.(`voice: ${narrators} narrator line(s) made in their Slovene ElevenLabs voice, the local clips kept (${this.lazyToday()}/${this.revoiceDaily} today)`)
      if (voices) this.o.log?.(`voice: ${voices} clip(s) of a swapped voice made again in the voice now (${this.lazyToday()}/${this.revoiceDaily} today)`)
      if (models) this.o.log?.(`voice: ${models} clip(s) of an older model made again with ${this.model} (${this.lazyToday()}/${this.revoiceDaily} today)`)
      if (locals) this.o.log?.(`voice: ${locals} character line(s) made in their ElevenLabs voice, the local clips replaced (${this.lazyToday()}/${this.revoiceDaily} today)`)
      if (words + narrators + voices + models + locals) this.o.onUpdated?.(words + narrators + voices + models + locals)
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

  // --- a newer ElevenLabs model: the clips made with an older one, again in the current one (voice-build) ---------------

  /** The ElevenLabs model new clips are made with ([ElevenLabs.model]: LANI_ELEVENLABS_MODEL), when the store has ElevenLabs. */
  get model(): string | undefined {
    return this.eleven?.model
  }

  /** Whether the store's clips have column [name] (a store from before it, only read, hasn't). */
  private hasColumn(name: string): boolean {
    return (this.db.query('PRAGMA table_info(clips)').all() as { name: string }[]).some(c => c.name === name)
  }

  /** The ElevenLabs clips by the model that made them ([modelOf]: one from before it was recorded, eleven_v3), with their characters. */
  modelStats(): Record<string, { clips: number; chars: number }> {
    const m = this.hasColumn('model') ? `COALESCE(model, '${OLD_MODEL}')` : `'${OLD_MODEL}'`
    const rows = this.db.query(`SELECT ${m} AS model, COUNT(*) AS n, SUM(chars) AS chars FROM clips WHERE engine = 'elevenlabs' GROUP BY 1`).all() as { model: string; n: number; chars: number }[]
    return Object.fromEntries(rows.map(r => [r.model, { clips: r.n, chars: r.chars ?? 0 }]))
  }

  /**
   * The ElevenLabs clips made with another model than the one new clips are made with ([model]; one from before it was
   * recorded: eleven_v3), most played first (hits), then the oldest: what voice-build --upgrade-model makes again. Never a
   * local (Gepard) clip, nor a Slovene narrator's from before in its English library voice (never asked for again); the
   * family's recordings aren't clips of the store.
   */
  modelQueue(): Clip[] {
    const now = this.model
    if (!now) return []
    return (this.db.query("SELECT * FROM clips WHERE engine = 'elevenlabs'").all() as Clip[])
      .filter(c => modelOf(c) !== now && !this.sloveneNarrator(c.voice))
      .sort((a, b) => (b.hits ?? 0) - (a.hits ?? 0) || a.created_at.localeCompare(b.created_at))
  }

  /**
   * [c] made again by the current model, in its voice (a short word in its carrier sentence, an explanation plainly in
   * its language, as it was made), stored as every new clip is (its voice's filter, the edges, the level) under a new file
   * name, its hits kept, the old file removed: 'made'; 'skip' for a voice ElevenLabs no longer has (not in the cast now);
   * 'stop' when it can't now (the quota down to the reserve, kept for live lines; an error; a read-only store).
   */
  async upgradeModelOne(c: Clip): Promise<'made' | 'skip' | 'stop'> {
    return this.remake(c, false)
  }

  /**
   * voice-build --upgrade-model --run: the clips of [modelQueue] made again by the current model, most played first, at
   * most [max] clips and [maxChars] characters, within the reserve; stops when ElevenLabs can't now. A running bridge
   * tells the app (voice_updated), as for the other re-voices.
   */
  async upgradeModel(o: { max?: number; maxChars?: number; out?: (line: string) => void } = {}) {
    let made = 0
    let chars = 0
    let skipped = 0
    let stopped: string | undefined
    const list = this.modelQueue()
    for (const c of list) {
      if (o.max !== undefined && made >= o.max) break
      const cost = this.eleven?.cost(c.text, c.voice, explanationLang(c.norm)) ?? c.text.length
      if (o.maxChars !== undefined && chars + cost > o.maxChars) continue // a shorter one may still fit
      const from = modelOf(c)
      const r = await this.upgradeModelOne(c)
      if (r === 'skip') {
        skipped++
        o.out?.(`  · skipped  ${c.voice.padEnd(11)} ${c.text} (no ElevenLabs voice for it now)`)
        continue
      }
      if (r === 'stop') {
        stopped = 'ElevenLabs can\'t now (the quota at its reserve, or an error)'
        break
      }
      made++
      chars += cost
      o.out?.(`  ✓ ${from} → ${this.model} ${c.voice.padEnd(11)} ${c.text}`)
    }
    return { made, chars, skipped, left: list.length - made - skipped, ...(stopped ? { stopped } : {}) }
  }

  /**
   * The local clips of [speakers] (Gepard's, made while ElevenLabs couldn't: the quota, an error, the bridge's fallback;
   * and Drama 3's, voice-build --drama3), most played first, then the oldest: what voice-build --elevenlabs makes in the
   * speaker's ElevenLabs voice (ElevenLabs ranks above both, ENGINE_RANK). Never a narrator's (female, male): theirs
   * stay beside their Slovene ElevenLabs clips as the fallback (--narrators); never a speaker without an ElevenLabs voice.
   */
  localQueue(speakers: VoiceName[]): Clip[] {
    const names = speakers.filter(v => !isNarrator(v) && !!elevenlabsOf(v))
    if (!names.length) return []
    return (this.db.query(`SELECT * FROM clips WHERE engine IN ('gepard', 'drama3') AND voice IN (${names.map(() => '?').join(', ')})`).all(...names) as Clip[])
      .sort((a, b) => (b.hits ?? 0) - (a.hits ?? 0) || a.created_at.localeCompare(b.created_at))
  }

  /**
   * voice-build --elevenlabs S --run: the clips of [localQueue] made again in their speaker's ElevenLabs voice ([remake]:
   * filtered, trimmed and levelled as every new clip, a new file name, the hits kept, the local file removed), most played
   * first, at most [max] clips and [maxChars] characters, within the reserve; stops when ElevenLabs can't now. A running
   * bridge tells the app (voice_updated), as for the other re-voices.
   */
  async elevenlabsFor(speakers: VoiceName[], o: { max?: number; maxChars?: number; out?: (line: string) => void } = {}) {
    let made = 0
    let chars = 0
    let skipped = 0
    let stopped: string | undefined
    const list = this.localQueue(speakers)
    for (const c of list) {
      if (o.max !== undefined && made >= o.max) break
      const cost = this.eleven?.cost(c.text, c.voice, explanationLang(c.norm)) ?? c.text.length
      if (o.maxChars !== undefined && chars + cost > o.maxChars) continue // a shorter one may still fit
      const r = await this.remake(c, false)
      if (r === 'skip') {
        skipped++
        o.out?.(`  · skipped  ${c.voice.padEnd(11)} ${c.text} (no ElevenLabs voice for it now)`)
        continue
      }
      if (r === 'stop') {
        stopped = 'ElevenLabs can\'t now (the quota at its reserve, or an error)'
        break
      }
      made++
      chars += cost
      o.out?.(`  ✓ ${c.engine} → elevenlabs ${c.voice.padEnd(11)} ${c.text}`)
    }
    return { made, chars, skipped, left: list.length - made - skipped, ...(stopped ? { stopped } : {}) }
  }

  // --- Drama 3: a speaker's local clips made again by the Drama 3 worker (voice-build --drama3) -----------------------

  /**
   * The Gepard clips of [speakers] that voice-build --drama3 makes again with Drama 3, most played first, then the oldest.
   * Never a narrator's (female, male: their local clips are the fallback of their Slovene ElevenLabs voices, and the
   * narrators' local voices stay Gepard's); never an explanation (Drama 3 speaks the village's language here); never a
   * line Drama 3 could not make before ([drama3Failed]) unless [retry].
   */
  drama3Queue(speakers: VoiceName[], retry = false): Clip[] {
    const names = speakers.filter(v => !isNarrator(v) && isVoice(v))
    if (!names.length) return []
    const failed = retry ? new Set<string>() : new Set(this.drama3Failed().map(f => f.key))
    return (this.db.query(`SELECT * FROM clips WHERE engine = 'gepard' AND voice IN (${names.map(() => '?').join(', ')})`).all(...names) as Clip[])
      .filter(c => !explanationLang(c.norm) && !failed.has(c.key))
      .sort((a, b) => (b.hits ?? 0) - (a.hits ?? 0) || a.created_at.localeCompare(b.created_at))
  }

  /** The lines Drama 3 could not make (no take passed its checks), and why; of [speakers] when given. */
  drama3Failed(speakers?: VoiceName[]): { key: string; voice: VoiceName; text: string; reasons: string[]; at: string; tries: number }[] {
    let rows: { key: string; voice: string; text: string; reasons: string; at: string; tries: number }[]
    try {
      rows = this.db.query('SELECT * FROM drama3_failed ORDER BY at').all() as typeof rows
    } catch {
      return [] // a store from before, only read
    }
    return rows
      .filter(r => !speakers || speakers.includes(r.voice))
      .map(r => ({ ...r, reasons: (() => { try { return JSON.parse(r.reasons) as string[] } catch { return [r.reasons] } })() }))
  }

  /**
   * voice-build --drama3 S --run: each clip of [drama3Queue] made again by [engine], [concurrency] at a time (the worker
   * takes two: one on the GPU while the other is checked), most played first, at most [max]. A take is stored as every new
   * clip is (its edges trimmed, brought to the level; no filter, no pace) under a new file name, its hits kept, the Gepard
   * file removed, its row marked drama3 with the worker's voice ("grandma/grandma-2"); a running bridge tells the app
   * within a minute (voice_updated). A line no take passed (422) is recorded with the reasons ([drama3Failed]) and keeps
   * its Gepard clip; a clip that changed meanwhile (the bridge made an ElevenLabs one, a re-record) is left as it is.
   * [before] runs before each clip: false stops the batch (the memory pause gave up, a signal). Stops when the worker
   * can't be reached; what is left is taken up by the next run (the queue is what is still Gepard's).
   */
  async drama3For(engine: Drama3, speakers: VoiceName[], o: { max?: number; retry?: boolean; concurrency?: number; before?: () => Promise<boolean>; out?: (line: string) => void } = {}) {
    let made = 0
    let failed = 0
    let changed = 0
    let stopped: string | undefined
    const list = this.drama3Queue(speakers, o.retry)
    const todo = o.max !== undefined ? list.slice(0, o.max) : list
    let next = 0
    const one = async (c: Clip) => {
      if (this.running.has(c.key)) return
      let why: Drama3Rejection | undefined
      const job = (async (): Promise<Clip | null> => {
        const { take, rejection } = await engine.make(c.text, c.voice)
        why = rejection
        if (!take) return null
        // made meanwhile by another engine, or re-recorded: that one stays
        const now = this.get(c.norm, c.voice)
        if (!now || now.file !== c.file || !outranks('drama3', now)) {
          changed++
          o.out?.(`  · changed  ${c.voice.padEnd(11)} ${c.text} (left as it is now)`)
          return null
        }
        return await this.save(c.norm, c.text, c.voice, 'drama3', take.bytes, c.source, take)
      })()
      this.running.set(c.key, job.catch(() => null).finally(() => this.running.delete(c.key)))
      const clip = await job.catch(() => null)
      if (clip) {
        made++
        try {
          this.db.query('DELETE FROM drama3_failed WHERE key = ?').run(c.key)
        } catch {}
        o.out?.(`  ✓ gepard → drama3 ${c.voice.padEnd(11)} ${c.text}`)
        return
      }
      if (why?.status === 422) {
        failed++
        const reasons = (why.attempts ?? []).map(a => a.reasons.join('; ')).filter(Boolean)
        this.db
          .query('INSERT INTO drama3_failed (key, voice, text, reasons, at, tries) VALUES (?, ?, ?, ?, ?, 1) ON CONFLICT (key) DO UPDATE SET reasons = excluded.reasons, at = excluded.at, tries = tries + 1')
          .run(c.key, c.voice, c.text, JSON.stringify(reasons.length ? reasons : [why.error]), new Date().toISOString())
        o.out?.(`  ✗ failed   ${c.voice.padEnd(11)} ${c.text} (${reasons.at(-1) ?? why.error}; its Gepard clip stays)`)
      } else if (why) {
        stopped ??= `the Drama 3 worker: ${why.error}`
      }
    }
    const lane = async () => {
      while (!stopped && next < todo.length) {
        if (o.before && !(await o.before())) {
          stopped ??= 'stopped'
          break
        }
        if (stopped) break
        await one(todo[next++])
      }
    }
    await Promise.all(Array.from({ length: Math.max(1, o.concurrency ?? 2) }, lane))
    if (made) this.o.onUpdated?.(made)
    return { made, failed, changed, left: this.drama3Queue(speakers, o.retry).length, ...(stopped ? { stopped } : {}) }
  }

  /**
   * The short narrator clips: said in their carrier sentence, kept plain, old-style (and of those, asked for by the
   * app), the characters re-voicing the old-style ones would send, today's re-voices of the lazy re-voice's daily cap,
   * today's re-records on request of theirs, and whether the node can cut (ffmpeg).
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
      today: this.lazyToday(),
      daily: this.revoiceDaily,
      redos: this.redosToday(),
      redo_daily: this.redoDaily,
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
    if (!this.denoise) return { done: 0, kept: 0, limited: 0, trimmed: 0, skipped: 0, failed: 0, left: list.length }
    return this.processClips(list, 'filtered (denoise)', o)
  }

  /**
   * Each clip of [list] (at most [max]; a few at a time) through what it waits for, in one encode: its voice's denoise
   * filter (an ElevenLabs clip of a voice the cast marks, not filtered yet), its pace ([paceDue]: its voice's tempo, a
   * local clip's pace when one is set; not paced at it yet: never paced, or by another factor, which is sped up by the
   * difference from the file as it is now), its edges (not trimmed yet: EDGES; a clip with no more than EDGES.slack of
   * silence at either edge and nothing else to do keeps its file) and the level (not at the level's target: never levelled, or levelled to another
   * target, which is re-levelled from the file as it is now; a paced or trimmed clip is levelled again with it). Written
   * under a new file name (the app caches by name), recorded, and only then the old file removed; a clip kept as it is
   * keeps its file and is only recorded. A clip the bridge made again meanwhile (a re-record) keeps that take. The app
   * is told ([onUpdated]; when this runs in voice-build, the bridge's [watch] tells it).
   */
  private async processClips(list: Clip[], what: string, o: { max?: number; out?: (line: string) => void }): Promise<Processed> {
    const r: Processed = { done: 0, kept: 0, limited: 0, trimmed: 0, skipped: 0, failed: 0, left: list.length }
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
      // its pace, when it isn't at it (its voice's tempo, a local clip's pace): what the clip is sped up by now (the factor,
      // or what is left of it after another one)
      const pace = lv ? this.paceDue(c) : undefined
      const edges = !!lv && c.edges == null
      // a single word asked for as it is ends at its first long pause (EDGES.wordGap)
      const word = edges && c.method !== 'carrier' && !explanationLang(c.norm) && isOneWord(c.text)
      const toLevel = !!lv && (c.level !== lv.target || !!pace || edges)
      // at the level already, only its edges due: it keeps its loudness, and its file unless it is trimmed
      const levelledAlready = !!lv && c.level === lv.target && !pace && !preset
      const bytes: Uint8Array = new Uint8Array(readFileSync(src))
      let out = bytes
      let levelled: Levelled | null = null
      if (toLevel && (!preset || this.o.denoise === undefined)) {
        // ffmpeg's own filter, the pace, the edges and the level: one encode
        levelled = await lv!.level(bytes, { denoise: preset, pace, edges, word, levelled: levelledAlready })
        if (!levelled) {
          r.failed++
          return line('✗ failed')
        }
        out = levelled.bytes
      } else {
        // another filter (a stand-in): the filter, then the pace, the edges and the level
        const d = preset ? await f?.(bytes, preset).catch(() => null) : bytes
        if (!d) {
          r.failed++
          return line('✗ failed')
        }
        out = d
        levelled = toLevel ? await lv!.level(d, { pace, edges, word, levelled: levelledAlready }) : null
        if ((pace || edges) && !levelled) {
          r.failed++
          return line('✗ failed')
        }
        if (levelled) out = levelled.bytes
      }
      const denoise = preset ?? c.denoise ?? null
      const level = levelled ? lv!.target : (c.level ?? null)
      const gain = levelled ? Math.round(((c.gain ?? 0) + levelled.gain) * 100) / 100 : (c.gain ?? null)
      const paced = pace ? (clipPace(c.voice, c.engine, this.pace)?.tempo ?? null) : (c.tempo ?? null)
      const trimmed = !!levelled?.trimmed
      const edged = levelled?.edges ? 1 : (c.edges ?? null)
      const secs = (b: Uint8Array) => mp3Duration(b).toFixed(1)
      const e = levelled?.edges
      const how = `${preset ? `${preset} ` : ''}${pace ? `×${paced ?? 1}${pace.pause ? ` pauses ≤${pace.pause} s` : ''} ${secs(bytes)}→${secs(out)} s ` : ''}${trimmed && e ? `edges ${e.lead.toFixed(2)}+${e.tail.toFixed(2)} s ` : ''}${levelled ? (out === bytes ? `= kept (${levelled.before.lufs.toFixed(1)} LUFS)` : `${levelled.before.lufs.toFixed(1)} ${levelled.gain >= 0 ? '+' : ''}${levelled.gain.toFixed(1)} dB${levelled.limited ? ' limited' : ''}`) : ''}`
      // kept as it is: only recorded, while it is still the clip that was measured
      if (out === bytes) {
        if (!this.db.query('UPDATE clips SET level = ?, gain = ?, edges = ? WHERE key = ? AND file = ?').run(level, gain, edged, c.key, c.file).changes) {
          r.skipped++
          return line('· changed')
        }
        r.done++
        r.kept++
        return line(`✓ ${how}`)
      }
      const file = processedFile(c.file, { denoise: preset, level: levelled ? level : null, tempo: pace ? paced : null, edges: trimmed })
      const p = join(this.filesDir, file)
      writeFileSync(`${p}.tmp`, out)
      renameSync(`${p}.tmp`, p)
      // only while it is still the clip that was processed
      if (!this.db.query('UPDATE clips SET file = ?, denoise = ?, level = ?, gain = ?, tempo = ?, edges = ? WHERE key = ? AND file = ?').run(file, denoise, level, gain, paced, edged, c.key, c.file).changes) {
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
      if (trimmed) r.trimmed++
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
    if (!this.leveller) return { done: 0, kept: 0, limited: 0, trimmed: 0, skipped: 0, failed: 0, left: list.length }
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

  // --- the silence at the edges: the clips from before it ---------------------------------------------------------

  /** The clips whose edges wait (EDGES): every engine's, not trimmed nor found short enough yet; oldest first. */
  edgesQueue(): Clip[] {
    // a store from before the edges has no edges column: every clip waits
    return (this.db.query('SELECT * FROM clips ORDER BY created_at').all() as Clip[]).filter(c => c.edges == null)
  }

  /**
   * voice-build --edges --run: each clip whose edges wait ([edgesQueue], at most [max]): measured, and where it has more
   * than EDGES.slack of silence before or after its sound, trimmed to EDGES and levelled again in one encode, under a new
   * file name; the others only recorded ([processClips]). A voice's filter or the local voice's pace that is due runs in
   * the same encode. No engine is asked, no characters.
   */
  async edgesClips(o: { max?: number; out?: (line: string) => void } = {}): Promise<Processed> {
    const list = this.edgesQueue()
    if (!this.leveller) return { done: 0, kept: 0, limited: 0, trimmed: 0, skipped: 0, failed: 0, left: list.length }
    return this.processClips(list, 'through the edges', o)
  }

  /** The edges: what they are trimmed to, the clips trimmed or found short enough and waiting, and whether the node can (ffmpeg). */
  edgesStats() {
    let done = 0
    try {
      done = (this.db.query('SELECT COUNT(*) AS n FROM clips WHERE edges IS NOT NULL').get() as { n: number }).n
    } catch {} // a store from before the edges, only read: none yet
    return { lead: EDGES.lead, tail: EDGES.tail, threshold: EDGES.threshold, done, waiting: this.edgesQueue().length, ffmpeg: !!this.leveller }
  }

  // --- the pace: a voice's own (the cast's tempo), the local voice's when one is set; the clips from before it -------

  /**
   * The pace clip [c] waits for, from its file as it is now: the factor from the tempo it was paced at (its row's tempo)
   * to the one it should be at (clipPace: its voice's tempo by its engine, times the local voice's pace for a local
   * clip), with the local voice's longest pause; undefined when it is at its pace. A local clip paced before while no pace
   * is set now, its voice without a tempo of its own there, waits for --restore-gepard instead (not slowed down by another
   * encode).
   */
  paceDue(c: Clip): GepardPace | undefined {
    const want = clipPace(c.voice, c.engine, this.pace)
    const target = want?.tempo ?? null
    const has = c.tempo ?? null
    if (has === target) return undefined
    if (c.engine === 'gepard' && !this.pace && has !== null && tempoOf(c.voice, 'gepard') === 1) return undefined
    return { tempo: Math.round(((target ?? 1) / (has ?? 1)) * 10_000) / 10_000, pause: want?.pause ?? null }
  }

  /**
   * The clips waiting for their pace ([paceDue]): of a voice the cast gives a tempo (or no longer gives one), a local
   * clip while the local voice's pace is set; never paced, or paced by another factor; oldest first. None while no voice
   * has a tempo and no pace is set (the default: the engines' own; local clips paced before go back with
   * --restore-gepard).
   */
  tempoQueue(): Clip[] {
    // a store from before the pace has no tempo column: every clip of a paced voice waits
    return (this.db.query('SELECT * FROM clips ORDER BY created_at').all() as Clip[]).filter(c => !!this.paceDue(c))
  }

  /**
   * voice-build --pace (--tempo) --run: each clip waiting for its pace ([tempoQueue], at most [max]): a local clip's pauses
   * shortened, sped up, its edges trimmed and levelled again, in one encode ([processClips]). No engine is asked, no
   * characters.
   */
  async tempoClips(o: { max?: number; out?: (line: string) => void } = {}): Promise<Processed> {
    const list = this.tempoQueue()
    if (!this.leveller) return { done: 0, kept: 0, limited: 0, trimmed: 0, skipped: 0, failed: 0, left: list.length }
    return this.processClips(list, 'paced', o)
  }

  /**
   * The pace: the local voice's factor and longest pause (null: the worker's own), the local clips it paced (at any
   * factor: paced before, they wait for --restore-gepard while none is set), the voices with a tempo of their own (the cast's)
   * and the clips at it, the clips waiting, and whether the node can (ffmpeg).
   */
  tempoStats() {
    let paced = 0
    let voiced = 0
    const voices = tempoVoices()
    try {
      // paced by the local voice's pace: not only at their voice's own tempo
      paced = (this.db.query("SELECT voice, tempo FROM clips WHERE engine = 'gepard' AND tempo IS NOT NULL").all() as Clip[]).filter(c => c.tempo !== (clipPace(c.voice, 'gepard', null)?.tempo ?? null)).length
      const names = Object.keys(voices)
      if (names.length) voiced = (this.db.query(`SELECT COUNT(*) AS n FROM clips WHERE tempo IS NOT NULL AND voice IN (${names.map(() => '?').join(', ')})`).get(...names) as { n: number }).n
    } catch {} // a store from before the pace, only read: none yet
    const pace = this.pace
    return { tempo: pace?.tempo ?? null, pause: pace?.pause ?? null, paced, voices, voiced, waiting: this.tempoQueue().length, ffmpeg: !!this.leveller }
  }

  /**
   * voice-build --restore-gepard: the local (Gepard) clips paced before (their tempo set) back to their files and rows in
   * [snap], the voice store of a snapshot taken before they were paced (lani-backup's voice/: voice.db and files/; its
   * database is read from a copy, so the snapshot is never written to). A clip whose snapshot row is the same text by the
   * local worker, unpaced, with its file there: the file copied into files/ (under a temporary name, then renamed; its
   * bytes checked against the snapshot's), the row pointed at it with the snapshot's level, gain and filter, its pace,
   * edges and engine voice cleared (as before: the edge trim comes after), only while it is still the paced file; then
   * the paced file removed, once no row points at it. At most [max]; [run] false: what it would do, nothing written.
   */
  restorePaced(snap: string, o: { run?: boolean; max?: number; out?: (line: string) => void } = {}): Restored {
    const copy = join(tmpdir(), `lani-restore-${randomUUID()}.db`)
    copyFileSync(join(snap, 'voice.db'), copy)
    const before = new Database(copy)
    const r: Restored = { paced: 0, restored: 0, missing: [], verified: 0, skipped: 0, failed: 0, left: 0 }
    // paced by the local voice's pace (its tempo set), not only by its voice's own tempo
    const byPace = (c: Clip) => c.tempo != null && c.tempo !== (clipPace(c.voice, 'gepard', null)?.tempo ?? null)
    try {
      const paced = (this.db.query("SELECT * FROM clips WHERE engine = 'gepard' ORDER BY created_at").all() as Clip[]).filter(byPace)
      r.paced = paced.length
      const was = before.query('SELECT * FROM clips WHERE key = ?')
      const line = (how: string, c: Clip) => o.out?.(`  ${how.padEnd(34)} ${c.voice.padEnd(10)} ${c.text.length > 56 ? `${c.text.slice(0, 55)}…` : c.text}`)
      for (const c of paced) {
        const w = was.get(c.key) as Clip | null
        const why = !w ? 'not in the snapshot'
          : w.engine !== 'gepard' ? `by ${w.engine} in the snapshot`
          : w.text !== c.text ? 'another text in the snapshot'
          : w.tempo != null ? 'paced in the snapshot too'
          : !FILE.test(w.file) || !existsSync(join(snap, 'files', w.file)) ? 'its file is not in the snapshot'
          : undefined
        if (why) {
          r.missing.push({ key: c.key, why })
          line(`· ${why}`, c)
          continue
        }
        if (o.max !== undefined && r.restored + r.failed + r.skipped >= o.max) break
        if (!o.run) {
          r.restored++
          line(`→ ${w!.file.slice(0, 12)}… (${w!.level ?? '—'} LUFS)`, c)
          continue
        }
        const from = join(snap, 'files', w!.file)
        const to = join(this.filesDir, w!.file)
        const want = readFileSync(from)
        let made = false
        try {
          if (!existsSync(to)) {
            writeFileSync(`${to}.tmp`, want)
            renameSync(`${to}.tmp`, to)
            made = true
          }
          if (!Buffer.from(readFileSync(to)).equals(want)) {
            r.failed++
            line('✗ another file of its name here', c)
            continue
          }
        } catch (e) {
          r.failed++
          line(`✗ ${(e as Error).message.slice(0, 32)}`, c)
          continue
        }
        // only while it is still the paced clip
        const changed = this.db
          .query('UPDATE clips SET file = ?, level = ?, gain = ?, denoise = ?, tempo = NULL, edges = NULL, engine_voice = NULL WHERE key = ? AND file = ?')
          .run(w!.file, w!.level ?? null, w!.gain ?? null, w!.denoise ?? null, c.key, c.file).changes
        if (!changed) {
          if (made) {
            try {
              unlinkSync(to)
            } catch {}
          }
          r.skipped++
          line('· changed meanwhile', c)
          continue
        }
        this.seen?.files.set(c.key, w!.file)
        if (c.file !== w!.file && !this.db.query('SELECT 1 FROM clips WHERE file = ?').get(c.file)) {
          try {
            unlinkSync(join(this.filesDir, c.file))
          } catch {}
        }
        r.restored++
        if (Buffer.from(readFileSync(to)).equals(want)) r.verified++
        line(`✓ ${mp3Duration(new Uint8Array(want)).toFixed(1)} s, as before`, c)
      }
      r.left = o.run ? (this.db.query("SELECT * FROM clips WHERE engine = 'gepard'").all() as Clip[]).filter(byPace).length : paced.length
    } finally {
      before.close()
      for (const f of [copy, `${copy}-wal`, `${copy}-shm`]) {
        try {
          unlinkSync(f)
        } catch {}
      }
    }
    if (o.run && r.restored) this.o.onUpdated?.(r.restored)
    return r
  }

  // --- the narrators in the local voice: the clips from before -------------------------------------------------------

  /**
   * The Slovene narrators' clips in the narrator's key not in their local voice ([inLocalVoice]: ElevenLabs' English
   * narrators; the female narrator's lone words in nina or sentences in ana), where the narrators speak Slovene
   * ([sloveneNarrator]); most asked for first, then the oldest. [slovene] tells the clips in the village's Slovene (by the
   * corpus or source they came from) from the others, which are left as they are. The clips in the narrators' Slovene
   * ElevenLabs voices ("female.sl") are rows of their own, never in it; so are the explanations in the learner's own
   * language ([explanationLang]), which the local voice can't say.
   */
  narratorQueue(slovene: (c: Clip) => boolean): { todo: Clip[]; skipped: Clip[] } {
    if (!this.o.narrators) return { todo: [], skipped: [] }
    const rows = (this.db.query("SELECT * FROM clips WHERE voice IN ('female', 'male')").all() as Clip[])
      .filter(c => !explanationLang(c.norm) && this.sloveneNarrator(c.voice) && !this.inLocalVoice(c))
      .sort((a, b) => (b.hits ?? 0) - (a.hits ?? 0) || a.created_at.localeCompare(b.created_at))
    return { todo: rows.filter(slovene), skipped: rows.filter(c => !slovene(c)) }
  }

  /**
   * voice-build --narrators --run: the narrators' Slovene clips ([narratorQueue], at most [max]) made again in their local
   * voice, one at a time (the worker makes one at a time, about a second each), each under a new file name, the old file
   * removed once the new one is recorded, its edges trimmed and levelled as every new clip. A busy worker is waited for
   * (a few tries); a worker that is down stops the run, and the next run goes on where it stopped. No ElevenLabs.
   */
  async revoiceNarrators(slovene: (c: Clip) => boolean, o: { max?: number; out?: (line: string) => void; waitMs?: number } = {}): Promise<NarratorsRevoiced> {
    const { todo } = this.narratorQueue(slovene)
    const r: NarratorsRevoiced = { made: 0, failed: 0, skipped: 0, left: todo.length }
    const worker = this.gepard
    if (this.o.readonly || !worker) return { ...r, stopped: worker ? undefined : 'no local worker' }
    const line = (how: string, c: Clip) => o.out?.(`  ${how.padEnd(18)} ${c.voice.padEnd(7)} ${c.text.length > 64 ? `${c.text.slice(0, 63)}…` : c.text}`)
    let failedInARow = 0
    for (const c of todo.slice(0, o.max ?? todo.length)) {
      if (!(await worker.up())) {
        r.stopped = 'the local worker is down'
        break
      }
      let made: Clip | null = null
      for (let attempt = 0; attempt < 4 && !made; attempt++) {
        if (attempt) await Bun.sleep(o.waitMs ?? 1500) // busy: the bridge may be asking it too
        const now = this.get(c.norm, c.voice)
        if (!now || now.file !== c.file) break // made again meanwhile
        const got = await this.voice(c.text, c.voice, c.source, { engines: ['gepard'], force: true })
        if (got && got.file !== c.file && got.engine === 'gepard') made = got
      }
      const now = this.get(c.norm, c.voice)
      if (made) {
        r.made++
        failedInARow = 0
        line(`✓ ${made.engine_voice ?? 'gepard'}${made.edges ? '' : ' (untrimmed)'}`, made)
      } else if (!now || now.file !== c.file) {
        r.skipped++
        line('· changed', c)
      } else {
        r.failed++
        line('✗ failed', c)
        if (++failedInARow >= 10) {
          r.stopped = '10 failed in a row'
          break
        }
      }
    }
    r.left = this.narratorQueue(slovene).todo.length
    if (r.made) {
      this.o.log?.(`voice: ${r.made} narrator clip(s) made again in the local voice`)
      this.o.onUpdated?.(r.made)
    }
    return r
  }

  /**
   * The narrators here: how they speak in Slovene (the setting: 'elevenlabs' or 'gepard'; null: as the cast has them), each
   * one's Slovene ElevenLabs voice and local voices (for a lone word, for a sentence), their clips in the Slovene
   * ElevenLabs voices, their clips in the narrators' keys in the local voice and not, and the local clips whose ElevenLabs
   * one is still wanted (of them, asked for by the app: the lazy upgrade takes those up).
   */
  narratorStats() {
    const local = !!this.o.narrators
    const out: {
      local: boolean; mode: NarratorVoices | null
      voices: Record<string, string | undefined>; elevenlabs_voices: Record<string, string | undefined>; word_voices: Record<string, string | undefined>
      elevenlabs: number; in_local_voice: number; other: number; waiting: number; asked: number
    } = { local, mode: this.o.narrators ?? null, voices: {}, elevenlabs_voices: {}, word_voices: {}, elevenlabs: 0, in_local_voice: 0, other: 0, waiting: 0, asked: 0 }
    if (!local) return out
    for (const v of GENDERS) {
      out.voices[v] = this.localVoiceOf(v, 'Dober dan, kako ste?')
      out.word_voices[v] = this.localVoiceOf(v, 'kosilo')
      const sl = this.slVoiceOf(v)
      out.elevenlabs_voices[v] = sl ? (languageVoiceName(sl) ?? elevenlabsOf(sl)) : undefined
    }
    try {
      const sl = GENDERS.map(v => this.slVoiceOf(v)).filter((v): v is string => !!v)
      if (sl.length) out.elevenlabs = (this.db.query(`SELECT COUNT(*) AS n FROM clips WHERE voice IN (${sl.map(() => '?').join(', ')}) AND instr(norm, '|') = 0`).get(...sl) as { n: number }).n
      for (const c of this.db.query("SELECT * FROM clips WHERE voice IN ('female', 'male')").all() as Clip[]) {
        if (explanationLang(c.norm)) continue // the explanations: counted apart (status: explanations)
        if (this.inLocalVoice(c)) out.in_local_voice++
        else out.other++
        if (this.elevenlabsFirst(c.voice) && this.wantsElevenLabs(c.norm, c.voice)) {
          out.waiting++
          if ((c.hits ?? 0) > 0) out.asked++
        }
      }
    } catch {}
    return out
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

  /** The texts of [items] without a clip the app would get ([best]: a Slovene narrator's in either voice; an explanation under its own key). */
  missing(items: CorpusItem[]): CorpusItem[] {
    return items.filter(i => !this.best(this.normOf(i.text, i.lang), i.voice))
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
      const c = this.best(this.normOf(i.text, i.lang), i.voice)
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
    const queued = new Set(this.queue.map(i => clipKey(this.normOf(i.text, i.lang), i.voice)))
    for (const i of this.missing(items)) {
      const k = clipKey(this.normOf(i.text, i.lang), i.voice)
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
        const before = this.get(this.normOf(i.text, i.lang), i.voice)
        const c = await this.voice(i.text, i.voice, i.source, { lang: i.lang })
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

  /**
   * The explanations in the learner's own language ([explanationNorm]: a school intro's steps), apart from the lines: how
   * many clips, the characters they cost, and how many in each language.
   */
  explanationStats() {
    const rows = this.db.query("SELECT substr(norm, 1, 2) AS lang, COUNT(*) AS n, SUM(chars) AS chars FROM clips WHERE instr(norm, '|') = 3 GROUP BY lang").all() as { lang: string; n: number; chars: number }[]
    return {
      clips: rows.reduce((n, r) => n + r.n, 0),
      chars: rows.reduce((n, r) => n + (r.chars ?? 0), 0),
      by_lang: Object.fromEntries(rows.map(r => [r.lang, r.n])),
    }
  }

  async status(items?: CorpusItem[]) {
    // the lines in the village's language; the explanations in the learner's own are counted apart ([explanationStats])
    const clips = this.db.query("SELECT voice, engine, COUNT(*) AS n, SUM(chars) AS chars FROM clips WHERE instr(norm, '|') = 0 GROUP BY voice, engine").all() as {
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
      explanations: this.explanationStats(),
      // the ElevenLabs clips by the model that made them, and the model new ones are made with (voice-build --upgrade-model)
      models: { now: this.model ?? null, by: this.modelStats() },
      // the clips of a swapped voice and of an older model: all, those the app asked for (re-voiced lazily), the cost
      ...(this.eleven?.configured ? { stale: this.staleStats() } : {}),
      // the characters' local clips waiting for their speaker's ElevenLabs voice: all, those the app asked for, the cost
      ...(this.eleven?.configured ? { local_clips: this.localStats() } : {}),
      engines,
      cast: castInfo(),
      // who says the learner's own lines in a dialog, by their gender (the cast's `player`)
      player: playerVoices(),
      queued: this.queue.length,
      ...(this.eleven ? { carrier: this.carrierStats() } : {}),
      ...(Object.keys(denoiseVoices()).length ? { denoise: this.denoiseStats() } : {}),
      level: this.levelStats(),
      edges: this.edgesStats(),
      tempo: this.tempoStats(),
      narrators: this.narratorStats(),
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
app, which falls back to phone TTS. Some character clips the Gepard model made are made again in a batch by the local Drama 3 model
(companion/bin/voice-build --drama3, Jan's call; Built with Fish Audio): ElevenLabs' clips come first, then Drama 3's, then
Gepard's. Don't add audio files or TTS notes yourself; voice_status shows
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
Every clip's silence at its edges is trimmed when it is stored (0.05 s kept before the sound, 0.1 s after it), so a word
starts at once; voice_status shows the clips still waiting (edges), which companion/bin/voice-build --edges trims. The
local Gepard voice keeps its own pace.
In the Slovene village the narrators (female, male) speak with their native Slovene ElevenLabs voices (Nina, Luka), the
local Gepard voice as their fallback (ana for a lone word, nina for a sentence; marko), never with the English library
voices; a narrator's local clip stays when the ElevenLabs one is made, and the ElevenLabs ones are made as the app asks
for them, within the day's re-voice cap and above the reserve (voice_status: narrators). The villagers keep their voices:
a villager's line the local Gepard voice said while ElevenLabs couldn't is made in their speaker's ElevenLabs voice as the
app asks for it, within the same cap and above the reserve (voice_status: local_clips).
The learner has a voice too: their own lines in a dialog (a choice tapped, a turn typed right, an echo gone on from) are
said in it before the reply comes, the "player" of companion/voice-cast.json by the profile's learner.gender (in the
Slovene village Luka for a man, Nina for a woman, Nina while the profile says none; voice_status: player). A line the
learner said into the microphone isn't said again; their line's 🔊 plays it in their voice.
The school's guided introductions are read out too, in the teacher's voice: each step's explanation in the learner's own
language, the Slovene forms in it, one clip made by ElevenLabs as the app first asks for it (never from the reserve; the app
reads the step with the phone's voice and the Slovene clips meanwhile); voice_status counts them apart (explanations). The
Slovene the steps read (examples, quick questions and their answers, the forms) is voiced ahead like the other content.
`.trim()
