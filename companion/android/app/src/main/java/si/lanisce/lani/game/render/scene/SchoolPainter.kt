package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.Season
import si.lanisce.lani.game.scene.Poke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * V šoli: the village classroom as a diorama, a room cut open on its own plot of grass. Two back walls of plaster
 * over a wooden wainscot, a frieze of painted tulips under the top: on the right one the blackboard with today's
 * words in chalk ("Dober dan!", "Kako si?", "Dobro.") and a chalk sun, the chalk and the sponge on its ledge, the
 * pointer leaning at its end, and a window with the day outside; on the left one a second window over a low
 * bookcase, a lamp on a bracket, the map of Slovenia under the clock, a scarf on a peg and the door. The teacher
 * stands at the board; her desk is by the right window with the globe (turning), a stack of books and an oil lamp,
 * her chair pulled out beside it. Two pupils' desks with a notebook, a pencil and a book on them, their chairs, a
 * satchel on the floor against the first. The lamps are lit from dusk; by day the sun lays the left window's panes
 * on the floor. Outside the cut-away front a bit of schoolyard: a linden with a bench, a hopscotch chalked on the
 * ground (a snowman on it in winter), a bed of flowers (a sled in winter), a bicycle and the flag.
 *
 * Closer up ([detail] 2 and 3) everything keeps its place and weight (sizes in pixels grow with the detail; sticks,
 * straps and rods stay a pixel of the scene canvas thick, see [rod]) and the extra pixels go into what the scene's
 * canvas can't hold: the lesson in a finer chalk hand ([ChalkHand]) with a sun of long and short rays, numbers in the
 * hopscotch, the map's smooth coastline with its rivers, mountains, towns and title, twelve marks on the clock, the
 * globe's meridians, gilt bands and titles on the spines, pages between the book covers, handwriting in the exercise
 * book, grain on the desks, moulded glazing bars and old glass, round geranium heads, the scarf's knitting, wicker,
 * sharpened pencils, a sunflower's petals and seeds, twice the spokes in the wheels, and the rubble in the cut walls.
 *
 * Tapped ([pokes]), the globe whirls round and slows again (faster the second time); the sponge hops on the ledge in
 * a puff of chalk dust, twice the second time.
 *
 * The school grows with its level ([si.lanisce.lani.game.scene.SceneFixtures]): the map and the globe come with the
 * second, the clock with the third.
 *
 * At night ([beds]: "alcove") Učiteljica Mojca sleeps in the school, as the village teacher did in the old schools, where
 * the teacher's room was in the school house: in a wooden bed along the left wall under the map, its head by the bookcase,
 * a curtain on a rod along it drawn back and gathered at its head, under a rose wool blanket with a cream check. The bed
 * and its curtain are there only while she is in it, and the lamps are out.
 */
internal class SchoolPainter : DioramaPainter() {
    override val art = "school"
    override val pokes = listOf(Poke("globe", listOf(2.4, 3.4), rest = 2.0), Poke("sponge", listOf(1.4, 2.0), rest = 2.0))

    /** At night Mojca sleeps in her curtained bed along the left wall. */
    override val beds = listOf(ALCOVE)
    override val plotW = 13f
    override val plotD = 11f
    override val rise = 26f

    // the room: a plank floor over X0..X1 × Y0..Y1 at FL; the back walls' inner faces are x = X0 (left) and y = Y0
    // (right), TH thick and HW high, the wainscot up to WAIN
    private val x0 = 0.6f; private val x1 = 10.4f
    private val y0 = 0.6f; private val y1 = 8.4f
    private val th = 0.35f; private val hw = 26f; private val fl = 2f
    private val wain = fl + 7f

    // on the right wall: the blackboard's frame and a window
    private val ba = 1.3f; private val bb = 8.1f; private val bz0 = 9.2f; private val bz1 = 24.4f
    private val wbA = 8.7f; private val wbB = 10.0f
    // on the left wall: a window, the map under the clock, the coat peg, the door
    private val waA = 1.3f; private val waB = 2.9f
    private val ma = 3.5f; private val mb = 5.9f; private val mz0 = 10.6f; private val mz1 = 19.6f
    private val clockY = 4.7f; private val clockZ = 22.9f; private val clockR = 2.5f
    private val da = 6.75f; private val db = 8.1f; private val dh = 18.5f
    private val winZ0 = 11.2f; private val winZ1 = 21.8f

    // the teacher's desk, the pupils' desks (they face +x, their chairs behind them)
    private val tx0 = 7.4f; private val tx1 = 9.8f; private val ty0 = 2.1f; private val ty1 = 3.2f
    private val d1x = 3.9f; private val d1y0 = 5.0f; private val d1y1 = 6.9f; private val p1y = 6.45f
    private val d2x = 8.6f; private val d2y0 = 5.6f; private val d2y1 = 7.5f; private val p2y = 7.05f
    private val deskD = 1.2f

    // the people
    private val teacherX = 4.2f; private val teacherY = 3.2f
    private val doorX = 1.5f; private val doorY = 7.35f

    // the yard
    private val treeX = 0.45f; private val treeY = 10.5f
    private val poleX = 12.4f; private val poleY = 0.7f
    private val bikeX = 11.7f; private val bikeY0 = 3.4f; private val bikeY1 = 6.2f
    private val hopX0 = 4.3f; private val hopX1 = 6.9f; private val hopY = 9.75f
    private val bedX = 11.7f; private val bedY = 9.7f

    private var floorId = 0

    private val plasterL = Col.hex(0xF2ECDC); private val plasterM = Col.hex(0xE4DBC4); private val plasterD = Col.hex(0xCDC2A6)
    private val slate = Col.hex(0x2E4A3A); private val slateL = Col.hex(0x3A5A48); private val slateD = Col.hex(0x253D30)
    private val chalkW = Col.hex(0xF4F2E8); private val chalkD = Col.hex(0xC8CCC0); private val chalkY = Col.hex(0xF2D46A)
    private val oakL = Col.hex(0xD6A870); private val oakM = Col.hex(0xB0804E); private val oakD = Col.hex(0x8A5E36)
    private val frameW = Col.hex(0xF2EEE2); private val frameD = Col.hex(0xCFC8B6)

    override fun ambient(): FloatArray {
        val lift = env.dark * 0.07f
        return floatArrayOf(env.ambR + lift, env.ambG + lift * 0.9f, env.ambB + lift * 0.6f)
    }

    // ------------------------------------------------------------------ drawing at the detail

    /** A line [n] px thick ([detail] by default: a pixel of the scene canvas), widening down a flat line and right along a steep one. */
    private fun thick(xa: Int, ya: Int, xb: Int, yb: Int, col: Int, n: Int = detail) {
        val steep = abs(yb - ya) > abs(xb - xa)
        for (o in 0 until n) if (steep) c.line(xa + o, ya, xb + o, yb, col) else c.line(xa, ya + o, xb, yb + o, col)
    }

    /** A stick, a strap or a rod between two world points: [thick], so it keeps its weight closer up (at detail 1 a one-pixel line). */
    private fun rod(xa: Float, ya: Float, za: Float, xb: Float, yb: Float, zb: Float, col: Int, n: Int = detail) =
        thick(iso.ix(xa, ya), iso.iy(xa, ya, za), iso.ix(xb, yb), iso.iy(xb, yb, zb), col, n)

    /** A pixel of the scene canvas with its top-left corner at the picture's ([x], [y]): [detail] × [detail] px. */
    private fun pix(x: Int, y: Int, col: Int) = c.fillRect(x, y, detail, detail, col)

