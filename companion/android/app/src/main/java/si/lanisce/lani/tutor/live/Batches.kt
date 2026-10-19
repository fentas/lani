package si.lanisce.lani.tutor.live

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import si.lanisce.lani.tutor.AnthropicTransport
import si.lanisce.lani.tutor.ApiError
import si.lanisce.lani.tutor.Effort
import si.lanisce.lani.tutor.KeyGuard
import si.lanisce.lani.tutor.str
import si.lanisce.lani.tutor.tutorJson
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * A message batch as the API reports it: [status] its processing_status (in_progress, canceling, ended), [counts] its
 * request_counts (processing, succeeded, errored, canceled, expired), [resultsUrl] where its results are once it ended.
 * The times are the API's ISO 8601 strings.
 */
data class Batch(
    val id: String,
    val status: String,
    val counts: Map<String, Int>,
    val resultsUrl: String?,
    val createdAt: String?,
    val endedAt: String?,
    val expiresAt: String?,
) {
    val ended: Boolean get() = status == ENDED

    companion object {
        const val IN_PROGRESS = "in_progress"
        const val CANCELING = "canceling"
        const val ENDED = "ended"
    }
}

/**
 * One request's result in an ended batch, by its [customId]: [type] succeeded (its [message], a Messages API response),
 * errored (its [error] as the API sent it: `{"type": "error", "error": {"type": …, "message": …}}`), canceled or expired.
 */
data class BatchResult(val customId: String, val type: String, val message: JsonObject?, val error: JsonObject?)

/**
 * Anthropic's Message Batches API (plan 5, phase C: "Batches for what can wait"): half price, for work nobody waits for
 * (the studio task's stories and dialog variants, a digest). Plain HTTP as [AnthropicTransport] is, the same key and the
 * same guard: the key goes only where [KeyGuard.mayCarryKey] lets it, for [results] too (its URL is the API's answer).
 * Throws [ApiError] ([ApiError.of] for an HTTP error, [ApiError.Network] for no answer).
 *
 * A request's params are a Messages API body as the tutor builds it, made non-streaming: `stream` and each tool's
 * `eager_input_streaming` are left out. [betas] go with the batch (the API applies the header to each request), and
 * [Effort.BETA] when some request has an effort message ([Effort.inRequest]).
 */
