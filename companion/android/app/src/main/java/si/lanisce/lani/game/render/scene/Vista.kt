package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Env
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.Season
import si.lanisce.lani.game.render.SkyView
import si.lanisce.lani.game.scene.SceneFrame
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * An outdoor close-up as a painted vista: a wide sky in dithered bands, layers of hills fading into the haze,
 * and the ground as a real plane in perspective down to the viewer, so fields, rows and paths run off to the
 * horizon and everything on them shrinks with distance.
 *
 * The camera stands [EYE] m above the ground; a point [d] m away on the ground lies `F × EYE / d` rows below the
 * horizon and a point [x] m to the side lies `F × x / d` columns off the middle ([gx], [gy], and back with
 * [depthAt], [sideAt]). The stage's bottom row is 10 m away, where the people sprites (about 27 px, big heads
 * and all) stand for 1.7 m, so people stand 9–13 m from us; [pm] gives the pixels per metre at any depth.
 *
 * Closer up ([detail] 2 or 3) the focal length [F] and every pixel size grow with the detail: the same view,
 * finer, with rows, furrows and blades that were too thin to draw now showing.
 */
internal abstract class VistaPainter : ArtPainter() {
    /** The horizon's row on the 240 × 160 stage. */
    protected open val horizon: Int = 56

    /** The horizon's canvas row and the vanishing point's column. */
    protected var hz = 0
    protected var vx = 0f

    override fun stageTop(height: Int): Int = (height - STAGE_H) * 2 / 5
    override fun backdrop(frame: SceneFrame): Int = Env(frame.hour, frame.month, false, frame.sky.gloom).skyTop

    /** The focal length in the picture's pixels: [BASE_F] on the scene's canvas, times the [detail]. */
    protected val F: Float get() = BASE_F * detail

    /** Call first: the horizon and the vanishing point for this canvas. */
    protected fun setup() {
        hz = oy + horizon * detail
        vx = ox + STAGE_W * detail / 2f
    }

    // ------------------------------------------------------------------ the ground plane

    /** The row of the ground [d] m away, and the column of a point [x] m to the side of the middle there. */
    protected fun gy(d: Float): Float = hz + F * EYE / d
    protected fun gx(x: Float, d: Float): Float = vx + F * x / d

    /** Pixels per metre [d] m away. */
    protected fun pm(d: Float): Float = F / d

    /** The row of a point [hgt] m above the ground [d] m away. */
    protected fun hy(d: Float, hgt: Float): Float = hz + F * (EYE - hgt) / d

    /** The ground's depth under canvas row [py] (rows below the horizon only) and the side offset of column [px] there. */
    protected fun depthAt(py: Int): Float = F * EYE / max(0.35f, py + 0.5f - hz)
    protected fun sideAt(px: Int, d: Float): Float = (px + 0.5f - vx) * d / F

    /**
     * Fills the ground from the horizon down: [shade] gets the side offset and depth (m) of each pixel and the
     * pixel itself. The id pen is [id] (0: plain ground that shadows fall on).
     */
    protected inline fun ground(id: Int = 0, crossinline shade: (Float, Float, Int, Int) -> Int) {
        c.penId = id
        for (py in max(max(0, hz), c.top) until min(h, c.bottom)) {
            val d = depthAt(py)
            for (px in max(0, c.left) until min(w, c.right)) c.set(px, py, shade(sideAt(px, d), d, px, py))
        }
        c.penId = 0
    }

    /**
     * A pattern of rows running away from us, [spacing] m apart, as a 0..1 phase across the row, or -1 where the
     * rows are too thin to see at this depth (use the field's plain colour there, maybe dithered).
     */
    protected fun rowPhase(x: Float, d: Float, spacing: Float): Float {
        val px = spacing * pm(d)
        if (px < 2.2f) return -1f
        val u = x / spacing
        return u - floor(u)
    }

    /** A pattern of rows across our view (furrows, rungs) every [spacing] m of depth, 0..1, or -1 when too thin. */
    protected fun bandPhase(d: Float, spacing: Float): Float {
        val rows = F * EYE / (d * d) * spacing // rows per band at this depth
        if (rows < 2.2f) return -1f
        val u = d / spacing
        return u - floor(u)
    }

    /** The haze over the land [d] m away: 0 near, toward 1 at the horizon. */
    protected fun haze(d: Float): Float = ((d - 30f) / 260f).coerceIn(0f, 0.75f)

    /** [col] seen through the haze [d] m away, toward the horizon's sky colour. */
    protected fun hazy(col: Int, d: Float): Int = Col.mix(col, env.skyHorizon, haze(d))

    // ------------------------------------------------------------------ sky

    /**
     * Which way the vista looks at night, when its moon isn't up: the stars over it are the ones in that part of the sky
     * (0 north, 90 east, 180 south, 270 west). While the moon is up the view turns to it (see [vistaSky]).
     */
    protected open val skyFacing: Double = 180.0

