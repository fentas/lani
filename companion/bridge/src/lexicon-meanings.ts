// A dictionary's meanings in the learner's base language (companion/README.md, "Word lookup"): how
// companion/bin/lexicon-build finds, for a lemma (Italian casa), its meaning in Slovene, German, Italian or English from
// Wiktionary's extracts at kaikki.org. The sources, the best evidence first (meaningsOf):
// 1. direct: the base language's own Wiktionary explains the word in the base, in a few words (German Wiktionary's
//    Italian "casa": Haus, Zuhause);
// 2. pivot, confirmed: the lemma's English gloss, and the table of that English word that lists the lemma itself
//    (casa, "house": English Wiktionary's table "human abode" lists Italian casa and Slovene hiša): the sense shown, and
//    a table that says so;
// 3. translations: a table between the two languages: the target's Wiktionary lists the word's translations (German
//    Wiktionary's "Haus": Slovene hiša), or, turned round, the base's Wiktionary lists the word first as a translation
//    of one of its own (Italian Wiktionary's "casa" lists German Haus: Haus means casa);
// 4. pivot, together: a third language's table lists the lemma and base words (English "forest": Italian bosco, Slovene
//    gozd);
// 5. pivot, by sense: the English gloss's table its other words single out ("bank (of a river)": "edge of river");
//    never a guess between two senses ("bank" alone), never an English word's only table (another sense may have none);
// 6. the base's Wiktionary's gloss even when it is a definition.
// companion/bin/lexicon-build --index writes each extract's rows (IndexRow) once; --meanings picks from them.
import { normalizeForm } from './lexicon'

/** The languages Lani serves: a dictionary's target, and the bases its meanings are in. */
export const LANI_LANGUAGES = ['sl', 'it', 'de', 'en'] as const

/** How a meaning in the base was found; "claude": written by the tutor (publish_gloss), machine-written. */
export type Via = 'direct' | 'translations' | 'pivot' | 'claude'
export type Meaning = { gloss: string[]; via: Via }

/** A translation table: its sense as the table names it, its words per language, and (English) the gloss it sits under. */
export type Table = { s: string; x: Record<string, string[]>; g?: string }

/**
 * One row of an index: an entry of one of Lani's languages in one Wiktionary: the entry's translation tables (a word
 * of the Wiktionary's own language, with [n], how many senses it has), or its glosses (a word of another language,
 * explained in the Wiktionary's).
 */
export type IndexRow = { w: string; l: string; p: string; t?: Table[]; n?: number; g?: string[] }

/** wiktextract's JSON (kaikki.org), as far as the index reads it. */
type RawTranslation = { lang_code?: string; code?: string; word?: string; sense?: string; sense_index?: string; tags?: string[]; raw_tags?: string[] }
type RawSense = { glosses?: string[]; tags?: string[]; form_of?: unknown[]; alt_of?: unknown[]; translations?: RawTranslation[] }
export type RawEntry = { word?: string; pos?: string; lang_code?: string; senses?: RawSense[]; translations?: RawTranslation[] }

// --- glosses -------------------------------------------------------------------------------------------------------

/** A gloss short enough for a phone: no explanations in parentheses or brackets, at most ~60 characters. */
export function shortGloss(g: string): string {
  let s = g.replace(/\^\([^)]*\)/g, '')
  for (let prev = ''; prev !== s; ) {
    prev = s
    s = s.replace(/\s*\([^()]*\)/g, '').replace(/\s*\[[^[\]]*\]/g, '')
  }
  s = s.replace(/\s+/g, ' ').trim().replace(/[.;:,]+$/, '')
  if (!s) s = g.trim()
  if (s.length <= 60) return s
  const cut = Math.max(s.lastIndexOf(', ', 60), s.lastIndexOf('; ', 60))
  if (cut >= 20) return s.slice(0, cut)
  const space = s.lastIndexOf(' ', 59)
  return `${s.slice(0, space > 20 ? space : 59)}…`
}

/**
 * A word as a meaning shows it: Slovene without Wiktionary's tonal accents (dóber → dober), the caron kept, and its
 * pronunciation letters as spelled (nəčke → nečke), as normalizeForm compares them.
 */
