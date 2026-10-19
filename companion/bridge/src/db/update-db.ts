// .claude/hooks/update-db.py: a session report (stdin, JSON) into the six databases, after a backup, each file written
// atomically. Exit 0, 1 (validation error, nothing written), 2 (data or I/O error, nothing written).
import { sessionMessage } from './data-repo'
import {
  CEFR, dateOf, FILE, FILES, freshDatabases, homeLanguage, languageCode, languageDir, languageName, listEntry, loadJsonFile,
  migrateQuietly, normDate, saveJson,
} from './languages'
import { ensureBackupsDir, ensureDataDir } from './paths'
import {
  add, append, ceil, contains, deepcopy, eq, excStr, F, fixed, floordiv, fmtDate, ge, get, getItem, gt, int, isDict,
  isList, isStr, items, iter, keysOf, le, len, lower, lt, max, min, mul, num, or, plusDays, PyDict, PyError, repr,
  round, setdefault, setItem, slice, str as pyStr, strip, stripChars, strptime, sub, sumOf, SysExit, traceback,
  truediv, truthy, typeName, unique, upper, values, weekday, type Py,
} from './py'
import { loads } from './pyjson'
import { baseName, copy2, exists, globSuffix, isDir, mkdirp, parent, pjoin, type Ctx } from './sys'

const DATE_FULL = /^\p{Nd}{4}-\p{Nd}{2}-\p{Nd}{2}$/u
/** A session's id names its backup's directory (.backups/pre-update-<session_id>/): one name, never a path. */
const SESSION_ID = /^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$/
const FORMS = 'forms'
const RESURFACE_DAYS = 30
const RESURFACED_KEPT = 10
/** What describes practice: a report that records no session must not carry any of it. */
const SESSION_FIELDS = ['duration_minutes', 'skill_scores', 'review_results', 'errors', 'milestones',
  'skills_practiced', 'topics_covered', 'breakthroughs', 'achievements_earned', 'level']

const D = PyDict.of
const dateStr = fmtDate
const tomorrow = (t: Py) => dateStr(plusDays(strptime(t), 1))
const yesterday = (t: Py) => dateStr(plusDays(strptime(t), -1))
const datePlusDays = (t: Py, days: Py) => dateStr(plusDays(strptime(t), days))
/** due_of: a report's due_date (new_vocabulary, resurface) as YYYY-MM-DD, zero-padded; null when it isn't one. */
const dueOf = (v: Py): string | null => (isStr(v) && DATE_FULL.test(v) ? dateOf(v) : null)
function weekStart(t: Py): string {
  const d = strptime(t)
  return dateStr(plusDays(d, -weekday(d)))
}

class Update {
  readonly DATA_DIR: string
  readonly BACKUP_DIR: string
  readonly HOME_PROFILE: string

  constructor(readonly ctx: Ctx) {
    this.DATA_DIR = ensureDataDir(ctx)
    ctx.dataRoot = this.DATA_DIR
    this.BACKUP_DIR = ensureBackupsDir(ctx)
    this.HOME_PROFILE = pjoin(this.DATA_DIR, FILE.profile)
  }

  fail(code: number, msg: string): never {
    this.ctx.eprint(msg)
    throw new SysExit(code)
  }

  // --- languages ---

  loadHomeProfile(): Py {
    if (!exists(this.HOME_PROFILE)) return new PyDict()
    const p = loadJsonFile(this.HOME_PROFILE)
    return isDict(p) ? p : new PyDict()
  }

  reportLanguage(report: PyDict): string | null {
    const raw = report.get('language')
    if (raw === null || (isStr(raw) && !strip(raw))) return null
    const code = languageCode(raw)
    if (code === null) this.fail(1, `[Lani] Error: 'language' must be a language code (it) or name (Italian), got ${repr(raw)}`)
    let home: string | null
    try {
      home = homeLanguage(this.loadHomeProfile())
    } catch (e) {
      this.fail(2, `[Lani] Error loading ${baseName(this.HOME_PROFILE)}: ${excStr(e)}`)
    }
    return code === home ? null : code
  }

  /** report_session_id: letters, digits, '.', '_' and '-', a letter or digit first, at most 100 (session-NNN); exit 1
   *  otherwise. */
  reportSessionId(report: PyDict): string {
    const sid = report.get('session_id')
    if (!(isStr(sid) && SESSION_ID.test(sid))) {
      this.fail(1, "[Lani] Error: 'session_id' must be letters, digits, '.', '_' and '-', a letter or digit first "
        + `(at most 100; e.g. session-005), got ${repr(sid)}`)
    }
    return sid
  }

  /** report_date: the report's date, zero-padded (the databases compare dates as text); exit 1 when it isn't one. */
  reportDate(report: PyDict): string {
    const d = dateOf(report.get('date'))
    if (d === null) this.fail(1, "[Lani] Error: 'date' must be YYYY-MM-DD")
    return d
  }

