// The case lens's trigger table (docs/plans/05-built/case-lens.md): what decides a Slovene word's case, as data
// (companion/grammar/sl/data/case-triggers.json, lani.case-triggers/v0): the six cases with their question words and
// colours, the prepositions and the cases they take (where to, where, when, with, from), the negations, the words of
// quantity and the numbers from five, the verbs and the words of feeling that govern a case, the verbs of motion and of
// rest (kam? or kje?), the copulas, the verbs without an object, the time nouns, the fixed phrases, and the personal
// pronouns' forms (the grammar book's osebni-zaimki page, completed with the cases its table leaves out). Every entry
// names its rule: the grammar book's page that explains it. The analysis that reads it is cases.ts.
import { existsSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import type { Log } from './config'
import { schemaField } from './schema'

export const CASE_TRIGGERS_SCHEMA = 'lani.case-triggers/v0'

/** The six cases in the order a Slovene teacher numbers them (imenovalnik 1 … orodnik 6), by the slots' keys (forms.ts). */
export const CASE_KEYS = ['nom', 'gen', 'dat', 'acc', 'loc', 'ins'] as const
export type CaseKey = (typeof CASE_KEYS)[number]
export const NUMBER_KEYS = ['sg', 'du', 'pl'] as const
export type NumberKey = (typeof NUMBER_KEYS)[number]
export const GENDER_KEYS = ['m', 'f', 'n'] as const
export type GenderKey = (typeof GENDER_KEYS)[number]

/** The cases' ids, shared with the case trainer: imenovalnik 1, rodilnik 2, dajalnik 3, tozilnik 4, mestnik 5, orodnik 6. */
export const CASE_IDS = { nom: 'imenovalnik', gen: 'rodilnik', dat: 'dajalnik', acc: 'tozilnik', loc: 'mestnik', ins: 'orodnik' } as const satisfies Record<CaseKey, string>

const caseKey = z.enum(CASE_KEYS)
const numberKey = z.enum(NUMBER_KEYS)
const genderKey = z.enum(GENDER_KEYS)
/** A word as the analysis compares it: lowercase letters (č, š, ž), one word. */
const word = z.string().min(1).max(40).regex(/^[\p{Ll}]+$/u, 'one word, lowercase letters only')
/** A grammar book page's id (its rule). */
const page = z.string().regex(/^[a-z0-9][a-z0-9-]{0,63}$/, 'a grammar page id, e.g. "rodilnik-predlogi"')
const words = z.array(word).min(1)

/**
 * An example of an entry: a sentence, its translation, the word it decides ([word], as written; [nth]: which of the same
 * words, from 0), the case and number the analysis must give it, and the word that decides it ([trigger], as written;
 * none: the word's role decides, the subject or the object). The smoke analyses every example (test/smoke/case-lens.ts).
 */
const example = z.strictObject({
  sl: z.string().trim().min(3).max(160),
  en: z.string().trim().min(2).max(200),
  word: z.string().min(1).max(40),
  nth: z.number().int().min(0).max(5).optional(),
  case: caseKey,
  number: numberKey,
  trigger: z.string().min(1).max(40).optional(),
})
export type CaseExample = z.infer<typeof example>
const examples = z.array(example).min(1).max(6)

/** What a preposition means with one of its cases: where to (motion), where (rest), when (time), with, from, for, about, after. */
export const PREPOSITION_SENSES = ['motion', 'rest', 'time', 'with', 'from', 'for', 'about', 'after', 'towards', 'fetch', 'other'] as const

const caseEntry = z.strictObject({
  key: caseKey,
  id: z.string(),
  n: z.number().int().min(1).max(6),
  /** The question words the school asks it with ("koga?", "česa?"); [also]: the other pair (o kom? o čem?). */
  questions: z.array(z.string().min(2).max(20)).min(2).max(2),
  also: z.array(z.string().min(2).max(20)).max(2).optional(),
  /** Its colour in the light theme (the dark theme derives one of the same hue). */
  colour: z.string().regex(/^#[0-9A-F]{6}$/, 'a colour, e.g. "#4A7FC1"'),
  page,
})

const prepositionEntry = z.strictObject({
  words,
  /** The cases it takes, the most usual first, each with its sense and its rule. */
  takes: z
    .array(z.strictObject({
      case: caseKey,
      sense: z.enum(PREPOSITION_SENSES),
      rule: page,
      /** The rule when the noun is one of time (v sredo, ob petih, pred eno uro): kdaj-cas. */
      time: page.optional(),
      /** The likely one when the form and the verb leave two (za kavo: for; po vasi: around): "likely", never decided. */
      default: z.boolean().optional(),
    }))
    .min(1)
    .max(3),
  examples,
})

/** A verb by its infinitive and its first person singular (pomagati, pomagam): the analysis makes its forms. */
const verbForms = {
  lemma: z.string().min(2).max(40).regex(/^[\p{Ll}]+$/u),
  /** The first person singular of the present (pomagam, vidim, pišem): the present's stem. */
  present: word,
  /** Forms the rules would get wrong (povejo, povej, rekel): matched as forms of this verb too. */
  forms: z.array(word).max(24).optional(),
  /** A verb said with se (bati se, veseliti se): its case only with se in the clause. */
  se: z.boolean().optional(),
}

const verbEntry = z.strictObject({
  ...verbForms,
  /** The cases of what it governs, the indirect one first (dati: komu? kaj?: dat, acc). */
  takes: z.array(caseKey).min(1).max(2),
  rule: page,
  examples: examples.optional(),
})

const predicateEntry = z.strictObject({
  /** Words of feeling or state that take who feels it in [case]: všeč, mrzlo (komu?), strah, sram (koga?). */
  words,
  case: caseKey,
  rule: page,
  examples,
})

const pronounEntry = z.strictObject({
  lemma: word,
  person: z.union([z.literal(1), z.literal(2), z.literal(3), z.null()]),
  number: numberKey,
  genders: z.array(genderKey).min(1).max(3),
  /** Its forms by case, the long one first, then the short ones (clitics). */
  forms: z.partialRecord(caseKey, z.array(word).min(1).max(4)),
})

const phraseEntry = z.strictObject({
  /** The phrase as the analysis compares it (lowercase, one space): "na svidenje". */
  words: z.string().min(3).max(60).regex(/^[\p{Ll}]+(?: [\p{Ll}]+)+$/u, 'two or more lowercase words'),
  /** Its words that have a case, and the case they are in. */
  case: caseKey,
  number: numberKey,
  /** The word of it that decides (its preposition); none: the phrase itself. */
  trigger: word.optional(),
  rule: page,
})

const fusedEntry = z.strictObject({
  /** A preposition and a pronoun said as one word: zame (za + mene), nanj (na + njega). */
  form: word,
  preposition: word,
  pronoun: word,
  case: caseKey,
  number: numberKey,
  rule: page,
})

const roleEntry = z.strictObject({ rule: page, examples })

export const caseTriggersSchema = z.strictObject({
  schema: schemaField(CASE_TRIGGERS_SCHEMA),
  language: z.string().regex(/^[a-z]{2,3}$/),
  machine_written: z.boolean(),
  review: z.string().min(10).max(600),
  about: z.string().min(10).max(2000),
  cases: z.array(caseEntry).length(6),
  prepositions: z.array(prepositionEntry).min(1),
  negation: z.strictObject({
    /** The words that negate on their own: ni, nimam, nočem, nisem … */
    words,
    /** The word before a verb that negates it: ne. */
    before_verb: words,
    /** A contrast after which the object may stay in the accusative ("…, ampak revijo"). */
    contrast: words,
    rule: page,
    examples,
  }),
  quantity: z.strictObject({
    /** The words of quantity before a noun: malo, veliko, nekaj, koliko … */
    words,
    /** The nouns of measure (their lemmas): kos, kozarec, liter, kilogram … */
    measures: words,
    /** The numbers from five in their nominative (and accusative): pet … sto, tisoč; figures from 5 too. */
    numbers: words,
    rule: page,
    examples,
  }),
  /** The numbers below five that agree with their noun (en, dva, trije, štirje, oba …), their lemmas. */
  agreeing_numbers: words,
  /** The words that say a noun is two (dva, dve, oba, obe …): only with one of them, or a dual verb, is a form a dual. */
  dual_words: words,
  verbs: z.array(verbEntry).min(1),
  predicates: z.array(predicateEntry).min(1),
  /** Verbs of motion (where to: kam? the accusative) and of rest (where: kje? the locative or the instrumental). */
  motion: z.array(z.strictObject(verbForms)).min(1),
  rest: z.array(z.strictObject(verbForms)).min(1),
  /** Verbs after which a noun says what something is: the nominative (biti, postati …). */
  copulas: z.array(z.strictObject(verbForms)).min(1),
  /** Verbs that take an object in the accusative (videti, imeti …): negated, it goes into the genitive. */
  transitive: z.array(z.strictObject(verbForms)).min(1),
  /** Verbs without an object (iti, spati …): a noun beside them is no accusative object. */
  intransitive: z.array(z.strictObject(verbForms)).min(1),
  /** Nouns of time (dan, ura, ponedeljek …), their lemmas: v ponedeljek, ob petih, ves dan. */
  time: z.strictObject({ nouns: words, rule: page, examples }),
  phrases: z.array(phraseEntry),
  /**
   * Words said of the subject, in the nominative whatever the verb (rad, rada, rado …: Rad pomagam, Rada imam juho): each
   * form with its number and gender.
   */
  predicatives: z.array(z.strictObject({
    lemma: word,
    forms: z.array(z.strictObject({ form: word, number: numberKey, genders: z.array(genderKey).min(1).max(3) })).min(1),
    rule: page,
  })),
  pronouns: z.array(pronounEntry).min(1),
  fused: z.array(fusedEntry),
  /** The roles that decide without a trigger word: the subject, what something is, the object, a call, a genitive after a noun, a dative. */
  roles: z.strictObject({
    subject: roleEntry,
    predicate: roleEntry,
    object: roleEntry,
    address: roleEntry,
    attribute: roleEntry,
    dative: roleEntry,
    partitive: roleEntry,
  }),
})

export type CaseTriggers = z.infer<typeof caseTriggersSchema>
export type PrepositionEntry = CaseTriggers['prepositions'][number]
export type VerbEntry = CaseTriggers['verbs'][number]
export type VerbForms = CaseTriggers['motion'][number]
export type PronounEntry = CaseTriggers['pronouns'][number]

/**
 * The table checked: its schema, every rule a page of the grammar book ([pages]; none given: not checked), each
 * preposition, verb and phrase once, the cases in their order with the shared ids and colours.
 */
export function validateCaseTriggers(input: unknown, pages?: Set<string>): { ok: true; table: CaseTriggers } | { ok: false; errors: string } {
  const r = caseTriggersSchema.safeParse(input)
  if (!r.success) return { ok: false, errors: z.prettifyError(r.error) }
  const t = r.data
  const errors: string[] = []
  t.cases.forEach((c, i) => {
    if (c.key !== CASE_KEYS[i] || c.n !== i + 1 || c.id !== CASE_IDS[c.key]) errors.push(`cases[${i}]: ${c.key} ${c.n} ${c.id}, not ${CASE_KEYS[i]} ${i + 1} ${CASE_IDS[CASE_KEYS[i]]}`)
  })
  const once = (what: string, list: string[]) => {
    const seen = new Set<string>()
    for (const w of list) {
      if (seen.has(w)) errors.push(`${what}: "${w}" twice`)
      seen.add(w)
    }
  }
  once('prepositions', t.prepositions.flatMap(p => p.words))
  once('verbs', t.verbs.map(v => `${v.lemma}${v.se ? ' se' : ''}|${v.present}`))
  once('phrases', t.phrases.map(p => p.words))
  once('fused', t.fused.map(f => f.form))
  once('pronouns', t.pronouns.map(p => p.lemma))
  if (pages) {
    const rules = [
      ...t.cases.map(c => c.page),
      ...t.prepositions.flatMap(p => p.takes.flatMap(x => [x.rule, ...(x.time ? [x.time] : [])])),
      t.negation.rule, t.quantity.rule, t.time.rule,
      ...t.verbs.map(v => v.rule), ...t.predicates.map(p => p.rule), ...t.phrases.map(p => p.rule), ...t.fused.map(f => f.rule), ...t.predicatives.map(p => p.rule),
      ...Object.values(t.roles).map(r => r.rule),
    ]
    for (const r of new Set(rules)) if (!pages.has(r)) errors.push(`rule "${r}": no such page in the grammar book`)
  }
  return errors.length ? { ok: false, errors: errors.join('\n') } : { ok: true, table: t }
}

/** Where a language's table is: companion/grammar/<language>/data/case-triggers.json (data, not a page of the book). */
export const caseTriggersPath = (grammarRoot: string, language: string) => join(grammarRoot, language, 'data', 'case-triggers.json')

/** A language's trigger table, read again when it changes; null without one, or when it doesn't validate (said once). */
export class CaseTriggersFile {
  private mtime = -1
  private table: CaseTriggers | null = null

  constructor(private readonly path: string, private readonly log?: Log) {}

  current(): CaseTriggers | null {
    const mtime = existsSync(this.path) ? statSync(this.path).mtimeMs : 0
    if (mtime === this.mtime) return this.table
    this.mtime = mtime
    this.table = null
    if (!mtime) return null
    try {
      const v = validateCaseTriggers(JSON.parse(readFileSync(this.path, 'utf8')))
      if (v.ok) this.table = v.table
      else this.log?.(`case triggers ${this.path} left out: ${v.errors}`)
    } catch (e) {
      this.log?.(`case triggers ${this.path} unreadable: ${(e as Error).message}`)
    }
    return this.table
  }
}
