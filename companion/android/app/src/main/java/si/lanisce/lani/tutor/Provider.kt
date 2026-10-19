package si.lanisce.lani.tutor

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The provider layer (docs/plans/05-built/providers.md): the tool loop ([TutorLoop]) runs the same on every LLM provider.
 * A provider builds a request from the stable [Prefix] and a thread, streams the call and hands back one normalized
 * [Step]; a [ThreadFormat] knows how its threads are written. A thread never changes its model, so it stores its own
 * provider's native messages, and nothing converts a thread from one provider to another.
 *
 * - [AnthropicProvider]: the Messages API ([AnthropicTransport]), exactly as before the layer (AnthropicWireTest pins it).
 * - [OpenAIProvider]: the Responses API ([OpenAITransport]) with an API key or the ChatGPT sign-in ([ChatGPTAuth]).
 *
 * **How the live tutor uses it:** pick the provider for the thread's model ([ProviderId.of]), build the turn's user
 * blocks in the neutral shape ([textBlock], [ShadowImages.block]: Anthropic's own block shapes serve as the neutral
 * ones), add them with [ThreadFormat.append] (it places an effort change where the provider wants it), and run
 * [TutorLoop.run] with that provider: each step's native items are appended and saved, tool calls arrive as [ToolUse],
 * errors as [ApiError].
 */

/** Which company's API a model is on, by its id. */
@Serializable
enum class ProviderId(val id: String) {
    ANTHROPIC("anthropic"),
    OPENAI("openai"),
    /** Google's Gemini API, direct, with a Google AI Studio key ([GeminiProvider]). */
    GEMINI("gemini"),
    /** OpenRouter, "one sign-in, every model": its ids name the vendor (google/gemini-4-argon) ([OpenRouterProvider]). */
    OPENROUTER("openrouter");

    /** "Anthropic", "OpenAI", "Gemini", "OpenRouter". */
    val label: String get() = when (this) {
        ANTHROPIC -> "Anthropic"
        OPENAI -> "OpenAI"
        GEMINI -> "Gemini"
        OPENROUTER -> "OpenRouter"
    }

    companion object {
        private val OPENAI_MODEL = Regex("""^(gpt-|o\d|chatgpt-|codex-)""")

        /**
         * The provider of [model]: OpenRouter's ids name their vendor (`google/gemini-4-argon`, `openai/gpt-6.1-sol`),
         * Gemini's begin with gemini-, OpenAI's with gpt-, o3, chatgpt-, codex-; else Anthropic's (claude-…).
         */
        fun of(model: String): ProviderId = when {
            model.contains('/') -> OPENROUTER
            model.startsWith("gemini-") -> GEMINI
            OPENAI_MODEL.containsMatchIn(model) -> OPENAI
            else -> ANTHROPIC
        }
    }
}

/**
 * How a call is paid: an API key (the learner pays per token), the learner's ChatGPT plan (no per-token cost), the
 * Gemini API's free tier (no per-token cost; Google may use what's sent: [GeminiTerms]), OpenRouter's credits (the
 * price OpenRouter reports for each call).
 */
object Billing {
    const val API_KEY = "api_key"
    const val CHATGPT_PLAN = "chatgpt_plan"
    const val FREE_TIER = "free_tier"
    const val OPENROUTER = "openrouter_credits"

    /** Whether calls paid by [billing] cost the learner nothing per token (the API-equivalent is kept beside them). */
    fun free(billing: String): Boolean = billing == CHATGPT_PLAN || billing == FREE_TIER
}

/** Why a response stopped, in the loop's words (the Messages API's; OpenAI's are mapped onto them). */
object Stop {
    const val END_TURN = "end_turn"
    const val TOOL_USE = "tool_use"
    const val MAX_TOKENS = "max_tokens"
    const val REFUSAL = "refusal"
}

/**
 * One call's response, the same for every provider.
 *
 * @property items what the thread keeps of it, in the provider's own format: Anthropic one assistant message (its
 *   thinking blocks with their signatures), OpenAI the response's output items (reasoning items with their
 *   `encrypted_content`, messages with their `phase`, function calls), passed back unchanged.
 * @property toolCalls its tool calls in order; a call whose streamed input wasn't JSON has [ToolUse.badJson] (the loop
 *   answers it with an error instead of running it).
 * @property text its visible text.
 * @property stopReason [Stop]'s: end_turn, tool_use, max_tokens, refusal (or the provider's own for anything else).
 * @property stopDetails a refusal's details (Anthropic's stop_details; OpenAI's refusal text or failure code).
 * @property usage its tokens: [Usage.cacheRead] the cached input, [Usage.reasoning] the reasoning tokens where reported.
 * @property kept what stays of a response cut off at [Stop.MAX_TOKENS]: its text, as the thread's items.
 * @property visibleChars its text and its tool calls' name and input in characters (the thinking's estimate).
 * @property inputTransformations Anthropic's `input_transformations` (null: none reported).
 */
