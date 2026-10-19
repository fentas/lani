package si.lanisce.lani.tutor

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * The Messages API's wire format as far as the in-app tutor uses it (docs/plans/05-in-app-tutor.md, "The client"): a
 * streamed response put back together ([StreamAccumulator]), its usage ([Usage]) and its errors ([ApiError]). Plain
 * kotlinx.serialization JSON, no SDK.
 */

/** Tolerant JSON for what the API and the bundled files send. */
internal val tutorJson = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/**
 * A response's tokens, as `usage` counts them: [input] the uncached rest, [cacheWrite5m] and [cacheWrite1h] what was
 * written to the cache (usage.cache_creation), [cacheRead] what came from it, [output] what the model wrote (its
 * thinking included); [reasoning] the part of [output] that was reasoning, where the provider reports it (OpenAI's
 * output_tokens_details.reasoning_tokens; the Messages API doesn't: 0, left out of the records).
 *
 * OpenAI's cache writes (input_tokens_details.cache_write_tokens, kept at least 30 minutes) are filed as
 * [cacheWrite5m], the short-lived writes; its price table prices them at its cache-write rate (companion/tutor/prices.json).
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class Usage(
    val input: Long = 0,
    val cacheWrite5m: Long = 0,
    val cacheWrite1h: Long = 0,
    val cacheRead: Long = 0,
    val output: Long = 0,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val reasoning: Long = 0,
) {
    val cacheWrite: Long get() = cacheWrite5m + cacheWrite1h
    /** The whole prompt: what the thread weighs now (the restart's measure, plan: "input + cache reads + cache writes"). */
    val prompt: Long get() = input + cacheWrite + cacheRead

    companion object {
        /**
         * [o] (a `usage` object) over [before]: the fields it has replace [before]'s (a stream's message_delta carries the
         * final counts). A write without its breakdown by lifetime keeps the 1-hour part [before] had (the API sends the
         * breakdown in message_start only, and the delta's total alone: Jan's calls, 2026-10-16), the rest 5-minute.
         */
        fun merge(before: Usage, o: JsonObject?): Usage {
            if (o == null) return before
            fun n(k: String) = (o[k] as? JsonPrimitive)?.longOrNull
            val creation = o["cache_creation"] as? JsonObject
            val w5 = (creation?.get("ephemeral_5m_input_tokens") as? JsonPrimitive)?.longOrNull
            val w1 = (creation?.get("ephemeral_1h_input_tokens") as? JsonPrimitive)?.longOrNull
            val total = n("cache_creation_input_tokens")
            val (write5m, write1h) = when {
                w5 != null || w1 != null -> (w5 ?: 0) to (w1 ?: 0)
                total != null -> minOf(before.cacheWrite1h, total).let { h -> (total - h) to h }
                else -> before.cacheWrite5m to before.cacheWrite1h
            }
            return Usage(
                input = n("input_tokens") ?: before.input,
                cacheWrite5m = write5m,
                cacheWrite1h = write1h,
                cacheRead = n("cache_read_input_tokens") ?: before.cacheRead,
                output = n("output_tokens") ?: before.output,
                reasoning = before.reasoning,
            )
        }
    }
}

/**
 * A response's `usage` objects as the API sent them, in `message_start` ([start]) and in `message_delta` ([delta]): for
 * the cache question (does `cache_creation.ephemeral_1h_input_tokens` come back, or only `cache_creation_input_tokens`,
 * which [Usage.merge] then files as 5-minute writes?). The shadow keeps them for a few calls ([UsageSample]).
 */
@Serializable
data class RawUsage(val start: JsonObject? = null, val delta: JsonObject? = null) {
    /**
     * How the cache writes were reported: [BREAKDOWN] (usage.cache_creation by lifetime, in either event), [TOTAL] (only
     * cache_creation_input_tokens), null (neither).
     */
    val cacheReport: String?
        get() = when {
            listOfNotNull(start, delta).any { it["cache_creation"] is JsonObject } -> BREAKDOWN
            listOfNotNull(start, delta).any { it["cache_creation_input_tokens"] is JsonPrimitive } -> TOTAL
            else -> null
        }

    companion object {
        const val BREAKDOWN = "breakdown"
        const val TOTAL = "total"
    }
}

