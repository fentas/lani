// The school's course (companion/COURSE.md): the language learned level by level, each level an ordered list of units,
// each unit a guided introduction, two to four worksheets and a test.
//
//   lani.course/v0     the outline: the levels and their units in order (companion/course/<language>/course.json)
//   lani.unit/v0       a unit (companion/course/<language>/units/<id>.json)
//   lani.worksheet/v0  the tutor's homework for a unit (<data>/app/course/<unit>/<id>.json, publish_worksheet)
//
// A worksheet takes every exercise type of a module (spec.ts) and the school's own four: gaptext (a text with gaps),
// table (conjugate or decline), transform (a list to change) and sort (words into columns). They are worksheet types
// for now; modules take them once every phone plays them. Read per request like the grammar book: a file that doesn't
// validate is skipped and logged once. Keep in sync with companion/COURSE.md and the lani-studio skill's
// reference/module-spec.md ("The school's exercise types").
import { existsSync, mkdirSync, readdirSync, readFileSync, renameSync, statSync, unlinkSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import { learnerErrorsIn } from './addressee'
import { cell, grammarRef, withEn } from './grammar'
import { LANG } from './langs'
import { WORD_LANGUAGES } from './packs'
import { schemaField } from './schema'
import { choice, cloze, exercise } from './spec'

export const COURSE_SCHEMA = 'lani.course/v0'
export const UNIT_SCHEMA = 'lani.unit/v0'
export const WORKSHEET_SCHEMA = 'lani.worksheet/v0'
/** The CEFR levels, in order. */
export const LEVELS = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'] as const
/** A unit's, a worksheet's or a homework's id: kebab-case, 2–63 characters. */
export const COURSE_ID = /^[a-z0-9][a-z0-9-]{1,62}$/
/** The forms book's key of a form: "pres.1sg", "neg.3pl", "past.f.sg", "loc.sg", "adj.f.sg" (forms.ts). */
export const FORM_KEY = /^[a-z]+(\.[a-z0-9]+){1,2}$/
/** A gap of a gap text: three underscores or more. */
export const GAP = /_{3,}/g
/**
 * What a session_end's data.course names as the sheet of a unit's test: no worksheet or homework may be called so,
 * or the tutor couldn't tell which it was.
 */
const TEST_SHEET = 'test'

const text = z.string().trim().min(1)
const opt = text.optional()
const grammar = grammarRef.optional()
const id = z.string().regex(COURSE_ID, 'kebab-case, 2-63 chars')
const sheetId = id.refine(s => s !== TEST_SHEET, `"${TEST_SHEET}" is what a session_end calls the unit's test: name the sheet otherwise`)
const level = z.enum(LEVELS)
const form = z.string().regex(FORM_KEY, 'a forms book key: pres.1sg, neg.3pl, past.f.sg, loc.sg …')

/** Answers compared as the bank and the sort compare them: trimmed, any case. */
const same = (s: string) => s.trim().toLocaleLowerCase('sl')
/** How many gaps (`___`) [s] has. */
export const gapsIn = (s: string) => s.match(GAP)?.length ?? 0

// --- the school's exercise types ---------------------------------------------------------------------------------

/** A gap of a gap text: the right answers (the first shown as the correction), its hint, why, and its own rule. */
const gap = z.strictObject({ accept: z.array(text).min(1), hint: opt, explain: opt, grammar })

const gaptext = z
  .strictObject({
    type: z.literal('gaptext'),
    grammar,
    instruction: opt,
    title: opt,
    /** A few sentences, each gap `___` (three underscores or more), 2–12 gaps. */
    text: text.max(600),
    gaps: z.array(gap).min(2).max(12),
    /** A word bank under the text: each gap's first answer and the distractors, shuffled. */
    bank: z.boolean().default(false),
    distractors: z.array(text).max(6).default([]),
    /** The text's meaning in English, shown once checked. */
    translation: opt,
    explain: opt,
  })
  .superRefine((e, ctx) => {
    const n = gapsIn(e.text)
    if (n < 2 || n > 12) ctx.addIssue({ code: 'custom', path: ['text'], message: `${n} gap(s) (___); a gap text has 2 to 12` })
    if (n !== e.gaps.length) ctx.addIssue({ code: 'custom', path: ['gaps'], message: `${e.gaps.length} gaps for the ${n} ___ of the text: one per ___, in order` })
    if (e.distractors.length && !e.bank) ctx.addIssue({ code: 'custom', path: ['distractors'], message: 'distractors only with a word bank ("bank": true)' })
    const right = new Set(e.gaps.flatMap(g => g.accept.map(same)))
    e.distractors.forEach((d, i) => {
      if (right.has(same(d))) ctx.addIssue({ code: 'custom', path: ['distractors', i], message: `"${d}" is a right answer of a gap` })
    })
  })

/** A cell to fill: the right answers, hint, why, its rule, and the forms book's key of its form. */
const fill = z.strictObject({ accept: z.array(text).min(1), hint: opt, explain: opt, grammar, form: form.optional() })

const table = z
  .strictObject({
    type: z.literal('table'),
    grammar,
    instruction: opt,
    prompt: opt,
    /** The word whose forms the table holds: with a cell's form, the learner's word's form practised too. */
    lemma: opt,
    columns: z.array(text).min(1).max(4),
    /** A label (jaz, ti …; "1. kdo? kaj?") and a cell per column: given (a string, "—" for none) or to fill. */
    rows: z.array(z.strictObject({ label: text.max(80), cells: z.array(z.union([text.max(80), fill])) })).min(1).max(12),
    explain: opt,
  })
  .superRefine((t, ctx) => {
    t.rows.forEach((r, i) => {
      if (r.cells.length !== t.columns.length) ctx.addIssue({ code: 'custom', path: ['rows', i], message: `${r.cells.length} cells, the table has ${t.columns.length} columns` })
    })
    if (!t.rows.some(r => r.cells.some(c => typeof c !== 'string'))) ctx.addIssue({ code: 'custom', path: ['rows'], message: 'nothing to fill: at least one cell {"accept": [...]}' })
  })

const transform = z.strictObject({
  type: z.literal('transform'),
  grammar,
  /** The one instruction for the whole list: "Zanikaj · Say it with no". */
  instruction: text,
  /** Shown between each line and its answer: "(preteklik)". */
  cue: opt,
  /**
   * Every word of `accept` asked for. Without it, the person in brackets after a line ("Pečem kruh. (babica)") may be
   * left out of the answer as the line leaves its own subject out ("Peče kruh.": the verb's ending says the person; the
   * app's grading, in a language that drops its subject); with it, the instruction asks for the whole sentence.
   */
  whole: z.boolean().optional(),
  items: z
    .array(z.strictObject({ from: text, accept: z.array(text).min(1), hint: opt, explain: opt, grammar, lemma: opt, form: form.optional() }))
    .min(2)
    .max(12),
  explain: opt,
})

const sort = z
  .strictObject({
    type: z.literal('sort'),
    grammar,
    instruction: opt,
    prompt: opt,
    columns: z.array(z.strictObject({ label: text, words: z.array(text).min(1).max(12) })).min(2).max(4),
    explain: opt,
  })
  .superRefine((s, ctx) => {
    const words = s.columns.flatMap(c => c.words)
    if (words.length < 4 || words.length > 16) ctx.addIssue({ code: 'custom', path: ['columns'], message: `${words.length} words; a sort has 4 to 16 in all` })
    const twice = [...new Set(words.map(same).filter((w, i, all) => all.indexOf(w) !== i))]
    if (twice.length) ctx.addIssue({ code: 'custom', path: ['columns'], message: `a word twice: ${twice.join(', ')}` })
  })

/** The school's own four types: a page of work at once (companion/COURSE.md, "The school's exercise types"). */
export const sheetExercise = z.discriminatedUnion('type', [gaptext, table, transform, sort])
/** What a worksheet, a test or homework holds: every module type (spec.ts) and the school's four. */
export const worksheetExercise = z.discriminatedUnion('type', [...exercise.options, ...sheetExercise.options])

export type SheetExercise = z.infer<typeof sheetExercise>
export type WorksheetExercise = z.infer<typeof worksheetExercise>

// --- the introduction --------------------------------------------------------------------------------------------

/**
 * An example: the sentence in the course's language and its translation in each base, English always ({"sl": "Jaz sem
 * {learner}.", "en": "I am {learner}.", …}), as the grammar book's; an optional note per base. The course's language is
 * checked where it is known (the unit, the homework).
 */
const example = z
  .object({ note: withEn(300).optional() })
  .catchall(text.max(240))
  .superRefine((e, ctx) => {
    for (const k of Object.keys(e)) {
      if (k !== 'note' && !LANG.test(k)) ctx.addIssue({ code: 'custom', path: [k], message: `"${k}" isn't a language code (sl, en, it …) or "note"` })
    }
    if (typeof e.en !== 'string') ctx.addIssue({ code: 'custom', path: ['en'], message: 'the English translation ("en") is missing' })
  })

/** A step: the teacher's few lines, examples, rows the table grows by, and one quick question (a tap or a one-word gap). */
const step = z.strictObject({
  say: withEn(600),
  examples: z.array(example).max(4).default([]),
  rows: z.array(z.array(cell)).max(8).default([]),
  ask: z.discriminatedUnion('type', [choice, cloze]).optional(),
})

export const intro = z
  .strictObject({
    table: z.strictObject({ head: z.array(cell).min(2).max(4) }).optional(),
    steps: z.array(step).min(1).max(10),
  })
  .superRefine((t, ctx) => {
    t.steps.forEach((s, i) => {
      if (s.rows.length && !t.table) ctx.addIssue({ code: 'custom', path: ['steps', i, 'rows'], message: 'rows need the table they are added to ("table": {"head": [...]})' })
      if (!t.table) return
      s.rows.forEach((r, j) => {
        if (r.length !== t.table!.head.length) ctx.addIssue({ code: 'custom', path: ['steps', i, 'rows', j], message: `${r.length} cells, the head has ${t.table!.head.length}` })
      })
    })
  })

export type Intro = z.infer<typeof intro>

type Ctx = z.RefinementCtx
type Path = (string | number)[]

/** The examples of [i] in [language], each one's. */
function introIn(i: Intro | undefined, language: string, ctx: Ctx, path: Path) {
  i?.steps.forEach((s, n) =>
    s.examples.forEach((e, j) => {
      if (typeof e[language] !== 'string') ctx.addIssue({ code: 'custom', path: [...path, 'steps', n, 'examples', j, language], message: `the example in the course's language ("${language}") is missing` })
    }),
  )
}

/** A title of [t] in [language]. */
function titleIn(t: Record<string, string>, language: string, ctx: Ctx, path: Path) {
  if (typeof t[language] !== 'string') ctx.addIssue({ code: 'custom', path: [...path, language], message: `the title in the course's language ("${language}") is missing` })
}

// --- a unit ------------------------------------------------------------------------------------------------------

const worksheet = z.strictObject({
  id: sheetId,
  title: withEn(80),
  /** As the unit's, before the worksheet's exercises. */
  intro: intro.optional(),
  exercises: z.array(worksheetExercise).min(1).max(8),
})

const test = z.strictObject({
  /** The share of the test's items to get right, in %: an almost-right item counts half. */
  pass: z.number().int().min(50).max(100).default(80),
  exercises: z.array(worksheetExercise).min(2).max(12),
})

export const unitSpec = z
  .strictObject({
    schema: schemaField(UNIT_SCHEMA),
    id,
    language: z.enum(WORD_LANGUAGES).default('sl'),
    level,
    emoji: text.max(16).default('📘'),
    title: withEn(80),
    /** What the learner can do after it, per base (English always), one sentence. */
    goal: withEn(300).optional(),
    /** The grammar book's pages it teaches; the first is the summary card at the end of the intro. */
    grammar: z.array(grammarRef).min(1).max(4),
    /** The words it uses (lemmas or short phrases), for the tutor and the word lookup. */
    words: z.array(text.max(60)).max(40).default([]),
    intro,
    worksheets: z.array(worksheet).min(2).max(4),
    test,
    machine_written: z.boolean().default(true),
    review: text.max(200).optional(),
  })
  .superRefine((u, ctx) => {
    titleIn(u.title, u.language, ctx, ['title'])
    introIn(u.intro, u.language, ctx, ['intro'])
    u.worksheets.forEach((w, i) => {
      titleIn(w.title, u.language, ctx, ['worksheets', i, 'title'])
      introIn(w.intro, u.language, ctx, ['worksheets', i, 'intro'])
    })
    const twice = u.worksheets.map(w => w.id).filter((w, i, all) => all.indexOf(w) !== i)
    if (twice.length) ctx.addIssue({ code: 'custom', path: ['worksheets'], message: `a worksheet id twice: ${twice.join(', ')}` })
    if (new Set(u.grammar).size !== u.grammar.length) ctx.addIssue({ code: 'custom', path: ['grammar'], message: 'a page twice' })
  })

export type Unit = z.infer<typeof unitSpec>

// --- the outline -------------------------------------------------------------------------------------------------

export const courseSpec = z
  .strictObject({
    schema: schemaField(COURSE_SCHEMA),
    language: z.enum(WORD_LANGUAGES).default('sl'),
    levels: z
      .array(
        z.strictObject({
          level,
          units: z
            .array(
              z.strictObject({
                id,
                title: withEn(80),
                /** Written later: greyed in the school, can't be opened, doesn't count for its level. */
                planned: z.boolean().default(false),
                /** The pages it is to teach, for whoever writes it. */
                grammar: z.array(grammarRef).max(4).default([]),
              }),
            )
            .max(40),
        }),
      )
      .min(1)
      .max(6),
    machine_written: z.boolean().default(true),
    review: text.max(200).optional(),
  })
  .superRefine((c, ctx) => {
    let last = -1
    c.levels.forEach((l, i) => {
      const at = LEVELS.indexOf(l.level)
      if (at <= last) ctx.addIssue({ code: 'custom', path: ['levels', i, 'level'], message: `${l.level} after ${LEVELS[last]}: the levels in order (${LEVELS.join(', ')}), each once` })
      last = Math.max(last, at)
      l.units.forEach((u, j) => titleIn(u.title, c.language, ctx, ['levels', i, 'units', j, 'title']))
    })
    const ids = c.levels.flatMap(l => l.units.map(u => u.id))
    const twice = ids.filter((u, i) => ids.indexOf(u) !== i)
    if (twice.length) ctx.addIssue({ code: 'custom', path: ['levels'], message: `a unit twice in the course: ${[...new Set(twice)].join(', ')}` })
  })

export type Course = z.infer<typeof courseSpec>
export type OutlineUnit = Course['levels'][number]['units'][number]

// --- homework ----------------------------------------------------------------------------------------------------

export const homeworkSpec = z
  .strictObject({
    schema: schemaField(WORKSHEET_SCHEMA),
    /** The unit it belongs to: one of the course that isn't planned. */
    unit: id,
    id: sheetId,
    /** The course's language (the village's): taken from the village when it is left out. */
    language: z.enum(WORD_LANGUAGES).default('sl'),
    title: withEn(80),
    intro: intro.optional(),
    exercises: z.array(worksheetExercise).min(1).max(8),
    machine_written: z.boolean().default(true),
  })
  .superRefine((w, ctx) => {
    titleIn(w.title, w.language, ctx, ['title'])
    introIn(w.intro, w.language, ctx, ['intro'])
  })

export type Homework = z.infer<typeof homeworkSpec>
/** Homework as served: the tutor's ([source]), and when it was last written ([published_at], ms). */
export type LoadedHomework = Homework & { source: 'tutor'; published_at: number }

/** What the bridge adds to homework it serves; homework fetched from GET /course can be published again as it is. */
const SERVED = ['source', 'published_at'] as const

type Result<K extends string, T> = ({ ok: true } & { [k in K]: T }) | { ok: false; errors: string }

/** [schema]'s parse of [input], then its learner's placeholders ({learner}, {m:…|f:…}) checked. */
function validate<K extends string, S extends z.ZodType>(schema: S, key: K, input: unknown): Result<K, z.infer<S>> {
  const r = schema.safeParse(input)
  if (!r.success) return { ok: false, errors: z.prettifyError(r.error) }
  const learner = learnerErrorsIn(r.data)
  return learner.length ? { ok: false, errors: learner.map(e => `✖ ${e}`).join('\n') } : ({ ok: true, [key]: r.data } as Result<K, z.infer<S>>)
}

export const validateCourse = (input: unknown) => validate(courseSpec, 'course', input)
export const validateUnit = (input: unknown) => validate(unitSpec, 'unit', input)
export function validateWorksheet(input: unknown) {
  const raw = input && typeof input === 'object' && !Array.isArray(input) ? Object.fromEntries(Object.entries(input).filter(([k]) => !(SERVED as readonly string[]).includes(k))) : input
  return validate(homeworkSpec, 'worksheet', raw)
}

/**
 * Every grammar page [v] names (a unit's pages, an exercise's, a gap's, a cell's, a line's, an intro question's): the
 * strings under a "grammar" key anywhere in it, each once.
 */
export function grammarNamed(v: unknown): string[] {
  const out = new Set<string>()
  const walk = (x: unknown) => {
    if (Array.isArray(x)) return x.forEach(walk)
    if (!x || typeof x !== 'object') return
    for (const [k, y] of Object.entries(x)) {
      if (k === 'grammar' && typeof y === 'string') out.add(y)
      else if (k === 'grammar' && Array.isArray(y)) y.forEach(g => typeof g === 'string' && out.add(g))
      else walk(y)
    }
  }
  walk(v)
  return [...out]
}

// --- the store ---------------------------------------------------------------------------------------------------

/** A unit as served: the unit and the tutor's homework for it, the newest first. */
export type ServedUnit = Unit & { homework: LoadedHomework[] }
/** A unit of the outline as served: [planned] when it has no unit file (yet). */
export type ServedOutlineUnit = OutlineUnit
/**
 * GET /course: the outline's levels (a unit file the outline lacks after its level's units; a unit is planned when it
 * has no file), every unit with its homework, and who wrote the outline.
 */
export type ServedCourse = {
  schema: typeof COURSE_SCHEMA
  language: string
  levels: { level: (typeof LEVELS)[number]; units: ServedOutlineUnit[] }[]
  units: ServedUnit[]
  machine_written: boolean
  review?: string
}

/** Where the course of [language] is: companion/course/sl for Slovene, companion/course/it for Italian … */
export const courseDir = (root: string, language: string) => join(root, language)

/**
 * The course of one language: the curated outline and units (read on every request, like the grammar book) and the
 * tutor's homework for its units (<data>/app/course/<unit>/<id>.json).
 */
export class CourseStore {
  private warned = new Set<string>() // path:mtime of invalid files already logged

  constructor(
    private curatedDir: string,
    readonly language: string,
    private tutorDir: string,
    private log: (...a: unknown[]) => void = () => {},
  ) {
    mkdirSync(tutorDir, { recursive: true })
  }

  /** Logs [why] of [path] once per version of the file. */
  private skip(path: string, why: string) {
    let key = path
    try {
      key = `${path}:${statSync(path).mtimeMs}`
    } catch {}
    if (!this.warned.has(key)) this.log(`skipping course file ${path}: ${why}`)
    this.warned.add(key)
  }

  /** The outline (course.json), or undefined when there is none or it doesn't validate. */
  outline(): Course | undefined {
    const path = join(this.curatedDir, 'course.json')
    if (!existsSync(path)) return undefined
    try {
      const v = validateCourse(JSON.parse(readFileSync(path, 'utf8')))
      if (!v.ok) throw new Error(v.errors)
      if (v.course.language !== this.language) throw new Error(`a course of "${v.course.language}", this one is "${this.language}"`)
      return v.course
    } catch (e) {
      this.skip(path, (e as Error).message)
      return undefined
    }
  }

  /** The valid unit files (units/<id>.json of this language), in their files' order. */
  units(): Unit[] {
    const dir = join(this.curatedDir, 'units')
    if (!existsSync(dir)) return []
    return readdirSync(dir)
      .filter(f => f.endsWith('.json'))
      .sort()
      .flatMap(f => {
        const path = join(dir, f)
        try {
          const v = validateUnit(JSON.parse(readFileSync(path, 'utf8')))
          if (!v.ok) throw new Error(v.errors)
          if (v.unit.language !== this.language) throw new Error(`a unit of "${v.unit.language}", this course is "${this.language}"`)
          if (`${v.unit.id}.json` !== f) throw new Error(`the unit "${v.unit.id}" belongs in ${v.unit.id}.json`)
          return [v.unit]
        } catch (e) {
          this.skip(path, (e as Error).message)
          return []
        }
      })
  }

  /** The tutor's homework for [unit], the newest first. */
  homework(unit: string): LoadedHomework[] {
    if (!COURSE_ID.test(unit)) return []
    const dir = join(this.tutorDir, unit)
    if (!existsSync(dir)) return []
    return readdirSync(dir)
      .filter(f => f.endsWith('.json'))
      .flatMap(f => {
        const path = join(dir, f)
        try {
          const v = validateWorksheet(JSON.parse(readFileSync(path, 'utf8')))
          if (!v.ok) throw new Error(v.errors)
          if (v.worksheet.unit !== unit || `${v.worksheet.id}.json` !== f) throw new Error(`homework "${v.worksheet.unit}/${v.worksheet.id}" belongs in ${v.worksheet.unit}/${v.worksheet.id}.json`)
          if (v.worksheet.language !== this.language) throw new Error(`homework of "${v.worksheet.language}", this course is "${this.language}"`)
          return [{ ...v.worksheet, source: 'tutor' as const, published_at: statSync(path).mtimeMs }]
        } catch (e) {
          this.skip(path, (e as Error).message)
          return []
        }
      })
      .sort((a, b) => b.published_at - a.published_at)
  }

  /**
   * The course as the app gets it (GET /course), or undefined when this language has none. A unit file must be of its
   * outline entry's level (else it is skipped); a unit is planned when it has no file, whatever the outline says.
   */
  served(): ServedCourse | undefined {
    const outline = this.outline()
    if (!outline) return undefined
    const listedAt = new Map(outline.levels.flatMap(l => l.units.map(u => [u.id, l.level] as const)))
    const files = this.units().filter(u => {
      const at = listedAt.get(u.id)
      if (at && at !== u.level) this.skip(join(this.curatedDir, 'units', `${u.id}.json`), `a unit of ${u.level}, the outline has it in ${at}`)
      return !at || at === u.level
    })
    const have = new Map(files.map(u => [u.id, u]))
    const levels = outline.levels.map(l => ({
      level: l.level,
      units: l.units.map(u => {
        if (!u.planned && !have.has(u.id)) this.skip(join(this.curatedDir, 'units', `${u.id}.json`), `the outline's unit "${u.id}" isn't planned but has no valid file: served as planned`)
        return { ...u, planned: !have.has(u.id) }
      }),
    }))
    // a unit file the outline lacks: after its level's units (its level added in order when the outline lacks it)
    for (const u of files) {
      if (listedAt.has(u.id)) continue
      let l = levels.find(x => x.level === u.level)
      if (!l) {
        l = { level: u.level, units: [] }
        levels.push(l)
        levels.sort((a, b) => LEVELS.indexOf(a.level) - LEVELS.indexOf(b.level))
      }
      l.units.push({ id: u.id, title: u.title, planned: false, grammar: u.grammar })
    }
    const order = levels.flatMap(l => l.units.map(u => u.id))
    // homework under the id of a worksheet the unit got since is left out: the learner's progress keeps a sheet by its id
    const homework = (u: Unit) => this.homework(u.id).filter(h => !u.worksheets.some(w => w.id === h.id))
    const units = order.flatMap(id => (have.has(id) ? [{ ...have.get(id)!, homework: homework(have.get(id)!) }] : []))
    return {
      schema: COURSE_SCHEMA,
      language: this.language,
      levels,
      units,
      machine_written: outline.machine_written,
      ...(outline.review ? { review: outline.review } : {}),
    }
  }

  /** The unit [id] of the course: written (its file), planned, or not in the course. */
  unit(id: string): { unit: Unit } | { planned: true } | undefined {
    const course = this.served()
    if (!course) return undefined
    const u = course.units.find(x => x.id === id)
    if (u) return { unit: u }
    return course.levels.some(l => l.units.some(x => x.id === id)) ? { planned: true } : undefined
  }

  /**
   * Writes the tutor's homework [ws] into its unit (replacing the tutor's earlier one of its id): whether it replaced
   * one. An error for a course there isn't, a unit there isn't or that is planned, an id of the unit's own worksheets,
   * homework of another language.
   */
  publish(ws: Homework): { replaced: boolean } | { error: string } {
    if (ws.language !== this.language) return { error: `this village's course is in "${this.language}", the homework is "${ws.language}"` }
    if (!this.outline()) return { error: `there is no course in "${this.language}" yet (companion/course/${this.language}/course.json)` }
    const u = this.unit(ws.unit)
    if (!u) return { error: `no unit "${ws.unit}" in the course (get_course lists them)` }
    if ('planned' in u) return { error: `the unit "${ws.unit}" is planned, not written yet: homework goes into a unit there is (get_course)` }
    if (u.unit.worksheets.some(w => w.id === ws.id)) return { error: `"${ws.id}" is a worksheet of the unit "${ws.unit}" itself: give the homework an id of its own` }
    const dir = join(this.tutorDir, ws.unit)
    mkdirSync(dir, { recursive: true })
    const path = join(dir, `${ws.id}.json`)
    const replaced = existsSync(path)
    writeFileSync(`${path}.tmp`, JSON.stringify({ ...ws, machine_written: true }, null, 2) + '\n')
    renameSync(`${path}.tmp`, path)
    return { replaced }
  }

  /** Removes the tutor's homework [id] of [unit]: whether there was one. */
  remove(unit: string, id: string): boolean {
    if (!COURSE_ID.test(unit) || !COURSE_ID.test(id)) return false
    const path = join(this.tutorDir, unit, `${id}.json`)
    if (!existsSync(path)) return false
    unlinkSync(path)
    return true
  }
}
