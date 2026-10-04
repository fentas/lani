// lani.villager/v0 — the people of Moja vas: quest givers, the people in scenes, the companion on
// the training stage, and the characters the tutor plays when Jan talks to them (companion/VILLAGERS.md).
// Curated villagers are a culture pack's cast (companion/cultures/<culture>/villagers/*.json, the learner's
// culture: cultures.ts); the tutor publishes more to <data>/app/villagers
// (a tutor villager with a curated id replaces it, until remove_villager).
// Keep in sync with .claude/skills/lani-studio/reference/module-spec.md ("Villagers").
import { existsSync, mkdirSync, readdirSync, readFileSync, renameSync, statSync, unlinkSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import { schemaField } from './schema'
import { cast, genderOf, isSpeaker, newcomerSpeaker, SPEAKER_ID, speakerFor, type Gender } from './cast'
import { PRIMORSKA_TUTOR, type CultureTutor } from './cultures'
import type { GameDoc } from './game'
import { fits, label, labelEn, labelTarget, lineLangs, meant, parseLike, partNow, PARTS, said, spokenLine, type Label, type Part, type SpokenLine } from './langs'
import { knownIds, Names, residentsNamed } from './mentions'
import type { Scenario } from './scenarios'
import { ARTS, RESOURCES, SCENE_ART, spriteVoice } from './scenes'

/**
 * The landscape's named spots, mirrored from TownSpots in TownMarkers.kt (the smoke test compares):
 * where a villager can stand without a building, with the where-phrase in both languages.
 */
export const SPOTS: Record<string, { sl: string; en: string }> = {
  meadow: { sl: 'na travniku', en: 'in the meadow' },
  pond: { sl: 'ob ribniku', en: 'by the pond' },
  highseat: { sl: 'na preži', en: 'on the high seat' },
  path: { sl: 'na gozdni poti', en: 'on the forest path' },
  woodpile: { sl: 'pri skladovnici', en: 'by the woodpile' },
  rocks: { sl: 'pri skalah', en: 'by the rocks' },
  riverbank: { sl: 'ob potoku', en: 'by the stream' },
  road: { sl: 'na cesti', en: 'on the road' },
  // the charcoal pile deep in the woods below the village (a tall canvas only), where its burner sits some evenings
  kopa: { sl: 'pri kopi', en: 'by the charcoal pile' },
  // the land at the horizon: what the culture pack says is there (Primorska's hills, Friuli's sea)
  horizon: { sl: 'na obzorju', en: 'at the horizon' },
}
export const SPOT_IDS = Object.keys(SPOTS)
/** Where a villager can live: a building type (once built) or `spot:<id>` (always there). */
export const HOMES = [...SCENE_ART.buildings, ...SPOT_IDS.map(s => `spot:${s}`)]
export const REGISTERS = ['ti', 'vi'] as const
export const LINE_KINDS = ['greet', 'thanks', 'remember', 'idle', 'cheer', 'comfort', 'listen', 'bye'] as const
export type LineKind = (typeof LINE_KINDS)[number]
/**
 * `lines.gift`: what they say when the learner gives them a good from the chest, by the good: one they like (delight),
 * an ordinary one (warm thanks), a rare one (special joy). Optional (the app has plain ones); a culture pack's cast has them.
 */
export const GIFT_KINDS = ['liked', 'ordinary', 'rare'] as const
export type GiftKind = (typeof GIFT_KINDS)[number]

/** Friendship levels, mirrored from Bonds.kt: the points each level starts at, and its name. */
export const BOND_THRESHOLDS = [0, 10, 30, 70, 150]
export const BOND_NAMES = ['Tujec · Stranger', 'Znanec · Acquaintance', 'Prijatelj · Friend', 'Dober prijatelj · Good friend', 'Kot družina · Like family']

const text = z.string().trim().min(1)
const id = z.string().regex(/^[a-z0-9][a-z0-9-]{0,62}$/, 'must be kebab-case, 1-63 chars')
/** {"sl": "Dober dan!", "en": "Good day!"}, or {"it": "Buongiorno!", "sl": "Dober dan!", "en": "Good day!"} (langs.ts). */
const line = spokenLine(200)
/** At most [MAX_AT_ONCE] of a kind for one part of the day (crossCheck): a line for another time doesn't count. */
const lines = z.array(line).min(2).max(12)
const giftLines = z.array(line).min(1).max(6)
/** The most lines of one kind a villager has for one part of the day. */
export const MAX_AT_ONCE = 6

/**
 * A villager's day (companion/VILLAGERS.md, "A day in the village", the app's Routine.kt): when they get up and go to bed,
 * "H:MM" on the village's clock ("0:30" is after midnight), and their night rituals, the only reason to be out at night: a
 * short walk with a lantern to a place (a building type, a spot, the fire or the forest), what they do there, and home.
 */
const clockTime = z.string().regex(/^([01]?\d|2[0-3]):[0-5]\d$/, 'a time on the clock, "H:MM": "5:00", "21:30"')
/** What someone does at a ritual's place: stands with the lantern up, tends (the hives, the embers), carries (wood), watches. */
export const RITUAL_DOINGS = ['stand', 'tend', 'carry', 'watch'] as const
/** Where a ritual can go: where a villager can be (a building type, a spot), the fire and the forest. */
export const RITUAL_PLACES = [...HOMES, 'fire', 'forest']
const ritualSpec = z.object({
  id,
  at: clockTime,
  until: clockTime,
  to: z.enum(RITUAL_PLACES),
  doing: z.enum(RITUAL_DOINGS).default('stand'),
  /** "Luka pogleda ovce · Luka checks the sheep", or per language as a role. */
  title: label(120).optional(),
  lantern: z.boolean().optional(),
})
const routineSpec = z.object({ up: clockTime.optional(), bed: clockTime.optional(), rituals: z.array(ritualSpec).max(4).optional() })

/**
 * The roofs someone can sleep under, mirrored from Sleep.kt's ROOFS: the dwellings, and the workplaces with a bed (the smith's
 * cot by his forge, the teacher's alcove in the school).
 */
export const SLEEP_ROOFS = ['tent', 'hut', 'house', 'smithy', 'school', 'market', 'beehive'] as const
/**
 * Where a villager sleeps, when it isn't where they are by day (their `home`): a roof ("smithy", "school", "house") or a room
 * of one by its art, "house:cellar" (the cellar of the house that has it, a bed there; while no house has it, a house).
 */
const sleepsSpec = z.string().regex(new RegExp(`^(${SLEEP_ROOFS.join('|')})(:[a-z]+)?$`), `a roof (${SLEEP_ROOFS.join(', ')}) or a room of one by its art ("house:cellar")`)

/** Minutes after midnight of an "H:MM" (already checked). */
export const minutesOf = (t: string): number => {
  const [h, m] = t.split(':').map(Number)
  return h * 60 + m
}

export const villagerSpec = z.object({
  schema: schemaField('lani.villager/v0'),
  id,
  name: text.max(60),
  emoji: text.max(16).default('🙂'),
  art: z.enum(SCENE_ART.people),
  /** Their gender (grammar, and the fallback voice); defaults to the sprite's. */
  voice: z.enum(['female', 'male']).optional(),
  /** Their voice in the cast (companion/voice-cast.json): "grandma", "young-man"; else the gender's narrator. */
  speaker: z.string().regex(SPEAKER_ID).optional(),
  /** "Babica · Grandmother" (target · English), or per language: {"it": "Nonna", "sl": "Babica", "en": "Grandmother"}. */
  role: z.union([label(80), z.literal('')]).default(''),
  home: z.array(z.enum(HOMES)).max(6).default([]),
  /** How the learner addresses them: ti (Italian tu) or vi (the polite form: Slovene vi, Italian Lei). */
  register: z.enum(REGISTERS).default('vi'),
  personality: text.max(600).default(''),
  story: text.max(500).default(''),
  /**
   * "ovce · his sheep": target · English, so a talk can hint at them; plain English is allowed; or per language:
   * {"it": "le pecore", "sl": "ovce", "en": "his sheep"}.
   */
  likes: z.array(label(80)).max(8).default([]),
  skill: z.enum(RESOURCES).optional(),
  since: z.enum(SCENE_ART.ages).optional(),
  order: z.number().int().min(0).max(999).default(100),
  /**
   * An extra: a smaller person of the culture (the hunter) with fewer lines; moves in as a newcomer does, taking turns
   * with the names the village makes up, not with the cast (Residents.kt).
   */
  extra: z.boolean().optional(),
  lines: z.object({
    greet: lines, thanks: lines, remember: lines, idle: lines, cheer: lines, comfort: lines, listen: lines, bye: lines,
    gift: z.strictObject({ liked: giftLines, ordinary: giftLines, rare: giftLines }).optional(),
  }),
  /**
   * When they get up and go to bed, and their night rituals (optional: without it, their kind's, in the app). An older
   * bridge leaves it out, an older app ignores it.
   */
  routine: routineSpec.optional(),
  /**
   * Where they sleep, when it isn't where they are by day: "smithy" (Tone by his forge), "house:cellar" (Anton lodging in the
   * cellar). Optional: without it, the first roof of their home. An older bridge leaves it out, an older app ignores it.
   */
  sleeps: sleepsSpec.optional(),
})

export type VillagerLine = SpokenLine
export type GiftLines = Record<GiftKind, VillagerLine[]>
export type VillagerLines = Record<LineKind, VillagerLine[]> & { gift?: GiftLines }
export type Villager = Omit<z.infer<typeof villagerSpec>, 'lines'> & { voice: Gender; lines: VillagerLines }

/**
 * Whether who asks reads a line's `when`: an app that says the time of day on the phone (`?time=evening`, as it has since
 * lines have a `when`), or a bridge asking a linked town for a visit (it says its own). An older app refuses a villager
 * with a line it doesn't know, so it gets them [withoutWhen].
 */
export const readsWhen = (url: URL): boolean => url.searchParams.has('time')

/** [v] as someone who doesn't read `when` gets it ([readsWhen]): every line without it, so every line fits any time. */
export function withoutWhen<T extends { lines: object }>(v: T): T {
  return { ...v, lines: JSON.parse(JSON.stringify(v.lines, (k, x) => (k === 'when' ? undefined : x))) }
}

/** Every line a villager says, each kind's and their gift lines (the voice corpus, the counts). */
export function allLines(lines: VillagerLines): VillagerLine[] {
  return [...LINE_KINDS.flatMap(k => lines[k] ?? []), ...GIFT_KINDS.flatMap(k => lines.gift?.[k] ?? [])]
}

export type VillagerSource = 'curated' | 'tutor'
export type LoadedVillager = Villager & { source: VillagerSource; published_at?: number }

/**
 * What the shape alone can't say: remember lines carry {memory}, the others don't; homes once each; at most six lines of
 * a kind for one part of the day; a greeting for a stranger at every part of the day.
 */
export function crossCheck(v: z.infer<typeof villagerSpec>): string[] {
  const errs: string[] = []
  const seen = new Set<string>()
  for (const h of v.home) {
    if (seen.has(h)) errs.push(`home "${h}" is listed twice`)
    seen.add(h)
  }
  for (const kind of LINE_KINDS) {
    for (const p of PARTS) {
      const n = (v.lines[kind] as VillagerLine[]).filter(l => fits(l, p)).length
      if (n > MAX_AT_ONCE) errs.push(`lines.${kind}: ${n} lines for the ${p}, at most ${MAX_AT_ONCE} (a line with "when" counts only at its times)`)
    }
  }
  for (const kind of LINE_KINDS) {
    ;(v.lines[kind] as VillagerLine[]).forEach((l, i) => {
      const texts = lineLangs(l).map(k => [k, l[k] as string] as const)
      if (kind === 'remember') {
        if (!texts.some(([k, s]) => k !== 'en' && s.includes('{memory}'))) errs.push(`lines.remember[${i}]: a remember line must contain {memory}`)
        else for (const [k, s] of texts) if (!s.includes('{memory}')) errs.push(`lines.remember[${i}]: the ${k === 'en' ? 'English' : `"${k}"`} needs {memory} too`)
      } else if (texts.some(([, s]) => /\{/.test(s))) errs.push(`lines.${kind}[${i}]: only remember lines may contain {memory}`)
    })
  }
  for (const kind of GIFT_KINDS) {
    ;(v.lines.gift?.[kind] ?? []).forEach((l, i) => {
      if (lineLangs(l).some(k => /\{/.test(l[k] as string))) errs.push(`lines.gift.${kind}[${i}]: a gift line is said as it is (no {…})`)
    })
  }
  errs.push(...routineErrors(v.routine))
  const room = v.sleeps?.split(':')[1]
  if (room !== undefined && !ARTS.includes(room)) errs.push(`sleeps: "${v.sleeps}" names no room the app draws (${ARTS.join(', ')})`)
  if (!v.lines.greet.some(l => l.level === 0)) errs.push('lines.greet: a stranger needs a greeting (a line with level 0)')
  else {
    // a greeting bound to a time of day ("Dober dan" by day) leaves the other times to one of its own or one of any time
    const bare = PARTS.filter(p => !v.lines.greet.some(l => l.level === 0 && fits(l, p)))
    if (bare.length) errs.push(`lines.greet: a stranger isn't greeted in the ${bare.join(', ')}: every level-0 greeting has a "when" without it (add one for it, "Dober večer" in the evening, or one without "when")`)
  }
  return errs
}

/**
 * A routine's sense: they get up in the morning (3:00–10:59) and go to bed in the evening (18:00) or a little after midnight
 * (before 3:00); a ritual is at night (from 19:00 until 6:00, while they'd be asleep or on the way to bed), lasts 10 to 60
 * minutes, is over before they get up, and each has its own id.
 */
export function routineErrors(r: z.infer<typeof routineSpec> | undefined): string[] {
  if (!r) return []
  const errs: string[] = []
  const up = r.up === undefined ? undefined : minutesOf(r.up)
  const bed = r.bed === undefined ? undefined : minutesOf(r.bed)
  if (up !== undefined && (up < 3 * 60 || up >= 11 * 60)) errs.push(`routine.up: "${r.up}" is no time to get up (3:00–10:59)`)
  if (bed !== undefined && bed < 18 * 60 && bed >= 3 * 60) errs.push(`routine.bed: "${r.bed}" is no bedtime (18:00–2:59)`)
  const seen = new Set<string>()
  for (const [i, x] of (r.rituals ?? []).entries()) {
    const where = `routine.rituals[${i}] "${x.id}"`
    if (seen.has(x.id)) errs.push(`${where}: its id is taken`)
    seen.add(x.id)
    const at = minutesOf(x.at)
    const long = (minutesOf(x.until) - at + 1440) % 1440
    if (at < 19 * 60 && at >= 6 * 60) errs.push(`${where}: at ${x.at} it isn't night (a ritual is from 19:00 until 6:00: by day they're about anyway)`)
    if (long < 10 || long > 60) errs.push(`${where}: ${long} minutes (10 to 60)`)
    if (up !== undefined && at < up && at + long > up) errs.push(`${where}: runs past ${r.up}, when they get up`)
  }
  return errs
}

/**
 * What publishing checks beyond the shape: the speaker is in the voice cast, with the villager's gender.
 * (Loading doesn't: a speaker the cast lost falls back to the gender voice.)
 */
export function speakerErrors(v: Pick<Villager, 'voice' | 'speaker'>): string[] {
  if (!v.speaker) return []
  if (!isSpeaker(v.speaker)) return [`speaker "${v.speaker}" is not in the voice cast (${Object.keys(cast()).join(', ')})`]
  const g = genderOf(v.speaker)
  return g === v.voice ? [] : [`speaker "${v.speaker}" is ${g}, the villager's voice is ${v.voice}`]
}

export function validateVillager(input: unknown): { ok: true; villager: Villager } | { ok: false; errors: string } {
  const r = villagerSpec.safeParse(input)
  if (!r.success) return { ok: false, errors: z.prettifyError(r.error) }
  const errs = crossCheck(r.data)
  if (errs.length) return { ok: false, errors: errs.map(e => `✖ ${e}`).join('\n') }
  return { ok: true, villager: { ...r.data, voice: r.data.voice ?? spriteVoice(r.data.art) } }
}

export class VillagerStore {
  private warned = new Set<string>()

  constructor(
    private curatedDir: string,
    private tutorDir: string,
    private log: (...a: unknown[]) => void = () => {},
  ) {
    mkdirSync(tutorDir, { recursive: true })
  }

  private read(dir: string, source: VillagerSource): LoadedVillager[] {
    if (!existsSync(dir)) return []
    return readdirSync(dir)
      .filter(f => f.endsWith('.json'))
      .sort()
      .flatMap(f => {
        const path = join(dir, f)
        try {
          const v = validateVillager(JSON.parse(readFileSync(path, 'utf8')))
          if (!v.ok) throw new Error(v.errors)
          const published_at = source === 'tutor' ? statSync(path).mtimeMs : undefined
          return [{ ...v.villager, source, published_at }]
        } catch (e) {
          const key = `${path}:${statSync(path).mtimeMs}`
          if (!this.warned.has(key)) this.log(`skipping villager ${path}: ${(e as Error).message}`)
          this.warned.add(key)
          return []
        }
      })
  }

  /** Everyone, one per id, in arrival order: a tutor villager replaces the curated one with the same id. */
  all(): LoadedVillager[] {
    const tutor = this.read(this.tutorDir, 'tutor')
    const ids = new Set(tutor.map(v => v.id))
    return [...this.read(this.curatedDir, 'curated').filter(v => !ids.has(v.id)), ...tutor].sort((a, b) => a.order - b.order)
  }

  get(id: string): LoadedVillager | undefined {
    return this.all().find(v => v.id === id)
  }

  /** The culture's cast as it came, in arrival order, without the tutor's (what a visiting town sees: visits.ts). */
  curated(): LoadedVillager[] {
    return this.read(this.curatedDir, 'curated').sort((a, b) => a.order - b.order)
  }

  isCurated(id: string): boolean {
    return this.read(this.curatedDir, 'curated').some(v => v.id === id)
  }

  /** Writes a tutor villager (same id = replace; a curated id is replaced too, until remove()). */
  publish(v: Villager): void {
    const path = join(this.tutorDir, `${v.id}.json`)
    writeFileSync(`${path}.tmp`, JSON.stringify(v, null, 2) + '\n')
    renameSync(`${path}.tmp`, path)
  }

  /** Removes a tutor villager (a curated one with that id shows again). False when there is none. */
  remove(id: string): boolean {
    if (!/^[a-z0-9][a-z0-9-]{0,62}$/.test(id)) return false
    const path = join(this.tutorDir, `${id}.json`)
    if (!existsSync(path)) return false
    unlinkSync(path)
    return true
  }
}

// --- friendship, from the village state ---------------------------------------------------------

export type Memory = { on?: string; sl: string; en?: string; kind?: string }
export type Bond = { points: number; memories: Memory[]; met?: string; seen?: string }

/** What Jan and villager [id] share, from the app's game state (GameState.bonds); a stranger when nothing is saved. */
export function bondOf(game: GameDoc | undefined, id: string): Bond {
  const b = (game?.state as any)?.bonds?.[id]
  const memories = Array.isArray(b?.memories) ? b.memories.filter((m: any) => m && typeof m.sl === 'string') : []
  return { points: typeof b?.points === 'number' ? b.points : 0, memories, met: b?.met, seen: b?.seen }
}

export function bondLevel(points: number): number {
  let l = 0
  BOND_THRESHOLDS.forEach((t, i) => {
    if (points >= t) l = i
  })
  return l
}

/**
 * The warmest line of [pool] the friendship allows (Bonds.linesFor), or the first one; given the [part] of the day, only
 * the lines that fit it (a line's `when`), and undefined when none does.
 */
export function lineFor(pool: VillagerLine[], level: number, part?: Part): VillagerLine | undefined {
  const fit = part ? pool.filter(l => fits(l, part)) : pool
  const open = fit.filter(l => l.level <= level).sort((a, b) => b.level - a.level)
  return open[0] ?? fit[0]
}

/**
 * A plain greeting for the part of the day, for someone with none of their own that fits it (as the app's
 * VillagerLogic.hello): good day by day, good evening from the evening on.
 */
export function helloFor(part: Part): VillagerLine {
  return part === 'morning' || part === 'afternoon'
    ? { sl: 'Dober dan!', it: 'Buongiorno!', de: 'Guten Tag!', en: 'Good day!', level: 0 }
    : { sl: 'Dober večer!', it: 'Buonasera!', de: 'Guten Abend!', en: 'Good evening!', level: 0 }
}

// --- where a talk happens --------------------------------------------------------------------------

/**
 * How each language says ti and vi (the register's two forms): Italian tu and Lei, German du and Sie. English has one
 * "you": its villages say first names, and the polite register is "Mr" or "Mrs" and the surname (the lakeland pack's
 * Mr Dixon).
 */
const REGISTER_WORDS: Record<string, { ti: string; vi: string }> = {
  sl: { ti: 'ti', vi: 'vi' },
  it: { ti: 'tu', vi: 'Lei' },
  de: { ti: 'du', vi: 'Sie' },
  en: { ti: 'first names', vi: '"Mr" or "Mrs" and the surname' },
}
export const registerWord = (lang: string, r: 'ti' | 'vi') => (REGISTER_WORDS[lang] ?? REGISTER_WORDS.sl)[r]

/** A talk's hints in each language, by register: how are you, fine and you, goodbye. */
const PHRASES: Record<string, Record<'how' | 'fine' | 'bye', { ti: string; vi: string }>> = {
  sl: { how: { ti: 'Kako si?', vi: 'Kako ste?' }, fine: { ti: 'Dobro, hvala. In ti?', vi: 'Dobro, hvala. In vi?' }, bye: { ti: 'Adijo!', vi: 'Na svidenje!' } },
  it: { how: { ti: 'Come stai?', vi: 'Come sta?' }, fine: { ti: 'Bene, grazie. E tu?', vi: 'Bene, grazie. E Lei?' }, bye: { ti: 'Ciao!', vi: 'Arrivederci!' } },
  de: { how: { ti: "Wie geht's dir?", vi: 'Wie geht es Ihnen?' }, fine: { ti: 'Gut, danke. Und dir?', vi: 'Gut, danke. Und Ihnen?' }, bye: { ti: 'Servus!', vi: 'Auf Wiedersehen!' } },
  en: { how: { ti: 'How are you?', vi: 'How are you?' }, fine: { ti: 'Fine, thanks. And you?', vi: 'Fine, thanks. And you?' }, bye: { ti: 'Goodbye!', vi: 'Goodbye!' } },
}

/**
 * Where a talk with a villager happens, and in which languages: the village's language ([target], the culture pack's),
 * the learner's base ([base], what translations are in), the learner's name, what the tutor is told about the place
 * (the pack's `tutor`), and the plain lines of the pack's newcomers ([plain], people.json).
 */
export type TalkPlace = {
  target: string
  base: string
  learner: string
  tutor: CultureTutor
  plain?: { adult: Partial<Record<LineKind, VillagerLine[]>>; child: Partial<Record<LineKind, VillagerLine[]>> }
}

/** Jan's: Slovene explained in English, in Primorska. */
export const JAN_PLACE: TalkPlace = { target: 'sl', base: 'en', learner: 'Jan', tutor: PRIMORSKA_TUTOR }

/**
 * Someone who lives in the village without being in the cast (a newcomer or a child born there,
 * GameState.residents, see Residents.kt): enough of a villager to talk to, with the plain lines of the village's
 * culture pack (people.json; built-in Slovene ones when it has none).
 */
export function residentVillager(game: GameDoc | undefined, id: string, place: TalkPlace = JAN_PLACE): Villager | undefined {
  const r = ((game?.state as any)?.residents ?? []).find((x: any) => x?.id === id && typeof x.name === 'string')
  if (!r) return undefined
  const young = typeof r.born === 'string'
  const voice: Gender = r.voice === 'male' ? 'male' : 'female'
  // a line bound to a time of day says when ("Dober dan" by day, "Dober večer" from the evening on)
  const l = (sl: string, en: string, level = 0, when?: Part[]) => ({ sl, en, level, ...(when ? { when } : {}) })
  const [day, evening, notNight]: Part[][] = [['morning', 'afternoon'], ['evening', 'night'], ['morning', 'afternoon', 'evening']]
  const polite = registerWord(place.target, 'vi')
  const familiar = registerWord(place.target, 'ti')
  const plain = place.plain?.[young ? 'child' : 'adult']
  const own = (kind: LineKind, fallback: VillagerLine[]) => (plain?.[kind]?.length ? plain[kind]! : fallback)
  const built: Villager = {
    schema: 'lani.villager/v0',
    id,
    name: r.name,
    emoji: typeof r.emoji === 'string' ? r.emoji : '🙂',
    art: SCENE_ART.people.includes(r.art) ? r.art : young ? 'child1' : voice === 'male' ? 'man' : 'woman',
    voice,
    // A child's voice until they are a youth (Residents.Stage in the app: 240 days).
    speaker: newcomerSpeaker(voice, young && Date.now() - Date.parse(r.born) < 240 * 86_400_000),
    role: typeof r.role === 'string' ? r.role : '',
    home: ['house', 'hut', 'tent'],
    register: young ? 'ti' : 'vi',
    personality: young
      ? `A child of the village: quick, curious, simple words. Says ${familiar}.`
      : `Someone who moved into the village: friendly, plain-spoken, a little shy with a stranger. Says ${polite} at first.`,
    story: [r.family ? `Of the ${r.family} family.` : '', r.born ? `Born in the village on ${r.born}.` : r.since ? `In the village since ${r.since}.` : ''].filter(Boolean).join(' '),
    likes: [],
    order: 100,
    lines: young
      ? {
          greet: [l('Živjo!', 'Hi!'), l('Ej, Jan! Se greš igrat?', 'Hey, Jan! Coming to play?', 1)],
          thanks: [l('Hvala!', 'Thanks!'), l('Hvala, Jan!', 'Thanks, Jan!')],
          remember: [l('Se spomniš? {memory}!', 'Remember? {memory}!', 1), l('Še vedno mislim na {memory}.', 'I still think of {memory}.', 1)],
          idle: [l('Danes je lep dan za igro.', 'Today is a nice day for playing.', 0, notNight), l('Kje so ostali otroci?', 'Where are the other children?')],
          cheer: [l('Juhu!', 'Yay!'), l('Super!', 'Super!')],
          comfort: [l('Nič hudega!', 'Never mind!'), l('Še enkrat!', 'Once more!')],
          listen: [l('Poslušaj!', 'Listen!'), l('Psst, poslušaj.', 'Psst, listen.')],
          bye: [l('Adijo!', 'Bye!'), l('Čav!', 'Bye! (čav: Primorska for bye)')],
        }
      : {
          greet: [l('Dober dan!', 'Good day!', 0, day), l('Dober večer!', 'Good evening!', 0, evening), l('Živjo, Jan!', 'Hi, Jan!', 2)],
          thanks: [l('Hvala lepa!', 'Thank you very much!'), l('Res ste mi pomagali. Hvala!', 'You really helped me. Thanks!', 1)],
          remember: [l('Še vedno mislim na {memory}.', 'I still think of {memory}.', 1), l('Ne pozabim na {memory}.', 'I do not forget {memory}.', 2)],
          idle: [l('Lep dan je danes.', 'It is a nice day today.', 0, notNight), l('V vasi je vedno kaj za delati.', 'There is always something to do in the village.')],
          cheer: [l('Odlično!', 'Excellent!'), l('Tako je!', 'That is right!')],
          comfort: [l('Nič hudega. Poskusite še enkrat.', 'Never mind. Try once more.'), l('Skoraj. Še enkrat.', 'Almost. Once more.')],
          listen: [l('Dobro poslušajte.', 'Listen carefully.'), l('Tiho. Poslušajte.', 'Quiet. Listen.')],
          bye: [l('Na svidenje!', 'Goodbye!'), l('Adijo!', 'Bye! (Primorska)', 2)],
        },
  }
  if (plain) built.lines = Object.fromEntries(LINE_KINDS.map(k => [k, own(k, built.lines[k])])) as Record<LineKind, VillagerLine[]>
  return built
}

// --- talking to a villager ----------------------------------------------------------------------

export { parseLike }

/** Where they are, for the setting: "in the meadow", "at the smithy". */
function whereOf(v: Villager): string {
  const h = v.home[0]
  if (!h) return 'by the fire'
  if (h.startsWith('spot:')) return SPOTS[h.slice(5)]?.en ?? 'in the village'
  return `at the ${h === 'lipa' ? 'linden tree' : h === 'kozolec' ? 'hayrack' : h}`
}

const cut = (s: string, max: number) => (s.length <= max ? s : s.slice(0, max - 1).trimEnd() + '…')

/**
 * A role-play (lani.scenario/v0, id "villager:<id>") built from the villager for the current
 * friendship: the tutor plays them, the opener is their warmest greeting the learner has earned for the [part] of the
 * day (the phone's, else this node's: no "Dober dan" in the evening; a plain one for it when they have none), the goals
 * are open (greet, ask how they are, talk about what they like, say goodbye), the hints come from their likes.
 * Where it is and in which languages is [place] (the learner's culture pack; Jan's Primorska by default). The
 * scenario format's "_sl" fields carry the village's language and "_en" its translation into the learner's base
 * (their names are from before a learner could learn another language).
 */
export function villagerScenario(v: Villager, bond: Bond, level: Scenario['level'] = 'A1', place: TalkPlace = JAN_PLACE, part: Part = partNow()): Scenario {
  const { target, base, learner, tutor } = place
  const lvl = bondLevel(bond.points)
  const greet = lineFor(v.lines.greet, lvl, part) ?? helloFor(part)
  const talkAbout = v.likes
    .slice(0, 2)
    .map(l => labelEn(l).split(' (')[0])
    .join(', ')
  const you = v.register === 'vi' ? 'vi' : 'ti'
  const say = PHRASES[target] ?? PHRASES.sl
  const gloss = PHRASES[base] ?? PHRASES.en
  const phrase = (k: 'how' | 'fine' | 'bye') => ({ sl: say[k][you], en: gloss[k][you] })
  const likeIn = (l: Label, lang: string) => (typeof l === 'string' ? (lang === 'en' ? labelEn(l) : undefined) : l[lang])
  const hints: { sl: string; en: string }[] = [
    phrase('how'),
    phrase('fine'),
    ...v.likes.flatMap(l => {
      const t = labelTarget(l, target)
      return t ? [{ sl: t, en: (base !== target && likeIn(l, base)) || labelEn(l) }] : []
    }),
    phrase('bye'),
  ]
  return {
    schema: 'lani.scenario/v0',
    id: `villager:${v.id}`,
    title: v.name,
    emoji: v.emoji,
    level,
    setting: cut(
      `${tutor.setting.replaceAll('{learner}', learner)}. You meet ${v.name} ${whereOf(v)}. ` +
        `Friendship: ${BOND_NAMES[lvl]}${bond.memories.length ? `, ${bond.memories.length} shared memor${bond.memories.length === 1 ? 'y' : 'ies'}` : ''}.`,
      400,
    ),
    role: cut(
      `${v.name}, ${labelEn(v.role) || 'a villager'} of ${tutor.village}. ${you === 'vi' ? `${learner} says ${registerWord(target, 'vi')} to them.` : `Everyone says ${registerWord(target, 'ti')} here.`} ${v.personality}`,
      300,
    ),
    voice: v.voice,
    ...(speakerFor(v) !== v.voice ? { speaker: speakerFor(v) } : {}),
    goals: [
      cut(`Greet ${v.name}${you === 'vi' ? ` politely (${registerWord(target, 'vi')})` : ` (${registerWord(target, 'ti')})`}`, 80),
      'Ask how they are',
      cut(talkAbout ? `Talk about something they like (${talkAbout})` : 'Talk about something they like', 80),
      'Say goodbye',
    ],
    opener_sl: said(greet, target),
    opener_en: meant(greet, target, base) || said(greet, target),
    vocabulary_hints: hints.slice(0, 12),
  }
}

/** The persona the tutor gets on every villager role-play turn: who they are, the friendship, what they remember. */
export function villagerBrief(v: Villager, bond: Bond) {
  const lvl = bondLevel(bond.points)
  return {
    id: v.id,
    name: v.name,
    role: v.role,
    register: v.register,
    personality: v.personality,
    story: v.story,
    likes: v.likes,
    friendship: { level: lvl, name: BOND_NAMES[lvl], points: bond.points, met: bond.met, seen: bond.seen },
    memories: bond.memories.slice(-6).map(m => ({ on: m.on, sl: m.sl, en: m.en })),
  }
}

/** The learner's level from the profile (cheap: the file, not read-db.py); A1 when unknown. */
export function learnerLevel(dataDir: string): Scenario['level'] {
  try {
    const l = JSON.parse(readFileSync(join(dataDir, 'learner-profile.json'), 'utf8'))?.learner?.current_level
    return ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'].includes(l) ? l : 'A1'
  } catch {
    return 'A1'
  }
}

/**
 * Everything a talk with villager [id] needs: the villager (cast, or a resident of the village), the
 * role-play built for the current friendship and the [part] of the day, and the brief for the tutor. Undefined for
 * nobody.
 */
export function villagerTalk(store: VillagerStore, game: GameDoc | undefined, id: string, level: Scenario['level'], place: TalkPlace = JAN_PLACE, part: Part = partNow()) {
  const cast = store.get(id)
  const v = cast ?? residentVillager(game, id, place)
  if (!v) return undefined
  const bond = bondOf(game, id)
  // the opener names only people the learner knows (as the app's lines: companion/VILLAGERS.md, "Who a text may name")
  const known = knownIds(game?.state)
  const names = known && new Names([...store.all(), ...residentsNamed(game?.state)], place.target)
  const opens = (l: VillagerLine) => !known || !names || [...names.of(said(l, place.target))].every(n => n === v.id || known.has(n))
  const speaking = known ? { ...v, lines: { ...v.lines, greet: v.lines.greet.filter(opens) } } : v
  return { villager: v, source: cast?.source ?? ('village' as const), scenario: villagerScenario(speaking, bond, level, place, part), brief: villagerBrief(v, bond) }
}
