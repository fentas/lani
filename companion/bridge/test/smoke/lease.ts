// Smoke checks: who writes the learner's data (src/lease.ts, docs/plans/05-built/git-sync.md "The lease"): while
// app/lease.json names the phone, the app's writes are refused with 423 (reads, pairing, speech and the voice go on) and
// the tutor's writing tools fail, each saying why; the node's own lease, or none, lets everything write. The handover's
// QR code (src/handover.ts): its bundle opens with the key in it, or with the typed code, and not with a wrong one, a
// changed fingerprint or after its time. The town's key (pairing.ts loadTownKey): town-key.pem when there is one.
import { generateKeyPairSync } from 'node:crypto'
import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { decodeHandover, encodeHandover, githubRepo, scalarOf } from '../../src/handover'
import { describeLease, LeaseGuard, leaseState, parseLease, type Party } from '../../src/lease'
import { loadBridgeKey, loadTownKey } from '../../src/pairing'
import { auth, base, check, client, dataDir, tempDir } from './harness'

const PHONE: Party = { side: 'phone', device: 'p_0123abcd', name: 'Pixel 8', mode: 'hosted' }
const NODE: Party = { side: 'node', device: 'smoke-node', name: 'smoke-node', mode: 'self-hosted' }
const leaseFile = join(dataDir, 'app/lease.json')
const now = () => new Date().toISOString().replace(/\.\d+Z$/, 'Z')
const later = () => new Date(Date.now() + 14 * 86_400_000).toISOString().replace(/\.\d+Z$/, 'Z')
const held = (holder: Party) => ({ schema: 'lani.lease/v1', holder, offer: null, since: now(), renewed: now(), expires: later() })
const offered = (to: Party, by: Party) => ({ schema: 'lani.lease/v1', holder: null, offer: { ...to, by, at: now() }, since: now(), renewed: now(), expires: later() })

