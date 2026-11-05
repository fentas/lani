// Smoke checks: a family on a computer from a parent's device (src/family-members.ts, features/family-members.ts;
// docs/plans/05-built/leftovers-app.md). Jan's bridge adds a child and an adult through the app's routes: a profile as
// lani-profile add makes one, its bridge started by the one fixed command, the tailnet's way out, a one-time code with no key
// in it. What's left says itself. A real child's bridge refuses all of it, keeps its own rules and loses a device at once. Only
// members a parent looks after: another adult's devices are theirs. Fake lani-bridge and tailscale: nothing outside the test runs.
import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js'
import { chmodSync, readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { addRefusal, cultureOfTarget, looksAfter, memberId, memberUrl } from '../../src/family-members'
import { addLearner, entryDataDir, readRegistry, type ProfileEntry } from '../../src/learners'
import { Devices, redeemCode } from '../../src/pairing'
import { auth, base, check, culturesDir, dataDir, familyBin, familyToken, italianPacksDir, port, profilesDir, token } from './harness'

const repo = resolve(import.meta.dir, '../../../..')
const fake = (name: string, text: string) => {
  const p = join(familyBin, name)
  writeFileSync(p, text)
  chmodSync(p, 0o755)
}

const call = async (method: string, path: string, body?: unknown, headers: Record<string, string> = auth, at = base) => {
  const r = await fetch(`${at}${path}`, { method, headers, ...(body === undefined ? {} : { body: JSON.stringify(body) }) })
  return { status: r.status, body: (await r.json().catch(() => ({}))) as any }
}

export default async function familyMembers() {
  // --- the id, the culture, the address ----------------------------------------------------------------------------------
  check('a member: an id from the name', memberId('Žan Novak', () => false) === 'zan-novak' && memberId('  Ana-Marija ', () => false) === 'ana-marija' && memberId('Ana', x => x === 'ana') === 'ana-2' && memberId('李', () => false) === 'member' && memberId('1984', () => false) === 'member')
  check('a member: the id is never shell text', /^[a-z][a-z0-9-]{0,23}$/.test(memberId('$(rm -rf ~); `x` "y"', () => false)), memberId('$(rm -rf ~); `x` "y"', () => false))
  check('a member: the village of the language they learn', cultureOfTarget(culturesDir, 'it') === 'friuli' && cultureOfTarget(culturesDir, 'sl') === 'primorska' && cultureOfTarget(culturesDir, 'de') === 'kaernten' && cultureOfTarget(culturesDir, 'en') === 'lakeland')
  check("a member's address: the tailnet's, or what the node says, or none", memberUrl({ port: 8792, https_port: 8443 }, { LANI_TAILNET_HOST: 'node.example.ts.net' }) === 'https://node.example.ts.net:8443' && memberUrl({ port: 8792, https_port: 8443 }, { LANI_FAMILY_URL: 'https://lani.home.example:{https_port}/' }) === 'https://lani.home.example:8443' && memberUrl({ port: 8792, https_port: 8443 }, { LANI_FAMILY_URL: 'http://10.0.2.2:{port}' }) === 'http://10.0.2.2:8792')

  // --- fakes of what the computer has ------------------------------------------------------------------------------------
  const log = join(familyBin, 'calls.log')
  const noServe = join(familyBin, 'no-serve')
  const noBridge = join(familyBin, 'no-bridge')
  // lani-bridge start: answers on the member's port for a while, as the real one's service does
  fake(
    'lani-bridge',
    `#!/usr/bin/env bun
import { appendFileSync, existsSync, readFileSync } from 'node:fs'
appendFileSync(${JSON.stringify(log)}, JSON.stringify({ args: process.argv.slice(2), profile: process.env.LANI_PROFILE, data: process.env.LANI_DATA_DIR, token: process.env.LANI_BRIDGE_TOKEN, family: process.env.LANI_FAMILY_TOKEN, child: process.env.LANI_CHILD, port: process.env.LANI_BRIDGE_PORT }) + '\\n')
if (!existsSync(${JSON.stringify(noBridge)})) {
  const id = process.argv[process.argv.indexOf('--profile') + 1]
  const p = JSON.parse(readFileSync(${JSON.stringify(join(profilesDir, 'profiles.json'))}, 'utf8')).profiles.find(e => e.id === id)
  Bun.spawn(['bun', '-e', 'Bun.serve({ hostname: "127.0.0.1", port: ' + p.port + ', fetch: () => Response.json({ ok: true }) }); setTimeout(() => process.exit(0), 12000)'], { stdio: ['ignore', 'ignore', 'ignore'] }).unref()
}
`,
  )
  fake('tailscale', `#!/bin/sh\necho "tailscale $*" >> ${log}\n[ -e ${noServe} ] && exit 1\nexit 0\n`)

  // --- what a family token and a bad request get ---------------------------------------------------------------------------
  const family = { authorization: `Bearer ${familyToken}`, 'content-type': 'application/json' }
  const start = await call('GET', '/family/members')
  check('GET /family/members: the learner themselves first, and that a member can be added', start.status === 200 && start.body.members[0].self === true && start.body.members[0].name === 'Jan' && start.body.can_add === true, start)
  const before = readRegistry(profilesDir).profiles.length
  const refused = await Promise.all([
    call('GET', '/family/members', undefined, family),
    call('POST', '/family/members', { name: 'X', target: 'it', base: 'sl' }, family),
    call('POST', '/family/members', { name: 'X', target: 'it', base: 'sl' }, { 'content-type': 'application/json' }),
    call('POST', '/family/members', { name: 'X', target: 'it', base: 'it' }),
    call('POST', '/family/members', { name: '', target: 'it', base: 'sl' }),
    call('POST', '/family/members', { name: '<b>x</b>', target: 'it', base: 'sl' }),
    call('POST', '/family/members', { name: 'X', target: 'fr', base: 'sl' }),
  ])
  check('POST /family/members: not the family page, nobody unsigned, nothing but a name and two languages', refused.map(r => r.status).join() === '403,403,401,400,400,400,400' && readRegistry(profilesDir).profiles.length === before, refused.map(r => r.status))

  // --- a child, added ---------------------------------------------------------------------------------------------------------
  const zan = await call('POST', '/family/members', { name: 'Žan', target: 'it', base: 'sl', child: true })
  const q = new URL(zan.body.pair?.uri ?? 'lani://pair')
  const entry = readRegistry(profilesDir).profiles.find(p => p.id === 'zan') as ProfileEntry
  const zanData = entry ? entryDataDir(profilesDir, entry) : ''
  const zanApp = join(zanData, 'app')
  check('a child added from a parent\'s device: its profile, its bridge up, the left steps said', zan.status === 200 && zan.body.profile === 'zan' && zan.body.child === true && zan.body.up === true && entry?.added_by === 'default' && entry?.child === true && entry?.culture === 'friuli' && entry?.port >= port + 15 && zan.body.left.length === 1 && zan.body.left[0].what === 'tutor' && zan.body.left[0].command.includes('lani-session --profile zan'), zan)
  const learner = JSON.parse(readFileSync(join(zanData, 'learner-profile.json'), 'utf8')).learner
  check('its learner: as lani-profile add makes one, 15 minutes', learner.name === 'Žan' && learner.target_language_code === 'it' && learner.base_language_code === 'sl' && learner.current_level === 'A1' && learner.target_level === 'B2' && learner.daily_goal_minutes === 15, learner)
  const calls = readFileSync(log, 'utf8').trim().split('\n')
  const started = JSON.parse(calls.find(c => c.startsWith('{'))!)
  check('its bridge: the one fixed command, with none of the parent\'s learner, tokens or port', started.args.join(' ') === 'start --profile zan' && !started.profile && !started.data && !started.token && !started.family && !started.child && !started.port, started)
  check('the tailnet: lani-profile\'s own command, for this member\'s ports', calls.includes(`tailscale serve --bg --https=${entry.https_port} http://127.0.0.1:${entry.port}`), calls)
  // the code
  check('the QR holds no key and no token: a version, the address, a code, the profile, a fingerprint', q.protocol === 'lani:' && q.host === 'pair' && [...q.searchParams.keys()].sort().join() === 'c,f,p,u,v' && q.searchParams.get('u') === `https://smoke.example.ts.net:${entry.https_port}` && q.searchParams.get('p') === 'zan' && !zan.body.pair.uri.includes(token) && !zan.body.pair.uri.includes('sk-'), zan.body.pair)
  check('the code is the new learner\'s, and works once', redeemCode(zanApp, q.searchParams.get('c')!) === 'ok' && redeemCode(zanApp, q.searchParams.get('c')!) === 'invalid')
  const listed = await call('GET', '/family/members')
  check('the child on the list, with its bridge running', listed.body.members.some((m: any) => m.profile === 'zan' && m.child === true && m.up === true && m.self === false), listed)

  // --- what the computer won't do from here --------------------------------------------------------------------------------
  writeFileSync(noServe, '')
  writeFileSync(noBridge, '')
  const ana = await call('POST', '/family/members', { name: 'Ana', target: 'de', base: 'en', child: false })
  const anaEntry = readRegistry(profilesDir).profiles.find(p => p.id === 'ana') as ProfileEntry
  check('what can\'t be done from here says itself, with the command, and the member stays', ana.status === 200 && ana.body.up === false && ana.body.left.map((l: any) => l.what).join() === 'start,publish,tutor' && ana.body.left[0].command === 'companion/bin/lani-bridge start --profile ana' && ana.body.left[1].command === `tailscale serve --bg --https=${anaEntry.https_port} http://127.0.0.1:${anaEntry.port}` && !!anaEntry, ana)
  check('an adult added by a parent: looked after by them, and no town linked', looksAfter('default', anaEntry) && !ana.body.town_linked)

  // --- the limits ----------------------------------------------------------------------------------------------------------------
  const env = { LANI_PROFILES_DIR: profilesDir }
  check("the node's owner can turn it off; a computer takes so many", addRefusal(repo, { ...env, LANI_FAMILY_ADD: 'off' }) === 'off' && addRefusal(repo, { ...env, LANI_FAMILY_MAX: '1' }) === 'full' && addRefusal(repo, env) === undefined)

  // another adult, added on the computer: not this parent's to look after
  const other = addLearner(profilesDir, repo, 'mira', { name: 'Mira', target: 'sl', base: 'en', culture: 'primorska' }, { port: port + 17, httpsPort: 7443 })
  const miraApp = join(entryDataDir(profilesDir, other), 'app')
  const miraPhone = new Devices(miraApp).add('Mira telefon').device
  const off = await Promise.all([
    call('DELETE', `/family/members/mira/devices/${miraPhone.id}`),
    call('PUT', `/family/members/mira/devices/${miraPhone.id}/budget`, { daily_cap_usd: 0.5 }),
    call('POST', '/family/members/mira/pair'),
  ])
  const seen = await call('GET', '/family/members')
  check("another adult's devices are theirs: not on the list, not touched", off.map(r => r.status).join() === '404,404,404' && !seen.body.members.some((m: any) => m.profile === 'mira') && new Devices(miraApp).list().length === 1, off.map(r => r.body))

  // --- a child's real bridge -----------------------------------------------------------------------------------------------------
  const kidPort = port + 14
  const kidBase = `http://127.0.0.1:${kidPort}`
  const tablet = new Devices(zanApp).add('Tablica')
  const phone = new Devices(zanApp).add('Telefon')
  const kid = { authorization: `Bearer ${tablet.token}`, 'content-type': 'application/json' }
  const child = new Client({ name: 'smoke-family-members', version: '0' })
  await child.connect(
    new StdioClientTransport({
      command: 'bun',
      args: [resolve(import.meta.dir, '../../src/index.ts')],
      env: {
        ...process.env, LANI_PROFILE: 'zan', LANI_CHILD: '1', LANI_CULTURE: 'friuli', LANI_DATA_DIR: zanData, LANI_BRIDGE_PORT: String(kidPort), LANI_BRIDGE_TOKEN: 'zan-token', LANI_PROFILES_DIR: profilesDir,
        LANI_CULTURES_DIR: culturesDir, LANI_RHYTHM: 'off', LANI_VOICE_DESIGN: 'off', LANI_PACKS_DIR: italianPacksDir, LANI_GEPARD_URL: 'http://127.0.0.1:9', LANI_STT_URL: 'http://127.0.0.1:9',
      } as Record<string, string>,
      stderr: 'inherit',
    }),
  )
  try {
    for (let i = 0; i < 100 && !(await fetch(`${kidBase}/health`).then(r => r.ok).catch(() => false)); i++) await Bun.sleep(100)
    const denied = await Promise.all([
      call('GET', '/family/members', undefined, kid, kidBase),
      call('POST', '/family/members', { name: 'Teo', target: 'it', base: 'de', child: true }, kid, kidBase),
      call('POST', '/family/members/zan/pair', undefined, kid, kidBase),
      call('DELETE', `/family/members/zan/devices/${phone.device.id}`, undefined, kid, kidBase),
      call('PUT', `/family/members/zan/devices/${tablet.device.id}/budget`, { daily_cap_usd: 10 }, kid, kidBase),
    ])
    check("a child's device can't add a member, change a budget or take a device away: its bridge refuses, whatever it sends", denied.every(r => r.status === 403 && r.body.code === 'child') && !readRegistry(profilesDir).profiles.some(p => p.id === 'teo') && new Devices(zanApp).list().length === 2, denied.map(r => [r.status, r.body.code]))

    // a parent's budget, per device
    const none = await call('GET', '/family/rules', undefined, kid, kidBase)
    check('GET /family/rules: nothing set, nothing said', none.status === 200 && none.body.daily_cap_usd === null, none)
    const set = await call('PUT', `/family/members/zan/devices/${tablet.device.id}/budget`, { daily_cap_usd: 0.5 })
    const rule = await call('GET', '/family/rules', undefined, kid, kidBase)
    const otherPhone = await call('GET', '/family/rules', undefined, { authorization: `Bearer ${phone.token}` }, kidBase)
    check("a parent's budget reaches that device and no other, with who set it", set.status === 200 && rule.body.daily_cap_usd === 0.5 && rule.body.by === 'Jan' && otherPhone.body.daily_cap_usd === null, { set, rule, otherPhone })
    const rows = await call('GET', '/family/members')
    check("the parent's list says each device's budget", rows.body.members.find((m: any) => m.profile === 'zan').devices.find((d: any) => d.id === tablet.device.id).daily_cap_usd === 0.5)
    const bad = await Promise.all([
      call('PUT', `/family/members/zan/devices/${tablet.device.id}/budget`, { daily_cap_usd: -1 }),
      call('PUT', `/family/members/zan/devices/${tablet.device.id}/budget`, { daily_cap_usd: 'lots' }),
      call('PUT', '/family/members/zan/devices/d_00000000/budget', { daily_cap_usd: 1 }),
      call('PUT', `/family/members/nobody/devices/${tablet.device.id}/budget`, { daily_cap_usd: 1 }),
    ])
    check('a budget: a number or nothing, for a device there is', bad.map(r => r.status).join() === '400,400,404,404', bad.map(r => r.status))
    await call('PUT', `/family/members/zan/devices/${tablet.device.id}/budget`, { daily_cap_usd: null })
    check('a budget taken back', (await call('GET', '/family/rules', undefined, kid, kidBase)).body.daily_cap_usd === null)

    // another code, and a device taken away
    const again = await call('POST', '/family/members/zan/pair')
    check('another code for a member: a new one, the old one spent', again.status === 200 && new URL(again.body.pair.uri).searchParams.get('c') !== q.searchParams.get('c'), again)
    await call('PUT', `/family/members/zan/devices/${phone.device.id}/budget`, { daily_cap_usd: 2 })
    const gone = await call('DELETE', `/family/members/zan/devices/${phone.device.id}`)
    const after = await fetch(`${kidBase}/family/rules`, { headers: { authorization: `Bearer ${phone.token}` } })
    const still = await call('GET', '/family/rules', undefined, kid, kidBase)
    check('a device taken away: its token stops at its next request, its rule goes with it, the other goes on', gone.status === 200 && gone.body.device.name === 'Telefon' && after.status === 401 && still.status === 200, { gone, after: after.status })
    check('its rule is gone from the file', !JSON.stringify(JSON.parse(readFileSync(join(zanApp, 'family-rules.json'), 'utf8'))).includes(phone.device.id))
  } finally {
    await child.close()
  }

  // this device can't take itself away
  const own = new Devices(join(dataDir, 'app')).add('Janov telefon')
  const me = { authorization: `Bearer ${own.token}`, 'content-type': 'application/json' }
  const self = await call('DELETE', `/family/members/default/devices/${own.device.id}`, undefined, me)
  check('the device a parent is on can\'t be taken away from it', self.status === 409 && self.body.code === 'this_device', self)
  new Devices(join(dataDir, 'app')).revoke(own.device.id)
}
