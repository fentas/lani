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
