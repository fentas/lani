package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.Season
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Na gričih: the village's vineyard on the hill (the project "vinograd", open once its terraces are finished), looking
 * along its rows toward Brda. The rows of vines run away from us either side of a gravel track and converge on a white
 * farmstead with a red-tiled roof at the far end, its wine cellar's arched door under the house, a tall cypress and
 * round trees about it. Beyond it the rolling hills of Brda lie layer on layer, sunlit green on their tops and violet in
 * the shade below, farms and cypresses along their crests, one crowned by a little church with its bell tower; behind
 * them the blue Julian Alps and, far to the south, a glint of the sea. In front a dry-stone terrace wall runs across the
 * right, the persimmon (kaki) tree standing behind it; the nearest vine at our feet has a bunch of grapes on it and a
 * crate waits at the end of its row.
 *
 * The year shows in the vines: bare and pruned in winter (a few dry bunches left), leaves in spring, green grapes in
 * summer, the grape harvest (trgatev) in September and October with ripe bunches and full crates, gold and red leaves
 * in autumn; the kaki keeps its orange fruit on bare branches into November. A dialog can ring the church bell, send a
 * tractor with crates down the track and startle the starlings off the vines; rain veils the far hills before it reaches us.
 *
 * Closer up ([detail] 2 and 3) the same view is drawn finer: leaves in clusters with shade between them on the vines,
 * the rows on the far hills, the farmstead's shutters, tiles and portal, the church's roof in courses of tiles, the
 * crates' slats, the kaki's fruit with its calyx, the joints, lichen and moss of the wall; the near vine's leaves are
 * five-lobed and its bunch comes berry by berry at every detail.
 */
internal class HillsPainter : VistaPainter() {
    override val art = "hills"

    /** West-south-west over the vineyards, toward the plain where the sun sets. */
    override val skyFacing = 250.0

    // the plan, in metres: the track down the middle to the farmstead, the vine rows either side of it (the outermost
    // first; a row's hedge faces the track), the wall across the right in front, the farmstead and its trees at the rows'
    // far end, the kaki on the right
    private fun road(d: Float) = 0.3f + 0.12f * sin(d * 0.21f)
    private val roadHalf = 1.2f
    private val leftRows = floatArrayOf(-11.2f, -9.0f, -6.8f, -4.6f, -2.1f)
    private val rightRows = floatArrayOf(11.6f, 9.4f, 5.0f, 2.8f)
    private val rowNear = 4.5f; private val rowFar = 54f
    private val vineX = -2.1f; private val vineD = 10.9f
    private val crateX = -1.05f; private val crateD = 10.3f
    private val wallD = 11.3f; private val wallA = 1.75f; private val wallB = 13f
    private val kakiX = 7.4f; private val kakiD = 14.6f
    private val houseX = 0.5f; private val houseD = 63f
    private val cypressX = 7.8f; private val cypressD = 58f

    /** The round trees and the cypresses about the farmstead: side, depth, height, whether a cypress. */
    private val grove = listOf(
        floatArrayOf(-7.5f, 67f, 9f, 0f), floatArrayOf(-6.8f, 61f, 11f, 1f), floatArrayOf(9f, 69f, 8f, 0f), floatArrayOf(13.5f, 64f, 10f, 1f),
        floatArrayOf(-12f, 70f, 12f, 1f), floatArrayOf(-15f, 62f, 8f, 0f), floatArrayOf(18f, 71f, 9f, 0f),
        // along the meadows beyond the rows, either side
        floatArrayOf(-19f, 48f, 9f, 0f), floatArrayOf(-24f, 57f, 13f, 1f), floatArrayOf(-30f, 52f, 10f, 0f), floatArrayOf(-17f, 80f, 10f, 0f),
        floatArrayOf(21f, 50f, 9f, 0f), floatArrayOf(27f, 60f, 12f, 1f), floatArrayOf(40f, 58f, 9f, 0f),
    )

    /** Lavender bushes in the foreground, in front of the wall and at the rows' ends: side, depth. */
    private val lavenders = listOf(3.1f to 9.5f, 4.8f to 9.0f, 6.6f to 9.7f, 8.2f to 8.8f, 2.6f to 7.4f, 5.4f to 6.9f, -1.35f to 7.0f)

    private val items = ArrayList<Pair<Float, () -> Unit>>()

    /** Where the near hills come down to the flat (m): the ground is drawn nearer than this. */
    private val HILLS_D = 88f

    /** Picture pixels per scene canvas pixel. */
    private val z: Int get() = detail

    /** How much taller the hills and the mountains stand in this view: a taller view has sky to spare above the stage. */
    private var lift = 1f

    override fun paint() {
        setup()
        val z = z
        lift = 1f + 0.45f * (oy / z / 55f).coerceIn(0f, 1f)
        vistaSky(4, 21, moonX = ox + 60 * z, moonY = oy + 10 * z)
        thing("mountains", outline = 0) { alps() }
        thing("sea", outline = 0) { seaGlint() }
        farHills()
        thing("hill", outline = Col.hex(0x46603A)) { bigHill() }
        church()
        // woods along the near hills' foot, behind the farmstead's grove
        prop(0) { woodsAt(gy(HILLS_D - 1f).toInt()) }
        // the ground from where the near hills come down to our feet
        c.penId = 0
        for (py in max(gy(HILLS_D).toInt(), c.top) until min(h, c.bottom)) {
            val d = depthAt(py)
            for (px in max(0, c.left) until min(w, c.right)) c.set(px, py, land(sideAt(px, d), d, px, py))
        }
        thing("road") { roadPixels() }

        items.clear()
        for ((k, g) in grove.withIndex()) items.add(g[1] to { prop(Pal.OUTLINE_TREE) { if (g[3] > 0f) cypress(g[0], g[1], g[2], k + 3) else leafyTree(g[0], g[1], g[2], k * 7 + 5) } })
        items.add(houseD to { thing("farmhouse") { farmhouse() } })
        items.add(cypressD to { thing("cypress", outline = Pal.OUTLINE_TREE) { cypress(cypressX, cypressD, 13f, 1) } })
        items.add(rowFar + 1f to { prop(Col.hex(0x1E3A1E)) { vineyard(outer = true) } })
        items.add(rowFar to { thing("vineyard", outline = Col.hex(0x1E3A1E)) { vineyard(outer = false) } })
        items.add(kakiD to { groundShadow(kakiX, kakiD, 2.2f); thing("persimmon", outline = Pal.OUTLINE_TREE) { kaki(kakiX, kakiD) } })
        val tractor = fxOn("tractor")
        if (tractor > 0.01f) { val d = 58f - 50f * tractor; items.add(d to { prop { tractor(d) } }) }
        items.add(wallD to { thing("wall") { dryWall(wallA, wallB, wallD, 0.85f, 91) } })
        for (p in peopleAt("wall")) items.add(10.6f to { groundShadow(3.3f, 10.6f, 0.5f); person(p, gx(3.3f, 10.6f).toInt(), gy(10.6f).toInt(), flip = true) })
        for (p in peopleAt("road")) items.add(15.6f to { val x = road(15.6f) + 0.5f; groundShadow(x, 15.6f, 0.5f); person(p, gx(x, 15.6f).toInt(), gy(15.6f).toInt(), flip = false) })
        for (p in peopleAt("vines")) items.add(12.9f to { groundShadow(-1.05f, 12.9f, 0.5f); person(p, gx(-1.05f, 12.9f).toInt(), gy(12.9f).toInt(), flip = false) })
        items.add(vineD to {
            thing("vine", outline = Col.hex(0x1E3A1E)) { nearVine() }
            thing("grapes", slop = 2) { bunch(gx(vineX + 0.3f, vineD), hy(vineD, 0.95f), pm(vineD), 0, big = true) }
        })
        items.add(crateD to { groundShadow(crateX, crateD, 0.4f); thing("crate", slop = 1) { crate() } })
        for ((k, lv) in lavenders.withIndex()) if (gy(lv.second) < h + 8 * z) items.add(lv.second to { prop(Col.hex(0x3A4A36)) { lavender(lv.first, lv.second, k) } })
        items.sortByDescending { it.first }
        for ((_, draw) in items) draw()

        foregroundGrass(0, w, 23)
        prop(outline = 0) { starlings(fxOn("starlings")) }
        fireflies(0, hz + 14 * z, w, h, 12, 47)
        s.fx { s.effects.leaves(env.season, env.month) }
    }

    // ------------------------------------------------------------------ far away

