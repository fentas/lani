package si.lanisce.lani.tutor

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer

/** A key shaped like a Google AI Studio key (AIza and 35 more): never anywhere but the x-goog-api-key header. */
const val FAKE_GEMINI_KEY = "AIzaSyFAKEgeminiTESTkey0123456789abcdef"

/** A key shaped like an OpenRouter key: never anywhere but the Authorization header to openrouter.ai. */
const val FAKE_ROUTER_KEY = "sk-or-v1-0123456789abcdef0123456789abcdef0123456789abcdef0fakekey"

/**
 * Sends every request to [server] instead of its host (the path and query kept): the app's code builds the real URL
 * and checks its guard on it; the test's server answers. The original host goes along as `x-test-host`.
 */
class Reroute(private val server: MockWebServer) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val r = chain.request()
        val url = r.url.newBuilder().scheme("http").host(server.hostName).port(server.port).build()
        return chain.proceed(r.newBuilder().url(url).header("x-test-host", r.url.host).build())
    }
}

fun reroutedClient(server: MockWebServer): OkHttpClient = OkHttpClient.Builder().addInterceptor(Reroute(server)).build()

// --- Gemini ---------------------------------------------------------------------------------------------------------

/** A Gemini text part ([signature]: its thoughtSignature). */
fun gText(text: String, signature: String? = null): JsonObject =
    if (signature == null) obj("text" to text) else obj("text" to text, "thoughtSignature" to signature)

/** A Gemini functionCall part, with the API's [id] and the step's [signature] (on the first call only, as the API puts it). */
fun gCall(name: String, args: JsonObject = obj(), id: String? = "fc-$name", signature: String? = null): JsonObject {
    val fc = if (id == null) obj("name" to name, "args" to args) else obj("id" to id, "name" to name, "args" to args)
    return if (signature == null) obj("functionCall" to fc) else obj("functionCall" to fc, "thoughtSignature" to signature)
}

/** A Gemini usageMetadata: the prompt counts its cached part; the thoughts apart from the candidates. */
fun geminiUsage(prompt: Long = 6_000, cached: Long = 5_000, candidates: Long = 80, thoughts: Long = 40) = obj(
    "promptTokenCount" to prompt, "cachedContentTokenCount" to cached, "candidatesTokenCount" to candidates,
    "thoughtsTokenCount" to thoughts, "totalTokenCount" to prompt + candidates + thoughts,
)

/** A streamed chunk of Gemini's (a GenerateContentResponse). */
fun geminiChunk(parts: List<JsonObject>, finish: String? = null, usage: JsonObject? = null, model: String = "gemini-3.8-flash", id: String = "resp-1"): JsonObject {
    val candidate = buildMap<String, Any?> {
        put("content", obj("role" to "model", "parts" to JsonArray(parts)))
        put("index", 0)
        finish?.let { put("finishReason", it) }
    }
    return obj("candidates" to JsonArray(listOf(obj(*candidate.toList().toTypedArray()))), "usageMetadata" to usage, "modelVersion" to model, "responseId" to id)
}

/**
 * A model turn of [parts] as Gemini streams it: a text part in two pieces, each other part in a chunk of its own, the
 * last chunk with the finishReason and the usage.
 */
fun geminiSse(parts: List<JsonObject>, finish: String = "STOP", usage: JsonObject = geminiUsage(), model: String = "gemini-3.8-flash", id: String = "resp-1"): String {
    val chunks = mutableListOf<JsonObject>()
    for (p in parts) {
        val t = p.str("text")
        if (t != null && p.keys == setOf("text") && t.length > 3) {
            chunks += geminiChunk(listOf(gText(t.take(t.length / 2))), model = model, id = id)
            chunks += geminiChunk(listOf(gText(t.drop(t.length / 2))), model = model, id = id)
        } else chunks += geminiChunk(listOf(p), model = model, id = id)
    }
    chunks += geminiChunk(emptyList(), finish = finish, usage = usage, model = model, id = id)
    return chunks.joinToString("") { "data: $it\r\n\r\n" }
}

/** The contents of a Gemini request [body]. */
fun contentsOf(body: JsonObject): List<JsonObject> = (body["contents"] as JsonArray).map { it as JsonObject }

/** Gemini played by a script: each POST's model turn parts, by its number; GET …/models the list. */
class ScriptedGemini(
    private val usage: () -> JsonObject = { geminiUsage() },
    private val answer: ((JsonObject, Int) -> Triple<Int, String, String>?)? = null,
    private val respond: (JsonObject, Int) -> List<JsonObject>,
) {
    @Volatile var calls = 0
        private set
    val http = Answering { r ->
        if (r.method == "GET") return@Answering Triple(200, GEMINI_MODELS, "application/json")
        val body = bodyOf(r)
        val n = synchronized(this) { ++calls }
        answer?.invoke(body, n)?.let { return@Answering it }
        val model = r.url.encodedPath.substringAfterLast('/').substringBefore(':')
        Triple(200, geminiSse(respond(body, n), usage = usage(), model = model, id = "resp-$n"), "text/event-stream")
    }
    val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(http).build()

    fun posts(): List<JsonObject> = synchronized(http.requests) { http.requests.toList() }.filter { it.method == "POST" }.map(::bodyOf)
}

