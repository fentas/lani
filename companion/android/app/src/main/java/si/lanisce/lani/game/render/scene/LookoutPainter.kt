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

/**
 * Na razglednem stolpu: the view from the top of the village's lookout tower on its hill (the project `razgledni_stolp`,
 * Gostilničarka Vida's), a painted vista (companion/SCENES.md "Arts"). We stand on its platform: the plank floor, the railing of
 * peeled logs across the front, the corner posts and their braces either side, the shingle roof overhead with the flag on its
 * pole; on the railing in the middle the board with the mountains drawn on it and their names, on the right the telescope on its
 * post, on the left post the little box with the visitors' book and the stamp, the bench along the left side, the stairs coming
 * up through their hatch in the floor at the right.
 *
 * The view: far below the village in its clearing in the woods, its red roofs and the church's spire; the valley beyond it, its
 * fields and the river winding; green hills, the forest; on the left the Julian Alps along the sky with Triglav's three heads
 * in the middle of them, snow on it most of the year; on the right the plain going down to the sea, a glint at the horizon. The
 * haze of the day (summer's milky, autumn's and winter's clear) hides the far land or shows it; the peaks glow pink at sunrise
 * and sunset; the village's windows are lit at night and the moon lies on the sea.
 *
 * Its effects ([si.lanisce.lani.game.scene.SceneArt.effects]):
 * - `clear`: how clear the air is: 0 a white haze over the far land, the Alps and the sea gone in it; 1 clear to the sea and
 *   every peak sharp. Not set: as the day is (hazy in summer, clear in autumn and winter, clearer after rain).
 * - `glow`: the alpenglow on the peaks (how pink). Not set: at sunrise and sunset.
 * - `hawk`: a buzzard circling over the valley below us, its wings spread (how many rounds; 0 none).
 * - `bell`: the village's church bell far below, its rings coming up (how hard).
 *
 * Tapped ([pokes]): the telescope turns on its post to the left, then to the right and back.
 *
 * Closer up ([detail] 2 and 3): the shingles, the logs' grain, the board's drawing and its little names, the telescope's
 * brass, the village's houses, the fields' furrows and hedges.
 *
 * Friuli's look ([look]; companion/SCENES.md "Culture looks"): the view of Friuli: the Collio's hills striped with vines, the
 * plain with its rows of poplars, the village on the hill as Friuli builds it with its campanile, the lagoon and the sea at
 * Grado on the right; the flag on the roof the golden eagle of Friuli.
 */
internal class LookoutPainter : VistaPainter() {
    override val art = "lookout"
    override val horizon = 52
    override val pokes = listOf(Poke("telescope", listOf(1.8, 2.6), rest = 2.5))

    /** North over the land toward the Alps: the Plough at night. */
    override val skyFacing = 350.0

    // the land far below: the eye LAND_EYE m above it, a point d m away F × LAND_EYE / d rows under the horizon
    private fun ly(d: Float): Float = hz + F * LAND_EYE / d
    private fun lx(x: Float, d: Float): Float = vx + F * x / d
    private fun ldepth(py: Int): Float = F * LAND_EYE / max(0.35f, py + 0.5f - hz)
    private fun lside(px: Int, d: Float): Float = (px + 0.5f - vx) * d / F

    // the platform: the railing stands RAIL_D m away (its foot's row), the floor runs from it to us
    private val railD = 11.6f
    private val railH = 1.15f

    // the people: at the railing, by the board, by the stairs
    private val railPX = -1.6f; private val railPD = 10.7f
    private val boardPX = 1.25f; private val boardPD = 10.8f
    private val stairsPX = 3.7f; private val stairsPD = 10.1f

    // the village far below and its church; the river; the sea's band on the right
    private val villageX = -120f; private val villageD = 640f

    private val logL = Col.hex(0xC49A68); private val logM = Col.hex(0xA27848); private val logD = Col.hex(0x7A5632)
    private val plankL = Col.hex(0xB88A58); private val plankM = Col.hex(0x9A7046); private val plankD = Col.hex(0x6E4E30)

    /** Friuli's view ([readLook2]). */
    private var friuli = false

    private val z: Int get() = detail
    private var haze = 0.5f
    private val winter: Boolean get() = env.season == Season.WINTER

    private fun readLook2() { friuli = variant("view") == "friuli" }

    /** How clear the air is: `clear`, else by the season and the weather (hazy summer, clear autumn and winter). */
    private fun clarity(): Float {
        fx("clear")?.let { return it.coerceIn(0f, 1f) }
        val base = when (env.month) { 6, 7, 8 -> 0.45f; 9, 10, 11 -> 0.85f; 12, 1, 2 -> 0.9f; else -> 0.65f }
        return (base - frame.sky.gloom * 0.4f - frame.sky.fog * 0.6f).coerceIn(0f, 1f)
    }

