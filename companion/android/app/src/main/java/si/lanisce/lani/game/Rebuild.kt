package si.lanisce.lani.game

import si.lanisce.lani.game.BuildingType.BEEHIVE
import si.lanisce.lani.game.BuildingType.FIELD
import si.lanisce.lani.game.BuildingType.HOUSE
import si.lanisce.lani.game.BuildingType.HUT
import si.lanisce.lani.game.BuildingType.KOZOLEC
import si.lanisce.lani.game.BuildingType.TENT
import si.lanisce.lani.game.BuildingType.WELL
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.bi
import kotlin.math.roundToInt

/**
 * A tent growing into a hut, or a hut into a house ([Rebuild.grow]): building [building] becomes [to] at its level, for
 * [cost]; [effect] what it gives then ("+5 👥"); [reason] what holds it back (the damage, the resources), null when nothing
 * does.
 */
data class GrowOption(val building: String, val to: BuildingType, val cost: Map<Res, Int>, val reason: String?, val effect: String) {
    val available: Boolean get() = reason == null
}

/**
 * How a building that is held back only by the plots (every plot of the age built) can still go up ([Rebuild.plotWay]),
 * one tap from its card on the build sheet.
 */
sealed interface PlotWay {
    /** The building of the village the way goes through. */
    val building: String

    /** That dwelling ([from]) grows into the building wanted ([to]): a tent into a hut, a hut into a house. */
    data class Grow(override val building: String, val from: BuildingType, val to: BuildingType) : PlotWay

    /** That building ([what]), which the next age doesn't need, comes down and the one wanted goes up on its plot. */
    data class Replace(override val building: String, val what: BuildingType) : PlotWay
}

/**
 * "Nobody blocks their own way" (companion/GAME.md "Buildings", "When every plot is built"): the plots of an age are few,
 * and a village whose plots are all built (tents in every slot) must still be able to raise what the next age needs. The
 * last free plots are kept for that already ([GameEngine.buildOptions]), but only for the next age, and Mesto needs nine
 * buildings where the market town brings six plots. So besides the free plots there are two ways:
 *
 * - **Growing up** ([grow]): a tent grows into a hut, a hut into a house, on its own plot. Its level stays (a level
 *   reached stays); it costs what the bigger building costs more at that level ([growCost]); like building new it asks
 *   for no words, and its next upgrade asks for the new building's.
 * - **Replacing** ([replace]): a building comes down and another goes up on its plot, paid as a new one. Who lives in the
 *   village stays: the people are the village's, not a building's. Only a building of the common kinds ([REPLACEABLE])
 *   that the next age doesn't need ([keeps]), not the village's first tent (the shepherd moves it to the pond, [TentMove]),
 *   nor the last workplace of someone who lives here (the beekeeper's bee house).
 *
 * A building held back only by the plots carries its way ([plotWay]): a dwelling to grow when that's what is wanted, else
 * the building that is cheapest to lose. `BuildOrdersTest` plays thousands of random build orders in every culture and
 * finds no village that can't reach the next age's buildings by these ways. Pure, like the engine.
 */
object Rebuild {
    /** What each dwelling grows into. */
    val GROWS: Map<BuildingType, BuildingType> = mapOf(TENT to HUT, HUT to HOUSE)

    /**
     * The kinds that can be replaced: the common ones a village has several of. The village's own buildings (one of a kind:
     * the linden, the church, the school, the market, the watchtower, the smithy, the palisade) stay; every age after the
     * one that opens them needs them anyway.
     */
    val REPLACEABLE: Set<BuildingType> = setOf(TENT, FIELD, WELL, HUT, KOZOLEC, BEEHIVE, HOUSE)

    /** The build cost times this is what a building and its upgrades to a level cost in all: 1, 3, 6, 11, 18. */
    fun levelsFactor(level: Int): Float =
        (1..level.coerceIn(1, Catalog.MAX_LEVEL)).sumOf { k -> if (k == 1) 1.0 else Catalog.upgradeFactor[k].toDouble() }.toFloat()

