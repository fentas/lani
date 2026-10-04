// A second dev town for emulator QA (companion/bin/qa): the fixture Italian town of test/fixtures/dev-town.json (the
// Friuli culture pack, not a child's, Italian from Slovene) in a temp data dir made as `lani-profile add` makes one,
// with its village state, served by a bridge of its own. Nothing of a real learner is read or written.
// Run: bun test/dev-town.ts   → http://127.0.0.1:8797, app token "dev-town-token".
// Env: LANI_DEV_TOWN_PORT its port (default 8797, reserved for it in src/learners.ts).
//      LANI_DEV_TOWN_LINK the URL of a town to link with once both answer (companion/bin/qa: the dev bridge,
//      http://127.0.0.1:8791), as the app does it: that town's POST /towns/invite (LANI_DEV_TOWN_LINK_TOKEN, its app
//      token, default dev-token), accepted here with POST /towns/accept. Both links go away with the temp dirs.
import '../src/env' // first: LANI_* from the FLUENT_* names of a node set up before the rename
import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js'
import { mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { RESERVED_PORTS, initLearnerData, portFree } from '../src/learners'
import { loadBridgeKey } from '../src/pairing'
import { Towns, localDate } from '../src/towns'

const DEFAULT_PORT = 8797
const TOKEN = 'dev-town-token'
const projectDir = resolve(import.meta.dir, '../../..')
const fixture = JSON.parse(readFileSync(resolve(import.meta.dir, 'fixtures/dev-town.json'), 'utf8'))

const port = Number(process.env.LANI_DEV_TOWN_PORT ?? DEFAULT_PORT)
const fail = (why: string, code = 1): never => {
  console.error(`dev town: ${why}`)
  process.exit(code)
}
if (!Number.isInteger(port) || port < 1024 || port > 65535) fail(`LANI_DEV_TOWN_PORT is not a port: ${process.env.LANI_DEV_TOWN_PORT}`)
// the real bridge, the dev bridge, the voice and speech workers, the smoke tests: never
if (port !== DEFAULT_PORT && RESERVED_PORTS.has(port)) fail(`port ${port} is reserved (src/learners.ts RESERVED_PORTS)`)
// something answers there already (a learner's bridge, another dev town): leave it be
if (!portFree(port)) fail(`port ${port} is taken; LANI_DEV_TOWN_PORT picks another`, 3)

// --- the town's data: a new learner's databases (as lani-profile add), its name, later its village ---------------------
const root = mkdtempSync(join(tmpdir(), 'lani-dev-town-'))
const dataDir = join(root, 'data')
process.on('exit', () => rmSync(root, { recursive: true, force: true }))
/** Stops the town: its bridge first, once it runs (so it writes nothing more into the data being deleted), then this. */
let stop: () => void = () => process.exit(0)
for (const sig of ['SIGINT', 'SIGTERM', 'SIGHUP'] as const) process.on(sig, () => stop())
const l = fixture.learner
initLearnerData(projectDir, dataDir, { name: l.name, target: l.target, base: l.base, culture: l.culture, child: l.child })
new Towns(join(dataDir, 'app')).setName(fixture.town_name)
console.log(`dev town data: ${dataDir}`)

/** The fixture's state with its days filled in: "@day" is today, "@day-40" forty days before (the node's dates). */
function withDays(v: unknown): unknown {
  if (typeof v === 'string') {
    const m = /^@day(?:([+-])(\d+))?$/.exec(v)
    return m ? localDate(Date.now() + (m[1] === '-' ? -1 : 1) * Number(m[2] ?? 0) * 86_400_000) : v
  }
  if (Array.isArray(v)) return v.map(withDays)
  if (v && typeof v === 'object') return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, withDays(x)]))
  return v
}

