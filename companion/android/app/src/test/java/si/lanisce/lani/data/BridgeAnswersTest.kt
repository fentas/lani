package si.lanisce.lani.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.lanisce.lani.l10n.Learner
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The bridge's answers read once while they stay the same (docs/plans/05-built/home-return.md): Home reads everything anew
 * each time it comes back, and an answer the same as the last one gives back the value read then (the same instance:
 * nothing parsed, nothing for the screen's state to compare); a changed answer, another learner, a route an older bridge
 * hasn't (404: none) and a failure (its status named) as before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BridgeAnswersTest {
    /** The node: each path's body (changed by the test), and how many times each was asked. */
    private val bodies = ConcurrentHashMap<String, Pair<Int, String>>()
    private val asked = ConcurrentHashMap<String, AtomicInteger>()
    private val server = MockWebServer().also {
        it.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                asked.getOrPut(path) { AtomicInteger() }.incrementAndGet()
                val (code, body) = bodies[path] ?: (404 to """{"error":"not found"}""")
                return MockResponse().setResponseCode(code).setBody(body)
            }
        }
    }
    private val bridge = Bridge(BridgeConfig(server.url("/").toString(), "token"))
    private val learner = Learner.current

    private fun state(name: String, today: String = "2026-10-28") =
        """{"databases":{"learner_profile":{"learner":{"name":"$name","current_level":"A2"}}},"computed":{"today":"$today"}}"""

    @After fun tearDown() {
        Learner.current = learner
        server.shutdown()
    }

    @Test fun `an answer the same as the last is not read again, the value read then comes back`() = runBlocking {
        bodies["/state"] = 200 to state("Ana")
        val first = bridge.dashboard()
        val again = bridge.dashboard()
        assertEquals(2, asked["/state"]!!.get()) // asked each time: the node may have something new
        assertSame("the same answer: the same value, not read again", first, again)
        assertEquals("Ana", again.name)

        bodies["/state"] = 200 to state("Ana", today = "2026-10-29")
        val changed = bridge.dashboard()
        assertNotSame("a changed answer is read anew", first, changed)
        assertSame(changed, bridge.dashboard())
    }

    @Test fun `another learner reads the same answer anew, said to them`() = runBlocking {
        bodies["/state"] = 200 to state("{learner}")
        Learner.current = Learner.of("Ana", "f")
        val ana = bridge.dashboard()
        assertEquals("Ana", ana.name)
        Learner.current = Learner.of("Marko", "m")
        val marko = bridge.dashboard()
        assertNotSame(ana, marko)
        assertEquals("Marko", marko.name)
    }

    @Test fun `a route an older bridge hasn't is none, a failure names its status, neither kept`() = runBlocking {
        assertNull(bridge.grammar())
        assertNull(bridge.grammar())
        bodies["/grammar"] = 200 to "[]"
        assertEquals(emptyList<GrammarPage>(), bridge.grammar())

        bodies["/course"] = 500 to """{"error":"boom"}"""
        try {
            bridge.course()
            fail("a 500 is a failure")
        } catch (e: IOException) {
            assertTrue(e.message, e.message!!.startsWith("HTTP 500: "))
        }
        bodies["/scenes"] = 503 to "busy"
        try {
            bridge.scenes()
            fail("a 503 is a failure")
        } catch (e: IOException) {
            assertEquals("HTTP 503: busy", e.message)
        }
        bodies["/scenes"] = 200 to "[]"
        assertEquals(0, bridge.scenes().size)
    }
}
