package si.lanisce.lani.tutor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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
import java.util.concurrent.TimeUnit

/**
 * Google's Gemini API for the tutor, direct, with a key from Google AI Studio (docs/plans/05-built/providers.md,
 * "Gemini"): `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent?alt=sse` with
 * `x-goog-api-key`. Google's docs call generateContent "legacy" now (the Interactions API is their new default) and say
 * it "remains fully supported". Plain OkHttp and kotlinx.serialization, as the other providers; no SDK.
 */

/** Where the Gemini key may go: the Gemini API only. */
object GeminiGuard {
    const val API = "https://generativelanguage.googleapis.com"

    fun mayCarryKey(url: HttpUrl): Boolean = url.scheme == "https" && url.host == "generativelanguage.googleapis.com" && url.port == 443
}

/**
 * What Google's terms say of the free tier (the Gemini API Additional Terms of Service, ai.google.dev/gemini-api/terms,
 * effective 2026-03-23, read 2026-10-19; quoted in docs/plans/05-built/providers.md): on "Unpaid Services, including …
 * the unpaid quota on Gemini API, Google uses the content you submit to the Services and any generated responses to
 * provide, improve, and develop Google products", "human reviewers may read, annotate, and process your API input and
 * output", "Do not submit sensitive, confidential, or personal information to the Unpaid Services". The app says so
 * plainly while the key is on the free tier (the Tutor section, and once in the chat).
 */
object GeminiTerms {
    const val TERMS = "https://ai.google.dev/gemini-api/terms"
    const val AI_STUDIO = "https://aistudio.google.com/apikey"
    const val RATE_LIMITS = "https://aistudio.google.com/usage"

    /** The key's tier as the learner said it, or as a 429 showed it (the free tier's quota named in the error). */
    const val FREE = "free"
    const val FREE_SEEN = "free-seen"
    const val PAID = "paid"

    fun isFree(tier: String?): Boolean = tier == FREE || tier == FREE_SEEN
}

/**
 * Gemini's threads: `contents`, each `{"role": "user" | "model", "parts": [...]}`. A user turn's parts are `text` and
 * `inlineData` (a photo); a model turn is kept exactly as it came, parts and `thoughtSignature`s unchanged and in
 * order ("you must pass back thought signatures during function calling", "Don't concatenate parts with signatures
 * together": the thought-signatures guide); a tool's result is a `functionResponse` part by the call's `id` ("Gemini 3
 * now always returns a unique id with every functionCall. Include this exact id in your functionResponse"). A call
 * that came without an id gets one of ours, kept beside the parts (`lani_ids`) and never sent.
 *
 * No effort changes within a thread: `thinkingLevel` is a request's own setting, and Google's docs don't say whether a
 * thread may change it from one request to the next, or what that does to the implicit cache (OpenRouter's reasoning
 * guide says a request-level change "invalidates the prompt cache"). A thread keeps its level ([ModelCatalog.perMessageEffort]
 * is false for Gemini); another task's level is a stateless call.
 */
object GeminiFormat : ThreadFormat {
    /** Our id of a call that came without one: never sent to the API. */
    const val OWN_ID = "lani_gc_"
    const val IDS = "lani_ids"

