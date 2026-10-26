package si.lanisce.lani.tutor.live

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import si.lanisce.lani.tutor.Guide
import si.lanisce.lani.tutor.PhaseA
import si.lanisce.lani.tutor.ReplyData
import si.lanisce.lani.tutor.ReportCheck
import si.lanisce.lani.tutor.Schema
import si.lanisce.lani.tutor.ShadowReply
import si.lanisce.lani.tutor.ToolCallRecord
import si.lanisce.lani.tutor.ToolKind
import si.lanisce.lani.tutor.ToolOutcome
import si.lanisce.lani.tutor.ToolRunner
import si.lanisce.lani.tutor.ToolSpec
import si.lanisce.lani.tutor.ToolUse
import java.util.concurrent.atomic.AtomicInteger

/**
 * Phase A's tools for real (plan 5, "The tool inventory"): the bridge's own through its tutor session ([LiveBridge.tool]:
 * the very handlers Claude Code's calls use, `reply` among them, so a reply reaches the chat as Claude Code's do), and
 * the app's own:
 * - `guide(topic)`: one topic of the bundled guide;
 * - `dictionary_lookup`: the app's dictionary on the node (GET /lookup) and, for Slovene, fran.si fetched by the app
 *   ([FranLookup]), both as marked untrusted data;
 * - `report_problem`: a fix about a word needs a `dictionary_lookup` earlier in the turn ([ReportCheck]), then the
 *   bridge's (the wishlist until the discussions have a GitHub token).
 *
 * Every input is checked against its tool's schema first; an invalid one returns the errors (`is_error`), as the
 * bridge's `fail()` does. A tool the node's bridge doesn't have says so. One instance serves one turn.
 *
 * A `reply` goes with `data.score` only where the event asks for one ([ReplyData.clean]: the matrix found scores on
 * chats and digests), and in a turn with a session's end only once `record_session` ran ([persist]).
 */
