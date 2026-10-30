package si.lanisce.lani.game.render

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The palisade's ring as people walk it ([Walkways], [TownPeople.route]): its stakes stand side by side, a wall nobody
 * walks through, and its road, path and river gates are the ways through it ([Door]). A water gate is closed by its
 * stakes standing in the stream, and where a lake or the sea is the wall the opening is water: neither is a way for
 * anyone on foot. Nor is a damaged palisade's breach: the stakes knocked down lie across it and the broken ones still
 * stand beside them, so the village keeps to its gates until it is patched up (whoever patches it stands inside it, see
 * [TownReactions]).
 *
 * Which side of the ring a point is on is the ring's own: inside the closed line from stake to stake round the clearing,
 * across each gate from post to post. Pure, worked out once per ring ([of]): the ring changes only with the age, the land
 * and the woods below the canvas.
 */
internal class PalisadeWalk private constructor(ring: PalisadeRing) {
    /**
     * A gate people walk through, between its posts ([ax], [ay]) and ([bx], [by]) (world cells): its middle ([mx], [my]),
     * the way in through it ([nx], [ny], a unit vector), how far each post stands from the middle ([half]), and where a walk
     * through it goes: [inner], [reach] cells inside its middle, and [outer], as far outside, so it goes straight through
     * the middle of the opening.
     */
    class Door internal constructor(
        val kind: PalisadeRing.Kind,
        val ax: Float, val ay: Float, val bx: Float, val by: Float,
        val nx: Float, val ny: Float, val reach: Float,
    ) {
        val mx = (ax + bx) / 2f
        val my = (ay + by) / 2f
        val half = hypot(bx - ax, by - ay) / 2f
        val inner = floatArrayOf(mx + nx * reach, my + ny * reach)
        val outer = floatArrayOf(mx - nx * reach, my - ny * reach)

        /** The door's point on the side [inside] says: [inner] or [outer]. */
        fun at(inside: Boolean): FloatArray = if (inside) inner else outer

        /** Whether ([x], [y]) stands in the opening itself: between the posts (a step off each) and no farther out or in than [inner] and [outer]. */
        fun mouth(x: Float, y: Float): Boolean {
            val ux = (bx - ax) / (2f * half); val uy = (by - ay) / (2f * half)
            val s = (x - mx) * ux + (y - my) * uy
            val t = (x - mx) * nx + (y - my) * ny
            return abs(s) < half - POST_ROOM && abs(t) <= reach + 0.05f
        }
    }

    private val n = ring.stakes.size
    private val xs = FloatArray(n) { ring.stakes[it].x }
    private val ys = FloatArray(n) { ring.stakes[it].y }

    /** The gates people walk through, by the ring's edge they open: edge i runs from stake i to stake i + 1. */
    private val open = BooleanArray(n)

    /** The road, path and river gates. */
    val doors: List<Door>

    /** The door of the path into the woods, or null (no woods below this canvas). */
    val path: Door?

    // the walls (every edge but the doors') in a grid of [CELL] cells, for the walks that cross them; and every edge by the
    // grid's rows, for which side a point is on
    private val gx0: Float
    private val gy0: Float
    private val gw: Int
    private val gh: Int
    private val start: IntArray
    private val edges: IntArray
    private val rowStart: IntArray
    private val rowEdges: IntArray

