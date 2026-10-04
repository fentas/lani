package si.lanisce.lani.game.render

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Pines, broadleaf trees, the big linden canopy, bushes and rocks. Sizes scale with the lens's detail; the
 * extra pixels of a closer look go into needle rows, bark, leaf highlights and cracks.
 */
internal class TreePainter(private val s: SceneCtx) {
    private val c get() = s.canvas
    private val k get() = s.k

    fun pine(bx: Int, by: Int, size: Float, seed: Int) {
        val env = s.env
        val k = this.k
        val h = (13 + (seed ushr 3) % 5) * size * k
        val w = h * 0.62f
        val p = env.pine
        val needle = Col.scale(p[1], 0.86f)
        c.fillRect(bx - k, by - 3 * k, 2 * k, 3 * k, Pal.LOG_D)
        c.block(bx - k, by - 3 * k, k, Pal.LOG_M)
        val f = h - 2 * k
        for (j in 0..2) {
            val bottom = by - 2 * k - j * f * 0.28f
            val th = f * 0.46f
            val hw = w / 2 * (1 - j * 0.24f)
            val top = bottom - th
            var y = floor(top).toInt()
            while (y <= bottom.toInt()) {
                val r = (y - top) / th
                val half = hw * r + (if ((y / k + j) % 3 == 0) -0.6f * k else 0f)
                val x0 = (bx - half - 0.5f).toInt(); val x1 = (bx + half - 0.5f).toInt()
                for (x in x0..x1) {
                    val rel = (x + 0.5f - bx) / max(half, 1f)
                    var col = when {
                        rel < -0.25f -> p[0]
                        rel < 0.4f -> p[1]
                        else -> p[2]
                    }
                    if (y == bottom.toInt()) col = p[2]
                    if (env.snow && (y - top < 1.6f * k || (rel < -0.1f && Noise.rnd(x, y, seed) < 0.35f))) col = if (rel < 0.3f) Pal.SNOW_L else Pal.SNOW_D
                    else if (rel < 0f && Noise.rnd(x, y, seed) < 0.12f) col = p[3]
                    else if (k >= 2 && (x + y) % (k + 1) == 0 && Noise.rnd(x, y, seed + 1) < 0.4f) col = needle // needle rows
                    c.set(x, y, col)
                }
                y++
            }
        }
    }

    fun broadleaf(bx: Int, by: Int, size: Float, seed: Int) {
        val trunkH = (4 * size).toInt() * k + k
        c.fillRect(bx - k, by - trunkH, 2 * k, trunkH, Pal.LOG_M)
        c.fillRect(bx - k, by - trunkH, k, trunkH, Pal.LOG_L)
        c.block(bx, by - k, k, Pal.LOG_D)
        if (k >= 2) c.vline(bx, by - trunkH + k, by - k - 1, Pal.LOG_D) // bark
        canopy(bx.toFloat(), by - trunkH - 4f * size * k, size * k, s.env.season, seed, big = false)
    }