    /** A content's parts. */
    fun parts(m: JsonObject): List<JsonObject> = (m["parts"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    /** The part of a neutral block: text as `text`, a base64 image as `inlineData`. */
    fun part(b: JsonObject): JsonObject = when {
        b.str("type") == "text" -> buildJsonObject { put("text", b.str("text").orEmpty()) }
        b.str("type") == "image" -> {
            val source = b["source"] as? JsonObject
            buildJsonObject {
                putJsonObject("inlineData") {
                    put("mimeType", source?.str("media_type") ?: "image/jpeg")
                    put("data", source?.str("data").orEmpty())
                }
            }
        }
        b["text"] != null || b["inlineData"] != null || b["functionResponse"] != null -> b
        else -> buildJsonObject { put("text", b.toString()) }
    }

    private fun content(role: String, parts: List<JsonObject>) = buildJsonObject {
        put("role", role)
        put("parts", JsonArray(parts))
    }

    override fun user(blocks: List<JsonObject>): JsonObject = content("user", blocks.map(::part))

    /** The blocks as a new user turn, or joined to the last one when it is a user's (a turn that stopped before its answer). */
    override fun append(messages: List<JsonObject>, blocks: List<JsonObject>, top: String?, want: String?): List<JsonObject> {
        val last = messages.lastOrNull()
        return if (last != null && last.role() == "user") messages.dropLast(1) + content("user", parts(last) + blocks.map(::part))
        else messages + user(blocks)
    }

    override fun pending(messages: List<JsonObject>): List<ToolUse>? {
        val last = messages.lastOrNull() ?: return null
        if (last.role() != "model") return null
        return calls(last)
    }

    /** The function calls of model turn [m], in order, with their ids (the API's, else ours). */
    fun calls(m: JsonObject): List<ToolUse> {
        val ours = (m[IDS] as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.content.orEmpty() }
        return parts(m).mapNotNull { it["functionCall"] as? JsonObject }.mapIndexed { i, fc ->
            val args = fc["args"]
            ToolUse(fc.str("id") ?: ours.getOrNull(i) ?: "$OWN_ID$i", fc.str("name").orEmpty(), args as? JsonObject ?: JsonObject(emptyMap()), if (args != null && args !is JsonObject) args.toString() else null)
        }
    }

    /**
     * Each call's `functionResponse` by its id, in one user turn: `{"output": …}`, or `{"error": …}` for a failed one
     * (the API reference: "Use 'output' key to specify function output and 'error' key to specify error details").
     */
    override fun results(calls: List<ToolUse>, outcomes: List<ToolOutcome>): List<JsonObject> = listOf(
        content(
            "user",
            calls.zip(outcomes).map { (u, o) ->
                buildJsonObject {
                    putJsonObject("functionResponse") {
                        if (!u.id.startsWith(OWN_ID)) put("id", u.id)
                        put("name", u.name)
                        putJsonObject("response") { put(if (o.isError) "error" else "output", o.content) }
                    }
                }
            },
        ),
    )

    /** [m] as the API takes it: its role and parts only (our ids left out). */
    fun wire(m: JsonObject): JsonObject = JsonObject(m.filterKeys { it == "role" || it == "parts" })
}

/**
 * The free tier's pace (one per key: its quota is the project's, shared by the primary and the matrix's lanes). Google
 * doesn't publish the free tier's limits per model ("can be viewed in Google AI Studio"); the lead read 5–15 requests a
 * minute and about 1,000 a day. On the free tier calls go at most one per [baseGapMs] (10 a minute); a 429 doubles the
 * gap (at most a minute) and waits its retry delay; calls that go through bring it back. A paid key isn't paced.
 */
class GeminiPacer(private val clock: () -> Long = System::currentTimeMillis, private val sleep: suspend (Long) -> Unit = { delay(it) }, private val baseGapMs: Long = BASE_GAP_MS) {
    @Volatile var free: Boolean = false
    private val lock = Mutex()
    private var next = 0L
    @Volatile var gapMs: Long = baseGapMs
        private set

    /** Waits for the next call's slot (free tier only). */
    suspend fun await() {
        if (!free) return
        lock.withLock {
            val now = clock()
            if (next > now) sleep(next - now)
            next = maxOf(clock(), next) + gapMs
        }
    }

    /** A 429: slower from now on, and not before its retry delay. */
    fun limited(retryAfterMs: Long?) {
        if (!free) return
        gapMs = (gapMs * 2).coerceAtMost(MAX_GAP_MS)
        next = maxOf(next, clock() + (retryAfterMs ?: gapMs))
    }

    /** A call went through: the gap eases back. */
    fun ok() {
        gapMs = maxOf(baseGapMs, gapMs * 3 / 4)
    }

    companion object {
        const val BASE_GAP_MS = 6_000L
        const val MAX_GAP_MS = 60_000L
    }
}

/**
 * The Gemini key ([key], encrypted as the others are) and its tier ([tier]: what the learner said, or what a 429 showed;
 * no API tells it: Google documents none), with its [pacer].
 */
class GeminiCredentials(val key: KeyVault, private val tierStore: SecretStore, val pacer: GeminiPacer = GeminiPacer()) {
    suspend fun has(): Boolean = key.has()

    /** [GeminiTerms.FREE], [GeminiTerms.FREE_SEEN], [GeminiTerms.PAID], or null (not said). */
    suspend fun tier(): String? = tierStore.read().also { pacer.free = GeminiTerms.isFree(it) }

    suspend fun setTier(t: String?) {
        tierStore.write(t)
        pacer.free = GeminiTerms.isFree(t)
    }

    /** How its calls are paid: [Billing.FREE_TIER] on the free tier, else [Billing.API_KEY]. */
    suspend fun billing(): String = if (GeminiTerms.isFree(tier())) Billing.FREE_TIER else Billing.API_KEY

    suspend fun remove() {
        key.remove()
        tierStore.write(null)
        pacer.free = false
    }

    companion object {
        fun android(context: android.content.Context): GeminiCredentials = GeminiCredentials(
            KeyVault(KeystoreCipher("lani-tutor-gemini-key"), DataStoreSecretStore(context, "gemini_key_sealed")),
            DataStoreSecretStore(context, "gemini_tier"),
        )
    }
}

/**
 * The Gemini API as a [TutorProvider] over [transport], paid by [billing] ([Billing.API_KEY], or [Billing.FREE_TIER]).
 *
 * The request (the API reference, models.streamGenerateContent; the model goes in the URL, so the body carries it as
 * `model` for the transport, which leaves it out):
 * - `systemInstruction`: the core prompt; `tools`: one `functionDeclarations` with the bridge's JSON schemas as
 *   `parametersJsonSchema` (JSON Schema; `additionalProperties` and `anyOf` are taken, no tool uses `$ref`), in the
 *   prefix's order, the same in every request, so the implicit cache holds (on by default from Gemini 2.5; at least
 *   4,096 tokens on 4 Argon and 3.8 Flash; no explicit caching on Argon);
 * - `toolConfig.functionCallingConfig.mode: AUTO` (the default with function declarations only);
 * - `generationConfig`: `maxOutputTokens` (thinking counts toward it on Argon) and `thinkingConfig.thinkingLevel`
 *   where the model takes one ([ModelCatalog.geminiLevels]; written lowercase, as the docs' REST examples do). No
 *   temperature, top_p or top_k: "If provided, sampling parameters are ignored" (Argon), deprecated from 3.6 Flash.
 */
class GeminiProvider(private val transport: GeminiTransport, override val billing: String = Billing.API_KEY) : TutorProvider {
    override val id = ProviderId.GEMINI
    override val format: ThreadFormat = GeminiFormat

    override suspend fun models(): List<ModelInfo> = transport.models()

    override fun prefixHash(prefix: Prefix): String = fingerprint("gemini\u0000" + functions(prefix) + "\u0000" + prefix.system)

    override fun request(prefix: Prefix, model: String, task: Task, messages: List<JsonObject>, maxTokens: Int, effort: String?): JsonObject = buildJsonObject {
        put("model", model)
        putJsonObject("systemInstruction") { put("parts", JsonArray(listOf(buildJsonObject { put("text", prefix.system) }))) }
        put("contents", JsonArray(messages.filter { it.role() == "user" || it.role() == "model" }.map(GeminiFormat::wire)))
        put("tools", JsonArray(listOf(buildJsonObject { put("functionDeclarations", functions(prefix)) })))
        putJsonObject("toolConfig") { putJsonObject("functionCallingConfig") { put("mode", "AUTO") } }
        putJsonObject("generationConfig") {
            put("maxOutputTokens", maxTokens)
            ModelCatalog.fit(effort, model)?.takeIf { effort != null }?.let { level -> putJsonObject("thinkingConfig") { put("thinkingLevel", level) } }
        }
    }

    override suspend fun call(body: JsonObject, onDelta: () -> Unit): Step = transport.stream(body, onDelta)

    override suspend fun callTools(body: JsonObject, onDelta: () -> Unit, onTool: (ToolInputDelta) -> Unit): Step = transport.stream(body, onDelta, onTool)

    companion object {
        private fun fingerprint(s: String): String =
            java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

        /** The prefix's tools (the Messages API's definitions) as function declarations, in the same order. */
        fun functions(prefix: Prefix): JsonArray = JsonArray(
            prefix.tools.mapNotNull { it as? JsonObject }.map { def ->
                val d = def.str("type")?.let { prefix.alternates[def.str("name")] } ?: def
                buildJsonObject {
                    put("name", d.str("name").orEmpty())
                    put("description", d.str("description").orEmpty())
                    put("parametersJsonSchema", d["input_schema"] ?: JsonObject(mapOf("type" to JsonPrimitive("object"))))
                }
            },
        )
    }
}

/**
 * The Gemini API's client: GET /v1beta/models and POST …:streamGenerateContent?alt=sse, with the [key] to the Gemini
 * API only ([GeminiGuard]); on the free tier paced by [pacer]. [onFreeTier] hears a 429 that names the free tier's quota
 * (the key is on it). Throws [ApiError] ([GeminiErrors]).
 */
class GeminiTransport(
    private val key: suspend () -> String?,
    private val client: OkHttpClient = defaultClient,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pacer: GeminiPacer? = null,
    private val onFreeTier: () -> Unit = {},
) {
    private suspend fun request(path: String): Request.Builder {
        val k = key()?.takeIf { it.isNotBlank() } ?: throw ApiError.Auth("no Gemini key")
        val url = (GeminiGuard.API + path).toHttpUrl()
        check(GeminiGuard.mayCarryKey(url)) { "the Gemini key goes only to the Gemini API" }
        return Request.Builder().url(url).header("x-goog-api-key", k)
    }

    /**
     * The models the key may use (GET /v1beta/models: `models[]` with `name` "models/…", `displayName`,
     * `inputTokenLimit`, `outputTokenLimit`, `supportedGenerationMethods`): the Gemini ones that generate content.
     */
    suspend fun models(): List<ModelInfo> {
        val req = request("/v1beta/models?pageSize=1000").get().build()
        val (code, body, retryAfter) = execute(req) { r -> Triple(r.code, r.body?.string().orEmpty(), r.header("retry-after")) }
        if (code !in 200..299) throw GeminiErrors.of(code, body, retryAfter)
        val list = runCatching { (tutorJson.parseToJsonElement(body) as JsonObject)["models"] as? JsonArray }.getOrNull()
            ?: throw ApiError.Overloaded(code, "GET /v1beta/models: an answer without models")
        return list.mapNotNull { e ->
            val m = e as? JsonObject ?: return@mapNotNull null
            val id = m.str("name")?.removePrefix("models/") ?: return@mapNotNull null
            val methods = (m["supportedGenerationMethods"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
            if (!id.startsWith("gemini-") || (methods.isNotEmpty() && "generateContent" !in methods)) return@mapNotNull null
            ModelInfo(
                id = id, displayName = m.str("displayName"),
                maxInputTokens = (m["inputTokenLimit"] as? JsonPrimitive)?.longOrNull, maxTokens = (m["outputTokenLimit"] as? JsonPrimitive)?.longOrNull,
            )
        }
    }

    /** One call of [body] ([GeminiProvider.request]'s, its `model` the URL's), streamed as server-sent events. */
    suspend fun stream(body: JsonObject, onDelta: () -> Unit = {}, onTool: (ToolInputDelta) -> Unit = {}): Step {
        val model = body.str("model") ?: throw ApiError.BadRequest(0, "no model")
        val sent = JsonObject(body - "model")
        pacer?.await()
        val req = request("/v1beta/models/$model:streamGenerateContent?alt=sse").header("accept", "text/event-stream")
            .post(sent.toString().toRequestBody(JSON)).build()
        val started = clock()
        val acc = GeminiAccumulator(model)
        var first: Long? = null
        try {
            execute(req) { r ->
                if (!r.isSuccessful) throw failure(r.code, r.body?.string().orEmpty(), r.header("retry-after"))
                val source = r.body?.source() ?: throw ApiError.Network("an answer without a body")
                val data = StringBuilder()
                fun dispatch() {
                    if (data.isEmpty()) return
                    if (acc.accept(data.toString())) {
                        if (first == null) first = clock() - started
                        onDelta()
                        acc.takeTool().forEach { runCatching { onTool(it) } }
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
                        else -> Unit
                    }
                    if (acc.error != null) break
                }
                dispatch()
            }
        } catch (e: ApiError.RateLimited) {
            pacer?.limited(e.retryAfterMs)
            throw e
        }
        acc.error?.let { throw it }
        if (!acc.finished) throw ApiError.Network("the stream ended before its finishReason")
        pacer?.ok()
        return acc.step(first, clock() - started)
    }

    private fun failure(code: Int, body: String, retryAfter: String?): ApiError {
        if (code == 429 && GeminiErrors.freeTier(body)) runCatching(onFreeTier)
        return GeminiErrors.of(code, body, retryAfter)
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
         * Gemini 4 Argon reasons long ("Set HTTP client timeouts to at least 20 minutes per call": the latest-model
         * guide); past 32,768 tokens its stream "sends a checkpoint token every 5,000 tokens".
         */
        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.MINUTES).callTimeout(25, TimeUnit.MINUTES).followRedirects(false).build()
        }

        /** The matrix's lanes' client: a dispatcher of its own, as the other providers' lanes. */
        val laneClient: OkHttpClient by lazy {
            defaultClient.newBuilder().dispatcher(Dispatcher().apply { maxRequests = 32; maxRequestsPerHost = 16 }).build()
        }
    }
}

/**
 * Puts a streamed response back together from its chunks (each a GenerateContentResponse: `candidates[0].content.parts`,
 * `finishReason`, `promptFeedback`, `usageMetadata`, `modelVersion`, `responseId`). Parts are kept as they came, in
 * order; only plain text parts (no signature, not a thought) are joined, so no part with a signature is ever merged
 * with another. A function call comes whole in its part (the docs show no partial calls for generateContent: not
 * verified for a long call); its input goes to the typing bubble at once.
 */
class GeminiAccumulator(private val model: String) {
    private val parts = mutableListOf<JsonObject>()
    private var usage: JsonObject? = null
    private var responseId: String? = null
    private var modelVersion: String? = null
    private var finishReason: String? = null
    private var finishMessage: String? = null
    private var blockReason: String? = null
    private val tools = mutableListOf<ToolInputDelta>()

    var finished = false
        private set
    var error: ApiError? = null
        private set

    /** The function calls that came since the last call, once. */
    fun takeTool(): List<ToolInputDelta> = tools.toList().also { tools.clear() }

    private fun plain(p: JsonObject) = p.keys == setOf("text")

    /** Takes one chunk's [data]; true when it carried content (text, a function call). */
    fun accept(data: String): Boolean {
        val el = runCatching { tutorJson.parseToJsonElement(data) }.getOrNull() ?: return false
        val o = (el as? JsonObject) ?: ((el as? JsonArray)?.firstOrNull() as? JsonObject) ?: return false
        (o["error"] as? JsonObject)?.let {
            error = GeminiErrors.ofBody((it["code"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 500, o, null, "stream")
            return false
        }
        responseId = o.str("responseId") ?: responseId
        modelVersion = o.str("modelVersion") ?: modelVersion
        (o["usageMetadata"] as? JsonObject)?.let { usage = it }
        (o["promptFeedback"] as? JsonObject)?.str("blockReason")?.let {
            blockReason = it
            finished = true
        }
        val candidate = (o["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject
        var content = false
        ((candidate?.get("content") as? JsonObject)?.get("parts") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.forEach { p ->
            val last = parts.lastOrNull()
            if (plain(p) && last != null && plain(last)) parts[parts.size - 1] = buildJsonObject { put("text", last.str("text").orEmpty() + p.str("text").orEmpty()) }
            else parts += p
            if (!p.str("text").isNullOrEmpty() || p["functionCall"] != null) content = true
            (p["functionCall"] as? JsonObject)?.let { fc ->
                tools += ToolInputDelta(fc.str("id").orEmpty(), fc.str("name").orEmpty(), (fc["args"] ?: JsonObject(emptyMap())).toString())
            }
        }
        candidate?.str("finishReason")?.let {
            finishReason = it
            finishMessage = candidate.str("finishMessage")
            finished = true
        }
        return content
    }

    /** The response as a [Step], after [first] ms to its first token and [total] in all. */
    fun step(first: Long?, total: Long): Step {
        val fcs = parts.mapNotNull { it["functionCall"] as? JsonObject }
        // a call without the API's id gets ours, kept beside the parts (never sent)
        val ours = if (fcs.any { it.str("id") == null }) fcs.indices.map { i -> fcs[i].str("id") ?: "${GeminiFormat.OWN_ID}${responseId ?: "r"}_$i" } else null
        val item = if (parts.isEmpty()) null else buildJsonObject {
            put("role", "model")
            put("parts", JsonArray(parts.toList()))
            ours?.let { put(GeminiFormat.IDS, JsonArray(it.map(::JsonPrimitive))) }
        }
        val calls = item?.let(GeminiFormat::calls).orEmpty()
        val texts = parts.filter { it["thought"]?.toString() != "true" }.mapNotNull { it.str("text") }.filter { it.isNotEmpty() }
        val text = texts.joinToString("")
        val refusal = blockReason ?: finishReason?.takeIf { it in REFUSED }
        val stop = when {
            refusal != null -> Stop.REFUSAL
            finishReason == "MAX_TOKENS" -> Stop.MAX_TOKENS
            finishReason == "MISSING_THOUGHT_SIGNATURE" -> throw ApiError.BadRequest(400, "Gemini: a thought signature was missing (MISSING_THOUGHT_SIGNATURE)")
            finishReason in MALFORMED -> throw ApiError.Overloaded(500, "Gemini: ${finishReason!!.lowercase()}${finishMessage?.let { ": ${it.take(200)}" } ?: ""}")
            calls.isNotEmpty() -> Stop.TOOL_USE
            finishReason == null || finishReason == "STOP" -> Stop.END_TURN
            else -> finishReason!!.lowercase()
        }
        val details = refusal?.let {
            buildJsonObject {
                put("type", "refusal")
                put("category", it.lowercase())
                finishMessage?.let { m -> put("message", m.take(500)) }
            }
        }
        val keptParts = parts.filter { plain(it) && !it.str("text").isNullOrBlank() }
        return Step(
            items = listOfNotNull(item),
            toolCalls = calls,
            text = text,
            stopReason = stop,
            stopDetails = details,
            usage = GeminiUsage.of(usage),
            model = modelVersion ?: model,
            id = responseId,
            firstTokenMs = first,
            totalMs = total,
            visibleChars = text.length + calls.sumOf { it.name.length + (it.badJson?.length ?: it.input.toString().length) },
            kept = if (keptParts.isEmpty()) emptyList() else listOf(buildJsonObject { put("role", "model"); put("parts", JsonArray(keptParts)) }),
            rawUsage = RawUsage(delta = usage),
        )
    }

    private companion object {
        /** The finish reasons that are the model or a filter declining: a refusal, never retried. */
        val REFUSED = setOf("SAFETY", "RECITATION", "LANGUAGE", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY", "IMAGE_PROHIBITED_CONTENT", "IMAGE_RECITATION")
        /** A call or an answer the model botched: made again (the loop's backoff). */
        val MALFORMED = setOf("MALFORMED_FUNCTION_CALL", "UNEXPECTED_TOOL_CALL", "TOO_MANY_TOOL_CALLS", "MALFORMED_RESPONSE")
    }
}

/** The Gemini API's `usageMetadata` as [Usage]. */
object GeminiUsage {
    /**
     * [u] (the API reference's UsageMetadata): `promptTokenCount` is the whole prompt, the cached part included
     * ("this includes the number of tokens in the cached content"), so [Usage.input] is the rest and [Usage.cacheRead]
     * `cachedContentTokenCount`; `toolUsePromptTokenCount` (built-in tools' prompts: none here) counts as input;
     * `thoughtsTokenCount` is apart from `candidatesTokenCount` ("total … prompt + tool-use prompt + thoughts + response
     * candidates"), and billed as output ("Output price (including thinking tokens)"): [Usage.output] is both,
     * [Usage.reasoning] the thoughts. Implicit caching writes nothing billed: no cache writes.
     */
    fun of(u: JsonObject?): Usage {
        if (u == null) return Usage()
        fun n(k: String) = (u[k] as? JsonPrimitive)?.longOrNull ?: 0
        val cached = n("cachedContentTokenCount")
        val thoughts = n("thoughtsTokenCount")
        return Usage(
            input = (n("promptTokenCount") + n("toolUsePromptTokenCount") - cached).coerceAtLeast(0),
            cacheRead = cached,
            output = n("candidatesTokenCount") + thoughts,
            reasoning = thoughts,
        )
    }
}

/**
 * The Gemini API's errors as [ApiError] (the API errors guide, ai.google.dev/gemini-api/docs/generate-content/api-errors;
 * the troubleshooting guide: "Only retry on transient errors (like 429, 408, or 5xx). Do not retry on client errors
 * (like 400, 402, or 403)"). The body is `{"error": {code, message, status, details[]}}`:
 * - 400 with `API_KEY_INVALID` ("API key not valid"), 401, 403 (PERMISSION_DENIED, a key reported as leaked) → [ApiError.Auth];
 * - 400 FAILED_PRECONDITION ("Gemini API free tier is not available in your country. Please enable billing") →
 *   [ApiError.LowCredit] with `limit`;
 * - 402 ("Your Prepay credit balance is depleted") → [ApiError.LowCredit];
 * - 429 RESOURCE_EXHAUSTED: a day's quota (a `PerDay` quota, a retry delay of an hour or more) or a quota of 0 (a model
 *   without free quota on this key: Argon on the free tier) → [ApiError.LowCredit] with `limit`; else
 *   [ApiError.RateLimited] after its RetryInfo's `retryDelay` or `retry-after`. The details aren't documented (seen in
 *   the wild: QuotaFailure with the quota's metric, RetryInfo; some 429s have none);
 * - 408, 499, 500, 503 (UNAVAILABLE: "temporarily overloaded"), 504 → [ApiError.Overloaded];
 * - the rest (400 INVALID_ARGUMENT, 404) → [ApiError.BadRequest].
 */
object GeminiErrors {
    private data class Said(val status: String?, val message: String?, val reasons: List<String>, val quotas: List<String>, val retryDelayMs: Long?)

    private fun said(o: JsonObject?): Said {
        val e = (o?.get("error") as? JsonObject) ?: o
        val details = (e?.get("details") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val reasons = details.mapNotNull { it.str("reason") }
        val quotas = details.flatMap { d -> (d["violations"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.flatMap { v -> listOfNotNull(v.str("quotaId"), v.str("quotaMetric"), v.str("subject")) } }
        val delay = details.firstNotNullOfOrNull { it.str("retryDelay") }?.let(::duration)
        return Said(e?.str("status"), e?.str("message"), reasons, quotas, delay)
    }

    private fun parse(body: String): JsonObject? = runCatching {
        when (val el = tutorJson.parseToJsonElement(body)) {
            is JsonObject -> el
            is JsonArray -> el.firstOrNull() as? JsonObject
            else -> null
        }
    }.getOrNull()

    /** A protobuf Duration as Google writes it ("37s", "1.5s", "10h17m5.7s"), in ms. */
    fun duration(s: String): Long? {
        val m = Regex("""^(?:(\d+)h)?(?:(\d+)m(?!s))?(?:(\d+(?:\.\d+)?)s)?$""").matchEntire(s.trim()) ?: return null
        if (m.groupValues.drop(1).all { it.isEmpty() }) return null
        val h = m.groupValues[1].toLongOrNull() ?: 0
        val min = m.groupValues[2].toLongOrNull() ?: 0
        val sec = m.groupValues[3].toDoubleOrNull() ?: 0.0
        return h * 3_600_000 + min * 60_000 + (sec * 1000).toLong()
    }

    /** Whether a 429's [body] names the free tier's quota (`generate_content_free_tier_requests`, a `FreeTier` quota id). */
    fun freeTier(body: String): Boolean {
        val s = said(parse(body))
        return (s.quotas + listOfNotNull(s.message)).any { it.contains("free_tier", ignoreCase = true) || it.contains("FreeTier") }
    }

    /** An HTTP answer [code] with [body] and its `retry-after` header. */
    fun of(code: Int, body: String, retryAfter: String? = null): ApiError = ofBody(code, parse(body), retryAfter, "HTTP $code", body)

    fun ofBody(code: Int, o: JsonObject?, retryAfter: String?, where: String, raw: String = ""): ApiError {
        val s = said(o)
        val text = "$where${s.status?.let { " $it" } ?: ""}${s.reasons.firstOrNull()?.let { " ($it)" } ?: ""}: ${(s.message ?: raw).take(300)}"
        val msg = s.message.orEmpty()
        return when {
            code == 400 && ("API_KEY_INVALID" in s.reasons || msg.contains("API key not valid", ignoreCase = true)) -> ApiError.Auth(text)
            code == 401 || code == 403 -> ApiError.Auth(text)
            code == 400 && s.status == "FAILED_PRECONDITION" -> ApiError.LowCredit("$text: enable billing in Google AI Studio", limit = true)
            code == 402 -> ApiError.LowCredit(text)
            code == 429 && daily(s, msg) -> ApiError.LowCredit("$text: the day's quota is used (it resets at midnight Pacific time)", limit = true)
            code == 429 && Regex("""\blimit:\s*0\b""").containsMatchIn(msg) -> ApiError.LowCredit("$text: this model has no free quota on this key (Gemini 4 Argon has no free tier): enable billing in Google AI Studio", limit = true)
            code == 429 -> ApiError.RateLimited(s.retryDelayMs ?: ApiError.retryAfterMs(retryAfter), text)
            code == 408 || code == 499 || code >= 500 -> ApiError.Overloaded(code, text)
            else -> ApiError.BadRequest(code, text)
        }
    }

    private fun daily(s: Said, msg: String): Boolean =
        s.quotas.any { it.contains("PerDay", ignoreCase = true) || it.contains("per_day", ignoreCase = true) } ||
            msg.contains("per day", ignoreCase = true) || (s.retryDelayMs ?: 0) >= 3_600_000
}
