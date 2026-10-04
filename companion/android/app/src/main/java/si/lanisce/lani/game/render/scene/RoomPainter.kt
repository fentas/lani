package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Moon
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.Season
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.Poke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A room of a farmhouse as a diorama (the kitchen, the living room, the workshop, the wine cellar; companion/SCENES.md,
 * "Houses and their rooms"): a room on its own plot of grass with its two front walls cut down to a stub so we look in.
 * What every room shares: the plot and its garden (the stepping stones, a bench by the wall, the geraniums, the
 * vegetable bed and the hen), the whitewashed walls that grow with the room's level (plain lime at level 1, a painted
 * dado from 2, a stencilled border from 3), the floor (beaten earth at 1, terracotta tiles from 2), the window with its
 * view and the gingham curtains, the plank door with the horseshoe over it, and the furniture more than one room has
 * (the clock, the petroleum lamp, the tiled stove's tiles, the chairs with hearts in their backs, the holy corner). Each
 * room paints itself from these ([paint]); the kitchen ([KitchenPainter]) is the first of them.
 */
internal abstract class RoomPainter : DioramaPainter() {
    override val plotW = 12f
    override val plotD = 12f
    override val rise = 26f

    // the room: the back walls' inner faces at x = X0 (left) and y = Y0 (right), the front walls cut down to a stub
    // at X1 and Y1; the walls TK thick and HW high, the tiled floor FL above the grass, the stubs HS high
    protected val x0 = 1f; protected val y0 = 1f; protected val x1 = 9f; protected val y1 = 9f
    protected val tk = 0.45f; protected val hw = 27f; protected val fl = 2f; protected val hs = 5f

    // on the right wall: the stove in the corner, the window over the bench, the door
    protected val stoveX = 3.3f; protected val stoveY = 2.3f; protected val stoveZ = fl + 7.6f
    // the bench from the stove's warm side under the window, where the grandmother sleeps; the jugs on the cupboard's counter
    protected val benchA = 3.4f; protected val benchB = 6.4f
    protected val counterZ = fl + 9.3f
    protected open val winA = 4.1f; protected open val winB = 5.95f; protected open val winZ0 = fl + 10f; protected open val winZ1 = fl + 20f
    protected open val doorA = 6.95f; protected open val doorB = 8.3f; protected open val doorZ = fl + 19.5f
    // on the left wall: the clock and the cupboard
    protected open val clockY = 3.3f
    protected val cupA = 4.4f; protected val cupB = 6.7f
    // the table, and the lamp over it
    protected val ta = 3.0f; protected val tb = 7.0f; protected val tc = 5.2f; protected val td = 7.5f; protected val tz = fl + 7f
    protected open val lampX = (ta + tb) / 2f; protected open val lampY = (tc + td) / 2f
    // outside: the hen scratching in the grass
    protected val henX = 7.6f; protected val henY = 10.75f
    // level 1: the hearth in the corner out to (HX1, HY1), its top at HZ, the fire (and the pot on its trivet) at (FIRE_X,
    // FIRE_Y); the straw bed along the left wall from BED_Y0 to BED_Y1
    protected val hx1 = 3.2f; protected val hy1 = 2.4f; protected val hz = fl + 4.6f
    protected val fireX = 2.0f; protected val fireY = 1.68f; protected val trivetZ = hz + 2.6f
    protected val bedY0 = 4.75f; protected val bedY1 = 7.45f; protected val bedX1 = x0 + 1.5f; protected val bedZ = fl + 2.3f

    /** The room's level as it shows (1 … 3; its building's, see [SceneWorld.hereLevel]), set as each frame is painted. */
    protected var lv = 3

    protected val lime = Col.hex(0xEDE5D2); protected val limeL = Col.hex(0xFAF6EA); protected val limeD = Col.hex(0xD9CFB9)
    protected val dado = Col.hex(0x93A98F); protected val dadoD = Col.hex(0x7C927A)
    protected val border = Col.hex(0xB0503C); protected val leafC = Col.hex(0x5E8A4A)
    protected val iron = Col.hex(0x34303A); protected val ironL = Col.hex(0x5E5A64); protected val ironD = Col.hex(0x1E1A20)
    protected val tileL = Col.hex(0x9ACAA6); protected val tileM = Col.hex(0x6FA282); protected val tileD = Col.hex(0x4F8064); protected val grout = Col.hex(0xE6E0CA)
    protected val china = Col.hex(0xF7F5EE); protected val chinaD = Col.hex(0xD2CEC2); protected val chinaBlue = Col.hex(0x3E6FB0)
    protected val paintL = Col.hex(0x7C9EC4); protected val paintM = Col.hex(0x5A7EA8); protected val paintD = Col.hex(0x3E5C82)
    protected val brass = Col.hex(0xD9A441); protected val brassL = Col.hex(0xF6D27A); protected val brassD = Col.hex(0x96692A)
    protected val red = Col.hex(0xD23A32); protected val pink = Col.hex(0xF0A8A0); protected val linen = Col.hex(0xFAF4EA)

    protected var floorId = 0
    protected var rugId = 0

    override fun ambient(): FloatArray {
        // the room keeps the stove's warmth: a shade warmer at night
        val k = env.dark
        return floatArrayOf(env.ambR + k * 0.1f, env.ambG + k * 0.07f, env.ambB + k * 0.03f)
    }

    // ------------------------------------------------------------------ helpers

    /** The part of the plane x = [xw] over y [ya]..[yb], z [za]..[zb]; [shader] (y, z, px, py) gives a colour, or 0 to leave the pixel. */
    protected inline fun onX(xw: Float, ya: Float, yb: Float, za: Float, zb: Float, crossinline shader: (Float, Float, Int, Int) -> Int) {
        c.polyBegin()
        c.polyAdd(iso.sx(xw, ya), iso.sy(xw, ya, za)); c.polyAdd(iso.sx(xw, yb), iso.sy(xw, yb, za))
        c.polyAdd(iso.sx(xw, yb), iso.sy(xw, yb, zb)); c.polyAdd(iso.sx(xw, ya), iso.sy(xw, ya, zb))
        c.polyScan { px, py -> val col = shader(wallXy(px, xw), wallXz(px, py, xw), px, py); if (col != 0) c.set(px, py, col) }
    }

    /** The part of the plane y = [yw] over x [xa]..[xb], z [za]..[zb]; [shader] (x, z, px, py) gives a colour, or 0. */
    protected inline fun onY(yw: Float, xa: Float, xb: Float, za: Float, zb: Float, crossinline shader: (Float, Float, Int, Int) -> Int) {
        c.polyBegin()
        c.polyAdd(iso.sx(xa, yw), iso.sy(xa, yw, za)); c.polyAdd(iso.sx(xb, yw), iso.sy(xb, yw, za))
        c.polyAdd(iso.sx(xb, yw), iso.sy(xb, yw, zb)); c.polyAdd(iso.sx(xa, yw), iso.sy(xa, yw, zb))
        c.polyScan { px, py -> val col = shader(wallYx(px, yw), wallYz(px, py, yw), px, py); if (col != 0) c.set(px, py, col) }
    }

    /** The horizontal plane z = [z] over x [xa]..[xb], y [ya]..[yb]; [shader] (x, y, px, py) gives a colour, or 0. */
    protected inline fun onFlat(z: Float, xa: Float, ya: Float, xb: Float, yb: Float, crossinline shader: (Float, Float, Int, Int) -> Int) {
        c.polyBegin()
        c.polyAdd(iso.sx(xa, ya), iso.sy(xa, ya, z)); c.polyAdd(iso.sx(xb, ya), iso.sy(xb, ya, z))
        c.polyAdd(iso.sx(xb, yb), iso.sy(xb, yb, z)); c.polyAdd(iso.sx(xa, yb), iso.sy(xa, yb, z))
        c.polyScan { px, py -> val col = shader(flatX(px, py, z), flatY(px, py, z), px, py); if (col != 0) c.set(px, py, col) }
    }

    /** An upright drum around screen x [cx] from its base row [base] up to [top], [rx] × [ry] px across, lit from the left; closer up a glint of [hi]. */
    protected fun drum(cx: Float, base: Float, top: Float, rx: Float, ry: Float, l: Int, m: Int, d: Int, hi: Int = l) {
        for (x in floor(cx - rx).toInt()..ceil(cx + rx).toInt()) {
            val u = (x + 0.5f - cx) / rx
            if (abs(u) > 1f) continue
            val e = sqrt(1f - u * u) * ry
            val col = if (P > 1 && abs(u + 0.55f) < 0.5f / rx) hi else if (u < -0.4f) l else if (u > 0.45f) d else m
            c.vline(x, (top + e).toInt(), (base + e).toInt(), col)
        }
    }

    protected fun footShadow(x: Float, y: Float) = shade(iso.sx(x, y), iso.sy(x, y, fl), 5f * K, 1.6f * K, 0.72f, floorId, rugId)

    /** Someone standing on the stage casts their shadow on the floor. */
    override fun footprint(x: Float, y: Float) = footShadow(x, y)

    // ------------------------------------------------------------------ closer up

    /** Picture px per nominal px of the scene's canvas: 1, and 2 or 3 closer up (the diorama's [K] is two of them). */
    protected val P: Int get() = detail

    /** A rod from world point 1 to 2 (a rail, a stretcher): a 1-px line of [col]; closer up as thick as it was, [l] along its top, [dk] under it. */
    protected fun rod(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, col: Int, l: Int = col, m: Int = col, dk: Int = col) {
        val ax = iso.ix(x1, y1); val ay = iso.iy(x1, y1, z1); val bx = iso.ix(x2, y2); val by = iso.iy(x2, y2, z2)
        if (P == 1) { c.line(ax, ay, bx, by, col); return }
        val steep = abs(by - ay) > abs(bx - ax)
        for (o in 0 until P) {
            val cc = if (o == 0) l else if (o == P - 1) dk else m
            if (steep) c.line(ax + o, ay, bx + o, by, cc) else c.line(ax, ay + o, bx, by + o, cc)
        }
    }

    /** A nominal pixel's knob at world ([x], [y], [z]): a [K] block; closer up round, lit at its upper left. */
    protected fun knob(x: Float, y: Float, z: Float, col: Int, l: Int, d: Int) {
        if (P == 1) { iso.px(x, y, z, col); return }
        val kx = iso.ix(x, y) + K / 2f; val ky = iso.iy(x, y, z) + K / 2f
        c.fillCircle(kx, ky, K * 0.62f, d)
        c.fillCircle(kx - 0.5f, ky - 0.5f, K * 0.5f, col)
        c.set(floor(kx - K * 0.25f).toInt(), floor(ky - K * 0.25f).toInt(), l)
    }

    /**
     * A round thing standing upright, closer up: its rows from the picture row [base] (its foot's lower edge) up [hgt]
     * nominal px, [half] (nominal height → nominal half-width) either side of [cx]; [shade] colours a pixel from where it
     * lies across (−1 at the left edge … 1 at the right), a pixel's width in those units, its nominal height and the
     * pixel itself; 0 leaves it.
     */
    protected inline fun lathe(cx: Float, base: Float, hgt: Float, half: (Float) -> Float, shade: (Float, Float, Float, Int, Int) -> Int) {
        val p = P.toFloat()
        for (yy in max(floor(base - hgt * p).toInt(), c.top)..min(ceil(base).toInt() - 1, c.bottom - 1)) {
            val hn = (base - yy - 0.5f) / p
            if (hn < 0f || hn > hgt) continue
            val hw = half(hn) * p
            if (hw <= 0f) continue
            for (xx in max(floor(cx - hw).toInt(), c.left)..min(ceil(cx + hw).toInt(), c.right - 1)) {
                val u = (xx + 0.5f - cx) / hw
                if (abs(u) > 1f) continue
                val col = shade(u, 1f / hw, hn, xx, yy)
                if (col != 0) c.set(xx, yy, col)
            }
        }
    }

    /** Glazed and lit from the left: [l] at the left edge, [dk] at the right, [m] between, a 1-px glint of [hi] where the light catches. */
    protected fun glaze(u: Float, du: Float, l: Int, m: Int, dk: Int, hi: Int): Int = when {
        abs(u + 0.5f) < du * 0.5f -> hi
        u < -0.7f -> l
        u > 0.6f -> dk
        else -> m
    }

    /** A handle: the right half (or the left) of an elliptic ring around ([cx], [cy]), [rx] × [ry] picture px, [th] thick, [hi] along its top. */
    protected fun loop(cx: Float, cy: Float, rx: Float, ry: Float, th: Float, right: Boolean, col: Int, hi: Int) {
        val ix = rx - th; val iy = ry - th
        for (yy in max(floor(cy - ry).toInt(), c.top)..min(ceil(cy + ry).toInt(), c.bottom - 1)) {
            for (xx in max(floor(cx - rx).toInt(), c.left)..min(ceil(cx + rx).toInt(), c.right - 1)) {
                val dx = xx + 0.5f - cx; val dy = yy + 0.5f - cy
                if ((dx >= 0f) != right) continue
                val o = (dx / rx) * (dx / rx) + (dy / ry) * (dy / ry)
                val i = (dx / ix) * (dx / ix) + (dy / iy) * (dy / iy)
                if (o <= 1f && i > 1f) c.set(xx, yy, if (dy < 0f && i < 1.45f) hi else col)
            }
        }
    }

    /** The outline of an ellipse around ([cx], [cy]), [rx] × [ry] picture px, 1 px thin, from angle [a0] to [a1] (0 = right, clockwise on screen). */
    protected fun arc(cx: Float, cy: Float, rx: Float, ry: Float, col: Int, a0: Float = 0f, a1: Float = 2f * PI.toFloat(), every: Int = 1) {
        val n = max(8, ((rx + ry) * 3f * (a1 - a0) / PI.toFloat()).toInt())
        var lx = Int.MIN_VALUE; var ly = Int.MIN_VALUE; var k = 0
        for (i in 0..n) {
            val a = a0 + (a1 - a0) * i / n
            val x = floor(cx + cos(a) * rx).toInt(); val y = floor(cy + sin(a) * ry).toInt()
            if (x == lx && y == ly) continue
            lx = x; ly = y
            if (k++ % every == 0) c.set(x, y, col)
        }
    }

    /** A chain down picture column [x] from row [y0] until [y1], closer up: links face-on and edge-on in turn. */
    protected fun chain(x: Int, y0: Int, y1: Int, l: Int, d: Int) {
        val n = P + 1
        for (yy in max(y0, c.top) until min(y1, c.bottom)) {
            val k = (yy - y0) / n; val ph = (yy - y0) % n
            if (k % 2 == 0) {
                if (ph == 0 || ph == n - 1) c.set(x, yy, d) else { c.set(x - 1, yy, l); c.set(x + 1, yy, d) }
            } else c.set(x, yy, if (ph == 1) l else d)
        }
    }

    /** A geranium's umbel of florets around ([cx], [cy]), [rx] × [ry] picture px: dark under, bright over, lit at the upper left. */
    protected fun umbel(cx: Float, cy: Float, rx: Float, ry: Float, n: Int, seed: Int) {
        c.fillEllipse(cx, cy + ry * 0.2f, rx * 0.9f, ry * 0.8f, Pal.GERANIUM_D)
        for (i in 0 until n) {
            val a = Noise.rnd(i, seed) * 2f * PI.toFloat(); val r = sqrt(Noise.rnd(i, seed + 1)) * 0.85f
            val fx = floor(cx + cos(a) * r * rx).toInt(); val fy = floor(cy + sin(a) * r * ry).toInt()
            val up = sin(a) * r < 0.1f
            c.set(fx, fy, Pal.GERANIUM); c.set(fx + 1, fy, if (up) Pal.GERANIUM else Pal.GERANIUM_D)
            c.set(fx, fy + 1, Pal.GERANIUM_D)
            if (up) c.set(fx, fy - 1, if (cos(a) < 0.3f) Col.hex(0xFF7A74) else Pal.GERANIUM)
        }
        c.set(floor(cx - rx * 0.3f).toInt(), floor(cy - ry * 0.55f).toInt(), Col.hex(0xFFB0A8))
    }

    /** Round geranium leaves over the ellipse ([cx], [cy]) [rx] × [ry] picture px: each with a light rim, a darker zone and a vein. */
    protected fun leaves(cx: Float, cy: Float, rx: Float, ry: Float, n: Int, seed: Int) {
        c.fillEllipse(cx, cy, rx, ry, Col.hex(0x2E6A2E))
        c.fillEllipse(cx - 0.5f, cy - 0.7f, rx - 1f, ry - 0.8f, Pal.LEAF)
        val lr = max(1.6f, rx * 0.36f)
        for (i in 0 until n) {
            val a = (i + Noise.rnd(i, seed) * 0.6f) / n * 2f * PI.toFloat()
            val lx = cx + cos(a) * rx * 0.55f; val ly = cy + sin(a) * ry * 0.45f - ry * 0.1f
            c.fillEllipse(lx, ly, lr, lr * 0.7f, Col.hex(0x2E6A2E))
            c.fillEllipse(lx - 0.5f, ly - 0.5f, lr - 0.8f, lr * 0.7f - 0.6f, if (sin(a) < 0f) Col.hex(0x5AAA4A) else Pal.LEAF)
            c.fillEllipse(lx, ly, lr * 0.45f, lr * 0.3f, Col.hex(0x3A7A34))
            c.set(floor(lx).toInt(), floor(ly).toInt(), Col.hex(0x6ABA58))
        }
    }

    protected fun smooth(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }

    // ------------------------------------------------------------------ ground, floor, walls

    protected fun ground(x: Float, y: Float, px: Int, py: Int): Int {
        val g = grassAt(x, y, px, py)
        // scratched bare where the hen pecks
        val f = (1f - hypot(x - henX, (y - henY) * 1.3f) / 0.8f) * (0.7f + Noise.v2(x * 3f, y * 3f, 5) * 0.6f)
        return dirtAt(px, py, f.coerceIn(0f, 1f), g)
    }

    /** The terracotta floor: square tiles in four tones, their back edges catching the light, lime grout between; at level 1 beaten earth. */
    protected open fun floor() {
        floorId = s.newObject(Pal.OUTLINE)
        if (lv < 2) {
            onFlat(fl, x0, y0, x1, y1) { x, y, px, py -> earthFloor(x, y, px, py) }
            c.penId = 0
            return
        }
        val terra = intArrayOf(Col.hex(0xB86A42), Col.hex(0xAA5F3B), Col.hex(0xC27650), Col.hex(0xA05736))
        val sz = 0.75f
        onFlat(fl, x0, y0, x1, y1) { x, y, px, py ->
            val u = (x - x0) / sz; val v = (y - y0) / sz
            val iu = floor(u).toInt(); val iv = floor(v).toInt()
            val fu = (u - iu) * sz; val fv = (v - iv) * sz
            if (fu < 0.12f || fv < 0.12f) Col.hex(0x8A5A3E)
            else {
                var col = terra[Math.floorMod(Noise.hash(iu, iv, 17), terra.size)]
                if (fu < 0.24f || fv < 0.24f) col = Col.mix(col, Col.hex(0xF0B084), 0.22f)
                else if (fu > sz - 0.12f || fv > sz - 0.12f) col = Col.scale(col, 0.9f)
                else if (P > 1) {
                    // closer up: the fired clay mottled, worn paler where feet go
                    val mott = Noise.v2(x * 7f, y * 7f, 44)
                    if (mott > 0.7f) col = Col.scale(col, 0.94f) else if (mott < 0.22f) col = Col.mix(col, Col.hex(0xF0B084), 0.1f)
                }
                val n = Noise.rnd(px, py, 43)
                if (n > 0.975f) Col.scale(col, 0.84f) else if (n < 0.025f) Col.mix(col, Col.hex(0xF0C090), 0.3f) else col
            }
        }
        c.penId = 0
    }

    /**
     * Level 1's floor at ([x], [y]): beaten clay, mottled, worn darker and smoother on the way from the door to the hearth,
     * ash by the hearth, a straw here and there; closer up small stones and cracks.
     */
    protected fun earthFloor(x: Float, y: Float, px: Int, py: Int): Int {
        val m = Noise.v2(x * 1.7f, y * 1.7f, 71) + (Dither.at(px, py) - 0.5f) * 0.18f
        var col = when {
            m > 0.66f -> Col.hex(0x9A8064)
            m > 0.4f -> Col.hex(0x8A7058)
            m > 0.22f -> Col.hex(0x7C644E)
            else -> Col.hex(0x6E5846)
        }
        // the path worn from the door to the hearth, darker and smoother
        val worn = abs((y - 1.9f) - (x - 7.6f) * -0.05f)
        if (y < 3.2f && x > 2.9f && worn < 0.55f && Dither.at(px, py) < 0.6f) col = Col.scale(col, 0.9f)
        // ash scattered out of the hearth
        val ash = hypot(x - 3.35f, y - 2.0f)
        if (ash < 0.8f && Noise.rnd(px, py, 73) < (0.8f - ash) * 0.9f) col = if (Noise.rnd(px, py, 74) < 0.5f) Col.hex(0xA8A098) else Col.hex(0x8E8880)
        val n = Noise.rnd(px, py, 75)
        return when {
            n > 0.997f -> Col.mix(col, Col.hex(0xD8C07A), 0.6f) // a straw
            n > 0.975f -> Col.scale(col, 0.82f)
            n < 0.02f -> Col.mix(col, Col.hex(0xC0A888), 0.4f)
            P > 1 && abs(Noise.v2(x * 6f, y * 6f, 77) - 0.5f) < 0.012f -> Col.scale(col, 0.8f) // a crack in the clay
            else -> col
        }
    }

    /** A rag rug in front of the door: stripes of rags in every colour across it, a dark warp at its sides, fringes. */
    protected fun rug(ra: Float = 5.65f, rb: Float = 8.4f, rc: Float = 2.75f, rd: Float = 4.25f) {
        rugId = s.newObject(Pal.OUTLINE)
        val z = fl + 0.05f
        val rags = intArrayOf(Col.hex(0xB8452E), Col.hex(0xEADFC4), Col.hex(0x3E6A9E), Col.hex(0xE0A040), Col.hex(0x5E8A4A), Col.hex(0x8A4A7A))
        onFlat(z, ra, rc, rb, rd) { x, y, px, py ->
            val k = floor((x - ra) / 0.19f).toInt()
            val edge = min(y - rc, rd - y)
            var col = rags[(Noise.rnd(k, 5) * rags.size).toInt()]
            if (k % 5 == 0) col = rags[1]
            if (edge < 0.14f) col = Col.hex(0x6E3A2A)
            if (P > 1) {
                // closer up: each rag twisted as it was woven in, lit on one side of every twist
                val f = (x - ra) / 0.19f - k
                val tw = (y - rc) / 0.075f + f * 1.4f
                val ph = tw - floor(tw)
                if (edge < 0.14f) (if (Math.floorMod(floor((x - ra) / 0.06f).toInt(), 2) == 0) Col.hex(0x5A2E20) else col)
                else if (ph < 0.3f) Col.scale(col, 0.84f) else if (ph > 0.8f) Col.mix(col, Col.hex(0xFFFFFF), 0.15f) else col
            } else if (Dither.at(px, py) < 0.25f) Col.scale(col, 0.88f) else col
        }
        // the fringes: closer up twice as many threads, each as thin
        var fy = rc + 0.1f
        val step = if (P > 1) 0.105f else 0.21f
        var n = 0
        while (fy < rd - 0.05f) {
            val col = if (n++ % 2 == 1 && P > 1) Col.hex(0xD4C088) else Col.hex(0xE8D6A0)
            iso.line(rb, fy, z, rb + 0.14f, fy, z, col)
            iso.line(ra, fy, z, ra - 0.12f, fy, z, col)
            fy += step
        }
        // the pen stays on the rug: a room that makes it a thing of its own ends it there
    }

    /** The two back walls: their inner faces, their ends at the house's corners and their tops cut through the masonry. */
    protected fun walls() {
        part(Pal.OUTLINE)
        quadFill(x0, y0, fl, x0, y1 + tk, fl, x0, y1 + tk, hw, x0, y0, hw) { px, py ->
            val y = wallXy(px, x0); val z = wallXz(px, py, x0)
            inner(y, z, px, py, 0.9f, soot(y - 1.35f, z))
        }
        quadFill(x0 - tk, y1 + tk, 0f, x0, y1 + tk, 0f, x0, y1 + tk, hw, x0 - tk, y1 + tk, hw) { px, py ->
            outer(wallYz(px, py, y1 + tk), px, py, 1f)
        }
        quadFill(x0 - tk, y0 - tk, hw, x0, y0 - tk, hw, x0, y1 + tk, hw, x0 - tk, y1 + tk, hw) { px, py ->
            cut(flatX(px, py, hw) - (x0 - tk), flatY(px, py, hw), px, py)
        }
        c.penId = 0
        part(Pal.OUTLINE)
        quadFill(x0, y0, fl, x1 + tk, y0, fl, x1 + tk, y0, hw, x0, y0, hw) { px, py ->
            val x = wallYx(px, y0); val z = wallYz(px, py, y0)
            inner(x, z, px, py, 1f, soot(x - 1.55f, z))
        }
        quadFill(x1 + tk, y0 - tk, 0f, x1 + tk, y0, 0f, x1 + tk, y0, hw, x1 + tk, y0 - tk, hw) { px, py ->
            outer(wallXz(px, py, x1 + tk), px, py, 0.82f)
        }
        quadFill(x0 - tk, y0 - tk, hw, x1 + tk, y0 - tk, hw, x1 + tk, y0, hw, x0 - tk, y0, hw) { px, py ->
            cut(flatY(px, py, hw) - (y0 - tk), flatX(px, py, hw), px, py)
        }
        c.penId = 0
    }

    /**
     * How sooty the wall is [d] cells beside the stovepipe at height [z]: a smudge widening upward over the stove; at
     * level 1 the open hearth's smoke has blackened the whole corner above it, and under the top of every wall.
     */
    protected open fun soot(d: Float, z: Float): Float {
        if (lv < 2) {
            val under = ((z - (hw - 9f)) / 9f).coerceIn(0f, 1f) * 0.45f
            if (z < hz + 0.5f) return under
            val up = ((z - hz - 0.5f) / (hw - hz)).coerceIn(0f, 1f)
            return max(under, ((1.25f - abs(d - 0.4f) / (1.1f + up * 2.6f)) * (0.6f + up * 0.5f)).coerceIn(0f, 1f))
        }
        if (z < stoveZ + 1f) return 0f
        val up = (z - stoveZ - 1f) / (hw - stoveZ)
        return ((1f - abs(d) / (0.35f + up * 0.9f)) * (0.25f + up * 0.35f)).coerceIn(0f, 0.5f)
    }

    /**
     * Whitewash at [along] cells and height [z] inside: a stippled green dado with a red line over it (from level 2), a
     * stencilled border of little flowers under the top (from level 3), specks in the lime, soot over the stove ([sootF]);
     * [dim] shades the wall. At level 1 plain lime, patchy, as the plasterer left it.
     */
    protected open fun inner(along: Float, z: Float, px: Int, py: Int, dim: Float, sootF: Float): Int {
        val h = z - fl
        var col = when {
            lv >= 2 && h < 4.3f -> if (Dither.at(px, py) < 0.22f || Noise.rnd(px, py, 3) < 0.08f) dadoD else dado
            lv >= 2 && h < 4.9f -> border
            lv < 2 -> {
                val n = Noise.rnd(px, py, 7)
                val patch = Noise.v2(along * 1.3f, z * 0.18f, 9)
                if (n < 0.03f) limeL else if (n > 0.95f || patch > 0.72f && Dither.at(px, py) < 0.4f) limeD else if (h < 1.2f) Col.mix(lime, limeD, 0.6f) else lime
            }
            else -> {
                val n = Noise.rnd(px, py, 7)
                if (n < 0.04f) limeL else if (n > 0.965f) limeD else lime
            }
        }
        val dz = z - (hw - 3.6f)
        if (lv >= 3 && abs(abs(dz) - 2f) < 0.26f) col = border
        else if (lv >= 3 && abs(dz) < 1.3f) {
            val m = along / 0.62f
            val a = abs(m - floor(m) - 0.5f) * 0.62f * 8f
            if (P > 1) stencil(a, dz)?.let { col = it }
            else if (a < 1.1f && abs(dz) < 0.8f) col = Col.hex(0xC8442E)
            else if (a < 2.6f && abs(dz) < 0.3f) col = leafC
        }
        if (z > hw - 0.6f) col = limeL
        if (sootF > 0f) col = if (lv < 2) Col.mix(col, Col.hex(0x3A342E), sootF * 0.85f + (Dither.at(px, py) - 0.5f) * 0.12f) else Col.mix(col, Col.hex(0x6A6058), sootF * 0.7f)
        return Col.scale(col, dim)
    }

    /**
     * A flower of the stencilled border closer up, [a] eighths of a cell beside its middle (half a nominal px each) and
     * [dz] nominal px above it: five round petals round a yellow eye, a pointed leaf to each side with its vein; null
     * where the wall shows.
     */
    protected fun stencil(a: Float, dz: Float): Int? {
        val ax = a * 0.5f
        val r = hypot(ax, dz)
        val ang = atan2(dz, ax)
        if (r < 0.2f) return Col.hex(0xE8B040)
        if (r < 0.62f + 0.16f * cos(ang * 5f + PI.toFloat() / 2f)) return if (r > 0.5f && dz < 0f) Col.hex(0xA83624) else Col.hex(0xC8442E)
        if (ax in 0.58f..1.36f) {
            val w = 0.3f * sin((ax - 0.58f) / 0.78f * PI.toFloat())
            if (abs(dz + 0.05f) < w) return if (abs(dz + 0.05f) < 0.5f / K) Col.hex(0x3E6A34) else leafC
        }
        return null
    }

    /** The house's outside: warm whitewash over a grey plinth. */
    protected fun outer(z: Float, px: Int, py: Int, dim: Float): Int {
        val n = Noise.rnd(px, py, 19)
        val col = when {
            z < 2.3f -> if (n < 0.3f) Pal.STONE_D else if (n < 0.85f) Pal.STONE_M else Pal.STONE_L
            z < 2.8f -> Pal.STONE_L
            else -> if (n < 0.05f) limeL else if (n > 0.95f) limeD else Col.hex(0xF0E6CC)
        }
        return Col.scale(col, dim)
    }

    /** A wall's top where it is cut: lime at both faces, rubble and mortar between; snow on it in winter. */
    protected fun cut(across: Float, along: Float, px: Int, py: Int): Int {
        if (winter && Noise.v2(along * 3f, across * 4f, 23) + (Dither.at(px, py) - 0.5f) * 0.3f < 0.78f) return if (Dither.at(px, py) < 0.2f) Pal.SNOW_M else Pal.SNOW_L
        if (across < 0.12f || across > tk - 0.12f) return limeL
        val n = Noise.v2(along * 5f, across * 7f, 21)
        return when {
            n > 0.64f -> Col.hex(0xB0A48E)
            n > 0.42f -> Col.hex(0x968A76)
            n > 0.32f -> Col.hex(0xCFC4AC)
            else -> Col.hex(0x7C715F)
        }
    }

    /** The front walls, cut down to a stub: their outer faces and their cut tops. */
    protected fun stubs() {
        part(Pal.OUTLINE)
        quadFill(x1 + tk, y0, 0f, x1 + tk, y1, 0f, x1 + tk, y1, hs, x1 + tk, y0, hs) { px, py -> outer(wallXz(px, py, x1 + tk), px, py, 0.82f) }
        quadFill(x1, y0, hs, x1 + tk, y0, hs, x1 + tk, y1, hs, x1, y1, hs) { px, py -> cut(flatX(px, py, hs) - x1, flatY(px, py, hs), px, py) }
        c.penId = 0
        part(Pal.OUTLINE)
        quadFill(x0, y1 + tk, 0f, x1 + tk, y1 + tk, 0f, x1 + tk, y1 + tk, hs, x0, y1 + tk, hs) { px, py -> outer(wallYz(px, py, y1 + tk), px, py, 1f) }
        quadFill(x1 + tk, y1, 0f, x1 + tk, y1 + tk, 0f, x1 + tk, y1 + tk, hs, x1 + tk, y1, hs) { px, py -> outer(wallXz(px, py, x1 + tk), px, py, 0.82f) }
        quadFill(x0, y1, hs, x1 + tk, y1, hs, x1 + tk, y1 + tk, hs, x0, y1 + tk, hs) { px, py -> cut(flatY(px, py, hs) - y1, flatX(px, py, hs), px, py) }
        c.penId = 0
    }

    // ------------------------------------------------------------------ the right wall

    /**
     * The window: small panes in a brown frame set into the wall's thickness, the sky and the weather outside
     * (hills, a spruce, the sun or the moon and stars, snow falling in winter), a geranium on the sill from level 2.
     */
    protected fun window() {
        val yg = y0 - 0.25f
        val reveal = Col.scale(lime, 0.78f)
        onY(y0, winA, winB, winZ0, winZ1) { _, _, _, _ -> reveal }
        onY(yg, winA, winB, winZ0, winZ1) { x, z, px, py ->
            val fx = wallYx(px, y0); val fz = wallYz(px, py, y0)
            if (fx < winA || fx > winB || fz < winZ0 || fz > winZ1) 0 else pane(x, z, px, py)
        }
        c.penEmissive = false
        iso.box(winA - 0.15f, yg, winZ0 - 0.9f, winB - winA + 0.3f, y0 + 0.32f - yg, 0.9f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        if (lv >= 2) geranium(winA + 0.45f, y0 + 0.05f, winZ0)
    }

    /** The gingham curtains tied back under a scalloped valance, on a brass rod with its knobs (from level 2). */
    protected fun curtains() {
        onY(y0 + 0.12f, winA - 0.5f, winB + 0.5f, winZ0 - 1.4f, winZ1 + 2f) { x, z, _, _ -> curtain(x, z) }
        val rz = winZ1 + 2.1f; val ry = y0 + 0.14f
        rod(winA - 0.6f, ry, rz, winB + 0.6f, ry, rz, brassD, brass, brassD, brassD)
        knob(winA - 0.62f, ry, rz + 0.5f, brass, brassL, brassD); knob(winB + 0.58f, ry, rz + 0.5f, brass, brassL, brassD)
    }

    protected fun pane(x: Float, z: Float, px: Int, py: Int): Int {
        val ww = winB - winA; val wh = winZ1 - winZ0
        val u = (x - winA) / ww; val v = (z - winZ0) / wh
        val ex = min(u, 1f - u) * ww; val ez = min(v, 1f - v) * wh
        c.penEmissive = false
        if (P > 1) {
            // closer up: the frame's bevel, the glazing bars lit on top, a latch on the middle bar; a picture px is 1/K high, 1/4K cells wide
            val hz = 1f / K; val hx = 0.25f / K
            if (ex < 0.14f || ez < 0.6f) return when {
                ex < 0.07f || ez < 0.3f -> if (ex > 0.07f - hx * 1.5f && ez > 0.3f - hz * 1.5f) Col.mix(Pal.WOOD_D, Pal.WOOD_M, 0.5f) else Pal.WOOD_D
                ex > 0.14f - hx * 1.5f || ez > 0.6f - hz * 1.5f -> Pal.WOOD_M
                ex < 0.07f + hx * 1.5f || ez < 0.3f + hz * 1.5f -> Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.25f)
                else -> Pal.WOOD_L
            }
            val bx = (u - 0.5f) * ww; val bz = (v - 0.52f) * wh
            if (abs(bx) < 0.1f && abs(bz + 0.95f) < 0.42f) return if (bz + 0.95f > 0.42f - hz * 1.5f) brassL else if (bx > 0.05f) brassD else brass
            if (abs(bx) < 0.07f || abs(bz) < 0.28f) return when {
                abs(bz) < 0.28f && bz > 0.28f - hz * 1.5f -> Pal.WOOD_L
                abs(bz) < 0.28f && bz < -0.28f + hz * 1.5f && abs(bx) >= 0.07f -> Pal.WOOD_D
                abs(bx) < 0.07f && bx < -0.07f + hx * 1.5f -> Pal.WOOD_L
                else -> Pal.WOOD_M
            }
        } else {
            if (ex < 0.14f || ez < 0.6f) return if (ex < 0.07f || ez < 0.3f) Pal.WOOD_D else Pal.WOOD_L
            if (abs(u - 0.5f) * ww < 0.07f || abs(v - 0.52f) * wh < 0.28f) return Pal.WOOD_M
        }
        c.penEmissive = true
        return view(u, v, px, py)
    }

    /** What the window shows at ([u], [v]) of the glass, 0..1 from its lower left: pre-lit, since the glass glows. */
    protected fun view(u: Float, v: Float, px: Int, py: Int): Int {
        val ww = (winB - winA) * 8f; val wh = (winZ1 - winZ0) * 2f
        // snow falling in winter, in front of everything
        if (winter) for (k in 0 until 7) {
            val fu = (Noise.rnd(k, 71) + sin(t * 0.8 + k).toFloat() * 0.05f); val fv = 1f - ((Noise.rnd(k, 72) + t.toFloat() * 0.13f) % 1f)
            if (abs((u - fu) * ww) < 0.6f && abs((v - fv) * wh) < 0.6f) return Col.hex(0xFFFFFF)
        }
        // the spruce and the hills
        val spruce = (0.64f - v) * wh * 0.38f - abs(u - 0.24f) * ww
        val far = 0.36f + Noise.v1(u * 2.4f + 1.3f, 5) * 0.14f
        val near = 0.2f + u * 0.12f + Noise.v1(u * 4f, 9) * 0.05f
        val snow = winter
        when {
            v < near -> return env.lit(if (snow) (if (Dither.at(px, py) < 0.3f) Pal.SNOW_M else Pal.SNOW_L) else when (env.season) {
                Season.AUTUMN -> if (Dither.at(px, py) < 0.3f) Col.hex(0x9A8A3E) else Col.hex(0xB09A48)
                else -> if (Dither.at(px, py) < 0.3f) Col.hex(0x5C9A48) else Col.hex(0x72B25A)
            })
            spruce > 0f && v > near - 0.05f -> return env.lit(if (snow && ((v * wh).toInt() % 3 == 0)) Pal.SNOW_L else Col.hex(0x2C5E3A))
            v < far -> return env.lit(if (snow) Col.hex(0xC8D4E4) else Col.hex(0x6F8F7A))
        }
        // the sun by day, the moon by night
        if (env.sun > -0.1f) {
            val span = ((s.hour - env.sunrise) / (env.sunset - env.sunrise)).coerceIn(0f, 1f)
            val sd = hypot((u - 0.15f - span * 0.7f) * ww, (v - 0.55f - env.sun.coerceIn(0f, 1f) * 0.35f) * wh)
            if (sd < 2.3f) return Col.mix(Col.hex(0xFFF6D8), Col.hex(0xFFB04A), (1f - env.sun * 2.5f).coerceIn(0f, 1f))
        } else if (env.moonShows) {
            // the moon as the date has it (see Moon), where it is up
            val mx = (u - 0.72f) * ww; val my = (0.84f - v) * wh
            if (hypot(mx, my) < 2.2f && Moon.light(mx / 2.2f, my / 2.2f, env.moonAge) > 0f) return Col.hex(0xF4EED0)
        }
        var col = skyAt((0.35f + (1f - v) * 0.6f + (Dither.at(px, py) - 0.5f) * 0.08f).coerceIn(0f, 1f))
        // the stars: as many closer up, each still a single pixel
        val star = Noise.rnd(px / P, py / P, 3) < 0.035f && (P == 1 || (px % P == P / 2 && py % P == P / 2))
        if (env.dark > 0.3f && star && v > 0.5f)
            col = Col.mix(col, Col.hex(0xFFF8E0), ((env.dark - 0.3f) / 0.5f).coerceIn(0f, 1f) * (0.6f + 0.4f * sin(t * 2 + px / P).toFloat()))
        else if (env.dark < 0.3f && v > 0.7f && Noise.v2(u * 3f + t.toFloat() * 0.03f, v * 5f, 41) > 0.66f) col = Col.mix(col, Col.hex(0xFFFFFF), 0.7f)
        // a sheen across the glass
        if (abs(u * ww - (1f - v) * wh * 0.5f - 1f) < 0.7f) col = Col.mix(col, Col.hex(0xFFFFFF), 0.25f)
        return col
    }

    /** The gingham curtains and their valance at ([x], [z]) in front of the wall; 0 where there is none. */
    protected fun curtain(x: Float, z: Float): Int {
        val tie = winZ0 + 3.2f; val top = winZ1 + 0.6f; val bottom = winZ0 - 1.2f
        val hem = winZ1 - 1.1f + abs(sin((x - winA) * 7.6f)) * 1f
        val valance = z > hem && z < winZ1 + 1.8f
        val pw = if (z > tie) 0.24f + 0.38f * ((z - tie) / (top - tie)).coerceIn(0f, 1f) else 0.24f + 0.14f * ((tie - z) / (tie - bottom)).coerceIn(0f, 1f)
        val left = x < winA - 0.5f + pw && z < top && z > bottom
        val right = x > winB + 0.5f - pw && z < top && z > bottom
        if (!valance && !left && !right) return 0
        val a = floor((x - winA) / 0.2f).toInt() and 1; val b = floor(z / 1f).toInt() and 1
        var col = if (a == 1 && b == 1) red else if (a == 1 || b == 1) pink else linen
        if (valance) { if (z < hem + 0.45f) col = Col.scale(col, 0.82f) }
        else {
            if (abs(z - tie) < 0.45f) return Col.hex(0x9A2A22)
            val edge = if (left) winA - 0.5f + pw - x else x - (winB + 0.5f - pw)
            if (edge < 0.08f) col = Col.scale(col, 0.8f)
            else if (sin((x - winA) * 22f) > 0.6f) col = Col.scale(col, 0.9f)
        }
        return col
    }

    /** A geranium in a clay pot at world ([x], [y]) on a ledge at height [z]. */
    protected fun geranium(x: Float, y: Float, z: Float) {
        val cx = iso.ix(x, y); val by = iso.iy(x, y, z)
        val pot = Col.hex(0xC0603A); val potL = Col.hex(0xD8784A); val potD = Col.hex(0x8A4028)
        if (P > 1) {
            val p = P.toFloat(); val mx = cx + 0.5f * p
            flowerPot(mx, by.toFloat(), 4f, 2.2f, 2.55f, 3.4f, pot, potL, potD)
            leaves(mx, by - 7.5f * p, 4f * p, 2.6f * p, 5, 7)
            umbel(mx - 0.2f * p, by - 9.3f * p, 2.6f * p, 1.4f * p, 7 * P, 11)
            return
        }
        c.fillRect(cx - 2, by - 4, 5, 4, pot); c.hline(cx - 3, cx + 3, by - 5, potL); c.vline(cx + 2, by - 4, by - 1, potD); c.set(cx - 2, by - 4, potL)
        c.fillEllipse(cx + 0.5f, by - 7.5f, 4f, 2.6f, Pal.LEAF)
        c.set(cx - 2, by - 8, Col.hex(0x5AAA4A)); c.set(cx + 2, by - 7, Col.hex(0x2E6A2E))
        for (k in 0..3) c.set(cx - 2 + k + (k / 2), by - 9 - (k % 2), if (k == 2) Pal.GERANIUM_D else Pal.GERANIUM)
        c.set(cx, by - 10, Pal.GERANIUM); c.set(cx + 1, by - 10, Col.hex(0xFF6A6A))
    }

    /**
     * A clay flower pot closer up, around picture x [mx] on the row [base]: a tapered body [hgt] nominal px high from
     * [h0] to [h1] half-wide, a rolled rim [rim] half-wide over it and the dark earth in its mouth.
     */
    protected fun flowerPot(mx: Float, base: Float, hgt: Float, h0: Float, h1: Float, rim: Float, pot: Int, potL: Int, potD: Int) {
        val p = P.toFloat()
        lathe(mx, base, hgt, { h -> h0 + (h1 - h0) * h / hgt }) { u, du, h, _, _ ->
            val col = glaze(u, du, potL, pot, potD, Col.hex(0xE89868))
            if (h > hgt - 0.5f / P) Col.scale(potD, 0.8f) else col
        }
        lathe(mx, base - hgt * p, 1f, { rim }) { u, du, h, _, _ ->
            if (h < 0.5f / P) potD else if (h > 1f - 1f / P) Col.mix(potL, Col.hex(0xFFE0C0), 0.3f) else glaze(u, du, potL, Col.mix(pot, potL, 0.5f), potD, potL)
        }
        c.fillEllipse(mx, base - (hgt + 1f) * p, rim * p - 1f, 0.8f * p, potD)
        c.fillEllipse(mx, base - (hgt + 1f) * p + 0.5f, rim * p - 2f, 0.55f * p, Pal.SOIL_D)
    }

    /**
     * The plank door in its frame: boards, ledges and a brace, strap hinges, a latch; a stone threshold, a horseshoe above.
     * [open]: the door stands open into the room ([leaf], drawn after whoever hides behind it) and the frame shows the
     * dim hall beyond.
     */
    protected fun door(open: Boolean = false) {
        val l = Col.hex(0xA06C40); val m = Col.hex(0x8A5A34); val d = Col.hex(0x6A4226)
        onY(y0 + 0.02f, doorA - 0.2f, doorB + 0.2f, fl, doorZ + 1.1f) { x, z, px, py ->
            val fa = x - doorA; val fb = doorB - x; val ft = doorZ - z
            if (fa < 0f || fb < 0f || ft < 0f) {
                if (fa < -0.15f || fb < -0.15f || ft < -0.85f) Pal.WOOD_X else if (fa < 0f && fa > -0.06f) Pal.WOOD_L else Pal.WOOD_D
            } else if (open) {
                hall(fa, z - fl, px, py)
            } else {
                val h = z - fl
                val board = floor(fa / 0.27f).toInt(); val fbd = fa - board * 0.27f
                val brace = h - (3.9f + fa / (doorB - doorA) * 11.3f)
                val ledge = h in 2.4f..3.9f || h in 15.2f..16.7f
                val hinge = fa < 0.8f && (abs(h - 3.15f) < 0.35f || abs(h - 15.95f) < 0.35f)
                val fine = if (P > 1) doorFine(fa, fb, h, board, fbd, hinge, ledge, m, d) else 0
                if (fine != 0) fine else when {
                    hinge -> if (fa > 0.72f) ironL else iron
                    abs(fb - 0.22f) < 0.07f && abs(h - 9.6f) < 1.1f -> iron
                    abs(fb - 0.32f) < 0.1f && abs(h - 9.6f) < 0.3f -> ironL
                    ledge -> if (h < 2.7f || (h > 15.2f && h < 15.5f)) d else l
                    abs(brace) < 0.9f && h > 3.9f && h < 15.2f -> if (brace < -0.6f) d else Col.mix(l, m, 0.4f)
                    fbd < 0.08f -> d
                    Noise.v2(board * 3.1f, h * 0.4f, 7) > 0.7f -> Col.scale(m, 0.92f)
                    Noise.rnd(px, py, 13) > 0.97f -> l
                    else -> if (Noise.rnd(board, 3) < 0.5f) m else Col.mix(m, l, 0.25f)
                }
            }
        }
        iso.box(doorA - 0.1f, y0, fl, doorB - doorA + 0.2f, 0.3f, 0.45f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        // the horseshoe over the door, for luck
        val hx = iso.ix((doorA + doorB) / 2f, y0 + 0.05f); val hy = iso.iy((doorA + doorB) / 2f, y0 + 0.05f, doorZ + 3.6f)
        val shoe = Col.hex(0x6A6A74)
        if (P > 1) { horseshoe(hx + 0.5f * P, hy + 1.5f * P, shoe); return }
        c.vline(hx - 2, hy - 1, hy + 2, shoe); c.vline(hx + 2, hy - 1, hy + 2, shoe); c.hline(hx - 1, hx + 1, hy + 3, shoe)
        c.set(hx - 2, hy - 2, Col.hex(0x9A9AA6)); c.set(hx + 2, hy - 2, Col.hex(0x9A9AA6))
    }

    /**
     * The hall beyond the open door at [fa] cells from its hinge side and [h] nominal px up: dim, its stone floor catching a
     * little light, the far wall darker, a streak of daylight down the far door's crack.
     */
    private fun hall(fa: Float, h: Float, px: Int, py: Int): Int {
        val w = doorB - doorA
        val dim = 1f - env.dark * 0.5f
        val col = when {
            h < 1.6f -> if (Dither.at(px, py) < 0.3f) Col.hex(0x6A6058) else Col.hex(0x5A5048)
            h < 2.1f -> Col.hex(0x3E3630)
            abs(fa - w * 0.62f) < 0.05f && h < 15f && env.dark < 0.6f -> Col.hex(0xB8A67E)
            else -> if (Dither.at(px, py) < 0.12f + h / 80f) Col.hex(0x2A221E) else Col.hex(0x3A302A)
        }
        return Col.scale(col, dim)
    }

    /**
     * The door standing open into the room, hinged at [doorA]: its leaf at right angles to the wall, a little off the floor
     * (the shoes of someone behind it show under it), its planks, ledges and hinges, the latch at its free edge and that
     * edge's thickness. Draw it after whoever hides behind it.
     */
    protected fun leaf() {
        val l = Col.hex(0xA06C40); val m = Col.hex(0x8A5A34); val d = Col.hex(0x6A4226)
        val w = doorB - doorA; val bottom = fl + LEAF_GAP
        onX(doorA, y0 + 0.02f, y0 + w, bottom, doorZ) { y, z, px, py ->
            val fa = y - y0; val fb = w - fa; val h = z - fl
            val board = floor(fa / 0.27f).toInt(); val fbd = fa - board * 0.27f
            val brace = h - (3.9f + fa / w * 11.3f)
            val col = when {
                fa < 0.8f && (abs(h - 3.15f) < 0.35f || abs(h - 15.95f) < 0.35f) -> if (fa > 0.72f) ironL else iron
                abs(fb - 0.22f) < 0.07f && abs(h - 9.6f) < 1.1f -> iron
                h in 2.4f..3.9f || h in 15.2f..16.7f -> if (h < 2.7f || (h > 15.2f && h < 15.5f)) d else l
                abs(brace) < 0.9f && h > 3.9f && h < 15.2f -> if (brace < -0.6f) d else Col.mix(l, m, 0.4f)
                fbd < 0.08f -> d
                Noise.v2(board * 3.1f, h * 0.4f, 7) > 0.7f -> Col.scale(m, 0.92f)
                Noise.rnd(px, py, 13) > 0.97f -> l
                else -> if (Noise.rnd(board, 3) < 0.5f) m else Col.mix(m, l, 0.25f)
            }
            Col.scale(col, 0.86f)
        }
        // its free edge, the plank's thickness catching the light
        onY(y0 + w, doorA - 0.1f, doorA, bottom, doorZ) { _, z, _, _ -> if (z > doorZ - 0.4f) Col.mix(l, Col.hex(0xFFFFFF), 0.2f) else l }
    }

    /**
     * The door closer up at [fa] cells from its hinge side, [fb] from the latch side and [h] nominal px up, on [board]
     * [fbd] cells across it: nail heads where the ledges and the hinges' straps cross the boards, grain along the
     * boards and a knot here and there, a keyhole in the latch's plate; 0 where the plain door shows.
     */
    protected fun doorFine(fa: Float, fb: Float, h: Float, board: Int, fbd: Float, hinge: Boolean, ledge: Boolean, m: Int, d: Int): Int {
        // along the wall a cell is 4 nominal px across
        val nx = fa * 4f
        // nail heads: one on each board across each ledge and strap
        val nailRow = if (ledge) (if (h < 9f) 3.15f else 15.95f) else if (hinge) (if (h < 9f) 3.15f else 15.95f) else -99f
        if (nailRow > 0f) {
            val bc = (board * 0.27f + 0.135f) * 4f
            val nd = hypot(nx - bc, (h - nailRow) * 1.4f)
            if (nd < 0.34f) return if (nx - bc < -0.08f && h > nailRow + 0.05f) Col.hex(0x8A8694) else ironD
        }
        // the keyhole under the latch
        if (abs(fb - 0.22f) * 4f < 0.14f && h in 8.65f..9.2f) return if (h > 9.02f || abs(fb - 0.22f) * 4f < 0.07f) ironD else 0
        if (hinge || ledge) return 0
        // grain: thin dark streaks wandering along each board, a knot here and there
        val g = Noise.v2(fbd * 16f, h * 0.22f + board * 5.3f, 61)
        if (abs(g - 0.5f) < 0.035f) return Col.mix(m, d, 0.55f)
        val knot = hypot((fbd - 0.135f) * 4f, (h - (4f + Noise.rnd(board, 67) * 10f)) * 0.9f)
        if (Noise.rnd(board, 71) < 0.5f && knot < 0.26f) return if (knot < 0.12f) d else Col.mix(m, d, 0.4f)
        return 0
    }

    /** The horseshoe closer up, its bend's centre at ([cx], [cy]): heels up, nail holes, lit at its upper left. */
    protected fun horseshoe(cx: Float, cy: Float, shoe: Int) {
        val p = P.toFloat()
        for (yy in max(floor(cy - 3.6f * p).toInt(), c.top)..min(ceil(cy + 2.6f * p).toInt(), c.bottom - 1)) {
            for (xx in max(floor(cx - 2.6f * p).toInt(), c.left)..min(ceil(cx + 2.6f * p).toInt(), c.right - 1)) {
                val rx = (xx + 0.5f - cx) / p; val ry = (yy + 0.5f - cy) / p
                val r = if (ry <= 0f) abs(rx) else hypot(rx, ry)
                if (r < 1.45f || r > 2.5f || ry < -3.5f) continue
                val heel = ry < -2.6f
                val hole = r in 1.8f..2.15f && (abs(ry + 1.9f) < 0.28f || abs(ry + 0.6f) < 0.28f || (ry > 0.3f && abs(abs(rx) - 1.3f) < 0.26f))
                val col = when {
                    hole -> Col.hex(0x2E2A32)
                    heel -> Col.hex(0x9A9AA6)
                    r > 2.5f - 1f / p && (rx < 0f || ry < -1f) -> Col.hex(0xA4A4B0)
                    rx > 0.4f && r < 1.45f + 1f / p -> Col.hex(0x4A4A54)
                    rx > 0f -> Col.scale(shoe, 0.88f)
                    else -> shoe
                }
                c.set(xx, yy, col)
            }
        }
    }

    /** The stovepipe from the back of the stove up the corner, bending into the wall under a collar. */
    protected fun pipe() {
        val px = iso.ix(1.55f, 1.35f); val top = iso.iy(1.55f, 1.35f, fl + 21f); val bot = iso.iy(1.55f, 1.35f, stoveZ + 0.8f)
        if (P > 1) { pipeFine(px, top, bot); return }
        for (dx in -2..1) c.vline(px + dx, top, bot, when (dx) { -2 -> ironL; 1 -> ironD; else -> iron })
        for (zz in floatArrayOf(stoveZ + 4f, stoveZ + 8.5f)) { val jy = iso.iy(1.55f, 1.35f, zz); c.hline(px - 2, px + 1, jy, ironL); c.hline(px - 2, px + 1, jy + 1, ironD) }
        // the elbow into the right wall and its collar
        for (k in 0..3) { c.hline(px - 2 + k, px + 1 + k, top - k / 2, if (k == 3) ironL else iron); c.hline(px - 2 + k, px + 1 + k, top - k / 2 - 1, ironL) }
        c.fillEllipse(px + 4.5f, top - 1.5f, 1.8f, 3f, ironD)
    }

    /** The stovepipe closer up: round, shaded across; its joints collars a little proud of it, riveted; the elbow and its collar in the wall. */
    protected fun pipeFine(px: Int, top: Int, bot: Int) {
        val p = P
        val cx = px.toFloat(); val hw = 2f * p // its columns px − 2 .. px + 1 on the scene's canvas
        val tones = intArrayOf(ironL, Col.mix(ironL, iron, 0.5f), iron, iron, Col.mix(iron, ironD, 0.5f), ironD)
        val glint = Col.hex(0x8C8896)
        for (x in px - 2 * p until px + 2 * p) {
            val u = (x + 0.5f - cx) / hw
            val col = if (abs(u + 0.55f) < 0.5f / hw) glint else tones[((u + 1f) / 2f * tones.size).toInt().coerceIn(0, tones.size - 1)]
            c.vline(x, top, bot, col)
        }
        for (zz in floatArrayOf(stoveZ + 4f, stoveZ + 8.5f)) {
            val jy = iso.iy(1.55f, 1.35f, zz)
            for (x in px - 2 * p - 1..px + 2 * p) {
                val u = (x + 0.5f - cx) / (hw + 1f)
                c.vline(x, jy, jy + p - 1, if (u < -0.6f) glint else if (u > 0.5f) iron else Col.mix(ironL, iron, 0.3f))
                c.vline(x, jy + p, jy + 2 * p - 1, ironD)
            }
            for (rv in -1..1) c.set(px + rv * p - (if (rv > 0) 1 else 0), jy + p / 2, ironD)
        }
        // the elbow into the right wall and its collar
        sprite(px, top) {
            for (k in 0..3) { c.hline(px - 2 + k, px + 1 + k, top - k / 2, if (k == 3) ironL else iron); c.hline(px - 2 + k, px + 1 + k, top - k / 2 - 1, ironL) }
        }
        c.line(px - 2 * p, top - p, px + 5 * p - 1, top - 2 * p - p / 2, glint)
        c.fillEllipse(px + 4.5f * p, top - 1.5f * p, 1.8f * p, 3f * p, ironD)
        arc(px + 4.5f * p, top - 1.5f * p, 1.8f * p - 0.5f, 3f * p - 0.5f, ironL, PI.toFloat() * 0.55f, PI.toFloat() * 1.45f)
    }

    /**
     * The stove in the corner: green tiles, a tiled back with a ledge the pipe rises from, an iron plate on top with a
     * polished rim and a hob ring, the oven door, the firebox with the embers glowing through its grate, the ash door,
     * a brass rail along the front with a towel on it.
     */
    protected fun stove() {
        val steel = Col.hex(0xA4A4B0)
        // the tiled back against the right wall, its ledge
        val bz = stoveZ + 5.5f
        onY(y0 + 0.35f, x0, stoveX, stoveZ, bz) { x, z, px, py -> tiles(x - x0, z - stoveZ + fl + 0.9f, px, py, 0.92f) }
        onX(stoveX, y0, y0 + 0.35f, stoveZ, bz) { y, z, px, py -> tiles(y - y0, z - stoveZ + fl + 0.9f, px, py, 0.76f) }
        iso.box(x0, y0, bz, stoveX - x0 + 0.08f, 0.45f, 0.7f, tileL, tileM, tileD)
        // the body
        onY(stoveY, x0, stoveX, fl, stoveZ) { x, z, px, py -> stoveFront(x, z, px, py) }
        c.penEmissive = false
        onX(stoveX, y0 + 0.35f, stoveY, fl, stoveZ) { y, z, px, py -> tiles(y - y0, z, px, py, 0.8f) }
        // the plate, a little proud of the tiles
        iso.box(x0, y0 + 0.35f, stoveZ, stoveX - x0 + 0.1f, stoveY - y0 - 0.25f, 1f, Col.hex(0x46424E), steel, Col.hex(0x6E6C78))
        onFlat(stoveZ + 1f, x0, y0 + 0.35f, stoveX + 0.1f, stoveY + 0.1f) { x, y, _, _ ->
            val d = hypot(x - 1.7f, y - 1.72f)
            when {
                abs(d - 0.34f) < 0.05f -> ironD
                abs(d - 0.2f) < 0.04f -> ironL
                x > stoveX - 0.04f || y > stoveY - 0.02f -> Col.hex(0xC8C8D2)
                // closer up: the hob's outer ring, the notch for the lifter, the rings lit where the window's light
                // catches them, the plate's back edges worn bright
                P > 1 && abs(d - 0.5f) < 0.03f -> if (y < 1.72f && x > 1.7f) Col.hex(0x6E6A78) else ironD
                P > 1 && abs(d - 0.27f) < 0.1f && abs(y - 1.72f) < 0.03f && x > 1.7f -> ironD
                P > 1 && abs(d - 0.34f) < 0.08f && y < 1.62f && x > 1.72f -> Col.hex(0x6E6A78)
                P > 1 && (x < x0 + 0.05f || y < y0 + 0.4f) -> Col.hex(0x8A8896)
                else -> 0
            }
        }
        // the brass rail along the front on two brackets, a towel over it
        val ry = stoveY + 0.36f; val rz = stoveZ - 0.7f
        for (bx in floatArrayOf(x0 + 0.25f, stoveX - 0.1f)) rod(bx, stoveY + 0.1f, rz, bx, ry, rz, brassD, brass, brassD, brassD)
        rod(x0 + 0.2f, ry, rz, stoveX, ry, rz, brass, brass, brass, brassD)
        rod(x0 + 0.2f, ry, rz + 0.5f, stoveX, ry, rz + 0.5f, brassL, Col.hex(0xFFF0C0), brassL, brassL)
        onY(ry + 0.02f, 1.2f, 1.65f, rz - 4.2f, rz + 0.4f) { x, z, px, _ ->
            val hem = rz - 3.4f + sin((x - 1.2f) * 30f) * 0.3f
            // closer up a fringe of threads hangs from the hem, and the red stripes are woven of finer threads
            if (z < hem) (if (P > 1 && z > hem - 0.7f && px % 2 == 0 && Noise.rnd(px, 5, 77) > 0.2f) Col.scale(linen, 0.9f) else 0)
            else if (abs(z - (rz - 2.4f)) < 0.3f || abs(z - (rz - 1.6f)) < 0.2f) (if (P > 1 && px % 3 == 0) Col.hex(0xB02A24) else red)
            else if (x > 1.57f) Col.scale(linen, 0.85f) else linen
        }
    }

    protected fun stoveFront(x: Float, z: Float, px: Int, py: Int): Int {
        val h = z - fl
        val steel = Col.hex(0xB4B4C0); val steelD = Col.hex(0x74747E)
        c.penEmissive = false
        // the firebox: a door with a grate the embers glow through
        if (x in 2.5f..3.12f && h in 3.7f..6.6f) {
            val fx = x - 2.5f; val fz = h - 3.7f
            if (fx < 0.1f || fz > 2.45f) return steel
            if (fx > 0.52f || fz < 0.4f) return steelD
            if (((fz / 0.5f).toInt() % 2) == 1) {
                c.penEmissive = true
                // fired up (see paint), the grate burns brighter
                val flick = Noise.rnd((x * 9f).toInt(), (t * 6).toInt(), 3) - fxOn("oven") * 0.35f
                return if (flick < 0f) Pal.FLAME[0] else if (flick < 0.3f) Pal.FLAME[1] else if (flick < 0.75f) Pal.FLAME[2] else Pal.FLAME[3]
            }
            return ironD
        }
        // the ash door under it
        if (x in 2.5f..3.12f && h in 1.3f..3.3f) {
            val fx = x - 2.5f
            return if (fx < 0.1f || h > 3.0f) steel else if (fx > 0.52f || h < 1.6f) steelD else if (abs(h - 2.3f) < 0.3f && fx in 0.22f..0.42f) brass else iron
        }
        // the oven door, a brass bar for a handle
        if (x in 1.4f..2.3f && h in 1.7f..6.5f) {
            val fx = x - 1.4f; val fz = h - 1.7f
            return when {
                fz > 3.6f && fz < 4.2f && fx in 0.14f..0.76f -> brassL
                fx < 0.1f || fz > 4.4f -> steel
                fx > 0.8f || fz < 0.4f -> steelD
                fz > 3.4f -> ironL
                else -> iron
            }
        }
        return tiles(x - x0, z, px, py, 1f)
    }

    /** Glazed green tiles at [a] cells along a face and height [z], on an iron base. */
    protected fun tiles(a: Float, z: Float, px: Int, py: Int, dim: Float): Int {
        val h = z - fl
        if (h < 0.9f) return ironD
        val tw = 0.55f; val th = 2.4f
        val fu = a - floor(a / tw) * tw; val hv = h - 0.9f; val fv = hv - floor(hv / th) * th
        val col = when {
            fu < 0.1f || fv < 0.45f -> grout
            P > 1 -> tileFine(fu, fv, tw, th, px, py)
            fu < 0.22f || fv > th - 0.6f -> tileL
            fu > tw - 0.14f || fv < 0.9f -> tileD
            else -> if (Noise.rnd(px, py, 29) > 0.93f) tileL else tileM
        }
        return Col.scale(col, dim)
    }

    /**
     * A tile closer up at [fu] cells along and [fv] nominal px up within its [tw] × [th]: bevelled, a glint on its
     * shoulder, a raised boss in the middle catching the light above and shadowed below, the glaze pooling darker.
     */
    protected fun tileFine(fu: Float, fv: Float, tw: Float, th: Float, px: Int, py: Int): Int {
        val hz = 1f / K; val hx = 0.25f / K // a picture px, up and along
        return when {
            fu < 0.1f + hx * 1.5f && fv > 0.9f -> Col.mix(tileL, Col.hex(0xFFFFFF), 0.35f)
            fu < 0.22f || fv > th - 0.6f -> if (fv > th - 0.6f + hz * 1.5f && fu > 0.22f && fu < 0.3f) Col.mix(tileL, Col.hex(0xFFFFFF), 0.5f) else tileL
            fu > tw - 0.14f || fv < 0.9f -> if (fu > tw - hx * 1.5f || fv < 0.45f + hz * 1.5f) Col.scale(tileD, 0.88f) else tileD
            else -> {
                // the boss: a little dome in the tile's middle
                val bu = (fu - (0.22f + tw - 0.14f) / 2f) * 4f; val bv = fv - (0.9f + th - 0.6f) / 2f
                val r = hypot(bu, bv * 1.1f)
                when {
                    r < 0.3f -> if (bu - bv < -0.08f) tileL else if (bu - bv > 0.16f) Col.mix(tileM, tileD, 0.5f) else tileM
                    r < 0.3f + hz * 1.2f -> Col.mix(tileM, tileD, 0.6f)
                    Noise.rnd(px, py, 29) > 0.95f -> Col.mix(tileM, tileL, 0.6f)
                    else -> tileM
                }
            }
        }
    }

    /**
     * The pot on the stove (or on the hearth's trivet), standing at height [z]: blue enamel speckled white, a white rim,
     * its lid, handles; steam rising. Tapped ([pokes]) the
     * lid rattles on the rim and a puff of steam escapes; tapped again it boils over: the lid hops, steam bursts out.
     */
    protected fun pot(x: Float, y: Float, z: Float) {
        val cx = iso.sx(x, y); val base = iso.sy(x, y, z)
        val l = Col.hex(0x6A9ADA); val m = Col.hex(0x3E6CB2); val d = Col.hex(0x284C86)
        val p = P
        val rx = 5.5f * p; val ry = 2.8f * p; val top = base - 9f * p
        val pk = poked("pot"); val a = pk?.age(t) ?: 0f
        val beat = (a * 16).toInt()
        // the lid's lift (scene px) and its sideways jig (picture px) this frame
        val lift = when {
            pk == null -> 0
            pk.step == 0 -> if (a < 1.05f) beat and 1 else 0
            a < 0.84f -> (abs(sin(a * PI.toFloat() / 0.42f)) * 3.4f).toInt()
            else -> if (a < 1.5f) beat and 1 else 0
        }
        val jig = if (pk != null && a < (if (pk.step == 0) 1.05f else 1.5f)) (if ((beat shr 1) and 1 == 0) 1 else -1) * max(1, p / 2) else 0
        val lx = cx + jig; val lt = top - lift * p
        c.fillRect((cx - rx - 2 * p).toInt(), (top + 3 * p).toInt(), 2 * p, 2 * p, d); c.fillRect((cx + rx).toInt(), (top + 3 * p).toInt(), 2 * p, 2 * p, d)
        if (p > 1) {
            // the handles: little loops riveted on, lit on top
            c.hline((cx - rx - 2 * p).toInt(), (cx - rx).toInt() - 1, (top + 3 * p).toInt(), m)
            c.hline((cx + rx).toInt(), (cx + rx + 2 * p).toInt() - 1, (top + 3 * p).toInt(), m)
        }
        drum(cx, base, top, rx, ry, l, m, d, Col.hex(0xA6C6EE))
        // speckled enamel: finer closer up, as many specks on every scene pixel
        val specks = if (p == 1) 6 else 11 * p
        for (k in 0 until specks) c.set((cx - 4 * p + Noise.rnd(k, 3) * 8 * p).toInt(), (top + 3 * p + Noise.rnd(k, 4) * 6 * p).toInt(), Col.hex(0xE8F0FA))
        if (p > 1) {
            c.fillEllipse(cx, top + 1f, rx, ry, d) // the rim's shadow on the pot
            for (k in 0 until 3 * p) c.set((cx - 5 * p + Noise.rnd(k, 5) * 10 * p).toInt(), (top + 2 * p + Noise.rnd(k, 6) * 7 * p).toInt(), Col.hex(0x1C3868))
        }
        c.fillEllipse(cx, top, rx, ry, china)
        // lifted, the dark of the pot under the lid
        if (lift > 0 || jig != 0) c.fillEllipse(cx, top, rx - 1f * p, ry - 0.9f * p, Col.hex(0x1C2A44))
        c.fillEllipse(lx, lt - 0.5f * p, rx - 1f * p, ry - 0.9f * p, m)
        if (p > 1) {
            // the lid's rolled edge and its shadow
            arc(lx, lt - 0.5f * p, rx - 1f * p, ry - 0.9f * p, d, 0f, PI.toFloat())
            c.fillEllipse(lx, lt - 0.5f * p - 1f, rx - 1f * p - 1.5f, ry - 0.9f * p - 1f, m)
        }
        c.fillEllipse(lx - 1f * p, lt - 1f * p, rx - 3f * p, ry - 1.6f * p, l)
        c.fillRect((lx - 1 * p).toInt(), (lt - 3 * p).toInt(), 2 * p, 2 * p, ironD)
        if (p > 1) {
            // the knob: turned, a brass ring under it, lit at its top
            c.hline((lx - 1.5f * p).toInt(), (lx + 1.5f * p).toInt() - 1, (lt - 1 * p).toInt(), brassD)
            c.hline((lx - 1 * p).toInt(), (lx + 1 * p).toInt() - 1, (lt - 3 * p).toInt(), ironL)
            c.set((lx - 1 * p).toInt(), (lt - 3 * p).toInt() + 1, ironL)
            c.set((lx - 1.5f * p).toInt(), (lt - 1.4f * p).toInt(), Col.hex(0xFFFFFF))
        }
        s.smoke(cx - 2f * p, top - 4f * p, 0.6f, 11); s.smoke(cx + 2f * p, top - 3f * p, 0.5f, 12)
        if (pk != null) s.fx {
            val steam = Col.hex(0xF4F6FA)
            if (pk.step == 0) PokeArt.puff(c, cx + rx * 0.7f, top - 1f * p, a / 1.3f, 5f * p, 5, steam, 3, 0.75f)
            else {
                PokeArt.puff(c, cx - rx * 0.8f, top - 1f * p, a / 1.9f, 7f * p, 6, steam, 4, 0.85f)
                PokeArt.puff(c, cx + rx * 0.8f, top - 2f * p, (a - 0.15f) / 1.75f, 8f * p, 6, steam, 5, 0.85f)
            }
        }
    }

    /**
     * The bench by the stove, under the window, where the grandmother sleeps: a thick plank on legs and a stretcher, from
     * the stove's warm side to the door; once the stove is there ([dressed]) an embroidered cushion at the stove's end
     * and, while nobody sleeps on it ([made]), the red wool blanket folded at its other end. At level 1 the bare plank.
     */
    protected fun bench(made: Boolean, dressed: Boolean) {
        val bc = y0 + 0.08f; val bd = y0 + 0.82f; val bz = fl + 4.6f
        val legs = listOf(benchA + 0.15f, benchA + 1.6f, benchB - 0.15f)
        for (lx in legs) for (ly in floatArrayOf(bc + 0.15f, bd - 0.12f)) iso.post(lx, ly, fl, bz, Pal.WOOD_D)
        rod(benchA + 0.15f, bd - 0.12f, fl + 1.6f, benchB - 0.15f, bd - 0.12f, fl + 1.6f, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        iso.box(benchA, bc, bz, benchB - benchA, bd - bc, 0.9f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        if (P > 1) grain(benchA, bc, benchB, bd, bz + 0.9f, 3)
        iso.line(benchA, bd, bz + 0.9f, benchB, bd, bz + 0.9f, Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.25f))
        if (!dressed) return
        // the cushion at the stove's end, a red flower stitched on it
        val top = bz + 0.9f
        iso.box(benchA + 0.1f, bc + 0.1f, top, 0.52f, 0.6f, 1.1f, linen, Col.hex(0xE6DCC6), Col.hex(0xC4B89E))
        iso.px(benchA + 0.36f, bc + 0.7f, top + 0.6f, red)
        if (made) {
            // the blanket folded at the far end, its check across the folds
            val fa = benchB - 0.95f; val fb = benchB - 0.12f
            iso.box(fa, bc + 0.06f, top, fb - fa, 0.64f, 1.2f, WOOL_L, WOOL_M, WOOL_D)
            iso.line(fa + 0.3f, bc + 0.7f, top, fa + 0.3f, bc + 0.7f, top + 1.2f, WOOL_CHECK)
            iso.line(fa + 0.6f, bc + 0.7f, top, fa + 0.6f, bc + 0.7f, top + 1.2f, WOOL_CHECK)
            iso.line(fa, bc + 0.7f, top + 0.6f, fb, bc + 0.7f, top + 0.6f, Col.scale(WOOL_CHECK, 0.85f))
        }
    }

    /**
     * Closer up: the grain along a plank's top over x [xa]..[xb], y [ya]..[yb] at height [z], thin lines wandering along
     * its length (x, or y when [alongY]), and two nails at each end.
     */
    protected fun grain(xa: Float, ya: Float, xb: Float, yb: Float, z: Float, seed: Int, alongY: Boolean = false) {
        val gc = Col.mix(Pal.WOOD_L, Pal.WOOD_M, 0.55f)
        onFlat(z, xa, ya, xb, yb) { x, y, _, _ ->
            val len = if (alongY) y - ya else x - xa; val lenMax = if (alongY) yb - ya else xb - xa
            val across = if (alongY) (x - xa) / (xb - xa) else (y - ya) / (yb - ya)
            val g = Noise.v2(len * 1.3f, across * 6f, seed + 80)
            val nail = min(abs(len - 0.12f), abs(lenMax - len - 0.12f)) < 0.035f && (abs(across - 0.3f) < 0.07f || abs(across - 0.7f) < 0.07f)
            if (nail) Pal.WOOD_X else if (abs(g - 0.5f) < 0.03f) gc else 0
        }
    }

    /** A white milk jug with a blue band, a spout and a handle, standing at height [z]. */
    protected fun milk(x: Float, y: Float, z: Float) {
        val cx = iso.ix(x, y); val by = iso.iy(x, y, z)
        if (P > 1) { milkFine(cx + 0.5f * P, by.toFloat()); return }
        c.fillRect(cx - 3, by - 10, 7, 10, china); c.vline(cx - 3, by - 9, by - 1, Col.hex(0xFFFFFF)); c.vline(cx + 3, by - 9, by - 1, chinaD)
        c.hline(cx - 2, cx + 2, by - 11, china); c.set(cx - 4, by - 11, china); c.set(cx - 3, by - 12, china)
        c.hline(cx - 3, cx + 3, by - 7, chinaBlue); c.hline(cx - 3, cx + 3, by - 3, chinaBlue)
        c.vline(cx + 5, by - 9, by - 5, chinaD); c.set(cx + 4, by - 9, chinaD); c.set(cx + 4, by - 5, chinaD)
        c.set(cx, by - 5, chinaBlue)
        c.hline(cx - 2, cx + 1, by - 10, Col.hex(0xFFFFFF))
    }

    /** The milk jug closer up around picture x [mx] on the row [base]: bellied, two blue bands with little flowers between, a lip with milk in it, the spout, a looped handle. */
    protected fun milkFine(mx: Float, base: Float) {
        val p = P.toFloat()
        val blueL = Col.hex(0x6A92CC); val blueD = Col.hex(0x2A5088); val white = Col.hex(0xFFFFFF)
        loop(mx + 3.5f * p, base - 6.5f * p, 2f * p, 2.5f * p, 1f * p, true, chinaD, china)
        lathe(mx, base, 10.4f, { h -> when { h < 0.6f -> 3.1f + h * 0.6f; h < 8.6f -> 3.5f + 0.12f * sin(h / 8.6f * PI.toFloat()); else -> 3.5f - (h - 8.6f) * 0.5f } }) { u, du, h, _, _ ->
            val band = abs(h - 6.5f) < 0.5f || abs(h - 2.5f) < 0.5f
            when {
                band -> if (u < -0.6f) blueL else if (u > 0.55f) blueD else chinaBlue
                h > 10.4f - 0.8f / P && u > -0.8f -> white
                else -> glaze(u, du, white, china, chinaD, white)
            }
        }
        // little blue flowers painted between the bands
        for (fx in floatArrayOf(-2.3f, 0f, 2.3f)) {
            val x0 = floor(mx + fx * p).toInt(); val y0 = floor(base - 4.5f * p).toInt()
            val side = if (fx > 0f) blueD else chinaBlue
            c.set(x0, y0, Col.hex(0xF0D060)); c.set(x0 - 1, y0, side); c.set(x0 + 1, y0, side); c.set(x0, y0 - 1, side); c.set(x0, y0 + 1, side)
            if (P > 2) { c.set(x0 - 1, y0 + 2, Pal.LEAF); c.set(x0 + 1, y0 + 2, Pal.LEAF) }
        }
        // the lip, the milk in the mouth, and the spout drawn out to the left
        val ly = base - 10.9f * p
        c.polyBegin()
        c.polyAdd(mx - 1.6f * p, ly - 0.3f * p); c.polyAdd(mx - 4.7f * p, ly - 1.3f * p); c.polyAdd(mx - 4.3f * p, ly - 0.2f * p); c.polyAdd(mx - 2.2f * p, ly + 1.1f * p)
        c.polyFill(china)
        c.line(floor(mx - 4.6f * p).toInt(), floor(ly - 1.2f * p).toInt(), floor(mx - 2f * p).toInt(), floor(ly + 1f * p).toInt(), chinaD)
        c.fillEllipse(mx, ly, 2.9f * p, 0.85f * p, china)
        arc(mx, ly, 2.9f * p, 0.85f * p, white, PI.toFloat(), 2f * PI.toFloat())
        c.fillEllipse(mx + 0.2f * p, ly + 0.1f * p, 2.1f * p, 0.5f * p, chinaD)
        c.fillEllipse(mx + 0.2f * p, ly + 0.1f * p + 1f, 2.1f * p - 1f, 0.5f * p - 0.5f, Col.hex(0xFBF8F0))
    }

    /** A brown glazed water jug, round-bellied with a cream band, standing at height [z]. */
    protected fun water(x: Float, y: Float, z: Float) {
        val cx = iso.ix(x, y); val by = iso.iy(x, y, z)
        val gl = Col.hex(0xB47A4A); val g = Col.hex(0x8E5A34); val gd = Col.hex(0x5E3A20)
        if (P > 1) { waterFine(cx + 0.5f * P, by.toFloat(), gl, g, gd); return }
        for (row in 0 until 13) {
            val yy = by - 1 - row
            val half = when { row < 2 -> 3; row < 8 -> 4; row < 10 -> 3; else -> 2 }
            c.hline(cx - half, cx + half, yy, g); c.set(cx - half, yy, gl); c.set(cx + half, yy, gd)
        }
        c.hline(cx - 3, cx + 2, by - 14, gd); c.set(cx - 4, by - 14, g)
        c.hline(cx - 3, cx + 3, by - 6, Col.hex(0xE8D8B0)); c.hline(cx - 2, cx + 2, by - 7, Col.hex(0xE8D8B0))
        c.vline(cx + 6, by - 11, by - 6, gd); c.set(cx + 5, by - 12, gd); c.set(cx + 5, by - 5, gd)
        c.set(cx - 2, by - 10, Col.hex(0xD8A070))
    }

    /** The water jug closer up around picture x [mx] on the row [base]: a round belly, a cream band with a slip-trailed wave, the neck and lip, a looped handle. */
    protected fun waterFine(mx: Float, base: Float, gl: Int, g: Int, gd: Int) {
        val p = P.toFloat()
        val cream = Col.hex(0xE8D8B0); val creamD = Col.hex(0xC8B488); val hi = Col.hex(0xE8B888)
        loop(mx + 4.5f * p, base - 8f * p, 2f * p, 4f * p, 1f * p, true, gd, g)
        lathe(mx, base, 14f, { h ->
            when {
                h < 10.6f -> 2.6f + 2.0f * sqrt(max(0f, 1f - ((h - 5f) / 5.6f) * ((h - 5f) / 5.6f))) + (if (h < 0.6f) -0.3f else 0f)
                h < 13f -> 2.5f
                else -> 2.9f
            }
        }) { u, du, h, _, _ ->
            when {
                h > 13f -> if (h > 14f - 1f / P) gl else gd
                abs(h - 6f) < 1f && abs(u) < 0.86f -> {
                    val wave = 6f + 0.4f * sin(u * 9f)
                    if (abs(h - wave) < 0.55f / P + 0.12f) g else if (u > 0.5f) creamD else cream
                }
                abs(h - 6f) < 1.25f && abs(u) < 0.9f -> gd
                h > 10.6f && h < 11.1f -> gd
                else -> glaze(u, du, gl, g, gd, hi)
            }
        }
        // the mouth, dark with water, and a pinched spout
        val ly = base - 13.6f * p
        c.polyBegin()
        c.polyAdd(mx - 1.8f * p, ly - 0.2f * p); c.polyAdd(mx - 4.3f * p, ly - 0.9f * p); c.polyAdd(mx - 3.9f * p, ly + 0.3f * p); c.polyAdd(mx - 2.2f * p, ly + 0.9f * p)
        c.polyFill(g)
        c.fillEllipse(mx, ly, 2.9f * p, 0.8f * p, gl)
        c.fillEllipse(mx + 0.2f * p, ly + 0.15f * p, 2.1f * p, 0.45f * p, Col.hex(0x2E1C10))
        c.fillEllipse(mx + 0.4f * p, ly + 0.25f * p, 1.4f * p, 0.25f * p, Col.hex(0x3E5A6A))
    }

    // ------------------------------------------------------------------ level 1: the hearth, the straw bed, the shelf

    /**
     * The open hearth in the corner (level 1, before the stove): a knee-high block of rough stones in lime mortar,
     * blackened towards its top, a niche under it with the firewood's ends showing; its top of flat slabs grey with ash,
     * black round the fire.
     */
    protected fun hearth() {
        onY(hy1, x0, hx1, fl, hz) { x, z, px, py -> hearthStone(x - x0, z, px, py, 1f, niche = true) }
        onX(hx1, y0, hy1, fl, hz) { y, z, px, py -> hearthStone(y - y0 + 3.1f, z, px, py, 0.8f, niche = false) }
        onFlat(hz, x0, y0, hx1, hy1) { x, y, px, py ->
            val d = hypot(x - fireX, (y - fireY) * 1.2f)
            val fu = (x - x0) / 0.62f; val fv = (y - y0) / 0.55f
            val slab = floor(fu).toInt() + floor(fv).toInt() * 7
            val n = Noise.rnd(px, py, 81)
            when {
                d < 0.42f -> if (n < 0.3f) Col.hex(0x1E1A18) else Col.hex(0x2E2826)
                d < 0.75f + Noise.v2(x * 4f, y * 4f, 83) * 0.25f -> if (n < 0.25f) Col.hex(0x6A6660) else if (n < 0.6f) Col.hex(0x8E8A84) else Col.hex(0xA8A49C)
                x > hx1 - 0.08f || y > hy1 - 0.08f -> Col.hex(0xB0A898)
                fu - floor(fu) < 0.08f || fv - floor(fv) < 0.1f -> Col.hex(0x5A534C)
                else -> Col.scale(if (Noise.rnd(slab, 85) < 0.5f) Pal.STONE_M else Col.hex(0x9A948A), if (n > 0.96f) 0.86f else 1f)
            }
        }
    }

    /**
     * A face of the hearth at [a] cells along it and height [z]: rough stones in courses, lime mortar between, soot
     * creeping down from the top; on the front ([niche]) the dark niche with the logs' ends stacked in it. [dim] shades it.
     */
    protected fun hearthStone(a: Float, z: Float, px: Int, py: Int, dim: Float, niche: Boolean): Int {
        val h = z - fl
        if (niche && a in 0.45f..1.55f && h in 0.45f..2.9f) {
            val lx = (a - 0.45f) / 0.37f; val ly = (h - 0.45f) / 0.8f
            val r = hypot((lx - floor(lx) - 0.5f) * 1.2f, (ly - floor(ly) - 0.5f) * 1.1f)
            return Col.scale(when {
                ly > 2.8f -> Col.hex(0x1A1512)
                r < 0.2f -> Col.hex(0xC89A62)
                r < 0.38f -> Col.hex(0x8A5E36)
                r < 0.47f -> Col.hex(0x4E3420)
                else -> Col.hex(0x1E1812)
            }, dim)
        }
        val course = floor(h / 1.45f).toInt()
        val u = (a + if (course % 2 == 0) 0f else 0.27f) / 0.55f
        val stone = floor(u).toInt()
        val fu = u - stone; val fv = h / 1.45f - course
        val n = Noise.rnd(px, py, 87)
        var col = when {
            fv < 0.14f || fu < 0.1f -> if (n < 0.5f) Col.hex(0xC8BCA6) else Col.hex(0xB4A892)
            fv > 0.86f -> Pal.STONE_D
            else -> {
                val tone = Noise.rnd(stone, course, 89)
                if (tone < 0.3f) Pal.STONE_M else if (tone < 0.7f) Col.hex(0x9A9284) else Col.hex(0x847C70)
            }
        }
        if (fu in 0.1f..0.22f && fv in 0.14f..0.86f) col = Col.mix(col, Col.hex(0xFFFFFF), 0.15f)
        // soot from the fire, thickest at the top
        val sootF = ((h - 1.8f) / (hz - fl - 1.8f)).coerceIn(0f, 1f) * 0.6f
        if (sootF > 0f && Dither.at(px, py) < sootF + 0.2f) col = Col.mix(col, Col.hex(0x2A2420), sootF)
        return Col.scale(col, dim)
    }

    /**
     * The fire on the hearth: two logs crossed on the embers, small flames (higher and brighter when a dialog fires the
     * oven up), sparks and the smoke going up into the black corner. Scenery, under the pot's trivet.
     */
    protected fun hearthFire() {
        val oven = fxOn("oven")
        val cx = iso.sx(fireX, fireY); val cy = iso.sy(fireX, fireY, hz)
        val p = P.toFloat()
        prop {
            for (j in 0 until 2) {
                val a = j * 1.9f + 0.6f
                val ex = cos(a) * 3.4f * p; val ey = sin(a) * 1.4f * p
                for (tk in 0 until P + 1) c.line((cx - ex).toInt(), (cy - ey).toInt() - tk, (cx + ex).toInt(), (cy + ey).toInt() - P - tk, if (tk == 0) Pal.LOG_D else if (tk == P) Pal.LOG_L else Pal.LOG_M)
            }
        }
        prop(outline = 0) {
            glow {
                for (e in 0 until 8 * P) {
                    val ex = (cx + (Noise.rnd(e, 91) - 0.5f) * 6f * p).toInt(); val ey = (cy - Noise.rnd(e, 92) * 1.6f * p).toInt()
                    if (sin(t * 3 + e) > -0.4) c.set(ex, ey, Pal.FLAME[if (e % 3 == 0) 1 else 2])
                }
                val base = cy - 1.2f * p
                val flick = sin(t * 11).toFloat() * 0.5f + sin(t * 7.3 + 2).toFloat() * 0.5f
                val hgt = (5.2f + 3.8f * oven + flick) * p
                for (tongue in 0..2) {
                    val off = (tongue - 1) * 1.6f * p
                    val th = hgt * (1f - abs(tongue - 1) * 0.28f) * (0.8f + 0.2f * sin(t * (9 + tongue) + tongue * 1.7).toFloat())
                    val sway = sin(t * 6 + tongue).toFloat() * 0.6f * p
                    c.fillEllipse(cx + off + sway * 0.5f, base - th * 0.42f, 1.5f * p, th * 0.5f, Pal.FLAME[3])
                    c.fillEllipse(cx + off * 0.8f + sway * 0.6f, base - th * 0.34f, 1f * p, th * 0.36f, Pal.FLAME[2])
                }
                c.fillEllipse(cx, base - hgt * 0.2f, 1.4f * p, hgt * 0.24f, Pal.FLAME[1])
                c.fillEllipse(cx, base - hgt * 0.08f, 0.9f * p, hgt * 0.1f, Pal.FLAME[0])
            }
        }
        s.sparks(cx, cy - (7f + 3f * oven) * p, 2 + (3 * oven).toInt(), 57)
        s.smoke(cx - 2f * p, cy - 16f * p, 0.5f + 0.5f * oven, 58)
    }

    /** The iron trivet the pot stands on over the hearth's fire: a ring wider than the pot on three splayed legs. */
    protected fun trivet() {
        val cx = iso.sx(fireX, fireY); val ry = iso.sy(fireX, fireY, trivetZ); val by = iso.sy(fireX, fireY, hz)
        val p = P.toFloat()
        for (a in floatArrayOf(0.35f, 2.45f, 4.55f)) {
            val lx = cx + cos(a) * 6.4f * p; val ly = ry + sin(a) * 2.5f * p
            val fx = cx + cos(a) * 7.4f * p; val fy = by + sin(a) * 2.9f * p
            for (o in 0 until P) c.line(floor(lx).toInt() + o, floor(ly).toInt(), floor(fx).toInt() + o, floor(fy).toInt(), if (o == 0) ironL else ironD)
        }
        for (o in 0 until P) arc(cx, ry + o, 6.4f * p, 2.5f * p, if (o == 0) ironL else ironD)
    }

    /**
     * The straw bed along the left wall (level 1): a plain frame of boards, a headboard at the back, the straw mattress
     * (slamnjača) in striped ticking bulging over the frame with straws poking out at its seam, a linen pillow and, while
     * nobody sleeps in it ([made]), a coarse brown blanket folded at the foot.
     */
    protected fun strawBed(made: Boolean) {
        val ba = x0 + 0.06f; val bb = bedX1; val bc = bedY0; val bd = bedY1
        val frame = bedZ - 0.4f
        iso.box(ba, bc - 0.14f, fl, bb - ba, 0.14f, 6.4f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        iso.line(ba, bc, fl + 6.4f, bb, bc, fl + 6.4f, Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.25f))
        for ((lx, ly) in listOf(bb - 0.1f to bd - 0.1f, ba + 0.1f to bd - 0.1f, bb - 0.1f to bc + 0.1f)) iso.post(lx, ly, fl, frame, Pal.WOOD_D)
        iso.box(ba, bc, frame - 1.1f, bb - ba, bd - bc, 1.1f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        if (P > 1) grain(ba, bc, bb, bd, frame, 11, alongY = true)
        // the mattress: ticking striped along its length, bulging at its middle, the seam round its sides
        val mz = frame + 1.4f
        onY(bd - 0.04f, ba + 0.04f, bb - 0.04f, frame, mz) { x, z, px, _ -> ticking(x - ba, z - frame, 0.92f, px, end = true) }
        onX(bb - 0.04f, bc + 0.02f, bd - 0.04f, frame, mz) { y, z, px, _ -> ticking(y - bc, z - frame, 0.8f, px, end = false) }
        onFlat(mz, ba + 0.04f, bc + 0.02f, bb - 0.04f, bd - 0.04f) { x, y, px, _ ->
            val edge = min(min(x - ba, bb - x) * 2.2f, min(y - bc, bd - y))
            ticking(x - ba, 2f, if (edge < 0.12f) 0.9f else 1f, px, end = true)
        }
        // straws poking out along the seam
        for (k in 0 until 9) {
            val yy = bc + 0.2f + Noise.rnd(k, 93) * (bd - bc - 0.4f)
            val zz = frame + 0.7f
            val sx = iso.ix(bb, yy); val sy = iso.iy(bb, yy, zz)
            c.line(sx, sy, sx + P + (Noise.rnd(k, 94) * 2 * P).toInt(), sy - (Noise.rnd(k, 95) * 2 * P).toInt(), if (k % 2 == 0) Col.hex(0xE0C878) else Col.hex(0xC8A858))
        }
        // the pillow at the head
        iso.box(ba + 0.14f, bc + 0.1f, mz, bb - ba - 0.28f, 0.6f, 1.1f, linen, Col.hex(0xE6DCC6), Col.hex(0xC4B89E))
        if (made) {
            iso.box(ba + 0.08f, bd - 0.95f, mz, bb - ba - 0.16f, 0.8f, 1.2f, COARSE[0], COARSE[1], COARSE[2])
            iso.line(ba + 0.08f, bd - 0.15f, mz + 0.6f, bb - 0.08f, bd - 0.15f, mz + 0.6f, Col.scale(COARSE[2], 0.9f))
        }
    }

    // ------------------------------------------------------------------ beds for the night (companion/SCENES.md, "Asleep at night")

    /**
     * A straw mattress (slamnjača) rolled out for the night on the floor, or on a cot's frame at [z0] ([strawMattress]). A
     * room draws it only while someone lies on it.
     */
    protected fun mattress(xa: Float, ya: Float, xb: Float, yb: Float, alongY: Boolean, z0: Float = fl, h: Float = MAT_H, far: Boolean = false) =
        strawMattress(xa, ya, xb, yb, alongY, z0, h, far)

    /**
     * The shelf on the left wall (levels 1 and 2, before the dresser): two planks on wooden brackets, the jugs on the
     * lower one (at the counter's height); on the upper one brown clay bowls, at level 2 two plates leaning on the wall
     * too, and a bunch of herbs hanging from it to dry.
     */
    protected fun shelf() {
        val xf = x0 + 1.12f
        for (z in floatArrayOf(counterZ, fl + 16f)) {
            for (by in floatArrayOf(cupA + 0.3f, cupB - 0.3f)) {
                rod(x0, by, z - 0.6f, xf - 0.12f, by, z - 0.6f, Pal.WOOD_D, Pal.WOOD_M, Pal.WOOD_D, Pal.WOOD_X)
                rod(x0, by, z - 3.4f, xf - 0.3f, by, z - 0.7f, Pal.WOOD_D, Pal.WOOD_M, Pal.WOOD_D, Pal.WOOD_X)
            }
            iso.box(x0, cupA, z - 0.6f, xf - x0, cupB - cupA, 0.6f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
            if (P > 1) grain(x0, cupA, xf, cupB, z, 13, alongY = true)
        }
        val top = fl + 16f
        val p = P.toFloat()
        if (lv >= 2) for (k in 0..1) {
            // plates standing against the wall, white with a blue rim
            val px = iso.sx(x0 + 0.2f, cupA + 0.9f + k * 0.55f); val py = iso.sy(x0 + 0.2f, cupA + 0.9f + k * 0.55f, top + 2.2f)
            c.fillEllipse(px, py, 1.3f * p, 2.3f * p, chinaD); c.fillEllipse(px - 0.3f, py - 0.2f, 1.2f * p, 2.1f * p, china)
            arc(px - 0.3f, py - 0.2f, 1.2f * p - 0.5f, 2.1f * p - 0.5f, chinaBlue)
        }
        // clay bowls, one in the other, and a jar
        val clay = Col.hex(0xB8683E); val clayL = Col.hex(0xD48652); val clayD = Col.hex(0x7E4226)
        for ((by, n) in listOf(cupB - 0.75f to 2, cupB - 1.45f to 1)) {
            val bx = iso.sx(x0 + 0.58f, by); val bz = iso.sy(x0 + 0.58f, by, top)
            for (j in 0 until n) {
                val yy = bz - j * 1.6f * p
                c.fillEllipse(bx, yy - 1.4f * p, 3.4f * p, 1.7f * p, clayD)
                c.fillEllipse(bx - 0.4f, yy - 1.7f * p, 3.1f * p, 1.4f * p, clay)
                c.fillEllipse(bx, yy - 2.8f * p, 3.2f * p, 0.9f * p, clayL)
                c.fillEllipse(bx, yy - 2.7f * p, 2.4f * p, 0.55f * p, clayD)
            }
        }
        // the herbs: a bunch hung head down from the upper plank's front edge by a string
        val hx = iso.ix(xf - 0.05f, cupA + 0.55f); val hy = iso.iy(xf - 0.05f, cupA + 0.55f, top - 0.6f)
        c.vline(hx, hy, hy + 2 * P, Col.hex(0xC8B48A))
        for (k in -2..2) {
            val col = if (k % 2 == 0) Col.hex(0x6E8A4A) else Col.hex(0x8A9A5A)
            c.line(hx, hy + 2 * P, hx + k * P, hy + (7 + abs(k)) * P, col)
            c.set(hx + k * P, hy + (7 + abs(k)) * P, Col.hex(0xA8B070))
        }
        c.hline(hx - P / 2 - 1, hx + P / 2 + 1, hy + 2 * P, Col.hex(0xB0402E))
    }

    /**
     * The holy corner (bohkov kot, level 3), high where the left wall meets the front one, over the table's end: a little
     * corner shelf on a bracket with a candle (lit from dusk) and a sprig in a vase, the crucifix over it with the pale
     * corpus, a holy picture in a gilt frame beside it.
     */
    protected fun corner() {
        val cy = y1 - 0.48f; val sz = fl + 13.4f
        val dark = Col.hex(0x5A3A22); val darkL = Col.hex(0x7E5634); val darkD = Col.hex(0x3A2414)
        // the picture on the wall beside the corner: a gilt frame, the Virgin's blue cloak and red dress
        onX(x0 + 0.03f, cy - 2.05f, cy - 1.15f, sz + 3f, sz + 8.4f) { y, z, _, _ ->
            val u = (y - (cy - 2.05f)) / 0.9f; val v = (z - sz - 3f) / 5.4f
            when {
                u < 0.12f || u > 0.88f || v < 0.08f || v > 0.92f -> if (u < 0.12f || v > 0.92f) brassL else brass
                abs(u - 0.5f) < 0.13f && v in 0.62f..0.8f -> Col.hex(0xE8C8A0)
                v > 0.82f -> Col.hex(0xF0D060)
                abs(u - 0.5f) < 0.3f - (v - 0.2f) * 0.1f && v < 0.64f -> if (abs(u - 0.5f) < 0.1f) red else Col.hex(0x3E5C9A)
                else -> Col.hex(0x2A3A5A)
            }
        }
        // the shelf on its bracket
        rod(x0, cy, sz - 2.4f, x0 + 0.45f, cy, sz - 0.1f, darkD, dark, darkD, darkD)
        iso.box(x0, cy - 0.5f, sz - 0.5f, 0.6f, 1.0f, 0.5f, darkL, dark, darkD)
        // the crucifix: the upright and the crossbar of dark wood, a little tin roof over it
        val xc = x0 + 0.04f
        iso.box(xc, cy - 0.08f, sz + 1.6f, 0.12f, 0.16f, 7.2f, darkL, dark, darkD)
        iso.box(xc, cy - 0.44f, sz + 6.2f, 0.12f, 0.88f, 0.6f, darkL, dark, darkD)
        iso.line(xc + 0.12f, cy - 0.3f, sz + 9.4f, xc + 0.12f, cy, sz + 9.9f, Col.hex(0x9A9AA6))
        iso.line(xc + 0.12f, cy, sz + 9.9f, xc + 0.12f, cy + 0.3f, sz + 9.4f, Col.hex(0x6A6A74))
        // the corpus: head, arms along the crossbar, the body and the legs down the upright
        val skin = Col.hex(0xE8D2B0); val skinD = Col.hex(0xC8A888)
        val ax = xc + 0.13f
        iso.px(ax, cy, sz + 7.4f, skin)
        iso.line(ax, cy - 0.34f, sz + 6.7f, ax, cy + 0.34f, sz + 6.7f, skin)
        iso.post(ax, cy, sz + 3.4f, sz + 6.8f, skin)
        iso.px(ax, cy + 0.05f, sz + 5.2f, skinD)
        iso.px(ax, cy, sz + 3.4f, skinD)
        // on the shelf: a candle and a little vase with a sprig of olive, as blessed on Palm Sunday
        val canX = iso.ix(x0 + 0.32f, cy + 0.25f); val canY = iso.iy(x0 + 0.32f, cy + 0.25f, sz)
        c.fillRect(canX, canY - 3 * P, P, 3 * P, Col.hex(0xF4EEDC)); c.set(canX, canY - 3 * P, Col.hex(0xFFFFFF))
        if (env.windows > 0.35f) {
            glow { c.fillRect(canX, canY - 5 * P, P, 2 * P, Pal.FLAME[1]); c.set(canX, canY - 5 * P, Pal.FLAME[0]) }
            s.light(canX + 0.5f * P, canY - 4f * P, 12f, 0.5f)
        }
        val vx = iso.ix(x0 + 0.32f, cy - 0.28f); val vy = iso.iy(x0 + 0.32f, cy - 0.28f, sz)
        c.fillRect(vx - P, vy - 2 * P, 2 * P + 1, 2 * P, Col.hex(0x3E6FB0))
        for (k in -1..1) c.line(vx, vy - 2 * P, vx + k * 2 * P, vy - (5 + abs(k)) * P, Col.hex(0x6E8A5A))
    }

    // ------------------------------------------------------------------ the left wall

    /**
     * A chest against the left wall from [ya] to [yb]: plain planks with two iron bands, or ([painted]) blue with a cream
     * panel of red tulips on its front and its year; on little feet, its lid a shade proud, a lock.
     */
    protected fun paintedChest(ya: Float, yb: Float, painted: Boolean) {
        val xa = x0; val xb = x0 + 0.95f; val h = fl + 4.2f
        for (fy in floatArrayOf(ya + 0.1f, yb - 0.18f)) iso.box(xb - 0.16f, fy, fl, 0.14f, 0.1f, 0.5f, Pal.WOOD_D, Pal.WOOD_D, Pal.WOOD_X)
        onY(yb, xa, xb, fl + 0.5f, h) { _, z, _, _ -> if (painted) (if (z < fl + 0.9f) paintD else paintM) else Pal.WOOD_M }
        onX(xb, ya, yb, fl + 0.5f, h) { y, z, px, py -> chestFront(y - ya, z - fl - 0.5f, yb - ya, h - fl - 0.5f, painted, px, py) }
        val ll = if (painted) paintL else Pal.WOOD_L; val lm = if (painted) paintM else Pal.WOOD_M; val ld = if (painted) paintD else Pal.WOOD_D
        iso.box(xa, ya - 0.04f, h, xb - xa + 0.06f, yb - ya + 0.08f, 0.7f, ll, lm, ld)
        iso.line(xb + 0.06f, ya - 0.04f, h + 0.7f, xb + 0.06f, yb + 0.04f, h + 0.7f, Col.mix(ll, Col.hex(0xFFFFFF), 0.3f))
        iso.px(xb + 0.02f, (ya + yb) / 2f, h - 0.6f, if (painted) brass else ironL)
    }

    /** The chest's front at [a] cells along its [w] and [h] up its [hh]: the painted panel of tulips, or planks with two iron bands. */
    protected fun chestFront(a: Float, h: Float, w: Float, hh: Float, painted: Boolean, px: Int, py: Int): Int {
        if (!painted) {
            if (abs(a - 0.16f) < 0.06f || abs(a - (w - 0.16f)) < 0.06f) return if (h > hh - 0.4f) ironL else iron
            val board = floor(h / 1.2f).toInt()
            return if (h - board * 1.2f < 0.15f) Pal.WOOD_D else if (Noise.v2(a * 3f, board * 2.3f, 99) > 0.7f) Col.scale(Pal.WOOD_M, 0.92f) else Pal.WOOD_M
        }
        if (a < 0.08f || w - a < 0.08f || h < 0.3f || h > hh - 0.3f) return paintL
        val inPanel = a in 0.22f..(w - 0.22f) && h in 0.8f..(hh - 0.7f)
        if (!inPanel) return if (Dither.at(px, py) < 0.15f) paintD else paintM
        // three tulips on the cream, the year under the middle one
        val u = (a - 0.22f) / (w - 0.44f); val v = (h - 0.8f) / (hh - 1.5f)
        val tu = (u * 3f) - floor(u * 3f) - 0.5f
        return when {
            abs(tu) < 0.12f && v in 0.52f..0.82f -> if (abs(tu) < 0.05f && v > 0.74f) Col.hex(0xF0C23A) else red
            abs(tu) < 0.04f && v in 0.2f..0.52f -> leafC
            abs(abs(tu) - 0.14f) < 0.05f && v in 0.3f..0.45f -> leafC
            v < 0.14f && u in 0.3f..0.7f && (px / max(1, P)) % 2 == 0 -> Col.hex(0x8A4A2A)
            else -> if (Noise.rnd(px, py, 5) < 0.05f) Col.hex(0xD8C8A2) else Col.hex(0xEBDDB8)
        }
    }

    /** The wall clock: a walnut case under a little gable, a white dial with its hands at the hour, a swinging pendulum, chains and weights. */
    protected fun clock() {
        val ya = clockY - 0.5f; val yb = clockY + 0.5f; val za = fl + 16.5f; val zb = fl + 22.5f; val dep = 0.32f
        val l = Col.hex(0xA0703E); val m = Col.hex(0x7A4E2C); val d = Col.hex(0x5A361E)
        // the pendulum and the weights hang against the wall below the case
        val ang = sin(t * PI).toFloat() * 0.34f
        val pz = za - 0.2f; val len = 7f
        val bobY = clockY + sin(ang) * len / 4f; val bobZ = pz - cos(ang) * len
        iso.line(x0 + 0.15f, clockY, pz, x0 + 0.15f, bobY, bobZ, brassD)
        val bx = iso.sx(x0 + 0.15f, bobY); val byy = iso.sy(x0 + 0.15f, bobY, bobZ)
        if (P > 1) {
            // the bob: a brass disc with a rim, lit at its upper left
            val p = P.toFloat()
            c.fillCircle(bx, byy, 1.7f * p, brassD); c.fillCircle(bx - 0.4f, byy - 0.4f, 1.7f * p - 1f, brass)
            c.fillCircle(bx - 0.5f * p, byy - 0.5f * p, 0.6f * p, brassL); c.set(floor(bx - 0.7f * p).toInt(), floor(byy - 0.7f * p).toInt(), Col.hex(0xFFFFFF))
        } else { c.fillCircle(bx, byy, 1.7f, brass); c.set(bx.toInt() - 1, byy.toInt() - 1, brassL) }
        for ((wy, wz) in listOf(clockY - 0.3f to za - 7.5f, clockY + 0.3f to za - 5.5f)) {
            val cx = iso.ix(x0 + 0.2f, wy); val cTop = iso.iy(x0 + 0.2f, wy, za); val cBot = iso.iy(x0 + 0.2f, wy, wz)
            if (P > 1) { chain(cx + P / 2, cTop, cBot, ironL, ironD); pineCone(cx + 0.5f * P, cBot.toFloat()); continue }
            for (yy in cTop until cBot) c.set(cx, yy, if (yy % 2 == 0) ironL else ironD)
            c.fillRect(cx - 1, cBot, 3, 6, brass); c.vline(cx - 1, cBot, cBot + 5, brassL); c.vline(cx + 1, cBot + 1, cBot + 5, brassD)
            c.set(cx, cBot + 6, brassD); c.hline(cx - 1, cx + 1, cBot + 2, brassD)
        }
        // the case
        iso.box(x0, ya, za, dep, yb - ya, zb - za, l, m, d)
        val xf = x0 + dep
        onX(xf, ya, yb, za, zb) { y, z, _, _ -> if (min(y - ya, yb - y) < 0.08f) l else if (z < za + 0.5f) d else m }
        // the dial, round and white in a brass ring, its hands at the hour
        val dx = iso.sx(xf, clockY); val dy = iso.sy(xf, clockY, (za + zb) / 2f)
        val hr = (s.hour % 12f) / 12f * 2f * PI.toFloat(); val mn = (s.hour % 1f) * 2f * PI.toFloat()
        if (P > 1) dial(dx, dy, hr, mn) else {
            c.fillCircle(dx, dy, 4.3f, brassD); c.fillCircle(dx, dy, 3.8f, brass); c.fillCircle(dx, dy, 3.2f, china)
            for (k in 0 until 4) { val a = k * PI.toFloat() / 2f; c.set((dx + sin(a) * 2.4f).toInt(), (dy - cos(a) * 2.4f).toInt(), chinaD) }
            c.line(dx.toInt(), dy.toInt(), (dx + sin(hr) * 1.8f).toInt(), (dy - cos(hr) * 1.8f).toInt(), Pal.OUTLINE)
            c.line(dx.toInt(), dy.toInt(), (dx + sin(mn) * 2.8f).toInt(), (dy - cos(mn) * 2.8f).toInt(), Col.hex(0x3A3A44))
        }
        // the gable and its finial
        c.polyBegin()
        c.polyAdd(iso.sx(xf, ya - 0.1f), iso.sy(xf, ya - 0.1f, zb)); c.polyAdd(iso.sx(xf, yb + 0.1f), iso.sy(xf, yb + 0.1f, zb))
        c.polyAdd(iso.sx(xf, clockY), iso.sy(xf, clockY, zb + 2.6f))
        c.polyFill(l)
        if (P > 1) {
            // closer up: a carved leaf under the gable's peak and the gable's lower edge in shadow
            iso.line(xf, ya - 0.1f, zb, xf, yb + 0.1f, zb, d)
            val gx = iso.sx(xf, clockY); val gy = iso.sy(xf, clockY, zb + 1.1f)
            c.fillEllipse(gx, gy, 1.2f * P, 0.7f * P, m); c.fillEllipse(gx - 0.3f, gy - 0.3f, 1.2f * P - 1f, 0.7f * P - 0.6f, Col.mix(l, Col.hex(0xFFFFFF), 0.15f))
            c.vline(floor(gx).toInt(), floor(gy - 0.5f * P).toInt(), floor(gy + 0.6f * P).toInt(), d)
        }
        iso.line(xf, ya - 0.1f, zb, xf, clockY, zb + 2.6f, Col.mix(l, Col.hex(0xFFFFFF), 0.3f))
        knob(xf, clockY, zb + 3f, brass, brassL, brassD)
    }

    /** The clock's dial closer up around ([dx], [dy]): a brass ring lit at its upper left, the white face, twelve hour marks, the hands at [hr] and [mn], a pin. */
    protected fun dial(dx: Float, dy: Float, hr: Float, mn: Float) {
        val p = P.toFloat()
        val face = china; val faceD = Col.hex(0xE6E1D4)
        for (yy in max(floor(dy - 4.4f * p).toInt(), c.top)..min(ceil(dy + 4.4f * p).toInt(), c.bottom - 1)) {
            for (xx in max(floor(dx - 4.4f * p).toInt(), c.left)..min(ceil(dx + 4.4f * p).toInt(), c.right - 1)) {
                val ex = xx + 0.5f - dx; val ey = yy + 0.5f - dy
                val r = hypot(ex, ey) / p
                if (r > 4.3f) continue
                val lit = ex + ey < -0.3f * r * p
                c.set(xx, yy, when {
                    r > 3.8f -> if (lit && r < 4.3f - 0.8f / p) brass else brassD
                    r > 3.2f -> if (lit) brassL else if (ex + ey > 0.5f * r * p) brassD else brass
                    r > 3.2f - 1f / p -> chinaD
                    else -> if (ex + ey > 0.9f * r * p && r > 1.8f) faceD else face
                })
            }
        }
        for (k in 0 until 12) {
            val a = k * PI.toFloat() / 6f; val r0 = if (k % 3 == 0) 2.2f else 2.6f
            c.line(floor(dx + sin(a) * r0 * p).toInt(), floor(dy - cos(a) * r0 * p).toInt(), floor(dx + sin(a) * 2.95f * p).toInt(), floor(dy - cos(a) * 2.95f * p).toInt(), if (k % 3 == 0) Col.hex(0x3A3A44) else chinaD)
        }
        val ox = floor(dx).toInt(); val oy = floor(dy).toInt()
        c.line(ox, oy, floor(dx + sin(hr) * 1.8f * p).toInt(), floor(dy - cos(hr) * 1.8f * p).toInt(), Pal.OUTLINE)
        c.line(ox + 1, oy, floor(dx + sin(hr) * 1.8f * p).toInt() + 1, floor(dy - cos(hr) * 1.8f * p).toInt(), Pal.OUTLINE)
        c.line(ox, oy, floor(dx + sin(mn) * 2.8f * p).toInt(), floor(dy - cos(mn) * 2.8f * p).toInt(), Col.hex(0x3A3A44))
        c.set(ox, oy, brass)
    }

    /** A clock weight closer up: a brass pine cone hanging from its hook under picture x [mx] from the row [top]: scales in rows, lit from the left. */
    protected fun pineCone(mx: Float, top: Float) {
        val p = P.toFloat()
        val base = top + 7f * p
        lathe(mx, base, 7f, { h -> if (h < 1.2f) 0.35f + h * 0.9f else if (h < 6.4f) 1.5f else 1.5f - (h - 6.4f) * 1.2f }) { u, _, h, _, _ ->
            // scales in rows, each rounded at its lower tip and lit on its upper middle, the rows offset by half a scale
            val row = floor(h * 1.1f).toInt(); val fv = h * 1.1f - row
            val fu0 = u * 1.3f + (if (row % 2 == 0) 0.5f else 0f); val fu = fu0 - floor(fu0)
            val edge = 0.18f + 0.9f * (2f * fu - 1f) * (2f * fu - 1f)
            when {
                h > 6.4f -> if (u < 0f) brassL else brass
                fv < edge -> if (u > 0.4f) Col.scale(brassD, 0.85f) else brassD
                u > 0.55f -> Col.mix(brass, brassD, 0.5f)
                fv > 0.62f && abs(fu - 0.5f) < 0.22f -> brassL
                else -> brass
            }
        }
        c.set(floor(mx).toInt(), floor(top).toInt() - 1, ironL)
    }

    /**
     * The painted cupboard: a blue cabinet with cream panels and red tulips, two drawers and two doors, the counter;
     * on it the open shelves with plates standing and cups on hooks, under a cornice.
     */
    protected fun cupboard() {
        val xf = x0 + 1.0f; val xs = x0 + 0.62f; val zc = fl + 8.5f; val zt = fl + 21.5f
        onY(cupB, x0, xf, fl, zc) { _, z, _, _ -> if (z < fl + 0.8f) paintD else paintM }
        onX(xf, cupA, cupB, fl, zc) { y, z, px, py -> cabinet(y, z, px, py) }
        iso.box(x0, cupA - 0.06f, zc, xf - x0 + 0.1f, cupB - cupA + 0.12f, 0.8f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        val z1 = zc + 0.8f
        onY(cupB - 0.04f, x0, xs, z1, zt) { _, _, _, _ -> paintM }
        onX(xs, cupA + 0.02f, cupB - 0.04f, z1, zt) { y, z, px, py -> shelves(y, z, px, py) }
        iso.box(x0, cupA - 0.1f, zt, xs - x0 + 0.16f, cupB - cupA + 0.16f, 1.3f, paintL, paintM, paintD)
        iso.line(x0, cupB + 0.06f, zt + 1.3f, xs + 0.16f, cupB + 0.06f, zt + 1.3f, Col.mix(paintL, Col.hex(0xFFFFFF), 0.3f))
        iso.line(xs + 0.16f, cupA - 0.1f, zt + 1.3f, xs + 0.16f, cupB + 0.06f, zt + 1.3f, Col.mix(paintL, Col.hex(0xFFFFFF), 0.3f))
    }

    protected fun cabinet(y: Float, z: Float, px: Int, py: Int): Int {
        val h = z - fl; val a = y - cupA; val w = cupB - cupA
        if (h < 0.8f) return paintD
        if (a < 0.09f || w - a < 0.09f) return paintL
        // the drawers under the counter, with knobs
        if (h > 6f) {
            val da = if (a < w / 2f) a else a - w / 2f
            if (h < 6.35f || abs(a - w / 2f) < 0.06f) return paintD
            if (abs(da - w / 4f) < 0.07f && abs(h - 7.2f) < 0.35f) return brass
            return paintM
        }
        // the doors: cream panels with a red tulip
        val da = if (a < w / 2f) a else a - w / 2f
        if (abs(a - w / 2f) < 0.05f) return paintD
        val inPanel = da in 0.2f..(w / 2f - 0.18f) && h in 1.6f..5.3f
        if (!inPanel) return if (abs(da - (w / 2f - 0.11f)) < 0.04f && abs(h - 3.4f) < 0.5f) brass else paintM
        val pc = w / 4f
        val dx = (da - pc) * 8f; val dz = (h - 3.7f) * 2f
        return when {
            abs(dx) < 1.2f && dz in -0.5f..2.2f -> red
            abs(dx) < 0.6f && dz in -3.5f..-0.5f -> leafC
            abs(abs(dx) - 1.8f) < 0.7f && dz in -2.6f..-1.4f -> leafC
            da < 0.26f || h > 5.1f -> Col.hex(0xD4C49E)
            else -> if (Noise.rnd(px, py, 5) < 0.05f) Col.hex(0xD8C8A2) else Col.hex(0xEBDDB8)
        }
    }

    /** The open shelves at ([y], [z]): plates standing on the upper two, cups hanging under the lowest. */
    protected fun shelves(y: Float, z: Float, px: Int, py: Int): Int {
        val h = z - fl; val a = y - cupA
        if (a < 0.1f || cupB - y < 0.12f) return paintL
        val back = Col.hex(0x3E4E66)
        if (h in 9.3f..10.1f || h in 13.6f..14.4f || h in 17.6f..18.4f) return if (h - floor(h) < 0.3f) Pal.WOOD_D else Pal.WOOD_L
        // plates on the upper shelves: white with a blue rim and a red dot
        for (row in 0..1) for (k in 0 until 3) {
            val yc = 0.42f + k * 0.72f + row * 0.12f
            val dd = hypot((a - yc) * 4f, h - (16f + row * 4f))
            if (dd < 1.95f && P > 1) {
                // closer up: a painted flower of five petals, a dotted ring round it, the blue rim, the edge lit on top
                val ang = atan2(h - (16f + row * 4f), (a - yc) * 4f)
                return when {
                    dd < 0.16f -> Col.hex(0xF0C23A)
                    dd < 0.46f + 0.14f * cos(ang * 5f) -> if (dd > 0.4f && ang < 0f) Col.hex(0xA82A24) else red
                    abs(dd - 0.88f) < 0.12f && Math.floorMod(floor((ang + PI.toFloat()) * 10f / PI.toFloat()).toInt(), 2) == 0 -> chinaBlue
                    dd < 1.2f -> china
                    dd < 1.55f -> chinaBlue
                    else -> if (ang > 0.4f && ang < 2.6f) china else chinaD
                }
            }
            if (dd < 1.95f) return when { dd < 0.5f -> red; dd < 1.2f -> china; dd < 1.55f -> chinaBlue; else -> chinaD }
        }
        // cups on hooks under the middle shelf
        if (h in 10.2f..12.6f) for (k in 0 until 3) {
            val cc = 0.45f + k * 0.7f
            val dx = (a - cc) * 8f
            if (P > 1) {
                // closer up: rounded at the foot, shaded round, the handle a ring, the hook bent
                val hk = h - 10.2f
                if (h > 12f && abs(dx) < 0.6f) return if (h > 12.4f && dx > -0.1f) brassD else if (dx < 0f) brassL else brass
                val half = 2f - (if (hk < 0.45f) (0.45f - hk) * 1.8f else 0f)
                if (h < 12f && abs(dx) < half) return when {
                    h > 11.6f -> if (dx > 1f) Col.hex(0x2A5088) else chinaBlue
                    dx < -1.3f -> Col.hex(0xFFFFFF)
                    dx > 1.2f -> chinaD
                    else -> china
                }
                val rx = (dx - 2.2f) / 1.05f; val ry = (h - 11.15f) / 0.6f
                val rr = rx * rx + ry * ry
                if (dx > 1.6f && rr < 1f && rr > 0.3f) return chinaD
                continue
            }
            if (h > 12.3f && abs(dx) < 0.6f) return brass
            if (h < 12f && abs(dx) < 2f) return if (h > 11.6f) chinaBlue else china
            if (h < 11.6f && dx in 2f..3.2f && h > 10.8f) return chinaD
        }
        // a jar on the bottom shelf
        if (h in 10.1f..12.6f && a > 2.0f) {
            if (P == 1) return if (h > 12f) red else Col.hex(0xE8D8A0)
            // closer up: a checked cloth tied over it with a string, a paper label, the glass's glint
            val ja = (a - 2.0f) / (cupB - cupA - 0.12f - 2.0f)
            return when {
                h > 12f -> if (((px + py) / 2) % 2 == 0) red else linen
                h > 11.85f -> Col.hex(0x8A6A48)
                h in 10.75f..11.5f && ja in 0.2f..0.85f -> if (abs(h - 11.25f) < 0.5f / K || abs(h - 11.0f) < 0.5f / K) Col.hex(0x9A8A70) else Col.hex(0xF6F0E0)
                ja < 0.2f -> Col.hex(0xF6ECC4)
                ja > 0.82f -> Col.hex(0xC0A868)
                else -> Col.hex(0xE8D8A0)
            }
        }
        return back
    }

    // ------------------------------------------------------------------ the table

    /** A chair on the far side of the table: four legs, the seat, a back with a heart cut out of it. */
    protected fun chair(x: Float, y: Float) {
        val sw = 0.72f; val seat = fl + 4f
        // the back, its posts running down into the back legs; a heart cut out of the board
        val hx = iso.ix(x + sw / 2f, y); val hy = iso.iy(x + sw / 2f, y, seat + 4.4f)
        onY(y, x, x + sw, fl, seat + 7.4f) { xx, z, px, py ->
            val a = xx - x; val hz = z - seat
            val dx = px - hx; val dy = py - hy
            val heart = if (P == 1) (dy == -1 && (dx == -1 || dx == 1)) || (dy == 0 && dx in -1..1) || (dy == 1 && dx == 0)
            else heartAt((px + 0.5f - hx - 0.5f * P) / (1.5f * P), (py + 0.5f - hy - 0.5f * P) / (1.5f * P))
            val topZ = 6.3f + 0.5f * sin(PI.toFloat() * a / sw)
            when {
                a < 0.1f || a > sw - 0.1f -> if (hz < 7.2f) (if (a < 0.05f) Pal.WOOD_L else Pal.WOOD_M) else 0
                heart -> 0
                hz in 2.2f..topZ -> if (hz < 2.6f) Pal.WOOD_D else if (P > 1) chairBack(a, hz, topZ, px, py, hx, hy) else Pal.WOOD_L
                else -> 0
            }
        }
        iso.post(x + 0.06f, y + sw - 0.06f, fl, seat, Pal.WOOD_M)
        iso.post(x + sw - 0.06f, y + sw - 0.06f, fl, seat, Pal.WOOD_D)
        iso.box(x, y, seat - 0.8f, sw, sw, 0.8f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
    }

    /** Whether (x, y) (−1 … 1 across the heart's box, y down) lies in a heart. */
    protected fun heartAt(x: Float, y: Float): Boolean {
        val hx = x * 1.22f; val hy = -y * 1.18f + 0.18f
        val q = hx * hx + hy * hy - 1f
        return q * q * q - hx * hx * hy * hy * hy <= 0f
    }

    /** A chair's back board closer up at [a] cells across and [hz] up (its top at [topZ]): grain following its arch, the heart's cut edge in shadow below and lit above. */
    protected fun chairBack(a: Float, hz: Float, topZ: Float, px: Int, py: Int, hx: Int, hy: Int): Int {
        val p = P.toFloat()
        // the cut's edge: its lower face lit under the hole, a shadow over it
        val ex = (px + 0.5f - hx - 0.5f * p) / (1.5f * p); val ey = (py + 0.5f - hy - 0.5f * p) / (1.5f * p)
        if (heartAt(ex, ey - 1f / (1.5f * p))) return Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.2f)
        if (heartAt(ex, ey + 1f / (1.5f * p))) return Pal.WOOD_D
        if (topZ - hz < 0.5f / K * 2f) return Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.2f)
        val g = Noise.v2(a * 5f, (hz - 2.2f) * 0.5f + a * 1.5f, 83)
        return if (abs(g - 0.5f) < 0.035f) Col.mix(Pal.WOOD_L, Pal.WOOD_M, 0.6f) else Pal.WOOD_L
    }

    /** The table: turned legs, a stretcher, the top under a red checked cloth that hangs over its front edges with a hem. */
    protected fun table() {
        for ((lx, ly) in listOf(tb - 0.18f to tc + 0.18f, ta + 0.18f to td - 0.18f, tb - 0.18f to td - 0.18f)) {
            iso.box(lx - 0.1f, ly - 0.1f, fl, 0.2f, 0.2f, tz - fl - 1f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
            iso.line(lx - 0.1f, ly + 0.1f, fl + 2.6f, lx + 0.1f, ly + 0.1f, fl + 2.6f, Pal.WOOD_X)
            if (P > 1) {
                // closer up the turning shows: the light down its round, more rings, each with a lit bead over it
                iso.line(lx - 0.06f, ly + 0.1f, fl + 0.2f, lx - 0.06f, ly + 0.1f, tz - 1.2f, Col.mix(Pal.WOOD_L, Pal.WOOD_M, 0.3f))
                for (rz in floatArrayOf(fl + 0.9f, fl + 2.6f, tz - 2.2f)) {
                    iso.line(lx - 0.1f, ly + 0.1f, rz + 0.4f, lx + 0.1f, ly + 0.1f, rz + 0.4f, Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.2f))
                    iso.line(lx - 0.1f, ly + 0.1f, rz, lx + 0.1f, ly + 0.1f, rz, Pal.WOOD_X)
                    iso.line(lx + 0.1f, ly - 0.1f, rz, lx + 0.1f, ly + 0.1f, rz, Pal.WOOD_X)
                }
            }
        }
        rod(ta + 0.3f, td - 0.18f, fl + 1.6f, tb - 0.3f, td - 0.18f, fl + 1.6f, Pal.WOOD_D, Pal.WOOD_M, Pal.WOOD_D, Pal.WOOD_X)
        iso.box(ta, tc, tz - 1f, tb - ta, td - tc, 1f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        val zc = tz + 0.15f; val drop = 1.9f; val e = 0.06f
        onFlat(zc, ta - e, tc - e, tb + e, td + e) { x, y, px, py -> check(x - ta, y - tc, 0.5f, 1f, px, py) }
        onY(td + e, ta - e, tb + e, zc - drop, zc) { x, z, px, py ->
            val hem = zc - drop + 0.35f * abs(sin((x - ta) * 10f))
            if (z < hem) 0 else if (z < hem + 0.45f) hemAt(z - hem, px, Col.hex(0xC8423A)) else check(x - ta, (zc - z) * 0.3f + 0.2f, 0.5f, 0.9f, px, py)
        }
        onX(tb + e, tc - e, td + e, zc - drop, zc) { y, z, px, py ->
            val hem = zc - drop + 0.35f * abs(sin((y - tc) * 10f))
            if (z < hem) 0 else if (z < hem + 0.45f) hemAt(z - hem, px, Col.hex(0x9A3028)) else check(y - tc, (zc - z) * 0.3f + 0.2f, 0.5f, 0.76f, px, py)
        }
    }

    /** The cloth's hem [dz] nominal px above its edge: plain red; closer up with a white running stitch along it. */
    protected fun hemAt(dz: Float, px: Int, col: Int): Int {
        if (P == 1) return col
        if (abs(dz - 0.24f) < 0.5f / K && (px / 2) % 2 == 0) return Col.hex(0xF6EEE0)
        return if (dz < 0.5f / K) Col.scale(col, 0.8f) else col
    }

    /**
     * The cloth at ([u], [v]) cells: white linen checked by red bands [p] apart, deeper red where they cross; [dim] shades
     * it. Closer up the weave shows: where one band crosses the white, red and white threads alternate.
     */
    protected fun check(u: Float, v: Float, p: Float, dim: Float, px: Int = 0, py: Int = 0): Int {
        val a = u - floor(u / p) * p < 0.15f; val b = v - floor(v / p) * p < 0.15f
        if (P > 1 && (a != b)) return Col.scale(if ((px + py) % 2 == 0) Col.hex(0xE69A92) else Col.hex(0xF0B8B0), dim)
        return Col.scale(if (a && b) Col.hex(0xC8423A) else if (a || b) Col.hex(0xEAA69E) else linen, dim)
    }

    /** A round loaf on a board, scored across the top, the heel cut off. */
    protected fun bread(x: Float, y: Float, z: Float) {
        val cx = iso.sx(x, y); val cy = iso.sy(x, y, z)
        if (P > 1) { breadFine(cx, cy); return }
        c.fillEllipse(cx + 1f, cy + 0.5f, 8f, 3.4f, Pal.WOOD_D); c.fillEllipse(cx + 1f, cy, 7.5f, 3f, Pal.WOOD_L)
        val crust = Col.hex(0xC98A46); val crustL = Col.hex(0xE6AE66); val crustD = Col.hex(0x9A6230)
        c.fillEllipse(cx, cy - 2.5f, 6f, 3.8f, crustD)
        c.fillEllipse(cx - 0.5f, cy - 3f, 5.5f, 3.3f, crust)
        c.fillEllipse(cx - 1.5f, cy - 4f, 3.4f, 1.8f, crustL)
        for (k in 0..2) c.line((cx - 3 + k * 2.5f).toInt(), (cy - 5.5f).toInt(), (cx - 1.5f + k * 2.5f).toInt(), (cy - 3.5f).toInt(), crustD)
        c.fillRect((cx + 5).toInt(), (cy - 4).toInt(), 2, 4, Col.hex(0xF4E2B8)); c.vline((cx + 7).toInt(), (cy - 4).toInt(), cy.toInt() - 1, crust)
    }

    /** The loaf closer up around ([cx], [cy]): the board's grain, a crust shaded and dusted with flour, scores with their torn ears, the crumb of the cut face. */
    protected fun breadFine(cx: Float, cy: Float) {
        val p = P.toFloat()
        val crust = Col.hex(0xC98A46); val crustL = Col.hex(0xE6AE66); val crustD = Col.hex(0x9A6230); val crumb = Col.hex(0xF4E2B8)
        c.fillEllipse(cx + 1f * p, cy + 0.5f * p, 8f * p, 3.4f * p, Pal.WOOD_D); c.fillEllipse(cx + 1f * p, cy, 7.5f * p, 3f * p, Pal.WOOD_L)
        arc(cx + 1f * p, cy, 7.5f * p - 0.5f, 3f * p - 0.5f, Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.3f), PI.toFloat() * 1.05f, PI.toFloat() * 1.6f)
        for (k in 0..2) arc(cx + 1f * p, cy + (k - 1) * 0.9f * p + 0.6f * p, (4f + k * 1.3f) * p, (1.1f + k * 0.35f) * p, Col.mix(Pal.WOOD_L, Pal.WOOD_M, 0.6f), 0.15f, PI.toFloat() - 0.15f, 1 + k % 2)
        // the loaf
        c.fillEllipse(cx, cy - 2.5f * p, 6f * p, 3.8f * p, crustD)
        c.fillEllipse(cx - 0.5f * p, cy - 3f * p, 5.5f * p, 3.3f * p, crust)
        c.fillEllipse(cx - 1.5f * p, cy - 4f * p, 3.4f * p, 1.8f * p, crustL)
        // flour on its top, darker bake specks
        for (k in 0 until 26 * P) {
            val a = Noise.rnd(k, 51) * 2f * PI.toFloat(); val r = sqrt(Noise.rnd(k, 52))
            val fx = cx - 1f * p + cos(a) * r * 4.2f * p; val fy = cy - 4.1f * p + sin(a) * r * 1.9f * p
            c.set(floor(fx).toInt(), floor(fy).toInt(), if (k % 4 == 0) Col.hex(0xB07038) else Col.hex(0xF6E8CC))
        }
        // the scores, each with its ear lit above the cut
        for (k in 0..2) {
            val x0 = floor(cx + (-3f + k * 2.5f) * p).toInt(); val y0 = floor(cy - 5.5f * p).toInt()
            val x1 = floor(cx + (-1.5f + k * 2.5f) * p).toInt(); val y1 = floor(cy - 3.5f * p).toInt()
            c.line(x0 - 1, y0, x1 - 1, y1, Col.hex(0xF0C27E))
            c.line(x0, y0, x1, y1, crustD)
            c.line(x0 + 1, y0, x1 + 1, y1, Col.hex(0xE8C898))
        }
        // the heel cut off: the loaf's section, a dome of crumb, pocked, in its crust
        val fcx = cx + 5.9f * p; val frx = 1.25f * p; val fry = 4.3f * p
        for (yy in max(floor(cy - fry).toInt(), c.top)..min(ceil(cy).toInt() - 1, c.bottom - 1)) {
            for (xx in max(floor(fcx - frx).toInt(), c.left)..min(ceil(fcx + frx).toInt(), c.right - 1)) {
                val u = (xx + 0.5f - fcx) / frx; val v = (yy + 0.5f - cy) / fry
                val r = sqrt(u * u + v * v)
                if (v > 0f || r > 1f) continue
                c.set(xx, yy, when {
                    r > 1f - 1.3f / fry || u > 1f - 1.3f / frx -> crustD
                    r > 1f - 2.4f / fry -> crust
                    Noise.rnd(xx, yy, 57) < 0.18f -> Col.hex(0xDCC08C)
                    v > -0.25f -> Col.hex(0xECD6A8)
                    else -> crumb
                })
            }
        }
    }

    /** A little salt cellar of turned wood, heaped with white salt. */
    protected fun salt(x: Float, y: Float, z: Float) {
        val cx = iso.ix(x, y); val by = iso.iy(x, y, z)
        if (P > 1) {
            // closer up: a turned cellar with rings, a heap of salt in it glittering
            val p = P.toFloat(); val mx = cx + 0.5f * p; val base = by.toFloat()
            c.fillEllipse(mx, base - 4.2f * p, 2.6f * p, 1.9f * p, Col.hex(0xD8E2EA))
            c.fillEllipse(mx - 0.4f * p, base - 4.4f * p, 2.1f * p, 1.6f * p, Col.hex(0xFFFFFF))
            for (k in 0 until 5 * P) c.set(floor(mx + (Noise.rnd(k, 61) - 0.5f) * 4f * p).toInt(), floor(base - 4.1f * p - Noise.rnd(k, 62) * 1.8f * p).toInt(), if (k % 3 == 0) Col.hex(0xC0CCD8) else Col.hex(0xF0F6FC))
            lathe(mx, base, 3f, { h -> if (h < 0.7f) 2.1f else 2.1f + (h - 0.7f) * 0.25f }) { u, du, h, _, _ ->
                if (abs(h - 1.4f) < 0.5f / P) Pal.WOOD_D else glaze(u, du, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D, Col.hex(0xD8A068))
            }
            lathe(mx, base - 3f * p, 1f, { 3.5f }) { u, _, h, _, _ -> if (h > 1f - 1f / P) Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.25f) else if (u > 0.6f) Pal.WOOD_M else Pal.WOOD_L }
            return
        }
        c.fillRect(cx - 2, by - 3, 5, 3, Pal.WOOD_M); c.vline(cx - 2, by - 3, by - 1, Pal.WOOD_L); c.vline(cx + 2, by - 3, by - 1, Pal.WOOD_D)
        c.hline(cx - 3, cx + 3, by - 4, Pal.WOOD_L)
        c.hline(cx - 2, cx + 2, by - 5, Col.hex(0xFFFFFF)); c.hline(cx - 1, cx + 1, by - 6, Col.hex(0xFFFFFF)); c.set(cx + 1, by - 5, Col.hex(0xD8E2EA))
    }

    /** A bowl of soup, white with a blue band, the broth golden with carrot and parsley; it steams (more when a dialog says it is hot). */
    protected fun bowl(x: Float, y: Float, z: Float) {
        val cx = iso.sx(x, y); val by = iso.sy(x, y, z)
        wisps(cx, by - 8f * P, fxOn("steam"), 23)
        if (P > 1) { bowlFine(cx, by); return }
        for (row in 0 until 5) {
            val half = 6f - row * row * 0.22f
            val yy = (by - 1 - row).toInt()
            c.hline((cx - half).toInt(), (cx + half).toInt(), yy, china)
            c.set((cx - half).toInt(), yy, Col.hex(0xFFFFFF)); c.set((cx + half).toInt(), yy, chinaD)
        }
        c.hline((cx - 5).toInt(), (cx + 5).toInt(), (by - 3).toInt(), chinaBlue)
        c.fillEllipse(cx, by - 6f, 6.5f, 2.4f, chinaD)
        c.fillEllipse(cx, by - 6f, 5.3f, 1.7f, Col.hex(0xE8A83A))
        c.set((cx - 2).toInt(), (by - 7).toInt(), Col.hex(0xF6CC66)); c.set((cx + 2).toInt(), (by - 6).toInt(), Col.hex(0xE06A2A))
        c.set(cx.toInt(), (by - 6).toInt(), Col.hex(0x5E9A3A)); c.set((cx - 3).toInt(), (by - 6).toInt(), Col.hex(0xE06A2A))
        steam(cx, by - 8f, 3, 13)
    }

    /** The bowl closer up around picture x [cx] on the row [by]: a foot, a curved body with a blue band and a fine line, the rim, beef soup with noodles, carrot and parsley. */
    protected fun bowlFine(cx: Float, by: Float) {
        val p = P.toFloat()
        val white = Col.hex(0xFFFFFF)
        lathe(cx, by, 4f, { h -> if (h < 0.7f) 3.6f else 4.3f + 2.2f * sqrt(((h - 0.7f) / 3.3f).coerceIn(0f, 1f)) }) { u, du, h, _, _ ->
            when {
                h < 0.7f -> if (h > 0.7f - 1f / P) chinaD else if (u > 0.5f) chinaD else china
                abs(h - 2.45f) < 0.45f -> if (u < -0.7f) Col.hex(0x6A92CC) else if (u > 0.6f) Col.hex(0x2A5088) else chinaBlue
                abs(h - 1.55f) < 0.5f / P -> chinaBlue
                else -> glaze(u, du, white, china, chinaD, white)
            }
        }
        // the rim and the far inside wall, the broth in it
        c.fillEllipse(cx, by - 6f * p, 6.5f * p, 2.4f * p, china)
        arc(cx, by - 6f * p, 6.5f * p - 0.5f, 2.4f * p - 0.5f, white, PI.toFloat() * 0.05f, PI.toFloat() * 0.95f)
        c.fillEllipse(cx, by - 6.1f * p, 6.5f * p - 1.5f, 2.4f * p - 1f, chinaD)
        val broth = Col.hex(0xE8A83A)
        c.fillEllipse(cx, by - 5.8f * p, 5.3f * p, 1.7f * p, broth)
        c.fillEllipse(cx - 1f * p, by - 6.1f * p, 3.4f * p, 0.9f * p, Col.hex(0xEEB446))
        // noodles: thin pale squiggles
        for (k in 0 until 3 + P) {
            val nx = cx + (Noise.rnd(k, 91) - 0.5f) * 7f * p; val ny = by - 5.8f * p + (Noise.rnd(k, 92) - 0.5f) * 2f * p
            for (i in 0 until 2 * P) c.set(floor(nx + i).toInt(), floor(ny + sin(i * 1.3f + k) * 0.8f).toInt(), Col.hex(0xF8E2A0))
        }
        // carrot rounds and parsley, a few drops of fat catching the light
        for ((ox, oy) in listOf(-2.8f to -0.1f, 2f to 0.2f, 0.4f to -0.6f)) {
            c.fillEllipse(cx + ox * p, by - 5.8f * p + oy * p, 0.7f * p, 0.4f * p, Col.hex(0xE06A2A))
            c.set(floor(cx + ox * p - 0.3f * p).toInt(), floor(by - 5.8f * p + oy * p - 0.2f * p).toInt(), Col.hex(0xF49A5A))
        }
        for (k in 0 until 3 * P) c.set(floor(cx + (Noise.rnd(k, 93) - 0.5f) * 8f * p).toInt(), floor(by - 5.8f * p + (Noise.rnd(k, 94) - 0.5f) * 2.2f * p).toInt(), if (k % 3 == 2) Col.hex(0xFCE08A) else Col.hex(0x4E8A32))
        steam(cx, by - 8f * p, 3, 13)
    }

    /** Thin wisps rising from something hot at canvas ([x], [y]); closer up each a little streak, as far and as high. */
    protected fun steam(x: Float, y: Float, n: Int, seed: Int) = s.fx {
        for (j in 0 until n) {
            val a = ((t * 0.5 + j.toFloat() / n + seed * 0.37) % 1.0).toFloat()
            val px = x + (j - (n - 1) / 2f) * 2f * P + sin(t * 2.1 + j * 1.7 + seed).toFloat() * 1.5f * P * a
            val py = y - a * 10f * P
            if (P > 1) {
                for (i in 0 until 2 * P) {
                    val wob = (sin(t * 3.0 + j + i * 0.6).toFloat() * 0.8f).toInt()
                    c.blend(px.toInt() + wob, py.toInt() - i, Col.hex(0xF6F6FA), (0.6f - i * 0.15f / P) * (1f - a))
                }
                continue
            }
            c.blend(px.toInt(), py.toInt(), Col.hex(0xF6F6FA), 0.6f * (1f - a))
            c.blend(px.toInt(), py.toInt() - 1, Col.hex(0xF6F6FA), 0.45f * (1f - a))
        }
    }

    /** A white plate with a blue rim and a painted sprig in the middle. */
    protected fun plate(x: Float, y: Float, z: Float) {
        val cx = iso.sx(x, y); val cy = iso.sy(x, y, z)
        if (P > 1) {
            // closer up: a fine blue line round the rim and a dotted one inside it, the well's edge, a painted flower with leaves
            val p = P.toFloat()
            c.fillEllipse(cx, cy + 0.5f * p, 5.6f * p, 2.6f * p, chinaD)
            c.fillEllipse(cx, cy, 5.6f * p, 2.4f * p, china)
            arc(cx, cy, 5.6f * p - 0.5f, 2.4f * p - 0.5f, Col.hex(0xFFFFFF), PI.toFloat() * 1.05f, PI.toFloat() * 1.7f)
            arc(cx, cy, 4.6f * p, 1.85f * p, chinaBlue)
            arc(cx, cy, 3.8f * p, 1.5f * p, chinaBlue, every = 2)
            c.fillEllipse(cx, cy, 2.6f * p, 1f * p, Col.hex(0xECE8DE))
            arc(cx, cy, 2.6f * p, 1f * p, chinaD, PI.toFloat(), 2f * PI.toFloat())
            val fx = floor(cx).toInt(); val fy = floor(cy - 0.3f * p).toInt()
            c.set(fx - 2, fy + 1, Pal.LEAF); c.set(fx - 1, fy + 1, Pal.LEAF); c.set(fx + 2, fy + 1, Pal.LEAF); c.set(fx + 3, fy + 1, Col.hex(0x2E6A2E))
            c.set(fx, fy + 1, Col.hex(0x2E6A2E))
            c.set(fx, fy - 1, Pal.GERANIUM); c.set(fx - 1, fy, Pal.GERANIUM); c.set(fx + 1, fy, Pal.GERANIUM_D); c.set(fx, fy, Col.hex(0xF0C23A))
            if (P > 2) { c.set(fx + 1, fy - 1, Pal.GERANIUM); c.set(fx - 1, fy - 1, Col.hex(0xFF6A6A)) }
            return
        }
        c.fillEllipse(cx, cy + 0.5f, 5.6f, 2.6f, chinaD)
        c.fillEllipse(cx, cy, 5.6f, 2.4f, china)
        for (k in 0 until 18) { val a = k * PI.toFloat() / 9f; c.set((cx + cos(a) * 4.4f).toInt(), (cy + sin(a) * 1.7f).toInt(), chinaBlue) }
        c.fillEllipse(cx, cy, 2.6f, 1f, Col.hex(0xECE8DE))
        c.set(cx.toInt(), cy.toInt() - 1, Pal.GERANIUM); c.set(cx.toInt() - 1, cy.toInt(), Pal.LEAF); c.set(cx.toInt() + 1, cy.toInt(), Pal.LEAF)
    }

    /** Silver pixels at ([cx], [cy]) plus each (dx, dy) pair of [pts], in [col]. */
    protected fun dots(cx: Int, cy: Int, col: Int, vararg pts: Int) { var i = 0; while (i < pts.size) { c.set(cx + pts[i], cy + pts[i + 1], col); i += 2 } }

    /** A fork lying on the cloth pointing to the wall: its handle, a neck and two tines. */
    protected fun fork(x: Float, y: Float, z: Float) {
        val cx = iso.ix(x, y); val cy = iso.iy(x, y, z)
        if (P > 1) { cutlery(cx + 0.5f * P, cy + 0.5f * P, 0); return }
        dots(cx, cy, Pal.STONE_M, -4, 2, -3, 2, -2, 1, -1, 1, 0, 0)
        dots(cx, cy, Col.hex(0xE4E6EE), 1, -2, 1, -1, 1, 0, 2, -2, 3, -2, 4, -3, 2, 0, 3, 0, 4, -1)
    }

    /** A knife with a wooden handle and a bright blade. */
    protected fun knife(x: Float, y: Float, z: Float) {
        val cx = iso.ix(x, y); val cy = iso.iy(x, y, z)
        if (P > 1) { cutlery(cx + 0.5f * P, cy + 0.5f * P, 1); return }
        dots(cx, cy, Pal.WOOD_D, -4, 2, -3, 2, -2, 1, -1, 1)
        dots(cx, cy, Pal.WOOD_L, -3, 1, -2, 0)
        dots(cx, cy, Col.hex(0xF0F2F6), 0, -1, 1, -1, 2, -2, 3, -2, 4, -3)
        dots(cx, cy, Pal.STONE_M, 0, 0, 1, 0, 2, -1, 3, -1)
    }

    /** A spoon, its bowl catching the light. */
    protected fun spoon(x: Float, y: Float, z: Float) {
        val cx = iso.ix(x, y); val cy = iso.iy(x, y, z)
        if (P > 1) { cutlery(cx + 0.5f * P, cy + 0.5f * P, 2); return }
        dots(cx, cy, Pal.STONE_M, -4, 2, -3, 2, -2, 1, -1, 1, 0, 0)
        c.fillEllipse(cx + 2.5f, cy - 1f, 2.1f, 1.4f, Pal.STONE_L)
        dots(cx, cy, Col.hex(0xFFFFFF), 2, -2)
        dots(cx, cy, Pal.STONE_D, 3, 0)
    }

    /**
     * A fork (0), knife (1) or spoon (2) closer up, lying on the cloth pointing to the wall from ([ox], [oy]) (the middle of
     * its anchor pixel): drawn along the table's axis in nominal px, so its handle, tines, blade or bowl come out finer.
     */
    protected fun cutlery(ox: Float, oy: Float, kind: Int) {
        val p = P.toFloat()
        val ax = 0.8944f; val ay = -0.4472f; val nx = 0.4472f; val ny = 0.8944f // along it, and across it (towards us)
        fun fx(s: Float, w: Float) = ox + (ax * s + nx * w) * p
        fun fy(s: Float, w: Float) = oy + (ay * s + ny * w) * p
        fun line(s0: Float, s1: Float, w: Float, col: Int) = c.line(floor(fx(s0, w)).toInt(), floor(fy(s0, w)).toInt(), floor(fx(s1, w)).toInt(), floor(fy(s1, w)).toInt(), col)
        fun quad(s0: Float, w0a: Float, w0b: Float, s1: Float, w1a: Float, w1b: Float, col: Int) {
            c.polyBegin(); c.polyAdd(fx(s0, w0a), fy(s0, w0a)); c.polyAdd(fx(s1, w1a), fy(s1, w1a)); c.polyAdd(fx(s1, w1b), fy(s1, w1b)); c.polyAdd(fx(s0, w0b), fy(s0, w0b))
            c.polyFill(col)
        }
        val silver = Col.hex(0xE4E6EE); val bright = Col.hex(0xFFFFFF)
        if (kind == 1) {
            // the knife: a wooden handle with two rivets, a steel bolster, the blade with its bright back and a grey edge
            quad(-4.8f, -0.8f, 0.4f, -0.3f, -0.85f, 0.45f, Pal.WOOD_D)
            line(-4.6f, -0.3f, -0.55f, Pal.WOOD_L); line(-4.6f, -0.3f, -0.1f, Pal.WOOD_M)
            for (s in floatArrayOf(-3.5f, -1.5f)) c.set(floor(fx(s, -0.2f)).toInt(), floor(fy(s, -0.2f)).toInt(), Col.hex(0xD8D0B8))
            quad(-0.3f, -0.9f, 0.5f, 0.2f, -0.9f, 0.5f, Pal.STONE_M)
            quad(0.2f, -0.85f, 0.4f, 4.6f, -0.9f, 0.1f, Pal.STONE_L)
            c.polyBegin(); c.polyAdd(fx(4.6f, -0.9f), fy(4.6f, -0.9f)); c.polyAdd(fx(5.3f, -0.95f), fy(5.3f, -0.95f)); c.polyAdd(fx(4.6f, 0.1f), fy(4.6f, 0.1f)); c.polyFill(Pal.STONE_L)
            line(0.3f, 5.1f, -0.75f, Col.hex(0xF0F2F6)); line(0.4f, 4.4f, 0.25f, Pal.STONE_M)
            c.set(floor(fx(1.2f, -0.55f)).toInt(), floor(fy(1.2f, -0.55f)).toInt(), bright)
            return
        }
        // a handle, lit along its top
        val hx0 = floor(fx(-4.6f, 0f)).toInt(); val hy0 = floor(fy(-4.6f, 0f)).toInt()
        val hx1 = floor(fx(if (kind == 2) 1.2f else 0.7f, 0f)).toInt(); val hy1 = floor(fy(if (kind == 2) 1.2f else 0.7f, 0f)).toInt()
        for (o in 0 until P) c.line(hx0, hy0 + o - P / 2, hx1, hy1 + o - P / 2, if (o == 0) silver else if (o == P - 1) Pal.STONE_M else Pal.STONE_L)
        c.set(floor(fx(-3.9f, -0.2f)).toInt(), floor(fy(-3.9f, -0.2f)).toInt(), bright)
        if (kind == 0) {
            // the fork's shoulders, then its tines, thin, with gaps between
            quad(0.5f, -0.4f, 0.45f, 1.9f, -1.15f, 1.35f, Pal.STONE_L)
            line(0.5f, 1.9f, -0.35f, silver)
            val n = if (P >= 3) 4 else 3
            for (i in 0 until n) {
                val w = -1.0f + i * 2.2f / (n - 1)
                line(1.8f, if (i == 0 || i == n - 1) 4.5f else 4.8f, w, if (i < n / 2) silver else Pal.STONE_L)
            }
            return
        }
        // the spoon's bowl: its rim, the hollow shaded towards us, a glint
        val bx = ox - 0.5f * p + 2.5f * p; val by = oy - 0.5f * p - 1f * p
        c.fillEllipse(bx, by, 2.1f * p, 1.4f * p, Pal.STONE_M)
        c.fillEllipse(bx - 0.3f, by - 0.3f, 2.1f * p - 1f, 1.4f * p - 0.8f, silver)
        c.fillEllipse(bx + 0.3f * p, by + 0.2f * p, 1.4f * p, 0.8f * p, Pal.STONE_L)
        c.fillEllipse(bx + 0.5f * p, by + 0.35f * p, 0.9f * p, 0.45f * p, Pal.STONE_M)
        c.set(floor(bx - 0.9f * p).toInt(), floor(by - 0.6f * p).toInt(), bright); c.set(floor(bx - 0.9f * p).toInt() + 1, floor(by - 0.6f * p).toInt(), bright)
    }

    /** A glass of water: clear sides, the water line, a glint. */
    protected fun glass(x: Float, y: Float, z: Float) {
        val cx = iso.ix(x, y); val by = iso.iy(x, y, z)
        val gl = Col.hex(0xDCEAF2); val wat = Col.hex(0x8EC4E6)
        if (P > 1) {
            // closer up: a thick glass foot, the water deepening downwards, its surface, the glass's edges and a long glint, the rim
            val p = P.toFloat(); val mx = cx + 0.5f * p; val base = by.toFloat()
            val white = Col.hex(0xFFFFFF); val edge = Col.hex(0xA8BCCC)
            lathe(mx, base, 8.6f, { h -> 2.25f + h * 0.03f }) { u, du, h, _, _ ->
                when {
                    u < -1f + du * 1.2f -> white
                    u > 1f - du * 1.2f -> edge
                    abs(u + 0.45f) < du * 0.6f && h in 1f..7.6f -> white
                    h < 0.8f -> if (h < 0.8f - 1f / P) Col.hex(0xC8DCE8) else Col.hex(0xB0C8D8)
                    h < 5f -> Col.mix(Col.hex(0x6AAAD6), wat, (h / 5f).coerceIn(0f, 1f)).let { if (u > 0.5f) Col.scale(it, 0.9f) else it }
                    else -> gl
                }
            }
            c.fillEllipse(mx, base - 5.1f * p, 2.3f * p - 1f, 0.5f * p, Col.hex(0xC8E4F6))
            arc(mx, base - 5.1f * p, 2.3f * p - 1f, 0.5f * p, Col.hex(0xE8F6FF), 0f, PI.toFloat())
            // the mouth: an ellipse of glass seen through, its near rim bright
            c.fillEllipse(mx, base - 8.6f * p, 2.5f * p, 0.65f * p, Col.hex(0xE8F2F8))
            arc(mx, base - 8.6f * p, 2.5f * p - 0.5f, 0.65f * p - 0.3f, edge, PI.toFloat(), 2f * PI.toFloat())
            arc(mx, base - 8.6f * p, 2.5f * p - 0.5f, 0.65f * p - 0.3f, white, 0f, PI.toFloat())
            return
        }
        for (row in 0 until 8) {
            val yy = by - 1 - row; val half = if (row < 2) 2 else 2
            c.hline(cx - half, cx + half, yy, if (row < 5) wat else gl)
            c.set(cx - half, yy, Col.hex(0xFFFFFF)); c.set(cx + half, yy, Col.hex(0xA8BCCC))
        }
        c.hline(cx - 1, cx + 1, by - 6, Col.hex(0xC8E4F6)); c.hline(cx - 2, cx + 2, by - 9, Col.hex(0xFFFFFF))
    }

    /** A red enamel mug with white spots and a white rim, coffee in it; it steams in the morning, and when a dialog says it is hot. */
    protected fun cup(x: Float, y: Float, z: Float) {
        val cx = iso.ix(x, y); val by = iso.iy(x, y, z)
        wisps(cx.toFloat(), by - 7f * P, fxOn("steam"), 19)
        val r = Col.hex(0xC8322A); val rl = Col.hex(0xE0584A); val rd = Col.hex(0x8A2020)
        if (P > 1) {
            // closer up: round, glossy, white spots going round it, a white rim, coffee with a paler ring, a looped handle
            val p = P.toFloat(); val mx = cx.toFloat(); val base = by.toFloat()
            loop(mx + 3f * p, base - 2.5f * p, 2f * p, 1.6f * p, 0.9f * p, true, rd, r)
            lathe(mx, base, 5f, { h -> if (h < 0.5f) 2.75f else 3f }) { u, du, _, _, _ -> glaze(u, du, rl, r, rd, Col.hex(0xF49080)) }
            for ((sx, sy) in listOf(-0.5f to 3.6f, 1.5f to 1.5f, -1.6f to 1.4f, 2.5f to 3.9f, 0.4f to 0.6f)) {
                val dx = mx + sx * p; val dy = base - sy * p
                val edge = abs(sx) > 2f
                c.fillEllipse(dx, dy, if (edge) 0.3f * p else 0.55f * p, 0.5f * p, if (sx > 1f) chinaD else china)
            }
            c.fillEllipse(mx, base - 5.1f * p, 3f * p, 0.95f * p, china)
            c.fillEllipse(mx + 0.1f * p, base - 5f * p, 2.3f * p, 0.55f * p, Col.hex(0x5A3A24))
            arc(mx + 0.1f * p, base - 5f * p, 2.3f * p - 0.5f, 0.55f * p - 0.3f, Col.hex(0x8A5A34), PI.toFloat() * 1.1f, PI.toFloat() * 1.9f)
            if (s.hour in 6f..10f) steam(mx, base - 7f * p, 2, 17)
            return
        }
        c.fillRect(cx - 3, by - 5, 6, 5, r); c.vline(cx - 3, by - 5, by - 1, rl); c.vline(cx + 2, by - 5, by - 1, rd)
        c.hline(cx - 3, cx + 2, by - 6, china); c.hline(cx - 2, cx + 1, by - 6, Col.hex(0x5A3A24))
        c.vline(cx + 4, by - 4, by - 2, rd); c.set(cx + 3, by - 4, rd); c.set(cx + 3, by - 2, rd)
        c.set(cx - 1, by - 4, china); c.set(cx + 1, by - 2, china); c.set(cx - 2, by - 2, china)
        if (s.hour in 6f..10f) steam(cx.toFloat(), by - 7f, 2, 17)
    }

    /**
     * The petroleum lamp over the table, on a chain from a hook where the ceiling would be: a white glass shade, the
     * chimney glowing from dusk with a soft halo, the brass fount under it.
     */
    protected fun lamp(lit: Boolean) {
        val cx = iso.ix(lampX, lampY); val hook = iso.iy(lampX, lampY, hw)
        val top = iso.iy(lampX, lampY, fl + 20.5f)
        if (P > 1) { lampFine(cx, hook, top, lit); return }
        for (y in hook until top) c.set(cx, y, if ((y - hook) % 2 == 0) Col.hex(0x3A3A44) else Col.hex(0x7A7A88))
        c.set(cx - 1, hook, Col.hex(0x3A3A44)); c.set(cx + 1, hook, Col.hex(0x3A3A44)); c.set(cx - 1, hook - 1, Col.hex(0x7A7A88)); c.set(cx + 1, hook - 1, Col.hex(0x7A7A88)); c.set(cx, hook - 2, Col.hex(0x3A3A44))
        c.hline(cx - 1, cx + 1, top, brass)
        val shadeL = Col.hex(0xFAF8F0); val shadeD = Col.hex(0xCFC6B4)
        for (r in 0 until 4) {
            val half = intArrayOf(3, 5, 6, 7)[r]
            c.hline(cx - half, cx + half, top + 1 + r, if (r == 3) shadeD else shadeL)
            c.set(cx + half, top + 1 + r, shadeD)
        }
        val g0 = top + 5; val g1 = top + 8
        if (lit) glow {
            c.hline(cx - 6, cx + 6, top + 4, Pal.WINDOW_LIT)
            c.fillRect(cx - 1, g0, 3, g1 - g0 + 1, Pal.FLAME[1]); c.vline(cx, g0 + 1, g1, Pal.FLAME[0])
        } else {
            c.fillRect(cx - 1, g0, 3, g1 - g0 + 1, Col.hex(0xC8D8E4)); c.vline(cx - 1, g0, g1, Col.hex(0xF0F6FA))
        }
        c.fillEllipse(cx + 0.5f, g1 + 2.5f, 3.6f, 2.2f, brass)
        c.hline(cx - 2, cx, g1 + 1, brassL); c.hline(cx - 2, cx + 3, g1 + 4, brassD)
        c.set(cx, g1 + 5, brassD)
        if (lit) s.fx {
            c.penEmissive = true
            c.ditherCircle(cx + 0.5f, (top + g1) / 2f + 1f, 9f * K, Pal.FLAME[1], 0.14f * env.windows)
            c.penEmissive = false
        }
    }

    /**
     * The lamp closer up, its middle column [cx], the chain from the hook row [hook] to the shade's top [top]: chain links,
     * a fluted opal shade with a glowing rim when lit, the glass chimney with the flame in it, the brass fount with its key.
     */
    protected fun lampFine(cx: Int, hook: Int, top: Int, lit: Boolean) {
        val p = P.toFloat(); val mx = cx + 0.5f * p
        val dark = Col.hex(0x3A3A44); val light = Col.hex(0x7A7A88)
        chain(cx + P / 2, hook, top, light, dark)
        arc(mx, hook - 0.9f * p, 0.9f * p, 0.9f * p, dark)
        // the brass cap
        c.fillEllipse(mx, top + 0.5f * p, 1.7f * p, 0.7f * p, brassD); c.fillEllipse(mx - 0.3f, top + 0.3f * p, 1.4f * p, 0.5f * p, brass)
        // the shade, widening down, fluted, its rim
        val shadeL = Col.hex(0xFAF8F0); val shadeD = Col.hex(0xCFC6B4); val shadeM = Col.hex(0xECE6D8)
        lathe(mx, top + 5f * p, 4f, { hn -> val h = 4f - hn; 3.3f + 4.2f * Math.pow((h / 4f).toDouble(), 0.7).toFloat() }) { u, du, hn, _, _ ->
            val flute = abs(((u * 7f + 70.5f) % 1f) - 0.5f) < du * 3.5f
            when {
                hn < 1f / P -> if (lit) Pal.WINDOW_LIT else shadeD
                hn < 1f -> if (u > 0.55f) shadeD else shadeM
                u > 0.65f -> shadeD
                abs(u + 0.45f) < du * 0.6f -> Col.hex(0xFFFFFF)
                flute -> if (u < 0f) shadeM else shadeD
                u < -0.6f -> shadeL
                else -> Col.mix(shadeL, shadeM, 0.4f)
            }
        }
        if (lit) glow { c.hline(floor(mx - 7.4f * p).toInt() + 1, ceil(mx + 7.4f * p).toInt() - 2, top + 5 * P - 1, Pal.WINDOW_LIT_HI) }
        // the chimney: a bulb over the burner, the flame inside it when lit
        val g0 = top + 5 * P; val gb = top + 9f * p
        if (lit) glow {
            lathe(mx, gb, 4f, { h -> if (h < 1.8f) 1.5f + 0.25f * sin(h / 1.8f * PI.toFloat()) else 1.15f }) { u, _, _, _, _ -> if (abs(u) > 0.7f) Col.hex(0xFFE9A8) else Pal.FLAME[1] }
            c.fillEllipse(mx, gb - 1.6f * p, 0.7f * p, 1.3f * p, Pal.FLAME[2])
            c.fillEllipse(mx, gb - 1.5f * p, 0.45f * p, 1f * p, Pal.FLAME[0])
            c.set(floor(mx).toInt(), floor(gb - 3f * p).toInt(), Pal.FLAME[1])
        } else {
            lathe(mx, gb, 4f, { h -> if (h < 1.8f) 1.5f + 0.25f * sin(h / 1.8f * PI.toFloat()) else 1.15f }) { u, du, _, _, _ ->
                if (abs(u + 0.5f) < du * 0.6f) Col.hex(0xF0F6FA) else if (u > 0.6f) Col.hex(0xA8BCCC) else Col.hex(0xC8D8E4)
            }
            c.vline(floor(mx).toInt(), floor(gb - 1.2f * p).toInt(), floor(gb).toInt() - 1, Col.hex(0x4A3A30))
        }
        c.hline(floor(mx - 1.5f * p).toInt(), ceil(mx + 1.5f * p).toInt() - 1, g0, brassD)
        // the fount: round brass, a ring round its shoulder, the wick's key, a drop finial
        val fy = gb + 1.5f * p
        c.fillEllipse(mx + 0.3f, fy + 0.3f, 3.6f * p, 2.2f * p, brassD)
        c.fillEllipse(mx - 0.3f, fy - 0.2f, 3.6f * p - 1f, 2.2f * p - 1f, brass)
        c.fillEllipse(mx - 1.2f * p, fy - 0.8f * p, 1.4f * p, 0.7f * p, brassL)
        arc(mx, fy - 0.2f * p, 3.3f * p, 1.4f * p, brassD, 0.2f, PI.toFloat() - 0.2f)
        c.set(floor(mx - 1.6f * p).toInt(), floor(fy - 1f * p).toInt(), Col.hex(0xFFFFFF))
        c.fillCircle(mx + 2.4f * p, gb - 0.4f * p, 0.5f * p, brassD); c.set(floor(mx + 2.3f * p).toInt(), floor(gb - 0.6f * p).toInt(), brassL)
        c.vline(floor(mx).toInt(), floor(fy + 2.2f * p).toInt(), floor(fy + 3f * p).toInt(), brassD)
        if (lit) s.fx {
            c.penEmissive = true
            c.ditherCircle(mx, (top + gb) / 2f + 1f * p, 9f * K, Pal.FLAME[1], 0.14f * env.windows)
            c.penEmissive = false
        }
    }

    /** A burlap sack of potatoes, its neck rolled open, a potato fallen out. */
    protected fun sack(x: Float, y: Float) {
        val cx = iso.ix(x, y); val by = iso.iy(x, y, fl)
        val l = Col.hex(0xDCC48E); val m = Col.hex(0xC2A66E); val d = Col.hex(0x9A7E4E)
        if (P > 1) { sackFine(cx + 0.5f * P, by.toFloat(), l, m, d); return }
        for (row in 0 until 15) {
            val yy = by - 1 - row
            val half = when { row < 1 -> 5; row < 3 -> 6; row < 10 -> 7; row < 13 -> 6; else -> 5 }
            for (xx in cx - half..cx + half) {
                val u = (xx - cx).toFloat() / half
                c.set(xx, yy, if (u < -0.6f) l else if (u > 0.55f) d else if ((xx + yy) % 3 == 0) Col.scale(m, 0.94f) else m)
            }
        }
        c.hline(cx - 6, cx + 6, by - 16, d); c.hline(cx - 5, cx + 5, by - 17, l)
        c.fillEllipse(cx + 0.5f, by - 16.5f, 5f, 1.8f, Col.hex(0x5A4028))
        val spud = Col.hex(0xB08050); val spudL = Col.hex(0xD0A070); val spudD = Col.hex(0x7A5230)
        for (k in 0..2) { val px = cx - 3 + k * 3; val py = by - 17 - (k % 2); c.fillEllipse(px + 0.5f, py + 0.5f, 2f, 1.5f, spud); c.set(px, py, spudL) }
        c.fillEllipse(cx - 8.5f, by - 1.5f, 2.2f, 1.6f, spud); c.set(cx - 9, by - 2, spudL); c.set(cx - 7, by - 1, spudD)
    }

    /** The sack closer up around picture x [mx] on the row [base]: a soft bulging outline, the burlap's weave and slubs, a seam, the rolled neck, potatoes with eyes. */
    protected fun sackFine(mx: Float, base: Float, l: Int, m: Int, d: Int) {
        val p = P.toFloat()
        lathe(mx, base, 15f, { h ->
            when {
                h < 3f -> 5.2f + 2.2f * sin(h / 3f * PI.toFloat() / 2f)
                h < 9.5f -> 7.4f
                else -> 7.4f - 2.0f * smooth((h - 9.5f) / 5.5f)
            }
        }) { u, du, h, xx, yy ->
            val weave = ((xx shr 1) + (yy shr 1)) and 1
            val base0 = when {
                u < -0.62f -> l
                u > 0.55f -> d
                else -> m
            }
            when {
                abs(u - 0.72f) < du * 0.6f && h > 1f && h < 14f -> Col.scale(d, 0.85f)
                Noise.rnd(xx, yy, 97) < 0.03f -> Col.scale(base0, 0.84f)
                weave == 0 -> Col.mix(base0, l, 0.18f)
                else -> Col.scale(base0, 0.95f)
            }
        }
        // the neck, rolled open: a twisted band, the dark mouth
        lathe(mx, base - 15f * p, 2f, { h -> if (h < 1f) 6.5f else 5.5f }) { u, _, h, xx, yy ->
            val twist = Math.floorMod(xx + yy * 2, 4) == 0
            when {
                h < 1f -> if (twist) Col.scale(d, 0.85f) else d
                u < -0.6f -> Col.mix(l, Col.hex(0xFFFFFF), 0.15f)
                twist -> m
                else -> l
            }
        }
        c.fillEllipse(mx, base - 16.5f * p, 5f * p, 1.8f * p, Col.hex(0x5A4028))
        c.fillEllipse(mx + 0.4f * p, base - 16.3f * p, 4.2f * p, 1.2f * p, Col.hex(0x3E2A18))
        val spud = Col.hex(0xB08050); val spudL = Col.hex(0xD0A070); val spudD = Col.hex(0x7A5230)
        fun potato(px: Float, py: Float, rx: Float, ry: Float, seed: Int) {
            c.fillEllipse(px + 0.3f, py + 0.4f, rx, ry, spudD)
            c.fillEllipse(px - 0.2f, py - 0.2f, rx - 0.6f, ry - 0.5f, spud)
            c.fillEllipse(px - rx * 0.35f, py - ry * 0.4f, rx * 0.35f, ry * 0.3f, spudL)
            for (k in 0 until 2) c.set(floor(px + (Noise.rnd(k, seed) - 0.3f) * rx).toInt(), floor(py + (Noise.rnd(k, seed + 1) - 0.4f) * ry).toInt(), Col.hex(0x5A3A20))
        }
        for (k in 0..2) potato(mx + (-3f + k * 3f) * p, base - (16.5f + (k % 2)) * p, 2f * p, 1.5f * p, k * 3)
        potato(mx - 9f * p, base - 1.5f * p, 2.2f * p, 1.6f * p, 11)
    }

    /** A wicker basket of eggs with a handle over it. */
    protected fun basket(x: Float, y: Float) {
        val cx = iso.ix(x, y); val by = iso.iy(x, y, fl)
        if (P > 1) { basketFine(cx + 0.5f * P, by.toFloat()); return }
        c.line(cx - 5, by - 6, cx - 2, by - 12, Pal.WOOD_D); c.line(cx + 5, by - 6, cx + 2, by - 12, Pal.WOOD_D); c.hline(cx - 2, cx + 2, by - 12, Pal.WOOD_M)
        for (k in 0..4) {
            val ex = cx - 4 + k * 2; val ey = by - 7 + (k % 2)
            c.fillEllipse(ex + 0.5f, ey + 0.5f, 1.6f, 2f, if (k % 2 == 0) Col.hex(0xF3E6C8) else Col.hex(0xE0BE92))
            c.set(ex, ey - 1, Col.hex(0xFFFBEE))
        }
        for (row in 0 until 5) {
            val yy = by - 1 - row; val half = if (row < 2) 5 else 6
            for (xx in cx - half..cx + half) c.set(xx, yy, if ((xx + row) % 2 == 0) Pal.WOOD_L else Pal.WOOD_M)
            c.set(cx - half, yy, Pal.WOOD_D); c.set(cx + half, yy, Pal.WOOD_D)
        }
        c.hline(cx - 6, cx + 6, by - 6, Pal.WOOD_D)
    }

    /** The basket closer up around picture x [mx] on the row [base]: a twisted willow handle, eggs shaded and speckled, wicker woven over stakes, a rolled rim. */
    protected fun basketFine(mx: Float, base: Float) {
        val p = P.toFloat()
        // the handle: an arch of twisted willow
        val hcx = mx; val hcy = base - 5.6f * p; val hrx = 4.4f * p; val hry = 6.4f * p; val th = 1.1f * p
        for (yy in max(floor(hcy - hry).toInt(), c.top)..min(ceil(hcy).toInt(), c.bottom - 1)) {
            for (xx in max(floor(hcx - hrx).toInt(), c.left)..min(ceil(hcx + hrx).toInt(), c.right - 1)) {
                val dx = (xx + 0.5f - hcx); val dy = (yy + 0.5f - hcy)
                val o = (dx / hrx) * (dx / hrx) + (dy / hry) * (dy / hry)
                val i = (dx / (hrx - th)) * (dx / (hrx - th)) + (dy / (hry - th)) * (dy / (hry - th))
                if (o > 1f || i <= 1f) continue
                val ang = atan2(dy / hry, dx / hrx)
                val twist = ((ang * 9f + i * 3f) % 1f + 1f) % 1f < 0.4f
                c.set(xx, yy, if (twist) Pal.WOOD_D else if (i > 1.25f && dy < 0f) Pal.WOOD_L else Pal.WOOD_M)
            }
        }
        // the eggs
        for (k in 0..4) {
            val ex = mx + (-4f + k * 2f) * p; val ey = base - (6.5f - (k % 2)) * p
            val brown = k % 2 == 1
            val col = if (brown) Col.hex(0xE0BE92) else Col.hex(0xF3E6C8)
            c.fillEllipse(ex, ey, 1.6f * p, 2f * p, if (brown) Col.hex(0xC49A6A) else Col.hex(0xDCCCA8))
            c.fillEllipse(ex - 0.3f, ey - 0.4f, 1.6f * p - 0.8f, 2f * p - 0.8f, col)
            c.set(floor(ex - 0.6f * p).toInt(), floor(ey - 1f * p).toInt(), Col.hex(0xFFFBEE))
            if (brown) for (j in 0 until P) c.set(floor(ex + (Noise.rnd(j, k + 40) - 0.5f) * 2f * p).toInt(), floor(ey + (Noise.rnd(j, k + 41) - 0.3f) * 2f * p).toInt(), Col.hex(0xA87A50))
        }
        // the body: weavers passing over and under the stakes
        lathe(mx, base, 5f, { h -> if (h < 1.2f) 5.1f + h * 0.45f else 5.64f + (h - 1.2f) * 0.23f }) { u, du, _, xx, yy ->
            val row = Math.floorDiv(floor(base).toInt() - 1 - yy, 2); val top = (floor(base).toInt() - 1 - yy) % 2 == 1
            val col = Math.floorDiv(xx - floor(mx).toInt(), 3)
            val over = (row + col) % 2 == 0
            val c0 = if (over) (if (top) Pal.WOOD_L else Col.mix(Pal.WOOD_L, Pal.WOOD_M, 0.5f)) else (if (Math.floorMod(xx - floor(mx).toInt(), 3) == 0) Pal.WOOD_D else Pal.WOOD_M)
            when {
                u < -1f + du * 1.5f || u > 1f - du * 1.5f -> Pal.WOOD_D
                u > 0.6f -> Col.scale(c0, 0.85f)
                else -> c0
            }
        }
        // the rim, a rolled band
        lathe(mx, base - 5f * p, 1f, { 6.5f }) { u, _, _, xx, yy ->
            if (Math.floorMod(xx + yy, 3) == 0) Pal.WOOD_D else if (u > 0.6f) Col.mix(Pal.WOOD_M, Pal.WOOD_D, 0.5f) else if (u < -0.6f) Pal.WOOD_L else Pal.WOOD_M
        }
    }

    // ------------------------------------------------------------------ outside

    /** Flat stepping stones through the grass from the plot's front corner to the bench. */
    protected fun path() {
        for (k in 0 until 7) {
            val x = 11.05f + sin(k * 1.9f) * 0.18f; val y = 10.9f - k * 0.92f
            val cx = iso.sx(x, y); val cy = iso.sy(x, y, 0f)
            val p = P
            val rx = (3.6f + Noise.rnd(k, 3) * 1.2f) * p; val ry = rx * 0.5f
            c.fillEllipse(cx, cy, rx, ry, Pal.STONE_D)
            c.fillEllipse(cx - 0.5f * p, cy - 0.5f * p, rx - 0.8f * p, ry - 0.6f * p, if (winter) Pal.SNOW_M else Pal.STONE_M)
            if (p > 1 && !winter) {
                // closer up: grit in the stone, a crack in some, lichen at the edge of others
                for (j in 0 until 5 * p) {
                    val a = Noise.rnd(j, k + 20) * 2f * PI.toFloat(); val r = sqrt(Noise.rnd(j, k + 21)) * 0.85f
                    c.set(floor(cx - 0.5f * p + cos(a) * r * (rx - 0.8f * p)).toInt(), floor(cy - 0.5f * p + sin(a) * r * (ry - 0.6f * p)).toInt(), if (j % 2 == 0) Pal.STONE_D else Pal.STONE_L)
                }
                if (k % 3 == 1) c.line(floor(cx - rx * 0.3f).toInt(), floor(cy - ry * 0.5f).toInt(), floor(cx + rx * 0.1f).toInt(), floor(cy + ry * 0.2f).toInt(), Pal.STONE_X)
                if (k % 3 == 2) c.fillEllipse(cx + rx * 0.45f, cy + ry * 0.2f, 0.8f * p, 0.5f * p, Col.hex(0xA8B45A))
            }
            c.fillEllipse(cx - 1.5f * p, cy - 1f * p, rx * 0.4f, ry * 0.4f, if (winter) Pal.SNOW_L else Pal.STONE_L)
        }
    }

    /** A bench by the house's right wall. */
    protected fun gardenBench() {
        val ba = 9.85f; val bb = 10.55f; val bc = 2.3f; val bd = 4.9f; val bz = 4.4f
        for ((lx, ly) in listOf(ba + 0.12f to bc + 0.2f, bb - 0.12f to bc + 0.2f, ba + 0.12f to bd - 0.2f, bb - 0.12f to bd - 0.2f)) iso.post(lx, ly, 0f, bz, Pal.WOOD_D)
        iso.box(ba, bc, bz, bb - ba, bd - bc, 0.9f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        if (P > 1) grain(ba, bc, bb, bd, bz + 0.9f, 7, alongY = true)
        iso.line(bb, bc, bz + 0.9f, bb, bd, bz + 0.9f, Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.2f))
        if (winter) iso.top(ba + 0.05f, bc + 0.1f, bz + 1f, bb - ba - 0.1f, bd - bc - 0.2f, Pal.SNOW_L)
    }

    // ------------------------------------------------------------------ the cat

    protected val catO = Col.hex(0xE09040); protected val catD = Col.hex(0xA8602A); protected val catL = Col.hex(0xF6C078)

    /** Where the cat sits on the garden bench (world x, y), and the bench's top (z). */
    protected val catX = 10.2f; protected val catY = 3.35f; protected val catZ = 5.3f

    /** The cat's dash (world x, y): its seat, down on the grass, round the house's back corner, behind the house. */
    protected val dashPath = floatArrayOf(catX, catY, 10.95f, 3.0f, 9.95f, 0.4f, 7.4f, 0.3f)

    /** When (s into the dash) it leaves the bench, lands, reaches the corner and is behind the house; comes out again, reaches the corner, the bench, and sits. */
    protected val dashAt = floatArrayOf(0.2f, 0.55f, 1.15f, 1.95f, 19.6f, 20.5f, 21.2f, 21.55f)

    /** Where the cat is on its dash: world x, y, z, facing (1 right, −1 left) and gait ([catSide]), as [catDash] leaves it. */
    protected val dashPos = FloatArray(5)

    /** The cat is drawn before the walls, going behind the house or coming out: no whiskers over them. */
    protected var catBehind = false

    /**
     * The cat as the taps have it ([pokes]): on the bench curled up or sitting (purring with its eyes shut, hearts
     * rising), up and stretching, or off on its dash. [behind] is the call before the walls, for the legs of its run
     * round the back corner (the walls hide what is behind them); the rest is drawn after the garden bench.
     */
    protected fun catAround(behind: Boolean) {
        val pk = poked("cat")
        val a = pk?.age(t) ?: 0f
        val step = pk?.step ?: -1
        if (step == 2 && a >= dashAt[0] && a < dashAt[7]) {
            if (!catDash(a) || (a >= dashAt[2] && a < dashAt[5]) != behind) return
            val cx = floor(iso.sx(dashPos[0], dashPos[1])); val by = floor(iso.sy(dashPos[0], dashPos[1], dashPos[2]))
            if (dashPos[2] < 0.1f) shade(cx, by, 3.5f * P, 1.1f * P, 0.8f, groundId)
            catBehind = behind
            pokeable("cat") { catSide(cx, by, dashPos[3], dashPos[4].toInt()) }
            catBehind = false
            return
        }
        if (behind) return
        val cx = iso.ix(catX, catY).toFloat(); val by = iso.iy(catX, catY, catZ).toFloat()
        when {
            // up, and stretching along the bench with a yawn at the deepest of it, then down again
            step == 0 && a < 1.65f -> pokeable("cat") { catSide(cx, by, -1f, if (a < 0.25f || a >= 1.35f) STAND else BOW, yawn = a in 0.55f..1.1f) }
            // about to leap
            step == 2 && a < dashAt[0] -> pokeable("cat") { catSide(cx, by, 1f, STAND) }
            else -> {
                // back from its dash it sits a moment, even at night
                pokeable("cat") { cat(catX, catY, shut = step == 1, sit = step == 2) }
                if (step == 1) {
                    val curled = env.dark > 0.5f || winter
                    val hx = if (curled) cx - 3.5f * P else cx + 0.5f * P; val hy = if (curled) by - 4.5f * P else by - 9.5f * P
                    s.fx { PokeArt.hearts(c, hx, hy, a, 4, P, 7) }
                }
            }
        }
    }

    /** Where the cat is [a] s into its dash, into [dashPos]; false while it is away. */
    protected fun catDash(a: Float): Boolean {
        val d = dashAt
        fun at(i: Int, j: Int, v: Float, z: Float, f: Float, gait: Int) {
            dashPos[0] = dashPath[i * 2] + (dashPath[j * 2] - dashPath[i * 2]) * v
            dashPos[1] = dashPath[i * 2 + 1] + (dashPath[j * 2 + 1] - dashPath[i * 2 + 1]) * v
            dashPos[2] = z; dashPos[3] = f; dashPos[4] = gait.toFloat()
        }
        // a gallop out, a trot back: stretched out and gathered in turn
        val run = if (((a * 10).toInt() and 1) == 0) RUN else GATHER
        val trot = if (((a * 6).toInt() and 1) == 0) RUN else GATHER
        val pi = PI.toFloat()
        when {
            a < d[1] -> { val v = (a - d[0]) / (d[1] - d[0]); at(0, 1, v, catZ * (1f - v) + 4f * sin(pi * v), 1f, RUN) }
            a < d[2] -> at(1, 2, (a - d[1]) / (d[2] - d[1]), 0f, 1f, run)
            a < d[3] -> at(2, 3, (a - d[2]) / (d[3] - d[2]), 0f, -1f, run)
            a < d[4] -> return false
            a < d[5] -> at(3, 2, (a - d[4]) / (d[5] - d[4]), 0f, 1f, trot)
            a < d[6] -> at(2, 1, (a - d[5]) / (d[6] - d[5]), 0f, -1f, trot)
            else -> { val v = ((a - d[6]) / (d[7] - d[6])).coerceIn(0f, 1f); at(1, 0, v, catZ * v + 3f * sin(pi * v), -1f, RUN) }
        }
        return true
    }

    /**
     * The cat from the side, facing right ([f] 1) or left (−1), its feet at picture ([cx], [by]): standing, bowed low in
     * a stretch ([yawn]ing at the deepest of it), or running, stretched out or gathered; closer up with tabby stripes, an
     * eye with a slit pupil, a pink nose and whiskers.
     */
    protected fun catSide(cx: Float, by: Float, f: Float, gait: Int, yawn: Boolean = false) {
        val p = P.toFloat()
        fun x(dx: Float) = cx + dx * p * f
        fun y(dy: Float) = by + dy * p
        fun leg(x0: Float, y0: Float, x1: Float, y1: Float, near: Boolean) {
            limb(x(x0), y(y0), x(x1), y(y1), 0.95f * p, if (near) catO else catD)
            c.fillEllipse(x(x1 + 0.2f), y(y1 - 0.3f), 0.6f * p, 0.4f * p, if (near) catL else catD)
        }
        fun blob(dx: Float, dy: Float, rx: Float, ry: Float, col: Int = catO) = c.fillEllipse(x(dx), y(dy), rx * p, ry * p, col)
        fun stripes(dx: Float, dy: Float, ry: Float) {
            if (P == 1) { c.set(floor(x(dx)).toInt(), floor(y(dy - ry * 0.8f)).toInt(), catD); return }
            for (k in -1..1) limb(x(dx + k * 1.1f), y(dy - ry * 0.95f), x(dx + k * 1.1f - 0.35f), y(dy - ry * 0.25f), 1f, catD)
        }
        when (gait) {
            BOW -> {
                leg(-1.8f, -3.6f, -2f, 0f, false); leg(1.6f, -1.3f, 4.6f, -0.35f, false)
                tail(x(-3.2f), y(-4.6f), x(-3.9f), y(-8f), x(-2.8f), y(-9.4f))
                blob(-2f, -4f, 1.9f, 1.5f); blob(0f, -3.1f, 2.2f, 1.45f); blob(1.9f, -2.2f, 1.7f, 1.25f)
                blob(1.4f, -1.5f, 1.2f, 0.6f, catL)
                stripes(-0.6f, -3.3f, 1.45f)
                leg(-2.5f, -3.6f, -2.6f, 0f, true); leg(1.9f, -1.5f, 5.1f, -0.5f, true)
                catHead(x(3.9f), y(-2.8f), f, if (yawn) 2 else 1)
            }
            RUN -> {
                leg(2.2f, -2.4f, 4.6f, -0.9f, false); leg(-2.2f, -2.4f, -4.8f, -0.7f, false)
                tail(x(-3.4f), y(-3.7f), x(-5.4f), y(-4.6f), x(-7.2f), y(-4.2f))
                blob(0f, -3.2f, 3.8f, 1.5f)
                stripes(-0.3f, -3.2f, 1.5f)
                leg(2.8f, -2.6f, 5.3f, -1.4f, true); leg(-2.8f, -2.6f, -5.2f, -1.2f, true)
                catHead(x(4.4f), y(-4.3f), f, 0)
            }
            GATHER -> {
                leg(1.2f, -2.8f, -0.4f, 0f, false); leg(-1.4f, -2.8f, 0.6f, -0.2f, false)
                tail(x(-2.8f), y(-4.3f), x(-4.6f), y(-5.2f), x(-5f), y(-7.2f))
                blob(0f, -3.6f, 3f, 1.9f)
                stripes(-0.3f, -3.6f, 1.9f)
                leg(1.6f, -2.8f, 0.2f, 0f, true); leg(-1.8f, -2.8f, 0.2f, -0.2f, true)
                catHead(x(3.3f), y(-5f), f, 0)
            }
            else -> {
                leg(-1.8f, -2.6f, -1.8f, 0f, false); leg(2.3f, -2.6f, 2.3f, 0f, false)
                tail(x(-3.1f), y(-4f), x(-4.6f), y(-6f), x(-3.7f), y(-8.4f))
                blob(0f, -3.4f, 3.4f, 1.7f)
                stripes(-0.3f, -3.4f, 1.7f)
                leg(-2.5f, -2.6f, -2.5f, 0f, true); leg(1.6f, -2.6f, 1.6f, 0f, true)
                catHead(x(3.6f), y(-5.3f), f, 0)
            }
        }
    }

    /** The cat's head from the side at picture ([hx], [hy]), facing as [f]: ears, the light muzzle, a pink nose; its eye open (0), shut (1) or shut in a yawn (2). */
    protected fun catHead(hx: Float, hy: Float, f: Float, eyes: Int) {
        val p = P.toFloat()
        fun ear(ex: Float, col: Int) {
            c.polyBegin(); c.polyAdd(ex - 0.65f * p, hy - 0.9f * p); c.polyAdd(ex + 0.65f * p, hy - 0.9f * p); c.polyAdd(ex - 0.2f * p * f, hy - 2.5f * p); c.polyFill(col)
            if (P > 1) c.set(floor(ex).toInt(), floor(hy - 1.4f * p).toInt(), Col.hex(0xF0A0A0))
        }
        ear(hx - 0.8f * p * f, catD); ear(hx + 0.5f * p * f, catO)
        c.fillEllipse(hx, hy, 1.9f * p, 1.6f * p, catO)
        c.fillEllipse(hx + 1.05f * p * f, hy + 0.55f * p, 1f * p, 0.65f * p, catL)
        c.set(floor(hx + 1.95f * p * f).toInt(), floor(hy + 0.05f * p).toInt(), Col.hex(0xF0A0A0))
        val ex = floor(hx + 0.55f * p * f).toInt(); val ey = floor(hy - 0.45f * p).toInt()
        when {
            P == 1 -> c.set(ex, ey, if (eyes == 0) Col.hex(0x3A5A2A) else catD)
            eyes == 0 -> { c.fillRect(ex - P / 2, ey - P / 2, P, P, Col.hex(0x8AB04A)); c.vline(ex, ey - P / 2, ey - P / 2 + P - 1, Col.hex(0x1E2A12)) }
            // shut, content: a little arch
            else -> { c.hline(ex - P / 2, ex - P / 2 + P - 1, ey, catD); c.set(ex, ey - 1, catD) }
        }
        if (eyes == 2) {
            // the yawn: the mouth wide, pink inside, a fang
            c.fillEllipse(hx + 1.25f * p * f, hy + 0.95f * p, 0.8f * p, 0.65f * p, catD)
            c.fillEllipse(hx + 1.3f * p * f, hy + 1.05f * p, 0.5f * p, 0.4f * p, Col.hex(0xE0708A))
            if (P > 1) c.set(floor(hx + 1.7f * p * f).toInt(), floor(hy + 0.6f * p).toInt(), Col.hex(0xFFFFFF))
        }
        if (P > 1 && !catBehind) s.fx {
            val nx = floor(hx + 1.6f * p * f).toInt(); val ny = floor(hy + 0.5f * p).toInt()
            for (k in 0..1) for (i in 0..2 * P) c.blend(nx + ((1 + i) * f).toInt(), ny + k + (k - 1) * i / (2 * P), Col.hex(0xFFF0DC), 0.8f - 0.4f * i / (2 * P))
        }
    }

    /** A leg or a stretch of tail from picture ([x0], [y0]) to ([x1], [y1]), [th] px thick (a line at a pixel or less). */
    protected fun limb(x0: Float, y0: Float, x1: Float, y1: Float, th: Float, col: Int) {
        if (th <= 1.01f) { c.line(floor(x0).toInt(), floor(y0).toInt(), floor(x1).toInt(), floor(y1).toInt(), col); return }
        val dx = x1 - x0; val dy = y1 - y0
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        val nx = -dy / len * th / 2; val ny = dx / len * th / 2
        c.polyBegin(); c.polyAdd(x0 + nx, y0 + ny); c.polyAdd(x1 + nx, y1 + ny); c.polyAdd(x1 - nx, y1 - ny); c.polyAdd(x0 - nx, y0 - ny)
        c.polyFill(col)
    }

    /** The cat's tail: a curve from picture ([x0], [y0]) toward ([x1], [y1]) to its tip ([x2], [y2]); closer up ringed darker. */
    protected fun tail(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
        val n = 6
        var px = x0; var py = y0
        for (i in 1..n) {
            val u = i / n.toFloat()
            val qx = (1 - u) * (1 - u) * x0 + 2 * (1 - u) * u * x1 + u * u * x2
            val qy = (1 - u) * (1 - u) * y0 + 2 * (1 - u) * u * y1 + u * u * y2
            limb(px, py, qx, qy, 0.85f * P, if (P > 1 && i % 2 == 0) catD else catO)
            px = qx; py = qy
        }
        c.set(floor(x2).toInt(), floor(y2).toInt(), catL)
    }

    /** The cat on the bench, curled up asleep by night, sitting up and flicking its tail by day (or when it must [sit]); its eyes [shut] as it purrs. */
    protected fun cat(x: Float, y: Float, shut: Boolean = false, sit: Boolean = false) {
        val cx = iso.ix(x, y); val by = iso.iy(x, y, 5.3f)
        val o = catO; val od = catD; val ol = catL
        if (P > 1) { catFine(cx.toFloat(), by.toFloat(), o, od, ol, shut, sit); return }
        if (!sit && (env.dark > 0.5f || winter)) {
            c.fillEllipse(cx + 0.5f, by - 2f, 5f, 2.6f, o); c.fillEllipse(cx - 1f, by - 3f, 3f, 1.4f, ol)
            c.fillEllipse(cx - 3.5f, by - 2.5f, 2.2f, 2f, o); c.set(cx - 5, by - 5, od); c.set(cx - 3, by - 5, od)
            c.hline(cx - 1, cx + 5, by - 1, od); c.set(cx + 2, by - 3, od); c.set(cx + 4, by - 2, od)
            return
        }
        val flick = (sin(t * 2.3) > 0.4).let { if (it) 1 else 0 }
        c.fillEllipse(cx + 0.5f, by - 3f, 3.2f, 3f, o)
        c.vline(cx - 2, by - 5, by - 1, ol); c.set(cx + 1, by - 4, od); c.set(cx + 2, by - 2, od)
        c.fillEllipse(cx + 0.5f, by - 7.5f, 2.4f, 2f, o)
        c.set(cx - 1, by - 10, o); c.set(cx + 2, by - 10, o); c.set(cx - 1, by - 9, od); c.set(cx + 2, by - 9, od)
        val blink = shut || sin(t * 0.7 + 1.3) > 0.96
        c.set(cx - 1, by - 8, if (blink) od else Col.hex(0x3A5A2A)); c.set(cx + 1, by - 8, if (blink) od else Col.hex(0x3A5A2A))
        c.line(cx + 3, by - 1, cx + 6, by - 2 - flick, od); c.set(cx + 6, by - 3 - flick, o)
    }

    /** The cat closer up, anchored at picture ([cx], [by]) as on the scene's canvas: tabby stripes, ears lined pink, eyes with slit pupils, whiskers. */
    protected fun catFine(cx: Float, by: Float, o: Int, od: Int, ol: Int, shut: Boolean, sit: Boolean) {
        val p = P.toFloat()
        val pink = Col.hex(0xF0A0A0); val whisker = Col.hex(0xFFF0DC)
        fun ear(x0: Float, y0: Float, left: Boolean) {
            // a triangle standing on (x0, y0) … (x0 + 1.4, y0), its tip up
            c.polyBegin(); c.polyAdd(x0, y0); c.polyAdd(x0 + 1.5f * p, y0); c.polyAdd(x0 + (if (left) 0.35f else 1.15f) * p, y0 - 1.7f * p); c.polyFill(o)
            c.set(floor(x0 + 0.75f * p).toInt(), floor(y0 - 0.6f * p).toInt(), pink)
            c.line(floor(x0 + (if (left) 0.35f else 1.15f) * p).toInt(), floor(y0 - 1.6f * p).toInt(), floor(x0 + (if (left) 0f else 1.5f) * p).toInt(), floor(y0).toInt() - 1, od)
        }
        if (!sit && (env.dark > 0.5f || winter)) {
            // curled up asleep, the tail round its front
            c.fillEllipse(cx + 0.5f * p, by - 2f * p, 5f * p, 2.6f * p, o)
            for (k in 0..3) arc(cx + (1.5f + k * 1.1f) * p, by - 0.2f * p, 1.4f * p, 2.3f * p, od, PI.toFloat() * 1.15f, PI.toFloat() * 1.5f)
            c.fillEllipse(cx - 1f * p, by - 3f * p, 3f * p, 1.4f * p, ol)
            ear(cx - 5.4f * p, by - 3.6f * p, true); ear(cx - 3.3f * p, by - 3.8f * p, false)
            c.fillEllipse(cx - 3.5f * p, by - 2.5f * p, 2.2f * p, 2f * p, o)
            c.fillEllipse(cx - 3.9f * p, by - 1.7f * p, 1.2f * p, 0.8f * p, ol)
            c.line(floor(cx - 4.6f * p).toInt(), floor(by - 2.7f * p).toInt(), floor(cx - 3.8f * p).toInt(), floor(by - 2.4f * p).toInt(), od)
            c.line(floor(cx - 3.2f * p).toInt(), floor(by - 2.4f * p).toInt(), floor(cx - 2.4f * p).toInt(), floor(by - 2.7f * p).toInt(), od)
            c.set(floor(cx - 3.9f * p).toInt(), floor(by - 1.9f * p).toInt(), pink)
            c.fillEllipse(cx + 1.5f * p, by - 0.6f * p, 4.6f * p, 0.7f * p, od)
            c.fillEllipse(cx + 1.3f * p, by - 0.8f * p, 4.2f * p, 0.45f * p, o)
            c.fillEllipse(cx - 2.8f * p, by - 0.7f * p, 0.9f * p, 0.5f * p, ol)
            return
        }
        val flick = (sin(t * 2.3) > 0.4).let { if (it) 1 else 0 }
        // the tail, curling up at its tip
        for (o2 in 0 until P) c.line(floor(cx + 3f * p).toInt(), floor(by - 1f * p).toInt() + o2 - P / 2, floor(cx + 6f * p).toInt(), floor(by - (2f + flick) * p).toInt() + o2 - P / 2, if (o2 == 0) o else od)
        c.fillEllipse(cx + 6.2f * p, by - (2.8f + flick) * p, 0.6f * p, 0.9f * p, o)
        // the body, the white chest, the stripes, the paws
        c.fillEllipse(cx + 0.5f * p, by - 3f * p, 3.2f * p, 3f * p, o)
        for (k in 0..2) arc(cx + (1.6f + k * 0.2f) * p, by - (1.2f + k * 1.3f) * p, 1.6f * p, 0.9f * p, od, -PI.toFloat() * 0.35f, PI.toFloat() * 0.35f)
        c.fillEllipse(cx - 1.4f * p, by - 3f * p, 1f * p, 2f * p, ol)
        c.fillEllipse(cx - 1.3f * p, by - 0.5f * p, 0.9f * p, 0.5f * p, ol); c.fillEllipse(cx + 0.4f * p, by - 0.5f * p, 0.9f * p, 0.5f * p, ol)
        // the head: ears, stripes on the brow, eyes, nose, whiskers
        ear(cx - 1.6f * p, by - 8.6f * p, true); ear(cx + 1.2f * p, by - 8.6f * p, false)
        c.fillEllipse(cx + 0.5f * p, by - 7.5f * p, 2.4f * p, 2f * p, o)
        c.fillEllipse(cx + 0.5f * p, by - 6.6f * p, 1.3f * p, 0.8f * p, ol)
        for (k in -1..1) c.vline(floor(cx + (0.5f + k * 0.6f) * p).toInt(), floor(by - 9.3f * p).toInt(), floor(by - 8.6f * p).toInt(), od)
        val blink = shut || sin(t * 0.7 + 1.3) > 0.96
        for (ex in floatArrayOf(-0.6f, 1.4f)) {
            val x0 = floor(cx + ex * p).toInt(); val y0 = floor(by - 7.9f * p).toInt()
            if (blink) c.hline(x0 - 1, x0 + P - 2, y0 + P / 2, od)
            else { c.fillRect(x0 - 1, y0, P, P, Col.hex(0x8AB04A)); c.vline(x0 - 1 + P / 2, y0, y0 + P - 1, Col.hex(0x1E2A12)) }
        }
        val nx = floor(cx + 0.5f * p).toInt(); val ny = floor(by - 6.9f * p).toInt()
        c.set(nx, ny, pink); c.set(nx - 1, ny, pink); c.set(nx, ny + 1, od)
        // the whiskers: fine light lines over whatever is behind, not outlined
        s.fx {
            for (s0 in intArrayOf(-1, 1)) for (k in 0..1) {
                val n = 2 * P
                for (i in 0..n) c.blend(nx + s0 * (2 + i), ny + k + (k - 1) * i / n, whisker, 0.8f - 0.4f * i / n)
            }
        }
    }

    /** Three clay pots of geraniums along the wall, red in bloom from spring to autumn, bare with a cap of snow in winter. */
    protected fun pots() {
        for ((px, py) in listOf(10.0f to 6.0f, 10.45f to 6.85f, 10.05f to 7.75f)) {
            val cx = iso.ix(px, py); val by = iso.iy(px, py, 0f)
            val pot = Col.hex(0xC0603A); val potL = Col.hex(0xD8784A); val potD = Col.hex(0x8A4028)
            if (P > 1) {
                // closer up: a tapered pot with a rolled rim, a mound of round leaves, umbels of florets; in winter snow on the earth and dry stalks
                val p = P.toFloat(); val mx = cx + 0.5f * p
                flowerPot(mx, by.toFloat(), 5f, 3.1f, 3.55f, 4.5f, pot, potL, potD)
                if (winter) {
                    for (k in -1..1) c.line(floor(mx + k * 1.2f * p).toInt(), floor(by - 6.6f * p).toInt(), floor(mx + k * 1.8f * p).toInt(), floor(by - 9.2f * p).toInt(), Pal.WOOD_D)
                    c.fillEllipse(mx, by - 6.8f * p, 3.6f * p, 1.1f * p, Pal.SNOW_M); c.fillEllipse(mx - 0.4f * p, by - 7.1f * p, 3f * p, 0.8f * p, Pal.SNOW_L)
                    continue
                }
                leaves(mx, by - 10f * p, 5f * p, 3.4f * p, 6, (px * 10).toInt())
                val n = if (env.season == Season.AUTUMN) 3 else 6
                for (k in 0 until n) {
                    val fx = -4 + (Noise.rnd(k, (px * 10).toInt()) * 9).toInt(); val fy = -14 + (Noise.rnd(k, (py * 10).toInt()) * 4).toInt()
                    umbel(cx + (fx + 1f) * p, by + (fy + 0.3f) * p, 1.3f * p, 1f * p, 3 * P, k + (px * 10).toInt())
                }
                continue
            }
            c.fillRect(cx - 3, by - 5, 7, 5, pot); c.vline(cx - 3, by - 5, by - 1, potL); c.vline(cx + 3, by - 5, by - 1, potD)
            c.hline(cx - 4, cx + 4, by - 6, potL); c.hline(cx - 4, cx + 4, by - 7, potD)
            if (winter) { c.hline(cx - 3, cx + 3, by - 8, Pal.SNOW_L); c.hline(cx - 2, cx + 2, by - 9, Pal.SNOW_M); continue }
            c.fillEllipse(cx + 0.5f, by - 10f, 5f, 3.4f, Pal.LEAF)
            c.fillEllipse(cx - 1f, by - 11f, 2.5f, 1.6f, Col.hex(0x5AAA4A))
            val n = if (env.season == Season.AUTUMN) 3 else 6
            for (k in 0 until n) {
                val fx = cx - 4 + (Noise.rnd(k, (px * 10).toInt()) * 9).toInt(); val fy = by - 14 + (Noise.rnd(k, (py * 10).toInt()) * 4).toInt()
                c.set(fx, fy, Pal.GERANIUM); c.set(fx + 1, fy, Pal.GERANIUM_D); c.set(fx, fy - 1, Col.hex(0xFF6A6A))
            }
        }
    }

    /** The vegetable bed in the front garden: board edges, dark soil, rows of lettuce and cabbage, a pumpkin in autumn; snow over it in winter. */
    protected fun vegetables() {
        val ba = 1.6f; val bb = 6.2f; val bc = 10.0f; val bd = 11.3f; val bz = 1.4f
        iso.box(ba, bc, 0f, bb - ba, bd - bc, bz, Pal.SOIL_L, Pal.WOOD_M, Pal.WOOD_D)
        onFlat(bz, ba + 0.1f, bc + 0.1f, bb - 0.1f, bd - 0.1f) { x, y, px, py ->
            if (winter) (if (Noise.rnd(px, py, 3) < 0.15f) Pal.SNOW_M else Pal.SNOW_L)
            else if (abs(y - 10.35f) < 0.1f || abs(y - 10.95f) < 0.1f) Pal.SOIL_D
            else if (Noise.rnd(px, py, 5) < 0.2f) Pal.SOIL_L else Pal.SOIL_M
        }
        if (winter) return
        for (row in 0..1) {
            var x = ba + 0.45f
            var k = 0
            while (x < bb - 0.3f) {
                val yy = 10.35f + row * 0.6f
                val cx = iso.sx(x, yy); val cy = iso.sy(x, yy, bz)
                if (P > 1) { veg(cx, cy, row, k); x += 0.62f; k++; continue }
                when {
                    env.season == Season.SPRING -> { c.set(cx.toInt(), cy.toInt() - 1, Col.hex(0x7AC050)); c.set(cx.toInt() - 1, cy.toInt() - 2, Col.hex(0x5AA040)); c.set(cx.toInt() + 1, cy.toInt() - 2, Col.hex(0x5AA040)) }
                    env.season == Season.AUTUMN && row == 1 && k == 2 -> {
                        c.fillEllipse(cx, cy - 2.5f, 4f, 2.8f, Col.hex(0xE07A2A)); c.vline(cx.toInt() - 1, (cy - 5).toInt(), (cy - 1).toInt(), Col.hex(0xC0601E))
                        c.vline(cx.toInt() + 2, (cy - 4).toInt(), (cy - 1).toInt(), Col.hex(0xC0601E)); c.set(cx.toInt(), (cy - 6).toInt(), Pal.WOOD_D)
                    }
                    row == 0 -> { c.fillEllipse(cx, cy - 2f, 2.6f, 2f, Col.hex(0x8ACD5A)); c.set(cx.toInt() - 1, (cy - 3).toInt(), Col.hex(0xB6E27A)); c.set(cx.toInt() + 1, (cy - 1).toInt(), Col.hex(0x5E9A3A)) }
                    else -> { c.fillEllipse(cx, cy - 2.5f, 3.4f, 2.6f, Col.hex(0x6A9A8A)); c.fillEllipse(cx - 0.5f, cy - 3f, 2f, 1.5f, Col.hex(0x9ACAB6)); c.set(cx.toInt() + 2, (cy - 1).toInt(), Col.hex(0x4A7A6A)) }
                }
                x += 0.62f; k++
            }
        }
    }

    /** One plant of the bed closer up at picture ([cx], [cy]): sprouts, frilly lettuce, veined cabbage, a ribbed pumpkin. */
    protected fun veg(cx: Float, cy: Float, row: Int, k: Int) {
        val p = P.toFloat()
        when {
            env.season == Season.SPRING -> {
                c.vline(floor(cx).toInt(), floor(cy - 1.4f * p).toInt(), floor(cy).toInt() - 1, Col.hex(0x6AB048))
                c.fillEllipse(cx - 0.8f * p, cy - 1.7f * p, 0.8f * p, 0.4f * p, Col.hex(0x5AA040))
                c.fillEllipse(cx + 0.9f * p, cy - 1.8f * p, 0.8f * p, 0.4f * p, Col.hex(0x7AC050))
            }
            env.season == Season.AUTUMN && row == 1 && k == 2 -> {
                c.fillEllipse(cx, cy - 2.5f * p, 4f * p, 2.8f * p, Col.hex(0xC0601E))
                c.fillEllipse(cx - 0.3f, cy - 2.7f * p, 4f * p - 1f, 2.8f * p - 1f, Col.hex(0xE07A2A))
                for (r in -2..2) if (r != 0) arc(cx + r * 1.1f * p, cy - 2.5f * p, abs(r) * 0.5f * p + 0.5f, 2.6f * p, Col.hex(0xC0601E), if (r < 0) PI.toFloat() * 0.6f else -PI.toFloat() * 0.4f, if (r < 0) PI.toFloat() * 1.4f else PI.toFloat() * 0.4f)
                c.fillEllipse(cx - 1.4f * p, cy - 3.6f * p, 1f * p, 0.5f * p, Col.hex(0xF4A050))
                c.fillRect(floor(cx).toInt(), floor(cy - 6f * p).toInt(), max(1, P - 1), P + 1, Pal.WOOD_D)
                c.line(floor(cx).toInt() + 1, floor(cy - 5.6f * p).toInt(), floor(cx + 1.6f * p).toInt(), floor(cy - 6.2f * p).toInt(), Col.hex(0x5E8A3A))
            }
            row == 0 -> {
                // lettuce: a rosette, frilled at its leaves' edges
                c.fillEllipse(cx, cy - 2f * p, 2.6f * p, 2f * p, Col.hex(0x6AAD48))
                c.fillEllipse(cx - 0.3f, cy - 2.2f * p, 2.6f * p - 1f, 2f * p - 1f, Col.hex(0x8ACD5A))
                arc(cx, cy - 2f * p, 2.6f * p - 0.5f, 2f * p - 0.5f, Col.hex(0xB6E27A), PI.toFloat() * 1.05f, PI.toFloat() * 1.95f, 2)
                c.fillEllipse(cx - 0.2f * p, cy - 2.6f * p, 1.3f * p, 0.9f * p, Col.hex(0xA6DA6E))
                c.line(floor(cx).toInt(), floor(cy - 1f * p).toInt(), floor(cx - 1.4f * p).toInt(), floor(cy - 2.6f * p).toInt(), Col.hex(0xC8EC98))
                c.line(floor(cx).toInt(), floor(cy - 1f * p).toInt(), floor(cx + 1.5f * p).toInt(), floor(cy - 2.4f * p).toInt(), Col.hex(0x6AAD48))
            }
            else -> {
                // a cabbage: the tight head in pale outer leaves, their veins
                c.fillEllipse(cx, cy - 2.5f * p, 3.4f * p, 2.6f * p, Col.hex(0x4A7A6A))
                c.fillEllipse(cx - 0.3f, cy - 2.7f * p, 3.4f * p - 1f, 2.6f * p - 1f, Col.hex(0x6A9A8A))
                for (v in 0..4) {
                    val a = PI.toFloat() * (0.15f + v * 0.175f)
                    c.line(floor(cx).toInt(), floor(cy - 1.2f * p).toInt(), floor(cx - cos(a) * 3f * p).toInt(), floor(cy - 1.2f * p - sin(a) * 2f * p).toInt(), Col.hex(0x8ABAAA))
                }
                c.fillEllipse(cx - 0.5f * p, cy - 3f * p, 2f * p, 1.5f * p, Col.hex(0x88B8A4))
                c.fillEllipse(cx - 0.8f * p, cy - 3.3f * p, 1.4f * p, 1f * p, Col.hex(0x9ACAB6))
                arc(cx - 0.5f * p, cy - 3f * p, 2f * p - 0.5f, 1.5f * p - 0.5f, Col.hex(0x5A8A7A), 0f, PI.toFloat())
            }
        }
    }

    /** A brown hen pecking at the grass: her head bobs down and up. */
    protected fun hen(x: Float, y: Float) {
        val cx = iso.ix(x, y); val by = iso.iy(x, y, 0f)
        val b = Col.hex(0x9A5A2E); val bl = Col.hex(0xC07A40); val bd = Col.hex(0x6A3A1E)
        val peck = sin(t * 3.1) > 0.35
        if (P > 1) { henFine(cx.toFloat(), by.toFloat(), b, bl, bd, peck); return }
        c.vline(cx - 1, by - 2, by - 1, Col.hex(0xE0A030)); c.vline(cx + 1, by - 2, by - 1, Col.hex(0xE0A030))
        c.fillEllipse(cx + 0.5f, by - 5f, 4f, 3f, b)
        c.fillEllipse(cx - 0.5f, by - 5.5f, 2.2f, 1.4f, bl)
        c.fillEllipse(cx + 4f, by - 7f, 1.6f, 2.4f, bd); c.set(cx + 4, by - 9, bd)
        if (peck) {
            c.fillRect(cx - 5, by - 5, 3, 3, b); c.set(cx - 6, by - 3, Col.hex(0xE0A030)); c.set(cx - 4, by - 6, red)
        } else {
            c.fillRect(cx - 4, by - 9, 3, 4, b); c.set(cx - 5, by - 7, Col.hex(0xE0A030)); c.set(cx - 3, by - 10, red); c.set(cx - 4, by - 10, red)
            c.set(cx - 3, by - 8, Pal.OUTLINE); c.set(cx - 4, by - 6, red)
        }
    }

    /** The hen closer up, anchored at picture ([cx], [by]) as on the scene's canvas: thin legs with toes, scalloped feathers, a folded wing, tail plumes, comb, wattle, eye and beak. */
    protected fun henFine(cx: Float, by: Float, b: Int, bl: Int, bd: Int, peck: Boolean) {
        val p = P.toFloat()
        val leg = Col.hex(0xE0A030); val beak = Col.hex(0xE8B040)
        for (lx in floatArrayOf(-1f, 1f)) {
            val x0 = floor(cx + (lx + 0.5f) * p).toInt()
            c.vline(x0, floor(by - 2.2f * p).toInt(), floor(by).toInt() - 1, leg)
            c.hline(x0 - P / 2 - 1, x0 + P / 2, floor(by).toInt() - 1, leg)
        }
        // tail plumes, then the body with its feathers, the wing
        for (k in 0..2) c.fillEllipse(cx + (3.6f + k * 0.4f) * p, by - (6.4f + k * 0.5f) * p, 1.2f * p, (2.2f - k * 0.3f) * p, if (k == 1) Col.hex(0x3A2412) else bd)
        c.set(floor(cx + 4f * p).toInt(), floor(by - 9f * p).toInt(), bd)
        c.fillEllipse(cx + 0.5f * p, by - 5f * p, 4f * p, 3f * p, bd)
        c.fillEllipse(cx + 0.2f * p, by - 5.3f * p, 4f * p - 1f, 3f * p - 1f, b)
        // the breast and flank feathers: little scallops, each lit along its top
        val bdark = Col.scale(bd, 0.85f)
        for (row in 0..2) for (k in 0..4) {
            val fx = cx + (-2.6f + k * 1.25f + (row % 2) * 0.62f) * p; val fy = by - (3.2f + row * 1.0f) * p
            if (((fx - cx - 0.5f * p) / (4f * p)).let { it * it } + ((fy - by + 5f * p) / (3f * p)).let { it * it } > 0.8f) continue
            arc(fx, fy - 0.45f * p, 0.62f * p, 0.5f * p, bdark, 0.25f, PI.toFloat() - 0.25f)
            c.set(floor(fx).toInt(), floor(fy - 0.9f * p).toInt(), bl)
        }
        c.fillEllipse(cx - 0.5f * p, by - 5.5f * p, 2.2f * p, 1.4f * p, bd)
        c.fillEllipse(cx - 0.6f * p, by - 5.7f * p, 2.2f * p - 1f, 1.4f * p - 1f, bl)
        for (k in 0..2) c.line(floor(cx + (-1.5f + k * 0.9f) * p).toInt(), floor(by - 5.4f * p).toInt(), floor(cx + (-0.4f + k * 0.9f) * p).toInt(), floor(by - 4.5f * p).toInt(), b)
        // the head, down in the grass or up and looking
        val hx: Float; val hy: Float
        if (peck) { hx = cx - 3.5f * p; hy = by - 3.5f * p } else { hx = cx - 2.5f * p; hy = by - 7f * p }
        if (!peck) c.fillEllipse(cx - 2f * p, by - 5.8f * p, 1.4f * p, 1.8f * p, b)
        c.fillEllipse(hx, hy, 1.6f * p, 1.5f * p, b)
        c.fillEllipse(hx - 0.3f * p, hy - 0.4f * p, 1f * p, 0.7f * p, bl)
        val tip = hx - 2.4f * p
        c.polyBegin(); c.polyAdd(hx - 1.2f * p, hy - 0.4f * p); c.polyAdd(tip, hy + 0.2f * p); c.polyAdd(hx - 1.1f * p, hy + 0.5f * p); c.polyFill(beak)
        val redD = Col.hex(0xA82420)
        c.fillEllipse(hx - 0.1f * p, hy - 1.2f * p, 1.1f * p, 0.45f * p, redD)
        for (k in 0..2) c.fillCircle(hx - 0.9f * p + k * 0.75f * p, hy - 1.55f * p - (if (k == 1) 0.35f * p else 0f), 0.42f * p, red)
        c.fillEllipse(hx - 0.9f * p, hy + 1.1f * p, 0.45f * p, 0.7f * p, red)
        c.set(floor(hx - 0.4f * p).toInt(), floor(hy - 0.4f * p).toInt(), Pal.OUTLINE)
        c.set(floor(hx - 0.4f * p).toInt() - 1, floor(hy - 0.4f * p).toInt() - 1, Col.hex(0xFFE0A0))
    }

    /** Sunlight through the window lying on the floor by day, the panes' cross in its shadow. */
    protected fun sunPatch() {
        val a = 0.34f * env.sun.coerceIn(0f, 1f)
        if (a <= 0.01f) return
        val shift = ((s.hour - 13f) / 6f).coerceIn(-1f, 1f) * 0.7f
        val q0 = y0 + 1.5f; val q1 = y0 + 3.6f
        val p0 = winA + 0.1f; val p1 = winB - 0.1f
        s.fx {
            c.polyBegin()
            c.polyAdd(iso.sx(p0, q0), iso.sy(p0, q0, fl)); c.polyAdd(iso.sx(p1, q0), iso.sy(p1, q0, fl))
            c.polyAdd(iso.sx(p1 + shift, q1), iso.sy(p1 + shift, q1, fl)); c.polyAdd(iso.sx(p0 + shift, q1), iso.sy(p0 + shift, q1, fl))
            val ids = c.ids
            c.polyScan { px, py ->
                if (!c.inside(px, py)) return@polyScan
                val id = ids[c.index(px, py)]
                if (id != floorId && id != rugId) return@polyScan
                val y = flatY(px, py, fl); val x = flatX(px, py, fl) - shift * (y - q0) / (q1 - q0)
                val u = (x - p0) / (p1 - p0); val v = (y - q0) / (q1 - q0)
                if (abs(u - 0.5f) < 0.05f || abs(v - 0.5f) < 0.06f) return@polyScan
                c.blend(px, py, Col.hex(0xFFF4D0), a)
            }
        }
    }

    companion object {
        /** How far off the floor an open door's leaf ends (nominal px): the shoes of someone behind it show under it. */
        const val LEAF_GAP = 2f

        /** The cat's gaits from the side ([catSide]). */
        const val STAND = 0
        const val BOW = 1
        const val RUN = 2
        const val GATHER = 3

        /** The stove's bench, where the grandmother sleeps at night ("za pečjo"). */
        const val BED = "zapecek"

        /** The red wool blanket on it (the sleeper's cover, see PeoplePainter.lying), with its ochre check. */
        val WOOL_L = Col.hex(0xD8604A); val WOOL_M = Col.hex(0xB8452E); val WOOL_D = Col.hex(0x7E2A20); val WOOL_CHECK = Col.hex(0xC98A3A)

        /** The coarse brown blanket of the straw bed (level 1), and its sleeper's cover. */
        val COARSE = intArrayOf(Col.hex(0xA08A6A), Col.hex(0x846E52), Col.hex(0x5A4A38))

        /** A straw mattress rolled out on the floor for the night: Ančka's in the kitchen, Vida's in the attic, France's in the workshop. */
        const val MATTRESS = "mattress"

        /** A child's bed: Zala's small mattress in the kitchen, Nejc's own bed in the attic, Tine's cot in the workshop. */
        const val COT = "cot"
    }
}
