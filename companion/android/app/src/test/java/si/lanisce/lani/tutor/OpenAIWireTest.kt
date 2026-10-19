package si.lanisce.lani.tutor

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Responses API's wire format, pinned byte for byte (docs/plans/05-built/providers.md: a new provider must not
 * change what OpenAI gets), as AnthropicWireTest pins the Messages API's: a request's body with an API key and with the
 * ChatGPT plan (the prefix as instructions, the tools, the effort, store false, the encrypted reasoning, the cache key,
 * the thread with a reasoning item, a function call and its output, a configuration_update, a photo), its headers, and
 * the items the loop appends.
 */
class OpenAIWireTest {
    private val prefix = Prefix(
        JsonArray(listOf(ToolSpec("reply", "Say it.", obj("type" to "object", "properties" to obj("text" to obj("type" to "string")))).definition())),
        "core prompt",
    )

    private val photo = obj("type" to "image", "source" to obj("type" to "base64", "media_type" to "image/jpeg", "data" to "AAAA"))

    private val thread: List<JsonObject> = listOf(
        OpenAIFormat.user(listOf(textBlock("<notebook>n</notebook>"), photo)),
        reasoningItem("rs_1"),
        functionCall("fc_1", "call_1", "reply", obj("conversation_id" to "main", "text" to "Živjo!")),
        obj("type" to "function_call_output", "call_id" to "call_1", "output" to "delivered"),
        OpenAIFormat.update("high"),
        OpenAIFormat.user(listOf(textBlock("<event kind=\"chat\">Živjo</event>"))),
    )

    @Test fun `a request's body with an API key is exactly as before`() {
        val body = OpenAIProvider(OpenAITransport({ null }), Billing.API_KEY).request(prefix, "gpt-6.1-sol", Task.CHAT, thread, 16_000, "medium")
        assertEquals(
            """{"model":"gpt-6.1-sol","instructions":"core prompt",""" +
                """"input":[{"type":"message","role":"user","content":[{"type":"input_text","text":"<notebook>n</notebook>"},{"type":"input_image","image_url":"data:image/jpeg;base64,AAAA","detail":"auto"}]},""" +
                """{"type":"reasoning","id":"rs_1","summary":[],"encrypted_content":"enc-rs_1","status":"completed"},""" +
                """{"type":"function_call","id":"fc_1","call_id":"call_1","name":"reply","arguments":"{\"conversation_id\":\"main\",\"text\":\"Živjo!\"}","status":"completed"},""" +
                """{"type":"function_call_output","call_id":"call_1","output":"delivered"},{"type":"configuration_update","reasoning":{"effort":"high"}},""" +
                """{"type":"message","role":"user","content":[{"type":"input_text","text":"<event kind=\"chat\">Živjo</event>"}]}],""" +
                """"tools":[{"type":"function","name":"reply","description":"Say it.","parameters":{"type":"object","properties":{"text":{"type":"string"}}},"strict":false}],""" +
                """"tool_choice":"auto","reasoning":{"effort":"medium"},"store":false,"stream":true,"include":["reasoning.encrypted_content"],""" +
                """"prompt_cache_key":"lani-tutor-$KEY_HASH","max_output_tokens":16000}""",
            body.toString(),
        )
    }

    @Test fun `with the ChatGPT plan - the tools in one namespace, no max_output_tokens`() {
        val body = OpenAIProvider(OpenAITransport({ null }), Billing.CHATGPT_PLAN).request(prefix, "gpt-6-astra", Task.CONTENT, thread.take(1), 32_000, "low")
        assertEquals(
            """{"model":"gpt-6-astra","instructions":"core prompt",""" +
                """"input":[{"type":"message","role":"user","content":[{"type":"input_text","text":"<notebook>n</notebook>"},{"type":"input_image","image_url":"data:image/jpeg;base64,AAAA","detail":"auto"}]}],""" +
                """"tools":[{"type":"namespace","name":"lani","description":"Lani's tutor tools: reply to the learner, read their data and the guide, and change their data.",""" +
                """"tools":[{"type":"function","name":"reply","description":"Say it.","parameters":{"type":"object","properties":{"text":{"type":"string"}}},"strict":false}]}],""" +
                """"tool_choice":"auto","reasoning":{"effort":"low"},"store":false,"stream":true,"include":["reasoning.encrypted_content"],""" +
                """"prompt_cache_key":"lani-tutor-$PLAN_HASH"}""",
            body.toString(),
        )
    }