    /** [col] far off, through the haze: more of the horizon's colour the farther ([far] 0..1) and the hazier the day. */
    private fun distant(col: Int, far: Float): Int = Col.mix(col, Col.mix(env.skyHorizon, Col.hex(0xF0F2F4), 0.4f * (1f - env.dark)), (far * (0.35f + 0.6f * haze)).coerceIn(0f, 0.95f))

    override fun paint() {
        setup()
        readLook2()
        haze = 1f - clarity()
        val z = z
        vistaSky(3, 81, moonX = ox + 196 * z, moonY = oy + 14 * z)
        thing("mountains", outline = 0) { alps() }
        thing("peak", outline = 0) { triglav() }
        farHills(near = false)
        thing("sea", outline = 0) { sea() }
        farHills(near = true)
        thing("valley", outline = 0) { valley() }
        prop(0) { river() }
        thing("forest", outline = Pal.OUTLINE_TREE) { woods() }
        thing("village") { village() }
        hawk()
        churchBell()
        // the tower: the floor, the bench, the hatch and its stairs, the railing with the board, the telescope, the box, the posts and the roof
        prop { floor() }
        thing("bench") { bench() }
        thing("railing") { railing() }
        thing("board") { board() }
        thing("telescope", slop = 1) { telescope() }
        thing("book", slop = 1) { boxAndBook() }
        thing("stamp", slop = 2) { stamp() }
        for (p in peopleAt("rail")) person(p, gx(railPX, railPD).toInt(), gy(railPD).toInt(), flip = false)
        for (p in peopleAt("board")) person(p, gx(boardPX, boardPD).toInt(), gy(boardPD).toInt(), flip = true)
        // whoever has come up the stairs stands before the railing, just past the hatch and its rail
        for (p in peopleAt("stairs")) person(p, gx(stairsPX, stairsPD).toInt(), gy(stairsPD).toInt(), flip = true)
        thing("stairs") { stairs() }
        // the corner posts: the left one scenery, the right one (the flag's) the tower to tap, its box no wider than a post
        prop { post(-1) }
        thing("tower") { post(1) }
        thing("roof") { roof() }
        thing("flag", slop = 1) { flag() }
    }

    // ------------------------------------------------------------------ the far land

    /** The sea on the right at the horizon: a band over the plain, its glints drifting (the moon's at night), gone in a haze. */
    private fun sea() {
        val z = z
        val x0 = ox + 150 * z
        val body = env.lit(distant(if (friuli) Col.hex(0x4A90C4) else Col.hex(0x3F84C4), 0.6f))
        val line = distant(Col.mix(env.skyHorizon, Col.hex(0xFFFFFF), 0.4f), 0.4f)
        c.penEmissive = true
        for (x in max(x0, c.left) until min(w, c.right)) {
            val u = (x - x0) / (60f * z)
            val rows = ((1.5f + 3f * min(1f, u)) * z * (if (friuli) 1.4f else 1f)).toInt()
            val top = hz - rows
            for (y in max(top, c.top) until min(hz + z, c.bottom)) {
                val g = Noise.rnd(floor((x - ox) / (3f * z) + t * 0.3).toInt(), (y - top) / z, 811)
                c.set(x, y, when {
                    y < top + z -> line
                    g > 0.9f && haze < 0.7f -> if (env.dark > 0.5f) Col.mix(body, Col.hex(0xD8D2B0), 0.5f) else Col.mix(line, Col.hex(0xFFFFFF), 0.6f)
                    else -> body
                })
            }
        }
        c.penEmissive = false
    }

    /** How high the Alps stand at scene column [u] (0 … 240), in scene px over the horizon: tall on the left, sinking to the south. */
    private fun alpsTop(u: Float): Float {
        val tall = (1f - (u - 20f) / 150f).coerceIn(0.15f, 1f)
        val a = 1f - abs(Noise.v1(u * 0.05f, 821) * 2f - 1f)
        val b = 1f - abs(Noise.v1(u * 0.13f, 822) * 2f - 1f)
        return 6f + 22f * tall * (a * a * 0.7f + b * 0.3f)
    }

    /** The glow on the peaks (0 … 1): `glow`, else round sunrise and sunset. */
    private fun glow(): Float = fx("glow") ?: if (env.dark < 0.6f && frame.sky.gloom < 0.5f) (1f - abs(env.sun - 0.06f) / 0.18f).coerceIn(0f, 1f) else 0f

    /** The Julian Alps along the sky on the left: grey rock lit on the sun's side, snow on the tops (down the slopes in winter). */
    private fun alps() = range(0f, 175f) { u -> alpsTop(u) }

