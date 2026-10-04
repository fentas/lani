package si.lanisce.lani.game.render

import si.lanisce.lani.game.scene.Sky
import si.lanisce.lani.game.sky.Astro
import si.lanisce.lani.game.sky.Meteor
import si.lanisce.lani.game.sky.Meteors
import si.lanisce.lani.game.sky.Planet
import si.lanisce.lani.game.sky.SkyNow
import si.lanisce.lani.game.sky.SkyTargets
import si.lanisce.lani.game.sky.Stars
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How a picture shows the sky: looking toward azimuth [facing] (180: the south; from the north through the east), the
 * horizon on nominal row [horizon], the middle of the view in column [cx]. A stereographic projection of radius [r]
 * (nominal px): a point θ from the middle of the view lies 2r·tan(θ/2) from it. The constellations keep their shapes,
 * the horizon stays a straight row, and the zenith lies 2r above it; everything is bigger toward the edges, like a
 * wide lens.
 */
data class SkyView(val facing: Double, val cx: Float, val horizon: Float, val r: Float) {
    private val fs = sin(facing * Astro.DEG)
    private val fc = cos(facing * Astro.DEG)

    /** Nominal x and y of altitude [alt] and azimuth [az] into [out]; false when it's behind the view. */
    fun project(alt: Double, az: Double, out: FloatArray): Boolean {
        val ca = cos(alt * Astro.DEG)
        val e = sin(az * Astro.DEG) * ca; val n = cos(az * Astro.DEG) * ca; val u = sin(alt * Astro.DEG)
        // right (facing × up), forward
        val x = e * fc - n * fs
        val z = e * fs + n * fc
        if (z < -0.6) return false
        out[0] = cx + (2 * r * x / (1 + z)).toFloat()
        out[1] = horizon - (2 * r * u / (1 + z)).toFloat()
        return true
    }

    /** Altitude and azimuth shown at nominal ([x], [y]). */
    fun unproject(x: Float, y: Float): DoubleArray {
        val px = (x - cx) / (2.0 * r); val py = (horizon - y) / (2.0 * r)
        val rho2 = px * px + py * py
        val k = 1 / (1 + rho2)
        val sx = 2 * px * k; val sy = 2 * py * k; val sz = (1 - rho2) * k
        val e = sx * fc + sz * fs; val n = -sx * fs + sz * fc
        return doubleArrayOf(asin(sy.coerceIn(-1.0, 1.0)) / Astro.DEG, Astro.norm(atan2(e, n) / Astro.DEG))
    }

    companion object {
        /**
         * The facing that shows altitude [alt], azimuth [az] in column [col] of a view with its middle in [cx] and radius
         * [r]: a vista turns to its moon, so the moon stands where the painter puts it (its light on the water under
         * it). Solves x = cx + 2r·cos(alt)·sin(Δ) / (1 + cos(alt)·cos(Δ)) for Δ, the azimuth off the middle.
         */
        fun facingFor(alt: Double, az: Double, col: Float, cx: Float, r: Float): Double {
            val t = ((col - cx) / (2.0 * r))
            val c = cos(alt * Astro.DEG).coerceAtLeast(0.05)
            val phi = atan2(t * c, c)
            val s = (t / (c * sqrt(1 + t * t))).coerceIn(-1.0, 1.0)
            return Astro.norm(az - (phi + asin(s)) / Astro.DEG)
        }
    }
}

/**
 * How clear the sky is for the stars: 1 clear, toward 0 under the weather a dialog brought (grey clouds, rain, snow,
 * fog) and none at all in a storm.
 */
fun skyClear(sky: Sky, storm: Boolean = false): Float =
    if (storm) 0f else (1f - sky.gloom * 1.7f - sky.rain * 1.5f - sky.snow * 1.5f - sky.fog * 1.3f).coerceIn(0f, 1f)

