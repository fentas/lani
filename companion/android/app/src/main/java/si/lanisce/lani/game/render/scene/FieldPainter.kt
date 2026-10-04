package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.Season
import si.lanisce.lani.game.scene.Poke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Na njivi: the fields below the village, seen from a rise. The cart track runs from our feet to the hills
 * and bends away; the kozolec (the hayrack) stands on the left with hay drying in its rails and a rake against
 * its post, the cart loaded with hay waits on the track with the horse grazing in its shafts. Potato rows on
 * the left, cabbage rows on the right behind the fence with a scythe leaning on it and a basket among them,
 * the wheat beyond with the scarecrow, the corn behind the kozolec; then a patchwork of fields, hedges and
 * orchards up to the village on the hill with its church. The harvest follows the seasons: green shoots in
 * spring, gold in summer, stubble and dug rows in autumn, snow in winter; the day goes golden at evening.
 *
 * Closer up ([detail] 2 and 3) the same view is drawn finer: flowers in the grass, pebbles in the ruts, ears on
 * the wheat and arching leaves, tassels and cobs on the corn, grain and nails on the fence, shingles on the
 * kozolec, strands in the hay, spokes, felloes and iron tyres on the cart's wheels, the horse's mane and
 * harness, and the far village with windows, tiled roofs and the church's clock, belfry and cross.
 *
 * Tapped ([pokes]), the scarecrow's crow flaps up off its arm and drops back; tapped again, it and the crows a dialog
 * brought fly off, wheel over the field and settle back.
 *
 * Its wild animals ([WildPainter]): a hare in the potatoes, butterflies over the cabbages by day; a roe deer grazing at
 * the wheat's edge and a fox by the kozolec at dawn and dusk; the wild boar and her piglets digging up the potatoes at
 * night, bats over the field. Tapped,
 * the hare hops off and back, the piglets scatter. Dew glints on the grass at dawn; the evening bell (the ave) rings from
 * the church on the hill round sunset, its rings coming over the fields.
 */
internal class FieldPainter : WildPainter() {
    override val art = "field"
    override val pokes = listOf(Poke("scarecrow", listOf(1.6, 6.8), rest = 3.0), Poke("hare", listOf(2.4), rest = 3.0), Poke("boar", listOf(2.8), rest = 3.0))

    // the plan, in metres: the track's centre, the fence beside it, where each field lies (side x, depth d)
    private fun track(d: Float) = 0.5f - max(0f, d - 22f) * 0.07f
    private val trackHalf = 1.3f
    private val fenceX = 2.7f
    private val cabbages = 3.4f to 13.2f      // x from 3.4 m, depth from near to 13.2 m
    private val wheat = 13.6f to 34f
    private val potatoes = 12.3f             // depth up to, left of the track
    private val meadow = 19f                 // the kozolec's strip ends here; the corn begins
    private val corn = 19f to 36f
    private val kozolecD = 16f; private val kozolecA = -9.2f; private val kozolecB = -3.6f
    private val cartD = 18.5f

    private companion object {
        /** The village project this field shows as it's built: the double hayrack. */
        const val TOPLAR = "toplar"
    }

    private val items = ArrayList<Pair<Float, () -> Unit>>()

    /** Fruit trees about the fields: side, depth, height (m). */
    private val orchard = listOf(
        Triple(-17f, 44f, 6f), Triple(-13f, 47f, 5.5f), Triple(-21f, 49f, 6f), Triple(14f, 46f, 5.5f), Triple(18f, 50f, 6f),
        Triple(22f, 60f, 6.5f), Triple(-28f, 34f, 6.5f), Triple(12.5f, 27f, 7f),
    )

    override fun paint() {
        setup()
        val season = env.season
        val k = detail
        vistaSky(5, 13, moonX = ox + 196 * k, moonY = oy + 12 * k)
        farHills()
        ground { x, d, px, py -> land(x, d, px, py) }
        nearHills()

        // the track, then everything standing, far to near
        thing("path") { trackPixels() }
        items.clear()
        for ((x, d, hgt) in orchard) items.add(d to { prop(Col.hex(0x1E3A22)) { leafyTree(x, d, hgt, (x * 7 + d).toInt(), blossom = true, fruit = Col.hex(0xD83A2E)) } })
        items.add(40f to { thing("fence") { fenceRun(24f, 40f) } })
        cornPlants()
        items.add(wheat.first to { thing("wheat", outline = Col.hex(0x6A5220), slop = 1) { wheatEdge() } })
        items.add(17f to { thing("scarecrow", slop = 1) { scarecrow(6.4f, 17f) } })
        items.add(16.2f to { prop(0) { crows(fxOn("crows")) } })
        // the kozolec and its hay once the village has built one (KOZOLEC); without it the rake leans on the fence
        val kozolec = there("kozolec")
        if (kozolec) items.add(kozolecD to {
            thing("kozolec") { kozolec(kozolecA, kozolecB, kozolecD) }
            if (there("hay")) thing("hay") { hay(kozolecA, kozolecB, kozolecD) }
        })
        if (kozolec) items.add(kozolecD - 0.3f to { thing("rake", slop = 1) { rake(kozolecB + 0.35f, kozolecD - 0.3f) } })
        else items.add(12.6f to { thing("rake", slop = 1) { rake(fenceX + 0.4f, 12.6f) } })
        // the double hayrack (the project "toplar") beyond the wheat, as far as it's built
        toplar()
        items.add(cartD to {
            thing("horse", slop = 1) { horse(-2.6f, cartD + 0.2f) }
            if (k > 1) prop(0) { grazeTuft(-2.6f, cartD + 0.2f) }
        })
        items.add(cartD - 0.1f to { thing("cart") { cart(-1.2f, 2.1f, cartD) } })
        items.add(24f to { thing("fence") { fenceRun(13f, 24f) } })
        items.add(13f to { thing("fence") { fenceRun(3.5f, 13f) } })
        items.add(11.2f to { thing("scythe", slop = 1) { scythe(fenceX + 0.1f, 11.2f) } })
        potatoPlants()
        cabbagePlants()
        items.add(10.6f to { thing("basket", slop = 1) { basket(4.2f, 10.6f) } })
        for (p in peopleAt("kozolec")) items.add(12.2f to { groundShadow(-5.4f, 12.2f, 0.5f); person(p, gx(-5.4f, 12.2f).toInt(), gy(12.2f).toInt(), flip = false) })
        for (p in peopleAt("field")) items.add(11.3f to { groundShadow(4.6f, 11.3f, 0.5f); person(p, gx(4.6f, 11.3f).toInt(), gy(11.3f).toInt(), flip = true) })
        for (p in peopleAt("path")) items.add(10.6f to { groundShadow(track(10.6f) - 0.2f, 10.6f, 0.5f); person(p, gx(track(10.6f) - 0.2f, 10.6f).toInt(), gy(10.6f).toInt(), flip = false) })
        wildlife()
        items.sortByDescending { it.first }
        for ((_, draw) in items) draw()

        crow()
        val nBats = batCount(fx("bats"), out("bat"))
        if (nBats > 0) creature("bat", slop = 3) { batsOver(ox + 20f * k, hz - 46f * k, ox + 200f * k, hz - 12f * k, nBats, 443, 1.1f) }
        farBell(belfryX, belfryY, fx("bell") ?: if (aveNow()) 1f else 0f, 5)
        dew(hz + 14 * k, h, 91)
        foregroundGrass(0, (w * 0.12f).toInt(), 5)
        s.fx { s.effects.leaves(season, env.month) }
        fireflies(0, hz + 10 * k, w, h, 14, 43)
    }

    // ------------------------------------------------------------------ helpers

    /** A line [th] px thick, stacked across its steeper direction. */
    private fun thick(x0: Float, y0: Float, x1: Float, y1: Float, th: Int, col: Int) {
        if (th <= 1) { c.line(x0.toInt(), y0.toInt(), x1.toInt(), y1.toInt(), col); return }
        val steep = abs(y1 - y0) > abs(x1 - x0)
        for (i in 0 until th) {
            val o = i - th / 2
            if (steep) c.line(x0.toInt() + o, y0.toInt(), x1.toInt() + o, y1.toInt(), col)
            else c.line(x0.toInt(), y0.toInt() + o, x1.toInt(), y1.toInt() + o, col)
        }
    }

    /** [col] far off, in the haze and the light of the hour (for the emissive far things). */
    private fun far(col: Int, haze: Float): Int = env.lit(Col.mix(col, env.skyHorizon, haze))

    // ------------------------------------------------------------------ far away

    /** The far ridges: the high plateau in the haze, snow on its tops in winter and spring, then the wooded hills. */
    private fun farHills() {
        val season = env.season
        val k = detail
        val farCol = if (season == Season.WINTER) Col.hex(0xB0BED6) else Col.hex(0x6474A0)
        ridge(hz - 12f * k, 26f * k, 0.011f, 51, farCol, 0.32f, snowLine = 0.5f, sharp = true)
        val midCol = when (season) { Season.WINTER -> Col.hex(0xC6D2E2); Season.AUTUMN -> Col.hex(0x7A7A44); else -> Col.hex(0x4E7A48) }
        val mid = ridge(hz - 8f * k, 12f * k, 0.014f, 53, midCol, 0.2f) { x, y, below -> woods(x, y, below, midCol) }
        treeline(mid, 6 * k, 2.2f * k, if (season == Season.WINTER) Col.hex(0x6A7A8A) else Col.hex(0x2E5A36), 0.28f, 57)
    }

    /** The near hills over the far edge of the plain: fields on their slopes, hedges, farmsteads, the village on top. */
    private fun nearHills() {
        val season = env.season
        val k = detail
        val nearCol = when (season) { Season.WINTER -> Col.hex(0xDCE6F2); Season.AUTUMN -> Col.hex(0x9A9448); else -> Col.hex(0x6A9A4E) }
        val near = ridge(hz + 2f * k, 11f * k, 0.02f, 59, nearCol, 0.08f, bottom = hz + 9 * k) { x, y, below -> slopeFields(x, y, below) }
        treeline(near, 5 * k, 2.8f * k, if (season == Season.WINTER) Col.hex(0x5A6A5A) else Col.hex(0x2E5A2E), 0.1f, 61, from = 0, to = (vx - 10 * k).toInt())
        treeline(near, 6 * k, 2.6f * k, if (season == Season.WINTER) Col.hex(0x5A6A5A) else Col.hex(0x2E5A2E), 0.1f, 62, from = (vx + 90 * k).toInt(), to = w)
        // the village where the hill is highest, right of the track's end
        var top = (vx + 30 * k).toInt().coerceIn(0, w - 1)
        for (x in (vx + 10 * k).toInt()..(vx + 70 * k).toInt()) if (x in 0 until w && near[x] < near[top]) top = x
        village(near, top)
        // farmsteads here and there on the slopes
        for (i in 0 until 5) {
            val fx = (ox + Noise.rnd(i, 63) * STAGE_W * k).toInt()
            if (fx !in 4 * k until w - 6 * k || abs(fx - top) < 40 * k) continue
            farmstead(fx, near[fx] + (3 + (Noise.rnd(i, 64) * 3).toInt()) * k, i)
        }
    }

    /** A forest canopy for the wooded hills: dark crowns with lit tops, a clearing now and then; single crowns closer up. */
    private fun woods(x: Int, y: Int, below: Int, meadowCol: Int): Int {
        val season = env.season
        val k = detail
        // the scene canvas's point: the same woods at every detail
        val sx = (x - ox) / k.toFloat(); val sy = y / k.toFloat()
        val clearing = Noise.v2(sx * 0.03f, sy * 0.09f, 83) > 0.66f
        if (clearing) return Col.mix(meadowCol, Col.hex(0xFFF0C8), 0.12f)
        var n = Noise.v2(sx * 0.3f, sy * 0.45f, 81)
        if (k > 1) n += (Noise.v2(x * 0.42f, y * 0.62f, 82) - 0.5f) * 0.34f
        val (d, m, l) = when (season) {
            Season.WINTER -> Triple(Col.hex(0x7A8898), Col.hex(0xA8B6C6), Col.hex(0xD8E2EE))
            Season.AUTUMN -> Triple(Col.hex(0x5A4A2A), Col.hex(0x8A6A34), Col.hex(0xB08A44))
            else -> Triple(Col.hex(0x264A2E), Col.hex(0x345E38), Col.hex(0x4A7A44))
        }
        return if (n > 0.64f) l else if (n > 0.36f + (Dither.at(x, y) - 0.5f) * 0.2f) m else d
    }

    /** Fields on the near slopes: strips following the hill, hedges between them, by the season. */
    private fun slopeFields(x: Int, y: Int, below: Int): Int {
        val season = env.season
        val k = detail
        val sx = (x - ox) / k.toFloat(); val sy = y / k.toFloat(); val sb = below / k.toFloat()
        val v = sb / 4.5f + Noise.v1(sx * 0.04f, 71) * 1.2f
        val u = (sx + sb * 1.6f) / 28f + Noise.v1(sy * 0.13f, 73) * 0.25f
        val i = floor(u).toInt(); val j = floor(v).toInt()
        var hedge = if (season == Season.WINTER) Col.hex(0x9AA4B2) else Col.hex(0x3A6A3A)
        if (v - j < 0.2f || (u - i) * 28f < 1f) {
            // closer up the hedges are bushes: lit tops here and there
            if (k > 1 && season != Season.WINTER && Noise.rnd(x / 2, y / 2, 74) < 0.3f) hedge = Col.hex(0x4E844A)
            return if (season == Season.WINTER && Dither.at(x, y) < 0.5f) Pal.SNOW_M else hedge
        }
        val kind = Math.floorMod(Noise.hash(i, j, 75), 6)
        val dither = Dither.at(x, y) < 0.5f
        return when (season) {
            Season.WINTER -> if ((below / k) % 9 < 2 && dither) Pal.SNOW_M else Pal.SNOW_L
            Season.SPRING -> when (kind) { 0 -> Col.hex(0xE8D84A); 1, 4 -> env.grass[if (dither) 0 else 1]; 2 -> Col.hex(0x7AB452); 3 -> Pal.SOIL_M; else -> Col.hex(0x92C466) }
            Season.SUMMER -> when (kind) { 0, 3 -> if (dither) Pal.HAY_L else Pal.HAY_M; 1, 4 -> env.grass[if (dither) 0 else 1]; 2 -> Col.hex(0x3E7E36); else -> Col.hex(0x6AA64E) }
            Season.AUTUMN -> when (kind) { 0 -> Pal.SOIL_M; 1, 4 -> env.grass[if (dither) 0 else 1]; 2 -> Col.hex(0xB8A060); 3 -> Pal.SOIL_L; else -> Col.hex(0xA89A58) }
        }
    }

    /** A farmstead far off: a white house, a red roof, a barn beside it, a lit window at night. */
    private fun farmstead(x: Int, base: Int, seed: Int) {
        val wall = env.lit(Col.mix(Pal.WALL_L, env.skyHorizon, 0.12f)); val roof = env.lit(Col.mix(if (env.snow) Pal.SNOW_M else Pal.ROOF_M, env.skyHorizon, 0.1f))
        val barn = env.lit(Col.mix(Pal.WOOD_M, env.skyHorizon, 0.12f))
        c.penEmissive = true
        val k = detail
        if (k == 1) {
            c.fillRect(x, base - 3, 4, 3, wall); c.hline(x - 1, x + 4, base - 4, roof); c.hline(x, x + 3, base - 5, roof)
            if (seed % 2 == 0) { c.fillRect(x + 5, base - 2, 3, 2, barn); c.hline(x + 5, x + 7, base - 3, roof) }
            c.set(x + 1, base - 2, if (env.windows > 0.35f) Pal.WINDOW_LIT else env.lit(Pal.GLASS))
        } else {
            val roofD = far(if (env.snow) Pal.SNOW_D else Pal.ROOF_D, 0.1f)
            val wallD = far(Pal.WALL_M, 0.12f)
            val lit = env.windows > 0.35f
            if (seed % 2 == 0) {
                // the barn: boards, a dark door, its own low roof
                val bx = x + 5 * k
                c.fillRect(bx, base - 2 * k, 3 * k, 2 * k, barn)
                val board = far(Pal.WOOD_D, 0.12f)
                for (xx in bx + 1 until bx + 3 * k step 2) c.vline(xx, base - 2 * k + 1, base - 1, board)
                c.fillRect(bx + k, base - k - k / 2, k, k + k / 2, far(Pal.WOOD_X, 0.12f))
                for (j in 0 until k) c.hline(bx - 1 + j, bx + 3 * k - j, base - 2 * k - 1 - j, if (j == 0) roofD else roof)
            }
            c.fillRect(x, base - 3 * k, 4 * k, 3 * k, wall)
            c.vline(x + 4 * k - 1, base - 3 * k, base - 1, wallD)
            farRoof(x - k, x + 5 * k - 1, base - 3 * k - 1, 2 * k, roof, roofD)
            farWindow(x + k, base - 2 * k, k, k, lit)
            // the door
            c.fillRect(x + 2 * k + k / 2, base - 2 * k + 1, max(2, k - 1), 2 * k - 1, far(Pal.DOOR, 0.12f))
        }
        c.penEmissive = false
    }