    /**
     * What growing [from] into [to] at [level] costs: what the bigger building costs more, built and upgraded to that
     * level (a tent's 25 🌾 15 🪵 against a hut's 30 🌾 40 🪵 10 🪨: 5 🌾 25 🪵 10 🪨 at level 1, three times that at 2).
     */
    fun growCost(from: BuildingType, to: BuildingType, level: Int): Map<Res, Int> {
        val f = levelsFactor(level)
        val a = Catalog.spec(from).cost
        val b = Catalog.spec(to).cost
        return Res.entries.associateWith { r -> (((b[r] ?: 0) - (a[r] ?: 0)).coerceAtLeast(0) * f).roundToInt() }.filterValues { it > 0 }
    }

    /** The village's first tent while none stands at the pond: it stays a tent where it is, the shepherd moves it there ([TentMove]). */
    fun firstTent(s: GameState, b: Building): Boolean = b.type == TENT && !TentMove.moved(s) && TentMove.tent(s)?.id == b.id

    /**
     * Building [id] growing up ([GROWS]): null when it doesn't (not a tent or a hut, the tent at the pond or the village's
     * first tent, or the age doesn't have what it grows into yet); else its price and what holds it back.
     */
    fun growOption(s: GameState, id: String): GrowOption? {
        val b = s.buildings.firstOrNull { it.id == id } ?: return null
        val to = GROWS[b.type] ?: return null
        if (b.plot == NO_PLOT || firstTent(s, b) || s.age < Catalog.spec(to).minAge) return null
        val cost = growCost(b.type, to, b.level)
        val short = cost.mapValues { (r, n) -> n - s.res(r) }.filterValues { it > 0 }
        val reason = when {
            b.damaged -> bi("engine.repairFirst")
            short.isNotEmpty() -> "${bi("engine.need")} ${costText(short)}"
            else -> null
        }
        return GrowOption(b.id, to, cost, reason, Catalog.effectText(Catalog.spec(to).effect.at(b.level)))
    }

    /**
     * Building [id] grows up ([growOption]): the same plot and level, a new building of the bigger kind (its own id, built
     * [now], so a house that grew gets the room of whoever moved in next, as a new house does). [s] unchanged when it can't.
     */
    fun grow(s: GameState, id: String, now: Long): GameState {
        val o = growOption(s, id)?.takeIf { it.available } ?: return s
        val b = s.buildings.first { it.id == id }
        val (paid, _) = debit(s, o.cost)
        val grown = b.copy(id = GameEngine.newId(s, o.to), type = o.to, builtAt = now)
        return paid.copy(buildings = paid.buildings.map { if (it.id == b.id) grown else it })
            .logged(now, o.to.emoji to bi("engine.grew", "a" to b.type.names, "b" to o.to.names))
    }

    /**
     * Why building [b] stays where it is, or null when it may be replaced: it isn't of a common kind or stands on no plot of
     * the age, it is the village's first tent, the next age needs it (its kind counts for the next age and no more of it
     * stand than it needs), or it is the last workplace of someone who lives here ([cast]: the bee house of the beekeeper).
     */
    fun keeps(s: GameState, b: Building, cast: List<Villager> = emptyList()): Keep? = when {
        b.type !in REPLACEABLE || b.plot !in 0 until PLOTS_PER_AGE[s.age.ordinal] -> Keep.KIND
        firstTent(s, b) -> Keep.FIRST_TENT
        needed(s, b.type) -> Keep.NEEDED
        s.buildings.count { it.type == b.type } <= 1 && Residents.brings(b.type, cast)?.let { v -> s.residents.any { it.id == v.id } } == true -> Keep.WORKPLACE
        else -> null
    }

    /** Why a building stays ([keeps]). */
    enum class Keep { KIND, FIRST_TENT, NEEDED, WORKPLACE }

    /** Whether the next age needs every building of [type] that stands: its kind counts for it, and no more stand than it needs. */
    fun needed(s: GameState, type: BuildingType): Boolean {
        val next = Age.entries.getOrNull(s.age.ordinal + 1) ?: return false
        val rule = Catalog.ageRules[next] ?: return false
        return rule.needs.any { (types, n) -> type in types && s.buildings.count { it.type in types } <= n }
    }