    /**
     * The sky from the top to the horizon: bands, stars, the sun's glow and disc, the moon, clouds (more under a grey sky,
     * the sun behind them). The moon stands in column [moonX] (its light on the water under it), at row [moonY] when it
     * is highest and lower as it rises and sets, behind the hills drawn after it.
     *
     * With the real sky ([SceneFrame.skyNow]) the stars are the real ones: while the moon is up the vista looks toward it,
     * so it stands in its column among the stars around it, as high as it is (between [moonY] and the hills); else the
     * vista looks its own way ([skyFacing]).
     */
    protected fun vistaSky(cloudCount: Int = 4, seed: Int = 0, moonX: Int = ox + 200 * detail, moonY: Int = oy + 14 * detail) {
        val d = detail
        val gloom = frame.sky.gloom
        sky(0, hz + 2 * d)
        val real = frame.skyNow
        var moonRowReal: Int? = null
        if (real != null) {
            val facing = if (real.moonShows) SkyView.facingFor(real.moonAlt, real.moonAz, moonX.toFloat() / d, (ox + STAGE_W * d / 2f) / d, SKY_R) else skyFacing
            val view = nightSky(facing, hz, hz - 6 * d)
            val at = view?.let { night.moonAt(real, it) }
            if (at != null) moonRowReal = (at[1] * d).toInt().coerceIn(moonY, hz - 2 * d)
        } else if (gloom < 0.5f) stars(0, hz - 6 * d, 90, seed)
        sunGlow()
        if (gloom < 0.55f) sun(ox + 24 * d, ox + 216 * d, hz - 4 * d)
        if (env.dark > 0.25f && gloom < 0.55f && (real == null || moonRowReal != null)) {
            val row = moonRowReal ?: moonRow(moonY, hz - 2 * d)
            moon(moonX, row, 6 * d, skyAt(0.2f))
            if (real != null && env.moonShows) night.moonDrawn(moonX.toFloat() / d, row.toFloat() / d, 7f)
        }
        vistaClouds(cloudCount + (gloom * 9f).toInt(), seed)
    }

    /** Where the sun stands (canvas x, y), or null below the horizon. */
    protected fun sunAt(): Pair<Float, Float>? {
        if (env.sun <= -0.12f) return null
        val span = (s.hour - env.sunrise) / (env.sunset - env.sunrise)
        val d = detail
        val x = ox + (24 + 192 * span.coerceIn(0f, 1f)) * d
        val y = max(7f * d, hz - 4f * d - env.sun * hz * 0.95f)
        return x to y
    }

    /** A wide warm glow in the sky around a low sun, in dithered rings; none behind grey clouds. */
    protected fun sunGlow() {
        if (frame.sky.gloom > 0.3f) return
        val (sx, sy) = sunAt() ?: return
        val low = (1f - env.sun * 2.2f).coerceIn(0f, 1f)
        if (low <= 0.05f) return
        val warm = Col.hex(0xFFC98A)
        val r = (70f + 40f * low) * detail
        c.penEmissive = true
        for (y in max(max(0, (sy - r * 0.6f).toInt()), c.top) until min(min(h, hz + 2 * detail), c.bottom)) for (x in max(max(0, (sx - r).toInt()), c.left) until min(min(w, (sx + r).toInt()), c.right)) {
            val dx = (x + 0.5f - sx) / r; val dy = (y + 0.5f - sy) / (r * 0.6f)
            val q = 1f - (dx * dx + dy * dy)
            if (q <= 0f) continue
            if (q * q * 0.55f * low > Dither.at(x, y)) c.set(x, y, Col.mix(c.get(x, y), warm, 0.35f))
        }
        c.penEmissive = false
    }