    /** A roof far off from the eave row [y] up [rows] rows, from [xl] to [xr] at the eave, 45° slopes, courses of tiles. */
    private fun farRoof(xl: Int, xr: Int, y: Int, rows: Int, roof: Int, roofD: Int) {
        val course = Col.mix(roof, roofD, 0.45f)
        val ridgeCol = Col.mix(roof, Col.hex(0xFFF0C8), 0.12f)
        for (j in 0 until rows) {
            val a = xl + j; val b = xr - j
            if (a > b) break
            val col = when {
                j == 0 -> roofD
                j == rows - 1 -> ridgeCol
                j % 2 == 0 -> course
                else -> roof
            }
            c.hline(a, b, y - j, col)
            if (col == roof && !env.snow) { var xx = a + ((j / 2) and 1) * 2; while (xx <= b) { c.set(xx, y - j, course); xx += 4 } }
        }
    }

    /** A window a [ww] × [hh] block far off: glass with a glint by day, lit at night, a sill under it. */
    private fun farWindow(x: Int, y: Int, ww: Int, hh: Int, lit: Boolean) {
        c.fillRect(x, y, ww, hh, if (lit) Pal.WINDOW_LIT else env.lit(Pal.GLASS))
        c.set(x, y, if (lit) Pal.WINDOW_LIT_HI else env.lit(Pal.GLASS_HI))
        if (ww >= 3 && hh >= 3) c.vline(x + ww / 2, y, y + hh - 1, far(Pal.WALL_D, 0.15f))
    }

    /** An arched window or door far off: the top row narrower. */
    private fun farArch(x: Int, y: Int, ww: Int, hh: Int, col: Int) {
        if (ww >= 3) { c.fillRect(x, y + 1, ww, hh - 1, col); c.hline(x + 1, x + ww - 2, y, col) } else c.fillRect(x, y, ww, hh, col)
    }

    /** The village on the near hill: the church with its bell tower in the middle, houses about it, lit windows at night. */
    private fun village(crest: IntArray, cx: Int) {
        val wall = env.lit(Col.mix(Pal.WALL_L, env.skyHorizon, 0.18f)); val wallD = env.lit(Col.mix(Pal.WALL_D, env.skyHorizon, 0.18f))
        val roof = env.lit(Col.mix(if (env.snow) Pal.SNOW_M else Pal.ROOF_M, env.skyHorizon, 0.15f))
        val roofD = env.lit(Col.mix(if (env.snow) Pal.SNOW_D else Pal.ROOF_D, env.skyHorizon, 0.15f))
        val spire = env.lit(Col.mix(Pal.SLATE_M, env.skyHorizon, 0.15f))
        val lit = env.windows > 0.35f
        val k = detail
        c.penEmissive = true
        val base = crest[cx.coerceIn(0, w - 1)] + 3 * k
        // houses either side, smaller away from the church
        for ((i, off) in intArrayOf(-26, -17, -9, 11, 19, 28).withIndex()) {
            val hx = cx + off * k; if (hx !in 2 * k until w - 8 * k) continue
            val hw = (6 + (i % 2)) * k; val hh = 4 * k
            val hb = min(base + k, crest[hx.coerceIn(0, w - 1)] + 4 * k)
            if (k == 1) {
                c.fillRect(hx, hb - hh, hw, hh, wall); c.vline(hx + hw - 1, hb - hh, hb - 1, wallD)
                for (r in 0..2) c.hline(hx - 1 + r, hx + hw - r, hb - hh - 1 - r, if (r == 0) roofD else roof)
                if (lit) c.set(hx + 2, hb - 2, Pal.WINDOW_LIT) else c.set(hx + 2, hb - 2, env.lit(Pal.GLASS))
            } else {
                c.fillRect(hx, hb - hh, hw, hh, wall)
                c.fillRect(hx + hw - k, hb - hh, k, hh, wallD)
                c.hline(hx, hx + hw - k - 1, hb - 1, far(Pal.STONE_M, 0.18f))
                farRoof(hx - k, hx + hw + k - 1, hb - hh - 1, 3 * k, roof, roofD)
                // a chimney where the smoke rises
                val chx = hx + hw - 2 * k - k / 2
                c.fillRect(chx, hb - hh - 3 * k - k / 2, k, k + k / 2, far(Pal.WALL_M, 0.18f))
                c.hline(chx, chx + k - 1, hb - hh - 3 * k - k / 2, far(Pal.STONE_X, 0.18f))
                farWindow(hx + 2 * k, hb - 2 * k, k, k, lit)
                farWindow(hx + 4 * k, hb - 2 * k, k, k, lit && i % 2 == 0)
            }
            if (i % 3 == 0 && env.dark < 0.8f) s.smoke(hx + hw - 2f * k, hb - hh - 3f * k, 0.35f, i + 5)
        }
        if (k == 1) {
            // the church: the nave, then the tower with its spire and a clock face
            c.fillRect(cx - 4, base - 7, 12, 7, wall); c.vline(cx + 7, base - 7, base - 1, wallD)
            for (r in 0..3) c.hline(cx - 5 + r, cx + 8 - r, base - 8 - r, if (r == 0) roofD else roof)
            c.fillRect(cx - 8, base - 16, 5, 16, wall); c.vline(cx - 4, base - 16, base - 1, wallD)
            for (r in 0..6) c.hline(cx - 8 + r / 2, cx - 4 - r / 2, base - 17 - r, spire)
            c.set(cx - 6, base - 25, Pal.GOLD)
            c.set(cx - 6, base - 13, Pal.GOLD_L); c.set(cx - 6, base - 9, if (lit) Pal.WINDOW_LIT else env.lit(Pal.GLASS))
            belfryX = cx - 5.5f; belfryY = base - 11f
            if (lit) { c.set(cx + 1, base - 4, Pal.WINDOW_LIT); c.set(cx + 4, base - 4, Pal.WINDOW_LIT) }
        } else {
            farChurch(cx, base, wall, wallD, roof, roofD, spire, lit)
            belfryX = cx - 5.5f * k; belfryY = base - 11f * k
        }
        c.penEmissive = false
    }

    /** Where the church's bell hangs on the hill (picture px), for the evening bell's rings. */
    private var belfryX = 0f
    private var belfryY = 0f

    // ------------------------------------------------------------------ the wild animals

    /** The field's wild animals of the frame ([out], or the bats a dialog's cue brings). */
    private fun wildlife() {
        val k = detail
        if (out("deer")) {
            val u = spot("deer")
            // at the near edge of the wheat, right of the cabbages
            val x = 6.9f + u * 1.2f
            val d = 14.6f + u
            // drawn in front of the wheat's edge, over the stalks it stands in
            items.add(13.5f to { groundShadow(x, d, 0.9f); creature("deer") { roe(gx(x, d), gy(d), 36f / d, left = true, graze = sin(t * 0.5 + u * 5) > -0.3, buck = u > 0.7f) } })
        }
        if (out("fox")) {
            val u = spot("fox")
            // by the kozolec's foot, between it and the potatoes
            val x = -8.2f + u * 1.4f; val d = 13.4f
            items.add(d to { creature("fox") { fox(gx(x, d), gy(d), 30f / d, left = false, sit = true) } })
        }
        if (out("hare")) {
            val u = spot("hare")
            val hx = -7.2f + u * 1.2f; val hd = 11.4f + u * 0.6f
            val pk = poked("hare")
            val a = pk?.age(t) ?: 0f
            val q = if (pk != null && a < 2.4f) a / 2.4f else 0f
            val off = -sin(q * Math.PI.toFloat()) * 2.2f
            val hop = if (q > 0f) ((q * 6f) % 1f) else 0f
            items.add(hd to { groundShadow(hx + off, hd, 0.3f); creature("hare") { hare(gx(hx + off, hd), gy(hd), 26f / hd, left = q < 0.5f, hop = hop) } })
        }
        if (out("butterfly")) for ((j, at) in listOf(5.6f to 10.4f, -5f to 10.2f).withIndex()) {
            val (bx, bd) = at
            items.add(bd - 0.4f to {
                creature("butterfly", slop = 3) {
                    butterfly(gx(bx, bd) + sin(t * 0.8 + j * 2).toFloat() * 8f * k, gy(bd) - (15f + 5f * sin(t * 1.2 + j).toFloat()) * k, 1.6f, j + (spot("butterfly") * 3).toInt())
                }
            })
        }
        if (out("boar")) {
            val u = spot("boar")
            // digging in the potatoes
            val bx = -4.2f + u * 1.4f; val bd = 9.4f + u
            val pk = poked("boar")
            val a = pk?.age(t) ?: 0f
            // tapped: the piglets scatter and come running back to her
            val scatter = if (pk != null && a < 2.8f) PokeArt.ease(a, 0f, 0.6f) * (1f - PokeArt.ease(a, 1.6f, 2.8f)) else 0f
            items.add(bd to { groundShadow(bx, bd, 1f); creature("boar") { boars(gx(bx, bd), gy(bd), 22f / bd, left = u > 0.5f, piglets = 3, scatter = scatter) } })
        }
    }

    /** The church far off, closer up: the nave with its tiled roof and arched windows, the tower with the clock, the belfry and its bell, the spire, the gold cross. */
    private fun farChurch(cx: Int, base: Int, wall: Int, wallD: Int, roof: Int, roofD: Int, spire: Int, lit: Boolean) {
        val k = detail
        val glass = if (lit) Pal.WINDOW_LIT else env.lit(Pal.GLASS)
        val stone = far(Pal.STONE_M, 0.18f)
        // the nave
        c.fillRect(cx - 4 * k, base - 7 * k, 12 * k, 7 * k, wall)
        c.fillRect(cx + 7 * k, base - 7 * k, k, 7 * k, wallD)
        c.hline(cx - 4 * k, cx + 8 * k - 1, base - 1, stone)
        farRoof(cx - 5 * k, cx + 9 * k - 1, base - 7 * k - 1, 4 * k, roof, roofD)
        for (wxs in intArrayOf(1, 4)) farArch(cx + wxs * k, base - 5 * k + 1, k, 2 * k - 1, glass)
        // the tower: a string course, the belfry with its bell, the clock, a window, the door
        val tl = cx - 8 * k; val tw = 5 * k
        val mid = tl + tw / 2f
        c.fillRect(tl, base - 16 * k, tw, 16 * k, wall)
        c.fillRect(cx - 4 * k, base - 16 * k, k, 16 * k, wallD)
        c.hline(tl, tl + tw - 1, base - 11 * k, wallD)
        c.hline(tl, tl + tw - 1, base - 16 * k, wallD)
        c.hline(tl, tl + tw - 1, base - 1, stone)
        val bell = (mid - k / 2f).toInt()
        farArch(bell, base - 15 * k + k / 2, k, k + k / 2, far(Col.hex(0x2A2226), 0.15f))
        c.set(bell + k / 2, base - 15 * k + k / 2 + 1, Pal.GOLD); if (k >= 3) c.set(bell + k / 2, base - 15 * k + k / 2 + 2, Pal.GOLD_L)
        val cy = base - 13 * k + k / 2f
        // the clock: a gold ring, its face, the hands
        c.fillCircle(mid, cy, 0.95f * k, Pal.GOLD)
        if (k >= 3) {
            c.fillCircle(mid, cy, 0.7f * k, if (lit) Pal.WINDOW_LIT else far(Col.hex(0xF7F5EE), 0.08f))
            c.set(mid.toInt(), cy.toInt() - 1, Pal.OUTLINE); c.set(mid.toInt(), cy.toInt(), Pal.OUTLINE); c.set(mid.toInt() + 1, cy.toInt(), Pal.OUTLINE)
        } else c.set(mid.toInt(), cy.toInt(), Pal.OUTLINE)
        farArch(bell, base - 10 * k + 1, k, 2 * k - 1, glass)
        farArch(bell, base - 2 * k, k, 2 * k, far(Pal.DOOR, 0.15f))
        // the spire: lit on the left, a gold ball and the cross on its tip
        val sb = base - 16 * k - 1
        val sh = 7 * k
        val spireD = Col.scale(spire, 0.78f)
        for (j in 0 until sh) {
            val half = tw / 2f * (1f - j / sh.toFloat()) + 0.5f
            c.hline((mid - half).toInt(), (mid + half).toInt() - 1, sb - j, spire)
            c.hline(mid.toInt(), (mid + half).toInt() - 1, sb - j, spireD)
        }
        val tip = sb - sh
        c.set(mid.toInt(), tip, Pal.GOLD)
        c.vline(mid.toInt(), tip - 2 * k, tip - 1, Pal.GOLD)
        c.hline(mid.toInt() - k / 2, mid.toInt() + k / 2, tip - 2 * k + k / 2, Pal.GOLD)
    }

    // ------------------------------------------------------------------ the land

    /** The colour of the ground at side [x], depth [d]: the fields near by, a patchwork of them far off. */
    private fun land(x: Float, d: Float, px: Int, py: Int): Int {
        val season = env.season
        val tx = track(d)
        val grassCol = meadowAt(x, d, px, py)
        if (abs(x - tx) < trackHalf + 0.4f) return grassCol
        return if (x > tx) {
            when {
                x < fenceX + 0.5f && d < 44f -> grassCol
                d < cabbages.second && x > cabbages.first -> soilRows(x - cabbages.first, d, 0.9f, px, py)
                d < cabbages.second -> grassCol
                d < wheat.second && x > 3.0f -> wheatAt(x, d, px, py)
                else -> patch(x, d, px, py)
            }
        } else {
            when {
                d < potatoes && x < tx - trackHalf - 0.3f -> soilRows(x, d, 0.8f, px, py, hilled = true)
                d < meadow -> grassCol
                d < corn.second && x < tx - trackHalf - 0.6f -> cornAt(x, d, px, py)
                else -> patch(x, d, px, py)
            }
        }.let { if (season == Season.WINTER) snowOver(it, x, d, px, py) else it }
    }

    private fun meadowAt(x: Float, d: Float, px: Int, py: Int): Int {
        if (env.season == Season.WINTER) return snowOver(0, x, d, px, py)
        val g = env.grass
        val k = detail
        val n = Noise.v2(x * 0.5f, d * 0.5f, 3) + (Dither.at(px, py) - 0.5f) * 0.25f
        var col = if (n > 0.62f) g[0] else if (n > 0.32f) g[1] else g[2]
        val r = Noise.rnd(px, py, 5)
        if (d < 25f && r < 0.04f) col = g[2]
        if (d < 25f && r > 0.985f) col = g[3]
        if (k > 1 && d < 18f) {
            // blades closer up: short upright strokes, dark in their shade, lit at their tips
            val b = Noise.rnd(px, (py + (px and 1)) / 3, 6)
            if (b < 0.07f) col = g[2] else if (b > 0.95f) col = g[0]
        }
        if (env.season != Season.WINTER && env.month in 4..8 && d < 30f) {
            if (k == 1) { if (Noise.rnd(px, py, 9) < 0.012f) col = Pal.PAINTED[(px * 7 + py) % 6] }
            else {
                // a flower where detail 1 has its dot: petals round a golden heart
                val fx = px / k; val fy = py / k
                if (Noise.rnd(fx, fy, 9) < 0.012f) {
                    val petal = Pal.PAINTED[(fx * 7 + fy) % 6]
                    val ix = px - fx * k; val iy = py - fy * k
                    col = if (k == 2) (if (ix == 0 && iy == 0) Col.mix(petal, Col.hex(0xFFFFFF), 0.4f) else if (ix == 1 && iy == 1) Pal.GOLD_L else petal)
                    else when { ix == 1 && iy == 1 -> Pal.GOLD_L; ix == 1 || iy == 1 -> petal; else -> col }
                }
            }
        }
        return hazy(col, d)
    }

