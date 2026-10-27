package si.lanisce.lani.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import si.lanisce.lani.tutor.Online

/**
 * The phone's network back after it was down, while the app is on screen ([start] … [stop]): [onBack] then. The app sends
 * its outbox at once (its backoff ignored) and reconnects the live events, instead of waiting for the outbox's next retry
 * (minutes after a long time offline) or an event stream that didn't notice the network go: a word asked for offline
 * ("➕ Dodaj med moje besede") reaches the tutor as soon as the phone is online. [online] is the process's one network
 * watcher ([si.lanisce.lani.tutor.live.AndroidOnline], which the tutors' queues wait on too); only a change counts, not the
 * state at [start].
 */
class NetworkBack(private val scope: CoroutineScope, private val online: Online, private val onBack: () -> Unit) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var was: Boolean? = null
            online.changes.collect { on ->
                if (on && was == false) onBack()
                was = on
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
