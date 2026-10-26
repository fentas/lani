// Voices on a visit (companion/README.md, "Voices on a visit"; docs/plans/05-built/visit-voices.md). A visited town
// speaks its own language in its own cast's voices, best first, each a fallback for the one before:
// 1. the host's own clips: a linked town asks the host for a line's clip by its text and voice, as the host's voice index
//    keys them (GET /town/voice, signed both ways); the host hands over a clip it has, never makes one;
// 2. the visitor's node voices it in the host's cast voice (GET /town/voice/cast: the host's speakers, the ElevenLabs
//    voice each speaks the town's language with, its local voice for a Slovene town, the learner's voice by gender),
//    with the visitor's own engines and their budget: ElevenLabs as a live line, then the local Gepard worker for a
//    Slovene town only; Drama 3 makes batch clips only, never a live line;
// 3. then the phone (its offline voice of the town's language, Android's text-to-speech, else a note that nothing can).
// The visitor keeps what it got in its own store, apart from its village's clips ([VisitClips]: <voice dir>/visits/), per
// town and voice. Only node:crypto, node:fs and fetch: the phone's bridge (QuickJS) runs this too (it passes the host's
// clips on; it has no engines of its own).
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, renameSync, statSync, unlinkSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { CAST_FILE, DENOISE_PRESETS, PERSON_VOICE, PLAYER, SPEAKER_ID, carrierVoice, cast, denoiseOf, elevenlabsOf, isVoice, languageVoice, playerVoice, tempoOf, type DenoisePreset, type Gender } from './cast'
import { CULTURE_ID, cultureDir } from './cultures'
import { normalizeText } from './family'
import type { ProfileView } from './profiles'
import { ELEVENLABS_ID, FILE, isMp3, isOneWord, isShortText, LINE_CHARS, type ElevenLabs, type Gepard, type Leveller, type VoiceStore } from './voice'

type Log = (...a: unknown[]) => void

/** The routes between towns: a line's clip, and the cast a visit is voiced in. */
export const VOICE_PATH = '/town/voice'
export const CAST_PATH = '/town/voice/cast'
/** The version of the clip's answer and of the cast's. */
export const VISIT_VOICE_VERSION = 1
/** The largest clip a town hands another: a line of LINE_CHARS at 128 kbit/s takes about 20 s, 320 KB. */
export const MAX_CLIP_BYTES = 768 * 1024
/** The largest answer with a clip taken (its base64 and the head). */
export const MAX_CLIP_ANSWER = Math.ceil((MAX_CLIP_BYTES * 4) / 3) + 4 * 1024
/** The largest cast answer taken. */
export const MAX_CAST_ANSWER = 256 * 1024
/** At most this many voices in one ask: the one wanted, then the ones it falls back to. */
export const MAX_ASKED = 4
/** At most this many speakers and people in a cast answer. */
const MAX_SPEAKERS = 40
const MAX_PEOPLE = 200

const LANGUAGE = /^[a-z]{2,3}$/
const WORKER_VOICE = /^[a-z0-9-]{1,32}$/
const PERSON_ID = /^[a-z0-9][a-z0-9-]{0,63}$/

/** A voice a visit may ask for: a speaker of a cast ("grandma"), someone's own voice ("@micka"), or the learner's ("player"). */
export const isAskedVoice = (v: unknown): v is string => typeof v === 'string' && (SPEAKER_ID.test(v) || PERSON_VOICE.test(v))

// --- the cast a visit is voiced in ---------------------------------------------------------------------------------

/**
 * A speaker of a town's cast as a visitor voices them: their gender, the ElevenLabs voice they speak the town's language
 * with (a narrator's voice in that language: Nina for the female narrator in Slovene), whether that voice says a lone
 * word inside a carrier sentence, the local worker's voices (a Slovene town only: the worker speaks nothing else), the
 * filter their ElevenLabs clips go through, and their pace by engine.
 */
export type CastSpeaker = {
  gender: Gender
  elevenlabs?: string
  carrier?: true
  gepard?: string
  gepard_words?: string
  denoise?: DenoisePreset
  tempo?: { elevenlabs?: number; gepard?: number }
}

