// Friendship between towns (../friendship.ts; companion/README.md, "Friendship between towns"; plan 2, §3.3). Two sides:
// - between bridges (public routes, every request signed by the asking town's key, linked towns only):
//   GET /town/friendship (this town's counts with the asking one, its trouble now, its last feast, whether it is a
//   child's), POST /town/aid (help against this town's trouble: into the guest book, once per event and town) and
//   POST /town/moves (a move between the two towns offered, said yes to, or declined);
// - for the learner's own app (the app token): GET /towns/friendship (each linked town's friendship as both towns agree
//   it, and what it brings: the market's rates, the friend's trouble, the shared feast, people moving),
//   POST /towns/:id/aid (send help), POST /towns/:id/moves (offer a move) and POST /towns/:id/moves/:move/accept or
//   /decline.
//
// People moving: a move is offered by one town and said yes to by the other; nobody moves on their own. A child's town
// offers moves itself and never says yes to one (403 `child`); it refuses offers from other towns (409 `child`): with a
// child's town, the grown-up on the other side (in the family: the parent) decides. The one who moves is a newcomer of
// the sending town's culture (named from its culture pack, of a family of its village); nobody leaves a village.
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import { cultureDir } from '../cultures'
import type { FeatureFactory } from '../feature'
import {
  AID_LEVEL,
  FEAST_DAYS,
  FRIEND_LEVELS,
  FRIEND_POINTS,
  MAX_MOVERS,
  MOVE_EVERY_DAYS,
  MOVE_LEVEL,
  MOVE_SKILL,
  atLeast,
  cleanMover,
  dayNumber,
  feastOf,
  feastOpenUntil,
  friendsOf,
  pickMover,
  sharedFeast,
  skillIn,
  troubleOf,
  type Friendship,
  type MoveRecord,
  type Report,
  type ReportAnswer,
} from '../friendship'
import { ACT_ID, GuestBook, VisitLog, WriteLimits, limitsFromEnv, type GuestEntry } from '../guests'
import { json, readText, type RouteCtx } from '../http'
import { TOWN_VERSION, Nonces, TownError, Towns, checkSigned, cleanDisplay, cleanText, localDate, selfDisplay, signedJson, townRequest, type TownLink } from '../towns'

const actId = z.string().regex(ACT_ID, 'an id of 8-80 letters, digits, - or _ (a retry sends the same)')
const EVENT_ID = /^[\w:.-]{1,64}$/
const aidIn = z.object({ id: actId, event: z.string().regex(EVENT_ID) })
const moveIn = z.object({ id: actId, dir: z.enum(['in', 'out']) })

const instructions = `
Friendship between towns (companion/README.md, "Friendship between towns"): linked towns grow a friendship by visits,
requests done, gifts, trades, help sent when wolves or a storm hit, and questions answered. At a high level someone of a
friend's town may move into the learner's village (villager_arrived with data.kind "moved", data.culture and
data.language: theirs): a bilingual neighbour who speaks their language with the learner now and then; when you give
them lines, give them lines in their language with the learner's base translation, at the learner's level in it.
`.trim()

