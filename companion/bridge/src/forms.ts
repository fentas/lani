// Word forms (companion/README.md, "Word forms"): a word the learner has, with its forms and the grammar book's page
// each belongs to (GET /forms), for the app's review to ask a form of a word once its meaning is known ("Jaz ___ na
// klop." → sedem) and for the word card's table. The forms come from Wiktionary's dictionary (companion/lexicon/
// <language>.json: its grammar strings, "dative/locative singular", read back into slots: dat.sg, loc.sg), or from a hand-
// checked table of the forms book (companion/lexicon/<language>.forms.json) for the everyday irregular verbs, where
// Wiktionary merges homographs (biti, to be, and biti, to beat) or has odd rows. The forms book also pairs verbs (sesti,
// sit down: sedeti, be sitting), which the word lookup marks. A form's real sentences come from the content (the scenes'
// dialogs, the stories, the readings, the grammar pages' examples, the packs' examples), where that form stands as that
// word ([LineIndex]).
import { existsSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import type { Log } from './config'
import { englishWords, enScore, glossCoverage, normalizeForm, PREPOSITIONS, wordsOf, type Aspect, type Lexicon, type Partner, type WordGender } from './lexicon'

// --- slots -------------------------------------------------------------------------------------------------------

const PERSONS = ['1sg', '2sg', '3sg', '1pl', '2pl', '3pl', '1du', '2du', '3du']
/** A verb's slots, in the order the table shows them: present, negative present, future, the l-form (past), imperative. */
export const VERB_KEYS = [
  ...PERSONS.map(p => `pres.${p}`),
  ...PERSONS.map(p => `neg.${p}`),
  ...PERSONS.map(p => `fut.${p}`),
  'past.m.sg', 'past.f.sg', 'past.n.sg', 'past.m.pl', 'past.f.pl', 'past.m.du', 'past.f.du',
  'imp.2sg', 'imp.2pl', 'imp.1pl', 'imp.1du', 'imp.2du',
]
const CASE_KEYS = ['nom', 'gen', 'dat', 'acc', 'loc', 'ins'] as const
/** A noun's slots: the six cases in the singular, then the plural, then the dual (the vocative is left out). */
export const NOUN_KEYS = ['sg', 'pl', 'du'].flatMap(n => CASE_KEYS.map(c => `${c}.${n}`))
/** An adjective's slots: the nominative's genders and numbers, and the comparative. */
export const ADJ_KEYS = ['adj.m.sg', 'adj.f.sg', 'adj.n.sg', 'adj.m.pl', 'adj.f.pl', 'cmp.m.sg']
export const SLOT_KEYS = [...VERB_KEYS, ...NOUN_KEYS, ...ADJ_KEYS]
const KEYS_OF: Record<string, string[]> = { verb: VERB_KEYS, noun: NOUN_KEYS, adj: ADJ_KEYS }
export const FORM_POS = ['verb', 'noun', 'adj'] as const
export type FormPos = (typeof FORM_POS)[number]

const CASE_NAMES: Record<string, (typeof CASE_KEYS)[number]> = { nominative: 'nom', genitive: 'gen', dative: 'dat', accusative: 'acc', locative: 'loc', instrumental: 'ins' }
const NUMBER_NAMES: Record<string, string> = { singular: 'sg', plural: 'pl', dual: 'du' }
const PERSON_NAMES: Record<string, string> = { 'first-person': '1', 'second-person': '2', 'third-person': '3' }
const GENDER_NAMES: Record<string, string> = { masculine: 'm', feminine: 'f', neuter: 'n' }
/** Grammar words that are no reading of a form of their own ("verbal noun", "alternative form", a participle). */
const OTHER = new Set(['infinitive', 'supine', 'participle', 'converb', 'verbal', 'noun', 'alternative', 'form', 'possessive', 'adjective', 'demonym', 'diminutive', 'relational', 'adverb', 'superlative', 'historic', 'imperfect', 'conditional', 'subjunctive', 'gerund', 'augmentative'])
/** Wiktionary's table rows that are a grammar word, not a form ("genitive" as lep's accusative masculine singular). */
const NOT_FORMS = new Set([...Object.keys(CASE_NAMES), 'vocative', ...Object.keys(NUMBER_NAMES), ...Object.keys(GENDER_NAMES), 'animate', 'inanimate', 'definite', 'indefinite'])

/** One reading of a grammar string, word by word: "second/third-person dual present" → persons 2, 3; dual; present. */
type Reading = { persons: string[]; cases: string[]; genders: string[]; numbers: string[]; words: Set<string>; vocative: boolean }

function readingOf(text: string): Reading | null {
  const r: Reading = { persons: [], cases: [], genders: [], numbers: [], words: new Set(), vocative: false }
  let rest = text.trim()
  if (rest.startsWith('any case')) {
    r.cases.push(...CASE_KEYS)
    rest = rest.replace(/^any case,?\s*/, '')
  }
  for (const word of rest.split(/\s+/).filter(Boolean)) {
    const alts = word.split('/')
    // "second/third-person": the suffix belongs to each
    const person = alts.at(-1)!.endsWith('-person')
    for (const a0 of alts) {
      const a = person && !a0.endsWith('-person') ? `${a0}-person` : a0
      if (CASE_NAMES[a]) r.cases.push(CASE_NAMES[a])
      else if (a === 'vocative') r.vocative = true
      else if (NUMBER_NAMES[a]) r.numbers.push(NUMBER_NAMES[a])
      else if (PERSON_NAMES[a]) r.persons.push(PERSON_NAMES[a])
      else if (GENDER_NAMES[a]) r.genders.push(GENDER_NAMES[a])
      else r.words.add(a)
    }
  }
  return r
}

/**
 * The slots one of Wiktionary's grammar strings of a form of a [pos] says ("feminine singular l-participle; masculine
 * dual l-participle; neuter plural l-participle" → past.f.sg, past.m.du), and whether it also has a reading no slot
 * stands for (an infinitive, a supine, a participle, a definite form, a case of a gender the adjective's slots leave out):
 * a line with such a form can't say which one it is.
 */
export function slotsOf(pos: string, grammar: string | null): { keys: string[]; other: boolean } {
  const keys: string[] = []
  let other = false
  if (!grammar) return { keys, other: true }
  for (const text of grammar.split('; ')) {
    const r = readingOf(text)
    if (!r) continue
    const got = readingSlots(pos, r)
    if (got.length) keys.push(...got)
    else if (!(r.vocative && !r.cases.length)) other = true
  }
  return { keys: [...new Set(keys)], other }
}

function readingSlots(pos: string, r: Reading): string[] {
  const w = r.words
  const junk = [...w].some(x => OTHER.has(x))
  if (pos === 'verb') {
    if (w.has('l-participle')) {
      if (r.persons.length || r.cases.length) return []
      return r.genders.flatMap(g => r.numbers.flatMap(n => {
        if (n === 'sg') return [`past.${g}.sg`]
        if (n === 'pl') return g === 'n' ? [] : [`past.${g}.pl`] // the neuter plural is the feminine singular
        return [g === 'm' ? 'past.m.du' : 'past.f.du']
      }))
    }
    if (junk || r.cases.length || !r.persons.length || !r.numbers.length) return []
    const each = (prefix: string, ok: (p: string) => boolean = () => true) =>
      r.persons.flatMap(p => r.numbers.map(n => `${p}${n}`)).filter(ok).map(p => `${prefix}.${p}`)
    if (w.has('imperative')) {
      if (r.genders.length) return []
      return each('imp', p => ['2sg', '2pl', '1pl', '1du', '2du'].includes(p))
    }
    if (r.genders.length) return []
    if (w.has('future')) return each('fut')
    if (w.has('present')) return each(w.has('negative') ? 'neg' : 'pres')
    return []
  }
  if (pos === 'noun') {
    if (junk || r.persons.length || [...w].some(x => x !== 'animate' && x !== 'inanimate') || !r.cases.length || !r.numbers.length) return []
    return r.numbers.flatMap(n => r.cases.map(c => `${c}.${n}`))
  }
  if (pos === 'adj') {
    if (w.has('comparative')) {
      const plain = !junk && [...w].every(x => x === 'comparative' || x === 'indefinite')
      const fits = (have: string[], want: string) => !have.length || have.includes(want)
      return plain && fits(r.cases, 'nom') && fits(r.genders, 'm') && fits(r.numbers, 'sg') ? ['cmp.m.sg'] : []
    }
    if (junk || w.has('definite') || [...w].some(x => x !== 'indefinite') || !r.cases.includes('nom')) return []
    const out = r.genders.flatMap(g => r.numbers.flatMap(n => (n === 'du' ? [] : [`adj.${g}.${n}`])))
    return out.filter(k => ADJ_KEYS.includes(k))
  }
  return []
}

/** "first-person singular present", "locative plural", "feminine singular l-participle": a slot's grammar, written out. */
export function grammarOfSlot(key: string): string {
  const [a, b, c] = key.split('.')
  const person: Record<string, string> = { '1': 'first-person', '2': 'second-person', '3': 'third-person' }
  const number: Record<string, string> = { sg: 'singular', pl: 'plural', du: 'dual' }
  const gender: Record<string, string> = { m: 'masculine', f: 'feminine', n: 'neuter' }
  const kase = Object.fromEntries(Object.entries(CASE_NAMES).map(([k, v]) => [v, k]))
  switch (a) {
    case 'pres':
    case 'neg':
    case 'fut':
    case 'imp': {
      const tense = { pres: 'present', neg: 'negative present', fut: 'future', imp: 'imperative' }[a]
      return `${person[b[0]]} ${number[b.slice(1)]} ${tense}`
    }
    case 'past':
      return c === 'du' && b === 'f' ? 'feminine/neuter dual l-participle' : `${gender[b]} ${number[c]} l-participle`
    case 'adj':
      return `nominative ${gender[b]} ${number[c]}`
    case 'cmp':
      return 'nominative masculine singular comparative'
    default:
      return `${kase[a]} ${number[b]}`
  }
}

/** Where a slot's form counts: its grammar book page (the rule a wrong one breaks) and the pages it needs introduced too. */
export type SlotPage = { page: string; needs: string[] }

const CASE_PAGES: Record<string, string> = { nom: 'imenovalnik', gen: 'rodilnik', dat: 'dajalnik', acc: 'tozilnik', loc: 'mestnik', ins: 'orodnik' }

/**
 * The page of slot [key] of [lemma] ("sesti", "pres.1sg" → glagoli-sedanjik): the present's endings, biti's own page,
 * imeti's and iti's; the dual's page for a dual, needing the tense's or the case's; the plural's needed by a plural case.
 */
export function pageOf(lemma: string, key: string): SlotPage | null {
  const [a, b, c] = key.split('.')
  const own = lemma === 'biti' ? 'biti' : lemma === 'imeti' || lemma === 'iti' ? 'imeti-iti' : 'glagoli-sedanjik'
  const dual = (page: string): SlotPage => ({ page: 'dvojina', needs: [page] })
  switch (a) {
    case 'pres':
      return b.endsWith('du') ? dual(own) : { page: own, needs: [] }
    case 'neg':
      return b.endsWith('du') ? dual('zanikanje') : { page: 'zanikanje', needs: [own] }
    case 'fut':
      return b.endsWith('du') ? dual('prihodnjik') : { page: 'prihodnjik', needs: [] }
    case 'past':
      return c === 'du' ? dual('pretekli-cas') : { page: 'pretekli-cas', needs: [] }
    case 'imp':
      return b.endsWith('du') ? dual('velelnik') : { page: 'velelnik', needs: [] }
    case 'adj':
      return { page: 'pridevniki-ujemanje', needs: c === 'pl' ? ['mnozina'] : [] }
    case 'cmp':
      return { page: 'primernik', needs: [] }
  }
  const page = CASE_PAGES[a]
  if (!page) return null
  if (b === 'sg') return { page, needs: [] }
  if (b === 'pl') return a === 'nom' ? { page: 'mnozina', needs: [] } : { page, needs: ['mnozina'] }
  return a === 'nom' ? { page: 'dvojina', needs: [] } : dual(page)
}

// --- the forms book: hand-checked tables and the verb pairs -------------------------------------------------------

const lemmaText = z.string().trim().min(1).max(80)
const formWord = z.string().trim().min(1).max(40).regex(/^\p{L}+$/u, 'one word, letters only')
const gloss = z.array(z.string().trim().min(1).max(120)).min(1).max(3)
const aspect = z.enum(['pf', 'impf', 'both'])

/**
 * A hand-checked table: a word's forms by slot (one, or several standard ones), the forms also accepted (spoken ones:
 * bojo for bodo) and its gloss. It replaces Wiktionary's forms of that word and part of speech.
 */
const tableEntry = z
  .object({
    lemma: lemmaText,
    pos: z.enum(FORM_POS),
    aspect: aspect.optional(),
    gloss: gloss.optional(),
    forms: z.partialRecord(z.enum(SLOT_KEYS as [string, ...string[]]), z.union([formWord, z.array(formWord).min(1).max(4)])),
    also: z.partialRecord(z.enum(SLOT_KEYS as [string, ...string[]]), z.array(formWord).min(1).max(4)).optional(),
    note: z.string().trim().max(300).optional(),
  })
  .superRefine((t, ctx) => {
    const keys = [...Object.keys(t.forms), ...Object.keys(t.also ?? {})]
    if (!Object.keys(t.forms).length) ctx.addIssue({ code: 'custom', path: ['forms'], message: 'at least one form' })
    for (const k of keys) if (!KEYS_OF[t.pos].includes(k)) ctx.addIssue({ code: 'custom', path: ['forms', k], message: `"${k}" isn't a slot of a ${t.pos}` })
    for (const k of Object.keys(t.also ?? {})) if (!t.forms[k]) ctx.addIssue({ code: 'custom', path: ['also', k], message: `"${k}" is also accepted, but has no form of its own` })
  })
/** Two verbs that pair: [pf] once, [impf] going on; [kind] position (a movement and the state after it) or aspect. */
const partnerEntry = z.object({
  pf: lemmaText,
  impf: lemmaText,
  kind: z.enum(['position', 'aspect']),
  /** A gloss for one of them where the dictionary's says more (stati: to stand, not to cost). */
  gloss: z.record(lemmaText, gloss).optional(),
})
/** A word's forms not to ask (all of them, without [forms]): ambiguous, or wrong in Wiktionary's table. */
const leaveOutEntry = z.object({ lemma: lemmaText, pos: z.enum(FORM_POS).optional(), forms: z.array(formWord).min(1).optional(), why: z.string().max(300).optional() })

export type FormsTable = z.infer<typeof tableEntry>
export type PartnerPair = z.infer<typeof partnerEntry>
export type LeaveOut = z.infer<typeof leaveOutEntry>
export type FormsBookFile = { tables: FormsTable[]; partners: PartnerPair[]; leaveOut: LeaveOut[] }

/** The forms book's entries, each checked on its own: a wrong one is reported and left out, never the whole file. */
export function validateFormsBook(input: unknown): FormsBookFile & { errors: string[] } {
  const file = z
    .object({
      meta: z.looseObject({ language: z.string(), reviewed: z.boolean() }),
      tables: z.array(z.unknown()).default([]),
      partners: z.array(z.unknown()).default([]),
      leave_out: z.array(z.unknown()).default([]),
    })
    .safeParse(input)
  if (!file.success) return { tables: [], partners: [], leaveOut: [], errors: [z.prettifyError(file.error)] }
  const errors: string[] = []
  const each = <T>(list: unknown[], schema: z.ZodType<T>, what: string): T[] =>
    list.flatMap((e, i) => {
      const r = schema.safeParse(e)
      if (r.success) return [r.data]
      errors.push(`${what}[${i}] (${(e as { lemma?: string; pf?: string })?.lemma ?? (e as { pf?: string })?.pf ?? '?'}): ${z.prettifyError(r.error)}`)
      return []
    })
  return {
    tables: each(file.data.tables, tableEntry, 'tables'),
    partners: each(file.data.partners, partnerEntry, 'partners'),
    leaveOut: each(file.data.leave_out, leaveOutEntry, 'leave_out'),
    errors,
  }
}

/** The forms book of a language (companion/lexicon/<language>.forms.json), read again when it changes. */
export class FormsBook {
  private mtime = -1
  private book: FormsBookFile = { tables: [], partners: [], leaveOut: [] }

  constructor(private readonly o: { dir: string; language: string; log?: Log }) {}

  private get path() {
    return join(this.o.dir, `${this.o.language}.forms.json`)
  }

  /** The book as it is on disk now; empty when there is none. */
  current(): FormsBookFile {
    const path = this.path
    const mtime = existsSync(path) ? statSync(path).mtimeMs : 0
    if (mtime === this.mtime) return this.book
    this.mtime = mtime
    this.book = { tables: [], partners: [], leaveOut: [] }
    if (!mtime) return this.book
    try {
      const r = validateFormsBook(JSON.parse(readFileSync(path, 'utf8')))
      if (r.errors.length) this.o.log?.(`forms book ${path}: left out:\n${r.errors.join('\n')}`)
      this.book = { tables: r.tables, partners: r.partners, leaveOut: r.leaveOut }
    } catch (e) {
      this.o.log?.(`forms book ${path} unreadable: ${(e as Error).message}`)
    }
    return this.book
  }

  private norm = (s: string) => normalizeForm(s, this.o.language)

  /** The hand-checked table of [lemma] of [pos]. */
  table(lemma: string, pos: string): FormsTable | undefined {
    const key = this.norm(lemma)
    return this.current().tables.find(t => t.pos === pos && this.norm(t.lemma) === key)
  }

  /** The parts of speech [lemma] has a table of (a word Wiktionary lacks). */
  tablePos(lemma: string): FormPos[] {
    const key = this.norm(lemma)
    return this.current().tables.filter(t => this.norm(t.lemma) === key).map(t => t.pos)
  }

  /** What the book leaves out of [lemma] of [pos]: every form (all), or these. */
  leftOut(lemma: string, pos: string): { all: boolean; forms: Set<string> } {
    const key = this.norm(lemma)
    const hits = this.current().leaveOut.filter(l => this.norm(l.lemma) === key && (!l.pos || l.pos === pos))
    return { all: hits.some(l => !l.forms), forms: new Set(hits.flatMap(l => (l.forms ?? []).map(this.norm))) }
  }

  /** A verb's aspect: its table's, else its place in the pairs (both when it is once perfective, once imperfective). */
  aspect(lemma: string): Aspect | undefined {
    const key = this.norm(lemma)
    const own = this.current().tables.find(t => t.pos === 'verb' && this.norm(t.lemma) === key)?.aspect
    if (own) return own
    const roles = new Set(this.current().partners.flatMap(p => [...(this.norm(p.pf) === key ? ['pf'] : []), ...(this.norm(p.impf) === key ? ['impf'] : [])]))
    return roles.size > 1 ? 'both' : ([...roles][0] as Aspect | undefined)
  }

  /** [lemma]'s pair (the first the book lists it in): the other verb, its role and the kind of pair. */
  pair(lemma: string): { other: string; aspect: Aspect; kind: PartnerPair['kind']; gloss?: string[] } | undefined {
    const key = this.norm(lemma)
    const p = this.current().partners.find(p => this.norm(p.pf) === key || this.norm(p.impf) === key)
    if (!p) return undefined
    const other = this.norm(p.pf) === key ? p.impf : p.pf
    const glossOf = p.gloss ? Object.entries(p.gloss).find(([l]) => this.norm(l) === this.norm(other))?.[1] : undefined
    return { other, aspect: other === p.pf ? 'pf' : 'impf', kind: p.kind, ...(glossOf ? { gloss: glossOf } : {}) }
  }
}

/**
 * A verb's aspect and partner for the word lookup and GET /forms: none for a word the book doesn't pair. The partner's
 * gloss is the book's, else its table's, else the dictionary's.
 */
export function partnerOf(book: FormsBook, dict: Lexicon, lemma: string): { aspect: Aspect; partner: Partner } | undefined {
  const pair = book.pair(lemma)
  if (!pair) return undefined
  const mine = book.aspect(lemma) ?? (pair.aspect === 'pf' ? 'impf' : 'pf')
  const gloss = pair.gloss ?? book.table(pair.other, 'verb')?.gloss ?? dict.lemmasOf(pair.other).find(l => l.pos === 'verb')?.gloss ?? []
  return { aspect: mine, partner: { lemma: pair.other, aspect: book.aspect(pair.other) ?? pair.aspect, kind: pair.kind, gloss } }
}

// --- the content's lines -------------------------------------------------------------------------------------------

/** A line of the content: in the village's language ([text]), its translations, where it is from, and its words. */
export type ContentLine = { text: string; en: string; de?: string; it?: string; from: string; tokens: string[] }

/** A text of the content in the village's [language] and its translations, as the sources give them. */
type Said = Partial<Record<string, unknown>>

/** The longest line asked about, in characters and in words. */
const MAX_CHARS = 120
const MAX_WORDS = 14

/** Lines in [language] from the content: each scene, story, reading, grammar page and pack gives its own (sources). */
export type LineSources = {
  language: string
  scenes: { id: string; language?: string; dialogs?: DialogLike[]; variants?: DialogLike[] }[]
  stories: { id: string; language?: string; levels?: Partial<Record<string, { lines: LineLike[] }>>; chapters?: { levels: Partial<Record<string, { lines: LineLike[] }>> }[] }[]
  readings: { id: string; lines?: { text: Said }[]; steps?: Said[]; intro?: Said }[]
  grammar: { id: string; language: string; examples: Said[] }[]
  packs: { id: string; language?: string; words: Said[] }[]
}
type LineLike = Said & { choices?: (Said & { ok?: boolean; reply?: Said })[] }
type DialogLike = { lines: LineLike[] }

/**
 * Every line of the content in [s.language] worth asking a form in: what the people say in the scenes' dialogs and the
 * stories (their lines, the learner's right choices and the replies to them; never a wrong choice: it holds the wrong
 * form), the readings' lines (and a recipe's steps), the grammar pages' examples and the packs' examples. A line with a
 * placeholder ({n}) or a gap, without an English translation, longer than [MAX_CHARS] or [MAX_WORDS] words, is left
 * out; each line once.
 */
export function contentLines(s: LineSources): ContentLine[] {
  const lang = s.language
  const out: ContentLine[] = []
  const seen = new Set<string>()
  /** [t]'s text in the village's language and its translations; a pack word's are its example's ([prefix] "example_"). */
  const add = (t: Said | undefined, from: string, prefix = '') => {
    if (!t) return
    const said = (l: string) => {
      const v = t[prefix + l]
      return typeof v === 'string' && v.trim() ? v.trim() : undefined
    }
    const text = said(lang)
    const en = said('en')
    // a line of a poem that goes on from the one before ("prost bo vsak,"): not a sentence of its own
    if (!text || !en || /[{}]|__/.test(text) || text.length > MAX_CHARS || /^[^\p{L}]*\p{Ll}/u.test(text)) return
    const words = wordsOf(text)
    if (!words.length || words.length > MAX_WORDS) return
    const k = normalizeForm(text, lang).replace(/\s+/g, ' ')
    if (seen.has(k)) return
    seen.add(k)
    const de = lang !== 'de' ? said('de') : undefined
    const it = lang !== 'it' ? said('it') : undefined
    out.push({ text, en, ...(de ? { de } : {}), ...(it ? { it } : {}), from, tokens: words.map(w => normalizeForm(w, lang)) })
  }
  const dialogLines = (lines: LineLike[], from: string) => {
    for (const l of lines) {
      add(l, from)
      for (const c of l.choices ?? []) {
        if (!c.ok) continue
        add(c, from)
        add(c.reply, from)
      }
    }
  }
  for (const sc of s.scenes) {
    if ((sc.language ?? 'sl') !== lang) continue
    for (const d of [...(sc.dialogs ?? []), ...(sc.variants ?? [])]) dialogLines(d.lines, `scene:${sc.id}`)
  }
  for (const st of s.stories) {
    if ((st.language ?? 'sl') !== lang) continue
    const evenings = st.chapters?.map(c => c.levels) ?? (st.levels ? [st.levels] : [])
    for (const levels of evenings) for (const v of Object.values(levels)) if (v) dialogLines(v.lines, `story:${st.id}`)
  }
  for (const r of s.readings) {
    add(r.intro, `reading:${r.id}`)
    for (const l of r.lines ?? []) add(l.text, `reading:${r.id}`)
    for (const st of r.steps ?? []) add(st, `reading:${r.id}`)
  }
  for (const g of s.grammar) if (g.language === lang) for (const e of g.examples) add(e, `grammar:${g.id}`)
  for (const p of s.packs) {
    if ((p.language ?? 'sl') !== lang) continue
    for (const w of p.words) add(w, `pack:${p.id}`, 'example_')
  }
  return out
}

/** The content's lines by the words in them (normalized): the lines a form stands in. */
export class LineIndex {
  private readonly byToken = new Map<string, ContentLine[]>()

  constructor(readonly lines: ContentLine[]) {
    for (const l of lines) {
      for (const t of new Set(l.tokens)) {
        const list = this.byToken.get(t) ?? []
        list.push(l)
        this.byToken.set(t, list)
      }
    }
  }

  with(form: string): ContentLine[] {
    return this.byToken.get(form) ?? []
  }
}

// --- a word's forms ------------------------------------------------------------------------------------------------

/** A sentence of the content a form stands in: in the village's language (keyed by it, "sl"), its translations, where from. */
export type FormLine = Record<string, string> & { en: string; form: string; from: string }

export type Slot = {
  key: string
  /** The standard form or forms: the first is shown as the answer, all are right. */
  forms: string[]
  /** Accepted too, never shown as the answer: a spoken form (bojo for bodo). */
  also?: string[]
  grammar: string
  page: string
  needs?: string[]
  lines?: FormLine[]
}

export type LemmaForms = {
  lemma: string
  pos: FormPos
  gloss: string[]
  gender: WordGender | null
  aspect?: Aspect
  partner?: Partner
  table: 'wiktionary' | 'checked'
  slots: Slot[]
}

/** At most this many lines a slot gets. */
const LINES_PER_SLOT = 3

/** Words before a noun that make it dual: dva, dve, oba, obe (and their cases' forms that say two: dveh, dvema). */
const DUAL_BEFORE = new Set(['dva', 'dve', 'oba', 'obe', 'dveh', 'dvema', 'obeh', 'obema'])
/** The forms of biti that make an l-form the future's (bom šel) or the conditional's (bi šel). */
const FUTURE_OR_CONDITIONAL = new Set(['bom', 'boš', 'bo', 'bova', 'bosta', 'bomo', 'boste', 'bodo', 'bojo', 'bi'])
/** The forms of biti that say the number of an l-form beside them ("je sedla": singular, "sta sedla": dual). */
const AUX_NUMBER: Record<string, string> = Object.fromEntries([
  ...['sem', 'si', 'je', 'bom', 'boš', 'bo', 'nisem', 'nisi', 'ni'].map(w => [w, 'sg']),
  ...['sva', 'sta', 'bova', 'bosta', 'nisva', 'nista'].map(w => [w, 'du']),
  ...['smo', 'ste', 'so', 'bomo', 'boste', 'bodo', 'bojo', 'nismo', 'niste', 'niso'].map(w => [w, 'pl']),
])

export type FormsContext = {
  dict: Lexicon
  book: FormsBook
  /** The grammar book's pages: a slot of a page it lacks isn't served. */
  pages: Set<string>
  lines: LineIndex
  language: string
}

/** One word's paradigm: its slots' forms, the forms also accepted, and each form's slots and whether it reads otherwise too. */
type Paradigm = { slots: Map<string, string[]>; also: Map<string, string[]>; ofForm: Map<string, { keys: Set<string>; other: boolean }>; all: Set<string> }

function paradigmOf(c: FormsContext, index: number | null, lemma: string, pos: FormPos, table: FormsTable | undefined): Paradigm {
  const norm = (s: string) => normalizeForm(s, c.language)
  const left = c.book.leftOut(lemma, pos)
  const p: Paradigm = { slots: new Map(), also: new Map(), ofForm: new Map(), all: new Set([norm(lemma)]) }
  const put = (form: string, key: string | null, into: Map<string, string[]>) => {
    const f = norm(form)
    if (left.forms.has(f)) return
    p.all.add(f)
    const o = p.ofForm.get(f) ?? { keys: new Set<string>(), other: false }
    if (key) {
      o.keys.add(key)
      const list = into.get(key) ?? []
      if (!list.includes(f)) list.push(f)
      into.set(key, list)
    } else o.other = true
    p.ofForm.set(f, o)
  }
  if (table) {
    for (const [key, v] of Object.entries(table.forms)) for (const f of typeof v === 'string' ? [v] : (v ?? [])) put(f, key, p.slots)
    for (const [key, v] of Object.entries(table.also ?? {})) for (const f of v ?? []) put(f, key, p.also)
    return p
  }
  const own = norm(lemma)
  for (const [form, grammar] of c.dict.paradigm(index, lemma, pos)) {
    if (NOT_FORMS.has(form)) continue
    const { keys, other } = slotsOf(pos, grammar)
    if (other) put(form, null, p.slots)
    for (const k of keys) put(form, k, p.slots)
  }
  // the masculine singular of an adjective is the word itself (lepi is its definite form, and the plural)
  const m = p.slots.get('adj.m.sg')
  if (m?.includes(own)) p.slots.set('adj.m.sg', [own])
  for (const list of p.slots.values()) list.sort((a, b) => Number(b === own) - Number(a === own) || a.localeCompare(b, c.language))
  return p
}

/** The other words a form of [lemma] is a form of too: their glosses; null when one is biti (its form is biti's: je). */
function othersOf(c: FormsContext, lemma: string, pos: string, form: string): string[][] | null {
  const others = c.dict.readings(form, 'en').filter(r => !(normalizeForm(r.lemma, c.language) === normalizeForm(lemma, c.language) && r.pos === pos))
  // a form of biti too (je: jesti's, and the auxiliary of "je pojedel", ate): biti's, whatever the translation says
  if (others.some(r => normalizeForm(r.lemma, c.language) === 'biti' && r.pos === 'verb')) return null
  return others.map(r => r.gloss)
}

/**
 * Whether a word of [gloss] is what its form means in a line translated [en]: no other word the form is of ([others],
 * their glosses) fits the translation as well, by the glosses' words, then by how much of a gloss it has.
 */
function meansThis(gloss: string[], others: string[][], en: Set<string>): boolean {
  if (!others.length) return true
  const fit = (g: string[]): [number, number] => [enScore(g, en), glossCoverage(g, en)]
  const mine = fit(gloss)
  if (mine[0] <= 0) return false
  return others.every(g => {
    const o = fit(g)
    return mine[0] > o[0] || (mine[0] === o[0] && mine[1] > o[1])
  })
}

/**
 * The slots [form] stands for in [line] (as a form of a word with paradigm [p]): its slots narrowed by the word before
 * it (a preposition's cases, dva/dve's dual) and, for an l-form, by biti's form beside it (je: singular); empty when the
 * form reads as something no slot stands for, or the slots left are of more than one page.
 */
function slotsInLine(c: FormsContext, lemma: string, gloss: string[], p: Paradigm, form: string, line: ContentLine): string[] {
  lemma = normalizeForm(lemma, c.language)
  const of = p.ofForm.get(form)
  if (!of || of.other || !of.keys.size) return []
  let keys = [...of.keys]
  const i = line.tokens.indexOf(form)
  const before = i > 0 ? line.tokens[i - 1] : undefined
  const caseOf = (k: string): string | null => {
    const [a] = k.split('.')
    return (CASE_KEYS as readonly string[]).includes(a) ? a : a === 'adj' || a === 'cmp' ? 'nom' : null
  }
  const cases: string[] | undefined = before ? PREPOSITIONS[c.language]?.[before]?.map(x => CASE_NAMES[x] as string).filter(Boolean) : undefined
  if (cases) keys = keys.filter(k => caseOf(k) === null || cases.includes(caseOf(k)!))
  if (before && DUAL_BEFORE.has(before)) keys = keys.filter(k => caseOf(k) === null || k.endsWith('.du'))
  // a present and an imperative alike (sedi: sits, sit!): the translation says which
  if (keys.some(k => k.startsWith('imp.')) && keys.some(k => k.startsWith('pres.'))) {
    const mood = moodInEnglish(line.en, gloss, keys.some(k => /^imp\.1/.test(k)))
    keys = keys.filter(k => !mood || k.startsWith(`${mood}.`))
  }
  if (keys.some(k => k.startsWith('past.'))) {
    // an l-form in the future (bom šel) or the conditional (bi šel) is another rule's: not the past's line
    if (line.tokens.some(t => FUTURE_OR_CONDITIONAL.has(t))) keys = keys.filter(k => !k.startsWith('past.'))
    const numbers = new Set(line.tokens.flatMap(t => (AUX_NUMBER[t] ? [AUX_NUMBER[t]] : [])))
    if (numbers.size === 1) {
      const n = [...numbers][0]
      keys = keys.filter(k => !k.startsWith('past.') || k.endsWith(`.${n}`))
    }
  }
  const sigs = new Set(keys.map(k => {
    const pg = pageOf(lemma, k)
    return pg ? `${pg.page}|${[...pg.needs].sort().join(',')}` : '?'
  }))
  return sigs.size === 1 && !sigs.has('?') ? keys : []
}

/** English words a clause may start with before an imperative: "Then sit down", "Please come", "Now see". */
const BEFORE_IMPERATIVE = new Set(['then', 'and', 'now', 'please', 'so', 'just', 'also', 'oh', 'ok', 'okay', 'well', 'quick', 'quickly', 'here', 'there', 'yes', 'no', 'come', 'go', 'do', 'always', 'never'])
const SUBJECTS = new Set(['i', 'you', 'we', 'they'])

/**
 * Whether a line's translation [en] says the verb glossed [gloss] ("to sit") in the present ("The king sits", "is
 * sitting", "we sit") or as an imperative: starting a clause ("Then sit down by the fire", "Sit closer, lad"), or after
 * "let's" for the first person's ([first]: "Let's go"). Null when it says neither, or both.
 */
export function moodInEnglish(en: string, gloss: string[], first = false): 'pres' | 'imp' | null {
  const verbs = new Set(gloss.flatMap(g => /^to\s+([a-z]+)/i.exec(g.trim())?.[1]?.toLowerCase() ?? []))
  if (!verbs.size) return null
  // the words, with "|" where a clause ends
  const words = en.toLowerCase().replace(/[’]/g, "'").replace(/[.,!?;:—–…"«»„“]+/g, ' | ').match(/[a-z']+|\|/g) ?? []
  let pres = false
  let imp = false
  words.forEach((w, i) => {
    const before = words[i - 1] ?? '|'
    for (const v of verbs) {
      if (w === `${v}s` || w === `${v}es` || (v.endsWith('y') && w === `${v.slice(0, -1)}ies`)) pres = true
      else if (w.endsWith('ing') && w.startsWith(v.replace(/e$/, '')) && ['is', 'are', 'am', "'s", "'re"].some(b => before === b || before.endsWith(b))) pres = true
      else if (w === v) {
        if (SUBJECTS.has(before)) pres = true
        else if (first ? ["let's", 'lets'].includes(before) || (before === 'us' && words[i - 2] === 'let') : before === '|' || (BEFORE_IMPERATIVE.has(before) && (words[i - 2] ?? '|') === '|')) imp = true
      }
    }
  })
  return pres === imp ? null : pres ? 'pres' : 'imp'
}

/** The lines of the content [form] (of the word [lemma], paradigm [p]) stands in as that word, by the slots it is there. */
function linesOf(c: FormsContext, lemma: string, pos: string, gloss: string[], p: Paradigm): Map<string, FormLine[]> {
  const out = new Map<string, FormLine[]>()
  for (const form of p.ofForm.keys()) {
    const candidates = c.lines
      .with(form)
      .filter(l => l.tokens.filter(t => t === form).length === 1 && !l.tokens.some(t => t !== form && p.all.has(t)))
      .sort((a, b) => a.text.length - b.text.length || a.text.localeCompare(b.text))
    if (!candidates.length) continue
    const others = othersOf(c, lemma, pos, form)
    if (!others) continue
    for (const l of candidates) {
      const keys = slotsInLine(c, lemma, gloss, p, form, l)
      if (!keys.length || !meansThis(gloss, others, englishWords(l.en))) continue
      for (const k of keys) {
        const list = out.get(k) ?? []
        if (list.length >= LINES_PER_SLOT) continue
        list.push({ [c.language]: l.text, en: l.en, ...(l.de ? { de: l.de } : {}), ...(l.it ? { it: l.it } : {}), form, from: l.from } as FormLine)
        out.set(k, list)
      }
    }
  }
  return out
}

/**
 * A word's forms, by the dictionary's lemmas written [word] (a noun, a verb, an adjective: one entry each), with the
 * pages they count on and the content's lines for each; none when the dictionary and the forms book don't know it.
 */
export function formsOf(c: FormsContext, word: string): LemmaForms[] {
  const lemmas = c.dict.lemmasOf(word).filter(l => (FORM_POS as readonly string[]).includes(l.pos))
  // a word the forms book has a table of, and Wiktionary hasn't
  for (const pos of c.book.tablePos(word)) {
    if (lemmas.some(l => l.pos === pos)) continue
    const t = c.book.table(word, pos)!
    lemmas.push({ index: null, lemma: t.lemma, pos, gloss: t.gloss ?? [], gender: null })
  }
  const out: LemmaForms[] = []
  for (const l of lemmas) {
    const pos = l.pos as FormPos
    if (c.book.leftOut(l.lemma, pos).all) continue
    const table = c.book.table(l.lemma, pos)
    const gloss = table?.gloss ?? l.gloss
    const p = paradigmOf(c, l.index, l.lemma, pos, table)
    const lines = linesOf(c, l.lemma, pos, gloss, p)
    const slots: Slot[] = []
    for (const key of KEYS_OF[pos]) {
      const forms = p.slots.get(key)
      if (!forms?.length) continue
      const pg = pageOf(normalizeForm(l.lemma, c.language), key)
      if (!pg || !c.pages.has(pg.page) || pg.needs.some(n => !c.pages.has(n))) continue
      const also = p.also.get(key)?.filter(f => !forms.includes(f))
      const ls = lines.get(key)
      slots.push({ key, forms, ...(also?.length ? { also } : {}), grammar: grammarOfSlot(key), page: pg.page, ...(pg.needs.length ? { needs: pg.needs } : {}), ...(ls?.length ? { lines: ls } : {}) })
    }
    if (!slots.length) continue
    const pair = pos === 'verb' ? partnerOf(c.book, c.dict, l.lemma) : undefined
    const aspect = pos === 'verb' ? (table?.aspect ?? pair?.aspect ?? c.book.aspect(l.lemma)) : undefined
    out.push({
      lemma: table?.lemma ?? l.lemma, pos, gloss, gender: l.gender,
      ...(aspect ? { aspect } : {}), ...(pair ? { partner: pair.partner } : {}),
      table: table ? 'checked' : 'wiktionary', slots,
    })
  }
  return out
}
