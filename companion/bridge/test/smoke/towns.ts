// Smoke checks: towns that know each other (src/towns.ts, src/features/towns.ts, src/cli/town.ts). Three towns, each
// its own bridge, as on a family node: Jan's (the harness's bridge), Luka's (a child, friuli) and Mia's (friuli), on
// their own ports (LANI_SMOKE_TOWN_PORT and the next; by default the harness's port + 3 and + 4). Invitations from
// the app and accepting them, the CLI's links, the signatures between bridges, what a town shows (nothing private),
// the proxy for the app, and that a child's app can't invite or accept.
import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js'
import { readFileSync, readdirSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { loadBridgeKey } from '../../src/pairing'
import { H, createInvite, festivalDay, localDate, parseInvite, publicState, responseMessage, signRequest, verifySigned, type FestivalRule } from '../../src/towns'
import { auth, base, bridgeWhisper, channelEvents, check, client as janClient, culturesDir, curatedDir, dataDir, fam, modulesDir, port, scenesDir, tempDir } from './harness'
import { friendship } from './friendship'
import { visitActs } from './guests'
import { questions, questionsOffline } from './questions'
import { visits, visitsOffline } from './visits'

const cliDir = resolve(import.meta.dir, '../../src/cli')
const townPort = Number(process.env.LANI_SMOKE_TOWN_PORT ?? port + 3)

function cli(cmd: string[], env: Record<string, string>) {
  const p = Bun.spawnSync(cmd, { env: { ...process.env, ...env }, stdout: 'pipe', stderr: 'pipe' })
  return { code: p.exitCode, out: p.stdout.toString(), err: p.stderr.toString() }
}

const bearer = (t: string) => ({ authorization: `Bearer ${t}`, 'content-type': 'application/json' })

/**
 * A learner's bridge, started as their session's Claude Code would start it, with [token] as its app token ([env]: more
 * of its environment). What it sends its session is kept in [notes].
 */
async function startBridge(o: { profile: string; data: string; port: number; token: string; child?: boolean; culture: string; env?: Record<string, string> }) {
  const env: Record<string, string> = {}
  for (const [k, v] of Object.entries(process.env)) if (v !== undefined && !['LANI_BRIDGE_TOKEN', 'LANI_FAMILY_TOKEN'].includes(k)) env[k] = v
  Object.assign(env, {
    LANI_PROFILE: o.profile, LANI_DATA_DIR: o.data, LANI_BRIDGE_PORT: String(o.port), LANI_BRIDGE_TOKEN: o.token, LANI_CHILD: o.child ? '1' : '',
    LANI_CULTURE: o.culture, LANI_CULTURES_DIR: culturesDir, LANI_RHYTHM: 'off', ELEVENLABS_API_KEY: '', LANI_VOICE_DESIGN: 'off', LANI_PACKS_DIR: curatedDir,
    LANI_MODULES_DIR: modulesDir, LANI_SCENES_DIR: scenesDir, LANI_STT_URL: bridgeWhisper.url, LANI_GEPARD_URL: 'http://127.0.0.1:9',
    LANI_TOWN_FRIEND_CACHE_MS: '0',
  }, o.env ?? {})
  const notes: any[] = []
  const client = new Client({ name: `smoke-${o.profile}`, version: '0' })
  client.fallbackNotificationHandler = async n => void notes.push(n)
  await client.connect(new StdioClientTransport({ command: 'bun', args: [resolve(import.meta.dir, '../../src/index.ts')], env, stderr: 'inherit' }))
  await Bun.sleep(300)
  return { client, notes, base: `http://127.0.0.1:${o.port}`, auth: bearer(o.token) }
}

export default async function towns() {
  // --- three learners on one node --------------------------------------------------------------------
  const profilesDir = tempDir('lani-smoke-towns-')
  const env = { LANI_DATA_DIR: dataDir, LANI_BRIDGE_PORT: String(port), LANI_PROFILES_DIR: profilesDir, LANI_TAILNET_HOST: 'node.test.ts.net' }
  const add = (args: string[]) => cli(['bun', join(cliDir, 'profile.ts'), 'add', ...args], env)
  const a1 = add(['luka', '--name', 'Luka', '--target', 'it', '--base', 'sl', '--child', '--culture', 'friuli', '--port', String(townPort)])
  const a2 = add(['mia', '--name', 'Mia Novak', '--target', 'it', '--base', 'en', '--culture', 'friuli', '--port', String(townPort + 1)])
  check('towns: two more learners on the node (a child and an adult)', a1.code === 0 && a2.code === 0, a1.err + a2.err)
  const lukaData = join(profilesDir, 'luka/data')
  const miaData = join(profilesDir, 'mia/data')
  const luka = await startBridge({ profile: 'luka', data: lukaData, port: townPort, token: 'luka-town-token', child: true, culture: 'friuli' })
  // Mia's town takes three gifts a day from a town (./guests.ts checks the limit), and one help against an event (./friendship.ts)
  const mia = await startBridge({ profile: 'mia', data: miaData, port: townPort + 1, token: 'mia-town-token', culture: 'friuli', env: { LANI_TOWN_GIFTS_PER_DAY: '3', LANI_TOWN_AID_PER_DAY: '1' } })
  const janKey = loadBridgeKey(join(dataDir, 'app'))
  const lukaKey = loadBridgeKey(join(lukaData, 'app'))
  const miaKey = loadBridgeKey(join(miaData, 'app'))
  const town = (d: string) => JSON.parse(readFileSync(join(d, 'app/towns.json'), 'utf8'))
  try {
    // --- who a town is ---------------------------------------------------------------------------------
    const hello = await (await fetch(`${base}/town/hello`)).json()
    check("towns: GET /town/hello says the town's id (its key's fingerprint) and public key, without a token", hello.v === 1 && hello.id === janKey.fingerprint && hello.public_key === janKey.spki.toString('base64'), hello)
    check('towns: the app routes need the app token', (await fetch(`${base}/towns`)).status === 401 && (await fetch(`${base}/towns`, { headers: fam })).status === 403 && (await fetch(`${base}/towns/invite`, { method: 'POST', headers: fam })).status === 403)

    // --- an invitation from the app ------------------------------------------------------------------------
    const inv = await (await fetch(`${base}/towns/invite`, { method: 'POST', headers: auth })).json()
    const parsed = parseInvite(inv.link)
    check("towns: POST /towns/invite makes a one-time invitation: the town's URL, a code and its fingerprint", parsed.ok && parsed.invite.url === base && parsed.invite.code === inv.code && parsed.invite.fingerprint === janKey.fingerprint && /^[A-Z2-9]{12}$/.test(inv.code) && Date.parse(inv.expires_at) > Date.now() + 25 * 60_000, inv)
    check('towns: no invitation is stored in the clear', readdirSync(join(dataDir, 'app/town-invites')).every(f => !f.includes(inv.code) && !readFileSync(join(dataDir, 'app/town-invites', f), 'utf8').includes(inv.code)))
    const accept = (b: { base: string; auth: Record<string, string> }, body: unknown) => fetch(`${b.base}/towns/accept`, { method: 'POST', headers: b.auth, body: JSON.stringify(body) })

    // a link whose fingerprint isn't the inviter's: refused before the code is spent
    const forged = inv.link.replace(janKey.fingerprint, lukaKey.fingerprint)
    const wrong = await accept(mia, { invite: forged })
    check("towns: an invitation whose fingerprint isn't the town's key is refused", wrong.status === 409 && (await wrong.json()).code === 'not_that_town')
    check('towns: an invitation to the town itself is refused', (await accept({ base, auth }, { invite: inv.link })).status === 400)
    check('towns: something else than an invitation is a 400', (await accept(mia, { invite: 'fluent://pair?v=1' })).status === 400 && (await accept(mia, { code: 'X' })).status === 400)

    const ok = await accept(mia, { invite: inv.link })
    const okBody = await ok.json()
    check("towns: Mia's app accepts Jan's invitation: her bridge asks Jan's, checks the key and joins", ok.status === 200 && okBody.town?.id === janKey.fingerprint && okBody.town.learner && okBody.town.culture === 'primorska' && okBody.town.language === 'sl', okBody)
    const janTowns = town(dataDir)
    const miaTowns = town(miaData)
    const janLink = janTowns.links.find((l: any) => l.id === miaKey.fingerprint)
    const miaLink = miaTowns.links.find((l: any) => l.id === janKey.fingerprint)
    check('towns: … both ways: each keeps the other\'s id, public key, URL, when and how', janLink?.public_key === miaKey.spki.toString('base64') && janLink.url === mia.base && janLink.via === 'invite' && janLink.by === 'app' && janLink.display?.learner === 'Mia' && janLink.display.language === 'it' && miaLink?.url === base && miaLink.via === 'accept' && miaLink.by === 'app' && !!Date.parse(miaLink.linked_at), { janLink, miaLink })
    const raw = readFileSync(join(dataDir, 'app/towns.json'), 'utf8') + readFileSync(join(miaData, 'app/towns.json'), 'utf8')
    check('towns: towns.json holds no secrets (no private key, no token)', !raw.includes('PRIVATE') && !raw.includes('mia-town-token') && !raw.includes('smoke-token') && !raw.includes(inv.code))
    const again = await accept(mia, { invite: inv.link })
    check('towns: an invitation works once', again.status === 403 && (await again.json()).code === 'invalid_code')
    const late = createInvite(join(dataDir, 'app'), { by: 'app', ttlMs: -1_000 })
    const expired = await accept(mia, { code: late.code, url: base, fingerprint: janKey.fingerprint })
    check('towns: an expired invitation is a 410 (accepted as code, URL and fingerprint)', expired.status === 410 && (await expired.json()).code === 'expired_code')

    // --- a child's app neither invites nor accepts ------------------------------------------------------------
    const childInvite = await fetch(`${luka.base}/towns/invite`, { method: 'POST', headers: luka.auth })
    const childAccept = await accept(luka, { invite: (await (await fetch(`${base}/towns/invite`, { method: 'POST', headers: auth })).json()).link })
    check("towns: a child's app can't invite or accept (403): a parent links their town on the node", childInvite.status === 403 && (await childInvite.json()).code === 'child' && childAccept.status === 403, childInvite.status)
    check("towns: … and nothing was linked for the child", !(await (await fetch(`${luka.base}/towns`, { headers: luka.auth })).json()).towns.length)

    // --- the parent's shortcut: two towns of this node -----------------------------------------------------------
    const linked = cli(['bun', join(cliDir, 'town.ts'), 'link', 'default', 'luka'], env)
    check('towns: lani-town link default luka links the two towns both ways', linked.code === 0 && town(dataDir).links.some((l: any) => l.id === lukaKey.fingerprint && l.via === 'link' && l.by === 'cli' && l.url === luka.base) && town(lukaData).links.some((l: any) => l.id === janKey.fingerprint && l.url === base), linked.out + linked.err)
    const listed = cli(['bun', join(cliDir, 'town.ts'), 'list', '--profile', 'luka'], env)
    check("towns: lani-town list shows a profile's links", listed.code === 0 && listed.out.includes(janKey.fingerprint.slice(0, 8)) && /\bdefault\b/.test(listed.out) && listed.out.includes('a child'), listed.out + listed.err)

    // --- a village's public state --------------------------------------------------------------------------------
    const today = localDate()
    const born = localDate(Date.now() - 100 * 86_400_000)
    const village = {
      seed: 42, age: 'VAS', foundedOn: '2026-08-01', villagers: 3, resources: { FOOD: 777, WOOD: 778 }, fire: 55, morale: 61, help: 12,
      buildings: [{ id: 'b1', type: 'TENT', plot: -1 }, { id: 'b2', type: 'HUT', plot: 1, level: 2, builtAt: 5 }, { id: 'b3', type: 'FIELD', plot: 2, damaged: true }],
      residents: [
        { id: 'rosa', since: '2026-08-01' },
        { id: 'n1', since: born, name: 'Chiara Furlan', emoji: '👧', art: 'child2', voice: 'female', role: 'SECRET-ROLE', born, family: 'Furlan', parents: ['p1'] },
      ],
      visitor: { id: 'davide', on: today },
      sky: { on: today, scene: 'field', sky: { rain: 0.6, wind: 0.3, lightning: true } },
      projects: { mulino: 2 },
      quests: [{ id: 'q1', giver: 'Nonna Rosa', emoji: '👵', title: 'SECRET-QUEST', story: 's', skill: 'FOOD', reward: {} }],
      log: [{ at: 1, emoji: '📜', text: 'SECRET-CHRONICLE' }],
      chest: { goods: { miele: 3 }, tools: { rosa: { tier: 1 } } },
      bonds: { rosa: { points: 99, memories: [{ sl: 'SECRET-MEMORY' }] } },
      stats: { questsDone: 7 },
      surprise: { on: today, kind: 'letter', who: 'rosa' },
      land: { kind: 'hills', seed: -12345 },
    }
    const put = await fetch(`${mia.base}/game`, { method: 'PUT', headers: mia.auth, body: JSON.stringify({ rev: 0, state: village }) })
    check("towns: Mia's village is saved", put.ok)

    // Signed requests, as Jan's bridge asks Mia's.
    const signedGet = (headers: Record<string, string>) => fetch(`${mia.base}/town/public`, { headers })
    const good = signRequest(janKey, { method: 'GET', path: '/town/public', to: miaKey.fingerprint })
    const res = await signedGet(good)
    const text = await res.text()
    const pub = JSON.parse(text)
    check("towns: GET /town/public answers a linked town's signed request, signed back", res.status === 200 && verifySigned(miaKey.spki.toString('base64'), responseMessage(good[H.nonce], text), res.headers.get(H.signature) ?? ''), text.slice(0, 200))
    check('towns: … the town, the day, the age, the buildings, the landmarks', pub.v === 1 && pub.town.id === miaKey.fingerprint && pub.town.learner === 'Mia' && pub.town.culture === 'friuli' && pub.town.language === 'it' && pub.town.name === 'Il mio villaggio' && pub.date === today && pub.age === 'VAS' && pub.founded_on === '2026-08-01' && pub.villagers === 3 && JSON.stringify(pub.buildings) === JSON.stringify([{ type: 'TENT', plot: -1, level: 1, damaged: false }, { type: 'HUT', plot: 1, level: 2, damaged: false }, { type: 'FIELD', plot: 2, level: 1, damaged: true }]) && pub.projects.mulino === 2, pub)
    check("towns: … who lives there (a cast member by the pack's name, a child born there as a child), today's visitor and sky", JSON.stringify(pub.residents) === JSON.stringify([{ id: 'rosa', name: 'Nonna Rosa', stage: 'adult' }, { id: 'n1', name: 'Chiara Furlan', stage: 'child', emoji: '👧', art: 'child2' }]) && pub.visitor?.name === 'Pastore Davide' && pub.sky?.scene === 'field' && pub.sky.rain === 0.6 && pub.sky.lightning === true && pub.sky.snow === 0, pub)
    const keys = Object.keys(pub).sort().join(',')
    check("towns: … the land it stands on: its landscape and its map's own number (not the village's seed)", pub.land?.kind === 'hills' && pub.land.map === -12345, pub.land)
    check('towns: … and nothing private: only the public fields, none of the resources, requests, chest, friendships, chronicle, stats or tokens', keys === 'age,buildings,date,festival,founded_on,land,projects,residents,sky,town,v,villagers,visitor' && !/SECRET|miele|questsDone|mia-town-token|"(seed|morale|help|resources|fire|bonds|chest|log|quests|stats|surprise|role|family|parents|since|born|builtAt|id":"b\d)"/.test(text), keys)

    const stale = signRequest(janKey, { method: 'GET', path: '/town/public', to: miaKey.fingerprint, now: Date.now() - 10 * 60_000 })
    const tampered = { ...signRequest(janKey, { method: 'GET', path: '/town/public', to: miaKey.fingerprint }) }
    tampered[H.signature] = signRequest(janKey, { method: 'GET', path: '/town/other', to: miaKey.fingerprint })[H.signature]
    const stranger = loadBridgeKey(tempDir('lani-smoke-stranger-'))
    const codeOf = async (r: Response) => [r.status, (await r.json()).code]
    const results = {
      replayed: await codeOf(await signedGet(good)),
      stale: await codeOf(await signedGet(stale)),
      badSignature: await codeOf(await signedGet(tampered)),
      unlinked: await codeOf(await signedGet(signRequest(stranger, { method: 'GET', path: '/town/public', to: miaKey.fingerprint }))),
      misaddressed: await codeOf(await signedGet(signRequest(janKey, { method: 'GET', path: '/town/public', to: lukaKey.fingerprint }))),
      unsigned: await codeOf(await fetch(`${mia.base}/town/public`)),
      appToken: await codeOf(await fetch(`${mia.base}/town/public`, { headers: mia.auth })),
    }
    const want: Record<string, string> = { replayed: 'replayed', stale: 'stale', badSignature: 'bad_signature', unlinked: 'unlinked', misaddressed: 'misaddressed', unsigned: 'unsigned', appToken: 'unsigned' }
    check('towns: a replayed nonce, an old time, a bad signature, an unlinked town, a request for another town, no signature (the app token is none): 401', Object.entries(results).every(([k, [s, c]]) => s === 401 && c === want[k]), results)

    // --- the proxy: the app asks its own bridge ---------------------------------------------------------------------
    const list = await (await fetch(`${base}/towns`, { headers: auth })).json()
    const miaRow = list.towns.find((t: any) => t.id === miaKey.fingerprint)
    const lukaRow = list.towns.find((t: any) => t.id === lukaKey.fingerprint)
    check("towns: GET /towns lists Jan's linked towns, each asked for its state now", list.self?.id === janKey.fingerprint && list.self.can_invite === true && list.towns.length === 2 && miaRow?.status === 'ok' && miaRow.age === 'VAS' && miaRow.villagers === 3 && miaRow.buildings.HUT === 1 && miaRow.learner === 'Mia' && miaRow.language === 'it' && lukaRow?.status === 'ok' && lukaRow.via === 'link' && lukaRow.age === null, list)
    const proxied = await fetch(`${base}/towns/${miaKey.fingerprint}/public`, { headers: auth })
    const proxiedBody = await proxied.json()
    check("towns: GET /towns/:id/public passes the town's public state on to the app", proxied.status === 200 && JSON.stringify(proxiedBody.residents) === JSON.stringify(pub.residents) && proxiedBody.town.id === miaKey.fingerprint && proxiedBody.age === 'VAS' && JSON.stringify(proxiedBody.land) === JSON.stringify({ kind: 'hills', map: -12345 }), proxiedBody)
    check('towns: … an unknown town is a 404', (await fetch(`${base}/towns/${'0'.repeat(32)}/public`, { headers: auth })).status === 404)
    const lukaList = await (await fetch(`${luka.base}/towns`, { headers: luka.auth })).json()
    check("towns: a child's app sees their linked towns, and that it can't invite", lukaList.self?.child === true && lukaList.self.can_invite === false && lukaList.towns.length === 1 && lukaList.towns[0].id === janKey.fingerprint && lukaList.towns[0].status === 'ok' && lukaList.towns[0].age === 'TABOR', lukaList)

    // --- visiting (M2): the people and places of a linked town, through the visitor's bridge (./visits.ts) ------------------
    const janTown = { base, auth, key: janKey, data: dataDir }
    const lukaTown = { ...luka, key: lukaKey, data: lukaData }
    await visits(janTown, { ...mia, key: miaKey, data: miaData }, lukaTown)

    // --- things to do on a visit (M3): requests, gifts, the market, talks, guests' dialogs (./guests.ts) ----------------------
    await visitActs(janTown, { ...mia, key: miaKey, data: miaData }, lukaTown)

    // --- friendship between towns (M5): the level both agree on, rates, help on an event, the shared feast, moves (./friendship.ts)
    await friendship(janTown, { ...mia, key: miaKey, data: miaData }, lukaTown)

    // --- the host takes part (M6): a guest's question and the host's answer, each one's tutor grading it (./questions.ts) ---
    const janPeer = { ...janTown, notes: channelEvents, client: janClient }
    await questions(janPeer, { ...mia, key: miaKey, data: miaData }, lukaTown)

    // --- the CLI on the node: an invitation to the child's town, accepted for Mia ------------------------------------
    const made = cli(['bun', join(cliDir, 'town.ts'), 'invite', '--profile', 'luka', '--no-qr'], env)
    const link = /lani:\/\/town\?\S+/.exec(made.out)?.[0] ?? ''
    const took = cli(['bun', join(cliDir, 'town.ts'), 'accept', link, '--profile', 'mia'], env)
    check("towns: a parent invites to the child's town (lani-town invite --profile luka), and another town accepts on the node", made.code === 0 && link.includes(lukaKey.fingerprint) && took.code === 0 && town(miaData).links.some((l: any) => l.id === lukaKey.fingerprint && l.by === 'cli' && l.via === 'accept') && town(lukaData).links.some((l: any) => l.id === miaKey.fingerprint && l.by === 'cli' && l.via === 'invite'), made.out + made.err + took.out + took.err)
    check('towns: … the invitation works once from the CLI too', cli(['bun', join(cliDir, 'town.ts'), 'accept', link, '--profile', 'default'], env).code !== 0)

    // --- letting go ------------------------------------------------------------------------------------------------------
    const unl = cli(['bun', join(cliDir, 'town.ts'), 'unlink', 'luka', '--profile', 'mia'], env)
    const afterUnlink = await fetch(`${luka.base}/town/public`, { headers: signRequest(miaKey, { method: 'GET', path: '/town/public', to: lukaKey.fingerprint }) })
    check('towns: lani-town unlink lets go both ways; the other town then answers 401', unl.code === 0 && !town(miaData).links.some((l: any) => l.id === lukaKey.fingerprint) && !town(lukaData).links.some((l: any) => l.id === miaKey.fingerprint) && afterUnlink.status === 401, unl.out + unl.err)
    // A town tells another it lets go (a signed POST /town/unlink), as a town of another node would be told.
    const body = '{}'
    const bye = await fetch(`${base}/town/unlink`, { method: 'POST', headers: { ...signRequest(miaKey, { method: 'POST', path: '/town/unlink', body, to: janKey.fingerprint }), 'content-type': 'application/json' }, body })
    const refused = await fetch(`${mia.base}/towns/${janKey.fingerprint}/public`, { headers: mia.auth })
    check("towns: POST /town/unlink: the asking town is let go; its app's bridge then hears 'refused' (502)", bye.status === 200 && !town(dataDir).links.some((l: any) => l.id === miaKey.fingerprint) && refused.status === 502 && (await refused.json()).code === 'refused')

    // --- wrong invitations: the limit ---------------------------------------------------------------------------------------
    const join_ = (code: string) => {
      const b = JSON.stringify({ v: 1, code, town: { id: stranger.fingerprint, public_key: stranger.spki.toString('base64'), url: 'http://127.0.0.1:9', display: { name: 'X' } } })
      return fetch(`${luka.base}/town/join`, { method: 'POST', headers: { ...signRequest(stranger, { method: 'POST', path: '/town/join', body: b, to: lukaKey.fingerprint }), 'content-type': 'application/json' }, body: b })
    }
    const unsignedJoin = await fetch(`${luka.base}/town/join`, { method: 'POST', body: JSON.stringify({ v: 1, code: 'WRONGWRONG22', town: { id: stranger.fingerprint, public_key: stranger.spki.toString('base64'), url: 'http://127.0.0.1:9' } }) })
    const statuses: number[] = []
    for (let i = 0; i < 10; i++) statuses.push((await join_('WRONGWRONG22')).status)
    // the spent invitation the CLI tried above was the first wrong one: nine more, then every join waits
    check('towns: a join must be signed by the key it brings; ten wrong invitations in ten minutes, then every join waits', unsignedJoin.status === 401 && statuses.slice(0, 9).every(s => s === 403) && statuses[9] === 429, { unsigned: unsignedJoin.status, statuses })

    // --- the festival that is on (pure) ----------------------------------------------------------------------------------------
    const rules: FestivalRule[] = JSON.parse(readFileSync(join(culturesDir, 'friuli/festivals.json'), 'utf8')).festivals
    const on = (y: number, m: number, d: number) => publicState({ state: { festivals: { vendemmia: '2026-09-26' } }, town: { id: 'x', name: 'n', learner: 'l', culture: 'friuli', language: 'it' }, person: () => undefined, festivals: rules, now: new Date(y, m - 1, d, 12).getTime() }).festival
    const easter = on(2026, 4, 6)
    const harvest = on(2026, 9, 27)
    check('towns: the festival that is on: Easter a day late, the grape harvest (the last Saturday of September) celebrated, none on a plain day', easter?.id === 'pasqua' && easter.day === '2026-04-05' && easter.late === 1 && !easter.done && harvest?.id === 'vendemmia' && harvest.day === '2026-09-26' && harvest.done && on(2026, 7, 7) === null && festivalDay(rules.find(f => f.id === 'san_martino')!, 2026) === Date.UTC(2026, 10, 11) / 86_400_000, { easter, harvest })

    // --- a town goes away: Luka's bridge stops; Jan's app still sees its last state (./visits.ts) ------------------------------
    await luka.client.close()
    await Bun.sleep(200)
    await visitsOffline(janTown, lukaTown)
    await questionsOffline(janPeer, lukaTown)
  } finally {
    for (const b of [luka, mia]) await b.client.close().catch(() => {})
  }
}