    init {
        val passable = ring.gates.filter { it.kind in WALKED }
        for (g in passable) {
            val i = if ((g.a + 1) % n == g.b) g.a else g.b
            open[i] = true
        }
        // the grid of the walls
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        for (i in 0 until n) { x0 = min(x0, xs[i]); y0 = min(y0, ys[i]); x1 = max(x1, xs[i]); y1 = max(y1, ys[i]) }
        gx0 = x0 - CELL; gy0 = y0 - CELL
        gw = if (n == 0) 1 else ((x1 - gx0) / CELL).toInt() + 2
        gh = if (n == 0) 1 else ((y1 - gy0) / CELL).toInt() + 2
        val count = IntArray(gw * gh + 1)
        fun cells(i: Int, each: (Int) -> Unit) {
            val j = (i + 1) % n
            val c0 = cx(min(xs[i], xs[j]) - EPS); val c1 = cx(max(xs[i], xs[j]) + EPS)
            val r0 = cy(min(ys[i], ys[j]) - EPS); val r1 = cy(max(ys[i], ys[j]) + EPS)
            for (r in r0..r1) for (c in c0..c1) each(r * gw + c)
        }
        for (i in 0 until n) if (!open[i]) cells(i) { count[it + 1]++ }
        for (k in 1..gw * gh) count[k] += count[k - 1]
        start = count.copyOf()
        edges = IntArray(count[gw * gh])
        val fill = count.copyOf()
        for (i in 0 until n) if (!open[i]) cells(i) { edges[fill[it]++] = i }
        // every edge, doors too, in each row its height spans
        val rows = IntArray(gh + 1)
        fun rowsOf(i: Int, each: (Int) -> Unit) {
            val j = (i + 1) % n
            for (r in cy(min(ys[i], ys[j]) - EPS)..cy(max(ys[i], ys[j]) + EPS)) each(r)
        }
        for (i in 0 until n) rowsOf(i) { rows[it + 1]++ }
        for (k in 1..gh) rows[k] += rows[k - 1]
        rowStart = rows.copyOf()
        rowEdges = IntArray(rows[gh])
        val rowFill = rows.copyOf()
        for (i in 0 until n) rowsOf(i) { rowEdges[rowFill[it]++] = i }
        doors = passable.mapNotNull { door(ring, it) }
        path = doors.firstOrNull { it.kind == PalisadeRing.Kind.PATH }
    }

    private fun cx(x: Float) = ((x - gx0) / CELL).toInt().coerceIn(0, gw - 1)
    private fun cy(y: Float) = ((y - gy0) / CELL).toInt().coerceIn(0, gh - 1)

    /** The door of gate [g]: which way is in, and how far in and out of its middle a walk through it goes (shorter where the ring bends close by). */
    private fun door(ring: PalisadeRing, g: PalisadeRing.Gate): Door? {
        val a = ring.stakes[g.a]; val b = ring.stakes[g.b]
        val len = hypot(b.x - a.x, b.y - a.y).takeIf { it > 1e-3f } ?: return null
        var nx = -(b.y - a.y) / len; var ny = (b.x - a.x) / len
        val mx = (a.x + b.x) / 2f; val my = (a.y + b.y) / 2f
        if (!inside(mx + nx * 0.2f, my + ny * 0.2f)) { nx = -nx; ny = -ny }
        for (reach in REACHES) {
            val d = Door(g.kind, a.x, a.y, b.x, b.y, nx, ny, reach)
            if (inside(d.inner[0], d.inner[1]) && !inside(d.outer[0], d.outer[1]) && !crosses(d.inner[0], d.inner[1], d.outer[0], d.outer[1])) return d
        }
        return null
    }

    /** Whether world ([x], [y]) is inside the ring (its line closed across every gate from post to post). */
    fun inside(x: Float, y: Float): Boolean {
        if (n < 3 || y < gy0 || y >= gy0 + gh * CELL) return false
        // a ray to the right, over the edges of the point's row of the grid: inside when it crosses an odd number of them
        val r = cy(y)
        var c = false
        for (e in rowStart[r] until rowStart[r + 1]) {
            val i = rowEdges[e]
            val j = if (i == n - 1) 0 else i + 1
            val yi = ys[i]; val yj = ys[j]
            if ((yi > y) != (yj > y) && x < (xs[j] - xs[i]) * (y - yi) / (yj - yi) + xs[i]) c = !c
        }
        return c
    }

