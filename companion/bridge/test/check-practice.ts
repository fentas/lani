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
/** The bases every drill text is in besides the language's own: English always (the smoke's practice.ts has the same). */
const drillBases = { it: ['en', 'de'], de: ['en', 'sl', 'it'], en: ['en', 'sl', 'de', 'it'], sl: ['en', 'de'] }[language] ?? ['en', 'de']
/** The bases a curated module's texts are in: the language's learners' (the smoke's practice.ts has the same). */
const bases = { it: ['en', 'de', 'sl'], de: ['en', 'sl', 'it'], en: ['en', 'sl', 'de', 'it'], sl: ['en', 'de', 'it'] }[language] ?? ['en', 'de', 'sl']
const repo = resolve(import.meta.dir, '../..')
const tmp = join(process.env.TMPDIR ?? '/tmp', `check-practice-${process.pid}`)
const grammarPages = new GrammarStore(join(repo, 'grammar', language), language, join(tmp, 'g')).all()
const pages = new Set(grammarPages.map(p => p.id))
const refs = {
  grammar: pages,
  packs: new PackStore(packsDirs(join(repo, 'packs'), join(repo, 'cultures'), culture, language), join(tmp, 'p')).all(),
  villagers: new VillagerStore(join(repo, 'cultures', culture, 'villagers'), join(tmp, 'v')).all(),
}
/** Where a module's text the learner reads is one string, or lacks a base (the smoke's practice.ts checks the same). */
const untexted = (v: unknown, at: string, out: string[]): string[] => {
  if (Array.isArray(v)) v.forEach((x, i) => untexted(x, `${at}.${i}`, out))
  else if (v && typeof v === 'object')
    for (const [k, x] of Object.entries(v)) {
      if (['explain', 'instruction', 'description', 'story', 'title', 'hint', 'prompt'].includes(k) && (typeof x === 'string' ? k !== 'prompt' : bases.some(b => !(x as any)?.[b]))) out.push(`${at}.${k}`)
      else untexted(x, `${at}.${k}`, out)
    }
  return out
}
let bad = 0
const dDir = join(repo, 'drills', language)
for (const f of existsSync(dDir) ? readdirSync(dDir).filter(f => f.endsWith('.json')).sort() : []) {
  const v = validateDrill(JSON.parse(readFileSync(join(dDir, f), 'utf8')))
  if (!v.ok) { bad++; console.log(`✖ drills/${language}/${f}\n${v.errors}`); continue }
  const lacks: string[] = []
  const need = (t: Record<string, string> | undefined, at: string, said: boolean) => {
    for (const b of [...(said ? [language] : []), ...drillBases]) if (!t?.[b]) lacks.push(`${at} (${b})`)
  }
  const d = v.drill
  need(d.title, 'title', false)
  if (d.kind === 'transform') d.sets.forEach(st => (need(st.do, `${st.id}.do`, false), st.items.forEach((it, i) => (need(it.from, `${st.id}.${i}.from`, true), need(it.to, `${st.id}.${i}.to`, true)))))
  else if (d.kind === 'rapid') d.sets.forEach(st => (need(st.title, `${st.id}.title`, false), st.items.forEach((it, i) => need(it, `${st.id}.${i}`, true))))
  else if (d.kind === 'build') d.builds.forEach(b => b.steps.forEach((x, i) => (need(x as Record<string, string>, `${b.id}.${i}`, true), i > 0 && need(x.add, `${b.id}.${i}.add`, false))))
  else d.riddles.forEach(r => [...r.clues, r.ask, r.answer].forEach((x, i) => need(x, `${r.id}.${i}`, true)))
  if (lacks.length) { bad++; console.log(`✖ drills/${language}/${f}: not in ${[language, ...drillBases].join(', ')}: ${lacks.slice(0, 8).join(', ')}${lacks.length > 8 ? ` and ${lacks.length - 8} more` : ''}`) }
  const problems = drillProblems(v.drill, refs)
  if (problems.length) { bad += problems.length; console.log(`✖ drills/${language}/${f}: ${problems.join('; ')}`) }
  console.log(`✓ drills/${language}/${f}`)
}
const mDir = language === 'sl' ? join(repo, 'modules') : join(repo, 'modules', language)
for (const f of existsSync(mDir) ? readdirSync(mDir).filter(f => f.endsWith('.json')).sort() : []) {
  const raw = JSON.parse(readFileSync(join(mDir, f), 'utf8'))
  for (const base of bases) {
    const v = validateCuratedModule(raw, base)
    if (!v.ok) { bad++; console.log(`✖ modules/${language}/${f} (${base})\n${v.errors}`); break }
    const named = [...new Set(v.spec.exercises.flatMap(e => (e.grammar ? [e.grammar] : [])))].filter(g => !pages.has(g))
    if (named.length) { bad++; console.log(`✖ modules/${language}/${f}: no grammar page ${named.join(', ')}`) }
    if (v.spec.exercises.some(e => !e.grammar)) { bad++; console.log(`✖ modules/${language}/${f}: an exercise names no grammar page`) }
    if (base === 'en') {
      // Slovene's modules are written for Jan, in plain English strings
      const lacking = language === 'sl' ? [] : untexted(raw, f, [])
      if (lacking.length) { bad++; console.log(`✖ modules/${language}/${f}: not in each base (${bases.join(', ')}): ${lacking.slice(0, 6).join(', ')}`) }
      console.log(`✓ modules/${language}/${f}: ${v.spec.exercises.length} exercises`)
    }
  }
}
console.log(bad ? `${bad} problems` : 'all good')
process.exit(bad ? 1 : 0)
