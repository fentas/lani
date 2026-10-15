// Counting cards ("Štetje · Counting", companion/README.md "Counting cards"): once the learner has done the intro of the
// A1 unit stevila, their reviews hold a card per noun they have learned, which counts it 1, 2, 3–4 and 5+: en / ena / eno
// kava, dve kavi, tri kave, pet kav. The forms are the dictionary's (forms.ts: the noun's nominative singular, dual and
// plural and its genitive plural, its gender the lexicon's), never made by a rule: oče → dva očeta, dan → dva dneva,
// pesem → dve pesmi come out as they are, and a noun without a gender or one of the four forms gets no card. The card
// keeps its forms when it is made (the item's `counting`), so the app asks it offline. The app plays the four steps,
// grades them and sends how each went with the review (POST /reviews `counting`): a wrong step becomes a mistake of its
// kind in the mistakes database (COUNTING_PATTERNS), and the tutor's note names them. Pure: the route is
// features/counting.ts.
import { z } from 'zod'
import type { LemmaForms } from './forms'
import { normalizeForm, wordSlug, type WordGender } from './lexicon'

/** The item type of a counting card in spaced-repetition.json. */
export const COUNTING_TYPE = 'counting'
/** The course unit whose intro turns the cards on (companion/course/sl/units/stevila.json). */
export const COUNTING_UNIT = 'stevila'
/** The grammar book's pages a card is about: the counting rule's, and the gender's (step 1). */
export const COUNTING_PAGE = 'stevila-samostalniki'
export const GENDER_PAGE = 'spol-samostalnikov'

/** The four counts a card asks, in order: the number shown, and the slot of the forms book its noun stands in. */
export const COUNTS = [
  { n: 1, slot: 'nom.sg' },
  { n: 2, slot: 'nom.du' },
  { n: 3, slot: 'nom.pl' },
  { n: 5, slot: 'gen.pl' },
] as const
export type CountSlot = (typeof COUNTS)[number]['slot']

/** The numerals that agree with the noun's gender: 1 en/ena/eno, 2 dva/dve, 3 trije/tri, 5 pet. */
export const NUMERALS: Record<WordGender, readonly [string, string, string, string]> = {
  m: ['en', 'dva', 'trije', 'pet'],
  f: ['ena', 'dve', 'tri', 'pet'],
  n: ['eno', 'dve', 'tri', 'pet'],
}

/** The other forms of the noun a card keeps for its wrong choices (a case that doesn't fit after a number at all). */
const OTHER_SLOTS = ['gen.sg', 'acc.sg', 'loc.sg', 'ins.sg', 'dat.pl', 'loc.pl', 'ins.pl']
/** At most this many of them. */
const OTHERS = 6

/** What a counting card keeps of its noun (the item's `counting`): what the app asks it from, offline. */
export type CountingCard = {
  v: 1
  lemma: string
  gender: WordGender
  /** The noun's picture, where one of the learner's word packs has one. */
  emoji?: string
  /** What the noun means: the learner's card of it (their base). */
  meaning?: string
  /** The dictionary's forms of each count's slot: all are right, the [lines] show one. */
  forms: Record<CountSlot, string[]>
  /** The four lines the card shows: "ena kava", "dve kavi", "tri kave", "pet kav". */
  lines: [string, string, string, string]
  /** Other forms of the noun, for the wrong choices. */
  others: string[]
  /** Whose table the forms are: Wiktionary's, or the forms book's hand-checked one. */
  table: 'wiktionary' | 'checked'
}

/** Why a noun gets no card. */
export type NoCard = { why: string }

/**
 * The form a count's slot shows of its dictionary forms ([forms], at least one): a form that isn't also the slot it is
 * told from ([avoid]: the dual's -e is the plural's in ovce, sestre; the genitive plural's -a the singular's in sestra,
 * voda), then the one the content uses most ([uses]: "pet ovc", not ovac), then the dictionary's first.
 */
function shown(forms: string[], avoid: Set<string>, uses: (form: string) => number): string {
  const ranked = forms
    .map((f, i) => ({ f, i, clash: avoid.has(f) ? 1 : 0, used: uses(f) }))
    .sort((a, b) => a.clash - b.clash || b.used - a.used || a.i - b.i)
  return ranked[0].f
}

