package si.lanisce.lani.game.scene

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.culture.CycleDelivery
import si.lanisce.lani.game.culture.CycleStage
import si.lanisce.lani.game.culture.CycleTalk
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.SpotCycle
import si.lanisce.lani.game.culture.StandsEntry
import si.lanisce.lani.game.culture.Text
import si.lanisce.lani.game.culture.WorldFile
import si.lanisce.lani.game.villagers.Arrivals
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * What a village keeps of a spot's cycle ([GameState.cycles], by spot id), by round ("2026-1"): the rounds that burnt down
 * while the learner kept the night watch ([lost]), those they helped save ([saved]), and the deliveries whose yield the
 * smith has used ([used]: the charcoal burnt in a forging). The last [KEEP] of each. An older save has none.
 */
@Serializable
data class CycleMarks(val lost: List<String> = emptyList(), val saved: List<String> = emptyList(), val used: List<String> = emptyList()) {
    fun decided(round: String): Boolean = round in lost || round in saved

    companion object {
        const val KEEP = 12
    }
}

/**
 * How the app draws a spot on a day: its [art] (one of [SpotCycles.ARTS]), how far along it is ([progress], 0 just begun
 * … 1 its last day, across the stages of the same art in a row: the stack grows, the air holes go lower day by day) and
 * the [smoke]'s colour ("white", "yellow", "blue"; null: none of its own).
 */
data class SpotLook(val art: String, val progress: Float = 1f, val smoke: String? = null)

/** A stage of a round as scheduled: the [index]-th of its cycle's stages, from [from] to [until] (both days of it). */
data class StageSpan(val index: Int, val stage: CycleStage, val from: LocalDate, val until: LocalDate) {
    val days: Int get() = (ChronoUnit.DAYS.between(from, until) + 1).toInt()

    operator fun contains(d: LocalDate): Boolean = !d.isBefore(from) && !d.isAfter(until)
}

/**
 * One round of a cycle in a year ([n]: 0 the year's first), its [key] ("2026-0"), its stages as scheduled ([spans]) and the
 * night it breaks out, if it does ([breakout]).
 */
data class CycleRound(val key: String, val year: Int, val n: Int, val spans: List<StageSpan>, val breakout: LocalDate?) {
    val from: LocalDate get() = spans.first().from
    val until: LocalDate get() = spans.last().until

    /** The day its yield is delivered (the first day of the stage that delivers), if one does. */
    val delivery: LocalDate? get() = spans.firstOrNull { it.stage.delivers }?.from

    operator fun contains(d: LocalDate): Boolean = !d.isBefore(from) && !d.isAfter(until)
}

/** What a cycle's day is: a stage of a round, the rest between rounds, the winter outside the season, a round lost. */
enum class CycleKind { STAGE, REST, WINTER, LOST }

/**
 * Where a spot's cycle is on [date]: a [kind] of day, what shows ([shown]: the stage, else the cycle's rest, winter or lost
 * entry; null when it has none), the [round] and its stage as scheduled ([span], also while the round is lost), how long
 * the stage (or the rest, the winter, the burnt round) has been going ([day]: 0 its first) and lasts ([days]), and the
 * [look] the app draws.
 */
data class CycleDay(
    val date: LocalDate,
    val kind: CycleKind,
    val shown: CycleStage?,
    val round: CycleRound?,
    val span: StageSpan?,
    val day: Int,
    val days: Int,
    val look: SpotLook,
) {
    /** "zlaganje", "rest", "winter", "lost": what the day is, for the tutor and the talk's id. */
    val id: String get() = when (kind) {
        CycleKind.STAGE -> span?.stage?.id ?: "stage"
        CycleKind.REST -> "rest"
        CycleKind.WINTER -> "winter"
        CycleKind.LOST -> "lost"
    }
}

/**
 * A spot's cycle over real days (companion/GAME.md, "A spot's cycle"; world.json `spots.<id>.cycle`, [SpotCycle]): which
 * day of which stage it is is a function of the date, the content and the cycle's seed (its `seed`, else the spot's id),
 * never of the learner's visits: a round's stages and the pause after it are rolled from the year and the round's number,
 * so the bridge (spot-cycles.ts) and the app agree to the day, and every village of the culture sees the same pile. What
 * the learner did is kept beside it ([CycleMarks]: a round lost to a breakout, one saved, a delivery used). Pure.
 */
