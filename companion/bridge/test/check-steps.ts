// Checks a culture pack's project steps (project-steps/*.json, lani.project-steps/v0) and its projects' word packs
// (packs/ with a "project") as the bridge does at start, with the packs and scenes the village is served and the
// dictionary: `bun test/check-steps.ts [culture] [project]`. Prints every problem and the counts, or "ok".
// LANI_EXTRA_FORMS: more supplement files (a lexicon extra's format), comma-separated, read as if in the supplement.
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { cultureProjectSteps, lexiconLemmas, stepScenes, stepTellings } from '../src/project-steps'
import { readManifest } from '../src/cultures'
import { PackStore, packsDirs, validatePack } from '../src/packs'

const companion = resolve(import.meta.dir, '../..')
const culturesDir = join(companion, 'cultures')
const id = process.argv[2] ?? 'primorska'
const only = process.argv[3]
const m = readManifest(culturesDir, id)
const lang = m.ok ? m.manifest.language : 'sl'
const problems: string[] = []

// the projects' word packs: valid packs of the culture
const packsDir = join(culturesDir, id, 'packs')
for (const f of existsSync(packsDir) ? readdirSync(packsDir).filter(x => x.endsWith('.json')).sort() : []) {
  const raw = JSON.parse(readFileSync(join(packsDir, f), 'utf8'))
  if (!raw.project) continue
  if (only && raw.project !== only) continue
  const v = validatePack(raw)
  if (!v.ok) problems.push(`${id}/packs/${f}:\n${v.errors}`)
  else if (`${v.pack.id}.json` !== f) problems.push(`${id}/packs/${f}: id "${v.pack.id}", but the file is ${f}`)
}

const packs = new PackStore(packsDirs(join(companion, 'packs'), culturesDir, id, lang), '/tmp/lani-check-steps-unused').all()
const projects = JSON.parse(readFileSync(join(culturesDir, id, 'projects.json'), 'utf8')).projects.map((p: any) => ({ id: p.id, leader: p.leader, helpers: p.helpers ?? [], steps: p.steps.length }))
const book = join(companion, 'grammar', lang)
const pages = existsSync(book) ? new Set(readdirSync(book).filter(f => f.endsWith('.json')).map(f => f.slice(0, -5))) : undefined
// the dictionary, and forms still to go into the supplement
const base = lexiconLemmas(join(companion, 'lexicon'), lang)
const extra = new Map<string, Set<string>>()
for (const file of (process.env.LANI_EXTRA_FORMS ?? '').split(',').filter(Boolean)) {
  const e = JSON.parse(readFileSync(file, 'utf8')) as { entries: { lemma: string; forms?: Record<string, string> }[] }
  for (const x of e.entries) for (const f of [x.lemma, ...Object.keys(x.forms ?? {})]) {
    const set = extra.get(f.toLowerCase()) ?? new Set<string>()
    set.add(x.lemma.toLowerCase())
    extra.set(f.toLowerCase(), set)
  }
}
const lemmas = (w: string) => [...base(w), ...(extra.get(w.toLowerCase()) ?? [])]
const r = cultureProjectSteps(culturesDir, id, lang, projects, { packs, pages, scenes: stepScenes(culturesDir, id, lang), lemmas })
problems.push(...(only ? r.errors.filter(e => e.includes(`/${only}.json`)) : r.errors))
const files = only ? r.files.filter(f => f.project === only) : r.files
const tellings = files.flatMap(stepTellings)
const lines = tellings.reduce((n, t) => n + t.lines.length, 0)
const steps = files.reduce((n, f) => n + f.steps.filter(Boolean).length, 0)
console.log(problems.length ? problems.join('\n') : 'ok')
console.log(`${files.length} files, ${steps} steps, ${tellings.length} dialogs, ${lines} lines`)
process.exit(problems.length ? 1 : 0)
