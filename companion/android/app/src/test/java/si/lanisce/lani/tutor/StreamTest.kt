package si.lanisce.lani.tutor

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The stream parsing: text, thinking, tool_use with partial JSON, usage; the errors by the plan's table. */
class StreamTest {
    private val start = """{"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","model":"claude-sonnet-5-5","content":[],"stop_reason":null,"usage":{"input_tokens":12,"cache_creation_input_tokens":3100,"cache_read_input_tokens":0,"cache_creation":{"ephemeral_5m_input_tokens":100,"ephemeral_1h_input_tokens":3000},"output_tokens":1}}}"""

    private val whole = sse(
        "message_start" to start,
        "ping" to """{"type":"ping"}""",
        "content_block_start" to """{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}""",
        "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"Hm, "}}""",
        "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"a chat."}}""",
        "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"EqQBsig=="}}""",
        "content_block_stop" to """{"type":"content_block_stop","index":0}""",
        "content_block_start" to """{"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}""",
        "content_block_delta" to """{"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"Pogle"}}""",
        "content_block_delta" to """{"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"dam …"}}""",
        "content_block_stop" to """{"type":"content_block_stop","index":1}""",
        "content_block_start" to """{"type":"content_block_start","index":2,"content_block":{"type":"tool_use","id":"toolu_1","name":"reply","input":{}}}""",
        "content_block_delta" to """{"type":"content_block_delta","index":2,"delta":{"type":"input_json_delta","partial_json":"{\"conversa"}}""",
        "content_block_delta" to """{"type":"content_block_delta","index":2,"delta":{"type":"input_json_delta","partial_json":"tion_id\": \"main\", \"te"}}""",
        "content_block_delta" to """{"type":"content_block_delta","index":2,"delta":{"type":"input_json_delta","partial_json":"xt\": \"**Dobro!** \\u010d\"}"}}""",
        "content_block_stop" to """{"type":"content_block_stop","index":2}""",
        "message_delta" to """{"type":"message_delta","delta":{"stop_reason":"tool_use","stop_sequence":null},"usage":{"output_tokens":95}}""",
        "message_stop" to """{"type":"message_stop"}""",
    )

    private fun transport(code: Int, body: String, type: String = "text/event-stream") =
        AnthropicTransport(KeyGuard.API, { FAKE_KEY }, OkHttpClient.Builder().addInterceptor(Answering { Triple(code, body, type) }).build())

    private fun body() = Prefix(kotlinx.serialization.json.JsonArray(emptyList()), "core").request("claude-sonnet-5-5", Task.CHAT, listOf(userText("hi")))

