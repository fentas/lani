// Runs the conformance corpus (build-corpus.ts) through the validators' bundle (validators/build.ts) in bun's own engine,
// as the app's harness runs it in QuickJS and JavaScriptEngine (companion/android/app/src/androidTest, ValidatorsTest):
// the references kept, the files mounted when a case needs them, each case's answer compared with the bridge's. A
// file that isn't JSON is compared on its verdict only: the parser's message is the engine's own.
//
// Run: bun test/conformance/run-bundle.ts [--bundle validators/dist/validators-lexicon.js]   (from companion/bridge)
// Exits 1 on a mismatch.
import { readdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'

const at = process.argv.indexOf('--bundle')
const bundle = resolve(at > 0 ? process.argv[at + 1] : join(import.meta.dir, '../../validators/dist/validators-lexicon.js'))
const out = join(import.meta.dir, 'out')

type Lani = {
  validate(kind: string, text: string, refs: string | null, opts: string | null): string
  setRefs(name: string, json: string): number
  mount(path: string, text: string): number
  copy(from: string, to: string, rels: string): number
  unmount(path: string): boolean
}
type Mount = { root: string; files: Record<string, string>; base?: string; remove?: string[] }
type Case = { id: string; kind: string; refs: string | null; mounts?: string[]; input: string; expected: Record<string, unknown> }

const t0 = performance.now()
;(0, eval)(readFileSync(bundle, 'utf8'))
const lani = (globalThis as { lani?: Lani }).lani!
const loaded = performance.now() - t0

const t1 = performance.now()
for (const f of readdirSync(join(out, 'refs')).sort()) lani.setRefs(f.slice(0, -5), readFileSync(join(out, 'refs', f), 'utf8'))
const refsMs = performance.now() - t1

const mountMs: Record<string, number> = {}
const done = new Set<string>()
function ensure(name: string) {
  if (done.has(name)) return
  const m: Mount = JSON.parse(readFileSync(join(out, 'mounts', `${name}.json`), 'utf8'))
  if (m.base) {
    ensure(m.base)
    const base: Mount = JSON.parse(readFileSync(join(out, 'mounts', `${m.base}.json`), 'utf8'))
    const t = performance.now()
    lani.copy(base.root, m.root, JSON.stringify(Object.keys(base.files)))
    for (const [r, text] of Object.entries(m.files)) lani.mount(`${m.root}/${r}`, text)
    for (const r of m.remove ?? []) lani.unmount(`${m.root}/${r}`)
    mountMs[name] = performance.now() - t
  } else {
    const t = performance.now()
    for (const [r, text] of Object.entries(m.files)) lani.mount(`${m.root}/${r}`, text)
    mountMs[name] = performance.now() - t
  }
  done.add(name)
}

const lines = readFileSync(join(out, 'cases.jsonl'), 'utf8').split('\n').filter(Boolean)
const mismatches: string[] = []
const engineMessages: string[] = []
const times: Record<string, number[]> = {}
const same = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b)
for (const line of lines) {
  const c: Case = JSON.parse(line)
  for (const m of c.mounts ?? []) ensure(m)
  const t = performance.now()
  const got = JSON.parse(lani.validate(c.kind, c.input, c.refs, '{"digest":true}'))
  ;(times[c.kind] ??= []).push(performance.now() - t)
  if (c.expected.json === false) {
    if (got.json !== false || got.ok !== false) mismatches.push(`${c.id}: ${JSON.stringify(got)} (expected not JSON)`)
    else if (got.errors !== c.expected.errors) engineMessages.push(`${c.id}: "${got.errors}" (bun: "${c.expected.errors}")`)
  } else if (!same(got, c.expected)) mismatches.push(`${c.id}:\n  got      ${JSON.stringify(got).slice(0, 400)}\n  expected ${JSON.stringify(c.expected).slice(0, 400)}`)
}

const q = (xs: number[], p: number) => [...xs].sort((a, b) => a - b)[Math.min(xs.length - 1, Math.floor(p * xs.length))]
console.log(`bundle ${bundle}: loaded in ${loaded.toFixed(1)} ms, refs in ${refsMs.toFixed(1)} ms`)
console.log(`mounts: ${Object.entries(mountMs).map(([k, v]) => `${k} ${v.toFixed(1)} ms`).join(', ')}`)
for (const [k, xs] of Object.entries(times)) console.log(`${k.padEnd(10)} ${String(xs.length).padStart(4)} cases  median ${q(xs, 0.5).toFixed(2)} ms  p95 ${q(xs, 0.95).toFixed(2)} ms  max ${Math.max(...xs).toFixed(2)} ms`)
console.log(`${lines.length} cases, ${mismatches.length} mismatches, ${engineMessages.length} parser messages of the engine's own`)
for (const m of mismatches.slice(0, 30)) console.log(`MISMATCH ${m}`)
for (const m of engineMessages.slice(0, 3)) console.log(`parser: ${m}`)
process.exit(mismatches.length ? 1 : 0)
