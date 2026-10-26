// Learning progress between linked towns (companion/README.md, "Progress between towns"; docs/plans/05-built/
// progress-sharing.md). The sharer decides: each town sets, per linked town, what that town may see of its learning
// ([ShareLevel] in towns.ts: "village", "summary" or "details", the link's [shareOf]); GET /town/progress answers a linked
// town at that level, built here field by field from the learner's databases (read-db's output: the Python scripts'
// TypeScript twin on the phone), so nothing else of them ever leaves:
// - summary: the streak, this week's days (as dots), minutes, reviews and new words, the words known, the level and the
//   school's unit;
// - details: summary, plus the words being learned now (the newest cards, with their meaning), what is hard (the error
//   patterns with recent mistakes: their name, and the cards their mistakes name; the cards failed lately) and what is due.
// Never at any level: the tutor chat, free-text answers and what was said in them (a mistake's examples: what was typed,
// the correction, its context; an error pattern's own card, which holds what was typed; its notes), recordings, the
// family's messages, the session notes, raw databases.
// Shared by the bridge (features/progress.ts), its smoke and the CLI (cli/town.ts).
import { SHARE_LEVELS, cleanText, isShareLevel, localDate, shareOf, shareRank, type ShareLevel, type TownLink } from './towns'

/** The version of a progress answer. */
export const PROGRESS_VERSION = 1
/** Words being learned shown at Details: the newest cards. */
export const LEARNING_MAX = 20
/** Error patterns shown as hard, and cards under each. */
export const PATTERNS_MAX = 5
export const PATTERN_ITEMS_MAX = 4
/** Cards failed lately shown as hard. */
export const HARD_WORDS_MAX = 10
/** "Lately": a mistake or a failed review in the last four weeks. */
export const RECENT_DAYS = 28

const TOKEN = /^[A-Za-z0-9_:.-]{1,80}$/
const LANG = /^[a-z]{2,3}$/
const LEVELS = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2']
/** The kinds of review cards whose front and back are the word and its meaning (never what the learner typed). */
const CARD_TYPES = new Set(['vocabulary', 'phrase', 'grammar_rule', 'forms', 'counting'])

const obj = (v: unknown): Record<string, any> => (v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, any>) : {})
const arr = (v: unknown): any[] => (Array.isArray(v) ? v : [])
const num = (v: unknown) => (typeof v === 'number' && Number.isFinite(v) ? v : 0)
const int = (v: unknown) => Math.round(num(v))
const token = (v: unknown) => (typeof v === 'string' && TOKEN.test(v) ? v : undefined)

/** "YYYY-MM-DD" of a date's text, or undefined. */
export function dateOf(v: unknown): string | undefined {
  const m = typeof v === 'string' ? /^(\d{4})-(\d{2})-(\d{2})/.exec(v) : null
  return m && !Number.isNaN(Date.UTC(+m[1], +m[2] - 1, +m[3])) ? `${m[1]}-${m[2]}-${m[3]}` : undefined
}
const dayNum = (iso: string) => Date.UTC(+iso.slice(0, 4), +iso.slice(5, 7) - 1, +iso.slice(8, 10)) / 86_400_000
const isoOfDay = (n: number) => new Date(n * 86_400_000).toISOString().slice(0, 10)
const plusDays = (iso: string, n: number) => isoOfDay(dayNum(iso) + n)

/** The Monday of [iso]'s week (the app's weeks: Monday to Sunday). */
export function weekStart(iso: string): string {
  const n = dayNum(iso)
  const dow = (new Date(n * 86_400_000).getUTCDay() + 6) % 7 // Monday 0
  return isoOfDay(n - dow)
}

/** "2026-W43": [iso]'s ISO week (the weekly note's, once a week). */
export function isoWeek(iso: string): string {
  const n = dayNum(iso)
  const dow = (new Date(n * 86_400_000).getUTCDay() + 6) % 7
  const thursday = n - dow + 3
  const year = new Date(thursday * 86_400_000).getUTCFullYear()
  const week = Math.floor((thursday - dayNum(`${year}-01-01`)) / 7) + 1
  return `${year}-W${String(week).padStart(2, '0')}`
}