    @Test fun `what goes over the wire - the body with stream, the bearer, the stream's accept`() = runBlocking {
        val api = Answering { Triple(200, responseSse(listOf(messageItem("m", "ok"))), "text/event-stream") }
        val t = OpenAITransport({ OpenAIBearer(FAKE_OPENAI_KEY, Billing.API_KEY) }, OkHttpClient.Builder().addInterceptor(api).build())
        val body = OpenAIProvider(t, Billing.API_KEY).request(prefix, "gpt-6.1-sol", Task.CHAT, thread, 16_000, "medium")
        t.stream(body)
        val r = api.requests.single()
        assertEquals("https://api.openai.com/v1/responses", r.url.toString())
        assertEquals("Bearer $FAKE_OPENAI_KEY", r.header("Authorization"))
        assertEquals("text/event-stream", r.header("accept"))
        assertEquals(null, r.header("x-api-key"))
        assertEquals(JsonObject(body + ("stream" to kotlinx.serialization.json.JsonPrimitive(true))).toString(), Buffer().also { r.body!!.writeTo(it) }.readUtf8())
    }

    @Test fun `the loop appends the output items as received and the outputs in the calls' order`() = runBlocking {
        var n = 0
        val api = Answering {
            n++
            val out = if (n == 1) listOf(reasoningItem("rs_9"), functionCall("fc_a", "a", "reply", obj("conversation_id" to "main", "text" to "x")), functionCall("fc_b", "b", "guide", obj("topic" to "t")))
            else listOf(messageItem("m_2", "ok"))
            Triple(200, responseSse(out), "text/event-stream")
        }
        val p = OpenAIProvider(OpenAITransport({ OpenAIBearer(FAKE_OPENAI_KEY, Billing.API_KEY) }, OkHttpClient.Builder().addInterceptor(api).build()), Billing.API_KEY)
        val m = mutableListOf(OpenAIFormat.user(listOf(textBlock("<event>hi</event>"))))
        TutorLoop(p, { u -> ToolOutcome("ran ${u.name}", u.name == "guide", ToolCallRecord(u.id, u.name, u.input, "read", "ok")) }).run(prefix, "gpt-6.1-sol", Task.CHAT, m)
        assertEquals(
            """[{"type":"reasoning","id":"rs_9","summary":[],"encrypted_content":"enc-rs_9","status":"completed"},""" +
                """{"type":"function_call","id":"fc_a","call_id":"a","name":"reply","arguments":"{\"conversation_id\":\"main\",\"text\":\"x\"}","status":"completed"},""" +
                """{"type":"function_call","id":"fc_b","call_id":"b","name":"guide","arguments":"{\"topic\":\"t\"}","status":"completed"},""" +
                """{"type":"function_call_output","call_id":"a","output":"ran reply"},{"type":"function_call_output","call_id":"b","output":"Error: ran guide"},""" +
                """{"type":"message","id":"m_2","role":"assistant","status":"completed","phase":"final_answer","content":[{"type":"output_text","text":"ok","annotations":[]}]}]""",
            JsonArray(m.drop(1)).toString(),
        )
    }

    private companion object {
        /** The prefix's fingerprint as OpenAI's requests carry it (its tools, its prompt, the billing): pinned too. */
        const val KEY_HASH = "024d27eed180e925"
        const val PLAN_HASH = "af2ae30190cd0f95"
    }
}
