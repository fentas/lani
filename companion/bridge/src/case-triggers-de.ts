// German's trigger table (docs/plans/05-built/case-de.md): what decides a German word's case, as data
// (companion/grammar/de/data/case-triggers.json, lani.case-triggers/v0 with language "de", made by case-triggers.py beside
// it). German shows its case mostly in the article, so the table has every determiner's sixteen forms and the adjective
// endings after each kind, beside what Slovene's has too: the prepositions by case (the two-way ones by motion and rest,
// the genitive's with the dative people say), the preposition fused with the article (im, zum, ins), the verbs that
// take a dative, a dative and an accusative, or a genitive, the verbs of motion and of rest, the copulas, the personal
// pronouns, the relative pronoun's own forms, the n-Deklination's nouns, times without a preposition and fixed phrases.
// Every entry names its rule: the German grammar book's page that explains it. The case lens reads it (case-lens-de.ts),
// and so do the case trainer's checks of the German bank (case-bank.ts) and the forms it declines (german-forms.ts).
import { z } from 'zod'
import { schemaField } from './schema'

/** The same schema id as Slovene's table (case-triggers.ts): the language tells them apart. */
const CASE_TRIGGERS_SCHEMA = 'lani.case-triggers/v0'

/** German's four cases by the slots' keys, in the order the course teaches them (Nominativ, Akkusativ, Dativ, Genitiv). */
export const DE_CASE_KEYS = ['nom', 'acc', 'dat', 'gen'] as const
export type DeCaseKey = (typeof DE_CASE_KEYS)[number]
/** Their ids (the bank's, the wire's, the trainer's mastery keys), and the Austrian school's number of each (der 3. Fall). */
export const DE_CASE_IDS = { nom: 'nominativ', acc: 'akkusativ', dat: 'dativ', gen: 'genitiv' } as const satisfies Record<DeCaseKey, string>
export const DE_FALL = { nom: 1, gen: 2, dat: 3, acc: 4 } as const satisfies Record<DeCaseKey, number>
/** The colours are Slovene's for the same case (one look for a learner of both). */
export const DE_COLOURS = { nom: '#4A7FC1', acc: '#3E9B5F', dat: '#D69A2D', gen: '#C2503C' } as const satisfies Record<DeCaseKey, string>
/** Each case's grammar page (the nominative has none of its own: the overview of the four). */
export const DE_CASE_PAGES = { nom: 'faelle', acc: 'akkusativ', dat: 'dativ', gen: 'genitiv' } as const satisfies Record<DeCaseKey, string>

/** A determiner's or an adjective's slot: the case and the gender, or the plural ("dat.f", "gen.pl"). */
export const DE_SLOTS = DE_CASE_KEYS.flatMap(c => ['m', 'f', 'n', 'pl'].map(g => `${c}.${g}`))
export type DeGenderKey = 'm' | 'f' | 'n'

const caseKey = z.enum(DE_CASE_KEYS)
const numberKey = z.enum(['sg', 'pl'])
const genderKey = z.enum(['m', 'f', 'n'])
/** A word as the analysis compares it: lowercase letters (ä, ö, ü, ß), one word. */
const word = z.string().min(1).max(40).regex(/^[\p{Ll}]+$/u, 'one word, lowercase letters only')
const page = z.string().regex(/^[a-z0-9][a-z0-9-]{0,63}$/, 'a grammar page id, e.g. "praepositionen-dativ"')
const words = z.array(word).min(1)
const slot = z.string().refine(s => DE_SLOTS.includes(s), 'a slot, e.g. "dat.f"')

/**
 * An example of an entry: a sentence, its translation, the word it decides ([word] as written, [nth]: which of the same
 * words, from 0), the case and number the analysis must give it and the word that decides it ([trigger]; none: its role).
 */
const example = z.strictObject({
  de: z.string().trim().min(3).max(160),
  en: z.string().trim().min(2).max(200),
  word: z.string().min(1).max(40),
  nth: z.number().int().min(0).max(5).optional(),
  case: caseKey,
  number: numberKey,
  trigger: z.string().min(1).max(40).optional(),
})
export type DeExample = z.infer<typeof example>
const examples = z.array(example).min(1).max(6)

export const DE_SENSES = ['motion', 'rest', 'time', 'with', 'from', 'for', 'towards', 'other'] as const

