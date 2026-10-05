// Voice clips: natural Slovene audio made once on the node (../voice.ts). The clips are readable
// with either token; synthesis and status are the app's.
import { z } from 'zod'
import { fail, ok } from '../channel'
import type { Ctx, FeatureFactory } from '../feature'
import { json, readJson } from '../http'
import { cast, isVoice, PERSON_VOICE, personVoice, SPEAKER_ID } from '../cast'
import { cultureKeepers, keeperTellings } from '../cultures'
import { corpus, fileUrl, voiceInstructions, type CorpusItem, type Redo, type VoiceStore } from '../voice'

// voice: a speaker of the cast (female, male, grandma, …) or someone's own voice ("@micka"); an unknown one is a
// 400, so the app tries its fallback.
const sayIn = z.object({ text: z.string().trim().min(1).max(300), voice: z.union([z.string().regex(SPEAKER_ID), z.string().regex(PERSON_VOICE)]).default('female') })
// a re-record (the app's long press on 🔊): the text and the voice the app plays it in; client_id makes a retry harmless
const redoIn = sayIn.extend({ client_id: z.string().optional() })

/**
 * A re-record's answer: 200 with the new clip ("done"), or the clip re-recorded earlier today ("not_needed"); 429
 * "limit" with the reason ("cap": the day's re-records are spent; "reserve": the characters left are for live lines);
 * 503 "failed". The day's count and cap come along.
 */
function redoAnswer(r: Redo, voice: VoiceStore): Response {
  const day = { today: voice.revoicedToday(), daily: voice.revoiceDaily }
  switch (r.status) {
    case 'done':
    case 'not_needed': {
      const c = r.clip
      const clip = { url: fileUrl(c.file), file: c.file, key: c.key, norm: c.norm, voice: c.voice, method: c.method ?? null }
      return json({ status: r.status, ...clip, ...(r.status === 'not_needed' ? { message: r.message } : {}), ...day })
    }
    case 'limit':
      return json({ status: 'limit', reason: r.reason, message: r.message, ...day }, 429)
    case 'failed':
      return json({ status: 'failed', message: r.message }, 503)
  }
}

// The people the app knows (the cast who live here, the residents), for their voice profiles.
const personId = z.string().regex(/^[a-z0-9][a-z0-9-]{0,63}$/)
const profilesIn = z.object({
  people: z.array(z.object({
    id: personId,
    name: z.string().trim().min(1).max(80),
    gender: z.enum(['female', 'male']),
    speaker: z.string().regex(SPEAKER_ID),
    stage: z.enum(['child', 'youth', 'adult', 'old']).optional(),
    cast: z.boolean().optional(),
    order: z.number().int().min(0).max(999).optional(),
    present: z.boolean().optional(),
  })).max(80),
})
const redesignIn = z.object({ person: personId, description: z.string().trim().min(10).max(900).optional(), like: z.string().trim().min(2).max(64).optional() })

/** Every text to voice (in the village's language) from the review cards and the current packs, modules, scenarios, scenes, villagers, grammar pages, stories and the car's drills. */
function voiceCorpus({ modules, packs, scenarios, scenes, villagers, learner, culture, grammar, stories, drills, addressee }: Ctx): CorpusItem[] {
  const current = modules.list().filter(m => !m.tags.includes('retired')).flatMap(m => modules.get(m.id) ?? [])
  return corpus({ packs: packs.all(), modules: current, scenarios: scenarios.all(), scenes: scenes.resolved(), keepers: cultureKeepers(culture.dir, culture.id).map(k => ({ ...k, tellings: keeperTellings(k) })), villagers: villagers.all(), cards: learner.reviewCards(), grammar: grammar.all(), stories: stories.all(), drills: drills.all(), ownVoice: id => !!personVoice(`@${id}`), language: culture.manifest?.language, learner: addressee() })
}

