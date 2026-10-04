package si.lanisce.lani.l10n

import android.content.Context
import android.util.Log
import si.lanisce.lani.BuildConfig

/**
 * Which pair the phone shows ([L10n.pair]): one chosen on the phone (the connection sheet; for testing, and for the
 * second learner), else the learner profile's (learner-profile.json, served by the bridge's /state), else sl · en.
 *
 * SharedPreferences rather than the DataStore (data/Prefs.kt): the pair is read before the first frame, and by the
 * widget and the background check, none of which should wait for a coroutine.
 */
class LangSetting(context: Context) {
    private val store = context.applicationContext.getSharedPreferences("l10n", Context.MODE_PRIVATE)

    /** The pair chosen on the phone; null follows the learner profile. */
    val chosen: LangPair? get() = LangPair.parse(store.getString(CHOSEN, null))

    /** The learner profile's pair, as last read from the bridge. */
    val profile: LangPair get() = LangPair.parse(store.getString(PROFILE, null)) ?: LangPair.DEFAULT

    val pair: LangPair get() = chosen ?: profile

    fun choose(p: LangPair?) = store.edit().apply { if (p == null) remove(CHOSEN) else putString(CHOSEN, p.code) }.apply()

    /** The learner profile says [p]; true when that changed it. */
    fun profileSays(p: LangPair): Boolean {
        if (p == profile) return false
        store.edit().putString(PROFILE, p.code).apply()
        return true
    }

    /** Shows every label in [pair] from now on (and logs missing keys in debug builds); returns it. */
    fun apply(): LangPair {
        if (BuildConfig.DEBUG && L10n.onMissing == null) L10n.onMissing = { lang, key -> Log.w("L10n", "${lang.code}.json has no $key") }
        return pair.also { L10n.pair = it }
    }

    private companion object {
        const val CHOSEN = "chosen_pair"
        const val PROFILE = "profile_pair"
    }
}