// --- its bridge -------------------------------------------------------------------------------------------------------
// Only what this town needs of the environment: none of the caller's LANI_* settings, no voice keys, no speech worker.
const env: Record<string, string> = {}
for (const [k, v] of Object.entries(process.env)) if (v !== undefined && !k.startsWith('LANI_') && k !== 'ELEVENLABS_API_KEY') env[k] = v
Object.assign(env, {
  CLAUDE_PROJECT_DIR: projectDir,
  LANI_PROFILE: fixture.profile, LANI_DATA_DIR: dataDir, LANI_BRIDGE_PORT: String(port), LANI_BRIDGE_TOKEN: TOKEN,
  LANI_FAMILY_TOKEN: 'dev-town-family', LANI_CHILD: l.child ? '1' : '', LANI_CULTURE: l.culture, LANI_RHYTHM: 'off',
  ELEVENLABS_API_KEY: '', LANI_VOICE_DESIGN: 'off', LANI_STT_URL: 'http://127.0.0.1:9', LANI_GEPARD_URL: 'http://127.0.0.1:9',
  // companion/bin/qa knows a leftover of this town by it (it stops only these, never a learner's bridge on the port)
  LANI_DEV_TOWN: '1',
})
const client = new Client({ name: 'dev-town', version: '0' })
// No tutor here: what the town's bridge tells its session (a guest's gift, a message) is only logged.
client.fallbackNotificationHandler = async n => {
  const p = n.params as any
  if (n.method === 'notifications/claude/channel') console.log(`← ${p?.meta?.kind ?? '?'} ${String(p?.content ?? '').slice(0, 120).replace(/\n/g, ' ')}`)
}
await client.connect(new StdioClientTransport({ command: 'bun', args: [resolve(import.meta.dir, '../src/index.ts')], env, stderr: 'inherit' }))
// Never outlive QA (as test/dev-bridge.ts): exit when the bridge closes, or when whoever started us is gone.
let stopping = false
client.onclose = () => process.exit(stopping ? 0 : 1)
stop = () => {
  if (stopping) return
  stopping = true
  setTimeout(() => process.exit(0), 2_000).unref()
  client.close().catch(() => {}).finally(() => process.exit(0))
}
const parent = process.ppid
setInterval(() => { if (process.ppid !== parent) stop() }, 2000)

const self = `http://127.0.0.1:${port}`
const bearer = (t: string) => ({ authorization: `Bearer ${t}`, 'content-type': 'application/json' })

async function answers(url: string, ms: number): Promise<boolean> {
  const end = Date.now() + ms
  while (Date.now() < end) {
    try {
      if ((await fetch(`${url}/health`, { signal: AbortSignal.timeout(1_000) })).ok) return true
    } catch {}
    await Bun.sleep(300)
  }
  return false
}

if (!(await answers(self, 20_000))) fail(`its bridge doesn't answer on ${self}`)
const put = await fetch(`${self}/game`, { method: 'PUT', headers: bearer(TOKEN), body: JSON.stringify({ rev: 0, state: withDays(fixture.state) }) })
if (!put.ok) fail(`its village wasn't saved (PUT /game: ${put.status} ${await put.text()})`)
const id = loadBridgeKey(join(dataDir, 'app')).fingerprint
console.log(`dev town up on :${port}: "${fixture.town_name}" (${l.name}, ${l.culture}, ${l.target} from ${l.base}), town id ${id}, token ${TOKEN}`)

/** Links this town with the one at [url] ([token]: its app token), as two apps do: an invitation there, accepted here. */
async function link(url: string, token: string): Promise<string> {
  if (!(await answers(url, 60_000))) return `not linked: nothing answers on ${url}`
  const inv = await fetch(`${url}/towns/invite`, { method: 'POST', headers: bearer(token), signal: AbortSignal.timeout(10_000) })
  if (!inv.ok) return `not linked: ${url}/towns/invite answered ${inv.status} ${await inv.text()}`
  const { link: invite } = (await inv.json()) as { link: string }
  const ok = await fetch(`${self}/towns/accept`, { method: 'POST', headers: bearer(TOKEN), body: JSON.stringify({ invite }), signal: AbortSignal.timeout(15_000) })
  const body = (await ok.json().catch(() => ({}))) as any
  if (!ok.ok) return `not linked: accepting ${url}'s invitation answered ${ok.status} ${JSON.stringify(body)}`
  return `linked with "${body.town?.name}" (${body.town?.learner}, ${body.town?.language}) on ${url}, both ways`
}

const peer = process.env.LANI_DEV_TOWN_LINK
if (peer) console.log(`dev town: ${await link(peer.replace(/\/+$/, ''), process.env.LANI_DEV_TOWN_LINK_TOKEN ?? 'dev-token')}`)
await new Promise(() => {})
