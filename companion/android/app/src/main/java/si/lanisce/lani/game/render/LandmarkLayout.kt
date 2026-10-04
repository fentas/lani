package si.lanisce.lani.game.render

import si.lanisce.lani.game.Age
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Landmark
import si.lanisce.lani.game.PLOTS_PER_AGE
import si.lanisce.lani.game.Projects
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Where a village project's landmark stands on the map (see [LandmarkLayout]): its footprint's top corner ([x], [y]) and
 * size ([w] along +x, [d] along +y) in world cells, and how tall it rises above it ([height], nominal px).
 */
data class LandmarkSpot(val landmark: Landmark, val x: Float, val y: Float, val w: Float, val d: Float, val height: Float) {
    val cx: Float get() = x + w / 2f
    val cy: Float get() = y + d / 2f

    /** The middle of its body (nominal px from the fire), where a tap hits it: a little above the footprint's middle. */
    val bodyX: Float get() = (cx - cy) * 4f
    val bodyY: Float get() = (cx + cy) * 2f - min(height, 24f) * 0.4f

    /** What it covers on the nominal canvas, px from the fire: [left, top, right, bottom] (the footprint and its height). */
    fun screenRect(): FloatArray = floatArrayOf((x - y - d) * 4f, (x + y) * 2f - height, (x + w - y) * 4f, (x + w + y + d) * 2f)

    /** Whether the world point ([px], [py]) is on the footprint or within [pad] cells of it. */
    fun covers(px: Float, py: Float, pad: Float): Boolean = px > x - pad && px < x + w + pad && py > y - pad && py < y + d + pad

    /**
     * Whether a tree or a bush standing at the world point ([tx], [ty]) would be in the way: on the footprint, or just in
     * front of it where its crown would hide it. The renderer leaves those out: a little glade round the landmark.
     */
    fun clears(tx: Float, ty: Float): Boolean {
        if (covers(tx, ty, 0.8f)) return true
        if (tx + ty <= cx + cy) return false
        val sx = (tx - ty) * 4f; val sy = (tx + ty) * 2f
        return sx > (x - y - d) * 4f - 5f && sx < (x + w - y) * 4f + 5f && sy < (x + w + y + d) * 2f + 18f
    }
}

/**
 * A landmark's footprint ([w] × [d] cells), how tall it stands ([height] px) and whether it is built of stone (its heap on
 * the building site is stone, else timber). A long one that [turns] may lie along either axis; a flat one that
 * [shrinks] may be laid out a little smaller where there's no room. [solid]: how much of its box it fills on the screen (a
 * maypole hides little of what's behind it).
 */
internal class LandmarkShape(
    val w: Float, val d: Float, val height: Float, val stone: Boolean,
    val turns: Boolean = false, val shrinks: Boolean = false, val solid: Float = 1f,
)

/**
 * Where each landmark of the village's projects stands (see [Projects.landmarks]): near the first of its sites that the
 * village has (a building, the fire, a spot of the landscape), on a spot that keeps clear of every plot of the age and
 * the paths their buildings will have, the road, the stream (the footbridge crosses it), the fire and the ring where
 * people gather round it, the rocks, the woodpile, the stream bank, the landing and the other landmarks. It stays in the
 * clearing (the few trees just in front of it make way, see [LandmarkSpot.clears]), on the part of the world every canvas
 * of the town shows; it isn't mostly hidden behind a building or a landmark, nor hides much of one, unless a crowded
 * village leaves no other place. The bee meadow goes to the meadow in the woods when the canvas has the woods, else to the forest's edge.
 *
 * Pure and stable: the same village gives the same places (a canvas matters only by whether it has the woods),
 * landmarks are placed in the catalog's order (a new project never moves one that is already there), and a landmark
 * keeps its place through its stages. Cached, it's asked for every frame.
 */
object LandmarkLayout {
    /** Clearance kept round a footprint (cells). */
    internal const val CLEAR = 0.3f

    /** The ring round the fire where people gather (the plaza, the festival's poles and dancers): kept free. */
    internal const val FIRE_RING = 4.4f

