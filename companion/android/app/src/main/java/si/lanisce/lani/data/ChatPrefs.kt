package si.lanisce.lani.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What the tutor chat remembers on this phone: whether its tip about swiping a message was shown ([swipeHintSeen]). */
class ChatPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("chat", Context.MODE_PRIVATE)

    /** "← zaznamek · odgovor →" under the tutor's latest message, once: dismissed, a swipe done, or the chat closed. */
    var swipeHintSeen by mutableStateOf(prefs.getBoolean(KEY_SWIPE_HINT, false))
        private set

    fun sawSwipeHint() {
        if (swipeHintSeen) return
        swipeHintSeen = true
        prefs.edit().putBoolean(KEY_SWIPE_HINT, true).apply()
    }

    private companion object {
        const val KEY_SWIPE_HINT = "swipe_hint_seen"
    }
}
