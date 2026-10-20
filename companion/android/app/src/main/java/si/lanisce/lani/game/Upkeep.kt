package si.lanisce.lani.game

import si.lanisce.lani.game.culture.Cultures
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.random.Random

internal const val DAY_MS = 86_400_000L
internal const val HOUR_MS = 3_600_000L
internal const val LOG_SIZE = 50
internal const val MAX_CATCH_UP_DAYS = 14
internal const val SOFT_MORALE_FLOOR = 30

/**
 * Deterministic randomness: the same seed and parts always give the same sequence, in every process.
 * Parts are strings, numbers or enums (an enum's own hashCode changes from one app start to the next).
 */
internal fun rng(seed: Long, vararg parts: Any?): Random =
    Random(parts.fold(seed) { h, p -> h * 1_000_003 + stableHash(p) })

internal fun stableHash(p: Any?): Int = when (p) {
    null -> 0
    is Enum<*> -> p.name.hashCode()
    else -> p.hashCode()
}

/** An ISO date (yyyy-MM-dd), or null when empty or unreadable. */
internal fun isoDay(s: String): LocalDate? = runCatching { LocalDate.parse(s) }.getOrNull()

/**
 * The clock was far ahead when the village last ticked (a wrong phone date): bring the dates and deadlines
 * that were set then back to [today], so the village does not wait for that day to come round again.
 */
internal fun GameState.backFromTheFuture(today: LocalDate, now: Long): GameState = copy(
    foundedOn = isoDay(foundedOn)?.takeIf { it <= today }?.toString() ?: today.toString(),
    event = event?.let { e ->
        val hours = Events.BASE_DEADLINE_HOURS + warningHours(this)
        if (e.startedAt > now) e.copy(startedAt = now, deadline = now + hours * HOUR_MS) else e
    },
    quests = quests.map { q -> if (q.expiresAt > now + Quests.LIFETIME_DAYS * DAY_MS) q.copy(expiresAt = now + Quests.LIFETIME_DAYS * DAY_MS) else q },
)

/**
 * A save from before the palisade stood round the clearing: it stood on a plot then. It gives the plot back (see
 * [BuildingType.onPlot]); anything else stays as it was.
 */
internal fun GameState.offPlots(): GameState =
    if (buildings.all { it.type.onPlot || it.plot == NO_PLOT }) this
    else copy(buildings = buildings.map { if (it.type.onPlot) it else it.copy(plot = NO_PLOT) })

internal fun liveEffects(s: GameState): List<Effect> =
    s.buildings.filterNot { it.damaged }.map { Catalog.spec(it.type).effect.at(it.level) }

/** The morale the village settles at: 50, plus buildings (the linden, the well, the church), gifts (candles, keepsakes) and projects (the maypole …). */
internal fun moraleTarget(s: GameState) = (50 + liveEffects(s).sumOf { it.morale } + bonusEffect(s).morale).coerceAtMost(100)

/** Extra hours to answer an event: the watchtower, and the radio and the horn. */
internal fun warningHours(s: GameState) = liveEffects(s).sumOf { it.warningHours } + bonusEffect(s).warningHours

/**
 * What the tools in the chest, the finished village projects and the treasures' keepsakes add, together (see
 * [Chest.effect], [Projects.effect], [Treasure.effect]).
 */
internal fun bonusEffect(s: GameState): ToolEffect = Chest.effect(s) + Projects.effect(s) + Treasure.effect(s)

/** Adds [gain] clamped to storage caps; returns the new state and what was actually credited. */
internal fun credit(s: GameState, gain: Map<Res, Int>): Pair<GameState, Map<Res, Int>> {
    val caps = GameEngine.attributes(s).caps
    val credited = gain.mapValues { (r, n) -> n.coerceAtMost((caps.getValue(r) - s.res(r)).coerceAtLeast(0)) }
        .filterValues { it > 0 }
    val stats = s.stats.copy(totalEarned = s.stats.totalEarned.plusSaturated(credited))
    val after = s.copy(resources = s.resources.sumWith(credited), stats = stats)
    // wood brought in lights the hearths that went out today again (companion/GAME.md "Hearths")
    return (if ((credited[Res.WOOD] ?: 0) > 0) Hearths.relight(after) else after) to credited
}

