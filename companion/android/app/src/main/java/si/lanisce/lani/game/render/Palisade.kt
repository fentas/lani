package si.lanisce.lani.game.render

import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The palisade round the village: a ring of pointed stakes a few pixels inside the forest's edge
 * ([Terrain.forestEdge]), hugging the tree line all the way round and growing with the clearing from age to age.
 * It opens in a gate where the road leaves the clearing (to the right; from Zaselek on also to the left, over the
 * bridge) and where the path goes down into the woods (a tall canvas, see [Foreground]), two stout posts under a lintel
 * where they stand level. Along the stream it keeps to one bank from gate to gate: the far bank, taking the stream in,
 * where it would go over the water (see [Palisade.keepOffStream]); it crosses the water only in a water gate where the
 * stream comes in or goes out, closed by a row of shorter stakes standing in the water ([wet]) with gaps for the current.
 * A ring that runs by the stream on the village's side opens there in a small river gate, a postern, with a footpath
 * through it down to a landing at the water (see [Palisade.footpath]). The stakes are lower on the near side, so the
 * village shows over them. Every gap between two stakes is a gate.
 *
 * [stakes] go round the ring in order, in world cells (see [Iso]); [gates] name their posts among them.
 */
internal class PalisadeRing(val stakes: List<Stake>, val gates: List<Gate>, val wet: List<Stake> = emptyList(), val land: Terrain = Terrain.CLASSIC) {
    /**
     * A stake at world cells ([x], [y]): its shaft is [h] nominal px tall, its point [Palisade.POINT] more. A gate's
     * [post] is stouter and taller. [u]: how far round the ring from the main gate it stands (0 at the gate, 1 opposite),
     * the order the stakes go up in when the palisade is built. [seed]: its own noise.
     */
    class Stake(val x: Float, val y: Float, val h: Float, val post: Boolean, val u: Float, val seed: Int) {
        val depth: Float get() = x + y
    }

    /**
     * Where the ring opens, between the posts [a] and [b] (indices into [stakes], [a] the farther one): the road, the
     * path into the woods, the stream, or the river gate down to it. A narrow road or path gate whose posts stand level
     * on the screen has a [lintel] across them, just below their points.
     */
    class Gate(val kind: Kind, val a: Int, val b: Int, val lintel: Boolean)

    /** [SHORE]: where a lake or the sea is the wall (a generated land's), the ring ending on the shore either side. */
    enum class Kind { ROAD, PATH, WATER, RIVER, SHORE }

    /** The main gate: where the road leaves the clearing to the right, towards the rest of the world. */
    val main: Gate? = gates.filter { it.kind == Kind.ROAD }.maxByOrNull { stakes[it.a].x + stakes[it.b].x }

    /** The river gate: the postern where a footpath goes down to the stream. */
    val river: Gate? = gates.firstOrNull { it.kind == Kind.RIVER }

    /** The middle of the main gate on the ground (world cells) and the height of its posts' points (px): [x, y, top]. */
    fun gatePoint(): FloatArray {
        val g = main ?: return stakes.maxByOrNull { it.x - it.y }?.let { floatArrayOf(it.x, it.y, it.h + Palisade.POINT) } ?: floatArrayOf(0f, 0f, 0f)
        return middle(g)
    }

    /** The middle of gate [g] on the ground (world cells) and the height of its posts' points (px): [x, y, top]. */
    fun middle(g: Gate): FloatArray {
        val a = stakes[g.a]; val b = stakes[g.b]
        return floatArrayOf((a.x + b.x) / 2f, (a.y + b.y) / 2f, max(a.h, b.h) + Palisade.POINT)
    }
}

/** The palisade's ring for an age (see [PalisadeRing]): pure, and cached (the renderer asks every frame). */
internal object Palisade {
    /** How far inside the forest's edge the stakes stand (the clearing's metric, see [VillageLayout.screenMetric]). */
    const val INSET = 4f

    /** Stake spacing along the ring (cells), and a stake's and a gate post's width. */
    const val STEP = 0.5f
    const val WIDTH = 0.45f
    const val POST_WIDTH = 0.6f

    /** A stake's point above its shaft (px). */
    const val POINT = 3f

    /** Shafts on the far side of the ring and straight in front (px): the near side is low, so the village shows. */
    private const val FAR_H = 8f
    private const val NEAR_H = 4f

    /** A gate post stands this much above the stakes beside it, and at least [GATE_H], so people pass under its lintel. */
    private const val POST_EXTRA = 4f
    private const val GATE_H = 11f

