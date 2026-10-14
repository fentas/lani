// The validators' conformance corpus (docs/plans/05-phase-0.md, check 4; plan 5, "One source of truth"): every curated
// content file of the repository, the specs' examples, and broken variants of each kind, each with the bridge's verdict
// and messages as bun gives them from the bridge's own source (src/validate-entry.ts, the real file system). An engine in
// the app runs the same cases through the bundle (validators/build.ts) and must answer the same, file by file.
//
// Writes test/conformance/out/ (not in git):
//   refs/<name>.json    the references a village checks against (its packs, scenarios, cast, projects, grammar pages),
//                       as the bridge loads them for a learner of that culture; the engine keeps them (lani.setRefs)
//   mounts/<name>.json  files the validators read themselves ({root, files: {relative path: text}}): a culture pack, the
//                       dictionary, a broken copy of a pack (a variant: {root, base, files, remove}); lani.mount
//   cases.jsonl         {id, kind, source, mutation?, refs, mounts?, input, expected}: input the file's text, refs the JSON
//                       text the call passes, expected the bridge's answer (src/validate-entry.ts Verdict, with digest)
//   summary.json        the counts per kind and verdict
//
// Run: bun test/conformance/build-corpus.ts   (from companion/bridge)
import { cpSync, existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, relative, resolve } from 'node:path'
import { cultureProjects } from '../../src/cultures'
import { lemmaFinder, testedWords } from '../../src/dialog-words'
import { FormsBook } from '../../src/forms'
import { GrammarStore, grammarDir } from '../../src/grammar'
import { Lexicon } from '../../src/lexicon'
import { PackStore, packsDirs, wordOf } from '../../src/packs'
import { ScenarioStore } from '../../src/scenarios'
import { resolveScene, validateScene, type Scene } from '../../src/scenes'
import { VillagerStore } from '../../src/villagers'
import { check, setRefs, type Verdict } from '../../src/validate-entry-lexicon'

const companion = resolve(import.meta.dir, '../../..')
const repo = resolve(companion, '..')
const out = join(import.meta.dir, 'out')
const culturesDir = join(companion, 'cultures')
const lexiconDir = join(companion, 'lexicon')
for (const d of ['refs', 'mounts']) mkdirSync(join(out, d), { recursive: true })
const scratch = mkdtempSync(join(tmpdir(), 'lani-conformance-'))

const read = (p: string) => readFileSync(p, 'utf8')
const rel = (p: string) => relative(repo, p)
const jsonFiles = (dir: string): string[] => (existsSync(dir) ? readdirSync(dir).filter(f => f.endsWith('.json')).sort().map(f => join(dir, f)) : [])
/** Every file under [dir], relative to it. */
function walk(dir: string, at = ''): string[] {
  return readdirSync(join(dir, at)).sort().flatMap(f => (statSync(join(dir, at, f)).isDirectory() ? walk(dir, join(at, f)) : [join(at, f)]))
}

// --- the references, as a bridge of each culture loads them (bridge.ts) ------------------------------------------------

const CULTURES = readdirSync(culturesDir).filter(c => existsSync(join(culturesDir, c, 'culture.json'))).sort()
const languageOf: Record<string, string> = {}
const villagerIds: Record<string, string[]> = {}
const refSets: Record<string, Record<string, unknown>> = {}
for (const c of CULTURES) {
  const L = JSON.parse(read(join(culturesDir, c, 'culture.json'))).language as string
  languageOf[c] = L
  const tmp = mkdtempSync(join(scratch, `${c}-`))
  const packs = new PackStore(packsDirs(join(companion, 'packs'), culturesDir, c, L), join(tmp, 'packs')).all()
  const scenarios = new ScenarioStore(L === 'sl' ? join(companion, 'scenarios') : join(companion, 'scenarios', L), join(tmp, 'scenarios')).all()
  const villagers = new VillagerStore(join(culturesDir, c, 'villagers'), join(tmp, 'villagers')).all()
  const projects = cultureProjects(culturesDir, c)
  const pages = new GrammarStore(grammarDir(join(companion, 'grammar'), L), L, join(tmp, 'grammar')).all().map(p => p.id)
  villagerIds[c] = villagers.map(v => v.id)
  refSets[c] = { language: L, packs, scenarios, villagers, projects, pages }
}
// the learner's cards for the woven words: as if every word of the Slovene village's packs were learned
const slPacks = (refSets.primorska.packs as { id: string; language?: 'sl'; words: { id: string; sl?: string; en: string }[] }[])
const cards = Object.fromEntries(
  slPacks.flatMap(p => p.words.map(w => [`vocab_${p.id}_${w.id}`, { type: 'vocabulary', content: wordOf({ language: p.language ?? 'sl' }, w as never), answer: w.en, category: p.id }])),
)
refSets['primorska-woven'] = { ...refSets.primorska, cards, lexicon: { dir: lexiconDir, language: 'sl' } }
for (const [name, r] of Object.entries(refSets)) {
  const text = JSON.stringify(r)
  writeFileSync(join(out, 'refs', `${name}.json`), text)
  setRefs(name, text)
}

