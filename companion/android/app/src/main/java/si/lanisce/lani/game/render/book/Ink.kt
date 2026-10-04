package si.lanisce.lani.game.render.book

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.PixelCanvas
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The inks and washes of the book pictures: sepia-black lines on warm paper, hatching in a softer brown, and the few flat
 * colours a hand-coloured folk print has, laid on thin so the paper shows through.
 */
internal object InkPal {
    val PAPER = Col.hex(0xF0E4C6)
    val PAPER_SHADE = Col.hex(0xE0CDA3)
    val INK = Col.hex(0x2A1D14)
    val SOFT = Col.hex(0x7C6651)
    val SKY = Col.hex(0x86ABC2)
    val WATER = Col.hex(0x5A8BA6)
    val GRASS = Col.hex(0x9AAA66)
    val LEAF = Col.hex(0x6F8C4A)
    val PINE = Col.hex(0x4D6A46)
    val EARTH = Col.hex(0xBF9762)
    val STONE = Col.hex(0xA0978A)
    val WOOD = Col.hex(0x94653D)
    val GOLD = Col.hex(0xE4A72A)
    val RED = Col.hex(0xB4443A)
    val BLUE = Col.hex(0x4E6C99)
    val WHITE = Col.hex(0xFBF7EC)
    val NIGHT = Col.hex(0x262E48)
    val FIRE = Col.hex(0xE8742C)
}

/**
 * Drawing in ink on a [PixelCanvas]: lines, shapes of any outline (filled even-odd), washes that let the paper through,
 * hatching (diagonal lines at a fixed pitch, crossed for the darker shade), blobs (the union of circles: a cloud, a
 * treetop, a fleece). [seed] varies the washes' grain.
 */
internal class Ink(val c: PixelCanvas, val seed: Int) {
    val w: Int get() = c.width
    val h: Int get() = c.height

    fun dot(x: Int, y: Int, col: Int = InkPal.INK) = c.set(x, y, col)

    fun line(x0: Float, y0: Float, x1: Float, y1: Float, col: Int = InkPal.INK) =
        c.line(x0.roundToInt(), y0.roundToInt(), x1.roundToInt(), y1.roundToInt(), col)

    /** Calls [plot] for every pixel inside the outline [xs], [ys] (even-odd, so any shape), clipped to the canvas. */
    fun scan(xs: FloatArray, ys: FloatArray, plot: (Int, Int) -> Unit) {
        val n = xs.size
        if (n < 3) return
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (v in ys) { minY = min(minY, v); maxY = max(maxY, v) }
        val cross = FloatArray(n)
        for (y in max(0, floor(minY).toInt())..min(h - 1, ceil(maxY).toInt())) {
            val yc = y + 0.5f
            var k = 0
            for (i in 0 until n) {
                val j = if (i + 1 == n) 0 else i + 1
                val ya = ys[i]; val yb = ys[j]
                if ((ya <= yc && yc < yb) || (yb <= yc && yc < ya)) cross[k++] = xs[i] + (yc - ya) / (yb - ya) * (xs[j] - xs[i])
            }
            cross.sort(0, k)
            var i = 0
            while (i + 1 < k) {
                for (x in max(0, ceil(cross[i] - 0.5f).toInt())..min(w - 1, ceil(cross[i + 1] - 0.5f).toInt() - 1)) plot(x, y)
                i += 2
            }
        }
    }

    /** Whether pixel ([x], [y]) is on the hatching's lines: every [step]th diagonal, both ways when [cross]. */
    fun hatched(x: Int, y: Int, step: Int, cross: Boolean = false): Boolean =
        Math.floorMod(x + y, step) == 0 || (cross && Math.floorMod(x - y, step) == 0)

    /** A thin colour laid over the paper at pixel ([x], [y]): [a] of [col], a little uneven, as a brush leaves it. */
    fun washAt(x: Int, y: Int, col: Int, a: Float) {
        c.blend(x, y, col, a * (0.78f + 0.44f * Noise.v2(x / 4.5f, y / 4.5f, seed)))
    }

    fun wash(xs: FloatArray, ys: FloatArray, col: Int, a: Float = 0.5f) = scan(xs, ys) { x, y -> washAt(x, y, col, a) }
    fun solid(xs: FloatArray, ys: FloatArray, col: Int) = scan(xs, ys) { x, y -> c.set(x, y, col) }
    fun hatch(xs: FloatArray, ys: FloatArray, step: Int = 3, cross: Boolean = false, col: Int = InkPal.SOFT) =
        scan(xs, ys) { x, y -> if (hatched(x, y, step, cross)) c.set(x, y, col) }