  reportLevel(report: PyDict): string | null {
    const raw = report.get('level')
    if (raw === null) return null
    const level = isStr(raw) ? upper(strip(raw)) : null
    if (level === null || !CEFR.includes(level)) {
      this.fail(1, `[Lani] Error: 'level' must be a CEFR level (A1, A2, B1, B2, C1, C2), got ${repr(raw)}`)
    }
    return level
  }

  pathsOf(code: string | null): [string, string][] {
    const d = code === null ? this.DATA_DIR : languageDir(this.DATA_DIR, code)
    return FILES.map(([k, name]) => [k, pjoin(d, name)])
  }

  loadDatabases(code: string | null, date: Py): Map<string, Py> {
    const paths = this.pathsOf(code)
    if (code === null) return new Map(paths.map(([k, p]) => [k, loadJsonFile(p)]))
    const fresh = freshDatabases(code, date, 'A1', get(or(get(this.loadHomeProfile(), 'learner'), new PyDict()), 'name', ''))
    return new Map(paths.map(([k, p]) => [k, exists(p) ? loadJsonFile(p) : fresh[k]]))
  }

  backupAll(tag: string) {
    const backup = pjoin(this.BACKUP_DIR, tag)
    mkdirp(backup)
    for (const f of globSuffix(this.DATA_DIR, '.json')) copy2(f, pjoin(backup, baseName(f)))
  }

  backupLanguage(code: string, tag: string) {
    const backup = pjoin(this.BACKUP_DIR, tag)
    mkdirp(backup)
    if (exists(this.HOME_PROFILE)) copy2(this.HOME_PROFILE, pjoin(backup, baseName(this.HOME_PROFILE)))
    const src = languageDir(this.DATA_DIR, code)
    if (isDir(src)) {
      const dst = pjoin(backup, 'languages', code)
      mkdirp(dst)
      for (const f of globSuffix(src, '.json')) copy2(f, pjoin(dst, baseName(f)))
    }
  }

  listedInHome(code: string, langProfile: Py, date: Py): PyDict | null {
    let home = this.loadHomeProfile()
    if (!truthy(home)) return null
    home = deepcopy(home) as PyDict
    listEntry(home, code, langProfile, date)
    return home
  }

  // --- milestones ---

  normalizeMilestones(session: PyDict): PyDict[] {
    const raw = session.get('milestones', [])
    if (!truthy(raw)) {
      session.set('milestones', [])
      return []
    }
    if (!isList(raw)) this.fail(1, `[Lani] Error: 'milestones' must be a list, got ${typeName(raw)}`)
    const outerDate = getItem(session, 'date')
    const outerSid = getItem(session, 'session_id')
    const normalized: PyDict[] = []
    raw.forEach((ms, i) => {
      let text: string
      let when: Py
      if (isStr(ms)) {
        text = strip(ms)
        if (!text) this.fail(1, `[Lani] Error: milestone at index ${i} is an empty string`)
        when = outerDate
      } else if (isDict(ms)) {
        const t = ms.get('milestone')
        if (!isStr(t) || !strip(t)) this.fail(1, `[Lani] Error: milestone at index ${i} must have a non-empty string 'milestone' field`)
        text = strip(t as string)
        when = outerDate
        const d = ms.get('date')
        if (isStr(d) && strip(d)) when = dateOf(strip(d)) ?? outerDate
      } else {
        this.fail(1, `[Lani] Error: milestone at index ${i} must be a string or object, got ${typeName(ms)}`)
      }
      const slug = stripChars(lower(slice(text, 0, 30)).replace(/[^a-z0-9]+/gu, '_'), '_') || 'milestone'
      normalized.push(D({ date: when, milestone: text, session_id: outerSid, _achievement_id: `session_${pyStr(outerSid)}_${i}_${slug}` }))
    })
    session.set('milestones', normalized)
    return normalized
  }
}

// --- SM-2 ---

/** calculate_sm2: classic SM-2, ceil() for the interval's growth. */
function calculateSm2(item: Py, quality: Py): PyDict {
  let ef = get(item, 'easiness_factor', F(2.5))
  let interval = get(item, 'interval_days', 1)
  let reps = get(item, 'repetitions', 0)
  if (ge(quality, 3)) {
    if (eq(reps, 0)) interval = 1
    else if (eq(reps, 1)) interval = 6
    else interval = int(ceil(mul(interval, ef)))
    reps = add(reps, 1)
  } else {
    reps = 0
    interval = 1
  }
  ef = add(ef, sub(F(0.1), mul(sub(5, quality), add(F(0.08), mul(sub(5, quality), F(0.02))))))
  ef = max(F(1.3), ef)
  return D({ easiness_factor: round(ef, 2), interval_days: interval, repetitions: reps })
}

// --- updaters (each changes its database in place) ---

