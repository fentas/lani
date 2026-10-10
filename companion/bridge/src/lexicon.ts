// Word lookup (companion/README.md, "Word lookup"): what a word tapped in one of the app's dialogs means, and the
// review item it becomes. The sources, in the order they rank: the word packs (glosses written for the learner), the
// tutor's glosses (publish_gloss, <data>/app/lexicon.json), the village's people, a hand-written supplement
// (companion/lexicon/<language>.extra.json) and English Wiktionary as kaikki.org extracts it
// (companion/lexicon/<language>.json, made by companion/bin/lexicon-build; CC BY-SA 4.0, so every answer that uses it
// carries the attribution).
import { existsSync, readFileSync, renameSync, statSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import type { Log } from './config'
import type { SrItem } from './learner'
import type { Meaning, Via } from './lexicon-meanings'
import { exampleOf, itemId, meaningLangOf, meaningOf, normWord, vocabularyWords, wordOf, type LoadedPack } from './packs'

export const ATTRIBUTION = 'Wiktionary (CC BY-SA 4.0) via kaikki.org'

/**
 * A form as the lexicon compares it: lowercase, NFC, a typographic apostrophe as ' (l’acqua → l'acqua). Slovene drops
 * Wiktionary's tonal accents (potọ́ka → potoka) but keeps the caron (č, š, ž), and writes its pronunciation letters as
 * spelled (bȋł → bil, šə̏ł → šel). Italian drops the stress marks Wiktionary writes inside a word (casìna → casina) and
 * keeps the accent on the last letter, the one Italian writes (città, perché). Another language keeps its accents.
 */
export function normalizeForm(s: string, language: string): string {
  const lower = s.trim().toLocaleLowerCase(language)
  if (language === 'sl') return lower.normalize('NFD').replace(/(?!̌)\p{M}/gu, '').normalize('NFC').replace(/ł/g, 'l').replace(/ə/g, 'e')
  const apostrophe = lower.replace(/[’ʼ]/g, "'")
  if (language === 'it') return apostrophe.normalize('NFD').replace(/\p{M}(?=[\p{M}']*\p{L})/gu, '').normalize('NFC')
  return apostrophe.normalize('NFC')
}

/** The words of a line: its runs of letters. */
export const wordsOf = (line: string): string[] => line.match(/[\p{L}\p{M}]+/gu) ?? []

/** "Na svidenje" → "na-svidenje", "čebela" → "cebela": the id part of a word's review item. */
export function wordSlug(lemma: string): string {
  const s = lemma.toLowerCase().normalize('NFD').replace(/\p{M}/gu, '').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
  return s.slice(0, 60).replace(/-+$/, '') || 'word'
}

/** The review item of a word met outside the packs: vocab_word_gozd. */
export const wordItemId = (lemma: string) => `vocab_word_${wordSlug(lemma)}`

export type WordGender = 'm' | 'f' | 'n'
export type LexiconSource = 'pack' | 'wiktionary' | 'extra' | 'tutor' | 'village'

/** companion/lexicon/<language>.json, as companion/bin/lexicon-build writes it. */
export type LexiconFile = {
  meta: {
    language: string
    source: string
    licence: string
    licence_url?: string
    attribution?: string
    url: string
    built: string
    counts: Record<string, number>
    /** The languages the lemmas have meanings in (English: their own glosses), with their coverage and how found. */
    bases?: Record<string, { lemmas: number; share: number; via: Partial<Record<Via, number>>; [more: string]: unknown }>
    /** The Wiktionaries the meanings come from. */
    sources?: { edition: string; name: string; urls: string[]; indexed: string }[]
    meanings_built?: string
  }
  /** The grammar of forms ("dative/locative singular; nominative dual"), referred to by index. */
  grammar: string[]
  lemmas: LexiconLemma[]
  /** A normalized form → its readings: [lemma index] or [lemma index, grammar index]. */
  forms: Record<string, number[][]>
}

/**
 * A lemma: its English glosses (an English dictionary's: English definitions), a noun's gender, and its meanings in
 * Lani's other languages ("de": {"gloss": ["Haus"], "via": "direct"}; lexicon-meanings.ts), when found.
 */
export type LexiconLemma = { lemma: string; pos: string; gloss: string[]; gender?: WordGender; meanings?: Record<string, Meaning> }

/** An entry of companion/lexicon/<language>.extra.json: a word the content uses that Wiktionary lacks, written by hand. */
const extraEntry = z.object({
  lemma: z.string().trim().min(1).max(80),
  pos: z.string().trim().min(1).max(24),
  gloss: z.array(z.string().trim().min(1).max(120)).min(1).max(3),
  gender: z.enum(['m', 'f', 'n']).optional(),
  /** The forms met in the content → the grammar of each (null: nothing worth saying, or Wiktionary's). */
  forms: z.record(z.string().min(1).max(40), z.string().max(160).nullable()).refine(f => Object.keys(f).length > 0, 'at least one form'),
  /**
   * The gloss replaces Wiktionary's for every form of the same lemma and part of speech, with Wiktionary's grammar
   * (babica: grandmother, where Wiktionary starts with midwife).
   */
  override: z.boolean().optional(),
})
type ExtraEntry = z.infer<typeof extraEntry>

/** The supplement's entries, each checked on its own: a wrong one is reported and left out, never the whole file. */
export function validateExtra(input: unknown): { entries: ExtraEntry[]; errors: string[] } {
  const file = z.object({ meta: z.looseObject({ language: z.string(), reviewed: z.boolean(), note: z.string() }), entries: z.array(z.unknown()) }).safeParse(input)
  if (!file.success) return { entries: [], errors: [z.prettifyError(file.error)] }
  const entries: ExtraEntry[] = []
  const errors: string[] = []
  file.data.entries.forEach((e, i) => {
    const r = extraEntry.safeParse(e)
    if (r.success) entries.push(r.data)
    else errors.push(`entries[${i}] (${(e as { lemma?: string })?.lemma ?? '?'}): ${z.prettifyError(r.error)}`)
  })
  return { entries, errors }
}

const LANG_CODE = /^[a-z]{2,3}$/

/**
 * What the tutor explains with publish_gloss: a form, its lemma, 1-3 short glosses and the language they are in
 * ([gloss_lang]; the learner's base when not given); [language]: the word's (another town's on a visit), the village's
 * when not given.
 */
export const glossIn = z.object({
  form: z.string().trim().min(1).max(40),
  lemma: z.string().trim().min(1).max(80),
  pos: z.string().trim().min(1).max(24).optional(),
  gloss: z.array(z.string().trim().min(1).max(120)).min(1).max(3),
  grammar: z.string().trim().min(1).max(80).optional(),
  note: z.string().trim().min(1).max(300).optional(),
  gloss_lang: z.string().trim().regex(LANG_CODE, 'a language code, e.g. "sl"').optional(),
  language: z.string().trim().regex(LANG_CODE, 'a language code, e.g. "it"').optional(),
})
/** A gloss the tutor wrote; one kept before glosses had a language ([gloss_lang] missing) is in the learner's base. */
export type TutorGloss = Omit<z.infer<typeof glossIn>, 'language'> & { key: string; at: string }

/** One meaning a form can have. */
export type Reading = {
  lemma: string
  pos: string
  gloss: string[]
  gender: WordGender | null
  grammar: string | null
  source: LexiconSource
  note?: string
  /** Wiktionary's reading of the form, with the supplement's gloss of its lemma (babica: grandmother, not midwife). */
  wiktionary?: true
  /** The language [gloss] is in when it isn't English: the base's (the lemma's meaning in it), or the tutor's. */
  glossLang?: string
  /** How a meaning in the base was found (lexicon-meanings.ts); "claude": the tutor wrote it (machine-written). */
  via?: Via
  /** The English gloss a base-language one stands for. */
  en?: string[]
  /** No meaning in the base language asked for: [gloss] is English. */
  fallback?: true
}

/** The tutor's glosses kept: the newest this many. */
const MAX_GLOSSES = 5000

/**
 * The dictionary of one language: Wiktionary's (if it was built for the language), the supplement and the tutor's
 * glosses. The files are read on first use; the tutor's again when it changes.
 */
export class Lexicon {
  private loaded = false
  private file: LexiconFile | null = null
  /** Where Wiktionary's dictionary came from: the repository's companion/lexicon, or a download (lexicon-download.ts). */
  private origin: { from: 'bundled' | 'download' | 'cache'; version: string } | null = null
  private extra = new Map<string, Reading[]>()
  /** The supplement's lemmas that override Wiktionary's gloss, by "lemma|pos". */
  private overrides = new Map<string, ExtraEntry>()
  private tutor: { mtime: number; entries: TutorGloss[] } = { mtime: -1, entries: [] }
  /** How many lemmas have a meaning in a base, counted once per base. */
  private counted = new Map<string, number>()
  /** The dictionary's lemmas' meanings by "lemma|pos", made when the supplement first asks. */
  private byLemma: Map<string, Record<string, Meaning>> | null = null

  constructor(
    private readonly o: { dir: string; language: string; tutorFile: string; log?: Log; base?: string },
  ) {}

  get language() {
    return this.o.language
  }

  /**
   * The learner's base language: what a reading is explained in unless a lookup asks for another (English: the
   * dictionary's own glosses), and what the tutor's glosses from before they had a language are in.
   */
  get base() {
    return this.o.base ?? 'en'
  }

  private load() {
    if (this.loaded) return
    this.loaded = true
    const { dir, language, log } = this.o
    const main = join(dir, `${language}.json`)
    try {
      if (existsSync(main)) {
        this.file = JSON.parse(readFileSync(main, 'utf8'))
        this.origin = { from: 'bundled', version: this.file?.meta?.built ?? '' }
      }
    } catch (e) {
      log?.(`lexicon ${main} unreadable: ${(e as Error).message}`)
    }
    const extra = join(dir, `${language}.extra.json`)
    try {
      if (!existsSync(extra)) return
      const r = validateExtra(JSON.parse(readFileSync(extra, 'utf8')))
      if (r.errors.length) log?.(`lexicon supplement ${extra}: left out:\n${r.errors.join('\n')}`)
      for (const e of r.entries) {
        if (e.override) this.overrides.set(`${normalizeForm(e.lemma, language)}|${e.pos}`, e)
        for (const [form, grammar] of Object.entries(e.forms)) {
          const key = normalizeForm(form, language)
          const list = this.extra.get(key) ?? []
          list.push({ lemma: e.lemma, pos: e.pos, gloss: e.gloss, gender: e.gender ?? null, grammar, source: 'extra' })
          this.extra.set(key, list)
        }
      }
    } catch (e) {
      log?.(`lexicon supplement ${extra} skipped: ${(e as Error).message}`)
    }
  }

  /** Whether Wiktionary's dictionary was built for this language. */
  get wiktionary(): boolean {
    this.load()
    return !!this.file
  }

  /** Whether the repository has this language's dictionary (companion/lexicon/<language>.json): nothing to download. */
  get bundled(): boolean {
    this.load()
    return this.origin?.from === 'bundled'
  }

  /** Serves a downloaded dictionary from now on; false when the repository has one of the language (it wins). */
  use(file: LexiconFile, from: 'download' | 'cache', version: string): boolean {
    this.load()
    if (this.origin?.from === 'bundled') return false
    if (file.meta?.language && file.meta.language !== this.o.language) throw new Error(`a ${file.meta.language} dictionary, not ${this.o.language}`)
    this.file = file
    this.origin = { from, version }
    this.counted.clear()
    this.byLemma = null
    this.byIndex = null
    return true
  }

  /** Each lemma's forms in Wiktionary's dictionary, by lemma index, made at the first ask ([paradigm]). */
  private byIndex: Map<number, [string, string | null][]> | null = null
  /** The supplement's forms by "lemma|pos", made at the first ask ([paradigm]). */
  private extraByLemma: Map<string, [string, string | null][]> | null = null

  /**
   * The dictionary's lemmas written [lemma] (normalized), each with its index, part of speech, English glosses (the
   * supplement's when it overrides them: babica) and gender; none when Wiktionary's dictionary has none. The supplement's
   * lemmas Wiktionary lacks come after, without an index.
   */
  lemmasOf(lemma: string): { index: number | null; lemma: string; pos: string; gloss: string[]; gender: WordGender | null }[] {
    this.load()
    const lang = this.o.language
    const key = normalizeForm(lemma, lang)
    const f = this.file
    const out: { index: number | null; lemma: string; pos: string; gloss: string[]; gender: WordGender | null }[] = []
    if (f && Object.hasOwn(f.forms, key)) {
      for (const [i] of f.forms[key]) {
        const l = f.lemmas[i]
        if (!l || normalizeForm(l.lemma, lang) !== key || out.some(o => o.index === i)) continue
        const own = this.overrides.get(`${key}|${l.pos}`)
        out.push({ index: i, lemma: l.lemma, pos: l.pos, gloss: own?.gloss ?? l.gloss, gender: own?.gender ?? l.gender ?? null })
      }
    }
    for (const r of this.extra.get(key) ?? []) {
      if (normalizeForm(r.lemma, lang) !== key || out.some(o => o.pos === r.pos)) continue
      out.push({ index: null, lemma: r.lemma, pos: r.pos, gloss: r.gloss, gender: r.gender })
    }
    return out
  }

  /**
   * The forms of a lemma with the grammar of each ("sedem", "first-person singular present"): Wiktionary's of lemma
   * [index], else the supplement's of [lemma] and [pos]. Normalized as the lexicon compares them.
   */
  paradigm(index: number | null, lemma: string, pos: string): [string, string | null][] {
    this.load()
    const f = this.file
    if (index !== null && f) {
      if (!this.byIndex) {
        this.byIndex = new Map()
        for (const [form, rs] of Object.entries(f.forms)) {
          for (const [i, g] of rs) {
            const list = this.byIndex.get(i) ?? []
            list.push([form, g === undefined ? null : (f.grammar[g] ?? null)])
            this.byIndex.set(i, list)
          }
        }
      }
      return this.byIndex.get(index) ?? []
    }
    if (!this.extraByLemma) {
      this.extraByLemma = new Map()
      for (const [form, rs] of this.extra) {
        for (const r of rs) {
          const key = `${normalizeForm(r.lemma, this.o.language)}|${r.pos}`
          this.extraByLemma.set(key, [...(this.extraByLemma.get(key) ?? []), [form, r.grammar]])
        }
      }
    }
    return this.extraByLemma.get(`${normalizeForm(lemma, this.o.language)}|${pos}`) ?? []
  }

  /** The meanings of the dictionary's lemma [lemma] of [pos] (a supplement's entry of a word Wiktionary has too). */
  private lemmaMeanings(lemma: string, pos: string): Record<string, Meaning> | undefined {
    if (!this.file) return undefined
    if (!this.byLemma) {
      this.byLemma = new Map()
      for (const l of this.file.lemmas) {
        const key = `${normalizeForm(l.lemma, this.o.language)}|${l.pos}`
        if (l.meanings && !this.byLemma.has(key)) this.byLemma.set(key, l.meanings)
      }
    }
    return this.byLemma.get(`${normalizeForm(lemma, this.o.language)}|${pos}`)
  }

  /** How many of the dictionary's lemmas have a meaning in [base] (English: every one). */
  meaningsIn(base: string): number {
    this.load()
    if (!this.file) return 0
    if (base === 'en') return this.file.lemmas.length
    let n = this.counted.get(base)
    if (n === undefined) {
      n = this.file.lemmas.reduce((k, l) => k + (l.meanings?.[base]?.gloss?.length ? 1 : 0), 0)
      this.counted.set(base, n)
    }
    return n
  }

  /**
   * What GET /lexicon/status shows: the language, where its dictionary came from, how big it is, how many of its lemmas
   * have a meaning in the learner's base, and the bases it has meanings in.
   */
  status(): { language: string; wiktionary: boolean; from: string | null; version: string | null; lemmas: number; forms: number; base: string; base_meanings: number; bases: string[] } {
    this.load()
    return {
      language: this.o.language,
      wiktionary: !!this.file,
      from: this.origin?.from ?? null,
      version: this.origin?.version ?? null,
      lemmas: this.file?.lemmas.length ?? 0,
      forms: this.file ? (this.file.meta?.counts?.forms ?? Object.keys(this.file.forms).length) : 0,
      base: this.base,
      base_meanings: this.meaningsIn(this.base),
      bases: this.file ? Object.keys(this.file.meta?.bases ?? { en: {} }) : [],
    }
  }

  /** The tutor's newest gloss of [lemma] ([pos], when it names one) in [base]: it fills, or corrects, the dictionary's. */
  private tutorMeaning(lemma: string, pos: string, base: string): TutorGloss | undefined {
    const lang = this.o.language
    const key = normalizeForm(lemma, lang)
    return this.glosses().findLast(g => (g.gloss_lang ?? this.base) === base && normalizeForm(g.lemma, lang) === key && (!g.pos || g.pos === pos))
  }

  /**
   * [r] (the dictionary's, in English) explained in [base]: the tutor's gloss of its lemma in the base ([used] then
   * keeps it from being an entry of its own), else the lemma's meaning in the base ([meanings]; [pivot]: also one found
   * through English), else the English, marked [Reading.fallback].
   */
  private meant(r: Reading, meanings: Record<string, Meaning> | undefined, base: string, used: Set<TutorGloss>, pivot = true): Reading {
    if (base === 'en') return r
    const t = this.tutorMeaning(r.lemma, r.pos, base)
    if (t) {
      used.add(t)
      return { ...r, gloss: t.gloss, glossLang: base, via: 'claude', en: r.gloss, ...(t.note && !r.note ? { note: t.note } : {}) }
    }
    const m = meanings?.[base]
    if (m?.gloss?.length && (pivot || m.via !== 'pivot')) return { ...r, gloss: m.gloss, glossLang: base, via: m.via, en: r.gloss }
    return { ...r, fallback: true }
  }

  /** The tutor's glosses, newest last. */
  glosses(): TutorGloss[] {
    const path = this.o.tutorFile
    const mtime = existsSync(path) ? statSync(path).mtimeMs : 0
    if (mtime !== this.tutor.mtime) {
      let entries: TutorGloss[] = []
      try {
        if (mtime) entries = JSON.parse(readFileSync(path, 'utf8')).entries ?? []
      } catch (e) {
        this.o.log?.(`tutor glosses ${path} unreadable: ${(e as Error).message}`)
      }
      this.tutor = { mtime, entries }
    }
    return this.tutor.entries
  }

  /**
   * Keeps the tutor's gloss of a form, in its language (the learner's base when not given), replacing an earlier one of
   * the same form, lemma and language.
   */
  publish(g: z.infer<typeof glossIn>): TutorGloss {
    const lang = this.o.language
    const key = normalizeForm(g.form, lang)
    const { language: _, ...rest } = g
    const entry: TutorGloss = { ...rest, gloss_lang: g.gloss_lang ?? this.base, key, at: new Date().toISOString() }
    const same = (e: TutorGloss) => e.key === key && normalizeForm(e.lemma, lang) === normalizeForm(g.lemma, lang) && (e.gloss_lang ?? this.base) === entry.gloss_lang
    const entries = [...this.glosses().filter(e => !same(e)), entry].slice(-MAX_GLOSSES)
    const path = this.o.tutorFile
    writeFileSync(`${path}.tmp`, JSON.stringify({ schema: 'lani.glosses/v0', language: lang, entries }, null, 2) + '\n')
    renameSync(`${path}.tmp`, path)
    return entry
  }

  /**
   * The readings of a normalized form, explained in [base] (the learner's by default): the tutor's (by form or lemma),
   * the supplement's, then Wiktionary's. A lemma the supplement overrides keeps Wiktionary's grammar with the
   * supplement's gloss, and the lemma's meaning in the base unless it came through the English the supplement replaced;
   * the supplement's entry of a lemma Wiktionary has too (hvala) takes that lemma's meaning.
   * A reading with no meaning in the base keeps its English and says so (fallback); the tutor's gloss of the lemma in
   * the base fills it, and is no entry of its own then.
   */
  readings(form: string, base = this.base): Reading[] {
    this.load()
    const lang = this.o.language
    const used = new Set<TutorGloss>()
    // the supplement's entry of a word Wiktionary has too (hvala, the interjection) takes that lemma's meanings
    const extra = (this.extra.get(form) ?? []).map(r => this.meant(r, base === 'en' ? undefined : this.lemmaMeanings(r.lemma, r.pos), base, used))
    const wiktionary: Reading[] = []
    const f = this.file
    if (f && Object.hasOwn(f.forms, form)) {
      for (const [i, g] of f.forms[form]) {
        const l = f.lemmas[i]
        if (!l) continue
        const grammar = g === undefined ? null : (f.grammar[g] ?? null)
        const own = this.overrides.get(`${normalizeForm(l.lemma, lang)}|${l.pos}`)
        const r: Reading = own
          ? { lemma: own.lemma, pos: own.pos, gloss: own.gloss, gender: own.gender ?? l.gender ?? null, grammar, source: 'extra', wiktionary: true }
          : { lemma: l.lemma, pos: l.pos, gloss: l.gloss, gender: l.gender ?? null, grammar, source: 'wiktionary' }
        wiktionary.push(this.meant(r, l.meanings, base, used, !own))
      }
    }
    // the tutor's other glosses: in the base, or in English (the fallback); not in a third language
    const readable = (g: TutorGloss) => [base, 'en'].includes(g.gloss_lang ?? this.base)
    const tutor: Reading[] = this.glosses()
      .filter(g => (g.key === form || normalizeForm(g.lemma, lang) === form) && !used.has(g) && readable(g))
      .reverse()
      .map(g => ({
        lemma: g.lemma, pos: g.pos ?? 'word', gloss: g.gloss, gender: null, grammar: g.key === form ? (g.grammar ?? null) : null, source: 'tutor',
        ...(g.note ? { note: g.note } : {}), via: 'claude', ...((g.gloss_lang ?? this.base) !== 'en' ? { glossLang: g.gloss_lang ?? this.base } : {}),
      }))
    return [...tutor, ...extra, ...wiktionary]
  }
}

// --- the lookup -------------------------------------------------------------------------------------------------

export type LookupEntry = {
  lemma: string
  pos: string
  gloss: string[]
  /**
   * The language [gloss] is in: the learner's base (a pack word's meaning in it, the dictionary's, the tutor's), else
   * English, with [fallback].
   */
  gloss_lang: string
  /**
   * How a meaning in the base was found: Wiktionary explains the word in the base ("direct"), a translation table
   * between the two languages ("translations"), through English ("pivot"), or the tutor wrote it ("claude": the app
   * flags it machine-written).
   */
  gloss_via?: Via
  /** The English gloss a base-language one stands for. */
  gloss_en?: string[]
  /** The base (not English) has no meaning of this word: [gloss] is the English (the app says so). */
  fallback?: true
  grammar: string | null
  gender: WordGender | null
  source: LexiconSource
  /** The review item the word is (or becomes): a pack word's own, vocab_word_<lemma> otherwise; none for a name. */
  item_id: string | null
  known: boolean
  example: { sl: string; en: string } | null
  emoji: string | null
  /** The tutor's usage note, when it gave one. */
  note?: string
  /** A verb's aspect, when the forms book knows it (lexicon/<language>.forms.json, forms.ts): pf, impf or both. */
  aspect?: Aspect
  /**
   * The verb it pairs with (sesti, sit down: sedeti, be sitting), from the forms book: the app shows the partner as a line
   * of this entry, not as an entry of its own, with what tells them apart ([kind]).
   */
  partner?: Partner
  /** The entry the line means, of two partners both found: its translation fits this one better ("Sit down" → sesti). */
  here?: true
}

/** A verb's aspect: perfective (once, done), imperfective (going on, again and again), or both (iti). */
export type Aspect = 'pf' | 'impf' | 'both'

/**
 * A verb's partner: its [lemma], [aspect] and English [gloss]; [kind] "position" for a movement and the state it ends in
 * (sesti, sit down: sedeti, be sitting), "aspect" for the same act once or going on (kupiti, kupovati).
 */
export type Partner = { lemma: string; aspect: Aspect; kind: 'position' | 'aspect'; gloss: string[] }

export type LookupResult = { word: string; entries: LookupEntry[]; attribution: string | null }

/** Someone a name in a dialog can be: a villager, or the learner. */
export type Person = { name: string; given: string; gloss: string; emoji: string | null }

export type LookupSources = {
  lexicon: Lexicon
  /** The language the meanings are in: the learner's base (on a visit, the visit's). */
  base: string
  packs: LoadedPack[]
  people: Person[]
  items: Record<string, SrItem>
  /** A verb's aspect and partner (forms.ts); none for a word the forms book doesn't pair. */
  partners?: (lemma: string) => { aspect: Aspect; partner: Partner } | undefined
}

const CASES = ['nominative', 'genitive', 'dative', 'accusative', 'locative', 'instrumental', 'vocative']

/** The cases Slovene prepositions take: the word before a tapped one narrows its reading ("v gozdu": locative). */
export const PREPOSITIONS: Record<string, Record<string, string[]>> = {
  sl: Object.fromEntries(
    Object.entries({
      'v na po': ['accusative', 'locative'],
      'o pri ob': ['locative'],
      'k h proti kljub': ['dative'],
      'iz od do brez blizu zaradi okoli okrog mimo poleg sredi zraven izpod izza': ['genitive'],
      'z s': ['instrumental', 'genitive'],
      'med nad pod pred': ['instrumental', 'accusative'],
      za: ['instrumental', 'accusative', 'genitive'],
      'skozi čez zoper': ['accusative'],
    }).flatMap(([words, cases]) => words.split(' ').map(w => [w, cases])),
  ),
}

/**
 * [grammar] ("dative/locative singular; nominative dual") with only the readings of [cases] ("locative singular"); as
 * it is when none fits. At most three readings.
 */
function narrow(grammar: string | null, cases: string[] | undefined): string | null {
  if (!grammar) return null
  let groups = grammar.split('; ')
  if (cases) {
    const kept = groups.flatMap(g => {
      const [head, ...rest] = g.split(' ')
      const cs = head.split('/')
      if (!cs.every(c => CASES.includes(c))) return [g]
      const keep = cs.filter(c => cases.includes(c))
      return keep.length ? [[keep.join('/'), ...rest].join(' ')] : []
    })
    if (kept.length) groups = kept
  }
  return groups.slice(0, 3).join('; ')
}

const STOP = new Set(['the', 'a', 'an', 'to', 'of', 'or', 'and', 'in', 'on', 'at', 'by', 'for', 'with', 'from', 'as', 'used', 'form', 'someone', 'something', 'etc'])

/** English words a gloss has in another form: "is" is glossed "to be", "us" is "we". */
const ENGLISH_BASE: Record<string, string> = {
  is: 'be', are: 'be', am: 'be', was: 'be', were: 'be', been: 'be', has: 'have', had: 'have', goes: 'go', went: 'go',
  gone: 'go', does: 'do', did: 'do', done: 'do', made: 'make', said: 'say', saw: 'see', seen: 'see', came: 'come',
  took: 'take', taken: 'take', gave: 'give', given: 'give', got: 'get', knew: 'know', known: 'know', ate: 'eat',
  eaten: 'eat', found: 'find', thought: 'think', told: 'tell', brought: 'bring', bought: 'buy', ran: 'run', sat: 'sit',
  stood: 'stand', wrote: 'write', written: 'write', spoke: 'speak', me: 'i', my: 'i', us: 'we', our: 'we', him: 'he',
  his: 'he', her: 'she', them: 'they', their: 'they', your: 'you', children: 'child', men: 'man', women: 'woman',
  people: 'person', feet: 'foot', teeth: 'tooth', mice: 'mouse', geese: 'goose',
}

/** The words of a line's translation, with their base forms ("is" → also "be", "says" → also "say", "sitting" → "sit"). */
export function englishWords(en: string | undefined): Set<string> {
  const words = en?.toLowerCase().match(/\p{L}+/gu) ?? []
  return new Set(words.flatMap(w => (ENGLISH_BASE[w] ? [w, ENGLISH_BASE[w]] : w.length > 3 && /[^s]s$/.test(w) ? [w, w.slice(0, -1)] : w.length > 5 && w.endsWith('ing') ? [w, ...ingBase(w)] : [w])))
}

/** The verb an -ing form is of: "sitting" → sit, "making" → mak and make, "going" → go. */
function ingBase(w: string): string[] {
  const stem = w.slice(0, -3)
  return /([bdfgklmnprtz])\1$/.test(stem) ? [stem.slice(0, -1)] : [stem, `${stem}e`]
}

/**
 * How many of an entry's gloss words the line's translation has ("village" in "…the village…"; the translation may be
 * in the learner's base, and so may the gloss).
 */
export function enScore(gloss: string[], en: Set<string>): number {
  if (!en.size) return 0
  const words = new Set(gloss.join(' ').toLowerCase().match(/\p{L}+/gu) ?? [])
  let n = 0
  for (const g of words) {
    if (!glossWord(g)) continue
    if (inEnglish(g, en)) n++
  }
  return n
}

/** A word of a gloss that says something: not "to", "the", "of" … */
const glossWord = (g: string) => !((g.length < 2 && g !== 'i') || STOP.has(g))
/** Whether the translation's words [en] have gloss word [g] (or a word it starts, or that starts it: "village", "villages"). */
const inEnglish = (g: string, en: Set<string>) => [...en].some(t => t === g || (g.length >= 4 && t.startsWith(g)) || (t.length >= 4 && g.startsWith(t)))

/**
 * How much of its best gloss the translation [en] has, 0 to 1: "to sit" all of it in "The king sits at the table", "to
 * sit down" half. Tells two entries apart that [enScore] scores alike (sedeti, not sesti, sits there).
 */
export function glossCoverage(gloss: string[], en: Set<string>): number {
  if (!en.size) return 0
  let best = 0
  for (const g of gloss) {
    const words = [...new Set(g.toLowerCase().match(/\p{L}+/gu) ?? [])].filter(glossWord)
    if (words.length) best = Math.max(best, words.filter(w => inEnglish(w, en)).length / words.length)
  }
  return best
}

/** Whether a tapped word names [p]: their name or given name, or (capitalized) a declined given name (Micke, Tonetu). */
function names(raw: string, form: string, p: Person, language: string): boolean {
  const given = normalizeForm(p.given, language)
  if (form === given || form === normalizeForm(p.name, language)) return true
  const stem = /[aeiou]$/.test(given) ? given.slice(0, -1) : given
  // Slovene declension endings: Micke, Janezu, Tonetom
  return language === 'sl' && /^\p{Lu}/u.test(raw) && stem.length >= 3 && form.startsWith(stem) && /^(?:et|j)?(?:a|e|i|o|u|om|em)$/.test(form.slice(stem.length))
}

/** A word or an expression as it is compared: normalized, without punctuation ("Lahko noč!" → "lahko noč"). */
function phraseKey(s: string, language: string): string {
  return normalizeForm(s, language).replace(/[^\p{L}\p{M}'’ ]+/gu, ' ').replace(/\s+/g, ' ').trim()
}

/** The article a pack word may start with, by language: Italian "il bosco", German "der Wald", English "the forest", a verb's "to roll". */
const ARTICLES: Record<string, RegExp> = {
  it: /^(?:(?:il|lo|la|i|gli|le|un|uno|una) +|(?:l|un)['’])/,
  de: /^(?:der|die|das|ein|eine) +/,
  en: /^(?:the|an?|to) +/,
}

/** A pack word as it is compared: as phraseKey, its article left out ("il bosco" → "bosco", "der Wald" → "wald"). */
function packKey(word: string, language: string): string {
  const w = phraseKey(word, language)
  return ARTICLES[language] ? w.replace(ARTICLES[language], '') : w
}

/** The languages that elide a word before an apostrophe: l'acqua is the article and acqua. */
const ELIDES = new Set(['it', 'fr', 'ca'])

/** The longest expression a tapped word is looked for in: "srečno novo leto", "kaj pa ti". */
const PHRASE_WORDS = 4

/**
 * The expressions of a line around the words at [positions]: every run of 2-4 of its words with one of them, the
 * longest first ("Ravno prav." → "ravno prav").
 */
function phrasesAround(tokens: string[], positions: number[]): string[] {
  const out = new Set<string>()
  for (let n = PHRASE_WORDS; n >= 2; n--) {
    for (const i of positions) {
      for (let a = Math.max(0, i - n + 1); a <= i && a + n <= tokens.length; a++) out.add(tokens.slice(a, a + n).join(' '))
    }
  }
  return [...out]
}

/**
 * What [word] means where it was tapped. The order:
 * 1. an expression of the line the word is part of ("prav" in "Ravno prav." → ravno prav, just in time);
 * 2. the entries whose glosses the line's translation [en] has ("Vidim vas." / "I see you": the pronoun first);
 * 3. the pack words, then the tutor's glosses, then the rest; a pack word the tapped word is only a form of after the
 *    pack words it is ("debel": thick, before the genitive plural of deblo), and, without a translation, after the word
 *    itself when the supplement or the tutor wrote it ("ravno": the adverb, just, before raven, flat; "dolgo");
 * 4. the village's people; then the word itself, or what was written for it (the supplement), before Wiktionary's
 *    readings of it as a form of another word ("prav": the adverb before a genitive plural of pravo); Wiktionary's names
 *    last ("lepa": nice, not a cat's name), or first when the word is capitalized inside the line ("pozdravi Samo").
 * The word before it narrows a noun's case ("v gozdu"). At most five entries, one per lemma and part of speech.
 */
export function lookup(word: string, at: { line?: string; en?: string }, s: LookupSources): LookupResult {
  const lang = s.lexicon.language
  const raw = word.replace(/^[^\p{L}]+|[^\p{L}\p{M}]+$/gu, '')
  let form = normalizeForm(raw, lang)
  // an elided word before an apostrophe ("l'acqua", "dell'acqua", "un'amica"): the word after it, unless the whole is
  // an entry of its own
  if (ELIDES.has(lang) && /'\p{L}/u.test(form) && !s.lexicon.readings(form, s.base).length) form = form.slice(form.lastIndexOf("'") + 1)
  if (!form) return { word: raw, entries: [], attribution: null }

  const tokens = at.line ? wordsOf(at.line).map(t => normalizeForm(t, lang)) : []
  const positions = tokens.flatMap((t, k) => (t === form ? [k] : []))
  const i = positions[0] ?? -1
  // the expressions of the line around the word ("na svidenje", "dober dan"), and the case the word before asks for
  const phrases = phrasesAround(tokens, positions)
  const cases = i > 0 ? PREPOSITIONS[lang]?.[tokens[i - 1]] : undefined

  const direct = s.lexicon.readings(form, s.base)
  const known = vocabularyWords(s.items)
  const isKnown = (id: string | null, lemma: string) => (!!id && !!s.items[id]) || known.has(normWord(lemma))
  const entries: LookupEntry[] = []
  // the entries that are Wiktionary's readings (the ranking puts its forms of other words after the rest), and those
  // that show some of Wiktionary's text (their answer carries the attribution)
  const fromWiktionary = new WeakSet<LookupEntry>()
  const attributed = new WeakSet<LookupEntry>()
  const isWiktionary = (r: Reading) => r.source === 'wiktionary' || !!r.wiktionary
  const fromReading = (r: Reading, grammar: string | null): LookupEntry => {
    const id = wordItemId(r.lemma)
    const e: LookupEntry = {
      lemma: r.lemma, pos: r.pos, gloss: r.gloss, gloss_lang: r.glossLang ?? 'en',
      ...(r.via ? { gloss_via: r.via } : {}), ...(r.en ? { gloss_en: r.en } : {}), grammar, gender: r.gender, source: r.source,
      item_id: id, known: isKnown(id, r.lemma), example: null, emoji: null, ...(r.note ? { note: r.note } : {}),
    }
    if (isWiktionary(r)) {
      fromWiktionary.add(e)
      attributed.add(e)
    }
    return e
  }
  // an expression is one entry whatever its part of speech ("Dober tek!": the pack's, the supplement's phrase and
  // Wiktionary's intj)
  const keyOf = (e: { lemma: string; pos: string }) => {
    const lemma = phraseKey(e.lemma, lang)
    return lemma.includes(' ') ? lemma : `${lemma}|${e.pos}`
  }
  const inLine = new Set<string>()

  // the pack words: the tapped form, its plural, its lemma, or a phrase of the line around it
  const lemmas = new Set(direct.map(r => normalizeForm(r.lemma, lang)))
  const hits = s.packs.flatMap(p =>
    p.words
      .map(w => ({ p, w, key: packKey(wordOf(p, w), lang), plural: w.plural ? packKey(w.plural, lang) : undefined }))
      .filter(h => h.key === form || h.plural === form || lemmas.has(h.key) || phrases.includes(h.key)),
  )
  // the pack whose item Jan has already comes first
  const fresh = (h: (typeof hits)[number]) => (s.items[itemId(h.p.id, h.w.id)] ? 0 : 1)
  hits.sort((a, b) => fresh(a) - fresh(b))
  // the pack words the tapped word is only a form of (not the word, its plural or a phrase of the line)
  const formOf = new WeakSet<LookupEntry>()
  for (const { p, w, key, plural } of hits) {
    const lemma = wordOf(p, w)
    // the dictionary's reading of the pack word: the one whose gloss is the pack's (hvala: "thank you", not "praise")
    const own = (rs: Reading[]) => rs.filter(r => normalizeForm(r.lemma, lang) === key)
    const candidates = own(direct).length ? own(direct) : own(s.lexicon.readings(key, s.base))
    const meant = englishWords(w.en)
    const inEnglish = (r: Reading) => r.en ?? r.gloss
    const reading = candidates.reduce<Reading | undefined>((best, r) => (!best || enScore(inEnglish(r), meant) > enScore(inEnglish(best), meant) ? r : best), undefined)
    const withGrammar = reading?.grammar && own(direct).includes(reading) ? reading : own(direct).find(r => r.grammar)
    const grammar = withGrammar?.grammar ?? (plural === form && key !== form ? 'plural' : null)
    const example = exampleOf(p, w)
    // the example meant in the learner's base, else in English (an English pack's example_en is the example itself)
    const exampleMeant = (w as Record<string, string | undefined>)[`example_${s.base}`] ?? (p.language === 'en' ? undefined : w.example_en)
    const id = itemId(p.id, w.id)
    // a pack without the learner's base (the Slovene packs have Slovene and English): the dictionary's meaning in it
    const inBase = meaningLangOf(p, w, s.base) === s.base || s.base === 'en'
    const theirs = !inBase && reading?.glossLang === s.base ? reading : undefined
    const e: LookupEntry = {
      lemma,
      pos: reading?.pos ?? (w.gender ? 'noun' : key.includes(' ') ? 'phrase' : 'word'),
      gloss: theirs ? theirs.gloss : [meaningOf(p, w, s.base)],
      gloss_lang: theirs ? s.base : meaningLangOf(p, w, s.base),
      ...(theirs ? { gloss_en: [w.en], ...(theirs.via ? { gloss_via: theirs.via } : {}) } : {}),
      grammar: narrow(grammar, cases),
      gender: w.gender ?? reading?.gender ?? null,
      source: 'pack',
      item_id: id,
      known: isKnown(id, lemma),
      example: example ? { sl: example, en: exampleMeant ?? '' } : null,
      emoji: w.emoji ?? null,
    }
    if ((withGrammar && isWiktionary(withGrammar)) || (theirs && isWiktionary(theirs))) attributed.add(e)
    if (phrases.includes(key)) inLine.add(keyOf(e))
    else if (key !== form && plural !== form) formOf.add(e)
    entries.push(e)
  }

  const tutor = direct.filter(r => r.source === 'tutor')
  entries.push(...tutor.map(r => fromReading(r, narrow(r.grammar, cases))))
  for (const p of s.people) {
    if (names(raw, form, p, lang)) entries.push({ lemma: p.name, pos: 'name', gloss: [p.gloss], gloss_lang: 'en', grammar: null, gender: null, source: 'village', item_id: null, known: false, example: null, emoji: p.emoji })
  }
  for (const ph of phrases) {
    for (const r of s.lexicon.readings(ph, s.base)) {
      const e = fromReading(r, r.grammar)
      inLine.add(keyOf(e))
      entries.push(e)
    }
  }
  entries.push(...direct.filter(r => r.source !== 'tutor').map(r => fromReading(r, narrow(r.grammar, cases))))

  // one entry per lemma and part of speech, per expression (the first; a later one may bring the grammar)
  const byKey = new Map<string, LookupEntry>()
  for (const e of entries) {
    const k = keyOf(e)
    const had = byKey.get(k)
    if (!had) byKey.set(k, e)
    else if (!had.grammar && e.grammar) {
      had.grammar = e.grammar
      if (attributed.has(e)) attributed.add(had)
    }
  }
  const en = englishWords(at.en)
  // without a translation to tell, the word itself as the supplement or the tutor wrote it ("ravno": just) comes before a
  // pack word it is only a form of
  const written = !en.size && [...byKey.values()].some(e => (e.source === 'extra' || e.source === 'tutor') && normalizeForm(e.lemma, lang) === form)
  const tier = (e: LookupEntry) => (e.source === 'pack' ? (written && formOf.has(e) ? 2 : 0) : e.source === 'tutor' ? 1 : 2)
  const capitalizedInside = /^\p{Lu}/u.test(raw) && i > 0
  const kind = (e: LookupEntry) => {
    if (e.source === 'village') return -2
    if (formOf.has(e)) return 1
    if (!fromWiktionary.has(e)) return 0
    if (e.pos === 'name') return capitalizedInside ? -1 : 2
    return normalizeForm(e.lemma, lang) === form ? 0 : 1
  }
  const ranked = [...byKey.values()]
    .map((e, n) => ({ e, n, score: Math.max(enScore(e.gloss, en), e.gloss_en ? enScore(e.gloss_en, en) : 0), line: inLine.has(keyOf(e)) ? 0 : 1 }))
    .sort((a, b) => a.line - b.line || Number(!a.score) - Number(!b.score) || tier(a.e) - tier(b.e) || kind(a.e) - kind(b.e) || b.score - a.score || a.n - b.n)
    .map(x => x.e)
    .slice(0, 5)
  // an English meaning for a learner who reads another language: the app says it is English
  if (s.base !== 'en') for (const e of ranked) if (e.gloss_lang === 'en') e.fallback = true
  markPartners(ranked, en, s.partners, lang)
  return { word: raw, entries: ranked, attribution: ranked.some(e => attributed.has(e)) ? ATTRIBUTION : null }
}

/**
 * A verb of the forms book's pairs gets its aspect and its partner (sesti: sedeti); of two partners both among the
 * [entries] ("sedite": sesti's imperative, sedeti's present and imperative), the earlier is the one the line means
 * ([LookupEntry.here]) when its translation [en] fits it better, by its glosses' words, then by how much of a gloss it has
 * ("Sit down, sit down": sesti).
 */
function markPartners(entries: LookupEntry[], en: Set<string>, partners: LookupSources['partners'], lang: string) {
  if (!partners) return
  for (const e of entries) {
    if (e.pos !== 'verb') continue
    const p = partners(e.lemma)
    if (!p) continue
    e.aspect = p.aspect
    e.partner = p.partner
  }
  const fit = (e: LookupEntry): [number, number] => {
    const glosses = [e.gloss, ...(e.gloss_en ? [e.gloss_en] : [])]
    return [Math.max(...glosses.map(g => enScore(g, en))), Math.max(...glosses.map(g => glossCoverage(g, en)))]
  }
  entries.forEach((a, i) => {
    const b = entries.slice(i + 1).find(x => x.pos === 'verb' && a.partner && normalizeForm(x.lemma, lang) === normalizeForm(a.partner.lemma, lang))
    if (!b || a.here || b.here) return
    const [fa, fb] = [fit(a), fit(b)]
    if (fa[0] > 0 && (fa[0] > fb[0] || (fa[0] === fb[0] && fa[1] > fb[1]))) a.here = true
  })
}

