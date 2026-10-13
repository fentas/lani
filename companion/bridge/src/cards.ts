// lani.card/v0 — the cheat sheet's cards ("📋 Plonk listek · Cheat sheet", companion/README.md "Cheat sheet"): compact
// reference cards of the basics, a bold rule line, a small table, two or three examples, a 💡 tip at most. The app makes
// most of them itself, one from each page of the grammar book (its `card` when the page has one: grammar.ts pageCard).
// Here are the others: the curated ones no page fits (companion/cards/<language>/<id>.json: the pronouns 1 · 2 · 3+, kje
// or kam, the numbers …), and the tutor's (publish_card, <data>/app/cards), written in Markdown as the tutor answers in
// the chat. A card unlocks with the pages it names (`grammar`: the day the learner meets one), an A1 basic ([start]) and a
// tutor's at once. The learner's pinned cards (a tutor message kept) and how often they open each are the app's, in the
// village state (GameState.sheet), which list_cards reads.
import { existsSync, mkdirSync, readdirSync, readFileSync, renameSync, statSync, unlinkSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import { learnerErrorsIn } from './addressee'
import { example, grammarRef, table, withEn, type GrammarExample, type GrammarPage } from './grammar'
import { langText } from './langs'
import { WORD_LANGUAGES } from './packs'
import { schemaField } from './schema'
import { LEVELS } from './stories'

export const CARD_SCHEMA = 'lani.card/v0'
/** A card's id: kebab-case ("zaimki-osebe"). */
export const CARD_ID = /^[a-z0-9][a-z0-9-]{1,62}$/
export const cardRef = z.string().regex(CARD_ID, 'a card id (kebab-case, 2-63 chars)')

const text = z.string().trim().min(1)

/**
 * A card: [title] per language (the page's language and the bases, English always), or one string (the tutor's, in the
 * learner's base); either the curated face (a [rule] line per base, a [table], up to three [examples], a [tip]) or the
 * tutor's [markdown] (a table or a list, as in the chat). [grammar]: the pages it belongs to, which unlock it and bring it
 * first in their exercises; [start]: an A1 basic, there from the start.
 */
export const cardSpec = z
  .strictObject({
    schema: schemaField(CARD_SCHEMA),
    id: cardRef,
    language: z.enum(WORD_LANGUAGES).default('sl'),
    level: z.enum(LEVELS),
    emoji: text.max(16).default('📋'),
    title: z.union([text.max(80), langText(80)]),
    rule: withEn(300).optional(),
    table: table.optional(),
    examples: z.array(example).max(3).optional(),
    tip: withEn(300).optional(),
    markdown: text.max(2400).optional(),
    grammar: z.array(grammarRef).max(8).default([]),
    start: z.boolean().default(false),
    machine_written: z.boolean().default(true),
    review: text.max(200).optional(),
  })
  .superRefine((c, ctx) => {
    if (!c.markdown && !c.rule) ctx.addIssue({ code: 'custom', path: ['rule'], message: 'a card needs its rule line ("rule") or the tutor\'s "markdown"' })
    if (c.markdown && (c.rule || c.table || c.examples || c.tip)) ctx.addIssue({ code: 'custom', path: ['markdown'], message: 'a Markdown card is the whole card: no rule, table, examples or tip beside it' })
    if (typeof c.title === 'object') {
      if (typeof c.title.en !== 'string') ctx.addIssue({ code: 'custom', path: ['title', 'en'], message: 'the English title ("en") is missing' })
      if (typeof c.title[c.language] !== 'string') ctx.addIssue({ code: 'custom', path: ['title', c.language], message: `the title in the card's language ("${c.language}") is missing` })
    }
    c.examples?.forEach((e, i) => {
      if (typeof e[c.language] !== 'string') ctx.addIssue({ code: 'custom', path: ['examples', i, c.language], message: `the example in the card's language ("${c.language}") is missing` })
    })
  })

export type Card = z.infer<typeof cardSpec>
export type CardSource = 'curated' | 'tutor'
/** A card as served: [source] curated (companion/cards) or the tutor's; [published_at] when the tutor last wrote it. */
export type LoadedCard = Card & { source: CardSource; published_at?: number }

/** What the bridge adds to a card it serves: a served card can be published again as it is. */
const SERVED = ['source', 'published_at'] as const

export function validateCard(input: unknown): { ok: true; card: Card } | { ok: false; errors: string } {
  const raw = input && typeof input === 'object' && !Array.isArray(input) ? Object.fromEntries(Object.entries(input).filter(([k]) => !(SERVED as readonly string[]).includes(k))) : input
  const r = cardSpec.safeParse(raw)
  if (!r.success) return { ok: false, errors: z.prettifyError(r.error) }
  const learner = learnerErrorsIn(r.data)
  return learner.length ? { ok: false, errors: learner.map(e => `✖ ${e}`).join('\n') } : { ok: true, card: r.data }
}

/** "Imeti: vse osebe!" → "imeti-vse-osebe": a card's id from its title (letters without their marks). */
export function cardIdOf(title: string): string {
  const slug = title
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 48)
    .replace(/-+$/, '')
  return slug.length >= 2 ? slug : `card-${slug || 'x'}`
}