    /**
     * The Julian Alps along the back, blue with the distance: tallest on the left (the north), lower ridges toward the
     * south; rock lit on the sun's side, gullies, snow on the peaks most of the year (the tops only in summer).
     */
    private fun alps() {
        val z = z
        val rock = if (env.season == Season.WINTER) Col.hex(0xB6C4D8) else Col.hex(0x6E80B4)
        val body = env.lit(Col.mix(rock, env.skyHorizon, 0.46f)); val shade = env.lit(Col.mix(Col.scale(rock, 0.84f), env.skyHorizon, 0.46f))
        val snow = env.lit(Col.mix(Pal.SNOW_L, env.skyHorizon, 0.34f)); val snowD = env.lit(Col.mix(Pal.SNOW_D, env.skyHorizon, 0.36f))
        val snowLine = when (env.season) { Season.SUMMER -> 0.8f; Season.AUTUMN -> 0.62f; else -> 0.4f }
        val sunLeft = (sunAt()?.first ?: vx) < vx
        val crest = FloatArray(w)
        for (x in 0 until w) {
            val u = (x - ox) / z.toFloat() / STAGE_W
            // tall on the left, sinking toward the south
            val tall = 0.35f + 0.65f * ((0.9f - u) / 0.7f).coerceIn(0f, 1f)
            val a = 1f - abs(Noise.v1(u * 8f, 211) * 2f - 1f)
            val b = 1f - abs(Noise.v1(u * 21f, 212) * 2f - 1f)
            crest[x] = hz - (12f + 30f * tall * (a * a * 0.7f + b * 0.3f)) * lift * z
        }
        c.penEmissive = true
        for (x in max(0, c.left) until min(w, c.right)) {
            val top = crest[x]
            val peak = (hz - top) / (42f * lift * z)
            // the side facing the sun, from the slope a few columns either side (single columns would stripe)
            val facing = (crest[min(w - 1, x + 3 * z)] - crest[max(0, x - 3 * z)]).let { if (sunLeft) it > 0 else it < 0 }
            for (y in max(max(0, top.toInt()), c.top) until min(hz + 1, c.bottom)) {
                val up = (hz - y) / (42f * lift * z)
                val snowy = up > snowLine + (Noise.v2((x - ox) * 0.08f / z, up * 6f, 213) - 0.5f) * 0.3f && peak > snowLine
                val gully = !snowy && Noise.v2((x - ox) * 0.12f / z + up * 3f, up * 8f, 214) > 0.78f
                c.set(x, y, when {
                    snowy -> if (facing) snow else snowD
                    gully -> Col.scale(shade, 0.9f)
                    facing || Dither.at(x, y) < 0.3f -> body
                    else -> shade
                })
            }
        }
        c.penEmissive = false
    }

    /**
     * The sea far to the south, on the right: a band of the Gulf of Trieste over the plain at the horizon, pale where it
     * meets the sky, with glints drifting on it (the moon's by night).
     */
    private fun seaGlint() {
        val z = z
        val x0 = ox + 168 * z
        val body = env.lit(Col.mix(Col.hex(0x3F84C4), env.skyHorizon, 0.45f))
        val line = Col.mix(env.skyHorizon, Col.hex(0xFFFFFF), 0.3f)
        c.penEmissive = true
        for (x in max(x0, c.left) until min(w, c.right)) {
            val u = (x - x0) / (40f * z)
            val rows = (1.5f + 2.5f * min(1f, u)) * z
            val top = (hz - 2 * z - rows).toInt()
            for (y in max(top, c.top) until min(hz - 2 * z, c.bottom)) {
                val g = Noise.rnd(floor((x - ox) / (3f * z) + t * 0.3).toInt(), (y - top) / z, 215)
                c.set(x, y, when {
                    y < top + z -> line
                    g > 0.9f -> if (env.dark > 0.5f) Col.mix(body, Col.hex(0xD8D2B0), 0.4f) else Col.mix(line, Col.hex(0xFFFFFF), 0.6f)
                    else -> body
                })
            }
        }
        c.penEmissive = false
    }

    /**
     * The rolling hills of Brda to the horizon, layer on layer: the farthest blue in the haze, then greener, the nearest
     * right behind the farmstead striped with vineyards, sunlit on their tops and violet in the shade of their lower
     * slopes, farms and cypresses along the crests; lower in the middle, where the view opens, and low on the right
     * toward the plain and the sea.
     */
    private fun farHills() {
        val z = z
        val season = env.season
        val farCol = when (season) { Season.WINTER -> Col.hex(0x9EA8BC); Season.AUTUMN -> Col.hex(0x8A8A6E); else -> Col.hex(0x6E8AA0) }
        val midCol = when (season) { Season.WINTER -> Col.hex(0x9A9C98); Season.AUTUMN -> Col.hex(0x96864A); else -> Col.hex(0x5E8E5A) }
        val nearCol = when (season) { Season.WINTER -> Col.hex(0x9A9486); Season.AUTUMN -> Col.hex(0xA88E3E); Season.SPRING -> Col.hex(0x8EB85A); else -> Col.hex(0x8CAE44) }
        rolling(0.016f, 231, farCol, 0.42f, lift = 13f, amp = 9f)
        val mid = rolling(0.022f, 232, midCol, 0.26f, lift = 9f, amp = 8f) { x, y, below -> violet(farVines(x, y, below, midCol), below, 0.25f) }
        val near = rolling(0.03f, 233, nearCol, 0.12f, lift = 4f, amp = 9f, dip = true, bottom = gy(HILLS_D).toInt() + z) { x, y, below -> violet(farVines(x, y, below, nearCol), below, 0.45f) }
        treeline(mid, 9 * z, 1.8f * z, if (season == Season.WINTER) Col.hex(0x6A7A7A) else Col.hex(0x355A34), 0.24f, 238)
        // farms and cypresses along the crests
        for (i in 0 until 14) {
            val fx = (ox + (-40 + Noise.rnd(i, 234) * 320) * z).toInt()
            if (fx !in 0 until w || fx in (ox + 150 * z)..(ox + 220 * z)) continue
            val crest = if (i % 2 == 0) near else mid
            val base = crest[fx] + (2 + (Noise.rnd(i, 235) * 3).toInt()) * z
            if (i % 3 == 0) farCypress(fx, base, if (crest === near) 0.1f else 0.24f) else farHouse(fx, base, i)
        }
    }

    /**
     * A band of woods lying along the near hills' foot down to row [base]: round crowns in rows, lit on top, dark between,
     * autumn-coloured, bare and grey in winter.
     */
    private fun woodsAt(base: Int) {
        val z = z
        val (dk, md, lt) = when (env.season) {
            Season.AUTUMN -> Triple(Col.hex(0x6A4A22), Col.hex(0x9A6A2A), Col.hex(0xC89A3A))
            Season.WINTER -> Triple(Col.hex(0x5E5850), Col.hex(0x746C62), Col.hex(0x8A8276))
            Season.SPRING -> Triple(Col.hex(0x3A6A2E), Col.hex(0x5A9040), Col.hex(0x86B85A))
            else -> Triple(Col.hex(0x2A5230), Col.hex(0x3A6E3A), Col.hex(0x5A904A))
        }
        c.penEmissive = true
        val top = base - 5 * z
        for (x in max(0, c.left) until min(w, c.right)) {
            val u = (x + 0.5f - ox) / z
            val cell = floor(u / 4f).toInt(); val fu = u / 4f - cell
            val crown = top + ((1f - kotlin.math.sqrt(max(0f, 1f - (fu * 2f - 1f) * (fu * 2f - 1f)))) * 3f + Noise.rnd(cell, 239) * 2.5f) * z
            for (y in max(crown.toInt(), c.top) until min(base, c.bottom)) {
                val v = (y - crown) / max(1f, base - crown)
                val col = if (v < 0.25f && fu < 0.6f) lt else if (v > 0.7f || fu > 0.85f) dk else md
                c.set(x, y, env.lit(Col.mix(col, env.skyHorizon, 0.1f)))
            }
        }
        c.penEmissive = false
    }

    /** The shade of a far slope's lower part: violet in the pixel painters' way, [share] of it at the foot. */
    private fun violet(col: Int, below: Int, share: Float): Int {
        val f = (below / (9f * z)).coerceIn(0f, 1f) * share
        return if (f > 0.05f) Col.mix(col, if (env.season == Season.WINTER) Col.hex(0x8A8AA8) else Col.hex(0x7A5E8E), f) else col
    }

