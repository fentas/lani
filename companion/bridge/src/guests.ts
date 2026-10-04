// Things to do on a visit (plan 2, §3.2; companion/README.md, "Visiting", "Things to do on a visit"): what a visiting
// town writes to the town it visits, and what each side keeps of it.
// - The host's guest book (<data>/app/town-guests.json): the help, gifts and trades linked towns brought, each once (by
//   the sending town and its id, so a retried write is the same entry). The host's app applies them to its village
//   (GameState.guestbook keeps the keys it applied) and shows them; a gift's message reaches the host's tutor as
//   untrusted content (features/towns.ts).
// - The visitor's log (<data>/app/town-visits.json): what its app did on visits and what the host answered, so the app
//   retrying a write gets the same answer without asking again.
// - What a host offers a visitor: its open requests, each with words of its curated packs to practise it in the host's
//   language, and its market: the goods its chest can spare. And the rules a write is checked against: the request is
//   still open, the goods are there to spare, the trade is fair enough and was agreed, the limits per day and hour.
import { existsSync, mkdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { CULTURE_ID, cultureDir } from './cultures'
import type { LoadedPack } from './packs'
import { withLock, writeAtomic } from './pairing'
import { cleanText, FESTIVAL_GRACE_DAYS, festivalDay, type FestivalRule, type TownDisplay } from './towns'

/** The village's resources as the app's state names them (Res in Model.kt). */
export const RES = ['FOOD', 'WOOD', 'STONE', 'WISDOM'] as const
export type Res = (typeof RES)[number]
export const GOOD_ID = /^[a-z0-9][a-z0-9_-]{0,62}$/
/** An activity's id, made by the visitor's app (a UUID, a role-play's conversation id): a retry sends the same one. */
export const ACT_ID = /^[\w-]{8,80}$/
/** A request's id in the host's village state ("q-2026-09-26-0"). */
const REQUEST_ID = /^[A-Za-z0-9_:-]{1,64}$/
/** A message left with a gift: plain text, one line, at most this long. */
export const MAX_MESSAGE = 200
/** 🤝 a host gets when a visitor does one of its requests (the app's Catalog.HELP_QUEST). */
export const HELP_POINTS = 3
/** The least score (0-10) of a market's negotiation (the tutor's debrief) that makes a deal. */
export const DEAL_SCORE = 6
/** A trade gives at least this share of the value it takes (a good negotiation makes a bargain, never a robbery). */
export const FAIR_SHARE = 0.5
/** At most this many of one good a host's market sells a visitor, and it always keeps one. */
export const MAX_SPARE = 3
/** Mirrored from Catalog.kt (GOOD_PRICES): a good's value by its price class. */
export const GOOD_PRICES: Record<string, number> = { small: 10, plain: 15, good: 20, fine: 25, rich: 30, precious: 35 }
const LANGS = ['sl', 'en', 'it', 'de'] as const
const LANGUAGE = /^[a-z]{2,3}$/

const obj = (v: unknown): Record<string, any> | undefined => (v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, any>) : undefined)
const int = (v: unknown, min: number, max: number) => (typeof v === 'number' && Number.isInteger(v) && v >= min && v <= max ? v : undefined)

/** A text per language ({"it": "…", "en": "…"}) from another town, cleaned: known language codes, plain short texts. */
export function cleanTexts(v: unknown, max = 120): Record<string, string> {
  const out: Record<string, string> = {}
  for (const [k, t] of Object.entries(obj(v) ?? {})) if (LANGUAGE.test(k) && cleanText(t, max)) out[k] = cleanText(t, max)
  return out
}

// --- goods and resources ------------------------------------------------------------------------------------------

/** Goods (a culture pack's good ids, in [culture]'s chest) and resources: what one side of a trade gives. */
export type Bundle = { culture: string; goods: Record<string, number>; res: Partial<Record<Res, number>> }

/**
 * A bundle as sent, checked: good ids, whole numbers in range, at most [maxKinds] goods; [goodsOnly]: no resources.
 * Undefined when anything in it is wrong, or it is empty.
 */