/** The card's title in English, for the tutor. */
export const cardTitleEn = (c: Pick<Card, 'title'>): string => (typeof c.title === 'string' ? c.title : (c.title.en ?? Object.values(c.title)[0] ?? ''))

/** Where the curated cards of [language] are: companion/cards/sl for Slovene. */
export const cardsDir = (root: string, language: string) => join(root, language)

/**
 * The cheat sheet's cards of one language that aren't a grammar page's: the curated ones (read on every request, like the
 * grammar book's) and the tutor's. A tutor's card can't take a curated card's id.
 */
export class CardStore {
  private warned = new Set<string>() // path:mtime of invalid files already logged

  constructor(
    private curatedDir: string,
    readonly language: string,
    private tutorDir: string,
    private log: (...a: unknown[]) => void = () => {},
  ) {
    mkdirSync(tutorDir, { recursive: true })
  }

  private read(dir: string, tutor: boolean): LoadedCard[] {
    if (!existsSync(dir)) return []
    return readdirSync(dir)
      .filter(f => f.endsWith('.json'))
      .sort()
      .flatMap(f => {
        const path = join(dir, f)
        try {
          const v = validateCard(JSON.parse(readFileSync(path, 'utf8')))
          if (!v.ok) throw new Error(v.errors)
          if (v.card.language !== this.language) throw new Error(`a card of "${v.card.language}", this sheet is "${this.language}"`)
          return [{ ...v.card, source: tutor ? ('tutor' as const) : ('curated' as const), ...(tutor ? { published_at: statSync(path).mtimeMs } : {}) }]
        } catch (e) {
          const key = `${path}:${statSync(path).mtimeMs}`
          if (!this.warned.has(key)) this.log(`skipping card ${path}: ${(e as Error).message}`)
          this.warned.add(key)
          return []
        }
      })
  }

  curated(): LoadedCard[] {
    return this.read(this.curatedDir, false)
  }

  tutor(): LoadedCard[] {
    return this.read(this.tutorDir, true)
  }

  /** Every card: the curated ones, then the tutor's (a tutor's under a curated id is left out). */
  all(): LoadedCard[] {
    const curated = this.curated()
    const ids = new Set(curated.map(c => c.id))
    return [...curated, ...this.tutor().filter(c => !ids.has(c.id))]
  }

  get(id: string): LoadedCard | undefined {
    return CARD_ID.test(id) ? this.all().find(c => c.id === id) : undefined
  }

