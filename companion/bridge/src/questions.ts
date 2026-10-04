// The host takes part (plan 2, §3.2; companion/README.md, "Questions between towns"): on a visit the guest asks the host
// a question in the host town's language, the host answers in it, and both practise, each with their own tutor. What each
// side keeps, in <data>/app/town-questions.json:
// - `received`: the questions guests from linked towns asked this town's learner, each once by "<their town>:<their
//   id>" (a retry is the same question), with this learner's answer once written and whether it reached them yet;
// - `asked`: the questions this town's learner asked other towns, by "<that town>:<id>", with the host's answer once it
//   came;
// - on either, what the learner's own tutor said of their text (`feedback`): the guest's tutor grades the question, the
//   host's the answer, and each records it as practice in that language.
// The texts between towns are plain (one line, no < or >, short) and untrusted content to both tutors
// (features/questions.ts).
import { existsSync, mkdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import type { GuestFrom } from './guests'
import { withLock, writeAtomic } from './pairing'
import { cleanText } from './towns'

/** A guest's question: one line of plain text, at most this long. */
export const MAX_QUESTION = 200
/** The host's answer: one line of plain text, at most this long. */
export const MAX_ANSWER = 300
/** At most this many entries of each side are kept (the oldest go). */
const KEEP = 300

/** What the learner's own tutor said of their question or answer (its reply, with its score and a corrected text). */
export type Feedback = { text: string; score?: number; corrected?: string; at: string }

/**
 * A question a guest from linked town [from] asked this town's learner, in this town's [language], once by [key]
 * ("<their town>:<their id>"). [answer]: this learner's, once written: [sent] when it reached the guest's town; the
 * tutor's note on it came as [conversation] ("tq_…"), its reply is [feedback].
 */
export type ReceivedQuestion = {
  key: string
  id: string
  from: GuestFrom
  at: string
  language: string
  text: string
  answer?: { text: string; at: string; sent: boolean; conversation?: string; feedback?: Feedback }
}

/**
 * A question this town's learner asked linked town [town], in its [language], by [key] ("<that town>:<id>"). The tutor's
 * note on it came as [conversation] ("tq_…"), its reply is [feedback]; [answer]: the host's, once it came.
 */
export type AskedQuestion = {
  key: string
  id: string
  town: GuestFrom
  at: string
  language: string
  text: string
  conversation?: string
  feedback?: Feedback
  answer?: { text: string; at: string }
}

export type QuestionsFile = { schema: 'lani.questions/v1'; received: ReceivedQuestion[]; asked: AskedQuestion[] }

/** <appDir>/town-questions.json: the questions this town was asked and asked, each side's answers and feedback. */
export class QuestionBook {
  readonly file: string

  constructor(appDir: string) {
    mkdirSync(appDir, { recursive: true })
    this.file = join(appDir, 'town-questions.json')
  }

  private read(): QuestionsFile {
    try {
      const d = existsSync(this.file) ? JSON.parse(readFileSync(this.file, 'utf8')) : undefined
      const list = (v: unknown) => (Array.isArray(v) ? v.filter(e => e && typeof e.key === 'string') : [])
      return { schema: 'lani.questions/v1', received: list(d?.received), asked: list(d?.asked) }
    } catch {
      return { schema: 'lani.questions/v1', received: [], asked: [] }
    }
  }

  received(): ReceivedQuestion[] {
    return this.read().received
  }

  asked(): AskedQuestion[] {
    return this.read().asked
  }

  /** Runs [fn] on the book as it is on disk, under its lock, and writes what it changed. */
  change<T>(fn: (d: QuestionsFile) => T): T {
    return withLock(`${this.file}.lock`, () => {
      const d = this.read()
      const before = JSON.stringify(d)
      const out = fn(d)
      d.received = d.received.slice(-KEEP)
      d.asked = d.asked.slice(-KEEP)
      if (JSON.stringify(d) !== before) writeAtomic(this.file, JSON.stringify(d, null, 1) + '\n')
      return out
    })
  }
}

/**
 * How many questions were answered between this town and town [id], both ways: this learner's questions it answered, and
 * its learner's questions this learner answered (and that reached them). The friendship between towns (plan 2, §3.3; M5)
 * grows by these.
 */
export function answeredWith(book: QuestionBook, id: string): number {
  return book.asked().filter(q => q.town.id === id && q.answer).length + book.received().filter(q => q.from.id === id && q.answer?.sent).length
}

/** The questions town [id] asked here that this learner hasn't answered yet. */
export const openFrom = (book: QuestionBook, id: string) => book.received().filter(q => q.from.id === id && !q.answer).length

/**
 * Whether [text] carries a link, an e-mail address or a web address: a child's town takes none (the child's rules: no
 * links, no personal data asked for).
 */
export const hasLink = (text: string) => /https?:|www\.|[\w.+-]+@[\w-]+\.[a-z]|\b[\w-]+\.(com|net|org|si|it|de|at|eu|io|app|me|ly)\b/i.test(text)

/** The tutor's reply to a question's or an answer's note (its text and data), kept as feedback. */
export function feedbackOf(text: unknown, data: unknown, now = Date.now()): Feedback {
  const d = (data && typeof data === 'object' ? data : {}) as Record<string, unknown>
  const score = typeof d.score === 'number' && Number.isFinite(d.score) ? Math.max(0, Math.min(10, d.score)) : undefined
  const corrected = cleanText(d.corrected, MAX_ANSWER)
  return {
    text: (typeof text === 'string' ? text : '').slice(0, 4000),
    ...(score !== undefined ? { score } : {}),
    ...(corrected ? { corrected } : {}),
    at: new Date(now).toISOString(),
  }
}

// --- limits -------------------------------------------------------------------------------------------------------------

/**
 * What one linked town may write here: [question]s a day, and writes (questions and answers) an hour; and how many of
 * its questions may wait for an answer at once ([open]).
 */
export type QuestionLimits = { question: number; open: number; hour: number }

export function questionLimitsFromEnv(env: Record<string, string | undefined> = process.env): QuestionLimits {
  const n = (v: string | undefined, d: number) => (v && Number.isInteger(Number(v)) && Number(v) >= 0 ? Number(v) : d)
  return {
    question: n(env.LANI_TOWN_QUESTIONS_PER_DAY, 10),
    open: n(env.LANI_TOWN_OPEN_QUESTIONS, 5),
    hour: n(env.LANI_TOWN_WRITES_PER_HOUR, 30),
  }
}

const DAY_MS = 24 * 60 * 60_000
const HOUR_MS = 60 * 60_000

/** The questions and answers each linked town sent lately (in memory: a restart forgets them; the limits are about floods). */
export class QuestionRate {
  private seen = new Map<string, number[]>()

  constructor(readonly limits: QuestionLimits) {}

  /** Milliseconds until town [from] may send a [kind] again; 0 when it may now (and it is counted). */
  take(from: string, kind: 'question' | 'answer', now = Date.now()): number {
    const day = (this.seen.get(`${from}:${kind}`) ?? []).filter(t => now - t < DAY_MS)
    const hour = (this.seen.get(`${from}:*`) ?? []).filter(t => now - t < HOUR_MS)
    const waits = [
      kind === 'question' && day.length >= this.limits.question ? (day[0] ?? now) + DAY_MS - now : 0,
      hour.length >= this.limits.hour ? (hour[0] ?? now) + HOUR_MS - now : 0,
    ]
    const wait = Math.max(...waits, 0)
    if (wait > 0) return Math.max(1, wait)
    this.seen.set(`${from}:${kind}`, [...day, now])
    this.seen.set(`${from}:*`, [...hour, now])
    return 0
  }
}
