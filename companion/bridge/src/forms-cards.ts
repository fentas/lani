// Forms cards ("📋 Oblike · Forms", companion/README.md "Forms cards"): one word with its whole table in a review card:
// a noun's six cases by number, a verb's present, negative, future, past and imperative, an adjective's genders and its
// cases. The forms are the dictionary's (forms.ts formsOf: Wiktionary's, or the forms book's hand-checked table), never
// made by a rule; the one exception is the future of a verb that has none of its own, joined from two dictionary forms:
// biti's future (bom, boš, bodo, also bojo) and the verb's l-form (delal, delali): "bom delal". What's tested is what the
// learner has been introduced to: a slot whose grammar page (and the pages it needs) was met in the grammar book or
// taught by a course unit whose intro is done (the app works that out and sends it as `introduced`). In a review the app
// shows the sections with a gap asked now, the others hidden; the whole table is its overview, to read. A slot not
// introduced is a preview, until its page is: then the card comes back soon (resurfacing).
// A card keeps its table when it is made (the item's `forms`), so the app asks it offline; the app plays it and sends
// how its cells went with the review (POST /reviews `forms_cards`): a wrong cell becomes a mistake of its kind in the
// mistakes database (FORMS_PATTERNS). Pure: the route is features/forms-cards.ts.
import { z } from 'zod'
import { CASE_PAGES, pageOf, type LemmaForms, type SlotPage } from './forms'
import { normalizeForm, wordSlug, type WordGender } from './lexicon'

/** The item type of a forms card in spaced-repetition.json. */
export const FORMS_TYPE = 'forms'

/** A slot of a card: its forms (all right, the first shown), the forms also accepted, the page it counts on. */
export type CardSlot = { key: string; forms: string[]; also?: string[]; page: string; needs?: string[]; composed?: true }

/** What a forms card keeps of its word (the item's `forms`): what the app asks it from, offline. */
export type FormsCardData = {
  v: 1
  lemma: string
  pos: 'noun' | 'verb' | 'adj'
  gender?: WordGender
  gloss: string[]
  meaning?: string
  emoji?: string
  table: 'wiktionary' | 'checked'
  slots: CardSlot[]
  /** The slots tested when the bridge last scheduled the card (by the learner's introduced pages). */
  tested: string[]
  /** The slots newly tested at the last resurfacing: the app plays them first. */
  fresh?: string[]
  /** The day of that resurfacing. */
  unlocked?: string
  /** The day the resurfacing planned the card for (the per-day cap counts these). */
  resurface?: string
  /** Made on demand, from a word's card. */
  asked?: true
}

export type NoCard = { why: string }

export const isCard = (c: FormsCardData | NoCard): c is FormsCardData => 'slots' in c

/** The lemma's own slot (the card's head, shown as given, never tested). */
export const OWN_KEYS = new Set(['nom.sg', 'adj.m.sg'])

/** At least this many slots besides the lemma's own, and this many different forms in all, make a card. */
export const MIN_SLOTS = 4
export const MIN_DISTINCT = 3
/** Slots tested a card made by itself needs; one made on demand needs one. */
export const MIN_TESTED = 2
export const MIN_TESTED_ASKED = 1

/** The everyday verbs that get their cards first, in this order. */
export const CORE = ['biti', 'imeti', 'iti', 'delati', 'hoteti', 'moči', 'vedeti', 'videti', 'reči', 'dati', 'priti', 'govoriti', 'piti', 'jesti', 'spati', 'živeti']

/** What the card needs besides the word's dictionary entry. */
export type CardContext = {
  /** biti's future (the forms book's hand-checked table): fut.1sg → its forms and those also accepted. Empty: no composed future. */
  bitiFuture: Record<string, { forms: string[]; also: string[] }>
  /** The adjective's forms beyond its slots (forms.ts adjectiveForms). */
  adjective?: (lemma: string) => Map<string, string[]>
  /** The grammar book's pages: a slot of a page it lacks (or needs) isn't kept. */
  pages: Set<string>
  /** The learner is a woman: the first person's future shows the feminine l-form first. */
  female?: boolean
  language?: string
}

