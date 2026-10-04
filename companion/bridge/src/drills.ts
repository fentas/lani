// lani.drill/v0 — the car's audio drills (companion/README.md, "Im Auto · In the car"): hands-free practice in the
// Pimsleur way, a prompt, a pause to say it aloud, the answer. Four kinds:
//   transform  "🔄 Preobrat · Transformations": a sentence and an instruction ("Into the past"), the sentence changed
//   rapid      "⚡ Hitri odziv · Rapid fire": a number, a time, a day in the base language, the word at once
//   build      "🧱 Gradnja stavkov · Sentence building": a sentence grown in steps ("Now add: tomorrow")
//   riddle     "🕵️ Uganke · Riddles": the storyteller's clues to a thing, an animal, a person or a place of the village
// Curated in companion/drills/<language>/<id>.json, one file a drill (machine-written, like the other curated content);
// served at GET /drills (features/drills.ts), bundled in the app (data/Drills.kt), and voiced by voice-build (the
// corpus's "drills" rows: drillTexts): the car plays only what has its clips.
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import { schemaField } from './schema'
import { grammarRef } from './grammar'
import { LANG, langText } from './langs'
import { WORD_LANGUAGES, type Pack } from './packs'
import { LEVELS, type Level } from './stories'

export const DRILL_SCHEMA = 'lani.drill/v0'
export const DRILL_KINDS = ['transform', 'rapid', 'build', 'riddle'] as const
export type DrillKind = (typeof DRILL_KINDS)[number]

const id = z.string().regex(/^[a-z0-9][a-z0-9-]{0,62}$/, 'must be kebab-case, 1-63 chars')
const level = z.enum(LEVELS)
const withEn = (t: Record<string, string>) => typeof t.en === 'string'

/**
 * A text said in the drill's language with its meaning in the bases: {"sl": "Micka kuha kosilo.", "en": "Micka is
 * cooking lunch.", "de": "Micka kocht das Mittagessen."}. English is always there (every base's fallback); the drill's
 * language is checked on the drill.
 */
const said = langText(240).refine(withEn, 'the English ("en") is missing')

/** What the learner hears in their base language before answering: {"en": "Into the past.", "de": "In die Vergangenheit."}. */
const cue = langText(160).refine(withEn, 'the English ("en") is missing')

/** A drill's or a set's name in its language and the bases: {"sl": "Preobrat", "en": "Transformations"}. */
const title = langText(80).refine(withEn, 'the English ("en") is missing')

/** A sentence and what it becomes: [from] heard, [to] said; [do] the item's own instruction (a set's is its `do`). */
const transformItem = z.strictObject({ from: said, to: said, do: cue.optional() })

/**
 * A set of one change ("V preteklik · Into the past"), of one rule of the grammar book ([rule]: the app leaves out a
 * set of a rule not introduced to the learner yet): its instruction [do] (in the drill's language, shown on the car's
 * screen, and in the bases, spoken) and its items.
 */
const transformSet = z.strictObject({ id, rule: grammarRef, level, do: cue, items: z.array(transformItem).min(3).max(80) })

/** Numbers to say, generated ([numberWord]): from [from] to [to] (every [step]th), or a [list]. 0 … 999. */
const n = z.number().int().min(0).max(999)
const numbers = z.union([
  z.strictObject({ from: n, to: n, step: z.number().int().min(1).max(500).optional() }).refine(r => r.from <= r.to, '"from" is after "to"'),
  z.strictObject({ list: z.array(n).min(1).max(300) }),
])

/**
 * A set of rapid fire ("Koliko je ura? · What time is it?"): [items], each said in the drill's language and asked in
 * the bases ({"sl": "Ura je tri.", "en": "It's three o'clock."}: "It's three o'clock" asked, "Ura je tri." the answer), or
 * [numbers] generated, asked as the base language's number.
 */
