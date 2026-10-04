package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.Calendar
import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.scene.Poke
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * V cerkvi: a small village church of Primorska as a diorama, its nave cut open on a plot of the churchyard. The far
 * wall (the right one) holds the altar on two steps of red and white tiles: the stone table under a linen cloth with lace,
 * the gilded tabernacle and four candles on it, the retable over it, red marble columns in gold round the painting of the
 * Virgin in blue, a golden sunburst on top; a vase of flowers on each side (lilies at Easter), the red sanctuary lamp on
 * its chain, and the Virgin on a pedestal to the right (in December and January the nativity scene, jaslice, instead).
 * Over the wall the vault's end, painted blue with golden stars. The left wall has a crucifix, two tall windows of
 * coloured glass that lay their colours on the floor by day, the holy-water font and the door under the organ loft,
 * the organ's pipes over its painted balustrade and the bell rope hanging past it to the floor; above the door a bell
 * gable of Karst stone with the bell in its arch. Pews in rows either side of a red runner. Outside: a cypress, a stone
 * bench, the gravel path and lavender. At Easter the baskets of the žegen stand before the altar.
 *
 * Closer up ([fine]) the extra pixels go into the lead of the windows, the stars' points, the gilding's glints, the
 * wood's grain, the lace, the organ's pipe mouths and the stones' joints.
 *
 * Tapped ([pokes]): the candles flicker in a draught, then one goes out and lights again; the rope sways and the bell
 * rings, twice the second time; the sparrow on the window sill hops and chirps, flutters up, then flies a round of the
 * nave and comes back.
 */
internal class ChurchPainter : DioramaPainter() {
    override val art = "church"
    override val pokes = listOf(
        Poke("candles", listOf(1.4, 2.6), rest = 2.0),
        Poke("rope", listOf(2.6, 3.6), rest = 2.5),
        Poke(SPARROW, listOf(1.6, 2.2, 4.8), rest = 3.0),
    )
    override val plotW = 12f
    override val plotD = 12f
    override val rise = 27f

    // the nave: the left wall's inner face at X0, the far (right) wall's at Y0, TH thick; the floor at FL over X0..X1 × Y0..Y1;
    // the walls WALL_H high, the far wall rising ARCH_H more in the vault's end (a round arch from X0 to X1)
    private val x0 = 1.2f; private val y0 = 1.0f; private val x1 = 9.6f; private val y1 = 10.4f
    private val th = 0.5f
    private val fl = 1f
    private val wallH = 30f
    private val archH = 8f
    private val xm = (x0 + x1) / 2f
    private val hw = (x1 - x0) / 2f

    // the left wall: the crucifix, two windows (A is the word), the font, the door, the loft over it with the organ
    private val crossY = 2.45f
    private val waA = 3.6f; private val wbA = 4.7f
    private val waB = 5.75f; private val wbB = 6.85f
    private val wz0 = fl + 10.5f; private val wz1 = fl + 23.5f
    private val fontY = 7.75f
    private val da = 8.1f; private val db = 9.5f; private val dTop = fl + 15.3f
    private val xl = 2.3f; private val yl = 7.6f; private val zl = fl + 18.8f
    private val ropeX = 2.0f; private val ropeY = 9.75f
    // the bell gable over the door
    private val gy0 = 7.8f; private val gy1 = 10.0f; private val gm = 8.9f

    // the altar: two steps, the table, the retable on the wall
    private val s1x0 = 3.1f; private val s1x1 = 7.7f; private val s1y = 3.3f
    private val s2x0 = 3.5f; private val s2x1 = 7.3f; private val s2y = 2.9f
    private val mx0 = 4.4f; private val mx1 = 6.4f; private val my1 = 2.1f
    private val mTop = fl + 8.4f

    // the pews: the rows' front edges, the blocks either side of the runner
    private val rowsL = floatArrayOf(5.2f, 6.4f, 7.6f)
    private val rowsR = floatArrayOf(5.2f, 6.4f, 7.6f, 8.8f)
    private val lx0 = 2.6f; private val lx1 = 4.75f; private val rx0 = 5.95f; private val rx1 = 8.9f
    private val runA = 4.95f; private val runB = 5.75f

    // the people
    private val altarPX = 2.95f; private val altarPY = 3.7f
    private val aisleX = 5.35f; private val aisleY = 9.9f
    private val doorPX = 2.6f; private val doorPY = 8.9f

    // outside
    private val cypX = 10.9f; private val cypY = 2.3f

    private var floorId = 0
    private var pewId = -1

    private val plasterL = Col.hex(0xF4EEDF); private val plasterM = Col.hex(0xE8DFCB); private val plasterD = Col.hex(0xD3C7AC)
    private val dadoL = Col.hex(0xA9B8A2); private val dadoM = Col.hex(0x8C9E88); private val dadoD = Col.hex(0x6E806C)
    private val ochre = Col.hex(0xD29A48); private val ochreD = Col.hex(0xA87430); private val redP = Col.hex(0xA8402E)
    private val vaultL = Col.hex(0x2C4C8E); private val vault = Col.hex(0x223E7A); private val vaultD = Col.hex(0x18305E)
    private val goldHi = Col.hex(0xFFF2B0); private val goldL = Col.hex(0xF2CC5A); private val gold = Col.hex(0xD6A13A); private val goldD = Col.hex(0x9C6E1E)
    private val slabL = Col.hex(0xDCD4C4); private val slabM = Col.hex(0xCBC2B0); private val slabD = Col.hex(0xB6AC98); private val joint = Col.hex(0x8E8676)
    private val stoneHi = Col.hex(0xEAE5D8); private val stoneL = Col.hex(0xD6CFBF); private val stoneM = Col.hex(0xBDB4A1); private val stoneD = Col.hex(0x9A917F)
    private val woodL = Col.hex(0x9E6C42); private val woodM = Col.hex(0x7E5232); private val woodD = Col.hex(0x5E3B21); private val woodX = Col.hex(0x3E2615)
    private val marbleR = Col.hex(0xA84A3E); private val marbleRL = Col.hex(0xC87462); private val marbleRD = Col.hex(0x7C2E28)
    private val linen = Col.hex(0xF7F4EA); private val linenD = Col.hex(0xDCD6C8)
    private val runner = Col.hex(0xA42C2E); private val runnerD = Col.hex(0x7E1E22)
    private val mantle = Col.hex(0x3558A8); private val mantleD = Col.hex(0x243E80)
    private val ironD = Col.hex(0x26232A); private val iron = Col.hex(0x46424C); private val ironL = Col.hex(0x6E6A76)
    private val bronze = Col.hex(0xB8883A); private val bronzeL = Col.hex(0xE0B660); private val bronzeD = Col.hex(0x7A5620)
    private val glass = intArrayOf(Col.hex(0xC42A3C), Col.hex(0x2E54BE), Col.hex(0xEAA83A), Col.hex(0x2E9A5C), Col.hex(0x7A42AA), Col.hex(0xEDE6C4))

    /** Closer up (detail 2 or 3): the finer touches the scene's own canvas has no room for. */
    private val fine: Boolean get() = detail > 1

    /** The length of ([x], [y]). */
    private fun hyp(x: Float, y: Float): Float = sqrt(x * x + y * y)

    /** A church is a little dim inside; the windows and the candles light it. */
    override fun ambient(): FloatArray {
        val lift = env.dark * 0.06f
        return floatArrayOf(env.ambR * 0.92f + lift, env.ambG * 0.92f + lift * 0.85f, env.ambB * 0.92f + lift * 0.6f)
    }

    // ------------------------------------------------------------------ the feasts

    /** The nativity scene stands from Christmas to the end of January. */
    private val nativity: Boolean get() = frame.month == 12 || frame.month == 1

    /** The žegen: the baskets are blessed on Holy Saturday and stand before the altar until Easter Tuesday. */
    private val easter: Boolean get() {
        val d = frame.date ?: return false
        val days = ChronoUnit.DAYS.between(Calendar.easter(d.year), d)
        return days in -1..2
    }

    // ------------------------------------------------------------------ what plays now

    /** How hard the bell swings: a dialog's "bell", or the rope tapped (once, twice). */
    private fun bellLevel(): Float {
        val pk = poked("rope")
        val tolled = if (pk == null) 0f else {
            val a = pk.age(t); val d = if (pk.step == 0) 2.6f else 3.6f
            PokeArt.ease(a, 0.1f, 0.5f) * (1f - PokeArt.ease(a, d - 0.9f, d))
        }
        return max(fxOn("bell"), tolled)
    }

    /** The bell's swing angle now (radians), from [level]. */
    private fun swing(level: Float): Float = sin(t * 4.2).toFloat() * 0.5f * level

    /** Whether candle [i] (0..3, left to right) burns: a dialog's "candles" lights them one by one; two burn by themselves. */
    private fun candleLit(i: Int): Boolean {
        val lv = fx("candles")
        val lit = if (lv == null) i == 1 || i == 2 else lv > (i + 0.5f) / 4f
        if (!lit) return false
        // tapped a second time, the third one goes out in a draught and lights again
        val pk = poked("candles")
        if (pk != null && pk.step == 1 && i == 2) { val a = pk.age(t); if (a in 0.45f..1.7f) return false }
        return true
    }

    override fun paint() {
        fit()
        vignette()
        yardPlot()
        tufts(70, 23) { x, y -> busy(x, y) }
        // the church's shade on the grass by its right side
        shade(iso.sx(x1 + 0.4f, 5.5f), iso.sy(x1 + 0.4f, 5.5f, 0f), 4.4f * 4 * K, 1.1f * 4 * K, 0.86f, groundId)

        floor()
        walls()
        gable()
        thing("bell", slop = 1) { bell() }
        thing("window") { window(waA, wbA) }
        prop { window(waB, wbB) }
        sparrow()
        thing("cross", slop = 1) { crucifix() }
        thing("font", slop = 1) { font() }
        thing("door") { door() }
        prop { retable() }
        thing("painting") { painting() }
        prop { steps() }
        thing("altar") { altar() }
        thing("candles", slop = 2) { candles() }
        thing("flowers", slop = 1) { flowers() }
        if (nativity) prop { jaslice() } else prop { statue() }
        prop(outline = 0) { lamp() }
        if (easter) prop { baskets() }
        for (p in peopleAt("altar")) { footShadow(altarPX, altarPY); personAt(p, altarPX, altarPY, fl, flip = false) }

        thing("pews") { pewId = c.penId; pews() }
        for (p in peopleAt("door")) { footShadow(doorPX, doorPY); personAt(p, doorPX, doorPY, fl) }
        thing("rope", outline = 0, slop = 2) { rope() }
        prop { loftFloor() }
        thing("organ") { organ() }
        prop { balustrade() }
        for (p in peopleAt("aisle")) { footShadow(aisleX, aisleY); personAt(p, aisleX, aisleY, fl) }

        // the churchyard
        prop { cypress() }
        prop { bench() }
        prop { lavender(4.05f, 11.3f); lavender(6.65f, 11.3f) }

        fireflies(iso.ix(9f, 12f), iso.iy(9f, 12f, 16f), iso.ix(12f, 5f), iso.iy(12f, 12f, 0f), 7, 11)
        sunPatches()
        lights()
        organNotes(fxOn("organ"))
        bellRings()
        candleFlicker()
    }

    // ------------------------------------------------------------------ small helpers