const PERSONS = ['1sg', '2sg', '3sg', '1pl', '2pl', '3pl', '1du', '2du', '3du']
const NUMBER_OF: Record<string, string> = { sg: 'sg', pl: 'pl', du: 'du' }
const ADJ_NOM = ['adj.m.sg', 'adj.f.sg', 'adj.n.sg', 'adj.m.du', 'adj.f.du', 'adj.m.pl', 'adj.f.pl', 'adj.n.pl']
const ADJ_CASES = ['gen', 'dat', 'acc', 'loc', 'ins']

/** The page an adjective's extra slot counts on: its agreement's, with the case's (and the number's) introduced too. */
export function adjectivePage(key: string): SlotPage | null {
  const p = key.split('.')
  if (p[0] !== 'adj') return null
  if (p.length === 3) {
    if (p[2] === 'du') return { page: 'dvojina', needs: ['pridevniki-ujemanje'] }
    return { page: 'pridevniki-ujemanje', needs: p[2] === 'pl' ? ['mnozina'] : [] }
  }
  const casePage = CASE_PAGES[p[1]]
  if (!casePage || p.length !== 4) return null
  return { page: 'pridevniki-ujemanje', needs: [casePage, ...(p[3] === 'pl' ? ['mnozina'] : [])] }
}

/** The future of a verb that has none of its own, by person: biti's future and the l-form of the number, both the dictionary's. */
function composedFuture(e: LemmaForms, k: CardContext): CardSlot[] {
  const of = (key: string) => e.slots.find(s => s.key === key)?.forms ?? []
  const out: CardSlot[] = []
  for (const p of PERSONS) {
    const aux = k.bitiFuture[`fut.${p}`]
    const n = NUMBER_OF[p.slice(1)]
    const m = of(`past.m.${n}`)
    if (!aux?.forms.length || !m.length) continue
    const f = of(`past.f.${n}`)
    const phrases = (auxes: string[], ls: string[]) => auxes.flatMap(a => ls.map(l => `${a} ${l}`))
    // the first and second person may be either gender (jaz, ti, midva, vi); the third is the pronoun's (on, onadva, oni)
    const either = p[0] !== '3'
    let forms = phrases(aux.forms, m)
    let also = [...phrases(aux.also, m), ...(either ? phrases([...aux.forms, ...aux.also], f) : [])]
    if (either && p[0] === '1' && k.female && f.length) {
      const fem = phrases(aux.forms, f)
      also = [...forms, ...also.filter(x => !fem.includes(x))]
      forms = fem
    }
    const pg = pageOf(normalizeForm(e.lemma, k.language ?? 'sl'), `fut.${p}`)
    if (!pg || !k.pages.has(pg.page) || pg.needs.some(x => !k.pages.has(x))) continue
    also = [...new Set(also)].filter(x => !forms.includes(x))
    out.push({ key: `fut.${p}`, forms: [...new Set(forms)].slice(0, 4), ...(also.length ? { also: also.slice(0, 6) } : {}), page: pg.page, ...(pg.needs.length ? { needs: pg.needs } : {}), composed: true })
  }
  return out
}

const cardSlot = (s: { key: string; forms: string[]; also?: string[]; page: string; needs?: string[] }): CardSlot => ({
  key: s.key, forms: s.forms, ...(s.also?.length ? { also: s.also } : {}), page: s.page, ...(s.needs?.length ? { needs: s.needs } : {}),
})

/**
 * The forms card of a word's dictionary entry ([e], from formsOf), or why there is none: a noun's slots, a verb's (its
 * future joined from biti's and its l-form when it has none of its own), an adjective's nominative and its cases
 * ([k].adjective); the comparative left out. A card needs [MIN_SLOTS] slots besides the lemma's own and [MIN_DISTINCT]
 * different forms (something to choose among). [tested] is left empty: [testedOf] fills it.
 */
