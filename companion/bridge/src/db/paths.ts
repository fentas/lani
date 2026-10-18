// .claude/hooks/lani_paths.py: where the learner's data is, from the environment (LANI_DATA_DIR, lani.env's for the
// default learner, $CLAUDE_PROJECT_DIR/data, ./data, ~/.claude/lani-data), with the same precedence and the same
// adoption of FLUENT_* names and of lani.env's machine settings into the environment (on import, there; in
// adoptEnv() here, which each script calls first).
import { strip, WS } from './py'
import { abspath, exists, expanduser, home, isFile, mkdirp, pjoin, pnorm, readText, resolve, type Ctx, type Env } from './sys'

const LEGACY_PREFIX = 'FLUENT_'

/** The default learner's settings in lani.env: never copied into the environment. */
const LEARNER_KEYS = new Set([
  'LANI_DATA_DIR', 'LANI_RESULTS_DIR', 'LANI_VOICE_CACHE', 'LANI_CULTURE', 'LANI_LANDSCAPE', 'LANI_BRIDGE_PORT',
  'LANI_PUBLIC_URL', 'LANI_TOWN_URL', 'LANI_DATA_AUTOCOMMIT', 'LANI_DATA_REMOTE', 'LANI_DATA_PUSH', 'LANI_PROFILE',
  'LANI_CHILD',
])

/** adopt_legacy_env: each FLUENT_X into LANI_X where that isn't set. */
function adoptLegacyEnv(env: Env) {
  for (const [k, v] of Object.entries(env)) {
    if (k.startsWith(LEGACY_PREFIX) && v !== undefined) {
      const n = 'LANI_' + k.slice(LEGACY_PREFIX.length)
      if (!(n in env) || env[n] === undefined) env[n] = v
    }
  }
}

/** [base]/[name], or the same named fluent while only that one exists. */
export function laniDir(base: string, name = 'lani'): string {
  const fresh = pjoin(base, name)
  const old = pjoin(base, name.replaceAll('lani', 'fluent'))
  return !exists(fresh) && exists(old) ? old : fresh
}

const homeOf = (env: Env) => env.HOME || home(env)

function configFile(name: string, env: Env): string {
  if (env.LANI_CONFIG_DIR) return pjoin(env.LANI_CONFIG_DIR, name)
  const fresh = pjoin(homeOf(env), '.config', 'lani', name)
  const old = pjoin(homeOf(env), '.config', 'fluent', name)
  return !exists(fresh) && exists(old) ? old : fresh
}

function envFile(env: Env, processEnv: Env): string {
  const named = env.LANI_ENV_FILE || processEnv.LANI_ENV_FILE
  return named ? pnorm(named) : configFile('lani.env', env)
}

/** expand_home: "~", "~/x", "$HOME/x", "${HOME}/x" under [h]. */
function expandHome(v: string, h: string): string {
  if (v === '~' || v.startsWith('~/')) return h + v.slice(1)
  return v.replace(/^(\$\{HOME\}|\$HOME)(?=\/|$)/, () => h)
}

const LINE = new RegExp(`^[${WS}]*(?:export[${WS}]+)?(LANI_[A-Z0-9_]*)[${WS}]*=(.*)$`, 'u')

/** parse_env_file */
function parseEnvFile(text: string, h: string): Record<string, string> {
  const out: Record<string, string> = {}
  for (const line of text.split(/\r\n|[\n\r\v\f\x1c\x1d\x1e\x85\u2028\u2029]/)) {
    const m = LINE.exec(line)
    if (!m) continue
    let v = strip(m[2])
    if (v[0] === "'" || v[0] === '"') {
      const end = v.indexOf(v[0], 1)
      v = end > 0 ? v.slice(1, end) : v.slice(1)
    } else v = strip(v.replace(new RegExp(`(^|[${WS}])#.*$`, 'u'), ''))
    out[m[1]] = expandHome(v, h)
  }
  return out
}

function readEnvFile(env: Env, processEnv: Env): Record<string, string> {
  const path = envFile(env, processEnv)
  try {
    if (!isFile(path)) return {}
    return parseEnvFile(readText(path), homeOf(env))
  } catch {
    return {}
  }
}

/** lani_paths on import: FLUENT_* names, then lani.env's settings for this machine, into the environment. */
export function adoptEnv(ctx: Ctx): void {
  adoptLegacyEnv(ctx.env)
  for (const [k, v] of Object.entries(readEnvFile(ctx.env, ctx.env))) {
    if (!LEARNER_KEYS.has(k) && !(k in ctx.env && ctx.env[k] !== undefined)) ctx.env[k] = v
  }
}

/** _norm: realpath(abspath(expand_home(p))). */
const norm = (p: string, h: string, cwd: string) => resolve(abspath(expandHome(p, h), cwd), '/')

/** learner_setting: lani.env's [key] for the default learner; null for another learner's process. */
export function learnerSetting(ctx: Ctx, key: string, env: Env = ctx.env): string | null {
  const profile = strip(env.LANI_PROFILE || '')
  if (profile && profile !== 'default') return null
  const values = readEnvFile(env, ctx.env)
  const v = values[key]
  if (!v) return null
  if (key !== 'LANI_DATA_DIR' && env.LANI_DATA_DIR) {
    const mine = values.LANI_DATA_DIR
    if (!mine || norm(env.LANI_DATA_DIR, homeOf(env), ctx.cwd) !== norm(mine, homeOf(env), ctx.cwd)) return null
  }
  return v
}

/** setting: the environment's [key], else lani.env's for the default learner. */
export function setting(ctx: Ctx, key: string, env: Env = ctx.env): string | null {
  const v = env[key]
  return v !== undefined ? v : learnerSetting(ctx, key, env)
}

const resolved = (ctx: Ctx, p: string) => resolve(expanduser(pnorm(p), ctx.env), ctx.cwd)

const cache = new WeakMap<Ctx, string>()

/** data_dir(): the learner's data directory (it isn't made). */
export function dataDir(ctx: Ctx): string {
  const known = cache.get(ctx)
  if (known) return known
  const d = findDataDir(ctx)
  cache.set(ctx, d)
  return d
}

function findDataDir(ctx: Ctx): string {
  const env = ctx.env.LANI_DATA_DIR
  if (env) return resolved(ctx, env)
  const configured = learnerSetting(ctx, 'LANI_DATA_DIR')
  if (configured) return resolved(ctx, configured)
  const project = ctx.env.CLAUDE_PROJECT_DIR
  if (project) {
    const candidate = resolve(pjoin(project, 'data'), ctx.cwd)
    if (exists(pjoin(candidate, 'learner-profile.json'))) return candidate
  }
  const cwdData = resolve(pjoin(ctx.cwd, 'data'), ctx.cwd)
  if (exists(pjoin(cwdData, 'learner-profile.json'))) return cwdData
  return resolve(laniDir(pjoin(home(ctx.env), '.claude'), 'lani-data'), ctx.cwd)
}

/** ensure_data_dir() */
export function ensureDataDir(ctx: Ctx): string {
  const d = dataDir(ctx)
  mkdirp(d)
  return d
}

/** backups_dir() */
export const backupsDir = (ctx: Ctx) => pjoin(dataDir(ctx), '.backups')

/** ensure_backups_dir() */
export function ensureBackupsDir(ctx: Ctx): string {
  const b = backupsDir(ctx)
  mkdirp(b)
  return b
}
