// Villagers (lani.villager/v0): the cast of Moja vas. The app gets them (and a role-play built for
// the friendship with each); the tutor lists, reads, publishes and removes them, plays them in the
// villager role-plays (features/chat.ts attaches the persona) and welcomes the people who move in.
// Where the village is (its culture pack, cultures.ts) says in which language they speak and how the
// tutor is told about the place.
import { badNote, fail, noteIn, ok } from '../channel'
import { peopleLines, tutorOf } from '../cultures'
import type { Ctx, FeatureFactory } from '../feature'
import { json } from '../http'
import { partNow } from '../langs'
import { LANGUAGES, learnerFacts, learnerLangs } from '../learners'
import { allLines, GIFT_KINDS, JAN_PLACE, LINE_KINDS, learnerLevel, readsWhen, registerWord, speakerErrors, validateVillager, villagerTalk, withoutWhen, type LoadedVillager, type TalkPlace } from '../villagers'
import { corpus } from '../voice'

/** Jan's paragraph, as it has always been: the default profile's instructions don't change. */
const instructions = `
Villagers are the people of Moja vas (lani.villager/v0, companion/VILLAGERS.md): Babica Micka, Stari
Janez, Pastir Luka, Kovač Tone and the others, plus the people who move in. Each has a personality,
a story, likes, a register (ti/vi), a home, lines and a speaker (their voice in the voice cast:
grandma, grandpa, young-man, gruff, woman, teacher, young-woman, girl, boy; "voice" stays their gender,
for grammar); the friendship with Jan (points, level, memories)
lives in the app's village state. list_villagers shows the cast, get_villager one villager's spec,
publish_villager adds or changes one (a curated id is replaced until remove_villager). A line that
belongs to a time of day says so with "when": "Dober dan" ["morning", "afternoon"], "Dobro jutro"
["morning"], "Dober večer" and "Lahko noč" ["evening", "night"]; the app says it only then (a line
without "when" fits any time), so keep a level-0 greeting for every part of the day.
Villager role-play: a roleplay message whose scenario_id is "villager:<id>" carries, besides the
scenario brief (its opener greets for the time of day on Jan's phone), data.villager: the persona, the
friendship level and their memories. Play them: stay
in character and in their register (a vi villager keeps vi and expects it back; note when Jan slips),
simple Slovene at Jan's level, 1-2 short sentences, their manner and their topics. Bring up one
shared memory naturally when there is one ("Še vedno mislim na …"), never all of them. Correct only
real mistakes, gently, as in other role-plays. In the roleplay_end debrief add data.memory: {"sl",
"en"}, one thing from this talk worth keeping, in the villager's words, fitting "Še vedno mislim na …"
(accusative: "pogovor o ovcah ob ribniku", "the talk about sheep by the pond"); the app stores it and
the friendship grows.
kind villager_arrived: someone moved in or was born (data: {id, name, role, family, kind:
newcomer|birth}). You may welcome them in a line to Jan. For a newcomer, give them a personality, a
story and their own lines with publish_villager under the same id, keeping the name, sprite (art) and
voice as sent (speaker: young-woman or young-man, girl or boy for a child), in Primorska style (load lani-studio, "Villagers"); for a birth a short warm note
is enough. Either way, write how Jan meets them with publish_arrival (see Arrivals): the app plays a
template until it comes.
`.trim()

/** How a remember line and a memory read in the village's language (the villager format's `remember` lines). */
const REMEMBER: Record<string, { line: string; memory: string }> = {
  en: { line: 'I still think of …', memory: 'a noun phrase with "the" or "our", the object of the verb: "the talk about the sheep by the tarn"' },
  sl: { line: 'Še vedno mislim na …', memory: 'accusative: "pogovor o ovcah ob ribniku"' },
  it: { line: 'Ricordo ancora …', memory: 'a noun phrase with its article, the object of the verb: "la chiacchierata sulle pecore vicino allo stagno"' },
  de: { line: 'Ich denke noch oft an …', memory: 'a noun phrase with its article in the accusative, after "an": "das Gespräch über die Schafe am Teich"' },
}

/**
 * The paragraph for a village in another culture pack: the same rules, with its cast, its language, its registers
 * (Italian tu and Lei, German du and Sie), its remember lines and its style.
 */