export function formsCardOf(e: LemmaForms, k: CardContext): FormsCardData | NoCard {
  if (e.pos !== 'noun' && e.pos !== 'verb' && e.pos !== 'adj') return { why: `not a noun, verb or adjective (${e.pos})` }
  const language = k.language ?? 'sl'
  const norm = (s: string) => normalizeForm(s, language)
  const inBook = (pg: SlotPage | null) => !!pg && k.pages.has(pg.page) && pg.needs.every(n => k.pages.has(n))
  let slots: CardSlot[] = []
  if (e.pos === 'noun') slots = e.slots.map(cardSlot)
  else if (e.pos === 'verb') {
    const own = e.slots.some(s => s.key.startsWith('fut.'))
    const fut = own ? [] : composedFuture(e, k)
    for (const s of e.slots) {
      if (s.key.startsWith('past.') && fut.length && !slots.some(x => x.key.startsWith('fut.'))) slots.push(...fut)
      slots.push(cardSlot(s))
    }
    if (fut.length && !slots.some(x => x.key.startsWith('fut.'))) slots.push(...fut)
  } else {
    const extra = k.adjective?.(e.lemma) ?? new Map<string, string[]>()
    const base = new Map(e.slots.filter(s => s.key !== 'cmp.m.sg').map(s => [s.key, cardSlot(s)]))
    const add = (key: string) => {
      const had = base.get(key)
      if (had) return slots.push(had)
      const forms = extra.get(key)
      const pg = adjectivePage(key)
      if (!forms?.length || !pg || !inBook(pg)) return
      slots.push({ key, forms, page: pg.page, ...(pg.needs.length ? { needs: pg.needs } : {}) })
    }
    for (const key of ADJ_NOM) add(key)
    for (const n of ['sg', 'pl']) for (const c of ADJ_CASES) for (const g of ['m', 'f', 'n']) add(`adj.${c}.${g}.${n}`)
  }
  slots = slots.filter(s => s.forms.length)
  const others = slots.filter(s => !OWN_KEYS.has(s.key))
  if (others.length < MIN_SLOTS) return { why: `too few forms in the dictionary (${others.length} besides the word itself)` }
  const distinct = new Set(slots.flatMap(s => s.forms.map(norm)))
  if (distinct.size < MIN_DISTINCT) return { why: 'too few different forms in the dictionary to choose among' }
  return {
    v: 1,
    lemma: norm(e.lemma),
    pos: e.pos,
    ...(e.pos === 'noun' && e.gender ? { gender: e.gender } : {}),
    gloss: e.gloss,
    table: e.table,
    slots,
    tested: [],
  }
}

/** The keys of [slots] tested: each one's page and the pages it needs are introduced; never the lemma's own. */
export function testedOf(slots: CardSlot[], introduced: Set<string>): string[] {
  return slots.filter(s => !OWN_KEYS.has(s.key) && introduced.has(s.page) && (s.needs ?? []).every(n => introduced.has(n))).map(s => s.key)
}

/** How useful a word's card is first: the core verbs by their place, then by how often the content says its forms ([uses]). */
export function usefulness(lemma: string, uses: number): number {
  const i = CORE.indexOf(lemma)
  return i >= 0 ? 1e9 - i : uses
}

/** How often the content says a card's forms: [uses] of each form, summed. */
export function usesOf(card: Pick<FormsCardData, 'slots'>, uses: (form: string) => number): number {
  const forms = new Set(card.slots.flatMap(s => s.forms.filter(f => !f.includes(' '))))
  let n = 0
  for (const f of forms) n += uses(f)
  return n
}

