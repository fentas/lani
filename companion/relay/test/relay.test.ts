// The relay's unit tests: every route in process (no port), a clock the tests move, fake towns that poll and answer.
// Run: cd companion/relay && bun test
import { afterAll, beforeEach, describe, expect, test } from 'bun:test'
import { generateKeyPairSync, sign, type KeyObject } from 'node:crypto'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { configFromEnv, type Config } from '../src/config'
import { createRelay, type Relay } from '../src/relay'
import { fingerprintOf, sha256hex, signedMessage, verifySig } from '../src/sign'

const temps: string[] = []
afterAll(() => {
  for (const d of temps) rmSync(d, { recursive: true, force: true })
})

type Key = { id: string; publicKey: string; privateKey: KeyObject }
function newKey(): Key {
  const { privateKey, publicKey } = generateKeyPairSync('ec', { namedCurve: 'P-256' })
  const spki = publicKey.export({ type: 'spki', format: 'der' }) as Buffer
  return { id: fingerprintOf(spki), publicKey: spki.toString('base64'), privateKey }
}

let clock = Date.now()
/** Each signed request a time of its own (as the bridge does: the clock, or one more than the last when it hasn't moved). */
let seq = 0
function relayWith(over: Partial<Config> = {}): Relay {
  const dir = mkdtempSync(join(tmpdir(), 'lani-relay-test-'))
  temps.push(dir)
  return createRelay({ ...configFromEnv({}), dataDir: dir, webDir: resolve(import.meta.dir, '../../bridge/web/family'), now: () => clock, ...over })
}

/** X-Lani-* for [key] over a request (the shared contract). */
function signed(key: Key, method: string, pathWithQuery: string, body: Uint8Array | string = '', time = clock + seq++): Record<string, string> {
  const sig = sign('sha256', Buffer.from(signedMessage(method, pathWithQuery, time, sha256hex(body))), key.privateKey).toString('base64')
  return { 'x-lani-key': key.publicKey, 'x-lani-time': String(time), 'x-lani-sig': sig }
}

const BASE = 'http://relay.test'
async function call(relay: Relay, key: Key | null, method: string, path: string, o: { body?: string | Uint8Array; headers?: Record<string, string>; time?: number } = {}) {
  const body = o.body ?? ''
  const headers = { ...(key ? signed(key, method, path, body, o.time) : {}), ...(o.headers ?? {}) }
  const res = await relay.fetch(new Request(BASE + path, { method, headers, ...(method === 'GET' || method === 'HEAD' ? {} : { body: body as BodyInit }) }))
  return res
}
const asJson = async (r: Response) => ({ status: r.status, body: (await r.json()) as any, relay: r.headers.get('x-lani-relay') })

/** Inner town headers as towns.ts signs them (only from and to matter to the relay). */
const inner = (from: Key, to: Key) => ({ 'x-lani-town-from': from.id, 'x-lani-town-to': to.id, 'x-lani-town-nonce': 'n'.repeat(22), 'x-lani-town-time': String(clock), 'x-lani-town-signature': 'sig' })

/** A town that polls its mailbox and answers each request with [answer]. */
function fakeTown(relay: Relay, key: Key, answer: (m: any) => { status?: number; headers?: Record<string, string>; body: string | Uint8Array } | null) {
  let stop = false
  const seen: any[] = []
  const loop = (async () => {
    while (!stop) {
      const r = await call(relay, key, 'GET', '/v1/mailbox?wait=1')
      if (r.status !== 200) {
        await Bun.sleep(20)
        continue
      }
      const { mail } = (await r.json()) as any
      for (const m of mail) {
        seen.push(m)
        const a = answer(m)
        if (!a) continue
        const path = `/v1/mail/${m.id}/reply`
        await call(relay, key, 'POST', path, { body: a.body, headers: { 'x-lani-reply-status': String(a.status ?? 200), 'x-lani-reply-headers': JSON.stringify(a.headers ?? { 'content-type': 'application/json' }) } })
      }
    }
  })()
  return { seen, stop: async () => { stop = true; await loop } }
}

beforeEach(() => {
  clock = Date.now()
})