    /**
     * Soft clouds drifting slowly: flat bottoms, heaped tops, a dithered fringe, lit from below by a low sun and
     * grey-blue by night.
     */
    protected fun vistaClouds(n: Int, seed: Int) {
        val low = (1f - env.sun * 2.5f).coerceIn(0f, 1f)
        val top = when {
            env.dark > 0.6f -> Col.hex(0x3A4266)
            else -> Col.mix(Col.hex(0xFBFCFF), Col.hex(0xFFE2C4), low)
        }
        var under = when {
            env.dark > 0.6f -> Col.hex(0x262C4A)
            else -> Col.mix(Col.hex(0xD6E0EE), Col.mix(Col.hex(0xE8A08A), env.skyMid, 0.25f), low)
        }
        // under a grey sky, grey clouds, dark underneath
        val gloom = frame.sky.gloom
        val topCol = if (gloom > 0f) Col.mix(top, env.lit(Col.hex(0xB4BAC4)), gloom * 0.8f) else top
        if (gloom > 0f) under = Col.mix(under, env.lit(Col.hex(0x6E7684)), gloom * 0.8f)
        val d = detail
        val span = w / d + 120
        c.penEmissive = true
        for (k in 0 until n) {
            val speed = 0.5 + Noise.rnd(k, seed + 7) * 0.6
            val cx = (((Noise.rnd(k, seed + 8) * span + t * speed) % span).toFloat() - 60f) * d
            val cy = 6f * d + Noise.rnd(k, seed + 9) * (hz * 0.55f)
            val cw = (22f + Noise.rnd(k, seed + 10) * 26f) * d
            val ch = cw * (0.28f + Noise.rnd(k, seed + 11) * 0.1f)
            for (y in max(max(0, (cy - ch).toInt()), c.top) until min(min(h, (cy + 2 * d).toInt()), c.bottom)) for (x in max(max(0, (cx - cw).toInt()), c.left) until min(min(w, (cx + cw).toInt()), c.right)) {
                val u = (x + 0.5f - cx) / cw
                // the heap: bumps along the top, flat underneath
                val heap = (1f - u * u) * (0.65f + 0.35f * Noise.v1(u * 3.2f + k * 7f, seed + 12)) * ch
                val above = cy - (y + 0.5f)
                if (above < 0f || above > heap) continue
                // the fringe, measured on the scene canvas: the same soft edge at every detail
                val edge = min((heap - above) / d, 1f - abs(u))
                if (edge < 0.18f * (ch / d) / 10f && Dither.at(x, y) < 0.5f) continue
                val lit = above / max(1f, heap)
                c.set(x, y, if (lit > 0.45f + (Dither.at(x, y) - 0.5f) * 0.3f) topCol else under)
            }
        }
        c.penEmissive = false
    }

    // ------------------------------------------------------------------ hills

    /**
     * A layer of hills: the crest at [base] − [amp] × noise, filled down to [bottom], in [col] hazed by [haze]
     * toward the horizon's colour, lit on the side that faces the sun and with dithered shade toward its foot; in
     * winter and spring the peaks higher than [snowLine] (a share of [amp]) carry snow. [sharp] makes mountain peaks. [texture] (column, row,
     * rows below the crest → colour) paints the slope instead, e.g. with fields. Emissive (drawn in the light of
     * the hour already). Returns the crest per column for trees and houses.
     */
    protected fun ridge(
        base: Float, amp: Float, freq: Float, seed: Int, col: Int, haze: Float, bottom: Int = hz + 1, snowLine: Float = 2f,
        sharp: Boolean = false, texture: ((Int, Int, Int) -> Int)? = null,
    ): IntArray {
        val crest = IntArray(w)
        val body = env.lit(Col.mix(col, env.skyHorizon, haze))
        val shadeCol = env.lit(Col.mix(Col.scale(col, 0.82f), env.skyHorizon, haze))
        val rim = env.lit(Col.mix(Col.mix(col, Col.hex(0xFFF0C8), 0.25f), env.skyHorizon, haze * 0.8f))
        val snow = env.lit(Col.mix(Pal.SNOW_L, env.skyHorizon, haze * 0.7f))
        val sunLeft = (sunAt()?.first ?: vx) < vx
        val capped = env.season == Season.WINTER || env.season == Season.SPRING
        c.penEmissive = true
        val d = detail
        for (x in 0 until w) {
            // the same hills at every detail: the noise runs along the scene canvas's columns
            val u = (x - ox) * freq / d
            val n = if (sharp) {
                // ridged: peaks where the noise folds, smaller ridges on their flanks
                val a = 1f - abs(Noise.v1(u, seed) * 2f - 1f)
                val b = 1f - abs(Noise.v1(u * 2.9f, seed + 1) * 2f - 1f)
                a * a * 0.75f + b * 0.25f
            } else Noise.v1(u, seed) * 0.7f + Noise.v1(u * 2.7f, seed + 1) * 0.3f
            crest[x] = (base - amp * n).toInt()
        }
        for (x in max(0, c.left) until min(w, c.right)) {
            val top = crest[x]
            val slope = crest[min(w - 1, x + d)] - crest[max(0, x - d)]
            val facing = if (sunLeft) slope > 0 else slope < 0
            for (y in max(max(0, top), c.top) until min(min(h, bottom), c.bottom)) {
                val depth = (y - top).toFloat() / max(1f, (bottom - top).toFloat())
                var col2 = if (texture != null) env.lit(Col.mix(texture(x, y, y - top), env.skyHorizon, haze))
                else if (depth > 0.45f + (Dither.at(x, y) - 0.5f) * 0.4f) shadeCol else body
                if (y - top < 2 * d && facing) col2 = if (texture != null) Col.mix(col2, rim, 0.5f) else rim
                val peak = (base - top) / max(1f, amp)
                if (capped && peak > snowLine && y - top < (peak - snowLine) * amp * 0.9f + d) col2 = snow
                c.set(x, y, col2)
            }
        }
        c.penEmissive = false
        return crest
    }

