// German's case lens (docs/plans/05-built/case-de.md): a sentence and a word in it → the word's case and number, what
// decided it and the rule's grammar page, as Slovene's lens says it (case-lens.ts, the same wire: WordCase, LineCases),
// from German's trigger table (case-triggers-de.ts) and its forms by case (german-forms.ts), the dictionary's nouns and
// the packs'. German shows the case in its noun phrase: the article, the adjectives' endings, the noun's -n and -s. So
// the lens reads each phrase (a preposition or a contraction, a determiner, adjectives, the noun; a pronoun; a name),
// keeps the readings its forms allow, and lets the sentence decide among them: the preposition (the two-way ones by
// their form, else the verb: Wohin? or Wo?; a verb's fixed one: warten auf), the verb that takes a dative or a genitive,
// a copula, the noun a genitive belongs to, a time, a fixed phrase, the subject and the object by elimination. It says
// "decided" only where one reading is left by the forms and a hard rule; "likely" for the order's or a verb's
// preference; "ambiguous" with every reading else. A form people say is read as what it is and never called wrong: wegen
// dem Regen is a dative after wegen (the written form beside it: des Regens), dem Bub a dative. Pure: runs in the
// phone's bridge (QuickJS) too.
import type { CaseKind, CaseOption, CaseSure, FormReading, Governs, LineCases, RuledOut, WordCase, WordRef } from './case-lens'
import type { CaseKey, GenderKey, NumberKey } from './case-triggers'
import type { CaseTriggersDe, DeCaseKey, DeChunk, DePreposition, DeVerb } from './case-triggers-de'
import { DE_CASE_KEYS, DE_CASE_PAGES } from './case-triggers-de'
import { dictPlural, GermanForms, type DeNumber } from './german-forms'
import type { Lexicon } from './lexicon'

/** A name of the village (a villager's, the learner's) and its gender, for the article Austria puts before it (der Stefan). */
export type DeName = { name: string; gender?: 'm' | 'f' }

/** What the lens reads: the table, the dictionary, the packs' nouns (gender, nominative plural). */
export type CaseLensDeSources = { table: CaseTriggersDe; dict?: Lexicon; nouns?: Map<string, { gender: GenderKey; plural?: string }> }

type Slot = { case: DeCaseKey; number: DeNumber; gender?: GenderKey; lemma?: string }
const key = (s: Slot) => `${s.case}.${s.number}.${s.gender ?? ''}`
const lower = (s: string) => s.toLocaleLowerCase('de')

/** A word of the sentence: its place, as written, lowercased, and the clause it is in. */
type Tok = { i: number; raw: string; low: string; start: number; end: number; clause: number; cap: boolean; first: boolean; question?: boolean }

/** A noun phrase: its preposition (or contraction), determiner, adjectives, head, the readings its forms leave. */
type Phrase = {
  prep?: Tok
  prepEntry?: DePreposition
  fused?: CaseTriggersDe['contractions'][number]
  det?: Tok
  detLemma?: string
  adjs: Tok[]
  head: Tok
  pos: 'noun' | 'pron' | 'name' | 'rel'
  lemma: string
  slots: Slot[]
  /** Every reading of the forms, before the sentence decided (the card's "can also be"). */
  all: Slot[]
  person?: 1 | 2 | 3
  /** What decided, once decided. */
  pick?: Slot
  options?: Slot[]
  sure?: CaseSure
  kind?: CaseKind
  trigger?: Tok
  sense?: string
  rule?: string
  written?: string
  out?: RuledOut[]
}

type VerbHit = { tok: Tok; lemma: string; finite: boolean; person?: 1 | 2 | 3; number?: DeNumber }

const PERSONAL = /^(ich|du|er|sie|es|wir|ihr|mich|dich|ihn|uns|euch|mir|dir|ihm|ihnen|sich|man)$/
/** Adverbs that are a noun's word too, first in a sentence: morgen (tomorrow), not der Morgen. */
const ADVERBS = /^(morgen)$/
/** The numbers in words before a noun (zwei Seen, hundert Schafe): no ending, no case of their own. */
const NUMBERS = /^(zwei|drei|vier|fünf|sechs|sieben|acht|neun|zehn|elf|zwölf|zwanzig|dreißig|vierzig|fünfzig|hundert|tausend|(drei|vier|fünf|sech|sieb|acht|neun)zehn|(ein|zwei|drei|vier|fünf|sechs|sieben|acht|neun)und(zwanzig|dreißig|vierzig|fünfzig|sechzig|siebzig|achtzig|neunzig))$/

export class CaseLensDe {
  private readonly f: GermanForms
  private readonly preps = new Map<string, DePreposition>()
  private readonly fusedForms = new Map<string, CaseTriggersDe['contractions'][number]>()
  private readonly governing = new Map<string, DeVerb[]>()
  private readonly particleVerbs: DeVerb[] = []
  private readonly joined = new Map<string, DeVerb[]>()
  private readonly motion = new Set<string>()
  private readonly rest = new Set<string>()
  private readonly copulas = new Set<string>()
  private readonly chunks = new Map<string, DeChunk[]>()
  private readonly predicates = new Map<string, { case: DeCaseKey; rule: string }>()
  private readonly pronouns = new Map<string, { lemma: string; slots: Slot[]; person: 1 | 2 | 3 | null }[]>()
  private readonly relativeOnly = new Map<string, Slot[]>()
  private readonly timeNouns: Set<string>
  private readonly timeGenitive: Set<string>
  private readonly phrases: CaseTriggersDe['phrases']

