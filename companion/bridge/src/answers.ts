// The learner's recent answers (<data>/app/answers.json): what they typed or said in a review, in a form a review asked
// and in a dialog's word turn, against what was expected, with the verdict, the grading hint and the cheat sheet's cards
// opened during it (looked_up: a right answer then counts as right with effort). When Jan reports a card as wrong later,
// in a plain chat message (no exercise attached), the tutor can see what was typed and why it was marked so (the tool
// recent_answers, the lani-fix skill). A rolling log: the last ANSWERS_KEPT.days days, at most
// ANSWERS_KEPT.max answers, the oldest dropped as new ones come. Nothing else reads it, the learner's databases don't
// change with it, and a data repository never commits it (.claude/hooks/lani_data_repo.py PRIVATE: it stays on the
// machine, docs/privacy.md).
import { readFileSync } from 'node:fs'
import { z } from 'zod'
import { writeAtomic } from './pairing'

/** How much the log keeps: answers of the last [days] days, at most [max] of them. */
export const ANSWERS_KEPT = { days: 7, max: 500 }

/** Where an answer was given: a review card's exercise, a form of a card's word a review asked, a dialog's word turn. */
export type AnswerKind = 'review' | 'form' | 'dialog'

/**
 * Where in the app a review came from (POST /reviews `from`), as the tutor reads it, and whether the learner types or
 * picks an answer there (the car's ratings and I spy's taps have nothing typed). An app from before sends none.
 */
export const REVIEW_FROM: Record<string, { label: string; typed: boolean }> = {
  review: { label: 'the review', typed: true },
  grammar: { label: "the grammar book's card practice", typed: true },
  upgrade: { label: "a building's words run", typed: true },
  challenge: { label: 'a village challenge', typed: true },
  ispy: { label: 'I spy (the thing tapped in the picture)', typed: false },
  car: { label: "the car (✓ knew / ✗ didn't pressed)", typed: false },
  'car-quiz': { label: "the car's quiz", typed: true },
  dialog: { label: "a scene's dialog", typed: true },
}

/** How [from] reads for the tutor ("the car (…)"); one the bridge doesn't know, as it came. */
export const fromLabel = (from: string): string => REVIEW_FROM[from]?.label ?? from

/** Where a review came from ([REVIEW_FROM]) as the app sends it; undefined when it sent none or one that isn't a name. */
export const fromOf = (raw: unknown): string | undefined => (typeof raw === 'string' && /^[a-z][a-z0-9-]{0,23}$/.test(raw) ? raw : undefined)

/**
 * The cheat sheet (📋 Plonk listek) opened while an exercise was open: the cards opened ([cards]: their keys, page:biti,
 * deck:zaimki-osebe, tutor:…, pin:…) and their grammar pages ([pages]); both empty: the sheet opened, no card.
 */
export type LookedUp = { cards: string[]; pages: string[] }

export type Answer = {
  /** When it was answered (ISO): the app's time (the answer's, else the review's), else when it reached the node. */
  at: string
  kind: AnswerKind
  /** The review card (spaced-repetition.json). */
  item_id: string
  /** Where in the app ([REVIEW_FROM]); absent: an app from before said nothing. */
  from?: string
  /** The session it was persisted as, if it was. */
  session_id?: string
  /** Another language's deck ("it"); absent: the home language's. */
  language?: string
  /** How the card was asked (type, tiles, listen, …); a dialog's word: meaning or form. */
  variant?: string
  /** The exercise (translate, choice, cloze, …). */
  type?: string
  prompt?: string
  /** What was typed or said ("": nothing). Absent: nothing typed, or no answer sent. */
  answer?: string
  expected?: string
  verdict?: 'correct' | 'almost' | 'wrong'
  /** Why it wasn't exact (typo, ending, diacritics, …). */
  hint?: string
  /** [answer] is what the speech recognizer heard. */
  spoken?: boolean
  /** 💡 hints used. */
  hints_used?: number
  /** The cheat sheet opened during it: the cards opened, their grammar pages (none: no card opened). */
  looked_up?: LookedUp
  /** The quality the review gave the card (0-5); absent: the bridge didn't count it as a review. */
  quality?: number
  /** A form of the word asked: its slot ("pres.1sg") and its grammar page. */
  form?: string
  page?: string
  /** A dialog's who and where. */
  where?: string
}

