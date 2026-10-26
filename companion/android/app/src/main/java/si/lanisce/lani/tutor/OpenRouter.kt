package si.lanisce.lani.tutor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
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
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * OpenRouter for the tutor, "one sign-in, every model" (docs/plans/05-built/providers.md, "OpenRouter"): the learner
 * signs in with OpenRouter's PKCE flow and gets a key of their own, billed to their OpenRouter credits; the calls are
 * OpenRouter's OpenAI-compatible chat completions with tools, streamed. Plain OkHttp and kotlinx.serialization.
 */

/** Where the OpenRouter key may go: openrouter.ai only. */
object OpenRouterGuard {
    const val API = "https://openrouter.ai"

    fun mayCarryKey(url: HttpUrl): Boolean = url.scheme == "https" && url.host == "openrouter.ai" && url.port == 443
}

/** The OpenRouter pages the app links to and the app's attribution (openrouter.ai/docs/app-attribution). */
object OpenRouterLinks {
    const val AUTH = "https://openrouter.ai/auth"
    const val KEYS = "https://openrouter.ai/settings/keys"
    const val CREDITS = "https://openrouter.ai/settings/credits"
    /** "HTTP-Referer … required for app attribution": the app's page. */
    const val REFERER = "https://github.com/fentas/lani"
    const val TITLE = "Lani"
    const val CALLBACK_PATH = "/auth/openrouter"
}

/** Why the OpenRouter sign-in failed: its [code] (declined, invalid_code, offline, …) and what to tell the learner. */
class OpenRouterError(val code: String, message: String) : Exception(message)

/**
 * "Continue with OpenRouter" (openrouter.ai/docs/guides/overview/auth/oauth, checked 2026-10-19): OAuth PKCE for a key.
 * - The browser opens `https://openrouter.ai/auth?callback_url=…&code_challenge=…&code_challenge_method=S256` with a
 *   `state` ("Returned unchanged as a state query parameter on the callback URL") and `key_label=Lani`; it comes back
 *   to the shared loopback ([LoopbackServer], `http://127.0.0.1:<port>/auth/openrouter`: the API reference takes
 *   "localhost/127.0.0.1 URLs on any port") with `?code=`.
 * - The code ("single-use and expires after 10 minutes") goes to `POST https://openrouter.ai/api/v1/auth/keys` with the
 *   verifier: `{"key": "sk-or-v1-…", "user_id": …}`. The exchange is tried again for about a minute while the network
 *   isn't there ([WhileOffline]: Lani is in the background while the browser comes back).
 * - The key is the learner's own, billed to their credits, kept encrypted in [vault]. OpenRouter has no revocation
 *   for it from the app: "Sign out" forgets it here, and the learner deletes it at openrouter.ai/settings/keys.
 */