/**
 * The real night sky drawn into a picture (companion/GAME.md, "The night sky"): the Milky Way where it lies, the
 * constellations' stick figures (faint, brighter for the one whose card is open), the bright stars twinkling in their
 * colours, the planets shining steadily, and now and then a shooting star, all where [SkyNow] has them over the
 * village, seen through a [SkyView]. The stars show as the painted sky darkens, the faintest last; the moon's light
 * and the weather wash them out. Positions are worked out once a minute (the [SkyNow] and the view say when), only
 * the twinkle and the shooting stars each frame.
 *
 * One per painter: it keeps what the last picture drew to tap ([targets]).
 */
internal class NightSky {
    val targets = SkyTargets()

    // the projected catalog of the last minute and view (nominal px; NaN: not in the view)
    private var projNow: SkyNow? = null
    private var projView: SkyView? = null
    private var starX = FloatArray(0)
    private var starY = FloatArray(0)
    private val planetX = FloatArray(Planet.entries.size)
    private val planetY = FloatArray(Planet.entries.size)

    // the Milky Way on a coarse grid of the nominal picture, for the minute and view
    private var mwNow: SkyNow? = null
    private var mwView: SkyView? = null
    private var mwW = 0
    private var mwH = 0
    private var mwRows = 0
    private var mw = FloatArray(0)

    // what the last picture drew, to settle what is still to be seen when it's done
    private var canvas: PixelCanvas? = null
    private var k = 1
    private var x0 = 0
    private var y0 = 0
    private var snapTop = 0
    private var snapRows = 0
    private var snap = IntArray(0)
    private var skyMask = BooleanArray(0)
    private var settled = false
    private val checks = ArrayList<IntArray>() // slot (−1 the moon), picture x, y, colour
    private val tmp = FloatArray(2)

    /** Starts a picture: nothing to tap yet. */
    fun begin() {
        targets.clear()
        checks.clear()
        canvas = null
    }

    /**
     * Draws the sky of [now] through [view] into [c], over nominal rows up to [bottom] (just above the horizon: the hills
     * hide the rest), a nominal [width] wide; [k], [x0], [y0] map nominal to the picture's pixels (x·k − x0). [dark]: how
     * dark the painted sky is (0 day .. 1 night, [Env.dark]); [clear] how clear ([skyClear]); [time] the frame clock (the
     * twinkle); [wall] the clock's epoch ms (the shooting stars); [mark] a figure's, planet's or "milky-way" to show
     * brighter (its card is open). The moon's light washes out the faint stars and the Milky Way.
     */
    fun draw(
        c: PixelCanvas, now: SkyNow, view: SkyView, width: Int, bottom: Float, k: Int, x0: Int, y0: Int,
        dark: Float, clear: Float, time: Double, wall: Long, mark: String?,
    ) {
        canvas = c; this.k = k; this.x0 = x0; this.y0 = y0
        val a = ((dark - 0.3f) / 0.5f).coerceIn(0f, 1f) * clear
        if (a > 0f) {
            project(now, view)
            val wash = if (now.moonShows) now.moonLit * ((now.moonAlt / 25.0).toFloat().coerceIn(0f, 1f)) else 0f
            val c0 = c.penEmissive
            c.penEmissive = true
            c.penId = 0
            milkyWay(c, now, view, width, bottom, a * (1f - 0.85f * wash) * ((dark - 0.55f) / 0.35f).coerceIn(0f, 1f), mark == MILKY_WAY)
            lines(c, a, mark, bottom)
            stars(c, now, a, wash, time, bottom)
            c.penEmissive = c0
        }
        // the planets show earlier in the dusk than the stars
        val pa = ((dark - 0.15f) / 0.4f).coerceIn(0f, 1f) * clear
        if (pa > 0f) {
            project(now, view)
            val c0 = c.penEmissive
            c.penEmissive = true
            planets(c, now, pa, mark, bottom)
            c.penEmissive = c0
        }
        if (dark > 0.6f && clear > 0.5f) {
            val c0 = c.penEmissive
            c.penEmissive = true
            meteors(c, now, view, wall, ((dark - 0.6f) / 0.3f).coerceIn(0f, 1f) * clear, width, bottom)
            c.penEmissive = c0
        }
        snapshot(c, bottom)
        targets.isSky = ::stillSky
    }

