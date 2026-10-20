package si.lanisce.lani.game.render

import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Where the wild animals of a threat stand on the map (wolves about the village, a bear at the forest's edge by the field; companion/GAME.md
 * "People react"): the renderer draws them there, and the people keep away from the same spots ([Threat.keep]). Pure:
 * the village, its canvas, the woods below it; each animal slinks a little to and fro about its spot ([Beast.wobble]).
 */
internal object Threats {
    /**
     * One animal: its spot (world cells), how far it slinks to and fro from it at time t (sin(t × [rate] + [phase]) times
     * [dx], [dy] cells), and which way it faces. [screen]: placed on the woods' path (its depth by its screen row).
     */
    class Beast(val x: Float, val y: Float, val dx: Float, val dy: Float, val rate: Double, val phase: Double, val faceRight: Boolean) {
        fun at(time: Double): FloatArray { val s = sin(time * rate + phase).toFloat(); return floatArrayOf(x + dx * s, y + dy * s) }
        val reach: Float get() = hypot(dx, dy)
    }

    /**
     * A threat on the map: its [kind], its [beasts], where they are together ([cx], [cy], world cells) and how far round
     * them nobody goes ([keep], cells: the farthest one's reach and [KEEP] more).
     */
    class Threat(val kind: EventKind, val beasts: List<Beast>) {
        val cx: Float = beasts.map { it.x }.average().toFloat()
        val cy: Float = beasts.map { it.y }.average().toFloat()
        val keep: Float = (beasts.maxOfOrNull { hypot(it.x - cx, it.y - cy) + it.reach } ?: 0f) + KEEP

        /** How far world ([x], [y]) is from the nearest animal at its farthest slink (cells). */
        fun distance(x: Float, y: Float): Float = beasts.minOf { b -> max(0f, hypot(x - b.x, y - b.y) - b.reach) }

        /** Whether world ([x], [y]) is too close to the animals for anybody to be (within [KEEP] of one). */
        fun near(x: Float, y: Float): Boolean = distance(x, y) < KEEP

        /** The ground nobody walks on: an octagon round them [keep] cells out ([Walkways.Block]), its corners a little outside. */
        fun block(): Walkways.Block {
            val core = FloatArray(16); val corners = FloatArray(16)
            for (i in 0 until 8) {
                val a = PI / 8 + i * PI / 4
                // the octagon's edges are its circle's chords: grown so that every point within [keep] is inside
                val r = keep / cos(PI / 8).toFloat()
                core[2 * i] = cx + (cos(a) * r).toFloat(); core[2 * i + 1] = cy + (sin(a) * r).toFloat()
                corners[2 * i] = cx + (cos(a) * (r + 0.5f)).toFloat(); corners[2 * i + 1] = cy + (sin(a) * (r + 0.5f)).toFloat()
            }
            return Walkways.Block(core, corners)
        }
    }

    /** How near an animal nobody comes (cells): a few strides, so a walk round them keeps well clear. */
    const val KEEP = 2.5f

    /** How near the defenders stand to them (cells): facing them at a distance, in the palisade's gate when they're at it. */
    const val GUARD = 1.3f

    /** How many wolves come for an event of [strength]: two, three or four. */
    fun wolves(strength: Int): Int = 2 + strength.coerceIn(1, 5) / 2

