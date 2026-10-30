// The case lens (docs/plans/05-built/case-lens.md): a Slovene word of a sentence, its case and number, and the word
// that decided it. The readings of each word come from the forms book (forms.ts): Wiktionary's dictionary and the
// supplement read back into case and number, the hand-checked tables, the personal pronouns of the trigger table
// (case-triggers.ts), the village's names (addressee.ts sloveneName) and, for a noun the dictionary has without that form,
// its regular endings (marked: "by the endings"). The sentence then decides between the readings, each step a rule of
// the trigger table: a fixed phrase, the preposition before the word, a number or a word of quantity, a word of feeling,
// a negation, a verb that governs a case, the subject's agreement with its verb, the object of a verb that takes one.
// A word whose readings the sentence can't tell apart keeps them all ("ambiguous"), the likely one first: the lens never
// guesses silently. Adjectives, possessives and numbers agree with their noun: they take its case, with their gender.
// Pure: everything it reads is handed in (the bridge's dictionary, its forms book, the table).
import { normalizeForm, type Lexicon } from './lexicon'
import { slotsOf, type FormsBook } from './forms'
import { sloveneName } from './addressee'
import { CASE_KEYS, type CaseKey, type CaseTriggers, type GenderKey, type NumberKey, type PrepositionEntry, type VerbEntry, type VerbForms } from './case-triggers'

// --- what the lens says --------------------------------------------------------------------------------------------

/** One reading of a word's form: its lemma, part of speech, case, number and the genders it can be. */
export type FormReading = { lemma: string; pos: string; case: CaseKey; number: NumberKey; genders: GenderKey[]; derived?: true }

/**
 * What decided the case: a preposition, a negation, a word of quantity or a number from five, a verb or a word of
 * feeling that governs it, a copula (what something is), an adjective of its own saying what the subject is like (Rad
 * pomagam, Zvonik je visok: predicative), the verb whose subject or object it is, a call, the noun it
 * belongs to (of whom?), a time, a fixed phrase, a preposition fused with a pronoun (zame), its ending alone (form), or
 * nothing could (unresolved: the sentence's words fit none of the readings the dictionary has).
 */
export type CaseKind =
  | 'preposition' | 'negation' | 'quantity' | 'number' | 'verb' | 'predicate' | 'copula' | 'predicative' | 'subject' | 'object'
  | 'address' | 'attribute' | 'time' | 'phrase' | 'fused' | 'form' | 'unresolved'

/** A case and number the sentence leaves possible ([gender]: an adjective's, a pronoun's), and what it would be ([as]). */
export type CaseOption = { case: CaseKey; number: NumberKey; gender?: GenderKey; as?: CaseKind }

/** Why a reading of the form is out here. */
export type OutReason = 'dual' | 'preposition' | 'needs-preposition' | 'needs-trigger' | 'agreement' | 'subject-taken' | 'object-taken' | 'no-object' | 'negation' | 'governed' | 'phrase'
export type RuledOut = { case: CaseKey; number: NumberKey; why: OutReason }

/** A word of the sentence the lens points at: its index among the words, as written, and where it is in the sentence. */
export type WordRef = { i: number; word: string; start: number; end: number; lemma?: string }

/** How sure: one reading left (decided), one preferred (likely: the singular, the sentence's order), or several (ambiguous). */
export type CaseSure = 'decided' | 'likely' | 'ambiguous'

/** What the lens says of one word: its readings, the one the sentence picks and why, or the ones it leaves. */
export type WordCase = WordRef & {
  /** Every reading the form can have (the forms book's), whatever the sentence. */
  readings: FormReading[]
  /** The case the sentence gives it: the only one left, or the likely one; null when it can't tell. */
  pick: CaseOption | null
  sure: CaseSure
  kind: CaseKind
  /** The word that decided ([kind] says how); none for its role alone, its ending, or a fixed phrase without one. */
  trigger?: WordRef
  /** What the preposition means here: where to (motion), where (rest), when (time), with, from, for, about … */
  sense?: string
  /** The grammar book's page that explains it. */
  rule: string
  /** The readings left, the likely one first (more than one: ambiguous, or likely). */
  options: CaseOption[]
  /** The readings of the form the sentence rules out, and why. */
  out: RuledOut[]
  /** An adjective's, a possessive's or a number's noun: it agrees with it (same case and number, its gender). */
  agrees?: WordRef
  /** The pick rests on a form the dictionary doesn't list: read by the regular endings of its lemma. */
  derived?: true
}

/** A word that decides other words' case (a preposition, a negation, a word of quantity, a verb): which, and to what. */
export type Governs = WordRef & { words: (WordRef & { case: CaseKey })[] }

/** A sentence through the lens: each word with a case, and the words that decide one. */
export type LineCases = { words: WordCase[]; governs: Governs[] }

/** A name of the village (a villager's, the learner's) and its forms by case: Mojca, Mojce, Mojci, Mojco … */
export type NameForms = { name: string; forms: Partial<Record<CaseKey, string>> }

/** The village's people as the lens declines their names: [given] names, each with its gender when known. */
export function namesOf(people: { given: string; gender?: 'male' | 'female'; forms?: Partial<Record<CaseKey, string>> }[]): NameForms[] {
  return people.flatMap(p => {
    const given = p.given.trim()
    if (!/^\p{Lu}\p{Ll}+$/u.test(given)) return []
    const f = sloveneName(given, p.gender ?? 'male')
    return [{ name: given, forms: { nom: given, gen: f.gen, dat: f.dat, acc: f.acc, loc: f.loc, ins: f.ins, ...(p.forms ?? {}) } }]
  })
}

// --- the words of a sentence -------------------------------------------------------------------------------------

type VerbKind = 'pres' | 'neg' | 'fut' | 'past' | 'imp' | 'inf'
type VerbReading = { lemma: string; kind: VerbKind; person?: 1 | 2 | 3; number?: NumberKey; gender?: GenderKey; weak?: true }
type Nominal = FormReading & { person?: 1 | 2 | 3; strong: boolean }

/** A word of the sentence and what the lens knows of it. */
type Tok = {
  i: number
  raw: string
  form: string
  start: number
  end: number
  clause: number
  /** Its readings as a noun, adjective, possessive, pronoun, number or name. */
  nominal: Nominal[]
  verbs: VerbReading[]
  /** Its other parts of speech (adv, conj, particle, prep …), by the dictionary. */
  other: Set<string>
  prep?: PrepositionEntry
  negation: boolean
  quantity: boolean
  /** A number from five on (pet, 12): what it counts is in the genitive plural. */
  count: boolean
  /** A noun of measure (kos, kozarec): what it measures is in the genitive. */
  measure: boolean
  /** A form of biti (je, so, bo): a verb, never a pronoun. */
  biti: boolean
  /** The reflexive clitics se and si: a part of their verb, no case of their own here. */
  clitic: boolean
  dual: boolean
  fused?: CaseTriggers['fused'][number]
  /** The table's fixed phrase the word is part of (lahko noč). */
  phrase?: CaseTriggers['phrases'][number]
  /** What the token is in the sentence: a word with a case, a verb, a preposition, or something else. */
  role: 'nominal' | 'verb' | 'prep' | 'other'
}

/** A noun phrase: its words (modifiers first, the head last), and the readings they agree in. */
type Phrase = { toks: Tok[]; head: Tok; options: CaseOption[] }

/** An option while the clause decides: what makes it so ([by]: the word; [rule], [sense]). */
type Option = CaseOption & { by?: Tok; rule?: string; sense?: string; likely?: true }

/** What the clause makes of a phrase. */
type PhraseState = {
  phrase: Phrase
  options: Option[]
  out: RuledOut[]
  /** Nothing later changes it (a preposition decided it, a negation …). */
  final: boolean
  /** In a coordination (Jan in Mojca): a subject or an object of two. */
  coordinated: boolean
  /** The sentence fits none of the readings: what the dictionary has, unresolved. */
  unresolved: boolean
}

/** What the lens knows of a clause: its verbs, its negation, the subjects its verbs agree with. */
type Clause = {
  toks: Tok[]
  verbs: Tok[]
  hasSe: boolean
  contrast: boolean
  dual: boolean
  negated: Tok | null
  /** A negated biti without a verb of its own: there isn't (ni kruha): what isn't there is in the genitive. */
  existential: boolean
  copular: boolean
  /** The persons, numbers and genders the clause's finite verbs agree with. */
  subjects: { person?: 1 | 2 | 3; number?: NumberKey; gender?: GenderKey }[]
  /** A verb of the clause that can take an object in the accusative. */
  objectVerb?: Tok
  /** No verb at all: a call, a greeting, an answer of a word or two. */
  verbless: boolean
  asking: boolean
  finite: { tok: Tok; r: VerbReading }[]
}

const NOMINAL_POS = new Set(['noun', 'adj', 'det', 'pron', 'num', 'name'])
const MODIFIER_POS = new Set(['adj', 'det', 'num'])
/**
 * Words that start a clause of their own: a comma isn't always written before them in a short line. The relative
 * adverbs among them (kjer, kamor, koder …) are never a word with a case there, though one is a noun's form too
 * (koder: wherever, and a curl).
 */