/** An answer to add: its time is the app's when it sent one ([at]), else the arrival's. */
export type NewAnswer = Omit<Answer, 'at'> & { at?: string }

/** A time the app sent (ISO), if it reads as one; else undefined (the arrival's time is used then). */
export function timeOf(raw: unknown): string | undefined {
  if (typeof raw !== 'string' || raw.length > 40) return undefined
  const t = Date.parse(raw)
  return Number.isNaN(t) ? undefined : new Date(t).toISOString()
}

const text = (max: number) => z.string().max(max).transform(s => s.trim().slice(0, 200))

/** At most 20 distinct short strings of a list, trimmed (one longer than [max] or not a string left out); not a list: none. */
const keys = (max: number) =>
  z.array(z.unknown()).catch([]).transform(v => [...new Set(v.flatMap(x => (typeof x === 'string' && x.trim() && x.trim().length <= max ? [x.trim()] : [])))].slice(0, 20))

/** [LookedUp] as the app sends it: the cards' keys (≤ 80 characters) and their pages (≤ 64), a bad entry left out. */
export const lookedUp = z.object({ cards: keys(80), pages: keys(64) })

/** A `looked_up` the app sent, if it is an object; else undefined (left out, never what carried it). */
export const lookedUpOf = (raw: unknown): LookedUp | undefined => {
  const r = lookedUp.safeParse(raw)
  return r.success ? r.data : undefined
}

/**
 * One answer of a review as the app sends it (`answers` of POST /reviews), each field optional but the card: what was
 * asked and how, what was typed or said, what was expected, the verdict, the hint, the cheat sheet opened during it
 * (`looked_up`; a malformed one is left out, never the answer), when it was answered (`at`, ISO; one that doesn't read
 * as a time is left out, never the answer). Not strict: a field the bridge doesn't know is dropped.
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
  looked_up: z.unknown().optional().transform(lookedUpOf),
  at: z.unknown().optional().transform(timeOf),
})
export type ReviewAnswer = z.infer<typeof reviewAnswer>

/** The most answers of one review the log takes; the rest are left out (never the review). */
export const ANSWERS_PER_REVIEW = 100

/** The answers of a review body that are well formed (at most ANSWERS_PER_REVIEW); a bad one is left out, never the review. */
export function reviewAnswers(raw: unknown): ReviewAnswer[] {
  if (!Array.isArray(raw)) return []
  return raw.slice(0, ANSWERS_PER_REVIEW).flatMap(x => {
    const r = reviewAnswer.safeParse(x)
    return r.success ? [r.data] : []
  })
}

/**
 * What to look for in the log: a card, a word in what was asked, typed or expected, only the wrong ones, only those given
 * with the cheat sheet open ([looked_up]), a language.
 */
export type AnswerQuery = { item_id?: string; text?: string; wrong?: boolean; looked_up?: boolean; language?: string; limit?: number }

/**
 * [s] for a search: lower case, without diacritics (č→c, š→s, ž→z, ć→c, đ→d; ä→a, ö→o, ü→u, ß→ss; à→a, è→e, …), on
 * both sides, so "zivjo" finds "živjo" and "ŽIVJO" finds "zivjo".
 */
export const fold = (s: string | undefined): string =>
  (s ?? '').normalize('NFD').replace(/\p{M}+/gu, '').toLocaleLowerCase('sl').replace(/đ/g, 'd').replace(/ß/g, 'ss').replace(/ł/g, 'l').replace(/ø/g, 'o')

/** Whether [a] was marked wrong (or almost): its verdict; one without a verdict (an app from before sent none) by its quality. */
export const markedWrong = (a: Answer): boolean =>
  a.verdict !== undefined ? a.verdict === 'wrong' || a.verdict === 'almost' : a.quality !== undefined && a.quality < 3

