// Bundles the bridge's content validators for a JavaScript engine without Node (the app's QuickJS or Jetpack
// JavaScriptEngine: docs/plans/05-phase-0.md, check 4). Two builds, each one script that sets globalThis.lani
// (src/validate-entry.ts):
//   dist/validators.js          every validator (validate-entry.ts)
//   dist/validators-lexicon.js  and the dictionary's check of a dialog's woven words (validate-entry-lexicon.ts)
// node:fs is validators/fs.ts (an in-memory tree the app mounts files in), node:path validators/path.ts. Also writes
// the unminified builds next to them (*.debug.js) and prints the sizes, raw and gzipped.
//
// Run: bun validators/build.ts [--out <dir>]
import { gzipSync } from 'bun'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'

const here = import.meta.dir
const at = process.argv.indexOf('--out')
const out = resolve(at > 0 ? process.argv[at + 1] : join(here, 'dist'))
mkdirSync(out, { recursive: true })

const shims: import('bun').BunPlugin = {
  name: 'lani-node-shims',
  setup(b) {
    b.onResolve({ filter: /^(node:)?fs$/ }, () => ({ path: join(here, 'fs.ts') }))
    b.onResolve({ filter: /^(node:)?path$/ }, () => ({ path: join(here, 'path.ts') }))
    // zod's locales: English only (zod-locales.ts)
    b.onResolve({ filter: /\/locales\/index\.js$/ }, a => (a.importer.includes('/node_modules/zod/') ? { path: join(here, 'zod-locales.ts') } : undefined))
    // anything else of Node's or Bun's: the build fails, so nothing slips into the bundle unseen
    b.onResolve({ filter: /^(node:|bun$)/ }, a => {
      throw new Error(`${a.importer} imports ${a.path}: not available in the validators' engine`)
    })
  },
}

/** What a script engine lacks: `process` (a cast file's override, read only when the cast is) and import.meta.dir. */
const define = { 'import.meta.dir': '"/lani/bridge/src"', 'process.env.LANI_VOICE_CAST': 'undefined' }

async function build(entry: string, name: string, minify: boolean): Promise<string> {
  const r = await Bun.build({ entrypoints: [join(here, entry)], target: 'browser', format: 'iife', minify, plugins: [shims], define })
  if (!r.success) throw new Error(r.logs.map(String).join('\n'))
  const js = await r.outputs[0].text()
  writeFileSync(join(out, name), js)
  return js
}

const kb = (n: number) => `${(n / 1024).toFixed(1)} KB`
const rows: string[] = []
for (const [entry, name] of [['engine-core.ts', 'validators'], ['engine-lexicon.ts', 'validators-lexicon']] as const) {
  const min = await build(entry, `${name}.js`, true)
  const dbg = await build(entry, `${name}.debug.js`, false)
  const bytes = Buffer.byteLength(min)
  rows.push(`${name}.js: ${kb(bytes)} minified, ${kb(gzipSync(Buffer.from(min)).length)} gzipped (unminified ${kb(Buffer.byteLength(dbg))})`)
  // a leftover of Node or Bun in the script would fail only when it runs: say so now
  for (const bad of ['import.meta', 'require(', 'Bun.', 'process.']) if (min.includes(bad)) rows.push(`  warning: ${name}.js contains "${bad}"`)
}
const lexicon = join(here, '../../lexicon')
for (const f of ['sl.json', 'sl.extra.json', 'sl.forms.json']) {
  const b = readFileSync(join(lexicon, f))
  rows.push(`data ${f}: ${kb(b.length)} (${kb(gzipSync(b).length)} gzipped)`)
}
console.log(`${out}\n${rows.join('\n')}`)
