#!/usr/bin/env bun
// companion/bin/lani-setup (also `lani setup`): sets up Lani on this machine, step by step (docs/setup.md). Run it again
// to change anything: every step starts from what is set up already.
//
//   1. the tools it needs        5. voices and speech (the ElevenLabs key, the local workers)
//   2. the learner               6. the network (Tailscale Serve, or your HTTPS proxy on the LAN)
//   3. their data (a git repo)   7. running the tutor (tmux, or the bridge under systemd / launchd)
//   4. a private remote for it   8. pairing the phone, and getting the app
//
// Without a terminal, or with --yes, it asks nothing: the flags and what is set up already decide, and nothing that
// reaches outside this machine (a GitHub repository, a push, tailscale serve, a service) happens without its flag.
import '../env' // first: lani.env's settings, LANI_* from the FLUENT_* names of a node set up before the rename
import './plain' // no colours when the output isn't a terminal (a log, a test)
import * as p from '@clack/prompts'
import { spawnSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { platform } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { parseArgs } from 'node:util'
import { APP_LANGUAGES } from '../cultures'
import { envFile, expandHome, keysFile, setting } from '../env'
import { ID, initLearnerData, LANGUAGES } from '../learners'
import {
  applyLearner, bridgeAnswers, checkTools, cultureFor, dataRepo, defaultDataDir, defaultReleaseDir, defaultVoiceCache, git, githubRepoOf, GOALS,
  hasKey, holdsLearner, homeOf, idOf, importData, LAUNCHD_LABEL, launchdPlist, learnerOf, LEVELS, readLaniEnv, repoFiles, setKey, size,
  systemdUnit, tailscaleServes, tailscaleStatus, tilde, workerAnswers, writeLaniEnv, type Caches, type LearnerAnswers,
} from '../setup'
import { projectDir } from './common'

const { values: f } = parseArgs({
  args: process.argv.slice(2),
  options: {
    yes: { type: 'boolean', short: 'y', default: false },
    name: { type: 'string' },
    id: { type: 'string' },
    gender: { type: 'string' },
    target: { type: 'string' },
    base: { type: 'string' },
    level: { type: 'string' },
    goal: { type: 'string' },
    data: { type: 'string' },
    import: { type: 'string' },
    fresh: { type: 'boolean', default: false },
    caches: { type: 'string' },
    remote: { type: 'string' },
    push: { type: 'boolean', default: false },
    'elevenlabs-key-file': { type: 'string' },
    network: { type: 'string' },
    url: { type: 'string' },
    serve: { type: 'boolean', default: false },
    run: { type: 'string' },
    pair: { type: 'boolean', default: false },
    'skip-checks': { type: 'boolean', default: false },
    help: { type: 'boolean', short: 'h', default: false },
  },
})

if (f.help) {
  console.log(`usage: lani-setup [--yes] [options]          set up Lani on this machine (docs/setup.md); run it again to change things

The learner:  --name NAME  --id ID  --gender male|female  --target sl|en|it|de  --base sl|en|it|de
              --level A1..C2  --goal MINUTES
Their data:   --data DIR (default ~/.local/share/lani/<id>)
              --import DIR   an earlier install's data (Lani's or Fluent's data/): copied, its caches moved out, a git repository
              --caches move|copy   what --import does with the voice clips and APKs (default move)
              --fresh        a new learner, from the templates
The remote:   --remote none|URL|github[:OWNER/NAME]   --push (push now)
Voices:       --elevenlabs-key-file FILE|-   the ElevenLabs key from a file or stdin (never on the command line)
Network:      --network tailscale|lan|skip   --url https://…   --serve (run tailscale serve --bg <port>)
Running:      --run tmux|service|systemd|launchd|skip
Pairing:      --pair (show the QR code when the bridge runs)
Other:        --yes (ask nothing)  --skip-checks`)
  process.exit(0)
}

const interactive = !f.yes && !!process.stdin.isTTY && !!process.stdout.isTTY
const env = process.env

// --- asking (or, without a terminal, taking the flags and the defaults) ------------------------------------------------------

function stop(): never {
  p.cancel('Stopped. What was set up so far stays; run lani-setup again any time.')
  process.exit(1)
}

function die(msg: string): never {
  p.log.error(msg)
  p.outro('lani-setup stopped. Fix the above and run it again: what is set up already stays.')
  process.exit(2)
}

const ask = {
  async text(o: { message: string; initial?: string; placeholder?: string; validate?: (v: string) => string | undefined }): Promise<string> {
    if (!interactive) {
      const v = o.initial ?? ''
      const bad = o.validate?.(v)
      if (bad) die(`${o.message} ${bad}`)
      return v
    }
    const v = await p.text({ message: o.message, initialValue: o.initial, placeholder: o.placeholder, validate: o.validate ? x => o.validate!(x ?? '') : undefined })
    if (p.isCancel(v)) stop()
    return v.trim()
  },
  async select<T extends string>(message: string, options: { value: T; label: string; hint?: string }[], initial: T): Promise<T> {
    if (!interactive) return initial
    const v = await p.select<T>({ message, options: options as never, initialValue: initial })
    if (p.isCancel(v)) stop()
    return v as T
  },
  /** [auto]: the answer without a terminal (what a flag said, else the safe one). */
  async confirm(message: string, initial: boolean, auto = false): Promise<boolean> {
    if (!interactive) return auto
    const v = await p.confirm({ message, initialValue: initial })
    if (p.isCancel(v)) stop()
    return v
  },
  async secret(message: string): Promise<string> {
    if (!interactive) return ''
    const v = await p.password({ message, mask: '•' })
    if (p.isCancel(v)) stop()
    return (v ?? '').trim()
  },
}

const oneOf = <T extends string>(flag: string, v: string | undefined, allowed: readonly T[]): T | undefined => {
  if (v === undefined) return undefined
  const x = v.trim().toLowerCase() as T
  if (!allowed.includes(x)) die(`--${flag}: one of ${allowed.join(', ')}`)
  return x
}
const langName = (c: string) => LANGUAGES[c] ?? c
/** A spinner on a terminal; plain lines without one. */
const spin = () => {
  if (interactive) return p.spinner()
  return { start: (m: string) => p.log.message(`${m}…`), stop: (m: string) => p.log.success(m), error: (m: string) => p.log.error(m) }
}
/** [cmd]: its output, within a minute; [inherit]: on this terminal, for as long as it takes (the tutor, the QR code). */
const run = (cmd: string, args: string[], o: { inherit?: boolean } = {}) =>
  spawnSync(cmd, args, { encoding: 'utf8', ...(o.inherit ? { stdio: 'inherit' as const } : { timeout: 60_000 }) })

// --- what is set up already ---------------------------------------------------------------------------------------------------

const cfgPath = envFile()
const before = readLaniEnv(cfgPath)
const cfg: Record<string, string | undefined> = { ...before.values }
const save = () => writeLaniEnv(cfgPath, cfg, before.text)
const port = Number(setting('LANI_BRIDGE_PORT') ?? cfg.LANI_BRIDGE_PORT ?? 8790)
const bin = (name: string) => join(projectDir, 'companion/bin', name)

p.intro('Lani setup')
p.log.message(
  [
    `This sets up your tutor on this machine, in 8 steps. Run it again any time to change something: each step starts`,
    `from what is set up already. Settings: ${tilde(cfgPath)}${before.text ? ' (found)' : ' (new)'}. Guide: docs/setup.md.`,
  ].join('\n'),
)

// --- 1. the tools ---------------------------------------------------------------------------------------------------------------

p.log.step('1/8 · The tools')
if (f['skip-checks']) p.log.info('Skipped (--skip-checks).')
else {
  const tools = checkTools()
  const line = (t: (typeof tools)[number]) =>
    `${t.problem ? (t.required ? '✗' : '–') : '✓'} ${t.name.padEnd(9)} ${(t.problem ? '' : (t.version ?? '')).padEnd(9)} ${t.for}` +
    (t.problem ? `\n    ${t.problem}: ${t.how}` : '')
  p.note([...tools.filter(t => t.required).map(line), '', 'Optional:', ...tools.filter(t => !t.required).map(line)].join('\n'), 'What Lani needs')
  const missing = tools.filter(t => t.required && t.problem)
  if (missing.length) {
    p.log.warn(`Missing: ${missing.map(t => t.name).join(', ')}. Setup can go on (it writes the settings and the data), but the tutor can't run until they're there.`)
    if (!(await ask.confirm('Go on anyway?', true, true))) stop()
  }
}

// --- 2. the learner -----------------------------------------------------------------------------------------------------------

p.log.step('2/8 · The learner')
const configuredData = cfg.LANI_DATA_DIR ? resolve(expandHome(cfg.LANI_DATA_DIR, homeOf())) : undefined
const existing = configuredData && holdsLearner(configuredData) ? configuredData : undefined
let source = f.import ? resolve(expandHome(f.import, homeOf())) : undefined
if (source && !holdsLearner(source)) die(`--import ${f.import}: no learner there (no learner-profile.json). Name a data directory, e.g. the old checkout's data/.`)
if (existing) p.log.info(`The learner's data is in ${tilde(existing)}: their profile below; change what you like.`)
else if (!source && !f.fresh && interactive) {
  // a learner in this checkout's own data/ (from before lani-setup): offered as what to import
  const inCheckout = holdsLearner(join(projectDir, 'data')) ? join(projectDir, 'data') : undefined
  const from = await ask.select('Is this a new learner, or do you have data from an earlier install?', [
    { value: 'fresh', label: 'A new learner', hint: 'start fresh' },
    { value: 'import', label: 'Import data from an earlier install', hint: inCheckout ? `e.g. this checkout's data/` : "Lani's or Fluent's data/ directory" },
  ], inCheckout ? 'import' : 'fresh')
  if (from === 'import') {
    const dir = await ask.text({
      message: 'Where is it? (the data directory: it has learner-profile.json)',
      initial: inCheckout,
      placeholder: '/path/to/old/checkout/data',
      validate: v => (holdsLearner(resolve(expandHome(v, homeOf()))) ? undefined : 'no learner-profile.json there'),
    })
    source = resolve(expandHome(dir, homeOf()))
  }
}
const known = learnerOf(existing ?? source ?? '/nonexistent')
const name = f.name?.trim() || (await ask.text({ message: "The learner's name", initial: known.name, validate: v => (v.trim() ? undefined : 'a name, please') }))
if (!name) die('--name: the learner\'s name')
const gender =
  oneOf('gender', f.gender, ['male', 'female'] as const) ??
  ((await ask.select<'male' | 'female' | 'unset'>(
    `Grammatical gender: Slovene agrees with it ("${name}, si lačen?" to a man, "si lačna?" to a woman)`,
    [{ value: 'female', label: 'Feminine', hint: 'lačna, bila' }, { value: 'male', label: 'Masculine', hint: 'lačen, bil' }],
    known.gender ?? 'unset',
  )) as 'male' | 'female' | 'unset')
const languages = APP_LANGUAGES.map(c => ({ value: c as string, label: langName(c) }))
let target = (f.target && (APP_LANGUAGES as readonly string[]).includes(f.target.toLowerCase()) ? f.target.toLowerCase() : f.target ? die(`--target: one of ${APP_LANGUAGES.join(', ')}`) : undefined) as string | undefined
if (!target) {
  if (known.target && (existing || source)) {
    target = known.target
    p.log.info(`Learns ${langName(target)} (from their data; another language is another learner: companion/bin/lani-profile).`)
  } else target = await ask.select('The language they learn', languages, known.target ?? 'sl')
} else if (known.target && (existing || source) && target !== known.target) die(`--target ${target}: the data is a learner of ${langName(known.target)}. Another language is another learner (companion/bin/lani-profile), or a fresh start in another directory.`)
const baseDefault = known.base && known.base !== target ? known.base : target === 'en' ? 'sl' : 'en'
let base = f.base?.toLowerCase() || (await ask.select('The language explanations are in', languages.filter(l => l.value !== target), baseDefault))
if (!(APP_LANGUAGES as readonly string[]).includes(base)) die(`--base: one of ${APP_LANGUAGES.join(', ')}`)
if (base === target) die(`--base and --target: two different languages (both are ${langName(base)})`)
const level = f.level?.toUpperCase() || (await ask.select('Their level now', LEVELS.map(l => ({ value: l as string, label: l, hint: l === 'A1' ? 'a beginner' : undefined })), known.level ?? 'A1'))
if (!(LEVELS as readonly string[]).includes(level)) die(`--level: one of ${LEVELS.join(', ')}`)
const goal = Number(f.goal ?? (await ask.select('Minutes a day', GOALS.map(g => ({ value: String(g), label: `${g} min` })), String(known.goal ?? 30))))
if (!(goal > 0 && goal <= 240)) die('--goal: minutes a day, 1 to 240')
const answers: LearnerAnswers = { name, ...(gender !== 'unset' ? { gender } : {}), target, base, level, goal }
if (!interactive) p.log.info(`${name}${gender !== 'unset' ? ` (${gender})` : ''} learns ${langName(target)} from ${langName(base)}, ${level}, ${goal} min a day.`)
if (gender === 'unset') p.log.warn('No grammatical gender set: the tutor asks the learner (or run lani-setup again with --gender).')
const culture = cultureFor(join(projectDir, 'companion/cultures'), target)
if (!culture) die(`No village speaks ${langName(target)} yet (companion/cultures).`)
if (target !== 'sl') p.log.warn(`Slovene is Lani's first language; the ${langName(target)} village (${culture}) has less content so far.`)

// --- 3. their data --------------------------------------------------------------------------------------------------------------

p.log.step("3/8 · The learner's data")
const idDefault = f.id ?? (existing ? undefined : idOf(name))
const id = existing ? undefined : await ask.text({ message: 'A short id for the learner (their data directory is named after it)', initial: idDefault, validate: v => (ID.test(v) ? undefined : 'lowercase letters, digits and dashes, starting with a letter') })
const dataDefault = existing ?? (f.data ? resolve(expandHome(f.data, homeOf())) : defaultDataDir(id!))
let dataDir = resolve(
  expandHome(
    f.data ?? (await ask.text({ message: "Where the learner's data lives (a git repository of its own; it's what to keep safe)", initial: tilde(dataDefault), validate: v => (v.trim() ? undefined : 'a directory') })),
    homeOf(),
  ),
)
const voiceCache = resolve(expandHome(cfg.LANI_VOICE_CACHE ?? defaultVoiceCache(), homeOf()))
const releaseDir = resolve(expandHome(cfg.LANI_RELEASE_DIR ?? defaultReleaseDir(), homeOf()))
if (holdsLearner(dataDir)) {
  if (source && source !== dataDir) die(`${tilde(dataDir)} holds a learner already. Import into a directory without one (--data), or move it away first.`)
  if (existing && existing !== dataDir) die(`${tilde(dataDir)} holds another learner's data. lani-setup sets up one learner (the others: companion/bin/lani-profile).`)
  applyLearner(dataDir, answers) && p.log.success("Updated the learner's profile.")
} else if (existing && existing !== dataDir) {
  // a new place for the same learner: the data repository is copied; the old one stays until you remove it
  const s = spin()
  s.start(`Copying the data repository to ${tilde(dataDir)}`)
  mkdirSync(dirname(dataDir), { recursive: true })
  const cp = run('cp', ['-a', existing, dataDir])
  if (cp.status !== 0) die(`copying ${existing}: ${cp.stderr}`)
  s.stop(`Copied to ${tilde(dataDir)}; ${tilde(existing)} is left as it was (remove it once everything works).`)
  applyLearner(dataDir, answers)
} else if (source) {
  const caches = (oneOf('caches', f.caches, ['move', 'copy'] as const) ??
    (await ask.select<Caches>('The voice clips and the APKs of the old install', [
      { value: 'move', label: `Move them out (to ${tilde(voiceCache)} and ${tilde(releaseDir)})`, hint: 'the old install loses them' },
      { value: 'copy', label: 'Copy them', hint: 'the old install keeps working; needs the space twice' },
    ], 'move'))) as Caches
  const s = spin()
  s.start(`Importing ${tilde(source)}`)
  try {
    const r = importData(source, dataDir, { voiceCache, releaseDir, caches })
    s.stop(`Imported ${r.files} files and ${r.results} session result${r.results === 1 ? '' : 's'} into ${tilde(dataDir)}.`)
    p.log.info(`Voice clips: ${r.voice.replaceAll(homeOf(), '~')}.\nAPKs: ${r.releases.replaceAll(homeOf(), '~')}.`)
  } catch (e) {
    s.error('Not imported.')
    die((e as Error).message)
  }
  applyLearner(dataDir, answers)
} else {
  initLearnerData(projectDir, dataDir, { name, target, base, level, gender: answers.gender, goal, culture, results: join(dataDir, 'results') })
  p.log.success(`A new learner, ${name}, in ${tilde(dataDir)}.`)
}
const init = dataRepo(projectDir, ['init', dataDir, '-m', source ? `lani-setup: imported from ${source}` : `lani-setup: ${name}'s data`])
if (!init.ok) die(`The data repository: ${init.err}`)
p.log.success(`The data is a git repository${init.out ? ` (commit ${init.out})` : ''}: commits after each session; secrets and caches stay out (.gitignore).`)
Object.assign(cfg, {
  LANI_DATA_DIR: tilde(dataDir),
  LANI_VOICE_CACHE: tilde(voiceCache),
  LANI_RELEASE_DIR: tilde(releaseDir),
  LANI_CULTURE: culture === 'primorska' ? undefined : culture,
})
save()

// --- 4. a remote ------------------------------------------------------------------------------------------------------------------

p.log.step('4/8 · A private remote for the data')
const origin = git(dataDir, ['remote', 'get-url', 'origin'])
const ghReady = !!Bun.which('gh') && run('gh', ['auth', 'status']).status === 0
const remoteFlag = f.remote?.trim()
type RemoteChoice = 'keep' | 'none' | 'url' | 'github'
let choice: RemoteChoice = remoteFlag === undefined ? (origin.ok ? 'keep' : 'none') : remoteFlag === 'none' ? 'none' : remoteFlag.startsWith('github') && !remoteFlag.includes('github.com') ? 'github' : 'url'
if (remoteFlag === undefined && interactive) {
  p.log.message('A copy of the learner\'s data elsewhere: each session is pushed there after its commit. Private only: it is personal.')
  choice = await ask.select<RemoteChoice>('Where to?', [
    ...(origin.ok ? [{ value: 'keep' as const, label: `Keep ${origin.out}` }] : []),
    { value: 'none', label: 'None for now', hint: 'the data stays on this machine (and in lani-backup snapshots)' },
    { value: 'url', label: 'A git URL I have', hint: 'an empty private repository' },
    ...(ghReady ? [{ value: 'github' as const, label: 'Create a private GitHub repository', hint: 'with gh' }] : []),
  ], origin.ok ? 'keep' : 'none')
}
let remote: string | undefined = choice === 'keep' ? origin.out : undefined
/** A GitHub repository that isn't private: refused, it would publish the learner's data. */
const publicOnGithub = (url: string) => {
  const repo = githubRepoOf(url)
  if (!repo || !ghReady) return false
  const v = run('gh', ['repo', 'view', repo, '--json', 'visibility', '--jq', '.visibility'])
  return v.status === 0 && v.stdout.trim() !== 'PRIVATE'
}
if (choice === 'url') {
  remote = remoteFlag && remoteFlag !== 'url' ? remoteFlag : await ask.text({ message: 'The git URL (an empty private repository)', placeholder: 'git@github.com:you/lani-learner.git', validate: v => (v.trim() ? undefined : 'a URL') })
  if (publicOnGithub(remote)) die(`${remote} is not a private repository: the learner's data would be public. Make it private first.`)
  const reach = git(dataDir, ['ls-remote', remote], 30_000)
  if (!reach.ok) {
    p.log.warn(`Can't reach ${remote} now: ${reach.err.split('\n')[0]}`)
    if (!(await ask.confirm('Use it anyway?', false, true))) remote = undefined
  }
} else if (choice === 'github') {
  if (!ghReady) die('--remote github needs gh, signed in (gh auth login).')
  const login = run('gh', ['api', 'user', '--jq', '.login']).stdout.trim()
  const wanted = remoteFlag?.includes(':') ? remoteFlag.split(':')[1] : `${login}/lani-${idOf(name)}`
  const repo = interactive ? await ask.text({ message: 'The repository (owner/name)', initial: wanted, validate: v => (/^[\w.-]+\/[\w.-]+$/.test(v) ? undefined : 'owner/name') }) : wanted
  const files = repoFiles(dataDir)
  p.note(
    [`${files.count} files, ${size(files.bytes)}: the learner profile and databases, the village, what the tutor made, the session results.`, 'The largest:', ...files.top.map(x => `  ${size(x.bytes).padStart(7)}  ${x.path}`), 'Never: the tokens, the bridge key, the voice clips, the APKs (.gitignore).'].join('\n'),
    'What it will hold',
  )
  if (await ask.confirm(`Create the private repository ${repo} on GitHub?`, true, true)) {
    const exists = run('gh', ['repo', 'view', repo, '--json', 'visibility', '--jq', '.visibility'])
    if (exists.status === 0) {
      if (exists.stdout.trim() !== 'PRIVATE') die(`${repo} exists and is not private: the learner's data would be public.`)
      p.log.info(`${repo} exists already (private): using it.`)
    } else {
      const made = run('gh', ['repo', 'create', repo, '--private', '--description', `Lani: ${name}'s learner data (private)`])
      if (made.status !== 0) die(`gh repo create: ${(made.stderr || made.stdout).trim()}`)
      p.log.success(`Created ${repo} (private).`)
    }
    const ssh = run('gh', ['config', 'get', 'git_protocol']).stdout.trim() === 'ssh'
    remote = ssh ? `git@github.com:${repo}.git` : `https://github.com/${repo}.git`
  }
}
if (choice === 'none') remote = undefined
if (remote) {
  const set = origin.ok ? (origin.out === remote ? { ok: true, err: '' } : git(dataDir, ['remote', 'set-url', 'origin', remote])) : git(dataDir, ['remote', 'add', 'origin', remote])
  if (!set.ok) die(`git remote: ${set.err}`)
  cfg.LANI_DATA_REMOTE = remote
  cfg.LANI_DATA_PUSH = cfg.LANI_DATA_PUSH === 'off' ? 'off' : undefined
  save()
  if (await ask.confirm(`Push the data to ${tilde(remote)} now?`, true, f.push)) {
    const s = spin()
    s.start('Pushing')
    const pushed = git(dataDir, ['push', '-u', 'origin', 'HEAD'], 300_000)
    if (pushed.ok) s.stop(`Pushed to ${tilde(remote)}.`)
    else s.error(`Not pushed: ${pushed.err.split('\n').slice(-2).join(' ')} (the next session's commit tries again).`)
  }
} else {
  if (origin.ok && choice === 'none') p.log.info(`The repository keeps its origin (${origin.out}), but nothing is pushed there any more.`)
  cfg.LANI_DATA_REMOTE = undefined
  save()
  p.log.info('No remote: the data stays on this machine.')
}

// --- 5. voices and speech --------------------------------------------------------------------------------------------------------

p.log.step('5/8 · Voices and speech')
const keys = keysFile()
const hadKey = hasKey(keys, 'ELEVENLABS_API_KEY')
if (f['elevenlabs-key-file']) {
  const k = (f['elevenlabs-key-file'] === '-' ? await Bun.stdin.text() : readFileSync(expandHome(f['elevenlabs-key-file'], homeOf()), 'utf8')).trim()
  if (!k) die('--elevenlabs-key-file: empty')
  setKey(keys, 'ELEVENLABS_API_KEY', k)
  p.log.success(`The ElevenLabs key is in ${tilde(keys)} (mode 600).`)
} else if (interactive) {
  p.log.message('Natural voices for the villagers come from ElevenLabs (a paid plan; Starter is enough). Without a key, the phone speaks.')
  const what = hadKey
    ? await ask.select('An ElevenLabs key is set', [{ value: 'keep', label: 'Keep it' }, { value: 'replace', label: 'Replace it' }, { value: 'remove', label: 'Remove it' }], 'keep')
    : 'replace'
  if (what === 'remove') {
    setKey(keys, 'ELEVENLABS_API_KEY', undefined)
    p.log.info('Removed the ElevenLabs key.')
  } else if (what === 'replace') {
    const k = await ask.secret(hadKey ? 'The new ElevenLabs API key' : 'Your ElevenLabs API key (Enter to skip)')
    if (k) {
      setKey(keys, 'ELEVENLABS_API_KEY', k)
      p.log.success(`Saved in ${tilde(keys)} (mode 600); it's never shown.`)
    } else if (!hadKey) p.log.info('No key: the phone speaks. Add one any time: run lani-setup again.')
  }
} else p.log.info(hadKey ? `An ElevenLabs key is set (${tilde(keys)}).` : 'No ElevenLabs key: the phone speaks (--elevenlabs-key-file to add one).')
const gepard = env.LANI_GEPARD_URL || 'http://127.0.0.1:8795'
const stt = env.LANI_STT_URL || 'http://127.0.0.1:8796'
const share = (n: string) => [join(homeOf(), '.local/share', n), join(homeOf(), '.local/share', n.replace('lani', 'fluent'))].find(existsSync)
const workers = [
  { what: 'Local voice (Gepard, Slovene)', url: gepard, installed: share('lani-voice'), how: 'companion/voice-local/install.sh (a GPU helps; about 15 GB)' },
  { what: 'Local speech recognition (Whisper)', url: stt, installed: share('lani-stt'), how: 'companion/stt-local/install.sh (a GPU helps; about 17 GB)' },
]
const lines: string[] = []
for (const w of workers) {
  const up = await workerAnswers(w.url)
  lines.push(up ? `✓ ${w.what}: running at ${w.url}` : w.installed ? `– ${w.what}: installed (${tilde(w.installed)}), not answering at ${w.url} (systemctl --user start ${w.what.includes('voice') ? 'lani-voice' : 'lani-stt'})` : `– ${w.what}: not installed; the ${w.what.includes('voice') ? "phone's voice" : "phone's recognizer"} is used. Optional: ${w.how}`)
}
p.note(lines.join('\n'), 'On this machine')

// --- 6. the network -------------------------------------------------------------------------------------------------------------

p.log.step('6/8 · How the phone reaches the tutor')
const ts = tailscaleStatus()
type Net = 'tailscale' | 'lan' | 'skip'
// without a terminal: --network, else what is set up already (nothing is chosen for you)
let net: Net = oneOf('network', f.network, ['tailscale', 'lan', 'skip'] as const) ?? (cfg.LANI_NETWORK as Net | undefined) ?? 'skip'
if (!f.network && interactive) {
  net = await ask.select<Net>('The phone reaches the bridge over', [
    { value: 'tailscale', label: 'Tailscale (recommended)', hint: ts.running ? `this machine is ${ts.host}` : (ts.why ?? '') },
    { value: 'lan', label: 'My own HTTPS reverse proxy on the LAN', hint: 'advanced' },
    { value: 'skip', label: 'Decide later' },
  ], (cfg.LANI_NETWORK as Net | undefined) ?? 'tailscale')
}
if (net === 'tailscale') {
  cfg.LANI_NETWORK = 'tailscale'
  cfg.LANI_PUBLIC_URL = f.url ?? undefined
  if (!ts.running) {
    p.log.warn(`${ts.why}. Install Tailscale (https://tailscale.com/download), sign in (tailscale up), then run lani-setup again. The phone needs the Tailscale app, signed in to the same tailnet.`)
  } else if (tailscaleServes(port)) {
    p.log.success(`Tailscale Serve publishes the bridge: the app's address is https://${ts.host}`)
  } else {
    p.log.message(`Tailscale Serve publishes the bridge (127.0.0.1:${port}) on your tailnet only, over HTTPS: https://${ts.host}. Never Funnel (the internet).`)
    if (await ask.confirm(`Run tailscale serve --bg ${port} now?`, true, f.serve)) {
      const s = run('tailscale', ['serve', '--bg', String(port)])
      if (s.status === 0) p.log.success(`Served: https://${ts.host}`)
      else p.log.warn(`tailscale serve: ${(s.stderr || s.stdout).trim().split('\n')[0]}. You may need: sudo tailscale set --operator=$USER, then tailscale serve --bg ${port}`)
    } else p.log.info(`Later: tailscale serve --bg ${port}`)
  }
} else if (net === 'lan') {
  cfg.LANI_NETWORK = 'lan'
  p.log.message(`The app needs HTTPS (the release build refuses plain HTTP). Put a reverse proxy with a certificate (Caddy, nginx) in front of 127.0.0.1:${port}, on a name your phone resolves. Keep the bridge on 127.0.0.1.`)
  const url = f.url ?? (await ask.text({ message: 'Its HTTPS address', initial: cfg.LANI_PUBLIC_URL, placeholder: 'https://lani.home.example', validate: v => (/^https:\/\/[^\s/]+/.test(v) ? undefined : 'an https:// address') }))
  if (!/^https:\/\/[^\s/]+/.test(url)) die('--url: an https:// address (the app refuses plain HTTP)')
  cfg.LANI_PUBLIC_URL = url.replace(/\/+$/, '')
} else p.log.info('Later: run lani-setup again (docs/setup.md, "The network").')
save()

// --- 7. running the tutor ------------------------------------------------------------------------------------------------------

p.log.step('7/8 · Running the tutor')
type RunMode = 'tmux' | 'service' | 'systemd' | 'launchd' | 'skip'
const linux = platform() === 'linux'
const mac = platform() === 'darwin'
let runMode: RunMode = oneOf('run', f.run, ['tmux', 'service', 'systemd', 'launchd', 'skip'] as const) ?? (cfg.LANI_BRIDGE_MODE === 'service' ? 'service' : 'tmux')
if (!f.run && interactive) {
  p.log.message('The tutor is a Claude Code session in tmux: it keeps running when you close the terminal (reattach: tmux attach -t lani).')
  runMode = await ask.select<RunMode>('How should it run?', [
    { value: 'tmux', label: 'tmux, the bridge inside the tutor session', hint: 'simplest' },
    { value: 'service', label: 'tmux, the bridge as a service of its own', hint: 'bridge updates without restarting the tutor' },
    ...(linux && Bun.which('systemctl') ? [{ value: 'systemd' as const, label: 'The bridge as a systemd user service', hint: 'starts at login' }] : []),
    ...(mac ? [{ value: 'launchd' as const, label: 'The bridge as a launchd agent', hint: 'starts at login' }] : []),
    { value: 'skip', label: 'Decide later' },
  ], runMode)
}
if (runMode !== 'skip') cfg.LANI_BRIDGE_MODE = runMode === 'tmux' ? 'classic' : 'service'
save()
const bunDir = dirname(Bun.which('bun') ?? process.execPath)
if (runMode === 'systemd') {
  if (!linux) die('--run systemd: Linux only (macOS: --run launchd)')
  const unitDir = join(env.XDG_CONFIG_HOME || join(homeOf(), '.config'), 'systemd', 'user')
  const unit = join(unitDir, 'lani-bridge@.service')
  mkdirSync(unitDir, { recursive: true })
  writeFileSync(unit, systemdUnit(projectDir, bunDir))
  p.log.success(`Wrote ${tilde(unit)}.`)
  if (await ask.confirm('Enable and start it now (systemctl --user enable --now lani-bridge@default)?', true, true)) {
    for (const args of [['--user', 'daemon-reload'], ['--user', 'enable', '--now', 'lani-bridge@default']]) {
      const r = run('systemctl', args)
      if (r.status !== 0) p.log.warn(`systemctl ${args.join(' ')}: ${(r.stderr || r.stdout).trim().split('\n')[0]}`)
    }
  }
  const linger = run('loginctl', ['show-user', env.USER ?? '', '-p', 'Linger', '--value']).stdout.trim()
  p.log.message('systemd stops your user services when you log out, unless "linger" is on for your user. With linger, the bridge runs from boot without a login. (The tutor itself is in tmux: start it after a reboot with lani-session.)')
  if (linger === 'yes') p.log.info('Linger is on.')
  else if (interactive && (await ask.confirm('Turn linger on (loginctl enable-linger)?', false))) {
    const r = run('loginctl', ['enable-linger', env.USER ?? ''])
    r.status === 0 ? p.log.success('Linger is on.') : p.log.warn(`loginctl: ${(r.stderr || '').trim()}`)
  } else p.log.info('Linger stays off (turn it on any time: loginctl enable-linger).')
} else if (runMode === 'launchd') {
  if (!mac) die('--run launchd: macOS only (Linux: --run systemd)')
  const plist = join(homeOf(), 'Library/LaunchAgents', `${LAUNCHD_LABEL}.plist`)
  mkdirSync(dirname(plist), { recursive: true })
  writeFileSync(plist, launchdPlist(projectDir, bunDir, join(homeOf(), 'Library/Logs/lani-bridge.log')))
  p.log.success(`Wrote ${tilde(plist)}: it runs while you're logged in.`)
  if (await ask.confirm('Load it now (launchctl bootstrap)?', true, true)) {
    const r = run('launchctl', ['bootstrap', `gui/${process.getuid?.() ?? ''}`, plist])
    if (r.status !== 0) p.log.warn(`launchctl: ${(r.stderr || r.stdout).trim().split('\n')[0]}`)
  }
}
const tutor = runMode === 'skip' ? undefined : 'companion/bin/lani-session'
if (tutor && interactive && Bun.which('claude') && Bun.which('tmux') && !env.TMUX) {
  p.log.message('On its first start Claude Code asks you to confirm the development channel (channels are a research preview) and to approve the lani MCP server. Then detach with Ctrl-b d to come back here.')
  if (await ask.confirm('Start the tutor now?', true)) run(bin('lani-session'), [], { inherit: true })
} else if (tutor) p.log.info(`Start the tutor: ${tutor} (detach with Ctrl-b d).`)

// --- 8. the phone ---------------------------------------------------------------------------------------------------------------

p.log.step('8/8 · The phone')
p.log.message('The app: docs/setup.md, "Get the app" (an APK from GitHub Releases soon; or build it, with Docker or the Android SDK).')
const up = await bridgeAnswers(port)
if (up && (await ask.confirm('The bridge runs: show the QR code to pair your phone?', true, f.pair))) run(bin('lani-pair'), [], { inherit: true })
else if (!up) p.log.info('Once the tutor runs: companion/bin/lani-pair shows the QR code; scan it in the app.')

p.note(
  [
    `Learner      ${name}${answers.gender ? ` (${answers.gender})` : ''}, ${langName(target)} from ${langName(base)}, ${level}, ${goal} min a day`,
    `Data         ${tilde(dataDir)} (git${cfg.LANI_DATA_REMOTE ? `, pushed to ${tilde(cfg.LANI_DATA_REMOTE)}` : ''})`,
    `Voice cache  ${tilde(voiceCache)}`,
    `APKs         ${tilde(releaseDir)}`,
    `Network      ${cfg.LANI_NETWORK ?? 'not chosen yet'}${cfg.LANI_PUBLIC_URL ? ` (${cfg.LANI_PUBLIC_URL})` : net === 'tailscale' && ts.host ? ` (https://${ts.host})` : ''}`,
    `Running      ${runMode === 'skip' ? 'not chosen yet' : runMode}`,
    `Settings     ${tilde(cfgPath)}${hasKey(keys, 'ELEVENLABS_API_KEY') ? `, keys in ${tilde(keys)}` : ''}`,
  ].join('\n'),
  'Set up',
)
p.outro('Done. Run lani-setup again to change anything; docs/troubleshooting.md if something is off.')
