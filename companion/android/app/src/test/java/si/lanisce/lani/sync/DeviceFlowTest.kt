package si.lanisce.lani.sync

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** GitHub's OAuth device flow against a fake github.com: the code, pending, slow down, the token; refused; ran out. */
class DeviceFlowTest {
    private var answers = ArrayDeque<String>()
    private val bodies = mutableListOf<String>()
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                bodies += "${request.path} $body"
                if (request.getHeader("Accept") != "application/json") return MockResponse().setResponseCode(406)
                return when (request.path) {
                    "/login/device/code" -> MockResponse().setBody("""{"device_code":"dc-1","user_code":"WDJB-MJHT","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}""")
                    "/login/oauth/access_token" -> MockResponse().setBody(answers.removeFirstOrNull() ?: """{"error":"authorization_pending"}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }
    private var now = Instant.parse("2026-10-18T21:00:00Z")
    private val slept = mutableListOf<Long>()
    private fun flow() = DeviceFlow("Iv1.testclient", server.url("/").toString().trimEnd('/'), clock = { now }, sleep = { slept += it; now = now.plusMillis(it) })

    @After fun tearDown() = server.shutdown()

    @Test fun `a code, then pending and slow down, then the token`() = runBlocking {
        val f = flow()
        assertTrue(f.available)
        val code = f.start()
        assertEquals("WDJB-MJHT", code.userCode)
        assertTrue(bodies.first().contains("client_id=Iv1.testclient") && bodies.first().contains("scope=repo"))
        answers.addAll(listOf("""{"error":"authorization_pending"}""", """{"error":"slow_down","interval":10}""", """{"access_token":"gho_abc","token_type":"bearer","scope":"repo"}"""))
        val r = f.await(code)
        assertEquals(DeviceFlow.Result.Token("gho_abc", "repo"), r)
        assertEquals(listOf(5_000L, 5_000L, 10_000L), slept)
        assertTrue(bodies.last().contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code") && bodies.last().contains("device_code=dc-1"))
    }

    @Test fun `refused, ran out, or given up`() = runBlocking {
        val f = flow()
        answers.add("""{"error":"access_denied"}""")
        assertEquals(DeviceFlow.Result.Denied, f.await(f.start()))
        answers.add("""{"error":"expired_token"}""")
        assertEquals(DeviceFlow.Result.Expired, f.await(f.start()))
        val code = f.start()
        now = now.plusSeconds(1000)
        assertEquals(DeviceFlow.Result.Expired, f.await(code))
        assertEquals(DeviceFlow.Result.Denied, f.await(f.start(), stop = { true }))
        assertEquals(false, DeviceFlow("").available)
    }
}
