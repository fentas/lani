package si.lanisce.lani.tutor

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Phase A's tools in shadow mode (plan 5 phase 0): it watches, it never acts.
 * - `reply` goes to the debug panel ([onReply]), never into the chat, never a notification;
 * - reads go through [reads] (the paired bridge's GET routes);
 * - writes are dry runs: validated against their schema, recorded with their input, never sent anywhere;
 * - `guide`, `notebook_note`, `notebook_write`: the tutor's own, kept in the shadow's private storage ([notebook]).
 * Every input is checked against its tool's schema first; an invalid one returns the errors (`is_error`), as the
 * bridge's `fail()` does.
 */
class ShadowTools(
    private val specs: Map<String, ToolSpec>,
    private val reads: ReadSource,
    private val guide: Guide,
    private val notebook: NotebookHost,
    private val onReply: suspend (ShadowReply) -> Unit,
    private val learner: () -> String? = { null },
    private val onGuide: (String) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) : ToolRunner {

    /** The notebook as the tools change it. */
    interface NotebookHost {
        fun note(line: String)
        fun write(text: String)
        val size: Int
    }

    override suspend fun run(use: ToolUse): ToolOutcome {
        val started = clock()
        fun out(kind: ToolKind?, content: String, isError: Boolean = false, dryRun: Boolean = false, errors: List<String> = emptyList()) =
            ToolOutcome(
                content, isError,
                ToolCallRecord(
                    id = use.id, name = use.name, input = use.input, kind = kind?.label ?: "unknown",
                    result = content.take(RECORD), isError = isError, dryRun = dryRun, errors = errors, at = started, ms = clock() - started,
                ),
            )
        val spec = specs[use.name]
        val kind = PhaseA.kindOf(use.name, use.input)
        if (spec == null || kind == null) return out(kind, "unknown tool: ${use.name}", isError = true)
        if (use.badJson != null) return out(kind, "{\"INVALID_JSON\": ${JsonPrimitive(use.badJson.take(2000))}}", isError = true, dryRun = kind == ToolKind.WRITE)
        val errors = Schema.check(spec.inputSchema, use.input)
        if (errors.isNotEmpty()) {
            val said = "${use.name}: invalid input, nothing done:\n" + errors.joinToString("\n") { "- $it" }
            return out(kind, said, isError = true, dryRun = kind == ToolKind.WRITE, errors = errors)
        }
        return when (kind) {
            ToolKind.REPLY -> {
                val conv = use.input.s("conversation_id").orEmpty()
                if (!Regex("""^[\w-]{1,64}$""").matches(conv)) return out(kind, "not sent: conversation_id must be the one from the event", isError = true)
                onReply(ShadowReply(conv, use.input.s("text").orEmpty(), clock(), use.input["data"] as? JsonObject))
                out(kind, "delivered")
            }
            ToolKind.READ -> reads.read(use.name, use.input).let { out(kind, it.text, isError = it.isError) }
            ToolKind.WRITE -> out(kind, "dry run (shadow mode): ${use.name}'s input is valid; it is recorded, and nothing was written or sent.", dryRun = true)
            ToolKind.LOCAL -> when (use.name) {
                "guide" -> {
                    val id = use.input.s("topic").orEmpty()
                    val text = guide.topic(id, learner())
                    if (text == null) out(kind, "no topic \"$id\": the topics are ${guide.topics.joinToString(", ") { it.id }}", isError = true)
                    else {
                        onGuide(id)
                        out(kind, text)
                    }
                }
                "notebook_note" -> {
                    notebook.note(use.input.s("text").orEmpty().trim())
                    out(kind, "noted (the notebook has ${notebook.size} characters)")
                }
                "notebook_write" -> {
                    notebook.write(use.input.s("text").orEmpty().trim())
                    out(kind, "written: the notebook has ${notebook.size} characters; the old version is kept")
                }
                else -> out(kind, "unknown tool: ${use.name}", isError = true)
            }
        }
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        /** A result kept in the log is cut here (the model got it whole). */
        const val RECORD = 4_000
    }
}