/** A forms card's review item id: forms_hisa; forms_lep_adj for a second part of speech; forms_hisa-2 on a clash. */
export function formsItemId(lemma: string, pos: string, taken: (id: string) => boolean, posTaken: boolean): string {
  const base = posTaken ? `forms_${wordSlug(lemma)}_${pos}` : `forms_${wordSlug(lemma)}`
  let id = base
  for (let n = 2; taken(id); n++) id = `${base}-${n}`
  return id
}

/**
 * The update-db.py `new_vocabulary` entry of a card: type forms, the word on its front and a few of its forms on its back
 * (what card.py and the tutor read), its table in `forms`; due [due] (the day it is made: it is of a word already learned).
 */
export function formsCardVocabulary(id: string, card: FormsCardData, due: string) {
  const answer = card.slots.filter(s => !OWN_KEYS.has(s.key)).slice(0, 6).map(s => s.forms[0]).join(' · ')
  return {
    item_id: id,
    item_type: FORMS_TYPE,
    content: card.lemma,
    answer,
    category: FORMS_TYPE,
    difficulty: 'A1',
    initial_quality: 3,
    priority: 'medium',
    source: FORMS_TYPE,
    due_date: due,
    forms: card,
  }
}

/** An item's forms card data, when it reads as one (a forms item with a lemma and slots). */
export function cardOfItem(item: Record<string, unknown>): FormsCardData | null {
  if (item.type !== FORMS_TYPE) return null
  const f = item.forms as FormsCardData | undefined
  if (!f || typeof f !== 'object' || typeof f.lemma !== 'string' || !Array.isArray(f.slots)) return null
  if (!f.slots.every(s => s && typeof s.key === 'string' && Array.isArray(s.forms) && typeof s.page === 'string')) return null
  return f
}

// --- resurfacing: a part newly introduced brings the card back soon ------------------------------------------------

export type Resurfaced = { item_id: string; lemma: string; due_date: string; planned: string; fresh: string[]; forms: FormsCardData }

const plusDays = (day: string, n: number) => {
  const d = new Date(`${day}T12:00:00Z`)
  d.setUTCDate(d.getUTCDate() + n)
  return d.toISOString().slice(0, 10)
}

/**
 * The forms cards [items] that come back soon because [introduced] tests slots their table didn't test before (newly =
 * tested now less forms.tested): the most useful first ([rank] of the lemma), each on the first day from [today] with
 * room ([perDay] less the cards a resurfacing planned for that day before), due then, or earlier if it is due earlier
 * already. Each one's data: tested now, fresh the new keys (with those not played twice yet, while its repetitions are
 * below 2), unlocked today, resurface the planned day. A card whose tested slots shrank or stayed is left out.
 */
export function resurfacePlan(items: Record<string, Record<string, unknown>>, introduced: Set<string>, today: string, perDay: number, rank: (card: FormsCardData) => number): Resurfaced[] {
  type Cand = { id: string; item: Record<string, unknown>; card: FormsCardData; now: string[]; newly: string[]; rank: number }
  const cands: Cand[] = []
  const load = new Map<string, number>()
  for (const [id, item] of Object.entries(items)) {
    const card = cardOfItem(item)
    if (!card) continue
    const now = testedOf(card.slots, introduced)
    const stored = new Set(Array.isArray(card.tested) ? card.tested : [])
    const newly = now.filter(k => !stored.has(k))
    if (newly.length) cands.push({ id, item, card, now, newly, rank: rank(card) })
    else if (typeof card.resurface === 'string' && card.resurface >= today) load.set(card.resurface, (load.get(card.resurface) ?? 0) + 1)
  }
  cands.sort((a, b) => b.rank - a.rank || a.id.localeCompare(b.id))
  const out: Resurfaced[] = []
  const cap = Math.max(1, perDay)
  for (const c of cands) {
    let day = today
    for (let i = 0; (load.get(day) ?? 0) >= cap && i < 366; i++) day = plusDays(day, 1)
    load.set(day, (load.get(day) ?? 0) + 1)
    const current = typeof c.item.due_date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(c.item.due_date) ? c.item.due_date : null
    const due = current && current < day ? current : day
    const reps = typeof c.item.repetitions === 'number' ? c.item.repetitions : 0
    const pending = reps < 2 && Array.isArray(c.card.fresh) ? c.card.fresh.filter(k => c.now.includes(k)) : []
    const fresh = c.card.slots.map(s => s.key).filter(k => c.newly.includes(k) || pending.includes(k))
    out.push({
      item_id: c.id, lemma: c.card.lemma, due_date: due, planned: day, fresh,
      forms: { ...c.card, tested: c.now, fresh, unlocked: today, resurface: day },
    })
  }
  return out
}

