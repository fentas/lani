// The tutor's daily rhythm: morning plan, evening recap, weekly review.
// The bridge wakes the always-on session at these times with a compact learner snapshot.
import { existsSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import { z } from 'zod'

export type Routine = 'morning' | 'evening' | 'weekly'

const hhmm = z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, 'time must be HH:MM')

export const rhythmSettings = z.object({
  enabled: z.boolean().default(true),
  morning: hhmm.nullable().default('07:30'), // null = off
  evening: hhmm.nullable().default('20:30'),
  /** 0 = Sunday … 6 = Saturday; null = off. */
  weekly_day: z.number().int().min(0).max(6).nullable().default(0),
  weekly: hhmm.default('19:00'),
})
export type RhythmSettings = z.infer<typeof rhythmSettings>

/** Settings plus when each routine last ran (local date "YYYY-MM-DD"). */
export type RhythmState = { settings: RhythmSettings; last: Partial<Record<Routine, string>> }

/** A routine that starts late (node restarted) still runs within this many hours; later it's skipped. */
const CATCH_UP_HOURS: Record<Routine, number> = { morning: 4, evening: 3, weekly: 24 }

const pad = (n: number) => String(n).padStart(2, '0')
export const localDate = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`

function at(day: Date, time: string): Date {
  const [h, m] = time.split(':').map(Number)
  const d = new Date(day)
  d.setHours(h, m, 0, 0)
  return d
}

/**
 * Routines to run now. Pure: given the clock and state, returns what is due.
 * Each routine runs at most once per day; a missed one catches up only within its window.
 */
export function dueRoutines(now: Date, state: RhythmState): Routine[] {
  const s = state.settings
  if (!s.enabled) return []
  const today = localDate(now)
  const times: [Routine, string | null][] = [
    ['morning', s.morning],
    ['evening', s.evening],
    ['weekly', s.weekly_day === now.getDay() ? s.weekly : null],
  ]
  return times.flatMap(([r, time]) => {
    if (!time || state.last[r] === today) return []
    const start = at(now, time)
    const late = (now.getTime() - start.getTime()) / 3_600_000
    return late >= 0 && late <= CATCH_UP_HOURS[r] ? [r] : []
  })
}

export class RhythmStore {
  constructor(private path: string) {}

  /** The saved state; the defaults when the file is missing or unreadable. */
  read(): RhythmState {
    let raw: any = {}
    try {
      raw = existsSync(this.path) ? JSON.parse(readFileSync(this.path, 'utf8')) : {}
    } catch {}
    const settings = rhythmSettings.safeParse(raw?.settings ?? {})
    return { settings: settings.success ? settings.data : rhythmSettings.parse({}), last: raw?.last ?? {} }
  }

  write(state: RhythmState) {
    writeFileSync(`${this.path}.tmp`, JSON.stringify(state, null, 2))
    renameSync(`${this.path}.tmp`, this.path)
  }

  markRan(r: Routine, now: Date) {
    const s = this.read()
    this.write({ ...s, last: { ...s.last, [r]: localDate(now) } })
  }

  update(patch: Partial<RhythmSettings>): RhythmSettings {
    const s = this.read()
    const settings = rhythmSettings.parse({ ...s.settings, ...patch })
    this.write({ ...s, settings })
    return settings
  }
}

/** What each routine asks of the tutor (appended to the channel event). */
export const routineAsk: Record<Routine, string> = {
  morning:
    'Morning. Plan Jan\'s day: pick the one thing that matters most today (weakest pattern, due reviews, or what the village lacks). If useful, publish one fresh module or quest for it (lani-studio). Then reply with a short plan: 2–3 bullets, one of them a 5-minute option. Greet in Slovene. Put {"plan": true} in data.',
  evening:
    'Evening. If Jan practised today: a warm 2–3 line recap and one tip for tomorrow. If not: a gentle, guilt-free nudge with one 5-minute thing to keep the streak. Reply, short.',
  weekly:
    'Weekly review. Look at the week (sessions, accuracy, recurring mistakes, words learned, the village). Retire modules the learner has mastered (tag "retired"), write one or two new ones for what keeps going wrong, and suggest the next word pack. Reply with a short weekly summary (what improved, what to focus on). Put {"weekly": true} in data.',
}