    /** The pixels of the line from ([xa], [ya]) to ([xb], [yb]), one picture pixel thin, handed to [plot]. */
    private inline fun seg(xa: Int, ya: Int, xb: Int, yb: Int, plot: (Int, Int) -> Unit) {
        var x = xa; var y = ya
        val dx = abs(xb - xa); val dy = -abs(yb - ya)
        val sx = if (xa < xb) 1 else -1; val sy = if (ya < yb) 1 else -1
        var err = dx + dy
        while (true) {
            plot(x, y)
            if (x == xb && y == yb) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    override fun paint() {
        fit()
        vignette()
        // the lamps are lit from dusk, and put out while Mojca sleeps
        val lit = env.windows > 0.35f && !asleepHere
        plot { x, y, px, py -> ground(x, y, px, py) }
        // tufts first: they lie flat, and whatever stands in front of them covers them
        tufts(80, 17) { x, y ->
            (x < x1 + 0.3f && y < y1 + 0.3f) || hypot(x - treeX, y - treeY) < 0.7f || (x in hopX0 - 0.3f..hopX1 + 0.3f && abs(y - hopY) < 0.9f) ||
                (abs(x - bikeX) < 0.5f && y in bikeY0 - 0.3f..bikeY1 + 0.3f) || (x in 1.2f..3.4f && y in 9.4f..10.5f) || hypot(x - bedX, y - bedY) < 1.1f
        }
        if (detail > 1 && !winter) hopNumbers()

        floor()
        sunPatch()
        wallLeft()
        wallRight()
        thing("window") { window(true, waA, waB) }
        prop { window(false, wbA, wbB) }
        thing("blackboard") { blackboard() }
        thing("chalk", slop = 2) { chalk() }
        thing("sponge", slop = 2) { sponge() }
        // the school's upgrades ([SceneFixtures]): the map and the globe at its second level, the clock at its third
        if (there("map")) thing("map") { map() }
        if (there("clock")) thing("clock", slop = 1) { clock() }
        thing("door") { door() }
        prop { peg() }
        prop { wallTops() }
        prop { sconce(lit) }
        prop { bookcase() }
        prop { pointer() }
        // Mojca asleep in her bed along the left wall, its curtain drawn back to its foot
        peopleAt(ALCOVE).firstOrNull()?.let { p ->
            val top = teacherBed()
            sleeperOn(p, MB_A, MB_C, MB_B, MB_D, top, alongY = true, ROSE_WOOL)
            prop { footboard(); alcoveCurtain() }
        }

        // someone at the door
        for (p in peopleAt("door")) { footShadow(doorX, doorY); personAt(p, doorX, doorY, fl) }

        // the teacher's corner by the window: her chair, the desk, the books, the lamp and the globe on it
        thing("chair") { chair(6.75f, 2.55f, facingX = false) }
        prop { basket(7.25f, 3.75f) }
        prop { teacherDesk() }
        thing("book", slop = 1) { books(7.75f, 2.35f) }
        prop { oilLamp(8.55f, 2.45f, lit) }
        if (there("globe")) thing("globe", slop = 1) { globe(9.35f, 2.65f) }
        for (p in peopleAt("teacher")) { footShadow(teacherX, teacherY); personAt(p, teacherX, teacherY, fl, flip = true) }

        // the pupils' desks: the first one with the notebook and the pencil, a satchel against it
        prop { chair(d1x - 0.75f, p1y, facingX = true) }
        for (p in peopleAt("desk1")) personAt(p, d1x - 0.3f, p1y, fl, seated = true)
        thing("desk") { pupilDesk(d1x, d1y0, d1y1, heart = true) }
        thing("notebook", slop = 1) { notebook(d1x + 0.18f, 5.2f) }
        thing("pencil", slop = 2) { pencil(d1x + 0.3f, 5.95f) }
        shade(iso.sx(d1x + deskD + 0.2f, 5.85f), iso.sy(d1x + deskD + 0.2f, 5.85f, fl), 4f * K, 1.3f * K, 0.75f, floorId)
        thing("bag") { bag(d1x + deskD, 5.35f) }
        prop { chair(d2x - 0.75f, p2y, facingX = true) }
        for (p in peopleAt("desk2")) personAt(p, d2x - 0.3f, p2y, fl, seated = true)
        prop { pupilDesk(d2x, d2y0, d2y1, heart = false) }
        prop { readingBook(d2x + 0.2f, d2y0 + 0.15f) }

        // the schoolyard
        yard()

        fireflies(iso.ix(0f, 11f), iso.iy(6f, 11f, 16f), iso.ix(13f, 6f), iso.iy(13f, 11f, 0f), 10, 9)
        if (lit) {
            s.light(iso.sx(8.55f, 2.45f), iso.sy(8.55f, 2.45f, fl + 11f), 44f, 0.95f * env.windows)
            s.light(iso.sx(x0 + 0.4f, sconceY), iso.sy(x0 + 0.4f, sconceY, sconceZ), 38f, 0.8f * env.windows)
        }
    }

    // ------------------------------------------------------------------ ground and floor

    private fun ground(x: Float, y: Float, px: Int, py: Int): Int {
        var g = grassAt(x, y, px, py)
        // a hopscotch chalked on a trodden patch (under the snow in winter)
        if (!winter) {
            val hop = hopscotch(x, y)
            if (hop >= 0) {
                val edge = min(min(x - hopX0 + 0.3f, hopX1 + 0.3f - x), 0.95f - abs(y - hopY)) + (Noise.v2(x * 3f, y * 3f, 19) - 0.5f) * 0.3f
                if (edge > 0.12f || (edge > 0f && Dither.at(px, py) < edge / 0.12f)) g = if (Noise.rnd(px, py, 37) < 0.12f) Pal.DIRT_L else Pal.DIRT_M
            }
            if (hop == 1) g = if (Noise.rnd(px, py, 61) < 0.12f) Col.mix(chalkW, Pal.DIRT_L, 0.5f) else Col.mix(chalkW, Pal.DIRT_L, 0.12f)
            if (hop == 2) g = Pal.STONE_D
        }
        // the building's shade along its foot
        val dy = y - y1; val dx = x - x1
        if ((dy in 0f..0.45f && x < x1 + 0.1f) || (dx in 0f..0.45f && y < y1 + 0.1f)) {
            val d = if (dy in 0f..0.45f && x < x1 + 0.1f) dy else dx
            if (Dither.at(px, py) < 1f - d / 0.45f) g = Col.scale(g, 0.8f)
        }
        return g
    }

    /** The hopscotch at world (x, y): -1 off it, 0 its trodden ground, 1 a chalk line, 2 the pebble. */
    private fun hopscotch(x: Float, y: Float): Int {
        if (x < hopX0 - 0.4f || x > hopX1 + 0.4f || abs(y - hopY) > 1.05f) return -1
        val size = (hopX1 - hopX0) / 4f
        val u = (x - hopX0) / size
        if (u < -0.02f || u > 4.02f) return 0
        val row = min(3, u.toInt())
        val double = row == 1 || row == 3
        val half = if (double) size else size / 2f
        val v = y - hopY
        if (abs(v) > half + 0.05f) return 0
        if (hypot(x - hopX0 - size * 1.5f, v - size * 0.5f) < 0.09f) return 2
        // a line half a pixel either side of the edge in both screen directions: two pixels per row, like the iso lines
        val onLine = abs(u - floor(u + 0.5f)) * size < 0.064f || abs(abs(v) - half) < 0.064f || (double && abs(v) < 0.064f)
        return if (onLine) 1 else 0
    }

    /**
     * Closer up, the hopscotch's boxes numbered 1 to 6 in chalk, in thin strokes (as [ChalkHand] writes): 1, then 2 and 3
     * side by side, 4, then 5 and 6. Drawn on the plot, under the pebble.
     */
    private fun hopNumbers() {
        val size = (hopX1 - hopX0) / 4f
        val u = 0.8f + (detail - 2) * 0.35f // a stroke unit, in picture px
        val chalk = Col.mix(chalkW, Pal.DIRT_L, 0.15f)
        c.penId = groundId
        var n = 1
        for (row in 0..3) for (side in if (row % 2 == 1) intArrayOf(1, -1) else intArrayOf(0)) {
            // (the 2 moved up its box, clear of the pebble lying on it)
            val wx = hopX0 + size * (row + if (n == 2) 0.2f else 0.45f); val wy = hopY + side * size / 2f
            val ox = iso.sx(wx, wy) - 1f * u; val oy = iso.sy(wx, wy, 0f) - 2f * u
            for (st in DIGITS[n - 1]) {
                var lx = 0; var ly = 0
                for (i in 0 until st.size / 2) {
                    val x = (ox + st[2 * i] * u).roundToInt(); val y = (oy + st[2 * i + 1] * u).roundToInt()
                    if (i > 0) seg(lx, ly, x, y) { a, b -> if (c.get(a, b) != Pal.STONE_D) c.set(a, b, chalk) }
                    lx = x; ly = y
                }
            }
            n++
        }
        c.penId = 0
    }

    /** The plank floor on a stone footing: rough blocks under an oak sill along the two open fronts. */
    private fun floor() {
        floorId = s.newObject(Pal.OUTLINE)
        quadFill(x0 - th, y1, 0f, x1, y1, 0f, x1, y1, fl, x0 - th, y1, fl) { px, py -> footing(px, py, wallYx(px, y1), wallYz(px, py, y1), 1f) }
        quadFill(x1, y0 - th, 0f, x1, y1, 0f, x1, y1, fl, x1, y0 - th, fl) { px, py -> footing(px, py, wallXy(px, x1) + 17f, wallXz(px, py, x1), 0.8f) }
        planks(x0, y0, x1, y1, fl, 0.5f)
        c.penId = 0
    }

    /**
     * The sun through the left window lies on the floor by day: the panes as a warm patch with the bars' shadows
     * across it, sliding along the floor with the hour.
     */
    private fun sunPatch() {
        if (env.sun < 0.12f) return
        val f = ((env.sun - 0.12f) * 2.5f).coerceIn(0f, 1f) * 0.4f
        val warm = Col.hex(0xFFE7A8)
        val drift = (13.5f - s.hour) * 0.1f
        val ids = c.ids; val pix = c.pixels
        val xa = iso.ix(x0, y1); val xb = iso.ix(x1, y0)
        val ya = iso.iy(x0, y0, fl); val yb = iso.iy(x1, y1, fl)
        for (py in max(c.top, ya)..min(c.bottom - 1, yb)) for (px in max(c.left, xa)..min(c.right - 1, xb)) {
            val i = c.index(px, py)
            if (ids[i] != floorId) continue
            val x = flatX(px, py, fl); val y = flatY(px, py, fl)
            // back along the sunbeam to the window's plane: it falls at 45°, sideways by the hour
            val tt = x - x0; val wy = y - tt * drift; val wz = fl + tt * CELL
            val u = (waB - wy) / (waB - waA); val v = (wz - winZ0) / (winZ1 - winZ0)
            if (u !in 0.06f..0.94f || v !in 0.06f..0.94f) continue
            if (abs(u - 0.5f) < 0.05f || abs(v - 0.62f) < 0.04f) continue
            val edge = min(min(u - 0.06f, 0.94f - u), min(v - 0.06f, 0.94f - v)) * 12f
            if (edge < 1f && Dither.at(px, py) > edge) continue
            pix[i] = Col.mix(pix[i], warm, f)
        }
    }

    private fun footing(px: Int, py: Int, along: Float, z: Float, light: Float): Int {
        val col = when {
            z > fl - 0.6f -> if (z > fl - 0.25f) Pal.WOOD_L else Pal.WOOD_D
            else -> {
                val block = floor(along / 0.7f + (if (z < 0.7f) 0.5f else 0f)).toInt()
                val f = along / 0.7f + (if (z < 0.7f) 0.5f else 0f) - block
                when {
                    f < 0.1f || abs(z - 0.7f) < 0.2f -> Pal.STONE_X
                    Noise.rnd(block, if (z < 0.7f) 1 else 2, 7) < 0.5f -> Pal.STONE_M
                    else -> if (Dither.at(px, py) < 0.5f) Pal.STONE_L else Pal.STONE_M
                }
            }
        }
        return Col.scale(col, light)
    }

    private fun footShadow(x: Float, y: Float) = shade(iso.sx(x, y), iso.sy(x, y, fl), 5f * K, 1.6f * K, 0.72f, floorId, floorId)

    // ------------------------------------------------------------------ the walls

    /** The left wall's inner face (x = X0): plaster over the wainscot, a shade in the corner. */
    private fun wallLeft() {
        part(Pal.OUTLINE)
        quadFill(x0, y0, fl, x0, y1, fl, x0, y1, hw, x0, y0, hw) { px, py ->
            wallColor(px, py, wallXy(px, x0) - y0, wallXz(px, py, x0), 0.86f)
        }
        // the cut end of the wall, its stone core between two skins of plaster
        quadFill(x0 - th, y1, 0f, x0, y1, 0f, x0, y1, hw, x0 - th, y1, hw) { px, py ->
            val x = wallYx(px, y1)
            if (x > x0 - 0.06f || x < x0 - th + 0.06f) plasterL else wallCore(px, py)
        }
        c.penId = 0
    }

    /** The right wall's inner face (y = Y0), a touch lighter. */
    private fun wallRight() {
        part(Pal.OUTLINE)
        quadFill(x0, y0, fl, x1, y0, fl, x1, y0, hw, x0, y0, hw) { px, py ->
            wallColor(px, py, wallYx(px, y0) - x0, wallYz(px, py, y0), 1f)
        }
        quadFill(x1, y0 - th, 0f, x1, y0, 0f, x1, y0, hw, x1, y0 - th, hw) { px, py ->
            val y = wallXy(px, x1)
            if (y > y0 - 0.06f || y < y0 - th + 0.06f) Col.scale(plasterM, 0.8f) else Col.scale(wallCore(px, py), 0.8f)
        }
        c.penId = 0
    }

    /** The rubble in a cut wall: stones 2 × 3 px of the scene canvas; closer up their mortar joints and a lit top edge show. */
    private fun wallCore(px: Int, py: Int): Int {
        val bw = 2 * detail; val bh = 3 * detail
        // every other course shifted by half a stone, as they are laid
        val row = py / bh; val sx = px + (if (detail > 1 && row % 2 == 1) detail else 0)
        val n = Noise.rnd(sx / bw, row, 53)
        val col = if (n < 0.3f) Pal.STONE_D else if (n < 0.8f) Pal.STONE_M else Pal.STONE_L
        if (detail == 1) return col
        return when {
            py % bh == 0 || sx % bw == 0 -> Pal.STONE_X
            py % bh == 1 -> Col.mix(col, Pal.STONE_L, 0.5f)
            else -> col
        }
    }

    /** The wall at [u] cells from the corner and [z] px up: skirting, boards, the rail, plaster with a painted frieze. */
    private fun wallColor(px: Int, py: Int, u: Float, z: Float, lum: Float): Int {
        val zf = z - fl
        val wz = wain - fl
        var col = when {
            zf < 0.5f -> Col.hex(0x3E2616)
            zf < 1.1f -> Pal.WOOD_X
            zf < wz - 0.5f -> {
                val b = u / 0.42f; val bi = floor(b).toInt(); val f = b - bi
                val base = if (Noise.rnd(bi, 3) < 0.5f) Pal.WOOD_M else Col.mix(Pal.WOOD_M, Pal.WOOD_L, 0.3f)
                when {
                    f < 0.1f -> Pal.WOOD_X
                    f < 0.22f -> Col.mix(base, Pal.WOOD_L, 0.45f)
                    Noise.v2(u * 8f, zf * 0.3f + bi * 3f, 7) > 0.74f -> Col.scale(base, 0.9f)
                    else -> base
                }
            }
            zf < wz -> Pal.WOOD_D
            zf < wz + 0.9f -> if (zf > wz + 0.55f) Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.25f) else Pal.WOOD_L
            else -> plaster(px, py, u, z)
        }
        // the corner and the floor in a soft shade
        if (u < 0.4f && Dither.at(px, py) < (0.4f - u) * 1.8f) col = Col.scale(col, 0.88f)
        return Col.scale(col, lum)
    }

    private fun plaster(px: Int, py: Int, u: Float, z: Float): Int {
        // a painted frieze under the top: a band with little red tulips between two green lines
        val fz = hw - 2.6f
        if (z > fz - 0.25f && z < fz + 0.25f || z > fz + 1.9f && z < fz + 2.25f) return Col.hex(0x5E8A5A)
        if (z in fz..fz + 1.9f) {
            val m = (u / 0.55f) - floor(u / 0.55f)
            val zz = z - fz
            if (detail == 1) {
                if (abs(m - 0.5f) < 0.1f && zz in 0.3f..1.3f) return Col.hex(0x5E8A5A)
                if (abs(m - 0.5f) < 0.2f && zz in 1.0f..1.7f) return Col.hex(0xC0503A)
            } else {
                val tl = tulip(m - 0.5f, zz)
                if (tl != 0) return tl
            }
        }
        val patch = Noise.v2(u * 1.2f, z * 0.1f, 41)
        val speck = Noise.rnd(px, py, 43)
        var col = when {
            speck < 0.04f -> plasterD
            speck > 0.965f -> plasterL
            patch > 0.64f && Dither.at(px, py) < 0.5f -> Col.mix(plasterM, plasterD, 0.45f)
            patch < 0.25f && Dither.at(px, py) < 0.5f -> Col.mix(plasterM, plasterL, 0.6f)
            else -> plasterM
        }
        // a hairline crack or two
        val crack = abs(u - 5.3f - sin(z * 0.9f) * 0.08f) < 0.05f && z in wain + 1f..wain + 4.5f
        if (crack) col = plasterD
        return col
    }

    /**
     * Closer up, a tulip of the frieze at [dm] from its stem (in tulip spacings along the wall) and [zz] px up the band:
     * a cup of three petals lit from the left over a thin stem with two leaves; 0 off it.
     */
    private fun tulip(dm: Float, zz: Float): Int {
        val px = 1f / (4f * K * 0.55f) // a picture pixel along the wall, in tulip spacings
        val a = abs(dm)
        if (zz in 0.95f..1.75f) {
            val half = 0.2f * sqrt(((zz - 0.95f) / 0.35f).coerceIn(0f, 1f))
            if (a < half && (zz < 1.45f || a < 0.05f || abs(a - 0.15f) < 0.05f)) return when {
                dm < -0.07f -> Col.hex(0xD86A4C)
                dm > 0.07f -> Col.hex(0x9C3A28)
                zz > 1.45f -> Col.hex(0xE07A5A)
                else -> Col.hex(0xC0503A)
            }
        }
        if (a < px * 0.6f && zz in 0.3f..1.1f) return Col.hex(0x4E7A4A)
        // a leaf either side, curving up and out from the stem
        if (a < 0.14f && zz in 0.35f..0.9f) {
            val rise = zz - (if (dm < 0f) 0.35f else 0.25f)
            if (abs(a - rise * 0.26f) < px * 0.7f + (0.14f - a) * 0.25f && a > px * 0.4f) return Col.hex(0x6E9A5A)
        }
        return 0
    }

    /** The cut tops of the two walls. */
    private fun wallTops() {
        val top = Col.hex(0xEDE4CE); val edge = Col.hex(0xB8AC90)
        iso.top(x0 - th, y0 - th, hw, th, y1 - y0 + th, top)
        iso.top(x0, y0 - th, hw, x1 - x0, th, top)
        iso.line(x0, y0, hw, x0, y1, hw, edge)
        iso.line(x0, y0, hw, x1, y0, hw, edge)
    }

    // ------------------------------------------------------------------ the openings

    /**
     * Looks through an opening in a back wall: [onLeft] the left one (x = X0, running along y), else the right one
     * (y = Y0, along x), over [a]..[b] along it and [z0]..[z1] up. Each pixel of the opening follows the line of
     * sight into the wall and finds the lit jamb (kind 0), the sill (1) or the pane [inset] cells deep (2); [shade]
     * colours it from the kind, the spot along the wall and its height.
     */
    private inline fun opening(onLeft: Boolean, a: Float, b: Float, z0: Float, z1: Float, inset: Float, crossinline shade: (Int, Float, Float, Int, Int) -> Int) {
        if (onLeft) quadFill(x0, a, z0, x0, b, z0, x0, b, z1, x0, a, z1) { px, py ->
            val u = wallXy(px, x0); val z = wallXz(px, py, x0)
            look(u - a, (z - z0) / 4f, inset, u, z, px, py, shade)
        } else quadFill(a, y0, z0, b, y0, z0, b, y0, z1, a, y0, z1) { px, py ->
            val u = wallYx(px, y0); val z = wallYz(px, py, y0)
            look(u - a, (z - z0) / 4f, inset, u, z, px, py, shade)
        }
        c.penEmissive = false
    }

    private inline fun look(tj: Float, ts: Float, inset: Float, u: Float, z: Float, px: Int, py: Int, shade: (Int, Float, Float, Int, Int) -> Int): Int = when {
        tj < ts && tj < inset -> shade(0, tj, z - 4f * tj, px, py)
        ts <= tj && ts < inset -> shade(1, u - ts, ts, px, py)
        else -> shade(2, u - inset, z - 4f * inset, px, py)
    }

    /** A window: the reveal, a white frame of four panes over the day outside, the sill with a geranium; frost and paper snowflakes in winter. */
    private fun window(onLeft: Boolean, a: Float, b: Float) {
        val z0 = winZ0; val z1 = winZ1
        // the casing round it
        part(Pal.OUTLINE)
        val e = 0.16f; val ez = 0.9f
        if (onLeft) quadFill(x0 + 0.02f, a - e, z0 - ez, x0 + 0.02f, b + e, z0 - ez, x0 + 0.02f, b + e, z1 + ez, x0 + 0.02f, a - e, z1 + ez) { px, py ->
            casing(wallXz(px, py, x0 + 0.02f) > z1 + ez - 0.35f, 0.9f)
        } else quadFill(a - e, y0 + 0.02f, z0 - ez, b + e, y0 + 0.02f, z0 - ez, b + e, y0 + 0.02f, z1 + ez, a - e, y0 + 0.02f, z1 + ez) { px, py ->
            casing(wallYz(px, py, y0 + 0.02f) > z1 + ez - 0.35f, 1f)
        }
        val seed = if (onLeft) 3 else 7
        opening(onLeft, a, b, z0, z1, 0.24f) { kind, p, q, px, py ->
            when (kind) {
                0 -> { c.penEmissive = false; if (p > 0.2f) frameD else if (onLeft) plasterM else Col.scale(plasterM, 0.82f) }
                1 -> { c.penEmissive = false; if (q > 0.2f) frameD else plasterL }
                else -> {
                    val u = if (onLeft) (b - p) / (b - a) else (p - a) / (b - a)
                    val v = (q - z0) / (z1 - z0)
                    val fu = 1.6f / ((b - a) * 8f); val fv = 1.6f / ((z1 - z0) * 2f)
                    val bar = u < fu || u > 1f - fu || v < fv || v > 1f - fv || abs(u - 0.5f) < fu * 0.5f || abs(v - 0.62f) < fv * 0.5f
                    if (bar) {
                        c.penEmissive = false
                        // closer up the glazing bars are moulded: a picture pixel of shade along their right and lower edges
                        val pu = 1f / ((b - a) * 4f * K); val pv = 1f / ((z1 - z0) * K)
                        val moulded = detail > 1 && ((u - 0.5f) in fu * 0.5f - pu..fu * 0.5f || (v - 0.62f) in -fv * 0.5f..-fv * 0.5f + pv ||
                            (u in fu - pu..fu && v in fv..1f - fv) || (v in 1f - fv - pv..1f - fv && u in fu..1f - fu))
                        if (u > 1f - fu || v < fv || moulded) frameD else frameW
                    } else {
                        val frost = winter && (min(u, 1f - u) * 3f + min(v, 1f - v) * 2f < 0.35f + Noise.rnd(px, py, 5) * 0.2f)
                        c.penEmissive = !frost
                        if (frost) Col.hex(0xE8F0F8) else if (detail == 1) view(px, py, u, v, seed) else glass(view(px, py, u, v, seed), u, v, b - a, z1 - z0)
                    }
                }
            }
        }
        // the inner sill, a pot on it
        part(Pal.OUTLINE)
        if (onLeft) iso.box(x0, a - 0.12f, z0 - 0.8f, 0.28f, b - a + 0.24f, 0.8f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        else iso.box(a - 0.12f, y0, z0 - 0.8f, b - a + 0.24f, 0.28f, 0.8f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        if (onLeft) geranium(x0 + 0.14f, (a + b) / 2f + 0.25f, z0) else geranium((a + b) / 2f - 0.2f, y0 + 0.14f, z0)
        // paper snowflakes on the panes in winter
        if (env.month == 12 || env.month == 1) {
            val (fx, fy) = if (onLeft) x0 - 0.2f to a + (b - a) * 0.3f else a + (b - a) * 0.7f to y0 - 0.2f
            val sx = iso.ix(fx, fy); val sy = iso.iy(fx, fy, z1 - 3f)
            if (detail == 1) {
                c.set(sx, sy, Pal.FLAG_WHITE); c.set(sx - 1, sy, Pal.FLAG_WHITE); c.set(sx + 1, sy, Pal.FLAG_WHITE); c.set(sx, sy - 1, Pal.FLAG_WHITE); c.set(sx, sy + 1, Pal.FLAG_WHITE)
            } else {
                // closer up a cut-paper star: six arms, each with a pair of little twigs
                val mx = sx + detail / 2; val my = sy + detail / 2
                val len = 1.5f * detail
                for (k in 0 until 6) {
                    val an = k * PI / 3
                    val ex = cos(an).toFloat(); val ey = -sin(an).toFloat()
                    c.line(mx, my, (mx + ex * len).roundToInt(), (my + ey * len).roundToInt(), Pal.FLAG_WHITE)
                    if (detail > 2) {
                        val bx = mx + ex * len * 0.6f; val by = my + ey * len * 0.6f
                        for (s in intArrayOf(-1, 1)) {
                            val tw = an + s * PI / 3
                            c.set((bx + cos(tw).toFloat() * 1.2f).roundToInt(), (by - sin(tw).toFloat() * 1.2f).roundToInt(), Pal.FLAG_WHITE)
                        }
                    }
                }
            }
        }
    }

    private fun casing(top: Boolean, lum: Float): Int = Col.scale(if (top) frameW else Col.mix(frameW, frameD, 0.4f), lum)

    /**
     * Closer up, the old glass over [col] at [u] across and [v] up a window [across] cells wide and [high] px tall: two
     * thin streaks of reflected light running down to the right, fainter at night.
     */
    private fun glass(col: Int, u: Float, v: Float, across: Float, high: Float): Int {
        val g = u * across * 4f * K + (1f - v) * high * K * 0.8f
        val m = g - floor(g / (9f * K)) * (9f * K)
        val a = if (env.dark > 0.5f) 0.1f else 0.22f
        return if (m < 1f || m in 2.5f..3.5f + detail * 0.3f) Col.mix(col, Col.hex(0xFFFFFF), a) else col
    }

    /** A pot of red geraniums on a sill at world ([x], [y]) on height [z]; closer up, round flower heads over mottled leaves. */
    private fun geranium(x: Float, y: Float, z: Float) {
        val cx = iso.ix(x, y); val by = iso.iy(x, y, z)
        val d = detail
        val pot = Col.hex(0xC0603A); val potD = Col.hex(0x8E4228)
        c.fillRect(cx - 2 * d, by - 5 * d, 5 * d, 5 * d, pot); c.fillRect(cx + 2 * d, by - 5 * d, d, 5 * d, potD); c.fillRect(cx - 3 * d, by - 5 * d, 7 * d, d, Col.hex(0xD8784E))
        if (d > 1) {
            // the rim's lit lip, the pot's shade under it, and the earth
            c.hline(cx - 3 * d, cx + 4 * d - 1, by - 5 * d, Col.hex(0xF09A6A))
            c.hline(cx - 2 * d, cx + 3 * d - 1, by - 4 * d, potD)
            c.fillRect(cx - 2 * d, by - 5 * d - 1, 5 * d, 1, Col.hex(0x4A3020))
        }
        c.fillEllipse(cx + 0.5f * d, by - 8f * d, 4f * d, 2.8f * d, Pal.LEAF)
        c.fillEllipse(cx - 0.5f * d, by - 8.8f * d, 2.4f * d, 1.6f * d, Col.hex(0x5AA84E))
        val heads = listOf(-2 to -10, 1 to -11, 3 to -9, -3 to -8)
        if (d == 1) {
            for ((dx, dy) in heads) { c.set(cx + dx, by + dy, Pal.GERANIUM); c.set(cx + dx + 1, by + dy, Pal.GERANIUM_D) }
            c.set(cx, by - 12, Pal.GERANIUM)
            return
        }
        // the leaves: darker veins and edges, a few catching the light
        val lx = cx + 0.5f * d; val ly = by - 8f * d; val rx = 4f * d; val ry = 2.8f * d
        for (yy in (ly - ry).toInt()..(ly + ry).toInt()) for (xx in (lx - rx).toInt()..(lx + rx).toInt()) {
            val ex = (xx + 0.5f - lx) / rx; val ey = (yy + 0.5f - ly) / ry
            if (ex * ex + ey * ey > 1f || !c.inside(xx, yy)) continue
            val n = Noise.rnd(xx, yy, 13)
            if (n < 0.14f || (ex * ex + ey * ey > 0.7f && ey > 0f && n < 0.5f)) c.set(xx, yy, Col.scale(Pal.LEAF, 0.78f))
            else if (ey < -0.2f && n > 0.93f) c.set(xx, yy, Col.hex(0x8ACB6A))
        }
        for ((dx, dy) in heads) flowerHead(cx + (dx + 1f) * d, by + (dy + 0.5f) * d, 1.15f * d)
        flowerHead(cx + 0.5f * d, by - 11.5f * d, 0.8f * d)
    }

    /** A geranium's head closer up: a ball of little florets, dark on the lower right, a light one on top. */
    private fun flowerHead(x: Float, y: Float, r: Float) {
        c.fillEllipse(x, y, r, r * 0.8f, Pal.GERANIUM)
        c.fillEllipse(x + r * 0.35f, y + r * 0.3f, r * 0.55f, r * 0.45f, Pal.GERANIUM_D)
        c.set((x - r * 0.4f).toInt(), (y - r * 0.45f).toInt(), Col.hex(0xFF9A8A))
        c.set((x + r * 0.1f).toInt(), (y - 0.1f).toInt(), Col.hex(0xF06A5A))
    }

    /** What a window shows at [u] across and [v] up its glass: the sky of the hour, the hills (snowy in winter), the church or a tree. */
    private fun view(px: Int, py: Int, u: Float, v: Float, seed: Int): Int {
        val hill = 0.3f + Noise.v1(u * 3f + seed, 5) * 0.12f
        if (v < hill) {
            val far = if (winter) Pal.SNOW_M else when (env.season) { Season.AUTUMN -> Col.hex(0x8A8A46); Season.SPRING -> Col.hex(0x6CB050); else -> Col.hex(0x5C9A48) }
            // the church on its hill in one window, a tree in the other
            if (seed == 3 && abs(u - 0.62f) < 0.07f && v > hill - 0.02f) return env.lit(Pal.WALL_L)
            return env.lit(if (v > hill - 0.04f) Col.mix(far, Col.hex(0xFFFFFF), 0.12f) else far)
        }
        if (seed == 3 && abs(u - 0.62f) < 0.07f && v < hill + 0.16f) return env.lit(Pal.WALL_L)
        if (seed == 3 && abs(u - 0.62f) < 0.1f - (v - hill - 0.16f) * 0.9f && v in hill + 0.16f..hill + 0.28f) return env.lit(Pal.SLATE_M)
        if (seed == 7 && hypot((u - 0.3f) * 1.4f, v - hill - 0.12f) < 0.14f) {
            val leaf = when (env.season) { Season.WINTER -> Col.hex(0x6A6058); Season.AUTUMN -> Col.hex(0xE0902C); Season.SPRING -> Col.hex(0x6CB050); else -> Col.hex(0x3E8A3A) }
            return env.lit(if (winter && Dither.at(px, py) < 0.5f) Pal.SNOW_L else leaf)
        }
        if (seed == 7 && abs(u - 0.3f) < 0.03f && v < hill + 0.05f) return env.lit(Pal.WOOD_D)
        var col = skyAt((1f - v + Dither.at(px, py) * 0.08f).coerceIn(0f, 1f))
        // the stars at night, the snow falling in winter: as many as on the scene's canvas, a star a picture pixel
        // sharp and a flake round closer up
        val d = detail
        val bx = px / d; val by = py / d; val ix = px - bx * d; val iy = py - by * d
        val st = ((env.dark - 0.3f) / 0.5f).coerceIn(0f, 1f)
        if (st > 0f && ix == d / 2 && iy == d / 2 && Noise.rnd(bx, by, 67) < 0.03f) col = Col.mix(col, Col.hex(0xFFF8E0), st * (0.6f + 0.4f * sin(t * 2 + bx).toFloat()))
        if (env.snow && (d < 3 || ix == 1 || iy == 1) && Noise.rnd(bx, by - (t * 9).toInt(), 69) < 0.04f) col = Col.hex(0xFFFFFF)
        return col
    }

    /** The door: a panelled leaf in its casing, a brass handle, the threshold. */
    private fun door() {
        val z1 = fl + dh
        part(Pal.OUTLINE)
        quadFill(x0 + 0.02f, da - 0.18f, fl, x0 + 0.02f, db + 0.18f, fl, x0 + 0.02f, db + 0.18f, z1 + 1f, x0 + 0.02f, da - 0.18f, z1 + 1f) { px, py ->
            val z = wallXz(px, py, x0 + 0.02f)
            Col.scale(if (z > z1 + 0.6f) Pal.WOOD_L else Pal.WOOD_M, 0.86f)
        }
        opening(true, da, db, fl, z1, 0.12f) { kind, p, q, px, py ->
            when (kind) {
                0 -> Pal.WOOD_L
                1 -> Pal.WOOD_D
                else -> {
                    val u = (db - p) / (db - da); val v = (q - fl) / dh
                    val inU = u in 0.16f..0.84f
                    val upper = v in 0.55f..0.92f; val lower = v in 0.08f..0.47f
                    when {
                        inU && (upper || lower) -> {
                            val pv = if (upper) (v - 0.55f) / 0.37f else (v - 0.08f) / 0.39f
                            when {
                                u < 0.22f || pv > 0.94f -> Pal.WOOD_D
                                u > 0.78f || pv < 0.06f -> Col.mix(Pal.WOOD_M, Pal.WOOD_L, 0.6f)
                                else -> if (Noise.v2(u * 3f, pv * 8f, 13) > 0.7f) Col.scale(Pal.WOOD_M, 0.93f) else Pal.WOOD_M
                            }
                        }
                        else -> if (Noise.v2(u * 2f, v * 12f, 17) > 0.72f) Col.scale(Pal.DOOR, 1.25f) else Col.mix(Pal.DOOR, Pal.WOOD_M, 0.45f)
                    }
                }
            }
        }
        // the handle, on the side away from the hinges
        val hx = iso.ix(x0 - 0.1f, db - 0.28f); val hy = iso.iy(x0 - 0.1f, db - 0.28f, fl + 8.5f)
        val d = detail
        if (d > 1) {
            // closer up: the brass plate behind the lever, the keyhole in it
            c.fillRect(hx - 1, hy - d, d + 2, 4 * d, Col.scale(Pal.GOLD, 0.8f))
            c.vline(hx - 1, hy - d, hy + 3 * d - 1, Pal.GOLD_L)
        }
        c.fillRect(hx - d, hy, 3 * d, d, Pal.GOLD); pix(hx - d, hy - d, Pal.GOLD_L)
        if (d == 1) { c.set(hx, hy + 2, Pal.WOOD_X); return }
        // the lever's lit top and shaded underside, a round end; the keyhole a dot over a slot
        c.hline(hx - d, hx + 2 * d - 1, hy, Col.mix(Pal.GOLD_L, Col.hex(0xFFFFFF), 0.3f))
        c.hline(hx - d + 1, hx + 2 * d - 1, hy + d - 1, Col.scale(Pal.GOLD, 0.7f))
        val kx = hx + d / 2
        c.set(kx, hy + 2 * d - 1, Pal.WOOD_X); c.vline(kx, hy + 2 * d, hy + 3 * d - 2, Pal.WOOD_X)
        if (d > 2) { c.set(kx - 1, hy + 2 * d - 1, Pal.WOOD_X); c.set(kx + 1, hy + 2 * d - 1, Pal.WOOD_X) }
    }

    /** A coat peg between the map and the door with a red scarf on it. */
    private fun peg() {
        val y = 6.35f; val z = fl + 13.5f
        rod(x0 + 0.05f, y - 0.2f, z, x0 + 0.05f, y + 0.2f, z, Pal.WOOD_D)
        rod(x0 + 0.05f, y - 0.2f, z + 0.5f, x0 + 0.05f, y + 0.2f, z + 0.5f, Pal.WOOD_L)
        // the scarf, hanging in two ends
        val d = detail
        for (j in 0..1) {
            val yy = y - 0.08f + j * 0.16f
            val sx = iso.ix(x0 + 0.1f, yy); val top = iso.iy(x0 + 0.1f, yy, z - 0.2f)
            val len = if (j == 0) 11 else 9
            c.fillRect(sx - d, top, 2 * d, len * d, Pal.FLAG_RED)
            c.fillRect(sx - d, top + (len - 3) * d, 2 * d, d, Pal.FLAG_WHITE)
            if (d == 1) { c.set(sx - 1, top + len, Pal.GERANIUM_D); c.set(sx, top + len, Pal.GERANIUM_D); continue }
            // closer up: the knitted ribs down it, a fringe of loose threads
            val stripe = (len - 3) * d until (len - 2) * d
            for (ry in 0 until len * d) for (xx in sx - d until sx + d) if ((xx - sx) and 1 == 1) {
                c.set(xx, top + ry, if (ry in stripe) Col.hex(0xDCD6CC) else Col.scale(Pal.FLAG_RED, 0.8f))
            }
            for (xx in sx - d until sx + d) c.vline(xx, top + len * d, top + len * d + d - 1 - (if (Noise.rnd(xx, j, 5) < 0.4f) 1 else 0), Pal.GERANIUM_D)
        }
    }

    private val sconceY = 3.2f; private val sconceZ = fl + 14.8f

    /** A lamp on a brass bracket between the window and the map, its glass shade lit from dusk. */
    private fun sconce(lit: Boolean) {
        val y = sconceY; val z = sconceZ
        val brass = Col.hex(0xC89A3A); val brassD = Col.hex(0x8A6420)
        rod(x0 + 0.02f, y, z - 2.2f, x0 + 0.02f, y, z - 0.6f, brassD)
        rod(x0 + 0.02f, y, z - 1.6f, x0 + 0.45f, y, z - 0.3f, brass)
        val cx = iso.ix(x0 + 0.45f, y); val cy = iso.iy(x0 + 0.45f, y, z)
        val d = detail
        c.fillRect(cx - d, cy - d, 3 * d, 2 * d, brass)
        if (d == 1) {
            if (lit) glow {
                c.fillRect(cx - 2, cy - 6, 5, 5, Pal.WINDOW_LIT); c.fillRect(cx - 1, cy - 5, 3, 3, Pal.WINDOW_LIT_HI)
            } else {
                c.fillRect(cx - 2, cy - 6, 5, 5, Col.hex(0xE8E0CC)); c.vline(cx + 2, cy - 6, cy - 2, Col.hex(0xC0B8A4)); c.set(cx - 1, cy - 5, Col.hex(0xFFFFFF))
            }
        } else {
            // closer up the shade is a round glass bell: lit, glowing out from the bulb; by day, milky with a gleam
            val gx = cx + 0.5f * d; val gy = cy - 3.4f * d
            if (lit) glow {
                c.fillEllipse(gx, gy, 2.5f * d, 2.6f * d, Pal.WINDOW_LIT)
                c.fillEllipse(gx, gy + 0.2f * d, 1.6f * d, 1.7f * d, Pal.WINDOW_LIT_HI)
                c.fillEllipse(gx, gy + 0.3f * d, 0.7f * d, 0.8f * d, Col.hex(0xFFF8E0))
            } else {
                c.fillEllipse(gx, gy, 2.5f * d, 2.6f * d, Col.hex(0xE8E0CC))
                c.fillEllipse(gx + 0.9f * d, gy + 0.4f * d, 1.3f * d, 2f * d, Col.hex(0xC0B8A4))
                c.fillEllipse(gx - 0.2f * d, gy + 0.1f * d, 1.5f * d, 2.1f * d, Col.hex(0xE8E0CC))
                c.vline(cx - d + 1, cy - 5 * d + 1, cy - 4 * d + 1, Col.hex(0xFFFFFF))
            }
        }
        c.fillRect(cx - 2 * d, cy - 7 * d, 5 * d, d, brassD)
        if (d > 1) c.hline(cx - 2 * d + 1, cx + 3 * d - 2, cy - 7 * d, brass)
        if (lit) s.fx {
            c.penEmissive = true
            c.ditherCircle(cx + 0.5f * d, cy - 3.5f * d, 7f * K, Pal.WINDOW_LIT, 0.12f * env.windows)
            c.penEmissive = false
        }
    }

    /** A low bookcase under the window: two shelves of books, two more lying on top, a jar of coloured pencils. */
    private fun bookcase() {
        val ya = 0.95f; val yb = 3.25f; val dpt = 0.55f; val hgt = 7f
        val xf = x0 + dpt
        iso.panelY(x0, yb, fl, dpt, hgt, Pal.WOOD_M)
        quadFill(xf, ya, fl, xf, yb, fl, xf, yb, fl + hgt, xf, ya, fl + hgt) { px, py ->
            val y = wallXy(px, xf); val z = wallXz(px, py, xf) - fl
            val shelf = if (z < 3.6f) 0.7f else 3.9f
            val bi = floor((y - ya - 0.1f) / 0.13f).toInt()
            val bookH = 2.1f + Noise.rnd(bi, if (shelf < 1f) 3 else 4, 9) * 0.8f
            val lean = Noise.rnd(bi, 5, 9) < 0.1f
            when {
                y < ya + 0.08f || y > yb - 0.08f || z > hgt - 0.5f || z < 0.7f -> Pal.WOOD_D
                detail > 1 && z in 3.75f..3.9f -> Pal.WOOD_M // closer up, the shelf's lit front edge
                z in 3.3f..3.9f -> Pal.WOOD_D
                z - shelf < bookH && !lean -> {
                    val col = BOOKS[Math.floorMod(Noise.hash(bi, if (shelf < 1f) 11 else 12), BOOKS.size)]
                    if (detail == 1) return@quadFill if (abs(z - shelf - bookH * 0.7f) < 0.25f) Col.mix(col, Pal.GOLD_L, 0.5f) else col
                    // closer up each spine is round: a shadow between the books, its lettering and two gilt bands
                    val f = (y - ya - 0.1f) / 0.13f - bi
                    val zb = z - shelf; val line = 0.6f / K
                    val band = abs(zb - bookH * 0.84f) < line || abs(zb - bookH * 0.22f) < line
                    val title = Noise.rnd(bi, 6, 9) < 0.6f && abs(zb - bookH * 0.55f) < bookH * 0.18f && abs(f - 0.55f) < 0.2f &&
                        Noise.rnd(bi, (zb * K).toInt(), 17) < 0.55f
                    when {
                        f < 0.24f -> Col.scale(col, 0.7f)
                        band -> Col.mix(col, Pal.GOLD_L, 0.6f)
                        title -> Col.mix(col, Col.hex(0xF4EEDD), 0.55f)
                        f > 0.7f -> Col.mix(col, Col.hex(0xFFFFFF), 0.14f)
                        else -> col
                    }
                }
                else -> Col.hex(0x3A2416)
            }
        }
        iso.top(x0, ya, fl + hgt, dpt, yb - ya, Pal.WOOD_L)
        iso.line(xf, ya, fl + hgt, xf, yb, fl + hgt, Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.25f))
        // two books lying on top, a jar of coloured pencils
        iso.box(x0 + 0.08f, 1.15f, fl + hgt, 0.4f, 0.6f, 0.7f, Col.hex(0x5E8A5A), Col.hex(0x3E6A3A), Col.hex(0xF4EEDD))
        iso.box(x0 + 0.12f, 1.2f, fl + hgt + 0.7f, 0.34f, 0.5f, 0.6f, Col.hex(0xD86A4A), Col.hex(0xB84A2E), Col.hex(0xF4EEDD))
        val jx = iso.ix(x0 + 0.3f, 2.95f); val jy = iso.iy(x0 + 0.3f, 2.95f, fl + hgt)
        val pencils = intArrayOf(Pal.FLAG_RED, Pal.GOLD, Col.hex(0x3E8A3A), Col.hex(0x2E63B0))
        val d = detail
        if (d == 1) {
            for (j in 0..3) c.line(jx - 1 + j, jy - 4, jx - 2 + j * 2 - 1, jy - 8 - (j % 2), pencils[j])
            c.fillRect(jx - 2, jy - 4, 4, 4, Col.hex(0xB8D0DC)); c.vline(jx - 2, jy - 4, jy - 1, Col.hex(0xE8F4F8)); c.vline(jx + 1, jy - 4, jy - 1, Col.hex(0x8AA8B8))
            return
        }
        // closer up: slimmer pencils, sharpened, seen through the glass below its rim
        val glassC = Col.hex(0xB8D0DC)
        c.fillRect(jx - 2 * d, jy - 4 * d, 4 * d, 4 * d, glassC)
        for (j in 0..3) {
            val xa = jx + (j - 1) * d + d / 2; val xb = jx + (2 * j - 3) * d + d / 2; val ya = jy - 4 * d; val yb = jy - (8 + j % 2) * d
            for (yy in ya + 1 until jy) c.set(xa + (xb - xa) * (yy - ya) / (yb - ya), yy, Col.mix(glassC, pencils[j], 0.45f))
            sharpPencil(xa, ya, xb, yb, pencils[j], d - 1)
        }
        c.hline(jx - 2 * d, jx + 2 * d - 1, jy - 4 * d, Col.hex(0xE8F4F8))
        c.vline(jx - 2 * d, jy - 4 * d, jy - 1, Col.hex(0xE8F4F8)); c.vline(jx - 2 * d + 1, jy - 3 * d, jy - d, Col.hex(0xFFFFFF))
        c.vline(jx + 2 * d - 1, jy - 4 * d, jy - 1, Col.hex(0x8AA8B8))
    }

    // ------------------------------------------------------------------ Mojca's alcove (companion/SCENES.md, "Asleep at night")

    /**
     * Mojca's bed for the night along the left wall: an oak bedstead, its headboard against the bookcase's end, the frame on
     * legs, a straw mattress with a linen pillow at the head. Returns the mattress's top. (Its footboard is [footboard],
     * drawn in front of her feet.)
     */
    private fun teacherBed(): Float {
        val fz = fl + 2.6f; val mh = 1.4f
        prop {
            iso.box(MB_A, MB_C - 0.14f, fl, MB_B - MB_A, 0.14f, 7.6f, oakL, oakM, oakD)
            iso.line(MB_A, MB_C, fl + 7.6f, MB_B, MB_C, fl + 7.6f, Col.mix(oakL, Col.hex(0xFFFFFF), 0.25f))
            iso.post(MB_B - 0.1f, MB_D - 0.1f, fl, fz, oakD)
            iso.post(MB_B - 0.1f, MB_C + 0.1f, fl, fz, oakD)
            iso.box(MB_A, MB_C, fz - 0.9f, MB_B - MB_A, MB_D - MB_C, 0.9f, oakL, oakM, oakD)
            iso.line(MB_B, MB_C, fz, MB_B, MB_D, fz, Col.mix(oakL, Col.hex(0xFFFFFF), 0.25f))
            strawMattress(MB_A + 0.04f, MB_C + 0.02f, MB_B - 0.04f, MB_D - 0.04f, alongY = true, z0 = fz, h = mh)
        }
        return fz + mh
    }

    /** The bed's footboard, lower than its head, in front of her feet. */
    private fun footboard() {
        iso.box(MB_A, MB_D, fl, MB_B - MB_A, 0.14f, 5.4f, oakL, oakM, oakD)
        iso.line(MB_A, MB_D + 0.14f, fl + 5.4f, MB_B, MB_D + 0.14f, fl + 5.4f, Col.mix(oakL, Col.hex(0xFFFFFF), 0.25f))
    }

    /**
     * The alcove's curtain: a rod high along the bed's outer side on two brackets from the wall, and the curtain drawn back
     * along it and gathered at the bed's head, by the bookcase, tied back with a red cord halfway down, its folds falling to
     * the floor; blue and white checked cotton.
     */
    private fun alcoveCurtain() {
        val ya = MB_C - 0.9f; val yb = MB_D + 0.1f
        val iron = Col.hex(0x3A3632); val ironL = Col.hex(0x7A746C)
        for (y in floatArrayOf(ya, yb)) rod(x0 + 0.02f, y, ROD_Z, ROD_X, y, ROD_Z, iron)
        rod(ROD_X, ya, ROD_Z + 0.4f, ROD_X, yb, ROD_Z + 0.4f, ironL)
        // the gathered curtain on the plane x = ROD_X, from the rod down to the floor
        val ga = ya; val gb = MB_C + 0.02f; val tie = fl + 9.5f
        quadFill(ROD_X, ga, fl + 0.4f, ROD_X, gb, fl + 0.4f, ROD_X, gb, ROD_Z, ROD_X, ga, ROD_Z) { px, py ->
            val y = wallXy(px, ROD_X); val z = wallXz(px, py, ROD_X)
            // how much of the bunch's width is cloth at this height: full at the rod, pinched at the cord, flaring below
            val w = when {
                z > tie -> 0.6f + 0.4f * ((z - tie) / (ROD_Z - tie)).coerceIn(0f, 1f)
                else -> 0.6f + 0.3f * ((tie - z) / (tie - fl)).coerceIn(0f, 1f)
            }
            val u = (y - ga) / (gb - ga)
            val off = abs(u - 0.5f) * 2f
            if (off > w) return@quadFill 0
            val cord = abs(z - tie) < 0.45f
            val fold = floor(u * 6f + (z - tie) * 0.02f).toInt()
            val check = detail > 1 && Math.floorMod(floor(z / 1.6f).toInt(), 2) == 0
            when {
                cord -> if (z > tie) CORD else Col.scale(CORD, 0.75f)
                off > w - 0.12f -> CURTAIN_D
                fold % 2 == 0 -> if (check) Col.mix(CURTAIN_L, CURTAIN_M, 0.35f) else CURTAIN_L
                else -> if (check) CURTAIN_D else CURTAIN_M
            }
        }
    }

    /** A coloured pencil closer up from ([xa], [ya]) up to ([xb], [yb]), [n] px thick, its sharpened tip beyond. */
    private fun sharpPencil(xa: Int, ya: Int, xb: Int, yb: Int, col: Int, n: Int) {
        thick(xa, ya, xb, yb, col, n)
        val dx = (xb - xa).toFloat(); val dy = (yb - ya).toFloat(); val l = hypot(dx, dy)
        val tx = (xb + dx / l * 1.6f * detail).roundToInt(); val ty = (yb + dy / l * 1.6f * detail).roundToInt()
        c.line(xb, yb, tx, ty, Col.hex(0xE8C898))
        c.set(tx, ty, Col.scale(col, 0.7f))
    }

    // ------------------------------------------------------------------ the blackboard

    private fun blackboard() {
        val yb = y0 + 0.02f
        quadFill(ba, yb, bz0, bb, yb, bz0, bb, yb, bz1, ba, yb, bz1) { px, py ->
            val x = wallYx(px, yb); val z = wallYz(px, py, yb)
            when {
                z > bz1 - 0.35f -> Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.2f)
                x < ba + 0.06f -> Pal.WOOD_L
                x > bb - 0.06f || z < bz0 + 0.3f -> Pal.WOOD_D
                detail == 1 -> Pal.WOOD_M
                else -> {
                    // closer up the frame's grain runs along each piece: across the rails, up the stiles
                    val g = if (x < ba + 0.2f || x > bb - 0.2f) Noise.v2(x * 30f, z * 0.22f, 31) else Noise.v2(x * 1.4f, z * 2.4f, 31)
                    if (g > 0.66f) Col.scale(Pal.WOOD_M, 0.88f) else if (g < 0.22f) Col.mix(Pal.WOOD_M, Pal.WOOD_L, 0.35f) else Pal.WOOD_M
                }
            }
        }
        val ia = ba + 0.2f; val ib = bb - 0.2f; val iz0 = bz0 + 0.8f; val iz1 = bz1 - 0.8f
        quadFill(ia, yb, iz0, ib, yb, iz0, ib, yb, iz1, ia, yb, iz1) { px, py ->
            val x = wallYx(px, yb); val z = wallYz(px, py, yb)
            val smudge = Noise.v2(x * 1.6f, z * 0.35f, 29)
            when {
                Noise.rnd(px, py, 7) < 0.08f -> slateL
                smudge > 0.72f && Dither.at(px, py) < (smudge - 0.72f) * 4f -> Col.mix(slate, chalkD, 0.22f)
                z < iz0 + 0.5f -> slateD
                // closer up, the chalk dust settled along the bottom of the slate
                detail > 1 && z < iz0 + 1.8f && Noise.rnd(px, py, 19) < (iz0 + 1.8f - z) / 1.3f * 0.35f -> Col.mix(slate, chalkD, 0.2f)
                else -> slate
            }
        }
        // the lesson, on the board's own pixel grid: each column steps down half a pixel, like the wall's edge
        val sx0 = iso.sx(ia, yb); val sy0 = iso.sy(ia, yb, iz1)
        val ax = ceil(sx0 - 0.5f).toInt()
        val cols = ((ib - ia) * 4f * K).toInt()
        if (detail == 1) {
            fun top(col: Int): Int = ceil(sy0 + (ax + col + 0.5f - sx0) * 0.5f - 0.5f).toInt()
            fun dot(col: Int, y: Int, ink: Int) {
                if (col < 0 || col >= cols) return
                val x = ax + col
                c.set(x, y, if (ink == chalkW && Noise.rnd(x, y, 71) < 0.06f) Col.mix(chalkW, chalkD, 0.5f) else ink)
            }
            // each letter slants on its own and starts where the board's edge is at its left, so all of them look alike
            fun letter(x0: Int, gx: Int, k: Int, row: Int, ink: Int) = dot(x0 + gx + k, top(x0 + gx) + row, ink)
            val w1 = ChalkFont.write("Dober dan!", slant = true) { gx, k, ry -> letter(4, gx, k, ry, chalkW) }
            for (cx in 4 until 4 + w1) if (Noise.rnd(cx, 3) < 0.85f) dot(cx, top(cx) + 8 + (if (cx % 7 == 3) 1 else 0), chalkD)
            ChalkFont.write("Kako si?", slant = true) { gx, k, ry -> letter(4, gx, k, 10 + ry, chalkW) }
            ChalkFont.write("Dobro.", slant = true) { gx, k, ry -> letter(4, gx, k, 18 + ry, chalkD) }
            // a chalk sun with a smile
            val sc = cols - 8; val sr = 18
            for (row in -8..8) for (col in -8..8) {
                val d = hypot(col.toFloat(), row.toFloat())
                val a = kotlin.math.atan2(row.toFloat(), col.toFloat())
                val ray = d in 5.5f..7.6f && abs(((a / (PI.toFloat() / 4f)) + 8f) % 1f - 0.5f) > 0.38f
                val ring = abs(d - 3.6f) < 0.55f
                val smile = (row == 1 && abs(col) == 2) || (row == 2 && abs(col) <= 1)
                val eye = row == -1 && abs(col) == 1
                if (ray || ring || smile || eye) dot(sc + col, top(sc + col) + sr + row, chalkY)
            }
        } else lesson(sx0, sy0, ax, cols, ((ib - ia) * 4f * BASE_K).toInt())
        // the ledge under the board, dusty with chalk
        part(Pal.OUTLINE)
        iso.box(ba + 0.1f, y0, bz0 - 0.9f, bb - ba - 0.2f, 0.34f, 0.9f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        var lx = ba + 0.3f
        while (lx < bb - 0.3f) { if (Noise.rnd((lx * 10).toInt(), 5) < 0.5f) iso.px(lx, y0 + 0.2f, bz0, Col.mix(Pal.WOOD_L, chalkW, 0.6f)); lx += 0.37f }
        if (detail > 1) for (k in 0 until 70) {
            // closer up, fine dust all along it
            val dx = ba + 0.2f + Noise.rnd(k, 81) * (bb - ba - 0.4f); val dy = y0 + 0.04f + Noise.rnd(k, 82) * 0.28f
            c.set(iso.ix(dx, dy), iso.iy(dx, dy, bz0), Col.mix(Pal.WOOD_L, chalkW, 0.35f + Noise.rnd(k, 83) * 0.5f))
        }
    }

    /**
     * The lesson closer up: the same words and the same sun in the same places as on the scene's canvas ([cols1] of
     * its columns wide), written with a finer piece of chalk ([ChalkHand]): strokes a picture pixel thin, slanting
     * with the board's edge ([sx0], [sy0] its top left; [ax] its first column, [cols] its columns in the picture).
     */
    private fun lesson(sx0: Float, sy0: Float, ax: Int, cols: Int, cols1: Int) {
        val d = detail
        val mid = (d - 1) / 2f
        // the board's top edge over the picture's column x, and the middle of a cell of the scene canvas under it
        fun edge(x: Float): Float = sy0 + (x + 0.5f - sx0) * 0.5f
        fun chalk(x: Int, y: Int, ink: Int) {
            if (x < ax || x >= ax + cols) return
            c.set(x, y, if (Noise.rnd(x, y, 71) < 0.16f) Col.mix(ink, slate, 0.5f) else ink)
        }
        fun write(text: String, row0: Int, ink: Int) {
            var l = 0
            for (ch in text) {
                val g = ChalkFont.glyph(ch) ?: continue
                for (st in ChalkHand.strokes(ch)) {
                    var lx = 0; var ly = 0
                    for (i in 0 until st.size / 2) {
                        val xf = ax + (4 + l + st[2 * i]) * d + mid
                        val yf = edge(xf) + (row0 + st[2 * i + 1]) * d + mid
                        val x = floor(xf + 0.5f).toInt(); val y = floor(yf + 0.5f).toInt()
                        if (i > 0) seg(lx, ly, x, y) { a, b -> chalk(a, b, ink) }
                        else if (st.size == 2) { chalk(x, y, ink); if (d > 2) { chalk(x + 1, y, ink); chalk(x, y + 1, ink); chalk(x + 1, y + 1, ink) } }
                        lx = x; ly = y
                    }
                }
                l += g[0].length + 1
            }
        }
        write("Dober dan!", 0, chalkW)
        // underlined with a wavering stroke
        val w1 = ChalkFont.write("Dober dan!", slant = false) { _, _, _ -> }
        for (x in ax + 4 * d until ax + (4 + w1) * d) {
            if (Noise.rnd(x / (2 * d), 3) >= 0.85f) continue
            chalk(x, floor(edge(x.toFloat()) + 8 * d + mid + sin(x * 0.55f / d) * 0.4f * d + 0.5f).toInt(), chalkD)
        }
        write("Kako si?", 10, chalkW)
        write("Dobro.", 18, chalkD)
        // the sun with a smile: a ring, long and short rays, two eyes
        val xc = ax + (cols1 - 8) * d + mid
        val r8 = 8.5f * d
        for (x in floor(xc - r8).toInt()..ceil(xc + r8).toInt()) {
            val yc = edge(x.toFloat()) + 18 * d + mid
            for (y in floor(yc - r8).toInt()..ceil(yc + r8).toInt()) {
                val cx = (x - xc) / d; val cy = (y - yc) / d
                val dist = hypot(cx, cy)
                val a = kotlin.math.atan2(cy, cx) / (PI.toFloat() / 8f)
                val k = floor(a + 0.5f)
                val off = abs(a - k) * (PI.toFloat() / 8f) * dist
                val long = Math.floorMod(k.toInt(), 2) == 0
                val ray = off * d < 0.6f && (if (long) dist in 5.2f..7.8f else dist in 5.4f..6.6f)
                val ring = abs(dist - 3.6f) * d < 0.6f
                val smile = abs(hypot(cx, cy + 0.5f) - 2.5f) * d < 0.6f && cy > 0.6f
                val eye = hypot(abs(cx) - 1f, cy + 1f) * d < 0.9f
                if (ray || ring || smile || eye) chalk(x, y, chalkY)
            }
        }
    }

    /** The teacher's pointer leaning against the board's end. */
    private fun pointer() {
        val x = 1.45f
        if (detail == 1) {
            iso.line(x, y0 + 0.5f, fl, x + 0.1f, y0 + 0.08f, fl + 15f, Pal.WOOD_L)
            iso.line(x + 0.05f, y0 + 0.5f, fl, x + 0.15f, y0 + 0.08f, fl + 15f, Pal.WOOD_D)
        } else {
            // closer up a round stick: shaded, a picture pixel of light down its left side
            rod(x + 0.02f, y0 + 0.5f, fl, x + 0.12f, y0 + 0.08f, fl + 15f, Pal.WOOD_D)
            rod(x, y0 + 0.5f, fl, x + 0.1f, y0 + 0.08f, fl + 15f, Pal.WOOD_L, detail - 1)
            rod(x, y0 + 0.5f, fl, x + 0.1f, y0 + 0.08f, fl + 15f, Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.3f), 1)
        }
        pix(iso.ix(x + 0.1f, y0 + 0.08f), iso.iy(x + 0.1f, y0 + 0.08f, fl + 15.5f), Pal.OUTLINE)
    }

    /** Two sticks of chalk on the ledge, a white one and a yellow one. */
    private fun chalk() {
        val z = bz0 + 0.05f
        rod(4.3f, y0 + 0.16f, z, 4.75f, y0 + 0.16f, z, chalkW)
        rod(4.3f, y0 + 0.2f, z + 0.5f, 4.75f, y0 + 0.2f, z + 0.5f, chalkW)
        rod(4.95f, y0 + 0.2f, z, 5.25f, y0 + 0.2f, z, chalkY)
        rod(4.95f, y0 + 0.22f, z + 0.5f, 5.25f, y0 + 0.22f, z + 0.5f, Col.mix(chalkY, chalkW, 0.4f))
        if (detail > 1) {
            // closer up, round: shaded along the bottom, the white one worn down at its end
            val d = detail
            c.line(iso.ix(4.3f, y0 + 0.16f), iso.iy(4.3f, y0 + 0.16f, z) + d - 1, iso.ix(4.75f, y0 + 0.16f), iso.iy(4.75f, y0 + 0.16f, z) + d - 1, chalkD)
            c.line(iso.ix(4.95f, y0 + 0.2f), iso.iy(4.95f, y0 + 0.2f, z) + d - 1, iso.ix(5.25f, y0 + 0.2f), iso.iy(5.25f, y0 + 0.2f, z) + d - 1, Col.scale(chalkY, 0.8f))
            pix(iso.ix(4.3f, y0 + 0.16f), iso.iy(4.3f, y0 + 0.16f, z + 0.5f) - d + 1, Col.mix(chalkW, slate, 0.08f))
        }
    }

    /** The sponge on the ledge, its pores showing. Tapped ([pokes]) it hops in a puff of chalk dust; the second time twice. */
    private fun sponge() {
        val x = 3.15f
        val pk = poked("sponge"); val a = pk?.age(t) ?: 0f
        val hops = if (pk == null) 0 else pk.step + 1
        val lift = if (a < hops * 0.45f) abs(sin(a * PI.toFloat() / 0.45f)) * 2.5f else 0f
        val z0 = bz0 + floor(lift)
        iso.box(x, y0 + 0.02f, z0, 0.6f, 0.3f, 1.1f, Pal.HAY_L, Pal.HAY_M, Pal.HAY_D)
        if (pk != null) s.fx {
            val cx = iso.sx(x + 0.3f, y0 + 0.2f); val cy = iso.sy(x + 0.3f, y0 + 0.2f, bz0 + 0.8f)
            for (h in 0 until hops) PokeArt.puff(c, cx, cy, (a - h * 0.45f - 0.2f) / 1.1f, (4f + 2f * h) * K, 7, chalkW, 61 + h, 0.8f)
        }
        if (detail == 1) {
            for (j in 0..2) c.set(iso.ix(x + 0.12f + j * 0.18f, y0 + 0.33f), iso.iy(x + 0.12f + j * 0.18f, y0 + 0.33f, z0 + 0.8f), Pal.HAY_D)
            return
        }
        // closer up, pores all over its face and top, a streak of chalk dust on it
        for (k in 0 until 26) {
            val px = x + 0.04f + Noise.rnd(k, 91) * 0.52f
            if (k < 18) {
                val z = z0 + 0.1f + Noise.rnd(k, 92) * 0.9f
                c.set(iso.ix(px, y0 + 0.32f), iso.iy(px, y0 + 0.32f, z), if (k % 5 == 0) Pal.HAY_L else Pal.HAY_D)
            } else {
                val py = y0 + 0.05f + Noise.rnd(k, 93) * 0.24f
                c.set(iso.ix(px, py), iso.iy(px, py, z0 + 1.1f), Pal.HAY_M)
            }
        }
        c.line(iso.ix(x + 0.08f, y0 + 0.32f), iso.iy(x + 0.08f, y0 + 0.32f, z0 + 0.25f), iso.ix(x + 0.5f, y0 + 0.32f), iso.iy(x + 0.5f, y0 + 0.32f, z0 + 0.25f), Col.mix(Pal.HAY_M, chalkW, 0.5f))
    }

    // ------------------------------------------------------------------ the map and the clock

    /** The school map of Slovenia rolled down from its rod: the land, the Alps, Ljubljana, the Sava, the sea in the corner. */
    private fun map() {
        val xm = x0 + 0.03f
        // the cord to the nail and the two rods
        val nx = iso.ix(xm, (ma + mb) / 2f); val ny = iso.iy(xm, (ma + mb) / 2f, mz1 + 2.2f)
        c.line(iso.ix(xm, ma + 0.2f), iso.iy(xm, ma + 0.2f, mz1 + 0.6f), nx, ny, Pal.WOOD_X)
        c.line(iso.ix(xm, mb - 0.2f), iso.iy(xm, mb - 0.2f, mz1 + 0.6f), nx, ny, Pal.WOOD_X)
        pix(nx, ny, Pal.STONE_D)
        val land = Col.hex(0x8CC25A); val landD = Col.hex(0x5C9A48); val sea = Col.hex(0x7FB8E0)
        val paper = Col.hex(0xF0E8D0); val paperD = Col.hex(0xD8CCA8)
        quadFill(xm, ma, mz0, xm, mb, mz0, xm, mb, mz1, xm, ma, mz1) { px, py ->
            val y = wallXy(px, xm); val z = wallXz(px, py, xm)
            val u = (mb - y) / (mb - ma); val v = (mz1 - z) / (mz1 - mz0)
            if (detail > 1) return@quadFill mapFine(u, v, paper, paperD)
            val col = ((u - 0.05f) / 0.9f * 30f).toInt(); val row = ((v - 0.14f) / 0.78f * 16f).toInt()
            val inLand = row in 0..15 && col in 0..29 && SLOVENIA[row][col] == '#'
            when {
                v < 0.1f -> Col.hex(0xB83A2E)                                   // the title band
                u > 0.97f || v > 0.96f -> paperD
                inLand -> {
                    val edge = row == 0 || row == 15 || col == 0 || col == 29 || SLOVENIA[row - 1][col] != '#' || SLOVENIA[row + 1][col] != '#' || SLOVENIA[row][col - 1] != '#' || SLOVENIA[row][col + 1] != '#'
                    when {
                        edge -> landD
                        col == 13 && row == 8 -> Pal.FLAG_RED                    // Ljubljana
                        col < 10 && row < 6 -> if ((col + row) % 3 == 0) Col.hex(0xF4F4F0) else Col.hex(0xB8A888) // the Alps
                        abs(row - 5 - (col - 6) * 0.3f) < 0.5f && col in 7..24 -> sea // the Sava
                        else -> land
                    }
                }
                u < 0.2f && v > 0.72f -> sea                                     // the Adriatic
                else -> paper
            }
        }
        for (z in floatArrayOf(mz1 + 0.4f, mz0 - 0.2f)) {
            rod(xm + 0.02f, ma - 0.15f, z, xm + 0.02f, mb + 0.15f, z, Pal.WOOD_D)
            rod(xm + 0.02f, ma - 0.15f, z + 0.5f, xm + 0.02f, mb + 0.15f, z + 0.5f, Pal.WOOD_L)
            if (detail > 1) for (ey in floatArrayOf(ma - 0.15f, mb + 0.15f)) {
                // closer up, turned knobs on the rods' ends
                val kx = iso.ix(xm + 0.02f, ey); val ky = iso.iy(xm + 0.02f, ey, z + 0.5f)
                c.fillEllipse(kx + 0.5f, ky + detail * 0.5f, detail * 0.9f, detail * 1.1f, Pal.WOOD_D)
                c.set(kx, ky, Pal.WOOD_L)
            }
        }
    }

    /**
     * The map closer up at [u] across and [v] down its paper: Slovenia's outline smoothed out of [SLOVENIA] with a
     * fine border, the mountains in relief, the Sava, the Drava and the Soča, the lake at Bled, the towns (Ljubljana
     * as the capital), the sea with its ripples, a grid of degrees on the paper, and the title in the red band.
     */
    private fun mapFine(u: Float, v: Float, paper: Int, paperD: Int): Int {
        val land = Col.hex(0x8CC25A); val landD = Col.hex(0x4E8A40); val sea = Col.hex(0x7FB8E0)
        // a picture pixel, in the map's cells (30 × 16 over the paper)
        val pu = 1f / ((mb - ma) * 4f * K); val pv = 1f / ((mz1 - mz0) * K)
        val dc = pu / 0.9f * 30f; val dr = pv / 0.78f * 16f
        if (v < 0.1f) return mapTitle(u, v)
        if (u > 0.97f || v > 0.96f) return paperD
        val cf = (u - 0.05f) / 0.9f * 30f; val rf = (v - 0.14f) / 0.78f * 16f
        if (landness(cf, rf) > 0.5f) {
            val edge = landness(cf - dc, rf) <= 0.5f || landness(cf + dc, rf) <= 0.5f || landness(cf, rf - dr) <= 0.5f || landness(cf, rf + dr) <= 0.5f
            if (edge) return landD
            // the capital: a red dot in a white ring; the other towns small dark dots
            val lj = hypot((cf - 13.5f) / dc, (rf - 8.5f) / dr)
            if (lj < 1.2f) return Pal.FLAG_RED
            if (lj < 2f) return Col.hex(0xFFFFFF)
            for (k in TOWNS.indices step 2) if (hypot((cf - TOWNS[k]) / dc, (rf - TOWNS[k + 1]) / dr) < 0.9f) return Col.hex(0x3A2A24)
            // the rivers, a picture pixel thin, and the lake
            val river = Col.hex(0x4E8EC8)
            if (cf in 5.5f..25.5f && abs(rf - (4.2f + (cf - 5f) * 0.32f + sin(cf * 0.8f) * 0.35f)) < dr * 0.6f) return river // the Sava
            if (cf in 15.5f..27.5f && abs(rf - (2.2f + (cf - 16f) * 0.3f + sin(cf * 1.1f) * 0.25f)) < dr * 0.6f) return river // the Drava
            if (rf in 4.5f..10.8f && abs(cf - (5.6f - (rf - 4.5f) * 0.38f + sin(rf * 1.3f) * 0.3f)) < dc * 0.6f) return river // the Soča
            if (hypot((cf - 7f) / 0.5f, (rf - 4.3f) / 0.35f) < 1f) return river // Bled
            // relief: the Alps in the north-west, Pohorje in the north-east, lit from the upper left
            val e = elevation(cf, rf)
            val lit = e - elevation(cf - 0.4f, rf - 0.4f)
            return when {
                e > 0.74f -> if (lit > 0.02f) Col.hex(0xC8C4B8) else Col.hex(0xF4F4F0)
                e > 0.5f -> if (lit > 0.02f) Col.hex(0x9A8A6A) else Col.hex(0xB8A888)
                e > 0.3f -> Col.mix(land, Col.hex(0xB8A888), if (lit > 0.02f) 0.6f else 0.35f)
                Noise.v2(cf * 0.7f, rf * 0.7f, 35) > 0.66f -> Col.mix(land, Col.hex(0xC8E080), 0.4f)
                else -> land
            }
        }
        // the Adriatic in the corner, rippled
        val coast = hypot(max(0f, u - 0.12f) / 0.09f, max(0f, 0.8f - v) / 0.09f)
        if (u < 0.21f && v > 0.71f && coast < 1f) {
            val ripple = Math.floorMod((v / pv).toInt(), 3) == 0 && Math.floorMod((u / pu).toInt() + (v / pv).toInt() / 3 * 2, 5) < 2
            return if (ripple) Col.mix(sea, Col.hex(0xFFFFFF), 0.35f) else sea
        }
        // the paper: the map's neat line and a grid of degrees
        val neat = (abs(u - 0.035f) < pu * 0.6f || abs(u - 0.955f) < pu * 0.6f) && v in 0.12f..0.94f ||
            (abs(v - 0.12f) < pv * 0.6f || abs(v - 0.94f) < pv * 0.6f) && u in 0.035f..0.955f
        if (neat) return Col.mix(paperD, Col.hex(0x8A7A5A), 0.3f)
        val grid = abs(cf / 7.5f - floor(cf / 7.5f + 0.5f)) * 7.5f < dc * 0.5f || abs(rf / 4f - floor(rf / 4f + 0.5f)) * 4f < dr * 0.5f
        return if (grid && u in 0.035f..0.955f && v in 0.12f..0.94f) Col.mix(paper, paperD, 0.55f) else paper
    }

    /** How much the map's cell at ([cf], [rf]) is land, the outline's cells blended smoothly, its edge a little ragged. */
    private fun landness(cf: Float, rf: Float): Float {
        val x = cf - 0.5f; val y = rf - 0.5f
        val ix = floor(x).toInt(); val iy = floor(y).toInt(); val fx = x - ix; val fy = y - iy
        fun at(cl: Int, rw: Int) = if (rw in 0..15 && cl in 0..29 && SLOVENIA[rw][cl] == '#') 1f else 0f
        val l = (at(ix, iy) * (1 - fx) + at(ix + 1, iy) * fx) * (1 - fy) + (at(ix, iy + 1) * (1 - fx) + at(ix + 1, iy + 1) * fx) * fy
        return l + (Noise.v2(cf * 1.3f, rf * 1.3f, 5) - 0.5f) * 0.3f
    }

    /** The height of the land on the map at ([cf], [rf]): the Alps high in the north-west, Pohorje's lower hills in the north-east. */
    private fun elevation(cf: Float, rf: Float): Float {
        val alps = 1.05f - hypot((cf - 6f) / 5.5f, (rf - 4.2f) / 2.4f)
        val pohorje = 0.62f - hypot((cf - 20f) / 3f, (rf - 4f) / 1.4f) * 0.62f
        return max(alps, pohorje) + (Noise.v2(cf * 0.9f, rf * 0.9f, 33) - 0.5f) * 0.4f
    }

    /** The map's red title band closer up: SLOVENIJA in white capitals when there's room for them (detail 3), else a hint of them. */
    private fun mapTitle(u: Float, v: Float): Int {
        val red = Col.hex(0xB83A2E)
        val xp = u * (mb - ma) * 4f * K; val yp = v * (mz1 - mz0) * K
        val ink = Col.mix(red, Col.hex(0xF4EEDD), 0.85f)
        if (detail < 3) {
            // too low for letters: their row along the middle of the band, broken into strokes
            val span = (mb - ma) * 4f * K
            val inText = abs(xp - span / 2f) < span * 0.3f && abs(yp - 0.05f * (mz1 - mz0) * K) < 0.7f
            return if (inText && Math.floorMod(floor(xp).toInt(), 3) != 2) Col.mix(red, ink, 0.55f) else red
        }
        val w = TITLE.sumOf { it[0].length + 1 } - 1
        val gx0 = floor(xp - ((mb - ma) * 4f * K - w) / 2f).toInt(); val gy = floor(yp - 0.3f).toInt()
        if (gy !in 0..4 || gx0 < 0) return red
        var x = gx0
        for (g in TITLE) {
            val gw = g[0].length
            if (x < gw) return if (g[gy][x] == '#') ink else red
            x -= gw + 1
            if (x < 0) return red
        }
        return red
    }

    /** The school clock over the map: a wooden rim, the hours, the hands at the hour of the day, the red second hand ticking. */
    private fun clock() {
        val xc = x0 + 0.05f
        val r = clockR / CELL
        quadFill(xc, clockY - r, clockZ - clockR, xc, clockY + r, clockZ - clockR, xc, clockY + r, clockZ + clockR, xc, clockY - r, clockZ + clockR) { px, py ->
            val y = wallXy(px, xc); val z = wallXz(px, py, xc)
            val d = hypot((y - clockY) * CELL, z - clockZ)
            when {
                d > clockR -> Col.scale(plasterM, 0.86f)
                // closer up the rim is turned: a bright bead on its lit side, a groove next to the dial
                detail > 1 && d > clockR - 0.3f && z > clockZ + 0.4f -> Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.3f)
                detail > 1 && d in clockR - 0.7f..clockR - 0.7f + 0.8f / K -> Col.scale(Pal.WOOD_D, 0.8f)
                d > clockR - 0.7f -> if (z > clockZ) Pal.WOOD_L else Pal.WOOD_D
                else -> Col.hex(0xF7F5EE)
            }
        }
        // the hour marks and the hands
        fun at(a: Double, len: Float): Pair<Int, Int> {
            val y = clockY - (sin(a) * len / CELL).toFloat(); val z = clockZ + (cos(a) * len).toFloat()
            return iso.ix(xc, y) to iso.iy(xc, y, z)
        }
        val (cx, cy) = at(0.0, 0f)
        val hr = (s.hour % 12f) / 12.0 * 2 * PI; val mn = (s.hour % 1f) * 2 * PI
        val sec = ((t * 0.5) % 1.0) * 2 * PI
        if (detail == 1) {
            for (k in 0 until 4) { val (hx, hy) = at(k * PI / 2, clockR - 1.2f); c.set(hx, hy, Pal.OUTLINE) }
            val (hx, hy) = at(hr, clockR * 0.45f); c.line(cx, cy, hx, hy, Pal.OUTLINE)
            val (mx, my) = at(mn, clockR * 0.72f); c.line(cx, cy, mx, my, Pal.OUTLINE)
            val (sx, sy) = at(sec, clockR * 0.7f); c.line(cx, cy, sx, sy, Pal.FLAG_RED)
            c.set(cx, cy, Pal.GOLD)
            return
        }
        // closer up: twelve marks, the quarters longer; a heavier hour hand; the second hand with its tail; a brass cap
        for (k in 0 until 12) {
            val (ax, ay) = at(k * PI / 6, clockR - 1.05f)
            if (k % 3 == 0) { val (bx, by) = at(k * PI / 6, clockR - 1.6f); c.line(ax, ay, bx, by, Pal.OUTLINE) } else c.set(ax, ay, Col.hex(0x6A6058))
        }
        val (hx, hy) = at(hr, clockR * 0.45f); thick(cx, cy, hx, hy, Pal.OUTLINE, detail - 1)
        val (mx, my) = at(mn, clockR * 0.72f); c.line(cx, cy, mx, my, Pal.OUTLINE)
        val (sx, sy) = at(sec, clockR * 0.7f); val (tx, ty) = at(sec + PI, clockR * 0.18f)
        c.line(tx, ty, sx, sy, Pal.FLAG_RED)
        c.fillCircle(cx + 0.5f, cy + 0.5f, detail * 0.5f, Pal.GOLD)
    }