/**
 * The counting card of a noun's dictionary entry ([e], from formsOf), or why there is none: not a noun, no gender, or one
 * of the four forms missing. [uses]: how often the content says a form (the shown form's tie-break). The lines show the
 * numeral that agrees with its gender and one form of each slot; every form of a slot is right.
 */
export function countingOf(e: LemmaForms, uses: (form: string) => number = () => 0, language = 'sl'): CountingCard | NoCard {
  if (e.pos !== 'noun') return { why: `not a noun (${e.pos})` }
  if (!e.gender) return { why: 'the dictionary gives no gender' }
  const norm = (s: string) => normalizeForm(s, language)
  const slot = (k: string) => [...new Set((e.slots.find(s => s.key === k)?.forms ?? []).map(norm).filter(Boolean))]
  const forms = Object.fromEntries(COUNTS.map(c => [c.slot, slot(c.slot)])) as Record<CountSlot, string[]>
  const missing = COUNTS.filter(c => !forms[c.slot].length).map(c => c.slot)
  if (missing.length) return { why: `no ${missing.join(', ')} in the dictionary` }
  const lemma = norm(e.lemma)
  const sg = new Set(forms['nom.sg'])
  const one = forms['nom.sg'].includes(lemma) ? lemma : shown(forms['nom.sg'], new Set(), uses)
  const two = shown(forms['nom.du'], new Set([...forms['nom.pl'], ...sg]), uses)
  const three = shown(forms['nom.pl'], sg, uses)
  const five = shown(forms['gen.pl'], sg, uses)
  const n = NUMERALS[e.gender]
  const counted = new Set(COUNTS.flatMap(c => forms[c.slot]))
  const others = [...new Set(OTHER_SLOTS.flatMap(slot))].filter(f => !counted.has(f)).slice(0, OTHERS)
  return {
    v: 1,
    lemma,
    gender: e.gender,
    forms,
    lines: [`${n[0]} ${one}`, `${n[1]} ${two}`, `${n[2]} ${three}`, `${n[3]} ${five}`],
    others,
    table: e.table,
  }
}

export const isCard = (c: CountingCard | NoCard): c is CountingCard => 'lines' in c

/** A counting card's review item: counting_kava. */
export const countingItemId = (lemma: string) => `counting_${wordSlug(lemma)}`

/**
 * The update-db.py `new_vocabulary` entry of a card: type counting, the noun on its front and the four lines on its
 * back (what card.py and the tutor read), its forms in `counting`; due [due] (the day it is made: it hasn't been asked).
 */
export function countingVocabulary(id: string, card: CountingCard, due: string) {
  return {
    item_id: id,
    item_type: COUNTING_TYPE,
    content: card.lemma,
    answer: card.lines.join(' · '),
    category: COUNTING_TYPE,
    difficulty: 'A1',
    initial_quality: 3,
    priority: 'medium',
    source: COUNTING_TYPE,
    due_date: due,
    counting: card,
  }
}

/** Whether a vocabulary item counts as learned, as the app counts it (data/Stats.kt `learned`): answered right, not rusty. */
export function learnedItem(item: Record<string, unknown>): boolean {
  const n = (k: string) => (typeof item[k] === 'number' ? (item[k] as number) : 0)
  const lookup = item.source === 'lookup'
  const rusty = n('total_reviews') >= 1 && n('repetitions') === 0 && 'last_quality' in item && n('last_quality') < 3 && !lookup
  return !rusty && (n('repetitions') >= 1 || n('mastery_level') >= 1 || (n('total_reviews') === 0 && n('last_quality') >= 3 && !lookup))
}

// --- how a card went: POST /reviews `counting` -------------------------------------------------------------------

/**
 * What a wrong step was wrong about: the numeral's agreement with the noun's gender (en/ena/eno; dva/dve, trije/tri with
 * the noun's form right), the dual (2), the plural (3–4), the genitive plural after 5 and more.
 */