const CLAUSE_WORDS = new Set(['ki', 'ko', 'ker', 'če', 'ampak', 'toda', 'vendar', 'dokler', 'preden', 'čeprav', 'temveč', 'kjer', 'kamor', 'koder', 'kod', 'odkoder', 'dokoder', 'kadar'])
const COORDINATION = new Set(['in', 'ali', 'ter'])
/** Words that ask for something, after which a verbless line names what is wanted (Kavo, prosim: the accusative). */
const ASKING = new Set(['prosim', 'hvala'])
const PUNCTUATION = /[,.!?;:—–…()"«»„“”]/
/** The numbers as an hour says them (ob šestih: Wiktionary's šesti), and the ordinals. */
const HOURS = new Set(['en', 'eden', 'dva', 'trije', 'štirje', 'pet', 'šest', 'sedem', 'osem', 'devet', 'deset', 'enajst', 'dvanajst', 'prvi', 'drugi', 'tretji', 'četrti', 'peti', 'šesti', 'sedmi', 'osmi', 'deveti', 'deseti', 'enajsti', 'dvanajsti'])
/** A word ending as an adjective does (delovno, Vipavske, Tonetovo, pastirski): one between a preposition and its noun is read so. */
const ADJECTIVE_LIKE = /(?:ega|emu|em|im|ih|imi|ima)$|(?:n|sk|šk|ov|ev|in|ast|at|iv|ljiv|ičn)[aoei]$/
/** An adjective's suffix and ending (Tonetovo, Vipavske, Mickinega): no name, though it is written with a capital. */
const ADJECTIVE_SUFFIX = /(?:sk|šk|ov|ev|in|ičn|ljiv)(?:[aoei]|ega|emu|em|im|ih|imi|ima)$/
/** English words of a gloss that make a noun a person (or an animal one calls): whom a call can be to. */
const PERSON = /\b(man|woman|boy|girl|child|children|kid|teacher|mother|mum|mom|father|dad|grand\w*|friend|person|people|neighbou?r|sister|brother|aunt|uncle|son|daughter|king|queen|lady|gentleman|sir|madam|miller|smith|blacksmith|hunter|shepherd|farmer|baker|priest|doctor|guest|wife|husband|master|mistress|pupil|student|lad|lass|fellow|dog|cat|horse|beekeeper|winemaker|innkeeper|landlady|landlord|neighbour|cousin|godfather|godmother|uncle|elder|old man|old woman|stranger|traveller|traveler|pedlar|peddler|merchant|knight|soldier|witch|fairy)\b/

const CASE_NAMES: Record<string, CaseKey> = { nominative: 'nom', genitive: 'gen', dative: 'dat', accusative: 'acc', locative: 'loc', instrumental: 'ins' }
const NUMBER_NAMES: Record<string, NumberKey> = { singular: 'sg', plural: 'pl', dual: 'du' }
const GENDER_NAMES: Record<string, GenderKey> = { masculine: 'm', feminine: 'f', neuter: 'n' }
/** Words of a grammar string that change nothing of a case reading. */
const NEUTRAL_WORDS = new Set(['animate', 'inanimate', 'unstressed', 'stressed', 'indefinite', 'definite', 'short', 'long', 'form', 'clitic', 'comparative'])

/**
 * The case readings one of the dictionary's grammar strings says ("genitive feminine singular; nominative/accusative
 * feminine plural; accusative masculine plural"): each case with its number and genders ([genders]: the lemma's, for
 * a noun's string that says none). "any case, singular" reads as each case; a string without a number (a pronoun's
 * "genitive/accusative") as the numbers [numberless] says. A reading of a person, a participle or a verbal noun is none.
 */
export function caseReadingsOf(grammar: string | null, genders: GenderKey[], numberless: NumberKey[] = ['sg']): { case: CaseKey; number: NumberKey; genders: GenderKey[] }[] {
  if (!grammar) return []
  const out: { case: CaseKey; number: NumberKey; genders: GenderKey[] }[] = []
  for (const part of grammar.replace(/\([^)]*\)/g, ' ').split(';')) {
    let text = part.replace(/,/g, ' ').trim()
    const cases: CaseKey[] = []
    if (/^any case\b/.test(text)) {
      cases.push(...CASE_KEYS)
      text = text.replace(/^any case\s*/, '')
    }
    const nums: NumberKey[] = []
    const gens: GenderKey[] = []
    let junk = false
    for (const w of text.split(/\s+/).filter(Boolean)) {
      for (const a of w.split('/')) {
        if (CASE_NAMES[a]) cases.push(CASE_NAMES[a])
        else if (NUMBER_NAMES[a]) nums.push(NUMBER_NAMES[a])
        else if (GENDER_NAMES[a]) gens.push(GENDER_NAMES[a])
        else if (a !== 'vocative' && !NEUTRAL_WORDS.has(a)) junk = true
      }
    }
    if (junk || !cases.length) continue
    const numbers = nums.length ? nums : numberless
    for (const c of new Set(cases)) for (const n of new Set(numbers)) out.push({ case: c, number: n, genders: gens.length ? [...new Set(gens)] : genders })
  }
  return out
}

// --- the verbs of the table -------------------------------------------------------------------------------------

/** Endings of the present by person and number, after the present's stem (pomaga-m: pomaga). */
const PRESENT_ENDINGS: [string, 1 | 2 | 3, NumberKey][] = [
  ['m', 1, 'sg'], ['š', 2, 'sg'], ['', 3, 'sg'], ['va', 1, 'du'], ['ta', 2, 'du'], ['ta', 3, 'du'], ['mo', 1, 'pl'], ['te', 2, 'pl'],
]

/**
 * A verb's forms by the table ([v]: its infinitive and first person singular): the present's persons (pomagam …
 * pomagajo), the l-form (pomagal, pomagala …), the imperative (pomagaj, pomagajte) and the infinitive, with the forms the
 * table gives on top (read by their ending); each with what it says of the subject (person, number, gender).
 */
export function verbFormsOf(v: VerbForms): { form: string; reading: VerbReading }[] {
  const out: { form: string; reading: VerbReading }[] = []
  const add = (form: string, reading: Omit<VerbReading, 'lemma'>) => out.push({ form, reading: { lemma: v.lemma, ...reading } })
  const stem = v.present.endsWith('m') ? v.present.slice(0, -1) : v.present
  for (const [end, person, number] of PRESENT_ENDINGS) add(stem + end, { kind: 'pres', person, number })
  // the third person plural: -ajo, -ijo, -ejo (pišejo), or -o after an e-stem (pišo)
  add(`${stem}jo`, { kind: 'pres', person: 3, number: 'pl' })
  if (stem.endsWith('e')) add(`${stem.slice(0, -1)}o`, { kind: 'pres', person: 3, number: 'pl' })
  add(v.lemma, { kind: 'inf' })
  if (v.lemma.endsWith('ti')) {
    const base = v.lemma.slice(0, -2)
    for (const [end, gender, number] of [['l', 'm', 'sg'], ['la', 'f', 'sg'], ['lo', 'n', 'sg'], ['li', 'm', 'pl'], ['le', 'f', 'pl'], ['la', 'm', 'du'], ['li', 'f', 'du']] as const)
      add(base + end, { kind: 'past', gender, number })
  }
  // the imperative: an a-stem's -j (pomagaj), an e- or i-stem's -i (piši, vidi)
  const imp = stem.endsWith('a') ? `${stem}j` : /[ei]$/.test(stem) ? `${stem.slice(0, -1)}i` : null
  if (imp) {
    add(imp, { kind: 'imp', person: 2, number: 'sg' })
    add(`${imp}te`, { kind: 'imp', person: 2, number: 'pl' })
    add(`${imp}mo`, { kind: 'imp', person: 1, number: 'pl' })
  }
  for (const f of v.forms ?? []) {
    if (out.some(o => o.form === f)) continue
    if (/l[aoie]?$/.test(f)) add(f, { kind: 'past', ...(f.endsWith('l') ? { gender: 'm', number: 'sg' } : f.endsWith('la') ? { gender: 'f', number: 'sg' } : f.endsWith('lo') ? { gender: 'n', number: 'sg' } : f.endsWith('le') ? { gender: 'f', number: 'pl' } : { gender: 'm', number: 'pl' }) } as Omit<VerbReading, 'lemma'>)
    else if (/(jte|ite)$/.test(f)) add(f, { kind: 'imp', person: 2, number: 'pl' })
    else if (/(jmo|imo)$/.test(f) && !/emo$/.test(f)) add(f, { kind: 'imp', person: 1, number: 'pl' })
    else if (/jo$/.test(f) || /[^l]o$/.test(f)) add(f, { kind: 'pres', person: 3, number: 'pl' })
    else if (/[^aeiou]i$|j$/.test(f)) add(f, { kind: 'imp', person: 2, number: 'sg' })
    else if (f.endsWith('š')) add(f, { kind: 'pres', person: 2, number: 'sg' })
    else if (f.endsWith('mo')) add(f, { kind: 'pres', person: 1, number: 'pl' })
    else if (f.endsWith('te')) add(f, { kind: 'pres', person: 2, number: 'pl' })
    else if (f.endsWith('va')) add(f, { kind: 'pres', person: 1, number: 'du' })
    else if (f.endsWith('ta')) add(f, { kind: 'pres', number: 'du' })
    else if (f.endsWith('m')) add(f, { kind: 'pres', person: 1, number: 'sg' })
    else add(f, { kind: 'pres', person: 3, number: 'sg' })
  }
  return out
}

// --- the regular endings ------------------------------------------------------------------------------------------

/** A noun's paradigm by its class: each ending after the stem, and the slot it is. */
type Endings = [string, CaseKey, NumberKey][]
const MASCULINE: Endings = [
  ['a', 'gen', 'sg'], ['u', 'dat', 'sg'], ['u', 'loc', 'sg'], ['om', 'ins', 'sg'],
  ['a', 'nom', 'du'], ['a', 'acc', 'du'], ['ov', 'gen', 'du'], ['oma', 'dat', 'du'], ['ih', 'loc', 'du'], ['oma', 'ins', 'du'],
  ['i', 'nom', 'pl'], ['ov', 'gen', 'pl'], ['om', 'dat', 'pl'], ['e', 'acc', 'pl'], ['ih', 'loc', 'pl'], ['i', 'ins', 'pl'],
]
const FEMININE: Endings = [
  ['a', 'nom', 'sg'], ['e', 'gen', 'sg'], ['i', 'dat', 'sg'], ['o', 'acc', 'sg'], ['i', 'loc', 'sg'], ['o', 'ins', 'sg'],
  ['i', 'nom', 'du'], ['i', 'acc', 'du'], ['ama', 'dat', 'du'], ['ah', 'loc', 'du'], ['ama', 'ins', 'du'],
  ['e', 'nom', 'pl'], ['am', 'dat', 'pl'], ['e', 'acc', 'pl'], ['ah', 'loc', 'pl'], ['ami', 'ins', 'pl'],
]
const NEUTER: Endings = [
  ['a', 'gen', 'sg'], ['u', 'dat', 'sg'], ['u', 'loc', 'sg'], ['om', 'ins', 'sg'],
  ['i', 'nom', 'du'], ['i', 'acc', 'du'], ['oma', 'dat', 'du'], ['ih', 'loc', 'du'], ['oma', 'ins', 'du'],
  ['a', 'nom', 'pl'], ['om', 'dat', 'pl'], ['a', 'acc', 'pl'], ['ih', 'loc', 'pl'], ['i', 'ins', 'pl'],
]
/** A feminine noun in a consonant (vas, noč, stvar): its i-stem endings after the lemma. */
const I_STEM: Endings = [
  ['i', 'gen', 'sg'], ['i', 'dat', 'sg'], ['i', 'loc', 'sg'], ['jo', 'ins', 'sg'],
  ['i', 'nom', 'du'], ['i', 'acc', 'du'], ['ema', 'dat', 'du'], ['eh', 'loc', 'du'], ['ema', 'ins', 'du'],
  ['i', 'nom', 'pl'], ['i', 'gen', 'pl'], ['em', 'dat', 'pl'], ['i', 'acc', 'pl'], ['eh', 'loc', 'pl'], ['mi', 'ins', 'pl'],
]
/** An adjective's endings after its stem, each with the genders it is (the masculine nominative is the lemma itself). */
const ADJECTIVE: [string, CaseKey, NumberKey, GenderKey[]][] = [
  ['ega', 'gen', 'sg', ['m', 'n']], ['emu', 'dat', 'sg', ['m', 'n']], ['ega', 'acc', 'sg', ['m']], ['em', 'loc', 'sg', ['m', 'n']], ['im', 'ins', 'sg', ['m', 'n']],
  ['a', 'nom', 'sg', ['f']], ['e', 'gen', 'sg', ['f']], ['i', 'dat', 'sg', ['f']], ['o', 'acc', 'sg', ['f']], ['i', 'loc', 'sg', ['f']], ['o', 'ins', 'sg', ['f']],
  ['o', 'nom', 'sg', ['n']], ['o', 'acc', 'sg', ['n']],
  ['i', 'nom', 'pl', ['m']], ['e', 'nom', 'pl', ['f']], ['a', 'nom', 'pl', ['n']], ['ih', 'gen', 'pl', ['m', 'f', 'n']], ['im', 'dat', 'pl', ['m', 'f', 'n']],
  ['e', 'acc', 'pl', ['m', 'f']], ['a', 'acc', 'pl', ['n']], ['ih', 'loc', 'pl', ['m', 'f', 'n']], ['imi', 'ins', 'pl', ['m', 'f', 'n']],
  ['a', 'nom', 'du', ['m']], ['i', 'nom', 'du', ['f', 'n']], ['ih', 'gen', 'du', ['m', 'f', 'n']], ['ima', 'dat', 'du', ['m', 'f', 'n']],
  ['a', 'acc', 'du', ['m']], ['i', 'acc', 'du', ['f', 'n']], ['ih', 'loc', 'du', ['m', 'f', 'n']], ['ima', 'ins', 'du', ['m', 'f', 'n']],
]
/** The endings of a feminine noun in a consonant (-ost, a vowel's fleeting e: misel, pesem, cerkev): not masculine. */
const FEMININE_CONSONANT = /(ost|ezen|zen|sel|sem|kev|tev|ev|ad|ast|ež|oš|ič|ez|al|en)$/
/** After a soft consonant the o of an ending is an e (z nožem, s čajem, s poljem), and -ov is -ev. */
const softened = (stem: string, end: string) => (/[cčjšž]$/.test(stem) && /^o/.test(end) ? `e${end.slice(1)}` : end)

