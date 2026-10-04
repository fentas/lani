// lani.culture/v0: culture packs (companion/cultures/<id>/, described in companion/GAME.md "Culture packs").
// A pack is everything that makes a village a place, in its language: the people (its villagers/ directory, the
// cast), the requests they make, the feasts, the surprises at the road, the goods and tools, the projects' words,
// the event flavour, the names of newcomers and of the village's resources, ages and buildings, the chronicle's
// phrasings. Every text is {"<language>": "…"}: the pack's language, then translations into the base languages it
// serves.
//
// The app bundles the packs and plays them (game/culture/Cultures.kt, which checks them too, messages' arguments and
// the game's projects included). The bridge knows which one its learner's village is in (the profile's culture,
// LANI_CULTURE; primorska by default), serves that pack's cast, word packs and scenes (its scenes/: the places only
// it has), and tells the app (GET /culture).
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import { schemaField } from './schema'
import { arrivalErrors, cultureArrivals, PACK_LANGUAGES, type ArrivalBody } from './arrivals'
import { grammarRef } from './grammar'
import { label, spokenLine, type Label } from './langs'
import { SCENE_ART, RESOURCES, SPOT_NAMES, TIMES, checkNeeds, dialogGrammar, dialogLine, validateScene, type SceneProject, type SceneRefs } from './scenes'
import { readReadings, type Reading } from './readings'
import { cultureSky } from './sky'
import { CURATED_LEVELS, LEVELS, chaptersOf, curatedPictureErrors, validateStory } from './stories'

export const SCHEMA = 'lani.culture/v0'
export const DEFAULT_CULTURE = 'primorska'
export const CULTURE_ID = /^[a-z][a-z0-9-]{0,39}$/
/** The files of a complete pack besides culture.json. */
export const FILES = ['world', 'quests', 'festivals', 'surprises', 'chest', 'projects', 'events', 'people', 'chronicle'] as const
/** Mirrored from Catalog.kt (GOOD_PRICES, TOOL_EFFECTS): what the game has numbers for. */
export const PRICES = ['small', 'plain', 'good', 'fine', 'rich', 'precious'] as const
export const TOOL_EFFECTS = ['wood', 'food', 'stone', 'wisdom', 'thrift', 'warning', 'morale', 'quest_pay', 'trade', 'stores', 'keepsake_morale', 'keepsake_warning'] as const
/** The app's languages (l10n/Lang.kt): a pack is in one of them. */
export const APP_LANGUAGES = ['sl', 'en', 'it', 'de'] as const

const LANG = /^[a-z]{2,3}$/
const str = z.string().trim().min(1)
/** {"sl": "…", "en": "…"} */
const text = z.record(z.string().regex(LANG, 'a language code (sl, en, it …)'), str).refine(t => Object.keys(t).length > 0, 'a text in at least one language')
const id = z.string().regex(/^[a-z0-9][a-z0-9_-]{0,62}$/, 'lowercase letters, digits, - and _')
const obj = <T extends z.ZodRawShape>(shape: T) => z.strictObject(shape)
const names = (keys: readonly string[]) => obj(Object.fromEntries(keys.map(k => [k, obj({ name: text, partitive: text.optional(), leader: str.optional() })])) as Record<string, z.ZodTypeAny>)

export const manifestSpec = obj({
  schema: schemaField(SCHEMA),
  id: z.string().regex(CULTURE_ID, 'lowercase letters, digits and -'),
  language: z.enum(APP_LANGUAGES),
  status: z.enum(['complete', 'stub']).default('complete'),
  region: text,
  name: text,
  about: text.optional(),
  /** Who wrote the pack's language and who checked it: "machine-written, not reviewed by a native speaker". */
  review: str.optional(),
  /**
   * What the tutor is told about the place, in English: the village's [village] name, the role-play [setting]
   * ({learner}: the learner's name), the [style] newcomers are written in, and the [voice] a designed voice speaks with.
   */
  tutor: obj({ village: str, setting: str, style: str, voice: str }).optional(),
})
export type Manifest = z.infer<typeof manifestSpec>

/** [read]: a reading of the pack (readings/<id>.json) the item holds: a recipe, a book's page. */
const item = obj({ emoji: str, name: text, acc: text.optional(), the: text.optional(), read: id.optional() })
const options = z.array(str).min(2)
const line = spokenLine(200)
const lines = obj(Object.fromEntries(['greet', 'thanks', 'remember', 'idle', 'cheer', 'comfort', 'listen', 'bye'].map(k => [k, z.array(line).optional()])) as Record<string, z.ZodTypeAny>)
const stranger = obj({
  id, name: str, emoji: str, art: z.enum(SCENE_ART.people), voice: z.enum(['female', 'male']), role: label(80),
  home: z.array(str), register: z.enum(['ti', 'vi']).optional(), story: str.optional(), lines,
})
const events = obj({ leader: str.optional(), title: text, title_walled: text.optional(), intro: text, won: text, lost: text, gift: text.optional(), quiet: text.optional() })
const gendered = obj({ female: text, male: text })
/** A keeper's talk at each level: a scene dialog's lines (3-12), his own `who` his id. */
const keeperLevels = z
  .partialRecord(z.enum(LEVELS), obj({ lines: z.array(dialogLine).min(3).max(12) }))
  .refine(l => Object.keys(l).length > 0, 'at least one level: {"A1": {"lines": […]}}')
/**
 * A talk of the story a keeper tells across visits, after the first one (the meeting, his `levels`): its [id] (the
 * learner's place in the story is a count, so a learner whose level rises keeps it), the bubble's [title] while it is the
 * one he has (else his own), its [levels], and [again]: how he tells it on a later round of the story (the next pile's,
 * varied by small things: the weather, the season, the village), each at its levels; round after round he tells the
 * next of `levels` and these, in turn. An older bridge refuses the key (strict): the code goes out before the content.
 */