    /** Round leafy canopy, shaded from the upper left; seasonal. [scale] includes the lens's detail. */
    fun canopy(cx: Float, cy: Float, scale: Float, season: Season, seed: Int, big: Boolean) {
        val blobs = if (big) BIG else SMALL
        val cols = foliage(season, seed, big)
        if (cols == null) { bare(cx, cy, scale, seed, big); return }
        var minX = 1e9f; var maxX = -1e9f; var minY = 1e9f; var maxY = -1e9f
        for (b in blobs) {
            minX = min(minX, cx + (b[0] - b[2]) * scale); maxX = max(maxX, cx + (b[0] + b[2]) * scale)
            minY = min(minY, cy + (b[1] - b[2]) * scale); maxY = max(maxY, cy + (b[1] + b[2]) * scale)
        }
        val rr = (maxX - minX) / 2
        val edge = 0.5f + k
        val highlight = Col.mix(cols[0], Col.hex(0xFFFFFF), 0.25f)
        for (y in floor(minY).toInt()..maxY.toInt()) for (x in floor(minX).toInt()..maxX.toInt()) {
            if (!inside(blobs, cx, cy, scale, x + 0.5f, y + 0.5f)) continue
            val dx = x + 0.5f - cx; val dy = y + 0.5f - cy
            val n = Noise.rnd(x shr 1, y shr 1, seed) - 0.5f
            var v = (-dx * 0.55f - dy * 0.85f) / rr + n * 0.55f
            if (!inside(blobs, cx, cy, scale, x + edge, y + edge)) v -= 0.5f
            var col = when {
                v > 0.5f -> cols[0]
                v > 0.02f -> cols[1]
                v > -0.45f -> cols[2]
                else -> cols[3]
            }
            if (season == Season.SPRING && Noise.rnd(x, y, seed + 5) < 0.1f) col = if ((x + y) % 3 == 0) Col.hex(0xFFFFFF) else Col.hex(0xF4B8CC)
            if (big && season == Season.SUMMER && s.env.month == 6 && Noise.rnd(x, y, seed + 6) < 0.08f) col = Col.hex(0xF4E48A)
            // a closer look: single leaves catching the light, and leaf edges in the shade
            if (k >= 2) {
                if (v > 0.25f && Noise.rnd(x, y, seed + 7) < 0.05f) col = highlight
                else if (v < 0f && (x + y * 2) % 5 == 0 && Noise.rnd(x, y, seed + 8) < 0.3f) col = cols[3]
            }
            c.set(x, y, col)
        }
    }

    private fun inside(blobs: Array<FloatArray>, cx: Float, cy: Float, sc: Float, x: Float, y: Float): Boolean {
        for (b in blobs) {
            val dx = x - (cx + b[0] * sc); val dy = y - (cy + b[1] * sc); val r = b[2] * sc
            if (dx * dx + dy * dy <= r * r) return true
        }
        return false
    }

    private fun bare(cx: Float, cy: Float, scale: Float, seed: Int, big: Boolean) {
        val bx = cx.toInt(); val base = (cy + 4 * scale).toInt()
        val branch = Col.hex(0x6A5244); val twig = Col.hex(0x85705F)
        val n = if (big) 7 else 4
        c.vline(bx, (cy - 3 * scale).toInt(), base, branch)
        if (k >= 2) c.vline(bx - 1, (cy - scale).toInt(), base, Col.scale(branch, 0.8f))
        for (j in 0 until n) {
            val a = -PI / 2 + (j - (n - 1) / 2.0) * (if (big) 0.42 else 0.6) + (Noise.rnd(seed, j) - 0.5) * 0.3
            val len = (if (big) 11f else 5.5f) * scale * (0.75f + Noise.rnd(seed, j, 2) * 0.35f)
            val sy = base - (j % 3) * 2 * scale
            val ex = bx + (cos(a) * len * 1.2f).toInt(); val ey = (sy + sin(a) * len).toInt()
            c.line(bx, sy.toInt(), ex, ey, branch)
            c.line(ex, ey, ex + (if (j % 2 == 0) 2 else -2) * k, ey - 2 * k, twig)
            if (s.env.snow) { c.block(ex, ey - k, k, Pal.SNOW_L); c.block((bx + ex) / 2, (sy.toInt() + ey) / 2 - k, k, Pal.SNOW_L) }
        }
    }

    private fun foliage(season: Season, seed: Int, big: Boolean): IntArray? = when (season) {
        Season.SPRING -> SPRING
        Season.SUMMER -> SUMMER
        Season.WINTER -> null
        Season.AUTUMN -> {
            val m = s.env.month
            if (m == 11 && !big && (seed ushr 4) % 3 != 0) null
            else if (big) YELLOW
            else {
                val r = ((seed ushr 5) % 100)
                when {
                    r < 34 -> ORANGE
                    r < 58 -> RED
                    r < 84 -> YELLOW
                    else -> if (m == 9) TURNING else ORANGE
                }
            }
        }
    }