// --- the lens -----------------------------------------------------------------------------------------------------

/** What the lens needs: the dictionary, the forms book and the trigger table of the language (Slovene's). */
export type CaseLensSources = { dict: Lexicon; book: FormsBook; table: CaseTriggers }

type VerbIndex = Map<string, { lemma: string; se: boolean; reading: VerbReading }[]>

/**
 * The case lens of a language: its table indexed (the prepositions, the verbs' forms, the pronouns' forms), the
 * dictionary's readings of each form read once and kept.
 */
export class CaseLens {
  private readonly lang = 'sl'
  private readonly preps = new Map<string, PrepositionEntry>()
  private readonly negations: Set<string>
  private readonly beforeVerb: Set<string>
  private readonly contrast: Set<string>
  private readonly quantity: Set<string>
  private readonly measures: Set<string>
  private readonly numbers: Set<string>
  private readonly dualWords: Set<string>
  private readonly timeNouns: Set<string>
  /** The table's verbs that govern a case, by their forms and by their lemmas (pomagati: the dative). */
  private readonly governing = new Map<string, VerbEntry[]>()
  private readonly governingLemmas = new Map<string, VerbEntry[]>()
  private readonly motion: VerbIndex
  private readonly rest: VerbIndex
  private readonly copulas: VerbIndex
  private readonly transitive: VerbIndex
  private readonly intransitive: VerbIndex
  /** Every verb form the table makes, with what it says of its subject. */
  private readonly tableVerbs = new Map<string, VerbReading[]>()
  private readonly predicates = new Map<string, { case: CaseKey; rule: string }>()
  private readonly pronouns = new Map<string, Nominal[]>()
  private readonly pronounLemmas: Set<string>
  private readonly fused = new Map<string, CaseTriggers['fused'][number]>()
  /** The words said of the subject (rad, rada …), by their forms, and their lemmas. */
  private readonly predicatives = new Map<string, Nominal[]>()
  private readonly predicativeLemmas = new Set<string>()
  /** The forms book's verbs (hand-checked) by their forms, and the lemmas it has tables of. */
  private readonly bookVerbs = new Map<string, VerbReading[]>()
  private readonly tabled: Set<string>
  private readonly cache = new Map<string, { nominal: Nominal[]; verbs: VerbReading[]; other: Set<string> }>()
  /** The nouns of measure's forms by their regular endings (kilogram: kilograma, kilogramov …), for those the dictionary lacks. */
  private readonly measureForms = new Map<string, Nominal[]>()

  constructor(readonly s: CaseLensSources) {
    const t = s.table
    for (const p of t.prepositions) for (const w of p.words) this.preps.set(w, p)
    this.negations = new Set(t.negation.words)
    this.beforeVerb = new Set(t.negation.before_verb)
    this.contrast = new Set(t.negation.contrast)
    this.quantity = new Set(t.quantity.words)
    this.measures = new Set(t.quantity.measures)
    this.numbers = new Set(t.quantity.numbers)
    this.dualWords = new Set(t.dual_words)
    this.timeNouns = new Set(t.time.nouns)
    const remember = (form: string, r: VerbReading) => {
      const list = this.tableVerbs.get(form) ?? []
      if (!list.some(x => x.lemma === r.lemma && x.kind === r.kind && x.person === r.person && x.number === r.number && x.gender === r.gender)) list.push(r)
      this.tableVerbs.set(form, list)
    }
    for (const entry of t.verbs) {
      for (const { form, reading } of verbFormsOf(entry)) {
        this.governing.set(form, [...(this.governing.get(form) ?? []), entry])
        remember(form, reading)
      }
      this.governingLemmas.set(entry.lemma, [...(this.governingLemmas.get(entry.lemma) ?? []), entry])
    }
    const index = (list: VerbForms[]): VerbIndex => {
      const m: VerbIndex = new Map()
      for (const v of list) {
        for (const { form, reading } of verbFormsOf(v)) {
          m.set(form, [...(m.get(form) ?? []), { lemma: v.lemma, se: !!v.se, reading }])
          remember(form, reading)
        }
      }
      return m
    }
    this.motion = index(t.motion)
    this.rest = index(t.rest)
    this.copulas = index(t.copulas)
    this.transitive = index(t.transitive)
    this.intransitive = index(t.intransitive)
    for (const p of t.predicates) for (const w of p.words) this.predicates.set(w, { case: p.case, rule: p.rule })
    for (const p of t.pronouns) {
      for (const [c, forms] of Object.entries(p.forms)) {
        for (const f of forms ?? []) {
          const list = this.pronouns.get(f) ?? []
          list.push({ lemma: p.lemma, pos: 'pron', case: c as CaseKey, number: p.number, genders: p.genders, strong: true, ...(p.person ? { person: p.person } : {}) })
          this.pronouns.set(f, list)
        }
      }
    }
    this.pronounLemmas = new Set(t.pronouns.map(p => p.lemma))
    for (const f of t.fused) this.fused.set(f.form, f)
    for (const p of t.predicatives) {
      this.predicativeLemmas.add(p.lemma)
      for (const f of p.forms) this.predicatives.set(f.form, [...(this.predicatives.get(f.form) ?? []), { lemma: p.lemma, pos: 'adj', case: 'nom', number: f.number, genders: f.genders, strong: true }])
    }
    for (const lemma of t.quantity.measures) {
      // its class by its ending: -a feminine, -o or -e neuter, a consonant masculine (its fleeting e: kozarec, kozarca)
      const [gender, stem, endings]: [GenderKey, string, Endings] = lemma.endsWith('a') ? ['f', lemma.slice(0, -1), FEMININE]
        : /[oe]$/.test(lemma) ? ['n', lemma.slice(0, -1), NEUTER]
        : ['m', lemma.replace(/e([cklnr])$/, '$1'), MASCULINE]
      const put = (form: string, c: CaseKey, n: NumberKey) => this.measureForms.set(form, [...(this.measureForms.get(form) ?? []), { lemma, pos: 'noun', case: c, number: n, genders: [gender], strong: false, derived: true }])
      if (gender === 'm') {
        put(lemma, 'nom', 'sg')
        put(lemma, 'acc', 'sg')
      } else if (gender === 'n') {
        put(lemma, 'nom', 'sg')
        put(lemma, 'acc', 'sg')
      }
      for (const [end, c, n] of endings) put(stem + (gender === 'f' ? end : softened(stem, end)), c, n)
      if (gender === 'f' || gender === 'n') put(gender === 'f' ? stem.replace(/([^aeiou])([^aeiou])$/, '$1e$2') : stem, 'gen', 'pl')
    }
    const book = s.book.current()
    this.tabled = new Set(book.tables.map(x => `${this.norm(x.lemma)}|${x.pos}`))
    for (const table of book.tables) {
      if (table.pos !== 'verb') continue
      for (const [key, v] of [...Object.entries(table.forms), ...Object.entries(table.also ?? {})]) {
        for (const f of typeof v === 'string' ? [v] : (v ?? [])) {
          const r = verbReadingOfSlot(table.lemma, key)
          if (r) this.bookVerbs.set(this.norm(f), [...(this.bookVerbs.get(this.norm(f)) ?? []), r])
        }
      }
      this.bookVerbs.set(this.norm(table.lemma), [...(this.bookVerbs.get(this.norm(table.lemma)) ?? []), { lemma: table.lemma, kind: 'inf' }])
    }
  }

  private norm = (s: string) => normalizeForm(s, this.lang)

  /** The dictionary's readings of [form]: as a word with a case, as a verb, and its other parts of speech. */
  private lexical(form: string): { nominal: Nominal[]; verbs: VerbReading[]; other: Set<string> } {
    const had = this.cache.get(form)
    if (had) return had
    const nominal: Nominal[] = []
    const verbs: VerbReading[] = [...(this.bookVerbs.get(form) ?? []), ...(this.tableVerbs.get(form) ?? [])]
    const other = new Set<string>()
    const pron = this.pronouns.get(form)
    for (const r of this.s.dict.grammarReadings(form)) {
      const key = `${this.norm(r.lemma)}|${r.pos}`
      if (r.pos === 'verb') {
        // a verb the forms book has a table of: the table's forms only (Wiktionary's merge homographs: biti, to beat)
        if (this.tabled.has(key)) continue
        for (const k of slotsOf('verb', r.grammar).keys) {
          const v = verbReadingOfSlot(r.lemma, k)
          if (v) verbs.push(v)
        }
        if ((r.grammar && /\binfinitive\b/.test(r.grammar)) || this.norm(r.lemma) === form) verbs.push({ lemma: r.lemma, kind: 'inf' })
        continue
      }
      if (!NOMINAL_POS.has(r.pos)) {
        other.add(r.pos)
        continue
      }
      // the table's personal pronouns are hand-checked: Wiktionary's readings of them are left out
      if (r.pos === 'pron' && this.pronounLemmas.has(this.norm(r.lemma))) continue
      // a word of two letters Wiktionary has as a noun (pi, ti: a letter, a note) is no reading worth a case
      if (r.pos === 'noun' && [...this.norm(r.lemma)].length <= 2) continue
      const genders: GenderKey[] = r.gender ? [r.gender] : ['m', 'f', 'n']
      const strong = r.source !== 'wiktionary'
      // (a number's readings without one: dva, oba the dual; trije, štirje and from five the plural)
      const numberless: NumberKey[] = r.pos === 'pron' ? pronounNumbers(r.lemma)
        : r.pos === 'num' && /^(dva|dve|oba|obe)$/.test(this.norm(r.lemma)) ? ['du']
        : r.pos === 'num' && (this.numbers.has(this.norm(r.lemma)) || /^(trije|tri|štirje|štiri)$/.test(this.norm(r.lemma))) ? ['pl'] : ['sg']
      for (const c of caseReadingsOf(r.grammar, genders, numberless)) nominal.push({ lemma: r.lemma, pos: r.pos, ...c, strong })
      // the dictionary form of a noun listed without its grammar (Wiktionary's lemma alone, a pack's word): its nominative,
      // and a thing's accusative too (by the endings)
      if (!r.grammar && r.pos === 'noun' && this.norm(r.lemma) === form) {
        nominal.push({ lemma: r.lemma, pos: r.pos, case: 'nom', number: 'sg', genders, strong })
        if (r.gender === 'n' || (r.gender === 'm' && /[^aeiou]$/.test(form))) nominal.push({ lemma: r.lemma, pos: r.pos, case: 'acc', number: 'sg', genders, strong: false, derived: true })
      }
    }
    nominal.push(...(pron ?? []))
    // an adjective's accusative masculine singular, which Wiktionary's table gives as the nominative (inanimate) or the
    // genitive (animate) and never names
    for (const r of [...nominal]) {
      if (!MODIFIER_POS.has(r.pos) || r.number !== 'sg' || !r.genders.includes('m')) continue
      if ((r.case === 'nom' || r.case === 'gen') && !nominal.some(x => x.lemma === r.lemma && x.pos === r.pos && x.case === 'acc' && x.number === 'sg' && x.genders.includes('m')))
        nominal.push({ ...r, case: 'acc', genders: ['m'] })
    }
    // rad (rada, rado, radi, rade: glad, to like): said of the subject, the nominative only, nothing else it may read as
    const said = this.predicatives.get(form)
    if (said) nominal.splice(0, nominal.length, ...said)
    // a noun the dictionary has, in a form it doesn't list: its regular endings (marked)
    if (!nominal.length && !verbs.length && !other.size && [...form].length >= 4) nominal.push(...this.byEndings(form))
    const out = { nominal: dedupe(nominal), verbs: dedupeVerbs(verbs), other }
    this.cache.set(form, out)
    return out
  }