    /** How far the stakes keep off the road (cells, past its half width), the woods path (px) and the stream (cells). */
    private const val ROAD_CLEAR = 0.45f
    private const val PATH_CLEAR = 3f
    private const val STREAM_CLEAR = 1.55f

    /** Where the stream runs along the ring, the stakes keep this far from its middle (cells): on the bank, off the water gate. */
    private const val BANK = 1.7f

    /** Where the ring comes this close to the stream's village side (cells from its middle), or over it, it runs along the stream. */
    private const val NEAR = 4f

    /**
     * A stretch along the stream where the ring goes over it for at least this much (cells) takes the stream in: the stakes
     * keep to its far bank the whole stretch. Less, and they keep to the village's bank: the stream stays outside.
     */
    private const val MIN_RUN = 4f

    /** A gate wider than this (cells) gets no lintel: where the road and the stream leave the clearing together. */
    private const val MAX_LINTEL = 3.8f

    /**
     * A lintel only where the gate's posts stand about level on the screen: its rise over its run at most this. Where
     * one post stands well behind the other a beam from post to post would read as a slanted plank; they stand alone.
     */
    private const val LEVEL = 0.36f

    /** The water palisade: stakes across a water gate this far apart (cells), leaving gaps for the current, and their shafts (px). */
    private const val WET_STEP = 0.6f
    private const val WET_H = 3.5f

    /**
     * The river gate stands where the ring runs this close to the stream's middle on the village's side (cells, from, to),
     * at least [RIVER_CLEAR] stakes from any other gate, nearest the stream bank's spot; it is [RIVER_W] stakes wide, and
     * its posts stand [RIVER_POST] px over the stakes beside them.
     */
    private const val RIVER_NEAR = 0.5f
    private const val RIVER_FAR = 3.3f
    private const val RIVER_CLEAR = 6
    private const val RIVER_W = 4
    private const val RIVER_POST = 2f

    /** How far the ring's last stakes stand off a lake's or the sea's water (nominal px; the beach counts as the sea's). */
    private const val SHORE = 5f

    /** The river gate stands at least this far below the fire on the screen (nominal px): 14 px below the horizon. */
    private const val RIVER_TOP = 14f - VillageLayout.HORIZON

    /**
     * Where the ring has taken the stream in, the landing stands on the village's bank inside it, at the first of these
     * places (world y) clear of every plot, the road and the stream bank's spot: a little downstream of the fishing spot.
     */
    private val INSIDE_LANDING = floatArrayOf(6f, 5.5f, 6.5f, 5f, 7f, -6f, -5.5f, -6.5f, -5f, -7f)

    /**
     * The way down to the stream: the landing at the water (world cells [x, y], the stream's edge on the village's side)
     * and the top of the stepping stones up from it ([x, y]). Below the river gate where the ring runs by the stream on
     * the village's side: the stones come through the gate. Where the ring takes the stream in (see [keepOffStream]), on
     * the village's bank inside it (see [INSIDE_LANDING]). Null when there is neither.
     */
    fun footpath(ring: PalisadeRing): Pair<FloatArray, FloatArray>? {
        val land = ring.land
        ring.river?.let { g ->
            val m = ring.middle(g)
            return floatArrayOf(land.streamX(m[1]) + 1.05f, m[1]) to floatArrayOf(m[0], m[1])
        }
        if (ring.gates.none { it.kind == PalisadeRing.Kind.WATER }) return null
        val bank = land.riverbank
        for (y in INSIDE_LANDING) {
            val x = land.streamX(y) + 1.05f
            val top = x + 1.6f
            // the planks, the stones and the path up to them, and a little room round them
            val x0 = x - 0.9f; val x1 = top + 2.4f; val y0 = y - 0.9f; val y1 = y + 0.9f
            val onPlot = land.centers.any { c -> x1 > c[0] - 1.5f && x0 < c[0] + 1.5f && y1 > c[1] - 1.5f && y0 < c[1] + 1.5f }
            if (onPlot || abs(y - land.roadY(x)) < 2.5f || hypot(x + 1f - bank[0], y - bank[1]) < 2.5f || (x + y) * 2f < RIVER_TOP) continue
            return floatArrayOf(x, y) to floatArrayOf(top, y)
        }
        return null
    }

    /** The landing at the water (world cells [x, y]; see [footpath]), or null. */
    fun landing(ring: PalisadeRing): FloatArray? = footpath(ring)?.first

    private const val SAMPLES = 720

    private val cache = LinkedHashMap<String, PalisadeRing>()