    /**
     * A layer of hills: the crest [lift] + [amp] × noise px above the horizon, lower toward the right (the plain), filled
     * down to the horizon in [col] hazed by [haze], lit on the sun's side; [texture] paints the slope. Returns the crest.
     */
    private fun rolling(freq: Float, seed: Int, col: Int, haze: Float, lift: Float, amp: Float, dip: Boolean = false, bottom: Int = hz + z, texture: ((Int, Int, Int) -> Int)? = null): IntArray {
        val z = z
        val crest = IntArray(w)
        for (x in 0 until w) {
            val u = (x - ox) / z.toFloat()
            val low = ((u - 196f) / 30f).coerceIn(0f, 1f)
            val n = Noise.v1(u * freq * 3f, seed) * 0.7f + Noise.v1(u * freq * 8f, seed + 1) * 0.3f
            // [dip]: lower in the middle, rising to the sides, as hills either side of a valley
            val side = if (dip) 0.55f + 0.45f * (abs(u - 118f) / 110f).coerceIn(0f, 1f) else 1f
            crest[x] = (hz - (lift + amp * n) * side * (1f - 0.92f * low) * this.lift * z).toInt()
        }
        val body = env.lit(Col.mix(col, env.skyHorizon, haze)); val shade = env.lit(Col.mix(Col.scale(col, 0.84f), env.skyHorizon, haze))
        val rim = env.lit(Col.mix(Col.mix(col, Col.hex(0xFFF0C8), 0.25f), env.skyHorizon, haze * 0.8f))
        val sunLeft = (sunAt()?.first ?: vx) < vx
        c.penEmissive = true
        for (x in max(0, c.left) until min(w, c.right)) {
            val top = crest[x]
            val facing = (crest[min(w - 1, x + z)] - crest[max(0, x - z)]).let { if (sunLeft) it > 0 else it < 0 }
            for (y in max(max(0, top), c.top) until min(bottom, c.bottom)) {
                var col2 = if (texture != null) env.lit(Col.mix(texture(x, y, y - top), env.skyHorizon, haze)) else if ((y - top) > (hz - top) * 0.55f + (Dither.at(x, y) - 0.5f) * 3f) shade else body
                if (y - top < z && facing) col2 = Col.mix(col2, rim, 0.6f)
                c.set(x, y, col2)
            }
        }
        c.penEmissive = false
        return crest
    }

    /** Vineyards on the far slopes: rows along the hill in the vines' colour of the month, woods between the parcels. */
    private fun farVines(x: Int, y: Int, below: Int, col: Int): Int {
        val z = z
        val sx = (x - ox) / z.toFloat(); val sy = y / z.toFloat()
        val parcel = Noise.v2(sx * 0.05f, sy * 0.2f, 236)
        if (parcel < 0.3f) return if (env.season == Season.WINTER) Col.hex(0x6E6A5E) else Col.scale(col, 0.72f) // a wood
        val stripe = ((below / z) + (Noise.v1(sx * 0.06f, 237) * 3f).toInt()) % 2 == 0
        return if (stripe) canopy(0.8f) else Col.mix(col, soil(), 0.35f)
    }

    /** A farmhouse far off: a white wall, a red roof, a lit window at dusk. */
    private fun farHouse(x: Int, base: Int, seed: Int) {
        val z = z
        val wall = env.lit(Col.mix(Pal.WALL_L, env.skyHorizon, 0.2f)); val roof = env.lit(Col.mix(Pal.ROOF_M, env.skyHorizon, 0.18f))
        c.penEmissive = true
        c.fillRect(x, base - 3 * z, (3 + seed % 2) * z, 3 * z, wall)
        for (r in 0 until 2 * z) c.hline(x - z + r / 2, x + (4 + seed % 2) * z - 1 - r / 2, base - 3 * z - 1 - r, roof)
        c.fillRect(x + z, base - 2 * z, z, z, if (env.windows > 0.35f) Pal.WINDOW_LIT else env.lit(Pal.GLASS))
        c.penEmissive = false
    }

    /** A cypress far off: a dark flame [h] px tall. */
    private fun farCypress(x: Int, base: Int, haze: Float) {
        val z = z
        val col = env.lit(Col.mix(Col.hex(0x1E3A26), env.skyHorizon, haze))
        val hh = 8 * z
        c.penEmissive = true
        for (j in 0 until hh) {
            val f = j / hh.toFloat()
            val half = (z * 1.3f * sin(f * 3.1f).coerceAtLeast(0.2f)).toInt()
            c.hline(x - half, x + half, base - j, col)
        }
        c.penEmissive = false
    }

    // ------------------------------------------------------------------ the church hill

    /** The church hill's crest row at picture column [x], or the horizon's foot where it isn't. */
    private fun hillTop(x: Int): Float {
        val u = ((x - ox) / z.toFloat() - 184f) / 46f
        val bump = exp(-u * u * 2.6f) * 0.9f + exp(-(u + 1.1f) * (u + 1.1f) * 5f) * 0.18f
        return hz + (6f - 30f * bump * lift) * z
    }

    private val hillFoot: Float get() = hz + 6f * z

    /**
     * The church hill on the right, the tallest of the near hills: vineyards in rows along its contours (in the month's
     * colour), a stone terrace wall every few rows, woods in its folds, violet in the shade of its foot; the church on its
     * top ([church]).
     */
    private fun bigHill() {
        val z = z
        val grass = env.grass
        val wallCol = Col.mix(Pal.STONE_L, env.skyHorizon, 0.15f)
        val sunLeft = (sunAt()?.first ?: vx) < vx
        for (x in max(0, c.left) until min(w, c.right)) {
            val top = hillTop(x)
            if (top >= hillFoot - 2.5f * z) continue
            val slope = hillTop(min(w - 1, x + z)) - hillTop(max(0, x - z))
            val lit = if (sunLeft) slope > 0 else slope < 0
            for (y in max(top.toInt(), c.top) until min(hillFoot.toInt() + z, c.bottom)) {
                val below = (y - top) / z
                // rows along the contour: the row index counts down from the crest, a little wavy
                val v = below / 2.2f + Noise.v1((x - ox) * 0.05f / z, 241) * 1.5f
                val ri = floor(v).toInt()
                val fr = v - ri
                var col = when {
                    below < 1.2f -> grass[1]
                    Noise.v2((x - ox) * 0.04f / z, below * 0.08f, 244) > 0.72f -> Col.scale(grass[2], 0.8f) // a wood in a fold
                    ri % 5 == 4 && fr > 0.6f -> wallCol
                    fr < 0.45f -> canopy(0.7f + Noise.rnd(x / z, ri, 242) * 0.3f)
                    else -> Col.mix(grass[if (fr > 0.8f) 0 else 1], soil(), 0.25f)
                }
                if (z > 1 && fr < 0.45f && Noise.rnd(x, y, 243) < 0.15f) col = Col.scale(col, 0.88f)
                if (!lit && Dither.at(x, y) < 0.35f) col = Col.scale(col, 0.9f)
                col = violet(col, (y - (hillFoot - 10f * z)).toInt().coerceAtLeast(0), 0.4f)
                c.set(x, y, hazy(col, 90f))
            }
        }
        for ((cx, cy) in listOf(166f to 2f, 200f to -4f, 176f to -12f)) farCypress((ox + cx * z).toInt(), (hz + cy * z).toInt(), 0.1f)
    }

    /** The church on the hilltop, its apse toward us, a red-tiled roof; the bell tower beside it with the bell in its belfry. */
    private fun church() {
        val z = z
        val cx = (ox + 184 * z)
        val base = hillTop(cx).toInt() + 2 * z
        val wall = env.lit(Col.mix(Pal.WALL_L, env.skyHorizon, 0.12f)); val wallD = env.lit(Col.mix(Pal.WALL_D, env.skyHorizon, 0.12f))
        val roof = env.lit(Col.mix(Pal.ROOF_M, env.skyHorizon, 0.1f)); val roofD = env.lit(Col.mix(Pal.ROOF_D, env.skyHorizon, 0.1f))
        val stone = env.lit(Col.mix(Pal.STONE_L, env.skyHorizon, 0.12f))
        val lit = env.windows > 0.35f
        thing("church") {
            c.penEmissive = true
            c.fillRect(cx - 4 * z, base - 5 * z, 10 * z, 5 * z, wall)
            c.fillRect(cx + 5 * z, base - 5 * z, z, 5 * z, wallD)
            c.hline(cx - 4 * z, cx + 6 * z - 1, base - 1, stone)
            // the roof, in courses of tiles closer up
            for (r in 0 until 3 * z) {
                val a = cx - 5 * z + r; val b = cx + 7 * z - 1 - r
                val col = if (r < z) roofD else if (z > 1 && (r / 2) % 2 == 1) Col.mix(roof, roofD, 0.35f) else roof
                c.hline(a, b, base - 5 * z - 1 - r, col)
            }
            // the door and a round window over it
            c.fillRect(cx + z, base - 2 * z, max(1, z + z / 2), 2 * z, env.lit(Pal.DOOR))
            c.fillRect(cx + z + z / 2, base - 4 * z, z, z, if (lit) Pal.WINDOW_LIT else env.lit(Pal.GLASS))
        }
        thing("belltower") {
            c.penEmissive = true
            val tl = cx - 8 * z; val tw = 3 * z
            c.fillRect(tl, base - 15 * z, tw, 15 * z, wall)
            c.fillRect(tl + tw - z, base - 15 * z, z, 15 * z, wallD)
            c.hline(tl, tl + tw - 1, base - 10 * z, wallD)
            c.hline(tl, tl + tw - 1, base - 1, stone)
            // the belfry: a dark opening with the bell, swinging when it rings
            val ring = fxOn("bell")
            val swing = if (ring > 0.02f) sin(t * 5.0).toFloat() * ring else 0f
            c.fillRect(tl + z / 2, base - 14 * z, max(2, 2 * z), 2 * z + z / 2, env.lit(Col.hex(0x2A2226)))
            val bx = tl + tw / 2 + (swing * z * 0.8f).toInt()
            c.fillRect(bx - z / 2, base - 13 * z, max(1, z), max(1, z + z / 2), Pal.GOLD)
            if (z > 1) c.set(bx - z / 2, base - 13 * z, Pal.GOLD_L)
            // the pyramid roof and a little cross
            for (r in 0 until 4 * z) {
                val half = (tw / 2f + z * 0.5f) * (1f - r / (4f * z))
                c.hline((tl + tw / 2f - half).toInt(), (tl + tw / 2f + half).toInt() - 1, base - 15 * z - 1 - r, if (r < z) roofD else roof)
            }
            c.vline(tl + tw / 2, base - 21 * z, base - 19 * z - 1, Pal.GOLD)
            c.hline(tl + tw / 2 - z / 2, tl + tw / 2 + z / 2, base - 20 * z, Pal.GOLD)
            if (lit) c.set(tl + tw / 2, base - 6 * z, Pal.WINDOW_LIT)
            if (ring > 0.02f) bellRings(tl + tw / 2, base - 12 * z, ring)
        }
    }