/** A card is learned once answered right and not failed since (the app's Stats.learned). */
export function learned(i: Record<string, any>): boolean {
  return !rusty(i) && (int(i.repetitions) >= 1 || int(i.mastery_level) >= 1 || (int(i.total_reviews) === 0 && int(i.last_quality) >= 3 && i.source !== 'lookup'))
}

/** A rusty card: its last review failed (the app's Stats.rusty). */
export function rusty(i: Record<string, any>): boolean {
  return int(i.total_reviews) >= 1 && int(i.repetitions) === 0 && 'last_quality' in i && int(i.last_quality) < 3 && i.source !== 'lookup'
}

/** The current streak: days practised in a row, back from today, or from yesterday while today is still open. */
export function streakOf(days: Set<string>, today: string): number {
  let d = days.has(today) ? today : plusDays(today, -1)
  let n = 0
  while (days.has(d)) {
    n++
    d = plusDays(d, -1)
  }
  return n
}

/** Whether a pattern keeps recurring ("worse"), was answered right since ("better"), or neither (the app's Stats.trend). */
export function trendOf(p: Record<string, any>, today: string): 'worse' | 'steady' | 'better' {
  if (int(p.consecutive_correct) >= 1) return 'better'
  const t = dayNum(today)
  const dates = arr(p.examples).flatMap(e => {
    const d = dateOf(obj(e).date)
    return d ? [t - dayNum(d)] : []
  })
  const recent = dates.filter(a => a >= 0 && a <= 13).length
  const before = dates.filter(a => a >= 14 && a <= 27).length
  return int(p.consecutive_incorrect) >= 2 || recent > before ? 'worse' : 'steady'
}

/** "articles_la_il" → "Articles la il": a pattern's id as a name, when it has no description. */
export const humanize = (id: string) => {
  const s = id.replace(/[_-]+/g, ' ').trim()
  return s.charAt(0).toUpperCase() + s.slice(1)
}

export type Meanings = Record<string, string>
export type ProgressWord = { id: string; word: string; meanings: Meanings; added?: string; state: 'learning' | 'known' | 'rusty' }
export type HardWord = { id: string; word: string; meanings: Meanings; misses: number; last?: string }
export type HardPattern = { id: string; name: string; category: string; count: number; last?: string; trend: 'worse' | 'steady' | 'better'; items: { id: string; word: string; meanings: Meanings }[] }
export type Summary = {
  streak: number
  practised_today: boolean
  week: { start: string; days: boolean[]; days_practised: number; minutes: number; reviews: number; new_words: number }
  words: { known: number; tracked: number }
  level: string
  target_level?: string
  course?: { level: string; done: number; of: number; current?: { id: string; title: Record<string, string> } }
}
export type Details = {
  learning: ProgressWord[]
  hard: { patterns: HardPattern[]; words: HardWord[] }
  due: { today: number; week: number }
}
export type ChallengeResult = { id: string; at: string; type: string; text: string; answered: boolean; answered_at?: string; score?: number; note?: string }
export type Progress = {
  v: number
  town: { id: string; name: string; learner: string; culture: string; language: string }
  /** What this town shares with the asking one. */
  level: ShareLevel
  date: string
  /** This town is a child's. */
  child?: true
  /** The asking town is this child's guardian. */
  guardian?: true
  /** The language the cards' meanings are in (this learner's base). */
  base?: string
  summary?: Summary
  details?: Details
  /** The asking town's own challenges to this learner, and how they went (the tutor's note for the one who asked). */
  challenges?: ChallengeResult[]
}

/** The course's outline as far as the summary needs it: its levels' units (their id, title, whether planned). */
export type CourseOutline = { levels: { level: string; units: { id: string; title: Record<string, string>; planned?: boolean }[] }[] }

