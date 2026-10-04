package si.lanisce.lani.game

import si.lanisce.lani.game.BuildingType.CHURCH
import si.lanisce.lani.game.BuildingType.BEEHIVE
import si.lanisce.lani.game.BuildingType.FIELD
import si.lanisce.lani.game.BuildingType.HOUSE
import si.lanisce.lani.game.BuildingType.HUT
import si.lanisce.lani.game.BuildingType.KOZOLEC
import si.lanisce.lani.game.BuildingType.LIPA
import si.lanisce.lani.game.BuildingType.MARKET
import si.lanisce.lani.game.BuildingType.WELL
import si.lanisce.lani.game.render.Terrain
import si.lanisce.lani.game.render.VillageLayout
import kotlin.math.hypot

/**
 * Which plot the game suggests for a new building (the ★ of the plot chooser, see [PlotChoice]; the learner may take
 * another free plot): next to what it belongs with. A field, a hut, a house and a bee house go beside one of their own
 * kind ([KIN]: the fields make one big field, the huts and the houses a street); the scenes show some buildings together,
 * and the map keeps them together too: the free plot of the age nearest a building of its [partners] that stands, when
 * that plot is a neighbour of it ([NEIGHBOUR]). Without one built, without a free plot beside one, and for a building
 * without either, the next free plot, as before (plot 0 is the nearest the fire). Pure and deterministic: plot-centre
 * distances on the village's land ([Terrain.centers]), ties to the lower plot.
 */
object Placement {
    /**
     * Two plots whose centres lie this close (world cells) are neighbours: the next plot in a row (4.5 to 6.5 apart), the
     * nearest ones in the rows in front, behind and across the road (6 to 8.2). The next but one lies 9.7 or more away.
     */
    const val NEIGHBOUR = 8.5f

    /** Buildings that go beside one of their own kind first (then beside their [partners]): fields, huts, houses and bee houses. */
    val KIN = setOf(FIELD, HUT, HOUSE, BEEHIVE)

    /** Na vasi · On the village square: the linden, the well and the church stand there (and the market, from Vas). */
    private val SQUARE = setOf(WELL, CHURCH, LIPA, MARKET)

    /**
     * What stands beside what, from the scenes that show them together:
     * - the kozolec and the field ("Na njivi": the hayrack in the field, the hay on it), both ways;
     * - the square's buildings with each other: the linden, the well and the church ("Na vasi"), and the market.
     *
     * A field goes beside another field first ([KIN]), then beside a kozolec. The tent stands at the campfire in its
     * scene; today's order already gives it the plots nearest the fire. The bee house has no partner: no scene shows it
     * with anything (the bee meadow is a project of its own, on the meadow); it goes beside another bee house, as the huts
     * and the houses go beside their own kind. Everything else keeps today's order.
     */
    val partners: Map<BuildingType, Set<BuildingType>> = buildMap {
        put(KOZOLEC, setOf(FIELD))
        put(FIELD, setOf(KOZOLEC))
        for (t in SQUARE) put(t, SQUARE)
    }

    /** The free plots of the village's age, in order. */
    fun freePlots(s: GameState): List<Int> {
        val used = s.buildings.filter { it.type.onPlot }.map { it.plot }.toSet()
        return (0 until PLOTS_PER_AGE[s.age.ordinal]).filter { it !in used }
    }

    /** The plot the game suggests for a new [type] (see [Placement]), or null when no plot is free. */
    fun plotFor(s: GameState, type: BuildingType): Int? = plotBeside(s, type) ?: freePlots(s).firstOrNull()

    /**
     * The free plot beside what [type] belongs with: of its own kind first ([KIN]), else of its [partners]; null when it
     * belongs with nothing that stands, or no free plot is beside one.
     */
    fun plotBeside(s: GameState, type: BuildingType): Int? {
        val free = freePlots(s)
        val land = VillageLayout.of(s)
        fun near(types: Set<BuildingType>) = nearest(land, free, s.buildings.filter { it.type in types && onMap(it.plot) }.map { it.plot })
        return (if (type in KIN) near(setOf(type)) else null) ?: partners[type]?.let { near(it) }
    }

    /**
     * Where the game suggests [b] could move (the ★ when moving it): the free plot beside what it belongs with, as if it
     * weren't standing ([plotBeside]); null when it belongs with nothing, has no such plot, or already stands there.
     */
    fun moveTo(s: GameState, b: Building): Int? {
        if (!b.type.onPlot || b.plot == NO_PLOT) return null
        return plotBeside(s.copy(buildings = s.buildings.filter { it.id != b.id }), b.type)?.takeIf { it != b.plot }
    }