    /** Like [quadFill], but pixels the shader answers 0 for are left alone. */
    private inline fun quadPlot(
        xa: Float, ya: Float, za: Float, xb: Float, yb: Float, zb: Float,
        xc: Float, yc: Float, zc: Float, xd: Float, yd: Float, zd: Float,
        crossinline shader: (Int, Int) -> Int,
    ) {
        c.polyBegin()
        c.polyAdd(iso.sx(xa, ya), iso.sy(xa, ya, za)); c.polyAdd(iso.sx(xb, yb), iso.sy(xb, yb, zb))
        c.polyAdd(iso.sx(xc, yc), iso.sy(xc, yc, zc)); c.polyAdd(iso.sx(xd, yd), iso.sy(xd, yd, zd))
        c.polyScan { px, py -> val col = shader(px, py); if (col != 0) c.set(px, py, col) }
    }

    /** Paints the part of the plane x = [xp] over [ya]..[yb] × [za]..[zb] that [shader] (y, z) gives a colour (0: none). */
    private inline fun onLeft(xp: Float, ya: Float, yb: Float, za: Float, zb: Float, crossinline shader: (Float, Float) -> Int) =
        quadPlot(xp, ya, za, xp, yb, za, xp, yb, zb, xp, ya, zb) { px, py -> shader(wallXy(px, xp), wallXz(px, py, xp)) }

    /** The same in the plane y = [yp] over [xa]..[xb] × [za]..[zb], the shader getting (x, z). */
    private inline fun onRight(yp: Float, xa: Float, xb: Float, za: Float, zb: Float, crossinline shader: (Float, Float) -> Int) =
        quadPlot(xa, yp, za, xb, yp, za, xb, yp, zb, xa, yp, zb) { px, py -> shader(wallYx(px, yp), wallYz(px, py, yp)) }

    /** The same on the level z = [z] over [xa]..[xb] × [ya]..[yb], the shader getting (x, y). */
    private inline fun onFlat(z: Float, xa: Float, xb: Float, ya: Float, yb: Float, crossinline shader: (Float, Float) -> Int) =
        quadPlot(xa, ya, z, xb, ya, z, xb, yb, z, xa, yb, z) { px, py -> shader(flatX(px, py, z), flatY(px, py, z)) }

    /** A box with shaded faces: the top [top], its +y face [front], its +x face [side], each shader getting the pixel. */
    private inline fun block(
        xa: Float, ya: Float, xb: Float, yb: Float, za: Float, zb: Float,
        crossinline front: (Float, Float) -> Int, crossinline side: (Float, Float) -> Int, crossinline top: (Float, Float) -> Int,
    ) {
        onRight(yb, xa, xb, za, zb) { x, z -> front(x, z) }
        onLeft(xb, ya, yb, za, zb) { y, z -> side(y, z) }
        onFlat(zb, xa, xb, ya, yb) { x, y -> top(x, y) }
    }

    /** A rod [n] px thick between two world points: steep ones widen right, flat ones down. */
    private fun rod(xa: Float, ya: Float, za: Float, xb: Float, yb: Float, zb: Float, col: Int, n: Int = detail) {
        val ax = iso.ix(xa, ya); val ay = iso.iy(xa, ya, za); val bx = iso.ix(xb, yb); val by = iso.iy(xb, yb, zb)
        val steep = abs(by - ay) > abs(bx - ax)
        for (o in 0 until n) if (steep) c.line(ax + o, ay, bx + o, by, col) else c.line(ax, ay + o, bx, by + o, col)
    }

    /** One scene pixel ([detail] × [detail]) with its top left at picture ([x], [y]). */
    private fun pix(x: Int, y: Int, col: Int) = c.fillRect(x, y, detail, detail, col)

    /** The sky at [tt] (0 = the top, 1 = the horizon) of this hour. */
    private fun skyCol(tt: Float): Int =
        if (tt < 0.55f) Col.mix(env.skyTop, env.skyMid, tt / 0.55f) else Col.mix(env.skyMid, env.skyHorizon, (tt - 0.55f) / 0.45f)

    private fun footShadow(x: Float, y: Float) = shade(iso.sx(x, y), iso.sy(x, y, fl), 5f * K, 1.6f * K, 0.72f, floorId)

    // ------------------------------------------------------------------ the churchyard

    private fun yardPlot() = plot { x, y, px, py -> ground(x, y, px, py) }

    /** Grass, the gravel path from the nave out to the plot's edge, trodden earth by the church. */
    private fun ground(x: Float, y: Float, px: Int, py: Int): Int {
        if (x > x0 - th + 0.15f && x < x1 - 0.15f && y > y0 - th + 0.15f && y < y1 - 0.15f) return slabD
        val g = grassAt(x, y, px, py)
        if (y > y1 - 0.2f && abs(x - aisleX) < 0.85f + Noise.v1(y * 3f, 5) * 0.12f) return gravel(px, py)
        val apron = if (x < x1 + 0.6f && y < y1 + 0.6f) 1f - max(max(0f, x - x1), max(0f, y - y1)) / 0.6f else 0f
        if (apron <= 0f) return g
        return dirtAt(px, py, (apron * (0.6f + Noise.v2(x * 2f, y * 2f, 3) * 0.5f)).coerceIn(0f, 1f), g)
    }

    /** Pale Karst gravel, snow in winter. */
    private fun gravel(px: Int, py: Int): Int {
        if (winter) return if (Dither.at(px, py) < 0.7f) Pal.SNOW_L else Pal.SNOW_M
        val n = Noise.rnd(px / detail, py / detail, 31)
        return when {
            n < 0.18f -> Col.hex(0xA8A090)
            n < 0.6f -> Col.hex(0xCFC8B8)
            n < 0.9f -> Col.hex(0xBDB5A3)
            else -> Col.hex(0xE6E0D2)
        }
    }

    private fun busy(x: Float, y: Float): Boolean =
        (x < x1 + 0.4f && y < y1 + 0.4f) || (y > y1 - 0.3f && abs(x - aisleX) < 1.1f) || hyp(x - cypX, y - cypY) < 0.8f ||
            (x in 10.2f..11.9f && y in 6.9f..8.1f) || hyp(x - 4.05f, y - 11.3f) < 0.6f || hyp(x - 6.65f, y - 11.3f) < 0.6f

    /** A cypress by the far corner: a dark narrow flame of green, lighter tufts on its sunny side; a little snow in winter. */
    private fun cypress() {
        val bx = iso.sx(cypX, cypY); val by = iso.sy(cypX, cypY, 0f)
        shade(bx + 3f * K, by, 5f * K, 1.6f * K, 0.8f, groundId)
        c.fillRect(floor(bx).toInt() - detail, floor(by - 3f * K).toInt(), 2 * detail, (3f * K).toInt(), Pal.LOG_D)
        val hgt = 27f * K
        val dk = Col.hex(0x1E3F26); val md = Col.hex(0x2C5634); val lt = Col.hex(0x3E7042); val hi = Col.hex(0x58894E)
        val top = by - 2f * K - hgt
        for (py in max(c.top, floor(top).toInt())..min(c.bottom - 1, floor(by - 2f * K).toInt())) {
            val u = (py - top) / hgt // 0 at the tip
            val half = (0.15f + 0.85f * sqrt(u) * (1f - 0.35f * u * u)) * 3.2f * K + sin(u * 23f) * 0.35f * K
            for (px in max(c.left, floor(bx - half).toInt())..min(c.right - 1, floor(bx + half).toInt())) {
                val f = (px + 0.5f - bx) / half
                val n = Noise.rnd(px / detail, py / detail, 41)
                val tuft = Noise.v2(px / (1.6f * K), py / (2.2f * K), 42)
                c.set(px, py, when {
                    winter && tuft > 0.72f && f < 0.2f -> Pal.SNOW_L
                    f < -0.35f && tuft > 0.45f -> if (n < 0.3f) hi else lt
                    f < 0.15f -> if (tuft > 0.62f) lt else md
                    tuft > 0.7f -> md
                    else -> dk
                })
            }
        }
    }

    /** A stone bench: a Karst slab on two blocks. */
    private fun bench() {
        val xa = 10.3f; val xb = 11.7f; val ya = 7.2f; val yb = 7.7f
        for (bx in floatArrayOf(xa + 0.12f, xb - 0.42f)) block(bx, ya + 0.08f, bx + 0.3f, yb - 0.08f, 0f, 2.6f,
            { _, _ -> stoneM }, { _, _ -> stoneD }, { _, _ -> stoneL })
        block(xa, ya, xb, yb, 2.6f, 3.6f, { _, z -> if (z > 3.3f) stoneHi else stoneL }, { _, _ -> stoneM },
            { _, _ -> if (winter) Pal.SNOW_L else stoneHi })
    }

    /** A lavender bush by the path: grey-green, its spikes purple from June to August; a mound of snow in winter. */
    private fun lavender(x: Float, y: Float) {
        val bx = iso.sx(x, y); val by = iso.sy(x, y, 0f)
        if (winter) { c.fillEllipse(bx, by - 1.5f * K, 3f * K, 2f * K, Pal.SNOW_M); c.fillEllipse(bx - 0.5f * K, by - 2f * K, 2.2f * K, 1.4f * K, Pal.SNOW_L); return }
        c.fillEllipse(bx, by - 2f * K, 3.2f * K, 2.4f * K, Col.hex(0x5E7A5C))
        c.fillEllipse(bx - 0.8f * K, by - 2.6f * K, 2f * K, 1.5f * K, Col.hex(0x7E9A78))
        if (frame.month in 6..8) for (j in 0 until 9) {
            val sx = floor(bx + (Noise.rnd(j, 51) - 0.5f) * 5.2f * K).toInt(); val sy = floor(by - 3f * K - Noise.rnd(j, 52) * 2.2f * K).toInt()
            c.fillRect(sx, sy, detail, 2 * detail, if (j % 3 == 0) Col.hex(0xA888D8) else Col.hex(0x8A62C0))
        }
    }

    // ------------------------------------------------------------------ the floor

    /** The limestone floor a step over the churchyard, the red runner up the aisle; its cut edges show the footing. */
    private fun floor() {
        floorId = s.newObject(Pal.OUTLINE)
        quadFill(x0 - th, y1, 0f, x1, y1, 0f, x1, y1, fl, x0 - th, y1, fl) { px, py -> footing(wallYx(px, y1), wallYz(px, py, y1), px, py, 1f) }
        quadFill(x1, y0 - th, 0f, x1, y1, 0f, x1, y1, fl, x1, y0 - th, fl) { px, py -> footing(wallXy(px, x1) + 7f, wallXz(px, py, x1), px, py, 0.8f) }
        quadFill(x0, y0, fl, x1, y0, fl, x1, y1, fl, x0, y1, fl) { px, py -> slab(flatX(px, py, fl), flatY(px, py, fl), px, py) }
        c.penId = 0
        // contact shadows: along the steps, under the pews, the pedestal
        shade(iso.sx(5.4f, s1y + 0.1f), iso.sy(5.4f, s1y + 0.1f, fl), 9.5f * K, 1.4f * K, 0.8f, floorId)
        for (r in rowsR) shade(iso.sx((rx0 + rx1) / 2f, r + 0.75f), iso.sy((rx0 + rx1) / 2f, r + 0.75f, fl), 6.5f * K, 1.2f * K, 0.84f, floorId)
        for (r in rowsL) shade(iso.sx((lx0 + lx1) / 2f, r + 0.75f), iso.sy((lx0 + lx1) / 2f, r + 0.75f, fl), 5f * K, 1.2f * K, 0.84f, floorId)
    }

    private fun footing(a: Float, z: Float, px: Int, py: Int, light: Float): Int {
        val col = when {
            z > fl - 0.35f -> stoneHi
            else -> {
                val course = floor(z * 1.6f).toInt()
                val u = a * 1.4f + course * 0.5f
                val fu = u - floor(u)
                if (fine && fu < 0.06f) joint else if (Noise.rnd(floor(u).toInt(), course, 77) < 0.5f) stoneM else stoneD
            }
        }
        return Col.scale(col, light)
    }

