// .claude/hooks/lani_streak.py's database side: a session log's dates and the computed fields' streak, as Python's
// values (the rule itself is ../streak.ts, the app's and the bridge's too).
import { isDict, PyDict, type Py } from './py'
import { computedStreak, type Streak } from '../streak'

/** lani_streak.log_dates: the dates of a session log's sessions (a dict's; anything else has none), as they are. */
export function logDates(log: Py): Py[] {
  const sessions = isDict(log) ? log.get('sessions') : null
  return Array.isArray(sessions) ? sessions.filter(isDict).map(s => s.get('date') ?? null) : []
}

/** lani_streak.computed: the streak as the computed fields give it (this run's covered days only), as Python's dict. */
export function streakDict(s: Streak): PyDict {
  const c = computedStreak(s)
  return PyDict.of({
    current: c.current, best: c.best, practised_today: c.practised_today, freezes: c.freezes, covered: c.covered,
    since: c.since, today_covered: c.today_covered, last_run: c.last_run, to_next_freeze: c.to_next_freeze,
  })
}