export function cleanBundle(
  b: unknown,
  o: { culture?: string; maxKinds?: number; maxEach?: number; maxRes?: number; goodsOnly?: boolean } = {},
): Bundle | undefined {
  const x = obj(b)
  if (!x) return undefined
  const culture = typeof x.culture === 'string' && CULTURE_ID.test(x.culture) ? x.culture : o.culture
  if (!culture) return undefined
  const goods: Record<string, number> = {}
  for (const [id, n] of Object.entries(obj(x.goods) ?? {})) {
    const k = int(n, 1, o.maxEach ?? 10)
    if (!GOOD_ID.test(id) || k === undefined) return undefined
    goods[id] = k
  }
  if (Object.keys(goods).length > (o.maxKinds ?? 5)) return undefined
  const res: Partial<Record<Res, number>> = {}
  for (const [r, n] of Object.entries(obj(x.res) ?? {})) {
    const k = int(n, 1, o.maxRes ?? 100)
    if (!(RES as readonly string[]).includes(r) || k === undefined) return undefined
    res[r as Res] = k
  }
  if (o.goodsOnly && Object.keys(res).length) return undefined
  if (!Object.keys(goods).length && !Object.keys(res).length) return undefined
  return { culture, goods, res }
}

/**
 * What a culture pack's chest.json says of its goods, the thank-you goods of its people (one each, and the rotation:
 * `thanks_rotation`), and its market (read loosely).
 */
export type ChestInfo = {
  goods: Map<string, { emoji: string; price: string; name: Record<string, string> }>
  thanks: Record<string, string>
  thanksDefault: string
  rotation: Record<string, { goods: string[]; seasonal: Record<string, string> }>
  market?: { emoji: string; name: Record<string, string> }
}

/** chest.json's `thanks_rotation`, read loosely: ids only. */
function rotationOf(v: unknown): ChestInfo['rotation'] {
  const out: ChestInfo['rotation'] = {}
  for (const [who, r] of Object.entries(obj(v) ?? {})) {
    const o = obj(r)
    const goods = Array.isArray(o?.goods) ? o.goods.filter((g: unknown): g is string => typeof g === 'string') : []
    const seasonal = Object.fromEntries(Object.entries(obj(o?.seasonal) ?? {}).filter((e): e is [string, string] => typeof e[1] === 'string'))
    out[who] = { goods, seasonal }
  }
  return out
}

/** A festival's seasonal thank-you comes from this many days before its day to its last grace day (the app's Catalog.THANKS_SEASON_DAYS). */
export const THANKS_SEASON_DAYS = 7

/**
 * The good [giver] (a villager id; none: the pack's default) thanks a visitor with for request [request] done on [today]
 * ("YYYY-MM-DD"), as the app's Chest.thanksFor: in a festival's season ([festivals]: the pack's) the one the pack gives
 * them for it, of two the one nearer its day; else from their rotation: two requests in three their usual thanks good,
 * the third one of the others, the same for the same request. Only goods the chest has; empty when it has none to give.
 */
export function thanksFor(chest: ChestInfo | undefined, giver: string | undefined, request: string, festivals: FestivalRule[], today: string): string {
  if (!chest) return ''
  const base = (giver && chest.thanks[giver]) || chest.thanksDefault || ''
  const r = giver ? chest.rotation[giver] : undefined
  if (!r) return base
  const day = Date.UTC(+today.slice(0, 4), +today.slice(5, 7) - 1, +today.slice(8, 10)) / 86_400_000
  const year = +today.slice(0, 4)
  let best: { good: string; away: number } | undefined
  for (const [fid, good] of Object.entries(r.seasonal)) {
    const f = festivals.find(x => x.id === fid)
    if (!f || !chest.goods.has(good)) continue
    for (const y of [year - 1, year, year + 1]) {
      const away = day - festivalDay(f, y)
      if (away >= -THANKS_SEASON_DAYS && away <= FESTIVAL_GRACE_DAYS && (!best || Math.abs(away) < best.away)) best = { good, away: Math.abs(away) }
    }
  }
  if (best) return best.good
  const others = [...new Set(r.goods)].filter(g => g && g !== base && chest.goods.has(g))
  if (!others.length) return base
  // two requests in three their usual good, the third one of the others
  const k = hash(`${giver}/${request}`) % (3 * others.length)
  return k < 2 * others.length ? base : others[k - 2 * others.length]
}

const chests = new Map<string, ChestInfo | null>()

