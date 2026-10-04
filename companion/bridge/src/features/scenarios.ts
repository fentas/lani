// Role-play scenarios (lani.scenario/v0): the tutor plays a character, Jan talks. The turns
// themselves arrive as /message kind roleplay (features/chat.ts adds the scenario brief).
import { badNote, fail, noteIn, ok } from '../channel'
import type { FeatureFactory } from '../feature'
import { json } from '../http'
import { validateScenario } from '../scenarios'
import { corpus } from '../voice'

const instructions = `
Role-play ("Pogovor · Talk"): kind roleplay carries {scenario_id, turn, heard, alternatives?} plus the
scenario brief (role, setting, goals). heard is what the speech recognizer made of Jan's line (it may
mishear; alternatives are other readings) or what Jan typed. Stay in character as the role and answer in
simple Slovene at Jan's level, 1-2 short sentences, as the text and in data: {"sl": "...", "en":
"<translation>", "correction"?: "<only for a real mistake: Jan's line corrected, Slovene + short English
why>", "goals_done": [indices of goals reached so far], "end"?: true when the scene reaches a natural
end}. data.hint = true means Jan is stuck: reply {"hint": true, "sl": "<one line Jan could say next>",
"en": "..."} without moving the scene on. kind roleplay_end carries the transcript: reply out of
character with a short debrief (what went well, 2 things to practise) and data {"debrief": true},
then persist it with the lani-db-updater skill as a speaking session. To write new scenarios
("let's practise at the post office"), load lani-studio and use publish_scenario.
`.trim()

export const scenarios: FeatureFactory = ({ scenarios, events, voice }) => ({
  instructions,
  routes: [
    { method: 'GET', path: '/scenarios', handle: () => json(scenarios.all()) },
    {
      method: 'GET',
      path: /^\/scenarios\/([a-z0-9-]+)$/,
      handle: ({ params: [id] }) => {
        const s = scenarios.get(id)
        return s ? json(s) : json({ error: 'not found' }, 404)
      },
    },
  ],
  tools: [
    {
      name: 'publish_scenario',
      description:
        'Validate a lani.scenario/v0 role-play scenario and publish it to the app (replaces a tutor scenario with the same id). Returns validation errors instead of publishing if invalid. Load the lani-studio skill before using.',
      inputSchema: {
        type: 'object',
        properties: {
          scenario: { type: 'object', description: 'The scenario (lani.scenario/v0)' },
          note: { type: 'string', description: 'One-line announcement shown in the app' },
        },
        required: ['scenario'],
      },
      handle: args => {
        const v = validateScenario(args.scenario)
        if (!v.ok) return fail(`scenario invalid, nothing published:\n${v.errors}`)
        const note = noteIn.safeParse(args.note)
        if (!note.success) return badNote()
        const err = scenarios.publish(v.scenario)
        if (err) return fail(err)
        events.emit({ type: 'scenario_published', id: v.scenario.id, title: v.scenario.title, emoji: v.scenario.emoji, note: note.data ?? undefined })
        voice.enqueue(corpus({ packs: [], modules: [], scenarios: [v.scenario] }))
        return ok(`published scenario ${v.scenario.id} (${v.scenario.goals.length} goals); curated and published: ${scenarios.all().map(s => s.id).join(', ')}`)
      },
    },
  ],
})
