// Lazy preloading (docs/plans/05-built/audio-names.md): at the start of a dialog, a test or a module the app says what the
// learner is about to hear, in play order, and the node gets the lines it lacks ready one at a time, at a turn between a live
// line and the background (voice.ts TURN). Only what this learner hears: their level's lines, their gender and their name, the
// app having rendered them; a wrong answer's reaction stays on demand. A new start replaces the last, and leaving cancels it.
//
// Off unless the node sets LANI_VOICE_PRELOAD: it spends without anyone waiting for a line.
import { normalizeText } from './family'
import type { VoiceName, VoiceStore } from './voice'

export type PreloadSettings = {
  /** Lines are got ready ahead (LANI_VOICE_PRELOAD). */
  on: boolean
  /** The characters that may cost a day (LANI_VOICE_PRELOAD_DAILY). */
  daily: number
}

export const preloadSettings = (env: Record<string, string | undefined> = process.env): PreloadSettings => {
  const v = Number(env.LANI_VOICE_PRELOAD_DAILY?.trim() || NaN)
  return { on: /^(on|1|true|yes)$/i.test(env.LANI_VOICE_PRELOAD?.trim() ?? ''), daily: Number.isInteger(v) && v >= 0 ? v : 4_000 }
}

/** The most lines one start may name. */
export const PRELOAD_LINES = 60

export type PreloadLine = { text: string; voice: VoiceName }

const today = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export class Preload {
  /** What is being got ready, and for which start of the app's. */
  private job?: { id: string; todo: PreloadLine[] }
  private working = false

  constructor(
    private readonly store: VoiceStore,
    private readonly o: {
      settings: PreloadSettings
      /** Whether a clip of [line] is there, or none of it can be had in this voice (nothing to do). */
      done: (line: PreloadLine) => boolean
      /** Gets [line] ready, in the preload's turn. */
      make: (line: PreloadLine) => Promise<void>
      log?: (...a: unknown[]) => void
    },
  ) {}

  get on(): boolean { return this.o.settings.on }

  /** The characters preloading cost today. */
  spentToday(): number {
    if (!this.on) return 0
    return (this.store.database.query('SELECT chars FROM spend WHERE month = ?').get(`preload:${today()}`) as { chars: number } | null)?.chars ?? 0
  }

  private count(chars: number) {
    if (chars > 0) this.store.database.query('INSERT INTO spend (month, chars) VALUES (?, ?) ON CONFLICT (month) DO UPDATE SET chars = chars + excluded.chars').run(`preload:${today()}`, chars)
  }

  /** [lines] for the start [id], in play order, in place of whatever was being got ready: how many there are to make. */
  start(id: string, lines: PreloadLine[]): number {
    if (!this.on) return 0
    const seen = new Set<string>()
    const todo = lines.filter(l => {
      const key = `${l.voice}:${normalizeText(l.text)}`
      if (seen.has(key)) return false
      seen.add(key)
      return !this.o.done(l)
    })
    const n = todo.length
    this.job = n ? { id, todo } : undefined
    if (n) void this.run()
    return n
  }

  /** The learner left what [id] was for: what is left of it isn't made (a take at work ends, and is kept). */
  cancel(id: string) {
    if (this.job?.id === id) this.job = undefined
  }

  private async run() {
    if (this.working) return
    this.working = true
    try {
      for (let line = this.job?.todo.shift(); line; line = this.job?.todo.shift()) {
        if (this.spentToday() + this.store.cost(line.text, line.voice) > this.o.settings.daily) {
          this.job = undefined
          this.o.log?.(`voice: the day's characters for getting lines ready are spent (${this.spentToday()} of ${this.o.settings.daily})`)
          break
        }
        const before = this.store.spentThisMonth()
        await this.o.make(line).catch(e => this.o.log?.(`voice: a line wasn't got ready: ${(e as Error).message}`))
        this.count(this.store.spentThisMonth() - before)
      }
    } finally {
      this.working = false
    }
  }
}
