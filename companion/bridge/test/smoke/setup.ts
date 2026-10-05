// Smoke checks for lani-setup's layout (docs/setup.md, "Where things live"): lani.env and its precedence (the
// environment wins; the default learner's settings never reach another learner's process), the voice cache and the
// releases outside the data with the old places as a fallback, and a bridge that finds its learner's data, port, voice
// cache and releases through lani.env alone. Ports: LANI_SMOKE_SETUP_PORT (by default the harness's port + 7).
import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js'
import { spawnSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { adoptEnvFile, learnerSetting, parseEnvFile, readEnvFile, setting } from '../../src/env'
import { initLearnerData, learnerFacts } from '../../src/learners'
import { releaseDir, releaseDirs, resultsDirOf, voiceDir } from '../../src/paths'
import { check, culturesDir, port as smokePort, tempDir } from './harness'

const repo = resolve(import.meta.dir, '../../../..')
const port = Number(process.env.LANI_SMOKE_SETUP_PORT ?? smokePort + 7)

export default async function setup() {
  // --- lani.env: parsed alike everywhere; the environment wins; the default learner's settings stay theirs ---------------
  const home = tempDir('lani-smoke-setup-home-')
  const data = join(home, '.local/share/lani/ana')
  const cache = join(home, '.cache/lani/voice')
  const releases = join(home, '.local/share/lani/releases')
  const envFile = join(home, 'lani.env')
  writeFileSync(envFile, [
    '# the default learner',
    'LANI_DATA_DIR=~/.local/share/lani/ana',
    'LANI_VOICE_CACHE="$HOME/.cache/lani/voice"   # quoted',
    `LANI_BRIDGE_PORT=${port}`,
    'LANI_CULTURE=primorska',
    '# this machine',
    'export LANI_RELEASE_DIR=${HOME}/.local/share/lani/releases',
    'LANI_STT_URL=http://127.0.0.1:9 # a comment',
    'ELEVENLABS_API_KEY=never-here',
  ].join('\n') + '\n')
  const env = (more: Record<string, string> = {}) => ({ HOME: home, LANI_ENV_FILE: envFile, ...more }) as Record<string, string | undefined>
  const parsed = parseEnvFile(readFileSync(envFile, 'utf8'), home)
  check('lani.env: ~ and $HOME expanded, quotes and comments taken off, only LANI_* keys', parsed.LANI_DATA_DIR === data && parsed.LANI_VOICE_CACHE === cache && parsed.LANI_RELEASE_DIR === releases && parsed.LANI_STT_URL === 'http://127.0.0.1:9' && !('ELEVENLABS_API_KEY' in parsed), parsed)
  const adopted = adoptEnvFile(env({ LANI_STT_URL: 'http://mine' }))
  check("lani.env: this machine's settings come into the environment, which wins", adopted.LANI_RELEASE_DIR === releases && adopted.LANI_STT_URL === 'http://mine', adopted)
  check("lani.env: the default learner's settings don't (a process of another learner would inherit them)", adopted.LANI_DATA_DIR === undefined && adopted.LANI_VOICE_CACHE === undefined && adopted.LANI_BRIDGE_PORT === undefined, adopted)
  check('learnerSetting: the default learner gets them', learnerSetting('LANI_DATA_DIR', env()) === data && learnerSetting('LANI_VOICE_CACHE', env()) === cache && setting('LANI_BRIDGE_PORT', env()) === String(port))
  check('learnerSetting: … also with LANI_DATA_DIR naming the same directory', learnerSetting('LANI_VOICE_CACHE', env({ LANI_DATA_DIR: data })) === cache && learnerSetting('LANI_VOICE_CACHE', env({ LANI_DATA_DIR: '~/.local/share/lani/ana/' })) === cache)
  check("learnerSetting: another learner's process never gets them (LANI_PROFILE, another LANI_DATA_DIR)", learnerSetting('LANI_VOICE_CACHE', env({ LANI_PROFILE: 'luka' })) === undefined && learnerSetting('LANI_VOICE_CACHE', env({ LANI_DATA_DIR: join(home, 'other') })) === undefined && learnerSetting('LANI_DATA_DIR', env({ LANI_PROFILE: 'luka' })) === undefined)
  check('setting: the environment first', setting('LANI_BRIDGE_PORT', env({ LANI_BRIDGE_PORT: '1' })) === '1')
  check('readEnvFile: none without the file, /dev/null names none', Object.keys(readEnvFile({ HOME: home, LANI_ENV_FILE: join(home, 'missing.env') })).length === 0 && Object.keys(readEnvFile({ HOME: home, LANI_ENV_FILE: '/dev/null' })).length === 0)
  const bash = spawnSync('bash', ['-c', `. ${JSON.stringify(join(repo, 'companion/bin/lani-env.sh'))}; printf '%s|%s|%s|%s' "$LANI_RELEASE_DIR" "$LANI_STT_URL" "\${LANI_DATA_DIR-unset}" "\${LANI_VOICE_CACHE-unset}"`], {
    env: { PATH: process.env.PATH, HOME: home, LANI_ENV_FILE: envFile, LANI_STT_URL: 'http://mine' }, encoding: 'utf8',
  })
  check("lani.env (bash, bin/lani-env.sh): the same; the learner's settings not exported", bash.stdout === `${releases}|http://mine|unset|unset`, bash.stdout + bash.stderr)
  const py = spawnSync('python3', ['-c', 'import sys, os; sys.path.insert(0, ".claude/hooks"); import lani_paths as p; print(p.data_dir(), os.environ.get("LANI_RELEASE_DIR"), os.environ.get("LANI_VOICE_CACHE", "unset"), p.learner_setting("LANI_VOICE_CACHE"), sep="|")'], {
    cwd: repo, env: { PATH: process.env.PATH, HOME: home, LANI_ENV_FILE: envFile }, encoding: 'utf8',
  })
  check("lani.env (python, lani_paths): the data directory from it, the learner's settings not in the environment", py.stdout.trim() === `${data}|${releases}|unset|${cache}`, py.stdout + py.stderr)

  // --- the voice cache and the releases: the new places, the old ones while only they have something ---------------------
  const app = join(tempDir('lani-smoke-setup-old-'), 'app')
  mkdirSync(join(app, 'voice'), { recursive: true })
  check('voiceDir: <data>/app/voice without the setting', voiceDir(app, { HOME: home, LANI_ENV_FILE: '/dev/null' }) === join(app, 'voice'))
  const own = join(home, 'own-cache')
  check('voiceDir: LANI_VOICE_CACHE when neither place has clips', voiceDir(app, { HOME: home, LANI_VOICE_CACHE: own }) === own)
  writeFileSync(join(app, 'voice', 'voice.db'), '')
  check('voiceDir: … the old place while only it has the clips (a node not moved yet)', voiceDir(app, { HOME: home, LANI_VOICE_CACHE: own }) === join(app, 'voice'))
  mkdirSync(own, { recursive: true })
  writeFileSync(join(own, 'voice.db'), '')
  check('voiceDir: … the cache once it has them', voiceDir(app, { HOME: home, LANI_VOICE_CACHE: own }) === own)
  const fresh = join(tempDir('lani-smoke-setup-fresh-'), 'app')
  check("voiceDir: lani.env's cache for the default learner, not for another", voiceDir(fresh, env()) === cache && voiceDir(fresh, env({ LANI_PROFILE: 'luka' })) === join(fresh, 'voice'))
  mkdirSync(join(app, 'release'), { recursive: true })
  check('releaseDirs: the configured directory first, then the old one; only the old one without the setting', releaseDirs(app, { HOME: home, LANI_RELEASE_DIR: releases }).join() === [releases, join(app, 'release')].join() && releaseDirs(app, { HOME: home }).join() === join(app, 'release'))
  writeFileSync(join(app, 'release', 'latest.json'), '{}')
  check('releaseDir: the old one while only it has a release', releaseDir(app, { HOME: home, LANI_RELEASE_DIR: releases }) === join(app, 'release'))
  mkdirSync(releases, { recursive: true })
  writeFileSync(join(releases, 'latest.json'), JSON.stringify({ versionCode: 1042, versionName: '0.2.42', file: 'lani-1042.apk', notes: 'setup smoke' }))
  writeFileSync(join(releases, 'lani-1042.apk'), 'apk')
  check('releaseDir: the configured one once it has one', releaseDir(app, { HOME: home, LANI_RELEASE_DIR: releases }) === releases)
  const plain = tempDir('lani-smoke-setup-plain-')
  check('resultsDirOf: next to a plain data directory; inside a data repository', resultsDirOf(join(plain, 'data'), { HOME: home }) === join(plain, 'results') && (mkdirSync(join(plain, 'repo', '.git'), { recursive: true }), resultsDirOf(join(plain, 'repo'), { HOME: home }) === join(plain, 'repo', 'results')))

  // --- a bridge configured by lani.env alone: data, port, voice cache, releases -------------------------------------------
  initLearnerData(repo, data, { name: 'Ana', target: 'sl', base: 'en', gender: 'female', goal: 20, results: join(data, 'results') })
  const profile = JSON.parse(readFileSync(join(data, 'learner-profile.json'), 'utf8'))
  check("initLearnerData: the learner's gender and daily goal; results inside the data", profile.learner.gender === 'female' && profile.learner.daily_goal_minutes === 20 && existsSync(join(data, 'results')) && !existsSync(join(data, '..', 'results')), profile.learner)
  mkdirSync(join(data, '.git'), { recursive: true }) // a data repository, as lani-setup makes it (only its root matters here)
  const childEnv: Record<string, string> = {}
  for (const [k, v] of Object.entries(process.env)) if (v !== undefined && !k.startsWith('LANI_') && !k.startsWith('FLUENT_') && !k.startsWith('XDG_')) childEnv[k] = v
  Object.assign(childEnv, {
    HOME: home, LANI_ENV_FILE: envFile, LANI_TUTOR: '1', LANI_BRIDGE_TOKEN: 'setup-token', LANI_RHYTHM: 'off', LANI_CULTURES_DIR: culturesDir,
    ELEVENLABS_API_KEY: '', LANI_VOICE_DESIGN: 'off', LANI_GEPARD_URL: 'http://127.0.0.1:9', CLAUDE_PROJECT_DIR: repo,
    XDG_CACHE_HOME: join(home, '.cache'), XDG_STATE_HOME: join(home, '.local/state'), XDG_DATA_HOME: join(home, '.local/share'), XDG_CONFIG_HOME: join(home, '.config'),
  })
  const client = new Client({ name: 'smoke-setup', version: '0' })
  const transport = new StdioClientTransport({ command: 'bun', args: [resolve(import.meta.dir, '../../src/index.ts')], cwd: home, env: childEnv, stderr: 'pipe' })
  let logged = ''
  transport.stderr?.on('data', (b: Buffer) => void (logged += b.toString()))
  await client.connect(transport)
  const base = `http://127.0.0.1:${port}`
  const auth = { authorization: 'Bearer setup-token' }
  let up = false
  for (let i = 0; i < 50 && !up; i++) {
    up = await fetch(`${base}/health`).then(r => r.ok, () => false)
    if (!up) await Bun.sleep(100)
  }
  try {
    check("a bridge configured by lani.env alone serves the app on lani.env's port", up, logged)
    const state = up ? await fetch(`${base}/state`, { headers: auth }).then(r => r.json() as Promise<any>) : {}
    check("… from the learner's data in lani.env's LANI_DATA_DIR", state?.databases?.learner_profile?.learner?.name === 'Ana' && learnerFacts(data).name === 'Ana', state?.databases?.learner_profile?.learner)
    check('… its voice clips in the voice cache, none in the data', existsSync(join(cache, 'voice.db')) && existsSync(join(cache, 'files')) && !existsSync(join(data, 'app', 'voice')) && logged.includes(`clips in ${cache}`), logged.split('\n').filter(l => l.includes('voice')).join('\n'))
    const latest = up ? await fetch(`${base}/app/latest`, { headers: auth }).then(r => r.json() as Promise<any>) : {}
    const apk = up ? await fetch(`${base}/app/apk`, { headers: auth }).then(r => r.text()) : ''
    check('… the releases from LANI_RELEASE_DIR', latest?.versionCode === 1042 && apk === 'apk' && !existsSync(join(data, 'app', 'release')), latest)
    const rules = client.getInstructions() ?? ''
    check("… and the tutor told where the data and the session results are (outside the checkout)", rules.includes(`Ana's data is in ${data}`) && rules.includes(`${join(data, 'results')}/`) && rules.startsWith("You are Ana's Lani language tutor"), rules)
  } finally {
    await client.close()
  }
}