// --- the files the validators read themselves ------------------------------------------------------------------------

type Mount = { root: string; files: Record<string, string>; base?: string; remove?: string[] }
const mounts: Record<string, Mount> = {}
/**
 * What a culture pack's check reads, relative to companion/: the pack, and beside it (project-steps.ts: the steps'
 * scenes and words) the curated scenes of its language and its language's dictionary, when there are.
 */
function cultureReads(c: string): string[] {
  const L = languageOf[c]
  const scenes = L === 'sl' ? 'scenes' : join('scenes', L)
  return [
    ...walk(join(culturesDir, c)).map(f => join('cultures', c, f)),
    ...jsonFiles(join(companion, scenes)).map(f => relative(companion, f)),
    ...[`${L}.json`, `${L}.extra.json`].filter(f => existsSync(join(lexiconDir, f))).map(f => join('lexicon', f)),
  ]
}
for (const c of CULTURES) mounts[`culture-${c}`] = { root: companion, files: Object.fromEntries(cultureReads(c).map(f => [f, read(join(companion, f))])) }
mounts['lexicon-sl'] = { root: lexiconDir, files: Object.fromEntries(['sl.json', 'sl.extra.json', 'sl.forms.json'].map(f => [f, read(join(lexiconDir, f))])) }

// --- the cases -------------------------------------------------------------------------------------------------------

type Case = { id: string; kind: string; source: string; mutation?: string; refs: string | null; mounts?: string[]; input: string; expected?: Verdict }
const cases: Case[] = []
const ids = new Set<string>()
function add(c: Omit<Case, 'id' | 'expected'> & { id?: string }) {
  const id = c.id ?? `${c.kind}:${c.source}${c.mutation ? `#${c.mutation}` : ''}`
  if (ids.has(id)) throw new Error(`case ${id} twice`)
  ids.add(id)
  cases.push({ ...c, id, expected: check(c.kind, c.input, c.refs, '{"digest":true}') })
}
const use = (name: string, more: Record<string, unknown> = {}) => JSON.stringify({ use: name, ...more })

/** A curated file as the bridge reads it: its text. */
const curated: { kind: string; path: string; refs: string | null }[] = []
const kindOf = (kind: string, paths: string[], refs: string | null = null) => paths.forEach(path => curated.push({ kind, path, refs }))
kindOf('module', jsonFiles(join(companion, 'modules')))
kindOf('pack', [
  ...jsonFiles(join(companion, 'packs')),
  ...['de', 'en', 'it'].flatMap(l => jsonFiles(join(companion, 'packs', l))),
  ...CULTURES.flatMap(c => jsonFiles(join(culturesDir, c, 'packs'))),
])
kindOf('scene', jsonFiles(join(companion, 'scenes')), use('primorska'))
for (const c of CULTURES) kindOf('scene', jsonFiles(join(culturesDir, c, 'scenes')), use(c))
for (const c of CULTURES) kindOf('story', jsonFiles(join(culturesDir, c, 'stories')), use(c))
for (const c of CULTURES) kindOf('villager', jsonFiles(join(culturesDir, c, 'villagers')))
for (const l of readdirSync(join(companion, 'grammar')).sort()) kindOf('grammar', jsonFiles(join(companion, 'grammar', l)))
kindOf('drill', jsonFiles(join(companion, 'drills/sl')), use('primorska'))
kindOf('course', [join(companion, 'course/sl/course.json')])
kindOf('unit', jsonFiles(join(companion, 'course/sl/units')))
kindOf('card', jsonFiles(join(companion, 'cards/sl')))
kindOf('scenario', jsonFiles(join(companion, 'scenarios')))
kindOf('forms', [join(lexiconDir, 'sl.forms.json')])
kindOf('extra', [join(lexiconDir, 'sl.extra.json')])
for (const { kind, path, refs } of curated) add({ kind, source: rel(path), refs, input: read(path) })