const keeperTalk = obj({ id, title: text.optional(), levels: keeperLevels, again: z.array(obj({ levels: keeperLevels })).default([]) })
/**
 * Someone at a spot of the landscape now and then who doesn't live in the village (the charcoal burner who sits by his kopa
 * some evenings): staged like a villager, there in the parts of the day he comes ([when]) on a day the dice give him
 * ([chance]), his bubble's [title], and a short talk at each level (a scene dialog's lines, his own `who` his [id]) that
 * pays [reward] once a day: the first time, [levels] (the meeting), then his story ([talks]), one talk a day he is there
 * and it is done, in their order, and after the last the first again in its next telling (`again`), never the meeting.
 * No friendship, no introduction (companion/VILLAGERS.md, "At the charcoal pile").
 */
const keeper = obj({
  id, name: str, emoji: str, art: z.enum(SCENE_ART.people), voice: z.enum(['female', 'male']).default('male'), speaker: str.optional(),
  role: text.optional(), story: str.optional(), when: z.array(z.enum(TIMES)).default([]), chance: z.number().min(0).max(1).default(1),
  title: text, reward: z.partialRecord(z.enum(RESOURCES), z.number().int().min(1).max(50)).default({}),
  levels: keeperLevels,
  talks: z.array(keeperTalk).default([]),
})
export type Keeper = z.infer<typeof keeper>
/** One telling of a keeper's talk at one level: its lines. */
export type KeeperTelling = NonNullable<Keeper['levels'][(typeof LEVELS)[number]]>
/**
 * A spot of the landscape as the culture tells it (world.json `spots`, by a spot's name: "kopa"): its name and [where] on
 * its card and bubbles (they win over the app's own), the card's line ([about]), a few sentences of its history by level
 * ([story]), the word [pack] its words are in ([words]: the card's, else all), who wrote it ([review]), and who is there
 * now and then ([keeper]).
 */
const spot = obj({
  emoji: str.optional(), name: text, where: text, about: text.optional(),
  story: z.partialRecord(z.enum(LEVELS), text).default({}),
  pack: id.optional(), words: z.array(id).default([]), review: str.optional(), keeper: keeper.optional(),
})
/** The levels a culture's spot tells its story at, and its keeper talks at: A1 and A2 at least. */
export const SPOT_LEVELS = ['A1', 'A2'] as const

