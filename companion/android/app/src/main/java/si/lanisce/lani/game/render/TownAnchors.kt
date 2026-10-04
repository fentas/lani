package si.lanisce.lani.game.render

import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.TentMove
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.scene.TownSpots
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** A point on the canvas, in canvas pixels. */
data class CanvasPoint(val x: Float, val y: Float)

/** A rectangle on the canvas, in canvas pixels. */
data class CanvasRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
}

/**
 * Where things are in the full-screen village, in canvas pixels of a [w] × [h] canvas: the points bubbles
 * point at, the forest's edge, the clearing to frame. Pure (the same state and canvas give the same
 * answer), so the overlays can be laid out without waiting for a frame.
 */
object TownAnchors {
    /**
     * Angles (in the clearing's metric, 0 = right, π/2 = straight down) tried for the forest spot, best first:
     * lower left, lower right, then flatter and steeper. Up is the horizon, never a spot.
     */
    private val FOREST_ANGLES = floatArrayOf(0.72f, 0.28f, 0.86f, 0.14f, 0.6f, 0.4f).map { (it * PI).toFloat() }

    /** How far bubbles need to be from each other's places (canvas px) before the forest spot moves on. */
    private const val CROWD = 26f

    /** The bubble anchor of every place in the village: the fire, the forest and the first building of each type. */
    fun of(state: GameState, w: Int, h: Int): Map<TownPlace, CanvasPoint> {
        val comp = VillageLayout.composition(w, h, compact = false)
        val out = LinkedHashMap<TownPlace, CanvasPoint>()
        out[TownPlace.Fire] = fireTop(state, comp)
        for (t in BuildingType.entries) buildingTop(state, t, comp)?.let { out[TownPlace.At(t)] = it }
        // a house other than the first is a place of its own (its room's bubbles, see game/scene/Homes)
        for (b in state.buildings) (TownPlace.of(b, state) as? TownPlace.At)?.takeIf { it.id != null && b.plot in 0 until VillageLayout.MAX_PLOTS }?.let { out[it] = top(b, state, comp) }
        out[TownPlace.Forest] = forest(state, w, h, avoid = out.values.toList())
        // the tent at the pond (game/TentMove) is the tent: its campsite, or the forest's spot on a canvas without the woods
        if (TentMove.moved(state)) out[TownPlace.At(BuildingType.TENT)] = Campsite.of(state, w, h)?.top ?: out.getValue(TownPlace.Forest)
        return out
    }

    /** The middle of [plot] on the ground of a [w] × [h] canvas (canvas px). */
    fun plotCenter(state: GameState, plot: Int, w: Int, h: Int): CanvasPoint {
        val comp = VillageLayout.composition(w, h, compact = false)
        val c = VillageLayout.of(state).centers[plot.coerceIn(0, VillageLayout.MAX_PLOTS - 1)]
        return CanvasPoint(comp.fireX + (c[0] - c[1]) * 4f, comp.fireY + (c[0] + c[1]) * 2f)
    }

    /**
     * Of [plots], the one a tap at canvas point ([x], [y]) is on or nearest (its middle within [reach] canvas px, measured
     * as the plots lie, twice as wide as deep), or null: the plot chooser's tap (see [PlotMarks]).
     */
    fun plotAt(state: GameState, w: Int, h: Int, x: Float, y: Float, plots: Collection<Int>, reach: Float): Int? =
        plots.filter { it in 0 until VillageLayout.MAX_PLOTS }
            .map { p -> p to plotCenter(state, p, w, h).let { c -> hypot(x - c.x, (y - c.y) * 2f) } }
            .filter { it.second <= reach }
            .minWithOrNull(compareBy<Pair<Int, Float>> { it.second }.thenBy { it.first })?.first

