// lani.arrivals/v0 and lani.arrival/v0 — how someone new is introduced (companion/VILLAGERS.md, "Arrivals"). When
// someone joins the village (a cast member, a newcomer, a baby born there, a mover from a friend's town), an arrival
// waits for the learner: a bubble at their home, and a short dialog at the learner's level, with the right people (the
// newcomer themself, or a relative who introduces them: Micka her granddaughter Zala; the parents show their baby).
// Nobody hosts a run, asks for help or stands in a scene before the learner has met them there.
//
// The curated ones are a culture pack's (companion/cultures/<id>/arrivals.json): one for each of its cast, and the
// templates for the people the village makes up (a newcomer, a birth, a mover). The app bundles them. The tutor writes a
// proper one for a newcomer or a birth when the app says they arrived (publish_arrival, <data>/app/arrivals/<id>.json),
// which replaces the template; one with a cast member's id replaces the pack's until remove_arrival.
// Keep in sync with .claude/skills/lani-studio/reference/module-spec.md ("Arrivals").
import { existsSync, mkdirSync, readdirSync, readFileSync, renameSync, statSync, unlinkSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import { learnerErrors, withoutLearner } from './addressee'
import { schemaField } from './schema'
import { dialogLine, offStage, reply, SCENE_LANGUAGES } from './scenes'
import { LEVELS, type Level } from './stories'

export const ARRIVALS_SCHEMA = 'lani.arrivals/v0'
export const ARRIVAL_SCHEMA = 'lani.arrival/v0'
/** The levels every curated arrival has (the tutor's may have one: the learner's). */
export const CURATED_ARRIVAL_LEVELS: Level[] = ['A1', 'A2']
/** The languages every text of a culture pack's arrivals has: the pack's and the bases it serves (the app's four). */
export const PACK_LANGUAGES = SCENE_LANGUAGES

/** The people the village makes up, and who speaks in their template: the newcomer, or a newborn's parents. */
export const TEMPLATE_KINDS = ['newcomer', 'birth', 'moved'] as const
export type TemplateKind = (typeof TEMPLATE_KINDS)[number]
export const TEMPLATE_SPEAKERS: Record<TemplateKind, string[]> = {
  newcomer: ['self'],
  moved: ['self'],
  // the first of the parents, and the other (the first again when only one lives here)
  birth: ['parent', 'parent2'],
}
/**
 * What a template's texts may name (ICU: `{first}`, `{g, select, f {Dobrodošla} other {Dobrodošel}}`): the person's full
 * [name], [first] and [family] name, their trade ([role], lower case in the village's language), their gender [g] (f or
 * m); a mover's town ([from]); a newborn's parents' first names ([parent], [parent2]) and genders ([pg], [p2g]).
 */
export const TEMPLATE_ARGS: Record<TemplateKind, string[]> = {
  newcomer: ['name', 'first', 'family', 'role', 'g'],
  moved: ['name', 'first', 'family', 'role', 'g', 'from'],
  birth: ['first', 'family', 'g', 'parent', 'parent2', 'pg', 'p2g'],
}

const id = z.string().regex(/^[a-z0-9][a-z0-9-]{0,62}$/, 'must be kebab-case, 1-63 chars')
const str = z.string().trim().min(1)

/**
 * One level's introduction: its lines (a scene dialog's: someone's line or the learner's turn), and [late]: the line
 * said instead of the first when the learner meets them days after they came ("Jan! Saj še ne poznaš moje vnukinje?").
 */
const variant = z.strictObject({
  late: dialogLine.optional(),
  lines: z.array(dialogLine).min(3).max(12),
})
const levels = z
  .partialRecord(z.enum(LEVELS), variant)
  .refine(l => Object.keys(l).length > 0, 'at least one level: {"A1": {"lines": […]}}')

/**
 * An arrival as a pack's file has it (by the villager's id): who introduces them ([by], someone of the cast who came
 * before), what the newcomer remembers of it ([memory], fitting their remember lines: "dan, ko sem prišla k babici"), and
 * the dialog per level.
 */
const body = { by: id.optional(), memory: reply, levels }
export const arrivalBody = z.strictObject(body)
export type ArrivalBody = z.infer<typeof arrivalBody>
export type ArrivalVariant = z.infer<typeof variant>
type Line = z.infer<typeof dialogLine>

/** The tutor's arrival for one person of the village (publish_arrival): the resident's (or a cast member's) id. */
export const arrivalSpec = z.strictObject({
  schema: schemaField(ARRIVAL_SCHEMA),
  id,
  /** The village's language (a Slovene village {"sl", "en"}; an Italian one {"it", "sl", "en"}). */
  language: z.enum(SCENE_LANGUAGES).default('sl'),
  ...body,
})
export type Arrival = z.infer<typeof arrivalSpec>

/** A culture pack's arrivals.json: its cast's, by villager id, and the templates for the people the village makes up. */
export const arrivalsFileSpec = z.strictObject({
  schema: schemaField(ARRIVALS_SCHEMA),
  language: z.enum(SCENE_LANGUAGES),
  /** Who wrote them and who checked: "machine-written, not reviewed by a native speaker". */
  review: str.optional(),
  cast: z.record(id, arrivalBody),
  templates: z.strictObject({ newcomer: arrivalBody, birth: arrivalBody, moved: arrivalBody.optional() }),
})
export type ArrivalsFile = z.infer<typeof arrivalsFileSpec>

// --- messages: the ICU the app's culture texts use (l10n/Message.kt) ----------------------------------------------

/**
 * The arguments message [s] reads (`{name}`, `{g, select, f {…} other {…}}`, `{n, plural, one {…} other {…}}`), as the
 * app's Message parses it; throws with where it is wrong.
 */
export function messageArgs(s: string): Set<string> {
  let i = 0
  const out = new Set<string>()
  const fail = (why: string): never => {
    throw new Error(`${why} at ${i} in "${s}"`)
  }
  const space = () => {
    while (i < s.length && /\s/.test(s[i])) i++
  }
  const word = () => {
    space()
    const start = i
    while (i < s.length && !',{} \t\n'.includes(s[i])) i++
    if (i === start) fail('expected a name')
    return s.slice(start, i)
  }
  const expect = (c: string) => {
    space()
    if (s[i] !== c) fail(`expected ${c}`)
    i++
  }
  const parts = () => {
    while (i < s.length && s[i] !== '}') {
      if (s[i] === '{') arg()
      else i++
    }
  }
  const arg = () => {
    i++
    const name = word()
    out.add(name)
    space()
    if (s[i] === '}') return void i++
    expect(',')
    const type = word()
    if (type !== 'plural' && type !== 'select') fail(`unknown argument type ${type}`)
    expect(',')
    const keys = new Set<string>()
    for (;;) {
      space()
      if (i >= s.length) fail(`unclosed {${name}`)
      if (s[i] === '}') { i++; break }
      keys.add(word())
      expect('{')
      parts()
      expect('}')
    }
    if (!keys.has('other')) fail(`{${name}, ${type}} without an other branch`)
  }
  parts()
  if (i < s.length) fail('unmatched }')
  return out
}

// --- checks ---------------------------------------------------------------------------------------------------

/** What an arrival's check needs to know: its language(s), who may speak, what its texts may name. */
type Rules = {
  /** The village's language: every text has it and English. */
  lang: string
  /** Every text has these too (a culture pack's: the app's four languages). */
  all?: readonly string[]
  /** The ids that may say a line: the newcomer, whoever introduces them; a template's roles. */
  speakers: Set<string>
  /** The arguments its texts may use (a template's); none elsewhere. */
  args?: string[]
  /** A curated one: A1 and A2 at least, a late line at every level. */
  curated?: boolean
  /** What it is, in its problems ("an arrival" by default; "a talk": someone's at a spot of the landscape, world.json `spots`). */
  what?: string
}

type Texts = Partial<Record<string, unknown>>

/** The problems of one arrival's texts and dialogs (a pack's, a template, the tutor's). */
export function arrivalErrors(a: ArrivalBody, rules: Rules, at = ''): string[] {
  const errs: string[] = []
  const where = (p: string) => (at ? `${at}.${p}` : p)
  const what = rules.what ?? 'an arrival'
  const langs = [...new Set([rules.lang, 'en', ...(rules.all ?? [])])]
  const which = (missing: string[]) => (missing.includes(rules.lang) && rules.lang !== 'en' ? ' (the village\'s language)' : rules.all ? ' (every language the pack serves)' : '')
  /** Its placeholders: only a template's arguments, as the app's messages write them. */
  const named = (s: string, p: string) => {
    // the learner's placeholders are no arguments ({learner}, {m:…|f:…}: addressee.ts): any arrival may say them
    for (const e of learnerErrors(s)) errs.push(`${where(p)}: ${e}`)
    let used: Set<string>
    try {
      used = messageArgs(withoutLearner(s))
    } catch (e) {
      return void errs.push(`${where(p)}: ${(e as Error).message}`)
    }
    const extra = [...used].filter(x => !(rules.args ?? []).includes(x))
    if (extra.length) {
      errs.push(`${where(p)}: uses {${extra.join('}, {')}}, ` + (rules.args ? `which a template isn't given (it gets {${rules.args.join('}, {')}})` : 'but an arrival names people as they are (no {…})'))
    }
  }
  const inLangs = (t: Texts | undefined, p: string) => {
    if (!t) return
    const missing = langs.filter(x => typeof t[x] !== 'string')
    if (missing.length) errs.push(`${where(p)}: no ${missing.map(x => `"${x}"`).join(', ')} text${which(missing)}`)
    for (const l of SCENE_LANGUAGES) if (typeof t[l] === 'string') named(t[l] as string, `${p}.${l}`)
  }
  const why = (w: unknown, p: string) => {
    if (w === undefined) return
    if (typeof w === 'string') {
      if (rules.all) errs.push(`${where(p)}: a curated why is per language ({"en": …, …}: the learner reads it in their base)`)
      return named(w, p)
    }
    // the learner reads it in their base: every language but the village's own
    const need = (rules.all ?? ['en']).filter(x => x !== rules.lang)
    const missing = need.filter(x => typeof (w as Texts)[x] !== 'string')
    if (missing.length) errs.push(`${where(p)}: no ${missing.map(x => `"${x}"`).join(', ')} why`)
    for (const [l, s] of Object.entries(w as Texts)) if (typeof s === 'string') named(s, `${p}.${l}`)
  }
  const spoken = (l: Line, p: string) => {
    if (l.choices.length) return errs.push(`${where(p)}: a line someone says, not the learner's choices`)
    if (!l.who) errs.push(`${where(p)}: who says it? (${[...rules.speakers].join(', ')})`)
    else if (!rules.speakers.has(l.who)) errs.push(`${where(p)}: who "${l.who}" isn't in it (${[...rules.speakers].join(', ')})`)
    if (l.sky || l.fx) errs.push(`${where(p)}: ${what} has no scene around it: no sky or fx cue`)
    inLangs(l, p)
  }
  inLangs(a.memory, 'memory')
  const present = LEVELS.filter(l => a.levels[l])
  if (rules.curated) {
    const missing = CURATED_ARRIVAL_LEVELS.filter(l => !a.levels[l])
    if (missing.length) errs.push(`${where('levels')}: no ${missing.join(', ')} (a curated arrival is written for ${CURATED_ARRIVAL_LEVELS.join(' and ')} at least)`)
  }
  for (const lv of present) {
    const v = a.levels[lv]!
    const p = `levels.${lv}`
    if (v.late) spoken(v.late, `${p}.late`)
    else if (rules.curated) errs.push(`${where(p)}.late: missing (the first line when the learner meets them days after they came: "Nisva se še spoznala …")`)
    if (v.lines[0]?.choices.length) errs.push(`${where(p)}.lines[0]: the first line is someone's, not the learner's choices`)
    errs.push(...offStage({ lines: v.late ? [v.late, ...v.lines] : v.lines }, where(p), what))
    let turns = 0
    v.lines.forEach((l, li) => {
      const here = `${p}.lines[${li}]`
      if (!l.choices.length) return spoken(l, here)
      turns++
      if (l.who || SCENE_LANGUAGES.some(x => l[x])) errs.push(`${where(here)}: a choice turn has no who and no text of its own`)
      if (l.sky || l.fx) errs.push(`${where(here)}: ${what} has no scene around it: no sky or fx cue`)
      if (l.choices.length < 2) errs.push(`${where(here)}: a turn needs 2-3 choices`)
      if (!l.choices.some(c => c.ok)) errs.push(`${where(here)}: no choice is ok`)
      if (l.choices.every(c => c.ok)) errs.push(`${where(here)}: every choice is ok: a turn keeps a wrong one`)
      l.choices.forEach((c, ci) => {
        const ch = `${here}.choices[${ci}]`
        inLangs(c, ch)
        if (c.reply) {
          inLangs(c.reply, `${ch}.reply`)
          if (c.reply.sky || c.reply.fx) errs.push(`${where(ch)}.reply: ${what} has no scene around it: no sky or fx cue`)
        }
        if (!c.ok) {
          if (!c.why) errs.push(`${where(ch)}: a wrong choice needs a why`)
          why(c.why, `${ch}.why`)
          if (!c.reply) errs.push(`${where(ch)}: a wrong choice needs a reaction (its reply: what they make of it, never the right form)`)
        }
      })
    })
    if (!turns) errs.push(`${where(p)}: no turn for the learner (a greeting, a welcome, "Me veseli.")`)
    if (turns > 3) errs.push(`${where(p)}: ${turns} turns: at most 3 (${what === 'an arrival' ? 'an introduction' : what} is short)`)
  }
  return errs
}

/** One person who can say a line in the village: [id], and [order] for the cast (who came before). */
export type ArrivalPerson = { id: string; order?: number; extra?: boolean; source?: string }

/**
 * The problems of a culture pack's arrivals file [f] (in [lang], the pack's language): every text in the app's four
 * languages, each of the [cast] (the curated ones, extras aside) with an arrival at A1 and A2, a late line at every level,
 * a reaction to every wrong choice; an introducer of the cast who came before; the templates' roles and arguments.
 */
export function arrivalsFileErrors(f: ArrivalsFile, lang: string, cast?: ArrivalPerson[]): string[] {
  const errs: string[] = []
  if (f.language !== lang) errs.push(`language: "${f.language}", not the pack's "${lang}"`)
  const byId = cast && new Map(cast.map(v => [v.id, v]))
  for (const [who, a] of Object.entries(f.cast)) {
    const v = byId?.get(who)
    if (byId && !v) errs.push(`cast.${who}: no villager "${who}" in the pack's villagers`)
    const speakers = new Set([who, ...(a.by ? [a.by] : [])])
    if (a.by) {
      const b = byId?.get(a.by)
      if (a.by === who) errs.push(`cast.${who}.by: someone else introduces them`)
      else if (byId && !b) errs.push(`cast.${who}.by: no villager "${a.by}" in the pack's villagers`)
      else if (b && v && (b.order ?? 100) >= (v.order ?? 100)) errs.push(`cast.${who}.by: "${a.by}" comes after "${who}" (order ${b.order} ≥ ${v.order}): who introduces them lives here first`)
    }
    errs.push(...arrivalErrors(a, { lang, all: PACK_LANGUAGES, speakers, curated: true }, `cast.${who}`))
  }
  for (const v of cast ?? []) {
    if (v.source === 'tutor' || v.extra) continue
    if (!f.cast[v.id]) errs.push(`cast: no arrival for "${v.id}" (each of the cast is introduced)`)
  }
  for (const kind of TEMPLATE_KINDS) {
    const t = f.templates[kind]
    if (!t) continue
    if (t.by) errs.push(`templates.${kind}.by: a template is said by ${TEMPLATE_SPEAKERS[kind].join(' and ')}`)
    errs.push(...arrivalErrors(t, { lang, all: PACK_LANGUAGES, speakers: new Set(TEMPLATE_SPEAKERS[kind]), args: TEMPLATE_ARGS[kind], curated: true }, `templates.${kind}`))
  }
  return errs
}

/**
 * Culture pack [cultureId]'s arrivals (arrivals.json), checked: its problems, each with the file; none when the pack has
 * no arrivals file (the app then introduces nobody and filters nobody).
 */
export function cultureArrivals(culturesDir: string, cultureId: string, lang: string, cast?: ArrivalPerson[]): { file?: ArrivalsFile; errors: string[] } {
  const p = join(culturesDir, cultureId, 'arrivals.json')
  if (!existsSync(p)) return { errors: [] }
  try {
    const r = arrivalsFileSpec.safeParse(JSON.parse(readFileSync(p, 'utf8')))
    if (!r.success) return { errors: [`${cultureId}/arrivals.json:\n${z.prettifyError(r.error)}`] }
    const errs = arrivalsFileErrors(r.data, lang, cast)
    return { file: r.data, errors: errs.map(e => `${cultureId}/arrivals.json: ${e}`) }
  } catch (e) {
    return { errors: [`${cultureId}/arrivals.json: ${(e as Error).message}`] }
  }
}

/** What publish_arrival checks the tutor's arrival against: the village's language, and who lives there. */
export type ArrivalRefs = {
  /** The village's language: the arrival is in it. */
  language?: string
  /** Who may speak in it besides the newcomer: the residents and the cast (their ids). */
  people?: string[]
}

export function validateArrival(input: unknown, refs: ArrivalRefs = {}): { ok: true; arrival: Arrival } | { ok: false; errors: string } {
  const r = arrivalSpec.safeParse(input)
  if (!r.success) return { ok: false, errors: z.prettifyError(r.error) }
  const a = r.data
  const errs: string[] = []
  if (refs.language && a.language !== refs.language) errs.push(`language: "${a.language}", but the village speaks "${refs.language}"`)
  const people = refs.people && new Set(refs.people)
  if (a.by && a.by === a.id) errs.push('by: someone else introduces them (leave it out when they introduce themself)')
  if (a.by && people && !people.has(a.by)) errs.push(`by: "${a.by}" doesn't live in the village (list_villagers; the residents are in the village state)`)
  // who speaks: the newcomer, whoever introduces them, and anyone of the village (a newborn's parents)
  const speakers = new Set([a.id, ...(a.by ? [a.by] : []), ...(people ?? [])])
  // without the village (an older state), any id may speak: the app falls back to the newcomer for one it doesn't know
  const open = !people
  errs.push(...arrivalErrors(a, { lang: a.language, speakers: open ? new Set([...speakers, ...allWho(a)]) : speakers }))
  return errs.length ? { ok: false, errors: errs.map(e => `✖ ${e}`).join('\n') } : { ok: true, arrival: a }
}

/** Every `who` an arrival names. */
function allWho(a: ArrivalBody): string[] {
  return Object.values(a.levels).flatMap(v => [...(v?.late?.who ? [v.late.who] : []), ...(v?.lines ?? []).flatMap(l => (l.who ? [l.who] : []))])
}

/** The ids who live in the village (GameState.residents), from the app's game state; undefined when there is none. */
export function residentIds(game: { state?: unknown } | undefined): string[] | undefined {
  const rs = (game?.state as { residents?: { id?: unknown }[] } | undefined)?.residents
  return Array.isArray(rs) ? rs.flatMap(r => (typeof r?.id === 'string' ? [r.id] : [])) : undefined
}

// --- the tutor's arrivals -------------------------------------------------------------------------------------

export type LoadedArrival = Arrival & { source: 'tutor'; published_at?: number }

/** The tutor's arrivals (<data>/app/arrivals/<id>.json), one per person of the village. */
export class ArrivalStore {
  private warned = new Set<string>()

  constructor(
    private dir: string,
    private log: (...a: unknown[]) => void = () => {},
  ) {
    mkdirSync(dir, { recursive: true })
  }

  all(): LoadedArrival[] {
    if (!existsSync(this.dir)) return []
    return readdirSync(this.dir)
      .filter(f => f.endsWith('.json'))
      .sort()
      .flatMap(f => {
        const path = join(this.dir, f)
        try {
          const v = validateArrival(JSON.parse(readFileSync(path, 'utf8')))
          if (!v.ok) throw new Error(v.errors)
          return [{ ...v.arrival, source: 'tutor' as const, published_at: statSync(path).mtimeMs }]
        } catch (e) {
          const key = `${path}:${statSync(path).mtimeMs}`
          if (!this.warned.has(key)) this.log(`skipping arrival ${path}: ${(e as Error).message}`)
          this.warned.add(key)
          return []
        }
      })
  }

  get(id: string): LoadedArrival | undefined {
    return this.all().find(a => a.id === id)
  }

  publish(a: Arrival): void {
    const path = join(this.dir, `${a.id}.json`)
    writeFileSync(`${path}.tmp`, JSON.stringify(a, null, 2) + '\n')
    renameSync(`${path}.tmp`, path)
  }

  remove(id: string): boolean {
    if (!/^[a-z0-9][a-z0-9-]{0,62}$/.test(id)) return false
    const path = join(this.dir, `${id}.json`)
    if (!existsSync(path)) return false
    unlinkSync(path)
    return true
  }
}
