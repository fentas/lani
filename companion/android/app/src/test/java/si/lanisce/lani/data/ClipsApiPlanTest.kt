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
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Which requests of [ClipsApi] follow the plan and which stay on the connection, asked anew at each ([AudioPlan]). */
class ClipsApiPlanTest {
    private class Stub(val path: (RecordedRequest) -> MockResponse) : Dispatcher() {
        override fun dispatch(request: RecordedRequest) = path(request)
    }

    /** A plan whose answer the test changes, as an end in the middle of a session does. */
    private class Plan(var to: BridgeConfig?) : VoicePlan {
        var ends = 0
        override fun voices(connection: BridgeConfig) = to ?: connection
        override fun keepsHeard(connection: BridgeConfig) = to == null
        override fun ended() {
            ends++
            to = null
        }
    }

    private val node = MockWebServer()
    private val glas = MockWebServer()
    private lateinit var connection: BridgeConfig
    private lateinit var trial: BridgeConfig

    private fun url(s: MockWebServer) = s.url("/").toString().trimEnd('/')

    @Before fun start() {
        node.dispatcher = Stub { r ->
            when {
                r.path == "/voice/say" -> MockResponse().setResponseCode(204)
                r.path == "/voice/prepare" -> MockResponse().setBody("""{"clips":[{"text":"a","voice":"female","url":null}]}""")
                r.path == "/voice/index" -> MockResponse().setBody("{}")
                r.path == "/voice/profiles" -> MockResponse().setBody("""{"people":{}}""")
                r.path == "/voice/redo" -> MockResponse().setBody("""{"status":"failed","message":"no voices"}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        glas.dispatcher = Stub { r ->
            when {
                r.path == "/v1/app/sl/voice/say" -> MockResponse().setBody("""{"url":"/voice/file/abc.mp3","voice":"grandma","engine":"glas"}""")
                r.path == "/v1/app/sl/voice/prepare" -> MockResponse().setBody("""{"clips":[{"text":"a","voice":"female","url":"/voice/file/abc.mp3"}]}""")
                r.path == "/v1/app/sl/voice/index" -> MockResponse().setBody("""{"dober dan":{"female":"/voice/file/abc.mp3"}}""").setHeader("ETag", "\"one\"")
                r.path == "/v1/app/sl/voice/file/abc.mp3" -> MockResponse().setBody("clip")
                else -> MockResponse().setResponseCode(404)
            }
        }
        node.start()
        glas.start()
        connection = BridgeConfig(url(node), "phone-token")
        trial = BridgeConfig("${url(glas)}/v1/app/sl", "lani_0123")
    }

    @After fun stop() {
        node.shutdown()
        glas.shutdown()
    }

    private fun temp(): File = Files.createTempDirectory("clips-plan").toFile().resolve("abc.mp3")

    @Test fun `index, say, prepare and a clip's file go to the plan, with its token`() = runBlocking {
        val api = ClipsApi(connection, plan = Plan(trial))
        assertTrue(api.planned)
        assertEquals(trial, api.voices)
        assertEquals("""{"dober dan":{"female":"/voice/file/abc.mp3"}}""", api.index())
        assertEquals("/voice/file/abc.mp3" to "grandma", api.say("Dober dan", "grandma"))
        assertEquals(listOf("/voice/file/abc.mp3"), api.prepare(listOf("a" to "female")))
        val to = temp()
        api.download("/voice/file/abc.mp3", to)
        assertEquals("clip", to.readText())
        assertEquals(4, glas.requestCount)
        assertEquals(0, node.requestCount)
        repeat(4) { assertEquals("Bearer lani_0123", glas.takeRequest().getHeader("Authorization")) }
    }

    @Test fun `voice profiles and a re-record stay on the connection`() = runBlocking {
        val api = ClipsApi(connection, plan = Plan(trial))
        assertEquals("""{"people":{}}""", api.profiles())
        assertEquals("""{"people":{}}""".length, api.profiles(emptyList())?.length)
        api.redo("Dober dan", "grandma")
        assertEquals(0, glas.requestCount)
        assertEquals(3, node.requestCount)
        repeat(3) { assertEquals("Bearer phone-token", node.takeRequest().getHeader("Authorization")) }
    }

    @Test fun `a visit's routes stay as they are`() = runBlocking {
        val api = ClipsApi(connection, ClipRoutes.visit("ti-1"), Plan(trial))
        assertFalse(api.planned)
        assertEquals(connection, api.voices)
        assertFalse(api.keepsHeard)
        runCatching { api.index() }
        runCatching { api.say("Dober dan", "female") }
        runCatching { api.ahead(listOf(ClipAsk("Dober dan", "female"))) }
        assertEquals(0, glas.requestCount)
        assertEquals(listOf("/towns/ti-1/voice/index", "/towns/ti-1/voice/say", "/towns/ti-1/voice/prepare"), List(3) { node.takeRequest().path })
    }

    @Test fun `a plan with nothing to say leaves the connection as it was`() = runBlocking {
        val api = ClipsApi(connection, plan = Plan(null))
        assertFalse(api.planned)
        assertEquals("{}", api.index())
        assertNull(api.say("Dober dan", "female"))
        assertEquals(listOf<String?>(null), api.prepare(listOf("a" to "female")))
        assertEquals(0, glas.requestCount)
    }

    @Test fun `every request asks again, so an end in the middle of a session takes effect`() = runBlocking {
        val plan = Plan(trial)
        val api = ClipsApi(connection, plan = plan)
        assertEquals("/voice/file/abc.mp3" to "grandma", api.say("Dober dan", "grandma"))
        plan.to = null
        assertNull("the phone's bridge has no clip: the phone speaks", api.say("Dober dan", "grandma"))
        assertEquals(1, glas.requestCount)
        assertEquals(1, node.requestCount)
    }

    private fun ended() = MockResponse().setResponseCode(403).setBody("""{"error":{"code":"trial_ended","message":"over"}}""")

    @Test fun `a 403 trial_ended from any voice route ends it at once`() = runBlocking {
        val asks: List<suspend (ClipsApi) -> Unit> = listOf(
            { it.index() }, { it.indexIfChanged(null) }, { it.say("a", "female") }, { it.prepare(listOf("a" to "female")) }, { it.download("/voice/file/abc.mp3", temp()) },
        )
        glas.dispatcher = Stub { ended() }
        for (ask in asks) {
            val plan = Plan(trial)
            val api = ClipsApi(connection, plan = plan)
            assertTrue(runCatching { ask(api) }.exceptionOrNull() is IOException)
            assertEquals(1, plan.ends)
            assertFalse("the connection answers from now on", api.planned)
        }
    }

    @Test fun `a 403 of another kind ends nothing`() = runBlocking {
        glas.dispatcher = Stub { MockResponse().setResponseCode(403).setBody("<Error><Code>AccessDenied</Code></Error>") }
        val plan = Plan(trial)
        val api = ClipsApi(connection, plan = plan)
        assertTrue(runCatching { api.download("/voice/file/abc.mp3", temp()) }.isFailure)
        assertEquals(0, plan.ends)
    }

    @Test fun `the phone's bridge saying it never ends anything`() = runBlocking {
        node.dispatcher = Stub { ended() }
        val plan = Plan(null)
        assertTrue(runCatching { ClipsApi(connection, plan = plan).index() }.isFailure)
        assertEquals(0, plan.ends)
    }

    @Test fun `a clip's file follows a redirect to another host and leaves the token behind`() = runBlocking {
        val store = MockWebServer().apply { dispatcher = Stub { MockResponse().setBody("clip from the store") }; start() }
        try {
            glas.dispatcher = Stub { MockResponse().setResponseCode(302).setHeader("Location", store.url("/signed/abc.mp3?sig=x")) }
            val to = temp()
            ClipsApi(connection, plan = Plan(trial)).download("/voice/file/abc.mp3", to)
            assertEquals("clip from the store", to.readText())
            assertNull("no token on the way", store.takeRequest().getHeader("Authorization"))
        } finally {
            store.shutdown()
        }
    }

    @Test fun `the trial's routes get the install's token, an empty body and nothing else of the device`() = runBlocking {
        glas.dispatcher = Stub { MockResponse().setBody("""{"status":"closed"}""") }
        val api = TrialApi("${url(glas)}/", "0123")
        assertEquals(TrialAnswer(AudioTrial.CLOSED), api.start())
        assertEquals(TrialAnswer(AudioTrial.CLOSED), api.read())
        val post = glas.takeRequest()
        assertEquals("POST /v1/trial HTTP/1.1", post.requestLine)
        assertEquals("{}", post.body.readUtf8())
        assertEquals("Bearer lani_0123", post.getHeader("Authorization"))
        val allowed = setOf("authorization", "content-type", "content-length", "host", "connection", "accept-encoding", "user-agent")
        assertEquals(emptySet<String>(), post.headers.names().map { it.lowercase() }.toSet() - allowed)
        assertEquals("GET /v1/trial HTTP/1.1", glas.takeRequest().requestLine)
    }

    @Test fun `a trial route that fails says nothing`() = runBlocking {
        glas.dispatcher = Stub { MockResponse().setResponseCode(401).setBody("""{"error":{"code":"bad_token"}}""") }
        assertNull(TrialApi(url(glas), "0123").start())
    }
}