    fun bush(bx: Int, by: Int, seed: Int) {
        val cols = foliage(s.env.season, seed + 3, false) ?: intArrayOf(Col.hex(0x7A6A5A), Col.hex(0x6A5A4A), Col.hex(0x5A4A3A), Col.hex(0x4A3A2A))
        val k = this.k
        for (y in -4 * k..0) for (x in -3 * k..3 * k) {
            val xf = x.toFloat() / k; val yf = y.toFloat() / k
            val d = (xf * xf) / 12f + ((yf + 2) * (yf + 2)) / 6f
            if (d > 1f) continue
            val v = -xf * 0.3f - (yf + 2) * 0.4f + (Noise.rnd(bx + x, by + y, seed) - 0.5f) * 0.8f
            c.set(bx + x, by + y, if (v > 0.6f) cols[0] else if (v > -0.2f) cols[1] else cols[2])
        }
        if (s.env.snow) { c.fillRect(bx - 2 * k, by - 4 * k, 4 * k, k, Pal.SNOW_L); c.block(bx - k, by - 5 * k, k, Pal.SNOW_L) }
    }

    fun rock(bx: Int, by: Int, seed: Int) {
        val w = 2 + (seed ushr 6) % 2
        val k = this.k
        c.fillRect(bx - w * k, by - 2 * k, w * 2 * k, 2 * k, Pal.STONE_M)
        c.fillRect(bx - (w - 1) * k, by - 3 * k, (2 * w - 2) * k, k, Pal.STONE_L)
        c.fillRect(bx, by - k, w * k, k, Pal.STONE_D)
        if (k >= 2) {
            c.line(bx - k, by - 2 * k, bx + k - 1, by - 1, Pal.STONE_D) // a crack
            c.set(bx - (w - 1) * k, by - 3 * k, Col.mix(Pal.STONE_L, Col.hex(0xFFFFFF), 0.3f))
        }
        if (s.env.snow) c.fillRect(bx - (w - 1) * k, by - 3 * k, (2 * w - 2) * k, k, Pal.SNOW_L)
    }

    companion object {
        private val SMALL = arrayOf(
            floatArrayOf(0f, 0f, 4.2f), floatArrayOf(-3f, 1.5f, 3f), floatArrayOf(3f, 1.2f, 3.2f), floatArrayOf(-1f, -2.8f, 3f),
        )
        private val BIG = arrayOf(
            floatArrayOf(0f, 0f, 8f), floatArrayOf(-6.5f, 2.5f, 5.5f), floatArrayOf(6.5f, 2f, 5.8f),
            floatArrayOf(-3.5f, -5f, 5.5f), floatArrayOf(4f, -4.5f, 5.2f), floatArrayOf(0f, 4.5f, 5.5f),
        )
        private fun p(vararg v: Long) = IntArray(v.size) { Col.hex(v[it]) }
        private val SPRING = p(0xB2E07A, 0x8CCB58, 0x6AAE44, 0x4A8634)
        private val SUMMER = p(0x86C659, 0x5FA43F, 0x44842F, 0x2F6325)
        private val ORANGE = p(0xFFC468, 0xF08C2C, 0xC9651F, 0x8C4418)
        private val RED = p(0xF47E5A, 0xD6452C, 0xA23022, 0x6E2319)
        private val YELLOW = p(0xFBE67A, 0xECC23E, 0xC49B2A, 0x8A6C1E)
        private val TURNING = p(0xC8CC5A, 0x9EAA42, 0x768434, 0x516026)
    }
}

/**
 * Sky, sun/moon/stars, clouds and the Julian Alps with Triglav, and (a village by the sea, see [draw]) a band of the sea
 * along the horizon. Drawn unlit (baked with ambient light). Laid out on the nominal canvas and mapped through the lens,
 * so a closer look shows the same sky, finer.
 */
internal class Backdrop(private val s: SceneCtx) {
    private val c get() = s.canvas
    private val lens get() = s.lens
    private val k get() = s.k

