// Learning progress between linked towns (../progress.ts; companion/README.md, "Progress between towns";
// docs/plans/05-built/progress-sharing.md). The sharer decides, per linked town, what it sees of their learning. Two sides:
// - between bridges (public routes, every request signed by the asking town's key, linked towns only, 401 otherwise):
//   GET /town/progress (this learner's progress at the level they share with the asking town, and how the asking town's
//   own challenges went) and POST /town/challenges (a challenge from the asking town's learner, into the family thread);
// - for the learner's own app (the app token; never the family token, never through the relay): GET /towns/:id/progress
//   (a linked town's, asked by this bridge, checked and cleaned, kept for a while), PUT /towns/:id/sharing (what this
//   learner shares with that town; a parent's weekly note of it) and POST /towns/:id/challenges (a challenge sent there).
// A child's town shares Details with its guardian's town (a parent's: lani-town link, or lani-town guardian) and its app
// can't lower that; the parent can, on the node (lani-town share … --profile <child>). A guardian's weekly note: once a
// week (Sunday from 18:00 by the node's clock), a town_progress event for each linked town the learner asked it for.
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import type { FeatureFactory } from '../feature'
import { CHALLENGE_TYPES, MAX_CHALLENGE_HINT, MAX_CHALLENGE_TEXT } from '../family'
import { ACT_ID } from '../guests'
import { json, readText, type RouteCtx } from '../http'
import { LANGUAGES, learnerLangs } from '../learners'
import { normalizeForm } from '../lexicon'
import { stripArticle } from '../lexicon-meanings'
import { withLock, writeAtomic } from '../pairing'
import { LEVEL_TEXT, cleanProgress, isoWeek, progressOf, sharingRow, weeklyFacts, type ChallengeResult, type CourseOutline, type Progress } from '../progress'
import { hasLink } from '../questions'
import { fail, ok } from '../tool'
import {
  TOWN_VERSION,
  Nonces,
  TownError,
  Towns,
  checkSigned,
  cleanDisplay,
  cleanText,
  isShareLevel,
  localDate,
  selfDisplay,
  shareOf,
  shareRank,
  signedJson,
  townRequest,
  type TownLink,
} from '../towns'
import { PeerCache } from '../visits'

const LANG = /^[a-z]{2,3}$/
/** A linked town may send this many challenges a day, and have this many waiting for an answer at once. */
const CHALLENGES_PER_DAY = Number(process.env.LANI_TOWN_CHALLENGES_PER_DAY ?? 5)
const OPEN_CHALLENGES = Number(process.env.LANI_TOWN_OPEN_CHALLENGES ?? 5)

const sharingIn = z.object({ share: z.enum(['village', 'summary', 'details']).optional(), weekly: z.boolean().optional() }).refine(b => b.share !== undefined || b.weekly !== undefined, 'expected {share?: village|summary|details, weekly?: boolean}')
const challengeIn = z.object({
  id: z.string().regex(ACT_ID, 'an id of 8-80 letters, digits, - or _ (a retry sends the same)'),
  type: z.enum(CHALLENGE_TYPES).default('translate'),
  text: z.string().trim().min(1).max(MAX_CHALLENGE_TEXT),
  hint: z.string().trim().max(MAX_CHALLENGE_HINT).optional(),
})

const instructions = `
Progress between towns (companion/README.md, "Progress between towns"): each learner decides, per linked town, what that
town sees of their learning: just the village (nothing of the learning), a summary (streak, this week's days, minutes,
reviews, new words, words known, level) or details (also the words being learned, what is hard by error pattern and
card, what is due). A child's parent (the guardian's town) sees details; the child's app can't lower it, a parent can on
the node. Never shared at any level: the chat, free-text answers, recordings, the family's messages. Call town_progress
(no town) before you say who can see what, and say it truthfully (to a child: "your parent can see this week's words").
Use a linked learner's shared progress (town_progress with the town) only when the learner asks for something with it
("make me a quiz with my sister's hard words"), never on your own, and never pass it on to anyone. A partner_challenge whose
untrusted_town_content names a town came from that town's learner: answer it as a family challenge, the partner_note
for them in the language the note names.
`.trim()