const rapidSet = z
  .strictObject({ id, level, rule: grammarRef.optional(), title, items: z.array(said).max(200).default([]), numbers: z.array(numbers).max(10).default([]) })
  .refine(s => s.items.length + s.numbers.length > 0, 'a set has items or numbers')

/**
 * A step of a sentence grown: what it adds ([add], in the bases: "tomorrow"; the first step has none, it is said as a
 * whole) and the sentence so far, said and meant ({"sl": "Jutri grem v trgovino.", "en": "Tomorrow I'm going to the
 * shop."}).
 */
const step = z
  .object({ add: cue.optional() })
  .catchall(z.string().trim().min(1).max(240))
  .superRefine((s, ctx) => {
    for (const k of Object.keys(s)) if (k !== 'add' && !LANG.test(k)) ctx.addIssue({ code: 'custom', path: [k], message: `"${k}" isn't a language code (sl, en, de …) or "add"` })
    if (typeof s.en !== 'string') ctx.addIssue({ code: 'custom', path: ['en'], message: 'the English ("en") is missing' })
  })

/** A sentence grown in 2–5 steps; [rules]: the grammar book's pages it asks for beyond the obvious (the app leaves out a build of a rule not yet). */
const build = z.strictObject({ id, level, rules: z.array(grammarRef).max(6).default([]), steps: z.array(step).min(2).max(5) })

/**
 * A riddle: [clues] said by the teller (3–4 short sentences: "Iz lesa sem. Imam štiri noge. Na meni sediš."), the
 * question ([ask]: "Kaj sem?"), and the [answer] ("Miza!"). What it is: a pack word ([word], "<pack>/<word id>": the
 * learner knows it from there) or a villager ([villager]).
 */
const riddle = z
  .strictObject({
    id,
    level,
    word: z.string().regex(/^[a-z0-9][a-z0-9-]*\/[a-z0-9][a-z0-9-]*$/, 'a pack word: "<pack>/<word id>"').optional(),
    villager: id.optional(),
    clues: z.array(said).min(2).max(5),
    ask: said,
    answer: said,
  })
  .refine(r => !(r.word && r.villager), 'a riddle is a word or a villager, not both')

const common = {
  schema: schemaField(DRILL_SCHEMA),
  id,
  /** The language drilled (what is said): Slovene unless it says otherwise. */
  language: z.enum(WORD_LANGUAGES).default('sl'),
  emoji: z.string().trim().min(1).max(16),
  title,
  /** What it is for, for the tutor and the docs. */
  about: langText(600).optional(),
  /** Written by a machine (Claude): the curated drills are. */
  machine_written: z.boolean().default(true),
  /** Who wrote which languages and who checked them: "machine-written, not reviewed by a native speaker". */
  review: z.string().trim().min(1).max(200).optional(),
}

