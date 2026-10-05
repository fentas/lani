// What lani-setup does without asking (companion/bin/lani-setup asks: cli/setup.ts): lani.env's schema and writer,
// keys.env, the tools it needs, importing an earlier install's data with its caches moved out, the learner profile, the
// data repository, the network and the service files. docs/setup.md is the guide.
import { spawnSync } from 'node:child_process'
import {
  chmodSync, cpSync, existsSync, mkdirSync, readdirSync, readFileSync, readlinkSync, renameSync, rmSync, rmdirSync, statSync, writeFileSync,
} from 'node:fs'
import { homedir } from 'node:os'
import { basename, dirname, join, resolve } from 'node:path'
import { APP_LANGUAGES, culturesIn, DEFAULT_CULTURE } from './cultures'
import { expandHome, parseEnvFile } from './env'
import { LANGUAGES } from './learners'

type Env = Record<string, string | undefined>

// --- where things go ---------------------------------------------------------------------------------------------------

export const homeOf = (env: Env = process.env) => env.HOME || homedir()
const dataHome = (env: Env) => env.XDG_DATA_HOME || join(homeOf(env), '.local', 'share')
const cacheHome = (env: Env) => env.XDG_CACHE_HOME || join(homeOf(env), '.cache')

/** ~/.local/share/lani/<id>: a learner's data repository. */
export const defaultDataDir = (id: string, env: Env = process.env) => join(dataHome(env), 'lani', id)
/** ~/.cache/lani/voice: the voice clips. */
export const defaultVoiceCache = (env: Env = process.env) => join(cacheHome(env), 'lani', 'voice')
/** ~/.local/share/lani/releases: the app's APKs. */
export const defaultReleaseDir = (env: Env = process.env) => join(dataHome(env), 'lani', 'releases')

/** [p] with ~ for the home directory, for lani.env and for messages. */
export function tilde(p: string, env: Env = process.env): string {
  const home = homeOf(env)
  return p === home ? '~' : p.startsWith(home + '/') ? `~${p.slice(home.length)}` : p
}

// --- lani.env ------------------------------------------------------------------------------------------------------------

export type Setting = { key: string; group: 'learner' | 'node'; doc: string; example: string }

/** lani.env's documented settings, in the file's order (docs/setup.md, "lani.env", has the same table). */
export const SETTINGS: Setting[] = [
  { key: 'LANI_DATA_DIR', group: 'learner', example: '~/.local/share/lani/<id>', doc: "The learner's data: a git repository of its own (lani-setup). Default: the checkout's data/." },
  { key: 'LANI_VOICE_CACHE', group: 'learner', example: '~/.cache/lani/voice', doc: 'The voice clips the bridge made (voice.db, files/): a cache. Default: <data>/app/voice.' },
  { key: 'LANI_CULTURE', group: 'learner', example: 'primorska', doc: "The village's culture pack: primorska (Slovene), friuli (Italian), kaernten (German), lakeland (English). Default: primorska." },
  { key: 'LANI_BRIDGE_PORT', group: 'learner', example: '8790', doc: "The bridge's port on 127.0.0.1. Default: 8790." },
  { key: 'LANI_PUBLIC_URL', group: 'learner', example: 'https://lani.home.example', doc: "The HTTPS address the app reaches the bridge at, when it isn't Tailscale Serve's (a reverse proxy on the LAN). The pairing QR code carries it." },
  { key: 'LANI_DATA_REMOTE', group: 'learner', example: 'git@github.com:<you>/lani-<id>.git', doc: "A private git remote for the data repository (its origin). Each commit after a session is pushed there in the background, never forced. Empty: no push." },
  { key: 'LANI_DATA_PUSH', group: 'learner', example: 'on', doc: 'on: push after each commit when LANI_DATA_REMOTE is set; off: commit only. Default: on.' },
  { key: 'LANI_DATA_AUTOCOMMIT', group: 'learner', example: 'on', doc: 'on: commit the data repository after each session and when the tutor session ends; off: never. Default: on.' },
  { key: 'LANI_NETWORK', group: 'node', example: 'tailscale', doc: 'How the phone reaches the bridge: tailscale (Tailscale Serve, recommended) or lan (your HTTPS reverse proxy, LANI_PUBLIC_URL).' },
  { key: 'LANI_BRIDGE_MODE', group: 'node', example: 'classic', doc: 'classic: the bridge runs inside the tutor session; service: on its own (lani-bridge, tmux or systemd), the session with a shim. Default: classic.' },
  { key: 'LANI_RELEASE_DIR', group: 'node', example: '~/.local/share/lani/releases', doc: "The app's APKs (release-app writes them, every bridge serves them for the app's self-update). Default: <data>/app/release." },
  { key: 'LANI_GEPARD_URL', group: 'node', example: 'http://127.0.0.1:8795', doc: 'The local voice worker (companion/voice-local). Skipped while it is down.' },
  { key: 'LANI_STT_URL', group: 'node', example: 'http://127.0.0.1:8796', doc: 'The local speech recognition worker (companion/stt-local). Without it, the phone recognizes speech.' },
  { key: 'LANI_BACKUP_DIR', group: 'node', example: '~/.local/share/lani/backups', doc: "lani-backup's snapshots." },
  { key: 'LANI_BRIDGE_HOST', group: 'node', example: '127.0.0.1', doc: 'The address the bridge listens on. Keep 127.0.0.1: Tailscale Serve or your reverse proxy is in front.' },
]