/** How someone of a town is heard: their voice ("@micka", else their archetype "grandma") and their pitch and pace with it. */
export type PersonVoiceView = { speaker: string; archetype: string; pitch: number; rate: number }

/**
 * A town's cast for a visit (GET /town/voice/cast): its speakers ([CastSpeaker]), the voice its cast says a learner's own
 * lines in by their gender (`player`: a speaker, "male"), and how each of its people is heard ([PersonVoiceView]).
 * Nothing private: library voice ids, genders and pitches, no key, no account, no designed voice's id.
 */
export type VisitCast = {
  v: number
  town: string
  culture: string
  language: string
  speakers: Record<string, CastSpeaker>
  player: Record<Gender, string>
  people: Record<string, PersonVoiceView>
}

/** A pace factor from a cast: 0.5 to 2, rounded; undefined for 1 or none. */
const factor = (n: unknown): number | undefined => (typeof n === 'number' && Number.isFinite(n) && n >= 0.5 && n <= 2 && n !== 1 ? Math.round(n * 10_000) / 10_000 : undefined)

/** [t] without its empty parts: undefined when nothing is left. */
function paceOf(t: { elevenlabs?: number; gepard?: number }): CastSpeaker['tempo'] | undefined {
  const out = { ...(t.elevenlabs ? { elevenlabs: t.elevenlabs } : {}), ...(t.gepard ? { gepard: t.gepard } : {}) }
  return Object.keys(out).length ? out : undefined
}

/**
 * This town's cast as a visit is voiced in it (the host's side of GET /town/voice/cast): each speaker of the voice cast
 * (cast.ts) as it speaks [language], the village's, with the voice the cast gives it there; the learner's voice by gender
 * (the cast's `player`, as the store speaks it: "male.sl" is the male narrator's); [people]: the voice profiles' views.
 */
export function castForVisits(language: string, people: Record<string, ProfileView> = {}): Pick<VisitCast, 'speakers' | 'player' | 'people'> {
  const speakers: Record<string, CastSpeaker> = {}
  for (const [id, s] of Object.entries(cast())) {
    // a narrator's voice in the village's language ("female.sl": Nina), else its own
    const voice = languageVoice(id, language) ?? id
    const el = elevenlabsOf(voice)
    const denoise = denoiseOf(voice)
    const tempo = paceOf({ elevenlabs: factor(tempoOf(voice, 'elevenlabs')), gepard: factor(tempoOf(voice, 'gepard')) })
    speakers[id] = {
      gender: s.gender,
      ...(el ? { elevenlabs: el } : {}),
      ...(carrierVoice(voice) ? { carrier: true as const } : {}),
      // the local worker speaks Slovene only
      ...(language === 'sl' && s.gepard ? { gepard: s.gepard, ...(s.gepard_words ? { gepard_words: s.gepard_words } : {}) } : {}),
      ...(denoise ? { denoise } : {}),
      ...(tempo ? { tempo } : {}),
    }
  }
  const views: Record<string, PersonVoiceView> = {}
  for (const [id, p] of Object.entries(people).slice(0, MAX_PEOPLE)) views[id] = { speaker: p.speaker, archetype: p.archetype, pitch: p.pitch, rate: p.rate }
  return { speakers, player: { female: playerVoice('female'), male: playerVoice('male') }, people: views }
}

/** A cast speaker from another town, checked; undefined when it isn't one. */
function cleanSpeaker(s: any): CastSpeaker | undefined {
  if (!s || typeof s !== 'object' || (s.gender !== 'female' && s.gender !== 'male')) return undefined
  const tempo = s.tempo && typeof s.tempo === 'object' ? paceOf({ elevenlabs: factor(s.tempo.elevenlabs), gepard: factor(s.tempo.gepard) }) : undefined
  const gepard = typeof s.gepard === 'string' && WORKER_VOICE.test(s.gepard) ? s.gepard : undefined
  return {
    gender: s.gender,
    ...(typeof s.elevenlabs === 'string' && ELEVENLABS_ID.test(s.elevenlabs) ? { elevenlabs: s.elevenlabs } : {}),
    ...(s.carrier === true ? { carrier: true as const } : {}),
    ...(gepard ? { gepard, ...(typeof s.gepard_words === 'string' && WORKER_VOICE.test(s.gepard_words) ? { gepard_words: s.gepard_words } : {}) } : {}),
    ...((DENOISE_PRESETS as readonly unknown[]).includes(s.denoise) ? { denoise: s.denoise as DenoisePreset } : {}),
    ...(tempo ? { tempo } : {}),
  }
}