// --- how a card went: POST /reviews `forms_cards` ------------------------------------------------------------------

/** What a wrong cell was wrong about (the app's kind): the pattern id is "forms_" + the kind. */
export const FORMS_KINDS = [
  'case_nominative', 'case_genitive', 'case_dative', 'case_accusative', 'case_locative', 'case_instrumental',
  'number_plural', 'number_dual',
  'verb_person_1sg', 'verb_person_2sg', 'verb_person_3sg', 'verb_person_1pl', 'verb_person_2pl', 'verb_person_3pl',
  'tense_past', 'tense_future', 'tense_imperative', 'tense_negative',
  'adjective_gender',
] as const
export type FormsKind = (typeof FORMS_KINDS)[number]

/**
 * The mistake pattern of each kind (mistakes-db.json `error_patterns`): its id's words match the grammar book's pages'
 * tags (grammar.ts tagMatches: "accusative" the accusative's, "dual" the dual's, "verb_person" the present's, "past",
 * "future", "imperative", "adjective"). A kind is only written for a slot that was tested, whose page was introduced: an
 * A2 page's word (genitive, dative, instrumental) reaches an A1 learner's patterns only once that page was.
 */
export const FORMS_PATTERNS: Record<FormsKind, { id: string; description: string }> = {
  case_nominative: { id: 'forms_case_nominative', description: 'Forms: the nominative (imenovalnik, 1st case: kdo? kaj?)' },
  case_genitive: { id: 'forms_case_genitive', description: 'Forms: the genitive (rodilnik, 2nd case: koga? česa?)' },
  case_dative: { id: 'forms_case_dative', description: 'Forms: the dative (dajalnik, 3rd case: komu? čemu?)' },
  case_accusative: { id: 'forms_case_accusative', description: 'Forms: the accusative (tožilnik, 4th case: koga? kaj?)' },
  case_locative: { id: 'forms_case_locative', description: 'Forms: the locative (mestnik, 5th case: pri kom? pri čem?)' },
  case_instrumental: { id: 'forms_case_instrumental', description: 'Forms: the instrumental (orodnik, 6th case: s kom? s čim?)' },
  number_plural: { id: 'forms_number_plural', description: 'Forms: the plural (množina)' },
  number_dual: { id: 'forms_number_dual', description: 'Forms: the dual (dvojina): exactly two' },
  verb_person_1sg: { id: 'forms_verb_person_1sg', description: "Forms: the present's ending for jaz (1st person singular)" },
  verb_person_2sg: { id: 'forms_verb_person_2sg', description: "Forms: the present's ending for ti (2nd person singular)" },
  verb_person_3sg: { id: 'forms_verb_person_3sg', description: "Forms: the present's ending for on, ona (3rd person singular)" },
  verb_person_1pl: { id: 'forms_verb_person_1pl', description: "Forms: the present's ending for mi (1st person plural)" },
  verb_person_2pl: { id: 'forms_verb_person_2pl', description: "Forms: the present's ending for vi (2nd person plural)" },
  verb_person_3pl: { id: 'forms_verb_person_3pl', description: "Forms: the present's ending for oni (3rd person plural)" },
  tense_past: { id: 'forms_tense_past', description: "Forms: the past's l-form (preteklik: sem delal, je delala)" },
  tense_future: { id: 'forms_tense_future', description: 'Forms: the future (prihodnjik: bom delal, bodo delali)' },
  tense_imperative: { id: 'forms_tense_imperative', description: 'Forms: the imperative (velelnik: delaj, delajte)' },
  tense_negative: { id: 'forms_tense_negative', description: 'Forms: the negative present (nisem, nimam)' },
  adjective_gender: { id: 'forms_adjective_gender', description: "Forms: the adjective agrees with its noun's gender (dober, dobra, dobro)" },
}

