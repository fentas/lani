// The case trainer's bank ("🧩 Skloni · Cases", companion/README.md "Case trainer"; lani.cases/v0): the curated items of
// "Kateri sklon? · Which case?" and "Kje ali kam?" with their gold annotations (the word asked about, its lemma, case and
// number, the word that decides the case or the role when nothing does, the rule's grammar page), the rules' one lines,
// the frames "Sklanjaj! · Decline it" fills with the learner's own words, and the adjectives and possessives it declines
// with them. companion/cases/<language>/bank.json is the curated bank; the tutor adds items (publish_case_items,
// <data>/app/cases/items/<id>.json) checked the same way, and assigns a session focused on a case or a rule
// (assign_cases, <data>/app/cases/assigned.json). Slovene only, as the forms book (FORMS_LANGUAGES).
//
// The shared contract with the case lens (docs/plans/05-built/case-trainer.md, "The contract"): the case ids and their
// order (imenovalnik 1 … orodnik 6), the school's question words, and the rule ids, which are the grammar pages that
// explain each rule. The app's CaseLook holds the colours.
import { existsSync, mkdirSync, readdirSync, readFileSync, renameSync, statSync, unlinkSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import { adjectiveForms, formsOf, type FormsContext, type LemmaForms } from './forms'
import { normalizeForm } from './lexicon'

// --- the contract ------------------------------------------------------------------------------------------------

/** The six cases in the school's order (1. imenovalnik … 6. orodnik): their ids are their grammar pages. */
export const CASES = ['imenovalnik', 'rodilnik', 'dajalnik', 'tozilnik', 'mestnik', 'orodnik'] as const
export type CaseId = (typeof CASES)[number]
/** Each case's slot in the forms book (forms.ts NOUN_KEYS: "gen.sg"). */
export const CASE_SLOT: Record<CaseId, string> = { imenovalnik: 'nom', rodilnik: 'gen', dajalnik: 'dat', tozilnik: 'acc', mestnik: 'loc', orodnik: 'ins' }
/** The school's question words of each case (the app's CaseLook shows the same). */
export const CASE_QUESTIONS: Record<CaseId, string> = {
  imenovalnik: 'kdo? kaj?',
  rodilnik: 'koga? česa?',
  dajalnik: 'komu? čemu?',
  tozilnik: 'koga? kaj?',
  mestnik: 'pri kom? pri čem? (o kom? o čem?)',
  orodnik: 's kom? s čim?',
}
/** English names, for the tutor's notes and the mistakes' pattern ids. */
export const CASE_EN: Record<CaseId, string> = { imenovalnik: 'nominative', rodilnik: 'genitive', dajalnik: 'dative', tozilnik: 'accusative', mestnik: 'locative', orodnik: 'instrumental' }
export const NUMBERS = ['sg', 'du', 'pl'] as const
export const LEVELS = ['A1', 'A2', 'B1'] as const
const LEVEL_RANK: Record<string, number> = { A1: 1, A2: 2, B1: 3, B2: 4, C1: 5, C2: 6 }

/** The languages whose bank the bridge serves (the trainer is the cases', Slovene's). */
export const CASES_LANGUAGES: ReadonlySet<string> = new Set(['sl'])

// --- a sentence's words ----------------------------------------------------------------------------------------------

/** A sentence's tokens, as the app shows and taps them: split at white space (punctuation stays on its word). */
export const tokensOf = (sentence: string): string[] => sentence.trim().split(/\s+/).filter(Boolean)
/** A token without the punctuation around it: "šoli." → "šoli", "„Dober" → "Dober". */
export const bare = (token: string): string => token.replace(/^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu, '')

// --- the format -----------------------------------------------------------------------------------------------------

const ID = /^[a-z0-9][a-z0-9-]{0,63}$/
const id = z.string().regex(ID, 'kebab-case, a-z 0-9 -')
const line = z.string().trim().min(1).max(160)
const said = z.string().trim().min(2).max(120)
/** A text in the learner's bases: English, German, Italian (the bases a Slovene village's learners have). */
const bases = z.strictObject({ en: line, de: line, it: line })
const level = z.enum(LEVELS)
const word = z.string().trim().min(1).max(40).regex(/^[\p{L}][\p{L}' -]*$/u, 'a word')

/**
 * A rule's one line, by its key (the items' `why`): its case, its grammar page (the rule id), the words that trigger it
 * ([words], lower case: the trigger an item names must be one of them; none for an open class, a noun a genitive belongs
 * to), and the rule in a line in Slovene ([sl], short: "brez + rodilnik") and each base.
 */
const ruleSpec = z.strictObject({
  case: z.enum(CASES),
  page: id,
  words: z.array(word).min(1).max(200).optional(),
  sl: z.string().trim().min(1).max(80),
  en: line,
  de: line,
  it: line,
})

const common = {
  id,
  level,
  /** The sentence, in Slovene: the word asked about in its case. */
  sl: said,
  en: line,
  de: line,
  it: line,
  /** The token (tokensOf) asked about: the noun, an adjective or a possessive agreeing with it, a pronoun. */
  word: z.number().int().min(0).max(20),
  lemma: word,
  pos: z.enum(['noun', 'adj', 'pron', 'name']).default('noun'),
  /** An adjective's (or possessive's) gender, of the noun it agrees with; a noun's own is the dictionary's. */
  gender: z.enum(['m', 'f', 'n']).optional(),
  case: z.enum(CASES),
  number: z.enum(NUMBERS),
  /** The rule's key (a key of the bank's rules): its case must be the item's. */
  why: z.string().regex(/^[a-z0-9][a-z0-9-]{0,40}$/),
  /** The token of the word that decides the case: a preposition, a verb, ni or nimam, a number, a quantity word; null: nothing does ([role]). */
  trigger: z.number().int().min(0).max(20).nullable(),
  /** More tokens a tap on which is right too: the verb that says where to beside v or na ("Grem v šolo"). */
  also: z.array(z.number().int().min(0).max(20)).max(3).default([]),
  /** Nothing decides it: the subject (kdo? kaj?) or the object (koga? kaj?). */
  role: z.enum(['subject', 'object']).optional(),
  /** The other readings of the same form worth naming (slots: "nom.pl"), each checked against the dictionary. */
  ambiguous: z.array(z.string().regex(/^(nom|gen|dat|acc|loc|ins)\.(sg|du|pl)$/)).max(6).default([]),
  /** How the sentence decides an ambiguous form when no word does, or a spoken form's label. */
  note: bases.optional(),
  /** A spoken form in the sentence (vilica for vilice, mam for imam): right, never a mistake; labelled. */
  spoken: z.strictObject({ form: word, standard: word, note: bases }).optional(),
  /** Where the sentence is from: "scene:v-hisi", "story:…", "reading:…", "grammar:<page>", or written for the bank ("bank"). */
  from: z.string().trim().min(1).max(80).default('bank'),
}

/** "Kateri sklon? · Which case?": the sentence, the word highlighted; the case picked, then the word that decides it tapped. */
const whichItem = z.strictObject({ ...common, kind: z.literal('which') })

/**
 * "Kje ali kam?": the sentence with its [gap] (tokens from … to, inclusive) chosen among [options] ([answer] the right
 * one, the gap's words as they stand), the word in brackets after the gap ([cue]: the noun as the dictionary has it, when
 * the gap holds it: none where only the preposition is asked, "Grem ___ pošto.");
 * [question]: what is tested (kje or kam: v/na + the locative or the accusative, pri or k; v or na; od kod), [scene]: the
 * village scene where the place is drawn, shown above it. Its [trigger] is the verb that says where or where to (sem,
 * grem), outside the gap: "Zakaj?" asks for it after the pick; none (v or na) asks nothing more.
 */
const placeItem = z.strictObject({
  ...common,
  kind: z.literal('place'),
  question: z.enum(['kje-kam', 'v-na', 'pri-k', 'od-kod']),
  gap: z.tuple([z.number().int().min(0).max(20), z.number().int().min(0).max(20)]),
  options: z.array(z.string().trim().min(1).max(40)).min(2).max(3),
  answer: z.number().int().min(0).max(2),
  cue: z.string().trim().min(1).max(40).optional(),
  scene: id.optional(),
})

export const caseItem = z.discriminatedUnion('kind', [whichItem, placeItem])
export type CaseItem = z.infer<typeof caseItem>
export type PlaceItem = z.infer<typeof placeItem>

/**
 * A frame of "Sklanjaj! · Decline it": a sentence with a known trigger and the gap ({x}) the learner's word goes into,
 * declined ("Danes ni {x}." → mize); {k} and {s} before it are k/h and s/z as the word after them wants (h kmetu, s
 * sestro), {two} is dva or dve as the noun's gender wants (dva brata, dve hiši). Its translations have "…" where the word goes. [adj]: an adjective or a possessive may come with the noun
 * ("Vidim {x}." → lepo hišo); [needs]: pages it needs introduced besides its rule's and its case's; [gender]: only
 * nouns of these genders.
 */
const frameSpec = z.strictObject({
  id,
  case: z.enum(CASES),
  number: z.enum(NUMBERS),
  why: z.string().regex(/^[a-z0-9][a-z0-9-]{0,40}$/),
  level,
  sl: z.string().trim().min(3).max(100),
  en: line,
  de: line,
  it: line,
  adj: z.boolean().default(false),
  needs: z.array(id).max(4).default([]),
  gender: z.array(z.enum(['m', 'f', 'n'])).min(1).max(3).optional(),
})
export type CaseFrame = z.infer<typeof frameSpec>

/** An adjective's key in the bank's table: "<case slot>.<gender>.<number>" (no dual), and "acc.m.sg.anim". */
export const ADJ_KEYS = [
  ...['nom', 'gen', 'dat', 'acc', 'loc', 'ins'].flatMap(c => ['m', 'f', 'n'].flatMap(g => ['sg', 'pl'].map(n => `${c}.${g}.${n}`))),
  'acc.m.sg.anim',
]

/** An adjective or a possessive the frames decline with the learner's nouns: every form of [ADJ_KEYS], what it means. */
const adjectiveSpec = z.strictObject({
  lemma: word,
  kind: z.enum(['adj', 'possessive']),
  level,
  en: line,
  de: line,
  it: line,
  forms: z.record(z.string(), word),
})
export type CaseAdjective = z.infer<typeof adjectiveSpec>

/** The noun slots a fallback noun gives (forms.ts NOUN_KEYS). */
export const NOUN_SLOTS = ['sg', 'pl', 'du'].flatMap(n => ['nom', 'gen', 'dat', 'acc', 'loc', 'ins'].map(c => `${c}.${n}`))

/** A noun the frames use when the learner has too few of their own with forms: its gender, meaning and forms by slot. */
const nounSpec = z.strictObject({
  lemma: word,
  gender: z.enum(['m', 'f', 'n']),
  level,
  en: line,
  de: line,
  it: line,
  forms: z.record(z.string(), z.union([word, z.array(word).min(1).max(3)])),
})
export type CaseNoun = z.infer<typeof nounSpec>

export const bankSpec = z.strictObject({
  schema: z.literal('lani.cases/v0'),
  language: z.string().regex(/^[a-z]{2}$/),
  machine_written: z.boolean(),
  reviewed: z.boolean(),
  review: z.string().trim().min(1).max(2000),
  rules: z.record(z.string().regex(/^[a-z0-9][a-z0-9-]{0,40}$/), ruleSpec),
  items: z.array(caseItem).max(2000),
  frames: z.array(frameSpec).max(200),
  adjectives: z.array(adjectiveSpec).max(80),
  nouns: z.array(nounSpec).max(200),
})
export type CaseBank = z.infer<typeof bankSpec>
export type CaseRule = CaseBank['rules'][string]

// --- checks against the forms book, the grammar book and the scenes -----------------------------------------------

/** What the checks read: the dictionary and the forms book (forms.ts), the grammar book's pages and levels, the scenes. */
export type CaseCheck = {
  forms: FormsContext
  /** The grammar book's pages: id → level. */
  pages: Map<string, string>
  /** The scenes' ids (a place item's picture). */
  scenes: Set<string>
}

/** What the dictionary says of [lemma] as a noun (its entry) and as an adjective (its nominative and its cases). */
function lexOf(c: CaseCheck, lemma: string) {
  const entries = formsOf(c.forms, lemma)
  const noun = entries.find(e => e.pos === 'noun' && normalizeForm(e.lemma, c.forms.language) === normalizeForm(lemma, c.forms.language))
  const adj = entries.find(e => e.pos === 'adj' && normalizeForm(e.lemma, c.forms.language) === normalizeForm(lemma, c.forms.language))
  return { noun, adj, adjCases: adj ? adjectiveForms(c.forms, adj.lemma) : new Map<string, string[]>() }
}

const slotForms = (e: LemmaForms | undefined, key: string): string[] | undefined => {
  const s = e?.slots.find(x => x.key === key)
  return s ? [...s.forms, ...(s.also ?? [])] : undefined
}

/**
 * The forms an adjective of the dictionary has in [slot] ("gen.f.sg", "acc.m.sg.anim"): its nominative's slots, its
 * cases' (forms.ts adjectiveForms); the masculine accusative singular, which those leave out, is the nominative (a thing)
 * or the genitive (a person). Undefined where the dictionary has nothing for it.
 */
function adjForms(lex: ReturnType<typeof lexOf>, key: string): string[] | undefined {
  if (!lex.adj) return undefined
  const [c, g, n, anim] = key.split('.')
  if (c === 'nom') {
    if (g === 'n' && n === 'pl') return lex.adjCases.get('adj.n.pl')
    return slotForms(lex.adj, `adj.${g}.${n}`)
  }
  if (c === 'acc' && g === 'm' && n === 'sg') return anim ? lex.adjCases.get('adj.gen.m.sg') : slotForms(lex.adj, 'adj.m.sg')
  return lex.adjCases.get(`adj.${c}.${g}.${n}`)
}

/** Every slot of [e] whose forms include [form]. */
function slotsWith(e: LemmaForms | undefined, form: string, language: string): string[] {
  const f = normalizeForm(form, language)
  return (e?.slots ?? []).filter(s => [...s.forms, ...(s.also ?? [])].some(x => normalizeForm(x, language) === f)).map(s => s.key)
}

/** The problems of [items] (each "<id>: …"), and how many words the dictionary judged. */
export function checkItems(items: CaseItem[], rules: Record<string, CaseRule>, c: CaseCheck): { problems: string[]; judged: number; unjudged: string[] } {
  const lang = c.forms.language
  const norm = (s: string) => normalizeForm(s, lang)
  const problems: string[] = []
  const unjudged: string[] = []
  let judged = 0
  const seen = new Set<string>()
  for (const it of items) {
    const p = (m: string) => problems.push(`${it.id}: ${m}`)
    if (seen.has(it.id)) p('the id is taken')
    seen.add(it.id)
    const toks = tokensOf(it.sl)
    const at = (i: number) => bare(toks[i] ?? '')
    if (!/[.!?…]["»“]?$/.test(it.sl)) p('the sentence ends with . ! ? or …')
    if (toks.length > 14) p(`${toks.length} words: at most 14`)
    if (it.word >= toks.length || !at(it.word)) p(`word ${it.word} is past the sentence's ${toks.length} words`)
    const rule = rules[it.why]
    if (!rule) p(`why "${it.why}" is no rule of the bank`)
    else {
      if (rule.case !== it.case) p(`why "${it.why}" is the rule of ${rule.case}, the item is ${it.case}`)
      if (!c.pages.has(rule.page)) p(`the rule's page "${rule.page}" isn't in the grammar book`)
      else if ((LEVEL_RANK[it.level] ?? 0) < (LEVEL_RANK[c.pages.get(rule.page)!] ?? 0)) p(`level ${it.level} is below its page ${rule.page}'s ${c.pages.get(rule.page)}`)
    }
    if (!c.pages.has(it.case)) p(`the case's page "${it.case}" isn't in the grammar book`)
    // the deciding word, or the role when nothing decides
    if (it.trigger === null) {
      if (it.kind === 'which' && !it.role) p('no trigger and no role: say subject or object')
    } else {
      if (it.trigger >= toks.length) p(`trigger ${it.trigger} is past the sentence`)
      else if (it.trigger === it.word) p('the trigger is the word itself')
      // a place item's trigger is the verb that says kje or kam (sem, grem): not the rule's preposition, which is the gap
      else if (it.kind === 'which' && rule?.words && !rule.words.map(norm).includes(norm(at(it.trigger)))) p(`trigger "${at(it.trigger)}" isn't a word of the rule "${it.why}" (${rule.words.slice(0, 8).join(', ')}…)`)
      if (it.role) p('a trigger and a role: one or the other')
    }
    for (const a of it.also) if (a >= toks.length || a === it.word || a === it.trigger) p(`also ${a}: past the sentence, the word or the trigger`)
    if (it.pos === 'adj' && !it.gender) p("an adjective's item says the noun's gender")
    // the form: that case and number of its lemma, where the dictionary has the word
    const form = at(it.word)
    const lex = it.pos === 'noun' || it.pos === 'adj' ? lexOf(c, it.lemma) : undefined
    const slot = `${CASE_SLOT[it.case]}.${it.number}`
    if (it.pos === 'noun' && lex?.noun) {
      judged++
      const fs = slotForms(lex.noun, slot)
      if (!fs) p(`the dictionary has no ${slot} of ${it.lemma}`)
      else if (!fs.map(norm).includes(norm(form))) p(`"${form}" isn't the ${slot} of ${it.lemma} (${fs.join('/')})`)
      const others = slotsWith(lex.noun, form, lang).filter(k => k.split('.')[0] !== CASE_SLOT[it.case])
      for (const a of it.ambiguous) if (!slotsWith(lex.noun, form, lang).includes(a)) p(`ambiguous ${a}: "${form}" isn't the ${a} of ${it.lemma}`)
      // an ambiguous form only where the sentence decides it, and says how: by its trigger, else in its note
      if (others.length && it.trigger === null && !it.note) p(`"${form}" is also ${others.join(', ')}: no word decides it, so the note says how the sentence does`)
    } else if (it.pos === 'adj' && lex?.adj) {
      judged++
      const key = `${slot.split('.')[0]}.${it.gender}.${it.number === 'du' ? 'pl' : it.number}`
      const fs = it.number === 'du' ? undefined : [...(adjForms(lex, key) ?? []), ...(key === 'acc.m.sg' ? (adjForms(lex, 'acc.m.sg.anim') ?? []) : [])]
      if (fs && fs.length && !fs.map(norm).includes(norm(form))) p(`"${form}" isn't the ${key} of the adjective ${it.lemma} (${fs.join('/')})`)
    } else if (it.pos === 'noun' || it.pos === 'adj') unjudged.push(it.lemma)
    if (it.spoken && !toks.map(t => norm(bare(t))).includes(norm(it.spoken.form))) p(`the spoken form "${it.spoken.form}" isn't in the sentence`)
    if (it.kind === 'place') {
      const [from, to] = it.gap
      if (from > to || to >= toks.length) p(`gap ${from}–${to} isn't in the sentence`)
      else {
        if (it.word < from || it.word > to) {
          if (it.question !== 'v-na' && it.question !== 'pri-k') p('the word asked about is in the gap')
        }
        if (it.word >= from && it.word <= to && !it.cue) p('the noun is in the gap: its cue (the word in brackets) says which')
        const gapText = toks.slice(from, to + 1).map(bare).join(' ')
        if (norm(it.options[it.answer] ?? '') !== norm(gapText)) p(`options[${it.answer}] "${it.options[it.answer]}" isn't the gap's "${gapText}"`)
        if (it.trigger !== null && it.trigger >= from && it.trigger <= to) p('the verb that says kje or kam (the trigger) stands outside the gap')
      }
      if (it.answer >= it.options.length) p(`answer ${it.answer}: only ${it.options.length} options`)
      if (new Set(it.options.map(norm)).size !== it.options.length) p('two options are the same')
      if (it.scene && !c.scenes.has(it.scene)) p(`scene "${it.scene}" isn't one of the village's`)
      // a wrong option of kje or kam is a form of the same noun (another case), where the dictionary has it
      if ((it.question === 'kje-kam' || it.question === 'od-kod') && lex?.noun) {
        for (const [i, o] of it.options.entries()) {
          if (i === it.answer) continue
          const w = bare(o.split(/\s+/).at(-1) ?? '')
          if (!slotsWith(lex.noun, w, lang).length) p(`option "${o}": "${w}" isn't a form of ${it.lemma}`)
        }
      }
    }
  }
  return { problems, judged, unjudged: [...new Set(unjudged)] }
}

/** The problems of the bank's rules, frames, adjectives and fallback nouns, against the grammar book and the dictionary. */
export function checkRest(bank: Pick<CaseBank, 'rules' | 'frames' | 'adjectives' | 'nouns'>, c: CaseCheck): { problems: string[]; judged: number } {
  const lang = c.forms.language
  const norm = (s: string) => normalizeForm(s, lang)
  const problems: string[] = []
  let judged = 0
  for (const [k, r] of Object.entries(bank.rules)) {
    if (!c.pages.has(r.page)) problems.push(`rules.${k}: its page "${r.page}" isn't in the grammar book`)
  }
  const frameIds = new Set<string>()
  for (const f of bank.frames) {
    const p = (m: string) => problems.push(`frames.${f.id}: ${m}`)
    if (frameIds.has(f.id)) p('the id is taken')
    frameIds.add(f.id)
    const rule = bank.rules[f.why]
    if (!rule) p(`why "${f.why}" is no rule`)
    else if (rule.case !== f.case) p(`why "${f.why}" is ${rule.case}'s, the frame is ${f.case}`)
    if ((f.sl.match(/\{x\}/g) ?? []).length !== 1) p('{x} once: where the word goes')
    if (/\{[a-z]+\}/.test(f.sl.replace(/\{(x|k|s|two)\}/g, ''))) p('only {x}, {k}, {s} and {two}')
    if (f.sl.includes('{two}') && f.number !== 'du') p('{two} (dva or dve) is the dual\'s')
    if (/\{k\}|\{s\}/.test(f.sl) && !/\{[ks]\} \{x\}/.test(f.sl)) p('{k} and {s} stand right before {x}')
    for (const l of ['en', 'de', 'it'] as const) if ((f[l].match(/…/g) ?? []).length !== 1) p(`${l}: "…" once, where the word goes`)
    for (const n of f.needs) if (!c.pages.has(n)) p(`needs "${n}": not in the grammar book`)
  }
  for (const a of bank.adjectives) {
    const p = (m: string) => problems.push(`adjectives.${a.lemma}: ${m}`)
    const missing = ADJ_KEYS.filter(k => !a.forms[k])
    if (missing.length) p(`forms missing: ${missing.join(', ')}`)
    const extra = Object.keys(a.forms).filter(k => !ADJ_KEYS.includes(k))
    if (extra.length) p(`not a form of the table: ${extra.join(', ')}`)
    const lex = lexOf(c, a.lemma)
    if (!lex.adj) continue
    judged++
    for (const k of ADJ_KEYS) {
      const fs = adjForms(lex, k)
      if (fs?.length && a.forms[k] && !fs.map(norm).includes(norm(a.forms[k]))) p(`${k} "${a.forms[k]}" isn't the dictionary's (${fs.join('/')})`)
    }
  }
  for (const n of bank.nouns) {
    const p = (m: string) => problems.push(`nouns.${n.lemma}: ${m}`)
    const sg = ['nom', 'gen', 'dat', 'acc', 'loc', 'ins'].map(c => `${c}.sg`)
    const missing = sg.filter(k => !n.forms[k])
    if (missing.length) p(`the singular's forms missing: ${missing.join(', ')}`)
    const extra = Object.keys(n.forms).filter(k => !NOUN_SLOTS.includes(k))
    if (extra.length) p(`not a slot: ${extra.join(', ')}`)
    const lex = lexOf(c, n.lemma)
    if (!lex.noun) continue
    judged++
    if (lex.noun.gender && lex.noun.gender !== n.gender) p(`gender ${n.gender}, the dictionary says ${lex.noun.gender}`)
    for (const [k, v] of Object.entries(n.forms)) {
      const fs = slotForms(lex.noun, k)
      for (const f of typeof v === 'string' ? [v] : v) if (fs && !fs.map(norm).includes(norm(f))) p(`${k} "${f}" isn't the dictionary's (${fs.join('/')})`)
    }
  }
  return { problems, judged }
}

/** The bank validated: its format, then the checks; [ok] when both are clean. */
export function validateBank(input: unknown, c?: CaseCheck): { ok: true; bank: CaseBank; judged: number; unjudged: string[] } | { ok: false; errors: string } {
  const v = bankSpec.safeParse(input)
  if (!v.success) return { ok: false, errors: z.prettifyError(v.error) }
  if (!c) return { ok: true, bank: v.data, judged: 0, unjudged: [] }
  const items = checkItems(v.data.items, v.data.rules, c)
  const rest = checkRest(v.data, c)
  const problems = [...items.problems, ...rest.problems]
  if (problems.length) return { ok: false, errors: problems.map(p => `✖ ${p}`).join('\n') }
  return { ok: true, bank: v.data, judged: items.judged + rest.judged, unjudged: items.unjudged }
}

/** How the bank is spread: items by kind, case, number and level. */
export function bankCounts(items: CaseItem[]) {
  const count = <K extends string>(f: (i: CaseItem) => K) => items.reduce<Record<string, number>>((m, i) => ((m[f(i)] = (m[f(i)] ?? 0) + 1), m), {})
  return {
    items: items.length,
    kind: count(i => i.kind),
    case: count(i => i.case),
    number: count(i => i.number),
    level: count(i => i.level),
    by: count(i => `${i.case}.${i.number}`),
  }
}

// --- the learner's way through it (the village state's cases) ---------------------------------------------------------

/** The village state's cases (the app's CaseProgress) as the tutor reads it: each case's and rule's last answers, stars, what comes back. */
export function casesOf(c: any) {
  if (!c || typeof c !== 'object') return undefined
  const window = (s: unknown) => (typeof s === 'string' ? s.replace(/[^01]/g, '').slice(-12) : '')
  const score = (s: string) => ({ right: [...s].filter(x => x === '1').length, of: s.length })
  const keys = c.keys && typeof c.keys === 'object' ? (c.keys as Record<string, unknown>) : {}
  const by = (prefix: string) =>
    Object.fromEntries(Object.entries(keys).filter(([k]) => k.startsWith(prefix)).map(([k, v]) => [k.slice(prefix.length), score(window(v))]).filter(([, s]) => (s as { of: number }).of > 0))
  const again = Array.isArray(c.again) ? c.again.filter((a: any) => typeof a?.item === 'string' && typeof a?.due === 'string') : []
  const seen = c.seen && typeof c.seen === 'object' ? Object.fromEntries(Object.entries(c.seen as Record<string, any>).filter(([, s]) => Number.isInteger(s?.n)).map(([k, s]) => [k, { n: s.n, last: s.day }])) : {}
  return {
    cases: by('case:'),
    rules: by('rule:'),
    coming_back: again.length ? again.slice(0, 12).map((a: any) => ({ item: a.item, due: a.due })) : undefined,
    seen_in_lens: Object.keys(seen).length ? seen : undefined,
    sessions: Number.isInteger(c.runs) ? c.runs : 0,
    last_session: typeof c.day === 'string' && c.day ? c.day : undefined,
  }
}

// --- the store -------------------------------------------------------------------------------------------------------

/** A served item: the bank's, or the tutor's (when it was published). */
export type LoadedItem = CaseItem & { source: 'curated' | 'tutor'; published_at?: number }

/** The tutor's assignment: a session focused on a case or a rule ([focus]: a case id or a rule's page), with a note. */
export const assignSpec = z.strictObject({
  focus: id,
  note: z.string().trim().max(200).optional(),
  at: z.string(),
  done: z.string().optional(),
})
export type Assigned = z.infer<typeof assignSpec>

/**
 * The bank of a language (companion/cases/<language>/bank.json, read again when it changes) and the tutor's items and
 * assignment (<data>/app/cases/). A bank that doesn't validate is logged and serves nothing.
 */
export class CaseStore {
  private mtime = -1
  private bank: CaseBank | null = null
  private warned = new Set<string>()

  constructor(
    private readonly curatedDir: string,
    readonly language: string,
    private readonly tutorDir: string,
    private readonly log: (...a: unknown[]) => void = () => {},
  ) {}

  private get bankPath() {
    return join(this.curatedDir, this.language, 'bank.json')
  }

  private get itemsDir() {
    return join(this.tutorDir, 'items')
  }

  /** The curated bank as it is on disk (its format checked; the dictionary's checks are the smoke's and the tool's). */
  curated(): CaseBank | null {
    if (!CASES_LANGUAGES.has(this.language)) return null
    const path = this.bankPath
    const mtime = existsSync(path) ? statSync(path).mtimeMs : 0
    if (mtime === this.mtime) return this.bank
    this.mtime = mtime
    this.bank = null
    if (!mtime) return null
    try {
      const v = validateBank(JSON.parse(readFileSync(path, 'utf8')))
      if (!v.ok) throw new Error(v.errors)
      if (v.bank.language !== this.language) throw new Error(`a bank of "${v.bank.language}", the village speaks "${this.language}"`)
      this.bank = v.bank
    } catch (e) {
      this.log(`case bank ${path} left out: ${(e as Error).message}`)
    }
    return this.bank
  }

  /** The tutor's items, each file one; a file that doesn't validate is logged once and left out. */
  tutor(): LoadedItem[] {
    const dir = this.itemsDir
    if (!existsSync(dir)) return []
    return readdirSync(dir).filter(f => f.endsWith('.json')).sort().flatMap(f => {
      const path = join(dir, f)
      try {
        const v = caseItem.safeParse(JSON.parse(readFileSync(path, 'utf8')))
        if (!v.success) throw new Error(z.prettifyError(v.error))
        return [{ ...v.data, source: 'tutor' as const, published_at: statSync(path).mtimeMs }]
      } catch (e) {
        const key = `${path}:${statSync(path).mtimeMs}`
        if (!this.warned.has(key)) this.log(`skipping case item ${path}: ${(e as Error).message}`)
        this.warned.add(key)
        return []
      }
    })
  }

  /** Every item: the bank's, then the tutor's (a tutor's under a curated id left out). */
  items(): LoadedItem[] {
    const curated = (this.curated()?.items ?? []).map(i => ({ ...i, source: 'curated' as const }))
    const ids = new Set(curated.map(i => i.id))
    return [...curated, ...this.tutor().filter(i => !ids.has(i.id))]
  }

  /** Writes the tutor's [items] (replacing their earlier ones of the same ids); an error for a curated id. */
  publish(items: CaseItem[]): { added: string[]; replaced: string[] } | { error: string } {
    const curated = new Set((this.curated()?.items ?? []).map(i => i.id))
    const taken = items.filter(i => curated.has(i.id)).map(i => i.id)
    if (taken.length) return { error: `curated items' ids, give yours others: ${taken.join(', ')}` }
    mkdirSync(this.itemsDir, { recursive: true })
    const added: string[] = []
    const replaced: string[] = []
    for (const it of items) {
      const path = join(this.itemsDir, `${it.id}.json`)
      ;(existsSync(path) ? replaced : added).push(it.id)
      writeFileSync(`${path}.tmp`, JSON.stringify({ ...it, from: it.from === 'bank' ? 'tutor' : it.from }, null, 2) + '\n')
      renameSync(`${path}.tmp`, path)
    }
    return { added, replaced }
  }

  /** Takes the tutor's item [id] back; false when the tutor has none of that id. */
  remove(itemId: string): boolean {
    if (!ID.test(itemId)) return false
    const path = join(this.itemsDir, `${itemId}.json`)
    if (!existsSync(path)) return false
    unlinkSync(path)
    return true
  }

  private get assignedPath() {
    return join(this.tutorDir, 'assigned.json')
  }

  /** The tutor's assignment, if there is one not done yet. */
  assigned(): Assigned | null {
    try {
      const v = assignSpec.safeParse(JSON.parse(readFileSync(this.assignedPath, 'utf8')))
      return v.success && !v.data.done ? v.data : null
    } catch {
      return null
    }
  }

  /** The tutor assigns a session focused on [focus]; null takes the assignment back. */
  assign(a: Omit<Assigned, 'done'> | null) {
    mkdirSync(this.tutorDir, { recursive: true })
    if (!a) {
      if (existsSync(this.assignedPath)) unlinkSync(this.assignedPath)
      return
    }
    writeFileSync(`${this.assignedPath}.tmp`, JSON.stringify(a, null, 2) + '\n')
    renameSync(`${this.assignedPath}.tmp`, this.assignedPath)
  }

  /** A session focused on the assignment's focus was played on [day]: it is done. */
  done(focus: string | undefined, day: string): Assigned | null {
    const a = this.assigned()
    if (!a || !focus || a.focus !== focus) return null
    const d = { ...a, done: day }
    writeFileSync(`${this.assignedPath}.tmp`, JSON.stringify(d, null, 2) + '\n')
    renameSync(`${this.assignedPath}.tmp`, this.assignedPath)
    return d
  }

  /** What GET /cases serves: the bank's rules, frames, adjectives and nouns, every item, the assignment. */
  served() {
    const b = this.curated()
    if (!b) return null
    const assigned = this.assigned()
    return {
      schema: b.schema,
      language: b.language,
      machine_written: b.machine_written,
      reviewed: b.reviewed,
      review: b.review,
      rules: b.rules,
      frames: b.frames,
      adjectives: b.adjectives,
      nouns: b.nouns,
      items: this.items(),
      ...(assigned ? { assigned } : {}),
    }
  }
}
