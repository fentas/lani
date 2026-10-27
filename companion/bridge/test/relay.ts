// End-to-end smoke of the relay (companion/relay; docs/plans/05-built/relay.md): the relay, and the towns' bridges as
// their sessions' Claude Code starts them, with nothing of a real learner:
// - Ana's town, self-hosted: its own URL is one the others can't reach (a private tailnet: LANI_TOWN_URL points at a
//   port nobody answers on), so they reach it only through the relay; its family page stays on its node;
// - Marta's town, hosted (the phone's bridge): no address of its own (LANI_TOWN_URL=relay), the family page through
//   the relay; the QA's fixture town (test/fixtures/dev-town.json: Friuli, a village with requests and a chest);
// - Cene's town, hosted too: two hosted towns.
// Through the relay: linking by invitation, the public state, a visit (people, places, requests, market), a request
// done, a gift (a guest), a trade, a friendship both agree on, a question and its answer, the family page's API (the
// page, settings, a challenge, a recording up and down), a backup put/get/list/delete; then Marta's town goes away (its
// last published state, a challenge that waits for it) and comes back (the challenge arrives). Last, a relay that lies:
// a fake one hands a bridge crafted mail (paths that a URL turns into another route, the app's routes, the learner's
// answer, a body from elsewhere), and the bridge answers none of it but the family page's own request.
// Run (in companion/bridge): bun test/relay.ts
// Env: LANI_RELAY_SMOKE_PORT the relay's port (default 18870); the towns' bridges take the next three, the fake relay
// and its town the two after.
import './isolate' // first: never this machine's lani.env
import '../src/env'
import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js'
import { randomBytes } from 'node:crypto'
import { Database } from 'bun:sqlite'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { RESERVED_PORTS, initLearnerData, portFree } from '../src/learners'
import { loadBridgeKey } from '../src/pairing'
import { parseInvite, localDate } from '../src/towns'
import { relayHeaders } from '../src/relay'
import { normalizeText } from '../src/family'

const projectDir = resolve(import.meta.dir, '../../..')
const relayPort = Number(process.env.LANI_RELAY_SMOKE_PORT ?? 18870)
const ports = { relay: relayPort, ana: relayPort + 1, marta: relayPort + 2, cene: relayPort + 3, fake: relayPort + 4, vid: relayPort + 5 }
for (const p of Object.values(ports)) {
  if (RESERVED_PORTS.has(p) || (p >= 8790 && p <= 8899)) throw new Error(`port ${p} is a learner's or reserved: LANI_RELAY_SMOKE_PORT picks another`)
  if (!portFree(p)) throw new Error(`port ${p} is taken: LANI_RELAY_SMOKE_PORT picks another`)
}
const relayUrl = `http://127.0.0.1:${ports.relay}`

let failures = 0
const check = (name: string, cond: unknown, detail?: unknown) => {
  console.log(`${cond ? '✅' : '❌'} ${name}`, cond ? '' : JSON.stringify(detail ?? '').slice(0, 1500))
  if (!cond) failures++
}

const root = mkdtempSync(join(tmpdir(), 'lani-relay-smoke-'))
const stops: (() => Promise<void> | void)[] = []
process.on('exit', () => rmSync(root, { recursive: true, force: true }))

// --- the relay -----------------------------------------------------------------------------------------------------------
const relayProc = Bun.spawn(['bun', resolve(projectDir, 'companion/relay/src/index.ts')], {
  env: { ...process.env, RELAY_HOST: '127.0.0.1', RELAY_PORT: String(ports.relay), RELAY_DATA_DIR: join(root, 'relay'), RELAY_ONLINE_MS: '3000', RELAY_TUNNEL_WAIT_MS: '8000' },
  stdout: 'inherit',
  stderr: 'inherit',
})
stops.push(() => relayProc.kill())

async function answers(url: string, ms: number): Promise<boolean> {
  const end = Date.now() + ms
  while (Date.now() < end) {
    try {
      if ((await fetch(url, { signal: AbortSignal.timeout(1_000) })).ok) return true
    } catch {}
    await Bun.sleep(200)
  }
  return false
}

// --- the towns -----------------------------------------------------------------------------------------------------------
type Town = { name: string; data: string; port: number; base: string; token: string; family: string; auth: Record<string, string>; notes: any[]; client: Client; id: string; env: Record<string, string> }