describe('signatures', () => {
  test('a signed request is taken once; unsigned, a bad key, an old time, a changed body: 401 with a code', async () => {
    const relay = relayWith()
    const a = newKey()
    const ok = await call(relay, a, 'GET', '/v1/mailbox')
    expect(ok.status).toBe(200)
    const headers = signed(a, 'GET', '/v1/mailbox')
    const first = await relay.fetch(new Request(BASE + '/v1/mailbox', { headers }))
    const again = await relay.fetch(new Request(BASE + '/v1/mailbox', { headers }))
    expect(first.status).toBe(200)
    expect((await asJson(again)).body.code).toBe('replayed')
    expect((await asJson(await call(relay, null, 'GET', '/v1/mailbox'))).body.code).toBe('unsigned')
    expect((await asJson(await call(relay, null, 'GET', '/v1/mailbox', { headers: { ...signed(a, 'GET', '/v1/mailbox'), 'x-lani-key': 'AAAA' } }))).body.code).toBe('bad_key')
    expect((await asJson(await call(relay, a, 'GET', '/v1/mailbox', { time: clock - 6 * 60_000 }))).body.code).toBe('stale')
    const tampered = await relay.fetch(new Request(BASE + '/v1/towns/' + a.id + '/readers', { method: 'PUT', headers: signed(a, 'PUT', `/v1/towns/${a.id}/readers`, '{"readers":[]}'), body: '{"readers":["' + a.id + '"]}' }))
    expect((await asJson(tampered)).body.code).toBe('bad_signature')
    // an RSA or another curve's key is no town key
    const rsa = generateKeyPairSync('rsa', { modulusLength: 1024 }).publicKey.export({ type: 'spki', format: 'der' }).toString('base64')
    expect((await asJson(await call(relay, null, 'GET', '/v1/mailbox', { headers: { ...signed(a, 'GET', '/v1/mailbox'), 'x-lani-key': rsa } }))).body.code).toBe('bad_key')
  })

  test('the rate limit per key: 429 with retry-after', async () => {
    const relay = relayWith({ ratePerMin: 60, rateBurst: 3 })
    const a = newKey()
    const statuses = []
    for (let i = 0; i < 4; i++) statuses.push((await call(relay, a, 'GET', `/v1/mailbox?i=${i}`)).status)
    expect(statuses).toEqual([200, 200, 200, 429])
    const r = await call(relay, a, 'GET', '/v1/mailbox?i=9')
    expect(r.headers.get('retry-after')).toBeTruthy()
    expect((await call(relay, newKey(), 'GET', '/v1/mailbox')).status).toBe(200)
  })
})

describe('public state', () => {
  test('a town publishes its state; its readers fetch it with its own signature; others may not', async () => {
    const relay = relayWith()
    const town = newKey()
    const reader = newKey()
    const stranger = newKey()
    const state = JSON.stringify({ v: 1, town: { id: town.id, name: 'Moja vas' }, age: 'VAS' })
    const path = `/v1/towns/${town.id}/public`
    expect((await call(relay, stranger, 'PUT', path, { body: state })).status).toBe(403)
    expect((await call(relay, town, 'PUT', path, { body: state })).status).toBe(200)
    expect((await call(relay, town, 'PUT', path, { body: 'not json' })).status).toBe(400)
    expect((await call(relay, reader, 'GET', path)).status).toBe(403)
    expect((await call(relay, town, 'PUT', `/v1/towns/${town.id}/readers`, { body: JSON.stringify({ readers: [reader.id] }) })).status).toBe(200)
    const got = await call(relay, reader, 'GET', path)
    const text = await got.text()
    expect(got.status).toBe(200)
    expect(text).toBe(state)
    const msg = signedMessage('PUT', got.headers.get('x-lani-publisher-path')!, got.headers.get('x-lani-publisher-time')!, sha256hex(text))
    expect(verifySig(town.publicKey, msg, got.headers.get('x-lani-publisher-sig')!)).toBe(true)
    expect(Date.parse(got.headers.get('x-lani-published-at')!)).toBeGreaterThan(0)
    expect((await call(relay, stranger, 'GET', path)).status).toBe(403)
    expect((await call(relay, reader, 'GET', `/v1/towns/${stranger.id}/public`)).status).toBe(404)
  })

  test("hello answers with the town's key once the town signed as itself", async () => {
    const relay = relayWith()
    const town = newKey()
    const hello = () => relay.fetch(new Request(`${BASE}/v1/towns/${town.id}/town/hello`))
    expect((await hello()).status).toBe(404)
    await call(relay, town, 'GET', '/v1/mailbox')
    const h = await asJson(await hello())
    expect(h.body).toEqual({ v: 1, id: town.id, public_key: town.publicKey, relay: true })
  })
})