  /**
   * [form] as a form of a noun the dictionary (or the supplement) has, by the regular endings of its class: šotorom as
   * šotor's instrumental, tržnice as tržnica's genitive or plural. A masculine stem is the lemma or the lemma without its
   * fleeting e (kozarc: kozarec). The genitive plural and dual of a feminine or neuter noun (often with a vowel put in:
   * sester) aren't read; nor a masculine noun's accusative singular (a person's or an animal's is the genitive's).
   */
  private byEndings(form: string): Nominal[] {
    const out: Nominal[] = []
    const tried = new Set<string>()
    // a noun the dictionary has, with its gender; one it has without a gender: masculine in a consonant (zaboj), feminine
    // in -a, neuter in -o (a feminine in a consonant, -ost, misel, gets none)
    const nounOf = (lemma: string) => {
      const gender = this.s.dict.genderOf(lemma, 'noun')
      if (gender) return { lemma, gender }
      if (gender === null) {
        if (lemma.endsWith('a')) return { lemma, gender: 'f' as GenderKey }
        if (lemma.endsWith('o')) return { lemma, gender: 'n' as GenderKey }
        if (/[^aeiou]$/.test(lemma) && !FEMININE_CONSONANT.test(lemma)) return { lemma, gender: 'm' as GenderKey }
      }
      return undefined
    }
    // an adjective the dictionary has (hiter: hitrih, hitri; its stem without the fleeting e)
    for (const [end, c, n, genders] of ADJECTIVE) {
      if (!form.endsWith(end) || [...form].length - end.length < 2) continue
      const stem = form.slice(0, -end.length)
      for (const lemma of new Set([stem, stem.replace(/([^aeiou])([^aeiou])$/, '$1e$2'), `${stem}i`])) {
        const r = this.s.dict.grammarReadings(lemma).find(x => (x.pos === 'adj' || x.pos === 'det' || (x.pos === 'pron' && !this.pronounLemmas.has(lemma))) && this.norm(x.lemma) === lemma)
        if (r) out.push({ lemma, pos: r.pos === 'pron' ? 'det' : r.pos, case: c, number: n, genders, strong: false, derived: true })
      }
    }
    // a feminine noun in a consonant (brv: brvi, brvjo)
    for (const [end, c, n] of I_STEM) {
      if (!form.endsWith(end)) continue
      const lemma = form.slice(0, -end.length)
      if ([...lemma].length >= 2 && this.s.dict.genderOf(lemma, 'noun') === 'f' && /[^aeiou]$/.test(lemma)) out.push({ lemma, pos: 'noun', case: c, number: n, genders: ['f'], strong: false, derived: true })
    }
    const add = (lemma: string, gender: GenderKey, stem: string, endings: Endings) => {
      for (const [end, c, n] of endings) if (stem + (gender === 'f' ? end : softened(stem, end)) === form) out.push({ lemma, pos: 'noun', case: c, number: n, genders: [gender], strong: false, derived: true })
    }
    for (const end of ['ami', 'ama', 'oma', 'ema', 'om', 'em', 'ov', 'ev', 'ih', 'ah', 'am', 'a', 'e', 'i', 'o', 'u']) {
      if (!form.endsWith(end) || [...form].length - end.length < 2) continue
      const stem = form.slice(0, -end.length)
      for (const lemma of [stem, stem.replace(/([^aeiou])([^aeiou])$/, '$1e$2')]) {
        if (tried.has(`m|${lemma}`)) continue
        tried.add(`m|${lemma}`)
        const n = nounOf(lemma)
        if (n?.gender === 'm' && /[^aeiou]$/.test(lemma)) add(n.lemma, 'm', stem, MASCULINE)
      }
      for (const [lemma, gender, endings] of [[`${stem}a`, 'f', FEMININE], [`${stem}o`, 'n', NEUTER], [`${stem}e`, 'n', NEUTER]] as const) {
        if (tried.has(`${gender}|${lemma}`)) continue
        tried.add(`${gender}|${lemma}`)
        const n = nounOf(lemma)
        if (n?.gender === gender) add(n.lemma, gender, stem, endings)
      }
    }
    return out
  }

  /** The words of [line] with what the lens knows of each (its readings, whether it is a preposition, a negation …). */
  private tokens(line: string, names: NameForms[]): Tok[] {
    const out: Tok[] = []
    let clause = 0
    let last = 0
    for (const m of line.matchAll(/[\p{L}\p{M}]+|\d+/gu)) {
      const raw = m[0]
      const start = m.index ?? 0
      if (out.length && PUNCTUATION.test(line.slice(last, start))) clause++
      const form = this.norm(raw)
      if (out.length && CLAUSE_WORDS.has(form)) clause++
      last = start + raw.length
      const digits = /^\d+$/.test(raw)
      const lex = digits ? { nominal: [] as Nominal[], verbs: [] as VerbReading[], other: new Set<string>() } : this.lexical(form)
      // a name only as one is written, with a capital (stane is stati's, not Stana's)
      // (one the dictionary has only as a name, written small, is the common noun it comes from: mlinar, Mlinar)
      const small = lex.nominal.filter(r => r.pos !== 'name')
      let nominal = /^\p{Lu}/u.test(raw) ? lex.nominal : small.length || lex.verbs.length || lex.other.size ? small : lex.nominal.map(r => ({ ...r, pos: 'noun' }))
      // a villager's or the learner's name: its forms by case, declined as the village says it (not Wiktionary's)
      if (/^\p{Lu}/u.test(raw)) {
        const named = names.flatMap(n => CASE_KEYS.filter(c => n.forms[c] && this.norm(n.forms[c]!) === form).map(c => ({ lemma: n.name, pos: 'name', case: c, number: 'sg' as NumberKey, genders: ['m', 'f'] as GenderKey[], strong: true })))
        if (named.length) nominal = [...nominal.filter(r => r.pos !== 'name'), ...named]
      }
      const biti = lex.verbs.some(v => v.lemma === 'biti')
      const n = digits ? Number(raw) : undefined
      out.push({
        i: out.length, raw, form, start, end: start + raw.length, clause,
        nominal: biti ? [] : nominal, verbs: lex.verbs, other: lex.other,
        prep: this.preps.get(form),
        negation: this.negations.has(form),
        quantity: this.quantity.has(form),
        count: n !== undefined ? !(n % 100 >= 1 && n % 100 <= 4) : this.numbers.has(form) || /^(ena|dva|tri|štiri|pet|šest|sedem|osem|devet)in(dvajset|trideset|štirideset|petdeset|šestdeset|sedemdeset|osemdeset|devetdeset)$/.test(form),
        measure: nominal.some(r => r.pos === 'noun' && this.measures.has(this.norm(r.lemma))),
        biti,
        clitic: form === 'se' || form === 'si',
        dual: this.dualWords.has(form) || lex.verbs.some(v => v.number === 'du' && v.kind !== 'past'),
        fused: this.fused.get(form),
        role: 'other',
      })
    }
    // the table's fixed phrases (na svidenje, lahko noč): their words in the phrase's case, whatever the dictionary has
    for (const entry of this.s.table.phrases) {
      const words = entry.words.split(' ')
      for (let a = 0; a + words.length <= out.length; a++) {
        if (!words.every((w, k) => out[a + k].form === w && out[a + k].clause === out[a].clause)) continue
        for (const t of out.slice(a, a + words.length)) {
          if (t.form === entry.trigger) continue
          t.phrase = entry
          if (!t.nominal.some(r => r.case === entry.case && r.number === entry.number)) t.nominal = [...t.nominal, { lemma: t.form, pos: 'noun', case: entry.case, number: entry.number, genders: ['m', 'f', 'n'], strong: true }]
        }
      }
    }
    // a noun of measure the dictionary lacks (kilogramov, litrov): its regular endings
    for (const t of out) {
      if (t.measure || t.nominal.length) continue
      const m = this.measureForms.get(t.form)
      if (m) {
        t.nominal = m
        t.measure = true
      }
    }
    // a word the dictionary doesn't know, ending as a verb does (počakam, zapojem), or right after ne (ne mara): a verb,
    // weakly, for the subject it says
    for (const t of out) {
      if (t.nominal.length || t.verbs.length || t.other.size || t.prep || /^\p{Lu}/u.test(t.raw) || [...t.form].length < 3) continue
      const guess = guessVerb(t.form) ?? (this.beforeVerb.has(out[t.i - 1]?.form ?? '') ? { lemma: '?', kind: 'pres' as VerbKind, person: 3 as const, number: 'sg' as NumberKey, weak: true as const } : null)
      if (guess) t.verbs = [guess]
    }
    // a capitalized word the dictionary doesn't know (Muriju, Krpan, Lambergarju): a name, its case by its ending, marked
    for (const t of out) {
      if (t.nominal.length || t.verbs.length || t.other.size || t.prep || !/^\p{Lu}\p{Ll}{2,}$/u.test(t.raw) || ADJECTIVE_SUFFIX.test(t.form)) continue
      t.nominal = nameByEnding(t.raw)
    }
    return out
  }

  /**
   * Whether [t] is a verb of [index] (the table's motion, rest … verbs): one of its forms, or a form of its lemma;
   * [noImperative]: by its readings that aren't an imperative (a clause with a subject of its own: Janez sedi na klopi).
   */
  private inIndex(t: Tok, index: VerbIndex, hasSe: boolean, noImperative = false): boolean {
    const readings = noImperative && t.verbs.some(v => v.kind !== 'imp') ? t.verbs.filter(v => v.kind !== 'imp') : t.verbs
    const lemmas = new Set(readings.map(v => this.norm(v.lemma)))
    const own = (index.get(t.form) ?? []).filter(h => !noImperative || h.reading.kind !== 'imp' || !t.verbs.some(v => v.kind !== 'imp'))
    const hits = [...own.filter(h => !lemmas.size || lemmas.has(h.lemma) || !t.verbs.length), ...[...lemmas].flatMap(l => index.get(l) ?? [])]
    return hits.some(h => !h.se || hasSe)
  }

  /** The words of [line] that have a case, each with it and why, and the words that decide one. */
  analyze(line: string, names: NameForms[] = []): LineCases {
    const toks = this.tokens(line, names)
    this.resolveRoles(toks)
    const words: WordCase[] = []
    for (const c of [...new Set(toks.map(t => t.clause))]) words.push(...this.clauseCases(toks, toks.filter(t => t.clause === c)))
    const governs = new Map<number, Governs>()
    for (const w of words) {
      if (!w.trigger || !w.pick || w.agrees || !['preposition', 'negation', 'quantity', 'number', 'verb', 'predicate'].includes(w.kind) || w.trigger.i === w.i) continue
      const g = governs.get(w.trigger.i) ?? { ...w.trigger, words: [] }
      g.words.push({ i: w.i, word: w.word, start: w.start, end: w.end, case: w.pick.case })
      governs.set(w.trigger.i, g)
    }
    return { words: words.sort((a, b) => a.i - b.i), governs: [...governs.values()].sort((a, b) => a.i - b.i) }
  }

