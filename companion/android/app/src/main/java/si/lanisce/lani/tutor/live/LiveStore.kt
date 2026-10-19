package si.lanisce.lani.tutor.live

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import si.lanisce.lani.tutor.ModelInfo
import si.lanisce.lani.tutor.NotebookState
import si.lanisce.lani.tutor.Preset
import si.lanisce.lani.tutor.ProviderId
import si.lanisce.lani.tutor.Task
import si.lanisce.lani.tutor.ThreadState
import si.lanisce.lani.tutor.TutorStore
import java.io.File

/**
 * The live tutor's settings (the "🧑‍🏫 Učitelj · Tutor" section; never a credential: those are in their KeyVaults).
 *
 * @property on who answers: false the Claude Code session on the node (as before, the default), true the app.
 * @property provider whose models: Anthropic's with the Claude key, OpenAI's with its key or the ChatGPT sign-in,
 *   Gemini's with a Google AI Studio key, or OpenRouter's (any model) with the OpenRouter sign-in.
 * @property openai which OpenAI credential ([Billing]): null the ChatGPT plan when signed in, else the key.
 * @property models the model per task set under "advanced" (a task without one follows the preset).
 * @property efforts the effort per task set under "advanced".
 * @property dailyCap a day's spend in dollars that stops the tutor (it says so in the chat).
 * @property reactAtOnce reviews and session ends get their reaction right away instead of in the digest.
 * @property debugBaseUrl debug and qa builds only (BuildConfig.QA): the Messages API's base URL (QA's mock).
 * @property listed the models the credential in use listed at its check, and whose ([listedWith]: anthropic, or an
 *   OpenAI [Billing]).
 * @property keyRefused the credential was refused (401): the section asks for a new one.
 * @property lowCredit why money stopped the calls (the credit, a spend limit, the ChatGPT plan's usage limit), until
 *   checked again.
 * @property cursor the newest inbox id taken into the queue (the bridge's tutor inbox).
 * @property told the day each kind of note to the learner was last given (a note is given at most once a day).
 * @property learnerName the learner's name as the dashboard last gave it (the guide's paragraphs say Jan).
 * @property monthlyCap a month's spend that stops the tutor but for the learner's own chats, once they said so ([monthAllowed]).
 * @property batches what can wait goes through the Message Batches API (Anthropic, half price).
 */
@Serializable
data class LiveSettings(
    val on: Boolean = false,
    val provider: ProviderId = ProviderId.ANTHROPIC,
    val openai: String? = null,
    val preset: Preset = Preset.BALANCED,
    val models: Map<Task, String> = emptyMap(),
    val efforts: Map<Task, String> = emptyMap(),
    val threadLimit: Int? = null,
    val dailyCap: Double = 1.0,
    val reactAtOnce: Boolean = false,
    val debugBaseUrl: String? = null,
    val listed: List<ModelInfo> = emptyList(),
    val listedAt: Long = 0,
    val listedWith: String? = null,
    val keyRefused: Boolean = false,
    val lowCredit: String? = null,
    val cursor: Long = 0,
    val eveningDigest: String? = null,
    val told: Map<String, String> = emptyMap(),
    val learnerName: String? = null,
    /** A month's spend in dollars that stops all but what the learner sends, after asking (plan 5, "Budgets"). */
    val monthlyCap: Double = 20.0,
    /** The month ("2026-10") the learner said "answer anyway" at the monthly cap: their own chats run, nothing else. */
    val monthAllowed: String? = null,
    /** What can wait (the night's content, the dictionary's gaps) goes through the Message Batches API at half price. */
    val batches: Boolean = true,
    /** The ChatGPT plan's welcome was shown (OpenAI's UI guidelines: once, at the first sign-in). */
    val chatgptWelcomed: Boolean = false,
    /** The chat was told what the Gemini free tier means (Google may use what's sent): once. */
    val geminiFreeTold: Boolean = false,
) {
    val limit: Int get() = threadLimit ?: preset.threadLimit
}

/**
 * The live tutor's files (files/tutor-live): a [TutorStore] (its queue, the day's thread, a cache of the notebook, the
 * digest, the day logs) with its settings, a role-play's own thread per conversation (rp/), the inbox ids still to
 * acknowledge, and the notes waiting for the chat (said while the app was closed).
 */
class LiveStore(dir: File) : TutorStore(dir) {
    fun settings(): LiveSettings = read("settings.json", LiveSettings.serializer()) ?: LiveSettings()
    fun saveSettings(s: LiveSettings) = write("settings.json", LiveSettings.serializer(), s)

    /** A role-play's own short thread (plan 5, "The day's thread": its scenario and its turns), by its conversation. */
    fun roleplay(conversation: String): ThreadState? = read("rp/${safe(conversation)}.json", ThreadState.serializer())
    fun saveRoleplay(conversation: String, t: ThreadState?) =
        if (t == null) { File(dir, "rp/${safe(conversation)}.json").delete(); Unit } else write("rp/${safe(conversation)}.json", ThreadState.serializer(), t)

    /** The inbox ids done here, still to acknowledge on the bridge. */
    fun acks(): List<Long> = read("acks.json", ListSerializer(Long.serializer())).orEmpty()
    fun saveAcks(ids: List<Long>) = write("acks.json", ListSerializer(Long.serializer()), ids)

    /** What the tutor app said to the learner (a cap reached, a key refused) while no chat listened. */
    fun notes(): List<String> = read("notes.json", ListSerializer(String.serializer())).orEmpty()
    fun saveNotes(n: List<String>) = write("notes.json", ListSerializer(String.serializer()), n)

    /** The reports waiting for the learner's "Send to Lani's discussions?" ([PendingReport]). */
    fun pendingReports(): List<PendingReport> = read("reports-pending.json", ListSerializer(PendingReport.serializer())).orEmpty()
    fun savePendingReports(r: List<PendingReport>) = write("reports-pending.json", ListSerializer(PendingReport.serializer()), r)

    /** The notebook as the bridge last gave it (the thread's first message when the node can't be read). */
    fun cachedNotebook(): NotebookState = notebook()

    private fun safe(id: String) = id.replace(Regex("[^\\w-]"), "_").take(64)
}

/**
 * A report_problem waiting for the learner's confirmation (plan 5, "Reports: GitHub discussions": "The learner confirms
 * before anything is posted"): what would be posted, and the discussion that has it already ([foundNumber]: their vote
 * goes there), or none (a new one).
 */
@Serializable
data class PendingReport(
    val id: String,
    val kind: String,
    val title: String,
    val whatIsWrong: String,
    val suggestedFix: String? = null,
    val item: kotlinx.serialization.json.JsonObject? = null,
    val nativeCheck: Boolean = false,
    val conversationId: String = "main",
    val at: Long = 0,
    val foundId: String? = null,
    val foundNumber: Int? = null,
    val foundUrl: String? = null,
    val foundTitle: String? = null,
    val foundUpvotes: Int = 0,
    val foundUpvoted: Boolean = false,
    /** The tool's input as the model gave it (the wishlist's entry when it isn't posted). */
    val input: kotlinx.serialization.json.JsonObject,
)