    /**
     * The part of the world every canvas of the town shows (px from the fire; see [SceneFit.town], at least
     * [SceneFit.TOWN_W] wide, and the Home header's strip): footprints inside, below the horizon, tops below [TOP] (a phone
     * on its side, 180 px tall, has the least room above and below the fire).
     */
    internal const val LEFT = -148f
    internal const val RIGHT = 148f
    internal const val UP = 8f - VillageLayout.HORIZON
    internal const val DOWN = 66f
    internal const val TOP = -106f

    /**
     * A place where more of a landmark would be hidden behind the buildings in front of it than this isn't one, nor one
     * where it would hide more of a building than [HIDES].
     */
    private const val MOSTLY_HIDDEN = 0.45f
    private const val HIDES = 0.35f

    /** How far from its site a landmark may go (cells), and the grid its places are tried on. */
    private const val REACH = 10f
    private const val STEP = 0.25f

    internal val SHAPES: Map<String, LandmarkShape> = mapOf(
        "mlaj" to LandmarkShape(2.0f, 2.0f, 36f, stone = false, solid = 0.3f),
        "most" to LandmarkShape(3.4f, 1.1f, 9f, stone = true),
        "mlin" to LandmarkShape(2.4f, 2.0f, 20f, stone = false),
        "balinisce" to LandmarkShape(4.6f, 1.3f, 4f, stone = false, turns = true, shrinks = true),
        "kapelica" to LandmarkShape(1.6f, 1.6f, 20f, stone = true),
        "vinograd" to LandmarkShape(3.0f, 2.6f, 16f, stone = false, shrinks = true),
        "cebelji_travnik" to LandmarkShape(2.6f, 2.0f, 8f, stone = false, shrinks = true),
        "gasilski_dom" to LandmarkShape(2.6f, 2.2f, 33f, stone = true),
        "igrisce" to LandmarkShape(2.8f, 2.0f, 12f, stone = false),
        "vodnjak_na_trgu" to LandmarkShape(2.0f, 2.0f, 14f, stone = true),
        "toplar" to LandmarkShape(4.4f, 2.2f, 19f, stone = false),
        "razgledni_stolp" to LandmarkShape(1.8f, 1.8f, 46f, stone = false, solid = 0.6f),
        "mestna_ura" to LandmarkShape(1.6f, 1.6f, 50f, stone = true),
    )

    /** A landmark the renderer doesn't know yet: a memorial stone. */
    private val MONUMENT = LandmarkShape(1.4f, 1.4f, 10f, stone = true)

    internal fun shapeOf(id: String): LandmarkShape = SHAPES[id] ?: MONUMENT

    private val cache = LinkedHashMap<String, Map<String, FloatArray>>()

    /**
     * The landmarks of [state] on a [w] × [h] nominal canvas ([compact]: a strip without the woods), in the catalog's
     * order. A landmark with no room at all (a crowded village) is left out.
     */
    fun of(state: GameState, w: Int, h: Int, compact: Boolean = false): List<LandmarkSpot> {
        val marks = Projects.landmarks(state)
        if (marks.isEmpty()) return emptyList()
        val fg = if (compact) null else Foreground.of(state, w, h)
        val key = buildString {
            append(VillageLayout.of(state).key).append('/').append(state.age.name).append('/').append(if (fg == null) "-" else "${fg.w}x${fg.h}").append('/')
            for (m in marks) append(m.project).append(',')
            append('/')
            for (b in state.buildings) append(b.plot).append(':').append(b.type.ordinal).append(',')
        }
        val places = synchronized(cache) { cache[key] } ?: place(state, marks, fg).also { p ->
            synchronized(cache) {
                if (cache.size >= 8) cache.remove(cache.keys.first())
                cache[key] = p
            }
        }
        return marks.mapNotNull { m -> places[m.project]?.let { r -> LandmarkSpot(m, r[0], r[1], r[2], r[3], shapeOf(m.id).height) } }
    }

