// .claude/hooks/lani_data_repo.py, as far as update-db.py uses it: the commit message of a session. The commit itself
// (git) is the host's: update-db asks for it through DbHooks.afterWrite (sys.ts), where the Python calls
// lani_data_repo.autocommit; on the phone nothing commits (yet).
import { add, eq, fmtDate, int, isDict, iter, len, or, PyDict, str as pyStr, truthy, values, type Py } from './py'
import type { Ctx } from './sys'

const count = (n: Py, one: string, many: string) => `${pyStr(n)} ${eq(n, 1) ? one : many}`

/** session_message: 'session 2026-10-05: 12 reviews, 2 new words, 1 mistake', and a line of the session's details. */
export function sessionMessage(ctx: Ctx, report: PyDict): string {
  const parts: string[] = []
  const results = or(report.get('review_results'), [])
  const reviews = iter(results).filter(r => !(isDict(r) && r.get('gentle') === true))
  const lowered = len(results) - reviews.length
  const words = or(report.get('new_vocabulary'), [])
  const errors = or(report.get('errors'), [])
  if (reviews.length) parts.push(count(reviews.length, 'review', 'reviews'))
  if (lowered) parts.push(count(lowered, 'card lowered', 'cards lowered'))
  if (truthy(words)) parts.push(count(len(words), 'new word', 'new words'))
  if (truthy(errors)) parts.push(count(len(errors), 'mistake', 'mistakes'))
  if (!parts.length) {
    let exercises: Py = 0
    for (const s of values(or(report.get('skill_scores'), new PyDict()))) {
      if (isDict(s)) exercises = add(exercises, int(or(s.get('exercises', 0), 0)))
    }
    if (truthy(exercises)) parts.push(count(exercises, 'exercise', 'exercises'))
    else if (truthy(report.get('duration_minutes'))) parts.push(`${pyStr(report.get('duration_minutes'))} min`)
  }
  const date = report.has('date') ? report.get('date') : fmtDate(ctx.now())
  const head = `session ${pyStr(date)}` + (parts.length ? `: ${parts.join(', ')}` : '')
  const details = [report.get('session_id'), report.get('command_used'), report.get('language')].filter(truthy).map(pyStr)
  return head + (details.length ? '\n\n' + details.join(' · ') : '')
}