    /** Ridges and furrows in rows [spacing] m apart; [hilled] raises them (potatoes). */
    private fun soilRows(x: Float, d: Float, spacing: Float, px: Int, py: Int, hilled: Boolean = false): Int {
        val ph = rowPhase(x, d, spacing)
        val r = Noise.rnd(px, py, 11)
        val base = if (r < 0.15f) Pal.SOIL_L else Pal.SOIL_M
        var col = when {
            ph < 0f -> if (Dither.at(px, py) < 0.4f) Pal.SOIL_D else Pal.SOIL_M
            ph < (if (hilled) 0.32f else 0.25f) -> Pal.SOIL_D
            hilled && ph > 0.55f && ph < 0.8f -> Pal.SOIL_L
            else -> base
        }
        // clods closer up: a lit lump, its shadow under it
        if (detail > 1 && ph >= 0f && spacing * pm(d) > 8f) {
            val cl = Noise.rnd(px / 2, py / 2, 12)
            if (cl < 0.05f) col = if ((py and 1) == 0) Pal.SOIL_L else Pal.SOIL_D
        }
        return hazy(col, d)
    }

    /** Wheat: gold in summer with the wind running over it, green in spring, stubble in autumn. */
    private fun wheatAt(x: Float, d: Float, px: Int, py: Int): Int {
        val season = env.season
        val stalk = Noise.rnd(px, 0, 7) < 0.22f && d < 26f
        val wind = sin(d * 0.55f - t.toFloat() * 1.3f + x * 0.25f) > 0.72f
        val n = Noise.v2(x * 0.7f, d * 0.4f, 17) + (Dither.at(px, py) - 0.5f) * 0.3f
        var col = when (season) {
            Season.SUMMER -> when {
                env.month == 8 && d > 24f -> if (n > 0.5f) Col.hex(0xD8C27A) else Col.hex(0xC0A868) // cut already, far away
                wind -> Pal.HAY_L
                stalk -> Pal.HAY_D
                n > 0.62f -> Pal.HAY_L
                n > 0.3f -> Pal.HAY_M
                else -> Col.mix(Pal.HAY_M, Pal.HAY_D, 0.5f)
            }
            Season.SPRING -> if (wind) Col.hex(0x9ACC66) else if (stalk || n < 0.35f) Col.hex(0x5E9A40) else Col.hex(0x7AB452)
            Season.AUTUMN -> if (bandPhase(d, 0.9f).let { it in 0f..0.2f }) Col.hex(0xA89060) else if (n > 0.5f) Col.hex(0xD0BC88) else Col.hex(0xC2AA74)
            Season.WINTER -> Pal.SNOW_L
        }
        // closer up the ears show over the field: grains in pairs, lit on top
        if (detail > 1 && season == Season.SUMMER && !(env.month == 8 && d > 24f) && d < 30f && Noise.rnd(px / 2, py / 3, 8) < 0.18f)
            col = if ((py % 3) == 0) Col.mix(Pal.HAY_L, Col.hex(0xFFF4D8), 0.3f) else if ((px and 1) == 0) Pal.HAY_L else Pal.HAY_M
        return hazy(col, d)
    }

    /** Corn seen from above: dark rows of leaves in summer, dry in autumn, shoots on soil in spring. */
    private fun cornAt(x: Float, d: Float, px: Int, py: Int): Int {
        val season = env.season
        val ph = rowPhase(x, d, 0.8f)
        val n = Noise.rnd(px, py, 19)
        val col = when (season) {
            Season.SPRING -> if (ph in 0f..0.2f && n < 0.6f) Col.hex(0x6AAA48) else Pal.SOIL_M
            Season.SUMMER -> if (n < 0.3f) Col.hex(0x2E6A2C) else if (n < 0.8f) Col.hex(0x3E7E36) else Col.hex(0x5A9A48)
            Season.AUTUMN -> if (n < 0.3f) Col.hex(0x9A8450) else if (n < 0.8f) Col.hex(0xB8A060) else Col.hex(0xD0B878)
            Season.WINTER -> Pal.SNOW_L
        }
        return hazy(col, d)
    }

    /** The patchwork beyond: fields of every kind in strips, hedges between them. */
    private fun patch(x: Float, d: Float, px: Int, py: Int): Int {
        val season = env.season
        val k = detail
        val u = (x * 0.9f + d * 0.3f) / 18f + Noise.v1(d * 0.02f, 23) * 0.6f
        val v = kotlin.math.ln(d / 20f) * 3.2f
        val i = floor(u).toInt(); val j = floor(v).toInt()
        // hedges along the strip edges, thin in the distance (closer up a little thicker, with lit bushes)
        val eu = (u - i) * 18f * pm(d) / 0.9f; val ev = (v - j) / 3.2f * d * F * EYE / (d * d)
        val hu = if (k == 1) 1.1f else 0.6f * k + 0.5f; val hv = if (k == 1) 1.0f else 0.5f * k + 0.5f
        if (eu < hu || ev < hv) {
            if (season == Season.WINTER) return hazy(if (Dither.at(px, py) < 0.5f) Pal.SNOW_M else Col.hex(0x9AA4B2), d)
            return hazy(if (k > 1 && Noise.rnd(px / 2, py / 2, 33) < 0.3f) Col.hex(0x4E844A) else Col.hex(0x3A6A3A), d)
        }
        val kind = Math.floorMod(Noise.hash(i, j, 29), 7)
        val n = Noise.rnd(px, py, 31) + (Dither.at(px, py) - 0.5f) * 0.2f
        val col = when (season) {
            Season.WINTER -> if (kind == 3 && n < 0.3f) Pal.SNOW_D else Pal.SNOW_L
            Season.SPRING -> when (kind) {
                0, 5 -> Col.hex(0xE8D84A)                                   // rapeseed in flower
                1, 4 -> env.grass[if (n < 0.5f) 0 else 1]
                2 -> Col.hex(0x7AB452)
                3 -> Pal.SOIL_M
                else -> Col.hex(0x92C466)
            }
            Season.SUMMER -> when (kind) {
                0, 5 -> if (n < 0.5f) Pal.HAY_L else Pal.HAY_M
                1, 4 -> env.grass[if (n < 0.5f) 0 else 1]
                2 -> Col.hex(0x3E7E36)
                3 -> Col.hex(0xD8C27A)
                else -> Col.hex(0x6AA64E)
            }
            Season.AUTUMN -> when (kind) {
                0, 5 -> Pal.SOIL_M
                1, 4 -> env.grass[if (n < 0.5f) 0 else 1]
                2 -> Col.hex(0xB8A060)
                3 -> Pal.SOIL_L
                else -> Col.hex(0xA89A58)
            }
        }
        return hazy(col, d)
    }

    private fun snowOver(col: Int, x: Float, d: Float, px: Int, py: Int): Int {
        val n = Noise.v2(x * 0.6f, d * 0.6f, 41) + (Dither.at(px, py) - 0.5f) * 0.2f
        return hazy(if (n < 0.25f) Pal.SNOW_M else Pal.SNOW_L, d)
    }

    // ------------------------------------------------------------------ the track

    /** The cart track, row by row down from the horizon: two ruts, grass on the crown, puddles of shade; pebbles closer up. */
    private fun trackPixels() {
        val snow = env.snow
        val k = detail
        val dirt = if (snow) intArrayOf(Pal.SNOW_L, Pal.SNOW_M, Col.mix(Pal.DIRT_D, Pal.SNOW_D, 0.6f)) else intArrayOf(Pal.DIRT_L, Pal.DIRT_M, Pal.DIRT_D)
        val g = env.grass
        for (py in max(hz + 1, c.top) until min(h, c.bottom)) {
            val d = depthAt(py)
            if (d > 120f) continue
            val cx = gx(track(d), d); val half = trackHalf * pm(d)
            val fade = ((d - 50f) / 70f).coerceIn(0f, 1f)
            for (px in max((cx - half - 1).toInt(), c.left)..min((cx + half + 1).toInt(), c.right - 1)) {
                if (px !in 0 until w) continue
                val off = (px + 0.5f - cx) / half
                val edge = abs(off) + (Noise.rnd(px, py, 43) - 0.5f) * 0.25f
                if (edge > 1f) continue
                val r = Noise.rnd(px, py, 45)
                if (fade > 0f && Dither.at(px, py) < fade) continue
                var col = if (r < 0.12f) dirt[0] else if (r > 0.9f || (edge > 0.86f && half > 4f)) dirt[2] else dirt[1]
                val rut = abs(abs(off) - 0.45f)
                if (rut < 0.12f && half > 3f) col = dirt[2]
                else if (k > 1 && rut < 0.19f && half > 4f * k) col = dirt[0] // the ruts' worn lips
                if (abs(off) < 0.12f && half > 5f && !snow && r < 0.7f) col = g[1]
                // pebbles: a lit top, a dark foot
                if (k > 1 && !snow && half > 5f * k && rut >= 0.12f && Noise.rnd(px shr 1, py shr 1, 44) < 0.03f)
                    col = if ((py and 1) == 0) (if ((px and 1) == 0) Pal.STONE_L else Pal.STONE_M) else Pal.STONE_D
                c.set(px, py, hazy(col, d))
            }
        }
    }

    // ------------------------------------------------------------------ standing things, scaled by distance

    /** Fence posts beside the track from depth [d0] to [d1], rails between them. */
    private fun fenceRun(d0: Float, d1: Float) {
        if (detail > 1) { fenceRunFine(d0, d1); return }
        var d = d1
        var prev: Pair<Float, Float>? = null
        while (d >= d0) {
            val p = pm(d)
            val x = gx(fenceX, d); val by = gy(d)
            val hh = 1.25f * p; val pw = max(1f, 0.14f * p)
            c.fillRect((x - pw / 2).toInt(), (by - hh).toInt(), max(1, pw.toInt()), hh.toInt() + 1, Pal.WOOD_M)
            if (pw >= 2f) c.vline((x + pw / 2).toInt() - 1, (by - hh).toInt(), by.toInt(), Pal.WOOD_D)
            c.set(x.toInt(), (by - hh).toInt() - 1, if (env.snow) Pal.SNOW_L else Pal.WOOD_L)
            prev?.let { (qx, qd) ->
                val qp = pm(qd); val qby = gy(qd)
                for ((k, hgt) in floatArrayOf(0.5f, 0.98f).withIndex()) {
                    c.line(qx.toInt(), (qby - hgt * qp).toInt(), x.toInt(), (by - hgt * p).toInt(), if (k == 1) Pal.WOOD_L else Pal.WOOD_M)
                    if (p > 12f) c.line(qx.toInt(), (qby - hgt * qp).toInt() + 1, x.toInt(), (by - hgt * p).toInt() + 1, Pal.WOOD_D)
                }
            }
            prev = x to d
            d -= 2.6f
        }
    }

    /** The fence closer up: posts with a lit side, the grain down them and a cut top; round rails nailed to them. */
    private fun fenceRunFine(d0: Float, d1: Float) {
        var d = d1
        var prev: FloatArray? = null
        val grain = Col.mix(Pal.WOOD_M, Pal.WOOD_D, 0.5f)
        while (d >= d0) {
            val p = pm(d)
            val x = gx(fenceX, d); val by = gy(d)
            val hh = 1.25f * p; val pw = max(1f, 0.14f * p)
            val x0 = (x - pw / 2).toInt(); val pwi = max(1, pw.toInt()); val top = (by - hh).toInt()
            c.fillRect(x0, top, pwi, hh.toInt() + 1, Pal.WOOD_M)
            if (pwi >= 3) {
                c.vline(x0, top, by.toInt(), Pal.WOOD_L)
                for (gc in x0 + 1 until x0 + pwi - 1) {
                    if (gc < c.left || gc >= c.right || Noise.rnd(gc, 71) > 0.4f) continue
                    for (yy in max(top + 1, c.top)..min(by.toInt(), c.bottom - 1)) if (Noise.rnd(gc, yy / 3, 72) < 0.55f) c.set(gc, yy, grain)
                }
            }
            if (pw >= 2f) c.vline((x + pw / 2).toInt() - 1, top, by.toInt(), Pal.WOOD_D)
            // the cut top: lit, snow on it in winter
            val cap = if (env.snow) max(1, (0.05f * p).toInt()) else 1
            for (i in 1..cap) c.hline(x0, x0 + pwi - 1, top - i, if (env.snow) Pal.SNOW_L else Pal.WOOD_L)
            prev?.let { q ->
                val qx = q[0]; val qby = q[1]; val qp = q[2]
                for ((i, hgt) in floatArrayOf(0.5f, 0.98f).withIndex()) {
                    val ya = qby - hgt * qp; val yb = by - hgt * p
                    val ta = max(1.2f, 0.085f * qp); val tb = max(1.2f, 0.085f * p)
                    val base = if (i == 1) Pal.WOOD_L else Pal.WOOD_M
                    val lite = if (i == 1) Col.mix(Pal.WOOD_L, Col.hex(0xFFF0C8), 0.35f) else Pal.WOOD_L
                    val dark = if (i == 1) Pal.WOOD_M else Pal.WOOD_D
                    val gr = Col.mix(base, Pal.WOOD_D, 0.3f)
                    c.polyBegin(); c.polyAdd(qx, ya - ta / 2); c.polyAdd(x, yb - tb / 2); c.polyAdd(x, yb + tb / 2); c.polyAdd(qx, ya + ta / 2)
                    c.polyFill { px, py ->
                        val f = ((px + 0.5f - qx) / (x - qx)).coerceIn(0f, 1f)
                        val th = ta + (tb - ta) * f
                        val v = (py + 0.5f - (ya + (yb - ya) * f - th / 2)) / th
                        when { v < 0.3f -> lite; v > 0.7f -> dark; Noise.rnd(px / 4, py, 73) < 0.2f -> gr; else -> base }
                    }
                    // a nail where it meets the post
                    if (tb >= 3f) { c.fillRect(x.toInt() - 1, yb.toInt() - 1, 2, 2, Col.hex(0x3A3A44)); c.set(x.toInt() - 1, yb.toInt() - 1, Col.hex(0x9A9AA4)) }
                }
            }
            prev = floatArrayOf(x, by, p)
            d -= 2.6f
        }
    }

    /** The front of the wheat: a wall of stalks with their ears, swaying. */
    private fun wheatEdge() {
        val season = env.season
        val k = detail
        val d = wheat.first
        val p = pm(d); val by = gy(d)
        val x0 = max(0, gx(3.0f, d).toInt()); val x1 = w
        val hh = when (season) { Season.SUMMER -> if (env.month == 8) 0.25f else 1.05f; Season.SPRING -> 0.55f; Season.AUTUMN -> 0.2f; Season.WINTER -> 0.22f } * p
        val lean = (0.2f * p).toInt() + 2
        for (x in max(x0, c.left - lean) until min(x1, c.right + lean)) {
            val r = Noise.rnd(x, 0, 47)
            val sway = (sin(t * 1.4 + x * 0.12 / k) * (hh / 10f)).toFloat() * gust
            val top = by - hh * (0.85f + r * 0.25f)
            val col = when (season) {
                Season.SUMMER -> if (r < 0.3f) Pal.HAY_D else if (r < 0.8f) Pal.HAY_M else Pal.HAY_L
                Season.SPRING -> if (r < 0.4f) Col.hex(0x4E8A36) else Col.hex(0x7AB452)
                Season.AUTUMN -> Col.hex(0xB8A070)
                Season.WINTER -> Pal.SNOW_M
            }
            c.line(x, by.toInt(), (x + sway).toInt(), top.toInt(), col)
            if (season == Season.SUMMER && env.month != 8) {
                if (k == 1) { c.set((x + sway).toInt(), top.toInt() - 1, Pal.HAY_L); if (r > 0.6f) c.set((x + sway).toInt() + 1, top.toInt(), Pal.HAY_L) }
                else if (Noise.rnd(x, 1, 47) < 1.1f / k) wheatEar(x + sway, top, p, r)
            } else if (k > 1) when (season) {
                Season.SPRING -> if (r > 0.6f) c.set((x + sway).toInt(), top.toInt(), Col.hex(0x9ACC66))
                Season.AUTUMN -> c.set(x, top.toInt(), Col.hex(0xDCCA9C)) // the cut stalk's end
                Season.WINTER -> if (r > 0.5f) c.set(x, top.toInt() - 1, Pal.SNOW_L)
                else -> {}
            }
        }
    }

