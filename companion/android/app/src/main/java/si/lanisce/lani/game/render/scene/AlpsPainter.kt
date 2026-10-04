package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.Season
import si.lanisce.lani.game.scene.Poke
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * V gorah: a planina (a mountain pasture) high above a lake under Triglav, the way Bohinj looks from Vogar. In front of us
 * the pasture: a dry-stone wall (suhozid) across the left with a step stile, a path winding past the shepherd's wooden
 * hut and over the brow, cows grazing with bells round their necks, a mountain stream on the right pouring over a rock
 * step in a little waterfall, a big mossy stone at its side, a marmot on a rock. A spruce and a larch frame the view.
 * Below the brow, far down, the lake lies between forested slopes, the peaks mirrored in it and ruffled by the breeze;
 * the little church with its bell tower stands on the right shore by a stone bridge. Behind it all the Julian Alps rise
 * in ranges, paler and bluer the farther they are, and over them Triglav, three-headed, with the Aljaž turret on top.
 *
 * The year shows: snow comes down the peaks from October and lies on everything in winter, the lake freezing at its
 * edges, the larches bare; spring leaves snow in the gullies and crocuses on the pasture, summer gentians and alpine
 * roses; the larches turn gold in October. The peaks glow pink at sunrise and sunset (alpenglow); at night the hut's
 * window and the church are lit and the moon lies on the lake. A dialog can ring the cows' bells ("cowbells"), the
 * church bell ("bell"), make a fish jump in the lake ("fish") and send the alpine choughs off the crags ("birds"). Fog
 * lies in the valley over the lake; rain veils the peaks first. Taps: the cow lifts its head and lows, then shakes its
 * head so its bell clangs; the marmot stands up and whistles, then dives into its burrow and peeks out again.
 *
 * Closer up ([detail] 2 and 3) the same view is drawn finer: the wall's joints, lichen and moss, the hut's logs and
 * shingles, the cows' faces and bells, the curtain of the waterfall and its foam, needles on the spruce, the church's
 * windows and the bridge's arches.
 */
internal class AlpsPainter : WildPainter() {
    /** Toward Triglav, north-north-west: the Plough and the Pole Star stand over the peaks. */
    override val skyFacing = 330.0

    override val art = "alps"
    override val horizon = 63
    override val pokes = listOf(Poke("cow", listOf(2.2, 2.8), rest = 3.0), Poke("marmot", listOf(2.4, 6.5), rest = 3.0))

    /** Picture pixels per scene canvas pixel: every size and offset in pixels is multiplied by it. */
    private val z: Int get() = detail

    // ------------------------------------------------------------------ the plan
    // On the pasture, in metres: the path, the stream (its middle and half its width at each depth), the brow where the
    // pasture ends and the land drops to the lake, the wall, the hut, the cows, the trees, the rocks.
    private fun path(d: Float) = 0.4f - 1.6f * sin((d - 8f) * 0.16f) - max(0f, d - 16f) * 0.15f
    private val pathHalf = 0.46f
    private fun brook(d: Float) = 3.1f + 0.34f * (d - 10f) + 0.4f * sin(d * 0.55f)
    private fun brookHalf(d: Float) = 0.46f + 0.022f * (d - 10f)
    private fun brow(x: Float) = 25.5f + 1.6f * sin(x * 0.23f + 0.8f) + 0.035f * min(x * x, 150f)
    private val fallD = 14.6f
    private val poolD = 13.2f
    private val wallD = 12.4f; private val wallA = -9.6f; private val wallB = -1.55f; private val wallH = 1.05f; private val stileX = -2.1f
    private val hutX = -7.2f; private val hutD = 22f; private val hutW = 4.4f; private val hutL = 5f
    private val cowX = -5.2f; private val cowD = 17.2f
    private val spruceX = -9.4f; private val spruceD = 17f
    private val larchX = 10.6f; private val larchD = 18.2f
    private val stoneX = 5.1f; private val stoneD = 11.2f
    private val marmotX = 7.4f; private val marmotD = 16.4f

    /** The far cows on the pasture: side, depth, facing left. */
    private val herd = listOf(Triple(-8.3f, 20.6f, true), Triple(-2.9f, 24f, false), Triple(1.3f, 20.8f, true))

    /** The lake lies this far (m) below the pasture: its rows are those of a plane seen from EYE + DROP. */
    private val drop = 22f

    // ------------------------------------------------------------------ the frame's layers, per picture column

    private var farTop = FloatArray(0)
    private var farLit = BooleanArray(0)
    private var massTop = FloatArray(0)
    private var massPeak = IntArray(0)
    private var midTop = FloatArray(0)
    private var flankTop = FloatArray(0)
    private var shore = FloatArray(0)
    private var meadow = FloatArray(0)
    private var browRow = IntArray(0)
    private val skyLut = IntArray(SKY_STEPS + 1)
    private var sunLeft = true
    private var alpenglow = 0f
    /** How much taller the mountains stand in this view (see [layers]). */
    private var massLift = 1f
    private var bareMonths = false

    /** The scene canvas's column of picture column [x] (the stage's, 0..240 on it). */
    private fun ux(x: Int): Float = (x + 0.5f - ox) / z

    /** Height above the horizon (scene canvas px) of the picture's row [y]. */
    private fun upOf(y: Int): Float = (hz - (y + 0.5f)) / z

    private val items = ArrayList<Pair<Float, () -> Unit>>()

    override fun paint() {
        setup()
        val z = z
        sunLeft = (sunAt()?.first ?: vx) < vx
        alpenglow = fx("glow") ?: if (env.dark < 0.6f && frame.sky.gloom < 0.5f) (1f - abs(env.sun - 0.07f) / 0.2f).coerceIn(0f, 1f) else 0f
        bareMonths = env.month == 12 || env.month <= 2
        layers()
        for (i in 0..SKY_STEPS) skyLut[i] = skyAt(i / SKY_STEPS.toFloat())
        vistaSky(4, 61, moonX = ox + 96 * z, moonY = oy + 13 * z)

        // far away: the ranges, Triglav, the lower mountains, the forested slopes down to the lake, the lake
        prop(0) { back(FAR) }
        thing("mountain", outline = 0) { back(MASS) }
        thing("peak", outline = 0) { back(PEAK); turret() }
        prop(0) { back(MID) }
        prop(0) { back(FLANK) }
        prop(0) { flock(fxOn("birds")) }
        thing("lake", outline = 0) { lake() }
        thing("bridge", outline = 0) { bridge(false) }
        thing("church", outline = 0) { church(false) }
        prop(0) { jumps(fxOn("fish")) }

        // the pasture we stand on, up to its brow
        pasture0()
        thing("pasture", outline = 0) { pasture() }
        thing("path", outline = 0) { pathPixels() }
        thing("stream", outline = STREAM_EDGE) { streamPixels() }

        items.clear()
        // the forest at the pasture's sides, far and near
        for ((k, t) in FOREST.withIndex()) {
            val (x, d, larch) = t
            val hgt = 14f + Noise.rnd(k, 701) * 6f
            items.add(d to { prop(Pal.OUTLINE_TREE) { if (larch) larchTree(x, d, hgt, k) else spruceTree(x, d, hgt, k) } })
        }
        items.add(hutD to { thing("hut") { hut() } })
        for ((k, cw) in herd.withIndex()) items.add(cw.second to { prop { cow(cw.first, cw.second, cw.third, k + 1, near = false) } })
        items.add(marmotD to { pokeable("marmot") { marmot() } })
        items.add(spruceD to { thing("spruce", outline = Pal.OUTLINE_TREE) { spruceTree(spruceX, spruceD, 17f, 0, big = true) } })
        items.add(larchD to { thing("larch", outline = Pal.OUTLINE_TREE) { larchTree(larchX, larchD, 16f, 0, big = true) } })
        items.add(cowD to {
            thing("cow") { cow(cowX, cowD, false, 0, near = true) }
            thing("cowbell", slop = 1) { bell(cowBell[0], cowBell[1], pm(cowD), cowBell[2]) }
        })
        items.add(fallD + 0.05f to { prop { ledge() }; thing("waterfall", outline = 0, slop = 1) { fall() } })
        items.add(wallD to { thing("wall") { dryWall(wallA, wallB, wallD, wallH, 71, stile = stileX) } })
        items.add(stoneD to { groundShadow(stoneX, stoneD - 0.2f, 0.8f); thing("stone") { boulder(stoneX, stoneD, 1.5f, 1.0f, 72, mossy = true) } })
        for (p in peopleAt("wall")) items.add(11.5f to { groundShadow(-2.75f, 11.5f, 0.5f); person(p, gx(-2.75f, 11.5f).toInt(), gy(11.5f).toInt(), flip = false) })
        for (p in peopleAt("path")) items.add(13.3f to { val x = path(13.3f) + 0.15f; groundShadow(x, 13.3f, 0.5f); person(p, gx(x, 13.3f).toInt(), gy(13.3f).toInt(), flip = true) })
        for (p in peopleAt("stream")) items.add(12.2f to { groundShadow(2.3f, 12.2f, 0.5f); person(p, gx(2.3f, 12.2f).toInt(), gy(12.2f).toInt(), flip = false) })
        wildlife()
        items.sortByDescending { it.first }
        for ((_, draw) in items) draw()

        nearEdge()
        foregroundGrass(0, (w * 0.28f).toInt(), 73)
        foregroundGrass((w * 0.84f).toInt(), w, 74)
        cowbellRings()
        fireflies(0, hz + 40 * z, w, h, 10, 75)
        s.fx { s.effects.leaves(env.season, env.month) }
    }

    /**
     * The mountains' wild animals of the frame ([out]): chamois, a mother and her kid, grazing up at the pasture's edge by
     * the forest; an ibex on a crag at the brow, against the lake.
     */
    private fun wildlife() {
        if (out("chamois")) {
            val u = spot("chamois")
            val x = 7.2f + u * 3.6f; val d = 22.6f + u * 1.4f
            items.add(d to { groundShadow(x, d, 0.5f); creature("chamois") { chamois(gx(x, d), gy(d), 34f / d, left = u > 0.5f, look = sin(t * 0.4 + u * 7) > 0.4) } })
            items.add(d + 0.8f to { prop { chamois(gx(x + (if (u > 0.5f) -1.6f else 1.6f), d + 0.8f), gy(d + 0.8f), 22f / (d + 0.8f), left = u > 0.5f) } })
        }
        if (out("ibex")) {
            val u = spot("ibex")
            val x = 11.8f + u * 1.4f; val d = 25.6f
            val rock = 1.5f
            items.add(d to {
                prop { crag(x, d, rock) }
                creature("ibex") { ibex(gx(x, d - 0.2f), hy(d - 0.2f, rock * 0.94f), 40f / d, left = u > 0.4f) }
            })
        }
    }

    /** A grey crag of limestone [tall] m high at ([x], [d]), lit on its top and the sun's side, a crack down it. */
    private fun crag(x: Float, d: Float, tall: Float) {
        val p = pm(d); val bx = gx(x, d); val by = gy(d)
        val rock = Col.hex(0xA8A49A); val lit = Col.hex(0xD2CEC2); val shade = Col.hex(0x7A766E)
        c.polyBegin(); c.polyAdd(bx - 1.1f * p, by); c.polyAdd(bx - 0.7f * p, by - tall * 0.8f * p); c.polyAdd(bx - 0.1f * p, by - tall * p)
        c.polyAdd(bx + 0.6f * p, by - tall * 0.9f * p); c.polyAdd(bx + 1.0f * p, by); c.polyFill(rock)
        c.polyBegin(); c.polyAdd(bx + 0.1f * p, by); c.polyAdd(bx + 0.2f * p, by - tall * 0.85f * p); c.polyAdd(bx + 0.6f * p, by - tall * 0.9f * p)
        c.polyAdd(bx + 1.0f * p, by); c.polyFill(if (sunLeft) shade else lit)
        c.line((bx - 0.7f * p).toInt(), (by - tall * 0.8f * p).toInt(), (bx - 0.1f * p).toInt(), (by - tall * p).toInt(), if (env.snow) Pal.SNOW_L else lit)
        c.line((bx - 0.3f * p).toInt(), (by - tall * 0.6f * p).toInt(), (bx - 0.2f * p).toInt(), (by - tall * 0.15f * p).toInt(), shade)
    }

