package si.lanisce.lani.tutor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * OpenAI's Responses API for the tutor (docs/plans/05-built/providers.md): `POST https://api.openai.com/v1/responses`
 * with `stream: true` and `store: false`, by an API key or the learner's ChatGPT plan ([ChatGPTAuth]). Plain OkHttp and
 * kotlinx.serialization, as the Messages API's client; no SDK.
 */

/** Where an OpenAI credential may go: the API's bearer to api.openai.com only, the sign-in's tokens to auth.openai.com only. */
object OpenAIGuard {
    const val API = "https://api.openai.com"
    const val AUTH = "https://auth.openai.com"

    /** Whether a request to [url] may carry the API key or the ChatGPT access token. */
    fun mayCarryBearer(url: HttpUrl): Boolean = url.scheme == "https" && url.host == "api.openai.com" && url.port == 443

    /** Whether a request to [url] may carry the sign-in's refresh token or authorization code. */
    fun mayCarryOAuth(url: HttpUrl): Boolean = url.scheme == "https" && url.host == "auth.openai.com" && url.port == 443
}

/** The bearer a call goes with: the API key or the ChatGPT access token, and how its calls are paid ([Billing]). */
data class OpenAIBearer(val token: String, val billing: String) {
    override fun toString(): String = "OpenAIBearer(‹…›, $billing)"
}

/**
 * The Responses API's threads: input items, as the guides replay them with `store: false` (the reasoning guide,
 * "Preserve reasoning without stored responses": "preserve every output item, append the next user message, and replay
 * the complete history"). A user message is `{"type": "message", "role": "user", "content": [input_text, input_image]}`;
 * the model's output items (reasoning with its `encrypted_content`, messages with their `phase`, function calls) go back
 * as they came; a tool's result is a `function_call_output`; an effort change is a `configuration_update` item placed
 * before the next user message (GPT-6 models: [ModelCatalog.perMessageEffort]).
 */
object OpenAIFormat : ThreadFormat {
    /** The part of a neutral block: text as `input_text`, a base64 image as `input_image` with a data URL. */
    fun part(b: JsonObject): JsonObject = when (b.str("type")) {
        "text" -> buildJsonObject {
            put("type", "input_text")
            put("text", b.str("text").orEmpty())
        }
        "image" -> {
            val source = b["source"] as? JsonObject
            buildJsonObject {
                put("type", "input_image")
                put("image_url", "data:${source?.str("media_type") ?: "image/jpeg"};base64,${source?.str("data").orEmpty()}")
                put("detail", "auto")
            }
        }
        "input_text", "input_image" -> b
        else -> buildJsonObject {
            put("type", "input_text")
            put("text", b.toString())
        }
    }

    override fun user(blocks: List<JsonObject>): JsonObject = native(blocks.map(::part))

    private fun native(parts: List<JsonObject>): JsonObject = buildJsonObject {
        put("type", "message")
        put("role", "user")
        put("content", JsonArray(parts))
    }

    /** An effort change for the next user message and after (the reasoning guide, "Change reasoning mid-conversation"). */
    fun update(level: String): JsonObject = buildJsonObject {
        put("type", "configuration_update")
        putJsonObject("reasoning") { put("effort", level) }
    }

    private fun isUpdate(m: JsonObject) = m.str("type") == "configuration_update"
    private fun isUser(m: JsonObject) = m.role() == "user"

    /** One of the model's output items: a reasoning item, a function call, an assistant message. */
    fun isOutput(m: JsonObject): Boolean = m.str("type") == "reasoning" || m.str("type") == "function_call" || m.role() == "assistant"

