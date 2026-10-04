package si.lanisce.lani.game.render

import si.lanisce.lani.game.Age
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Land
import si.lanisce.lani.game.Landscape
import si.lanisce.lani.game.Landscape.COAST
import si.lanisce.lani.game.Landscape.HILLS
import si.lanisce.lani.game.Landscape.LAKE
import si.lanisce.lani.game.Landscape.MOUNTAINS
import si.lanisce.lani.game.PLOTS_PER_AGE
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The land a village stands on, laid out (see [Land]; GAME.md, "The land"): where the stream runs and where the road
 * crosses it, how the road bends, where the clearing's edge runs, where the plots lie and in which order, how thick the
 * woods are, where the rocks and the woodpile are, and where the pond, the meadow and the path lie in the woods below
 * ([Woods]); on the landscapes that have them, the lake or the sea ([lake], [sea]), the rises of the land ([relief]),
 * the vineyards' terraces ([vineyard]), the torrent ([torrent]) and what the horizon shows ([skyline]). Generated once
 * from the land's seed within its landscape, the same every time (pure, cached): world cells (see [Iso]) round the fire,
 * and nominal canvas px from it for what is laid out on the screen.
 *
 * The classic land ([CLASSIC]) is the fixed valley every village had before generated land, number for number: the
 * villages from then (their land marked classic) keep their look pixel for pixel. A generated land keeps what the game
 * needs: the fire in the middle, the plots on open ground off the stream, the water, the road and the stream's bank, in
 * reach of the fire and on every canvas of the town, the road through the clearing and over the stream, the stream on the
 * left (the palisade's far-bank rule and the stream's spot, mill and footbridge take it there), the woods' path, pond and
 * meadow below.
 */
class Terrain private constructor(val land: Land) {
    val landscape: Landscape = land.landscape
    val classic: Boolean = landscape == Landscape.CLASSIC

    /** What caches key on: every classic land is the same one. */
    val key: String = if (classic) "classic" else "${landscape.id}:${land.seed}"

    /** The land's dice: the [i]th number, uniform in [0, 1). */
    private fun r(i: Int): Float = Noise.rnd(land.seed, i, DICE)
    private fun between(i: Int, a: Float, b: Float): Float = a + (b - a) * r(i)

    // ------------------------------------------------------------------ the stream

    /** Its course: x = base + amp · sin(y · freq + phase) + a smaller wiggle (world cells along y); the hills' is a brook, the mountains' a torrent. */
    private val sBase = if (landscape == HILLS) between(1, -13.5f, -12f) else between(1, -13.5f, -11.5f)
    private val sFreq = if (landscape == MOUNTAINS) between(3, 0.3f, 0.42f) else between(3, 0.22f, 0.42f)
    /** No steeper than the classic stream's bends (its amplitude times its frequency, about 0.5), so a mill fits on its bank. */
    private val sAmp = between(2, 0.8f, min(if (landscape == HILLS) 1.6f else 2.2f, 0.55f / sFreq))
    private val sPhase = between(4, 0f, 2f * PI.toFloat())
    private val sAmp2 = if (landscape == MOUNTAINS) between(5, 0.2f, 0.3f) else between(5, 0f, 0.35f)
    private val sFreq2 = if (landscape == MOUNTAINS) between(6, 0.9f, 1.1f) else between(6, 0.6f, 0.9f)
    private val sPhase2 = between(7, 0f, 2f * PI.toFloat())

    /** The stream's middle at world y: it runs down the clearing's left side, from the horizon to the lower left. */
    fun streamX(y: Float): Float =
        if (classic) -13f + 1.5f * sin(y * 0.35f)
        else sBase + sAmp * sin(y * sFreq + sPhase) + sAmp2 * sin(y * sFreq2 + sPhase2)

    /** A mountain torrent: narrower, breaking white. */
    val torrent: Boolean = landscape == MOUNTAINS

    /** The stream's water either side of its middle (cells), its deep middle, and its banks. */
    val streamHalf: Float = if (torrent) 0.72f else 0.95f
    val streamDeep: Float = if (torrent) 0.45f else 0.7f
    val streamBank: Float = if (torrent) 1.12f else 1.35f

    // ------------------------------------------------------------------ the road