    // ------------------------------------------------------------------ the teacher's corner

    /** The teacher's desk: a walnut top over a front panel, a pedestal of three drawers with brass pulls. */
    private fun teacherDesk() {
        val top = fl + 6.6f
        val l = Col.hex(0x9A6A42); val m = Col.hex(0x7A4E2E); val d = Col.hex(0x5A3820)
        iso.box(tx0 + 0.1f, ty0 + 0.1f, fl, tx1 - tx0 - 0.2f, ty1 - ty0 - 0.2f, top - fl, l, m, d)
        // the front panel: a recessed field on the left, the drawers on the right
        val py = ty1 - 0.1f
        val split = tx1 - 1.0f
        iso.line(tx0 + 0.3f, py, fl + 1.5f, split - 0.2f, py, fl + 1.5f, d)
        iso.line(tx0 + 0.3f, py, top - 1f, split - 0.2f, py, top - 1f, Col.mix(m, l, 0.5f))
        iso.line(tx0 + 0.3f, py, fl + 1.5f, tx0 + 0.3f, py, top - 1f, Col.mix(m, l, 0.5f))
        iso.line(split - 0.2f, py, fl + 1.5f, split - 0.2f, py, top - 1f, d)
        iso.line(split, py, fl, split, py, top, d)
        for (j in 1..2) iso.line(split, py, fl + j * (top - fl) / 3f, tx1 - 0.1f, py, fl + j * (top - fl) / 3f, d)
        for (j in 0..2) {
            val kx = split + 0.45f; val kz = fl + (j + 0.6f) * (top - fl) / 3f
            iso.px(kx, py + 0.02f, kz, Pal.GOLD)
            if (detail > 1) {
                // closer up, round brass knobs: a gleam, a shade
                val bx = iso.ix(kx, py + 0.02f); val by = iso.iy(kx, py + 0.02f, kz)
                c.set(bx, by, Pal.GOLD_L); c.set(bx + K - 1, by + K - 1, Col.scale(Pal.GOLD, 0.7f)); c.set(bx + K - 1, by, Col.scale(Pal.GOLD, 0.85f))
            }
        }
        // the top, a little proud
        iso.box(tx0, ty0, top, tx1 - tx0, ty1 - ty0, 0.9f, Col.mix(l, Col.hex(0xFFFFFF), 0.1f), m, d)
        iso.line(tx0, ty1, top + 0.9f, tx1, ty1, top + 0.9f, Col.mix(l, Col.hex(0xFFFFFF), 0.3f))
        if (detail > 1) for (j in 0 until 7) {
            // closer up, the walnut's grain along the top
            val gy = ty0 + 0.1f + (ty1 - ty0 - 0.2f) * (j + Noise.rnd(j, 51) * 0.5f) / 7f
            val ga = tx0 + 0.05f + Noise.rnd(j, 52) * 0.6f; val gb = tx1 - 0.05f - Noise.rnd(j, 53) * 0.6f
            iso.line(ga, gy, top + 0.9f, gb, gy, top + 0.9f, Col.mix(l, m, 0.45f))
        }
        // an apple for the teacher
        val ax = iso.sx(tx0 + 0.25f, ty1 - 0.3f); val ay = iso.sy(tx0 + 0.25f, ty1 - 0.3f, top + 0.9f)
        if (detail == 1) {
            c.fillCircle(ax, ay - 2.2f, 2.3f, Pal.FLAG_RED); c.set(ax.toInt() - 1, (ay - 3.6f).toInt(), Col.hex(0xFF8A80))
            c.set(ax.toInt(), (ay - 5f).toInt(), Pal.WOOD_D); c.set(ax.toInt() + 1, (ay - 5f).toInt(), Pal.LEAF)
        } else apple(ax, ay - 2.2f * detail, 2.3f * detail)
    }