    /**
     * As [Effort.append] does for the Messages API: the blocks become a new user message, or join the last one when it
     * is a user's (a turn that stopped before its answer); an update placed at the unanswered end is placed again, never
     * stacked ("the API rejects adjacent updates"), and never moves anything a reply came after.
     */
    override fun append(messages: List<JsonObject>, blocks: List<JsonObject>, top: String?, want: String?): List<JsonObject> {
        var head = messages
        if (head.lastOrNull()?.let(::isUpdate) == true) head = head.dropLast(1)
        val last = head.lastOrNull()
        val (before, user) = if (last != null && isUser(last)) {
            var b = head.dropLast(1)
            if (b.lastOrNull()?.let(::isUpdate) == true) b = b.dropLast(1)
            b to native(last.blocks() + blocks.map(::part))
        } else {
            head to user(blocks)
        }
        val change = want != null && want != Effort.inEffect(before, top)
        return if (change) before + update(want!!) + user else before + user
    }

    override fun pending(messages: List<JsonObject>): List<ToolUse>? {
        val answer = messages.takeLastWhile(::isOutput)
        if (answer.isEmpty()) return null
        return answer.filter { it.str("type") == "function_call" }.map(::toolUse)
    }

    /** A `function_call` item as a tool call: its `call_id`, its name, its `arguments` (a JSON string) parsed. */
    fun toolUse(fc: JsonObject): ToolUse {
        val raw = fc.str("arguments").orEmpty()
        val input = if (raw.isBlank()) JsonObject(emptyMap()) else runCatching { tutorJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        return ToolUse(fc.str("call_id").orEmpty(), fc.str("name").orEmpty(), input ?: JsonObject(emptyMap()), if (input == null) raw else null)
    }

    /**
     * Each call's `function_call_output` by its `call_id`. The Responses API has no error flag on a result, so a failed
     * one says so in its first word ("Error: …"), where the Messages API sets `is_error`.
     */
    override fun results(calls: List<ToolUse>, outcomes: List<ToolOutcome>): List<JsonObject> = calls.zip(outcomes).map { (u, o) ->
        buildJsonObject {
            put("type", "function_call_output")
            put("call_id", u.id)
            put("output", if (o.isError) "Error: ${o.content}" else o.content)
        }
    }
}

/**
 * The Responses API as a [TutorProvider] over [transport], paid by [billing] ([Billing.API_KEY] or
 * [Billing.CHATGPT_PLAN]); [renew] gets a fresh credential after a 401 (the ChatGPT sign-in refreshes its access token)
 * and the call is tried once more.
 *
 * The request (the API reference, POST /v1/responses; for the ChatGPT plan the sign-in guide's "Preview limitations"):
 * - `instructions`: the core prompt (system-role messages are rejected on the plan's route), then the tools: the
 *   stable prefix, the same in every request, so the prompt cache holds (automatic past 1,024 tokens; `prompt_cache_key`
 *   by the prefix's fingerprint);
 * - the tools: the bridge's JSON schemas as function tools with `strict: false` (strict mode needs every property
 *   required and `additionalProperties: false` on every object, which most of them aren't: [OpenAIProvider.strictFits]);
 *   with the ChatGPT plan grouped in one `namespace` ("Group function/custom tools in namespaces");
 * - `reasoning.effort` where the model takes one ([ModelCatalog.openAIEfforts]); reasoning items come back with their
 *   `encrypted_content` and are replayed (`include: ["reasoning.encrypted_content"]`, accepted as the legacy way to ask);
 * - `store: false`, `stream: true`; `max_output_tokens` with an API key only (the plan's route rejects it); no
 *   temperature, top_p, metadata, truncation, safety_identifier or user.
 */
class OpenAIProvider(
    private val transport: OpenAITransport,
    override val billing: String,
    private val renew: (suspend () -> Boolean)? = null,
) : TutorProvider {
    override val id = ProviderId.OPENAI
    override val format: ThreadFormat = OpenAIFormat

    override suspend fun models(): List<ModelInfo> = transport.models()

    override fun prefixHash(prefix: Prefix): String = fingerprint(tools(prefix, billing).toString() + "\u0000" + prefix.system + "\u0000" + billing)

    override fun request(prefix: Prefix, model: String, task: Task, messages: List<JsonObject>, maxTokens: Int, effort: String?): JsonObject = buildJsonObject {
        put("model", model)
        put("instructions", prefix.system)
        put("input", JsonArray(messages.toList())) // a snapshot: the loop's list grows after the call
        put("tools", tools(prefix, billing))
        put("tool_choice", "auto")
        if (effort != null && ModelCatalog.efforts(model).isNotEmpty()) putJsonObject("reasoning") { put("effort", effort) }
        put("store", false)
        put("stream", true)
        put("include", JsonArray(listOf(JsonPrimitive("reasoning.encrypted_content"))))
        put("prompt_cache_key", "lani-tutor-" + prefixHash(prefix))
        if (billing == Billing.API_KEY) put("max_output_tokens", maxTokens)
    }

    override fun toolsOff(body: JsonObject): JsonObject = JsonObject(body + ("tool_choice" to JsonPrimitive("none")))

    override suspend fun call(body: JsonObject, onDelta: () -> Unit): Step = callTools(body, onDelta) {}

    override suspend fun callTools(body: JsonObject, onDelta: () -> Unit, onTool: (ToolInputDelta) -> Unit): Step = try {
        transport.stream(body, onDelta, onTool)
    } catch (e: ApiError.Auth) {
        if (renew?.invoke() == true) transport.stream(body, onDelta, onTool) else throw e
    }

    companion object {
        /** The namespace the tools go in with the ChatGPT plan. */
        const val NAMESPACE = "lani"

        private fun fingerprint(s: String): String =
            java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

        /** The prefix's tools (the Messages API's definitions) as function tools, in the same order. */
        fun functions(prefix: Prefix): List<JsonObject> = prefix.tools.mapNotNull { it as? JsonObject }.map { def ->
            // a tool the Messages API knows by its type (the text editor): its function form ([Prefix.alternates])
            val d = def.str("type")?.let { prefix.alternates[def.str("name")] } ?: def
            buildJsonObject {
                put("type", "function")
                put("name", d.str("name").orEmpty())
                put("description", d.str("description").orEmpty())
                put("parameters", d["input_schema"] ?: JsonObject(mapOf("type" to JsonPrimitive("object"))))
                put("strict", false)
            }
        }

        /** The request's `tools`: the functions, or with the ChatGPT plan one namespace holding them. */
        fun tools(prefix: Prefix, billing: String): JsonArray = if (billing == Billing.CHATGPT_PLAN) {
            JsonArray(
                listOf(
                    buildJsonObject {
                        put("type", "namespace")
                        put("name", NAMESPACE)
                        put("description", "Lani's tutor tools: reply to the learner, read their data and the guide, and change their data.")
                        put("tools", JsonArray(functions(prefix)))
                    },
                ),
            )
        } else JsonArray(functions(prefix))

        /**
         * What keeps [schema] out of strict mode (the function-calling guide, "Strict mode": "additionalProperties must
         * be set to false for each object", "All fields in properties must be marked as required"): empty when it fits.
         */
        fun strictFits(schema: JsonObject, at: String = "$"): List<String> {
            val out = mutableListOf<String>()
            val type = schema["type"]
            val isObject = (type as? JsonPrimitive)?.content == "object" || (type as? JsonArray)?.any { (it as? JsonPrimitive)?.content == "object" } == true
            if (isObject) {
                val props = schema["properties"] as? JsonObject ?: JsonObject(emptyMap())
                if (schema["additionalProperties"] != JsonPrimitive(false)) out += "$at: additionalProperties isn't false"
                val required = (schema["required"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }.toSet()
                props.keys.filter { it !in required }.forEach { out += "$at.$it: not required" }
                props.forEach { (k, v) -> (v as? JsonObject)?.let { out += strictFits(it, "$at.$k") } }
            }
            (schema["items"] as? JsonObject)?.let { out += strictFits(it, "$at[]") }
            (schema["anyOf"] as? JsonArray)?.forEachIndexed { i, a -> (a as? JsonObject)?.let { out += strictFits(it, "$at|$i") } }
            return out
        }
    }
}

/**
 * The Responses API's client: GET /v1/models and POST /v1/responses (streamed), with the [bearer] (an API key or the
 * ChatGPT access token: `Authorization: Bearer`) to api.openai.com only ([OpenAIGuard]). Throws [ApiError] ([OpenAIErrors]).
 */
class OpenAITransport(
    private val bearer: suspend () -> OpenAIBearer?,
    private val client: OkHttpClient = defaultClient,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private suspend fun request(path: String): Request.Builder {
        val b = bearer()?.takeIf { it.token.isNotBlank() } ?: throw ApiError.Auth("no OpenAI credential")
        val url = (OpenAIGuard.API + path).toHttpUrl()
        check(OpenAIGuard.mayCarryBearer(url)) { "the OpenAI credential goes only to the Responses API" }
        return Request.Builder().url(url).header("Authorization", "Bearer ${b.token}")
    }

    /**
     * The models the credential may use: an API key's (`data[].id`, `created` in Unix seconds) or the ChatGPT plan's
     * catalog (`models[]` with `slug`, `display_name` and `visibility`; only those shown, "visibility": "list", in the
     * server's order: the sign-in guide's "Models and inference").
     */
    suspend fun models(): List<ModelInfo> {
        val req = request("/v1/models").get().build()
        val (code, body, retryAfter) = execute(req) { r -> Triple(r.code, r.body?.string().orEmpty(), r.header("retry-after")) }
        if (code !in 200..299) throw OpenAIErrors.of(code, body, retryAfter)
        return parseModels(body) ?: throw ApiError.Overloaded(code, "GET /v1/models: an answer without models")
    }

    /**
     * POST /v1/responses with [body] (`stream: true` set); [onDelta] for each piece of content as it comes. The events
     * are read line by line (server-sent events: `data:` lines, a blank line ends one), whatever the content type says:
     * the plan's route may send a valid stream without one (the sign-in devkit's responses.ts), and only a finished
     * response (response.completed, .incomplete) counts.
     */
    suspend fun stream(body: JsonObject, onDelta: () -> Unit = {}, onTool: (ToolInputDelta) -> Unit = {}): Step {
        val sent = JsonObject(body + ("stream" to JsonPrimitive(true)))
        val req = request("/v1/responses").header("accept", "text/event-stream").post(sent.toString().toRequestBody(JSON)).build()
        val started = clock()
        val acc = ResponsesAccumulator()
        var first: Long? = null
        execute(req) { r ->
            if (!r.isSuccessful) throw OpenAIErrors.of(r.code, r.body?.string().orEmpty(), r.header("retry-after"))
            val type = r.header("content-type")?.substringBefore(';')?.trim()?.lowercase()
            if (type != null && type != "text/event-stream") throw OpenAIErrors.of(502, r.body?.string().orEmpty().take(500), null)
            val source = r.body?.source() ?: throw ApiError.Network("an answer without a body")
            val data = StringBuilder()
            fun dispatch() {
                if (data.isEmpty()) return
                if (acc.accept(data.toString())) {
                    if (first == null) first = clock() - started
                    onDelta()
                    acc.takeTool()?.let { runCatching { onTool(it) } }
                }
                data.setLength(0)
            }
            while (true) {
                val line = source.readUtf8Line() ?: break
                when {
                    line.isEmpty() -> dispatch()
                    line.startsWith(":") -> Unit
                    line.startsWith("data:") -> {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.removePrefix("data:").removePrefix(" "))
                    }
                    else -> Unit // event:, id:, retry:: the data says its type
                }
                if (acc.error != null) break
            }
            dispatch()
        }
        acc.error?.let { throw it }
        if (!acc.finished) throw ApiError.Network("the stream ended before response.completed")
        return acc.step(first, clock() - started)
    }

    private suspend fun <T> execute(req: Request, read: (Response) -> T): T = withContext(Dispatchers.IO) {
        val call = client.newCall(req)
        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { if (it != null) call.cancel() }
        try {
            call.execute().use(read)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError) {
            throw e
        } catch (e: IOException) {
            throw ApiError.Network(e.message ?: e.javaClass.simpleName)
        } finally {
            handle?.dispose()
        }
    }

    companion object {
        private val JSON = "application/json".toMediaType()

        /**
         * A turn's call streams for seconds to minutes: at `xhigh` and `max` the model may reason for long without an
         * event (whether the API sends keep-alives in between isn't documented), so the read timeout is long.
         */
        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(600, TimeUnit.SECONDS).followRedirects(false).build()
        }

        /** The matrix's lanes' client: a dispatcher of its own, as [AnthropicTransport.laneClient]. */
        val laneClient: OkHttpClient by lazy {
            defaultClient.newBuilder().dispatcher(Dispatcher().apply { maxRequests = 32; maxRequestsPerHost = 16 }).build()
        }

        /** The models of a GET /v1/models answer ([body]): either shape, or null when it has neither. */
        fun parseModels(body: String): List<ModelInfo>? {
            val o = runCatching { tutorJson.parseToJsonElement(body) as JsonObject }.getOrNull() ?: return null
            (o["data"] as? JsonArray)?.let { data ->
                return data.mapNotNull { e ->
                    val m = e as? JsonObject ?: return@mapNotNull null
                    val created = (m["created"] as? JsonPrimitive)?.longOrNull
                    ModelInfo(id = m.str("id") ?: return@mapNotNull null, createdAt = created?.let { Instant.ofEpochSecond(it).toString() })
                }
            }
            (o["models"] as? JsonArray)?.let { models ->
                return models.mapNotNull { e ->
                    val m = e as? JsonObject ?: return@mapNotNull null
                    if (m.str("visibility")?.let { it != "list" } == true) return@mapNotNull null
                    ModelInfo(id = m.str("slug") ?: m.str("id") ?: return@mapNotNull null, displayName = m.str("display_name"))
                }
            }
            return null
        }
    }
}