const time = (a: Answer) => Date.parse(a.at)

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
      return Array.isArray(raw?.answers) ? (raw.answers as Answer[]).filter(a => a && typeof a.item_id === 'string' && typeof a.at === 'string' && !Number.isNaN(time(a))) : []
    } catch {
      return []
    }
  }

  /**
   * Adds [answers], each at the time it was answered ([NewAnswer.at]; never later than now: a phone's clock may run
   * ahead), else now; keeps the answers of the days kept, at most the newest [kept].max, in the order they were
   * answered. Written atomically, readable by this user only. Never throws.
   */
  add(answers: NewAnswer[]): void {
    if (!answers.length) return
    try {
      const now = this.now()
      const since = now.getTime() - this.kept.days * 86_400_000
      const stamp = (at: string | undefined) => {
        const t = at === undefined ? Number.NaN : Date.parse(at)
        return (Number.isNaN(t) || t > now.getTime() ? now : new Date(t)).toISOString()
      }
      const added: Answer[] = answers.map(a => ({ ...a, at: stamp(a.at) }))
      const kept = [...this.all(), ...added].filter(a => time(a) >= since).sort((a, b) => time(a) - time(b)).slice(-this.kept.max)
      writeAtomic(this.file, JSON.stringify({ schema: 'lani.answers/v0', kept: this.kept, answers: kept }, null, 1) + '\n')
    } catch {}
  }

  /** The answers that match [q], newest first, at most [q].limit (20; 100 at most). */
  find(q: AnswerQuery = {}): Answer[] {
    const t = fold(q.text?.trim())
    const out = this.all()
      .filter(a => !q.item_id || a.item_id === q.item_id)
      .filter(a => !q.wrong || markedWrong(a))
      .filter(a => !q.looked_up || (!!a.looked_up && typeof a.looked_up === 'object'))
      .filter(a => q.language === undefined || (a.language ?? '') === q.language)
      .filter(a => !t || [a.prompt, a.answer, a.expected, a.item_id, a.where].some(s => fold(s).includes(t)))
      .reverse()
    return out.slice(0, Math.max(1, Math.min(100, q.limit ?? 20)))
  }
}

/** What the cheat sheet named while it was open: the pages, else the cards' keys (a card with no page); none: nothing. */
const namedBy = (l: LookedUp): string[] => (l.pages.length ? l.pages : l.cards)

/** "📋 looked up biti, osebni-zaimki" for an answer's line: the pages, else the cards; none: the sheet opened, no card. */
const lookedUpPart = (l: LookedUp | undefined): string => {
  if (!l || typeof l !== 'object') return ''
  const named = namedBy({ cards: Array.isArray(l.cards) ? l.cards : [], pages: Array.isArray(l.pages) ? l.pages : [] })
  return named.length ? `📋 looked up ${named.join(', ')}` : '📋 the cheat sheet opened, no card'
}

/**
 * An answer as one line for the tutor: `2026-10-11 09:14 review vocab_kurim (type, translate) "kurim" for "kurim ogenj":
 * wrong, ending; quality 1; 📋 looked up glagoli-sedanjik; asked "to make a fire"; in the review`. One with nothing typed
 * says so, and why: there is nothing to type where it came from, or an app from before sent no answer.
 */