    /** Triglav among them: its broad shoulders and the three heads, the highest in the middle, whiter than the rest. */
    private fun triglav() = range(TRIGLAV_U - 18f, TRIGLAV_U + 18f) { u ->
        val dx = (u - TRIGLAV_U) / 18f
        val body = (1f - dx * dx) * 30f
        val heads = 3.2f * (max(0f, 1f - abs(dx) * 7f) + 0.6f * max(0f, 1f - abs(dx + 0.38f) * 8f) + 0.5f * max(0f, 1f - abs(dx - 0.42f) * 8f))
        max(alpsTop(u), body + heads)
    }

    /** A range of peaks between scene columns [u0] and [u1], its crest at [top] (scene px over the horizon). */
    private inline fun range(u0: Float, u1: Float, top: (Float) -> Float) {
        val z = z
        val snowLine = when (env.season) { Season.SUMMER -> 0.75f; Season.AUTUMN -> 0.55f; Season.WINTER -> 0.12f; else -> 0.4f }
        val g = glow()
        val sunLeft = (sunAt()?.first ?: vx) < vx
        c.penEmissive = true
        for (x in max(max(0, (ox + u0 * z).toInt()), c.left) until min(min(w, (ox + u1 * z).toInt()), c.right)) {
            val u = (x + 0.5f - ox) / z
            val tt = top(u)
            val facing = (top(u + 1.5f) - top(u - 1.5f)).let { if (sunLeft) it < 0f else it > 0f }
            val crest = hz + z - tt * z
            for (y in max(crest.toInt(), c.top) until min(hz + z, c.bottom)) {
                val up = (hz + z - y - 0.5f) / z
                val rel = up / max(1f, tt)
                val snowy = rel > snowLine + (Noise.v2(u * 0.2f, up * 0.4f, 823) - 0.5f) * 0.3f
                val gully = Noise.v2(u * 0.4f + up * 0.2f, up * 0.3f, 824) > 0.7f
                var col = when {
                    snowy -> if (facing) Col.hex(0xF4F7FF) else Col.hex(0xC0CCE4)
                    gully -> Col.hex(0x6E7088)
                    facing -> Col.hex(0xA8A8BC)
                    else -> Col.hex(0x7E8098)
                }
                if (g > 0f && facing) col = Col.mix(col, if (snowy) Col.hex(0xFFC4AC) else Col.hex(0xE89A80), 0.5f * g)
                c.set(x, y, env.lit(distant(col, 0.75f)))
            }
        }
        c.penEmissive = false
    }

    /** The hills between the Alps and the valley: blue far ones, green near ones with woods (vines in Friuli's Collio). */
    private fun farHills(near: Boolean) {
        val z = z
        if (!near) {
            // the far hills under the Alps, greyed green in the distance, sinking toward the plain and the sea on the right
            val farCol = distant(if (env.season == Season.WINTER) Col.hex(0xA8B4C2) else Col.hex(0x6E8E78), 0.55f)
            val crest = ridge(hz.toFloat() + 2f * z, 7f * z, 0.016f, 831, farCol, 0f, bottom = hz + 13 * z)
            treeline(crest, 4 * z, 1.4f * z, distant(Col.hex(0x4E6E58), 0.55f), 0f, 832, 0, ox + 150 * z)
            return
        }
        val nearCol = distant(when (env.season) { Season.WINTER -> Col.hex(0xC8D2DE); Season.AUTUMN -> Col.hex(0x8A7A44); else -> Col.hex(0x54804A) }, 0.3f)
        val crest = if (friuli) ridge(hz.toFloat() + 9f * z, 9f * z, 0.022f, 833, nearCol, 0f, bottom = hz + 14 * z, texture = { x, y, below -> collioSlope(x, y, below, 835) })
        else ridge(hz.toFloat() + 9f * z, 9f * z, 0.022f, 833, nearCol, 0f, bottom = hz + 14 * z)
        treeline(crest, 4 * z, 1.6f * z, distant(if (env.season == Season.WINTER) Col.hex(0x6E7A86) else Col.hex(0x2E5A36), 0.3f), 0f, 837)
    }