    /**
     * The ring of [age]'s clearing on [land]; [woods]: the woods below the village on this canvas (their path gets a gate),
     * or null.
     */
    fun ring(land: Terrain, age: Age, woods: Foreground?): PalisadeRing {
        val key = "${land.key}/$age/${woods != null}"
        synchronized(cache) { cache[key] }?.let { return it }
        return build(land, age, woods).also { r ->
            synchronized(cache) {
                if (cache.size >= 8) cache.remove(cache.keys.first())
                cache[key] = r
            }
        }
    }

    /** World point ([wx], [wy]) of a canvas offset from the fire. */
    private fun worldX(sx: Float, sy: Float) = (sx / 4f + sy / 2f) / 2f
    private fun worldY(sx: Float, sy: Float) = (sy / 2f - sx / 4f) / 2f

    private fun build(land: Terrain, age: Age, fg: Foreground?): PalisadeRing {
        // the rim [INSET] inside the forest's edge, round the fire from the left; it closes where the edge's noise meets
        // itself, straight left of the fire, with a short step in or out
        val n = SAMPLES
        val px = FloatArray(n + 1); val py = FloatArray(n + 1); val pa = FloatArray(n + 1)
        for (i in 0..n) {
            val a = (-PI + 2 * PI * i / n).toFloat()
            val r = land.forestEdge(age, a) - INSET
            val sx = r * cos(a); val sy = r * sin(a) / 2.4f
            px[i] = worldX(sx, sy); py[i] = worldY(sx, sy); pa[i] = a
        }
        val farBank = keepOffStream(land, px, py)
        val seg = FloatArray(n + 1) // segment i runs from sample i to i + 1; the last one closes the ring
        var total = 0f
        for (i in 0..n) {
            val j = if (i == n) 0 else i + 1
            seg[i] = hypot(px[j] - px[i], py[j] - py[i]); total += seg[i]
        }
        // stakes evenly along it
        val count = (total / STEP).roundToInt().coerceAtLeast(8)
        val step = total / count
        val xs = FloatArray(count); val ys = FloatArray(count); val angs = FloatArray(count)
        // the stakes on a stretch that keeps to the stream's far bank (see keepOffStream)
        val beyond = BooleanArray(count)
        var i = 0; var before = 0f
        for (k in 0 until count) {
            val t = k * step
            while (i < n && before + seg[i] < t) { before += seg[i]; i++ }
            val j = if (i == n) 0 else i + 1
            val f = if (seg[i] > 0f) ((t - before) / seg[i]).coerceIn(0f, 1f) else 0f
            xs[k] = px[i] + (px[j] - px[i]) * f; ys[k] = py[i] + (py[j] - py[i]) * f
            angs[k] = if (i == n) PI.toFloat() else pa[i] + (pa[j] - pa[i]) * f
            beyond[k] = farBank[i] || farBank[j]
        }
        // where it opens: the road (as the renderer lays it), the path into the woods, the stream
        val roadHalf = if (age >= Age.VAS) 1f else 0.55f
        val roadLeft = age >= Age.ZASELEK
        val open = arrayOfNulls<PalisadeRing.Kind>(count)
        for (k in 0 until count) {
            val x = xs[k]; val y = ys[k]
            val sx = (x - y) * 4f; val sy = (x + y) * 2f
            open[k] = when {
                abs(y - land.roadY(x)) < roadHalf + ROAD_CLEAR && (x > 0f || roadLeft) -> PalisadeRing.Kind.ROAD
                fg != null && sy > 0f && abs(fg.comp.fireX + sx - fg.pathX(fg.comp.fireY + sy)) < fg.pathHalf + PATH_CLEAR -> PalisadeRing.Kind.PATH
                abs(x - land.streamX(y)) < STREAM_CLEAR -> PalisadeRing.Kind.WATER
                // a lake or the sea: the water is the wall there, the ring ends on its shore either side
                land.water && land.wetScreen(sx, sy, SHORE) -> PalisadeRing.Kind.SHORE
                else -> null
            }
        }
        // the river gate: a postern where the ring runs by the stream on the village's side, clear of the other gates
        riverGate(land, xs, ys, open, beyond)?.let { k -> for (j in 0 until RIVER_W) open[(k + j) % count] = PalisadeRing.Kind.RIVER }
        val start = (0 until count).firstOrNull { open[it] == null } ?: return PalisadeRing(emptyList(), emptyList(), land = land)
        // each opening is a gate between the stakes either side of it: its posts
        val post = BooleanArray(count)
        val openings = ArrayList<IntArray>() // [kind, post before, post after]
        var run = -1
        for (m in 1..count) {
            val k = (start + m) % count
            if (open[k] != null) { if (run < 0) run = k; continue }
            if (run < 0) continue
            // the road before the path before the water before the river gate
            var kind = open[run]!!
            var q = run
            while (q != k) {
                val o = open[q]!!
                if (o.ordinal < kind.ordinal) kind = o
                q = (q + 1) % count
            }
            val a = (run - 1 + count) % count
            post[a] = true; post[k] = true
            openings += intArrayOf(kind.ordinal, a, k)
            run = -1
        }
        // heights: lower towards the near side; a gate's two posts alike, the river gate's a little over the stakes beside it
        val h = FloatArray(count)
        for (k in 0 until count) h[k] = FAR_H - (FAR_H - NEAR_H) * max(0f, sin(angs[k])) + ((Noise.hash(k, 71) ushr 5) % 3) * 0.5f
        for (o in openings) {
            val top = if (o[0] == PalisadeRing.Kind.RIVER.ordinal) max(h[o[1]], h[o[2]]) + RIVER_POST else max(max(h[o[1]], h[o[2]]) + POST_EXTRA, GATE_H)
            h[o[1]] = top; h[o[2]] = top
        }
        // the order they go up in: out from the main gate both ways
        val mainGate = openings.filter { it[0] == PalisadeRing.Kind.ROAD.ordinal }.maxByOrNull { xs[it[1]] + xs[it[2]] }
        val from = mainGate?.get(1) ?: (0 until count).maxBy { xs[it] - ys[it] }
        val index = IntArray(count) { -1 }
        val stakes = ArrayList<PalisadeRing.Stake>(count)
        for (m in 0 until count) {
            val k = (start + m) % count
            if (open[k] != null) continue
            val d = abs(k - from).let { min(it, count - it) }
            index[k] = stakes.size
            stakes += PalisadeRing.Stake(xs[k], ys[k], h[k], post[k], (d * 2f / count).coerceIn(0f, 1f), Noise.hash(k, 73))
        }
        val wet = ArrayList<PalisadeRing.Stake>()
        val gates = openings.map { o ->
            // [a] the farther post, drawn first
            val a = index[o[1]]; val b = index[o[2]]
            val (far, near) = if (stakes[a].depth <= stakes[b].depth) a to b else b to a
            val pa = stakes[a]; val pb = stakes[b]
            val kind = PalisadeRing.Kind.entries[o[0]]
            val wide = hypot(pa.x - pb.x, pa.y - pb.y)
            // level on the screen: the beam's rise over its run (a cell is 4 px across and 2 down per step in x and y)
            val run = abs((pb.x - pb.y) - (pa.x - pa.y)) * 4f; val rise = abs((pb.x + pb.y) - (pa.x + pa.y)) * 2f
            val level = rise <= run * LEVEL
            // a water gate is closed by shorter stakes standing in the stream from bank to bank, gaps between them for the current
            if (kind == PalisadeRing.Kind.WATER) {
                val n = (wide / WET_STEP).roundToInt() - 1
                for (j in 1..n) {
                    val u = j / (n + 1f)
                    val seed = Noise.hash(o[1], j, 79)
                    wet += PalisadeRing.Stake(pa.x + (pb.x - pa.x) * u, pa.y + (pb.y - pa.y) * u, WET_H + ((seed ushr 5) % 3) * 0.5f, false, (pa.u + pb.u) / 2f, seed)
                }
            }
            val beam = (kind == PalisadeRing.Kind.ROAD || kind == PalisadeRing.Kind.PATH) && wide <= MAX_LINTEL && level
            PalisadeRing.Gate(kind, far, near, lintel = beam)
        }
        return PalisadeRing(stakes, gates, wet, land)
    }