    /** Where the moon stands in [view] (nominal x, y), or null while it doesn't show or is behind the view. */
    fun moonAt(now: SkyNow, view: SkyView): FloatArray? {
        if (!now.moonShows) return null
        val out = FloatArray(2)
        return if (view.project(now.moonAlt, now.moonAz, out)) out else null
    }

    /** The painter drew the moon at nominal ([x], [y]), radius [r]: it can be tapped, unless something hides it. */
    fun moonDrawn(x: Float, y: Float, r: Float) {
        targets.moon(x, y, r)
        val c = canvas ?: return
        val px = floor(x * k - x0).toInt(); val py = floor(y * k - y0).toInt()
        if (c.inside(px, py)) checks.add(intArrayOf(-1, px, py, c.get(px, py)))
    }

    /**
     * Once the picture is drawn (before a pass that tints every pixel, the village's grey of low spirits): what hills,
     * clouds or rain were drawn over isn't there to tap.
     */
    fun settle() {
        val c = canvas ?: return
        for (ch in checks) {
            val still = c.inside(ch[1], ch[2]) && c.get(ch[1], ch[2]) == ch[3]
            if (ch[0] < 0) targets.moonSeen = targets.moonSeen && still else targets.seen(ch[0], still)
        }
        val n = snapRows * c.width
        if (skyMask.size < n) skyMask = BooleanArray(n)
        val base = (snapTop - c.top) * c.width
        for (i in 0 until n) skyMask[i] = c.pixels[base + i] == snap[i]
        settled = true
        targets.settleMeteors()
    }

    // ------------------------------------------------------------------ the catalog, projected

    private fun project(now: SkyNow, view: SkyView) {
        if (projNow === now && projView == view) return
        projNow = now; projView = view
        val n = Stars.all.size
        if (starX.size != n) { starX = FloatArray(n); starY = FloatArray(n) }
        for (i in 0 until n) {
            if (now.starAlt[i] > -1f && view.project(now.starAlt[i].toDouble(), now.starAz[i].toDouble(), tmp)) {
                starX[i] = tmp[0]; starY[i] = tmp[1]
            } else { starX[i] = Float.NaN; starY[i] = Float.NaN }
        }
        for ((j, p) in now.planets.withIndex()) {
            if (p.alt > -1.0 && !p.inGlare && view.project(p.alt, p.az, tmp)) { planetX[j] = tmp[0]; planetY[j] = tmp[1] }
            else { planetX[j] = Float.NaN; planetY[j] = Float.NaN }
        }
    }

    /** The picture pixel of nominal x and y. */
    private fun pxOf(nx: Float) = floor(nx * k - x0).toInt()
    private fun pyOf(ny: Float) = floor(ny * k - y0).toInt()

    // ------------------------------------------------------------------ the Milky Way

    private fun milkyWay(c: PixelCanvas, now: SkyNow, view: SkyView, width: Int, bottom: Float, alpha: Float, marked: Boolean) {
        if (alpha <= 0.02f) { targets.milkyWay = null; return }
        grid(now, view, width, bottom)
        val boost = if (marked) 1.6f else 1f
        val col = Col.hex(0x8C98C8)
        val py0 = max(c.top, pyOf(0f)); val py1 = min(c.bottom, pyOf(bottom))
        val px0 = max(c.left, pxOf(0f)); val px1 = min(c.right, pxOf(width.toFloat()))
        for (y in py0 until py1) {
            val ny = (y + y0 + 0.5f) / k
            for (x in px0 until px1) {
                val v = sample((x + x0 + 0.5f) / k, ny) * alpha * boost
                if (v <= 0.03f) continue
                val q = Dither.at(x, y)
                if (v > q * 0.9f) c.blend(x, y, col, 0.05f + 0.14f * min(1f, v))
            }
        }
        targets.milkyWay = { x, y -> sample(x, y) }
    }