    /** The valley floor far below: a patchwork of fields with hedges between them, in the season's colours, in the haze. */
    private fun valley() {
        // down to the platform's floor: the land shows between the railing's rails too
        val railTop = hy(railD, 0f).toInt()
        val top = (hz + 12 * z).toInt()
        c.penEmissive = true
        for (py in max(top, c.top) until min(railTop + 2 * z, c.bottom)) {
            val d = ldepth(py)
            val far = ((d - 300f) / 3000f).coerceIn(0f, 1f)
            for (px in max(0, c.left) until min(w, c.right)) {
                val x = lside(px, d)
                val cx = floor(x / 140f + Noise.v1(d / 500f, 841) * 0.6f).toInt(); val cd = floor(d / 220f).toInt()
                val kind = (Noise.rnd(cx, cd, 842) * 5f).toInt()
                var col = when (env.season) {
                    Season.WINTER -> if (kind == 2) Col.hex(0xD8E0EA) else Pal.SNOW_L
                    Season.SPRING -> when (kind) { 0 -> Col.hex(0x8CC45A); 1 -> Col.hex(0x6AA84A); 2 -> Col.hex(0xB8D06A); 3 -> Col.hex(0x9A7A52); else -> Col.hex(0x7AB658) }
                    Season.SUMMER -> when (kind) { 0 -> Col.hex(0xD8C060); 1 -> Col.hex(0x5E9A42); 2 -> Col.hex(0x8AB450); 3 -> Col.hex(0xC8A858); else -> Col.hex(0x6CA44C) }
                    Season.AUTUMN -> when (kind) { 0 -> Col.hex(0x9A7A4E); 1 -> Col.hex(0x7E8E48); 2 -> Col.hex(0xB08A4A); 3 -> Col.hex(0x8A6A44); else -> Col.hex(0x6E8A46) }
                }
                val fxv = x / 140f + Noise.v1(d / 500f, 841) * 0.6f
                if (fxv - floor(fxv) < 0.06f && env.season != Season.WINTER) col = Col.scale(col, 0.72f)
                if (Dither.at(px, py) < 0.15f) col = Col.scale(col, 0.94f)
                c.set(px, py, env.lit(distant(col, 0.2f + far * 0.6f)))
            }
        }
        c.penEmissive = false
        // Friuli's plain: rows of poplars along its ditches
        if (friuli) for (k in 0 until 9) {
            val d = 900f + k * 260f; val x = -700f + Noise.rnd(k, 845) * 1400f
            val bx = lx(x, d).toInt(); val by = ly(d).toInt()
            for (j in 0 until 6) {
                val px = bx + (j * 3 - 8) * z; val hh = (2 + (j % 2)) * z
                c.fillRect(px, by - hh, max(1, z), hh, env.lit(distant(Col.hex(0x3E6A36), 0.4f)))
            }
        }
    }

    /** The river winding through the valley, catching the sky. */
    private fun river() {
        val z = z
        c.penEmissive = true
        val col = env.lit(distant(Col.mix(skyAt(0.5f), Col.hex(0x6FA6D0), 0.4f), 0.3f))
        for (py in max((hz + 13 * z), c.top) until min(hy(railD, 0f).toInt() + z, c.bottom)) {
            val d = ldepth(py)
            val xr = 250f + 300f * sin(d / 900f) - d * 0.08f
            val half = max(0.5f, 22f * F / d / 2f)
            val cx = lx(xr, d)
            for (px in max((cx - half).toInt(), c.left)..min((cx + half).toInt(), c.right - 1)) c.set(px, py, col)
        }
        c.penEmissive = false
    }

    /** The woods round the village's clearing and along the hills' foot: dark round crowns, gold in autumn, white in winter. */
    private fun woods() {
        val z = z
        val (d0, l0) = when (env.season) {
            Season.WINTER -> Col.hex(0x6E7884) to Pal.SNOW_L
            Season.AUTUMN -> Col.hex(0x8A5A22) to Col.hex(0xC89238)
            else -> Col.hex(0x264E2E) to Col.hex(0x3E6E3E)
        }
        c.penEmissive = true
        for (k in 0 until 420) {
            val d = 330f + Noise.rnd(k, 851) * 1100f
            val x = -1000f + Noise.rnd(k, 852) * 2000f
            // woods in patches, thick round the village's clearing (but not in it)
            val near = abs(x - villageX) < 260f && abs(d - villageD) < 260f
            if (Noise.v2(x / 380f, d / 300f, 853) < (if (near) 0.35f else 0.56f)) continue
            if (abs(x - villageX) < 110f && abs(d - villageD) < 130f) continue
            val bx = lx(x, d); val by = ly(d)
            if (by > hy(railD, 0f) + 2 * z) continue
            val r = max(1f, 24f * F / d / 2f)
            c.fillEllipse(bx, by - r * 0.6f, r * 1.2f, r * 0.8f, env.lit(distant(d0, ((d - 300f) / 3000f).coerceIn(0f, 1f))))
            c.fillEllipse(bx - r * 0.3f, by - r * 0.85f, r * 0.6f, r * 0.4f, env.lit(distant(l0, ((d - 300f) / 3000f).coerceIn(0f, 1f))))
        }
        c.penEmissive = false
    }

