// The voice cast (companion/voice-cast.json): who speaks Lani's Slovene. A speaker has an ElevenLabs
// voice, a local Gepard voice for when ElevenLabs can't, and a gender (the app's fallback voice). The
// narrators `female` and `male` voice every generic text; a villager speaks with their `speaker`.
// The file is read again when it changes, so swapping a voice is a one-line edit (README, "Voice").
// A culture pack in another language has its own cast (companion/cultures/<id>/voice-cast.json, the same
// archetypes): a speaker whose ElevenLabs id is still empty has no voice yet, and a cast with none voices
// nothing, so the app speaks with the phone's text-to-speech (castVoiced).
// The cast's `denoise` names the voices whose ElevenLabs clips go through a filter before they are stored (a designed
// voice that hisses: Stari Janez's own); voice.ts DENOISE has the filters. A speaker's `tempo` speeds up a voice that
// speaks slowly (voice.ts clipPace): its clips are paced as they are stored, the ones from before by voice-build --pace.
// A speaker's `languages` are the voices it speaks a language with that its own voice doesn't speak natively: the
// narrators' Slovene voices (Nina, Luka) beside their English ones (Matilda, Daniel). Their clips are those of the voice
// "female.sl" (languageVoice), next to the narrator's local Gepard clips (voice.ts, "The narrators in Slovene").
// The cast's `player` is who says the learner's own lines in a dialog: a voice of the cast for each gender (the narrators'
// Slovene voices: Luka, Nina), the app asking for them as the voice "player" (PLAYER, playerVoice). The cast's `players`
// are the voices the learner chooses theirs from ("Moj glas · My voice", my-voice.ts): 2–3 per gender and age (adult,
// child), each a speaker of the cast that nobody else in it speaks with (crossCheck), the default first.
import { readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'

export type Gender = 'female' | 'male'
export const GENDERS: Gender[] = ['female', 'male']
/** A learner's voice's age (the cast's `players`): a grown-up's, or a child's for a child (the profile's child flag). */
export type Age = 'adult' | 'child'
export const AGES: Age[] = ['adult', 'child']
/** A speaker name: "grandma", "young-man". */
export const SPEAKER_ID = /^[a-z][a-z0-9-]{0,31}$/
/** A speaker's voice in a language (its `languages`): "female.sl", the female narrator's Slovene voice. */
export const LANGUAGE_VOICE = /^([a-z][a-z0-9-]{0,31})\.([a-z]{2})$/

const ELEVENLABS_ID = /^[A-Za-z0-9]{10,40}$/
/** A pace factor: 0.5 to 2 (1: as the engine makes it). */
const factor = z.number().min(0.5).max(2)
// a voice's own pace (voice.ts clipPace): its clips sped up (atempo, the pitch kept) by a factor, from every engine (a
// number) or by engine ({ "elevenlabs": 1.1 }: an engine left out keeps its pace)
const tempoSpec = z.union([factor, z.object({ elevenlabs: factor.optional(), gepard: factor.optional(), drama3: factor.optional() }).strict()])
export type TempoSpec = z.infer<typeof tempoSpec>
const languageSpec = z.object({
  // the ElevenLabs voice it speaks the language with: a library voice native in it (no voice slot)
  elevenlabs: z.string().regex(ELEVENLABS_ID, 'an ElevenLabs voice id'),
  name: z.string().max(80).optional(),
  // says a lone word inside the language's carrier sentence, which is cut out (voice.ts, CARRIERS), as a speaker's
  // `carrier`: the narrators' Slovene voices since 2026-10-26 (Nina, Luka: a native voice's lone word said plainly can
  // come out in another language's sound); their clips from before, said plainly, are kept (voice.ts oldStyle)
  carrier: z.boolean().optional(),
  // the library voice's owner, to add it to another account (the first request with its id adds it to this one)
  library_owner: z.string().max(80).optional(),
  // its own pace (tempoSpec)
  tempo: tempoSpec.optional(),
})
const speakerSpec = z.object({
  gender: z.enum(['female', 'male']),
  // an ElevenLabs voice id; "" while the speaker has none yet (a culture pack's cast still to choose)
  elevenlabs: z.string().regex(/^([A-Za-z0-9]{10,40})?$/, 'an ElevenLabs voice id, or "" for none yet'),
  // the day that voice took effect ("2026-10-14"): its clips made before it whose voice isn't recorded (from before
  // October 11, 2026) are in an earlier one, and are made again as the app asks for them (voice.ts, staleVoice)
  since: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'a day, YYYY-MM-DD').optional(),
  gepard: z.string().regex(/^[a-z0-9-]{1,32}$/).optional(),
  // the local voice for a lone word (a word, up to three: voice.ts isShortText), when it isn't [gepard] (the female
  // narrator: ana for words, nina for sentences)
  gepard_words: z.string().regex(/^[a-z0-9-]{1,32}$/).optional(),
  // says a lone word inside a carrier sentence, which is cut out (voice.ts, CARRIERS): an English library voice says a
  // lone Slovene word the English way
  carrier: z.boolean().optional(),
  // the voices it speaks a language with that its own voice doesn't speak natively (the narrators' Slovene: Nina, Luka)
  languages: z.record(z.string().regex(/^[a-z]{2}$/), languageSpec).optional(),
  // its own pace (tempoSpec): a voice that speaks slowly sped up (the girl's Piku, ×1.1 since 2026-10-19)
  tempo: tempoSpec.optional(),
  // a voice of the learner's own (the cast's `players`): a grown-up's or a child's; its place in `players` must agree
  age: z.enum(['adult', 'child']).optional(),
  name: z.string().max(80).optional(),
  role: z.string().max(200).optional(),
})
const castSpec = z
  .object({ speakers: z.record(z.string().regex(SPEAKER_ID, 'speaker names are kebab-case'), speakerSpec) })
  .refine(c => c.speakers.female?.gender === 'female' && c.speakers.male?.gender === 'male', 'the narrators "female" and "male" must be there, with their gender')

