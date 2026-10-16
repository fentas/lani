package si.lanisce.lani.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the village's HUD remembers on this phone (companion/GAME.md, "The village's HUD"): whether its row of cards shows
 * under the bar ([cardsShown]; shown by default, the ring hides it), and what its cards marked new were when their sheets
 * showed them ([soonSeen]: today's lines and tonight's story; [stepsSeen]: the goal's open steps), so a dot comes back
 * only with something new.
 */
class HudPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("village_hud", Context.MODE_PRIVATE)

    var cardsShown by mutableStateOf(prefs.getBoolean(KEY_CARDS, true))
        private set

    var soonSeen by mutableStateOf(prefs.getStringSet(KEY_SOON, null)?.toSet().orEmpty())
        private set

    var stepsSeen by mutableStateOf(prefs.getStringSet(KEY_STEPS, null)?.toSet().orEmpty())
        private set

    fun showCards(on: Boolean) {
        if (cardsShown == on) return
        cardsShown = on
        prefs.edit().putBoolean(KEY_CARDS, on).apply()
    }

    /** The sheet showed [keys] of "Danes in kmalu": they are the ones seen now (yesterday's are forgotten). */
    fun sawSoon(keys: Collection<String>) {
        val now = keys.toSet()
        if (now == soonSeen) return
        soonSeen = now
        prefs.edit().putStringSet(KEY_SOON, now).apply()
    }

    /** The goal's open steps ([keys]) were seen: kept with the ones seen before, so a step done and gone is no news. */
    fun sawSteps(keys: Collection<String>) {
        val now = stepsSeen + keys
        if (now == stepsSeen) return
        stepsSeen = now
        prefs.edit().putStringSet(KEY_STEPS, now).apply()
    }

    private companion object {
        const val KEY_CARDS = "cards_shown"
        const val KEY_SOON = "soon_seen"
        const val KEY_STEPS = "steps_seen"
    }
}