  constructor(readonly s: CaseLensDeSources) {
    const t = s.table
    this.f = new GermanForms(t)
    for (const p of t.prepositions) for (const w of p.words) this.preps.set(w, p)
    for (const c of t.contractions) this.fusedForms.set(c.form, c)
    for (const v of t.verbs) {
      if (v.particle) {
        this.particleVerbs.push(v)
        for (const j of v.joined ?? []) this.joined.set(j, [...(this.joined.get(j) ?? []), v])
      } else for (const form of v.forms) this.governing.set(form, [...(this.governing.get(form) ?? []), v])
    }
    for (const v of t.motion) for (const x of [...v.forms, ...(v.joined ?? [])]) this.motion.add(x)
    for (const v of t.rest) for (const x of [...v.forms, ...(v.joined ?? [])]) this.rest.add(x)
    for (const v of t.copulas) for (const x of v.forms) this.copulas.add(x)
    for (const c of t.chunks) for (const x of [...c.forms, ...(c.joined ?? [])]) this.chunks.set(x, [...(this.chunks.get(x) ?? []), c])
    for (const p of t.predicates) for (const w of p.words) this.predicates.set(w, { case: p.case, rule: p.rule })
    for (const p of t.pronouns) {
      for (const [c, forms] of Object.entries(p.forms)) {
        for (const form of forms ?? []) {
          // a gender only where the pronoun has one (er, sie, es): ich, wir, sich are anyone
          const slots = p.genders.length === 3 ? [{ case: c as DeCaseKey, number: p.number }] : p.genders.map(g => ({ case: c as DeCaseKey, number: p.number, gender: g }))
          const list = this.pronouns.get(form) ?? []
          const had = list.find(x => x.lemma === p.lemma && x.person === p.person)
          if (had) had.slots.push(...slots)
          else list.push({ lemma: p.lemma, slots, person: p.person })
          this.pronouns.set(form, list)
        }
      }
    }
    for (const [slot, form] of Object.entries(t.relative.forms)) {
      const [c, g] = slot.split('.') as [DeCaseKey, string]
      this.relativeOnly.set(form, [...(this.relativeOnly.get(form) ?? []), { case: c, number: g === 'pl' ? 'pl' : 'sg', ...(g === 'pl' ? {} : { gender: g as GenderKey }) }])
    }
    this.timeNouns = new Set(t.time.nouns)
    this.timeGenitive = new Set(t.time_genitive.phrases)
    this.phrases = t.phrases
  }

  // --- the words -----------------------------------------------------------------------------------------------------

  private tokens(line: string): Tok[] {
    const out: Tok[] = []
    let clause = 0
    let last = 0
    let first = true
    for (const m of line.matchAll(/[\p{L}\p{M}]+|\d+/gu)) {
      // a figure's full stop is an ordinal's (am 1. Mai), no sentence's end
      const between = line.slice(last, m.index).replace(/^\.(?=\s)/, out.length && /^\d+$/.test(out.at(-1)!.raw) ? '' : '.')
      if (out.length && /[,;:–—()]|[.!?…]\s/.test(between)) clause++
      // a sentence starts after . ! ? and after a colon (Er hat gesagt: Schieb …), capitalized as any
      if (/[.!?…:]/.test(between)) first = true
      const raw = m[0]
      out.push({ i: out.length, raw, low: lower(raw), start: m.index!, end: m.index! + raw.length, clause, cap: /^\p{Lu}/u.test(raw), first })
      first = false
      last = m.index! + raw.length
    }
    // a word of a question (its sentence ends with ?): a verb first there asks, it doesn't tell someone to
    for (const t of out) {
      const end = line.slice(t.end).search(/[.!?…]/)
      t.question = end >= 0 && line[t.end + end] === '?'
    }
    return out
  }

  /** The packs' nouns by their other forms (the plural, its dative, the genitive): made once. */
  private formIndex: Map<string, { lemma: string; gender: GenderKey; plural?: string }[]> | null = null
  private nounForms() {
    if (this.formIndex) return this.formIndex
    const m = new Map<string, { lemma: string; gender: GenderKey; plural?: string }[]>()
    const put = (form: string, x: { lemma: string; gender: GenderKey; plural?: string }) => m.set(form, [...(m.get(form) ?? []), x])
    for (const [lemma, x] of this.s.nouns ?? []) {
      const n = { lemma, gender: x.gender, ...(x.plural ? { plural: x.plural } : {}) }
      if (x.plural) {
        put(x.plural, n)
        if (this.f.dativePlural(x.plural) !== x.plural) put(this.f.dativePlural(x.plural), n)
      }
      const g = this.f.genitive(lemma)
      for (const f of [...g.forms, ...g.also]) put(f, n)
    }
    this.formIndex = m
    return m
  }

  /** A noun's readings by its form: its lemma, gender, the slots the form can be in; null for a word that isn't one. */
  private nounOf(tok: Tok): { lemma: string; gender?: GenderKey; slots: Slot[] } | null {
    if (!tok.cap) return null
    // the sentence's first word, capitalized as any: a noun only when it is no other word (Morgen kommt er: morgen)
    if (tok.first && (ADVERBS.test(tok.low) || (this.s.dict?.grammarReadings(tok.low) ?? []).some(r => r.pos !== 'noun' && r.pos !== 'name'))) return null
    const w = tok.raw
    const fromNouns = this.s.nouns
    const candidates: { lemma: string; gender: GenderKey; plural?: string }[] = []
    const add = (lemma: string, gender: GenderKey | undefined | null, plural?: string) => {
      if (gender && !candidates.some(c => c.lemma === lemma && c.gender === gender)) candidates.push({ lemma, gender, ...(plural ? { plural } : {}) })
    }
    const own = fromNouns?.get(w)
    if (own) add(w, own.gender, own.plural)
    const n = this.f.nNoun(w) ?? this.f.table.n_declension.find(x => x.oblique === w || x.genitive === w || x.plural === w)
    if (n) add(n.lemma, n.gender ?? 'm', n.plural ?? n.oblique)
    for (const x of this.nounForms().get(w) ?? []) add(x.lemma, x.gender, x.plural)
    for (const r of this.s.dict?.grammarReadings(tok.low) ?? []) {
      if (r.pos !== 'noun' || !r.gender) continue
      const lemma = r.lemma.charAt(0).toLocaleUpperCase('de') + r.lemma.slice(1)
      add(lemma, r.gender, fromNouns?.get(lemma)?.plural ?? dictPlural(this.s.dict, lemma) ?? (/plural/.test(r.grammar ?? '') && !/dative/.test(r.grammar ?? '') ? w : undefined))
    }
    if (!candidates.length) {
      if (tok.first) return null
      // a noun the lens doesn't know (capitalized inside a sentence): its gender and number open
      return { lemma: w, slots: DE_CASE_KEYS.flatMap(c => [...(['m', 'f', 'n'] as const).map(g => ({ case: c, number: 'sg' as const, gender: g })), { case: c, number: 'pl' as const }]) }
    }
    const slots: Slot[] = []
    for (const c of candidates) {
      for (const cs of DE_CASE_KEYS) {
        for (const num of ['sg', 'pl'] as const) {
          const x = this.f.noun(c.lemma, c.gender, c.plural, cs, num)
          if (!x.forms.length && num === 'pl') continue
          const forms = [...x.forms, ...x.also]
          // a plural the lens doesn't know: the form that isn't the lemma's singular may be it
          if (num === 'pl' && !c.plural && !this.f.nNoun(c.lemma)) continue
          if (forms.includes(w)) slots.push({ case: cs, number: num, gender: c.gender, lemma: c.lemma })
        }
      }
      // the singular a genitive or a plural the rules don't know: the form as the dictionary has it
    }
    if (!slots.length) {
      // the dictionary's form whose slot the rules don't make (Herzen, an old genitive): its gender, the case open
      const c = candidates[0]!
      return { lemma: c.lemma, gender: c.gender, slots: DE_CASE_KEYS.flatMap(cs => [{ case: cs, number: 'sg' as const, gender: c.gender }, { case: cs, number: 'pl' as const, gender: c.gender }]) }
    }
    return { lemma: candidates[0]!.lemma, gender: candidates[0]!.gender, slots }
  }

