// Smoke checks: the in-app tutor's copy of the bridge's tools and guide (companion/tutor/tools, companion/tutor/guide)
// is what the bridge makes now (src/cli/tutor-export.ts --check: one source of truth, docs/plans/05-in-app-tutor.md).
import { resolve } from 'node:path'
import { check } from './harness'

export default async function tutor() {
  const script = resolve(import.meta.dir, '../../src/cli/tutor-export.ts')
  // its own process: the script builds the bridge in an environment of its own (no lani.env, a throwaway data dir)
  const p = Bun.spawn(['bun', script, '--check'], { stdout: 'pipe', stderr: 'pipe' })
  const [out, err, code] = await Promise.all([new Response(p.stdout).text(), new Response(p.stderr).text(), p.exited])
  check(
    "companion/tutor's tool specs and guide topics are the bridge's (tutor-export --check; after a change to a tool or a guide paragraph: bun companion/bridge/src/cli/tutor-export.ts)",
    code === 0 && out.includes('match the bridge'),
    `${out}${err}`,
  )
}
