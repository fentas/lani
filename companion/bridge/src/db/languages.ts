// .claude/hooks/lani_languages.py: the learning data per language (docs/DB_SCRIPTS.md, "Languages"), the summaries
// read-db.py prints, and the migration of single-language data.
import {
  excStr, F, float, fmtDate, fmtStamp, get, int, isDict, isExc, isList, isNum, isStr, le, lower, or, PyDict, round,
  sortedStrs, strip, strptime, truthy, unique, type Py,
} from './py'
import { loads, dumpIndented, checkEncodable } from './pyjson'
import { copy2, exists, globSuffix, isDir, listdir, mkdirp, pjoin, readText, replace, withSuffix, writeText, type Ctx } from './sys'

/** ISO 639-1 code -> the language's English name (the bridge's LANGUAGES, companion/bridge/src/learners.ts). */
export const LANGUAGES: Record<string, string> = {
  sl: 'Slovene', it: 'Italian', en: 'English', de: 'German', hr: 'Croatian', sr: 'Serbian',
  bs: 'Bosnian', fr: 'French', es: 'Spanish', pt: 'Portuguese', nl: 'Dutch', pl: 'Polish',
  cs: 'Czech', sk: 'Slovak', hu: 'Hungarian', uk: 'Ukrainian', ru: 'Russian',
}
const OTHER_NAMES: Record<string, string> = {
  slovenian: 'sl', 'slovenščina': 'sl', slovensko: 'sl', italiano: 'it', 'italijanščina': 'it',
  deutsch: 'de', 'nemščina': 'de', 'angleščina': 'en', 'français': 'fr', 'español': 'es',
  hrvatski: 'hr', nederlands: 'nl',
}
/** CODE.fullmatch: a language code, two or three letters, the whole string. */
export const CODE = /^[a-z]{2,3}$/
const codeMatch = (s: Py): boolean => isStr(s) && CODE.test(s)
export const CEFR = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2']

/** The six databases, by update-db.py's key, and their file names. */
export const FILES: [string, string][] = [
  ['profile', 'learner-profile.json'],
  ['progress', 'progress-db.json'],
  ['mistakes', 'mistakes-db.json'],
  ['mastery', 'mastery-db.json'],
  ['sr', 'spaced-repetition.json'],
  ['log', 'session-log.json'],
]
export const FILE = Object.fromEntries(FILES) as Record<string, string>

/** language_code: 'it', 'IT', 'Italian' or 'italiano' -> 'it'; another 2-3 letter code as it is; null otherwise. */
export function languageCode(s: Py): string | null {
  if (!isStr(s)) return null
  const t = lower(strip(s))
  if (!t || t.includes('{')) return null
  if (CODE.test(t)) return t
  for (const [code, name] of Object.entries(LANGUAGES)) if (name.toLowerCase() === t) return code
  return OTHER_NAMES[t] ?? null
}

// --- dates --------------------------------------------------------------------------------------------------------

const ISO_DATE = /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/

/** date_of: [v] as YYYY-MM-DD, zero-padded, ASCII digits ("2026-4-24" -> "2026-04-24"); null when strptime refuses it. */
export function dateOf(v: Py): string | null {
  if (!isStr(v)) return null
  try {
    return fmtDate(strptime(v))
  } catch (e) {
    if (isExc(e, 'ValueError')) return null
    throw e
  }
}

/** norm_date: a stored date as the scripts compare it (dateOf when it is a date); anything else as it is. */
export function normDate(v: Py): Py {
  if (isStr(v) && ISO_DATE.test(v)) return v
  return dateOf(v) ?? v
}

export const languageName = (code: string): string => LANGUAGES[code] ?? code

/** home_language: the profile's home_language, else its learner's target language. */
export function homeLanguage(profile: Py): string | null {
  if (!isDict(profile)) return null
  const h = profile.get('home_language')
  if (isStr(h) && CODE.test(h)) return h
  const learner = isDict(profile.get('learner')) ? (profile.get('learner') as PyDict) : new PyDict()
  return languageCode(learner.get('target_language_code')) ?? languageCode(learner.get('target_language'))
}

/** is_migrated */
export function isMigrated(profile: Py): boolean {
  const h = isDict(profile) ? profile.get('home_language') : null
  return isStr(h) && CODE.test(h) && isList((profile as PyDict).get('languages'))
}

export const languageDir = (dataDir: string, code: string) => pjoin(dataDir, 'languages', code)