    /** A limestone slab at ([x], [y]) in staggered rows; the red runner up the aisle with its gold borders. */
    private fun slab(x: Float, y: Float, px: Int, py: Int): Int {
        if (x in runA..runB && y > s1y - 0.05f) {
            val e = min(x - runA, runB - x)
            return when {
                e < 0.07f -> runnerD
                e < 0.15f -> if (fine && Dither.at(px, py) < 0.3f) goldL else gold
                fine && Noise.rnd(px, py, 88) < 0.06f -> runnerD
                else -> runner
            }
        }
        val rw = 1.05f
        val row = floor(y / rw).toInt(); val fy = y - row * rw
        val len = 1.1f + Noise.rnd(row, 71) * 0.7f
        val u = x / len + Noise.rnd(row, 72) * 3f
        val b = floor(u).toInt(); val fx = (u - b) * len
        val jw = if (fine) 0.05f else 0.1f
        if (fy < jw || fx < jw) return joint
        val tone = Noise.rnd(b, row, 73)
        var col = if (tone < 0.3f) slabL else if (tone > 0.75f) slabD else slabM
        if (fine && (fy < jw + 0.06f || fx < jw + 0.06f)) col = Col.mix(col, Col.hex(0xFFFFFF), 0.18f)
        val n = Noise.rnd(px, py, 74)
        // worn smooth in the aisle and before the altar
        if (n < 0.05f) col = Col.scale(col, 0.92f)
        if (fine && Noise.v2(x * 3f, y * 3f, 75) > 0.78f) col = Col.mix(col, Col.hex(0xE8E2D2), 0.3f)
        return col
    }

    // ------------------------------------------------------------------ the walls

    /** The top of the far wall at [x]: the vault's end rises over it in a round arch. */
    private fun archTop(x: Float): Float {
        val u = (x - xm) / hw
        return wallH + archH * sqrt(max(0f, 1f - u * u))
    }

    /**
     * The whitewashed plaster at [a] cells along a wall and [z] px up: a painted dado of grey-green marbling below, a
     * frieze of red waves on ochre under the top; [light] dims the face in the shade.
     */
    private fun plaster(a: Float, z: Float, px: Int, py: Int, light: Float): Int {
        val col = when {
            z < fl + 4.6f -> {
                if (z > fl + 4.1f) dadoD
                else {
                    val vein = abs(Noise.v2(a * 2.6f, z * 0.45f, 13) - 0.5f)
                    if (vein < 0.035f) dadoD else if (Noise.v2(a * 1.3f, z * 0.3f, 14) > 0.6f) dadoL else dadoM
                }
            }
            z > wallH - 3.2f -> frieze(a, z)
            else -> {
                val n = Noise.rnd(px, py, 12)
                val cloud = Noise.v2(a * 1.1f, z * 0.12f, 15)
                if (n < 0.04f) plasterD else if (cloud > 0.7f) plasterM else plasterL
            }
        }
        return if (light == 1f) col else Col.scale(col, light)
    }

    /** The painted frieze under the top of the walls: a running red wave on ochre between thin red lines. */
    private fun frieze(a: Float, z: Float): Int {
        val v = z - (wallH - 3.2f)
        return when {
            v < 0.45f || v > 2.75f -> redP
            v < 0.75f || v > 2.45f -> plasterL
            else -> {
                val wave = 1.6f + sin(a * 4.47f / 1.3f) * 0.55f
                if (abs(v - wave) < (if (fine) 0.28f else 0.4f)) redP else ochre
            }
        }
    }

    /** Whether ([a], [z]) of the far wall is a painted consecration cross: a red cross in a ring. */
    private fun consecration(x: Float, z: Float): Boolean {
        for (cx in floatArrayOf(2.3f, 8.55f)) {
            val dx = (x - cx) * 4.47f; val dz = z - (fl + 16.5f)
            val r = hyp(dx, dz)
            if (r > 1.9f) continue
            if (r > 1.45f) return true
            if (abs(dx) < 0.42f || abs(dz) < 0.42f) return r < 1.25f
        }
        return false
    }

    private fun walls() {
        part()
        // the left wall's face, in the shade
        onLeft(x0, y0, y1, fl, wallH) { y, z -> plaster(y, z, 0, 0, 0.86f) }
        part()
        // the far wall's face, and over it the vault's end: blue with golden stars
        onRight(y0, x0, x1, fl, wallH + archH) { x, z ->
            val top = archTop(x)
            when {
                z > top -> 0
                z > wallH -> lunette(x, z, top)
                consecration(x, z) -> redP
                else -> plaster(x + 17f, z, 0, 0, 1f)
            }
        }
        // the walls' cut ends: rubble in section, the plaster's skin
        part()
        onRight(y1, x0 - th, x0, 0f, wallH) { x, z -> cutStone(x * 3f + 5f, z, if (x > x0 - 0.06f) 1f else 0.95f) }
        onLeft(x1, y0 - th, y0, 0f, wallH) { y, z -> if (z > wallH) 0 else cutStone(y * 3f + 11f, z, if (y > y0 - 0.06f) 0.82f else 0.78f) }
        // the tops of the walls, and the arch's back
        part()
        onFlat(wallH, x0 - th, x0, y0 - th, y1) { _, _ -> if (winter) Pal.SNOW_L else stoneL }
        onFlat(wallH, x0 - th, x1, y0 - th, y0) { x, _ -> if (x > x0 && x < x1) 0 else if (winter) Pal.SNOW_L else stoneL }
        archBack()
        c.penId = 0
    }

    /** The vault's end over the far wall: deep blue, lighter at its foot, golden stars, an ochre band along the arch. */
    private fun lunette(x: Float, z: Float, top: Float): Int {
        val v = z - wallH
        if (v < 0.55f) return if (v < 0.25f) goldD else goldL
        if (top - z < 0.55f) return ochreD
        if (top - z < 0.95f) return ochre
        if (top - z < 1.25f) return redP
        // the stars on a staggered grid, each a point (a little cross closer up)
        val gs = 2.3f
        val a = x * 4.47f / gs; val row = floor(v / gs).toInt()
        val u = a + (row and 1) * 0.5f
        val cu = floor(u).toInt()
        val fu = (u - cu - 0.5f) * gs; val fv = (v / gs - row - 0.5f) * gs
        val jitter = Noise.rnd(cu, row, 17)
        if (jitter < 0.8f) {
            val dx = abs(fu - (jitter - 0.4f) * 0.8f); val dz = abs(fv)
            val p = 0.5f / K
            if (dx < p * 2f && dz < p) return goldHi
            if (fine && ((dx < p && dz < 0.55f) || (dz < p * 0.7f && dx < 0.36f))) return goldL
        }
        val shade = v / archH
        return if (shade < 0.25f) vaultL else if (shade > 0.8f && Dither.at(x.toInt(), z.toInt()) < 0.5f) vaultD else vault
    }

    /** The back of the arch: a thin strip along its top, the wall's thickness. */
    private fun archBack() {
        val n = 28
        val col = if (winter) Pal.SNOW_L else stoneL
        for (i in 0 until n) {
            val xa = x0 + (x1 - x0) * i / n; val xb = x0 + (x1 - x0) * (i + 1) / n
            val za = archTop(xa); val zb = archTop(xb)
            quadFill(xa, y0 - th, za, xb, y0 - th, zb, xb, y0, zb, xa, y0, za) { _, _ -> col }
        }
    }

    /** Rubble in section: rough courses, dark joints. */
    private fun cutStone(a: Float, z: Float, light: Float): Int {
        val course = floor(z / 2.4f).toInt()
        val u = a + Noise.rnd(course, 81) * 3f
        val fu = u - floor(u); val fz = z / 2.4f - course
        val col = when {
            fz < 0.14f || fu < 0.1f -> stoneD
            Noise.rnd(floor(u).toInt(), course, 82) < 0.4f -> stoneM
            else -> stoneL
        }
        return Col.scale(col, light)
    }

    // ------------------------------------------------------------------ the bell gable and the bell

    /** The gable's outline at [y]: how high its stone stands. */
    private fun gableTop(y: Float): Float {
        val d = abs(y - gm)
        return if (d > 0.75f) wallH + 3.2f else wallH + 8.4f + (1f - d / 0.75f) * 2.4f
    }

    /** Whether ([y], [z]) is in the gable's arched opening. */
    private fun inOpening(y: Float, z: Float): Boolean {
        val r = 0.45f * 4.47f
        val spring = wallH + 6.2f
        if (abs(y - gm) > 0.45f || z < wallH + 2.6f) return false
        return z <= spring || hyp((y - gm) * 4.47f, z - spring) <= r
    }

    /** A bell gable of Karst stone over the door: ashlar courses, the arch open to the sky, a small iron cross on top. */
    private fun gable() {
        prop {
            onLeft(x0, gy0, gy1, wallH - 0.1f, wallH + 11f) { y, z ->
                val top = gableTop(y)
                when {
                    y < gy0 || y > gy1 || z > top -> 0
                    inOpening(y, z) -> {
                        val tt = ((wallH + 9f - z) / 9f).coerceIn(0f, 1f)
                        skyCol(0.3f + tt * 0.6f)
                    }
                    top - z < 0.45f -> if (winter) Pal.SNOW_L else stoneHi
                    else -> {
                        val course = floor((z - wallH) / 1.8f).toInt()
                        val u = (y - gy0) * 4.47f / 2.6f + (course and 1) * 0.5f
                        val fu = u - floor(u); val fz = (z - wallH) / 1.8f - course
                        val tone = Noise.rnd(floor(u).toInt(), course, 83)
                        val rim = inOpening(y, z - 0.6f) || inOpening(y + 0.12f, z) || inOpening(y - 0.12f, z)
                        when {
                            rim -> stoneD
                            fz < (if (fine) 0.1f else 0.16f) || fu < (if (fine) 0.05f else 0.08f) -> stoneM
                            tone < 0.35f -> stoneHi
                            else -> stoneL
                        }
                    }
                }
            }
            // its end, the stone's thickness
            onRight(gy1, x0 - th, x0, wallH, wallH + 3.2f) { _, z -> if (z > wallH + 2.8f && winter) Pal.SNOW_L else stoneM }
            // the cross on top
            val cx = iso.ix(x0 - th / 2f, gm); val ct = iso.iy(x0 - th / 2f, gm, wallH + 14.4f); val cb = iso.iy(x0 - th / 2f, gm, wallH + 10.6f)
            c.fillRect(cx - detail / 2, ct, detail, cb - ct, ironL)
            c.fillRect(cx - K - detail / 2, ct + K, 2 * K + detail, detail, ironL)
        }
    }

    /** The bell in the gable's arch, swinging from its yoke when it rings. */
    private fun bell() {
        val lv = bellLevel()
        val a = swing(lv)
        val xp = x0 + 0.01f
        val pivot = wallH + 8.1f
        val bh = 4.4f
        // the yoke: a beam across the arch
        onLeft(xp, gm - 0.42f, gm + 0.42f, pivot - 0.2f, pivot + 0.7f) { _, z -> if (z > pivot + 0.4f) woodL else woodD }
        val ca = cos(a); val sa = sin(a)
        onLeft(xp, gm - 0.62f, gm + 0.62f, pivot - bh - 1.8f, pivot) { y, z ->
            // into the bell's own frame: u across (nominal px), v down from the pivot
            val dy = (y - gm) * 4.47f; val dz = pivot - z
            val u = dy * ca + dz * sa; val v = -dy * sa + dz * ca
            val f = v / bh
            val half = 0.6f + 0.45f * f + 0.8f * f * f * f * f
            when {
                v < 0.2f || v > bh + 0.5f -> 0
                !inOpening(y, z) && !inOpening(y, z + 1.2f) -> 0 // it swings within the arch
                v > bh -> if (abs(u) < 0.45f && v < bh + 0.5f) bronzeD else 0 // the clapper's ball under the lip
                abs(u) > half -> 0
                f > 0.86f -> if (u < 0f) bronze else bronzeD // the lip
                u < -half + 0.55f -> bronzeL
                fine && abs(f - 0.3f) < 0.04f -> bronzeD // a band cast round it
                u > half - 0.6f -> bronzeD
                else -> bronze
            }
        }
    }

