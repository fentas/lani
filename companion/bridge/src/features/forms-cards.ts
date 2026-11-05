// Forms cards (forms-cards.ts; companion/README.md "Forms cards"): the app asks for the cards of words the learner has
// learned, a few new a day, and for one word's card at once from its word card ("📋 Vse oblike · All forms"); either
// way it says which grammar pages the learner has been introduced to (`introduced`). The bridge makes each card from the
// dictionary's forms as a review item of its own (update-db.py, no session) and brings back soon the cards whose table
// tests more than before, a few a day (update-db.py `resurface`). How a card went comes with the review (POST /reviews
// `forms_cards`, features/reviews.ts): its wrong cells become mistakes by kind. GET /forms-cards gives a word's tables
// as its card would have them, to read (the app's overview), and writes nothing.
import { z } from 'zod'
import { renderDeep } from '../addressee'
import { learnedItem } from '../counting'
import type { FeatureFactory } from '../feature'
import { adjectiveForms, contentLines, formsAttribution, formsOf, LineIndex, type LemmaForms } from '../forms'
import { bareWord, isParadigmLanguage } from '../paradigms'
import {
  cardOfItem, formsCardOf, formsCardVocabulary, formsItemId, isCard, MIN_TESTED, MIN_TESTED_ASKED, resurfacePlan,
  testedOf, usefulness, usesOf, type CardContext, type FormsCardData,
} from '../forms-cards'
import { json, readJson } from '../http'
import { normalizeForm } from '../lexicon'
import { LANGUAGES } from '../learners'
import { itemId, normWord, wordOf } from '../packs'

const instructions = `
Forms cards ("📋 Oblike · Forms"): one of Jan's words with its whole table in a review card (type "forms", id
forms_<word>, its table in the item's forms: the dictionary's forms, never made by a rule; a verb's future is biti's
future and its l-form, "bom delal"): a noun's six cases by number (ednina, dvojina, množina), a verb's present by
person with the dual, the negative (nisem, nimam), the future, the past (on je delal, ona je delala …) and the
imperative, an adjective's genders, then its cases. Only what Jan has been introduced to is tested: a form whose
grammar page (and the pages it needs: a dual case needs dvojina and the case's) was met in the grammar book or taught
by a unit whose intro is done. A long table is played a part at a time (a row or two, at most 6 gaps), the parts in
turn; the card shows only the sections with a gap asked now (biti's present, not its negative, future, past and
imperative), the others hidden, and in a shown section a form not introduced yet is greyed, to read ahead. Each gap
chosen among three of the word's forms until the card is secure (two good reviews in a row), typed after. The whole
table is an overview to read, never a test: "📋 Vse oblike · All forms" on a word's card (and after a forms card)
shows every section, the forms Jan practises marked. Cards come two new a day of Jan's learned words (the core verbs
biti, imeti, iti, delati first, then the words the content says most), or at once from the overview's "🃏 Vadi oblike
· Practise the forms". When a unit or a page introduces new forms of a card's
table, the card comes back within days (three a day at most), its new part first, scheduled as new learning (its
repetitions back to 0, its history kept). review_done names each forms card and its wrong cells, which are ALREADY in
the mistakes database by kind: forms_case_<nominative|genitive|dative|accusative|locative|instrumental>,
forms_number_plural, forms_number_dual, forms_verb_person_<1sg…3pl> (the present's ending), forms_tense_past,
forms_tense_future, forms_tense_imperative, forms_tense_negative, forms_adjective_gender; don't persist them again. A
kind that keeps coming back: point Jan to the unit or the grammar page that teaches it (get_course lists the units by
page: tozilnik, mestnik, mnozina, dvojina, sedanjik, pretekli-cas, prihodnjik, velelnik, spol for the adjective's
gender), its intro again or a worksheet; a module with lani-studio if it persists. A form on a card that looks wrong
is the dictionary's: the lani-fix skill (card.py show), fixed in the dictionary. A card shows a slot's forms in today's
order by itself (GET /state: the deck isn't written), so it's deleted, to be made again, only for a form it lacks.
`.trim()

const num = (v: string | undefined, d: number) => (n => (Number.isFinite(n) ? Math.max(0, Math.floor(n)) : d))(Number(v ?? d))
/** How many forms cards are made a day at most by themselves (LANI_FORMS_PER_DAY; 2 when it isn't a number). */
const PER_DAY = num(process.env.LANI_FORMS_PER_DAY, 2)
/** How many forms cards come back a day at most when new forms of theirs are introduced (LANI_FORMS_RESURFACE_PER_DAY, 3). */
const RESURFACE_PER_DAY = Math.max(1, num(process.env.LANI_FORMS_RESURFACE_PER_DAY, 3))
/** How long the content's lines (the ranking's uses) are kept. */
const LINES_TTL_MS = 10 * 60_000
/** The words one GET /forms-cards asks about at most. */
const TABLE_WORDS = 10