function updateLearnerProfile(profile: Py, session: PyDict) {
  const today = getItem(session, 'date')
  const last = normDate(get(profile, 'last_updated', ''))
  if (eq(last, today)) {
    // the same day: the streak stays
  } else if (eq(last, yesterday(today))) {
    setItem(profile, 'current_streak_days', add(get(profile, 'current_streak_days', 0), 1))
  } else setItem(profile, 'current_streak_days', 1)

  setItem(profile, 'last_updated', today)
  setItem(profile, 'total_sessions', add(get(profile, 'total_sessions', 0), 1))
  setItem(profile, 'total_study_minutes', add(get(profile, 'total_study_minutes', 0), session.get('duration_minutes', 0)))

  for (const [skill, scores] of items(session.get('skill_scores', new PyDict()))) {
    const skills = setdefault(profile, 'skills', new PyDict())
    const s = setdefault(skills, skill, D({ current_level: 0, confidence: 0, last_practiced: null, total_practice_time: 0 }))
    setItem(s, 'last_practiced', today)
    setItem(s, 'total_practice_time', add(get(s, 'total_practice_time', 0), get(scores, 'time_minutes', 0)))
    if (gt(get(scores, 'exercises', 0), 0)) {
      const newAccPct = mul(truediv(getItem(scores, 'correct'), getItem(scores, 'exercises')), 100)
      const oldConf = get(s, 'confidence', 0)
      setItem(s, 'confidence', round(add(mul(oldConf, F(0.7)), mul(newAccPct, F(0.3)))))
    }
    setItem(s, 'current_level', max(get(s, 'current_level', 0), 1))
  }

  if (truthy(session.get('focus_areas'))) setItem(profile, 'focus_areas', getItem(session, 'focus_areas'))

  for (const m of iter(session.get('milestones', []))) {
    append(setdefault(profile, 'achievements', []), D({
      id: getItem(m, '_achievement_id'),
      name: getItem(m, 'milestone'),
      earned_date: getItem(m, 'date'),
      description: getItem(m, 'milestone'),
    }))
  }
}

function updateProgressDb(progress: Py, session: PyDict) {
  const today = getItem(session, 'date')
  const skillScores = session.get('skill_scores', new PyDict())
  const totalEx = sumOf(values(skillScores), s => get(s, 'exercises', 0))
  const totalCor = sumOf(values(skillScores), s => get(s, 'correct', 0))
  const accuracy = gt(totalEx, 0) ? round(truediv(totalCor, totalEx), 3) : F(0)

  const stats = setdefault(progress, 'overall_stats', D({
    total_sessions: 0, total_exercises: 0, total_correct: 0, total_incorrect: 0, accuracy_rate: F(0),
    total_study_minutes: 0, average_session_duration: 0,
  }))
  setItem(stats, 'total_sessions', add(get(stats, 'total_sessions', 0), 1))
  setItem(stats, 'total_exercises', add(get(stats, 'total_exercises', 0), totalEx))
  setItem(stats, 'total_correct', add(get(stats, 'total_correct', 0), totalCor))
  setItem(stats, 'total_incorrect', add(get(stats, 'total_incorrect', 0), sub(totalEx, totalCor)))
  setItem(stats, 'accuracy_rate', gt(getItem(stats, 'total_exercises'), 0)
    ? round(truediv(getItem(stats, 'total_correct'), getItem(stats, 'total_exercises')), 3) : F(0))
  setItem(stats, 'total_study_minutes', add(get(stats, 'total_study_minutes', 0), session.get('duration_minutes', 0)))
  setItem(stats, 'average_session_duration', round(truediv(getItem(stats, 'total_study_minutes'), getItem(stats, 'total_sessions'))))

  const trend = setdefault(progress, 'accuracy_trend', [])
  const existing = iter(trend).find(t => eq(normDate(get(t, 'date')), today))
  if (existing !== undefined) {
    setItem(existing, 'accuracy', accuracy)
    setItem(existing, 'exercises', add(get(existing, 'exercises', 0), totalEx))
  } else append(trend, D({ date: today, accuracy, exercises: totalEx }))

  for (const [skill, scores] of items(skillScores)) {
    const sp = setdefault(setdefault(progress, 'skill_progress', new PyDict()), skill, D({
      sessions: 0, accuracy: F(0), last_practiced: null, exercises_completed: 0, correct_count: 0, incorrect_count: 0,
    }))
    const oldSessions = get(sp, 'sessions', 0)
    setItem(sp, 'sessions', add(oldSessions, 1))
    const newAcc = gt(get(scores, 'exercises', 0), 0) ? truediv(getItem(scores, 'correct'), getItem(scores, 'exercises')) : F(0)
    setItem(sp, 'accuracy', round(truediv(add(mul(get(sp, 'accuracy', F(0)), oldSessions), newAcc), getItem(sp, 'sessions')), 3))
    setItem(sp, 'last_practiced', today)
    setItem(sp, 'exercises_completed', add(get(sp, 'exercises_completed', 0), get(scores, 'exercises', 0)))
    setItem(sp, 'correct_count', add(get(sp, 'correct_count', 0), get(scores, 'correct', 0)))
    setItem(sp, 'incorrect_count', add(get(sp, 'incorrect_count', 0), sub(get(scores, 'exercises', 0), get(scores, 'correct', 0))))
  }

  const start = weekStart(today)
  const weekly = setdefault(progress, 'weekly_summary', [])
  let week = iter(weekly).find(w => eq(normDate(get(w, 'week_start')), start))
  if (week === undefined) {
    week = D({ week_start: start, sessions: 0, total_minutes: 0, accuracy: F(0) })
    append(weekly, week)
  }
  const oldS = get(week, 'sessions', 0)
  setItem(week, 'sessions', add(oldS, 1))
  setItem(week, 'total_minutes', add(get(week, 'total_minutes', 0), session.get('duration_minutes', 0)))
  setItem(week, 'accuracy', round(truediv(add(mul(get(week, 'accuracy', F(0)), oldS), accuracy), getItem(week, 'sessions')), 3))

  setItem(setdefault(progress, 'metadata', new PyDict()), 'last_updated', today)
}