export type Speaker = z.infer<typeof speakerSpec>
export type Cast = Record<string, Speaker>

/**
 * The narrators alone, when there's no cast file: Matilda and Daniel (English), in Slovene Nina and Luka (native library
 * voices, a lone word in its carrier sentence too, as in the cast file); the worker's nina (ana for lone words) and
 * marko.
 */
export const NARRATORS: Cast = {
  female: {
    gender: 'female', elevenlabs: 'XrExE9yKIg1WjnnlVkGX', gepard: 'nina', gepard_words: 'ana', name: 'Matilda', carrier: true,
    languages: { sl: { elevenlabs: '0IwLFuWc6siAnpd77hRG', name: 'Nina', carrier: true } },
  },
  male: {
    gender: 'male', elevenlabs: 'onwK4e9ZLuTAKqWW03F9', gepard: 'marko', name: 'Daniel', carrier: true,
    languages: { sl: { elevenlabs: 'SmCUS1ekZnVjC5lqnRQG', name: 'Luka', carrier: true } },
  },
}

export const CAST_FILE = join(import.meta.dir, '../../voice-cast.json')

/**
 * The filters a voice's clips can go through before they are stored (the cast's `denoise`: a voice → a preset, `true`
 * for the first): `hiss`, the gentle one; `hiss-strong`, for a voice whose hiss `hiss` doesn't take away.
 */
export const DENOISE_PRESETS = ['hiss', 'hiss-strong'] as const
export type DenoisePreset = (typeof DENOISE_PRESETS)[number]

let cached: { path: string; mtime: number; cast: Cast; denoise: Record<string, DenoisePreset>; player: Record<Gender, string>; players: Players; explicit: Set<Gender> } | undefined
let castFile: string | undefined

/** The voice cast this bridge speaks with from now on: its culture pack's (voice-cast.json in the pack), else the default. */
export function useCastFile(path: string | undefined) {
  castFile = path
}

/**
 * The cast from LANI_VOICE_CAST, else the culture pack's ([useCastFile]), else companion/voice-cast.json, re-read when
 * the file changes; the narrators alone when it is missing or invalid.
 */