    @Test fun `a whole stream comes back as the API's blocks, with its usage`() = runBlocking {
        var deltas = 0
        val r = transport(200, whole).stream(body()) { deltas++ }
        val m = r.message
        assertEquals("msg_1", m.id)
        assertEquals("tool_use", m.stopReason)
        assertEquals(3, m.content.size)
        // thinking goes back unchanged: its (summarized) text and its signature
        assertEquals(JsonObject(mapOf("type" to JsonPrimitive("thinking"), "thinking" to JsonPrimitive("Hm, a chat."), "signature" to JsonPrimitive("EqQBsig=="))), m.content[0])
        assertEquals("Pogledam …", m.text)
        val tool = m.toolUses.single()
        assertEquals("reply", tool["name"]!!.jsonPrimitive.content)
        assertEquals("main", tool["input"]!!.jsonObject["conversation_id"]!!.jsonPrimitive.content)
        assertEquals("**Dobro!** č", tool["input"]!!.jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals(Usage(input = 12, cacheWrite5m = 100, cacheWrite1h = 3000, cacheRead = 0, output = 95), m.usage)
        assertEquals(12 + 3100L, m.usage.prompt)
        assertEquals(8, deltas)
        assertTrue(m.badInput.isEmpty())
    }

    @Test fun `a tool input that isn't JSON is kept aside, not run`() {
        val acc = StreamAccumulator()
        acc.accept(start)
        acc.accept("""{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"toolu_9","name":"reply","input":{}}}""")
        acc.accept("""{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\"text\": \"cut"}}""")
        acc.accept("""{"type":"message_delta","delta":{"stop_reason":"max_tokens"},"usage":{"output_tokens":4000}}""")
        val m = acc.message()
        assertEquals("{\"text\": \"cut", m.badInput["toolu_9"])
        assertEquals(JsonObject(emptyMap()), m.toolUses.single()["input"])
        assertEquals("max_tokens", m.stopReason)
    }

    @Test fun `a refusal's stop details are kept`() {
        val acc = StreamAccumulator()
        acc.accept(start)
        acc.accept("""{"type":"message_delta","delta":{"stop_reason":"refusal","stop_details":{"type":"refusal","category":"cyber","explanation":"x"}},"usage":{"output_tokens":0}}""")
        acc.accept("""{"type":"message_stop"}""")
        assertEquals("refusal", acc.message().stopReason)
        assertEquals("cyber", acc.message().stopDetails!!["category"]!!.jsonPrimitive.content)
    }

    @Test fun `an error in the stream is overloaded, and a stream cut off is a network error`() = runBlocking {
        val broken = sse("message_start" to start, "error" to """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""")
        try {
            transport(200, broken).stream(body()); fail()
        } catch (e: ApiError.Overloaded) {
            assertEquals(529, e.code)
        }
        try {
            transport(200, sse("message_start" to start)).stream(body()); fail()
        } catch (e: ApiError.Network) {
            assertTrue(e.message!!.contains("early"))
        }
    }

    @Test fun `HTTP errors map to the plan's table`() = runBlocking {
        suspend fun err(code: Int, body: String): ApiError = try {
            transport(code, body, "application/json").stream(body()); error("no error")
        } catch (e: ApiError) { e }
        assertTrue(err(401, """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}""") is ApiError.Auth)
        val low = err(400, """{"type":"error","error":{"type":"invalid_request_error","message":"Your credit balance is too low to access the Anthropic API. Please go to Plans & Billing to upgrade or purchase credits."}}""")
        assertTrue(low is ApiError.LowCredit)
        val limited = err(429, """{"type":"error","error":{"type":"rate_limit_error","message":"slow down"}}""")
        assertEquals(2_000L, (limited as ApiError.RateLimited).retryAfterMs)
        assertTrue(err(529, """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""").retryable)
        assertTrue(err(500, "oops").retryable)
        val bad = err(400, """{"type":"error","error":{"type":"invalid_request_error","message":"tool_choice: type \"any\" is not supported"}}""")
        assertTrue(bad is ApiError.BadRequest && !bad.retryable)
        assertTrue(err(404, """{"type":"error","error":{"type":"not_found_error","message":"model: x"}}""") is ApiError.BadRequest)
        // the spend limits: the workspace's (400) and the tier's (429 with its error_code, no retry-after): not retried
        val workspace = err(400, """{"type":"error","error":{"type":"invalid_request_error","message":"You have reached your specified workspace API usage limits. You will regain access on 2026-11-01."}}""")
        assertTrue(workspace is ApiError.LowCredit && workspace.limit && !workspace.retryable)
        val tier = err(429, """{"type":"error","error":{"type":"rate_limit_error","error_code":"enforced_spend_limit_reached","message":"spend limit"}}""")
        assertEquals("spend_limit", tier.kind)
        assertTrue(!tier.retryable)
        assertTrue(err(402, """{"type":"error","error":{"type":"billing_error","message":"billing"}}""") is ApiError.LowCredit)
    }

    @Test fun `what the API changed of the history is kept for the log`() {
        val acc = StreamAccumulator()
        acc.accept("""{"type":"message_start","message":{"id":"m","model":"claude-sonnet-5-5","content":[],"usage":{"input_tokens":1},"input_transformations":[{"type":"drop_thinking_block","message_index":3}]}}""")
        acc.accept("""{"type":"message_stop"}""")
        assertTrue(acc.message().inputTransformations!!.contains("drop_thinking_block"))
        val none = StreamAccumulator().apply { accept(start); accept("""{"type":"message_stop"}""") }
        assertNull(none.message().inputTransformations)
    }

    @Test fun `the request carries the thinking-binding beta`() = runBlocking {
        val api = Answering { Triple(200, whole, "text/event-stream") }
        AnthropicTransport(KeyGuard.API, { FAKE_KEY }, OkHttpClient.Builder().addInterceptor(api).build()).stream(body())
        assertEquals("thinking-binding-controls-2026-08-01", api.requests.single().header("anthropic-beta"))
        assertEquals("text/event-stream", api.requests.single().header("accept"))
    }

    @Test fun `the models the key lists`() = runBlocking {
        val list = """{"data":[{"type":"model","id":"claude-sonnet-5-5","display_name":"Claude Sonnet 5.5","created_at":"2026-10-01T00:00:00Z","max_input_tokens":1000000,"max_tokens":128000}],"has_more":false}"""
        val m = transport(200, list, "application/json").models().single()
        assertEquals("claude-sonnet-5-5", m.id)
        assertEquals(1_000_000L, m.maxInputTokens)
    }

    @Test fun `usage merges the final counts`() {
        val u = Usage.merge(Usage(input = 5, cacheRead = 900, output = 1), JsonObject(mapOf("output_tokens" to JsonPrimitive(70))))
        assertEquals(Usage(input = 5, cacheRead = 900, output = 70), u)
        // a write without its breakdown counts as a 5-minute one
        val w = Usage.merge(Usage(), JsonObject(mapOf("cache_creation_input_tokens" to JsonPrimitive(40))))
        assertEquals(40L, w.cacheWrite5m)
        assertNull(ApiError.retryAfterMs(null))
    }
}