    // ------------------------------------------------------------------ the windows

    /** The windows' arch radius (nominal px) and where it springs. */
    private val wr: Float get() = (wbA - waA) / 2f * 4.47f
    private val wSpring: Float get() = wz1 - wr

    /** Whether ([y], [z]) is in the window between [wa] and [wb]. */
    private fun inWindow(wa: Float, wb: Float, y: Float, z: Float): Boolean {
        if (y < wa || y > wb || z < wz0) return false
        if (z <= wSpring) return true
        return hyp((y - (wa + wb) / 2f) * 4.47f, z - wSpring) <= wr
    }

    /** How bright the glass is: backlit by day, a little warm at dusk, dark at night. */
    private fun glassLight(): Float = (0.22f + 0.78f * (1f - env.dark)) * (1f - 0.35f * frame.sky.gloom)

    /**
     * The coloured glass at ([p], [q]) nominal px from the window's lower left: diamonds of ruby, cobalt, amber, green and
     * violet in lead, a pale border, a red rose in a ring in the middle.
     */
    private fun glassAt(p: Float, q: Float, w: Float, px: Int, py: Int): Int {
        val lead = Col.hex(0x2A2428)
        val lw = if (fine) 0.5f / K + 0.12f else 0.5f
        // the border
        val span = wz1 - wz0
        if (p < 0.9f || w - p < 0.9f || q < 0.9f) return if (p < lw || w - p < lw || q < lw || (p in 0.9f - lw..0.9f) || (w - p in 0.9f - lw..0.9f) || (q in 0.9f - lw..0.9f)) lead else glass[2]
        // the rose in the middle
        val cq = span * 0.5f
        val rr = hyp(p - w / 2f, q - cq)
        if (rr < 2.1f) return when {
            rr > 1.9f - lw * 0.3f -> lead
            rr < 0.55f -> glass[2]
            else -> {
                val ang = kotlin.math.atan2(q - cq, p - w / 2f)
                val petal = abs(sin(ang * 3f))
                if (fine && abs(petal - 0.5f) < 0.08f) lead else if (petal > 0.5f) glass[0] else Col.mix(glass[0], glass[5], 0.35f)
            }
        }
        val s1 = (p + q) / 2.2f; val s2 = (p - q) / 2.2f
        val f1 = s1 - floor(s1); val f2 = s2 - floor(s2)
        val lwc = lw / 2.2f
        if (f1 < lwc || f2 < lwc) return lead
        val cell = Noise.hash(floor(s1).toInt(), floor(s2).toInt(), 19)
        var col = glass[Math.floorMod(cell, 5)]
        // old glass: a little uneven
        if (fine && Noise.rnd(px, py, 20) < 0.08f) col = Col.mix(col, Col.hex(0xFFFFFF), 0.18f)
        return col
    }

    /**
     * A tall window with a round head deep in the left wall: the coloured glass (glowing by day, dark at night), the
     * plaster reveal splayed round it lit on one side and shaded on the other, the sill.
     */
    private fun window(wa: Float, wb: Float) {
        val w = (wb - wa) * 4.47f
        val light = glassLight()
        val night = Col.hex(0x141A30)
        onLeft(x0 + 0.01f, wa - 0.2f, wb + 0.2f, wz0 - 0.9f, wz1 + 0.9f) { y, z ->
            val inner = inWindow(wa, wb, y, z)
            val outer = inWindow(wa - 0.2f, wb + 0.2f, y, z + (if (z < wz0) 0.9f else 0f)) || (y in wa - 0.2f..wb + 0.2f && z in wz0 - 0.9f..wz0)
            when {
                inner -> {
                    val g = glassAt((y - wa) * 4.47f, z - wz0, w, 0, 0)
                    val lit = Col.scale(g, light)
                    if (env.dark > 0.5f) Col.mix(lit, night, 0.35f) else lit
                }
                !outer -> 0
                z < wz0 -> if (z > wz0 - 0.35f) plasterL else plasterD // the sill
                y < (wa + wb) / 2f -> plasterM
                else -> Col.scale(plasterM, 0.8f)
            }
        }
        // the glass shines: the light pass leaves it as it is
        val n = c.penId
        val xs = iso.sx(x0, wa); val xe = iso.sx(x0, wb)
        val ys = iso.sy(x0, wa, wz1 + 1f); val ye = iso.sy(x0, wb, wz0)
        for (py in max(c.top, floor(ys).toInt())..min(c.bottom - 1, floor(ye).toInt())) for (px in max(c.left, floor(xe).toInt())..min(c.right - 1, floor(xs).toInt() + 1)) {
            val i = c.index(px, py)
            if (c.ids[i] == n && inWindow(wa, wb, wallXy(px, x0 + 0.01f), wallXz(px, py, x0 + 0.01f))) c.emissive[i] = true
        }
    }

    /**
     * By day each window lays its colours on the floor: its glass, slanted in, further out the higher the sun is; the
     * patches move with the hour.
     */
    private fun sunPatches() {
        val sun = env.sun.coerceIn(0f, 1f)
        val a = min(1f, sun * 2.2f) * 0.42f * (1f - frame.sky.gloom * 0.8f)
        if (a <= 0.02f) return
        val slope = 0.11f + 0.06f * (1f - sun)
        val dy = (s.hour - 13f) * 0.16f
        val id = floorId; val pew = pewId
        for ((wa, wb) in listOf(waA to wbA, waB to wbB)) {
            val w = (wb - wa) * 4.47f
            val xa = x0 + (wz0 - fl) * slope; val xb = x0 + (wz1 - fl) * slope
            s.fx {
                c.polyBegin()
                c.polyAdd(iso.sx(xa, wa + dy), iso.sy(xa, wa + dy, fl)); c.polyAdd(iso.sx(xa, wb + dy), iso.sy(xa, wb + dy, fl))
                c.polyAdd(iso.sx(xb, wb + dy), iso.sy(xb, wb + dy, fl)); c.polyAdd(iso.sx(xb, wa + dy), iso.sy(xb, wa + dy, fl))
                c.polyScan { px, py ->
                    if (c.inside(px, py) && c.ids[c.index(px, py)].let { it == id || it == pew }) {
                        val fx = flatX(px, py, fl); val fy = flatY(px, py, fl)
                        val z = fl + (fx - x0) / slope; val y = fy - dy
                        if (inWindow(wa, wb, y, z)) {
                            val g = glassAt((y - wa) * 4.47f, z - wz0, w, px, py)
                            if (g != Col.hex(0x2A2428)) c.blend(px, py, Col.mix(g, Col.hex(0xFFF4D8), 0.3f), if (Dither.at(px, py) < 0.85f) a else a * 0.5f)
                        }
                    }
                }
            }
            s.light(iso.sx((xa + xb) / 2f, (wa + wb) / 2f + dy), iso.sy((xa + xb) / 2f, (wa + wb) / 2f + dy, fl + 2f), 24f, 0.35f * sun)
        }
    }

    // ------------------------------------------------------------------ the sparrow on the sill

    /**
     * A sparrow on the inner sill of the window: it hops and chirps when tapped; again, it flutters up and settles; a
     * third time it flies a round of the nave and comes back. At night it sleeps, a round ball.
     */
    private fun sparrow() {
        val pk = poked(SPARROW)
        val a = pk?.age(t) ?: 0f
        val step = pk?.step ?: -1
        val hx = x0 + 0.08f; val hy = 4.3f
        val bx = iso.sx(hx, hy); val by = iso.sy(hx, hy, wz0 - 0.2f)
        var ox = 0f; var oy = 0f; var flying = false; var face = -1f
        when (step) {
            0 -> if (a in 0.2f..0.55f || a in 0.8f..1.15f) oy = -1.6f * K * sin(((if (a < 0.6f) a - 0.2f else a - 0.8f) / 0.35f) * PI.toFloat())
            1 -> if (a < 1.6f) { val u = a / 1.6f; oy = -7f * K * sin(u * PI.toFloat()); ox = 2.2f * K * sin(u * 2f * PI.toFloat()); flying = true }
            2 -> if (a < 4.2f) {
                val u = a / 4.2f
                // out over the pews and round, back to the sill
                val ang = u * 2f * PI.toFloat()
                ox = (1f - cos(ang)) * 26f * K; oy = -sin(ang) * 9f * K - sin(u * PI.toFloat()) * 7f * K
                face = if (sin(ang) >= 0f) 1f else -1f
                flying = true
            }
        }
        val asleep = !flying && step < 0 && env.dark > 0.55f
        pokeable(SPARROW) { sparrowAt(floor(bx + ox).toInt(), floor(by + oy).toInt(), flying, asleep, face, step == 0 && a < 1.3f) }
        if (step == 0 && a < 1.3f) s.fx { PokeArt.rings(c, bx - 2f * K, by - 4f * K, a / 1.3f, 5f * K, Col.hex(0xFFF6D8), max(1, detail / 2), 0.7f, n = 2) }
    }

    /** The sparrow, its feet at ([x], [y]): brown back, grey cap, black bib; wings beating when [flying]; a ball when [asleep]. */
    private fun sparrowAt(x: Int, y: Int, flying: Boolean, asleep: Boolean, face: Float, chirp: Boolean) {
        val brown = Col.hex(0x8A5A32); val brownL = Col.hex(0xB08050); val grey = Col.hex(0x8C8C90); val cream = Col.hex(0xD8CCB4); val black = Col.hex(0x242022)
        val f = face.toInt()
        sprite(x, y) {
            if (asleep) {
                c.fillRect(x - 2, y - 3, 4, 3, brown); c.fillRect(x - 1, y - 4, 3, 1, brownL); c.set(x + f, y - 3, grey); c.hline(x - 1, x + 1, y - 1, cream)
                return@sprite
            }
            // body and tail
            c.fillRect(x - 1, y - 3, 3, 2, brown); c.set(x - 2 * f, y - 2, brown); c.set(x - 3 * f, y - 1, Col.scale(brown, 0.8f))
            c.hline(x - 1, x + 1, y - 1, cream)
            // head: grey cap, a black bib, the beak
            c.fillRect(x + f, y - 5, 2, 2, grey); if (f < 0) c.fillRect(x - 2, y - 5, 2, 2, grey)
            c.set(x + 2 * f, y - 4, if (chirp && (t * 8).toInt() % 2 == 0) Col.hex(0xE8C070) else Col.hex(0x6A5040))
            c.set(x + f, y - 3, black); c.set(x + f, y - 4, black)
            if (flying) {
                val up = (t * 14).toInt() % 2 == 0
                if (up) { c.set(x - 1, y - 4, brownL); c.set(x - 2, y - 5, brownL); c.set(x, y - 5, brownL) } else { c.set(x - 1, y - 1, brownL); c.set(x - 2, y, brownL) }
            } else {
                c.set(x, y - 3, brownL); c.set(x - f, y - 2, Col.hex(0x5A3A20))
                c.set(x, y, Col.hex(0x6A5040)); c.set(x + f, y, Col.hex(0x6A5040))
            }
        }
    }