    /** How the road bends away from the village on its right and its left (cells aside at 30 cells out). */
    private val bendR = between(10, -0.3f, 0.3f)
    private val bendL = between(11, -0.25f, 0.25f)

    /**
     * The road's middle at world x: straight through the plaza and past the first plots (along x, y = 0), bending gently
     * away beyond them. The classic road is straight.
     */
    fun roadY(x: Float): Float {
        if (classic) return 0f
        val t = abs(x) - ROAD_STRAIGHT
        if (t <= 0f) return 0f
        return (if (x > 0f) bendR else bendL) * t * t / 60f
    }

    /**
     * How far along the road (world x) it is still [inward] px inside the clearing of [age], where the road leaves the
     * clearing to the right: the road's spot (see [TownAnchors.spotAnchor]) stands there, 8 px in.
     */
    fun roadOut(age: Age, inward: Float = 8f): Float {
        var x = 2f
        while (x < 60f) {
            val n = x + 0.1f; val y = roadY(n)
            val sx = (n - y) * 4f; val sy = (n + y) * 2f
            if (VillageLayout.screenMetric(sx, sy) > forestEdge(age, VillageLayout.angle(sx, sy)) - inward) return x
            x = n
        }
        return x
    }

    /** Where the road crosses the stream on its bridge (world cells). */
    val bridge: FloatArray = if (classic) floatArrayOf(streamX(0f), 0f) else {
        var y = 0f
        var x = streamX(y)
        repeat(3) { y = roadY(x); x = streamX(y) }
        floatArrayOf(x, y)
    }

    /**
     * The stream's near bank above the bridge, where people sit and fish (world cells; the "riverbank" spot, see
     * [TownAnchors.spotWorld]): no plot comes this close.
     */
    val riverbank: FloatArray = if (classic) floatArrayOf(streamX(3f) + 2.4f, 3f) else {
        val y = 3f + roadY(streamX(3f))
        floatArrayOf(streamX(y) + 2.4f, y)
    }

    // ------------------------------------------------------------------ the clearing

    private val eOff = between(20, 0f, 90f)
    private val eSeed = 3 + (land.seed ushr 3 and 0xffff)
    private val eAmp = between(21, 14f, 20f)
    /** A broad bulge of the clearing to one side (px of the metric), and where it points. */
    private val eLobe = between(22, 0f, 5f)
    private val eLobeAt = between(23, -PI.toFloat(), PI.toFloat())

    /** The forest's edge (screen metric from the fire, see [VillageLayout.screenMetric]) at angle [ang]: the clearing with a ragged, noisy rim. */
    fun forestEdge(age: Age, ang: Float): Float =
        if (classic) clearing(age) + (Noise.v1(ang * 3f + 10f, 2) - 0.5f) * 18f
        else clearing(age) + (Noise.v1(ang * 3f + eOff, eSeed) - 0.5f) * eAmp + eLobe * cos(ang - eLobeAt) + bank(ang)

    /**
     * How far the clearing reaches out towards the stream's bank where it would leave the bank's spot in the woods (a smooth
     * bump round the spot's angle): whoever fishes there sits in the clearing from the first age on, as in the classic land.
     */
    private fun bank(ang: Float): Float {
        if (bankBump <= 0f) return 0f
        var d = abs(ang - bankAt)
        if (d > PI.toFloat()) d = 2f * PI.toFloat() - d
        val t = d / BANK_WIDTH
        return if (t >= 1f) 0f else bankBump * (1f - t * t) * (1f - t * t)
    }

    /** Radius (screen metric) of the forest clearing for an age: 30 px beyond the farthest plot of the age. */
    fun clearing(age: Age): Float = clearings[age.ordinal]

    // ------------------------------------------------------------------ the woods below

