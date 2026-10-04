package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.scene.Poke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * V šotoru: the learner's first home as a diorama, a canvas lean-to at the back of its own meadow. The back
 * wall stands along the left under a log beam; the roof slopes from it down to a low wall on the right and
 * leaves the front open, so we look in: the camp bed with its sleeping bag, pillow and folded red blanket, the
 * table under the lantern (lit from dusk) with a tin cup and an open book, the map pinned to the wall, the
 * backpack in the shade of the roof, the boots by the edge of the floor. The coat hangs on the corner post with
 * the hat on top, the door flap is rolled up under the roof's edge; outside, the guy ropes, a coil of rope and
 * the open meadow with a worn path across it and stepping stones.
 *
 * Closer up ([detail] 2 and 3) the same tent comes out finer: the canvas shows its weave and the stitches beside
 * its seams, the floor its nail heads and grain, the rug its weave, the lashings and the coil their twisted
 * strands, and the small things read: the cup's handle and rim, the lantern's frame, chain and wick, the book's
 * lines of text, the map's contours, the boots' laces, the sleeping bag's zip.
 *
 * Tapped ([pokes]), the lantern swings on its chain and comes to rest; tapped again it swings wider and its flame
 * gutters, the light swaying with it.
 *
 * At night whoever lives in the tent sleeps on the camp bed ([beds]: "bed"), the head on the pillow, zipped into the
 * sleeping bag, the Zzz rising; the lantern is turned low for them (unless a dialog lit it).
 */
internal class TentPainter : DioramaPainter() {
    override val art = "tent"
    override val pokes = listOf(Poke("lantern", listOf(3.0, 3.6), rest = 3.0))
    override val beds = listOf(BED)

    /** How far the lantern has swung out (picture px) and how bright its flame is (1 steady), this frame. */
    private var lampSwing = 0f
    private var lampFlame = 1f
    override val plotW = 14f
    override val plotD = 12f
    override val rise = 26f

    // the tent: the back wall at x = X0, the roof from the beam (H) at X0 down to the eave (E) at X1, over Y0..YR;
    // the floor reaches out to Y1 in front, under the open sky
    private val x0 = 1f; private val x1 = 8f
    private val y0 = 1f; private val yr = 5.5f; private val y1 = 9.6f
    private val hBeam = 28f; private val hEave = 12f
    private val fl = 1.5f
    private val slope = (hBeam - hEave) / (x1 - x0)
    private fun roofZ(x: Float) = hBeam - (x - x0) * slope

    private val coilAt = 10.6f to 2.2f

    /**
     * The stage (companion/SCENES.md, "Stage directions"): where people stand, well apart, inside on the rug ("left"),
     * outside by the tent's front ("door"), out on the meadow ("right"); and by the lantern, on the bedroll behind the
     * table, under the roof, sitting or standing ("lantern": out of the rain; the weather keeps off the tent's inside,
     * [indoors]). A walk in from outside comes through the open front.
     */
    override val marks = mapOf(
        "left" to Mark(5.0f, 7.5f, fl),
        "door" to Mark(10.0f, 10.0f, 0f, flip = true),
        "right" to Mark(12.2f, 6.2f, 0f, flip = true),
        "lantern" to Mark(2.75f, 4.85f, fl),
    )

    /** A shadow at the feet: on the floor inside, on the grass out on the meadow. */
    override fun footprint(x: Float, y: Float) {
        if (x in x0..x1 && y in y0..y1) footShadow(x, y, fl, floorId) else footShadow(x, y, 0f, groundId)
    }
    // the path from the tent across the meadow, and its stepping stones
    private val pathPts = listOf(6.4f to 9.9f, 9.2f to 11.0f, 13.9f to 9.4f)
    private val pathXY = FloatArray(pathPts.size * 2) { if (it % 2 == 0) pathPts[it / 2].first else pathPts[it / 2].second }

    private var floorId = 0

    override fun ambient(): FloatArray {
        val lift = env.dark * 0.06f
        return floatArrayOf(env.ambR + lift, env.ambG + lift, env.ambB + lift)
    }

    override fun paint() {
        fit()
        vignette()
        val asleep = peopleAt(BED).firstOrNull()
        // the lantern burns from dusk, or as a dialog has it (its "lantern": 0 dark, 1 lit); turned low for a sleeper
        val low = asleep != null && fx("lantern") == null
        val flame = fx("lantern") ?: if (env.windows > 0.35f) (if (low) LOW else env.windows) else 0f
        val lit = flame > 0.15f
        plot { x, y, px, py -> ground(x, y, px, py) }
        // tufts first: they lie flat, and whatever stands in front of them covers them
        tufts(90, 7) { x, y -> (x in x0 - 0.2f..x1 + 0.3f && y in y0 - 0.2f..y1 + 0.3f) || pathDist(x, y) < 0.5f || hypot(x - coilAt.first, y - coilAt.second) < 1f }
        prop { steppingStones() }
        // the tent's shade on the grass beside the low wall and in front of the floor
        shade(iso.sx(x1 + 0.6f, (y0 + yr) / 2), iso.sy(x1 + 0.6f, (y0 + yr) / 2, 0f), 3.2f * 4 * K, 1.1f * 4 * K, 0.8f, groundId)

        floor()
        backWall()
        thing("map", slop = 1) { map() }
        prop { post(x0 + 0.12f, yr, fl, hBeam + 2f); post(x0 + 0.12f, y1 - 0.1f, fl, hBeam + 2f) }

        // in the shade under the roof
        prop { bedroll(2.0f, 4.0f) }
        thing("backpack") { backpack(4.3f, 4.5f) }
        roof()
        prop { beam() }
        prop { post(x1, yr, 0f, hEave + 1.6f); post(x1, y0 + 0.05f, 0f, hEave + 1.6f) }
        prop { rolledFlap() }
        drips(frame.sky.rain)
        thing("coat") { coat() }
        thing("hat", slop = 1) { hat(x1, yr, hEave + 1.6f) }

        // the open front: rug, whoever sits on the bedroll behind the table, the table, lantern, bed
        rug()
        cast("lantern")
        prop { table() }
        thing("cup", slop = 2) { cup(2.95f, 6.25f, fl + 7.2f) }
        thing("book", slop = 1) { book() }
        thing("lantern", slop = 1) { lantern(2.8f, 5.75f, flame, low) }
        cast("left")
        prop { cot() }
        thing("sleepingbag") { sleepingBag() }
        thing("pillow", slop = 1) { pillow() }
        // asleep on the camp bed, the head on the pillow, zipped into the sleeping bag; the folded blanket over the feet
        asleep?.let { sleeperAt(it, 1.68f, 8.55f, fl + 5.9f, 3.85f, 8.55f, fl + 5.5f, BAG) }
        thing("blanket") { blanket() }
        thing("boots", slop = 1) { boots() }

        // outside
        prop(outline = 0) {
            rope(x1, yr, hEave, 10.5f, 7.0f, 0f, sag = 0.8f)
            rope(x1, y0 + 0.05f, hEave, 10.6f, 0.5f, 0f, sag = 0.8f)
            rope(x0, y1 + 0.2f, hBeam, 0.35f, 11.3f, 0f, sag = 1.2f)
        }
        prop { stake(10.5f, 7.0f); stake(10.6f, 0.5f); stake(0.35f, 11.3f) }
        thing("rope", slop = 1) { coil(coilAt.first, coilAt.second) }
        cast("right")
        cast("door")

        fireflies(iso.ix(3f, plotD), iso.iy(plotW, 0f, 30f), iso.ix(plotW, 3f), iso.iy(plotW, plotD, 0f), 14, 5)

        if (lit) s.light(iso.sx(2.8f, 5.75f) + lampSwing, iso.sy(2.8f, 5.75f, 15f), 34f, 0.95f * flame * lampFlame)
    }

    // ------------------------------------------------------------------ ground and floor

    private fun ground(x: Float, y: Float, px: Int, py: Int): Int {
        val g = grassAt(x, y, px, py)
        // worn along the path from the tent across the meadow (away from it, grass: no need for the noise)
        val near = 1f - pathDist(x, y) / 0.45f
        if (near <= 0f) return g
        val f = near * (0.55f + Noise.v2(x * 2f, y * 2f, 3) * 0.6f)
        return dirtAt(px, py, f.coerceIn(0f, 1f), g)
    }

    /** The distance from the path (per pixel of the ground, so without allocating). */
    private fun pathDist(x: Float, y: Float): Float {
        var best = Float.MAX_VALUE
        val p = pathXY
        var i = 0
        while (i + 3 < p.size) { best = min(best, segDist(x, y, p[i], p[i + 1], p[i + 2], p[i + 3])); i += 2 }
        return best
    }

    /** Flat stones along the path, a step apart; closer up, flecks and a lit rim on them. */
    private fun steppingStones() {
        var k = 0
        for ((a, b) in pathPts.zipWithNext()) {
            val len = hypot(b.first - a.first, b.second - a.second)
            var u = 0.6f
            while (u < len - 0.3f) {
                val f = u / len
                val x = a.first + (b.first - a.first) * f; val y = a.second + (b.second - a.second) * f
                val cx = iso.sx(x, y); val cy = iso.sy(x, y, 0f)
                c.fillEllipse(cx, cy, 2.6f * K, 1.2f * K, Pal.STONE_D)
                c.fillEllipse(cx - 0.4f * K, cy - 0.4f * K, 2.2f * K, 0.9f * K, if (winter) Pal.SNOW_L else Pal.STONE_M)
                if (detail >= 2 && !winter) flecks(cx - 0.4f * K, cy - 0.4f * K, 2.2f * K, 0.9f * K, 73 + k)
                dot(cx.toInt() - K, (cy - 0.8f * K).toInt(), if (winter) Pal.SNOW_L else Pal.STONE_L)
                u += 1.25f + Noise.rnd(k++, 71) * 0.3f
            }
        }
    }

