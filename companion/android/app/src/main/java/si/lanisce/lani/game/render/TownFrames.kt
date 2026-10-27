package si.lanisce.lani.game.render

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** A rectangle of a picture's canvas pixels (a lens's, see [Lens]): [x0] until [x0] + [w], [y0] until [y0] + [h]. */
data class PixelRect(val x0: Int, val y0: Int, val w: Int, val h: Int) {
    val x1: Int get() = x0 + w
    val y1: Int get() = y0 + h

    /** Whether [r] lies inside this one. */
    fun covers(r: PixelRect): Boolean = r.x0 >= x0 && r.y0 >= y0 && r.x1 <= x1 && r.y1 <= y1
}

/**
 * Which part of the town's picture to render closer up (detail 2 or 3, see [Lens]) for a camera. The picture is the lens
 * with no offset ([lens]: the whole village at k × k canvas pixels per nominal pixel), so its pixels stay where they are
 * as the camera moves: the ground it keeps in cells ([GroundCache]) is the same whatever part of it is shown. The canvas
 * holds a window of it ([PixelCanvas.originX]) covering what the screen shows ([screen]), [apron] px more round it so
 * its edges are drawn as the whole picture has them, and [margin] px more while the camera moves, for the frames until
 * the next picture is there. Its size is a multiple of [SIZE_STEP] px, so one canvas serves many frames. Pure:
 * TownWindowTest.
 */
object TownFrames {
    /**
     * Pixels round what the screen shows that a window always has, so the screen's edges are drawn as the whole picture
     * has them: the outline's pixel, a tuft of grass (k + 1 px tall) or a stone standing in from beyond.
     */
    fun apron(k: Int) = k + 2

    /**
     * Pixels more on each side while the camera moves: the frames until the next picture is there show the closer look up
     * to the screen's edge on a pan of up to 2,400 screen px a second (at 3 × the fitted zoom, a picture in 80 ms: Teo's
     * tablet's); a faster fling shows the nominal picture under it at the edge for a frame or two.
     */
    fun margin(k: Int) = 8 * k

    /** A window's size is a multiple of this many canvas pixels. */
    const val SIZE_STEP = 32

    /** The picture at detail [k] of the nominal [baseW] × [baseH] canvas: no offset. */
    fun lens(k: Int, baseW: Int, baseH: Int) = Lens(k, 0, 0, baseW, baseH)

    /**
     * What camera [c] shows of the picture at detail [k] on a [viewW] × [viewH] screen over the nominal [baseW] × [baseH]
     * canvas: in whole canvas pixels, inside the picture.
     */
    fun screen(k: Int, c: Camera, viewW: Int, viewH: Int, baseW: Int, baseH: Int): PixelRect {
        val pw = baseW * k; val ph = baseH * k
        val x0 = floor(c.toCanvasX(0f) * k).toInt().coerceIn(0, pw); val y0 = floor(c.toCanvasY(0f) * k).toInt().coerceIn(0, ph)
        val x1 = ceil(c.toCanvasX(viewW.toFloat()) * k).toInt().coerceIn(x0, pw); val y1 = ceil(c.toCanvasY(viewH.toFloat()) * k).toInt().coerceIn(y0, ph)
        return PixelRect(x0, y0, x1 - x0, y1 - y0)
    }

    /** What a window for [screen] at detail [k] must hold at least: the screen and its [apron], inside the picture. */
    fun needed(k: Int, screen: PixelRect, baseW: Int, baseH: Int): PixelRect {
        val a = apron(k)
        val x0 = max(0, screen.x0 - a); val y0 = max(0, screen.y0 - a)
        return PixelRect(x0, y0, min(baseW * k, screen.x1 + a) - x0, min(baseH * k, screen.y1 + a) - y0)
    }

    /** The window to render for [screen] at detail [k] (with the camera's [margin] when [moving]), inside the picture. */
    fun window(k: Int, screen: PixelRect, moving: Boolean, baseW: Int, baseH: Int): PixelRect {
        val pw = baseW * k; val ph = baseH * k
        val m = apron(k) + if (moving) margin(k) else 0
        val x0 = max(0, screen.x0 - m)
        val y0 = max(0, screen.y0 - m)
        val x1 = min(pw, screen.x1 + m); val y1 = min(ph, screen.y1 + m)
        val w = min(pw, roundUp(x1 - x0, SIZE_STEP)); val h = min(ph, roundUp(y1 - y0, SIZE_STEP))
        return PixelRect(min(x0, pw - w), min(y0, ph - h), w, h)
    }

    private fun roundUp(v: Int, step: Int) = (v + step - 1) / step * step
}
