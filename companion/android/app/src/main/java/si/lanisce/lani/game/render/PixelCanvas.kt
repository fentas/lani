package si.lanisce.lani.game.render

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A tiny software raster: ARGB pixels plus two side buffers used by the renderer.
 *
 * - [ids]: which object last wrote each pixel (0 = ground/sky). Objects get increasing ids in painter's
 *   order, which drives the 1-px outline pass and hit-test-free depth reasoning.
 * - [emissive]: pixels that glow (flames, lit windows, sky); the lighting pass leaves them alone.
 *
 * A canvas may hold just a window of a bigger picture (a close-up scene at a finer detail, see [originX]): the
 * drawing calls then take the picture's coordinates, and only what falls in the window lands.
 *
 * Pure Kotlin, no Android types, so it renders in JVM unit tests.
 */
class PixelCanvas(val width: Int, val height: Int) {
    val pixels = IntArray(width * height)
    val ids = IntArray(width * height)
    val emissive = BooleanArray(width * height)

    /** Id written by the drawing calls. */
    var penId = 0
    /** Whether drawn pixels glow. */
    var penEmissive = false

    /** Drawing is limited to rows >= [clipTop] (used for the "rising" construction reveal). */
    var clipTop = 0

    /**
     * The picture's point at this canvas's pixel (0, 0): every drawing call takes the picture's coordinates (x, y)
     * and lands at canvas pixel (x − originX, y − originY), if that's inside. [pixels], [ids] and [emissive] are
     * the canvas's own, row by row from its top-left corner ([index]). 0, 0 (the default): the canvas is the picture.
     */
    var originX = 0
    var originY = 0

    /** The window of the picture this canvas holds: [left] until [right], [top] until [bottom]. */
    val left: Int get() = originX
    val top: Int get() = originY
    val right: Int get() = originX + width
    val bottom: Int get() = originY + height

    /** Whether the picture's point (x, y) lands on this canvas. */
    fun inside(x: Int, y: Int): Boolean = x >= originX && y >= originY && x < originX + width && y < originY + height

    /** The index in [pixels], [ids] and [emissive] of the picture's point (x, y) (inside the window, see [inside]). */
    fun index(x: Int, y: Int): Int = (y - originY) * width + (x - originX)

    /**
     * Sprite zoom: while [zoom] > 1, every pixel drawn at (x, y) lands as a zoom × zoom block at
     * (zx + (x − zx) × zoom, zy + (y − zy) × zoom), so a sprite drawn around its anchor ([zoomAt]) comes out
     * zoom times bigger, in whole blocks. Pixel-space sprites use it at the higher levels of detail.
     */
    var zoom = 1
        private set
    private var zx = 0
    private var zy = 0

    fun zoomAt(ax: Int, ay: Int, k: Int) { zoom = k.coerceAtLeast(1); zx = ax; zy = ay }
    fun zoomOff() { zoom = 1 }

    fun reset(color: Int) {
        pixels.fill(color); ids.fill(0); emissive.fill(false)
        penId = 0; penEmissive = false; clipTop = 0; zoom = 1
    }

    fun set(x: Int, y: Int, c: Int) {
        if (zoom > 1) { block(zx + (x - zx) * zoom, zy + (y - zy) * zoom, zoom, c); return }
        val cx = x - originX; val cy = y - originY
        if (cx < 0 || cy < 0 || y < clipTop || cx >= width || cy >= height) return
        val i = cy * width + cx
        pixels[i] = c; ids[i] = penId; emissive[i] = penEmissive
    }

    /** An n × n block with its top-left corner at (x, y), drawn as one big pixel (unaffected by [zoom]). */
    fun block(x: Int, y: Int, n: Int, c: Int) {
        for (yy in max(max(y, clipTop), originY) until min(y + n, originY + height)) for (xx in max(x, originX) until min(x + n, originX + width)) {
            val i = (yy - originY) * width + (xx - originX)
            pixels[i] = c; ids[i] = penId; emissive[i] = penEmissive
        }
    }

    fun get(x: Int, y: Int): Int =
        if (!inside(x, y)) 0 else pixels[index(x, y)]

    /** Alpha-blend [c] over the pixel without touching its id. */
    fun blend(x: Int, y: Int, c: Int, a: Float) {
        if (zoom > 1) {
            val x0 = zx + (x - zx) * zoom; val y0 = zy + (y - zy) * zoom
            for (yy in y0 until y0 + zoom) for (xx in x0 until x0 + zoom) blendOne(xx, yy, c, a)
            return
        }
        blendOne(x, y, c, a)
    }