    /** The bell's ringing: arcs going out either side of the belfry and fading, as long as it rings. */
    private fun bellRings(x: Int, y: Int, level: Float) {
        val z = z
        s.fx {
            c.penEmissive = true
            for (j in 0 until 3) {
                val ph = ((t * 0.9 + j / 3.0) % 1.0).toFloat()
                val r = (3f + ph * 12f) * z
                val a = (1f - ph) * level
                for (side in intArrayOf(-1, 1)) for (q in -6..6) {
                    val ang = q * 0.11f
                    for (th in 0 until z) {
                        val rr = r + th
                        c.blend((x + side * rr * cos(ang)).toInt(), (y + rr * sin(ang)).toInt(), Col.hex(0xFFD86A), a)
                    }
                }
            }
            c.penEmissive = false
        }
    }

    // ------------------------------------------------------------------ the ground

    /** The soil of the vineyard, by the month: dark after rain, pale and dusty in summer. */
    private fun soil(): Int = if (env.season == Season.SUMMER) Col.hex(0x9A7650) else Col.hex(0x7A5A3C)

    /** How much of the vines is in leaf in this month: bare in winter, full in summer, thinning in November. */
    private val leafy: Float get() = when (env.month) { 12, 1, 2 -> 0f; 3 -> 0.05f; 4 -> 0.35f; 5 -> 0.75f; 10 -> 0.9f; 11 -> 0.35f; else -> 1f }

    /**
     * The vines' leaves in the month's colour, [l] 0 (shade) .. 1 (lit); [v] 0..1 picks between the leaves that turn
     * early and late in autumn (the gold of the rebula, the red of the merlot), and still green ones in September.
     */
    private fun canopy(l: Float, v: Float = 0f): Int {
        val m = env.month
        val (d, mid, lt) = when {
            m == 4 || m == 5 -> Triple(Col.hex(0x5A8A34), Col.hex(0x7AAE44), Col.hex(0xA8D46A))
            m == 9 -> if (v > 0.6f) Triple(Col.hex(0x6A6E24), Col.hex(0x9A9A34), Col.hex(0xC8C050)) else Triple(Col.hex(0x3E6426), Col.hex(0x5E8A32), Col.hex(0x8EAE48))
            m == 10 -> when {
                v > 0.66f -> Triple(Col.hex(0x7A2E1A), Col.hex(0xA84426), Col.hex(0xD0703A))
                v > 0.33f -> Triple(Col.hex(0x8A5A1E), Col.hex(0xB8862A), Col.hex(0xDCB448))
                else -> Triple(Col.hex(0x5A6A26), Col.hex(0x8A9034), Col.hex(0xB8B04A))
            }
            m == 11 -> if (v > 0.5f) Triple(Col.hex(0x5E2A1A), Col.hex(0x8A4424), Col.hex(0xAE6A34)) else Triple(Col.hex(0x6A4A1E), Col.hex(0x9A6E2A), Col.hex(0xBE9440))
            m == 12 || m <= 3 -> Triple(Col.hex(0x5A4636), Col.hex(0x6E5A48), Col.hex(0x847060))
            else -> Triple(Col.hex(0x2E5A28), Col.hex(0x437A34), Col.hex(0x6AA24A))
        }
        return if (l > 0.66f) lt else if (l > 0.33f) mid else d
    }

    /** The ground: soil worked under each vine row, grass between the rows and along the track, a meadow at the sides; snow in winter's cold spells. */
    private fun land(x: Float, d: Float, px: Int, py: Int): Int {
        val g = env.grass
        val n = Noise.v2(x * 0.6f, d * 0.6f, 251) + (Dither.at(px, py) - 0.5f) * 0.25f
        var col = if (n > 0.62f) g[0] else if (n > 0.32f) g[1] else g[2]
        if (!env.snow && env.season != Season.SUMMER && d < 20f && Noise.rnd(px, py, 252) < 0.012f) col = Pal.PAINTED[(px * 3 + py) % 6]
        if (d < HILLS_D && d > rowNear - 1f) {
            // under the rows the soil is worked (the marl of Brda, the opoka); between them, grass
            val rows = if (x < 0f) leftRows else rightRows
            for (rx in rows) if (abs(x - rx) < 0.36f && d > rowStart(rx) - 0.6f && d < rowEnd(rx) + 0.6f) {
                col = if (Noise.rnd(px, py, 253) < 0.2f) Col.scale(soil(), 1.15f) else soil()
                break
            }
        }
        if (env.snow && Noise.v2(x * 0.4f, d * 0.5f, 254) > 0.4f) col = if (n > 0.5f) Pal.SNOW_L else Pal.SNOW_M
        if (z > 1 && d < 22f && !env.snow && Noise.rnd(px, py / 2, 255) < 0.06f) col = g[0]
        return hazy(col, d)
    }

    /** The track: white gravel of the hills, two worn wheel tracks, grass along its crown; straight down the middle to the farmstead. */
    private fun roadPixels() {
        val z = z
        val snow = env.snow
        val gravel = if (snow) intArrayOf(Pal.SNOW_L, Pal.SNOW_M, Pal.SNOW_D) else intArrayOf(Col.hex(0xE0D6C0), Col.hex(0xC8BCA2), Col.hex(0xA8987C))
        for (py in max(hz + 1, c.top) until min(h, c.bottom)) {
            val d = depthAt(py)
            if (d > houseD - 2.5f) continue
            val cx = gx(road(d), d); val half = roadHalf * pm(d)
            for (px in max((cx - half - 1).toInt(), c.left)..min((cx + half + 1).toInt(), c.right - 1)) {
                if (px !in 0 until w) continue
                val off = (px + 0.5f - cx) / half
                if (abs(off) + (Noise.rnd(px, py, 256) - 0.5f) * 0.2f > 1f) continue
                val r = Noise.rnd(px, py, 257)
                var col = if (r < 0.15f) gravel[0] else if (r > 0.88f) gravel[2] else gravel[1]
                if (abs(abs(off) - 0.5f) < 0.12f && half > 3f * z) col = gravel[2]
                if (abs(off) < 0.1f && half > 5f && !snow && r < 0.6f) col = env.grass[1]
                c.set(px, py, hazy(col, d))
            }
        }
    }

    // ------------------------------------------------------------------ the vineyard

    /**
     * The vine rows running away from us either side of the track ([outer]: the rows farther out, else the two either side
     * of the track, the vineyard to tap), the outermost first: each a hedge of leaves on wires
     * between posts, its face toward the track lit, bunches hanging under it in summer and autumn; bare in winter, the
     * trunks, the cordons along the wires and the pruned spurs showing. The right-hand rows begin behind the wall (the
     * outermost behind the kaki); the nearest left row behind the near vine.
     */
    private fun vineyard(outer: Boolean) {
        for ((i, rx) in leftRows.withIndex()) if ((rx < -4f) == outer) vineRow(rx, rowStart(rx), rowEnd(rx), i, 1f)
        for ((i, rx) in rightRows.withIndex()) if ((rx > 4f) == outer) vineRow(rx, rowStart(rx), rowEnd(rx), i + 7, -1f)
    }

    /** Where the row at side [rx] ends: the ones along the track at the farmstead's yard, the outer ones on past it toward the woods. */
    private fun rowEnd(rx: Float): Float = if (abs(rx) < 4f) rowFar else HILLS_D - 4f

    /** Where the row at side [rx] begins: the nearest left one behind the near vine, the right ones behind the wall (the outermost behind the kaki). */
    private fun rowStart(rx: Float): Float = when {
        rx == leftRows.last() -> vineD + 0.9f
        rx < 0f -> rowNear
        rx > 7f -> kakiD + 3.5f
        else -> wallD + 1f
    }

