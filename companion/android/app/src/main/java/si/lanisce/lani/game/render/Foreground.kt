package si.lanisce.lani.game.render

import si.lanisce.lani.game.Age
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The woods below the village on a tall canvas (the full-screen town on a phone held upright): a path winds south
 * out of the clearing, past a woodcutter's stack, a forest pond and a small meadow, and on past a charcoal pile to
 * the bottom edge. Everything is laid out in canvas pixels from the forest's edge straight below the fire, so it
 * sits the same way at every age. The meadow is where the forest's bubbles point and where someone from the forest
 * waits. Where each lies is the village's land's ([Terrain.woods]: the classic land's are fixed). Pure: the same land, age
 * and canvas give the same layout, so the overlays can use it without a frame.
 */
class Foreground private constructor(val comp: Composition, val w: Int, val h: Int, age: Age, val land: Terrain) {
    /** Canvas y of the forest's edge straight below the fire. */
    val edgeY: Float = comp.fireY + land.forestEdge(age, (PI / 2).toFloat()) / 2.4f

    /** Rows between the forest's edge and the canvas bottom. */
    val room: Float = h - edgeY

    private val x0 = comp.fireX + 8f

    /** The path starts a little inside the clearing, in the grass. */
    val pathTop: Float = edgeY - 8f

    /** Half the path's width, in canvas pixels. */
    val pathHalf = 3.5f

    /** Where the things lie on a generated land; null: the classic land's, fixed. */
    private val plan: Woods? = land.woods

    /** The path's centre column at canvas row [y]. */
    fun pathX(y: Float): Float {
        val u = y - edgeY
        val p = plan ?: return x0 + 13f * sin(u * 0.041f) + 5f * sin(u * 0.12f + 2f)
        return x0 + p.a1 * sin(u * p.f1 + p.p1) + p.a2 * sin(u * p.f2 + p.p2)
    }

    val meadowRx = plan?.meadowRx ?: 32f
    val meadowRy = plan?.meadowRy ?: 14f
    val meadowY: Float = edgeY + min(plan?.meadowDY ?: 72f, room - 44f)
    val meadowX: Float = pathX(meadowY)

    val pondRx = plan?.pondRx ?: 20f
    val pondRy = plan?.pondRy ?: 8f
    val pondY: Float = edgeY + (plan?.pondDY ?: 44f)
    val pondX: Float = if (plan == null) x0 - 62f else column(pondY) - plan.pondDX

    val stackY: Float = edgeY + (plan?.stackDY ?: 17f)
    val stackX: Float = if (plan == null) x0 + 27f else column(stackY) + plan.stackDX

    /** The hunter's high seat, at the meadow's right edge (deer are watched from there). */
    val seatX: Float = meadowX + meadowRx + (plan?.seatDX ?: 12f)
    val seatY: Float = meadowY - (plan?.seatDY ?: 6f)

    /** The charcoal pile, deeper in the woods; only when the canvas goes down that far. */
    val kopaY: Float = edgeY + (plan?.kopaDY ?: 150f)
    val kopa: Boolean = room >= (plan?.kopaDY ?: 150f) + 22f
    val kopaX: Float = if (plan == null) x0 + 60f else column(kopaY) + plan.kopaDX

    /** The path's column at row [y] to the whole px: where a generated land's things stand from it (as the classic's from [x0]). */
    private fun column(y: Float): Float = floor(pathX(y) + 0.5f)

    /** Where the charcoal burner sits, left of his pile and facing it (the "kopa" spot's ground point). */
    val kopaSeatX: Float get() = kopaX - 11f
    val kopaSeatY: Float get() = kopaY + 1f

    /** Where the forest's bubbles point: the middle of the meadow. */
    val anchorX: Float get() = meadowX
    val anchorY: Float get() = meadowY - 3f

    /** Where the path enters the meadow: someone from the forest waits here. */
    val gateY: Float get() = meadowY - meadowRy + 3f
    val gateX: Float get() = pathX(gateY)

    /** Distance from the meadow's centre in units of its radius, with a ragged rim: under 1 is meadow. */
    fun meadowD(x: Float, y: Float): Float {
        val dx = (x - meadowX) / meadowRx
        val dy = (y - meadowY) / meadowRy
        if (abs(dx) > 1.5f || abs(dy) > 1.5f) return 2f
        return sqrt(dx * dx + dy * dy) + (Noise.v2(x * 0.12f, y * 0.2f, 29) - 0.5f) * 0.35f
    }

    /** Distance from the pond's centre in units of its radius, with a ragged bank: under 1 is water. */
    fun pondD(x: Float, y: Float): Float {
        val dx = (x - pondX) / pondRx
        val dy = (y - pondY) / pondRy
        if (abs(dx) > 1.6f || abs(dy) > 1.6f) return 2f
        return sqrt(dx * dx + dy * dy) + (Noise.v2(x * 0.16f, y * 0.28f, 30) - 0.5f) * 0.3f
    }

    fun onPath(x: Float, y: Float, slack: Float = 0f): Boolean = y >= pathTop && abs(x - pathX(y)) < pathHalf + slack

    /** Things a tree must not stand on or in front of: (x, y, half width). */
    private val things = listOfNotNull(
        floatArrayOf(stackX, stackY, 10f),
        floatArrayOf(seatX, seatY, 8f),
        // the pile and, left of it, where its burner sits
        if (kopa) floatArrayOf(kopaX - 3f, kopaY, 15f) else null,
    )

    /**
     * Whether a tree with its trunk at ([x], [y]) keeps clear of the path, the pond, the meadow and the things in the
     * woods: not on them, and not so close in front that its crown would hang over them.
     */
    fun treeFree(x: Float, y: Float): Boolean {
        if (y < pathTop - 20f) return true
        if (onPath(x, y, slack = 3.5f)) return false
        if (meadowD(x, y) < 1.05f) return false
        if (y > meadowY && y < meadowY + meadowRy + 9f && abs(x - meadowX) < meadowRx * 1.05f) return false
        if (pondD(x, y) < 1.3f) return false
        if (y > pondY && y < pondY + pondRy + 14f && abs(x - pondX) < pondRx + 3f) return false
        for (t in things) if (abs(x - t[0]) < t[2] && y > t[1] - 7f && y < t[1] + 17f) return false
        return true
    }

    companion object {
        /**
         * The woods below [age]'s village on [land] on a [w] × [h] canvas, or null when the canvas isn't tall enough for
         * them.
         */
        fun of(age: Age, w: Int, h: Int, land: Terrain = Terrain.CLASSIC): Foreground? {
            if (h <= VillageLayout.TALL) return null
            val f = Foreground(VillageLayout.composition(w, h, compact = false), w, h, age, land)
            return if (f.room >= 120f) f else null
        }

        /** The woods below [state]'s village on a [w] × [h] canvas, or null (see [of]). */
        fun of(state: si.lanisce.lani.game.GameState, w: Int, h: Int): Foreground? = of(state.age, w, h, VillageLayout.of(state))
    }
}