object SpotCycles {
    /** How the app can draw a spot (its [CycleStage.art]); the bridge mirrors them (spot-cycles.ts SPOT_ARTS). */
    val ARTS = listOf("empty", "logs", "stack", "covered", "lit", "smoke", "cooling", "opened", "sacks", "ash")

    /** The smoke's colours, as it goes from damp to done. */
    val SMOKES = listOf("white", "yellow", "blue")

    /** The weather a talk may be for. */
    val SKIES = listOf("rain", "wind")

    /** The most rounds a year has (a guard: a cycle of one-day stages in a long season). */
    const val MAX_ROUNDS = 24

    /** What the smith's forging saves in 🪵 while he has the round's charcoal ([charcoal]): a quarter. */
    const val CHARCOAL_SAVES = 0.25f

    /**
     * The day QA asks the cycles to be on ("kopa:<day>", app/QaHooks), the rest of the game keeping today; while it is set,
     * the keepers come whatever their dice (QA plays them). Null: today.
     */
    @Volatile
    var qaDay: LocalDate? = null

    /** The day the cycles are on when it is [today]: QA's day, else [today]. */
    fun dayOf(today: LocalDate): LocalDate = qaDay ?: today

    // --- the dice -----------------------------------------------------------------------------------------------------

    /** FNV-1a (32 bits) of [parts] joined by "|", as an unsigned number: the bridge computes the same (spot-cycles.ts hash). */
    fun hash(vararg parts: Any): Long {
        var h = 0x811c9dc5.toInt()
        for (b in parts.joinToString("|").encodeToByteArray()) {
            h = h xor (b.toInt() and 0xff)
            h *= 16777619
        }
        return h.toLong() and 0xffffffffL
    }

    /** [parts]'s hash as a share of 1 (0 ≤ x < 1). */
    fun frac(vararg parts: Any): Double = hash(*parts) / 4294967296.0

    /** A whole number from [range] ([least, most], both in; one number: just it) for [parts]. */
    fun pick(range: List<Int>, vararg parts: Any): Int {
        val a = range.firstOrNull() ?: 0
        val b = (range.getOrNull(1) ?: a).coerceAtLeast(a)
        return a + (frac(*parts) * (b - a + 1)).toInt().coerceAtMost(b - a)
    }

    // --- the schedule -------------------------------------------------------------------------------------------------

    /** The rounds worked out, by the cycle (itself: a culture read again has new ones) and "spot/year". */
    private val cache: MutableMap<SpotCycle, ConcurrentHashMap<String, List<CycleRound>>> = java.util.Collections.synchronizedMap(java.util.IdentityHashMap())

    /** The key [c]'s dice roll under: its `seed`, else [spot]. */
    fun seedOf(c: SpotCycle, spot: String): String = c.seed ?: spot

    /** The season's first and last day in [year]. */
    fun season(c: SpotCycle, year: Int): Pair<LocalDate, LocalDate>? {
        val from = StandsEntry.monthDay(c.season.from)?.atYear(year) ?: return null
        val until = StandsEntry.monthDay(c.season.until)?.atYear(year) ?: return null
        return from to until
    }

    /**
     * [c]'s rounds in [year]: the first [SpotCycle.start] days after the season's first day, the next a [SpotCycle.pause]
     * after each, each stage as long as rolled; a round that wouldn't end by the season's last day isn't begun.
     */
    fun rounds(c: SpotCycle, spot: String, year: Int): List<CycleRound> =
        synchronized(cache) { cache.getOrPut(c) { ConcurrentHashMap() } }.getOrPut("$spot/$year") { schedule(c, seedOf(c, spot), year) }

    private fun schedule(c: SpotCycle, seed: String, year: Int): List<CycleRound> {
        val (from, until) = season(c, year) ?: return emptyList()
        if (c.stages.isEmpty() || until.isBefore(from)) return emptyList()
        val out = ArrayList<CycleRound>()
        var d = from.plusDays(pick(c.start, seed, year, "start").toLong())
        for (n in 0 until MAX_ROUNDS) {
            val spans = ArrayList<StageSpan>()
            var at = d
            c.stages.forEachIndexed { i, st ->
                val len = pick(st.days, seed, year, n, st.id).coerceAtLeast(1)
                spans += StageSpan(i, st, at, at.plusDays(len - 1L))
                at = at.plusDays(len.toLong())
            }
            if (spans.last().until.isAfter(until)) break
            out += CycleRound("$year-$n", year, n, spans, breakout(c, seed, year, n, spans))
            d = spans.last().until.plusDays(1L + pick(c.pause, seed, year, n, "pause"))
        }
        return out
    }

