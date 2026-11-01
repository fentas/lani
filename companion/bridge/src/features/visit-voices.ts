// Voices on a visit (../visit-voices.ts; companion/README.md, "Voices on a visit"). Two sides:
// - between bridges (public routes, every request signed by the asking town's key, linked towns only): GET /town/voice
//   (a clip this town has of a line, by its text and voices: never one made for the asking) and GET /town/voice/cast
//   (the cast its visits are voiced in: its speakers' voices in its language, the learner's voice by gender, its people);
// - for the learner's own app (the app token), on a visit to linked town :id: POST /towns/:id/voice/say (a line's clip:
//   kept here, else the host's, else voiced here in the host's cast voice, else one in a voice it falls back to; 204 when
//   none can be had; a kept clip is checked against the host now and then: the host's own take of a line voiced here, its
//   new take of one re-voiced there), POST /towns/:id/voice/prepare (the visit's first lines got ahead: kept or the host's,
//   never voiced here), GET /towns/:id/voice/index (the town's clips kept here), GET /towns/:id/voice/profiles (how its
//   people are heard, and the learner's voice there) and GET /towns/voice/file/<file> (a kept clip).
// - the host makes a guest's line in someone's own voice (designed in its own ElevenLabs account, which no other node can
//   speak with) only when its node allows it: LANI_TOWN_VOICE_OWN_CHARS characters a day for all linked towns together
//   (off by default: the host's credits are never spent for a guest without the host's say), never from the reserve kept
//   for its own live lines; the clip is kept in its own store, for its own learner too.
// Through the relay too: the host's routes are /town/… (relay.ts passes them on), the visitor asks with townRequest.
import { join } from 'node:path'
import { readFileSync } from 'node:fs'
import { z } from 'zod'
import { voicedGender } from '../addressee'
import { PERSON_VOICE, PLAYER, personVoice } from '../cast'
import { choiceOf, myVoice } from '../my-voice'
import type { FeatureFactory } from '../feature'
import { normalizeText } from '../family'
import { json, readText, type RouteCtx } from '../http'
import { Nonces, TownError, Towns, checkSigned, signedJson, townRequest, type TownLink } from '../towns'
import { PeerCache } from '../visits'
import {
  CAST_PATH,
  MAX_CAST_ANSWER,
  MAX_CLIP_ANSWER,
  VISIT_VOICE_MB,
  VISIT_VOICE_VERSION,
  VOICE_PATH,
  VisitClips,
  castForVisits,
  castFromPack,
  cleanHostAnswer,
  cleanVisitCast,
  clipSha,
  hostClip,
  isAskedVoice,
  parseVoiceAsk,
  recheckDue,
  visitFileUrl,
  visitVoices as voicesFor,
  voiceInTown,
  visitPlayer,
  type VisitCast,
  type VisitClip,
  type VisitMine,
} from '../visit-voices'
import { ffmpegLeveller, LINE_CHARS, type Leveller } from '../voice'

// what the app asks: a line of the visited town in a voice of its cast ("grandma"), someone's own ("@micka") or "player";
// lang: an explanation in the learner's own language (a hint's, an intro's), which a visit doesn't voice: refused (400)
const sayIn = z.object({
  text: z.string().trim().min(1).max(LINE_CHARS),
  voice: z.string().refine(isAskedVoice, 'a speaker, someone\'s own "@id", or "player"').default('female'),
  lang: z.string().regex(/^[a-z]{2,3}$/).optional(),
})
/** At most this many lines in one POST /towns/:id/voice/prepare (as /voice/prepare takes). */
const PREPARE_MAX = 20
const prepareIn = z.object({ texts: z.array(sayIn.omit({ lang: true })).min(1).max(PREPARE_MAX) })
/** How many lines of a prepare are asked of the host at once. */
const PREPARE_AT_ONCE = 3

/** A line the host had no clip of, or a town whose bridge is older than voices on a visit: not asked again for this long. */
const MISS_MS = Number(process.env.LANI_VISIT_VOICE_MISS_MS ?? 10 * 60_000)
/** How long the host has to answer for a clip or its cast (the app waits up to 8 s for a line, the node may voice it after). */
const ASK_MS = 4_000
/**
 * How long the host waits for a guest's line made in someone's own voice before it answers without it (`making`: the clip
 * comes with a re-check soon). Within [ASK_MS], with room for the answer.
 */