  /** A verb of the sentence: finite or not, its person and number where the form tells. */
  private verbOf(tok: Tok): VerbHit | null {
    const w = tok.low
    if (tok.cap && !tok.first) return null
    const table = this.governing.has(w) || this.motion.has(w) || this.rest.has(w) || this.copulas.has(w) || this.joined.has(w) || this.chunks.has(w)
    const dict = (this.s.dict?.grammarReadings(w) ?? []).filter(r => r.pos === 'verb' || r.pos === 'aux')
    if (!table && !dict.length) return null
    const g = dict.map(r => r.grammar ?? '').join(' ')
    const finite = !/^(infinitive|past participle)/.test(g) || /present|past|imperative|subjunctive|Konjunktiv/.test(g) || /(e|st|t|en)$/.test(w)
    let person: 1 | 2 | 3 | undefined
    let number: DeNumber | undefined
    if (/first-person singular/.test(g)) [person, number] = [1, 'sg']
    else if (/second-person singular/.test(g) || /, du$/.test(g)) [person, number] = [2, 'sg']
    else if (/third-person singular/.test(g)) [person, number] = [3, 'sg']
    else if (/second-person plural/.test(g)) [person, number] = [2, 'pl']
    else if (/plural/.test(g)) number = 'pl'
    else if (/st$/.test(w)) [person, number] = [2, 'sg']
    return { tok, lemma: dict[0]?.lemma ?? w, finite, ...(person ? { person } : {}), ...(number ? { number } : {}) }
  }

  private nameOf(tok: Tok, names: DeName[]): DeName | null {
    return names.find(n => n.name === tok.raw) ?? null
  }

  // --- the phrases ---------------------------------------------------------------------------------------------------

  /** The sentence's noun phrases, pronouns and names, each with the readings its forms leave. */
  private phrasesOf(toks: Tok[], names: DeName[]): Phrase[] {
    const out: Phrase[] = []
    let i = 0
    while (i < toks.length) {
      const t = toks[i]!
      let j = i
      let prep: Tok | undefined
      let prepEntry: DePreposition | undefined
      let fused: CaseTriggersDe['contractions'][number] | undefined
      if (this.fusedForms.has(t.low)) {
        fused = this.fusedForms.get(t.low)
        prep = t
        prepEntry = this.preps.get(fused!.preposition)
        j++
      } else if (this.preps.has(t.low) && toks[i + 1]?.clause === t.clause) {
        prep = t
        prepEntry = this.preps.get(t.low)
        j++
      }
      const p = this.phraseAt(toks, j, names, prep, prepEntry, fused)
      if (p) {
        out.push(p)
        i = p.head.i + 1
        continue
      }
      i++
    }
    return out
  }

