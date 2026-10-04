package si.lanisce.lani.ui.stage

import android.content.Context
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.Villager
import java.time.LocalDate

/**
 * The people the stage draws from: the node's cast, then the village's own people ([Residents]) in
 * [state]. Without a cast (an older node) everyone is a stand-in with built-in lines.
 */
object StageCast {
    fun of(cast: List<Villager>, state: GameState? = null, today: LocalDate = LocalDate.now()): List<Villager> =
        cast + state?.let { s -> Residents.people(s, cast, today).filter { p -> cast.none { it.id == p.id } } }.orEmpty()
}

/** What the stage keeps on the phone: whether the companion speaks aloud, and today's companion. */
class StageMemory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("stage", Context.MODE_PRIVATE)

    var muted: Boolean
        get() = prefs.getBoolean(MUTED, false)
        set(v) = prefs.edit().putBoolean(MUTED, v).apply()

    /** The companion picked earlier [today], so they stay the same all day (training changes who was seen last). */
    fun daily(today: LocalDate): String? = prefs.getString(DAILY_ID, null)?.takeIf { prefs.getString(DAILY_ON, null) == today.toString() }

    fun pinDaily(today: LocalDate, id: String) = prefs.edit().putString(DAILY_ON, today.toString()).putString(DAILY_ID, id).apply()

    private companion object {
        const val MUTED = "muted"
        const val DAILY_ON = "daily.on"
        const val DAILY_ID = "daily.id"
    }
}