    /** An ear of wheat on its stalk's tip at ([x], [top]): grains in two rows, whiskers over it. */
    private fun wheatEar(x: Float, top: Float, p: Float, r: Float) {
        val len = max(3, (0.13f * p).toInt())
        val xi = x.toInt(); val yt = top.toInt()
        for (j in 0 until len) {
            val y = yt - j
            c.set(xi, y, if (j % 2 == 0) Pal.HAY_L else Pal.HAY_M)
            if (j in 1 until len - 1) c.set(xi + 1, y, if (j % 2 == 1) Pal.HAY_L else Pal.HAY_D)
        }
        val awn = Col.mix(Pal.HAY_L, Col.hex(0xFFF4D8), 0.45f)
        c.set(xi, yt - len, awn)
        c.set(if (r > 0.5f) xi + 1 else xi - 1, yt - len - 1, awn)
    }

    /** Corn plants at the near edge of the corn, row by row: tall stalks, leaves, a tassel; dry in autumn. */
    private fun cornPlants() {
        val season = env.season
        val k = detail
        var d = corn.first + 6f
        while (d >= corn.first) {
            val dd = d
            items.add(dd to {
                thing("corn", outline = Col.hex(0x1E3A1E)) {
                    val p = pm(dd); val by = gy(dd)
                    val hgt = (when (season) { Season.SPRING -> 0.4f; Season.WINTER -> 0.45f; else -> 2.2f }) * p
                    val dry = season == Season.AUTUMN || season == Season.WINTER
                    val stalk = if (dry) Col.hex(0xB8A060) else Col.hex(0x3E7A30); val leaf = if (dry) Col.hex(0xD0B878) else Col.hex(0x5E9A44); val leafL = if (dry) Col.hex(0xE0CC90) else Col.hex(0x86BC5A)
                    // the plants that can reach the canvas (all of them at detail 1)
                    val lo = if (k == 1) -4f else c.left - p * 0.6f - 4f
                    val hi = if (k == 1) w + 4f else c.right + p * 0.6f + 4f
                    if (k == 1 || (by + 2f >= c.top && by - hgt * 1.2f - 0.2f * p < c.bottom)) {
                        var x = -34f + (dd * 0.37f) % 0.8f
                        while (x < track(dd) - trackHalf - 0.8f) {
                            val sx = gx(x, dd)
                            if (sx >= lo && sx <= hi) {
                                if (k > 1) cornPlantFine(sx, by, p, hgt, x, dd, season, stalk, leaf, leafL)
                                else {
                                    val r = Noise.rnd((x * 10).toInt(), (dd * 10).toInt(), 49)
                                    val top = by - hgt * (0.9f + r * 0.2f)
                                    val sway = (sin(t * 1.1 + x) * p * 0.05f).toFloat() * gust
                                    c.line(sx.toInt(), by.toInt(), (sx + sway).toInt(), top.toInt(), stalk)
                                    if (season == Season.WINTER) c.set(sx.toInt(), top.toInt() - 1, Pal.SNOW_L)
                                    else if (season != Season.SPRING) {
                                        for (kk in 0..2) {
                                            val ly = by - hgt * (0.3f + kk * 0.2f)
                                            val lw = p * 0.35f
                                            c.line(sx.toInt(), ly.toInt(), (sx - lw).toInt(), (ly - lw * 0.6f).toInt(), if (kk % 2 == 0) leaf else leafL)
                                            c.line(sx.toInt(), (ly - lw * 0.3f).toInt(), (sx + lw).toInt(), (ly - lw * 0.9f).toInt(), if (kk % 2 == 0) leafL else leaf)
                                        }
                                        c.set((sx + sway).toInt(), top.toInt() - 1, Pal.HAY_L)
                                    } else c.set(sx.toInt() + 1, top.toInt(), leafL)
                                }
                            }
                            x += 0.8f
                        }
                    }
                }
            })
            d -= 1.1f
        }
    }

    /** A corn plant closer up: the stalk, arching leaves lit along their tops, a cob in its husk with silk, the tassel. */
    private fun cornPlantFine(sx: Float, by: Float, p: Float, hgt: Float, x: Float, dd: Float, season: Season, stalk: Int, leaf: Int, leafL: Int) {
        val r = Noise.rnd((x * 10).toInt(), (dd * 10).toInt(), 49)
        val top = by - hgt * (0.9f + r * 0.2f)
        val sway = (sin(t * 1.1 + x) * p * 0.05f).toFloat() * gust
        val thickStalk = p > 20f
        c.line(sx.toInt(), by.toInt(), (sx + sway).toInt(), top.toInt(), stalk)
        if (thickStalk) c.line(sx.toInt() + 1, by.toInt(), (sx + sway).toInt() + 1, top.toInt(), Col.scale(stalk, 0.8f))
        val tx = sx + sway
        when (season) {
            Season.WINTER -> { c.hline(tx.toInt() - 1, tx.toInt() + 1, top.toInt() - 1, Pal.SNOW_L); c.set(tx.toInt(), top.toInt() - 2, Pal.SNOW_L) }
            Season.SPRING -> {
                cornLeaf(sx, by - hgt * 0.45f, -1f, 0.16f * p, false, leaf, leafL)
                cornLeaf(sx, by - hgt * 0.7f, 1f, 0.14f * p, false, leafL, leafL)
                c.set(tx.toInt(), top.toInt() - 1, leafL)
            }
            else -> {
                for (kk in 0..2) {
                    val f = 0.3f + kk * 0.2f
                    val ly = by - hgt * f; val lxs = sx + sway * f
                    val lw = p * 0.35f
                    cornLeaf(lxs, ly, -1f, lw * 1.15f, thickStalk, if (kk % 2 == 0) leaf else leafL, leafL)
                    cornLeaf(lxs, ly - lw * 0.3f, 1f, lw * 1.15f, thickStalk, if (kk % 2 == 0) leafL else leaf, leafL)
                }
                // the cob, beside the stalk halfway up
                val cy = by - hgt * 0.42f
                val husk = if (season == Season.AUTUMN) Col.hex(0xE8D49A) else Col.hex(0x9ACC66)
                c.fillEllipse(sx + 1.5f + 0.03f * p, cy, max(1f, 0.045f * p), max(1.5f, 0.12f * p), husk)
                c.set((sx + 1.5f + 0.03f * p).toInt(), (cy - 0.12f * p).toInt(), Col.hex(0x8A5A30))
                c.set((sx + 1.5f + 0.03f * p).toInt() + 1, (cy - 0.12f * p).toInt() - 1, Col.hex(0xA8743A))
                // the tassel: a fan of spikes
                val tl = max(2f, 0.14f * p)
                val tassel = if (season == Season.AUTUMN) Col.hex(0xC8A870) else Pal.HAY_L
                c.line(tx.toInt(), top.toInt(), tx.toInt(), (top - tl).toInt(), tassel)
                c.line(tx.toInt(), (top - tl * 0.3f).toInt(), (tx - tl * 0.5f).toInt(), (top - tl * 0.85f).toInt(), tassel)
                c.line(tx.toInt(), (top - tl * 0.3f).toInt(), (tx + tl * 0.5f).toInt(), (top - tl * 0.75f).toInt(), Pal.HAY_M)
            }
        }
    }

    /** A leaf arching from ([x0], [y0]) out [len] px toward [dir]: up, then drooping at its tip; lit along its middle. */
    private fun cornLeaf(x0: Float, y0: Float, dir: Float, len: Float, thick: Boolean, col: Int, colL: Int) {
        val n = max(4, (len * 1.6f).toInt())
        val under = Col.scale(col, 0.78f)
        for (i in 0..n) {
            val f = i / n.toFloat()
            val x = (x0 + dir * len * f).toInt()
            val y = (y0 - len * (1.25f * f - 0.8f * f * f)).toInt()
            c.set(x, y, if (f in 0.2f..0.65f) colL else col)
            if (thick && f < 0.6f) c.set(x, y + 1, under)
        }
    }

    /** Potato plants on their ridges, the rows running away from us: leafy in summer with white flowers, dug in autumn. */
    private fun potatoPlants() {
        val season = env.season
        val k = detail
        var d = potatoes - 0.2f
        while (d >= 4f) {
            val dd = d
            items.add(dd to {
                thing("potatoes", outline = Col.hex(0x2A3A1E)) {
                    val p = pm(dd); val by = gy(dd)
                    if (k == 1 || (by + 2 >= c.top && by - 0.4f * p < c.bottom)) {
                        val lo = if (k == 1) -6f else c.left - 0.3f * p - 6f
                        val hi = if (k == 1) w + 6f else c.right + 0.3f * p + 6f
                        // on the ridge tops: the soil's rows are 0.8 m apart, ridges at 0.5–0.6 of a row
                        var x = floor((track(dd) - trackHalf - 0.6f) / 0.8f) * 0.8f + 0.52f
                        while (x > -30f) {
                            val sx = gx(x, dd)
                            if (sx >= lo && sx <= hi) plantPotato(sx, by, p, season, (x * 13 + dd * 7).toInt())
                            x -= 0.8f
                        }
                    }
                }
            })
            d -= 0.62f
        }
    }

    private fun plantPotato(x: Float, by: Float, p: Float, season: Season, seed: Int) {
        val r = 0.2f * p
        val k = detail
        when (season) {
            Season.SPRING -> {
                c.fillEllipse(x, by - r * 0.5f, r * 0.6f, r * 0.45f, Col.hex(0x5AAA48)); c.set(x.toInt(), (by - r).toInt(), Col.hex(0x8CC25A))
                if (k > 1 && r > 3f) { c.set(x.toInt() - 1, (by - r * 0.7f).toInt(), Col.hex(0x8CC25A)); c.set(x.toInt() + 1, (by - r * 0.55f).toInt(), Col.hex(0x3E8A3A)) }
            }
            Season.SUMMER -> {
                c.fillEllipse(x, by - r * 0.7f, r * 1.2f, r * 0.8f, Col.hex(0x2E6A2C))
                c.fillEllipse(x - r * 0.2f, by - r * 0.95f, r * 0.8f, r * 0.5f, Col.hex(0x3E8A3A))
                c.fillEllipse(x - r * 0.4f, by - r * 1.15f, r * 0.4f, r * 0.25f, Col.hex(0x5AAA48))
                if (k > 1 && r > 3f) {
                    // the leaflets, the far ones first: each an oval with its shade under it, lit toward the top
                    val n = 16
                    for (i in 0 until n) {
                        val fy = i / (n - 1f)
                        val lx = x + (Noise.rnd(seed, i, 5) - 0.5f) * r * 1.9f * (0.55f + 0.45f * fy)
                        // (within the plant's outline of detail 1: its top is 1.5 r up)
                        val ly = by - r * 1.22f + fy * r * 0.9f + (Noise.rnd(seed, i, 6) - 0.5f) * r * 0.14f
                        val col = if (fy < 0.35f) Col.hex(0x5AAA48) else if (fy < 0.7f) Col.hex(0x4A9A40) else Col.hex(0x3E8A3A)
                        c.fillEllipse(lx, ly, r * 0.24f, r * 0.15f, Col.hex(0x2E6A2C))
                        c.fillEllipse(lx - r * 0.03f, ly - r * 0.04f, r * 0.2f, r * 0.11f, col)
                        c.set((lx - r * 0.1f).toInt(), (ly - r * 0.08f).toInt(), Col.mix(col, Col.hex(0xFFF0C8), 0.3f))
                    }
                }
                if (r > 2.5f && Noise.rnd(seed, 3) < 0.5f) {
                    val fx = x.toInt(); val fy = (by - r * 1.4f).toInt()
                    if (k > 1 && r > 2.5f * k) {
                        // a flower: white petals round a golden heart
                        c.set(fx, fy, Pal.GOLD); c.set(fx - 1, fy, Col.hex(0xFFFFFF)); c.set(fx + 1, fy, Col.hex(0xFFFFFF))
                        c.set(fx, fy - 1, Col.hex(0xFFFFFF)); c.set(fx, fy + 1, Col.hex(0xE8E4F0))
                    } else { c.set(fx, fy, Col.hex(0xFFFFFF)); c.set(fx + 1, fy, Pal.GOLD) }
                }
            }
            Season.AUTUMN -> {
                c.fillEllipse(x, by - r * 0.3f, r * 1.1f, r * 0.4f, Pal.SOIL_L)
                if (r > 2f) for (i in 0..1) {
                    val px = x - r * 0.5f + i * r
                    c.fillEllipse(px, by - r * 0.5f, r * 0.35f, r * 0.25f, Col.hex(0xC4956A))
                    if (k > 1 && r > 3f) { c.set((px - r * 0.12f).toInt(), (by - r * 0.62f).toInt(), Col.hex(0xE0B88A)); c.set((px + r * 0.15f).toInt(), (by - r * 0.45f).toInt(), Col.hex(0x8A6440)) }
                }
            }
            Season.WINTER -> {
                c.fillEllipse(x, by - r * 0.35f, r * 1.1f, r * 0.45f, Pal.SNOW_L)
                if (k > 1 && r > 3f) c.fillEllipse(x + r * 0.3f, by - r * 0.2f, r * 0.6f, r * 0.2f, Pal.SNOW_M)
            }
        }
    }

    /** Cabbages in rows behind the fence: heads in their leaves, seedlings in spring, humps of snow in winter. */
    private fun cabbagePlants() {
        val season = env.season
        val k = detail
        var d = cabbages.second - 0.3f
        while (d >= 4f) {
            val dd = d
            items.add(dd to {
                thing("cabbage", outline = Col.hex(0x23402E)) {
                    val p = pm(dd); val by = gy(dd)
                    if (k == 1 || (by + 2 >= c.top && by - 0.4f * p < c.bottom)) {
                        val lo = if (k == 1) -6f else c.left - 0.3f * p - 6f
                        val hi = if (k == 1) w + 6f else c.right + 0.3f * p + 6f
                        var x = cabbages.first + 0.45f
                        while (x < 30f) {
                            val sx = gx(x, dd)
                            if (sx >= lo && sx <= hi) plantCabbage(sx, by, p, season, (x * 11 + dd * 5).toInt())
                            x += 0.9f
                        }
                    }
                }
            })
            d -= 0.75f
        }
    }

    private fun plantCabbage(x: Float, by: Float, p: Float, season: Season, seed: Int) {
        val r = 0.19f * p * (0.9f + Noise.rnd(seed, 1) * 0.2f)
        val k = detail
        when (season) {
            Season.SPRING -> {
                if (k == 1) { c.set(x.toInt(), (by - 1).toInt(), Col.hex(0x5A9A80)); c.set(x.toInt(), (by - 2).toInt(), Col.hex(0x9ACCB0)) }
                else {
                    // a seedling: a stem, two round leaves
                    c.vline(x.toInt(), (by - k).toInt(), (by - 1).toInt(), Col.hex(0x5A9A80))
                    c.fillEllipse(x - 0.6f * k, by - 1.6f * k, 0.7f * k, 0.45f * k, Col.hex(0x9ACCB0))
                    c.fillEllipse(x + 0.7f * k, by - 1.8f * k, 0.7f * k, 0.45f * k, Col.hex(0x7AB49A))
                }
            }
            Season.SUMMER, Season.AUTUMN -> {
                if (season == Season.AUTUMN && Noise.rnd(seed, 2) > 0.7f) { c.fillEllipse(x, by - r * 0.2f, r * 0.9f, r * 0.3f, Col.hex(0x3E7A62)); return }
                c.fillEllipse(x, by - r * 0.35f, r * 1.3f, r * 0.55f, Col.hex(0x3E7A62))
                if (k > 1 && r > 3f) {
                    // the outer leaves one by one, each lit on its top, its vein toward the heart
                    val vein = Col.hex(0x7AB49A); val leafL = Col.hex(0x4E8A72)
                    for (a in floatArrayOf(0.06f, 0.28f, 0.5f, 0.72f, 0.94f)) {
                        val ang = a * PI.toFloat()
                        val lx = x + cos(ang) * r * 0.95f; val ly = by - r * 0.3f + sin(ang) * r * 0.16f
                        c.fillEllipse(lx, ly, r * 0.42f, r * 0.26f, Col.hex(0x3E7A62))
                        c.fillEllipse(lx - r * 0.05f, ly - r * 0.08f, r * 0.3f, r * 0.13f, leafL)
                        c.line(lx.toInt(), (ly + r * 0.08f).toInt(), (x + (lx - x) * 0.35f).toInt(), (by - r * 0.55f).toInt(), vein)
                    }
                }
                c.fillEllipse(x, by - r * 0.55f, r * 1.1f, r * 0.5f, Col.hex(0x5A9A80))
                c.fillEllipse(x, by - r * 0.8f, r * 0.7f, r * 0.55f, Col.hex(0x9ACCB0))
                c.fillEllipse(x - r * 0.25f, by - r * 1.0f, r * 0.35f, r * 0.25f, Col.hex(0xC4E4CC))
                if (k > 1 && r > 3f) {
                    // the head's folded leaf
                    c.line((x - r * 0.55f).toInt(), (by - r * 0.72f).toInt(), (x + r * 0.15f).toInt(), (by - r * 1.05f).toInt(), Col.hex(0x7AB49A))
                    c.line((x + r * 0.15f).toInt(), (by - r * 1.05f).toInt(), (x + r * 0.5f).toInt(), (by - r * 0.8f).toInt(), Col.hex(0x7AB49A))
                }
            }
            Season.WINTER -> {
                c.fillEllipse(x, by - r * 0.4f, r * 1.1f, r * 0.55f, Pal.SNOW_L)
                if (k > 1 && r > 3f) c.fillEllipse(x + r * 0.35f, by - r * 0.25f, r * 0.6f, r * 0.22f, Pal.SNOW_M)
            }
        }
    }