export function villagersInstructions(place: TalkPlace, cast: { name: string }[], languageName: string): string {
  if (place.target === JAN_PLACE.target && place.tutor.style === JAN_PLACE.tutor.style) return instructions
  const r = REMEMBER[place.target] ?? { line: '…', memory: 'a noun phrase' }
  const names = cast.slice(0, 4).map(v => v.name).join(', ')
  // the register's two forms in the village's language, when it has two (Italian tu and Lei, German du and Sie)
  const [ti, vi] = [registerWord(place.target, 'ti'), registerWord(place.target, 'vi')]
  const two = place.target !== 'sl' && ti !== vi
  // English has one "you": the vi villagers are addressed with Mr or Mrs and their name
  const registers = place.target === 'en'
    ? 'English has one "you": a vi villager is addressed with Mr or Mrs and their name'
    : `they say ${two ? ti : 'tu'} to Jan, and a vi villager expects ${two ? vi : 'vi'} back (note gently when Jan slips)`
  const reg = two ? `ti = ${ti}, vi = ${vi}` : 'ti/vi'
  // the languages of a line: the village's, the learner's base and English (once: an English village's line is its "en")
  const langs = `{${[...new Set([place.target, place.base, 'en'])].map(code => `"${code}"`).join(', ')}}`
  return `
Villagers are the people of ${place.tutor.village} (lani.villager/v0, companion/VILLAGERS.md): ${names || 'the cast'} and the others, plus the people who move in. Each has a personality, a story, likes, a register (${reg}: how Jan addresses them), a home, lines in ${languageName} with translations (${langs}) and a speaker (their voice's archetype: grandma, grandpa, young-man, gruff, woman, teacher, young-woman, girl, boy; "voice" stays their gender, for grammar); the friendship with Jan (points, level, memories) lives in the app's village state. list_villagers shows the cast, get_villager one villager's spec, publish_villager adds or changes one (a curated id is replaced until remove_villager). A line that belongs to a time of day says so with "when": a good day ["morning", "afternoon"], a good morning ["morning"], a good evening or a good night ["evening", "night"]; the app says it only then (a line without "when" fits any time), so keep a level-0 greeting for every part of the day. A villager may have a "routine" (their day, VILLAGERS.md "A day in the village"): "up" and "bed" ("6:00", "22:15"; without them their kind's, never after the storyteller), and night "rituals", the only reason to be out at night: {id, at, until, to, doing, title}, 10 to 60 minutes between 19:00 and 6:00, "to" a building type, spot:<id>, fire or forest, "doing" stand, tend, carry or watch, the title in ${languageName} with its translations; the map shows them walking there with a lantern. Where they sleep is the first roof of their home (the tent, the hut or house, the smithy, the school); one whose work is no home says "sleeps": a roof ("smithy") or a room of a house by its art ("house:cellar", "house:livingroom": they lodge there, in a bed of that room).
Villager role-play: a roleplay message whose scenario_id is "villager:<id>" carries, besides the scenario brief (its opener_sl is in ${languageName}, opener_en its translation, a greeting for the time of day on the learner's phone), data.villager: the persona, the friendship level and their memories. Play them: stay in character; ${registers}; simple ${languageName} at Jan's level, 1-2 short sentences, their manner and their topics. Bring up one shared memory naturally when there is one ("${r.line}"), never all of them. Correct only real mistakes, gently, as in other role-plays. In the roleplay_end debrief add data.memory: ${langs}, one thing from this talk worth keeping, in the villager's words, fitting "${r.line}" (${r.memory}; "the talk about sheep by the pond"); the app stores it and the friendship grows.
kind villager_arrived: someone moved in or was born (data: {id, name, role, family, kind: newcomer|birth}). You may welcome them in a line to Jan. For a newcomer, give them a personality, a story and their own lines (${langs}) with publish_villager under the same id, keeping the name, sprite (art) and voice as sent (speaker: young-woman or young-man, girl or boy for a child), in ${place.tutor.style} (load lani-studio, "Villagers"); for a birth a short warm note is enough. Either way, write how Jan meets them with publish_arrival (see Arrivals): the app plays a template until it comes.
`.trim()
}

/**
 * Where talks happen for this bridge's learner: the village's language and the learner's base, their name (Jan for the
 * default profile), the culture pack's tutor texts and newcomers' lines.
 */
export function talkPlace({ cfg, culture, profile }: Pick<Ctx, 'cfg' | 'culture' | 'profile'>): TalkPlace {
  const { target, base } = learnerLangs(cfg.dataDir, culture.manifest?.language ?? 'sl')
  const learner = profile.isDefault ? 'Jan' : learnerFacts(cfg.dataDir).name || profile.id
  return { target, base, learner, tutor: tutorOf(culture.manifest), plain: peopleLines(culture.dir, culture.id) }
}

function villagerInfo(v: LoadedVillager) {
  const { schema: _, lines, ...meta } = v
  const gift = lines.gift && Object.fromEntries(GIFT_KINDS.map(k => [k, lines.gift?.[k]?.length ?? 0]))
  return { ...meta, lines: { ...Object.fromEntries(LINE_KINDS.map(k => [k, lines[k]?.length ?? 0])), ...(gift ? { gift } : {}) } }
}