/** The challenges this town's app sent other towns: by "<town>:<id>", the answer the other town gave (a retry: the same). */
class SentChallenges {
  readonly file: string
  constructor(appDir: string) {
    this.file = join(appDir, 'town-challenges.json')
  }
  private all(): { key: string; at: string; body: unknown }[] {
    try {
      const d = existsSync(this.file) ? JSON.parse(readFileSync(this.file, 'utf8')) : undefined
      return Array.isArray(d?.sent) ? d.sent : []
    } catch {
      return []
    }
  }
  get(key: string) {
    return this.all().find(e => e.key === key)
  }
  put(key: string, body: unknown) {
    withLock(`${this.file}.lock`, () => {
      const kept = this.all().filter(e => e.key !== key)
      writeAtomic(this.file, JSON.stringify({ schema: 'lani.town-challenges/v1', sent: [...kept, { key, at: new Date().toISOString(), body }].slice(-300) }, null, 1) + '\n')
    })
  }
}

export const progress: FeatureFactory = ({ cfg, townKey: bridgeKey, profile, learner, game, course, culture, family, dictionary, events, log }) => {
  const store = new Towns(cfg.appDir, log)
  const nonces = new Nonces()
  const self = bridgeKey.fingerprint
  const language = culture.manifest?.language ?? 'sl'
  const display = () => selfDisplay({ dataDir: cfg.dataDir, profile, culture: culture.id, manifest: culture.manifest, towns: store })
  /** The learner's base: what their cards' meanings are in, and what another town's are asked in. */
  const base = () => learnerLangs(cfg.dataDir, language).base
  /** Other towns' progress: fresh for a minute; the last good one, stale, while a town is away (a day). */
  const cache = new PeerCache<Progress>(Number(process.env.LANI_TOWN_PROGRESS_CACHE_MS ?? 60_000))
  const sent = new SentChallenges(cfg.appDir)
  const taken = new Map<string, number[]>()

  /** The learner's databases (read-db), read at most every 30 seconds: every asking town is answered from the same read. */
  let db: { at: number; state: any } | undefined
  function databases(): any {
    if (db && Date.now() - db.at < 30_000) return db.state
    const state = learner.state()
    db = { at: Date.now(), state }
    return state
  }

  function villageState(): Record<string, any> | undefined {
    try {
      return game.read()?.state as Record<string, any> | undefined
    } catch {
      return undefined
    }
  }

  /** The school's outline, as the summary needs it. */
  function outline(): CourseOutline | undefined {
    try {
      const c = course.outline()
      return c ? { levels: c.levels.map(l => ({ level: l.level, units: l.units.map(u => ({ id: u.id, title: u.title as Record<string, string>, planned: u.planned })) })) } : undefined
    } catch {
      return undefined
    }
  }

  /** A word's meaning in [b] from the village language's dictionary (its first reading in that base), if it has one. */
  function meaning(word: string, b: string): string | undefined {
    if (dictionary.language !== learner.home()) return undefined
    const form = normalizeForm(stripArticle(word, dictionary.language), dictionary.language)
    const r = dictionary.dict.readings(form, b).find(x => !x.fallback && (x.glossLang ?? 'en') === b && x.gloss.length)
    return r ? r.gloss.slice(0, 2).join(', ') : undefined
  }

  /** How town [from]'s own challenges to this learner went (theirs only; never another sender's, never the answer's text). */
  const resultsFor = (from: string): ChallengeResult[] =>
    family
      .challenges()
      .filter(c => c.town?.id === from)
      .slice(-20)
      .reverse()
      .map(c => ({
        id: c.town!.key,
        at: c.created_at,
        type: c.type,
        text: c.text,
        answered: !!c.answer,
        ...(c.answer ? { answered_at: c.answer.at } : {}),
        ...(c.feedback?.score !== undefined ? { score: c.feedback.score } : {}),
        ...(c.feedback?.partner_note ? { note: c.feedback.partner_note } : {}),
      }))

  /** This learner's progress for linked town [l], at the level shared with it; [viewer]: its learner's base. */
  function progressFor(l: TownLink, viewer?: string): Progress {
    const s = shareOf(l, profile.child)
    return progressOf({
      db: s.level === 'village' ? undefined : databases(),
      level: s.level,
      town: { id: self, ...display() },
      child: profile.child,
      guardian: s.guardian,
      base: base(),
      viewer,
      village: villageState(),
      course: outline(),
      meaning,
      challenges: resultsFor(l.id),
    })
  }

  /** A signed request from a linked town: its check, its body, and the 401 when it isn't one. */
  async function signed(c: RouteCtx, max = 8 * 1024) {
    const body = await readText(c.req, max)
    const r = checkSigned(c.req.headers, { method: c.req.method, path: c.url.pathname + c.url.search, body, self, keyOf: id => store.get(id)?.public_key, nonces })
    return { r, body, refused: r.ok ? undefined : json({ error: 'unauthorized', code: r.code }, 401) }
  }

  const sharingOf = (l: TownLink) => sharingRow(l, profile.child)

  /** Keeps what town [id] says it shares with this one, and whether this town is its guardian, when that changed. */
  function noteTheirs(id: string, p: Progress) {
    store.patch(id, l => {
      const was = l.theirs
      if (was && was.share === p.level && !!was.guardian === !!p.guardian && Date.now() - Date.parse(was.at) < 24 * 60 * 60_000) return l
      return { ...l, theirs: { share: p.level, at: new Date().toISOString(), ...(p.guardian ? { guardian: true as const } : {}) } }
    })
  }

  /**
   * Linked town [l]'s progress as it shares it with this town, asked for now (kept for a minute); when it doesn't answer,
   * the last one had (up to a day old), stale. Why not when it can't be had: `unreachable`, `refused` (it no longer knows
   * this town), `unsupported` (its bridge is older than progress between towns), `bad_answer`.
   */
  async function peerProgress(l: TownLink, o: { fresh?: boolean; timeoutMs?: number } = {}): Promise<{ body: Progress; at: number; stale?: true } | { error: string; code: string }> {
    const had = o.fresh ? undefined : cache.fresh(l.id, 'progress')
    if (had) return { body: had.body, at: had.at }
    try {
      const r = await townRequest(bridgeKey, l, 'GET', `/town/progress?base=${base()}`, undefined, { timeoutMs: o.timeoutMs ?? 6_000, maxBytes: 256 * 1024 })
      if (r.status === 401) {
        cache.drop(l.id)
        return { error: 'the town no longer knows this one', code: 'refused' }
      }
      if (r.status === 404) return { error: "the town's bridge is older than progress between towns", code: 'unsupported' }
      if (r.status !== 200) return { error: `the town answered ${r.status}`, code: 'bad_answer' }
      const p = cleanProgress(r.body, l.id)
      if (!p) return { error: "the town's answer names another town", code: 'bad_answer' }
      const now = Date.now()
      cache.put(l.id, 'progress', p, now)
      noteTheirs(l.id, p)
      return { body: p, at: now }
    } catch (e) {
      const code = e instanceof TownError ? e.code : 'unreachable'
      const old = code === 'unreachable' ? cache.stale(l.id, 'progress') : undefined
      if (old) return { body: old.body, at: old.at, stale: true }
      return { error: (e as Error).message, code }
    }
  }

  /** The link [which] names for the tutor: a town id (or its first characters), a town's or its learner's name. */
  function findLink(which: string): TownLink | undefined {
    const w = which.trim().toLowerCase()
    const links = store.links()
    const byId = /^[0-9a-f]{4,32}$/.test(w) ? links.filter(l => l.id.startsWith(w)) : []
    if (byId.length === 1) return byId[0]
    const byName = links.filter(l => [l.display?.name, l.display?.learner].some(n => n?.toLowerCase() === w))
    return byName.length === 1 ? byName[0] : undefined
  }

  const who = (l: TownLink) => `${l.display?.learner || '?'}'s town (${l.display?.name || l.id.slice(0, 8)}, ${LANGUAGES[l.display?.language ?? ''] ?? l.display?.language ?? '?'})`

  /** A town's shared progress as a few lines for the tutor. */
  function progressText(l: TownLink, p: Progress, stale: boolean): string {
    const out = [`${who(l)} shares ${p.level === 'village' ? 'just the village: nothing of the learning' : LEVEL_TEXT[p.level]}${stale ? ' (its last answer: the town is away now)' : ''}.`]
    const s = p.summary
    if (s) out.push(`This week: ${s.week.days_practised} day(s), ${s.week.minutes} min, ${s.week.reviews} review(s), ${s.week.new_words} new word(s); streak ${s.streak}; ${s.words.known} words known; level ${s.level}.`)
    const d = p.details
    if (d) {
      if (d.learning.length) out.push(`Learning now: ${d.learning.map(w => `${w.word}${Object.values(w.meanings)[0] ? ` (${Object.values(w.meanings)[0]})` : ''}`).join(', ')}.`)
      if (d.hard.patterns.length) out.push(`Hard: ${d.hard.patterns.map(x => `${x.name}${x.items.length ? ` (${x.items.map(i => i.word).join(', ')})` : ''}`).join('; ')}.`)
      if (d.hard.words.length) out.push(`Words failed lately: ${d.hard.words.map(w => w.word).join(', ')}.`)
    }
    return out.join('\n')
  }

  // --- the weekly note: a guardian's, once a week ----------------------------------------------------------------------

  const weeklyDay = Number(process.env.LANI_TOWN_WEEKLY_DAY ?? 0) // Sunday
  const weeklyHour = Number(process.env.LANI_TOWN_WEEKLY_HOUR ?? 18)
  let weeklyBusy = false
  /** For each link the learner asked a weekly note of, once in the week's note time: its progress as a town_progress event. */
  async function weeklyNotes(now = new Date()) {
    if (weeklyBusy || now.getDay() !== weeklyDay || now.getHours() < weeklyHour) return
    weeklyBusy = true
    try {
      const week = isoWeek(localDate(now.getTime()))
      for (const l of store.links().filter(x => x.weekly && x.weekly_sent !== week)) {
        const got = await peerProgress(l, { fresh: true })
        if ('error' in got || got.stale) continue // asked again at the next check
        const f = weeklyFacts(got.body)
        store.patch(l.id, x => ({ ...x, weekly_sent: week }))
        if (!f) continue // it shares just the village now: nothing to say
        events.emit({ type: 'town_progress', town: l.id, name: got.body.town.name, learner: got.body.town.learner, week, ...f })
        log(`towns: the weekly note of ${got.body.town.learner}'s progress (${week})`)
      }
    } catch (e) {
      log(`towns: the weekly note: ${(e as Error).message}`)
    } finally {
      weeklyBusy = false
    }
  }

  return {
    instructions,
    start: () => {
      const every = Number(process.env.LANI_TOWN_WEEKLY_CHECK_MS ?? 20 * 60_000)
      setTimeout(() => void weeklyNotes(), Math.min(every, 60_000)).unref?.()
      setInterval(() => void weeklyNotes(), every).unref?.()
    },
    routes: [
      // --- between bridges ---------------------------------------------------------------------------------------------
      {
        // this learner's progress, at the level they share with the asking town; ?base=: its learner's base, for meanings
        method: 'GET',
        path: '/town/progress',
        access: 'public',
        handle: async c => {
          const { r, refused } = await signed(c)
          if (!r.ok) return refused!
          const l = store.get(r.from)!
          const viewer = c.url.searchParams.get('base') ?? undefined
          try {
            return signedJson(bridgeKey, r.nonce, progressFor(l, viewer && LANG.test(viewer) ? viewer : undefined))
          } catch (e) {
            log(`towns: progress for ${l.display?.name ?? l.id}: ${(e as Error).message}`)
            return json({ error: "the learner's data can't be read now", code: 'unavailable' }, 503)
          }
        },
      },
      {
        // a challenge from the asking town's learner: into the family thread, once per its id
        method: 'POST',
        path: '/town/challenges',
        access: 'public',
        handle: async c => {
          const { r, body, refused } = await signed(c)
          if (!r.ok) return refused!
          let parsed: any = null
          try {
            parsed = JSON.parse(body)
          } catch {}
          const b = challengeIn.safeParse(parsed)
          if (!b.success) return json({ error: 'expected {v, id, type: question|translate|say, text, hint?, base?}', code: 'bad_request' }, 400)
          const text = cleanText(b.data.text, MAX_CHALLENGE_TEXT)
          const hint = cleanText(b.data.hint ?? '', MAX_CHALLENGE_HINT)
          if (!text) return json({ error: 'a challenge needs its text', code: 'bad_request' }, 400)
          // a child's town: the child's rules (no links, no addresses)
          if (profile.child && hasLink(`${text} ${hint}`)) return json({ error: "a child's town takes no links or addresses", code: 'not_for_a_child' }, 409)
          const l = store.get(r.from)!
          const key = cleanText(b.data.id, 80)
          const mine = family.challenges().filter(x => x.town?.id === r.from)
          const had = mine.find(x => x.town?.key === key)
          if (!had) {
            if (mine.filter(x => !x.answer).length >= OPEN_CHALLENGES) return json({ error: 'enough of this town\'s challenges are waiting for an answer', code: 'too_many_open' }, 409)
            const now = Date.now()
            const day = (taken.get(r.from) ?? []).filter(t => now - t < 24 * 60 * 60_000)
            if (day.length >= CHALLENGES_PER_DAY) {
              const res = json({ error: 'too many challenges today: try again tomorrow', code: 'rate_limited' }, 429)
              res.headers.set('retry-after', String(Math.ceil(((day[0] ?? now) + 24 * 60 * 60_000 - now) / 1000)))
              return res
            }
            taken.set(r.from, [...day, now])
          }
          const shown = l.display ?? cleanDisplay({})
          const sender = typeof parsed?.base === 'string' && LANG.test(parsed.base) ? parsed.base : undefined
          const { challenge, duplicate } = await family.addFromTown({ type: b.data.type, text, ...(hint ? { hint } : {}), town: { id: r.from, key, name: shown.name, learner: shown.learner, ...(sender ? { base: sender } : {}) } })
          if (!duplicate) log(`towns: a challenge from ${shown.name} (${shown.learner})`)
          return signedJson(bridgeKey, r.nonce, { ok: true, id: key, at: challenge.created_at, ...(duplicate ? { duplicate: true } : {}) })
        },
      },

      // --- the learner's app -------------------------------------------------------------------------------------------
      {
        method: 'GET',
        path: /^\/towns\/([0-9a-f]{32})\/progress$/,
        handle: async ({ params: [id], url }) => {
          const l = store.get(id)
          if (!l) return json({ error: 'no such town', code: 'unknown_town' }, 404)
          const got = await peerProgress(l, { fresh: url.searchParams.get('fresh') === '1' })
          if ('error' in got) return json({ error: got.error, code: got.code, sharing: sharingOf(store.get(id) ?? l) }, 502)
          return json({ ...got.body, fetched_at: new Date(got.at).toISOString(), ...(got.stale ? { stale: true } : {}), sharing: sharingOf(store.get(id) ?? l) })
        },
      },
      {
        // what this learner shares with that town (a child's app: not below a guardian's floor), and a weekly note of theirs
        method: 'PUT',
        path: /^\/towns\/([0-9a-f]{32})\/sharing$/,
        handle: async ({ req, params: [id] }) => {
          const l = store.get(id)
          if (!l) return json({ error: 'no such town', code: 'unknown_town' }, 404)
          let parsed: unknown = null
          try {
            parsed = JSON.parse(await readText(req, 4 * 1024))
          } catch {}
          const b = sharingIn.safeParse(parsed)
          if (!b.success) return json({ error: 'expected {share?: village|summary|details, weekly?: boolean}', code: 'bad_request' }, 400)
          const s = shareOf(l, profile.child)
          if (b.data.share && s.floor && shareRank(b.data.share) < shareRank(s.floor)) {
            return json({ error: "your parent's town sees your progress: a parent can change that on the node", code: 'guardian', floor: s.floor }, 403)
          }
          const next = store.patch(id, x => {
            const { weekly: _w, ...rest } = x
            return { ...rest, ...(b.data.share ? { share: b.data.share } : {}), ...((b.data.weekly ?? x.weekly) ? { weekly: true as const } : {}) }
          })!
          if (b.data.share) {
            log(`towns: shares ${b.data.share} with ${l.display?.name ?? id} now`)
            cache.drop(id)
          }
          return json({ id, ...sharingOf(next) })
        },
      },
      {
        // a challenge to that town's learner (from their progress screen): sent on, its answer kept so a retry is the same
        method: 'POST',
        path: /^\/towns\/([0-9a-f]{32})\/challenges$/,
        handle: async ({ req, params: [id] }) => {
          const l = store.get(id)
          if (!l) return json({ error: 'no such town', code: 'unknown_town' }, 404)
          let parsed: unknown = null
          try {
            parsed = JSON.parse(await readText(req, 8 * 1024))
          } catch {}
          const b = challengeIn.safeParse(parsed)
          if (!b.success) return json({ error: z.prettifyError(b.error), code: 'bad_request' }, 400)
          const key = `${id}:${b.data.id}`
          const had = sent.get(key)
          if (had) return json({ ...(had.body as object), duplicate: true })
          let r: { status: number; body: any }
          try {
            r = await townRequest(bridgeKey, l, 'POST', '/town/challenges', { v: TOWN_VERSION, ...b.data, base: base() }, { maxBytes: 16 * 1024 })
          } catch (e) {
            return json({ error: (e as Error).message, code: e instanceof TownError ? e.code : 'unreachable' }, 502)
          }
          if (r.status === 401) return json({ error: 'the town no longer knows this one', code: 'refused' }, 502)
          if (r.status === 404) return json({ error: "the town's bridge is older than challenges between towns", code: 'unsupported' }, 502)
          if ([400, 409, 429].includes(r.status)) {
            const code = typeof r.body?.code === 'string' && /^[a-z_]{1,40}$/.test(r.body.code) ? r.body.code : 'refused'
            return json({ error: cleanText(r.body?.error, 200) || `the town answered ${r.status}`, code }, r.status)
          }
          if (r.status !== 200 || r.body?.ok !== true) return json({ error: `the town answered ${r.status}`, code: 'bad_answer' }, 502)
          const answer = { ok: true, id: b.data.id, at: typeof r.body.at === 'string' ? r.body.at.slice(0, 30) : new Date().toISOString() }
          sent.put(key, answer)
          cache.drop(id) // its challenges show on the progress screen
          log(`towns: a challenge to ${l.display?.name ?? id}`)
          return json(answer)
        },
      },
    ],
    tools: [
      {
        name: 'town_progress',
        description:
          "Progress between linked towns (companion/README.md, \"Progress between towns\"). Without town: what this learner shares with each linked town, and what each shares with them (say it truthfully when asked who can see what). With town (its learner's or village's name, or its id): that town's progress as it shares it with this learner. Use it only when the learner asks for something with it (a quiz with a friend's hard words); never pass it on.",
        inputSchema: { type: 'object', properties: { town: { type: 'string', description: "a linked town: its learner's or village's name, or its id" } } },
        handle: async (args: unknown) => {
          const a = (args && typeof args === 'object' ? args : {}) as { town?: unknown }
          const links = store.links()
          if (typeof a.town !== 'string' || !a.town.trim()) {
            if (!links.length) return ok('No linked towns: nobody sees anything of the learning.')
            const lines = links.map(l => {
              const s = shareOf(l, profile.child)
              const mine = `you share ${s.level}${s.guardian ? ` (their guardian's town: ${s.floor === 'details' ? 'details, which the child can\'t lower' : `at least ${s.floor}`})` : s.set ? '' : ' (the default)'}`
              const theirs = l.theirs && isShareLevel(l.theirs.share) ? `; they share ${l.theirs.share} with you${l.theirs.guardian ? ' (you are their guardian)' : ''} (as of ${l.theirs.at.slice(0, 10)})` : '; what they share: not asked yet'
              return `- ${who(l)}: ${mine}${theirs}${l.weekly ? '; a weekly note of theirs is on' : ''}`
            })
            return ok([`What each level shares: village = ${LEVEL_TEXT.village}; summary = ${LEVEL_TEXT.summary}; ${LEVEL_TEXT.details}. Never: the chat, free-text answers, recordings, the family's messages.`, ...lines].join('\n'))
          }
          const l = findLink(a.town)
          if (!l) return fail(`No one linked town "${a.town}". Linked: ${links.map(who).join(', ') || 'none'}.`)
          const got = await peerProgress(l)
          if ('error' in got) return fail(`${who(l)}: ${got.error} (${got.code}).`)
          return ok(progressText(l, got.body, !!got.stale))
        },
      },
    ],
  }
}