    /** The canvas rectangle round the middles of [plots], [pad] px wider on every side (clipped to the canvas); null for none. */
    fun plotsRect(state: GameState, w: Int, h: Int, plots: Collection<Int>, pad: Float): CanvasRect? {
        val cs = plots.filter { it in 0 until VillageLayout.MAX_PLOTS }.map { plotCenter(state, it, w, h) }.ifEmpty { return null }
        return CanvasRect(
            (cs.minOf { it.x } - pad).coerceAtLeast(0f), (cs.minOf { it.y } - pad * 1.4f).coerceAtLeast(0f),
            (cs.maxOf { it.x } + pad).coerceAtMost(w.toFloat()), (cs.maxOf { it.y } + pad * 0.6f).coerceAtMost(h.toFloat()),
        )
    }

    /**
     * [of] plus the named spots ([SPOTS]): a bubble just above whoever stands there. A spot this canvas doesn't
     * have (the woods on a short canvas) shares the forest's anchor.
     */
    fun withSpots(state: GameState, w: Int, h: Int): Map<TownPlace, CanvasPoint> {
        val out = LinkedHashMap(of(state, w, h))
        val forest = out.getValue(TownPlace.Forest)
        for (id in SPOTS) out[TownPlace.Spot(id)] = spotAnchor(state, w, h, id)?.let { CanvasPoint(it[0].toFloat(), it[1] - 12f) } ?: forest
        out[TownPlace.Spot(TownSpots.HORIZON)] = horizon(w, h)
        // the finished projects' landmarks, where a scene of theirs opens (the vineyard): just above each one's top
        val comp = VillageLayout.composition(w, h, compact = false)
        for (m in LandmarkLayout.of(state, w, h)) if (m.landmark.finished) {
            out[TownPlace.Landmark(m.landmark.project)] = CanvasPoint(comp.fireX + (m.cx - m.cy) * 4f, comp.fireY + (m.cx + m.cy) * 2f - m.height - 2f)
        }
        return out
    }

    /**
     * Where someone stands at the landmark of [project] in world cells (just in front of its footprint's front corner), or
     * null when it isn't on this canvas's map; and whether it stands in the clearing (else out in the woods, see
     * [TownPeople.away]).
     */
    fun landmarkSpot(state: GameState, project: String, w: Int, h: Int): Pair<FloatArray, Boolean>? {
        val m = LandmarkLayout.of(state, w, h).firstOrNull { it.landmark.project == project } ?: return null
        val sx = (m.cx - m.cy) * 4f; val sy = (m.cx + m.cy) * 2f
        val inside = VillageLayout.screenMetric(sx, sy) <= VillageLayout.of(state).forestEdge(state.age, VillageLayout.angle(sx, sy))
        return floatArrayOf(m.x + m.w + 0.35f, m.y + m.d * 0.55f) to inside
    }

    /**
     * The land at the horizon ([TownSpots.HORIZON]): the hills and mountains behind the clearing (or the sea, the backdrop a
     * culture shows), where its bubble points: on the far hills left of the middle.
     */
    fun horizon(w: Int, h: Int): CanvasPoint {
        val comp = VillageLayout.composition(w, h, compact = false)
        return CanvasPoint(w * 0.3f, comp.horizon - horizonBand(comp) * 0.35f)
    }

    /**
     * Whether a canvas point is on the land at the horizon: the band from the mountains' shoulders down to the horizon,
     * the whole width (a tap the buildings, people, spots and the forest didn't take; see [horizon]).
     */
    fun isHorizon(w: Int, h: Int, x: Float, y: Float): Boolean {
        val comp = VillageLayout.composition(w, h, compact = false)
        return x >= 0f && x < w && y >= comp.horizon - horizonBand(comp) && y < comp.horizon + 2f
    }

    /** How far above the horizon the land's band reaches: most of the Alps' height (see [Backdrop]). */
    private fun horizonBand(comp: Composition): Float = min(comp.horizon - 3f, Backdrop.MAX_MOUNTAIN_PX) * 0.7f

    /** Just above the flames (they grow with the fire's strength and the age). */
    fun fireTop(state: GameState, comp: Composition): CanvasPoint {
        val fire = state.fire.coerceIn(0, 100) / 100f
        val fireScale = FIRE_SCALE[state.age.ordinal]
        val fh = (5f + fire * 10f) * fireScale
        return CanvasPoint(comp.fireX.toFloat(), comp.fireY - fh - 5f)
    }