const formsCardsIn = z.object({
  client_id: z.string().optional(),
  // none: only the resurfacing (a unit or page was introduced, nothing new to ask for)
  words: z.array(z.object({ word: z.string().trim().min(1).max(40), item_id: z.string().trim().max(128).optional() })).max(40),
  introduced: z.array(z.string().trim().min(1).max(60)).max(200).default([]),
  asked: z.boolean().optional(),
  /** The tables the app reads: 2 has Italian's, German's and English's too. */
  v: z.number().int().optional(),
})

/** What each language's forms card holds (paradigms.ts' slots), for the tutor. */
const OTHER_TABLES: Record<string, string> = {
  it: "a verb's present, passato prossimo (the participle after avere or essere), imperfetto, futuro, condizionale, congiuntivo (and its imperfect), passato remoto, imperativo and gerundio; an adjective's genders and numbers, its comparative and superlative",
  de: "a verb's Präsens, Präteritum, Perfekt (the Partizip II after haben or sein), Konjunktiv II (sein, haben, werden, the modals, wissen) and Imperativ; a noun's four cases by number with the article (dem Haus, den Häusern)",
  en: "a verb's -s, past, past participle and -ing (be's am, are, is, was, were)",
}

/** The tutor's paragraph of forms cards in an Italian, German or English village. */
const otherInstructions = (language: string) => `
Forms cards in ${LANGUAGES[language] ?? language} ("📋" in the review): one of the learner's words with its table in a review card
(type "forms", id forms_<word>, its table in the item's forms, forms.language "${language}"): Wiktionary's forms of the
content's words (companion/lexicon/${language}.paradigms.json), never made by a rule: ${OTHER_TABLES[language] ?? 'its forms'}.
Only what the learner has been introduced to is tested (a form whose grammar page was met in the grammar book or taught
by a unit whose intro is done); a form the same as the word's own is shown, never asked (German's dem Haus, English's
cut). A part at a time, each gap chosen until the card is secure, typed after; "📋" on a word's card shows the whole
table, to read. A review's wrong cell is ALREADY in the mistakes database as forms_<kind> (forms_tense_imperfect,
forms_tense_subjunctive, forms_tense_participle, forms_verb_person_1sg, forms_case_dative, forms_number_plural …):
suggest its grammar page or a drill, never record it again. An app from before November 2026 has none.`

type Item = Record<string, unknown> & { type?: string; content?: string; answer?: string; created_date?: string }