    private fun blendOne(x: Int, y: Int, c: Int, a: Float) {
        if (!inside(x, y) || y < clipTop || a <= 0f) return
        val i = index(x, y)
        pixels[i] = Col.mix(pixels[i], c, min(1f, a))
        if (penEmissive) emissive[i] = true
    }

    /** Multiply ground pixels (id 0, below the sky) by [f]; for soft shadows that never darken objects. */
    fun shadeGround(x: Int, y: Int, f: Float) {
        if (!inside(x, y) || y < clipTop) return
        val i = index(x, y)
        if (ids[i] != 0 || emissive[i]) return
        pixels[i] = Col.scale(pixels[i], f)
    }

    fun fillRect(x: Int, y: Int, w: Int, h: Int, c: Int) {
        // a zoomed sprite's coordinates aren't the window's: [set] maps and clips them
        val y0 = if (zoom > 1) y else max(y, originY); val y1 = if (zoom > 1) y + h else min(y + h, originY + height)
        val x0 = if (zoom > 1) x else max(x, originX); val x1 = if (zoom > 1) x + w else min(x + w, originX + width)
        for (yy in y0 until y1) for (xx in x0 until x1) set(xx, yy, c)
    }

    fun hline(x0: Int, x1: Int, y: Int, c: Int) { for (x in min(x0, x1)..max(x0, x1)) set(x, y, c) }
    fun vline(x: Int, y0: Int, y1: Int, c: Int) { for (y in min(y0, y1)..max(y0, y1)) set(x, y, c) }

    fun line(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
        var x = x0; var y = y0
        val dx = abs(x1 - x0); val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1; val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            set(x, y, c)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    /** Rows [a]..[b] of a shape, clipped to the window (not for a zoomed sprite, whose coordinates [set] maps). */
    private fun rowsOf(a: Int, b: Int): IntRange = if (zoom > 1) a..b else max(a, originY)..min(b, originY + height - 1)
    private fun colsOf(a: Int, b: Int): IntRange = if (zoom > 1) a..b else max(a, originX)..min(b, originX + width - 1)

    fun fillCircle(cx: Float, cy: Float, r: Float, c: Int) {
        val r2 = r * r
        for (y in rowsOf(floor(cy - r).toInt(), ceil(cy + r).toInt())) for (x in colsOf(floor(cx - r).toInt(), ceil(cx + r).toInt())) {
            val dx = x + 0.5f - cx; val dy = y + 0.5f - cy
            if (dx * dx + dy * dy <= r2) set(x, y, c)
        }
    }

    fun fillEllipse(cx: Float, cy: Float, rx: Float, ry: Float, c: Int) {
        for (y in rowsOf(floor(cy - ry).toInt(), ceil(cy + ry).toInt())) for (x in colsOf(floor(cx - rx).toInt(), ceil(cx + rx).toInt())) {
            val dx = (x + 0.5f - cx) / rx; val dy = (y + 0.5f - cy) / ry
            if (dx * dx + dy * dy <= 1f) set(x, y, c)
        }
    }

    /** Dithered translucent disc (smoke, dust, glows). */
    fun ditherCircle(cx: Float, cy: Float, r: Float, c: Int, alpha: Float) {
        if (alpha <= 0f) return
        for (y in rowsOf(floor(cy - r).toInt(), ceil(cy + r).toInt())) for (x in colsOf(floor(cx - r).toInt(), ceil(cx + r).toInt())) {
            val dx = x + 0.5f - cx; val dy = y + 0.5f - cy
            val d = sqrt(dx * dx + dy * dy) / r
            if (d > 1f) continue
            val a = alpha * (1f - d * d * 0.6f)
            if (a > Dither.at(x, y)) blend(x, y, c, 0.85f)
        }
    }

    // ---- convex polygon scan conversion (pixel centres inside [xl, xr)) ----

    private val px = FloatArray(16)
    private val py = FloatArray(16)
    private var pn = 0

    fun polyBegin() { pn = 0 }
    fun polyAdd(x: Float, y: Float) { px[pn] = x; py[pn] = y; pn++ }

    inline fun polyFill(crossinline shader: (Int, Int) -> Int) = polyScan { x, y -> set(x, y, shader(x, y)) }

    fun polyFill(c: Int) = polyScan { x, y -> set(x, y, c) }

    inline fun polyScan(crossinline plot: (Int, Int) -> Unit) {
        val n = polyCount()
        if (n < 3) return
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in 0 until n) { minY = min(minY, polyY(i)); maxY = max(maxY, polyY(i)) }
        // clipped to the window (a zoomed sprite's coordinates aren't the window's: [set] maps and clips them)
        val z = zoom > 1
        val y0 = if (z) ceil(minY - 0.5f).toInt() else max(ceil(minY - 0.5f).toInt(), originY)
        val y1 = if (z) ceil(maxY - 0.5f).toInt() - 1 else min(ceil(maxY - 0.5f).toInt() - 1, originY + height - 1)
        for (y in y0..y1) {
            val yc = y + 0.5f
            var xl = Float.MAX_VALUE; var xr = -Float.MAX_VALUE
            for (i in 0 until n) {
                val j = if (i + 1 == n) 0 else i + 1
                val ya = polyY(i); val yb = polyY(j)
                if ((ya <= yc && yc < yb) || (yb <= yc && yc < ya)) {
                    val t = (yc - ya) / (yb - ya)
                    val x = polyX(i) + t * (polyX(j) - polyX(i))
                    if (x < xl) xl = x
                    if (x > xr) xr = x
                }
            }
            if (xl > xr) continue
            val xa = if (z) ceil(xl - 0.5f).toInt() else max(ceil(xl - 0.5f).toInt(), originX)
            val xb = if (z) ceil(xr - 0.5f).toInt() - 1 else min(ceil(xr - 0.5f).toInt() - 1, originX + width - 1)
            for (x in xa..xb) plot(x, y)
        }
    }

