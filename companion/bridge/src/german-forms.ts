// German's forms by case, from its trigger table (case-triggers-de.ts; docs/plans/05-built/case-de.md): what a determiner's
// form can be (der: nom.m, dat.f, gen.f, gen.pl), the determiner's and the adjective's form in a slot (dem, großen), and
// a noun's forms by case and number by its rules (dem Hund; des Hundes or des Hunds; den Kindern; dem Kollegen, the
// n-Deklination's), with how sure each is. The case lens reads a sentence with them (case-lens-de.ts), the bank's checks
// check the German bank with them (case-bank.ts), and the app declines the learner's nouns by the same rules
// (data/GermanForms.kt, its parity fixture test/fixtures/german-forms.json).
import type { CaseTriggersDe, DeCaseKey, DeGenderKey } from './case-triggers-de'
import { DE_CASE_KEYS } from './case-triggers-de'

export type DeNumber = 'sg' | 'pl'
/** One reading of a determiner's form: its lemma, kind, case, and gender (none: the plural). */
export type DetReading = { lemma: string; kind: 'definite' | 'der' | 'ein' | 'possessive'; case: DeCaseKey; number: DeNumber; gender?: DeGenderKey }
/** A noun's forms in one slot: the standard ones (the first shown), others said and right ([also]), and whether the rules are sure. */
export type NounSlot = { forms: string[]; also: string[]; sure: boolean }
/** The adjective declension after a determiner: weak (der, dieser), mixed (ein, mein), strong (none). */
export type Declension = 'weak' | 'mixed' | 'strong'

const lower = (s: string) => s.toLocaleLowerCase('de')
const VOWELS = /[aeiouäöüy]+/g

export class GermanForms {
  private readonly byForm = new Map<string, DetReading[]>()
  private readonly byLemma = new Map<string, CaseTriggersDe['determiners'][number]>()
  private readonly nNouns = new Map<string, CaseTriggersDe['n_declension'][number]>()

  constructor(readonly table: CaseTriggersDe) {
    for (const d of table.determiners) {
      this.byLemma.set(d.lemma, d)
      for (const [slot, form] of Object.entries(d.forms)) {
        if (!form) continue
        const [c, g] = slot.split('.') as [DeCaseKey, string]
        const r: DetReading = { lemma: d.lemma, kind: d.kind, case: c, number: g === 'pl' ? 'pl' : 'sg', ...(g === 'pl' ? {} : { gender: g as DeGenderKey }) }
        const list = this.byForm.get(form) ?? []
        list.push(r)
        this.byForm.set(form, list)
      }
    }
    for (const n of table.n_declension) this.nNouns.set(n.lemma, n)
  }

  /** Every reading of [form] as a determiner (lowercase compared); none for a word that isn't one. */
  determiner(form: string): DetReading[] {
    return this.byForm.get(lower(form)) ?? []
  }

  /** The determiner [lemma]'s form in a slot; undefined where it has none (ein in the plural). */
  determinerForm(lemma: string, c: DeCaseKey, gender: DeGenderKey, number: DeNumber): string | undefined {
    return this.byLemma.get(lemma)?.forms[`${c}.${number === 'pl' ? 'pl' : gender}`]
  }

  /** The adjectives' declension after determiner [lemma] (null: none). */
  declensionAfter(lemma: string | null): Declension {
    if (!lemma) return 'strong'
    const k = this.byLemma.get(lemma)?.kind
    return k === 'ein' || k === 'possessive' ? 'mixed' : 'weak'
  }

  /** An adjective's ending in a slot of a declension ("en"). */
  ending(decl: Declension, c: DeCaseKey, gender: DeGenderKey, number: DeNumber): string {
    return this.table.adjective_endings[decl][`${c}.${number === 'pl' ? 'pl' : gender}`] ?? ''
  }

  /** The stem an adjective takes its ending on (hoch: hoh, dunkel: dunkl); null for one that takes none (rosa). */
  adjectiveStem(lemma: string): string | null {
    const l = lower(lemma)
    if (this.table.adjective_fixed.includes(l)) return null
    return this.table.adjective_stems[l] ?? l
  }

  /** The adjective [lemma] in a slot after determiner [det] (none: strong): großen, hohe, rosa. */
  adjectiveForm(lemma: string, det: string | null, c: DeCaseKey, gender: DeGenderKey, number: DeNumber): string {
    const stem = this.adjectiveStem(lemma)
    if (stem === null) return lower(lemma)
    return stem + this.ending(this.declensionAfter(det), c, gender, number)
  }

  /** Every slot an adjective's ending can be in after [det] (the strong ones when none): "großen" after dem → dat.m, dat.n. */
  adjectiveSlots(ending: string, det: string | null): { case: DeCaseKey; number: DeNumber; gender?: DeGenderKey }[] {
    const e = this.table.adjective_endings[this.declensionAfter(det)]
    return Object.entries(e).filter(([, x]) => x === ending).map(([slot]) => {
      const [c, g] = slot.split('.') as [DeCaseKey, string]
      return { case: c, number: g === 'pl' ? 'pl' : 'sg', ...(g === 'pl' ? {} : { gender: g as DeGenderKey }) }
    })
  }