    /**
     * The threat of [state]'s event on a canvas of [comp] ([w] × [h]) with the woods [fg] below it (null: none), or null
     * when the event is no threat (or there is none). [kind]: the event's kind, by default the state's.
     */
    fun of(state: GameState, comp: Composition, fg: Foreground?, w: Int, h: Int, kind: EventKind? = state.event?.kind): Threat? {
        val land = VillageLayout.of(state)
        val age = state.age
        val clearR = land.clearing(age)
        return when (kind) {
            EventKind.WOLVES -> {
                val n = wolves(state.event?.strength ?: 1)
                val palisade = state.buildings.any { it.type == BuildingType.PALISADE }
                // with a palisade they gather outside it: at the gate of the path from the woods, or at the ring's lower left
                val ring = if (palisade) Palisade.ring(land, age, fg) else null
                val gate = ring?.gates?.firstOrNull { it.kind == PalisadeRing.Kind.PATH }?.let { ring.middle(it) }
                Threat(kind, (0 until n).map { j ->
                    if (fg != null) {
                        // out of the woods, up the path from the meadow towards the village (or its gate)
                        val py = if (gate != null) comp.fireY + (gate[0] + gate[1]) * 2f + 6f + j * 6f else fg.meadowY - fg.meadowRy - 3f - j * 9f
                        val px = fg.pathX(py) + (j % 2 * 2 - 1) * 4f
                        val sx = px - comp.fireX; val sy = py - comp.fireY
                        // 2 px to and fro along the screen's x
                        Beast(world(sx, sy)[0], world(sx, sy)[1], 0.25f, -0.25f, 0.6, j.toDouble(), faceRight = j % 2 == 0)
                    } else {
                        val a = PI * (0.66 + j * 0.1)
                        val p = dry(land, edgePoint(comp, if (ring != null) land.forestEdge(age, a.toFloat()) + 2f else clearR * 0.98f, a, w, h))
                        Beast(p[0], p[1] - j * 0.3f, 0.8f, 0f, 0.6, j.toDouble(), faceRight = true)
                    }
                })
            }
            EventKind.BEAR -> {
                // out of the forest at its edge on the field's side (the orchard), outside the palisade when there is one: not
                // in among the houses, where the village gathers by the fire
                val field = state.buildings.firstOrNull { it.type == BuildingType.FIELD && it.plot in 0 until VillageLayout.MAX_PLOTS }
                val a = field?.let { val c = land.centers[it.plot]; VillageLayout.angle((c[0] - c[1]) * 4f, (c[0] + c[1]) * 2f).toDouble() } ?: (PI * 0.12)
                val walled = state.buildings.any { it.type == BuildingType.PALISADE }
                val r = if (walled) land.forestEdge(age, a.toFloat()) + 2f else minOf(land.forestEdge(age, a.toFloat()) - 6f, clearR * 0.92f)
                val p = dry(land, edgePoint(comp, r, a, w, h))
                Threat(kind, listOf(Beast(p[0], p[1], 0.5f, 0f, 0.5, 0.0, faceRight = (p[0] - p[1]) < 0f)))
            }
            else -> null
        }
    }

    /** The threat of [state]'s event on a [w] × [h] canvas as the people see it (the full map's composition and woods). */
    fun of(state: GameState, w: Int, h: Int): Threat? =
        if (state.event?.kind != EventKind.WOLVES && state.event?.kind != EventKind.BEAR) null
        else of(state, VillageLayout.composition(w, h, compact = false), Foreground.of(state, w, h), w, h)

    /**
     * Where the merchant's cart stands once it has come up the road (world cells): just past the last building that would
     * hide the road on a canvas [w] wide; the people who gather round it stand there too.
     */
    fun cart(state: GameState, w: Int): FloatArray {
        val land = VillageLayout.of(state)
        var stop = 6.5f
        val plots = state.buildings.filter { it.type.onPlot && it.plot in 0 until VillageLayout.MAX_PLOTS }.map { it.plot }.distinct()
        for (p in plots) { val c = land.centers[p]; if (c[1] > land.roadY(c[0])) stop = max(stop, c[0] - c[1] + 8f) }
        stop = kotlin.math.min(stop, (w / 2f - 22f) / 4f)
        return floatArrayOf(stop, 0.1f + land.roadY(stop))
    }

    /** Where the cart is [since] s after the merchant came (it drives in over [CART_S] s from the road's end, [cart] then). */
    fun cartAt(state: GameState, w: Int, since: Double): FloatArray {
        val stop = cart(state, w)[0]
        val u = (since / CART_S).coerceIn(0.0, 1.0).toFloat()
        val e = 1 - (1 - u) * (1 - u)
        val x = 30f - (30f - stop) * e
        return floatArrayOf(x, 0.1f + VillageLayout.of(state).roadY(x), if (u < 1f) 1f else 0f)
    }

    /** How long the merchant's cart takes to come up the road (s). */
    const val CART_S = 7.0

    /** World point [p] (cells), moved in off the water of [land] (its lake or sea) when it is in it. */
    fun dry(land: Terrain, p: FloatArray): FloatArray {
        if (!land.water) return p
        val sx = (p[0] - p[1]) * 4f; val sy = (p[0] + p[1]) * 2f
        if (!land.wetScreen(sx, sy, 4f)) return p
        val q = land.inland(sx, sy, 4f)
        return world(q[0], q[1])
    }

    /** A world point [r] (screen metric) from the fire at angle [a], kept on the nominal canvas. */
    fun edgePoint(comp: Composition, r: Float, a: Double, w: Int, h: Int): FloatArray {
        var sx = (r * cos(a)).toFloat(); var sy = (r * sin(a)).toFloat() / 2.4f
        sx = sx.coerceIn(14f - comp.fireX, w - 14f - comp.fireX)
        sy = sy.coerceIn(comp.horizon + 10f - comp.fireY, h - 8f - comp.fireY)
        return world(sx, sy)
    }

    /** Nominal px from the fire as world cells. */
    fun world(sx: Float, sy: Float): FloatArray = floatArrayOf((sx / 4f + sy / 2f) / 2f, (sy / 2f - sx / 4f) / 2f)
}