    /**
     * [id] moved to [plot] of the age: onto a free plot, or onto another building's, the two swapping places. Only the
     * plots change (ids, levels, damage, dates and the buildings' order stay). Null when it can't: a building on no plot
     * (the palisade, the tent at the pond), its own plot, or one beyond the age's.
     */
    fun moved(s: GameState, id: String, plot: Int): GameState? {
        val b = s.buildings.firstOrNull { it.id == id && it.type.onPlot && it.plot != NO_PLOT } ?: return null
        if (plot == b.plot || plot !in 0 until PLOTS_PER_AGE[s.age.ordinal]) return null
        val other = s.buildings.firstOrNull { it.id != b.id && it.type.onPlot && it.plot == plot }
        return s.copy(buildings = s.buildings.map {
            when (it.id) {
                b.id -> it.copy(plot = plot)
                other?.id -> it.copy(plot = b.plot)
                else -> it
            }
        })
    }

    /**
     * The learner chose a plot (built where they chose, or moved a building): from then on the game moves nothing on its
     * own, so an older save's one-time move of its kozolec ([hayracksByFields]) counts as done.
     */
    internal fun chosen(s: GameState): GameState =
        if (HAYRACKS_BY_FIELDS in s.migrated) s else s.copy(migrated = s.migrated + HAYRACKS_BY_FIELDS)

    /** Plot-centre distance between two plots of [land] (world cells). */
    fun distance(a: Int, b: Int, land: Terrain = Terrain.CLASSIC): Float {
        val p = land.centers[a]; val q = land.centers[b]
        return hypot(p[0] - q[0], p[1] - q[1])
    }

    /** Whether plot [a] of [land] is a neighbour of one of [others]. */
    fun beside(a: Int, others: List<Int>, land: Terrain = Terrain.CLASSIC): Boolean = onMap(a) && others.any { onMap(it) && distance(a, it, land) <= NEIGHBOUR }

    /** Of [free], the plot nearest one of [anchors] and a neighbour of it (the lower plot on a tie), or null. */
    private fun nearest(land: Terrain, free: List<Int>, anchors: List<Int>): Int? {
        if (anchors.isEmpty()) return null
        return free.map { p -> p to anchors.minOf { distance(p, it, land) } }
            .filter { it.second <= NEIGHBOUR }
            .minWithOrNull(compareBy<Pair<Int, Float>> { it.second }.thenBy { it.first })?.first
    }

    private fun onMap(plot: Int) = plot in 0 until VillageLayout.MAX_PLOTS

    /** The id of the kozolec's move in [GameState.migrated]. */
    const val HAYRACKS_BY_FIELDS = "hayracks-by-fields"

    /**
     * A village from before the placement ([Placement]) may have its kozolec far from its field: a kozolec that stands
     * farther than [NEIGHBOUR] from every field moves to the free plot nearest a field, beside it. Only its plot changes
     * (its level, damage and all stay); no other building moves. It is done once no kozolec stands apart any more, or
     * there is nothing to move (no kozolec or no field: a new field goes beside the kozolec); a kozolec with no free
     * plot beside a field waits for one (the next age's plots) until then. Once done it never runs again, so a kozolec
     * built later where the plots allowed stays where it was built. Returns the same state when nothing changed.
     */
    internal fun hayracksByFields(s: GameState): GameState {
        if (HAYRACKS_BY_FIELDS in s.migrated) return s
        val land = VillageLayout.of(s)
        val fields = s.buildings.filter { it.type == FIELD && onMap(it.plot) }.map { it.plot }
        val apart = s.buildings.filter { it.type == KOZOLEC && onMap(it.plot) && !beside(it.plot, fields, land) }.sortedBy { it.plot }
        var buildings = s.buildings
        var waiting = false
        if (fields.isNotEmpty()) for (k in apart) {
            val to = nearest(land, freePlots(s.copy(buildings = buildings)), fields)
            if (to == null) { waiting = true; continue }
            buildings = buildings.map { if (it.id == k.id) it.copy(plot = to) else it }
        }
        return if (waiting) (if (buildings === s.buildings) s else s.copy(buildings = buildings))
        else s.copy(buildings = buildings, migrated = s.migrated + HAYRACKS_BY_FIELDS)
    }
}