function example(error: Py, today: Py): PyDict {
  return D({
    incorrect: get(error, 'your_answer', ''),
    correct: get(error, 'correct_answer', ''),
    context: get(error, 'context', ''),
    date: today,
  })
}

function updateMistakesDb(mistakes: Py, session: PyDict) {
  const today = getItem(session, 'date')
  const patterns = setdefault(mistakes, 'error_patterns', new PyDict())
  for (const error of iter(session.get('errors', []))) {
    const pid = getItem(error, 'pattern_id')
    if (contains(patterns, pid)) {
      const pat = getItem(patterns, pid)
      setItem(pat, 'frequency', add(get(pat, 'frequency', 0), 1))
      setItem(pat, 'last_seen', today)
      setItem(pat, 'last_occurred', today)
      setItem(pat, 'next_review', tomorrow(today))
      setItem(pat, 'consecutive_incorrect', add(get(pat, 'consecutive_incorrect', 0), 1))
      setItem(pat, 'consecutive_correct', 0)
      append(setdefault(pat, 'examples', []), example(error, today))
      setItem(pat, 'examples', slice(getItem(pat, 'examples') as Py[], -5))
      if (truthy(get(error, 'notes'))) setItem(pat, 'notes', getItem(error, 'notes'))
    } else {
      setItem(patterns, pid, D({
        category: get(error, 'category', 'other'),
        subcategory: get(error, 'subcategory', ''),
        description: get(error, 'description', ''),
        severity: get(error, 'severity', 'minor'),
        frequency: 1,
        mastery_level: 0,
        difficulty_score: get(error, 'difficulty_score', F(0.5)),
        last_seen: today,
        last_occurred: today,
        next_review: tomorrow(today),
        consecutive_correct: 0,
        consecutive_incorrect: 1,
        examples: [example(error, today)],
        notes: get(error, 'notes', ''),
      }))
    }
  }
  setItem(setdefault(mistakes, 'metadata', new PyDict()), 'last_updated', today)
  setItem(getItem(mistakes, 'metadata'), 'total_patterns_tracked', len(patterns))
}

function updateMasteryDb(mastery: Py, session: PyDict, progress: Py) {
  const today = getItem(session, 'date')
  for (const [skill, scores] of items(session.get('skill_scores', new PyDict()))) {
    const s = setdefault(setdefault(mastery, 'skills', new PyDict()), skill, D({
      mastery_level: 0, confidence_score: F(0), total_practice_time: 0, last_practiced: null, practice_count: 0,
      avg_accuracy: F(0),
    }))
    setItem(s, 'last_practiced', today)
    setItem(s, 'total_practice_time', add(get(s, 'total_practice_time', 0), get(scores, 'time_minutes', 0)))
    setItem(s, 'practice_count', add(get(s, 'practice_count', 0), get(scores, 'exercises', 0)))
    const sp = get(get(progress, 'skill_progress', new PyDict()), skill, new PyDict())
    const acc = get(sp, 'accuracy', 0)
    const sessions = get(sp, 'sessions', 0)
    setItem(s, 'confidence_score', round(acc, 3))
    setItem(s, 'avg_accuracy', round(acc, 3))
    if (eq(sessions, 0)) setItem(s, 'mastery_level', 0)
    else if (lt(sessions, 3) || lt(acc, F(0.5))) setItem(s, 'mastery_level', max(get(s, 'mastery_level', 0), 1))
    else if (lt(sessions, 5) || lt(acc, F(0.65))) setItem(s, 'mastery_level', max(get(s, 'mastery_level', 0), 2))
    else if (lt(sessions, 10) || lt(acc, F(0.8))) setItem(s, 'mastery_level', max(get(s, 'mastery_level', 0), 3))
    else if (lt(sessions, 20) || lt(acc, F(0.9))) setItem(s, 'mastery_level', max(get(s, 'mastery_level', 0), 4))
    else setItem(s, 'mastery_level', 5)
  }
  setItem(setdefault(mastery, 'metadata', new PyDict()), 'last_updated', today)
}

