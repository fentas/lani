// The voice cast (companion/voice-cast.json): who speaks Lani's Slovene. A speaker has an ElevenLabs
// voice, a local Gepard voice for when ElevenLabs can't, and a gender (the app's fallback voice). The
// narrators `female` and `male` voice every generic text; a villager speaks with their `speaker`.
// The file is read again when it changes, so swapping a voice is a one-line edit (README, "Voice").
// A culture pack in another language has its own cast (companion/cultures/<id>/voice-cast.json, the same
// archetypes): a speaker whose ElevenLabs id is still empty has no voice yet, and a cast with none voices
// nothing, so the app speaks with the phone's text-to-speech (castVoiced).
// The cast's `denoise` names the voices whose ElevenLabs clips go through a filter before they are stored (a designed
// voice that hisses: Stari Janez's own); voice.ts DENOISE has the filters.
import { readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'

export type Gender = 'female' | 'male'
export const GENDERS: Gender[] = ['female', 'male']
/** A speaker name: "grandma", "young-man". */
export const SPEAKER_ID = /^[a-z][a-z0-9-]{0,31}$/

const speakerSpec = z.object({
  gender: z.enum(['female', 'male']),
  // an ElevenLabs voice id; "" while the speaker has none yet (a culture pack's cast still to choose)
  elevenlabs: z.string().regex(/^([A-Za-z0-9]{10,40})?$/, 'an ElevenLabs voice id, or "" for none yet'),
  gepard: z.string().regex(/^[a-z0-9-]{1,32}$/).optional(),
  // says a lone word inside a carrier sentence, which is cut out (voice.ts, CARRIERS): an English library voice says a
  // lone Slovene word the English way
  carrier: z.boolean().optional(),
  // the languages its ElevenLabs voice speaks natively ("en" for the English library narrators): a Slovene narrator line
  // that the local voice can't make goes to a cast voice native in Slovene of the narrator's gender (nativeSpeaker)
  native: z.array(z.string().regex(/^[a-z]{2}$/)).optional(),
  name: z.string().max(80).optional(),
  role: z.string().max(200).optional(),
})
const castSpec = z
  .object({ speakers: z.record(z.string().regex(SPEAKER_ID, 'speaker names are kebab-case'), speakerSpec) })
  .refine(c => c.speakers.female?.gender === 'female' && c.speakers.male?.gender === 'male', 'the narrators "female" and "male" must be there, with their gender')

export type Speaker = z.infer<typeof speakerSpec>
export type Cast = Record<string, Speaker>

/** The narrators alone, when there's no cast file: Matilda and Daniel (English), the worker's nina and marko. */
export const NARRATORS: Cast = {
  female: { gender: 'female', elevenlabs: 'XrExE9yKIg1WjnnlVkGX', gepard: 'nina', name: 'Matilda', carrier: true, native: ['en'] },
  male: { gender: 'male', elevenlabs: 'onwK4e9ZLuTAKqWW03F9', gepard: 'marko', name: 'Daniel', carrier: true, native: ['en'] },
}

export const CAST_FILE = join(import.meta.dir, '../../voice-cast.json')

/**
 * The filters a voice's clips can go through before they are stored (the cast's `denoise`: a voice → a preset, `true`
 * for the first): `hiss`, the gentle one; `hiss-strong`, for a voice whose hiss `hiss` doesn't take away.
 */
export const DENOISE_PRESETS = ['hiss', 'hiss-strong'] as const
export type DenoisePreset = (typeof DENOISE_PRESETS)[number]

let cached: { path: string; mtime: number; cast: Cast; denoise: Record<string, DenoisePreset> } | undefined
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
  if (mtime >= 0) {
    try {
      const raw = JSON.parse(readFileSync(path, 'utf8'))
      const r = castSpec.safeParse(raw)
      if (r.success) c = r.data.speakers
      else console.error(`[voice] ${path}: ${z.prettifyError(r.error)}; using the narrators only`)
      denoise = denoiseSpec(raw?.denoise, c, path)
    } catch (e) {
      console.error(`[voice] ${path}: ${(e as Error).message}; using the narrators only`)
    }
  }
  cached = { path, mtime, cast: c, denoise }
  return cached
}

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
    else if (!PERSON_VOICE.test(voice) && !Object.hasOwn(speakers, voice)) console.error(`[voice] ${path}: denoise "${voice}": not a speaker of the cast, nor someone's own voice ("@id"); not filtered`)
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

/** The cast speaker a voice stands in for: itself, or a person's archetype ("@micka" → grandma). */
export const archetypeOf = (name: string): string => personVoice(name)?.archetype ?? name

/** The ElevenLabs voice id of a voice: a person's own, else the cast speaker's (none while it's ""). */
export const elevenlabsOf = (name: string): string | undefined => personVoice(name)?.elevenlabs ?? (cast()[name]?.elevenlabs || undefined)

/**
 * Whether [name] says a lone word inside a carrier sentence: a cast speaker marked `carrier` (the narrators, English
 * library voices). A person's own voice is designed from Slovene and never needs it.
 */
export const carrierVoice = (name: string): boolean => isSpeaker(name) && cast()[name].carrier === true

/**
 * A speaker of the cast whose ElevenLabs voice speaks [language] natively (its `native`), of [gender]: where a Slovene
 * narrator line goes when the local voice can't make it (voice.ts). Undefined when the cast has none: the library has one
 * Slovenian voice, and the cast doesn't use it.
 */
export function nativeSpeaker(gender: Gender, language: string): string | undefined {
  return Object.entries(cast()).find(([, s]) => s.gender === gender && !!s.elevenlabs && !!s.native?.includes(language))?.[0]
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

/** Who reads someone's lines: their registered speaker, else their gender voice. */
export function speakerFor(v: { voice: Gender; speaker?: string }): string {
  return isSpeaker(v.speaker) ? v.speaker : v.voice
}

/**
 * The speaker of someone who moved in or was born in the village (mirrored in the app's
 * Residents.speakerOf): children are the girl and the boy, grown-ups the young woman and man.
 */
export function newcomerSpeaker(gender: Gender, young: boolean): string {
  const s = young ? (gender === 'female' ? 'girl' : 'boy') : gender === 'female' ? 'young-woman' : 'young-man'
  return isSpeaker(s) ? s : gender
}

/** The speakers as the tutor and the status see them. */
export function castInfo() {
  return Object.entries(cast()).map(([id, s]) => ({ id, gender: s.gender, name: s.name, role: s.role }))
}