export const fileSpecs = {
  world: obj({
    host: obj({ name: str, emoji: str }),
    resources: names(RESOURCES),
    ages: names(SCENE_ART.ages),
    buildings: names(SCENE_ART.buildings),
    events: names(['wolves', 'bear', 'storm', 'merchant', 'festival']),
    places: z.array(obj({ id, name: text, about: text.optional() })).default([]),
    /**
     * What lies at the village's horizon, a place the learner can go (its scenes open from "spot:horizon"): the [name]
     * the place card shows, [where] (in a sentence: "na gričih"), [about] (the card's line), and [sea]: the village's
     * backdrop shows the sea along the horizon.
     */
    backdrop: obj({ emoji: str, name: text, where: text, about: text.optional(), sea: z.boolean().default(false) }).optional(),
    /** The culture's own texts for spots of the landscape (the charcoal pile), by spot: see `spot`. An older bridge rejects the key. */
    spots: z.partialRecord(z.enum(SPOT_NAMES), spot).default({}),
  }),
  quests: obj({
    // memory: how the giver remembers the learner's help, fitting a remember line; without it the app makes one;
    // grammar: the grammar book's page of the rule the request is about (grammar.ts), which playing it unlocks
    requests: z.array(obj({ giver: str, emoji: str, skill: z.enum(RESOURCES), title: text, story: text, topics: z.array(str).default([]), memory: text.optional(), grammar: grammarRef.optional() })).min(1),
    lines: obj({ asks: text, expired: text, thanked: text, try_again: text, thanks_good: text }),
    // the shepherd's request to move the tent out of the village to the pond (the app's game/TentMove.kt): his
    // description of the place (said) and the questions about it, the answer first, each with the grammar book's page
    // of its rule (grammar.ts: "kje-mestnik-orodnik"); moved: the chronicle's line
    tent_move: obj({
      giver: str, emoji: str, skill: z.enum(RESOURCES).default('wood'), title: text, story: text, memory: text.optional(),
      said: text, listen: text, read: text, heard: text, questions: z.array(obj({ ask: text, options, explain: text, grammar: grammarRef.optional() })).min(1), moved: text,
    }).optional(),
  }),
  festivals: obj({
    festivals: z.array(obj({
      id, emoji: str,
      date: obj({ month: z.number().int().min(1).max(12).optional(), day: z.number().int().min(1).max(31).optional(), easter: z.number().int().optional(), last: z.enum(['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday']).optional() })
        .refine(d => [d.easter !== undefined, d.last !== undefined, d.day !== undefined].filter(Boolean).length === 1 && (d.easter !== undefined || d.month !== undefined), 'one of {"month", "day"}, {"easter": days} or {"month", "last": weekday}'),
      name: text, leaders: z.array(id).min(1), ask: text, about: text, line: text, good: id.optional(), goods: z.number().int().min(1).default(1), words: id,
    })),
    lines: obj({ tag: text, means: text, say: text, tried: text }),
  }),
  // a letter's question and a way to show the pilgrim may name the grammar book's page of their rule (grammar), as the
  // tent's questions do: answering unlocks it
  surprises: obj({
    pedlar: obj({ emoji: str, title: text, intro: text, ask: text, goods: z.array(id).min(1) }),
    letter: obj({
      emoji: str, title: text, intro: text, intro_mine: text, ask: text, ask_mine: text, read: text, won: text, won_mine: text, lost: text,
      letters: z.array(obj({ to: id, to_name: text.optional(), female: z.boolean().default(false), text, questions: z.array(obj({ ask: text, options, explain: text, grammar: grammarRef.optional() })).min(1) })).min(1),
    }),
    lamb: obj({ emoji: str, shepherd: id, title: text, intro: text, ask: text, where: text, heard: text, listen: text, read: text, won: text, lost: text, places: z.array(text).min(4) }),
    pilgrim: obj({ emoji: str, title: text, intro: text, ask: text, prompt: text, show: text, won: text, lost: text, ways: z.array(obj({ say: text, options, explain: z.array(text).min(1), grammar: grammarRef.optional() })).min(1) }),
    riddle: obj({ emoji: str, child: id, title: text, intro: text, ask: text, won: text, lost: text, riddles: z.array(obj({ riddle: text, options, answer: text, meaning: text })).min(1) }),
    strangers: obj({ pedlar: stranger, pilgrim: stranger }),
  }),
  chest: obj({
    goods: z.array(obj({ id, emoji: str, name: text, acc: text.optional(), the: text.optional(), price: z.enum(PRICES), food: z.boolean().default(true), rare: z.boolean().default(false), adult: z.boolean().default(false), read: id.optional() })).min(1),
    tools: z.array(obj({ giver: id, giver_name: str, effect: z.enum(TOOL_EFFECTS), forge: z.boolean().default(false), first: item, better: item })),
    thanks: z.record(id, id),
    // what a villager thanks with in turn (their `thanks` good first, 2 to 4 of the chest's everyday goods) and in a
    // festival's season (festival id → good, a rare one too): the app's Chest.thanksFor. A bridge from before the key
    // rejects it (its check at start only logs it; guests.ts reads the chest loosely), an app from before it never
    // reads it (it bundles the pack it was built with).
    thanks_rotation: z.record(id, obj({ goods: z.array(id).min(2).max(4), seasonal: z.record(id, id).default({}) })).default({}),
    thanks_default: id,
    small_gifts: z.array(id).min(1),
    likes: z.record(id, z.array(id)),
    likes_default: z.array(id),
    children: z.array(id),
    smith: obj({ id, name: str, title: text }),
    sellers: obj({ pedlar: obj({ emoji: str, name: text }), merchant: obj({ emoji: str, name: text }), market: obj({ emoji: str, name: text }) }),
    // gift_memory: how a villager remembers a good the learner gave them ({item}: the good's "the" form), fitting a
    // remember line ("Še vedno mislim na {memory}"); without it the app says it as Primorska always has.
    lines: obj({ gift_tool: text, gift: text, given: text, bought: text, sold: text, forged: text, feast: text, gift_memory: text.optional() }),
  }),
  projects: obj({
    projects: z.array(obj({ id, leader: id, helpers: z.array(id).default([]), name: text, in_text: text.optional(), about: text, steps: z.array(obj({ task: text, line: text })).min(1), done: text, memory: text, where: text.optional() })),
    lines: obj({ try_again: text }),
  }),
  events: obj({ wolves: events, bear: events, storm: events, merchant: events, festival: events, too_late: text }),
  people: obj({
    first_names: obj({ female: z.array(str).min(1), male: z.array(str).min(1) }),
    surnames: z.array(str).min(1),
    trades: z.array(obj({ needs: z.enum(SCENE_ART.buildings).optional(), female: text, male: text })).min(1),
    stages: obj({ baby: gendered, child: gendered, youth: gendered }),
    newborn: text,
    story: text,
    lines: obj({ adult: lines, child: lines }),
    news: obj({ moved_in_cast: text, born: text, moved_in: text, left: text }),
  }),
  chronicle: obj(Object.fromEntries(['founded', 'built', 'upgraded', 'repaired', 'new_age', 'felled', 'felled_more', 'quarried', 'quarried_more', 'picked', 'picked_more', 'waited', 'hungry', 'cold', 'joined', 'left'].map(k => [k, text])) as Record<string, z.ZodTypeAny>),
} as const

export type Pack = { manifest: Manifest; files: { [K in (typeof FILES)[number]]: z.infer<(typeof fileSpecs)[K]> } }

// --- readings: what a chest item holds to read (readings/<id>.json; the format is readings.ts's) ----------------------

export { READING_KINDS, READING_LEVELS, readingSpec, type Reading } from './readings'

/** The ids of the readings pack [cultureId]'s chest names (its tools' and goods' `read`), with where. */
export function readingRefs(chest: Pack['files']['chest']): [string, string][] {
  const refs: [string, string][] = []
  chest.tools.forEach((t, i) => (['first', 'better'] as const).forEach(k => t[k].read && refs.push([`chest.tools[${i}].${k}.read`, t[k].read])))
  chest.goods.forEach((g, i) => g.read && refs.push([`chest.goods[${i}].read`, g.read]))
  return refs
}

/**
 * The readings of pack [cultureId] (readings/*.json), each checked (readings.ts, readReadings): its shape, named for its
 * id, every text in the pack's language [lang] and in every language its title has (a translation of every line), a
 * word question's word in the text, who reads it in the [cast]. The chest's things hold some of them; the reading corner
 * lists them all.
 */
export function cultureReadings(culturesDir: string, cultureId: string, lang: string, cast?: { id: string }[]): { readings: Map<string, Reading>; errors: string[] } {
  return readReadings(join(cultureDir(culturesDir, cultureId), 'readings'), lang, `${cultureId}/readings`, cast, `${cultureId}/villagers`)
}

/**
 * Texts that needn't be in the pack's language: what's said in the base language (the pilgrim's way, a riddle's
 * answer), notes for writers and the tutor (about, story), a name's form inside a sentence (in_text). The strangers'
 * role and lines and the newcomers' lines are villager lines (lani.villager/v0, langs.ts): validateCulture checks
 * those for the pack's language itself.
 */