    private fun place(state: GameState, marks: List<Landmark>, fg: Foreground?): Map<String, FloatArray> {
        val f = Field(state)
        val out = LinkedHashMap<String, FloatArray>()
        for (m in marks) {
            val shape = shapeOf(m.id)
            val (anchor, woods) = anchorOf(state, m.site, fg)
            val r = when {
                m.id == "most" -> f.bridge(anchor, shape)
                m.id == "mlin" -> f.bank(anchor, shape) ?: f.search(anchor, shape)
                woods && fg != null -> f.meadow(fg, anchor, shape)
                else -> f.search(anchor, shape)
            } ?: continue
            out[m.project] = r
            f.placed += floatArrayOf(r[0], r[1], r[0] + r[2], r[1] + r[3])
            // the next ones neither hide it nor hide behind it, as with the buildings
            val x0 = r[0]; val y0 = r[1]; val x1 = r[0] + r[2]; val y1 = r[1] + r[3]
            f.sprites += floatArrayOf((x0 - y1) * 4f, (x0 + y0) * 2f - shape.height, (x1 - y0) * 4f, (x1 + y1) * 2f, x0 + x1 + y0 + y1)
        }
        return out
    }

    /**
     * The world point a landmark is placed near: the first of its [site]s the village has (as
     * [si.lanisce.lani.game.scene.TownMarkers.projectPlace] picks it), and whether that is the meadow in the woods.
     * A spot of the woods on a canvas without them stands at the forest's edge, where its bubble points.
     */
    private fun anchorOf(state: GameState, site: List<String>, fg: Foreground?): Pair<FloatArray, Boolean> {
        for (key in site) {
            when {
                key == "fire" -> return floatArrayOf(0f, 0f) to false
                key == "forest" -> return forestPoint(state) to false
                key.startsWith("spot:") -> {
                    val id = key.removePrefix("spot:")
                    if (id == "meadow" && fg != null) return world(fg.meadowX - 8f - fg.comp.fireX, fg.meadowY - 5f - fg.comp.fireY) to true
                    return (TownAnchors.spotWorld(state, id) ?: forestPoint(state)) to false
                }
                else -> {
                    val type = BuildingType.entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: continue
                    // the palisade: inside its main gate
                    if (!type.onPlot) {
                        if (state.buildings.none { it.type == type }) continue
                        val g = TownAnchors.gate(state)
                        return floatArrayOf(g[0] - 2f, g[1]) to false
                    }
                    val b = state.buildings.filter { it.type == type && it.plot in 0 until VillageLayout.MAX_PLOTS }.minByOrNull { it.plot } ?: continue
                    val c = VillageLayout.of(state).centers[b.plot]
                    return floatArrayOf(c[0], c[1]) to false
                }
            }
        }
        return floatArrayOf(0f, 0f) to false
    }

    /** Inside the clearing's lower left edge, where the forest's bubble points on a canvas without the woods. */
    private fun forestPoint(state: GameState): FloatArray {
        val a = (0.72 * PI).toFloat()
        val r = VillageLayout.of(state).forestEdge(state.age, a) - 16f
        return world(r * cos(a), r * sin(a) / 2.4f)
    }

    private fun world(sx: Float, sy: Float): FloatArray = floatArrayOf((sx / 4f + sy / 2f) / 2f, (sy / 2f - sx / 4f) / 2f)

    /** The village as the landmarks see it: what they keep clear of, and what they'd rather not stand in front of. */
    private class Field(state: GameState) {
        val age: Age = state.age
        val land: Terrain = VillageLayout.of(state)
        val reserved = PLOTS_PER_AGE[age.ordinal].coerceIn(0, VillageLayout.MAX_PLOTS)
        val next = PLOTS_PER_AGE.getOrElse(age.ordinal + 1) { reserved }.coerceIn(reserved, VillageLayout.MAX_PLOTS)
        val road = if (age >= Age.VAS) 1f else 0.55f

