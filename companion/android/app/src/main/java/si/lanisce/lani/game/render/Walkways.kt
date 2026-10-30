package si.lanisce.lani.game.render

import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Where people walk on the map (companion/VILLAGERS.md, "A day in the village"): never through the fire, a building or a
 * landmark, nor across a field's crops. Each of those is a [Block]: its [Block.core], where nobody walks (the fire's: wherever
 * someone standing would be in its flames, [TownPeople.inFire]; a building's: its plot's footprint; a field's: its soil and
 * crops, which only its own workers walk into, from their way's end or start there), and the corners just outside it that a
 * way round it goes by. A walk is straight when nothing is in the way, else the shortest way
 * round what is ([around]): past the fire on its nearer side, round the plaza's ring; round the corner of a house. Where
 * the palisade stands, never through its stakes but by its gates ([PalisadeWalk], [along] with the village's [Ways]). Pure,
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
     * What a walk in [state]'s village on a [w] × [h] canvas goes round: the fire, every building on a plot, a field's crops
     * (along its edge: its workers walk in from where they set off in it or to where they work in it, a block they are in being
     * none for them), the projects' landmarks (but the bridge, which they walk over), the campsite's fire and tent at the pond,
     * and the wolves or the bear while they're about ([Threats.Threat.block]).
     */
    fun blocks(state: GameState, w: Int, h: Int): List<Block> {
        val land = VillageLayout.of(state)
        val out = ArrayList<Block>()
        out += fire(TownAnchors.FIRE_SCALE[state.age.ordinal])
        for (b in state.buildings) if (b.type.onPlot && b.plot in 0 until VillageLayout.MAX_PLOTS) {
            val c = land.centers[b.plot]; out += if (b.type == BuildingType.FIELD) plot(c[0], c[1], TownPeople.CROPS) else plot(c[0], c[1])
        }
        // the bridge is walked over, not round
        for (m in LandmarkLayout.of(state, w, h)) if (m.landmark.id != Crossing.BRIDGE) out += rect(m.x, m.y, m.x + m.w, m.y + m.d)
        Campsite.of(state, w, h)?.let { camp ->
            out += plot(camp.wx, camp.wy, TENT_HALF)
            val fx = camp.fireX; val fy = camp.fireY
            val core = screen(floatArrayOf(-CAMP_FIRE_HALF, -CAMP_FIRE_TOP, CAMP_FIRE_HALF, -CAMP_FIRE_TOP, CAMP_FIRE_HALF, CAMP_FIRE_FRONT, -CAMP_FIRE_HALF, CAMP_FIRE_FRONT))
            val grown = screen(floatArrayOf(-CAMP_FIRE_HALF - 2f, -CAMP_FIRE_TOP - 2f, CAMP_FIRE_HALF + 2f, -CAMP_FIRE_TOP - 2f, CAMP_FIRE_HALF + 2f, CAMP_FIRE_FRONT + 2f, -CAMP_FIRE_HALF - 2f, CAMP_FIRE_FRONT + 2f))
            for (i in core.indices step 2) { core[i] += fx; core[i + 1] += fy; grown[i] += fx; grown[i + 1] += fy }
            out += Block(core, grown)
        }
        // wolves or a bear about: nobody walks near them ([Threats.KEEP]), a way goes round them at that distance
        Threats.of(state, w, h)?.let { out += it.block() }
        return out
    }

    /**
     * The way from [a] to [b] (world cells) round [blocks]: straight when nothing is in the way, else the shortest way by
     * the blocks' corners (a block [a] or [b] is in, they walk out of or into). Its points, [a] first and [b] last.
     */
    fun around(a: FloatArray, b: FloatArray, blocks: List<Block>): List<FloatArray> = around(a, b, blocks, null, true)

    /**
     * [around] by only those of the blocks' corners that [sides] (by block, by corner) says are on [side] of the palisade
     * (null: all of them), so a way on one side of it doesn't go round a block by the other's.
     */
    internal fun around(a: FloatArray, b: FloatArray, blocks: List<Block>, sides: List<BooleanArray>?, side: Boolean): List<FloatArray> {
        val liveAt = blocks.indices.filter { !blocks[it].inside(a[0], a[1]) && !blocks[it].inside(b[0], b[1]) }
        val live = liveAt.map { blocks[it] }
        fun clear(p: FloatArray, q: FloatArray) = live.none { it.crossed(p[0], p[1], q[0], q[1]) }
        if (hypot(b[0] - a[0], b[1] - a[1]) < EPS || clear(a, b)) return listOf(a, b)
        // the corners to go by: each block's, those inside another left out (and those on the palisade's other side)
        val pts = ArrayList<FloatArray>()
        pts += a; pts += b
        for (k in liveAt) {
            val bl = blocks[k]
            for (i in bl.corners.indices step 2) {
                if (sides != null && sides[k][i / 2] != side) continue
                val x = bl.corners[i]; val y = bl.corners[i + 1]
                if (live.none { it.inside(x, y) }) pts += floatArrayOf(x, y)
            }
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

    // ------------------------------------------------------------------ through the palisade's gates

    /**
     * [along] in [ways]' village: each leg round what is in the way and, where the palisade stands ([Ways.walk]), never
     * through its stakes but by its gates ([leg]). Without a palisade, [along] as it was.
     */
    internal fun along(points: List<FloatArray>, ways: Ways): List<FloatArray> {
        val walk = ways.walk ?: return along(points, ways.blocks)
        val out = ArrayList<FloatArray>()
        out += points.first()
        for (i in 1 until points.size) out.addAll(leg(points[i - 1], points[i], ways, walk).drop(1))
        return out
    }

    /**
     * The way from [a] to [b] where the palisade stands ([PalisadeWalk]): both on one side of it, on that side, round what
     * is in the way by that side's corners and clear of the stakes ([keep]); from one side to the other, through the gate
     * that makes the way shortest ([through]).
     */
    private fun leg(a: FloatArray, b: FloatArray, ways: Ways, walk: PalisadeWalk): List<FloatArray> {
        val ia = walk.inside(a[0], a[1]); val ib = walk.inside(b[0], b[1])
        return if (ia == ib) keep(a, b, ia, ways, walk, 0) else through(a, b, ia, ways, walk) ?: around(a, b, ways.blocks)
    }

    /**
     * The way from [a] to [b], both [inside] the ring or both outside it, on that side: round what is in the way by that
     * side's corners, and where that would go through the stakes (the ring bends in between them), bent round the bend
     * ([bend]); outside, the way in through one gate and out through another when that is shorter than round the ring.
     * The way with the fewest stretches through the stakes, of those the shortest (none, unless shut in).
     */
    private fun keep(a: FloatArray, b: FloatArray, inside: Boolean, ways: Ways, walk: PalisadeWalk, depth: Int): List<FloatArray> {
        val way = around(a, b, ways.blocks, ways.inner, inside)
        if (depth >= MAX_BENDS || !walk.crosses(way)) return way
        var best = way; var bestX = walk.crossings(way); var bestLen = length(way)
        fun offer(w: List<FloatArray>) {
            val x = walk.crossings(w); val len = length(w)
            if (x < bestX || x == bestX && len < bestLen) { best = w; bestX = x; bestLen = len }
        }
        bend(a, b, inside, ways, walk)?.let { m -> offer(join(keep(a, m, inside, ways, walk, depth + 1), keep(m, b, inside, ways, walk, depth + 1))) }
        if (!inside && depth == 0) for (d1 in walk.doors) for (d2 in walk.doors) {
            if (d1 === d2) continue
            val est = dist(a, d1.outer) + 2f * d1.reach + dist(d1.inner, d2.inner) + 2f * d2.reach + dist(d2.outer, b)
            if (bestX == 0 && est >= bestLen) continue
            offer(join(keep(a, d1.outer, false, ways, walk, 1), keep(d1.inner, d2.inner, true, ways, walk, 1), keep(d2.outer, b, false, ways, walk, 1)))
        }
        return best
    }

    /**
     * Where a way from [a] to [b] on one side of the ring ([inside]) that would go through its stakes bends to keep clear
     * of them: from the middle between them towards the fire inside the ring (it stands round the fire), away from it
     * outside, or to either side, a little farther each time, the first point that is on that side, in nothing, and in
     * sight of both [a] and [b] (else of one of them, the rest bent again). Null: none.
     */
    private fun bend(a: FloatArray, b: FloatArray, inside: Boolean, ways: Ways, walk: PalisadeWalk): FloatArray? {
        val mx = (a[0] + b[0]) / 2f; val my = (a[1] + b[1]) / 2f
        val dirs = ArrayList<FloatArray>(3)
        val d = hypot(mx, my)
        if (d > 0.3f) dirs += if (inside) floatArrayOf(-mx / d, -my / d) else floatArrayOf(mx / d, my / d)
        val ex = b[0] - a[0]; val ey = b[1] - a[1]
        val el = hypot(ex, ey).coerceAtLeast(1e-3f)
        dirs += floatArrayOf(-ey / el, ex / el); dirs += floatArrayOf(ey / el, -ex / el)
        var half: FloatArray? = null
        for (t in BEND_STEPS) for (dir in dirs) {
            val x = mx + dir[0] * t; val y = my + dir[1] * t
            if (walk.inside(x, y) != inside || ways.blocks.any { it.inside(x, y) }) continue
            val ca = walk.crosses(a[0], a[1], x, y); val cb = walk.crosses(x, y, b[0], b[1])
            if (!ca && !cb) return floatArrayOf(x, y)
            if (half == null && (!ca || !cb)) half = floatArrayOf(x, y)
        }
        return half
    }

    /**
     * The way from [a] on one side of the ring ([inside]: inside it) to [b] on the other: through the gate that makes it
     * shortest, straight through the middle of its opening ([PalisadeWalk.Door.inner] to [PalisadeWalk.Door.outer]), on
     * each side as [keep] has it; from or to [a] or [b] where they stand in the opening itself. A gate something to keep
     * off stands in (the wolves at the path's gate) only when no other will do. Null: the ring has no gate to walk through.
     */
    private fun through(a: FloatArray, b: FloatArray, inside: Boolean, ways: Ways, walk: PalisadeWalk): List<FloatArray>? {
        var best: List<FloatArray>? = null; var bestX = Int.MAX_VALUE; var bestLen = Float.MAX_VALUE
        val doors = walk.doors.sortedBy { dist(a, it.at(inside)) + 2f * it.reach + dist(it.at(!inside), b) }
        for (pass in 0..1) {
            for (d in doors) {
                val near = if (d.mouth(a[0], a[1])) a else d.at(inside)
                val far = if (d.mouth(b[0], b[1])) b else d.at(!inside)
                val shut = ways.blocks.any { bl ->
                    !bl.inside(a[0], a[1]) && !bl.inside(b[0], b[1]) && (bl.inside(near[0], near[1]) || bl.inside(far[0], far[1]) || bl.inside(d.mx, d.my))
                }
                if (shut != (pass == 1)) continue
                if (bestX == 0 && dist(a, near) + dist(near, far) + dist(far, b) >= bestLen) continue
                val w = join(if (near === a) listOf(a) else keep(a, near, inside, ways, walk, 0), if (far === b) listOf(b) else keep(far, b, !inside, ways, walk, 0))
                val x = walk.crossings(w); val len = length(w)
                if (x < bestX || x == bestX && len < bestLen) { best = w; bestX = x; bestLen = len }
            }
            if (best != null) return best
        }
        return best
    }

    /** [ways] one after the other, a point the next one starts at not twice. */
    private fun join(vararg ways: List<FloatArray>): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        for (w in ways) for (p in w) {
            val last = out.lastOrNull()
            if (last == null || last[0] != p[0] || last[1] != p[1]) out += p
        }
        return out
    }

    private fun dist(a: FloatArray, b: FloatArray) = hypot(b[0] - a[0], b[1] - a[1])

    /** How often a way on one side of the ring bends again to keep clear of its stakes, at most. */
    private const val MAX_BENDS = 4

    /** How far from the middle a bend goes, a step farther each time (cells). */
    private val BEND_STEPS = floatArrayOf(0.5f, 1f, 1.5f, 2f, 3f, 4f, 5f, 6.5f, 8f, 10f, 13f)

    /** The path into the woods from just outside its gate: a point every this many rows of the canvas (nominal px). */
    private const val FOREST_STEP = 6f

    /**
     * The path down into the woods below a tall canvas ([fg]) from just outside its gate in the palisade ([door]): its
     * middle every few rows, down to the meadow's way in (the forest's spot) or, on a canvas that goes down that far, to
     * the charcoal pile's row. World cells.
     */
    private fun forestPath(fg: Foreground, door: PalisadeWalk.Door): List<FloatArray> {
        val fx = fg.comp.fireX.toFloat(); val fy = fg.comp.fireY.toFloat()
        fun world(sx: Float, sy: Float) = floatArrayOf((sx / 4f + sy / 2f) / 2f, (sy / 2f - sx / 4f) / 2f)
        val out = ArrayList<FloatArray>()
        out += door.outer
        val bottom = if (fg.kopa) fg.kopaY else fg.gateY
        var y = fy + (door.outer[0] + door.outer[1]) * 2f + FOREST_STEP
        while (y < bottom) { out += world(fg.pathX(y) - fx, y - fy); y += FOREST_STEP }
        out += world(fg.pathX(bottom) - fx, bottom - fy)
        return out
    }

    // ------------------------------------------------------------------ the ways worked out, per village

    /**
     * [state]'s village's blocks on a [w] × [h] canvas and the ways round them already worked out, by their points: the
     * renderer asks for each walker every frame. A village that changes (a building, the age) gets a new one. Where the
     * palisade stands, [walk] is its ring as people walk it, and [forest] the path into the woods below from its gate (see
     * [TownPeople.route]); null: no palisade (or no woods).
     */
    class Ways internal constructor(val blocks: List<Block>, internal val walk: PalisadeWalk? = null, internal val forest: List<FloatArray>? = null) {
        private val cache = object : LinkedHashMap<List<Float>, List<FloatArray>>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<List<Float>, List<FloatArray>>?) = size > 512
        }

        /** Which of each block's corners stand inside the palisade (by block, by corner): a way goes round by its own side's. */
        internal val inner: List<BooleanArray>? = walk?.let { w -> blocks.map { b -> BooleanArray(b.corners.size / 2) { w.inside(b.corners[2 * it], b.corners[2 * it + 1]) } } }

        /** Whether the walk from [a] to [b] would go through the palisade's stakes (never without one). */
        internal fun crosses(a: FloatArray, b: FloatArray): Boolean = walk?.crosses(a[0], a[1], b[0], b[1]) == true

        /** [Walkways.along] by [points], through the palisade's gates where it stands, remembered. */
        fun along(points: List<FloatArray>): List<FloatArray> {
            val key = ArrayList<Float>(points.size * 2).apply { for (p in points) { add(p[0]); add(p[1]) } }
            synchronized(cache) { cache[key] }?.let { return it }
            return along(points, this).also { synchronized(cache) { cache[key] = it } }
        }
    }

    private val ways = object : LinkedHashMap<String, Ways>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Ways>?) = size > 8
    }

    /**
     * The [Ways] of [state]'s village on a [w] × [h] canvas: with its palisade's ring as people walk it ([PalisadeWalk]) once
     * the palisade stands (the ring of the age, with a gate for the path where the canvas has the woods).
     */
    fun of(state: GameState, w: Int, h: Int): Ways {
        val key = buildString {
            append(VillageLayout.of(state).key).append('/').append(state.age.name).append('/').append(w).append('x').append(h).append('/')
            for (b in state.buildings) append(b.plot).append(':').append(b.type.ordinal).append(',')
            // a landmark down for the season (the maypole after May) is nothing to go round
            append('/').append(si.lanisce.lani.game.Projects.landmarks(state).joinToString(",") { it.project + ":" + it.finished + if (it.down) ":down" else "" })
            // the wolves or the bear about, as many as come
            state.event?.takeIf { it.kind == si.lanisce.lani.game.EventKind.WOLVES || it.kind == si.lanisce.lani.game.EventKind.BEAR }?.let { append('/').append(it.kind.name).append(':').append(it.strength) }
        }
        synchronized(ways) { ways[key] }?.let { return it }
        val fg = Foreground.of(state, w, h)
        val walk = if (state.buildings.none { it.type == BuildingType.PALISADE }) null
        else Palisade.ring(VillageLayout.of(state), state.age, fg).takeIf { it.stakes.size >= 3 }?.let { PalisadeWalk.of(it) }
        val forest = walk?.path?.let { d -> fg?.let { forestPath(it, d) } }
        return Ways(blocks(state, w, h), walk, forest).also { synchronized(ways) { ways[key] = it } }
    }
}