/** lower_gently: a word got wrong in a dialog: sooner and a little harder, no review. */
function lowerGently(item: Py, today: Py) {
  if (eq(normDate(get(item, 'last_lapse')), today)) return
  setItem(item, 'easiness_factor', round(max(F(1.3), sub(get(item, 'easiness_factor', F(2.5)), F(0.14))), 2))
  setItem(item, 'interval_days', max(1, floordiv(int(or(get(item, 'interval_days', 1), 1)), 2)))
  const due = get(item, 'due_date')
  const tom = tomorrow(today)
  setItem(item, 'due_date', isStr(due) && due && lt(normDate(due), tom) ? due : tom)
  setItem(item, 'consecutive_correct', 0)
  setItem(item, 'lapses', add(get(item, 'lapses', 0), 1))
  setItem(item, 'last_lapse', today)
}

/** resurface_entries: the entries that read (an item_id; a due date from [today] to 30 days after it). */
function resurfaceEntries(entries: Py, today: Py): PyDict[] {
  if (!isList(entries)) return []
  const last = datePlusDays(today, RESURFACE_DAYS)
  const out: PyDict[] = []
  for (const e of entries) {
    if (!isDict(e) || !isStr(e.get('item_id')) || !strip(getItem(e, 'item_id'))) continue
    const due = dueOf(e.get('due_date'))
    if (!(due && le(today, due) && le(due, last))) continue
    out.push(e)
  }
  return out
}

/** resurface_items: forms cards that come back soon; the ids changed. */
function resurfaceItems(its: Py, entries: Py, today: Py): string[] {
  const changed: string[] = []
  for (const e of resurfaceEntries(entries, today)) {
    const item = get(its, getItem(e, 'item_id') as string)
    if (!isDict(item) || item.get('type') !== FORMS) continue
    const due = item.get('due_date')
    const when = dueOf(getItem(e, 'due_date')) as string
    if (!(isStr(due) && due) || lt(when, normDate(due))) item.set('due_date', when)
    item.set('interval_days', 1)
    item.set('repetitions', 0)
    if (isDict(e.get('forms'))) item.set('forms', deepcopy(getItem(e, 'forms')))
    const notes = isList(item.get('resurfaced')) ? (item.get('resurfaced') as Py[]) : []
    item.set('resurfaced', slice([...notes, D({ date: today, due: item.get('due_date') })], -RESURFACED_KEPT))
    changed.push(getItem(e, 'item_id') as string)
  }
  return changed
}

function newItem(itemId: Py, o: Record<string, Py>): PyDict {
  return D({ id: itemId, ...o })
}