const val GEMINI_MODELS = """{"models":[
 {"name":"models/gemini-4-argon","displayName":"Gemini 4 Argon","inputTokenLimit":1048576,"outputTokenLimit":65536,"supportedGenerationMethods":["generateContent","countTokens"]},
 {"name":"models/gemini-3.8-flash","displayName":"Gemini 3.8 Flash","inputTokenLimit":1048576,"outputTokenLimit":65536,"supportedGenerationMethods":["generateContent","countTokens"]},
 {"name":"models/gemini-3.5-flash-lite","displayName":"Gemini 3.5 Flash-Lite","inputTokenLimit":1048576,"outputTokenLimit":65536,"supportedGenerationMethods":["generateContent"]},
 {"name":"models/gemini-embedding-001","displayName":"Embedding","supportedGenerationMethods":["embedContent"]}
]}"""

/**
 * The tutor played on Gemini: guide and learner_state first (the step's signature on the first call), then the reply,
 * then done; as the Anthropic and OpenAI scripts do.
 */
fun geminiTurn(body: JsonObject, n: Int): List<JsonObject> {
    val contents = contentsOf(body)
    val responses = contents.last().let { GeminiFormat.parts(it) }.mapNotNull { (it["functionResponse"] as? JsonObject)?.str("name") }
    if (responses.isEmpty()) return listOf(gText("Pogledam."), gCall("guide", obj("topic" to "feedback"), "g$n", signature = "sig-g$n"), gCall("learner_state", id = "s$n"))
    if ("reply" !in responses) {
        val user = contents.last { c -> c.role() == "user" && GeminiFormat.parts(c).any { it["text"] != null } }
        val conv = Regex("""conversation_id=\\"([\w-]+)""").find(user.toString())?.groupValues?.get(1) ?: "main"
        return listOf(gCall("reply", obj("conversation_id" to conv, "text" to "**Mock Gemini** · peč = stove"), "r$n", signature = "sig-r$n"))
    }
    return listOf(gText("Opravljeno.", signature = "sig-end$n"))
}

// --- OpenRouter -------------------------------------------------------------------------------------------------------

/** OpenRouter's usage with its cost. */
fun routerUsage(prompt: Long = 6_000, cached: Long = 5_000, written: Long = 0, completion: Long = 120, reasoning: Long = 40, cost: Double = 0.0123) = obj(
    "prompt_tokens" to prompt, "completion_tokens" to completion, "total_tokens" to prompt + completion, "cost" to cost,
    "prompt_tokens_details" to obj("cached_tokens" to cached, "cache_write_tokens" to written),
    "completion_tokens_details" to obj("reasoning_tokens" to reasoning),
)

/**
 * An assistant answer as OpenRouter streams it: a keep-alive comment, the reasoning details in pieces (text, then the
 * signature), the content in pieces, each tool call's arguments in pieces, the finish_reason, the usage chunk that
 * repeats it, then [DONE].
 */
fun routerSse(
    content: String? = null,
    calls: List<Triple<String, String, JsonObject>> = emptyList(),
    reasoning: String? = "Let me think.",
    signature: String? = "rsig",
    usage: JsonObject = routerUsage(),
    model: String = "google/gemini-3.8-flash",
    finish: String = if (calls.isNotEmpty()) "tool_calls" else "stop",
    id: String = "gen-1",
): String {
    val chunks = mutableListOf<JsonObject>()
    fun chunk(delta: JsonObject, finishReason: String? = null, u: JsonObject? = null) {
        val choice = if (finishReason == null) obj("index" to 0, "delta" to delta) else obj("index" to 0, "delta" to delta, "finish_reason" to finishReason)
        chunks += if (u == null) obj("id" to id, "object" to "chat.completion.chunk", "model" to model, "choices" to JsonArray(listOf(choice)))
        else obj("id" to id, "object" to "chat.completion.chunk", "model" to model, "choices" to JsonArray(listOf(choice)), "usage" to u)
    }
    if (reasoning != null) {
        reasoning.chunked(6).forEach { piece ->
            chunk(obj("role" to "assistant", "reasoning" to piece, "reasoning_details" to JsonArray(listOf(obj("type" to "reasoning.text", "text" to piece, "format" to "google-gemini-v1", "index" to 0)))))
        }
        if (signature != null) chunk(obj("reasoning_details" to JsonArray(listOf(obj("type" to "reasoning.text", "signature" to signature, "format" to "google-gemini-v1", "index" to 0)))))
    }
    content?.chunked(5)?.forEach { chunk(obj("content" to it)) }
    calls.forEachIndexed { i, (cid, name, args) ->
        val a = args.toString()
        chunk(obj("tool_calls" to JsonArray(listOf(obj("index" to i, "id" to cid, "type" to "function", "function" to obj("name" to name, "arguments" to ""))))))
        a.chunked(7).forEach { piece -> chunk(obj("tool_calls" to JsonArray(listOf(obj("index" to i, "function" to obj("arguments" to piece)))))) }
    }
    chunk(obj(), finish)
    chunk(obj(), finish, usage)
    return ": OPENROUTER PROCESSING\n\n" + chunks.joinToString("") { "data: $it\n\n" } + ": OPENROUTER PROCESSING\n\ndata: [DONE]\n\n"
}