/** [culture]'s chest (companion/cultures/<id>/chest.json), read once; undefined when this node doesn't have that pack. */
export function chestOf(culturesDir: string, culture: string): ChestInfo | undefined {
  const key = `${culturesDir}\u0000${culture}`
  if (!chests.has(key)) {
    let info: ChestInfo | null = null
    try {
      const p = join(cultureDir(culturesDir, culture), 'chest.json')
      if (CULTURE_ID.test(culture) && existsSync(p)) {
        const d = JSON.parse(readFileSync(p, 'utf8'))
        const goods = new Map<string, { emoji: string; price: string; name: Record<string, string> }>()
        for (const g of Array.isArray(d.goods) ? d.goods : []) {
          if (typeof g?.id === 'string') goods.set(g.id, { emoji: String(g.emoji ?? '🎁'), price: String(g.price ?? 'plain'), name: cleanTexts(g.name) })
        }
        const m = obj(obj(d.sellers)?.market)
        info = {
          goods,
          thanks: obj(d.thanks) ?? {},
          thanksDefault: typeof d.thanks_default === 'string' ? d.thanks_default : [...goods.keys()][0] ?? '',
          rotation: rotationOf(d.thanks_rotation),
          ...(m ? { market: { emoji: String(m.emoji ?? '🏪'), name: cleanTexts(m.name) } } : {}),
        }
      }
    } catch {}
    chests.set(key, info)
  }
  return chests.get(key) ?? undefined
}

/** What a bundle is worth: its goods by their price class (in its culture's chest; plain when unknown), a resource 1 each. */
export function valueOf(b: Bundle, chest: ChestInfo | undefined): number {
  let v = 0
  for (const [id, n] of Object.entries(b.goods)) v += (GOOD_PRICES[chest?.goods.get(id)?.price ?? 'plain'] ?? GOOD_PRICES.plain) * n
  for (const n of Object.values(b.res)) v += n ?? 0
  return v
}

/**
 * The goods a village can spare for a visitor: of each good it has more than one of, all but one, at most [max] (the
 * market's rate for the visitor's town: [MAX_SPARE], more between friends, see friendship.ts), less what trades it
 * hasn't applied yet already promised ([promised]).
 */
export function spareGoods(state: Record<string, any> | undefined, promised: Record<string, number> = {}, max = MAX_SPARE): Record<string, number> {
  const out: Record<string, number> = {}
  for (const [id, n] of Object.entries(obj(obj(state?.chest)?.goods) ?? {})) {
    if (!GOOD_ID.test(id) || typeof n !== 'number') continue
    const spare = Math.min(max, Math.floor(n) - 1 - (promised[id] ?? 0))
    if (spare > 0) out[id] = spare
  }
  return out
}

// --- the host's open requests ---------------------------------------------------------------------------------------

/** A request template of a culture pack (quests.json): who asks, the title and story per language, its topics. */
export type QuestTemplate = { giver: string; emoji: string; skill: string; title: Record<string, string>; story: Record<string, string>; topics: string[] }

const templates = new Map<string, QuestTemplate[]>()

/** [culture]'s request templates (quests.json), read once. */
export function questTemplates(culturesDir: string, culture: string): QuestTemplate[] {
  const key = `${culturesDir}\u0000${culture}`
  if (!templates.has(key)) {
    let list: QuestTemplate[] = []
    try {
      const d = JSON.parse(readFileSync(join(cultureDir(culturesDir, culture), 'quests.json'), 'utf8'))
      list = (Array.isArray(d.requests) ? d.requests : []).flatMap((r: any) =>
        typeof r?.giver === 'string'
          ? [{ giver: r.giver, emoji: String(r.emoji ?? '🙂'), skill: String(r.skill ?? 'food'), title: cleanTexts(r.title), story: cleanTexts(r.story, 300), topics: Array.isArray(r.topics) ? r.topics.filter((t: unknown) => typeof t === 'string') : [] }]
          : [],
      )
    } catch {}
    templates.set(key, list)
  }
  return templates.get(key)!
}

/** A word to practise a request with: in the host's language ([word]), what it means in the others, its emoji. */
export type RequestWord = { id: string; word: string; meaning: Record<string, string>; emoji?: string }

