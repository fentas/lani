// A spot's cycle over real days (companion/GAME.md "A spot's cycle"; world.json `spots.<id>.cycle`): which day of which
// stage the charcoal pile is on follows from the date, the content and the cycle's seed (its `seed`, else the spot's id),
// never from the learner's visits, so the bridge (here: for the tutor's get_village and the voice corpus) and the app
// (game/scene/SpotCycles.kt) agree to the day. Mirrors SpotCycles.kt: the same dice (FNV-1a over "a|b|c"), the same
// schedule; the smoke test checks a few days against the app's (test/smoke/cultures.ts, fixture spot-cycles.json).

/** How the app can draw a spot (SpotCycles.ARTS; the smoke test compares). */
export const SPOT_ARTS = ['empty', 'logs', 'stack', 'covered', 'lit', 'smoke', 'cooling', 'opened', 'sacks', 'ash'] as const
/** The smoke's colours, as it goes from damp to done. */
export const SPOT_SMOKES = ['white', 'yellow', 'blue'] as const
/** The weather a talk may be for. */
export const SPOT_SKIES = ['rain', 'wind'] as const
/** The most rounds a year has. */
const MAX_ROUNDS = 24

/** What the schedule reads of a cycle (cultures.ts's spec, loosely). */
export type CycleSpec = {
  seed?: string
  season: { from: string; until: string }
  start?: number[]
  pause?: number[]
  stages: { id: string; days?: number[]; art: string; delivers?: boolean; title?: Record<string, string> }[]
  rest?: { title?: Record<string, string>; art: string }
  winter?: { title?: Record<string, string>; art: string }
  lost?: { title?: Record<string, string>; art: string }
  breakout?: { stages: string[]; chance: number }
  delivery?: { to: string; days?: number }
}

/** What a village keeps of a spot's cycle (the app's GameState.cycles[spot]): rounds lost, saved, deliveries used. */
export type CycleMarks = { lost?: string[]; saved?: string[]; used?: string[] }

/** FNV-1a (32 bits) of [parts] joined by "|", unsigned: SpotCycles.hash. */
export function hash(...parts: (string | number)[]): number {
  let h = 0x811c9dc5
  for (const b of new TextEncoder().encode(parts.join('|'))) h = Math.imul(h ^ b, 16777619) >>> 0
  return h >>> 0
}

export const frac = (...parts: (string | number)[]) => hash(...parts) / 4294967296

/** A whole number from [range] ([least, most], both in) for [parts]: SpotCycles.pick. */
export function pick(range: number[] | undefined, ...parts: (string | number)[]): number {
  const a = range?.[0] ?? 0
  const b = Math.max(a, range?.[1] ?? a)
  return a + Math.min(b - a, Math.floor(frac(...parts) * (b - a + 1)))
}

// --- days as numbers (days since 1970-01-01, UTC): no time zones in a calendar of days ---------------------------------

export const dayOf = (iso: string): number => {
  const [y, m, d] = iso.split('-').map(Number)
  return Math.round(Date.UTC(y, m - 1, d) / 86400000)
}
export const isoOf = (n: number): string => new Date(n * 86400000).toISOString().slice(0, 10)
const yearOf = (n: number) => Number(isoOf(n).slice(0, 4))
const atYear = (md: string, year: number) => dayOf(`${year}-${md}`)

export type Span = { index: number; id: string; from: number; until: number }
export type Round = { key: string; year: number; n: number; spans: Span[]; breakout?: number; delivery?: number }

const cache = new WeakMap<CycleSpec, Map<string, Round[]>>()

/** [c]'s rounds in [year] (SpotCycles.rounds): a stage's length and the pause after a round rolled from the year and its number. */
export function rounds(c: CycleSpec, spot: string, year: number): Round[] {
  let byYear = cache.get(c)
  if (!byYear) cache.set(c, (byYear = new Map()))
  const key = `${spot}/${year}`
  const known = byYear.get(key)
  if (known) return known
  const seed = c.seed ?? spot
  const from = atYear(c.season.from, year)
  const until = atYear(c.season.until, year)
  const out: Round[] = []
  if (c.stages.length && until >= from) {
    let d = from + pick(c.start ?? [0, 0], seed, year, 'start')
    for (let n = 0; n < MAX_ROUNDS; n++) {
      const spans: Span[] = []
      let at = d
      c.stages.forEach((st, i) => {
        const len = Math.max(1, pick(st.days ?? [1, 1], seed, year, n, st.id))
        spans.push({ index: i, id: st.id, from: at, until: at + len - 1 })
        at += len
      })
      if (spans[spans.length - 1].until > until) break
      let breakout: number | undefined
      const b = c.breakout
      if (b && frac(seed, year, n, 'breakout') < b.chance) {
        const days = spans.filter(s => b.stages.includes(s.id)).flatMap(s => Array.from({ length: s.until - s.from + 1 }, (_, k) => s.from + k))
        if (days.length) breakout = days[Math.min(days.length - 1, Math.floor(frac(seed, year, n, 'breakout-day') * days.length))]
      }
      const delivers = spans.find(s => c.stages[s.index].delivers)
      out.push({ key: `${year}-${n}`, year, n, spans, breakout, delivery: delivers?.from })
      d = spans[spans.length - 1].until + 1 + pick(c.pause ?? [3, 7], seed, year, n, 'pause')
    }
  }
  byYear.set(key, out)
  return out
}

