// The case lens's "seen" signal (docs/plans/05-built/case-lens.md, "Feedback into practice"): a word looked up through
// the lens (GET /lookup with its line: the word card's "Sklon · Case" row) whose case its line decides is met for that
// rule, lightly, never a pass. The bridge only says when a look counts (`seen: true` on the lookup's case: the same word of
// the same line once a day); the count is the app's, in the village state's case trainer progress (GameState.cases.seen:
// how often each rule was seen and the last day), which the trainer's weighting reads and get_village shows. One count,
// not two: nothing is kept here but today's looks, in memory.

/** The (line, word) pairs counted today, at most this many kept to tell a second tap of the same word. */
const MAX_TODAY = 2000

export class CaseSeen {
  private today = { date: '', keys: new Set<string>() }

  /** [rule] seen through the lens on [line] at the word [key] (its place): true when it counts (once a day per line and word). */
  saw(rule: string, line: string, key: string, now = new Date()): boolean {
    const date = now.toLocaleDateString('sv') // YYYY-MM-DD, local, as the learner databases' days
    if (this.today.date !== date) this.today = { date, keys: new Set() }
    const k = `${rule}|${key}|${line}`
    if (this.today.keys.has(k)) return false
    if (this.today.keys.size >= MAX_TODAY) this.today.keys.clear()
    this.today.keys.add(k)
    return true
  }
}