  /** Writes the tutor's [card] (replacing the tutor's earlier one of its id); an error for a curated id or another language. */
  publish(card: Card): { replaced: boolean } | { error: string } {
    if (card.language !== this.language) return { error: `this cheat sheet is in "${this.language}", the card is "${card.language}"` }
    if (this.curated().some(c => c.id === card.id)) return { error: `"${card.id}" is a curated card's id: give yours another` }
    const path = join(this.tutorDir, `${card.id}.json`)
    const replaced = existsSync(path)
    writeFileSync(`${path}.tmp`, JSON.stringify({ ...card, machine_written: true }, null, 2) + '\n')
    renameSync(`${path}.tmp`, path)
    return { replaced }
  }

  /** Takes the tutor's card [id] back; false when the tutor has none of that id. */
  remove(id: string): boolean {
    if (!CARD_ID.test(id)) return false
    const path = join(this.tutorDir, `${id}.json`)
    if (!existsSync(path)) return false
    unlinkSync(path)
    return true
  }
}

/** A pinned card in the village state (the app's GameState.sheet.pinned): a tutor message the learner kept. */
type Pinned = { id?: string; language?: string; title?: string; pages?: string[]; level?: string; on?: string }
/** How often the learner opened a card (GameState.sheet.opens, "<language>/<card key>"): a count decaying by half in two weeks. */
type Use = { n?: number; on?: string }

/** Half-life of an open, in days (the app's CheatSheet.HALF_LIFE_DAYS). */
export const HALF_LIFE_DAYS = 14

/** [use]'s count on [today] (ISO), decayed by half every [HALF_LIFE_DAYS] since its day. */
export function decayed(use: Use | undefined, today: string): number {
  const n = typeof use?.n === 'number' && use.n > 0 ? use.n : 0
  if (!n || !use?.on) return n
  const days = Math.max(0, (Date.parse(today) - Date.parse(use.on)) / 86_400_000)
  return Number.isFinite(days) ? n * Math.pow(0.5, days / HALF_LIFE_DAYS) : n
}

/**
 * What the learner does with the sheet, from the village state ([sheet]: GameState.sheet) for [language]: the cards they
 * pinned, and the ones they open most (a count decaying over weeks), most first.
 */
export function sheetUse(sheet: unknown, language: string, today: string): { pinned: Pinned[]; opened: { card: string; opens: number; last?: string }[] } {
  const s = (sheet && typeof sheet === 'object' ? sheet : {}) as { pinned?: unknown; opens?: unknown }
  const pinned = (Array.isArray(s.pinned) ? (s.pinned as Pinned[]) : []).filter(p => p && typeof p === 'object' && (p.language ?? language) === language)
  const prefix = `${language}/`
  const opens = s.opens && typeof s.opens === 'object' ? (s.opens as Record<string, Use>) : {}
  const opened = Object.entries(opens)
    .filter(([k]) => k.startsWith(prefix))
    .map(([k, u]) => ({ card: k.slice(prefix.length), opens: Math.round(decayed(u, today) * 10) / 10, last: u?.on }))
    .filter(o => o.opens >= 0.1)
    .sort((a, b) => b.opens - a.opens)
  return { pinned: pinned.map(p => ({ id: p.id, title: p.title, pages: p.pages, level: p.level, on: p.on })), opened }
}

/**
 * The cards' examples heard with 🔊, as pages for the voice's corpus (voice.ts corpus: a grammar page's examples): each
 * grammar page's hand-written card's, and each curated or tutor's card's ("card:<id>").
 */
export function cardExamples(pages: Pick<GrammarPage, 'id' | 'language' | 'card'>[], cards: Pick<Card, 'id' | 'language' | 'examples'>[]): Pick<GrammarPage, 'id' | 'language' | 'examples'>[] {
  return [
    ...pages.flatMap(p => (p.card?.examples?.length ? [{ id: `card:${p.id}`, language: p.language, examples: p.card.examples as GrammarExample[] }] : [])),
    ...cards.flatMap(c => (c.examples?.length ? [{ id: `card:${c.id}`, language: c.language, examples: c.examples as GrammarExample[] }] : [])),
  ]
}
