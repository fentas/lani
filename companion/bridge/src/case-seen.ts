// The case lens's "seen" signal (docs/plans/05-built/case-lens.md, "Feedback into practice"): a word looked up through
// the lens (GET /lookup with its line: the word card's "Sklon · Case" row) counts as met for the case trainer's per-rule
// mastery, lightly: how often and on how many days each rule (the grammar page's id: rodilnik-predlogi, kam-tozilnik …)
// and its trigger words were seen. Never a pass: nothing here is an answer. <data>/app/case-lens-seen.json, GET
// /cases/seen. The same word of the same line counts once a day.
import { existsSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import type { Log } from './config'

export const CASE_SEEN_SCHEMA = 'lani.case-seen/v0'

/** One rule's sightings: how many, on how many days, the last day, and its trigger words (the 20 seen most). */
export type RuleSeen = { seen: number; days: number; last: string; words: Record<string, number> }
export type CaseSeenFile = { schema: typeof CASE_SEEN_SCHEMA; rules: Record<string, RuleSeen> }

const MAX_WORDS = 20
/** The (line, word) pairs counted today, at most this many kept to tell a second tap of the same word. */
const MAX_TODAY = 2000

export class CaseSeen {
  private file: CaseSeenFile | null = null
  private today = { date: '', keys: new Set<string>() }

  constructor(private readonly path: string, private readonly log?: Log) {}

  /** What was seen, per rule. */
  current(): CaseSeenFile {
    if (this.file) return this.file
    let rules: Record<string, RuleSeen> = {}
    try {
      if (existsSync(this.path)) {
        const raw = JSON.parse(readFileSync(this.path, 'utf8'))
        if (raw && typeof raw.rules === 'object' && raw.rules) rules = raw.rules
      }
    } catch (e) {
      this.log?.(`case lens seen ${this.path} unreadable, started again: ${(e as Error).message}`)
    }
    this.file = { schema: CASE_SEEN_SCHEMA, rules }
    return this.file
  }

  /**
   * [rule] seen through the lens on [line] at the word [key] (its form and place), decided by [trigger] (a word, or none:
   * its role decided): counted once a day per line and word. True when it counted.
   */
  saw(rule: string, line: string, key: string, trigger: string | undefined, now = new Date()): boolean {
    const date = now.toLocaleDateString('sv') // YYYY-MM-DD, local, as the learner databases' days
    if (this.today.date !== date) this.today = { date, keys: new Set() }
    const k = `${rule}|${key}|${line}`
    if (this.today.keys.has(k)) return false
    if (this.today.keys.size >= MAX_TODAY) this.today.keys.clear()
    this.today.keys.add(k)
    const f = this.current()
    const r = f.rules[rule] ?? { seen: 0, days: 0, last: '', words: {} }
    r.seen++
    if (r.last !== date) r.days++
    r.last = date
    if (trigger) {
      const w = trigger.toLocaleLowerCase('sl')
      r.words[w] = (r.words[w] ?? 0) + 1
      const top = Object.entries(r.words).sort((a, b) => b[1] - a[1]).slice(0, MAX_WORDS)
      r.words = Object.fromEntries(top)
    }
    f.rules[rule] = r
    try {
      writeFileSync(`${this.path}.tmp`, JSON.stringify(f, null, 2) + '\n')
      renameSync(`${this.path}.tmp`, this.path)
    } catch (e) {
      this.log?.(`case lens seen not kept: ${(e as Error).message}`)
    }
    return true
  }
}