// the specs' examples: each ```json block that names a schema the validators have
const SCHEMA_KIND: Record<string, string> = {
  'lani.module/v0': 'module', 'lani.scene/v0': 'scene', 'lani.story/v0': 'story', 'lani.villager/v0': 'villager', 'lani.arrival/v0': 'arrival',
  'lani.grammar/v0': 'grammar', 'lani.course/v0': 'course', 'lani.unit/v0': 'unit', 'lani.worksheet/v0': 'worksheet', 'lani.scenario/v0': 'scenario',
}
const examples: { kind: string; source: string; text: string; refs: string | null }[] = []
for (const doc of ['.claude/skills/lani-studio/reference/module-spec.md', '.claude/skills/lani-studio/reference/grammar-spec.md', 'companion/COURSE.md', 'companion/VILLAGERS.md', 'companion/SCENES.md']) {
  ;[...read(join(repo, doc)).matchAll(/```json\n([\s\S]*?)\n```/g)].forEach((m, i) => {
    const schema = /"schema":\s*"([^"]+)"/.exec(m[1])?.[1]
    const kind = schema && SCHEMA_KIND[schema]
    if (!kind) return
    const refs = kind === 'scene' || kind === 'story' ? use('primorska') : kind === 'arrival' ? JSON.stringify({ language: 'sl', people: villagerIds.primorska }) : null
    examples.push({ kind, source: `${doc}[${i}]`, text: m[1], refs })
  })
}
for (const e of examples) add({ kind: e.kind, source: e.source, refs: e.refs, input: e.text })

// every culture pack, checked whole as the bridge checks its village's at start
for (const c of CULTURES) add({ kind: 'culture', source: rel(join(culturesDir, c)), refs: use(c, { culturesDir, cultureId: c }), mounts: [`culture-${c}`], input: 'null' })