/** The learner's voice of a cast by gender, each a speaker of it (else that gender's narrator). */
function cleanPlayer(p: any, speakers: Record<string, CastSpeaker>): Record<Gender, string> {
  const of = (g: Gender) => (typeof p?.[g] === 'string' && Object.hasOwn(speakers, p[g]) ? (p[g] as string) : g)
  return { female: of('female'), male: of('male') }
}

/**
 * Another town's cast for a visit as this bridge takes it (GET /town/voice/cast): for town [id], every speaker and person
 * checked (ids, voice ids, the local voices' names, filters, paces, pitches); one that isn't valid is left out; a
 * Slovene town's local voices only (the worker speaks nothing else). Undefined when the answer isn't for town [id].
 */
export function cleanVisitCast(b: any, id: string): VisitCast | undefined {
  if (!b || typeof b !== 'object' || b.town !== id || typeof b.language !== 'string' || !LANGUAGE.test(b.language)) return undefined
  const speakers: Record<string, CastSpeaker> = {}
  for (const [k, s] of Object.entries(b.speakers && typeof b.speakers === 'object' ? b.speakers : {}).slice(0, MAX_SPEAKERS)) {
    const c = SPEAKER_ID.test(k) ? cleanSpeaker(s) : undefined
    if (!c) continue
    if (b.language !== 'sl') {
      delete c.gepard
      delete c.gepard_words
    }
    speakers[k] = c
  }
  const people: Record<string, PersonVoiceView> = {}
  const unit = (n: unknown) => (typeof n === 'number' && Number.isFinite(n) ? Math.max(0.8, Math.min(1.2, n)) : 1)
  for (const [k, p] of Object.entries(b.people && typeof b.people === 'object' ? b.people : {}).slice(0, MAX_PEOPLE) as [string, any][]) {
    if (!PERSON_ID.test(k) || !p || typeof p !== 'object' || !isAskedVoice(p.speaker) || typeof p.archetype !== 'string' || !SPEAKER_ID.test(p.archetype)) continue
    people[k] = { speaker: p.speaker, archetype: p.archetype, pitch: unit(p.pitch), rate: unit(p.rate) }
  }
  return {
    v: Number.isInteger(b.v) ? b.v : VISIT_VOICE_VERSION,
    town: id,
    culture: typeof b.culture === 'string' && CULTURE_ID.test(b.culture) ? b.culture : '',
    language: b.language,
    speakers,
    player: cleanPlayer(b.player, speakers),
    people,
  }
}

/**
 * A town's cast as this node's copy of its culture pack has it (its voice-cast.json; a Slovene pack without one: the
 * default cast, companion/voice-cast.json), for a town whose bridge is older than GET /town/voice/cast: read leniently,
 * as [castForVisits] gives it. Undefined when there is none.
 */