/**
 * Which calls keep their [RawUsage] in the shadow's calls log: the first call of each thread (the day's, a restarted
 * one, a stateless turn's own) and every [EVERY]th call of the day.
 */
object UsageSample {
    const val EVERY = 20

    /** Whether the day's [n]th call (from 1) keeps its raw usage; [firstOfThread]: its thread had no answer before it. */
    fun keep(firstOfThread: Boolean, n: Int): Boolean = firstOfThread || (n > 0 && n % EVERY == 0)
}

/**
 * One finished response: its [content] blocks as the API sent them (thinking blocks with their signature, passed back
 * unchanged), why it stopped, its [usage]; [badInput]: the tool_use blocks whose streamed input wasn't valid JSON (by
 * id: the text as received), which the loop answers with an error instead of running them; [rawUsage] the usage
 * objects as received.
 */
data class AssistantMessage(
    val id: String?,
    val model: String?,
    val content: List<JsonObject>,
    val stopReason: String?,
    val stopDetails: JsonObject?,
    val usage: Usage,
    val badInput: Map<String, String> = emptyMap(),
    /**
     * What the API changed of the history it was sent (`input_transformations`, with the thinking-binding-controls beta:
     * a thinking block dropped because the history before it changed), as received; null when it reports none. Phase 0
     * logs it: an append-only thread should never have one.
     */
    val inputTransformations: String? = null,
    val rawUsage: RawUsage = RawUsage(),
) {
    val toolUses: List<JsonObject> get() = content.filter { it.str("type") == "tool_use" }
    val text: String get() = content.filter { it.str("type") == "text" }.joinToString("\n") { it.str("text").orEmpty() }

    /** What it showed, in characters: its text and its tool calls' name and input, not its thinking (the thinking's estimate). */
    fun visibleChars(): Int = content.sumOf { b ->
        when (b.str("type")) {
            "text" -> b.str("text").orEmpty().length
            "tool_use" -> b.str("name").orEmpty().length + (b["input"]?.toString()?.length ?: 0)
            else -> 0
        }
    }
}

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/**
 * An error an LLM API answered (or the network gave), the same classes for every provider: how the loop and the queue
 * treat it, by the plan's table. [of] and [ofStream] read the Messages API's; [OpenAIErrors] the Responses API's and
 * the ChatGPT sign-in's.
 */
sealed class ApiError(message: String) : Exception(message) {
    /**
     * 401: the key is wrong or revoked (OpenAI: also a ChatGPT sign-in that can't be used or renewed). The panel asks for
     * a new one; nothing is lost.
     */
    class Auth(message: String) : ApiError(message)
    /**
     * The money is out: the credit balance is too low (its message on a 400 or 402, or a billing error), or ([limit]) a
     * spend limit was reached (the workspace's: a 400 "You have reached your specified workspace API usage limits"; the
     * tier's: a 429 with error_code enforced_spend_limit_reached and no retry-after), or ([allowance]) the ChatGPT plan's
     * usage limit for the app or the plan (OpenAI's subscription_sharing_usage_limit_exceeded). Not retried: routine
     * work waits.
     */
    class LowCredit(message: String, val limit: Boolean = false, val allowance: Boolean = false) : ApiError(message)
    /** 429: waits for `retry-after` ([retryAfterMs]), then tries again. */
    class RateLimited(val retryAfterMs: Long?, message: String) : ApiError(message)
    /** 529 overloaded, 5xx, 408/409, an error in the stream: retried with backoff. */
    class Overloaded(val code: Int, message: String) : ApiError(message)
    /** A request the API won't take (400, 403, 404, 413): no retry. */
    class BadRequest(val code: Int, message: String) : ApiError(message)
    /** No answer at all: offline, a timeout, the connection broke. Retried; the event stays queued. */
    class Network(message: String) : ApiError(message)

    val retryable: Boolean get() = this is RateLimited || this is Overloaded || this is Network

    /** A short name for the panel and the export. */
    val kind: String
        get() = when (this) {
            is Auth -> "auth"
            is LowCredit -> if (allowance) "allowance" else if (limit) "spend_limit" else "low_credit"
            is RateLimited -> "rate_limited"
            is Overloaded -> "overloaded"
            is BadRequest -> "bad_request"
            is Network -> "network"
        }