    /** Round treetops along a crest, darker than the hill, [every] px apart, [size] px tall. */
    protected fun treeline(crest: IntArray, every: Int, size: Float, col: Int, haze: Float, seed: Int, from: Int = 0, to: Int = w) {
        val dark = env.lit(Col.mix(col, env.skyHorizon, haze))
        val light = env.lit(Col.mix(Col.mix(col, Col.hex(0xFFF0C8), 0.18f), env.skyHorizon, haze))
        c.penEmissive = true
        // placed along the scene canvas's columns, then scaled: the same trees at every detail
        val d = detail
        val step = every / d.toFloat()
        var xb = from / d + (Noise.rnd(seed, 3) * step).toInt()
        var i = 0
        while (xb * d < to) {
            val x = xb * d
            if (Noise.rnd(i, seed) < 0.75f && x in 0 until w) {
                val r = size * (0.6f + Noise.rnd(i, seed + 1) * 0.5f)
                val y = crest[x] + r * 0.4f
                if (env.season == Season.WINTER && Noise.rnd(i, seed + 2) < 0.5f) {
                    // a spruce: a narrow cone
                    for (yy in 0 until (r * 2.2f).toInt()) c.hline((x - yy * 0.35f).toInt(), (x + yy * 0.35f).toInt(), (y - r * 1.6f + yy).toInt(), if ((yy / detail) % 3 == 0) light else dark)
                } else {
                    c.fillEllipse(x.toFloat(), y, r, r * 0.85f, dark)
                    c.fillEllipse(x - r * 0.3f, y - r * 0.3f, r * 0.55f, r * 0.45f, light)
                }
            }
            xb += (step * (0.6f + Noise.rnd(i, seed + 4) * 0.8f)).toInt().coerceAtLeast(1)
            i++
        }
        c.penEmissive = false
    }

    // ------------------------------------------------------------------ things on the ground, scaled by distance

    /**
     * A leafy tree [d] m away at side [x] m, [height] m tall: a trunk and a crown of clumps, each lit on the sun's
     * side and shaded below with a dithered edge; bare forked branches in winter, blossom in spring when
     * [blossom], fruit dots in summer and autumn when [fruit] (0 for none).
     */
    protected fun leafyTree(x: Float, d: Float, height: Float, seed: Int, blossom: Boolean = false, fruit: Int = 0) {
        val p = pm(d)
        val bx = gx(x, d); val by = gy(d)
        val trunkH = height * 0.42f * p; val tw = max(1f, 0.32f * p)
        val bark = Col.hex(0x5A4030); val barkL = Col.hex(0x7A5A40)
        c.fillRect((bx - tw / 2).toInt(), (by - trunkH).toInt(), max(1, tw.toInt()), trunkH.toInt() + 1, bark)
        if (tw >= 2f) c.vline((bx - tw / 2).toInt(), (by - trunkH).toInt(), by.toInt(), barkL)
        val cr = height * 0.34f * p
        val cy = by - trunkH - cr * 0.5f
        val season = env.season
        if (season == Season.WINTER) {
            // bare forked branches, snow along them
            fun branch(x0: Float, y0: Float, a: Float, len: Float, depth: Int) {
                val x1 = x0 + sin(a) * len; val y1 = y0 - kotlin.math.cos(a) * len
                c.line(x0.toInt(), y0.toInt(), x1.toInt(), y1.toInt(), bark)
                if (len > 3f) c.set(((x0 + x1) / 2).toInt(), ((y0 + y1) / 2).toInt() - 1, Pal.SNOW_L)
                if (depth > 0) { branch(x1, y1, a - 0.45f, len * 0.62f, depth - 1); branch(x1, y1, a + 0.4f, len * 0.6f, depth - 1) }
            }
            for (b in -1..1) branch(bx, by - trunkH, b * 0.55f, cr * 0.7f, if (cr > 8f) 2 else 1)
            return
        }
        val (dark, mid, light) = when (season) {
            Season.AUTUMN -> Triple(Col.hex(0x8A3E1A), Col.hex(0xC0682A), Col.hex(0xE8A844))
            Season.SPRING -> Triple(Col.hex(0x3E7A34), Col.hex(0x62A248), Col.hex(0x9ACC66))
            else -> Triple(Col.hex(0x2A5E30), Col.hex(0x3E7E3A), Col.hex(0x68A44C))
        }
        val sunLeft = (sunAt()?.first ?: vx) < bx
        val lx = if (sunLeft) -1f else 1f
        // the crown's clumps, back ones first; each dark below, mid, lit on the sun's side
        val n = 7
        for (k in 0 until n) {
            val a = k * 2.4f + seed * 0.7f
            val rr = cr * (0.42f + Noise.rnd(k, seed) * 0.16f)
            val px = bx + sin(a) * cr * 0.55f; val py = cy + kotlin.math.cos(a) * cr * 0.4f - (if (k == n - 1) cr * 0.35f else 0f)
            for (yy in (py - rr).toInt()..(py + rr).toInt()) for (xx in (px - rr).toInt()..(px + rr).toInt()) {
                val dx = (xx + 0.5f - px) / rr; val dy = (yy + 0.5f - py) / (rr * 0.9f)
                val q = dx * dx + dy * dy
                if (q > 1f) continue
                if (q > 0.8f && Dither.at(xx, yy) < 0.35f) continue
                val lit = -dx * lx * 0.55f - dy * 0.75f + (Noise.rnd(xx, yy, seed) - 0.5f) * 0.35f
                c.set(xx, yy, if (lit > 0.42f) light else if (lit > -0.2f) mid else dark)
            }
        }
        if (cr >= 3f) for (k in 0 until (cr * 1.4f).toInt()) {
            val px = bx + (Noise.rnd(k, seed + 1) - 0.5f) * cr * 1.8f; val py = cy + (Noise.rnd(k, seed + 2) - 0.5f) * cr * 1.4f
            if (!c.inside(px.toInt(), py.toInt()) || c.ids[c.index(px.toInt(), py.toInt())] != c.penId) continue
            when {
                blossom && season == Season.SPRING -> c.set(px.toInt(), py.toInt(), if (k % 3 == 0) Col.hex(0xF6C8D8) else Col.hex(0xFFF4F8))
                fruit != 0 && season != Season.SPRING && k % 2 == 0 -> c.set(px.toInt(), py.toInt(), fruit)
            }
        }
    }