    /** The night round [n] of [year] breaks out: one of the days of its breakout's stages, in its share of rounds; else null. */
    private fun breakout(c: SpotCycle, seed: String, year: Int, n: Int, spans: List<StageSpan>): LocalDate? {
        val b = c.breakout ?: return null
        if (frac(seed, year, n, "breakout") >= b.chance) return null
        val days = spans.filter { it.stage.id in b.stages }.flatMap { s -> (0 until s.days).map { s.from.plusDays(it.toLong()) } }
        if (days.isEmpty()) return null
        return days[(frac(seed, year, n, "breakout-day") * days.size).toInt().coerceAtMost(days.size - 1)]
    }

    /** The round of [c] [date] is in, if any. */
    fun roundOn(c: SpotCycle, spot: String, date: LocalDate): CycleRound? = rounds(c, spot, date.year).firstOrNull { date in it }

    /**
     * Where [c] is on [date], given what the village keeps of it ([marks]: a round lost burns from its breakout night on).
     */
    fun on(c: SpotCycle, spot: String, date: LocalDate, marks: CycleMarks = CycleMarks()): CycleDay {
        val rounds = rounds(c, spot, date.year)
        for (r in rounds) {
            val span = r.spans.firstOrNull { date in it } ?: continue
            val b = r.breakout
            if (r.key in marks.lost && b != null && !date.isBefore(b)) {
                val day = ChronoUnit.DAYS.between(b, date).toInt()
                val days = (ChronoUnit.DAYS.between(b, r.until) + 1).toInt()
                return CycleDay(date, CycleKind.LOST, c.lost, r, span, day, days, SpotLook(c.lost?.art ?: "ash", (day + 1f) / days))
            }
            return CycleDay(date, CycleKind.STAGE, span.stage, r, span, ChronoUnit.DAYS.between(span.from, date).toInt(), span.days, look(r, span, date))
        }
        val (from, until) = season(c, date.year) ?: return CycleDay(date, CycleKind.WINTER, c.winter, null, null, 0, 1, SpotLook(c.winter?.art ?: "empty"))
        if (!date.isBefore(from) && !date.isAfter(until)) {
            // between rounds: since the last one ended (or the season began), until the next begins (or the season ends)
            val before = rounds.lastOrNull { it.until.isBefore(date) }
            val after = rounds.firstOrNull { it.from.isAfter(date) }
            val start = before?.until?.plusDays(1) ?: from
            val end = after?.from?.minusDays(1) ?: until
            val day = ChronoUnit.DAYS.between(start, date).toInt()
            return CycleDay(date, CycleKind.REST, c.rest, null, null, day, (ChronoUnit.DAYS.between(start, end) + 1).toInt(), SpotLook(c.rest?.art ?: "empty", (day + 1f) / (ChronoUnit.DAYS.between(start, end) + 1)))
        }
        // the winter: from the day after last season to the day before the next
        val start = if (date.isBefore(from)) season(c, date.year - 1)?.second?.plusDays(1) ?: from.minusYears(1) else until.plusDays(1)
        val end = if (date.isBefore(from)) from.minusDays(1) else season(c, date.year + 1)?.first?.minusDays(1) ?: until.plusYears(1)
        val days = (ChronoUnit.DAYS.between(start, end) + 1).toInt().coerceAtLeast(1)
        val day = ChronoUnit.DAYS.between(start, date).toInt()
        return CycleDay(date, CycleKind.WINTER, c.winter, null, null, day, days, SpotLook(c.winter?.art ?: "empty", (day + 1f) / days))
    }

    /** How a round's stage [span] looks on [date]: its art, as far along as the stages of that art in a row are, its smoke. */
    private fun look(r: CycleRound, span: StageSpan, date: LocalDate): SpotLook {
        val art = span.stage.art
        var first = span.index
        while (first > 0 && r.spans[first - 1].stage.art == art) first--
        var last = span.index
        while (last < r.spans.size - 1 && r.spans[last + 1].stage.art == art) last++
        val from = r.spans[first].from
        val total = (ChronoUnit.DAYS.between(from, r.spans[last].until) + 1).toFloat()
        val done = ChronoUnit.DAYS.between(from, date) + 1f
        return SpotLook(art, (done / total).coerceIn(0f, 1f), span.stage.smoke)
    }