export function cast(path?: string): Cast {
  return load(path).cast
}

function load(path = process.env.LANI_VOICE_CAST || castFile || CAST_FILE): NonNullable<typeof cached> {
  let mtime = -1
  try {
    mtime = statSync(path).mtimeMs
  } catch {}
  if (cached?.path === path && cached.mtime === mtime) return cached
  let c = NARRATORS
  let denoise: Record<string, DenoisePreset> = {}
  let player = { ...PLAYER_DEFAULT }
  let players = emptyPlayers()
  let explicit = new Set<Gender>()
  if (mtime >= 0) {
    try {
      const raw = JSON.parse(readFileSync(path, 'utf8'))
      const r = castSpec.safeParse(raw)
      if (r.success) c = r.data.speakers
      else console.error(`[voice] ${path}: ${z.prettifyError(r.error)}; using the narrators only`)
      denoise = denoiseSpec(raw?.denoise, c, path)
      player = playerSpec(raw?.player, c, path)
      explicit = new Set(GENDERS.filter(g => typeof raw?.player?.[g] === 'string' && player[g] === raw.player[g]))
      const checked = checkPlayers(raw?.players, c)
      for (const e of checked.problems) console.error(`[voice] ${path}: ${e}`)
      players = checked.players
      // the cast's `player` written for a gender: never a voice the cast speaks with (the narrators' are theirs to give)
      for (const g of explicit) {
        const clash = castClash(player[g], c, optionSpeakers(players))
        if (clash) {
          console.error(`[voice] ${path}: player ${g}: ${JSON.stringify(player[g])} is ${clash === player[g] ? 'a voice of the cast (a voice of the learner\'s own is a speaker with its age)' : `${clash}'s voice`}: never a cast member's; the learner's voices say their lines`)
          explicit.delete(g)
        }
      }
    } catch (e) {
      console.error(`[voice] ${path}: ${(e as Error).message}; using the narrators only`)
    }
  }
  cached = { path, mtime, cast: c, denoise, player, players, explicit }
  return cached
}

/** The voice name the app asks for the learner's own lines in a dialog with: said in the cast's `player` ([playerVoice]). */
export const PLAYER = 'player'

/** Who says the learner's lines when the cast doesn't say: the narrator of their gender (in a Slovene village Luka, Nina). */
const PLAYER_DEFAULT: Record<Gender, string> = { female: 'female', male: 'male' }

/**
 * The cast's `player`: a gender (female, male) → the voice the learner's lines are said in, a speaker of the cast
 * ("player-male", a voice of the learner's own) or a speaker's voice in a language ("male.sl": the male narrator's
 * Slovene voice, Luka; a speaker without one there speaks it in its own); keys starting with "_" are comments. A wrong
 * entry is logged and that gender keeps its narrator.
 */
function playerSpec(raw: unknown, speakers: Cast, path: string): Record<Gender, string> {
  const out = { ...PLAYER_DEFAULT }
  if (raw === undefined) return out
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    console.error(`[voice] ${path}: "player" must map a gender (female, male) to a voice of the cast; the narrators say the learner's lines`)
    return out
  }
  for (const [g, v] of Object.entries(raw)) {
    if (g.startsWith('_')) continue
    if (!(GENDERS as string[]).includes(g)) console.error(`[voice] ${path}: player "${g}": not a gender (female, male); left out`)
    else if (typeof v !== 'string' || !Object.hasOwn(speakers, LANGUAGE_VOICE.exec(v)?.[1] ?? v)) console.error(`[voice] ${path}: player ${g}: ${JSON.stringify(v)} is neither a speaker of the cast nor one's voice in a language ("male.sl"); the ${g} narrator says the learner's lines`)
    else out[g as Gender] = v
  }
  return out
}

/** The cast's `player` as it is written, by gender ({male: "male.sl", female: "female.sl"}): the status, the tutor. */
export const playerVoices = (): Record<Gender, string> => ({ ...load().player })

// --- the learner's voices: the cast's `players` ("Moj glas · My voice", my-voice.ts) -------------------------------------