    /** The top centre of the first building of [type] (lowest plot; the palisade's main gate), or null when there is none. */
    fun buildingTop(state: GameState, type: BuildingType, comp: Composition): CanvasPoint? {
        val b = state.buildings.filter { it.type == type && (!type.onPlot || it.plot in 0 until VillageLayout.MAX_PLOTS) }.minByOrNull { it.plot } ?: return null
        return top(b, state, comp)
    }

    /** The top centre of [b] on a [w] × [h] canvas (the palisade: of its main gate, in [state]'s clearing; the tent at the pond: of its campsite). */
    fun top(b: Building, state: GameState, w: Int, h: Int): CanvasPoint =
        (if (b.type == BuildingType.TENT && b.plot == NO_PLOT) Campsite.of(state, w, h)?.top else null) ?: top(b, state, VillageLayout.composition(w, h, compact = false))

    /**
     * The palisade's main gate, where the road leaves the clearing to the right (see [Palisade]): the middle of its
     * threshold, in world cells, and the height of its posts' points (px): [x, y, top].
     */
    internal fun gate(state: GameState): FloatArray = Palisade.ring(VillageLayout.of(state), state.age, null).gatePoint()

    private fun top(b: Building, state: GameState, comp: Composition): CanvasPoint {
        if (!b.type.onPlot) {
            val g = gate(state)
            return CanvasPoint(comp.fireX + (g[0] - g[1]) * 4f, comp.fireY + (g[0] + g[1]) * 2f - g[2] - 2f)
        }
        val o = VillageLayout.of(state).origin(b.plot)
        val x = comp.fireX + (o[0] - o[1]) * 4f
        val y = comp.fireY + (o[0] + o[1]) * 2f - spriteHeight(b.type) - 2f
        return CanvasPoint(x, y)
    }

    /**
     * The forest's spot: the meadow in the woods below the village when the canvas is tall enough to have them
     * (see [Foreground]); else a spot at the forest's edge near the clearing, on the canvas with room for a bubble
     * above it, clear of the other places' bubbles ([avoid]), on the first side (see [FOREST_ANGLES]) where that fits.
     */
    fun forest(state: GameState, w: Int, h: Int, avoid: List<CanvasPoint> = emptyList()): CanvasPoint {
        Foreground.of(state, w, h)?.let { return CanvasPoint(it.anchorX, it.anchorY) }
        val comp = VillageLayout.composition(w, h, compact = false)
        val land = VillageLayout.of(state)
        var first: CanvasPoint? = null
        fun fits(p: CanvasPoint) = p.x in MARGIN..(w - MARGIN) && p.y in (comp.horizon + 30f)..(h - 20f) &&
            !(land.water && land.wetScreen(p.x - comp.fireX, p.y - comp.fireY, 6f))
        for (a in FOREST_ANGLES) {
            val p = forestPoint(state, comp, a, outward = 6f)
            if (first == null) first = p
            if (fits(p) && avoid.none { hypot(it.x - p.x, it.y - p.y) < CROWD }) return p
        }
        // a generated land: the rest of the forest's edge below the horizon, the fitting point nearest the first side
        if (!land.classic) {
            val more = (1 until 40).map { (it / 40.0 * PI).toFloat() }.sortedBy { kotlin.math.abs(it - FOREST_ANGLES[0]) }.map { forestPoint(state, comp, it, outward = 6f) }
            (more.firstOrNull { fits(it) && avoid.none { o -> hypot(o.x - it.x, o.y - it.y) < CROWD } } ?: more.firstOrNull(::fits))?.let { return it }
        }
        val p = first!!
        return CanvasPoint(p.x.coerceIn(MARGIN, w - MARGIN), p.y.coerceIn(comp.horizon + 30f, h - 20f))
    }