export const drillSpec = z
  .discriminatedUnion('kind', [
    z.strictObject({ ...common, kind: z.literal('transform'), sets: z.array(transformSet).min(1).max(40) }),
    z.strictObject({ ...common, kind: z.literal('rapid'), sets: z.array(rapidSet).min(1).max(40) }),
    z.strictObject({ ...common, kind: z.literal('build'), builds: z.array(build).min(1).max(300) }),
    /** [teller]: the villager who tells the riddles (Stari Janez): in their voice, their own one once it is ready. */
    z.strictObject({ ...common, kind: z.literal('riddle'), teller: id.optional(), riddles: z.array(riddle).min(1).max(300) }),
  ])
  .superRefine((d, ctx) => {
    const lang = d.language
    const needs = (t: Record<string, string> | undefined, path: (string | number)[]) => {
      if (t && typeof t[lang] !== 'string') ctx.addIssue({ code: 'custom', path: [...path, lang], message: `the text in the drill's language ("${lang}") is missing` })
    }
    const dupes = (xs: string[], path: (string | number)[], what: string) => {
      const d = xs.filter((x, i) => xs.indexOf(x) !== i)
      if (d.length) ctx.addIssue({ code: 'custom', path, message: `the same ${what} twice: ${[...new Set(d)].join(', ')}` })
    }
    needs(d.title, ['title'])
    if (d.kind === 'transform') {
      dupes(d.sets.map(s => s.id), ['sets'], 'set id')
      d.sets.forEach((s, i) => {
        needs(s.do, ['sets', i, 'do'])
        s.items.forEach((it, j) => {
          needs(it.from, ['sets', i, 'items', j, 'from'])
          needs(it.to, ['sets', i, 'items', j, 'to'])
          if (it.from[lang] && norm(it.from[lang]) === norm(it.to[lang] ?? '')) ctx.addIssue({ code: 'custom', path: ['sets', i, 'items', j], message: 'the sentence doesn\'t change' })
        })
        dupes(s.items.map(it => norm(it.to[lang] ?? '')), ['sets', i, 'items'], 'answer')
      })
    } else if (d.kind === 'rapid') {
      dupes(d.sets.map(s => s.id), ['sets'], 'set id')
      d.sets.forEach((s, i) => {
        needs(s.title, ['sets', i, 'title'])
        s.items.forEach((it, j) => needs(it, ['sets', i, 'items', j]))
        dupes(s.items.map(it => norm(it[lang] ?? '')), ['sets', i, 'items'], 'answer')
        if (s.numbers.length && !numberWord(1, lang)) ctx.addIssue({ code: 'custom', path: ['sets', i, 'numbers'], message: `numbers are generated in Slovene only, the drill is "${lang}"` })
        dupes(s.numbers.flatMap(numbersOf).map(String), ['sets', i, 'numbers'], 'number')
      })
    } else if (d.kind === 'build') {
      dupes(d.builds.map(b => b.id), ['builds'], 'build id')
      d.builds.forEach((b, i) =>
        b.steps.forEach((s, j) => {
          needs(s as Record<string, string>, ['builds', i, 'steps', j])
          if (j === 0 && s.add) ctx.addIssue({ code: 'custom', path: ['builds', i, 'steps', 0, 'add'], message: 'the first step is said whole: it adds nothing' })
          if (j > 0 && !s.add) ctx.addIssue({ code: 'custom', path: ['builds', i, 'steps', j, 'add'], message: 'what the step adds ("add") is missing' })
          const prev = j > 0 ? String(b.steps[j - 1][lang] ?? '') : ''
          const now = String(s[lang] ?? '')
          if (j > 0 && now && !(norm(now).length > norm(prev).length)) ctx.addIssue({ code: 'custom', path: ['builds', i, 'steps', j], message: 'a step grows the sentence: it is longer than the one before' })
        }),
      )
    } else {
      dupes(d.riddles.map(r => r.id), ['riddles'], 'riddle id')
      d.riddles.forEach((r, i) => {
        r.clues.forEach((c, j) => needs(c, ['riddles', i, 'clues', j]))
        needs(r.ask, ['riddles', i, 'ask'])
        needs(r.answer, ['riddles', i, 'answer'])
      })
    }
  })

export type Drill = z.infer<typeof drillSpec>
export type TransformDrill = Extract<Drill, { kind: 'transform' }>
export type RapidDrill = Extract<Drill, { kind: 'rapid' }>
export type BuildDrill = Extract<Drill, { kind: 'build' }>
export type RiddleDrill = Extract<Drill, { kind: 'riddle' }>
/** A drill as served: [source] "curated" (companion/drills). */
export type LoadedDrill = Drill & { source: 'curated' }

export function validateDrill(input: unknown): { ok: true; drill: Drill } | { ok: false; errors: string } {
  const raw = input && typeof input === 'object' && !Array.isArray(input) ? Object.fromEntries(Object.entries(input).filter(([k]) => k !== 'source')) : input
  const r = drillSpec.safeParse(raw)
  return r.success ? { ok: true, drill: r.data } : { ok: false, errors: z.prettifyError(r.error) }
}