/** A host villager's request a visitor may take (never its learner's cards or categories: those are private). */
export type OpenRequest = {
  id: string
  giver: string
  emoji: string
  skill: Res
  title: Record<string, string>
  story: Record<string, string>
  words: RequestWord[]
}

/** A number from a text, for a shuffle that is the same every time. */
function hash(s: string): number {
  let h = 2166136261
  for (let i = 0; i < s.length; i++) h = Math.imul(h ^ s.charCodeAt(i), 16777619)
  return h >>> 0
}

/**
 * The host's open requests (its village state's quests: its people's, not done, not expired; not the tutor's, whose
 * module is private; not the move of the tent), at most six, with their texts in every language the culture pack has
 * them in (matched by giver and title) and six words of the host's curated packs to practise each with: the giver's
 * packs, else the request's topics', else any. [helped]: the requests the asking town already did.
 */
export function openRequests(o: {
  state: Record<string, any> | undefined
  language: string
  templates: QuestTemplate[]
  packs: LoadedPack[]
  helped?: Set<string>
  now?: number
}): OpenRequest[] {
  const now = o.now ?? Date.now()
  const quests: any[] = Array.isArray(o.state?.quests) ? o.state!.quests : []
  const packs = o.packs.filter(p => p.language === o.language && p.words.length)
  const plain = packs.filter(p => !p.festival)
  return quests
    .filter(q => q && typeof q.id === 'string' && REQUEST_ID.test(q.id) && q.id !== 'tent-move' && q.done !== true && (q.source ?? 'LOCAL') === 'LOCAL' && !(typeof q.expiresAt === 'number' && q.expiresAt > 0 && q.expiresAt < now) && !o.helped?.has(q.id))
    .slice(0, 6)
    .flatMap(q => {
      const giver = cleanText(q.giver)
      if (!giver) return []
      const shown = cleanText(typeof q.title === 'string' ? q.title.split(' · ')[0] : '', 120)
      const t = o.templates.find(t => t.giver === q.giver && t.title[o.language] === shown) ?? o.templates.find(t => t.giver === q.giver)
      const from = [plain.filter(p => p.giver?.name === q.giver), plain.filter(p => t?.topics.includes(p.id)), plain, packs].find(l => l.length) ?? []
      const all = from.flatMap(p =>
        p.words.flatMap(w => {
          const word = w[p.language]
          if (!word) return []
          const meaning: Record<string, string> = {}
          for (const k of LANGS) if (k !== p.language && w[k]) meaning[k] = w[k]!
          return [{ id: w.id, word, meaning, ...(w.emoji ? { emoji: w.emoji } : {}) }]
        }),
      )
      const words = all.map(w => ({ w, k: hash(`${q.id}/${w.id}`) })).sort((a, b) => a.k - b.k).slice(0, 6).map(x => x.w)
      const skill = (RES as readonly string[]).includes(q.skill) ? (q.skill as Res) : 'FOOD'
      return [{
        id: q.id,
        giver,
        emoji: cleanText(q.emoji, 16) || t?.emoji || '🙂',
        skill,
        title: t && Object.keys(t.title).length ? t.title : { [o.language]: shown || giver },
        story: t && Object.keys(t.story).length ? t.story : { [o.language]: cleanText(q.story, 300) },
        words,
      }]
    })
}

/** Another town's requests as this bridge passes them on: every field checked and cleaned. */
export function cleanRequests(list: unknown): OpenRequest[] {
  return (Array.isArray(list) ? list.slice(0, 6) : []).flatMap((r: any) => {
    if (!r || typeof r.id !== 'string' || !REQUEST_ID.test(r.id) || !cleanText(r.giver)) return []
    const words = (Array.isArray(r.words) ? r.words.slice(0, 8) : []).flatMap((w: any) =>
      w && typeof w.id === 'string' && GOOD_ID.test(w.id) && cleanText(w.word, 60)
        ? [{ id: w.id, word: cleanText(w.word, 60), meaning: cleanTexts(w.meaning, 80), ...(cleanText(w.emoji, 16) ? { emoji: cleanText(w.emoji, 16) } : {}) }]
        : [],
    )
    return [{
      id: r.id,
      giver: cleanText(r.giver),
      emoji: cleanText(r.emoji, 16) || '🙂',
      skill: (RES as readonly string[]).includes(r.skill) ? r.skill : 'FOOD',
      title: cleanTexts(r.title),
      story: cleanTexts(r.story, 300),
      words,
    }]
  })
}