    /** The teacher's apple closer up, round around ([x], [y]) [r] px across: shaded to the lower right, a shine, the stalk and a leaf. */
    private fun apple(x: Float, y: Float, r: Float) {
        for (yy in floor(y - r).toInt()..ceil(y + r).toInt()) for (xx in floor(x - r).toInt()..ceil(x + r).toInt()) {
            val dx = (xx + 0.5f - x) / r; val dy = (yy + 0.5f - y) / r
            val dd = dx * dx + dy * dy
            if (dd > 1f) continue
            val lit = -dx * 0.6f - dy * 0.8f
            c.set(xx, yy, when {
                lit < -0.55f -> Col.hex(0x8E2A22)
                lit < -0.15f && Dither.at(xx, yy) < 0.5f -> Col.hex(0xB0352A)
                lit > 0.45f && dd > 0.1f -> Col.hex(0xE8584A)
                else -> Pal.FLAG_RED
            })
        }
        c.fillEllipse(x - r * 0.38f, y - r * 0.45f, r * 0.2f, r * 0.28f, Col.hex(0xFFB0A8))
        c.line(x.toInt(), (y - r * 0.8f).toInt(), (x + r * 0.2f).toInt(), (y - r * 1.3f).toInt(), Pal.WOOD_D)
        c.fillEllipse(x + r * 0.55f, y - r * 1.2f, r * 0.4f, r * 0.18f, Pal.LEAF)
        c.line((x + r * 0.25f).toInt(), (y - r * 1.15f).toInt(), (x + r * 0.85f).toInt(), (y - r * 1.25f).toInt(), Col.hex(0x3E7A34))
    }