    /** One row at side [rx] from [dNear] to [dFar] m, its hedge's face toward the track on the side [side] (+1: right). */
    private fun vineRow(rx: Float, dNear: Float, dFar: Float, idx: Int, side: Float) {
        val z = z
        val lo = 0.55f; val hi = 1.55f
        val face = rx + 0.22f * side
        val leaf = leafy
        // posts every 4 m: grey acacia stakes
        var d = dFar
        val post = Col.hex(0x8A7E6E); val postD = Col.hex(0x5E5448)
        while (d >= dNear) {
            val p = pm(d); val x = gx(rx, d)
            val pw = max(z.toFloat(), 0.1f * p)
            c.fillRect((x - pw / 2).toInt(), hy(d, 1.8f).toInt(), max(1, pw.toInt()), (gy(d) - hy(d, 1.8f)).toInt() + 1, hazy(post, d))
            if (pw >= 2f) c.vline((x + pw / 2).toInt() - 1, hy(d, 1.8f).toInt(), gy(d).toInt(), hazy(postD, d))
            d -= 4f
        }
        // the trunks, where there are pixels enough between them
        d = dFar
        while (d >= dNear) {
            val p = pm(d)
            if (p > 3.5f) {
                val x = gx(rx, d)
                val tw = max(1, (0.09f * p).toInt())
                for (j in 0 until tw) c.line((x + j).toInt(), gy(d).toInt(), (x + j + 0.05f * p * side).toInt(), hy(d, lo + 0.25f).toInt(), hazy(Col.hex(0x5A4030), d))
            }
            d -= 1.1f
        }
        if (leaf < 0.5f) bareRow(rx, dNear, dFar)
        if (leaf <= 0.02f) return
        // the leaves: the face toward the track (a column is one depth) and the top (a row is one depth)
        val xn = gx(face, dNear); val xf = gx(face, dFar)
        val left = min(xn, xf).toInt(); val right = max(xn, xf).toInt()
        for (px in max(left, c.left)..min(right, c.right - 1)) {
            val dd = F * face / (px + 0.5f - vx)
            if (dd < dNear || dd > dFar) continue
            val y0 = hy(dd, hi); val y1 = hy(dd, lo)
            for (py in max(y0.toInt(), c.top)..min(y1.toInt(), c.bottom - 1)) {
                val hgt = EYE - (py + 0.5f - hz) * dd / F
                val n = Noise.v2(dd * 2.2f, hgt * 5f, 261 + idx) + (if (z > 1) (Noise.rnd(px, py, 262) - 0.5f) * 0.25f else 0f)
                if (n > leaf + 0.05f + (hgt - lo) * 0.15f) continue
                val l = (hgt - lo) / (hi - lo) * 0.7f + (Noise.rnd(px, py, 263) - 0.5f) * 0.35f + (if (z > 1) (n - 0.4f) * 0.4f else 0f)
                c.set(px, py, hazy(canopy(l + 0.25f, Noise.v2(dd * 0.9f, hgt * 2f, 268 + idx)), dd))
            }
        }
        // the top of the hedge, a little lighter
        val ytop = hy(dNear, hi).toInt(); val yfar = hy(dFar, hi).toInt()
        for (py in max(yfar, c.top)..min(ytop, c.bottom - 1)) {
            val dd = F * (EYE - hi) / (py + 0.5f - hz)
            if (dd < dNear || dd > dFar) continue
            val xa = gx(min(face, rx - 0.22f * side), dd); val xb = gx(max(face, rx - 0.22f * side), dd)
            for (px in max(xa.toInt(), c.left)..min(xb.toInt(), c.right - 1)) {
                val across = (px + 0.5f - xa) / max(1f, xb - xa)
                val n = Noise.v2(dd * 2.2f, across * 3f, 264 + idx)
                if (n > leaf + 0.1f) continue
                // leaves heaped on top: lit clusters, shade between them
                val l = 0.55f + (n - 0.35f) * 0.9f + (Noise.rnd(px, py, 265) - 0.5f) * 0.3f
                c.set(px, py, hazy(canopy(l, Noise.v2(dd * 0.9f, 3f, 268 + idx)), dd))
            }
        }
        // bunches under the leaves, near enough to see
        if (env.month in 5..11) {
            d = dNear + 0.4f
            var k = 0
            while (d < min(dFar, 30f)) {
                if (Noise.rnd(k, idx, 266) < 0.55f) bunch(gx(face, d), hy(d, lo + 0.08f), pm(d), idx)
                d += 0.9f; k++
            }
        }
    }

    /** A row without its leaves: the wires, the cordon along the lower one, the pruned spurs standing up from it. */
    private fun bareRow(rx: Float, dNear: Float, dFar: Float) {
        val wire = Col.hex(0x9A9488); val cane = Col.hex(0x6A4A32)
        for (hgt in floatArrayOf(0.8f, 1.25f, 1.7f)) {
            c.line(gx(rx, dNear).toInt(), hy(dNear, hgt).toInt(), gx(rx, dFar).toInt(), hy(dFar, hgt).toInt(), if (hgt == 0.8f) hazy(cane, 20f) else hazy(wire, 20f))
        }
        var d = dNear
        var k = 0
        while (d < dFar) {
            val p = pm(d)
            if (p > 4f) {
                val x = gx(rx, d); val y = hy(d, 0.8f)
                c.line(x.toInt(), y.toInt(), (x + (Noise.rnd(k, 267) - 0.5f) * 0.2f * p).toInt(), (y - 0.3f * p).toInt(), hazy(cane, d))
            }
            d += 0.55f; k++
        }
    }

    /**
     * A bunch of grapes at ([x], [y]) (its top), at [p] px per metre: berries in a cone, rebula's golden green or the
     * merlot's purple by [kind], with the month (flowers in May, green in July, ripe in September; a few dry bunches
     * left over winter). [big]: the near vine's, drawn berry by berry.
     */
    private fun bunch(x: Float, y: Float, p: Float, kind: Int, big: Boolean = false) {
        val m = env.month
        val red = kind % 2 == 1
        val (d, mid, lt) = when {
            m in 5..6 -> Triple(Col.hex(0x6A9A3A), Col.hex(0x8ABA4A), Col.hex(0xB8DA7A))
            m == 7 -> Triple(Col.hex(0x5E8A3A), Col.hex(0x7EAA48), Col.hex(0xA8CC6A))
            m == 8 -> if (red) Triple(Col.hex(0x4A2A48), Col.hex(0x6A8A48), Col.hex(0x9A6A8A)) else Triple(Col.hex(0x7A8A3A), Col.hex(0xA8B04A), Col.hex(0xD0D07A))
            m in 9..10 -> if (red) Triple(Col.hex(0x2A1830), Col.hex(0x4A2A52), Col.hex(0x7A5A8A)) else Triple(Col.hex(0x8A8030), Col.hex(0xC0B044), Col.hex(0xE8DC86))
            else -> Triple(Col.hex(0x3A2420), Col.hex(0x5A3A2C), Col.hex(0x7A5A40)) // dry, left over
        }
        val size = when { m in 5..6 -> 0.55f; m == 7 -> 0.75f; m in 8..10 -> 1f; else -> 0.6f }
        val len = 0.22f * p * size; val wid = 0.1f * p * size
        if (big) {
            // berry by berry: rows narrowing to the tip, each lit at its top left
            val r = max(0.8f, 0.028f * p * (0.6f + 0.4f * size))
            c.line(x.toInt(), (y - 0.04f * p).toInt(), x.toInt(), y.toInt(), Col.hex(0x5A6A30))
            var yy = y + r
            var row = 0
            while (yy < y + len * 1.2f + r) {
                val f = (yy - y) / (len * 1.2f)
                val half = wid * 1.1f * (1f - f * 0.75f)
                var xx = x - half + (row % 2) * r
                while (xx <= x + half) {
                    c.fillCircle(xx, yy, r, mid)
                    c.set((xx + r * 0.4f).toInt(), (yy + r * 0.4f).toInt(), d)
                    c.set((xx - r * 0.35f).toInt(), (yy - r * 0.35f).toInt(), lt)
                    xx += r * 1.7f
                }
                yy += r * 1.5f; row++
            }
            return
        }
        if (len < 1f) { c.set(x.toInt(), y.toInt(), hazy(mid, 30f)); return }
        for (j in 0..len.toInt()) {
            val f = j / max(1f, len)
            val half = wid * (1f - f * 0.8f)
            c.hline((x - half).toInt(), (x + half).toInt(), (y + j).toInt(), if (j == 0) lt else if (f > 0.6f) d else mid)
        }
    }

