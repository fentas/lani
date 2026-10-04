package si.lanisce.lani.game.render

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Which part of the world a canvas shows, and how finely. The village has a nominal (fitted, k = 1) canvas of
 * [baseW] × [baseH] pixels, the one the camera, the bubble anchors and the spots are measured in. A lens shows
 * a window of it at detail [k]: canvas pixel (i, j) shows the nominal point ((i + [x0]) / k, (j + [y0]) / k),
 * so k × k canvas pixels stand for one nominal pixel. The iso geometry (cells are 8k × 4k px) and the sprites
 * scale with k; the detail passes draw what the extra pixels allow (roof tiles, window frames, faces).
 *
 * The default lens is the nominal canvas itself (k = 1, no offset, base size = the canvas), which is what the
 * fitted village and every short canvas use, so nothing changes for them.
 */
data class Lens(val k: Int = 1, val x0: Int = 0, val y0: Int = 0, val baseW: Int = 0, val baseH: Int = 0) {
    /** Nominal x → canvas x. */
    fun cx(bx: Float): Float = bx * k - x0
    fun cy(by: Float): Float = by * k - y0

    /** Canvas x → nominal x. */
    fun bx(cx: Float): Float = (cx + x0) / k
    fun by(cy: Float): Float = (cy + y0) / k

    val nominal: Boolean get() = k == 1 && x0 == 0 && y0 == 0

    companion object {
        /** The detail levels: 1 (the nominal look), 2 and 3. */
        const val MAX_DETAIL = 3

        /**
         * The detail level for a camera at [scale] screen pixels per nominal pixel over a fitted [minScale]: 2 from
         * 1.5 × the fitted zoom, 3 from 2.5 ×. Zooming in, the pixels on screen get smaller and the world finer (at
         * 1.5 × a canvas pixel is 0.75 of a fitted one), and the canvas keeps its size.
         */
        fun detail(scale: Float, minScale: Int): Int = floor(scale / minScale.coerceAtLeast(1) + 0.5f).toInt().coerceIn(1, MAX_DETAIL)

        /**
         * The lens of a [w] × [h] canvas that shows what camera [c] sees of the nominal [baseW] × [baseH] canvas
         * (a [viewW] × [viewH] screen) at detail [k]: the visible part sits in the middle of the canvas, the rest
         * (when the canvas is bigger than the view at this zoom) is margin for panning between frames. Whole
         * canvas pixels, so pixels stay crisp.
         */
        fun of(k: Int, c: Camera, viewW: Int, viewH: Int, baseW: Int, baseH: Int, w: Int, h: Int): Lens {
            if (k <= 1) return Lens(1, 0, 0, baseW, baseH)
            val visW = viewW / c.scale * k; val visH = viewH / c.scale * k
            val x0 = (c.toCanvasX(0f) * k - (w - visW) / 2f).roundToInt().coerceIn(0, (baseW * k - w).coerceAtLeast(0))
            val y0 = (c.toCanvasY(0f) * k - (h - visH) / 2f).roundToInt().coerceIn(0, (baseH * k - h).coerceAtLeast(0))
            return Lens(k, x0, y0, baseW, baseH)
        }
    }
}

/** How a felled tree grows back, by the days since it was felled (see [si.lanisce.lani.game.WorldMarks]). */
enum class Regrowth {
    /** A fresh stump with chips about it. */
    STUMP,
    /** A sapling: two leaves on a stem. */
    SAPLING,
    /** A young tree, half the size. */
    YOUNG,
    /** The tree is back. */
    GROWN;

    companion object {
        const val SAPLING_DAYS = 3
        const val YOUNG_DAYS = 7
        const val GROWN_DAYS = 14

        fun of(days: Int): Regrowth = when {
            days < SAPLING_DAYS -> STUMP
            days < YOUNG_DAYS -> SAPLING
            days < GROWN_DAYS -> YOUNG
            else -> GROWN
        }

        /** Days from ISO day [mark] to ISO day [today]; an unreadable date counts as long ago. */
        fun days(mark: String, today: String): Int {
            val a = dayNumber(mark) ?: return GROWN_DAYS
            val b = dayNumber(today) ?: return GROWN_DAYS
            return (b - a).coerceAtLeast(0).coerceAtMost(10_000).toInt()
        }

        /** Days since 0000-03-01 of an ISO date, or null; pure arithmetic so it runs without java.time. */
        private fun dayNumber(iso: String): Long? {
            if (iso.length < 10 || iso[4] != '-' || iso[7] != '-') return null
            val y = iso.substring(0, 4).toIntOrNull() ?: return null
            val m = iso.substring(5, 7).toIntOrNull() ?: return null
            val d = iso.substring(8, 10).toIntOrNull() ?: return null
            if (m !in 1..12 || d !in 1..31) return null
            val yy = if (m <= 2) y - 1 else y
            val mm = if (m <= 2) m + 9 else m - 3
            return 365L * yy + yy / 4 - yy / 100 + yy / 400 + (153 * mm + 2) / 5 + d
        }
    }
}