/** Lifetime totals only grow: they stop at Int.MAX_VALUE instead of wrapping to a negative number. */
internal fun Map<Res, Int>.plusSaturated(other: Map<Res, Int>): Map<Res, Int> =
    (keys + other.keys).associateWith { ((this[it] ?: 0).toLong() + (other[it] ?: 0)).coerceIn(0, Int.MAX_VALUE.toLong()).toInt() }
        .filterValues { it != 0 }

/** Takes [loss] away, never below zero; returns the new state and what was actually lost. */
internal fun debit(s: GameState, loss: Map<Res, Int>): Pair<GameState, Map<Res, Int>> {
    val taken = loss.mapValues { (r, n) -> n.coerceIn(0, s.res(r).coerceAtLeast(0)) }.filterValues { it > 0 }
    return s.copy(resources = s.resources.sumWith(taken.mapValues { -it.value })) to taken
}

internal fun GameState.logged(now: Long, vararg entries: Pair<String, String>): GameState =
    copy(log = (log + entries.map { (e, t) -> LogEntry(now, e, t) }).takeLast(LOG_SIZE))

/** Counters of what happened over several upkeep days, for one summary in the chronicle. */
internal class UpkeepReport {
    var days = 0
    var hungry = 0
    var cold = 0
    var joined = 0
    var left = 0
    /** The chronicle's lines of the days (the charcoal pile's wood given, a pile not built). */
    val news = ArrayList<Pair<String, String>>()
}

/**
 * One day of upkeep, of [date]. From the fourth missed day on ([dayIndex] ≥ 3) villagers forage and the hearths burn half
 * (see [Hearths.day]): half the consumption and much smaller losses, so a long break is recoverable.
 */
internal fun upkeepDay(s0: GameState, dayIndex: Int, report: UpkeepReport, date: LocalDate = isoDay(s0.lastTick)?.plusDays(1) ?: LocalDate.now()): GameState {
    val a = GameEngine.attributes(s0)
    val soft = dayIndex >= 3
    val foodNeed = if (soft) ceil(a.foodUpkeep / 2.0).toInt() else a.foodUpkeep
    val fed = s0.res(Res.FOOD) >= foodNeed
    // the hearths: their wood in order, the smith's charcoal, the charcoal pile's wood when it is due
    val hearths = Hearths.day(s0, date, soft)
    val s = hearths.state
    report.news += hearths.news

    val target = moraleTarget(s)
    var morale = when {
        !fed -> s.morale - if (soft) 3 else 10
        s.morale < target -> (s.morale + 5).coerceAtMost(target)
        else -> (s.morale - 2).coerceAtLeast(target) // festival glow fades slowly
    }
    // the hearths out: their share of the village's
    morale -= hearths.morale
    // A long break stops hurting at SOFT_MORALE_FLOOR: coming back must feel recoverable.
    morale = morale.coerceIn(0, 100)
    if (soft) morale = morale.coerceAtLeast(minOf(s.morale, SOFT_MORALE_FLOOR))

    var low = if (morale < 25) s.lowMoraleDays + 1 else 0
    var villagers = s.villagers
    if (low >= 3 && villagers > 1) {
        villagers--
        low = 0
        report.left++
    } else if (fed && morale >= 50 && villagers < a.populationCap) {
        villagers++
        report.joined++
    }

    report.days++
    if (!fed) report.hungry++
    if (hearths.cold) report.cold++
    val resources = s.resources.sumWith(mapOf(Res.FOOD to -foodNeed.coerceAtMost(s.res(Res.FOOD))))
    return s.copy(resources = resources, morale = morale, villagers = villagers, lowMoraleDays = low)
}

internal fun UpkeepReport.lines(): List<Pair<String, String>> = buildList {
    val c = Cultures.current.chronicle
    if (days > 1) add("🌙" to c.waited.bi("days" to days))
    if (hungry > 0) add("🍂" to c.hungry.bi("days" to hungry))
    if (cold > 0) add("🥶" to c.cold.bi("days" to cold))
    if (joined > 0) add("🧑" to c.joined.bi("n" to joined))
    if (left > 0) add("😢" to c.left.bi("n" to left))
    addAll(news)
}
