// The node's settings, read once by every entry point (they import this module first, so the rest of the code reads
// LANI_* only):
// - lani.env (~/.config/lani/lani.env, docs/setup.md "lani.env"): KEY=value lines; the environment wins over it. Its
//   settings for this machine are copied into process.env where it doesn't set them. The default learner's (their data,
//   voice cache, village, port: LEARNER_KEYS) are not: a process of another learner must never inherit them, so
//   learnerSetting() reads them, for the default learner only.
// - A node set up before the project was renamed from Fluent to Lani (docs/migrate-from-fluent.md) keeps working: every
//   LANI_* variable falls back to its old FLUENT_* name, and the directories named lani (~/.config/lani,
//   ~/.local/state/lani, ~/.cache/lani) to the ones named fluent while only those exist.
import { existsSync, readFileSync, realpathSync, statSync } from 'node:fs'
import { homedir } from 'node:os'
import { join, resolve } from 'node:path'

type Env = Record<string, string | undefined>

const LEGACY_PREFIX = 'FLUENT_'

/** Copies each FLUENT_X of [env] into LANI_X when LANI_X isn't set (LANI_X wins). Returns [env]. */
export function adoptLegacyEnv<T extends Env>(env: T): T {
  for (const [k, v] of Object.entries(env)) {
    if (!k.startsWith(LEGACY_PREFIX) || v === undefined) continue
    const name = `LANI_${k.slice(LEGACY_PREFIX.length)}`
    if (env[name] === undefined) (env as Env)[name] = v
  }
  return env
}

adoptLegacyEnv(process.env)

/** [base]/lani, or [base]/fluent while only that one exists (a node from before the rename). */
export function laniDir(base: string): string {
  const dir = join(base, 'lani')
  const legacy = join(base, 'fluent')
  return !existsSync(dir) && existsSync(legacy) ? legacy : dir
}

/** ~/.config/lani ($LANI_CONFIG_DIR), or ~/.config/fluent while only that one exists. */
export const configDir = (env: Env = process.env) => env.LANI_CONFIG_DIR || laniDir(join(env.HOME || homedir(), '.config'))

/** A file of the config directory: under ~/.config/lani, or under ~/.config/fluent while only the old one has it. */
export function configFile(name: string, env: Env = process.env): string {
  if (env.LANI_CONFIG_DIR) return join(env.LANI_CONFIG_DIR, name)
  const home = env.HOME || homedir()
  const file = join(home, '.config', 'lani', name)
  const legacy = join(home, '.config', 'fluent', name)
  return !existsSync(file) && existsSync(legacy) ? legacy : file
}

/** The API keys the tutor session and the bridge service load: $LANI_KEYS_FILE, else keys.env in the config directory. */
export const keysFile = (env: Env = process.env) => env.LANI_KEYS_FILE || configFile('keys.env', env)

// --- lani.env -------------------------------------------------------------------------------------------------------

/**
 * The node's settings: $LANI_ENV_FILE (/dev/null: none; the process's own when [env] doesn't name one, so a test that
 * isolates itself stays isolated), else lani.env in the config directory.
 */
export const envFile = (env: Env = process.env) => env.LANI_ENV_FILE || process.env.LANI_ENV_FILE || configFile('lani.env', env)

/**
 * The default learner's settings. Another learner's come in their processes' environment (profiles/profiles.json), so
 * these are never copied into the environment: learnerSetting() reads them. The same list is in
 * .claude/hooks/lani_paths.py and companion/bin/lani-env.sh.
 */
export const LEARNER_KEYS = new Set([
  'LANI_DATA_DIR', 'LANI_RESULTS_DIR', 'LANI_VOICE_CACHE', 'LANI_CULTURE', 'LANI_LANDSCAPE', 'LANI_BRIDGE_PORT',
  'LANI_PUBLIC_URL', 'LANI_TOWN_URL', 'LANI_DATA_AUTOCOMMIT', 'LANI_DATA_REMOTE', 'LANI_DATA_PUSH', 'LANI_PROFILE', 'LANI_CHILD',
])