    /** The kozolec from [xa] to [xb] m, [d] m away: four posts on stone feet, rails, a shingled roof with braces. */
    private fun kozolec(xa: Float, xb: Float, d: Float) {
        if (detail > 1) { kozolecFine(xa, xb, d); return }
        val p = pm(d); val by = gy(d)
        val xs = (0..3).map { gx(xa + (xb - xa) * it / 3f, d) }
        val eave = by - 4.4f * p; val ridge = by - 5.4f * p
        val pw = max(2f, 0.26f * p)
        // rails first, then the posts over them
        for (z in rails) {
            val y = (by - z * p).toInt()
            c.hline(xs[0].toInt(), xs[3].toInt(), y, Pal.WOOD_L); c.hline(xs[0].toInt(), xs[3].toInt(), y + 1, Pal.WOOD_D)
        }
        for (x in xs) {
            c.fillRect((x - pw / 2).toInt(), eave.toInt(), pw.toInt(), (by - eave).toInt(), Pal.WOOD_M)
            c.vline((x - pw / 2).toInt(), eave.toInt(), by.toInt() - 1, Pal.WOOD_L)
            c.vline((x + pw / 2).toInt() - 1, eave.toInt(), by.toInt() - 1, Pal.WOOD_D)
            c.fillRect((x - pw).toInt(), (by - 0.22f * p).toInt(), (pw * 2).toInt(), (0.22f * p).toInt() + 1, Pal.STONE_M)
            c.hline((x - pw).toInt(), (x + pw).toInt() - 1, (by - 0.22f * p).toInt(), Pal.STONE_L)
            c.line(x.toInt(), (eave + 0.8f * p).toInt(), (x - 0.7f * p).toInt(), eave.toInt(), Pal.WOOD_D)
            c.line(x.toInt(), (eave + 0.8f * p).toInt(), (x + 0.7f * p).toInt(), eave.toInt(), Pal.WOOD_D)
        }
        // the roof: its front slope as a band widening down to the eaves, shingled; snow on it in winter
        val over = 0.8f * p
        val step = max(2, (0.26f * p).toInt())
        for (y in ridge.toInt()..eave.toInt()) {
            val u = (y - ridge) / max(1f, eave - ridge)
            val xl = (xs[0] - over - (1 - u) * 0.25f * p).toInt(); val xr = (xs[3] + over + (1 - u) * 0.25f * p).toInt()
            val row = (y - ridge.toInt()) / step
            val col = when {
                env.snow && u < 0.75f -> if ((y + row) % 4 == 0) Pal.SNOW_M else Pal.SNOW_L
                (y - ridge.toInt()) % step == 0 -> Pal.SHINGLE_D
                u < 0.3f -> Pal.SHINGLE_L
                else -> Pal.SHINGLE_M
            }
            c.hline(xl, xr, y, col)
            if (!env.snow && p > 9f && (y - ridge.toInt()) % step != 0) { var sx = xl + (row % 2) * 3; while (sx < xr) { c.set(sx, y, Pal.SHINGLE_D); sx += 6 } }
        }
        c.hline((xs[0] - over - 0.25f * p).toInt(), (xs[3] + over + 0.25f * p).toInt(), ridge.toInt() - 1, if (env.snow) Pal.SNOW_L else Pal.WOOD_X)
        c.hline((xs[0] - over).toInt(), (xs[3] + over).toInt(), eave.toInt() + 1, Pal.WOOD_X)
    }

    /** The kozolec closer up: round rails lit on top, posts with their grain and the pegs through them, stone feet, braces, a roof of single shingles. */
    private fun kozolecFine(xa: Float, xb: Float, d: Float) {
        val k = detail
        val p = pm(d); val by = gy(d)
        val xs = (0..3).map { gx(xa + (xb - xa) * it / 3f, d) }
        val eave = by - 4.4f * p; val ridge = by - 5.4f * p
        val pw = max(2f, 0.26f * p)
        val grain = Col.mix(Pal.WOOD_M, Pal.WOOD_D, 0.45f)
        val railMid = Col.mix(Pal.WOOD_L, Pal.WOOD_M, 0.5f)
        for ((ri, z) in rails.withIndex()) {
            val y = (by - z * p).toInt()
            val th = 2 * k
            for (j in 0 until th) c.hline(xs[0].toInt(), xs[3].toInt(), y + j, when (j) { 0 -> Pal.WOOD_L; th - 1 -> Pal.WOOD_D; 1 -> railMid; else -> Pal.WOOD_M })
            for (xx in max(xs[0].toInt(), c.left)..min(xs[3].toInt(), c.right - 1)) if (Noise.rnd(xx / 5, ri, 74) < 0.3f && xx % 5 != 0) c.set(xx, y + th / 2, grain)
        }
        for ((pi, x) in xs.withIndex()) {
            val x0 = (x - pw / 2).toInt(); val pwi = pw.toInt()
            c.fillRect(x0, eave.toInt(), pwi, (by - eave).toInt(), Pal.WOOD_M)
            c.vline(x0, eave.toInt(), by.toInt() - 1, Pal.WOOD_L)
            c.vline((x + pw / 2).toInt() - 1, eave.toInt(), by.toInt() - 1, Pal.WOOD_D)
            for (gc in x0 + 1 until x0 + pwi - 1) {
                if (gc < c.left || gc >= c.right || Noise.rnd(gc, pi, 75) > 0.45f) continue
                for (yy in max(eave.toInt(), c.top) until min(by.toInt(), c.bottom)) if (Noise.rnd(gc, yy / 4, 76) < 0.55f) c.set(gc, yy, grain)
            }
            // the pegs through the rails
            for (z in rails) { val y = (by - z * p).toInt(); c.fillRect(x.toInt() - 1, y + k - 1, 2, 2, Pal.WOOD_D); c.set(x.toInt() - 1, y + k - 1, Pal.WOOD_L) }
            // the stone foot, lit on top, a crack in it
            val fy = (by - 0.22f * p).toInt()
            c.fillRect((x - pw).toInt(), fy, (pw * 2).toInt(), (0.22f * p).toInt() + 1, Pal.STONE_M)
            c.hline((x - pw).toInt(), (x + pw).toInt() - 1, fy, Pal.STONE_L)
            c.hline((x - pw).toInt() + 1, (x + pw).toInt() - 1, by.toInt(), Pal.STONE_D)
            c.line((x - pw * 0.3f).toInt(), fy + 1, (x - pw * 0.1f).toInt(), by.toInt() - 1, Pal.STONE_D)
            // braces under the eave, lit along their tops
            for (i in 0 until k) {
                val col = if (i == 0) Pal.WOOD_M else Pal.WOOD_D
                c.line(x.toInt(), (eave + 0.8f * p).toInt() + i, (x - 0.7f * p).toInt(), eave.toInt() + i, col)
                c.line(x.toInt(), (eave + 0.8f * p).toInt() + i, (x + 0.7f * p).toInt(), eave.toInt() + i, col)
            }
        }
        // the roof: courses of shingles, each shingle its own shade, a joint beside it, the course above casting a line
        val over = 0.8f * p
        val step = max(2, (0.26f * p).toInt())
        val sw = 3 * k
        val xref = (xs[0] - over - 0.25f * p).toInt()
        val lite = Col.mix(Pal.SHINGLE_L, Col.hex(0xFFF0C8), 0.2f)
        for (y in max(ridge.toInt(), c.top)..min(eave.toInt(), c.bottom - 1)) {
            val u = (y - ridge) / max(1f, eave - ridge)
            val xl = (xs[0] - over - (1 - u) * 0.25f * p).toInt(); val xr = (xs[3] + over + (1 - u) * 0.25f * p).toInt()
            val fy = (y - ridge.toInt()) % step; val row = (y - ridge.toInt()) / step
            if (env.snow && u < 0.75f) { c.hline(xl, xr, y, if (((y - ridge.toInt()) / k + row) % 4 == 0) Pal.SNOW_M else Pal.SNOW_L); continue }
            if (fy == 0) { c.hline(xl, xr, y, Pal.SHINGLE_D); continue }
            for (x in max(xl, c.left)..min(xr, c.right - 1)) {
                val sx = x - xref + (row % 2) * (sw / 2)
                val si = sx / sw; val fx = sx % sw
                val tone = Noise.rnd(si, row, 77)
                var col = if (u < 0.3f) Pal.SHINGLE_L else Pal.SHINGLE_M
                col = if (tone < 0.25f) Col.mix(col, Pal.SHINGLE_D, 0.35f) else if (tone > 0.8f) Col.mix(col, Pal.SHINGLE_L, 0.5f) else col
                col = when {
                    fx == 0 -> Pal.SHINGLE_D
                    fy == step - 1 && step >= 4 -> Col.mix(col, Pal.SHINGLE_D, 0.3f)
                    fy == 1 -> Col.mix(col, lite, 0.35f)
                    fx == 1 -> Col.mix(col, lite, 0.2f)
                    fx == sw / 2 + 1 && fy > 2 && Noise.rnd(si, row, 79) < 0.5f -> Col.mix(col, Pal.SHINGLE_D, 0.25f) // the grain
                    else -> col
                }
                c.set(x, y, col)
            }
        }
        val th = max(1, k - 1)
        for (i in 0 until th) {
            c.hline((xs[0] - over - 0.25f * p).toInt(), (xs[3] + over + 0.25f * p).toInt(), ridge.toInt() - 1 - i, if (env.snow) Pal.SNOW_L else Pal.WOOD_X)
            c.hline((xs[0] - over).toInt(), (xs[3] + over).toInt(), eave.toInt() + 1 + i, if (i == 0) Pal.WOOD_X else Pal.WOOD_D)
        }
    }

    /** The kozolec's rails, heights in metres. */
    private val rails = floatArrayOf(0.85f, 1.5f, 2.15f, 2.8f, 3.45f, 4.05f)

    // ------------------------------------------------------------------ the toplar

    // the double hayrack beyond the wheat: its front rack from topA to topB at topD, the back one topGap behind it
    private val topD = 41f; private val topA = 3.8f; private val topB = 11f; private val topGap = 3.2f

    /**
     * The toplar, the double hayrack, as far as the village has built it (the project "toplar", [stage]): oak logs
     * felled and heaped at its place, the stone feet for its eight posts, the posts raised, the poles fixed between
     * them, the plank roof over both racks; finished, the first hay hangs in it. Scenery: the field's word is the kozolec.
     */
    private fun toplar() {
        val st = stage(TOPLAR)
        if (st <= 0) return
        items.add(topD to { prop { toplar(st) } })
    }

    private fun toplar(st: Int) {
        val k = detail
        val xsF = (0..3).map { topA + (topB - topA) * it / 3f }
        val dB = topD + topGap
        val p = pm(topD); val pB = pm(dB)
        val snow = env.snow
        // the oak logs, felled and heaped in front, fewer as they go into the posts and poles
        if (st <= 3) {
            val n = if (st <= 2) 3 else 1
            for (row in 0 until n) for (j in 0 until n - row) {
                val d = topD - 1.2f; val lp = pm(d)
                val x0 = gx(topA + 0.4f + row * 0.3f + j * 0.05f, d); val x1 = gx(topA + 4.4f - row * 0.3f, d)
                val y = gy(d) - (row + 0.5f) * 0.4f * lp
                val th = max(1, (0.38f * lp).toInt())
                for (q in 0 until th) c.hline(x0.toInt(), x1.toInt(), y.toInt() - q, if (q == th - 1) Pal.LOG_M else Pal.LOG_D)
                c.fillRect(x0.toInt() - k, y.toInt() - th + 1, k, th, Col.hex(0xC8A070))
                if (snow) c.hline(x0.toInt(), x1.toInt(), y.toInt() - th, Pal.SNOW_L)
            }
        }
        if (st < 2) return
        // the stone feet under the eight posts, the back row first
        for ((d, pp) in listOf(dB to pB, topD to p)) for (x in xsF) {
            val fx = gx(x, d); val fy = gy(d)
            c.fillRect((fx - 0.3f * pp).toInt(), (fy - 0.3f * pp).toInt(), max(2, (0.6f * pp).toInt()), max(1, (0.3f * pp).toInt()) + 1, Pal.STONE_M)
            c.hline((fx - 0.3f * pp).toInt(), (fx + 0.3f * pp).toInt() - 1, (fy - 0.3f * pp).toInt(), if (snow) Pal.SNOW_L else Pal.STONE_L)
        }
        if (st < 3) return
        val eave = 4.7f; val ridgeH = 7f
        // the back rack's posts, their poles, then the front rack's
        for ((d, pp) in listOf(dB to pB, topD to p)) {
            val back = d == dB
            val by = gy(d)
            if (st >= 4) for (z in rails) {
                val y = (by - z * pp).toInt()
                c.hline(gx(topA, d).toInt(), gx(topB, d).toInt(), y, if (back) Pal.WOOD_M else Pal.WOOD_L)
                if (!back || k > 1) c.hline(gx(topA, d).toInt(), gx(topB, d).toInt(), y + 1, Pal.WOOD_D)
            }
            if (st >= 6 && !back) hay(topA, topB, d, full = true)
            for (x in xsF) {
                val px = gx(x, d); val pw = max(1f, 0.28f * pp)
                c.fillRect((px - pw / 2).toInt(), (by - eave * pp).toInt(), max(1, pw.toInt()), (eave * pp).toInt(), if (back) Pal.WOOD_D else Pal.WOOD_M)
                if (!back && pw >= 2f) c.vline((px - pw / 2).toInt(), (by - eave * pp).toInt(), by.toInt() - 1, Pal.WOOD_L)
            }
            // the tie beams along the tops of the posts
            c.hline(gx(topA, d).toInt() - k, gx(topB, d).toInt() + k, (by - eave * pp).toInt(), if (back) Pal.WOOD_D else Pal.WOOD_M)
        }
        if (st < 5) return
        // the plank roof over both racks: its front slope from the front eave up to the ridge above the middle, the boards
        // running down it, the gable's edge at each end
        val dF = topD - 0.7f; val dR = topD + topGap / 2f
        val yE = gy(dF) - eave * pm(dF); val yR = hz + F * (EYE - ridgeH) / dR
        val over = 0.9f
        for (y in max(yR.toInt(), c.top)..min(yE.toInt(), c.bottom - 1)) {
            val u = ((y - yR) / max(1f, yE - yR)).coerceIn(0f, 1f)
            val d = dR + (dF - dR) * u
            val xl = gx(topA - over, d).toInt(); val xr = gx(topB + over, d).toInt()
            for (x in max(xl, c.left)..min(xr, c.right - 1)) {
                val board = (x - xl) / max(1, 2 * k)
                c.set(x, y, when {
                    snow && u < 0.8f -> if (Noise.rnd(x, y, 91) < 0.1f) Pal.SNOW_M else Pal.SNOW_L
                    (x - xl) % max(2, 2 * k) == 0 && k > 1 -> Pal.WOOD_D
                    Noise.rnd(board, 0, 92) < 0.3f -> Pal.WOOD_M
                    u < 0.25f -> Pal.WOOD_L
                    else -> Col.mix(Pal.WOOD_L, Pal.WOOD_M, 0.5f)
                })
            }
        }
        c.hline(gx(topA - over, dR).toInt(), gx(topB + over, dR).toInt(), yR.toInt() - 1, if (snow) Pal.SNOW_L else Pal.WOOD_X)
        c.hline(gx(topA - over, dF).toInt(), gx(topB + over, dF).toInt(), yE.toInt() + 1, Pal.WOOD_X)
    }