        /**
         * Circles kept free (x, y, r in cells): the fire's ring, the festival's poles, the rocks, the woodpile, the stream
         * bank, the road's spot, the landing.
         */
        val keep = ArrayList<FloatArray>()
        /** Where people stand at their buildings. */
        val doors = ArrayList<FloatArray>()
        /**
         * The buildings as drawn, and the landmarks placed so far: left, top, right, bottom (px from the fire), and their
         * depth (x0 + x1 + y0 + y1 of the footprint: bigger is nearer).
         */
        val sprites = ArrayList<FloatArray>()
        /** The landmarks placed so far: x0, y0, x1, y1. */
        val placed = ArrayList<FloatArray>()

        /** The path into the woods below, relative to the fire: the same on every tall canvas. */
        private val woods: Foreground? = Foreground.of(age, 270, 600, land)
        private val woodsFire = VillageLayout.composition(270, 600, false)

        init {
            keep += floatArrayOf(0f, 0f, FIRE_RING)
            for (px in floatArrayOf(-3.4f, 3.4f)) for (py in floatArrayOf(-3.4f, 3.4f)) keep += floatArrayOf(px, py, 0.6f)
            TownAnchors.spotWorld(state, "rocks")?.let { keep += floatArrayOf(it[0], it[1], 1.8f); keep += floatArrayOf(it[0] - 1f, it[1] - 1f, 2.3f); keep += floatArrayOf(it[0] - 2.5f, it[1] - 2.5f, 2.3f) }
            TownAnchors.spotWorld(state, "woodpile")?.let { keep += floatArrayOf(it[0], it[1], 1.8f); keep += floatArrayOf(it[0] - 1f, it[1] - 0.6f, 2.3f) }
            TownAnchors.spotWorld(state, "riverbank")?.let { keep += floatArrayOf(it[0] + 0.4f, it[1], 1.8f) }
            TownAnchors.spotWorld(state, "road")?.let { keep += floatArrayOf(it[0] + 0.8f, it[1] + 0.3f, 2.0f) }
            // the landing at the water and the stones up from it (see Palisade.footpath)
            Palisade.footpath(Palisade.ring(land, age, null))?.let { (l, top) -> keep += floatArrayOf((l[0] + top[0]) / 2f + 0.5f, l[1], 2.2f) }
            for (b in state.buildings) {
                if (!b.type.onPlot || b.plot !in 0 until VillageLayout.MAX_PLOTS) continue
                val c = land.centers[b.plot]
                doors += floatArrayOf(c[0] + 2f, c[1] + 0.6f)
                val half = if (b.type == BuildingType.LIPA) 16f else 12f
                val base = (c[0] + c[1]) * 2f
                sprites += floatArrayOf((c[0] - c[1]) * 4f - half, base - 6f - spriteHeight(b.type), (c[0] - c[1]) * 4f + half, base + 6f, 2f * (c[0] + c[1]))
            }
        }

        // ---------------------------------------------------------------- what a footprint must keep clear of

        fun onPlot(x0: Float, y0: Float, x1: Float, y1: Float, from: Int, until: Int): Boolean {
            for (p in from until until) {
                val c = land.centers[p]
                if (x1 > c[0] - 1.5f && x0 < c[0] + 1.5f && y1 > c[1] - 1.5f && y0 < c[1] + 1.5f) return true
            }
            return false
        }

        /** On the path a building of the age has (or will have) to the road: from its plot's middle straight to the road. */
        fun onPath(x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
            for (p in 0 until reserved) {
                val c = land.centers[p]
                val r = land.roadY(c[0])
                if (x1 > c[0] - 0.5f && x0 < c[0] + 0.5f && y1 > min(c[1], r) && y0 < max(c[1], r) + 0.5f) return true
            }
            return false
        }

        /** On the road (where it bends: across its middle at the footprint's ends and middle). */
        fun onRoad(x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
            if (land.classic) return y1 > -road && y0 < road
            for (x in floatArrayOf(x0, (x0 + x1) / 2f, x1)) { val r = land.roadY(x); if (y1 > r - road && y0 < r + road) return true }
            return false
        }

        /** In the stream or on its banks. */
        fun inStream(x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
            var y = y0
            while (true) {
                val s = land.streamX(y)
                if (x1 > s - 1.4f && x0 < s + 1.4f) return true
                if (y >= y1) return false
                y = min(y + 0.25f, y1)
            }
        }