// the learner's placeholders: every curated file's texts that hold one ({learner}, {m:…|f:…}), as one list a file
const placeholders = (v: unknown, acc: string[] = []): string[] => {
  if (typeof v === 'string') { if (/\{(?:learner|[mf]:)/.test(v)) acc.push(v) } else if (Array.isArray(v)) v.forEach(x => placeholders(x, acc))
  else if (v && typeof v === 'object') Object.values(v).forEach(x => placeholders(x, acc))
  return acc
}
const allContent = [...new Set([...curated.map(c => c.path), ...CULTURES.flatMap(c => walk(join(culturesDir, c)).filter(f => f.endsWith('.json')).map(f => join(culturesDir, c, f)))])].sort()
for (const p of allContent) {
  const texts = placeholders(JSON.parse(read(p)))
  if (texts.length) add({ kind: 'learner', source: rel(p), refs: null, input: JSON.stringify(texts) })
}
const BAD_PLACEHOLDERS = [
  'Dober dan, {learner:xyz}!', 'Si {m:lačen|m:lačna}?', 'Si {f:lačna}?', 'Hvala, {learner', '{m:prišel|f:{learner:gen}a}', '{m:a|f:b|f:c}', '{learner:Gen}',
  '{m:Dragi {learner}|f:Draga {learner}}', '{m:spoznala|f:{g, select, f {spoznali} other {spoznala}}}', '{learner:poss}a hiša', 'Pri {learner:loc} doma.', '{f:prišla|m:prišel}',
  '{m:prišel|f:}', '{m:|f:}', '{x:prišel|f:prišla}', '{ learner }', '{learner}{learner:ins}', '{m:{m:a|f:b}|f:c}',
]
BAD_PLACEHOLDERS.forEach((t, i) => add({ kind: 'learner', source: 'placeholders', mutation: `${i}`, refs: null, input: JSON.stringify(t) }))

// --- broken variants ---------------------------------------------------------------------------------------------------

type J = any
const clone = (v: J): J => JSON.parse(JSON.stringify(v))
/** The path to the first string (depth first) worth writing into: a text, not an id, a code or a key word. */
function firstText(v: J, at: (string | number)[] = []): (string | number)[] | undefined {
  if (typeof v === 'string') return v.length > 3 && !/^[a-z0-9_:.\/-]+$/.test(v) && at.length > 1 ? at : undefined
  if (Array.isArray(v)) { for (const [i, x] of v.entries()) { const p = firstText(x, [...at, i]); if (p) return p } return undefined }
  if (v && typeof v === 'object') for (const [k, x] of Object.entries(v)) { if (k === 'schema') continue; const p = firstText(x, [...at, k]); if (p) return p }
  return undefined
}
const getAt = (v: J, p: (string | number)[]) => p.reduce((o, k) => o?.[k], v)
const setAt = (v: J, p: (string | number)[], x: unknown) => { getAt(v, p.slice(0, -1))[p[p.length - 1]] = x }
/** The first key of [v], depth first, called [key] (its path). */
function findKey(v: J, key: string, at: (string | number)[] = []): (string | number)[] | undefined {
  if (Array.isArray(v)) { for (const [i, x] of v.entries()) { const p = findKey(x, key, [...at, i]); if (p) return p } return undefined }
  if (v && typeof v === 'object') for (const [k, x] of Object.entries(v)) { if (k === key) return [...at, k]; const p = findKey(x, key, [...at, k]); if (p) return p }
  return undefined
}

type Mutation = [name: string, f: (v: J) => J | undefined]
const generic = (sample: J): Mutation[] => [
  // a required field dropped: each top-level key in turn
  ...Object.keys(sample).map((k): Mutation => [`drop:${k}`, v => { delete v[k]; return v }]),
  ['type:id', v => ('id' in v ? ((v.id = 42), v) : undefined)],
  ['form:id', v => ('id' in v ? ((v.id = 'Not An Id!'), v) : undefined)],
  ['form:level', v => ('level' in v ? ((v.level = 'Z9'), v) : undefined)],
  ['form:schema', v => ((v.schema = 'lani.nothing/v9'), v)],
  ['type:array', v => { const k = Object.keys(v).find(k => Array.isArray(v[k])); return k ? ((v[k] = 'oops'), v) : undefined }],
  ['type:text', v => { const p = firstText(v); return p ? (setAt(v, p, 7), v) : undefined }],
  ['empty:text', v => { const p = firstText(v); return p ? (setAt(v, p, '   '), v) : undefined }],
  ['extra-key', v => ((v.colour = 'red'), v)],
  ['placeholder:case', v => { const p = firstText(v); return p ? (setAt(v, p, `${getAt(v, p)} {learner:xyz}`), v) : undefined }],
  ['placeholder:pair', v => { const p = firstText(v); return p ? (setAt(v, p, `${getAt(v, p)} {m:sam|m:sama}`), v) : undefined }],
  ['placeholder:half', v => { const p = firstText(v); return p ? (setAt(v, p, `${getAt(v, p)} {f:sama}`), v) : undefined }],
  ['placeholder:ok', v => { const p = firstText(v); return p ? (setAt(v, p, `${getAt(v, p)} {m:Dragi|f:Draga} {learner}`), v) : undefined }],
  ['ref:who', v => { const p = findKey(v, 'who'); return p ? (setAt(v, p, 'nobody'), v) : undefined }],
]
/** What each kind names that must exist, made not to. */
const specific: Record<string, Mutation[]> = {
  module: [
    ['ref:helps-self', v => ((v.quest = { giver: 'Babica Micka', story: 'Pomagaj mi.', helps: v.id }), v)],
    ['form:cloze-gap', v => { const e = v.exercises?.find((x: J) => x.type === 'cloze'); return e ? ((e.text = e.text.replace(/_+/g, '')), v) : undefined }],
    ['form:answer-range', v => { const e = v.exercises?.find((x: J) => x.type === 'choice'); return e ? ((e.answer = e.options.length), v) : undefined }],
  ],
  pack: [
    ['form:words-few', v => ((v.words = v.words.slice(0, 3)), v)],
    ['form:word-twice', v => ((v.words = [...v.words.slice(0, -1), v.words[0]]), v)],
    ['drop:word-language', v => { delete v.words[0][v.language ?? 'sl']; return v }],
  ],
  scene: [
    ['ref:object-word', v => (v.objects?.length ? ((v.objects[0].word = 'no-such-word'), v) : undefined)],
    ['ref:object-pack', v => (v.objects?.length ? ((v.objects[0].pack = 'no-such-pack'), v) : undefined)],
    ['ref:object-slot', v => (v.objects?.length ? ((v.objects[0].slot = 'nowhere'), v) : undefined)],
    ['ref:person-spot', v => (v.people?.length ? ((v.people[0].slot = 'nowhere'), v) : undefined)],
    ['ref:villager', v => (v.people?.length ? ((v.people[0].villager = 'no-villager'), v) : undefined)],
    ['ref:happening-who', v => (v.happenings?.length ? ((v.happenings[0].who = 'nobody'), v) : undefined)],
    ['ref:happening-dialog', v => (v.happenings?.length ? ((v.happenings[0].dialog = 'no-dialog'), delete v.happenings[0].dialogs, v) : undefined)],
    ['ref:talk', v => (v.dialogs?.length ? ((v.dialogs[0].talk = 'no-scenario'), v) : undefined)],
    ['ref:grammar-id', v => { const l = v.dialogs?.[0]?.lines?.find((x: J) => x.choices?.length); return l ? ((l.grammar = 'Not A Page'), v) : undefined }],
    ['ref:effect', v => (v.dialogs?.length ? ((v.dialogs[0].lines[0].fx = { 'no-effect': 1 }), v) : undefined)],
    ['ref:act', v => (v.dialogs?.length ? ((v.dialogs[0].lines[0].act = { nobody: { to: 'nowhere' } }), v) : undefined)],
    ['form:no-ok-choice', v => { const l = v.dialogs?.[0]?.lines?.find((x: J) => x.choices?.length); return l ? (l.choices.forEach((c: J) => (c.ok = false)), v) : undefined }],
    ['form:needs-project', v => (v.objects?.length ? ((v.objects[0].needs = { project: 'no_project', step: 3 }), v) : undefined)],
    ['form:months', v => (v.happenings?.length ? ((v.happenings[0].months = [13]), v) : undefined)],
  ],
  story: [
    ['ref:teller', v => ((v.teller = 'nobody'), v)],
    ['ref:word', v => (Array.isArray(v.words) && v.words.length ? ((v.words[0] = 'no-such-word'), v) : undefined)],
    ['ref:continues-self', v => ((v.continues = v.id), v)],
  ],
  villager: [
    ['form:remember-memory', v => (v.lines?.remember?.length ? ((v.lines.remember[0] = { sl: 'Mislim nate.', en: 'I think of you.' }), v) : undefined)],
    ['form:greet-none', v => (v.lines?.greet ? ((v.lines.greet = v.lines.greet.map((l: J) => ({ ...l, level: 1 }))), v) : undefined)],
    ['form:sleeps', v => ((v.sleeps = 'house:nowhere'), v)],
  ],
  drill: [
    ['ref:grammar-page', v => { const p = findKey(v, 'rule'); return p ? (setAt(v, p, 'no-such-page'), v) : undefined }],
    ['ref:teller', v => ('teller' in v ? ((v.teller = 'nobody'), v) : undefined)],
    ['form:language', v => ((v.language = 'xx'), v)],
  ],
  grammar: [['form:strict-card', v => ((v.card = { rule: 'x', colour: 'red' }), v)]],
  arrival: [['ref:language', v => ((v.language = 'it'), v)]],
}

/** Up to three samples of each kind: the first, the middle and the largest of its curated files (or examples). */
const byKind = new Map<string, { source: string; text: string; refs: string | null }[]>()
for (const c of curated) byKind.set(c.kind, [...(byKind.get(c.kind) ?? []), { source: rel(c.path), text: read(c.path), refs: c.refs }])
for (const e of examples) if (!byKind.has(e.kind) || ['arrival', 'worksheet'].includes(e.kind)) byKind.set(e.kind, [...(byKind.get(e.kind) ?? []), { source: e.source, text: e.text, refs: e.refs }])
for (const [kind, list] of byKind) {
  if (kind === 'forms' || kind === 'extra') continue
  const bySize = [...list].sort((a, b) => a.text.length - b.text.length)
  const samples = [...new Set([list[0], list[Math.floor(list.length / 2)], bySize[bySize.length - 1]])]
  for (const [si, s] of samples.entries()) {
    const sample = JSON.parse(s.text)
    if (!sample || typeof sample !== 'object' || Array.isArray(sample)) continue
    for (const [name, f] of [...generic(sample), ...(specific[kind] ?? [])]) {
      const v = f(clone(sample))
      if (v === undefined) continue
      add({ kind, source: s.source, mutation: name, refs: s.refs, input: JSON.stringify(v, null, 2) })
    }
    // a file that isn't JSON (cut short): the engine's own parser message, which differs between engines
    if (si === 0) add({ kind, source: s.source, mutation: 'json:truncated', refs: s.refs, input: s.text.slice(0, Math.floor(s.text.length / 2)) })
  }
}
// the dictionary-free validators that report per entry: an entry broken in each list
{
  const forms = JSON.parse(read(join(lexiconDir, 'sl.forms.json')))
  const fv: [string, (v: J) => J][] = [
    ['drop:meta', v => (delete v.meta, v)], ['form:table-pos', v => ((v.tables[0].pos = 'thing'), v)], ['drop:partner-pf', v => (v.partners?.length ? (delete v.partners[0].pf, v) : v)],
    ['type:tables', v => ((v.tables = 'oops'), v)], ['form:leave-out', v => ((v.leave_out = [{ lemma: '' }]), v)],
  ]
  for (const [name, f] of fv) add({ kind: 'forms', source: rel(join(lexiconDir, 'sl.forms.json')), mutation: name, refs: null, input: JSON.stringify(f(clone(forms)), null, 2) })
  const extra = JSON.parse(read(join(lexiconDir, 'sl.extra.json')))
  const ev: [string, (v: J) => J][] = [
    ['drop:meta-reviewed', v => (delete v.meta.reviewed, v)], ['form:gloss-many', v => ((v.entries[0].gloss = ['a', 'b', 'c', 'd']), v)], ['form:forms-empty', v => ((v.entries[1].forms = {}), v)],
    ['form:gender', v => ((v.entries[2].gender = 'x'), v)], ['type:entries', v => ((v.entries = {}), v)],
  ]
  for (const [name, f] of ev) add({ kind: 'extra', source: rel(join(lexiconDir, 'sl.extra.json')), mutation: name, refs: null, input: JSON.stringify(f(clone(extra)), null, 2) })
}

// a culture pack broken: a copy of it with one file changed (or gone), checked as the pack (its own root: a mount on the base)
{
  const variants: { culture: string; name: string; change: (dir: string) => { files: Record<string, string>; remove: string[] } }[] = []
  // [dir]: the copy's companion/; the files' paths are relative to it, as the mounts' are
  const edit = (c: string, f: string, g: (v: J) => J) => (dir: string) => {
    const at = join('cultures', c, f)
    const text = JSON.stringify(g(JSON.parse(read(join(dir, at)))), null, 2)
    writeFileSync(join(dir, at), text)
    return { files: { [at]: text }, remove: [] }
  }
  const gone = (c: string, f: string) => (dir: string) => (rmSync(join(dir, 'cultures', c, f)), { files: {}, remove: [join('cultures', c, f)] })
  variants.push({ culture: 'primorska', name: 'gone:world', change: gone('primorska', 'world.json') })
  variants.push({ culture: 'primorska', name: 'ref:festival-leader', change: edit('primorska', 'festivals.json', v => ((v.festivals[0].leaders = ['nobody']), v)) })
  variants.push({ culture: 'primorska', name: 'ref:thanks-good', change: edit('primorska', 'chest.json', v => ((v.thanks_default = 'no-good'), v)) })
  variants.push({ culture: 'primorska', name: 'placeholder:quests', change: edit('primorska', 'quests.json', v => { const p = firstText(v)!; setAt(v, p, `${getAt(v, p)} {learner:xyz}`); return v }) })
  const scene = jsonFiles(join(culturesDir, 'primorska', 'scenes'))[0]
  if (scene) variants.push({ culture: 'primorska', name: 'scene:villager', change: edit('primorska', `scenes/${scene.split('/').pop()}`, v => ((v.people[0].villager = 'no-villager'), v)) })
  const story = jsonFiles(join(culturesDir, 'friuli', 'stories'))[0]
  if (story) variants.push({ culture: 'friuli', name: 'story:order', change: edit('friuli', `stories/${story.split('/').pop()}`, v => (delete v.order, v)) })
  variants.push({ culture: 'kaernten', name: 'type:projects', change: edit('kaernten', 'projects.json', v => ((v.projects = 'oops'), v)) })
  variants.push({ culture: 'lakeland', name: 'drop:manifest-language', change: edit('lakeland', 'culture.json', v => (delete v.language, v)) })
  for (const [i, x] of variants.entries()) {
    // a copy of what the pack's check reads, under a companion/ of its own
    const root = join(scratch, `variant-${i}`, 'companion')
    for (const f of cultureReads(x.culture)) {
      mkdirSync(join(root, f, '..'), { recursive: true })
      cpSync(join(companion, f), join(root, f))
    }
    const { files, remove } = x.change(root)
    const name = `culture-${x.culture}-${x.name.replace(/[^a-z0-9-]+/g, '-')}`
    mounts[name] = { root, base: `culture-${x.culture}`, files, remove }
    add({ kind: 'culture', source: rel(join(culturesDir, x.culture)), mutation: x.name, refs: use(x.culture, { culturesDir: join(root, 'cultures'), cultureId: x.culture }), mounts: [name], input: 'null' })
  }
}

// the words a tutor's dialog weaves in (publish_scene's check, the dictionary's lemmas): declared tested, said, unknown
{
  const dict = new Lexicon({ dir: lexiconDir, language: 'sl', tutorFile: join(lexiconDir, '.no-tutor-glosses.json') })
  const book = new FormsBook({ dir: lexiconDir, language: 'sl' })
  const refs = use('primorska-woven')
  for (const p of jsonFiles(join(companion, 'scenes'))) {
    const raw = JSON.parse(read(p))
    const v = validateScene(raw, refSets.primorska as never)
    if (!v.ok) continue
    const s: Scene = v.scene
    const objects = resolveScene({ ...s, source: 'tutor' }, slPacks as never).objects
    const dialogs = s.dialogs.filter(d => d.lines.some(l => l.choices.length)).slice(0, 2)
    for (const d of dialogs) {
      const di = raw.dialogs.findIndex((x: J) => x.id === d.id)
      const tested = testedWords(d, { language: s.language, objects }, lemmaFinder(dict, book, 'sl'))
      // a word someone says (the placeholders aside) that no turn tests
      const said = d.lines
        .filter(l => !l.choices.length)
        .flatMap(l => (l.sl ?? '').replace(/\{[^{}]*\}/g, ' ').toLowerCase().match(/[\p{L}]+/gu) ?? [])
        .find(w => w.length > 4 && !tested.some(t => t.word === w))
      const object = s.objects.find(o => o.pack ?? s.pack)
      const weave = (name: string, words: string[]) => {
        const x = clone(raw)
        x.dialogs[di].words = words
        add({ kind: 'woven', source: rel(p), mutation: `${d.id}:${name}`, refs, mounts: ['lexicon-sl'], input: JSON.stringify(x, null, 2) })
      }
      if (tested.length) weave('tested', [...new Set(tested.map(t => t.item_id ?? t.word))].slice(0, 3))
      if (said) weave('said', [said])
      if (object) weave('object-card', [`vocab_${object.pack ?? s.pack}_${object.word}`])
      weave('no-card', ['vocab_no-pack_nothing'])
      weave('two-words', ['dober dan'])
    }
  }
}

// --- written -------------------------------------------------------------------------------------------------------------

for (const [name, m] of Object.entries(mounts)) writeFileSync(join(out, 'mounts', `${name}.json`), JSON.stringify(m))
writeFileSync(join(out, 'cases.jsonl'), cases.map(c => JSON.stringify(c)).join('\n') + '\n')
const summary: Record<string, { cases: number; curated: number; variants: number; ok: number; refused: number; notJson: number; thrown: number }> = {}
for (const c of cases) {
  const s = (summary[c.kind] ??= { cases: 0, curated: 0, variants: 0, ok: 0, refused: 0, notJson: 0, thrown: 0 })
  s.cases++
  if (c.mutation) s.variants++
  else s.curated++
  if (c.expected!.thrown) s.thrown++
  else if (c.expected!.json === false) s.notJson++
  else if (c.expected!.ok) s.ok++
  else s.refused++
}
writeFileSync(join(out, 'summary.json'), JSON.stringify({ cases: cases.length, refs: Object.keys(refSets), mounts: Object.keys(mounts), kinds: summary }, null, 2) + '\n')
const size = (p: string) => statSync(p).size
console.log(`${cases.length} cases in ${rel(join(out, 'cases.jsonl'))} (${(size(join(out, 'cases.jsonl')) / 1e6).toFixed(1)} MB)`)
console.table(summary)