const fixture = JSON.parse(readFileSync(resolve(import.meta.dir, 'fixtures/dev-town.json'), 'utf8'))
/** The fixture's state with its days filled in ("@day": today), as test/dev-town.ts does. */
function withDays(v: unknown): unknown {
  if (typeof v === 'string') {
    const m = /^@day(?:([+-])(\d+))?$/.exec(v)
    return m ? localDate(Date.now() + (m[1] === '-' ? -1 : 1) * Number(m[2] ?? 0) * 86_400_000) : v
  }
  if (Array.isArray(v)) return v.map(withDays)
  if (v && typeof v === 'object') return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, withDays(x)]))
  return v
}

/** A learner's data (as lani-profile add makes it) and their bridge, started as their session's Claude Code would. */
function townData(name: string, learner: { name: string; target: string; base: string; culture: string }) {
  const data = join(root, name, 'data')
  initLearnerData(projectDir, data, { ...learner, child: false })
  return data
}

async function startTown(name: string, data: string, port: number, culture: string, extra: Record<string, string>, relay = relayUrl): Promise<Town> {
  const token = `${name}-relay-smoke-token`
  const family = `${name}-relay-smoke-family`
  const env: Record<string, string> = {}
  for (const [k, v] of Object.entries(process.env)) if (v !== undefined && !k.startsWith('LANI_') && k !== 'ELEVENLABS_API_KEY') env[k] = v
  Object.assign(env, {
    CLAUDE_PROJECT_DIR: projectDir, LANI_ENV_FILE: '/dev/null', LANI_PROFILE: name, LANI_DATA_DIR: data, LANI_BRIDGE_PORT: String(port),
    LANI_BRIDGE_TOKEN: token, LANI_FAMILY_TOKEN: family, LANI_CHILD: '', LANI_CULTURE: culture, LANI_RHYTHM: 'off', ELEVENLABS_API_KEY: '',
    LANI_VOICE_DESIGN: 'off', LANI_STT_URL: 'http://127.0.0.1:9', LANI_GEPARD_URL: 'http://127.0.0.1:9', LANI_TOWN_FRIEND_CACHE_MS: '0',
    LANI_RELAY_URL: relay, LANI_RELAY_POLL_S: '5', LANI_RELAY_PUBLISH_MS: '1000',
  }, extra)
  const notes: any[] = []
  const client = new Client({ name: `relay-smoke-${name}`, version: '0' })
  client.fallbackNotificationHandler = async n => void notes.push(n)
  await client.connect(new StdioClientTransport({ command: 'bun', args: [resolve(import.meta.dir, '../src/index.ts')], env, stderr: 'inherit' }))
  const base = `http://127.0.0.1:${port}`
  if (!(await answers(`${base}/health`, 30_000))) throw new Error(`${name}'s bridge doesn't answer on ${base}`)
  const id = loadBridgeKey(join(data, 'app')).fingerprint
  // its first poll makes it known to the relay
  if (relay === relayUrl && !(await answers(`${relayUrl}/v1/towns/${id}/town/hello`, 20_000))) throw new Error(`${name}'s town never reached the relay`)
  const t = { name, data, port, base, token, family, auth: { authorization: `Bearer ${token}`, 'content-type': 'application/json' }, notes, client, id, env }
  stops.push(() => client.close().catch(() => {}))
  return t
}

const get = async (t: Town, path: string) => {
  const r = await fetch(`${t.base}${path}`, { headers: t.auth })
  return { status: r.status, body: (await r.json().catch(() => null)) as any }
}
const post = async (t: Town, path: string, body: unknown = {}) => {
  const r = await fetch(`${t.base}${path}`, { method: 'POST', headers: t.auth, body: JSON.stringify(body) })
  return { status: r.status, body: (await r.json().catch(() => null)) as any }
}
const linksOf = (t: Town) => JSON.parse(readFileSync(join(t.data, 'app/towns.json'), 'utf8')).links as any[]
/** Waits until [cond] holds (or [ms] pass). */
async function until(cond: () => Promise<boolean> | boolean, ms = 15_000) {
  const end = Date.now() + ms
  while (Date.now() < end) {
    if (await cond()) return true
    await Bun.sleep(250)
  }
  return false
}