        fun kept(x0: Float, y0: Float, x1: Float, y1: Float): Boolean = keep.any { k ->
            val dx = max(max(x0 - k[0], 0f), k[0] - x1); val dy = max(max(y0 - k[1], 0f), k[1] - y1)
            dx * dx + dy * dy < k[2] * k[2]
        }

        fun onOthers(x0: Float, y0: Float, x1: Float, y1: Float): Boolean =
            placed.any { x1 > it[0] - CLEAR && x0 < it[2] + CLEAR && y1 > it[1] - CLEAR && y0 < it[3] + CLEAR }

        /** Everything on the ground, for a footprint expanded by the clearance. */
        fun clearOfThings(x0: Float, y0: Float, x1: Float, y1: Float, stream: Boolean): Boolean =
            !onRoad(x0, y0, x1, y1) && !onPlot(x0, y0, x1, y1, 0, reserved) && !kept(x0, y0, x1, y1) && !onPath(x0, y0, x1, y1) &&
                !onOthers(x0, y0, x1, y1) && !(stream && inStream(x0, y0, x1, y1)) && !(land.water && inWater(x0, y0, x1, y1))

        /** In a lake or the sea, or on the beach (a corner or the middle of the footprint, a little room round it). */
        fun inWater(x0: Float, y0: Float, x1: Float, y1: Float): Boolean =
            floatArrayOf(x0, (x0 + x1) / 2f, x1).any { x -> floatArrayOf(y0, (y0 + y1) / 2f, y1).any { y -> land.wet(x, y, 6f) } }

        fun inClearing(sx: Float, sy: Float, inset: Float): Boolean =
            VillageLayout.screenMetric(sx, sy) < land.forestEdge(age, VillageLayout.angle(sx, sy)) - inset

        /**
         * On every canvas of the town (inside [LEFT] … [DOWN], the top below [TOP]); with [clearing], in the clearing; off
         * the path into the woods.
         */
        fun onScreen(x0: Float, y0: Float, x1: Float, y1: Float, height: Float, clearing: Boolean): Boolean {
            var top = Float.MAX_VALUE
            for (px in floatArrayOf(x0, (x0 + x1) / 2f, x1)) for (py in floatArrayOf(y0, (y0 + y1) / 2f, y1)) {
                val sx = (px - py) * 4f; val sy = (px + py) * 2f
                if (sx < LEFT || sx > RIGHT || sy < UP || sy > DOWN) return false
                if (clearing && !inClearing(sx, sy, 5f)) return false
                top = min(top, sy)
            }
            if (top - height < TOP) return false
            return !crossesWoodsPath(x0, y0, x1, y1)
        }

        /** Right at the forest's edge the renderer clears the trees in front (see [LandmarkSpot.clears]): better further in. */
        fun edgy(x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
            for (p in arrayOf(floatArrayOf(x1, y1), floatArrayOf(x0, y1), floatArrayOf(x1, y0))) {
                if (!inClearing((p[0] - p[1]) * 4f, (p[0] + p[1]) * 2f + 8f, 0f)) return true
            }
            return false
        }

        private fun crossesWoodsPath(x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
            val fg = woods ?: return false
            val from = fg.pathTop - woodsFire.fireY - 4f
            if ((x1 + y1) * 2f < from) return false
            var px = x0
            while (px <= x1 + 0.001f) {
                var py = y0
                while (py <= y1 + 0.001f) {
                    val sx = (px - py) * 4f; val sy = (px + py) * 2f
                    if (sy >= from && abs(sx - (fg.pathX(woodsFire.fireY + sy) - woodsFire.fireX)) < fg.pathHalf + 5f) return true
                    py += 0.25f
                }
                px += 0.25f
            }
            return false
        }

