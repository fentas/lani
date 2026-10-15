// Counting cards (counting.ts; companion/README.md "Counting cards"): the app asks for the cards of nouns the learner has
// learned once the intro of the unit stevila is done (POST /counting), and the bridge makes them from the dictionary's
// forms, a few new a day, as review items of their own (update-db.py, no session). How a card went comes with the
// review (POST /reviews `counting`, features/reviews.ts): its wrong steps become mistakes by kind.
import { z } from 'zod'
import { renderDeep } from '../addressee'
import { COUNTING_TYPE, countingItemId, countingOf, countingVocabulary, isCard, learnedItem, type CountingCard } from '../counting'
import type { FeatureFactory } from '../feature'
import { contentLines, formsOf, LineIndex } from '../forms'
import { json, readJson } from '../http'
import { normalizeForm } from '../lexicon'
import { itemId, normWord, wordOf } from '../packs'

const instructions = `
Counting cards ("🔢 Štetje · Counting"): once Jan has done the intro of the A1 unit stevila ("Ena ovca, dve ovci, pet
ovc"), their reviews hold a card per noun they have learned, a few new a day (type "counting", id counting_<noun>, its
forms in the item's counting: the dictionary's, never made by a rule). A card asks in four steps: the numeral for 1 by
the noun's gender (en / ena / eno, with why from the noun's end: kava ends in -a, feminine), then the whole phrase for 2
(the dual: dva brata, dve kavi), 3 (trije bratje, tri kave) and 5 (the genitive plural: pet bratov, pet kav); chosen
among three until the card is secure (two good reviews in a row), typed after. review_done names each card and its wrong
steps, which are ALREADY in the mistakes database by kind: counting_noun_gender (en/ena/eno, or dva/dve, trije/tri with
the noun right), counting_dual, counting_plural, counting_from_five (the genitive plural); don't persist them again. A
kind that keeps coming back: point Jan to the unit stevila (its intro again, its worksheets; get_course), the pages
stevila-samostalniki and spol-samostalnikov, or the unit spol for the gender; a module with lani-studio if it persists.
A form on a card that looks wrong is the dictionary's: the lani-fix skill (card.py show).
`.trim()

/** How many counting cards are made a day at most (LANI_COUNTING_PER_DAY; 3 when it isn't a number). */
const PER_DAY = (n => (Number.isFinite(n) ? Math.max(0, Math.floor(n)) : 3))(Number(process.env.LANI_COUNTING_PER_DAY ?? 3))
/** At most this many words one request names. */
const MAX_WORDS = 40
/** How long the content's lines (the shown form's tie-break) are kept. */
const LINES_TTL_MS = 10 * 60_000

const countingIn = z.object({
  client_id: z.string().optional(),
  words: z.array(z.object({ word: z.string().trim().min(1).max(40), item_id: z.string().trim().max(128).optional() })).min(1).max(MAX_WORDS),
})

type Item = Record<string, unknown> & { type?: string; content?: string; answer?: string; created_date?: string; counting?: { lemma?: string } }

