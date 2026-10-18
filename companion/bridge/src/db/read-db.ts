// .claude/hooks/read-db.py: the six databases of the home language (or of --language), the computed fields and every
// language's summary, as one JSON object on stdout. Exit 0, 1 (files missing), 2 (--language isn't a language, or its
// data can't be read). Another language whose data can't be read is left out of the summaries, with a warning.
import {
  FILE, homeLanguage, languageCode, languageDir, languageName, listed, freshDatabases, loadJsonFile, migrateQuietly, normDate,
  otherCodes, summary,
} from './languages'
import { backupsDir, dataDir } from './paths'
import { Parser } from './pyargparse'
import { dumpIndented } from './pyjson'
import {
  add, contains, eq, excStr, fmtDate, get, getItem, int, isDict, isExc, items, le, len, merge, or, ordinal, plusDays,
  PyDict, repr, str as pyStr, strptime, SysExit, truthy, typeErr, typeName, type DateTime, type Py,
} from './py'
import { exists, pjoin, type Ctx } from './sys'

/** read-db.py's database keys -> update-db.py's. */
const KEYS: [string, string][] = [
  ['learner_profile', 'profile'],
  ['progress_db', 'progress'],
  ['mistakes_db', 'mistakes'],
  ['mastery_db', 'mastery'],
  ['spaced_repetition', 'sr'],
  ['session_log', 'log'],
]

const loadJson = (path: string): Py => (exists(path) ? loadJsonFile(path) : null)

/** next_session_id: 'session-NNN' (another language's 'session-<code>-NNN'). */
export function nextSessionId(sessions: Py, code: string | null = null): string {
  const prefix = code ? `session-${code}-` : 'session-'
  if (!truthy(sessions)) return `${prefix}001`
  const lastId = get(getItem(sessions, -1), 'session_id', '')
  if (typeof lastId !== 'string') throw typeErr(`expected string or bytes-like object, got '${typeName(lastId)}'`)
  const m = /(\p{Nd}+)/u.exec(lastId)
  const n = m ? add(int(m[1]), 1) : len(sessions) + 1
  return `${prefix}${pyStr(n).padStart(3, '0')}`
}

function loadDir(d: string): [PyDict, string[]] {
  const databases = new PyDict()
  const missing: string[] = []
  for (const [key, k] of KEYS) {
    const path = pjoin(d, FILE[k])
    const data = loadJson(path)
    if (data === null) {
      missing.push(path)
      databases.set(key, new PyDict())
    } else databases.set(key, data)
  }
  return [databases, missing]
}

/** Another language's data that can't be read: a file that isn't JSON (or can't be opened), or a learner profile whose
 *  `learner` isn't an object. */
class Unreadable extends Error {}

/** load_language: another language's six databases (fresh ones for a language never practised); Unreadable when its data
 *  can't be read. */
function loadLanguage(dir: string, code: string, homeProfile: Py, today: string): PyDict {
  const d = languageDir(dir, code)
  const fresh = freshDatabases(code, today, 'A1', get(or(get(homeProfile, 'learner'), new PyDict()), 'name', ''))
  const databases = new PyDict()
  for (const [key, k] of KEYS) {
    const path = pjoin(d, FILE[k])
    let data: Py
    try {
      data = loadJson(path)
    } catch (e) {
      if (isExc(e, 'OSError') || isExc(e, 'ValueError')) throw new Unreadable(`${path}: ${excStr(e)}`)
      throw e
    }
    databases.set(key, isDict(data) ? data : fresh[k])
  }
  const own = databases.get('learner_profile') as PyDict
  const learner = own.get('learner')
  if (truthy(learner) && !isDict(learner)) {
    throw new Unreadable(`${pjoin(d, FILE.profile)}: its learner is not an object (${typeName(learner)})`)
  }
  const merged = own.copy()
  merged.set('learner', merge(or(get(homeProfile, 'learner'), new PyDict()), or(own.get('learner'), new PyDict())))
  if (!own.has('preferences') && contains(homeProfile, 'preferences')) merged.set('preferences', getItem(homeProfile, 'preferences'))
  databases.set('learner_profile', merged)
  return databases
}

