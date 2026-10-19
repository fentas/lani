package si.lanisce.lani.tutor.live

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.tutor.ApiError
import si.lanisce.lani.tutor.Effort
import si.lanisce.lani.tutor.FAKE_KEY
import si.lanisce.lani.tutor.obj
import si.lanisce.lani.tutor.userText
import java.net.InetAddress

/**
 * Anthropic's Message Batches API against a fake (plan 5, phase C: "Batches for what can wait"): what a create sends
 * (the custom ids, the params made non-streaming, the key, the betas), a batch in progress then ended, its results by
 * custom_id in any order, cancel, the API's errors, the custom_id rule and the key's guard on results_url.
 */
class BatchesTest {
    private val api = FakeBatches()
    private val base = "http://127.0.0.1:$PORT"

    @Before fun start() = api.server.start(InetAddress.getByName("127.0.0.1"), PORT)

    @After fun stop() = api.server.shutdown()

    private fun batches(key: String? = FAKE_KEY, at: String = base) = AnthropicBatches(at, { key })

    private val tool = obj("name" to "reply", "description" to "Say it.", "input_schema" to obj("type" to "object"), "eager_input_streaming" to true)

    private fun params(vararg messages: JsonObject) = obj(
        "model" to "claude-opus-5-5",
        "max_tokens" to 16_000,
        "stream" to true,
        "tools" to JsonArray(listOf(tool, obj("type" to "web_search_20260209", "name" to "web_search"))),
        "messages" to JsonArray(messages.toList()),
    )

