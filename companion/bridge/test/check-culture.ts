// Checks a culture pack as the bridge does at start (validateCulture): every file's shape, its scenes with the packs and
// scenarios its village is served, the people its texts name in its cast, and the grammar pages its scenes' choices name
// in the book of its language: `bun test/check-culture.ts [culture]`. Prints every problem, or "<culture>: ok".
import { existsSync, mkdtempSync, readdirSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { readManifest, validateCulture } from '../src/cultures'
import { PackStore, packsDirs } from '../src/packs'
import { ScenarioStore } from '../src/scenarios'
import { levelPageErrors } from '../src/scenes'
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
// a levelled dialog names pages up to one level above its own (companion/SCENES.md, "Dialogs by level")
const pageLevel = (g: string): string | undefined => {
  try {
    return JSON.parse(readFileSync(join(companion, 'grammar', lang, `${g}.json`), 'utf8')).level
  } catch {
    return undefined
  }
}
const scenesDir = join(culturesDir, id, 'scenes')
const levels = existsSync(scenesDir)
  ? readdirSync(scenesDir).filter(f => f.endsWith('.json')).sort().flatMap(f => {
      const s = JSON.parse(readFileSync(join(scenesDir, f), 'utf8'))
      return levelPageErrors({ happenings: s.happenings ?? [], dialogs: s.dialogs ?? [], variants: s.variants ?? [] }, pageLevel).map(e => `${id}/scenes/${f}: ${e}`)
    })
  : []
if (v.ok && !levels.length) console.log(`${id}: ok`)
else {
  console.log([v.ok ? '' : v.errors, ...levels].filter(Boolean).join('\n'))
  process.exit(1)
}