    /**
     * Hay drying on the kozolec, hung over the rails in curtains: the left bay full, the middle one being filled,
     * the right one still empty (last year's scraps in spring, snow on the hay in winter); some fallen under it.
     */
    private fun hay(xa: Float, xb: Float, d: Float, full: Boolean = false) {
        val k = detail
        val p = pm(d); val by = gy(d)
        val fill = when (env.season) {
            Season.SPRING -> if (full) intArrayOf(3, 3, 3) else intArrayOf(2, 0, 0)
            Season.WINTER -> if (full) intArrayOf(4, 4, 4) else intArrayOf(4, 2, 0)
            else -> if (full) intArrayOf(6, 6, 6) else intArrayOf(6, 3, 1)
        }
        val hl = if (env.snow) Col.mix(Pal.HAY_L, Pal.SNOW_M, 0.3f) else Pal.HAY_L
        for (bay in 0 until 3) {
            val x0 = gx(xa + (xb - xa) * bay / 3f, d).toInt() + max(2, (0.2f * p).toInt())
            val x1 = gx(xa + (xb - xa) * (bay + 1) / 3f, d).toInt() - max(2, (0.2f * p).toInt())
            for (kk in 0 until fill[bay]) {
                val top = (by - rails[kk] * p).toInt() - k
                val hangM = 0.52f * p
                if (k == 1) {
                    for (x in x0..x1) {
                        val hang = (hangM * (0.8f + Noise.rnd(x / 2, kk, 52) * 0.35f)).toInt()
                        for (y in top..top + hang) {
                            val r = Noise.rnd(x, y, 51)
                            c.set(x, y, if (y == top) hl else if (r < 0.28f) hl else if (r > 0.85f || y > top + hang - 2) Pal.HAY_D else Pal.HAY_M)
                        }
                    }
                } else {
                    // strands: streaks down the curtain, a ragged fringe, the rail's shadow under the fold
                    for (x in max(x0, c.left)..min(x1, c.right - 1)) {
                        val hang = (hangM * (0.8f + Noise.rnd(x / (2 * k), kk, 52) * 0.35f) + (Noise.rnd(x, kk, 53) - 0.5f) * 0.14f * hangM).toInt()
                        val shift = (Noise.rnd(x, kk, 50) * 3).toInt()
                        for (y in max(top, c.top)..min(top + hang, c.bottom - 1)) {
                            val r = Noise.rnd(x, (y + shift) / 3, 51)
                            c.set(x, y, when {
                                y == top -> hl
                                y <= top + k && r < 0.5f -> Col.mix(hl, Col.hex(0xFFF4D8), 0.3f)
                                y > top + hang - 2 -> Pal.HAY_D
                                r < 0.28f -> hl
                                r > 0.85f -> Pal.HAY_D
                                y in top + 2 * k..top + 3 * k && r > 0.6f -> Col.mix(Pal.HAY_M, Pal.HAY_D, 0.5f)
                                else -> Pal.HAY_M
                            })
                        }
                    }
                }
                if (env.snow) for (i in 1..k) c.hline(x0, x1, top - i, Pal.SNOW_L)
            }
        }
        if (k == 1) {
            for (x in gx(xa, d).toInt() - 3..gx(xb, d).toInt() + 3 step 2) if (Noise.rnd(x, 54) < 0.5f) { c.set(x, by.toInt() - 1, Pal.HAY_M); c.set(x + 1, by.toInt() - 2, hl) }
        } else {
            val byi = by.toInt()
            var x = gx(xa, d).toInt() - 3 * k
            while (x <= gx(xb, d).toInt() + 3 * k) {
                if (Noise.rnd(x / k, 54) < 0.5f) {
                    c.line(x, byi - 1, x + k, byi - k, Pal.HAY_M)
                    c.line(x + 1, byi - 1, x + 2 * k - 1, byi - 1 - k / 2, hl)
                    c.set(x + k / 2, byi - 1, Pal.HAY_D)
                }
                x += 2 * k
            }
        }
    }

    /** A wooden rake leaning on the kozolec's post. */
    private fun rake(x: Float, d: Float) {
        val p = pm(d); val bx = gx(x, d); val by = gy(d)
        val tx = bx - 0.35f * p; val ty = by - 1.9f * p
        val k = detail
        if (k == 1) {
            c.line(bx.toInt(), by.toInt(), tx.toInt(), ty.toInt(), Pal.WOOD_L)
            c.line(bx.toInt() + 1, by.toInt(), tx.toInt() + 1, ty.toInt(), Pal.WOOD_D)
            c.fillRect((tx - 0.35f * p).toInt(), ty.toInt() - 1, (0.7f * p).toInt(), 2, Pal.WOOD_M)
            var tooth = tx - 0.33f * p
            while (tooth < tx + 0.35f * p) { c.vline(tooth.toInt(), ty.toInt() - 3, ty.toInt() - 2, Pal.WOOD_X); tooth += max(2f, 0.12f * p) }
            return
        }
        // the handle as thick as ever, lit on its left; the head with its lit top; thin teeth
        val lit = Col.mix(Pal.WOOD_L, Col.hex(0xFFF0C8), 0.3f); val half = Col.mix(Pal.WOOD_L, Pal.WOOD_D, 0.5f)
        for (i in 0 until 2 * k) c.line(bx.toInt() + i, by.toInt(), tx.toInt() + i, ty.toInt(), when { i == 0 -> lit; i < k -> Pal.WOOD_L; i == 2 * k - 1 -> Pal.WOOD_D; else -> half })
        val hx0 = (tx - 0.35f * p).toInt(); val hw = (0.7f * p).toInt()
        c.fillRect(hx0, ty.toInt() - k, hw, 2 * k, Pal.WOOD_M)
        c.hline(hx0, hx0 + hw - 1, ty.toInt() - k, Pal.WOOD_L); c.hline(hx0, hx0 + hw - 1, ty.toInt() + k - 1, Pal.WOOD_D)
        var tooth = tx - 0.33f * p
        while (tooth < tx + 0.35f * p) { c.vline(tooth.toInt(), ty.toInt() - 3 * k, ty.toInt() - k - 1, Pal.WOOD_X); c.set(tooth.toInt(), ty.toInt() - 3 * k, Pal.WOOD_D); tooth += max(2f, 0.12f * p) }
    }

    /** The horse grazing in the shafts, facing left; now and then its head comes up to look. Closer up: its mane, its hooves and its harness. */
    private fun horse(x: Float, d: Float) {
        val k = detail
        val p = pm(d); val bx = gx(x, d); val by = gy(d)
        val coat = Col.hex(0x7A4A2A); val coatL = Col.hex(0x9A6640); val coatD = Col.hex(0x54321C); val mane = Col.hex(0x2E2018)
        val maneL = Col.hex(0x4A3626)
        val graze = sin(t * 0.5) > -0.3
        val bodyY = by - 1.2f * p
        // legs, the far pair darker
        for ((i, lx) in floatArrayOf(-0.75f, -0.55f, 0.55f, 0.75f).withIndex()) {
            val sx = bx + lx * p
            val lw = max(1, (0.13f * p).toInt())
            c.fillRect(sx.toInt(), (bodyY + 0.2f * p).toInt(), lw, (0.95f * p).toInt(), if (i % 2 == 0) coatD else coat)
            if (k == 1) c.hline(sx.toInt(), sx.toInt() + lw - 1, by.toInt() - 1, mane)
            else {
                // the hoof, the fetlock over it; the near legs lit down their front
                if (i % 2 == 1) c.vline(sx.toInt(), (bodyY + 0.35f * p).toInt(), by.toInt() - k - 1, coatL)
                c.fillRect(sx.toInt(), by.toInt() - k, lw, k, mane)
                c.hline(sx.toInt(), sx.toInt() + lw - 1, by.toInt() - k, maneL)
                c.hline(sx.toInt(), sx.toInt() + lw - 1, by.toInt() - k - 1, coatD)
            }
        }
        c.fillEllipse(bx, bodyY, 1.0f * p, 0.42f * p, coat)
        c.fillEllipse(bx - 0.1f * p, bodyY - 0.15f * p, 0.7f * p, 0.2f * p, coatL)
        c.hline((bx - 0.8f * p).toInt(), (bx + 0.8f * p).toInt(), (bodyY + 0.38f * p).toInt(), coatD)
        if (k > 1) {
            // the belly's shade, the haunch's curve
            for (xx in (bx - 0.7f * p).toInt()..(bx + 0.7f * p).toInt()) if ((xx and 1) == 0) c.set(xx, (bodyY + 0.38f * p).toInt() - 1, coatD)
            for (a in 0..10) { val ang = PI * (0.55 + a * 0.06); c.set((bx + 0.62f * p + cos(ang) * 0.3f * p).toInt(), (bodyY - 0.02f * p - sin(ang) * 0.3f * p).toInt(), coatD) }
        }
        // the tail, swishing
        val swish = sin(t * 2.2).toFloat() * 0.1f * p
        if (k == 1) c.line((bx + 0.95f * p).toInt(), (bodyY - 0.2f * p).toInt(), (bx + 1.15f * p + swish).toInt(), (bodyY + 0.6f * p).toInt(), mane)
        else for (i in 0 until 2 + k) {
            val sp = (i - (1 + k) / 2f) * 0.035f * p
            c.line((bx + 0.95f * p).toInt() + i / 2, (bodyY - 0.2f * p).toInt(), (bx + 1.15f * p + swish + sp).toInt(), (bodyY + (0.58f + (i % 2) * 0.05f) * p).toInt(), if (i % 2 == 1) maneL else mane)
        }
        // the neck and head: down in the grass, or up
        val nx = bx - 0.85f * p; val ny = bodyY - 0.2f * p
        val hx: Float; val hy: Float
        if (graze) { hx = bx - 1.45f * p; hy = by - 0.35f * p } else { hx = bx - 1.35f * p; hy = bodyY - 0.95f * p }
        for (i in 0..6) { val f = i / 6f; c.fillCircle(nx + (hx - nx) * f, ny + (hy - ny) * f, 0.2f * p * (1.1f - f * 0.3f), coat) }
        c.fillEllipse(hx - 0.1f * p, hy, 0.3f * p, 0.16f * p, coat)
        c.fillEllipse(hx - 0.3f * p, hy + 0.06f * p, 0.13f * p, 0.1f * p, coatD)
        c.set((hx - 0.05f * p).toInt(), (hy - 0.06f * p).toInt(), Pal.OUTLINE)
        if (k == 1) {
            for (i in 0..4) { val f = i / 5f; c.set((nx + (hx - nx) * f + 0.1f * p).toInt(), (ny + (hy - ny) * f - 0.18f * p).toInt(), mane) }
            return
        }
        // a glint in the eye, the ear, the nostril
        c.set((hx - 0.05f * p).toInt() - 1, (hy - 0.06f * p).toInt(), Col.hex(0xC8B8A0))
        c.line((hx + 0.12f * p).toInt(), (hy - 0.12f * p).toInt(), (hx + 0.2f * p).toInt(), (hy - 0.24f * p).toInt(), coatD)
        c.set((hx - 0.36f * p).toInt(), (hy + 0.04f * p).toInt(), Pal.OUTLINE)
        // the harness: the collar round the neck's foot with its hames, the traces back to the shafts, the back band, the bridle
        val leather = Col.hex(0x3A2418); val leatherL = Col.hex(0x6A4428)
        val colX = nx + (hx - nx) * 0.1f; val colY = ny + (hy - ny) * 0.1f
        c.fillEllipse(colX, colY + 0.05f * p, 0.12f * p, 0.32f * p, leather)
        c.fillEllipse(colX - 0.04f * p, colY + 0.05f * p, 0.05f * p, 0.26f * p, Col.hex(0x8A6A3A))
        c.line((colX - 0.1f * p).toInt(), (colY - 0.22f * p).toInt(), (colX - 0.1f * p).toInt(), (colY + 0.3f * p).toInt(), Pal.GOLD)
        c.set((colX - 0.1f * p).toInt(), (colY - 0.25f * p).toInt() - 1, Pal.GOLD_L)
        thick(colX + 0.08f * p, colY + 0.2f * p, bx + 0.25f * p, bodyY + 0.12f * p, max(1, k - 1), leather)
        c.fillRect((bx - 0.2f * p).toInt(), (bodyY - 0.42f * p).toInt(), max(2, (0.07f * p).toInt()), (0.82f * p).toInt(), leather)
        c.fillEllipse(bx - 0.17f * p, bodyY - 0.4f * p, 0.16f * p, 0.06f * p, leatherL)
        c.line((hx + 0.08f * p).toInt(), (hy - 0.12f * p).toInt(), (hx - 0.02f * p).toInt(), (hy + 0.13f * p).toInt(), leather)
        c.line((hx - 0.26f * p).toInt(), (hy - 0.05f * p).toInt(), (hx - 0.24f * p).toInt(), (hy + 0.14f * p).toInt(), leather)
        // the mane: strands along the neck's top, falling over it; a forelock
        val strands = max(6, (sqrt((hx - nx) * (hx - nx) + (hy - ny) * (hy - ny)) / 1.5f).toInt())
        for (i in 0 until strands) {
            val f = i / strands.toFloat() * 0.85f
            val mx = nx + (hx - nx) * f + 0.1f * p; val my = ny + (hy - ny) * f - 0.18f * p
            val len = (0.1f + Noise.rnd(i, 58) * 0.08f) * p
            c.line(mx.toInt(), my.toInt(), (mx - 0.04f * p).toInt(), (my + len).toInt(), if (i % 3 == 1) maneL else mane)
        }
        c.line((hx + 0.04f * p).toInt(), (hy - 0.14f * p).toInt(), (hx - 0.08f * p).toInt(), (hy - 0.08f * p).toInt(), mane)
    }

    /**
     * A clump of tall grass round the grazing horse's muzzle, closer up (at detail 1 the kozolec's post covers
     * the muzzle; finer, it would peek out beside it): grass blades, or a drift in winter.
     */
    private fun grazeTuft(x: Float, d: Float) {
        val p = pm(d); val by = gy(d)
        val mx = gx(x, d) - 1.75f * p
        val half = 0.26f * p
        val g = env.grass
        val snow = env.snow
        for (xx in max((mx - half).toInt(), c.left)..min((mx + half).toInt(), c.right - 1)) {
            val u = (xx + 0.5f - mx) / half
            if (abs(u) > 1f) continue
            val top = (by - (0.52f - 0.22f * u * u + (Noise.rnd(xx, 81) - 0.5f) * 0.06f) * p).toInt()
            for (yy in max(top, c.top)..min(by.toInt(), c.bottom - 1)) {
                val f = (yy - top) / max(1f, by - top)
                val r = Noise.rnd(xx, yy / 2, 82)
                c.set(xx, yy, if (snow) (if (f < 0.3f || r < 0.2f) Pal.SNOW_L else Pal.SNOW_M) else if (f < 0.1f) g[0] else if (r < 0.25f) g[2] else if (r > 0.82f) g[0] else g[1])
            }
        }
        if (!snow) for (i in 0 until 10) {
            val bx0 = mx + (Noise.rnd(i, 84) - 0.5f) * 1.8f * half
            val hgt = (0.5f + Noise.rnd(i, 85) * 0.2f) * p
            val lean = (Noise.rnd(i, 86) - 0.5f) * 0.25f * p
            c.line(bx0.toInt(), by.toInt(), (bx0 + lean).toInt(), (by - hgt).toInt(), if (i % 3 == 0) g[3] else if (i % 3 == 1) g[2] else g[0])
        }
    }