describe('mailboxes', () => {
  test('deliver, fetch (a long poll), answer; the sender gets the answer; ack', async () => {
    const relay = relayWith()
    const town = newKey()
    const sender = newKey()
    await call(relay, town, 'GET', '/v1/mailbox')
    const polling = call(relay, town, 'GET', '/v1/mailbox?wait=5')
    await Bun.sleep(30)
    const t0 = Date.now()
    const msg = { request: { method: 'POST', path: '/town/gifts', headers: { ...inner(sender, town), 'content-type': 'application/json' }, body: '{"v":1,"id":"gift-00000001"}' } }
    const sent = await asJson(await call(relay, sender, 'POST', `/v1/towns/${town.id}/mail`, { body: JSON.stringify(msg) }))
    expect(sent.status).toBe(202)
    const got = await asJson(await polling)
    expect(Date.now() - t0).toBeLessThan(1000)
    expect(got.body.mail.length).toBe(1)
    const m = got.body.mail[0]
    expect(m.id).toBe(sent.body.id)
    expect(m.from).toBe(sender.id)
    expect(m.request).toMatchObject({ method: 'POST', path: '/town/gifts' })
    expect(m.request.headers['x-lani-town-from']).toBe(sender.id)
    expect(Buffer.from(m.body, 'base64').toString()).toBe(msg.request.body)
    // the sender's own signature of what it sent: the town can check it
    expect(verifySig(m.sender.key, signedMessage('POST', m.sender.path, m.sender.time, sha256hex(JSON.stringify(msg))), m.sender.sig)).toBe(true)
    // leased: not handed out again while the town answers it
    expect((await asJson(await call(relay, town, 'GET', '/v1/mailbox'))).body.mail.length).toBe(0)
    expect((await asJson(await call(relay, sender, 'GET', `/v1/mail/${m.id}/reply`))).status).toBe(202)
    await call(relay, town, 'POST', `/v1/mail/${m.id}/reply`, { body: '{"ok":true}', headers: { 'x-lani-reply-status': '200', 'x-lani-reply-headers': '{"content-type":"application/json","x-lani-town-signature":"abc","set-cookie":"no"}' } })
    const reply = await call(relay, sender, 'GET', `/v1/mail/${m.id}/reply`)
    expect(reply.status).toBe(200)
    expect(reply.headers.get('x-lani-reply-status')).toBe('200')
    expect(JSON.parse(reply.headers.get('x-lani-reply-headers')!)).toEqual({ 'content-type': 'application/json', 'x-lani-town-signature': 'abc' })
    expect(await reply.text()).toBe('{"ok":true}')
    expect((await call(relay, newKey(), 'GET', `/v1/mail/${m.id}/reply`)).status).toBe(404)
    // ack
    const second = await asJson(await call(relay, sender, 'POST', `/v1/towns/${town.id}/mail`, { body: JSON.stringify(msg) }))
    expect((await asJson(await call(relay, town, 'POST', '/v1/mailbox/ack', { body: JSON.stringify({ ids: [second.body.id] }) }))).body.acked).toBe(1)
    expect((await asJson(await call(relay, town, 'GET', '/v1/mailbox'))).body.mail.length).toBe(0)
  })

  test("a request must be the signer's and for that town; a mailbox has a limit; mail runs out after 14 days", async () => {
    const relay = relayWith({ mailboxMax: 2 })
    const town = newKey()
    const sender = newKey()
    const other = newKey()
    await call(relay, town, 'GET', '/v1/mailbox')
    const mail = (h: Record<string, string>) => call(relay, sender, 'POST', `/v1/towns/${town.id}/mail`, { body: JSON.stringify({ request: { method: 'GET', path: '/town/public', headers: h } }) })
    expect((await asJson(await mail(inner(other, town)))).body.code).toBe('not_yours')
    expect((await asJson(await mail(inner(sender, other)))).body.code).toBe('misaddressed')
    expect((await asJson(await call(relay, sender, 'POST', `/v1/towns/${other.id}/mail`, { body: '{}' }))).body.code).toBe('unknown_town')
    expect((await mail(inner(sender, town))).status).toBe(202)
    expect((await mail(inner(sender, town))).status).toBe(202)
    expect((await asJson(await mail(inner(sender, town)))).body.code).toBe('mailbox_full')
    clock += 15 * 86_400_000
    expect(relay.sweep().mail).toBe(2)
    clock = Date.now()
    expect((await mail(inner(sender, town))).status).toBe(202)
  })

  test('a lease runs out: an unanswered message is handed out again', async () => {
    const relay = relayWith({ leaseMs: 1_000 })
    const town = newKey()
    const sender = newKey()
    await call(relay, town, 'GET', '/v1/mailbox')
    await call(relay, sender, 'POST', `/v1/towns/${town.id}/mail`, { body: JSON.stringify({ request: { method: 'GET', path: '/town/public', headers: inner(sender, town) } }) })
    expect((await asJson(await call(relay, town, 'GET', '/v1/mailbox'))).body.mail.length).toBe(1)
    expect((await asJson(await call(relay, town, 'GET', '/v1/mailbox'))).body.mail.length).toBe(0)
    clock += 2_000
    expect((await asJson(await call(relay, town, 'GET', '/v1/mailbox'))).body.mail.length).toBe(1)
  })
})