    /** Where the path, the pond, the meadow and the rest lie in the woods below the village; null: the classic land's. */
    internal val woods: Woods? = if (classic) null else Woods(
        a1 = between(60, 9f, 15f), f1 = between(61, 0.034f, 0.048f), p1 = between(62, -0.5f, 0.5f),
        a2 = between(63, 3f, 6f), f2 = between(64, 0.1f, 0.14f), p2 = between(65, 1.5f, 2.5f),
        // whole px from the path's column and the forest's edge, as the classic land's are from theirs
        pondDX = whole(66, 66, 82), pondDY = whole(67, 38, 50), pondRx = whole(68, 17, 22), pondRy = whole(69, 7, 9),
        meadowDY = whole(70, 66, 80), meadowRx = whole(71, 28, 34), meadowRy = whole(72, 12, 15),
        stackDX = whole(73, 19, 26), stackDY = whole(74, 14, 20),
        seatDX = whole(75, 10, 14), seatDY = whole(76, 4, 8),
        kopaDX = whole(77, 48, 60), kopaDY = whole(78, 140, 156),
    )

    /** A whole number from [a] to [b], as a float. */
    private fun whole(i: Int, a: Int, b: Int): Float = (a + (r(i) * (b - a + 1)).toInt().coerceAtMost(b - a)).toFloat()

    // ------------------------------------------------------------------ the water: a lake, the sea

    /**
     * The lake at the clearing's right, a little above the road (nominal px from the fire: its middle and radii, [cx, cy,
     * rx, ry]), or null. Its shore is a little ragged; a path runs along it on the village's side.
     */
    internal val lake: FloatArray? = if (landscape != LAKE) null else {
        // farther out, then a little smaller, where the whole one would leave too little room for the plots
        val a = between(80, -0.1f, 0.04f) * PI.toFloat()
        val d = between(81, 118f, 142f)
        val rx = between(82, 44f, 58f); val ry = between(83, 16f, 21f)
        fun shape(out: Float, s: Float) = floatArrayOf((d + out) * cos(a), (d + out) * sin(a) / 2.4f, rx * s, ry * s)
        listOf(shape(0f, 1f), shape(12f, 1f), shape(22f, 0.92f), shape(32f, 0.84f), shape(42f, 0.76f))
            .firstOrNull { l -> roomy(candidates { sx, sy -> ellipse(l, sx, sy, PLOT_ROOM) }) } ?: shape(52f, 0.7f)
    }
    private val lakeSeed = 31 + (land.seed ushr 5 and 0xff)

    private val seaSeed = 37 + (land.seed ushr 9 and 0xff)

    /** Whether nominal ([sx], [sy]) is within the ellipse of lake [l] grown by [margin] px (half that up and down). */
    private fun ellipse(l: FloatArray, sx: Float, sy: Float, margin: Float): Boolean {
        val dx = (sx - l[0]) / (l[2] + margin); val dy = (sy - l[1]) / (l[3] + margin * 0.5f)
        return dx * dx + dy * dy < 1.12f
    }

    /** Distance from the lake's middle in its radii (under 1 is water), at nominal ([sx], [sy]) from the fire; 9 without a lake. */
    fun lakeD(sx: Float, sy: Float): Float {
        val l = lake ?: return 9f
        val dx = (sx - l[0]) / l[2]; val dy = (sy - l[1]) / l[3]
        if (abs(dx) > 1.8f || abs(dy) > 1.8f) return 9f
        return sqrt(dx * dx + dy * dy) + (Noise.v2(sx * 0.07f, sy * 0.14f, lakeSeed) - 0.5f) * 0.2f
    }

    /**
     * The sea along the left (the coast): the shore's x at the horizon's row (nominal px from the fire), how it comes in to
     * the right going down, and how far right it may come (well left of the woods' pond). Null without the sea.
     */
    internal val sea: FloatArray? = if (landscape != COAST) null else {
        val w = woods!!
        // the pond's left bank from the fire (the path's column where the pond lies, less the pond's offset and radius)
        val pondLeft = 8f + w.a1 * sin(w.pondDY * w.f1 + w.p1) + w.a2 * sin(w.pondDY * w.f2 + w.p2) - w.pondDX - w.pondRx
        val s0 = between(85, -150f, -132f); val slope = between(86, 0.08f, 0.2f)
        // further out where it would leave too little room for the plots
        floatArrayOf(0f, 10f, 20f).map { floatArrayOf(s0 - it, slope, pondLeft - 18f) }
            .firstOrNull { s -> roomy(candidates { sx, sy -> sx < shore(s, sy) + BEACH + PLOT_ROOM }) } ?: floatArrayOf(s0 - 30f, 0f, pondLeft - 18f)
    }