/**
 * Puts a streamed response back together from its events (the API reference's streaming events): `response.created`,
 * `response.output_item.added` / `.done`, `response.output_text.delta`, `response.function_call_arguments.delta`, the
 * reasoning summary's deltas, `response.completed` / `.incomplete` / `.failed`, `error`. The items of
 * `response.output_item.done` are the final ones (a reasoning item's `encrypted_content` "in response.output_item.added
 * may be incomplete"); `response.completed`'s `response.output`, when it has them, is the whole answer.
 */
class ResponsesAccumulator {
    private val added = sortedMapOf<Int, JsonObject>()
    private val done = sortedMapOf<Int, JsonObject>()
    private val args = mutableMapOf<Int, StringBuilder>()
    private val texts = mutableMapOf<Int, StringBuilder>()
    private var response: JsonObject? = null
    private var id: String? = null
    private var model: String? = null
    /** A response.failed for a policy reason: a refusal, not an error. */
    private var refusedFor: String? = null

    /** The response ended (completed, incomplete, or failed for a policy reason). */
    var finished = false
        private set
    /** The function call whose arguments grew last ([takeTool]). */
    private var tool: ToolInputDelta? = null

    /** The function call whose arguments grew since the last call (its arguments so far), once; null when none did. */
    fun takeTool(): ToolInputDelta? = tool.also { tool = null }
    /** An `error` event, or a `response.failed` for any other reason. */
    var error: ApiError? = null
        private set