describe('the tunnel', () => {
  test("a request to /v1/towns/{id}/town/… gets the town's own answer; a large one as a blob both ways", async () => {
    const relay = relayWith({ inlineMaxBytes: 1024 })
    const town = newKey()
    const sender = newKey()
    const big = 'x'.repeat(300_000)
    const t = fakeTown(relay, town, m => {
      if (m.request.path === '/town/scenes') return { body: JSON.stringify({ big }), headers: { 'content-type': 'application/json', 'x-lani-town-signature': 'sig-of-town' } }
      if (m.request.path.startsWith('/town/villagers')) return { status: 200, body: JSON.stringify({ path: m.request.path, method: m.request.method }) }
      if (m.request.path === '/town/help') return { status: 409, body: JSON.stringify({ code: 'not_open' }) }
      return { status: 404, body: '{}' }
    })
    await Bun.sleep(50)
    const tunnel = (method: string, p: string, body?: string, from = sender) => call(relay, from, method, `/v1/towns/${town.id}${p}`, { body, headers: inner(from, town) })
    const v = await tunnel('GET', '/town/villagers?time=morning')
    expect(v.status).toBe(200)
    expect(v.headers.get('x-lani-relay')).toBe('tunnel')
    expect(await v.json()).toEqual({ path: '/town/villagers?time=morning', method: 'GET' })
    const s = await tunnel('GET', '/town/scenes')
    expect(s.headers.get('x-lani-town-signature')).toBe('sig-of-town')
    expect(((await s.json()) as any).big.length).toBe(300_000)
    const h = await tunnel('POST', '/town/help', '{"v":1,"id":"help-00000001"}')
    expect(h.status).toBe(409)
    expect(h.headers.get('x-lani-relay')).toBe('tunnel')
    // a large request body: the town fetches it on its own
    const large = JSON.stringify({ v: 1, id: 'gift-00000002', message: 'y'.repeat(5_000) })
    await tunnel('POST', '/town/gifts', large)
    const seen = t.seen.find(m => m.request.path === '/town/gifts')
    expect(seen.body).toBeUndefined()
    expect(seen.body_url).toBe(`/v1/mail/${seen.id}/body`)
    // signed by one key, the request inside from another town: refused
    const stranger = newKey()
    expect((await asJson(await call(relay, stranger, 'GET', `/v1/towns/${town.id}/town/public`, { headers: inner(sender, town) }))).body.code).toBe('not_yours')
    await t.stop()
  })

  test('a town that is not polling: 504 away at once; one that does not answer: 504, the request taken back', async () => {
    const relay = relayWith({ onlineMs: 100 })
    const town = newKey()
    const sender = newKey()
    await call(relay, town, 'GET', '/v1/mailbox')
    clock += 150
    const t0 = Date.now()
    const r = await call(relay, sender, 'GET', `/v1/towns/${town.id}/town/public`, { headers: inner(sender, town) })
    expect(r.status).toBe(504)
    expect(r.headers.get('x-lani-relay')).toBe('away')
    expect(Date.now() - t0).toBeLessThan(300)
    // a town that is there but never answers (it isn't polling for this one: its mail waits unfetched)
    clock = Date.now()
    await call(relay, town, 'GET', '/v1/mailbox')
    const r2 = await call(relay, sender, 'GET', `/v1/towns/${town.id}/town/public`, { headers: { ...inner(sender, town), 'x-lani-relay-wait': '600' } })
    expect(r2.status).toBe(504)
    expect(((await r2.json()) as any).code).toBe('away')
    expect((await asJson(await call(relay, town, 'GET', '/v1/mailbox'))).body.mail.length).toBe(0)
  })
})

