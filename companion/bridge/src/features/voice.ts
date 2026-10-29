// Voice clips: natural Slovene audio made once on the node (../voice.ts). The clips are readable
// with either token; synthesis and status are the app's.
import { join } from 'node:path'
import { z } from 'zod'
import { fail, ok } from '../tool'
import type { Ctx, FeatureFactory } from '../feature'
import { json, readJson } from '../http'
import { cardExamples } from '../cards'
import { voicedGender } from '../addressee'
import { baseVoice, cast, isLearnerVoice, isVoice, PERSON_VOICE, personVoice, PLAYER, SPEAKER_ID } from '../cast'
import { CHOICE, choiceOf, DEFAULT, mineVoice, myVoice, OFF, sampleLine, writeChoice, type MyVoice } from '../my-voice'
import { cultureArrivals } from '../arrivals'
import { familyHas, indexHas } from '../course-speech'
import { cultureKeepers, keeperTellings } from '../cultures'
import { learnerLangs } from '../learners'
import { cultureStepFiles, stepTellings } from '../project-steps'
import { cultureCelebrationFiles, partyTellings } from '../celebrations'
import { readISpy } from '../ispy'
import { localDate } from '../rhythm'
import { corpus, EXPLANATION_CHARS, fileUrl, LINE_CHARS, voiceInstructions, type CorpusItem, type Redo, type VoiceStore } from '../voice'

// voice: a speaker of the cast (female, male, grandma, …), someone's own voice ("@micka"), or the learner's ("player":
// their own lines in a dialog, in the cast's `player` for their gender); an unknown one is a 400, so the app tries its
// fallback.
const lineIn = z.object({ text: z.string().trim().min(1).max(LINE_CHARS), voice: z.union([z.string().regex(SPEAKER_ID), z.string().regex(PERSON_VOICE)]).default('female') })
// lang: the text's language when it isn't the village's: an explanation in the learner's own (a school intro's step,
// the Slovene forms inside it), up to EXPLANATION_CHARS; without it (or in the village's language) a line, up to LINE_CHARS
const sayIn = lineIn.extend({ text: z.string().trim().min(1).max(EXPLANATION_CHARS), lang: z.string().regex(/^[a-z]{2}$/).optional() })
// a re-record (the app's long press on 🔊): the text and the voice the app plays it in; client_id makes a retry harmless
const redoIn = lineIn.extend({ client_id: z.string().optional() })
// the car's getting ready, and the day's audio (the next unit's explanations among them): up to 20 texts, each in the
// voice the app plays it in
const prepareIn = z.object({ texts: z.array(sayIn).min(1).max(20) })

/** Why [t] is too long for the node: a line over LINE_CHARS (an explanation, with its lang, may have EXPLANATION_CHARS); else undefined. */
const tooLong = (voice: VoiceStore, t: { text: string; lang?: string }) =>
  t.text.length > LINE_CHARS && !voice.explains(t.lang) ? `a line is at most ${LINE_CHARS} characters (an explanation in another language than the village's, with its lang, ${EXPLANATION_CHARS})` : undefined
/** The characters /voice/prepare may have voiced new a day (the node's local day), unless LANI_VOICE_ROAD_DAILY says. */
const ROAD_DAILY = 6_000
type Prepared = { text: string; voice: string; url: string | null; engine?: string }

/**
 * A re-record's answer: 200 with the new clip ("done"), or the clip re-recorded earlier today ("not_needed"); 429
 * "limit" with the reason ("cap": the day's re-records are spent; "reserve": the characters left are for live lines);
 * 503 "failed". The day's re-records and their cap come along. The clip's voice is the one the app knows (a Slovene narrator's
 * clip in its Slovene ElevenLabs voice, "female.sl", is the narrator's: female).
 */