    // ------------------------------------------------------------------ the mountains: layers by column

    /** Heights above the horizon (scene canvas px) of every layer's crest in every picture column, and the lake's shore. */
    private fun layers() {
        val z = z
        // a taller view has sky to spare above the stage: the mountains rise into it
        val lift = 1f + 0.5f * (oy / z / 55f).coerceIn(0f, 1f)
        if (farTop.size != w) {
            farTop = FloatArray(w); farLit = BooleanArray(w); massTop = FloatArray(w); massPeak = IntArray(w)
            midTop = FloatArray(w); flankTop = FloatArray(w); shore = FloatArray(w); meadow = FloatArray(w); browRow = IntArray(w)
        }
        for (x in 0 until w) {
            val u = ux(x)
            // the far ranges: ridged peaks across the whole view
            val a = 1f - abs(Noise.v1(u * 0.03f, 611) * 2f - 1f)
            val b = 1f - abs(Noise.v1(u * 0.075f, 612) * 2f - 1f)
            farTop[x] = (11f + 30f * (a * a * 0.7f + b * 0.3f)) * lift
            // Triglav and its neighbours: the highest peak of each column, a jagged crest
            var best = 0f; var bi = 0
            var i = 0
            while (i < PEAKS.size) {
                val f = 1f - abs(u - PEAKS[i]) / PEAKS[i + 2]
                if (f > 0f) { val hh = PEAKS[i + 1] * f.toDouble().pow(1.35).toFloat(); if (hh > best) { best = hh; bi = i / 3 } }
                i += 3
            }
            if (best > 3f) {
                val jag = 1f - abs(Noise.v1(u * 0.11f, 603) * 2f - 1f)
                best += (jag - 0.5f) * 3.2f * min(1f, best / 14f) + (Noise.v1(u * 0.45f, 601) - 0.5f) * 1.8f + (Noise.v1(u * 1.6f, 602) - 0.5f) * 0.9f
            }
            massTop[x] = best * lift; massPeak[x] = bi
            // the lower mountains at the lake's far end, forested
            midTop[x] = 3f + 8f * Noise.v1(u * 0.035f, 621) + 5f * (1f - abs(Noise.v1(u * 0.09f, 622) * 2f - 1f)) - max(0f, abs(u - 125f) - 70f) * 0.2f
            // the forested slopes either side of the lake, a row of spruce spires along their crest
            val sl = ((104f - u) / 150f).coerceIn(0f, 1.6f); val sr = ((u - 146f) / 150f).coerceIn(0f, 1.6f)
            val crest = if (u < 125f) 50f * sl.toDouble().pow(1.3).toFloat() else 47f * sr.toDouble().pow(1.3).toFloat()
            val cell = u / 2.6f - floor(u / 2.6f)
            flankTop[x] = crest - 2f + (1f - abs(cell - 0.5f) * 2f) * 2.4f * min(1f, crest / 4f)
            // where the water begins: the slopes' feet, lower (nearer) toward the sides
            val lumpy = (Noise.v1(u * 0.35f, 605) - 0.5f) * 2.2f * min(1f, (abs(u - 125f) - 21f).coerceAtLeast(0f) / 10f)
            val foot = -2f - max(0f, 104f - u) * 0.45f - min(max(0f, u - 146f), 59f) * 0.35f - max(0f, u - 205f) * 1.2f + lumpy
            shore[x] = hz - foot * z
            // a strip of meadow on the right shore, where the church stands
            val band = 4.4f * (1f - abs(u - 184f) / 20f).coerceIn(0f, 1f).let { it * (3f - 2f * it) }
            meadow[x] = shore[x] - band * z
        }
        massLift = lift
        for (x in 0 until w) farLit[x] = (farTop[min(w - 1, x + 2 * z)] - farTop[max(0, x - 2 * z)]).let { if (sunLeft) it < 0 else it > 0 }
        // where the pasture's brow cuts the view, per column (the first pasture row from the top)
        for (x in 0 until w) {
            var py = hz + z
            while (py < h) {
                val d = depthAt(py)
                if (d <= brow(sideAt(x, d))) break
                py++
            }
            browRow[x] = py
        }
    }

    /**
     * What the land behind the lake has at picture pixel ([x], [y]) (above the shore), nearest first: the forested slopes,
     * the lower mountains, Triglav's massif, the far ranges; 0 for the sky. [only] limits it to one layer (drawing), 0: any
     * (the lake's reflection). In the frame's light already.
     */
    private fun backAt(x: Int, y: Int, only: Int = 0): Int {
        if (x < 0 || x >= w || y >= shore[x]) return 0
        val up = upOf(y)
        val u = ux(x)
        if (up <= flankTop[x]) return if (only == 0 || only == FLANK) flank(x, y, u, up) else 0
        if (up <= midTop[x]) return if (only == 0 || only == MID) mid(x, y, u, up) else 0
        if (up <= massTop[x]) {
            val peak = massPeak[x] <= 2 && up > 38f * massLift
            return if (only == 0 || (only == MASS && !peak) || (only == PEAK && peak)) mass(x, y, u, up) else 0
        }
        if (up <= farTop[x]) return if (only == 0 || only == FAR) far(x, y, u, up) else 0
        return 0
    }

    /** Draws one layer of the land behind the lake (see [backAt]), glowing: it is lit already. */
    private fun back(layer: Int) {
        c.penEmissive = true
        for (x in max(0, c.left) until min(w, c.right)) {
            val top = when (layer) { FAR -> farTop[x]; MID -> midTop[x]; FLANK -> flankTop[x]; else -> massTop[x] }
            val y0 = (hz - top * z - 1).toInt()
            for (y in max(y0, c.top) until min(shore[x].toInt(), c.bottom)) {
                val col = backAt(x, y, layer)
                if (col != 0) c.set(x, y, col)
            }
        }
        c.penEmissive = false
    }

    /** How far down (a share of the height) the snow reaches on the peaks this month. */
    private fun snowLine(): Float = when (env.month) { 12, 1, 2 -> 0f; 3 -> 0.12f; 4 -> 0.3f; 5 -> 0.52f; 6 -> 0.72f; 7, 8 -> 0.8f; 9 -> 0.74f; 10 -> 0.55f; else -> 0.35f }

    /**
     * Triglav's massif: pale limestone faces either side of the ridge falling from each peak, lit on the sun's side; ribs
     * and gullies down the faces, ledges slanting across them; snow above the month's snow line, lingering in the gullies
     * and on the ledges; pink in the low sun of dawn and dusk; the valley's haze toward its foot.
     */
    private fun mass(x: Int, y: Int, u: Float, up: Float): Int {
        val k = massPeak[x]
        val cu = PEAKS[k * 3]; val ph = PEAKS[k * 3 + 1] * massLift
        val ridge = cu + (Noise.v1(up * 0.11f, 631) - 0.5f) * 5f * (1f - up / ph).coerceIn(0f, 1f)
        val lit = (u < ridge) == sunLeft
        val q = (u - cu) / max(3f, ph - up + 3f)
        val rib = Noise.v1(q * 9f + k * 3.1f, 632)
        val gully = rib > 0.68f
        val ledge = ((up + (u - cu) * 0.32f) / 6.5f).let { it - floor(it) } < 0.13f
        // streaks slanting down and out across the faces
        val side = if (u < ridge) -1f else 1f
        val streak = Noise.v1((u - cu) * 0.8f + up * 0.5f * side + k * 5f, 635)
        val rel = up / (56f * massLift)
        val line = snowLine()
        val snowy = rel > line + (Noise.v2(u * 0.2f, up * 0.25f, 633) - 0.5f) * 0.22f ||
            (gully && rel > line - 0.28f) || (ledge && rel > line - 0.16f)
        // the forest up the massif's foot, to a ragged treeline of spires
        val spire = (1f - abs((u / 2.4f - floor(u / 2.4f)) - 0.5f) * 2f) * 2.2f
        val treeline = (0.24f + (Noise.v1(u * 0.13f, 634) - 0.5f) * 0.16f) * 56f * massLift + spire
        val forest = !snowy && up < treeline
        var col = when {
            snowy -> if (lit) SNOW_LIT else SNOW_SHADE
            forest -> forest(u, up, treeline, Col.scale(FOREST_FAR, 0.82f), FOREST_FAR, FOREST_FAR_L, 636, 2.4f).let { f -> if (bareMonths && f != FOREST_FAR_L) SNOW_SHADE else f }
            gully -> ROCK_GULLY
            lit -> if (streak > 0.72f || rib < 0.25f) ROCK_L2 else ROCK_L
            else -> if (streak > 0.74f) ROCK_M else if (ledge) ROCK_GULLY else ROCK_D
        }
        if (snowy && !lit && streak > 0.8f && rel < 0.9f) col = ROCK_D
        if (alpenglow > 0f && !forest) col = if (lit) Col.mix(col, if (snowy) GLOW_SNOW else GLOW_ROCK, 0.5f * alpenglow) else Col.mix(col, GLOW_SHADE, 0.3f * alpenglow)
        return env.lit(Col.mix(col, env.skyHorizon, 0.14f + 0.16f * (1f - rel).coerceIn(0f, 1f)))
    }

    /** The far ranges: blue in the haze, snow on their tops most of the year, lit on the side facing the sun. */
    private fun far(x: Int, y: Int, u: Float, up: Float): Int {
        val lit = farLit[x]
        val rel = up / (41f * massLift)
        val snowy = rel > snowLine() * 0.9f + 0.05f + (Noise.v2(u * 0.3f, up * 0.3f, 641) - 0.5f) * 0.2f
        var col = if (snowy) (if (lit) SNOW_LIT else SNOW_SHADE) else if (lit) FAR_L else FAR_D
        if (alpenglow > 0f && lit) col = Col.mix(col, if (snowy) GLOW_SNOW else GLOW_ROCK, 0.35f * alpenglow)
        return env.lit(Col.mix(col, env.skyHorizon, 0.5f))
    }

    /** The lower mountains at the lake's far end: forest in tiers of spires, blue with the distance; snowy in winter. */
    private fun mid(x: Int, y: Int, u: Float, up: Float): Int {
        var col = forest(u, up, midTop[x], FOREST_FAR, FOREST_FAR_L, Col.scale(FOREST_FAR, 0.8f), 651, 2.2f)
        if (env.snow) col = if (col == FOREST_FAR_L) SNOW_LIT else if (col == FOREST_FAR) SNOW_SHADE else Col.mix(col, SNOW_SHADE, 0.5f)
        // the haze thickens toward the lake
        val low = ((midTop[x] - up) / 10f).coerceIn(0f, 1f)
        return env.lit(Col.mix(col, env.skyHorizon, 0.3f + 0.2f * low))
    }