/** A card's meanings: its back in the learner's [base], and in [viewer]'s base from the dictionary ([meaning]) when it differs. */
function meaningsOf(i: Record<string, any>, word: string, base: string | undefined, viewer: string | undefined, meaning?: (word: string, base: string) => string | undefined): Meanings {
  const out: Meanings = {}
  const own = cleanText(i.answer, 80)
  if (own && base) out[base] = own
  if (viewer && viewer !== base && meaning) {
    try {
      const m = cleanText(meaning(word, viewer), 80)
      if (m) out[viewer] = m
    } catch {
      // the dictionary can't say: the card's own meaning
    }
  }
  return out
}

/**
 * What town [town] shares with a linked town at [level], from its learner's databases ([db]: read-db's output) and its
 * village ([village]: the app's state, for the school's units; [course]: the school's outline). [viewer]: the asking
 * learner's base, for the meanings ([meaning]: the dictionary's meaning of a word in a base). Every field is picked and
 * cleaned here; nothing else of the databases is in it.
 */
export function progressOf(o: {
  db: any
  level: ShareLevel
  town: Progress['town']
  child: boolean
  guardian: boolean
  base?: string
  viewer?: string
  village?: Record<string, any>
  course?: CourseOutline
  meaning?: (word: string, base: string) => string | undefined
  challenges?: ChallengeResult[]
  now?: number
}): Progress {
  const dbs = obj(obj(o.db).databases)
  const today = dateOf(obj(obj(o.db).computed).today) ?? localDate(o.now)
  const base = typeof o.base === 'string' && LANG.test(o.base) ? o.base : undefined
  const viewer = typeof o.viewer === 'string' && LANG.test(o.viewer) ? o.viewer : undefined
  const head: Progress = {
    v: PROGRESS_VERSION,
    town: o.town,
    level: o.level,
    date: today,
    ...(o.child ? { child: true as const } : {}),
    ...(o.guardian ? { guardian: true as const } : {}),
    ...(o.challenges?.length ? { challenges: o.challenges } : {}),
  }
  if (o.level === 'village') return head

  const profile = obj(dbs.learner_profile)
  const learner = obj(profile.learner)
  const items = Object.entries(obj(obj(dbs.spaced_repetition).items)).filter(([id, i]) => token(id) && obj(i) === i) as [string, Record<string, any>][]
  const cards = items.filter(([, i]) => CARD_TYPES.has(String(i.type ?? '')))
  const vocab = cards.filter(([, i]) => i.type === 'vocabulary')
  const start = weekStart(today)
  const inWeek = (d: string | undefined) => !!d && d >= start && d <= today

  const sessions = arr(obj(dbs.session_log).sessions).map(obj).filter(s => dateOf(s.date))
  const days = new Set(sessions.map(s => dateOf(s.date)!))
  const weekDays = Array.from({ length: 7 }, (_, k) => days.has(plusDays(start, k)) && plusDays(start, k) <= today)
  const minutes = sessions.filter(s => inWeek(dateOf(s.date))).reduce((n, s) => n + Math.max(0, int(s.duration_minutes)), 0)
  const reviews = items.reduce((n, [, i]) => n + arr(i.review_history).filter(r => inWeek(dateOf(obj(r).date))).length, 0)
  const level = LEVELS.includes(learner.current_level) ? (learner.current_level as string) : 'A1'
  const target = LEVELS.includes(learner.target_level) ? (learner.target_level as string) : undefined

  const summary: Summary = {
    streak: streakOf(days, today),
    practised_today: days.has(today),
    week: { start, days: weekDays, days_practised: weekDays.filter(Boolean).length, minutes, reviews, new_words: vocab.filter(([, i]) => inWeek(dateOf(i.created_date))).length },
    words: { known: vocab.filter(([, i]) => learned(i)).length, tracked: vocab.length },
    level,
    ...(target ? { target_level: target } : {}),
    ...courseOf(o.village, o.course, level),
  }
  if (o.level === 'summary') return { ...head, ...(base ? { base } : {}), summary }

  const word = (i: Record<string, any>) => cleanText(i.content, 60)
  const ms = (i: Record<string, any>) => meaningsOf(i, word(i), base, viewer, o.meaning)

  // the words being learned now: the newest cards, with how they stand
  const learning: ProgressWord[] = vocab
    .filter(([, i]) => word(i))
    .sort(([a, x], [b, y]) => (dateOf(y.created_date) ?? '').localeCompare(dateOf(x.created_date) ?? '') || a.localeCompare(b))
    .slice(0, LEARNING_MAX)
    .map(([id, i]) => ({ id, word: word(i), meanings: ms(i), ...(dateOf(i.created_date) ? { added: dateOf(i.created_date) } : {}), state: rusty(i) ? 'rusty' : learned(i) ? 'known' : 'learning' }))

  // what is hard: the error patterns with mistakes lately (their name only), each with the cards its mistakes name (the
  // card's own word: the example's text, what was typed or corrected, never leaves; it is only searched for the card's id)
  const since = plusDays(today, -RECENT_DAYS)
  const byId = new Map(cards.map(([id, i]) => [id, i]))
  const named = new Set<string>()
  const patterns: HardPattern[] = Object.entries(obj(obj(dbs.mistakes_db).error_patterns))
    .map(([id, p]) => ({ id, p: obj(p), last: dateOf(obj(p).last_seen) ?? dateOf(obj(p).last_occurred) }))
    .filter(x => x.last && x.last >= since && x.last <= today && token(x.id))
    .sort((a, b) => b.last!.localeCompare(a.last!) || int(b.p.frequency) - int(a.p.frequency) || a.id.localeCompare(b.id))
    .slice(0, PATTERNS_MAX)
    .map(({ id, p, last }) => {
      const text = arr(p.examples).map(e => String(obj(e).context ?? '')).join('\n')
      const ids = [...byId.keys()].filter(k => k.length >= 4 && new RegExp(`(^|[^A-Za-z0-9_])${k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}($|[^A-Za-z0-9_])`).test(text))
      const its = ids.slice(0, PATTERN_ITEMS_MAX).flatMap(k => {
        const i = byId.get(k)!
        return word(i) ? [{ id: k, word: word(i), meanings: ms(i) }] : []
      })
      for (const x of its) named.add(x.id)
      return { id, name: cleanText(p.description, 80) || humanize(id), category: token(p.category) ?? '', count: Math.max(0, int(p.frequency)), last, trend: trendOf(p, today), items: its }
    })

  // the cards failed lately: a rusty card, or a review below 3 in the last four weeks
  const words: HardWord[] = vocab
    .flatMap(([id, i]) => {
      if (named.has(id) || !word(i)) return []
      const fails = arr(i.review_history).map(obj).filter(r => num(r.quality) < 3 && (dateOf(r.date) ?? '') >= since)
      if (!fails.length && !rusty(i)) return []
      const last = fails.map(r => dateOf(r.date)!).sort().pop() ?? dateOf(i.last_reviewed)
      return [{ id, word: word(i), meanings: ms(i), misses: Math.max(fails.length, rusty(i) ? 1 : 0), ...(last ? { last } : {}) }]
    })
    .sort((a, b) => (b.last ?? '').localeCompare(a.last ?? '') || b.misses - a.misses || a.id.localeCompare(b.id))
    .slice(0, HARD_WORDS_MAX)

  // what is due: the cards the app reviews (not the error patterns, which modules drill)
  const dueDates = cards.map(([, i]) => dateOf(i.due_date) ?? dateOf(i.next_review)).filter((d): d is string => !!d)
  const due = { today: dueDates.filter(d => d <= today).length, week: dueDates.filter(d => d <= plusDays(today, 6)).length }

  return { ...head, ...(base ? { base } : {}), summary, details: { learning, hard: { patterns, words }, due } }
}

