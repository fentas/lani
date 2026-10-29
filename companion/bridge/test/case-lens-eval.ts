// The case lens against its hand-labelled sentences (test/fixtures/case-lens-gold.json, the set it was built on;
// case-lens-heldout.json, lines drawn after it was written and labelled before it ran on them, its misses then fixed; and
// case-lens-heldout2.json, drawn after those fixes, run once): how often its pick is the
// labelled case and number, how often it decides wrongly (the one that matters: a wrong answer said as sure), its
// trigger and its rule, and the labelled ambiguities it keeps open. The smoke checks the numbers (test/smoke/case-lens.ts);
// The case trainer's bank too, once it is merged (trainerItems). `bun test/case-lens-eval.ts [gold|heldout|heldout2|trainer]
// [-v]` prints them with each miss.
import { existsSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { validateCaseTriggers } from '../src/case-triggers'
import { CaseLens, namesOf, type CaseOption } from '../src/case-lens'
import { FormsBook } from '../src/forms'
import { Lexicon } from '../src/lexicon'

const companion = resolve(import.meta.dir, '../..')

export type GoldItem = {
  sl: string
  en: string
  from: string
  word: string
  nth?: number
  case?: CaseOption['case']
  number?: CaseOption['number']
  trigger?: string
  rule?: string
  also?: { case: string; number: string }[]
  ambiguous?: { case: string; number: string }[]
  note?: string
}

/** The village's names the sentences have (the villagers' given names, the smoke's learner Jan), declined. */
export const VILLAGE_NAMES = namesOf(['Ančka', 'Anton', 'France', 'Janez', 'Jože', 'Luka', 'Marko', 'Micka', 'Mojca', 'Nejc', 'Tine', 'Tone', 'Vida', 'Zala', 'Jan'].map(given => ({ given })))

/** The lens over the repository's dictionary, forms book and trigger table. */
export function repoLens(): CaseLens {
  const dict = new Lexicon({ dir: join(companion, 'lexicon'), language: 'sl', tutorFile: join(tmpdir(), 'lani-case-lens-no-glosses.json') })
  const book = new FormsBook({ dir: join(companion, 'lexicon'), language: 'sl' })
  const v = validateCaseTriggers(JSON.parse(readFileSync(join(companion, 'grammar/sl/data/case-triggers.json'), 'utf8')))
  if (!v.ok) throw new Error(`case-triggers.json: ${v.errors}`)
  return new CaseLens({ dict, book, table: v.table })
}

export type Evaluation = {
  items: number
  /** Items labelled with one case and number. */
  labelled: number
  right: number
  /** Picked a wrong case or number and said it is decided: what must not happen. */
  wrongDecided: number
  /** Picked a wrong one as likely (shown with the others). */
  wrongLikely: number
  /** No pick: the dictionary doesn't know the word, or the line leaves it open. */
  open: number
  triggers: { labelled: number; right: number }
  rules: { labelled: number; right: number }
  ambiguous: { labelled: number; keptOpen: number }
  misses: string[]
  ms: number
}

export function evaluate(lens: CaseLens, items: GoldItem[]): Evaluation {
  const e: Evaluation = { items: items.length, labelled: 0, right: 0, wrongDecided: 0, wrongLikely: 0, open: 0, triggers: { labelled: 0, right: 0 }, rules: { labelled: 0, right: 0 }, ambiguous: { labelled: 0, keptOpen: 0 }, misses: [], ms: 0 }
  const t0 = performance.now()
  for (const g of items) {
    const w = lens.at(g.sl, g.word, g.nth ?? 0, VILLAGE_NAMES)
    const said = w && 'pick' in w ? `${w.pick ? `${w.pick.case}.${w.pick.number}` : '-'} ${w.sure} ${w.kind} by ${w.trigger?.word ?? '-'} (${w.rule}) of [${w.options.map(o => `${o.case}.${o.number}`).join(' ')}]` : 'no case'
    if (g.ambiguous) {
      e.ambiguous.labelled++
      const got = new Set(w && 'options' in w ? w.options.map(o => `${o.case}.${o.number}`) : [])
      if (w && 'sure' in w && w.sure !== 'decided' && g.ambiguous.every(a => got.has(`${a.case}.${a.number}`))) e.ambiguous.keptOpen++
      else e.misses.push(`open?  ${g.sl} | ${g.word}: ${g.ambiguous.map(a => `${a.case}.${a.number}`).join(' or ')} → ${said}`)
      continue
    }
    e.labelled++
    if (!w || !('pick' in w) || !w.pick) {
      e.open++
      e.misses.push(`open   ${g.sl} | ${g.word}: ${g.case}.${g.number} → ${said}`)
      continue
    }
    const fits = (c: string, n: string) => w.pick!.case === c && w.pick!.number === n
    if (fits(g.case!, g.number!) || (g.also ?? []).some(a => fits(a.case, a.number))) {
      e.right++
      if (g.trigger !== undefined) {
        e.triggers.labelled++
        if (w.trigger?.word === g.trigger) e.triggers.right++
        else e.misses.push(`trigger ${g.sl} | ${g.word}: ${g.trigger} → ${said}`)
      }
      if (g.rule) {
        e.rules.labelled++
        if (w.rule === g.rule) e.rules.right++
        else e.misses.push(`rule   ${g.sl} | ${g.word}: ${g.rule} → ${said}`)
      }
    } else {
      if (w.sure === 'decided') e.wrongDecided++
      else e.wrongLikely++
      e.misses.push(`${w.sure === 'decided' ? 'WRONG ' : 'likely '} ${g.sl} | ${g.word}: ${g.case}.${g.number} → ${said}`)
    }
  }
  e.ms = performance.now() - t0
  return e
}

/**
 * The case trainer's bank (companion/cases/sl/bank.json, lani.cases/v0, its "which case?" items: a sentence, the word's
 * place among its words, its case by its id and its number, the deciding word's place) as labelled items; none while the
 * trainer isn't merged (the file isn't there).
 */
export function trainerItems(): GoldItem[] | null {
  const path = join(companion, 'cases/sl/bank.json')
  if (!existsSync(path)) return null
  const ids: Record<string, CaseOption['case']> = { imenovalnik: 'nom', rodilnik: 'gen', dajalnik: 'dat', tozilnik: 'acc', mestnik: 'loc', orodnik: 'ins' }
  const bank = JSON.parse(readFileSync(path, 'utf8')) as { items: { kind: string; sl: string; word: number; case: string; number: CaseOption['number']; trigger?: number | null }[] }
  const words = (line: string) => [...line.matchAll(/[\p{L}\p{M}]+|\d+/gu)].map(m => m[0])
  return bank.items.filter(i => i.kind === 'which').map(i => {
    const w = words(i.sl)
    const nth = w.slice(0, i.word).filter(x => x.toLowerCase() === w[i.word]?.toLowerCase()).length
    return {
      sl: i.sl, en: '', from: 'trainer', word: w[i.word] ?? '', ...(nth ? { nth } : {}), case: ids[i.case], number: i.number,
      ...(typeof i.trigger === 'number' && w[i.trigger] ? { trigger: w[i.trigger] } : {}),
    }
  })
}

export const goldItems = (name: 'gold' | 'heldout' | 'heldout2'): GoldItem[] => JSON.parse(readFileSync(join(import.meta.dir, `fixtures/case-lens-${name}.json`), 'utf8')).items

export const summary = (e: Evaluation) =>
  `${e.right}/${e.labelled} right (${((e.right / e.labelled) * 100).toFixed(1)}%), ${e.wrongDecided} wrong and decided, ${e.wrongLikely} wrong as likely, ${e.open} left open; ` +
  `triggers ${e.triggers.right}/${e.triggers.labelled}, rules ${e.rules.right}/${e.rules.labelled}, ambiguities kept open ${e.ambiguous.keptOpen}/${e.ambiguous.labelled}; ${e.ms.toFixed(0)} ms`

if (import.meta.main) {
  const which = (['heldout', 'heldout2', 'trainer'].includes(process.argv[2]) ? process.argv[2] : 'gold') as 'gold' | 'heldout' | 'heldout2' | 'trainer'
  const items = which === 'trainer' ? trainerItems() : goldItems(which)
  if (!items) {
    console.log('trainer: no companion/cases/sl/bank.json (the case trainer not merged)')
    process.exit(0)
  }
  const e = evaluate(repoLens(), items)
  if (process.argv.includes('-v')) for (const m of e.misses) console.log(m)
  console.log(`${which}: ${e.items} items: ${summary(e)}`)
}
