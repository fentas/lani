// The village game ("Moja vas"): the app saves its state here, the tutor reads a summary.
import { z } from 'zod'
import { topicsLookedUp } from '../cards'
import { ok } from '../tool'
import { cultureFestivals, cultureSpots } from '../cultures'
import { cyclesNow } from '../spot-cycles'
import { givesWood, kopaWood } from '../game'
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
The app's map shows a few requests at a time (3 on a phone, 4 on a tablet, more while Jan is on a
roll), one per villager, the best for Jan first (what the next age needs, their weak and due items,
one almost passed, your newest); the rest wait in the Scroll under "Later" (get_village: on_map for
the ones shown). A run clears its "!" either way: passed it's done; below the pass mark it comes
back the next morning (back), and the villager rests a few hours after any run (resting). Keep
about four open tasks of yours, spread over the cast; don't give a villager a new task right after
one of theirs was finished; rather extend an open task (a new version of its module) than add
another (publish_module says when a giver rests or has tasks open). A session_end with data.quest
is a request's run: passed or not, right of total against pass_mark, missed (the indices of
data.results Jan got wrong), missed_pages; persist it as any session and put what was missed into
the reviews (each missed item an error and a review item of its own, so it comes back in Jan's
review); below the mark (comes_back: tomorrow) the request waits for tomorrow by itself: don't
publish it again. For a
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
get_village's hearths: every hut, house, the school and the forge has its own fire, and so has the
centre (the campfire, from Vas the stone fire ring lit for the evening, from Trg the square's fire
basket; a kres on feast evenings). Each day the hearths burn their wood (wood_a_day) and the ones on
the edge go out first when it runs short (out, lit of all); the forge burns Miha's charcoal (until
when). Miha's pile takes the village's wood once a round (spots.<pile>.wood: how much, the days, given
or waiting); without it he waits, and a pile not built brings the smith no charcoal. When hearths are
out or Miha waits, a listening round brings wood ("Miha is waiting for 120 wood for his pile: a
listening drill would get it to him"). Never promise wood: the app pays it.
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
        // (companion/README.md "Numbers"), and one from before the hearths its hearths (GAME.md "Hearths": the first day it
        // kept them, which piles count as given): they stay as the newer app left them
        const was = (current?.state ?? undefined) as Record<string, unknown> | null | undefined
        const kept = Object.fromEntries((['land', 'course', 'numbers', 'hearths'] as const).flatMap(k => (was?.[k] !== undefined && r.data.state[k] === undefined ? [[k, was[k]]] : [])))
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
        "Summary of the learner's village in the app's game: age, resources, buildings, the hearths (each hut, house, the school, the forge and the centre its own fire: lit of all, the ones out, the wood a day and the days the store lasts, the forge's charcoal), who is here and met (people.known: all a scene, a request or a line may name) and who waits to be met (people.to_meet), active event, open quests (their module, tries, best try and pass mark, the grammar pages missed, the task a drill helps and task_passed once that task is passed: the drill is optional practice then; on_map: the app's map shows it today; back: tried below the pass mark, when it comes back), resting (who rests after a run of theirs, until when: no new task for them now), help (🤝, earned by helping villagers, spent on a moba), friends, the chest's tools and goods, the village projects (steps done; landmarks_down: finished ones whose landmark is down for the season now, as the maypole after May: not standing in the village or on its square), the calendar (the culture's days on now and the next, a quiet one marked: remembered, not celebrated), spots (the landscape's spots with work over real days, the charcoal pile: its stage today, the day of it, a breakout tonight, the last delivery to the smith, the village's wood for it: given or waited for), the school's course (the day the class began, the units done and in progress: get_course has the rest), numbers (the numbers module 🔢 Števila, once the unit stevila's intro is done: the ranges open, the newest one's last answers right of how many, 8 of the last 10 opening the next, the numbers missed coming back as asked, the runs), looked_up (the grammar pages Jan looked up on the cheat sheet during exercises in the last two weeks, most first: page, title, n answers given with one of its cards open, the last day; left out when none: work potential, get_course names their units), grown and parties (the day each age came, the day its party was had), recent chronicle.",
      inputSchema: { type: 'object', properties: {} },
      // the calendar as the app has it today: the days on, the next, which are quiet (towns.ts calendarOn); what was looked
      // up on the cheat sheet, in its language (the village's)
      handle: () => {
        const today = localDate(new Date())
        const spots = cultureSpots(culture.dir, culture.id)
        return ok(game.summary(
          id => villagers.get(id)?.name, kept => calendarOn(cultureFestivals(culture.dir, culture.id), today, kept), sheet => topicsLookedUp(sheet, cards.language, today, grammar.all()),
          (cycles, s) =>
            cyclesNow(
              spots, today, spot => ((cycles as Record<string, unknown>)[spot] ?? {}) as Record<string, string[]>, culture.manifest?.language ?? 'sl',
              (_spot, _c, r) => givesWood(s, r.woodUntil), kopaWood(s?.age),
            ),
          // the forge burns charcoal where a spot's cycle brings it to the smith (Primorska's kopa), else wood
          Object.values(spots).some(sp => sp?.cycle?.delivery),
        ))
      },
    },
  ],
})