export const counting: FeatureFactory = ({ learner, dictionary, grammar, packs, scenes, stories, readings, addressee, events, culture, outbox, log }) => {
  const language = culture.manifest?.language ?? 'sl'
  const { dict, book } = dictionary

  // the content's lines, by their words: how often it says a form (the shown form's tie-break: "pet ovc", not ovac)
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

  async function handleCounting(req: Request): Promise<Response> {
    const r = countingIn.safeParse(await readJson(req))
    if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
    // the numerals and their agreement are Slovene's
    if (language !== 'sl' || dictionary.language !== 'sl') return json({ error: `counting cards are Slovene's; this village speaks "${language}"` }, 400)
    const today = new Date().toLocaleDateString('sv') // YYYY-MM-DD, local, as read-db.py's today
    const items = learner.srItems() as Record<string, Item>
    const made = Object.values(items).filter(i => i.type === COUNTING_TYPE && i.created_date === today).length
    let room = Math.max(0, PER_DAY - made)
    const norm = (s: string) => normalizeForm(s, language)
    // the nouns that have a card already, and the learner's words by what they say
    const counted = new Set(Object.values(items).filter(i => i.type === COUNTING_TYPE).map(i => norm(String(i.counting?.lemma ?? i.content ?? ''))))
    const words = new Map<string, { id: string; item: Item }>()
    for (const [id, it] of Object.entries(items)) {
      if ((it.type ?? 'vocabulary') !== 'vocabulary' || typeof it.content !== 'string') continue
      const k = norm(it.content)
      if (!words.has(k) || learnedItem(it)) words.set(k, { id, item: it })
    }
    const added: { item_id: string; lemma: string; lines: string[] }[] = []
    const skipped: { word: string; why: string }[] = []
    const vocabulary: ReturnType<typeof countingVocabulary>[] = []
    const asked = new Set<string>()
    for (const w of r.data.words) {
      const key = norm(w.word)
      if (asked.has(key)) continue
      asked.add(key)
      if (counted.has(key)) {
        skipped.push({ word: w.word, why: 'it has a counting card' })
        continue
      }
      const own = words.get(key)
      if (!own || !learnedItem(own.item)) {
        skipped.push({ word: w.word, why: 'not a word the learner has learned' })
        continue
      }
      if (room <= 0) {
        skipped.push({ word: w.word, why: `today's ${PER_DAY} new counting cards are made` })
        continue
      }
      const { index, pages } = content()
      const nouns = formsOf({ dict, book, pages, lines: index, language }, w.word).filter(e => e.pos === 'noun')
      if (!nouns.length) {
        skipped.push({ word: w.word, why: 'no noun of that name in the dictionary' })
        continue
      }
      // the noun the learner's card means (a word of its gloss in the card's meaning), else the first that makes a card
      const meant = new Set(String(own.item.answer ?? '').toLowerCase().split(/[^\p{L}]+/u).filter(x => x.length > 2))
      const fits = (g: string[]) => (g.some(x => x.toLowerCase().split(/[^\p{L}]+/u).some(y => meant.has(y))) ? 1 : 0)
      const ordered = [...nouns].sort((a, b) => fits(b.gloss) - fits(a.gloss))
      const uses = (f: string) => index.with(f).length
      const tries = ordered.map(e => countingOf(e, uses, language))
      const card = tries.find(isCard) as CountingCard | undefined
      if (!card) {
        skipped.push({ word: w.word, why: (tries[0] as { why: string }).why })
        continue
      }
      let id = countingItemId(card.lemma)
      for (let n = 2; items[id] || vocabulary.some(v => v.item_id === id); n++) id = `${countingItemId(card.lemma)}-${n}`
      const emoji = emojiOf(card.lemma, w.item_id ?? own.id)
      const meaning = typeof own.item.answer === 'string' ? own.item.answer.trim().slice(0, 120) : undefined
      vocabulary.push(countingVocabulary(id, { ...card, ...(emoji ? { emoji } : {}), ...(meaning ? { meaning } : {}) }, today))
      added.push({ item_id: id, lemma: card.lemma, lines: card.lines })
      counted.add(key)
      room--
    }
    if (vocabulary.length) {
      const u = learner.update({ record_session: false, date: today, new_vocabulary: vocabulary })
      if (!u.ok) return json({ error: u.output }, 500)
      events.emit({ type: 'words_added', item_ids: vocabulary.map(v => v.item_id) })
      log(`counting cards made: ${added.map(a => a.lemma).join(', ')}`)
    }
    return json({ added, skipped, made_today: made + added.length, per_day: PER_DAY })
  }

  return {
    instructions,
    routes: [{ method: 'POST', path: '/counting', handle: ({ req, url }) => outbox.once(req, url.pathname, handleCounting) }],
  }
}