    // ------------------------------------------------------------------ the crucifix, the font, the door

    /** A crucifix on the left wall near the altar: a dark cross, the ivory figure on it, the plaque; gilt rays round the head closer up. */
    private fun crucifix() {
        val xp = x0 + 0.06f
        val z0 = fl + 11f; val z1 = fl + 25f; val zc = fl + 21f
        val bw = 0.13f; val arm = 0.62f
        val corpus = Col.hex(0xECD8B8); val corpusD = Col.hex(0xC8AE88)
        onLeft(xp, crossY - arm - 0.05f, crossY + arm + 0.05f, z0, z1 + 0.5f) { y, z ->
            val dy = (y - crossY) * 4.47f
            val onBeam = (abs(y - crossY) < bw && z < z1) || (abs(z - zc) < 0.65f && abs(y - crossY) < arm)
            // the figure: head, arms along the beam, the body and the loincloth, the feet
            val head = hyp(dy, z - (zc - 1.4f)) < 0.85f
            val arms = abs(z - (zc - 0.2f + abs(dy) * 0.12f)) < 0.42f && abs(dy) < arm * 4.47f - 0.4f
            val body = abs(dy) < 0.85f - (zc - 2.2f - z) * 0.03f && z in zc - 8.2f..zc - 2f
            val cloth = abs(dy) < 1.05f && z in zc - 5.6f..zc - 4.2f
            val plaque = abs(dy) < 0.9f && z in z1 - 1.4f..z1 - 0.2f
            when {
                plaque -> if (fine && abs(z - (z1 - 0.8f)) < 0.2f && abs(dy) < 0.6f) redP else linen
                head -> if (dy > 0.3f) corpusD else corpus
                cloth -> if (dy > 0.4f) linenD else linen
                arms || body -> if (dy > 0.35f && body) corpusD else corpus
                onBeam -> if (abs(y - crossY) > bw - 0.05f || abs(abs(z - zc) - 0.6f) < 0.08f) woodX else woodD
                else -> 0
            }
        }
        // the gilt rays round the head, closer up
        if (fine) {
            val hx = iso.sx(xp, crossY); val hy = iso.sy(xp, crossY, zc - 1.4f)
            for (k in 0 until 8) {
                val ang = k * PI.toFloat() / 4f
                c.set(floor(hx + cos(ang) * 1.3f * K).toInt(), floor(hy + sin(ang) * 1.3f * K).toInt(), goldL)
            }
        }
    }

    /** The holy-water font by the door: a stone shell on the wall, its bowl out before it with a little water. */
    private fun font() {
        val cx = iso.sx(x0 + 0.25f, fontY); val base = iso.sy(x0 + 0.25f, fontY, fl + 8f)
        // the scallop back on the wall
        onLeft(x0 + 0.02f, fontY - 0.38f, fontY + 0.38f, fl + 9.5f, fl + 12.8f) { y, z ->
            val dy = (y - fontY) * 4.47f; val dz = z - (fl + 9.5f)
            val r = hyp(dy, dz)
            when {
                r > 1.65f || dz < 0f -> 0
                fine && abs(sin(kotlin.math.atan2(dz, dy) * 4.5f)) < 0.2f -> stoneD
                r > 1.4f -> stoneM
                else -> if (dy < 0f) stoneHi else stoneL
            }
        }
        // the bowl
        c.fillEllipse(cx, base - 0.6f * K, 1.5f * K, 1.3f * K, stoneM)
        c.fillEllipse(cx - 0.3f * K, base - 0.9f * K, 1.1f * K, 0.9f * K, stoneL)
        c.fillEllipse(cx, base - 1.9f * K, 1.55f * K, 0.55f * K, stoneHi)
        c.fillEllipse(cx, base - 1.85f * K, 1.15f * K, 0.35f * K, Col.hex(0x5A6878))
        if (fine) c.set(floor(cx - 0.5f * K).toInt(), floor(base - 1.95f * K).toInt(), Col.hex(0xB8C8D8))
        // its bracket
        c.fillRect(floor(cx).toInt() - detail, floor(base).toInt(), 2 * detail, K, stoneD)
    }

    /** The door under the loft: two leaves of walnut boards with iron studs in an arched frame of Karst stone, a ring. */
    private fun door() {
        val xp = x0 + 0.02f
        val mid = (da + db) / 2f
        val r = (db - da) / 2f * 4.47f
        val spring = dTop - r
        onLeft(xp, da - 0.22f, db + 0.22f, fl, dTop + 1f) { y, z ->
            val dy = (y - mid) * 4.47f
            val inside = abs(y - mid) <= (db - da) / 2f && (z <= spring || hyp(dy, z - spring) <= r)
            val frame = abs(y - mid) <= (db - da) / 2f + 0.22f && (z <= spring || hyp(dy, z - spring) <= r + 0.95f)
            when {
                inside -> {
                    val leaf = if (y < mid) 0 else 1
                    val bu = (y - da) / 0.23f; val board = floor(bu).toInt(); val fb = bu - board
                    var col = if (Noise.rnd(board, 91) < 0.5f) woodM else Col.mix(woodM, woodD, 0.4f)
                    if (fb < 0.12f || abs(y - mid) < 0.03f) col = woodX
                    else if (fine && abs((fb * 3f + Noise.v1(z * 0.4f + board * 7f, 92) * 1.5f) % 1f - 0.5f) < 0.07f) col = woodD
                    // iron studs in rows, and the ring
                    val sz = (z - fl) / 3f; val sy = bu * 0.5f
                    if (abs(sz - floor(sz) - 0.5f) < (if (fine) 0.09f else 0.14f) && abs(sy - floor(sy) - 0.5f) < (if (fine) 0.1f else 0.16f) && z < spring) col = if (fine) ironL else iron
                    if (leaf == 1 && hyp((y - (mid + 0.15f)) * 4.47f, z - (fl + 7.5f)) in 0.45f..0.85f) col = ironD
                    if (z < fl + 0.35f && env.sun > 0f) col = Col.hex(0xFFF0C8)
                    col
                }
                frame -> if ((z > spring && hyp(dy, z - spring) > r + 0.6f) || abs(y - mid) > (db - da) / 2f + 0.16f) stoneHi else stoneL
                else -> 0
            }
        }
    }

    // ------------------------------------------------------------------ the altar

    /**
     * The retable on the far wall over the altar: a gilded predella, two red marble columns with gold bases and capitals,
     * the cornice, and on top a golden sunburst between two small angels; the painting in its frame is [painting].
     */
    private fun retable() {
        val yp = y0 + 0.03f
        val xa = 4.1f; val xb = 6.7f
        val p0 = mTop; val p1 = mTop + 2f; val c1 = fl + 21.6f; val e1 = c1 + 1.5f
        val sunZ = e1 + 2.6f; val sunR = 2.3f
        onRight(yp, xa - 0.05f, xb + 0.05f, p0, e1 + 5.4f) { x, z ->
            val colL = x in 4.18f..4.62f; val colR = x in 6.18f..6.62f
            when {
                z < p1 && x in xa..xb -> {
                    // the predella: gold, a red panel either side
                    if ((x in 4.3f..4.95f || x in 5.85f..6.5f) && z in p0 + 0.45f..p1 - 0.45f) marbleR
                    else if (z > p1 - 0.35f) goldL else gold
                }
                z < c1 && (colL || colR) -> {
                    val cx = if (colL) 4.4f else 6.4f
                    val d = (x - cx) * 4.47f
                    when {
                        z < p1 + 0.8f || z > c1 - 1f -> if (d < -0.4f) goldHi else if (d > 0.5f) goldD else goldL // base, capital
                        d < -0.55f -> marbleRL
                        d > 0.6f -> marbleRD
                        // twisted: a light spiral round it
                        abs(((z * 0.55f + d * 0.4f) % 1.2f) - 0.6f) < 0.12f -> marbleRL
                        fine && Noise.v2(x * 20f, z * 0.8f, 22) > 0.72f -> Col.mix(marbleR, stoneHi, 0.25f)
                        else -> marbleR
                    }
                }
                z < c1 && x in 4.62f..6.18f -> if (x < 4.8f || x > 6.0f || z > c1 - 0.6f) (if (x < 4.7f || z > c1 - 0.3f) goldL else gold) else 0
                z in c1..e1 && x in xa - 0.05f..xb + 0.05f -> if (z > e1 - 0.4f) goldHi else if (z < c1 + 0.4f) goldD else goldL // the cornice
                z > e1 -> {
                    val d = hyp((x - xm) * 4.47f, z - sunZ)
                    val ang = kotlin.math.atan2(z - sunZ, (x - xm) * 4.47f)
                    val ray = abs(sin(ang * 8f))
                    // the angels on the cornice's ends
                    val angel = (abs(x - 4.4f) < 0.2f || abs(x - 6.4f) < 0.2f) && z < e1 + 3.2f
                    when {
                        angel -> {
                            val cx = if (x < xm) 4.4f else 6.4f
                            if (z > e1 + 2.2f) Col.hex(0xF0DCC0) else if ((x - cx) * (if (x < xm) 1f else -1f) > 0.05f && z > e1 + 1f) goldHi else linen
                        }
                        d < 1.1f -> if (fine && d < 0.6f) goldHi else linen
                        d < sunR -> goldL
                        d < sunR + 1.4f && ray > 0.55f && z > e1 + 0.3f -> if (ray > 0.85f) goldHi else gold
                        else -> 0
                    }
                }
                else -> 0
            }
        }
    }

    /**
     * The altarpiece: the Virgin in a blue mantle over a red dress, a halo round her head, the Child on her arm, against
     * a golden sky over a blue hill; in a gilt frame with a round head.
     */
    private fun painting() {
        val yp = y0 + 0.04f
        val xa = 4.8f; val xb = 6.0f; val za = mTop + 2.4f; val zb = fl + 21f
        val w = (xb - xa) * 4.47f; val r = w / 2f; val spring = zb - r
        onRight(yp, xa - 0.12f, xb + 0.12f, za - 0.5f, zb + 0.5f) { x, z ->
            val u = (x - xa) * 4.47f; val v = z - za
            val cu = u - r
            val inside = u in 0f..w && v >= 0f && (z <= spring || hyp(cu, z - spring) <= r)
            val frame = u in -0.55f..w + 0.55f && v >= -0.5f && (z <= spring || hyp(cu, z - spring) <= r + 0.5f)
            when {
                inside -> madonna(cu / r, v / (zb - za))
                frame -> if (cu < 0f) goldHi else goldL
                else -> 0
            }
        }
    }

    /** The painting at ([u] −1..1 across, [v] 0..1 up). */
    private fun madonna(u: Float, v: Float): Int {
        val skin = Col.hex(0xF0CCA8); val red = Col.hex(0xB83A30)
        val halo = hyp(u * 1.3f, (v - 0.72f) * 3.2f)
        val head = hyp(u * 1.9f, (v - 0.7f) * 5.2f)
        val child = hyp((u - 0.32f) * 2.6f, (v - 0.5f) * 6f)
        val cloak = v < 0.66f && abs(u) < 0.2f + (0.66f - v) * 1.05f
        val dress = v < 0.6f && abs(u) < 0.14f + (0.6f - v) * 0.25f
        return when {
            head < 0.5f -> if (fine && head < 0.18f && u > 0.05f) Col.hex(0xD8A888) else skin
            head < 0.72f && v > 0.7f -> mantleD // the veil round her face
            halo < 0.95f && v > 0.62f -> if (halo > 0.8f) goldHi else if (fine) goldL else goldHi
            child < 0.55f -> if (v > 0.52f) skin else linen
            dress -> red
            cloak -> if (u > 0.08f) mantleD else mantle
            v < 0.22f -> Col.hex(0x5A7A9A) // a blue hill far off
            v < 0.3f -> Col.hex(0xB8C8D0)
            else -> if (v > 0.85f) goldL else Col.mix(goldL, Col.hex(0xF8E8B8), (0.85f - v) * 1.6f)
        }
    }