    /**
     * Where the river gate opens (its first sample, [RIVER_W] of them): among the samples where the ring runs by the
     * stream on the village's side ([RIVER_NEAR] to [RIVER_FAR] cells from its middle, the fire's side), clear of every
     * other opening by [RIVER_CLEAR] samples, the one nearest the stream bank's spot. Null when the ring never runs by it
     * on the village's side: where it takes the stream in ([beyond]: the stakes on its far bank), the water is inside.
     */
    private fun riverGate(land: Terrain, xs: FloatArray, ys: FloatArray, open: Array<PalisadeRing.Kind?>, beyond: BooleanArray): Int? {
        val n = xs.size
        val bank = land.riverbank
        // well clear of the other gates where the ring runs close by the stream; else nearer them, or a little farther from it
        for ((far, room) in listOf(RIVER_FAR to RIVER_CLEAR, RIVER_FAR to RIVER_CLEAR / 2, RIVER_FAR + 1.2f to RIVER_CLEAR / 2, RIVER_FAR + 1.2f to 2)) {
            var best: Int? = null; var bestD = Float.MAX_VALUE
            for (k in 0 until n) {
                val clear = (-room until RIVER_W + room).all { open[(k + it + n) % n] == null && !beyond[(k + it + n) % n] }
                if (!clear) continue
                // by the stream on the village's side, and well below the horizon, where the stakes stand full height
                val ok = (0 until RIVER_W).all { j -> val i = (k + j) % n; (xs[i] - land.streamX(ys[i])) in RIVER_NEAR..far && (xs[i] + ys[i]) * 2f > RIVER_TOP }
                if (!ok) continue
                val d = hypot(xs[k] - bank[0], ys[k] - bank[1])
                if (d < bestD) { bestD = d; best = k }
            }
            if (best != null) return best
        }
        return null
    }