    /** The nearest vine: a gnarled trunk, the cordon along the wire, its leaves (five-lobed closer up) or its bare spurs. */
    private fun nearVine() {
        val z = z
        val p = pm(vineD); val bx = gx(vineX, vineD); val by = gy(vineD)
        val bark = Col.hex(0x5A4030); val barkL = Col.hex(0x7A5A40)
        // the trunk, twisting up to the wire, and the cordon along it
        val top = hy(vineD, 0.8f)
        val tw = max(2f, 0.1f * p)
        var y = by
        while (y > top) {
            val f = (by - y) / (by - top)
            val xx = bx + sin(f * 4f) * 0.05f * p
            c.hline((xx - tw / 2).toInt(), (xx + tw / 2).toInt(), y.toInt(), bark)
            c.set((xx - tw / 2).toInt(), y.toInt(), barkL)
            y -= 1f
        }
        for (j in 0 until max(1, (tw * 0.7f).toInt())) c.line(bx.toInt(), top.toInt() + j, (bx + 0.9f * p).toInt(), (top - 0.05f * p).toInt() + j, if (j == 0) barkL else bark)
        c.line(bx.toInt(), top.toInt(), (bx - 0.6f * p).toInt(), (top - 0.03f * p).toInt(), bark)
        if (leafy < 0.3f) {
            // bare: spurs off the cordon
            for (k in 0 until 5) {
                val sx = bx - 0.5f * p + k * 0.33f * p
                c.line(sx.toInt(), top.toInt(), (sx + (k - 2) * 0.05f * p).toInt(), (top - 0.35f * p).toInt(), barkL)
            }
            if (leafy <= 0.02f) return
        }
        // the leaves: big five-lobed vine leaves in a hedge over the cordon, lit on top
        val cy = hy(vineD, 1.2f)
        for (k in 0 until 11) {
            if (Noise.rnd(k, 271) > leafy + 0.1f) continue
            val lx = bx - 0.55f * p + Noise.rnd(k, 272) * 1.4f * p
            val ly = cy - 0.35f * p + Noise.rnd(k, 273) * 0.6f * p
            vineLeaf(lx, ly, (0.13f + Noise.rnd(k, 274) * 0.05f) * p, 0.35f + Noise.rnd(k, 275) * 0.5f)
        }
    }

    /** One vine leaf [r] px across round ([x], [y]): lobed closer up, lit toward the top; [l] its light. */
    private fun vineLeaf(x: Float, y: Float, r: Float, l: Float) {
        for (yy in (y - r).toInt()..(y + r).toInt()) for (xx in (x - r).toInt()..(x + r).toInt()) {
            val dx = (xx + 0.5f - x) / r; val dy = (yy + 0.5f - y) / r
            val a = kotlin.math.atan2(dy, dx)
            val lobe = 0.78f + 0.22f * cos(a * 5f + 1.6f)
            val q = dx * dx + dy * dy
            if (q > lobe * lobe) continue
            val lit = l - dy * 0.35f - q * 0.15f
            c.set(xx, yy, canopy(lit))
        }
        if (r >= 3f) c.line(x.toInt(), y.toInt(), x.toInt(), (y + r * 0.7f).toInt(), canopy(0.1f))
    }

    /** A harvest crate at the end of the row: slatted wood, full of grapes at the trgatev, empty the rest of the year. */
    private fun crate() {
        val z = z
        val p = pm(crateD); val bx = gx(crateX, crateD); val by = gy(crateD)
        val ww = 0.55f * p; val hh = 0.3f * p
        val x0 = (bx - ww / 2).toInt(); val y0 = (by - hh).toInt()
        val full = env.month in 8..10
        if (full) for (k in 0 until 7) bunchTop(x0 + (k + 0.5f) * ww / 7f, y0.toFloat(), p, k)
        box(x0, y0, ww.toInt().coerceAtLeast(3), hh.toInt().coerceAtLeast(2), Col.hex(0xD8A868), Pal.WOOD_L, Pal.WOOD_D)
        val slats = if (z > 1) 3 else 2
        for (j in 1 until slats) c.hline(x0 + 1, x0 + ww.toInt() - 2, y0 + (hh * j / slats).toInt(), Pal.WOOD_M)
        if (z > 1) { c.vline(x0 + (ww * 0.33f).toInt(), y0 + 1, y0 + hh.toInt() - 2, Pal.WOOD_M); c.vline(x0 + (ww * 0.66f).toInt(), y0 + 1, y0 + hh.toInt() - 2, Pal.WOOD_M) }
    }

    /** Grapes heaped over the crate's rim. */
    private fun bunchTop(x: Float, y: Float, p: Float, k: Int) {
        val red = k % 3 == 0
        val col = if (red) Col.hex(0x4A2A52) else Col.hex(0xC0B044); val lt = if (red) Col.hex(0x7A5A8A) else Col.hex(0xE8DC86)
        val r = max(1f, 0.07f * p)
        c.fillCircle(x, y - r * 0.4f, r, col)
        c.set((x - r * 0.3f).toInt(), (y - r).toInt(), lt)
    }

    // ------------------------------------------------------------------ standing things

    /**
     * A lavender bush: a mound of grey-green stems, purple spikes of flowers over it from June to August (the bees about
     * them), grey and dry in winter.
     */
    private fun lavender(x: Float, d: Float, seed: Int) {
        val z = z
        val p = pm(d); val bx = gx(x, d); val by = gy(d)
        val wid = 0.55f * p; val tall = 0.5f * p
        val leaf = if (env.snow) Col.hex(0x8A9088) else Col.hex(0x7A9A7A); val leafD = if (env.snow) Col.hex(0x6A7068) else Col.hex(0x5A7A5E)
        val flowering = env.month in 6..8
        val bloom = Col.hex(0x8A62C0); val bloomL = Col.hex(0xB08AE0)
        val n = max(8, (wid * 1.6f).toInt())
        for (k in 0 until n) {
            val u = (k + 0.5f) / n * 2f - 1f
            val hgt = tall * (1f - u * u * 0.6f) * (0.8f + 0.3f * Noise.rnd(k, seed, 291))
            val lean = u * 0.3f * p + sin(t * 1.3 + k * 0.7 + seed).toFloat() * 0.02f * p * gust
            val x0 = bx + u * wid * 0.8f
            c.line(x0.toInt(), by.toInt(), (x0 + lean).toInt(), (by - hgt).toInt(), if (k % 3 == 0) leafD else leaf)
            if (z > 1) c.line(x0.toInt() + 1, by.toInt(), (x0 + lean).toInt() + 1, (by - hgt * 0.8f).toInt(), leafD)
            if (flowering) {
                val sx = x0 + lean; val sy = by - hgt
                val spike = max(2f, 0.14f * p)
                for (j in 0 until spike.toInt()) c.set((sx + (if (j % 2 == 0) 0 else 1)).toInt(), (sy - j).toInt(), if (j < spike * 0.4f) bloomL else bloom)
            } else if (env.snow) c.set((x0 + lean).toInt(), (by - hgt).toInt() - 1, Pal.SNOW_L)
        }
    }

