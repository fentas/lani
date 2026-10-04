package si.lanisce.lani.widget

import si.lanisce.lani.game.GameState
import si.lanisce.lani.l10n.bi
import kotlin.math.ceil
import kotlin.math.roundToInt

/** The widget's texts and geometry; pure, so it can be tested without Android. */
internal data class WidgetInfo(val streak: String, val due: String, val event: String?) {
    companion object {
        /** [due] < 0 = unknown yet (the app has not loaded the dashboard since the widget was placed). */
        fun of(state: GameState?, streak: Int, due: Int, now: Long): WidgetInfo = WidgetInfo(
            streak = "🔥 $streak",
            due = when {
                due < 0 -> bi("widgetInfo.openLani")
                due == 0 -> "✅ ${bi("widgetInfo.allDone")}"
                due >= 20 -> "📚 ${bi("widgetInfo.dueMany")}"
                else -> "📚 ${bi("widgetInfo.dueToday", "due" to due)}"
            },
            event = state?.event?.takeIf { it.deadline > now }?.let { "${it.kind.emoji} ${it.kind.sl}!" },
        )

        /**
         * Internal render size for a widget of [widthPx] × [heightPx]: about 240 px wide at the widget's
         * aspect ratio, between 80 and 200 px tall (tall widgets get a narrower internal width).
         */
        fun internalSize(widthPx: Int, heightPx: Int): Pair<Int, Int> {
            val aspect = (widthPx.coerceAtLeast(1).toFloat() / heightPx.coerceAtLeast(1)).coerceIn(0.5f, 5f)
            val h = (240 / aspect).roundToInt().coerceIn(80, 200)
            val w = (h * aspect).roundToInt().coerceIn(120, 360)
            return w to h
        }

        /** Calls [clear] for each pixel outside a rounded rectangle of [radius] px over a [w] × [h] image. */
        inline fun roundCorners(w: Int, h: Int, radius: Float, clear: (Int, Int) -> Unit) {
            val r = ceil(radius).toInt().coerceAtMost(minOf(w, h) / 2)
            if (r <= 0) return
            for (y in 0 until r) for (x in 0 until r) {
                val dx = r - x - 0.5f; val dy = r - y - 0.5f
                if (dx * dx + dy * dy <= radius * radius) continue
                clear(x, y); clear(w - 1 - x, y); clear(x, h - 1 - y); clear(w - 1 - x, h - 1 - y)
            }
        }
    }
}