/** Another town's market as this bridge passes it on: its goods to spare (ids, 1-[max]: the best rate) and its seller. */
export function cleanWares(w: unknown, max = MAX_SPARE): Record<string, number> {
  const out: Record<string, number> = {}
  for (const [id, n] of Object.entries(obj(w) ?? {}).slice(0, 40)) {
    const k = int(n, 1, max)
    if (GOOD_ID.test(id) && k !== undefined) out[id] = k
  }
  return out
}

// --- the host's guest book ------------------------------------------------------------------------------------------

/** [aid]: help sent when an event hit the town (M5, features/friendship.ts). */
export type GuestKind = 'help' | 'gift' | 'trade' | 'aid'
export const GUEST_KINDS: GuestKind[] = ['help', 'gift', 'trade', 'aid']

/** Who wrote: the linked town's id and how it shows itself. */
export type GuestFrom = { id: string } & TownDisplay

/**
 * What a linked town brought, once ([key]: "<its id>:<its id for it>"). [help]: it did request [request] of [giver]
 * (+[amount] 🤝 for the host; the visitor got the giver's [thanks] good); [gift]: a good of its culture ([good]) and/or
 * a [message] (plain text, untrusted);
 * [trade]: the host [got] goods and resources and [gave] goods (the visitor's culture's and the host's), agreed with
 * [score]; [aid]: the town sent help against the host's [event] (its id and kind: wolves, a bear, a storm).
 */
export type GuestEntry = {
  key: string
  id: string
  kind: GuestKind
  from: GuestFrom
  at: string
  help?: { request: string; giver: string; amount: number; thanks?: string }
  gift?: { good?: { culture: string; id: string }; message?: string }
  trade?: { got: Bundle; gave: Bundle; score: number }
  aid?: { event: string; kind: string }
}

type GuestFile = { schema: 'lani.guests/v1'; entries: GuestEntry[] }

/** At most this many entries are kept (the oldest go). */
const KEEP = 500

/** <appDir>/town-guests.json: the host's guest book. */
export class GuestBook {
  readonly file: string

  constructor(appDir: string) {
    mkdirSync(appDir, { recursive: true })
    this.file = join(appDir, 'town-guests.json')
  }

  entries(): GuestEntry[] {
    try {
      const d = existsSync(this.file) ? JSON.parse(readFileSync(this.file, 'utf8')) : undefined
      return Array.isArray(d?.entries) ? d.entries.filter((e: GuestEntry) => e && typeof e.key === 'string') : []
    } catch {
      return []
    }
  }

  get(key: string): GuestEntry | undefined {
    return this.entries().find(e => e.key === key)
  }

  /**
   * Adds [e]; the entry with its key, when there is one already (a retry), is kept and returned instead. [check] runs
   * under the book's lock with the entries there are (a trade: are the goods still there to spare?) and refuses with a
   * code, so two trades at once can't both take the last of something.
   */
  add(e: GuestEntry, check?: (entries: GuestEntry[]) => string | undefined): { entry: GuestEntry; duplicate: boolean } | { refused: string } {
    return withLock(`${this.file}.lock`, () => {
      const all = this.entries()
      const had = all.find(x => x.key === e.key)
      if (had) return { entry: had, duplicate: true }
      const no = check?.(all)
      if (no) return { refused: no }
      const doc: GuestFile = { schema: 'lani.guests/v1', entries: [...all, e].slice(-KEEP) }
      writeAtomic(this.file, JSON.stringify(doc, null, 1) + '\n')
      return { entry: e, duplicate: false }
    })
  }
}

/** What the trades in [entries] the village hasn't applied yet ([applied]: GameState.guestbook) promised of its goods. */
export function promisedBy(entries: GuestEntry[], applied: Set<string>): Record<string, number> {
  const out: Record<string, number> = {}
  for (const e of entries) {
    if (e.kind !== 'trade' || applied.has(e.key) || !e.trade) continue
    for (const [id, n] of Object.entries(e.trade.gave.goods)) out[id] = (out[id] ?? 0) + n
  }
  return out
}

const worlds = new Map<string, Record<string, Record<string, string>>>()

