package si.lanisce.lani.tutor.live

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import si.lanisce.lani.tutor.AssistantMessage
import si.lanisce.lani.tutor.Usage
import si.lanisce.lani.tutor.blocks
import si.lanisce.lani.tutor.message
import si.lanisce.lani.tutor.obj
import si.lanisce.lani.tutor.role
import si.lanisce.lani.tutor.sse
import si.lanisce.lani.tutor.str
import si.lanisce.lani.tutor.text
import si.lanisce.lani.tutor.thinking
import si.lanisce.lani.tutor.toolUse
import java.util.Collections

/** The live tutor's tests' API: a scripted Messages API, streamed as the real one streams (the reply's input in pieces). */
internal object LiveFakes {
    const val REPLY = "**Peč** · the stove. *Na peči je toplo.* · It's warm on the stove."
    const val MODELS = """{"data":[{"id":"claude-opus-5-5","created_at":"2026-10-01"},{"id":"claude-sonnet-5-5","created_at":"2026-10-01"},{"id":"claude-haiku-5-5","created_at":"2026-10-07"}],"has_more":false}"""

    fun bodyOf(r: Request): String = r.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }.orEmpty()

    /** A small tutor: guide and learner_state first, then the reply, then done (as the bun mock). */
    fun tutorTurn(body: JsonObject, n: Int): AssistantMessage {
        val messages = body["messages"]!!.jsonArray.map { it.jsonObject }
        val last = messages.last()
        val results = last.blocks().any { it.str("type") == "tool_result" }
        val prev = messages.getOrNull(messages.size - 2)
        val replied = prev?.blocks()?.any { it.str("type") == "tool_use" && it.str("name") in setOf("reply", "notebook_write") } == true
        if (!results && last.toString().contains("[notebook_write]")) return message("tool_use", toolUse("nb$n", "notebook_write", obj("text" to "# Notebook\n- Jan: A2")))
        if (!results) return message("tool_use", thinking("sig$n"), text("Pogledam."), toolUse("g$n", "guide", obj("topic" to "feedback")), toolUse("s$n", "learner_state"))
        if (!replied) {
            val conv = Regex("""conversation_id=\\"([\w-]+)""").find(messages.last { m -> m.role() == "user" && m.blocks().none { it.str("type") == "tool_result" } }.toString())?.groupValues?.get(1) ?: "main"
            return message("tool_use", toolUse("r$n", "reply", obj("conversation_id" to conv, "text" to REPLY)))
        }
        return message("end_turn", text("Opravljeno."))
    }

    /** An OkHttp interceptor that answers every request itself and keeps them. */
    class Answering(private val answer: (Request) -> Triple<Int, String, String>) : Interceptor {
        val requests: MutableList<Request> = Collections.synchronizedList(mutableListOf())
        override fun intercept(chain: Interceptor.Chain): Response {
            val r = chain.request()
            requests += r
            val (code, body, type) = answer(r)
            return Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(code).message("x").body(body.toResponseBody(type.toMediaType())).build()
        }
    }

    /** [m] as the API streams it: a tool's input in pieces of seven characters (input_json_delta). */
    fun sseOf(m: AssistantMessage, model: String): String {
        val events = mutableListOf<Pair<String, String>>()
        fun usage(u: Usage, output: Long) = obj(
            "input_tokens" to u.input, "cache_creation_input_tokens" to u.cacheWrite, "cache_read_input_tokens" to u.cacheRead,
            "cache_creation" to obj("ephemeral_5m_input_tokens" to u.cacheWrite5m, "ephemeral_1h_input_tokens" to u.cacheWrite1h), "output_tokens" to output,
        )
        events += "message_start" to obj("type" to "message_start", "message" to obj("id" to "msg_x", "type" to "message", "role" to "assistant", "model" to model, "content" to JsonArray(emptyList()), "usage" to usage(m.usage, 1))).toString()
        m.content.forEachIndexed { i, b ->
            fun delta(d: JsonObject) { events += "content_block_delta" to obj("type" to "content_block_delta", "index" to i, "delta" to d).toString() }
            when (b.str("type")) {
                "text" -> {
                    events += "content_block_start" to obj("type" to "content_block_start", "index" to i, "content_block" to obj("type" to "text", "text" to "")).toString()
                    delta(obj("type" to "text_delta", "text" to b.str("text")))
                }
                "thinking" -> {
                    events += "content_block_start" to obj("type" to "content_block_start", "index" to i, "content_block" to obj("type" to "thinking", "thinking" to "")).toString()
                    delta(obj("type" to "signature_delta", "signature" to b.str("signature")))
                }
                "tool_use" -> {
                    events += "content_block_start" to obj("type" to "content_block_start", "index" to i, "content_block" to obj("type" to "tool_use", "id" to b.str("id"), "name" to b.str("name"), "input" to JsonObject(emptyMap()))).toString()
                    b["input"].toString().chunked(7).forEach { delta(obj("type" to "input_json_delta", "partial_json" to it)) }
                }
            }
            events += "content_block_stop" to obj("type" to "content_block_stop", "index" to i).toString()
        }
        events += "message_delta" to obj("type" to "message_delta", "delta" to obj("stop_reason" to m.stopReason), "usage" to obj("output_tokens" to m.usage.output)).toString()
        events += "message_stop" to obj("type" to "message_stop").toString()
        return sse(*events.toTypedArray())
    }
}