    /** The point at the forest edge at angle [a], [outward] px beyond it (negative: inside the clearing). */
    private fun forestPoint(state: GameState, comp: Composition, a: Float, outward: Float): CanvasPoint {
        val r = VillageLayout.of(state).forestEdge(state.age, a) + outward
        return CanvasPoint(comp.fireX + r * cos(a), comp.fireY + r * sin(a) / 2.4f)
    }

    /** Whether a canvas point is in the forest around the clearing (not the sky, the meadow or the village). */
    fun isForest(state: GameState, w: Int, h: Int, x: Float, y: Float): Boolean {
        val comp = VillageLayout.composition(w, h, compact = false)
        if (y < comp.horizon + 2 || x < 0 || y < 0 || x >= w || y >= h) return false
        val dx = x - comp.fireX; val dy = y - comp.fireY
        return VillageLayout.screenMetric(dx, dy) > VillageLayout.of(state).forestEdge(state.age, VillageLayout.angle(dx, dy)) + 2f
    }

    /**
     * The clearing and what stands in it, with the Alps' feet above and (on a tall canvas) the meadow below where
     * the forest's bubbles point: what the town view frames when it (re)centres. Canvas pixels, clipped to the canvas.
     */
    fun home(state: GameState, w: Int, h: Int): CanvasRect {
        val comp = VillageLayout.composition(w, h, compact = false)
        val r = VillageLayout.of(state).clearing(state.age)
        val meadow = Foreground.of(state, w, h)?.let { it.anchorY + 16f } ?: 0f
        return CanvasRect(
            (comp.fireX - r - 6f).coerceAtLeast(0f),
            (comp.horizon - 28f).coerceAtLeast(0f),
            (comp.fireX + r + 6f).coerceAtMost(w.toFloat()),
            max(comp.fireY + r / 2.4f + 8f, meadow).coerceAtMost(h.toFloat()),
        )
    }

    /**
     * Where a villager waits at [place], in world cells (see [Iso]): beside the fire on its right, at the right front corner
     * of the first building of the type (just inside the palisade's main gate), on the path where it enters the
     * meadow in the woods, or (a canvas without the woods) just inside the clearing below the forest spot.
     */
    fun visitorSpot(state: GameState, place: TownPlace, comp: Composition, w: Int, h: Int): FloatArray? = when (place) {
        // A named spot's ground point; one this canvas doesn't have sends its visitors to the forest's. Nobody waits at the
        // horizon: it is far away (its bubble shows what is on there).
        // Someone at a project's landmark stands just in front of it, when it's in the clearing.
        is TownPlace.Landmark -> landmarkSpot(state, place.project, w, h)?.takeIf { it.second }?.first
        is TownPlace.Spot -> if (place.id == TownSpots.HORIZON) null else spotAnchor(state, w, h, place.id)?.let { p ->
            val dx = p[0] - comp.fireX.toFloat(); val dy = p[1] - comp.fireY.toFloat()
            floatArrayOf(worldX(dx, dy), worldY(dx, dy))
        } ?: visitorSpot(state, TownPlace.Forest, comp, w, h)
        // beside the fire on its right, where the first to come stands (see [TownPeople.aroundFire]), clear of the flames
        TownPlace.Fire -> TownPeople.aroundFire(0, FIRE_SCALE[state.age.ordinal])
        TownPlace.Forest -> {
            val fg = Foreground.of(state, w, h)
            val p = if (fg != null) CanvasPoint(fg.gateX, fg.gateY) else {
                val spot = forest(state, w, h, of(state, w, h).filterKeys { it != TownPlace.Forest }.values.toList())
                val dx = spot.x - comp.fireX; val dy = spot.y - comp.fireY
                // inside the palisade, when there is one (see [Palisade.INSET])
                val walled = state.buildings.any { it.type == BuildingType.PALISADE }
                forestPoint(state, comp, VillageLayout.angle(dx, dy), outward = if (walled) -Palisade.INSET - 8f else -4f)
            }
            floatArrayOf(worldX(p.x - comp.fireX, p.y - comp.fireY), worldY(p.x - comp.fireX, p.y - comp.fireY))
        }
        // the tent at the pond (game/TentMove): beside its fire, or with the forest's visitors on a canvas without the woods
        is TownPlace.At -> if (place.type == BuildingType.TENT && TentMove.moved(state)) {
            Campsite.of(state, w, h)?.visitor ?: visitorSpot(state, TownPlace.Forest, comp, w, h)
        } else if (!place.type.onPlot) {
            state.buildings.firstOrNull { it.type == place.type }?.let { val g = gate(state); floatArrayOf(g[0] - 1.6f, g[1] + 1.4f) }
        } else state.buildings.filter { it.type == place.type && (place.id == null || it.id == place.id) && it.plot in 0 until VillageLayout.MAX_PLOTS }.minByOrNull { it.plot }?.let { b ->
            // at the building's right front corner, in the gap before the next plot
            val c = VillageLayout.of(state).centers[b.plot]
            floatArrayOf(c[0] + 2.0f, c[1] + 0.6f)
        }
    }