class LiveTools(
    private val specs: Map<String, ToolSpec>,
    private val bridge: LiveBridge?,
    /** The tools the node's session listed (its channel's and its own); empty: not known yet, every call is tried. */
    private val nodeTools: Set<String>,
    private val guide: Guide,
    private val fran: FranLookup?,
    private val onReply: suspend (ShadowReply) -> Unit,
    private val learner: () -> String? = { null },
    private val onGuide: (String) -> Unit = {},
    /** A notebook tool changed the notebook on the node: the cache follows. */
    private val onNotebook: suspend () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    private val max: Int = 16_000,
    /** What a tool does, by its name and input (phase A's; the studio's: [StudioSpecs.kindOf]); null: not a tool here. */
    private val kinds: (String, JsonObject) -> ToolKind? = PhaseA::kindOf,
    /**
     * The tools the app carries out itself besides the guide and the dictionary (request_content, studio_subtask, a
     * report_problem posted to the discussions): their result's text and whether it is an error.
     */
    private val local: Map<String, suspend (ToolUse) -> Pair<String, Boolean>> = emptyMap(),
    /** A bridge tool's call came back (the studio keeps its drafts and what it published). */
    private val onNode: suspend (ToolUse, LiveBridge.ToolResult) -> Unit = { _, _ -> },
    /**
     * The client id a bridge tool's call goes with (the bridge answers one once): the call's own id ([LiveBridge.clientId]);
     * the studio's writes reuse an earlier one for the same write ([Studio]'s intents).
     */
    private val clientIdFor: suspend (ToolUse) -> String = { LiveBridge.clientId(it.id) },
    /** The kind of the event a reply's conversation belongs to ([ReplyData.kindOf]); null: not known (the studio's). */
    private val replyKind: ((String) -> String?)? = null,
    /** The sessions' ends the turn must persist before it replies ([ReplyData.persistFirst]: 0 for none). */
    private val persist: Int = 0,
) : ToolRunner {
    /** The turn's dictionary_lookup calls whose result came back. */
    private val lookups = AtomicInteger()

    /** The turn's record_session calls (tried: one the node refused counts, the reply then says why). */
    private val recorded = AtomicInteger()

    override suspend fun run(use: ToolUse): ToolOutcome {
        val started = clock()
        fun out(kind: ToolKind?, content: String, isError: Boolean = false, errors: List<String> = emptyList()): ToolOutcome {
            val text = if (content.length > max) content.take(max) + "\n… (cut at $max characters)" else content
            return ToolOutcome(
                text, isError,
                ToolCallRecord(
                    id = use.id, name = use.name, input = use.input, kind = kind?.label ?: "unknown",
                    result = text.take(RECORD), isError = isError, errors = errors, at = started, ms = clock() - started,
                ),
            )
        }
        val spec = specs[use.name]
        val kind = kinds(use.name, use.input)
        if (spec == null || kind == null) return out(kind, "unknown tool: ${use.name}", isError = true)
        if (use.badJson != null) return out(kind, "{\"INVALID_JSON\": ${JsonPrimitive(use.badJson.take(2000))}}", isError = true)
        val errors = Schema.check(spec.inputSchema, use.input)
        if (errors.isNotEmpty()) return out(kind, "${use.name}: invalid input, nothing done:\n" + errors.joinToString("\n") { "- $it" }, isError = true, errors = errors)
        return try {
            when (use.name) {
                "guide" -> {
                    val id = use.input.s("topic").orEmpty()
                    val text = guide.topic(id, learner())
                    if (text == null) out(kind, "no topic \"$id\": the topics are ${guide.topics.joinToString(", ") { it.id }}", isError = true)
                    else {
                        onGuide(id)
                        out(kind, text)
                    }
                }
                "dictionary_lookup" -> lookup(use.input).let { (text, error) ->
                    lookups.incrementAndGet()
                    out(kind, text, isError = error)
                }
                "report_problem" -> if (lookups.get() == 0 && ReportCheck.aboutWords(use.input)) out(kind, ReportCheck.LOOK_FIRST, isError = true)
                    else local[use.name]?.let { f -> f(use).let { (text, error) -> out(kind, text, isError = error) } } ?: node(use, kind, ::out)
                "reply" -> {
                    ReplyData.persistFirst(persist, recorded.get())?.let { return out(kind, it, isError = true) }
                    val (data, dropped) = ReplyData.clean(use.input["data"] as? JsonObject, replyKind?.invoke(use.input.s("conversation_id").orEmpty()), replyKind != null)
                    val sent = if (dropped == null) use else use.copy(input = JsonObject((use.input - "data") + (data?.let { mapOf("data" to it) } ?: emptyMap())))
                    node(sent, kind) { k, text, error, errors -> out(k, if (dropped != null && !error) "$text ($dropped)" else text, error, errors) }
                }
                "record_session" -> {
                    recorded.incrementAndGet()
                    local[use.name]?.let { f -> f(use).let { (text, error) -> out(kind, text, isError = error) } } ?: node(use, kind, ::out)
                }
                else -> local[use.name]?.let { f -> f(use).let { (text, error) -> out(kind, text, isError = error) } } ?: node(use, kind, ::out)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: StudioSignal) {
            throw e
        } catch (e: LiveBridgeError) {
            out(kind, "${use.name} didn't reach the node (${if (e.offline) "no connection" else "HTTP ${e.code}"}): nothing done. ${if (e.offline) "Try again later." else ""}".trim(), isError = true)
        } catch (e: Exception) {
            out(kind, "${use.name} failed: ${e.message ?: e.javaClass.simpleName}", isError = true)
        }
    }

    /** A tool of the node's bridge, by its tutor session ([LiveBridge.tool]), once per call id. */
    private suspend fun node(use: ToolUse, kind: ToolKind, out: (ToolKind?, String, Boolean, List<String>) -> ToolOutcome): ToolOutcome {
        val b = bridge ?: return out(kind, "not connected to the tutor's node: ${use.name} didn't run", true, emptyList())
        if (nodeTools.isNotEmpty() && use.name !in nodeTools) return out(kind, "the node's bridge has no ${use.name} tool (it needs an update): nothing done", true, emptyList())
        val r = b.tool(use.name, use.input, clientIdFor(use))
        runCatching { onNode(use, r) }
        if (!r.isError) when (use.name) {
            "reply" -> onReply(ShadowReply(use.input.s("conversation_id").orEmpty(), use.input.s("text").orEmpty(), clock(), use.input["data"] as? JsonObject))
            "notebook_note", "notebook_write" -> runCatching { onNotebook() }
        }
        return out(kind, r.text.ifBlank { if (r.isError) "${use.name} failed" else "done" }, r.isError, emptyList())
    }

    /** The app's dictionary on the node, and fran.si for Slovene, each as marked untrusted data. */
    private suspend fun lookup(input: JsonObject): Pair<String, Boolean> {
        val word = input.s("word")?.trim().orEmpty()
        if (word.isEmpty()) return "word: required" to true
        val line = input.s("line")
        val own = runCatching { bridge?.lookup(word, line) }.getOrNull()
        val outside = runCatching { fran?.look(word) }.getOrNull()
        if (own == null && outside == null) return "dictionary_lookup: no dictionary could be reached for \"$word\" (the node's lookup and fran.si): say so, and don't call it a mistake" to true
        val parts = buildList {
            own?.let { add("<untrusted_dictionary_content source=\"Lani's dictionary\">\n${it.take(6_000)}\n</untrusted_dictionary_content>") }
            outside?.let { add("<untrusted_dictionary_content source=\"fran.si\">\n$it\n</untrusted_dictionary_content>") }
        }
        return parts.joinToString("\n\n") to false
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        /** A result kept in the log is cut here (the model got it whole). */
        const val RECORD = 4_000
    }
}