    /**
     * The stream runs along the clearing's upper left rim, now just inside it, now just outside, and the forest's ragged
     * edge weaves across it. Where the ring runs along the stream (within [NEAR] of it, or over it) it keeps to one bank
     * the whole stretch, never switching between its ends ([px] moves along x, across the stream): where the ring goes over
     * the stream for [MIN_RUN] or more it takes the stream in and keeps to the far bank; else it keeps to the village's bank
     * and the stream stays outside. A far-bank stretch meets the rest of the ring across the water at each of its ends: the
     * water gates. Returns, by sample, whether it is on a far-bank stretch (its crossing at the end included).
     */
    private fun keepOffStream(land: Terrain, px: FloatArray, py: FloatArray): BooleanArray {
        val m = px.size
        val far = BooleanArray(m)
        val d = FloatArray(m) { px[it] - land.streamX(py[it]) }
        val start = (0 until m).firstOrNull { d[it] >= NEAR } ?: return far
        var n = 0
        while (n < m) {
            val first = (start + n) % m
            if (d[first] >= NEAR) { n++; continue }
            // a stretch along the stream: how far the ring goes over it there
            var count = 0; var over = 0f
            while (n + count < m && d[(first + count) % m] < NEAR) {
                val q = (first + count) % m; val r = (q + 1) % m
                if (d[q] < 0f) over += hypot(px[r] - px[q], py[r] - py[q])
                count++
            }
            val takeIn = over >= MIN_RUN
            for (j in 0 until count) {
                val q = (first + j) % m
                val bank = land.streamX(py[q])
                if (takeIn) { px[q] = min(px[q], bank - BANK); far[q] = true } else px[q] = max(px[q], bank + BANK)
            }
            n += count
        }
        return far
    }
}

/**
 * Draws the palisade's ring (see [Palisade]) in the style of the old corner of stakes: pointed logs with a bark line,
 * snow on their points in winter, and at a closer look ([fine]) the grain and the withies binding them. Gate posts are
 * stouter, under a lintel; the water gate's grating hangs over the stream. From level 2 torches burn at the gates, from
 * level 3 a pennant flies over the main gate. A damaged palisade has a breach: stakes broken off and knocked down.
 */
internal class PalisadePainter(private val s: SceneCtx, private val buildings: BuildingPainter) {
    private val i get() = s.iso
    private val c get() = s.canvas
    private val env get() = s.env
    private val k get() = s.k
    private val fine get() = s.k >= 2