    fun outline(xs: FloatArray, ys: FloatArray, closed: Boolean = true, col: Int = InkPal.INK) {
        for (i in 0 until xs.size - 1) line(xs[i], ys[i], xs[i + 1], ys[i + 1], col)
        if (closed && xs.size > 2) line(xs.last(), ys.last(), xs[0], ys[0], col)
    }

    /** A rectangle's outline as [scan] takes it. */
    fun rect(x0: Float, y0: Float, x1: Float, y1: Float): Pair<FloatArray, FloatArray> =
        floatArrayOf(x0, x1, x1, x0) to floatArrayOf(y0, y0, y1, y1)

    /** An outline given as x, y pairs, as [scan] takes it. */
    fun xy(vararg p: Float): Pair<FloatArray, FloatArray> = FloatArray(p.size / 2) { p[2 * it] } to FloatArray(p.size / 2) { p[2 * it + 1] }

    /**
     * A shape through the points (x, y pairs): a [wash] of [a] (or [fill], solid), hatched every [hatch] pixels (crossed
     * when [cross]), outlined in [edge] (none when null).
     */
    fun area(
        vararg p: Float, wash: Int? = null, a: Float = 0.5f, fill: Int? = null, hatch: Int = 0, cross: Boolean = false,
        edge: Int? = InkPal.INK, hatchCol: Int = InkPal.SOFT,
    ) {
        val (xs, ys) = xy(*p)
        fill?.let { solid(xs, ys, it) }
        wash?.let { wash(xs, ys, it, a) }
        if (hatch > 0) hatch(xs, ys, hatch, cross, hatchCol)
        edge?.let { outline(xs, ys, true, it) }
    }

    /**
     * A blob: the union of the circles ([cx], [cy], [r]). Inside gets [fill] (a wash of [a], or solid when [a] is 1), the
     * pixels [shaded] says hatched every [shade] pixels (its shaded side), and its edge inked in [edge].
     */
    fun blob(
        cx: FloatArray, cy: FloatArray, r: FloatArray, fill: Int?, a: Float = 0.5f, edge: Int = InkPal.INK,
        shade: Int = 0, shaded: (Int, Int) -> Boolean = { _, _ -> false },
    ) {
        fun inside(x: Int, y: Int): Boolean {
            for (i in cx.indices) {
                val dx = x + 0.5f - cx[i]; val dy = y + 0.5f - cy[i]
                if (dx * dx + dy * dy <= r[i] * r[i]) return true
            }
            return false
        }
        var x0 = Int.MAX_VALUE; var x1 = Int.MIN_VALUE; var y0 = Int.MAX_VALUE; var y1 = Int.MIN_VALUE
        for (i in cx.indices) {
            x0 = min(x0, floor(cx[i] - r[i]).toInt()); x1 = max(x1, ceil(cx[i] + r[i]).toInt())
            y0 = min(y0, floor(cy[i] - r[i]).toInt()); y1 = max(y1, ceil(cy[i] + r[i]).toInt())
        }
        for (y in max(0, y0)..min(h - 1, y1)) for (x in max(0, x0)..min(w - 1, x1)) {
            if (!inside(x, y)) continue
            val edgePx = !inside(x - 1, y) || !inside(x + 1, y) || !inside(x, y - 1) || !inside(x, y + 1)
            when {
                edgePx -> c.set(x, y, edge)
                shade > 0 && shaded(x, y) && hatched(x, y, shade) -> c.set(x, y, InkPal.SOFT)
                fill != null && a >= 1f -> c.set(x, y, fill)
                fill != null -> washAt(x, y, fill, a)
            }
        }
    }
}

/**
 * A figure's or a prop's own coordinates on the [Ink]: units round its anchor ([ax], [ay]: where it stands, the feet),
 * y up being negative, [k] pixels a unit, mirrored when [flip] (it faces left). What it draws: lines, shapes, ovals, dots.
 */
internal class Pen(val ink: Ink, val ax: Float, val ay: Float, val k: Float, val flip: Boolean) {
    fun x(u: Float): Float = ax + (if (flip) -u else u) * k
    fun y(v: Float): Float = ay + v * k