const MAKE_WAIT_MS = Number(process.env.LANI_TOWN_VOICE_OWN_WAIT_MS ?? 2_500)

/**
 * The characters a day the host voices for linked towns' visits in its people's own voices (LANI_TOWN_VOICE_OWN_CHARS; 0 or
 * unset: none, the default). Their ElevenLabs voices are designed in the host's account: only the host can speak with them,
 * and only from its own credits.
 */
export function ownVoiceChars(env: Record<string, string | undefined> = process.env): number {
  const n = Number(env.LANI_TOWN_VOICE_OWN_CHARS)
  return Number.isFinite(n) && n > 0 ? Math.floor(n) : 0
}

/** The visits' own-voice clips in the host's store: their source, which the day's characters are counted by. */
const OWN_SOURCE = 'visit'

const sleep = (ms: number) => new Promise<void>(r => setTimeout(r, ms))

/** At most [max] asks an hour from one town (the host's side): the hour's asks per town, kept in memory. */
class HourlyLimit {
  private asks = new Map<string, number[]>()
  constructor(readonly max: number) {}
  /** 0 when town [from] may ask now (counted), else the ms until it may. */
  take(from: string, now = Date.now()): number {
    const recent = (this.asks.get(from) ?? []).filter(t => now - t < 3_600_000)
    if (recent.length >= this.max) {
      this.asks.set(from, recent)
      return 3_600_000 - (now - recent[0])
    }
    recent.push(now)
    this.asks.set(from, recent)
    return 0
  }
}