    /**
     * Adds every stake of [ring] to the painter's order: [add] takes its depth (as the trees', x + y) and how to draw it,
     * [tag] gets the id of each object drawn, for taps. [grow]: the construction, 0 to 1 (1 = standing); [horizon] and
     * the canvas bound what is drawn (a stake behind the far treeline or off a close-up's window isn't). The ring makes
     * way for what stands at the clearing's edge: a landmark (its footprint, and in front of it), and the things of the
     * [keep] circles (world x, y and radius in cells: the rocks, the woodpile), so they stand in a gap of the palisade.
     */
    fun gather(
        b: Building, ring: PalisadeRing, landmarks: List<LandmarkSpot>, keep: List<FloatArray>, grow: Float, horizon: Int,
        add: (Float, () -> Unit) -> Unit, tag: (Int) -> Unit,
    ) {
        val stakes = ring.stakes
        if (stakes.isEmpty()) return
        // what shows: on the canvas, below the horizon (the ring of a big clearing runs on behind the far treeline, the
        // stakes getting lower towards it), off the landmarks
        val shown = BooleanArray(stakes.size)
        val low = FloatArray(stakes.size)
        val x0 = -12f * k; val x1 = c.width + 12f * k
        for ((n, st) in stakes.withIndex()) {
            val bx = i.sx(st.x, st.y); val by = i.sy(st.x, st.y, 0f)
            if (bx < x0 || bx > x1 || by - (st.h + Palisade.POINT) * k > c.height) continue
            low[n] = (((by - horizon) / k - 2f) / TAPER).coerceIn(0f, 1f)
            if (low[n] < 0.3f) continue
            if (landmarks.isNotEmpty() && landmarks.any { hides(st, it) }) continue
            if (keep.any { hypot(st.x - it[0], st.y - it[1]) < it[2] }) continue
            shown[n] = true
        }
        // the breach: a few stakes knocked down, those beside them broken off (towards the lower left, where the wolves come from)
        val breach = if (b.damaged) stakes.indices.minByOrNull { abs(breachAngle(stakes[it]) - BREACH_ANGLE) } ?: -1 else -1
        val gates = ring.gates.associateBy { it.b }
        val torches = if (b.level < 2) emptySet() else ring.gates.filter { it.kind == PalisadeRing.Kind.ROAD || it.kind == PalisadeRing.Kind.PATH }.flatMap { listOf(it.a, it.b) }.toSet()
        val flag = if (b.level >= 3) ring.main?.a ?: -1 else -1
        for ((n, st) in stakes.withIndex()) {
            if (!shown[n]) continue
            val f = ((grow - st.u * 0.7f) / 0.3f).coerceIn(0f, 1f) * low[n]
            if (f <= 0f) continue
            // whole: standing, built and not tapering towards the horizon (a gate's beam and its torches want that)
            val whole = f >= 1f
            val off = if (breach < 0) 99 else abs(n - breach).let { min(it, stakes.size - it) }
            val gate = gates[n]
            val tappable = landmarks.none { behind(st, it) }
            add(st.depth) {
                val id = s.newObject(Pal.OUTLINE)
                if (tappable) tag(id)
                when {
                    off <= 1 -> fallen(st)
                    off <= 3 -> stake(st, st.h * 0.4f, broken = true)
                    else -> stake(st, st.h * f, broken = false)
                }
                // a gate's nearer post carries the lintel from the farther one
                if (gate != null && whole && off > 3) {
                    val far = stakes[gate.a]
                    if (shown[gate.a] && low[gate.a] >= 1f && gate.lintel && grow >= 1f) lintel(far, st)
                }
                if (whole && off > 3 && n in torches) torch(st)
                if (whole && n == flag) buildings.pennant(b.level, st.x, st.y, st.h + Palisade.POINT - 1f, pole = 5f)
            }
        }
        // the water gates' stakes in the stream, each in its own order, going up with the posts beside them
        for (st in ring.wet) {
            val bx = i.sx(st.x, st.y); val by = i.sy(st.x, st.y, 0f)
            if (bx < x0 || bx > x1 || by - (st.h + WET_POINT) * k > c.height) continue
            val lo = (((by - horizon) / k - 2f) / TAPER).coerceIn(0f, 1f)
            if (lo < 0.3f || (landmarks.isNotEmpty() && landmarks.any { hides(st, it) })) continue
            val f = ((grow - st.u * 0.7f) / 0.3f).coerceIn(0f, 1f) * lo
            if (f <= 0f) continue
            val tappable = landmarks.none { behind(st, it) }
            add(st.depth) {
                val id = s.newObject(Pal.OUTLINE)
                if (tappable) tag(id)
                wetStake(st, st.h * f)
            }
        }
        // the dust of the building work at the main gate
        if (grow < 1f) {
            val g = ring.gatePoint()
            val gx = i.sx(g[0], g[1]); val gy = i.sy(g[0], g[1], 0f)
            s.fx { dust(gx, gy, grow) }
        }
    }

    /** The main gate's rectangle on this canvas (left, top, right, bottom), for [VillageRenderer.hitRects]. */
    fun gateRect(ring: PalisadeRing): IntArray {
        val g = ring.main ?: return IntArray(4)
        val a = ring.stakes[g.a]; val b = ring.stakes[g.b]
        val top = max(a.h, b.h) + Palisade.POINT
        val l = min(i.sx(a.x, a.y), i.sx(b.x, b.y)) - 3f * k; val r = max(i.sx(a.x, a.y), i.sx(b.x, b.y)) + 3f * k
        val t = min(i.sy(a.x, a.y, top), i.sy(b.x, b.y, top)); val bot = max(i.sy(a.x, a.y, 0f), i.sy(b.x, b.y, 0f))
        return intArrayOf(l.toInt(), t.toInt(), r.toInt(), bot.toInt())
    }