export const ANY_LANGUAGE = [
  /^culture\.about$/, /\.about$/, /^people\.story$/, /\.in_text$/, /^surprises\.pilgrim\.ways\[\d+\]\.say$/,
  /^surprises\.riddle\.riddles\[\d+\]\.(answer|meaning)$/, /^surprises\.strangers\./, /^people\.lines\./,
  // a wrong choice's why in a spot keeper's talk is read in the learner's base: every language but the pack's (spotErrors),
  // in his meeting and in every talk of his story, each telling
  /^world\.spots\.\w+\.keeper\.(talks\[\d+\]\.(again\[\d+\]\.)?)?levels\.\w+\.lines\[\d+\]\.choices\[\d+\]\.why$/,
]

/** Every text (an object of language codes → strings) in [v], with its path. */
export function texts(v: unknown, path: string, out: [string, Record<string, string>][] = []) {
  if (Array.isArray(v)) v.forEach((x, i) => texts(x, `${path}[${i}]`, out))
  else if (v && typeof v === 'object') {
    const entries = Object.entries(v)
    if (entries.length && entries.every(([k, x]) => LANG.test(k) && typeof x === 'string')) out.push([path, v as Record<string, string>])
    else for (const [k, x] of entries) texts(x, `${path}.${k}`, out)
  }
  return out
}

export const cultureDir = (culturesDir: string, cultureId: string) => join(culturesDir, cultureId)

/** The packs in [culturesDir] (a directory with a culture.json each), sorted. */
export function cultureIds(culturesDir: string): string[] {
  if (!existsSync(culturesDir)) return []
  return readdirSync(culturesDir, { withFileTypes: true })
    .filter(d => d.isDirectory() && existsSync(join(culturesDir, d.name, 'culture.json')))
    .map(d => d.name)
    .sort()
}

/**
 * The packs in [culturesDir] whose village speaks [language], sorted: the ones that can be played, and the stubs (a
 * manifest alone, not played yet).
 */
export function culturesIn(culturesDir: string, language: string): { complete: string[]; stubs: string[] } {
  const out = { complete: [] as string[], stubs: [] as string[] }
  for (const id of cultureIds(culturesDir)) {
    const m = readManifest(culturesDir, id)
    if (m.ok && m.manifest.language === language) (m.manifest.status === 'complete' ? out.complete : out.stubs).push(id)
  }
  return out
}

/** [cultureId]'s manifest, or why there is none. */
export function readManifest(culturesDir: string, cultureId: string): { ok: true; manifest: Manifest } | { ok: false; errors: string } {
  if (!CULTURE_ID.test(cultureId)) return { ok: false, errors: `"${cultureId}" isn't a culture pack id` }
  const p = join(cultureDir(culturesDir, cultureId), 'culture.json')
  if (!existsSync(p)) return { ok: false, errors: `${cultureId}/culture.json: missing` }
  try {
    const r = manifestSpec.safeParse(JSON.parse(readFileSync(p, 'utf8')))
    if (!r.success) return { ok: false, errors: `${cultureId}/culture.json:\n${z.prettifyError(r.error)}` }
    if (r.data.id !== cultureId) return { ok: false, errors: `${cultureId}/culture.json: id "${r.data.id}", but the pack is "${cultureId}"` }
    return { ok: true, manifest: r.data }
  } catch (e) {
    return { ok: false, errors: `${cultureId}/culture.json: ${(e as Error).message}` }
  }
}

/** What a culture pack's people say when the learner gives them a good (the villager format's `lines.gift`). */
const GIFT_KINDS = ['liked', 'ordinary', 'rare'] as const

/**
 * One of a pack's cast as its check needs them: id and name, the role (the storyteller tells the stories), and (the
 * curated ones') gift lines.
 */
export type CastEntry = { id: string; name: string; source?: string; role?: Label; lines?: { gift?: Partial<Record<(typeof GIFT_KINDS)[number], object[]>> }; routine?: { rituals?: { title?: Label }[] } }

/**
 * The grammar book's pages [files] name, with where: the requests', the tent's questions', the letters' questions' and
 * the pilgrim's ways' `grammar` (each a page of the pack's language, companion/grammar/<language>).
 */
export function packGrammar(files: Pick<Pack['files'], 'quests' | 'surprises'>): [string, string][] {
  const out: [string, string][] = []
  const add = (where: string, g: string | undefined) => { if (g) out.push([where, g]) }
  files.quests.requests.forEach((r, i) => add(`quests.requests[${i}].grammar`, r.grammar))
  files.quests.tent_move?.questions.forEach((q, i) => add(`quests.tent_move.questions[${i}].grammar`, q.grammar))
  files.surprises.letter.letters.forEach((l, i) => l.questions.forEach((q, j) => add(`surprises.letter.letters[${i}].questions[${j}].grammar`, q.grammar)))
  files.surprises.pilgrim.ways.forEach((w, i) => add(`surprises.pilgrim.ways[${i}].grammar`, w.grammar))
  return out
}

/**
 * Reads and checks pack [cultureId]: every file's shape, the pack's language in every text, the goods its lists name,
 * its own scenes (scenes/: each a scene in the pack's language; given [refs], its words in the packs), its stories
 * (stories/: each told by its storyteller, in the pack's language, at A1 and A2 at least), its night sky (sky.json,
 * when it has one: sky.ts), and (given its [cast], the villagers' ids and names) the people its texts, scenes and
 * stories name, and its own people's gift lines (every kind, in its language). Given the grammar book's [pages] (the
 * ids of the pack's language), every page its requests, questions and scenes' choices name is one. A stub is its
 * manifest alone.
 */