/** listed: the profile's other languages, as listed: entries with a valid code. */
export function listed(profile: Py): PyDict[] {
  const out = isDict(profile) ? profile.get('languages') : null
  return isList(out) ? (out.filter(e => isDict(e) && codeMatch(e.get('code'))) as PyDict[]) : []
}

/** other_codes: the profile's list, then any directory in data/languages/ it doesn't list. */
export function otherCodes(dataDir: string, profile: Py): string[] {
  const codes = listed(profile).map(e => e.get('code') as string)
  const root = pjoin(dataDir, 'languages')
  if (isDir(root)) {
    for (const name of sortedStrs(listdir(root))) {
      const d = pjoin(root, name)
      if (isDir(d) && CODE.test(name) && !codes.includes(name) && exists(pjoin(d, FILE.profile))) codes.push(name)
    }
  }
  const home = homeLanguage(profile)
  return unique(codes).filter(c => c !== home)
}

function nextLevel(level: string): string {
  const i = CEFR.includes(level) ? CEFR.indexOf(level) : 0
  return CEFR[Math.min(i + 1, CEFR.length - 1)]
}

/** fresh_databases: the six databases of a language practised for the first time, by update-db.py key. */
export function freshDatabases(code: string, date: Py, level = 'A1', learnerName: Py = '', source = 'visits'): Record<string, PyDict> {
  const name = languageName(code)
  const D = PyDict.of
  return {
    profile: D({
      learner: D({ target_language: name, target_language_code: code, current_level: level, target_level: nextLevel(level) }),
      language: code,
      source,
      profile_created: date,
      last_updated: '',
      current_streak_days: 0,
      total_sessions: 0,
      total_study_minutes: 0,
      skills: D({}),
      focus_areas: [],
      achievements: [],
    }),
    progress: D({
      metadata: D({ last_updated: date, language: name, tracking_started: date }),
      overall_stats: D({
        total_sessions: 0, total_exercises: 0, total_correct: 0, total_incorrect: 0,
        accuracy_rate: F(0), total_study_minutes: 0, average_session_duration: 0,
      }),
      accuracy_trend: [],
      skill_progress: D({}),
      weekly_summary: [],
    }),
    mistakes: D({ metadata: D({ last_updated: date, total_patterns_tracked: 0, language: name }), error_patterns: D({}) }),
    mastery: D({ metadata: D({ last_updated: date, language: name }), skills: D({}), patterns: D({}) }),
    sr: D({
      metadata: D({ algorithm: 'SM-2', last_updated: date, total_items_tracked: 0, language: name }),
      review_queue: D({ today: [], tomorrow: [], this_week: [], later: [] }),
      items: D({}),
    }),
    log: D({ metadata: D({ language: name, learner_name: learnerName, total_sessions: 0 }), sessions: [], milestones: [] }),
  }
}

/** list_entry: adds [code] to the home profile's languages, or refreshes its entry's level. */
export function listEntry(profile: PyDict, code: string, langProfile: Py, date: Py): PyDict {
  let langs = profile.get('languages')
  if (!isList(langs)) {
    langs = []
    profile.set('languages', langs)
  }
  let entry = langs.find(e => isDict(e) && e.get('code') === code) as PyDict | undefined
  if (entry === undefined) {
    entry = PyDict.of({ code, name: languageName(code), source: get(langProfile, 'source', 'visits'), since: date })
    langs.push(entry)
  }
  const level = get(or(get(langProfile, 'learner'), new PyDict()), 'current_level')
  if (isStr(level) && level) entry.set('level', level)
  return entry
}

// --- summaries (read-db.py) --------------------------------------------------------------------------------------

/** _int */
function toInt(v: Py): number {
  if (typeof v === 'boolean') return v ? 1 : 0
  if (isNum(v)) return Number(int(round(v)))
  try {
    return Number(int(round(float(v))))
  } catch (e) {
    if (isExc(e, 'TypeError') || isExc(e, 'ValueError')) return 0
    throw e
  }
}

/** rusty: its last review failed. */
export function rusty(item: PyDict): boolean {
  return toInt(item.get('total_reviews')) >= 1 && toInt(item.get('repetitions')) === 0 && item.has('last_quality')
    && toInt(item.get('last_quality')) < 3 && item.get('source') !== 'lookup'
}