const KINDS = new Set<string>(FORMS_KINDS)
const text = z.string().trim().max(80)
const cellIn = z.object({
  key: z.string().trim().min(1).max(24),
  right: z.boolean(),
  kind: z.string().trim().max(40).optional(),
  answer: text.optional(),
  expected: text.optional(),
})
const runIn = z.object({
  item_id: z.string().trim().min(1).max(128),
  lemma: z.string().trim().min(1).max(80),
  typed: z.boolean().optional(),
  fresh: z.boolean().optional(),
  cells: z.array(z.unknown()).min(1).max(60),
})
export type FormsCell = { key: string; right: boolean; kind?: FormsKind; answer?: string; expected?: string }
export type FormsRun = { item_id: string; lemma: string; typed?: boolean; fresh?: boolean; cells: FormsCell[] }

/**
 * POST /reviews' `forms_cards` as the app sent it, read leniently: a card that doesn't read is left out, a cell that
 * doesn't read too (a card without a cell left), a kind this bridge doesn't know dropped; never the review.
 */
export function formsRuns(raw: unknown): FormsRun[] {
  if (!Array.isArray(raw)) return []
  return raw.slice(0, 40).flatMap(r => {
    const p = runIn.safeParse(r)
    if (!p.success) return []
    const cells = p.data.cells.flatMap(c => {
      const x = cellIn.safeParse(c)
      if (!x.success) return []
      const { kind, ...rest } = x.data
      return [{ ...rest, ...(kind && KINDS.has(kind) ? { kind: kind as FormsKind } : {}) }]
    })
    return cells.length ? [{ ...p.data, cells }] : []
  })
}

/** The update-db.py `errors` of the wrong cells: each a mistake of its kind, with what was said and what was right. */
export function formsErrors(runs: FormsRun[]) {
  return runs.flatMap(r =>
    r.cells.filter(c => !c.right && c.kind).map(c => ({
      pattern_id: FORMS_PATTERNS[c.kind!].id,
      category: 'grammar',
      subcategory: 'forms',
      description: FORMS_PATTERNS[c.kind!].description,
      severity: 'moderate',
      your_answer: c.answer ?? '',
      correct_answer: c.expected ?? '',
      context: `forms card ${r.item_id} (${r.lemma}), ${c.key}`,
    })),
  )
}

/**
 * " Forms cards: 2 (hiša 5/6; delati 6/6, typed). Their mistakes are ALREADY in the mistakes database: hiša loc.pl
 * «hiše» for «hišah» (forms_case_locative)." for the tutor; empty without forms cards.
 */
export function formsCardsNote(runs: FormsRun[]): string {
  if (!runs.length) return ''
  const cards = runs.map(r => `${r.lemma} ${r.cells.filter(c => c.right).length}/${r.cells.length}${r.typed ? ', typed' : ''}${r.fresh ? ', its new part' : ''}`)
  const wrong = runs.flatMap(r =>
    r.cells.filter(c => !c.right).map(c => `${r.lemma} ${c.key} «${c.answer || '…'}» for «${c.expected ?? '?'}»${c.kind ? ` (${FORMS_PATTERNS[c.kind].id})` : ''}`),
  )
  const kept = wrong.length ? ` Their mistakes are ALREADY in the mistakes database: ${wrong.slice(0, 8).join('; ')}${wrong.length > 8 ? `; ${wrong.length - 8} more` : ''}.` : ''
  return ` Forms cards: ${runs.length} (${cards.join('; ')}).${kept}`
}