  /** The n-Deklination's entry of a noun (Herr, Kollege, Name), if it is one. */
  nNoun(lemma: string) {
    return this.nNouns.get(lemma)
  }

  /**
   * The genitive singular of a der or das noun by its rules: -es after s, ß, x, z (des Hauses, des Platzes), -s after a
   * vowel or an unstressed ending (des Lehrers, des Mädchens, des Autos), a short noun either (des Hundes, des Hunds:
   * the first shown), a longer one -s first (des Abends, des Abendes). Not sure for a noun in a single -s after a short
   * vowel, which may double it (des Busses, des Zeugnisses).
   */
  genitive(lemma: string): NounSlot {
    const l = lower(lemma)
    const syllables = (l.match(VOWELS) ?? []).length
    if (/sch$/.test(l)) return { forms: [lemma + 'es'], also: [lemma + 's'], sure: true }
    if (/(ss|ß|x|z|tz)$/.test(l) || /(au|ei|ai|eu|äu|ie)s$/.test(l)) return { forms: [lemma + 'es'], also: [], sure: true }
    if (/s$/.test(l)) return { forms: [lemma + 'es'], also: [], sure: false }
    if (/[aeiouäöüy]$/.test(l)) return { forms: [lemma + 's'], also: [], sure: true }
    if (syllables >= 2 && /(el|er|en|em|chen|lein|ling|ig|ich|ik|um|ment|in|or)$/.test(l)) return { forms: [lemma + 's'], also: [], sure: true }
    if (syllables <= 1) return { forms: [lemma + 'es'], also: [lemma + 's'], sure: true }
    return { forms: [lemma + 's'], also: [lemma + 'es'], sure: true }
  }

  /** The dative plural of [plural] (its nominative): -n added unless it ends in -n or -s (den Kindern, den Frauen, den Autos). */
  dativePlural(plural: string): string {
    return /[ns]$/.test(plural) ? plural : plural + 'n'
  }

  /**
   * A noun's forms in [c] and [number]: its [gender] and its nominative plural ([plural], none: unknown, so the plural
   * isn't sure). The n-Deklination by the table (dem Kollegen; spoken: dem Bub, never wrong, as [also] for one with no
   * -e); a der or das noun's genitive by [genitive]; the dative singular's old -e (dem Manne) is never the answer, never
   * wrong ([also]).
   */
  noun(lemma: string, gender: DeGenderKey, plural: string | undefined, c: DeCaseKey, number: DeNumber): NounSlot {
    const n = this.nNoun(lemma)
    if (number === 'pl') {
      const pl = n?.plural ?? (n ? n.oblique : plural)
      if (!pl) return { forms: [], also: [], sure: false }
      return { forms: [c === 'dat' ? this.dativePlural(pl) : pl], also: [], sure: true }
    }
    if (n) {
      if (c === 'nom') return { forms: [lemma], also: [], sure: true }
      if (n.gender === 'n' && c === 'acc') return { forms: [n.accusative ?? lemma], also: [], sure: true }
      const form = c === 'gen' ? (n.genitive ?? n.oblique) : n.oblique
      // in speech the -n goes on a noun with no -e (den Mensch, dem Bub, dem Student): said, never wrong
      return { forms: [form], also: !/e$/.test(lemma) && c !== 'gen' ? [lemma] : [], sure: true }
    }
    if (gender === 'f' || c === 'nom' || c === 'acc') return { forms: [lemma], also: [], sure: true }
    // the dative is the word as it is (the old -e, dem Manne, is in fixed phrases: zu Hause, the table's)
    if (c === 'dat') return { forms: [lemma], also: [], sure: true }
    return this.genitive(lemma)
  }

  /** The cases in the course's order. */
  get cases(): readonly DeCaseKey[] {
    return DE_CASE_KEYS
  }
}

/**
 * A noun's nominative plural as the dictionary has it (its forms' grammar: "plural", not a case of it), capitalized as a
 * noun is; undefined where it has none. [dict]: the Lexicon (lemmasOf, paradigm).
 */
export function dictPlural(dict: { lemmasOf(l: string): { index: number | null; lemma: string; pos: string }[]; paradigm(i: number | null, l: string, pos: string): [string, string | null][] } | undefined, lemma: string): string | undefined {
  if (!dict) return undefined
  for (const e of dict.lemmasOf(lemma)) {
    if (e.pos !== 'noun') continue
    const hit = dict.paradigm(e.index, e.lemma, 'noun').find(([, g]) => !!g && /plural/.test(g) && !/dative|genitive|singular/.test(g))
    if (hit) return hit[0].charAt(0).toLocaleUpperCase('de') + hit[0].slice(1)
  }
  return undefined
}

/** A pack word's German as its noun ("der Steg" → Steg, m; "die Stege" → Stege): the lemma without its article. */
export function bareNoun(written: string | undefined): string | undefined {
  const w = written?.trim()
  if (!w) return undefined
  const m = /^(?:der|die|das)\s+(\S+)$/u.exec(w)
  const lemma = m ? m[1]! : w
  return /^\p{Lu}[\p{L}-]*$/u.test(lemma) ? lemma : undefined
}
