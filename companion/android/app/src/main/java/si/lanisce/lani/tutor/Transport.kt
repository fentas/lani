package si.lanisce.lani.tutor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A model the key may use (GET /v1/models). */
@Serializable
data class ModelInfo(
    val id: String,
    val displayName: String? = null,
    val createdAt: String? = null,
    val maxInputTokens: Long? = null,
    val maxTokens: Long? = null,
)

/** A streamed response, with the time to its first token and in all (milliseconds). */
data class StreamResult(val message: AssistantMessage, val firstTokenMs: Long?, val totalMs: Long)

/**
 * How the tutor reaches Claude (plan 5, "One interface"): BYOK calls the Messages API with the learner's key; later the
 * covered variant calls our proxy with the account's token, with the same requests. Throws [ApiError].
 */
interface TutorTransport {
    /** The models the key may use: GET /v1/models, not a model call (the key's check). */
    suspend fun models(): List<ModelInfo>

    /** POST /v1/messages with `"stream": true`; [onDelta] for each piece of content as it comes. */
    suspend fun stream(body: JsonObject, onDelta: () -> Unit = {}): StreamResult
}

/**
 * Where the key may go: only to the Messages API itself ([API]), or, in a debug build, to the mock base URL QA set
 * (the shadow's debug-only setting): never to the bridge, glas or anywhere else.
 */
object KeyGuard {
    const val API = "https://api.anthropic.com"

    /** The base URL a build calls: the API, or in a debug build ([debug]) a mock's address set in the panel. */
    fun baseUrl(debug: Boolean, override: String?): String =
        override?.takeIf { debug && it.isNotBlank() }?.trim()?.trimEnd('/')?.takeIf { it.toHttpUrlOrNull() != null } ?: API

    /** Whether a request to [url] may carry the key, for a transport whose base is [base]. */
    fun mayCarryKey(url: HttpUrl, base: String): Boolean {
        val b = base.toHttpUrlOrNull() ?: return false
        if (url.host == "api.anthropic.com" && url.scheme == "https") return true
        // the debug mock: the same scheme, host and port as the base the panel set
        return b.host != "api.anthropic.com" && url.scheme == b.scheme && url.host == b.host && url.port == b.port
    }
}

/** The plain client (plan 5: OkHttp, okhttp-sse and kotlinx.serialization; the Java SDK is a later spike). */
class AnthropicTransport(
    private val baseUrl: String,
    private val key: suspend () -> String?,
    private val client: OkHttpClient = defaultClient,
    private val clock: () -> Long = System::currentTimeMillis,
) : TutorTransport {

    private fun request(path: String, apiKey: String): Request.Builder {
        val url = (baseUrl.trimEnd('/') + path).toHttpUrlOrNull() ?: throw ApiError.BadRequest(0, "bad base URL")
        check(KeyGuard.mayCarryKey(url, baseUrl)) { "the key goes only to the Messages API" }
        return Request.Builder().url(url)
            .header("x-api-key", apiKey)
            .header("anthropic-version", VERSION)
    }

    private suspend fun apiKey(): String = key()?.takeIf { it.isNotBlank() } ?: throw ApiError.Auth("no key")

    override suspend fun models(): List<ModelInfo> {
        val req = request("/v1/models?limit=100", apiKey()).get().build()
        val (code, body, retryAfter) = execute(req) { r -> Triple(r.code, r.body?.string().orEmpty(), r.header("retry-after")) }
        if (code !in 200..299) throw ApiError.of(code, body, retryAfter)
        val data = runCatching { (tutorJson.parseToJsonElement(body) as JsonObject)["data"] as JsonArray }.getOrNull()
            ?: throw ApiError.Overloaded(code, "GET /v1/models: an answer without data")
        return data.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            ModelInfo(
                id = o.str("id") ?: return@mapNotNull null,
                displayName = o.str("display_name"),
                createdAt = o.str("created_at"),
                maxInputTokens = (o["max_input_tokens"] as? JsonPrimitive)?.longOrNull,
                maxTokens = (o["max_tokens"] as? JsonPrimitive)?.longOrNull,
            )
        }
    }

    override suspend fun stream(body: JsonObject, onDelta: () -> Unit): StreamResult {
        val sent = JsonObject(body + ("stream" to JsonPrimitive(true)))
        val req = request("/v1/messages", apiKey())
            .header("accept", "text/event-stream")
            .header("anthropic-beta", BETAS.joinToString(","))
            .post(sent.toString().toRequestBody(JSON))
            .build()
        val started = clock()
        val acc = StreamAccumulator()
        var first: Long? = null
        return suspendCancellableCoroutine { cont ->
            fun fail(e: ApiError) {
                if (cont.isActive) cont.resumeWithException(e)
            }
            val source = EventSources.createFactory(client).newEventSource(req, object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (acc.accept(data)) {
                        if (first == null) first = clock() - started
                        onDelta()
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    acc.error?.let { return fail(it) }
                    if (!acc.finished) return fail(ApiError.Network("the stream ended early"))
                    if (cont.isActive) cont.resume(StreamResult(acc.message(), first, clock() - started))
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    val e = when {
                        response != null && !response.isSuccessful ->
                            ApiError.of(response.code, runCatching { response.body?.string() }.getOrNull().orEmpty(), response.header("retry-after"))
                        acc.error != null -> acc.error!!
                        t is ApiError -> t
                        t?.suppressed?.firstOrNull() is ApiError -> t.suppressed.first() as ApiError
                        else -> ApiError.Network(t?.message ?: "the stream failed")
                    }
                    fail(e)
                }
            })
            cont.invokeOnCancellation { source.cancel() }
        }
    }

    /** Runs [req] off the main thread, cancelled with the coroutine; an [IOException] is a [ApiError.Network]. */
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
        const val VERSION = "2023-06-01"
        /**
         * Phase 0's betas: the thinking-binding controls, so the response reports `input_transformations` when the API
         * changed the history it got (preserved thinking: the thread must be append-only; logged per call).
         */
        val BETAS = listOf("thinking-binding-controls-2026-08-01")
        private val JSON = "application/json".toMediaType()
        /** A turn's call streams for seconds; a long thinking pause is still a stream (pings). */
        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
        }
    }
}