    /** The village far below in its clearing: white houses with red roofs (Friuli's coppi), the church with its spire (Friuli's campanile). */
    private fun village() {
        val z = z
        val lit = env.windows > 0.35f
        val p = F / villageD
        val cx = lx(villageX, villageD); val base = ly(villageD)
        if (friuli) {
            val st = FireStyle.of(frame.world.culture, frame.world.landscape)
            for (i in 0 until 7) farCoppiHouse((cx - 18f * z + i * 5f * z).toInt(), (base + (i % 3) * z).toInt(), 4 * z, 3 * z, st, i, lit, 0.2f)
            farCampanile((cx + 2f * z).toInt(), base.toInt() - z, st, lit, 0.2f, tall = 11)
            return
        }
        val wall = env.lit(Col.hex(0xF2EEE4)); val wallD = env.lit(Col.hex(0xC8C0B0))
        val roof = env.lit(Pal.ROOF_M); val roofD = env.lit(Pal.ROOF_D)
        c.penEmissive = true
        for (i in 0 until 8) {
            val hx = (cx - 20f * z + i * 5f * z + (if (i % 2 == 0) 0 else z)).toInt()
            val hb = (base + ((i * 7) % 3) * z).toInt()
            c.fillRect(hx, hb - 2 * z, 4 * z, 2 * z, wall)
            c.fillRect(hx + 3 * z, hb - 2 * z, z, 2 * z, wallD)
            for (r in 0 until 2 * z) c.hline(hx - z / 2 + r / 2, hx + 4 * z + z / 2 - 1 - r / 2, hb - 2 * z - 1 - r, if (r < z / 2 + 1) roofD else roof)
            if (lit && i % 3 != 1) c.fillRect(hx + z, hb - z - z / 2, max(1, z / 2 + 1), max(1, z / 2 + 1), Pal.WINDOW_LIT)
        }
        // the church: its nave and the tower with the spire
        val tx = (cx + 4f * z).toInt(); val tb = (base - z).toInt()
        c.fillRect(tx, tb - 3 * z, 5 * z, 3 * z, wall)
        for (r in 0 until 2 * z) c.hline(tx - z / 2 + r / 2, tx + 5 * z + z / 2 - 1 - r / 2, tb - 3 * z - 1 - r, if (r < z / 2 + 1) roofD else roof)
        c.fillRect(tx - 2 * z, tb - 8 * z, 2 * z, 8 * z, wall)
        for (r in 0 until 4 * z) {
            val half = (z + z * 0.2f) * (1f - r / (4f * z))
            c.hline((tx - z - half).toInt(), (tx - z + half).toInt() - 1, tb - 8 * z - 1 - r, if (r % (2 * z) < z) roofD else roof)
        }
        c.penEmissive = false
    }

    /** A buzzard circling over the valley below us (`hawk`): its wings spread, banking round. */
    private fun hawk() {
        val k = fxOn("hawk")
        if (k <= 0.02f) return
        val z = z
        val a = (t * 0.6).toFloat()
        val cx = ox + (150f + cos(a) * 26f * k) * z; val cy = hz + (30f + sin(a) * 9f * k) * z
        prop(0) {
            val dark = env.lit(Col.hex(0x4A3424)); val light = env.lit(Col.hex(0xC8A878))
            val span = 5 * z
            for (i in -span..span) {
                val f = abs(i) / span.toFloat()
                val yy = cy - f * 1.5f * z * sin(a * 2f + 1f).coerceAtLeast(0.2f)
                c.set((cx + i).toInt(), yy.toInt(), if (f > 0.75f) dark else light)
                if (z > 1) c.set((cx + i).toInt(), yy.toInt() + 1, dark)
            }
            c.fillRect(cx.toInt() - z / 2, cy.toInt(), max(1, z), max(2, 2 * z), dark)
        }
    }

    /** The village's church bell far below, its rings coming up from its tower (`bell`). */
    private fun churchBell() {
        val b = fxOn("bell")
        if (b <= 0.02f) return
        val z = z
        val cx = lx(villageX, villageD) + 3f * z; val cy = ly(villageD) - 7f * z
        val u = ((t * 1.5) % 1.0).toFloat()
        s.fx { PokeArt.rings(c, cx, cy, u, 16f * z, Col.hex(0xFFF0C0), max(1, z), 0.55f * b, 3) }
    }

    // ------------------------------------------------------------------ the tower's top

    /** The platform's floor: planks running across, from the railing to our feet; snow on them in winter. */
    private fun floor() {
        val top = hy(railD, 0f).toInt()
        for (py in max(top, c.top) until min(h, c.bottom)) {
            val d = depthAt(py)
            val band = bandPhase(d, 0.32f)
            for (px in max(0, c.left) until min(w, c.right)) {
                val x = sideAt(px, d)
                var col = if (band >= 0f && band < 0.12f) plankD else if (Noise.rnd(floor(d / 0.32f).toInt(), floor(x / 2.3f).toInt(), 861) < 0.4f) plankL else plankM
                if (winter && Noise.v2(x * 1.5f, d * 2f, 863) > 0.35f) col = if (Dither.at(px, py) < 0.3f) Pal.SNOW_M else Pal.SNOW_L
                c.set(px, py, env.lit(col))
            }
        }
    }

