package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Env
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.Lightning
import si.lanisce.lani.game.scene.Sky
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The weather over a scene's picture ([w] × [h] at [detail], see [ArtPainter]): streaks of rain, snowflakes, bands
 * of fog drifting by, a flash of lightning far off. Drawn over the lit picture (the colours are lit by [env] here).
 *
 * Everything is placed on the scene's canvas (detail 1), scaled by the detail and moved by the time alone, so a
 * window of the picture holds just what the whole picture has there, and every detail shows the same weather,
 * finer. [open] says where it falls: an object id → whether that is out in the open (null: everywhere); [cover] where a
 * roof keeps it off, whatever is drawn there: a pixel of the picture → whether it is under one (null: nowhere).
 */
internal class Weather(
    private val c: PixelCanvas,
    private val w: Int,
    private val h: Int,
    private val detail: Int,
    private val t: Double,
    private val env: Env,
    private val sky: Sky,
    private val cover: Cover? = null,
    private val open: Open? = null,
) {
    fun interface Open { fun at(id: Int): Boolean }

    /** Whether pixel ([x], [y]) of the picture is under a roof: no drop, flake or mist lands on it. */
    fun interface Cover { fun at(x: Int, y: Int): Boolean }

    /** A share of the picture's rows (on the scene canvas) → how thick the fog is there, 0..1. */
    fun interface Profile { fun at(row: Float): Float }

    private val bw = w / detail.toFloat()
    private val bh = h / detail.toFloat()

    private fun free(x: Int, y: Int): Boolean {
        if (!c.inside(x, y)) return false
        if (open != null && !open.at(c.ids[c.index(x, y)])) return false
        return cover == null || !cover.at(x, y)
    }

    /** A daylight colour in this frame's light, a little brighter than the land: weather catches what light there is. */
    private fun lit(col: Int): Int = Col.mix(env.lit(col), col, 0.25f)

    /** Rain: streaks as many and as long as it pours, slanting with the wind, fading toward their tails. */
    fun rain(level: Float = sky.rain) {
        if (level <= 0.01f) return
        val d = detail
        val n = (bw * bh / 110f * level).toInt()
        val slant = 0.2f + 0.6f * sky.wind
        val len = ((3f + 4f * level) * d).toInt()
        val col = lit(RAIN)
        val a = 0.4f + 0.3f * level
        val span = bh + 24f
        val reach = slant * span
        for (j in 0 until n) {
            val sp = 130f + Noise.rnd(j, 32) * 70f
            val yb = ((Noise.rnd(j, 33) * span + t * sp) % span).toFloat() - 12f
            val xb = Noise.rnd(j, 31) * (bw + reach + 20f) - reach - 10f + yb * slant
            val x = (xb * d).toInt(); val y = (yb * d).toInt()
            if (y < c.top || y - len >= c.bottom || x < c.left - len || x - len >= c.right) continue
            for (q in 0 until len) {
                val px = x - (q * slant).toInt(); val py = y - q
                if (free(px, py)) c.blend(px, py, col, a * (1f - 0.6f * q / len))
            }
        }
    }

    /** Snow: flakes falling slowly, swaying, blown along by the wind. */
    fun snow(level: Float = sky.snow) {
        if (level <= 0.01f) return
        val d = detail
        val n = (bw * bh / 220f * level).toInt()
        val light = lit(SNOW); val shade = lit(SNOW_SHADE)
        val span = bh + 10f
        for (j in 0 until n) {
            val sp = 8f + Noise.rnd(j, 51) * 9f
            val yb = ((Noise.rnd(j, 52) * span + t * sp) % span).toFloat() - 5f
            val sway = sin(t * (0.8 + Noise.rnd(j, 54)) + j).toFloat() * 3f * (1f + sky.wind)
            val xr = Noise.rnd(j, 53) * bw + sway + yb * sky.wind * 0.9f
            val xb = ((xr % bw) + bw) % bw
            val x = floor(xb * d).toInt(); val y = floor(yb * d).toInt()
            if (!free(x, y)) continue
            val big = Noise.rnd(j, 55) < 0.3f
            dab(x, y, d, d, if (big) light else shade)
            if (big && d >= 2) dab(x + d, y, max(1, d / 2), d, shade) // a bigger flake, closer up
        }
    }

    /**
     * A [bw] × [bh] patch of [col] over what is there where it falls ([free]: a flake closer up is hidden behind what
     * stands in front, as the rain is), keeping its ids (what was tapped stays tappable).
     */
    private fun dab(x: Int, y: Int, bw: Int, bh: Int, col: Int) {
        for (yy in y until y + bh) for (xx in x until x + bw) if (free(xx, yy)) c.blend(xx, yy, col, 1f)
    }

    /**
     * Fog: bands of mist drifting slowly across, as thick as [level] times [profile] at each row, dithered in a few
     * steps like the sky.
     */
    fun fog(level: Float, profile: Profile) {
        if (level <= 0.01f) return
        val d = detail
        val col = lit(FOG)
        val band = 16f
        val cols = c.width
        var a = FloatArray(cols); var b = FloatArray(cols)
        var at = Int.MIN_VALUE
        for (y in max(0, c.top) until min(h, c.bottom)) {
            val yb = (y + 0.5f) / d
            val p = profile.at(yb) * level
            if (p <= 0.02f) continue
            val bi = floor(yb / band).toInt()
            if (bi != at) {
                if (bi == at + 1) { val tmp = a; a = b; b = tmp; bandAt(bi + 1, b) } else { bandAt(bi, a); bandAt(bi + 1, b) }
                at = bi
            }
            val f = yb / band - bi
            val s = f * f * (3f - 2f * f)
            for (x in max(0, c.left) until min(w, c.right)) {
                val i = c.index(x, y)
                if (open != null && !open.at(c.ids[i])) continue
                if (cover != null && cover.at(x, y)) continue
                val k = x - c.left
                val v = p * (a[k] + (b[k] - a[k]) * s)
                val q = floor(v * STEPS + Dither.at(x, y) - 0.5f) / STEPS
                if (q <= 0f) continue
                c.pixels[i] = Col.mix(c.pixels[i], col, min(1f, q) * 0.8f)
            }
        }
    }

    /** How thick the fog is along band [bi] at each column of the canvas: patches drifting, every other band the other way. */
    private fun bandAt(bi: Int, out: FloatArray) {
        val drift = t * (if (bi and 1 == 0) 2.2 else -1.5)
        for (x in c.left until c.right) {
            val xb = (x + 0.5f) / detail
            out[x - c.left] = 0.3f + 0.7f * Noise.v1(((xb + drift) * 0.03).toFloat(), 97 + bi)
        }
    }

    /**
     * How bright a flash of lightning is now, 0..1: now and then (when [Lightning.SCENE] says: every 5–13 s) one soft
     * flash ([Lightning.glow]: a quick rise, half a second's fade). The thunder after it is timed from the same flashes
     * (game/ambient/Thunder.kt). None with the system's animations off: the scene's sky comes without lightning then.
     */
    fun flash(): Float = if (!sky.lightning) 0f else Lightning.glow(Lightning.SCENE.since(t))

    /** Whether the bolt of the flash shows now: its first [Lightning.BOLT_S] (none without lightning). */
    fun boltShows(): Boolean = sky.lightning && Lightning.SCENE.since(t) in 0.0..Lightning.BOLT_S

    /** Which flash it is (a new bolt each time). */
    fun bolt(): Int = Lightning.SCENE.index(t)

    /** The whole picture brightened by a flash of [a]; what is out in the open ([open]) the most. */
    fun lighten(a: Float, openShare: Float, restShare: Float) {
        if (a <= 0f) return
        val px = c.pixels; val ids = c.ids
        for (i in px.indices) {
            val share = if (open == null || open.at(ids[i])) openShare else restShare
            if (share > 0f) px[i] = Col.mix(px[i], FLASH, a * share)
        }
    }

    /** A bolt far off from row [top] down to row [bottom] (the picture's), crooked, seeded by [seed]. */
    fun boltLine(top: Int, bottom: Int, seed: Int) {
        val d = detail
        var x = bw * (0.2f + Noise.rnd(seed, 41) * 0.6f)
        var y = top / d.toFloat()
        val end = bottom / d.toFloat()
        var k = 0
        while (y < end) {
            val nx = x + (Noise.rnd(seed, k, 42) - 0.5f) * 7f
            val ny = min(end, y + 3f + Noise.rnd(seed, k, 43) * 4f)
            for (o in 0 until max(1, d - 1)) glowLine((x * d).toInt() + o, (y * d).toInt(), (nx * d).toInt() + o, (ny * d).toInt())
            x = nx; y = ny; k++
        }
    }

    private fun glowLine(xa: Int, ya: Int, xb: Int, yb: Int) {
        var x = xa; var y = ya
        val dx = abs(xb - xa); val dy = -abs(yb - ya)
        val sx = if (xa < xb) 1 else -1; val sy = if (ya < yb) 1 else -1
        var err = dx + dy
        while (true) {
            c.blend(x, y, FLASH, 1f)
            if (x == xb && y == yb) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    companion object {
        /**
         * A window's glass in the weather ([sky]), seen from inside (companion/SCENES.md "The rain indoors"): at ([u], [v])
         * of the glass (0..1 from its lower left; it is [ww] × [wh] scene-canvas pixels), what the window shows there
         * ([col]) greyed under the clouds; in the rain the drops streaking past outside, beads clinging to the pane and now
         * and then a trickle running down it; a flash of lightning brightening it ([Lightning.glow] at [t], none when the
         * sky has no lightning: the system's animations off). Pure per pixel: every painter's window agrees, and closer up
         * the same drops are finer.
         */
        fun glass(col: Int, u: Float, v: Float, ww: Float, wh: Float, t: Double, sky: Sky): Int {
            if (sky.clear) return col
            var out = col
            if (sky.gloom > 0f) out = Col.mix(out, GLASS_GREY, 0.55f * sky.gloom)
            val level = sky.rain
            if (level > 0.02f) {
                val x = u * ww; val y = (1f - v) * wh // px from the glass's top left
                // the rain outside: thin streaks slanting in the wind, one in some columns along the slant
                val slant = 0.2f + 0.5f * sky.wind
                val sx = x - y * slant
                val ci = floor(sx / STREAK_GAP).toInt()
                if (Noise.rnd(ci, 84) < 0.4f + 0.5f * level && abs(sx - (ci + 0.5f) * STREAK_GAP) < 0.5f) {
                    val len = 2.5f + 3.5f * level
                    val period = wh + len + 6f
                    val head = ((Noise.rnd(ci, 86) * period + t * (55.0 + Noise.rnd(ci, 85) * 35.0)) % period).toFloat() - 3f
                    val d = y - (head - len)
                    if (d in 0f..len) out = Col.mix(out, RAIN, 0.75f * (0.35f + 0.65f * d / len))
                }
                // beads on the pane: a drop in some cells of a small grid, light on top, a shade under it
                val gx = floor(x / BEAD_GRID).toInt(); val gy = floor(y / BEAD_GRID).toInt()
                if (Noise.rnd(gx, gy, 87) < 0.45f * level) {
                    val bx = (gx + 0.25f + 0.5f * Noise.rnd(gx, gy, 88)) * BEAD_GRID
                    val by = (gy + 0.25f + 0.5f * Noise.rnd(gx, gy, 89)) * BEAD_GRID
                    val dx = x - bx; val dy = y - by
                    if (dx * dx + dy * dy < 0.36f) out = Col.mix(out, SHINE, if (dy < 0f) 0.5f else 0.3f)
                    else if (dy in 0.5f..1.1f && abs(dx) < 0.6f) out = Col.scale(out, 0.82f)
                }
                // now and then a trickle runs down, a drop at its head, a wet trail behind it
                val tc = floor(x / TRICKLE_GAP).toInt()
                if (Noise.rnd(tc, 90) < 0.4f * level) {
                    val per = 5.0 + Noise.rnd(tc, 91) * 6.0
                    val ph = ((t / per + Noise.rnd(tc, 92)) % 1.0).toFloat()
                    val head = ph * (wh * 1.6f) - 2f
                    val tx = (tc + 0.5f) * TRICKLE_GAP + sin(y * 0.8f + tc).toFloat() * 0.35f
                    val ax = abs(x - tx)
                    if (ax < 0.6f && abs(y - head) < 0.6f) out = Col.mix(out, SHINE, 0.55f)
                    else if (ax < 0.45f && y < head && y > head - wh * 0.6f) out = Col.mix(out, RAIN, 0.3f * (1f - (head - y) / (wh * 0.6f)))
                }
            }
            if (sky.lightning) {
                val f = Lightning.glow(Lightning.SCENE.since(t))
                if (f > 0f) out = Col.mix(out, FLASH, 0.55f * f)
            }
            return out
        }

        /** The grey of a storm's sky in a window. */
        private val GLASS_GREY = Col.hex(0x56606E)
        private val SHINE = Col.hex(0xF2F6FF)
        /** Px between the columns a streak of rain may fall in, seen through a window; a bead's cell; a trickle's column. */
        private const val STREAK_GAP = 1.9f
        private const val BEAD_GRID = 3.2f
        private const val TRICKLE_GAP = 3.7f

        private val RAIN = Col.hex(0xC4D2E6)
        private val SNOW = Col.hex(0xFFFFFF)
        private val SNOW_SHADE = Col.hex(0xDDE7F3)
        private val FOG = Col.hex(0xD6DCE4)
        private val FLASH = Col.hex(0xEEF2FF)
        /** The fog's steps of thickness: dithered between them like the sky's bands, fine enough not to show a pattern. */
        private const val STEPS = 8f
    }
}