        /** What a place costs besides its distance: the next age's plots, people's doorsteps, hiding a building or being hidden. */
        /**
         * Whether a landmark there would be mostly hidden: more of its box than [MOSTLY_HIDDEN] behind the buildings and
         * landmarks in front of it; or would hide one: cover more than [HIDES] of a building's (as much as it is solid).
         */
        fun hiddenOrHiding(x0: Float, y0: Float, x1: Float, y1: Float, shape: LandmarkShape): Boolean {
            val l = (x0 - y1) * 4f; val r = (x1 - y0) * 4f; val b = (x1 + y1) * 2f; val t = (x0 + y0) * 2f - shape.height
            val depth = x0 + x1 + y0 + y1
            var covered = 0f
            for (sp in sprites) {
                val ow = min(r, sp[2]) - max(l, sp[0]); val oh = min(b, sp[3]) - max(t, sp[1])
                if (ow <= 0f || oh <= 0f) continue
                if (sp[4] > depth) covered += ow * oh
                else if (shape.solid * ow * oh > HIDES * (sp[2] - sp[0]) * (sp[3] - sp[1])) return true
            }
            return covered > MOSTLY_HIDDEN * (r - l) * (b - t)
        }

        fun penalty(x0: Float, y0: Float, x1: Float, y1: Float, shape: LandmarkShape): Float {
            val height = shape.height
            var p = 0f
            if (onPlot(x0 - CLEAR, y0 - CLEAR, x1 + CLEAR, y1 + CLEAR, reserved, next)) p += 25f
            if (edgy(x0, y0, x1, y1)) p += 12f
            for (dd in doors) if (dd[0] > x0 - 0.5f && dd[0] < x1 + 0.5f && dd[1] > y0 - 0.5f && dd[1] < y1 + 0.5f) p += 12f
            val l = (x0 - y1) * 4f; val r = (x1 - y0) * 4f; val b = (x1 + y1) * 2f; val t = (x0 + y0) * 2f - height
            val area = (r - l) * (b - t)
            val depth = x0 + x1 + y0 + y1
            for (sp in sprites) {
                val ow = min(r, sp[2]) - max(l, sp[0]); val oh = min(b, sp[3]) - max(t, sp[1])
                if (ow <= 0f || oh <= 0f) continue
                // hidden behind a building (by how much of it is hidden), or hiding one (by how much of the building, as
                // much as the landmark is solid)
                p += if (sp[4] > depth) 100f * ow * oh / area else 120f * shape.solid * ow * oh / ((sp[2] - sp[0]) * (sp[3] - sp[1]))
            }
            return p
        }

        // ---------------------------------------------------------------- the searches

        /**
         * The best place near [anchor]: within [REACH], else a little further; a flat landmark that [LandmarkShape.shrinks]
         * makes do with a smaller patch when a crowded village has no room for the whole one.
         */
        fun search(anchor: FloatArray, shape: LandmarkShape): FloatArray? {
            // in a village too crowded for that, the best place even if half hidden or hiding a building
            for (strict in booleanArrayOf(true, false)) for (scale in if (shape.shrinks) floatArrayOf(1f, 0.8f) else floatArrayOf(1f)) {
                val w = shape.w * scale; val d = shape.d * scale
                val sizes = if (shape.turns) arrayOf(floatArrayOf(w, d), floatArrayOf(d, w)) else arrayOf(floatArrayOf(w, d))
                for (reach in floatArrayOf(REACH, REACH * 1.6f)) find(anchor, sizes, shape, reach, strict)?.let { return it }
            }
            return null
        }