    /**
     * The farmstead (domačija) at the far end of the rows, its long side toward us: whitewashed walls on a stone plinth,
     * stone quoins at the corners, a low roof of red clay tiles (korci) with a chimney, small windows with green shutters
     * (lit at dusk), the arched stone portal of the wine cellar under the house, a pergola of vines by the door; snow on
     * the roof in winter, smoke from the chimney on a cold morning or evening.
     */
    private fun farmhouse() {
        val z = z
        val d = houseD; val p = pm(d)
        val by = gy(d); val bx = gx(houseX, d)
        val hw = 5.6f; val wallH = 5.2f; val roofH = 1.9f
        val x0 = gx(houseX - hw, d); val x1 = gx(houseX + hw, d)
        val wall = hazy(Col.hex(0xF2ECDC), d); val wallD = hazy(Col.hex(0xD6CCB6), d)
        val stone = hazy(Col.hex(0xB4AA96), d); val stoneD = hazy(Col.hex(0x8A826E), d)
        val tile = hazy(Col.hex(0xB8583A), d); val tileD = hazy(Col.hex(0x8A3A24), d); val tileL = hazy(Col.hex(0xD4744A), d)
        val shutter = hazy(Col.hex(0x3E6E3A), d)
        val sunLeft = (sunAt()?.first ?: vx) < bx
        // the walls, shaded toward the side away from the sun; the plinth and the quoins of stone
        for (py in max(hy(d, wallH).toInt(), c.top)..min(by.toInt(), c.bottom - 1)) {
            val hm = (by - py - 0.5f) / p
            for (px in max(x0.toInt(), c.left)..min(x1.toInt(), c.right - 1)) {
                val u = (px + 0.5f - x0) / max(1f, x1 - x0)
                var col = if ((u > 0.5f) == sunLeft && u !in 0.1f..0.9f) wallD else wall
                if (hm < 0.6f) col = if (Noise.rnd(px / z, py / z, 281) < 0.3f) stoneD else stone
                if ((u < 0.05f || u > 0.95f) && ((hm / 0.45f).toInt() % 2 == 0)) col = stone
                c.set(px, py, col)
            }
        }
        // the cellar's arched portal, left of the middle, down in the plinth and up into the wall
        val ax = gx(houseX - 2.2f, d); val aw = 1.5f * p; val ah = 2.2f * p
        for (py in max((by - ah).toInt(), c.top)..min(by.toInt(), c.bottom - 1)) {
            val v = (by - py) / ah
            val half = aw / 2f * (if (v > 0.7f) kotlin.math.sqrt(max(0f, 1f - ((v - 0.7f) / 0.3f).let { it * it })) else 1f)
            for (px in max((ax - half - z).toInt(), c.left)..min((ax + half + z).toInt(), c.right - 1)) {
                val inside = abs(px + 0.5f - ax) <= half
                c.set(px, py, if (inside) hazy(Col.hex(0x5A3A24), d) else stone)
            }
        }
        // windows with shutters, lit at dusk; the house door with a pergola by it
        val night = env.windows > 0.35f
        for ((k, wxm) in floatArrayOf(-4.3f, -0.4f, 1.6f, 3.9f).withIndex()) {
            val wy = if (k == 0) 3.9f else 3.6f
            val wx = gx(houseX + wxm, d); val ww = 0.8f * p; val wh = 1.1f * p
            val top = hy(d, wy + 1.1f)
            glow { c.fillRect((wx - ww / 2).toInt(), top.toInt(), max(1, ww.toInt()), max(1, wh.toInt()), if (night) Pal.WINDOW_LIT else hazy(Pal.GLASS, d)) }
            c.fillRect((wx - ww / 2 - 0.35f * p).toInt(), top.toInt(), max(1, (0.3f * p).toInt()), max(1, wh.toInt()), shutter)
            c.fillRect((wx + ww / 2 + 0.05f * p).toInt(), top.toInt(), max(1, (0.3f * p).toInt()), max(1, wh.toInt()), shutter)
            if (night) s.light(wx, top + wh / 2f, 14f, 0.35f)
        }
        val dx = gx(houseX + 0.6f, d)
        c.fillRect((dx - 0.5f * p).toInt(), hy(d, 2.1f).toInt(), max(1, p.toInt()), (by - hy(d, 2.1f)).toInt(), hazy(Pal.DOOR, d))
        // the roof: low, of clay tiles in courses, its eaves over the wall, a chimney
        val ey = hy(d, wallH); val ry = hy(d, wallH + roofH)
        for (py in max(ry.toInt(), c.top)..min(ey.toInt() + z, c.bottom - 1)) {
            val v = (py - ry) / max(1f, ey - ry)
            val over = 0.5f * p * v
            for (px in max((x0 - over).toInt(), c.left)..min((x1 + over).toInt(), c.right - 1)) {
                val course = ((py - ry) / max(1.5f, 0.35f * p)).toInt()
                var col = if (py >= ey.toInt()) tileD else if (course % 2 == 0) tile else if (Noise.rnd(px / max(1, z), course, 282) < 0.3f) tileL else tile
                if (z > 1 && (px / (2 * z) + course) % 3 == 0 && py < ey) col = Col.mix(col, tileD, 0.4f)
                if (env.snow && v < 0.9f) col = if (course % 2 == 0) Pal.SNOW_L else Pal.SNOW_M
                c.set(px, py, col)
            }
        }
        val cx = gx(houseX + 3.2f, d)
        c.fillRect(cx.toInt(), (ry + 0.6f * p - 1.2f * p).toInt(), max(z, (0.6f * p).toInt()), (0.9f * p).toInt(), wallD)
        c.fillRect(cx.toInt() - max(1, z / 2), (ry - 0.6f * p).toInt(), max(z, (0.6f * p).toInt()) + 2 * max(1, z / 2), max(1, z), tileD)
        val hr = s.hour
        if (env.season != Season.SUMMER && (hr in 6f..9f || hr in 17.5f..21f)) s.smoke(cx + z, ry - 0.8f * p, 0.45f, 13)
    }

    /** A cypress: a tall dark flame of scale-like foliage, lit on the sun's side, swaying a little at its tip. */
    private fun cypress(x: Float, d: Float, height: Float, seed: Int) {
        val z = z
        val p = pm(d); val bx = gx(x, d); val by = gy(d)
        val hh = height * p; val half = 0.75f * p
        val sunLeft = (sunAt()?.first ?: vx) < bx
        val dark = Col.hex(0x183424); val mid = Col.hex(0x24482E); val light = Col.hex(0x3A6A40)
        val sway = sin(t * 1.1 + seed).toFloat() * 0.06f * p * gust
        c.fillRect((bx - 0.1f * p).toInt(), (by - 0.5f * p).toInt(), max(z, (0.2f * p).toInt()), (0.5f * p).toInt() + 1, Col.hex(0x4A3428))
        for (j in 0 until hh.toInt()) {
            val f = j / hh
            // widest a third of the way up, a pointed tip
            val wid = half * (if (f < 0.3f) 0.75f + f else (1.05f - (f - 0.3f) * 1.3f)).coerceAtLeast(0.08f)
            val y = (by - 0.35f * p - j).toInt()
            if (y < c.top - 1 || y >= c.bottom) continue
            // only the upper half sways: the tree's outline (its widest part) stays where it is
            val cx = bx + sway * (max(0f, f - 0.5f) * 2f).let { it * it }
            for (xx in max((cx - wid).toInt(), c.left)..min((cx + wid).toInt(), c.right - 1)) {
                val u = (xx + 0.5f - cx) / max(1f, wid)
                val grain = Noise.v2(xx * 0.5f / z, y * 0.35f / z, 290 + seed) - 0.5f
                val lit = (if (sunLeft) -u else u) * 0.55f + grain * 0.7f
                if (abs(u) > 0.85f && Dither.at(xx, y) < 0.4f) continue
                c.set(xx, y, if (lit > 0.3f) light else if (lit > -0.2f) mid else dark)
            }
        }
    }

    /**
     * The kaki (persimmon) behind the wall: a dark trunk, a round crown; its leaves glossy green in summer and orange-red in
     * October, and its fruit green, then orange, hanging on into November on bare branches; bare in winter.
     */
    private fun kaki(x: Float, d: Float) {
        val z = z
        val p = pm(d); val bx = gx(x, d); val by = gy(d)
        val m = env.month
        val bark = Col.hex(0x3E2E26)
        val trunkTop = by - 1.8f * p
        c.fillRect((bx - 0.14f * p).toInt(), trunkTop.toInt(), max(2, (0.28f * p).toInt()), (by - trunkTop).toInt() + 1, bark)
        val cy = trunkTop - 1.5f * p; val cr = 2.1f * p
        val leaves = when (m) { 12, 1, 2, 3 -> 0f; 4 -> 0.5f; 11 -> 0.25f; else -> 1f }
        // the branches, always there (they show through where the leaves are thin)
        fun branch(x0: Float, y0: Float, a: Float, len: Float, depth: Int) {
            val x1 = x0 + sin(a) * len; val y1 = y0 - cos(a) * len
            c.line(x0.toInt(), y0.toInt(), x1.toInt(), y1.toInt(), bark)
            if (depth > 0) { branch(x1, y1, a - 0.5f, len * 0.65f, depth - 1); branch(x1, y1, a + 0.45f, len * 0.62f, depth - 1) }
        }
        for (a in floatArrayOf(-0.7f, -0.25f, 0.2f, 0.65f)) branch(bx, trunkTop, a, cr * 0.5f, 2)
        if (leaves > 0f) {
            val (dk, md, lt) = when (m) {
                10 -> Triple(Col.hex(0x8A3A1A), Col.hex(0xC8622A), Col.hex(0xE89A3A))
                11 -> Triple(Col.hex(0x7A2E16), Col.hex(0xA84A22), Col.hex(0xD07A2E))
                4, 5 -> Triple(Col.hex(0x3E7A2E), Col.hex(0x62A240), Col.hex(0x9ACC62))
                else -> Triple(Col.hex(0x1E4A22), Col.hex(0x2E6A2E), Col.hex(0x4E9444))
            }
            val sunLeft = (sunAt()?.first ?: vx) < bx
            val lx = if (sunLeft) -1f else 1f
            // a round crown of clumps, the middle one first, the rest round it, the top ones last
            for (k in 0 until 12) {
                val a = k * 2.4f + 0.3f
                val ring = if (k == 0) 0f else if (k < 8) 0.55f else 0.3f
                val rr = cr * (if (k == 0) 0.6f else 0.36f + Noise.rnd(k, 301) * 0.12f)
                val px = bx + sin(a) * cr * ring; val py = cy + cos(a) * cr * ring * 0.7f - (if (k >= 8) cr * 0.3f else 0f)
                for (yy in max((py - rr).toInt(), c.top)..min((py + rr).toInt(), c.bottom - 1)) for (xx in max((px - rr).toInt(), c.left)..min((px + rr).toInt(), c.right - 1)) {
                    val dx = (xx + 0.5f - px) / rr; val dy = (yy + 0.5f - py) / (rr * 0.9f)
                    val q = dx * dx + dy * dy
                    if (q > 1f || (q > 0.75f && Dither.at(xx, yy) < 0.4f)) continue
                    if (Noise.rnd(xx / max(1, z), yy / max(1, z), 302) > leaves + 0.05f) continue
                    val lit = -dx * lx * 0.5f - dy * 0.8f + (Noise.rnd(xx, yy, 303) - 0.5f) * 0.4f
                    c.set(xx, yy, if (lit > 0.45f) lt else if (lit > -0.2f) md else dk)
                }
            }
        }
        // the fruit: green in summer, orange from September, on the bare branches into winter's start
        if (m in 6..12) {
            val ripe = m >= 9
            val fruit = if (ripe) Col.hex(0xF08A1E) else Col.hex(0x8AAA3A); val fruitL = if (ripe) Col.hex(0xFFC060) else Col.hex(0xB8D06A)
            val n = if (m == 12) 6 else 14
            val r = max(1f, 0.13f * p * (if (m in 6..7) 0.7f else 1f))
            for (k in 0 until n) {
                val a = Noise.rnd(k, 304) * 6.28f; val rad = sqrt01(Noise.rnd(k, 305)) * cr * 0.85f
                val fx = bx + cos(a) * rad; val fy = cy + sin(a) * rad * 0.75f
                c.fillCircle(fx, fy, r, fruit)
                if (r >= 1.5f) c.set((fx - r * 0.35f).toInt(), (fy - r * 0.35f).toInt(), fruitL)
                if (z > 1) c.set(fx.toInt(), (fy - r).toInt(), Col.hex(0x3A4A22)) // the calyx
            }
        }
    }