export const COUNTING_KINDS = ['gender', 'dual', 'plural', 'genitive_plural'] as const
export type CountingKind = (typeof COUNTING_KINDS)[number]

/**
 * The mistake pattern of each kind (mistakes-db.json `error_patterns`): its id names the grammar book's pages it is on by
 * their tags (grammar.ts tagMatches: "counting" the counting rule's, "noun_gender" the gender's, "dual", "plural"); the
 * genitive plural's says no "genitive", so it never lands on the A2 genitive page (an A1 learner's mistake there would
 * bring that rule into their dialogs).
 */
export const COUNTING_PATTERNS: Record<CountingKind, { id: string; description: string }> = {
  gender: { id: 'counting_noun_gender', description: "Counting: the numeral agrees with the noun's gender (en brat, ena kava, eno jajce; dva/dve, trije/tri)" },
  dual: { id: 'counting_dual', description: 'Counting: 2 takes the dual (dva brata, dve kavi, dve jajci)' },
  plural: { id: 'counting_plural', description: 'Counting: 3 and 4 take the nominative plural (trije bratje, tri kave, tri jajca)' },
  genitive_plural: { id: 'counting_from_five', description: 'Counting: from 5 on, the genitive plural (pet bratov, pet kav, pet jajc)' },
}

const line = z.string().trim().max(80)
const countingStep = z.object({
  n: z.union([z.literal(1), z.literal(2), z.literal(3), z.literal(5)]),
  right: z.boolean(),
  kind: z.enum(COUNTING_KINDS).optional(),
  answer: line.optional(),
  expected: line.optional(),
})
const countingRun = z.object({
  item_id: z.string().trim().min(1).max(128),
  lemma: z.string().trim().min(1).max(80),
  typed: z.boolean().optional(),
  steps: z.array(countingStep).min(1).max(4),
})
export type CountingRun = z.infer<typeof countingRun>

/** POST /reviews' `counting` as the app sent it, read leniently: a card that doesn't read is left out, never the review. */
export function countingRuns(raw: unknown): CountingRun[] {
  if (!Array.isArray(raw)) return []
  return raw.slice(0, 40).flatMap(r => {
    const p = countingRun.safeParse(r)
    return p.success ? [p.data] : []
  })
}

/** The update-db.py `errors` of the wrong steps: each a mistake of its kind, with what was said and what was right. */
export function countingErrors(runs: CountingRun[]) {
  return runs.flatMap(r =>
    r.steps.filter(s => !s.right && s.kind).map(s => ({
      pattern_id: COUNTING_PATTERNS[s.kind!].id,
      category: 'grammar',
      subcategory: 'counting',
      description: COUNTING_PATTERNS[s.kind!].description,
      severity: 'moderate',
      your_answer: s.answer ?? '',
      correct_answer: s.expected ?? '',
      context: `counting card ${r.item_id} (${r.lemma}), ${s.n}${s.n === 5 ? '+' : ''}`,
    })),
  )
}

/**
 * " Counting cards: 2 (kava 4/4; sin 3/4, typed): sin 2 «dve sinova» for «dva sinova» (counting_noun_gender)." for the
 * tutor; empty without counting cards.
 */
export function countingNote(runs: CountingRun[]): string {
  if (!runs.length) return ''
  const cards = runs.map(r => `${r.lemma} ${r.steps.filter(s => s.right).length}/${r.steps.length}${r.typed ? ', typed' : ''}`)
  const wrong = runs.flatMap(r =>
    r.steps.filter(s => !s.right).map(s => `${r.lemma} ${s.n}${s.n === 5 ? '+' : ''} «${s.answer || '…'}» for «${s.expected ?? '?'}»${s.kind ? ` (${COUNTING_PATTERNS[s.kind].id})` : ''}`),
  )
  const kept = wrong.length ? ` Their mistakes are ALREADY in the mistakes database: ${wrong.slice(0, 8).join('; ')}${wrong.length > 8 ? `; ${wrong.length - 8} more` : ''}.` : ''
  return ` Counting cards: ${runs.length} (${cards.join('; ')}).${kept}`
}