    /** The hay cart from [xa] to [xb] m, [d] m away: a plank body on spoked wheels, the load by season, the shafts. */
    private fun cart(xa: Float, xb: Float, d: Float) {
        if (detail > 1) { cartFine(xa, xb, d); return }
        val p = pm(d); val by = gy(d)
        val x0 = gx(xa, d); val x1 = gx(xb, d)
        val bodyTop = by - 1.15f * p; val bodyBot = by - 0.62f * p
        // the load
        when (env.season) {
            Season.SUMMER, Season.AUTUMN -> {
                val top = bodyTop - 1.3f * p
                for (y in top.toInt()..bodyTop.toInt()) {
                    val u = (y - top) / max(1f, bodyTop - top)
                    val inset = (1 - u) * 0.35f * p
                    for (x in (x0 + inset - 0.15f * p).toInt()..(x1 - inset + 0.15f * p).toInt()) c.set(x, y, if (Noise.rnd(x, y, 55) < 0.3f) Pal.HAY_L else if (Noise.rnd(x, y, 56) < 0.15f) Pal.HAY_D else Pal.HAY_M)
                }
            }
            Season.SPRING -> { var x = x0 + 0.1f * p; while (x < x1 - 0.5f * p) { c.fillRect(x.toInt(), (bodyTop - 0.5f * p).toInt(), (0.45f * p).toInt(), (0.5f * p).toInt(), Col.hex(0xC9AE7A)); x += 0.55f * p } }
            Season.WINTER -> c.fillRect(x0.toInt(), (bodyTop - 0.2f * p).toInt(), (x1 - x0).toInt(), (0.2f * p).toInt() + 1, Pal.SNOW_L)
        }
        c.fillRect(x0.toInt(), bodyTop.toInt(), (x1 - x0).toInt(), (bodyBot - bodyTop).toInt(), Pal.WOOD_M)
        c.hline(x0.toInt(), x1.toInt() - 1, bodyTop.toInt(), Pal.WOOD_L); c.hline(x0.toInt(), x1.toInt() - 1, bodyBot.toInt(), Pal.WOOD_D)
        if (p > 9f) c.hline(x0.toInt(), x1.toInt() - 1, ((bodyTop + bodyBot) / 2).toInt(), Pal.WOOD_D)
        // the shafts out to the horse
        c.line(x0.toInt(), (bodyBot - 0.1f * p).toInt(), (x0 - 1.4f * p).toInt(), (by - 1.0f * p).toInt(), Pal.WOOD_D)
        // wheels
        for ((wx, rr) in listOf(x0 + 0.55f * p to 0.52f * p, x1 - 0.6f * p to 0.58f * p)) {
            val wy = by - rr
            val n = max(12, (rr * 6).toInt())
            for (a in 0 until n) { val ang = a * 2 * PI / n; c.set((wx + cos(ang) * rr).toInt(), (wy + sin(ang) * rr).toInt(), Pal.WOOD_X) }
            if (rr > 4f) for (a in 0 until n) { val ang = a * 2 * PI / n; c.set((wx + cos(ang) * (rr - 1)).toInt(), (wy + sin(ang) * (rr - 1)).toInt(), Pal.WOOD_L) }
            for (a in 0 until 6) { val ang = a * PI / 3 + 0.3; c.line(wx.toInt(), wy.toInt(), (wx + cos(ang) * (rr - 1)).toInt(), (wy + sin(ang) * (rr - 1)).toInt(), Pal.WOOD_M) }
            c.set(wx.toInt(), wy.toInt(), Pal.STONE_L)
        }
    }

    /** The cart closer up: the load in strands, side boards with their joints and grain, iron straps and nails, the shafts, the wheels. */
    private fun cartFine(xa: Float, xb: Float, d: Float) {
        val k = detail
        val p = pm(d); val by = gy(d)
        val x0 = gx(xa, d); val x1 = gx(xb, d)
        val bodyTop = by - 1.15f * p; val bodyBot = by - 0.62f * p
        when (env.season) {
            Season.SUMMER, Season.AUTUMN -> {
                val top = bodyTop - 1.3f * p
                for (y in max(top.toInt(), c.top)..min(bodyTop.toInt(), c.bottom - 1)) {
                    val u = (y - top) / max(1f, bodyTop - top)
                    val inset = (1 - u) * 0.35f * p
                    val xa0 = (x0 + inset - 0.15f * p).toInt(); val xb0 = (x1 - inset + 0.15f * p).toInt()
                    for (x in max(xa0, c.left)..min(xb0, c.right - 1)) {
                        // strands lying along the load, a few sticking out at its edges
                        val r = Noise.rnd(x / 3, y, 55)
                        val edge = x - xa0 < k || xb0 - x < k
                        c.set(x, y, when {
                            edge && Noise.rnd(x, y, 57) < 0.4f -> Pal.HAY_D
                            r < 0.3f -> Pal.HAY_L
                            r > 0.86f -> Pal.HAY_D
                            u < 0.2f && Noise.rnd(x, y, 56) < 0.3f -> Col.mix(Pal.HAY_L, Col.hex(0xFFF4D8), 0.35f)
                            else -> Pal.HAY_M
                        })
                    }
                }
                // loose wisps over its top
                for (i in 0 until 6 * k) {
                    val wx = x0 + 0.2f * p + Noise.rnd(i, 59) * (x1 - x0 - 0.4f * p); val wy = top + (Noise.rnd(i, 60) * 0.2f) * p
                    c.line(wx.toInt(), wy.toInt(), (wx + (Noise.rnd(i, 61) - 0.5f) * 0.3f * p).toInt(), (wy - 0.06f * p).toInt(), Pal.HAY_L)
                }
            }
            Season.SPRING -> {
                var x = x0 + 0.1f * p
                while (x < x1 - 0.5f * p) {
                    // sacks, tied at the top, a seam down them
                    c.fillRect(x.toInt(), (bodyTop - 0.5f * p).toInt(), (0.45f * p).toInt(), (0.5f * p).toInt(), Col.hex(0xC9AE7A))
                    c.vline((x + 0.3f * p).toInt(), (bodyTop - 0.45f * p).toInt(), bodyTop.toInt() - 1, Col.hex(0xA88E5E))
                    c.hline(x.toInt(), (x + 0.45f * p).toInt() - 1, (bodyTop - 0.5f * p).toInt(), Col.hex(0xDCC498))
                    c.hline((x + 0.15f * p).toInt(), (x + 0.3f * p).toInt(), (bodyTop - 0.42f * p).toInt(), Col.hex(0x7A5A3A))
                    x += 0.55f * p
                }
            }
            Season.WINTER -> {
                c.fillRect(x0.toInt(), (bodyTop - 0.2f * p).toInt(), (x1 - x0).toInt(), (0.2f * p).toInt() + 1, Pal.SNOW_L)
                for (x in x0.toInt() until x1.toInt()) if (Noise.rnd(x / 3, 62) < 0.4f) c.set(x, (bodyTop - 0.2f * p).toInt() - 1, Pal.SNOW_L)
                c.hline(x0.toInt(), x1.toInt() - 1, bodyTop.toInt() - 1, Pal.SNOW_M)
            }
        }
        // the side boards
        val bt = bodyTop.toInt(); val bb = bodyBot.toInt()
        c.fillRect(x0.toInt(), bt, (x1 - x0).toInt(), bb - bt, Pal.WOOD_M)
        val grain = Col.mix(Pal.WOOD_M, Pal.WOOD_D, 0.4f)
        for (y in max(bt, c.top) until min(bb, c.bottom)) for (x in max(x0.toInt(), c.left) until min(x1.toInt(), c.right)) if (Noise.rnd(x / 6, y, 78) < 0.16f) c.set(x, y, grain)
        for (i in 1..2) {
            val y = (bodyTop + (bodyBot - bodyTop) * i / 3f).toInt()
            c.hline(x0.toInt(), x1.toInt() - 1, y, Pal.WOOD_D); c.hline(x0.toInt(), x1.toInt() - 1, y + 1, Col.mix(Pal.WOOD_M, Pal.WOOD_L, 0.5f))
        }
        c.hline(x0.toInt(), x1.toInt() - 1, bt, Pal.WOOD_L); c.hline(x0.toInt(), x1.toInt() - 1, bb, Pal.WOOD_D)
        // iron straps with their nails
        val iron = Col.hex(0x3A3640); val ironL = Col.hex(0x6A6670)
        val sw = max(2, (0.06f * p).toInt())
        for (sx in floatArrayOf(x0 + 0.1f * p, (x0 + x1) / 2f, x1 - 0.1f * p - sw)) {
            c.fillRect(sx.toInt(), bt, sw, bb - bt + 1, iron)
            c.vline(sx.toInt(), bt, bb, ironL)
            for (i in 0..2) c.set(sx.toInt() + sw / 2, (bodyTop + (bodyBot - bodyTop) * (i + 0.5f) / 3f).toInt(), Col.hex(0xA8A4B0))
        }
        // the shafts out to the horse
        val sth = max(1, (0.05f * p).toInt())
        thick(x0, bodyBot - 0.1f * p, x0 - 1.4f * p, by - 1.0f * p, sth, Pal.WOOD_D)
        c.line(x0.toInt(), (bodyBot - 0.1f * p).toInt() - sth / 2, (x0 - 1.4f * p).toInt(), (by - 1.0f * p).toInt() - sth / 2, Pal.WOOD_M)
        for ((wx, rr) in listOf(x0 + 0.55f * p to 0.52f * p, x1 - 0.6f * p to 0.58f * p)) wheel(wx, by - rr, rr, p)
    }

    /** A cart wheel closer up: ten spokes, the felloe in its segments lit on the upper left, the iron tyre, the hub. */
    private fun wheel(wx: Float, wy: Float, rr: Float, p: Float) {
        val tyre = max(1f, 0.045f * p); val felloe = max(2f, 0.1f * p)
        val inner = rr - tyre - felloe
        val hub = max(2f, 0.12f * p)
        val sth = if (p > 30f) 2 else 1
        for (a in 0 until 10) {
            val ang = a * 2 * PI / 10 + 0.3
            val ex = wx + cos(ang).toFloat() * (inner + 1); val ey = wy + sin(ang).toFloat() * (inner + 1)
            thick(wx + 1, wy + 1, ex + 1, ey + 1, sth, Pal.WOOD_D) // its shade
            thick(wx, wy, ex, ey, sth, Pal.WOOD_L)
        }
        val tyreL = Col.hex(0x6A6268); val tyreD = Col.hex(0x2A2226)
        for (y in max((wy - rr).toInt() - 1, c.top)..min((wy + rr).toInt() + 1, c.bottom - 1)) for (x in max((wx - rr).toInt() - 1, c.left)..min((wx + rr).toInt() + 1, c.right - 1)) {
            val dx = x + 0.5f - wx; val dy = y + 0.5f - wy
            val r = sqrt(dx * dx + dy * dy)
            if (r > rr || r < inner) continue
            val lit = (-dx - dy) / (r * 1.4142f + 0.01f)
            val col = if (r > rr - tyre) (if (lit > 0.55f) tyreL else if (lit < -0.3f) tyreD else Pal.WOOD_X)
            else {
                val seg = ((atan2(dy, dx) / (2 * PI.toFloat() / 5) + 10f) % 1f)
                if (seg < 0.035f) Pal.WOOD_D else if (lit > 0.35f) Pal.WOOD_L else if (lit < -0.45f) Pal.WOOD_D else Pal.WOOD_M
            }
            c.set(x, y, col)
        }
        c.fillCircle(wx, wy, hub, Pal.WOOD_D)
        c.fillCircle(wx - hub * 0.2f, wy - hub * 0.2f, hub * 0.62f, Pal.WOOD_L)
        c.fillCircle(wx, wy, max(1f, hub * 0.3f), Col.hex(0x2A2226))
    }

    /** The scarecrow in the wheat: an old blue coat on a cross, a sack face, a straw hat, a crow on its arm. */
    private fun scarecrow(x: Float, d: Float) {
        val k = detail
        val p = pm(d); val bx = gx(x, d); val by = gy(d)
        val coat = Col.hex(0x3A5A8A); val coatD = Col.hex(0x2A4468)
        c.fillRect(bx.toInt(), (by - 2.2f * p).toInt(), max(1, (0.1f * p).toInt()), (2.2f * p).toInt(), Pal.WOOD_D)
        c.fillRect((bx - 0.75f * p).toInt(), (by - 1.75f * p).toInt(), (1.5f * p).toInt(), max(1, (0.08f * p).toInt()), Pal.WOOD_M)
        c.fillRect((bx - 0.3f * p).toInt(), (by - 1.75f * p).toInt(), (0.6f * p).toInt(), (0.85f * p).toInt(), coat)
        c.vline((bx + 0.3f * p).toInt() - 1, (by - 1.75f * p).toInt(), (by - 0.9f * p).toInt(), coatD)
        c.fillRect((bx - 0.7f * p).toInt(), (by - 1.75f * p).toInt(), (1.4f * p).toInt(), max(1, (0.18f * p).toInt()), coat)
        if (k == 1) { c.set((bx - 0.75f * p).toInt(), (by - 1.65f * p).toInt(), Pal.HAY_L); c.set((bx + 0.75f * p).toInt(), (by - 1.65f * p).toInt(), Pal.HAY_L) }
        else {
            // a patch sewn on, buttons down the front, the hem frayed; straw out of the sleeves
            val px0 = (bx - 0.22f * p).toInt(); val py0 = (by - 1.3f * p).toInt(); val pw = max(3, (0.18f * p).toInt())
            c.fillRect(px0, py0, pw, pw, Col.hex(0x8A6A3A))
            for (i in 0 until pw step 2) { c.set(px0 + i, py0, Col.hex(0xE8DCC0)); c.set(px0 + pw - 1, py0 + i, Col.hex(0xE8DCC0)) }
            for (i in 0..2) c.set((bx + 0.08f * p).toInt(), (by - (1.5f - i * 0.2f) * p).toInt(), Pal.GOLD_L)
            for (xx in (bx - 0.3f * p).toInt() until (bx + 0.3f * p).toInt()) if (Noise.rnd(xx, 79) < 0.5f) c.set(xx, (by - 0.9f * p).toInt(), coatD)
            for (side in intArrayOf(-1, 1)) {
                val sx = bx + side * 0.72f * p; val sy = by - 1.66f * p
                for (i in 0..3) c.line(sx.toInt(), sy.toInt(), (sx + side * (0.08f + i * 0.03f) * p).toInt(), (sy + (i - 1.5f) * 0.05f * p).toInt(), if (i % 2 == 0) Pal.HAY_L else Pal.HAY_M)
            }
        }
        c.fillCircle(bx, by - 2.0f * p, 0.2f * p, Col.hex(0xC9AE7A))
        if (k == 1) { c.set((bx - 0.07f * p).toInt(), (by - 2.03f * p).toInt(), Pal.OUTLINE); c.set((bx + 0.07f * p).toInt(), (by - 2.03f * p).toInt(), Pal.OUTLINE) }
        else {
            // stitched eyes and mouth, the sack's weave
            for (xx in (bx - 0.18f * p).toInt()..(bx + 0.18f * p).toInt()) for (yy in (by - 2.18f * p).toInt()..(by - 1.82f * p).toInt()) if (((xx + yy) and 3) == 0 && c.inside(xx, yy) && c.ids[c.index(xx, yy)] == c.penId) c.set(xx, yy, Col.hex(0xB09868))
            for (e in intArrayOf(-1, 1)) {
                val ex = (bx + e * 0.07f * p).toInt(); val ey = (by - 2.03f * p).toInt()
                c.set(ex, ey, Pal.OUTLINE); c.set(ex - 1, ey - 1, Pal.OUTLINE); c.set(ex + 1, ey + 1, Pal.OUTLINE); c.set(ex + 1, ey - 1, Pal.OUTLINE); c.set(ex - 1, ey + 1, Pal.OUTLINE)
            }
            val my = (by - 1.92f * p).toInt()
            for (xx in (bx - 0.1f * p).toInt()..(bx + 0.1f * p).toInt() step 2) c.set(xx, my, Pal.OUTLINE)
            for (side in intArrayOf(-1, 1)) c.line((bx + side * 0.17f * p).toInt(), (by - 2.12f * p).toInt(), (bx + side * 0.27f * p).toInt(), (by - 2.0f * p).toInt(), Pal.HAY_L)
        }
        val brimY = (by - 2.18f * p).toInt()
        c.hline((bx - 0.35f * p).toInt(), (bx + 0.35f * p).toInt(), brimY, Pal.HAY_L)
        c.fillRect((bx - 0.15f * p).toInt(), (by - 2.38f * p).toInt(), (0.3f * p).toInt() + 1, (0.2f * p).toInt(), Pal.HAY_M)
        if (k > 1) {
            // the brim's shade, the band, the weave of the straw
            c.hline((bx - 0.33f * p).toInt(), (bx + 0.33f * p).toInt(), brimY + 1, Pal.HAY_D)
            c.hline((bx - 0.15f * p).toInt(), (bx + 0.15f * p).toInt(), brimY - 1, Col.hex(0x8A3A2A))
            for (xx in (bx - 0.15f * p).toInt()..(bx + 0.15f * p).toInt()) for (yy in (by - 2.38f * p).toInt() until brimY - 1) if (((xx * 2 + yy) % 3) == 0) c.set(xx, yy, Pal.HAY_L)
        }
        if (env.snow) for (i in 0 until k) c.hline((bx - 0.15f * p).toInt(), (bx + 0.15f * p).toInt(), (by - 2.4f * p).toInt() - i, Pal.SNOW_L)
        // the crow on the right arm, hopping now and then; startled off it by a tap it flies (see [crows])
        val hop = if (sin(t * 2.3) > 0.7) 1 else 0
        if (!crowUp(0, (bx + 0.55f * p).toInt(), (by - 1.8f * p).toInt())) perchedCrow((bx + 0.55f * p).toInt(), (by - 1.8f * p).toInt() - hop * k, flip = false)
    }