    /** The bench along the left side of the platform. */
    private fun bench() {
        val xa = -5.2f
        for (d in floatArrayOf(10.0f, 11.3f)) {
            val p = pm(d)
            c.fillRect(gx(xa, d).toInt(), hy(d, 0.45f).toInt(), max(2, (0.12f * p).toInt()), (gy(d) - hy(d, 0.45f)).toInt(), env.lit(logD))
        }
        c.polyBegin()
        c.polyAdd(gx(xa - 0.25f, 9.8f), hy(9.8f, 0.48f)); c.polyAdd(gx(xa + 0.35f, 9.8f), hy(9.8f, 0.48f))
        c.polyAdd(gx(xa + 0.35f, 11.5f), hy(11.5f, 0.48f)); c.polyAdd(gx(xa - 0.25f, 11.5f), hy(11.5f, 0.48f))
        c.polyFill(if (winter) Pal.SNOW_L else env.lit(logL))
        c.line(gx(xa + 0.35f, 9.8f).toInt(), hy(9.8f, 0.48f).toInt(), gx(xa + 0.35f, 11.5f).toInt(), hy(11.5f, 0.48f).toInt(), env.lit(logD))
    }

    /** The stairs coming up through their hatch in the floor at the right: the opening, the top steps going down, a rail round it. */
    private fun stairs() {
        val xa = 1.9f; val xb = 3.1f; val da = 9.1f; val db = 9.9f
        // the dark opening
        c.polyBegin()
        c.polyAdd(gx(xa, da), gy(da)); c.polyAdd(gx(xb, da), gy(da)); c.polyAdd(gx(xb, db), gy(db)); c.polyAdd(gx(xa, db), gy(db))
        c.polyFill(env.lit(Col.hex(0x2A2018)))
        // the steps going down into it, lit at their edges
        for (k in 1..3) {
            val d = da + (db - da) * (k / 4f)
            c.hline(gx(xa + 0.08f, d).toInt(), gx(xb - 0.08f, d).toInt(), (gy(d) + k * z).toInt(), env.lit(plankL))
        }
        // the rail round it: posts at its far corners and a rail along its far side and its left
        val p = pm(db)
        for (sx in floatArrayOf(xa, xb)) c.fillRect(gx(sx, db).toInt(), hy(db, 0.95f).toInt(), max(1, (0.08f * p).toInt()), (gy(db) - hy(db, 0.95f)).toInt(), env.lit(logD))
        c.line(gx(xa, db).toInt(), hy(db, 0.95f).toInt(), gx(xb, db).toInt(), hy(db, 0.95f).toInt(), env.lit(logM))
    }

    /** The railing of peeled logs across the front: posts, a top rail and a middle one; snow on the top rail in winter. */
    private fun railing() {
        val p = pm(railD)
        val top = hy(railD, railH); val mid = hy(railD, 0.6f); val foot = gy(railD)
        val th = max(2, (0.12f * p).toInt())
        c.fillRect(0, top.toInt(), w, th, env.lit(logM))
        c.hline(0, w - 1, top.toInt(), if (winter) Pal.SNOW_L else env.lit(logL))
        c.hline(0, w - 1, top.toInt() + th, env.lit(logD))
        c.fillRect(0, mid.toInt(), w, max(1, th - z), env.lit(logM))
        c.hline(0, w - 1, mid.toInt() + max(1, th - z), env.lit(logD))
        var x = -12f
        while (x < 12f) {
            val px = gx(x, railD)
            c.fillRect(px.toInt(), top.toInt(), max(2, (0.14f * p).toInt()), (foot - top).toInt(), env.lit(logM))
            c.vline(px.toInt(), top.toInt(), foot.toInt(), env.lit(logL))
            x += 1.6f
        }
    }

    /** The board on the railing's middle: the mountains drawn on it in a line, their peaks marked and named in little letters. */
    private fun board() {
        val p = pm(railD - 0.1f)
        val a0 = gx(-0.75f, railD - 0.1f); val a1 = gx(0.75f, railD - 0.1f)
        val t0 = hy(railD - 0.1f, railH + 0.62f); val t1 = hy(railD - 0.1f, railH + 0.05f)
        // its two legs on the rail
        for (sx in floatArrayOf(-0.6f, 0.55f)) c.fillRect(gx(sx, railD - 0.1f).toInt(), t1.toInt(), max(1, (0.06f * p).toInt()), (hy(railD, railH) - t1).toInt() + 1, env.lit(Pal.WOOD_D))
        for (py in max(t0.toInt(), c.top)..min(t1.toInt(), c.bottom - 1)) for (px in max(a0.toInt(), c.left)..min(a1.toInt(), c.right - 1)) {
            val u = (px + 0.5f - a0) / max(1f, a1 - a0); val v = (py + 0.5f - t0) / max(1f, t1 - t0)
            val frame = u < 0.04f || u > 0.96f || v < 0.08f || v > 0.92f
            // the mountains' line across it, the peaks' ticks and their names under them
            val line = 0.55f - 0.25f * (1f - abs(Noise.v1(u * 6f, 871) * 2f - 1f)) - 0.12f * max(0f, 1f - abs(u - 0.3f) * 10f)
            val col = when {
                frame -> Pal.WOOD_D
                abs(v - line) < 1.2f / max(1f, t1 - t0) -> Col.hex(0x2E3A5A)
                v > line && v < 0.75f -> Col.hex(0xE8EEF2)
                v in 0.78f..0.86f && (floor(u * 24f).toInt() % 3 != 2) && Noise.rnd(floor(u * 40f).toInt(), 873) < 0.7f -> Col.hex(0x4A4A50)
                else -> Col.hex(0xF4EED8)
            }
            c.set(px, py, env.lit(col))
        }
    }

