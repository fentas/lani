package si.lanisce.lani.game.render

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How the full-screen village is looked at: canvas pixel (x, y) is drawn at screen pixel
 * (x × [scale] + [tx], y × [scale] + [ty]). [scale] is screen pixels per canvas pixel.
 */
data class Camera(val scale: Float, val tx: Float, val ty: Float) {
    fun toScreenX(x: Float) = x * scale + tx
    fun toScreenY(y: Float) = y * scale + ty
    fun toCanvasX(sx: Float) = (sx - tx) / scale
    fun toCanvasY(sy: Float) = (sy - ty) / scale

    /** The canvas pixel under screen x [sx] (whole canvas pixels, as a tap needs them); [pixelY] for y. */
    fun pixelX(sx: Float) = floor(toCanvasX(sx)).toInt()
    fun pixelY(sy: Float) = floor(toCanvasY(sy)).toInt()

    /** Canvas x [x] on screen, on a whole screen pixel (for what follows the canvas); [screenY] for y. */
    fun screenX(x: Int) = toScreenX(x.toFloat()).roundToInt()
    fun screenY(y: Int) = toScreenY(y.toFloat()).roundToInt()

    /** At rest for pixel art: a whole number of screen pixels per canvas pixel, on whole screen pixels. */
    val crisp: Boolean get() = scale == scale.roundToInt().toFloat() && tx == tx.roundToInt().toFloat() && ty == ty.roundToInt().toFloat()
}

/**
 * The camera's limits for one view of [viewW] × [viewH] screen pixels over a [canvasW] × [canvasH] canvas: from
 * [minScale] (the fitted view: the canvas just covers the view) up to [maxScale]. Every camera it returns keeps
 * the canvas covering the whole view, so the village can't be dragged away. [detailed]: the picture gets finer
 * closer up (the detail levels of the village and the scenes, see [Lens]); without, a plain magnification.
 */