    /** Where [crowUp] puts a crow in flight (picture px). */
    private var flyX = 0
    private var flyY = 0

    /**
     * Whether the [j]th crow of the scarecrow (0 the one on its arm, then those a dialog brought, see [crows]), perched at
     * ([tx], [ty]), is off it, startled by a tap on the scarecrow ([pokes]): the first time it flaps up and drops back
     * (the one on the arm alone), the second they all fly off, wheel over the field and glide back, one after another.
     * Where it is then goes to [flyX], [flyY].
     */
    private fun crowUp(j: Int, tx: Int, ty: Int): Boolean {
        val pk = poked("scarecrow") ?: return false
        val k = detail
        val a = pk.age(t) - j * 0.15f
        if (pk.step == 0) {
            if (j > 0 || a < 0.1f || a > 1.5f) return false
            val v = (a - 0.1f) / 1.4f
            flyX = tx + (sin(v * PI.toFloat()) * 3f * k).toInt(); flyY = ty - (sin(v * PI.toFloat()) * 13f * k).toInt()
            return true
        }
        if (a < 0.05f || a > 6.6f) return false
        // off and up to the ring, once round it, and back in a glide
        val r = (20f + 4f * j) * k; val rx = tx + (14f + 5f * j) * k; val ry = ty - (44f + 6f * j) * k
        val sx = rx - r; val sy = ry.toFloat()
        val x: Float; val y: Float
        when {
            a < 1.1f -> { val e = PokeArt.ease(a, 0.05f, 1.1f); x = tx + (sx - tx) * e; y = ty + (sy - ty) * (1f - (1f - e) * (1f - e)) }
            a < 5.0f -> { val ang = PI.toFloat() * (1f + 2f * (a - 1.1f) / 3.9f); x = rx + cos(ang) * r; y = ry + sin(ang) * r * 0.45f }
            else -> { val e = PokeArt.ease(a, 5.0f, 6.6f); x = sx + (tx - sx) * e; y = sy + (ty - 2f * k - sy) * e * e }
        }
        flyX = x.toInt(); flyY = y.toInt()
        return true
    }

    /** A crow on the wing at ([x], [y]), its wings up or down in turn (the [j]th, out of step with the others). */
    private fun flyingCrow(x: Int, y: Int, j: Int) {
        val col = Col.hex(0x2A2A30)
        PokeArt.wings(c, x, y, ((t * 9).toInt() + j) and 1 == 0, detail, col)
    }

    /**
     * A crow sitting with its feet at ([cx], [cy]), facing right or, [flip]ped, left; closer up its body and folded
     * wing, the tail, head and beak, an eye, its feet.
     */
    private fun perchedCrow(cx: Int, cy: Int, flip: Boolean) {
        val k = detail
        val f = if (flip) -1 else 1
        val crowC = Col.hex(0x2A2A30)
        if (k == 1) { c.fillRect(cx - 1, cy - 2, 3, 2, crowC); c.set(cx + 2 * f, cy - 3, crowC); c.set(cx + 3 * f, cy - 3, Pal.GOLD); return }
        val wing = Col.hex(0x3E3E4A)
        c.fillEllipse(cx + 0.5f * k * f, cy - k.toFloat(), 1.6f * k, 1.0f * k, crowC)
        c.line(cx - k * f, cy - k, cx - 2 * k * f, cy, crowC); c.line(cx - k * f, cy - k + 1, cx - 2 * k * f, cy + 1, crowC)
        c.line(cx - k / 2 * f, cy - 2 * k + 1, cx + (k + k / 2) * f, cy - k - 1, wing)
        c.fillCircle(cx + 2.5f * k * f, cy - 2.4f * k, 0.8f * k, crowC)
        c.line(cx + 3 * k * f, cy - 2 * k - k / 2, cx + (4 * k - 1) * f, cy - 2 * k - k / 3, Pal.GOLD)
        c.set(cx + (2 * k + k / 2 + 1) * f, cy - 2 * k - k / 2 - 1, Col.hex(0xE8E8F0))
        c.vline(cx, cy, cy + k / 2, Col.hex(0x5A4A3A)); c.vline(cx + k * f, cy, cy + k / 2, Col.hex(0x5A4A3A))
    }

    /**
     * Crows coming to the scarecrow (a dialog's "crows"), no longer afraid of it: the level is how far they have come,
     * from high over the field (0) down onto its arm and hat and the ground at its feet (1), one after another.
     */
    private fun crows(level: Float) {
        val k = detail
        val d = 17f; val p = pm(d); val bx = gx(6.4f, d); val by = gy(d)
        // the one off the scarecrow's arm, startled by a tap
        if (crowUp(0, (bx + 0.55f * p).toInt(), (by - 1.8f * p).toInt())) flyingCrow(flyX, flyY, 0)
        if (level <= 0.01f) return
        // where each one lands, and which way it faces: the left arm, the hat, the ground on either side
        val perches = arrayOf(
            floatArrayOf(bx - 0.6f * p, by - 1.8f * p, 1f), floatArrayOf(bx + 0.05f * p, by - 2.38f * p, -1f),
            floatArrayOf(gx(5.3f, 16.4f), gy(16.4f), 1f), floatArrayOf(gx(7.5f, 16.7f), gy(16.7f), -1f),
        )
        val col = Col.hex(0x2A2A30)
        for ((j, pc) in perches.withIndex()) {
            val u = ((level - j * 0.08f) / 0.76f).coerceIn(0f, 1f)
            if (u <= 0f) continue
            val tx = pc[0].toInt(); val ty = pc[1].toInt()
            if (u >= 1f) {
                if (crowUp(j + 1, tx, ty)) { flyingCrow(flyX, flyY, j + 1); continue }
                val hop = if (sin(t * 2.1 + j * 1.9) > 0.8) k else 0
                perchedCrow(tx, ty - hop, flip = pc[2] < 0f)
                continue
            }
            // gliding in from high up on the side it faces from, slowing to land
            val e = 1f - (1f - u) * (1f - u)
            val sx = tx - pc[2] * (60f + j * 12f) * k; val sy = ty - (70f + j * 8f) * k
            val x = (sx + (tx - sx) * e).toInt(); val y = (sy + (ty - 3 * k - sy) * e).toInt()
            val flap = ((t * 8).toInt() + j) and 1
            sprite(x, y) {
                c.set(x, y, col); c.set(x + 1, y, col); c.set(x - 1, y, col)
                if (flap == 0) { c.set(x - 2, y - 1, col); c.set(x - 3, y - 2, col); c.set(x + 2, y - 1, col); c.set(x + 3, y - 2, col) }
                else { c.set(x - 2, y + 1, col); c.set(x - 3, y + 1, col); c.set(x + 2, y + 1, col); c.set(x + 3, y + 1, col) }
            }
        }
    }

    /** A scythe leaning on the fence, its blade curving out. */
    private fun scythe(x: Float, d: Float) {
        val k = detail
        val p = pm(d); val bx = gx(x, d); val by = gy(d)
        val tx = bx - 0.45f * p; val ty = by - 1.8f * p
        if (k == 1) {
            c.line(bx.toInt(), by.toInt(), tx.toInt(), ty.toInt(), Pal.WOOD_D); c.line(bx.toInt() + 1, by.toInt(), tx.toInt() + 1, ty.toInt(), Pal.WOOD_M)
            c.fillRect((bx - 0.3f * p).toInt(), (by - 1.1f * p).toInt(), max(2, (0.2f * p).toInt()), 2, Pal.WOOD_D)
            val n = (0.8f * p).toInt()
            for (i in 0..n) {
                val f = i / max(1f, n.toFloat())
                val sx = tx - i; val sy = ty - (sin(f * PI * 0.5) * 0.3f * p).toFloat() + f * 0.15f * p
                c.set(sx.toInt(), sy.toInt(), Pal.STONE_L); c.set(sx.toInt(), sy.toInt() + 1, Pal.STONE_M)
            }
            return
        }
        // the snath as thick as ever, its two grips; the blade tapering to its point, its edge bright
        for (i in 0 until 2 * k) c.line(bx.toInt() + i, by.toInt(), tx.toInt() + i, ty.toInt(), if (i < k) Pal.WOOD_D else if (i == k) Pal.WOOD_L else Pal.WOOD_M)
        for (up in floatArrayOf(1.1f, 1.5f)) {
            // a grip sticking out of the snath, lit on top (the lower one where detail 1 has it)
            val gl = (bx + (tx - bx) * (up / 1.8f) - 0.025f * p).toInt(); val gw = max(2, (0.2f * p).toInt())
            c.fillRect(gl, (by - up * p).toInt(), gw, 2 * k, Pal.WOOD_D)
            c.hline(gl, gl + gw - 1, (by - up * p).toInt(), Pal.WOOD_M)
        }
        val n = (0.8f * p).toInt()
        val edge = Col.hex(0xE8ECF0)
        for (i in 0..n) {
            val f = i / max(1f, n.toFloat())
            val sx = tx - i; val sy = ty - (sin(f * PI * 0.5) * 0.3f * p).toFloat() + f * 0.15f * p
            val th = max(1, ((1f - f) * 0.07f * p).toInt()) + 1
            c.set(sx.toInt(), sy.toInt(), edge)
            for (j in 1..th) c.set(sx.toInt(), sy.toInt() + j, if (j == th) Pal.STONE_D else Pal.STONE_M)
        }
        c.fillRect(tx.toInt() - 1, ty.toInt() - 1, 3, max(2, k), Col.hex(0x3A3640))
    }

    /** A willow basket among the cabbages, filled by the season. */
    private fun basket(x: Float, d: Float) {
        val k = detail
        val p = pm(d); val cx = gx(x, d); val by = gy(d)
        val hw = 0.3f * p; val hh = 0.33f * p
        for (y in (by - hh).toInt() until by.toInt()) {
            val u = (y - (by - hh)) / hh
            val half = hw * (1f - u * 0.2f)
            if (k == 1) c.hline((cx - half).toInt(), (cx + half).toInt(), y, if ((y + cx.toInt()) % 2 == 0) Pal.WOOD_L else Pal.WOOD_M)
            else {
                // the weave: withies over and under the stakes
                val row = (y - (by - hh).toInt()) / 2
                for (xx in (cx - half).toInt()..(cx + half).toInt()) {
                    val st = (xx - (cx - hw).toInt()) / 3
                    c.set(xx, y, if ((xx - (cx - hw).toInt()) % 3 == 0) Pal.WOOD_D else if ((row + st) % 2 == 0) Pal.WOOD_L else Pal.WOOD_M)
                }
            }
            c.set((cx - half).toInt(), y, Pal.WOOD_D); c.set((cx + half).toInt(), y, Pal.WOOD_D)
        }
        c.hline((cx - hw).toInt(), (cx + hw).toInt(), (by - hh).toInt() - 1, Pal.WOOD_D)
        val n = 12 * k * k
        for (a in 0..n) {
            val ang = PI * a / n
            c.set((cx + cos(ang) * hw * 0.8f).toInt(), (by - hh - sin(ang) * hh * 0.9f).toInt() - 1, Pal.WOOD_D)
            if (k > 1) c.set((cx + cos(ang) * (hw * 0.8f - 1)).toInt(), (by - hh - sin(ang) * (hh * 0.9f - 1)).toInt() - 1, Pal.WOOD_M)
        }
        when (env.season) {
            Season.WINTER -> for (i in 0 until k) c.hline((cx - hw * 0.8f).toInt(), (cx + hw * 0.8f).toInt(), (by - hh).toInt() - 2 * k + i, Pal.SNOW_L)
            Season.SPRING -> for (i in 0..2) {
                val sx = (cx - hw * 0.6f + i * hw * 0.6f).toInt(); val sy = (by - hh).toInt() - 2 * k
                if (k == 1) c.set(sx, sy, Col.hex(0x6AAA48))
                else { c.vline(sx, sy, sy + k, Col.hex(0x6AAA48)); c.set(sx - 1, sy, Col.hex(0x8CC25A)); c.set(sx + 1, sy - 1, Col.hex(0x8CC25A)) }
            }
            else -> {
                c.fillCircle(cx - hw * 0.35f, by - hh - 1f * k, hw * 0.4f, Col.hex(0x9ACCB0))
                c.fillEllipse(cx + hw * 0.35f, by - hh - 0.5f * k, hw * 0.35f, hw * 0.25f, Col.hex(0xC4956A))
                if (k > 1) {
                    c.line((cx - hw * 0.55f).toInt(), (by - hh - 1f * k).toInt(), (cx - hw * 0.2f).toInt(), (by - hh - 1f * k - hw * 0.25f).toInt(), Col.hex(0x6AA88E))
                    c.set((cx + hw * 0.25f).toInt(), (by - hh - 0.5f * k - hw * 0.1f).toInt(), Col.hex(0xE0B88A))
                }
            }
        }
    }

    /** A crow flying over the fields by day. */
    private fun crow() {
        if (env.dark > 0.6f) return
        val k = detail
        val flap = ((t * 7).toInt() and 1)
        val col = Col.hex(0x2A2A30)
        c.penEmissive = true
        if (k == 1) {
            val x = ox + 240 - ((t * 14) % 300).toInt(); val y = oy + 26 + (sin(t * 2.5) * 3).toInt()
            c.set(x, y, col); c.set(x + 1, y, col); c.set(x - 1, y, col)
            if (flap == 0) { c.set(x - 2, y - 1, col); c.set(x - 3, y - 2, col); c.set(x + 2, y - 1, col); c.set(x + 3, y - 2, col) }
            else { c.set(x - 2, y + 1, col); c.set(x - 3, y + 1, col); c.set(x + 2, y + 1, col); c.set(x + 3, y + 1, col) }
        } else {
            // closer up: a body and a head, wings with their fingered tips, a fanned tail
            val x = ox + ((240 - (t * 14) % 300) * k).toInt(); val y = oy + ((26 + sin(t * 2.5) * 3) * k).toInt()
            val wingC = Col.hex(0x3A3A46)
            c.fillEllipse(x + 0.5f, y + 0.5f, 1.6f * k, 0.55f * k, col)
            c.fillCircle(x - 1.4f * k, y - 0.1f * k, 0.5f * k, col)
            c.set((x - 2.0f * k).toInt(), y, Col.hex(0x4A4A52))
            c.line(x + k, y, x + 2 * k, y - k / 2, col); c.line(x + k, y, x + 2 * k, y + k / 2, col)
            for (side in intArrayOf(-1, 1)) {
                val tipY = if (flap == 0) y - 2 * k else y + k
                val tipX = x + side * 3 * k
                thick(x.toFloat(), y.toFloat(), (x + side * 1.6f * k), (if (flap == 0) y - k else y).toFloat(), 2, col)
                c.line(x + side * (1.6f * k).toInt(), if (flap == 0) y - k else y, tipX, tipY, wingC)
                c.line(x + side * (1.6f * k).toInt(), (if (flap == 0) y - k else y) + 1, tipX, tipY + 1, col)
                c.set(tipX + side, tipY + (if (flap == 0) -1 else 0), wingC)
            }
        }
        c.penEmissive = false
    }
}
