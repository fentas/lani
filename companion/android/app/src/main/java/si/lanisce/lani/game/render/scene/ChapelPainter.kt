package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.Season
import si.lanisce.lani.game.scene.Poke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pri kapelici: the wayside chapel by the road among the fields (the project `kapelica`, Teta Ančka's), a painted vista
 * (companion/SCENES.md "Arts"). The road comes up from our feet on the left and runs on past the chapel, over the fields
 * toward the hills; on its left the wheat (green in spring, gold at the harvest, stubble, then the brown furrows and snow), on
 * its right the meadow. The chapel stands on the right: whitewashed walls on a stone plinth, a roof of wooden shingles (skodle),
 * on the ridge over its front a little open turret with its bell and the cross on its top; in the round-arched front a
 * wrought-iron grille, and through it the little altar with its embroidered cloth (white, a red border), a candle and vases
 * of flowers on it, the painting of the Virgin in her blue cloak over it; on the stone step in front two red glass candles
 * and a pot of flowers. Left of it under the linden a bench by the road. Far off the hills, a wooded one, and the village with
 * its church's spire.
 *
 * Over the day and the year: the light of the hour on the whitewash, the linden in flower in June and July and gold in
 * October, the swallows over the wheat in summer, the candles glowing at night; the weather of the vista (rain, fog in the
 * fields, snow).
 *
 * Its effects ([si.lanisce.lani.game.scene.SceneArt.effects]):
 * - `bell`: the little bell swinging in its turret and ringing (how hard), its rings going out over the fields.
 * - `candles`: 0 all out, 1 all lit (the two red glass candles on the step and the one on the altar), one more lit with
 *   every third. Not set: one red one burns.
 * - `flowers`: the flowers in the vases and the pot: 0 wilted, brown and drooping; 1 fresh in the season's colours, fading
 *   and drooping more the lower. Not set: fresh.
 * - `traveller`: a traveller with his staff and his hat walking up the road from our feet past the chapel and on toward the
 *   hills (how far: 1 gone over the rise).
 *
 * Tapped ([pokes]): the bell swings and rings, then twice.
 *
 * Closer up ([detail] 2 and 3): the shingles' courses, the plaster's texture, the grille's scrolls, the cloth's embroidery,
 * the painting's face and halo, the wheat's ears, the road's stones.
 *
 * Friuli's look ([look]; companion/SCENES.md "Culture looks"): an ancona: its walls ochre, its roof of coppi, no turret but
 * the bell on an iron bracket under the gable; inside a fresco of the Madonna and Child on a blue ground with stars; maize in
 * the field instead of wheat, Lombardy poplars along the far ditch, the village on the hill as Friuli builds it, with its
 * campanile.
 */
internal class ChapelPainter : VistaPainter() {
    override val art = "chapel"
    override val pokes = listOf(Poke("bell", listOf(2.2, 3.0), rest = 3.0))

    /** South-west over the fields toward the hills. */
    override val skyFacing = 220.0

    // the chapel: its front at depth CD, from side CX0 to CX1, its walls WH high, its gable's apex AH; the arch in its front
    private val cd = 15.5f; private val cdb = 18.1f; private val cx0 = 2.0f; private val cx1 = 4.8f
    private val wallH = 3.0f; private val apexH = 5.0f
    private val ax0 = 2.75f; private val ax1 = 4.05f; private val archH = 2.55f
    private val cmid: Float get() = (cx0 + cx1) / 2f
    // the bench left of it, the linden behind the bench
    private val benchX0 = 0.15f; private val benchX1 = 1.55f; private val benchD = 14.7f
    private val treeX = 0.6f; private val treeD = 19.5f

    // the people: by the chapel's front on its right, on the bench, on the road
    private val chapelPX = 5.75f; private val chapelPD = 14.4f
    private val benchPX = 0.85f; private val benchPD = 14.5f
    private val roadPX = -2.7f; private val roadPD = 11.4f

    /** The road's middle at depth [d] (m): from the lower left up past the chapel and away to the right. */
    private fun road(d: Float): Float = -3.4f + (d - 8f) * 0.23f + 2.2f * sin((d - 8f) * 0.035f)
    private val roadHalf = 1.5f

    private val wallC = Col.hex(0xF2EADA); private val wallL = Col.hex(0xFCF8EE); private val wallD = Col.hex(0xCFC4AE)
    private val shingleL = Col.hex(0x8E7660); private val shingleM = Col.hex(0x6E5A48); private val shingleD = Col.hex(0x4E4034)
    private val ironC = Col.hex(0x2A2628); private val ironL = Col.hex(0x5A5658)
    private val bellC = Col.hex(0xC8962E); private val bellL = Col.hex(0xF0CA62); private val bellD = Col.hex(0x8A6420)
    private val cloth = Col.hex(0xFAF6EC); private val clothBorder = Col.hex(0xC0302A)
    private val glassRed = Col.hex(0xB0202A)

    /** Friuli's look ([readLook2]): the ancona, the maize, the Friulian village, the poplars. */
    private var ancona = false
    private var maize = false
    private var friuliVillage = false

    private val z: Int get() = detail

    private val items = ArrayList<Pair<Float, () -> Unit>>()

    private fun readLook2() {
        ancona = variant("chapel") == "ancona"
        maize = variant("field") == "maize"
        friuliVillage = variant("village") != null
    }

    /** How hard the bell swings: the effect, else a tap's ring. */
    private fun bellSwing(): Float {
        val e = fxOn("bell")
        val pk = poked("bell") ?: return e
        val a = pk.age(t)
        val dur = if (pk.step == 0) 2.2f else 3.0f
        return max(e, (1f - a / dur).coerceIn(0f, 1f) * (if (pk.step == 0) 0.7f else 1f))
    }

    override fun paint() {
        setup()
        readLook2()
        val z = z
        vistaSky(4, 61, moonX = ox + 50 * z, moonY = oy + 12 * z)
        // far: the hills layer on layer, the wooded hill, the village on its slope
        val far = ridge(hz.toFloat() - 4f * z, 14f * z, 0.012f, 611, Col.hex(0x6E8AA4), 0.55f)
        treeline(far, 5 * z, 2.2f * z, Col.hex(0x5A7690), 0.5f, 612)
        thing("hills", outline = 0) {
            val mid = ridge(hz.toFloat() + 1f * z, 10f * z, 0.02f, 613, Col.hex(0x5E8A58), 0.3f)
            treeline(mid, 4 * z, 2.6f * z, Col.hex(0x3E6A3E), 0.28f, 614, 0, (ox + 130 * z))
            village(mid)
        }
        if (friuliVillage) prop(0) { poplars() }
        // the ground: the field, the road, the meadow
        c.penId = 0
        for (py in max(hz, c.top) until min(h, c.bottom)) {
            val d = depthAt(py)
            for (px in max(0, c.left) until min(w, c.right)) c.set(px, py, land(sideAt(px, d), d, px, py))
        }
        thing("field") { fieldPixels() }
        thing("road") { roadPixels() }

        items.clear()
        items.add(treeD to { groundShadow(treeX, treeD, 2.4f); thing("linden", outline = Pal.OUTLINE_TREE) { linden() } })
        items.add(cdb to { chapel() })
        items.add(benchD to { groundShadow((benchX0 + benchX1) / 2f, benchD, 0.9f); thing("bench") { bench() } })
        for (p in peopleAt("bench")) items.add(benchPD - 0.05f to { person(p, gx(benchPX, benchPD).toInt(), gy(benchPD).toInt() - (0.42f * pm(benchPD)).toInt(), flip = false, seated = true) })
        for (p in peopleAt("chapel")) items.add(chapelPD to { groundShadow(chapelPX, chapelPD, 0.5f); person(p, gx(chapelPX, chapelPD).toInt(), gy(chapelPD).toInt(), flip = true) })
        for (p in peopleAt("road")) items.add(roadPD to { groundShadow(roadPX, roadPD, 0.5f); person(p, gx(roadPX, roadPD).toInt(), gy(roadPD).toInt(), flip = false) })
        fx("traveller")?.let { tr -> if (tr > 0f && tr < 1f) { val d = 9.5f + 70f * tr * tr; items.add(d to { prop { traveller(d, tr) } }) } }
        items.sortByDescending { it.first }
        for ((_, draw) in items) draw()

        foregroundGrass(0, w, 61)
        swallows()
        bellRings(bellSwing())
        fireflies(0, hz + 14 * z, w, h, 10, 63)
        s.fx { s.effects.leaves(env.season, env.month) }
        lights()
    }

    // ------------------------------------------------------------------ far away

    /** The village on the wooded hill's slope at the right: houses with red roofs (Friuli's coppi and campanile), the church's spire. */
    private fun village(crest: IntArray) {
        val z = z
        val lit = env.windows > 0.35f
        if (friuliVillage) {
            val st = FireStyle.of(frame.world.culture, frame.world.landscape)
            for (i in 0 until 5) {
                val hx = ox + (168 + i * 9) * z
                if (hx < 0 || hx >= w) continue
                farCoppiHouse(hx, crest[hx] + 6 * z + (i % 2) * z, 5 * z, 3 * z, st, i, lit, 0.3f)
            }
            val cx = ox + 186 * z
            if (cx in 0 until w) farCampanile(cx, crest[cx] + 5 * z, st, lit, 0.3f, tall = 13)
            return
        }
        val wall = env.lit(Col.mix(Pal.WALL_L, env.skyHorizon, 0.3f)); val wallDk = env.lit(Col.mix(Pal.WALL_D, env.skyHorizon, 0.3f))
        val roof = env.lit(Col.mix(Pal.ROOF_M, env.skyHorizon, 0.28f)); val roofDk = env.lit(Col.mix(Pal.ROOF_D, env.skyHorizon, 0.28f))
        c.penEmissive = true
        for (i in 0 until 5) {
            val hx = ox + (166 + i * 9) * z
            if (hx < 0 || hx + 6 * z >= w) continue
            val base = crest[hx + 2 * z] + 6 * z + (i % 2) * z
            c.fillRect(hx, base - 3 * z, 5 * z, 3 * z, wall)
            c.fillRect(hx + 4 * z, base - 3 * z, z, 3 * z, wallDk)
            for (r in 0 until 2 * z) c.hline(hx - z / 2 + r / 2, hx + 5 * z + z / 2 - r / 2 - 1, base - 3 * z - 1 - r, if (r < z / 2 + 1) roofDk else roof)
            c.fillRect(hx + z, base - 2 * z, max(1, z), max(1, z), if (lit && i % 2 == 0) Pal.WINDOW_LIT else env.lit(Pal.GLASS))
        }
        // the church: its nave and the tower with its pointed spire
        val cx = ox + 190 * z
        if (cx + 6 * z < w) {
            val base = crest[cx] + 5 * z
            c.fillRect(cx, base - 4 * z, 7 * z, 4 * z, wall)
            for (r in 0 until 2 * z) c.hline(cx - z / 2 + r / 2, cx + 7 * z + z / 2 - r / 2 - 1, base - 4 * z - 1 - r, if (r < z / 2 + 1) roofDk else roof)
            val tl = cx - 3 * z
            c.fillRect(tl, base - 11 * z, 3 * z, 11 * z, wall)
            c.fillRect(tl + 2 * z, base - 11 * z, z, 11 * z, wallDk)
            c.fillRect(tl + z, base - 9 * z, max(1, z), max(1, z), env.lit(Col.hex(0x2A2226)))
            for (r in 0 until 5 * z) {
                val half = (1.5f * z + z * 0.3f) * (1f - r / (5f * z))
                c.hline((tl + 1.5f * z - half).toInt(), (tl + 1.5f * z + half).toInt() - 1, base - 11 * z - 1 - r, if (r % (2 * z) < z) roofDk else roof)
            }
            c.vline(tl + (1.5f * z).toInt(), base - 18 * z, base - 16 * z, Pal.GOLD)
        }
        c.penEmissive = false
    }

    /** Lombardy poplars along a far ditch across the fields (Friuli's look). */
    private fun poplars() {
        for (k in 0 until 7) poplar(-26f + k * 4.5f + Noise.rnd(k, 621) * 1.5f, 120f + Noise.rnd(k, 622) * 20f, 16f + Noise.rnd(k, 623) * 5f, 620 + k)
    }

    // ------------------------------------------------------------------ the ground

    /** Whether side [x] at depth [d] is on the road. */
    private fun onRoad(x: Float, d: Float): Boolean = abs(x - road(d)) < roadHalf * (1f + d / 120f) && d < 150f

    /** Whether side [x] at depth [d] is in the field left of the road. */
    private fun inField(x: Float, d: Float): Boolean = x < road(d) - roadHalf - 0.6f && d > 8.5f && d < 160f && x > -22f - d * 0.2f

    /** The land far off a little paler: the meadows and fields toward the hills, never as pale as the sky (it read as water). */
    private fun farLand(col: Int, d: Float): Int = Col.mix(col, env.skyHorizon, ((d - 40f) / 700f).coerceIn(0f, 0.35f))

    /** The meadow by the road, and far off the patchwork of fields and meadows toward the hills; snow in winter. */
    private fun land(x: Float, d: Float, px: Int, py: Int): Int {
        val g = env.grass
        val n = Noise.v2(x * 0.5f, d * 0.5f, 631) + (Dither.at(px, py) - 0.5f) * 0.25f
        var col = if (n > 0.62f) g[0] else if (n > 0.32f) g[1] else g[2]
        if (d > 60f) {
            // the fields beyond: strips of every crop, hedges between them
            val cx = floor(x / (18f + d * 0.05f) + Noise.v1(d / 70f, 633) * 0.7f).toInt(); val cd = floor(d / 45f).toInt()
            val kind = (Noise.rnd(cx, cd, 634) * 5f).toInt()
            col = when {
                env.snow -> if (kind == 2) Pal.SNOW_M else Pal.SNOW_L
                env.season == Season.AUTUMN -> when (kind) { 0 -> Col.hex(0x8A6A44); 1 -> Col.hex(0x7E8A48); 2 -> Col.hex(0xA88A4E); else -> g[1] }
                env.month in 6..7 -> when (kind) { 0 -> Col.hex(0xD8B850); 1 -> Col.hex(0x6AA048); 2 -> Col.hex(0xC8A858); else -> g[1] }
                else -> when (kind) { 0 -> Col.hex(0x7AB050); 1 -> Col.hex(0x5E9A42); 2 -> Col.hex(0x9A7A52); else -> g[0] }
            }
            val fx = x / (18f + d * 0.05f) + Noise.v1(d / 70f, 633) * 0.7f
            if (fx - floor(fx) < 0.05f && !env.snow) col = Col.scale(col, 0.75f)
        }
        if (!env.snow && env.month in 4..8 && d < 26f && Noise.rnd(px, py, 632) < 0.014f) col = Pal.PAINTED[(px * 3 + py) % 6]
        return farLand(col, d)
    }

    /** The wheat (or Friuli's maize) left of the road, by the month: rows running away from us. */
    private fun fieldPixels() {
        for (py in max(hz, c.top) until min(h, c.bottom)) {
            val d = depthAt(py)
            for (px in max(0, c.left) until min(w, c.right)) {
                val x = sideAt(px, d)
                if (!inField(x, d)) continue
                c.set(px, py, farLand(crop(x, d, px, py), d))
            }
        }
        // near the road the stalks stand up out of it: a fringe of ears along the field's edge
        if (!env.snow) for (px in max(0, c.left) until min(w, c.right)) {
            for (dd in floatArrayOf(9f, 10.5f, 12f, 14f, 17f, 21f)) {
                val x = sideAt(px, dd)
                if (abs(x - (road(dd) - roadHalf - 0.6f)) > 0.08f) continue
                val tall = (if (maize) 1.8f else 0.9f) * pm(dd)
                val top = gy(dd) - tall
                for (yy in top.toInt() until gy(dd).toInt()) c.set(px, yy, farLand(crop(x, dd, px, yy), dd))
            }
        }
    }

    private fun crop(x: Float, d: Float, px: Int, py: Int): Int {
        val m = env.month
        val ph = rowPhase(x, d, if (maize) 0.75f else 0.25f)
        val row = ph >= 0f && ph < 0.4f
        val sway = sin(t * 1.4 + d * 0.3 + x).toFloat() * 0.5f * gust
        if (maize) return when (m) {
            in 6..8 -> if (row) Col.hex(0x3E7A30) else if (Dither.at(px, py) < 0.5f) Col.hex(0x5E9A3E) else Col.hex(0x4E8A34)
            9, 10 -> if (row) Col.hex(0xB8963E) else Col.hex(0xD8B460)
            in 4..5 -> if (row) Col.hex(0x6AA448) else Col.hex(0x8A6A48)
            else -> if (env.snow) Pal.SNOW_L else if (row) Col.hex(0x7A6048) else Col.hex(0x8E7258)
        }
        return when (m) {
            in 3..5 -> if (row) Col.hex(0x5E9A3E) else if (Dither.at(px, py) + sway * 0.1f < 0.5f) Col.hex(0x78B44E) else Col.hex(0x6AA846)
            6, 7 -> if (Noise.rnd(px, py, 641) < 0.18f) Col.hex(0xF0D070) else if (row) Col.hex(0xC8A040) else Col.hex(0xE0B850)
            8 -> if (row) Col.hex(0xC8B070) else Col.hex(0xD8C488)
            9, 10, 11 -> if (row) Col.hex(0x6E4E30) else Col.hex(0x86603C)
            else -> if (env.snow) (if (row && Dither.at(px, py) < 0.3f) Pal.SNOW_M else Pal.SNOW_L) else Col.hex(0x7A5A3C)
        }
    }

    /** The road: gravel, its two wheel ruts, grass down its middle; puddles in the rain, snow trodden in winter. */
    private fun roadPixels() {
        for (py in max(hz, c.top) until min(h, c.bottom)) {
            val d = depthAt(py)
            if (d > 150f) continue
            for (px in max(0, c.left) until min(w, c.right)) {
                val x = sideAt(px, d)
                if (!onRoad(x, d)) continue
                val u = (x - road(d)) / (roadHalf * (1f + d / 120f))
                val n = Noise.rnd(px / max(1, z), py / max(1, z), 651)
                var col = when {
                    env.snow -> if (abs(abs(u) - 0.5f) < 0.18f) Pal.SNOW_D else Pal.SNOW_L
                    abs(u) < 0.14f -> if (n < 0.5f) env.grass[1] else env.grass[2]
                    abs(abs(u) - 0.5f) < 0.2f -> if (n < 0.3f) Pal.DIRT_D else Pal.DIRT_M
                    n < 0.15f -> Pal.STONE_M
                    else -> Pal.DIRT_L
                }
                if (frame.sky.rain > 0.3f && abs(abs(u) - 0.5f) < 0.15f && Noise.v2(x * 2f, d * 0.8f, 653) > 0.55f) col = Col.mix(skyAt(0.5f), Col.hex(0x6E7684), 0.4f)
                c.set(px, py, farLand(col, d))
            }
        }
    }

    // ------------------------------------------------------------------ the chapel

    /** The chapel, back to front: its left side and its roof, the front and the gable, what is in the arch, the step, the turret. */
    private fun chapel() {
        groundShadow(cmid + 0.6f, cdb, 2.6f, 0.8f)
        thing("chapel") {
            sideWall()
            front()
        }
        thing("roof") { roof() }
        thing("altar") { altar() }
        thing("picture") { picture() }
        thing("cloth") { altarCloth() }
        thing("grille") { grille() }
        prop { step() }
        thing("candle", slop = 1) { candles() }
        thing("flowers", slop = 1) { flowers() }
        thing("bell", slop = 2) { bell() }
        thing("cross", slop = 1) { cross() }
    }

    /** A wall colour at height [hm] (m): the plinth's stone, whitewash (ochre for an ancona), [dim] its shade. */
    private fun plaster(hm: Float, px: Int, py: Int, dim: Float): Int {
        val n = Noise.rnd(px / max(1, z), py / max(1, z), 661)
        val base = if (ancona) Pal.OCHRE_M else wallC
        val col = when {
            hm < 0.45f -> if (n < 0.3f) Pal.STONE_D else if (n < 0.85f) Pal.STONE_M else Pal.STONE_L
            hm < 0.55f -> Pal.STONE_L
            n < 0.05f -> if (ancona) Pal.OCHRE_L else wallL
            n > 0.95f -> if (ancona) Pal.OCHRE_D else wallD
            else -> base
        }
        return env.lit(Col.scale(col, dim))
    }

    /** The left side wall, receding from the front to the back (the side we see, toward the road). */
    private fun sideWall() {
        val xa = gx(cx0, cd); val xb = gx(cx0, cdb)
        c.polyBegin()
        c.polyAdd(xa, hy(cd, 0f)); c.polyAdd(xb, hy(cdb, 0f)); c.polyAdd(xb, hy(cdb, wallH)); c.polyAdd(xa, hy(cd, wallH))
        c.polyScan { px, py ->
            // the depth at this column on the wall's plane, and the height there
            val d = F * cx0 / max(0.01f, px + 0.5f - vx)
            val hm = EYE - (py + 0.5f - hz) * d / F
            c.set(px, py, plaster(hm, px, py, 0.8f))
        }
    }

    /** The front: its wall with the round-arched opening, the gable over it; the bargeboards. */
    private fun front() {
        val xl = gx(cx0, cd); val xr = gx(cx1, cd)
        val p = pm(cd)
        val ground = gy(cd)
        // the wall and the gable
        for (py in max(hy(cd, apexH).toInt(), c.top) until min(ground.toInt(), c.bottom)) {
            val hm = (ground - py - 0.5f) / p
            val half = if (hm > wallH) (cx1 - cx0) / 2f * (1f - (hm - wallH) / (apexH - wallH)) else (cx1 - cx0) / 2f
            for (px in max(xl.toInt(), c.left)..min(xr.toInt(), c.right - 1)) {
                val x = sideAt(px, cd)
                if (abs(x - cmid) > half) continue
                if (inArch(x, hm)) continue
                var col = plaster(hm, px, py, 1f)
                // the arch's stone frame, and a painted band under the gable
                if (archFrame(x, hm)) col = env.lit(if (ancona) Col.hex(0xE8D8B8) else Pal.STONE_L)
                if (abs(hm - wallH - 0.08f) < 0.06f) col = env.lit(if (ancona) Pal.OCHRE_D else wallD)
                c.set(px, py, col)
            }
        }
    }

    /** Whether ([x] m, [hm] m up) of the front is in the arch's opening (round at its top). */
    private fun inArch(x: Float, hm: Float): Boolean {
        val r = (ax1 - ax0) / 2f
        val mx = (ax0 + ax1) / 2f
        if (hm < 0.5f || abs(x - mx) > r) return false
        val spring = archH - r
        return hm < spring || (hm - spring) * (hm - spring) + (x - mx) * (x - mx) < r * r
    }

    private fun archFrame(x: Float, hm: Float): Boolean {
        val r = (ax1 - ax0) / 2f + 0.14f
        val mx = (ax0 + ax1) / 2f
        if (hm < 0.5f || abs(x - mx) > r) return false
        val spring = archH - r + 0.14f
        return hm < spring + 0.02f || (hm - spring) * (hm - spring) + (x - mx) * (x - mx) < r * r
    }

    /**
     * The roof: the left slope of shingles in courses (coppi in channels for an ancona) seen from above, the front's
     * bargeboards; snow on it in winter.
     */
    private fun roof() {
        val over = 0.25f
        val ea = cx0 - over; val eb = cx1 + over
        val d0 = cd - 0.3f; val d1 = cdb + 0.2f
        val eaveH = wallH - 0.15f
        // the left slope: from the eave on the left up to the ridge
        c.polyBegin()
        c.polyAdd(gx(ea, d0), hy(d0, eaveH)); c.polyAdd(gx(cmid, d0), hy(d0, apexH + 0.15f))
        c.polyAdd(gx(cmid, d1), hy(d1, apexH + 0.15f)); c.polyAdd(gx(ea, d1), hy(d1, eaveH))
        val sx = gx(ea, d0); val ex = gx(cmid, d0)
        c.polyScan { px, py ->
            // how far up the slope (0 eave … 1 ridge), roughly by the column at the front
            val u = ((px + 0.5f - sx) / max(1f, ex - sx)).coerceIn(0f, 1f)
            c.set(px, py, roofCol(u, px, py, 0.9f))
        }
        // the front's bargeboards along the gable, the ridge's edge
        val apex = hy(d0, apexH + 0.2f)
        val bl = if (ancona) Pal.ROOF_D else shingleD
        for (k in 0 until max(1, z)) {
            c.line(gx(ea, d0).toInt(), hy(d0, eaveH).toInt() + k, gx(cmid, d0).toInt(), apex.toInt() + k, env.lit(bl))
            c.line(gx(cmid, d0).toInt(), apex.toInt() + k, gx(eb, d0).toInt(), hy(d0, eaveH).toInt() + k, env.lit(bl))
        }
        if (!ancona) c.line(gx(cmid, d0).toInt(), apex.toInt(), gx(cmid, d1).toInt(), hy(d1, apexH + 0.2f).toInt(), env.lit(shingleL))
    }

    private fun roofCol(u: Float, px: Int, py: Int, dim: Float): Int {
        if (env.snow) return env.lit(if (Dither.at(px, py) < 0.2f) Pal.SNOW_M else Pal.SNOW_L)
        val courses = 7f
        val k = floor(u * courses).toInt(); val f = u * courses - k
        val col = if (ancona) {
            // coppi: channels down the slope, light and dark in turn
            if (((px / max(1, z)) + k) % 3 == 0) Pal.ROOF_D else if (f < 0.2f) Pal.ROOF_L else Pal.ROOF_M
        } else when {
            f < 0.22f -> shingleD
            Noise.rnd(px / (2 * max(1, z)), k, 671) < 0.35f -> shingleL
            else -> shingleM
        }
        return env.lit(Col.scale(col, dim))
    }

    // ------------------------------------------------------------------ in the arch

    /** The arch's inside: the dark back wall, the floor; drawn where the front left its opening. */
    private fun interior(px: Int, py: Int): Boolean {
        val x = sideAt(px, cd)
        val hm = (gy(cd) - py - 0.5f) / pm(cd)
        return inArch(x, hm)
    }

    /** The little altar against the back wall: a stone table under its cloth (the cloth is its own thing). */
    private fun altar() {
        val dB = cdb - 0.3f
        val xa = gx(cmid - 0.55f, dB); val xb = gx(cmid + 0.55f, dB)
        val top = hy(dB, 0.95f); val bot = gy(dB)
        // the dim inside first: the back wall, warm in the light of the candles
        for (py in max(hy(cd, archH).toInt(), c.top) until min(gy(cd).toInt(), c.bottom)) for (px in max(gx(ax0, cd).toInt(), c.left)..min(gx(ax1, cd).toInt(), c.right - 1)) {
            if (!interior(px, py)) continue
            val inside = if (ancona) Col.hex(0x3A4A86) else Col.hex(0x5A5048)
            c.set(px, py, env.lit(if (py > bot) Col.hex(0x6A6058) else if (Dither.at(px, py) < 0.2f) Col.scale(inside, 0.9f) else inside))
        }
        // the Friulian fresco's stars on its blue
        if (ancona) for (k in 0 until 8) {
            val sx = gx(cmid + (Noise.rnd(k, 681) - 0.5f) * 1.2f, cdb - 0.25f); val sy = hy(cdb - 0.25f, 1.2f + Noise.rnd(k, 682) * 1.2f)
            if (interior(sx.toInt(), sy.toInt())) c.set(sx.toInt(), sy.toInt(), Col.hex(0xF0D060))
        }
        c.fillRect(xa.toInt(), top.toInt(), max(1, (xb - xa).toInt()), max(1, (bot - top).toInt()), env.lit(Pal.STONE_L))
    }

    /** The embroidered cloth on the altar: white, its border of red stitches, hanging down its front. */
    private fun altarCloth() {
        val dB = cdb - 0.32f
        val xa = gx(cmid - 0.58f, dB).toInt(); val xb = gx(cmid + 0.58f, dB).toInt()
        val top = hy(dB, 0.98f).toInt(); val hang = hy(dB, 0.55f).toInt()
        for (py in max(top, c.top)..min(hang, c.bottom - 1)) for (px in max(xa, c.left)..min(xb, c.right - 1)) {
            val edge = py >= hang - max(1, z) || (z > 1 && py == hang - 2 * z)
            c.set(px, py, env.lit(if (edge) (if (z > 1 && (px / max(1, z / 2)) % 2 == 0) Pal.GOLD else clothBorder) else cloth))
        }
        // its lace points along the hem, closer up
        if (z > 1) for (px in max(xa, c.left)..min(xb, c.right - 1)) if ((px - xa) % (2 * z) < z) c.set(px, hang + 1, env.lit(cloth))
    }

    /** The painting over the altar: the Virgin in her blue cloak, a halo (in Friuli the Madonna and Child on blue with stars). */
    private fun picture() {
        val dB = cdb - 0.25f
        val xa = gx(cmid - 0.32f, dB); val xb = gx(cmid + 0.32f, dB)
        val ta = hy(dB, 2.25f); val tb = hy(dB, 1.15f)
        for (py in max(ta.toInt(), c.top)..min(tb.toInt(), c.bottom - 1)) for (px in max(xa.toInt(), c.left)..min(xb.toInt(), c.right - 1)) {
            val u = (px + 0.5f - xa) / max(1f, xb - xa); val v = (py + 0.5f - ta) / max(1f, tb - ta)
            val frame = u < 0.1f || u > 0.9f || v < 0.08f || v > 0.92f
            val col = when {
                frame -> Pal.GOLD
                // the head and its halo, the face, the cloak widening down, the child in her arms (Friuli)
                (u - 0.5f) * (u - 0.5f) * 30f + (v - 0.28f) * (v - 0.28f) * 60f < 1f -> if ((u - 0.5f) * (u - 0.5f) * 90f + (v - 0.3f) * (v - 0.3f) * 160f < 1f) Pal.SKIN else Pal.GOLD_L
                abs(u - 0.5f) < 0.12f + (v - 0.4f) * 0.45f && v > 0.4f -> if (ancona && abs(u - 0.42f) < 0.07f && v in 0.5f..0.66f) Pal.SKIN else if (abs(u - 0.5f) < 0.05f && v > 0.5f) Col.hex(0xC03A3A) else Col.hex(0x2E5AA8)
                else -> if (ancona) Col.hex(0x3A4A86) else Col.hex(0xE8D8A8)
            }
            c.set(px, py, env.lit(col))
        }
    }

    /** The wrought-iron grille in the arch: bars, a rail across, scrolls on the top rail. */
    private fun grille() {
        val p = pm(cd)
        val ground = gy(cd)
        val gTop = 1.35f
        for (py in max(hy(cd, archH).toInt(), c.top) until min(ground.toInt(), c.bottom)) {
            val hm = (ground - py - 0.5f) / p
            for (px in max(gx(ax0, cd).toInt(), c.left)..min(gx(ax1, cd).toInt(), c.right - 1)) {
                val x = sideAt(px, cd)
                if (!inArch(x, hm)) continue
                val bar = ((x - ax0) / 0.22f) % 1f < 0.5f / p * 2f
                val rail = abs(hm - gTop) < 0.6f / p || abs(hm - 0.6f) < 0.6f / p
                // scrolls over the rail: little curls between the bars
                val curl = hm in gTop..gTop + 0.25f && abs(sqrt(((x - ax0) % 0.22f - 0.11f).let { it * it } + (hm - gTop - 0.12f) * (hm - gTop - 0.12f)) - 0.08f) < 0.6f / p
                if ((bar && hm < gTop + 0.02f) || rail || curl) c.set(px, py, env.lit(if (Dither.at(px, py) < 0.2f) ironL else ironC))
            }
        }
    }

    /** The stone step in front of the arch. */
    private fun step() {
        val d0 = cd - 0.45f
        val xa = gx(ax0 - 0.35f, d0); val xb = gx(ax1 + 0.35f, d0)
        c.fillRect(xa.toInt(), hy(d0, 0.28f).toInt(), max(1, (xb - xa).toInt()), max(1, (gy(d0) - hy(d0, 0.28f)).toInt()), env.lit(Pal.STONE_M))
        c.hline(xa.toInt(), xb.toInt(), hy(d0, 0.28f).toInt(), env.lit(Pal.STONE_L))
        if (env.snow) c.hline(xa.toInt(), xb.toInt(), hy(d0, 0.3f).toInt() - 1, Pal.SNOW_L)
    }

    /** How many of the candles burn: `candles` (0 none, 1 all three), else one red one. */
    private fun litCandles(): Int = fx("candles")?.let { (it * 3f + 0.5f).toInt().coerceIn(0, 3) } ?: 1

    /** The two red glass candles on the step and the white one on the altar, their flames as `candles` has them. */
    private fun candles() {
        val n = litCandles()
        val d0 = cd - 0.4f
        for ((k, cxm) in floatArrayOf(ax0 + 0.05f, ax1 - 0.05f).withIndex()) {
            val cx = gx(cxm, d0); val base = hy(d0, 0.28f)
            val hh = 0.24f * pm(d0); val r = max(1f, 0.08f * pm(d0))
            if (k < n) {
                // a lit lumino glows through its red glass, brightest round the flame inside
                glow {
                    c.fillRect((cx - r).toInt(), (base - hh).toInt(), max(1, (2 * r).toInt()), max(1, hh.toInt()), Col.hex(0xE0503A))
                    c.fillRect((cx - r * 0.5f).toInt(), (base - hh * 0.75f).toInt(), max(1, r.toInt()), max(1, (hh * 0.5f).toInt()), Col.hex(0xF8A060))
                }
                flame(cx, base - hh, 0.6f, k)
            } else {
                c.fillRect((cx - r).toInt(), (base - hh).toInt(), max(1, (2 * r).toInt()), max(1, hh.toInt()), env.lit(glassRed))
                c.set((cx - r).toInt(), (base - hh).toInt(), env.lit(Col.hex(0xE85A5A)))
            }
        }
        // the altar's candle, a white stick on the cloth's right
        val dB = cdb - 0.35f
        val ax = gx(cmid + 0.38f, dB); val ab = hy(dB, 0.98f)
        val ch = 0.3f * pm(dB)
        c.vline(ax.toInt(), (ab - ch).toInt(), ab.toInt(), env.lit(Col.hex(0xF6F0DC)))
        if (n >= 3) flame(ax, ab - ch, 0.45f, 2)
    }

    private fun flame(x: Float, y: Float, size: Float, seed: Int) {
        val fl = 0.8f + 0.2f * sin(t * 9 + seed * 2.1).toFloat()
        val hgt = max(1, (size * 3f * z * fl).toInt())
        glow {
            for (k in 0 until hgt) c.set(x.toInt(), (y - 1 - k).toInt(), if (k < hgt / 2) Pal.FLAME[1] else Pal.FLAME[2])
            if (z > 1) c.set(x.toInt() - 1, (y - 2).toInt(), Pal.FLAME[0])
        }
        s.light(x, y - 2f * z, 14f * z, 0.45f)
    }

    /** The flowers: two vases on the altar and a pot on the step; fresh in the season's colours, or wilted (`flowers` 0). */
    private fun flowers() {
        val fresh = (fx("flowers") ?: 1f).coerceIn(0f, 1f)
        val season = env.season
        val seasonal = when (season) {
            Season.SPRING -> intArrayOf(Col.hex(0xF6F2E6), Col.hex(0xF0D040)) // snowdrops, daffodils
            Season.AUTUMN -> intArrayOf(Col.hex(0xA06AC8), Col.hex(0xE89A3A)) // asters, marigolds
            Season.WINTER -> intArrayOf(Col.hex(0xC03030), Col.hex(0x2E6A3A)) // red berries, fir
            else -> intArrayOf(Col.hex(0xE8303A), Col.hex(0xF6F2E6)) // geraniums, daisies
        }
        val wilted = intArrayOf(Col.hex(0x8A6A3A), Col.hex(0x6A5030))
        // how far gone they are: none while fresh, all brown and hanging at 0
        val wilt = ((1f - fresh) / 0.7f).coerceIn(0f, 1f)
        val petals = IntArray(2) { Col.mix(seasonal[it], wilted[it], wilt) }
        val droop = wilt
        // the vases on the altar either side of the picture
        val dB = cdb - 0.35f
        for ((k, vx0) in floatArrayOf(cmid - 0.42f, cmid + 0.22f).withIndex()) {
            val cx = gx(vx0, dB); val base = hy(dB, 0.98f)
            val vh = max(2f, 0.22f * pm(dB))
            c.fillRect(cx.toInt(), (base - vh).toInt(), max(1, z), max(1, vh.toInt()), env.lit(Col.hex(0xD8D0E0)))
            bouquet(cx, base - vh, 0.32f * pm(dB), petals, droop, k)
        }
        // the pot on the step
        val d0 = cd - 0.42f
        val px = gx(cmid, d0); val pb = hy(d0, 0.28f)
        val ph = 0.28f * pm(d0)
        c.fillRect((px - 0.12f * pm(d0)).toInt(), (pb - ph).toInt(), max(2, (0.24f * pm(d0)).toInt()), max(1, ph.toInt()), env.lit(Col.hex(0xB8683E)))
        bouquet(px, pb - ph, 0.45f * pm(d0), petals, droop, 5)
    }

    private fun bouquet(cx: Float, top: Float, r: Float, petals: IntArray, droop: Float, seed: Int) {
        val leaf = env.lit(Col.mix(Col.hex(0x3E7A3A), Col.hex(0x6A6A3A), droop))
        val n = max(4, (r * 2.2f).toInt())
        for (k in 0 until n) {
            val a = (Noise.rnd(k, seed + 691) - 0.5f) * 2.2f
            val rr = r * (0.5f + 0.5f * Noise.rnd(k, seed + 692))
            val fx = cx + sin(a) * rr; val fy = top - cos(a) * rr * (1f - 0.6f * droop) + rr * 0.4f * droop
            c.line(cx.toInt(), top.toInt(), fx.toInt(), fy.toInt(), leaf)
            c.fillRect(fx.toInt(), fy.toInt(), max(1, z), max(1, z), env.lit(petals[k % petals.size]))
        }
    }

    // ------------------------------------------------------------------ the turret, the bell, the cross

    /** The bell's angle now, swinging in its turret. */
    private fun bellAngle(): Float = sin(t * 4.6).toFloat() * 0.6f * bellSwing()

    /** The bell: in the little open turret on the ridge over the front, or (an ancona) on an iron bracket under the gable. */
    private fun bell() {
        val d = cd + 0.15f
        val p = pm(d)
        val cx = gx(cmid, d)
        if (ancona) {
            // the bracket out of the gable, the bell hanging from it
            val by = hy(cd, wallH + 1.2f)
            c.hline(cx.toInt(), (cx + 0.5f * p).toInt(), by.toInt(), env.lit(ironC))
            bellShape(cx + 0.5f * p, by, 0.32f * p)
            return
        }
        // the turret: two posts on the ridge, a little gable roof over them, the bell between
        val base = hy(d, apexH + 0.05f); val top = hy(d, apexH + 0.75f)
        val pw = max(1, (0.07f * p).toInt())
        for (o in floatArrayOf(-0.24f, 0.2f)) c.fillRect((cx + o * p).toInt(), top.toInt(), pw, max(1, (base - top).toInt()), env.lit(Pal.WOOD_M))
        val rt = hy(d, apexH + 1.05f)
        for (r in 0 until max(1, (top - rt).toInt())) {
            val half = (0.36f * p) * (r / max(1f, top - rt))
            c.hline((cx - half).toInt(), (cx + half).toInt(), (rt + r).toInt(), env.lit(if (env.snow) Pal.SNOW_L else shingleD))
        }
        bellShape(cx, top + 0.6f * z, 0.26f * p)
    }

    private fun bellShape(cx: Float, hang: Float, size: Float) {
        val a = bellAngle()
        val len = max(2f, size)
        val n = len.toInt().coerceAtLeast(2)
        for (i in 0..n) {
            val f = i / n.toFloat()
            val half = max(0.5f, (0.35f + 0.65f * f * f) * len * 0.55f)
            val mx = cx + sin(a) * len * f; val my = hang + cos(a) * len * f
            for (xx in (mx - half).toInt()..(mx + half).toInt()) {
                val u = (xx + 0.5f - mx) / half
                c.set(xx, my.toInt(), env.lit(if (u < -0.4f) bellL else if (u > 0.5f) bellD else bellC))
            }
        }
    }

    /** The cross on top: on the turret's roof (an ancona's on its gable's apex), iron. */
    private fun cross() {
        val d = cd + 0.15f
        val p = pm(d)
        val cx = gx(cmid, d)
        val base = if (ancona) hy(cd, apexH + 0.2f) else hy(d, apexH + 1.05f)
        val hh = 0.55f * p
        val pw = max(1, (0.06f * p).toInt())
        c.fillRect(cx.toInt(), (base - hh).toInt(), pw, max(1, hh.toInt()), env.lit(ironC))
        c.fillRect((cx - 0.17f * p).toInt(), (base - hh * 0.72f).toInt(), max(2, (0.34f * p).toInt()), pw, env.lit(ironC))
    }

    private fun bellRings(swing: Float) {
        if (swing < 0.05f) return
        val d = cd + 0.15f
        val cx = gx(cmid, d); val cy = if (ancona) hy(cd, wallH + 1f) else hy(d, apexH + 0.4f)
        val u = ((t * 2.0) % 1.0).toFloat()
        s.fx { PokeArt.rings(c, cx, cy, u, 18f * z, Col.hex(0xFFF0C0), max(1, z), 0.55f * swing, 2) }
    }

    // ------------------------------------------------------------------ the bench and the linden

    /** The bench by the road under the linden: a plank seat and back on two posts. */
    private fun bench() {
        val p = pm(benchD)
        val xa = gx(benchX0, benchD); val xb = gx(benchX1, benchD)
        val seat = hy(benchD, 0.45f); val back = hy(benchD, 0.95f)
        val th = max(1, (0.09f * p).toInt())
        c.fillRect(xa.toInt(), (seat - th).toInt(), max(1, (xb - xa).toInt()), th + 1, if (env.snow) Pal.SNOW_L else env.lit(Pal.WOOD_L))
        c.fillRect(xa.toInt(), seat.toInt() + 1, max(1, (xb - xa).toInt()), max(1, z), env.lit(Pal.WOOD_D))
        c.fillRect(xa.toInt(), back.toInt(), max(1, (xb - xa).toInt()), th, env.lit(Pal.WOOD_M))
        for (lx in floatArrayOf(xa + 0.12f * p, xb - 0.2f * p)) c.fillRect(lx.toInt(), back.toInt(), max(1, (0.07f * p).toInt()), (gy(benchD) - back).toInt(), env.lit(Pal.WOOD_D))
    }

    /** The linden behind the bench: a big crown; in flower in June and July (pale yellow among the leaves), gold in October, bare in winter. */
    private fun linden() {
        leafyTree(treeX, treeD, 11f, 671)
        if (env.month in 6..7) {
            val p = pm(treeD); val bx = gx(treeX, treeD); val by = gy(treeD)
            val cy = by - 11f * 0.42f * p - 11f * 0.34f * p * 0.5f
            for (k in 0 until 40) {
                val x = bx + (Noise.rnd(k, 673) - 0.5f) * 11f * 0.6f * p; val y = cy + (Noise.rnd(k, 674) - 0.5f) * 11f * 0.45f * p
                if (c.inside(x.toInt(), y.toInt()) && c.ids[c.index(x.toInt(), y.toInt())] == c.penId) c.set(x.toInt(), y.toInt(), Col.hex(0xF0E89A))
            }
        }
    }

    // ------------------------------------------------------------------ the traveller and the swallows

    /**
     * The traveller walking up the road at depth [d] ([u] of the way): a brown cloak, a broad hat, a staff, his legs stepping;
     * as small as the distance makes him, fading over the rise at the end.
     */
    private fun traveller(d: Float, u: Float) {
        val p = pm(d)
        val x = road(d) - 0.35f
        val bx = gx(x, d); val by = gy(d)
        val hgt = 1.75f * p
        val step = sin(t * 7.0).toFloat()
        val cloak = env.lit(Col.hex(0x6A4A30)); val hat = env.lit(Col.hex(0x3A2A1E)); val skin = env.lit(Pal.SKIN)
        val w2 = max(1f, 0.22f * p)
        // the legs, stepping
        c.line(bx.toInt(), (by - hgt * 0.42f).toInt(), (bx + step * 0.12f * p).toInt(), by.toInt(), env.lit(Col.hex(0x3A3028)))
        c.line(bx.toInt(), (by - hgt * 0.42f).toInt(), (bx - step * 0.12f * p).toInt(), by.toInt(), env.lit(Col.hex(0x2A221C)))
        // the cloak and the head under the hat, the staff in his hand
        c.fillRect((bx - w2).toInt(), (by - hgt * 0.82f).toInt(), max(1, (2 * w2).toInt()), max(1, (hgt * 0.42f).toInt()), cloak)
        c.fillCircle(bx, by - hgt * 0.88f, max(0.6f, 0.13f * p), skin)
        c.hline((bx - 0.24f * p).toInt(), (bx + 0.24f * p).toInt(), (by - hgt * 0.95f).toInt(), hat)
        c.fillRect((bx - 0.1f * p).toInt(), (by - hgt * 1.03f).toInt(), max(1, (0.2f * p).toInt()), max(1, (0.09f * p).toInt()), hat)
        c.line((bx + w2 + 0.05f * p).toInt(), (by - hgt * 1.0f).toInt(), (bx + w2 + 0.12f * p).toInt(), by.toInt(), env.lit(Pal.WOOD_D))
    }

    /** Swallows over the wheat on summer days, swooping. */
    private fun swallows() {
        if (env.month !in 5..8 || env.dark > 0.3f || frame.sky.rain > 0.2f) return
        s.fx {
            for (k in 0 until 4) {
                val ph = (t * 0.25 + k * 0.27) % 1.0
                val x = ox + ((ph * 300 - 30) * z).toFloat()
                val y = hz - (8f + 6f * sin(ph * 12.0 + k).toFloat() + k * 3f) * z
                PokeArt.wings(c, x.toInt(), y.toInt(), sin(t * 10 + k) > 0, max(1, z / 2 + if (z == 1) 0 else 0), Col.hex(0x1E2230))
            }
        }
    }

    private fun lights() {
        if (env.windows > 0.3f && litCandles() > 0) s.light(gx(cmid, cd - 0.3f), hy(cd - 0.3f, 0.6f), 30f * z, 0.6f * env.windows)
    }
}
