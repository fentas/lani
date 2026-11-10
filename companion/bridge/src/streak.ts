// The streak: one number, derived from the practice days, with freezes (companion/GAME.md, "The streak"). The twin of
// the scripts' .claude/hooks/lani_streak.py and the app's data/Streak.kt: test/fixtures/streak-cases.json keeps the
// three equal (the smoke's streak section, tests/test_streak.py, StreakParityTest).
//
// The rule: a run counts its practised days; today is open, never a miss. Each learner starts with 1 freeze, every 7th
// practised day of a run earns 1 more, at most 2 held. A day with no practice before today is covered by a held freeze,
// spent by itself: it keeps the run and adds nothing to its count. A gap longer than the freezes held ends the run and
// spends none. Nothing is stored: the rule holds for every past gap, and the longest streak is the longest run under it.
import { dateOf } from './db/languages'

export const START_FREEZES = 1
export const EARN_EVERY = 7
export const MAX_FREEZES = 2

/**
 * The streak on a day: [current] the practised days of the run going on (0 when none is), [best] the longest run,
 * [freezes] held now, [covered] every day a freeze covered (oldest first), [since] the current run's first practised
 * day, [today_covered] a miss today would be covered, [last_run] the run that ended last, [to_next_freeze] the
 * practised days still to go for the next freeze (0 while the most are held).
 */
export type Streak = {
  current: number
  best: number
  practised_today: boolean
  freezes: number
  covered: string[]
  since: string | null
  today_covered: boolean
  last_run: number
  to_next_freeze: number
}

/** A session's day as the streak counts it: its date (date_of's forms), or the date a timestamp starts with; else null. */
export function dayOf(v: unknown): string | null {
  if (typeof v !== 'string') return null
  return dateOf(v) ?? dateOf(v.slice(0, 10))
}

const pad = (n: number, w = 2) => String(n).padStart(w, '0')
function dayNum(iso: string): number {
  const d = new Date(0)
  d.setUTCFullYear(+iso.slice(0, 4), +iso.slice(5, 7) - 1, +iso.slice(8, 10))
  return Math.round(d.getTime() / 86_400_000)
}
function isoOf(n: number): string {
  const d = new Date(n * 86_400_000)
  return `${pad(d.getUTCFullYear(), 4)}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`
}

/** The streak of the practice days [dates] (the session log's dates, as they are) on [today] (YYYY-MM-DD). */
export function streakOf(dates: Iterable<unknown>, today: string): Streak {
  const end = dayNum(today)
  const days = [...new Set([...dates].map(dayOf).filter((d): d is string => d !== null && d <= today))].sort()
  let freezes = START_FREEZES
  let run = 0
  let best = 0
  let lastRun = 0
  const covered: string[] = []
  let since: string | null = null
  let prev: number | null = null

  // [missed] days without practice after day [start]: covered while freezes last, else the run ends
  const gap = (start: number, missed: number) => {
    if (missed <= 0) return
    if (missed <= freezes) {
      freezes -= missed
      for (let k = 1; k <= missed; k++) covered.push(isoOf(start + k))
    } else {
      lastRun = run
      run = 0
      since = null
    }
  }

  for (const d of days) {
    const day = dayNum(d)
    if (prev !== null) gap(prev, day - prev - 1)
    run++
    if (since === null) since = d
    if (run % EARN_EVERY === 0 && freezes < MAX_FREEZES) freezes++
    best = Math.max(best, run)
    prev = day
  }
  const practisedToday = prev === end
  if (prev !== null && !practisedToday) gap(prev, end - prev - 1) // the gap still open: the days before today
  return {
    current: run,
    best,
    practised_today: practisedToday,
    freezes,
    covered,
    since,
    today_covered: run > 0 && !practisedToday && freezes >= 1,
    last_run: lastRun,
    to_next_freeze: freezes >= MAX_FREEZES ? 0 : EARN_EVERY - (run % EARN_EVERY),
  }
}

/** The dates of a session log's sessions (session-log.json's object, parsed; anything else has none), as they are. */
export function logDates(log: unknown): unknown[] {
  const sessions = log && typeof log === 'object' && !Array.isArray(log) ? (log as Record<string, unknown>).sessions : undefined
  if (!Array.isArray(sessions)) return []
  return sessions.flatMap(s => (s && typeof s === 'object' && !Array.isArray(s) ? [(s as Record<string, unknown>).date ?? null] : []))
}

/** The streak of a session log on [today]. */
export const streakOfLog = (log: unknown, today: string) => streakOf(logDates(log), today)

/** The streak as read-db's computed fields give it: covered is this run's days only (since on). */
export function computedStreak(s: Streak): Streak {
  return { ...s, covered: s.covered.filter(d => s.since !== null && d >= s.since) }
}

/** The covered days of the gap still open before [today] (the morning after: a freeze kept the run), oldest first. */
export function keptToday(s: Streak, today: string): string[] {
  if (s.practised_today || s.current === 0) return []
  const out: string[] = []
  for (let n = dayNum(today) - 1; s.covered.includes(isoOf(n)); n--) out.unshift(isoOf(n))
  return out
}

/** Which of the 7 days from Monday [start] a freeze covered (the week's dots: a covered day as a freeze, not as practice). */
export const frozenDays = (s: Streak, start: string) => Array.from({ length: 7 }, (_, k) => s.covered.includes(isoOf(dayNum(start) + k)))