try {
  check('relay: GET /health', await answers(`${relayUrl}/health`, 15_000))

  const anaData = townData('ana', { name: 'Ana', target: 'sl', base: 'en', culture: 'primorska' })
  const martaData = townData('marta', { name: fixture.learner.name, target: fixture.learner.target, base: fixture.learner.base, culture: fixture.learner.culture })
  const ceneData = townData('cene', { name: 'Cene', target: 'sl', base: 'en', culture: 'primorska' })
  // Ana's own URL: a port nobody answers on (127.0.0.1:9), as a tailnet the others aren't on
  const ana = await startTown('ana', anaData, ports.ana, 'primorska', { LANI_TOWN_URL: 'http://127.0.0.1:9' })
  const marta = await startTown('marta', martaData, ports.marta, fixture.learner.culture, { LANI_TOWN_URL: 'relay' })
  const cene = await startTown('cene', ceneData, ports.cene, 'primorska', { LANI_TOWN_URL: 'relay' })
  console.log(`relay smoke: relay :${ports.relay}, Ana :${ports.ana} (${ana.id.slice(0, 8)}), Marta :${ports.marta} (${marta.id.slice(0, 8)}), Cene :${ports.cene} (${cene.id.slice(0, 8)})`)
  const put = await fetch(`${marta.base}/game`, { method: 'PUT', headers: marta.auth, body: JSON.stringify({ rev: 0, state: withDays(fixture.state) }) })
  check("relay: Marta's village is saved (the fixture town's)", put.ok)

  // --- linking through the relay -------------------------------------------------------------------------------------
  const inv = await post(ana, '/towns/invite')
  const parsed = parseInvite(inv.body?.link ?? '')
  check("relay: Ana's invitation carries her own URL and her relay address (r=)", parsed.ok && parsed.invite.url === 'http://127.0.0.1:9' && parsed.invite.relay === `${relayUrl}/v1/towns/${ana.id}` && inv.body.relay === parsed.invite.relay, inv.body)
  check("relay: … anyone can accept it (reach: relay), and its web link carries the relay address too", inv.body.reach === 'relay' && parseInvite(inv.body.web_link ?? '').ok && (parseInvite(inv.body.web_link) as any).invite.relay === parsed.invite.relay, inv.body)
  const acc = await post(marta, '/towns/accept', { invite: inv.body.link })
  check("relay: Marta's town accepts it: Ana's URL doesn't answer, so her relay address does (hello, then the join through the tunnel)", acc.status === 200 && acc.body.town?.id === ana.id && acc.body.town.learner === 'Ana', acc)
  const mToA = linksOf(marta).find(l => l.id === ana.id)
  const aToM = linksOf(ana).find(l => l.id === marta.id)
  check("relay: … both ways: Marta's town reaches Ana's by the relay; Ana's reaches Marta's (no address of its own) by the relay",
    mToA?.url === `${relayUrl}/v1/towns/${ana.id}` && !mToA.relay && aToM?.url === `${relayUrl}/v1/towns/${marta.id}` && aToM.via === 'invite' && aToM.display?.learner === fixture.learner.name, { mToA, aToM })
  const mInv = await post(marta, '/towns/invite')
  check("relay: a hosted town's invitation names only its relay address", parseInvite(mInv.body?.link ?? '').ok && parseInvite(mInv.body.link).ok && (parseInvite(mInv.body.link) as any).invite.url === `${relayUrl}/v1/towns/${marta.id}` && !(parseInvite(mInv.body.link) as any).invite.relay, mInv.body)
  const cAcc = await post(cene, '/towns/accept', { invite: mInv.body.link })
  check('relay: two hosted towns link (Cene accepts Marta\'s invitation)', cAcc.status === 200 && cAcc.body.town?.id === marta.id && linksOf(marta).some(l => l.id === cene.id), cAcc)

  // --- the public state ----------------------------------------------------------------------------------------------
  const aTowns = await get(ana, '/towns')
  const mRow = aTowns.body?.towns?.find((t: any) => t.id === marta.id)
  check("relay: Ana's GET /towns asks Marta's town through the relay: its state now (the village, its age, its people)", mRow?.status === 'ok' && mRow.age === 'VAS' && mRow.villagers === 6 && aTowns.body.self.relay === `${relayUrl}/v1/towns/${ana.id}`, aTowns.body)
  const mTowns = await get(marta, '/towns')
  check("relay: Marta's GET /towns reaches Ana's and Cene's towns (through the relay both)", mTowns.body?.towns?.length === 2 && mTowns.body.towns.every((t: any) => t.status === 'ok'), mTowns.body)

  // --- a visit -----------------------------------------------------------------------------------------------------------
  const people = await fetch(`${ana.base}/towns/${marta.id}/villagers`, { headers: ana.auth })
  const peopleText = await people.text()
  const peopleBody = JSON.parse(peopleText)
  check(`relay: a visit: Ana's bridge gets Marta's town's people (${Math.round(peopleText.length / 1024)} KB, in Italian) through the relay`, people.status === 200 && peopleBody.language === 'it' && peopleBody.villagers?.length > 3 && peopleBody.villagers.some((v: any) => v.id === 'rosa'), peopleText.slice(0, 300))
  const places = await get(ana, `/towns/${marta.id}/scenes`)
  check("relay: … and its places (scenes)", places.status === 200 && Array.isArray(places.body.scenes) && places.body.scenes.length > 0, { status: places.status, n: places.body?.scenes?.length, error: places.body?.error })
  const reqs = await get(ana, `/towns/${marta.id}/requests`)
  check("relay: … its people's open requests", reqs.status === 200 && reqs.body.requests?.some((q: any) => q.id === 'q-dev-rosa'), reqs.body)
  const market = await get(ana, `/towns/${marta.id}/market`)
  check("relay: … the goods its market can spare", market.status === 200 && market.body.wares?.miele === 3, market.body)

  // --- voices on a visit: Marta visits Ana's Slovene town, reached only through the relay ---------------------------------
  // a clip of Babica's in Ana's voice store (as her node made it), then Marta's app asks her bridge for the line
  const line = 'Dober dan, dobrodošla pri nas!'
  const clip = new Uint8Array([0x49, 0x44, 0x33, 4, 0, 0, ...new TextEncoder().encode(`relay-clip:${'x'.repeat(2_000)}`)])
  const voiceFile = `${'d'.repeat(40)}.mp3`
  writeFileSync(join(ana.data, 'app/voice/files', voiceFile), clip)
  const vdb = new Database(join(ana.data, 'app/voice/voice.db'))
  vdb.exec('PRAGMA busy_timeout = 5000')
  vdb.query('INSERT OR REPLACE INTO clips (key, norm, text, voice, engine, file, chars, created_at, source) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)').run(`grandma:${normalizeText(line)}`, normalizeText(line), line, 'grandma', 'elevenlabs', voiceFile, line.length, new Date().toISOString(), 'relay-smoke')
  vdb.close()
  const vs = await post(marta, `/towns/${ana.id}/voice/say`, { text: line, voice: 'grandma' })
  const got = vs.body?.url ? new Uint8Array(await (await fetch(`${marta.base}${vs.body.url}`, { headers: marta.auth })).arrayBuffer()) : new Uint8Array()
  check("relay: voices on a visit: Marta's app gets Ana's own clip of Babica's line through the relay, byte for byte, kept on Marta's node", vs.status === 200 && vs.body.from === 'host' && vs.body.voice === 'grandma' && Buffer.from(got).equals(Buffer.from(clip)), vs.body)
  const vp = await get(marta, `/towns/${ana.id}/voice/profiles`)
  check("relay: … and the town's cast (the learner's voice there by gender, its language) through the relay", vp.status === 200 && vp.body.language === 'sl' && ['female', 'male'].includes(vp.body.player), vp.body)
  const none = await fetch(`${marta.base}/towns/${ana.id}/voice/say`, { method: 'POST', headers: marta.auth, body: JSON.stringify({ text: 'Tega Ana nima posnetega.', voice: 'grandma' }) })
  check("relay: … a line Ana's town has no clip of, and no engine on Marta's node: 204 (the phone speaks it)", none.status === 204, none.status)

  // --- things to do there ----------------------------------------------------------------------------------------------
  const help = await post(ana, `/towns/${marta.id}/help`, { id: 'relay-help-0001', request: 'q-dev-rosa' })
  check("relay: a request done (Nonna Rosa's): +3 for Marta's town, a thank-you good for Ana", help.status === 200 && help.body.ok && help.body.amount === 3 && help.body.giver === 'Nonna Rosa', help.body)
  const n0 = marta.notes.length
  const gift = await post(ana, `/towns/${marta.id}/gift`, { id: 'relay-gift-0001', good: 'med', message: 'Ciao Marta! Un saluto dal Carso.' })
  await Bun.sleep(300)
  const guests = await get(marta, '/towns/guests')
  const told = marta.notes.slice(n0).filter(n => n.params?.meta?.kind === 'town_gift')
  check("relay: a guest: Ana's gift and message are in Marta's guest book, her tutor told (as untrusted content)", gift.status === 200 && guests.body.entries?.some((e: any) => e.kind === 'gift' && e.from.id === ana.id && e.gift.message.startsWith('Ciao Marta')) && told.length === 1 && String(told[0].params.content).includes('untrusted_town_content'), { gift: gift.body, guests: guests.body?.entries?.length, told: told.length })
  const deal = { id: 'relay-trade-0001', give: { res: { WOOD: 20 } }, get: { culture: 'friuli', goods: { miele: 1 } }, score: 8 }
  const trade = await post(ana, `/towns/${marta.id}/trade`, deal)
  const again = await post(ana, `/towns/${marta.id}/trade`, deal)
  const trades = (await get(marta, '/towns/guests')).body.entries.filter((e: any) => e.kind === 'trade')
  check('relay: a trade at the market (20 wood for a jar of honey, 8/10): once, however often it is sent', trade.status === 200 && trade.body.ok && again.body.duplicate === true && trades.length === 1 && trades[0].trade.gave.goods.miele === 1, { trade: trade.body, again: again.body, trades })
  const cTrade = await post(cene, `/towns/${marta.id}/trade`, { ...deal, id: 'relay-trade-cene-1' })
  check('relay: … and between the two hosted towns (Cene trades at Marta\'s market)', cTrade.status === 200 && cTrade.body.ok, cTrade.body)

  // --- a question and its answer ------------------------------------------------------------------------------------------
  const asked = await post(ana, `/towns/${marta.id}/questions`, { id: 'relay-ask-0001', text: 'Come si chiama il tuo cane?' })
  const received = (await get(marta, '/towns/questions')).body.received ?? []
  const q = received.find((x: any) => x.from.id === ana.id)
  check("relay: Ana asks Marta a question (in Italian): it reaches Marta's town", asked.status === 200 && asked.body.ok && q?.text === 'Come si chiama il tuo cane?', { asked: asked.body, received })
  const ans = await post(marta, '/towns/questions/answer', { key: q?.key, text: 'Si chiama Bobi.' })
  const back = (await get(ana, '/towns/questions')).body.asked?.find((x: any) => x.key === asked.body.key)
  check("relay: Marta's answer goes back to Ana's town (Marta's bridge reaches Ana's only through the relay)", ans.status === 200 && ans.body.sent === true && back?.answer?.text === 'Si chiama Bobi.', { ans: ans.body, back })

  // --- the friendship both agree on ------------------------------------------------------------------------------------------
  const fa = (await get(ana, '/towns/friendship')).body.towns?.find((t: any) => t.id === marta.id)
  const fm = (await get(marta, '/towns/friendship')).body.towns?.find((t: any) => t.id === ana.id)
  check("relay: friendship: both towns agree (each asks the other through the relay): Ana's visit, request, gift and trade; Marta's answer",
    fa?.status === 'ok' && fm?.status === 'ok' && fa.level === fm.level && fa.points === fm.points && fa.mine.visit === 1 && fa.mine.help === 1 && fa.mine.gift === 1 && fa.mine.trade === 1 && fa.theirs.answer === 1 && JSON.stringify(fa.mine) === JSON.stringify(fm.theirs),
    { fa: fa && { status: fa.status, level: fa.level, mine: fa.mine, theirs: fa.theirs }, fm: fm && { status: fm.status, level: fm.level, mine: fm.mine } })

  // --- learning progress between towns (features/progress.ts) through the relay ---------------------------------------------------
  const prog = await get(ana, `/towns/${marta.id}/progress?fresh=1`)
  check("relay: Ana's app gets Marta's progress through the relay: a summary between two adults' towns, no details", prog.status === 200 && prog.body.level === 'summary' && !!prog.body.summary && prog.body.details === undefined && prog.body.town?.id === marta.id, prog.body)
  // the tunnel, asked by someone who isn't a linked town (signed for the relay only, or not at all): the town gives nothing
  const tunnel = `${relayUrl}/v1/towns/${marta.id}/town/progress`
  const stranger = loadBridgeKey(mkdtempSync(join(root, 'stranger-')))
  const outerOnly = await fetch(tunnel, { headers: relayHeaders(stranger, 'GET', tunnel) })
  const bare = await fetch(tunnel)
  const [oText, bText] = [await outerOnly.text(), await bare.text()]
  check("relay: … a request for a town's progress without a linked town's signature gets nothing (the relay refuses it: 401 unsigned, 403 not the signer's)", bare.status === 401 && outerOnly.status === 403 && !oText.includes('summary') && !bText.includes('summary'), { outerOnly: [outerOnly.status, oText.slice(0, 120)], bare: [bare.status, bText.slice(0, 120)] })

  // --- the family page without a node (Marta's) ---------------------------------------------------------------------------------
  const fam = (tok: string, extra: Record<string, string> = {}) => ({ authorization: `Bearer ${tok}`, ...extra })
  const fapi = (path: string, init: RequestInit = {}) => fetch(`${relayUrl}/v1/family/${marta.id}${path}`, init)
  const page = await fetch(`${relayUrl}/family/${marta.id}/`)
  const html = await page.text()
  check("relay: the family page is served by the relay for Marta's town, its script and style from under its path", page.ok && html.includes(`/family/${marta.id}/app.js`) && !!page.headers.get('content-security-policy') && (await fetch(`${relayUrl}/family/${marta.id}/app.js`)).ok, html.slice(0, 200))
  await until(async () => (await fapi('/family/api/settings', { headers: fam(marta.family) })).status === 200, 10_000)
  const settings = await fapi('/family/api/settings', { method: 'PUT', headers: fam(marta.family, { 'content-type': 'application/json' }), body: JSON.stringify({ partner_name: 'Maja', partner_emoji: '💃' }) })
  const sBody = (await settings.json()) as any
  check("relay: the family page's settings through the relay (Marta's bridge answers: \"Maje\")", settings.status === 200 && sBody.partner_name === 'Maja' && sBody.from_label === 'Maje', sBody)
  check('relay: … a wrong family token is a 401 at the relay; the learner\'s answer route is not the family\'s (403)', (await fapi('/family/api/settings', { headers: fam('nope') })).status === 401 && (await fapi('/family/api/challenges/abc/answer', { method: 'POST', headers: fam(marta.family), body: '{}' })).status === 403)
  // a child's links that wait for the parent: the family page's routes go through the relay too (Marta's is an adult's town)
  const famTowns = await fapi('/family/api/towns', { headers: fam(marta.family) })
  const famDecide = await fapi('/family/api/towns/requests/lr_0123456789abcdef', { method: 'POST', headers: fam(marta.family, { 'content-type': 'application/json' }), body: JSON.stringify({ approve: true }) })
  check("relay: … the family page's friends (GET /family/api/towns, a decision) reach Marta's bridge through the relay, which answers (an adult's town: nothing waits, 403 not_a_child)", famTowns.status === 200 && ((await famTowns.json()) as any).child === false && famDecide.status === 403 && ((await famDecide.json()) as any).code === 'not_a_child', famDecide.status)
  const ch = await fapi('/family/api/challenges', { method: 'POST', headers: fam(marta.family, { 'content-type': 'application/json' }), body: JSON.stringify({ type: 'question', text: 'Kaj si danes jedla?' }) })
  const chBody = (await ch.json()) as any
  const mine = await get(marta, '/family/api/challenges')
  check("relay: a challenge from the family page reaches Marta's town (her app sees it)", ch.status === 200 && chBody.text === 'Kaj si danes jedla?' && mine.body?.some?.((c: any) => c.id === chBody.id), { ch: chBody, mine: mine.body })
  // a recording larger than a mailbox listing holds (a blob each way): an m4a header and 100 KB
  const m4a = new Uint8Array(100_000)
  m4a.set([0, 0, 0, 0x18, ...new TextEncoder().encode('ftypM4A ')])
  const up = await fapi(`/audio?${new URLSearchParams({ text: 'Dober dan!', speaker: 'Maja' })}`, { method: 'POST', headers: fam(marta.family, { 'content-type': 'audio/mp4' }), body: m4a })
  const upBody = (await up.json()) as any
  const idx = (await (await fapi('/audio/index', { headers: fam(marta.family) })).json()) as any
  const file = await fapi(`/audio/file/${upBody.file}`, { headers: fam(marta.family) })
  const bytes = new Uint8Array(await file.arrayBuffer())
  check('relay: a recording up through the relay (100 KB), listed, and back down byte for byte', up.status === 200 && idx['dober dan']?.[0]?.file === upBody.file && file.status === 200 && file.headers.get('content-type') === 'audio/mp4' && bytes.length === m4a.length, { up: upBody, n: bytes.length })
  const del = await fapi(`/audio/file/${upBody.file}`, { method: 'DELETE', headers: fam(marta.family) })
  check('relay: … and deleted', del.status === 200 && !((await (await fapi('/audio/index', { headers: fam(marta.family) })).json()) as any)['dober dan'])
  const anaFam = await fetch(`${relayUrl}/v1/family/${ana.id}/family/api/settings`, { headers: fam(ana.family) })
  check("relay: a self-hosted town's family page stays on its node (not on the relay: 404)", anaFam.status === 404 && ((await anaFam.json()) as any).code === 'no_family')

  // --- backups -------------------------------------------------------------------------------------------------------------------
  const anaKey = loadBridgeKey(join(ana.data, 'app'))
  const martaKey = loadBridgeKey(join(marta.data, 'app'))
  const blob = randomBytes(1024 * 1024)
  const backup = (key: typeof anaKey, method: string, path: string, body?: Uint8Array) => fetch(relayUrl + path, { method, headers: relayHeaders(key, method, relayUrl + path, body ?? ''), ...(body ? { body: body as BodyInit } : {}) })
  const bPut = await backup(anaKey, 'PUT', '/v1/backup/smoke-1.enc', blob)
  const bGet = await backup(anaKey, 'GET', '/v1/backup/smoke-1.enc')
  const bList = (await (await backup(anaKey, 'GET', '/v1/backup')).json()) as any
  const otherList = (await (await backup(martaKey, 'GET', '/v1/backup')).json()) as any
  check('relay: backup: put (1 MB), get it back byte for byte, list it; another key sees none', bPut.status === 200 && Buffer.from(await bGet.arrayBuffer()).equals(blob) && bList.length === 1 && bList[0].name === 'smoke-1.enc' && bList[0].size === blob.length && Array.isArray(otherList) && otherList.length === 0, { put: bPut.status, list: bList, otherList })
  const replay = await fetch(`${relayUrl}/v1/backup/smoke-1.enc`, { method: 'DELETE', headers: relayHeaders(anaKey, 'DELETE', `${relayUrl}/v1/backup/smoke-1.enc`) })
  const replayHeaders = relayHeaders(anaKey, 'GET', `${relayUrl}/v1/backup`)
  await fetch(`${relayUrl}/v1/backup`, { headers: replayHeaders })
  const replayed = await fetch(`${relayUrl}/v1/backup`, { headers: replayHeaders })
  check('relay: … delete it (then the list is empty); a request sent twice is refused (replayed)', replay.status === 200 && ((await (await backup(anaKey, 'GET', '/v1/backup')).json()) as any).length === 0 && replayed.status === 401 && ((await replayed.json()) as any).code === 'replayed')

  // --- Marta's town goes away, and comes back ------------------------------------------------------------------------------------
  await marta.client.close()
  await until(async () => {
    try {
      await fetch(`${marta.base}/health`, { signal: AbortSignal.timeout(300) })
      return false
    } catch {
      return true
    }
  }, 5_000)
  await Bun.sleep(3_500) // the relay's RELAY_ONLINE_MS: it no longer counts the town as there
  const away = await get(ana, `/towns/${marta.id}/public`)
  check("relay: Marta's town is away: Ana's app sees the state it last published on the relay (checked with its key)", away.status === 200 && !away.body.stale && away.body.town?.id === marta.id && away.body.age === 'VAS' && away.body.villagers === 6 && !!away.body.fetched_at, away.body)
  const read = await fapi('/family/api/challenges', { headers: fam(marta.family) })
  const waiting = await fapi('/family/api/challenges', { method: 'POST', headers: fam(marta.family, { 'content-type': 'application/json' }), body: JSON.stringify({ type: 'say', text: 'Dober tek!' }) })
  check('relay: … the family page: reading is a 504 (away); a challenge waits in the mailbox (202, queued)', read.status === 504 && waiting.status === 202 && ((await waiting.json()) as any).queued === true, { read: read.status, waiting: waiting.status })
  const back2 = await startTown('marta', martaData, ports.marta, fixture.learner.culture, { LANI_TOWN_URL: 'relay' })
  const arrived = await until(async () => ((await get(back2, '/family/api/challenges')).body ?? []).some?.((c: any) => c.text === 'Dober tek!'), 15_000)
  check('relay: … Marta\'s town is back: the waiting challenge reaches it', arrived)
  const live = await get(ana, '/towns')
  check("relay: … and Ana's app reaches it live again", live.body?.towns?.find((t: any) => t.id === marta.id)?.status === 'ok', live.body)

  // --- a relay that lies -----------------------------------------------------------------------------------------------------
  const vidFamily = 'vid-relay-smoke-family'
  const fam64 = (s: string) => Buffer.from(s).toString('base64')
  const auth = { authorization: `Bearer ${vidFamily}`, 'content-type': 'application/json' }
  const mailId = (c: string) => `m_${c.repeat(22)}`
  const crafted = [
    { id: mailId('a'), scope: 'family', request: { method: 'POST', path: '/audio/%2e%2e/pair', headers: auth }, body_size: 2, body: fam64('{}') },
    { id: mailId('b'), scope: 'family', request: { method: 'POST', path: '/audio/../pair', headers: auth }, body_size: 2, body: fam64('{}') },
    { id: mailId('c'), scope: 'town', request: { method: 'GET', path: '/towns', headers: {} }, body_size: 0 },
    { id: mailId('d'), scope: 'family', request: { method: 'GET', path: '/state', headers: auth }, body_size: 0 },
    { id: mailId('e'), scope: 'family', request: { method: 'POST', path: '/family/api/challenges/abc123/answer', headers: auth }, body_size: 2, body: fam64('{}') },
    { id: mailId('f'), scope: 'family', request: { method: 'POST', path: '/audio?text=x', headers: auth }, body_size: 100_000, body_url: '@127.0.0.1:9/elsewhere' },
    { id: mailId('g'), scope: 'admin', request: { method: 'GET', path: '/town/hello', headers: {} }, body_size: 0 },
    { id: mailId('h'), scope: 'family', request: { method: 'GET', path: '/family/api/settings', headers: auth }, body_size: 0 },
    // a town's progress: without a town's signature, through the family's scope, and the app's own route
    { id: mailId('i'), scope: 'town', request: { method: 'GET', path: '/town/progress', headers: {} }, body_size: 0 },
    { id: mailId('j'), scope: 'family', request: { method: 'GET', path: '/town/progress', headers: auth }, body_size: 0 },
    { id: mailId('k'), scope: 'family', request: { method: 'GET', path: `/towns/${'0'.repeat(32)}/progress`, headers: auth }, body_size: 0 },
  ].map(m => ({ kind: 'http', from: 'family:x', at: new Date().toISOString(), expires: new Date(Date.now() + 60_000).toISOString(), sender: null, ...m }))
  const answered = new Map<string, { status: number; body: any }>()
  let handed = false
  const fake = Bun.serve({
    hostname: '127.0.0.1',
    port: ports.fake,
    async fetch(req) {
      const u = new URL(req.url)
      if (u.pathname === '/v1/mailbox') {
        if (handed) {
          await Bun.sleep(1_000)
          return Response.json({ mail: [] })
        }
        handed = true
        return Response.json({ mail: crafted })
      }
      const m = /^\/v1\/mail\/(m_[a-z]+)\/reply$/.exec(u.pathname)
      if (m && req.method === 'POST') {
        const text = await req.text()
        answered.set(m[1], { status: Number(req.headers.get('x-lani-reply-status')), body: (() => { try { return JSON.parse(text) } catch { return text } })() })
        return Response.json({ ok: true })
      }
      if (req.method === 'PUT') return Response.json({ ok: true })
      return Response.json({ error: 'not found' }, { status: 404 })
    },
  })
  stops.push(() => void fake.stop(true))
  const vid = await startTown('vid', townData('vid', { name: 'Vid', target: 'sl', base: 'en', culture: 'primorska' }), ports.vid, 'primorska', { LANI_TOWN_URL: 'relay', LANI_FAMILY_TOKEN: vidFamily }, `http://127.0.0.1:${ports.fake}`)
  await until(() => answered.size >= crafted.length - 1, 15_000)
  const status = (c: string) => answered.get(mailId(c))
  const refused = ['a', 'b', 'c', 'd', 'e', 'g'].map(c => [c, status(c)?.status, status(c)?.body?.code] as const)
  check("relay: a lying relay: paths a URL turns into another route (%2e%2e, ..), the app's routes, the learner's answer, another scope: refused (403), never routed",
    refused.every(([, st, code]) => st === 403 && code === 'forbidden'), refused)
  check("relay: … a body from elsewhere than the relay's own message: not fetched, refused", status('f')?.status === 403, status('f'))
  check("relay: … a town's progress: unsigned (401), through the family's scope or the app's route (403): nothing of it", status('i')?.status === 401 && status('j')?.status === 403 && status('k')?.status === 403 && !JSON.stringify([status('i'), status('j'), status('k')]).includes('summary'), [status('i'), status('j'), status('k')])
  check("relay: … the family page's own request is answered (the token checked by the town)", status('h')?.status === 200 && status('h')?.body?.partner_name !== undefined, status('h'))
  const vidDevices = JSON.parse(await Bun.file(join(vid.data, 'app/devices.json')).text().catch(() => '{"devices":[]}'))
  check('relay: … and no phone was paired through it', !vidDevices.devices?.length, vidDevices)
} catch (e) {
  check(`relay smoke: ${(e as Error).stack ?? e}`, false)
} finally {
  for (const s of stops.reverse()) await s()
}
console.log(failures ? `\n${failures} check(s) failed` : '\nall relay checks passed')
process.exit(failures ? 1 : 0)