    /** The presbytery: two steps of white stone with red and white tiles on top. */
    private fun steps() {
        block(s1x0, y0, s1x1, s1y, fl, fl + 0.8f, { _, z -> if (z > fl + 0.6f) stoneHi else stoneL }, { _, _ -> stoneM }, { x, y -> tile(x, y) })
        block(s2x0, y0, s2x1, s2y, fl + 0.8f, fl + 1.6f, { _, z -> if (z > fl + 1.4f) stoneHi else stoneL }, { _, _ -> stoneM }, { x, y -> tile(x, y) })
    }

    /** The presbytery's tiles: red and white diamonds. */
    private fun tile(x: Float, y: Float): Int {
        val a = (x + y) / 0.55f; val b = (x - y) / 0.55f
        val ia = floor(a).toInt(); val ib = floor(b).toInt()
        if (fine && (a - ia < 0.06f || b - ib < 0.06f)) return Col.hex(0x8A7E70)
        return if ((ia + ib) and 1 == 0) Col.hex(0xE8E0D2) else Col.hex(0xB0503E)
    }

    /**
     * The altar: a table of pale stone with a panel of red marble and a gold cross in front, a linen cloth over it hanging
     * down with lace, and the gilded tabernacle on it against the retable.
     */
    private fun altar() {
        val zb = fl + 1.6f
        val clothZ = mTop - 1.4f
        block(mx0, y0, mx1, my1, zb, mTop,
            { x, z ->
                val lace = z < clothZ + (if (fine) 0.35f + 0.3f * abs(sin((x - mx0) * 4.47f * 1.6f)) else 0.4f)
                when {
                    z > clothZ + 0.2f -> if (z > mTop - 0.3f) Col.hex(0xFFFFFF) else linen
                    z > clothZ -> if (lace) linenD else linen
                    x in mx0 + 0.25f..mx1 - 0.25f && z in zb + 0.8f..clothZ - 0.7f -> {
                        val cx = (x - (mx0 + mx1) / 2f) * 4.47f; val cz = z - (zb + clothZ) / 2f
                        if ((abs(cx) < 0.45f && abs(cz) < 1.7f) || (abs(cz - 0.5f) < 0.4f && abs(cx) < 1.3f)) goldL
                        else if (fine && Noise.v2(x * 12f, z * 0.6f, 24) > 0.7f) marbleRL else marbleR
                    }
                    else -> if (fine && Noise.v2(x * 9f, z * 0.5f, 25) > 0.74f) stoneM else stoneHi
                }
            },
            { _, z -> if (z > clothZ) linenD else stoneL },
            { _, _ -> linen })
        // the tabernacle
        block(5.05f, y0 + 0.02f, 5.75f, y0 + 0.62f, mTop, mTop + 2.8f,
            { x, z ->
                val d = (x - 5.4f) * 4.47f
                when {
                    abs(d) < 0.9f && z < mTop + 2.2f && z > mTop + 0.3f -> if (abs(d) < 0.2f && z in mTop + 0.8f..mTop + 1.9f || abs(z - (mTop + 1.5f)) < 0.2f && abs(d) < 0.5f) goldHi else goldD
                    else -> goldL
                }
            },
            { _, _ -> gold }, { _, _ -> goldHi })
        val tx = iso.sx(5.4f, y0 + 0.32f); val ty = iso.sy(5.4f, y0 + 0.32f, mTop + 2.8f)
        c.fillEllipse(tx, ty - 0.4f * K, 1.4f * K, 0.9f * K, goldL)
        c.fillRect(floor(tx).toInt() - detail / 2, floor(ty - 2.3f * K).toInt(), detail, (1.6f * K).toInt(), goldL)
    }

    /** The four candles on the altar in brass candlesticks, the flames flickering; which burn, see [candleLit]. */
    private fun candles() {
        val xs = floatArrayOf(4.62f, 4.98f, 5.82f, 6.18f)
        val y = 1.75f
        val pk = poked("candles")
        val draught = if (pk != null && pk.step == 0) PokeArt.ease(pk.age(t), 0f, 0.2f) * (1f - PokeArt.ease(pk.age(t), 1.1f, 1.4f)) else 0f
        for ((i, x) in xs.withIndex()) {
            val tall = if (i == 0 || i == 3) 4.6f else 5.4f
            val bx = iso.ix(x, y); val by = iso.iy(x, y, mTop)
            val stemTop = by - floor(tall * K).toInt()
            // the foot, the stem, the drip pan
            c.fillRect(bx - K, by - detail, 2 * K + detail, detail, goldD)
            c.fillRect(bx - K / 2, by - 2 * detail, K + detail, detail, gold)
            c.fillRect(bx, stemTop, detail, by - 2 * detail - stemTop, goldL)
            if (fine) c.fillRect(bx + detail, stemTop, 1, by - 2 * detail - stemTop, goldD)
            c.fillRect(bx - K / 2, stemTop, K + detail, detail, gold)
            // the candle
            val ch = floor(3.6f * K).toInt()
            c.fillRect(bx, stemTop - ch, detail, ch, Col.hex(0xF6F0DC))
            if (fine) c.fillRect(bx, stemTop - ch, 1, ch, Col.hex(0xFFFFFF))
            c.set(bx, stemTop - ch - 1, Col.hex(0x3A3028))
            if (!candleLit(i)) continue
            // the flame, swaying and leaning in the draught
            val flick = sin(t * 12 + i * 1.7).toFloat() * 0.5f + sin(t * 7.3 + i).toFloat() * 0.5f
            val lean = (-1.6f * draught + flick * 0.35f * draught) * K
            val fh = (1.9f + 0.25f * flick - 0.6f * draught) * K
            val fx = bx + detail / 2f + lean * 0.5f; val fy = stemTop - ch - 1f
            glow {
                c.fillEllipse(fx, fy - fh * 0.45f, 0.7f * K, fh * 0.55f, Pal.FLAME[2])
                c.fillEllipse(fx, fy - fh * 0.35f, 0.42f * K, fh * 0.36f, Pal.FLAME[1])
                c.fillEllipse(fx, fy - fh * 0.2f, 0.25f * K, fh * 0.18f, Pal.FLAME[0])
            }
        }
    }

    /** The flames' light, and the smoke of the one the draught puts out. */
    private fun candleFlicker() {
        val pk = poked("candles") ?: return
        if (pk.step != 1) return
        val a = pk.age(t)
        val x = 5.82f; val bx = iso.sx(x, 1.75f); val by = iso.sy(x, 1.75f, mTop) - 5.4f * K - 3.6f * K
        // a curl of smoke as it goes out, a spark as it catches again
        if (a in 0.45f..2.2f) wisps(bx, by, 0.6f, 31)
        if (a in 1.6f..2.3f) s.fx { PokeArt.sparks(c, bx, by - K, (a - 1.6f) / 0.7f, 5, 33, 3f * K, 3f * K, detail, fall = 1.5f) }
    }

    /** The two vases of flowers either side of the altar, as the season has them (white lilies at Easter). */
    private fun flowers() {
        for (x in floatArrayOf(3.85f, 6.95f)) {
            val y = 2.55f
            val bx = iso.sx(x, y); val by = iso.sy(x, y, fl + 1.6f)
            // the vase: white porcelain with a gold band
            c.fillRect(floor(bx - 1.1f * K).toInt(), floor(by - 3.2f * K).toInt(), floor(2.2f * K).toInt(), floor(3.2f * K).toInt(), linen)
            c.fillRect(floor(bx + 0.4f * K).toInt(), floor(by - 3.2f * K).toInt(), floor(0.7f * K).toInt(), floor(3.2f * K).toInt(), linenD)
            c.fillRect(floor(bx - 1.1f * K).toInt(), floor(by - 2.2f * K).toInt(), floor(2.2f * K).toInt(), detail, gold)
            c.fillRect(floor(bx - 1.4f * K).toInt(), floor(by - 3.6f * K).toInt(), floor(2.8f * K).toInt(), detail, linenD)
            bouquet(bx, by - 3.6f * K, if (x < 5f) 61 else 67)
        }
    }

    /** A bouquet over ([cx], [cy]): stems fanning out of the vase, leaves, then blooms coloured by the season or the feast. */
    private fun bouquet(cx: Float, cy: Float, seed: Int) {
        val leaf = Col.hex(0x3E7A3A); val leafD = Col.hex(0x2A5A2A); val leafL = Col.hex(0x5E9A4A)
        for (k in -2..2) {
            val ex = cx + k * 1.2f * K; val ey = cy - (3.4f - abs(k) * 0.5f) * K
            c.line(floor(cx).toInt(), floor(cy).toInt(), floor(ex).toInt(), floor(ey).toInt(), leafD)
            c.fillEllipse((cx + ex) / 2f + k * 0.3f * K, (cy + ey) / 2f, 0.7f * K, 0.45f * K, if (k % 2 == 0) leaf else leafL)
        }
        val (a, b) = when {
            easter -> Col.hex(0xFAFAF2) to Col.hex(0xF2D65A)
            nativity -> Col.hex(0xD8282E) to Col.hex(0xF4F0E4)
            frame.month in 3..5 -> Col.hex(0xF6E04A) to Col.hex(0xF8F6EC)
            frame.month in 6..8 -> Col.hex(0xE0344A) to Col.hex(0xF8F2F4)
            frame.month in 9..11 -> Col.hex(0xE8942A) to Col.hex(0xF2C43A)
            else -> Col.hex(0xC8243A) to Col.hex(0xF4F0E4)
        }
        // the blooms on a dome over the stems, a bright heart in each closer up
        for (j in 0 until 7) {
            val ang = (j / 6f - 0.5f) * 2.3f + (Noise.rnd(j, seed) - 0.5f) * 0.25f
            val r = 2.5f + Noise.rnd(j, seed + 1) * 0.6f
            val fx = cx + sin(ang) * r * K; val fy = cy - 2.2f * K - cos(ang) * r * 0.85f * K
            val col = if (j % 3 == 1) b else a
            c.fillEllipse(fx, fy, 0.95f * K, 0.8f * K, col)
            c.fillEllipse(fx + 0.3f * K, fy + 0.25f * K, 0.5f * K, 0.4f * K, Col.scale(col, 0.82f))
            if (fine) c.fillRect(floor(fx - 0.2f * K).toInt(), floor(fy - 0.2f * K).toInt(), detail, detail, if (col == b && !easter) Col.hex(0xF2C43A) else Col.mix(col, Col.hex(0xFFF6A0), 0.6f))
        }
    }