/** A gender → an age → the voices a learner of them chooses theirs from, the default first. */
export type Players = Record<Gender, Record<Age, string[]>>

/** At most this many voices to choose from in one gender and age: two or three are a choice, more a chore. */
export const MAX_OPTIONS = 3

const emptyPlayers = (): Players => ({ female: { adult: [], child: [] }, male: { adult: [], child: [] } })

/** [v] as the cast names a voice: a speaker ("player-boy-a"), or a speaker's voice in a language ("male.sl"). */
const speakerOfVoice = (v: string) => LANGUAGE_VOICE.exec(v)?.[1] ?? v

/** The speakers [players] names (a language voice's speaker: "male.sl" → male). */
const optionSpeakers = (players: Players) => new Set(GENDERS.flatMap(g => AGES.flatMap(a => players[g][a].map(speakerOfVoice))))

/** The ElevenLabs voice [v] speaks with in [speakers]: a speaker's own, a speaker's voice in a language's; undefined for none. */
function elevenlabsIn(v: string, speakers: Cast): string | undefined {
  const m = LANGUAGE_VOICE.exec(v)
  if (m) return Object.hasOwn(speakers, m[1]) ? speakers[m[1]].languages?.[m[2]]?.elevenlabs || undefined : undefined
  return Object.hasOwn(speakers, v) ? speakers[v].elevenlabs || undefined : undefined
}

/**
 * Who in [speakers] already speaks with the ElevenLabs voice [v] speaks with, if anyone: a speaker the learner talks to in a
 * dialog (an archetype: grandma, young-man, boy …; any of its voices in a language too; [v]'s own speaker when it is one),
 * never the narrators (they read the words and exercises: the app's own voice, which may be a learner's, Luka for Jan)
 * nor the learner's own voices ([options], and every speaker with an `age`: a learner's voice by its declaration).
 * "young-man" for the friuli male narrator, Chris Basetta, who is Pastore Davide's voice too: the bug of October 2026,
 * Teo's line and Davide's reply in one voice.
 */
export function castClash(v: string, speakers: Cast, options: Set<string>): string | undefined {
  const id = elevenlabsIn(v, speakers)
  if (!id) return undefined
  for (const [name, s] of Object.entries(speakers)) {
    if (isNarrator(name) || options.has(name) || s.age) continue
    if (s.elevenlabs === id) return name
    for (const [l, lv] of Object.entries(s.languages ?? {})) if (lv.elevenlabs === id) return `${name}.${l}`
  }
  return undefined
}

/**
 * The cast's `players` checked against its [speakers] ([raw]: the file's `players`): a gender (female, male) → an age
 * (adult, child) → the voices a learner of them chooses theirs from, the default first, at most MAX_OPTIONS. Each is a
 * speaker of the cast ("player-boy-a") or a speaker's voice in a language ("male.sl": the male narrator's Slovene voice,
 * Luka), of that gender, its `age` (when it says one) that age, in one place only, a voice no other option has, and never
 * a voice someone in the cast speaks with ([castClash]). Keys starting with "_" are comments. [problems]: each entry left
 * out, and why (the bridge logs them; the smoke test wants none in the casts shipped).
 */