    private val deskTop: Float get() = fl + 7.5f

    /** A stack of books on the desk, spines to the room, the pages' edges to the right. */
    private fun books(x: Float, y: Float) {
        val covers = intArrayOf(Pal.FLAG_RED, Col.hex(0x2E63B0), Col.hex(0x3E8A3A), Col.hex(0xD4972E))
        val page = Col.hex(0xF4EEDD)
        for (k in 0..3) {
            val z = deskTop + k * 1.1f
            val ox = if (k % 2 == 0) 0f else 0.06f; val oy = if (k % 2 == 0) 0f else -0.05f
            val w = if (k == 3) 0.55f else 0.65f
            iso.box(x + ox, y + oy, z, w, 0.5f, 1.1f, Col.scale(covers[k], 1.15f), covers[k], page)
            iso.line(x + ox + 0.1f, y + oy + 0.5f, z + 0.55f, x + ox + w - 0.1f, y + oy + 0.5f, z + 0.55f, Pal.GOLD_L)
            if (detail > 1) {
                // closer up: the leaves of the pages between the boards, the boards' edges, a second gilt line on the spine
                val xe = x + ox + w
                for (zz in floatArrayOf(0.35f, 0.55f, 0.75f)) iso.line(xe, y + oy + 0.04f, z + zz, xe, y + oy + 0.46f, z + zz, Col.hex(0xD8CCB0))
                iso.line(xe, y + oy, z + 0.12f, xe, y + oy + 0.5f, z + 0.12f, Col.scale(covers[k], 0.8f))
                iso.line(xe, y + oy, z + 1.05f, xe, y + oy + 0.5f, z + 1.05f, covers[k])
                iso.line(x + ox + 0.1f, y + oy + 0.5f, z + 0.85f, x + ox + w - 0.1f, y + oy + 0.5f, z + 0.85f, Col.mix(covers[k], Pal.GOLD_L, 0.6f))
            }
        }
    }

    /** An oil lamp: a brass foot and font, the glass chimney, the flame and its halo when lit. */
    private fun oilLamp(x: Float, y: Float, lit: Boolean) {
        val cx = iso.ix(x, y); val by = iso.iy(x, y, deskTop)
        val brass = Col.hex(0xC89A3A); val brassD = Col.hex(0x8A6420); val brassL = Col.hex(0xF0CC70)
        val d = detail
        c.fillRect(cx - 3 * d, by - 2 * d, 7 * d, 2 * d, brassD); c.fillRect(cx - 3 * d, by - 2 * d, 7 * d, d, brass)
        c.fillRect(cx - d, by - 5 * d, 3 * d, 3 * d, brass)
        c.fillEllipse(cx + 0.5f * d, by - 8f * d, 3.4f * d, 2.6f * d, brass)
        if (d == 1) { c.set(cx - 1, by - 9, brassL); c.set(cx + 2, by - 7, brassD) }
        else {
            // closer up the brass is round: the foot's lit edge, the stem and the font shaded to the right, a gleam on the font
            c.hline(cx - 3 * d, cx + 4 * d - 1, by - 2 * d, brassL)
            c.fillRect(cx + 2 * d - 1, by - 5 * d, 1, 3 * d, brassD)
            c.fillEllipse(cx + 1.3f * d, by - 7.6f * d, 2.2f * d, 1.9f * d, brassD)
            c.fillEllipse(cx + 0.2f * d, by - 8.3f * d, 2.7f * d, 2.1f * d, brass)
            c.fillEllipse(cx - 0.9f * d, by - 9f * d, 0.8f * d, 0.5f * d, brassL)
        }
        val g0 = by - 18 * d; val g1 = by - 11 * d
        val flick = sin(t * 9).toFloat() * 0.3f
        if (lit) glow {
            c.fillRect(cx - d, g0, 3 * d, g1 - g0 + d, Pal.FLAME[1])
            c.fillEllipse(cx + 0.5f * d, g1 - 2f * d, 1.2f * d, (2.4f + flick) * d, Pal.FLAME[0])
            if (d > 1) {
                // the flame's white heart over the wick
                c.fillEllipse(cx + 0.5f * d, g1 - 1.4f * d, 0.5f * d, (1.1f + flick * 0.5f) * d, Col.hex(0xFFFBEA))
            }
        } else {
            c.fillRect(cx - d, g0, 3 * d, g1 - g0 + d, Col.hex(0xB8C8D4))
            c.fillRect(cx - d + (if (d > 1) 1 else 0), g0 + d, 1, g1 - g0 - d, Col.hex(0xE8F0F4))
        }
        if (d > 1) {
            // the wick, the chimney's rim
            c.fillRect(cx, g1, d, 1, Pal.OUTLINE)
            c.hline(cx - d, cx + 2 * d - 1, g0, if (lit) Pal.FLAME[0] else Col.hex(0xE8F0F4))
        }
        c.fillRect(cx - 2 * d, g1 - 3 * d, d, 4 * d, Col.hex(0x9AAABA)); c.fillRect(cx + 2 * d, g1 - 3 * d, d, 4 * d, Col.hex(0x7A8A9A))
        if (d > 1) c.vline(cx - 2 * d, g1 - 3 * d + 1, g1 - 1, Col.hex(0xD8E4EC))
        if (lit) s.fx {
            c.penEmissive = true
            c.ditherCircle(cx + 0.5f * d, g1 - 3f * d, 9f * K, Pal.FLAME[1], 0.14f * env.windows)
            c.penEmissive = false
        }
    }

