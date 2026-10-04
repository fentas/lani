package si.lanisce.lani.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class NoticesTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Test fun `launchSafely reports a failure instead of crashing`() = runBlocking {
        var reported: Exception? = null
        scope.launchSafely({ reported = it }) { throw IOException("offline") }.join()
        assertEquals("offline", reported?.message)
    }

    @Test fun `cancelling is not an error`() = runBlocking {
        var reported: Exception? = null
        val job = scope.launchSafely({ reported = it }) { awaitCancellation() }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertNull(reported)
    }

    @Test fun `a village bug becomes a message and null`() {
        val n = Notices()
        val r: Int? = n.village { error("no such building") }
        assertNull(r)
        assertEquals("Vas · Village: no such building", n.error)
        assertEquals(7, n.village { 7 })
    }
}