function redoAnswer(r: Redo, voice: VoiceStore): Response {
  const day = { today: voice.redosToday(), daily: voice.redoDaily }
  switch (r.status) {
    case 'done':
    case 'not_needed': {
      const c = r.clip
      const clip = { url: fileUrl(c.file), file: c.file, key: c.key, norm: c.norm, voice: baseVoice(c.voice), method: c.method ?? null }
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

/**
 * Every text to voice (in the village's language) from the review cards and the current packs, modules, scenarios, the
 * school's course (in the teacher's voice; its explanations in the learner's own language are counted apart: voice_status
 * explanations), scenes, the cast's arrivals, villagers, grammar pages (and the cheat sheet's cards' examples), stories
 * and the car's drills.
 */
function voiceCorpus({ cfg, modules, packs, scenarios, scenes, villagers, learner, culture, grammar, cards, stories, drills, addressee, course, arrivals, voice, family, profile }: Ctx): CorpusItem[] {
  const current = modules.list().filter(m => !m.tags.includes('retired')).flatMap(m => modules.get(m.id) ?? [])
  // «Vidim, vidim»: the content the app bundles (companion/ispy), read here for its voices only
  const ispy = readISpy(join(cfg.projectDir, 'companion/ispy'))
  const language = culture.manifest?.language ?? 'sl'
  // the cast's arrivals: the culture pack's, the tutor's for one of them replacing it
  const met = new Map(Object.entries(cultureArrivals(culture.dir, culture.id, language).file?.cast ?? {}).map(([id, a]) => [id, { id, levels: a.levels }]))
  for (const a of arrivals.all()) if (a.language === language) met.set(a.id, { id: a.id, levels: a.levels })
  // the app's check for the language learned: a clip of the text in any voice, a family recording of it (looked at once)
  let clips: ReturnType<VoiceStore['index']> | undefined
  const has = (t: string) => indexHas((clips ??= voice.index()), t) || familyHas(family.index(), t)
  const school = { units: course.served()?.units ?? [], pages: grammar.all(), bases: [learnerLangs(cfg.dataDir, language).base], has }
  return corpus({ packs: packs.all(), modules: current, scenarios: scenarios.all(), scenes: scenes.resolved(), keepers: cultureKeepers(culture.dir, culture.id).map(k => ({ ...k, tellings: keeperTellings(k, k.cycle) })), steps: [...cultureStepFiles(culture.dir, culture.id).map(f => ({ project: f.project, tellings: stepTellings(f) })), ...cultureCelebrationFiles(culture.dir, culture.id).map(f => ({ project: `party:${f.age}`, tellings: partyTellings(f) }))], villagers: villagers.all(), cards: learner.reviewCards(), grammar: [...grammar.all(), ...cardExamples(grammar.all(), cards.all())], stories: stories.all(), drills: drills.all(), ispy, course: school, arrivals: [...met.values()], ownVoice: id => !!personVoice(`@${id}`), language: culture.manifest?.language, learner: addressee(), mine: mineVoice(cfg.dataDir, addressee(), profile.child) })
    // the explanations: apart (voice_status explanations), as the app asks for them
    .filter(i => !i.lang)
}

// the learner's choice of their own voice ("Moj glas · My voice"): one of the voices offered them, "off" or "default"
const choiceIn = z.object({ voice: z.string().regex(CHOICE, 'a voice offered, "off" or "default"') })
// a voice offered, to hear its sample (its ▶)
const sampleIn = z.object({ voice: z.string().regex(CHOICE, 'a voice offered') })

/** The learner's voice as the app gets it (GET and POST /learner/voice): what is offered, what is in effect, the sample. */
function myVoiceBody(m: MyVoice, sample: string, language: string) {
  return {
    language,
    sample,
    // the voice in effect, as the cast names it ("male.sl", "player-boy-b"); null when none may say the learner's lines
    voice: m.option?.id ?? null,
    off: m.off,
    chosen: m.chosen,
    choice: m.choice ?? null,
    age: m.age,
    options: m.options.map(o => ({ id: o.id, voice: o.voice, name: o.name, gender: o.gender, age: o.age, pick: o.pick, default: o.pick === 0 })),
  }
}

export const voice: FeatureFactory = ctx => {
  const { voice, profiles, outbox } = ctx
  const language = ctx.culture.manifest?.language ?? 'sl'
  // the learner's own voice: their choice (learner-profile.json preferences.my_voice), else the default of their gender
  // (the profile's learner.gender; the female one's while it says none) and age (the profile's child flag, LANI_CHILD)
  const mine = () => myVoice({ gender: voicedGender(ctx.addressee()), child: ctx.profile.child, choice: choiceOf(ctx.cfg.dataDir) })
  // the voice a line asked for in [v] is said in: the learner's own ("player") in their voice (undefined when the cast has
  // none that may say it: the phone speaks), anyone else's as asked
  const voiceOf = (v: string): string | undefined => (v === PLAYER ? mine().option?.voice : v)
  const handleRedo = async (req: Request) => {
    const r = redoIn.safeParse(await readJson(req))
    if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
    const v = voiceOf(r.data.voice)
    if (!v && r.data.voice === PLAYER) return json({ status: 'failed', message: "no voice of the cast may say the learner's lines" }, 503)
    if (!isVoice(v)) return json({ error: `unknown voice "${r.data.voice}"; the cast has ${Object.keys(cast()).join(', ')}` }, 400)
    return redoAnswer(await voice.redo(r.data.text, v), voice)
  }
  const roadDaily = process.env.LANI_VOICE_ROAD_DAILY ? Number(process.env.LANI_VOICE_ROAD_DAILY) : ROAD_DAILY
  // the characters /voice/prepare had voiced new today; kept in memory (a restart counts the day again)
  const road = { day: '', chars: 0 }
  const roadToday = () => {
    const day = localDate(new Date())
    if (road.day !== day) Object.assign(road, { day, chars: 0 })
    return road.chars
  }
  const handlePrepare = async (req: Request) => {
    const r = prepareIn.safeParse(await readJson(req))
    if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
    const long = r.data.texts.map(t => tooLong(voice, t)).find(Boolean)
    if (long) return json({ error: long }, 400)
    const clips: Prepared[] = []
    for (const { text, voice: asked, lang } of r.data.texts) {
      const v = voiceOf(asked)
      // a voice not in the cast (or none for the learner's lines): none for this text only (the app asks in the voice it
      // wants, no other voice's clip)
      if (!isVoice(v)) {
        clips.push({ text, voice: asked, url: null })
        continue
      }
      // the clip the app would get (a Slovene narrator's in its Slovene ElevenLabs voice or the local one; an explanation's
      // under its own key)
      const had = voice.best(voice.normOf(text, lang), v)
      // a character's local clip: in the speaker's ElevenLabs voice now when it can be (the lazy re-voice's daily cap,
      // never the reserve), so the day's clips got ready ahead are hers; the local clip when it can't
      const up = had && voice.wantsElevenLabsVoice(had) ? await voice.upgradeLocal(had) : undefined
      // not live: ElevenLabs only above the reserve, then the local worker (never for an explanation); nothing new over
      // the day's cap
      const c = up ?? had ?? (roadToday() + voice.cost(text, v, lang) <= roadDaily ? await voice.voice(text, v, 'road', { lang }) : null)
      if (c && !had && c.source === 'road') road.chars = roadToday() + c.chars
      clips.push(c ? { text, voice: asked, url: fileUrl(c.file), engine: c.engine } : { text, voice: asked, url: null })
    }
    return json({ clips, today: roadToday(), daily: roadDaily })
  }
  // everyone's voice profile, and the voice the learner's own lines are said in (the cast's `player` for their gender):
  // the app looks their clips up by it
  const views = () => {
    profiles.syncCast()
    const m = mine()
    const o = m.option
    // mine: the learner's voice for the app (what speaks their lines when no clip can be had: the phone's voice in its
    // gender, higher for a child's; off: their lines aren't said), and whether they have chosen (the offer at the first line)
    return { people: profiles.views(), player: o?.voice ?? null, mine: { voice: o?.voice ?? null, id: o?.id ?? null, name: o?.name ?? null, gender: o?.gender ?? null, age: o?.age ?? m.age, off: m.off, chosen: m.chosen } }
  }
  /** The learner's voice as the app's "Moj glas · My voice" shows it: the voices offered, the one in effect, the sample. */
  const myVoiceNow = () => myVoiceBody(mine(), sampleLine(language, ctx.addressee()), language)
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
          const long = tooLong(voice, r.data)
          if (long) return json({ error: long }, 400)
          const v = voiceOf(r.data.voice)
          // the learner's own line when the cast has no voice that may say it: the phone speaks it
          if (!v && r.data.voice === PLAYER) return new Response(null, { status: 204 })
          if (!isVoice(v)) return json({ error: `unknown voice "${r.data.voice}"; the cast has ${Object.keys(cast()).join(', ')}` }, 400)
          // with lang: an explanation in the learner's own language (not a live line: never the reserve). The learner's own
          // line (asked as "player", or in one of the voices they choose from: a sample, a line asked in it): on the normal
          // path in their voice, a word to three said inside the carrier sentence as a re-record says it, and never in
          // another voice when theirs can't be had now (the app speaks it otherwise)
          const own = r.data.voice === PLAYER || isLearnerVoice(v)
          const c = await voice.say(r.data.text, v, r.data.lang, own ? { carrier: true, alone: true } : undefined)
          // the voice the app knows the clip by (a Slovene narrator's in its ElevenLabs voice: the narrator's)
          return c ? json({ url: fileUrl(c.file), voice: baseVoice(c.voice), engine: c.engine }) : new Response(null, { status: 204 })
        },
      },
      // Jan's re-record of a clip they hear wrong (a long press on 🔊): made again now, once a clip a day, within the day's cap.
      { method: 'POST', path: '/voice/redo', handle: ({ req, url }) => outbox.once(req, url.pathname, handleRedo) },
      // The car's quiz getting ready ("🚗 Za pot", in the background): clips for the options the store lacks (a turn's
      // wrong answers, a sentence's wrong forms, "Prav!"), up to 20 at a time, each in the voice asked for (url null when
      // it can't be had). Not a live line: never the reserve, no rate limit; at most LANI_VOICE_ROAD_DAILY characters
      // (6,000) voiced new a day, the clips there come all the same. A clip made once is reused: a retry is harmless. The
      // day's audio on Wi-Fi asks here too, the next school unit's explanations among it (each with its lang).
      { method: 'POST', path: '/voice/prepare', handle: ({ req }) => handlePrepare(req) },
      { method: 'GET', path: '/voice/status', handle: async () => json({ ...(await voice.status(voiceCorpus(ctx))), profiles: views().people }) },
      // Everyone's voice: their speaker (their own "@id", else their archetype) and the pitch and pace to play it with.
      { method: 'GET', path: '/voice/profiles', handle: () => json(views()) },
      // The learner's own voice ("🗣️ Moj glas · My voice"): the voices offered them (their gender's, their age's), the one
      // in effect, whether they have chosen, and the sample line their ▶ says.
      { method: 'GET', path: '/learner/voice', handle: () => json(myVoiceNow()) },
      {
        // the choice, kept in the learner profile (preferences.my_voice): a voice offered, "off" (their lines aren't
        // said) or "default" (they saw it and keep the default, which follows their gender, age and the cast). It writes the
        // learner's data, so the lease guards it (not a /voice/ route). Nothing is voiced again: their new lines come in it.
        method: 'POST',
        path: '/learner/voice',
        handle: async ({ req }) => {
          const r = choiceIn.safeParse(await readJson(req))
          if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
          const c = r.data.voice
          const now = mine()
          if (c !== OFF && c !== DEFAULT && !now.options.some(o => o.id === c)) return json({ error: `"${c}" isn't a voice offered: ${now.options.map(o => o.id).join(', ') || 'none'}, off or default`, code: 'not_offered' }, 400)
          try {
            writeChoice(ctx.cfg.dataDir, c, localDate(new Date()))
          } catch (e) {
            return json({ error: `the choice couldn't be kept: ${(e as Error).message}`, code: 'not_kept' }, 409)
          }
          ctx.log(`my voice: ${c}`)
          return json(myVoiceNow())
        },
      },
      {
        // a voice offered, heard: its sample line ("Ciao! Sono Teo.") in that voice alone, on the normal clip path (a live
        // line: a few characters, the reserve's rules; made the first time, kept after); url null when it can't be had
        // now (the app says it with the phone's voice in the option's gender, higher for a child's)
        method: 'POST',
        path: '/voice/sample',
        handle: async ({ req }) => {
          const r = sampleIn.safeParse(await readJson(req))
          if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
          const o = mine().options.find(x => x.id === r.data.voice)
          if (!o) return json({ error: `"${r.data.voice}" isn't a voice offered`, code: 'not_offered' }, 400)
          const text = sampleLine(language, ctx.addressee())
          const c = await voice.say(text, o.voice, undefined, { alone: true })
          return json({ text, voice: o.voice, url: c ? fileUrl(c.file) : null, engine: c?.engine ?? null })
        },
      },
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
        handle: async () => {
          const m = mine()
          const my_voice = { choice: m.choice ?? null, voice: m.option ? `${m.option.id} (${m.option.name})` : null, off: m.off, age: m.age, offered: m.options.map(o => `${o.id} (${o.name})`) }
          return ok(JSON.stringify({ ...(await voice.status(voiceCorpus(ctx))), my_voice }, null, 2))
        },
      },
    ],
  }
}
