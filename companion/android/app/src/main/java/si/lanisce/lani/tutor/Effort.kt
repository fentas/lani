package si.lanisce.lani.tutor

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The thinking effort (`output_config.effort`; plan 5, "Presets"): how much the model thinks and writes. It applies to
 * every output token, thinking included, which is billed as output. The levels in order; [MORE] sit behind the panel's
 * "more" toggle. Which model takes which: [ModelCatalog.efforts].
 *
 * **Within a thread** (the effort docs, "Change effort mid-conversation"): the top-level effort shapes the rendered
 * prompt, so changing it between requests clears the thread's cache. On the models with per-message effort
 * ([ModelCatalog.perMessageEffort]) an effort-only system message changes the level instead and keeps the cache:
 * `{"role": "system", "content": [], "output_config": {"effort": …}}`, with the beta [BETA]. Placed directly after a user
 * turn with new input it sets the level for the reply to that turn; anywhere else (between an assistant turn and the
 * next user turn) it takes effect from the next user turn with new input (a user turn of only tool_results doesn't
 * count); the level then holds until another one changes it. [append] places them.
 */
object Effort {
    const val LOW = "low"
    const val MEDIUM = "medium"
    const val HIGH = "high"
    const val XHIGH = "xhigh"
    const val MAX = "max"

    val LEVELS = listOf(LOW, MEDIUM, HIGH, XHIGH, MAX)

    /** The levels for long agentic work and the hardest problems: behind the panel's "more". */
    val MORE = setOf(XHIGH, MAX)

    /**
     * The per-message effort's beta. Sent only with a request that has an effort message ([inRequest]): without the beta
     * such a message is a 400 (`messages.N.output_config: Extra inputs are not permitted`), and a request without one
     * stays as before (the thinking-binding beta alone).
     */
    const val BETA = "mid-conversation-output-config-2026-07-01"

    /** An effort-only system message setting [level]. */
    fun message(level: String): JsonObject = buildJsonObject {
        put("role", "system")
        put("content", JsonArray(emptyList()))
        putJsonObject("output_config") { put("effort", level) }
    }

    /** [m]'s level when it is an effort-only system message, else null. */
    fun of(m: JsonObject): String? = if (m.role() == "system") (m["output_config"] as? JsonObject)?.str("effort") else null

    /**
     * The level in effect at the end of [messages] on a request whose top-level effort is [top]: the last effort
     * message's (each applies to the turn it was placed for and holds after it), else [top].
     */
    fun inEffect(messages: List<JsonObject>, top: String?): String? = messages.asReversed().firstNotNullOfOrNull(::of) ?: top

    /** Whether the level in effect at the end of [messages] was set by an effort message (not the top-level one). */
    fun byMessage(messages: List<JsonObject>): Boolean = messages.any { of(it) != null }

    /** Whether a request [body] has an effort message: it then needs [BETA]. */
    fun inRequest(body: JsonObject): Boolean = (body["messages"] as? JsonArray).orEmpty().any { (it as? JsonObject)?.let(::of) != null }

    /** The key of the numbers by model and effort ("claude-haiku-5-5 · high"; "· —" for a model that takes none). */
    fun key(model: String, effort: String?): String = "$model · ${effort ?: "—"}"

    /**
     * A turn's user [blocks] added to a thread's [messages] whose top-level effort is [top], at the level [want] (null:
     * whatever is in effect). The blocks become a new user message, or join the last one when it is a user's (a turn that
     * stopped before its answer); when [want] differs from the level in effect there, an effort message goes with them:
     * - before the new user message, after the last answer (the docs' own example);
     * - after it when the user message holds tool_results (a turn that hit its call limit): never between a tool_use and
     *   its results; directly after a user turn with new input it applies to that turn's reply.
     *
     * An effort message at the unanswered end (one placed for the turn that stopped) is placed again, not stacked: only
     * the unanswered end changes, never anything a reply came after (the thread's thinking stays bound, its cache holds).
     */
    fun append(messages: List<JsonObject>, blocks: List<JsonObject>, top: String?, want: String?): List<JsonObject> {
        var head = messages
        if (head.lastOrNull()?.let(::of) != null) head = head.dropLast(1)
        val last = head.lastOrNull()
        val (before, user) = if (last?.role() == "user") {
            var b = head.dropLast(1)
            if (b.lastOrNull()?.let(::of) != null) b = b.dropLast(1)
            b to user(last.blocks() + blocks)
        } else {
            head to user(blocks)
        }
        val change = want != null && want != inEffect(before, top)
        return when {
            !change -> before + user
            user.blocks().any { it.str("type") == "tool_result" } -> before + user + message(want!!)
            else -> before + message(want!!) + user
        }
    }

    private fun user(blocks: List<JsonObject>): JsonObject = buildJsonObject {
        put("role", "user")
        put("content", JsonArray(blocks))
    }
}