export function checkPlayers(raw: unknown, speakers: Cast): { players: Players; problems: string[] } {
  const players = emptyPlayers()
  const problems: string[] = []
  if (raw === undefined) return { players, problems }
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return { players, problems: ['"players" must map a gender (female, male) to an age (adult, child) to a list of voices of the cast'] }
  // every speaker the file names among them, so one option's voice is never counted as the cast's against another
  const named = new Set<string>()
  for (const [g, ages] of Object.entries(raw as Record<string, unknown>))
    if (!g.startsWith('_') && ages && typeof ages === 'object') for (const [a, list] of Object.entries(ages)) if (!a.startsWith('_') && Array.isArray(list)) for (const v of list) if (typeof v === 'string') named.add(speakerOfVoice(v))
  const placed = new Map<string, string>()
  const voices = new Map<string, string>()
  for (const [g, ages] of Object.entries(raw as Record<string, unknown>)) {
    if (g.startsWith('_')) continue
    if (!(GENDERS as string[]).includes(g)) {
      problems.push(`players "${g}": not a gender (female, male); left out`)
      continue
    }
    if (!ages || typeof ages !== 'object' || Array.isArray(ages)) {
      problems.push(`players ${g}: must map an age (adult, child) to a list of voices; left out`)
      continue
    }
    for (const [a, list] of Object.entries(ages as Record<string, unknown>)) {
      if (a.startsWith('_')) continue
      const at = `players ${g} ${a}`
      if (!(AGES as string[]).includes(a)) {
        problems.push(`${at}: not an age (adult, child); left out`)
        continue
      }
      if (!Array.isArray(list)) {
        problems.push(`${at}: must be a list of voices; left out`)
        continue
      }
      for (const v of list) {
        const why = optionProblem(v, g as Gender, a as Age, speakers, named, placed, voices)
        if (why) problems.push(`${at}: ${JSON.stringify(v)} ${why}; left out`)
        else if (players[g as Gender][a as Age].length >= MAX_OPTIONS) problems.push(`${at}: ${JSON.stringify(v)}: more than ${MAX_OPTIONS} voices to choose from; left out`)
        else {
          players[g as Gender][a as Age].push(v as string)
          placed.set(v as string, at)
          const id = elevenlabsIn(v as string, speakers)
          if (id) voices.set(id, v as string)
        }
      }
    }
  }
  return { players, problems }
}

/** Why [v] can't be a voice to choose in [g] [a] (see [checkPlayers]); undefined when it can. */
function optionProblem(v: unknown, g: Gender, a: Age, speakers: Cast, named: Set<string>, placed: Map<string, string>, voices: Map<string, string>): string | undefined {
  if (typeof v !== 'string') return 'is not a voice'
  const m = LANGUAGE_VOICE.exec(v)
  const name = m?.[1] ?? v
  if (!Object.hasOwn(speakers, name)) return `is neither a speaker of the cast nor one's voice in a language ("male.sl")`
  const s = speakers[name]
  if (m && !s.languages?.[m[2]]) return `names a voice in "${m[2]}" that ${name} hasn't`
  if (s.gender !== g) return `is a ${s.gender} voice`
  if (!m && !s.age && !isNarrator(name)) return 'has no age: a voice of the learner\'s is a speaker with its age (adult, child)'
  if (s.age && s.age !== a) return `is a${s.age === 'adult' ? 'n' : ''} ${s.age}'s voice`
  if (!elevenlabsIn(v, speakers) && !s.gepard) return 'has no voice yet (no ElevenLabs id, no local one)'
  if (placed.has(v)) return `is in ${placed.get(v)} already`
  const id = elevenlabsIn(v, speakers)
  if (id && voices.has(id)) return `is the same ElevenLabs voice as ${voices.get(id)}`
  const clash = castClash(v, speakers, named)
  if (clash) return `is ${clash}'s voice: never a voice someone in the cast speaks with`
  return undefined
}

/**
 * One of the voices a learner chooses theirs from: [id] as the cast names it ("male.sl", "player-boy-a"), [voice] as the
 * store speaks it ("male", "player-boy-a"), its gender and age, the name to show ("Luka", "Teddy"), and its place on its
 * list ([pick]: 0, the default, 1, 2), which a visit carries to the town's list of that gender and age.
 */
export type PlayerOption = { id: string; voice: string; gender: Gender; age: Age; name: string; pick: number }