    /** The Virgin on a pedestal of white stone to the right of the altar, a pot of flowers at her feet. */
    private fun statue() {
        block(8.25f, 1.45f, 8.95f, 2.15f, fl, fl + 6.2f, { _, z -> if (z > fl + 5.8f) stoneHi else stoneL }, { _, _ -> stoneM }, { _, _ -> stoneHi })
        val bx = iso.sx(8.6f, 1.8f); val by = iso.sy(8.6f, 1.8f, fl + 6.2f)
        val skin = Col.hex(0xF0CCA8)
        // the white dress, the blue mantle open over it with a gold hem, the hands at her breast, her face in a white veil,
        // a crown of stars
        c.polyBegin(); c.polyAdd(bx - 2.2f * K, by); c.polyAdd(bx + 2.2f * K, by); c.polyAdd(bx + 1.3f * K, by - 7.2f * K); c.polyAdd(bx - 1.3f * K, by - 7.2f * K); c.polyFill(mantle)
        c.polyBegin(); c.polyAdd(bx + 0.7f * K, by); c.polyAdd(bx + 2.2f * K, by); c.polyAdd(bx + 1.3f * K, by - 7.2f * K); c.polyAdd(bx + 0.8f * K, by - 7.2f * K); c.polyFill(mantleD)
        c.polyBegin(); c.polyAdd(bx - 0.9f * K, by); c.polyAdd(bx + 0.9f * K, by); c.polyAdd(bx + 0.5f * K, by - 6.6f * K); c.polyAdd(bx - 0.5f * K, by - 6.6f * K); c.polyFill(linen)
        c.fillRect(floor(bx - 2.2f * K).toInt(), floor(by - 0.6f * K).toInt(), floor(4.4f * K).toInt(), max(1, K / 2), goldL)
        c.fillEllipse(bx, by - 5.6f * K, 0.7f * K, 0.6f * K, skin)
        c.fillEllipse(bx, by - 8.4f * K, 1.35f * K, 1.5f * K, linen)
        c.fillEllipse(bx + 0.1f * K, by - 8.2f * K, 0.9f * K, 1.05f * K, skin)
        c.fillRect(floor(bx - 0.4f * K).toInt(), floor(by - 8.4f * K).toInt(), detail, detail, Col.hex(0x3A2A2A))
        c.fillRect(floor(bx + 0.4f * K).toInt(), floor(by - 8.4f * K).toInt(), detail, detail, Col.hex(0x3A2A2A))
        for (k in 0 until 5) c.fillRect(floor(bx + (k - 2) * 0.8f * K).toInt(), floor(by - 10.3f * K - (if (k % 2 == 0) 0f else 0.5f * K)).toInt(), detail, detail, goldL)
        // flowers at the foot
        val fx = iso.sx(8.55f, 2.45f); val fy = iso.sy(8.55f, 2.45f, fl)
        c.fillRect(floor(fx - 0.8f * K).toInt(), floor(fy - 1.6f * K).toInt(), floor(1.6f * K).toInt(), floor(1.6f * K).toInt(), Col.hex(0xA8583A))
        bouquet(fx, fy - 1.2f * K, 71)
    }

    /**
     * The nativity scene (jaslice) to the right of the altar in December and January: a stable of boards and bark on a
     * bed of moss, the Holy Family at the manger, the ox and the donkey, two sheep, the star over the roof.
     */
    private fun jaslice() {
        val xa = 7.9f; val xb = 9.4f; val ya = 1.15f; val yb = 2.9f
        val moss = Col.hex(0x4E7A36); val mossL = Col.hex(0x6E9A48)
        block(xa, ya, xb, yb, fl, fl + 0.7f, { _, _ -> Col.hex(0x5A3E26) }, { _, _ -> Col.hex(0x4A321E) },
            { x, y -> if (Noise.rnd((x * 12).toInt(), (y * 12).toInt(), 26) < 0.35f) mossL else moss })
        // the stable's back and its roof
        onRight(ya + 0.2f, xa + 0.2f, xb - 0.2f, fl + 0.7f, fl + 7f) { x, _ -> if (((x - xa) / 0.2f).toInt() % 2 == 0) woodM else woodD }
        quadFill(xa + 0.05f, ya + 0.1f, fl + 7.8f, xb - 0.05f, ya + 0.1f, fl + 7.8f, xb - 0.05f, ya + 1.3f, fl + 6.2f, xa + 0.05f, ya + 1.3f, fl + 6.2f) { px, py ->
            if (Noise.rnd(px / (2 * detail), py / detail, 27) < 0.4f) Col.hex(0x6A4A30) else Col.hex(0x8A6440)
        }
        for (x in floatArrayOf(xa + 0.15f, xb - 0.15f)) post(x, ya + 1.2f, fl + 0.7f, fl + 6.3f, woodL, woodM, woodD)
        val base = fl + 0.7f
        fun fig(x: Float, y: Float, body: Int, head: Int, h: Float, w: Float = 0.9f) {
            val bx = iso.sx(x, y); val by = iso.sy(x, y, base)
            c.fillRect(floor(bx - w * K).toInt(), floor(by - h * K).toInt(), floor(2 * w * K).toInt(), floor(h * K).toInt(), body)
            c.fillEllipse(bx, by - (h + 0.7f) * K, 0.8f * K, 0.8f * K, head)
        }
        val skin = Col.hex(0xF0CCA8)
        // ox and donkey at the back
        c.fillEllipse(iso.sx(8.2f, 1.6f), iso.sy(8.2f, 1.6f, base) - 1.6f * K, 1.8f * K, 1.2f * K, Col.hex(0x8A5A34))
        c.fillEllipse(iso.sx(9.1f, 1.6f), iso.sy(9.1f, 1.6f, base) - 1.6f * K, 1.6f * K, 1.1f * K, Col.hex(0x8C8A88))
        fig(8.3f, 2.2f, mantle, skin, 3.6f)
        fig(9.0f, 2.2f, Col.hex(0x8A5A32), skin, 4.2f)
        // the manger with the Child
        val mx = iso.sx(8.65f, 2.45f); val my = iso.sy(8.65f, 2.45f, base)
        c.fillRect(floor(mx - 1.2f * K).toInt(), floor(my - 1.4f * K).toInt(), floor(2.4f * K).toInt(), floor(1.4f * K).toInt(), woodL)
        c.fillRect(floor(mx - 1f * K).toInt(), floor(my - 1.9f * K).toInt(), floor(2f * K).toInt(), floor(0.6f * K).toInt(), Pal.HAY_L)
        c.fillEllipse(mx, my - 2f * K, 0.8f * K, 0.5f * K, linen)
        // sheep in front
        for ((x, y) in listOf(8.0f to 2.75f, 9.25f to 2.7f)) {
            val sx = iso.sx(x, y); val sy = iso.sy(x, y, base)
            c.fillEllipse(sx, sy - 1f * K, 1.1f * K, 0.75f * K, Col.hex(0xF2EEE4))
            c.fillRect(floor(sx + 0.7f * K).toInt(), floor(sy - 1.5f * K).toInt(), detail, detail, Col.hex(0x3A3432))
        }
        // the star over the roof: eight points and a tail
        val stx = iso.sx(8.65f, 1.5f); val sty = iso.sy(8.65f, 1.5f, fl + 11f)
        glow {
            val tw = 0.85f + 0.15f * sin(t * 3).toFloat()
            val r = 1.8f * K * tw
            for (py in floor(sty - r).toInt()..floor(sty + r).toInt()) for (px in floor(stx - r).toInt()..floor(stx + r).toInt()) {
                val dx = abs(px + 0.5f - stx); val dy = abs(py + 0.5f - sty)
                val four = (dx < 0.45f * K * (1f - dy / r) || dy < 0.45f * K * (1f - dx / r)) && max(dx, dy) < r
                val diag = abs(dx - dy) < 0.3f * K && dx + dy < r * 0.8f
                if (hyp(dx, dy) < 0.6f * K || four || diag) c.set(px, py, if (hyp(dx, dy) < 0.5f * K) Col.hex(0xFFFFFF) else goldHi)
            }
            for (k in 1..(4 * K)) c.blend(floor(stx + k * 0.8f).toInt(), floor(sty + k * 0.45f).toInt(), goldL, 0.9f - k / (5f * K))
        }
        s.light(stx, sty, 14f, 0.35f)
    }

    /** The red sanctuary lamp on its chain by the altar, always burning. */
    private fun lamp() {
        val x = 3.05f; val y = 3.05f
        val top = fl + 28.5f; val lz = fl + 20.2f
        rod(x, y, top, x, y, lz + 1.6f, goldD, max(1, detail / 2))
        val bx = iso.sx(x, y); val by = iso.sy(x, y, lz)
        c.fillEllipse(bx, by, 1.3f * K, 0.9f * K, gold)
        c.fillEllipse(bx - 0.3f * K, by - 0.2f * K, 0.6f * K, 0.4f * K, goldHi)
        c.fillRect(floor(bx - 0.5f * K).toInt(), floor(by + 0.6f * K).toInt(), K, K, goldD)
        glow {
            val fl2 = 0.8f + 0.2f * sin(t * 6.3).toFloat()
            c.fillEllipse(bx, by - 1.1f * K, 0.8f * K, 0.9f * K, Col.hex(0xC8202A))
            c.fillEllipse(bx, by - 1.3f * K, 0.35f * K, 0.5f * K * fl2, Col.hex(0xFF9A6A))
        }
    }

    /** The žegen at Easter: baskets before the altar under embroidered cloths, red eggs and a potica peeking out. */
    private fun baskets() {
        for ((j, x) in floatArrayOf(3.45f, 4.25f, 6.45f, 7.25f).withIndex()) {
            val bx = iso.sx(x, 3.6f); val by = iso.sy(x, 3.6f, fl)
            c.fillEllipse(bx, by - 1.2f * K, 1.8f * K, 1.2f * K, Col.hex(0x9A6A36))
            c.fillEllipse(bx, by - 2f * K, 1.8f * K, 0.7f * K, Col.hex(0xB88448))
            if (fine) for (k in -1..1) c.hline(floor(bx - 1.5f * K).toInt(), floor(bx + 1.5f * K).toInt(), floor(by - 1.2f * K + k * 0.5f * K).toInt(), Col.hex(0x7A5028))
            // the handle
            for (k in 0..8) { val ang = PI.toFloat() * k / 8f; c.fillRect(floor(bx + cos(ang) * 1.5f * K).toInt(), floor(by - 2f * K - sin(ang) * 2.2f * K).toInt(), detail, detail, Col.hex(0x8A5A2C)) }
            // the cloth, and the eggs
            c.fillEllipse(bx - 0.5f * K, by - 2.3f * K, 1.2f * K, 0.6f * K, linen)
            if (fine) c.set(floor(bx - 0.5f * K).toInt(), floor(by - 2.3f * K).toInt(), redP)
            val egg = listOf(Col.hex(0xC82828), Col.hex(0xE0A020), Col.hex(0x3A6AC0), Col.hex(0x2E8A48))[j]
            c.fillEllipse(bx + 0.8f * K, by - 2.4f * K, 0.5f * K, 0.6f * K, egg)
            c.fillEllipse(bx + 0.2f * K, by - 2.6f * K, 0.45f * K, 0.55f * K, Col.hex(0xC82828))
        }
    }

    // ------------------------------------------------------------------ the pews

    /** The pews in rows either side of the runner, seen from behind: their backs, the scrolled ends, the kneelers. */
    private fun pews() {
        val n = max(rowsL.size, rowsR.size)
        for (i in 0 until n) {
            if (i < rowsL.size) pew(if (i == rowsL.lastIndex) 3.1f else lx0, lx1, rowsL[i])
            if (i < rowsR.size) pew(rx0, rx1, rowsR[i])
        }
    }