export function plainWord(w: string, lang: string): string {
  const s = w.trim().replace(/\s+/g, ' ')
  return lang === 'sl' ? s.normalize('NFD').replace(/(?!̌)\p{M}/gu, '').normalize('NFC').replace(/ł/g, 'l').replace(/ə/g, 'e') : s
}

/** The articles a gloss or a table's word may start with ("das Haus", "la casa", "l'acqua"). */
const ARTICLE: Record<string, RegExp> = {
  de: /^(?:der|die|das|den|dem|des|ein|eine|einen|einem|einer|eines)\s+(?=\S)/i,
  it: /^(?:(?:il|lo|la|i|gli|le|un|uno|una)\s+|(?:l|un)['’](?=\p{L}))/iu,
  en: /^(?:the|a|an)\s+(?=\S)/i,
}
export const stripArticle = (w: string, lang: string) => w.replace(ARTICLE[lang] ?? /^$/, '')

/**
 * A gloss in [lang] as a meaning shows it: short, without the articles and German Wiktionary's gender marks and
 * explanations ("das Haus, das Zuhause" → "Haus, Zuhause"; "Engel m" → Engel; "Bergsteigerin: weibliche Person" →
 * Bergsteigerin).
 */
export function cleanGloss(g: string, lang: string): string {
  return shortGloss(g)
    .split(/,\s+/)
    .map(p => plainWord(stripArticle(p.trim(), lang).replace(/^([^:]{1,30}):\s.*$/, '$1').replace(/\s+(?:m|f|n|pl|sg|mf)\.?$/, ''), lang))
    .filter(Boolean)
    .join(', ')
}

/** Senses that go last, as in the dictionary itself. */
const RARE = new Set(['archaic', 'obsolete', 'dated', 'rare', 'dialectal', 'historical'])
/** Translations never shown. */
const OLD = new Set(['archaic', 'obsolete', 'dialectal', 'regional'])
/**
 * A translation into a dialect or a region's variety, not the standard language: a variety's name as a tag
 * ("Alemannic-German": Stain for Stein) or a note the extract didn't parse ("Alsatian Alemannic German", "besonders
 * Schottland").
 */
const variety = (t: RawTranslation) => !!t.raw_tags?.length || !!t.tags?.some(x => OLD.has(x) || /^\p{Lu}/u.test(x))
/** What some Wiktionaries write where a gloss is still missing. */
const PLACEHOLDER = /definizione mancante|Bedeutung fehlt|^\?+$/i

/** The words a translation table gives for one translation ("work , be operable" → work, be operable). */
export function translationWords(raw: string | undefined, lang: string): string[] {
  if (!raw) return []
  return raw
    .split(/\s*[,;]\s*/)
    .map(w => plainWord(w.replace(/\([^)]*\)/g, ' '), lang).replace(/^[\s\-–—]+|[\s\-–—.!?¡¿]+$/g, ''))
    .filter(w => w.length > 0 && w.length <= 40 && !/[0-9=<>[\]{}|/\\*]/.test(w) && w.split(' ').length <= 4)
}

/** Italian Wiktionary names a table's part of speech first: "(verbo) muoversi da un luogo verso un altro luogo". */
const SENSE_POS: Record<string, string> = { verbo: 'verb', sostantivo: 'noun', aggettivo: 'adj', avverbio: 'adv', pronome: 'pron', preposizione: 'prep', congiunzione: 'conj', interiezione: 'intj' }

/** An entry's translation tables into Lani's other languages, in the order of its senses; empty when it has none. */
export function tablesOf(e: RawEntry, edition: string): Table[] {
  const want = new Set<string>(LANI_LANGUAGES.filter(l => l !== edition))
  const rows = [
    ...(e.translations ?? []).map(t => ({ t, g: undefined as string | undefined })),
    ...(e.senses ?? []).flatMap(s => (s.translations ?? []).map(t => ({ t, g: s.glosses?.at(-1) }))),
  ]
  const tables = new Map<string, Table & { i: number }>()
  for (const { t, g } of rows) {
    const lang = t.lang_code ?? t.code
    if (!lang || !want.has(lang) || variety(t)) continue
    let s = (t.sense ?? '').trim()
    const marked = /^\((\p{L}+)\)\s*/u.exec(s)
    if (marked && SENSE_POS[marked[1]]) {
      if (SENSE_POS[marked[1]] !== e.pos) continue
      s = s.slice(marked[0].length)
    }
    // German Wiktionary numbers its senses: a table is its sense's number ("1", "2a")
    const key = t.sense_index ? `#${t.sense_index}` : s
    const words = translationWords(t.word, lang)
    if (!words.length) continue
    const i = t.sense_index ? Number.parseFloat(t.sense_index) : Number.NaN
    const table = tables.get(key) ?? { s, x: {}, i: Number.isNaN(i) ? tables.size + 1000 : i, ...(edition === 'en' && g ? { g } : {}) }
    table.x[lang] = [...new Set([...(table.x[lang] ?? []), ...words])]
    tables.set(key, table)
  }
  return [...tables.values()].sort((a, b) => a.i - b.i).map(({ i: _, ...t }) => t)
}

/** An entry's senses that are meanings, not the forms of other words. */
const meaningSenses = (e: RawEntry) => (e.senses ?? []).filter(s => !s.form_of?.length && !s.alt_of?.length && !s.tags?.includes('form-of') && !s.tags?.includes('alt-of'))

/** An entry's glosses as written, its meanings first, rare ones last; not the forms of other words. */
export function glossesOf(e: RawEntry): string[] {
  const late = (s: RawSense) => (s.tags?.some(t => RARE.has(t)) ? 1 : 0)
  const senses = meaningSenses(e)
  const out: string[] = []
  for (const s of senses.sort((a, b) => late(a) - late(b))) {
    // the heading of subsenses ("Taking the form of …:") says nothing: its subsense does
    let g = s.glosses?.[0]?.trim()
    if (g?.endsWith(':')) g = s.glosses?.length && s.glosses.length > 1 ? s.glosses.at(-1)?.trim() : undefined
    if (g && !PLACEHOLDER.test(g) && !out.includes(g)) out.push(g)
  }
  return out.slice(0, 8)
}

/** A Wiktionary entry as an index row of [edition] (the Wiktionary's language); undefined when it has nothing to give. */
export function indexRow(e: RawEntry, edition: string): IndexRow | undefined {
  const l = e.lang_code
  if (!e.word || !e.pos || !l || !(LANI_LANGUAGES as readonly string[]).includes(l)) return undefined
  if (l === edition) {
    const t = tablesOf(e, edition)
    return t.length ? { w: e.word, l, p: e.pos, t, n: meaningSenses(e).length } : undefined
  }
  const g = glossesOf(e)
  return g.length ? { w: e.word, l, p: e.pos, g } : undefined
}

/**
 * [rows] without the tables a Wiktionary copied from one part of speech of a page to the others (Italian Wiktionary
 * lists a page's translations under each: "grazie" the interjection and "grazie" the noun): a table the same under
 * several stays with the one of the most senses, the page's main word.
 */
export function unshare(rows: IndexRow[]): IndexRow[] {
  const byWord = new Map<string, IndexRow[]>()
  for (const r of rows) if (r.t) byWord.set(r.w, [...(byWord.get(r.w) ?? []), r])
  const drop = new Map<IndexRow, Set<string>>()
  for (const group of byWord.values()) {
    if (new Set(group.map(r => r.p)).size < 2) continue
    const having = new Map<string, IndexRow[]>()
    for (const r of group) for (const t of r.t!) having.set(JSON.stringify(t), [...(having.get(JSON.stringify(t)) ?? []), r])
    for (const [sig, rs] of having) {
      if (new Set(rs.map(r => r.p)).size < 2) continue
      const keep = rs.reduce((a, b) => ((b.n ?? 0) > (a.n ?? 0) ? b : a))
      for (const r of rs) if (r !== keep) drop.set(r, (drop.get(r) ?? new Set()).add(sig))
    }
  }
  return rows.flatMap(r => {
    const d = drop.get(r)
    if (!d) return [r]
    const t = r.t!.filter(x => !d.has(JSON.stringify(x)))
    return t.length ? [{ ...r, t }] : []
  })
}

// --- picking a meaning -------------------------------------------------------------------------------------------

/** The parts of speech of an English entry (or a table's headword) a lemma's meaning may come from. */
export const SAME_POS: Record<string, string[]> = { noun: ['noun'], verb: ['verb'], adj: ['adj'], adv: ['adv'], intj: ['intj', 'phrase', 'particle'], phrase: ['phrase', 'intj'], num: ['num'], pron: ['pron'], prep: ['prep'], conj: ['conj'], det: ['det', 'pron'], particle: ['particle', 'adv', 'intj'] }

/** Whether a word of part of speech [p] can mean a lemma of [pos]. */
export const samePos = (pos: string, p: string) => p === pos || !!SAME_POS[pos]?.includes(p)

/** An adjective's words without its other genders' forms ("bello, bella" → bello). */
function oneGender(words: string[], pos: string): string[] {
  if (pos !== 'adj') return words
  return words.filter((w, i) => !words.slice(0, i).some(u => u.length === w.length && u.length > 3 && u.slice(0, -1) === w.slice(0, -1)))
}

/** The English words a gloss stands for, as headwords of English Wiktionary: "to talk, to speak (a language)" → talk, speak. */
export function englishHeads(gloss: string, pos: string): string[] {
  return shortGloss(gloss)
    .split(/[;,]\s*/)
    .map(g => g.trim().toLowerCase().replace(/[!?.…]+$/, '').replace(pos === 'verb' ? /^to\s+/ : /^$/, ''))
    .filter(g => g && /^[a-z][a-z' -]*$/.test(g) && g.split(' ').length <= 3)
    .slice(0, 3)
}

const STOP = new Set(['the', 'and', 'for', 'with', 'from', 'into', 'onto', 'that', 'this', 'which', 'who', 'one', 'ones', 'someone', 'something', 'used', 'being', 'etc', 'especially', 'usually', 'often', 'any', 'some', 'its', 'their', 'other', 'such', 'not', 'very', 'more', 'most', 'also', 'kind', 'type', 'sort', 'act', 'state', 'person', 'thing'])

/** A text's content words: lowercase, no stop words, at least 3 letters. */
const contentWords = (s: string) => (s.toLowerCase().match(/[a-z]+/g) ?? []).filter(w => w.length >= 3 && !STOP.has(w))

/** The words of an English gloss besides its heads: what tells its sense ("bank (financial institution)": financial, institution). */
export function contextWords(gloss: string, heads: string[]): string[] {
  const own = new Set(heads.flatMap(contentWords))
  return [...new Set(contentWords(gloss).filter(w => !own.has(w)))]
}

/** Whether two words are the same word, or share a stem of at least 5 letters (financial, finance). */
function sameStem(a: string, b: string): boolean {
  if (a === b) return true
  let n = 0
  while (n < a.length && n < b.length && a[n] === b[n]) n++
  return n >= 5 && n >= Math.min(a.length, b.length) - 3
}

/** How many of [context] a table's sense (and, in English Wiktionary, the gloss it sits under) has. */
function overlap(t: Table, context: string[]): number {
  const words = contentWords(`${t.s} ${t.g ?? ''}`)
  return context.filter(c => words.some(w => sameStem(c, w))).length
}

/** The first of each list, then the second …, without repeats: at most [n]. */
export function roundRobin(lists: string[][], n = 3): string[] {
  const out: string[] = []
  for (let i = 0; out.length < n && lists.some(l => l[i] !== undefined); i++) {
    for (const l of lists) if (l[i] !== undefined && out.length < n && !out.some(o => o.toLowerCase() === l[i].toLowerCase())) out.push(l[i])
  }
  return out
}

/** Whether a table lists [lemma] (normalized) among its words in [lang]. */
const lists = (t: Table, lang: string, lemma: string) => (t.x[lang] ?? []).some(w => normalizeForm(stripArticle(w, lang), lang) === lemma)

/**
 * The meaning of a [target] lemma in [base] through English: for each of its English glosses (the first four), the
 * tables of its heads' English entries (the same part of speech) that list the lemma itself ([confirmed]); for a gloss
 * with none, the one table its other words single out ("bank (financial institution)"). Never the only table of an
 * English word: another sense may have none (English "present" has a table for the time, none for the gift). At most
 * 2 words of a gloss, 3 in all; [confirmed] when they all come from tables that list the lemma.
 */
export function pivot(target: string, base: string, lemma: string, pos: string, enGlosses: string[], english: Map<string, Table[]>): { words: string[]; confirmed: boolean } {
  const own = normalizeForm(stripArticle(lemma, target), target)
  const sure: string[][] = []
  const picked: string[][] = []
  const take = (tables: Table[]) => {
    const words: string[] = []
    for (const t of tables) for (const w of oneGender(t.x[base] ?? [], pos)) if (words.length < 2 && !words.includes(w)) words.push(w)
    return words
  }
  for (const g of enGlosses.slice(0, 4)) {
    const heads = englishHeads(g, pos)
    const tables = heads.flatMap(h => (SAME_POS[pos] ?? [pos]).flatMap(p => english.get(`${h}|${p}`) ?? []))
    const confirmed = tables.filter(t => lists(t, target, own) && t.x[base]?.length)
    if (confirmed.length) {
      sure.push(take(confirmed))
      continue
    }
    const context = contextWords(g, heads)
    const scored = tables.filter(t => t.x[base]?.length).map(t => ({ t, n: overlap(t, context) })).sort((a, b) => b.n - a.n)
    if (scored[0] && scored[0].n > 0 && (scored[1]?.n ?? 0) < scored[0].n) picked.push(take([scored[0].t]))
  }
  return sure.length ? { words: roundRobin(sure), confirmed: true } : { words: roundRobin(picked), confirmed: false }
}

/**
 * A base word listed in the same translation table as the target word, in a third language's Wiktionary (English's
 * "forest" lists Italian bosco and Slovene gozd): the table is one sense, so the two mean the same. [score]: how often,
 * and how early in the lists, the two are met together.
 */
export type Together = { w: string; score: number }

/** The base words met together with the target word, the most often and earliest first; at most 3. */
export function together(candidates: Together[], pos = ''): string[] {
  const score = new Map<string, number>()
  for (const c of candidates) score.set(c.w, (score.get(c.w) ?? 0) + c.score)
  const ranked = [...score].sort((a, b) => b[1] - a[1]).map(([w]) => w)
  return oneGender(ranked, pos).slice(0, 3)
}

/**
 * What a table gives [together]: for each target word among its first three, the base words among its first three,
 * scored by their places (both first: 1).
 */
export function pairsOf(t: Table, target: string, base: string): { target: string; w: string; score: number }[] {
  const out: { target: string; w: string; score: number }[] = []
  ;(t.x[target] ?? []).slice(0, 3).forEach((tw, i) => (t.x[base] ?? []).slice(0, 3).forEach((w, j) => out.push({ target: normalizeForm(stripArticle(tw, target), target), w, score: 1 / ((1 + i) * (1 + j)) })))
  return out
}

/** The words of a word's own translation tables in [base], a sense at a time (at most 2 of a sense, 3 in all). */
export function tableWords(tables: Table[], base: string, pos = ''): string[] {
  return roundRobin(tables.map(t => oneGender(t.x[base] ?? [], pos).slice(0, 2)).filter(l => l.length))
}

/**
 * A base word whose translation table lists the target word: the base word, the table's place among its tables
 * ([sense]), the target word's place in the table's list ([place]), whether its part of speech is the lemma's own
 * ([exact]), and how many translations the base word has in all ([size]: a common word has many).
 */
export type Listed = { w: string; sense: number; place: number; exact: boolean; size: number }

/**
 * The base words whose tables list the target word first (or second in their first sense's table): those of the
 * earliest sense first, then the earliest place, the most translated word (danke before merci), the same part of
 * speech. At most 3.
 */
export function listedBy(candidates: Listed[]): string[] {
  const out: string[] = []
  const strong = candidates.filter(c => c.place === 0 || (c.place === 1 && c.sense === 0))
  strong.sort((a, b) => a.sense - b.sense || a.place - b.place || b.size - a.size || Number(b.exact) - Number(a.exact))
  for (const c of strong) if (out.length < 3 && !out.includes(c.w)) out.push(c.w)
  return out
}

/** Whether a meaning reads like a translation (a few words), not a definition ("männliche oder weibliche Person"). */
const translationLike = (g: string) => g.split(/,\s+/)[0].split(' ').length <= 3

/**
 * The meaning part of a gloss: German Wiktionary explains a foreign word, then translates it ("größeres fließendes
 * Gewässer; Fluss" → Fluss): after the last semicolon, when that is shorter than what comes before.
 */
export function meaningPart(g: string): string {
  const parts = g.split(/;\s+/)
  if (parts.length < 2) return g
  const words = (s: string) => s.trim().split(/\s+/).length
  const last = parts.at(-1)!.trim()
  return words(parts[0]) >= 2 && words(last) < words(parts[0]) && translationLike(last) ? last : g
}

/** What a lemma's meaning in a base can come from (see the file's head). */
export type Candidates = {
  /** Its glosses in the base's own Wiktionary. */
  direct?: string[]
  /** Its own translation tables (the target's Wiktionary). */
  own?: Table[]
  /** The base's words whose tables list it (the base's Wiktionary). */
  listed?: Listed[]
  /** Through English: its English glosses' tables. */
  pivot?: () => { words: string[]; confirmed: boolean }
  /** The base's words met with it in the tables of a third language's Wiktionary. */
  together?: Together[]
}

/** Where a meaning was found, finer than [Via]: which table, or which kind of pivot. */
export type Kind = 'direct' | 'confirmed' | 'own' | 'listed' | 'together' | 'sense' | 'definition'
/** The kinds that go through a third language. */
export type PivotKind = Extract<Kind, 'confirmed' | 'together' | 'sense'>
const VIA: Record<Kind, Via> = { direct: 'direct', confirmed: 'pivot', own: 'translations', listed: 'translations', together: 'pivot', sense: 'pivot', definition: 'direct' }

/**
 * Every meaning a lemma of [pos] can have in [base], the best evidence first: the base's Wiktionary's gloss when it
 * reads like a translation; the table of the lemma's English gloss that lists the lemma (the sense shown, and a table
 * that says so); a table between the two languages (the target's own, then the base's turned round); a third
 * language's table that lists it with base words; the table the English gloss's other words pick; the base's gloss even
 * when it is a definition.
 */
export function meaningsOf(c: Candidates, base: string, pos = ''): { gloss: string[]; kind: Kind }[] {
  // each sense's words not said by an earlier one ("sprechen", "sprechen, reden, sagen" → sprechen; reden, sagen)
  const said = new Set<string>()
  const direct = (c.direct ?? [])
    .map(g => cleanGloss(meaningPart(g), base).split(/,\s+/).filter(w => w && !said.has(w.toLowerCase()) && !!said.add(w.toLowerCase())).join(', '))
    .filter(Boolean)
    .slice(0, 3)
  const through = c.pivot?.() ?? { words: [], confirmed: false }
  const found: [Kind, string[]][] = [
    ['direct', direct.length && translationLike(direct[0]) ? direct : []],
    ['confirmed', through.confirmed ? through.words : []],
    ['own', c.own ? tableWords(c.own, base, pos) : []],
    ['listed', c.listed ? listedBy(c.listed) : []],
    ['together', c.together ? together(c.together, pos) : []],
    ['sense', through.confirmed ? [] : through.words],
    ['definition', direct],
  ]
  return found.filter(([, g]) => g.length).map(([kind, gloss]) => ({ gloss, kind }))
}

/** A lemma of [pos]'s meaning in [base]: the first of [meaningsOf]. */
export function chooseMeaning(c: Candidates, base: string, pos = ''): Meaning | undefined {
  const m = meaningsOf(c, base, pos)[0]
  return m && { gloss: m.gloss, via: VIA[m.kind] }
}
