package si.lanisce.lani.game.culture

import android.content.Context
import android.util.Log
import si.lanisce.lani.BuildConfig

/**
 * Which culture pack the village is in ([Cultures.current]): the learner's, as their bridge last said (GET /culture),
 * else [Cultures.DEFAULT]. Kept on the phone, so the village reads the same offline and before the first connection;
 * the packs themselves are bundled with the app.
 *
 * SharedPreferences, like the language pair (l10n/LangSetting): read before the first frame, and by the widget and the
 * background check.
 */
class CultureSetting(context: Context) {
    private val store = context.applicationContext.getSharedPreferences("culture", Context.MODE_PRIVATE)

    /** The pack the learner's bridge said; the default until it has. */
    val profile: String get() = store.getString(PROFILE, null) ?: Cultures.DEFAULT

    /** The bridge says [id]; true when that changed it. */
    fun profileSays(id: String): Boolean {
        if (id == profile) return false
        store.edit().putString(PROFILE, id).apply()
        return true
    }

    /** The village is in the learner's culture from now on (a pack that can't be used leaves the default); its id. */
    fun apply(): String {
        if (Cultures.onProblem == null) Cultures.onProblem = { e -> if (BuildConfig.DEBUG) Log.w("Cultures", e.message ?: e.id) }
        return Cultures.use(profile).id
    }

    private companion object {
        const val PROFILE = "profile_culture"
    }
}
