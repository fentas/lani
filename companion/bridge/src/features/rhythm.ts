// The daily rhythm: the bridge wakes the session in the morning, the evening and once a week with a
// compact snapshot of the learner's day. The scheduling rules live in ../rhythm.ts.
import { join } from 'node:path'
import { fail, ok } from '../channel'
import type { FeatureFactory } from '../feature'
import { ofLanguage, ripeRules } from '../grammar'
import { dueRoutines, RhythmStore, routineAsk, type Routine } from '../rhythm'
import { learnerLevel } from '../villagers'

const instructions = `
daily_morning / daily_evening / daily_weekly: the bridge wakes you on a schedule with a snapshot of
Jan's progress. Do what the event asks (plan the day, recap, weekly review) and reply to conversation
"main"; the reply reaches Jan's phone as a notification. Keep these short and warm, never guilt-tripping.
Jan can change the times ("wake me at 8", "no evening messages"): use set_rhythm.
A snapshot's grammar_ripe lists rules the dialogs said often enough without asking for them: a good day to introduce
one (see Grammar book, "Rules not yet").
`.trim()

export const rhythm: FeatureFactory = ({ cfg, channel, game, learner, log, grammar: book }) => {
  const store = new RhythmStore(join(cfg.appDir, 'rhythm.json'))

  /**
   * The rules ripe to introduce (companion/GAME.md, "Rules not yet"): the dialogs said them often enough without asking
   * for them. An app from before rules not yet records no meetings: none.
   */
  function ripe(state: any) {
    try {
      const rules = ripeRules(book.all(), ofLanguage(state?.meetings, book.language), ofLanguage(state?.grammar, book.language), learnerLevel(cfg.dataDir))
      return rules.length ? rules.map(({ id, title, level, met }) => ({ id, title, level, met })) : undefined
    } catch (e) {
      log(`rhythm: no ripe rules (${(e as Error).message})`)
      return undefined
    }
  }

  /** A compact picture of today for the tutor: enough to plan without reading every database. */
  function snapshot(): string {
    const st = learner.state()
    const db = st.databases
    const today = st.computed.today
    const sessions = (db.session_log?.sessions ?? []).filter((x: any) => x.date === today)
    const items = Object.values(db.spaced_repetition?.items ?? {}) as any[]
    const weak = Object.entries(db.mistakes_db?.error_patterns ?? {})
      .filter(([, p]: any) => (p.consecutive_incorrect ?? 0) > 0)
      .sort(([, a]: any, [, b]: any) => (b.frequency ?? 0) - (a.frequency ?? 0))
      .slice(0, 3)
      .map(([id]) => id)
    const g = game.read()?.state as any
    return JSON.stringify(
      {
        today,
        streak_days: db.learner_profile?.current_streak_days,
        due_reviews: st.computed.due_reviews_count,
        words_known: items.filter(i => i.type === 'vocabulary').length,
        practised_today: { sessions: sessions.length, minutes: sessions.reduce((n: number, x: any) => n + (x.duration_minutes ?? 0), 0) },
        weak_patterns: weak,
        village: g ? { age: g.age, resources: g.resources, event: g.event?.kind ?? null } : null,
        // rules the dialogs said often enough without asking for them: ripe to introduce (a module, the page extended)
        grammar_ripe: ripe(g),
      },
      null,
      2,
    )
  }

  async function runRhythm(now = new Date()) {
    try {
      await wake(now)
    } catch (e) {
      log(`rhythm failed: ${(e as Error).message}`) // a background job must never end the process
    }
  }

  async function wake(now: Date) {
    for (const r of dueRoutines(now, store.read()) as Routine[]) {
      store.markRan(r, now) // before sending: a failing snapshot must not fire every minute
      let snap = '{}'
      try {
        snap = snapshot()
      } catch (e) {
        log(`rhythm snapshot failed: ${(e as Error).message}`)
      }
      await channel.notify(`${routineAsk[r]}\n\n\`\`\`json\n${snap}\n\`\`\``, {
        kind: `daily_${r}`,
        conversation_id: 'main',
        msg_id: `rhythm-${r}-${Date.now().toString(36)}`,
      })
      log(`rhythm: ${r}`)
    }
  }

  return {
    instructions,
    tools: [
      {
        name: 'set_rhythm',
        description:
          'Show or change the daily rhythm (morning plan, evening recap, weekly review). Pass only what changes; times are HH:MM local, null turns one off, weekly_day 0 = Sunday. With no arguments, returns the current settings.',
        inputSchema: {
          type: 'object',
          properties: {
            enabled: { type: 'boolean' },
            morning: { type: ['string', 'null'] },
            evening: { type: ['string', 'null'] },
            weekly_day: { type: ['number', 'null'] },
            weekly: { type: 'string' },
          },
        },
        handle: args => {
          try {
            const settings = Object.keys(args).length ? store.update(args) : store.read().settings
            return ok(JSON.stringify(settings, null, 2))
          } catch (e) {
            return fail(`invalid rhythm settings: ${(e as Error).message}`)
          }
        },
      },
    ],
    start() {
      if (process.env.LANI_RHYTHM === 'off') return
      setTimeout(() => void runRhythm(), 15_000) // after startup, so a restart catches up on a missed routine
      setInterval(() => void runRhythm(), 60_000)
    },
  }
}