    /** The sea's shore at nominal row [sy] from the fire: the sea lies left of it, the beach just right. */
    fun shoreX(sy: Float): Float = sea?.let { shore(it, sy) } ?: -1e4f

    /** The shore of the sea [s] ([shoreX]). */
    private fun shore(s: FloatArray, sy: Float): Float = min(s[0] + s[1] * (sy + VillageLayout.HORIZON), s[2]) + (Noise.v1(sy * 0.05f, seaSeed) - 0.5f) * 10f

    /** Whether there is a lake or the sea. */
    val water: Boolean = lake != null || sea != null

    /**
     * Whether nominal ([sx], [sy]) from the fire is in the lake or the sea, or within [margin] px of it (the beach counts
     * as the sea's: nothing stands on it but people).
     */
    fun wetScreen(sx: Float, sy: Float, margin: Float = 0f): Boolean {
        if (sea != null && sx < shoreX(sy) + BEACH + margin) return true
        val l = lake ?: return false
        return ellipse(l, sx, sy, margin)
    }

    /** [wetScreen] of world cells ([wx], [wy]). */
    fun wet(wx: Float, wy: Float, margin: Float = 0f): Boolean = water && wetScreen((wx - wy) * 4f, (wx + wy) * 2f, margin)

    /** Nominal ([sx], [sy]) from the fire, moved in towards the fire until it is [margin] px off the water. */
    fun inland(sx: Float, sy: Float, margin: Float = 4f): FloatArray {
        var x = sx; var y = sy; var n = 0
        while (n < 80 && wetScreen(x, y, margin)) { x *= 0.97f; y *= 0.97f; n++ }
        return floatArrayOf(x, y)
    }

    // ------------------------------------------------------------------ the plots

    /** Plot centres in world cells, index = plot: [x, y, metric, order]. */
    val centers: List<FloatArray> = plots()

    fun origin(plot: Int): FloatArray { val p = centers[plot.coerceIn(0, VillageLayout.MAX_PLOTS - 1)]; return floatArrayOf(p[0] - 1.5f, p[1] - 1.5f) }

    private val clearings = FloatArray(Age.entries.size) { a ->
        val n = PLOTS_PER_AGE[a].coerceIn(1, VillageLayout.MAX_PLOTS)
        var m = 0f
        for (k in 0 until n) m = max(m, centers[k][2])
        m + 30f
    }

    /** The stream bank's spot's angle, and how far the first age's clearing must reach out to take it in (see [bank]). */
    private val bankAt: Float = VillageLayout.angle((riverbank[0] - riverbank[1]) * 4f, (riverbank[0] + riverbank[1]) * 2f)
    private val bankBump: Float = if (classic) 0f else run {
        val m = VillageLayout.metric(riverbank[0], riverbank[1])
        max(0f, m + BANK_ROOM - forestEdge(Age.OGENJ, bankAt))
    }

    /**
     * Where plot i lies (see [VillageLayout]): in rows along the road, [COL] cells apart in a row and [ROW] cells from row
     * to row, every other row shifted along by [STAGGER] of a step and each plot a little off the line; off the fire,
     * the stream and its bank's spot, the lake and the sea; on every canvas of the town; nearest the fire first, with a
     * little noise in the order. A generated land shifts the rows along the road, bends them with it, and has its own noise.
     */
    private fun plots(): List<FloatArray> {
        val cand = candidates { sx, sy -> water && wetScreen(sx, sy, PLOT_ROOM) }
        check(cand.size >= VillageLayout.MAX_PLOTS) { "the land ${land.kind}:${land.seed} has room for ${cand.size} plots" }
        return cand.take(VillageLayout.MAX_PLOTS)
    }

    /**
     * Whether the places [cand] leave room for every plot, the last of them no farther out than a town's clearing may reach
     * (so the town's clearing, 30 px beyond it, stays on the canvases of the town).
     */
    private fun roomy(cand: List<FloatArray>): Boolean = cand.size >= VillageLayout.MAX_PLOTS && cand[VillageLayout.MAX_PLOTS - 1][2] <= FARTHEST_PLOT

