// Friendship between towns (plan 2, §3.3; companion/README.md, "Friendship between towns"). Shared by the towns'
// features: features/towns.ts counts what towns do for each other and gives its market's rates by the level, and
// features/friendship.ts has the level for the app, help sent when an event hits a friend, the shared feast and people
// moving between towns.
//
// How both towns agree on one level, and why neither can raise it alone:
// - Each town counts, per linked town, what it did there ([gave]: the days its learner visited, and its writes the
//   other town took: requests done, gifts, trades, help on an event; a host's answer to a guest's question once M6
//   counts it as kind "answer") and what the other town did here ([got]: the days its learner visited here, the guest
//   book's entries). The counts go up as the acts happen and keep them all (the guest book and the visit log keep only
//   their latest entries).
// - A town tells a linked one its counts (GET /town/friendship, signed both ways). Each direction's acts are the LOWER
//   of the doer's count and the receiver's: an act counts once both towns have it. One town's records, even made up,
//   can't raise the level; they can only lower its own report.
// - The level comes from both directions' points: twice the smaller, plus how far the larger runs ahead, at most
//   [ONE_SIDED]. A friendship one learner keeps up alone stops at level 2; people move (level 3) only when both do
//   their part.
// Both towns compute it from the same four counts, so they show the same level; while an act is on its way, the next
// ask agrees again. Each town grants what the level brings (its market's rates, help taken, a move) from its own
// computation: nothing hangs on the other town's word alone.
import { existsSync, mkdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { CULTURE_ID } from './cultures'
import { GuestBook, VisitLog, cleanTexts, type GuestEntry, type VisitEntry } from './guests'
import { learnerLangs } from './learners'
import { withLock, writeAtomic, type BridgeKey } from './pairing'
import { TOWN_ID, TownError, cleanText, localDate, townRequest, type TownLink } from './towns'
import { PeerCache, levelIn } from './visits'

// --- the level ----------------------------------------------------------------------------------------------------

/** What counts between two towns: a day of a visit, a request done, a gift, a trade, help on an event, an answer (M6). */
export const FRIEND_KINDS = ['visit', 'help', 'gift', 'trade', 'aid', 'answer'] as const
export type FriendKind = (typeof FRIEND_KINDS)[number]
/** How many of each kind of act, by kind (kinds this bridge doesn't know are kept, and count nothing). */
export type Counts = Record<string, number>

/** Points per act. M6: a host's answer to a guest's question counts as "answer" (log it with that kind). */
export const FRIEND_POINTS: Record<FriendKind, number> = { visit: 2, help: 3, gift: 2, trade: 3, aid: 4, answer: 2 }
/** The points each level starts at (the index is the level): acquaintance, neighbours, friends, close friends, twin towns. */
export const FRIEND_LEVELS = [0, 8, 24, 48, 90] as const
/** How far one town's part may run ahead of the other's in the level's points. */
export const ONE_SIDED = 30
/** What a level brings: help on a friend's event from level 1, the shared feast from 2, people moving from 3. */
export const AID_LEVEL = 1
export const FEAST_LEVEL = 2
export const MOVE_LEVEL = 3
/** The level a learner needs in a town's language before someone of that town moves in with them. */
export const MOVE_SKILL = 'A2'
/** Two towns' feasts this many days apart or less make a shared feast. */
export const FEAST_DAYS = 3
/** A feast older than this (days) is no news. */
export const FEAST_RECENT_DAYS = 14
/** At most this many people move from one town into the other (each way), and one a fortnight. */
export const MAX_MOVERS = 3
export const MOVE_EVERY_DAYS = 14

/** The market's rates for a visitor, by the level: the least share of what a trade takes it must give, the haggle's least score, how many of a good it spares. */
export type Rates = { fair_share: number; deal_score: number; max_spare: number }
export const RATES: Rates[] = [
  { fair_share: 0.5, deal_score: 6, max_spare: 3 },
  { fair_share: 0.45, deal_score: 6, max_spare: 3 },
  { fair_share: 0.4, deal_score: 5, max_spare: 4 },
  { fair_share: 0.35, deal_score: 5, max_spare: 4 },
  { fair_share: 0.3, deal_score: 5, max_spare: 5 },
]
/** The most of a good any market spares (the best rate). */
export const BEST_SPARE = Math.max(...RATES.map(r => r.max_spare))

export const ratesFor = (level: number): Rates => RATES[Math.max(0, Math.min(RATES.length - 1, level))]

export const zero = (): Counts => Object.fromEntries(FRIEND_KINDS.map(k => [k, 0]))

/** Counts from another town, checked: short kind names, whole numbers. */
export function cleanCounts(v: unknown): Counts {
  const out = zero()
  const o = v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
  for (const [k, n] of Object.entries(o).slice(0, 30)) {
    if (/^[a-z_]{1,24}$/.test(k) && typeof n === 'number' && Number.isInteger(n) && n >= 0) out[k] = Math.min(n, 1_000_000)
  }
  return out
}

export const pointsOf = (c: Counts) => FRIEND_KINDS.reduce((s, k) => s + FRIEND_POINTS[k] * (c[k] ?? 0), 0)

/** Twice the smaller side, plus how far the larger runs ahead, at most [ONE_SIDED]. */
export const combine = (a: number, b: number) => 2 * Math.min(a, b) + Math.min(Math.abs(a - b), ONE_SIDED)

export const levelOf = (points: number): number => FRIEND_LEVELS.findLastIndex(from => points >= from)

/**
 * What each town did for the other, agreed: [mine] (this town for the other) is the lower of this town's [own.gave] and
 * the other's [report.got]; [theirs] (the other for this one) the lower of [report.gave] and [own.got].
 */
export function agree(own: { gave: Counts; got: Counts }, report: { gave: Counts; got: Counts } | undefined): { mine: Counts; theirs: Counts } {
  const kinds = new Set([...FRIEND_KINDS, ...Object.keys(own.gave), ...Object.keys(own.got)])
  const mine = zero()
  const theirs = zero()
  for (const k of kinds) {
    mine[k] = Math.min(own.gave[k] ?? 0, report?.got[k] ?? 0)
    theirs[k] = Math.min(report?.gave[k] ?? 0, own.got[k] ?? 0)
  }
  return { mine, theirs }
}

export type Friendship = {
  level: number
  points: number
  /** What this town did for the other, and the other for this one, as both agree. */
  mine: Counts
  theirs: Counts
  mine_points: number
  theirs_points: number
  /** The next level and the points it starts at; null at the top. */
  next: { level: number; points: number } | null
  rates: Rates
}

/** The friendship from this town's counts and the other's report (none: nothing is agreed yet, level 0). */
export function friendshipOf(own: { gave: Counts; got: Counts }, report: { gave: Counts; got: Counts } | undefined): Friendship {
  const { mine, theirs } = agree(own, report)
  const a = pointsOf(mine)
  const b = pointsOf(theirs)
  const points = combine(a, b)
  const level = levelOf(points)
  const next = level + 1 < FRIEND_LEVELS.length ? { level: level + 1, points: FRIEND_LEVELS[level + 1] } : null
  return { level, points, mine, theirs, mine_points: a, theirs_points: b, next, rates: ratesFor(level) }
}

// --- what a town tells a friend -------------------------------------------------------------------------------------

/** Trouble in a village a friend can send help against: wolves, a bear, a storm (the village state's event). */
export const TROUBLES = ['WOLVES', 'BEAR', 'STORM'] as const
export type Trouble = { id: string; kind: string; strength: number; until: string }

/** The village's trouble now: its event, when it is one of [TROUBLES] and its deadline hasn't passed. */
export function troubleOf(state: Record<string, any> | undefined, now = Date.now()): Trouble | null {
  const e = state?.event
  if (!e || typeof e !== 'object' || typeof e.id !== 'string' || !/^[\w:.-]{1,64}$/.test(e.id)) return null
  if (!(TROUBLES as readonly string[]).includes(e.kind) || !(typeof e.deadline === 'number' && e.deadline > now)) return null
  return { id: e.id, kind: e.kind, strength: Number.isInteger(e.strength) ? Math.max(1, Math.min(5, e.strength)) : 1, until: new Date(e.deadline).toISOString() }
}

const DAY = /^\d{4}-\d{2}-\d{2}$/
/** A date's day number; NaN when it isn't "YYYY-MM-DD". */
export const dayNumber = (d: unknown) => (typeof d === 'string' && DAY.test(d.slice(0, 10)) ? Date.UTC(+d.slice(0, 4), +d.slice(5, 7) - 1, +d.slice(8, 10)) / 86_400_000 : NaN)
const isoDay = (n: number) => new Date(n * 86_400_000).toISOString().slice(0, 10)

/** The village's last feast: the feast under the linden (lastFeast) or a festival celebrated, whichever is later; null for none. */
export function feastOf(state: Record<string, any> | undefined, today = localDate()): string | null {
  const days = [state?.lastFeast, ...Object.values(state?.festivals && typeof state.festivals === 'object' ? state.festivals : {})]
    .filter(d => !Number.isNaN(dayNumber(d)) && dayNumber(d) <= dayNumber(today))
    .map(d => String(d).slice(0, 10))
    .sort()
  return days.at(-1) ?? null
}

/**
 * A shared feast ("skupna veselica"): both towns held one [FEAST_DAYS] days apart or less, lately, and they are friends
 * enough ([FEAST_LEVEL]). Its [first] and [last] day; null when not.
 */
export function sharedFeast(mine: string | null, theirs: string | null, level: number, today = localDate()): { first: string; last: string } | null {
  const a = dayNumber(mine)
  const b = dayNumber(theirs)
  if (level < FEAST_LEVEL || Number.isNaN(a) || Number.isNaN(b) || Math.abs(a - b) > FEAST_DAYS) return null
  if (Math.max(a, b) < dayNumber(today) - FEAST_RECENT_DAYS) return null
  return { first: isoDay(Math.min(a, b)), last: isoDay(Math.max(a, b)) }
}

/** The last day this town can hold a feast to share the friend's ([theirs], lately); null when there is none to share. */
export function feastOpenUntil(mine: string | null, theirs: string | null, level: number, today = localDate()): string | null {
  const b = dayNumber(theirs)
  const t = dayNumber(today)
  if (level < FEAST_LEVEL || Number.isNaN(b) || b + FEAST_DAYS < t || sharedFeast(mine, theirs, level, today)) return null
  return isoDay(b + FEAST_DAYS)
}

/** What a town tells a linked one (GET /town/friendship): its counts with it, its trouble, its last feast, whether it is a child's. */
export type Report = {
  v: number
  town: string
  with: string
  date: string
  gave: Counts
  got: Counts
  event: Trouble | null
  feast: string | null
  child: boolean
}

/** Another town's report as this bridge takes it: for this town ([self]) from town [from], every field checked. */
export function cleanReport(b: any, from: string, self: string): Report | undefined {
  if (!b || typeof b !== 'object' || b.town !== from || b.with !== self) return undefined
  const e = b.event
  const event = e && typeof e === 'object' && typeof e.id === 'string' && /^[\w:.-]{1,64}$/.test(e.id) && (TROUBLES as readonly string[]).includes(e.kind) && !Number.isNaN(Date.parse(e.until))
    ? { id: e.id, kind: e.kind, strength: Number.isInteger(e.strength) ? Math.max(1, Math.min(5, e.strength)) : 1, until: new Date(Date.parse(e.until)).toISOString() }
    : null
  return {
    v: Number.isInteger(b.v) ? b.v : 1,
    town: from,
    with: self,
    date: typeof b.date === 'string' && DAY.test(b.date) ? b.date : '',
    gave: cleanCounts(b.gave),
    got: cleanCounts(b.got),
    event,
    feast: typeof b.feast === 'string' && !Number.isNaN(dayNumber(b.feast)) ? b.feast.slice(0, 10) : null,
    child: b.child === true,
  }
}

// --- a learner's skill -----------------------------------------------------------------------------------------------

export const LEVELS = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'] as const
export const atLeast = (level: string, need: string) => LEVELS.indexOf(level as (typeof LEVELS)[number]) >= LEVELS.indexOf(need as (typeof LEVELS)[number])

/**
 * The learner's level in [language]: their base language is theirs (C2); the village's and any other language as their
 * profiles have it (visits.ts levelIn: A1 for a language never practised).
 */
export function skillIn(dataDir: string, language: string, villageLanguage: string): string {
  if (language === learnerLangs(dataDir, villageLanguage).base) return 'C2'
  return levelIn(dataDir, language, villageLanguage)
}

// --- people moving between towns --------------------------------------------------------------------------------------

/**
 * Someone moving from one town into another: a newcomer of the sending town's culture (named from its culture pack, of
 * a family of its village when there is one), a resident of the receiving village from then on, who speaks their
 * language there now and then.
 */
export type Mover = {
  id: string
  name: string
  emoji: string
  art: 'woman' | 'man'
  voice: 'female' | 'male'
  /** Their trade, per language (the culture pack's people.json). */
  role: Record<string, string>
  family: string
  culture: string
  language: string
  /** The town they come from. */
  from: { id: string; name: string }
}

/**
 * A move between this town and town [town], from this town's view: [dir] "in" (someone of that town's culture moves
 * here) or "out" (someone of this town's culture moves there); proposed [by] this town ("me") or the other ("them");
 * [status] offered, then done (the other side said yes) or declined. [key]: "<the proposing town's id>:<its id>".
 */
export type MoveRecord = {
  key: string
  id: string
  town: string
  dir: 'in' | 'out'
  by: 'me' | 'them'
  status: 'offered' | 'done' | 'declined'
  person?: Mover
  at: string
  done_at?: string
}

const MOVER_ID = /^m-[a-z0-9-]{1,72}$/

/** A mover from another town, checked and cleaned; undefined when it isn't one. */
export function cleanMover(v: unknown): Mover | undefined {
  const o = v && typeof v === 'object' ? (v as Record<string, any>) : undefined
  if (!o || typeof o.id !== 'string' || !MOVER_ID.test(o.id)) return undefined
  const name = cleanText(o.name)
  const culture = typeof o.culture === 'string' && CULTURE_ID.test(o.culture) ? o.culture : ''
  const language = typeof o.language === 'string' && /^[a-z]{2,3}$/.test(o.language) ? o.language : ''
  const from = o.from && typeof o.from.id === 'string' && TOWN_ID.test(o.from.id) ? { id: o.from.id as string, name: cleanText(o.from.name) } : undefined
  if (!name || !culture || !language || !from) return undefined
  return {
    id: o.id,
    name,
    emoji: cleanText(o.emoji, 16) || '🙂',
    art: o.art === 'man' ? 'man' : 'woman',
    voice: o.voice === 'male' ? 'male' : 'female',
    role: cleanTexts(o.role, 60),
    family: cleanText(o.family, 40),
    culture,
    language,
    from,
  }
}

/** A number from a text, the same every time. */
function hash(s: string): number {
  let h = 2166136261
  for (let i = 0; i < s.length; i++) h = Math.imul(h ^ s.charCodeAt(i), 16777619)
  return h >>> 0
}

const slug = (s: string) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 50) || 'x'

