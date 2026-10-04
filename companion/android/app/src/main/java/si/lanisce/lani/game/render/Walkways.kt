package si.lanisce.lani.game.render

import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Where people walk on the map (companion/VILLAGERS.md, "A day in the village"): never through the fire, a building or a
 * landmark. Each of those is a [Block]: its [Block.core], where nobody walks (the fire's: wherever someone standing would be
 * in its flames, [TownPeople.inFire]; a building's: its plot's footprint, a field's excepted, which is walked in), and the
 * corners just outside it that a way round it goes by. A walk is straight when nothing is in the way, else the shortest way
 * round what is ([around]): past the fire on its nearer side, round the plaza's ring; round the corner of a house. Pure,
 * the ways worked out once per village and walk ([Ways]).
 */
object Walkways {
    /** How far outside the fire's core its corners lie (px): a little room between the walkers and its flames. */
    private const val FIRE_ROOM = 2f

    /** How much of the fire's core rectangle's corners the way round cuts off (px), so it rounds the plaza. */
    private const val FIRE_CUT = 3.5f

    /** Half a plot's footprint (cells), and how far round it its corners lie. */
    private const val HALF = 1.5f
    private const val ROOM = 0.5f

    /** The campsite's fire: how far to either side of its middle nobody walks (px), and above and below it (see [TownPeople]). */
    private const val CAMP_FIRE_HALF = 5.5f
    private const val CAMP_FIRE_TOP = 12f
    private const val CAMP_FIRE_FRONT = 10f
    private const val TENT_HALF = 1.4f

    /** A walk shorter than this is taken to be on the spot (cells). */
    private const val EPS = 1e-3f

    /**
     * Something to walk round: its [core] where nobody walks (a convex polygon, world cells, x and y in turn) and the
     * [corners] a way round it goes by, a little outside the core (the same polygon grown).
     */
    class Block internal constructor(val core: FloatArray, val corners: FloatArray) {
        internal val minX = (core.indices step 2).minOf { core[it] }
        internal val maxX = (core.indices step 2).maxOf { core[it] }
        internal val minY = (1 until core.size step 2).minOf { core[it] }
        internal val maxY = (1 until core.size step 2).maxOf { core[it] }
        /** The core's edges' outward normals and offsets: a point p is inside when every nx·px + ny·py < c. */
        private val nx = FloatArray(core.size / 2)
        private val ny = FloatArray(core.size / 2)
        private val c = FloatArray(core.size / 2)

        init {
            // counter-clockwise or clockwise: the outward normal is on the side away from the middle
            val n = core.size / 2
            val mx = (0 until n).map { core[2 * it] }.average().toFloat()
            val my = (0 until n).map { core[2 * it + 1] }.average().toFloat()
            for (i in 0 until n) {
                val ax = core[2 * i]; val ay = core[2 * i + 1]
                val bx = core[2 * ((i + 1) % n)]; val by = core[2 * ((i + 1) % n) + 1]
                var ex = by - ay; var ey = ax - bx
                val l = hypot(ex, ey).coerceAtLeast(1e-6f)
                ex /= l; ey /= l
                if (ex * (mx - ax) + ey * (my - ay) > 0f) { ex = -ex; ey = -ey }
                nx[i] = ex; ny[i] = ey; c[i] = ex * ax + ey * ay
            }
        }

        /** Whether world ([x], [y]) is inside the core (on its edge is outside). */
        fun inside(x: Float, y: Float): Boolean {
            if (x <= minX || x >= maxX || y <= minY || y >= maxY) return false
            for (i in nx.indices) if (nx[i] * x + ny[i] * y >= c[i] - 1e-5f) return false
            return true
        }

        /** Whether the walk from ([ax], [ay]) to ([bx], [by]) goes through the core (along its edge or touching it doesn't). */
        fun crossed(ax: Float, ay: Float, bx: Float, by: Float): Boolean {
            if (max(ax, bx) <= minX || min(ax, bx) >= maxX || max(ay, by) <= minY || min(ay, by) >= maxY) return false
            val dx = bx - ax; val dy = by - ay
            var t0 = 0f; var t1 = 1f
            for (i in nx.indices) {
                // inside this edge: nx·(a + t d) < c
                val den = nx[i] * dx + ny[i] * dy
                val num = c[i] - (nx[i] * ax + ny[i] * ay)
                if (den == 0f) {
                    if (num <= 1e-5f) return false
                } else {
                    val t = num / den
                    if (den > 0f) t1 = min(t1, t) else t0 = max(t0, t)
                }
                if (t1 <= t0) return false
            }
            // a stretch of it inside, not a touch: its middle well within the core
            val len = hypot(dx, dy)
            if ((t1 - t0) * len < 1e-3f) return false
            val tm = (t0 + t1) / 2f
            val mx = ax + dx * tm; val my = ay + dy * tm
            for (i in nx.indices) if (nx[i] * mx + ny[i] * my > c[i] - 1e-4f) return false
            return true
        }
    }

    /**
     * The fire's block for a fire of [scale] ([TownAnchors.FIRE_SCALE]), round its middle at world 0, 0: its core wherever
     * someone standing would be in it ([TownPeople.inFire]: within its reach to either side, between the top of its tallest
     * flames and its front stones), its corners [FIRE_ROOM] px outside that, the rectangle's corners cut off.
     */
    fun fire(scale: Float): Block {
        val a = TownAnchors.fireReach(scale) + TownPeople.BODY_HALF
        val top = 15f * scale + 6f
        val front = TownAnchors.fireFront(scale) + TownPeople.BODY_HEIGHT
        val core = screen(floatArrayOf(-a, -top, a, -top, a, front, -a, front))
        val g = FIRE_ROOM; val k = FIRE_CUT
        val l = -a - g; val r = a + g; val t = -top - g; val b = front + g
        val corners = screen(floatArrayOf(l + k, t, r - k, t, r, t + k, r, b - k, r - k, b, l + k, b, l, b - k, l, t + k))
        return Block(core, corners)
    }

    /** A plot's footprint at [cx], [cy] (its middle, world cells) and its corners [ROOM] outside it. */
    private fun plot(cx: Float, cy: Float, half: Float = HALF): Block = Block(square(cx, cy, half), square(cx, cy, half + ROOM))

    /** A rectangle [x0, x1] × [y0, y1] of world cells, and its corners [ROOM] outside. */
    private fun rect(x0: Float, y0: Float, x1: Float, y1: Float): Block = Block(
        floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1),
        floatArrayOf(x0 - ROOM, y0 - ROOM, x1 + ROOM, y0 - ROOM, x1 + ROOM, y1 + ROOM, x0 - ROOM, y1 + ROOM),
    )

    private fun square(cx: Float, cy: Float, h: Float) = floatArrayOf(cx - h, cy - h, cx + h, cy - h, cx + h, cy + h, cx - h, cy + h)

    /** Nominal px from the fire ([sx, sy] in turn) as world cells. */
    private fun screen(p: FloatArray): FloatArray = FloatArray(p.size).also { out ->
        for (i in p.indices step 2) { val sx = p[i]; val sy = p[i + 1]; out[i] = (sx / 4f + sy / 2f) / 2f; out[i + 1] = (sy / 2f - sx / 4f) / 2f }
    }

    /**
     * What a walk in [state]'s village on a [w] × [h] canvas goes round: the fire, every building on a plot but a field, the
     * projects' landmarks, the campsite's fire and tent at the pond.
     */
    fun blocks(state: GameState, w: Int, h: Int): List<Block> {
        val land = VillageLayout.of(state)
        val out = ArrayList<Block>()
        out += fire(TownAnchors.FIRE_SCALE[state.age.ordinal])
        for (b in state.buildings) if (b.type.onPlot && b.type != BuildingType.FIELD && b.plot in 0 until VillageLayout.MAX_PLOTS) {
            val c = land.centers[b.plot]; out += plot(c[0], c[1])
        }
        for (m in LandmarkLayout.of(state, w, h)) out += rect(m.x, m.y, m.x + m.w, m.y + m.d)
        Campsite.of(state, w, h)?.let { camp ->
            out += plot(camp.wx, camp.wy, TENT_HALF)
            val fx = camp.fireX; val fy = camp.fireY
            val core = screen(floatArrayOf(-CAMP_FIRE_HALF, -CAMP_FIRE_TOP, CAMP_FIRE_HALF, -CAMP_FIRE_TOP, CAMP_FIRE_HALF, CAMP_FIRE_FRONT, -CAMP_FIRE_HALF, CAMP_FIRE_FRONT))
            val grown = screen(floatArrayOf(-CAMP_FIRE_HALF - 2f, -CAMP_FIRE_TOP - 2f, CAMP_FIRE_HALF + 2f, -CAMP_FIRE_TOP - 2f, CAMP_FIRE_HALF + 2f, CAMP_FIRE_FRONT + 2f, -CAMP_FIRE_HALF - 2f, CAMP_FIRE_FRONT + 2f))
            for (i in core.indices step 2) { core[i] += fx; core[i + 1] += fy; grown[i] += fx; grown[i + 1] += fy }
            out += Block(core, grown)
        }
        return out
    }

    /**
     * The way from [a] to [b] (world cells) round [blocks]: straight when nothing is in the way, else the shortest way by
     * the blocks' corners (a block [a] or [b] is in, they walk out of or into). Its points, [a] first and [b] last.
     */
    fun around(a: FloatArray, b: FloatArray, blocks: List<Block>): List<FloatArray> {
        val live = blocks.filter { !it.inside(a[0], a[1]) && !it.inside(b[0], b[1]) }
        fun clear(p: FloatArray, q: FloatArray) = live.none { it.crossed(p[0], p[1], q[0], q[1]) }
        if (hypot(b[0] - a[0], b[1] - a[1]) < EPS || clear(a, b)) return listOf(a, b)
        // the corners to go by: each block's, those inside another left out
        val pts = ArrayList<FloatArray>()
        pts += a; pts += b
        for (bl in live) for (i in bl.corners.indices step 2) {
            val x = bl.corners[i]; val y = bl.corners[i + 1]
            if (live.none { it.inside(x, y) }) pts += floatArrayOf(x, y)
        }
        // Dijkstra from a (0) to b (1) over the clear walks between them
        val n = pts.size
        val dist = FloatArray(n) { Float.MAX_VALUE }
        val prev = IntArray(n) { -1 }
        val done = BooleanArray(n)
        dist[0] = 0f
        while (true) {
            var u = -1
            for (i in 0 until n) if (!done[i] && dist[i] < Float.MAX_VALUE && (u < 0 || dist[i] < dist[u])) u = i
            if (u < 0 || u == 1) break
            done[u] = true
            for (v in 0 until n) {
                if (done[v]) continue
                val d = dist[u] + hypot(pts[v][0] - pts[u][0], pts[v][1] - pts[u][1])
                if (d < dist[v] && clear(pts[u], pts[v])) { dist[v] = d; prev[v] = u }
            }
        }
        // nothing clear (shut in): straight, as before
        if (prev[1] < 0) return listOf(a, b)
        val way = ArrayList<FloatArray>()
        var i = 1
        while (i >= 0) { way += pts[i]; i = prev[i] }
        way.reverse()
        return way
    }

    /** [around] for each leg of [points] in turn: the walk by them, round whatever is in the way. */
    fun along(points: List<FloatArray>, blocks: List<Block>): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        out += points.first()
        for (i in 1 until points.size) out.addAll(around(points[i - 1], points[i], blocks).drop(1))
        return out
    }

    /** The length of [way] (cells). */
    fun length(way: List<FloatArray>): Float = (1 until way.size).sumOf { hypot(way[it][0] - way[it - 1][0], way[it][1] - way[it - 1][1]).toDouble() }.toFloat()

    // ------------------------------------------------------------------ the ways worked out, per village

    /**
     * [state]'s village's blocks on a [w] × [h] canvas and the ways round them already worked out, by their points: the
     * renderer asks for each walker every frame. A village that changes (a building, the age) gets a new one.
     */
    class Ways internal constructor(val blocks: List<Block>) {
        private val cache = object : LinkedHashMap<List<Float>, List<FloatArray>>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<List<Float>, List<FloatArray>>?) = size > 512
        }

        /** [Walkways.along] by [points], remembered. */
        fun along(points: List<FloatArray>): List<FloatArray> {
            val key = ArrayList<Float>(points.size * 2).apply { for (p in points) { add(p[0]); add(p[1]) } }
            synchronized(cache) { cache[key] }?.let { return it }
            return along(points, blocks).also { synchronized(cache) { cache[key] = it } }
        }
    }

    private val ways = object : LinkedHashMap<String, Ways>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Ways>?) = size > 8
    }

    /** The [Ways] of [state]'s village on a [w] × [h] canvas. */
    fun of(state: GameState, w: Int, h: Int): Ways {
        val key = buildString {
            append(VillageLayout.of(state).key).append('/').append(state.age.name).append('/').append(w).append('x').append(h).append('/')
            for (b in state.buildings) append(b.plot).append(':').append(b.type.ordinal).append(',')
            append('/').append(si.lanisce.lani.game.Projects.landmarks(state).joinToString(",") { it.project + ":" + it.finished })
        }
        synchronized(ways) { ways[key] }?.let { return it }
        return Ways(blocks(state, w, h)).also { synchronized(ways) { ways[key] = it } }
    }
}