export const voice: FeatureFactory = ctx => {
  const { voice, profiles, outbox } = ctx
  const handleRedo = async (req: Request) => {
    const r = redoIn.safeParse(await readJson(req))
    if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
    if (!isVoice(r.data.voice)) return json({ error: `unknown voice "${r.data.voice}"; the cast has ${Object.keys(cast()).join(', ')}` }, 400)
    return redoAnswer(await voice.redo(r.data.text, r.data.voice), voice)
  }
  const views = () => {
    profiles.syncCast()
    return { people: profiles.views() }
  }
  return {
    start: () => {
      profiles.syncCast()
      void profiles.designNext()
      // short narrator words the app asked for that wait for their carrier re-voice (the daily cap, the reserve)
      voice.resumeRevoice()
      // clips voice-build changes while the bridge runs (--run, --revoice-words, --denoise): the app is told
      voice.watch()
    },
    instructions: voiceInstructions,
    routes: [
      { method: 'GET', path: '/voice/index', access: 'any', handle: () => json(voice.index()) },
      {
        method: 'GET',
        path: /^\/voice\/file\/([^/]+)$/,
        access: 'any',
        handle: ({ params: [name], url }) => {
          const p = voice.path(name)
          if (!p) return json({ error: 'not found' }, 404)
          // counted; a short narrator word from before the carrier sentence is served now and re-voiced soon. Not with
          // ?count=0: the app getting the car's clips ready ("🚗 Za pot") takes them as they are and never has one voiced.
          if (url.searchParams.get('count') !== '0') voice.requested(name)
          return new Response(Bun.file(p), {
            headers: { 'content-type': 'audio/mpeg', 'x-content-type-options': 'nosniff', 'cache-control': 'private, max-age=31536000, immutable' },
          })
        },
      },
      {
        method: 'POST',
        path: '/voice/say',
        handle: async ({ req }) => {
          const r = sayIn.safeParse(await readJson(req))
          if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
          if (!isVoice(r.data.voice)) return json({ error: `unknown voice "${r.data.voice}"; the cast has ${Object.keys(cast()).join(', ')}` }, 400)
          const c = await voice.say(r.data.text, r.data.voice)
          return c ? json({ url: fileUrl(c.file), voice: c.voice, engine: c.engine }) : new Response(null, { status: 204 })
        },
      },
      // Jan's re-record of a clip they hear wrong (a long press on 🔊): made again now, once a clip a day, within the day's cap.
      { method: 'POST', path: '/voice/redo', handle: ({ req, url }) => outbox.once(req, url.pathname, handleRedo) },
      { method: 'GET', path: '/voice/status', handle: async () => json({ ...(await voice.status(voiceCorpus(ctx))), profiles: views().people }) },
      // Everyone's voice: their speaker (their own "@id", else their archetype) and the pitch and pace to play it with.
      { method: 'GET', path: '/voice/profiles', handle: () => json(views()) },
      {
        method: 'POST',
        path: '/voice/profiles',
        handle: async ({ req }) => {
          const r = profilesIn.safeParse(await readJson(req))
          if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
          profiles.syncCast()
          profiles.upsert(r.data.people)
          return json(views())
        },
      },
    ],
    tools: [
      {
        name: 'voice_profiles',
        description:
          "Everyone's voice (one voice per person): who speaks with a voice of their own (designed with ElevenLabs Voice Design from a Slovene sample of their lines) and who with their archetype's voice (grandma, young-man) at their own pitch and pace; who lives in the village, and any design error. Voices of one's own go to the cast first while the account has voice slots.",
        inputSchema: { type: 'object', properties: {} },
        handle: () => {
          profiles.syncCast()
          const rows = profiles.all().map(p => ({ person: p.person, name: p.name, cast: !!p.cast, present: !!p.present, archetype: p.archetype, status: p.status, own_voice: p.elevenlabs ?? undefined, pitch: p.pitch, rate: p.rate, error: p.error ?? undefined }))
          return ok(JSON.stringify(rows, null, 2))
        },
      },
      {
        name: 'voice_redesign',
        description:
          "Gives someone a new voice of their own (when Jan says a voice doesn't suit them): ElevenLabs Voice Design makes three takes, the clearest Slovene is kept, the old voice and its clips go. Optionally a new description in English (age, timbre, manner; always 'a native Slovene speaker'), and a voice to sound like (like: a cast speaker such as grandpa, someone's own @micka, or an ElevenLabs voice id; its sample lends the timbre, the Slovene stays native). Needs a free voice slot; takes about half a minute.",
        inputSchema: {
          type: 'object',
          properties: {
            person: { type: 'string', description: 'villager or resident id, e.g. micka' },
            description: { type: 'string', description: 'optional: how the new voice should sound' },
            like: { type: 'string', description: 'optional: a voice to sound like (grandpa, @micka, or an ElevenLabs voice id)' },
          },
          required: ['person'],
        },
        handle: async (args: unknown) => {
          const r = redesignIn.safeParse(args)
          if (!r.success) return fail(z.prettifyError(r.error))
          const out = await profiles.redesign(r.data.person, r.data.description, r.data.like)
          if (!('person' in out)) return fail(out.error)
          return out.status === 'own' && !out.error ? ok(`${out.name} has a new voice (${out.elevenlabs}); their lines are voiced in it as they are heard.`) : fail(`No new voice: ${out.error}`)
        },
      },
      {
        name: 'voice_status',
        description:
          'Voice store status: cached clips per voice and engine, ElevenLabs quota, whether the local Gepard worker is up, the voice cast (speakers), and how much of the content (packs, modules, scenarios, scenes, villagers) is voiced.',
        inputSchema: { type: 'object', properties: {} },
        handle: async () => ok(JSON.stringify(await voice.status(voiceCorpus(ctx)), null, 2)),
      },
    ],
  }
}
