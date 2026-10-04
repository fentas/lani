package si.lanisce.lani.game

import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.ModuleInfo
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.localized
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.bi
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

// Engine API contract. Signatures are fixed (the UI codes against them); bodies are the engine's job.
// Everything here is pure and deterministic given its inputs (randomness from state.seed + day).

/** What the engine may build exercises from. */
data class ContentPool(
    val cards: List<ReviewCard>,
    /** Exercises from published modules, with their module id. */
    val moduleExercises: List<Pair<String, Exercise>> = emptyList(),
    val canSpeak: Boolean = false,
    /** The names of the people in the village today (residents and a visitor); null = everyone (no cast). */
    val present: Set<String>? = null,
)

/** A playable run: an event defence, a local quest, or gathering one resource. */
data class Challenge(
    val title: String,
    val intro: String,
    val emoji: String,
    val exercises: List<Exercise>,
    /** Resource earned by each exercise (same size as [exercises]). */
    val skills: List<Res>,
    /** Spaced-repetition card behind each exercise, or null (same size); lets reviews be persisted. */
    val cardIds: List<String?>,
    /** Null = untimed. */
    val timeLimitSeconds: Int? = null,
    /** Correct answers needed to win (events) or to complete (quests). */
    val passMark: Int = 0,
    /**
     * The word pack whose words the run asks about (a festival's), and the word id under each exercise (same size as
     * [exercises]): the answers are recorded like a pack session's (POST /packs/:id/learn), the known words skipped.
     */
    val pack: String? = null,
    val packWords: List<String> = emptyList(),
    /** The practice module each exercise is from, or null (same size as [exercises] when set): for questions about it. */
    val moduleIds: List<String?> = emptyList(),
    /**
     * The grammar book's pages the run is about as a whole (a request's `grammar`): its intro leads there, and they are
     * met when it starts, as a module's pages are (an exercise's own page is [Exercise.grammar]).
     */
    val pages: List<String> = emptyList(),
    /**
     * The text its questions are about (the day's letter): read first, then hidden while they're asked, "👁 Pokaži
     * besedilo · Show the text" bringing it back ([Peeks]). Null: the questions carry what they're about.
     */
    val text: ReadFirst? = null,
)

data class BuildOption(
    val type: BuildingType,
    val cost: Map<Res, Int>,
    val available: Boolean,
    /** Why not, e.g. "Needs Zaselek" or "Need 12 more 🪨". Null when available. */
    val reason: String?,
    /** Human-readable effect, e.g. "+2 villagers", "+15 defence". */
    val effect: String,
    /**
     * The neighbours' help for it (see [Help]); null when no moba is possible (no 🤝, nobody to come, or the age,
     * the limit or the plots forbid it).
     */
    val moba: Moba? = null,
    /**
     * When [reason] is that the person it brings has nowhere to live (the beehive's beekeeper, and every bed is taken):
     * the way to make room now, a dwelling to build or upgrade ([GameEngine.roomWay]).
     */
    val room: RoomWay? = null,
) {
    /** It can be built with the moba: nothing else stands in the way and the village can pay the rest. */
    val mobaAvailable: Boolean get() = moba?.affordable == true
}

/**
 * How the village makes room for someone a building brings (companion/VILLAGERS.md, "A building brings its person"): a
 * dwelling of [type] to build, or the one standing to upgrade ([upgrade], its id); [short]: the resource it lacks most
 * (gather it first), null when the village can pay for it now; [words]: the upgrade's words, when they aren't all known
 * yet (practise them first: [UpgradeWords.missing]).
 */
data class RoomWay(val type: BuildingType, val upgrade: String? = null, val short: Res? = null, val words: UpgradeWords? = null)

data class Attributes(
    val populationCap: Int,
    val defence: Int,
    /** Storage cap per resource. */
    val caps: Map<Res, Int>,
    /** Earning multiplier per resource (1.0 = none). */
    val production: Map<Res, Float>,
    /** Daily food eaten. */
    val foodUpkeep: Int,
    /** Daily wood burnt by the fire. */
    val woodUpkeep: Int,
)

/**
 * Upgrading one building to [toLevel]. [reason]: what holds it back besides the words (the damage, the age, the
 * resources), null when nothing does; [available] needs [words] done too.
 */
data class UpgradeOption(
    val toLevel: Int,
    val cost: Map<Res, Int>,
    val available: Boolean,
    val reason: String?,
    /** The building's effect after the upgrade, e.g. "+5 👥". */
    val effect: String,
    /** The neighbours' help for it; null when no moba is possible. */
    val moba: Moba? = null,
    /** The building's words it asks for ("Knowledge builds", see [BuildingWords]); null: none asked. */
    val words: UpgradeWords? = null,
) {
    /** The words hold nothing back. */
    val wordsDone: Boolean get() = words?.done != false

    /** It can be done with the neighbours' help: they bring timber and stone, never the words. */
    val mobaAvailable: Boolean get() = moba?.affordable == true && wordsDone
}

/**
 * What stands between the village and [next]: [ok] when nothing does, [missing] as short lines, [steps] as
 * counts (every requirement, met or not) for progress bars.
 */
data class AdvanceCheck(val next: Age?, val ok: Boolean, val missing: List<String>, val steps: List<AgeStep> = emptyList()) {
    /** How far along the way to [next] the village is, 0..1: the mean of the steps' shares. */
    val progress: Float
        get() = if (steps.isEmpty()) (if (ok) 1f else 0f) else steps.sumOf { it.share.toDouble() }.toFloat() / steps.size
}

/**
 * One requirement of the next age: [have] of [need] (buildings standing, a resource in store, words learned, friends;
 * for [Kind.RUSTY] the words learned of those plus the rusty ones, so it's done when none is rusty).
 */