    private fun breachAngle(st: PalisadeRing.Stake): Float = VillageLayout.angle((st.x - st.y) * 4f, (st.x + st.y) * 2f)

    /** Whether stake [st] stands on landmark [l]'s footprint, or in front of it, high enough to hide some of it. */
    private fun hides(st: PalisadeRing.Stake, l: LandmarkSpot): Boolean {
        if (l.covers(st.x, st.y, 0.3f)) return true
        if (st.x + st.y <= l.cx + l.cy) return false
        val sx = (st.x - st.y) * 4f; val sy = (st.x + st.y) * 2f
        val r = l.screenRect()
        return sx > r[0] - 3f && sx < r[2] + 3f && sy - st.h - Palisade.POINT < r[3]
    }

    /** Whether stake [st] stands behind landmark [l], where it shows through it (a tap there is the landmark's). */
    private fun behind(st: PalisadeRing.Stake, l: LandmarkSpot): Boolean {
        if (st.x + st.y > l.cx + l.cy) return false
        val sx = (st.x - st.y) * 4f; val sy = (st.x + st.y) * 2f
        val r = l.screenRect()
        return sx > r[0] - 2f && sx < r[2] + 2f && sy > r[1] && sy - st.h - Palisade.POINT < r[3]
    }

    /** One stake: a squared log, its point, the bark line down its left edge so every stake reads on its own. */
    private fun stake(st: PalisadeRing.Stake, h: Float, broken: Boolean) {
        val w = if (st.post) Palisade.POST_WIDTH else Palisade.WIDTH
        val x0 = st.x - w / 2f; val y0 = st.y - w / 2f
        shadow(st, w)
        val hh = max(h, 1f)
        i.box(x0, y0, 0f, w, w, hh, Pal.LOG_L, Pal.LOG_M, Pal.LOG_D)
        i.post(x0, y0 + w, 0f, hh, Pal.WOOD_X)
        if (broken) {
            // splintered: a jagged, charred top
            i.px(x0 + w * 0.7f, y0 + w * 0.3f, hh + 1f, Pal.LOG_L)
            i.px(st.x, st.y, hh, CHAR)
            return
        }
        i.pyramid(x0, y0, hh, w, w, Palisade.POINT, Pal.LOG_L, Pal.LOG_D)
        if (fine) {
            // the grain down the front face, and the withies binding the stakes to each other
            i.line(x0 + w / 2f, y0 + w, 1f, x0 + w / 2f, y0 + w, hh - 1f, Pal.LOG_D)
            val band = (hh * 0.62f).roundToInt().toFloat()
            i.line(x0, y0 + w, band, x0 + w, y0 + w, band, Pal.WOOD_X)
            i.line(x0 + w, y0, band, x0 + w, y0 + w, band, Pal.WOOD_X)
            if (s.k >= 3 && hh > 5f) {
                i.line(x0, y0 + w, 2f, x0 + w, y0 + w, 2f, Pal.WOOD_X)
                if ((st.seed ushr 3) % 4 == 0) i.px(x0 + w * 0.3f, y0 + w, hh * 0.35f, Pal.WOOD_X) // a knot
            }
        }
        i.px(st.x, st.y, hh + Palisade.POINT, TIP)
        if (env.snow) { i.px(st.x, st.y, hh + 1f, Pal.SNOW_L); if (st.post) i.px(st.x, st.y, hh + 2f, Pal.SNOW_M) }
    }

    /** A stake knocked down, lying on the ground outwards. */
    private fun fallen(st: PalisadeRing.Stake) {
        val len = st.h / 4f * 0.5f + 0.6f
        val dx = if (st.x < 0f) -1f else 1f
        i.box(st.x - 0.2f, st.y - 0.2f, 0f, 0.4f, len, 1.5f, Pal.LOG_L, Pal.LOG_M, CHAR)
        i.px(st.x + dx * 0.1f, st.y, 0f, Pal.WOOD_X)
    }

    /** A squared beam from gate post [a] to gate post [b], just below their points. */
    private fun lintel(a: PalisadeRing.Stake, b: PalisadeRing.Stake) {
        val z = min(a.h, b.h) - 0.5f
        val dx = b.x - a.x; val dy = b.y - a.y
        val len = hypot(dx, dy).coerceAtLeast(0.01f)
        val ox = dx / len * 0.35f; val oy = dy / len * 0.35f
        val ax = a.x - ox; val ay = a.y - oy; val bx = b.x + ox; val by = b.y + oy
        i.quad(ax, ay, z, bx, by, z, bx, by, z - 2f, ax, ay, z - 2f, Pal.LOG_M)
        i.line(ax, ay, z, bx, by, z, Pal.LOG_L)
        i.line(ax, ay, z - 2f, bx, by, z - 2f, Pal.WOOD_X)
    }

