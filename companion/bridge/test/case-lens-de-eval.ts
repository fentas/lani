// German's case lens (case-lens-de.ts; docs/plans/05-built/case-de.md) against its gold: the trainer's German bank (its
// "Welcher Fall?" items, written apart from the lens) and the held-out lines of the content (test/fixtures/
// case-lens-de-heldout.json, labelled by hand before the lens ran on them); and over every German content line, how many
// of its words it decides, how many it leaves likely or open, and how fast. The smoke holds it to its numbers
// (test/smoke/case-de.ts). `bun test/case-lens-de-eval.ts [bank|heldout|content] [-v]` (all three by default; -v prints
// each miss, or for content a sample of lines).
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { addressee, renderDeep } from '../src/addressee'
import { CaseLensDe } from '../src/case-lens-de'
import { contentLines } from '../src/forms'
import { caseCheck } from './check-cases'

const companion = resolve(import.meta.dir, '../..')
const NAMES = [
  ...['Kathi', 'Mitzi', 'Resi', 'Lena', 'Eva'].map(name => ({ name, gender: 'f' as const })),
  ...['Stefan', 'Franz', 'Sepp', 'Toni', 'Jakob', 'Florian', 'Maxi', 'Stanko', 'Ana'].map(name => ({ name, gender: 'm' as const })),
]
const SLOT: Record<string, string> = { nominativ: 'nom', akkusativ: 'acc', dativ: 'dat', genitiv: 'gen' }
const bare = (t: string) => t.replace(/^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu, '')
const verbose = process.argv.includes('-v')
const which = process.argv.slice(2).filter(a => !a.startsWith('-'))
const ctx = caseCheck('de', companion)
const lens = new CaseLensDe({ table: ctx.de!.forms.table, dict: ctx.de!.dict, nouns: ctx.de!.nouns })

function score(name: string, items: { de: string; word: string; nth: number; want: string }[]) {
  let right = 0
  let open = 0
  const wrong: string[] = []
  for (const it of items) {
    const c = lens.at(it.de, it.word, it.nth, NAMES)
    const p = c && 'pick' in c ? c.pick : null
    const sure = c && 'sure' in c ? c.sure : 'ambiguous'
    if (!p || sure === 'ambiguous') open++
    else if (`${p.case}.${p.number}` === it.want) right++
    else wrong.push(`${sure === 'decided' ? 'DECIDED' : 'likely '} ${it.word} ${p.case}.${p.number}, not ${it.want}: ${it.de}`)
  }
  const decided = wrong.filter(w => w.startsWith('DECIDED')).length
  console.log(`${name}: ${items.length} words: ${right} right, ${open} open, ${decided} wrong and decided, ${wrong.length - decided} wrong as likely`)
  if (verbose) for (const w of wrong) console.log(`  ${w}`)
}

if (!which.length || which.includes('bank')) {
  const bank = JSON.parse(readFileSync(join(companion, 'cases/de/bank.json'), 'utf8'))
  score('bank', bank.items.filter((i: any) => i.kind === 'which').map((i: any) => {
    const toks = i.de.trim().split(/\s+/)
    const word = bare(toks[i.word])
    return { de: i.de, word, nth: toks.slice(0, i.word).filter((t: string) => bare(t).toLowerCase() === word.toLowerCase()).length, want: `${SLOT[i.case]}.${i.number}` }
  }))
}
if (!which.length || which.includes('heldout')) {
  const held = JSON.parse(readFileSync(join(companion, 'bridge/test/fixtures/case-lens-de-heldout.json'), 'utf8'))
  score('heldout', held.items.map((i: any) => ({ de: i.de, word: i.word, nth: i.nth ?? 0, want: `${i.case}.${i.number}` })))
}
if (!which.length || which.includes('content')) {
  const js = (d: string) => (existsSync(d) ? readdirSync(d).filter(f => f.endsWith('.json')).sort().map(f => JSON.parse(readFileSync(join(d, f), 'utf8'))) : [])
  const c = join(companion, 'cultures/kaernten')
  const lines = contentLines(renderDeep({ language: 'de', scenes: js(join(c, 'scenes')), stories: js(join(c, 'stories')), readings: js(join(c, 'readings')), grammar: js(join(companion, 'grammar/de')), packs: [...js(join(companion, 'packs/de')), ...js(join(c, 'packs'))] }, addressee({ name: 'Ana', gender: 'female' })))
  let words = 0
  const by = { decided: 0, likely: 0, ambiguous: 0 }
  const t0 = performance.now()
  for (const [n, l] of lines.entries()) {
    const r = lens.analyze(l.text, NAMES)
    for (const w of r.words) {
      words++
      by[w.sure]++
    }
    if (verbose && n % 97 === 0 && r.words.length) console.log(`  ${l.text}\n    ${r.words.map(w => `${w.word}:${w.pick ? w.pick.case : '?'}${w.sure === 'decided' ? '' : w.sure === 'likely' ? '~' : '?'}`).join(' ')}`)
  }
  const ms = (performance.now() - t0) / lines.length
  console.log(`content: ${lines.length} lines, ${words} words with a case: ${by.decided} decided, ${by.likely} likely, ${by.ambiguous} open; ${ms.toFixed(2)} ms a line`)
}
