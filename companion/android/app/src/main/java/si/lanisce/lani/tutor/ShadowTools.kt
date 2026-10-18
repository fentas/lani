package si.lanisce.lani.tutor

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.atomic.AtomicInteger

/**
 * Phase A's tools in shadow mode (plan 5 phase 0): it watches, it never acts.
 * - `reply` goes to the debug panel ([onReply]), never into the chat, never a notification;
 * - reads go through [reads] (the paired bridge's GET routes);
 * - writes are dry runs: validated against their schema, recorded with their input (`dryRun`), never sent anywhere;
 *   the model reads the live tool's success ([DryRuns]), so it acts as it would live;
 * - `guide`, `notebook_note`, `notebook_write`: the tutor's own, kept in the shadow's private storage ([notebook]).
 * Every input is checked against its tool's schema first; an invalid one returns the errors (`is_error`), as the
 * bridge's `fail()` does. A `report_problem` about a word needs a `dictionary_lookup` earlier in the turn
 * ([ReportCheck]): one instance serves one turn (ShadowTutor makes it per turn), so it keeps its own lookups.
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
            ToolKind.READ -> reads.read(use.name, use.input).let {
                // counted when its result is back: a lookup sent with the report in one message (its result not read yet)
                // doesn't count, since the report, with no I/O, ends first
                if (use.name == "dictionary_lookup") lookups.incrementAndGet()
                out(kind, it.text, isError = it.isError)
            }
            ToolKind.WRITE -> {
                val r = if (use.name == "report_problem" && lookups.get() == 0 && ReportCheck.aboutWords(use.input)) DryRuns.Result(ReportCheck.LOOK_FIRST, isError = true)
                else DryRuns.of(use.name, use.input, learner(), clock())
                out(kind, r.text, isError = r.isError, dryRun = true, errors = if (r.isError) listOf(r.text) else emptyList())
            }
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

    /** The turn's dictionary_lookup calls whose result came back (tool calls of one message run together). */
    private val lookups = AtomicInteger()

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        /** A result kept in the log is cut here (the model got it whole). */
        const val RECORD = 4_000
    }
}

/**
 * `report_problem`'s check (a quality pass over four days: the shadow called a word the dictionary has, *vedve*, "not
 * a word" and a worksheet's key wrong, and offered a report; it looked a word up once in those days): a report that
 * says a word, a form or an answer key is wrong fails until a `dictionary_lookup` ran earlier in the turn.
 */
object ReportCheck {
    /**
     * The sources (`about_exercise.source.kind`, the fix guide's table) whose items have an answer or are a word: a review
     * card, a module's or the school course's exercise, a pack word, a challenge's, a visit's request, a word card.
     */
    private val WORD_SOURCES = setOf(
        "review", "card", "module", "course", "worksheet", "homework", "pack", "challenge", "visit_request", "word", "lookup",
        "dictionary", "lexicon", "gloss", "forms", "counting", "grammar",
    )

    /** A source's ids that name a word or an exercise. */
    private val WORD_IDS = setOf("card_id", "word_id", "word", "lemma", "form", "exercise_index")

    /** What a report about language says (its title names the item: "Fix: pack praznik-trgatev, word škarje"). */
    private val WORDS = Regex(
        """\b(words?|forms?|spell\w*|typos?|answers?|keys?|accept\w*|translat\w*|gloss\w*|meanings?|dictionar\w*|lemmas?|""" +
            """conjugat\w*|declen\w*|declin\w*|endings?|exists?|existing|grammar|dual|plurals?|genders?)\b""",
        RegexOption.IGNORE_CASE,
    )

    const val LOOK_FIRST =
        "report_problem: nothing sent. This report says a word, a form or an answer key is wrong, and no dictionary_lookup " +
            "ran in this turn. Look the word up first (dictionary_lookup): variants are common, and an exercise often " +
            "accepts more than one. Report only what the dictionary shows is wrong."

    /** Whether report [input] is a fix about a word, a form or an answer key (a wish never is). */
    fun aboutWords(input: JsonObject): Boolean {
        if ((input["kind"] as? JsonPrimitive)?.content != "fix") return false
        val item = input["item"] as? JsonObject
        if ((item?.get("kind") as? JsonPrimitive)?.content?.lowercase() in WORD_SOURCES) return true
        if (item != null && item.keys.any { it in WORD_IDS }) return true
        val said = listOf("title", "what_is_wrong", "suggested_fix").mapNotNull { (input[it] as? JsonPrimitive)?.content }
        return said.any { WORDS.containsMatchIn(it) }
    }
}