    /** The telescope on its post at the right, its brass and green body, turning on a tap. */
    private fun telescope() {
        val d = railD - 0.6f; val sx = 3.0f
        val p = pm(d)
        val bx = gx(sx, d); val foot = gy(d); val headY = hy(d, 1.35f)
        c.fillRect((bx - 0.07f * p).toInt(), headY.toInt(), max(2, (0.14f * p).toInt()), (foot - headY).toInt(), env.lit(Col.hex(0x3A4A3E)))
        c.fillRect((bx - 0.22f * p).toInt(), (foot - 0.08f * p).toInt(), max(3, (0.44f * p).toInt()), max(1, (0.08f * p).toInt()), env.lit(Col.hex(0x2E3A30)))
        // the head: a body with two eyepieces toward us and its lenses toward the view, turned as a tap has it
        val pk = poked("telescope")
        val turn = if (pk == null) 0f else { val a = pk.age(t); val dur = if (pk.step == 0) 1.8f else 2.6f; if (a > dur) 0f else sin(a / dur * PI.toFloat() * (if (pk.step == 0) 1f else 2f)) * 0.35f * p }
        val hw = 0.3f * p; val hh = 0.2f * p
        c.fillRect((bx - hw + turn).toInt(), (headY - hh).toInt(), max(3, (2 * hw).toInt()), max(2, hh.toInt()), env.lit(Col.hex(0x4E7A5A)))
        c.hline((bx - hw + turn).toInt(), (bx + hw + turn).toInt(), (headY - hh).toInt(), env.lit(Col.hex(0x7AAA86)))
        for (o in floatArrayOf(-0.15f, 0.15f)) c.fillRect((bx + o * p + turn).toInt(), (headY - hh * 0.4f).toInt(), max(1, (0.08f * p).toInt()), max(1, (0.12f * p).toInt()), env.lit(Col.hex(0xC8962E)))
    }

    /** The little box on the left post with its door open: the visitors' book on its shelf, red. */
    private fun boxAndBook() {
        val d = railD - 0.05f; val sx = -3.6f
        val p = pm(d)
        val bx = gx(sx, d); val top = hy(d, railH + 0.55f); val bot = hy(d, railH + 0.05f)
        val bw = 0.42f * p
        c.fillRect(bx.toInt(), top.toInt(), bw.toInt(), (bot - top).toInt(), env.lit(Pal.WOOD_M))
        c.fillRect((bx + 0.04f * p).toInt(), (top + 0.08f * p).toInt(), (bw - 0.08f * p).toInt(), (bot - top - 0.12f * p).toInt(), env.lit(Col.hex(0x2A2018)))
        // its roof, the door open on its hinge
        c.hline((bx - 0.04f * p).toInt(), (bx + bw + 0.04f * p).toInt(), top.toInt(), if (winter) Pal.SNOW_L else env.lit(Pal.SHINGLE_D))
        c.fillRect((bx - 0.14f * p).toInt(), (top + 0.06f * p).toInt(), max(1, (0.1f * p).toInt()), (bot - top - 0.1f * p).toInt(), env.lit(Pal.WOOD_L))
        // the book lying on its shelf, its pages at the edge
        c.fillRect((bx + 0.06f * p).toInt(), (bot - 0.14f * p).toInt(), max(2, (0.22f * p).toInt()), max(1, (0.08f * p).toInt()), env.lit(Col.hex(0xB02A2A)))
        c.hline((bx + 0.06f * p).toInt(), (bx + 0.28f * p).toInt(), (bot - 0.14f * p).toInt(), env.lit(Col.hex(0xF4EED8)))
    }