    /** The places a plot could be (see [plots]), nearest the fire first, off what [wet] says is water (nominal px from the fire). */
    private fun candidates(wet: (Float, Float) -> Boolean): List<FloatArray> {
        val salt = if (classic) 0 else 1 + (land.seed and 0x3ff)
        val shift = if (classic) 0f else between(30, -COL / 2f, COL / 2f)
        // four rows each side of the road: -24, -17, -10, -3, 3, 10, 17, 24
        val rows = FloatArray(8) { k -> if (k < 4) -FIRST_ROW - (3 - k) * ROW else FIRST_ROW + (k - 4) * ROW }
        val cand = ArrayList<FloatArray>()
        for ((k, ry) in rows.withIndex()) {
            val off = if (k % 2 == 1) STAGGER * COL else 0f
            for (xi in -7..7) {
                val jit = ((Noise.hash(k, xi, 99 + salt) ushr 4) % 3 - 1) * 0.5f
                val cx: Float; val cy: Float
                if (classic) { cx = xi * COL + off + jit; cy = ry } else { cx = xi * COL + off + shift + jit; cy = ry + roadY(cx) }
                if (cx + 1.5f > -2.2f && cx - 1.5f < 2.2f && cy + 1.5f > -2.2f && cy - 1.5f < 2.2f) continue
                // off the stream and its banks, and clear of where people sit by the water
                if (abs(cx - streamX(cy)) < if (classic) 3.3f else STREAM_ROOM) continue
                if (abs(cx - riverbank[0]) < 2.6f && abs(cy - riverbank[1]) < 2.6f) continue
                // below the horizon, and on the screen of every canvas the town has (see SceneFit.town)
                val sx = (cx - cy) * 4f; val sy = (cx + cy) * 2f
                if (sx < -124f || sx > 124f || sy < -48f || sy > 56f) continue
                // off the lake and the sea, the whole building and a little room
                if (wet(sx, sy)) continue
                cand.add(floatArrayOf(cx, cy, VillageLayout.metric(cx, cy), VillageLayout.metric(cx, cy) + (Noise.rnd(k, xi, 7 + salt) - 0.5f) * 10f))
            }
        }
        cand.sortBy { it[3] }
        return cand
    }

    // ------------------------------------------------------------------ the woods round the clearing

    /** The trees' own noise (see the renderer's trees): 0 for the classic land. */
    val treeSalt: Int = if (classic) 0 else 100 + (land.seed ushr 7 and 0xfff)

    /**
     * How many trees the forest's first rows leave out (the classic 0.45), where glades open deep in the woods (0.78), and
     * where spruce takes over from beech (0.55): the hills' woods are open beech, the mountains' spruce with pastures, the
     * coast's broadleaf.
     */
    val thin: Float = when {
        classic -> 0.45f
        landscape == HILLS -> between(40, 0.55f, 0.7f)
        landscape == MOUNTAINS -> between(40, 0.5f, 0.65f)
        landscape == COAST -> between(40, 0.4f, 0.6f)
        else -> between(40, 0.3f, 0.58f)
    }
    val glades: Float = when {
        classic -> 0.78f
        landscape == HILLS -> between(41, 0.62f, 0.72f)
        landscape == MOUNTAINS -> between(41, 0.6f, 0.7f)
        landscape == COAST -> between(41, 0.66f, 0.78f)
        else -> between(41, 0.7f, 0.84f)
    }
    val spruce: Float = when {
        classic -> 0.55f
        landscape == HILLS -> between(42, 1.02f, 1.18f)
        landscape == MOUNTAINS -> between(42, 0.2f, 0.32f)
        landscape == COAST -> between(42, 0.62f, 0.78f)
        else -> between(42, 0.45f, 0.65f)
    }

    /** How many of the grid's places in the clearing and in the woods' glades hold a rock (the mountains' boulders). */
    val rocksMeadow: Float = if (landscape == MOUNTAINS) 0.11f else 0.075f
    val rocksGlade: Float = if (landscape == MOUNTAINS) 0.27f else 0.18f

    // ------------------------------------------------------------------ the rises and the vineyards