        /**
         * The best place of one of [sizes] within [reach] of [anchor]: nearest on the screen, plus the [penalty]; [strict],
         * not where it is mostly hidden or hides a building (see [hiddenOrHiding]).
         */
        private fun find(anchor: FloatArray, sizes: Array<FloatArray>, shape: LandmarkShape, reach: Float, strict: Boolean): FloatArray? {
            val asx = (anchor[0] - anchor[1]) * 4f; val asy = (anchor[0] + anchor[1]) * 2f
            // the wider ring on a coarser grid
            val step = if (reach > REACH) STEP * 2f else STEP
            val n = (reach / step).toInt()
            val cands = ArrayList<FloatArray>((2 * n + 1) * (2 * n + 1) * sizes.size)
            for (s in sizes.indices) for (j in -n..n) for (i in -n..n) {
                val cx = anchor[0] + i * step; val cy = anchor[1] + j * step
                cands += floatArrayOf(cx, cy, s.toFloat(), hypot((cx - cy) * 4f - asx, 1.5f * ((cx + cy) * 2f - asy)))
            }
            cands.sortBy { it[3] }
            var best: FloatArray? = null; var bestScore = Float.MAX_VALUE
            for (c in cands) {
                if (c[3] >= bestScore) break
                val w = sizes[c[2].toInt()][0]; val d = sizes[c[2].toInt()][1]
                val x0 = c[0] - w / 2f; val y0 = c[1] - d / 2f; val x1 = x0 + w; val y1 = y0 + d
                if (!clearOfThings(x0 - CLEAR, y0 - CLEAR, x1 + CLEAR, y1 + CLEAR, stream = true)) continue
                if (!onScreen(x0, y0, x1, y1, shape.height, clearing = true)) continue
                if (strict && hiddenOrHiding(x0, y0, x1, y1, shape)) continue
                val sc = c[3] + penalty(x0, y0, x1, y1, shape)
                if (sc < bestScore) { bestScore = sc; best = floatArrayOf(x0, y0, w, d) }
            }
            return best
        }

        /**
         * The footbridge: across the stream, near [anchor] (the stream bank), well apart from the road's bridge, clear of the
         * bank where people fish. Its ends may reach the forest's edge (the trees there make way), not into the woods.
         */
        fun bridge(anchor: FloatArray, shape: LandmarkShape): FloatArray? {
            val asx = (anchor[0] - anchor[1]) * 4f; val asy = (anchor[0] + anchor[1]) * 2f
            var best: FloatArray? = null; var bestScore = Float.MAX_VALUE
            var y0 = -14f
            while (y0 <= 14f) {
                val y1 = y0 + shape.d
                val x0 = land.streamX((y0 + y1) / 2f) - shape.w / 2f; val x1 = x0 + shape.w
                val ok = (y1 < land.bridge[1] - 2.8f || y0 > land.bridge[1] + 2.8f) &&
                    clearOfThings(x0 - CLEAR, y0 - CLEAR, x1 + CLEAR, y1 + CLEAR, stream = false) &&
                    onScreen(x0, y0, x1, y1, shape.height, clearing = false)
                if (ok) {
                    var sc = hypot((x0 + x1 - y0 - y1) * 2f - asx, 1.5f * ((x0 + x1 + y0 + y1) - asy)) + penalty(x0, y0, x1, y1, shape)
                    var deep = false
                    for (px in floatArrayOf(x0, x1)) for (py in floatArrayOf(y0, y1)) {
                        val sx = (px - py) * 4f; val sy = (px + py) * 2f
                        if (!inClearing(sx, sy, -10f)) deep = true
                        else if (!inClearing(sx, sy, 2f)) sc += 20f
                    }
                    if (!deep && sc < bestScore) { bestScore = sc; best = floatArrayOf(x0, y0, shape.w, shape.d) }
                }
                y0 += 0.25f
            }
            return best
        }

        /**
         * The mill: on the stream's bank on the village's (+x) side, its end toward the water where its race comes off the
         * stream, near [anchor] (the stream bank), well apart from the road's bridge and clear of what's placed (the
         * footbridge), the bank where people fish and the rest, in the clearing. Null when the stream has no such place.
         */
        fun bank(anchor: FloatArray, shape: LandmarkShape): FloatArray? {
            val asx = (anchor[0] - anchor[1]) * 4f; val asy = (anchor[0] + anchor[1]) * 2f
            var best: FloatArray? = null; var bestScore = Float.MAX_VALUE
            var y0 = -14f
            while (y0 <= 14f) {
                val y1 = y0 + shape.d
                var s = -Float.MAX_VALUE
                var yy = y0
                while (yy <= y1) { s = max(s, land.streamX(yy)); yy += 0.25f }
                // on the village's bank, its side toward the stream just off the water's edge (the water runs 0.95 cells
                // either side): the mill race comes off the stream there
                val x0 = s + 1.05f; val x1 = x0 + shape.w
                val ok = (y1 < land.bridge[1] - 2.8f || y0 > land.bridge[1] + 2.8f) &&
                    clearOfThings(x0 - CLEAR, y0 - CLEAR, x1 + CLEAR, y1 + CLEAR, stream = false) &&
                    !inStream(x0 + 0.55f, y0, x1 + CLEAR, y1) &&
                    onScreen(x0, y0, x1, y1, shape.height, clearing = true)
                if (ok) {
                    val sc = hypot((x0 + x1 - y0 - y1) * 2f - asx, 1.5f * ((x0 + x1 + y0 + y1) - asy)) + penalty(x0, y0, x1, y1, shape)
                    if (sc < bestScore) { bestScore = sc; best = floatArrayOf(x0, y0, shape.w, shape.d) }
                }
                y0 += 0.25f
            }
            return best
        }