class OpenRouterAuth(
    private val vault: KeyVault,
    private val http: OkHttpClient = defaultClient,
    private val random: SecureRandom = SecureRandom(),
    private val label: String = "Lani",
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    private fun randomValue(): String = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    /** The key kept, or null. */
    suspend fun key(): String? = vault.load()

    suspend fun has(): Boolean = vault.has()

    /** Starts a sign-in: the loopback on [port] (0: a free one), the authorize URL. Open [SignIn.url], then [SignIn.await]. */
    suspend fun begin(port: Int = 0): SignIn {
        val state = randomValue()
        val verifier = randomValue()
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val code = CompletableDeferred<String>()
        val server = withContext(Dispatchers.IO) {
            LoopbackServer(port, OpenRouterLinks.CALLBACK_PATH, "openrouter-sign-in") { hit ->
                val states = hit.all("state")
                val codes = hit.all("code")
                when {
                    // a stray request or another attempt's: never ends this one
                    states.size > 1 || (states.size == 1 && !LoopbackPage.same(states[0], state)) ->
                        LoopbackServer.Answer(400, LoopbackPage.page("Prijava ni veljavna · Invalid sign-in", "Vrni se v zavihek, ki je začel prijavo. · Return to the tab that started the sign-in."), done = false)
                    codes.size != 1 || states.isEmpty() -> LoopbackServer.Answer(200, LoopbackPage.page("Prijava preklicana · Sign-in declined", LoopbackPage.BACK_LINK), done = true) {
                        code.completeExceptionally(OpenRouterError("declined", "the OpenRouter sign-in was declined or didn't finish: try again"))
                    }
                    else -> LoopbackServer.Answer(200, LoopbackPage.signedIn("Lani zdaj prevzame ključ OpenRouter. · Lani is getting the OpenRouter key now."), done = true) {
                        code.complete(codes[0])
                    }
                }
            }
        }
        val url = OpenRouterLinks.AUTH.toHttpUrl().newBuilder()
            .addQueryParameter("callback_url", server.uri)
            .addQueryParameter("code_challenge", challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("state", state)
            .addQueryParameter("key_label", label)
            .build().toString()
        return SignIn(url, server, code) { c -> exchange(c, verifier) }
    }

    /** A sign-in in progress: [url] to open in the browser; [await] the key it got (kept; throws [OpenRouterError]). */
    class SignIn internal constructor(val url: String, private val server: LoopbackServer, private val code: CompletableDeferred<String>, private val finish: suspend (String) -> String) {
        val redirectUri: String get() = server.uri

        suspend fun await(timeoutMs: Long = 10 * 60_000L): String = try {
            val c = try {
                withTimeout(timeoutMs) { code.await() }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                throw OpenRouterError("timeout", "the sign-in took too long (OpenRouter's code lasts 10 minutes): try again")
            }
            finish(c)
        } finally {
            server.close()
        }

        fun cancel() {
            server.close()
            if (!code.isCompleted) code.completeExceptionally(OpenRouterError("cancelled", "the sign-in was cancelled"))
        }
    }

    private suspend fun exchange(code: String, verifier: String): String {
        val body = buildJsonObject {
            put("code", code)
            put("code_verifier", verifier)
            put("code_challenge_method", "S256")
        }
        val (status, text) = WhileOffline.run(OFFLINE_WAITS, pause, { e ->
            OpenRouterError("offline", "Lani couldn't reach OpenRouter to finish the sign-in (${e.message}). Is the phone online? Battery or data saver can hold an app's network while it's in the background: try again with Lani open")
        }) { post(body) }
        val o = runCatching { tutorJson.parseToJsonElement(text) as JsonObject }.getOrNull()
        val key = o?.str("key")
        if (status !in 200..299 || key.isNullOrBlank()) {
            val said = (o?.get("error") as? JsonObject)?.str("message") ?: o?.str("message") ?: "HTTP $status"
            throw when (status) {
                403 -> OpenRouterError("invalid_code", "OpenRouter didn't take the sign-in's code ($said): sign in again")
                in 500..599 -> OpenRouterError("unavailable", "OpenRouter couldn't finish the sign-in (HTTP $status): try again")
                else -> OpenRouterError("exchange_failed", "the OpenRouter sign-in failed ($said)")
            }
        }
        vault.save(key)
        return key
    }

    private suspend fun post(body: JsonObject): Pair<Int, String> = withContext(Dispatchers.IO) {
        val url = "${OpenRouterGuard.API}/api/v1/auth/keys".toHttpUrl()
        check(OpenRouterGuard.mayCarryKey(url))
        try {
            http.newCall(Request.Builder().url(url).header("accept", "application/json").post(body.toString().toRequestBody(JSON)).build()).execute()
                .use { it.code to it.body?.string().orEmpty() }
        } catch (e: IOException) {
            throw ApiError.Network(e.message ?: "the sign-in couldn't reach OpenRouter")
        }
    }

    /** Forgets the key here; it stays on OpenRouter until the learner deletes it ([OpenRouterLinks.KEYS]). */
    suspend fun signOut(): String {
        vault.remove()
        return "the key is gone from the phone; delete it at openrouter.ai/settings/keys too"
    }

    companion object {
        val OFFLINE_WAITS = WhileOffline.WAITS
        private val JSON = "application/json".toMediaType()
        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).followRedirects(false).build()
        }
    }
}

/** The OpenRouter sign-in's key, kept in its own [KeyVault] (Keystore key and DataStore entry of its own). */
class OpenRouterCredentials(val auth: OpenRouterAuth) {
    suspend fun key(): String? = auth.key()
    suspend fun has(): Boolean = auth.has()

    companion object {
        fun android(context: android.content.Context): OpenRouterCredentials =
            OpenRouterCredentials(OpenRouterAuth(KeyVault(KeystoreCipher("lani-tutor-openrouter-key"), DataStoreSecretStore(context, "openrouter_key_sealed"))))
    }
}