    /**
     * The forested slopes either side of the lake: spruce in tiers of spires, lit on the sun's side, patches of larch
     * (gold in October, grey in winter) and of beech lower down (orange in autumn), rock showing near the crest; the
     * meadow strip on the right shore; snow on it all in winter.
     */
    private fun flank(x: Int, y: Int, u: Float, up: Float): Int {
        if (y >= meadow[x]) {
            val g = env.grass
            val n = Noise.v2(u * 0.3f, up * 0.8f, 661) + (Dither.at(x, y) - 0.5f) * 0.3f
            return env.lit(Col.mix(if (n > 0.55f) g[0] else g[1], env.skyHorizon, 0.14f))
        }
        val depth = flankTop[x] - up
        val patch = Noise.v2(u * 0.05f, up * 0.07f, 662)
        val larch = patch > 0.64f
        val beech = !larch && depth > 14f && Noise.v2(u * 0.06f, up * 0.09f, 663) > 0.6f
        val (dk, md, lt) = when {
            larch -> when (env.month) {
                10, 11 -> Triple(Col.hex(0x9A6A1E), Col.hex(0xC8962E), Col.hex(0xE8C050))
                12, 1, 2, 3 -> Triple(Col.hex(0x5E5248), Col.hex(0x746658), Col.hex(0x8A7C6A))
                4, 5 -> Triple(Col.hex(0x4A7A2E), Col.hex(0x6A9A3A), Col.hex(0x9ACB5A))
                else -> Triple(Col.hex(0x3A6A2E), Col.hex(0x527E36), Col.hex(0x74A048))
            }
            beech -> when (env.season) {
                Season.AUTUMN -> Triple(Col.hex(0x7A3A1A), Col.hex(0xA8582A), Col.hex(0xD08A3A))
                Season.WINTER -> Triple(Col.hex(0x5A5048), Col.hex(0x6E655A), Col.hex(0x847A6C))
                else -> Triple(Col.hex(0x2E5A2A), Col.hex(0x437A36), Col.hex(0x62984A))
            }
            else -> Triple(SPRUCE_D, SPRUCE_M, SPRUCE_L)
        }
        var col = forest(u, up, flankTop[x], dk, md, lt, 671, 2.6f)
        if (depth < 7f && Noise.v2(u * 0.12f, up * 0.2f, 664) > 0.72f) col = if ((Noise.rnd(x / z, y / z, 665) < 0.5f) == sunLeft) ROCK_L else ROCK_D
        if (env.snow) col = if (col == lt) Pal.SNOW_L else if (col == md) Pal.SNOW_D else Col.mix(col, Pal.SNOW_D, 0.45f)
        return env.lit(Col.mix(col, env.skyHorizon, 0.1f + 0.1f * ((up + 10f) / 60f).coerceIn(0f, 1f)))
    }

    /**
     * Forest seen from afar: tiers of little spires in columns [cw] scene px wide, each lit on the sun's side, the gaps
     * between them dark. [crest] the layer's top (scene px above the horizon) at this column.
     */
    private fun forest(u: Float, up: Float, crest: Float, dark: Int, mid: Int, light: Int, seed: Int, cw: Float): Int {
        val cell = floor(u / cw).toInt(); val fu = u / cw - cell
        val tier = (crest - up + Noise.rnd(cell, seed) * 3f) / (cw * 1.4f)
        val ft = tier - floor(tier) // 0 at a spire's tip .. 1 at its foot
        if (abs(fu - 0.5f) > ft * 0.5f + 0.1f) return dark
        return if ((fu < 0.5f) == sunLeft && ft > 0.25f) light else mid
    }

    /** The Aljaž turret on Triglav's top: a little tin cylinder with its flag, closer up. */
    private fun turret() {
        val z = z
        val x = (ox + (PEAKS[0] + 0.5f) * z).toInt()
        var top = hz.toFloat()
        for (xx in x - z..x + z) if (xx in 0 until w) top = min(top, hz - massTop[xx] * z)
        val y = top.toInt() + z / 2
        val tin = env.lit(Col.mix(Col.hex(0x7A7E86), env.skyHorizon, 0.2f))
        c.penEmissive = true
        c.fillRect(x, y - 2 * z, z, 2 * z, tin)
        if (z > 1) {
            c.set(x, y - 2 * z - 1, env.lit(Col.hex(0xB0B4BA)))
            c.vline(x + z, y - 3 * z - z / 2, y - 2 * z, tin)
            c.fillRect(x + z + 1, y - 3 * z - z / 2, z, z / 2 + 1, env.lit(Pal.FLAG_RED))
        }
        c.penEmissive = false
    }

    // ------------------------------------------------------------------ the lake

    private fun skyRefl(y: Int): Int = skyLut[((y.toFloat() / (hz + 2f * z)).coerceIn(0f, 1f) * SKY_STEPS).toInt()]

    /**
     * The lake: what stands behind it upside down in it (mirrored at its shore, column by column), darker and bluer the
     * nearer the water, broken by the breeze into wavering bands; the glitter of the low sun or the moon; ice creeping out
     * from the shores in winter; the church and the bridge in it too.
     */
    private fun lake() {
        val z = z
        c.penEmissive = true
        val deep = env.lit(Col.hex(0x1E4C64))
        val icy = bareMonths
        val sun = sunAt()
        val glintX = if (sun != null && env.sun < 0.4f) sun.first else if (sun == null && env.dark > 0.3f && env.moonShows) (ox + 96f * z) else Float.NaN
        val glintCol = if (sun != null) Col.mix(Col.hex(0xFFF2D0), Col.hex(0xFFB86A), (1f - env.sun * 2f).coerceIn(0f, 1f)) else Col.hex(0xD8DCE8)
        for (x in max(0, c.left) until min(w, c.right)) {
            val sh = shore[x]
            for (y in max(sh.toInt(), c.top) until min(browRow[x] + 1, c.bottom)) {
                val dn = (y + 0.5f - sh) / z
                val breeze = Noise.v2((x / z) * 0.03f + t.toFloat() * 0.03f, (y / z) * 0.12f, 681)
                val amp = (0.5f + dn * 0.06f) * (0.5f + breeze) * z
                val wob = sin(y * 0.85f / z + t.toFloat() * 1.6f + breeze * 5f) * amp
                val sx = (x + wob).toInt().coerceIn(0, w - 1)
                val my = (2f * sh - y - 1f).toInt()
                var refl = backAt(sx, my)
                if (refl == 0) refl = skyRefl(my)
                val near = (dn / 38f).coerceIn(0f, 1f)
                var col = Col.mix(Col.scale(refl, 0.84f), deep, 0.12f + 0.32f * near)
                // bands the breeze draws across it
                val band = Noise.rnd(floor((x / z + t * 2.2) / 9.0).toInt(), y / z, 682)
                if (band > 0.92f - breeze * 0.06f) col = Col.mix(col, skyRefl(0), 0.28f)
                else if (band < 0.05f) col = Col.scale(col, 0.88f)
                // the glitter under the sun or the moon
                if (!glintX.isNaN() && frame.sky.gloom < 0.5f) {
                    val spread = (3f + dn * 0.5f) * z
                    // short streaks lying across the water
                    val gx0 = floor((x - glintX) / (3f * z)).toInt()
                    if (abs(x - glintX) < spread && Noise.rnd(gx0, (y / z + (t * 2).toInt()), 683) > 0.82f + abs(x - glintX) / spread * 0.14f) col = Col.mix(col, glintCol, 0.75f)
                }
                // a bright line where the water meets a level shore
                if (dn < 0.9f && abs(shore[min(w - 1, x + z)] - shore[max(0, x - z)]) < 1.5f * z) col = Col.mix(col, skyRefl(0), 0.35f)
                if (icy && (dn < 2.5f + 2f * Noise.v1(x * 0.1f / z, 684) || dn > 24f + 6f * Noise.v1(x * 0.07f / z, 685))) {
                    col = if (Noise.v2(x * 0.15f / z, y * 0.4f / z, 686) > 0.55f) env.lit(Pal.SNOW_L) else env.lit(Col.mix(Pal.ICE_L, refl, 0.25f))
                }
                c.set(x, y, col)
            }
        }
        c.penEmissive = false
        // the church and the bridge upside down in the water, wavering
        bridge(true)
        church(true)
    }

    /** A fish jumping out of the lake again and again ([fx] "fish"), rings where it falls back. */
    private fun jumps(level: Float) {
        if (level <= 0.01f) return
        val z = z
        val period = 2.6
        val n = floor(t / period).toInt()
        val ph = ((t % period) / period).toFloat()
        val cu = 118f + Noise.rnd(n, 691) * 34f
        val x = ox + cu * z
        val xi = x.toInt().coerceIn(0, w - 1)
        val baseY = min(browRow[xi] - 3 * z, (shore[xi] + (10f + Noise.rnd(n, 692) * 18f) * z).toInt())
        val silver = env.lit(Col.hex(0xE0E8EE)); val back = env.lit(Col.hex(0x4A5E6A))
        c.penEmissive = true
        // the splash where it leaves the water, the fish arching over, the splash where it falls back
        if (ph < 0.12f) PokeArt.puff(c, x - 2.5f * z, baseY.toFloat(), ph / 0.12f, 3f * z, 5, Col.hex(0xFFFFFF), n, 0.8f)
        if (ph < 0.35f) {
            val a = ph / 0.35f
            val fx = x + (a - 0.5f) * 6f * z; val fy = baseY - sin(a * 3.14f) * 6f * z
            c.fillRect(fx.toInt() - z, fy.toInt(), 3 * z, z + z / 2 + 1, silver)
            c.fillRect(fx.toInt() + (if (a < 0.5f) 2 * z else -2 * z), fy.toInt(), z, z, back)
            if (a > 0.8f) PokeArt.puff(c, x + 3f * z, baseY.toFloat(), (a - 0.8f) / 0.2f, 3f * z, 5, Col.hex(0xFFFFFF), n + 1, 0.8f)
        }
        if (ph > 0.3f) {
            val r = (ph - 0.3f) / 0.7f
            val rr = (1.5f + r * 8f) * z
            val steps = max(16, (rr * 2f).toInt())
            for (q in 0 until steps) {
                val ang = q / steps.toFloat() * 6.28f
                for (th in 0 until max(1, z / 2 + 1)) c.blend((x + 3f * z + cos(ang) * (rr + th)).toInt(), (baseY + sin(ang) * (rr + th) * 0.3f).toInt(), Col.hex(0xF0F6F8), (1f - r) * 0.85f)
            }
        }
        c.penEmissive = false
    }

    // ------------------------------------------------------------------ the church and the bridge on the right shore

    /** The shore row under scene column [u]. */
    private fun shoreAt(u: Float): Float {
        val x = (ox + u * z).toInt().coerceIn(0, w - 1)
        return shore[x]
    }

    /**
     * Sets a pixel of something standing on the shore, or ([mirror]) its picture in the lake: flipped at [base], wavering,
     * dimmed and blended into the water, only where there is water.
     */
    private fun stand(x: Int, y: Int, col: Int, mirror: Boolean, base: Float) {
        if (!mirror) { c.set(x, y, col); return }
        val my = (2f * base - y).toInt()
        val wob = (sin(my * 0.9f / z + t.toFloat() * 1.6f) * 0.6f * z).toInt()
        val xx = x + wob
        if (xx < 0 || xx >= w || my < shore[xx] || my > browRow[xx]) return
        c.blend(xx, my, Col.mix(Col.scale(col, 0.8f), Col.hex(0x1E4C64), 0.25f), 0.75f)
    }