export function validateCulture(culturesDir: string, cultureId: string, cast?: CastEntry[], refs?: Omit<SceneRefs, 'villagers'>, pages?: Set<string>):
  { ok: true; pack: Pack | { manifest: Manifest } } | { ok: false; errors: string } {
  const m = readManifest(culturesDir, cultureId)
  if (!m.ok) return m
  if (m.manifest.status === 'stub') return { ok: true, pack: { manifest: m.manifest } }
  const errors: string[] = []
  const files: Record<string, unknown> = {}
  for (const f of FILES) {
    const p = join(cultureDir(culturesDir, cultureId), `${f}.json`)
    if (!existsSync(p)) {
      errors.push(`${cultureId}/${f}.json: missing`)
      continue
    }
    try {
      const r = fileSpecs[f].safeParse(JSON.parse(readFileSync(p, 'utf8')))
      if (!r.success) errors.push(`${cultureId}/${f}.json:\n${z.prettifyError(r.error)}`)
      else files[f] = r.data
    } catch (e) {
      errors.push(`${cultureId}/${f}.json: ${(e as Error).message}`)
    }
  }
  if (errors.length) return { ok: false, errors: errors.join('\n') }
  const pack = { manifest: m.manifest, files } as Pack
  const lang = m.manifest.language
  for (const [path, t] of [...texts(m.manifest, 'culture'), ...FILES.flatMap(f => texts(pack.files[f], f))]) {
    if (!(lang in t) && !ANY_LANGUAGE.some(r => r.test(path))) errors.push(`${cultureId}: ${path}: no "${lang}" text (the pack's language)`)
  }
  // what people say is in the pack's language too (the villager format's lines: {"sl": …, "en": …}, {"it": …, "sl": …, "en": …})
  const said = (where: string, lines: Record<string, unknown>) => {
    for (const [kind, list] of Object.entries(lines)) ((list ?? []) as object[]).forEach((l, i) => {
      if (!(lang in (l as object))) errors.push(`${cultureId}: ${where}.${kind}[${i}]: no "${lang}" text (the pack's language)`)
    })
  }
  const { people: folk, surprises: road } = pack.files
  said('people.lines.adult', folk.lines.adult)
  said('people.lines.child', folk.lines.child)
  for (const [k, s] of Object.entries(road.strangers)) said(`surprises.strangers.${k}.lines`, s.lines)
  const { chest, festivals, surprises, projects, quests } = pack.files
  const goods = new Set(chest.goods.map(g => g.id))
  const good = (where: string, g: string | undefined) => {
    if (g !== undefined && !goods.has(g)) errors.push(`${cultureId}: ${where}: no good "${g}" in chest.json`)
  }
  for (const [who, g] of Object.entries(chest.thanks)) good(`chest.thanks.${who}`, g)
  good('chest.thanks_default', chest.thanks_default)
  // a rotation starts with the villager's thanks good, names every good once, none rare (a rare one only in a season),
  // and its seasons are festivals of the pack
  const rare = new Set(chest.goods.filter(g => g.rare).map(g => g.id))
  const feasts = new Set(festivals.festivals.map(f => f.id))
  for (const [who, r] of Object.entries(chest.thanks_rotation)) {
    const at = `chest.thanks_rotation.${who}`
    const first = chest.thanks[who]
    if (first === undefined) errors.push(`${cultureId}: ${at}: "${who}" has no thanks good (chest.thanks.${who} comes first in the rotation)`)
    else if (r.goods[0] !== first) errors.push(`${cultureId}: ${at}.goods[0]: "${r.goods[0]}", not their thanks good "${first}"`)
    if (new Set(r.goods).size !== r.goods.length) errors.push(`${cultureId}: ${at}.goods: a good twice`)
    r.goods.forEach((g, i) => {
      good(`${at}.goods[${i}]`, g)
      if (rare.has(g)) errors.push(`${cultureId}: ${at}.goods[${i}]: "${g}" is rare: only a seasonal thank-you may be`)
    })
    for (const [f, g] of Object.entries(r.seasonal)) {
      if (!feasts.has(f)) errors.push(`${cultureId}: ${at}.seasonal: no festival "${f}" in festivals.json`)
      good(`${at}.seasonal.${f}`, g)
    }
  }
  chest.small_gifts.forEach((g, i) => good(`chest.small_gifts[${i}]`, g))
  for (const [who, list] of Object.entries(chest.likes)) list.forEach((g, i) => good(`chest.likes.${who}[${i}]`, g))
  chest.likes_default.forEach((g, i) => good(`chest.likes_default[${i}]`, g))
  surprises.pedlar.goods.forEach((g, i) => good(`surprises.pedlar.goods[${i}]`, g))
  festivals.festivals.forEach((f, i) => good(`festivals.festivals[${i}].good`, f.good))
  // what the chest's things hold to read: each a reading of the pack
  const read = cultureReadings(culturesDir, cultureId, lang, cast)
  errors.push(...read.errors)
  for (const [where, r] of readingRefs(chest)) if (!read.readings.has(r)) errors.push(`${cultureId}: ${where}: no reading "${r}" in ${cultureId}/readings`)
  if (cast) {
    const ids = new Set(cast.map(v => v.id))
    const names = new Set(cast.map(v => v.name))
    const person = (where: string, v: string) => {
      if (!ids.has(v)) errors.push(`${cultureId}: ${where}: no villager "${v}" in ${cultureId}/villagers`)
    }
    const named = (where: string, n: string | undefined) => {
      if (n !== undefined && !names.has(n)) errors.push(`${cultureId}: ${where}: no villager named "${n}"`)
    }
    quests.requests.forEach((r, i) => named(`quests.requests[${i}].giver`, r.giver))
    named('quests.tent_move.giver', quests.tent_move?.giver)
    const { world, events } = pack.files
    named('world.host.name', world.host.name)
    for (const [k, v] of Object.entries(world.resources as Record<string, { leader?: string }>)) named(`world.resources.${k}.leader`, v.leader)
    for (const k of ['wolves', 'bear', 'storm', 'merchant', 'festival'] as const) named(`events.${k}.leader`, events[k].leader)
    festivals.festivals.forEach((f, i) => f.leaders.forEach((l, j) => person(`festivals.festivals[${i}].leaders[${j}]`, l)))
    projects.projects.forEach((p, i) => [p.leader, ...p.helpers].forEach(h => person(`projects.projects[${i}]`, h)))
    chest.tools.forEach((t, i) => person(`chest.tools[${i}].giver`, t.giver))
    for (const who of [...Object.keys(chest.thanks), ...Object.keys(chest.thanks_rotation), ...Object.keys(chest.likes)]) person(`chest (thanks, thanks_rotation, likes): ${who}`, who)
    chest.children.forEach((c, i) => person(`chest.children[${i}]`, c))
    person('chest.smith.id', chest.smith.id)
    person('surprises.lamb.shepherd', surprises.lamb.shepherd)
    person('surprises.riddle.child', surprises.riddle.child)
    surprises.letter.letters.forEach((l, i) => l.to !== 'jan' && person(`surprises.letter.letters[${i}].to`, l.to))
    // the pack's own people thank for a gift in their words, in its language, for each kind of good (not the tutor's:
    // theirs fall back to the app's plain lines)
    for (const v of cast) {
      if (v.source === 'tutor' || !v.lines) continue
      // what their night rituals are, in the pack's language (VILLAGERS.md, "A day in the village")
      for (const [i, r] of (v.routine?.rituals ?? []).entries()) {
        const at = `${cultureId}: villagers/${v.id}.json: routine.rituals[${i}].title`
        if (r.title === undefined) errors.push(`${at}: missing (what they do, in the pack's language)`)
        else if (typeof r.title !== 'string' && !(lang in r.title)) errors.push(`${at}: no "${lang}" text (the pack's language)`)
      }
      const where = `${cultureId}: villagers/${v.id}.json: lines.gift`
      if (!v.lines.gift) {
        errors.push(`${where}: missing (what they say for a good they like, an ordinary one and a rare one)`)
        continue
      }
      for (const kind of GIFT_KINDS) {
        const list = v.lines.gift[kind] ?? []
        if (!list.length) errors.push(`${where}.${kind}: no line`)
        list.forEach((l, i) => {
          if (!(lang in l)) errors.push(`${where}.${kind}[${i}]: no "${lang}" text (the pack's language)`)
        })
      }
    }
  }
  // the grammar book's pages its requests and questions name: pages of its language
  if (pages) for (const [where, g] of packGrammar(pack.files)) if (!pages.has(g)) errors.push(`${cultureId}: ${where}: no grammar page "${g}" in companion/grammar/${lang}`)
  const own = projects.projects.map(p => ({ id: p.id, steps: p.steps.length }))
  errors.push(...cultureScenes(culturesDir, cultureId, lang, cast, refs && { ...refs, projects: own }, own, pages))
  errors.push(...cultureStories(culturesDir, cultureId, lang, cast, refs))
  // how its people are introduced (arrivals.json, when the pack has one): each of its cast, and the templates
  errors.push(...cultureArrivals(culturesDir, cultureId, lang, cast).errors)
  // its spots' texts (world.json `spots`): the story, the words in their pack, the keeper's talk
  errors.push(...spotErrors(pack.files.world.spots, lang, refs?.packs).map(e => `${cultureId}/world.json: ${e}`))
  // the grammar book's pages its keepers' turns name (a wrong choice's `grammar`, as a scene's): pages of its language
  if (pages) {
    for (const [id, s] of Object.entries(pack.files.world.spots)) {
      if (!s?.keeper) continue
      for (const [at, g] of dialogGrammar(keeperTellings(s.keeper).map(t => ({ id: t.at, lines: t.lines })))) {
        if (!pages.has(g)) errors.push(`${cultureId}/world.json: spots.${id}.keeper: ${at}: no grammar page "${g}" in companion/grammar/${lang}`)
      }
    }
  }
  // its night sky (sky.json, when the pack has one): its texts in the pack's language, the reading and the word pack it names
  const packsDir = join(cultureDir(culturesDir, cultureId), 'packs')
  const ownPacks = new Set(existsSync(packsDir) ? readdirSync(packsDir).filter(f => f.endsWith('.json')).map(f => f.slice(0, -5)) : [])
  errors.push(...cultureSky(culturesDir, cultureId, lang, new Set(read.readings.keys()), ownPacks).errors)
  return errors.length ? { ok: false, errors: errors.join('\n') } : { ok: true, pack }
}