const caseEntry = z.strictObject({
  key: caseKey,
  id: z.string(),
  /** The Austrian school's number (1. Fall … 4. Fall: Nominativ, Genitiv, Dativ, Akkusativ). */
  n: z.number().int().min(1).max(4),
  questions: z.array(z.string().min(3).max(20)).min(1).max(2),
  colour: z.string().regex(/^#[0-9A-F]{6}$/),
  page,
})

const determinerEntry = z.strictObject({
  lemma: word,
  /** The article der (its own table), a der-word (dieser, jeder: weak adjectives after it), an ein-word (ein, kein) or a possessive. */
  kind: z.enum(['definite', 'der', 'ein', 'possessive']),
  forms: z.partialRecord(z.string(), word),
})

const prepositionEntry = z.strictObject({
  words,
  takes: z
    .array(z.strictObject({
      case: caseKey,
      sense: z.enum(DE_SENSES),
      rule: page,
      /** The rule when the noun is one of time (im Mai, am Montag, vor einer Woche). */
      time: page.optional(),
      /** Said, and right in talk; writing wants the other (wegen dem Regen: the genitive's preposition with a dative). */
      spoken: z.boolean().optional(),
    }))
    .min(1)
    .max(3),
  examples,
})

const contractionEntry = z.strictObject({
  form: word,
  preposition: word,
  article: word,
  case: caseKey,
  genders: z.array(genderKey).min(1).max(2),
  spoken: z.boolean().optional(),
  /** The usual form, written too: the article alone after its preposition wants a reason (im, am, zum, zur, beim, vom, ins, ans). */
  usual: z.boolean().optional(),
})

/** A verb with its forms (as the generator makes them); [particle] a separable one's (zuhören: höre … zu), [joined] its forms with it. */
const verbForms = {
  lemma: z.string().min(2).max(40).regex(/^[\p{Ll}]+$/u),
  forms: z.array(word).min(1).max(40),
  particle: word.optional(),
  joined: z.array(word).max(40).optional(),
  reflexive: z.boolean().optional(),
}
const verbEntry = z.strictObject({ ...verbForms, takes: z.array(caseKey).min(1).max(2), rule: page })

const pronounEntry = z.strictObject({
  lemma: word,
  person: z.union([z.literal(1), z.literal(2), z.literal(3), z.null()]),
  number: numberKey,
  genders: z.array(genderKey).min(1).max(3),
  forms: z.partialRecord(caseKey, z.array(word).min(1).max(3)),
})

const roleEntry = z.strictObject({ rule: page, examples })

export const caseTriggersDeSchema = z.strictObject({
  schema: schemaField(CASE_TRIGGERS_SCHEMA),
  language: z.literal('de'),
  machine_written: z.boolean(),
  review: z.string().min(10).max(800),
  about: z.string().min(10).max(2000),
  cases: z.array(caseEntry).length(4),
  determiners: z.array(determinerEntry).min(1),
  adjective_endings: z.strictObject({ weak: z.record(slot, z.string()), mixed: z.record(slot, z.string()), strong: z.record(slot, z.string()) }),
  /** An adjective's stem before an ending where it isn't the word (hoch: hoh-e; dunkel: dunkl-e). */
  adjective_stems: z.record(word, word),
  /** Adjectives that take no ending (rosa, lila). */
  adjective_fixed: z.array(word),
  prepositions: z.array(prepositionEntry).min(1),
  contractions: z.array(contractionEntry),
  /**
   * A verb, an adjective or a noun with a fixed preposition (warten auf, stolz auf, Angst vor), its forms, the case the
   * chunk takes: with a two-way preposition the chunk decides, not wo or wohin.
   */
  chunks: z.array(z.strictObject({ ...verbForms, preposition: word, case: caseKey, rule: page })),
  verbs: z.array(verbEntry).min(1),
  predicates: z.array(z.strictObject({ words, case: caseKey, rule: page, examples })),
  motion: z.array(z.strictObject(verbForms)).min(1),
  rest: z.array(z.strictObject(verbForms)).min(1),
  copulas: z.array(z.strictObject(verbForms)).min(1),
  pronouns: z.array(pronounEntry).min(1),
  relative: z.strictObject({ forms: z.record(slot, word), rule: page, examples }),
  /** The nouns that take -n (n-Deklination): the oblique form, the genitive where it adds -s (Namens), a neuter's own. */
  n_declension: z.array(z.strictObject({
    lemma: z.string().min(2).max(40).regex(/^\p{Lu}\p{Ll}+$/u),
    oblique: z.string().min(2).max(40),
    genitive: z.string().min(2).max(40).optional(),
    accusative: z.string().min(2).max(40).optional(),
    plural: z.string().min(2).max(40).optional(),
    gender: genderKey.optional(),
  })),
  time: z.strictObject({ nouns: z.array(z.string().regex(/^\p{Lu}\p{Ll}+$/u)).min(1), rule: page, examples }),
  time_genitive: z.strictObject({ phrases: z.array(z.string().regex(/^[\p{Ll}]+ [\p{Ll}]+$/u)).min(1), rule: page, examples }),
  phrases: z.array(z.strictObject({
    words: z.string().min(3).max(60).regex(/^[\p{Ll}]+(?: [\p{Ll}]+)+$/u),
    case: caseKey,
    number: numberKey,
    trigger: word.optional(),
    rule: page,
  })),
  roles: z.strictObject({ subject: roleEntry, predicate: roleEntry, object: roleEntry, dative: roleEntry, attribute: roleEntry, address: roleEntry }),
})

export type CaseTriggersDe = z.infer<typeof caseTriggersDeSchema>
export type DePreposition = CaseTriggersDe['prepositions'][number]
export type DeVerb = CaseTriggersDe['verbs'][number]
export type DeVerbForms = CaseTriggersDe['motion'][number]
export type DeChunk = CaseTriggersDe['chunks'][number]

/**
 * German's table checked: its schema, the cases in the course's order with their ids, the Austrian numbers, the colours
 * and the pages; every determiner with its sixteen forms (ein-words without a plural where they have none), every
 * ending's slot; each preposition and contraction once; every rule a page of the grammar book ([pages], when given).
 */
export function validateCaseTriggersDe(input: unknown, pages?: Set<string>): { ok: true; table: CaseTriggersDe } | { ok: false; errors: string } {
  const r = caseTriggersDeSchema.safeParse(input)
  if (!r.success) return { ok: false, errors: z.prettifyError(r.error) }
  const t = r.data
  const errors: string[] = []
  t.cases.forEach((c, i) => {
    const k = DE_CASE_KEYS[i]!
    if (c.key !== k || c.id !== DE_CASE_IDS[k] || c.n !== DE_FALL[k] || c.colour !== DE_COLOURS[k] || c.page !== DE_CASE_PAGES[k])
      errors.push(`cases[${i}]: ${c.key} ${c.id} ${c.n} ${c.colour} ${c.page}, not ${k} ${DE_CASE_IDS[k]} ${DE_FALL[k]} ${DE_COLOURS[k]} ${DE_CASE_PAGES[k]}`)
  })
  for (const d of t.determiners) {
    const plural = !(d.lemma === 'ein' || d.lemma === 'jeder')
    const want = d.lemma === 'alle' || d.lemma === 'beide' ? DE_SLOTS.filter(s => s.endsWith('.pl')) : DE_SLOTS.filter(s => plural || !s.endsWith('.pl'))
    const missing = want.filter(s => !d.forms[s])
    if (missing.length) errors.push(`determiners.${d.lemma}: forms missing: ${missing.join(', ')}`)
    const extra = Object.keys(d.forms).filter(s => !want.includes(s))
    if (extra.length) errors.push(`determiners.${d.lemma}: not its slots: ${extra.join(', ')}`)
  }
  for (const [k, e] of Object.entries(t.adjective_endings)) {
    const missing = DE_SLOTS.filter(s => !(s in e))
    if (missing.length) errors.push(`adjective_endings.${k}: missing ${missing.join(', ')}`)
  }
  const once = (what: string, list: string[]) => {
    const seen = new Set<string>()
    for (const w of list) {
      if (seen.has(w)) errors.push(`${what}: "${w}" twice`)
      seen.add(w)
    }
  }
  once('prepositions', t.prepositions.flatMap(p => p.words))
  once('contractions', t.contractions.map(c => c.form))
  once('verbs', t.verbs.map(v => v.lemma))
  once('phrases', t.phrases.map(p => p.words))
  once('n_declension', t.n_declension.map(n => n.lemma))
  const preps = new Set(t.prepositions.flatMap(p => p.words))
  for (const c of t.contractions) {
    if (!preps.has(c.preposition)) errors.push(`contractions.${c.form}: "${c.preposition}" is no preposition of the table`)
    const def = t.determiners.find(d => d.kind === 'definite')
    const slots = c.genders.map(g => `${c.case}.${g}`)
    if (def && slots.some(s => def.forms[s] !== c.article)) errors.push(`contractions.${c.form}: "${c.article}" isn't the article's ${slots.join('/')}`)
  }
  if (pages) {
    const rules = [
      ...t.cases.map(c => c.page),
      ...t.prepositions.flatMap(p => p.takes.flatMap(x => [x.rule, ...(x.time ? [x.time] : [])])),
      ...t.verbs.map(v => v.rule), ...t.chunks.map(c => c.rule), ...t.predicates.map(p => p.rule), ...t.phrases.map(p => p.rule),
      t.relative.rule, t.time.rule, t.time_genitive.rule, ...Object.values(t.roles).map(r => r.rule),
    ]
    for (const r of new Set(rules)) if (!pages.has(r)) errors.push(`rule "${r}": no such page in the grammar book`)
  }
  return errors.length ? { ok: false, errors: errors.join('\n') } : { ok: true, table: t }
}