    /**
     * The little church of St John on the right shore: a white nave with a steep red roof, its apse, round-headed windows
     * lit at night, the bell tower at its front with the bell in the belfry (swinging and ringing, [fx] "bell") under a
     * pointed roof and a cross. [mirror]: its picture in the lake.
     */
    private fun church(mirror: Boolean) {
        val z = z
        val cu = 181f
        val base = shoreAt(cu) - 1.5f * z
        val x0 = (ox + cu * z).toInt()
        val wall = env.lit(Col.mix(Pal.WALL_L, env.skyHorizon, 0.1f)); val wallD = env.lit(Col.mix(Pal.WALL_D, env.skyHorizon, 0.1f))
        val roof = env.lit(Col.mix(Pal.ROOF_M, env.skyHorizon, 0.08f)); val roofD = env.lit(Col.mix(Pal.ROOF_D, env.skyHorizon, 0.08f))
        val night = env.windows > 0.35f
        val glass = if (night) Pal.WINDOW_LIT else env.lit(Pal.GLASS)
        c.penEmissive = true
        val b = base.toInt()
        // the nave, 14 px long, 7 tall, its long side toward us
        for (yy in b - 7 * z until b) for (xx in x0 until x0 + 14 * z) stand(xx, yy, if (xx >= x0 + 13 * z) wallD else wall, mirror, base)
        for (r in 0 until 5 * z) {
            val col = if (r < z) roofD else roof
            for (xx in x0 - z + r / 2 until x0 + 15 * z - r / 2) stand(xx, b - 7 * z - 1 - r, col, mirror, base)
        }
        // the apse at the far end, round and lower
        for (yy in b - 5 * z until b) for (xx in x0 + 14 * z until x0 + 17 * z) stand(xx, yy, wallD, mirror, base)
        for (r in 0 until 2 * z) for (xx in x0 + 14 * z until x0 + 17 * z - r) stand(xx, b - 5 * z - 1 - r, roofD, mirror, base)
        // round-headed windows
        for (k in 0 until 3) {
            val wx = x0 + (3 + k * 4) * z
            for (yy in b - 5 * z until b - 3 * z) for (xx in wx until wx + z) stand(xx, yy, glass, mirror, base)
            if (z > 1) stand(wx + z / 2, b - 5 * z - 1, glass, mirror, base)
        }
        // the bell tower at the front, taller than the nave, a pointed roof and a cross
        val tx = x0 - 5 * z; val tw = 5 * z
        for (yy in b - 21 * z until b) for (xx in tx until tx + tw) stand(xx, yy, if (xx >= tx + tw - z) wallD else wall, mirror, base)
        for (yy in b - 19 * z until b - 16 * z) for (xx in tx + z until tx + tw - z) stand(xx, yy, env.lit(Col.hex(0x2A2226)), mirror, base)
        val ring = fxOn("bell")
        val swing = if (ring > 0.02f) sin(t * 5.0).toFloat() * ring else 0f
        val bx = tx + tw / 2 + (swing * z).toInt()
        for (yy in b - 19 * z until b - 17 * z) for (xx in bx - max(1, z / 2) until bx + max(1, z / 2) + (if (z == 1) 0 else 0)) stand(xx, yy, Pal.GOLD, mirror, base)
        for (r in 0 until 7 * z) {
            val half = (tw / 2f + z * 0.5f) * (1f - r / (7f * z))
            for (xx in (tx + tw / 2f - half).toInt() until (tx + tw / 2f + half).toInt()) stand(xx, b - 21 * z - 1 - r, if (r < z) roofD else roof, mirror, base)
        }
        for (yy in b - 31 * z until b - 28 * z) stand(tx + tw / 2, yy, env.lit(Col.hex(0x3A3432)), mirror, base)
        for (xx in tx + tw / 2 - z until tx + tw / 2 + z + 1) stand(xx, b - 30 * z, env.lit(Col.hex(0x3A3432)), mirror, base)
        // its door and the tower's clock-face window
        for (yy in b - 4 * z until b) for (xx in tx + 2 * z until tx + 3 * z) stand(xx, yy, env.lit(Pal.DOOR), mirror, base)
        c.penEmissive = false
        if (!mirror) {
            if (night) {
                s.light((tx + tw / 2).toFloat(), (b - 12 * z).toFloat(), 22f, 0.5f)
                s.light((x0 + 7 * z).toFloat(), (b - 4 * z).toFloat(), 16f, 0.35f)
            }
            if (ring > 0.02f) s.fx { PokeArt.rings(c, bx.toFloat(), (b - 18 * z).toFloat(), ((t * 0.8) % 1.0).toFloat(), 12f * z, Col.hex(0xFFD86A), z, ring) }
        }
    }

    /**
     * The stone bridge over the water by the church: three round arches on piers, a low parapet, the water showing through
     * the arches (and in the lake the arches close into rings); a spit of stones under its far end.
     */
    private fun bridge(mirror: Boolean) {
        val z = z
        val u0 = 154f; val u1 = 176f
        val base = shoreAt(u1) + 1.5f * z
        val deck = base - 7f * z
        val stone = env.lit(Col.mix(Col.hex(0xC8C0B0), env.skyHorizon, 0.12f)); val stoneM = env.lit(Col.mix(Pal.STONE_M, env.skyHorizon, 0.12f))
        val stoneD = env.lit(Col.mix(Pal.STONE_D, env.skyHorizon, 0.12f))
        val x0 = (ox + u0 * z).toInt(); val x1 = (ox + u1 * z).toInt()
        val span = (u1 - u0 - 2f) / 2f
        c.penEmissive = true
        for (xx in x0 until x1) {
            val u = (xx + 0.5f - ox) / z - u0
            // which arch, and where in it: 0 at a pier .. 1 at the next
            val a = ((u - 1f) / span)
            val inSpan = a > 0f && a < 2f
            val f = a - floor(a)
            val archR = span * 0.36f
            val dx = (f - 0.5f) * span
            // the arch's crown: a half circle springing from the piers
            val crown = base - (1.5f * z) - sqrt(max(0f, archR * archR - dx * dx)) * z
            for (yy in (deck - 1.6f * z).toInt() until base.toInt()) {
                val open = inSpan && abs(dx) < archR && yy >= crown
                if (open) continue
                val parapet = yy < deck
                var col = when {
                    parapet -> if (yy < deck - 1.6f * z + z) stone else stoneM
                    inSpan && abs(dx) < archR + z && yy >= crown - z -> stoneD // the arch's ring of voussoirs
                    else -> if (z > 1 && Noise.rnd(xx / 2, yy / 2, 693) < 0.22f) stoneM else stone
                }
                if (!parapet && z > 1 && ((yy - deck.toInt()) / (2 * z)) % 2 == 0 && (xx / (3 * z)) % 2 == 0 && col == stone) col = Col.mix(stone, stoneM, 0.5f)
                stand(xx, yy, col, mirror, base)
            }
        }
        // the stones of the spit under its far end
        if (!mirror) for (k in 0 until 4) {
            val sx = x0 - (1 + k) * z * 1.5f; val sy = base - z * 0.5f
            c.fillEllipse(sx, sy, (1.2f + Noise.rnd(k, 694)) * z, 0.8f * z + 0.3f, if (k % 2 == 0) stone else stoneM)
        }
        c.penEmissive = false
    }

    // ------------------------------------------------------------------ the pasture

    /** The pasture's grass at ([x], [d]) m, picture pixel ([px], [py]): the season's, flowers, snow; lighter along the brow. */
    private fun grassAt(x: Float, d: Float, px: Int, py: Int): Int {
        val g = env.grass
        val n = Noise.v2(x * 0.55f, d * 0.55f, 711) + (Dither.at(px, py) - 0.5f) * 0.25f
        var col = if (n > 0.62f) g[0] else if (n > 0.3f) g[1] else g[2]
        if (!env.snow) {
            val r = Noise.rnd(px / z, py / z, 712)
            if (d < 22f && r < 0.07f) col = g[2]
            if (d < 22f && r > 0.975f) col = g[3]
            // flowers: gentians and alpine roses in summer, crocuses in spring, a few in autumn
            val f = Noise.rnd(px / z, py / z, 713)
            if (d < 20f && f < when (env.month) { 3, 4 -> 0.011f; 5, 6, 7 -> 0.013f; 8 -> 0.008f; else -> 0f }) {
                col = when (env.month) {
                    3, 4 -> if (f < 0.0055f) Col.hex(0xB08AD8) else Col.hex(0xF4F0FA)
                    else -> if (f < 0.005f) Col.hex(0x3A62D0) else if (f < 0.009f) Col.hex(0xD8587E) else Col.hex(0xF0D458)
                }
            }
            // snow still lying in patches in early spring
            if (env.month in 3..4 && Noise.v2(x * 0.25f, d * 0.3f, 714) > (if (env.month == 3) 0.52f else 0.68f)) col = if (n > 0.5f) Pal.SNOW_L else Pal.SNOW_M
        }
        // the brow: the grass catches the light where the land drops away
        val toBrow = brow(x) - d
        if (toBrow < 0.6f) col = Col.mix(col, if (env.snow) Pal.SNOW_L else g[3], 0.4f)
        return hazy(col, d)
    }

    /** The ground from the brow to our feet (plain ground, id 0), the pasture's part drawn again over it as a thing. */
    private fun pasture0() {
        c.penId = 0
        for (px in max(0, c.left) until min(w, c.right)) {
            for (py in max(browRow[px], c.top) until min(h, c.bottom)) {
                val d = depthAt(py); val x = sideAt(px, d)
                c.set(px, py, grassAt(x, d, px, py))
            }
        }
    }

    /** The pasture proper (the planina): the grass beyond the wall, left of the stream, up to the brow; the cows' shadows on it. */
    private fun pasture() {
        val id = c.penId
        val y1 = gy(fallD).toInt()
        for (px in max(0, c.left) until min(w, c.right)) {
            for (py in max(browRow[px], c.top) until min(y1, c.bottom)) {
                val d = depthAt(py); val x = sideAt(px, d)
                if (x > brook(d) - brookHalf(d) - 0.1f || x < -16f) continue
                var col = grassAt(x, d, px, py)
                // the cows' shadows
                for ((cx, cd) in cowSpots()) {
                    val dx = (x - cx) / 1.2f; val dd = (d - cd) / 0.45f
                    if (dx * dx + dd * dd < 1f) col = Col.scale(col, 0.8f)
                }
                c.set(px, py, col)
            }
        }
        c.penId = id
    }

    private fun cowSpots(): List<Pair<Float, Float>> = listOf(cowX to cowD) + herd.map { it.first to it.second }

    /** The path: bare earth and stones worn into the pasture, grass along its middle, winding over the brow. */
    private fun pathPixels() {
        val z = z
        val snow = env.snow
        val dirt = if (snow) intArrayOf(Pal.SNOW_M, Pal.SNOW_D, Col.hex(0xA8B8CC)) else intArrayOf(Col.hex(0xB08A5E), Col.hex(0x96744C), Col.hex(0x7A5C3C))
        for (px in max(0, c.left) until min(w, c.right)) {
            for (py in max(browRow[px], c.top) until min(h, c.bottom)) {
                val d = depthAt(py)
                val cx = path(d); val x = sideAt(px, d)
                val half = pathHalf * (1f + (Noise.v1(d * 0.9f, 721) - 0.5f) * 0.25f)
                val off = (x - cx) / half
                if (abs(off) > 1f) continue
                val r = Noise.rnd(px, py, 722)
                var col = if (r < 0.15f) dirt[0] else if (r > 0.85f) dirt[2] else dirt[1]
                // stones in the path, closer up
                if (z > 1 || d < 12f) { val st = Noise.v2(x * 5f, d * 5f, 723); if (st > 0.78f) col = if (st > 0.86f) Col.hex(0xC8C0B0) else Col.hex(0x9A948A) }
                if (!snow && abs(off) < 0.18f && r < 0.5f) col = env.grass[1]
                if (!snow && abs(off) > 0.82f && r < 0.5f) col = env.grass[2]
                c.set(px, py, hazy(col, d))
            }
        }
    }

