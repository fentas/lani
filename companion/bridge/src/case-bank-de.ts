// German's case trainer bank (companion/cases/de/bank.json, lani.cases/v0 with language "de"; docs/plans/05-built/case-de.md):
// Slovene's format (case-bank.ts) for German's four cases. The sentence is under the bank's language ("de"), its meaning
// under each base of a German village (sl, en, it), as Slovene's sentence is under "sl" with en, de and it. A German item
// frames its noun phrase ([phrase]: the determiner, the adjectives and the noun), since German shows the case mostly in
// the article; its noun's [gender] lets the checks hold the article to the case. "Wo oder wohin?" asks the two-way
// prepositions (in der Hütte, in die Hütte), Wo, wohin oder woher? adds aus and von, zu, bei oder nach? the people and
// the towns. A frame of "Dekliniere!" names the determiner its cue has ("(der Nachbar)" → dem Nachbarn) and declines the
// learner's nouns by the rules of german-forms.ts, as the app does (data/GermanForms.kt).
import { z } from 'zod'
import { DE_CASE_IDS, DE_CASE_KEYS, DE_CASE_PAGES, type DeCaseKey, type DeGenderKey } from './case-triggers-de'
import { bareNoun, dictPlural, type DeNumber, type GermanForms } from './german-forms'
import { normalizeForm, type Lexicon } from './lexicon'

/** German's case ids in the course's order (Nominativ, Akkusativ, Dativ, Genitiv), the bank's and the wire's. */
export const DE_CASES = DE_CASE_KEYS.map(k => DE_CASE_IDS[k]) as ['nominativ', 'akkusativ', 'dativ', 'genitiv']
export type DeCaseId = (typeof DE_CASES)[number]
export const DE_CASE_SLOT = Object.fromEntries(DE_CASE_KEYS.map(k => [DE_CASE_IDS[k], k])) as Record<DeCaseId, DeCaseKey>
export const DE_CASE_EN: Record<DeCaseId, string> = { nominativ: 'nominative', akkusativ: 'accusative', dativ: 'dative', genitiv: 'genitive' }
const LEVEL_RANK: Record<string, number> = { A1: 1, A2: 2, B1: 3, B2: 4 }
const LEVELS = ['A1', 'A2', 'B1', 'B2'] as const
/** A sentence's tokens and a token bare, as the app taps them (case-bank.ts tokensOf and bare: the same). */
const tokensOf = (sentence: string): string[] => sentence.trim().split(/\s+/).filter(Boolean)
const bare = (token: string): string => token.replace(/^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu, '')

/** The determiners a frame's cue may have: the article, ein, kein, the possessives, dieser and jeder; none (strong adjectives, a plural). */
export const FRAME_DETERMINERS = ['der', 'ein', 'kein', 'mein', 'dein', 'sein', 'ihr', 'unser', 'euer', 'dieser', 'jeder', 'none'] as const