    /**
     * Every stage of [c]'s rounds that ended after [after] and before [before] (both days out), in their order: what the
     * keeper tells a learner who wasn't there. A round lost leaves out what came after its night; the rest and the winter
     * aren't told.
     */
    fun passed(c: SpotCycle, spot: String, after: LocalDate, before: LocalDate, marks: CycleMarks = CycleMarks()): List<StageSpan> {
        if (!after.isBefore(before)) return emptyList()
        // a gap longer than a year: only the last year's
        val from = maxOf(after.year, before.year - 1)
        return (from..before.year).flatMap { y -> rounds(c, spot, y) }.flatMap { r ->
            val lostFrom = r.breakout?.takeIf { r.key in marks.lost }
            r.spans.filter { s -> s.until.isAfter(after) && s.until.isBefore(before) && (lostFrom == null || s.until.isBefore(lostFrom)) }
        }
    }

    /** The rounds of [c] whose yield is delivered on a day from [from] to [to] (both in), with that day. */
    fun deliveries(c: SpotCycle, spot: String, from: LocalDate, to: LocalDate): List<Pair<CycleRound, LocalDate>> {
        if (to.isBefore(from)) return emptyList()
        return (from.year..to.year).flatMap { rounds(c, spot, it) }.mapNotNull { r -> r.delivery?.takeIf { !it.isBefore(from) && !it.isAfter(to) }?.let { r to it } }
    }

    // --- in the village -----------------------------------------------------------------------------------------------

    /** What [state] keeps of [spot]'s cycle. */
    fun marks(state: GameState?, spot: String): CycleMarks = state?.cycles?.get(spot) ?: CycleMarks()

    /** How the app draws [spot] on [today] (QA's day, when it asks for one), when its culture gives it a cycle; null: as ever. */
    fun look(state: GameState?, spot: String, today: LocalDate, world: WorldFile = Cultures.current.world): SpotLook? {
        val c = world.spots[spot]?.cycle ?: return null
        return on(c, spot, dayOf(today), marks(state, spot)).look
    }

    /** Where [spot]'s cycle is on [today] (QA's day, when it asks for one); null when it has none. */
    fun day(state: GameState?, spot: String, today: LocalDate, world: WorldFile = Cultures.current.world): CycleDay? {
        val c = world.spots[spot]?.cycle ?: return null
        return on(c, spot, dayOf(today), marks(state, spot))
    }

    /** [state] with round [round] of [spot] marked [lost] or saved (once: a round decided stays as it was). */
    fun decide(state: GameState, spot: String, round: String, lost: Boolean): GameState {
        val m = marks(state, spot)
        if (m.decided(round)) return state
        val next = if (lost) m.copy(lost = (m.lost + round).takeLast(CycleMarks.KEEP)) else m.copy(saved = (m.saved + round).takeLast(CycleMarks.KEEP))
        return state.copy(cycles = state.cycles + (spot to next))
    }

    /**
     * A round's yield the receiver has now ([CycleDelivery.to]: the smith's charcoal): delivered on [on] ([date]) by the
     * round [round] of [spot], at most [CycleDelivery.days] ago and not used yet; [lost]: the round burnt down, so nothing
     * came (what the receiver says then, nothing to use).
     */
    data class Yield(val spot: String, val round: String, val date: LocalDate, val delivery: CycleDelivery, val lost: Boolean)

    /** The latest delivery of a spot whose receiver is [to] (the smith), as of [today], while it lasts; null: none. */
    fun yieldFor(state: GameState?, to: String, today: LocalDate, world: WorldFile = Cultures.current.world): Yield? {
        val day = dayOf(today)
        return world.spots.mapNotNull { (spot, s) ->
            val c = s.cycle ?: return@mapNotNull null
            val d = c.delivery?.takeIf { it.to == to } ?: return@mapNotNull null
            val m = marks(state, spot)
            deliveries(c, spot, day.minusDays(d.days - 1L), day).lastOrNull()?.let { (r, on) -> Yield(spot, r.key, on, d, r.key in m.lost) }
        }.maxByOrNull { it.date }
    }