type Spots = Pack['files']['world']['spots']

/**
 * The problems of a culture's spots (world.json `spots`), in the pack's language [lang]: every text (the name, the story,
 * the keeper's talk) in the app's four languages, the story at A1 and A2 at least, its words in its pack (given the
 * [packs]), and a keeper's talk as an introduction's is checked (every line his, a reaction and a why to every wrong
 * choice, 1-3 turns, no weather: it is played over the village), at A1 and A2 at least: his meeting, and each talk of his
 * story (`talks`, each id once, its title in the four languages) in each of its tellings (its `levels`, and `again`).
 */
export function spotErrors(spots: Spots, lang: string, packs?: SceneRefs['packs']): string[] {
  const errs: string[] = []
  const all = (t: Record<string, string> | undefined, where: string) => {
    if (!t) return
    const missing = PACK_LANGUAGES.filter(l => typeof t[l] !== 'string')
    if (missing.length) errs.push(`${where}: no ${missing.map(l => `"${l}"`).join(', ')} text (every language the pack serves)`)
  }
  for (const [id, s] of Object.entries(spots) as [string, NonNullable<Spots[keyof Spots]>][]) {
    const at = `spots.${id}`
    all(s.name, `${at}.name`)
    all(s.where, `${at}.where`)
    all(s.about, `${at}.about`)
    if (s.about && !(lang in s.about)) errs.push(`${at}.about: no "${lang}" text (the card's line is in the pack's language)`)
    const told = Object.keys(s.story)
    if (told.length) {
      const missing = SPOT_LEVELS.filter(l => !s.story[l])
      if (missing.length) errs.push(`${at}.story: no ${missing.join(', ')} (a spot's story is told at ${SPOT_LEVELS.join(' and ')} at least)`)
      for (const [lv, t] of Object.entries(s.story)) all(t, `${at}.story.${lv}`)
    }
    if (s.words.length && !s.pack) errs.push(`${at}.words: words without their pack`)
    const pack = s.pack && packs?.find(p => p.id === s.pack)
    if (s.pack && packs && !pack) errs.push(`${at}.pack: no pack "${s.pack}" in the packs the village is served`)
    if (pack) for (const w of s.words) if (!pack.words.some(x => x.id === w)) errs.push(`${at}.words: no word "${w}" in pack "${s.pack}"`)
    const k = s.keeper
    if (!k) continue
    all(k.title, `${at}.keeper.title`)
    all(k.role, `${at}.keeper.role`)
    const talk = (levels: Keeper['levels'], where: string) => {
      const missing = SPOT_LEVELS.filter(l => !levels[l])
      if (missing.length) errs.push(`${where}.levels: no ${missing.join(', ')} (a keeper talks at ${SPOT_LEVELS.join(' and ')} at least)`)
      errs.push(...arrivalErrors({ levels } as unknown as ArrivalBody, { lang, all: PACK_LANGUAGES, speakers: new Set([k.id]), what: 'a talk' }, where))
    }
    talk(k.levels, `${at}.keeper`)
    const talks = new Set<string>()
    k.talks.forEach((t, i) => {
      const here = `${at}.keeper.talks[${i}]`
      if (talks.has(t.id)) errs.push(`${here}.id: "${t.id}" twice (each talk of his story once)`)
      talks.add(t.id)
      all(t.title, `${here}.title`)
      talk(t.levels, here)
      t.again.forEach((g, j) => talk(g.levels, `${here}.again[${j}]`))
    })
  }
  return errs
}

