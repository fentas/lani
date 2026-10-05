// lani.grammar/v0 — the grammar book's pages: one rule of the language learned, explained in the learner's base
// languages, with a table, examples and links to what practises it. The app's village book has them as its chapter
// "📖 Slovnica · Grammar": a page unlocks the first time Jan meets its rule (an exercise's "grammar" field names it).
// Curated pages live in companion/grammar/<language>/ (Slovene: companion/grammar/sl/); the tutor publishes more, or
// extends a curated one, to <data>/app/grammar. Keep in sync with .claude/skills/lani-studio/reference/grammar-spec.md.
import { existsSync, mkdirSync, readdirSync, readFileSync, renameSync, statSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import { learnerErrorsIn } from './addressee'
import { schemaField } from './schema'
import { LANG, langText, textIn, type LangText } from './langs'
import { WORD_LANGUAGES } from './packs'

export const GRAMMAR_SCHEMA = 'lani.grammar/v0'
/** A page's id: kebab-case, what an exercise's "grammar" field names ("kje-mestnik-orodnik"). */
export const GRAMMAR_ID = /^[a-z0-9][a-z0-9-]{1,62}$/
/** A tag: matched against mistakes-db pattern ids and review card categories and ids ("accusative", "grammar_biti"). */
export const GRAMMAR_TAG = /^[a-z0-9][a-z0-9_-]{0,39}$/

const text = z.string().trim().min(1)
export const grammarRef = z.string().regex(GRAMMAR_ID, 'a grammar page id (kebab-case, 2-63 chars)')

/** A text per language that has its English: {"en": "…", "de": "…", "it": "…"}. English is the fallback of every base. */
const withEn = (max: number) => langText(max).refine(t => typeof t.en === 'string', 'the English ("en") is missing')

/** A table cell: words of the language learned as they are ("ob ribniku"), or a text per language ({"sl": "mestnik", "en": "locative"}). */
const cell = z.union([text.max(80), langText(80)])

const table = z
  .strictObject({ head: z.array(cell).min(2).max(4), rows: z.array(z.array(cell)).min(1).max(12) })
  .superRefine((t, ctx) => {
    t.rows.forEach((r, i) => {
      if (r.length !== t.head.length) ctx.addIssue({ code: 'custom', path: ['rows', i], message: `${r.length} cells, the head has ${t.head.length}` })
    })
  })

/**
 * An example: the sentence in the page's language and its translation in each base ({"sl": "Šotor stoji ob ribniku.",
 * "en": "The tent stands by the pond.", "de": …, "it": …}), an optional [note] per base, and [by] "tutor" on what the
 * tutor added to a curated page (the bridge sets it).
 */
const example = z
  .object({ note: withEn(300).optional(), by: z.literal('tutor').optional() })
  .catchall(text.max(240))
  .superRefine((e, ctx) => {
    for (const k of Object.keys(e)) {
      if (k !== 'note' && k !== 'by' && !LANG.test(k)) ctx.addIssue({ code: 'custom', path: [k], message: `"${k}" isn't a language code (sl, en, it …), "note" or "by"` })
    }
    if (typeof e.en !== 'string') ctx.addIssue({ code: 'custom', path: ['en'], message: 'the English translation ("en") is missing' })
  })

export const grammarSpec = z
  .strictObject({
    schema: schemaField(GRAMMAR_SCHEMA),
    id: grammarRef,
    /** The language the rule is of (what is learned): Slovene unless it says otherwise. */
    language: z.enum(WORD_LANGUAGES).default('sl'),
    level: z.enum(['A1', 'A2', 'B1', 'B2', 'C1', 'C2']),
    emoji: text.max(16).default('📖'),
    /** In the page's language and in the bases: {"sl": "Kje? Predlogi kraja", "en": "Where? Prepositions of place", …}. */
    title: withEn(80),
    /** The rule itself, a few sentences per base (English always), the page's terms in brackets: "the locative (mestnik)". */
    rule: withEn(1200),
    /** A clearer or longer explanation the tutor added, per base: the app shows it as the tutor's. */
    more: withEn(1200).optional(),
    table: table.optional(),
    examples: z.array(example).min(1).max(24),
    /** The modules that practise the rule (companion/modules, or the tutor's). */
    modules: z.array(grammarRef).max(10).default([]),
    /** What the rule's mistakes and review cards are called: mistakes-db pattern ids, card categories and ids. */
    tags: z.array(z.string().regex(GRAMMAR_TAG, 'lowercase a-z, 0-9, _ and -')).max(20).default([]),
    /** Related pages ("see also"). */
    see: z.array(grammarRef).max(8).default([]),
    /** Written by a machine (Claude): the curated pages and every tutor page are. */
    machine_written: z.boolean().default(true),
    /** Who wrote which languages and who checked them: "machine-written, not reviewed by a native speaker". */
    review: text.max(200).optional(),
  })
  .superRefine((p, ctx) => {
    if (typeof p.title[p.language] !== 'string') ctx.addIssue({ code: 'custom', path: ['title', p.language], message: `the title in the page's language ("${p.language}") is missing` })
    p.examples.forEach((e, i) => {
      if (typeof e[p.language] !== 'string') ctx.addIssue({ code: 'custom', path: ['examples', i, p.language], message: `the example in the page's language ("${p.language}") is missing` })
    })
    const dupes = p.examples.map(e => norm(String(e[p.language] ?? ''))).filter((s, i, all) => s && all.indexOf(s) !== i)
    if (dupes.length) ctx.addIssue({ code: 'custom', path: ['examples'], message: `the same example twice: ${dupes.join(', ')}` })
    if (p.see.includes(p.id)) ctx.addIssue({ code: 'custom', path: ['see'], message: 'a page doesn\'t see itself' })
  })

export type GrammarPage = z.infer<typeof grammarSpec>
export type GrammarExample = GrammarPage['examples'][number]
export type GrammarSource = 'curated' | 'tutor'
/**
 * A page as served: [source] "curated" (companion/grammar) or "tutor" (a page of the tutor's own); [extended] a curated
 * page the tutor added to (its examples marked "by": "tutor", its "more"); [published_at] when the tutor last wrote it.
 */
export type LoadedGrammar = GrammarPage & { source: GrammarSource; extended?: boolean; published_at?: number }

/** What the bridge adds to a page it serves; a page fetched with get_grammar can be published again as it is. */
const SERVED = ['source', 'extended', 'published_at'] as const

export function validateGrammar(input: unknown): { ok: true; page: GrammarPage } | { ok: false; errors: string } {
  const raw = input && typeof input === 'object' && !Array.isArray(input) ? Object.fromEntries(Object.entries(input).filter(([k]) => !(SERVED as readonly string[]).includes(k))) : input
  const r = grammarSpec.safeParse(raw)
  if (!r.success) return { ok: false, errors: z.prettifyError(r.error) }
  const learner = learnerErrorsIn(r.data)
  return learner.length ? { ok: false, errors: learner.map(e => `✖ ${e}`).join('\n') } : { ok: true, page: r.data }
}

/** "Šotor stoji ob ribniku." / "šotor stoji ob ribniku" → the same example. */
const norm = (s: string) => s.toLocaleLowerCase().replace(/[.!?¿¡,;:«»„“”"]/g, '').replace(/\s+/g, ' ').trim()

/** An example in its page's language. */
export const exampleOf = (p: Pick<GrammarPage, 'language'>, e: GrammarExample): string => String(e[p.language] ?? '')

/** The page's title in English, for the tutor. */
export const titleEn = (p: Pick<GrammarPage, 'title'>): string => textIn(p.title, 'en')

/** The page's title as the app shows it for [target] · [base]: "Kje? Predlogi kraja · Where? Prepositions of place". */
export function titleFor(p: Pick<GrammarPage, 'title'>, target: string, base: string): string {
  const t = p.title[target] ?? textIn(p.title, target)
  const b = base !== target ? p.title[base] ?? p.title.en : undefined
  return b && b !== t ? `${t} · ${b}` : t
}

/**
 * A curated page with what the tutor added ([tutor]: the page the tutor published under its id): the curated title, rule,
 * table, level and review stay; the tutor's examples that aren't the curated page's come after them, marked "by":
 * "tutor"; the tutor's [more] is kept; modules, tags and related pages are both pages'.
 */
export function extend(curated: GrammarPage, tutor: GrammarPage): GrammarPage {
  const have = new Set(curated.examples.map(e => norm(exampleOf(curated, e))))
  const added = tutor.examples.filter(e => !have.has(norm(exampleOf(curated, e)))).map(e => ({ ...e, by: 'tutor' }) as GrammarExample)
  const union = (a: string[], b: string[]) => [...new Set([...a, ...b])]
  return {
    ...curated,
    ...(tutor.more ? { more: tutor.more } : {}),
    examples: [...curated.examples, ...added].slice(0, 24),
    modules: union(curated.modules, tutor.modules).slice(0, 10),
    tags: union(curated.tags, tutor.tags).slice(0, 20),
    see: union(curated.see, tutor.see).filter(id => id !== curated.id).slice(0, 8),
  }
}

/** Mistakes and cards a page's [tags] name: the same, or its words in a row of theirs ("accusative" in "accusative_feminine_a_to_o"). */
export function tagMatches(tag: string, id: string): boolean {
  const t = tag.toLowerCase().split(/[_-]+/).filter(Boolean)
  const w = id.toLowerCase().split(/[_-]+/).filter(Boolean)
  if (!t.length || t.length > w.length) return false
  for (let i = 0; i + t.length <= w.length; i++) if (t.every((x, j) => w[i + j] === x)) return true
  return false
}

/** Where the curated pages of [language] are: companion/grammar/sl for Slovene, companion/grammar/it for Italian … */
export const grammarDir = (root: string, language: string) => join(root, language)

/**
 * The grammar book of one language: the curated pages (read on every request, like the packs) and the tutor's. A tutor
 * page under a curated id extends that page ([extend]); any other is the tutor's own.
 */
export class GrammarStore {
  private warned = new Set<string>() // path:mtime of invalid files already logged

  constructor(
    private curatedDir: string,
    readonly language: string,
    private tutorDir: string,
    private log: (...a: unknown[]) => void = () => {},
  ) {
    mkdirSync(tutorDir, { recursive: true })
  }

  private read(dir: string, tutor: boolean): (GrammarPage & { published_at?: number })[] {
    if (!existsSync(dir)) return []
    return readdirSync(dir)
      .filter(f => f.endsWith('.json'))
      .sort()
      .flatMap(f => {
        const path = join(dir, f)
        try {
          const v = validateGrammar(JSON.parse(readFileSync(path, 'utf8')))
          if (!v.ok) throw new Error(v.errors)
          if (v.page.language !== this.language) throw new Error(`a page of "${v.page.language}", this book is "${this.language}"`)
          return [{ ...v.page, ...(tutor ? { published_at: statSync(path).mtimeMs } : {}) }]
        } catch (e) {
          const key = `${path}:${statSync(path).mtimeMs}`
          if (!this.warned.has(key)) this.log(`skipping grammar page ${path}: ${(e as Error).message}`)
          this.warned.add(key)
          return []
        }
      })
  }

  /** The curated pages alone, in their files' order. */
  curated(): GrammarPage[] {
    return this.read(this.curatedDir, false)
  }

  /** Every page: the curated ones (with what the tutor added), then the tutor's own. */
  all(): LoadedGrammar[] {
    const tutor = new Map(this.read(this.tutorDir, true).map(p => [p.id, p]))
    const curated = this.curated()
    const ids = new Set(curated.map(p => p.id))
    const out: LoadedGrammar[] = curated.map(c => {
      const t = tutor.get(c.id)
      return t ? { ...extend(c, t), source: 'curated', extended: true, published_at: t.published_at } : { ...c, source: 'curated' }
    })
    for (const [id, t] of tutor) if (!ids.has(id)) out.push({ ...t, source: 'tutor' })
    return out
  }

  get(id: string): LoadedGrammar | undefined {
    return GRAMMAR_ID.test(id) ? this.all().find(p => p.id === id) : undefined
  }

  /**
   * Writes the tutor's [page] (replacing the tutor's earlier one with its id). Under a curated id it extends that page:
   * returns how many of its examples are new. An error for a page of another language.
   */
  publish(page: GrammarPage): { extended: boolean; added: number } | { error: string } {
    if (page.language !== this.language) return { error: `this village's grammar book is in "${this.language}", the page is "${page.language}"` }
    const curated = this.curated().find(p => p.id === page.id)
    const path = join(this.tutorDir, `${page.id}.json`)
    writeFileSync(`${path}.tmp`, JSON.stringify({ ...page, machine_written: true }, null, 2) + '\n')
    renameSync(`${path}.tmp`, path)
    if (!curated) return { extended: false, added: page.examples.length }
    return { extended: true, added: extend(curated, page).examples.length - curated.examples.length }
  }
}

/** A page's texts in [base] for the tutor's summaries: the title in English and the rule's languages. */
export function pageInfo(p: LoadedGrammar): Record<string, unknown> {
  return {
    id: p.id,
    title: titleEn(p),
    title_target: p.title[p.language],
    level: p.level,
    source: p.source,
    ...(p.extended ? { extended: true } : {}),
    examples: p.examples.length,
    ...(p.examples.some(e => e.by === 'tutor') ? { yours: p.examples.filter(e => e.by === 'tutor').length } : {}),
    rule_languages: Object.keys(p.rule),
    modules: p.modules,
    tags: p.tags,
  }
}

/**
 * How often the learner met a rule in the dialogs before it was introduced: the app's GameState.meetings
 * ("<language>/<id>"; companion/GAME.md, "Rules not yet"). A turn of a rule not yet says its form without asking for it.
 */
export type Meetings = { times?: number; days?: number; first?: string; last?: string }

/** Meetings that make a rule ripe to introduce (the app's Introduction.RIPE). */
export const RIPE = 3

const LEVELS = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2']

/** The next level's page of a learner at [learner] (i + 1): what the tutor introduces next. */
const nextLevel = (page: string, learner: string) => {
  const p = LEVELS.indexOf(page)
  const l = LEVELS.indexOf(learner)
  return p >= 0 && l >= 0 && p === l + 1
}

/**
 * The rules ripe to introduce (companion/GAME.md, "Rules not yet"): pages of the next level above the learner's
 * ([level]) whose page isn't unlocked ([met]: the app's GameState.grammar, by id) and that the dialogs said [RIPE] times or
 * more without asking for them ([meetings], by id). Most met first. The app opens such a page itself after more meetings;
 * the tutor can introduce it before (a module that names it, or the page extended for the learner).
 */
export function ripeRules(pages: LoadedGrammar[], meetings: Record<string, Meetings>, met: Record<string, unknown>, level: string) {
  return pages
    .filter(p => !met[p.id] && (meetings[p.id]?.times ?? 0) >= RIPE && nextLevel(p.level, level))
    .map(p => {
      const m = meetings[p.id]!
      return { id: p.id, title: titleEn(p), level: p.level, met: m.times ?? 0, days: m.days ?? 1, first: m.first, last: m.last }
    })
    .sort((a, b) => b.met - a.met)
}

/** [all] (the village state's "<language>/<id>" map) of [language], by id. */
export function ofLanguage<T>(all: Record<string, T> | undefined, language: string): Record<string, T> {
  const prefix = `${language}/`
  return Object.fromEntries(Object.entries(all ?? {}).filter(([k]) => k.startsWith(prefix)).map(([k, v]) => [k.slice(prefix.length), v]))
}

export type { LangText }
