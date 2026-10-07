// The learner's words in the village's dialogs (companion/SCENES.md and GAME.md): a turn that TESTS one of
// their words counts as a review of its card, and the tutor weaves their words into the day's dialog variants. A word
// merely there (said by someone, or in a choice where the choices don't differ in it) counts for nothing, so what
// counts is only what a turn asks the learner to tell apart:
//
//   a choice turn: a wrong choice differs from the right one in that word (another word in its place: its meaning; a
//   wrong form of it: its form). A typed or a said turn asks the same word (the gap is the word the choices differ in);
//   a tap turn: its right choice taps a thing of the scene whose word it is.
//
// The app finds the same words with the same rule (game/scene, the Kotlin twin of [alignTurn]): keep the two identical.
// What is here is pure but for [lemmaFinder], which reads the dictionary handed to it.
import { render, addressee as addresseeFor } from './addressee'
import { category, renderForms, samples, slovene, type CountSpec } from './counts'
import type { FormsBook } from './forms'
import { normalizeForm, type Lexicon } from './lexicon'
import { itemId, normWord, vocabularyWords } from './packs'
import { variantsOf } from './scenes'

// --- the words of a sentence, and which of them a wrong choice tests ------------------------------------------------

/** A word of a text: letters and digits, an apostrophe inside ("l'acqua", "c’è"). */
export const WORD_RE = /[\p{L}\p{N}]+(?:['’][\p{L}\p{N}]+)*/gu

/** The words of [text], lowercased: what the turns are compared by. */
export const wordsIn = (text: string | undefined): string[] => (text ?? '').match(WORD_RE)?.map(w => w.toLowerCase()) ?? []

/**
 * A longest common subsequence of [r] and [w] (exact equality), as pairs of positions, in order. Deterministic so that the
 * app finds the same one: the table of the suffixes' LCS lengths, walked from the start; equal words are matched, and on
 * a tie the right choice's word is passed over first (i + 1), else the wrong one's.
 */
export function lcsPairs(r: string[], w: string[]): [number, number][] {
  const n = r.length
  const m = w.length
  const L: number[][] = Array.from({ length: n + 1 }, () => new Array<number>(m + 1).fill(0))
  for (let i = n - 1; i >= 0; i--) for (let j = m - 1; j >= 0; j--) L[i][j] = r[i] === w[j] ? L[i + 1][j + 1] + 1 : Math.max(L[i + 1][j], L[i][j + 1])
  const pairs: [number, number][] = []
  let i = 0
  let j = 0
  while (i < n && j < m) {
    if (r[i] === w[j]) {
      pairs.push([i, j])
      i++
      j++
    } else if (L[i + 1][j] >= L[i][j + 1]) i++
    else j++
  }
  return pairs
}

/**
 * Which words of a right choice [r] a wrong choice [w] tests, and what stands in the wrong one in their place.
 *
 * U is r's positions outside the LCS. The wrong choice tests them when it differs in one or two words (1 ≤ |U| ≤ 2) and
 * the rest of the sentence is shared, at least as much of it as differs (n − |U| ≥ |U|; a one-word answer always:
 * "Žlica." / "Vilica."). "Deset jajc, prosim." / "Deset jajce, prosim." tests jajc; "Daj mi eno žlico." / "Daj mi dve
 * vilici." tests eno and žlico; "Ja, zelo." / "Dobro jutro!" tests nothing.
 *
 * [substitutes]: a tested position's word in the wrong choice, where the wrong choice has one in its place: between the
 * same two anchors of the LCS, as many unmatched words in the wrong choice as in the right one, paired in order.
 */
export function alignTurn(r: string[], w: string[]): { tested: number[]; substitutes: Map<number, number> } {
  const pairs = lcsPairs(r, w)
  const matched = new Set(pairs.map(([i]) => i))
  const U = r.map((_, i) => i).filter(i => !matched.has(i))
  const n = r.length
  const substitutes = new Map<number, number>()
  if (!(U.length >= 1 && U.length <= 2 && (n === 1 || n - U.length >= U.length))) return { tested: [], substitutes }
  const anchors: [number, number][] = [[-1, -1], ...pairs, [n, w.length]]
  for (let k = 0; k + 1 < anchors.length; k++) {
    const [ri, wj] = anchors[k]
    const [rn, wn] = anchors[k + 1]
    if (rn - ri - 1 > 0 && rn - ri === wn - wj) for (let x = 1; ri + x < rn; x++) substitutes.set(ri + x, wj + x)
  }
  return { tested: U, substitutes }
}

/** The lemmas a word may be a form of, normalized, the word itself among them (lemmaFinder). */
export type LemmaOf = (word: string) => string[]

/** Whether [word] is a form of [lemma]: the same word, or one the dictionary or the forms book reads as a form of it. */
export function isFormOf(word: string, lemma: string, lemmaOf: LemmaOf, language = 'sl'): boolean {
  const l = normalizeForm(lemma, language)
  return normalizeForm(word, language) === l || lemmaOf(word).includes(l)
}

const shareLemma = (a: string, b: string, lemmaOf: LemmaOf) => {
  const la = new Set(lemmaOf(a))
  return lemmaOf(b).some(x => la.has(x))
}

export type How = 'meaning' | 'form'

/**
 * What wrong choice [w] tests of right choice [r] (their words): each tested position, a FORM when what stands in its
 * place is a form of the same word (jajce for jajc: both jajce's), else its MEANING (vilici for žlico; no word in its place).
 */
export function testedBy(r: string[], w: string[], lemmaOf: LemmaOf): Map<number, How> {
  const { tested, substitutes } = alignTurn(r, w)
  const out = new Map<number, How>()
  for (const i of tested) {
    const j = substitutes.get(i)
    out.set(i, j !== undefined && shareLemma(r[i], w[j], lemmaOf) ? 'form' : 'meaning')
  }
  return out
}

// --- a dialog's tested words ----------------------------------------------------------------------------------------

type Said = Partial<Record<string, unknown>>
type ChoiceLike = Said & { ok?: boolean; tap?: string; wrong_form?: number }
export type DialogLike = { id?: string; count?: CountSpec; lines: (Said & { choices?: ChoiceLike[] })[]; words?: string[] }
/** The scene a dialog plays in, as far as the words need it: its language, and its things (resolved: their word). */
export type SceneLike = { language: string; objects: (Said & { slot: string; word: string; pack?: string })[] }

/** One word a turn tests: where (the line, its right choice, the word's place in it), the word as said, and how. */
export type Tested = {
  line: number
  choice: number
  /** The word's place in the right choice; -1 for a tap turn's thing. */
  position: number
  /** The word as in the sentence, lowercased; a tap turn's: the thing's word. */
  word: string
  how: How[]
  /** The right choice, as said to a man (the placeholders rendered). */
  text: string
  /** A tap turn's: the slot of the thing tapped, and its word's card (vocab_<pack>_<word>). */
  tap?: string
  item_id?: string
}

/**
 * Whom the texts are rendered for before they are compared: a man, as the app does (a turn's choices differ alike for a
 * woman: crossCheck refuses one that doesn't).
 */
const MAN = addresseeFor({ name: 'Learner' })

/** The numbers a counting dialog is rendered at to find its words: one of each of its language's categories it reaches. */
function countNumbers(c: CountSpec, language: string): number[] {
  const forms = language === 'sl' ? 4 : 2
  const seen = new Set<number>()
  return samples(c).filter(n => {
    const k = forms === 4 ? slovene(n) : category(n, 2)
    if (seen.has(k)) return false
    seen.add(k)
    return true
  })
}

/**
 * The words [d]'s turns test, in scene [scene]: for each right choice of a choice turn, the union over the turn's wrong
 * choices of what each tests (testedBy); for a tap turn (every choice taps somewhere), the thing its right choice taps,
 * when that is a thing of the scene (finding a place is no choice between words). The texts are said to a man first
 * ({learner}, {m:…|f:…}), and a counting dialog's at a number of each category ({n}, {ovca|ovci|ovce|ovc}).
 */
export function testedWords(d: DialogLike, scene: SceneLike, lemmaOf: LemmaOf): Tested[] {
  const lang = scene.language
  const arity = lang === 'sl' ? 4 : 2
  const numbers: (number | undefined)[] = d.count ? countNumbers(d.count, lang) : [undefined]
  const say = (c: ChoiceLike, n: number | undefined): string => {
    const t = c[lang]
    if (typeof t !== 'string') return ''
    const r = render(t, MAN)
    return n === undefined ? r : renderForms(r, n, arity, c.ok ? undefined : c.wrong_form)
  }
  const out = new Map<string, Tested>()
  d.lines.forEach((l, li) => {
    const choices = l.choices ?? []
    if (!choices.length) return
    const tap = choices.every(c => c.tap !== undefined)
    choices.forEach((c, ci) => {
      if (!c.ok) return
      if (tap) {
        const o = scene.objects.find(x => x.slot === c.tap)
        const word = typeof o?.[lang] === 'string' ? (o[lang] as string) : undefined
        if (!o || !word) return
        out.set(`${li}/${ci}/tap`, {
          line: li, choice: ci, position: -1, word: word.toLowerCase(), how: ['meaning'], text: say(c, numbers[0]), tap: o.slot,
          ...(o.pack ? { item_id: itemId(o.pack, o.word) } : {}),
        })
        return
      }
      for (const n of numbers) {
        const text = say(c, n)
        const r = wordsIn(text)
        for (const w of choices) {
          if (w.ok) continue
          for (const [i, how] of testedBy(r, wordsIn(say(w, n)), lemmaOf)) {
            const key = `${li}/${ci}/${i}/${r[i]}`
            const had = out.get(key)
            if (!had) out.set(key, { line: li, choice: ci, position: i, word: r[i], how: [how], text })
            else if (!had.how.includes(how)) had.how = [...had.how, how].sort() as How[]
          }
        }
      }
    })
  })
  return [...out.values()]
}

// --- the dictionary: which words a form may be of -------------------------------------------------------------------

/**
 * The lemmas a word may be a form of, in the village's [language]: the word itself, what the dictionary reads it as
 * (Wiktionary's forms, the supplement, the tutor's glosses: dict.readings) and the forms book's hand-checked tables (biti,
 * iti …). Kept per word for the finder's life: make one per check.
 */
export function lemmaFinder(dict: Pick<Lexicon, 'readings'> | undefined, book: Pick<FormsBook, 'current'> | undefined, language = 'sl'): LemmaOf {
  const cache = new Map<string, string[]>()
  let tables: Map<string, string[]> | undefined
  const norm = (s: string) => normalizeForm(s, language)
  const fromTables = (f: string) => {
    if (!tables) {
      tables = new Map()
      for (const t of book?.current().tables ?? []) {
        for (const v of [...Object.values(t.forms), ...Object.values(t.also ?? {})])
          for (const form of typeof v === 'string' ? [v] : (v ?? [])) tables.set(norm(form), [...(tables.get(norm(form)) ?? []), norm(t.lemma)])
      }
    }
    return tables.get(f) ?? []
  }
  return word => {
    const f = norm(word)
    let hit = cache.get(f)
    if (hit) return hit
    let read: string[] = []
    try {
      read = dict?.readings(f, 'en').map(r => norm(r.lemma)) ?? []
    } catch {
      // no dictionary: the word alone
    }
    hit = [...new Set([f, ...read, ...fromTables(f)])]
    cache.set(f, hit)
    return hit
  }
}

// --- the words a dialog declares: each one tested by a turn ---------------------------------------------------------

/** A review card as the words need it (spaced-repetition.json's items). */
export type Card = {
  type?: string
  content?: string
  answer?: string
  category?: string
  due_date?: string
  interval_days?: number
  repetitions?: number
  total_reviews?: number
  last_reviewed?: string | null
  last_quality?: number
  consecutive_incorrect?: number
  last_lapse?: string
}

/** A card's word, when it is one word: its content without a note in brackets ("hiša (house)": hiša). */
export function cardWord(c: Card | undefined): string | undefined {
  if (!c || (c.type ?? 'vocabulary') !== 'vocabulary' || typeof c.content !== 'string') return undefined
  const words = c.content.replace(/\s*\([^)]*\)/g, '').match(WORD_RE) ?? []
  return words.length === 1 ? words[0] : undefined
}

/** One declared word: as declared, its lemma, its card (none: not one of the learner's) and the turns that test it. */
export type Woven = { declared: string; lemma: string; item_id?: string; tests: Tested[] }

/** Where in [d] (in [lang]) a form of [lemma] stands although no turn tests it: for the refusal's hint. */
function presentAt(d: DialogLike, lang: string, lemma: string, lemmaOf: LemmaOf): string | undefined {
  const has = (t: unknown) => typeof t === 'string' && wordsIn(render(t, MAN)).some(w => isFormOf(w, lemma, lemmaOf, lang))
  for (const [li, l] of d.lines.entries()) {
    if (!l.choices?.length) {
      if (has(l[lang])) return `it is only said, at lines[${li}]`
      continue
    }
    for (const [ci, c] of l.choices.entries()) {
      if (has(c[lang])) return `it is in lines[${li}].choices[${ci}], but the choices don't differ in it there`
      if (has((c.reply as Said | undefined)?.[lang])) return `it is only said, in the reply at lines[${li}].choices[${ci}]`
    }
  }
  return undefined
}

/**
 * Whether the words dialog [d] declares ([declared]: lemmas, "žlica", or card ids, "vocab_v-kuhinji_zlica") are each the
 * tested element of a turn of it (testedWords) in [scene], with the learner's cards [items]. An id no card has, a card
 * that isn't one word, a word no turn tests: errors (nothing should be published). A lemma that isn't a card: a note
 * (it counts for nothing until it is). [words]: each declared word with the turns that test it.
 */
export function checkWoven(
  d: DialogLike,
  scene: SceneLike,
  declared: string[],
  o: { items: Record<string, Card>; lemmaOf: LemmaOf },
): { errors: string[]; notes: string[]; words: Woven[] } {
  const lang = scene.language
  const errors: string[] = []
  const notes: string[] = []
  const words: Woven[] = []
  const tested = testedWords(d, scene, o.lemmaOf)
  const byWord = vocabularyWords(o.items)
  for (const [i, raw] of [...new Set(declared.map(w => w.trim()).filter(Boolean))].entries()) {
    const card = o.items[raw]
    let lemma: string
    let id: string | undefined
    if (card) {
      const w = cardWord(card)
      if (!w) {
        errors.push(`words[${i}]: the card "${raw}" is ${typeof card.content === 'string' ? `"${card.content}"` : 'no word card'}, not one word: a turn tests one word at a time (declare a one-word card, or its lemma)`)
        continue
      }
      lemma = w
      id = raw
    } else if (/^vocab_/.test(raw) || raw.includes('_')) {
      errors.push(`words[${i}]: no card "${raw}" (the learner's cards are in spaced-repetition; a word that is none is declared as its lemma, "žlica")`)
      continue
    } else if (wordsIn(raw).length !== 1) {
      errors.push(`words[${i}]: "${raw}" is not one word: declare a lemma ("žlica") or a card id`)
      continue
    } else {
      lemma = raw
      id = byWord.get(normWord(raw))
      if (!id) notes.push(`"${raw}" is not one of the learner's cards: it counts for nothing until it is`)
    }
    const tests = tested.filter(t => isFormOf(t.word, lemma, o.lemmaOf, lang) || (!!id && t.item_id === id))
    if (!tests.length) {
      const where = presentAt(d, lang, lemma, o.lemmaOf)
      errors.push(
        `"${raw}" is in no turn's test${where ? ` (${where})` : ''}: put it where a wrong choice differs from the right one in that word ` +
          `("Daj mi žlico." / "Daj mi vilico."), or tap its thing; a word only said by someone or only present in a choice counts for nothing`,
      )
      continue
    }
    words.push({ declared: raw, lemma, ...(id ? { item_id: id } : {}), tests })
  }
  return { errors, notes, words }
}

/** "lines[3] tests "žlico" (meaning) in "Daj mi žlico."", "lines[2] taps the stove (peč)": where a word is tested. */
export function testedAt(t: Tested): string {
  return t.tap !== undefined ? `lines[${t.line}] taps its thing (${t.tap}: ${t.word})` : `lines[${t.line}] tests "${t.word}" (${t.how.join(', ')}) in "${t.text}"`
}

// --- which words the tutor weaves, and where ------------------------------------------------------------------------

export type WeaveWhy = 'weak' | 'due' | 'new'
/** A happening a word fits: its scene, its id, its person's name. */
export type WeaveFit = { scene: string; happening: string; person: string }
export type WeaveWord = { item_id: string; lemma: string; meaning?: string; why: WeaveWhy; fits: WeaveFit[] }

/** At most this many of the learner's words are offered to weave at once. */
export const WEAVE_WORDS = 12
/** At most this many woven variants a day besides the answers to dialog_variants_heard (those stay at most 3 a day). */
export const WEAVES_A_DAY = 2

type WeaveScene = {
  id: string
  pack?: string
  objects: { pack?: string }[]
  people: { id: string; name: string; villager?: string }[]
  happenings: { id: string; who: string; dialog?: string; dialogs?: string[]; stories?: boolean; guests?: boolean }[]
  dialogs: { id: string; guests?: boolean }[]
}

/** "YYYY-MM-DD" plus [days]. */
export function addDays(day: string, days: number): string {
  const d = new Date(`${day}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

const RANK: Record<WeaveWhy, number> = { weak: 0, due: 1, new: 2 }

/**
 * The learner's words worth weaving into a dialog today: their one-word vocabulary cards ([items]) that are weak
 * (last_quality under 3, a wrong answer in a row, or a word of an open mistakes-db pattern's example), due (by tomorrow)
 * or new (not reviewed yet), weak first, then due, then new, at most [max]; each with the happenings it fits. A card fits
 * by its pack (its category, or its id vocab_<pack>_<word>): the scenes that teach that pack (theirs, or a thing's), their
 * happenings; and by the pack's giver (a villager of the cast by name): the happenings where they are the person. Not a
 * storyteller's happening, nor a guest's, nor one without dialogs. A word that fits nowhere isn't offered.
 */
export function wordsToWeave(o: {
  items: Record<string, Card>
  today: string
  mistakes?: unknown
  scenes: WeaveScene[]
  packs: { id: string; giver?: { name: string } }[]
  villagers: { id: string; name: string }[]
  lemmaOf?: LemmaOf
  language?: string
  max?: number
}): WeaveWord[] {
  const language = o.language ?? 'sl'
  const lemmaOf = o.lemmaOf ?? ((w: string) => [normalizeForm(w, language)])
  const packs = new Map(o.packs.map(p => [p.id, p]))
  const norm = (s: string) => s.trim().toLocaleLowerCase(language)
  // the words of the open patterns' examples (still going wrong)
  const patterns = ((o.mistakes as { error_patterns?: Record<string, any> } | undefined)?.error_patterns ?? {}) as Record<string, any>
  const mistaken = Object.values(patterns)
    .filter(p => (p?.consecutive_incorrect ?? 0) > 0)
    .flatMap(p => (Array.isArray(p?.examples) ? p.examples : []))
    .flatMap((e: any) => [e?.incorrect, e?.correct, e?.your_answer, e?.correct_answer].flatMap(t => (typeof t === 'string' ? wordsIn(t) : [])))
  const inMistakes = (lemma: string) => mistaken.some(w => isFormOf(w, lemma, lemmaOf, language))
  // a guest's happening (forGuests in scenes.ts): it says so, or a dialog it plays does
  const guests = (s: WeaveScene, h: WeaveScene['happenings'][number]) => h.guests === true || s.dialogs.some(d => variantsOf(h).includes(d.id) && d.guests === true)
  const happeningsOf = (pred: (s: WeaveScene, h: WeaveScene['happenings'][number]) => boolean): WeaveFit[] =>
    o.scenes.flatMap(s =>
      s.happenings
        .filter(h => !h.stories && !guests(s, h) && variantsOf(h).length > 0 && pred(s, h))
        .map(h => {
          const p = s.people.find(x => x.id === h.who)
          const name = (p?.villager && o.villagers.find(v => v.id === p.villager)?.name) || p?.name || h.who
          return { scene: s.id, happening: h.id, person: name }
        }),
    )
  const fitsOf = (packId: string | undefined): WeaveFit[] => {
    if (!packId) return []
    const byPack = happeningsOf(s => s.pack === packId || s.objects.some(x => x.pack === packId))
    const giver = packs.get(packId)?.giver?.name
    const v = giver ? o.villagers.find(x => norm(x.name) === norm(giver)) : undefined
    const byGiver = v ? happeningsOf((s, h) => s.people.some(p => p.id === h.who && p.villager === v.id)) : []
    const seen = new Set<string>()
    return [...byPack, ...byGiver].filter(f => !seen.has(`${f.scene}/${f.happening}`) && !!seen.add(`${f.scene}/${f.happening}`))
  }
  const packOf = (id: string, c: Card): string | undefined => {
    if (c.category && packs.has(c.category)) return c.category
    const m = /^vocab_([a-z0-9][a-z0-9-]*)_/.exec(id)
    return m && packs.has(m[1]) ? m[1] : undefined
  }
  const soon = addDays(o.today, 1)
  const out: (WeaveWord & { due?: string })[] = []
  for (const [id, c] of Object.entries(o.items)) {
    const lemma = cardWord(c)
    if (!lemma) continue
    const weak = (typeof c.last_quality === 'number' && c.last_quality < 3) || (c.consecutive_incorrect ?? 0) >= 1 || inMistakes(lemma)
    const due = !c.due_date || c.due_date <= soon
    const fresh = (c.total_reviews ?? 0) === 0 || (c.repetitions ?? 0) === 0
    const why: WeaveWhy | undefined = weak ? 'weak' : due ? 'due' : fresh ? 'new' : undefined
    if (!why) continue
    const fits = fitsOf(packOf(id, c))
    if (!fits.length) continue
    out.push({ item_id: id, lemma, ...(c.answer ? { meaning: c.answer } : {}), why, fits, due: c.due_date })
  }
  return out
    .sort((a, b) => RANK[a.why] - RANK[b.why] || (a.due ?? '').localeCompare(b.due ?? '') || a.item_id.localeCompare(b.item_id))
    .slice(0, o.max ?? WEAVE_WORDS)
    .map(({ due: _due, ...w }) => w)
}

/** The words of [words] that fit happening [happening] of [scene], at most [max]: what dialog_variants_heard offers. */
export function fittingWords(words: WeaveWord[], scene: string, happening: string, max = 4): Omit<WeaveWord, 'fits'>[] {
  return words.filter(w => w.fits.some(f => f.scene === scene && f.happening === happening)).slice(0, max).map(({ fits: _fits, ...w }) => w)
}

/**
 * The morning's plan of words to weave: a few happenings ([max]) with 2-4 of [words] that fit each, the happenings with
 * the learner's weakest words first.
 */
export function weavePlan(words: WeaveWord[], max = 3): (WeaveFit & { words: Omit<WeaveWord, 'fits'>[] })[] {
  const groups = new Map<string, WeaveFit & { words: Omit<WeaveWord, 'fits'>[] }>()
  for (const w of words) {
    const { fits, ...word } = w
    for (const f of fits) {
      const key = `${f.scene}/${f.happening}`
      const g = groups.get(key) ?? { ...f, words: [] }
      if (g.words.length < 4) g.words.push(word)
      groups.set(key, g)
    }
  }
  return [...groups.values()].filter(g => g.words.length >= 2).slice(0, max)
}

/**
 * How many woven variants (a tutor's variant that declares words) were published [today] besides the answers to the
 * day's dialog_variants_heard ([asked]: variant-asks.json, a happening asked about today), by their files' time.
 */
export function wovenToday(variants: { scene: string; happening: string; dialog: { words?: string[] }; published_at?: number }[], asked: Record<string, { on?: string }>, today: string, day: (t: number) => string): number {
  return variants.filter(v => v.dialog.words?.length && v.published_at !== undefined && day(v.published_at) === today && asked[`${v.scene}/${v.happening}`]?.on !== today).length
}

// --- the reviews a dialog brings: the bridge re-checks the app ------------------------------------------------------

/**
 * How many days before its due date a card counts in a dialog: a tenth of its interval, 1 to 3 days (the app's
 * PlayReviews.window, for «Vidim, vidim» and the dialogs alike).
 */
export const dialogWindow = (interval?: number): number => Math.min(3, Math.max(1, Math.round((typeof interval === 'number' && interval > 0 ? interval : 1) / 10)))

/**
 * Whether a right answer in a dialog counts as a review of card [c] [today], as the app decides it (PlayReviews.counts):
 * it wasn't reviewed today (a card added today was), and it is due within its window (dialogWindow); a card without a
 * due date doesn't.
 */
export function countsInDialog(c: Card | undefined, today: string): boolean {
  if (!c) return false
  if (typeof c.last_reviewed === 'string' && c.last_reviewed >= today) return false
  if (typeof c.due_date !== 'string' || !c.due_date) return false
  return c.due_date <= addDays(today, dialogWindow(c.interval_days))
}