    companion object {
        /** An HTTP answer [code] with [body] (the API's `{"type":"error","error":{…}}`) and its `retry-after` header. */
        fun of(code: Int, body: String, retryAfter: String? = null): ApiError {
            val (type, message, errorCode) = errorOf(body)
            val said = "HTTP $code${type?.let { " $it" } ?: ""}${errorCode?.let { " ($it)" } ?: ""}: ${message ?: body.take(200)}"
            val credit = message?.contains("credit balance is too low", ignoreCase = true) == true
            return when {
                code == 401 -> Auth(said)
                (code == 400 || code == 402) && credit -> LowCredit(said)
                code == 402 || type == "billing_error" -> LowCredit(said)
                code == 400 && message?.contains("workspace API usage limits", ignoreCase = true) == true -> LowCredit(said, limit = true)
                code == 429 && (errorCode == "enforced_spend_limit_reached" || body.contains("enforced_spend_limit_reached")) -> LowCredit(said, limit = true)
                code == 429 -> RateLimited(retryAfterMs(retryAfter), said)
                code == 408 || code == 409 || code >= 500 -> Overloaded(code, said)
                else -> BadRequest(code, said)
            }
        }

        /** An `error` event in the stream (its data). */
        fun ofStream(data: String): ApiError {
            val (type, message, _) = errorOf(data)
            val said = "stream ${type ?: "error"}: ${message ?: data.take(200)}"
            return when (type) {
                "rate_limit_error" -> RateLimited(null, said)
                "authentication_error" -> Auth(said)
                "billing_error" -> LowCredit(said)
                "invalid_request_error" -> if (message?.contains("credit balance", true) == true) LowCredit(said) else BadRequest(400, said)
                "overloaded_error" -> Overloaded(529, said)
                else -> Overloaded(500, said)
            }
        }

        /** `retry-after` in seconds (the API's), as milliseconds; null when absent or not a number. */
        fun retryAfterMs(header: String?): Long? = header?.trim()?.toDoubleOrNull()?.takeIf { it >= 0 }?.let { (it * 1000).toLong() }

        /** The error's type, message and error_code (inside `error` or beside it). */
        private fun errorOf(body: String): Triple<String?, String?, String?> = runCatching {
            val o = tutorJson.parseToJsonElement(body) as JsonObject
            val e = (o["error"] as? JsonObject) ?: o
            Triple(e.str("type"), e.str("message"), e.str("error_code") ?: o.str("error_code"))
        }.getOrDefault(Triple(null, null, null))
    }
}

/**
 * Puts a streamed response back together from its server-sent events (the API's `message_start`, `content_block_start`,
 * `content_block_delta` (text, thinking, signature, partial tool input), `content_block_stop`, `message_delta`,
 * `message_stop`, `ping`, `error`). Feed it each event's data in order; [message] then gives the response.
 */
class StreamAccumulator {
    private class Block(val start: JsonObject) {
        val text = StringBuilder()
        val thinking = StringBuilder()
        var signature: String? = start.str("signature")
        val json = StringBuilder()
    }

    private val blocks = sortedMapOf<Int, Block>()
    private var id: String? = null
    private var model: String? = null
    private var stopReason: String? = null
    private var stopDetails: JsonObject? = null
    private var usage = Usage()
    private var rawStart: JsonObject? = null
    private var rawDelta: JsonObject? = null
    private var transformations: String? = null

    private fun transformations(o: JsonObject?) {
        val t = o?.get("input_transformations") ?: return
        if (t is JsonNull || (t is kotlinx.serialization.json.JsonArray && t.isEmpty())) return
        transformations = t.toString()
    }

    /** message_stop arrived: the response is whole. */
    var finished = false
        private set
    /** The tool call whose input grew last ([takeTool]): the chat's live bubble reads `reply`'s. */
    private var tool: ToolInputDelta? = null

    /** The tool call whose input grew since the last call (its input so far), once; null when none did. */
    fun takeTool(): ToolInputDelta? = tool.also { tool = null }
    /** An `error` event in the stream (the stream ends with it). */
    var error: ApiError? = null
        private set