  /** The phrase that starts at [j] (after a preposition [prep]); null for none there. */
  private phraseAt(toks: Tok[], j: number, names: DeName[], prep?: Tok, prepEntry?: DePreposition, fused?: CaseTriggersDe['contractions'][number]): Phrase | null {
    const t = toks[j]
    if (!t || (prep && t.clause !== prep.clause)) return null
    const base = { ...(prep ? { prep } : {}), ...(prepEntry ? { prepEntry } : {}), ...(fused ? { fused } : {}) }
    // a determiner, then adjectives (lowercase, an ending), then the noun (capitalized)
    const dets = fused ? [] : this.f.determiner(t.low)
    let k = j
    let detSlots: Slot[] | null = null
    let detLemma: string | undefined
    if (dets.length) {
      const next = toks[j + 1]
      if (next && next.clause === t.clause && (next.cap || /^[\p{Ll}]+(e|en|er|es|em)$/u.test(next.low) || /^\d+$/.test(next.raw))) {
        detSlots = dets.map(d => ({ case: d.case, number: d.number, ...(d.gender ? { gender: d.gender } : {}) }))
        detLemma = dets[0]!.lemma
        k = j + 1
      }
    }
    if (fused) {
      detSlots = fused.genders.map(g => ({ case: fused.case, number: 'sg' as const, gender: g }))
      detLemma = 'der'
    }
    const adjs: Tok[] = []
    const isAdj = (a: Tok) => (this.s.dict?.grammarReadings(a.low) ?? []).some(r => r.pos === 'adj' || r.pos === 'num')
    // a number before the noun (zwischen zwei Seen): no ending, the noun's case its phrase's
    const isNum = (a: Tok) => /^\d+$/.test(a.raw) || NUMBERS.test(a.low) || (this.s.dict?.grammarReadings(a.low) ?? []).some(r => r.pos === 'num')
    while (toks[k] && toks[k]!.clause === t.clause && (!toks[k]!.cap || (toks[k]!.first && isAdj(toks[k]!))) && (/^[\p{Ll}]+(e|en|er|es|em)$/u.test(toks[k]!.low) || isNum(toks[k]!) || this.f.table.adjective_fixed.includes(toks[k]!.low)) && toks[k + 1] && toks[k + 1]!.clause === t.clause) {
      // an adjective before its noun: never a pronoun, a preposition, a relative's dessen; with no determiner one the
      // dictionary has (not a verb: habe Angst)
      const a = toks[k]!
      if (PERSONAL.test(a.low) || this.preps.has(a.low) || this.f.determiner(a.low).length || a.low === 'dessen' || a.low === 'deren') break
      if (!detSlots && !isAdj(a) && !isNum(a)) break
      if (detSlots && !isAdj(a) && this.verbOf(a)?.finite && !/^(ge|.*(end|ene|enen|ener|enes|enem))/.test(a.low)) break
      adjs.push(a)
      k++
    }
    const head = toks[k]
    if (!head || head.clause !== t.clause) return null
    // a pronoun (no determiner, no adjectives before it)
    // (Sie, Ihnen: the polite you, capitalized)
    if (!detSlots && !adjs.length && (!head.cap || head.first || /^(Sie|Ihnen|Ihrer)$/.test(head.raw)) && this.pronouns.has(head.low)) {
      const pr = this.pronouns.get(head.low)
      if (pr && !(head.low === 'ihr' && toks[k + 1]?.cap && toks[k + 1]?.clause === head.clause)) {
        const slots = pr.flatMap(p => p.slots)
        return { ...base, adjs: [], head, pos: 'pron', lemma: pr[0]!.lemma, slots, all: slots, ...(pr.length === 1 && pr[0]!.person ? { person: pr[0]!.person } : {}) }
      }
    }
    if (!head.cap) return null
    const dictName = (this.s.dict?.grammarReadings(head.low) ?? []).find(r => r.pos === 'name')
    const name = this.nameOf(head, names) ?? (dictName && !this.s.nouns?.has(head.raw) ? { name: head.raw, ...(dictName.gender === 'm' || dictName.gender === 'f' ? { gender: dictName.gender } : {}) } : null)
    // a village's name in -s before a noun: whose (Kathis Gasthaus)
    const whose = !name && /^\p{Lu}\p{Ll}+s$/u.test(head.raw) && names.some(n => `${n.name}s` === head.raw) && toks[k + 1]?.cap && toks[k + 1]?.clause === head.clause
    const noun = name || whose ? null : this.nounOf(head)
    let slots: Slot[]
    let lemma: string
    let pos: Phrase['pos'] = 'noun'
    if (name || whose) {
      pos = 'name'
      const n = name ?? names.find(x => `${x.name}s` === head.raw)!
      lemma = n.name
      const g: GenderKey[] = n.gender ? [n.gender] : dictName ? ['n'] : ['m', 'f']
      // a name in -s before a noun: whose (Kathis Hund); else as it is, nom, acc, dat
      slots = head.raw === n.name ? (['nom', 'acc', 'dat'] as const).flatMap(c => g.map(x => ({ case: c, number: 'sg' as const, gender: x }))) : g.map(x => ({ case: 'gen' as const, number: 'sg' as const, gender: x }))
    } else if (noun) {
      lemma = noun.lemma
      slots = noun.slots
    } else return null
    const all = slots
    if (detSlots) slots = slots.filter(s => detSlots!.some(d => d.case === s.case && d.number === s.number && (s.number === 'pl' || !d.gender || !s.gender || d.gender === s.gender)))
    // the adjectives' endings after the determiner (or none: strong)
    for (const a of adjs) {
      if (isNum(a) || this.f.table.adjective_fixed.includes(a.low)) continue
      const ending = /em$/.test(a.low) ? 'em' : /en$/.test(a.low) ? 'en' : /er$/.test(a.low) ? 'er' : /es$/.test(a.low) ? 'es' : 'e'
      const ok = this.f.adjectiveSlots(ending, fused ? 'der' : detLemma ?? null)
      const left = slots.filter(s => ok.some(o => o.case === s.case && o.number === s.number && (s.number === 'pl' || !o.gender || !s.gender || o.gender === s.gender)))
      if (left.length) slots = left
    }
    if (!slots.length) slots = all
    // a number from two counts a plural, also a measure said as one (zwei Kilo, drei Liter)
    // (a number in words: a figure may be an ordinal, am 1. Mai; an ordinal word, ersten, is an adjective)
    if (adjs.some(a => NUMBERS.test(a.low))) {
      const pl = slots.filter(x => x.number === 'pl')
      slots = pl.length ? pl : dedupe(slots.map(x => ({ ...x, number: 'pl' as const })))
    }
    // one spelling, two nouns (Essen: das Essen, the plural of die Esse): the lemma the phrase's forms leave
    if (pos === 'noun') lemma = slots.find(x => x.lemma)?.lemma ?? lemma
    return { ...base, ...(detSlots && !fused ? { det: t, detLemma } : {}), adjs, head, pos, lemma, slots, all: detSlots ? all.filter(s => detSlots!.some(d => d.case === s.case && d.number === s.number)) : all }
  }

  // --- the sentence decides ------------------------------------------------------------------------------------------

  private settle(p: Phrase, slots: Slot[], how: { kind: CaseKind; sure?: CaseSure; trigger?: Tok; rule: string; sense?: string; written?: string }) {
    const left = slots.length ? slots : p.slots
    const cases = new Set(left.map(s => s.case))
    p.options = dedupe(left)
    p.pick = cases.size === 1 ? left[0] : undefined
    // one case left is decided (its number may stay open: a noun the lens doesn't know); more, ambiguous
    p.sure = how.sure ?? (cases.size === 1 ? 'decided' : 'ambiguous')
    if (!p.pick && how.sure === 'likely') p.pick = left[0]
    p.kind = how.kind
    p.trigger = how.trigger
    p.rule = how.rule
    if (how.sense) p.sense = how.sense
    if (how.written) p.written = how.written
  }