function updateSpacedRepetition(sr: Py, session: PyDict) {
  const today = getItem(session, 'date')
  const its = setdefault(sr, 'items', new PyDict())

  for (const review of iter(session.get('review_results', []))) {
    const itemId = getItem(review, 'item_id')
    if (get(review, 'gentle') === true) {
      if (contains(its, itemId)) lowerGently(getItem(its, itemId), today)
      continue
    }
    const quality = getItem(review, 'quality')
    if (contains(its, itemId)) {
      const item = getItem(its, itemId)
      const result = calculateSm2(item, quality)
      setItem(item, 'easiness_factor', result.get('easiness_factor'))
      setItem(item, 'interval_days', result.get('interval_days'))
      setItem(item, 'repetitions', result.get('repetitions'))
      setItem(item, 'due_date', datePlusDays(today, result.get('interval_days')))
      setItem(item, 'last_reviewed', today)
      setItem(item, 'last_quality', quality)
      setItem(item, 'total_reviews', add(get(item, 'total_reviews', 0), 1))
      if (ge(quality, 3)) {
        setItem(item, 'consecutive_correct', add(get(item, 'consecutive_correct', 0), 1))
        setItem(item, 'consecutive_incorrect', 0)
      } else {
        setItem(item, 'consecutive_incorrect', add(get(item, 'consecutive_incorrect', 0), 1))
        setItem(item, 'consecutive_correct', 0)
      }
      const current = get(item, 'mastery_level', 0)
      if (ge(getItem(item, 'repetitions'), 5) && ge(getItem(item, 'consecutive_correct'), 3)) {
        setItem(item, 'mastery_level', min(5, max(current, 3)))
      } else if (ge(getItem(item, 'repetitions'), 2) && ge(getItem(item, 'consecutive_correct'), 1) && ge(quality, 4)) {
        setItem(item, 'mastery_level', min(5, add(current, 1)))
      }
      if (ge(get(item, 'consecutive_incorrect', 0), 2)) setItem(item, 'priority', 'high')
      else if (ge(get(item, 'mastery_level', 0), 3)) setItem(item, 'priority', 'low')
      else setItem(item, 'priority', get(item, 'priority', 'medium'))
      append(setdefault(item, 'review_history', []), D({ date: today, quality, score: get(review, 'score', 0) }))
    }
  }

  for (const vocab of iter(session.get('new_vocabulary', []))) {
    const itemId = getItem(vocab, 'item_id')
    if (!contains(its, itemId)) {
      setItem(its, itemId, newItem(itemId, {
        type: get(vocab, 'item_type', 'vocabulary'),
        content: get(vocab, 'content', ''),
        answer: get(vocab, 'answer', ''),
        category: get(vocab, 'category', ''),
        difficulty: get(vocab, 'difficulty', ''),
        created_date: today,
        due_date: tomorrow(today),
        interval_days: 1,
        repetitions: 0,
        easiness_factor: F(2.5),
        consecutive_correct: 0,
        consecutive_incorrect: 0,
        last_reviewed: today,
        last_quality: get(vocab, 'initial_quality', 3),
        mastery_level: 0,
        total_reviews: 0,
        priority: get(vocab, 'priority', 'medium'),
      }))
      const item = getItem(its, itemId)
      for (const k of ['example', 'example_translation', 'source']) {
        const v = get(vocab, k)
        if (isStr(v) && strip(v)) setItem(item, k, strip(v))
      }
      if (isDict(get(vocab, 'counting'))) setItem(item, 'counting', deepcopy(getItem(vocab, 'counting')))
      if (isDict(get(vocab, 'forms'))) setItem(item, 'forms', deepcopy(getItem(vocab, 'forms')))
      const due = dueOf(get(vocab, 'due_date'))
      if (due && le(today, due) && lt(due, getItem(item, 'due_date'))) setItem(item, 'due_date', due)
    }
  }

  resurfaceItems(its, session.get('resurface', []), today)

  for (const error of iter(session.get('errors', []))) {
    const itemId = getItem(error, 'pattern_id')
    if (!contains(its, itemId)) {
      setItem(its, itemId, newItem(itemId, {
        type: 'error_pattern',
        content: get(error, 'your_answer', ''),
        answer: get(error, 'correct_answer', ''),
        category: get(error, 'category', ''),
        difficulty: '',
        created_date: today,
        due_date: tomorrow(today),
        interval_days: 1,
        repetitions: 0,
        easiness_factor: F(2.5),
        consecutive_correct: 0,
        consecutive_incorrect: 1,
        last_reviewed: today,
        last_quality: 2,
        mastery_level: 0,
        total_reviews: 0,
        priority: 'high',
      }))
    }
  }

  const queue = D({ today: [], tomorrow: [], this_week: [], later: [] })
  setItem(sr, 'review_queue', queue)
  const tom = tomorrow(today)
  const weekEnd = datePlusDays(today, 7)
  for (const [itemId, item] of items(its)) {
    const due = normDate(get(item, 'due_date', today))
    if (le(due, today)) (queue.get('today') as Py[]).push(itemId)
    else if (eq(due, tom)) (queue.get('tomorrow') as Py[]).push(itemId)
    else if (le(due, weekEnd)) (queue.get('this_week') as Py[]).push(itemId)
    else (queue.get('later') as Py[]).push(itemId)
  }
  setItem(setdefault(sr, 'metadata', new PyDict()), 'last_updated', today)
  setItem(getItem(sr, 'metadata'), 'total_items_tracked', len(its))
}

function updateSessionLog(log: Py, session: PyDict, streak: Py) {
  const today = getItem(session, 'date')
  const skillScores = session.get('skill_scores', new PyDict())
  const totalEx = sumOf(values(skillScores), s => get(s, 'exercises', 0))
  const totalCor = sumOf(values(skillScores), s => get(s, 'correct', 0))
  const breakdown = new PyDict()
  for (const [skill, s] of items(skillScores)) {
    breakdown.set(skill, gt(get(s, 'exercises', 0), 0) ? round(truediv(getItem(s, 'correct'), getItem(s, 'exercises')), 3) : F(0))
  }
  const entry = D({
    session_id: getItem(session, 'session_id'),
    date: today,
    duration_minutes: session.get('duration_minutes', 0),
    skills_practiced: session.get('skills_practiced', keysOf(skillScores)),
    command_used: session.get('command_used', '/lani-learn'),
    exercises_completed: totalEx,
    accuracy: gt(totalEx, 0) ? round(truediv(totalCor, totalEx), 3) : F(0),
    score_breakdown: breakdown,
    topics_covered: session.get('topics_covered', []),
    breakthroughs: session.get('breakthroughs', []),
    focus_next_session: session.get('focus_next_session', session.get('focus_areas', [])),
    notes: session.get('session_notes', ''),
    achievements_earned: session.get('achievements_earned', []),
    streak_day: streak,
  })
  if (truthy(session.get('exam_focus'))) entry.set('exam_focus', getItem(session, 'exam_focus'))
  if (truthy(session.get('critical_errors_identified'))) entry.set('critical_errors_identified', getItem(session, 'critical_errors_identified'))

  append(setdefault(log, 'sessions', []), entry)
  for (const m of iter(session.get('milestones', []))) {
    append(setdefault(log, 'milestones', []), D({ date: getItem(m, 'date'), milestone: getItem(m, 'milestone'), session_id: getItem(m, 'session_id') }))
  }
  setItem(setdefault(log, 'metadata', new PyDict()), 'total_sessions', len(getItem(log, 'sessions')))
}