function computed(databases: PyDict, now: DateTime, code: string | null): PyDict {
  const today = fmtDate(now)
  const yesterday = fmtDate(plusDays(now, -1))
  const sr = databases.get('spaced_repetition', new PyDict())
  const its = get(sr, 'items', new PyDict())
  const due = items(its).filter(([, item]) => le(normDate(get(item, 'due_date', '')), today)).map(([iid]) => iid)
  const log = databases.get('session_log', new PyDict())
  const sessions = get(log, 'sessions', [])
  const profile = databases.get('learner_profile', new PyDict())
  const lastUpdated = get(profile, 'last_updated', '')
  const streakActive = eq(normDate(lastUpdated), today) || eq(normDate(lastUpdated), yesterday)
  let daysSince: Py = null
  try {
    daysSince = truthy(lastUpdated) ? ordinal(now) - ordinal(strptime(lastUpdated)) : null
  } catch (e) {
    if (!isExc(e, 'ValueError')) throw e
    daysSince = null
  }
  return PyDict.of({
    today,
    due_reviews_count: due.length,
    due_review_items: due,
    next_session_id: nextSessionId(sessions, code),
    streak_active: streakActive,
    days_since_last_session: daysSince,
  })
}

function summaries(ctx: Ctx, dir: string, homeDbs: PyDict, home: string | null, today: string): Py[] {
  const profile = homeDbs.get('learner_profile', new PyDict())
  const since = new Map<string, Py>()
  for (const e of listed(profile)) since.set(getItem(e, 'code') as string, e.get('since'))
  const out: Py[] = [summary(homeDbs, home, 'home', today)]
  for (const code of otherCodes(dir, profile)) {
    // another language's data, however broken, is left out: it never fails the home language's answer (GET /state)
    try {
      const dbs = loadLanguage(dir, code, profile, today)
      const lp = dbs.get('learner_profile') as PyDict
      const source = or(lp.get('source'), 'visits')
      out.push(summary(dbs, code, source, today, or(since.get(code) ?? null, lp.get('profile_created'))))
    } catch (e) {
      if (e instanceof SysExit) throw e
      ctx.eprint(`[Lani] Warning: ${languageName(code)} (${code}) is left out of languages, its data can't be read: ${excStr(e)}`)
    }
  }
  return out
}

export function main(ctx: Ctx, argv: string[]): void {
  const DATA_DIR = dataDir(ctx)
  ctx.dataRoot = DATA_DIR
  const ap = new Parser(ctx, 'read-db.py', 'read-db')
  ap.arg('--language')
  const args = ap.parseArgs(argv)

  migrateQuietly(ctx, DATA_DIR, backupsDir(ctx))

  let [databases, missing] = loadDir(DATA_DIR)
  const homeProfile = databases.get('learner_profile', new PyDict())
  const home = homeLanguage(homeProfile)

  let code: string | null = null
  const language = args.get('language') ?? null
  if (language !== null) {
    code = languageCode(language)
    if (code === null) {
      ctx.eprint(`[Lani] Error: --language: a language code (it) or name (Italian), not ${repr(language)}`)
      throw new SysExit(2)
    }
    if (code === home) code = null
  }

  const now = ctx.now()
  const today = fmtDate(now)
  const homeDbs = databases
  if (code !== null) {
    try {
      databases = loadLanguage(DATA_DIR, code, homeProfile, today)
    } catch (e) {
      if (!(e instanceof Unreadable)) throw e
      ctx.eprint(`[Lani] Error: --language ${code}: its data can't be read: ${e.message}`)
      throw new SysExit(2)
    }
    missing = []
  }

  const result = new PyDict()
  result.set('databases', databases)
  result.set('computed', computed(databases, now, code))
  result.set('language', code ?? home)
  result.set('languages', summaries(ctx, DATA_DIR, homeDbs, home, today))
  if (missing.length) result.set('_warnings', missing.map(m => `Missing file: ${m}`))

  ctx.out.push(dumpIndented(result) + '\n')
  throw new SysExit(missing.length ? 1 : 0)
}