const HEADER = `# Lani's settings for this machine (docs/setup.md, "lani.env").
# companion/bin/lani-setup writes this file: run it again to change them, or edit it. KEY=value lines; ~ and $HOME are
# expanded, nothing else. The environment wins over a line here. Secrets (the ElevenLabs key) go in keys.env, mode 600.
`

/** One `# doc` paragraph, wrapped at 118 columns. */
function comment(text: string): string {
  const out: string[] = []
  let line = '#'
  for (const w of text.split(/\s+/)) {
    if (line.length + 1 + w.length > 118) {
      out.push(line)
      line = '#'
    }
    line += ` ${w}`
  }
  out.push(line)
  return out.join('\n')
}

/**
 * lani.env with [values] (a key with an empty or no value is written commented out, with its example), each setting
 * with its documentation; the lines of [previous] that set other keys are kept at the end.
 */
export function renderLaniEnv(values: Record<string, string | undefined>, previous = ''): string {
  const out = [HEADER]
  for (const [group, title] of [['learner', "The learner: the default learner on this machine (others: companion/bin/lani-profile)"], ['node', 'This machine']] as const) {
    out.push(`# --- ${title} ---\n`)
    for (const s of SETTINGS.filter(s => s.group === group)) {
      const v = values[s.key]
      out.push(`${comment(s.doc)}\n${v ? `${s.key}=${quote(v)}` : `# ${s.key}=${s.example}`}\n`)
    }
  }
  const known = new Set(SETTINGS.map(s => s.key))
  const others = previous.split(/\r?\n/).filter(l => {
    const m = /^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=/.exec(l)
    return m && !known.has(m[1])
  })
  if (others.length) out.push(`# --- Other settings (kept from before) ---\n${others.join('\n')}\n`)
  return out.join('\n')
}

/** A value as lani.env reads it back: quoted when it has spaces or a #. */
const quote = (v: string) => (/[\s#'"]/.test(v) ? `"${v.replace(/"/g, '')}"` : v)

/** lani.env's current values (only the keys it sets) and its text. */
export function readLaniEnv(path: string, env: Env = process.env): { values: Record<string, string>; text: string } {
  try {
    const text = readFileSync(path, 'utf8')
    return { values: parseEnvFile(text, homeOf(env)), text }
  } catch {
    return { values: {}, text: '' }
  }
}

/** Writes lani.env ([renderLaniEnv]); paths under the home directory as ~/…. */
export function writeLaniEnv(path: string, values: Record<string, string | undefined>, previous = '') {
  mkdirSync(dirname(path), { recursive: true, mode: 0o700 })
  const tmp = `${path}.tmp`
  const homed = Object.fromEntries(Object.entries(values).map(([k, v]) => [k, v && v.startsWith('/') ? tilde(v) : v]))
  writeFileSync(tmp, renderLaniEnv(homed, previous))
  renameSync(tmp, path)
}

// --- keys.env ------------------------------------------------------------------------------------------------------------

/** Whether keys.env sets [name] (to something). The value is never returned. */
export function hasKey(path: string, name: string): boolean {
  try {
    return readFileSync(path, 'utf8').split(/\r?\n/).some(l => new RegExp(`^\\s*(?:export\\s+)?${name}\\s*=\\s*\\S`).test(l))
  } catch {
    return false
  }
}

/**
 * Sets [name] in keys.env to [value] (undefined: removes it), keeping its other lines. The file is mode 600 and its
 * directory 700; the value is never printed.
 */
export function setKey(path: string, name: string, value: string | undefined) {
  mkdirSync(dirname(path), { recursive: true, mode: 0o700 })
  let lines: string[] = []
  try {
    lines = readFileSync(path, 'utf8').split(/\r?\n/)
    if (lines.at(-1) === '') lines.pop()
  } catch {}
  const is = (l: string) => new RegExp(`^\\s*(?:export\\s+)?${name}\\s*=`).test(l)
  const kept = lines.filter(l => !is(l))
  if (value !== undefined) kept.push(`${name}=${value.replace(/[\r\n]/g, '')}`)
  const tmp = `${path}.tmp`
  writeFileSync(tmp, kept.join('\n') + (kept.length ? '\n' : ''), { mode: 0o600 })
  chmodSync(tmp, 0o600)
  renameSync(tmp, path)
}

// --- the tools --------------------------------------------------------------------------------------------------------

export type Tool = { name: string; required: boolean; for: string; how: string; path?: string; version?: string; problem?: string }

const toolSpecs: (Omit<Tool, 'path' | 'version' | 'problem'> & { args: string[] })[] = [
  { name: 'bun', args: ['--version'], required: true, for: 'runs the bridge', how: 'https://bun.sh' },
  { name: 'claude', args: ['--version'], required: true, for: 'Claude Code: the tutor', how: 'https://code.claude.com' },
  { name: 'python3', args: ['--version'], required: true, for: 'keeps the learner databases', how: 'your package manager (3.9 or newer)' },
  { name: 'git', args: ['--version'], required: true, for: "the learner's data repository", how: 'your package manager' },
  { name: 'tmux', args: ['-V'], required: true, for: 'keeps the tutor running', how: 'your package manager' },
  { name: 'tailscale', args: ['version'], required: false, for: 'the phone reaches the tutor', how: 'https://tailscale.com/download' },
  { name: 'adb', args: ['version'], required: false, for: 'installs the app over USB', how: 'Android platform-tools' },
  { name: 'docker', args: ['--version'], required: false, for: 'builds the app without an SDK', how: 'https://docs.docker.com/get-docker/' },
  { name: 'gh', args: ['--version'], required: false, for: 'a private GitHub repository', how: 'https://cli.github.com' },
]

/** Each tool: where it is and its version, or why not. */
export function checkTools(): Tool[] {
  return toolSpecs.map(({ args, ...t }) => {
    const path = Bun.which(t.name) ?? undefined
    if (!path) return { ...t, problem: 'not found' }
    const p = spawnSync(path, args, { encoding: 'utf8', timeout: 10_000 })
    // the version number alone ("git version 2.55.0" → 2.55.0; "2.1.99 (Claude Code)" → 2.1.99)
    const first = `${p.stdout ?? ''}${p.stderr ?? ''}`.trim().split('\n')[0] ?? ''
    const version = /\d+(\.\d+)+[\w.-]*/.exec(first)?.[0] ?? first.slice(0, 16)
    if (p.status !== 0) return { ...t, path, problem: `doesn't run (${(p.stderr || p.error?.message || '').trim().split('\n')[0] || `exit ${p.status}`})` }
    if (t.name === 'python3') {
      const m = /(\d+)\.(\d+)/.exec(version ?? '')
      if (m && (Number(m[1]) < 3 || (Number(m[1]) === 3 && Number(m[2]) < 9))) return { ...t, path, version, problem: `${version}: 3.9 or newer is needed` }
    }
    return { ...t, path, version }
  })
}

// --- the learner -------------------------------------------------------------------------------------------------------

export const LEVELS = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'] as const
export const GOALS = [10, 15, 20, 30, 45, 60] as const
const NEXT: Record<string, string> = { A1: 'A2', A2: 'B1', B1: 'B2', B2: 'C1', C1: 'C2' }

export type LearnerAnswers = { name: string; gender?: 'male' | 'female'; target: string; base: string; level: string; goal: number }

/** A profile id from a name: "Jan" → jan, "Ana Marija Novak" → ana-marija-novak (letters, digits, dashes). */
export function idOf(name: string): string {
  const s = name.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
  const id = /^[a-z]/.test(s) ? s : `l-${s}`
  return id.slice(0, 24).replace(/-+$/, '') || 'learner'
}

/** What a learner profile says of the questions lani-setup asks (to offer as the answers). */
export function learnerOf(dataDir: string): Partial<LearnerAnswers> {
  try {
    const l = JSON.parse(readFileSync(join(dataDir, 'learner-profile.json'), 'utf8'))?.learner ?? {}
    const plain = (v: unknown) => (typeof v === 'string' && v.trim() && !/\{[^{}]*\}/.test(v) ? v.trim() : undefined)
    const code = (v: unknown, name: unknown) => {
      const c = plain(v)?.toLowerCase()
      if (c && (APP_LANGUAGES as readonly string[]).includes(c)) return c
      const n = plain(name)?.toLowerCase()
      return APP_LANGUAGES.find(k => LANGUAGES[k].toLowerCase() === n)
    }
    const level = plain(l.current_level)?.toUpperCase()
    return {
      ...(plain(l.name) ? { name: plain(l.name) } : {}),
      ...(l.gender === 'male' || l.gender === 'female' ? { gender: l.gender } : {}),
      ...(code(l.target_language_code, l.target_language) ? { target: code(l.target_language_code, l.target_language) } : {}),
      ...(code(l.base_language_code, l.base_language ?? l.native_language) ? { base: code(l.base_language_code, l.base_language ?? l.native_language) } : {}),
      ...(level && (LEVELS as readonly string[]).includes(level) ? { level } : {}),
      ...(typeof l.daily_goal_minutes === 'number' ? { goal: l.daily_goal_minutes } : {}),
    }
  } catch {
    return {}
  }
}

/**
 * The answers into an existing learner-profile.json: name, gender, base language, level (and the target level when it
 * isn't above it any more), daily goal. The language learned stays (the databases are that language's). Returns whether
 * it changed.
 */
export function applyLearner(dataDir: string, a: LearnerAnswers, today = new Date().toISOString().slice(0, 10)): boolean {
  const path = join(dataDir, 'learner-profile.json')
  const before = readFileSync(path, 'utf8')
  const p = JSON.parse(before)
  const l = (p.learner ??= {})
  const oldBase = l.base_language
  l.name = a.name
  if (a.gender) l.gender = a.gender
  l.base_language = LANGUAGES[a.base] ?? a.base
  l.base_language_code = a.base
  if (!l.native_language || /\{/.test(l.native_language) || l.native_language === oldBase) l.native_language = l.base_language
  if (!l.target_language_code && l.target_language === (LANGUAGES[a.target] ?? a.target)) l.target_language_code = a.target
  if (l.current_level !== a.level) {
    l.current_level = a.level
    l.level_since = today
  }
  const rank = (x: unknown) => LEVELS.indexOf(String(x ?? '').toUpperCase() as (typeof LEVELS)[number])
  if (rank(l.target_level) <= rank(a.level)) l.target_level = NEXT[a.level] ?? 'C2'
  l.daily_goal_minutes = a.goal
  const after = JSON.stringify(p, null, 2) + '\n'
  if (after === before || JSON.stringify(JSON.parse(before)) === JSON.stringify(p)) return false
  writeFileSync(`${path}.tmp`, after)
  renameSync(`${path}.tmp`, path)
  return true
}

/** The culture pack a learner of [target] lives in: the default one when it speaks it, else the first that does. */
export function cultureFor(culturesDir: string, target: string): string | undefined {
  const { complete } = culturesIn(culturesDir, target)
  return complete.includes(DEFAULT_CULTURE) ? DEFAULT_CULTURE : complete[0]
}

// --- importing an earlier install --------------------------------------------------------------------------------------

/** Processes (other than this one) that have files open under [dir]: a bridge still running on it. Linux only (/proc). */
export function openFilesUnder(dir: string): { pid: number; cmd: string }[] {
  const out: { pid: number; cmd: string }[] = []
  const root = resolve(dir) + '/'
  let pids: string[] = []
  try {
    pids = readdirSync('/proc').filter(p => /^\d+$/.test(p) && Number(p) !== process.pid)
  } catch {
    return out
  }
  for (const pid of pids) {
    let fds: string[] = []
    try {
      fds = readdirSync(`/proc/${pid}/fd`)
    } catch {
      continue
    }
    for (const fd of fds) {
      let target = ''
      try {
        target = readlinkSync(`/proc/${pid}/fd/${fd}`)
      } catch {
        continue
      }
      if (target.startsWith(root)) {
        let cmd = ''
        try {
          cmd = readFileSync(`/proc/${pid}/cmdline`, 'utf8').replace(/\0/g, ' ').trim()
        } catch {}
        out.push({ pid: Number(pid), cmd })
        break
      }
    }
  }
  return out
}

/** Whether [dir] holds a learner (their learner-profile.json). */
export const holdsLearner = (dir: string) => existsSync(join(dir, 'learner-profile.json'))

/** Where an earlier install kept its session results: <source>/results, or the checkout's results/ next to data/. */
export function resultsOf(source: string): string | undefined {
  if (existsSync(join(source, 'results'))) return join(source, 'results')
  const beside = join(dirname(source), 'results')
  return basename(source) === 'data' && existsSync(beside) ? beside : undefined
}

const SKIP_TOP = new Set(['README.md', '.gitkeep'])

function filesIn(dir: string): number {
  let n = 0
  for (const e of readdirSync(dir, { withFileTypes: true })) n += e.isDirectory() ? filesIn(join(dir, e.name)) : 1
  return n
}

/** Moves [src] to [dst] (a rename, or a copy and then the source removed across file systems); [dst] must be absent or empty. */
export function moveDir(src: string, dst: string) {
  if (existsSync(dst)) {
    if (readdirSync(dst).length) throw new Error(`${dst} isn't empty`)
    rmdirSync(dst)
  }
  mkdirSync(dirname(dst), { recursive: true })
  try {
    renameSync(src, dst)
  } catch (e) {
    if ((e as NodeJS.ErrnoException).code !== 'EXDEV') throw e
    cpSync(src, dst, { recursive: true, preserveTimestamps: true })
    const [a, b] = [filesIn(src), filesIn(dst)]
    if (a !== b) throw new Error(`copying ${src} to ${dst}: ${b} of ${a} files`)
    rmSync(src, { recursive: true })
  }
}

export type Caches = 'move' | 'copy'

export type ImportReport = {
  files: number
  results: number
  voice: string
  releases: string
}

/**
 * An earlier install's data (Lani's or Fluent's data/, or a data repository) into [dest]: the learner files as they
 * are (the tokens and the bridge's key too: phones stay paired; they stay out of git), its session results into
 * [dest]/results, and its caches to their own places: the voice clips to [voiceCache], the APKs to [releaseDir],
 * moved (or copied). A cache whose new place has one already is left where it was. Refuses while a process has files
 * open under [source] (its bridge is running).
 */
export function importData(source: string, dest: string, o: { voiceCache: string; releaseDir: string; caches: Caches }): ImportReport {
  source = resolve(source)
  dest = resolve(dest)
  if (!holdsLearner(source)) throw new Error(`${source} holds no learner (no learner-profile.json)`)
  if (source === dest) throw new Error('the data is there already')
  if (holdsLearner(dest)) throw new Error(`${dest} holds a learner already`)
  const busy = openFilesUnder(source)
  if (busy.length) {
    throw new Error(`a program still has files open in ${source}: ${busy.map(b => `pid ${b.pid} (${b.cmd.slice(0, 80)})`).join(', ')}. Stop the old tutor and its bridge first (docs/migrate-from-fluent.md, step 3).`)
  }
  mkdirSync(dest, { recursive: true })
  const voiceSrc = join(source, 'app', 'voice')
  const releaseSrc = join(source, 'app', 'release')
  let files = 0
  cpSync(source, dest, {
    recursive: true,
    preserveTimestamps: true,
    filter: src => {
      const rel = src.slice(source.length + 1)
      if (rel === '') return true
      if (SKIP_TOP.has(rel) || src === voiceSrc || src === releaseSrc || rel === 'results') return false
      if (!statSync(src).isDirectory()) files++
      return true
    },
  })
  let results = 0
  const from = resultsOf(source)
  if (from) {
    cpSync(from, join(dest, 'results'), {
      recursive: true,
      preserveTimestamps: true,
      filter: src => {
        if (src === from) return true
        if (SKIP_TOP.has(basename(src)) && dirname(src) === from) return false
        if (!statSync(src).isDirectory()) results++
        return true
      },
    })
  }
  mkdirSync(join(dest, 'results'), { recursive: true })

  let voice = 'none to move'
  if (existsSync(join(voiceSrc, 'voice.db'))) {
    if (existsSync(join(o.voiceCache, 'voice.db'))) voice = `left in ${voiceSrc}: ${o.voiceCache} has clips already`
    else if (o.caches === 'move') {
      moveDir(voiceSrc, o.voiceCache)
      voice = `moved to ${o.voiceCache}`
    } else {
      mkdirSync(dirname(o.voiceCache), { recursive: true })
      cpSync(voiceSrc, o.voiceCache, { recursive: true, preserveTimestamps: true })
      voice = `copied to ${o.voiceCache}`
    }
  }
  let releases = 'none to move'
  if (existsSync(releaseSrc)) {
    const apks = readdirSync(releaseSrc).filter(f => /\.apk$/.test(f) || f === 'latest.json')
    if (!apks.length) releases = 'none to move'
    else if (existsSync(join(o.releaseDir, 'latest.json'))) releases = `left in ${releaseSrc}: ${o.releaseDir} has a release already`
    else {
      mkdirSync(o.releaseDir, { recursive: true })
      for (const f of apks) {
        if (o.caches === 'move') {
          try {
            renameSync(join(releaseSrc, f), join(o.releaseDir, f))
          } catch (e) {
            if ((e as NodeJS.ErrnoException).code !== 'EXDEV') throw e
            cpSync(join(releaseSrc, f), join(o.releaseDir, f), { preserveTimestamps: true })
            rmSync(join(releaseSrc, f))
          }
        } else cpSync(join(releaseSrc, f), join(o.releaseDir, f), { preserveTimestamps: true })
      }
      if (o.caches === 'move' && !readdirSync(releaseSrc).length) rmdirSync(releaseSrc)
      releases = `${o.caches === 'move' ? 'moved' : 'copied'} to ${o.releaseDir} (${apks.length} files)`
    }
  }
  return { files, results, voice, releases }
}

// --- the data repository ------------------------------------------------------------------------------------------------

/** Runs .claude/hooks/lani_data_repo.py (one implementation of the repository's rules, the hooks' too). */
export function dataRepo(projectDir: string, args: string[], env: Env = process.env): { ok: boolean; out: string; err: string } {
  const p = spawnSync('python3', [join(projectDir, '.claude/hooks/lani_data_repo.py'), ...args], { encoding: 'utf8', env: env as NodeJS.ProcessEnv, timeout: 120_000 })
  return { ok: p.status === 0, out: (p.stdout ?? '').trim(), err: (p.stderr ?? '').trim() }
}

/** git in [dir]: never a prompt for a password; [ok] and what it said. */
export function git(dir: string, args: string[], timeout = 60_000): { ok: boolean; out: string; err: string } {
  const p = spawnSync('git', ['-C', dir, ...args], {
    encoding: 'utf8',
    timeout,
    env: { ...process.env, GIT_TERMINAL_PROMPT: '0', GIT_SSH_COMMAND: process.env.GIT_SSH_COMMAND ?? 'ssh -o BatchMode=yes' },
  })
  return { ok: p.status === 0, out: (p.stdout ?? '').trim(), err: (p.stderr ?? p.error?.message ?? '').trim() }
}

/** "owner/name" of a GitHub URL (https or ssh), else undefined. */
export function githubRepoOf(url: string): string | undefined {
  const m = /github\.com[:/]([^/\s]+)\/([^/\s]+?)(?:\.git)?\/?$/.exec(url.trim())
  return m ? `${m[1]}/${m[2]}` : undefined
}

/** What [dir]'s repository holds (what a push sends): the number of files, their size, the largest first. */
export function repoFiles(dir: string): { count: number; bytes: number; top: { path: string; bytes: number }[] } {
  const ls = git(dir, ['ls-files', '-z'])
  const files = ls.out.split('\0').filter(Boolean).map(path => {
    try {
      return { path, bytes: statSync(join(dir, path)).size }
    } catch {
      return { path, bytes: 0 }
    }
  })
  files.sort((a, b) => b.bytes - a.bytes)
  return { count: files.length, bytes: files.reduce((n, f) => n + f.bytes, 0), top: files.slice(0, 8) }
}

export const size = (n: number) => (n >= 1e9 ? `${(n / 1e9).toFixed(1)} GB` : n >= 1e6 ? `${(n / 1e6).toFixed(1)} MB` : n >= 1e3 ? `${(n / 1e3).toFixed(0)} kB` : `${n} B`)

// --- the network ---------------------------------------------------------------------------------------------------------

export type Tailnet = { installed: boolean; running: boolean; host?: string; why?: string }

/** `tailscale status`: whether this machine is on a tailnet, and its HTTPS name there. */
export function tailscaleStatus(): Tailnet {
  if (!Bun.which('tailscale')) return { installed: false, running: false, why: 'Tailscale is not installed' }
  const p = spawnSync('tailscale', ['status', '--json'], { encoding: 'utf8', timeout: 10_000 })
  try {
    const s = JSON.parse(p.stdout)
    const host = String(s?.Self?.DNSName ?? '').replace(/\.$/, '') || undefined
    const running = s?.BackendState === 'Running'
    return { installed: true, running, host, ...(running ? {} : { why: `Tailscale is ${String(s?.BackendState ?? 'not running').toLowerCase()} (tailscale up)` }) }
  } catch {
    return { installed: true, running: false, why: (p.stderr || 'tailscale status failed').trim().split('\n')[0] }
  }
}

/** Whether `tailscale serve` publishes 127.0.0.1:[port] already (its status names it as a proxy target). */
export function tailscaleServes(port: number): boolean {
  const p = spawnSync('tailscale', ['serve', 'status', '--json'], { encoding: 'utf8', timeout: 10_000 })
  return p.status === 0 && new RegExp(`(127\\.0\\.0\\.1|localhost):${port}\\b`).test(p.stdout)
}

/** Whether a bridge answers on 127.0.0.1:[port]. */
export async function bridgeAnswers(port: number): Promise<boolean> {
  try {
    return (await fetch(`http://127.0.0.1:${port}/health`, { signal: AbortSignal.timeout(1_500) })).ok
  } catch {
    return false
  }
}

/** Whether a worker answers its /health at [url]. */
export async function workerAnswers(url: string): Promise<boolean> {
  try {
    return (await fetch(`${url.replace(/\/+$/, '')}/health`, { signal: AbortSignal.timeout(1_500) })).ok
  } catch {
    return false
  }
}

// --- running it ------------------------------------------------------------------------------------------------------------

/** The bridge's systemd user unit (companion/systemd/lani-bridge@.service) for this checkout and this bun. */
export function systemdUnit(projectDir: string, bunDir: string): string {
  return readFileSync(join(projectDir, 'companion/systemd/lani-bridge@.service'), 'utf8').replaceAll('@REPO@', projectDir).replaceAll('@BUN_DIR@', bunDir)
}

export const LAUNCHD_LABEL = 'page.lani.bridge'

/** The bridge's launchd agent (macOS): lani-bridge exec for the default learner, started again when it exits. */
export function launchdPlist(projectDir: string, bunDir: string, logFile: string): string {
  const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;')
  return `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<!-- Lani's bridge service for the default learner (docs/setup.md, "Running it"); written by lani-setup. -->
<plist version="1.0">
<dict>
  <key>Label</key><string>${LAUNCHD_LABEL}</string>
  <key>ProgramArguments</key>
  <array>
    <string>/bin/bash</string>
    <string>${esc(join(projectDir, 'companion/bin/lani-bridge'))}</string>
    <string>exec</string>
    <string>--profile</string>
    <string>default</string>
  </array>
  <key>WorkingDirectory</key><string>${esc(projectDir)}</string>
  <key>EnvironmentVariables</key>
  <dict>
    <key>PATH</key><string>${esc(bunDir)}:/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin</string>
  </dict>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
  <key>StandardOutPath</key><string>${esc(logFile)}</string>
  <key>StandardErrorPath</key><string>${esc(logFile)}</string>
</dict>
</plist>
`
}