    /**
     * A stake of a water gate: shorter and thinner than the ring's, dark with the wet up its foot, the current breaking
     * white round it where it stands in the water (ice round it in winter).
     */
    private fun wetStake(st: PalisadeRing.Stake, h: Float) {
        val w = Palisade.WIDTH * 0.8f
        val x0 = st.x - w / 2f; val y0 = st.y - w / 2f
        val hh = max(h, 1f)
        val inWater = abs(st.x - s.land.streamX(st.y)) < 0.95f
        i.box(x0, y0, 0f, w, w, hh, Pal.LOG_L, Pal.LOG_M, Pal.LOG_D)
        i.post(x0, y0 + w, 0f, hh, Pal.WOOD_X)
        if (inWater) i.line(x0, y0 + w, 1f, x0 + w, y0 + w, 1f, Pal.LOG_D) // the wet line
        i.pyramid(x0, y0, hh, w, w, WET_POINT, Pal.LOG_L, Pal.LOG_D)
        i.px(st.x, st.y, hh + WET_POINT, TIP)
        if (env.snow) i.px(st.x, st.y, hh + 1f, Pal.SNOW_L)
        if (!inWater) return
        // the water breaking round its foot, upstream and down
        val foam = if (env.snow) Pal.ICE_L else FOAM
        i.px(st.x - 0.35f, st.y - 0.1f, 0f, foam); i.px(st.x + 0.3f, st.y + 0.25f, 0f, foam)
        if (fine && ((s.time * 3).toInt() + st.seed) % 3 != 0) i.px(st.x + 0.1f, st.y + 0.4f, 0f, foam)
    }

    /** A torch on a gate post, on the side facing the viewer, below the point. */
    private fun torch(st: PalisadeRing.Stake) {
        val tx = st.x + 0.4f; val ty = st.y + 0.4f; val z = st.h - 2f
        i.px(tx, ty, z - 1f, Pal.WOOD_X)
        val sx = i.ix(tx, ty); val sy = i.iy(tx, ty, z)
        val f = ((s.time * 10 + st.seed % 7).toInt() and 1)
        val was = c.penEmissive
        c.penEmissive = true
        s.sprite(sx, sy) { c.set(sx, sy, Pal.FLAME[1]); c.set(sx, sy - 1 - f, Pal.FLAME[2]) }
        c.penEmissive = was
        if (env.windows > 0.2f) s.light(sx.toFloat(), sy.toFloat(), 16f, 0.6f)
    }

    /** A small shadow on the ground at the stake's foot, to the lower right. */
    private fun shadow(st: PalisadeRing.Stake, w: Float) {
        val cx = i.sx(st.x, st.y) + 1.5f * k; val cy = i.sy(st.x, st.y, 0f) + w * 2f * k
        val rx = 2f * k; val ry = 1f * k
        for (y in (cy - ry).toInt()..(cy + ry).toInt()) for (x in (cx - rx).toInt()..(cx + rx).toInt()) {
            val ddx = (x + 0.5f - cx) / rx; val ddy = (y + 0.5f - cy) / ry
            if (ddx * ddx + ddy * ddy <= 1f) c.shadeGround(x, y, 0.82f)
        }
    }

    private fun dust(x: Float, y: Float, p: Float) {
        for (j in 0 until 5) {
            val a = j * 2 * PI / 5 + p * 2
            val dx = (cos(a) * (4f + p * 6f)).toFloat() * k; val dy = (sin(a) * (2f + p * 3f)).toFloat() * k - (2f + p * 6f) * k
            c.ditherCircle(x + dx, y + dy, (1.5f + p * 2.5f) * k, Col.hex(0xD8C8A8), (1f - p) * 0.9f)
        }
    }

    private companion object {
        /** The bare wood of a stake's sharpened point, and charred wood. */
        val TIP = Col.hex(0xD9B98A)
        val CHAR = Col.hex(0x2E2622)

        /** The water breaking round a water gate's stakes; their points, shorter than the ring's (px). */
        val FOAM = Col.hex(0xE8F2FA)
        const val WET_POINT = 2f

        /** Where a damaged palisade is breached (in the clearing's metric: the lower left, towards the forest spot). */
        val BREACH_ANGLE = (0.72 * PI).toFloat()

        /** The stakes standing within this many px below the horizon get lower towards it, into the far treeline. */
        const val TAPER = 4f
    }
}