/** Where a cycle is on a day (SpotCycles.on, CycleDay): its kind, the stage (or rest, winter, lost), the round, the day of it. */
export type CycleDay = { date: string; kind: 'stage' | 'rest' | 'winter' | 'lost'; id: string; round?: string; day: number; days: number; breakout?: string; delivery?: string }

export function on(c: CycleSpec, spot: string, iso: string, marks: CycleMarks = {}): CycleDay {
  const date = dayOf(iso)
  const year = yearOf(date)
  const all = rounds(c, spot, year)
  for (const r of all) {
    const span = r.spans.find(s => date >= s.from && date <= s.until)
    if (!span) continue
    const extra = { round: r.key, breakout: r.breakout !== undefined ? isoOf(r.breakout) : undefined, delivery: r.delivery !== undefined ? isoOf(r.delivery) : undefined }
    if (marks.lost?.includes(r.key) && r.breakout !== undefined && date >= r.breakout) {
      const last = r.spans[r.spans.length - 1].until
      return { date: iso, kind: 'lost', id: 'lost', day: date - r.breakout, days: last - r.breakout + 1, ...extra }
    }
    return { date: iso, kind: 'stage', id: span.id, day: date - span.from, days: span.until - span.from + 1, ...extra }
  }
  const from = atYear(c.season.from, year)
  const until = atYear(c.season.until, year)
  if (date >= from && date <= until) {
    const before = [...all].reverse().find(r => r.spans[r.spans.length - 1].until < date)
    const after = all.find(r => r.spans[0].from > date)
    const start = before ? before.spans[before.spans.length - 1].until + 1 : from
    const end = after ? after.spans[0].from - 1 : until
    return { date: iso, kind: 'rest', id: 'rest', day: date - start, days: end - start + 1 }
  }
  const start = date < from ? atYear(c.season.until, year - 1) + 1 : until + 1
  const end = date < from ? from - 1 : atYear(c.season.from, year + 1) - 1
  return { date: iso, kind: 'winter', id: 'winter', day: date - start, days: Math.max(1, end - start + 1) }
}

/**
 * The charcoal pile today for the tutor (get_village's `spots`): each spot with a cycle, what its keeper does (the stage's
 * title in the village's language and English), the day of it, the round, a breakout tonight or a round lost, and the
 * next stage; the last delivery and whether it came. [lang]: the village's language.
 */
export function cyclesNow(spots: Record<string, { cycle?: CycleSpec; keeper?: { name: string } } | undefined>, iso: string, marksOf: (spot: string) => CycleMarks, lang: string) {
  const out: Record<string, unknown> = {}
  for (const [spot, s] of Object.entries(spots)) {
    const c = s?.cycle
    if (!c) continue
    const marks = marksOf(spot)
    const d = on(c, spot, iso, marks)
    const stage = d.kind === 'stage' ? c.stages.find(st => st.id === d.id) : d.kind === 'rest' ? c.rest : d.kind === 'winter' ? c.winter : c.lost
    const title = stage?.title
    const say = (t?: Record<string, string>) => (t ? (lang === 'en' ? t.en : `${t[lang] ?? t.en} · ${t.en}`) : undefined)
    // the next stage and when (within the round; else the next round's first)
    const today = dayOf(iso)
    const ahead = [...rounds(c, spot, yearOf(today)), ...rounds(c, spot, yearOf(today) + 1)].flatMap(r => r.spans.map(sp => ({ r, sp }))).find(x => x.sp.from > today)
    const last = [...rounds(c, spot, yearOf(today) - 1), ...rounds(c, spot, yearOf(today))].filter(r => r.delivery !== undefined && r.delivery <= today).pop()
    out[spot] = {
      keeper: s?.keeper?.name,
      now: d.kind,
      stage: d.id,
      doing: say(title),
      day: `${d.day + 1}/${d.days}`,
      round: d.round,
      breakout_tonight: d.kind === 'stage' && d.breakout === iso && !(marks.lost?.includes(d.round!) || marks.saved?.includes(d.round!)) ? true : undefined,
      next: ahead ? { stage: ahead.sp.id, on: isoOf(ahead.sp.from) } : undefined,
      last_delivery: last && c.delivery ? { on: isoOf(last.delivery!), to: c.delivery.to, came: !marks.lost?.includes(last.key) } : undefined,
    }
  }
  return Object.keys(out).length ? out : undefined
}