export const visitVoices: FeatureFactory = ({ cfg, townKey, culture, voice, voiceEngines, profiles, addressee, log, profile }) => {
  const store = new Towns(cfg.appDir, log)
  const nonces = new Nonces()
  const self = townKey.fingerprint
  const language = culture.manifest?.language ?? 'sl'
  const mb = Number(process.env.LANI_VISIT_VOICE_MB)
  const clips = new VisitClips(join(voice.dir, 'visits'), { maxBytes: (Number.isFinite(mb) && mb > 0 ? mb : VISIT_VOICE_MB) * 1024 * 1024, log })
  /** The hosts' casts: fresh for ten minutes, any of them while a town is away (up to a day). */
  const casts = new PeerCache<VisitCast>(10 * 60_000)
  const limit = new HourlyLimit(Number(process.env.LANI_TOWN_VOICE_PER_HOUR ?? 900))
  /** Lines a host had no clip of, and towns older than this (their routes are a 404): when, so they aren't asked again soon. */
  const misses = new Map<string, number>()
  const running = new Map<string, Promise<Response>>()
  let leveller: Leveller | null | undefined
  /** ffmpeg's level for the clips voiced here, looked for once (a node's; the phone voices none). */
  const level = () => {
    if (leveller === undefined) leveller = ffmpegLeveller() ?? null
    return leveller ?? undefined
  }
  /**
   * Where the visitor's own lines go in a town's voices (my-voice.ts): their voice's gender (the profile's, or the one they
   * chose; the female one's while neither says), its age (a child's for a child) and its place on their own list, so the
   * town's voice of that kind says them, in the town's language; their own voice is their home cast's.
   */
  const mine = (): VisitMine => {
    const a = addressee()
    const m = myVoice({ gender: voicedGender(a), child: profile.child, choice: choiceOf(cfg.dataDir) })
    return m.option ? { gender: m.option.gender, age: m.option.age, pick: m.option.pick } : { gender: voicedGender(a), age: m.age }
  }
  const missed = (k: string, now = Date.now()) => {
    const at = misses.get(k)
    if (at === undefined) return false
    if (now - at < MISS_MS) return true
    misses.delete(k)
    return false
  }
  const miss = (k: string) => {
    if (misses.size > 5_000) for (const [m, at] of misses) if (Date.now() - at >= MISS_MS) misses.delete(m)
    misses.set(k, Date.now())
  }

  /** A signed request from a linked town ([RouteCtx]); its refusal (401 with a code) otherwise. */
  async function signed(c: RouteCtx) {
    const body = await readText(c.req, 1024)
    const r = checkSigned(c.req.headers, { method: c.req.method, path: c.url.pathname + c.url.search, body, self, keyOf: id => store.get(id)?.public_key, nonces })
    return r.ok ? { r } : { refused: json({ error: 'unauthorized', code: r.code }, 401) }
  }

  /**
   * Town [l]'s cast for a visit: kept for ten minutes; asked for (signed), checked; while it doesn't answer, the last one
   * (up to a day old); for a town older than this, its culture pack's cast as this node's copy of the pack has it.
   */
  async function castOf(l: TownLink): Promise<VisitCast | undefined> {
    const fresh = casts.fresh(l.id, 'cast')
    if (fresh) return fresh.body
    const old = `old:${l.id}:cast`
    if (!missed(old)) {
      try {
        const r = await townRequest(townKey, l, 'GET', CAST_PATH, undefined, { timeoutMs: ASK_MS, maxBytes: MAX_CAST_ANSWER })
        const c = r.status === 200 ? cleanVisitCast(r.body, l.id) : undefined
        if (c) {
          casts.put(l.id, 'cast', c)
          return c
        }
        if (r.status === 404) miss(old)
        if (r.status === 401) casts.drop(l.id)
      } catch (e) {
        if (!(e instanceof TownError) || e.code !== 'unreachable') log(`visit voices: ${l.display?.name ?? l.id}'s cast: ${(e as Error).message}`)
      }
    }
    const stale = casts.stale(l.id, 'cast')
    if (stale) return stale.body
    const lang = l.display?.language
    const cult = l.display?.culture
    const pack = lang && cult ? castFromPack(culture.dir, cult, lang) : undefined
    return pack && lang && cult ? { v: VISIT_VOICE_VERSION, town: l.id, culture: cult, language: lang, ...pack, people: {} } : undefined
  }

  /**
   * What town [l] has of [text] in the first of [voices] (GET /town/voice), checked: its clip; with [o].have (the SHA-256 of
   * the clip kept here), `same` when it is that one; `making` when it makes the line in someone's own voice now. Undefined
   * when it has none, or it can't be had. A line it had none of lately isn't asked again for a while, unless [o].fresh (a
   * re-check).
   */
  async function fromHost(l: TownLink, text: string, voices: string[], m: VisitMine = {}, o: { have?: string; fresh?: boolean } = {}) {
    const q = new URLSearchParams({ v: String(VISIT_VOICE_VERSION), text })
    for (const v of voices) q.append('voice', v)
    if (o.have) q.set('have', o.have)
    // the learner's own line: by their voice's gender, age and place on their list (a host older than them takes the gender)
    if (voices.includes(PLAYER)) {
      if (m.gender) q.set('gender', m.gender)
      if (m.age) q.set('age', m.age)
      if (m.pick !== undefined) q.set('pick', String(m.pick))
    }
    const k = `host:${l.id}:${voices.join(',')}:${voices.includes(PLAYER) ? `${m.gender ?? ''}/${m.age ?? ''}/${m.pick ?? ''}` : ''}:${normalizeText(text)}`
    if ((!o.fresh && missed(k)) || missed(`old:${l.id}:voice`)) return undefined
    try {
      const r = await townRequest(townKey, l, 'GET', `${VOICE_PATH}?${q}`, undefined, { timeoutMs: ASK_MS, maxBytes: MAX_CLIP_ANSWER })
      const a = r.status === 200 || r.status === 404 ? cleanHostAnswer(r.body, l.id, voices) : undefined
      if (r.status === 200) {
        if (a?.clip || a?.same) return a
        log(`visit voices: ${l.display?.name ?? l.id} answered with no clip that could be taken (${voices.join(', ')})`)
      } else if (r.status === 404 && r.body?.code !== 'no_clip') miss(`old:${l.id}:voice`) // its bridge is older than voices on a visit
      else if (r.status === 401) casts.drop(l.id)
      miss(k)
      // none yet, but the host is making it in someone's own voice now: a re-check soon brings it
      if (a?.making) return a
    } catch (e) {
      if (!(e instanceof TownError) || e.code !== 'unreachable') log(`visit voices: ${l.display?.name ?? l.id}: ${(e as Error).message}`)
    }
    return undefined
  }

  /** A kept clip as the app gets it: its URL, the voice it is in, its engine and where it came from (given out now). */
  const served = (c: VisitClip) => {
    clips.touch(c)
    return json({ url: visitFileUrl(c.file), voice: c.voice, engine: c.engine, from: c.from })
  }

  /** The kept clips being checked against their host now (by key): one check at a time each. */
  const rechecking = new Set<string>()

  /**
   * Kept clip [c] of town [l], given out for a line asked in [voices] (best first; [own]: the learner's own line), checked
   * against the host once its time came ([VisitClip.due]), in the background: the host's own clip in a better voice of
   * [voices] than [c]'s (someone's own voice made there since), its own take of a line voiced here, or its new take of one
   * it re-voiced (asked with the SHA-256 of the one kept: `same` costs no audio) takes [c]'s place for the next time; else
   * [c] stays, and isn't checked again for a while ([recheckDue]).
   */
  function recheck(l: TownLink, c: VisitClip, voices: string[], own = false) {
    if (!clips.due(c) || rechecking.has(c.key)) return
    rechecking.add(c.key)
    const at = voices.indexOf(c.voice)
    // the voices better than the kept clip's, and its own (a stand-in: a voice after the first)
    const asked = own ? [PLAYER] : at > 0 ? voices.slice(0, at + 1) : [c.voice]
    const standIn = !own && at !== 0
    void (async () => {
      try {
        const h = await fromHost(l, c.text, asked, own ? mine() : {}, { have: c.from === 'host' ? c.sha : undefined, fresh: true })
        const take = h?.clip
        const sameTake = !!h?.same || (!!take && take.voice === (own ? PLAYER : c.voice) && c.from === 'host' && !!c.sha && clipSha(take.bytes) === c.sha)
        if (take && !sameTake) {
          const as = own ? c.voice : take.voice
          const better = as !== c.voice
          log(`visit voices: ${l.display?.name ?? l.id}: "${c.text.slice(0, 60)}" (${as}): the town's own clip ${better ? `in place of ${c.voice}'s` : c.from === 'node' ? 'in place of the one voiced here' : '(a new take)'}`)
          const kept = clips.put({ town: c.town, language: c.language, voice: as, norm: c.norm, text: c.text, from: 'host', engine: take.engine, bytes: take.bytes, due: recheckDue({ from: 'host', standIn: !own && voices.indexOf(as) > 0, making: h?.making }) })
          // the stand-in stays for its own voice's asks; it is checked again in a while
          if (better) clips.checked(c, recheckDue({ from: c.from, standIn, making: h?.making }))
          return kept
        }
        clips.checked(c, recheckDue({ from: c.from, standIn, making: h?.making }))
      } catch (e) {
        log(`visit voices: a re-check of "${c.text.slice(0, 60)}": ${(e as Error).message}`)
      } finally {
        rechecking.delete(c.key)
      }
    })()
  }

  /**
   * A line of town [l] for the app: [text] asked in [asked], best first: a clip kept here; the host's own (in the voice
   * wanted, or someone's own voice's archetype); voiced here in the host's cast voice; a clip in a voice it falls back to
   * (kept, or the host's; never for the learner's own lines); else 204: the phone speaks it.
   */
  async function say(l: TownLink, line: string, asked: string): Promise<Response> {
    // one line of plain text, as the town's routes take it
    const text = line.replace(/[\u0000-\u001f\u007f]+/g, ' ').replace(/\s+/g, ' ').trim()
    const norm = normalizeText(text)
    if (!norm) return json({ error: 'expected a line with words', code: 'bad_request' }, 400)
    // a clip kept in the voice asked for: at once, without the town's cast (which may have to be asked for first)
    const quick = asked === PLAYER ? undefined : clips.get(l.id, asked, norm)
    if (quick) {
      recheck(l, quick, [asked])
      return served(quick)
    }
    const own = asked === PLAYER
    const my = mine()
    const cast = await castOf(l)
    const lang = cast?.language || l.display?.language || ''
    const v = voicesFor(asked, cast ?? { speakers: {}, player: { female: 'female', male: 'male' }, people: {} }, my)
    // the learner's own line when the town has no voice that may say it (a town older than the learner's voices, whose
    // `player` is a villager's voice too): the phone speaks it
    if (own && !v.wanted.length) return new Response(null, { status: 204 })
    for (const w of v.wanted) {
      const kept = clips.get(l.id, w, norm)
      if (kept) {
        recheck(l, kept, own ? [PLAYER] : v.wanted, own)
        return served(kept)
      }
    }
    const keep = (voiceAs: string, bytes: Uint8Array, from: 'host' | 'node', engine: string, o: { engine_voice?: string; standIn?: boolean; making?: boolean } = {}) => {
      log(`visit voices: ${l.display?.name ?? l.id}: "${text.slice(0, 60)}" (${voiceAs}): ${from === 'host' ? "the town's own clip" : `voiced here (${engine})`}${o.making ? ', the town makes it in their own voice' : ''}`)
      const due = recheckDue({ from, standIn: o.standIn, making: o.making })
      return served(clips.put({ town: l.id, language: lang, voice: voiceAs, norm, text, from, engine, ...(o.engine_voice ? { engine_voice: o.engine_voice } : {}), bytes, due }))
    }
    // the host's own clip; the learner's own line in the voice its cast says it in for them
    const host = await fromHost(l, text, own ? [PLAYER] : v.wanted, my)
    const hostMaking = !!host?.making
    if (host?.clip) return keep(own ? v.wanted[0] : host.clip.voice, host.clip.bytes, 'host', host.clip.engine, { standIn: !own && host.clip.voice !== v.wanted[0], making: hostMaking })
    // voiced here, in the host's cast voice (the speaker's: someone with a voice of their own in their archetype's)
    if (v.spec && v.speaker && lang) {
      const made = await voiceInTown({ text, language: lang, spec: v.spec, engines: voiceEngines, live: () => voice.liveSlot?.() ?? true, level, log })
      if (made) return keep(v.speaker, made.bytes, 'node', made.engine, { engine_voice: made.engine_voice, making: hostMaking })
    }
    if (own) return new Response(null, { status: 204 })
    // a voice it falls back to: kept here, else the host's
    for (const f of v.fallback) {
      const kept = clips.get(l.id, f, norm)
      if (kept) {
        recheck(l, kept, [...v.wanted, ...v.fallback].slice(0, 4))
        return served(kept)
      }
    }
    const other = v.fallback.length ? await fromHost(l, text, v.fallback) : undefined
    if (other?.clip) return keep(other.clip.voice, other.clip.bytes, 'host', other.clip.engine, { standIn: true, making: hostMaking })
    return new Response(null, { status: 204 })
  }

  /**
   * The clips of [asks] (a visit's first lines: its people's greetings, the first lines of its dialogs for guests) got here
   * ahead, for the app to fetch before they are said: each one kept here, else the host's own; never voiced here (that is
   * for a line said: no credits spent on a line maybe never heard), never a voice it falls back to. In their order: the
   * clip's URL and voice, or null.
   */
  async function prepare(l: TownLink, asks: { text: string; voice: string }[]): Promise<({ url: string; voice: string; from: string } | null)[]> {
    const my = mine()
    const cast = await castOf(l)
    const lang = cast?.language || l.display?.language || ''
    const out: ({ url: string; voice: string; from: string } | null)[] = asks.map(() => null)
    const one = async (i: number) => {
      const text = asks[i].text.replace(/[\u0000-\u001f\u007f]+/g, ' ').replace(/\s+/g, ' ').trim()
      const norm = normalizeText(text)
      if (!norm) return
      const asked = asks[i].voice
      const own = asked === PLAYER
      const v = voicesFor(asked, cast ?? { speakers: {}, player: { female: 'female', male: 'male' }, people: {} }, my)
      if (own && !v.wanted.length) return
      const kept = (own ? [] : [asked]).concat(v.wanted).map(w => clips.get(l.id, w, norm)).find(Boolean)
      const answer = (c: VisitClip) => ({ url: visitFileUrl(c.file), voice: c.voice, from: c.from })
      if (kept) {
        out[i] = answer(kept)
        return
      }
      const host = await fromHost(l, text, own ? [PLAYER] : v.wanted, my)
      if (!host?.clip) return
      const as = own ? v.wanted[0] : host.clip.voice
      log(`visit voices: ${l.display?.name ?? l.id}: "${text.slice(0, 60)}" (${as}): the town's own clip, got ahead`)
      out[i] = answer(clips.put({ town: l.id, language: lang, voice: as, norm, text, from: 'host', engine: host.clip.engine, bytes: host.clip.bytes, due: recheckDue({ from: 'host', standIn: !own && as !== v.wanted[0], making: host.making }) }))
    }
    let next = 0
    await Promise.all(Array.from({ length: Math.min(PREPARE_AT_ONCE, asks.length) }, async () => {
      while (next < asks.length) await one(next++)
    }))
    return out
  }

  // --- the host's side: someone's own voice for a guest, when this node allows it ---------------------------------

  /** Lines being made in someone's own voice for a guest now (by voice and line): one take each. */
  const making = new Map<string, Promise<boolean>>()

  /**
   * [text] in [own] (someone's own voice here, "@micka"), made now for a linked town's visit when this node allows it
   * ([ownVoiceChars] a day, the visits' own-voice clips of today counted from the store, never the reserve kept for its own
   * live lines): 'made' (in the store now), 'making' (still on its way after [MAKE_WAIT_MS]: kept when it comes), or 'no'.
   */
  async function makeOwn(text: string, own: string): Promise<'made' | 'making' | 'no'> {
    const cap = ownVoiceChars()
    if (!cap || !PERSON_VOICE.test(own) || !personVoice(own) || !voiceEngines.eleven?.configured || typeof voice.charsSince !== 'function') return 'no'
    const k = `${own}|${normalizeText(text)}`
    let job = making.get(k)
    if (!job) {
      const midnight = new Date()
      midnight.setHours(0, 0, 0, 0)
      const cost = voice.cost(text, own)
      const pending = making.size * LINE_CHARS
      if (voice.charsSince(OWN_SOURCE, midnight.toISOString()) + pending + cost > cap) return 'no'
      job = voice
        .voice(text, own, OWN_SOURCE)
        .then(c => !!c && c.voice === own)
        .catch(() => false)
        .finally(() => making.delete(k))
      making.set(k, job)
      log(`visit voices: a guest's line in ${own}'s own voice, made here: "${text.slice(0, 60)}"`)
    }
    return Promise.race([job.then((ok): 'made' | 'no' => (ok ? 'made' : 'no')), sleep(MAKE_WAIT_MS).then((): 'making' => 'making')])
  }

  return {
    start: () => {
      // the clips of towns no longer linked go; the order they were last given out in is written now and then
      clips.forget(id => !!store.get(id))
      const t = setInterval(() => clips.flush(), 60_000)
      ;(t as { unref?: () => void }).unref?.()
    },
    routes: [
      // --- between bridges ---------------------------------------------------------------------------------------
      {
        // a clip this town has of a line, in the first of the voices asked that has one: only a clip there, never made
        method: 'GET',
        path: VOICE_PATH,
        access: 'public',
        handle: async c => {
          const s = await signed(c)
          if (s.refused) return s.refused
          const ask = parseVoiceAsk(c.url)
          if ('error' in ask) return json({ error: ask.error, code: 'bad_request' }, 400)
          const wait = limit.take(s.r.from)
          if (wait) {
            const res = json({ error: 'too many clips asked for: try again later', code: 'rate_limited' }, 429)
            res.headers.set('retry-after', String(Math.ceil(wait / 1000)))
            return res
          }
          let clip = hostClip(voice, ask)
          // someone's own voice it has no clip of: made in it here, when this node allows it (their voice is this
          // town's own: no other node can speak with it); while it is on its way, the answer says so
          let makingIt = false
          const first = ask.voices[0]
          if (PERSON_VOICE.test(first) && clip?.voice !== first) {
            const made = await makeOwn(ask.text, first)
            if (made === 'made') clip = hostClip(voice, { ...ask, voices: [first] }) ?? clip
            makingIt = made === 'making'
          }
          if (!clip) return signedJson(townKey, s.r.nonce, { error: 'this town has no clip of that line in those voices', code: 'no_clip', town: self, ...(makingIt ? { making: true } : {}) }, 404)
          const head = { v: VISIT_VOICE_VERSION, town: self, language, voice: clip.voice, engine: clip.engine, ...(makingIt ? { making: true } : {}) }
          // the visitor keeps this very clip (a re-check): no audio again
          if (ask.have && clipSha(clip.bytes) === ask.have) return signedJson(townKey, s.r.nonce, { ...head, same: true })
          return signedJson(townKey, s.r.nonce, { ...head, bytes: clip.bytes.length, audio: Buffer.from(clip.bytes).toString('base64') })
        },
      },
      {
        // the cast a visit is voiced in: its speakers in this town's language, the learner's voice by gender, its people
        method: 'GET',
        path: CAST_PATH,
        access: 'public',
        handle: async c => {
          const s = await signed(c)
          if (s.refused) return s.refused
          return signedJson(townKey, s.r.nonce, { v: VISIT_VOICE_VERSION, town: self, culture: culture.id, language, ...castForVisits(language, profiles.views()) })
        },
      },

      // --- the learner's app -----------------------------------------------------------------------------------
      {
        method: 'POST',
        path: /^\/towns\/([0-9a-f]{32})\/voice\/say$/,
        handle: async ({ req, params: [id] }) => {
          const l = store.get(id)
          if (!l) return json({ error: 'no such town', code: 'unknown_town' }, 404)
          let parsed: unknown = null
          try {
            parsed = JSON.parse(await readText(req, 4 * 1024))
          } catch {}
          const b = sayIn.safeParse(parsed)
          if (!b.success) return json({ error: z.prettifyError(b.error), code: 'bad_request' }, 400)
          // a text in another language than the town's: not its line (the app reads it with the phone's voice)
          if (b.data.lang && b.data.lang !== l.display?.language) return json({ error: "a visit voices the town's lines in its language only", code: 'not_a_line' }, 400)
          // one at a time per line: a second ask waits for the first's answer
          const k = `${id}|${b.data.voice}|${normalizeText(b.data.text)}`
          const busy = running.get(k)
          if (busy) return (await busy).clone()
          const job = say(l, b.data.text, b.data.voice).finally(() => running.delete(k))
          running.set(k, job)
          return (await job).clone()
        },
      },
      {
        // a visit's first lines got ahead (kept here, else the host's own; never voiced here): their URLs, as /voice/prepare
        method: 'POST',
        path: /^\/towns\/([0-9a-f]{32})\/voice\/prepare$/,
        handle: async ({ req, params: [id] }) => {
          const l = store.get(id)
          if (!l) return json({ error: 'no such town', code: 'unknown_town' }, 404)
          let parsed: unknown = null
          try {
            parsed = JSON.parse(await readText(req, 32 * 1024))
          } catch {}
          const b = prepareIn.safeParse(parsed)
          if (!b.success) return json({ error: z.prettifyError(b.error), code: 'bad_request' }, 400)
          return json({ clips: await prepare(l, b.data.texts) })
        },
      },
      {
        method: 'GET',
        path: /^\/towns\/([0-9a-f]{32})\/voice\/index$/,
        handle: ({ params: [id] }) => (store.get(id) ? json(clips.index(id)) : json({ error: 'no such town', code: 'unknown_town' }, 404)),
      },
      {
        // how the town's people are heard (their voice, archetype, pitch and pace), and the learner's own voice there
        method: 'GET',
        path: /^\/towns\/([0-9a-f]{32})\/voice\/profiles$/,
        handle: async ({ params: [id] }) => {
          const l = store.get(id)
          if (!l) return json({ error: 'no such town', code: 'unknown_town' }, 404)
          const cast = await castOf(l)
          const my = mine()
          const people = Object.fromEntries(Object.entries(cast?.people ?? {}).map(([p, v]) => [p, { ...v, status: v.speaker.startsWith('@') ? 'own' : 'shared' }]))
          // the learner's voice there: the town's of their voice's kind (null when the town has none that may say them)
          const player = cast ? visitPlayer(cast, my) ?? null : (my.gender ?? 'female')
          return json({ people, player, language: cast?.language ?? l.display?.language ?? null })
        },
      },
      {
        method: 'GET',
        path: /^\/towns\/voice\/file\/([^/]+)$/,
        handle: ({ params: [name] }) => {
          const p = clips.path(name)
          if (!p) return json({ error: 'not found' }, 404)
          const c = clips.byFile(name)
          if (c) clips.touch(c)
          return new Response(new Uint8Array(readFileSync(p)), {
            headers: { 'content-type': 'audio/mpeg', 'x-content-type-options': 'nosniff', 'cache-control': 'private, max-age=31536000, immutable' },
          })
        },
      },
    ],
  }
}
