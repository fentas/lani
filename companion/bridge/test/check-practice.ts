// Checks the curated practice of one language as a writer goes: its modules and drills validate, and what they name is
// there. bun test/check-practice.ts [language] (default it). The smoke runs the same checks on every language's.
import { readdirSync, readFileSync, existsSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { drillProblems, validateDrill } from '../src/drills'
import { GrammarStore } from '../src/grammar'
import { PackStore, packsDirs } from '../src/packs'
import { validateCuratedModule } from '../src/store'
import { VillagerStore } from '../src/villagers'

const language = process.argv[2] ?? 'it'
const culture = { it: 'friuli', de: 'kaernten', en: 'lakeland', sl: 'primorska' }[language] ?? 'friuli'
const repo = resolve(import.meta.dir, '../..')
const tmp = join(process.env.TMPDIR ?? '/tmp', `check-practice-${process.pid}`)
const grammarPages = new GrammarStore(join(repo, 'grammar', language), language, join(tmp, 'g')).all()
const pages = new Set(grammarPages.map(p => p.id))
const refs = {
  grammar: pages,
  packs: new PackStore(packsDirs(join(repo, 'packs'), join(repo, 'cultures'), culture, language), join(tmp, 'p')).all(),
  villagers: new VillagerStore(join(repo, 'cultures', culture, 'villagers'), join(tmp, 'v')).all(),
}
let bad = 0
const dDir = join(repo, 'drills', language)
for (const f of existsSync(dDir) ? readdirSync(dDir).filter(f => f.endsWith('.json')).sort() : []) {
  const v = validateDrill(JSON.parse(readFileSync(join(dDir, f), 'utf8')))
  if (!v.ok) { bad++; console.log(`✖ drills/${language}/${f}\n${v.errors}`); continue }
  const problems = drillProblems(v.drill, refs)
  if (problems.length) { bad += problems.length; console.log(`✖ drills/${language}/${f}: ${problems.join('; ')}`) }
  console.log(`✓ drills/${language}/${f}`)
}
const mDir = language === 'sl' ? join(repo, 'modules') : join(repo, 'modules', language)
for (const f of existsSync(mDir) ? readdirSync(mDir).filter(f => f.endsWith('.json')).sort() : []) {
  const raw = JSON.parse(readFileSync(join(mDir, f), 'utf8'))
  for (const base of ['en', 'de', 'sl']) {
    const v = validateCuratedModule(raw, base)
    if (!v.ok) { bad++; console.log(`✖ modules/${language}/${f} (${base})\n${v.errors}`); break }
    const named = [...new Set(v.spec.exercises.flatMap(e => (e.grammar ? [e.grammar] : [])))].filter(g => !pages.has(g))
    if (named.length) { bad++; console.log(`✖ modules/${language}/${f}: no grammar page ${named.join(', ')}`) }
    if (v.spec.exercises.some(e => !e.grammar)) { bad++; console.log(`✖ modules/${language}/${f}: an exercise names no grammar page`) }
    if (base === 'en') console.log(`✓ modules/${language}/${f}: ${v.spec.exercises.length} exercises`)
  }
}
console.log(bad ? `${bad} problems` : 'all good')
process.exit(bad ? 1 : 0)