    /**
     * Named spots where someone can stand and a bubble can point, besides the places. In the woods below the
     * village (tall canvases only, see [Foreground]): "meadow" (in the meadow, left of the path), "pond" (the
     * pond's near bank), "highseat" (at the foot of the hunter's high seat), "path" (where the path leaves the
     * clearing into the woods) and "kopa" (where the charcoal burner sits by his pile, deep in the woods: only on a
     * canvas that goes down that far, [Foreground.kopa]). Around the clearing's edge, on every canvas: "woodpile" (the
     * woodcutter's stack by the path, or a small stack at the lower right edge when there are no woods), "rocks" (a rock
     * cluster at the upper right edge), "riverbank" (the stream's near bank, above the bridge) and "road" (where the road
     * leaves the clearing to the right). The renderer draws the rocks and the small stack at these spots.
     */
    val SPOTS = listOf("meadow", "pond", "highseat", "path", "woodpile", "rocks", "riverbank", "road", "kopa")

    /** The ground point of the spot [id] (see [SPOTS]) in canvas pixels ([x, y]), or null when this canvas doesn't have it. */
    fun spotAnchor(state: GameState, w: Int, h: Int, id: String): IntArray? {
        val comp = VillageLayout.composition(w, h, compact = false)
        val fg = Foreground.of(state, w, h)
        val land = VillageLayout.of(state)
        val p = when (id) {
            "meadow" -> fg?.let { floatArrayOf(it.meadowX - 8f, it.meadowY - 5f) }
            "pond" -> fg?.let { floatArrayOf(it.pondX - 2f, it.pondY + it.pondRy + 4f) }
            "highseat" -> fg?.let { floatArrayOf(it.seatX - 5f, it.seatY + 5f) }
            "path" -> fg?.let { floatArrayOf(it.pathX(it.edgeY + 6f), it.edgeY + 6f) }
            "kopa" -> fg?.takeIf { it.kopa }?.let { floatArrayOf(it.kopaSeatX, it.kopaSeatY) }
            "woodpile" -> if (fg != null) floatArrayOf(fg.stackX - 3f, fg.stackY + 6f) else edgeSpot(state, comp, land.woodpileAngle, inward = 10f)
            "rocks" -> edgeSpot(state, comp, land.rocksAngle, inward = 7f)
            "riverbank" -> {
                val (wx, wy) = land.riverbank
                floatArrayOf(comp.fireX + (wx - wy) * 4f, comp.fireY + (wx + wy) * 2f)
            }
            "road" -> {
                // along the road (down and right at 1:2, bending where the land's road bends), 8 px inside the
                // clearing's edge, on its near lane
                val wx = if (land.classic) (land.forestEdge(state.age, ROAD_ANGLE) - 8f) / 6.25f else land.roadOut(state.age)
                val wy = land.roadY(wx)
                floatArrayOf(comp.fireX + (wx - wy) * 4f - 3f, comp.fireY + (wx + wy) * 2f + 2f)
            }
            else -> null
        } ?: return null
        val x = p[0].roundToInt(); val y = p[1].roundToInt()
        return if (x in 4..(w - 4) && y in (comp.horizon + 4)..(h - 4)) intArrayOf(x, y) else null
    }