    /** How much the land rises and falls (the grass lighter on the slopes facing the light, darker away): 0 is flat. */
    val relief: Float = when (landscape) { HILLS -> 0.34f; MOUNTAINS -> 0.44f; else -> 0f }
    private val reliefFreq = if (landscape == MOUNTAINS) 0.09f else 0.055f
    private val reliefSeed = 41 + (land.seed ushr 11 and 0xff)

    /** The light on the land's slope at world cell ([cx], [cy]): -1 (facing away) to 1 (facing the light, up the screen). */
    fun slope(cx: Int, cy: Int): Float {
        val a = Noise.v2(cx * reliefFreq, cy * reliefFreq, reliefSeed)
        val b = Noise.v2((cx + 2) * reliefFreq, (cy + 2) * reliefFreq, reliefSeed)
        return ((b - a) * 6f).coerceIn(-1f, 1f)
    }

    /**
     * The vineyards' terraces on the slopes round the village (the hills): three sectors outside the clearing where the
     * woods would be (their middles and half widths, angles): behind it on the left and on the right, and on its right
     * side between the rocks and the road.
     */
    private val vines: FloatArray? = if (landscape != HILLS) null else floatArrayOf(
        between(90, -0.8f, -0.58f) * PI.toFloat(), between(91, 0.12f, 0.18f) * PI.toFloat(),
        between(92, -0.42f, -0.28f) * PI.toFloat(), between(93, 0.08f, 0.12f) * PI.toFloat(),
        between(94, -0.08f, 0.06f) * PI.toFloat(), between(95, 0.1f, 0.14f) * PI.toFloat(),
    )
    val vineSeed = 51 + (land.seed ushr 13 and 0xff)

    /** Whether angle [ang] (see [VillageLayout.angle]) is in a vineyard's sector (outside the clearing its ground is terraces). */
    fun vineyard(ang: Float): Boolean {
        val v = vines ?: return false
        for (i in v.indices step 2) if (abs(ang - v[i]) < v[i + 1]) return true
        return false
    }

    /** Whether there are vineyards at all. */
    val vineyards: Boolean = vines != null

    // ------------------------------------------------------------------ the horizon

    /** What the village's horizon shows on this land (see [Backdrop]). */
    internal val skyline: Skyline = when (landscape) {
        HILLS -> Skyline(alps = 0.5f, hills = 1.7f)
        MOUNTAINS -> Skyline(alps = 1.3f, hills = 1.15f)
        LAKE -> Skyline(alps = 1.1f, hills = 1f)
        COAST -> Skyline(alps = 0.7f, hills = 0.85f, sea = true, seaReach = 0.62f)
        else -> Skyline.CLASSIC
    }

    // ------------------------------------------------------------------ the spots round the clearing

    /**
     * Where the rocks are at the clearing's upper right, and the small woodpile at its lower right (angles, see
     * [VillageLayout.angle]): the land's own, turned towards the classic land's until the spot is on every canvas of the
     * town at every age and off the water (see [offCanvas]).
     */
    val rocksAngle: Float = if (classic) -0.3f * PI.toFloat() else edgeAngle(between(50, -0.34f, -0.22f), -0.28f, ROCKS_INWARD, -0.2f, -0.18f, -0.15f, -0.36f, 0.16f, 0.2f, -0.62f, -0.66f)
    val woodpileAngle: Float = if (classic) 0.4f * PI.toFloat() else edgeAngle(between(51, 0.36f, 0.45f), 0.4f, WOODPILE_INWARD, 0.36f, 0.34f, 0.33f, 0.32f, 0.31f, 0.3f)

    /**
     * The first angle (× π) from [from] towards [to], then of [more], where a spot [inward] px inside the clearing's edge is on
     * every canvas.
     */
    private fun edgeAngle(from: Float, to: Float, inward: Float, vararg more: Float): Float {
        val steps = 12
        val tried = ArrayList<Float>()
        for (i in 0..steps) tried += (from + (to - from) * i / steps) * PI.toFloat()
        for (m in more) tried += m * PI.toFloat()
        // the first that fits; else the one that misses by least
        return tried.firstOrNull { offCanvas(it, inward) == 0f } ?: tried.minBy { offCanvas(it, inward) }
    }

