// Smoke checks: several learners on one machine (profiles) and pairing a phone by QR code. Runs last:
// it fills the main bridge's limit on wrong pairing codes.
import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js'
import { createHash, verify } from 'node:crypto'
import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { loadConfig } from '../../src/config'
import { cleanDeviceName, createPairingCode, fingerprintOf, loadBridgeKey, pairMessage } from '../../src/pairing'
import { auth, base, bridgeWhisper, check, client, culturesDir, curatedDir, dataDir, modulesDir, port, scenesDir, tempDir, token, guideOf } from './harness'

const cliDir = resolve(import.meta.dir, '../../src/cli')
const binDir = resolve(import.meta.dir, '../../../bin')

/** Runs a CLI (a bun script in src/cli, or a bin/ script) with [env] added; its exit code and output. */
function cli(cmd: string[], env: Record<string, string>) {
  const p = Bun.spawnSync(cmd, { env: { ...process.env, ...env }, stdout: 'pipe', stderr: 'pipe' })
  return { code: p.exitCode, out: p.stdout.toString(), err: p.stderr.toString() }
}

const post = (url: string, body: unknown) => fetch(`${url}/pair`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) })
const bearerOf = (t: string) => ({ authorization: `Bearer ${t}` })
const payloadOf = (out: string) => new URL(/lani:\/\/pair\?\S+/.exec(out)?.[0] ?? 'lani://pair')

/** The app's check of an answer to POST /pair: the key matches the QR's fingerprint and signed this code, token and profile. */
function signedBy(r: any, f: string, code: string, profile: string) {
  const spki = Buffer.from(r.public_key, 'base64')
  const msg = pairMessage({ profile, code, token: r.token })
  return fingerprintOf(spki) === f && verify('sha256', Buffer.from(msg), { key: spki, format: 'der', type: 'spki' }, Buffer.from(r.signature, 'base64'))
}

