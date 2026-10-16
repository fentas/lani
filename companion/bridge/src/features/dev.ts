// The developer's data from the app (companion/README.md "Developer data on the node"). While the app's developer
// setting is on (seven taps on the version line) and it is paired, it sends the shadow tutor's export (POST /dev/shadow,
// lani.shadow-export/v0) and its own log, redacted (POST /dev/logs, text), after the shadow's day closed, when it goes to
// the background (at most every 30 minutes) and by "📤 Pošlji vozlišču · Send to the node" in the shadow's panel. The
// developer reads them here, as files under <data>/app/dev/: shadow-<YYYY-MM-DD>.json (that day's latest export) and
// app-log-<YYYY-MM-DD>.txt (that day's uploads, appended), mode 0600, kept 30 days (the day in the name, the node's).
// Never committed: the data repository's .gitignore keeps /app/dev/ out (lani_data_repo.py writes the line, and keeps
// it in with each commit); the bridge adds it at its start to a .gitignore without it. The tutor knows nothing of it:
// no tool, no paragraph of the channel guide.
import { appendFileSync, chmodSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync } from 'node:fs'
import { join } from 'node:path'
import type { Log } from '../config'
import type { FeatureFactory } from '../feature'
import { HttpError, json, readText, type RouteCtx } from '../http'
import { writeAtomic } from '../pairing'

/** The largest upload either route takes (413 beyond). */
export const DEV_MAX = 5 * 1024 * 1024
/** How many days the files are kept, by the day in their name. */
export const DEV_KEEP_DAYS = 30
/** The .gitignore line that keeps the files out of the data repository. */
export const DEV_IGNORE = '/app/dev/'

/** The files' names, with their day. */
const DAY_FILE = /^(?:shadow|app-log)-(\d{4}-\d{2}-\d{2})\.(?:json|txt)$/
/** Lines of a .gitignore that already keep app/dev/ out. */
const IGNORES = new Set(['/app/dev/', '/app/dev', 'app/dev/', 'app/dev', '/app/dev/*', '/app/dev/**'])

const pad = (n: number) => String(n).padStart(2, '0')

/** The node's local day of [ms]: YYYY-MM-DD. */
export function localDay(ms = Date.now()): string {
  const d = new Date(ms)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** A name that says a secret is in its value (a token, an API key, a password; not a count of tokens). */
const SECRET_NAME = String.raw`[\w-]*(?:token(?!s)|api[_-]?key|secret|passw(?:or)?d|authorization|cookie)[\w-]*`
const NAMED = new RegExp(String.raw`\b(${SECRET_NAME})(\s*["']?\s*[=:]\s*["']?\s*)(?:(?:Bearer|Basic|Token)\s+)?([^\s&#"',;<>()\[\]{}]+)`, 'gi')
const SECRET_KEY = new RegExp(`^${SECRET_NAME}$`, 'i')
const BEARER = /\bBearer\s+[A-Za-z0-9._~+/=-]{6,}/gi
const API_KEY = /sk-ant-[A-Za-z0-9_-]{6,}/g
const QUERY = /([?&](?:key|sig|signature|code|auth)=)[^&\s#"']+/gi

/**
 * [text] cleaned of what could carry a secret: the values of [known] (the request's own token), Bearer tokens, API keys
 * (sk-ant-…), the value after a secret's name (token=…, "api_key": "…", Authorization: …, x-api-key: …) and a URL's
 * key, sig or code. The app redacts before it sends (tutor/KeyVault.kt Redact): this is the node's second net.
 */
export function scrub(text: string, known: string[] = []): string {
  let t = text
  for (const k of known) if (k && k.length >= 8) t = t.split(k).join('‹secret›')
  return t
    .replace(API_KEY, 'sk-ant-‹…›')
    .replace(BEARER, 'Bearer ‹…›')
    .replace(NAMED, (_, name: string, sep: string) => `${name}${sep}‹…›`)
    .replace(QUERY, '$1‹…›')
}

/** [v] (parsed JSON) with every string [scrub]bed, and the string value of a key named like a secret replaced. */
export function scrubJson(v: unknown, known: string[] = []): unknown {
  if (typeof v === 'string') return scrub(v, known)
  if (Array.isArray(v)) return v.map(x => scrubJson(x, known))
  if (v && typeof v === 'object') {
    return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, typeof x === 'string' && SECRET_KEY.test(k) ? '‹…›' : scrubJson(x, known)]))
  }
  return v
}

/** Whether git ignores [rel] in the repository [dataDir] (its .gitignore and the user's excludes). */
function gitIgnores(dataDir: string, rel: string): boolean {
  const env: Record<string, string | undefined> = { ...process.env, GIT_TERMINAL_PROMPT: '0' }
  for (const k of ['GIT_DIR', 'GIT_WORK_TREE', 'GIT_INDEX_FILE', 'GIT_COMMON_DIR']) delete env[k]
  try {
    return Bun.spawnSync(['git', '-C', dataDir, 'check-ignore', '-q', '--no-index', '--', rel], { env, stdout: 'ignore', stderr: 'ignore' }).exitCode === 0
  } catch {
    return false // no git on this machine: the line goes in
  }
}