export function answerLine(a: Answer): string {
  const when = a.at.slice(0, 16).replace('T', ' ')
  const how = [a.variant, a.type].filter(Boolean).join(', ')
  const said = a.answer === undefined ? '' : a.answer === '' ? ' (no answer)' : ` "${a.answer}"${a.spoken ? ' (heard)' : ''}`
  const want = a.expected ? ` for "${a.expected}"` : ''
  const verdict = [a.verdict, a.hint && a.hint !== 'none' ? a.hint : undefined].filter(Boolean).join(', ')
  const nothing = a.answer !== undefined || a.kind !== 'review' ? ''
    : !a.from ? 'no answer sent (an app from before sends only the quality)'
    : a.type === 'flashcard' ? 'nothing typed (a flashcard, self-rated)'
    : REVIEW_FROM[a.from]?.typed === false ? 'nothing typed there'
    : 'no answer sent'
  const tail = [
    verdict,
    a.quality !== undefined ? `quality ${a.quality}` : '',
    a.hints_used ? `${a.hints_used} hint(s)` : '',
    lookedUpPart(a.looked_up),
    a.prompt ? `asked "${a.prompt}"` : '',
    a.form ? `form ${a.form}${a.page ? ` (${a.page})` : ''}` : '',
    nothing,
    a.where ?? '',
    a.from ? `in ${fromLabel(a.from)}` : '',
    a.language ? `language ${a.language}` : '',
    a.session_id ? `session ${a.session_id}` : '',
  ]
    .filter(Boolean)
    .join('; ')
  return `${when} ${a.kind} ${a.item_id}${how ? ` (${how})` : ''}${said}${want}${tail ? `: ${tail}` : ''}`
}

/** One exercise's look at the cheat sheet, for [lookedUpNote]: its [looked_up], its worksheet items answered after the sheet opened ([items]). */
export type LookedUpIn = { looked_up?: LookedUp; items?: number }

/** The most topics a note names; the rest are counted. */
const NAMES_SHOWN = 6

const counted = (n: number, one: string) => `${n} ${one}${n === 1 ? '' : 's'}`

/**
 * " 📋 Looked up on the cheat sheet: biti ×3, tozilnik ×2, mestnik (during 4 answers)." for the tutor's note on a run:
 * each of [records] with a looked_up counts once, and once for each page it names (a card without a page by its key):
 * most first, then by name, the first six named and the rest counted; the worksheet items answered after the sheet
 * opened ([LookedUpIn.items]) said too ("during 1 exercise, 6 worksheet items answered after it opened"). [one]: what a
 * record is ("answer", "exercise"). Empty when the sheet wasn't opened.
 */
export function lookedUpNote(records: LookedUpIn[], one = 'answer'): string {
  const looked = records.flatMap(r => (r.looked_up ? [r.looked_up] : []))
  const items = records.reduce((n, r) => n + (r.items ?? 0), 0)
  if (!looked.length && !items) return ''
  const times = new Map<string, number>()
  for (const l of looked) for (const k of new Set(namedBy(l))) times.set(k, (times.get(k) ?? 0) + 1)
  const named = [...times].sort(([a, x], [b, y]) => y - x || a.localeCompare(b)).map(([k, n]) => (n > 1 ? `${k} ×${n}` : k))
  const what = named.length
    ? `${named.slice(0, NAMES_SHOWN).join(', ')}${named.length > NAMES_SHOWN ? ` and ${named.length - NAMES_SHOWN} more` : ''}`
    : looked.length ? 'the sheet, no card opened' : 'the sheet'
  const during = [looked.length ? `during ${counted(looked.length, one)}` : '', items ? `${counted(items, 'worksheet item')} answered after it opened` : '']
  return ` 📋 Looked up on the cheat sheet: ${what} (${during.filter(Boolean).join(', ')}).`
}

/**
 * What a session_end's results ([raw]: data.results) say of the cheat sheet, for [lookedUpNote]: each result's looked_up
 * (one that isn't an object left out) and its worksheet items with looked_up true; not a list: none.
 */
export function resultsLookedUp(raw: unknown): LookedUpIn[] {
  if (!Array.isArray(raw)) return []
  return raw.slice(0, 200).flatMap(r => {
    if (!r || typeof r !== 'object') return []
    const o = r as { looked_up?: unknown; items?: unknown }
    const looked_up = lookedUpOf(o.looked_up)
    const items = Array.isArray(o.items) ? o.items.filter(i => !!i && typeof i === 'object' && (i as { looked_up?: unknown }).looked_up === true).length : 0
    return looked_up || items ? [{ ...(looked_up ? { looked_up } : {}), ...(items ? { items } : {}) }] : []
  })
}