    private fun pew(xa: Float, xb: Float, yr: Float) {
        val seat = fl + 3.2f; val back = fl + 7f; val end = fl + 7.6f
        val by0 = yr + 0.5f; val by1 = yr + 0.66f
        // the left end's inner side (the backrest hides all but its scroll), then the seat
        endSide(xa + 0.1f, yr, by1, end)
        onFlat(seat, xa, xb, yr, by0) { _, _ -> woodL }
        // the backrest: panels of walnut framed in a darker moulding
        onRight(by1, xa, xb, seat - 0.6f, back) { x, z ->
            val panel = ((x - xa) / 0.7f); val fp = panel - floor(panel)
            when {
                z > back - 0.4f -> if (fine && z > back - 0.15f) woodL else woodM
                z < seat - 0.2f -> woodX
                fp < 0.06f || z < seat + 0.35f || z > back - 0.8f -> woodD
                fine && abs(fp - 0.5f) < 0.04f -> Col.mix(woodM, woodD, 0.5f)
                else -> woodM
            }
        }
        onFlat(back, xa, xb, by0, by1) { _, _ -> woodL }
        // the ends' front edges, the right end's outer side
        for (x in floatArrayOf(xa, xb - 0.1f)) onRight(by1 + 0.02f, x, x + 0.1f, fl, end) { _, z -> if (z > end - 0.5f) woodL else woodM }
        endSide(xb, yr, by1, end)
        // the kneeler for the row behind
        block(xa + 0.15f, by1, xb - 0.15f, by1 + 0.28f, fl + 0.6f, fl + 1.4f, { _, _ -> woodD }, { _, _ -> woodX }, { _, _ -> Col.hex(0x6A4428) })
    }

    /** A pew's end board seen from the side (the plane x = [x]), scrolled at the top, a carved ring closer up. */
    private fun endSide(x: Float, yr: Float, by1: Float, end: Float) {
        onLeft(x, yr - 0.05f, by1 + 0.02f, fl, end + 0.6f) { y, z ->
            val u = (y - (yr - 0.05f)) / (by1 + 0.07f - yr)
            val top = end - 0.8f + 1.4f * sin(u * PI.toFloat()) * (1f - u * 0.3f)
            when {
                z > top -> 0
                z > top - 0.35f -> woodL
                fine && abs(hyp((u - 0.5f) * 3f, (z - (top - 1.6f)) * 0.9f) - 0.6f) < 0.09f -> woodX
                else -> woodD
            }
        }
    }

    // ------------------------------------------------------------------ the loft, the organ, the rope

    /** The loft's floor over the door: a beam along its front, carried on two turned posts. */
    private fun loftFloor() {
        for (y in floatArrayOf(yl + 0.12f, y1 - 0.12f)) {
            val px = iso.ix(xl - 0.12f, y); val top = iso.iy(xl - 0.12f, y, zl - 1.2f); val bot = iso.iy(xl - 0.12f, y, fl)
            for (dx in 0 until 2 * detail) c.vline(px - detail + dx, top, bot, if (dx < detail / 2 + 1) woodL else if (dx >= 2 * detail - detail / 2 - 1) woodX else woodM)
            // turned: rings along it
            var z = fl + 2f
            while (z < zl - 2f) { c.fillRect(px - detail - (detail + 1) / 2, iso.iy(xl - 0.12f, y, z), 3 * detail, detail, woodD); z += 3.4f }
        }
        block(x0, yl, xl, y1, zl - 1.2f, zl, { _, _ -> woodM }, { _, z -> if (z > zl - 0.35f) woodL else woodD }, { _, _ -> woodL })
    }

    /**
     * The organ on the loft against the wall: a painted case with three towers of tin pipes, the middle one tallest,
     * gilded carving on each; the pipes' mouths in a row.
     */
    private fun organ() {
        val xf = x0 + 0.6f
        val body = fl + 22.4f
        val fields = listOf(Triple(8.05f, 8.75f, fl + 26.4f), Triple(8.8f, 9.5f, fl + 28f), Triple(9.55f, 10.25f, fl + 26.4f))
        val caseC = Col.hex(0x5E7A6A); val caseD = Col.hex(0x44584C); val caseL = Col.hex(0x7E9A88)
        // the case: its end toward us, its front with a gilt band on top
        onRight(10.3f, x0, xf, zl, body) { _, _ -> caseD }
        onLeft(xf, 8.0f, 10.3f, zl, body) { y, z -> if (z > body - 0.6f) goldL else if (abs(y - 9.15f) < 0.9f && z > zl + 1f && z < body - 1.2f) caseL else caseC }
        for ((ya, yb, top) in fields) {
            onLeft(xf + 0.05f, ya, yb, body, top + 1.6f) { y, z ->
                val u = (y - ya) / (yb - ya)
                // the pipes stand in a V: long at the sides, short in the middle
                val len = top - 1.6f * sin(u * PI.toFloat())
                val pipe = (u * 7f); val fp = pipe - floor(pipe)
                val mouth = body + 1f
                when {
                    z > top -> if (z < top + 1.6f && abs(u - 0.5f) < 0.45f - (z - top) * 0.2f) (if (fine && Noise.rnd((u * 30).toInt(), (z * 3).toInt(), 28) < 0.3f) goldHi else goldL) else 0
                    z > len -> 0
                    fp < 0.14f -> Col.hex(0x5A5E66)
                    abs(z - mouth) < 0.4f && fp in 0.3f..0.75f -> Col.hex(0x2A2C32)
                    fp < 0.38f -> Col.hex(0xE6EAF0)
                    fp > 0.78f -> Col.hex(0x8C929C)
                    else -> Col.hex(0xB8BEC8)
                }
            }
        }
    }

    /** The loft's balustrade: panels painted blue-green with a red tulip each, in ochre frames, a rail on top. */
    private fun balustrade() {
        val zt = zl + 4.2f
        onLeft(xl, yl, y1, zl, zt) { y, z ->
            val panel = (y - yl) / 0.68f; val ip = floor(panel).toInt(); val fp = panel - ip
            val v = (z - zl) / (zt - zl)
            when {
                v > 0.86f -> if (v > 0.95f) woodL else woodM
                v < 0.1f -> woodD
                fp < 0.1f || fp > 0.9f || v < 0.18f || v > 0.8f -> ochre
                else -> {
                    val cu = (fp - 0.5f) * 0.68f * 4.47f; val cv = z - (zl + 2.1f)
                    val petal = hyp(cu * 1.2f, (cv - 0.3f) * 1.3f) < 0.9f && cv > -0.3f
                    val cup = cv > 0.4f && abs(cu) < 0.6f && abs(cu) > 0.2f
                    val stem = abs(cu) < 0.18f && cv < -0.1f
                    when {
                        petal || cup -> if (ip % 2 == 0) redP else Col.hex(0xD8B040)
                        stem -> Col.hex(0x3E7A3A)
                        fine && Noise.rnd((y * 40).toInt(), (z * 4).toInt(), 29) < 0.03f -> Col.hex(0xF2EEE0)
                        else -> Col.hex(0x3E6E86)
                    }
                }
            }
        }
        onFlat(zt, xl - 0.12f, xl + 0.05f, yl, y1) { _, _ -> woodL }
        onRight(y1, xl - 0.12f, xl, zl, zt) { _, _ -> woodM }
    }

    /**
     * The bell rope hanging from the loft by the door, twisted, a red, white and blue woollen grip at hand height and a
     * tassel; it swings when tapped and goes up and down while the bell rings.
     */
    private fun rope() {
        val lv = bellLevel()
        val pull = max(0f, sin(t * 4.2 - 0.6).toFloat()) * 2.4f * lv
        val pk = poked("rope")
        val sway = if (pk == null) 0f else sin(pk.age(t) * 5.5f) * 2.2f * (1f - PokeArt.ease(pk.age(t), 0f, if (pk.step == 0) 2.6f else 3.6f)) * K
        val topX = iso.sx(ropeX, ropeY); val topY = iso.sy(ropeX, ropeY, zl - 1.2f)
        val bottom = fl + 4.2f + pull
        val botY = iso.sy(ropeX, ropeY, bottom)
        val span = botY - topY
        val th = detail
        for (py in floor(topY).toInt()..floor(botY).toInt()) {
            val u = ((py - topY) / span).coerceIn(0f, 1f)
            val px = floor(topX + sway * u * u).toInt()
            val z = (bottom + (zl - 1.2f - bottom) * (1f - u))
            val grip = z < fl + 8.4f + pull && z > fl + 5f + pull
            val col = if (grip) {
                val band = floor((z - fl - pull) / 0.55f).toInt() % 3
                when (band) { 0 -> Col.hex(0xC42A30); 1 -> Col.hex(0xF4F0E6); else -> Col.hex(0x2C50A8) }
            } else if (((py / max(1, detail)) + (if (fine) 0 else 0)) % 3 == 0) Col.hex(0xA88A5A) else Col.hex(0xD2B888)
            c.fillRect(px, py, th + (if (grip) detail else 0), 1, col)
        }
        // the tassel
        val tx = floor(topX + sway).toInt(); val ty = floor(botY).toInt()
        c.fillRect(tx - detail / 2, ty, 2 * detail, 2 * detail, Col.hex(0xC42A30))
    }

    // ------------------------------------------------------------------ effects and light

    /** The bell's sound going out from the gable while it rings. */
    private fun bellRings() {
        val lv = bellLevel()
        if (lv <= 0.05f) return
        val bx = iso.sx(x0 - th / 2f, gm); val by = iso.sy(x0 - th / 2f, gm, wallH + 5.5f)
        s.fx {
            for (i in 0 until 3) {
                val u = ((t * 0.8 + i / 3.0) % 1.0).toFloat()
                PokeArt.rings(c, bx, by, u, 16f * K, Pal.GOLD_L, max(1, detail / 2), 0.85f * lv, n = 1)
            }
        }
    }

    /** The organ playing: notes rising from the pipes and drifting off, the pipes' tops shimmering. */
    private fun organNotes(level: Float) {
        if (level <= 0.03f) return
        val bx = iso.sx(x0 + 0.6f, 9.15f); val by = iso.sy(x0 + 0.6f, 9.15f, fl + 29f)
        s.fx {
            c.penEmissive = true
            val n = 5
            for (j in 0 until n) {
                val ph = ((t * 0.45 + j.toFloat() / n) % 1.0).toFloat()
                val a = level * (1f - ph) * (if (ph < 0.1f) ph / 0.1f else 1f)
                val x = bx + (Noise.rnd(j, 61) - 0.3f) * 12f * K + ph * 14f * K + sin(ph * 6f + j).toFloat() * 2f * K
                val y = by - ph * 16f * K
                note(floor(x).toInt(), floor(y).toInt(), a, j % 2 == 0)
            }
            c.penEmissive = false
        }
    }

    /** A note (a quaver, or two beamed) with its head at ([x], [y]), faded to [a]. */
    private fun note(x: Int, y: Int, a: Float, two: Boolean) {
        val col = Col.hex(0xFFF2C0)
        val d = detail
        fun p(dx: Int, dy: Int) { for (yy in 0 until d) for (xx in 0 until d) c.blend(x + dx * d + xx, y + dy * d + yy, col, a) }
        p(0, 0); p(1, 0); p(0, 1); p(1, 1)
        for (k in 1..4) p(1, -k)
        if (two) { p(4, -1); p(5, -1); p(4, 0); p(5, 0); for (k in 1..5) p(5, -1 - k); p(2, -4); p(3, -4); p(4, -5) } else { p(2, -3); p(3, -2) }
    }

    private fun lights() {
        val xs = floatArrayOf(4.62f, 4.98f, 5.82f, 6.18f)
        for ((i, x) in xs.withIndex()) if (candleLit(i)) s.light(iso.sx(x, 1.75f), iso.sy(x, 1.75f, mTop + 8f), 20f, 0.42f)
        s.light(iso.sx(3.05f, 3.05f), iso.sy(3.05f, 3.05f, fl + 19f), 12f, 0.4f)
        if (nativity) s.light(iso.sx(8.65f, 2f), iso.sy(8.65f, 2f, fl + 4f), 12f, 0.3f)
    }

    companion object {
        /** The sparrow on the window's sill: no word, a poke of its own. */
        const val SPARROW = "sparrow"
    }
}