data class AgeStep(
    val kind: Kind,
    val emoji: String,
    val label: String,
    val have: Int,
    val need: Int,
    /** The resource to gather, for a [Kind.RESOURCE] step. */
    val res: Res? = null,
    /** A building that counts is standing but damaged: repair it. */
    val broken: Boolean = false,
) {
    enum class Kind { BUILDING, RESOURCE, WORDS, RUSTY, FRIENDS }

    val done: Boolean get() = have >= need
    val share: Float get() = if (need <= 0) 1f else (have.toFloat() / need).coerceIn(0f, 1f)
}

data class TickResult(val state: GameState, val news: List<LogEntry>)

data class ChallengeResult(
    val won: Boolean,
    val rewards: Map<Res, Int>,
    val losses: Map<Res, Int>,
    /** Ids of buildings damaged. */
    val damaged: List<String>,
    /** Short story line for the result screen and chronicle. */
    val message: String,
    /** 🤝 earned: a villager's request passed. */
    val help: Int = 0,
    /** The good the villager thanked with, if any. */
    val thanks: GoodSpec? = null,
    /** How many of [thanks] came (a festival's honey comes twice). */
    val thanksCount: Int = 1,
)


/**
 * Rules of the village. Every run is credited with [earn] (one entry per answer); [resolveEvent]
 * and [completeQuest] then add their bonus on top.
 */
object GameEngine {
    private const val GATHER_SIZE = 7

    /**
     * A new village with its dice's [seed], founded [now] on [landscape] (its land generated from the same seed, see
     * [Land.of]; the classic valley is for villages from before generated land).
     */
    fun newGame(seed: Long, now: Long, landscape: Landscape = Landscape.VALLEY): GameState = GameState(
        seed = seed,
        resources = mapOf(Res.FOOD to Catalog.START_FOOD, Res.WOOD to Catalog.START_WOOD),
        land = Land.of(landscape, seed),
    ).logged(now, "🔥" to Cultures.current.chronicle.founded.bi())

    /**
     * Applies daily upkeep for each day since [GameState.lastTick] (at most 14), resolves an event
     * past its deadline as lost, may start a new event (at most one active), and keeps up to three
     * open local quests (and, once people sleep under a roof, the shepherd's request to move the tent: [TentMove]).
     * Days are calendar dates; a clock that moved back by more than a day starts the village again from [today]. An
     * older save's palisade gives its plot back (see [offPlots]), its kozolec moves beside its field (see
     * [Placement.hayracksByFields]), and a save from before generated land is marked as standing on the classic valley
     * (see [Land]): only that mark, its look stays as it was.
     */
    fun tick(state: GameState, today: LocalDate, now: Long, pool: ContentPool): TickResult {
        val last = isoDay(state.lastTick)
        val days = if (last == null) 0 else ChronoUnit.DAYS.between(last, today)
        // One day back is a time zone change: wait for the date to catch up. More than that means the clock
        // was wrong (far ahead) at the last tick; waiting for that date would freeze the village, so start
        // again from today, without upkeep for the days in between.
        val rewound = days < -1
        val newDay = last == null || days > 0 || rewound
        var s = Placement.hayracksByFields(state.copy(log = emptyList()).offPlots()) // collects this tick's news
        if (s.land == null) s = s.copy(land = Land.CLASSIC)
        if (rewound) s = s.backFromTheFuture(today, now)
        val news = mutableListOf<Pair<String, String>>()
        if (days > 0) {
            val report = UpkeepReport()
            repeat(days.coerceAtMost(MAX_CATCH_UP_DAYS.toLong()).toInt()) { s = upkeepDay(s, it, report) }
            news += report.lines()
        }
        if (newDay) s = s.copy(lastTick = today.toString(), answersToday = 0)
        if (isoDay(s.foundedOn) == null) s = s.copy(foundedOn = s.lastTick)
        s = s.logged(now, *news.toTypedArray())

        s.event?.takeIf { now >= it.deadline }?.let { s = Events.outcome(s, it, won = false, expired = true, now = now).first }

        val (pruned, expired) = Quests.prune(s, now, pool.present)
        val lines = Cultures.current.quests.lines
        s = pruned.logged(now, *expired.map { it.emoji to lines.expired.bi("giver" to it.giver) }.toTypedArray())

        if (newDay) {
            Events.maybeSpawn(s, today, now, pool)?.let { e ->
                s = s.copy(event = e).logged(now, e.kind.emoji to Events.title(s, e).removePrefix("${e.kind.emoji} "))
            }
            val (filled, added) = Quests.refill(s, today, now, pool)
            s = filled.logged(now, *added.map { it.emoji to lines.asks.bi("giver" to it.giver) }.toTypedArray())
            // people sleep under a roof now: the shepherd asks to move the tent to the pond (once)
            s = TentMove.offer(s, now)
        }
        // the day's surprise at the road: rolled once a day (also for a village that ticked today before surprises
        // existed), not while the date waits to catch up after a time zone change
        val waiting = last != null && days == -1L
        if (!waiting && s.surprise?.on != today.toString()) s = s.copy(surprise = Surprises.roll(s, today))
        for (d in Calendar.on(s, today)) if (d.late == 0 && !d.done && s.age >= Age.TABOR && newDay) {
            s = s.logged(now, d.festival.emoji to Calendar.todayText(d))
        }
        return TickResult(s.copy(log = (state.log + s.log).takeLast(LOG_SIZE)), s.log)
    }