    /** A spruce [d] m away at side [x] m, [height] m tall: tiers of dark branches, snow on them in winter. */
    protected fun spruce(x: Float, d: Float, height: Float, seed: Int) {
        val p = pm(d)
        val bx = gx(x, d); val by = gy(d)
        val hh = height * p; val half = height * 0.22f * p
        val dark = Col.hex(0x1E452C); val mid = Col.hex(0x2C5E3A); val light = Col.hex(0x3E7A4A)
        c.fillRect((bx - max(1f, 0.2f * p) / 2).toInt(), (by - hh * 0.15f).toInt(), max(1, (0.2f * p).toInt()), (hh * 0.15f).toInt() + 1, Col.hex(0x4A3428))
        val tiers = max(3, (hh / 6f).toInt())
        for (i in 0 until tiers) {
            val f = i / tiers.toFloat()
            val ty = by - hh * 0.12f - (hh * 0.88f) * (1f - f)
            val tw = half * (0.25f + 0.75f * f)
            val th = hh * 0.88f / tiers * 1.5f
            for (yy in 0 until th.toInt().coerceAtLeast(1)) {
                val ww = tw * (yy + 1) / th
                val y = (ty + yy).toInt()
                c.hline((bx - ww).toInt(), (bx + ww).toInt(), y, if (yy == th.toInt() - 1) dark else mid)
                c.set((bx - ww).toInt(), y, light)
                if (env.snow && yy < 2) c.hline((bx - ww * 0.8f).toInt(), (bx - ww * 0.1f).toInt(), y, Pal.SNOW_L)
            }
        }
        c.set(bx.toInt(), (by - hh).toInt(), mid)
    }

    /** A soft shadow on the plain ground (id 0) under something [d] m away at side [x] m, [r] m across. */
    protected fun groundShadow(x: Float, d: Float, r: Float, f: Float = 0.78f) {
        val p = pm(d)
        shadow(gx(x, d), gy(d), max(2f, r * p), max(1f, r * p * 0.3f), f)
    }

    /** Dark grass blades in silhouette across the bottom edge, swaying: the viewer's own patch of meadow. */
    protected fun foregroundGrass(from: Int, to: Int, seed: Int) {
        val g = env.grass
        val dark = if (env.snow) Pal.SNOW_D else Col.scale(g[2], 0.62f)
        val mid = if (env.snow) Pal.SNOW_M else Col.scale(g[2], 0.8f)
        val d = detail
        for (x in max(max(0, from), c.left) until min(min(w, to), c.right)) {
            val n = Noise.rnd(x, seed)
            if (n < 0.35f) continue
            val bladeH = ((4 + Noise.rnd(x, seed + 1) * 9) * d).toInt()
            val sway = (sin(t * 1.6 + x * 0.21 / d) * 1.2 * d).toFloat() * gust
            for (i in 0 until bladeH) {
                val y = h - 1 - i
                val xx = (x + sway * i / bladeH).toInt()
                c.set(xx, y, if (i < bladeH * 0.6f) dark else mid)
            }
        }
    }

