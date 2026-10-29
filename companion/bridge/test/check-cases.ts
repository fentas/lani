// Checks the case trainer's bank (companion/cases/<language>/bank.json, lani.cases/v0) as the smoke does: its format,
// every word's form against the dictionary and the forms book where they have the word, every trigger against its rule,
// every page against the grammar book, every scene against the village's: `bun test/check-cases.ts [language]`. Prints
// every problem and the bank's counts, or "ok" and the counts.
import { existsSync, mkdtempSync, readdirSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { bankCounts, validateBank, type CaseCheck } from '../src/case-bank'
import { FormsBook, LineIndex } from '../src/forms'
import { validateGrammar } from '../src/grammar'
import { Lexicon } from '../src/lexicon'

const companion = resolve(import.meta.dir, '../..')
const language = process.argv[2] ?? 'sl'

/** The check's context for [language], from the repository: the dictionary, the forms book, the grammar book, the scenes. */
export function caseCheck(language: string, root = companion): CaseCheck {
  const lexiconDir = join(root, 'lexicon')
  const dict = new Lexicon({ dir: lexiconDir, language, tutorFile: join(mkdtempSync(join(tmpdir(), 'lani-check-cases-')), 'lexicon.json') })
  const book = join(root, 'grammar', language)
  const pages = new Map<string, string>()
  for (const f of existsSync(book) ? readdirSync(book).filter(f => f.endsWith('.json')) : []) {
    const v = validateGrammar(JSON.parse(readFileSync(join(book, f), 'utf8')))
    if (v.ok) pages.set(v.page.id, v.page.level)
  }
  const scenesDir = join(root, 'scenes')
  const scenes = new Set(existsSync(scenesDir) ? readdirSync(scenesDir).filter(f => f.endsWith('.json')).map(f => f.slice(0, -5)) : [])
  return { forms: { dict, book: new FormsBook({ dir: lexiconDir, language }), pages: new Set(pages.keys()), lines: new LineIndex([]), language }, pages, scenes }
}

if (import.meta.main) {
  const path = join(companion, 'cases', language, 'bank.json')
  const v = validateBank(JSON.parse(readFileSync(path, 'utf8')), caseCheck(language))
  if (!v.ok) {
    console.log(v.errors)
    console.log(`\n${v.errors.split('\n').length} problem(s)`)
    process.exit(1)
  }
  console.log(JSON.stringify(bankCounts(v.bank.items), null, 1))
  console.log(`rules ${Object.keys(v.bank.rules).length}, frames ${v.bank.frames.length}, adjectives ${v.bank.adjectives.length}, nouns ${v.bank.nouns.length}`)
  console.log(`judged by the dictionary: ${v.judged}; not in it: ${v.unjudged.join(', ') || 'none'}`)
  console.log('ok')
}