export function castFromPack(culturesDir: string, culture: string, language: string): Pick<VisitCast, 'speakers' | 'player'> | undefined {
  if (!CULTURE_ID.test(culture)) return undefined
  const own = join(cultureDir(culturesDir, culture), 'voice-cast.json')
  const path = existsSync(own) ? own : language === 'sl' ? CAST_FILE : undefined
  if (!path) return undefined
  let raw: any
  try {
    raw = JSON.parse(readFileSync(path, 'utf8'))
  } catch {
    return undefined
  }
  const denoise: Record<string, unknown> = raw?.denoise && typeof raw.denoise === 'object' ? raw.denoise : {}
  const presetOf = (voice: string) => (denoise[voice] === true ? DENOISE_PRESETS[0] : denoise[voice])
  const tempoOfSpec = (t: unknown) => (typeof t === 'number' ? { elevenlabs: factor(t), gepard: factor(t) } : t && typeof t === 'object' ? { elevenlabs: factor((t as any).elevenlabs), gepard: factor((t as any).gepard) } : {})
  const speakers: Record<string, CastSpeaker> = {}
  for (const [id, s] of Object.entries(raw?.speakers && typeof raw.speakers === 'object' ? raw.speakers : {}).slice(0, MAX_SPEAKERS) as [string, any][]) {
    if (!SPEAKER_ID.test(id) || !s || typeof s !== 'object') continue
    const lang = s.languages?.[language]
    const inLang = lang && typeof lang.elevenlabs === 'string' && lang.elevenlabs ? lang : undefined
    const c = cleanSpeaker({
      gender: s.gender,
      elevenlabs: inLang ? inLang.elevenlabs : s.elevenlabs,
      carrier: inLang ? inLang.carrier : s.carrier,
      ...(language === 'sl' ? { gepard: s.gepard, gepard_words: s.gepard_words } : {}),
      denoise: presetOf(inLang ? `${id}.${language}` : id),
      tempo: tempoOfSpec(inLang ? inLang.tempo : s.tempo),
    })
    if (c) speakers[id] = c
  }
  // the cast's `player`: a speaker, or one's voice in a language ("male.sl": the male narrator's)
  const base = (v: unknown) => (typeof v === 'string' ? v.replace(/\.[a-z]{2}$/, '') : undefined)
  return { speakers, player: cleanPlayer({ female: base(raw?.player?.female), male: base(raw?.player?.male) }, speakers) }
}

/**
 * The voices to try for a line asked in [asked] in town [cast], best first, as they are kept: the learner's own lines
 * ([PLAYER]) in the town's voice for the learner's [gender] and no other; someone with a voice of their own ("@micka") in
 * it, then their archetype's; anyone else in theirs. [fallback]: the voices a line falls back to when the wanted one's
 * can't be had (their gender's narrator, the other narrator), none for the learner's own.
 */
export function visitVoices(asked: string, cast: Pick<VisitCast, 'speakers' | 'player' | 'people'>, gender?: Gender): { wanted: string[]; spec?: CastSpeaker; speaker?: string; fallback: string[] } {
  if (asked === PLAYER) {
    const v = cast.player[gender ?? 'female']
    return { wanted: [v], spec: cast.speakers[v], speaker: v, fallback: [] }
  }
  const person = PERSON_VOICE.test(asked) ? Object.values(cast.people).find(p => p.speaker === asked) ?? { archetype: undefined } : undefined
  const speaker = person ? person.archetype : asked
  const spec = speaker ? cast.speakers[speaker] : undefined
  const g: Gender = spec?.gender ?? (asked === 'male' ? 'male' : 'female')
  const wanted = [...new Set([asked, ...(speaker ? [speaker] : [])])]
  const fallback = [g, g === 'female' ? 'male' : 'female'].filter(v => !wanted.includes(v))
  return { wanted, spec, speaker, fallback }
}

// --- the host's side: a clip it has ----------------------------------------------------------------------------------

/** What a town asks for (GET /town/voice?text=…&voice=…[&voice=…][&gender=…]): checked; why not when it isn't valid. */
export function parseVoiceAsk(url: URL): { text: string; voices: string[]; gender?: Gender } | { error: string } {
  const text = (url.searchParams.get('text') ?? '').trim()
  if (!text || text.length > LINE_CHARS || /[\u0000-\u001f\u007f]/.test(text)) return { error: `expected text: a line of 1 to ${LINE_CHARS} characters` }
  const voices = url.searchParams.getAll('voice')
  if (!voices.length || voices.length > MAX_ASKED || !voices.every(isAskedVoice)) return { error: `expected 1 to ${MAX_ASKED} voices (a speaker, someone's own "@id", or "player")` }
  const g = url.searchParams.get('gender')
  if (g !== null && g !== 'female' && g !== 'male') return { error: 'gender: female or male' }
  return { text, voices, ...(g ? { gender: g } : {}) }
}