    /** The Milky Way's brightness on the grid (every [MW_CELL] nominal px), for the minute and view. */
    private fun grid(now: SkyNow, view: SkyView, width: Int, bottom: Float) {
        val rows = ceil(bottom).toInt()
        if (mwNow === now && mwView == view && mwRows == rows && mwW == width / MW_CELL + 2) return
        mwNow = now; mwView = view; mwRows = rows
        mwW = width / MW_CELL + 2; mwH = rows / MW_CELL + 2
        if (mw.size != mwW * mwH) mw = FloatArray(mwW * mwH)
        for (gy in 0 until mwH) for (gx in 0 until mwW) {
            val hz = view.unproject((gx * MW_CELL).toFloat(), (gy * MW_CELL).toFloat())
            mw[gy * mwW + gx] = if (hz[0] < -2.0) 0f else {
                val eq = Astro.equatorialOf(hz[0], hz[1], now.place.lat, now.lst)
                val g = Astro.galactic(eq[0], eq[1])
                band(g[0], g[1]) * (hz[0] / 12.0).toFloat().coerceIn(0.3f, 1f)
            }
        }
    }

    private fun sample(nx: Float, ny: Float): Float {
        if (mwW == 0) return 0f
        val gx = nx / MW_CELL; val gy = ny / MW_CELL
        val ix = floor(gx).toInt(); val iy = floor(gy).toInt()
        if (ix < 0 || iy < 0 || ix >= mwW - 1 || iy >= mwH - 1) return 0f
        val fx = gx - ix; val fy = gy - iy
        val i = iy * mwW + ix
        val top = mw[i] + (mw[i + 1] - mw[i]) * fx
        val bot = mw[i + mwW] + (mw[i + mwW + 1] - mw[i + mwW]) * fx
        return top + (bot - top) * fy
    }

    // ------------------------------------------------------------------ the figures and the stars

    private fun lines(c: PixelCanvas, a: Float, mark: String?, bottom: Float) {
        if (a < 0.35f) return
        val col = Col.hex(0x9FB2E6)
        for ((fi, f) in Stars.figures.withIndex()) {
            if (f.lines.isEmpty()) continue
            val marked = f.id == mark
            val alpha = (if (marked) 0.5f else 0.075f) * ((a - 0.35f) / 0.4f).coerceIn(0f, 1f)
            var j = 0
            while (j < f.lines.size) {
                val s = f.lines[j]; val e = f.lines[j + 1]
                j += 2
                val ax = starX[s]; val ay = starY[s]; val bx = starX[e]; val by = starY[e]
                if (ax.isNaN() || bx.isNaN() || (ay > bottom && by > bottom)) continue
                targets.line(fi, ax, ay, bx, by)
                dotted(c, ax, ay, bx, by, col, alpha, bottom, dense = marked)
            }
        }
    }

