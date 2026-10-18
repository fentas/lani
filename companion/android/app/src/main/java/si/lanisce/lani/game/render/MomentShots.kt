package si.lanisce.lani.game.render

import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Beat
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Change
import si.lanisce.lani.game.Decor
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Moment

/**
 * Where the camera looks in a beat of an age's moment (ui/game/AgeUpMoment; companion/GAME.md "Ages and their
 * celebrations"): at [at] (nominal canvas px) [zoom] times the fitted zoom, or the plots of [rect] framed; null [at] and
 * [rect]: the village's home view.
 */
data class Shot(val at: CanvasPoint? = null, val zoom: Float = 0f, val rect: CanvasRect? = null) {
    val home: Boolean get() = at == null && rect == null
}

/** The camera's shots of an age's moment, beat by beat. Pure: the same village and canvas, the same shots. */
object MomentShots {
    /**
     * Where [beat] of [m] looks in [state]'s village on a [w] × [h] canvas: the whole clearing as the forest draws back, the
     * new plots framed, the bridge for the road to the west, the road east of the fire for a wider road and its cobbles, the
     * fire closer for the square and the gathering, the decorations where they go up (the likof's roof, the stalls and the
     * banners along the road, else the fire), the sign at the road, and home for the opening and the cheer.
     */
    fun of(beat: Beat, m: Moment, state: GameState, w: Int, h: Int): Shot {
        val comp = VillageLayout.composition(w, h, compact = false)
        val land = VillageLayout.of(state)
        fun at(wx: Float, wy: Float, z: Float = 0f) = CanvasPoint(comp.fireX + (wx - wy) * 4f, comp.fireY + (wx + wy) * 2f - z)
        val fire = at(0f, 0f, 6f)
        val road = 5.5f.let { at(it, land.roadY(it)) }
        return when (beat.kind) {
            Beat.Kind.OPEN, Beat.Kind.CHEER -> Shot()
            Beat.Kind.GROW -> when (beat.change) {
                Change.FOREST -> Shot(fire, 1f)
                Change.PLOTS -> TownAnchors.plotsRect(state, w, h, m.plots, pad = 26f)?.let { Shot(rect = it) } ?: Shot(fire, 1f)
                Change.WEST -> Shot(at(land.bridge[0], land.bridge[1]), 2f)
                Change.WIDE, Change.COBBLES -> Shot(road, 2f)
                Change.SQUARE, Change.FIRE, Change.PALISADE, null -> Shot(fire, 2.5f)
            }
            Beat.Kind.GATHER -> Shot(fire, 2.5f)
            Beat.Kind.DECORATE -> {
                val decor = m.celebration.decor
                val roof = state.buildings.filter { it.type.onPlot && it.plot in 0 until VillageLayout.MAX_PLOTS }
                    .let { bs -> bs.filter { it.type == BuildingType.HOUSE }.maxByOrNull { it.builtAt } ?: bs.filter { it.type == BuildingType.HUT }.maxByOrNull { it.builtAt } }
                when {
                    Decor.SPRUCE in decor && roof != null -> Shot(TownAnchors.top(roof, state, w, h), 2.5f)
                    Decor.STALLS in decor || Decor.BANNERS in decor -> Shot(road, 2f)
                    else -> Shot(fire, 2f)
                }
            }
            Beat.Kind.SIGN -> {
                val x = land.roadOut(edgeAge(m, state), 20f)
                val half = if (state.age >= Age.VAS) 1f else 0.55f
                Shot(at(x, land.roadY(x) - half - 0.7f, 10f), 3f)
            }
        }
    }

    /** The age the forest's edge is at by the sign's beat: the new one (it drew back before). */
    private fun edgeAge(m: Moment, state: GameState): Age = if (m.to <= state.age) m.to else state.age
}