    /**
     * [shift] slides the mountain range sideways (fraction of the width), e.g. to frame Triglav in a compact view; [sea]:
     * the sea shows along the horizon on the left, where the hills open to the coast (the culture pack's backdrop).
     * [skyline]: the village's land's horizon (see [Terrain.skyline]): the Alps lower behind the hills, higher over the
     * mountains, the sea wider along a coast.
     */
    fun draw(horizon: Int, storm: Boolean, shift: Float = 0f, sea: Boolean = false, skyline: Skyline = Skyline.CLASSIC) {
        val env = s.env
        val w = c.width
        c.penEmissive = true
        c.penId = 0
        // sky: banded gradient with ordered dithering between bands, by nominal row
        val bands = 9f
        val hb = lens.by(horizon.toFloat()) + 2f
        for (y in 0 until min(horizon + 2 * k, c.height)) {
            val t = (lens.by(y.toFloat()) / hb).coerceIn(0f, 1f)
            for (x in 0 until w) {
                val q = floor(t * bands + Dither.at(x, y) - 0.5f) / bands
                c.set(x, y, skyAt(q.coerceIn(0f, 1f)))
            }
        }
        val real = s.skyNow
        val view = if (real != null) view(horizon) else null
        if (real != null && view != null) s.night?.draw(c, real, view, lens.baseW, lens.by(horizon.toFloat()), k, lens.x0, lens.y0, env.dark, s.clear, s.time, s.wall, s.skyMark)
        else if (env.dark > 0.3f && !storm) stars(horizon)
        if (!storm) sunAndMoon(horizon, view)
        clouds(horizon, storm)
        mountains(horizon, shift, skyline)
        if (sea || skyline.sea) sea(horizon, skyline.seaReach)
        c.penEmissive = false
    }

    /**
     * The sea far off, a thin band at the horizon where the near hills open to the coast on the left (as far across as
     * [reach] of the width): blue under the sky, a pale line where it meets it, glints drifting on it by day and the
     * moon's by night; the treeline hides its foot.
     */
    private fun sea(horizon: Int, reach: Float = 0.46f) {
        val env = s.env
        val sky = env.skyHorizon
        val body = env.lit(Col.mix(Col.hex(0x3F84C4), sky, 0.38f))
        val deep = env.lit(Col.mix(Col.hex(0x2B5F96), sky, 0.3f))
        val line = Col.mix(sky, Col.hex(0xFFFFFF), 0.35f)
        val bw = lens.baseW.toFloat()
        for (x in 0 until c.width) {
            // how much of the band this column has: all of it on the left, tapering off toward the middle
            val u = ux(x) / bw
            val m = ((reach - u) / 0.12f).coerceIn(0f, 1f)
            if (m <= 0f) continue
            // just over the treeline (its tips stand in front of it)
            val rows = (2f + 2.5f * m) * k
            val foot = horizon - 6 * k
            val top = (foot - rows).toInt()
            for (y in max(top, 0) until min(foot, c.height)) {
                val down = (y - top) / rows
                var col = if (y < top + k) line else if (down > 0.6f + (Dither.at(x, y) - 0.5f) * 0.3f) deep else body
                // glints: short bright dashes drifting slowly along the water
                val g = Noise.rnd(floor(lens.bx(x + 0.5f) / 3f + s.time * 0.4).toInt(), (y - top) / k, 13)
                if (y >= top + k && g > 0.93f) col = if (env.dark > 0.5f) Col.mix(body, Col.hex(0xD8D2B0), 0.4f) else Col.mix(line, Col.hex(0xFFFFFF), 0.5f)
                c.set(x, y, col)
            }
        }
    }

    /**
     * How the village shows the real sky: looking south (the sun rises on the left and sets on the right), the horizon
     * on its row, the zenith at the top of a tall sky or higher (a third of the width for the lens's radius).
     */
    private fun view(horizon: Int): SkyView {
        val hb = lens.by(horizon.toFloat())
        return SkyView(180.0, lens.baseW / 2f, hb, max(lens.baseW / 3f, hb / 2f))
    }