    /** Flecks of lichen and grit over the ellipse of a stone's top, and its upper rim catching the light (closer up). */
    private fun flecks(cx: Float, cy: Float, rx: Float, ry: Float, seed: Int) {
        for (py in max(c.top, floor(cy - ry).toInt())..min(c.bottom - 1, floor(cy + ry).toInt())) {
            for (px in max(c.left, floor(cx - rx).toInt())..min(c.right - 1, floor(cx + rx).toInt())) {
                val dx = (px + 0.5f - cx) / rx; val dy = (py + 0.5f - cy) / ry
                val e = dx * dx + dy * dy
                if (e > 1f) continue
                val n = Noise.rnd(px, py, seed)
                when {
                    e > 0.72f && dy < -0.35f && dx < 0.4f -> c.set(px, py, Col.mix(Pal.STONE_M, Pal.STONE_L, 0.7f))
                    n < 0.05f -> c.set(px, py, Pal.STONE_D)
                    n > 0.975f -> c.set(px, py, Col.hex(0xA8B07A)) // a speck of lichen
                    n > 0.94f -> c.set(px, py, Pal.STONE_L)
                }
            }
        }
    }

    /** A dot of the picture: one pixel on the scene's canvas, [detail] × [detail] closer up. */
    private fun dot(x: Int, y: Int, col: Int) = c.fillRect(x, y, detail, detail, col)

    /** Canvas closer up: the weave, a pixel a shade darker every other, and now and then a slub in the thread. */
    private fun weave(col: Int, px: Int, py: Int): Int = when {
        detail < 2 -> col
        ((px + py) and 1) == 0 -> Col.scale(col, 0.955f)
        Noise.rnd(px / 3, py, 5) < 0.025f -> Col.mix(col, Col.hex(0xFFF6DA), 0.3f)
        else -> col
    }

    /**
     * A band of rope across [xa]..[xb] from row [y], [detail] rows thick: a lashing's wrap. Closer up it is round
     * (lit on top, shaded below) and twisted (a strand's shadow every third pixel along the diagonal).
     */
    private fun wrap(xa: Int, xb: Int, y: Int, col: Int) {
        if (detail == 1) { c.hline(xa, xb, y, col); return }
        val lit = Col.mix(col, Col.hex(0xFFFFFF), 0.3f); val dark = Col.scale(col, 0.7f)
        for (r in 0 until detail) for (x in min(xa, xb)..max(xa, xb)) {
            val base = when (r) { 0 -> lit; detail - 1 -> dark; else -> col }
            c.set(x, y + r, if ((x + r) % 3 == 0 && r < detail - 1) Col.scale(col, 0.82f) else base)
        }
    }