// --- review items without a session ---

function addItemsOnly(u: Update, report: PyDict): never {
  const ctx = u.ctx
  report.set('date', u.reportDate(report))
  const practice = SESSION_FIELDS.filter(k => truthy(report.get(k)))
  if (practice.length) u.fail(1, `[Lani] Error: a report with record_session false adds review items only; remove ${practice.join(', ')}`)
  let vocab = report.get('new_vocabulary')
  const again = report.get('resurface')
  if (again !== null && !isList(again)) u.fail(1, "[Lani] Error: 'resurface' must be a list of {item_id, due_date, forms}")
  if (vocab === null && truthy(again)) vocab = []
  if (!isList(vocab) || (!truthy(vocab) && !truthy(again))
    || !vocab.every(v => isDict(v) && isStr(v.get('item_id')) && truthy(strip(v.get('item_id'))))) {
    u.fail(1, "[Lani] Error: 'new_vocabulary' must be a non-empty list of items with an item_id (or 'resurface' non-empty)")
  }
  const words = vocab as PyDict[]
  const code = u.reportLanguage(report)

  const paths = new Map(u.pathsOf(code))
  const path = paths.get('sr') as string
  const newLanguage = code !== null && !exists(paths.get('profile') as string)
  const date = getItem(report, 'date')
  let databases: Map<string, Py>
  try {
    databases = newLanguage ? u.loadDatabases(code, date) : new Map([['sr', loadJsonFile(path)]])
  } catch (e) {
    u.fail(2, `[Lani] Error loading ${baseName(path)}: ${excStr(e)}`)
  }
  const original = databases.get('sr') as Py
  const sr = deepcopy(original)
  let home: PyDict | null = null
  try {
    updateSpacedRepetition(sr, D({ date, new_vocabulary: words, resurface: or(again, []) }))
    home = newLanguage ? u.listedInHome(code as string, databases.get('profile') as Py, date) : null
  } catch (e) {
    ctx.eprint(`[Lani] Error updating ${baseName(path)}: ${excStr(e)}`)
    ctx.err.push(traceback(e))
    throw new SysExit(2)
  }
  const added = unique(words.map(v => v.get('item_id') as string)).filter(i => !contains(get(original, 'items', new PyDict()), i))
  const before = get(original, 'items', new PyDict())
  const back = unique(resurfaceEntries(or(again, []), date).map(e => e.get('item_id') as string))
    .filter(i => isDict(get(before, i)) && get(get(before, i), 'type') === FORMS)
  const leftOut = len(or(again, [])) - back.length
  if (added.length || back.length) {
    if (code === null) {
      const backup = pjoin(u.BACKUP_DIR, `pre-words-${pyStr(date)}`)
      mkdirp(backup)
      copy2(path, pjoin(backup, baseName(path)))
    } else u.backupLanguage(code, `pre-words-${code}-${pyStr(date)}`)
    try {
      if (newLanguage) {
        mkdirp(parent(path))
        for (const [k, p] of paths) saveJson(p, k === 'sr' ? sr : (databases.get(k) as Py))
        if (home !== null) saveJson(u.HOME_PROFILE, home)
      } else saveJson(path, sr)
    } catch (e) {
      u.fail(2, `[Lani] Error saving ${baseName(path)}: ${excStr(e)}`)
    }
  }
  const where = code ? ` in ${languageName(code)}` : ''
  ctx.print(`[Lani] ✅ Added ${added.length} review item(s)${where} (${added.join(', ') || 'all known already'}); no session recorded`)
  if (truthy(again)) {
    ctx.print(`[Lani] 🔁 Forms cards back soon: ${back.length} (${back.join(', ') || 'none'})`
      + (leftOut ? `; ${leftOut} entr(ies) left out (no forms card, or a date out of range)` : ''))
  }
  ctx.print(`[Lani] 🧠 SR: ${pyStr(getItem(getItem(sr, 'metadata'), 'total_items_tracked'))} items tracked`)
  throw new SysExit(0)
}

// --- main ---