    /** Takes one event's [data]; true when it carried content (a delta): the first one is the first token's time. */
    fun accept(data: String): Boolean {
        val o = runCatching { tutorJson.parseToJsonElement(data) as? JsonObject }.getOrNull() ?: return false
        fun index() = (o["output_index"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        when (o.str("type")) {
            "response.created", "response.in_progress", "response.queued" -> (o["response"] as? JsonObject)?.let { r ->
                id = r.str("id") ?: id
                model = r.str("model") ?: model
            }
            "response.output_item.added" -> index()?.let { i -> (o["item"] as? JsonObject)?.let { added[i] = it } }
            "response.output_item.done" -> index()?.let { i -> (o["item"] as? JsonObject)?.let { done[i] = it } }
            "response.output_text.delta" -> {
                index()?.let { i -> texts.getOrPut(i) { StringBuilder() }.append(o.str("delta").orEmpty()) }
                return true
            }
            "response.function_call_arguments.delta" -> {
                index()?.let { i ->
                    val a = args.getOrPut(i) { StringBuilder() }.append(o.str("delta").orEmpty())
                    val item = added[i]
                    tool = ToolInputDelta(item?.str("call_id") ?: o.str("item_id").orEmpty(), item?.str("name").orEmpty(), a.toString())
                }
                return true
            }
            "response.refusal.delta", "response.reasoning_summary_text.delta", "response.reasoning_text.delta" -> return true
            "response.completed", "response.incomplete" -> {
                response = o["response"] as? JsonObject
                finished = true
            }
            "response.failed" -> {
                response = o["response"] as? JsonObject
                val err = response?.get("error") as? JsonObject
                if (OpenAIErrors.policy(err?.str("code"))) {
                    refusedFor = err?.str("code")
                    finished = true
                } else error = OpenAIErrors.ofFailed(err)
            }
            "error" -> error = OpenAIErrors.ofStream(o)
            else -> Unit
        }
        return false
    }

    /** The output items: the completed response's, else each item's final form, else as added with the deltas. */
    private fun items(): List<JsonObject> {
        val final = (response?.get("output") as? JsonArray)?.mapNotNull { it as? JsonObject }
        val raw = when {
            !final.isNullOrEmpty() -> final
            done.isNotEmpty() -> (added.keys + done.keys).toSortedSet().mapNotNull { done[it] ?: added[it] }
            else -> added.map { (i, item) ->
                when {
                    item.str("type") == "function_call" && args[i] != null -> JsonObject(item + ("arguments" to JsonPrimitive(args[i].toString())))
                    else -> item
                }
            }
        }
        // what an item holds only as the API's provenance (the SDKs strip it before a replay: toResponseInputItem); a
        // reasoning item without its encrypted content can't be replayed with store: false (its id isn't stored)
        return raw.map { JsonObject(it - "created_by") }.filter { it.str("type") != "reasoning" || it.str("encrypted_content") != null }
    }

    /** The response as a [Step], after [first] ms to its first token and [total] in all. */
    fun step(first: Long?, total: Long): Step {
        val r = response
        val items = items()
        val calls = items.filter { it.str("type") == "function_call" }.map(OpenAIFormat::toolUse)
        val messages = items.filter { it.str("type") == "message" && it.role() == "assistant" }
        val parts = messages.flatMap { it.blocks() }
        val text = parts.filter { it.str("type") == "output_text" }.joinToString("\n") { it.str("text").orEmpty() }
        val refusal = parts.firstOrNull { it.str("type") == "refusal" }?.str("refusal")
        val incomplete = (r?.get("incomplete_details") as? JsonObject)?.str("reason")
        val stop = when {
            refusedFor != null -> Stop.REFUSAL
            r?.str("status") == "incomplete" && incomplete == "max_output_tokens" -> Stop.MAX_TOKENS
            incomplete == "content_filter" -> Stop.REFUSAL
            refusal != null -> Stop.REFUSAL
            calls.isNotEmpty() -> Stop.TOOL_USE
            r?.str("status") == "incomplete" -> incomplete ?: "incomplete"
            else -> Stop.END_TURN
        }
        val details = when {
            refusedFor != null -> buildJsonObject { put("type", "refusal"); put("category", refusedFor) }
            incomplete == "content_filter" -> buildJsonObject { put("type", "refusal"); put("category", "content_filter") }
            refusal != null -> buildJsonObject { put("type", "refusal"); put("refusal", refusal.take(500)) }
            else -> null
        }
        val usage = r?.get("usage") as? JsonObject
        val kept = messages.filter { m -> m.blocks().any { it.str("type") == "output_text" && !it.str("text").isNullOrBlank() } }
        return Step(
            items = items,
            toolCalls = calls,
            text = text,
            stopReason = stop,
            stopDetails = details,
            usage = OpenAIUsage.of(usage),
            model = r?.str("model") ?: model,
            id = r?.str("id") ?: id,
            firstTokenMs = first,
            totalMs = total,
            visibleChars = text.length + calls.sumOf { it.name.length + (it.badJson?.length ?: it.input.toString().length) },
            kept = kept,
            rawUsage = RawUsage(delta = usage),
        )
    }
}

/** The Responses API's `usage` as [Usage]. */
object OpenAIUsage {
    /**
     * [u]: `input_tokens` counts the cached and the written-to-cache tokens too (the prompt-caching guide's example:
     * 15,000 input with 12,000 cached and 3,000 written), so [Usage.input] is the rest; the writes are filed as
     * [Usage.cacheWrite5m]; [Usage.reasoning] from `output_tokens_details.reasoning_tokens`.
     */
    fun of(u: JsonObject?): Usage {
        if (u == null) return Usage()
        fun n(o: JsonObject?, k: String) = (o?.get(k) as? JsonPrimitive)?.longOrNull ?: 0
        val inDetails = u["input_tokens_details"] as? JsonObject
        val outDetails = u["output_tokens_details"] as? JsonObject
        val cached = n(inDetails, "cached_tokens")
        val written = n(inDetails, "cache_write_tokens")
        return Usage(
            input = (n(u, "input_tokens") - cached - written).coerceAtLeast(0),
            cacheWrite5m = written,
            cacheRead = cached,
            output = n(u, "output_tokens"),
            reasoning = n(outDetails, "reasoning_tokens"),
        )
    }
}

/**
 * OpenAI's errors as [ApiError] (the error-codes guide; for the ChatGPT plan the sign-in guide's "Errors and recovery"):
 * - 401 → [ApiError.Auth]; a 403 (the plan not allowed for this user or workspace, a region, a permission) too, but for
 *   subscription_sharing_route_not_supported (a request this app shouldn't send: [ApiError.BadRequest]);
 * - 429: credit_balance_exhausted and insufficient_quota → [ApiError.LowCredit]; the organization's or project's spend
 *   limit and the organization's usage limit → [ApiError.LowCredit] with `limit`; the ChatGPT plan's usage limit
 *   (subscription_sharing_usage_limit_exceeded) → [ApiError.LowCredit] with `allowance`; any other 429 (rate limits,
 *   slow_down) → [ApiError.RateLimited] after its `retry-after`;
 * - 408, 409, 5xx (server_is_overloaded, the plan's usage or user "unavailable", direct routing off) → [ApiError.Overloaded];
 * - the rest (400 with subscription_sharing_unsupported_capability, context too long, …) → [ApiError.BadRequest].
 * A response that failed for a policy reason is a refusal ([policy]), not an error.
 */
object OpenAIErrors {
    private val CREDIT = setOf("credit_balance_exhausted", "insufficient_quota")
    private val SPEND = setOf("organization_spend_limit_exceeded", "project_spend_limit_exceeded", "organization_usage_limit_exceeded")
    const val ALLOWANCE = "subscription_sharing_usage_limit_exceeded"
    private val AUTH_CODES = setOf(
        "invalid_api_key", "subscription_sharing_invalid_user", "subscription_sharing_user_not_eligible",
        "chatpass_v2_scope_not_authorized", "chatpass_v2_invalid_authorization_context",
    )
    private val UNAVAILABLE = setOf("server_error", "server_is_overloaded", "subscription_sharing_usage_unavailable", "subscription_sharing_user_unavailable")

    /** A response's failure that is the model or a policy declining (a refusal, never retried). */
    fun policy(code: String?): Boolean = code != null && (code.contains("policy") || code == "invalid_prompt")

    private data class Said(val type: String?, val message: String?, val code: String?)

    /** The error of a body: `{"error": {"message", "type", "param", "code"}}`, `{"error": "code"}` or `{"detail": "…"}`. */
    private fun said(body: String): Said = runCatching {
        val o = tutorJson.parseToJsonElement(body) as JsonObject
        when (val e = o["error"]) {
            is JsonObject -> Said(e.str("type"), e.str("message"), e.str("code"))
            is JsonPrimitive -> Said(null, o.str("error_description") ?: e.content, e.content)
            else -> Said(null, o.str("detail") ?: o.str("message"), o.str("code"))
        }
    }.getOrDefault(Said(null, null, null))

    /** An HTTP answer [code] with [body] and its `retry-after` header. */
    fun of(code: Int, body: String, retryAfter: String? = null): ApiError {
        val s = said(body)
        val text = "HTTP $code${s.type?.let { " $it" } ?: ""}${s.code?.let { " ($it)" } ?: ""}: ${s.message ?: body.take(200)}"
        return when {
            code == 401 -> ApiError.Auth(text)
            code == 403 && s.code == "subscription_sharing_route_not_supported" -> ApiError.BadRequest(code, text)
            code == 403 -> ApiError.Auth(text)
            code == 429 && s.code == ALLOWANCE -> ApiError.LowCredit(text, limit = true, allowance = true)
            code == 429 && s.code in SPEND -> ApiError.LowCredit(text, limit = true)
            code == 429 && (s.code in CREDIT || s.type == "insufficient_quota") -> ApiError.LowCredit(text)
            code == 429 -> ApiError.RateLimited(ApiError.retryAfterMs(retryAfter), text)
            code == 408 || code == 409 || code >= 500 -> ApiError.Overloaded(code, text)
            else -> ApiError.BadRequest(code, text)
        }
    }

    /** An error by its [code] and [type] in a stream (no HTTP status: the stream had begun). */
    private fun ofCode(code: String?, type: String?, message: String?, where: String): ApiError {
        val text = "$where ${type ?: "error"}${code?.let { " ($it)" } ?: ""}: ${message.orEmpty().take(300)}"
        return when {
            code == ALLOWANCE -> ApiError.LowCredit(text, limit = true, allowance = true)
            code in SPEND -> ApiError.LowCredit(text, limit = true)
            code in CREDIT || type == "insufficient_quota" -> ApiError.LowCredit(text)
            code in AUTH_CODES || type == "authentication_error" -> ApiError.Auth(text)
            code == "rate_limit_exceeded" || code == "slow_down" || type == "rate_limit_error" -> ApiError.RateLimited(null, text)
            code in UNAVAILABLE || type == "server_error" || type == "service_unavailable_error" -> ApiError.Overloaded(if (code?.contains("overloaded") == true) 503 else 500, text)
            type == "invalid_request_error" || code == "subscription_sharing_unsupported_capability" -> ApiError.BadRequest(400, text)
            else -> ApiError.Overloaded(500, text)
        }
    }

    /** An `error` event ([o]: its data; the error's fields beside `type`, or inside `error`). */
    fun ofStream(o: JsonObject): ApiError {
        val e = (o["error"] as? JsonObject) ?: o
        return ofCode(e.str("code"), e.str("type")?.takeIf { it != "error" }, e.str("message"), "stream")
    }

    /** A `response.failed`'s `response.error` ([e]: `{code, message}`). */
    fun ofFailed(e: JsonObject?): ApiError = ofCode(e?.str("code"), e?.str("type"), e?.str("message"), "response failed")
}