    // ------------------------------------------------------------------ a dry-stone wall (suhozid)

    /**
     * A dry-stone wall (suhozid) facing us [d] m away, from side [x0] to [x1] m, [height] m tall and [thick] m deep: courses
     * of limestone laid without mortar, every stone its own size, shape and tone, lit along its top edge and on the sun's
     * side, in shade underneath, with dark joints between the stones; pits, lichen and moss on some; a coping of stones set
     * on edge along the top, whose top shows from above; a step stile of three through-stones sticking out of the face at
     * side [stile] (m; none when NaN), each throwing its shadow on the wall below it; the ends stepping down; grass at the
     * foot, snow along the top and on the steps in winter. The stones are laid out in metres, so every detail shows the
     * same wall, finer: joints, pits and lichen come out closer up. Drawn in daylight colours (the light pass dims them).
     */
    protected fun dryWall(x0: Float, x1: Float, d: Float, height: Float, seed: Int, thick: Float = 0.55f, stile: Float = Float.NaN) {
        val p = pm(d); val by = gy(d)
        val cope = 0.2f
        val body = height - cope
        // the courses, bottom up: 0.15..0.26 m each, the last one fitted under the coping
        val courses = FloatArray(12)
        var n = 0; var hc = 0f
        while (hc < body - 0.1f && n < courses.size - 1) { courses[n++] = hc; hc += 0.2f + 0.12f * Noise.rnd(n, seed, 1) }
        courses[n] = body
        val sunLeft = (sunAt()?.first ?: vx) < gx((x0 + x1) / 2f, d)
        val gap = max(0.02f, 0.55f / p)
        val bevel = max(0.04f, 0.95f / p)
        val snow = env.snow
        /** The wall's top at side [x]: its full height, stepping down over the last metre at either end. */
        fun topAt(x: Float): Float {
            val end = min(x - x0, x1 - x)
            return if (end > 0.9f) height else height * (0.45f + 0.55f * floor(end / 0.3f).coerceAtLeast(0f) / 3f)
        }
        // the top face of the coping, seen from above: slabs set on edge across the wall, their edges catching the light
        val topRowFar = hy(d + thick, height).toInt(); val topRowNear = hy(d, height).toInt()
        for (py in max(topRowFar, c.top)..min(topRowNear, c.bottom - 1)) {
            val dd = F * (EYE - height) / max(0.1f, py + 0.5f - hz)
            if (dd < d || dd > d + thick) continue
            for (px in max(gx(x0, dd).toInt(), c.left)..min(gx(x1, dd).toInt(), c.right - 1)) {
                val x = sideAt(px, dd)
                if (topAt(x) < height - 0.01f) continue
                val cell = floor(x / COPE_W).toInt()
                val u = x / COPE_W - cell
                var col = STONES[(Noise.rnd(cell, seed, 5) * 4).toInt()]
                col = if (u < 0.18f || u > 0.88f) Col.scale(col, 0.62f) else if ((u < 0.45f) == sunLeft) Col.mix(col, LIT_STONE, 0.35f) else col
                if (snow && Noise.rnd(px, py, seed + 6) < 0.9f) col = if (u < 0.18f) Pal.SNOW_M else Pal.SNOW_L
                else if (!snow && Noise.v2(x * 7f, dd * 7f, seed + 7) > 0.72f) col = MOSS
                c.set(px, py, col)
            }
        }
        // the face
        val face0 = max((by - height * p).toInt() - 1, c.top); val face1 = min(by.toInt(), c.bottom - 1)
        for (py in face0..face1) {
            val hm = (by - (py + 0.5f)) / p
            if (hm < 0f) continue
            for (px in max(gx(x0, d).toInt(), c.left)..min(gx(x1, d).toInt(), c.right - 1)) {
                val x = sideAt(px, d)
                val top = topAt(x)
                if (hm > top) continue
                c.set(px, py, stoneAt(px, py, x, hm, top, body, courses, n, seed, sunLeft, gap, bevel, snow, stile, p))
            }
        }
        // the step stile: three through-stones, the lowest first
        if (!stile.isNaN()) for ((k, hs) in floatArrayOf(0.3f, 0.56f, 0.8f).withIndex()) {
            val out = 0.3f
            val xa = stile - 0.3f + k * 0.04f; val xb = stile + 0.28f - k * 0.03f
            val yb = hy(d, hs).toInt(); val yf = hy(d - out, hs).toInt(); val yl = hy(d - out, hs - 0.075f).toInt()
            for (py in max(yb, c.top)..min(yl, c.bottom - 1)) {
                val front = py > yf
                val dd = if (front) d - out else F * (EYE - hs) / max(0.1f, py + 0.5f - hz)
                for (px in max(gx(xa, dd).toInt(), c.left)..min(gx(xb, dd).toInt(), c.right - 1)) {
                    val x = sideAt(px, dd)
                    val edge = min(x - xa, xb - x)
                    var col = if (front) STONES[k % 4] else Col.mix(STONES[(k + 2) % 4], LIT_STONE, 0.45f)
                    if (front) col = Col.scale(col, if (py >= yl - 1) 0.6f else 0.78f)
                    if (edge < 0.035f) col = Col.scale(col, 0.7f)
                    if (!front && snow) col = Pal.SNOW_L
                    else if (!front && Noise.v2(x * 8f, hs * 20f + dd * 8f, seed + 8) > 0.74f) col = MOSS
                    c.set(px, py, col)
                }
            }
        }
        // grass and a flower or two at its foot; in winter a drift of snow against it
        val xs0 = max(gx(x0, d).toInt(), c.left); val xs1 = min(gx(x1, d).toInt(), c.right - 1)
        for (px in xs0..xs1) {
            val x = sideAt(px, d)
            if (snow) {
                val drift = (0.06f + 0.08f * Noise.v1(x * 1.3f, seed + 9)) * p
                for (j in 0..drift.toInt()) c.set(px, by.toInt() - j, if (j == drift.toInt()) Pal.SNOW_M else Pal.SNOW_L)
                continue
            }
            val r = Noise.rnd((x * 40f).toInt(), seed, 10)
            if (r < 0.3f) continue
            val tall = (0.06f + 0.18f * Noise.rnd((x * 40f).toInt(), seed, 11)) * p
            val g = env.grass
            for (j in 0 until max(1, tall.toInt())) c.set(px, by.toInt() - j, if (j > tall * 0.6f) g[1] else g[2])
            if (r > 0.985f && env.month in 5..8) c.set(px, (by - tall).toInt() - 1, Pal.PAINTED[(px * 7) % 6])
        }
    }