  /** Each phrase's case, decided by its sentence (see the file's head). */
  private decide(toks: Tok[], phrases: Phrase[]) {
    const clauses = [...new Set(toks.map(t => t.clause))]
    for (const c of clauses) {
      const ct = toks.filter(t => t.clause === c)
      const cp = phrases.filter(p => p.head.clause === c)
      const verbs = ct.map(t => this.verbOf(t)).filter((v): v is VerbHit => !!v && !cp.some(p => p.head === v.tok || p.adjs.includes(v.tok) || p.det === v.tok))
      const lows = ct.map(t => t.low)
      const lastLow = lows.at(-1) ?? ''
      // the governing verbs here: a plain one, a separable one with its particle at the clause's end (hört … zu) or joined
      const governing: { v: DeVerb; tok: Tok }[] = []
      for (const t of ct) {
        for (const v of this.governing.get(t.low) ?? []) if (!v.reflexive || lows.includes('sich') || lows.includes('mich') || lows.includes('dich') || lows.includes('uns') || lows.includes('euch')) governing.push({ v, tok: t })
        for (const v of this.joined.get(t.low) ?? []) governing.push({ v, tok: t })
        for (const v of this.particleVerbs) if (v.forms.includes(t.low) && lastLow === v.particle && ct.at(-1) !== t) governing.push({ v, tok: t })
      }
      const copula = ct.find(t => this.copulas.has(t.low) && !cp.some(p => p.det === t))
      const motion = ct.some(t => this.motion.has(t.low))
      const rest = ct.some(t => this.rest.has(t.low))
      const chunk = (prep: string) => {
        for (const t of ct) for (const ch of this.chunks.get(t.low) ?? []) if (ch.preposition === prep && (!ch.particle || lastLow === ch.particle || ch.joined?.includes(t.low))) return { ch, tok: t }
        return null
      }
      // fixed phrases first (zu Hause, Guten Morgen), then the times
      const text = lows.join(' ')
      for (const ph of this.phrases) {
        const at = ` ${text} `.indexOf(` ${ph.words} `)
        if (at < 0) continue
        const startIdx = ` ${text} `.slice(0, at).trim().split(' ').filter(Boolean).length
        const span = ph.words.split(' ')
        const words = ct.slice(startIdx, startIdx + span.length)
        if (ph.words === 'es gibt' || ph.words === 'gibt es') {
          for (const p of cp) if (p.head.i > words.at(-1)!.i && !p.prep && p.slots.some(s => s.case === 'acc')) this.settle(p, p.slots.filter(s => s.case === 'acc'), { kind: 'phrase', trigger: words.find(w => w.low === 'gibt'), rule: ph.rule })
          continue
        }
        for (const p of cp) {
          if (!words.some(w => w === p.head)) continue
          const fit = p.slots.filter(s => s.case === ph.case && s.number === ph.number)
          this.settle(p, fit.length ? fit : [{ case: ph.case, number: ph.number }], { kind: 'phrase', ...(ph.trigger ? { trigger: words.find(w => w.low === ph.trigger) } : {}), rule: ph.rule })
        }
      }
      for (const p of cp) {
        if (p.kind) continue
        const two = `${p.det?.low ?? ''} ${p.head.low}`.trim()
        if (!p.prep && this.timeGenitive.has(two)) {
          this.settle(p, [{ case: 'gen', number: 'sg', ...(p.slots[0]?.gender ? { gender: p.slots[0].gender } : {}) }], { kind: 'when', ...(p.det ? { trigger: p.det } : {}), rule: this.f.table.time_genitive.rule })
        } else if (!p.prep && p.det && this.timeNouns.has(p.lemma) && p.slots.some(s => s.case === 'acc') && p.detLemma !== 'der' || (!p.prep && !p.det && p.adjs.length && this.timeNouns.has(p.lemma) && p.slots.some(s => s.case === 'acc'))) {
          // a time with no preposition said by its determiner or adjective: the accusative (jeden Tag, nächste Woche)
          this.settle(p, p.slots.filter(s => s.case === 'acc'), { kind: 'time', rule: this.f.table.time.rule })
        }
      }
      // the prepositions
      for (const p of cp) {
        if (p.kind || !p.prep || !p.prepEntry) continue
        const takes = p.prepEntry.takes
        const fused = p.fused
        const allowed = fused ? [{ case: fused.case, sense: takes.find(x => x.case === fused.case)?.sense ?? 'other', rule: takes.find(x => x.case === fused.case)?.rule ?? takes[0]!.rule, time: takes.find(x => x.case === fused.case)?.time }] : takes
        // the written take first (trotz der Kälte: the genitive), the one people say only where the form is nothing else
        let left = p.slots.filter(s => allowed.some(x => x.case === s.case && !('spoken' in x && x.spoken)))
        if (!left.length) left = p.slots.filter(s => allowed.some(x => x.case === s.case))
        const cases = [...new Set(left.map(s => s.case))]
        const ch = chunk(fused?.preposition ?? p.prep.low)
        const isTime = this.timeNouns.has(p.lemma)
        const takeOf = (cs: DeCaseKey) => allowed.find(x => x.case === cs)
        if (ch && cases.includes(ch.ch.case)) {
          this.settle(p, left.filter(s => s.case === ch.ch.case), { kind: 'preposition', trigger: p.prep, rule: ch.ch.rule, sense: 'other' })
          continue
        }
        if (cases.length === 1) {
          const x = takeOf(cases[0]!)
          // the dative people say after a genitive's preposition: what it is, the written genitive beside it
          const spoken = x && 'spoken' in x && x.spoken ? this.writtenGenitive(p) : undefined
          this.settle(p, left, { kind: fused ? 'fused' : 'preposition', trigger: p.prep, rule: isTime && x?.time ? x.time : x?.rule ?? takes[0]!.rule, ...(x ? { sense: isTime && x.time ? 'time' : x.sense } : {}), ...(spoken ? { written: spoken } : {}) })
        } else if (cases.length > 1) {
          // a two-way preposition the forms leave open (no article: in Kärnten): the verb says wohin or wo, as likely
          const want: DeCaseKey | null = motion && !rest ? 'acc' : rest && !motion ? 'dat' : null
          const x = want ? takeOf(want) : undefined
          if (want && x) this.settle(p, [...left.filter(s => s.case === want), ...left.filter(s => s.case !== want)], { kind: 'preposition', sure: 'likely', trigger: p.prep, rule: x.rule, sense: x.sense })
          else this.settle(p, left, { kind: 'preposition', sure: 'ambiguous', trigger: p.prep, rule: takes[0]!.rule })
        }
      }
      // a genitive after its noun (das Haus des Müllers), a name in -s before one (Kathis Hund)
      for (const [n, p] of cp.entries()) {
        if (p.kind) continue
        if (p.pos === 'name' && p.slots.every(s => s.case === 'gen')) {
          const owned = cp.find(q => q.head.i === p.head.i + 1 || q.det?.i === p.head.i + 1 || q.adjs[0]?.i === p.head.i + 1)
          this.settle(p, p.slots, { kind: 'attribute', ...(owned ? { trigger: owned.head } : {}), rule: 'genitiv' })
          continue
        }
        const prev = cp[n - 1]
        const first = p.det ?? p.adjs[0] ?? p.head
        if (!p.prep && prev && prev.pos === 'noun' && prev.head.i === first.i - 1 && p.slots.some(s => s.case === 'gen')) {
          const only = p.slots.every(s => s.case === 'gen')
          this.settle(p, [...p.slots.filter(s => s.case === 'gen'), ...(only ? [] : p.slots.filter(s => s.case !== 'gen'))], { kind: 'attribute', trigger: prev.head, rule: 'genitiv', ...(only ? {} : { sure: 'likely' as const }) })
        }
      }
      // the dative of who has it, said in the south: dem Stefan sein Hund (written: Stefans Hund)
      for (const [n, p] of cp.entries()) {
        const q = cp[n + 1]
        if (p.kind || p.prep || !q || q.det?.i !== p.head.i + 1 || !['sein', 'ihr'].includes(q.detLemma ?? '') || !p.slots.some(s => s.case === 'dat')) continue
        if (!p.det && p.pos !== 'name') continue
        this.settle(p, p.slots.filter(s => s.case === 'dat'), { kind: 'possessor', trigger: q.det, rule: 'genitiv', ...(p.pos === 'name' ? { written: `${p.lemma}${/[sßxz]$/.test(p.lemma) ? "'" : 's'} ${q.head.raw}` } : {}) })
      }
      // the genitive only where something asks for it (a noun before it, its preposition, a verb, a time): else it is out
      const genVerb = governing.some(g => g.v.takes[0] === 'gen')
      for (const p of cp) {
        if (p.kind || p.prep || genVerb) continue
        const other = p.slots.filter(s => s.case !== 'gen')
        if (other.length && other.length < p.slots.length) {
          p.out = [...(p.out ?? []), ...p.slots.filter(s => s.case === 'gen').map(s => ({ case: s.case as CaseKey, number: s.number as NumberKey, why: 'needs-trigger' as const }))]
          p.slots = other
        }
      }
      // a word of feeling with the dative (Mir ist kalt)
      const pred = ct.find(t => this.predicates.has(t.low))
      if (pred && copula) {
        for (const p of cp) if (!p.kind && !p.prep && p.slots.some(s => s.case === 'dat') && p.pos === 'pron') this.settle(p, p.slots.filter(s => s.case === 'dat'), { kind: 'predicate', trigger: pred, rule: this.predicates.get(pred.low)!.rule })
      }
      // the verbs that govern a case: the dative (helfen), the dative and the accusative (geben), the genitive (gedenken)
      for (const { v, tok } of governing) {
        const free = cp.filter(p => !p.kind && !p.prep)
        const [first, second] = v.takes as DeCaseKey[]
        const subjectElsewhere = free.some(p => p.slots.length && p.slots.every(s => s.case === 'nom'))
        // with no subject by its form, the first that can be one is it (Lena gibt dem Pferd einen Apfel)
        const subjectLikely = subjectElsewhere ? undefined : free.find(p => p.slots.some(s => s.case === 'nom'))
        const objects = free.filter(p => p !== subjectLikely && !p.slots.every(s => s.case === 'nom'))
        const only = (p: Phrase, c: DeCaseKey) => p.slots.every(s => s.case === c)
        const settleAs = (p: Phrase, c: DeCaseKey, sure: boolean, kind: CaseKind, rule: string) =>
          this.settle(p, [...p.slots.filter(s => s.case === c), ...(sure ? [] : p.slots.filter(s => s.case !== c))], { kind, trigger: tok, rule, ...(sure ? {} : { sure: 'likely' as const }) })
        if (second === 'acc') {
          // whom (the dative) and what (the accusative): by their forms, else the dative first (Lena gibt dem Pferd einen
          // Apfel); one object alone is what is given, unless its form says the dative (Ich kaufe zwei Semmeln)
          const canDat = objects.filter(p => p.slots.some(s => s.case === first))
          // what is given already said (a phrase, an accusative by its form): the one left that can be the dative is whom
          const given = cp.some(p => p.kind && !p.prep && p.pick?.case === 'acc')
          const dat = canDat.find(p => only(p, first)) ?? ((objects.length >= 2 || given) && canDat.length ? canDat[0] : undefined)
          if (dat) settleAs(dat, first, only(dat, first) || subjectElsewhere && objects.length >= 2, 'verb', v.rule)
          for (const p of objects) {
            if (p === dat || p.kind || !p.slots.some(s => s.case === 'acc')) continue
            settleAs(p, 'acc', only(p, 'acc') || (!!dat && subjectElsewhere), 'object', 'akkusativ')
          }
          continue
        }
        const fit = objects.filter(p => p.slots.some(s => s.case === first))
        const sure = fit.filter(p => only(p, first))
        const notSubject = fit.filter(p => !p.slots.some(s => s.case === 'nom'))
        const pick = sure[0] ?? (fit.length === 1 ? fit[0] : notSubject.length === 1 ? notSubject[0] : undefined)
        if (pick) settleAs(pick, first, only(pick, first) || subjectElsewhere, 'verb', v.rule)
      }
      // a copula: what something is stays in the nominative
      if (copula) {
        const free = cp.filter(p => !p.kind && !p.prep && p.slots.some(s => s.case === 'nom'))
        // the subject: a personal pronoun, else the first; the others say what it is
        const subject = free.find(p => p.pos === 'pron') ?? free[0]
        for (const p of free) this.settle(p, p.slots.filter(s => s.case === 'nom'), { kind: p === subject ? 'subject' : 'copula', trigger: copula, rule: 'faelle' })
      }
      // the rest: the subject (nominative) and the object (accusative) by their forms, then by elimination and order
      const finite = verbs.find(v => v.finite)
      const rest_ = cp.filter(p => !p.kind && !p.prep)
      const subjects = rest_.filter(p => p.slots.length && p.slots.every(s => s.case === 'nom'))
      for (const p of subjects) this.settle(p, p.slots, { kind: 'subject', ...(finite ? { trigger: finite.tok } : {}), rule: 'faelle' })
      for (const p of rest_.filter(p => !p.kind)) {
        const cases = new Set(p.slots.map(s => s.case))
        if (cases.size === 1) {
          const c = [...cases][0]!
          this.settle(p, p.slots, { kind: c === 'acc' ? 'object' : c === 'nom' ? 'subject' : 'form', ...(finite && (c === 'acc' || c === 'nom') ? { trigger: finite.tok } : {}), rule: c === 'acc' ? 'akkusativ' : DE_CASE_PAGES[c] })
        }
      }
      const open = rest_.filter(p => !p.kind)
      if (open.length && finite) {
        // an imperative (its verb first, no question, no pronoun for who): it has no subject (Bring mir die Schüssel!)
        const imperative = finite.tok === ct[0] && !finite.tok.question && !cp.some(p => p.pos === 'pron' && p.slots.every(x => x.case === 'nom'))
        const subjectTaken = imperative || cp.some(p => p.kind === 'subject' && p.head.clause === c)
        for (const p of open) {
          const nomAcc = p.slots.filter(s => s.case === 'nom' || s.case === 'acc')
          if (!nomAcc.length) {
            this.settle(p, p.slots, { kind: 'form', sure: p.slots.length > 1 && new Set(p.slots.map(s => s.case)).size > 1 ? 'ambiguous' : undefined, rule: DE_CASE_PAGES[p.slots[0]!.case] })
            continue
          }
          if (subjectTaken && nomAcc.some(s => s.case === 'acc')) {
            // one subject a clause (a copula aside): the other is the object
            this.settle(p, nomAcc.filter(s => s.case === 'acc'), { kind: 'object', trigger: finite.tok, rule: 'akkusativ' })
            continue
          }
          // nothing decides it but the order: the subject usually first, the object after the verb (likely)
          const others = open.filter(q => q !== p)
          const firstOfTwo = others.length >= 1 && p.head.i < Math.min(...others.map(q => q.head.i))
          const agrees = finite.person === undefined || p.pos !== 'noun' || finite.person === 3
          const want: DeCaseKey = !subjectTaken && (firstOfTwo || others.length === 0) && agrees ? 'nom' : 'acc'
          const ordered = [...nomAcc.filter(s => s.case === want), ...p.slots.filter(s => s.case !== want)]
          this.settle(p, ordered, { kind: want === 'nom' ? 'subject' : 'object', sure: ordered.length && new Set(ordered.map(s => s.case)).size > 1 ? 'likely' : undefined, trigger: finite.tok, rule: want === 'nom' ? 'faelle' : 'akkusativ' })
        }
      }
      // what is left with no verb: a name or a noun alone in its clause is a call (Guten Morgen, Kathi!), the nominative
      if (!verbs.length) {
        const alone = cp.filter(p => !p.kind && !p.prep)
        // a call is a name or a bare noun (Schatzi, Fledermäuse), never a phrase with an article or a number (noch ein Spiel)
        if (alone.length === 1 && (alone[0]!.pos === 'name' || (alone[0]!.pos === 'noun' && !alone[0]!.det && !alone[0]!.adjs.length)) && alone[0]!.slots.some(s => s.case === 'nom') && cp.filter(p => p.head.clause === c).length === 1)
          this.settle(alone[0]!, alone[0]!.slots.filter(s => s.case === 'nom'), { kind: 'address', rule: this.f.table.roles.address.rule })
      }
      // what is left (a heading, a phrase alone): its forms alone
      for (const p of cp) if (!p.kind) {
        const cases = new Set(p.slots.map(s => s.case))
        this.settle(p, p.slots, { kind: cases.size === 1 ? 'form' : 'form', sure: cases.size === 1 ? 'decided' : 'ambiguous', rule: cases.size === 1 ? DE_CASE_PAGES[[...cases][0]!] : 'faelle' })
      }
    }
  }

