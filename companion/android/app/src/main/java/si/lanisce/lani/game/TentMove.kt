package si.lanisce.lani.game

import si.lanisce.lani.data.Exercise
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.TentMoveTexts
import kotlin.math.roundToInt

/**
 * "Šotor na novo mesto · A new place for the tent" (companion/GAME.md, "The tent moves to the pond"). Once people sleep
 * under a proper roof (a hut or a house stands, from Zaselek), the shepherd who shelters in the tent (the culture's: Pastir
 * Luka in Primorska) asks, once, to take it out of the village to the pond in the woods below. His request is the
 * description of the place, and the run is about understanding it: where it goes, under what, what is near (the
 * prepositions of place and their cases). Passed, the tent leaves its plot (it is free for a building) and stands at the
 * pond as a small campsite, still a TENT for every rule: the ages, the population, the places and the tent's scene.
 *
 * A special request among the local ones ([ID]): it never expires, it waits while the shepherd is away, it is not one
 * of the three, and it pays like one (and the shepherd's friendship). The words are the culture's (quests.json
 * `tent_move`); a pack without them never asks. Pure, like the engine.
 */
object TentMove {
    /** The request's id among [GameState.quests]. */
    const val ID = "tent-move"

    private val texts: TentMoveTexts? get() = Cultures.current.quests.tentMove

    /** The tent at the pond, once it moved there: on no plot. */
    fun atPond(s: GameState): Building? = s.buildings.firstOrNull { it.type == BuildingType.TENT && it.plot == NO_PLOT }

    /** The tent that moves: the first on a plot (the one the places and the tent's scene call the tent). */
    fun tent(s: GameState): Building? = s.buildings.filter { it.type == BuildingType.TENT && it.plot >= 0 }.minByOrNull { it.plot }

    /** Whether it stands at the pond. */
    fun moved(s: GameState): Boolean = atPond(s) != null

    /**
     * Whether the shepherd asks now: the village is a hamlet or more, a hut or a house stands (people no longer need the
     * tent), a tent stands on a plot and none at the pond, he hasn't asked yet, and he lives here.
     */
    fun due(s: GameState): Boolean {
        val t = texts ?: return false
        return s.age >= Age.ZASELEK &&
            s.buildings.any { it.type == BuildingType.HUT || it.type == BuildingType.HOUSE } &&
            tent(s) != null && !moved(s) &&
            s.quests.none { it.id == ID } &&
            s.residents.any { it.id == Quests.idOf(t.giver) }
    }

    /**
     * The shepherd asks, when he's [due]: the request joins the open ones (never expiring), paying what a request pays, and
     * the chronicle says so like any request's. Otherwise [s] as it is.
     */
    fun offer(s: GameState, now: Long): GameState {
        val t = texts ?: return s
        if (!due(s)) return s
        val skill = Quests.resOf(t.skill) ?: Res.WOOD
        val mult = Quests.ageMultiplier(s.age)
        // like a request's: its skill, and a little of what the village is shortest of
        val caps = GameEngine.attributes(s).caps
        val side = Res.entries.filter { it != skill }.minBy { s.res(it).toFloat() / caps.getValue(it).coerceAtLeast(1) }
        val q = Quest(
            id = ID, giver = t.giver, emoji = t.emoji, title = t.title.bi(), story = t.story.bi(), skill = skill,
            reward = mapOf(skill to (Quests.BASE_REWARD * mult).roundToInt(), side to (Quests.SIDE_REWARD * mult).roundToInt()),
            since = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString(),
        )
        return s.copy(quests = s.quests + q).logged(now, t.emoji to Cultures.current.quests.lines.asks.bi("giver" to t.giver))
    }

    /**
     * The run: a choice for each of the pack's questions about the shepherd's description, in their order, the options
     * shuffled. Heard in his voice when the phone can speak ([canSpeak]: listening), else read ("Luka: »…«").
     */
    fun exercises(seed: Long, canSpeak: Boolean): List<Exercise> {
        val t = texts ?: return emptyList()
        val r = rng(seed, "tent-move")
        val said = t.said.target
        return t.questions.map { q ->
            val opts = q.options.shuffled(r)
            val answer = opts.indexOf(q.options.first())
            // each names the grammar book's page of its rule: answering it unlocks the page, its "why" leads there
            if (canSpeak) Exercise.Choice(q.ask.bi(), opts, answer, explain = q.explain.bi(), audio = said, instruction = t.listen.bi(), grammar = q.grammar)
            else Exercise.Choice("${t.emoji} ${t.heard.inTarget("said" to said)}\n\n${q.ask.bi()}", opts, answer, explain = q.explain.bi(), instruction = t.read.bi(), grammar = q.grammar)
        }
    }

    /** The request [q]'s run, with a request's pass mark; null when the pack has no words for it. */
    fun challenge(s: GameState, q: Quest, canSpeak: Boolean): Challenge? {
        val ex = exercises(s.seed, canSpeak).ifEmpty { return null }
        val mark = Quests.passMark(ex.size)
        return Challenge(
            "${q.emoji} ${q.title}", "${q.story}\n$mark/${ex.size} ✔ · ${costText(q.reward)}", q.emoji,
            ex, ex.map { q.skill }, ex.map { null }, passMark = mark,
        )
    }

    /**
     * The request was passed: the tent leaves its plot for the pond, and the chronicle says so. Nothing when a tent
     * stands there already (or there is none to move).
     */
    fun move(s: GameState, now: Long): GameState {
        if (moved(s)) return s
        val tent = tent(s) ?: return s
        val moved = s.copy(buildings = s.buildings.map { if (it.id == tent.id) it.copy(plot = NO_PLOT) else it })
        return texts?.let { moved.logged(now, BuildingType.TENT.emoji to it.moved.bi()) } ?: moved
    }
}
