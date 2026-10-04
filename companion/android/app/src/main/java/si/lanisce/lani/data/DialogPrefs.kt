package si.lanisce.lani.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the scene dialogs remember on this phone: whether the lines and choices show their translations
 * ([translations]: "👁 Prevod · Translation" in a dialog's header; on by default), and whether the hint about tapping
 * words was shown.
 */
class DialogPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("dialog", Context.MODE_PRIVATE)

    var translations by mutableStateOf(prefs.getBoolean(KEY_TRANSLATIONS, true))
        private set

    var wordHintSeen by mutableStateOf(prefs.getBoolean(KEY_WORD_HINT, false))
        private set

    fun showTranslations(on: Boolean) {
        translations = on
        prefs.edit().putBoolean(KEY_TRANSLATIONS, on).apply()
    }

    fun sawWordHint() {
        if (wordHintSeen) return
        wordHintSeen = true
        prefs.edit().putBoolean(KEY_WORD_HINT, true).apply()
    }

    private companion object {
        const val KEY_TRANSLATIONS = "show_translations"
        const val KEY_WORD_HINT = "word_hint_seen"
    }
}