    /** The colour of the wall's face at ([x] m, [hm] m up), picture pixel ([px], [py]); see [dryWall]. */
    private fun stoneAt(
        px: Int, py: Int, x: Float, hm: Float, top: Float, body: Float, courses: FloatArray, n: Int, seed: Int,
        sunLeft: Boolean, gap: Float, bevel: Float, snow: Boolean, stile: Float, p: Float,
    ): Int {
        // the coping: slabs set on edge, rounded at the top
        if (hm > body && top >= body + 0.1f) {
            val cell = floor(x / COPE_W).toInt()
            val u = x / COPE_W - cell
            val tone = STONES[(Noise.rnd(cell, seed, 5) * 4).toInt()]
            val slabTop = top - 0.03f * Noise.rnd(cell, seed, 12) - (if (u < 0.2f || u > 0.8f) 0.02f else 0f)
            if (hm > slabTop) return JOINT
            if (u < 0.1f || u > 0.92f || hm < body + gap) return JOINT
            var col = if (hm > slabTop - bevel) Col.mix(tone, LIT_STONE, 0.5f) else if ((u < 0.35f) == sunLeft) tone else Col.scale(tone, 0.82f)
            if (snow && hm > slabTop - bevel * 1.5f) col = Pal.SNOW_L
            return col
        }
        var k = 0
        while (k < n - 1 && hm >= courses[k + 1]) k++
        val cb = courses[k]; val ct = courses[k + 1]
        // the stones along the course, their joints jittered, every course offset
        val avg = 0.42f + 0.24f * Noise.rnd(k, seed, 2)
        val sx = x / avg + Noise.rnd(k, seed, 3) * 7f
        var i = floor(sx).toInt()
        fun edge(j: Int) = (j + (Noise.rnd(j, k, seed + 4) - 0.5f) * 0.55f)
        if (sx < edge(i)) i-- else if (sx >= edge(i + 1)) i++
        val l = (edge(i) - Noise.rnd(k, seed, 3) * 7f) * avg; val r = (edge(i + 1) - Noise.rnd(k, seed, 3) * 7f) * avg
        val b = cb + (Noise.rnd(i, k, seed + 13) - 0.5f) * 0.04f; val t = ct + (Noise.rnd(i, k + 1, seed + 13) - 0.5f) * 0.04f
        // a rounded stone inside its cell, its edge a little rough
        val rough = (Noise.v2(x * 14f, hm * 14f, seed + 14) - 0.5f) * 0.02f
        val rad = min(r - l, t - b) * 0.38f
        val ex = max(0f, max(l + rad - x, x - (r - rad))); val ey = max(0f, max(b + rad - hm, hm - (t - rad)))
        val inside = kotlin.math.sqrt(ex * ex + ey * ey) - rad
        if (inside > -gap + rough) {
            // the joint: deep shade, now and then a small chinking stone wedged in it
            return if (Noise.v2(x * 11f, hm * 11f, seed + 15) > 0.8f && inside < gap * 0.3f) Col.scale(STONES[1], 0.6f) else JOINT
        }
        val tone0 = STONES[(Noise.rnd(i, k, seed + 16) * 4).toInt()]
        val tone = Col.scale(tone0, 0.9f + 0.18f * Noise.rnd(i, k, seed + 17))
        val fromTop = t - hm; val fromBottom = hm - b
        val fromSun = if (sunLeft) x - l else r - x
        val fromShade = if (sunLeft) r - x else x - l
        var col = when {
            fromTop < bevel -> Col.mix(tone, LIT_STONE, 0.55f)
            fromBottom < bevel * 0.8f -> Col.scale(tone, 0.66f)
            fromSun < bevel * 0.8f -> Col.mix(tone, LIT_STONE, 0.28f)
            fromShade < bevel * 0.8f -> Col.scale(tone, 0.8f)
            else -> tone
        }
        // pits, lichen and moss
        if (Noise.rnd(px, py, seed + 18) < 0.05f) col = Col.scale(col, 0.84f)
        val lich = Noise.v2(x * 9f, hm * 9f, seed + 19)
        if (lich > 0.8f) col = if (Noise.rnd(i, k, seed + 20) < 0.5f) LICHEN_Y else LICHEN_G
        if (!snow && fromTop < bevel * 1.6f && hm < 0.45f && Noise.v2(x * 6f, hm * 3f, seed + 21) > 0.55f) col = MOSS
        if (snow && fromTop < bevel && Noise.rnd(i, k, seed + 22) < 0.6f) col = Pal.SNOW_L
        // the stile's through-stones throw their shadow on the face below them
        if (!stile.isNaN() && x > stile - 0.32f && x < stile + 0.3f) for (hs in floatArrayOf(0.3f, 0.56f, 0.8f)) if (hm < hs - 0.075f && hm > hs - 0.2f) col = Col.scale(col, 0.7f)
        return col
    }