export default async function lease() {
  const post = (path: string, body: unknown) => fetch(`${base}${path}`, { method: 'POST', headers: auth, body: JSON.stringify(body) })
  const level = () => post('/level', { level: 'not-a-level' }) // a write the handler refuses (400): only the guard answers 423

  // --- no lease: the node writes, as before ----------------------------------------------------------
  const free = await level()
  check('lease: without app/lease.json the app may write (the handler answers)', free.status !== 423, free.status)

  // --- the phone holds the data -------------------------------------------------------------------------
  mkdirSync(join(dataDir, 'app'), { recursive: true })
  writeFileSync(leaseFile, JSON.stringify(held(PHONE)))
  const refused = await level()
  const body: any = await refused.json()
  check("lease: while the phone holds the data, the app's writes are refused (423 read_only)", refused.status === 423 && body.error === 'read_only', [refused.status, body])
  check('lease: …saying who writes and how to take it back', /the phone \(Pixel 8\) writes the data/.test(body.message) && body.message.includes('lani handover --take') && body.lease?.holder?.device === PHONE.device, body.message)
  const read = await fetch(`${base}/state`, { headers: auth })
  check('lease: reads go on (GET /state)', read.status === 200, read.status)
  // (not POST /pair: a wrong code would count against the pairing section's limit)
  const devLog = await fetch(`${base}/dev/logs`, { method: 'POST', headers: { ...auth, 'content-type': 'text/plain' }, body: '\n' })
  check("lease: the developer's files are no data write (POST /dev/logs answers as ever)", devLog.status === 400, devLog.status)
  const tool: any = await client.callTool({ name: 'publish_gloss', arguments: { word: 'miza', gloss: 'table' } })
  check("lease: the tutor's writing tools fail, saying why", tool.isError === true && /Nothing written\. Lani's data isn't this node's to write now/.test(tool.content?.[0]?.text ?? ''), tool.content?.[0]?.text)
  const list: any = await client.callTool({ name: 'list_modules', arguments: {} })
  check('lease: its reading tools work', !list.isError, list.content?.[0]?.text?.slice(0, 200))

  // --- handed over and not taken yet: nobody writes --------------------------------------------------------
  writeFileSync(leaseFile, JSON.stringify(offered(PHONE, NODE)))
  check('lease: handed over to the phone, the node still refuses writes', (await level()).status === 423)

  // --- the node holds it again ---------------------------------------------------------------------------
  writeFileSync(leaseFile, JSON.stringify(held(NODE)))
  check("lease: the node's own lease lets the app write", (await level()).status !== 423)
  writeFileSync(leaseFile, '{broken')
  check("lease: a lease that can't be read lets nobody write", (await level()).status === 423)
  rmSync(leaseFile)
  check('lease: without it again, writes go on', (await level()).status !== 423)

  // --- the rules: the app's file reads the same ------------------------------------------------------------
  const app = parseLease('{"schema":"lani.lease/v1","holder":{"side":"phone","device":"p_0123abcd","name":"Pixel 8","mode":"hosted"},"offer":null,"since":"2026-10-18T21:00:00Z","renewed":"2026-10-18T21:00:00Z","expires":"2026-11-01T21:00:00Z"}')
  check("lease: the app's lease file: the phone's, not the node's", leaseState(app, PHONE, Date.parse('2026-10-20T00:00:00Z')) === 'mine' && leaseState(app, NODE, Date.parse('2026-10-20T00:00:00Z')) === 'other')
  check('lease: run out after its time: takeable, not writable', leaseState(app, NODE, Date.parse('2026-11-02T00:00:00Z')) === 'expired')
  check('lease: a node with no lease writes; a phone finds it unclaimed', leaseState(null, NODE) === 'mine' && leaseState(null, PHONE) === 'unclaimed' && leaseState(null, PHONE, Date.now(), false) === 'free')
  check('lease: described in one line', describeLease(app) === 'the phone (Pixel 8) writes the data, since 2026-10-18 21:00:00 UTC', describeLease(app))

  // --- the guard alone: no lease writes on every side; a note that can't be read, or a .git that is a file, is none ----
  const g = tempDir('lani-smoke-guard-')
  const phoneGuard = new LeaseGuard(g, PHONE, {})
  const nodeGuard = new LeaseGuard(g, NODE, {})
  check('guard: without a lease the node writes, and a phone (no git) too', nodeGuard.refusal() === undefined && phoneGuard.refusal() === undefined)
  writeFileSync(join(g, '.git'), 'gitdir: /elsewhere\n')
  check('guard: a .git that is a file (a worktree) is no note', new LeaseGuard(g, NODE, {}).refusal() === undefined)
  rmSync(join(g, '.git'))
  mkdirSync(join(g, '.git'))
  writeFileSync(join(g, '.git/lani-lease-remote.json'), '{broken')
  check("guard: a note that can't be read is no note", new LeaseGuard(g, NODE, {}).refusal() === undefined)
  writeFileSync(join(g, '.git/lani-lease-remote.json'), JSON.stringify(held(PHONE)))
  const noted = new LeaseGuard(g, NODE, {}).refusal()
  check("guard: the repository's lease from a refused push counts", !!noted && noted.includes("a push of this node's was refused"), noted)
  check('guard: LANI_LEASE=ignore writes anyway', new LeaseGuard(g, NODE, { LANI_LEASE: 'ignore' }).refusal() === undefined)

  // --- the handover's QR code -----------------------------------------------------------------------------
  const dir = tempDir('lani-smoke-handover-')
  const key = loadBridgeKey(dir)
  const bundle = { v: 1, p: 'default', n: 'Ana', h: 'smoke-node', k: scalarOf(key), t: 'family-token-1234', r: 'ana/lani-data' }
  const at = Date.now() + 600_000
  const one = encodeHandover(bundle, { fingerprint: key.fingerprint, expiresAt: at })
  check('handover: the QR text is lani://handover with the key in it', /^lani:\/\/handover\?v=1&f=[0-9a-f]{32}&x=\d+&d=[\w-]+&k=[\w-]{43}$/.test(one.uri), one.uri.slice(0, 80))
  check('handover: small enough for a terminal QR code (under 420 characters)', one.uri.length < 420, one.uri.length)
  const back = decodeHandover(one.uri)
  check('handover: the bundle opens with its key', JSON.stringify(back.bundle) === JSON.stringify(bundle) && back.fingerprint === key.fingerprint)
  check('handover: the scalar is 32 bytes', Buffer.from(bundle.k, 'base64url').length === 32)
  const coded = encodeHandover(bundle, { fingerprint: key.fingerprint, expiresAt: at, withCode: true })
  check('handover: --code: a salt instead of the key, and a 12-letter code', !coded.uri.includes('&k=') && coded.uri.includes('&s=') && /^[A-HJ-NP-Z2-9]{12}$/.test(coded.code ?? ''), coded.code)
  check('handover: it opens with the typed code (any case, with dashes)', decodeHandover(coded.uri, { code: (coded.code ?? '').toLowerCase().replace(/(.{4})(?=.)/g, '$1-') }).bundle.t === bundle.t)
  const fails = (f: () => unknown) => {
    try {
      f()
      return false
    } catch {
      return true
    }
  }
  check('handover: not with a wrong code', fails(() => decodeHandover(coded.uri, { code: 'AAAAAAAAAAAA' })))
  check('handover: not with another fingerprint (it is bound to the ciphertext)', fails(() => decodeHandover(one.uri.replace(`f=${key.fingerprint}`, `f=${'0'.repeat(32)}`))))
  check('handover: not after its time', fails(() => decodeHandover(one.uri, { now: at + 1000 })))
  check('handover: GitHub remotes name their repository', githubRepo('https://github.com/fentas/lani-jan.git') === 'fentas/lani-jan' && githubRepo('git@github.com:fentas/lani-jan.git') === 'fentas/lani-jan' && githubRepo('ssh://git@github.com/a/b') === 'a/b' && githubRepo('/tmp/x.git') === undefined)

  // --- the town's key ---------------------------------------------------------------------------------------
  check("town key: without town-key.pem the town's key is the bridge's", loadTownKey(dir).fingerprint === key.fingerprint)
  const own = generateKeyPairSync('ec', { namedCurve: 'P-256' }).privateKey.export({ type: 'pkcs8', format: 'pem' }).toString()
  writeFileSync(join(dir, 'town-key.pem'), own, { mode: 0o600 })
  const town = loadTownKey(dir)
  check('town key: town-key.pem is the town\'s own, beside the bridge key', town.fingerprint !== key.fingerprint && loadBridgeKey(dir).fingerprint === key.fingerprint)
}