/** The school's units of [level] done of all (not the planned ones), and the one in progress, from the village's state. */
function courseOf(village: Record<string, any> | undefined, course: CourseOutline | undefined, level: string): Pick<Summary, 'course'> {
  const units = course?.levels.find(l => l.level === level)?.units.filter(u => !u.planned) ?? []
  if (!units.length) return {}
  const progress = obj(obj(obj(village).course).units)
  const done = units.filter(u => dateOf(obj(progress[u.id]).done)).length
  const started = units
    .filter(u => dateOf(obj(progress[u.id]).started) && !dateOf(obj(progress[u.id]).done))
    .sort((a, b) => (dateOf(obj(progress[b.id]).started) ?? '').localeCompare(dateOf(obj(progress[a.id]).started) ?? ''))[0]
  const current = started ?? (done ? units.find(u => !dateOf(obj(progress[u.id]).done)) : undefined)
  return { course: { level, done, of: units.length, ...(current ? { current: { id: current.id, title: titleOf(current.title) } } : {}) } }
}

const titleOf = (t: unknown) => {
  const out: Record<string, string> = {}
  for (const [k, v] of Object.entries(obj(t))) if (LANG.test(k) && cleanText(v, 80)) out[k] = cleanText(v, 80)
  return out
}

// --- the asking side ------------------------------------------------------------------------------------------------

