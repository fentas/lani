// Smoke checks: the streak's rule (src/streak.ts) against the shared cases of test/fixtures/streak-cases.json, which
// the app (StreakParityTest) and the scripts (tests/test_streak.py) read too, and what the bridge makes of it: the
// computed fields' streak, the morning after a covered day, the week's frozen days.
import { readdirSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { computedStreak, EARN_EVERY, frozenDays, keptToday, MAX_FREEZES, START_FREEZES, streakOf, streakOfLog } from '../../src/streak'
import { check } from './harness'

export default async function streak() {
  const fx = JSON.parse(readFileSync(resolve(import.meta.dir, '../fixtures/streak-cases.json'), 'utf8'))
  check('streak: the rule of the shared cases is this one (1 to start, 1 more every 7th day of a run, at most 2)',
    fx.rule.start_freezes === START_FREEZES && fx.rule.earn_every === EARN_EVERY && fx.rule.max_freezes === MAX_FREEZES, fx.rule)
  const wrong = (fx.cases as any[]).flatMap(c => {
    const got = streakOf(c.days, c.today)
    return JSON.stringify(got) === JSON.stringify(c.expect) ? [] : [{ name: c.name, got, expect: c.expect }]
  })
  check(`streak: every shared case answers as the fixture says (${fx.cases.length} cases)`, fx.cases.length >= 20 && wrong.length === 0, wrong)
  check("streak: the shared cases are in place for the app's and the scripts' tests", readdirSync(resolve(import.meta.dir, '../fixtures')).includes('streak-cases.json'))

  // a session log as it is: its sessions' dates, anything else left out
  const log = { sessions: [{ date: '2026-11-07' }, { date: '2026-11-08' }, 'x', null, { date: 3 }, { duration_minutes: 5 }] }
  const s = streakOfLog(log, '2026-11-10')
  check('streak: a session log: its sessions with a date count, the rest is left out', s.current === 2 && s.covered.join() === '2026-11-09' && s.freezes === 0 && !s.today_covered, s)
  check('streak: no session log is no streak', streakOfLog(null, '2026-11-10').current === 0 && streakOfLog({ sessions: 'x' }, '2026-11-10').freezes === 1)

  // the morning after: the covered days of the gap still open, and only then
  const two = streakOf(Array.from({ length: 7 }, (_, k) => `2026-11-0${k + 1}`), '2026-11-10')
  check('streak: the morning after two covered days, both kept it', keptToday(two, '2026-11-10').join() === '2026-11-08,2026-11-09' && two.current === 7 && two.freezes === 0, two)
  const after = streakOf(['2026-11-07', '2026-11-09'], '2026-11-10')
  check('streak: a covered day with practice after it is no longer the morning after', keptToday(after, '2026-11-10').length === 0 && after.covered.join() === '2026-11-08', after)
  check('streak: nothing kept today once practised, or once the run is over', keptToday(streakOf(['2026-11-08', '2026-11-10'], '2026-11-10'), '2026-11-10').length === 0 && keptToday(streakOf(['2026-11-05'], '2026-11-10'), '2026-11-10').length === 0)

  // the computed fields keep this run's covered days; the week's dots every covered day of the week
  const ended = streakOf(['2026-11-02', '2026-11-04', '2026-11-07', '2026-11-08'], '2026-11-09')
  check("streak: the computed fields keep this run's covered days only", ended.covered.join() === '2026-11-03' && computedStreak(ended).covered.length === 0 && ended.since === '2026-11-07', ended)
  check("streak: the week's dots mark each day a freeze covered", JSON.stringify(frozenDays(ended, '2026-11-02')) === '[false,true,false,false,false,false,false]')
}