    /**
     * Where the spot [id] is in world cells, for the spots around the clearing that every canvas can have: "rocks",
     * "riverbank", "road" and "woodpile" (the small stack at the clearing's edge); null for the spots of the woods below.
     * The same points as [spotAnchor], but not limited to a canvas.
     */
    internal fun spotWorld(state: GameState, id: String): FloatArray? {
        val origin = Composition(0, 0, 0)
        val land = VillageLayout.of(state)
        val p = when (id) {
            "woodpile" -> edgeSpot(state, origin, land.woodpileAngle, inward = 10f)
            "rocks" -> edgeSpot(state, origin, land.rocksAngle, inward = 7f)
            "riverbank" -> return land.riverbank.copyOf()
            "road" -> {
                val wx = if (land.classic) (land.forestEdge(state.age, ROAD_ANGLE) - 8f) / 6.25f else land.roadOut(state.age)
                val wy = land.roadY(wx)
                floatArrayOf((wx - wy) * 4f - 3f, (wx + wy) * 2f + 2f)
            }
            else -> return null
        }
        return floatArrayOf(worldX(p[0], p[1]), worldY(p[0], p[1]))
    }

    /** The road's direction in the clearing's metric (see [VillageLayout.angle]). */
    private val ROAD_ANGLE = atan2(4.8f, 4f)

    /** The point [inward] px inside the clearing's edge at angle [a] (0 = right, π/2 = straight down). */
    private fun edgeSpot(state: GameState, comp: Composition, a: Float, inward: Float): FloatArray {
        val r = VillageLayout.of(state).forestEdge(state.age, a) - inward
        return floatArrayOf(comp.fireX + r * cos(a), comp.fireY + r * sin(a) / 2.4f)
    }

    private fun worldX(sx: Float, sy: Float): Float = (sx / 4f + sy / 2f) / 2f
    private fun worldY(sx: Float, sy: Float): Float = (sy / 2f - sx / 4f) / 2f

    private const val MARGIN = 24f

    /**
     * The middle of each tappable place's body, where a tap hits it: the flames, every building (by id) and the
     * forest spot, the landscape's spots, and (with [horizon], when something opens there) the land at the horizon. For
     * screen readers, which can't tap pixels.
     */
    fun bodies(state: GameState, w: Int, h: Int, horizon: Boolean = false): List<Pair<String, CanvasPoint>> {
        val comp = VillageLayout.composition(w, h, compact = false)
        val out = ArrayList<Pair<String, CanvasPoint>>()
        out += "fire" to fireBody(state, comp)
        val land = VillageLayout.of(state)
        for (b in state.buildings.filter { it.type.onPlot && it.plot in 0 until VillageLayout.MAX_PLOTS }.sortedBy { it.plot }) {
            val c = land.centers[b.plot]
            val base = comp.fireY + (c[0] + c[1]) * 2f
            out += b.id to CanvasPoint(comp.fireX + (c[0] - c[1]) * 4f, base - spriteHeight(b.type) / 2f)
        }
        // the tent at the pond (game/TentMove), where the canvas has the woods
        TentMove.atPond(state)?.let { b -> Campsite.of(state, w, h)?.let { out += b.id to it.body } }
        // the palisade: its main gate's nearer post, halfway up
        state.buildings.firstOrNull { it.type == BuildingType.PALISADE }?.let { b ->
            val ring = Palisade.ring(land, state.age, null)
            val post = ring.main?.let { ring.stakes[it.b] }
            val g = if (post != null) floatArrayOf(post.x, post.y, post.h) else gate(state)
            out += b.id to CanvasPoint(comp.fireX + (g[0] - g[1]) * 4f, comp.fireY + (g[0] + g[1]) * 2f - g[2] / 2f)
        }
        out += "forest" to forest(state, w, h, of(state, w, h).filterKeys { it != TownPlace.Forest }.values.toList())
        // the landscape's tappable spots: what stands there, just above the ground point (the high seat's hide and the
        // charcoal pile, beside where the hunter and the burner are)
        val woods = Foreground.of(state, w, h)
        for (id in listOf("woodpile", "rocks", "meadow", "pond", "riverbank")) spotAnchor(state, w, h, id)?.let {
            // a generated land's woodcutter's stack: the middle of the stack itself (the classic land's as it always was)
            out += "spot:$id" to if (id == "woodpile" && woods != null && !land.classic) CanvasPoint(woods.stackX - 3f, woods.stackY - 6f) else CanvasPoint(it[0].toFloat(), it[1] - 4f)
        }
        woods?.let { fg ->
            out += "spot:highseat" to CanvasPoint(fg.seatX - 5f, fg.seatY - 10f)
            if (fg.kopa) out += "spot:${TownSpots.KOPA}" to CanvasPoint(fg.kopaX, fg.kopaY - 4f)
        }
        if (horizon) out += "spot:${TownSpots.HORIZON}" to horizon(w, h)
        return out
    }

