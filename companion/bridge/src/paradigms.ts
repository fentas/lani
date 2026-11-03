// Word forms for Italian, German and English (companion/README.md, "Word forms": "Other languages"). Slovene's forms are
// read from its dictionary and forms book (forms.ts); these three have their own slots (Italian's tenses and moods, German's
// four cases, English's few forms), each on a page of that language's grammar book, and come from a paradigms file:
// companion/lexicon/<language>.paradigms.json, the content's words with their forms by slot, made from English
// Wiktionary's tables (kaikki.org) by companion/bin/forms-build. It is small (the words the content uses, not the whole
// language), so the phone's APK has it as the node has it, with or without the language's downloaded dictionary.
// Licence: CC BY-SA 4.0, as Wiktionary's text (NOTICE.md); every answer that uses it carries the attribution.
import { existsSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import type { Log } from './config'
import { englishWords, enScore, glossCoverage, normalizeForm, type WordGender } from './lexicon'
import type { ContentLine, FormLine, FormPos, LemmaForms, LineIndex, Slot } from './forms'
import { moodInEnglish } from './forms'

/** The languages whose words GET /forms answers from a paradigms file, to an app that reads their tables (`v=2`). */
export const PARADIGM_LANGUAGES: ReadonlySet<string> = new Set(['it', 'de', 'en'])
export type ParadigmLanguage = 'it' | 'de' | 'en'
export const isParadigmLanguage = (l: string): l is ParadigmLanguage => PARADIGM_LANGUAGES.has(l)

// --- slots ---------------------------------------------------------------------------------------------------------

const P6 = ['1sg', '2sg', '3sg', '1pl', '2pl', '3pl']
const each = (head: string) => P6.map(p => `${head}.${p}`)

/**
 * Each language's slots by part of speech, in the order its table shows them. Italian: the present, the past participle
 * (the passato prossimo's, with its auxiliary), the imperfect, the future, the conditional, the present and imperfect
 * subjunctive, the passato remoto, the imperative (tu, noi, voi), the gerund; a noun's singular and plural; an
 * adjective's genders and numbers, its irregular comparative (migliore) and absolute superlative (bellissimo). German:
 * the present, the preterite, the past participle (Partizip II, with haben or sein), Konjunktiv II (of the verbs that
 * use their own: wäre, hätte, könnte), the imperative (du, ihr); a noun's four cases in the singular and the plural; an
 * adjective's comparative and superlative. English: the third person's -s (and be's am, are, is), the past (be's was,
 * were), the past participle, the -ing form; a noun's plural; an adjective's comparative and superlative.
 */
export const LANG_KEYS: Record<ParadigmLanguage, Record<FormPos, string[]>> = {
  it: {
    verb: [...each('pres'), 'pp', ...each('impf'), ...each('fut'), ...each('cond'), ...each('subj'), ...each('subji'), ...each('rem'), 'imp.2sg', 'imp.1pl', 'imp.2pl', 'ger'],
    noun: ['sg', 'pl'],
    adj: ['adj.m.sg', 'adj.f.sg', 'adj.m.pl', 'adj.f.pl', 'cmp', 'sup'],
  },
  de: {
    verb: [...each('pres'), ...each('pret'), 'pp', ...each('konj2'), 'imp.2sg', 'imp.2pl'],
    noun: ['nom.sg', 'gen.sg', 'dat.sg', 'acc.sg', 'nom.pl', 'gen.pl', 'dat.pl', 'acc.pl'],
    adj: ['cmp', 'sup'],
  },
  en: {
    verb: ['pres.1sg', 'pres.2sg', 'pres.3sg', 'past.1sg', 'past.2sg', 'past', 'pp', 'ing'],
    noun: ['sg', 'pl'],
    adj: ['cmp', 'sup'],
  },
}

/** The slots a word's own form fills (the dictionary form): shown in its table, never asked. */
export const OWN_KEYS: Record<ParadigmLanguage, Set<string>> = {
  it: new Set(['sg', 'adj.m.sg']),
  de: new Set(['nom.sg']),
  en: new Set(['sg']),
}

const PERSON: Record<string, string> = { 'first-person': '1', 'second-person': '2', 'third-person': '3' }
const NUMBER: Record<string, string> = { singular: 'sg', plural: 'pl' }
const CASE: Record<string, string> = { nominative: 'nom', genitive: 'gen', dative: 'dat', accusative: 'acc' }
const GENDER: Record<string, string> = { masculine: 'm', feminine: 'f', neuter: 'n' }

function personOf(tags: Set<string>): string | null {
  const ps = Object.keys(PERSON).filter(t => tags.has(t))
  const ns = Object.keys(NUMBER).filter(t => tags.has(t))
  if (ps.length !== 1 || ns.length !== 1) return null
  return `${PERSON[ps[0]]}${NUMBER[ns[0]]}`
}

/** Tags that make a row of Wiktionary's table no form of a slot (a clause's other word order, a compound tense, …). */
const NOT_A_SLOT = ['subordinate-clause', 'dependent', 'perfect', 'pluperfect', 'progressive', 'passive', 'infinitive', 'zu-infinitive', 'includes-article', 'diminutive', 'augmentative', 'auxiliary', 'canonical', 'table-tags', 'inflection-template', 'romanization', 'abbreviation', 'contraction']

/**
 * The slot one of Wiktionary's table rows of a [pos] in [lang] fills, by its tags (kaikki.org's: ["first-person",
 * "indicative", "present", "singular"] → pres.1sg); null for a row no slot stands for. Used by companion/bin/forms-build.
 */
export function slotOfTags(lang: ParadigmLanguage, pos: FormPos, tagList: string[]): string | null {
  const tags = new Set(tagList)
  if (NOT_A_SLOT.some(t => tags.has(t)) || tagList.some(t => t.startsWith('error-'))) return null
  const p = personOf(tags)
  if (lang === 'it') {
    if (pos === 'verb') {
      if (tags.has('gerund')) return tags.has('past') ? null : 'ger'
      if (tags.has('participle')) return tags.has('past') && !tags.has('present') && !tags.has('feminine') && !tags.has('plural') ? 'pp' : null
      if (tags.has('imperative')) return !tags.has('negative') && !tags.has('formal') && !tags.has('third-person') && p && ['2sg', '1pl', '2pl'].includes(p) ? `imp.${p}` : null
      if (!p) return null
      if (tags.has('subjunctive')) return tags.has('present') ? `subj.${p}` : tags.has('imperfect') ? `subji.${p}` : null
      if (tags.has('conditional')) return `cond.${p}`
      if (tags.has('future')) return `fut.${p}`
      if (tags.has('historic') && tags.has('past')) return `rem.${p}`
      if (tags.has('imperfect')) return `impf.${p}`
      if (tags.has('present')) return `pres.${p}`
      return null
    }
    if (pos === 'noun') return tags.has('plural') && !tags.has('singular') ? 'pl' : null
    if (pos === 'adj') {
      if (tags.has('comparative')) return 'cmp'
      if (tags.has('superlative')) return tags.has('feminine') || tags.has('plural') ? null : 'sup'
      const g = Object.keys(GENDER).filter(t => tags.has(t) && t !== 'neuter')
      // a gender alone is the singular's (Wiktionary's head: "feminine buona")
      const n = Object.keys(NUMBER).filter(t => tags.has(t))
      if (g.length === 1 && !n.length) return `adj.${GENDER[g[0]]}.sg`
      return g.length === 1 && n.length === 1 ? `adj.${GENDER[g[0]]}.${NUMBER[n[0]]}` : null
    }
    return null
  }
  if (lang === 'de') {
    if (pos === 'verb') {
      if (tags.has('participle')) return tags.has('past') && !tags.has('present') ? 'pp' : null
      if (tags.has('imperative')) return tags.has('formal') || tags.has('first-person') || tags.has('third-person') ? null : tags.has('singular') ? 'imp.2sg' : tags.has('plural') ? 'imp.2pl' : null
      if (!p) return null
      if (tags.has('subjunctive-ii')) return `konj2.${p}`
      if (tags.has('subjunctive-i') || tags.has('subjunctive')) return null
      if (tags.has('past') || tags.has('preterite')) return `pret.${p}`
      if (tags.has('present')) return `pres.${p}`
      return null
    }
    if (pos === 'noun') {
      const c = Object.keys(CASE).filter(t => tags.has(t))
      const n = Object.keys(NUMBER).filter(t => tags.has(t))
      if (tags.has('indefinite') || tags.has('vocative')) return null
      return c.length === 1 && n.length === 1 ? `${CASE[c[0]]}.${NUMBER[n[0]]}` : null
    }
    if (pos === 'adj') {
      // the predicative or bare comparative and superlative ("schöner", "am schönsten"), not their declined forms
      const declined = ['strong', 'weak', 'mixed', 'nominative', 'genitive', 'dative', 'accusative'].some(t => tags.has(t))
      if (declined) return null
      if (tags.has('comparative')) return tags.has('predicative') && (tags.has('feminine') || tags.has('neuter') || tags.has('plural')) ? null : 'cmp'
      if (tags.has('superlative')) return tags.has('predicative') && (tags.has('feminine') || tags.has('neuter') || tags.has('plural')) ? null : 'sup'
      return null
    }
    return null
  }
  // English
  if (pos === 'verb') {
    if (tags.has('participle')) return tags.has('past') ? 'pp' : tags.has('present') ? 'ing' : null
    if (tags.has('subjunctive') || tags.has('imperative') || tags.has('negative')) return null
    if (tags.has('past')) {
      if (!tags.has('first-person') && !tags.has('second-person') && !tags.has('third-person') && !tags.has('singular') && !tags.has('plural')) return 'past'
      // be's: was (first and third singular), were (second, plural)
      if (tags.has('second-person') || tags.has('plural')) return 'past.2sg'
      if (tags.has('singular') && (tags.has('first-person') || tags.has('third-person'))) return 'past.1sg'
      return null
    }
    if (tags.has('present')) {
      if (tags.has('third-person') && tags.has('singular')) return 'pres.3sg'
      if (tags.has('first-person') && tags.has('singular')) return 'pres.1sg'
      if (tags.has('second-person') || tags.has('plural')) return 'pres.2sg'
    }
    return null
  }
  if (pos === 'noun') return tags.has('plural') && !tags.has('singular') ? 'pl' : null
  if (pos === 'adj') return tags.has('comparative') ? 'cmp' : tags.has('superlative') ? 'sup' : null
  return null
}

/**
 * A row's register: a form people say, accepted and never shown (`also`); an old, rare or joking one, left out; else
 * standard. English keeps only another country's spelling (US, UK) as accepted: its nonstandard forms (gooder, goed, I
 * were) are the course's `nonstandard` grading's (almost, the standard shown), or a learner's mistakes, never right.
 */
export function registerOf(tagList: string[], lang?: string): 'standard' | 'also' | 'out' {
  const tags = new Set(tagList)
  if (['archaic', 'obsolete', 'dated', 'rare', 'poetic', 'literary', 'misspelling', 'historical', 'apocopic', 'nonstandard-spelling', 'proscribed', 'humorous', 'childish', 'eye-dialect', 'pronunciation-spelling'].some(t => tags.has(t))) return 'out'
  const spelling = ['US', 'UK', 'British', 'American', 'Commonwealth', 'Canada', 'Australia']
  if (lang === 'en') {
    if (['colloquial', 'informal', 'dialectal', 'regional', 'nonstandard', 'uncommon', 'alternative', 'slang', 'Scotland', 'Northern-England'].some(t => tags.has(t))) return 'out'
    return spelling.some(t => tags.has(t)) ? 'also' : 'standard'
  }
  if (['colloquial', 'informal', 'dialectal', 'regional', 'nonstandard', 'Austria', 'Austrian', 'Southern-Germany', 'Bavarian', 'Switzerland', 'Swiss', 'uncommon', 'alternative', 'Northern-Italy', 'Tuscany', ...spelling].some(t => tags.has(t))) return 'also'
  return 'standard'
}

/** What a slot is, in English words, as GET /forms' `grammar` says it ("first-person singular present subjunctive"). */
export function grammarOfSlotIn(lang: ParadigmLanguage, key: string): string {
  const [a, b, c] = key.split('.')
  const person = (pn: string) => {
    const who: Record<string, string> = { '1': 'first-person', '2': 'second-person', '3': 'third-person' }
    return `${who[pn[0]]} ${pn.slice(1) === 'sg' ? 'singular' : 'plural'}`
  }
  const tense: Record<string, string> = {
    pres: 'present', impf: 'imperfect', fut: 'future', cond: 'conditional', subj: 'present subjunctive', subji: 'imperfect subjunctive',
    rem: 'past historic', pret: 'preterite', konj2: 'subjunctive II', imp: 'imperative',
  }
  if (lang === 'en') {
    if (key === 'pres.1sg' || key === 'pres.3sg') return `${person(b)} present`
    if (key === 'pres.2sg') return 'second-person and plural present'
    if (key === 'past.1sg') return 'first/third-person singular past'
    if (key === 'past.2sg') return 'second-person and plural past'
  }
  if (tense[a] && b && /^[123](sg|pl)$/.test(b)) return `${person(b)} ${tense[a]}`
  const kase: Record<string, string> = { nom: 'nominative', gen: 'genitive', dat: 'dative', acc: 'accusative' }
  if (kase[a]) return `${kase[a]} ${b === 'sg' ? 'singular' : 'plural'}`
  switch (a) {
    case 'pp':
      return 'past participle'
    case 'ger':
      return 'gerund'
    case 'ing':
      return 'present participle'
    case 'past':
      return 'past'
    case 'sg':
      return 'singular'
    case 'pl':
      return 'plural'
    case 'cmp':
      return 'comparative'
    case 'sup':
      return 'superlative'
    case 'adj':
      return `${b === 'm' ? 'masculine' : 'feminine'} ${c === 'sg' ? 'singular' : 'plural'}`
  }
  return key
}

// --- pages ---------------------------------------------------------------------------------------------------------

/** Where a slot's form counts: its grammar book page (the rule a wrong one breaks) and the pages it needs introduced too. */
export type SlotPageIn = { page: string; needs: string[] }

const IT_OWN: Record<string, string> = { essere: 'essere-avere', avere: 'essere-avere', potere: 'modali', volere: 'modali', dovere: 'modali', sapere: 'modali' }
const DE_MODALS = new Set(['können', 'müssen', 'dürfen', 'sollen', 'wollen', 'mögen', 'möchten', 'wissen'])
/** German verbs with Konjunktiv II forms of their own in use (wäre, hätte, würde, könnte, wüsste): the others say würde. */
export const DE_OWN_KONJ2 = new Set(['sein', 'haben', 'werden', ...DE_MODALS])

/** Italian's regular present by its infinitive's ending, with the spelling's c/g → ch/gh and a stem's i kept once. */
function regularPresentIt(lemma: string, forms: (k: string) => string[]): boolean {
  const l = lemma.replace(/si$/, 'e')
  const m = /^(.+)(are|ere|ire)$/.exec(l)
  if (!m) return false
  const [, stem, end] = m
  const has = (k: string, ...want: string[]) => forms(k).some(f => want.includes(f))
  const hard = /[cg]$/.test(stem) && end === 'are'
  const i = stem.endsWith('i') ? stem.slice(0, -1) : stem
  if (end === 'are')
    return has('pres.1sg', `${stem}o`) && has('pres.2sg', hard ? `${stem}hi` : `${i}i`) && has('pres.3sg', `${stem}a`) && has('pres.1pl', hard ? `${stem}hiamo` : `${i}iamo`) && has('pres.2pl', `${stem}ate`) && has('pres.3pl', `${stem}ano`)
  if (end === 'ere') return has('pres.1sg', `${stem}o`) && has('pres.2sg', `${i}i`) && has('pres.3sg', `${stem}e`) && has('pres.1pl', `${i}iamo`) && has('pres.2pl', `${stem}ete`) && has('pres.3pl', `${stem}ono`)
  const plain = has('pres.1sg', `${stem}o`) && has('pres.2sg', `${i}i`) && has('pres.3sg', `${stem}e`) && has('pres.3pl', `${stem}ono`)
  const isc = has('pres.1sg', `${stem}isco`) && has('pres.2sg', `${stem}isci`) && has('pres.3sg', `${stem}isce`) && has('pres.3pl', `${stem}iscono`)
  return (plain || isc) && has('pres.1pl', `${i}iamo`) && has('pres.2pl', `${stem}ite`)
}

/** Italian's regular past participle: -are → -ato, -ere → -uto, -ire → -ito. */
function regularParticipleIt(lemma: string, forms: string[]): boolean {
  const l = lemma.replace(/si$/, 'e')
  const m = /^(.+)(are|ere|ire)$/.exec(l)
  if (!m) return false
  const want = `${m[1]}${{ are: 'ato', ere: 'uto', ire: 'ito' }[m[2] as 'are' | 'ere' | 'ire']}`
  return forms.includes(want)
}

/** Whether a German verb's du and er forms change its stem's vowel (fahren: fährst, fährt; sehen: siehst, sieht). */
function vowelChangeDe(lemma: string, forms: (k: string) => string[]): boolean {
  const main = forms('pres.3sg')[0] ?? ''
  const [word, ...particle] = main.split(' ')
  if (!word) return false
  // a separable verb's prefix ("fährt ab" of abfahren): the stem is the infinitive's after the particle
  const rest = particle.length ? lemma.slice(particle.join('').length) : lemma
  const stem = rest.replace(/e?n$/, '')
  return !word.startsWith(stem)
}

/**
 * The page of slot [key] of [lemma] in [lang] ([forms]: the entry's forms by slot, for what its regularity says):
 * Italian's present on presente-regolare, or essere-avere, modali, verbi-irregolari for an irregular one; a regular
 * participle on passato-prossimo, an irregular one on participi-irregolari; German's present on praesens, sein-haben,
 * modalverben, werden-futur, a changed vowel's du and er forms on vokalwechsel; the short preterite of sein, haben and
 * the modals on praeteritum-kurz; a noun's cases on their pages (a plural's need plural); English's on its tense's page.
 */
export function pageOfIn(lang: ParadigmLanguage, lemma: string, pos: FormPos, key: string, forms: (k: string) => string[], gender?: WordGender | null): SlotPageIn | null {
  const [a, b] = key.split('.')
  const l = lemma.toLowerCase()
  if (lang === 'it') {
    if (pos === 'noun') return key === 'pl' ? { page: 'plurale', needs: [] } : key === 'sg' ? { page: 'articoli-genere', needs: [] } : null
    if (pos === 'adj') {
      if (a === 'adj') return { page: 'aggettivi-accordo', needs: [] }
      return a === 'cmp' ? { page: 'comparativi', needs: [] } : a === 'sup' ? { page: 'superlativi', needs: [] } : null
    }
    switch (a) {
      case 'pres': {
        const own = IT_OWN[l] ?? (regularPresentIt(l, forms) ? 'presente-regolare' : 'verbi-irregolari')
        return { page: own, needs: [] }
      }
      case 'pp':
        return regularParticipleIt(l, forms('pp')) ? { page: 'passato-prossimo', needs: [] } : { page: 'participi-irregolari', needs: ['passato-prossimo'] }
      case 'impf':
        return { page: 'imperfetto', needs: [] }
      case 'fut':
        return { page: 'futuro', needs: [] }
      case 'cond':
        return { page: 'condizionale', needs: [] }
      case 'subj':
        return { page: 'congiuntivo', needs: [] }
      case 'subji':
        return { page: 'congiuntivo-imperfetto', needs: [] }
      case 'rem':
        return { page: 'passato-remoto', needs: [] }
      case 'imp':
        return { page: 'imperativo', needs: [] }
      case 'ger':
        return { page: 'stare-gerundio', needs: [] }
    }
    return null
  }
  if (lang === 'de') {
    if (pos === 'noun') {
      const casePage: Record<string, string> = { nom: 'artikel-genus', acc: 'akkusativ', dat: 'dativ', gen: 'genitiv' }
      const page = casePage[a]
      if (!page) return null
      // a weak masculine's -(e)n in the singular's other cases (den Jungen, dem Herrn): the n-Deklination
      const nom = forms('nom.sg')[0]
      const weak = gender === 'm' && b === 'sg' && a !== 'nom' && !!nom && forms(key).length > 0 && forms(key).every(f => f !== nom && /n$/.test(f) && !/s$/.test(f))
      if (weak) return { page: 'n-deklination', needs: [page] }
      if (b === 'pl') return a === 'nom' ? { page: 'plural', needs: [] } : { page, needs: ['plural'] }
      return { page, needs: [] }
    }
    if (pos === 'adj') return a === 'cmp' || a === 'sup' ? { page: 'steigerung', needs: [] } : null
    switch (a) {
      case 'pres': {
        if (l === 'sein' || l === 'haben') return { page: 'sein-haben', needs: [] }
        if (DE_MODALS.has(l)) return { page: 'modalverben', needs: [] }
        if (l === 'werden') return { page: 'werden-futur', needs: [] }
        if ((b === '2sg' || b === '3sg') && vowelChangeDe(l, forms)) return { page: 'vokalwechsel', needs: ['praesens'] }
        return { page: 'praesens', needs: [] }
      }
      case 'pret':
        return l === 'sein' || l === 'haben' || DE_MODALS.has(l) ? { page: 'praeteritum-kurz', needs: [] } : { page: 'praeteritum', needs: [] }
      case 'pp':
        return { page: 'partizip-zwei', needs: [] }
      case 'konj2':
        return DE_OWN_KONJ2.has(l) ? { page: 'konjunktiv-zwei', needs: [] } : null
      case 'imp':
        return { page: 'imperativ', needs: [] }
    }
    return null
  }
  // English
  if (pos === 'noun') return key === 'pl' ? { page: 'plurals', needs: [] } : key === 'sg' ? { page: 'articles', needs: [] } : null
  if (pos === 'adj') return a === 'cmp' || a === 'sup' ? { page: 'comparatives', needs: [] } : null
  switch (a) {
    case 'pres':
      return l === 'be' ? { page: 'to-be', needs: [] } : l === 'have' ? { page: 'to-have', needs: [] } : b === '3sg' ? { page: 'present-simple', needs: [] } : null
    case 'past':
      return l === 'be' ? { page: 'was-were', needs: [] } : b ? null : { page: 'past-simple', needs: [] }
    case 'pp':
      return { page: 'present-perfect', needs: [] }
    case 'ing':
      return { page: 'present-continuous', needs: [] }
  }
  return null
}

// --- the paradigms file ----------------------------------------------------------------------------------------------

/** One form as written: letters, and up to two more words after a space, an apostrophe or a hyphen ("fahre ab", "am schönsten"). */
const written = z.string().trim().min(1).max(40).regex(/^[\p{L}\p{M}]+(?:[ '’-][\p{L}\p{M}]+){0,2}$/u, 'a word (or up to three)')
const formsOfSlot = z.union([written, z.array(written).min(1).max(4)])

const paradigmEntry = z.object({
  lemma: z.string().trim().min(1).max(60),
  pos: z.enum(['verb', 'noun', 'adj']),
  gloss: z.array(z.string().trim().min(1).max(120)).max(3).default([]),
  gender: z.enum(['m', 'f', 'n']).optional(),
  /** A verb's auxiliary in the compound past (avere, essere; haben, sein), the usual first. */
  aux: z.array(z.string().trim().min(1).max(20)).min(1).max(2).optional(),
  forms: z.record(z.string(), formsOfSlot),
  /** Forms accepted too, never shown: what people say (Wiktionary's colloquial, regional or alternative forms). */
  also: z.record(z.string(), formsOfSlot).optional(),
})
export type ParadigmEntry = z.infer<typeof paradigmEntry>

export type ParadigmsMeta = {
  schema?: string
  language: string
  source?: string
  url?: string
  licence?: string
  licence_url?: string
  attribution?: string
  modified?: string
  built?: string
  version?: string
}

/** Each entry checked on its own: a wrong one is reported and left out, never the whole file; slots its language lacks too. */
export function validateParadigms(input: unknown, language: ParadigmLanguage): { meta: ParadigmsMeta | null; entries: ParadigmEntry[]; errors: string[] } {
  const file = z.object({ meta: z.looseObject({ language: z.string() }), entries: z.array(z.unknown()) }).safeParse(input)
  if (!file.success) return { meta: null, entries: [], errors: [z.prettifyError(file.error)] }
  const errors: string[] = []
  if (file.data.meta.language !== language) errors.push(`meta.language is "${file.data.meta.language}", the file is ${language}'s`)
  const entries = file.data.entries.flatMap((e, i) => {
    const r = paradigmEntry.safeParse(e)
    if (!r.success) {
      errors.push(`entries[${i}] (${(e as { lemma?: string })?.lemma ?? '?'}): ${z.prettifyError(r.error)}`)
      return []
    }
    const keys = LANG_KEYS[language][r.data.pos]
    const wrong = [...Object.keys(r.data.forms), ...Object.keys(r.data.also ?? {})].filter(k => !keys.includes(k))
    if (wrong.length) {
      errors.push(`entries[${i}] (${r.data.lemma}): "${wrong.join('", "')}" isn't a slot of a ${language} ${r.data.pos}`)
      return []
    }
    return [r.data]
  })
  return { meta: file.data.meta as ParadigmsMeta, entries, errors }
}

const list = (v: string | string[] | undefined): string[] => (v === undefined ? [] : typeof v === 'string' ? [v] : v)

/** A language's paradigms file (companion/lexicon/<language>.paradigms.json), read again when it changes. */
export class Paradigms {
  private mtime = -1
  private state: { meta: ParadigmsMeta | null; entries: ParadigmEntry[]; byLemma: Map<string, ParadigmEntry[]>; byForm: Map<string, ParadigmEntry[]> } = {
    meta: null, entries: [], byLemma: new Map(), byForm: new Map(),
  }

  constructor(private readonly o: { dir: string; language: string; log?: Log }) {}

  get language(): string {
    return this.o.language
  }

  private get path() {
    return join(this.o.dir, `${this.o.language}.paradigms.json`)
  }

  private norm = (s: string) => normalizeForm(s, this.o.language)

  private current() {
    if (!isParadigmLanguage(this.o.language)) return this.state
    const path = this.path
    const mtime = existsSync(path) ? statSync(path).mtimeMs : 0
    if (mtime === this.mtime) return this.state
    this.mtime = mtime
    this.state = { meta: null, entries: [], byLemma: new Map(), byForm: new Map() }
    if (!mtime) return this.state
    try {
      const r = validateParadigms(JSON.parse(readFileSync(path, 'utf8')), this.o.language)
      if (r.errors.length) this.o.log?.(`paradigms ${path}: left out:\n${r.errors.slice(0, 20).join('\n')}${r.errors.length > 20 ? `\n… ${r.errors.length - 20} more` : ''}`)
      const byLemma = new Map<string, ParadigmEntry[]>()
      const byForm = new Map<string, ParadigmEntry[]>()
      const add = (m: Map<string, ParadigmEntry[]>, k: string, e: ParadigmEntry) => {
        const l = m.get(k) ?? []
        if (!l.includes(e)) l.push(e)
        m.set(k, l)
      }
      for (const e of r.entries) {
        add(byLemma, this.norm(e.lemma), e)
        add(byForm, this.norm(e.lemma), e)
        for (const v of [...Object.values(e.forms), ...Object.values(e.also ?? {})]) for (const f of list(v)) add(byForm, this.norm(f), e)
      }
      this.state = { meta: r.meta, entries: r.entries, byLemma, byForm }
    } catch (e) {
      this.o.log?.(`paradigms ${path} unreadable: ${(e as Error).message}`)
    }
    return this.state
  }

  /** The file's meta (its source, licence and attribution); null without a file. */
  meta(): ParadigmsMeta | null {
    return this.current().meta
  }

  /** How many entries the file has. */
  size(): number {
    return this.current().entries.length
  }

  /** The entries of the lemma written [word] (a noun and a verb of one spelling: both; German's Essen and essen alike). */
  entriesOf(word: string): ParadigmEntry[] {
    return this.current().byLemma.get(this.norm(word)) ?? []
  }

  /** The entries [form] is a form of (or the lemma of). */
  entriesWithForm(form: string): ParadigmEntry[] {
    return this.current().byForm.get(this.norm(form)) ?? []
  }

  /** Every entry. */
  all(): ParadigmEntry[] {
    return this.current().entries
  }
}

// --- a word's forms ------------------------------------------------------------------------------------------------

/** At most this many lines a slot gets. */
const LINES_PER_SLOT = 3
/** English forms of have and be that make a past participle the perfect's or the passive's ("has seen", "was seen"). */
const EN_PERFECT = new Set(['have', 'has', 'had', 've', 'd', 'having'])
const EN_BE = new Set(['am', 'is', 'are', 'was', 'were', 'be', 'been', 'being', 'm', 're', 's'])
/** English words before a noun or an adjective: a form after one is not a verb's ("the left hand"). */
const EN_DETERMINERS = new Set(['the', 'a', 'an', 'my', 'your', 'his', 'her', 'its', 'our', 'their', 'this', 'that', 'these', 'those', 'some', 'no', 'very', 'so', 'too'])
/** Italian's words before a subjunctive (che, perché, prima che, sebbene …) and the imperfect subjunctive's se. */
const IT_SUBJ_BEFORE = new Set(['che', 'perché', 'affinché', 'sebbene', 'benché', 'qualora', 'purché', 'magari'])

export type OtherFormsContext = {
  paradigms: Paradigms
  /** The grammar book's pages: a slot of a page it lacks isn't served. */
  pages: Set<string>
  lines: LineIndex
  language: ParadigmLanguage
}

/** The slots of [e] by key, normalized for matching. */
function slotsOf(e: ParadigmEntry, language: string): Map<string, string[]> {
  const out = new Map<string, string[]>()
  for (const [k, v] of Object.entries(e.forms)) out.set(k, list(v).map(f => normalizeForm(f, language)))
  return out
}

/**
 * The slots [form] stands for in [line] as a form of [e]: its slots narrowed by what the line says around it (English:
 * have before a participle makes it the perfect's, be before -ing the continuous; Italian: che before it the
 * subjunctive's, none the indicative's; a present and an imperative alike, by the translation), and only when what is
 * left is of one page.
 */
function slotsInLineOther(c: OtherFormsContext, e: ParadigmEntry, norm: Map<string, string[]>, form: string, line: ContentLine): string[] {
  const tokens = line.tokens
  const i = tokens.indexOf(form)
  let keys = [...norm.entries()].filter(([, fs]) => fs.includes(form)).map(([k]) => k)
  if (!keys.length) return []
  const before = (n: number) => tokens.slice(Math.max(0, i - n), i)
  if (c.language === 'en') {
    if (keys.includes('pp') && keys.some(k => k.startsWith('past'))) {
      const b = before(3)
      if (b.some(t => EN_PERFECT.has(t))) keys = keys.filter(k => k === 'pp')
      else if (b.some(t => EN_BE.has(t))) return [] // a passive: another page's
      else keys = keys.filter(k => k !== 'pp')
    } else if (keys.includes('pp') && !before(3).some(t => EN_PERFECT.has(t))) return []
    if (keys.includes('ing') && !before(2).some(t => EN_BE.has(t))) return [] // a gerund ("I like walking"), not the continuous
  }
  if (c.language === 'it') {
    const subj = keys.filter(k => k.startsWith('subj'))
    if (subj.length) {
      if (before(4).some(t => IT_SUBJ_BEFORE.has(t))) keys = subj.length < keys.length ? subj : keys
      else keys = keys.filter(k => !k.startsWith('subj'))
    }
  }
  if (keys.some(k => k.startsWith('imp.')) && keys.some(k => k.startsWith('pres.'))) {
    const mood = moodInEnglish(line.en, e.gloss, keys.some(k => /^imp\.1/.test(k)))
    keys = keys.filter(k => !mood || k.startsWith(`${mood}.`))
  }
  if (!keys.length) return []
  const at = (k: string) => norm.get(k) ?? []
  const sigs = new Set(keys.map(k => {
    const pg = pageOfIn(c.language, e.lemma, e.pos, k, at, e.gender)
    return pg ? `${pg.page}|${[...pg.needs].sort().join(',')}` : '?'
  }))
  return sigs.size === 1 && !sigs.has('?') ? keys : []
}

/** Whether a line translated [en] means [e] rather than the other entries [form] is of (their glosses). */
function meansThisOther(e: ParadigmEntry, others: ParadigmEntry[], en: Set<string>): boolean {
  if (!others.length) return true
  const fit = (g: string[]): [number, number] => [enScore(g, en), glossCoverage(g, en)]
  const mine = fit(e.gloss)
  if (mine[0] <= 0) return false
  return others.every(o => {
    const f = fit(o.gloss)
    return mine[0] > f[0] || (mine[0] === f[0] && mine[1] > f[1])
  })
}

function linesOfOther(c: OtherFormsContext, e: ParadigmEntry, norm: Map<string, string[]>): Map<string, FormLine[]> {
  const out = new Map<string, FormLine[]>()
  const all = new Set([normalizeForm(e.lemma, c.language), ...[...norm.values()].flat()])
  const lang = c.language
  for (const form of new Set([...norm.values()].flat())) {
    if (form.includes(' ')) continue // a separable verb's two words ("fahre ab"): never one gap
    const candidates = c.lines
      .with(form)
      .filter(l => l.tokens.filter(t => t === form).length === 1 && !l.tokens.some(t => t !== form && all.has(t)))
      .sort((a, b) => a.text.length - b.text.length || a.text.localeCompare(b.text))
    if (!candidates.length) continue
    const others = c.paradigms.entriesWithForm(form).filter(o => o !== e && !(normalizeForm(o.lemma, lang) === normalizeForm(e.lemma, lang) && o.pos === e.pos && sameForms(o, e)))
    // English: the line is its own translation, which can't tell "walks" the verb from "walks" the noun; a word that is
    // the form itself (eaten, the adjective; left) is the form only where no determiner stands before it
    const ofOthers = lang === 'en' ? others.filter(o => normalizeForm(o.lemma, lang) !== form) : others
    if (lang === 'en' && ofOthers.some(o => o.pos !== e.pos)) continue
    const itself = lang === 'en' && others.length > ofOthers.length
    for (const l of candidates) {
      if (itself && EN_DETERMINERS.has(l.tokens[l.tokens.indexOf(form) - 1] ?? '')) continue
      if (lang !== 'en' && !meansThisOther(e, others, englishWords(l.en))) continue
      const keys = slotsInLineOther(c, e, norm, form, l)
      for (const k of keys) {
        const ls = out.get(k) ?? []
        if (ls.length >= LINES_PER_SLOT) continue
        const tr = l as ContentLine & { sl?: string }
        ls.push({ [lang]: l.text, text: l.text, en: l.en, ...(l.de ? { de: l.de } : {}), ...(l.it ? { it: l.it } : {}), ...(tr.sl ? { sl: tr.sl } : {}), form, from: l.from } as FormLine)
        out.set(k, ls)
      }
    }
  }
  return out
}

const sameForms = (a: ParadigmEntry, b: ParadigmEntry) => JSON.stringify(a.forms) === JSON.stringify(b.forms)

/**
 * A word's forms in Italian, German or English: an entry per lemma of the paradigms file written [word] (a noun, a verb,
 * an adjective), each slot with its page and the content's lines; a slot of a page the language's book lacks isn't
 * served, nor a word with fewer than two slots.
 */
export function otherFormsOf(c: OtherFormsContext, word: string): LemmaForms[] {
  const out: LemmaForms[] = []
  const lang = c.language
  for (const e of c.paradigms.entriesOf(word)) {
    const norm = slotsOf(e, lang)
    const at = (k: string) => norm.get(k) ?? []
    const lines = linesOfOther(c, e, norm)
    const slots: Slot[] = []
    for (const key of LANG_KEYS[lang][e.pos]) {
      const forms = list(e.forms[key])
      if (!forms.length) continue
      const pg = pageOfIn(lang, e.lemma, e.pos, key, at, e.gender)
      if (!pg || !c.pages.has(pg.page) || pg.needs.some(n => !c.pages.has(n))) continue
      const also = list(e.also?.[key]).filter(f => !forms.includes(f))
      const ls = lines.get(key)
      slots.push({
        key, forms, ...(also.length ? { also } : {}),
        grammar: grammarOfSlotIn(lang, key), page: pg.page, ...(pg.needs.length ? { needs: pg.needs } : {}), ...(ls?.length ? { lines: ls } : {}),
      })
    }
    if (slots.length < 2) continue
    out.push({
      lemma: e.lemma, pos: e.pos, gloss: e.gloss, gender: e.gender ?? null,
      table: 'wiktionary', slots, language: lang, ...(e.aux ? { aux: e.aux } : {}),
    })
  }
  return out
}