/**
 * The clip this town has of [text] in the first of [voices] that has one (the host's side of GET /town/voice): the one
 * its own app gets ([VoiceStore.best]: a Slovene narrator's in its ElevenLabs voice or its local one), the learner's own
 * lines in the voice the cast says them in for a learner of [gender]. Only a clip there: nothing is made, asked for or
 * counted, and a file is only ever one of the store's own (its name from the store's row, read from its files). Undefined
 * when it has none.
 */
export function hostClip(store: Pick<VoiceStore, 'best' | 'path'>, o: { text: string; voices: string[]; gender?: Gender }): { voice: string; engine: string; bytes: Uint8Array } | undefined {
  const norm = normalizeText(o.text)
  if (!norm) return undefined
  for (const asked of o.voices) {
    const v = asked === PLAYER ? playerVoice(o.gender) : asked
    if (!isVoice(v)) continue
    const c = store.best(norm, v)
    if (!c || !FILE.test(c.file)) continue
    const p = store.path(c.file)
    if (!p) continue
    try {
      if (statSync(p).size > MAX_CLIP_BYTES) continue
      const bytes = new Uint8Array(readFileSync(p))
      if (isMp3(bytes)) return { voice: asked, engine: c.engine, bytes }
    } catch {}
  }
  return undefined
}

/** A clip's answer from another town (GET /town/voice), checked: for [voices], an MP3 within MAX_CLIP_BYTES; else undefined. */
export function cleanHostClip(b: any, id: string, voices: string[]): { voice: string; engine: string; bytes: Uint8Array } | undefined {
  if (!b || typeof b !== 'object' || b.town !== id || typeof b.voice !== 'string' || !voices.includes(b.voice) || typeof b.audio !== 'string') return undefined
  if (b.audio.length > MAX_CLIP_ANSWER || !/^[A-Za-z0-9+/]+={0,2}$/.test(b.audio)) return undefined
  const bytes = new Uint8Array(Buffer.from(b.audio, 'base64'))
  if (!bytes.length || bytes.length > MAX_CLIP_BYTES || !isMp3(bytes)) return undefined
  const engine = typeof b.engine === 'string' && /^[a-z0-9]{1,16}$/.test(b.engine) ? b.engine : 'unknown'
  return { voice: b.voice, engine, bytes }
}

// --- the visitor's node voices it: in the host's cast voice -----------------------------------------------------------

/** The engines a visit's lines can be voiced with on this node: the node's own ElevenLabs and Gepard (none on the phone). */
export type VisitEngines = { eleven?: ElevenLabs; gepard?: Gepard }

/**
 * [text] in the town's [language], said by [spec] (a speaker of the host's cast) with this node's engines: ElevenLabs in
 * the speaker's voice for that language (a live line: [live] takes its place in the live lines' rate window, and it may
 * use the reserve; a short word of a carrier voice in the language's carrier sentence), else the local Gepard worker in
 * the speaker's local voice, for a Slovene town only. Drama 3 isn't asked: it makes batch clips only. Each take through
 * the host's filter for that voice, its pace, its edges and the level, as the store keeps a clip ([level]: ffmpeg's, when
 * the node has it). Null when no engine can now.
 */