/** learned: answered right at least once and not failed since. */
export function learned(item: PyDict): boolean {
  return !rusty(item) && (
    toInt(item.get('repetitions')) >= 1 || toInt(item.get('mastery_level')) >= 1
    || (toInt(item.get('total_reviews')) === 0 && toInt(item.get('last_quality')) >= 3 && item.get('source') !== 'lookup'))
}

/** summary: one language at a glance. */
export function summary(dbs: PyDict, code: string | null, source: Py, today: string, since: Py = null): PyDict {
  const profile = or(dbs.get('learner_profile'), new PyDict())
  const items: Py = or(get(or(dbs.get('spaced_repetition'), new PyDict()), 'items'), new PyDict())
  const list: PyDict[] = isDict(items) ? (items.values().filter(isDict) as PyDict[]) : []
  const learner = () => get(or(get(profile, 'learner'), new PyDict()), 'current_level')
  const level = or(learner(), 'A1')
  const out = PyDict.of({
    code,
    name: code ? languageName(code) : or(get(or(get(profile, 'learner'), new PyDict()), 'target_language'), ''),
    level,
    words: list.filter(i => i.get('type') === 'vocabulary' && learned(i)).length,
    due: list.filter(i => i.get('type') !== 'error_pattern' && le(normDate(or(i.get('due_date'), i.get('next_review'), '9999')), today)).length,
    source,
  })
  if (truthy(since)) out.set('since', since)
  return out
}

// --- migration ---------------------------------------------------------------------------------------------------

/** _save_json: the .json.tmp file, then os.replace. */
export function saveJson(path: string, data: Py): void {
  const tmp = withSuffix(path, '.json.tmp')
  const text = dumpIndented(data) + '\n'
  checkEncodable(text)
  writeText(tmp, text)
  replace(tmp, path)
}

/** json.load of a file. */
export const loadJsonFile = (path: string): Py => loads(readText(path))

function backupDir(backups: string, tag: string): string {
  let d = pjoin(backups, tag)
  let n = 1
  while (exists(d)) {
    n += 1
    d = pjoin(backups, `${tag}-${n}`)
  }
  return d
}

/** migrate: 'migrated', 'already' or 'skipped'. */
export function migrate(ctx: Ctx, dataDir: string, backups: string): string {
  const path = pjoin(dataDir, FILE.profile)
  if (!exists(path)) return 'skipped'
  const profile = loadJsonFile(path)
  if (!isDict(profile)) return 'skipped'
  if (isMigrated(profile)) return 'already'
  const home = homeLanguage(profile)
  if (!home) return 'skipped'

  const backup = backupDir(backups, 'pre-migrate-languages-' + fmtStamp(ctx.now()))
  mkdirp(backup)
  for (const f of globSuffix(dataDir, '.json')) copy2(f, pjoin(backup, f.slice(f.lastIndexOf('/') + 1)))

  profile.set('home_language', home)
  const langs: Py[] = listed(profile)
  const withList = profile.copy()
  withList.set('languages', langs)
  for (const code of otherCodes(dataDir, withList)) {
    if (!langs.some(e => (e as PyDict).get('code') === code)) {
      let lp: Py
      try {
        lp = loadJsonFile(pjoin(languageDir(dataDir, code), FILE.profile))
      } catch (e) {
        if (!isExc(e, 'OSError') && !isExc(e, 'ValueError')) throw e
        lp = new PyDict()
      }
      const lpd = isDict(lp) ? lp : new PyDict()
      const entry = PyDict.of({
        code, name: languageName(code), source: lpd.get('source', 'visits'),
        since: or(lpd.get('profile_created'), fmtDate(ctx.now())),
      })
      const level = isDict(lpd.get('learner')) ? get(or(lpd.get('learner'), new PyDict()), 'current_level') : null
      if (truthy(level)) entry.set('level', level)
      langs.push(entry)
    }
  }
  profile.set('languages', langs)
  saveJson(path, profile)
  return 'migrated'
}

/** migrate_quietly: a failure is a warning on stderr. */
export function migrateQuietly(ctx: Ctx, dataDir: string, backups: string): string {
  let r: string
  try {
    r = migrate(ctx, dataDir, backups)
  } catch (e) {
    ctx.eprint(`[Lani] Warning: language profiles migration failed, data left as it was: ${excStr(e)}`)
    return 'failed'
  }
  if (r === 'migrated') {
    ctx.eprint('[Lani] Migrated the learner data to language profiles (home_language, languages); backup in .backups/pre-migrate-languages-*')
  }
  return r
}