    /**
     * The stream: clear green-blue mountain water over pebbles, darker in its middle, white where it rushes round
     * stones, ripples running toward us; the pool below the waterfall foaming; ice along its edges in winter.
     */
    private fun streamPixels() {
        val z = z
        val winter = env.season == Season.WINTER
        for (py in max(hz + z, c.top) until min(h, c.bottom)) {
            val d = depthAt(py)
            if (d > brow(brook(d)) - 0.3f) continue
            val cx = brook(d)
            val pool = d in (poolD - 0.2f)..fallD
            val half = brookHalf(d) * (if (pool) 1.55f else 1f)
            val x0 = gx(cx - half * 1.3f, d).toInt(); val x1 = gx(cx + half * 1.3f, d).toInt()
            for (px in max(x0, c.left)..min(x1, c.right - 1)) {
                val x = sideAt(px, d)
                val rag = (Noise.v2(x * 3f, d * 2f, 731) - 0.5f) * 0.14f
                val off = (x - cx) / (half + rag)
                if (abs(off) > 1f) continue
                val flow = Noise.v2(x * 2.4f, d * 1.1f + t.toFloat() * 1.6f, 732)
                var col = when {
                    abs(off) > 0.8f -> Col.hex(0x6FA8A0)
                    abs(off) < 0.35f -> Col.hex(0x2E7A86)
                    else -> Col.hex(0x3E8E94)
                }
                if (flow > 0.72f) col = Col.hex(0x8ACCC6)
                if (flow > 0.84f || (pool && Noise.v2(x * 4f, d * 4f + t.toFloat() * 2f, 733) > 0.55f)) col = Col.hex(0xE8F4F2)
                if (d < 16f && Noise.rnd(px / z, py / z, 734) < 0.04f) col = Col.hex(0xA8C8B8)
                if (winter && abs(off) > 0.55f) col = if (Noise.rnd(px, py, 735) < 0.3f) Pal.ICE_M else Pal.ICE_L
                c.set(px, py, hazy(col, d))
            }
        }
    }

    // ------------------------------------------------------------------ the waterfall

    /** The rock step the stream pours over: boulders either side of the notch, mossy, snow on them in winter. */
    private fun ledge() {
        val cx = brook(fallD)
        boulder(cx - 1.05f, fallD + 0.15f, 1.2f, 1.35f, 741, mossy = true)
        boulder(cx + 1.1f, fallD + 0.25f, 1.3f, 1.5f, 742, mossy = true)
        boulder(cx - 0.05f, fallD + 0.4f, 0.9f, 1.05f, 743, mossy = false)
    }

    /**
     * The waterfall: a curtain of water pouring over the notch in the rock step, streaks running down it, white where it
     * breaks, foam and spray at its foot; fuller in spring, a trickle with icicles in winter.
     */
    private fun fall() {
        val z = z
        val cx = brook(fallD)
        val d = fallD - 0.1f
        val p = pm(d)
        val topH = 1.02f
        val full = when (env.month) { 4, 5, 6 -> 1.25f; 12, 1, 2 -> 0.45f; else -> 1f } * (1f + frame.sky.rain * 0.3f)
        val half = 0.42f * full
        val y0 = hy(d, topH); val y1 = gy(d)
        val frozen = bareMonths
        for (py in max(y0.toInt(), c.top)..min(y1.toInt(), c.bottom - 1)) {
            val v = (py - y0) / max(1f, y1 - y0) // 0 at the lip .. 1 at the foot
            // the curtain bulges out a little as it falls, wider at its foot
            val hw = half * (0.85f + 0.3f * v)
            for (px in max(gx(cx - hw, d).toInt(), c.left)..min(gx(cx + hw, d).toInt(), c.right - 1)) {
                val u = (sideAt(px, d) - cx) / hw
                val streak = Noise.v2(u * 5f + 3f, (py / z.toFloat()) * 0.35f - t.toFloat() * 5f, 744)
                var col = when {
                    frozen -> if (abs(u) > 0.4f || streak > 0.5f) Col.hex(0xDCEEF6) else Col.hex(0x9EC8DA)
                    streak > 0.7f -> Col.hex(0xFAFEFF)
                    streak > 0.45f -> Col.hex(0xC8E8EC)
                    else -> Col.hex(0x6EB4BE)
                }
                if (!frozen && v < 0.12f) col = Col.mix(col, Col.hex(0x3E8E94), 0.4f) // the smooth lip
                if (abs(u) > 0.85f && Dither.at(px, py) < 0.5f) continue
                c.set(px, py, col)
            }
        }
        // foam boiling at its foot
        if (!frozen) for (k in 0 until 7) {
            val a = t.toFloat() * 1.3f + k * 0.9f
            val fx = gx(cx + (Noise.rnd(k, 745) - 0.5f) * 1.3f, d - 0.15f) + sin(a) * 0.08f * p
            val fy = y1 - (0.08f + 0.1f * abs(sin(a * 1.7f))) * p
            c.fillEllipse(fx, fy, (0.18f + 0.08f * Noise.rnd(k, 746)) * p, 0.07f * p + z * 0.5f, if (k % 2 == 0) Col.hex(0xFFFFFF) else Col.hex(0xDCEEF0))
        }
        if (!frozen) s.fx {
            // spray drifting off it
            for (j in 0 until 10) {
                val ph = ((t * 0.7 + j / 10.0) % 1.0).toFloat()
                val sx = gx(cx + (Noise.rnd(j, 747) - 0.5f) * 1.2f, d) + ph * 0.4f * p * (if (j % 2 == 0) 1f else -1f)
                val sy = y1 - (0.1f + ph * 0.6f) * p
                c.blend(sx.toInt(), sy.toInt(), Col.hex(0xF4FAFC), (1f - ph) * 0.5f)
            }
        }
    }

    /**
     * A boulder [wid] m wide and [tall] m high at side [x], depth [d]: rounded limestone, lit on top and on the sun's side,
     * cracks and lichen, moss on its shady side ([mossy]), snow on its top in winter.
     */
    private fun boulder(x: Float, d: Float, wid: Float, tall: Float, seed: Int, mossy: Boolean) {
        val z = z
        val p = pm(d); val bx = gx(x, d); val by = gy(d)
        val rx = wid * 0.5f * p; val ry = tall * p
        val sunL = (sunAt()?.first ?: vx) < bx
        for (py in max((by - ry).toInt(), c.top)..min(by.toInt(), c.bottom - 1)) {
            val v = (by - py - 0.5f) / ry // 0 at the foot .. 1 at the top
            val bump = 1f + (Noise.v1(v * 3f + seed, seed) - 0.5f) * 0.25f
            val half = rx * sqrt(max(0f, 1f - v * v * v)) * bump
            for (px in max((bx - half).toInt(), c.left)..min((bx + half).toInt(), c.right - 1)) {
                val u = (px + 0.5f - bx) / max(1f, half)
                val lit = (if (sunL) -u else u) * 0.5f + v * 0.7f + (Noise.v2(px * 0.3f / z, py * 0.3f / z, seed + 1) - 0.5f) * 0.4f
                var col = when {
                    lit > 0.72f -> Col.hex(0xD6D0C2)
                    lit > 0.35f -> Pal.STONE_L
                    lit > 0.05f -> Pal.STONE_M
                    else -> Pal.STONE_D
                }
                val crack = abs(Noise.v2(px * 0.12f / z, py * 0.5f / z, seed + 2) - 0.5f) < 0.02f
                if (crack) col = Pal.STONE_X
                if (mossy && lit < 0.35f && v < 0.8f && Noise.v2(px * 0.25f / z, py * 0.25f / z, seed + 3) > 0.45f) col = if (lit < 0.1f) Col.hex(0x4A6A2E) else Col.hex(0x6A8A3A)
                if (Noise.v2(px * 0.4f / z, py * 0.4f / z, seed + 4) > 0.83f) col = LICHEN
                if (env.snow && v > 0.62f + (Noise.rnd(px, 0, seed + 5) - 0.5f) * 0.1f) col = if (lit > 0.5f) Pal.SNOW_L else Pal.SNOW_M
                c.set(px, py, col)
            }
        }
    }

    // ------------------------------------------------------------------ the hut