    @Test fun `create sends the custom ids, the params without stream or eager_input_streaming, the key and the betas`() = runBlocking {
        val b = batches().create(listOf("story-1" to params(userText("Write a story.")), "variant_2" to params(userText("A variant."))))
        assertEquals("msgbatch_01", b.id)
        assertEquals(Batch.IN_PROGRESS, b.status)
        assertEquals(mapOf("processing" to 2, "succeeded" to 0, "errored" to 0, "canceled" to 0, "expired" to 0), b.counts)
        assertNull(b.resultsUrl)
        assertEquals("2026-10-19T08:00:00Z", b.createdAt)
        assertEquals("2026-10-20T08:00:00Z", b.expiresAt)
        assertNull(b.endedAt)

        val r = api.requests.single()
        assertEquals("POST", r.method)
        assertEquals("/v1/messages/batches", r.path)
        assertEquals(FAKE_KEY, r.headers["x-api-key"])
        assertEquals("2023-06-01", r.headers["anthropic-version"])
        assertEquals("thinking-binding-controls-2026-08-01", r.headers["anthropic-beta"])
        assertTrue(r.headers["content-type"]!!.startsWith("application/json"))
        val sent = Json.parseToJsonElement(r.body).jsonObject["requests"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("story-1", "variant_2"), sent.map { it["custom_id"]!!.jsonPrimitive.content })
        for (s in sent) {
            val p = s["params"]!!.jsonObject
            assertFalse("no stream in a batch", "stream" in p)
            val tools = p["tools"]!!.jsonArray.map { it.jsonObject }
            assertTrue(tools.none { "eager_input_streaming" in it })
            assertEquals(JsonObject(tool - "eager_input_streaming"), tools[0])
            assertEquals("web_search", tools[1]["name"]!!.jsonPrimitive.content)
            assertEquals("claude-opus-5-5", p["model"]!!.jsonPrimitive.content)
            assertEquals(16_000, p["max_tokens"]!!.jsonPrimitive.content.toInt())
        }
        assertEquals("Write a story.", sent[0]["params"]!!.jsonObject["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test fun `the effort beta goes with a batch only when some request has an effort message`() = runBlocking {
        batches().create(listOf("a" to params(userText("x")), "b" to params(userText("y"), Effort.message("high"))))
        assertEquals("thinking-binding-controls-2026-08-01,${Effort.BETA}", api.requests.single().headers["anthropic-beta"])
        assertEquals("mid-conversation-output-config-2026-07-01", Effort.BETA)
        // the effort message itself is sent as given
        val b = Json.parseToJsonElement(api.requests.single().body).jsonObject["requests"]!!.jsonArray[1].jsonObject
        assertEquals(Effort.message("high"), b["params"]!!.jsonObject["messages"]!!.jsonArray[1])
        api.requests.clear()
        AnthropicBatches(base, { FAKE_KEY }, betas = emptyList()).create(listOf("c" to params(userText("z"))))
        assertNull("no betas: no header", api.requests.single().headers["anthropic-beta"])
    }

    @Test fun `get - in progress, then ended`() = runBlocking {
        val bs = batches()
        val created = bs.create(listOf("a" to params(userText("x")), "b" to params(userText("y")), "c" to params(userText("z")), "d" to params(userText("w"))))
        val running = bs.get(created.id)
        assertEquals(Batch.IN_PROGRESS, running.status)
        assertFalse(running.ended)
        api.ended = true
        val ended = bs.get(created.id)
        assertTrue(ended.ended)
        assertEquals(mapOf("processing" to 0, "succeeded" to 1, "errored" to 1, "canceled" to 1, "expired" to 1), ended.counts)
        assertEquals("$base/v1/messages/batches/msgbatch_01/results", ended.resultsUrl)
        assertEquals("2026-10-19T09:00:00Z", ended.endedAt)
        assertEquals(listOf("GET /v1/messages/batches/msgbatch_01", "GET /v1/messages/batches/msgbatch_01"), api.requests.drop(1).map { "${it.method} ${it.path}" })
        assertTrue(api.requests.all { it.headers["x-api-key"] == FAKE_KEY })
    }

    @Test fun `results come in any order and are matched by custom_id`() = runBlocking {
        val bs = batches()
        val submitted = listOf("a", "b", "c", "d")
        bs.create(submitted.map { it to params(userText(it)) })
        api.ended = true
        val results = bs.results(bs.get("msgbatch_01"))
        assertEquals(listOf("d", "b", "a", "c"), results.map { it.customId }) // as the API sent them, blank lines skipped
        val by = results.associateBy { it.customId }
        assertEquals(submitted.toSet(), by.keys)
        assertEquals("succeeded", by.getValue("a").type)
        assertEquals("msg_a", by.getValue("a").message!!["id"]!!.jsonPrimitive.content)
        assertNull(by.getValue("a").error)
        val errored = by.getValue("b")
        assertEquals("errored", errored.type)
        assertNull(errored.message)
        assertEquals("error", errored.error!!["type"]!!.jsonPrimitive.content)
        assertEquals("invalid_request_error", errored.error!!["error"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("max_tokens: too large", errored.error!!["error"]!!.jsonObject["message"]!!.jsonPrimitive.content)
        assertEquals("canceled", by.getValue("c").type)
        assertEquals("expired", by.getValue("d").type)
        assertNull(by.getValue("d").message)
        val get = api.requests.last()
        assertEquals("/v1/messages/batches/msgbatch_01/results", get.path)
        assertEquals("the results URL is the API's: the key goes with it", FAKE_KEY, get.headers["x-api-key"])
    }

    @Test fun `cancel`() = runBlocking {
        val b = batches().cancel("msgbatch_01")
        assertEquals(Batch.CANCELING, b.status)
        assertEquals("POST /v1/messages/batches/msgbatch_01/cancel", api.requests.single().let { "${it.method} ${it.path}" })
        assertEquals(FAKE_KEY, api.requests.single().headers["x-api-key"])
    }

    @Test fun `the API's errors - 401 is Auth, 529 Overloaded, no answer Network, no key Auth`() = runBlocking {
        api.status = 401 to """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""
        expect<ApiError.Auth> { batches().get("msgbatch_01") }
        api.status = 529 to """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"""
        val e = expect<ApiError.Overloaded> { batches().create(listOf("a" to params(userText("x")))) }
        assertEquals(529, e.code)
        api.status = 429 to """{"type":"error","error":{"type":"rate_limit_error","message":"slow down"}}"""
        assertEquals(5_000L, expect<ApiError.RateLimited> { batches().cancel("msgbatch_01") }.retryAfterMs)
        api.status = null
        api.ended = true
        api.resultsStatus = 500
        expect<ApiError.Overloaded> { batches().results(batches().get("msgbatch_01")) }
        expect<ApiError.Network> { batches(at = "http://127.0.0.1:$CLOSED").get("msgbatch_01") }
        val asked = api.server.requestCount
        expect<ApiError.Auth> { batches(key = null).get("msgbatch_01") }
        assertEquals("no key: nothing asked", asked, api.server.requestCount)
    }

    @Test fun `a bad custom_id is refused before any call`() = runBlocking {
        for (bad in listOf("", "has space", "x".repeat(65), "škarje", "a/b", "a.b")) {
            try {
                batches().create(listOf("ok" to params(userText("x")), bad to params(userText("y"))))
                fail("\"$bad\" went out")
            } catch (_: IllegalArgumentException) {
            }
        }
        try {
            batches().create(listOf("same" to params(userText("x")), "same" to params(userText("y"))))
            fail("a custom_id twice went out")
        } catch (_: IllegalArgumentException) {
        }
        try {
            batches().get("../../v1/models")
            fail("a path went out")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(0, api.server.requestCount)
        // 64 is fine
        batches().create(listOf("x".repeat(64) to params(userText("x"))))
        assertEquals(1, api.server.requestCount)
    }

    @Test fun `a results_url on another host is refused and gets no key`() = runBlocking {
        val other = MockWebServer().apply { start(InetAddress.getByName("127.0.0.1"), OTHER) }
        try {
            for (url in listOf("http://localhost:$OTHER/v1/messages/batches/msgbatch_01/results", "http://127.0.0.1:$OTHER/results", "https://evil.example/results")) {
                val b = Batch("msgbatch_01", Batch.ENDED, emptyMap(), url, null, null, null)
                try {
                    batches().results(b)
                    fail("$url got the key")
                } catch (e: IllegalStateException) {
                    assertFalse(e.message!!.contains(FAKE_KEY))
                }
            }
            assertEquals(0, other.requestCount)
            assertEquals(0, api.server.requestCount)
            // a batch that hasn't ended has no results to fetch
            try {
                batches().results(Batch("msgbatch_01", Batch.IN_PROGRESS, emptyMap(), null, null, null, null))
                fail("results of a running batch")
            } catch (_: IllegalStateException) {
            }
        } finally {
            other.shutdown()
        }
    }

    private suspend inline fun <reified E : ApiError> expect(crossinline block: suspend () -> Any?): E {
        try {
            block()
        } catch (e: ApiError) {
            if (e is E) return e
            fail("${E::class.simpleName} expected, got ${e::class.simpleName}: ${e.message}")
        }
        fail("no ${E::class.simpleName}")
        throw AssertionError()
    }

    companion object {
        const val PORT = 19622
        const val OTHER = 19623
        const val CLOSED = 19626
    }
}

/**
 * The Message Batches API as [AnthropicBatches] uses it: one batch, msgbatch_01, in progress until [ended]; its results
 * JSONL in another order than submitted (d, b, a, c) with blank lines between; cancel. [status] answers every call with
 * an error instead.
 */
class FakeBatches : Dispatcher() {
    val server = MockWebServer().also { it.dispatcher = this }

    class Seen(val method: String?, val path: String?, val headers: Map<String, String>, val body: String)

    val requests: MutableList<Seen> = java.util.Collections.synchronizedList(mutableListOf())
    @Volatile var ended = false
    @Volatile var status: Pair<Int, String>? = null
    @Volatile var resultsStatus = 200
    private var submitted = 0

    private fun batch(state: String): JsonObject {
        val done = state == "ended"
        return buildJsonObject {
            put("id", "msgbatch_01")
            put("type", "message_batch")
            put("processing_status", state)
            put("request_counts", buildJsonObject {
                put("processing", if (done) 0 else submitted)
                for (k in listOf("succeeded", "errored", "canceled", "expired")) put(k, if (done) 1 else 0)
            })
            put("created_at", "2026-10-19T08:00:00Z")
            put("expires_at", "2026-10-20T08:00:00Z")
            put("ended_at", if (done) JsonPrimitive("2026-10-19T09:00:00Z") else JsonNull)
            put("cancel_initiated_at", JsonNull)
            put("results_url", if (done) JsonPrimitive("http://127.0.0.1:${BatchesTest.PORT}/v1/messages/batches/msgbatch_01/results") else JsonNull)
        }
    }

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private val results = listOf(
        """{"custom_id":"d","result":{"type":"expired"}}""",
        "",
        """{"custom_id":"b","result":{"type":"errored","error":{"type":"error","error":{"type":"invalid_request_error","message":"max_tokens: too large"}}}}""",
        """{"custom_id":"a","result":{"type":"succeeded","message":{"id":"msg_a","type":"message","role":"assistant","model":"claude-opus-5-5","content":[{"type":"text","text":"Pravljica."}],"stop_reason":"end_turn","usage":{"input_tokens":10,"output_tokens":3}}}}""",
        "   ",
        """{"custom_id":"c","result":{"type":"canceled"}}""",
        "",
    )

    override fun dispatch(request: RecordedRequest): MockResponse {
        val body = request.body.readUtf8()
        requests += Seen(request.method, request.path, request.headers.associate { (k, v) -> k.lowercase() to v }, body)
        status?.let { (code, b) -> return json(code, b).apply { if (code == 429) setHeader("retry-after", "5") } }
        return when {
            request.method == "POST" && request.path == "/v1/messages/batches" -> {
                submitted = Json.parseToJsonElement(body).jsonObject["requests"]!!.jsonArray.size
                json(200, batch("in_progress").toString())
            }
            request.method == "GET" && request.path == "/v1/messages/batches/msgbatch_01" -> json(200, batch(if (ended) "ended" else "in_progress").toString())
            request.method == "GET" && request.path == "/v1/messages/batches/msgbatch_01/results" ->
                if (resultsStatus != 200) json(resultsStatus, """{"type":"error","error":{"type":"api_error","message":"boom"}}""")
                else MockResponse().setResponseCode(200).setHeader("Content-Type", "application/x-jsonl").setBody(results.joinToString("\n"))
            request.method == "POST" && request.path == "/v1/messages/batches/msgbatch_01/cancel" -> json(200, batch("canceling").toString())
            else -> json(404, """{"type":"error","error":{"type":"not_found_error","message":"${request.path}"}}""")
        }
    }
}