/** [id] of [g] [a] as an option, at place [pick]. */
function optionOf(id: string, g: Gender, a: Age, pick: number): PlayerOption {
  const m = LANGUAGE_VOICE.exec(id)
  const s = cast()[m?.[1] ?? id]
  const name = (m ? s?.languages?.[m[2]]?.name : s?.name) ?? s?.name ?? id
  return { id, voice: m ? m[1] : id, gender: g, age: a, name: name.replace(/\s+[-–(].*$/, '').trim() || name, pick }
}

/**
 * The voices a learner of [g] and [a] chooses theirs from, the default first: the cast's `players`, after the cast's
 * `player` for a grown-up when the file names one (a voice of the learner's own as one line: "player-male"), at most
 * MAX_OPTIONS; a child's without voices of their own: a grown-up's. None when the cast has none: [playerVoice] falls back.
 */
export function optionsOf(g: Gender, a: Age): PlayerOption[] {
  const l = load()
  let ids = l.players[g][a]
  if (a === 'adult' && l.explicit.has(g)) ids = [l.player[g], ...ids.filter(v => v !== l.player[g])]
  if (!ids.length && a === 'child') return optionsOf(g, 'adult')
  return ids.slice(0, MAX_OPTIONS).map((id, i) => optionOf(id, g, a, i))
}

/** Every voice of the cast's `players` (and `player`, written), by gender and age: the status, the tutor, voice-build. */
export function playerOptions(): Record<Gender, Record<Age, PlayerOption[]>> {
  const l = load()
  const of = (g: Gender, a: Age) => (a === 'child' && !l.players[g].child.length ? [] : optionsOf(g, a))
  return { female: { adult: of('female', 'adult'), child: of('female', 'child') }, male: { adult: of('male', 'adult'), child: of('male', 'child') } }
}

/**
 * Whether [name] is a speaker of the cast that is a learner's voice and nobody else's (not a narrator; one with an `age`,
 * or one the cast's `players` or `player` names): its lines are a learner's, said in it alone (no other voice's clip
 * stands in), and no villager may speak with it.
 */
export function isLearnerVoice(name: string): boolean {
  if (!isSpeaker(name) || isNarrator(name)) return false
  const l = load()
  return !!l.cast[name].age || optionSpeakers(l.players).has(name) || [...l.explicit].some(g => speakerOfVoice(l.player[g]) === name)
}

/**
 * The option a learner of [gender] (the female one's while the profile says none) and [age] hears their lines in at place
 * [pick] of their list (else its first): see [optionsOf]. Without options, the cast's `player` for the gender, as before
 * the options (the narrator of the gender when the cast says none), unless someone in the cast speaks with it
 * ([castClash]: never a cast member's voice): then none, and the phone speaks the learner's lines.
 */
export function playerOption(gender?: Gender, age: Age = 'adult', pick = 0): PlayerOption | undefined {
  const g = gender ?? 'female'
  const options = optionsOf(g, age)
  if (options.length) return options[pick] ?? options[0]
  const l = load()
  const v = l.player[g]
  if (castClash(v, l.cast, optionSpeakers(l.players))) return undefined
  return optionOf(v, g, age, 0)
}

/**
 * The voice the learner's own lines are said in, as the store speaks it, for a learner of [gender] and [age] at place
 * [pick] ([playerOption]): a speaker of the cast. A speaker's voice in a language there ("male.sl") is that speaker's: the
 * store says the village's language with it (the narrators' Slovene voices in a Slovene village: Luka, Nina). A learner
 * whose gender the profile doesn't say ([gender] undefined) has the female one, as [genderOf] counts a name it doesn't
 * know. Undefined when the cast has no voice that may say them.
 */
export function playerVoice(gender?: Gender, age: Age = 'adult', pick = 0): string | undefined {
  return playerOption(gender, age, pick)?.voice
}

/** [name] as the store speaks it: the learner's lines ([PLAYER]) in [playerVoice] for [gender], any other voice itself. */
export const voiceFor = (name: string, gender?: Gender): string | undefined => (name === PLAYER ? playerVoice(gender) : name)

/**
 * The cast's `denoise`: a voice (a speaker, or someone's own voice "@janez") → its preset (`true`: the first, `false`:
 * none); keys starting with "_" are comments. A wrong entry is logged and left out; the rest of the cast stays.
 */
function denoiseSpec(raw: unknown, speakers: Cast, path: string): Record<string, DenoisePreset> {
  const out: Record<string, DenoisePreset> = {}
  if (raw === undefined) return out
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    console.error(`[voice] ${path}: "denoise" must map voices to a preset (${DENOISE_PRESETS.join(', ')}); nothing is filtered`)
    return out
  }
  for (const [voice, v] of Object.entries(raw)) {
    if (voice.startsWith('_') || v === false) continue
    const preset = v === true ? DENOISE_PRESETS[0] : v
    if (!(DENOISE_PRESETS as readonly unknown[]).includes(preset)) console.error(`[voice] ${path}: denoise "${voice}": no preset ${JSON.stringify(v)} (${DENOISE_PRESETS.join(', ')}); not filtered`)
    else if (!PERSON_VOICE.test(voice) && !Object.hasOwn(speakers, voice) && !languageOf(voice, speakers)) console.error(`[voice] ${path}: denoise "${voice}": not a speaker of the cast, nor one's voice in a language ("female.sl"), nor someone's own voice ("@id"); not filtered`)
    else out[voice] = preset as DenoisePreset
  }
  return out
}