export default async function pairing() {
  const appDir = join(dataDir, 'app')
  const defaultEnv = { LANI_DATA_DIR: dataDir, LANI_BRIDGE_PORT: String(port) }

  // --- pairing the default profile ----------------------------------------------------------------
  const made = cli(['bun', join(cliDir, 'pair.ts'), '--no-qr', '--url', `${base}/`], defaultEnv)
  const qr = payloadOf(made.out)
  const q = (k: string) => qr.searchParams.get(k) ?? ''
  const key = loadBridgeKey(appDir)
  check('lani-pair prints a versioned payload: bridge URL, code, profile, fingerprint', made.code === 0 && qr.protocol === 'lani:' && qr.host === 'pair' && q('v') === '1' && q('u') === base && /^[A-Z2-9]{12}$/.test(q('c')) && q('p') === 'default' && q('f') === key.fingerprint, made.out + made.err)
  check('lani-pair sees the bridge up (no warning)', !made.out.includes('No bridge answers'))
  check('no pairing code is stored in the clear', !readdirSync(join(appDir, 'pairing')).some(f => readFileSync(join(appDir, 'pairing', f), 'utf8').includes(q('c'))))

  const pr = await post(base, { code: q('c').toLowerCase(), device: 'Pixel 8' })
  const paired = await pr.json()
  check('POST /pair trades the code for a device token', pr.status === 200 && paired.v === 1 && /^fd_[\w-]{40,}$/.test(paired.token) && paired.device?.name === 'Pixel 8' && paired.profile === 'default', paired)
  check("the answer is signed with the key the QR's fingerprint names", signedBy(paired, q('f'), q('c'), 'default') && paired.fingerprint === q('f'))
  check('a forged answer does not verify', !signedBy({ ...paired, token: paired.token + 'x' }, q('f'), q('c'), 'default') && !signedBy(paired, q('f'), q('c'), 'luka'))
  const deviceToken: string = paired.token
  check('the device token opens the app API', (await fetch(`${base}/modules`, { headers: bearerOf(deviceToken) })).status === 200 && (await fetch(`${base}/state`, { headers: bearerOf(deviceToken) })).status === 200)
  check('the bridge token still works', (await fetch(`${base}/modules`, { headers: auth })).status === 200)
  check('a made-up device token does not', (await fetch(`${base}/modules`, { headers: bearerOf('fd_' + 'A'.repeat(43)) })).status === 401)
  const stored = readFileSync(join(appDir, 'devices.json'), 'utf8')
  check('devices.json keeps the token hashed', !stored.includes(deviceToken) && stored.includes(createHash('sha256').update(deviceToken).digest('hex')))
  const again = await post(base, { code: q('c'), device: 'Pixel 8' })
  check('a code works once', again.status === 403 && (await again.json()).code === 'invalid_code')

  const expired = createPairingCode(appDir, { ttlMs: -1_000 })
  const late = await post(base, { code: expired.code, device: 'x' })
  check('an expired code is a 410', late.status === 410 && (await late.json()).code === 'expired_code')
  check('a malformed request is a 400', (await post(base, { device: 'x' })).status === 400 && (await fetch(`${base}/pair`, { method: 'POST', body: 'nope' })).status === 400)

  const odd = createPairingCode(appDir)
  const oddPaired = await (await post(base, { code: odd.code, device: 'Pixel 8' })).json()
  check('device names are made unique, and cleaned', oddPaired.device?.name === 'Pixel 8 (2)' && cleanDeviceName('Pixel\n<b>8</b>') === 'Pixel b 8 /b' && cleanDeviceName('  ') === 'Phone', oddPaired.device)
  const listed = cli(['bun', join(cliDir, 'pair.ts'), '--devices'], defaultEnv)
  check('lani-pair --devices lists the paired phones', listed.code === 0 && listed.out.includes('Pixel 8') && listed.out.includes(paired.device.id), listed.out + listed.err)
  const revoked = cli(['bun', join(cliDir, 'pair.ts'), '--revoke', 'Pixel 8'], defaultEnv)
  check('lani-pair --revoke unpairs a phone', revoked.code === 0 && revoked.out.includes(paired.device.id), revoked.out + revoked.err)
  check('a revoked token stops working at once', (await fetch(`${base}/modules`, { headers: bearerOf(deviceToken) })).status === 401)
  check('the other phone stays paired', (await fetch(`${base}/modules`, { headers: bearerOf(oddPaired.token) })).status === 200)
  check('revoking an unknown phone fails', cli(['bun', join(cliDir, 'pair.ts'), '--revoke', 'nobody'], defaultEnv).code !== 0)

  // --- a second learner ---------------------------------------------------------------------------------
  const profilesDir = tempDir('lani-smoke-profiles-')
  const pEnv = { ...defaultEnv, LANI_PROFILES_DIR: profilesDir, LANI_TAILNET_HOST: 'node.test.ts.net' }
  const port2 = port + 1
  const added = cli(['bun', join(cliDir, 'profile.ts'), 'add', 'luka', '--name', 'Luka', '--target', 'it', '--base', 'sl', '--child', '--culture', 'friuli', '--landscape', 'hills', '--port', String(port2)], pEnv)
  const reg = JSON.parse(readFileSync(join(profilesDir, 'profiles.json'), 'utf8'))
  const luka = reg.profiles[0]
  const lukaData = join(profilesDir, 'luka/data')
  check('lani-profile add registers the learner', added.code === 0 && luka?.id === 'luka' && luka.port === port2 && luka.https_port === 8443 && luka.tmux === 'lani-luka' && luka.remote_control === 'lani-tutor-luka' && luka.child === true && luka.target === 'it' && luka.base === 'sl' && luka.culture === 'friuli' && luka.landscape === 'hills', added.out + added.err)
  check('… prints the tailscale serve command and the URL', added.out.includes(`tailscale serve --bg --https=8443 http://127.0.0.1:${port2}`) && added.out.includes('https://node.test.ts.net:8443') && added.out.includes('lani-pair --profile luka'), added.out)
  const lp = JSON.parse(readFileSync(join(lukaData, 'learner-profile.json'), 'utf8'))
  check('… and makes their data from the templates', ['progress-db', 'mistakes-db', 'mastery-db', 'spaced-repetition', 'session-log'].every(f => existsSync(join(lukaData, `${f}.json`))) && lp.learner.name === 'Luka' && lp.learner.target_language === 'Italian' && lp.learner.base_language === 'Slovene' && lp.learner.current_level === 'A1' && !readFileSync(join(lukaData, 'spaced-repetition.json'), 'utf8').includes('{'.concat('unique')), lp.learner)
  const dup = cli(['bun', join(cliDir, 'profile.ts'), 'add', 'luka', '--name', 'L', '--target', 'it', '--base', 'sl'], pEnv)
  const bad = cli(['bun', join(cliDir, 'profile.ts'), 'add', 'default', '--name', 'L', '--target', 'it', '--base', 'sl'], pEnv)
  const reserved = cli(['bun', join(cliDir, 'profile.ts'), 'add', 'ana', '--name', 'Ana', '--target', 'sl', '--base', 'de', '--port', '8790'], pEnv)
  const nowhere = cli(['bun', join(cliDir, 'profile.ts'), 'add', 'eva', '--name', 'Eva', '--target', 'it', '--base', 'sl', '--culture', 'atlantis'], pEnv)
  check('… refuses a second luka, the id "default", the real bridge\'s port and a culture pack there isn\'t', dup.code !== 0 && bad.code !== 0 && reserved.code !== 0 && nowhere.code !== 0 && nowhere.err.includes('primorska'), nowhere.err)
  // the landscape a new village is offered first: one of the app's, the coast only in a region by the sea
  const shore = cli(['bun', join(cliDir, 'profile.ts'), 'add', 'eva', '--name', 'Eva', '--target', 'sl', '--base', 'de', '--landscape', 'coast'], pEnv)
  const volcano = cli(['bun', join(cliDir, 'profile.ts'), 'add', 'eva', '--name', 'Eva', '--target', 'sl', '--base', 'de', '--landscape', 'volcano'], pEnv)
  check('… refuses the coast for a village without the sea, and a landscape the app has not', shore.code !== 0 && shore.err.includes('primorska has no sea') && volcano.code !== 0 && volcano.err.includes('valley, hills, lake, mountains, coast') && !readFileSync(join(profilesDir, 'profiles.json'), 'utf8').includes('"eva"'), shore.err + volcano.err)
  const auto = cli(['bun', join(cliDir, 'profile.ts'), 'add', 'ana', '--name', 'Ana', '--target', 'sl', '--base', 'de'], pEnv)
  const ana = JSON.parse(readFileSync(join(profilesDir, 'profiles.json'), 'utf8')).profiles[1]
  check('… picks a free port and the next tailnet port for the next learner, and the default culture (the land left to the app)', auto.code === 0 && ana.port >= 8792 && ![8790, 8791, 8795, 8796, 8799, port2].includes(ana.port) && ana.https_port === 9443 && ana.culture === 'primorska' && ana.landscape === undefined, ana)
  check('… and shows the landscape it was given', added.out.includes('land     hills') && !auto.out.includes('land '), added.out)

  const listing = cli(['bash', join(binDir, 'lani-session'), '--list'], pEnv)
  check('lani-session --list shows the learners', listing.code === 0 && /^default\b/m.test(listing.out) && /^luka\s+Luka \(child\)\s+it ← sl\s+friuli\s+\d+/m.test(listing.out) && /^default\s+\S.*\s+primorska\s+\d+/m.test(listing.out) && /^ana\b/m.test(listing.out), listing.out + listing.err)
  // the classic mode: the default until Jan switches to the bridge service (~/.config/lani/bridge-mode, plan 4)
  const today = cli(['bash', join(binDir, 'lani-session'), '--print'], { ...pEnv, LANI_KEYS_FILE: '/k.env', LANI_BRIDGE_MODE: 'classic' })
  // The command lani-session ran before there were profiles, word for word (keys=/k.env), plus
  // LANI_TUTOR=1: only the tutor's bridge serves the app.
  const before = `set -a; if [ -r '/k.env' ]; then . '/k.env'; fi; set +a; export LANI_TUTOR=1; exec claude --dangerously-load-development-channels server:lani --remote-control lani-tutor`
  const quoted = cli(['bash', '-c', 'printf "tmux new-session -A -s %q %q\\n" lani "$0"', before], {}).out
  check('lani-session without a profile runs exactly what it did before', today.code === 0 && today.out === quoted && quoted.startsWith('tmux new-session -A -s lani set'), today.out + today.err)
  // A plain Claude Code session in the repo spawns a bridge too (.mcp.json): it keeps the port free for the tutor's.
  const cfgOf = (e: Record<string, string>) => loadConfig({ HOME: process.env.HOME, PATH: process.env.PATH, LANI_DATA_DIR: dataDir, ...e })
  check('only the tutor session (or an explicit port) serves the app', !cfgOf({}).serveApp && cfgOf({}).port === 8790 && cfgOf({ LANI_TUTOR: '1' }).serveApp && cfgOf({ LANI_BRIDGE_PORT: String(port) }).serveApp)
  const sess = cli(['bash', join(binDir, 'lani-session'), '--profile', 'luka', '--print'], { ...pEnv, LANI_BRIDGE_MODE: 'classic' })
  const spec = cli(['bun', join(cliDir, 'profile.ts'), 'session', 'luka', '--keys', '/k.env'], pEnv).out.split('\n')
  check("lani-session --profile luka: its own tmux session, data, port, child flag and Remote Control name", sess.code === 0 && sess.out.startsWith('tmux new-session -A -s lani-luka ') && spec[0] === 'lani-luka' && spec[1].includes(`LANI_PROFILE='luka'`) && spec[1].includes(`LANI_TUTOR='1'`) && spec[1].includes(`LANI_DATA_DIR='${lukaData}'`) && spec[1].includes(`LANI_BRIDGE_PORT='${port2}'`) && spec[1].includes(`LANI_CHILD='1'`) && spec[1].includes(`LANI_CULTURE='friuli'`) && spec[1].includes(`LANI_LANDSCAPE='hills'`) && spec[1].includes(`LANI_RELEASE_DIR='${join(dataDir, 'app/release')}'`) && spec[1].includes(`--remote-control 'lani-tutor-luka' --append-system-prompt '`) && spec[1].indexOf('/k.env') < spec[1].indexOf('LANI_DATA_DIR'), sess.out + sess.err + spec.join('\n'))
  // CLAUDE.md is written for the default learner: the session's prompt says whose it is and where their data is
  const prompt = spec[1].slice(spec[1].indexOf('--append-system-prompt'))
  check("… and its prompt says whose session it is, where their data is, and that it's a child's", prompt.includes('for Luka (learner profile "luka"), not for Jan') && prompt.includes(`data is in ${lukaData}`) && prompt.includes('learns Italian from Slovene') && prompt.includes('is a child'), prompt)

  // The second learner's bridge, started as that session's Claude Code would start it (without the harness's tokens).
  const env2: Record<string, string> = {}
  for (const [k, v] of Object.entries(process.env)) if (v !== undefined && !['LANI_BRIDGE_TOKEN', 'LANI_FAMILY_TOKEN'].includes(k)) env2[k] = v
  Object.assign(env2, { LANI_PROFILE: 'luka', LANI_DATA_DIR: lukaData, LANI_BRIDGE_PORT: String(port2), LANI_CHILD: '1', LANI_CULTURE: 'friuli', LANI_LANDSCAPE: 'hills', LANI_CULTURES_DIR: culturesDir, LANI_RHYTHM: 'off', ELEVENLABS_API_KEY: '', LANI_PACKS_DIR: curatedDir, LANI_MODULES_DIR: modulesDir, LANI_SCENES_DIR: scenesDir, LANI_STT_URL: bridgeWhisper.url, LANI_GEPARD_URL: 'http://127.0.0.1:9' })
  const client2 = new Client({ name: 'smoke-luka', version: '0' })
  await client2.connect(new StdioClientTransport({ command: 'bun', args: [resolve(import.meta.dir, '../../src/index.ts')], env: env2, stderr: 'inherit' }))
  await Bun.sleep(300)
  const base2 = `http://127.0.0.1:${port2}`
  const token2 = readFileSync(join(lukaData, 'app/bridge-token'), 'utf8').trim()
  check("the second bridge listens on its own port, with its own token in its own data", (await fetch(`${base2}/health`)).ok && token2.length >= 32 && token2 !== token)
  const state2 = await (await fetch(`${base2}/state`, { headers: bearerOf(token2) })).json()
  check("… and serves the second learner's data", state2?.databases?.learner_profile?.learner?.name === 'Luka', state2?.databases?.learner_profile?.learner)
  check("the learners' tokens don't cross", (await fetch(`${base2}/state`, { headers: auth })).status === 401 && (await fetch(`${base}/state`, { headers: bearerOf(token2) })).status === 401 && (await fetch(`${base2}/state`, { headers: bearerOf(oddPaired.token) })).status === 401)
  const ins2 = await guideOf(client2)
  const ins1 = await guideOf(client)
  // What Claude Code shows the session: the rules that fit its 2048 characters (the guide is the channel_guide tool).
  const rules1 = client.getInstructions() ?? ''
  const rules2 = client2.getInstructions() ?? ''
  check('the instructions Claude Code shows fit, and say to answer with the reply tool and to read the guide', [rules1, rules2].every(r => r.length <= 2048 && r.includes('never sees terminal output') && r.includes('conversation_id') && r.includes('channel_guide')) && ins1.length > 2048, [rules1.length, rules2.length])
  check("… and name the learner: Luka's with their data and the child's rules, Jan's as before", rules2.includes('Learner profile "luka"') && rules2.includes(lukaData) && rules2.includes('the learner is a child') && !/\bJan\b/.test(rules2) && rules1.startsWith("You are Jan's Lani language tutor") && !rules1.includes('Learner profile'), rules2)
  check("the second tutor's instructions name the learner, their data and that they are a child", ins2.includes('Learner profile "luka"') && ins2.includes("You are Luka's Lani language tutor") && !/\bJan\b/.test(ins2) && ins2.includes(lukaData) && ins2.includes('the learner is a child') && ins2.includes('Italian'), ins2.slice(0, 1200))
  check("the default tutor's instructions are as before", ins1.startsWith("You are Jan's Lani language tutor") && !ins1.includes('Learner profile') && !ins1.includes('the learner is a child'))
  // the Italian town's pack: the bridge plays it, and serves its cast
  const c2 = await (await fetch(`${base2}/culture`, { headers: bearerOf(token2) })).json()
  check('the second bridge plays its learner\'s friuli pack, in Italian', c2.id === 'friuli' && !c2.wanted && !c2.why && c2.language === 'it', c2)
  const c1 = await (await fetch(`${base}/culture`, { headers: auth })).json()
  check('… offers a new village its landscape (the hills), and the default bridge none', c2.landscape === 'hills' && c1.landscape === undefined, { c1: c1.landscape, c2: c2.landscape })
  const cast2 = (await (await fetch(`${base2}/villagers`, { headers: bearerOf(token2) })).json()) as any[]
  check('… and serves friuli\'s cast', cast2.length === 13 && cast2[0].id === 'rosa', cast2.map(v => v.id))
  check("a child's app gets no tool approvals", !(client2.getServerCapabilities() as any)?.experimental?.['claude/channel/permission'] && (await fetch(`${base2}/permission`, { method: 'POST', headers: { ...bearerOf(token2), 'content-type': 'application/json' }, body: JSON.stringify({ request_id: 'abcde', behavior: 'allow' }) })).status === 403)

  const made2 = cli(['bun', join(cliDir, 'pair.ts'), '--profile', 'luka', '--no-qr', '--url', base2], pEnv)
  const qr2 = payloadOf(made2.out)
  check('lani-pair --profile luka makes a code for that learner', made2.code === 0 && qr2.searchParams.get('p') === 'luka' && qr2.searchParams.get('f') === loadBridgeKey(join(lukaData, 'app')).fingerprint && qr2.searchParams.get('f') !== key.fingerprint, made2.out + made2.err)
  const mainCode = createPairingCode(appDir)
  check("one learner's code doesn't pair with another's tutor", (await post(base2, { code: mainCode.code, device: 'x' })).status === 403)
  const p2 = await post(base2, { code: qr2.searchParams.get('c'), device: 'Tablet' })
  const paired2 = await p2.json()
  check('the phone pairs with the second learner', p2.status === 200 && paired2.profile === 'luka' && paired2.learner?.name === 'Luka' && paired2.learner.target === 'Italian' && paired2.learner.base === 'Slovene' && paired2.learner.child === true && signedBy(paired2, qr2.searchParams.get('f')!, qr2.searchParams.get('c')!, 'luka'), paired2)
  check('… and its token opens only that tutor', (await fetch(`${base2}/modules`, { headers: bearerOf(paired2.token) })).status === 200 && (await fetch(`${base}/modules`, { headers: bearerOf(paired2.token) })).status === 401)
  check("… its devices.json is the second learner's", existsSync(join(lukaData, 'app/devices.json')) && !readFileSync(join(appDir, 'devices.json'), 'utf8').includes('Tablet'))
  await client2.close()

  // --- the limit on wrong codes (the main bridge has seen 2 so far: a spent code and an expired one) ------
  let statuses: number[] = []
  for (let i = 0; i < 9; i++) statuses.push((await post(base, { code: 'WRONGWRONG22', device: 'x' })).status)
  const good = createPairingCode(appDir)
  const blocked = await post(base, { code: good.code, device: 'x' })
  check('ten wrong codes in ten minutes, then every attempt waits', statuses.slice(0, 8).every(s => s === 403) && statuses[8] === 429 && blocked.status === 429 && Number(blocked.headers.get('retry-after')) > 0, statuses)
  check('… and the code that came too late is still unspent', existsSync(join(appDir, 'pairing')) && readdirSync(join(appDir, 'pairing')).length > 0)
}