  /** The genitive a spoken dative stands for, where the rules are sure of it (wegen dem Regen: des Regens). */
  private writtenGenitive(p: Phrase): string | undefined {
    const s = p.slots.find(x => x.case === 'dat')
    if (!s || !s.gender || p.pos !== 'noun') return undefined
    const det = p.detLemma ? this.f.determinerForm(p.detLemma, 'gen', s.gender, s.number) : undefined
    const noun = this.f.noun(p.lemma, s.gender, this.s.nouns?.get(p.lemma)?.plural, 'gen', s.number)
    if (!noun.sure || !noun.forms.length || (p.detLemma && !det)) return undefined
    const adjs = p.adjs.map(a => this.f.adjectiveForm(a.low.replace(/(em|en|er|es|e)$/, ''), p.detLemma ?? null, 'gen', s.gender!, s.number))
    return [det, ...adjs, noun.forms[0]].filter(Boolean).join(' ')
  }

  /** Relative pronouns: der, dem, denen … after a comma, standing for the noun before it, each as a phrase of its own. */
  private relatives(toks: Tok[], phrases: Phrase[]): Phrase[] {
    const out: Phrase[] = []
    for (const t of toks) {
      if (t.i === 0 || toks[t.i - 1]!.clause === t.clause && !this.preps.has(toks[t.i - 1]!.low)) continue
      const forms = this.relativeOnly.get(t.low)
      if (!forms) continue
      const next = toks[t.i + 1]
      // dessen, deren before a noun: whose (der Bub, dessen Hund bellt); another relative form there is an article
      const whose = t.low === 'dessen' || t.low === 'deren'
      const adjLike = (x: Tok) => /^[\p{Ll}]+(e|en|er|es|em)$/u.test(x.low) && !this.f.determiner(x.low).length && !this.pronouns.has(x.low)
      const relOnly = t.low === 'denen'
      if (!whose && !relOnly && next && next.clause === t.clause && (next.cap || adjLike(next) && toks[t.i + 2]?.cap)) continue
      const prepTok = this.preps.has(toks[t.i - 1]!.low) && toks[t.i - 1]!.clause === t.clause ? toks[t.i - 1] : undefined
      const anchor = prepTok ? prepTok.i - 1 : t.i - 1
      const ante = [...phrases].reverse().find(p => p.head.i <= anchor && p.pos !== 'pron')
      if (!ante || ante.head.clause !== toks[anchor]?.clause) continue
      const g = ante.slots[0]
      const slots = forms.filter(s => !g || s.number === g.number && (s.number === 'pl' || !g.gender || s.gender === g.gender))
      if (!slots.length) continue
      out.push({ ...(prepTok ? { prep: prepTok, prepEntry: this.preps.get(prepTok.low)! } : {}), adjs: [], head: t, pos: 'rel', lemma: 'der', slots, all: forms })
    }
    return out
  }