    // ------------------------------------------------------------------ weather

    override fun weather() {
        val sky = frame.sky
        val wx = Weather(c, w, h, detail, t, env, sky)
        val horizon = hz / detail.toFloat(); val bottom = h / detail.toFloat()
        // mist lies on the land: thick along the horizon, thinner up the sky and toward us
        wx.fog(sky.fog) { row -> if (row < horizon) exp((row - horizon) / 22f) else 1f - 0.8f * ((row - horizon) / max(1f, bottom - horizon)) }
        wx.rain()
        splashes(sky.rain)
        wx.snow()
        val flash = wx.flash()
        if (flash > 0f) {
            wx.lighten(flash, 0.42f, 0.42f)
            if (flash >= 1f) wx.boltLine(6 * detail, hz - 2 * detail, wx.bolt())
        }
    }

    /** Rain splashing on the ground: little crowns popping up here and there, the more the harder it rains. */
    private fun splashes(level: Float) {
        if (level <= 0.05f) return
        val col = Col.mix(env.lit(SPLASH), SPLASH, 0.25f)
        val n = (60 * level).toInt()
        for (j in 0 until n) {
            val cycle = t * 2.4 + Noise.rnd(j, 34)
            val ph = (cycle % 1.0).toFloat()
            if (ph > 0.3f) continue
            val k = cycle.toInt()
            val dist = 10.5f + Noise.rnd(j, 35, k) * 45f
            val side = (Noise.rnd(j, 36, k) - 0.5f) * (w / F) * dist
            val x = gx(side, dist).toInt(); val y = gy(dist).toInt()
            val r = max(1, (pm(dist) * 0.1f).toInt())
            if (ph < 0.12f) {
                c.blend(x - r, y - r, col, 0.7f); c.blend(x + r, y - r, col, 0.7f); c.blend(x, y - 2 * r, col, 0.5f)
            } else {
                for (o in -r..r) c.blend(x + o, y, col, 0.55f)
            }
        }
    }

    companion object {
        private val SPLASH = Col.hex(0xC4D2E6)

        /** The limestone of a dry-stone wall: warm grey, grey, blue-grey, a rusty one. */
        private val STONES = intArrayOf(Col.hex(0xC2BAA8), Col.hex(0xA8A294), Col.hex(0x9EA0A4), Col.hex(0xB49E7C))
        private val LIT_STONE = Col.hex(0xF4EEDC)
        private val JOINT = Col.hex(0x2E2A26)
        private val MOSS = Col.hex(0x62803A)
        private val LICHEN_Y = Col.hex(0xD4B04A)
        private val LICHEN_G = Col.hex(0xB6C2A0)
        /** How wide the coping's slabs set on edge are (m). */
        private const val COPE_W = 0.2f

        /** Camera height in metres and the focal length in the scene canvas's pixels: 1.7 m 10 m away is 32 px, about a person sprite. */
        const val EYE = 5.5f
        const val BASE_F = 190f
    }
}
