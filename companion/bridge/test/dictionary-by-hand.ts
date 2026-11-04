// One real lookup by hand, through the table as the bridge has it: bun test/dictionary-by-hand.ts <language> <word>.
// The one way to reach a real site from the tests' side: the smoke and the dev bridge never do. One word, one request per
// source, its answer printed. Its cache is a directory of its own, thrown away after.
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { OutsideDictionaries } from '../src/dictionaries'

const [language, word] = process.argv.slice(2)
if (!language || !word) throw new Error('bun test/dictionary-by-hand.ts <language> <word>')
const dir = mkdtempSync(join(tmpdir(), 'lani-dictionary-'))
try {
  console.log(JSON.stringify(await new OutsideDictionaries({ dir, log: console.error }).look(word, language), null, 2))
} finally {
  rmSync(dir, { recursive: true, force: true })
}