  // --- what the lens says -------------------------------------------------------------------------------------------

  private ref(t: Tok, lemma?: string): WordRef {
    return { i: t.i, word: t.raw, start: t.start, end: t.end, ...(lemma ? { lemma } : {}) }
  }

  private wordCase(p: Phrase, t: Tok, agrees?: Tok): WordCase {
    const readings: FormReading[] = dedupe(p.all).map(s => ({ lemma: p.lemma, pos: p.pos === 'rel' ? 'pron' : p.pos === 'name' ? 'name' : agrees ? (t === p.det ? 'det' : 'adj') : p.pos, case: s.case as CaseKey, number: s.number as NumberKey, genders: s.gender ? [s.gender] : (['m', 'f', 'n'] as GenderKey[]) }))
    const gendered = !!agrees || p.pos === 'pron' || p.pos === 'rel'
    const opt = (s: Slot): CaseOption => ({ case: s.case as CaseKey, number: s.number as NumberKey, ...(gendered && s.gender && s.number === 'sg' ? { gender: s.gender } : {}), ...(p.kind ? { as: p.kind } : {}) })
    const options = (p.options ?? p.slots).map(opt)
    const shown = new Set((p.options ?? []).map(s => `${s.case}.${s.number}`))
    const out: RuledOut[] = dedupe(p.all).filter(s => !shown.has(`${s.case}.${s.number}`)).map(s => ({ case: s.case as CaseKey, number: s.number as NumberKey, why: p.prep ? 'preposition' : 'governed' }))
    const sure = p.sure ?? 'ambiguous'
    const pick = p.pick && sure !== 'ambiguous' ? opt(p.pick) : null
    return {
      ...this.ref(t),
      readings,
      pick,
      sure,
      kind: p.kind ?? 'form',
      ...(p.trigger ? { trigger: this.ref(p.trigger, this.lemmaOf(p.trigger)) } : {}),
      ...(p.sense ? { sense: p.sense } : {}),
      rule: p.rule ?? 'faelle',
      options: dedupeOptions(options),
      out: dedupeOut(out),
      ...(agrees ? { agrees: this.ref(agrees) } : {}),
      ...(p.written ? { written: p.written } : {}),
    }
  }