    /** Points given as u, v pairs, on the canvas. */
    fun pts(p: FloatArray): Pair<FloatArray, FloatArray> =
        FloatArray(p.size / 2) { x(p[2 * it]) } to FloatArray(p.size / 2) { y(p[2 * it + 1]) }

    fun line(u0: Float, v0: Float, u1: Float, v1: Float, col: Int = InkPal.INK) = ink.line(x(u0), y(v0), x(u1), y(v1), col)

    /** A line through the points (u, v pairs), not closed. */
    fun lines(vararg p: Float, col: Int = InkPal.INK) {
        for (i in 0 until p.size / 2 - 1) line(p[2 * i], p[2 * i + 1], p[2 * i + 2], p[2 * i + 3], col)
    }

    /**
     * A shape through the points (u, v pairs): a [wash] of [a] (or [fill], solid), hatched every [hatch] pixels (crossed
     * when [cross]), outlined in [edge] (none when null).
     */
    fun shape(
        p: FloatArray, wash: Int? = null, a: Float = 0.55f, fill: Int? = null, hatch: Int = 0, cross: Boolean = false,
        edge: Int? = InkPal.INK, hatchCol: Int = InkPal.SOFT,
    ) {
        val (xs, ys) = pts(p)
        fill?.let { ink.solid(xs, ys, it) }
        wash?.let { ink.wash(xs, ys, it, a) }
        if (hatch > 0) ink.hatch(xs, ys, hatch, cross, hatchCol)
        edge?.let { ink.outline(xs, ys, true, it) }
    }

    /** An oval round (cu, cv), radii [ru] and [rv], as [shape] draws it. */
    fun oval(
        cu: Float, cv: Float, ru: Float, rv: Float, wash: Int? = null, a: Float = 0.55f, fill: Int? = null, hatch: Int = 0,
        cross: Boolean = false, edge: Int? = InkPal.INK,
    ) = shape(ovalPoints(cu, cv, ru, rv), wash, a, fill, hatch, cross, edge)

    /** The points of an oval, [n] of them, from the angle [from] to [to] (radians, 0 = right, going down first). */
    fun ovalPoints(cu: Float, cv: Float, ru: Float, rv: Float, n: Int = 20, from: Float = 0f, to: Float = (2 * PI).toFloat()): FloatArray {
        val closed = to - from >= (2 * PI).toFloat() - 1e-3f
        val m = if (closed) n else n + 1
        return FloatArray(m * 2) { i ->
            val t = from + (to - from) * (i / 2) / n
            if (i % 2 == 0) cu + ru * cos(t) else cv + rv * sin(t)
        }
    }

    /** An arc of an oval as a line, from angle [from] to [to]. */
    fun arc(cu: Float, cv: Float, ru: Float, rv: Float, from: Float, to: Float, col: Int = InkPal.INK) {
        val p = ovalPoints(cu, cv, ru, rv, 12, from, to)
        lines(*p, col = col)
    }

    /**
     * A blob (the union of circles at [cu], [cv], radii [r]) as [Ink.blob] draws it, its side away from the light (to the
     * figure's right, below [shadeBelow] units) hatched every [shade] pixels.
     */
    fun blob(
        cu: FloatArray, cv: FloatArray, r: FloatArray, fill: Int?, a: Float = 0.55f, edge: Int = InkPal.INK,
        shade: Int = 0, shadeRight: Float? = null, shadeBelow: Float? = null,
    ) {
        val right = shadeRight?.let { x(it) }
        val below = shadeBelow?.let { y(it) }
        ink.blob(
            FloatArray(cu.size) { x(cu[it]) }, FloatArray(cv.size) { y(cv[it]) }, FloatArray(r.size) { r[it] * k },
            fill, a, edge, shade,
        ) { px, py -> (right != null && (if (flip) px < right else px > right)) || (below != null && py > below) }
    }

    /** A dot of ink (a bigger one when the figure is drawn big). */
    fun dot(u: Float, v: Float, col: Int = InkPal.INK) {
        val px = x(u).roundToInt(); val py = y(v).roundToInt()
        ink.dot(px, py, col)
        if (k >= 1.9f) { ink.dot(px + 1, py, col); ink.dot(px, py + 1, col); ink.dot(px + 1, py + 1, col) }
    }
}