    /**
     * Adds quests for tutor modules that carry a quest block (and drops tutor quests whose module is gone). A drill's link to
     * the task it helps with comes from its quest block's `helps`, else it is found by a grammar page both modules share
     * ([pagesOf]: a module's pages; [QuestQueue.link]).
     */
    fun withTutorQuests(state: GameState, modules: List<ModuleInfo>, now: Long, pagesOf: (String) -> Set<String> = { emptySet() }): GameState {
        val today = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val wanted = modules.mapNotNull { Quests.fromModule(it, state.age, today) }
        val byId = wanted.associateBy { it.id }
        // a module published with `helps` before the app knew it: the link comes to its request now
        val kept = state.quests.filter { it.source == QuestSource.LOCAL || it.id in byId }
            .map { q -> if (q.source == QuestSource.TUTOR && q.helps == null && !q.done) byId[q.id]?.helps?.let { q.copy(helps = it) } ?: q else q }
        val known = kept.map { it.id }.toSet()
        val added = wanted.filter { it.id !in known }
        val short = modules.filter { QuestQueue.SHORT in it.tags }.map { it.id }.toSet()
        return state.copy(quests = QuestQueue.link(kept + added, pagesOf, short))
            .logged(now, *added.map { it.emoji to "${it.giver}: ${it.title}" }.toTypedArray())
    }

    /** The resource an exercise earns. */
    fun resourceOf(exercise: Exercise): Res = when (exercise) {
        is Exercise.Flashcard -> Res.FOOD
        is Exercise.Choice -> if (exercise.audio != null) Res.WOOD else Res.FOOD
        is Exercise.Dictation -> Res.WOOD
        // a gap or word chips by ear are listening
        is Exercise.Cloze -> if (exercise.audio != null) Res.WOOD else Res.STONE
        is Exercise.Reorder -> if (exercise.audio != null) Res.WOOD else Res.STONE
        is Exercise.Multi -> Res.STONE
        is Exercise.Scenario, is Exercise.Free -> Res.WISDOM
        is Exercise.Translate -> if (exercise.grade == "claude") Res.WISDOM else Res.FOOD
        is Exercise.Speak -> Res.WISDOM // conversation
        is Exercise.Unsupported -> Res.FOOD
    }

