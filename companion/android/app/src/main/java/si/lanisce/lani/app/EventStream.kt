package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import si.lanisce.lani.Link
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.BridgeEvent
import si.lanisce.lani.data.Prefs
import si.lanisce.lani.data.Unpaired
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The node's live events (SSE), only while the app is on screen: in the background the
 * [si.lanisce.lani.data.BackgroundCheck] turns them into notifications instead. Every connection
 * resumes after the last event handled, and a replayed event is never handled twice.
 */
class EventStream(
    private val scope: CoroutineScope,
    private val prefs: Prefs,
    private val bridge: () -> Bridge?,
    private val notices: Notices,
    /** Connected: a good moment to send what waited. */
    private val onOpen: () -> Unit,
    private val onEvent: (BridgeEvent) -> Unit,
) {
    var link by mutableStateOf(Link.CONNECTING)
        private set
    /** The node refused this phone's token: it was unpaired. Home offers to pair again. */
    var unpaired by mutableStateOf(false)
        private set
    private var job: Job? = null

    /** Connects unless connected already (app back on screen, or connected to a node). */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { listen() }
    }

    /** Disconnects (app in the background); [start] resumes where it stopped. */
    fun stop() {
        job?.cancel()
        job = null
    }

    /** Connects afresh, e.g. after the bridge config changed. */
    fun restart() {
        stop()
        start()
    }

    private suspend fun listen() {
        val cursor = EventCursor(prefs.lastEventId())
        var failures = 0
        while (currentCoroutineContext().isActive) {
            val b = bridge() ?: return
            link = Link.CONNECTING
            val opened = AtomicBoolean(false)
            try {
                b.events(cursor.lastId, onOpen = {
                    opened.set(true)
                    scope.launch { link = Link.ONLINE; unpaired = false; onOpen() }
                }).collect { (id, ev) ->
                    link = Link.ONLINE
                    if (!cursor.accept(id)) return@collect // replayed after a reconnect: handled already
                    if (id != null) {
                        prefs.saveLastEventId(id)
                        prefs.saveNotifiedEventId(id) // seen in the app; the background check won't notify it again
                    }
                    try {
                        onEvent(ev)
                    } catch (e: Exception) { // one bad event must not end the stream
                        notices.fail(e)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Unpaired) {
                unpaired = true // keeps trying: the token may come back (a restored devices.json)
            } catch (e: Exception) {
                // the stream failed; reconnect below
            }
            link = Link.OFFLINE
            failures = if (opened.get()) 0 else failures + 1
            delay(reconnectDelay(failures))
        }
    }

    companion object {
        /** 3 s after a connection that worked, then 3, 6, 12 … s up to a minute while the node stays unreachable. */
        fun reconnectDelay(failures: Int): Long = if (failures <= 0) 3_000L else (3_000L shl (failures - 1).coerceAtMost(5)).coerceAtMost(60_000L)
    }
}

/**
 * Where the event stream resumes, and its duplicate filter. The bridge numbers events in increasing
 * order across restarts (seeded from the clock), so an id at or below the last one was handled already.
 */
class EventCursor(lastId: Long) {
    var lastId = lastId
        private set

    /** True when the event is new, and moves the cursor past it. Events without an id always pass. */
    fun accept(id: Long?): Boolean {
        if (id == null) return true
        if (id <= lastId) return false
        lastId = id
        return true
    }
}