export async function voiceInTown(o: {
  text: string
  language: string
  spec: CastSpeaker
  engines: VisitEngines
  live: () => boolean
  level?: () => Leveller | undefined
  log?: Log
}): Promise<{ bytes: Uint8Array; engine: 'elevenlabs' | 'gepard'; engine_voice: string } | null> {
  const { text, language, spec, engines } = o
  const eleven = engines.eleven?.configured && spec.elevenlabs ? engines.eleven : undefined
  const gepard = language === 'sl' && spec.gepard ? engines.gepard : undefined
  if (!eleven && !gepard) return null
  if (!o.live()) return null
  const processed = async (bytes: Uint8Array, engine: 'elevenlabs' | 'gepard', carrierCut: boolean) => {
    const lv = o.level?.()
    const tempo = spec.tempo?.[engine]
    const r = lv
      ? await lv.level(bytes, { denoise: engine === 'elevenlabs' ? spec.denoise : undefined, pace: tempo ? { tempo, pause: null } : undefined, edges: true, word: !carrierCut && isOneWord(text) }).catch(() => null)
      : null
    return r?.bytes ?? bytes
  }
  if (eleven) {
    try {
      const carrier = { language, carrier: spec.carrier === true }
      if (await eleven.ready(eleven.costAs(text, carrier), true)) {
        const take = await eleven.sayAs(text, { id: spec.elevenlabs!, ...carrier, live: true })
        if (take) return { bytes: await processed(take.bytes, 'elevenlabs', take.method === 'carrier'), engine: 'elevenlabs', engine_voice: spec.elevenlabs! }
      }
    } catch (e) {
      o.log?.(`visit voice: elevenlabs failed: ${(e as Error).message}`)
    }
  }
  if (gepard && (await gepard.up())) {
    const speaker = isShortText(text) && spec.gepard_words ? spec.gepard_words : spec.gepard!
    const take = await gepard.sayAs(text, speaker)
    if (take) return { bytes: await processed(take.bytes, 'gepard', false), engine: 'gepard', engine_voice: speaker }
  }
  return null
}

// --- what the visitor keeps -------------------------------------------------------------------------------------------

/**
 * A visit's clip on this node: of town [town] (its [language]), the line [norm] ([text] as said) in [voice] as the visit
 * asked for it; [from] the host (its own clip) or this node (voiced here in the host's cast voice), by [engine] in the
 * engine's own voice; its [file] in the store's files and its size; when it came and when it was last given out.
 */
export type VisitClip = {
  key: string
  town: string
  language: string
  voice: string
  norm: string
  text: string
  file: string
  from: 'host' | 'node'
  engine: string
  engine_voice?: string
  bytes: number
  at: string
  used: string
}

/** The URL of a visit's clip for the app (GET /towns/voice/file/<file>). */
export const visitFileUrl = (file: string) => `/towns/voice/file/${file}`

/** What the visitor's store keeps, at most, unless LANI_VISIT_VOICE_MB says: the least recently given out go first. */
export const VISIT_VOICE_MB = 100

/**
 * The clips of the visits this node made (<voice dir>/visits/: index.json and files/<sha1>.mp3), apart from its village's
 * own clips (no re-voice, level or stale pass of the store ever sees them): per town, voice and line, at most [maxBytes]
 * in all, the least recently used dropped first; the clips of a town no longer linked go ([forget]).
 */
export class VisitClips {
  readonly dir: string
  readonly filesDir: string
  private readonly indexFile: string
  private clips = new Map<string, VisitClip>()
  private dirty = false

  constructor(
    dir: string,
    private readonly o: { maxBytes?: number; log?: Log } = {},
  ) {
    this.dir = dir
    this.filesDir = join(dir, 'files')
    this.indexFile = join(dir, 'index.json')
    try {
      if (existsSync(this.indexFile)) {
        const d = JSON.parse(readFileSync(this.indexFile, 'utf8'))
        for (const c of Array.isArray(d?.clips) ? d.clips : []) if (c && typeof c.key === 'string' && typeof c.file === 'string' && FILE.test(c.file)) this.clips.set(c.key, c)
      }
    } catch (e) {
      o.log?.(`visit voices: ${this.indexFile} unreadable (${(e as Error).message}); starting empty`)
    }
  }

  static key = (town: string, voice: string, norm: string) => `${town}|${voice}|${norm}`

  get maxBytes(): number { return this.o.maxBytes ?? VISIT_VOICE_MB * 1024 * 1024 }

  /** The clip of town [town]'s line [norm] in [voice], when its file is here. */
  get(town: string, voice: string, norm: string): VisitClip | undefined {
    const c = this.clips.get(VisitClips.key(town, voice, norm))
    return c && existsSync(join(this.filesDir, c.file)) ? c : undefined
  }

  /** A clip given out now (the least recently used go first). */
  touch(c: VisitClip) {
    c.used = new Date().toISOString()
    this.dirty = true
  }

