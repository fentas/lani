package si.lanisce.lani.tutor

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Messages API's wire format, pinned byte for byte (docs/plans/05-built/providers.md: the provider layer must not
 * change what Anthropic gets): a request's body as the loop sends it (the prefix, adaptive thinking, the effort, the
 * caching, the thread with a thinking block, a tool call and its result, an effort message), its headers, and the
 * messages the loop appends.
 */
class AnthropicWireTest {
    private val prefix = Prefix(
        JsonArray(listOf(ToolSpec("reply", "Say it.", obj("type" to "object", "properties" to obj("text" to obj("type" to "string")))).definition())),
        "core prompt",
    )

    private val thread: List<JsonObject> = listOf(
        userText("<notebook>n</notebook>", "<event kind=\"chat\">Živjo</event>"),
        TutorLoop.assistant(listOf(thinking("sig-1"), text("Pogledam."), toolUse("tu_1", "reply", obj("conversation_id" to "main", "text" to "Živjo!")))),
        TutorLoop.toolResults(
            listOf(toolUse("tu_1", "reply")),
            listOf(ToolOutcome("delivered", false, ToolCallRecord("tu_1", "reply", JsonObject(emptyMap()), "reply", "delivered"))),
        ),
        Effort.message("high"),
    )

    @Test fun `a request's body is exactly as before`() {
        val body = prefix.request("claude-sonnet-5-5", Task.CHAT, thread, 16_000, "medium")
        assertEquals(
            """{"model":"claude-sonnet-5-5","max_tokens":16000,"stream":true,""" +
                """"system":[{"type":"text","text":"core prompt","cache_control":{"type":"ephemeral","ttl":"1h"}}],""" +
                """"tools":[{"name":"reply","description":"Say it.","input_schema":{"type":"object","properties":{"text":{"type":"string"}}},"eager_input_streaming":true}],""" +
                """"tool_choice":{"type":"auto"},"thinking":{"type":"adaptive"},"output_config":{"effort":"medium"},"cache_control":{"type":"ephemeral"},""" +
                """"messages":[{"role":"user","content":[{"type":"text","text":"<notebook>n</notebook>"},{"type":"text","text":"<event kind=\"chat\">Živjo</event>"}]},""" +
                """{"role":"assistant","content":[{"type":"thinking","thinking":"","signature":"sig-1"},{"type":"text","text":"Pogledam."},""" +
                """{"type":"tool_use","id":"tu_1","name":"reply","input":{"conversation_id":"main","text":"Živjo!"}}]},""" +
                """{"role":"user","content":[{"type":"tool_result","tool_use_id":"tu_1","content":"delivered"}]},""" +
                """{"role":"system","content":[],"output_config":{"effort":"high"}}]}""",
            body.toString(),
        )
        // a model without adaptive thinking gets neither thinking nor an effort
        val old = prefix.request("claude-haiku-4-5", Task.ROUTINE, thread.take(1), 16_000, "high")
        assertEquals(null, old["thinking"])
        assertEquals(null, old["output_config"])
    }

    @Test fun `what goes over the wire - the body with stream, the headers, the betas`() = runBlocking {
        val api = Answering { Triple(200, sse("message_start" to """{"type":"message_start","message":{"id":"m","model":"claude-sonnet-5-5","usage":{"input_tokens":1}}}""", "message_stop" to """{"type":"message_stop"}"""), "text/event-stream") }
        val t = AnthropicTransport(KeyGuard.API, { FAKE_KEY }, OkHttpClient.Builder().addInterceptor(api).build())
        val body = prefix.request("claude-sonnet-5-5", Task.CHAT, thread, 16_000, "medium")
        t.stream(body)
        val r = api.requests.single()
        assertEquals("https://api.anthropic.com/v1/messages", r.url.toString())
        assertEquals(FAKE_KEY, r.header("x-api-key"))
        assertEquals("2023-06-01", r.header("anthropic-version"))
        assertEquals("text/event-stream", r.header("accept"))
        assertEquals("thinking-binding-controls-2026-08-01,mid-conversation-output-config-2026-07-01", r.header("anthropic-beta"))
        assertEquals(body.toString(), Buffer().also { r.body!!.writeTo(it) }.readUtf8())
        // without an effort message: the thinking-binding beta alone
        api.requests.clear()
        t.stream(prefix.request("claude-sonnet-5-5", Task.CHAT, thread.take(1), 16_000, "medium"))
        assertEquals("thinking-binding-controls-2026-08-01", api.requests.single().header("anthropic-beta"))
    }

    @Test fun `the loop appends the answer as received and the results in the calls' order`() = runBlocking {
        val answer = message("tool_use", thinking("sig-2"), toolUse("a", "reply", obj("conversation_id" to "main", "text" to "x")), toolUse("b", "guide", obj("topic" to "t")))
        val sent = mutableListOf<String>()
        val t = ScriptedTransport { body, n -> sent += body["messages"].toString(); if (n == 1) answer else message("end_turn", text("ok")) }
        val m = mutableListOf(userText("<event>hi</event>"))
        TutorLoop(t, { u -> ToolOutcome("ran ${u.name}", u.name == "guide", ToolCallRecord(u.id, u.name, u.input, "read", "ok")) })
            .run(prefix, "claude-sonnet-5-5", Task.CHAT, m)
        assertEquals(
            """{"role":"assistant","content":[{"type":"thinking","thinking":"","signature":"sig-2"},""" +
                """{"type":"tool_use","id":"a","name":"reply","input":{"conversation_id":"main","text":"x"}},{"type":"tool_use","id":"b","name":"guide","input":{"topic":"t"}}]}""",
            m[1].toString(),
        )
        assertEquals(
            """{"role":"user","content":[{"type":"tool_result","tool_use_id":"a","content":"ran reply"},{"type":"tool_result","tool_use_id":"b","content":"ran guide","is_error":true}]}""",
            m[2].toString(),
        )
        assertEquals("""{"role":"assistant","content":[{"type":"text","text":"ok"}]}""", m[3].toString())
        // the second request sent the history exactly as kept
        assertEquals(JsonArray(m.take(3)).toString(), sent[1])
    }

    @Test fun `effort messages go where they went`() {
        val answered = listOf(userText("a"), TutorLoop.assistant(listOf(text("b"))))
        assertEquals(
            """[{"role":"user","content":[{"type":"text","text":"a"}]},{"role":"assistant","content":[{"type":"text","text":"b"}]},""" +
                """{"role":"system","content":[],"output_config":{"effort":"high"}},{"role":"user","content":[{"type":"text","text":"c"}]}]""",
            JsonArray(Effort.append(answered, listOf(textBlock("c")), "medium", "high")).toString(),
        )
        assertEquals(
            """[{"role":"user","content":[{"type":"text","text":"a"}]},{"role":"assistant","content":[{"type":"text","text":"b"}]},{"role":"user","content":[{"type":"text","text":"c"}]}]""",
            JsonArray(Effort.append(answered, listOf(textBlock("c")), "medium", "medium")).toString(),
        )
    }
}
