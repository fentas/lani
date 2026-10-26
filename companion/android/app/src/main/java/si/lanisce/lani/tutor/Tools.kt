package si.lanisce.lani.tutor

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A tool as the Messages API gets it: one JSON file per tool (plan 5, "One source of truth"): the bridge's own,
 * exported from its MCP tools (companion/tutor/tools, by companion/bridge/src/cli/tutor-export.ts), and the app's new
 * ones (companion/tutor/app-tools), bundled under tutor/.
 */
data class ToolSpec(val name: String, val description: String, val inputSchema: JsonObject) {
    /** The definition sent: name, description, input_schema; streamed eagerly (the client validates every input). */
    fun definition(): JsonObject = buildJsonObject {
        put("name", name)
        put("description", description)
        put("input_schema", inputSchema)
        put("eager_input_streaming", true)
    }

    companion object {
        fun parse(raw: String): ToolSpec? = runCatching {
            val o = tutorJson.parseToJsonElement(raw) as JsonObject
            ToolSpec(o.str("name")!!, o.str("description").orEmpty(), (o["input_schema"] ?: o["inputSchema"]) as JsonObject)
        }.getOrNull()
    }
}

/** What a tool does in shadow mode. */
enum class ToolKind(val label: String) {
    /** The tutor's only way to speak: in shadow mode into the panel, never the chat. */
    REPLY("reply"),
    /** Reads the learner's data: through the paired bridge's GET routes (or what the shadow saw itself). */
    READ("read"),
    /** Changes the learner's data or content: validated and recorded as a dry run, never sent. */
    WRITE("write"),
    /** The tutor's own: the guide, its notebook (the shadow's private storage). */
    LOCAL("local"),
}

/**
 * Phase A's tools (plan 5, "New tools the hosted tutor needs": the 10 ported and the 7 new), in the fixed order the
 * prefix sends them (a change of order is a new prefix: the cache starts again). `card` reads with find, show and
 * overlaps and writes with the rest.
 */
object PhaseA {
    val TOOLS: List<Pair<String, ToolKind>> = listOf(
        "reply" to ToolKind.REPLY,
        "guide" to ToolKind.LOCAL,
        "learner_state" to ToolKind.READ,
        "get_village" to ToolKind.READ,
        "recent_answers" to ToolKind.READ,
        "list_packs" to ToolKind.READ,
        "list_grammar" to ToolKind.READ,
        "get_grammar" to ToolKind.READ,
        "dictionary_lookup" to ToolKind.READ,
        "card" to ToolKind.READ,
        "notebook_note" to ToolKind.LOCAL,
        "notebook_write" to ToolKind.LOCAL,
        "publish_gloss" to ToolKind.WRITE,
        "publish_sentence_note" to ToolKind.WRITE,
        "set_rhythm" to ToolKind.WRITE,
        "record_session" to ToolKind.WRITE,
        "report_problem" to ToolKind.WRITE,
    )

    /** `card`'s actions that only read. */
    val CARD_READS = setOf("find", "show", "overlaps")

    private val kinds = TOOLS.toMap()

    /** What [name] with [input] does; null for a tool phase A doesn't have. */
    fun kindOf(name: String, input: JsonObject): ToolKind? = when (name) {
        "card" -> if ((input["action"] as? JsonPrimitive)?.content in CARD_READS) ToolKind.READ else ToolKind.WRITE
        else -> kinds[name]
    }

    /** The specs from the bundle (app-tools first: an app tool of the same name as a bridge one is the app's). */
    fun load(read: (String) -> String? = Bundle::text): List<ToolSpec> = TOOLS.mapNotNull { (name, _) ->
        (read("app-tools/$name.json") ?: read("tools/$name.json"))?.let(ToolSpec::parse)
    }

    /** The request's `tools`: [specs] in phase A's order. */
    fun definitions(specs: List<ToolSpec>): JsonArray {
        val byName = specs.associateBy { it.name }
        return JsonArray(TOOLS.mapNotNull { (n, _) -> byName[n]?.definition() })
    }
}

/**
 * What a `reply` may carry and when it may go, the same for the shadow ([ShadowTools]) and the live tutor
 * (live/LiveTools): the matrix (2026-10-19 to 10-22) found seven lanes putting `{"score": …}` on chat and digest
 * replies, and Sonnet at `low` skipping `record_session` on two session ends.
 */
object ReplyData {
    /**
     * The kinds whose reply carries a score (their guide asks for one): an exercise's answer, a town's check, question and
     * answer, a market's debrief (a role-play's end), the family's challenges. Nowhere else: not on a chat, a digest, a
     * session's end or the rhythm.
     */
    val SCORED = setOf("answer", "town_check", "town_asked", "town_answer", "roleplay_end", "partner_challenge", "partner_answer")

    /** What a score goes with: its severity (an answer's feedback). */
    private val WITH_SCORE = listOf("score", "severity")

    /**
     * The kind of the event a reply to [conversation] answers, of a turn's [events]: the event of that conversation, else
     * (a turn of one event, the model naming another conversation) that event's; null when none fits (a digest's "main").
     */
    fun kindOf(events: List<ShadowEvent>, conversation: String): String? =
        events.lastOrNull { it.conversationId == conversation }?.kind ?: events.singleOrNull()?.kind

    /**
     * [data] for a reply to an event of [kind] (null: none of the turn's), when [known] (the caller knows the turn's
     * events): `score` (and its `severity`) dropped unless [kind] is [SCORED]. The data and what was dropped (for the
     * tool's answer: the model learns it), or null.
     */
    fun clean(data: JsonObject?, kind: String?, known: Boolean = true): Pair<JsonObject?, String?> {
        if (data == null || !known || kind in SCORED || WITH_SCORE.none { it in data }) return data to null
        val kept = JsonObject(data - WITH_SCORE.toSet())
        return kept.takeIf { it.isNotEmpty() } to "data.score dropped: a score goes only on the reply to an answer (or a town's check, a debrief)"
    }

    /** How many session ends of [events] a turn must persist (`record_session`) before it replies. */
    fun persistFirst(events: List<ShadowEvent>): Int = events.count { it.kind == "session_end" }

    /**
     * Why a reply can't go yet: [needed] session ends to persist and [recorded] `record_session` calls so far (a refused
     * one counts as tried: the reply then says why); null when it may.
     */
    fun persistFirst(needed: Int, recorded: Int): String? =
        if (recorded >= needed) null
        else "not sent: this turn has ${if (needed == 1) "a session's end" else "$needed session ends"} to persist first. " +
            "Call record_session with the session's report (the session-end topic of the guide), then reply."
}