    /** The globe on its brass stand, turning slowly. */
    private fun globe(x: Float, y: Float) {
        val cx = iso.ix(x, y); val by = iso.iy(x, y, deskTop)
        val d = detail
        c.fillEllipse(cx + 0.5f * d, by - 1f * d, 4f * d, 1.6f * d, Pal.WOOD_D); c.fillRect(cx - 3 * d, by - 2 * d, 7 * d, d, Pal.WOOD_M)
        c.fillRect(cx - d, by - 5 * d, 3 * d, 3 * d, Pal.GOLD)
        if (d > 1) {
            // closer up: the foot's lit edge, the brass stem round
            c.hline(cx - 3 * d, cx + 4 * d - 1, by - 2 * d, Pal.WOOD_L)
            c.vline(cx - d, by - 5 * d, by - 2 * d - 1, Pal.GOLD_L); c.vline(cx + 2 * d - 1, by - 5 * d, by - 2 * d - 1, Col.scale(Pal.GOLD, 0.7f))
        }
        val r = 6f * d; val cy = by - 11.5f * d
        if (d == 1) for (a in 0 until 22) {
            val ang = -PI * 0.2 + a * PI * 1.25 / 21
            c.set((cx + 0.5 + cos(ang) * (r + 1.5)).toInt(), (cy + 0.5 - sin(ang) * (r + 1.5)).toInt(), if (a % 5 == 0) Pal.GOLD_L else Pal.GOLD)
        } else meridian(cx + 0.5f * d, cy + 0.5f * d, r + 1.1f * d, r + 1.9f * d)
        c.fillCircle(cx + 0.5f * d, cy + 0.5f * d, r, Pal.WATER_M)
        // tapped ([pokes]) it whirls round a couple of turns and slows to its own pace again; four the second time
        val whirl = poked("globe")?.let { pk -> val u = (pk.age(t) / (if (pk.step == 1) 3.4f else 2.4f)).coerceIn(0f, 1f); (if (pk.step == 1) 4f else 2f) * (1f - (1f - u) * (1f - u)) } ?: 0f
        val spin = ((t * 0.35 + whirl) % 1.0).toFloat()
        for (yy in max((cy - r).toInt(), c.top)..min((cy + r).toInt(), c.bottom - 1)) for (xx in max((cx - r).toInt(), c.left)..min((cx + r).toInt(), c.right - 1)) {
            val dx = (xx + 0.5f - cx - 0.5f * d) / r; val dy = (yy + 0.5f - cy - 0.5f * d) / r
            if (dx * dx + dy * dy > 1f) continue
            val lon = (asin(dx.coerceIn(-1f, 1f)) / PI.toFloat() + spin) * 6f
            val n = Noise.v2(lon, dy * 3f, 27)
            if (n > 0.56f) c.set(xx, yy, if (n > 0.7f) Col.hex(0x5C9A48) else Col.hex(0x8CC25A))
            if (dy < -0.82f || dy > 0.86f) c.set(xx, yy, Col.hex(0xF4F4F0))
            if (d > 1) {
                // closer up, the lines of the meridians and the equator, a picture pixel thin
                val across = 3f / (PI.toFloat() * r * sqrt(max(0.05f, 1f - dx * dx)))
                if (abs(lon - floor(lon + 0.5f)) < across || abs(dy) * r < 0.5f) c.set(xx, yy, Col.mix(c.get(xx, yy), Col.hex(0xFFFFFF), 0.3f))
            }
            // the shade on the right, a shine on the upper left
            if (dx + dy * 0.3f > 0.55f && Dither.at(xx, yy) < 0.6f) c.set(xx, yy, Col.scale(c.get(xx, yy), 0.75f))
        }
        if (d == 1) {
            c.set(cx - 2, (cy - 3).toInt(), Col.hex(0xCFE6F6)); c.set(cx - 3, (cy - 2).toInt(), Col.hex(0x9CC8E8))
        } else {
            c.fillEllipse(cx + 0.5f * d - r * 0.42f, cy + 0.5f * d - r * 0.45f, r * 0.2f, r * 0.14f, Col.hex(0x9CC8E8))
            c.fillEllipse(cx + 0.5f * d - r * 0.45f, cy + 0.5f * d - r * 0.48f, r * 0.1f, r * 0.07f, Col.hex(0xE8F4FC))
        }
        pix(cx, (cy - r - 2 * d).toInt(), Pal.GOLD); pix(cx + 5 * d, (cy + r).toInt(), Pal.GOLD)
    }

    /** The globe's brass meridian closer up: a ring from [r0] to [r1] around ([x], [y]) over the top, with its degrees marked. */
    private fun meridian(x: Float, y: Float, r0: Float, r1: Float) {
        for (yy in floor(y - r1).toInt()..ceil(y + r1).toInt()) for (xx in floor(x - r1).toInt()..ceil(x + r1).toInt()) {
            val dx = xx + 0.5f - x; val dy = y - (yy + 0.5f)
            val rr = hypot(dx, dy)
            if (rr < r0 || rr > r1) continue
            var a = kotlin.math.atan2(dy, dx)
            if (a < -0.95f * PI.toFloat()) a += 2f * PI.toFloat()
            if (a < -0.2f * PI.toFloat() || a > 1.05f * PI.toFloat()) continue
            val deg = a / (PI.toFloat() / 18f)
            c.set(xx, yy, when {
                abs(deg - floor(deg + 0.5f)) * rr * PI.toFloat() / 18f < 0.5f && rr > (r0 + r1) / 2f -> Col.scale(Pal.GOLD, 0.7f)
                rr < r0 + 1f && a in 0.3f..2.2f -> Pal.GOLD_L
                else -> Pal.GOLD
            })
        }
    }

    /** A wicker waste basket with a crumpled page on top. */
    private fun basket(x: Float, y: Float) {
        val cx = iso.ix(x, y); val by = iso.iy(x, y, fl)
        val l = Col.hex(0xC8A060); val m = Col.hex(0xA07A40); val d = Col.hex(0x7A5A2E)
        val k = detail
        val top = by - 9 * k
        for (yy in top until by) {
            val half = 4 - (yy - top) / k / 5
            for (xx in cx - half * k until cx + half * k + k) c.set(xx, yy, when {
                k == 1 -> if ((xx + yy) % 3 == 0) d else if (xx < cx) l else m
                else -> {
                    // closer up, wicker: rows of strands woven over and under the stakes, shaded round to the right
                    val band = (yy - top) / 2
                    val over = Math.floorMod((xx - cx) / 3 + band, 2) == 0
                    val col = if (over) (if ((yy - top) % 2 == 0) l else m) else (if ((yy - top) % 2 == 0) m else d)
                    if (xx > cx + half * k / 2) Col.scale(col, 0.86f) else col
                }
            })
        }
        c.fillEllipse(cx + 0.5f * k, by - 9f * k, 4.5f * k, 1.5f * k, Col.hex(0x5A4020))
        c.fillCircle(cx - 0.5f * k, by - 10f * k, 1.8f * k, Col.hex(0xF4F0E4))
        if (k == 1) c.set(cx, by - 10, Col.hex(0xC8C0B0))
        else for (j in 0 until 4) {
            // the crumpled page's creases
            val a = j * 1.7f + 0.4f
            c.line((cx - 0.5f * k).toInt(), by - 10 * k, (cx - 0.5f * k + cos(a) * 1.4f * k).toInt(), (by - 10f * k + sin(a) * 1.4f * k).toInt(), Col.hex(0xC8C0B0))
        }
        c.fillRect(cx - 4 * k, by - 8 * k, 9 * k, k, l)
        if (k > 1) c.hline(cx - 4 * k, cx + 5 * k - 1, by - 8 * k, Col.mix(l, Col.hex(0xFFFFFF), 0.25f))
    }

    // ------------------------------------------------------------------ the pupils' corner

    /** A chair; its back is toward -x ([facingX]) or -y. */
    private fun chair(cx: Float, cy: Float, facingX: Boolean) {
        val s2 = 0.36f
        val seat = fl + 3.8f; val back = seat + 5.2f
        val l = oakL; val m = oakM; val d = oakD
        // the back: two uprights and two rails
        if (facingX) {
            iso.post(cx - s2 + 0.05f, cy - s2 + 0.08f, fl, back, d)
            iso.post(cx - s2 + 0.05f, cy + s2 - 0.08f, fl, back, m)
            for (z in floatArrayOf(back - 0.4f, seat + 2.6f)) {
                iso.box(cx - s2, cy - s2 + 0.05f, z - 0.9f, 0.12f, 2 * s2 - 0.1f, 0.9f, l, m, d)
            }
        } else {
            iso.post(cx - s2 + 0.08f, cy - s2 + 0.05f, fl, back, d)
            iso.post(cx + s2 - 0.08f, cy - s2 + 0.05f, fl, back, m)
            for (z in floatArrayOf(back - 0.4f, seat + 2.6f)) {
                iso.box(cx - s2 + 0.05f, cy - s2, z - 0.9f, 2 * s2 - 0.1f, 0.12f, 0.9f, l, m, d)
            }
        }
        // the front legs and the seat
        iso.post(cx + s2 - 0.08f, cy + s2 - 0.08f, fl, seat, d)
        if (facingX) iso.post(cx + s2 - 0.08f, cy - s2 + 0.08f, fl, seat, Col.scale(d, 0.85f))
        else iso.post(cx - s2 + 0.08f, cy + s2 - 0.08f, fl, seat, Col.scale(d, 0.85f))
        iso.box(cx - s2, cy - s2, seat, 2 * s2, 2 * s2, 0.7f, l, m, d)
    }

    /** A pupil's desk facing +x: a lid with a pencil groove and an inkwell, the shelf under it, a front board, legs. */
    private fun pupilDesk(xa: Float, ya: Float, yb: Float, heart: Boolean) {
        val xb = xa + deskD
        val top = fl + 6.2f
        iso.post(xb - 0.1f, yb - 0.1f, fl, top, oakD)
        iso.post(xa + 0.1f, yb - 0.1f, fl, top, Col.scale(oakD, 0.85f))
        iso.post(xb - 0.1f, ya + 0.1f, fl, top, Col.scale(oakD, 0.9f))
        // the shelf box and the front board
        iso.box(xa + 0.08f, ya + 0.08f, fl + 3.4f, deskD - 0.16f, yb - ya - 0.16f, top - fl - 3.4f, oakM, oakM, Col.mix(oakM, oakD, 0.5f))
        iso.line(xb - 0.08f, ya + 0.08f, fl + 3.4f, xb - 0.08f, yb - 0.08f, fl + 3.4f, oakD)
        // the lid
        iso.box(xa - 0.06f, ya - 0.06f, top, deskD + 0.12f, yb - ya + 0.12f, 0.8f, oakL, oakM, oakD)
        if (detail > 1) for (j in 0 until 6) {
            // closer up, the oak's grain along the lid
            val gx = xa + 0.3f + (deskD - 0.35f) * (j + Noise.rnd(j, 61, if (heart) 1 else 2) * 0.6f) / 6f
            val g0 = ya + Noise.rnd(j, 62) * 0.5f; val g1 = yb - Noise.rnd(j, 63) * 0.5f
            iso.line(gx, g0, top + 0.8f, gx, g1, top + 0.8f, Col.mix(oakL, oakM, 0.4f))
        }
        iso.line(xa - 0.06f, yb + 0.06f, top + 0.8f, xb + 0.06f, yb + 0.06f, top + 0.8f, Col.mix(oakL, Col.hex(0xFFFFFF), 0.3f))
        iso.line(xa + 0.18f, ya + 0.1f, top + 0.8f, xa + 0.18f, yb - 0.1f, top + 0.8f, Col.mix(oakL, oakD, 0.55f))
        if (detail > 1) iso.line(xa + 0.21f, ya + 0.1f, top + 0.8f, xa + 0.21f, yb - 0.1f, top + 0.8f, Col.mix(oakL, Col.hex(0xFFFFFF), 0.25f))
        // the inkwell with its brass lid
        iso.px(xa + 0.12f, ya + 0.3f, top + 0.8f, Pal.OUTLINE); iso.px(xa + 0.12f, ya + 0.3f, top + 1.2f, Pal.GOLD)
        if (detail > 1) {
            // closer up, the lid's gleam and a glint in the glass
            val ix = iso.ix(xa + 0.12f, ya + 0.3f); val iy = iso.iy(xa + 0.12f, ya + 0.3f, top + 1.2f)
            c.set(ix, iy, Pal.GOLD_L); c.set(ix + 1, iy, Pal.GOLD_L); c.set(ix + K - 1, iy + K - 1, Col.scale(Pal.GOLD, 0.7f))
            c.set(ix + 1, iso.iy(xa + 0.12f, ya + 0.3f, top + 0.8f) + K - 2, Col.hex(0x5A6A8A))
        }
        if (heart) {
            // a heart carved into the front board
            val hx = iso.ix(xb - 0.07f, (ya + yb) / 2f); val hy = iso.iy(xb - 0.07f, (ya + yb) / 2f, fl + 5.6f)
            if (detail == 1) {
                c.set(hx - 1, hy, Pal.WOOD_X); c.set(hx + 1, hy - 1, Pal.WOOD_X); c.set(hx, hy + 1, Pal.WOOD_X); c.set(hx + 1, hy + 1, Pal.WOOD_X); c.set(hx - 1, hy - 1, Pal.WOOD_X); c.set(hx, hy, Pal.WOOD_X)
            } else carvedHeart(hx + 0.5f * detail, hy + 0.5f * detail, 1.7f * detail)
        }
    }

    /**
     * Closer up, the heart carved in the desk's front board around ([x], [y]), [r] px from the middle to a lobe: its
     * groove a picture pixel wide, the wood inside it a little darker; sheared with the board, which runs down to the left.
     */
    private fun carvedHeart(x: Float, y: Float, r: Float) {
        for (yy in floor(y - 2f * r).toInt()..ceil(y + 2f * r).toInt()) for (xx in floor(x - 1.4f * r).toInt()..ceil(x + 1.4f * r).toInt()) {
            fun inside(px: Float, py: Float): Boolean {
                val u = (px - x) / r; val v = -((py - y) + (px - x) * 0.5f) / r + 0.15f
                val q = u * u + v * v - 1f
                return q * q * q - u * u * v * v * v <= 0f
            }
            val me = inside(xx + 0.5f, yy + 0.5f)
            if (!me) continue
            val rim = !inside(xx - 0.5f, yy + 0.5f) || !inside(xx + 1.5f, yy + 0.5f) || !inside(xx + 0.5f, yy - 0.5f) || !inside(xx + 0.5f, yy + 1.5f)
            c.set(xx, yy, if (rim) Pal.WOOD_X else Col.mix(oakM, Pal.WOOD_X, 0.35f))
        }
    }

    private val pupilTop: Float get() = fl + 7f

    /** An exercise book open on the desk: ruled lines, a few words in blue ink, a red margin. */
    private fun notebook(x: Float, y: Float) {
        val z = pupilTop + 0.05f
        val w = 0.78f; val d = 0.7f
        iso.top(x - 0.03f, y - 0.03f, z, w + 0.06f, d + 0.06f, Col.hex(0x3E6A9A))
        iso.top(x, y, z + 0.2f, w, d / 2f, Col.hex(0xF2EEE2))
        iso.top(x, y + d / 2f, z + 0.2f, w, d / 2f, Col.hex(0xFAF8F0))
        iso.line(x, y + d / 2f, z + 0.2f, x + w, y + d / 2f, z + 0.2f, Col.hex(0xC8C0B0))
        var lx = x + 0.16f
        while (lx < x + w - 0.08f) {
            iso.line(lx, y + 0.06f, z + 0.2f, lx, y + d / 2f - 0.06f, z + 0.2f, Col.hex(0x9CC8E8))
            iso.line(lx, y + d / 2f + 0.06f, z + 0.2f, lx, y + d - 0.06f, z + 0.2f, Col.hex(0x9CC8E8))
            lx += 0.16f
        }
        if (detail == 1) {
            iso.line(x + 0.16f, y + d / 2f + 0.08f, z + 0.2f, x + 0.16f, y + d - 0.2f, z + 0.2f, Col.hex(0x2E63B0))
            iso.line(x + 0.32f, y + d / 2f + 0.08f, z + 0.2f, x + 0.32f, y + d - 0.12f, z + 0.2f, Col.hex(0x2E63B0))
        } else {
            // closer up the lines are handwriting: words with gaps between them, a third line begun
            scribble(x + 0.16f, y + d / 2f + 0.08f, x + 0.16f, y + d - 0.14f, z + 0.2f, 1, Col.hex(0x2E63B0))
            scribble(x + 0.32f, y + d / 2f + 0.08f, x + 0.32f, y + d - 0.1f, z + 0.2f, 2, Col.hex(0x2E63B0))
            scribble(x + 0.48f, y + d / 2f + 0.08f, x + 0.48f, y + d - 0.26f, z + 0.2f, 3, Col.hex(0x2E63B0))
            // and the date at the top of the other page
            scribble(x + 0.16f, y + 0.12f, x + 0.16f, y + 0.24f, z + 0.2f, 4, Col.hex(0x2E63B0))
        }
        iso.line(x + 0.05f, y + d - 0.08f, z + 0.2f, x + w - 0.05f, y + d - 0.08f, z + 0.2f, Col.hex(0xE07A6A))
    }

    /** Closer up, a line of joined-up handwriting in [ink] from world ([xa], [ya]) to ([xb], [yb]) on a page at height [z]: letters' humps, a gap between words. */
    private fun scribble(xa: Float, ya: Float, xb: Float, yb: Float, z: Float, seed: Int, ink: Int) {
        val sx = iso.ix(xa, ya); val sy = iso.iy(xa, ya, z); val ex = iso.ix(xb, yb); val ey = iso.iy(xb, yb, z)
        val n = max(abs(ex - sx), abs(ey - sy))
        if (n <= 0) return
        var word = (2 + (Noise.rnd(seed, 1) * 4).toInt()) * detail
        var start = true
        var i = 0
        while (i <= n) {
            if (word <= 0) { i += detail; word = (2 + (Noise.rnd(seed, i) * 4).toInt()) * detail; start = true; continue }
            val px = sx + (ex - sx) * i / n; val py = sy + (ey - sy) * i / n
            c.set(px, py, ink)
            // a tall letter now and then, most often starting a word
            if ((start && Noise.rnd(seed, i, 3) < 0.6f) || Noise.rnd(seed, i, 4) < 0.12f) for (up in 1 until detail) c.set(px, py - up, ink)
            start = false
            i++; word--
        }
    }