    fun skyAt(t: Float): Int {
        val env = s.env
        return if (t < 0.55f) Col.mix(env.skyTop, env.skyMid, t / 0.55f) else Col.mix(env.skyMid, env.skyHorizon, (t - 0.55f) / 0.45f)
    }

    private fun stars(horizon: Int) {
        val a = ((s.env.dark - 0.3f) / 0.5f).coerceIn(0f, 1f)
        val hb = lens.by(horizon.toFloat())
        for (j in 0 until 70) {
            val x = lens.cx((Noise.rnd(j, 1) * lens.baseW).toInt().toFloat()).toInt(); val y = lens.cy((Noise.rnd(j, 2) * hb * 0.85f).toInt().toFloat()).toInt()
            val tw = 0.6f + 0.4f * sin(s.time * (1.5 + Noise.rnd(j, 3) * 2) + j).toFloat()
            val b = a * tw * (0.5f + Noise.rnd(j, 4) * 0.5f)
            s.sprite(x, y) {
                c.blend(x, y, Col.hex(0xFFF8E0), b)
                if (j % 11 == 0 && b > 0.6f) {
                    c.blend(x - 1, y, Col.hex(0xC8D0F0), b * 0.5f); c.blend(x + 1, y, Col.hex(0xC8D0F0), b * 0.5f)
                    c.blend(x, y - 1, Col.hex(0xC8D0F0), b * 0.5f); c.blend(x, y + 1, Col.hex(0xC8D0F0), b * 0.5f)
                }
            }
        }
    }

    private fun sunAndMoon(horizon: Int, view: SkyView?) {
        val env = s.env
        val hb = lens.by(horizon.toFloat())
        if (env.sun > -0.12f) {
            val span = (s.hour - env.sunrise) / (env.sunset - env.sunrise)
            val x = lens.cx(lens.baseW * (0.12f + 0.76f * span.coerceIn(0f, 1f)))
            val y = lens.cy(max(7f, hb - 4 - env.sun * hb * 0.95f)) // never cut off by the top edge
            val low = (1 - env.sun * 2.5f).coerceIn(0f, 1f)
            val core = Col.mix(Col.hex(0xFFF6D8), Col.hex(0xFFB04A), low)
            val ax = x.toInt(); val ay = y.toInt()
            s.sprite(ax, ay) {
                c.ditherCircle(x, y, 8f, Col.mix(core, env.skyHorizon, 0.4f), 0.28f)
                c.fillCircle(x, y, 4.5f, core)
                c.fillCircle(x - 1, y - 1, 2.5f, Col.mix(core, Col.hex(0xFFFFFF), 0.5f))
            }
        }
        // the moon as the date has it (see [Moon]), while it is up: from the left to the right like the sun, lower as it
        // rises and sets (behind the mountains); lit on the right while it waxes, on the left while it wanes. The real
        // moon stands where it is in the southern sky, kept in the picture when it is far east or west.
        val real = s.skyNow
        val at = if (real != null && view != null) s.night?.moonAt(real, view) else null
        if (env.dark > 0.25f && env.moonShows && (real == null || at != null)) {
            val nx = at?.get(0)?.coerceIn(lens.baseW * 0.06f, lens.baseW * 0.94f) ?: (lens.baseW * (0.12f + 0.76f * env.moonArc))
            val ny = at?.get(1)?.coerceIn(7f, hb - 3f) ?: (hb - 6f - env.moonHeight * (hb * 0.7f - 6f))
            val x = lens.cx(nx); val y = lens.cy(ny)
            val a = ((env.dark - 0.25f) / 0.4f).coerceIn(0f, 1f)
            val ax = x.toInt(); val ay = y.toInt()
            val earth = ((0.5f - env.moonLit) / 0.45f).coerceIn(0f, 1f) * 0.3f * a
            s.sprite(ax, ay) {
                c.ditherCircle(x, y, 9f, Col.hex(0x6A78A8), 0.3f * a * (0.4f + 0.6f * env.moonLit))
                for (yy in -5..5) for (xx in -5..5) {
                    if (xx * xx + yy * yy > 22) continue
                    val u = Moon.light(xx / 4.7f, yy / 4.7f, env.moonAge)
                    val px = (x + xx).toInt(); val py = (y + yy).toInt()
                    if (u > 0.3f) c.blend(px, py, Col.hex(0xF6F0D0), a)
                    else if (u > 0f) c.blend(px, py, Col.hex(0xD8D2B0), a)
                    else if (earth > 0f) c.blend(px, py, Col.hex(0x5A6488), earth)
                }
            }
            // the real moon can be tapped for its card
            if (real != null) s.night?.moonDrawn(nx, ny, 5.5f)
        }
    }

