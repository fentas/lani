// A second dev town for emulator QA (companion/bin/qa): the fixture Italian town of test/fixtures/dev-town.json (the
// Friuli culture pack, not a child's, Italian from Slovene) in a temp data dir made as `lani-profile add` makes one,
// with its village state, served by a bridge of its own. Nothing of a real learner is read or written.
// Run: bun test/dev-town.ts   → http://127.0.0.1:8797, app token "dev-town-token".
// Env: LANI_DEV_TOWN_PORT its port (default 8797, reserved for it in src/learners.ts).
//      LANI_DEV_TOWN_VOICES=1 the town's own clips of its people's greetings (companion/README.md, "Voices on a visit"):
//      once its bridge answers, a fake ElevenLabs (test/fakes/eleven.ts, tones; never a real key) voices each greeting
//      of its residents and today's visitor that names nobody (no {learner}, no {memory}; both forms of a {m:…|f:…})
//      in the town's language, through the town's own POST /voice/say in the speaker's cast voice; then the fake stops,
//      so the town keeps those clips and makes no more. Each one is logged as `dev town voice: {json}`.
//      LANI_DEV_TOWN_LINK the URL of a town to link with once both answer (companion/bin/qa: the dev bridge,
//      http://127.0.0.1:8791), as the app does it: that town's POST /towns/invite (LANI_DEV_TOWN_LINK_TOKEN, its app
//      token, default dev-token), accepted here with POST /towns/accept. Both links go away with the temp dirs.
//      LANI_DEV_TOWN_FIXTURE another fixture of test/fixtures (default dev-town.json): dev-town-child.json is a child's
//      town (Italian from German) with a learner's progress (test/fixtures/learning.ts). A child's app can't accept an
//      invitation, so it is linked as a parent links it on the node: an invitation made here (lani-town invite
//      --profile), accepted by the other town's app; with "guardian", that town is the child's guardian (lani-town
//      guardian): it sees the child's progress in detail.
//      dev-town-sl.json is a Slovene village of the Primorska culture pack (companion/bin/qa --steps visit-voices: an
//      Italian learner visits a Slovene town, as Teo visits Jan's village). dev-town-kaernten.json is a Carinthian village
//      in German (the Kärnten culture pack): its horizon, Am See, in Carinthia's look (companion/SCENES.md "Culture looks").
//      LANI_DEV_TOWN_REGISTRY a directory: this town is written there as a learner profile of a node (its profiles.json,
//      as lani-profile add writes it: its id, whether a child's, its port and data), so a dev bridge whose LANI_PROFILES_DIR
//      it is sees it as a town of its node: the parent's app links its own child's town (companion/README.md, "A child's
//      town"). Without LANI_DEV_TOWN_LINK the town links with nobody: the invitation steps link it in the app.
import '../src/env' // first: LANI_* from the FLUENT_* names of a node set up before the rename
import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js'
import { mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { RESERVED_PORTS, initLearnerData, portFree, readRegistry, writeRegistry } from '../src/learners'
import { loadBridgeKey } from '../src/pairing'
import { Towns, createInvite, inviteUri, localDate } from '../src/towns'
import { fakeEleven } from './fakes/eleven'
import { writeLearning } from './fixtures/learning'

const DEFAULT_PORT = 8797
const TOKEN = 'dev-town-token'
const projectDir = resolve(import.meta.dir, '../../..')
const fixtureName = process.env.LANI_DEV_TOWN_FIXTURE || 'dev-town.json'
if (!/^[a-z0-9-]+\.json$/.test(fixtureName)) {
  console.error(`dev town: LANI_DEV_TOWN_FIXTURE is a file of test/fixtures, e.g. dev-town-child.json: ${fixtureName}`)
  process.exit(1)
}
const readFixture = (name: string) => JSON.parse(readFileSync(resolve(import.meta.dir, 'fixtures', name), 'utf8'))
const own = readFixture(fixtureName)
// a fixture can take another's village ("state_from"): the child's town is the same village, a child's
const fixture = { ...own, state: own.state ?? readFixture(own.state_from).state }

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
// a learner's progress (cards, mistakes, sessions), for "📈 Napredek · Progress" on the linked town's phone
if (fixture.learning) writeLearning(dataDir, { language: fixture.learning.language, base: fixture.learning.base })
console.log(`dev town data: ${dataDir}`)
const registry = process.env.LANI_DEV_TOWN_REGISTRY
if (registry) {
  const r = readRegistry(registry)
  const entry = { id: fixture.profile, name: l.name, target: l.target, base: l.base, culture: l.culture, child: l.child === true, port, https_port: 0, tmux: '', remote_control: '', data_dir: dataDir, created: new Date().toISOString() }
  writeRegistry(registry, { ...r, profiles: [...r.profiles.filter(p => p.id !== fixture.profile), entry] })
  console.log(`dev town: a learner profile of the node in ${join(registry, 'profiles.json')}`)
}

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
// Only what this town needs of the environment: none of the caller's LANI_* settings, no voice keys (the fake's alone,
// with LANI_DEV_TOWN_VOICES), no speech worker.
const env: Record<string, string> = {}
for (const [k, v] of Object.entries(process.env)) if (v !== undefined && !k.startsWith('LANI_') && k !== 'ELEVENLABS_API_KEY') env[k] = v
Object.assign(env, {
  CLAUDE_PROJECT_DIR: projectDir,
  LANI_PROFILE: fixture.profile, LANI_DATA_DIR: dataDir, LANI_BRIDGE_PORT: String(port), LANI_BRIDGE_TOKEN: TOKEN,
  LANI_FAMILY_TOKEN: 'dev-town-family', LANI_CHILD: l.child ? '1' : '', LANI_CULTURE: l.culture, LANI_RHYTHM: 'off',
  ELEVENLABS_API_KEY: '', LANI_VOICE_DESIGN: 'off', LANI_STT_URL: 'http://127.0.0.1:9', LANI_GEPARD_URL: 'http://127.0.0.1:9',
  // companion/bin/qa knows a leftover of this town by it (it stops only these, never a learner's bridge on the port)
  LANI_DEV_TOWN: '1',
  // the node's settings file only when the caller names one (LANI_ENV_FILE=/dev/null: none at all)
  ...(process.env.LANI_ENV_FILE ? { LANI_ENV_FILE: process.env.LANI_ENV_FILE } : {}),
})
// LANI_DEV_TOWN_VOICES=1: a fake ElevenLabs while the town's people's greetings are voiced (below), then stopped
const fake = process.env.LANI_DEV_TOWN_VOICES === '1' ? fakeEleven({ tones: true }) : undefined
if (fake) Object.assign(env, { ELEVENLABS_API_KEY: 'test-key', ELEVENLABS_BASE_URL: fake.url })
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

/**
 * The greetings of the town's people (its residents, today's visitor) in its language that name nobody, each in the voice
 * its villager speaks with (their cast speaker, "grandma"), as a visitor's app asks for them: both forms of a {m:…|f:…},
 * none with another placeholder ({learner}, {memory}).
 */
function greetings(): { person: string; voice: string; text: string }[] {
  const dir = join(projectDir, 'companion', 'cultures', l.culture, 'villagers')
  const ids: string[] = [...(fixture.state.residents ?? []).map((r: { id: string }) => r.id), ...(fixture.state.visitor ? [fixture.state.visitor.id] : [])]
  const out: { person: string; voice: string; text: string }[] = []
  for (const person of ids) {
    let v: any
    try {
      v = JSON.parse(readFileSync(join(dir, `${person}.json`), 'utf8'))
    } catch {
      continue
    }
    const voice = String(v.speaker ?? v.voice ?? 'female')
    for (const line of v.lines?.greet ?? []) {
      const said = line?.[l.target]
      if (typeof said !== 'string') continue
      for (const g of ['m', 'f']) {
        const text = said.replace(/\{([mf]):([^|}]*)\|([mf]):([^}]*)\}/g, (_: string, a: string, x: string, _b: string, y: string) => (a === g ? x : y))
        if (/[{}]/.test(text) || out.some(o => o.person === person && o.text === text)) continue
        out.push({ person, voice, text })
      }
    }
  }
  return out
}