const bool = (v: unknown) => v === true
const date = (v: unknown) => dateOf(v)
const meaningsIn = (v: unknown): Meanings => {
  const out: Meanings = {}
  for (const [k, x] of Object.entries(obj(v)).slice(0, 4)) if (LANG.test(k) && cleanText(x, 80)) out[k] = cleanText(x, 80)
  return out
}
const level = (v: unknown): ShareLevel | undefined => (v === 'village' || v === 'summary' || v === 'details' ? v : undefined)
const count = (v: unknown, max = 1_000_000) => Math.max(0, Math.min(max, int(v)))

/**
 * Another town's progress answer as this bridge passes it on to its app: every field checked and cleaned again, at
 * most what its level says (a town that sends more than it shares has the rest dropped), never anything else.
 */
export function cleanProgress(p: any, id: string): Progress | undefined {
  const b = obj(p)
  const t = obj(b.town)
  const lv = level(b.level)
  if (t.id !== id || !lv) return undefined
  const out: Progress = {
    v: count(b.v, 99) || PROGRESS_VERSION,
    town: { id, name: cleanText(t.name) || '?', learner: cleanText(t.learner, 40), culture: typeof t.culture === 'string' && /^[a-z][a-z0-9-]{0,40}$/.test(t.culture) ? t.culture : '', language: typeof t.language === 'string' && LANG.test(t.language) ? t.language : '' },
    level: lv,
    date: date(b.date) ?? '',
    ...(bool(b.child) ? { child: true as const } : {}),
    ...(bool(b.guardian) ? { guardian: true as const } : {}),
    ...(typeof b.base === 'string' && LANG.test(b.base) ? { base: b.base } : {}),
  }
  const ch = arr(b.challenges).slice(0, 30).flatMap((c: any) => {
    const x = obj(c)
    const cid = token(x.id)
    if (!cid || !date(x.at)) return []
    const score = typeof x.score === 'number' && Number.isFinite(x.score) ? { score: Math.max(0, Math.min(10, x.score)) } : {}
    const note = cleanText(x.note, 600)
    return [{ id: cid, at: String(x.at).slice(0, 30), type: ['question', 'translate', 'say'].includes(x.type) ? x.type : 'question', text: cleanText(x.text, 500), answered: bool(x.answered), ...(date(x.answered_at) ? { answered_at: String(x.answered_at).slice(0, 30) } : {}), ...score, ...(note ? { note } : {}) }]
  })
  if (ch.length) out.challenges = ch
  if (lv === 'village') return out
  const s = obj(b.summary)
  const w = obj(s.week)
  const c = obj(s.course)
  const cur = obj(c.current)
  const days = arr(w.days).slice(0, 7).map(bool)
  out.summary = {
    streak: count(s.streak, 100_000),
    practised_today: bool(s.practised_today),
    week: { start: date(w.start) ?? '', days: [...days, ...Array(7 - days.length).fill(false)], days_practised: count(w.days_practised, 7), minutes: count(w.minutes, 7 * 24 * 60), reviews: count(w.reviews), new_words: count(w.new_words) },
    words: { known: count(obj(s.words).known), tracked: count(obj(s.words).tracked) },
    level: LEVELS.includes(s.level) ? s.level : 'A1',
    ...(LEVELS.includes(s.target_level) ? { target_level: s.target_level } : {}),
    ...(LEVELS.includes(c.level)
      ? { course: { level: c.level, done: count(c.done, 200), of: count(c.of, 200), ...(token(cur.id) ? { current: { id: cur.id, title: titleOf(cur.title) } } : {}) } }
      : {}),
  }
  if (lv === 'summary') return out
  const d = obj(b.details)
  const wordOf = (x: any) => ({ id: token(obj(x).id) ?? '', word: cleanText(obj(x).word, 60), meanings: meaningsIn(obj(x).meanings) })
  out.details = {
    learning: arr(d.learning).slice(0, LEARNING_MAX).map(x => ({ ...wordOf(x), ...(date(obj(x).added) ? { added: date(obj(x).added) } : {}), state: (['learning', 'known', 'rusty'].includes(obj(x).state) ? obj(x).state : 'learning') as ProgressWord['state'] })).filter(x => x.id && x.word),
    hard: {
      patterns: arr(obj(d.hard).patterns).slice(0, PATTERNS_MAX).flatMap((x: any) => {
        const pid = token(obj(x).id)
        const name = cleanText(obj(x).name, 80)
        if (!pid || !name) return []
        const tr = obj(x).trend
        return [{
          id: pid, name, category: token(obj(x).category) ?? '', count: count(obj(x).count, 100_000), ...(date(obj(x).last) ? { last: date(obj(x).last) } : {}),
          trend: (tr === 'worse' || tr === 'better' ? tr : 'steady') as HardPattern['trend'],
          items: arr(obj(x).items).slice(0, PATTERN_ITEMS_MAX).map(wordOf).filter(i => i.id && i.word),
        }]
      }),
      words: arr(obj(d.hard).words).slice(0, HARD_WORDS_MAX).map(x => ({ ...wordOf(x), misses: count(obj(x).misses, 1000), ...(date(obj(x).last) ? { last: date(obj(x).last) } : {}) })).filter(x => x.id && x.word),
    },
    due: { today: count(obj(d.due).today), week: count(obj(d.due).week) },
  }
  return out
}