    /** A yellow pencil lying on the desk, its tip sharpened, a pink eraser. */
    private fun pencil(x: Float, y: Float) {
        val z = pupilTop + 0.2f
        rod(x, y, z, x + 0.55f, y + 0.35f, z, Pal.GOLD)
        rod(x, y + 0.05f, z - 0.4f, x + 0.55f, y + 0.4f, z - 0.4f, Col.hex(0xC08A20))
        if (detail == 1) {
            iso.px(x + 0.62f, y + 0.4f, z - 0.3f, Col.hex(0xE0B888)); c.set(iso.ix(x + 0.7f, y + 0.46f), iso.iy(x + 0.7f, y + 0.46f, z - 0.3f), Pal.OUTLINE)
            c.set(iso.ix(x - 0.04f, y - 0.02f), iso.iy(x - 0.04f, y - 0.02f, z - 0.2f), Col.hex(0xF08AA0))
            return
        }
        // closer up: the sharpened tip a cone of bare wood to the lead's point, the lit facet along the top, the
        // tin ferrule and the eraser
        val d = detail
        val ax = iso.sx(x + 0.55f, y + 0.35f); val ay = iso.sy(x + 0.55f, y + 0.35f, z)
        val bx = iso.sx(x + 0.55f, y + 0.4f) + d; val by = iso.sy(x + 0.55f, y + 0.4f, z - 0.4f) + 0.5f
        val tx = iso.sx(x + 0.72f, y + 0.47f) + d * 0.5f; val ty = iso.sy(x + 0.72f, y + 0.47f, z - 0.3f) + d * 0.5f
        c.polyBegin(); c.polyAdd(ax, ay); c.polyAdd(ax + d, ay); c.polyAdd(bx, by); c.polyAdd(tx, ty); c.polyFill(Col.hex(0xE0B888))
        val lx = tx + ((ax + bx) / 2f - tx) * 0.35f; val ly = ty + ((ay + by) / 2f - ty) * 0.35f
        c.line(lx.toInt(), ly.toInt(), tx.toInt(), ty.toInt(), Pal.OUTLINE)
        rod(x + 0.02f, y, z + 0.1f, x + 0.53f, y + 0.33f, z + 0.1f, Col.hex(0xFFE58A), 1)
        pix(iso.ix(x - 0.04f, y - 0.02f), iso.iy(x - 0.04f, y - 0.02f, z - 0.2f), Col.hex(0xF08AA0))
        c.line(iso.ix(x + 0.03f, y + 0.01f), iso.iy(x + 0.03f, y + 0.01f, z + 0.05f), iso.ix(x + 0.03f, y + 0.06f), iso.iy(x + 0.03f, y + 0.06f, z - 0.45f) + d - 1, Col.hex(0xC8C8D0))
    }

    /** A satchel standing on the floor against the desk's front: blue canvas, a brown flap with two buckles, the strap up over the hook. */
    private fun bag(x: Float, y: Float) {
        val l = Col.hex(0x6F8FB0); val m = Col.hex(0x4E6E90); val d = Col.hex(0x36506E)
        val z0 = fl; val z1 = fl + 4.6f
        iso.box(x, y, z0, 0.3f, 0.95f, z1 - z0, l, m, d)
        // the flap over the front, the buckles
        val flap = Col.hex(0x8A5A34); val flapD = Col.hex(0x6A4228)
        iso.panelX(x + 0.31f, y, z1 - 2.6f, 0.95f, 2.6f, flap)
        iso.line(x + 0.31f, y, z1 - 2.6f, x + 0.31f, y + 0.95f, z1 - 2.6f, flapD)
        if (detail > 1) {
            // closer up: the flap's stitching and its lit edge, the buckles' prongs
            iso.line(x + 0.31f, y + 0.02f, z1 - 0.15f, x + 0.31f, y + 0.93f, z1 - 0.15f, Col.mix(flap, Col.hex(0xFFFFFF), 0.25f))
            var sy = y + 0.06f
            while (sy < y + 0.9f) { iso.line(x + 0.32f, sy, z1 - 2.3f, x + 0.32f, sy + 0.05f, z1 - 2.3f, Col.hex(0xD8B890)); sy += 0.1f }
        }
        for (by in floatArrayOf(0.25f, 0.7f)) {
            iso.px(x + 0.32f, y + by, z1 - 2.2f, Pal.GOLD)
            if (detail > 1) {
                val bx = iso.ix(x + 0.32f, y + by); val bz = iso.iy(x + 0.32f, y + by, z1 - 2.2f)
                c.fillRect(bx + 1, bz + 1, K - 2, K - 2, flapD); c.set(bx, bz, Pal.GOLD_L)
            }
        }
        // the strap up to the hook under the lid
        rod(x + 0.05f, y + 0.2f, z1, x, y + 0.45f, fl + 6.1f, flapD)
        rod(x + 0.05f, y + 0.75f, z1, x, y + 0.5f, fl + 6.1f, flapD)
        iso.px(x + 0.3f, y + 0.95f, z0 + 1f, Col.hex(0xF4EEDD))
    }

    /** A reading book open on the second desk, a ribbon hanging out. */
    private fun readingBook(x: Float, y: Float) {
        val z = pupilTop + 0.05f
        iso.top(x - 0.03f, y - 0.03f, z, 0.76f, 0.9f, Col.hex(0x7A2E22))
        iso.top(x, y, z + 0.3f, 0.7f, 0.42f, Col.hex(0xE8DDC2))
        iso.top(x, y + 0.42f, z + 0.3f, 0.7f, 0.42f, Col.hex(0xF6EEDA))
        if (detail == 1) for (j in 1..3) {
            val lx = x + j * 0.17f
            iso.line(lx, y + 0.07f, z + 0.3f, lx, y + 0.36f, z + 0.3f, Col.hex(0x8A7A6A))
            iso.line(lx, y + 0.48f, z + 0.3f, lx, y + 0.78f, z + 0.3f, Col.hex(0x8A7A6A))
        } else for (j in 1..7) {
            // closer up, twice as many lines of print, broken into words, a paragraph's short last line
            val lx = x + j * 0.085f
            for ((ya, yb) in listOf(y + 0.07f to y + 0.36f, y + 0.48f to y + 0.78f)) {
                var wy = ya
                val end = if (j == 4) ya + (yb - ya) * 0.45f else yb
                var k = 0
                while (wy < end) {
                    val we = min(end, wy + 0.05f + Noise.rnd(j, k, if (ya < y + 0.4f) 71 else 72) * 0.09f)
                    iso.line(lx, wy, z + 0.3f, lx, we, z + 0.3f, Col.hex(0x8A7A6A))
                    wy = we + 0.035f; k++
                }
            }
        }
        rod(x + 0.35f, y + 0.84f, z + 0.3f, x + 0.4f, y + 0.95f, z - 1.2f, Pal.FLAG_RED)
    }

    // ------------------------------------------------------------------ the schoolyard

    private fun yard() {
        // the flag by the school's corner, the bicycle on its stand
        prop { flagpole() }
        shade(iso.sx(bikeX, (bikeY0 + bikeY1) / 2f), iso.sy(bikeX, (bikeY0 + bikeY1) / 2f, 0f), 7f * K, 2.2f * K, 0.8f, groundId)
        prop { bicycle() }
        // the linden in the corner with a bench beside it, a snowman on the hopscotch in winter
        shade(iso.sx(treeX + 0.3f, treeY), iso.sy(treeX + 0.3f, treeY, 0f), 7f * K, 2.4f * K, 0.8f, groundId)
        prop(Pal.OUTLINE_TREE) { s.trees.broadleaf(iso.ix(treeX, treeY), iso.iy(treeX, treeY, 0f), 1.7f, 23) }
        prop { bench(1.3f, 9.9f) }
        if (winter) prop { snowman((hopX0 + hopX1) / 2f, hopY) }
        // the flower bed at the front corner; a sled there in winter
        if (winter) prop { sled(bedX - 0.4f, bedY) } else prop { flowerBed(bedX, bedY) }
    }

    /** The flagpole at the corner with the Slovenian flag waving: white, blue and red, the arms near the pole. */
    private fun flagpole() {
        val top = 31f
        post(poleX, poleY, 0f, top, Col.hex(0xD8D8DC), Col.hex(0xA8A8B0), Col.hex(0x6A6A74))
        iso.px(poleX, poleY, top + 0.8f, Pal.GOLD)
        val len = 2.1f; val fh = 7.5f; val z0 = top - 1f - fh
        val stripe = intArrayOf(Pal.FLAG_RED, Pal.FLAG_BLUE, Pal.FLAG_WHITE)
        val y = poleY
        quadScan(poleX + 0.1f, y, z0 - 1f, poleX + 0.1f + len, y, z0 - 1f, poleX + 0.1f + len, y, z0 + fh + 1f, poleX + 0.1f, y, z0 + fh + 1f) { px, py ->
            val x = wallYx(px, y); val u = (x - poleX - 0.1f) / len
            val wave = sin(t * 4 - u * 7).toFloat() * 0.7f * u
            val v = (wallYz(px, py, y) - z0 - wave) / fh
            if (v in 0f..1f) {
                var col = stripe[(v * 3f).toInt().coerceIn(0, 2)]
                // the arms by the hoist, over the white and the blue: a blue shield edged in red, the white peaks of Triglav
                val au = (u - 0.14f) / 0.2f; val av = (v - 0.4f) / 0.5f
                if (au in 0f..1f && av in 0f..1f && abs(au - 0.5f) < 0.5f - max(0f, 0.35f - av) * 1.2f) {
                    val inner = au in 0.2f..0.8f && av > 0.12f && abs(au - 0.5f) < 0.3f - max(0f, 0.4f - av) * 1.2f
                    col = when {
                        !inner -> Pal.FLAG_RED
                        av > 0.35f && av < 0.75f && abs(au - 0.5f) < (0.75f - av) * 0.8f -> Pal.FLAG_WHITE
                        av > 0.85f -> Pal.GOLD
                        else -> Pal.FLAG_BLUE
                    }
                }
                if (sin(t * 4 - u * 7) > 0.6f) col = Col.scale(col, 0.85f)
                c.set(px, py, col)
            }
        }
    }

    /** Fills the pixels of a quad that [plot] chooses to draw. */
    private inline fun quadScan(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, x3: Float, y3: Float, z3: Float, x4: Float, y4: Float, z4: Float, crossinline plot: (Int, Int) -> Unit) {
        c.polyBegin()
        c.polyAdd(iso.sx(x1, y1), iso.sy(x1, y1, z1)); c.polyAdd(iso.sx(x2, y2), iso.sy(x2, y2, z2))
        c.polyAdd(iso.sx(x3, y3), iso.sy(x3, y3, z3)); c.polyAdd(iso.sx(x4, y4), iso.sy(x4, y4, z4))
        c.polyScan(plot)
    }