export const friendship: FeatureFactory = ({ cfg, bridgeKey, profile, game, culture, events, log }) => {
  const store = new Towns(cfg.appDir, log)
  const nonces = new Nonces()
  const friends = friendsOf(cfg.appDir, bridgeKey)
  const ledger = friends.ledger
  const guests = new GuestBook(cfg.appDir)
  const visitLog = new VisitLog(cfg.appDir)
  const limits = new WriteLimits(limitsFromEnv())
  const self = bridgeKey.fingerprint
  const language = culture.manifest?.language ?? 'sl'
  const display = () => selfDisplay({ dataDir: cfg.dataDir, profile, culture: culture.id, manifest: culture.manifest, towns: store })

  function villageState(): Record<string, any> | undefined {
    try {
      return game.read()?.state as Record<string, any> | undefined
    } catch {
      return undefined
    }
  }

  let people: unknown
  /** The village's culture pack's people (people.json): the names and trades of those who move out. */
  function peopleFile(): any {
    if (people === undefined) {
      const p = join(cultureDir(culture.dir, culture.id), 'people.json')
      try {
        people = existsSync(p) ? JSON.parse(readFileSync(p, 'utf8')) : null
      } catch {
        people = null
      }
    }
    return people
  }

  /** A signed request from a linked town: its check, its body, and the 401 when it isn't one. */
  async function signed(c: RouteCtx, max = 8 * 1024) {
    const body = await readText(c.req, max)
    const r = checkSigned(c.req.headers, { method: c.req.method, path: c.url.pathname + c.url.search, body, self, keyOf: id => store.get(id)?.public_key, nonces })
    let parsed: any = null
    try {
      parsed = body ? JSON.parse(body) : null
    } catch {}
    return { r, parsed, refused: r.ok ? undefined : json({ error: 'unauthorized', code: r.code }, 401) }
  }

  const fromOf = (id: string) => ({ id, ...(store.get(id)?.display ?? cleanDisplay({})) })

  const tooMany = (wait: number) => {
    const res = json({ error: 'too many visits of this kind: try again later', code: 'rate_limited' }, 429)
    res.headers.set('retry-after', String(Math.ceil(wait / 1000)))
    return res
  }

  /** What this town tells town [from]: its counts with it, its trouble now, its last feast, whether it is a child's. */
  const report = (from: string): Report => {
    const state = villageState()
    return { v: TOWN_VERSION, town: self, with: from, date: localDate(), ...ledger.counts(from), event: troubleOf(state), feast: feastOf(state), child: profile.child }
  }

  // --- moves ----------------------------------------------------------------------------------------------------------

  /** A move as the app sees it (this town's view). */
  const moveView = (m: MoveRecord) => ({ key: m.key, id: m.id, dir: m.dir, by: m.by, status: m.status, at: m.at, ...(m.done_at ? { done_at: m.done_at } : {}), ...(m.person ? { person: m.person } : {}) })

  const movesWith = (town: string) => ledger.moves().filter(m => m.town === town)

  /**
   * Why a move [dir] ("in": someone of town [l] moves here; "out": someone of here moves there) can't be offered or
   * taken now; undefined when it can. [otherChild]: the other town is a child's (its report says so).
   */
  function whyNot(l: TownLink, dir: 'in' | 'out', level: number, otherChild: boolean, o: { offering?: boolean; except?: string } = {}): { why: string; until?: string; level?: string } | undefined {
    if (level < MOVE_LEVEL) return { why: 'level' }
    if (otherChild && (o.offering || profile.child)) return { why: 'child' }
    const list = movesWith(l.id)
    if (list.some(m => m.status === 'offered' && m.key !== o.except)) return { why: 'open' }
    if (dir === 'in') {
      const skill = skillIn(cfg.dataDir, l.display?.language || 'sl', language)
      if (!atLeast(skill, MOVE_SKILL)) return { why: 'skill', level: skill }
    }
    const done = list.filter(m => m.status === 'done' && m.dir === dir)
    if (done.length >= MAX_MOVERS) return { why: 'enough' }
    // the day a move was done, in the node's own time as today is (a timestamp's UTC date is yesterday's after midnight)
    const dayOf = (at: string) => (at.length > 10 && !Number.isNaN(Date.parse(at)) ? localDate(Date.parse(at)) : at.slice(0, 10))
    const last = Math.max(...done.map(m => dayNumber(dayOf(m.done_at ?? m.at))).filter(n => !Number.isNaN(n)), -Infinity)
    const until = last + MOVE_EVERY_DAYS
    if (until > dayNumber(localDate())) return { why: 'soon', until: new Date(until * 86_400_000).toISOString().slice(0, 10) }
    return undefined
  }

  /** Who moves out of this town for move [seed] to town [to]. */
  const moverFor = (seed: string) => pickMover({ people: peopleFile(), state: villageState(), seed, culture: culture.id, language, town: { id: self, name: display().name } })

  const moveRefusal = (w: { why: string; until?: string; level?: string }) => {
    const text: Record<string, string> = {
      level: 'people move between close friends only',
      child: "a move with a child's town starts on the child's side, and the grown-up side says yes",
      open: 'a move between these towns is open already',
      skill: `the learner needs ${MOVE_SKILL} in the town's language first`,
      enough: 'enough people moved this way already',
      soon: 'one move a fortnight',
    }
    return json({ error: text[w.why] ?? 'not now', code: w.why, ...(w.until ? { until: w.until } : {}), ...(w.level ? { level: w.level, need: MOVE_SKILL } : {}) }, 409)
  }

  /** A town said something about a move: the app hears of it (a town_guest of kind "move": it syncs, and reloads its friends). */
  const moveNews = (m: MoveRecord) => {
    const who = fromOf(m.town)
    events.emit({ type: 'town_guest', key: `move:${m.key}`, kind: 'move', status: m.status, dir: m.dir, from: who.name, learner: who.learner })
    log(`towns: a move with ${who.name} (${who.learner}): ${m.status}${m.person ? `, ${m.person.name} ${m.dir === 'in' ? 'moves here' : 'moves there'}` : ''}`)
  }

  // --- asking the other town ------------------------------------------------------------------------------------------

  /**
   * A signed write to town [l] ([path], [body]), its answer passed on: the other town's refusals (400, 403, 404, 409,
   * 429 with a `code`) as they are, what goes wrong between the towns as a 502 with a `code`. [ok]: what to keep of a 200.
   */
  async function send(l: TownLink, path: string, body: Record<string, unknown>, ok: (b: any) => Response | Promise<Response>): Promise<Response> {
    let r: { status: number; body: any }
    try {
      r = await townRequest(bridgeKey, l, 'POST', path, { v: TOWN_VERSION, ...body }, { maxBytes: 16 * 1024 })
    } catch (e) {
      return json({ error: (e as Error).message, code: e instanceof TownError ? e.code : 'unreachable' }, 502)
    }
    if (r.status === 401) {
      friends.drop(l.id)
      return json({ error: 'the town no longer knows this one', code: 'refused' }, 502)
    }
    if (r.status === 404 && !r.body?.code) return json({ error: "the town's bridge is older than friendship between towns", code: 'unsupported' }, 502)
    if ([400, 403, 404, 409, 429].includes(r.status)) {
      const code = typeof r.body?.code === 'string' && /^[a-z_]{1,40}$/.test(r.body.code) ? r.body.code : 'refused'
      const until = typeof r.body?.until === 'string' && !Number.isNaN(dayNumber(r.body.until)) ? { until: r.body.until.slice(0, 10) } : {}
      return json({ error: cleanText(r.body?.error, 200) || `the town answered ${r.status}`, code, ...until }, r.status === 403 ? 409 : r.status)
    }
    if (r.status !== 200 || r.body?.ok !== true) return json({ error: `the town answered ${r.status}`, code: 'bad_answer' }, 502)
    return ok(r.body)
  }

  async function appBody<T>(req: Request, schema: z.ZodType<T>): Promise<{ data: T } | { refused: Response }> {
    let parsed: unknown = null
    try {
      parsed = JSON.parse(await readText(req, 4 * 1024))
    } catch {}
    const r = schema.safeParse(parsed)
    return r.success ? { data: r.data } : { refused: json({ error: z.prettifyError(r.error), code: 'bad_request' }, 400) }
  }

  /** One linked town's friendship for the app: the level, what it brings, the friend's trouble, the feasts, the moves. */
  function row(l: TownLink, f: Friendship & ReportAnswer) {
    const shown = l.display ?? cleanDisplay({})
    const r = f.report
    const state = villageState()
    const mine = feastOf(state)
    const theirs = r?.feast ?? null
    const shared = sharedFeast(mine, theirs, f.level)
    const trouble = r?.event ?? null
    const aided = trouble ? visitLog.entries().some(e => e.kind === 'aid' && e.town === l.id && (e.body as any)?.event === trouble.id) : false
    const child = r?.child === true
    const skill = skillIn(cfg.dataDir, shown.language || 'sl', language)
    const can = (dir: 'in' | 'out') => {
      const no = whyNot(l, dir, f.level, child, { offering: true })
      return no ? { ok: false, ...no } : { ok: true }
    }
    return {
      id: l.id,
      name: shown.name,
      learner: shown.learner,
      culture: shown.culture,
      language: shown.language,
      status: f.code ?? (f.stale ? 'stale' : 'ok'),
      level: f.level,
      points: f.points,
      next: f.next,
      mine: f.mine,
      theirs: f.theirs,
      mine_points: f.mine_points,
      theirs_points: f.theirs_points,
      rates: { level: f.level, ...f.rates },
      child,
      event: trouble ? { ...trouble, aided, can: f.level >= AID_LEVEL } : null,
      feast: {
        mine,
        theirs,
        shared: shared ? { key: `feast:${l.id}:${shared.first}:${shared.last}`, ...shared } : null,
        open_until: feastOpenUntil(mine, theirs, f.level),
      },
      moves: { skill: { language: shown.language, level: skill, need: MOVE_SKILL }, in: can('in'), out: can('out'), list: movesWith(l.id).map(moveView) },
    }
  }

  return {
    instructions,
    routes: [
      // --- between bridges ---------------------------------------------------------------------------------------
      {
        // this town's counts with the asking one (and its trouble, its last feast): the asking town agrees the level with them
        method: 'GET',
        path: '/town/friendship',
        access: 'public',
        handle: async c => {
          const { r, refused } = await signed(c)
          if (!r.ok) return refused!
          return signedJson(bridgeKey, r.nonce, report(r.from))
        },
      },
      {
        // a friend sends help against this town's trouble (wolves, a bear, a storm): into the guest book, once per event
        method: 'POST',
        path: '/town/aid',
        access: 'public',
        handle: async c => {
          const { r, parsed, refused } = await signed(c)
          if (!r.ok) return refused!
          if (!parsed || typeof parsed.id !== 'string' || !ACT_ID.test(parsed.id) || typeof parsed.event !== 'string' || !EVENT_ID.test(parsed.event)) {
            return json({ error: 'expected {v, id, event}', code: 'bad_request' }, 400)
          }
          const key = `${r.from}:${parsed.id}`
          const answer = (e: GuestEntry, duplicate: boolean) => ({ ok: true, key: e.key, kind: 'aid', event: e.aid?.event ?? '', ...(duplicate ? { duplicate: true } : {}) })
          const had = guests.get(key)
          if (had) return signedJson(bridgeKey, r.nonce, answer(had, true))
          const trouble = troubleOf(villageState())
          if (!trouble || trouble.id !== parsed.event) return json({ error: 'there is no trouble here now (it is over)', code: 'no_event' }, 409)
          const helped = (all: GuestEntry[]) => all.some(e => e.kind === 'aid' && e.from.id === r.from && e.aid?.event === trouble.id)
          if (helped(guests.entries())) return json({ error: 'that town sent help against this already', code: 'already_helped' }, 409)
          if ((await friends.levelWith(store.get(r.from))) < AID_LEVEL) return json({ error: 'help on an event comes from good neighbours', code: 'too_early' }, 409)
          const wait = limits.take(r.from, 'aid')
          if (wait) return tooMany(wait)
          const entry: GuestEntry = { key, id: parsed.id, kind: 'aid', from: fromOf(r.from), at: new Date().toISOString(), aid: { event: trouble.id, kind: trouble.kind } }
          // checked again under the guest book's lock: two at once don't both count
          const added = guests.add(entry, all => (helped(all) ? 'already_helped' : undefined))
          if ('refused' in added) return json({ error: 'that town sent help against this already', code: added.refused }, 409)
          if (!added.duplicate) {
            ledger.count(r.from, 'got', 'aid')
            events.emit({ type: 'town_guest', key, kind: 'aid', from: entry.from.name, learner: entry.from.learner })
            log(`towns: ${entry.from.name} (${entry.from.learner}) sent help against the ${trouble.kind.toLowerCase()}`)
          }
          return signedJson(bridgeKey, r.nonce, answer(added.entry, added.duplicate))
        },
      },
      {
        // a move between the two towns: offered by the asking town, said yes to (accept) or declined
        method: 'POST',
        path: '/town/moves',
        access: 'public',
        handle: async c => {
          const { r, parsed, refused } = await signed(c)
          if (!r.ok) return refused!
          const id = parsed?.id
          if (!parsed || typeof id !== 'string' || !ACT_ID.test(id) || !['offer', 'accept', 'decline'].includes(parsed.action)) {
            return json({ error: 'expected {v, id, action: offer|accept|decline}', code: 'bad_request' }, 400)
          }
          const answer = (m: MoveRecord, duplicate = false) => signedJson(bridgeKey, r.nonce, { ok: true, key: m.key, status: m.status, ...(m.person ? { person: m.person } : {}), ...(duplicate ? { duplicate: true } : {}) })
          if (parsed.action === 'offer') {
            // the asking town offers: someone of its culture moves here ("in", from here), or someone of here moves there
            const key = `${r.from}:${id}`
            const had = ledger.move(key)
            if (had) return had.status === 'declined' ? json({ error: 'that move was declined', code: 'declined' }, 409) : answer(had, true)
            if (profile.child) return moveRefusal({ why: 'child' })
            const dir = parsed.to_town === self && parsed.from_town === r.from ? 'in' : parsed.from_town === self && parsed.to_town === r.from ? 'out' : undefined
            if (!dir) return json({ error: 'expected {from_town, to_town}: this town and the asking one', code: 'bad_request' }, 400)
            const l = store.get(r.from)!
            const level = await friends.levelWith(l)
            const no = whyNot(l, dir, level, false)
            if (no) return moveRefusal(no)
            const person = dir === 'out' ? moverFor(key) : cleanMover(parsed.person)
            if (!person || (dir === 'in' && person.from.id !== r.from)) return json({ error: 'who moves is not known', code: 'bad_request' }, 400)
            const m: MoveRecord = { key, id, town: r.from, dir, by: 'them', status: 'offered', person, at: new Date().toISOString() }
            ledger.putMove(m)
            moveNews(m)
            return answer(m)
          }
          // accept or decline an offer: one of this town's (it offered) or, declining, one of the asking town's
          const m = [ledger.move(`${self}:${id}`), ledger.move(`${r.from}:${id}`)].find(x => x?.town === r.from)
          if (!m) return json({ error: 'no such move', code: 'unknown_move' }, 404)
          if (parsed.action === 'decline') {
            if (m.status === 'offered') {
              const d = { ...m, status: 'declined' as const }
              ledger.putMove(d)
              moveNews(d)
              return answer(d)
            }
            return answer(m, true)
          }
          if (m.by !== 'me') return json({ error: 'the town that offered a move does not say yes to it', code: 'not_yours' }, 409)
          if (m.status === 'declined') return json({ error: 'that move was declined', code: 'declined' }, 409)
          if (m.status === 'done') return answer(m, true)
          const done = { ...m, status: 'done' as const, done_at: new Date().toISOString() }
          ledger.putMove(done)
          moveNews(done)
          return answer(done)
        },
      },

      // --- the learner's app ---------------------------------------------------------------------------------------
      {
        // every linked town's friendship, as both towns agree it (each is asked for its counts now, 3 s)
        method: 'GET',
        path: '/towns/friendship',
        handle: async () => {
          const rows = await Promise.all(store.links().map(async l => row(l, await friends.with(l))))
          return json({ self: { id: self, child: profile.child, language }, levels: FRIEND_LEVELS, points: FRIEND_POINTS, feast_days: FEAST_DAYS, towns: rows })
        },
      },
      {
        // help against a friend's trouble (wolves, a bear, a storm): passed on, logged, so a retry is answered the same
        method: 'POST',
        path: /^\/towns\/([0-9a-f]{32})\/aid$/,
        handle: async ({ req, params: [id] }) => {
          const l = store.get(id)
          if (!l) return json({ error: 'no such town', code: 'unknown_town' }, 404)
          const b = await appBody(req, aidIn)
          if ('refused' in b) return b.refused
          const key = `${l.id}:aid:${b.data.id}`
          const had = visitLog.get(key)
          if (had?.status === 200) return json({ ...(had.body as object), duplicate: true })
          if ((await friends.levelWith(l)) < AID_LEVEL) return json({ error: 'help on an event comes from good neighbours', code: 'too_early' }, 409)
          return send(l, '/town/aid', { id: b.data.id, event: b.data.event }, body => {
            const answer = { ok: true, key: cleanText(body.key, 160), kind: 'aid', event: b.data.event }
            visitLog.put({ key, kind: 'aid', town: l.id, at: new Date().toISOString(), status: 200, body: answer })
            ledger.count(l.id, 'gave', 'aid') // the first answer this town has: counted once
            return json(body.duplicate === true ? { ...answer, duplicate: true } : answer)
          })
        },
      },
      {
        // offer a move: someone of the town moves here ("in"), or someone of here moves there ("out")
        method: 'POST',
        path: /^\/towns\/([0-9a-f]{32})\/moves$/,
        handle: async ({ req, params: [id] }) => {
          const l = store.get(id)
          if (!l) return json({ error: 'no such town', code: 'unknown_town' }, 404)
          const b = await appBody(req, moveIn)
          if ('refused' in b) return b.refused
          const key = `${self}:${b.data.id}`
          const had = ledger.move(key)
          if (had) return json({ ok: true, move: moveView(had), duplicate: true })
          const f = await friends.with(l)
          const no = whyNot(l, b.data.dir, f.level, f.report?.child === true, { offering: true })
          if (no) return moveRefusal(no)
          const out = b.data.dir === 'out'
          const person = out ? moverFor(key) : undefined
          if (out && !person) return json({ error: "the village's culture pack has no names for someone to move", code: 'no_people' }, 409)
          return send(l, '/town/moves', { id: b.data.id, action: 'offer', from_town: out ? self : l.id, to_town: out ? l.id : self, ...(person ? { person } : {}) }, body => {
            const who = out ? person : cleanMover(body.person)
            if (!who || (!out && who.from.id !== l.id)) return json({ error: "the town's answer names nobody to move", code: 'bad_answer' }, 502)
            const m: MoveRecord = { key, id: b.data.id, town: l.id, dir: b.data.dir, by: 'me', status: 'offered', person: who, at: new Date().toISOString() }
            ledger.putMove(m)
            log(`towns: offered ${l.display?.name ?? l.id} a move: ${who.name} ${out ? 'moves there' : 'moves here'}`)
            return json({ ok: true, move: moveView(m) })
          })
        },
      },
      {
        // say yes to a move the other town offered, or decline one (either town's)
        method: 'POST',
        path: /^\/towns\/([0-9a-f]{32})\/moves\/([\w-]{8,80})\/(accept|decline)$/,
        handle: async ({ params: [id, moveId, action] }) => {
          const l = store.get(id)
          if (!l) return json({ error: 'no such town', code: 'unknown_town' }, 404)
          const m = [ledger.move(`${l.id}:${moveId}`), ledger.move(`${self}:${moveId}`)].find(x => x?.town === l.id)
          if (!m) return json({ error: 'no such move', code: 'unknown_move' }, 404)
          if (action === 'decline') {
            if (m.status !== 'offered') return json({ ok: true, move: moveView(m), duplicate: true })
            const d = { ...m, status: 'declined' as const }
            ledger.putMove(d)
            // the other town hears of it; if it doesn't answer now, its offer stays open there until it asks again
            await send(l, '/town/moves', { id: m.id, action: 'decline' }, () => json({ ok: true }))
            return json({ ok: true, move: moveView(d) })
          }
          if (profile.child) return json({ error: "a child's town doesn't say yes to a move: the grown-up side does", code: 'child' }, 403)
          if (m.by !== 'them') return json({ error: 'the other town says yes to a move this town offered', code: 'not_yours' }, 409)
          if (m.status === 'done') return json({ ok: true, move: moveView(m), duplicate: true })
          if (m.status === 'declined') return json({ error: 'that move was declined', code: 'declined' }, 409)
          const f = await friends.with(l)
          const no = whyNot(l, m.dir, f.level, false, { except: m.key })
          if (no && no.why !== 'child') return moveRefusal(no)
          return send(l, '/town/moves', { id: m.id, action: 'accept' }, body => {
            const person = cleanMover(body.person) ?? m.person
            const done: MoveRecord = { ...m, status: 'done', done_at: new Date().toISOString(), ...(person ? { person } : {}) }
            ledger.putMove(done)
            log(`towns: said yes to a move with ${l.display?.name ?? l.id}: ${done.person?.name} ${done.dir === 'in' ? 'moves here' : 'moves there'}`)
            return json({ ok: true, move: moveView(done) })
          })
        },
      },
    ],
  }
}