    fun polyCount() = pn
    fun polyX(i: Int) = px[i]
    fun polyY(i: Int) = py[i]
}

/** ARGB colour helpers. */
object Col {
    fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    fun hex(v: Long): Int = (0xFF000000L or v).toInt()
    fun r(c: Int) = (c shr 16) and 255
    fun g(c: Int) = (c shr 8) and 255
    fun b(c: Int) = c and 255

    fun mix(a: Int, b: Int, t: Float): Int {
        if (t <= 0f) return a
        if (t >= 1f) return b
        return rgb(
            (r(a) + (r(b) - r(a)) * t).toInt(),
            (g(a) + (g(b) - g(a)) * t).toInt(),
            (b(a) + (b(b) - b(a)) * t).toInt(),
        )
    }

    fun scale(c: Int, f: Float) = rgb((r(c) * f).toInt(), (g(c) * f).toInt(), (b(c) * f).toInt())
    fun mul(c: Int, fr: Float, fg: Float, fb: Float) = rgb((r(c) * fr).toInt(), (g(c) * fg).toInt(), (b(c) * fb).toInt())
    fun lum(c: Int) = (r(c) * 0.3f + g(c) * 0.59f + b(c) * 0.11f) / 255f
}

/** 4x4 ordered dithering thresholds in (0, 1). */
object Dither {
    private val bayer = floatArrayOf(0f, 8f, 2f, 10f, 12f, 4f, 14f, 6f, 3f, 11f, 1f, 9f, 15f, 7f, 13f, 5f)
    fun at(x: Int, y: Int): Float = (bayer[(y and 3) * 4 + (x and 3)] + 0.5f) / 16f
}

/** Deterministic hashing noise. */
object Noise {
    fun hash(a: Int, b: Int = 0, c: Int = 0): Int {
        var h = a * -0x61c88647 + b * 0x27d4eb2d + c * 0x165667b1
        h = h xor (h ushr 15); h *= 0x2c1b3c6d
        h = h xor (h ushr 12); h *= 0x297a2d39
        h = h xor (h ushr 15)
        return h
    }

    /** Uniform in [0, 1). */
    fun rnd(a: Int, b: Int = 0, c: Int = 0): Float = (hash(a, b, c) ushr 8) / 16777216f

    /** Smooth 1D value noise in [0, 1). */
    fun v1(x: Float, seed: Int = 0): Float {
        val i = floor(x).toInt(); val f = x - i
        val t = f * f * (3 - 2 * f)
        return rnd(i, seed) * (1 - t) + rnd(i + 1, seed) * t
    }

    /** Smooth 2D value noise in [0, 1). */
    fun v2(x: Float, y: Float, seed: Int = 0): Float {
        val ix = floor(x).toInt(); val iy = floor(y).toInt()
        val fx = x - ix; val fy = y - iy
        val tx = fx * fx * (3 - 2 * fx); val ty = fy * fy * (3 - 2 * fy)
        val a = rnd(ix, iy, seed); val b = rnd(ix + 1, iy, seed)
        val c = rnd(ix, iy + 1, seed); val d = rnd(ix + 1, iy + 1, seed)
        return (a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty
    }
}