export const formsCards: FeatureFactory = ({ learner, dictionary, grammar, packs, scenes, stories, readings, addressee, events, culture, outbox, log }) => {
  const language = culture.manifest?.language ?? 'sl'
  const { dict, book } = dictionary

  let lines: { at: number; index: LineIndex; pages: Set<string> } | null = null
  function content() {
    if (lines && Date.now() - lines.at < LINES_TTL_MS) return lines
    const pages = grammar.all()
    const all = contentLines(renderDeep({ language, scenes: scenes.all(), stories: stories.all(), readings: readings.all(), grammar: pages, packs: packs.all() }, addressee()))
    lines = { at: Date.now(), index: new LineIndex(all), pages: new Set(pages.map(p => p.id)) }
    return lines
  }

  /** The picture of [lemma] in the learner's word packs: its card's pack word's, else any pack word of it. */
  function emojiOf(lemma: string, item: string | undefined): string | undefined {
    const all = packs.all()
    const own = item ? all.flatMap(p => p.words.filter(w => itemId(p.id, w.id) === item)).find(w => w.emoji) : undefined
    const any = all.flatMap(p => p.words.filter(w => normWord(wordOf(p, w)) === normWord(lemma))).find(w => w.emoji)
    return (own ?? any)?.emoji?.trim() || undefined
  }

  /** biti's future from the forms book (bom, boš … bodo, also bojo): what a verb's future is joined from. */
  function bitiFuture(): CardContext['bitiFuture'] {
    const t = book.table('biti', 'verb')
    const out: CardContext['bitiFuture'] = {}
    if (!t) return out
    const norm = (s: string) => normalizeForm(s, language)
    for (const [k, v] of Object.entries(t.forms)) {
      if (!k.startsWith('fut.') || !v) continue
      out[k] = { forms: (typeof v === 'string' ? [v] : v).map(norm), also: (t.also?.[k] ?? []).map(norm) }
    }
    return out
  }

  /** What a card is made with: biti's future, the adjective's cases, the book's pages, the learner's gender. */
  function cardContext(index: LineIndex, pages: Set<string>): CardContext {
    if (language !== 'sl') return { bitiFuture: {}, pages, female: addressee().gender === 'female', language }
    return { bitiFuture: bitiFuture(), adjective: l => adjectiveForms({ dict, book, pages, lines: index, language }, l), pages, female: addressee().gender === 'female', language }
  }
  /** A word's dictionary entries as GET /forms has them: Slovene's dictionary's, the others' paradigms. */
  const entriesOf = (index: LineIndex, pages: Set<string>, word: string) =>
    formsOf({ dict, book, pages, lines: index, language, paradigms: dictionary.paradigms }, word).filter(e => e.pos === 'noun' || e.pos === 'verb' || e.pos === 'adj')

  /**
   * Slovene's cards, and Italian's, German's and English's (paradigms.ts) to an app that reads their tables ([v] 2): an
   * error for another village, or an older app there.
   */
  const notSlovene = (v?: unknown) =>
    (language === 'sl' && dictionary.language === 'sl') || (isParadigmLanguage(language) && Number(v) === 2)
      ? null
      : json({ error: `forms cards are Slovene's${isParadigmLanguage(language) ? ', and this village\'s to an app that asks with v=2' : ''}; this village speaks "${language}"` }, 400)

  async function handle(req: Request): Promise<Response> {
    const r = formsCardsIn.safeParse(await readJson(req))
    if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
    const other = notSlovene(r.data.v)
    if (other) return other
    const today = new Date().toLocaleDateString('sv') // YYYY-MM-DD, local, as read-db.py's today
    const items = learner.srItems() as Record<string, Item>
    const introduced = new Set(r.data.introduced)
    const asked = r.data.asked === true
    const norm = (s: string) => normalizeForm(s, language)
    const { index, pages } = content()
    const uses = (f: string) => index.with(f).length
    const k = cardContext(index, pages)
    const rank = (card: Pick<FormsCardData, 'lemma' | 'slots'>) => usefulness(card.lemma, usesOf(card, uses))

    const cards = Object.entries(items).flatMap(([id, it]) => {
      const c = cardOfItem(it)
      return c ? [{ id, card: c, item: it }] : []
    })
    const made = cards.filter(c => c.item.created_date === today && !c.card.asked).length
    const room = Math.max(0, PER_DAY - made)
    // the learner's words by what they say
    const words = new Map<string, { id: string; item: Item }>()
    for (const [id, it] of Object.entries(items)) {
      if ((it.type ?? 'vocabulary') !== 'vocabulary' || typeof it.content !== 'string') continue
      // a card's word without its article or its "to" too (la casa, der Wald, to run: the app names casa, Wald, run)
      for (const key of new Set([norm(it.content), norm(bareWord(it.content, language))])) if (!words.has(key) || learnedItem(it)) words.set(key, { id, item: it })
    }

    type Made = { card: FormsCardData; word: string; item_id?: string; tested: number }
    const added: { item_id: string; lemma: string; pos: string; tested: number; existing?: true }[] = []
    const skipped: { word: string; why: string }[] = []
    const ready: Made[] = []
    const seen = new Set<string>()
    for (const w of asked ? r.data.words.slice(0, 1) : r.data.words) {
      const key = norm(w.word)
      if (seen.has(key)) continue
      seen.add(key)
      const own = words.get(key)
      if (!asked && (!own || !learnedItem(own.item))) {
        skipped.push({ word: w.word, why: 'not a word the learner has learned' })
        continue
      }
      if (!asked && room <= 0) {
        skipped.push({ word: w.word, why: `today's ${PER_DAY} new forms cards are made` })
        continue
      }
      const entries = entriesOf(index, pages, w.word)
      if (!entries.length) {
        skipped.push({ word: w.word, why: 'no noun, verb or adjective of that name in the dictionary' })
        continue
      }
      // the entry the learner's card means (a word of its gloss in the card's meaning), else the first that makes a card
      const meant = new Set(String(own?.item.answer ?? '').toLowerCase().split(/[^\p{L}]+/u).filter(x => x.length > 2))
      const fits = (e: LemmaForms) => (e.gloss.some(x => x.toLowerCase().split(/[^\p{L}]+/u).some(y => meant.has(y))) ? 1 : 0)
      const tries = [...entries].sort((a, b) => fits(b) - fits(a)).map(e => formsCardOf(e, k))
      const card = tries.find(isCard)
      if (!card) {
        skipped.push({ word: w.word, why: (tries[0] as { why: string }).why })
        continue
      }
      const has = cards.find(c => norm(c.card.lemma) === norm(card.lemma) && c.card.pos === card.pos)
      if (has) {
        if (asked) added.push({ item_id: has.id, lemma: has.card.lemma, pos: has.card.pos, tested: testedOf(has.card.slots, introduced).length, existing: true })
        else skipped.push({ word: w.word, why: 'it has a forms card' })
        continue
      }
      const tested = testedOf(card.slots, introduced)
      if (tested.length < (asked ? MIN_TESTED_ASKED : MIN_TESTED)) {
        skipped.push({ word: w.word, why: asked ? 'nothing of its table is introduced yet' : `fewer than ${MIN_TESTED} of its forms are introduced yet` })
        continue
      }
      const meaning = typeof own?.item.answer === 'string' ? own.item.answer.trim().slice(0, 120) : undefined
      const emoji = emojiOf(card.lemma, w.item_id ?? own?.id)
      ready.push({
        card: { ...card, ...(meaning ? { meaning } : {}), ...(emoji ? { emoji } : {}), tested, ...(asked ? { asked: true as const } : {}) },
        word: w.word, item_id: w.item_id, tested: tested.length,
      })
    }
    // by themselves: the most useful first, the day's few
    if (!asked) {
      const order = new Map(ready.map((m, i) => [m, i]))
      ready.sort((a, b) => rank(b.card) - rank(a.card) || order.get(a)! - order.get(b)!)
      for (const m of ready.splice(room)) skipped.push({ word: m.word, why: `today's ${PER_DAY} new forms cards are made` })
    }
    const vocabulary: ReturnType<typeof formsCardVocabulary>[] = []
    const taken = (id: string) => !!items[id] || vocabulary.some(v => v.item_id === id)
    for (const m of ready) {
      const posTaken = cards.some(c => norm(c.card.lemma) === norm(m.card.lemma)) || vocabulary.some(v => norm(v.forms.lemma) === norm(m.card.lemma))
      const id = formsItemId(m.card.lemma, m.card.pos, taken, posTaken)
      vocabulary.push(formsCardVocabulary(id, m.card, today))
      added.push({ item_id: id, lemma: m.card.lemma, pos: m.card.pos, tested: m.tested })
    }

    // the cards whose table tests more than before come back soon, a few a day
    const plan = resurfacePlan(items, introduced, today, RESURFACE_PER_DAY, rank)
    if (vocabulary.length || plan.length) {
      const u = learner.update({
        record_session: false, date: today,
        ...(vocabulary.length ? { new_vocabulary: vocabulary } : {}),
        ...(plan.length ? { resurface: plan.map(p => ({ item_id: p.item_id, due_date: p.planned, forms: p.forms })) } : {}),
      })
      if (!u.ok) return json({ error: u.output }, 500)
      if (vocabulary.length) events.emit({ type: 'words_added', item_ids: vocabulary.map(v => v.item_id) })
      if (vocabulary.length) log(`forms cards made: ${vocabulary.map(v => v.content).join(', ')}`)
      if (plan.length) log(`forms cards back soon: ${plan.map(p => `${p.lemma} ${p.due_date}`).join(', ')}`)
    }
    return json({
      added, skipped,
      resurfaced: plan.map(p => ({ item_id: p.item_id, lemma: p.lemma, due_date: p.due_date, fresh: p.fresh })),
      made_today: made + vocabulary.filter(v => !v.forms.asked).length,
      per_day: PER_DAY,
      resurface_per_day: RESURFACE_PER_DAY,
    })
  }

  /**
   * GET /forms-cards?w=delati&w=hiša: each word's tables as its forms card would have them (a noun's, a verb's with its
   * future joined, an adjective's with its cases), to read: the app's overview of a word's forms ("📋 Vse oblike · All
   * forms"). Nothing is written, no card made; `tested` is left empty (the app knows what is introduced).
   */
  function tables(url: URL): Response {
    const words = url.searchParams.getAll('w').map(w => w.trim())
    if (!words.length || words.length > TABLE_WORDS || words.some(w => !w || w.length > 40)) return json({ error: `w: 1-${TABLE_WORDS} words, each 1-40 characters` }, 400)
    const other = notSlovene(url.searchParams.get('v'))
    if (other) return other
    const { index, pages } = content()
    const k = cardContext(index, pages)
    const out = words.map(word => {
      const entries = entriesOf(index, pages, word)
      const tables = entries.map(e => formsCardOf(e, k)).filter(isCard).map(c => {
        const emoji = emojiOf(c.lemma, undefined)
        return { ...c, ...(emoji ? { emoji } : {}) }
      })
      return { word, tables }
    })
    return json({ language, words: out, attribution: formsAttribution(language, out.flatMap(w => w.tables)) })
  }

  return {
    instructions: language === 'sl' ? instructions : isParadigmLanguage(language) ? otherInstructions(language) : `Forms cards are Slovene's: this village's language has none, so no review names one and no mistake is forms_*.`,
    routes: [
      { method: 'POST', path: '/forms-cards', handle: ({ req, url }) => outbox.once(req, url.pathname, handle) },
      { method: 'GET', path: '/forms-cards', handle: ({ url }) => tables(url) },
    ],
  }
}