    /** A line blended over what is there, keeping its ids: a streak of grain, a shadow. */
    private fun blendLine(xa: Int, ya: Int, xb: Int, yb: Int, col: Int, a: Float) {
        var x = xa; var y = ya
        val dx = abs(xb - xa); val dy = -abs(yb - ya)
        val sx = if (xa < xb) 1 else -1; val sy = if (ya < yb) 1 else -1
        var err = dx + dy
        while (true) {
            c.blend(x, y, col, a)
            if (x == xb && y == yb) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    /** A chain hanging from ([x], [y0]) to [y1]: 1 px on the scene's canvas, closer up links, one face on, the next edge on. */
    private fun chainDown(x: Int, y0: Int, y1: Int, dark: Int, light: Int) {
        if (detail == 1) { c.vline(x, y0, y1, dark); return }
        val q = detail
        var y = y0
        var m = 0
        while (y <= y1) {
            val bottom = min(y1, y + 2 * q - 1)
            if (m % 2 == 0) {
                // face on: a ring, lit on the left
                for (yy in y..bottom) {
                    val end = yy == y || yy == bottom
                    if (end) c.set(x, yy, dark)
                    else { c.set(x - 1, yy, light); c.set(x + 1, yy, dark) }
                }
            } else c.vline(x, y - 1, bottom + 1, if (m % 4 == 1) light else dark) // edge on, through the rings
            y += 2 * q - 1; m++
        }
    }

    private fun segDist(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val u = (((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        return hypot(x - ax - dx * u, y - ay - dy * u)
    }

    /** The plank floor, a hand's breadth off the grass: its front edges show the board ends. */
    private fun floor() {
        floorId = s.newObject(Pal.OUTLINE)
        iso.panelY(x0, y1, 0f, x1 - x0, fl, Pal.WOOD_D)
        iso.panelX(x1, y0, 0f, y1 - y0, fl, Pal.WOOD_X)
        var bx = x0
        while (bx < x1) { iso.line(bx, y1, 0f, bx, y1, fl, Col.hex(0x4A2D1A)); bx += 0.55f }
        planks(x0, y0, x1, y1, fl)
        if (detail >= 2) boardDetail()
        c.penId = 0
    }

    /**
     * Closer up, the floor boards' grain (thin darker streaks along each board) and their nail heads: two at each
     * end of a board, where the base's [planks] puts its joints (boards 0.55 wide along y, a joint every 2.6).
     */
    private fun boardDetail() {
        val width = 0.55f
        val grain = Col.hex(0x4E3019)
        var board = 0
        while (x0 + board * width < x1 - 0.05f) {
            // streaks of grain along the board
            for (g in 0 until 3) {
                val gx = x0 + (board + 0.18f + Noise.rnd(board, g, 81) * 0.64f) * width
                if (gx >= x1) continue
                var gy = y0 + Noise.rnd(board, g, 82) * 1.5f
                while (gy < y1 - 0.2f) {
                    val len = 0.5f + Noise.rnd(board, (gy * 10f).toInt(), 83) * 1.3f
                    val e = min(gy + len, y1 - 0.05f)
                    blendLine(iso.ix(gx, gy), iso.iy(gx, gy, fl), iso.ix(gx, e), iso.iy(gx, e, fl), grain, 0.28f)
                    gy = e + 0.4f + Noise.rnd(board, (gy * 10f).toInt(), 84) * 1.2f
                }
            }
            // the nails: at the floor's two ends and on both sides of every joint
            val off = Noise.rnd(board, 9) * 3f
            val rows = ArrayList<Float>()
            rows.add(y0 + 0.14f); rows.add(y1 - 0.14f)
            var v = ceil(off).toInt()
            while (true) {
                val jy = y0 + (v - off) * 2.6f
                if (jy > y1) break
                if (jy > y0 + 0.2f && jy < y1 - 0.2f) { rows.add(jy - 0.12f); rows.add(jy + 0.14f) }
                v++
            }
            for (ny in rows) for (s in floatArrayOf(0.3f, 0.72f)) {
                val nx = x0 + (board + s) * width
                if (nx >= x1 - 0.05f) continue
                nail(iso.ix(nx, ny), iso.iy(nx, ny, fl))
            }
            board++
        }
    }

    /** A nail head: a dark dot, closer still with a glint. */
    private fun nail(x: Int, y: Int) {
        c.set(x, y, Col.hex(0x3A2616))
        if (detail >= 3) { c.set(x + 1, y, Col.hex(0x3A2616)); c.set(x, y - 1, Col.hex(0x8A7A6A)) }
    }

    private fun footShadow(x: Float, y: Float, z: Float, id: Int) =
        shade(iso.sx(x, y), iso.sy(x, y, z), 5f * K, 1.6f * K, 0.72f, id, id + 1)

    // ------------------------------------------------------------------ the canvas

    /** The back wall's inner face: canvas in the shade, seams, a damp hem at the floor, the lantern's warmth near it. */
    private fun backWall() {
        part(Pal.OUTLINE)
        quadFill(x0, y0, fl, x0, y1 + 0.2f, fl, x0, y1 + 0.2f, hBeam, x0, y0, hBeam) { px, py ->
            val y = wallXy(px, x0); val z = wallXz(px, py, x0)
            var col = Pal.CANVAS_D
            val fold = Noise.v1(y * 3.1f, 13)
            if (fold > 0.7f) col = Col.scale(col, 0.93f)
            else if (fold < 0.18f && Dither.at(px, py) < 0.5f) col = Col.mix(col, Pal.CANVAS_M, 0.6f)
            val seam = (y - y0) / 2.05f
            val fs = seam - floor(seam)
            if (fs < 0.03f) col = Col.scale(Pal.CANVAS_D, 0.82f)
            else if (detail >= 2) {
                col = weave(col, px, py)
                // the stitches beside the seam, and along the hem at the top
                val sd = min(fs, 1f - fs) * 2.05f
                if (abs(sd - 0.12f) < 0.13f / K && ((z * 2.2f).toInt() and 1) == 0) col = Col.scale(Pal.CANVAS_D, 0.9f)
                if (abs(z - (hBeam - 1.6f)) < 0.5f / K && ((y * 9f).toInt() and 1) == 0) col = Col.scale(Pal.CANVAS_D, 0.95f)
            }
            if (z < fl + 2.2f) col = Col.mix(col, Col.hex(0x7A6448), if (z < fl + 1.2f) 0.55f else 0.3f)
            if (z > hBeam - 1.4f) col = Col.mix(col, Pal.CANVAS_M, 0.5f)
            col
        }
        c.penId = 0
    }

    /**
     * The roof, from the beam down to the eave: pale canvas, darker toward the eave, sagging folds that run down
     * the slope, two seams, a hem along the front edge; snow on it in winter.
     */
    private fun roof() {
        part(Pal.OUTLINE)
        quadFill(x0, y0 - 0.25f, hBeam, x1 + 0.35f, y0 - 0.25f, hEave - 0.8f, x1 + 0.35f, yr, hEave - 0.8f, x0, yr, hBeam) { px, py ->
            val a = ua(px); val b = vb(py)
            val x = (b + 2f * a + hBeam + x0 * slope) / (4f + slope)
            val y = x - a
            roofColor(px, py, (x - x0) / (x1 - x0), y)
        }
        // the hem along the front edge and the eave, catching the light
        iso.line(x0, yr, hBeam, x1 + 0.35f, yr, hEave - 0.8f, Pal.CANVAS_L)
        iso.line(x0 + 0.2f, yr - 0.12f, hBeam - 0.4f, x1 + 0.3f, yr - 0.12f, hEave - 0.6f, Col.scale(Pal.CANVAS_M, 0.9f))
        c.penId = 0
        // the low wall under the eave: the outer face, with a valance, straps and a mud-stained hem
        part(Pal.OUTLINE)
        quadFill(x1, y0, 0f, x1, yr, 0f, x1, yr, hEave, x1, y0, hEave) { px, py ->
            val y = wallXy(px, x1); val z = wallXz(px, py, x1)
            var col = Col.mix(Pal.CANVAS_M, Pal.CANVAS_D, 0.55f)
            val fold = Noise.v1(y * 3.7f, 19)
            if (fold > 0.72f) col = Col.scale(col, 0.92f)
            val strap = (y - y0 + 0.35f) / 1.5f
            val fs = strap - floor(strap)
            when {
                z > hEave - 3.2f && z < hEave - 2.7f -> col = Col.scale(Pal.CANVAS_D, 0.85f)
                z >= hEave - 2.7f -> col = Col.mix(Pal.CANVAS_M, Pal.CANVAS_L, 0.3f)
                z < 1.8f -> col = Col.mix(col, Col.hex(0x6A5A60), 0.65f)
            }
            if (fs < 0.06f && z in hEave - 5.5f..hEave - 1.2f) col = Col.hex(0x6F4527)
            else if (detail >= 2) col = weave(col, px, py)
            if (detail >= 2 && z > hEave - 2.7f && abs(z - (hEave - 2.35f)) < 0.5f / K && ((y * 3f * K).toInt() and 1) == 0) col = Col.mix(col, Pal.CANVAS_D, 0.6f)
            // closer up, a buckle on each strap
            if (detail >= 2 && fs < 0.1f && abs(z - (hEave - 4.3f)) < 0.55f) {
                val edge = fs < 0.1f / 6f || fs > 0.1f * 5f / 6f || abs(z - (hEave - 4.3f)) > 0.55f - 0.9f / K
                col = if (edge) Col.hex(0xB08A3A) else if (fs < 0.06f) Col.hex(0x6F4527) else col
            }
            if (winter && z < 1.2f) col = Pal.SNOW_M
            wet(col)
        }
        c.penId = 0
    }

    private fun roofColor(px: Int, py: Int, u: Float, y: Float): Int {
        // lighter near the beam, darker toward the eave, in dithered steps
        val shadeT = (u * u * 0.8f + (Dither.at(px, py) - 0.5f) * 0.25f)
        var col = when {
            shadeT < 0.18f -> Pal.CANVAS_L
            shadeT < 0.5f -> Col.mix(Pal.CANVAS_L, Pal.CANVAS_M, 0.5f)
            else -> Pal.CANVAS_M
        }
        // the folds run down the slope and fan out a little toward the eave
        val fold = Noise.v1(y * 2.6f + u * 0.9f, 7)
        if (fold > 0.7f) col = Col.mix(col, Pal.CANVAS_D, 0.55f)
        else if (fold > 0.62f && Dither.at(px, py) < 0.5f) col = Col.mix(col, Pal.CANVAS_D, 0.35f)
        else if (fold < 0.2f) col = Col.mix(col, Col.hex(0xFFF6DA), 0.35f)
        // two seams across the slope
        val sa = abs(y - (y0 + (yr - y0) / 3f)); val sb = abs(y - (y0 + (yr - y0) * 2f / 3f))
        val seam = min(sa, sb)
        if (seam < 0.05f) col = Col.scale(Pal.CANVAS_D, 0.92f)
        else if (detail >= 2) {
            col = weave(col, px, py)
            // closer up: a row of stitches either side of each seam, along the front hem and above the eave's hem
            val dash = ((u * 12f * K).toInt() and 1) == 0
            val thin = 0.13f / K
            if (dash && (abs(seam - 0.11f) < thin || abs(y - (yr - 0.24f)) < thin)) col = Col.mix(col, Pal.CANVAS_D, 0.7f)
            if (abs(u - 0.935f) * (x1 - x0) < thin * 1.6f && ((y * 3f * K).toInt() and 1) == 0) col = Col.mix(col, Pal.CANVAS_D, 0.7f)
        }
        if (u > 0.97f) col = Col.scale(Pal.CANVAS_D, 0.9f)
        if (winter) {
            val cover = 0.75f - u * 0.35f + (Noise.v2(u * 6f, y * 3f, 3) - 0.5f) * 0.4f
            if (cover > 0.3f) col = if (cover > 0.55f || Dither.at(px, py) < 0.5f) Pal.SNOW_L else Pal.SNOW_M
        }
        return wet(col)
    }

    /** Canvas out in the rain goes darker, the more the harder it rains. */
    private fun wet(col: Int): Int {
        val rain = frame.sky.rain
        return if (rain <= 0f) col else Col.scale(col, 1f - 0.24f * rain)
    }

    // ------------------------------------------------------------------ under the canvas: no weather in the tent

    /**
     * The ground under the canvas: the roof's whole span from the back wall out past the eave, and over the floor to its
     * front, as if the roof went on there too (it is only cut away for us to look in).
     */
    override fun sheltered(x: Float, y: Float): Boolean = x > x0 - 0.1f && x < x1 + 0.35f && y > y0 - 0.25f && y < y1 + 0.2f

    /**
     * Looking into the tent past its open front: the pixel's line of sight passes through the space over the floor where
     * the roof would go on (x0..x1 × yr..y1, from the floor up to the roof's slope). Along the line x + y = s, and then
     * x = (s + u) / 2, y = (s − u) / 2 and z = 2s − b ([ua] and [vb] of the pixel's column and row): each bound of the
     * space bounds s, and the pixel looks in when some s lies within them all.
     */
    override fun indoors(px: Int, py: Int): Boolean {
        val u = ua(px); val b = vb(py)
        val lo = maxOf(2f * x0 - u, 2f * yr + u, (fl + b) / 2f)
        val hi = minOf(2f * x1 - u, 2f * y1 + u, (hBeam + b - (u / 2f - x0) * slope) / (2f + slope / 2f))
        return lo < hi
    }

    /**
     * In the rain, water runs down the roof to its eave and off it: drops swell along the eave, fall past the low wall
     * and splash on the grass, the more the harder it rains. None off the roof's front edge by the rolled flap: the roof
     * goes on over the floor there, only cut away for us to look in ([indoors]), so nothing drips into the tent.
     * Blended over what is there (what stands in front, drawn after, hides them), lit with the rest.
     */
    private fun drips(level: Float) {
        if (level <= 0.05f) return
        val col = Col.hex(0xB8CCE4)
        val d = detail
        val n = (4 + 6 * level).toInt()
        val x = x1 + 0.4f
        for (j in 0 until n) {
            val f = (j + 0.5f + (Noise.rnd(j, 71) - 0.5f) * 0.6f) / n
            val y = y0 + 0.2f + f * (yr - y0 - 0.3f)
            val top = iso.iy(x, y, hEave - 0.8f); val bottom = iso.iy(x, y, 0f)
            val sx = iso.ix(x, y)
            val period = 0.8 + Noise.rnd(j, 72) * 0.9
            val u = (((t + Noise.rnd(j, 73) * 5.0) / period) % 1.0).toFloat()
            when {
                // a bead swelling on the edge, then falling faster and faster, then a splash
                u < 0.35f -> drop(sx, top, if (u > 0.2f) 2 * d else d, col, 0.8f)
                u < 0.8f -> { val v = (u - 0.35f) / 0.45f; drop(sx, top + ((bottom - top) * v * v).toInt(), 2 * d, col, 0.75f) }
                u < 0.92f -> for (o in -2 * d..2 * d) c.blend(sx + o, bottom - (if (abs(o) > d) 0 else d), col, 0.55f)
            }
        }
    }

    /** A drop [bh] picture rows tall and a pixel of the picture wide at ([x], [y]), blended over what is there. */
    private fun drop(x: Int, y: Int, bh: Int, col: Int, a: Float) {
        for (yy in y until y + bh) for (xx in x until x + detail) c.blend(xx, yy, col, a)
    }

    /** The log beam along the top of the back wall, lashed to the posts. */
    private fun beam() {
        val a0 = y0 - 0.5f; val a1 = y1 + 0.35f
        // a nominal px thick, a picture row per line; closer up, rounder (a lit and a shaded row more) with bark
        val lit = Col.mix(Pal.LOG_L, Pal.LOG_M, 0.5f); val shaded = Col.mix(Pal.LOG_M, Pal.LOG_D, 0.5f)
        for (d in 0..K) iso.line(x0, a0, hBeam + d.toFloat() / K, x0, a1, hBeam + d.toFloat() / K, when {
            d == K -> Pal.LOG_L; d == 0 -> Pal.LOG_D; detail >= 2 && d == K - 1 -> lit; detail >= 2 && d == 1 -> shaded; else -> Pal.LOG_M
        })
        for (d in 0..K) iso.line(x0 + 0.1f, a0, hBeam + d.toFloat() / K, x0 + 0.1f, a1, hBeam + d.toFloat() / K, when {
            d == K -> Pal.LOG_L; detail >= 2 && d == K - 1 -> lit; else -> Pal.LOG_M
        })
        if (detail >= 2) for (m in 0 until 16) {
            // furrows in the bark
            val ya = a0 + 0.2f + Noise.rnd(m, 31) * (a1 - a0 - 0.8f)
            val zz = hBeam + (1 + (Noise.rnd(m, 32) * (K - 2)).toInt()).toFloat() / K
            iso.line(x0 + 0.1f, ya, zz, x0 + 0.1f, ya + 0.2f + Noise.rnd(m, 33) * 0.35f, zz, Col.scale(Pal.LOG_M, 0.78f))
        }
        // the log ends and the lashings
        val ex = iso.sx(x0, a1) - 0.5f * detail; val ey = iso.sy(x0, a1, hBeam + 0.6f)
        c.fillCircle(ex, ey, K * 1.1f, Pal.LOG_L)
        if (detail >= 2) {
            // its rings round the heart
            c.fillCircle(ex, ey, K * 0.72f, Col.mix(Pal.LOG_L, Pal.LOG_M, 0.55f))
            c.fillCircle(ex, ey, K * 0.5f, Pal.LOG_L)
            c.fillCircle(ex, ey, K * 0.22f, Col.mix(Pal.LOG_L, Pal.LOG_M, 0.55f))
        }
        dot(iso.ix(x0, a1), iso.iy(x0, a1, hBeam + 0.6f), Pal.LOG_D)
        for (yy in floatArrayOf(yr, y1 - 0.1f)) for (j in 0 until 3) {
            val bx = iso.ix(x0 + 0.12f, yy) - K / 2; val by = iso.iy(x0 + 0.12f, yy, hBeam + 1f) + (j * 2 - 2) * detail
            wrap(bx - detail, bx + K + detail, by, Col.hex(0xD8C49A))
        }
    }

    /** The door flap, rolled up under the roof's front edge and tied, the tie ends hanging. */
    private fun rolledFlap() {
        val yf = yr + 0.1f
        val ax = iso.ix(x0 + 0.35f, yf); val ay = iso.iy(x0 + 0.35f, yf, hBeam - 1.6f)
        val bx = iso.ix(x1 - 0.15f, yf); val by = iso.iy(x1 - 0.15f, yf, hEave - 1.3f)
        val tones = intArrayOf(Pal.CANVAS_L, Pal.CANVAS_L, Pal.CANVAS_M, Pal.CANVAS_M, Col.mix(Pal.CANVAS_M, Pal.CANVAS_D, 0.5f), Pal.CANVAS_D)
        val n = tones.size * detail
        // a row of the roll per picture row: closer up, shaded smoothly round, with the edges of the turns in it
        for (r in 0 until n) c.line(ax, ay + r, bx, by + r, if (detail == 1) tones[r] else when {
            r == n / 3 || r == n * 2 / 3 + 1 -> Col.mix(Pal.CANVAS_M, Pal.CANVAS_D, 0.6f)
            else -> {
                val f = (r + 0.5f) / detail - 0.5f
                val i = floor(f).toInt().coerceIn(0, tones.size - 1)
                Col.mix(tones[i], tones[min(i + 1, tones.size - 1)], (f - i).coerceIn(0f, 1f))
            }
        })
        // the spiral of the roll at its near end
        if (detail == 1) { c.set(bx, by + 2, Pal.CANVAS_D); c.set(bx - 1, by + 3, Pal.CANVAS_D) }
        else {
            val rr = n / 2f
            val ex = bx + 0.5f; val ey = by + rr
            c.fillEllipse(ex, ey, rr * 0.55f, rr, Col.mix(Pal.CANVAS_L, Pal.CANVAS_M, 0.4f))
            val steps = 40 * detail
            for (i in 0 until steps) {
                val f = i / steps.toFloat()
                val a = f * 2.6f * 6.283f
                val rad = rr * (0.12f + 0.8f * f)
                c.set(floor(ex + cos(a) * rad * 0.55f).toInt(), floor(ey + sin(a) * rad).toInt(), Pal.CANVAS_D)
            }
        }
        // two ties, their ends hanging (closer up, a knot and a shaded edge)
        val tie = Col.hex(0x8A6A48)
        for (f in floatArrayOf(0.3f, 0.72f)) {
            val tx = (ax + (bx - ax) * f).toInt(); val ty = (ay + (by - ay) * f).toInt()
            sprite(tx, ty) {
                c.vline(tx, ty - 1, ty + tones.size, tie); c.vline(tx + 1, ty - 1, ty + tones.size, tie)
                c.vline(tx, ty + tones.size, ty + tones.size + 2 * BASE_K, tie); c.set(tx + 1, ty + tones.size + 2 * BASE_K - 1, tie)
            }
            if (detail >= 2) {
                val q = detail
                c.vline(tx + 2 * q - 1, ty - q, ty + n + q - 1, Col.scale(tie, 0.7f))
                c.vline(tx, ty - q, ty + n + q - 1, Col.mix(tie, Col.hex(0xFFFFFF), 0.2f))
                c.fillEllipse(tx + q.toFloat(), ty + n + q * 0.4f, q * 1.25f, q * 0.8f, Col.scale(tie, 0.85f))
                c.set(tx + q - 1, ty + n, Col.mix(tie, Col.hex(0xFFFFFF), 0.3f))
                // a frayed end
                c.set(tx + q / 2, ty + n + (2 * BASE_K + 1) * q - 1, Col.scale(tie, 0.8f))
            }
        }
    }

    // ------------------------------------------------------------------ the things

    /** A map pinned to the back wall: parchment, green land, a blue lake and river, a red dotted path to an X. */
    private fun map() {
        val ya = 6.35f; val yb = 8.95f; val za = fl + 11f; val zb = fl + 20f
        val xm = x0 + 0.04f
        quadFill(xm, ya, za, xm, yb, za, xm, yb, zb, xm, ya, zb) { px, py ->
            val y = wallXy(px, xm); val z = wallXz(px, py, xm)
            val u = (y - ya) / (yb - ya); val v = (z - za) / (zb - za)
            val edge = min(min(u, 1 - u) * (yb - ya) * 4f, min(v, 1 - v) * (zb - za) / 1.2f)
            val land = Noise.v2(u * 5f, v * 4f, 21)
            val river = abs(v - 0.35f - sin(u * 7f) * 0.12f) < 0.05f
            val lake = hypot((u - 0.72f) * 1.4f, v - 0.7f) < 0.13f
            val path = abs(v - 0.62f + u * 0.4f - sin(u * 11f) * 0.05f) < 0.035f && ((u * 14f).toInt() % 2 == 0)
            val x = hypot(u - 0.22f, v - 0.2f) < 0.06f && (abs((u - 0.22f) - (v - 0.2f)) < 0.025f || abs((u - 0.22f) + (v - 0.2f)) < 0.025f)
            val col = when {
                edge < 0.5f -> Col.hex(0xB89868)
                x -> Pal.FLAG_RED
                path -> Col.hex(0xC0392B)
                lake || river -> if (Dither.at(px, py) < 0.7f) Col.hex(0x5B8FC0) else Col.hex(0x7FB0DA)
                land > 0.62f -> Col.hex(0x6F9A4A)
                land > 0.48f -> Col.hex(0x9BBA64)
                else -> Col.hex(0xE6D7A4)
            }
            if (detail < 2 || edge < 0.5f || x || path) col
            else when {
                // closer up: an inked border inside the paper's edge, the shores and the contours of the hills,
                // a dot of a village by the river, and the paper's grain
                edge < 0.5f + 1.2f / K -> Col.hex(0x8A6A48)
                (lake || river) && (abs(hypot((u - 0.72f) * 1.4f, v - 0.7f) - 0.13f) < 0.012f || abs(abs(v - 0.35f - sin(u * 7f) * 0.12f) - 0.05f) < 0.008f) -> Col.hex(0x3E6A96)
                !lake && !river && (abs(land - 0.62f) < 0.011f || abs(land - 0.74f) < 0.009f) -> Col.hex(0x4E7A34)
                !lake && !river && abs(land - 0.48f) < 0.009f -> Col.hex(0x7E9A4E)
                hypot((u - 0.5f) * 1.3f, v - 0.46f) < 0.035f -> Col.hex(0x6A3A24)
                Noise.rnd(px, py, 27) < 0.04f -> Col.scale(col, 0.92f)
                else -> col
            }
        }
        // the pins (closer up, round heads with a glint)
        for ((yy, zz) in listOf(ya + 0.15f to zb - 0.5f, yb - 0.15f to zb - 0.5f)) {
            val px = iso.ix(xm, yy); val py = iso.iy(xm, yy, zz)
            if (detail == 1) { c.set(px, py, Pal.FLAG_RED); c.set(px + 1, py, Pal.GERANIUM_D); c.set(px, py - 1, Col.hex(0xFF8A80)) }
            else {
                val q = detail.toFloat()
                c.fillCircle(px + q, py + 0.1f * q, q * 0.95f, Pal.GERANIUM_D)
                c.fillCircle(px + q * 0.85f, py - 0.05f * q, q * 0.72f, Pal.FLAG_RED)
                c.set((px + q * 0.6f).toInt(), (py - q * 0.35f).toInt(), Col.hex(0xFFC0B8))
            }
        }
    }

    /** A rolled sleeping mat in the shade, tied with two straps. */
    private fun bedroll(x: Float, y: Float) {
        val l = Col.hex(0x5E8A6A); val m = Col.hex(0x46705A); val d = Col.hex(0x2F5040)
        iso.box(x, y, fl, 1.5f, 0.7f, 2.6f, l, m, d)
        c.fillEllipse(iso.sx(x + 1.5f, y + 0.35f) + K * 0.5f, iso.sy(x + 1.5f, y + 0.35f, fl + 1.3f), K * 2.2f, K * 1.8f, d)
        c.fillEllipse(iso.sx(x + 1.5f, y + 0.35f) + K * 0.5f, iso.sy(x + 1.5f, y + 0.35f, fl + 1.3f), K * 1.1f, K * 0.9f, m)
        if (detail >= 2) {
            // closer up, the turns of the mat in its end
            val ex = iso.sx(x + 1.5f, y + 0.35f) + K * 0.5f; val ey = iso.sy(x + 1.5f, y + 0.35f, fl + 1.3f)
            val steps = 36 * detail
            for (i in 0 until steps) {
                val f = i / steps.toFloat(); val a = f * 2.3f * 6.283f
                c.set(floor(ex + cos(a) * K * 2.0f * (0.2f + 0.8f * f)).toInt(), floor(ey + sin(a) * K * 1.65f * (0.2f + 0.8f * f)).toInt(), d)
            }
        }
        for (sx in floatArrayOf(0.35f, 1.15f)) iso.line(x + sx, y + 0.7f, fl, x + sx, y + 0.7f, fl + 2.6f, Col.hex(0x6F4527))
        if (detail >= 2) for (sx in floatArrayOf(0.35f, 1.15f)) iso.px(x + sx + 0.02f, y + 0.72f, fl + 1.9f, Pal.GOLD_L)
    }

    /** The backpack: a canvas body, a darker flap, two leather straps and a side pocket. */
    private fun backpack(x: Float, y: Float) {
        val l = Col.hex(0x9A8A56); val m = Col.hex(0x7A6A3E); val d = Col.hex(0x5A4E2E)
        iso.box(x, y, fl, 0.95f, 0.8f, 5.5f, l, m, d)
        // the flap over the top and down the front
        iso.top(x - 0.03f, y - 0.03f, fl + 5.7f, 1.0f, 0.86f, Col.scale(l, 0.9f))
        iso.panelY(x, y + 0.83f, fl + 3.4f, 0.95f, 2.3f, Col.scale(m, 0.85f))
        if (detail >= 2) {
            // closer up: the stitching round the flap and down the body's corners
            var sx = x + 0.06f
            while (sx < x + 0.9f) { iso.line(sx, y + 0.84f, fl + 3.4f + 1.2f / K, sx + 0.05f, y + 0.84f, fl + 3.4f + 1.2f / K, Col.scale(l, 1.1f)); sx += 0.12f }
            var sz = fl + 0.4f
            while (sz < fl + 5.2f) { iso.px(x + 0.9f, y + 0.8f, sz, Col.scale(m, 0.7f)); sz += 0.9f }
        }
        iso.line(x, y + 0.83f, fl + 3.4f, x + 0.95f, y + 0.83f, fl + 3.4f, Col.scale(d, 0.8f))
        // straps and buckles
        val strap = Col.hex(0x6F4527)
        for (sx in floatArrayOf(0.25f, 0.7f)) {
            iso.line(x + sx, y + 0.84f, fl + 0.6f, x + sx, y + 0.84f, fl + 5.6f, strap)
            iso.px(x + sx, y + 0.84f, fl + 3.3f, Pal.GOLD_L)
        }
        // the side pocket
        iso.box(x + 0.96f, y + 0.2f, fl + 0.8f, 0.2f, 0.45f, 2.4f, l, m, Col.scale(d, 0.9f))
        // a tin mug hooked on the side
        iso.px(x + 1.0f, y + 0.72f, fl + 4.2f, Col.hex(0xA8B4C2))
    }

    /** The corner coat: a wool coat on a peg, collar up, a row of buttons. */
    private fun coat() {
        val yc = yr + 0.14f
        val l = Col.hex(0x5A7AA6); val m = Col.hex(0x3E5C86); val d = Col.hex(0x2C4466)
        val top = hEave - 1.2f; val bottom = fl + 1.2f
        c.polyBegin()
        fun v(x: Float, z: Float) = c.polyAdd(iso.sx(x, yc), iso.sy(x, yc, z))
        v(7.62f, top + 0.8f); v(7.95f, top); v(8.0f, bottom); v(7.1f, bottom); v(7.25f, top - 1.6f); v(7.45f, top)
        c.polyFill { px, py ->
            val x = wallYx(px, yc); val z = wallYz(px, py, yc)
            when {
                x < 7.3f + (top - z) * 0.01f -> d                       // the sleeve's shade
                abs(x - 7.62f) < 0.05f && z < top - 1f -> d               // the front opening
                abs(x - 7.72f) < 0.05f && z < top - 1f && ((z * 1.3f).toInt() % 2 == 0) -> Pal.GOLD
                z > top - 0.9f && x > 7.45f -> l                          // the collar
                else -> m
            }
        }
        // the peg
        iso.px(x1 - 0.05f, yc, top + 0.6f, Pal.WOOD_D)
    }

    /** A felt hat sitting on the corner post's top. */
    private fun hat(x: Float, y: Float, z: Float) {
        val cx = iso.sx(x, y); val cy = iso.sy(x, y, z)
        val felt = Col.hex(0x6A4E38); val feltD = Col.hex(0x4A3526); val feltL = Col.hex(0x8A6A4E)
        c.fillEllipse(cx, cy - 0.5f * K, 3.4f * K, 1.25f * K, feltD)
        c.fillEllipse(cx, cy - 0.9f * K, 3.1f * K, 1.0f * K, felt)
        c.fillRect((cx - 1.8f * K).toInt(), (cy - 4f * K).toInt(), (3.6f * K).toInt(), (3f * K).toInt(), felt)
        c.fillEllipse(cx, cy - 4f * K, 1.8f * K, 0.7f * K, feltL)
        val q = detail
        if (q >= 2) {
            // closer up: the dent in the crown, the felt's nap, a lit edge on the brim
            c.hline((cx - 0.9f * K).toInt(), (cx + 0.7f * K).toInt(), (cy - 4f * K).toInt(), felt)
            for (py in (cy - 3.8f * K).toInt() until (cy - 1.6f * K).toInt()) for (px in (cx - 1.8f * K).toInt() until (cx + 1.8f * K).toInt() - q) {
                if (Noise.rnd(px, py, 61) < 0.07f) c.set(px, py, if (Noise.rnd(px, py, 62) < 0.5f) feltL else feltD)
            }
            for (px in (cx - 3.0f * K).toInt()..(cx + 3.0f * K).toInt()) {
                val dx = (px + 0.5f - cx) / (3.1f * K)
                if (abs(dx) >= 1f || abs(px + 0.5f - cx) < 1.8f * K) continue
                c.set(px, ceil(cy - 0.9f * K - 1.0f * K * kotlin.math.sqrt(1f - dx * dx) - 0.5f).toInt(), if (dx < 0f) feltL else felt)
            }
        }
        // the band: a picture row lighter along its top closer up
        for (r in 0 until q) c.hline((cx - 1.8f * K).toInt(), (cx + 1.8f * K).toInt() - 1, (cy - 1.6f * K).toInt() + r, if (q >= 2 && r == 0) Col.hex(0x4A3530) else Col.hex(0x2A1D1A))
        for (o in 1..q) c.vline((cx + 1.8f * K).toInt() - o, (cy - 3.8f * K).toInt(), (cy - 1.8f * K).toInt(), feltD)
        // a feather in the band
        if (q == 1) { c.set((cx - 1.9f * K).toInt(), (cy - 2.5f * K).toInt(), Pal.FLAG_WHITE); c.set((cx - 2.2f * K).toInt(), (cy - 3.2f * K).toInt(), Pal.FLAG_WHITE) }
        else {
            // its quill from the band up and out, barbs either side, a grey tip
            val fx0 = cx - 1.7f * K; val fy0 = cy - 1.8f * K
            val fx1 = cx - 2.35f * K; val fy1 = cy - 3.6f * K
            val steps = (fy0 - fy1).toInt()
            for (i in 0..steps) {
                val f = i / steps.toFloat()
                val x = fx0 + (fx1 - fx0) * f; val y = fy0 + (fy1 - fy0) * f
                val half = (q * 0.9f * sin(f * 3.1f)).coerceAtLeast(0f)
                for (b in 1..half.toInt()) { c.set((x - b).toInt(), (y - b * 0.4f).toInt(), if (f > 0.8f) Col.hex(0x9AA0AE) else Pal.FLAG_WHITE); c.set((x + b).toInt(), (y - b * 0.4f).toInt(), if (f > 0.8f) Col.hex(0x7A808E) else Col.hex(0xD8DCE6)) }
                c.set(x.toInt(), y.toInt(), Col.hex(0xB8BCC6))
            }
        }
    }

    /** A rug in red and ochre checks on the floor. */
    private fun rug() {
        part(Pal.OUTLINE)
        val ra = 2.6f; val rb = 6.6f; val rc = 6.25f; val rd = 9.45f
        val z = fl + 0.05f
        quadFill(ra, rc, z, rb, rc, z, rb, rd, z, ra, rd, z) { px, py ->
            val x = flatX(px, py, z); val y = flatY(px, py, z)
            val edge = min(min(x - ra, rb - x), min(y - rc, rd - y))
            val cu = ((x - ra) / 0.5f).toInt(); val cv = ((y - rc) / 0.5f).toInt()
            val fu = ((x - ra) / 0.5f) % 1f; val fv = ((y - rc) / 0.5f) % 1f
            val stripeX = fu < 0.18f; val stripeY = fv < 0.18f
            val col = when {
                edge < 0.12f -> Col.hex(0x6E2A1E)
                edge < 0.2f -> Col.hex(0xE0B060)
                stripeX && stripeY -> Col.hex(0xE8C070)
                stripeX || stripeY -> Col.hex(0xC98A3A)
                (cu + cv) % 2 == 0 -> Col.hex(0xB8452E)
                else -> Col.hex(0x9A3426)
            }
            if (detail < 2) col
            else when {
                // closer up: a little diamond in each check, a zigzag in the border, the weave over all
                !stripeX && !stripeY && edge >= 0.2f && abs(fu - 0.59f) + abs(fv - 0.59f) < 0.13f -> if ((cu + cv) % 2 == 0) Col.hex(0xE0A050) else Col.hex(0xD8B070)
                edge in 0.12f..0.2f && abs(edge - 0.16f) < 0.025f + 0.02f * sin((x + y) * 31f) -> Col.hex(0x9A3426)
                ((px + py) and 1) == 0 -> Col.scale(col, 0.92f)
                else -> col
            }
        }
        // fringes along the front edge (closer up, twice as many threads)
        var fx = ra + 0.1f
        val step = if (detail == 1) 0.22f else 0.11f
        var i = 0
        while (fx < rb) { iso.line(fx, rd, z, fx - 0.04f, rd + 0.18f, z, if (detail >= 2 && i % 2 == 1) Col.hex(0xD0BC88) else Col.hex(0xE8D6A0)); fx += step; i++ }
        c.penId = 0
    }

    /** The table against the back wall, under the lantern. */
    private fun table() {
        val xa = 1.2f; val xb = 3.4f; val ya = 5.9f; val yb = 7.4f
        val top = fl + 7f
        val leg = Pal.WOOD_D
        iso.post(xa + 0.15f, yb - 0.15f, fl, top, leg)
        iso.post(xb - 0.15f, yb - 0.15f, fl, top, leg)
        iso.post(xb - 0.15f, ya + 0.15f, fl, top, Col.scale(leg, 0.85f))
        // a shelf between the legs with a crock on it
        iso.box(xa + 0.1f, ya + 0.1f, fl + 2f, xb - xa - 0.2f, yb - ya - 0.2f, 0.6f, Pal.WOOD_M, Pal.WOOD_D, Pal.WOOD_X)
        iso.box(xa + 0.5f, ya + 0.4f, fl + 2.6f, 0.5f, 0.5f, 2.2f, Col.hex(0xC8B89A), Col.hex(0xA89878), Col.hex(0x887858))
        woodTop(xa, ya, top, xb - xa, yb - ya, 1.2f)
    }

    /** A thick wooden top with boards along x. */
    private fun woodTop(x: Float, y: Float, z: Float, bw: Float, bd: Float, th: Float) {
        iso.panelY(x, y + bd, z, bw, th, Pal.WOOD_M)
        iso.panelX(x + bw, y, z, bd, th, Pal.WOOD_D)
        iso.top(x, y, z + th, bw, bd, Pal.WOOD_L)
        var yy = y + 0.45f
        while (yy < y + bd - 0.1f) { iso.line(x + 0.05f, yy, z + th, x + bw - 0.05f, yy, z + th, Col.mix(Pal.WOOD_L, Pal.WOOD_D, 0.45f)); yy += 0.45f }
        if (detail >= 2) {
            // closer up: streaks of grain along the boards, a knot, the nails at their ends
            var b = 0
            var ya = y
            while (ya < y + bd - 0.1f) {
                val yb = min(ya + 0.45f, y + bd)
                for (g in 0 until 2) {
                    val gy = ya + (0.2f + Noise.rnd(b, g, 91) * 0.6f) * (yb - ya)
                    val gx = x + 0.15f + Noise.rnd(b, g, 92) * bw * 0.4f
                    blendLine(iso.ix(gx, gy), iso.iy(gx, gy, z + th), iso.ix(gx + bw * 0.45f, gy), iso.iy(gx + bw * 0.45f, gy, z + th), Pal.WOOD_D, 0.3f)
                }
                for (nx in floatArrayOf(x + 0.12f, x + bw - 0.12f)) nail(iso.ix(nx, (ya + yb) / 2f), iso.iy(nx, (ya + yb) / 2f, z + th))
                ya = yb; b++
            }
            val kx = x + bw * 0.62f; val ky = y + bd * 0.3f
            c.fillEllipse(iso.sx(kx, ky), iso.sy(kx, ky, z + th), K * 0.5f, K * 0.28f, Col.mix(Pal.WOOD_L, Pal.WOOD_D, 0.6f))
        }
        iso.line(x, y + bd, z + th, x + bw, y + bd, z + th, Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.25f))
    }

    /** A tin cup; steam rises from it in the morning. */
    private fun cup(x: Float, y: Float, z: Float) {
        val cx = iso.ix(x, y); val cy = iso.iy(x, y, z)
        val l = Col.hex(0xC8D4E2); val m = Col.hex(0x8A9AAE); val d = Col.hex(0x5E6C80)
        val q = detail
        if (q == 1) {
            c.fillRect(cx - K, cy - 3 * K, 2 * K + 1, 3 * K, m)
            c.vline(cx - K, cy - 3 * K, cy - 1, l)
            c.vline(cx + K, cy - 3 * K, cy - 1, d)
            c.hline(cx - K, cx + K, cy - 3 * K, l)
            c.set(cx, cy - 3 * K, Col.hex(0x5A3A24)) // coffee
            c.vline(cx + K + 1, cy - 2 * K - 1, cy - K, d); c.set(cx + K + 2, cy - 2 * K, d); c.set(cx + K + 2, cy - K - 1, d)
        } else {
            // closer up, a tin mug: a round body lit from the left with a glint, a rolled rim round the coffee, a
            // darker foot, and a proper loop of a handle
            val left = cx - K; val right = cx + K + q - 1; val top = cy - 3 * K
            val bw = right - left + 1
            for (px in left..right) {
                val u = (px - left + 0.5f) / bw
                val col = when { u < 0.18f -> l; u < 0.3f -> Col.mix(l, m, 0.5f); u > 0.84f -> d; u > 0.7f -> Col.mix(m, d, 0.5f); else -> m }
                c.vline(px, top, cy - 1, col)
            }
            c.vline(left + (bw * 0.22f).toInt(), top + q, cy - q - 1, Col.hex(0xEEF4FA))
            c.hline(left, right, cy - 1, Col.mix(m, d, 0.6f))
            val mid = (left + right + 1) / 2f
            c.fillEllipse(mid, top + 0.5f, bw / 2f, q * 0.9f, l)
            c.fillEllipse(mid, top + 0.7f, bw / 2f - q * 0.75f, q * 0.5f, Col.hex(0x5A3A24))
            c.set(mid.toInt() - 1, top, Col.hex(0x8A6448))
            // the handle: a ring whose hole shows what is behind
            val hx0 = right + 1; val hw = 2 * q
            val hy0 = cy - 5 * q; val hh = 4 * q
            val hcx = hx0 - 0.5f; val hcy = hy0 + hh / 2f
            for (py in hy0 until hy0 + hh) for (px in hx0 until hx0 + hw) {
                val nx = (px + 0.5f - hcx) / (hw + 0.5f); val ny = (py + 0.5f - hcy) / (hh / 2f)
                if (nx * nx + ny * ny > 1f) continue
                if ((nx / 0.5f) * (nx / 0.5f) + (ny / 0.52f) * (ny / 0.52f) < 1f) continue
                c.set(px, py, if (ny < -0.3f) m else d)
            }
        }
        if (s.hour in 6f..10f) s.fx {
            for (j in 0..2) {
                val a = ((t * 0.6 + j / 3.0) % 1.0).toFloat()
                val bx = cx + (sin(t * 2 + j) * 1.5 * q).toInt(); val by = (cy - 3 * K - 2 * q - a * 10 * K).toInt()
                for (yy in by until by + q) for (xx in bx until bx + q) c.blend(xx, yy, Col.hex(0xF4F4F4), 0.5f * (1 - a))
            }
        }
    }

    /**
     * The lantern hanging from the roof's front edge on a short chain: a tin cap and base, glass that glows when it
     * burns ([flame] over 0.15, the flame growing with it, a soft halo), a swing so slight it only shows at the chain;
     * [low], turned down for someone asleep: a small flame behind dim glass.
     */
    private fun lantern(x: Float, y: Float, flame: Float, low: Boolean = false) {
        val lit = flame > 0.15f
        val topZ = roofZ(x) - 0.3f
        // tapped ([pokes]) it swings out and back, dying down; the second time wider, its flame guttering
        val pk = poked("lantern"); val a = pk?.age(t) ?: 0f
        val d = if (pk?.step == 1) 3.6f else 3f
        val fade = (1f - a / d).coerceIn(0f, 1f)
        val kick = if (pk == null) 0f else (if (pk.step == 1) 3.2f else 2f) * K * fade * fade * sin(a * 2f * PI.toFloat() * 0.9f) * PokeArt.ease(a, 0f, 0.1f)
        lampFlame = if (pk?.step == 1 && a in 0.2f..1.8f) 0.45f + 0.4f * abs(sin(a * 23f)) else 1f
        lampSwing = sin(t * 1.3).toFloat() * 0.35f + kick
        val x0s = iso.sx(x, y)
        val cx = x0s + lampSwing; val hook = iso.sy(x, y, topZ)
        val bodyTop = iso.sy(x, y, fl + 17.5f); val bodyBottom = iso.sy(x, y, fl + 11.5f)
        val metal = Col.hex(0x3A3A44); val metalL = Col.hex(0x6A6A78)
        val q = detail
        // the chain (closer up, its links); swinging, slanting from the hook
        if (abs(kick) < 1f) chainDown(cx.toInt(), hook.toInt(), (bodyTop - 2 * K).toInt(), metal, metalL)
        else for (o in 0 until q) c.line(x0s.toInt() + o, hook.toInt(), cx.toInt() + o, (bodyTop - 2 * K).toInt(), if (q > 1 && o == 0) metalL else metal)
        // the ring and the cap
        c.fillCircle(cx, bodyTop - 2.2f * K, 1.2f * K, metal); c.fillCircle(cx, bodyTop - 2.2f * K, 0.55f * K, Col.hex(0x1A1A20))
        c.fillRect((cx - 2f * K).toInt(), (bodyTop - 1f * K).toInt(), (4f * K).toInt() + q, K + q, metal)
        c.hline((cx - 1.5f * K).toInt(), (cx + 1.5f * K).toInt(), (bodyTop - 1.4f * K).toInt(), metalL)
        if (q >= 2) {
            // closer up: the ring lit on its left, the cap's vents and its rim
            c.set((cx - 1.0f * K).toInt(), (bodyTop - 2.4f * K).toInt(), metalL)
            var vx = (cx - 1.3f * K).toInt()
            while (vx < cx + 1.3f * K) { c.set(vx, (bodyTop - 0.6f * K).toInt(), Col.hex(0x1A1A20)); vx += q + 1 }
            c.hline((cx - 2f * K).toInt(), (cx + 2f * K).toInt() + q - 1, (bodyTop - 1f * K).toInt(), metalL)
        }
        // the glass
        val gx0 = (cx - 1.6f * K).toInt(); val gx1 = (cx + 1.6f * K).toInt()
        val gy0 = bodyTop.toInt() + q; val gy1 = bodyBottom.toInt() - K
        if (lit) glow {
            c.fillRect(gx0, gy0, gx1 - gx0 + 1, gy1 - gy0 + 1, if (low) Col.mix(Pal.FLAME[2], Pal.GLASS, 0.55f) else Pal.FLAME[1])
            val fy = (gy0 + gy1) / 2f + K * 0.5f
            val grow = (fx("lantern")?.coerceIn(0.5f, 1f) ?: if (low) 0.5f else 1f) * lampFlame // lit by a dialog, the flame grows
            c.fillEllipse(cx, fy + (1f - grow) * K, 0.9f * K * grow, (1.6f * K + sin(t * 9).toFloat() * 0.3f * q) * grow, Pal.FLAME[0])
            if (q >= 2) {
                // closer up: the flame's white heart on its wick, the burner under it
                c.fillEllipse(cx, fy + 0.35f * K, 0.4f * K, 0.8f * K, Col.hex(0xFFFDF0))
                c.vline(cx.toInt(), (fy + 1.1f * K).toInt(), (fy + 1.6f * K).toInt(), Col.hex(0x5A3A24))
                c.fillRect((cx - 0.8f * K).toInt(), gy1 - q + 1, (1.6f * K).toInt(), q, Col.hex(0x8A6A3A))
            }
            c.vline(gx0, gy0, gy1, Pal.FLAME[2])
        } else {
            c.fillRect(gx0, gy0, gx1 - gx0 + 1, gy1 - gy0 + 1, Pal.GLASS)
            c.vline(gx0 + q, gy0 + q, gy1 - q, Pal.GLASS_HI)
            if (q >= 2) {
                // closer up: a second, fainter glint, the cold wick and burner inside
                c.vline(gx0 + 2 * q + 1, gy0 + 2 * q, gy0 + 4 * q, Col.mix(Pal.GLASS, Pal.GLASS_HI, 0.5f))
                c.vline(cx.toInt() + q, (gy1 - 1.4f * K).toInt(), gy1 - q, Col.hex(0x1E2630))
                c.fillRect((cx - 0.8f * K).toInt(), gy1 - q + 1, (1.6f * K).toInt(), q, Col.hex(0x4A4A52))
            }
        }
        // the frame bars and the base (closer up, each bar lit on its left)
        for (o in 1..q) {
            c.vline(gx0 - o, gy0, gy1, if (q >= 2 && o == q) metalL else metal)
            c.vline(gx1 + o, gy0, gy1, metal)
        }
        for (o in 0 until q) c.vline(cx.toInt() - (q - 1) / 2 + o, gy0, gy1, if (q >= 2 && o == 0) metalL else metal)
        c.fillRect((cx - 2f * K).toInt(), gy1 + q, (4f * K).toInt() + q, K + q, metal)
        c.hline((cx - 2f * K).toInt(), (cx + 2f * K).toInt(), gy1 + q, metalL)
        if (q >= 2) c.hline((cx - 2f * K).toInt() + q, (cx + 2f * K).toInt(), gy1 + q + K, Col.hex(0x26262E))
        if (lit) s.fx {
            c.penEmissive = true
            c.ditherCircle(cx, (gy0 + gy1) / 2f, 8f * K, Pal.FLAME[1], 0.15f * flame)
            c.penEmissive = false
        }
    }

    /** The camp bed's frame: rails on crossed legs. */
    private fun cot() {
        val xa = 1.2f; val xb = 4.2f; val ya = 7.7f; val yb = 9.4f
        val rail = fl + 3f
        for ((lx, ly) in listOf(xb - 0.12f to yb - 0.12f, xb - 0.12f to ya + 0.12f, xa + 0.4f to yb - 0.12f)) iso.post(lx, ly, fl, rail, Pal.WOOD_D)
        iso.box(xa, ya, rail, xb - xa, yb - ya, 1.0f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
    }

    /** The sleeping bag on the cot: quilted green, turned back at the top to show the lining. */
    private fun sleepingBag() {
        val xa = 1.3f; val xb = 3.4f; val ya = 7.8f; val yb = 9.3f
        val z = fl + 4f
        val l = Col.hex(0x5E9A7A); val m = Col.hex(0x437A5E); val d = Col.hex(0x2E5A44)
        iso.box(xa, ya, z, xb - xa, yb - ya, 1.6f, l, m, d)
        var qx = xa + 0.45f
        while (qx < xb) { iso.line(qx, ya + 0.1f, z + 1.6f, qx, yb, z + 1.6f, m); iso.line(qx, yb, z + 1.6f, qx, yb, z + 0.2f, d); qx += 0.45f }
        // turned back at the head: the lining
        iso.top(xa + 0.75f, ya + 0.05f, z + 1.65f, 0.4f, yb - ya - 0.1f, Col.hex(0xE9D9B0))
        iso.line(xa + 1.15f, ya + 0.05f, z + 1.65f, xa + 1.15f, yb - 0.05f, z + 1.65f, Col.hex(0xB8A77E))
        if (detail >= 2) {
            // closer up: the zip along its side, its pull by the head; the lining's check
            var zx = xa + 0.8f
            var i = 0
            while (zx < xb - 0.05f) { iso.line(zx, yb, z + 0.85f, zx + 0.03f, yb, z + 0.85f, if (i % 2 == 0) Col.hex(0xB8C0C8) else Col.hex(0x2A3A30)); zx += 0.06f; i++ }
            iso.px(xa + 0.72f, yb + 0.02f, z + 0.9f, Col.hex(0xD8DEE4))
            var ly = ya + 0.2f
            while (ly < yb - 0.1f) { iso.line(xa + 0.78f, ly, z + 1.65f, xa + 1.12f, ly, z + 1.65f, Col.hex(0xD8C69A)); ly += 0.28f }
        }
    }

    private fun pillow() {
        val xa = 1.3f; val ya = 7.9f
        val l = Col.hex(0xF6F0DE); val m = Col.hex(0xE0D6BC); val d = Col.hex(0xBFB395)
        iso.box(xa, ya, fl + 4.6f, 0.75f, 1.3f, 1.8f, l, m, d)
        iso.line(xa + 0.1f, ya + 0.65f, fl + 6.4f, xa + 0.65f, ya + 0.65f, fl + 6.4f, m)
        iso.px(xa + 0.75f, ya + 1.3f, fl + 6.2f, m)
    }

    /** The red plaid blanket, folded at the foot of the bed and hanging over its end. */
    private fun blanket() {
        val xa = 3.25f; val xb = 4.3f; val ya = 7.72f; val yb = 9.38f
        val z = fl + 4f
        val red = Col.hex(0xC0402F); val redD = Col.hex(0x8A2A22); val redL = Col.hex(0xDA5A40)
        iso.panelX(xb, ya + 0.05f, fl + 1.2f, yb - ya - 0.1f, z + 2.2f - fl - 1.2f, redD)
        iso.panelY(xa, yb, z, xb - xa, 2.2f, red)
        iso.top(xa, ya, z + 2.2f, xb - xa, yb - ya, redL)
        // the plaid
        val check = Col.hex(0xF0B050)
        var yy = ya + 0.35f
        while (yy < yb) { iso.line(xa, yy, z + 2.2f, xb, yy, z + 2.2f, Col.scale(check, 0.9f)); iso.line(xb, yy, z + 2.2f, xb, yy, fl + 1.2f, Col.scale(check, 0.7f)); yy += 0.55f }
        var xx = xa + 0.3f
        while (xx < xb) { iso.line(xx, ya, z + 2.2f, xx, yb, z + 2.2f, Col.scale(check, 0.8f)); iso.line(xx, yb, z + 2.2f, xx, yb, z, Col.scale(check, 0.75f)); xx += 0.5f }
        // the fringe along the hanging edge (closer up, its threads)
        var fy = ya + 0.1f
        if (detail == 1) while (fy < yb) { iso.px(xb + 0.02f, fy, fl + 0.9f, redL); fy += 0.28f }
        else {
            var i = 0
            while (fy < yb) { iso.line(xb + 0.02f, fy, fl + 1.2f, xb + 0.02f, fy + 0.03f, fl - 0.05f, if (i % 3 == 2) red else redL); fy += 0.28f / 3f; i++ }
        }
    }

    /** An open book on the table: two pages of lines, a ribbon. */
    private fun book() {
        val xa = 1.45f; val xb = 2.4f; val ya = 6.35f; val yb = 7.25f
        val z = fl + 8.25f
        val cover = Col.hex(0x7A2E22)
        iso.top(xa - 0.05f, ya - 0.05f, z, xb - xa + 0.1f, yb - ya + 0.1f, cover)
        val pageL = Col.hex(0xF6EEDA); val pageR = Col.hex(0xE8DDC2)
        val ym = (ya + yb) / 2f
        iso.top(xa, ya, z + 0.5f, xb - xa, ym - ya, pageR)
        iso.top(xa, ym, z + 0.5f, xb - xa, yb - ym, pageL)
        iso.line(xa, ym, z + 0.5f, xb, ym, z + 0.5f, Col.hex(0xA8987A))
        val ink = Col.hex(0x8A7A6A)
        if (detail == 1) for (j in 1..3) {
            val lx = xa + j * (xb - xa) / 4.2f
            iso.line(lx, ya + 0.08f, z + 0.5f, lx, ym - 0.08f, z + 0.5f, ink)
            iso.line(lx, ym + 0.08f, z + 0.5f, lx, yb - 0.1f, z + 0.5f, ink)
        } else {
            // closer up: the lines of text, word by word, a heading on the left page; the pages' edges below
            val n = 3 + 2 * detail
            for (j in 1..n) {
                val lx = xa + j * (xb - xa) / (n + 1.2f)
                for ((s0, s1, page) in listOf(Triple(ya + 0.08f, ym - 0.08f, 0), Triple(ym + 0.08f, yb - 0.1f, 1))) {
                    var s = s0
                    var w = 0
                    val end = if (j == n) s0 + (s1 - s0) * 0.5f else s1
                    while (s < end) {
                        val len = 0.05f + Noise.rnd(j, w + page * 31, 17) * 0.12f
                        iso.line(lx, s, z + 0.5f, lx, min(s + len, end), z + 0.5f, if (j == 1 && page == 1) Col.hex(0x5A4A3A) else ink)
                        s += len + 0.045f; w++
                    }
                }
            }
            iso.panelY(xa, yb, z, xb - xa, 0.5f, Col.hex(0xD8CCB0))
            iso.line(xa, yb, z + 0.25f, xb, yb, z + 0.25f, Col.hex(0xB8AA8A))
        }
        iso.line(xb - 0.1f, ym, z + 0.5f, xb + 0.15f, ym + 0.05f, z - 0.8f, Pal.FLAG_RED)
    }

    /** A pair of leather boots at the edge of the floor. */
    private fun boots() {
        val l = Col.hex(0x8A5A38); val m = Col.hex(0x6A4028); val d = Col.hex(0x3E2618)
        for ((bx, by) in listOf(6.45f to 8.72f, 6.95f to 8.55f)) {
            iso.box(bx, by, fl, 0.62f, 0.3f, 1.3f, l, m, d)
            iso.box(bx, by, fl + 1.3f, 0.3f, 0.3f, 3.2f, l, m, d)
            iso.top(bx + 0.03f, by + 0.03f, fl + 4.52f, 0.24f, 0.24f, d)
            iso.line(bx + 0.3f, by + 0.3f, fl + 1.3f, bx + 0.62f, by + 0.3f, fl + 1.3f, Col.hex(0x2A1D1A))
            if (detail >= 2) {
                // closer up: the sole and heel, the toe cap's shine, the laces crossing up the shaft
                iso.line(bx, by + 0.3f, fl + 0.3f, bx + 0.62f, by + 0.3f, fl + 0.3f, Col.hex(0x2A1D1A))
                iso.line(bx + 0.62f, by, fl + 0.3f, bx + 0.62f, by + 0.3f, fl + 0.3f, Col.hex(0x2A1D1A))
                iso.line(bx + 0.42f, by + 0.3f, fl + 0.9f, bx + 0.56f, by + 0.3f, fl + 0.9f, Col.mix(l, Col.hex(0xFFFFFF), 0.3f))
                var lz = fl + 1.8f
                while (lz < fl + 4.3f) {
                    iso.line(bx + 0.06f, by + 0.3f, lz, bx + 0.24f, by + 0.3f, lz + 0.5f, Col.hex(0xD8C49A))
                    iso.line(bx + 0.06f, by + 0.3f, lz + 0.5f, bx + 0.24f, by + 0.3f, lz, Col.hex(0xB8A07A))
                    lz += 0.8f
                }
            }
        }
    }

    /** A coil of rope lying flat in the grass: two loops under a spiral, its loose end trailing off. */
    private fun coil(x: Float, y: Float) {
        val cx = iso.sx(x, y); val cy = iso.sy(x, y, 0f)
        val l = Col.hex(0xEAD8A6); val m = Col.hex(0xC8AC72); val d = Col.hex(0x6E5A3A)
        val rx = 3.2f * K * 2f; val ry = 3.2f * K
        val q = detail
        c.fillEllipse(cx, cy - K * 0.8f, rx + q, ry + q, d)
        c.fillEllipse(cx, cy - K * 1.8f, rx, ry, m)
        // the spiral on top: rings of rope with dark gaps, each [detail] px thick; closer up the strands twist
        var r = 1f
        var i = 0
        while (r > 0.25f) {
            for (o in 0 until q) ringLine(cx, cy - K * 2.2f, rx * r - q - o, ry * r - q - o, d)
            for (o in 0 until q) ringLine(cx, cy - K * 2.2f - q, rx * r - 2 * q - o, ry * r - 1.5f * q - o, if (i % 2 == 0) l else m, twist = q >= 2 && o == q / 2)
            r -= 0.24f; i++
        }
        c.fillEllipse(cx, cy - K * 2.2f, rx * 0.2f, ry * 0.2f, d)
        // twists on the outer loop
        val tw = Col.hex(0x9A8052)
        for (j in 0 until 16 * q) {
            val a = j / (16f * q) * 6.283f
            if (sin(a) < -0.2f) continue
            val px = (cx + cos(a) * (rx - q)).toInt(); val py = (cy - K * 1.8f + sin(a) * (ry - q)).toInt()
            c.set(px, py, tw)
            if (q >= 2 && j % 2 == 0) c.set(px + 1, py - 1, tw)
        }
        // the loose end, and closer up the frayed tip
        for (j in 0 until 8 * q) {
            val f = j / q.toFloat()
            dot(iso.ix(x + 0.5f + f * 0.13f, y + 0.8f + f * 0.05f), iso.iy(x + 0.5f + f * 0.13f, y + 0.8f + f * 0.05f, 0f) - K / 2, if ((j / q) % 2 == 0) l else m)
            if (q >= 2 && j % q == 0) c.set(iso.ix(x + 0.5f + f * 0.13f, y + 0.8f + f * 0.05f) + 1, iso.iy(x + 0.5f + f * 0.13f, y + 0.8f + f * 0.05f, 0f) - K / 2, d)
        }
    }

    private companion object {
        /** The camp bed, where the tent's own sleeps at night. */
        const val BED = "bed"

        /** How bright the lantern burns turned low for a sleeper. */
        const val LOW = 0.3f

        /** The sleeping bag's quilted green: someone asleep in it is zipped in up to the chin. */
        val BAG = intArrayOf(Col.hex(0x5E9A7A), Col.hex(0x437A5E), Col.hex(0x2E5A44))
    }

    /** A one-pixel ellipse outline; [twist]: closer up, every few pixels a strand's shadow. */
    private fun ringLine(cx: Float, cy: Float, rx: Float, ry: Float, col: Int, twist: Boolean = false) {
        val n = ((rx + ry) * 3).toInt().coerceAtLeast(12)
        val dark = Col.scale(col, 0.78f)
        for (i in 0 until n) {
            val a = i / n.toFloat() * 6.283f
            c.set((cx + cos(a) * rx).toInt(), (cy + sin(a) * ry).toInt(), if (twist && (i / 3) % 2 == 0) dark else col)
        }
    }
}