if (fake) {
  // the town's own clips, as its own app would have had them made: its POST /voice/say, then no more
  let made = 0
  const lines = greetings()
  for (const g of lines) {
    const r = await fetch(`${self}/voice/say`, { method: 'POST', headers: bearer(TOKEN), body: JSON.stringify({ text: g.text, voice: g.voice }), signal: AbortSignal.timeout(30_000) })
    const body = r.status === 200 ? ((await r.json().catch(() => ({}))) as { url?: string; voice?: string; engine?: string }) : {}
    if (body.url) made++
    console.log(`dev town voice: ${JSON.stringify({ ...g, status: r.status, clip_voice: body.voice ?? null, engine: body.engine ?? null })}`)
  }
  fake.stop()
  console.log(`dev town: ${made} of ${lines.length} greetings of its people voiced into its own store (a fake ElevenLabs: ${fake.st.tts.length} takes), the fake stopped`)
}
console.log(`dev town up on :${port}: "${fixture.town_name}" (${l.name}, ${l.culture}, ${l.target} from ${l.base}), town id ${id}, token ${TOKEN}`)

/**
 * Links this town with the one at [url] ([token]: its app token), as two apps do: an invitation there, accepted here.
 * A child's town as a parent links it on the node: an invitation made here (by the CLI), accepted by that town's app.
 */
async function link(url: string, token: string): Promise<string> {
  if (!(await answers(url, 60_000))) return `not linked: nothing answers on ${url}`
  if (l.child) {
    const { code } = createInvite(join(dataDir, 'app'), { by: 'cli' })
    const invite = inviteUri({ url: self, code, fingerprint: id })
    const ok = await fetch(`${url}/towns/accept`, { method: 'POST', headers: bearer(token), body: JSON.stringify({ invite }), signal: AbortSignal.timeout(15_000) })
    const body = (await ok.json().catch(() => ({}))) as any
    if (!ok.ok) return `not linked: ${url}/towns/accept answered ${ok.status} ${JSON.stringify(body)}`
    const peer = ((await (await fetch(`${url}/town/hello`)).json()) as any).id as string
    // that town is the child's guardian's: it sees the child's progress in detail (lani-town guardian)
    if (fixture.guardian) new Towns(join(dataDir, 'app')).patch(peer, x => ({ ...x, guardian: true }))
    return `linked with ${url} by an invitation of this child's town, both ways${fixture.guardian ? ', that town its guardian' : ''}`
  }
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