/** The weekly note's facts of a progress answer: the days practised, new words, minutes, the hard patterns' names. */
export function weeklyFacts(p: Progress): { days: number; new_words: number; minutes: number; streak: number; hard: string[] } | undefined {
  if (!p.summary) return undefined
  return {
    days: p.summary.week.days_practised,
    new_words: p.summary.week.new_words,
    minutes: p.summary.week.minutes,
    streak: p.summary.streak,
    hard: (p.details?.hard.patterns ?? []).slice(0, 2).map(x => x.name),
  }
}

/** A line for the tutor and the CLI: what a level shares. */
export const LEVEL_TEXT: Record<ShareLevel, string> = {
  village: 'just the village (its public state: nothing of the learning)',
  summary: "a summary: the streak, this week's days, minutes, reviews and new words, the words known, the level and the school's unit",
  details: 'details: the summary, plus the words being learned now, what is hard (error patterns by name and the cards they name, cards failed lately) and what is due',
}

/**
 * A link's sharing as the app (GET /towns, GET /towns/:id/progress) and the CLI show it, both ways: what this town (a
 * [child]'s or not) shares with that one ([share]; [share_default]: not set, the link's default), whether that town is
 * this child's guardian (and the [floor] the child's app can't go below: the [choices] it offers), what that town said
 * it shares with this one when asked last ([theirs]), and whether a weekly note of theirs is on.
 */
export function sharingRow(l: TownLink, child: boolean) {
  const s = shareOf(l, child)
  return {
    share: s.level,
    ...(s.set ? {} : { share_default: true }),
    ...(s.guardian ? { guardian: true, floor: s.floor } : {}),
    choices: SHARE_LEVELS.filter(x => !s.floor || shareRank(x) >= shareRank(s.floor)),
    ...(l.theirs && isShareLevel(l.theirs.share) ? { theirs: { share: l.theirs.share, at: l.theirs.at, ...(l.theirs.guardian ? { guardian: true } : {}) } } : {}),
    ...(l.weekly ? { weekly: true } : {}),
  }
}
