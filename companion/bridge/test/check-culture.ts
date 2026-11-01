// Checks a culture pack as the bridge does at start (validateCulture): every file's shape, its scenes with the packs and
// scenarios its village is served, the people its texts name in its cast, and the grammar pages its scenes' choices name
// in the book of its language: `bun test/check-culture.ts [culture]`. Prints every problem, or "<culture>: ok".
import { mkdtempSync, readdirSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { readManifest, validateCulture } from '../src/cultures'
import { PackStore, packsDirs } from '../src/packs'
import { ScenarioStore } from '../src/scenarios'
import { VillagerStore } from '../src/villagers'

const companion = resolve(import.meta.dir, '../..')
const culturesDir = join(companion, 'cultures')
const id = process.argv[2] ?? 'primorska'
const m = readManifest(culturesDir, id)
const lang = m.ok ? m.manifest.language : 'sl'
// the stores' own directories for what a tutor would add: empty, apart from the repository
const unused = mkdtempSync(join(tmpdir(), 'lani-check-culture-'))
const cast = new VillagerStore(join(culturesDir, id, 'villagers'), join(unused, 'villagers')).all()
const refs = {
  packs: new PackStore(packsDirs(join(companion, 'packs'), culturesDir, id, lang), join(unused, 'packs')).all(),
  scenarios: new ScenarioStore(lang === 'sl' ? join(companion, 'scenarios') : join(companion, 'scenarios', lang), join(unused, 'scenarios'), undefined, lang).all(),
}
const pages = new Set(readdirSync(join(companion, 'grammar', lang)).filter(f => f.endsWith('.json')).map(f => f.slice(0, -5)))
const v = validateCulture(culturesDir, id, cast, refs, pages)
if (v.ok) console.log(`${id}: ok`)
else {
  console.log(v.errors)
  process.exit(1)
}