    /** Whether building [id] may be replaced ([keeps]). */
    fun replaceable(s: GameState, id: String, cast: List<Villager> = emptyList()): Boolean =
        s.buildings.firstOrNull { it.id == id }?.let { keeps(s, it, cast) == null } == true

    /**
     * What can go up in place of building [id] ([replaceable]): the build options of the village without it, its plot free
     * ([GameEngine.buildOptions]: the last plots kept for the next age, a bed for who a workplace brings, the price), of the
     * other kinds that stand on a plot, the age has and aren't at their most. Empty when it can't be replaced.
     */
    fun replaceOptions(
        s: GameState, id: String, cast: List<Villager> = emptyList(), words: (String) -> UpgradeWords? = { null },
    ): List<BuildOption> {
        val b = s.buildings.firstOrNull { it.id == id } ?: return emptyList()
        if (keeps(s, b, cast) != null) return emptyList()
        val rest = without(s, b)
        return GameEngine.buildOptions(rest, cast, words).filter { o -> o.type != b.type && open(rest, o.type) }
    }

    /**
     * Replaces building [id] by a new [type] on its plot ([replaceOptions]): the old one comes down (nothing back for it),
     * the new one is paid as building new ([withMoba]: the neighbours help) and goes up there; the chronicle says "🔁
     * Nadomeščeno: Šotor → Hiša · Replaced: Tent → House". [s] unchanged when it can't be.
     */
    fun replace(s: GameState, id: String, type: BuildingType, now: Long, withMoba: Boolean = false, cast: List<Villager> = emptyList()): GameState {
        val b = s.buildings.firstOrNull { it.id == id } ?: return s
        if (keeps(s, b, cast) != null || type == b.type || !type.onPlot) return s
        val rest = without(s, b)
        val built = GameEngine.buildAt(rest, type, now, withMoba, cast, b.plot) { used ->
            "🔁" to bi("engine.replaced", "a" to b.type.names, "b" to type.names) + Help.note(used)
        }
        return if (built === rest) s else Placement.chosen(built)
    }

    /**
     * The way [type] can go up while every plot of the age is built (null when a plot is free, or there is no way): a tent
     * that can grow into the hut wanted, a hut into the house (the lowest level first: the cheapest), else a building that
     * may be replaced ([keeps]) where [type] could stand (the plot kept for the next age, the age, the most of it: as on a
     * free plot), a kind the village has several of first, then the one cheapest to lose (its price and upgrades).
     */
    fun plotWay(s: GameState, type: BuildingType, cast: List<Villager> = emptyList()): PlotWay? {
        if (!type.onPlot || Placement.freePlots(s).isNotEmpty()) return null
        GROWS.entries.firstOrNull { it.value == type }?.key?.let { from ->
            s.buildings.filter { it.type == from && !it.damaged && growOption(s, it.id) != null }
                .minWithOrNull(compareBy({ it.level }, { it.plot }))
                ?.let { return PlotWay.Grow(it.id, from, type) }
        }
        val c = s.buildings
            .filter { it.type != type && keeps(s, it, cast) == null && without(s, it).let { rest -> open(rest, type) && GameEngine.placeable(rest, type) } }
            .minWithOrNull(compareBy({ s.buildings.count { o -> o.type == it.type } <= 1 }, { worth(it) }, { it.plot }))
            ?: return null
        return PlotWay.Replace(c.id, c.type)
    }

    /** What a building is worth to keep: its price and upgrades, half when damaged. */
    private fun worth(b: Building): Float = Catalog.spec(b.type).cost.values.sum() * levelsFactor(b.level) / (if (b.damaged) 2f else 1f)

    /** The age has [type], and fewer stand than its most. */
    private fun open(s: GameState, type: BuildingType): Boolean {
        val spec = Catalog.spec(type)
        return s.age >= spec.minAge && (spec.max == null || s.buildings.count { it.type == type } < spec.max)
    }

    private fun without(s: GameState, b: Building): GameState = s.copy(buildings = s.buildings.filter { it.id != b.id })
}