/** What [culture] calls its resources, per language (world.json): "food" → {"it": "Cibo", "sl": "Hrana", "en": "Food"}. */
export function resourceNames(culturesDir: string, culture: string): Record<string, Record<string, string>> {
  const key = `${culturesDir}\u0000${culture}`
  if (!worlds.has(key)) {
    const out: Record<string, Record<string, string>> = {}
    try {
      const d = JSON.parse(readFileSync(join(cultureDir(culturesDir, culture), 'world.json'), 'utf8'))
      for (const [r, v] of Object.entries(obj(d.resources) ?? {})) out[r] = cleanTexts(obj(v)?.name)
    } catch {}
    worlds.set(key, out)
  }
  return worlds.get(key)!
}

/** The keys of the guest-book entries the village already applied (GameState.guestbook). */
export function appliedKeys(state: Record<string, any> | undefined): Set<string> {
  return new Set((Array.isArray(state?.guestbook) ? state!.guestbook : []).filter((k: unknown) => typeof k === 'string'))
}

// --- the visitor's log ------------------------------------------------------------------------------------------------

/** A write this town's app made on a visit and the host's answer ([status], [body]), by "<town>:<kind>:<id>". */
export type VisitEntry = { key: string; kind: GuestKind; town: string; at: string; status: number; body: unknown }

/** <appDir>/town-visits.json: the visitor's log of its writes to other towns. */
export class VisitLog {
  readonly file: string

  constructor(appDir: string) {
    mkdirSync(appDir, { recursive: true })
    this.file = join(appDir, 'town-visits.json')
  }

  entries(): VisitEntry[] {
    try {
      const d = existsSync(this.file) ? JSON.parse(readFileSync(this.file, 'utf8')) : undefined
      return Array.isArray(d?.entries) ? d.entries : []
    } catch {
      return []
    }
  }

  get(key: string): VisitEntry | undefined {
    return this.entries().find(e => e.key === key)
  }

  put(e: VisitEntry) {
    withLock(`${this.file}.lock`, () => {
      const all = this.entries().filter(x => x.key !== e.key)
      writeAtomic(this.file, JSON.stringify({ schema: 'lani.visits/v1', entries: [...all, e].slice(-KEEP) }, null, 1) + '\n')
    })
  }
}

// --- limits -------------------------------------------------------------------------------------------------------------

/** How many writes of each kind one linked town may make a day, and of all kinds an hour. */
export type Limits = Record<GuestKind, number> & { hour: number }

export function limitsFromEnv(env: Record<string, string | undefined> = process.env): Limits {
  const n = (v: string | undefined, d: number) => (v && Number.isInteger(Number(v)) && Number(v) >= 0 ? Number(v) : d)
  return {
    help: n(env.LANI_TOWN_HELP_PER_DAY, 20),
    gift: n(env.LANI_TOWN_GIFTS_PER_DAY, 10),
    trade: n(env.LANI_TOWN_TRADES_PER_DAY, 10),
    aid: n(env.LANI_TOWN_AID_PER_DAY, 3),
    hour: n(env.LANI_TOWN_WRITES_PER_HOUR, 30),
  }
}

const DAY_MS = 24 * 60 * 60_000
const HOUR_MS = 60 * 60_000

/** The writes each linked town made lately (in memory: a restart forgets them, the limits are about floods). */
export class WriteLimits {
  private seen = new Map<string, number[]>()

  constructor(readonly limits: Limits) {}

  /** Milliseconds until town [from] may write [kind] again; 0 when it may now (and the write is counted). */
  take(from: string, kind: GuestKind, now = Date.now()): number {
    const day = (this.seen.get(`${from}:${kind}`) ?? []).filter(t => now - t < DAY_MS)
    const hour = (this.seen.get(`${from}:*`) ?? []).filter(t => now - t < HOUR_MS)
    const waits = [day.length >= this.limits[kind] ? (day[0] ?? now) + DAY_MS - now : 0, hour.length >= this.limits.hour ? (hour[0] ?? now) + HOUR_MS - now : 0]
    const wait = Math.max(...waits, 0)
    if (wait > 0) return Math.max(1, wait)
    this.seen.set(`${from}:${kind}`, [...day, now])
    this.seen.set(`${from}:*`, [...hour, now])
    return 0
  }
}