    /**
     * Credits a finished run: one entry per answered exercise. Returns the new state and what was earned.
     * The first [Catalog.FRESH_ANSWERS] answers of a day pay in full, later ones [Catalog.TIRED_PAY]: daily
     * practice builds the village faster than one long session. [cost]: the percent a run's looks back at its text took
     * off ([Peeks.cost]).
     */
    fun earn(state: GameState, results: List<Pair<Res, Verdict>>, cost: Int = 0): Pair<GameState, Map<Res, Int>> {
        val production = attributes(state).production
        val fresh = freshAnswersLeft(state)
        val raw = results.withIndex()
            .groupBy({ it.value.first }) { (i, rv) ->
                Catalog.earnPerAnswer.getValue(rv.second) * if (i < fresh) 1f else Catalog.TIRED_PAY
            }
            .mapValues { (r, pts) -> (pts.sum() * production.getValue(r)).roundToInt() }
        val counted = state.copy(answersToday = (state.answersToday.toLong() + results.size).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        return credit(counted, Peeks.cut(raw, cost))
    }

    /** Answers left today at full pay (see [earn]). */
    fun freshAnswersLeft(state: GameState): Int = (Catalog.FRESH_ANSWERS - state.answersToday).coerceAtLeast(0)

    fun attributes(state: GameState): Attributes {
        val fx = liveEffects(state)
        val tools = bonusEffect(state) // the chest's tools and the finished projects
        val mood = (state.morale - 50) / 500f
        val crowd = (0.02f * (state.villagers - 1)).coerceAtMost(0.3f)
        return Attributes(
            populationCap = Catalog.BASE_POP + fx.sumOf { it.pop },
            defence = fx.sumOf { it.defence },
            caps = Res.entries.associateWith { r -> Catalog.baseCap[state.age.ordinal] + fx.sumOf { it.caps[r] ?: 0 } + tools.caps },
            production = Res.entries.associateWith { r ->
                (1f + fx.sumOf { (it.production[r] ?: 0f).toDouble() }.toFloat() + (tools.production[r] ?: 0f) + mood + crowd).coerceAtLeast(0.5f)
            },
            // Micka's recipe: the same meals from less (never below one a day for someone living here)
            foodUpkeep = ceil(state.villagers * 1.5 * (1.0 - tools.thrift.coerceIn(0f, 0.5f)) - 1e-6).toInt(),
            woodUpkeep = 2 + state.age.ordinal,
        )
    }

    /**
     * What can be built, and why not. [cast]: the village's villagers (the node's), for who a workplace brings: it waits
     * for a free bed ([homeless]), and then says how to make room ([BuildOption.room]); without a cast nobody is brought.
     * [words]: what a dwelling's upgrade asks for, by its id ([BuildingWords]), for a way to make room by upgrading one.
     */
    fun buildOptions(
        state: GameState, cast: List<Villager> = emptyList(), words: (String) -> UpgradeWords? = { null },
    ): List<BuildOption> = BuildingType.entries.map { t ->
        val spec = Catalog.spec(t)
        val reason = blocker(state, t, cast = cast)
        // A moba only for what could be built but for the resources: never past the age, the limit, the plots or a bed.
        val moba = if (blocker(state, t, emptyMap(), cast) == null) Help.moba(state, spec.cost) else null
        // the way on, when what holds it back is the bed (not the age, the limit or a plot)
        val room = needsBed(state, t, cast)?.takeIf { it == reason }?.let { roomWay(state, keep = if (t.onPlot) 1 else 0, words = words) }
        BuildOption(t, spec.cost, reason == null, reason, Catalog.effectText(spec.effect), moba, room)
    }

    /** Why [t] can't be built for [cost] (its price, or what's left after a moba), or null. */
    private fun blocker(s: GameState, t: BuildingType, cost: Map<Res, Int> = Catalog.spec(t).cost, cast: List<Villager> = emptyList()): String? {
        val spec = Catalog.spec(t)
        val count = s.buildings.count { it.type == t }
        val short = cost.mapValues { (r, n) -> n - s.res(r) }.filterValues { it > 0 }
        return when {
            s.age < spec.minAge -> bi("engine.needsAge", "sl" to spec.minAge.names, "en" to spec.minAge.names)
            spec.max != null && count >= spec.max ->
                if (spec.max == 1) bi("engine.alreadyBuilt") else bi("engine.atMost", "max" to spec.max)
            t.onPlot && Placement.freePlots(s).isEmpty() -> bi("engine.noFreePlot")
            else -> reserved(s, t) ?: needsBed(s, t, cast) ?: if (short.isNotEmpty()) "${bi("engine.need")} ${costText(short)}" else null
        }
    }

    /**
     * Someone [t] would bring ([Residents.brings]: the beehive its beekeeper) who doesn't live here yet and whose age has
     * come, while every bed is taken (the population at [Attributes.populationCap]) and the village can make room
     * ([roomWay]); null otherwise. Without a way to make room (no plot for a dwelling, none to upgrade) the building
     * isn't held back: its person moves in once a bed is free.
     */
    fun homeless(s: GameState, t: BuildingType, cast: List<Villager>): Villager? {
        val v = Residents.brings(t, cast) ?: return null
        if (s.residents.any { it.id == v.id } || !Residents.reached(v.since, s.age)) return null
        if (maxOf(s.villagers, s.residents.size) < attributes(s).populationCap) return null
        return v.takeIf { roomWay(s, keep = if (t.onPlot) 1 else 0) != null }
    }

    /** "🛏️ Čebelar Anton potrebuje prostor · Anton needs somewhere to live (5/5)": [t]'s person has no bed ([homeless]). */
    private fun needsBed(s: GameState, t: BuildingType, cast: List<Villager>): String? {
        val v = homeless(s, t, cast) ?: return null
        val target = si.lanisce.lani.l10n.L10n.pair.target
        // what is said names them as the village does ("Čebelar Anton"), what it means by their own name ("Anton")
        val name = localized { lang -> if (lang == target) v.name else si.lanisce.lani.game.villagers.Mentions.personal(v) }
        return "🛏️ ${bi("engine.needsBed", "name" to name)} (${maxOf(s.villagers, s.residents.size)}/${attributes(s).populationCap})"
    }

    /** The dwellings, the most beds first. */
    private val DWELLINGS = listOf(BuildingType.HOUSE, BuildingType.HUT, BuildingType.TENT)

    /**
     * How the village makes room now: a dwelling that can go up (the most beds first; only on a plot beyond the [keep]
     * free ones the workplace itself needs), else one standing that can be upgraded (the most beds it adds first); of
     * those, one the village can do now, else the first with what it lacks most ([RoomWay.short], to gather) or the
     * words its upgrade asks for ([RoomWay.words], by the dwelling's id: [words]). Null when no dwelling can go up or be
     * upgraded (no plot to spare, the age, the top level).
     */
    fun roomWay(s: GameState, keep: Int = 0, words: (String) -> UpgradeWords? = { null }): RoomWay? {
        fun lacks(cost: Map<Res, Int>) = cost.mapValues { (r, n) -> n - s.res(r) }.filterValues { it > 0 }.maxByOrNull { it.value }?.key
        val spare = Placement.freePlots(s).size > keep
        val builds = DWELLINGS.filter { spare && blocker(s, it, emptyMap()) == null }.map { RoomWay(it, short = lacks(Catalog.spec(it).cost)) }
        val upgrades = s.buildings.filter { it.type in DWELLINGS && !it.damaged }
            .mapNotNull { b -> upgradeOption(s, b.id, words(b.id))?.takeIf { s.age >= Catalog.upgradeAge(it.toLevel) }?.let { b to it } }
            .sortedByDescending { (b, up) -> Catalog.spec(b.type).effect.at(up.toLevel).pop - Catalog.spec(b.type).effect.at(b.level).pop }
            .map { (b, up) -> RoomWay(b.type, b.id, lacks(up.cost), up.words?.takeIf { !it.done }) }
        val all = builds + upgrades
        return all.firstOrNull { it.short == null && it.words == null } ?: all.firstOrNull()
    }

    /**
     * The last free plots are kept for what the next age needs: with no more free plots than required
     * buildings still missing, only those can be built, so the village can never block its own way on.
     * The palisade takes no plot: it is never kept out, and no plot is kept for it.
     */
    private fun reserved(s: GameState, t: BuildingType): String? {
        if (!t.onPlot) return null
        val next = Age.entries.getOrNull(s.age.ordinal + 1) ?: return null
        val rule = Catalog.ageRules[next] ?: return null
        val used = s.buildings.count { it.type.onPlot && it.plot in 0 until PLOTS_PER_AGE[s.age.ordinal] }
        val free = PLOTS_PER_AGE[s.age.ordinal] - used
        val missing = rule.needs.filter { (types, n) -> types.any { it.onPlot } && s.buildings.count { it.type in types } < n }
        val count = missing.sumOf { (types, n) -> n - s.buildings.count { it.type in types } }
        if (count == 0 || free > count || missing.any { (types, _) -> t in types }) return null
        // "Kozolec, Cerkev" in Slovene, "Hayrack, Church" in English: each message names them in its language
        val names = localized { lang -> missing.joinToString(", ") { (types, _) -> types.joinToString("/") { it.names.of(lang) } } }
        return "${bi("engine.plotKeptFor", "sl" to names, "en" to names)} (${next.sl})"
    }

    /**
     * Builds on [plot], a free plot of the age the learner chose (the plot chooser, see [PlotChoice]); without one, on the
     * plot the game suggests: beside what it belongs with (a field by the fields, the kozolec by a field, the square's
     * buildings together; see [Placement]), else the next free one. The palisade takes none: it goes up round the
     * clearing. [withMoba]: the neighbours cover part of the 🪵 and 🪨 for 🤝 (see [Help]). [cast]: the village's
     * villagers, for whom it brings (see [buildOptions]). Returns [state] unchanged if not possible (also for a [plot]
     * that isn't free, or for a building that takes none).
     */
    fun build(
        state: GameState, type: BuildingType, now: Long, withMoba: Boolean = false, cast: List<Villager> = emptyList(), plot: Int? = null,
    ): GameState {
        val cost = Catalog.spec(type).cost
        val moba = if (withMoba) Help.moba(state, cost) else null
        if (blocker(state, type, moba?.rest ?: cost, cast) != null) return state
        if (plot != null && (!type.onPlot || plot !in Placement.freePlots(state))) return state
        val at = if (type.onPlot) plot ?: Placement.plotFor(state, type) ?: return state else NO_PLOT
        val (paid, used) = Help.pay(state, cost, withMoba) ?: return state
        val ids = state.buildings.map { it.id }.toSet()
        val id = generateSequence(state.buildings.size + 1) { it + 1 }.map { "${type.name.lowercase()}-$it" }.first { it !in ids }
        val b = Building(id, type, at, builtAt = now)
        val built = paid.copy(buildings = paid.buildings + b)
        return (if (plot != null) Placement.chosen(built) else built)
            .logged(now, "🔨" to Cultures.current.chronicle.built.bi("building" to type.names) + Help.note(used))
    }

    /**
     * Moves [buildingId] to [plot] of the age (the plot chooser, see [PlotChoice]), free of charge: onto a free plot, or onto
     * another building's, the two swapping places ([Placement.moved]). Only their plots change: levels, damage, dates and
     * whoever lives or works there stay. The palisade and the tent at the pond stand on no plot and don't move. Returns
     * [state] unchanged if not possible.
     */
    fun move(state: GameState, buildingId: String, plot: Int, now: Long): GameState {
        val moved = Placement.moved(state, buildingId, plot) ?: return state
        val b = state.buildings.first { it.id == buildingId }
        val other = state.buildings.firstOrNull { it.id != b.id && it.type.onPlot && it.plot == plot }
        val line = if (other == null) bi("engine.moved", "building" to b.type.names)
        else bi("engine.swapped", "a" to b.type.names, "b" to other.type.names)
        return Placement.chosen(moved).logged(now, "↔️" to line)
    }

    /**
     * Null when the building doesn't exist or is already at the top level. [words]: what the upgrade asks for of the
     * building's words ([BuildingWords.forUpgrade]; one for another building or level is left out); null asks for none.
     * Until they are known it can't be done, with the neighbours' help neither.
     */
    fun upgradeOption(state: GameState, buildingId: String, words: UpgradeWords? = null): UpgradeOption? {
        val b = state.buildings.firstOrNull { it.id == buildingId } ?: return null
        val to = b.level + 1
        if (to > Catalog.MAX_LEVEL) return null
        val cost = Catalog.upgradeCost(b.type, to)
        val short = cost.mapValues { (r, n) -> n - state.res(r) }.filterValues { it > 0 }
        val age = Catalog.upgradeAge(to)
        val blocked = when {
            b.damaged -> bi("engine.repairFirst")
            state.age < age -> bi("engine.needsAge", "sl" to age.names, "en" to age.names)
            else -> null
        }
        val reason = blocked ?: if (short.isNotEmpty()) "${bi("engine.need")} ${costText(short)}" else null
        val moba = if (blocked == null) Help.moba(state, cost) else null
        val asked = words?.takeIf { it.building == buildingId && it.toLevel == to }
        return UpgradeOption(
            to, cost, reason == null && asked?.done != false, reason, Catalog.effectText(Catalog.spec(b.type).effect.at(to)), moba, asked,
        )
    }

    /**
     * Upgrades one level; [withMoba]: with the neighbours' help when there is a moba to call. [words]: what it asks for of
     * the building's words (see [upgradeOption]): not known yet, nothing changes.
     */
    fun upgrade(state: GameState, buildingId: String, now: Long, withMoba: Boolean = false, words: UpgradeWords? = null): GameState {
        val o = upgradeOption(state, buildingId, words) ?: return state
        val moba = withMoba && o.mobaAvailable
        if (!moba && !o.available) return state
        val (paid, used) = Help.pay(state, o.cost, moba) ?: return state
        val b = state.buildings.first { it.id == buildingId }
        return paid.copy(buildings = paid.buildings.map { if (it.id == buildingId) it.copy(level = o.toLevel) else it })
            .logged(now, "⬆️" to Cultures.current.chronicle.upgraded.bi("building" to b.type.names, "level" to o.toLevel) + Help.note(used))
    }

    fun repairCost(state: GameState, buildingId: String): Map<Res, Int> {
        val b = state.buildings.firstOrNull { it.id == buildingId && it.damaged } ?: return emptyMap()
        return Catalog.spec(b.type).cost.mapValues { (_, n) -> (n + 1) / 2 }
    }

    /** The neighbours' help to repair [buildingId]; null when it isn't damaged or no moba is possible. */
    fun repairMoba(state: GameState, buildingId: String): Moba? = repairCost(state, buildingId).takeIf { it.isNotEmpty() }?.let { Help.moba(state, it) }

    /** Kept for callers without a clock: the chronicle entry reuses the last entry's time. Prefer the overload with `now`. */
    fun repair(state: GameState, buildingId: String): GameState = repair(state, buildingId, state.log.lastOrNull()?.at ?: 0)

    fun repair(state: GameState, buildingId: String, now: Long, withMoba: Boolean = false): GameState {
        val cost = repairCost(state, buildingId).ifEmpty { return state }
        val (paid, used) = Help.pay(state, cost, withMoba) ?: return state
        val b = state.buildings.first { it.id == buildingId }
        return paid.copy(buildings = paid.buildings.map { if (it.id == buildingId) it.copy(damaged = false) else it })
            .logged(now, "🔧" to Cultures.current.chronicle.repaired.bi("building" to b.type.names) + Help.note(used))
    }

    /**
     * What stands between the village and the next age. [wordsLearned]: vocabulary answered right at least once;
     * [target]: the learner's target level, which sets the word milestones ([Catalog.ageWords]); [rusty]: words whose
     * last review failed. Any rusty word holds the age back (a step only while there is one), however many words are
     * learned, until a review polishes it: a short one of the rusty words right away, or tomorrow's.
     */
    fun advanceCheck(state: GameState, wordsLearned: Int, target: String = Catalog.DEFAULT_TARGET, rusty: Int = 0): AdvanceCheck {
        val next = Age.entries.getOrNull(state.age.ordinal + 1) ?: return AdvanceCheck(null, false, emptyList())
        val rule = Catalog.ageRules.getValue(next)
        val words = Catalog.wordsFor(next, target)
        val steps = ArrayList<AgeStep>()
        val missing = buildList {
            for ((types, n) in rule.needs) {
                val have = state.buildings.count { it.type in types && !it.damaged }
                val sl = types.joinToString("/") { it.sl }
                val en = types.joinToString("/") { it.en }
                val broken = have < n && state.buildings.any { it.type in types && it.damaged }
                steps += AgeStep(AgeStep.Kind.BUILDING, types.first().emoji, "$sl · $en", have, n, broken = broken)
                if (have >= n) continue
                add((if (n == 1) "$sl · $en" else "$n× $sl · $en ($have/$n)") + if (broken) " 🔧" else "")
            }
            rule.cost.forEach { (r, n) ->
                steps += AgeStep(AgeStep.Kind.RESOURCE, r.emoji, "${r.sl} · ${r.en}", state.res(r), n, res = r)
                if (state.res(r) < n) add("+${n - state.res(r)} ${r.emoji}")
            }
            if (words > 0) steps += AgeStep(AgeStep.Kind.WORDS, "📚", bi("engine.words"), wordsLearned, words)
            if (wordsLearned < words) add(bi("engine.wordsGoal", "words" to words, "known" to wordsLearned))
            if (rusty > 0) {
                steps += AgeStep(AgeStep.Kind.RUSTY, "🔩", bi("engine.rusty", "n" to rusty), wordsLearned, wordsLearned + rusty)
                add(bi("engine.rustyGoal", "n" to rusty, "min" to polishMinutes(rusty)))
            }
            if (rule.friends > 0) {
                val friends = si.lanisce.lani.game.villagers.Bonds.friends(state)
                steps += AgeStep(AgeStep.Kind.FRIENDS, "💞", bi("engine.friends"), friends, rule.friends)
                if (friends < rule.friends) add("${friendsText(rule.friends)} ($friends/${rule.friends})")
            }
        }
        return AdvanceCheck(next, missing.isEmpty(), missing, steps)
    }

    /** "1 prijatelj · 1 friend", "2 prijatelja · 2 friends", "4 prijatelji", "6 prijateljev": ICU's plurals. */
    fun friendsText(n: Int): String = bi("engine.friendsCount", "n" to n)

    /** About how long polishing [rusty] words takes: ten seconds a card, a minute at least. */
    fun polishMinutes(rusty: Int): Int = ((rusty + 5) / 6).coerceAtLeast(1)

    fun advance(state: GameState, wordsLearned: Int, now: Long, target: String = Catalog.DEFAULT_TARGET, rusty: Int = 0): GameState {
        val check = advanceCheck(state, wordsLearned, target, rusty)
        val next = check.next?.takeIf { check.ok } ?: return state
        val (paid, _) = debit(state, Catalog.ageRules.getValue(next).cost)
        val grown = paid.copy(age = next).logged(now, next.emoji to Cultures.current.chronicle.newAge.bi("age" to next.names))
        // the camp opens the road: the first surprise comes the day the village reaches Tabor
        val day = isoDay(grown.lastTick)
        return if (day != null && grown.surprise?.on != grown.lastTick) grown.copy(surprise = Surprises.roll(grown, day)) else grown
    }

    /** A short run that mainly earns [res]: the player's way to choose what the village grows. */
    fun gatherChallenge(state: GameState, res: Res, pool: ContentPool): Challenge {
        val r = rng(state.seed, "gather", res, state.lastTick, state.stats.totalEarned.values.sumOf { it.toLong() }, state.stats.questsDone)
        val picked = Content.pick(res, GATHER_SIZE, pool, r)
        val (title, what) = when (res) {
            Res.FOOD -> "🌾 ${bi("engine.toFields")}" to bi("engine.wordsBringFood")
            Res.WOOD -> "🪵 ${bi("engine.intoForest")}" to bi("engine.listeningBringsWood")
            Res.STONE -> "🪨 ${bi("engine.toQuarry")}" to bi("engine.grammarBreaksStone")
            Res.WISDOM -> "📜 ${bi("engine.underLinden")}" to bi("engine.talkBringsWisdom")
        }
        return challenge(title, listOfNotNull(what, picked.note).joinToString("\n"), res.emoji, picked, null, 0)
    }

    /**
     * How many marks a gathering run with [correct] right answers (of [total]) leaves on the land: one for a
     * run with anything right, two for a very good one (six or more right, or all of a short run), none for a
     * run given up before a single right answer.
     */
    fun gatherMarks(correct: Int, total: Int): Int = when {
        correct <= 0 -> 0
        correct >= GATHER_GREAT || total in 1 until GATHER_GREAT && correct >= total -> 2
        else -> 1
    }

    /**
     * A gathering run for [res] finished with [correct] right answers of [total]: the land keeps the mark (see
     * [WorldMarks]): a tree felled for wood, stone quarried, berries picked, with a chronicle line; two marks
     * for a very good run. Nothing for wisdom, or for a run without a right answer. Marks carry the ISO day,
     * newest last, at most [WorldMarks.MAX] each (the oldest go).
     */
    fun gathered(state: GameState, res: Res, correct: Int, total: Int, today: LocalDate, now: Long): GameState {
        val n = gatherMarks(correct, total)
        if (n == 0 || res == Res.WISDOM) return state
        val day = today.toString()
        val w = state.world
        fun List<String>.marked() = (this + List(n) { day }).takeLast(WorldMarks.MAX)
        val c = Cultures.current.chronicle
        val (world, line) = when (res) {
            Res.WOOD -> w.copy(felled = w.felled.marked()) to ("🪓" to (if (n == 1) c.felled else c.felledMore).bi())
            Res.STONE -> w.copy(quarried = w.quarried.marked()) to ("⛏️" to (if (n == 1) c.quarried else c.quarriedMore).bi())
            Res.FOOD -> w.copy(picked = w.picked.marked()) to ("🧺" to (if (n == 1) c.picked else c.pickedMore).bi())
            Res.WISDOM -> return state
        }
        return state.copy(world = world).logged(now, line)
    }

    /** Six right answers of a seven-question gathering run: a very good run (see [gatherMarks]). */
    private const val GATHER_GREAT = 6

    /** The defence/response run for the active event, or null if there is none. */
    fun eventChallenge(state: GameState, pool: ContentPool): Challenge? {
        val e = state.event ?: return null
        // against the clock: a listening run writes by ear, its time from what its questions take (RunTime)
        val picked = Content.pick(e.kind.skill, Events.questions(e), pool, rng(state.seed, "event-run", e.id), timed = true)
        val total = picked.items.size
        return challenge(
            Events.title(state, e), Events.intro(state, e, total, picked.note), e.kind.emoji, picked,
            RunTime.limit(picked.items.map { it.exercise }), Events.passMark(state, e.kind, total),
        )
    }

    fun resolveEvent(state: GameState, correct: Int, total: Int, now: Long): Pair<GameState, ChallengeResult> {
        val e = state.event ?: return state to ChallengeResult(false, emptyMap(), emptyMap(), emptyList(), "")
        val won = total > 0 && correct >= Events.passMark(state, e.kind, total)
        return Events.outcome(state, e, won, expired = false, now = now)
    }

    /** LOCAL quests only; TUTOR quests are played as their module. */
    fun questChallenge(state: GameState, questId: String, pool: ContentPool): Challenge? {
        val q = state.quests.firstOrNull { it.id == questId && it.source == QuestSource.LOCAL && !it.done } ?: return null
        if (q.id == TentMove.ID) return TentMove.challenge(state, q, pool.canSpeak)
        val byId = pool.cards.associateBy { it.id }
        // a card the request was made from may be gone (the tutor merged or split it): another fitting one stands in
        val kept = q.cardIds.mapNotNull { byId[it] }
        val missing = q.cardIds.size.coerceAtLeast(if (kept.isEmpty()) 5 else 0) - kept.size
        val cards = if (missing <= 0) kept
        else kept + Content.pickCards(pool.cards.filter { c -> kept.none { it.id == c.id } }, missing, rng(state.seed, q.id))
        val picked = Content.pick(q.skill, cards.size, pool, rng(state.seed, "quest-run", q.id), cards)
        val mark = Quests.passMark(picked.items.size)
        val intro = "${q.story}\n$mark/${picked.items.size} ✔ · ${costText(q.reward)}"
        // the page of the rule the request is about: its intro leads there, and playing it meets it
        return challenge("${q.emoji} ${q.title}", intro, q.emoji, picked, null, mark).copy(pages = listOfNotNull(q.grammar))
    }

    /**
     * A quest was played: [correct] of [total]. A pass pays the reward (more with Mojca's chalk), marks it done,
     * earns [Catalog.HELP_QUEST] 🤝 and the giver's thank-you good ([giverId]: their villager id, else guessed
     * from their name; which good: [Chest.thanksFor], by the quest and [today], the day of [now] when not given);
     * a fail pays a fifth and the quest stays open. Either way it counts as a try ([QuestQueue.tried]: the best result, the
     * pages of the rules [missed]).
     */
    fun completeQuest(
        state: GameState, questId: String, correct: Int, total: Int, now: Long, giverId: String? = null, today: LocalDate? = null,
        missed: List<String> = emptyList(),
    ): Pair<GameState, ChallengeResult> {
        val q = state.quests.firstOrNull { it.id == questId && !it.done }
            ?: return state to ChallengeResult(false, emptyMap(), emptyMap(), emptyList(), "")
        val passed = Quests.passed(correct, total)
        val pay = 1f + bonusEffect(state).questPay
        val full = q.reward.mapValues { (_, n) -> (n * pay).roundToInt() }
        val gain = if (passed) full else full.mapValues { (_, n) -> (n / 5).coerceAtLeast(1) }
        val (paid, got) = credit(state, gain)
        val lines = Cultures.current.quests.lines
        val message = "${q.emoji} " + (if (passed) lines.thanked else lines.tryAgain).bi("giver" to q.giver)
        val tried = QuestQueue.tried(q, correct, total, missed)
        var s = paid.copy(quests = paid.quests.map { if (it.id == questId) tried else it })
            .logged(now, q.emoji to message.removePrefix("${q.emoji} "))
        if (!passed) return s to ChallengeResult(false, got, emptyMap(), emptyList(), message)
        val id = giverId ?: Quests.idOf(q.giver)
        val day = today ?: java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val thanks = Chest.thanksFor(id, q.id, state.seed, day)
        val name = Chest.nameOf(s, id)
        s = s.copy(
            quests = s.quests.map { if (it.id == questId) it.copy(done = true) else it },
            stats = s.stats.copy(questsDone = s.stats.questsDone + 1),
        )
        s = Help.earn(Chest.add(s, thanks.id, 1), Catalog.HELP_QUEST)
            .logged(now, thanks.name.emoji to lines.thanksGood.bi("name" to name, "item" to thanks.name.accText))
        if (q.id == TentMove.ID) s = TentMove.move(s, now)
        return s to ChallengeResult(true, got, emptyMap(), emptyList(), message, help = Catalog.HELP_QUEST, thanks = thanks)
    }

    /** Jan helped someone (a talk with a villager, a family answer): +[n] 🤝 (see [Help]). */
    fun helped(state: GameState, n: Int): GameState = Help.earn(state, n)

    /**
     * A scene's happening is done: its dialog was played to the end (see game/scene/Happenings). Pays
     * [reward] once per day, less after [mistakes] (see [happeningPay]), and [Catalog.HELP_HAPPENING] 🤝,
     * records the day in [GameState.happeningsDone] (only today's entries are kept) and writes [entry] to the
     * chronicle. Already done today: [state] stays as it is and nothing is paid.
     */
    fun completeHappening(
        state: GameState, key: String, reward: Map<Res, Int>, mistakes: Int, today: LocalDate, now: Long,
        entry: Pair<String, String>,
    ): Pair<GameState, Map<Res, Int>> {
        val day = today.toString()
        if (state.happeningsDone[key] == day) return state to emptyMap()
        val (paid, got) = credit(state, happeningPay(reward, mistakes))
        val done = paid.happeningsDone.filterValues { it == day } + (key to day)
        // a happening is someone's moment in the village: being there for it is helping (🤝)
        return Help.earn(paid.copy(happeningsDone = done), Catalog.HELP_HAPPENING).logged(now, entry) to got
    }

    // --- projects, festivals, the day's surprise ---------------------------------------------------

    /**
     * Today's step of project [id]: a short practice of the leader's skill, on the project's topics (as a request's).
     * Null when the step can't be done now (see [Projects.option]).
     */
    fun projectChallenge(state: GameState, id: String, pool: ContentPool, today: LocalDate): Challenge? {
        val spec = Projects.spec(id) ?: return null
        val o = Projects.option(state, spec, today).takeIf { it.available } ?: return null
        val r = rng(state.seed, "project", id, o.done, today.toString())
        val (cards, _) = Quests.cardsFor(spec.topics, pool.cards, Catalog.PROJECT_EXERCISES, r)
        val picked = Content.pick(spec.skill, Catalog.PROJECT_EXERCISES, pool, rng(state.seed, "project-run", id, o.done), cards.ifEmpty { null })
        val mark = Quests.passMark(picked.items.size)
        val intro = listOf(o.next!!.task, spec.about, "$mark/${picked.items.size} ✔ · ${Projects.costLine(o)}").joinToString("\n")
        return challenge("${spec.emoji} ${spec.name}", intro, spec.emoji, picked, null, mark)
    }

    /**
     * The festival [id]'s run, while it's on (its day or a grace day) and not celebrated: its pack's words, as choices
     * (see [Calendar]); the answers are recorded for those words like a word pack's ([Challenge.pack]).
     */
    fun festivalChallenge(state: GameState, id: String, today: LocalDate): Challenge? {
        val d = Calendar.open(state, today).firstOrNull { it.festival.id == id } ?: return null
        val f = d.festival
        val run = Calendar.run(f, state.seed, d.day)
        val ex = run.map { it.second }
        val mark = Calendar.passMark(ex.size)
        return Challenge(
            "${f.emoji} ${f.name}", listOf(f.ask, f.about, "$mark/${ex.size} ✔").joinToString("\n"), f.emoji, ex, ex.map { Res.FOOD }, ex.map { null }, null, mark,
            pack = f.pack, packWords = run.map { it.first.id },
        )
    }

    /** Today's surprise's run (not the pedlar's: he sells); null when there's none to play. */
    fun surpriseChallenge(state: GameState, pool: ContentPool, today: LocalDate, name: (String) -> String = { Chest.nameOf(state, it) }): Challenge? {
        val sp = Surprises.playable(state, today) ?: return null
        val ex = Surprises.exercises(sp, state.seed, pool.canSpeak)
        if (ex.isEmpty()) return null
        val skill = Surprises.skill(sp.kind)
        val mark = Surprises.passMark(ex.size)
        return Challenge(
            Surprises.title(sp), listOf(Surprises.intro(sp, name), "$mark/${ex.size} ✔").joinToString("\n"), Surprises.emoji(sp.kind),
            ex, ex.map { skill }, ex.map { null }, null, mark, text = Surprises.text(sp),
        )
    }

    /** A happening's reward after [mistakes] wrong choices: each takes a quarter off, down to a quarter, at least 1. */
    fun happeningPay(reward: Map<Res, Int>, mistakes: Int): Map<Res, Int> {
        val share = (1f - HAPPENING_MISTAKE_COST * mistakes.coerceAtLeast(0)).coerceAtLeast(HAPPENING_MIN_SHARE)
        return reward.filterValues { it > 0 }.mapValues { (_, n) -> (n * share).roundToInt().coerceAtLeast(1) }
    }

    private const val HAPPENING_MISTAKE_COST = 0.25f
    private const val HAPPENING_MIN_SHARE = 0.25f

    private fun challenge(title: String, intro: String, emoji: String, p: Picked, time: Int?, passMark: Int) = Challenge(
        title = title,
        intro = intro,
        emoji = emoji,
        exercises = p.items.map { it.exercise },
        skills = p.items.map { it.skill },
        cardIds = p.items.map { it.cardId },
        timeLimitSeconds = time,
        passMark = passMark,
        moduleIds = p.items.map { it.moduleId },
    )
}