    /**
     * The shepherd's hut (pastirska koča): its gable end toward us, a stone ground floor with the door, walls of dark larch
     * logs above, a steep roof of wooden shingles (skodle) coming far down, a small window with a red geranium in summer,
     * lit at night; a bench by the door; smoke from the chimney in the morning and evening; snow on the roof in winter.
     * The side wall runs back on the right.
     */
    private fun hut() {
        val z = z
        val d = hutD; val p = pm(d)
        val xl = hutX - hutW / 2f; val xr = hutX + hutW / 2f
        val base = 0.95f; val eave = 2.3f; val ridge = 5.1f
        val stone = intArrayOf(Col.hex(0xC8C2B4), Col.hex(0xAEA89A), Col.hex(0x8E887C))
        val log = intArrayOf(Col.hex(0x8A5E3C), Col.hex(0x6E4A2E), Col.hex(0x4E3420))
        val shingle = intArrayOf(Col.hex(0x8E8474), Col.hex(0x6E665A), Col.hex(0x524C44))
        // the side wall running back, its logs and the roof's slope over it
        val back = d + hutL
        val sx0 = gx(xr, d); val sx1 = gx(xr, back)
        for (px in max(sx0.toInt(), c.left)..min(sx1.toInt(), c.right - 1)) {
            val dd = F * xr / max(0.1f, px + 0.5f - vx)
            if (dd < d || dd > back) continue
            val yb = gy(dd); val yt = hy(dd, eave)
            for (py in max(yt.toInt(), c.top)..min(yb.toInt(), c.bottom - 1)) {
                val hm = (yb - py) / pm(dd)
                val col = if (hm < base) stone[if (Noise.rnd(px / z, py / z, 751) < 0.3f) 2 else 1] else log[if (((hm - base) / 0.22f).toInt() % 2 == 0) 1 else 2]
                c.set(px, py, Col.scale(col, 0.82f))
            }
        }
        // the front: stone below, logs above, the gable
        val fx0 = gx(xl, d).toInt(); val fx1 = gx(xr, d).toInt(); val fy = gy(d)
        for (py in max(hy(d, ridge).toInt(), c.top)..min(fy.toInt(), c.bottom - 1)) {
            val hm = (fy - py - 0.5f) / p
            val halfAt = if (hm <= eave) hutW / 2f else hutW / 2f * (ridge - hm) / (ridge - eave)
            for (px in max(gx(hutX - halfAt, d).toInt(), c.left)..min(gx(hutX + halfAt, d).toInt(), c.right - 1)) {
                val x = sideAt(px, d)
                val col = if (hm < base) {
                    // stones in courses with dark joints
                    val course = floor(hm / 0.24f).toInt()
                    val sx = (x + course * 0.17f) / 0.38f
                    val j = (hm / 0.24f - course < 0.14f) || (sx - floor(sx) < 0.1f)
                    if (j) Col.hex(0x5E584E) else stone[(Noise.rnd(floor(sx).toInt(), course, 752) * 2.9f).toInt()]
                } else {
                    // logs: round ends lit on top
                    val lv = (hm - base) / 0.22f
                    val f = lv - floor(lv)
                    if (f > 0.8f) log[0] else if (f < 0.18f) log[2] else log[1]
                }
                c.set(px, py, col)
            }
        }
        // the door in the stone floor, the window up in the logs, the bench
        val doorL = gx(hutX - 0.9f, d).toInt(); val doorR = gx(hutX - 0.1f, d).toInt()
        c.fillRect(doorL, hy(d, 1.75f).toInt(), doorR - doorL, (fy - hy(d, 1.75f)).toInt(), Col.hex(0x4A3020))
        if (z > 1) for (k in 1..2) c.vline(doorL + (doorR - doorL) * k / 3, hy(d, 1.75f).toInt(), fy.toInt() - 1, Col.hex(0x3A2418))
        c.set(doorR - z, hy(d, 0.95f).toInt(), Pal.GOLD)
        val night = env.windows > 0.35f
        val wx0 = gx(hutX + 0.35f, d).toInt(); val wx1 = gx(hutX + 1.2f, d).toInt(); val wy0 = hy(d, 1.95f).toInt(); val wy1 = hy(d, 1.3f).toInt()
        c.fillRect(wx0 - z, wy0 - z, wx1 - wx0 + 2 * z, wy1 - wy0 + 2 * z, Col.hex(0xE8DCC8))
        glow { c.fillRect(wx0, wy0, wx1 - wx0, wy1 - wy0, if (night) Pal.WINDOW_LIT else Pal.GLASS) }
        if (z > 1) { c.vline((wx0 + wx1) / 2, wy0, wy1 - 1, Col.hex(0xE8DCC8)); c.hline(wx0, wx1 - 1, (wy0 + wy1) / 2, Col.hex(0xE8DCC8)) }
        if (env.month in 5..9) for (k in 0 until 3) c.fillEllipse(wx0 + (k + 0.5f) * (wx1 - wx0) / 3f, wy1 + z.toFloat(), 1.2f * z, z.toFloat(), if (k == 1) Pal.GERANIUM else Pal.GERANIUM_D)
        if (night) s.light((wx0 + wx1) / 2f, (wy0 + wy1) / 2f, 26f, 0.6f)
        val bl = gx(hutX + 0.4f, d - 0.3f).toInt(); val br = gx(hutX + 1.9f, d - 0.3f).toInt(); val bt = hy(d - 0.3f, 0.45f).toInt()
        c.fillRect(bl, bt, br - bl, max(1, z), Pal.WOOD_L)
        c.fillRect(bl + z, bt, max(1, z), (gy(d - 0.3f) - bt).toInt(), Pal.WOOD_D); c.fillRect(br - 2 * z, bt, max(1, z), (gy(d - 0.3f) - bt).toInt(), Pal.WOOD_D)
        // the roof: the front edge of the shingles, the slope over the side wall back to the ridge
        val over = 0.45f
        val ry = hy(d - over, ridge + 0.25f)
        val ey = hy(d - over, eave - 0.35f)
        for (py in max(ry.toInt(), c.top)..min(ey.toInt(), c.bottom - 1)) {
            val v = (py - ry) / max(1f, ey - ry) // 0 at the ridge .. 1 at the eaves
            val halfFront = (hutW / 2f + 0.55f) * v
            val xa = gx(hutX - halfFront, d - over); val xb = gx(hutX + halfFront, d - over)
            // the slope over the side wall: from the front edge back to where the ridge line runs away
            val xBack = gx(hutX + halfFront, back + over)
            for (px in max(xa.toInt(), c.left)..min(max(xb, xBack).toInt(), c.right - 1)) {
                val onFront = px <= xb
                val row = ((py - ry) / max(2f, 0.28f * p)).toInt()
                var col = if (onFront) {
                    // the shingles' edge along the gable: a thick board, then courses
                    if (abs(px - xa) < 1.2f * z || abs(px - xb) < 1.2f * z) Pal.WOOD_D else shingle[if (row % 2 == 0) 0 else 1]
                } else shingle[if (row % 2 == 0) 1 else 2]
                if (env.snow && (v < 0.85f || !onFront)) col = if (onFront) Pal.SNOW_L else Pal.SNOW_M
                c.set(px, py, col)
            }
        }
        // the chimney, and its smoke in the morning and the evening
        val chx = gx(hutX + 0.9f, back - 1.2f); val chy = hy(back - 1.2f, ridge + 0.1f)
        c.fillRect(chx.toInt(), (chy - 0.7f * pm(back - 1.2f)).toInt(), max(z, (0.4f * pm(back - 1.2f)).toInt()), (0.7f * pm(back - 1.2f)).toInt(), stone[1])
        val hr = s.hour
        if (hr in 5.5f..9.5f || hr in 17f..21.5f) s.smoke(chx + z, chy - 0.8f * pm(back - 1.2f), 0.55f, 11)
    }

    // ------------------------------------------------------------------ the cows

    /** Where the near cow's bell hangs, and how it swings: x, y (picture px), swing. */
    private val cowBell = FloatArray(3)

    /**
     * A cika cow (the red-brown Bohinj breed with a white stripe along its back): legs, a deep body, its head down grazing
     * (or up, lowing, when poked), small horns, ears, a tail with a tuft swishing, the udder; the bell on a leather strap
     * under its neck. [near]: the cow in front, whose bell is its own thing and which reacts to a tap.
     */
    private fun cow(x: Float, d: Float, flip: Boolean, seed: Int, near: Boolean) {
        val z = z
        val p = pm(d); val bx = gx(x, d); val by = gy(d)
        val dir = if (flip) -1f else 1f
        val red = Col.hex(0x9A4A2A); val redL = Col.hex(0xBE6A3E); val redD = Col.hex(0x6A3020); val white = Col.hex(0xF2EADC); val horn = Col.hex(0xE8DCC0)
        // what it does: grazing, the head swinging a little; poked (near cow): head up lowing, then shaking its head
        val pk = if (near) poked("cow") else null
        val a = pk?.age(t) ?: 0f
        val bells = fxOn("cowbells")
        var headUp = if (bells > 0.02f) 0.5f + 0.3f * sin(t.toFloat() * 2f + seed) else 0.12f + 0.08f * sin(t.toFloat() * 0.7f + seed)
        var shake = 0f
        if (pk != null) {
            if (pk.step == 0) headUp = PokeArt.ease(a, 0f, 0.35f) * (1f - PokeArt.ease(a, 1.8f, 2.2f)) * 0.9f + headUp * (1f - PokeArt.ease(a, 0f, 0.35f))
            else { headUp = 0.55f; shake = sin(a * 14f) * (1f - PokeArt.ease(a, 1.6f, 2.6f)) }
        }
        // legs: the far pair first, darker
        for ((k, lx) in floatArrayOf(-0.7f, 0.62f, -0.55f, 0.75f).withIndex()) {
            val far = k < 2
            val legX = bx + (lx + (if (far) 0.08f else 0f)) * dir * p
            val lw = max(z.toFloat(), 0.13f * p)
            val top = by - 0.72f * p
            c.fillRect((legX - lw / 2).toInt(), top.toInt(), max(1, lw.toInt()), (by - top).toInt() - (if (far) z else 0), if (far) redD else red)
            if (!far) c.fillRect((legX - lw / 2).toInt(), (by - 0.28f * p).toInt(), max(1, lw.toInt()), max(1, (0.18f * p).toInt()), Col.hex(0xE8DCCB))
            c.fillRect((legX - lw / 2).toInt(), (by - 0.1f * p).toInt() - (if (far) z else 0), max(1, lw.toInt()), max(1, (0.1f * p).toInt()), Col.hex(0x2A2220))
        }
        // the body: deep, rounded, the white stripe along the back, the white belly
        val cx = bx; val cy = by - 1.02f * p
        val rx = 1.02f * p; val ry = 0.36f * p
        for (py in max((cy - ry).toInt(), c.top)..min((cy + ry).toInt(), c.bottom - 1)) {
            val vy = (py + 0.5f - cy) / ry
            val half = rx * sqrt(max(0f, 1f - vy.toDouble().pow(4.0).toFloat()))
            for (px in max((cx - half).toInt(), c.left)..min((cx + half).toInt(), c.right - 1)) {
                val vx2 = (px + 0.5f - cx) / rx
                var col = when {
                    vy < -0.72f && abs(vx2) < 0.85f -> white // the line down its back
                    vy > 0.62f -> white
                    vy < -0.35f -> redL
                    vy > 0.3f -> redD
                    else -> red
                }
                if (z > 1 && col == red && Noise.rnd(px / z, py / z, 761 + seed) < 0.06f) col = redD
                c.set(px, py, col)
            }
        }
        // the udder
        c.fillEllipse(cx - 0.45f * dir * p, cy + ry * 0.95f, 0.17f * p, 0.1f * p + 0.5f, Col.hex(0xE8A8A0))
        // the tail, swishing, its tuft
        val sw = sin(t.toFloat() * 1.7f + seed * 2f) * 0.12f * p * (if (pk?.step == 1) 2.5f else 1f)
        val tx = cx - dir * rx * 0.98f; val ty = cy - ry * 0.6f
        c.line(tx.toInt(), ty.toInt(), (tx - dir * 0.08f * p + sw).toInt(), (by - 0.35f * p).toInt(), redD)
        c.fillEllipse(tx - dir * 0.08f * p + sw, by - 0.3f * p, max(1f, 0.06f * p), max(1f, 0.1f * p), Col.hex(0xF0E6D6))
        // the neck and the head: down to the grass, or up
        val nx = cx + dir * rx * 0.82f
        val hx = nx + dir * (0.38f + 0.1f * headUp) * p + shake * 0.12f * p
        val hy2 = by - (0.25f + 0.95f * headUp) * p
        val neckTop = cy - ry * 0.55f
        for (j in 0..8) {
            val f = j / 8f
            c.fillEllipse(nx + (hx - nx) * f, neckTop + (hy2 - neckTop) * f, (0.26f - 0.08f * f) * p, (0.24f - 0.06f * f) * p, if (j < 3) red else redL)
        }
        // the head: red, a pale muzzle, a little white blaze
        val hw = 0.19f * p; val hh = 0.28f * p
        c.fillEllipse(hx, hy2, hw, hh, red)
        c.fillEllipse(hx - dir * 0.05f * p, hy2 - hh * 0.3f, hw * 0.6f, hh * 0.45f, redL)
        c.fillEllipse(hx + dir * 0.03f * p, hy2 + hh * 0.72f, hw * 0.8f, hh * 0.36f, Col.hex(0xD8B0A0))
        if (p > 8f) c.fillEllipse(hx + dir * 0.02f * p, hy2 - hh * 0.2f, 0.035f * p + 0.3f, 0.08f * p, white)
        // ears and horns
        c.fillEllipse(hx - dir * 0.2f * p, hy2 - hh * 0.45f, 0.1f * p, 0.05f * p + 0.5f, redD)
        c.line((hx - 0.06f * p).toInt(), (hy2 - hh * 0.8f).toInt(), (hx - 0.14f * p).toInt(), (hy2 - hh * 1.2f).toInt(), horn)
        c.line((hx + 0.06f * p).toInt(), (hy2 - hh * 0.8f).toInt(), (hx + 0.14f * p).toInt(), (hy2 - hh * 1.2f).toInt(), horn)
        if (p > 8f) {
            c.set((hx + dir * 0.06f * p).toInt(), (hy2 - hh * 0.2f).toInt(), Col.hex(0x1E1A18))
            if (z > 1) c.set((hx + dir * 0.06f * p).toInt() - 1, (hy2 - hh * 0.2f).toInt() - 1, Col.hex(0xFFFFFF))
        }
        // the strap round the neck; the bell hangs from it (the near cow's is a thing of its own)
        val sx = nx + (hx - nx) * 0.45f; val sy = neckTop + (hy2 - neckTop) * 0.45f
        c.line((sx - 0.12f * p).toInt(), (sy - 0.18f * p).toInt(), (sx + 0.05f * p).toInt(), (sy + 0.2f * p).toInt(), Col.hex(0x3A2418))
        val swing = sin(t.toFloat() * (if (bells > 0.02f) 7f else 1.5f) + seed) * (if (bells > 0.02f) 0.5f else 0.12f) + shake * 0.6f
        if (near) { cowBell[0] = sx + 0.05f * p; cowBell[1] = sy + 0.22f * p; cowBell[2] = swing }
        else bell(sx + 0.05f * p, sy + 0.22f * p, p, swing)
        // lowing: its breath, and the sound going out
        if (pk != null && pk.step == 0 && a in 0.35f..1.9f) {
            val mx = hx + dir * hw; val my = hy2 + hh * 0.7f
            s.fx { PokeArt.rings(c, mx, my, ((a - 0.35f) / 1.55f).coerceIn(0f, 1f), 0.8f * p, Col.hex(0xFFF4DA), max(1, z), 0.8f, n = 3) }
        }
    }