    private fun clouds(horizon: Int, storm: Boolean) {
        val env = s.env
        val n = if (storm) 16 else 4
        val hb = lens.by(horizon.toFloat())
        val lightC = when {
            storm -> Col.scale(Col.hex(0x6A7080), 1f - env.dark * 0.6f)
            env.dark > 0.6f -> Col.hex(0x2E3558)
            env.sun < 0.2f -> Col.mix(Col.hex(0xFFE6C8), env.skyHorizon, 0.35f)
            else -> Col.hex(0xFAFCFF)
        }
        val shadeC = when {
            storm -> Col.scale(Col.hex(0x454A58), 1f - env.dark * 0.6f)
            env.dark > 0.6f -> Col.hex(0x1E2442)
            env.sun < 0.2f -> Col.mix(Col.hex(0xB07A8A), env.skyMid, 0.3f)
            else -> Col.hex(0xD2DEEE)
        }
        for (j in 0 until n) {
            val speed = 0.8 + Noise.rnd(j, 7) * 0.8
            val span = lens.baseW + 60
            val x = lens.cx((((Noise.rnd(j, 8) * span + s.time * speed) % span) - 30).toFloat())
            val y = lens.cy(if (storm) 2f + Noise.rnd(j, 9) * hb * 0.5f else 4f + Noise.rnd(j, 9) * hb * 0.45f)
            val sz = (if (storm) 1.4f else 1f) * (0.8f + Noise.rnd(j, 10) * 0.6f)
            val ax = x.toInt(); val ay = y.toInt()
            s.sprite(ax, ay) {
                for (b in 0..3) {
                    val bx = x + (b - 1.5f) * 5 * sz; val by = y - (if (b == 1 || b == 2) 2.5f else 0f) * sz
                    c.fillEllipse(bx, by, 5f * sz, 3f * sz, shadeC)
                }
                for (b in 0..3) {
                    val bx = x + (b - 1.5f) * 5 * sz - 0.8f; val by = y - (if (b == 1 || b == 2) 2.5f else 0f) * sz - 1.2f
                    c.fillEllipse(bx, by, 4.2f * sz, 2.4f * sz, lightC)
                }
            }
        }
    }

    private fun snowFrac(m: Int) = when (m) {
        12, 1, 2 -> 0.3f; 3 -> 0.36f; 4 -> 0.42f; 5 -> 0.55f; 6 -> 0.68f; 7, 8 -> 0.8f; 9 -> 0.74f; 10 -> 0.6f; else -> 0.38f
    }

    /** The nominal x of canvas column [x]: the column itself at k = 1, else its centre (so slopes get finer, not stepped). */
    private fun ux(x: Int): Float = if (k == 1) x.toFloat() else lens.bx(x + 0.5f)