/** The filter [voice]'s ElevenLabs clips go through before they are stored (the cast's `denoise`), if any. */
export function denoiseOf(voice: string): DenoisePreset | undefined {
  const d = load().denoise
  return Object.hasOwn(d, voice) ? d[voice] : undefined
}

/** Every voice the cast filters, with its preset. */
export const denoiseVoices = (): Record<string, DenoisePreset> => ({ ...load().denoise })

export const isSpeaker = (name: string | undefined): name is string => !!name && Object.hasOwn(cast(), name)
export const isNarrator = (name: string) => (GENDERS as string[]).includes(name)

/** A person's own voice as a speaker name: "@micka" (see profiles.ts). */
export const PERSON_VOICE = /^@[a-z0-9][a-z0-9-]{0,63}$/
export type PersonVoice = { elevenlabs: string; archetype: string }
let people: (id: string) => PersonVoice | undefined = () => undefined

/** Where the voices of their own come from: the voice profiles (set once at start). */
export function usePersonVoices(f: (id: string) => PersonVoice | undefined) {
  people = f
}

/** [name]'s own voice when it is someone's ("@micka") and they have one. */
export function personVoice(name: string): PersonVoice | undefined {
  return PERSON_VOICE.test(name) ? people(name.slice(1)) : undefined
}

/** A voice clips can be made in: a speaker of the cast, or someone with a voice of their own. */
export const isVoice = (name: string | undefined): name is string => isSpeaker(name) || (!!name && !!personVoice(name))

/** [name] as one's voice in a language ("female.sl"): the speaker, the language and the cast's entry for it, if it has one. */
function languageOf(name: string, speakers: Cast = cast()): { speaker: string; language: string; spec: NonNullable<Speaker['languages']>[string] } | undefined {
  const m = LANGUAGE_VOICE.exec(name)
  const spec = m && Object.hasOwn(speakers, m[1]) ? speakers[m[1]].languages?.[m[2]] : undefined
  return m && spec ? { speaker: m[1], language: m[2], spec } : undefined
}

/**
 * The voice [speaker] speaks [language] with when the cast gives it one there (its `languages`): "female.sl", the female
 * narrator's Slovene voice (Nina); undefined when it speaks the language with its own voice, or not at all.
 */
export const languageVoice = (speaker: string, language: string): string | undefined =>
  isSpeaker(speaker) && cast()[speaker].languages?.[language]?.elevenlabs ? `${speaker}.${language}` : undefined

/** The name the cast gives a voice in a language ("female.sl" → Nina), if any. */
export const languageVoiceName = (name: string): string | undefined => languageOf(name)?.spec.name

/** The voice the app knows a voice by: one's voice in a language is the speaker's ("female.sl" → female); else itself. */
export const baseVoice = (name: string): string => LANGUAGE_VOICE.exec(name)?.[1] ?? name

/** The cast speaker a voice stands in for: itself, a person's archetype ("@micka" → grandma), a language voice's speaker ("female.sl" → female). */
export const archetypeOf = (name: string): string => personVoice(name)?.archetype ?? baseVoice(name)

/** The ElevenLabs voice id of a voice: a person's own, one's voice in a language, else the cast speaker's (none while it's ""). */
/**
 * The day [name]'s ElevenLabs voice took effect (the cast's `since`), for a speaker of the cast; undefined for one's voice
 * in a language, someone's own voice (a new one drops the old one's clips: profiles.ts) or a speaker without it.
 */
