package si.lanisce.lani.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import si.lanisce.lani.MainActivity
import si.lanisce.lani.R
import si.lanisce.lani.data.GameStore
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.render.Frame
import si.lanisce.lani.game.render.Moon
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.render.VillageRenderer
import si.lanisce.lani.l10n.LangSetting
import si.lanisce.lani.game.culture.CultureSetting
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * Home-screen widget: the pixel village (compact framing, the current hour and month), the streak,
 * today's due cards and the active event. Tapping it opens the app.
 *
 * It renders from the cached village (GameStore) and the last known learner stats ([saveStats]). The app
 * refreshes it whenever the village is saved or the dashboard loads; BackgroundCheck refreshes it while the app is
 * closed, and the system asks once an hour so day turns into night.
 */
class VillageWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = async { update(context, manager, ids) }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        async { update(context, manager, intArrayOf(id)) }

    private fun async(block: () -> Unit) {
        val pending = goAsync()
        scope.launch {
            try { runCatching { block() } } finally { pending.finish() }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private const val PREFS = "village_widget"

        fun installed(context: Context): Boolean = ids(context).isNotEmpty()

        private fun ids(context: Context): IntArray = runCatching {
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, VillageWidget::class.java))
        }.getOrNull() ?: IntArray(0)

        /** Remembers the learner stats the widget shows (the village itself comes from the game cache). */
        fun saveStats(context: Context, streak: Int, due: Int) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("streak", streak).putInt("due", due).apply()
        }

        /**
         * Redraws every placed widget; does nothing when there is none. Blocking (reads the cache, renders a few
         * milliseconds): call it from a background thread, e.g. the app's serial game-cache dispatcher.
         */
        fun refresh(context: Context) {
            val ids = ids(context)
            if (ids.isNotEmpty()) runCatching { update(context, AppWidgetManager.getInstance(context), ids) }
        }

        private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            LangSetting(context).apply() // the system may start the app for the widget alone
            CultureSetting(context).apply()
            val state = GameStore(context).read()?.state
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val info = WidgetInfo.of(state, prefs.getInt("streak", 0), prefs.getInt("due", -1), System.currentTimeMillis())
            val density = context.resources.displayMetrics.density
            val radius = context.resources.getDimension(R.dimen.widget_radius)
            val open = PendingIntent.getActivity(
                context, 10, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            for (id in ids) {
                val o = manager.getAppWidgetOptions(id)
                // Portrait size: min width, max height (dp). Fall back to the 4×2 default.
                val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 250
                val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 110
                val views = RemoteViews(context.packageName, R.layout.widget_village)
                views.setImageViewBitmap(
                    R.id.widget_village,
                    render(state ?: GameState(), (wDp * density).roundToInt(), (hDp * density).roundToInt(), radius),
                )
                views.setTextViewText(R.id.widget_streak, info.streak)
                views.setTextViewText(R.id.widget_due, info.due)
                views.setTextViewText(R.id.widget_event, info.event.orEmpty())
                views.setViewVisibility(R.id.widget_event, if (info.event == null) View.GONE else View.VISIBLE)
                views.setOnClickPendingIntent(R.id.widget_root, open)
                manager.updateAppWidget(id, views)
            }
        }

        /**
         * The village at the widget's aspect ratio: rendered at internal resolution (about 240 px wide), then
         * scaled up by a whole factor with nearest-neighbour sampling so the pixels stay crisp. Corners are cut
         * to [radiusPx] so the picture follows the rounded background on every Android version.
         */
        private fun render(state: GameState, widthPx: Int, heightPx: Int, radiusPx: Float): Bitmap {
            val (iw, ih) = WidgetInfo.internalSize(widthPx, heightPx)
            val canvas = PixelCanvas(iw, ih)
            val now = LocalDateTime.now()
            VillageRenderer().render(
                canvas, state,
                Frame(time = 2.0, hour = now.hour + now.minute / 60f, month = now.monthValue, compact = true, moon = Moon.now()),
            )
            val small = Bitmap.createBitmap(canvas.pixels, iw, ih, Bitmap.Config.ARGB_8888)
            val k = (widthPx / iw.toFloat()).roundToInt().coerceIn(1, 4)
            val scaled = if (k == 1) small else Bitmap.createScaledBitmap(small, iw * k, ih * k, false).also { small.recycle() }
            val out = if (scaled.isMutable) scaled else scaled.copy(Bitmap.Config.ARGB_8888, true).also { scaled.recycle() }
            WidgetInfo.roundCorners(out.width, out.height, radiusPx * out.width / widthPx.coerceAtLeast(1)) { x, y -> out.setPixel(x, y, 0) }
            return out
        }
    }
}