describe('after the review', () => {
  test('a town polling is there, however short RELAY_ONLINE_MS is: the request is answered (online is asked before delivering)', async () => {
    const relay = relayWith({ onlineMs: 1 })
    const town = newKey()
    const sender = newKey()
    const t = fakeTown(relay, town, () => ({ body: '{"ok":true}' }))
    await Bun.sleep(30)
    clock += 50 // its poll began longer ago than RELAY_ONLINE_MS
    const r = await call(relay, sender, 'GET', `/v1/towns/${town.id}/town/public`, { headers: inner(sender, town) })
    expect(r.status).toBe(200)
    expect(r.headers.get('x-lani-relay')).toBe('tunnel')
    await t.stop()
  })

  test("an answer nobody waits for isn't kept (a tunnelled request given up); a delivered one's is", async () => {
    const relay = relayWith()
    const town = newKey()
    const sender = newKey()
    await call(relay, town, 'GET', '/v1/mailbox')
    const gaveUp = call(relay, sender, 'GET', `/v1/towns/${town.id}/town/public`, { headers: { ...inner(sender, town), 'x-lani-relay-wait': '500' } })
    await Bun.sleep(50)
    const m = (await asJson(await call(relay, town, 'GET', '/v1/mailbox'))).body.mail[0]
    expect((await gaveUp).status).toBe(504)
    const late = await asJson(await call(relay, town, 'POST', `/v1/mail/${m.id}/reply`, { body: 'x'.repeat(1000) }))
    expect(late.body).toEqual({ ok: true, kept: false })
    expect(relay.store.reply(m.id)).toBeUndefined()
    expect(relay.store.mail(m.id)).toBeUndefined()
  })

  test("a poll doesn't load the bodies it only lists (a recording is fetched on its own)", async () => {
    const relay = relayWith({ inlineMaxBytes: 100 })
    const town = newKey()
    const sender = newKey()
    await call(relay, town, 'GET', '/v1/mailbox')
    await call(relay, sender, 'POST', `/v1/towns/${town.id}/mail`, { body: JSON.stringify({ request: { method: 'POST', path: '/town/gifts', headers: inner(sender, town), body: 'z'.repeat(500) } }) })
    const leased = relay.store.lease(town.id, 10, clock + 1, clock + 60_000, 100)
    expect(leased.length).toBe(1)
    expect(leased[0].body).toBeNull()
    expect(leased[0].size).toBe(500)
  })

  test('backups: a broken escape in a name is a 400; over the quota or the relay\'s room before the body is read; uploads at once', async () => {
    const relay = relayWith({ backupQuotaBytes: 1000, storeMaxBytes: 5000 })
    const a = newKey()
    expect((await call(relay, a, 'PUT', '/v1/backup/%E0%A4%A', { body: 'x' })).status).toBe(400)
    // a body announced over the key's quota: refused before it is read (the signature over a body never sent)
    const r = await relay.fetch(new Request(`${BASE}/v1/backup/big`, { method: 'PUT', headers: { ...signed(a, 'PUT', '/v1/backup/big', 'never'), 'content-length': '2000' }, body: 'x'.repeat(2000) }))
    expect(((await r.json()) as any).code).toBe('quota')
    const b = newKey()
    const filled = relayWith({ storeMaxBytes: 10 })
    expect((await asJson(await call(filled, b, 'PUT', '/v1/backup/one', { body: 'x'.repeat(50) }))).body.code).toBe('full')
    expect((await asJson(await call(relay, null, 'PUT', '/v1/backup/one', { body: 'x' }))).body.code).toBe('unsigned')
  })
})