    /** A cowbell of hammered iron with a brass sheen, [p] px per metre, its mouth at ([x], [y]) swinging by [swing]. */
    private fun bell(x: Float, y: Float, p: Float, swing: Float) {
        val bw = max(1.5f, 0.17f * p); val bh = max(2f, 0.36f * p)
        val ox2 = swing * 0.1f * p
        val dark = Col.hex(0x5A4A2A); val mid = Col.hex(0xA88A3A); val lit = Col.hex(0xE8C868)
        for (j in 0 until max(2, bh.toInt())) {
            val f = j / max(1f, bh - 1f)
            val half = bw * (0.55f + 0.45f * f)
            val yy = (y - bh + j).toInt()
            val xc = x + ox2 * (1f - f)
            for (xx in (xc - half).toInt()..(xc + half).toInt()) {
                val u = (xx + 0.5f - xc) / max(1f, half)
                c.set(xx, yy, if (u < -0.3f) lit else if (u > 0.45f || j == bh.toInt() - 1) dark else mid)
            }
        }
    }

    /** The bells ringing ([fx] "cowbells"): rings going out from every cow's bell, louder the higher the level. */
    private fun cowbellRings() {
        val lvl = fxOn("cowbells")
        if (lvl <= 0.02f) return
        val z = z
        s.fx {
            for ((k, spot) in cowSpots().withIndex()) {
                val (x, d) = spot
                val p = pm(d)
                val u = ((t * (0.9 + k * 0.13) + k * 0.37) % 1.0).toFloat()
                val bx = gx(x + (if (k == 0 || !herd[k - 1].third) 1f else -1f) * 1.25f, d); val by = gy(d) - 0.7f * p
                PokeArt.rings(c, bx, by, u, (0.9f + 0.3f * lvl) * p, Col.hex(0xFFE08A), max(1, z), 0.85f * lvl, n = 2)
            }
        }
    }

    // ------------------------------------------------------------------ the marmot

    /**
     * The marmot on its rock across the stream: sitting up by day, curled by its burrow at night; poked, it stands up
     * tall and whistles, then dives into the burrow under the rock and after a while peeks out and comes back up.
     */
    private fun marmot() {
        val z = z
        boulder(marmotX, marmotD, 1.3f, 0.75f, 771, mossy = false)
        val p = pm(marmotD)
        val bx = gx(marmotX - 0.1f, marmotD); val by = hy(marmotD, 0.7f)
        // its burrow at the rock's foot
        val hx = gx(marmotX - 0.75f, marmotD - 0.1f); val hy2 = gy(marmotD - 0.1f)
        c.fillEllipse(hx, hy2 - 0.06f * p, 0.16f * p, 0.09f * p + 0.5f, Col.hex(0x2A221C))
        val pk = poked("marmot"); val a = pk?.age(t) ?: 0f
        val fur = Col.hex(0x9A7650); val furL = Col.hex(0xC49A68); val furD = Col.hex(0x5E4632)
        val asleep = env.dark > 0.55f && pk == null
        when {
            pk != null && pk.step == 1 -> {
                // down the burrow, then peeking out, then back up on the rock
                val out = PokeArt.ease(a, 3.6f, 4.4f)
                val back = PokeArt.ease(a, 5.2f, 6.2f)
                if (back > 0f) sitting(bx, by + (1f - back) * 0.3f * p, p, 0f, fur, furL, furD)
                else if (out > 0f) { c.fillEllipse(hx, hy2 - 0.1f * p * out, 0.1f * p, 0.08f * p * out + 0.5f, fur); c.set(hx.toInt(), (hy2 - 0.13f * p * out).toInt(), Col.hex(0x1E1A18)) }
                else if (a < 0.5f) { val f = a / 0.5f; sitting(hx + (bx - hx) * (1f - f), by + (hy2 - by) * f, p, 0f, fur, furL, furD) }
            }
            pk != null -> {
                val up = PokeArt.ease(a, 0f, 0.3f) * (1f - PokeArt.ease(a, 2f, 2.4f))
                sitting(bx, by, p, up, fur, furL, furD)
                if (a in 0.3f..1.9f) s.fx { PokeArt.rings(c, bx + 0.12f * p, by - 0.55f * p, ((a - 0.3f) / 1.6f), 0.9f * p, Col.hex(0xFFFFFF), max(1, z), 0.9f, n = 3) }
            }
            asleep -> c.fillEllipse(bx, by - 0.12f * p, 0.2f * p, 0.13f * p, furD)
            else -> sitting(bx, by, p, 0f, fur, furL, furD)
        }
    }

    /**
     * A marmot sitting up at ([x], [y]) (its feet), [up] 0..1 standing tall on its hind legs: a stout brown body, a paler
     * belly, a round head with a dark snout and little ears, its forepaws held in front.
     */
    private fun sitting(x: Float, y: Float, p: Float, up: Float, fur: Int, furL: Int, furD: Int) {
        val hgt = (0.55f + 0.2f * up) * p
        // the body: wide at the haunches, narrowing to the shoulders
        c.fillEllipse(x, y - hgt * 0.28f, 0.2f * p, hgt * 0.3f, fur)
        c.fillEllipse(x + 0.01f * p, y - hgt * 0.55f, 0.15f * p, hgt * 0.26f, fur)
        c.fillEllipse(x + 0.05f * p, y - hgt * 0.4f, 0.08f * p, hgt * 0.3f, furL)
        c.fillEllipse(x - 0.12f * p, y - hgt * 0.35f, 0.06f * p, hgt * 0.22f, furD)
        // the forepaws
        c.fillEllipse(x + 0.09f * p, y - hgt * 0.62f, 0.04f * p + 0.5f, 0.05f * p + 0.5f, furD)
        // the head, the snout, an eye and an ear
        val hx = x + 0.03f * p; val hy2 = y - hgt * 0.9f
        c.fillEllipse(hx, hy2, 0.12f * p, 0.1f * p + 0.5f, fur)
        c.fillEllipse(hx + 0.08f * p, hy2 + 0.03f * p, 0.05f * p + 0.5f, 0.04f * p + 0.5f, furL)
        c.set((hx + 0.12f * p).toInt(), (hy2 + 0.02f * p).toInt(), Col.hex(0x1E1A18))
        c.set((hx + 0.04f * p).toInt(), (hy2 - 0.03f * p).toInt(), Col.hex(0x1E1A18))
        c.fillEllipse(hx - 0.07f * p, hy2 - 0.08f * p, 0.03f * p + 0.5f, 0.03f * p + 0.5f, furD)
    }

    // ------------------------------------------------------------------ the trees

    /**
     * A spruce [hgt] m tall at side [x], depth [d]: tier on tier of drooping branches, lit on the sun's side, snow on them
     * in winter; [big]: the one framing the view, with needles closer up.
     */
    private fun spruceTree(x: Float, d: Float, hgt: Float, seed: Int, big: Boolean = false) {
        val z = z
        val p = pm(d); val bx = gx(x, d); val by = gy(d)
        val hh = hgt * p; val half = hgt * 0.19f * p
        val dark = Col.hex(0x1A3E2A); val mid = Col.hex(0x285A3A); val light = Col.hex(0x3E7A4E); val tip = Col.hex(0x5A9A62)
        val sunL = (sunAt()?.first ?: vx) < bx
        val tw = max(z.toFloat(), 0.35f * p)
        c.fillRect((bx - tw / 2).toInt(), (by - 1.4f * p).toInt(), tw.toInt(), (1.4f * p).toInt() + z, Col.hex(0x4A3428))
        val tiers = if (big) 12 else 9
        for (i in 0 until tiers) {
            val f = (i + 1) / tiers.toFloat()
            val ty = by - 1.0f * p - (hh - 1.0f * p) * (1f - f)
            val tw2 = half * (0.12f + 0.88f * f)
            val th = (hh / tiers) * 1.7f
            val rows = th.toInt().coerceAtLeast(2 * z)
            for (yy in 0 until rows) {
                val v = yy / th
                val ww = tw2 * (0.3f + 0.7f * v)
                val y = (ty - th + yy).toInt()
                if (y < c.top - 2 * z || y >= c.bottom) continue
                for (xx in max((bx - ww).toInt(), c.left)..min((bx + ww).toInt(), c.right - 1)) {
                    val u = (xx + 0.5f - bx) / max(1f, ww)
                    if (abs(u) > 0.9f && v < 0.8f && Dither.at(xx, y) < 0.5f) continue
                    val grain = (Noise.rnd(xx / z + (if (big && z > 1) xx % z else 0), y / z, 781 + seed) - 0.5f) * 0.5f
                    val lit = (if (sunL) -u else u) * 0.6f - (1f - v) * 0.4f + grain
                    val col = when {
                        env.snow && v < 0.42f + (Noise.rnd(xx / z, i, 782) - 0.5f) * 0.3f -> if (lit > 0f) Pal.SNOW_L else Pal.SNOW_M
                        lit > 0.35f -> light
                        lit > -0.15f -> mid
                        else -> dark
                    }
                    c.set(xx, y, col)
                }
                if (v > 0.85f) { c.set((bx - ww).toInt(), y + 1, tip); c.set((bx + ww).toInt(), y + 1, tip) }
            }
        }
        for (r in 1..2 * z) c.set(bx.toInt(), (by - hh).toInt() - r, if (r > z) dark else mid)
    }