    private fun sqrt01(v: Float) = kotlin.math.sqrt(v)

    // ------------------------------------------------------------------ what a dialog brings

    /**
     * A small vineyard tractor coming down the road with a trailer of crates ([fx] "tractor": how far it has come, from the
     * bend by the hill to past us); red, big rear wheels turning, smoke from its pipe.
     */
    private fun tractor(d: Float) {
        val z = z
        val p = pm(d); val x = gx(road(d) + 0.2f, d); val by = gy(d)
        // the trailer behind it, farther up the road, with its six crates in two rows (Tine counts them)
        val td = d + 2.6f; val tp = pm(td); val tx = gx(road(td) + 0.2f, td); val tby = gy(td)
        c.fillRect((tx - 0.7f * tp).toInt(), (tby - 0.9f * tp).toInt(), (1.4f * tp).toInt().coerceAtLeast(2), (0.45f * tp).toInt().coerceAtLeast(1), Pal.WOOD_M)
        for (row in 0 until 2) for (k in 0 until 3) {
            val cx = (tx - 0.62f * tp + k * 0.44f * tp).toInt(); val cy = (tby - (1.2f + 0.28f * row) * tp).toInt()
            val cw = (0.38f * tp).toInt().coerceAtLeast(1); val ch = (0.26f * tp).toInt().coerceAtLeast(1)
            c.fillRect(cx, cy, cw, ch, Col.hex(0xC8985A))
            c.fillRect(cx, cy, cw, max(1, ch / 3), if ((k + row) % 2 == 0) Col.hex(0x4A2A52) else Col.hex(0xC0B044))
        }
        // rear wheels either side, turning
        val spin = ((t * 6).toInt() and 1)
        for (side in floatArrayOf(-0.62f, 0.62f)) {
            val wx = x + side * p; val wy = by - 0.5f * p
            c.fillEllipse(wx, wy, 0.2f * p, 0.5f * p, Col.hex(0x22201E))
            if (p > 6f) c.hline((wx - 0.12f * p).toInt(), (wx + 0.12f * p).toInt(), (wy + (if (spin == 0) -0.2f else 0.2f) * p).toInt(), Col.hex(0x46423C))
        }
        // the body: the hood toward us, red, a grille, lamps
        val red = Col.hex(0xC8352A); val redL = Col.hex(0xE8604A); val redD = Col.hex(0x8A2018)
        c.fillRect((x - 0.42f * p).toInt(), (by - 1.25f * p).toInt(), (0.84f * p).toInt().coerceAtLeast(2), (0.85f * p).toInt().coerceAtLeast(2), red)
        c.hline((x - 0.42f * p).toInt(), (x + 0.42f * p).toInt(), (by - 1.25f * p).toInt(), redL)
        c.vline((x + 0.42f * p).toInt(), (by - 1.25f * p).toInt(), (by - 0.4f * p).toInt(), redD)
        c.fillRect((x - 0.25f * p).toInt(), (by - 0.95f * p).toInt(), (0.5f * p).toInt().coerceAtLeast(1), (0.4f * p).toInt().coerceAtLeast(1), Col.hex(0x2A2226))
        val lamp = if (env.windows > 0.3f) Pal.WINDOW_LIT_HI else Col.hex(0xF0E8C8)
        c.set((x - 0.32f * p).toInt(), (by - 1.1f * p).toInt(), lamp); c.set((x + 0.32f * p).toInt(), (by - 1.1f * p).toInt(), lamp)
        if (env.windows > 0.3f) s.light(x, by - 1.1f * p, 26f, 0.8f)
        // front wheels, small
        for (side in floatArrayOf(-0.38f, 0.38f)) c.fillEllipse(x + side * p, by - 0.22f * p, 0.12f * p, 0.22f * p, Col.hex(0x22201E))
        // the driver's seat and the steering wheel behind the hood, the exhaust pipe with its smoke
        c.fillRect((x - 0.2f * p).toInt(), (by - 1.7f * p).toInt(), (0.4f * p).toInt().coerceAtLeast(1), (0.45f * p).toInt().coerceAtLeast(1), Col.hex(0x3A3A3E))
        c.vline((x + 0.3f * p).toInt(), (by - 1.9f * p).toInt(), (by - 1.25f * p).toInt(), Col.hex(0x3A3A3E))
        s.smoke(x + 0.3f * p, by - 1.95f * p, 0.4f, 7, dark = true)
    }

    /**
     * Starlings ([fx] "starlings": how far they have flown): a flock rising off the vines all at once, wheeling in a
     * cloud that swells and thins, and away over the hills.
     */
    private fun starlings(level: Float) {
        if (level <= 0.01f || level >= 0.995f) return
        val z = z
        val col = env.lit(Col.hex(0x26242A))
        val e = level * level * (3f - 2f * level)
        // the flock's middle: up from the rows on the left, a loop, then off to the upper right (scene canvas px)
        val cx = 60f + 190f * e + sin(e * 6.28f) * 30f
        val cy = (horizon + 30f) - 95f * e + cos(e * 6.28f) * 12f
        val spread = 6f + 16f * sin(e * 3.14f)
        for (j in 0 until 40) {
            val a = Noise.rnd(j, 321) * 6.28f + t.toFloat() * (0.8f + Noise.rnd(j, 322))
            val r = spread * kotlin.math.sqrt(Noise.rnd(j, 323)) * (0.8f + 0.2f * sin(t.toFloat() * 2f + j))
            val bx = ox + ((cx + cos(a) * r * 1.6f) * z).toInt(); val by = oy + ((cy + sin(a) * r * 0.8f) * z).toInt()
            val up = ((t * 12).toInt() + j) and 1
            sprite(bx, by) {
                c.set(bx, by, col)
                if (up == 0) { c.set(bx - 1, by - 1, col); c.set(bx + 1, by - 1, col) } else { c.set(bx - 1, by, col); c.set(bx + 1, by, col) }
            }
        }
    }

    // ------------------------------------------------------------------ weather

    /** Rain veils the far hills first: grey shafts hanging over them, thickest at the horizon; then the drops close by. */
    override fun weather() {
        val rain = frame.sky.rain
        if (rain > 0.02f) {
            val z = z
            val grey = env.lit(Col.hex(0x9AA2AE))
            for (py in max(0, c.top) until min(hz + 30 * z, c.bottom)) {
                val row = py / z.toFloat()
                val prof = if (py < hz) exp((row - hz / z.toFloat()) / 24f) else 1f - (py - hz) / (30f * z)
                if (prof <= 0.02f) continue
                for (px in max(0, c.left) until min(w, c.right)) {
                    val shaft = 0.55f + 0.45f * Noise.v1((px - ox) / z * 0.07f - row * 0.02f + t.toFloat() * 0.12f, 331)
                    val a = rain * 0.6f * prof * shaft
                    if (a > Dither.at(px, py) * 0.5f) c.pixels[c.index(px, py)] = Col.mix(c.pixels[c.index(px, py)], grey, min(0.75f, a))
                }
            }
        }
        super.weather()
    }
}
