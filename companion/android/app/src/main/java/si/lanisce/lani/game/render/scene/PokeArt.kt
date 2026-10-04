package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.PixelCanvas
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * The little effects the scenes' pokes share (see [si.lanisce.lani.game.scene.Poke]): a burst of sparks, hearts
 * rising, a puff of dust or steam, the rings of a sound, a bird on the wing. Each is drawn from how far it has come
 * ([u] 0..1, or its age), in the picture's pixels, so every detail and every window of the picture agree; the sparks,
 * hearts, puffs and rings are blended over what is there (the ids stay: what is under them can still be tapped), to
 * be called in the painter's effects pass (`s.fx { }`).
 */
internal object PokeArt {
    /**
     * A burst of [n] sparks from ([x], [y]), [u] of the way through: they fly up and out in a fan, [rise] px high and
     * [spread] px aside at the most, white-hot cooling to orange; [fall] 1 slows them to a stop up high (a fire's), 2
     * brings them back down to where they began, more drops them below it (off an anvil). Each [sz] px square.
     */
    fun sparks(c: PixelCanvas, x: Float, y: Float, u: Float, n: Int, seed: Int, rise: Float, spread: Float, sz: Int, fall: Float = 1f) {
        if (u <= 0f || u >= 1f) return
        val was = c.penEmissive
        c.penEmissive = true
        for (j in 0 until n) {
            val life = 0.55f + 0.45f * Noise.rnd(j, seed, 3)
            val v = u / life
            if (v >= 1f) continue
            val a = (Noise.rnd(j, seed, 1) - 0.5f) * 2.4f
            val sp = 0.45f + 0.55f * Noise.rnd(j, seed, 2)
            val px = x + sin(a) * sp * spread * v
            val py = y - cos(a) * sp * rise * (2f * v - fall * v * v)
            val col = when {
                v < 0.15f -> Pal.FLAME[0]
                v < 0.5f -> Pal.FLAME[1]
                v < 0.8f -> Pal.FLAME[2]
                else -> Pal.FLAME[3]
            }
            val sx = floor(px).toInt(); val sy = floor(py).toInt()
            for (yy in 0 until sz) for (xx in 0 until sz) c.blend(sx + xx, sy + yy, col, 1f)
            // closer up a short glowing trail behind it
            if (sz > 1) for (i in 1..sz) c.blend(sx + sz / 2, sy + sz - 1 + i, Pal.FLAME[2], 0.7f - i * 0.5f / sz)
        }
        c.penEmissive = was
    }

    /** One heart, a pixel of it [sz] × [sz] picture px, its top left at ([x], [y]), faded to [alpha]. */
    private fun heart(c: PixelCanvas, x: Int, y: Int, sz: Int, alpha: Float) {
        for ((row, line) in HEART.withIndex()) for ((col, ch) in line.withIndex()) {
            val tone = when (ch) { 'X' -> HEART_M; 'h' -> HEART_L; 'd' -> HEART_D; else -> continue }
            for (yy in 0 until sz) for (xx in 0 until sz) c.blend(x + col * sz + xx, y + row * sz + yy, tone, alpha)
        }
    }

    /**
     * Little hearts rising from ([x], [y]) over [age] s: [n] of them, one every [every] s, each swaying up [rise] px
     * and fading over [life] s; a heart's pixel is [sz] picture px.
     */
    fun hearts(c: PixelCanvas, x: Float, y: Float, age: Float, n: Int, sz: Int, seed: Int, rise: Float = 12f * sz, every: Float = 0.6f, life: Float = 1.5f) {
        // they keep their pink at night
        val was = c.penEmissive
        c.penEmissive = true
        for (j in 0 until n) {
            val a = age - j * every
            if (a <= 0f || a >= life) continue
            val v = a / life
            val hx = x + (Noise.rnd(j, seed) - 0.5f) * 6f * sz + sin(a * 4.5f + j * 2f) * 1.5f * sz - 2.5f * sz
            val hy = y - v * rise - 5f * sz
            heart(c, floor(hx).toInt(), floor(hy).toInt(), sz, if (v < 0.65f) 1f else (1f - v) / 0.35f)
        }
        c.penEmissive = was
    }

    /**
     * A puff of dust or steam from ([x], [y]), [u] of the way through: [n] soft blobs bursting out to [r] px and rising
     * a little as they thin away; [alpha] how thick it is at first.
     */
    fun puff(c: PixelCanvas, x: Float, y: Float, u: Float, r: Float, n: Int, col: Int, seed: Int, alpha: Float = 0.8f) {
        if (u <= 0f || u >= 1f) return
        val e = 1f - (1f - u) * (1f - u)
        for (j in 0 until n) {
            val ang = Noise.rnd(j, seed) * 2f * PI.toFloat()
            val far = 0.35f + 0.65f * Noise.rnd(j, seed, 1)
            val px = x + cos(ang) * r * far * e
            val py = y + sin(ang) * r * far * e * 0.55f - r * 0.45f * u
            val rr = r * (0.22f + 0.33f * e) * (0.7f + 0.6f * Noise.rnd(j, seed, 2))
            c.ditherCircle(px, py, max(1f, rr), col, alpha * (1f - u))
        }
    }

    /**
     * The rings of a sound (a bell's, the anvil's): [n] arcs on either side of ([x], [y]) spreading out to [r] px as [u]
     * goes from 0 to 1, fading; [th] px thick, as strong as [a] at first.
     */
    fun rings(c: PixelCanvas, x: Float, y: Float, u: Float, r: Float, col: Int, th: Int, a: Float, n: Int = 2) {
        if (u <= 0f || u >= 1f) return
        val was = c.penEmissive
        c.penEmissive = true
        for (i in 0 until n) {
            val v = u * 1.4f - i * 0.4f / max(1, n - 1)
            if (v <= 0f || v >= 1f) continue
            val rr = r * (0.25f + 0.75f * v)
            val steps = max(6, (rr * 1.6f).toInt())
            for (side in intArrayOf(-1, 1)) for (q in 0..steps) {
                val ang = (q.toFloat() / steps - 0.5f) * 1.1f
                val px = floor(x + side * cos(ang) * rr).toInt(); val py = floor(y + sin(ang) * rr).toInt()
                for (o in 0 until th) c.blend(px + side * o, py, col, a * (1f - v))
            }
        }
        c.penEmissive = was
    }

    /**
     * A small bird on the wing seen from afar, its middle at ([x], [y]): a body and two wings up or down ([up]), each of
     * its pixels [sz] picture px (the field's crows, the square's pigeons in flight); set, not blended: it has an id.
     */
    fun wings(c: PixelCanvas, x: Int, y: Int, up: Boolean, sz: Int, col: Int, tip: Int = col) {
        fun px(dx: Int, dy: Int, cc: Int) = c.fillRect(x + dx * sz, y + dy * sz, sz, sz, cc)
        px(-1, 0, col); px(0, 0, col); px(1, 0, col)
        if (up) { px(-2, -1, col); px(-3, -2, tip); px(2, -1, col); px(3, -2, tip) }
        else { px(-2, 1, col); px(-3, 1, tip); px(2, 1, col); px(3, 1, tip) }
    }

    /** A 0..1 step from [a] to [b], slow at both ends. */
    fun ease(v: Float, a: Float = 0f, b: Float = 1f): Float {
        val x = ((v - a) / (b - a)).coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private val HEART = arrayOf(".X.X.", "XhXXX", "XXXXd", ".XXd.", "..d..")
    private val HEART_M = Col.hex(0xF0587A)
    private val HEART_L = Col.hex(0xFFC4D2)
    private val HEART_D = Col.hex(0xC03458)
}