export const voiceSince = (name: string): string | undefined => (isSpeaker(name) ? cast()[name].since : undefined)

export const elevenlabsOf = (name: string): string | undefined => personVoice(name)?.elevenlabs ?? languageOf(name)?.spec.elevenlabs ?? (cast()[name]?.elevenlabs || undefined)

/**
 * Whether [name] says a lone word inside a carrier sentence: a cast speaker marked `carrier` (the narrators, English
 * library voices), or one's voice in a language marked so (the narrators' Slovene voices, "female.sl" and "male.sl":
 * Nina and Luka, since 2026-10-26). A person's own voice is designed from Slovene and never needs it.
 */
export const carrierVoice = (name: string): boolean => (isSpeaker(name) && cast()[name].carrier === true) || languageOf(name)?.spec.carrier === true

/**
 * The factor [name]'s clips by [engine] are sped up by (the cast's `tempo`, a speaker's or its voice in a language's:
 * "female.sl"; voice.ts clipPace): 1 for none, for an engine its `tempo` leaves out, and for someone's own voice.
 */
export function tempoOf(name: string, engine: 'elevenlabs' | 'gepard' | 'drama3'): number {
  const spec = isSpeaker(name) ? cast()[name].tempo : languageOf(name)?.spec.tempo
  const t = typeof spec === 'number' ? spec : spec?.[engine]
  return t ?? 1
}

/** The voices the cast gives a pace of their own, with their `tempo` (the status, voice-build --pace). */
export function tempoVoices(): Record<string, TempoSpec> {
  const out: Record<string, TempoSpec> = {}
  for (const [id, s] of Object.entries(cast())) {
    if (s.tempo !== undefined) out[id] = s.tempo
    for (const [lang, l] of Object.entries(s.languages ?? {})) if (l.tempo !== undefined) out[`${id}.${lang}`] = l.tempo
  }
  return out
}

/** Whether anyone in the cast has a voice (an ElevenLabs id or a local Gepard voice): a culture's cast may have none yet. */
export const castVoiced = (): boolean => Object.values(cast()).some(s => !!s.elevenlabs || !!s.gepard)

/** The speaker's gender; an unknown name counts as female (the default voice). */
export function genderOf(name: string): Gender {
  const n = archetypeOf(name)
  return cast()[n]?.gender ?? (n === 'male' ? 'male' : 'female')
}

/**
 * Where a clip of [name] falls back to when there is none: a person's archetype, then the gender's narrator,
 * then the other narrator.
 */
export function fallbacks(name: string): string[] {
  const g = genderOf(name)
  const chain = [archetypeOf(name), g, g === 'female' ? 'male' : 'female']
  return chain.filter((v, i) => v !== name && chain.indexOf(v) === i)
}

/** Who reads someone's lines: their registered speaker (never one of the learner's voices), else their gender voice. */
export function speakerFor(v: { voice: Gender; speaker?: string }): string {
  return isSpeaker(v.speaker) && !isLearnerVoice(v.speaker) ? v.speaker : v.voice
}

/**
 * The speaker of someone who moved in or was born in the village (mirrored in the app's
 * Residents.speakerOf): children are the girl and the boy, grown-ups the young woman and man.
 */
export function newcomerSpeaker(gender: Gender, young: boolean): string {
  const s = young ? (gender === 'female' ? 'girl' : 'boy') : gender === 'female' ? 'young-woman' : 'young-man'
  return isSpeaker(s) ? s : gender
}

/** The speakers as the tutor and the status see them (a speaker's voices in other languages: {sl: "Nina"}). */
export function castInfo() {
  return Object.entries(cast()).map(([id, s]) => ({
    id, gender: s.gender, name: s.name, role: s.role, ...(s.age ? { age: s.age } : {}),
    ...(s.languages ? { languages: Object.fromEntries(Object.entries(s.languages).map(([l, v]) => [l, v.name ?? v.elevenlabs])) } : {}),
  }))
}