/**
 * What OpenRouter's model list says of each model the tutor may pick (GET /api/v1/models: `reasoning.supported_efforts`
 * and `default_effort`), so [ModelCatalog.efforts] knows a routed model's levels. Filled when the list is read.
 */
object RoutedModels {
    private val known = ConcurrentHashMap<String, ModelInfo>()

    fun learn(models: List<ModelInfo>) = models.filter { ProviderId.of(it.id) == ProviderId.OPENROUTER }.forEach { known[it.id] = it }

    fun info(model: String): ModelInfo? = known[model]

    /** The efforts [model] takes, as the list said, in order; null: not read. */
    fun efforts(model: String): List<String>? = known[model]?.efforts?.let { e -> Effort.ALL.filter { it in e } }

    fun defaultEffort(model: String): String? = known[model]?.defaultEffort
}

/** An OpenRouter key's state (GET /api/v1/key): its label, its credit limit and what's left of it, what it used. */
data class OpenRouterKey(val label: String?, val limit: Double?, val remaining: Double?, val usage: Double?, val usageMonthly: Double?, val neverPaid: Boolean?)

/**
 * Chat completions' threads (OpenRouter's OpenAI-compatible API): `{"role": "user", "content": [text, image_url]}`, the
 * model's `{"role": "assistant", "content", "tool_calls", "reasoning_details"}` passed back unchanged ("the entire
 * sequence of consecutive reasoning blocks must match the outputs generated by the model during the original request":
 * the reasoning guide), a tool's result `{"role": "tool", "tool_call_id", "content"}` (no error flag: a failed one
 * begins "Error: "). The system prompt isn't in the thread: the request puts it first. No effort changes within a
 * thread ("Request-level effort applies to the whole conversation, so raising it for one hard turn … invalidates the
 * prompt cache").
 */
object ChatFormat : ThreadFormat {
    /** The part of a neutral block: text as `text`, a base64 image as an `image_url` with a data URL. */
    fun part(b: JsonObject): JsonObject = when (b.str("type")) {
        "text" -> buildJsonObject {
            put("type", "text")
            put("text", b.str("text").orEmpty())
        }
        "image" -> {
            val source = b["source"] as? JsonObject
            buildJsonObject {
                put("type", "image_url")
                putJsonObject("image_url") { put("url", "data:${source?.str("media_type") ?: "image/jpeg"};base64,${source?.str("data").orEmpty()}") }
            }
        }
        "image_url" -> b
        else -> buildJsonObject {
            put("type", "text")
            put("text", b.toString())
        }
    }

    private fun userOf(parts: List<JsonObject>) = buildJsonObject {
        put("role", "user")
        put("content", JsonArray(parts))
    }

    override fun user(blocks: List<JsonObject>): JsonObject = userOf(blocks.map(::part))

    override fun append(messages: List<JsonObject>, blocks: List<JsonObject>, top: String?, want: String?): List<JsonObject> {
        val last = messages.lastOrNull()
        return if (last != null && last.role() == "user") messages.dropLast(1) + userOf(last.blocks().map(::part) + blocks.map(::part))
        else messages + user(blocks)
    }

