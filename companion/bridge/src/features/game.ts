// The village game ("Moja vas"): the app saves its state here, the tutor reads a summary.
import { z } from 'zod'
import { ok } from '../channel'
import type { FeatureFactory } from '../feature'
import { json, readText } from '../http'

const instructions = `
The app has a village game, "Moja vas": practice earns resources (vocabulary → food, listening →
wood, grammar → stone, conversation/writing → wisdom) that grow a campfire into a town, with events
(wolves, storms…) and villager sidequests. get_village shows its current state; mention it when it
motivates Jan ("your palisade needs stone: a grammar round would help"). You can write sidequests
as modules with a quest block (lani-studio, companion/GAME.md): its giver and everyone its story
names should be in people.known (here and met), or the app keeps the request until they are.
A villager shows at most two open requests at once (their own first, then the newest tutor task);
the rest wait in the Scroll under "Later", so don't pile several modules on one villager. For a
task Jan keeps failing (get_village shows its tries, best and the pages missed), publish a short
drill at the same giver with quest.helps = that task's module id: the task waits behind the drill
and comes back once the drill is passed.
`.trim()

const gamePut = z.object({ rev: z.number().int().min(0), state: z.record(z.string(), z.unknown()) })

export const game: FeatureFactory = ({ game, villagers }) => ({
  instructions,
  routes: [
    {
      method: 'GET',
      path: '/game',
      handle: () => {
        const g = game.read()
        return g ? json(g) : json({ error: 'no game' }, 404)
      },
    },
    {
      method: 'PUT',
      path: '/game',
      handle: async ({ req }) => {
        const body = await readText(req, 512 * 1024, 'game state too large')
        let parsed: unknown
        try {
          parsed = JSON.parse(body)
        } catch {
          return json({ error: 'invalid JSON' }, 400)
        }
        const r = gamePut.safeParse(parsed)
        if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
        const current = game.read()
        if ((current?.rev ?? 0) !== r.data.rev) return json(current ?? { rev: 0, state: null }, 409)
        // an app from before generated land doesn't know the village's land and would drop it (its next tick would then
        // mark it classic): the land stays as it was founded (GAME.md, "The land")
        const land = (current?.state as Record<string, unknown> | null | undefined)?.land
        const state = land !== undefined && r.data.state.land === undefined ? { ...r.data.state, land } : r.data.state
        const next = { rev: r.data.rev + 1, state }
        game.write(next)
        return json({ rev: next.rev })
      },
    },
  ],
  tools: [
    {
      name: 'get_village',
      description:
        "Summary of the learner's village in the app's game: age, resources, buildings, who is here and met (people.known: all a scene, a request or a line may name) and who waits to be met (people.to_meet), active event, open quests (their module, tries, best try and pass mark, the grammar pages missed, the task a drill helps), help (🤝, earned by helping villagers, spent on a moba), friends, the chest's tools and goods, recent chronicle.",
      inputSchema: { type: 'object', properties: {} },
      handle: () => ok(game.summary(id => villagers.get(id)?.name)),
    },
  ],
})