export function main(ctx: Ctx, _argv: string[], stdin: () => string): void {
  const u: Update = new Update(ctx)
  let session: Py
  try {
    session = loads(stdin())
  } catch (e) {
    if (!(e instanceof PyError) || e.type !== 'JSONDecodeError') throw e
    u.fail(1, `[Lani] Error: Invalid JSON input: ${e.msg}`)
  }
  if (!isDict(session)) u.fail(1, '[Lani] Error: the report must be a JSON object')

  migrateQuietly(ctx, u.DATA_DIR, u.BACKUP_DIR)

  if (session.get('record_session') === false) addItemsOnly(u, session)

  for (const field of ['session_id', 'date']) {
    if (!session.has(field)) u.fail(1, `[Lani] Error: Missing required field '${field}'`)
  }
  u.reportSessionId(session)
  // the date as the databases keep it: "2026-4-24" is written "2026-04-24" (they compare dates as text)
  session.set('date', u.reportDate(session))

  u.normalizeMilestones(session)
  const level = u.reportLevel(session)
  const code = u.reportLanguage(session)

  session.setdefault('duration_minutes', 0)
  const files = new Map(u.pathsOf(code))

  let originals: Map<string, Py>
  try {
    originals = u.loadDatabases(code, getItem(session, 'date'))
  } catch (e) {
    u.fail(2, `[Lani] Error loading databases: ${excStr(e)}`)
  }
  const data = new Map([...originals].map(([k, v]) => [k, deepcopy(v)]))
  const db = (k: string) => data.get(k) as Py

  let home: PyDict | null = null
  let streak: Py
  try {
    if (level) {
      const learner = setdefault(db('profile'), 'learner', new PyDict())
      if (!eq(get(learner, 'current_level'), level)) setItem(learner, 'level_since', getItem(session, 'date'))
      setItem(learner, 'current_level', level)
    }
    updateLearnerProfile(db('profile'), session)
    updateProgressDb(db('progress'), session)
    updateMistakesDb(db('mistakes'), session)
    updateMasteryDb(db('mastery'), session, db('progress'))
    updateSpacedRepetition(db('sr'), session)
    streak = get(db('profile'), 'current_streak_days', 0)
    updateSessionLog(db('log'), session, streak)
    home = code ? u.listedInHome(code, db('profile'), getItem(session, 'date')) : null
  } catch (e) {
    ctx.eprint(`[Lani] Error updating databases: ${excStr(e)}`)
    ctx.err.push(traceback(e))
    throw new SysExit(2)
  }

  if (code === null) u.backupAll(`pre-update-${pyStr(getItem(session, 'session_id'))}`)
  else u.backupLanguage(code, `pre-update-${code}-${pyStr(getItem(session, 'session_id'))}`)

  try {
    mkdirp(parent(files.get('profile') as string))
    for (const [k, p] of files) saveJson(p, db(k))
    if (home !== null) saveJson(u.HOME_PROFILE, home)
  } catch (e) {
    u.fail(2, `[Lani] Error saving databases: ${excStr(e)}`)
  }

  const stats = getItem(db('progress'), 'overall_stats')
  const srTomorrow = len(get(getItem(db('sr'), 'review_queue'), 'tomorrow', []))
  const skillScores = session.get('skill_scores', new PyDict())
  const totalEx = sumOf(values(skillScores), s => get(s, 'exercises', 0))
  const totalCor = sumOf(values(skillScores), s => get(s, 'correct', 0))

  const where = code ? ` (${languageName(code)}: ${parent(files.get('profile') as string)})` : ''
  ctx.print(`[Lani] ✅ Updated 6 databases for session ${pyStr(getItem(session, 'session_id'))}${where}`)
  ctx.print(`[Lani] 🔥 Streak: ${pyStr(streak!)} days | Sessions: ${pyStr(getItem(stats, 'total_sessions'))} | Minutes: ${pyStr(getItem(stats, 'total_study_minutes'))}`)
  if (gt(totalEx, 0)) {
    ctx.print(`[Lani] 📊 This session: ${pyStr(totalCor)}/${pyStr(totalEx)} correct (${pyStr(round(mul(truediv(totalCor, totalEx), 100)))}%)`)
  } else ctx.print('[Lani] 📊 No exercises recorded')
  ctx.print(`[Lani] 📈 Overall accuracy: ${fixed(num(mul(getItem(stats, 'accuracy_rate'), 100)), 0)}% (${pyStr(getItem(stats, 'total_exercises'))} exercises)`)
  ctx.print(`[Lani] 🧠 SR: ${pyStr(getItem(getItem(db('sr'), 'metadata'), 'total_items_tracked'))} items tracked, ${srTomorrow} due tomorrow`)
  ctx.print(`[Lani] 📝 Errors tracked: ${pyStr(getItem(getItem(db('mistakes'), 'metadata'), 'total_patterns_tracked'))} patterns`)

  // lani_data_repo.autocommit(DATA_DIR, session_message(session)): the host's (DbHooks.afterWrite).
  ctx.commit = { dataDir: u.DATA_DIR, message: sessionMessage(ctx, session) }
  throw new SysExit(0)
}
