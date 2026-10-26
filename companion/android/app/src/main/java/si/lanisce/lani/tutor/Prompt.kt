package si.lanisce.lani.tutor

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** One topic of the guide (companion/tutor/guide/index.json). */
data class GuideTopic(val id: String, val title: String, val summary: String)

/**
 * The guide on demand (plan 5, "The guide on demand"): the channel guide's paragraphs and the skills, split into
 * topics at build time from the bridge's own text (companion/tutor/guide, by the bridge's tutor-export script), one
 * returned per `guide(topic)` call. The paragraphs are written for Jan; another learner's name replaces it, as the
 * bridge does.
 */
class Guide(private val read: (String) -> String? = Bundle::text) {
    val topics: List<GuideTopic> by lazy {
        val raw = read("guide/index.json") ?: return@lazy emptyList()
        runCatching {
            ((tutorJson.parseToJsonElement(raw) as JsonObject)["topics"] as JsonArray).mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                GuideTopic(o.str("id") ?: return@mapNotNull null, o.str("title").orEmpty(), o.str("summary").orEmpty())
            }
        }.getOrDefault(emptyList())
    }

    /** Topic [id]'s text for [learner] (null: the paragraphs' own name), or null when there is no such topic. */
    fun topic(id: String, learner: String? = null): String? {
        if (topics.none { it.id == id }) return null
        val text = read("guide/$id.md") ?: return null
        return if (learner.isNullOrBlank() || learner == "Jan") text else text.replace(Regex("""\bJan\b"""), learner)
    }
}

/**
 * The stable prefix (plan 5, "The stable prefix"): the tools in phase A's order, then the core prompt (who the tutor
 * is, the rules, the event kinds in brief, the guide's topics), with one 1-hour cache breakpoint at its end. Nothing
 * about the learner and nothing that changes with time: that goes in the messages.
 */
class Prefix(
    val tools: JsonArray,
    val system: String,
    /**
     * A tool the Messages API knows by its type (Anthropic's text editor, `text_editor_20250728`: no input schema of its
     * own in [tools]) as a provider without it gets the tool: its name, description and input schema (OpenAI's function
     * tool), by name. Empty for phase A's prefix.
     */
    val alternates: Map<String, JsonObject> = emptyMap(),
) {
    /**
     * The prefix's fingerprint: a thread made with another (an app update changed a tool or the prompt) can't go on, since
     * its thinking blocks are bound to the prefix they were made under: it restarts.
     */
    val hash: String by lazy {
        java.security.MessageDigest.getInstance("SHA-256").digest((tools.toString() + "\u0000" + system).toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
    }

    /** The request's `system`: one block, its end the 1-hour breakpoint (tools render before it: both are cached). */
    fun systemBlocks(): JsonArray = JsonArray(
        listOf(
            buildJsonObject {
                put("type", "text")
                put("text", system)
                putJsonObject("cache_control") {
                    put("type", "ephemeral")
                    put("ttl", "1h")
                }
            },
        ),
    )

    /**
     * A request on [model] for [task] over [messages]: streaming, `tool_choice: auto` (forced tool use is a 400 on
     * Opus 5.5 and Sonnet 5.5), adaptive thinking with the top-level [effort] where the model takes it (set always:
     * Sonnet 5.5's default is high; and fixed per thread, since a change clears the messages' cache: a thread's turn at
     * another level carries an effort message in [messages] instead, [Effort]), automatic caching on the last block
     * (5 minutes, after the prefix's 1-hour breakpoint). No temperature, top_p or top_k (the 5.5 models refuse them).
     */
    fun request(model: String, task: Task, messages: List<JsonObject>, maxTokens: Int = task.maxTokens, effort: String? = task.effort): JsonObject = buildJsonObject {
        put("model", model)
        put("max_tokens", maxTokens)
        put("stream", true)
        put("system", systemBlocks())
        put("tools", tools)
        putJsonObject("tool_choice") { put("type", "auto") }
        if (ModelCatalog.adaptive(model)) {
            putJsonObject("thinking") { put("type", "adaptive") }
            if (effort != null) putJsonObject("output_config") { put("effort", effort) }
        }
        putJsonObject("cache_control") { put("type", "ephemeral") }
        put("messages", JsonArray(messages))
    }

    companion object {
        /**
         * The bundled prefix: the core prompt (companion/tutor/core-prompt.md), its HTML comments left out (they are for
         * whoever edits it: where a rule came from; never sent). The guide's topics are listed once, in the `guide`
         * tool's description (companion/tutor/tools/guide.json, made with the topics); a guide without that tool gets the
         * list here.
         */
        fun bundled(specs: List<ToolSpec>, guide: Guide, read: (String) -> String? = Bundle::text): Prefix {
            val core = uncommented(read("core-prompt.md").orEmpty())
            val listed = specs.any { it.name == "guide" && guide.topics.all { t -> it.description.contains(t.id) } }
            val topics = guide.topics.joinToString("\n") { "- `${it.id}`: ${it.title}. ${it.summary}" }
            val text = if (listed || topics.isEmpty()) core else "$core\n\n## The guide's topics (`guide(topic)`)\n\n$topics"
            return Prefix(PhaseA.definitions(specs), text)
        }

        /** [text] without its HTML comments (`<!-- … -->`, each with the line break right after it), trimmed. */
        fun uncommented(text: String): String = text.replace(Regex("""<!--[\s\S]*?-->[ \t]*(\r?\n)?"""), "").trim()
    }
}