export const villagers: FeatureFactory = ctx => {
  const { cfg, villagers, game, events, voice, culture } = ctx
  const place = talkPlace(ctx)
  // the phone says the time of day it is there (?time=evening), so the opener greets for it
  const talk = (id: string, time?: string | null) => villagerTalk(villagers, game.read(), id, learnerLevel(cfg.dataDir), talkPlace(ctx), partNow(time))
  return {
    instructions: villagersInstructions(place, villagers.all(), LANGUAGES[place.target] ?? place.target),
    routes: [
      // a line's `when` only to an app that reads it (it says its time of day); an older one gets the lines without
      { method: 'GET', path: '/villagers', addressed: true, handle: ({ url }) => json(villagers.all().map(v => (readsWhen(url) ? v : withoutWhen(v)))) },
      {
        method: 'GET',
        path: /^\/villagers\/([a-z0-9-]+)$/,
        addressed: true,
        handle: ({ params: [id], url }) => {
          const v = villagers.get(id)
          return v ? json(readsWhen(url) ? v : withoutWhen(v)) : json({ error: 'not found' }, 404)
        },
      },
      {
        method: 'GET',
        path: /^\/villagers\/([a-z0-9-]+)\/scenario$/,
        addressed: true,
        handle: ({ params: [id], url }) => {
          const t = talk(id, url.searchParams.get('time'))
          return t ? json({ ...t.scenario, source: t.source }) : json({ error: 'not found' }, 404)
        },
      },
    ],
    tools: [
      {
        name: 'list_villagers',
        description: 'List the people of the village (curated and tutor-written): who they are, where they live, their register, and how many lines of each kind they have.',
        inputSchema: { type: 'object', properties: {} },
        handle: () => ok(JSON.stringify(villagers.all().map(villagerInfo), null, 2)),
      },
      {
        name: 'get_villager',
        description: 'One villager as their lani.villager/v0 spec (personality, story, likes, lines), to change and publish again.',
        inputSchema: { type: 'object', properties: { id: { type: 'string', description: 'The villager id' } }, required: ['id'] },
        handle: args => {
          const v = typeof args.id === 'string' ? villagers.get(args.id) : undefined
          return v ? ok(JSON.stringify(v, null, 2)) : fail(`no villager "${args.id}"; list_villagers shows the ids`)
        },
      },
      {
        name: 'publish_villager',
        description:
          'Validate a lani.villager/v0 villager and publish them to the app (replaces a tutor villager with the same id; a curated id is replaced until remove_villager; a newcomer keeps the id, name, art and voice the app sent). Returns validation errors instead of publishing if invalid. Load the lani-studio skill before using. Say the learner {learner} and what agrees with them {m:…|f:…} (companion/SCENES.md, the learner in the content).',
        inputSchema: {
          type: 'object',
          properties: {
            villager: { type: 'object', description: 'The villager (lani.villager/v0)' },
            note: { type: 'string', description: 'One-line announcement shown in the app' },
          },
          required: ['villager'],
        },
        handle: args => {
          const v = validateVillager(args.villager)
          if (!v.ok) return fail(`villager invalid, nothing published:\n${v.errors}`)
          const bad = speakerErrors(v.villager)
          if (bad.length) return fail(`villager invalid, nothing published:\n${bad.map(e => `✖ ${e}`).join('\n')}`)
          const note = noteIn.safeParse(args.note)
          if (!note.success) return badNote()
          const replaced = villagers.isCurated(v.villager.id)
          villagers.publish(v.villager)
          events.emit({ type: 'villager_published', id: v.villager.id, name: v.villager.name, emoji: v.villager.emoji, note: note.data ?? undefined })
          voice.enqueue(corpus({ packs: [], modules: [], scenarios: [], villagers: [v.villager], language: culture.manifest?.language, learner: ctx.addressee() }))
          const n = allLines(v.villager.lines).length
          return ok(`published villager ${v.villager.id} (${v.villager.name}, ${n} lines)` + (replaced ? `; it replaces the curated villager "${v.villager.id}" until remove_villager` : ''))
        },
      },
      {
        name: 'remove_villager',
        description: 'Remove a tutor-published villager. A curated villager with the same id shows again.',
        inputSchema: { type: 'object', properties: { id: { type: 'string', description: 'The villager id' } }, required: ['id'] },
        handle: args => {
          const id = typeof args.id === 'string' ? args.id : ''
          if (!villagers.remove(id)) return fail(`no tutor villager "${id}" to remove`)
          events.emit({ type: 'villager_removed', id })
          return ok(`removed tutor villager ${id}${villagers.get(id) ? ' (the curated one is back)' : ''}`)
        },
      },
    ],
  }
}