    override fun pending(messages: List<JsonObject>): List<ToolUse>? {
        val last = messages.lastOrNull() ?: return null
        if (last.role() != "assistant") return null
        return (last["tool_calls"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map(::toolUse)
    }

    /** A `tool_calls` entry as a tool call: its id, its function's name, its `arguments` (a JSON string) parsed. */
    fun toolUse(tc: JsonObject): ToolUse {
        val f = tc["function"] as? JsonObject
        val raw = f?.str("arguments").orEmpty()
        val input = if (raw.isBlank()) JsonObject(emptyMap()) else runCatching { tutorJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        return ToolUse(tc.str("id").orEmpty(), f?.str("name").orEmpty(), input ?: JsonObject(emptyMap()), if (input == null) raw else null)
    }

    override fun results(calls: List<ToolUse>, outcomes: List<ToolOutcome>): List<JsonObject> = calls.zip(outcomes).map { (u, o) ->
        buildJsonObject {
            put("role", "tool")
            put("tool_call_id", u.id)
            put("content", if (o.isError) "Error: ${o.content}" else o.content)
        }
    }
}

/**
 * OpenRouter's chat completions as a [TutorProvider] over [transport], paid by the learner's OpenRouter credits
 * ([Billing.OPENROUTER]; each call priced by the cost OpenRouter reports, `usage.cost`).
 *
 * The request (openrouter.ai/docs: the API reference, tool calling, reasoning, prompt caching):
 * - `messages`: the core prompt as the system message, then the thread; `tools` the bridge's JSON schemas as functions
 *   in the prefix's order; `tool_choice: auto` (Claude 5.5 refuses a forced tool there too); `stream: true`; `max_tokens`;
 * - `reasoning.effort` where the model takes one ([ModelCatalog.routedEfforts]: OpenRouter maps it onto Gemini's thinking
 *   levels, Anthropic's thinking budgets, OpenAI's efforts);
 * - Claude through OpenRouter: the system message's text with `cache_control` (`ttl: 1h`, OpenRouter passes it on for
 *   Anthropic's models) and a top-level `cache_control` for the moving breakpoint, as the Messages API's request has
 *   them; the others cache by themselves (OpenAI's, Gemini's implicit cache: no `cache_control` sent: on Argon an
 *   explicit cache is an error at Google's);
 * - `session_id` by the prefix, so OpenRouter routes the calls to the same provider (its cache).
 * Usage needs nothing now ("The usage: { include: true } … parameters are deprecated and have no effect").
 */
class OpenRouterProvider(private val transport: OpenRouterTransport) : TutorProvider {
    override val id = ProviderId.OPENROUTER
    override val format: ThreadFormat = ChatFormat
    override val billing = Billing.OPENROUTER

    override suspend fun models(): List<ModelInfo> = transport.models()

    override fun prefixHash(prefix: Prefix): String = fingerprint("openrouter\u0000" + tools(prefix) + "\u0000" + prefix.system)

    override fun request(prefix: Prefix, model: String, task: Task, messages: List<JsonObject>, maxTokens: Int, effort: String?): JsonObject = buildJsonObject {
        val claude = model.startsWith("anthropic/")
        put("model", model)
        put(
            "messages",
            JsonArray(
                listOf(
                    buildJsonObject {
                        put("role", "system")
                        if (claude) put(
                            "content",
                            JsonArray(
                                listOf(
                                    buildJsonObject {
                                        put("type", "text")
                                        put("text", prefix.system)
                                        putJsonObject("cache_control") {
                                            put("type", "ephemeral")
                                            put("ttl", "1h")
                                        }
                                    },
                                ),
                            ),
                        ) else put("content", prefix.system)
                    },
                ) + messages,
            ),
        )
        put("tools", tools(prefix))
        put("tool_choice", "auto")
        put("stream", true)
        put("max_tokens", maxTokens)
        val level = effort?.let { ModelCatalog.fit(it, model) }
        if (level != null) putJsonObject("reasoning") { put("effort", level) }
        if (claude) putJsonObject("cache_control") { put("type", "ephemeral") }
        put("session_id", "lani-tutor-" + prefixHash(prefix))
    }

    override fun toolsOff(body: JsonObject): JsonObject = JsonObject(body + ("tool_choice" to JsonPrimitive("none")))

    override suspend fun call(body: JsonObject, onDelta: () -> Unit): Step = transport.stream(body, onDelta)

    override suspend fun callTools(body: JsonObject, onDelta: () -> Unit, onTool: (ToolInputDelta) -> Unit): Step = transport.stream(body, onDelta, onTool)

    companion object {
        private fun fingerprint(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

        /** The prefix's tools (the Messages API's definitions) as chat completions' function tools, in the same order. */
        fun tools(prefix: Prefix): JsonArray = JsonArray(
            prefix.tools.mapNotNull { it as? JsonObject }.map { def ->
                val d = def.str("type")?.let { prefix.alternates[def.str("name")] } ?: def
                buildJsonObject {
                    put("type", "function")
                    putJsonObject("function") {
                        put("name", d.str("name").orEmpty())
                        put("description", d.str("description").orEmpty())
                        put("parameters", d["input_schema"] ?: JsonObject(mapOf("type" to JsonPrimitive("object"))))
                    }
                }
            },
        )
    }
}

/**
 * OpenRouter's client: GET /api/v1/models (public: no key sent), GET /api/v1/key, POST /api/v1/chat/completions
 * (streamed), with the key ([token]) to openrouter.ai only ([OpenRouterGuard]) and the app's attribution headers. Throws
 * [ApiError] ([OpenRouterErrors]).
 */
class OpenRouterTransport(
    private val token: suspend () -> String?,
    private val client: OkHttpClient = defaultClient,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private fun url(path: String): HttpUrl = (OpenRouterGuard.API + path).toHttpUrl().also { check(OpenRouterGuard.mayCarryKey(it)) { "the OpenRouter key goes only to openrouter.ai" } }

    private suspend fun authed(path: String): Request.Builder {
        val k = token()?.takeIf { it.isNotBlank() } ?: throw ApiError.Auth("no OpenRouter key")
        return Request.Builder().url(url(path)).header("Authorization", "Bearer $k")
            .header("HTTP-Referer", OpenRouterLinks.REFERER).header("X-OpenRouter-Title", OpenRouterLinks.TITLE)
    }

    /**
     * The models that take tools (GET /api/v1/models?supported_parameters=tools): id, name, context, prices (strings in
     * dollars a token: kept per million), the reasoning efforts, image input. The list is OpenRouter's, public.
     */
    suspend fun models(): List<ModelInfo> {
        val req = Request.Builder().url(url("/api/v1/models?supported_parameters=tools")).get().build()
        val (code, body, retryAfter) = execute(req) { r -> Triple(r.code, r.body?.string().orEmpty(), r.header("retry-after")) }
        if (code !in 200..299) throw OpenRouterErrors.of(code, body, retryAfter)
        return parseModels(body) ?: throw ApiError.Overloaded(code, "GET /api/v1/models: an answer without data")
    }

    /** The key's state (GET /api/v1/key: the credential's check, not a model call). */
    suspend fun key(): OpenRouterKey {
        val req = authed("/api/v1/key").get().build()
        val (code, body, retryAfter) = execute(req) { r -> Triple(r.code, r.body?.string().orEmpty(), r.header("retry-after")) }
        if (code !in 200..299) throw OpenRouterErrors.of(code, body, retryAfter)
        val d = runCatching { (tutorJson.parseToJsonElement(body) as JsonObject)["data"] as JsonObject }.getOrNull()
            ?: throw ApiError.Overloaded(code, "GET /api/v1/key: an answer without data")
        fun n(k: String) = (d[k] as? JsonPrimitive)?.doubleOrNull
        return OpenRouterKey(d.str("label"), n("limit"), n("limit_remaining"), n("usage"), n("usage_monthly"), (d["is_free_tier"] as? JsonPrimitive)?.booleanOrNull)
    }

    /** POST /api/v1/chat/completions with [body] (`stream: true`), read as server-sent events to `data: [DONE]`. */
    suspend fun stream(body: JsonObject, onDelta: () -> Unit = {}, onTool: (ToolInputDelta) -> Unit = {}): Step {
        val sent = JsonObject(body + ("stream" to JsonPrimitive(true)))
        val req = authed("/api/v1/chat/completions").header("accept", "text/event-stream").post(sent.toString().toRequestBody(JSON)).build()
        val started = clock()
        val acc = ChatAccumulator()
        var first: Long? = null
        execute(req) { r ->
            if (!r.isSuccessful) throw OpenRouterErrors.of(r.code, r.body?.string().orEmpty(), r.header("retry-after"))
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
                    // ": OPENROUTER PROCESSING" keeps the connection while a model thinks
                    line.startsWith(":") -> Unit
                    line.startsWith("data:") -> {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.removePrefix("data:").removePrefix(" "))
                    }
                    else -> Unit
                }
                if (acc.error != null || acc.done) break
            }
            dispatch()
        }
        acc.error?.let { throw it }
        if (!acc.finished) throw ApiError.Network("the stream ended before its finish_reason")
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

        /** As Gemini's: a routed model may reason long (Argon: at least 20 minutes a call). */
        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.MINUTES).callTimeout(25, TimeUnit.MINUTES).followRedirects(false).build()
        }