data class CameraRig(
    val viewW: Int, val viewH: Int, val canvasW: Int, val canvasH: Int, val minScale: Int, val maxScale: Int,
    val detailed: Boolean = true,
) {

    /** The fitted view: the whole canvas, centred (on whole pixels, like [SceneFit]'s offsets). */
    fun fitted(): Camera = clamp(Camera(minScale.toFloat(), ((viewW - canvasW * minScale) / 2).toFloat(), ((viewH - canvasH * minScale) / 2).toFloat()))

    fun clamp(c: Camera): Camera {
        // a broken input (NaN, infinity) must never leave the camera somewhere it can't come back from
        val s = if (c.scale.isFinite()) c.scale.coerceIn(minScale.toFloat(), maxScale.toFloat()) else minScale.toFloat()
        return Camera(s, axis(c.tx.finiteOr(0f), viewW, canvasW * s), axis(c.ty.finiteOr(0f), viewH, canvasH * s))
    }

    private fun Float.finiteOr(f: Float) = if (isFinite()) this else f

    /** Canvas edges stay at or beyond the view's; a canvas smaller than the view (never at rest) is centred. */
    private fun axis(t: Float, view: Int, content: Float): Float =
        if (content <= view) (view - content) / 2f else t.coerceIn(view - content, 0f)

    /** Zooms by [factor] around the screen point ([fx], [fy]): the canvas point under it stays under it. */
    fun zoomAt(c: Camera, fx: Float, fy: Float, factor: Float): Camera {
        if (!factor.isFinite() || factor <= 0f || !fx.isFinite() || !fy.isFinite()) return clamp(c)
        val s = (c.scale * factor).coerceIn(minScale.toFloat(), maxScale.toFloat())
        val k = s / c.scale
        return clamp(Camera(s, fx - (fx - c.tx) * k, fy - (fy - c.ty) * k))
    }

    fun pan(c: Camera, dx: Float, dy: Float): Camera = clamp(c.copy(tx = c.tx + dx.finiteOr(0f), ty = c.ty + dy.finiteOr(0f)))

    /** The detail level of the village at [scale] (see [Lens.detail]): 1 at the fitted zoom, 2 from 1.5 × it, 3 from 2.5 ×; always 1 when not [detailed]. */
    fun detail(scale: Float): Int = if (detailed) Lens.detail(scale, minScale) else 1

    /**
     * The crisp scale nearest [scale] (or the largest one at most [scale] when [down]): a whole number of screen
     * pixels per canvas pixel at that scale's detail level, so it's a multiple of the level, within the limits.
     */
    fun crisp(scale: Float, down: Boolean = false): Float {
        var s = scale.coerceIn(minScale.toFloat(), maxScale.toFloat()).let { if (down) floor(it).toInt() else it.roundToInt() }
        repeat(2) {
            val k = detail(s.toFloat())
            val n = if (down) s / k else (s.toFloat() / k).roundToInt()
            s = (n * k).coerceIn(minScale, maxScale)
        }
        while (s > minScale && s % detail(s.toFloat()) != 0) s--
        return s.toFloat()
    }

    /**
     * Where a gesture comes to rest: the nearest crisp scale (so every canvas pixel is an exact square of
     * screen pixels at its detail level) on whole screen pixels, keeping the screen point ([fx], [fy]) where it is.
     */
    fun settle(c0: Camera, fx: Float = viewW / 2f, fy: Float = viewH / 2f): Camera {
        val c = clamp(c0)
        if (!fx.isFinite() || !fy.isFinite()) return settle(c)
        val s = crisp(c.scale)
        val k = s / c.scale
        val z = clamp(Camera(s, fx - (fx - c.tx) * k, fy - (fy - c.ty) * k))
        return clamp(Camera(s, z.tx.roundToInt().toFloat(), z.ty.roundToInt().toFloat()))
    }

    /** The camera that shows canvas point ([x], [y]) at screen point ([fx], [fy]) at the crisp scale nearest [scale] (clamped, so maybe not exactly there). */
    fun lookAt(x: Float, y: Float, scale: Float, fx: Float = viewW / 2f, fy: Float = viewH / 2f): Camera {
        val s = crisp(scale)
        return settleExact(clamp(Camera(s, fx - x * s, fy - y * s)))
    }

    /** The lens for a [w] × [h] canvas showing what [c] sees at detail [k] (see [Lens.of]). */
    fun lens(c: Camera, k: Int, w: Int, h: Int): Lens = Lens.of(k, c, viewW, viewH, canvasW, canvasH, w, h)

    /**
     * Frames the canvas rectangle [r] in the screen area [area] (left, top, right, bottom; the part of the view
     * not under the HUD): the largest whole-number scale at which it fits, but at most [maxFrame], centred
     * sideways; of the room left over above and below, [above] goes above (half: centred; less: the rectangle
     * sits higher, with more of what lies below it in view).
     */
    fun frame(r: CanvasRect, area: FloatArray, maxFrame: Int = maxScale, above: Float = 0.5f): Camera {
        val aw = max(1f, area[2] - area[0]); val ah = max(1f, area[3] - area[1])
        val fit = floor(min(aw / max(1f, r.width), ah / max(1f, r.height))).toInt()
        val s = crisp(fit.coerceIn(minScale, max(minScale, min(maxFrame, maxScale))).toFloat(), down = true).toInt()
        val spare = max(0f, ah - r.height * s)
        val fy = area[1] + spare * above.coerceIn(0f, 1f) + (ah - spare) / 2f
        return lookAt(r.centerX, r.centerY, s.toFloat(), (area[0] + area[2]) / 2f, fy)
    }

    /**
     * On the way from [a] to [b] at [t] (0..1), for a fly-to: the zoom changes evenly in log scale, the canvas
     * point at the view's centre moves straight. The ends are exactly [a] and [b].
     */
    fun between(a: Camera, b: Camera, t: Float): Camera {
        if (t <= 0f) return a
        if (t >= 1f) return b
        val s = exp(ln(a.scale) + (ln(b.scale) - ln(a.scale)) * t)
        val cx = viewW / 2f; val cy = viewH / 2f
        val ax = a.toCanvasX(cx); val ay = a.toCanvasY(cy)
        val bx = b.toCanvasX(cx); val by = b.toCanvasY(cy)
        val x = ax + (bx - ax) * t; val y = ay + (by - ay) * t
        return clamp(Camera(s, cx - x * s, cy - y * s))
    }

    /** How far apart two cameras look (screen pixels of the view's centre, plus zoom), to tell "moved" from "home". */
    fun distance(a: Camera, b: Camera): Float {
        val cx = viewW / 2f; val cy = viewH / 2f
        val d = hypot((a.toCanvasX(cx) - b.toCanvasX(cx)) * b.scale, (a.toCanvasY(cy) - b.toCanvasY(cy)) * b.scale)
        return d + abs(ln(a.scale / b.scale)) * viewW
    }

    /** Whole screen pixels for a camera already on a whole-number scale. */
    private fun settleExact(c: Camera): Camera =
        if (c.scale == c.scale.roundToInt().toFloat()) clamp(Camera(c.scale, c.tx.roundToInt().toFloat(), c.ty.roundToInt().toFloat())) else c

    /** [px] canvas pixels at the fitted zoom, in canvas pixels at [c]'s: the same distance on screen, at least one (a tap's slop). */
    fun slop(c: Camera, px: Int): Int =
        if (c.scale.isFinite() && c.scale > 0f) (px * minScale / c.scale).roundToInt().coerceIn(1, max(1, px)) else max(1, px)

    /** The canvas rectangle on screen, clipped to the canvas: [x0, y0, x1, y1] in whole canvas pixels, for drawing only what shows. */
    fun visible(c: Camera): IntArray {
        val x0 = floor(c.toCanvasX(0f)).toInt().coerceIn(0, canvasW)
        val y0 = floor(c.toCanvasY(0f)).toInt().coerceIn(0, canvasH)
        val x1 = kotlin.math.ceil(c.toCanvasX(viewW.toFloat())).toInt().coerceIn(x0, canvasW)
        val y1 = kotlin.math.ceil(c.toCanvasY(viewH.toFloat())).toInt().coerceIn(y0, canvasH)
        return intArrayOf(x0, y0, x1, y1)
    }

    companion object {
        /** Zooming in goes this many times past the fitted scale. */
        const val ZOOM = 4

        /**
         * The town zooms in this many times past its fitted scale: its fitted view is further out than a scene's (see
         * [SceneFit.TOWN_W]), and its closest look as close as ever (a 1080 px wide phone: 3 × fitted, 15 × closest).
         */
        const val TOWN_ZOOM = 5

        fun of(fit: SceneFit, viewW: Int, viewH: Int): CameraRig =
            CameraRig(viewW, viewH, fit.width, fit.height, fit.scale, fit.scale * ZOOM)

        /** The full-screen town's (see [SceneFit.town]): the whole village fitted, zooming in [TOWN_ZOOM] times, finer as it comes closer. */
        fun town(fit: SceneFit, viewW: Int, viewH: Int): CameraRig =
            CameraRig(viewW, viewH, fit.width, fit.height, fit.scale, fit.scale * TOWN_ZOOM)

        /**
         * A close-up scene's: [ZOOM] times past its fitted view, with the village's detail levels, so closer up the scene is drawn
         * finer (2 × from 1.5 × the fitted zoom, 3 × from 2.5 ×; see [si.lanisce.lani.game.scene.ScenePainter.render]).
         */
        fun scene(fit: SceneFit, viewW: Int, viewH: Int): CameraRig = of(fit, viewW, viewH)
    }
}