    private fun mountains(horizon: Int, shift: Float, skyline: Skyline) {
        val env = s.env
        val w = c.width
        val hb = lens.by(horizon.toFloat())
        // Capped, so a tall canvas gets more sky instead of needle-thin peaks.
        val mh0 = min(hb - 3f, MAX_MOUNTAIN_PX) * k
        // the land's skyline: the Alps lower behind the hills, higher over the mountains (never above the canvas)
        val mh = if (skyline.alps == 1f) mh0 else min(mh0 * skyline.alps, (hb - 3f) * k)
        val sky = env.skyHorizon
        val snowF = snowFrac(env.month)
        val bw = lens.baseW.toFloat()
        // far range
        val farRock = env.lit(Col.mix(Col.hex(0x8C9CBA), sky, 0.5f))
        val farSnow = env.lit(Col.mix(Col.hex(0xEEF2FA), sky, 0.45f))
        for (x in 0 until w) {
            val u = ux(x) / bw - shift
            val top = horizon - mh * (0.42f + 0.2f * Noise.v1(u * 7f, 3) + 0.1f * Noise.v1(u * 19f, 4))
            for (y in max(top.toInt(), 0) until min(horizon, c.height)) {
                val hgt = (horizon - y) / mh
                c.set(x, y, if (hgt > snowF + 0.06f) farSnow else farRock)
            }
        }
        // main range with Triglav's three heads
        val peaks = PEAKS
        val rockL = env.lit(Col.mix(Col.hex(0xA3A7B4), sky, 0.22f)); val rockD = env.lit(Col.mix(Col.hex(0x6C7084), sky, 0.22f))
        val snowL = env.lit(Col.mix(Col.hex(0xF7F9FF), sky, 0.12f)); val snowD = env.lit(Col.mix(Col.hex(0xBCC6DC), sky, 0.18f))
        val gully = env.lit(Col.mix(Col.hex(0x575B6E), sky, 0.2f))
        for (x in 0 until w) {
            val xu = ux(x)
            val u = xu / bw - shift
            val uw = u - floor(u) // the range wraps around when shifted
            var best = 0f; var bp = 0
            for ((j, p) in peaks.withIndex()) {
                var dd = abs(uw - p[0]); if (dd > 0.5f) dd = 1f - dd
                val d = dd / p[2]
                if (d >= 1f) continue
                val hh = p[1] * (1 - d).pow(1.25f)
                if (hh > best) { best = hh; bp = j }
            }
            best += (Noise.v1(u * 38f, 9) - 0.5f) * 0.07f + (Noise.v1(u * 95f, 19) - 0.5f) * 0.03f
            val top = horizon - mh * best
            var pu = peaks[bp][0] + shift; pu -= floor(pu)
            val px = lens.cx(pu * bw)
            val peakTop = horizon - mh * peaks[bp][1]
            for (y in max(top.toInt(), 0) until min(horizon, c.height)) {
                val hgt = (horizon - y) / mh
                val litSide = x < px + (y - peakTop) * 0.45f
                // noise in units of the range's height, so the texture looks the same at every canvas size
                val snowy = hgt > snowF + (Noise.v2(xu * 0.25f, hgt * 5.5f, 5) - 0.5f) * 0.22f
                var col = if (snowy) (if (litSide) snowL else snowD) else (if (litSide) rockL else rockD)
                if (!snowy && Noise.v2(xu * 0.45f + hgt * 5f, hgt * 9f, 7) > 0.78f) col = gully // short, slanted rock gullies
                c.set(x, y, col)
            }
        }
        foothills(horizon, if (skyline.hills == 1f) mh else min(mh0 * skyline.hills, (hb - 3f) * k), shift)
    }