/**
 * Everything [k] says, each telling as its lines, with where it is in his `keeper` ([at]: "levels.A1",
 * "talks[0].again[0].levels.A2"): his meeting at each level, then every talk of his story at each level, in each of its
 * tellings (voice-build voices them: corpus `keepers`).
 */
export function keeperTellings(k: Keeper): (KeeperTelling & { at: string })[] {
  const levels = (l: Keeper['levels'], at: string) => LEVELS.flatMap(lv => (l[lv] ? [{ ...l[lv], at: `${at}levels.${lv}` }] : []))
  return [
    ...levels(k.levels, ''),
    ...k.talks.flatMap((t, i) => [...levels(t.levels, `talks[${i}].`), ...t.again.flatMap((g, j) => levels(g.levels, `talks[${i}].again[${j}].`))]),
  ]
}

/**
 * The keepers of pack [cultureId]'s spots (world.json `spots`: the charcoal burner), as its check reads them; none when
 * the file isn't there or doesn't pass (the bridge's check at start says why).
 */
export function cultureKeepers(culturesDir: string, cultureId: string): Keeper[] {
  try {
    const r = fileSpecs.world.safeParse(JSON.parse(readFileSync(join(cultureDir(culturesDir, cultureId), 'world.json'), 'utf8')))
    return r.success ? Object.values(r.data.spots).flatMap(s => (s?.keeper ? [s.keeper] : [])) : []
  } catch {
    return []
  }
}

/**
 * The problems of pack [cultureId]'s stories (stories/*.json, lani.story/v0): each valid (its words in [refs]'s packs,
 * when given; its teller in the [cast], the storyteller), named for its id, in the pack's language [lang], told at A1
 * and A2 at least (in every chapter), each in its own place in the rotation (`order`), its book's pictures (2-4, one or
 * two an evening in chapters); a continuation after the story of the pack it goes on from.
 */
export function cultureStories(culturesDir: string, cultureId: string, lang: string, cast?: CastEntry[], refs?: Omit<SceneRefs, 'villagers'>): string[] {
  const dir = join(cultureDir(culturesDir, cultureId), 'stories')
  if (!existsSync(dir)) return []
  const errors: string[] = []
  const orders = new Map<number, string>()
  const read: { where: string; id: string; order?: number; continues?: string }[] = []
  for (const f of readdirSync(dir).filter(f => f.endsWith('.json')).sort()) {
    const where = `${cultureId}/stories/${f}`
    try {
      const v = validateStory(JSON.parse(readFileSync(join(dir, f), 'utf8')), { packs: refs?.packs, villagers: cast })
      if (!v.ok) { errors.push(`${where}:\n${v.errors}`); continue }
      const s = v.story
      if (`${s.id}.json` !== f) errors.push(`${where}: id "${s.id}", but the file is ${f}`)
      if (s.language !== lang) errors.push(`${where}: in "${s.language}", not the pack's language "${lang}"`)
      chaptersOf(s).forEach((c, i) => {
        const missing = CURATED_LEVELS.filter(l => !c.levels[l])
        if (missing.length) errors.push(`${where}: ${s.chapters ? `chapters[${i}]` : 'levels'}: no ${missing.join(', ')} (a curated story is told at ${CURATED_LEVELS.join(' and ')} at least)`)
      })
      for (const e of curatedPictureErrors(s)) errors.push(`${where}: ${e}`)
      if (s.order === undefined) errors.push(`${where}: no order (its place in the rotation)`)
      else if (orders.has(s.order)) errors.push(`${where}: order ${s.order} is ${orders.get(s.order)}'s too`)
      else orders.set(s.order, s.id)
      read.push({ where, id: s.id, order: s.order, continues: s.continues })
    } catch (e) {
      errors.push(`${where}: ${(e as Error).message}`)
    }
  }
  // a continuation goes on from a story of the pack, told before it
  for (const s of read.filter(x => x.continues)) {
    const first = read.find(x => x.id === s.continues)
    if (!first) errors.push(`${s.where}: continues "${s.continues}", which isn't a story of the pack`)
    else if ((first.order ?? 0) >= (s.order ?? 0)) errors.push(`${s.where}: continues "${s.continues}" (order ${first.order}), so its order comes after it (it has ${s.order})`)
  }
  return errors
}