    /**
     * A larch [hgt] m tall at side [x], depth [d]: a straight trunk, a narrow cone of soft, airy foliage in whorls with the
     * trunk showing through, fresh green in spring, green in summer, gold in October, and in winter bare: grey twigs drooping
     * from the trunk, snow along them.
     */
    private fun larchTree(x: Float, d: Float, hgt: Float, seed: Int, big: Boolean = false) {
        val z = z
        val p = pm(d); val bx = gx(x, d); val by = gy(d)
        val hh = hgt * p; val half = hgt * 0.17f * p
        val bark = Col.hex(0x5A3E2E); val barkL = Col.hex(0x7E5A42)
        val (dk, md, lt) = when (env.month) {
            10 -> Triple(Col.hex(0xA8701E), Col.hex(0xD49A2E), Col.hex(0xF0C650))
            11 -> Triple(Col.hex(0x8A5A22), Col.hex(0xB47A2A), Col.hex(0xD8A040))
            12, 1, 2 -> Triple(0, 0, 0)
            3 -> Triple(Col.hex(0x5E5A48), Col.hex(0x7A7A52), Col.hex(0x98A060))
            4, 5 -> Triple(Col.hex(0x5E9A36), Col.hex(0x86C04A), Col.hex(0xB6E070))
            else -> Triple(Col.hex(0x3E6E2E), Col.hex(0x5A8E3A), Col.hex(0x82B452))
        }
        val bare = dk == 0
        val sunL = (sunAt()?.first ?: vx) < bx
        // the trunk, tapering to the top
        for (py in max((by - hh).toInt(), c.top)..min(by.toInt(), c.bottom - 1)) {
            val f = (by - py) / hh
            val tw = max(z * 0.6f, 0.18f * p * (1f - f * 0.9f))
            for (px in (bx - tw).toInt()..(bx + tw).toInt()) c.set(px, py, if ((px < bx) == sunL) barkL else bark)
        }
        val tiers = if (big) 15 else 11
        if (bare) {
            // bare twigs drooping from the trunk in whorls, snow along them
            for (i in 0 until tiers) {
                val f = (i + 0.5f) / tiers
                val y = by - hh * (0.2f + 0.78f * f)
                val len = half * (1f - f * 0.85f) * (0.85f + 0.3f * Noise.rnd(i, seed, 791))
                for (side in intArrayOf(-1, 1)) {
                    val steps = max(2, len.toInt())
                    for (s2 in 0..steps) {
                        val g = s2 / steps.toFloat()
                        val xx = bx + side * g * len; val yy = y + 0.3f * len * g * g
                        c.set(xx.toInt(), yy.toInt(), bark)
                        if (env.snow && s2 % 3 == 1) c.set(xx.toInt(), yy.toInt() - 1, Pal.SNOW_L)
                    }
                }
            }
            return
        }
        // the foliage: whorl on whorl, each a soft band drooping at its ends, the trunk showing between them
        for (i in 0 until tiers) {
            val f = (i + 1) / tiers.toFloat() // 0 top .. 1 bottom
            val ty = by - 1.6f * p - (hh - 1.6f * p) * (1f - f)
            val tw2 = half * (0.12f + 0.88f * f)
            val th = (hh / tiers) * 1.15f
            val rows = th.toInt().coerceAtLeast(2 * z)
            for (yy in 0 until rows) {
                val v = yy / th
                val ww = tw2 * (0.35f + 0.65f * v)
                val y = (ty - th + yy).toInt()
                if (y < c.top - 2 * z || y >= c.bottom) continue
                for (xx in max((bx - ww).toInt(), c.left)..min((bx + ww).toInt(), c.right - 1)) {
                    val u = (xx + 0.5f - bx) / max(1f, ww)
                    // soft edges, a few gaps near the trunk where it shows
                    val tuft = Noise.v2((xx - bx) * 0.5f / z, y * 0.7f / z, 795 + seed)
                    if (abs(u) > 0.8f && tuft < 0.45f + (abs(u) - 0.8f) * 2f) continue
                    if (abs(u) < 0.2f && v < 0.45f && tuft < 0.35f) continue
                    val lit = (if (sunL) -u else u) * 0.55f - (1f - v) * 0.3f + (tuft - 0.5f) * 0.7f
                    c.set(xx, y, if (lit > 0.28f) lt else if (lit > -0.2f) md else dk)
                }
                if (v > 0.8f) { c.set((bx - ww).toInt() - 1, y + 1, md); c.set((bx + ww).toInt() + 1, y + 1, md) }
            }
        }
        for (r in 1..2 * z) c.set(bx.toInt(), (by - hh).toInt() - r, md)
    }

    /** The trees at the pasture's sides: side, depth, whether a larch. */
    private val FOREST = listOf(
        Triple(-13.2f, 25f, false), Triple(-15.6f, 28f, true), Triple(-18.5f, 24f, false), Triple(-23f, 27f, true), Triple(-28f, 25f, false),
        Triple(14.6f, 27f, false), Triple(17.5f, 24.5f, true), Triple(21f, 27.5f, false), Triple(26f, 24f, false),
    )

    // ------------------------------------------------------------------ what is close to us

    /**
     * Close to us below the stage (a tall view shows it): stones half in the grass, clumps of gentians and alpine roses in
     * summer, crocuses in spring; the stream and the path run on down.
     */
    private fun nearEdge() {
        if (gy(10.2f) >= h) return
        val z = z
        for (k in 0 until 14) {
            val d = 5.4f + Noise.rnd(k, 801) * 4.6f; val x = (Noise.rnd(k, 802) - 0.5f) * 12f
            if (abs(x - brook(d)) < brookHalf(d) + 0.7f || abs(x - path(d)) < 0.9f) continue
            if (gy(d) >= h + 4 * z) continue
            if (k % 3 == 0) prop { boulder(x, d, 0.55f + Noise.rnd(k, 803) * 0.5f, 0.3f + Noise.rnd(k, 804) * 0.25f, 805 + k, mossy = true) }
            else if (!env.snow && env.month in 3..9) prop(0) { clump(x, d, k) }
        }
    }

    /** A clump of flowers close by: leaves, then blooms of the month (crocus, gentian, alpine rose, a yellow one). */
    private fun clump(x: Float, d: Float, seed: Int) {
        val p = pm(d); val bx = gx(x, d); val by = gy(d)
        val leaf = Col.scale(env.grass[2], 0.8f)
        val bloom = when (env.month) { 3, 4 -> Col.hex(0xB08AD8); 5, 6 -> if (seed % 2 == 0) Col.hex(0x3A62D0) else Col.hex(0xD8587E); else -> if (seed % 2 == 0) Col.hex(0xD8587E) else Col.hex(0xF0D458) }
        val n = 5 + seed % 4
        for (j in 0 until n) {
            val sx = bx + (Noise.rnd(j, seed, 811) - 0.5f) * 0.5f * p
            val top = by - (0.08f + 0.1f * Noise.rnd(j, seed, 812)) * p
            c.line(sx.toInt(), by.toInt(), sx.toInt(), top.toInt(), leaf)
            val r = max(1f, 0.035f * p)
            c.fillEllipse(sx, top, r, r * 0.8f, bloom)
            if (r >= 2f) c.set(sx.toInt(), top.toInt(), Col.hex(0xFFF4C0))
        }
    }

    // ------------------------------------------------------------------ what a dialog brings

    /**
     * Alpine choughs ([fx] "birds": how far they have flown): black birds with yellow bills rising off the crags of the
     * massif, wheeling as a loose flock and gliding away to the left.
     */
    private fun flock(level: Float) {
        if (level <= 0.01f || level >= 0.995f) return
        val z = z
        val col = env.lit(Col.hex(0x1C1A20)); val bill = env.lit(Col.hex(0xE8C030))
        val e = level * level * (3f - 2f * level)
        val cx = 130f - 150f * e + sin(e * 9f) * 16f
        val cy = (horizon - 30f) - 26f * sin(e * 3.14f) + cos(e * 9f) * 5f
        for (j in 0 until 16) {
            val a = Noise.rnd(j, 811) * 6.28f + t.toFloat() * (0.9f + Noise.rnd(j, 812))
            val r = (4f + 10f * sin(e * 3.14f)) * sqrt(Noise.rnd(j, 813))
            val x = ox + ((cx + cos(a) * r * 1.5f) * z).toInt(); val y = oy + ((cy + sin(a) * r * 0.7f) * z).toInt()
            val up = ((t * 10).toInt() + j) and 1
            sprite(x, y) {
                PokeArt.wings(c, x, y, up == 0, 1, col)
                c.set(x + 1, y, bill)
            }
        }
    }

    // ------------------------------------------------------------------ weather

    /** Fog lies in the valley over the lake; rain veils the peaks before it reaches the pasture. */
    override fun weather() {
        val z = z
        val rain = frame.sky.rain
        if (rain > 0.02f) {
            val grey = env.lit(Col.hex(0x9AA2AE))
            for (py in max(0, c.top) until min(hz + 36 * z, c.bottom)) {
                val row = py / z.toFloat()
                val prof = if (py < hz) exp((row - hz / z.toFloat()) / 30f) else 1f - (py - hz) / (36f * z)
                if (prof <= 0.02f) continue
                for (px in max(0, c.left) until min(w, c.right)) {
                    val shaft = 0.55f + 0.45f * Noise.v1((px - ox) / z * 0.06f - row * 0.02f + t.toFloat() * 0.12f, 821)
                    val a = rain * 0.6f * prof * shaft
                    if (a > Dither.at(px, py) * 0.5f) c.pixels[c.index(px, py)] = Col.mix(c.pixels[c.index(px, py)], grey, min(0.75f, a))
                }
            }
        }
        val fog = frame.sky.fog
        if (fog > 0.02f) {
            // a lake of fog in the valley: thick over the water, thinning up the slopes, drifting
            val white = Col.mix(env.lit(Col.hex(0xE8ECF0)), Col.hex(0xE8ECF0), 0.3f)
            for (py in max(hz - 14 * z, c.top) until min(hz + 44 * z, c.bottom)) {
                val row = (py - hz) / z.toFloat()
                val prof = if (row < 4f) exp((row - 4f) / 7f) else 1f - ((row - 4f) / 40f).coerceIn(0f, 1f) * 0.5f
                for (px in max(0, c.left) until min(w, c.right)) {
                    if (py >= browRow[px]) continue
                    val drift = 0.6f + 0.4f * Noise.v2((px - ox) / z * 0.04f + t.toFloat() * 0.05f, row * 0.12f, 822)
                    val a = fog * 0.85f * prof * drift
                    if (a > Dither.at(px, py) * 0.6f) c.pixels[c.index(px, py)] = Col.mix(c.pixels[c.index(px, py)], white, min(0.85f, a))
                }
            }
        }
        super.weather()
    }

    companion object {
        private const val SKY_STEPS = 48
        private const val FAR = 1
        private const val MASS = 2
        private const val PEAK = 3
        private const val MID = 4
        private const val FLANK = 5

        /** Triglav and its neighbours: scene column, height above the horizon, half width (scene px). Triglav first, then its two heads. */
        private val PEAKS = floatArrayOf(
            152f, 58f, 74f,
            141f, 51f, 22f,
            163f, 52f, 22f,
            110f, 42f, 50f,
            194f, 45f, 54f,
            70f, 28f, 50f,
            236f, 31f, 50f,
        )

        private val ROCK_L = Col.hex(0xC4C0C4)
        private val ROCK_L2 = Col.hex(0xB0ACB6)
        private val ROCK_D = Col.hex(0x7C7A8E)
        private val ROCK_M = Col.hex(0x9C99AA)
        private val ROCK_GULLY = Col.hex(0x5C5A70)
        private val SNOW_LIT = Col.hex(0xF6F9FF)
        private val SNOW_SHADE = Col.hex(0xB8C4DE)
        private val GLOW_ROCK = Col.hex(0xF09A7E)
        private val GLOW_SNOW = Col.hex(0xFFC0A8)
        private val GLOW_SHADE = Col.hex(0x8A6A9E)
        private val FAR_L = Col.hex(0x98A2C8)
        private val FAR_D = Col.hex(0x747CA4)
        private val FOREST_FAR = Col.hex(0x2E4E40)
        private val FOREST_FAR_L = Col.hex(0x44684E)
        private val SPRUCE_D = Col.hex(0x1A3626)
        private val SPRUCE_M = Col.hex(0x264A32)
        private val SPRUCE_L = Col.hex(0x3A6844)
        private val STREAM_EDGE = Col.hex(0x3A5A48)
        private val LICHEN = Col.hex(0xC8C49A)
    }
}