    /** A dotted line between two stars, clear of them by a couple of pixels, only above [bottom]. */
    private fun dotted(c: PixelCanvas, ax: Float, ay: Float, bx: Float, by: Float, col: Int, alpha: Float, bottom: Float, dense: Boolean) {
        val len = sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay))
        if (len < 6f) return
        val gap = 2.6f
        val steps = ((len - 2 * gap) * k).roundToInt()
        for (s in 0..steps) {
            val u = (gap + s.toFloat() / k) / len
            val nx = ax + (bx - ax) * u; val ny = ay + (by - ay) * u
            if (ny >= bottom) continue
            // dots a nominal pixel apart, every one on the marked figure
            if (!dense && (s / k) % 2 == 1) continue
            c.blend(pxOf(nx), pyOf(ny), col, alpha)
        }
    }

    private fun stars(c: PixelCanvas, now: SkyNow, a: Float, wash: Float, time: Double, bottom: Float) {
        // the faintest star to be seen: 4.6 on a dark night, fewer at dusk and under a bright moon
        val limit = 1.3f + 3.3f * a - 1.6f * wash
        val all = Stars.all
        for (i in Stars.faintestFirst) {
            val st = all[i]
            val x = starX[i]; val y = starY[i]
            if (x.isNaN() || y >= bottom || st.mag > limit) continue
            val alt = now.starAlt[i]
            val low = (alt / 12f).coerceIn(0.3f, 1f)
            val swing = if (alt < 20f) 0.32f else 0.2f
            val tw = 1f - swing + swing * sin(time * (1.6 + (i % 7) * 0.41) + i * 1.7).toFloat()
            val b = ((0.3f + 0.7f * a) * ((limit - st.mag) / 1.8f).coerceIn(0.3f, 1f) * tw * low).coerceIn(0f, 1f)
            val px = pxOf(x); val py = pyOf(y)
            val col = colour(st.bv)
            // the brightest a picture pixel bigger closer up, the faint ones a single one
            val size = when {
                st.mag < 1.6f -> k
                st.mag < 3f -> max(1, k - 1)
                else -> 1
            }
            for (dy in 0 until size) for (dx in 0 until size) c.blend(px + dx, py + dy, col, b)
            if (st.mag < 1.6f) {
                // a little cross of light round the bright ones, clearer round the brightest
                val g = b * if (st.mag < 0.5f) 0.5f else 0.22f
                val arm = max(1, k / 2)
                for (q in 0 until size) for (e in 1..arm) {
                    c.blend(px - e, py + q, col, g); c.blend(px + size - 1 + e, py + q, col, g)
                    c.blend(px + q, py - e, col, g); c.blend(px + q, py + size - 1 + e, col, g)
                }
            }
            if (st.proper != null || Stars.figureOf(i) != null) {
                // tapped at the middle of what was drawn
                val slot = targets.star(i, (px + x0 + size / 2f) / k, (py + y0 + size / 2f) / k)
                if (c.inside(px, py)) checks.add(intArrayOf(slot, px, py, c.get(px, py)))
            }
        }
    }

    private fun planets(c: PixelCanvas, now: SkyNow, a: Float, mark: String?, bottom: Float) {
        for ((j, p) in now.planets.withIndex()) {
            val x = planetX[j]; val y = planetY[j]
            if (x.isNaN() || y >= bottom) continue
            val b = (a * ((2.2 - p.mag) / 2.5).toFloat().coerceIn(0.35f, 1f)).coerceIn(0f, 1f)
            val col = when (p.planet) {
                Planet.VENUS -> Col.hex(0xFFFBEA)
                Planet.JUPITER -> Col.hex(0xFFF1D6)
                Planet.MARS -> Col.hex(0xFFAE86)
                Planet.SATURN -> Col.hex(0xF6E2B0)
            }
            val px = pxOf(x); val py = pyOf(y)
            // the bright ones a pixel bigger: Venus and Jupiter outshine every star
            val size = if (p.mag < -1.5) k + max(1, k / 2) else k
            if (p.planet.id == mark) c.ditherCircle(px + size / 2f, py + size / 2f, 3.5f * k, Col.hex(0xC8D4FF), 0.35f * b)
            if (p.mag < -3.0) c.ditherCircle(px + size / 2f, py + size / 2f, 2.6f * k, col, 0.18f * b)
            for (dy in 0 until size) for (dx in 0 until size) c.blend(px + dx, py + dy, col, b)
            val slot = targets.planet(p.planet, (px + x0 + size / 2f) / k, (py + y0 + size / 2f) / k)
            if (c.inside(px, py)) checks.add(intArrayOf(slot, px, py, c.get(px, py)))
        }
    }

    // ------------------------------------------------------------------ shooting stars

    private fun meteors(c: PixelCanvas, now: SkyNow, view: SkyView, wall: Long, a: Float, width: Int, bottom: Float) {
        if (wall <= 0L) return
        val from = FloatArray(2); val to = FloatArray(2)
        for (m in Meteors.around(wall, now.place)) {
            if (!view.project(m.alt0, m.az0, from) || !view.project(m.alt1, m.az1, to)) continue
            // in the picture's sky, or not at all
            if (from[1] >= bottom || max(from[0], to[0]) < 0f || min(from[0], to[0]) > width || max(from[1], to[1]) < 0f) continue
            targets.meteor(m, from[0], from[1], to[0], to[1])
            if (wall > m.end) continue
            streak(c, m, from, to, ((wall - m.start).toFloat() / m.duration).coerceIn(0f, 1f), a, bottom)
        }
    }

    /** One shooting star [p] of the way along: a bright head and a fading tail behind it. */
    private fun streak(c: PixelCanvas, m: Meteor, from: FloatArray, to: FloatArray, p: Float, a: Float, bottom: Float) {
        val hx = from[0] + (to[0] - from[0]) * p; val hy = from[1] + (to[1] - from[1]) * p
        val tail = max(0f, p - 0.4f)
        val tx = from[0] + (to[0] - from[0]) * tail; val ty = from[1] + (to[1] - from[1]) * tail
        val len = sqrt((hx - tx) * (hx - tx) + (hy - ty) * (hy - ty))
        val steps = max(1, (len * k).roundToInt())
        val glow = a * m.bright * sin(PI * p.coerceIn(0.05f, 0.95f)).toFloat().coerceAtLeast(0.35f)
        val col = Col.hex(0xFFF6E0)
        for (s in 0..steps) {
            val u = s.toFloat() / steps
            val nx = tx + (hx - tx) * u; val ny = ty + (hy - ty) * u
            if (ny >= bottom) continue
            c.blend(pxOf(nx), pyOf(ny), col, glow * (0.15f + 0.85f * u * u))
        }
        if (hy < bottom) c.blend(pxOf(hx), pyOf(hy), Col.hex(0xFFFFFF), glow)
    }

    // ------------------------------------------------------------------ what stays sky

    /** Keeps the sky's pixels as drawn, to tell later whether a point is still sky (not a hill or a cloud drawn over). */
    private fun snapshot(c: PixelCanvas, bottom: Float) {
        val top = max(c.top, 0); val rows = min(c.bottom, pyOf(bottom)) - top
        snapTop = top; snapRows = max(0, rows)
        settled = false
        val n = snapRows * c.width
        if (snap.size < n) snap = IntArray(n)
        if (n > 0) System.arraycopy(c.pixels, (top - c.top) * c.width, snap, 0, n)
    }

    private fun stillSky(nx: Float, ny: Float): Boolean {
        val c = canvas ?: return false
        if (!settled) return true
        val px = pxOf(nx); val py = pyOf(ny)
        if (!c.inside(px, py) || py < snapTop || py >= snapTop + snapRows) return false
        return skyMask[(py - snapTop) * c.width + (px - c.left)]
    }

    companion object {
        const val MILKY_WAY = "milky-way"

        /** The Milky Way's grid: a node every this many nominal pixels. */
        private const val MW_CELL = 4

        /** A star's colour from its B−V: blue-white, white, yellowish, orange, red. */
        fun colour(bv: Float): Int = when {
            bv < 0.0f -> Col.hex(0xD2DCFF)
            bv < 0.3f -> Col.hex(0xF4F6FF)
            bv < 0.8f -> Col.hex(0xFFF6DE)
            bv < 1.3f -> Col.hex(0xFFE4B4)
            else -> Col.hex(0xFFCB9A)
        }

        /**
         * How bright the Milky Way is at galactic longitude [l] and latitude [b] (degrees), 0..1: brightest toward the
         * centre in Sagittarius and bright again in Cygnus, faint toward the anticentre in Auriga, wider toward the
         * centre, split by the Great Rift from Cygnus down to Sagittarius, and clumpy all along.
         */
        fun band(l: Double, b: Double): Float {
            val lc = Astro.signed(l)
            val half = cos(lc / 2 * Astro.DEG)
            var v = 0.34 + 0.66 * half * half * abs(half) + 0.3 * exp(-((lc - 78) / 22).let { it * it }) + 0.2 * exp(-(lc / 16).let { it * it })
            val width = 6.5 + 5.0 * exp(-(lc / 40).let { it * it })
            v *= exp(-(b / width).let { it * it } / 2)
            v *= 1 - 0.6 * exp(-((lc - 38) / 30).let { it * it }) * exp(-((b - 2.4) / 1.6).let { it * it })
            v *= 0.62 + 0.62 * Noise.v2((lc / 5).toFloat(), (b / 3.5).toFloat(), 17)
            return v.toFloat().coerceIn(0f, 1f)
        }
    }
}