data class Step(
    val items: List<JsonObject>,
    val toolCalls: List<ToolUse>,
    val text: String,
    val stopReason: String?,
    val stopDetails: JsonObject?,
    val usage: Usage,
    val model: String?,
    val id: String?,
    val firstTokenMs: Long?,
    val totalMs: Long,
    val visibleChars: Int,
    val kept: List<JsonObject> = emptyList(),
    val inputTransformations: String? = null,
    val rawUsage: RawUsage = RawUsage(),
    /** What the provider says the call cost, in dollars (OpenRouter's `usage.cost`); null: priced by the table. */
    val cost: Double? = null,
)

/**
 * An LLM provider for the tool loop: one per thread's model and credential. Throws [ApiError] for what the API (or the
 * network) refused, classified the same for every provider.
 */
interface TutorProvider {
    val id: ProviderId

    /** How its threads are written. */
    val format: ThreadFormat

    /** How its calls are paid ([Billing]). */
    val billing: String

    /** The models the credential may use (not a model call: the credential's check). */
    suspend fun models(): List<ModelInfo>

    /**
     * The prefix's fingerprint as this provider sends it: a thread made under another one (an app update changed a tool
     * or the prompt, another credential) can't go on and restarts as text.
     */
    fun prefixHash(prefix: Prefix): String

    /**
     * The request for the next call of a turn on [model] for [task] over [messages] (the thread, in this provider's
     * format), at the top-level [effort] (a thread's, fixed for its life; null: none) with room for [maxTokens].
     */
    fun request(prefix: Prefix, model: String, task: Task, messages: List<JsonObject>, maxTokens: Int, effort: String?): JsonObject

    /** Streams one call of [body] ([onDelta] for each piece of content) and normalizes its response. */
    suspend fun call(body: JsonObject, onDelta: () -> Unit = {}): Step

    /**
     * [call], and [onTool] with a tool call's input so far as it streams (the live tutor's typing bubble). A provider
     * that can't tell calls as [call] does.
     */
    suspend fun callTools(body: JsonObject, onDelta: () -> Unit, onTool: (ToolInputDelta) -> Unit): Step = call(body, onDelta)
}

/**
 * How a provider writes its threads. The turn's content comes in neutral blocks: text blocks ([textBlock]) and image
 * blocks in the Messages API's base64 shape ([ShadowImages.block]).
 */
interface ThreadFormat {
    /** A user message of the neutral [blocks]. */
    fun user(blocks: List<JsonObject>): JsonObject

    /**
     * [messages] with a turn's user [blocks] added, at the level [want] on a thread whose top-level effort is [top]
     * (null: whatever is in effect): an effort change goes where the provider takes it (Anthropic's effort message,
     * OpenAI's configuration_update). Only the unanswered end ever changes.
     */
    fun append(messages: List<JsonObject>, blocks: List<JsonObject>, top: String?, want: String?): List<JsonObject>

    /**
     * The tool calls at the end of [messages] still waiting for their results: null when the thread ends with input
     * (the next step is a call), empty when it ends with an answer without tool calls (the turn is done).
     */
    fun pending(messages: List<JsonObject>): List<ToolUse>?

    /** The results of [calls] ([outcomes] in the same order), as the thread's next items. */
    fun results(calls: List<ToolUse>, outcomes: List<ToolOutcome>): List<JsonObject>

    companion object {
        /** The format of [model]'s threads. */
        fun of(model: String): ThreadFormat = when (ProviderId.of(model)) {
            ProviderId.ANTHROPIC -> AnthropicFormat
            ProviderId.OPENAI -> OpenAIFormat
            ProviderId.GEMINI -> GeminiFormat
            ProviderId.OPENROUTER -> ChatFormat
        }
    }
}

/** What reads a thread of any provider (the loop's callers: records, restarts, the panel). */
object Threads {
    /**
     * Whether the model answered anything in [messages] (an assistant message, a reasoning item, a function call, a
     * Gemini model turn).
     */
    fun answered(messages: List<JsonObject>): Boolean = messages.any { it.role() == "assistant" || it.role() == "model" || it.str("type") in OUTPUT }

