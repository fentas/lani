package si.lanisce.lani.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap

/**
 * What each read says of the bridge's areas (docs/plans/05-built/changes-areas.md, [Areas]): an answer's lani-areas kept by
 * its URL, fresh while every area it names is still at its version by GET /changes; an answer without them (an older
 * bridge, a read without areas, an error) forgets them, an answer that never came keeps them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AreasTest {
    /** The node: each path's status, body and lani-areas (null: none). */
    private val answers = ConcurrentHashMap<String, Triple<Int, String, String?>>()
    private val server = MockWebServer().also {
        it.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val (code, body, areas) = answers[request.requestUrl!!.encodedPath] ?: Triple(404, """{"error":"not found"}""", null)
                return MockResponse().setResponseCode(code).setBody(body).apply { areas?.let { setHeader("lani-areas", it) } }
            }
        }
    }
    private val bridge = Bridge(BridgeConfig(server.url("/").toString(), "token"))

    @Before fun setUp() = Areas.clear()

    @After fun tearDown() {
        Areas.clear()
        server.shutdown()
    }

    @Test fun `the header reads as each area's version`() {
        assertEquals(mapOf("learner" to "L1", "content" to "C-1_x"), Areas.parse("learner=L1, content=C-1_x"))
        assertEquals(emptyMap<String, String>(), Areas.parse(""))
        assertNull("no version", Areas.parse("learner="))
        assertNull("no name", Areas.parse("=L1"))
    }

    @Test fun `a read is fresh while the areas it said are at their versions`() = runBlocking {
        answers["/packs"] = Triple(200, "[]", "learner=L1, content=C1")
        bridge.packs()
        assertTrue(bridge.known(Bridge.PACKS))
        assertTrue(bridge.fresh(Bridge.PACKS, mapOf("learner" to "L1", "content" to "C1", "village" to "V9")))
        assertFalse("the learner's data moved", bridge.fresh(Bridge.PACKS, mapOf("learner" to "L2", "content" to "C1")))
        assertFalse("the content moved", bridge.fresh(Bridge.PACKS, mapOf("learner" to "L1", "content" to "C2")))
        assertFalse("a /changes without an area it said", bridge.fresh(Bridge.PACKS, mapOf("content" to "C1")))
        assertFalse("another read was never answered", bridge.fresh(Bridge.MODULES, mapOf("learner" to "L1", "content" to "C1")))
    }

    @Test fun `an answer without its areas forgets them, a 404 says them, a 500 not`() = runBlocking {
        answers["/grammar"] = Triple(200, "[]", "content=C1")
        bridge.grammar()
        assertTrue(bridge.fresh(Bridge.GRAMMAR, mapOf("content" to "C1")))
        // an older bridge (or one that says none now): never fresh
        answers["/grammar"] = Triple(200, "[]", null)
        bridge.grammar()
        assertFalse(bridge.known(Bridge.GRAMMAR))
        assertFalse(bridge.fresh(Bridge.GRAMMAR, mapOf("content" to "C1")))
        // no course for this language is an answer too
        answers["/course"] = Triple(404, """{"error":"no course"}""", "content=C1")
        assertNull(bridge.course())
        assertTrue(bridge.fresh(Bridge.COURSE, mapOf("content" to "C1")))
        // an error is asked again
        answers["/course"] = Triple(500, "oops", "content=C1")
        runCatching { bridge.course() }
        assertFalse(bridge.fresh(Bridge.COURSE, mapOf("content" to "C1")))
    }

    @Test fun `a read that got no answer keeps what it had, another node is another URL`() = runBlocking {
        answers["/cards"] = Triple(200, "[]", "content=C1")
        bridge.cards()
        server.shutdown()
        runCatching { bridge.cards() } // offline
        assertTrue("what was read then is still what the phone has", bridge.fresh(Bridge.CARDS, mapOf("content" to "C1")))
        val other = Bridge(BridgeConfig("http://127.0.0.1:9/", "token"))
        assertFalse(other.fresh(Bridge.CARDS, mapOf("content" to "C1")))
    }

    @Test fun `a write's answer says nothing`() = runBlocking {
        answers["/message"] = Triple(200, """{"msg_id":"m1"}""", "learner=L1")
        bridge.send("POST", "/message", "{}")
        assertFalse(bridge.known("/message"))
    }
}
