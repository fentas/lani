// Voices on a visit (../visit-voices.ts; companion/README.md, "Voices on a visit"). Two sides:
// - between bridges (public routes, every request signed by the asking town's key, linked towns only): GET /town/voice
//   (a clip this town has of a line, by its text and voices: never one made for the asking) and GET /town/voice/cast
//   (the cast its visits are voiced in: its speakers' voices in its language, the learner's voice by gender, its people);
// - for the learner's own app (the app token), on a visit to linked town :id: POST /towns/:id/voice/say (a line's clip:
//   kept here, else the host's, else voiced here in the host's cast voice, else one in a voice it falls back to; 204 when
//   none can be had), GET /towns/:id/voice/index (the town's clips kept here), GET /towns/:id/voice/profiles (how its
//   people are heard, and the learner's voice there) and GET /towns/voice/file/<file> (a kept clip).
// Through the relay too: the host's routes are /town/… (relay.ts passes them on), the visitor asks with townRequest.
import { join } from 'node:path'
import { readFileSync } from 'node:fs'
import { z } from 'zod'
import { voicedGender } from '../addressee'
import { PLAYER, type Gender } from '../cast'
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
  cleanHostClip,
  cleanVisitCast,
  hostClip,
  isAskedVoice,
  parseVoiceAsk,
  visitFileUrl,
  visitVoices as voicesFor,
  voiceInTown,
  type VisitCast,
  type VisitClip,
} from '../visit-voices'
import { ffmpegLeveller, LINE_CHARS, type Leveller } from '../voice'

// what the app asks: a line of the visited town in a voice of its cast ("grandma"), someone's own ("@micka") or "player";
// lang: an explanation in the learner's own language (a hint's, an intro's), which a visit doesn't voice: refused (400)
const sayIn = z.object({
  text: z.string().trim().min(1).max(LINE_CHARS),
  voice: z.string().refine(isAskedVoice, 'a speaker, someone\'s own "@id", or "player"').default('female'),
  lang: z.string().regex(/^[a-z]{2,3}$/).optional(),
})

/** A line the host had no clip of, or a town whose bridge is older than voices on a visit: not asked again for this long. */
const MISS_MS = Number(process.env.LANI_VISIT_VOICE_MISS_MS ?? 10 * 60_000)
/** How long the host has to answer for a clip or its cast (the app waits up to 8 s for a line, the node may voice it after). */
const ASK_MS = 4_000

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

export const visitVoices: FeatureFactory = ({ cfg, townKey, culture, voice, voiceEngines, profiles, addressee, log }) => {
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

  /** The clip of [text] in the first of [voices] that town [l] has (GET /town/voice), checked; undefined when none, or it can't be had. */
  async function fromHost(l: TownLink, text: string, voices: string[], gender?: Gender) {
    const q = new URLSearchParams({ v: String(VISIT_VOICE_VERSION), text })
    for (const v of voices) q.append('voice', v)
    if (gender && voices.includes(PLAYER)) q.set('gender', gender)
    const k = `host:${l.id}:${voices.join(',')}:${gender ?? ''}:${normalizeText(text)}`
    if (missed(k) || missed(`old:${l.id}:voice`)) return undefined
    try {
      const r = await townRequest(townKey, l, 'GET', `${VOICE_PATH}?${q}`, undefined, { timeoutMs: ASK_MS, maxBytes: MAX_CLIP_ANSWER })
      if (r.status === 200) {
        const c = cleanHostClip(r.body, l.id, voices)
        if (c) return c
        log(`visit voices: ${l.display?.name ?? l.id} answered with no clip that could be taken (${voices.join(', ')})`)
      } else if (r.status === 404 && r.body?.code !== 'no_clip') miss(`old:${l.id}:voice`) // its bridge is older than voices on a visit
      else if (r.status === 401) casts.drop(l.id)
      miss(k)
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
    if (quick) return served(quick)
    const gender = voicedGender(addressee())
    const cast = await castOf(l)
    const lang = cast?.language || l.display?.language || ''
    const v = voicesFor(asked, cast ?? { speakers: {}, player: { female: 'female', male: 'male' }, people: {} }, gender)
    for (const w of v.wanted) {
      const kept = clips.get(l.id, w, norm)
      if (kept) return served(kept)
    }
    const keep = (voiceAs: string, bytes: Uint8Array, from: 'host' | 'node', engine: string, engine_voice?: string) => {
      log(`visit voices: ${l.display?.name ?? l.id}: "${text.slice(0, 60)}" (${voiceAs}): ${from === 'host' ? "the town's own clip" : `voiced here (${engine})`}`)
      return served(clips.put({ town: l.id, language: lang, voice: voiceAs, norm, text, from, engine, ...(engine_voice ? { engine_voice } : {}), bytes }))
    }
    // the host's own clip; the learner's own line in the voice its cast says it in for them
    const own = asked === PLAYER
    const host = await fromHost(l, text, own ? [PLAYER] : v.wanted, gender)
    if (host) return keep(own ? v.wanted[0] : host.voice, host.bytes, 'host', host.engine)
    // voiced here, in the host's cast voice (the speaker's: someone with a voice of their own in their archetype's)
    if (v.spec && v.speaker && lang) {
      const made = await voiceInTown({ text, language: lang, spec: v.spec, engines: voiceEngines, live: () => voice.liveSlot?.() ?? true, level, log })
      if (made) return keep(v.speaker, made.bytes, 'node', made.engine, made.engine_voice)
    }
    if (own) return new Response(null, { status: 204 })
    // a voice it falls back to: kept here, else the host's
    for (const f of v.fallback) {
      const kept = clips.get(l.id, f, norm)
      if (kept) return served(kept)
    }
    const other = v.fallback.length ? await fromHost(l, text, v.fallback) : undefined
    if (other) return keep(other.voice, other.bytes, 'host', other.engine)
    return new Response(null, { status: 204 })
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
          const clip = hostClip(voice, ask)
          if (!clip) return json({ error: 'this town has no clip of that line in those voices', code: 'no_clip' }, 404)
          return signedJson(townKey, s.r.nonce, { v: VISIT_VOICE_VERSION, town: self, language, voice: clip.voice, engine: clip.engine, bytes: clip.bytes.length, audio: Buffer.from(clip.bytes).toString('base64') })
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
          const gender = voicedGender(addressee()) ?? 'female'
          const people = Object.fromEntries(Object.entries(cast?.people ?? {}).map(([p, v]) => [p, { ...v, status: v.speaker.startsWith('@') ? 'own' : 'shared' }]))
          return json({ people, player: cast?.player[gender] ?? gender, language: cast?.language ?? l.display?.language ?? null })
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