    /** The charcoal the smith ([to]) has from a delivery, to save 🪵 in a forging: a yield not lost and not used yet. */
    fun charcoal(state: GameState?, to: String, today: LocalDate, world: WorldFile = Cultures.current.world): Yield? =
        yieldFor(state, to, today, world)?.takeIf { !it.lost && it.round !in marks(state, it.spot).used }

    /** [state] with [y]'s yield used (the forging burnt the charcoal). */
    fun use(state: GameState, y: Yield): GameState {
        val m = marks(state, y.spot)
        if (y.round in m.used) return state
        return state.copy(cycles = state.cycles + (y.spot to m.copy(used = (m.used + y.round).takeLast(CycleMarks.KEEP))))
    }

    /**
     * What villager [who] says on their card while a delivery to them lasts ([CycleDelivery.says]; a round lost: its
     * [CycleDelivery.waits]), the day's pick; null when none is on.
     */
    fun says(state: GameState?, who: String, today: LocalDate, world: WorldFile = Cultures.current.world): Text? {
        val y = yieldFor(state, who, today, world) ?: return null
        val lines = if (y.lost) y.delivery.waits else y.delivery.says
        if (lines.isEmpty()) return null
        return lines[Math.floorMod(ChronoUnit.DAYS.between(y.date, dayOf(today)), lines.size.toLong()).toInt()]
    }

    /**
     * The chronicle's lines of the deliveries since the last tick ([last], out) up to [today] (in): the round's yield to its
     * receiver ("♨️ Oglar Miha je pripeljal oglje kovaču Tonetu") when he lives in the village (the smith, once there is a
     * smithy), none for a round that burnt down.
     */
    fun news(state: GameState, last: LocalDate?, today: LocalDate, world: WorldFile = Cultures.current.world): List<Pair<String, String>> {
        if (last == null || !today.isAfter(last)) return emptyList()
        return world.spots.flatMap { (spot, s) ->
            val c = s.cycle ?: return@flatMap emptyList()
            val d = c.delivery?.takeIf { d -> state.residents.any { it.id == d.to } } ?: return@flatMap emptyList()
            val m = marks(state, spot)
            deliveries(c, spot, maxOf(last.plusDays(1), today.minusDays(d.days.toLong())), today).filter { (r, _) -> r.key !in m.lost }.map { (s.emoji ?: "♨️") to d.line.bi() }
        }
    }

    // --- the keeper's talk --------------------------------------------------------------------------------------------

    /** The lines of [levels] (a level → {"lines": […]}) for a learner at [level]: the highest it has up to theirs, else its easiest. */
    fun linesAt(levels: JsonObject?, level: String): List<JsonElement> {
        if (levels == null) return emptyList()
        val lv = Arrivals.levelFor(levels.keys, level) ?: return emptyList()
        return ((levels[lv] as? JsonObject)?.get("lines") as? JsonArray).orEmpty()
    }

    /**
     * Of [talks], the ones for now: at [time] (a talk with parts of the day only then), and those for the weather [sky] has
     * (rain, wind) first when there are; none fits: those of any time.
     */
    fun fitting(talks: List<CycleTalk>, time: TimeOfDay, dawn: Boolean, sky: Set<String>): List<CycleTalk> {
        val t = time.name.lowercase()
        val now = talks.filter { it.times.isEmpty() || t in it.times || dawn && "dawn" in it.times }.filter { it.sky == null || it.sky in sky }
        val weather = now.filter { it.sky != null }
        return weather.ifEmpty { now.filter { it.sky == null } }.ifEmpty { talks.filter { it.times.isEmpty() && it.sky == null } }
    }

    /** The talk of [talks] a learner hears on their [at]-th evening: the next one each time, so a visit after another hears another. */
    fun variant(talks: List<CycleTalk>, at: Int): CycleTalk? = if (talks.isEmpty()) null else talks[Math.floorMod(at, talks.size)]

    /**
     * The sky the keeper talks of today: "rain", "wind" when the day's sky (a scene's dialog) brought them over the village,
     * both in a storm.
     */
    fun skyOf(state: GameState?, today: LocalDate): Set<String> {
        val s = DaySky.of(state, today)
        val storm = state?.event?.kind == si.lanisce.lani.game.EventKind.STORM
        return buildSet {
            if (storm || s.rain >= 0.3f || s.snow >= 0.3f) add("rain")
            if (storm || s.wind >= 0.3f) add("wind")
        }
    }
}
