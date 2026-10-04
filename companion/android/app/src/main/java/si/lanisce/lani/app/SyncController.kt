package si.lanisce.lani.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.FlushReport
import si.lanisce.lani.data.Outbox
import si.lanisce.lani.data.OutboxEntry
import si.lanisce.lani.data.deliver
import si.lanisce.lani.l10n.bi

/**
 * Writes for the node (reviews, new words, messages) go through the [Outbox]: queued on disk,
 * sent in order, retried with backoff while the node can't be reached.
 */
class SyncController(
    context: Context,
    private val scope: CoroutineScope,
    private val bridge: () -> Bridge?,
    private val notices: Notices,
    /** Reviews or new words reached the node, so its databases changed. */
    private val onSaved: () -> Unit,
) {
    private val outbox = Outbox(context)
    private var retryJob: Job? = null

    /** Writes queued in the outbox while the node was unreachable. */
    var pending by mutableIntStateOf(0)
        private set

    init {
        scope.launch { pending = withContext(Dispatchers.IO) { outbox.size } }
    }

    /** Queues [e] and sends everything waiting, in order. True when [e] reached the node. */
    suspend fun submit(e: OutboxEntry): Boolean {
        withContext(Dispatchers.IO) { outbox.enqueue(e) }
        return flush()?.sent?.any { it.clientId == e.clientId } == true
    }

    /** [submit] in the background; a failure goes to the snackbar. */
    fun submitLater(e: OutboxEntry): Job = scope.launchSafely(notices::fail) { submit(e) }

    /** Sends what is queued; [force] ignores the backoff (the network just came back). */
    suspend fun flush(force: Boolean = true): FlushReport? {
        val b = bridge() ?: return null
        val r = withContext(Dispatchers.IO) { outbox.flush(force) { b.deliver(it) } }
        pending = r.remaining
        r.dropped.firstOrNull()?.let { (e, why) -> notices.error = bi("syncController.notSavedWhy", "eLabel" to e.label, "why" to why) }
        // a node from before a route: it went the older way (the level change to the tutor), and the learner is told so
        r.fellBack.firstOrNull()?.let { e -> notices.banner = "📨 ${bi("syncController.olderNode", "eLabel" to e.label)}" }
        if (r.remaining > 0) scheduleRetry()
        if (r.sent.any { it.path != "/message" }) onSaved()
        return r
    }

    private fun scheduleRetry() {
        if (retryJob?.isActive == true) return
        retryJob = scope.launch {
            delay(withContext(Dispatchers.IO) { outbox.nextRetryIn() } ?: return@launch)
            retryJob = null
            flush(force = false)
        }
    }
}
