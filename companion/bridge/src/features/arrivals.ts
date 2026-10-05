// Arrivals (lani.arrival/v0, companion/VILLAGERS.md "Arrivals"): how someone new is introduced to the learner. The app
// bundles the culture pack's (arrivals.json: the cast's, and the templates for the people the village makes up) and gets
// the tutor's here (GET /arrivals). When someone moves in or is born the app says so (villager_arrived, chat.ts adds the
// learner's level and weak spots); the tutor writes their introduction with publish_arrival, which replaces the template.
import { badNote, fail, noteIn, ok } from '../channel'
import { ARRIVAL_SCHEMA, cultureArrivals, residentIds, validateArrival } from '../arrivals'
import type { FeatureFactory } from '../feature'
import { json } from '../http'
import { LANGUAGES } from '../learners'

/** The paragraph, in the village's language [lang] ([languageName]): Jan's names Slovene examples. */
export function arrivalsInstructions(lang: string, languageName: string): string {
  const examples = lang === 'sl'
    ? '"Dober dan" or "Živjo" by ti/vi, "Dobrodošel/Dobrodošla" agreeing with who it is, "Čestitam!" for a baby, "Me veseli."'
    : 'the greeting by the register, a welcome that agrees with who it is where the language has it, congratulations for a baby, "nice to meet you"'
  const memory = lang === 'sl' ? '"dan, ko sem se preselila v vas"' : 'a noun phrase, as their remember lines take it'
  return `
Arrivals (lani.arrival/v0, companion/VILLAGERS.md "Arrivals"): when someone joins the village, a short introduction
dialog waits for Jan (a bubble at their home; nobody hosts a run, asks for help or stands in a scene before Jan has met
them there). The culture pack has one for each of its cast, and templates for the people the village makes up (a
newcomer, a birth, a mover). kind villager_arrived (data.arrival: Jan's level and weak patterns, a baby's parents) is
your cue to write theirs: publish_arrival with data.id as the id, in ${languageName} with translations like a scene
dialog's lines, at data.arrival.level (one level is enough): 3-5 short lines at A1 (name, trade, where from), a bit more
at A2 (why they came, with whom), 1-2 turns for Jan with 2-3 choices each (${examples}), around one of the weak patterns
where it fits naturally. Every wrong choice has a why and a reply (their reaction to what was said, never the right
form). Who speaks: the newcomer (their id), or a baby's parents (their ids: a baby can't talk); a relative who
introduces them is "by". memory: what they keep of the day, in their words, fitting their remember lines (${memory}).
It replaces the template, which plays until then. get_arrival shows what the app would play for someone now.
`.trim()
}

export const arrivals: FeatureFactory = ctx => {
  const { arrivals: store, villagers, game, events, culture } = ctx
  const language = culture.manifest?.language ?? 'sl'
  /** Who may speak in an arrival: the residents and the cast (their ids); undefined without a village state. */
  const people = () => {
    const r = residentIds(game.read())
    return r && [...new Set([...r, ...villagers.all().map(v => v.id)])]
  }
  return {
    instructions: arrivalsInstructions(language, LANGUAGES[language] ?? language),
    routes: [
      // The tutor's arrivals; the app has the culture pack's (an older app never asks)
      { method: 'GET', path: '/arrivals', addressed: true, handle: () => json({ schema: ARRIVAL_SCHEMA, language, arrivals: store.all() }) },
    ],
    tools: [
      {
        name: 'get_arrival',
        description:
          "What the app would play to introduce someone of the village (a resident's or a cast member's id): the tutor's arrival, else the culture pack's for a cast member, else the pack's template for a newcomer, a birth or a mover (with the resident's facts). Base a new one on it.",
        inputSchema: { type: 'object', properties: { id: { type: 'string', description: 'The villager or resident id' } }, required: ['id'] },
        handle: args => {
          const id = typeof args.id === 'string' ? args.id : ''
          const own = store.get(id)
          if (own) return ok(JSON.stringify({ source: 'tutor', arrival: own }, null, 2))
          const pack = cultureArrivals(culture.dir, culture.id, language).file
          if (pack?.cast[id]) return ok(JSON.stringify({ source: 'culture', culture: culture.id, arrival: pack.cast[id] }, null, 2))
          const r = ((game.read()?.state as any)?.residents ?? []).find((x: any) => x?.id === id)
          if (!r) return fail(`no one "${id}" in the village; list_villagers shows the cast, the residents are in the village state`)
          const kind = typeof r.born === 'string' ? 'birth' : typeof r.culture === 'string' && r.culture !== culture.id ? 'moved' : 'newcomer'
          const template = pack?.templates[kind] ?? pack?.templates.newcomer
          return ok(JSON.stringify({ source: template ? 'template' : 'none', kind, resident: r, template }, null, 2))
        },
      },
      {
        name: 'publish_arrival',
        description:
          'Validate a lani.arrival/v0 arrival (the introduction dialog of someone who joined the village) and publish it to the app: it replaces the template for that newcomer or baby (or the culture pack\'s for a cast member, until remove_arrival). Returns validation errors instead of publishing if invalid. Load the lani-studio skill before using.',
        inputSchema: {
          type: 'object',
          properties: {
            arrival: { type: 'object', description: 'The arrival (lani.arrival/v0)' },
            note: { type: 'string', description: 'One-line announcement shown in the app' },
          },
          required: ['arrival'],
        },
        handle: args => {
          const who = people()
          const v = validateArrival(args.arrival, { language, people: who })
          if (!v.ok) return fail(`arrival invalid, nothing published:\n${v.errors}`)
          if (who && !who.includes(v.arrival.id)) return fail(`arrival invalid, nothing published:\n✖ id: "${v.arrival.id}" doesn't live in the village (nor is of the cast)`)
          const note = noteIn.safeParse(args.note)
          if (!note.success) return badNote()
          store.publish(v.arrival)
          events.emit({ type: 'arrival_published', id: v.arrival.id, note: note.data ?? undefined })
          const levels = Object.keys(v.arrival.levels).join(', ')
          return ok(`published the arrival of ${v.arrival.id} (${levels})`)
        },
      },
      {
        name: 'remove_arrival',
        description: "Remove a tutor-published arrival: the culture pack's (or its template) plays again.",
        inputSchema: { type: 'object', properties: { id: { type: 'string', description: 'The villager or resident id' } }, required: ['id'] },
        handle: args => {
          const id = typeof args.id === 'string' ? args.id : ''
          if (!store.remove(id)) return fail(`no tutor arrival "${id}" to remove`)
          events.emit({ type: 'arrival_removed', id })
          return ok(`removed the tutor's arrival of ${id}`)
        },
      },
    ],
  }
}