    /**
     * The model's visible text in [items] (the Messages API's text blocks, the Responses API's output_text parts, chat
     * completions' content, a Gemini model turn's text parts but its thoughts).
     */
    fun text(items: List<JsonObject>): String =
        (items.filter { it.role() == "assistant" }.flatMap { it.blocks() }.filter { it.str("type") == "text" || it.str("type") == "output_text" }.map { it.str("text").orEmpty() } +
            items.filter { it.role() == "model" }.flatMap { GeminiFormat.parts(it) }.filter { it.str("text").orEmpty().isNotEmpty() && it["thought"]?.toString() != "true" }.map { it.str("text").orEmpty() })
            .joinToString("\n")

    private val OUTPUT = setOf("reasoning", "function_call")
}

/** The Messages API's threads (unchanged by the provider layer: AnthropicWireTest). */
object AnthropicFormat : ThreadFormat {
    override fun user(blocks: List<JsonObject>): JsonObject = buildJsonObject {
        put("role", "user")
        put("content", JsonArray(blocks))
    }

    override fun append(messages: List<JsonObject>, blocks: List<JsonObject>, top: String?, want: String?): List<JsonObject> =
        Effort.append(messages, blocks, top, want)

    override fun pending(messages: List<JsonObject>): List<ToolUse>? {
        val last = messages.lastOrNull() ?: return null
        if (last.role() != "assistant") return null
        return last.blocks().filter { it.str("type") == "tool_use" }.map { u ->
            ToolUse(u.str("id").orEmpty(), u.str("name").orEmpty(), u["input"] as? JsonObject ?: JsonObject(emptyMap()))
        }
    }

    override fun results(calls: List<ToolUse>, outcomes: List<ToolOutcome>): List<JsonObject> = listOf(
        buildJsonObject {
            put("role", "user")
            put(
                "content",
                JsonArray(
                    calls.zip(outcomes).map { (u, o) ->
                        buildJsonObject {
                            put("type", "tool_result")
                            put("tool_use_id", u.id)
                            put("content", o.content)
                            if (o.isError) put("is_error", true)
                        }
                    },
                ),
            )
        },
    )
}

/**
 * The Messages API as a [TutorProvider] over [transport] (the plain client, [AnthropicTransport], or a test's script):
 * the request is [Prefix.request] and the response the stream as received, so the wire format is exactly as before.
 */
class AnthropicProvider(private val transport: TutorTransport) : TutorProvider {
    override val id = ProviderId.ANTHROPIC
    override val format: ThreadFormat = AnthropicFormat
    override val billing = Billing.API_KEY

    override suspend fun models(): List<ModelInfo> = transport.models()

    override fun prefixHash(prefix: Prefix): String = prefix.hash

    override fun request(prefix: Prefix, model: String, task: Task, messages: List<JsonObject>, maxTokens: Int, effort: String?): JsonObject =
        prefix.request(model, task, messages, maxTokens, effort)

    override suspend fun call(body: JsonObject, onDelta: () -> Unit): Step = step(transport.stream(body, onDelta))

    override suspend fun callTools(body: JsonObject, onDelta: () -> Unit, onTool: (ToolInputDelta) -> Unit): Step = step(transport.streamTools(body, onDelta, onTool))

    companion object {
        /** A streamed response as a [Step]: its content as one assistant message, its tool_use blocks as the calls. */
        fun step(r: StreamResult): Step {
            val m = r.message
            val kept = m.content.filter { it.str("type") == "text" && !it.str("text").isNullOrBlank() }
            return Step(
                items = if (m.content.isEmpty()) emptyList() else listOf(TutorLoop.assistant(m.content)),
                toolCalls = m.toolUses.map { u ->
                    val id = u.str("id").orEmpty()
                    ToolUse(id, u.str("name").orEmpty(), u["input"] as? JsonObject ?: JsonObject(emptyMap()), m.badInput[id])
                },
                text = m.text,
                stopReason = m.stopReason,
                stopDetails = m.stopDetails,
                usage = m.usage,
                model = m.model,
                id = m.id,
                firstTokenMs = r.firstTokenMs,
                totalMs = r.totalMs,
                visibleChars = m.visibleChars(),
                kept = if (kept.isEmpty()) emptyList() else listOf(TutorLoop.assistant(kept)),
                inputTransformations = m.inputTransformations,
                rawUsage = m.rawUsage,
            )
        }
    }
}