/** The village projects of pack [cultureId] (projects.json), each with its number of steps, as scenes' `needs` name them; undefined when it has none to read. */
export function cultureProjects(culturesDir: string, cultureId: string): SceneProject[] | undefined {
  try {
    const r = fileSpecs.projects.safeParse(JSON.parse(readFileSync(join(cultureDir(culturesDir, cultureId), 'projects.json'), 'utf8')))
    return r.success ? r.data.projects.map(p => ({ id: p.id, steps: p.steps.length })) : undefined
  } catch {
    return undefined
  }
}

/**
 * The problems of pack [cultureId]'s own scenes (scenes/*.json, lani.scene/v0): each valid (its words in [refs]'s
 * packs, when given), named for its id, in the pack's language [lang], everyone in it one of the pack's [cast], the
 * projects its `needs` name the pack's own ([projects]), and the grammar pages its choices name ones of [pages].
 */
export function cultureScenes(culturesDir: string, cultureId: string, lang: string, cast?: { id: string }[], refs?: Omit<SceneRefs, 'villagers'>, projects?: SceneProject[], pages?: Set<string>): string[] {
  const dir = join(cultureDir(culturesDir, cultureId), 'scenes')
  if (!existsSync(dir)) return []
  const errors: string[] = []
  for (const f of readdirSync(dir).filter(f => f.endsWith('.json')).sort()) {
    const where = `${cultureId}/scenes/${f}`
    try {
      const v = validateScene(JSON.parse(readFileSync(join(dir, f), 'utf8')), refs)
      if (!v.ok) { errors.push(`${where}:\n${v.errors}`); continue }
      if (!refs) errors.push(...checkNeeds(v.scene, projects).map(e => `${where}: ${e}`))
      if (`${v.scene.id}.json` !== f) errors.push(`${where}: id "${v.scene.id}", but the file is ${f}`)
      if (v.scene.language !== lang) errors.push(`${where}: in "${v.scene.language}", not the pack's language "${lang}"`)
      const ids = cast && new Set(cast.map(c => c.id))
      v.scene.people.forEach((p, i) => {
        if (!p.villager) errors.push(`${where}: people[${i}] (${p.id}): no villager (the people of a culture's scene are its cast)`)
        else if (ids && !ids.has(p.villager)) errors.push(`${where}: people[${i}] (${p.id}): no villager "${p.villager}" in ${cultureId}/villagers`)
      })
      if (pages) for (const [at, g] of dialogGrammar([...v.scene.dialogs, ...v.scene.variants])) if (!pages.has(g)) errors.push(`${where}: ${at}: no grammar page "${g}" in companion/grammar/${lang}`)
    } catch (e) {
      errors.push(`${where}: ${(e as Error).message}`)
    }
  }
  return errors
}

/**
 * The culture a bridge plays: [wanted] (the profile's) when it's a complete pack, else the default, and why not. A stub
 * (only its manifest, like friuli for now) isn't played: the app falls back the same way.
 */
export function resolveCulture(culturesDir: string, wanted: string | undefined): { id: string; manifest?: Manifest; wanted: string; why?: string } {
  const want = wanted?.trim() || DEFAULT_CULTURE
  const m = readManifest(culturesDir, want)
  if (m.ok && m.manifest.status === 'complete') return { id: want, manifest: m.manifest, wanted: want }
  const why = m.ok ? `${want} is a stub (only its manifest): nothing to play yet` : m.errors
  const d = readManifest(culturesDir, DEFAULT_CULTURE)
  return { id: DEFAULT_CULTURE, manifest: d.ok ? d.manifest : undefined, wanted: want, why }
}

// --- what the tutor is told about the place --------------------------------------------------------

/** How the tutor speaks of the village (culture.json `tutor`): its name, the role-play setting, the style, the voice. */
export type CultureTutor = { village: string; setting: string; style: string; voice: string }

/** Primorska's, for a pack that doesn't say (and as Jan's pack says it): the setting has {learner}, the learner's name. */
export const PRIMORSKA_TUTOR: CultureTutor = {
  village: 'Moja vas',
  setting: "Moja vas, {learner}'s village on the Trnovo plateau above Nova Gorica",
  style: 'Primorska style',
  voice: 'A native Slovene speaker from a village near Nova Gorica in Primorska, speaking Slovene naturally with the local accent.',
}

/** The tutor's texts of [m]'s pack: its own, else Primorska's. */
export const tutorOf = (m: Manifest | undefined): CultureTutor => m?.tutor ?? PRIMORSKA_TUTOR

/**
 * The landscapes a new village can be founded on (the app's Landscape, GAME.md "The land"); the classic valley is the
 * villages' from before, never chosen.
 */
export const LANDSCAPES = ['valley', 'hills', 'lake', 'mountains', 'coast'] as const

/** Whether pack [cultureId]'s horizon shows the sea (world.json `backdrop.sea`): only then can a village be on the coast. */
export function cultureHasSea(culturesDir: string, cultureId: string): boolean {
  try {
    return JSON.parse(readFileSync(join(cultureDir(culturesDir, cultureId), 'world.json'), 'utf8'))?.backdrop?.sea === true
  } catch {
    return false
  }
}

/** The landscapes pack [cultureId] offers a new village: every one, the coast only where it has the sea. */
export function landscapesOf(culturesDir: string, cultureId: string): string[] {
  return LANDSCAPES.filter(l => l !== 'coast' || cultureHasSea(culturesDir, cultureId))
}

/** The plain lines of the people who move in or are born in [cultureId]'s village (people.json), or undefined. */
export function peopleLines(culturesDir: string, cultureId: string): Pack['files']['people']['lines'] | undefined {
  try {
    const r = fileSpecs.people.safeParse(JSON.parse(readFileSync(join(cultureDir(culturesDir, cultureId), 'people.json'), 'utf8')))
    return r.success ? r.data.lines : undefined
  } catch {
    return undefined
  }
}