    /**
     * How far (px) the point [inward] px inside the clearing's edge at angle [a] misses being on every canvas of the town at
     * every age, 0 when it is: below the horizon and above the bottom of a phone on its side (400 × 180: the fire 110 px
     * down, the horizon 60 px above it), inside the narrowest (320 px wide), with a little room, and off the water.
     */
    private fun offCanvas(a: Float, inward: Float): Float = Age.entries.maxOf { age ->
        val r = forestEdge(age, a) - inward
        val dx = r * cos(a); val dy = r * sin(a) / 2.4f
        maxOf(0f, dy - 64f, -54f - dy, abs(dx) - 150f) + if (water && wetScreen(dx, dy, 10f)) 100f else 0f
    }

    companion object {
        /** A plot's distance from the next one in its row, and from row to row (cells). */
        const val COL = 5.5f
        const val ROW = 7f

        /** The rows nearest the road, each side of it (cells from its middle). */
        const val FIRST_ROW = 3f

        /** Every other row is shifted along the road by this share of [COL]. */
        const val STAGGER = 0.6f

        /** The road runs straight this far each way from the fire (cells) before it bends. */
        private const val ROAD_STRAIGHT = 7f

        /** The stream bank's spot stands at least this far inside the clearing (px), in a bump of this half width (radians). */
        private const val BANK_ROOM = 8f
        private const val BANK_WIDTH = 0.6f

        /** How far inside the clearing's edge the rocks and the small woodpile are (px; see [TownAnchors.spotAnchor]). */
        internal const val ROCKS_INWARD = 7f
        internal const val WOODPILE_INWARD = 10f

        /** How far a generated land's plots keep from the stream's middle (cells): the palisade may keep to its bank beside them. */
        private const val STREAM_ROOM = 3.9f

        /** How far out (the clearing's metric) the last plot of a land with water may lie; the classic land's is at 125. */
        private const val FARTHEST_PLOT = 132f

        /** The sea's beach (px wide), and the room a plot keeps from the water (px). */
        const val BEACH = 7f
        private const val PLOT_ROOM = 16f

        /** The land dice's own salt. */
        private const val DICE = 0x51ad

        private val cache = LinkedHashMap<String, Terrain>()

        /** The classic valley: the land of every village from before generated land. */
        val CLASSIC: Terrain by lazy { Terrain(Land.CLASSIC) }

        /** The laid-out [land] (cached: the renderer asks every frame). */
        fun of(land: Land): Terrain {
            if (land.landscape == Landscape.CLASSIC) return CLASSIC
            val key = "${land.kind}:${land.seed}"
            synchronized(cache) { cache[key] }?.let { return it }
            return Terrain(land).also { t ->
                synchronized(cache) {
                    if (cache.size >= 8) cache.remove(cache.keys.first())
                    cache[key] = t
                }
            }
        }

        /** The land of [state]'s village: the classic valley for a save from before generated land. */
        fun of(state: GameState): Terrain = of(state.land ?: Land.CLASSIC)
    }
}

/**
 * Where the things of the woods below lie on a generated land (see [Foreground]; nominal px): the path's two waves
 * ([a1] · sin(u · [f1] + [p1]) + [a2] · sin(u · [f2] + [p2]), u rows below the forest's edge), the pond [pondDX] left of
 * the path and [pondDY] below the edge, the meadow on the path [meadowDY] below it, the woodcutter's stack right of the
 * path, the high seat right of the meadow, the charcoal pile deep in the woods right of the path.
 */
internal class Woods(
    val a1: Float, val f1: Float, val p1: Float, val a2: Float, val f2: Float, val p2: Float,
    val pondDX: Float, val pondDY: Float, val pondRx: Float, val pondRy: Float,
    val meadowDY: Float, val meadowRx: Float, val meadowRy: Float,
    val stackDX: Float, val stackDY: Float, val seatDX: Float, val seatDY: Float, val kopaDX: Float, val kopaDY: Float,
)

/**
 * What the village's horizon shows (see [Backdrop]): the Alps' height ([alps], 1 the classic range), the rolling foothills'
 * ([hills]), and the sea along the horizon on the left ([sea], as far across as [seaReach] of the width).
 */
internal data class Skyline(val alps: Float = 1f, val hills: Float = 1f, val sea: Boolean = false, val seaReach: Float = 0.46f) {
    companion object {
        val CLASSIC = Skyline()
    }
}
