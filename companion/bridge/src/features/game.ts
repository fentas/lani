// The village game ("Moja vas"): the app saves its state here, the tutor reads a summary.
import { z } from 'zod'
import { topicsLookedUp } from '../cards'
import { ok } from '../tool'
import { cultureFestivals, cultureSpots } from '../cultures'
import { cyclesNow } from '../spot-cycles'
import type { FeatureFactory } from '../feature'
import { json, readText } from '../http'
import { localDate } from '../rhythm'
import { calendarOn } from '../towns'

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
and comes back once the drill is passed. Once the task is passed, its drill is optional practice
(get_village: task_passed; in "Later", no "!"): don't send Jan to it as a request. While wolves, a
bear or a storm are about (get_village's event) the requests fold away from the map and wait in the
Scroll until it's over: the event's run comes first. get_village's calendar names the culture's days that are on
(their day or the two after, done once kept) and the next within five days. One marked quiet (Dan
spomina na mrtve) is a day of remembrance, not a feast: the leader asks the learner to come and
remember, nothing is won; speak of it gently, never as a celebration.
get_village's spots tell what goes on at the landscape's spots that have work over real days: the
charcoal pile in the woods (kopa), its burner's stage today (felling the beech, stacking the logs
round the pole, covering them with turf and earth, lighting it, smoking white, yellow, then blue,
cooling, raking out, the sacks, the charcoal to the smith; a rest between piles, none in winter),
the day of it, a breakout tonight (the learner's night watch: covered in time, or the pile burns to
ash) and the last delivery to the smith (came or not: a pile burnt down brings none). The burner tells
it in the app; you may mention it in a chat ("Miha's pile smokes blue: the charcoal is nearly done"),
never decide it: the days follow from the calendar.
Every age the village reaches is a celebration of its culture (companion/GAME.md "Ages and their
celebrations"; Primorska: the first evening by the fire, the likof, the veselica, the semenj, the town's
feast): get_village's grown has the day each age came and parties the day its party was had. That evening
and the next the village parties in the app (a short dialog with all the village, its word pack);
congratulate Jan, or ask about the party, in the chat; the app plays it, you don't.
`.trim()

const gamePut = z.object({ rev: z.number().int().min(0), state: z.record(z.string(), z.unknown()) })

export const game: FeatureFactory = ({ game, villagers, culture, cards, grammar }) => ({
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
        // mark it classic): the land stays as it was founded (GAME.md, "The land"); an app from before the school drops
        // the learner's course (companion/COURSE.md) the same way, and one from before the numbers module its numbers
        // (companion/README.md "Numbers"): they stay as the newer app left them
        const was = (current?.state ?? undefined) as Record<string, unknown> | null | undefined
        const kept = Object.fromEntries((['land', 'course', 'numbers'] as const).flatMap(k => (was?.[k] !== undefined && r.data.state[k] === undefined ? [[k, was[k]]] : [])))
        const state = Object.keys(kept).length ? { ...r.data.state, ...kept } : r.data.state
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
        "Summary of the learner's village in the app's game: age, resources, buildings, who is here and met (people.known: all a scene, a request or a line may name) and who waits to be met (people.to_meet), active event, open quests (their module, tries, best try and pass mark, the grammar pages missed, the task a drill helps and task_passed once that task is passed: the drill is optional practice then), help (🤝, earned by helping villagers, spent on a moba), friends, the chest's tools and goods, the village projects (steps done; landmarks_down: finished ones whose landmark is down for the season now, as the maypole after May: not standing in the village or on its square), the calendar (the culture's days on now and the next, a quiet one marked: remembered, not celebrated), spots (the landscape's spots with work over real days, the charcoal pile: its stage today, the day of it, a breakout tonight, the last delivery to the smith), the school's course (the day the class began, the units done and in progress: get_course has the rest), numbers (the numbers module 🔢 Števila, once the unit stevila's intro is done: the ranges open, the newest one's last answers right of how many, 8 of the last 10 opening the next, the numbers missed coming back as asked, the runs), looked_up (the grammar pages Jan looked up on the cheat sheet during exercises in the last two weeks, most first: page, title, n answers given with one of its cards open, the last day; left out when none: work potential, get_course names their units), grown and parties (the day each age came, the day its party was had), recent chronicle.",
      inputSchema: { type: 'object', properties: {} },
      // the calendar as the app has it today: the days on, the next, which are quiet (towns.ts calendarOn); what was looked
      // up on the cheat sheet, in its language (the village's)
      handle: () => {
        const today = localDate(new Date())
        const spots = cultureSpots(culture.dir, culture.id)
        return ok(game.summary(
          id => villagers.get(id)?.name, kept => calendarOn(cultureFestivals(culture.dir, culture.id), today, kept), sheet => topicsLookedUp(sheet, cards.language, today, grammar.all()),
          cycles => cyclesNow(spots, today, spot => ((cycles as Record<string, unknown>)[spot] ?? {}) as Record<string, string[]>, culture.manifest?.language ?? 'sl'),
        ))
      },
    },
  ],
})