/**
 * Who moves out of this town ([culture], its [people]: the culture pack's people.json), for move [seed]: a first name and
 * a trade of the culture, of a family of the village ([state]'s residents) when there is one, else of the culture's
 * surnames. The same for the same seed. Undefined when the pack has no names.
 */
export function pickMover(o: { people: any; state: Record<string, any> | undefined; seed: string; culture: string; language: string; town: { id: string; name: string } }): Mover | undefined {
  let h = hash(o.seed)
  const next = (n: number) => {
    h = Math.imul(h ^ (h >>> 15), 2246822507) >>> 0
    h = (h ^ (h >>> 13)) >>> 0
    return n > 0 ? h % n : 0
  }
  const female = next(2) === 0
  const strings = (v: unknown) => (Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string' && x.trim() !== '') : [])
  const firsts = strings(o.people?.first_names?.[female ? 'female' : 'male'])
  const residents: any[] = Array.isArray(o.state?.residents) ? o.state!.residents : []
  const families = [...new Set(residents.map(r => r?.family).filter((f): f is string => typeof f === 'string' && f.trim() !== ''))]
  const surnames = strings(o.people?.surnames)
  const family = families.length ? families[next(families.length)] : surnames[next(surnames.length)]
  if (!firsts.length || !family) return undefined
  const taken = new Set(residents.map(r => r?.name))
  const free = firsts.filter(f => !taken.has(`${f} ${family}`))
  const first = (free.length ? free : firsts)[next((free.length ? free : firsts).length)]
  const trades = (Array.isArray(o.people?.trades) ? o.people.trades : []).filter((t: any) => t && !t.needs)
  const trade = trades.length ? trades[next(trades.length)]?.[female ? 'female' : 'male'] : undefined
  return {
    id: `m-${slug(`${first}-${family}`)}-${(hash(o.seed) % 46656).toString(36)}`,
    name: cleanText(`${first} ${family}`),
    emoji: female ? '👩' : '👨',
    art: female ? 'woman' : 'man',
    voice: female ? 'female' : 'male',
    role: cleanTexts(trade, 60),
    family: cleanText(family, 40),
    culture: o.culture,
    language: o.language,
    from: { id: o.town.id, name: cleanText(o.town.name) },
  }
}

// --- the ledger: counts and moves --------------------------------------------------------------------------------------

type TownCounts = { gave: Counts; got: Counts; visited_on?: string; guest_on?: string }
type LedgerFile = { schema: 'lani.friends/v1'; seeded?: boolean; towns: Record<string, TownCounts>; moves: MoveRecord[] }

/** At most this many moves are kept (the oldest go). */
const KEEP_MOVES = 200

/**
 * <appDir>/town-friends.json: what this town and each linked town did for each other ([counts]: every act once, as it
 * happens; the days of visits both ways), and the moves between them.
 */
export class FriendLedger {
  readonly file: string

  constructor(appDir: string) {
    mkdirSync(appDir, { recursive: true })
    this.file = join(appDir, 'town-friends.json')
  }

  private read(): LedgerFile {
    try {
      const d = existsSync(this.file) ? JSON.parse(readFileSync(this.file, 'utf8')) : undefined
      if (d && typeof d === 'object') {
        return {
          schema: 'lani.friends/v1',
          ...(d.seeded === true ? { seeded: true } : {}),
          towns: d.towns && typeof d.towns === 'object' ? d.towns : {},
          moves: Array.isArray(d.moves) ? d.moves.filter((m: MoveRecord) => m && typeof m.key === 'string') : [],
        }
      }
    } catch {}
    return { schema: 'lani.friends/v1', towns: {}, moves: [] }
  }

  private change(fn: (d: LedgerFile) => void) {
    withLock(`${this.file}.lock`, () => {
      const d = this.read()
      fn(d)
      writeAtomic(this.file, JSON.stringify(d, null, 1) + '\n')
    })
  }

  private town(d: LedgerFile, id: string): TownCounts {
    const t = d.towns[id] ?? { gave: {}, got: {} }
    d.towns[id] = { ...t, gave: t.gave ?? {}, got: t.got ?? {} }
    return d.towns[id]
  }

  get seeded(): boolean {
    return this.read().seeded === true
  }

  /**
   * Counts what the guest book ([guests]: what linked towns brought here) and the visit log ([visits]: what this town
   * did there, answered) hold, once: a town that did things before the counts were kept.
   */
  seed(guests: GuestEntry[], visits: VisitEntry[]) {
    this.change(d => {
      if (d.seeded) return
      for (const e of guests) if (e?.from?.id && TOWN_ID.test(e.from.id)) { const t = this.town(d, e.from.id); t.got[e.kind] = (t.got[e.kind] ?? 0) + 1 }
      for (const e of visits) if (e?.status === 200 && TOWN_ID.test(e.town)) { const t = this.town(d, e.town); t.gave[e.kind] = (t.gave[e.kind] ?? 0) + 1 }
      d.seeded = true
    })
  }

  /** What this town did for town [id] ([gave]) and it for this one ([got]). */
  counts(id: string): { gave: Counts; got: Counts } {
    const t = this.read().towns[id]
    return { gave: { ...zero(), ...(t?.gave ?? {}) }, got: { ...zero(), ...(t?.got ?? {}) } }
  }

  /** An act of [kind] between this town and town [id]: this town did it there ([side] "gave") or town [id] here ("got"). */
  count(id: string, side: 'gave' | 'got', kind: string) {
    if (!TOWN_ID.test(id) || !/^[a-z_]{1,24}$/.test(kind)) return
    this.change(d => {
      const t = this.town(d, id)
      t[side][kind] = (t[side][kind] ?? 0) + 1
    })
  }

  /** A day of a visit: this town's learner visited town [id] ([side] "gave"), or its learner visited here ("got"); once a day. */
  day(id: string, side: 'gave' | 'got', today = localDate()) {
    if (!TOWN_ID.test(id)) return
    const field = side === 'gave' ? 'visited_on' : 'guest_on'
    if (this.read().towns[id]?.[field] === today) return
    this.change(d => {
      const t = this.town(d, id)
      if (t[field] === today) return
      t[field] = today
      t[side].visit = (t[side].visit ?? 0) + 1
    })
  }

  moves(): MoveRecord[] {
    return this.read().moves
  }

  move(key: string): MoveRecord | undefined {
    return this.read().moves.find(m => m.key === key)
  }

  /** Adds move [m], or changes the one with its key. */
  putMove(m: MoveRecord) {
    this.change(d => {
      d.moves = [...d.moves.filter(x => x.key !== m.key), m].slice(-KEEP_MOVES)
    })
  }
}

// --- asking a friend ------------------------------------------------------------------------------------------------------

/** A friend's report, or why it can't be had: `refused` (it no longer knows this town), `unsupported` (an older bridge), `unreachable`, `bad_answer`. */
export type ReportAnswer = { report?: Report; at?: number; stale?: true; code?: string }

/**
 * The friendships of this town ([key]): its own counts ([ledger]) and each friend's report, asked when needed and kept
 * for a while ([freshMs]; when a friend doesn't answer, its last report up to a day old).
 */
export class Friends {
  private cache: PeerCache<Report>

  constructor(
    readonly key: BridgeKey,
    readonly ledger: FriendLedger,
    freshMs = Number(process.env.LANI_TOWN_FRIEND_CACHE_MS ?? 60_000),
  ) {
    this.cache = new PeerCache<Report>(freshMs)
  }

  drop(id: string) {
    this.cache.drop(id)
  }

  /** Town [l]'s report on this town: kept a minute; asked now (3 s); the last one while it doesn't answer. */
  async report(l: TownLink, timeoutMs = 3_000): Promise<ReportAnswer> {
    const fresh = this.cache.fresh(l.id, 'friendship')
    if (fresh) return { report: fresh.body, at: fresh.at }
    try {
      const r = await townRequest(this.key, l, 'GET', '/town/friendship', undefined, { timeoutMs, maxBytes: 16 * 1024 })
      if (r.status === 401) {
        this.cache.drop(l.id)
        return { code: 'refused' }
      }
      if (r.status === 404) return { code: 'unsupported' }
      if (r.status !== 200) return { code: 'bad_answer' }
      const report = cleanReport(r.body, l.id, this.key.fingerprint)
      if (!report) return { code: 'bad_answer' }
      const now = Date.now()
      this.cache.put(l.id, 'friendship', report, now)
      return { report, at: now }
    } catch (e) {
      const code = e instanceof TownError ? e.code : 'unreachable'
      const old = code === 'unreachable' ? this.cache.stale(l.id, 'friendship') : undefined
      return old ? { report: old.body, at: old.at, stale: true } : { code }
    }
  }

  /** The friendship with town [l], as both towns count it (level 0 while its report can't be had), with the report. */
  async with(l: TownLink): Promise<Friendship & ReportAnswer> {
    const a = await this.report(l)
    return { ...friendshipOf(this.ledger.counts(l.id), a.report), ...a }
  }

  /** The level with town [l] (0 for none, or while it can't be agreed). */
  async levelWith(l: TownLink | undefined): Promise<number> {
    return l ? (await this.with(l)).level : 0
  }
}

const shared = new Map<string, Friends>()

/**
 * This bridge's friendships ([appDir]: its data; [key]: its key), one for both features that need them. The first time,
 * what the guest book and the visit log already hold is counted.
 */
export function friendsOf(appDir: string, key: BridgeKey): Friends {
  const k = `${appDir}\u0000${key.fingerprint}`
  let f = shared.get(k)
  if (!f) {
    const ledger = new FriendLedger(appDir)
    if (!ledger.seeded) ledger.seed(new GuestBook(appDir).entries(), new VisitLog(appDir).entries())
    f = new Friends(key, ledger)
    shared.set(k, f)
  }
  return f
}