    /** Takes one event's [data]; true when it carried content (a delta): the first one is the first token's time. */
    fun accept(data: String): Boolean {
        val o = runCatching { tutorJson.parseToJsonElement(data) as? JsonObject }.getOrNull() ?: return false
        when (o.str("type")) {
            "message_start" -> {
                val m = o["message"] as? JsonObject ?: return false
                id = m.str("id")
                model = m.str("model")
                usage = Usage.merge(usage, m["usage"] as? JsonObject)
                (m["usage"] as? JsonObject)?.let { rawStart = it }
                transformations(m)
                transformations(m["usage"] as? JsonObject)
            }
            "content_block_start" -> {
                val i = o.index() ?: return false
                val start = o["content_block"] as? JsonObject ?: return false
                blocks[i] = Block(start).also { b ->
                    start.str("text")?.let(b.text::append)
                    start.str("thinking")?.let(b.thinking::append)
                }
            }
            "content_block_delta" -> {
                val b = o.index()?.let(blocks::get) ?: return false
                val d = o["delta"] as? JsonObject ?: return false
                when (d.str("type")) {
                    "text_delta" -> b.text.append(d.str("text").orEmpty())
                    "thinking_delta" -> b.thinking.append(d.str("thinking").orEmpty())
                    "signature_delta" -> b.signature = (b.signature.orEmpty() + d.str("signature").orEmpty())
                    "input_json_delta" -> {
                        b.json.append(d.str("partial_json").orEmpty())
                        tool = ToolInputDelta(b.start.str("id").orEmpty(), b.start.str("name").orEmpty(), b.json.toString())
                    }
                    else -> return false
                }
                return true
            }
            "content_block_stop" -> Unit
            "message_delta" -> {
                (o["delta"] as? JsonObject)?.let { d ->
                    d.str("stop_reason")?.let { stopReason = it }
                    (d["stop_details"] as? JsonObject)?.let { stopDetails = it }
                }
                (o["stop_details"] as? JsonObject)?.let { stopDetails = it }
                usage = Usage.merge(usage, o["usage"] as? JsonObject)
                (o["usage"] as? JsonObject)?.let { rawDelta = it }
                transformations(o)
                transformations(o["delta"] as? JsonObject)
                transformations(o["usage"] as? JsonObject)
            }
            "message_stop" -> finished = true
            "error" -> error = ApiError.ofStream(data)
            else -> Unit // ping, and kinds this client doesn't use
        }
        return false
    }

    private fun JsonObject.index(): Int? = (this["index"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

    /** The response as received so far (whole once [finished]). */
    fun message(): AssistantMessage {
        val bad = mutableMapOf<String, String>()
        val content = blocks.values.map { b ->
            when (b.start.str("type")) {
                "text" -> buildJsonObject {
                    put("type", "text")
                    put("text", b.text.toString())
                    b.start["citations"]?.let { put("citations", it) }
                }
                "thinking" -> buildJsonObject {
                    put("type", "thinking")
                    put("thinking", b.thinking.toString())
                    put("signature", b.signature.orEmpty())
                }
                "tool_use", "server_tool_use" -> {
                    val raw = b.json.toString()
                    val input: JsonElement = if (raw.isBlank()) (b.start["input"] ?: JsonObject(emptyMap()))
                    else runCatching { tutorJson.parseToJsonElement(raw) }.getOrNull() as? JsonObject
                        ?: run {
                            b.start.str("id")?.let { bad[it] = raw }
                            JsonObject(emptyMap())
                        }
                    JsonObject(b.start + ("input" to input))
                }
                else -> b.start // redacted_thinking and others: as they came
            }
        }
        return AssistantMessage(id, model, content, stopReason, stopDetails, usage, bad, transformations, RawUsage(rawStart, rawDelta))
    }
}

/** A user message of text blocks. */
internal fun userText(vararg texts: String): JsonObject = buildJsonObject {
    put("role", "user")
    put("content", kotlinx.serialization.json.JsonArray(texts.filter { it.isNotEmpty() }.map { textBlock(it) }))
}

internal fun textBlock(text: String): JsonObject = buildJsonObject {
    put("type", "text")
    put("text", text)
}

internal fun JsonObject.role(): String? = str("role")

internal fun JsonObject.blocks(): List<JsonObject> = when (val c = this["content"]) {
    is kotlinx.serialization.json.JsonArray -> c.mapNotNull { it as? JsonObject }
    is JsonPrimitive -> if (c.isString) listOf(textBlock(c.content)) else emptyList()
    JsonNull, null -> emptyList()
    else -> emptyList()
}

/** The primitive's string content, or null. */
internal fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