        val laneClient: OkHttpClient by lazy {
            defaultClient.newBuilder().dispatcher(Dispatcher().apply { maxRequests = 32; maxRequestsPerHost = 16 }).build()
        }

        /** The models of a GET /api/v1/models answer ([body]), or null when it has no `data`. */
        fun parseModels(body: String): List<ModelInfo>? {
            val data = runCatching { (tutorJson.parseToJsonElement(body) as JsonObject)["data"] as? JsonArray }.getOrNull() ?: return null
            return data.mapNotNull { e ->
                val m = e as? JsonObject ?: return@mapNotNull null
                val id = m.str("id") ?: return@mapNotNull null
                val pricing = m["pricing"] as? JsonObject
                fun perMillion(k: String) = pricing?.str(k)?.toDoubleOrNull()?.let { it * 1_000_000 }
                val reasoning = m["reasoning"] as? JsonObject
                val params = (m["supported_parameters"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
                val efforts = (reasoning?.get("supported_efforts") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
                    ?: if ("reasoning" in params) null else emptyList()
                val modalities = ((m["architecture"] as? JsonObject)?.get("input_modalities") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
                ModelInfo(
                    id = id,
                    displayName = m.str("name"),
                    createdAt = (m["created"] as? JsonPrimitive)?.longOrNull?.let { Instant.ofEpochSecond(it).toString() },
                    maxInputTokens = (m["context_length"] as? JsonPrimitive)?.longOrNull,
                    maxTokens = ((m["top_provider"] as? JsonObject)?.get("max_completion_tokens") as? JsonPrimitive)?.longOrNull,
                    efforts = efforts,
                    defaultEffort = reasoning?.str("default_effort"),
                    inputPrice = perMillion("prompt"),
                    outputPrice = perMillion("completion"),
                    images = modalities.takeIf { it.isNotEmpty() }?.contains("image"),
                )
            }
        }
    }
}

/**
 * Puts a streamed chat completion back together from its chunks: `choices[0].delta` (`content`, `reasoning`,
 * `reasoning_details` by their `index`, `tool_calls` by their `index`: id, name and the arguments' pieces, `refusal`),
 * `finish_reason` (it comes twice: the usage chunk "repeats the finish_reason"), `usage` on the last chunk, a chunk's
 * `error` (with `finish_reason: "error"`; "The error can be the first and only event in the stream"), `data: [DONE]`.
 * The reasoning details' pieces are joined by index (text, summary, data, the last signature): not documented for a
 * stream, so a model whose details come whole keeps them whole.
 */
class ChatAccumulator {
    private val text = StringBuilder()
    private val refusal = StringBuilder()
    private val calls = sortedMapOf<Int, Triple<StringBuilder, StringBuilder, StringBuilder>>() // id, name, arguments
    private val details = sortedMapOf<Int, MutableMap<String, JsonElement>>()
    private var finish: String? = null
    private var usage: JsonObject? = null
    private var id: String? = null
    private var model: String? = null
    private var tool: ToolInputDelta? = null

    var done = false
        private set
    var error: ApiError? = null
        private set

    /** The stream said how the answer ended (its finish_reason), or [DONE] came. */
    val finished: Boolean get() = finish != null

    fun takeTool(): ToolInputDelta? = tool.also { tool = null }

    fun accept(data: String): Boolean {
        if (data.trim() == "[DONE]") {
            done = true
            return false
        }
        val o = runCatching { tutorJson.parseToJsonElement(data) as? JsonObject }.getOrNull() ?: return false
        id = o.str("id") ?: id
        model = o.str("model") ?: model
        (o["usage"] as? JsonObject)?.let { usage = it }
        (o["error"] as? JsonObject)?.let {
            error = OpenRouterErrors.ofStream(it)
            return false
        }
        val choice = (o["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return false
        var content = false
        (choice["delta"] as? JsonObject)?.let { d ->
            d.str("content")?.takeIf { it.isNotEmpty() }?.let {
                text.append(it)
                content = true
            }
            d.str("refusal")?.takeIf { it.isNotEmpty() }?.let {
                refusal.append(it)
                content = true
            }
            if (!d.str("reasoning").isNullOrEmpty()) content = true
            (d["reasoning_details"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.forEachIndexed { i, rd ->
                val at = (rd["index"] as? JsonPrimitive)?.intOrNull ?: i
                val cur = details.getOrPut(at) { linkedMapOf() }
                for ((k, v) in rd) {
                    val s = (v as? JsonPrimitive)?.takeIf { it.isString }?.content
                    cur[k] = when {
                        k in JOINED && s != null -> JsonPrimitive(((cur[k] as? JsonPrimitive)?.content.orEmpty()) + s)
                        v is JsonNull -> cur[k] ?: v
                        else -> v
                    }
                }
                content = true
            }
            (d["tool_calls"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.forEachIndexed { i, tc ->
                val at = (tc["index"] as? JsonPrimitive)?.intOrNull ?: i
                val c = calls.getOrPut(at) { Triple(StringBuilder(), StringBuilder(), StringBuilder()) }
                tc.str("id")?.let { if (c.first.isEmpty()) c.first.append(it) }
                val f = tc["function"] as? JsonObject
                f?.str("name")?.let { if (c.second.isEmpty()) c.second.append(it) }
                f?.str("arguments")?.let { c.third.append(it) }
                tool = ToolInputDelta(c.first.toString(), c.second.toString(), c.third.toString())
                content = true
            }
        }
        choice.str("finish_reason")?.let { if (finish == null || finish == "error") finish = it }
        if (choice.str("finish_reason") == "error" && error == null) error = ApiError.Overloaded(500, "stream: the provider failed (finish_reason error)")
        return content
    }

    fun step(first: Long?, total: Long): Step {
        val toolCalls = calls.values.map { (cid, name, args) ->
            buildJsonObject {
                put("id", cid.toString())
                put("type", "function")
                putJsonObject("function") {
                    put("name", name.toString())
                    put("arguments", args.toString())
                }
            }
        }
        val reasoning = details.values.map { JsonObject(it) }
        val said = text.toString()
        val message = if (said.isEmpty() && toolCalls.isEmpty() && reasoning.isEmpty()) null else buildJsonObject {
            put("role", "assistant")
            put("content", if (said.isEmpty()) JsonNull else JsonPrimitive(said))
            if (toolCalls.isNotEmpty()) put("tool_calls", JsonArray(toolCalls))
            if (reasoning.isNotEmpty()) put("reasoning_details", JsonArray(reasoning))
        }
        val uses = toolCalls.map(ChatFormat::toolUse)
        val stop = when {
            refusal.isNotEmpty() || finish == "content_filter" -> Stop.REFUSAL
            finish == "length" -> Stop.MAX_TOKENS
            uses.isNotEmpty() -> Stop.TOOL_USE
            finish == "stop" || finish == null -> Stop.END_TURN
            else -> finish
        }
        val stopDetails = when {
            refusal.isNotEmpty() -> buildJsonObject { put("type", "refusal"); put("refusal", refusal.toString().take(500)) }
            finish == "content_filter" -> buildJsonObject { put("type", "refusal"); put("category", "content_filter") }
            else -> null
        }
        return Step(
            items = listOfNotNull(message),
            toolCalls = uses,
            text = said,
            stopReason = stop,
            stopDetails = stopDetails,
            usage = OpenRouterUsage.of(usage),
            model = model,
            id = id,
            firstTokenMs = first,
            totalMs = total,
            visibleChars = said.length + uses.sumOf { it.name.length + (it.badJson?.length ?: it.input.toString().length) },
            kept = if (said.isBlank()) emptyList() else listOf(buildJsonObject { put("role", "assistant"); put("content", said) }),
            rawUsage = RawUsage(delta = usage),
            cost = OpenRouterUsage.cost(usage),
        )
    }

    private companion object {
        val JOINED = setOf("text", "summary", "data")
    }
}

/** OpenRouter's `usage` as [Usage], and its `cost`. */
object OpenRouterUsage {
    /**
     * [u] (the API reference's ChatUsage, the prompt-caching and usage-accounting guides): `prompt_tokens` counts the
     * cached and written tokens too (as OpenAI's), so [Usage.input] is the rest, [Usage.cacheRead]
     * `prompt_tokens_details.cached_tokens`, [Usage.cacheWrite5m] its `cache_write_tokens`; `completion_tokens` includes
     * the reasoning ("subtract … reasoning_tokens … to get visible output"): [Usage.output] all of it, [Usage.reasoning]
     * `completion_tokens_details.reasoning_tokens`.
     */
    fun of(u: JsonObject?): Usage {
        if (u == null) return Usage()
        fun n(o: JsonObject?, k: String) = (o?.get(k) as? JsonPrimitive)?.longOrNull ?: 0
        val pd = u["prompt_tokens_details"] as? JsonObject
        val cd = u["completion_tokens_details"] as? JsonObject
        val cached = n(pd, "cached_tokens")
        val written = n(pd, "cache_write_tokens")
        return Usage(
            input = (n(u, "prompt_tokens") - cached - written).coerceAtLeast(0),
            cacheWrite5m = written,
            cacheRead = cached,
            output = n(u, "completion_tokens"),
            reasoning = n(cd, "reasoning_tokens"),
        )
    }

    /** "cost: total amount charged to your account" (dollars), or null. */
    fun cost(u: JsonObject?): Double? = (u?.get("cost") as? JsonPrimitive)?.doubleOrNull
}

/**
 * OpenRouter's errors as [ApiError] (openrouter.ai/docs/api_reference/errors-and-debugging and /limits, checked
 * 2026-10-19). The body is `{"error": {code, message, metadata}}`:
 * - 401 ("OAuth session expired, disabled/invalid API key") → [ApiError.Auth];
 * - 402 "insufficient credits": `metadata.limit_source` `openrouter_in_flight_budget` is passing (with Retry-After) →
 *   [ApiError.RateLimited]; `openrouter_key_limit` (the key's own limit) → [ApiError.LowCredit] with `limit`; else
 *   (`openrouter_credits`) → [ApiError.LowCredit];
 * - 403: a spent key limit, workspace or guardrail budget ("Budget limit exceeded", a `limit_source`) →
 *   [ApiError.LowCredit] with `limit`; a moderation or guardrail block (`metadata.reasons`, `flagged_input`) →
 *   [ApiError.BadRequest] (not retried: the input was refused); else (permissions) → [ApiError.Auth];
 * - 408, 502 ("model is down or … invalid response"), 503 ("no available model provider"), 5xx → [ApiError.Overloaded];
 * - 429 → [ApiError.RateLimited] after `retry-after`;
 * - the rest (400) → [ApiError.BadRequest].
 */
object OpenRouterErrors {
    private data class Said(val code: String?, val message: String?, val limitSource: String?, val flagged: Boolean, val errorType: String?)

    private fun said(e: JsonObject?): Said {
        val meta = e?.get("metadata") as? JsonObject
        val code = (e?.get("code") as? JsonPrimitive)?.content
        return Said(code, e?.str("message"), meta?.str("limit_source"), meta?.get("reasons") != null || meta?.get("flagged_input") != null, meta?.str("error_type"))
    }

    fun of(code: Int, body: String, retryAfter: String? = null): ApiError {
        val e = runCatching { (tutorJson.parseToJsonElement(body) as JsonObject)["error"] as? JsonObject }.getOrNull()
        return classify(code, said(e), retryAfter, "HTTP $code", body)
    }

    private fun classify(code: Int, s: Said, retryAfter: String?, where: String, raw: String = ""): ApiError {
        val text = "$where${s.limitSource?.let { " ($it)" } ?: ""}${s.errorType?.let { " [$it]" } ?: ""}: ${(s.message ?: raw).take(300)}"
        val msg = s.message.orEmpty()
        return when {
            code == 401 -> ApiError.Auth(text)
            code == 402 && s.limitSource == "openrouter_in_flight_budget" -> ApiError.RateLimited(ApiError.retryAfterMs(retryAfter), text)
            code == 402 && s.limitSource == "openrouter_key_limit" -> ApiError.LowCredit(text, limit = true)
            code == 402 -> ApiError.LowCredit(text)
            code == 403 && (s.limitSource != null || msg.contains("limit exceeded", ignoreCase = true) || msg.contains("budget", ignoreCase = true)) -> ApiError.LowCredit(text, limit = true)
            code == 403 && s.flagged -> ApiError.BadRequest(403, "$text (flagged by moderation)")
            code == 403 -> ApiError.Auth(text)
            code == 429 -> ApiError.RateLimited(ApiError.retryAfterMs(retryAfter), text)
            code == 408 || code >= 500 -> ApiError.Overloaded(code, text)
            else -> ApiError.BadRequest(code, text)
        }
    }

    /** A chunk's `error` (after a 200: the stream had begun): by its numeric code as an HTTP status, else passing. */
    fun ofStream(e: JsonObject): ApiError {
        val s = said(e)
        val code = s.code?.toIntOrNull()
        return if (code != null) classify(code, s, null, "stream")
        else ApiError.Overloaded(500, "stream ${s.code ?: "error"}${s.errorType?.let { " [$it]" } ?: ""}: ${s.message.orEmpty().take(300)}")
    }
}
