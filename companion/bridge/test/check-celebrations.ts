// Checks a culture pack's celebrations (celebrations/*.json, lani.celebration/v0) and their word packs as the bridge does
// at start, with the cast, the packs the village is served, the grammar book and the dictionary:
// `bun test/check-celebrations.ts [culture] [age]`. Prints every problem and the counts, or "ok".
// LANI_EXTRA_FORMS: more supplement files (a lexicon extra's format), comma-separated, read as if in the supplement.
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { cultureCelebrations, partyTellings } from '../src/celebrations'
import { readManifest } from '../src/cultures'
import { PackStore, packsDirs, validatePack } from '../src/packs'
import { lexiconLemmas } from '../src/project-steps'

const companion = resolve(import.meta.dir, '../..')
const culturesDir = join(companion, 'cultures')
const id = process.argv[2] ?? 'primorska'
const only = process.argv[3]
const m = readManifest(culturesDir, id)
const lang = m.ok ? m.manifest.language : 'sl'
const problems: string[] = []

const packs = new PackStore(packsDirs(join(companion, 'packs'), culturesDir, id, lang), '/tmp/lani-check-celebrations-unused').all()
const villagersDir = join(culturesDir, id, 'villagers')
const cast = new Set(existsSync(villagersDir) ? readdirSync(villagersDir).filter(f => f.endsWith('.json')).map(f => f.slice(0, -5)) : [])
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
const r = cultureCelebrations(culturesDir, id, lang, { cast, packs, pages, lemmas })
problems.push(...(only ? r.errors.filter(e => e.includes(`/${only}.json`)) : r.errors))
const files = only ? r.files.filter(f => f.age === only) : r.files
// their word packs: valid packs of the culture
for (const f of files) {
  if (!f.pack) continue
  const p = join(culturesDir, id, 'packs', `${f.pack}.json`)
  if (!existsSync(p)) continue // the check above says the pack is missing
  const v = validatePack(JSON.parse(readFileSync(p, 'utf8')))
  if (!v.ok) problems.push(`${id}/packs/${f.pack}.json:\n${v.errors}`)
}
const tellings = files.flatMap(partyTellings)
const lines = tellings.reduce((n, t) => n + t.lines.length, 0)
console.log(problems.length ? problems.join('\n') : 'ok')
console.log(`${files.length} celebrations, ${tellings.length} parties, ${lines} lines`)
process.exit(problems.length ? 1 : 0)