/**
 * The learner's data repository keeps app/dev/ out: a .gitignore (or a repository) without a line that does gets
 * [DEV_IGNORE] appended, once. A plain data directory without a .gitignore is left alone. Returns whether it added it.
 */
export function ignoreDevData(dataDir: string, log: Log): boolean {
  const file = join(dataDir, '.gitignore')
  const repo = existsSync(join(dataDir, '.git'))
  if (!repo && !existsSync(file)) return false
  const text = existsSync(file) ? readFileSync(file, 'utf8') : ''
  if (text.split('\n').some(l => IGNORES.has(l.trim()))) return false
  if (repo && ['app/dev/shadow-2026-01-01.json', 'app/dev/app-log-2026-01-01.txt'].every(rel => gitIgnores(dataDir, rel))) return false
  const sep = text && !text.endsWith('\n') ? '\n' : ''
  appendFileSync(file, `${sep}\n# Developer data from the app (POST /dev/shadow, /dev/logs): stays on this machine (added by the bridge).\n${DEV_IGNORE}\n`)
  log(`dev: ${file} now keeps ${DEV_IGNORE} out`)
  return true
}

/** Deletes the files of [dir] whose day is more than [days] days before [today]. Returns their names. */
export function pruneDevData(dir: string, today: string, days = DEV_KEEP_DAYS): string[] {
  if (!existsSync(dir)) return []
  const oldest = new Date(`${today}T12:00:00`)
  oldest.setDate(oldest.getDate() - days)
  const cut = localDay(oldest.getTime())
  const gone = readdirSync(dir).filter(f => {
    const day = DAY_FILE.exec(f)?.[1]
    return !!day && day < cut
  })
  for (const f of gone) rmSync(join(dir, f), { force: true })
  return gone
}

export const dev: FeatureFactory = ({ cfg, log }) => {
  const dir = join(cfg.appDir, 'dev')
  const ready = () => {
    mkdirSync(dir, { recursive: true, mode: 0o700 })
    chmodSync(dir, 0o700)
    // the learner may have edited the .gitignore since the start
    ignoreDevData(cfg.dataDir, log)
    pruneDevData(dir, localDay())
  }
  /** The request's own token: never written down, whatever the app missed. */
  const tokenOf = (req: Request) => (req.headers.get('authorization') ?? '').replace(/^Bearer\s+/i, '').trim()
  const kb = (n: number) => `${Math.round(n / 1024)} KB`

  async function shadow({ req }: RouteCtx) {
    const text = await readText(req, DEV_MAX, `the export is too large (max ${DEV_MAX} bytes)`)
    let body: unknown
    try {
      body = JSON.parse(text)
    } catch {
      throw new HttpError(400, 'not JSON')
    }
    const schema = (body as { schema?: unknown } | null)?.schema
    if (!body || typeof body !== 'object' || Array.isArray(body) || typeof schema !== 'string' || !schema.startsWith('lani.shadow-export/')) {
      throw new HttpError(400, 'not a shadow export (schema lani.shadow-export/…)')
    }
    ready()
    const name = `shadow-${localDay()}.json`
    writeAtomic(join(dir, name), JSON.stringify(scrubJson(body, [tokenOf(req)])))
    log(`dev: the shadow's export saved (${kb(text.length)}): ${join(dir, name)}`)
    return json({ ok: true, file: `app/dev/${name}`, bytes: text.length })
  }

  async function logs({ req }: RouteCtx) {
    const text = await readText(req, DEV_MAX, `the log is too large (max ${DEV_MAX} bytes)`)
    if (!text.trim()) throw new HttpError(400, 'an empty log')
    ready()
    const name = `app-log-${localDay()}.txt`
    const path = join(dir, name)
    appendFileSync(path, scrub(text, [tokenOf(req)]).replace(/\n*$/, '\n'), { mode: 0o600 })
    chmodSync(path, 0o600)
    const lines = text.split('\n').filter(l => l.trim()).length
    log(`dev: the app's log appended (${lines} lines, ${kb(text.length)}): ${path}`)
    return json({ ok: true, file: `app/dev/${name}`, lines })
  }

  return {
    routes: [
      { method: 'POST', path: '/dev/shadow', handle: shadow },
      { method: 'POST', path: '/dev/logs', handle: logs },
    ],
    start() {
      // Never throw here: the bridge goes on without it.
      try {
        ignoreDevData(cfg.dataDir, log)
        pruneDevData(dir, localDay())
      } catch (e) {
        log(`dev: ${(e as Error).message}`)
      }
    },
  }
}