describe('the family page', () => {
  const token = 'family-token-1234567890'

  test('the page is served for a town, its script and style from under its path', async () => {
    const relay = relayWith()
    const id = newKey().id
    const page = await relay.fetch(new Request(`${BASE}/family/${id}/`))
    const html = await page.text()
    expect(page.status).toBe(200)
    expect(page.headers.get('content-security-policy')).toContain("default-src 'self'")
    expect(html).toContain(`/family/${id}/app.js`)
    expect(html).toContain(`/family/${id}/style.css`)
    expect((await relay.fetch(new Request(`${BASE}/family/${id}/app.js`))).headers.get('content-type')).toContain('javascript')
    expect((await relay.fetch(new Request(`${BASE}/family/${id}/secret.json`))).status).toBe(404)
  })

  test("its API goes through the town's mailbox for the token the town registered; what waits while it is away", async () => {
    const relay = relayWith({ onlineMs: 200 })
    const town = newKey()
    const api = (method: string, rest: string, o: { tok?: string; body?: string; headers?: Record<string, string> } = {}) =>
      relay.fetch(new Request(`${BASE}/v1/family/${town.id}/${rest}`, { method, headers: { ...(o.tok === undefined ? { authorization: `Bearer ${token}` } : o.tok ? { authorization: `Bearer ${o.tok}` } : {}), ...(o.headers ?? {}) }, ...(o.body ? { body: o.body } : {}) }))
    expect((await asJson(await api('GET', 'family/api/settings'))).body.code).toBe('no_family')
    expect((await call(relay, town, 'PUT', `/v1/towns/${town.id}/family`, { body: JSON.stringify({ token_sha256: sha256hex(token) }) })).status).toBe(200)
    expect((await api('GET', 'family/api/settings', { tok: 'wrong' })).status).toBe(401)
    expect((await api('GET', 'family/api/settings', { tok: '' })).status).toBe(401)
    expect((await api('POST', 'family/api/challenges/abc/answer', { body: '{}' })).status).toBe(403)
    expect((await api('GET', 'state')).status).toBe(404)
    const t = fakeTown(relay, town, m => ({ body: JSON.stringify({ path: m.request.path, auth: m.request.headers.authorization, scope: m.scope }) }))
    await Bun.sleep(50)
    const s = await asJson(await api('GET', 'family/api/settings'))
    expect(s.status).toBe(200)
    expect(s.body).toEqual({ path: '/family/api/settings', auth: `Bearer ${token}`, scope: 'family' })
    const up = await asJson(await api('POST', 'audio?text=Dober%20dan&speaker=Maja', { body: 'x'.repeat(100), headers: { 'content-type': 'audio/mp4' } }))
    expect(up.body.path).toBe('/audio?text=Dober%20dan&speaker=Maja')
    await t.stop()
    clock += 250
    // away: a read is a 504, a challenge waits in the mailbox (202) for the town
    expect((await api('GET', 'family/api/challenges')).status).toBe(504)
    const q = await asJson(await api('POST', 'family/api/challenges', { body: '{"text":"Kaj si jedel?"}', headers: { 'content-type': 'application/json' } }))
    expect(q.status).toBe(202)
    expect(q.body.queued).toBe(true)
    const later = await asJson(await call(relay, town, 'GET', '/v1/mailbox'))
    expect(later.body.mail.map((m: any) => m.request.path)).toEqual(['/family/api/challenges'])
  })

  test('CORS for the origins set, none for others', async () => {
    const relay = relayWith({ familyOrigins: ['https://lani.team'] })
    const id = newKey().id
    const pre = (origin: string) => relay.fetch(new Request(`${BASE}/v1/family/${id}/family/api/settings`, { method: 'OPTIONS', headers: { origin, 'access-control-request-method': 'GET' } }))
    const ok = await pre('https://lani.team')
    expect(ok.status).toBe(204)
    expect(ok.headers.get('access-control-allow-origin')).toBe('https://lani.team')
    expect(ok.headers.get('access-control-allow-headers')).toContain('authorization')
    const no = await pre('https://evil.example')
    expect(no.status).toBe(403)
    expect(no.headers.get('access-control-allow-origin')).toBeNull()
  })
})

