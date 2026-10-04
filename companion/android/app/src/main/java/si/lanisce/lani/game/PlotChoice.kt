package si.lanisce.lani.game

import si.lanisce.lani.game.render.PlotMarks
import si.lanisce.lani.game.villagers.Villager

/**
 * Choosing where a building stands (GAME.md "Buildings", "The learner chooses the plot"): a new building of [type] (with
 * the neighbours' help: [withMoba]), or the building [moving] that stands already, onto the plot [chosen] of the village's
 * age. The map shows the plots to choose from, the one the game suggests (★, [suggested]) and the village as it would be
 * ([preview]); nothing is spent or moved until it is confirmed ([confirm]), and going back leaves the village as it was.
 * The rows of plots stay (they keep the buildings from hiding each other): the choice is which plot.
 */
data class PlotChoice(
    val type: BuildingType,
    /** The plot chosen now; it starts at the suggestion. Null: none yet (moving, with nothing to suggest). */
    val chosen: Int?,
    /** The id of the building that moves; null for a new one. */
    val moving: String? = null,
    val withMoba: Boolean = false,
) {
    /** The free plots of the age it can go on. */
    fun free(s: GameState): List<Int> = if (type.onPlot) Placement.freePlots(s) else emptyList()

    /** Moving: the plots of the age with another building on them, which would swap places with it. None for a new building. */
    fun swaps(s: GameState): List<Int> = others(s).values.distinct().sorted()

    /** Moving: the other buildings on plots of the age, which would swap places with it, by id: the plot each stands on. */
    fun others(s: GameState): Map<String, Int> {
        val b = building(s) ?: return emptyMap()
        val plots = PLOTS_PER_AGE[s.age.ordinal]
        return s.buildings.filter { it.id != b.id && it.type.onPlot && it.plot in 0 until plots }.associate { it.id to it.plot }
    }

    /** The plot the game suggests (★): a new building's is [Placement.plotFor]; moving, [Placement.moveTo] (maybe none). */
    fun suggested(s: GameState): Int? = if (moving == null) Placement.plotFor(s, type) else building(s)?.let { Placement.moveTo(s, it) }

    /** Whether it can go on [plot]: a free plot of the age, or (moving) another building's. */
    fun takes(s: GameState, plot: Int): Boolean = plot in free(s) || plot in swaps(s)

    /** [plot] chosen (a tap on the map); a plot it can't go on changes nothing. */
    fun pick(s: GameState, plot: Int?): PlotChoice = if (plot != null && takes(s, plot)) copy(chosen = plot) else this

    /** The building that moves, while it stands on a plot. */
    fun building(s: GameState): Building? = moving?.let { id -> s.buildings.firstOrNull { it.id == id && it.type.onPlot && it.plot != NO_PLOT } }

    /** Moving onto another building's plot: that building, which goes to the moving one's plot. */
    fun swapWith(s: GameState): Building? {
        val p = chosen ?: return null
        val b = building(s) ?: return null
        return s.buildings.firstOrNull { it.id != b.id && it.type.onPlot && it.plot == p }
    }

    /** Whether there is anywhere to choose from (a free plot, or moving, another building to swap with). */
    fun open(s: GameState): Boolean = free(s).isNotEmpty() || swaps(s).isNotEmpty()

    /**
     * The village as it would be: the new building ([PREVIEW]) on the chosen plot, or the moving one there (and the one it
     * swaps with on its plot). Only for the map: nothing is paid, and it is never saved. [s] itself while nothing is chosen.
     */
    fun preview(s: GameState): GameState {
        val p = chosen?.takeIf { takes(s, it) } ?: return s
        return if (moving == null) s.copy(buildings = s.buildings + Building(PREVIEW, type, p))
        else Placement.moved(s, moving, p) ?: s
    }

    /** The id of the building the map shows on the chosen plot ([preview]). */
    val shown: String get() = moving ?: PREVIEW

    /** What the map marks meanwhile: the plots to choose from, the ★ and the chosen plot with its building (see [PlotMarks]). */
    fun marks(s: GameState): PlotMarks = PlotMarks(free(s), others(s), suggested(s), chosen?.takeIf { takes(s, it) }, shown)

    /**
     * Confirmed on [plot] (the chosen one, or the suggestion for "Predlog · Suggested"): the village with the building
     * built there and paid for ([GameEngine.build]), or moved there ([GameEngine.move], free); null when it can't be done
     * now (the plot taken meanwhile, the resources gone, nothing chosen).
     */
    fun confirm(s: GameState, now: Long, cast: List<Villager> = emptyList(), plot: Int? = chosen): GameState? {
        val p = plot?.takeIf { takes(s, it) } ?: return null
        val n = if (moving == null) GameEngine.build(s, type, now, withMoba, cast, plot = p) else GameEngine.move(s, moving, p, now)
        return n.takeIf { it !== s }
    }

    companion object {
        /** The id of the building a [preview] shows where a new one would go. */
        const val PREVIEW = "preview"

        /** Choosing the plot of a new [type], starting at the suggestion. */
        fun build(s: GameState, type: BuildingType, withMoba: Boolean = false): PlotChoice =
            PlotChoice(type, Placement.plotFor(s, type), withMoba = withMoba)

        /** Choosing a new plot for [b], starting at the suggestion if there is one. */
        fun move(s: GameState, b: Building): PlotChoice = PlotChoice(b.type, Placement.moveTo(s, b), moving = b.id)
    }
}