const ID = /^[a-z0-9][a-z0-9-]{0,63}$/
const id = z.string().regex(ID, 'kebab-case, a-z 0-9 -')
const line = z.string().trim().min(1).max(160)
const said = z.string().trim().min(2).max(120)
/** A text in a German village's bases: Slovene, English, Italian. */
const bases = z.strictObject({ sl: line, en: line, it: line })
const level = z.enum(LEVELS)
const word = z.string().trim().min(1).max(40).regex(/^[\p{L}][\p{L}' -]*$/u, 'a word')
const index = z.number().int().min(0).max(20)

const ruleSpec = z.strictObject({
  case: z.enum(DE_CASES),
  page: id,
  /** The words that trigger it, lower case, as the sentences have them (every form of a verb used: hilft, helfe, half …). */
  words: z.array(word).min(1).max(600).optional(),
  /** The rule in a line in German ("mit + Dativ"), and in each base. */
  de: z.string().trim().min(1).max(80),
  sl: line,
  en: line,
  it: line,
})

const common = {
  id,
  level,
  /** The sentence, in German: the word asked about in its case. */
  de: said,
  sl: line,
  en: line,
  it: line,
  /** The token asked about (tokensOf): the noun, a pronoun, a name. */
  word: index,
  /** The tokens framed, from … to: the noun phrase (its determiner, adjectives and noun); none: the word alone. */
  phrase: z.tuple([index, index]).optional(),
  lemma: word,
  pos: z.enum(['noun', 'pron', 'name']).default('noun'),
  /** The noun's gender (a noun's item must say it: the checks hold the article to it). */
  gender: z.enum(['m', 'f', 'n']).optional(),
  case: z.enum(DE_CASES),
  number: z.enum(['sg', 'pl']),
  why: z.string().regex(/^[a-z0-9][a-z0-9-]{0,40}$/),
  trigger: index.nullable(),
  also: z.array(index).max(3).default([]),
  role: z.enum(['subject', 'object']).optional(),
  /** The form's other readings worth naming ("acc.sg"), each one its determiner can be. */
  ambiguous: z.array(z.string().regex(/^(nom|acc|dat|gen)\.(sg|pl)$/)).max(4).default([]),
  note: bases.optional(),
  /** A form people say (wegen dem Regen, dem Bub): right, never a mistake; labelled, with the written one. */
  spoken: z.strictObject({ form: z.string().trim().min(1).max(60), standard: z.string().trim().min(1).max(60), note: bases }).optional(),
  from: z.string().trim().min(1).max(80).default('bank'),
}

const whichItem = z.strictObject({ ...common, kind: z.literal('which') })

/**
 * "Wo oder wohin?": the gap chosen among [options]: [question] wo-wohin (a two-way preposition: in der Hütte, in die
 * Hütte), wo-wohin-woher (with aus or von), zu-bei-nach (to, at, from a person or a town); the [cue] the noun with its
 * article as the dictionary has it ("die Hütte"), the [trigger] the verb that says where or where to, outside the gap.
 */
const placeItem = z.strictObject({
  ...common,
  kind: z.literal('place'),
  question: z.enum(['wo-wohin', 'wo-wohin-woher', 'zu-bei-nach']),
  gap: z.tuple([index, index]),
  options: z.array(z.string().trim().min(1).max(40)).min(2).max(3),
  answer: z.number().int().min(0).max(2),
  cue: z.string().trim().min(1).max(40).optional(),
  scene: id.optional(),
})

export const caseItemDe = z.discriminatedUnion('kind', [whichItem, placeItem])
export type CaseItemDe = z.infer<typeof caseItemDe>

/**
 * A frame of "Dekliniere! · Decline it": a sentence with a known trigger and the gap ({x}) the noun phrase goes into, its
 * cue the phrase in the nominative with [det] ("der Nachbar", "ein Apfel", "mein Bruder"; none: an adjective alone or a
 * plural), an adjective with it where [adj]; translations with "…" where it goes.
 */
const frameSpec = z.strictObject({
  id,
  case: z.enum(DE_CASES),
  number: z.enum(['sg', 'pl']),
  why: z.string().regex(/^[a-z0-9][a-z0-9-]{0,40}$/),
  level,
  de: z.string().trim().min(3).max(100),
  sl: line,
  en: line,
  it: line,
  det: z.enum(FRAME_DETERMINERS),
  adj: z.boolean().default(false),
  needs: z.array(id).max(4).default([]),
  gender: z.array(z.enum(['m', 'f', 'n'])).min(1).max(3).optional(),
})
export type CaseFrameDe = z.infer<typeof frameSpec>

/** An adjective the frames decline (its forms by the table's endings and stems). */
const adjectiveSpec = z.strictObject({ lemma: z.string().regex(/^[\p{Ll}]+$/u), kind: z.literal('adj').default('adj'), level, sl: line, en: line, it: line })

/** A noun the frames use when the learner has too few: its gender, its plural (none: kept to the singular), what it means. */
const nounSpec = z.strictObject({
  lemma: z.string().regex(/^\p{Lu}[\p{L}]+$/u),
  gender: z.enum(['m', 'f', 'n']),
  plural: z.string().regex(/^\p{Lu}[\p{L}]+$/u).optional(),
  level,
  sl: line,
  en: line,
  it: line,
})

export const bankSpecDe = z.strictObject({
  schema: z.literal('lani.cases/v0'),
  language: z.literal('de'),
  machine_written: z.boolean(),
  reviewed: z.boolean(),
  review: z.string().trim().min(1).max(2000),
  rules: z.record(z.string().regex(/^[a-z0-9][a-z0-9-]{0,40}$/), ruleSpec),
  items: z.array(caseItemDe).max(2000),
  frames: z.array(frameSpec).max(200),
  adjectives: z.array(adjectiveSpec).max(80),
  nouns: z.array(nounSpec).max(200),
  /** Mass nouns the frames keep to the singular (Milch, Mehl). */
  uncountable: z.array(z.string().regex(/^\p{Lu}[\p{L}]+$/u)).max(200).optional(),
})
export type CaseBankDe = z.infer<typeof bankSpecDe>
export type CaseRuleDe = CaseBankDe['rules'][string]

/** What German's checks read: the table's forms, the dictionary, the packs' nouns (gender, plural), the pages and levels, the scenes. */
export type CaseCheckDe = {
  forms: GermanForms
  dict?: Lexicon
  /** The packs' nouns by lemma: gender and nominative plural ("Steg" → m, "Stege"). */
  nouns: Map<string, { gender: DeGenderKey; plural?: string }>
  pages: Map<string, string>
  scenes: Set<string>
}

/** The packs' German nouns ("der Steg", gender m, plural "die Stege") by lemma, the first pack's where two have one. */
export function germanPackNouns(packs: { words: { de?: string; gender?: string; plural?: string }[] }[]): Map<string, { gender: DeGenderKey; plural?: string }> {
  const out = new Map<string, { gender: DeGenderKey; plural?: string }>()
  for (const p of packs) {
    for (const w of p.words) {
      const lemma = bareNoun(w.de)
      if (!lemma || !w.gender || !['m', 'f', 'n'].includes(w.gender) || out.has(lemma)) continue
      const plural = bareNoun(w.plural)
      out.set(lemma, { gender: w.gender as DeGenderKey, ...(plural ? { plural } : {}) })
    }
  }
  return out
}

/** The prepositions whose fusion with the article is the usual form, written too (im, am, beim, vom, zum, zur, ins, ans). */
const FUSING = new Set(['in', 'an', 'bei', 'von', 'zu'])

const norm = (s: string) => normalizeForm(s, 'de')

/** What the item's article must be: the slot's form of a determiner, and the noun's forms by its rules (where they are sure). */
function nounOf(c: CaseCheckDe, lemma: string, gender: DeGenderKey): { gender: DeGenderKey; plural?: string } {
  const pack = c.nouns.get(lemma)
  if (pack?.plural) return pack
  const plural = dictPlural(c.dict, lemma)
  return { gender: pack?.gender ?? gender, ...(plural ? { plural } : {}) }
}

/** The problems of German items (each "<id>: …"), and how many noun phrases the rules judged. */
export function checkItemsDe(items: CaseItemDe[], rules: Record<string, CaseRuleDe>, c: CaseCheckDe): { problems: string[]; judged: number } {
  const problems: string[] = []
  let judged = 0
  const seen = new Set<string>()
  const contraction = new Map(c.forms.table.contractions.map(x => [x.form, x]))
  for (const it of items) {
    const p = (m: string) => problems.push(`${it.id}: ${m}`)
    if (seen.has(it.id)) p('the id is taken')
    seen.add(it.id)
    const toks = tokensOf(it.de)
    const at = (i: number) => bare(toks[i] ?? '')
    if (!/[.!?…]["»“]?$/.test(it.de)) p('the sentence ends with . ! ? or …')
    if (toks.length > 14) p(`${toks.length} words: at most 14`)
    if (it.word >= toks.length || !at(it.word)) p(`word ${it.word} is past the sentence's ${toks.length} words`)
    const [from, to] = it.phrase ?? [it.word, it.word]
    if (from > it.word || to < it.word || to >= toks.length) p(`phrase ${from}–${to} doesn't hold the word ${it.word}`)
    const rule = rules[it.why]
    if (!rule) p(`why "${it.why}" is no rule of the bank`)
    else {
      if (rule.case !== it.case) p(`why "${it.why}" is the rule of ${rule.case}, the item is ${it.case}`)
      if (!c.pages.has(rule.page)) p(`the rule's page "${rule.page}" isn't in the grammar book`)
      else if ((LEVEL_RANK[it.level] ?? 0) < (LEVEL_RANK[c.pages.get(rule.page)!] ?? 0)) p(`level ${it.level} is below its page ${rule.page}'s ${c.pages.get(rule.page)}`)
    }
    const key = DE_CASE_SLOT[it.case]
    if (!c.pages.has(DE_CASE_PAGES[key])) p(`the case's page "${DE_CASE_PAGES[key]}" isn't in the grammar book`)
    if (it.trigger === null) {
      if (it.kind === 'which' && !it.role) p('no trigger and no role: say subject or object')
    } else {
      if (it.trigger >= toks.length) p(`trigger ${it.trigger} is past the sentence`)
      else if (it.trigger >= from && it.trigger <= to && !contraction.has(norm(at(it.trigger)))) p('the trigger is inside the phrase asked about')
      else if (it.kind === 'which' && rule?.words && !rule.words.map(norm).includes(norm(at(it.trigger)))) p(`trigger "${at(it.trigger)}" isn't a word of the rule "${it.why}" (${rule.words.slice(0, 8).join(', ')}…)`)
      if (it.role) p('a trigger and a role: one or the other')
    }
    for (const a of it.also) if (a >= toks.length || a === it.word || a === it.trigger) p(`also ${a}: past the sentence, the word or the trigger`)
    if (it.spoken && !norm(it.de).includes(norm(it.spoken.form))) p(`the spoken form "${it.spoken.form}" isn't in the sentence`)
    // the noun phrase: the article (or the contraction before it) in the item's case, number and gender, the noun's form
    if (it.pos === 'noun') {
      if (!it.gender) p("a noun's item says its gender")
      else {
        judged++
        const g = it.gender as DeGenderKey
        const n = it.number as DeNumber
        const first = norm(at(from))
        const before = from > 0 ? norm(at(from - 1)) : ''
        const fused = contraction.get(first) ?? contraction.get(before)
        const dets = c.forms.determiner(first)
        if (fused) {
          if (fused.case !== key || n !== 'sg' || !fused.genders.includes(g)) p(`"${fused.form}" is ${fused.article}: ${fused.case} of ${fused.genders.join('/')}, not ${key}.${n} of ${g}`)
        } else if (dets.length && !it.spoken) {
          const fits = dets.some(d => d.case === key && d.number === n && (n === 'pl' || d.gender === g))
          if (!fits) p(`"${at(from)}" isn't a determiner's ${key}.${n === 'pl' ? 'pl' : g} (${dets.map(d => `${d.case}.${d.gender ?? 'pl'}`).join(', ')})`)
          // the article alone after a preposition that fuses with it: the contraction is what people say and write (zum, im, ins)
          if (FUSING.has(before) && n === 'sg' && dets.some(d => d.kind === 'definite') && c.forms.table.contractions.some(x => x.preposition === before && x.case === key && x.genders.includes(g) && x.usual) && !it.note)
            p(`"${before} ${at(from)}": the contraction is the usual form; a note says why the article stands alone`)
        }
        // the adjectives between: their ending after that determiner
        const det = fused ? 'der' : (dets.find(d => d.case === key && d.number === n && (n === 'pl' || d.gender === g))?.lemma ?? null)
        for (let i = from + (dets.length && !fused ? 1 : 0); i < it.word; i++) {
          const a = at(i)
          if (!a || /^\p{Lu}/u.test(a)) continue
          // a word may read as two adjectives (nächsten: nächst, and nah's superlative): one of them has to fit
          const adjs = (c.dict?.grammarReadings(norm(a)) ?? []).filter(r => r.pos === 'adj')
          if (!adjs.length) continue
          const wants = adjs.map(r => c.forms.adjectiveForm(r.lemma, det, key, g, n))
          if (!wants.some(w => norm(a) === norm(w)) && !it.spoken) p(`"${a}" isn't ${adjs[0].lemma} after ${det ?? 'no determiner'} in ${key}.${n === 'pl' ? 'pl' : g} (${wants[0]})`)
        }
        const noun = nounOf(c, it.lemma, g)
        const slot = c.forms.noun(it.lemma, g, noun.plural, key, n)
        const form = at(it.word)
        // a fixed phrase of the table keeps its old form (zu Hause, nach Hause)
        const fixed = c.forms.table.phrases.some(ph => norm(it.de).includes(ph.words) && ph.words.split(' ').includes(norm(form)))
        if (slot.sure && slot.forms.length && !fixed && ![...slot.forms, ...slot.also].map(norm).includes(norm(form)) && !(it.spoken && norm(it.spoken.form).includes(norm(form))))
          p(`"${form}" isn't the ${key}.${n} of ${it.lemma} (${[...slot.forms, ...slot.also].join('/')})`)
        // an ambiguous phrase only where the sentence decides it: by its trigger, else its note
        const others = dets.filter(d => d.number === n && (n === 'pl' || d.gender === g) && d.case !== key)
        if (others.length && it.trigger === null && !it.note) p(`"${at(from)} ${form}" is also ${[...new Set(others.map(d => d.case))].join(', ')}: no word decides it, so the note says how the sentence does`)
        for (const a of it.ambiguous) {
          const [ac, an] = a.split('.')
          if (dets.length && !dets.some(d => d.case === ac && d.number === an && (an === 'pl' || d.gender === g))) p(`ambiguous ${a}: "${at(from)}" isn't that`)
        }
      }
    } else if (it.pos === 'pron') {
      const f = norm(at(it.word))
      const pron = c.forms.table.pronouns.filter(x => Object.entries(x.forms).some(([k, fs]) => k === key && fs!.includes(f)))
      const rel = c.forms.table.relative.forms[`${key}.${it.number === 'pl' ? 'pl' : (it.gender ?? 'm')}`] === f
      if (!pron.length && !rel) p(`"${at(it.word)}" isn't a pronoun's ${key}`)
    }
    if (it.kind === 'place') {
      const [gf, gt] = it.gap
      if (gf > gt || gt >= toks.length) p(`gap ${gf}–${gt} isn't in the sentence`)
      else {
        const gapText = toks.slice(gf, gt + 1).map(bare).join(' ')
        if (norm(it.options[it.answer] ?? '') !== norm(gapText)) p(`options[${it.answer}] "${it.options[it.answer]}" isn't the gap's "${gapText}"`)
        if (it.trigger !== null && it.trigger >= gf && it.trigger <= gt) p('the verb that says wo or wohin (the trigger) stands outside the gap')
        if (it.word < gf || it.word > gt) p('the word asked about is in the gap')
      }
      if (it.answer >= it.options.length) p(`answer ${it.answer}: only ${it.options.length} options`)
      if (new Set(it.options.map(norm)).size !== it.options.length) p('two options are the same')
      if (it.scene && !c.scenes.has(it.scene)) p(`scene "${it.scene}" isn't one of the village's`)
      if (it.question === 'wo-wohin' && it.options.length !== 2) p('wo oder wohin: two options, the dative and the accusative')
      if (it.question === 'wo-wohin-woher' && it.options.length !== 3) p('wo, wohin oder woher: three options')
    }
  }
  return { problems, judged }
}

/** The problems of German frames, adjectives and fallback nouns. */
export function checkRestDe(bank: Pick<CaseBankDe, 'rules' | 'frames' | 'adjectives' | 'nouns' | 'uncountable'>, c: CaseCheckDe): { problems: string[]; judged: number } {
  const problems: string[] = []
  let judged = 0
  for (const [k, r] of Object.entries(bank.rules)) if (!c.pages.has(r.page)) problems.push(`rules.${k}: its page "${r.page}" isn't in the grammar book`)
  const ids = new Set<string>()
  for (const f of bank.frames) {
    const p = (m: string) => problems.push(`frames.${f.id}: ${m}`)
    if (ids.has(f.id)) p('the id is taken')
    ids.add(f.id)
    const rule = bank.rules[f.why]
    if (!rule) p(`why "${f.why}" is no rule`)
    else if (rule.case !== f.case) p(`why "${f.why}" is ${rule.case}'s, the frame is ${f.case}`)
    if ((f.de.match(/\{x\}/g) ?? []).length !== 1) p('{x} once: where the noun phrase goes')
    if (/\{[a-z]+\}/.test(f.de.replace('{x}', ''))) p('only {x}')
    for (const l of ['sl', 'en', 'it'] as const) if ((f[l].match(/…/g) ?? []).length !== 1) p(`${l}: "…" once, where the word goes`)
    if (f.det === 'ein' && f.number === 'pl') p('ein has no plural: kein, a possessive, der or none')
    if (f.det === 'jeder' && f.number === 'pl') p('jeder has no plural: alle is another frame')
    if (f.det === 'none' && f.number === 'sg' && !f.adj) p('no determiner in the singular: only with an adjective (mit frischem Brot)')
    const before = norm(f.de.slice(0, f.de.indexOf('{x}')).trim().split(/\s+/).at(-1) ?? '')
    if (FUSING.has(before) && f.det === 'der' && f.number === 'sg') p(`"${before} {x}" with der: the contraction would be the usual form (zum, im, ins, am); take another determiner, or the plural`)
    for (const n of f.needs) if (!c.pages.has(n)) p(`needs "${n}": not in the grammar book`)
  }
  for (const a of bank.adjectives) {
    const lex = c.dict?.grammarReadings(norm(a.lemma)).find(r => r.pos === 'adj')
    if (lex) judged++
    if (c.dict && !lex) problems.push(`adjectives.${a.lemma}: not an adjective of the dictionary`)
  }
  for (const n of bank.nouns) {
    const p = (m: string) => problems.push(`nouns.${n.lemma}: ${m}`)
    const pack = c.nouns.get(n.lemma)
    const g = c.dict?.genderOf(norm(n.lemma), 'noun') ?? pack?.gender
    if (g) {
      judged++
      if (g !== n.gender) p(`gender ${n.gender}, the dictionary or the packs say ${g}`)
    }
    if (pack?.plural && n.plural && pack.plural !== n.plural) p(`plural ${n.plural}, the packs say ${pack.plural}`)
    if (!c.forms.noun(n.lemma, n.gender, n.plural, 'gen', 'sg').sure) p("its genitive isn't sure by the rules (des Busses): leave it out")
  }
  for (const u of bank.uncountable ?? []) if (!c.nouns.has(u) && c.dict?.genderOf(norm(u), 'noun') === undefined) problems.push(`uncountable.${u}: no noun of the packs or the dictionary`)
  return { problems, judged }
}

/** German's bank validated: its format, then the checks. */
export function validateBankDe(input: unknown, c?: CaseCheckDe): { ok: true; bank: CaseBankDe; judged: number; unjudged: string[] } | { ok: false; errors: string } {
  const v = bankSpecDe.safeParse(input)
  if (!v.success) return { ok: false, errors: z.prettifyError(v.error) }
  if (!c) return { ok: true, bank: v.data, judged: 0, unjudged: [] }
  const items = checkItemsDe(v.data.items, v.data.rules, c)
  const rest = checkRestDe(v.data, c)
  const problems = [...items.problems, ...rest.problems]
  if (problems.length) return { ok: false, errors: problems.map(p => `✖ ${p}`).join('\n') }
  return { ok: true, bank: v.data, judged: items.judged + rest.judged, unjudged: [] }
}