    /** A wooden sled with red runners, left in the snow. */
    private fun sled(x: Float, y: Float) {
        val run = Col.hex(0xB83A2E); val runD = Col.hex(0x7A2A22)
        for (ry in floatArrayOf(0.05f, 0.5f)) {
            rod(x, y + ry, 0.4f, x + 1.3f, y + ry, 0.4f, runD)
            rod(x + 1.3f, y + ry, 0.4f, x + 1.5f, y + ry, 1.6f, run)
            rod(x + 1.5f, y + ry, 1.6f, x + 1.35f, y + ry, 2.4f, run)
            for (lx in floatArrayOf(0.25f, 1.0f)) rod(x + lx, y + ry, 0.4f, x + lx, y + ry, 2f, Pal.WOOD_D)
        }
        iso.box(x + 0.05f, y, 2f, 1.2f, 0.55f, 0.6f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        for (j in 1..3) iso.line(x + j * 0.3f, y, 2.6f, x + j * 0.3f, y + 0.55f, 2.6f, Pal.WOOD_M)
        iso.top(x + 0.2f, y + 0.05f, 2.7f, 0.7f, 0.45f, Pal.SNOW_L)
    }

    /** A plain wooden bench along x, its back toward the school. */
    private fun bench(x: Float, y: Float) {
        val len = 1.9f
        iso.post(x + 0.15f, y + 0.25f, 0f, 3.2f, Pal.WOOD_D)
        iso.post(x + len - 0.15f, y + 0.25f, 0f, 3.2f, Pal.WOOD_D)
        iso.post(x + 0.15f, y - 0.05f, 0f, 7.2f, Pal.WOOD_M)
        iso.post(x + len - 0.15f, y - 0.05f, 0f, 7.2f, Pal.WOOD_M)
        iso.box(x, y - 0.12f, 5.2f, len, 0.1f, 1.8f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        iso.box(x, y - 0.05f, 3.2f, len, 0.45f, 0.8f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        iso.line(x, y + 0.4f, 4f, x + len, y + 0.4f, 4f, Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.25f))
        if (winter) iso.top(x + 0.05f, y, 4.05f, len - 0.1f, 0.35f, Pal.SNOW_L)
    }

    /** A bicycle standing along y: two spoked wheels, the red frame, the saddle, the handlebar with a bell, a basket. */
    private fun bicycle() {
        val x = bikeX
        val r = 2.7f
        val wa = bikeY0 + r / CELL; val wb = bikeY1 - r / CELL
        for (wy in floatArrayOf(wa, wb)) wheel(x, wy, r)
        val frame = Col.hex(0xC0392B); val frameD = Col.hex(0x8A2A22)
        val seatY = wa + (wb - wa) * 0.62f; val barY = wa + (wb - wa) * 0.08f
        val crankY = wa + (wb - wa) * 0.55f
        // the frame: seat tube, top tube, down tube, the stays, the fork
        fun ln(ya: Float, za: Float, yb: Float, zb: Float, col: Int) = rod(x, ya, za, x, yb, zb, col)
        if (detail > 1) {
            // closer up, the chain from the chainring to the back hub, under the frame
            val cr = 0.75f
            iso.line(x, crankY, 1.6f + cr, x, wb, r + 0.4f, Col.hex(0x4A4A52))
            iso.line(x, crankY, 1.6f - cr, x, wb, r - 0.4f, Col.hex(0x4A4A52))
        }
        ln(crankY, 1.6f, seatY, 7.2f, frame); ln(seatY, 6.6f, barY + 0.1f, 6.8f, frame)
        ln(crankY, 1.6f, barY + 0.1f, 6.2f, frame); ln(crankY, 1.6f, wb, r, frameD); ln(seatY, 6.6f, wb, r, frameD)
        ln(barY + 0.1f, 6.8f, wa, r, frame); ln(barY + 0.1f, 6.8f, barY, 8.6f, Pal.STONE_D)
        if (detail > 1) {
            // the tubes' shine along their tops
            val shine = Col.mix(frame, Col.hex(0xFFFFFF), 0.35f)
            iso.line(x, seatY, 6.6f + 0.3f, x, barY + 0.1f, 6.8f + 0.3f, shine)
            iso.line(x, crankY, 1.6f + 0.3f, x, barY + 0.1f, 6.2f + 0.3f, shine)
        }
        // the saddle, the handlebar and its bell, the pedal, a basket on the front
        rod(x - 0.12f, seatY + 0.05f, 7.6f, x + 0.12f, seatY - 0.1f, 7.6f, Pal.OUTLINE)
        rod(x, seatY + 0.2f, 7.4f, x, seatY - 0.2f, 7.8f, Col.hex(0x3A2A24))
        rod(x - 0.3f, barY, 8.6f, x + 0.3f, barY, 8.6f, Pal.STONE_D)
        iso.px(x + 0.3f, barY, 8.9f, Pal.STONE_L)
        iso.px(x, crankY, 1.6f, Pal.STONE_D)
        if (detail > 1) {
            // the chainring round the crank, the grips on the bar
            val cx = iso.sx(x, crankY) + K / 2f; val cy = iso.sy(x, crankY, 1.6f) + K / 2f
            for (k in 0 until 24) {
                val a = k * PI / 12
                c.set((cx + cos(a) * 0.35f * 4f * K).toInt(), (cy - sin(a) * 0.75f * K).toInt(), Col.hex(0x8A8A94))
            }
            rod(x - 0.3f, barY, 8.6f, x - 0.18f, barY, 8.6f, Col.hex(0x3A2A24))
        }
        iso.box(x - 0.2f, wa - 0.25f, 5.6f, 0.4f, 0.35f, 2f, Col.hex(0xD8B878), Col.hex(0xB89858), Col.hex(0x8A6A3A))
        if (detail > 1) for (j in 1..3) {
            // the basket's wicker
            iso.line(x - 0.2f, wa + 0.1f, 5.6f + j * 0.5f, x + 0.2f, wa + 0.1f, 5.6f + j * 0.5f, Col.hex(0x9A7A48))
        }
        if (!winter) iso.px(x, wa - 0.1f, 7.8f, if (env.season == Season.AUTUMN) Col.hex(0xE0902C) else Pal.GERANIUM)
        // the stand
        rod(x, crankY + 0.1f, 1.5f, x + 0.25f, crankY + 0.3f, 0f, Pal.STONE_D)
    }

    /** A wheel in the plane x = [x] around ([y], [r] up): the tyre, the spokes, the hub. */
    private fun wheel(x: Float, y: Float, r: Float) {
        val ry = r / CELL
        c.polyBegin()
        c.polyAdd(iso.sx(x, y - ry), iso.sy(x, y - ry, 0f)); c.polyAdd(iso.sx(x, y + ry), iso.sy(x, y + ry, 0f))
        c.polyAdd(iso.sx(x, y + ry), iso.sy(x, y + ry, 2 * r)); c.polyAdd(iso.sx(x, y - ry), iso.sy(x, y - ry, 2 * r))
        c.polyScan { px, py ->
            val yy = wallXy(px, x); val zz = wallXz(px, py, x)
            val dy = (yy - y) * CELL; val dz = zz - r
            val d = hypot(dy, dz)
            when {
                d > r -> {}
                // closer up, the tyre's tread along its outer edge
                d > r - 0.7f -> c.set(px, py, if (detail > 1 && d > r - 0.2f && Math.floorMod(px + py, 3) == 0) Col.hex(0x4A4A52) else Col.hex(0x2A2A30))
                d > r - 1.05f -> c.set(px, py, Pal.STONE_L)
                d < 0.5f -> c.set(px, py, Pal.STONE_D)
                detail == 1 -> {
                    val a = kotlin.math.atan2(dz, dy) / (PI.toFloat() / 4f)
                    if (abs(a - floor(a + 0.5f)) * d < 0.28f) c.set(px, py, Pal.STONE_M)
                }
                else -> {
                    // closer up, twice the spokes, a picture pixel thin
                    val a = kotlin.math.atan2(dz, dy) / (PI.toFloat() / 8f)
                    if (abs(a - floor(a + 0.5f)) * d * (PI.toFloat() / 8f) * K < 0.55f) c.set(px, py, Pal.STONE_M)
                }
            }
        }
    }

    /** A round bed of flowers in a ring of stones: tulips in spring, sunflowers in summer, asters in autumn, a mound of snow in winter. */
    private fun flowerBed(x: Float, y: Float) {
        val r = 0.75f
        for (i in 0 until 12) {
            val a = (i + 0.5f) / 12f * 2f * PI.toFloat()
            if (sin(a) + cos(a) > 0.3f) continue
            stone(x + cos(a) * r, y + sin(a) * r, 0.22f, 40 + i)
        }
        val cx = iso.sx(x, y); val cy = iso.sy(x, y, 0f)
        c.fillEllipse(cx, cy - K, r * 3.6f * K, r * 1.7f * K, if (winter) Pal.SNOW_M else Pal.SOIL_M)
        if (winter) c.fillEllipse(cx - K, cy - 2f * K, r * 2.4f * K, r * 1.1f * K, Pal.SNOW_L)
        else for (i in 0 until 5) {
            val a = i * 1.26f + 0.4f; val rr = if (i == 4) 0f else r * 0.5f
            val fx = x + cos(a) * rr; val fy = y + sin(a) * rr
            val bx = iso.ix(fx, fy); val by = iso.iy(fx, fy, 0f)
            if (detail > 1 && env.season == Season.SUMMER && i < 3) { sunflower(bx, by, 13 + i * 4); continue }
            // the small flowers closer up: the same, in whole pixels of the scene canvas
            sprite(bx, by) { when (env.season) {
                Season.SUMMER -> if (i < 3) {
                    // sunflowers: a ring of petals round a brown heart, the leaves on the stem
                    val hgt = 13 + i * 4
                    c.vline(bx, by - hgt, by, Pal.LEAF)
                    c.hline(bx - 2, bx - 1, by - hgt / 2, Col.hex(0x5AA84E)); c.hline(bx + 1, bx + 2, by - hgt / 2 - 3, Col.hex(0x5AA84E))
                    val hy = by - hgt - 2f
                    c.fillCircle(bx + 0.5f, hy, 3.1f, Col.hex(0xF0C23A))
                    c.set(bx - 2, hy.toInt() + 1, Col.hex(0xD49A1E)); c.set(bx + 2, hy.toInt() + 1, Col.hex(0xD49A1E))
                    c.fillRect(bx - 1, hy.toInt() - 1, 3, 2, Col.hex(0x6A4028)); c.set(bx, hy.toInt() - 1, Col.hex(0x8A5A34))
                } else {
                    // marigolds low in front
                    c.vline(bx, by - 3, by, Pal.LEAF)
                    c.fillRect(bx - 1, by - 5, 3, 2, Col.hex(0xF08A2A)); c.set(bx, by - 5, Col.hex(0xFFC04A))
                }
                Season.SPRING -> {
                    c.vline(bx, by - 5, by, Pal.LEAF); c.set(bx - 1, by - 2, Col.hex(0x5AA84E))
                    val petal = if (i % 3 == 0) Pal.GERANIUM else if (i % 3 == 1) Col.hex(0xF0C23A) else Col.hex(0xF4B8CC)
                    c.fillRect(bx - 1, by - 8, 3, 3, petal); c.set(bx, by - 8, Col.scale(petal, 0.8f))
                }
                else -> {
                    c.vline(bx, by - 4, by, Col.hex(0x6A7F37))
                    val petal = if (i % 2 == 0) Col.hex(0xB08AD8) else Col.hex(0x8A6AC0)
                    c.fillRect(bx - 1, by - 6, 3, 2, petal); c.set(bx, by - 6, Col.hex(0xF0C23A))
                }
            } }
        }
        for (i in 0 until 12) {
            val a = (i + 0.5f) / 12f * 2f * PI.toFloat()
            if (sin(a) + cos(a) <= 0.3f) continue
            stone(x + cos(a) * r, y + sin(a) * r, 0.22f, 40 + i)
        }
    }

    /**
     * A sunflower closer up, rooted at ([bx], [by]) and [hgt] px of the scene canvas tall: a slim stem with two leaves,
     * a ring of petals, lit from the upper left, round a disc of seeds.
     */
    private fun sunflower(bx: Int, by: Int, hgt: Int) {
        val d = detail
        val sx = bx + d / 2
        thick(sx, by + d - 1, sx, by - hgt * d, Pal.LEAF, d - 1)
        if (d > 2) c.vline(sx + d - 2, by - hgt * d + d, by + d - 1, Col.scale(Pal.LEAF, 0.75f))
        for ((dx, dy) in listOf(-1.4f to hgt / 2f, 1.4f to hgt / 2f + 3f)) {
            val lx = sx + dx * d; val ly = by - dy * d + d * 0.5f
            c.fillEllipse(lx, ly, 1.3f * d, 0.6f * d, Col.hex(0x5AA84E))
            c.fillEllipse(lx + 0.2f * d, ly + 0.25f * d, 1f * d, 0.35f * d, Col.hex(0x3E8A3A))
            c.line(sx, ly.toInt(), (lx + dx.sign * 1.1f * d).toInt(), (ly - 0.3f * d).toInt(), Col.hex(0x3E8A3A))
        }
        val hx = bx + 0.5f * d; val hy = by - (hgt + 2f) * d + 0.5f * d
        val petal = Col.hex(0xF0C23A); val petalD = Col.hex(0xD49A1E); val petalL = Col.hex(0xFFE27A)
        c.fillCircle(hx, hy, 2.2f * d, petal)
        for (k in 0 until 14) {
            val a = k * 2f * PI.toFloat() / 14f + 0.2f
            val px = hx + cos(a) * 2.4f * d; val py = hy - sin(a) * 2.4f * d
            val lit = cos(a) * -0.6f + sin(a) * 0.8f
            c.fillCircle(px, py, 0.75f * d, if (lit > 0.4f) petalL else if (lit < -0.4f) petalD else petal)
        }
        for (yy in floor(hy - 1.5f * d).toInt()..ceil(hy + 1.5f * d).toInt()) for (xx in floor(hx - 1.6f * d).toInt()..ceil(hx + 1.6f * d).toInt()) {
            val ex = (xx + 0.5f - hx) / (1.55f * d); val ey = (yy + 0.5f - hy) / (1.35f * d)
            if (ex * ex + ey * ey > 1f) continue
            c.set(xx, yy, if (Math.floorMod(xx + yy * 2, 3) == 0) Col.hex(0x8A5A34) else if (ex + ey > 0.6f) Col.hex(0x4A2A18) else Col.hex(0x6A4028))
        }
    }

    /** A snowman by the hopscotch: three balls, coal eyes, a carrot nose, a pot for a hat and a twig arm. */
    private fun snowman(x: Float, y: Float) {
        val cx = iso.sx(x, y); val by = iso.sy(x, y, 0f)
        val d = detail
        c.fillEllipse(cx, by - 5f * d, 7f * d, 5.5f * d, Pal.SNOW_M); c.fillEllipse(cx - 1f * d, by - 6f * d, 5.5f * d, 4f * d, Pal.SNOW_L)
        c.fillEllipse(cx, by - 13.5f * d, 5f * d, 4.2f * d, Pal.SNOW_M); c.fillEllipse(cx - 1f * d, by - 14.2f * d, 3.8f * d, 3f * d, Pal.SNOW_L)
        c.fillEllipse(cx, by - 20f * d, 3.6f * d, 3.2f * d, Pal.SNOW_M); c.fillEllipse(cx - 0.8f * d, by - 20.6f * d, 2.6f * d, 2.3f * d, Pal.SNOW_L)
        val ix = cx.toInt(); val iy = by.toInt()
        if (d == 1) {
            c.set(ix - 2, iy - 21, Pal.OUTLINE); c.set(ix + 1, iy - 21, Pal.OUTLINE)
            c.hline(ix - 1, ix - 4, iy - 20, Col.hex(0xE8782A))
            for (j in 0..2) c.set(ix, iy - 15 + j * 2, Pal.OUTLINE)
            c.fillRect(ix - 3, iy - 26, 6, 3, Col.hex(0x6A4E38)); c.hline(ix - 4, ix + 3, iy - 23, Col.hex(0x4A3526))
            c.line(ix + 4, iy - 14, ix + 9, iy - 18, Pal.WOOD_D); c.set(ix + 9, iy - 19, Pal.WOOD_D); c.set(ix + 8, iy - 19, Pal.WOOD_D)
            c.fillRect(ix - 4, iy - 18, 8, 2, Pal.FLAG_RED)
            return
        }
        // closer up: round coals with a glint, a tapering carrot, a pot with a lit side, a forked twig, a striped scarf
        for (ex in intArrayOf(-2, 1)) {
            c.fillCircle(ix + (ex + 0.5f) * d, iy + (-20.5f) * d, 0.6f * d, Pal.OUTLINE)
            c.set(ix + ex * d + d / 2 - 1, iy - 21 * d + d / 2 - 1, Col.hex(0x8A8A9A))
        }
        c.polyBegin()
        c.polyAdd(ix.toFloat(), iy - 20.2f * d); c.polyAdd(ix.toFloat(), iy - 18.9f * d); c.polyAdd(ix - 4f * d, iy - 19.6f * d)
        c.polyFill(Col.hex(0xE8782A))
        c.line(ix - 1, iy - 19 * d - 1, ix - 3 * d, iy - 19 * d - d / 2, Col.hex(0xB85A1E))
        for (j in 0..2) c.fillCircle(ix + 0.5f * d, iy + (-14.5f + j * 2) * d, 0.5f * d, Pal.OUTLINE)
        c.fillRect(ix - 3 * d, iy - 26 * d, 6 * d, 3 * d, Col.hex(0x6A4E38)); c.fillRect(ix - 4 * d, iy - 23 * d, 8 * d, d, Col.hex(0x4A3526))
        c.vline(ix - 3 * d, iy - 26 * d, iy - 23 * d - 1, Col.hex(0x8A6E54)); c.hline(ix - 3 * d, ix + 3 * d - 1, iy - 24 * d, Col.hex(0x5A4030))
        c.line(ix + 4 * d, iy - 14 * d, ix + 9 * d, iy - 18 * d, Pal.WOOD_D); c.line(ix + 4 * d, iy - 14 * d + 1, ix + 9 * d, iy - 18 * d + 1, Pal.WOOD_D)
        c.line(ix + 7 * d, iy - 16 * d, ix + 8 * d, iy - 19 * d, Pal.WOOD_D); c.line(ix + 9 * d, iy - 18 * d, ix + 10 * d, iy - 19 * d, Pal.WOOD_D)
        c.fillRect(ix - 4 * d, iy - 18 * d, 8 * d, 2 * d, Pal.FLAG_RED)
        c.fillRect(ix + d, iy - 16 * d, 2 * d, 3 * d, Pal.FLAG_RED)
        for (sx in ix - 4 * d until ix + 4 * d) if (Math.floorMod(sx - ix, 3) == 0) c.vline(sx, iy - 18 * d, iy - 16 * d - 1, Col.hex(0xF4EEDD))
        c.hline(ix + d, ix + 3 * d - 1, iy - 14 * d, Col.hex(0xF4EEDD))
    }

    companion object {
        /** Nominal px along a wall per cell, for round things drawn on a wall. */
        private const val CELL = 4.47f

        /** Mojca's bed in the school, a curtained alcove along the left wall ("alcove"). */
        private const val ALCOVE = "alcove"

        /** Her bed along the left wall under the map, along y, its head by the bookcase; the curtain's rod along its outer side. */
        private const val MB_A = 0.68f; private const val MB_B = 1.78f; private const val MB_C = 3.42f; private const val MB_D = 6.12f
        private const val ROD_X = 1.92f; private const val ROD_Z = 21.4f

        /** Her blanket: rose wool, a cream check. */
        private val ROSE_WOOL = intArrayOf(Col.hex(0xD49290), Col.hex(0xB26C6C), Col.hex(0x7A4446), Col.hex(0xF2E6CE))

        /** The alcove's curtain: a blue and white check of cotton, its folds, and the cord that ties it back. */
        private val CURTAIN_L = Col.hex(0xD8E2EE); private val CURTAIN_M = Col.hex(0x8EA8C8); private val CURTAIN_D = Col.hex(0x5E7A9E)
        private val CORD = Col.hex(0xB83A2E)

        private val BOOKS = intArrayOf(
            Col.hex(0xB83A2E), Col.hex(0x2E63B0), Col.hex(0x3E8A3A), Col.hex(0xD4972E), Col.hex(0x7A4E9A), Col.hex(0xE9E2D0), Col.hex(0x6A4028),
        )

        /** The digits 1 to 6 as chalk strokes, points in a cell 2 wide and 4 high (as [ChalkHand]'s letters). */
        private val DIGITS: Array<Array<FloatArray>> = arrayOf(
            arrayOf(floatArrayOf(0.2f, 1f, 1.2f, 0f, 1.2f, 4f)),
            arrayOf(floatArrayOf(0f, 0.8f, 0.6f, 0f, 1.5f, 0f, 2f, 0.8f, 1.6f, 1.8f, 0f, 4f, 2.1f, 4f)),
            arrayOf(floatArrayOf(0f, 0.3f, 1.4f, 0f, 2f, 0.9f, 1.3f, 1.9f, 2f, 2.9f, 1.4f, 4f, 0f, 3.7f), floatArrayOf(1.3f, 1.9f, 0.6f, 1.9f)),
            arrayOf(floatArrayOf(1.6f, 4f, 1.6f, 0f, 0f, 2.8f, 2.2f, 2.8f)),
            arrayOf(floatArrayOf(2f, 0f, 0.2f, 0f, 0f, 1.8f, 1.3f, 1.6f, 2f, 2.5f, 1.7f, 3.7f, 0.8f, 4f, 0f, 3.6f)),
            arrayOf(floatArrayOf(1.8f, 0.1f, 0.8f, 0.2f, 0.1f, 1.5f, 0f, 3f, 0.5f, 4f, 1.5f, 4f, 2f, 3.1f, 1.5f, 2.2f, 0.5f, 2.2f, 0f, 2.8f)),
        )

        /** Towns on the map closer up, as (column, row) of [SLOVENIA]: Maribor, Celje, Kranj, Koper, Novo mesto. */
        private val TOWNS = floatArrayOf(22.5f, 3.3f, 18.3f, 6.3f, 10.8f, 6.6f, 4.6f, 14.2f, 17.2f, 11.8f)

        /** The map's title in small capitals, five rows high. */
        private val TITLE = arrayOf(
            arrayOf("###", "#..", "###", "..#", "###"), // S
            arrayOf("#..", "#..", "#..", "#..", "###"), // L
            arrayOf("###", "#.#", "#.#", "#.#", "###"), // O
            arrayOf("#.#", "#.#", "#.#", "#.#", ".#."), // V
            arrayOf("###", "#..", "##.", "#..", "###"), // E
            arrayOf("#..#", "##.#", "#.##", "#..#", "#..#"), // N
            arrayOf("#", "#", "#", "#", "#"), // I
            arrayOf("..#", "..#", "..#", "#.#", "###"), // J
            arrayOf("###", "#.#", "###", "#.#", "#.#"), // A
        )

        /** A rough outline of Slovenia, 30 × 16, north up. */
        private val SLOVENIA = arrayOf(
            ".........................##...",
            ".......................#####..",
            ".............###############..",
            ".....########################.",
            "..###########################.",
            "..#########################...",
            ".######################.......",
            "..####################........",
            "..###################.........",
            ".####################.........",
            "..###################.........",
            "..##################..........",
            "...###############............",
            "....##############............",
            "...########.######............",
            "...##.........####............",
        )
    }
}

/**
 * A chalk hand for the blackboard: letters six rows high and one to four columns wide, one column apart, for the
 * words of the lesson. [write] hands each lit pixel to the caller, which can shear the lines along a wall.
 */
internal object ChalkFont {
    private val glyphs: Map<Char, Array<String>> = mapOf(
        'D' to arrayOf("###.", "#..#", "#..#", "#..#", "#..#", "###."),
        'K' to arrayOf("#...", "#..#", "#.#.", "##..", "#.#.", "#..#", "...."),
        'o' to arrayOf("....", "....", ".##.", "#..#", "#..#", ".##."),
        'b' to arrayOf("#...", "#...", "###.", "#..#", "#..#", "###."),
        'e' to arrayOf("....", "....", ".##.", "####", "#...", ".###"),
        'r' to arrayOf("...", "...", "#.#", "##.", "#..", "#.."),
        'd' to arrayOf("...#", "...#", ".###", "#..#", "#..#", ".###"),
        'a' to arrayOf("....", "....", ".###", "#..#", "#..#", ".###"),
        'n' to arrayOf("....", "....", "###.", "#..#", "#..#", "#..#"),
        'k' to arrayOf("#..", "#..", "#..", "#.#", "##.", "#.#", "..."),
        's' to arrayOf("...", "...", ".##", "#..", ".#.", "..#", "##."),
        'i' to arrayOf("#", ".", "#", "#", "#", "#"),
        '!' to arrayOf("#", "#", "#", "#", ".", "#"),
        '?' to arrayOf("##.", "..#", "..#", ".#.", ".#.", "...", ".#."),
        '.' to arrayOf(".", ".", ".", ".", ".", "#"),
        ' ' to arrayOf("..", "..", "..", "..", "..", ".."),
    )

    /**
     * Calls [plot] (the letter's column, the column in the letter, the row) for each chalk pixel of [text] and returns
     * its width. With [slant] each letter's right half sits a row lower, as on a wall seen at the iso angle; the few
     * letters that would lose their shape that way (seven rows high) are drawn slanted already. Unknown letters are skipped.
     */
    inline fun write(text: String, slant: Boolean, plot: (Int, Int, Int) -> Unit): Int {
        var x = 0
        for (ch in text) {
            val g = glyph(ch) ?: continue
            val drop = slant && g.size == 6
            for ((row, line) in g.withIndex()) for ((k, p) in line.withIndex()) if (p == '#') plot(x, k, if (drop && k >= 2) row + 1 else row)
            x += g[0].length + 1
        }
        return x - 1
    }

    fun glyph(ch: Char): Array<String>? = glyphs[ch]
}

/**
 * The same hand closer up: [ChalkFont]'s letters as pen strokes, for a board drawn finer. A stroke is a run of points
 * in [ChalkFont]'s cells (x across the letter's columns, y down its rows: 0 the top of a capital, 2 the top of a small
 * letter, 5 the line), joined by lines a picture pixel thin; a stroke of one point is a dot. Each letter keeps its
 * width, so the words stand where they stand on the scene's canvas.
 */
internal object ChalkHand {
    private fun p(vararg v: Float) = v

    /** An arc of the ellipse around ([cx], [cy]) from [a0] to [a1] degrees (counter-clockwise from the right). */
    private fun arc(cx: Float, cy: Float, rx: Float, ry: Float, a0: Float = 0f, a1: Float = 360f, n: Int = 16): FloatArray {
        val out = FloatArray((n + 1) * 2)
        for (i in 0..n) {
            val a = Math.toRadians((a0 + (a1 - a0) * i / n).toDouble())
            out[2 * i] = cx + rx * cos(a).toFloat(); out[2 * i + 1] = cy - ry * sin(a).toFloat()
        }
        return out
    }

    private val o = arc(1.5f, 3.5f, 1.5f, 1.5f)
    private val glyphs: Map<Char, Array<FloatArray>> = mapOf(
        'D' to arrayOf(p(0f, 0f, 0f, 5f), p(0f, 0f, 1.4f, 0f, 2.4f, 0.4f, 3f, 1.5f, 3f, 3.5f, 2.4f, 4.6f, 1.4f, 5f, 0f, 5f)),
        'K' to arrayOf(p(0f, 0f, 0f, 5f), p(3f, 0f, 0.1f, 2.9f), p(1.1f, 2.1f, 3f, 5f)),
        'o' to arrayOf(o),
        'b' to arrayOf(p(0f, 0f, 0f, 5f), o),
        'e' to arrayOf(p(0.1f, 3.3f, 2.9f, 3.3f), arc(1.5f, 3.5f, 1.5f, 1.5f, 8f, 290f, 14)),
        'r' to arrayOf(p(0f, 2f, 0f, 5f), p(0f, 3.3f, 0.5f, 2.5f, 1.2f, 2.05f, 2f, 2.1f)),
        'd' to arrayOf(p(3f, 0f, 3f, 5f), o),
        'a' to arrayOf(o, p(3f, 2f, 3f, 5f)),
        'n' to arrayOf(p(0f, 2f, 0f, 5f), p(0f, 3.2f, 0.5f, 2.4f, 1.3f, 2f, 2.2f, 2.1f, 2.8f, 2.6f, 3f, 3.4f, 3f, 5f)),
        'k' to arrayOf(p(0f, 0f, 0f, 5f), p(2f, 2f, 0.1f, 3.7f), p(0.8f, 3.1f, 2f, 5f)),
        's' to arrayOf(p(2f, 2.2f, 1.2f, 2f, 0.3f, 2.2f, 0f, 2.8f, 0.6f, 3.4f, 1.5f, 3.6f, 2f, 4.2f, 1.7f, 4.9f, 0.8f, 5f, 0f, 4.7f)),
        'i' to arrayOf(p(0f, 2.2f, 0f, 5f), p(0f, 0.6f)),
        '!' to arrayOf(p(0f, 0f, 0f, 3.3f), p(0f, 5f)),
        '?' to arrayOf(p(0f, 0.9f, 0.4f, 0.2f, 1.2f, 0f, 1.9f, 0.3f, 2f, 1.1f, 1.6f, 1.8f, 1f, 2.5f, 1f, 3.5f), p(1f, 5f)),
        '.' to arrayOf(p(0f, 5f)),
    )

    /** The strokes of [ch] (none for a space or a letter it doesn't know). */
    fun strokes(ch: Char): Array<FloatArray> = glyphs[ch] ?: emptyArray()
}