/** The messages of a chat completions request [body]. */
fun messagesOf(body: JsonObject): List<JsonObject> = (body["messages"] as JsonArray).map { it as JsonObject }

/** OpenRouter played by a script: each POST's answer (an SSE text), by its number; GET the model list and the key. */
class ScriptedRouter(
    private val answer: ((JsonObject, Int) -> Triple<Int, String, String>?)? = null,
    private val respond: (JsonObject, Int) -> String,
) {
    @Volatile var calls = 0
        private set
    val http = Answering { r ->
        when {
            r.url.encodedPath == "/api/v1/models" -> return@Answering Triple(200, ROUTER_MODELS, "application/json")
            r.url.encodedPath == "/api/v1/key" -> return@Answering Triple(200, ROUTER_KEY, "application/json")
            r.url.encodedPath == "/api/v1/auth/keys" -> return@Answering Triple(200, """{"key":"$FAKE_ROUTER_KEY","user_id":"user_1"}""", "application/json")
        }
        val body = bodyOf(r)
        val n = synchronized(this) { ++calls }
        answer?.invoke(body, n)?.let { return@Answering it }
        Triple(200, respond(body, n), "text/event-stream")
    }
    val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(http).build()

    fun posts(): List<JsonObject> = synchronized(http.requests) { http.requests.toList() }.filter { it.method == "POST" && it.url.encodedPath == "/api/v1/chat/completions" }.map(::bodyOf)
}

const val ROUTER_MODELS = """{"data":[
 {"id":"google/gemini-4-argon","name":"Google: Gemini 4 Argon","created":1790000000,"context_length":1048576,"pricing":{"prompt":"0.000002","completion":"0.00001","input_cache_read":"0.0000001"},"supported_parameters":["tools","tool_choice","reasoning","max_tokens"],"reasoning":{"supported_efforts":["high","medium","low"],"default_effort":"medium","mandatory":true},"architecture":{"input_modalities":["text","image"]},"top_provider":{"max_completion_tokens":65536}},
 {"id":"google/gemini-3.8-flash","name":"Google: Gemini 3.8 Flash","created":1789000000,"context_length":1048576,"pricing":{"prompt":"0.00000075","completion":"0.00000375"},"supported_parameters":["tools","reasoning"],"reasoning":{"supported_efforts":["high","medium","low"],"default_effort":"medium"},"architecture":{"input_modalities":["text","image"]}},
 {"id":"openai/gpt-6-luna","name":"OpenAI: GPT-6 Luna","created":1788000000,"context_length":400000,"pricing":{"prompt":"0.0000001","completion":"0.0000005"},"supported_parameters":["tools","reasoning"],"reasoning":{"supported_efforts":["max","xhigh","high","medium","low","none"]}},
 {"id":"anthropic/claude-sonnet-5.5","name":"Anthropic: Claude Sonnet 5.5","created":1787000000,"context_length":1000000,"pricing":{"prompt":"0.000002","completion":"0.00001"},"supported_parameters":["tools","reasoning"]},
 {"id":"google/gemma-4-31b-it","name":"Google: Gemma 4 31B","created":1786000000,"context_length":131072,"pricing":{"prompt":"0.0000001","completion":"0.0000002"},"supported_parameters":["tools","max_tokens"]}
]}"""

const val ROUTER_KEY = """{"data":{"label":"Lani","limit":20.0,"limit_remaining":17.5,"usage":2.5,"usage_monthly":2.5,"is_free_tier":false}}"""

/** The tutor played on OpenRouter: guide and learner_state first, then the reply, then done. */
fun routerTurn(body: JsonObject, n: Int): String {
    val m = messagesOf(body)
    val since = m.drop(m.indexOfLast { it.role() == "user" } + 1)
    val model = body.str("model").orEmpty()
    if (since.isEmpty()) return routerSse("Pogledam.", listOf(Triple("call_g$n", "guide", obj("topic" to "feedback")), Triple("call_s$n", "learner_state", obj())), model = model, id = "gen-$n")
    if (since.none { a -> (a["tool_calls"] as? JsonArray)?.any { (it as JsonObject)["function"].toString().contains("\"reply\"") } == true }) {
        val conv = Regex("""conversation_id=\\"([\w-]+)""").find(m.last { it.role() == "user" }.toString())?.groupValues?.get(1) ?: "main"
        return routerSse(null, listOf(Triple("call_r$n", "reply", obj("conversation_id" to conv, "text" to "**Mock OpenRouter** · peč = stove"))), model = model, id = "gen-$n")
    }
    return routerSse("Opravljeno.", reasoning = null, model = model, id = "gen-$n")
}
