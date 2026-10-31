package si.lanisce.lani.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import si.lanisce.lani.BuildConfig
import si.lanisce.lani.data.NotifyChannels
import si.lanisce.lani.data.Notifier
import si.lanisce.lani.data.Prefs
import si.lanisce.lani.data.Seen

/**
 * The app's side of the notifications (companion/README.md, "Notifications"): the channels made at the start and named anew
 * with the pair, what the learner sees on screen taken away ([seen]: the launcher's dot goes with it), and "🔔 Večerni
 * opomnik · Evening reminder" ([reminder]), which the background check reads.
 */
class NotifyController(private val context: Context, private val scope: CoroutineScope, private val prefs: Prefs) {
    /** The learner's choice of the evening reminder; null: never set (on for an adult, off for a child). */
    var reminder by mutableStateOf<Boolean?>(null)
        private set

    init {
        scope.launch(Dispatchers.IO) {
            Notifier.ensureChannels(context)
            // an update's notification once it is installed
            Notifier.cancelIf(context) { NotifyChannels.updateInstalled(it, BuildConfig.VERSION_CODE) }
        }
        scope.launch { reminder = prefs.eveningReminder() }
    }

    /** Whether the evening reminder is on for this learner ([child]: a child's profile). */
    fun reminderOn(child: Boolean): Boolean = NotifyChannels.reminderOn(reminder, child)

    fun setReminder(on: Boolean) {
        reminder = on
        scope.launch { prefs.saveEveningReminder(on) }
    }

    /** The learner's pair changed: the channels' names in the phone's settings follow. */
    fun renamed() {
        scope.launch(Dispatchers.IO) { Notifier.ensureChannels(context) }
    }

    /** Whether the learner is a child, kept for the background check (which has no view model to ask). */
    fun child(child: Boolean) {
        scope.launch { prefs.saveLearnerChild(child) }
    }

    /** The learner sees what [what] matches: its notifications go. */
    fun seen(what: List<Seen>) {
        if (what.isEmpty()) return
        scope.launch(Dispatchers.IO) { Notifier.cancel(context, what) }
    }
}