  /**
   * The word [word] of [line] (its [nth] among the same words, from 0) through the lens, with the words it decides when
   * it is a trigger; null for a word without a case that decides none.
   */
  at(line: string, word: string, nth = 0, names: NameForms[] = []): ((WordCase & { governs?: Governs }) | (Governs & { governs: Governs })) | null {
    const form = this.norm(word.replace(/^[^\p{L}\d]+|[^\p{L}\p{M}\d]+$/gu, ''))
    const forms = [...line.matchAll(/[\p{L}\p{M}]+|\d+/gu)].map(m => this.norm(m[0]))
    const at = forms.flatMap((t, i) => (t === form ? [i] : []))
    const i = at[Math.min(Math.max(0, nth), at.length - 1)]
    if (i === undefined) return null
    const r = this.analyze(line, names)
    const w = r.words.find(x => x.i === i)
    const g = r.governs.find(x => x.i === i)
    if (w) return g ? { ...w, governs: g } : w
    return g ? { ...g, governs: g } : null
  }

  // --- what each word is -----------------------------------------------------------------------------------------

  /**
   * Each token's part in the sentence: a preposition (before a word with a case), a verb (biti's forms; a word only the
   * dictionary's verb; a verb of the forms book or the table before a weak noun), a word with a case, or neither.
   */
  private resolveRoles(toks: Tok[]) {
    const next = (t: Tok) => (toks[t.i + 1]?.clause === t.clause ? toks[t.i + 1] : undefined)
    const prev = (t: Tok) => (toks[t.i - 1]?.clause === t.clause ? toks[t.i - 1] : undefined)
    // si: biti's (Si lačen?) unless the clause has a verb of its own, then the reflexive's (Umijem si roke)
    for (const t of toks) {
      if (t.form !== 'si') continue
      t.clitic = toks.some(o => o !== t && o.clause === t.clause && !o.biti && o.verbs.some(v => !v.weak || v.kind !== 'inf'))
    }
    for (const t of toks) {
      if (t.clitic) continue
      // a word that starts its clause (ki, ko, ker) is no verb; nor da (that), unless after its subject or a pronoun
      // (Micka da Luki kavo, Kdo mi da kruh?: dati's gives)
      if (CLAUSE_WORDS.has(t.form) && toks[t.i - 1]?.clause !== t.clause) continue
      if (t.form === 'da') {
        const p0 = prev(t)
        if (!p0 || !(p0.role === 'nominal' || p0.nominal.some(r => r.pos === 'pron') || this.beforeVerb.has(p0.form))) continue
      }
      if (t.fused) {
        t.role = 'nominal'
        continue
      }
      const n0 = next(t)
      const caseWord = (x: Tok | undefined) => !!x && (x.nominal.length > 0 || !!x.fused || x.count || x.quantity || (!x.verbs.length && !x.other.size && !x.prep))
      if (t.prep && n0 && (caseWord(n0) || (n0.other.has('adv') && caseWord(next(n0)))) && !t.nominal.some(r => r.strong && r.pos !== 'pron')) {
        t.role = 'prep'
        continue
      }
      const sureVerb = this.bookVerbs.has(t.form) || this.tableVerbs.has(t.form)
      const nominal = t.nominal.length > 0
      if (t.biti) t.role = 'verb'
      else if (nominal && t.verbs.length) {
        const p = prev(t)
        const afterGovernor = !!p && (p.role === 'prep' || p.count || p.quantity || (p.role === 'nominal' && p.nominal.some(r => MODIFIER_POS.has(r.pos)) && agreeing([p, t]).length > 0))
        const strongNoun = t.nominal.some(r => r.strong)
        const named = t.i > 0 && /^\p{Lu}/u.test(t.raw) && t.nominal.some(r => r.pos === 'name')
        if (afterGovernor || named || t.phrase) t.role = 'nominal'
        else if (sureVerb && !strongNoun) t.role = 'verb'
        else if (strongNoun && !sureVerb) t.role = 'nominal'
        else {
          // neither is surer: a verb when the clause has none other, else a word with a case
          const others = toks.filter(o => o !== t && o.clause === t.clause && (o.biti || (o.verbs.some(v => !v.weak) && !o.nominal.length)))
          t.role = others.length ? 'nominal' : 'verb'
        }
      } else if (nominal) t.role = 'nominal'
      else if (t.verbs.length) t.role = t.verbs.every(v => v.kind === 'inf' && this.norm(v.lemma) !== t.form) && (t.other.has('adv') || t.other.has('particle')) ? 'other' : 'verb'
      // an adverb that is an adjective's form too (lepo, dobro, veliko): an adjective only before a noun it agrees with;
      // one that is a noun's form too (potem: pot's, doma: dom's): a noun only after a preposition, a number or its adjective
      // the same for a conjunction, a particle or an interjection (kot: as, and a noun's angle; hvala)
      const functional = ['adv', 'conj', 'particle', 'intj'].some(x => t.other.has(x))
      if (t.role === 'nominal' && functional && !t.phrase && !t.nominal.some(r => (r.strong && r.pos !== 'name') || r.pos === 'pron')) {
        if (t.nominal.every(r => MODIFIER_POS.has(r.pos))) {
          const n = next(t)
          if (!(n && n.nominal.some(r => !MODIFIER_POS.has(r.pos) && r.pos !== 'pron') && agreeing([t, n]).length > 0)) t.role = 'other'
        } else {
          const p = prev(t)
          const governed = !!p && (p.role === 'prep' || p.count || p.quantity || (p.role === 'nominal' && p.nominal.some(r => MODIFIER_POS.has(r.pos)) && agreeing([p, t]).length > 0))
          if (!governed) t.role = 'other'
        }
      }
    }
    // a personal pronoun that is a demonstrative's form too (ti: you, or these): the demonstrative only before its noun
    for (const t of toks) {
      if (t.role !== 'nominal' || !t.nominal.some(r => r.pos === 'pron' && this.pronounLemmas.has(r.lemma)) || !t.nominal.some(r => MODIFIER_POS.has(r.pos))) continue
      const n = next(t)
      const before = !!n && n.nominal.some(r => !MODIFIER_POS.has(r.pos) && r.pos !== 'pron') && agreeing([{ ...t, nominal: t.nominal.filter(r => MODIFIER_POS.has(r.pos)) }, n]).length > 0
      t.nominal = t.nominal.filter(r => (MODIFIER_POS.has(r.pos) ? before : !before || !(r.pos === 'pron' && this.pronounLemmas.has(r.lemma))))
    }
    // a number from five before a noun counts it (pet ovc); a word of quantity before a noun it doesn't agree with as an
    // adjective says how much (veliko borovnic, but veliko hišo): neither is a word with a case of its own
    for (const t of toks) {
      if (!(t.quantity || t.count) || t.role === 'prep') continue
      const n = next(t)
      const adjective = { ...t, nominal: t.nominal.filter(r => MODIFIER_POS.has(r.pos)) }
      if (n?.role === 'nominal' && (t.count || !agreeing([adjective, n]).length)) t.role = 'other'
      else if (t.count && !t.nominal.some(r => r.strong)) t.role = 'other'
    }
  }

  // --- a clause -----------------------------------------------------------------------------------------------------

  private clauseOf(all: Tok[], toks: Tok[]): Clause {
    const verbs = toks.filter(t => t.role === 'verb')
    const hasSe = toks.some(t => t.form === 'se')
    const finite = verbs.flatMap(v => v.verbs.filter(r => r.kind !== 'inf' && r.kind !== 'imp').map(r => ({ tok: v, r })))
    const aux = finite.filter(f => f.tok.biti)
    const lform = finite.filter(f => f.r.kind === 'past' && !f.tok.biti)
    const isCopula = (v: Tok) => v.biti || this.inIndex(v, this.copulas, hasSe)
    // biti as an auxiliary (je šel, bo prišel) with another verb: that verb is the clause's, biti no copula
    const lexical = verbs.filter(v => !isCopula(v))
    const copular = verbs.some(isCopula) && lexical.length === 0
    let negated: Tok | null = null
    for (const t of toks) {
      if (t.negation || (this.beforeVerb.has(t.form) && toks.some(o => o.i > t.i && o.role === 'verb'))) {
        negated = t
        break
      }
    }
    // a negated biti without a verb of its own: there isn't (ni kruha, ne bo toče)
    const existential = !!negated && lexical.length === 0 && (negated.biti || toks.some(t => t.biti && t.i > negated!.i))
    const subjects: Clause['subjects'] = []
    if (lform.length && aux.length) {
      for (const a of aux) for (const l of lform) if (!a.r.number || !l.r.number || a.r.number === l.r.number) subjects.push({ person: a.r.person, number: a.r.number ?? l.r.number, gender: l.r.gender })
    } else {
      for (const f of finite) if (f.r.kind !== 'past') subjects.push({ person: f.r.person, number: f.r.number })
      for (const l of lform) subjects.push({ person: 3, number: l.r.number, gender: l.r.gender })
    }
    // an imperative's subject is who is told (ti, vi): Ti pojdi po poti, Ti drži en konec (drži a present too)
    for (const v of verbs) for (const r of v.verbs) if (r.kind === 'imp') subjects.push({ person: r.person, number: r.number })
    const objectVerb = verbs.find(v => this.takesObject(v, hasSe))
    return {
      toks, verbs, hasSe, contrast: all.some(t => this.contrast.has(t.form)), dual: toks.some(t => t.dual), negated, existential, copular,
      subjects, objectVerb, verbless: verbs.length === 0, asking: all.some(t => ASKING.has(t.form)), finite,
    }
  }

  /** Whether [v] can take an object in the accusative: no copula, not a verb without an object, not one of a dative alone. */
  private takesObject(v: Tok, hasSe: boolean): boolean {
    if (v.biti || this.inIndex(v, this.copulas, hasSe) || this.isIntransitive(v, hasSe)) return false
    const governs = [...(this.governing.get(v.form) ?? []), ...v.verbs.flatMap(r => this.governingLemmas.get(this.norm(r.lemma)) ?? [])].filter(e => !e.se || hasSe)
    return !governs.length || governs.some(e => e.takes.includes('acc'))
  }

  private isIntransitive(v: Tok, hasSe: boolean): boolean {
    const lemmas = new Set([v.form, ...v.verbs.map(r => this.norm(r.lemma))])
    if ((this.governing.get(v.form) ?? []).some(e => e.takes.includes('acc') && (!e.se || hasSe))) return false
    // the form's own entries first: pade is pasti's that falls (padem), not pasti's that grazes (pasem)
    const ownTr = !!this.transitive.get(v.form)?.length
    const ownIntr = !!this.intransitive.get(v.form)?.some(h => !h.se || hasSe)
    if (ownTr !== ownIntr && !hasSe) return ownIntr
    const tr = [...lemmas].some(l => this.transitive.has(l)) || ownTr
    const intr = [...lemmas].some(l => this.intransitive.get(l)?.some(h => !h.se || hasSe)) || ownIntr
    // a verb with se has no object in the accusative, unless the table says it has one
    if (hasSe && !(this.governing.get(v.form) ?? []).some(e => e.se && e.takes.includes('acc'))) return true
    return intr && !tr
  }

