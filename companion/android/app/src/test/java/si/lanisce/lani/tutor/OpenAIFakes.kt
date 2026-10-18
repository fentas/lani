package si.lanisce.lani.tutor

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer

/** A key shaped like an OpenAI project key: it must never show up anywhere but the Authorization header. */
const val FAKE_OPENAI_KEY = "sk-proj-SHADOWTESTOPENAI0123456789abcdefXYZ"

/** A reasoning item as the Responses API returns it with store: false (its encrypted content to pass back). */
fun reasoningItem(id: String, encrypted: String? = "enc-$id") = buildJsonObject {
    put("type", "reasoning")
    put("id", id)
    put("summary", JsonArray(emptyList()))
    encrypted?.let { put("encrypted_content", it) }
    put("status", "completed")
}

/** An assistant message item with [text] (and its [phase], which a replay keeps). */
fun messageItem(id: String, text: String, phase: String? = "final_answer") = buildJsonObject {
    put("type", "message")
    put("id", id)
    put("role", "assistant")
    put("status", "completed")
    phase?.let { put("phase", it) }
    put("content", JsonArray(listOf(buildJsonObject { put("type", "output_text"); put("text", text); put("annotations", JsonArray(emptyList())) })))
}

fun functionCall(id: String, callId: String, name: String, args: JsonObject = JsonObject(emptyMap()), raw: String? = null, namespace: String? = null) = buildJsonObject {
    put("type", "function_call")
    put("id", id)
    put("call_id", callId)
    put("name", name)
    namespace?.let { put("namespace", it) }
    put("arguments", raw ?: args.toString())
    put("status", "completed")
}

/** A refusal content part in a message. */
fun refusalItem(id: String, text: String) = buildJsonObject {
    put("type", "message")
    put("id", id)
    put("role", "assistant")
    put("status", "completed")
    put("content", JsonArray(listOf(buildJsonObject { put("type", "refusal"); put("refusal", text) })))
}

/** A Responses API usage object. */
fun openAIUsage(input: Long = 5_000, cached: Long = 4_000, written: Long = 600, output: Long = 120, reasoning: Long = 64) = obj(
    "input_tokens" to input, "input_tokens_details" to obj("cached_tokens" to cached, "cache_write_tokens" to written),
    "output_tokens" to output, "output_tokens_details" to obj("reasoning_tokens" to reasoning), "total_tokens" to input + output,
)

/**
 * The server-sent events of a response with [output] (as the API streams them: created, each item added, its deltas
 * (text, function arguments in pieces), done; then completed (or [status] "incomplete" with [incomplete] as its reason).
 */
fun responseSse(output: List<JsonObject>, model: String = "gpt-6.1-sol", usage: JsonObject = openAIUsage(), status: String = "completed", incomplete: String? = null, id: String = "resp_1"): String {
    val events = mutableListOf<JsonObject>()
    var seq = 0
    fun ev(type: String, vararg kv: Pair<String, Any?>) {
        events += obj("type" to type, "sequence_number" to seq++, *kv)
    }
    val started = obj("id" to id, "object" to "response", "model" to model, "status" to "in_progress", "output" to JsonArray(emptyList()))
    ev("response.created", "response" to started)
    ev("response.in_progress", "response" to started)
    output.forEachIndexed { i, item ->
        when (item.str("type")) {
            "function_call" -> {
                ev("response.output_item.added", "output_index" to i, "item" to JsonObject(item + ("arguments" to JsonPrimitive("")) + ("status" to JsonPrimitive("in_progress"))))
                item.str("arguments").orEmpty().chunked(5).forEach { ev("response.function_call_arguments.delta", "output_index" to i, "item_id" to item.str("id"), "delta" to it) }
                ev("response.function_call_arguments.done", "output_index" to i, "item_id" to item.str("id"), "arguments" to item.str("arguments"))
            }
            "message" -> {
                ev("response.output_item.added", "output_index" to i, "item" to JsonObject(item + ("content" to JsonArray(emptyList())) + ("status" to JsonPrimitive("in_progress"))))
                item.blocks().forEachIndexed { c, part ->
                    if (part.str("type") == "output_text") part.str("text").orEmpty().chunked(4).forEach {
                        ev("response.output_text.delta", "output_index" to i, "content_index" to c, "item_id" to item.str("id"), "delta" to it)
                    }
                    if (part.str("type") == "refusal") ev("response.refusal.delta", "output_index" to i, "content_index" to c, "item_id" to item.str("id"), "delta" to part.str("refusal"))
                }
            }
            "reasoning" -> {
                // the encrypted content as added may be incomplete: only the done event's counts
                ev("response.output_item.added", "output_index" to i, "item" to JsonObject(item - "encrypted_content" + ("status" to JsonPrimitive("in_progress"))))
            }
            else -> ev("response.output_item.added", "output_index" to i, "item" to item)
        }
        ev("response.output_item.done", "output_index" to i, "item" to item)
    }
    val final = obj(
        "id" to id, "object" to "response", "model" to model, "status" to status, "output" to JsonArray(output), "usage" to usage,
        "incomplete_details" to (incomplete?.let { obj("reason" to it) }),
    )
    ev(if (status == "incomplete") "response.incomplete" else "response.completed", "response" to final)
    return events.joinToString("") { "event: ${it.str("type")}\ndata: $it\n\n" }
}

/** A request's JSON body as sent. */
fun bodyOf(r: Request): JsonObject = tutorJson.parseToJsonElement(Buffer().also { r.body!!.writeTo(it) }.readUtf8()) as JsonObject

/** The Responses API played by a script: [respond] gives each POST's output items (or a whole answer), by its number. */
class ScriptedResponses(
    private val models: String = """{"object":"list","data":[{"id":"gpt-6-astra","created":1790000000,"owned_by":"openai"},{"id":"gpt-6.1-sol","created":1791000000,"owned_by":"openai"},{"id":"gpt-6-luna","created":1789000000,"owned_by":"openai"},{"id":"text-embedding-3-small","created":1700000000,"owned_by":"system"}]}""",
    private val usage: () -> JsonObject = { openAIUsage() },
    private val answer: ((JsonObject, Int) -> Triple<Int, String, String>?)? = null,
    private val respond: (JsonObject, Int) -> List<JsonObject>,
) {
    @Volatile var calls = 0
        private set
    val http = Answering { r ->
        if (r.url.encodedPath == "/v1/models") return@Answering Triple(200, models, "application/json")
        val body = bodyOf(r)
        val n = synchronized(this) { ++calls }
        answer?.invoke(body, n)?.let { return@Answering it }
        Triple(200, responseSse(respond(body, n), (body["model"] as JsonPrimitive).content, usage()), "text/event-stream")
    }
    val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(http).build()

    /** The POSTs' bodies, in order. */
    fun posts(): List<JsonObject> = synchronized(http.requests) { http.requests.toList() }.filter { it.method == "POST" }.map(::bodyOf)
}

/** The input items of a request [body]. */
fun inputOf(body: JsonObject): List<JsonObject> = (body["input"] as JsonArray).map { it as JsonObject }
