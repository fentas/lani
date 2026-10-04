package si.lanisce.lani.game.render

import kotlin.math.max
import kotlin.math.min

/**
 * How a view shows the village: a canvas of [width] × [height] internal pixels, drawn at the whole-number
 * [scale] (the same on both axes, so pixels stay square) at ([offX], [offY]). The canvas covers the whole view;
 * offsets are ≤ 0 and crop less than one internal pixel per edge.
 */
data class SceneFit(val width: Int, val height: Int, val scale: Int, val offX: Int, val offY: Int) {
    companion object {
        /** Nominal internal resolutions: the village keeps about this size on screen, more world fills the rest. */
        const val BASE_W = 240
        const val BASE_H = 160
        const val BASE_H_COMPACT = 90

        /**
         * The village's nominal width (the full-screen town and the Home header's strip): its whole clearing, the biggest
         * one's too, with the forest's edge round it. Wider than a scene's [BASE_W]: the village is seen from further out,
         * and zooming in brings its details closer (see [CameraRig.town]).
         */
        const val TOWN_W = 320

        /**
         * The largest scale at which the nominal canvas still fits the view; then more world columns or rows
         * (never a stretch) fill the view, up to 1.5× the nominal width and 2× the nominal height. A view even
         * larger than that gets the next bigger scale instead. [compact]: the Home header's strip, the whole village
         * ([TOWN_W] wide) over a sky up to 3× the nominal strip's height.
         */
        fun of(viewW: Int, viewH: Int, compact: Boolean): SceneFit {
            val baseW = if (compact) TOWN_W else BASE_W
            val baseH = if (compact) BASE_H_COMPACT else BASE_H
            if (viewW <= 0 || viewH <= 0) return SceneFit(baseW, baseH, 1, 0, 0)
            val maxW = baseW * 3 / 2; val maxH = baseH * (if (compact) 3 else 2)
            var s = max(1, min(viewW / baseW, viewH / baseH))
            while (ceilDiv(viewW, s) > maxW || ceilDiv(viewH, s) > maxH) s++
            val w = ceilDiv(viewW, s).coerceAtLeast(16); val h = ceilDiv(viewH, s).coerceAtLeast(16)
            return SceneFit(w, h, s, (viewW - w * s) / 2, (viewH - h * s) / 2)
        }

        /** The full-screen town may be up to this many nominal heights tall (a phone in portrait): forest fills it. */
        const val TOWN_TALL = 6

        /**
         * The full-screen town's canvas at its fitted (most zoomed-out) scale: [TOWN_W] nominal pixels across or more,
         * so the whole village of every age shows; a portrait phone gets a tall canvas (up to [TOWN_TALL] nominal heights;
         * the renderer adds forest below, see [VillageLayout.composition]) instead of a bigger scale that would cut the
         * village's sides off.
         */
        fun town(viewW: Int, viewH: Int): SceneFit {
            if (viewW <= 0 || viewH <= 0) return SceneFit(TOWN_W, BASE_H, 1, 0, 0)
            val maxW = TOWN_W * 3 / 2; val maxH = BASE_H * TOWN_TALL
            var s = max(1, min(viewW / TOWN_W, viewH / BASE_H))
            while (ceilDiv(viewW, s) > maxW || ceilDiv(viewH, s) > maxH) s++
            val w = ceilDiv(viewW, s).coerceAtLeast(16); val h = ceilDiv(viewH, s).coerceAtLeast(16)
            return SceneFit(w, h, s, (viewW - w * s) / 2, (viewH - h * s) / 2)
        }

        private fun ceilDiv(a: Int, b: Int) = (a + b - 1) / b
    }
}