    /** Whether the walk from ([ax], [ay]) to ([bx], [by]) goes through the stakes anywhere but a [Door] (a touch isn't). */
    fun crosses(ax: Float, ay: Float, bx: Float, by: Float): Boolean {
        if (n < 2) return false
        val dx = bx - ax; val dy = by - ay
        // the stretch of it over the grid (the walls are all on it)
        var t0 = 0f; var t1 = 1f
        fun clip(p: Float, q: Float): Boolean {
            if (p == 0f) return q >= 0f
            val t = q / p
            if (p < 0f) { if (t > t1) return false; if (t > t0) t0 = t } else { if (t < t0) return false; if (t < t1) t1 = t }
            return true
        }
        if (!clip(-dx, ax - gx0) || !clip(dx, gx0 + gw * CELL - ax) || !clip(-dy, ay - gy0) || !clip(dy, gy0 + gh * CELL - ay)) return false
        val px = ax + dx * t0; val py = ay + dy * t0
        // the cells it goes through, in order (Amanatides and Woo), and the walls in each
        var c = cx(px); var r = cy(py)
        val ce = cx(ax + dx * t1); val re = cy(ay + dy * t1)
        val sx = if (dx > 0f) 1 else -1; val sy = if (dy > 0f) 1 else -1
        var tx = if (dx != 0f) ((gx0 + (c + if (dx > 0f) 1 else 0) * CELL) - px) / dx else Float.MAX_VALUE
        var ty = if (dy != 0f) ((gy0 + (r + if (dy > 0f) 1 else 0) * CELL) - py) / dy else Float.MAX_VALUE
        val tdx = if (dx != 0f) CELL / abs(dx) else Float.MAX_VALUE
        val tdy = if (dy != 0f) CELL / abs(dy) else Float.MAX_VALUE
        var steps = gw + gh + 2
        while (steps-- > 0) {
            val k = r * gw + c
            for (e in start[k] until start[k + 1]) if (cut(ax, ay, bx, by, edges[e])) return true
            if (c == ce && r == re) break
            if (tx < ty) { tx += tdx; c += sx; if (c < 0 || c >= gw) break } else { ty += tdy; r += sy; if (r < 0 || r >= gh) break }
        }
        return false
    }

    /** Whether [way] goes through the stakes anywhere but a door. */
    fun crosses(way: List<FloatArray>): Boolean = (1 until way.size).any { crosses(way[it - 1][0], way[it - 1][1], way[it][0], way[it][1]) }

    /** How many of [way]'s stretches go through the stakes. */
    fun crossings(way: List<FloatArray>): Int = (1 until way.size).count { crosses(way[it - 1][0], way[it - 1][1], way[it][0], way[it][1]) }

    /** Whether the walk from a to b goes through the wall from stake [i] to the next (they cross, a touch doesn't). */
    private fun cut(ax: Float, ay: Float, bx: Float, by: Float, i: Int): Boolean {
        val j = if (i == n - 1) 0 else i + 1
        val cxa = xs[i]; val cya = ys[i]; val cxb = xs[j]; val cyb = ys[j]
        if (max(ax, bx) < min(cxa, cxb) || min(ax, bx) > max(cxa, cxb) || max(ay, by) < min(cya, cyb) || min(ay, by) > max(cya, cyb)) return false
        val d1 = (cxb - cxa) * (ay - cya) - (cyb - cya) * (ax - cxa)
        val d2 = (cxb - cxa) * (by - cya) - (cyb - cya) * (bx - cxa)
        if (!(d1 > 0f && d2 < 0f || d1 < 0f && d2 > 0f)) return false
        val d3 = (bx - ax) * (cya - ay) - (by - ay) * (cxa - ax)
        val d4 = (bx - ax) * (cyb - ay) - (by - ay) * (cxb - ax)
        return d3 > 0f && d4 < 0f || d3 < 0f && d4 > 0f
    }

    companion object {
        /** The gates people walk through: the road's, the path's into the woods, the river gate down to the stream. */
        val WALKED = setOf(PalisadeRing.Kind.ROAD, PalisadeRing.Kind.PATH, PalisadeRing.Kind.RIVER)

        /** How far inside and outside a door's middle a walk through it goes (cells), the first that fits. */
        private val REACHES = floatArrayOf(0.9f, 0.6f, 0.4f, 0.25f)

        /** Someone standing within this of a door's post (cells, along its line) stands among the stakes, not in its opening. */
        private const val POST_ROOM = 0.35f

        /** The walls' grid (cells), and a little slack round each wall in it. */
        private const val CELL = 1.5f
        private const val EPS = 1e-3f

        private val cache = java.util.WeakHashMap<PalisadeRing, PalisadeWalk>()

        /** [ring] as people walk it, worked out once. */
        fun of(ring: PalisadeRing): PalisadeWalk {
            synchronized(cache) { cache[ring] }?.let { return it }
            return PalisadeWalk(ring).also { synchronized(cache) { cache[ring] = it } }
        }
    }
}
