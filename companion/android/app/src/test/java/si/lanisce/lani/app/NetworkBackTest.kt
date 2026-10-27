package si.lanisce.lani.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import si.lanisce.lani.tutor.Online
import si.lanisce.lani.tutor.OnlineState

/**
 * The network back while the app is on screen (app/NetworkBack): told on each coming back after the network was down,
 * from the one watcher the tutors' queues use too ([Online]); not for the state at start, not while stopped.
 */
class NetworkBackTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After fun done() = scope.cancel()

    @Test fun `the network back after it was down is told, each time, not the state at start, not while stopped`() {
        val online = OnlineState(true)
        var back = 0
        val n = NetworkBack(scope, online) { back++ }
        n.start()
        assertEquals("online at start: nothing", 0, back)
        online.set(false)
        online.set(true)
        assertEquals(1, back)
        online.set(true) // no change
        assertEquals(1, back)
        online.set(false)
        online.set(true)
        assertEquals(2, back)
        n.stop()
        online.set(false)
        online.set(true)
        assertEquals("stopped: the app off screen", 2, back)
        // started again while offline: its coming back counts
        online.set(false)
        n.start()
        online.set(true)
        assertEquals(3, back)
    }

    @Test fun `a watcher that knows nothing (always online) tells nothing`() {
        var back = 0
        NetworkBack(scope, Online.ALWAYS) { back++ }.start()
        assertEquals(0, back)
    }
}