        /**
         * In the meadow of the woods below ([fg]), near [anchor]: inside its rim, off the path, clear of the fallen log, the
         * berry bushes, where the shepherd walks and the high seat (the deer graze elsewhere once the bees are there: the
         * renderer leaves out what a landmark covers).
         */
        fun meadow(fg: Foreground, anchor: FloatArray, shape: LandmarkShape): FloatArray? {
            val fx = fg.comp.fireX.toFloat(); val fy = fg.comp.fireY.toFloat()
            val mx = fg.meadowX; val my = fg.meadowY
            val things = arrayOf(
                floatArrayOf(mx - 14f, my + 6f, 9f), // the fallen log, someone sitting on it
                floatArrayOf(mx - 25f, my - 8f, 7f), floatArrayOf(mx - 16f, my - 13f, 7f), // the berry bushes
                floatArrayOf(mx - 8f, my - 5f, 8f), floatArrayOf(mx, my - 2f, 7f), // where the shepherd stands and walks
                if (fg.land.classic) floatArrayOf(mx + 40f, my - 6f, 10f) else floatArrayOf(fg.seatX - 4f, fg.seatY, 10f), // the high seat
            )
            val asx = (anchor[0] - anchor[1]) * 4f; val asy = (anchor[0] + anchor[1]) * 2f
            val n = (8f / STEP).toInt()
            // the meadow is small: a smaller patch when the whole one doesn't fit
            for (size in arrayOf(floatArrayOf(shape.w, shape.d), floatArrayOf(shape.w * 0.8f, shape.d * 0.75f))) {
                val w = size[0]; val d = size[1]
                var best: FloatArray? = null; var bestScore = Float.MAX_VALUE
                for (j in -n..n) for (i in -n..n) {
                    val cx = anchor[0] + i * STEP; val cy = anchor[1] + j * STEP
                    val sc = hypot((cx - cy) * 4f - asx, 1.5f * ((cx + cy) * 2f - asy))
                    if (sc >= bestScore) continue
                    val x0 = cx - w / 2f; val y0 = cy - d / 2f; val x1 = x0 + w; val y1 = y0 + d
                    if (onOthers(x0 - CLEAR, y0 - CLEAR, x1 + CLEAR, y1 + CLEAR)) continue
                    var ok = true
                    var px = x0
                    while (ok && px <= x1 + 0.001f) {
                        var py = y0
                        while (ok && py <= y1 + 0.001f) {
                            val sx = fx + (px - py) * 4f; val sy = fy + (px + py) * 2f
                            if (fg.meadowD(sx, sy) > 1f || fg.onPath(sx, sy, slack = 2.5f)) ok = false
                            // the things themselves and, over them, the landmark's own height
                            for (t in things) if (hypot(sx - t[0], sy - t[1]) < t[2] || hypot(sx - t[0], sy - shape.height * 0.7f - t[1]) < t[2]) ok = false
                            py += d / 4f
                        }
                        px += w / 4f
                    }
                    if (ok) { bestScore = sc; best = floatArrayOf(x0, y0, w, d) }
                }
                if (best != null) return best
            }
            return null
        }
    }
}