    /**
     * The landmarks of the village's projects on a [w] × [h] canvas (see [LandmarkLayout]), each with the middle of its
     * body, where a tap hits it: for screen readers, which can't tap pixels.
     */
    fun landmarks(state: GameState, w: Int, h: Int): List<Pair<LandmarkSpot, CanvasPoint>> {
        val comp = VillageLayout.composition(w, h, compact = false)
        return LandmarkLayout.of(state, w, h).map { it to CanvasPoint(comp.fireX + it.bodyX, comp.fireY + it.bodyY) }
    }

    /** The fire's size per age (as the renderer draws it). */
    internal val FIRE_SCALE = floatArrayOf(0.8f, 0.9f, 1.0f, 1.15f, 1.3f, 1.45f)

    /**
     * How far the fire reaches to either side of its middle, nominal px, for a fire of [scale] ([FIRE_SCALE]): its ring of
     * stones and a little more, never less than ±9 px. A tap within it is the fire's (see [VillageRenderer.hitTest]), the
     * people at the fire stand beyond it ([TownPeople.aroundFire]), and a walk goes round it ([Walkways.fire]).
     */
    fun fireReach(scale: Float): Float = max(9f, 6.5f * scale + 4f)

    /** How far in front of its middle the fire reaches (nominal px): its front stones and a little more. */
    fun fireFront(scale: Float): Float = max(5f, 3.2f * scale + 3f)

    /** The middle of the fire's flames and stones on a [w] × [h] canvas, where its screen reader's target is centred ([bodies]). */
    fun fireBody(state: GameState, w: Int, h: Int): CanvasPoint = fireBody(state, VillageLayout.composition(w, h, compact = false))

    private fun fireBody(state: GameState, comp: Composition): CanvasPoint {
        val top = fireTop(state, comp)
        return CanvasPoint(top.x, (top.y + comp.fireY) / 2f)
    }

    /**
     * The fire's tap area on a [w] × [h] canvas, nominal px [left, top, right, bottom]: its flames as tall as they burn
     * now, its smoke just above them, its stones to either side ([fireReach]) and in front ([fireFront]); a tap within it
     * is the fire's ([VillageRenderer.hitTest] has the same box, in its canvas's pixels). The bubbles keep off it
     * (si.lanisce.lani.ui.game.layoutBubbles).
     */
    fun fireArea(state: GameState, w: Int, h: Int): FloatArray {
        val comp = VillageLayout.composition(w, h, compact = false)
        val scale = FIRE_SCALE[state.age.ordinal]
        val fh = (5f + state.fire.coerceIn(0, 100) / 100f * 10f) * scale
        val reach = fireReach(scale)
        return floatArrayOf(comp.fireX - reach, comp.fireY - fh - 6f, comp.fireX + reach, comp.fireY + fireFront(scale))
    }
}