class AnthropicBatches(
    private val baseUrl: String,
    private val key: suspend () -> String?,
    private val client: OkHttpClient = DEFAULT_CLIENT,
    private val betas: List<String> = AnthropicTransport.BETAS,
) {
    /**
     * POST /v1/messages/batches with [requests] (custom_id to params). Each custom_id is 1–64 letters, digits, `_` or `-`
     * and unique in the batch: anything else is an [IllegalArgumentException] before any call.
     */
    suspend fun create(requests: List<Pair<String, JsonObject>>): Batch {
        require(requests.isNotEmpty()) { "a batch needs at least one request" }
        for ((id, _) in requests) require(CUSTOM_ID.matches(id)) { "a custom_id is 1-64 letters, digits, _ or -: \"$id\"" }
        requests.groupBy { it.first }.filterValues { it.size > 1 }.keys.firstOrNull()?.let { throw IllegalArgumentException("custom_id \"$it\" twice in one batch") }
        val body = buildJsonObject {
            put("requests", JsonArray(requests.map { (id, params) -> buildJsonObject { put("custom_id", id); put("params", nonStreaming(params)) } }))
        }
        val all = if (requests.any { Effort.inRequest(it.second) }) betas + Effort.BETA else betas
        val req = request("/v1/messages/batches", apiKey())
            .apply { if (all.isNotEmpty()) header("anthropic-beta", all.joinToString(",")) }
            .post(body.toString().toRequestBody(JSON))
            .build()
        return batchOf(send(req, "POST /v1/messages/batches"))
    }

    /** GET /v1/messages/batches/{id}: where the batch is. */
    suspend fun get(id: String): Batch = batchOf(send(request("/v1/messages/batches/${checked(id)}", apiKey()).get().build(), "GET a batch"))

    /** POST /v1/messages/batches/{id}/cancel: the batch, canceling (its finished requests keep their results). */
    suspend fun cancel(id: String): Batch =
        batchOf(send(request("/v1/messages/batches/${checked(id)}/cancel", apiKey()).post(ByteArray(0).toRequestBody(JSON)).build(), "cancel a batch"))

    /**
     * An ended [batch]'s results, from its results_url (JSONL: one result a line, in any order: match them by
     * [BatchResult.customId]; blank lines skipped). The key goes only to a URL [KeyGuard.mayCarryKey] lets it: any other is
     * an [IllegalStateException], before any call; so is a batch without results yet.
     */
    suspend fun results(batch: Batch): List<BatchResult> {
        val raw = batch.resultsUrl ?: throw IllegalStateException("batch ${batch.id} has no results yet (${batch.status})")
        val url = raw.toHttpUrlOrNull() ?: throw IllegalStateException("batch ${batch.id}: a results URL that isn't one")
        check(KeyGuard.mayCarryKey(url, baseUrl)) { "the key goes only to the Messages API: not to ${url.host}" }
        val req = authorized(url, apiKey()).get().build()
        val (code, lines, retryAfter) = execute(req) { r ->
            if (r.isSuccessful) {
                val out = mutableListOf<String>()
                val source = r.body?.source()
                while (source != null) out += source.readUtf8Line() ?: break
                Triple<Int, List<String>, String?>(r.code, out, null)
            } else {
                Triple(r.code, listOf(r.body?.string().orEmpty()), r.header("retry-after"))
            }
        }
        if (code !in 200..299) throw ApiError.of(code, lines.joinToString("\n"), retryAfter)
        return lines.filter { it.isNotBlank() }.map { line ->
            val o = runCatching { tutorJson.parseToJsonElement(line) as JsonObject }.getOrNull()
            val result = o?.get("result") as? JsonObject
            val id = o?.str("custom_id")
            val type = result?.str("type")
            if (id == null || type == null) throw ApiError.Overloaded(code, "batch ${batch.id}'s results: a line that isn't a result")
            BatchResult(id, type, result["message"] as? JsonObject, result["error"] as? JsonObject)
        }
    }

    // --- the plumbing ----------------------------------------------------------------------------------

    private fun request(path: String, apiKey: String): Request.Builder {
        val url = (baseUrl.trimEnd('/') + path).toHttpUrlOrNull() ?: throw ApiError.BadRequest(0, "bad base URL")
        check(KeyGuard.mayCarryKey(url, baseUrl)) { "the key goes only to the Messages API" }
        return authorized(url, apiKey)
    }

    private fun authorized(url: HttpUrl, apiKey: String): Request.Builder = Request.Builder().url(url)
        .header("x-api-key", apiKey)
        .header("anthropic-version", AnthropicTransport.VERSION)

    private suspend fun apiKey(): String = key()?.takeIf { it.isNotBlank() } ?: throw ApiError.Auth("no key")

    /** [req]'s answer as an object, or its [ApiError]. */
    private suspend fun send(req: Request, what: String): JsonObject {
        val (code, body, retryAfter) = execute(req) { r -> Triple(r.code, r.body?.string().orEmpty(), r.header("retry-after")) }
        if (code !in 200..299) throw ApiError.of(code, body, retryAfter)
        return runCatching { tutorJson.parseToJsonElement(body) as JsonObject }.getOrNull() ?: throw ApiError.Overloaded(code, "$what: an answer that isn't JSON")
    }

    private fun batchOf(o: JsonObject): Batch {
        val id = o.str("id")
        val status = o.str("processing_status")
        if (id == null || status == null) throw ApiError.Overloaded(200, "an answer without a batch")
        return Batch(
            id = id,
            status = status,
            counts = (o["request_counts"] as? JsonObject).orEmpty().mapNotNull { (k, v) -> (v as? JsonPrimitive)?.intOrNull?.let { k to it } }.toMap(),
            resultsUrl = o.str("results_url"),
            createdAt = o.str("created_at"),
            endedAt = o.str("ended_at"),
            expiresAt = o.str("expires_at"),
        )
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
        /** The API's rule for a custom_id. */
        val CUSTOM_ID = Regex("^[a-zA-Z0-9_-]{1,64}$")
        private val BATCH_ID = Regex("^[A-Za-z0-9_-]{1,128}$")
        private val JSON = "application/json".toMediaType()

        /** A batch's create can be large, its results too; nothing streams. */
        val DEFAULT_CLIENT: OkHttpClient by lazy {
            OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS).build()
        }

        /** [params] for a batch: without `stream`, and its tools without `eager_input_streaming` (a non-streaming request). */
        fun nonStreaming(params: JsonObject): JsonObject {
            val tools = params["tools"] as? JsonArray
            return JsonObject(buildMap {
                for ((k, v) in params) {
                    when {
                        k == "stream" -> Unit
                        k == "tools" && tools != null -> put(k, JsonArray(tools.map { t -> (t as? JsonObject)?.let { JsonObject(it - "eager_input_streaming") } ?: t }))
                        else -> put(k, v)
                    }
                }
            })
        }

        /** A batch id as a path segment: letters, digits, `_` and `-` only. */
        private fun checked(id: String): String = id.also { require(BATCH_ID.matches(it)) { "not a batch id: \"$it\"" } }
    }
}
