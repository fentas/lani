// Slovene's case system, digested (docs/plans/05-built/case-de.md, "Slovene unchanged"): the lens over every content
// line and the course units' lines, the trainer's bank as GET /cases would serve it, and the trigger table, each as a
// SHA-256 of its JSON, so a change elsewhere (German's case set, its table, its lens) can be shown to leave Slovene as
// it was. `bun test/case-digest.ts [--write <file>] [--check <file>]`.
import { createHash } from 'node:crypto'
import { readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { validateBank } from '../src/case-bank'
import { validateCaseTriggers } from '../src/case-triggers'
import { repoLens, VILLAGE_NAMES } from './case-lens-eval'
import { lensLines } from './case-lens-gaps'

const companion = resolve(import.meta.dir, '../..')
const sha = (x: unknown) => createHash('sha256').update(JSON.stringify(x)).digest('hex')
const lens = repoLens()
const lines = lensLines(true)
const analysed = lines.map(l => lens.analyze(l.text, VILLAGE_NAMES))
const taps = lines.slice(0, 600).flatMap(l => [...l.text.matchAll(/[\p{L}\p{M}]+/gu)].slice(0, 4).map(m => lens.at(l.text, m[0], 0, VILLAGE_NAMES)))
const bank = validateBank(JSON.parse(readFileSync(join(companion, 'cases/sl/bank.json'), 'utf8')))
const table = validateCaseTriggers(JSON.parse(readFileSync(join(companion, 'grammar/sl/data/case-triggers.json'), 'utf8')))
const out = {
  lines: lines.length,
  lens: sha(analysed),
  taps: sha(taps),
  bank: bank.ok ? sha(bank.bank) : `invalid: ${bank.errors.slice(0, 200)}`,
  table: table.ok ? sha(table.table) : `invalid: ${table.errors.slice(0, 200)}`,
}
const arg = (k: string) => { const i = process.argv.indexOf(k); return i > 0 ? process.argv[i + 1] : undefined }
const write = arg('--write')
const check = arg('--check')
if (write) writeFileSync(write, JSON.stringify(out, null, 2) + '\n')
if (check) {
  const want = JSON.parse(readFileSync(check, 'utf8'))
  const diff = Object.keys(want).filter(k => JSON.stringify(want[k]) !== JSON.stringify((out as any)[k]))
  console.log(diff.length ? `✖ Slovene changed: ${diff.join(', ')}` : '✓ Slovene unchanged')
  process.exit(diff.length ? 1 : 0)
}
console.log(JSON.stringify(out, null, 2))