  private lemmaOf(t: Tok): string | undefined {
    for (const v of this.governing.get(t.low) ?? []) return v.lemma
    for (const v of this.joined.get(t.low) ?? []) return v.lemma
    for (const v of this.particleVerbs) if (v.forms.includes(t.low)) return v.lemma
    return undefined
  }

  /** The sentence through the lens: each word with a case (the phrase's words: the determiner and adjectives agree with the noun), and the words that decide. */
  analyze(line: string, names: DeName[] = []): LineCases {
    const toks = this.tokens(line)
    const phrases = this.phrasesOf(toks, names)
    const rel = this.relatives(toks, phrases)
    const all = [...phrases.filter(p => !rel.some(r => r.head === p.head || r.head === p.det)), ...rel].sort((a, b) => a.head.i - b.head.i)
    this.decide(toks, all.filter(p => p.pos !== 'rel'))
    this.decideRelatives(toks, rel, all)
    const words: WordCase[] = []
    for (const p of all) {
      if (p.fused) words.push({ ...this.wordCase(p, p.prep!, p.head), kind: 'fused', trigger: this.ref(p.prep!) })
      if (p.det) words.push(this.wordCase(p, p.det, p.head))
      // a number has no case of its own (zwei Seen): only the adjectives agree
      for (const a of p.adjs) if (!/^\d+$/.test(a.raw) && !NUMBERS.test(a.low)) words.push(this.wordCase(p, a, p.head))
      words.push(this.wordCase(p, p.head))
    }
    const governs = new Map<number, Governs>()
    for (const w of words) {
      if (!w.trigger || !w.pick || w.agrees || !['preposition', 'verb', 'predicate', 'fused', 'attribute', 'phrase'].includes(w.kind) || w.trigger.i === w.i) continue
      const g = governs.get(w.trigger.i) ?? { ...w.trigger, words: [] }
      g.words.push({ i: w.i, word: w.word, start: w.start, end: w.end, case: w.pick.case })
      governs.set(w.trigger.i, g)
    }
    return { words: words.sort((a, b) => a.i - b.i), governs: [...governs.values()].sort((a, b) => a.i - b.i) }
  }

  /** A relative pronoun's case: its preposition's, its form's (denen, dessen), else its job in its own clause. */
  private decideRelatives(toks: Tok[], rel: Phrase[], all: Phrase[]) {
    for (const p of rel) {
      if (p.prep && p.prepEntry) {
        const left = p.slots.filter(s => p.prepEntry!.takes.some(x => x.case === s.case))
        const cases = new Set(left.map(s => s.case))
        this.settle(p, left, { kind: 'preposition', trigger: p.prep, rule: this.f.table.relative.rule, ...(cases.size === 1 ? { sense: p.prepEntry.takes.find(x => x.case === [...cases][0])?.sense ?? 'other' } : { sure: 'ambiguous' as const }) })
        continue
      }
      const cases = new Set(p.slots.map(s => s.case))
      const clause = toks.filter(t => t.clause === p.head.clause)
      const owned = toks[p.head.i + 1]
      if (cases.size === 1 && cases.has('gen') && owned?.cap && owned.clause === p.head.clause) {
        this.settle(p, p.slots, { kind: 'attribute', trigger: owned, rule: this.f.table.relative.rule })
        continue
      }
      const verb = [...clause].reverse().find(t => this.verbOf(t) && t !== p.head)
      const governed = clause.flatMap(t => this.governing.get(t.low) ?? []).find(v => v.takes[0] === 'dat' || v.takes[0] === 'gen')
      if (cases.size === 1) {
        const c = [...cases][0]!
        this.settle(p, p.slots, { kind: c === 'nom' ? 'subject' : c === 'acc' ? 'object' : c === 'dat' && governed ? 'verb' : c === 'gen' ? 'attribute' : 'form', ...(verb ? { trigger: verb } : {}), rule: this.f.table.relative.rule })
        continue
      }
      if (governed && cases.has(governed.takes[0] as DeCaseKey)) {
        const vt = clause.find(t => (this.governing.get(t.low) ?? []).includes(governed))
        this.settle(p, p.slots.filter(s => s.case === governed.takes[0]), { kind: 'verb', ...(vt ? { trigger: vt } : {}), rule: this.f.table.relative.rule })
        continue
      }
      // der, die, das: the subject, unless another word of its clause is (then the object)
      const others = all.filter(q => q !== p && q.head.clause === p.head.clause && !q.prep)
      const subjectThere = others.some(q => q.slots.length && q.slots.every(s => s.case === 'nom'))
      const want: DeCaseKey = subjectThere ? 'acc' : 'nom'
      const left = p.slots.filter(s => s.case === want)
      this.settle(p, left.length ? left : p.slots, { kind: want === 'nom' ? 'subject' : 'object', ...(verb ? { trigger: verb } : {}), rule: this.f.table.relative.rule, ...(subjectThere || left.length === 0 ? {} : { sure: 'likely' as const }) })
      if (!subjectThere && left.length) p.options = [...left, ...p.slots.filter(s => s.case !== want)]
    }
  }

  /** The word [word] of [line] (its [nth] of the same), as GET /lookup's `case`; a trigger tapped: what it governs. */
  at(line: string, word: string, nth = 0, names: DeName[] = []): ((WordCase & { governs?: Governs }) | (Governs & { governs: Governs })) | null {
    const form = lower(word.replace(/^[^\p{L}\d]+|[^\p{L}\p{M}\d]+$/gu, ''))
    const forms = [...line.matchAll(/[\p{L}\p{M}]+|\d+/gu)].map(m => lower(m[0]))
    const at = forms.flatMap((t, i) => (t === form ? [i] : []))
    const i = at[Math.min(Math.max(0, nth), at.length - 1)]
    if (i === undefined) return null
    const r = this.analyze(line, names)
    const w = r.words.find(x => x.i === i)
    const g = r.governs.find(x => x.i === i)
    if (w) return g ? { ...w, governs: g } : w
    return g ? { ...g, governs: g } : null
  }
}

function dedupe(slots: Slot[]): Slot[] {
  const seen = new Set<string>()
  return slots.filter(s => (seen.has(key(s)) ? false : (seen.add(key(s)), true)))
}

function dedupeOptions(o: CaseOption[]): CaseOption[] {
  const seen = new Set<string>()
  return o.filter(x => {
    const k = `${x.case}.${x.number}.${x.gender ?? ''}`
    return seen.has(k) ? false : (seen.add(k), true)
  })
}

function dedupeOut(o: RuledOut[]): RuledOut[] {
  const seen = new Set<string>()
  return o.filter(x => {
    const k = `${x.case}.${x.number}`
    return seen.has(k) ? false : (seen.add(k), true)
  })
}
