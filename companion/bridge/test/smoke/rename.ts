// Smoke checks: a node set up before the project was renamed from Fluent to Lani (docs/migrate-from-fluent.md) keeps
// working: its FLUENT_* variables, its ~/.config/fluent, ~/.local/state/fluent and ~/.cache/fluent directories, and
// the content a tutor published then (schema fluent.*), its links (fluent://) and its towns' protocol (x-fluent-town-*).
// Runs last: it adds a pack and replaces a villager.
import { spawnSync } from 'node:child_process'
import { sign } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { adoptLegacyEnv, configFile, keysFile, laniDir } from '../../src/env'
import { validatePack } from '../../src/packs'
import { loadBridgeKey, pairMessage, pairUri } from '../../src/pairing'
import { LEGACY_H, Nonces, checkSigned, inviteUri, parseInvite, requestMessage, responseMessage, signRequest, signedJson, verifySigned } from '../../src/towns'
import { isSchema, legacySchema } from '../../src/schema'
import { auth, base, check, client, culturesDir, dataDir, tempDir } from './harness'

const repo = resolve(import.meta.dir, '../../../..')

export default async function rename() {
  // --- the environment: LANI_X, else FLUENT_X -------------------------------------------------------------
  const env = adoptLegacyEnv({ FLUENT_DATA_DIR: '/old', FLUENT_BRIDGE_PORT: '1', LANI_BRIDGE_PORT: '2' } as Record<string, string | undefined>)
  check('env: a FLUENT_* variable is read as its LANI_* name', env.LANI_DATA_DIR === '/old', env)
  check('env: … but a LANI_* variable that is set wins', env.LANI_BRIDGE_PORT === '2', env)

  const bash = spawnSync('bash', ['-c', `. ${JSON.stringify(join(repo, 'companion/bin/lani-env.sh'))}; printf '%s|%s' "$LANI_DATA_DIR" "$LANI_BRIDGE_PORT"`], {
    env: { PATH: process.env.PATH, HOME: '/nowhere', FLUENT_DATA_DIR: '/old', FLUENT_BRIDGE_PORT: '1', LANI_BRIDGE_PORT: '2' },
    encoding: 'utf8',
  })
  check('env (bash, bin/lani-env.sh): FLUENT_* is read as LANI_*, a LANI_* that is set wins', bash.stdout === '/old|2', bash.stdout + bash.stderr)

  const py = spawnSync('python3', ['-c', 'import sys, os; sys.path.insert(0, ".claude/hooks"); from lani_paths import data_dir; print(data_dir())'], {
    cwd: repo,
    env: { PATH: process.env.PATH, HOME: '/nowhere', FLUENT_DATA_DIR: '/tmp/old-data' },
    encoding: 'utf8',
  })
  check('env (python, lani_paths): FLUENT_DATA_DIR is the data directory when LANI_DATA_DIR is not set', py.stdout.trim() === '/tmp/old-data', py.stdout + py.stderr)

  // --- the directories: lani, else fluent while only that one exists -------------------------------------------
  const home = tempDir('lani-smoke-rename-')
  const state = join(home, '.local', 'state')
  check('dirs: a fresh node uses …/lani', laniDir(state) === join(state, 'lani'))
  mkdirSync(join(state, 'fluent'), { recursive: true })
  check('dirs: a node from before the rename keeps …/fluent', laniDir(state) === join(state, 'fluent'))
  mkdirSync(join(state, 'lani'), { recursive: true })
  check('dirs: … until …/lani exists', laniDir(state) === join(state, 'lani'))

  const e = { HOME: home } as Record<string, string | undefined>
  check('config: keys.env under ~/.config/lani on a fresh node', keysFile(e) === join(home, '.config', 'lani', 'keys.env'))
  mkdirSync(join(home, '.config', 'fluent'), { recursive: true })
  writeFileSync(join(home, '.config', 'fluent', 'keys.env'), '# test\n')
  check('config: the old ~/.config/fluent/keys.env while the new one does not exist', keysFile(e) === join(home, '.config', 'fluent', 'keys.env'))
  mkdirSync(join(home, '.config', 'lani'), { recursive: true })
  check('config: … also when ~/.config/lani exists without it', configFile('keys.env', e) === join(home, '.config', 'fluent', 'keys.env'))
  writeFileSync(join(home, '.config', 'lani', 'keys.env'), '# test\n')
  check('config: the new one once it exists', keysFile(e) === join(home, '.config', 'lani', 'keys.env'))
  check('config: LANI_KEYS_FILE names it outright', keysFile({ ...e, LANI_KEYS_FILE: '/k.env' }) === '/k.env')

  // --- content: schema ids fluent.* (before the rename) load as lani.*; what is published says lani.* ---------------
  check('schema: the name before the rename', legacySchema('lani.pack/v0') === 'fluent.pack/v0' && isSchema('fluent.pack/v0', 'lani.pack/v0') && isSchema('lani.pack/v0', 'lani.pack/v0') && !isSchema('other.pack/v0', 'lani.pack/v0'))
  const words = ['skleda', 'lonec', 'ponev', 'kozarec', 'skodelica', 'vilice', 'žlica', 'nož'].map(sl => ({ id: sl.normalize('NFD').replace(/[^a-z]/g, ''), sl, en: `${sl} (en)` }))
  const legacy = { schema: 'fluent.pack/v0', id: 'legacy-kitchen', title: 'Stara kuhinja · The old kitchen', emoji: '🥄', level: 'A1', words }
  const v = validatePack(legacy)
  check('schema: a pack that says fluent.pack/v0 is valid, and read as lani.pack/v0', v.ok && v.pack.schema === 'lani.pack/v0', v)
  mkdirSync(join(dataDir, 'app/packs'), { recursive: true })
  writeFileSync(join(dataDir, 'app/packs/legacy-kitchen.json'), JSON.stringify(legacy))
  const served = await (await fetch(`${base}/packs/legacy-kitchen`, { headers: auth })).json()
  check('schema: a pack a tutor published before the rename (in the learner data) is served, as lani.pack/v0', served.id === 'legacy-kitchen' && served.schema === 'lani.pack/v0', served)

  const micka = JSON.parse(readFileSync(join(culturesDir, 'primorska/villagers/micka.json'), 'utf8'))
  mkdirSync(join(dataDir, 'app/villagers'), { recursive: true })
  const mickaFile = join(dataDir, 'app/villagers/micka.json')
  writeFileSync(mickaFile, JSON.stringify({ ...micka, schema: 'fluent.villager/v0' }))
  const cast = await (await fetch(`${base}/villagers`, { headers: auth })).json()
  const old = cast.find((c: any) => c.id === 'micka')
  check('schema: a villager a tutor published before the rename is served (as lani.villager/v0)', old?.source === 'tutor' && old.schema === 'lani.villager/v0', old)
  rmSync(mickaFile)

  const pub = await client.callTool({ name: 'publish_pack', arguments: { pack: { ...legacy, id: 'legacy-published' } } })
  const file = join(dataDir, 'app/packs/legacy-published.json')
  check('schema: publish_pack takes fluent.pack/v0 and writes lani.pack/v0', !pub.isError && existsSync(file) && JSON.parse(readFileSync(file, 'utf8')).schema === 'lani.pack/v0', pub)

  // --- links and the towns' protocol: written as lani, read as lani or fluent ---------------------------------------
  const fp = '0'.repeat(32)
  check('links: a pairing code says lani://pair, the answer is signed as lani-pair/1',
    pairUri({ url: 'http://127.0.0.1:1', code: 'ABCDEFGH', profile: 'p', fingerprint: fp }).startsWith('lani://pair?') && pairMessage({ profile: 'p', code: 'C', token: 't' }).startsWith('lani-pair/1\n'))
  const invite = inviteUri({ url: 'http://127.0.0.1:1', code: 'ABCDEFGH', fingerprint: fp })
  check('links: an invitation says lani://town; one written before the rename (fluent://town) is read the same',
    invite.startsWith('lani://town?') && JSON.stringify(parseInvite(invite)) === JSON.stringify(parseInvite(invite.replace('lani://', 'fluent://'))) && parseInvite(invite).ok)

  const a = loadBridgeKey(tempDir('lani-smoke-rename-a-'))
  const b = loadBridgeKey(tempDir('lani-smoke-rename-b-'))
  const keyOf = (id: string) => (id === a.fingerprint ? a.spki.toString('base64') : undefined)
  const m = { method: 'GET', path: '/town/public', body: '', time: Date.now(), nonce: 'n'.repeat(22), from: a.fingerprint, to: b.fingerprint }
  const oldTown = new Headers({
    [LEGACY_H.from]: m.from, [LEGACY_H.to]: m.to, [LEGACY_H.time]: String(m.time), [LEGACY_H.nonce]: m.nonce,
    [LEGACY_H.signature]: sign('sha256', Buffer.from(requestMessage(m, 'fluent')), a.privateKey).toString('base64'),
  })
  const heard = checkSigned(oldTown, { method: m.method, path: m.path, body: '', self: b.fingerprint, keyOf, nonces: new Nonces() })
  check("towns: a request from a town that runs the code from before the rename (x-fluent-town-*, fluent-town/1) is taken", heard.ok, heard)
  const both = signRequest(a, { method: 'GET', path: '/town/public', to: b.fingerprint })
  const oldOnly = new Headers(Object.entries(both).filter(([k]) => k.startsWith('x-fluent-')))
  const newOnly = new Headers(Object.entries(both).filter(([k]) => k.startsWith('x-lani-')))
  const self = { method: 'GET', path: '/town/public', body: '', self: b.fingerprint, keyOf }
  check('towns: a request is signed under both names, so an old town takes it and a new one does',
    checkSigned(oldOnly, { ...self, nonces: new Nonces() }).ok && checkSigned(newOnly, { ...self, nonces: new Nonces() }).ok, both)
  const answer = signedJson(b, m.nonce, { ok: true })
  const text = await answer.text()
  check('towns: an answer is signed under both names too',
    verifySigned(b.spki.toString('base64'), responseMessage(m.nonce, text), answer.headers.get('x-lani-town-signature') ?? '') &&
      verifySigned(b.spki.toString('base64'), responseMessage(m.nonce, text, 'fluent'), answer.headers.get('x-fluent-town-signature') ?? ''))
}
