// The learner's recent answers (<data>/app/answers.json): what they typed or said in a review, in a form a review asked
// and in a dialog's word turn, against what was expected, with the verdict and the grading hint. When Jan reports a card
// as wrong later, in a plain chat message (no exercise attached), the tutor can see what was typed and why it was marked
// so (the tool recent_answers, the lani-fix skill). A rolling log: the last ANSWERS_KEPT.days days, at most
// ANSWERS_KEPT.max answers, the oldest dropped as new ones come. Nothing else reads it, the learner's databases don't
// change with it, and the data repository leaves it out (.gitignore: runtime state).
import { readFileSync, renameSync, writeFileSync } from 'node:fs'
import { z } from 'zod'

/** How much the log keeps: answers of the last [days] days, at most [max] of them. */
export const ANSWERS_KEPT = { days: 7, max: 500 }

/** Where an answer was given: a review card's exercise, a form of a card's word a review asked, a dialog's word turn. */
export type AnswerKind = 'review' | 'form' | 'dialog'

export type Answer = {
  /** When it reached the node (ISO). */
  at: string
  kind: AnswerKind
  /** The review card (spaced-repetition.json). */
  item_id: string
  /** The session it was persisted as, if it was. */
  session_id?: string
  /** Another language's deck ("it"); absent: the home language's. */
  language?: string
  /** How the card was asked (type, tiles, listen, …); a form's slot ("pres.1sg"). */
  variant?: string
  /** The exercise (translate, choice, cloze, …). */
  type?: string
  prompt?: string
  /** What was typed or said ("": nothing). */
  answer?: string
  expected?: string
  verdict?: 'correct' | 'almost' | 'wrong'
  /** Why it wasn't exact (typo, ending, diacritics, …). */
  hint?: string
  /** [answer] is what the speech recognizer heard. */
  spoken?: boolean
  /** 💡 hints used. */
  hints_used?: number
  /** The quality the review gave the card (0-5). */
  quality?: number
  /** A form's grammar page; a dialog's who and where. */
  where?: string
}

const text = (max: number) => z.string().max(max).transform(s => s.trim().slice(0, 200))

/**
 * One answer of a review as the app sends it (`answers` of POST /reviews), each field optional but the card: what was
 * asked and how, what was typed or said, what was expected, the verdict, the hint.
 */
export const reviewAnswer = z.object({
  item_id: z.string().trim().min(1).max(128),
  variant: z.string().trim().max(24).optional(),
  type: z.string().trim().max(24).optional(),
  prompt: text(400).optional(),
  answer: text(400).optional(),
  expected: text(400).optional(),
  verdict: z.enum(['correct', 'almost', 'wrong']).optional(),
  hint: z.string().trim().max(24).optional(),
  spoken: z.boolean().optional(),
  hints_used: z.number().int().min(0).max(20).optional(),
})
export type ReviewAnswer = z.infer<typeof reviewAnswer>

/** The answers of a review body that are well formed (at most 100); a bad one is left out, never the review. */
export function reviewAnswers(raw: unknown): ReviewAnswer[] {
  if (!Array.isArray(raw)) return []
  return raw.slice(0, 100).flatMap(x => {
    const r = reviewAnswer.safeParse(x)
    return r.success ? [r.data] : []
  })
}

/** What to look for in the log: a card, a word in what was asked, typed or expected, only the wrong ones, a language. */
export type AnswerQuery = { item_id?: string; text?: string; wrong?: boolean; language?: string; limit?: number }

const low = (s: string | undefined) => (s ?? '').normalize('NFC').toLocaleLowerCase('sl')

export class AnswerLog {
  constructor(
    private readonly file: string,
    private readonly now: () => Date = () => new Date(),
    readonly kept = ANSWERS_KEPT,
  ) {}

  /** Every answer kept, oldest first (none when the file is missing or unreadable). */
  all(): Answer[] {
    try {
      const raw = JSON.parse(readFileSync(this.file, 'utf8'))
      return Array.isArray(raw?.answers) ? (raw.answers as Answer[]).filter(a => a && typeof a.item_id === 'string' && typeof a.at === 'string') : []
    } catch {
      return []
    }
  }

  /** Adds [answers] (stamped now), dropping what is older than the days kept and the oldest beyond the most kept. Never throws. */
  add(answers: Omit<Answer, 'at'>[]): void {
    if (!answers.length) return
    try {
      const now = this.now()
      const since = now.getTime() - this.kept.days * 86_400_000
      const at = now.toISOString()
      const kept = [...this.all().filter(a => Date.parse(a.at) >= since), ...answers.map(a => ({ at, ...a }))].slice(-this.kept.max)
      writeFileSync(`${this.file}.tmp`, JSON.stringify({ schema: 'lani.answers/v0', kept: this.kept, answers: kept }, null, 1))
      renameSync(`${this.file}.tmp`, this.file)
    } catch {}
  }

  /** The answers that match [q], newest first, at most [q].limit (20; 100 at most). */
  find(q: AnswerQuery = {}): Answer[] {
    const t = low(q.text?.trim())
    const out = this.all()
      .filter(a => !q.item_id || a.item_id === q.item_id)
      .filter(a => !q.wrong || a.verdict === 'wrong' || a.verdict === 'almost' || (a.quality !== undefined && a.quality < 3))
      .filter(a => q.language === undefined || (a.language ?? '') === q.language)
      .filter(a => !t || [a.prompt, a.answer, a.expected, a.item_id].some(s => low(s).includes(t)))
      .reverse()
    return out.slice(0, Math.max(1, Math.min(100, q.limit ?? 20)))
  }
}

/** An answer as one line for the tutor: `2026-10-11 09:14 review vocab_kurim (type, translate) "kurim" for "kurim ogenj": wrong, ending; quality 1; asked "to make a fire"`. */
export function answerLine(a: Answer): string {
  const when = a.at.slice(0, 16).replace('T', ' ')
  const how = [a.variant, a.type].filter(Boolean).join(', ')
  const said = a.answer === undefined ? '' : a.answer === '' ? ' (no answer)' : ` "${a.answer}"${a.spoken ? ' (heard)' : ''}`
  const want = a.expected ? ` for "${a.expected}"` : ''
  const verdict = [a.verdict, a.hint && a.hint !== 'none' ? a.hint : undefined].filter(Boolean).join(', ')
  const tail = [verdict, a.quality !== undefined ? `quality ${a.quality}` : '', a.hints_used ? `${a.hints_used} hint(s)` : '', a.prompt ? `asked "${a.prompt}"` : '', a.where ?? '', a.language ? `language ${a.language}` : '', a.session_id ? `session ${a.session_id}` : '']
    .filter(Boolean)
    .join('; ')
  return `${when} ${a.kind} ${a.item_id}${how ? ` (${how})` : ''}${said}${want}${tail ? `: ${tail}` : ''}`
}