    /**
     * Two layers of rolling foothills between the Alps and the forest: a few broad shapes in flat colour,
     * the far layer paler (closer to the sky), with a band of haze where each layer meets the one behind it.
     */
    private fun foothills(horizon: Int, mh: Float, shift: Float) {
        val env = s.env
        val sky = env.skyHorizon
        val hill = when (env.season) {
            Season.WINTER -> Col.hex(0x9FB2C4)
            Season.AUTUMN -> Col.mix(Col.hex(0x4A6A4A), Col.hex(0x8A6A36), 0.3f)
            else -> Col.hex(0x3E6A56)
        }
        val farBody = env.lit(Col.mix(hill, sky, 0.55f))
        val farRidge = env.lit(Col.mix(Col.scale(hill, 1.15f), sky, 0.47f))
        val nearBody = env.lit(Col.mix(hill, sky, 0.34f))
        val nearRidge = env.lit(Col.mix(Col.scale(hill, 1.15f), sky, 0.28f))
        val haze = env.lit(sky)
        val bw = lens.baseW.toFloat()
        for (x in 0 until c.width) {
            val u = ux(x) / bw - shift
            val farTop = (horizon - mh * (0.19f + 0.12f * Noise.v1(u * 3.1f + 3f, 31))).toInt()
            val nearTop = (horizon - mh * (0.12f + 0.09f * Noise.v1(u * 4.3f + 1f, 11))).toInt()
            // haze at the foot of the mountains, then the far hills (hazy where the near hills rise in front)
            for (y in farTop - 3 * k until farTop) c.blend(x, y, haze, if (y >= farTop - k) 0.45f else 0.25f)
            for (y in max(farTop, 0) until min(nearTop, c.height)) {
                val low = nearTop - y
                c.set(x, y, when {
                    y < farTop + k -> farRidge
                    low <= k -> Col.mix(farBody, haze, 0.35f)
                    low <= 2 * k && (x / k and 1) == 0 -> Col.mix(farBody, haze, 0.35f)
                    else -> farBody
                })
            }
            for (y in max(nearTop, 0)..min(horizon, c.height - 1)) c.set(x, y, if (y < nearTop + k) nearRidge else nearBody)
        }
    }

    /** Dark band of distant forest where the ground meets the hills, from nominal x [from] on (a coast's sea comes up to the horizon left of it). */
    fun treeline(horizon: Int, from: Float = -1e9f) {
        val env = s.env
        c.penEmissive = true
        val base = if (env.snow) Col.hex(0x6E8494) else Col.hex(0x2B4A3A)
        val col = env.lit(Col.mix(base, env.skyHorizon, 0.18f))
        val colL = env.lit(Col.mix(Col.scale(base, 1.25f), env.skyHorizon, 0.2f))
        for (x in 0 until c.width) {
            val bx = floor(lens.bx(x + 0.5f)).toInt()
            if (bx < from) continue
            val tip = abs(((bx + (Noise.rnd(bx / 5, 1) * 3).toInt()) % 5) - 2).toFloat()
            val top = horizon - 2 * k - (3 - tip) * k - Noise.v1(bx * 0.15f, 21) * 3 * k
            for (y in max(top.toInt(), 0)..min(horizon + 3 * k, c.height - 1)) c.set(x, y, if (y < top + 2 * k && tip < 1.5f) colL else col)
        }
        c.penEmissive = false
    }

    companion object {
        /** Tallest the Alps get, in nominal pixels (the 240×160 view has 45). */
        const val MAX_MOUNTAIN_PX = 52f

        /** (u, height fraction, half width). Triglav: the tall middle head flanked by two lower ones. */
        val PEAKS = arrayOf(
            floatArrayOf(0.03f, 0.5f, 0.09f), floatArrayOf(0.13f, 0.6f, 0.08f), floatArrayOf(0.22f, 0.46f, 0.07f),
            floatArrayOf(0.33f, 0.56f, 0.09f), floatArrayOf(0.45f, 0.44f, 0.08f), floatArrayOf(0.54f, 0.5f, 0.06f),
            // Triglav: Mali Triglav shoulder, the broad main summit, and the eastern ridge
            floatArrayOf(0.648f, 0.74f, 0.06f), floatArrayOf(0.712f, 0.98f, 0.095f), floatArrayOf(0.777f, 0.76f, 0.06f),
            floatArrayOf(0.88f, 0.52f, 0.08f), floatArrayOf(0.97f, 0.6f, 0.09f),
        )
    }

}
