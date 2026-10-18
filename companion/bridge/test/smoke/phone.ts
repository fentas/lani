// Smoke checks: the bridge for the app (docs/plans/05-built/phone-runtime.md) still bundles: phone/build.ts finds no
// Node or Bun built-in its shims don't cover, no MCP SDK, nothing of the node's own parts in the modules the phone shares
// (a shared module that starts importing node:net or the MCP SDK fails here, not first in the app's build). The bundle
// runs in QuickJS in the app's JVM tests (companion/android: phone/PhoneBridgeTest, PhoneBridgeNodeTest).
import { resolve } from 'node:path'
import { check, tempDir } from './harness'

export default async function phone() {
  const out = tempDir('lani-smoke-phone-')
  const p = Bun.spawn(['bun', resolve(import.meta.dir, '../../phone/build.ts'), '--out', out], { stdout: 'pipe', stderr: 'pipe' })
  const [said, err, code] = await Promise.all([new Response(p.stdout).text(), new Response(p.stderr).text(), p.exited])
  check('the phone bundle builds: no Node or Bun built-in without its shim, no MCP SDK (bun companion/bridge/phone/build.ts)', code === 0 && /bridge-phone\.js: [\d.]+ KB minified/.test(said), `${said}${err}`)
  check('… and has nothing of Node or Bun left in it (import.meta, require)', code === 0 && !said.includes('warning:'), said)
  // its URL, URLSearchParams, Buffer and path against Bun's own (test/phone-shims.ts)
  const s = Bun.spawn(['bun', resolve(import.meta.dir, '../phone-shims.ts')], { stdout: 'pipe', stderr: 'pipe' })
  const [same, serr, scode] = await Promise.all([new Response(s.stdout).text(), new Response(s.stderr).text(), s.exited])
  check("the phone's URL, URLSearchParams, Buffer and path shims answer as Bun's own (test/phone-shims.ts)", scode === 0 && same.includes('all the same'), `${same}${serr}`)
}
