// Forms cards (forms-cards.ts; companion/README.md "Forms cards"): the app asks for the cards of words the learner has
// learned, a few new a day, and for one word's card at once from its word card ("📋 Vse oblike · All forms"); either
// way it says which grammar pages the learner has been introduced to (`introduced`). The bridge makes each card from the
// dictionary's forms as a review item of its own (update-db.py, no session) and brings back soon the cards whose table
// tests more than before, a few a day (update-db.py `resurface`). How a card went comes with the review (POST /reviews
// `forms_cards`, features/reviews.ts): its wrong cells become mistakes by kind.
import { z } from 'zod'
import { renderDeep } from '../addressee'
import { learnedItem } from '../counting'
import type { FeatureFactory } from '../feature'
import { adjectiveForms, contentLines, formsOf, LineIndex, type LemmaForms } from '../forms'
import {
  cardOfItem, formsCardOf, formsCardVocabulary, formsItemId, isCard, MIN_TESTED, MIN_TESTED_ASKED, resurfacePlan,
  testedOf, usefulness, usesOf, type CardContext, type FormsCardData,
} from '../forms-cards'
import { json, readJson } from '../http'
import { normalizeForm } from '../lexicon'
import { itemId, normWord, wordOf } from '../packs'

const instructions = `
Forms cards ("📋 Oblike · Forms"): one of Jan's words with its whole table in a review card (type "forms", id
forms_<word>, its table in the item's forms: the dictionary's forms, never made by a rule; a verb's future is biti's
future and its l-form, "bom delal"): a noun's six cases by number (ednina, dvojina, množina), a verb's present by
person with the dual, the negative (nisem, nimam), the future, the past (on je delal, ona je delala …) and the
imperative, an adjective's genders, then its cases. The whole table is shown; only what Jan has been introduced to is
tested: a form whose grammar page (and the pages it needs: a dual case needs dvojina and the case's) was met in the
grammar book or taught by a unit whose intro is done; the rest is greyed, a preview to read, until its page is
introduced. A long table is played a part at a time (a row or two, at most 6 gaps), the parts in turn; each gap chosen
among three of the word's forms until the card is secure (two good reviews in a row), typed after. Cards come two new
a day of Jan's learned words (the core verbs biti, imeti, iti, delati first, then the words the content says most),
or at once when Jan taps "📋 Vse oblike" on a word's card. When a unit or a page introduces new forms of a card's
table, the card comes back within days (three a day at most), its new part first, scheduled as new learning (its
repetitions back to 0, its history kept). review_done names each forms card and its wrong cells, which are ALREADY in
the mistakes database by kind: forms_case_<nominative|genitive|dative|accusative|locative|instrumental>,
forms_number_plural, forms_number_dual, forms_verb_person_<1sg…3pl> (the present's ending), forms_tense_past,
forms_tense_future, forms_tense_imperative, forms_tense_negative, forms_adjective_gender; don't persist them again. A
kind that keeps coming back: point Jan to the unit or the grammar page that teaches it (get_course lists the units by
page: tozilnik, mestnik, mnozina, dvojina, sedanjik, pretekli-cas, prihodnjik, velelnik, spol for the adjective's
gender), its intro again or a worksheet; a module with lani-studio if it persists. A form on a card that looks wrong
is the dictionary's: the lani-fix skill (card.py show), fixed in the dictionary, then the card deleted so a new one is
made.
`.trim()

const num = (v: string | undefined, d: number) => (n => (Number.isFinite(n) ? Math.max(0, Math.floor(n)) : d))(Number(v ?? d))
/** How many forms cards are made a day at most by themselves (LANI_FORMS_PER_DAY; 2 when it isn't a number). */
const PER_DAY = num(process.env.LANI_FORMS_PER_DAY, 2)
/** How many forms cards come back a day at most when new forms of theirs are introduced (LANI_FORMS_RESURFACE_PER_DAY, 3). */
const RESURFACE_PER_DAY = Math.max(1, num(process.env.LANI_FORMS_RESURFACE_PER_DAY, 3))
/** How long the content's lines (the ranking's uses) are kept. */
const LINES_TTL_MS = 10 * 60_000

const formsCardsIn = z.object({
  client_id: z.string().optional(),
  // none: only the resurfacing (a unit or page was introduced, nothing new to ask for)
  words: z.array(z.object({ word: z.string().trim().min(1).max(40), item_id: z.string().trim().max(128).optional() })).max(40),
  introduced: z.array(z.string().trim().min(1).max(60)).max(200).default([]),
  asked: z.boolean().optional(),
})

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

  async function handle(req: Request): Promise<Response> {
    const r = formsCardsIn.safeParse(await readJson(req))
    if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
    // the grammar book's pages and the future's auxiliary are Slovene's
    if (language !== 'sl' || dictionary.language !== 'sl') return json({ error: `forms cards are Slovene's; this village speaks "${language}"` }, 400)
    const today = new Date().toLocaleDateString('sv') // YYYY-MM-DD, local, as read-db.py's today
    const items = learner.srItems() as Record<string, Item>
    const introduced = new Set(r.data.introduced)
    const asked = r.data.asked === true
    const norm = (s: string) => normalizeForm(s, language)
    const { index, pages } = content()
    const uses = (f: string) => index.with(f).length
    const k: CardContext = { bitiFuture: bitiFuture(), adjective: l => adjectiveForms({ dict, book, pages, lines: index, language }, l), pages, female: addressee().gender === 'female', language }
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
      const key = norm(it.content)
      if (!words.has(key) || learnedItem(it)) words.set(key, { id, item: it })
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
      const entries = formsOf({ dict, book, pages, lines: index, language }, w.word).filter(e => e.pos === 'noun' || e.pos === 'verb' || e.pos === 'adj')
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
      const has = cards.find(c => norm(c.card.lemma) === card.lemma && c.card.pos === card.pos)
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
      const posTaken = cards.some(c => norm(c.card.lemma) === m.card.lemma) || vocabulary.some(v => v.forms.lemma === m.card.lemma)
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

  return {
    instructions,
    routes: [{ method: 'POST', path: '/forms-cards', handle: ({ req, url }) => outbox.once(req, url.pathname, handle) }],
  }
}

