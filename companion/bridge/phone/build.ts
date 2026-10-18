// Bundles the bridge for the app (docs/plans/05-built/phone-runtime.md): src/phone/entry.ts and everything it imports,
// one script for QuickJS (si.lanisce.lani.phone.PhoneBridge loads it from the APK's assets, phone/bridge-phone.js).
//
// Node's built-ins are the shims in phone/shims, over the host the app defines (globalThis.__lani, shims/host.ts):
// node:fs (and fs/promises), path, os, crypto, util, zlib, url, child_process; bun:sqlite is a stub (no voice store
// on the phone). Anything else of node's or Bun's, the MCP SDK, the CLIs' packages: the build fails, so nothing slips
// in unseen (the node's control socket, its MCP server). zod's locales: English only (validators/zod-locales.ts).
// import.meta.dir and .path in the repository's modules (the bridge's, companion/prints') become where they would be
// under the phone's project directory.
//
// Run: bun phone/build.ts [--out <dir>] [--debug] [--check]
//   --out    where bridge-phone.js goes (default phone/dist); the app's Gradle task (phoneBridge) passes its own
//   --debug  also bridge-phone.debug.js, unminified
//   --quiet  no report
// Prints the sizes (raw, gzipped) and warns about leftovers of Node or Bun in the script.
import { gzipSync } from 'bun'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'

const here = import.meta.dir
const bridge = resolve(here, '..')
const repo = resolve(bridge, '../..')
const args = process.argv.slice(2)
const at = args.indexOf('--out')
const out = resolve(at >= 0 ? args[at + 1] : join(here, 'dist'))
const debug = args.includes('--debug')
const quiet = args.includes('--quiet')
mkdirSync(out, { recursive: true })

const shim = (name: string) => join(here, 'shims', `${name}.ts`)
const ALIASES: Record<string, string> = {
  fs: shim('fs'),
  'fs/promises': shim('fs-promises'),
  path: shim('path'),
  os: shim('os'),
  crypto: shim('crypto'),
  util: shim('util'),
  zlib: shim('zlib'),
  url: shim('url'),
  child_process: shim('child_process'),
  'bun:sqlite': shim('sqlite'),
}

const plugin: import('bun').BunPlugin = {
  name: 'lani-phone',
  setup(b) {
    b.onResolve({ filter: /^(node:)?(fs|fs\/promises|path|os|crypto|util|zlib|url|child_process)$/ }, a => ({ path: ALIASES[a.path.replace(/^node:/, '')] }))
    b.onResolve({ filter: /^bun:sqlite$/ }, () => ({ path: ALIASES['bun:sqlite'] }))
    // zod's locales: English only
    b.onResolve({ filter: /\/locales\/index\.js$/ }, a => (a.importer.includes('/node_modules/zod/') ? { path: join(bridge, 'validators/zod-locales.ts') } : undefined))
    // anything else of Node's or Bun's, the MCP SDK, the CLIs' packages: not on the phone
    b.onResolve({ filter: /^(node:|bun$|bun:|@modelcontextprotocol\/|@clack\/|qrcode-terminal$)/ }, a => {
      throw new Error(`${relative(bridge, a.importer)} imports ${a.path}: not available in the phone bridge (phone/build.ts)`)
    })
    // import.meta in the repository's modules: where the file would be under the phone's project directory
    b.onLoad({ filter: /\.ts$/ }, a => {
      let text = readFileSync(a.path, 'utf8')
      if (text.includes('import.meta') && !a.path.includes('/node_modules/')) {
        const rel = relative(repo, a.path)
        text = text
          .replaceAll('import.meta.dir', `(globalThis.__laniProject + ${JSON.stringify(`/${dirname(rel)}`)})`)
          .replaceAll('import.meta.path', `(globalThis.__laniProject + ${JSON.stringify(`/${rel}`)})`)
          .replaceAll('import.meta.url', `("file://" + globalThis.__laniProject + ${JSON.stringify(`/${rel}`)})`)
      }
      return { contents: text, loader: 'ts' }
    })
  },
}

async function build(minify: boolean): Promise<string> {
  const r = await Bun.build({
    entrypoints: [join(bridge, 'src/phone/entry.ts')],
    target: 'browser',
    format: 'iife',
    minify,
    plugins: [plugin],
    // what a script engine without Node would trip over at its first read
    define: { 'process.env.NODE_ENV': '"production"' },
  })
  if (!r.success) throw new Error(r.logs.map(String).join('\n'))
  return r.outputs[0].text()
}

const kb = (n: number) => `${(n / 1024).toFixed(1)} KB`
const t0 = performance.now()
const min = await build(true)
writeFileSync(join(out, 'bridge-phone.js'), min)
const rows = [`${join(out, 'bridge-phone.js')}: ${kb(Buffer.byteLength(min))} minified, ${kb(gzipSync(Buffer.from(min)).length)} gzipped (${Math.round(performance.now() - t0)} ms)`]
if (debug) {
  const dbg = await build(false)
  writeFileSync(join(out, 'bridge-phone.debug.js'), dbg)
  rows.push(`bridge-phone.debug.js: ${kb(Buffer.byteLength(dbg))} unminified`)
}
// a leftover of Node or Bun in the script would fail only when it runs: say so now
for (const bad of ['import.meta', 'require(']) if (min.includes(bad)) rows.push(`  warning: bridge-phone.js contains "${bad}"`)
if (!quiet) console.log(rows.join('\n'))