  private clauseCases(all: Tok[], toks: Tok[]): WordCase[] {
    const c = this.clauseOf(all, toks)
    const phrases = this.phrasesOf(toks)
    // ni with an adjective or a noun of its own (Mačka ni neumna, Kruh ni svež) is no "there isn't": the subject stays
    if (c.existential && phrases.some(q => q.toks.every(t => t.nominal.every(r => MODIFIER_POS.has(r.pos))) && q.options.some(o => o.case === 'nom'))) c.existential = false
    const states = phrases.map(p => this.governed(p, all, c, phrases))
    // the subject and the object are one phrase each: one sure of its role takes it from the others still open
    for (let round = 0; round < 3; round++) {
      const sure = (s: PhraseState, as: CaseKind) => s.options.length > 0 && s.options.every(o => o.as === as)
      for (const s of states) {
        if (s.final || s.options.length < 2) continue
        const others = states.filter(x => x !== s && !x.coordinated)
        if (!c.copular && !s.coordinated && others.some(x => sure(x, 'subject'))) drop(s, o => o.as === 'subject', 'subject-taken', true)
        // (an experiencer's accusative, zebe me, boli me, takes the verb's accusative too: Boli me grlo, grlo the subject)
        const objectLike = (x: PhraseState) => x.options.length > 0 && x.options.every(o => o.case === 'acc' && (o.as === 'object' || o.as === 'verb'))
        if (!s.coordinated && others.some(objectLike)) drop(s, o => o.case === 'acc' && (o.as === 'object' || o.as === 'verb'), 'object-taken', true)
      }
    }
    return states.flatMap(s => this.wordsOf(s))
  }

  // --- the noun phrases ---------------------------------------------------------------------------------------------

  /** The clause's noun phrases: modifiers that agree, then their head (a noun, a pronoun, a name, or the last modifier). */
  private phrasesOf(toks: Tok[]): Phrase[] {
    const out: Phrase[] = []
    let run: Tok[] = []
    const flush = () => {
      let cur: Tok[] = []
      for (const [k, t] of run.entries()) {
        const n = run[k + 1]
        const modifier = { ...t, nominal: t.nominal.filter(r => MODIFIER_POS.has(r.pos)) }
        const head = t.nominal.some(r => !MODIFIER_POS.has(r.pos)) && !(n && modifier.nominal.length && agreeing([...cur, modifier, n]).length)
        const next = [...cur, head ? t : modifier.nominal.length ? modifier : t]
        if (cur.length && !agreeing(next).length) {
          out.push(phraseOf(cur))
          cur = [t]
        } else cur = next
        if (head) {
          out.push(phraseOf(cur))
          cur = []
        }
      }
      if (cur.length) out.push(phraseOf(cur))
      run = []
    }
    for (const t of toks) {
      if (t.role !== 'nominal') {
        flush()
        continue
      }
      // a personal pronoun, a fused one, a word said of the subject (rad: never its noun's adjective): a phrase of its own;
      // a name, with the adjectives before it (lepi Vidi)
      if (t.fused || t.nominal.every(r => r.pos === 'pron') || t.nominal.every(r => this.predicativeLemmas.has(this.norm(r.lemma)))) {
        flush()
        run = [t]
        flush()
      } else if (t.nominal.every(r => r.pos === 'name')) {
        run.push(t)
        flush()
      } else run.push(t)
    }
    flush()
    return out.filter(p => p.options.length)
  }

  // --- what governs a phrase ------------------------------------------------------------------------------------------

  private governed(p: Phrase, all: Tok[], c: Clause, phrases: Phrase[]): PhraseState {
    const first = p.toks[0]
    const before = all[first.i - 1]?.clause === first.clause ? all[first.i - 1] : undefined
    const after = all[p.head.i + 1]?.clause === first.clause ? all[p.head.i + 1] : undefined
    const s: PhraseState = { phrase: p, options: p.options.map(o => ({ ...o })), out: [], final: false, coordinated: [before, after].some(t => t && COORDINATION.has(t.form)), unresolved: false }
    const set = (as: CaseKind, by: Tok | undefined, rule: string, sense?: string) => {
      for (const o of s.options) Object.assign(o, { as, by, rule, ...(sense ? { sense } : {}) })
    }
    const unresolved = () => {
      s.options = p.options.map(o => ({ ...o, as: 'unresolved' as CaseKind, rule: this.caseRule(o.case) }))
      s.out = []
      s.unresolved = true
      s.final = true
      return s
    }
    const timeNoun = p.head.nominal.some(r => this.timeNouns.has(this.norm(r.lemma)) || r.pos === 'num' || HOURS.has(this.norm(r.lemma)))

    // a preposition fused with a pronoun (zame, nanj): the accusative, its preposition inside it
    if (p.head.fused) {
      const f = p.head.fused
      s.options = [{ case: f.case, number: f.number, as: 'fused', by: p.head, rule: f.rule }]
      s.final = true
      return s
    }

    // a fixed phrase (Dober dan, lahko noč, na svidenje): as the table says
    const phrase = this.phraseAt(all, p)
    if (phrase) {
      for (const o of s.options) if (o.case !== phrase.entry.case || o.number !== phrase.entry.number) s.out.push({ case: o.case, number: o.number, why: 'phrase' })
      s.options = [{ case: phrase.entry.case, number: phrase.entry.number, as: 'phrase', by: phrase.trigger, rule: phrase.entry.rule }]
      s.final = true
      return s
    }

    // the dual only with a word that says two (in the phrase or right before it: then only the dual), or when nothing
    // else of the form can stand here (Dajte mi vedri: two buckets, the instrumental plural would need a preposition)
    const twoBefore = before && before.dual && !before.verbs.length && !before.nominal.length
    const atPrep = before?.role === 'prep' ? new Set(before.prep?.takes.map(x => x.case)) : null
    const fitsHere = (o: Option) => (atPrep ? atPrep.has(o.case) : o.case !== 'loc' && o.case !== 'ins')
    if (p.toks.some(t => t.dual && !t.verbs.length) || twoBefore) narrow(s, o => o.number === 'du', 'dual')
    else if (!c.dual && s.options.some(o => o.number !== 'du' && fitsHere(o))) drop(s, o => o.number === 'du', 'dual', true)

    // a preposition right before the phrase (or before a word the dictionary doesn't know, an adjective of a name: iz
    // Vipavske doline): its cases, by what the sentence means (where to, where, when)
    const unknown = (t: Tok | undefined) => !!t && t.role === 'other' && !t.nominal.length && !t.verbs.length && !t.other.size && !t.prep
      && (/^\p{Lu}/u.test(t.raw) || ADJECTIVE_LIKE.test(t.form)) && p.head.nominal.some(r => r.pos === 'noun' || r.pos === 'name')
    const over = (t: Tok | undefined) => unknown(t) || (!!t && t.role === 'other' && (t.count || t.quantity || (t.other.has('adv') && !t.nominal.length)))
    let prep: Tok | undefined = before?.role === 'prep' ? before : undefined
    for (let k = first.i - 1; !prep && k >= first.i - 3 && k >= 0 && all[k].clause === first.clause && over(all[k]); k--) if (all[k - 1]?.role === 'prep' && all[k - 1].clause === first.clause) prep = all[k - 1]
    // a number from five in the phrase (dvajsetih letih) is no dual
    if (all.slice(Math.max(0, first.i - 3), first.i).some(t => t.count && t.clause === first.clause) || p.toks.some(t => t.count)) drop(s, o => o.number === 'du', 'dual', true)
    if (prep?.prep) return this.byPreposition(s, prep, c, timeNoun, unresolved)

    // the locative and the instrumental only after a preposition
    drop(s, o => o.case === 'loc' || o.case === 'ins', 'needs-preposition', true)
    if (s.options.every(o => o.case === 'loc' || o.case === 'ins')) return unresolved()

    // the nominative only for a subject that agrees with the verb, a call, or what something is (after biti, with a
    // subject of its own: France je mlinar, Jaz sem Tine)
    const pronoun = p.head.nominal.some(r => r.pos === 'pron' && r.person)
    const otherSubject = c.subjects.some(x => x.person && x.person !== 3) || phrases.some(q => q !== p && q.options.some(x => x.case === 'nom' && agrees(x, q, c.subjects)))
    const several = s.coordinated && c.subjects.some(x => x.number && x.number !== 'sg' && (!x.person || x.person === 3))
    const lone = p.toks.every(t => t.nominal.every(r => MODIFIER_POS.has(r.pos))) && p.head.nominal.some(r => r.pos === 'adj')
    const said = p.head.nominal.some(r => this.predicativeLemmas.has(this.norm(r.lemma)))
    const predicative = lone && !c.verbless && (c.copular || said)
    if (predicative) {
      // what the subject is like: its number and gender, the subject's (Kava je dobra: feminine singular) or the verb's
      const subject = phrases.filter(q => q !== p && !q.toks.every(t => t.nominal.every(r => MODIFIER_POS.has(r.pos)))).flatMap(q => q.options.filter(x => x.case === 'nom' && agrees(x, q, c.subjects)))
      drop(s, o => o.case === 'nom' && !(subject.length
        ? subject.some(x => x.number === o.number && (!x.gender || !o.gender || x.gender === o.gender || (x.number !== 'sg' && x.gender === 'm')))
        : agrees(o, p, c.subjects, 'number')), 'agreement', true)
    } else drop(s, o => o.case === 'nom' && !(c.verbless || several || agrees(o, p, c.subjects) || (c.copular && otherSubject && (!pronoun || agrees(o, p, c.subjects, 'person')))), 'agreement', true)

    // a number from five or a word of quantity before it, or a measure (kos kruha): the genitive
    const q = before && (before.count || before.quantity) && before.role !== 'nominal' ? before : undefined
    const measure = !q && before && before.role === 'nominal' && before.measure ? before : undefined
    if ((q || measure) && narrow(s, o => o.case === 'gen', 'governed')) {
      set(q?.count ? 'number' : 'quantity', q ?? measure, this.s.table.quantity.rule)
      s.final = true
      return s
    }

    // a word of feeling (všeč mi je, strah me je): who feels it, in its case
    const pred = c.toks.find(t => t !== p.head && this.predicates.has(t.form))
    if (pred) {
      const pc = this.predicates.get(pred.form)!
      if (narrow(s, o => o.case === pc.case, 'governed')) {
        set('predicate', pred, pc.rule)
        s.final = s.options.length === 1
        return s
      }
    }

    const governor = this.governorOf(p, c.verbs, c.hasSe)

    // a negation: what it denies (its verb's object, what there isn't) goes into the genitive
    if (c.negated && !(governor && !governor.entry.takes.includes('acc'))) {
      const aboutObject = c.existential || !!c.objectVerb
      if (aboutObject && s.options.some(o => o.case === 'gen')) {
        if (!c.contrast) drop(s, o => o.case === 'acc', 'negation', true)
        for (const o of s.options) if (o.case === 'gen') Object.assign(o, { as: 'negation', by: c.negated, rule: this.s.table.negation.rule })
        if (!s.options.some(o => o.case === 'nom')) {
          narrow(s, o => o.case === 'gen', 'negation')
          s.final = true
          return s
        }
      }
    }

    // a verb that governs a case (pomagati: komu?, bati se: česa?, dati: komu? kaj?)
    if (governor && s.options.some(o => o.case !== 'nom' && governor.entry.takes.includes(o.case))) {
      const takes = governor.entry.takes
      drop(s, o => o.case !== 'nom' && !takes.includes(o.case) && o.as !== 'negation', 'governed', true)
      for (const o of s.options) {
        if (o.as || !takes.includes(o.case)) continue
        if (o.case === 'acc' && takes.length === 1) Object.assign(o, { as: 'verb', by: governor.tok, rule: governor.entry.rule })
        else if (o.case === 'acc') Object.assign(o, { as: 'object', by: this.objectVerbOf(p, c) ?? governor.tok, rule: this.s.table.roles.object.rule })
        else Object.assign(o, { as: 'verb', by: governor.tok, rule: governor.entry.rule })
      }
    }

    // its role: the subject (it agrees with the verb), what something is, a call, the object, a time, the noun's
    // genitive, a dative of whom it is for; a reading none of them fits goes, unless none fits at all (then they all stay)
    // (a noun's genitive after it: never a personal pronoun's, whose own is a possessive: tvoj, njegov)
    const owner = before && before.role === 'nominal' && before.nominal.some(r => r.pos === 'noun' || r.pos === 'name') && !p.head.nominal.some(r => r.pos === 'pron' && this.pronounLemmas.has(r.lemma)) ? before : undefined
    const unsupported: { o: Option; why: OutReason }[] = []
    const dropRole = (o: Option, why: OutReason) => unsupported.push({ o, why })
    for (const o of [...s.options]) {
      if (o.as) continue
      if (o.case === 'nom') {
        if (predicative) Object.assign(o, { as: 'predicative', by: this.subjectVerb(c) ?? c.verbs[0], rule: this.s.table.roles.predicate.rule })
        else if (c.verbless) Object.assign(o, this.isAddress(all, p) ? { as: 'address', rule: this.s.table.roles.address.rule } : { as: 'form', rule: this.s.table.roles.subject.rule })
        else if (agrees(o, p, c.subjects) && !(c.copular && this.predicateAfter(p, c))) Object.assign(o, { as: 'subject', by: this.subjectVerb(c), rule: this.s.table.roles.subject.rule })
        else Object.assign(o, { as: 'copula', by: c.verbs.find(v => v.biti) ?? c.verbs[0], rule: this.s.table.roles.predicate.rule })
      } else if (o.case === 'acc') {
        const verb = this.objectVerbOf(p, c)
        if (timeNoun) Object.assign(o, { as: 'time', rule: this.s.table.time.rule })
        else if (verb) Object.assign(o, { as: 'object', by: verb, rule: this.s.table.roles.object.rule })
        else if (c.verbless) Object.assign(o, c.asking ? { as: 'object', rule: this.s.table.roles.object.rule } : { as: 'form', rule: this.s.table.roles.object.rule })
        else dropRole(o, 'no-object')
      } else if (o.case === 'gen') {
        if (owner) Object.assign(o, { as: 'attribute', by: owner, rule: this.s.table.roles.attribute.rule })
        else dropRole(o, 'needs-trigger')
      } else if (o.case === 'dat') Object.assign(o, { as: 'form', rule: this.s.table.roles.dative.rule })
    }
    if (unsupported.length < s.options.length) for (const { o, why } of unsupported) drop(s, x => x === o, why)
    else for (const { o } of unsupported) if (o.case !== 'gen' || s.options.some(x => x.case !== 'gen')) o.as = 'form'
    // a genitive with nothing to decide it: only when the form can be nothing else (some of it: Daj mi kruha), or a word of
    // quantity earlier in the clause says how much (Koliko ti je let?)
    const much = c.toks.find(t => t.i < first.i && (t.quantity || t.count) && t.role !== 'nominal')
    for (const o of s.options) if (o.case === 'gen' && !o.as) Object.assign(o, much ? { as: much.count ? 'number' : 'quantity', by: much, rule: this.s.table.quantity.rule } : { as: 'form', rule: this.s.table.roles.partitive.rule })
    for (const o of s.options) if (!o.as) Object.assign(o, { as: 'form', rule: this.caseRule(o.case) })
    return s
  }