/** "Micka kuha kosilo." / "micka kuha kosilo" → the same sentence. */
const norm = (s: string) => s.toLocaleLowerCase().replace(/[.!?¿¡,;:«»„“”"…]/g, '').replace(/\s+/g, ' ').trim()

// --- numbers ------------------------------------------------------------------------------------------------

const SL_ONES = ['nič', 'ena', 'dva', 'tri', 'štiri', 'pet', 'šest', 'sedem', 'osem', 'devet']
const SL_TEENS = ['deset', 'enajst', 'dvanajst', 'trinajst', 'štirinajst', 'petnajst', 'šestnajst', 'sedemnajst', 'osemnajst', 'devetnajst']
const SL_TENS = ['', '', 'dvajset', 'trideset', 'štirideset', 'petdeset', 'šestdeset', 'sedemdeset', 'osemdeset', 'devetdeset']

/**
 * [n] (0 … 999) as it is counted in [language]: Slovene only ("enaindvajset", "dvesto petinštirideset"), undefined in
 * another language or out of range. The same as the app's SpokenNumbers (its counting form), which the car asks for.
 */
export function numberWord(n: number, language: string): string | undefined {
  if (language !== 'sl' || !Number.isInteger(n) || n < 0 || n > 999) return undefined
  const word = (x: number): string => {
    if (x < 10) return SL_ONES[x]
    if (x < 20) return SL_TEENS[x - 10]
    if (x < 100) return x % 10 === 0 ? SL_TENS[x / 10] : SL_ONES[x % 10] + 'in' + SL_TENS[Math.floor(x / 10)]
    const h = Math.floor(x / 100)
    const head = h === 1 ? 'sto' : h === 2 ? 'dvesto' : SL_ONES[h] + 'sto'
    return x % 100 === 0 ? head : `${head} ${word(x % 100)}`
  }
  return word(n)
}

/** The numbers a rapid set's range or list names, in its order. */
export function numbersOf(r: z.infer<typeof numbers>): number[] {
  if ('list' in r) return r.list
  const out: number[] = []
  for (let x = r.from; x <= r.to; x += r.step ?? 1) out.push(x)
  return out
}

// --- what is said -------------------------------------------------------------------------------------------

/**
 * One text a drill says in its language, as the car plays it: [teller] the villager who says it (a riddle drill's
 * teller), else the narrator; [level] the set's, build's or riddle's; [source] "drill:<id>".
 */
export type DrillText = { text: string; level: Level; teller?: string; source: string }

/**
 * Every text the drills of [language] say, as the car plays them: a transformation's sentence and its answer, a rapid
 * set's answers (its numbers generated), a build's steps, a riddle's clues, question and answer. The meanings are the
 * phone's to speak. Placeholders ({…}) are left out, like a scene's.
 */
export function drillTexts(drills: Drill[], language: string): DrillText[] {
  const out: DrillText[] = []
  for (const d of drills) {
    if ((d.language ?? 'sl') !== language) continue
    const source = `drill:${d.id}`
    const push = (t: Record<string, string> | undefined, lv: Level, teller?: string) => {
      const text = t?.[language]
      if (typeof text === 'string' && text && !text.includes('{')) out.push({ text, level: lv, ...(teller ? { teller } : {}), source })
    }
    if (d.kind === 'transform') for (const s of d.sets) for (const it of s.items) (push(it.from, s.level), push(it.to, s.level))
    else if (d.kind === 'rapid')
      for (const s of d.sets) {
        for (const it of s.items) push(it, s.level)
        for (const x of s.numbers.flatMap(numbersOf)) push({ [language]: numberWord(x, language) ?? '' }, s.level)
      }
    else if (d.kind === 'build') for (const b of d.builds) for (const st of b.steps) push(st as Record<string, string>, b.level)
    else for (const r of d.riddles) for (const t of [...r.clues, r.ask, r.answer]) push(t, r.level, d.teller)
  }
  return out
}

// --- references ---------------------------------------------------------------------------------------------

export type DrillRefs = {
  /** The grammar book's page ids of the drill's language. */
  grammar?: Set<string>
  packs?: (Pick<Pack, 'id' | 'words'> & { language?: Pack['language'] })[]
  villagers?: { id: string }[]
}

/**
 * What a drill names that isn't there: a rule the grammar book lacks (the app can't tell whether it was introduced: the
 * set is offered), a pack word or a villager a riddle is of, the teller. Said, not refused: a missing one only weakens
 * the gating or the voice.
 */
export function drillProblems(d: Drill, refs: DrillRefs): string[] {
  const out: string[] = []
  const rule = (r: string, where: string) => {
    if (refs.grammar && !refs.grammar.has(r)) out.push(`${where}: no grammar page "${r}"`)
  }
  const people = refs.villagers ? new Set(refs.villagers.map(v => v.id)) : undefined
  if (d.kind === 'transform') d.sets.forEach(s => rule(s.rule, `set ${s.id}`))
  else if (d.kind === 'rapid') d.sets.forEach(s => s.rule && rule(s.rule, `set ${s.id}`))
  else if (d.kind === 'build') d.builds.forEach(b => b.rules.forEach(r => rule(r, `build ${b.id}`)))
  else {
    if (d.teller && people && !people.has(d.teller)) out.push(`teller: no villager "${d.teller}"`)
    for (const r of d.riddles) {
      if (r.villager && people && !people.has(r.villager)) out.push(`riddle ${r.id}: no villager "${r.villager}"`)
      if (r.word && refs.packs) {
        const [pack, word] = r.word.split('/')
        const p = refs.packs.find(x => x.id === pack)
        if (!p) out.push(`riddle ${r.id}: no pack "${pack}"`)
        else if ((p.language ?? 'sl') !== d.language) out.push(`riddle ${r.id}: pack "${pack}" is of "${p.language}"`)
        else if (!p.words.some(w => w.id === word)) out.push(`riddle ${r.id}: no word "${word}" in pack "${pack}"`)
      }
    }
  }
  return out
}

/** Where the curated drills of [language] are: companion/drills/sl for Slovene. */
export const drillsDir = (root: string, language: string) => join(root, language)

/**
 * The drills of one language: the curated ones (read on every request, like the grammar book), in their files' order.
 * A file that doesn't validate is skipped and logged once; what a valid one names that isn't there is logged too.
 */
export class DrillStore {
  private warned = new Set<string>() // path:mtime of files already logged

  constructor(
    private curatedDir: string,
    readonly language: string,
    private refs: () => DrillRefs = () => ({}),
    private log: (...a: unknown[]) => void = () => {},
  ) {}

  all(): LoadedDrill[] {
    if (!existsSync(this.curatedDir)) return []
    return readdirSync(this.curatedDir)
      .filter(f => f.endsWith('.json'))
      .sort()
      .flatMap(f => {
        const path = join(this.curatedDir, f)
        const key = `${path}:${statSync(path).mtimeMs}`
        try {
          const v = validateDrill(JSON.parse(readFileSync(path, 'utf8')))
          if (!v.ok) throw new Error(v.errors)
          if (v.drill.language !== this.language) throw new Error(`a drill of "${v.drill.language}", this village speaks "${this.language}"`)
          if (!this.warned.has(key)) {
            const problems = drillProblems(v.drill, this.refs())
            if (problems.length) this.log(`drill ${path}: ${problems.join('; ')}`)
          }
          this.warned.add(key)
          return [{ ...v.drill, source: 'curated' as const }]
        } catch (e) {
          if (!this.warned.has(key)) this.log(`skipping drill ${path}: ${(e as Error).message}`)
          this.warned.add(key)
          return []
        }
      })
  }

  get(id: string): LoadedDrill | undefined {
    return this.all().find(d => d.id === id)
  }
}