    /** The stamp beside the book in the box, its wooden handle up, its pad by it. */
    private fun stamp() {
        val d = railD - 0.05f; val sx = -3.6f
        val p = pm(d)
        val bx = gx(sx, d); val bot = hy(d, railH + 0.05f)
        val sxp = bx + 0.3f * p
        c.fillRect(sxp.toInt(), (bot - 0.22f * p).toInt(), max(1, (0.06f * p).toInt()), max(2, (0.14f * p).toInt()), env.lit(Pal.WOOD_L))
        c.fillCircle(sxp + 0.03f * p, bot - 0.24f * p, max(0.8f, 0.05f * p), env.lit(Pal.WOOD_D))
        c.fillRect((sxp - 0.03f * p).toInt(), (bot - 0.08f * p).toInt(), max(1, (0.12f * p).toInt()), max(1, (0.04f * p).toInt()), env.lit(Col.hex(0x3A3A6A)))
    }

    /** The roof's eave over the view: a band at the top of the picture (the roof is over us, painted as the watch's is). */
    private val eaveRow: Int get() = oy + 9 * z

    /** The corner post on [side] (-1 the left, 1 the right), from the roof down to the floor, its brace up to the beam under the eave. */
    private fun post(side: Int) {
        val top = eaveRow
        run {
            val x = side * 5.6f
            val d = railD
            val p = pm(d)
            val px = gx(x, d)
            val pw = max(3, (0.26f * p).toInt())
            c.fillRect((px - pw / 2f).toInt(), top, pw, (gy(d) - top).toInt() + 1, env.lit(logM))
            c.vline((px - pw / 2f).toInt(), top, gy(d).toInt(), env.lit(if (side < 0) logD else logL))
            c.vline((px + pw / 2f).toInt() - 1, top, gy(d).toInt(), env.lit(logD))
            // the brace from the post up to the beam
            val bx = gx(x - side * 1.6f, d)
            for (o in 0 until max(2, z * 2)) c.line(px.toInt() + o - z, top + 22 * z, bx.toInt() + o - z, top + 2 * z, env.lit(logD))
        }
    }

    /** The roof overhead: its underside of boards on rafters at the top of the view, the eave's shingles and the beam under them; snow on its edge in winter. */
    private fun roof() {
        val eave = eaveRow
        for (py in max(0, c.top) until min(eave, c.bottom)) for (px in max(0, c.left) until min(w, c.right)) {
            val raft = ((px - ox) / max(1f, 18f * z) + 0.5f) % 1f < 0.1f
            val board = (((py - oy) / max(1, 3 * z)) % 2 == 0)
            c.set(px, py, env.lit(if (raft) logD else if (board) plankM else plankD))
        }
        for (o in 0 until 2 * z) c.hline(0, w - 1, eave - 1 - o, if (winter && o >= z) Pal.SNOW_L else env.lit(if (o == 0) Pal.SHINGLE_D else Pal.SHINGLE_M))
        // the beam across under the eave, on the posts
        c.fillRect(0, eave, w, max(2, 2 * z), env.lit(logD))
        c.hline(0, w - 1, eave, env.lit(logM))
    }

    /** The flag on its pole off the right post, angled up into the sky: Slovenia's (Friuli's golden eagle on blue). */
    private fun flag() {
        val p = pm(railD)
        val bx = gx(5.6f, railD); val base = hy(railD, 2.6f)
        val tx = bx - 0.5f * p; val top = eaveRow + 6f * z
        for (o in 0 until max(1, z)) c.line(bx.toInt() + o, base.toInt(), tx.toInt() + o, top.toInt(), env.lit(Pal.WOOD_D))
        // the pole's tip over the cloth with its knob: the flag in a gale never flies higher than it
        val tipX = tx - 0.045f * p; val tipY = top - 0.3f * p
        for (o in 0 until max(1, z)) c.line(tx.toInt() + o, top.toInt(), tipX.toInt() + o, tipY.toInt(), env.lit(Pal.WOOD_D))
        c.fillCircle(tipX + z * 0.5f, tipY, max(1f, 0.06f * p), env.lit(Pal.GOLD))
        val fw = 1.1f * p; val fh = 0.7f * p
        val top0 = top
        for (i in 0 until fw.toInt()) {
            val f = i / fw
            val wave = sin(t * 4.0 * gust + f * 5f).toFloat() * f * 0.08f * p
            for (j in 0 until fh.toInt()) {
                val v = j / fh
                val col = if (friuli) (if (abs(f - 0.45f) < 0.15f && abs(v - 0.5f) < 0.22f) Pal.GOLD else Col.hex(0x2A5AA8))
                else when { v < 0.33f -> Pal.FLAG_WHITE; v < 0.66f -> Pal.FLAG_BLUE; else -> Pal.FLAG_RED }.let { if (f < 0.32f && v in 0.15f..0.55f && (abs(v - 0.35f) < 0.12f)) Col.hex(0x1E4A9A) else it }
                c.set((tx - z - i).toInt(), (top0 + j + wave).toInt(), env.lit(col))
            }
        }
    }

    companion object {
        /** The eye's height over the land far below (m), the tower on its hill. */
        private const val LAND_EYE = 130f

        /** The scene column of Triglav's middle head. */
        private const val TRIGLAV_U = 74f
    }
}