  /** A preposition's phrase: its cases; of two, by the clause's verb (kam? or kje?), by a time noun, or as it lists them. */
  private byPreposition(s: PhraseState, prep: Tok, c: Clause, timeNoun: boolean, unresolved: () => PhraseState): PhraseState {
    const takes = prep.prep!.takes
    const cases = new Set(takes.map(x => x.case))
    // the phrase's words agree in none of its cases: its noun alone (an adjective the supplement lists by one reading: močne)
    if (!s.options.some(o => cases.has(o.case)) && s.phrase.toks.length > 1) {
      const head = agreeing([s.phrase.head]).filter(o => cases.has(o.case) && (o.number !== 'du' || c.dual))
      if (head.length) s.options = head.map(o => ({ ...o }))
    }
    drop(s, o => !cases.has(o.case), 'preposition')
    if (!s.options.length) return unresolved()
    // a phrase of the clause that can be its subject: then the verb is no imperative
    const subject = c.toks.some(t => t.role === 'nominal' && t.i < prep.i && t.nominal.some(r => r.case === 'nom' && r.pos !== 'pron'))
    const motion = c.verbs.some(v => this.inIndex(v, this.motion, c.hasSe, subject))
    const rest = c.verbs.some(v => v.biti || this.inIndex(v, this.rest, c.hasSe, subject))
    const left = () => [...new Set(s.options.map(o => o.case))]
    if (left().length > 1) {
      const fits = (x: (typeof takes)[number]) => left().includes(x.case)
      const timed = timeNoun ? takes.filter(x => x.time && fits(x)) : []
      const pick = timed.length ? timed
        : motion && !rest ? takes.filter(x => x.sense === 'motion' && fits(x))
        : rest && !motion ? takes.filter(x => x.sense === 'rest' && fits(x))
        : []
      if (pick.length) drop(s, o => o.case !== pick[0].case, 'preposition')
    }
    // each case's sense: of a case it takes twice (za: for, or behind as where to), by the verb
    for (const o of s.options) {
      const same = takes.filter(x => x.case === o.case)
      // (za nedeljo: for Sunday, a time: not where to, whatever the verb)
      const e = same.length > 1 ? (motion && !timeNoun ? same.find(x => x.sense === 'motion') : same.find(x => x.sense !== 'motion')) ?? same[0] : same[0]
      const time = timeNoun && !!e.time
      Object.assign(o, { as: 'preposition', by: prep, rule: time ? e.time : e.rule, sense: time ? 'time' : e.sense, ...(e.default && left().length > 1 ? { likely: true } : {}) })
    }
    // still two: the likely one first (the table's default), then in the order the preposition lists them; the singular first
    const at = (o: Option) => (o.likely ? -1 : takes.findIndex(x => x.case === o.case)) * 3 + ['sg', 'pl', 'du'].indexOf(o.number)
    s.options.sort((a, b) => at(a) - at(b))
    s.final = true
    return s
  }

  /** The verb whose object the phrase is: the clause's nearest that takes one. */
  private objectVerbOf(p: Phrase, c: Clause): Tok | undefined {
    return [...c.verbs].filter(v => this.takesObject(v, c.hasSe)).sort((a, b) => Math.abs(a.i - p.head.i) - Math.abs(b.i - p.head.i))[0]
  }

  /** The verb whose subject a phrase is: the clause's lexical finite verb (its l-form, else biti). */
  private subjectVerb(c: Clause): Tok | undefined {
    return c.finite.find(f => f.r.kind === 'past')?.tok ?? c.finite.find(f => !f.tok.biti)?.tok ?? c.finite[0]?.tok ?? c.verbs.find(v => v.verbs.some(r => r.kind === 'imp'))
  }

  /** In a copula's clause, whether the phrase is what something is: after the copula, with a subject before it (To je nož). */
  private predicateAfter(p: Phrase, c: Clause): boolean {
    const cop = c.verbs.find(v => v.biti || this.copulas.has(v.form))
    if (!cop || p.head.i < cop.i) return false
    return c.toks.some(t => t.i < cop.i && t.role === 'nominal' && t.nominal.some(r => r.case === 'nom')) || c.subjects.some(x => x.person !== 3)
  }

  /** Whether the phrase calls someone: alone in its clause (between commas, or the line's ends), a name or a person. */
  private isAddress(all: Tok[], p: Phrase): boolean {
    const clause = all.filter(t => t.clause === p.head.clause)
    if (!clause.every(t => p.toks.includes(t) || t.role === 'other')) return false
    return p.head.nominal.some(r => r.pos === 'name' || (r.pos === 'noun' && this.isPerson(r.lemma)))
  }

  /** Whether a noun names a person or an animal one calls (babica, učiteljica, fant, Muri's pes), by its English gloss. */
  private isPerson(lemma: string): boolean {
    const gloss = this.s.dict.lemmasOf(lemma).filter(l => l.pos === 'noun').flatMap(l => l.gloss).join(' ').toLowerCase()
    return PERSON.test(gloss)
  }

  /** The table's phrase the phrase is part of (Dober dan, na svidenje), and its trigger word. */
  private phraseAt(all: Tok[], p: Phrase): { entry: CaseTriggers['phrases'][number]; trigger?: Tok } | null {
    for (const entry of this.s.table.phrases) {
      const words = entry.words.split(' ')
      for (let a = Math.max(0, p.head.i - words.length + 1); a <= p.head.i; a++) {
        if (words.every((w, k) => all[a + k]?.form === w && all[a + k].clause === p.head.clause)) {
          const trigger = entry.trigger ? all.slice(a, a + words.length).find(t => t.form === entry.trigger) : undefined
          return { entry, trigger }
        }
      }
    }
    return null
  }

  /** The verb of the clause that governs a case (the table's), the nearest first; with se, its se-verb. */
  private governorOf(p: Phrase, verbs: Tok[], hasSe: boolean): { tok: Tok; entry: VerbEntry } | null {
    const sorted = [...verbs].sort((a, b) => Math.abs(a.i - p.head.i) - Math.abs(b.i - p.head.i))
    for (const v of sorted) {
      const hits = [...(this.governing.get(v.form) ?? []), ...v.verbs.flatMap(r => this.governingLemmas.get(this.norm(r.lemma)) ?? [])]
      const fit = hits.find(e => !!e.se === hasSe) ?? hits.find(e => !e.se)
      if (fit) return { tok: v, entry: fit }
    }
    return null
  }

  private caseRule(c: CaseKey | undefined): string {
    if (!c) return 'skloni'
    return this.s.table.cases.find(x => x.key === c)?.page ?? 'skloni'
  }

  // --- each word of a phrase ------------------------------------------------------------------------------------------

