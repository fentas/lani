package si.lanisce.lani.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the village's HUD remembers on this phone (companion/GAME.md, "The village's HUD"): whether the learner minimized
 * it to its pill ([minimized]; the slim row by default), and the news lines its panel showed last ([newsSeen]), so the
 * row's 📣 badge comes back only with new ones.
 */
class HudPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("village_hud", Context.MODE_PRIVATE)

    var minimized by mutableStateOf(prefs.getBoolean(KEY_MINIMIZED, false))
        private set

    var newsSeen by mutableStateOf(prefs.getStringSet(KEY_NEWS, null)?.toSet().orEmpty())
        private set

    fun minimize(on: Boolean) {
        if (minimized == on) return
        minimized = on
        prefs.edit().putBoolean(KEY_MINIMIZED, on).apply()
    }

    /** The panel showed [lines]: they are the ones seen now (yesterday's are forgotten). */
    fun sawNews(lines: Collection<String>) {
        val now = lines.toSet()
        if (now == newsSeen) return
        newsSeen = now
        prefs.edit().putStringSet(KEY_NEWS, now).apply()
    }

    private companion object {
        const val KEY_MINIMIZED = "minimized"
        const val KEY_NEWS = "news_seen"
    }
}