  /** Keeps [bytes] as town [town]'s line in [voice]: a new file (the old one of the line goes), the index written, the store kept within its size. */
  put(c: Omit<VisitClip, 'key' | 'file' | 'bytes' | 'at' | 'used'> & { bytes: Uint8Array }): VisitClip {
    mkdirSync(this.filesDir, { recursive: true })
    const key = VisitClips.key(c.town, c.voice, c.norm)
    const sha = createHash('sha256').update(c.bytes).digest('hex')
    const file = `${createHash('sha1').update(`visit|${key}|${c.from}|${c.engine}|${c.engine_voice ?? ''}|${sha}`).digest('hex')}.mp3`
    const p = join(this.filesDir, file)
    writeFileSync(`${p}.tmp`, c.bytes)
    renameSync(`${p}.tmp`, p)
    const now = new Date().toISOString()
    const { bytes, ...rest } = c
    const clip: VisitClip = { ...rest, key, file, bytes: bytes.length, at: now, used: now }
    const old = this.clips.get(key)
    this.clips.set(key, clip)
    if (old && old.file !== file && ![...this.clips.values()].some(x => x.file === old.file)) this.unlink(old.file)
    this.trim()
    this.save()
    return clip
  }

  /** Town [town]'s clips as the app's index has them: normalized text → voice → URL. */
  index(town: string): Record<string, Record<string, string>> {
    const out: Record<string, Record<string, string>> = {}
    for (const c of this.clips.values()) if (c.town === town) (out[c.norm] ??= {})[c.voice] = visitFileUrl(c.file)
    return out
  }

  /** The path of a visit's clip [file] (a name of the store's own: <sha1>.mp3, in its index); undefined otherwise. */
  path(file: string): string | undefined {
    if (!FILE.test(file) || ![...this.clips.values()].some(c => c.file === file)) return undefined
    const p = join(this.filesDir, file)
    return existsSync(p) ? p : undefined
  }

  /** The clip whose file is [file], if any. */
  byFile(file: string): VisitClip | undefined {
    for (const c of this.clips.values()) if (c.file === file) return c
    return undefined
  }

  /** The clips of the towns that aren't [linked] any more go. */
  forget(linked: (town: string) => boolean) {
    let gone = 0
    for (const [k, c] of this.clips) {
      if (linked(c.town)) continue
      this.clips.delete(k)
      this.unlink(c.file)
      gone++
    }
    if (gone) {
      this.o.log?.(`visit voices: ${gone} clips of towns no longer linked let go`)
      this.save()
    }
  }

  /** How much is kept: the clips, their bytes, per town, and how many came from the host and how many were voiced here. */
  stats() {
    const all = [...this.clips.values()]
    const towns: Record<string, number> = {}
    for (const c of all) towns[c.town] = (towns[c.town] ?? 0) + 1
    return { clips: all.length, bytes: all.reduce((n, c) => n + c.bytes, 0), max_bytes: this.maxBytes, host: all.filter(c => c.from === 'host').length, node: all.filter(c => c.from === 'node').length, towns }
  }

  /** Writes the index when a clip was given out since (the least recently used order kept across restarts). */
  flush() {
    if (this.dirty) this.save()
  }

  private trim() {
    let total = [...this.clips.values()].reduce((n, c) => n + c.bytes, 0)
    if (total <= this.maxBytes) return
    for (const c of [...this.clips.values()].sort((a, b) => a.used.localeCompare(b.used))) {
      if (total <= this.maxBytes) break
      this.clips.delete(c.key)
      if (![...this.clips.values()].some(x => x.file === c.file)) this.unlink(c.file)
      total -= c.bytes
    }
  }

  private unlink(file: string) {
    try {
      unlinkSync(join(this.filesDir, file))
    } catch {}
  }

  private save() {
    mkdirSync(this.dir, { recursive: true })
    const tmp = `${this.indexFile}.tmp`
    writeFileSync(tmp, JSON.stringify({ schema: 'lani.visit-voices/v1', clips: [...this.clips.values()] }))
    renameSync(tmp, this.indexFile)
    this.dirty = false
  }
}