  private wordsOf(s: PhraseState): WordCase[] {
    const p = s.phrase
    const ref = (t: Tok): WordRef => ({ i: t.i, word: t.raw, start: t.start, end: t.end })
    const opts = order(s.options, p.head)
    const cases = new Set(opts.map(o => o.case))
    const top = opts[0]
    // one reading left: decided; one case of two numbers (vasi: gen. sg. or pl.): likely, the singular; the subject before
    // its verb and the object after it, or a preposition's cases in its own order: likely; else ambiguous
    const sure: CaseSure = s.unresolved ? 'ambiguous'
      : opts.length === 1 ? 'decided'
      : cases.size === 1 ? 'likely'
      : cases.size === 2 && cases.has('nom') && cases.has('acc') && opts.some(o => o.as === 'subject') && opts.some(o => o.as === 'object') && (top.as === 'subject' ? !!top.by && p.head.i < top.by.i : !!top.by && p.head.i > top.by.i) ? 'likely'
      : top?.as === 'subject' && p.head.nominal.some(x => x.pos === 'pron' && x.case === 'nom' && x.person && x.person !== 3) ? 'likely'
      : (top?.as === 'subject' || top?.as === 'copula') && opts.slice(1).every(o => o.as === 'form' || o.as === 'time' || (top.as === 'subject' && o.as === 'copula')) ? 'likely'
      : top?.as === 'preposition' && top.likely ? 'likely'
      : 'ambiguous'
    const kind: CaseKind = s.unresolved ? 'unresolved' : sure === 'ambiguous' ? (top?.as === 'preposition' || top?.as === 'negation' ? top.as : 'form') : (top?.as ?? 'form')
    const by = sure === 'ambiguous' && !(top?.as === 'preposition' || top?.as === 'negation') ? undefined : top?.by
    const triggerRef = by ? { ...ref(by), ...(by.role === 'verb' && by.verbs[0] && by.verbs[0].lemma !== '?' ? { lemma: by.verbs[0].lemma } : {}) } : undefined
    const rule = sure === 'ambiguous' ? (top?.as === 'preposition' ? (top.rule ?? 'skloni') : 'skloni') : (top?.rule ?? this.caseRule(top?.case))
    const pub = (o: Option): CaseOption => ({ case: o.case, number: o.number, ...(o.gender ? { gender: o.gender } : {}), ...(o.as ? { as: o.as } : {}) })
    return p.toks.map(t => {
      // each word's own options: the phrase's, narrowed to its readings (its gender)
      const own = opts.flatMap(o => {
        const g = t.nominal.filter(r => r.case === o.case && r.number === o.number).flatMap(r => r.genders)
        if (!g.length && !t.fused && o.as !== 'phrase') return []
        const genders = o.gender && g.includes(o.gender) ? [o.gender] : [...new Set(g)]
        const gender = o.as === 'phrase' ? undefined : genders.length === 1 && t.nominal.some(r => MODIFIER_POS.has(r.pos) || r.pos === 'pron') ? genders[0] : o.gender
        return [{ ...pub(o), ...(gender ? { gender } : {}) }]
      })
      const options = dedupeOptions(own.length ? own : opts.map(pub))
      const pick = sure === 'ambiguous' ? null : options[0] ?? null
      const derived = !!pick && t.nominal.some(r => r.derived) && t.nominal.filter(r => r.case === pick.case && r.number === pick.number).every(r => r.derived)
      const w: WordCase = {
        ...ref(t),
        readings: dedupeReadings(t.nominal.map(r => ({ lemma: r.lemma, pos: r.pos, case: r.case, number: r.number, genders: r.genders, ...(r.derived ? { derived: true as const } : {}) }))),
        pick, sure, kind, ...(triggerRef ? { trigger: triggerRef } : {}), ...(top?.sense && sure !== 'ambiguous' ? { sense: top.sense } : {}),
        rule, options, out: dedupeOut(s.out), ...(derived ? { derived: true as const } : {}),
      }
      if (t !== p.head) w.agrees = ref(p.head)
      return w
    })
  }
}

// --- helpers --------------------------------------------------------------------------------------------------------

/** Drops the options [f] says from [s], and says why; [keepOne]: unless none would be left. */
function drop(s: PhraseState, f: (o: Option) => boolean, why: OutReason, keepOne = false) {
  const keep = s.options.filter(o => !f(o))
  if (keepOne && !keep.length) return
  for (const o of s.options) if (f(o)) s.out.push({ case: o.case, number: o.number, why })
  s.options = keep
}

/** Narrows [s] to the options [f] keeps, unless none would be left: then nothing changes (false). */
function narrow(s: PhraseState, f: (o: Option) => boolean, why: OutReason): boolean {
  if (!s.options.some(f)) return false
  drop(s, o => !f(o), why)
  return true
}

/** The readings a phrase's words agree in: a case and number all have, with the genders they share. */
function agreeing(toks: Tok[]): CaseOption[] {
  if (!toks.length) return []
  let opts: { case: CaseKey; number: NumberKey; genders: GenderKey[] }[] = toks[0].nominal.map(r => ({ case: r.case, number: r.number, genders: r.genders }))
  for (const t of toks.slice(1)) {
    const next: typeof opts = []
    for (const o of opts) {
      for (const r of t.nominal) {
        if (r.case !== o.case || r.number !== o.number) continue
        const g = o.genders.filter(x => r.genders.includes(x))
        if (g.length) next.push({ case: o.case, number: o.number, genders: g })
      }
    }
    opts = next
  }
  const merged = new Map<string, Set<GenderKey>>()
  for (const o of opts) {
    const k = `${o.case}.${o.number}`
    merged.set(k, new Set([...(merged.get(k) ?? []), ...o.genders]))
  }
  return [...merged].map(([k, g]) => {
    const [c, n] = k.split('.') as [CaseKey, NumberKey]
    return { case: c, number: n, ...(g.size === 1 ? { gender: [...g][0] } : {}) }
  })
}

function phraseOf(toks: Tok[]): Phrase {
  const head = toks.at(-1)!
  return { toks, head, options: head.fused ? [{ case: head.fused.case, number: head.fused.number }] : agreeing(toks) }
}

/**
 * Whether a nominative reading [o] of phrase [p] agrees with one of the clause's subjects: its person (a pronoun's; a
 * noun's is the third), number and, an l-form's, gender. [person]: the person alone (a pronoun after biti: To sem jaz).
 */
function agrees(o: CaseOption, p: Phrase, subjects: Clause['subjects'], by: 'all' | 'person' | 'number' = 'all'): boolean {
  if (!subjects.length) return true
  const persons = p.head.nominal.filter(r => r.case === 'nom' && r.number === o.number).map(r => r.person ?? 3)
  const own = persons.length ? persons : [3]
  return subjects.some(x => (by === 'number' || !x.person || own.includes(x.person)) && (by === 'person' || !x.number || x.number === o.number)
    && (by === 'person' || !x.gender || !o.gender || x.gender === o.gender || (x.number !== 'sg' && x.gender === 'm')))
}

/** The options in the order the lens shows them: a role the sentence's order prefers first, the singular before the plural. */
function order(options: Option[], head: Tok): Option[] {
  const rank = (o: Option) => {
    if (o.as === 'preposition') return 0
    let r = CASE_KEYS.indexOf(o.case) * 3 + ['sg', 'pl', 'du'].indexOf(o.number)
    if (o.as === 'subject' && o.by && head.i < o.by.i) r -= 50
    if (o.as === 'subject' && head.nominal.some(x => x.pos === 'pron' && x.case === 'nom' && x.person && x.person !== 3)) r -= 60
    if (o.as === 'subject') r -= 40
    if (o.as === 'copula') r -= 30
    if (o.as === 'object' && o.by && head.i > o.by.i) r -= 50
    return r
  }
  return options.map((o, k) => ({ o, k })).sort((a, b) => rank(a.o) - rank(b.o) || a.k - b.k).map(x => x.o)
}

function dedupe<T extends FormReading>(list: T[]): T[] {
  const seen = new Map<string, T>()
  for (const r of list) {
    const k = `${r.lemma}|${r.pos}|${r.case}|${r.number}`
    const had = seen.get(k)
    if (had) {
      had.genders = [...new Set([...had.genders, ...r.genders])]
      if (!r.derived) delete had.derived
    } else seen.set(k, { ...r, genders: [...r.genders] })
  }
  return [...seen.values()]
}

function dedupeReadings(list: FormReading[]): FormReading[] {
  return dedupe(list).sort((a, b) => CASE_KEYS.indexOf(a.case) - CASE_KEYS.indexOf(b.case) || ['sg', 'du', 'pl'].indexOf(a.number) - ['sg', 'du', 'pl'].indexOf(b.number))
}

function dedupeOptions(list: CaseOption[]): CaseOption[] {
  const seen = new Set<string>()
  return list.filter(o => {
    const k = `${o.case}.${o.number}.${o.gender ?? ''}`
    if (seen.has(k)) return false
    seen.add(k)
    return true
  })
}

function dedupeOut(list: RuledOut[]): RuledOut[] {
  const seen = new Set<string>()
  return list.filter(o => {
    const k = `${o.case}.${o.number}`
    if (seen.has(k)) return false
    seen.add(k)
    return true
  })
}

function dedupeVerbs(list: VerbReading[]): VerbReading[] {
  const seen = new Set<string>()
  return list.filter(v => {
    const k = `${v.lemma}|${v.kind}|${v.person ?? ''}|${v.number ?? ''}|${v.gender ?? ''}`
    if (seen.has(k)) return false
    seen.add(k)
    return true
  })
}

/** The numbers a pronoun's readings without one are in (Wiktionary's "genitive/accusative" of oni: the plural). */
function pronounNumbers(lemma: string): NumberKey[] {
  if (/^(mi|vi|oni|one|me|ve)$/.test(lemma)) return ['pl']
  if (/dva$|dve$/.test(lemma)) return ['du']
  return ['sg']
}

/** A verb reading of one of the forms book's slots (pres.3sg, past.f.sg, imp.2pl, fut.1pl, neg.3sg). */
function verbReadingOfSlot(lemma: string, key: string): VerbReading | null {
  const [a, b, c] = key.split('.')
  const pn = (x: string) => ({ person: Number(x[0]) as 1 | 2 | 3, number: x.slice(1) as NumberKey })
  switch (a) {
    case 'pres':
    case 'neg':
    case 'fut':
      return { lemma, kind: a, ...pn(b) }
    case 'imp':
      return { lemma, kind: 'imp', ...pn(b) }
    case 'past':
      return { lemma, kind: 'past', gender: b as GenderKey, number: c as NumberKey }
  }
  return null
}

/**
 * A name the dictionary doesn't know, by its ending (derived, as a person's: a masculine's accusative is its genitive):
 * -u the dative or locative (Muriju), -om or -em the instrumental, -a a woman's nominative or a man's genitive and
 * accusative, -e a woman's genitive or a man's nominative (Tone), -i a woman's dative or locative or a man's nominative
 * (Muri), -o a woman's accusative or instrumental or a man's nominative (Marko), a consonant a man's nominative.
 */
function nameByEnding(raw: string): Nominal[] {
  const f = raw.toLowerCase()
  const r = (c: CaseKey, g: GenderKey[]): Nominal => ({ lemma: raw, pos: 'name', case: c, number: 'sg', genders: g, strong: false, derived: true })
  if (/(om|em)$/.test(f)) return [r('ins', ['m'])]
  if (f.endsWith('u')) return [r('dat', ['m']), r('loc', ['m'])]
  if (f.endsWith('a')) return [r('nom', ['f']), r('gen', ['m']), r('acc', ['m'])]
  if (f.endsWith('e')) return [r('gen', ['f']), r('nom', ['m'])]
  if (f.endsWith('i')) return [r('dat', ['f']), r('loc', ['f']), r('nom', ['m'])]
  if (f.endsWith('o')) return [r('acc', ['f']), r('ins', ['f']), r('nom', ['m'])]
  return /[^aeiou]$/.test(f) ? [r('nom', ['m'])] : []
}

/** A verb the dictionary doesn't know, by its ending: the present's persons (počakam: I), weakly. */
function guessVerb(form: string): VerbReading | null {
  const ends: [RegExp, 1 | 2 | 3, NumberKey][] = [[/[aei]mo$/, 1, 'pl'], [/[aei]te$/, 2, 'pl'], [/[aei]jo$/, 3, 'pl'], [/[aei]va$/, 1, 'du'], [/[aei]m$/, 1, 'sg'], [/[aei]š$/, 2, 'sg']]
  for (const [re, person, number] of ends) if (re.test(form)) return { lemma: '?', kind: 'pres', person, number, weak: true }
  return null
}