describe('backups', () => {
  test('put, get, list, delete: scoped to the signing key', async () => {
    const relay = relayWith()
    const a = newKey()
    const b = newKey()
    const blob = new Uint8Array(200_000).map((_, i) => i % 251)
    const put = await asJson(await call(relay, a, 'PUT', '/v1/backup/daily-2026-10-18.enc', { body: blob }))
    expect(put.status).toBe(200)
    expect(put.body).toMatchObject({ name: 'daily-2026-10-18.enc', size: blob.length })
    const got = await call(relay, a, 'GET', '/v1/backup/daily-2026-10-18.enc')
    expect(got.status).toBe(200)
    expect(new Uint8Array(await got.arrayBuffer())).toEqual(blob)
    expect(got.headers.get('x-lani-backup-sha256')).toBe(sha256hex(blob))
    const list = await asJson(await call(relay, a, 'GET', '/v1/backup'))
    expect(list.body).toEqual([{ name: 'daily-2026-10-18.enc', size: blob.length, time: put.body.time }])
    expect((await asJson(await call(relay, b, 'GET', '/v1/backup'))).body).toEqual([])
    expect((await call(relay, b, 'GET', '/v1/backup/daily-2026-10-18.enc')).status).toBe(404)
    expect((await call(relay, b, 'DELETE', '/v1/backup/daily-2026-10-18.enc')).status).toBe(404)
    expect((await call(relay, a, 'DELETE', '/v1/backup/daily-2026-10-18.enc')).status).toBe(200)
    expect((await call(relay, a, 'GET', '/v1/backup/daily-2026-10-18.enc')).status).toBe(404)
    expect((await asJson(await call(relay, a, 'GET', '/v1/backup'))).body).toEqual([])
  })

  test('a changed body, a replay, too large, over the quota, a bad name: refused', async () => {
    const relay = relayWith({ backupMaxBytes: 1000, backupQuotaBytes: 1500, backupMaxCount: 5 })
    const a = newKey()
    const headers = signed(a, 'PUT', '/v1/backup/one', 'abc')
    expect((await relay.fetch(new Request(`${BASE}/v1/backup/one`, { method: 'PUT', headers, body: 'abd' }))).status).toBe(401)
    expect((await relay.fetch(new Request(`${BASE}/v1/backup/one`, { method: 'PUT', headers, body: 'abc' }))).status).toBe(200)
    expect((await asJson(await relay.fetch(new Request(`${BASE}/v1/backup/one`, { method: 'PUT', headers, body: 'abc' })))).body.code).toBe('replayed')
    expect((await call(relay, a, 'PUT', '/v1/backup/big', { body: new Uint8Array(1001) })).status).toBe(413)
    expect((await call(relay, a, 'PUT', '/v1/backup/two', { body: new Uint8Array(900) })).status).toBe(200)
    expect((await asJson(await call(relay, a, 'PUT', '/v1/backup/three', { body: new Uint8Array(900) }))).body.code).toBe('quota')
    // replacing one counts its new size only
    expect((await call(relay, a, 'PUT', '/v1/backup/two', { body: new Uint8Array(1000) })).status).toBe(200)
    expect((await call(relay, a, 'PUT', '/v1/backup/-x', { body: 'x' })).status).toBe(400)
    expect((await call(relay, a, 'PUT', '/v1/backup/.hidden', { body: 'x' })).status).toBe(400)
  })
})

test('health', async () => {
  const r = await asJson(await relayWith().fetch(new Request(`${BASE}/health`)))
  expect(r.body).toMatchObject({ ok: true, service: 'lani-relay' })
})