/** "~", "~/x", "$HOME/x" and "${HOME}/x" under [home]; anything else as it is. */
export function expandHome(v: string, home: string = homedir()): string {
  if (v === '~' || v.startsWith('~/')) return home + v.slice(1)
  return v.replace(/^(\$\{HOME\}|\$HOME)(?=\/|$)/, home)
}

/**
 * lani.env's KEY=value lines. Blank lines, `# comments` and `export ` are skipped. A value in quotes is what is
 * between them; otherwise it ends at a ` #` comment. A leading ~ or $HOME is expanded, nothing else. Only LANI_*
 * keys count (secrets belong in keys.env).
 */
export function parseEnvFile(text: string, home: string = homedir()): Record<string, string> {
  const out: Record<string, string> = {}
  for (const line of text.split(/\r?\n/)) {
    const m = /^\s*(?:export\s+)?(LANI_[A-Z0-9_]*)\s*=(.*)$/.exec(line)
    if (!m) continue
    let v = m[2].trim()
    if (v.startsWith('"') || v.startsWith("'")) {
      const end = v.indexOf(v[0], 1)
      v = end > 0 ? v.slice(1, end) : v.slice(1)
    } else {
      v = v.replace(/(^|\s)#.*$/, '').trim()
    }
    out[m[1]] = expandHome(v, home)
  }
  return out
}

const parsed = new Map<string, { stamp: string; values: Record<string, string> }>()

/** All of lani.env's settings; {} without the file. Read again when it changes. */
export function readEnvFile(env: Env = process.env): Record<string, string> {
  const path = envFile(env)
  try {
    const st = statSync(path)
    if (!st.isFile()) return {}
    const stamp = `${st.mtimeMs}:${st.size}`
    const hit = parsed.get(path)
    if (hit?.stamp === stamp) return hit.values
    const values = parseEnvFile(readFileSync(path, 'utf8'), env.HOME || homedir())
    parsed.set(path, { stamp, values })
    return values
  } catch {
    return {}
  }
}

/** lani.env's settings for this machine (not LEARNER_KEYS) into [env] where it doesn't set them. Returns [env]. */
export function adoptEnvFile<T extends Env>(env: T): T {
  for (const [k, v] of Object.entries(readEnvFile(env))) {
    if (!LEARNER_KEYS.has(k) && env[k] === undefined) (env as Env)[k] = v
  }
  return env
}

/** Whether [a] and [b] name the same directory (~ expanded; symlinks followed where the paths exist). */
export function samePath(a: string, b: string, home: string = homedir()): boolean {
  const norm = (p: string) => {
    const r = resolve(expandHome(p, home))
    try {
      return realpathSync(r)
    } catch {
      return r
    }
  }
  return norm(a) === norm(b)
}

/**
 * lani.env's [key] (one of LEARNER_KEYS) for the default learner; undefined when it isn't set there, or when [env] is
 * another learner's process: LANI_PROFILE names another profile, or LANI_DATA_DIR another directory than lani.env's.
 * The environment's own value comes first: see [setting].
 */
export function learnerSetting(key: string, env: Env = process.env): string | undefined {
  const profile = env.LANI_PROFILE?.trim()
  if (profile && profile !== 'default') return undefined
  const file = readEnvFile(env)
  const v = file[key]
  if (v === undefined || v === '') return undefined
  if (key !== 'LANI_DATA_DIR' && env.LANI_DATA_DIR) {
    const mine = file.LANI_DATA_DIR
    if (!mine || !samePath(env.LANI_DATA_DIR, mine, env.HOME || homedir())) return undefined
  }
  return v
}

/** [key] from the environment, else (a learner's setting) lani.env's for the default learner. */
export const setting = (key: string, env: Env = process.env): string | undefined => env[key] ?? learnerSetting(key, env)

adoptEnvFile(process.env)
