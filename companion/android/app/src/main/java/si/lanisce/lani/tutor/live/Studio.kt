package si.lanisce.lani.tutor.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import si.lanisce.lani.tutor.AnthropicProvider
import si.lanisce.lani.tutor.ApiError
import si.lanisce.lani.tutor.AssistantMessage
import si.lanisce.lani.tutor.Bundle
import si.lanisce.lani.tutor.CallOutcome
import si.lanisce.lani.tutor.Failures
import si.lanisce.lani.tutor.Guide
import si.lanisce.lani.tutor.LoopEnd
import si.lanisce.lani.tutor.ModelInfo
import si.lanisce.lani.tutor.NotebookState
import si.lanisce.lani.tutor.PhaseA
import si.lanisce.lani.tutor.Prefix
import si.lanisce.lani.tutor.ProviderId
import si.lanisce.lani.tutor.Redact
import si.lanisce.lani.tutor.ShadowEvent
import si.lanisce.lani.tutor.ShadowReply
import si.lanisce.lani.tutor.Step
import si.lanisce.lani.tutor.StreamResult
import si.lanisce.lani.tutor.Task
import si.lanisce.lani.tutor.ThreadFormat
import si.lanisce.lani.tutor.Threads
import si.lanisce.lani.tutor.ToolInputDelta
import si.lanisce.lani.tutor.ToolKind
import si.lanisce.lani.tutor.ToolSpec
import si.lanisce.lani.tutor.ToolUse
import si.lanisce.lani.tutor.TurnText
import si.lanisce.lani.tutor.TutorLoop
import si.lanisce.lani.tutor.TutorProvider
import si.lanisce.lani.tutor.Usage
import si.lanisce.lani.tutor.str
import si.lanisce.lani.tutor.textBlock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

// --- the job, as the bridge keeps it (app/tutor/jobs/<id>.json) ------------------------------------------------------

/** A sub-task of a job: a part with its own short thread (plan 5, "Long jobs": as Claude Code's subagents). */
@Serializable
data class StudioSubtask(
    /** The studio_subtask call's id: a resumed job finds its sub-task by it. */
    val id: String,
    val title: String,
    val brief: String,
    val status: String = StudioJob.RUNNING,
    val messages: List<JsonObject> = emptyList(),
    val calls: Int = 0,
    /** What it ended with (its last text), the main thread's tool result. */
    val result: String? = null,
    val batch: StudioBatch? = null,
)

/** One step of a job, for its log and the strip's progress: a call, a tool's call, a batch. */
@Serializable
data class StudioStep(val at: Long, val kind: String, val note: String)

/** The last input sent to a publishing tool for an item, and what came of it: published, or refused (the validator's message). */
@Serializable
data class StudioDraft(val tool: String, val item: String, val state: String, val message: String = "", val input: String = "", val at: Long = 0)

/** What a job published: the tool and the item's id. */
@Serializable
data class StudioPublished(val tool: String, val item: String, val at: Long)

/**
 * A write a job sent to the bridge (a publishing tool, a remove, a rollback, a file tool's write), recorded in the job
 * before the call ([state] [SENT]) and kept with its outcome: the tool, the item, the hash of the tool and its input
 * ([StudioIntents.hash]), the [clientId] it went with. The bridge's /tutor/tools route answers a client id once (the
 * outbox's `once`), so the same write sent again with the same client id is answered, not applied twice: a job resumed
 * after the process was killed, whose model call was made again (its answer lost with the process, or its save never
 * reached the bridge), gets its earlier publish's answer instead of a second version of the module.
 */
@Serializable
data class StudioIntent(
    val tool: String,
    val item: String,
    val hash: String,
    @SerialName("client_id") val clientId: String,
    val at: Long,
    val state: String = SENT,
) {
    companion object {
        /** Sent, the answer not known (yet): a repeat goes with the same client id. */
        const val SENT = "sent"
        const val PUBLISHED = "published"
        /** The bridge refused it (a validator, the lease): a repeat is a new try. */
        const val REFUSED = "refused"
    }
}

/** The client ids of a job's writes ([StudioIntent]). */
object StudioIntents {
    /** At most this many are kept in a job (the newest). */
    const val KEPT = 60

    /** The fingerprint of [tool] with [input] (its JSON as sent: the same object gives the same text). */
    fun hash(tool: String, input: JsonObject): String =
        java.security.MessageDigest.getInstance("SHA-256").digest("$tool\u0000$input".toByteArray()).take(16).joinToString("") { "%02x".format(it) }

    /** The client id an earlier write of the same [tool] and [hash] went with, unless the bridge refused it. */
    fun earlier(intents: List<StudioIntent>, tool: String, hash: String): StudioIntent? =
        intents.lastOrNull { it.tool == tool && it.hash == hash && it.state != StudioIntent.REFUSED }
}

/**
 * A call of a job (its [slot]: "m" the main thread, "s<i>" a sub-task) sent through the Message Batches API: the batch,
 * the job's call count it answers ([call]); [result] the message once the batch ended, [error] its error (an errored or
 * expired request).
 */
@Serializable
data class StudioBatch(
    val id: String,
    @SerialName("custom_id") val customId: String,
    val call: Int,
    @SerialName("submitted_at") val submittedAt: Long,
    @SerialName("polled_at") val polledAt: Long = 0,
    val status: String = "in_progress",
    val result: JsonObject? = null,
    val error: String? = null,
)

/**
 * A content job of the studio (docs/plans/05-built/studio.md; plan 5, "Long jobs"): [brief] what to write, for whom
 * ([origin]: the chat's request_content, or an event that asks for content: [eventKind], [eventIds] the inbox events it
 * answers, acknowledged once it ends); [urgency] now (the learner waits), soon, whenever (overnight: the Message Batches
 * API at half price on Anthropic, else while charging on Wi-Fi). Saved to the data dir after each step through the
 * bridge (PUT /tutor/jobs/<id>), so a killed app resumes it: its thread ([messages]), its sub-tasks with their own,
 * its [steps], [drafts] and what it [published].
 */
@Serializable
data class StudioJob(
    val schema: String = SCHEMA,
    val id: String,
    val title: String,
    val brief: String,
    val origin: String,
    @SerialName("event_kind") val eventKind: String? = null,
    @SerialName("event_ids") val eventIds: List<String> = emptyList(),
    val kinds: List<String> = emptyList(),
    @SerialName("conversation_id") val conversationId: String = "main",
    val urgency: String = NOW,
    val status: String = QUEUED,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("started_at") val startedAt: Long? = null,
    @SerialName("ended_at") val endedAt: Long? = null,
    val task: Task = Task.CONTENT,
    val model: String,
    val effort: String? = null,
    /** Its calls go through the Message Batches API (whenever, on Anthropic). */
    val batched: Boolean = false,
    /** The studio prefix its thread was made under (an app update changing it restarts the thread as text). */
    val prefix: String? = null,
    val messages: List<JsonObject> = emptyList(),
    val subtasks: List<StudioSubtask> = emptyList(),
    val steps: List<StudioStep> = emptyList(),
    val drafts: Map<String, StudioDraft> = emptyMap(),
    val published: List<StudioPublished> = emptyList(),
    /** The writes sent to the bridge with their client ids, recorded before each call ([StudioIntent]). */
    val intents: List<StudioIntent> = emptyList(),
    /** The main thread's calls done (a sub-task counts its own). */
    val calls: Int = 0,
    val cost: Double = 0.0,
    val batch: StudioBatch? = null,
    val replied: Boolean = false,
    val error: String? = null,
    val why: String? = null,
    @SerialName("not_before") val notBefore: Long = 0,
    val attempts: Int = 0,
) {
    /** Still to run or running (a batch waiting counts). */
    val open: Boolean get() = status == QUEUED || status == RUNNING || status == BATCH

    /** The sub-task calls and the main thread's, for the progress line. */
    val allCalls: Int get() = calls + subtasks.sumOf { it.calls }

    /** Without its threads (the strip's and the settings' copy). */
    fun light(): StudioJob = copy(messages = emptyList(), subtasks = subtasks.map { it.copy(messages = emptyList(), batch = it.batch?.copy(result = null)) }, batch = batch?.copy(result = null))

    companion object {
        const val SCHEMA = "lani.studio-job/v1"
        const val QUEUED = "queued"
        const val RUNNING = "running"
        const val BATCH = "batch"
        const val DONE = "done"
        const val FAILED = "failed"
        const val CANCELLED = "cancelled"
        const val NOW = "now"
        const val SOON = "soon"
        const val WHENEVER = "whenever"
        const val REQUEST = "request"
        const val EVENT = "event"
    }
}

/** What stops a studio run without ending its job: a call sent to a batch ([Waiting]), the job cancelled ([Cancelled]). */
sealed class StudioSignal(message: String) : Exception(message) {
    class Waiting(val batchId: String) : StudioSignal("waiting for the batch $batchId")
    /** The spend's cap stopped a sub-task: the job waits for the next day (or month). */
    class Capped : StudioSignal("the spend's cap is reached")
}

/** When the studio's background work runs (tutor/live/StudioWork): now (network), a batch to poll, the night's (charging, Wi-Fi). */
enum class StudioWake { NOW, BATCH, NIGHT }

// --- the tools and the prompt ------------------------------------------------------------------------------------------

/**
 * The live tutor's tool lists of phase C (plan 5, "The tool inventory"): the chat's (phase A's and `request_content`),
 * and the studio's (the bridge's reads and publishing tools, the file tools, `studio_subtask`), each in a fixed order
 * (a change of order is a new prefix). Anthropic's text editor is declared by its type ([TEXT_EDITOR_TYPE]); OpenAI gets
 * it as a function tool from its bundled spec ([Prefix.alternates]).
 */
object StudioSpecs {
    const val TEXT_EDITOR = "str_replace_based_edit_tool"
    const val TEXT_EDITOR_TYPE = "text_editor_20250728"
    /** The text editor's `view` cap (the bridge cuts its answers at the same). */
    const val VIEW_MAX = 16_000
    const val REQUEST = "request_content"
    const val SUBTASK = "studio_subtask"

    /** The chat's tools: phase A's 17, then request_content (it hands a brief to the studio). */
    val CHAT: List<Pair<String, ToolKind>> = PhaseA.TOOLS + (REQUEST to ToolKind.LOCAL)

    private val READS = listOf(
        "learner_state", "get_village", "dictionary_lookup", "recent_answers", "list_packs", "list_modules", "get_module", "list_grammar", "get_grammar",
        "list_cards", "get_course", "list_scenes", "get_scene", "list_stories", "get_story", "list_readings", "get_reading", "list_villagers",
        "get_villager", "get_arrival",
    )
    private val WRITES = listOf(
        "publish_module", "publish_pack", "publish_grammar", "publish_card", "publish_worksheet", "publish_scenario", "publish_scene",
        "publish_dialog_variant", "publish_story", "publish_reading", "publish_villager", "publish_arrival", "publish_gloss",
        "publish_sentence_note", "remove_dialog_variant", "remove_scene", "remove_story", "remove_reading", "remove_villager", "remove_arrival",
        "remove_card", "remove_worksheet", "rollback_module", "report_problem",
    )

    /** The studio's tools: reply and the guide, the reads, the publishing tools, the notebook's line, the file tools, sub-tasks. */
    val STUDIO: List<Pair<String, ToolKind>> = listOf("reply" to ToolKind.REPLY, "guide" to ToolKind.LOCAL) +
        READS.map { it to ToolKind.READ } + WRITES.map { it to ToolKind.WRITE } +
        listOf("notebook_note" to ToolKind.LOCAL, TEXT_EDITOR to ToolKind.WRITE, "search" to ToolKind.READ, SUBTASK to ToolKind.LOCAL)

    private val kinds = (CHAT + STUDIO).toMap()

    /** What [name] with [input] does here; null for a tool neither list has. */
    fun kindOf(name: String, input: JsonObject): ToolKind? = when (name) {
        "card" -> PhaseA.kindOf(name, input)
        TEXT_EDITOR -> if ((input["command"] as? JsonPrimitive)?.contentOrNull == "view") ToolKind.READ else ToolKind.WRITE
        else -> kinds[name]
    }

    /** The specs of [list] from the bundle (app-tools first), in its order. */
    fun load(list: List<Pair<String, ToolKind>>, read: (String) -> String? = Bundle::text): List<ToolSpec> =
        list.mapNotNull { (name, _) -> (read("app-tools/$name.json") ?: read("tools/$name.json"))?.let(ToolSpec::parse) }

    /** The request's `tools`: [specs] in [list]'s order, the text editor by its type. */
    fun definitions(list: List<Pair<String, ToolKind>>, specs: List<ToolSpec>): JsonArray {
        val byName = specs.associateBy { it.name }
        return JsonArray(
            list.mapNotNull { (n, _) ->
                if (n == TEXT_EDITOR) buildJsonObject {
                    put("type", TEXT_EDITOR_TYPE)
                    put("name", TEXT_EDITOR)
                    put("max_characters", VIEW_MAX)
                } else byName[n]?.definition()
            },
        )
    }

    /** The chat's prefix: the core prompt as phase A's ([Prefix.bundled]), with [CHAT]'s tools. */
    fun chatPrefix(specs: List<ToolSpec>, guide: Guide, read: (String) -> String? = Bundle::text): Prefix =
        Prefix(definitions(CHAT, specs), Prefix.bundled(specs, guide, read).system)

    /** The studio's prefix: its own prompt (companion/tutor/studio-prompt.md), [STUDIO]'s tools, the text editor's function form. */
    fun studioPrefix(specs: List<ToolSpec>, read: (String) -> String? = Bundle::text): Prefix {
        val text = read("studio-prompt.md").orEmpty().trim()
        val editor = specs.firstOrNull { it.name == TEXT_EDITOR }
        return Prefix(definitions(STUDIO, specs), text, editor?.let { mapOf(TEXT_EDITOR to it.definition()) }.orEmpty())
    }

    /** The item a tool's [input] is about (its spec's id, a path, an id), for the job's drafts. */
    fun itemOf(input: JsonObject): String {
        for (k in listOf("spec", "pack", "story", "scene", "reading", "villager", "page", "dialog", "arrival", "card", "worksheet", "scenario")) {
            (input[k] as? JsonObject)?.str("id")?.let { return "$k:$it" }
        }
        input.str("path")?.let { return it.trimStart('/') }
        for (k in listOf("id", "module_id", "scene_id", "story_id", "reading_id", "villager_id", "lemma", "word")) input.str(k)?.let { return it }
        return "?"
    }
}

// --- the runner --------------------------------------------------------------------------------------------------------

/**
 * The studio task (docs/plans/05-built/studio.md; plan 5, phase C): writes content for the learner on the content model
 * (Opus 5.5 at the preset's effort), with the bridge's own publishing tools (they validate on write: a refusal comes back
 * as the tool's error, and the model fixes and tries again), the file tools over the learner's data folder (checked on
 * write too), and sub-tasks with their own threads. Jobs run one at a time, each step saved through the bridge, so a
 * killed app resumes it; while the app is open here, else as WorkManager work (tutor/live/StudioWork): now with the
 * network, the night's while charging on Wi-Fi, a batch's poll. What can wait goes through the Message Batches API on
 * Anthropic, metered at half price.
 *
 * Everything it changes happens on [io] (the live tutor's one thread).
 */
class Studio(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val prefix: Prefix,
    private val specs: Map<String, ToolSpec>,
    private val guide: Guide,
    private val bridge: suspend () -> LiveBridge?,
    /** The provider of [model] (its credential). */
    private val providerFor: (String) -> TutorProvider,
    /** The Message Batches API of [model]'s credential: null when it has none (OpenAI). */
    private val batchesFor: (String) -> AnthropicBatches?,
    private val nodeTools: () -> Set<String>,
    private val fran: () -> FranLookup?,
    private val learner: () -> String?,
    private val notebook: suspend () -> NotebookState,
    /** Whether the studio may run: the live tutor on, its credential set and not refused. */
    private val enabled: () -> Boolean,
    /** Whether the spend allows another call (today's cap, the month's). */
    private val budgetOk: () -> Boolean,
    /** A call's record for the day's log and the spend ([batch]: half price). */
    private val record: (job: StudioJob, outcome: CallOutcome, thread: String, batch: Boolean) -> Double?,
    /** An API error the live tutor acts on (the key refused, the credit out). */
    private val onApiError: (StudioJob, ApiError) -> Unit,
    /** A job ended (done, failed, cancelled): its events acknowledged, a line in the day's thread, a note when it failed. */
    private val onFinished: (StudioJob) -> Unit,
    /** The report_problem of the live tutor (the discussions, asked first), when it has one. */
    private val report: (suspend (ToolUse, String) -> Pair<String, Boolean>)?,
    /** Charging on an unmetered network: the night's work may run. */
    private val nightOk: () -> Boolean,
    private val schedule: (StudioWake) -> Unit,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
    private val where: () -> String?,
    private val line: (String) -> Unit,
    private val sleep: suspend (Long) -> Unit,
    private val maxCalls: Int = MAX_CALLS,
    private val maxSubCalls: Int = MAX_SUB_CALLS,
    /** How often a waiting batch is asked while the app is open. */
    private val pollEveryMs: Long = 30_000,
) {
    /** The jobs the bridge keeps, newest first, without their threads (the strip and the settings). */
    var jobs by mutableStateOf<List<StudioJob>>(emptyList())
        private set

    private val full = mutableMapOf<String, StudioJob>()
    private val lock = Mutex()
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)
    private var worker: Job? = null
    private var running: Pair<String, Job>? = null
    private var loaded = false
    /** Jobs cancelled while they ran: their run's own ending doesn't save over it. */
    private val cancelled = mutableSetOf<String>()

    private fun now() = clock()
    private fun newId() = "j_" + UUID.randomUUID().toString().replace("-", "").take(12)

    // --- the list -----------------------------------------------------------------------------------------------------

    /** The jobs as the bridge has them (the app started, the tutor attached); true when read. */
    suspend fun load(): Boolean = withContext(io) {
        val b = bridge() ?: return@withContext false
        val list = try {
            b.jobs()
        } catch (e: LiveBridgeError) {
            line("studio: the jobs weren't read (${if (e.offline) "offline" else "HTTP ${e.code}"})")
            return@withContext false
        }
        val read = list.mapNotNull { o -> runCatching { si.lanisce.lani.tutor.tutorJson.decodeFromJsonElement(StudioJob.serializer(), o) }.getOrNull() }
        // what this process holds of a job is newer than the bridge's summary (it saves after each step)
        jobs = (read.map { j -> full[j.id]?.light() ?: j } + jobs.filter { j -> read.none { it.id == j.id } && j.open })
            .sortedByDescending { it.createdAt }
        loaded = true
        true
    }

    private fun show(j: StudioJob) {
        jobs = (listOf(j.light()) + jobs.filterNot { it.id == j.id }).sortedByDescending { it.createdAt }
    }

    /** Saves [j] here and on the bridge (after each step); a bridge not reached keeps it here until the next save. */
    private suspend fun save(j: StudioJob): StudioJob {
        val next = j.copy(updatedAt = now(), steps = j.steps.takeLast(STEPS_KEPT))
        full[next.id] = next
        show(next)
        lock.withLock {
            val b = bridge()
            if (b == null) line("studio: ${next.id} not saved (no bridge)")
            else try {
                b.saveJob(next)
            } catch (e: LiveBridgeError) {
                line("studio: ${next.id} not saved (${if (e.offline) "offline" else "HTTP ${e.code}"})")
            }
        }
        return next
    }

    private suspend fun fetch(id: String): StudioJob? = full[id] ?: runCatching { bridge()?.job(id) }.getOrNull()?.also { full[id] = it }

    // --- what comes in -------------------------------------------------------------------------------------------------

    /**
     * The chat's request_content ([input]: title, brief, kinds, urgent) on [conversation], for [model] at [effort]: a job
     * queued and started; the tool's answer. [batches]: what can wait goes through the Message Batches API.
     */
    suspend fun request(input: JsonObject, conversation: String, model: String, effort: String?, batches: Boolean): Pair<String, Boolean> = withContext(io) {
        val title = input.str("title")?.trim().orEmpty().take(80)
        val brief = input.str("brief")?.trim().orEmpty()
        if (title.isEmpty() || brief.length < 10) return@withContext "request_content: a title and a brief (what to write, for which mistakes) are needed; nothing queued" to true
        if (bridge() == null) return@withContext "request_content: the tutor's bridge isn't reached: nothing queued. Say the studio can't start now." to true
        val urgent = (input["urgent"] as? JsonPrimitive)?.booleanOrNull ?: true
        val kinds = (input["kinds"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        val urgency = if (urgent) StudioJob.NOW else StudioJob.WHENEVER
        val batched = !urgent && batches && batchesFor(model) != null
        val open = jobs.count { it.open }
        if (open >= MAX_OPEN) return@withContext "request_content: the studio has $open jobs open already: nothing queued. Tell the learner to wait for those first." to true
        val job = StudioJob(
            id = newId(), title = title, brief = brief.take(6_000), origin = StudioJob.REQUEST, kinds = kinds, conversationId = conversation,
            urgency = urgency, createdAt = now(), updatedAt = now(), model = model, effort = effort, batched = batched,
        )
        save(job)
        line("studio: ${job.id} queued from the chat ($urgency${if (batched) ", batched" else ""}): $title")
        wake()
        val how = when {
            urgent -> "it starts now; the learner sees \"✍️ Pišem …\" with its progress and can cancel it"
            batched -> "it runs overnight through the batch API, at half price"
            else -> "it runs when the phone charges on Wi-Fi"
        }
        "The studio took the job ${job.id} «$title» on ${job.model}: $how. Tell the learner in one line that it's on its way; the studio tells them itself when it's done. Don't write the content here." to false
    }

    /**
     * An event that asks for content ([e]: an arrival, dialog variants heard, stories running low, the dictionary's
     * gaps), for [task]'s [model] at [effort], at [urgency]; [batches]: through the Message Batches API when it can wait.
     */
    suspend fun event(e: ShadowEvent, task: Task, model: String, effort: String?, urgency: String, batches: Boolean, title: String) = withContext(io) {
        if (jobs.any { e.id in it.eventIds }) return@withContext
        val batched = urgency == StudioJob.WHENEVER && batches && batchesFor(model) != null
        val job = StudioJob(
            id = newId(), title = title, brief = TurnText.event(e, zone()), origin = StudioJob.EVENT, eventKind = e.kind, eventIds = listOf(e.id),
            conversationId = e.conversationId ?: "main", urgency = urgency, createdAt = now(), updatedAt = now(), task = task, model = model,
            effort = effort, batched = batched,
        )
        save(job)
        line("studio: ${job.id} queued for ${e.kind} ($urgency${if (batched) ", batched" else ""})")
        wake()
    }

    /** The learner's ✕ on a job: it stops (a batch waiting is cancelled too), its events acknowledged. */
    suspend fun cancel(id: String, why: String = "cancelled by the learner") = withContext(io) {
        val j = fetch(id) ?: jobs.firstOrNull { it.id == id } ?: return@withContext
        if (!j.open) return@withContext
        cancelled += id
        running?.takeIf { it.first == id }?.second?.cancel(CancellationException(why))
        val batchIds = listOfNotNull(j.batch?.id) + j.subtasks.mapNotNull { it.batch?.id }
        batchIds.forEach { b -> runCatching { batchesFor(j.model)?.cancel(b) } }
        val done = save(j.copy(status = StudioJob.CANCELLED, endedAt = now(), why = why, steps = j.steps + StudioStep(now(), "cancel", why)))
        line("studio: ${j.id} $why")
        onFinished(done)
    }

    /**
     * The channel goes back to Claude Code: the jobs for events end (their events go back to it with the detach), the
     * learner's own requests wait until the app's tutor is on again.
     */
    suspend fun handBack() = withContext(io) {
        for (j in jobs.filter { it.open && it.origin == StudioJob.EVENT }) cancelled += j.id
        running?.second?.cancel(CancellationException("handed back"))
        for (j in jobs.filter { it.open && it.origin == StudioJob.EVENT }) {
            val f = fetch(j.id) ?: j
            save(f.copy(status = StudioJob.CANCELLED, endedAt = now(), why = "handed back to Claude Code"))
        }
    }

    // --- running -------------------------------------------------------------------------------------------------------

    /**
     * Starts the worker when it isn't running (the app open; the background's [background] runs it too). One run at a
     * time in the process ([runLock]): the WorkManager's run may have begun as the app started (QA's restart after a
     * kill: the work waiting with the network ran before the screen was up), and two runs of the same job would each
     * make its next call, each publishing (a second version of the module). The open app's worker waits for the
     * background's run to end, then goes on.
     */
    fun wake() {
        if (worker?.isActive == true) {
            wakeUp.trySend(Unit)
            return
        }
        worker = scope.launch(io) {
            guarded {
                runLock.withLock {
                    if (!loaded) load()
                    work(foreground = true, until = Long.MAX_VALUE)
                }
            }
        }
    }

    /** Held while jobs run ([work]): never two runs at once in this process. */
    private val runLock = Mutex()

    /** [f], what it throws (but a cancellation) logged instead of ending the app's process. */
    private suspend fun guarded(f: suspend () -> Unit) {
        try {
            f()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            line("studio: the worker broke: ${Redact.of(e.message ?: e.javaClass.simpleName)}")
        }
    }

    /** The WorkManager's run while the app isn't open: the batches asked, what may run now run for at most [budgetMs]. */
    suspend fun background(budgetMs: Long) {
        withContext(io) { load() }
        // the open app's worker (or another run) has the jobs: it runs them, this waits for it at most its time
        val busy = withContext(io) { worker?.isActive == true || !runLock.tryLock() }
        if (busy) {
            withTimeoutOrNull(budgetMs) { while (worker?.isActive == true || runLock.isLocked) kotlinx.coroutines.delay(500) }
            return
        }
        try {
            withContext(io) { guarded { work(foreground = false, until = now() + budgetMs) } }
        } finally {
            runLock.unlock()
        }
    }

    /** Whether a job waits for anything at all (the background's schedule). */
    fun waiting(): StudioWake? {
        val open = jobs.filter { it.open }
        return when {
            open.any { it.urgency != StudioJob.WHENEVER && it.status != StudioJob.BATCH } -> StudioWake.NOW
            open.any { it.status == StudioJob.BATCH } -> StudioWake.BATCH
            open.isNotEmpty() -> if (open.any { it.batched }) StudioWake.BATCH else StudioWake.NIGHT
            else -> null
        }
    }

    private fun runnable(j: StudioJob, at: Long): Boolean = when {
        !j.open || j.status == StudioJob.BATCH || j.notBefore > at -> false
        j.urgency == StudioJob.WHENEVER && !j.batched -> nightOk()
        else -> true
    }

    private val rank = mapOf(StudioJob.NOW to 0, StudioJob.SOON to 1, StudioJob.WHENEVER to 2)

    private suspend fun work(foreground: Boolean, until: Long) {
        // the batches' asking by the real clock (the injected one may stand still in a test)
        var lastPoll = 0L
        while (now() < until) {
            if (!enabled()) return
            val since = (System.nanoTime() - lastPoll) / 1_000_000
            if ((lastPoll == 0L || since >= pollEveryMs || !foreground) && jobs.any { it.status == StudioJob.BATCH }) {
                poll()
                lastPoll = System.nanoTime()
            }
            val at = now()
            val next = jobs.filter { runnable(it, at) }.sortedWith(compareBy({ rank[it.urgency] ?: 3 }, { it.createdAt })).firstOrNull()
            if (next == null) {
                val open = jobs.filter { it.open }
                if (open.isEmpty() || !foreground) break
                // a batch to ask again, a retry's time, the night: wait for it, or for something new
                val soonest = open.filter { it.status != StudioJob.BATCH && it.notBefore > at }.minOfOrNull { it.notBefore - at }
                val wait = listOfNotNull(soonest, if (open.any { it.status == StudioJob.BATCH }) pollEveryMs else null).minOrNull() ?: 60_000L
                withTimeoutOrNull(wait.coerceIn(500, 60_000)) { wakeUp.receive() }
                continue
            }
            if (!budgetOk()) {
                line("studio: the spend's cap is reached: the jobs wait")
                break
            }
            runOne(next.id, until)
        }
        waiting()?.let(schedule)
    }

    /** Runs job [id] in a coroutine of its own (a ✕ cancels it), for at most until [until]. */
    private suspend fun runOne(id: String, until: Long) = coroutineScope {
        val j = launch { withTimeoutOrNull((until - now()).coerceAtLeast(1_000)) { run(id) } }
        running = id to j
        j.join()
        running = null
    }

    /** One job's run: its thread from where it was saved, until it ends, waits for a batch, the cap or a retry. */
    private suspend fun run(id: String) {
        var job = fetch(id) ?: run {
            jobs = jobs.filterNot { it.id == id }
            return
        }
        if (!job.open) return
        val base = providerFor(job.model)
        val hash = base.prefixHash(prefix)
        job = job.copy(status = StudioJob.RUNNING, startedAt = job.startedAt ?: now(), attempts = job.attempts + 1, error = null)
        val format = base.format
        if (job.messages.isEmpty()) job = job.copy(messages = listOf(first(job, format, readNotebook())), prefix = hash)
        else if (job.prefix != hash) {
            // an app update changed the studio's tools or prompt: the thread restarts as text, with what was done
            job = job.copy(messages = listOf(first(job, format, readNotebook(), restarted = true)), subtasks = job.subtasks.filter { it.status == StudioJob.DONE }, prefix = hash, batch = null)
        }
        job = save(job)
        val cell = Cell(job)
        val provider = if (job.batched) Batching(base, cell, "m") else base
        val r = try {
            loop(cell, provider, "m").run(
                prefix, job.model, job.task, job.messages.toMutableList(), callsDone = job.calls,
                save = { m -> cell.job = save(cell.job.copy(messages = m)) },
                onCall = { c -> onCall(cell, c, "m") },
                onTool = { rec -> cell.job = cell.job.copy(steps = cell.job.steps + StudioStep(now(), "tool", rec.name + if (rec.isError) " ✗" else "")) },
                effort = job.effort, maxTokens = job.task.maxTokensAt(job.effort),
            )
        } catch (e: StudioSignal.Waiting) {
            val j = save(cell.job.copy(status = StudioJob.BATCH, steps = cell.job.steps + StudioStep(now(), "batch", "sent: ${e.batchId}")))
            line("studio: ${j.id} waits for the batch ${e.batchId}")
            schedule(StudioWake.BATCH)
            return
        } catch (e: StudioSignal.Capped) {
            save(cell.job.copy(status = StudioJob.QUEUED, why = "the spend's cap is reached"))
            line("studio: ${job.id} waits: the cap")
            return
        } catch (e: CancellationException) {
            // a ✕ (cancel saved it), the WorkManager's time, the app gone: what was saved stays for the next run
            if (id !in cancelled && cell.job.status == StudioJob.RUNNING) {
                runCatching { withContext(kotlinx.coroutines.NonCancellable) { save(cell.job.copy(status = StudioJob.QUEUED)) } }
            }
            throw e
        } catch (e: Exception) {
            line("studio: ${job.id} broke: ${e.message}")
            finish(cell.job.copy(error = Redact.of(e.message ?: e.javaClass.simpleName)), StudioJob.FAILED, "the studio broke: ${Redact.of(e.message ?: e.javaClass.simpleName).take(200)}")
            return
        }
        job = cell.job
        when (val end = r.end) {
            LoopEnd.Done, LoopEnd.Limit -> finish(job, StudioJob.DONE, if (end == LoopEnd.Limit) "used its $maxCalls calls" else null)
            LoopEnd.Truncated -> finish(job, StudioJob.FAILED, "the answer was cut off twice (max_tokens)")
            is LoopEnd.Refused -> finish(job, StudioJob.FAILED, "the model declined (refusal)")
            LoopEnd.Paused -> {
                save(job.copy(status = StudioJob.QUEUED, why = "the spend's cap is reached"))
                line("studio: ${job.id} waits: the cap")
            }
            is LoopEnd.Failed -> failed(job, end.error)
        }
    }

    private class Cell(@Volatile var job: StudioJob)

    private suspend fun onCall(cell: Cell, c: CallOutcome, slot: String) {
        val j = cell.job
        val cost = record(j, c, "studio", j.batched && c.step != null) ?: 0.0
        val note = c.step?.let { "${it.model ?: j.model} ${it.stopReason}${if (slot != "m") " ($slot)" else ""}" } ?: "error: ${c.error?.kind}${if (c.retried) " (retried)" else ""}"
        var next = j.copy(cost = j.cost + cost, steps = j.steps + StudioStep(now(), "call", note))
        if (c.step != null) next = if (slot == "m") next.copy(calls = next.calls + 1) else next.copy(subtasks = next.subtasks.map { if (slotOf(it, next) == slot) it.copy(calls = it.calls + 1) else it })
        // saved with the step's answer right after (the loop's save)
        cell.job = next
    }

    private fun slotOf(st: StudioSubtask, j: StudioJob) = "s" + j.subtasks.indexOfFirst { it.id == st.id }

    private suspend fun loop(cell: Cell, provider: TutorProvider, slot: String): TutorLoop =
        TutorLoop(provider, tools(cell, slot), maxCalls = if (slot == "m") maxCalls else maxSubCalls, budgetOk = budgetOk, sleep = sleep)

    /** The tools of a job's thread ([slot] "m": the main one, which may start sub-tasks). */
    private suspend fun tools(cell: Cell, slot: String): LiveTools {
        val local = buildMap<String, suspend (ToolUse) -> Pair<String, Boolean>> {
            put(StudioSpecs.SUBTASK) { use -> if (slot != "m") "a sub-task can't start another: do it here" to true else subtask(cell, use) }
            report?.let { f -> put("report_problem") { use -> f(use, cell.job.conversationId) } }
        }
        return LiveTools(
            specs, bridge(), nodeTools(), guide, fran(),
            onReply = { cell.job = cell.job.copy(replied = true) },
            learner = learner, clock = clock, kinds = StudioSpecs::kindOf, local = local,
            onNode = { use, r -> drafted(cell, use, r) },
            clientIdFor = { use -> clientId(cell, use) },
        )
    }

    /** Whether [use] changes what the bridge keeps (a publish, a remove, a rollback, a file tool's write). */
    private fun writes(use: ToolUse): Boolean = use.name != "report_problem" && StudioSpecs.kindOf(use.name, use.input) == si.lanisce.lani.tutor.ToolKind.WRITE

    /**
     * The client id [use] goes to the bridge with. A write's: the id of an earlier write of the same tool and input in
     * this job, unless the bridge refused that one (the bridge answers it again instead of applying it twice); else the
     * call's own, recorded in the job and saved on the bridge before the call ([StudioIntent]), so a run that resumes after
     * the process was killed finds it even when the step with the call wasn't saved.
     */
    private suspend fun clientId(cell: Cell, use: ToolUse): String {
        val own = LiveBridge.clientId(use.id)
        if (!writes(use)) return own
        val hash = StudioIntents.hash(use.name, use.input)
        StudioIntents.earlier(cell.job.intents, use.name, hash)?.let { prior ->
            line("studio: ${cell.job.id}: ${use.name} ${prior.item} again with the same input: sent as ${prior.clientId} (${prior.state}), not applied twice")
            return prior.clientId
        }
        val intent = StudioIntent(use.name, StudioSpecs.itemOf(use.input), hash, own, now())
        cell.job = cell.job.copy(intents = (cell.job.intents + intent).takeLast(StudioIntents.KEPT))
        save(cell.job)
        return own
    }

    /**
     * A bridge tool's answer: a write's intent with its outcome; a publishing tool's input kept as the item's draft,
     * published or refused (its message). An answer the bridge gave again for an earlier write isn't published twice.
     */
    private fun drafted(cell: Cell, use: ToolUse, r: LiveBridge.ToolResult) {
        val hash = if (writes(use)) StudioIntents.hash(use.name, use.input) else null
        val before = hash?.let { h -> StudioIntents.earlier(cell.job.intents, use.name, h) }
        if (before != null) {
            val state = if (refused(r)) StudioIntent.REFUSED else StudioIntent.PUBLISHED
            cell.job = cell.job.copy(intents = cell.job.intents.map { if (it === before) it.copy(state = state) else it })
        }
        val writes = use.name.startsWith("publish_") || (use.name == StudioSpecs.TEXT_EDITOR && use.input.str("command") != "view")
        if (!writes) return
        val item = StudioSpecs.itemOf(use.input)
        val state = if (refused(r)) "refused" else "published"
        val d = StudioDraft(use.name, item, state, r.text.take(800), use.input.toString().take(DRAFT_MAX), now())
        var j = cell.job.copy(drafts = cell.job.drafts + ("${use.name}:$item" to d))
        val again = before?.state == StudioIntent.PUBLISHED
        if (state == "published" && !again) j = j.copy(published = j.published + StudioPublished(use.name, item, now()))
        cell.job = j
    }

    /** The bridge's ToolResult accessor for [refused] (kept beside the runner that reads it). */
    private fun refused(r: LiveBridge.ToolResult) = r.isError

    /** studio_subtask: a part of the job on its own thread, resumed by its call's id; its last text is the result. */
    private suspend fun subtask(cell: Cell, use: ToolUse): Pair<String, Boolean> {
        val title = use.input.str("title")?.trim().orEmpty().take(80)
        val brief = use.input.str("brief")?.trim().orEmpty()
        if (title.isEmpty() || brief.isEmpty()) return "studio_subtask: title and brief are needed" to true
        var j = cell.job
        val known = j.subtasks.firstOrNull { it.id == use.id }
        if (known?.status == StudioJob.DONE) return (known.result ?: "done") to false
        if (known == null && j.subtasks.size >= MAX_SUBTASKS) return "studio_subtask: at most $MAX_SUBTASKS sub-tasks a job; do the rest here" to true
        val base = providerFor(j.model)
        val st0 = known ?: StudioSubtask(id = use.id, title = title, brief = brief.take(4_000), messages = listOf(subFirst(j, title, brief, base.format)))
        if (known == null) cell.job = save(j.copy(subtasks = j.subtasks + st0, steps = j.steps + StudioStep(now(), "subtask", title)))
        j = cell.job
        val slot = "s" + j.subtasks.indexOfFirst { it.id == use.id }
        val provider = if (j.batched) Batching(base, cell, slot) else base
        fun cur() = cell.job.subtasks.first { it.id == use.id }
        fun put(st: StudioSubtask) {
            cell.job = cell.job.copy(subtasks = cell.job.subtasks.map { if (it.id == st.id) st else it })
        }
        val r = loop(cell, provider, slot).run(
            prefix, j.model, j.task, cur().messages.toMutableList(), callsDone = cur().calls,
            save = { m -> put(cur().copy(messages = m)); cell.job = save(cell.job) },
            onCall = { c -> onCall(cell, c, slot) },
            onTool = { rec -> cell.job = cell.job.copy(steps = cell.job.steps + StudioStep(now(), "tool", "$slot ${rec.name}" + if (rec.isError) " ✗" else "")) },
            effort = j.effort, maxTokens = j.task.maxTokensAt(j.effort),
        )
        val st = cur()
        val text = Threads.text(st.messages.takeLast(4)).trim().takeIf { it.isNotEmpty() }
        val (result, error) = when (val end = r.end) {
            LoopEnd.Done, LoopEnd.Limit -> (text ?: "the sub-task ended") to false
            is LoopEnd.Failed -> "the sub-task failed: ${Redact.of(end.error.message ?: end.error.kind)}" to true
            LoopEnd.Paused -> throw StudioSignal.Capped()
            else -> "the sub-task stopped (${end::class.simpleName})" to true
        }
        put(st.copy(status = if (error) StudioJob.FAILED else StudioJob.DONE, result = result.take(2_000)))
        cell.job = save(cell.job)
        return result.take(2_000) to error
    }

    private fun first(job: StudioJob, format: ThreadFormat, nb: NotebookState, restarted: Boolean = false): JsonObject {
        val head = buildString {
            append("<studio_job id=\"${job.id}\" origin=\"${job.origin}\"")
            job.eventKind?.let { append(" event_kind=\"$it\"") }
            append(" conversation_id=\"${job.conversationId}\" urgency=\"${job.urgency}\"")
            if (job.batched) append(" batched=\"true\"")
            append(">\n")
            append("Title: ${job.title}\n")
            if (job.kinds.isNotEmpty()) append("Kinds: ${job.kinds.joinToString(", ")}\n")
            append(if (job.origin == StudioJob.EVENT) "The event that asks for content:\n" else "Brief:\n")
            append(job.brief).append('\n')
            append("</studio_job>")
            if (job.batched) append("\nThis job runs through the Message Batches API: each step waits for its batch, so keep the steps few and start no sub-tasks side by side.")
            if (restarted) {
                append("\nThe app was updated during this job, so its thread started again. Done so far:")
                if (job.published.isEmpty()) append(" nothing published yet.")
                else job.published.forEach { append("\n- ${it.tool} ${it.item}") }
                job.subtasks.filter { it.status == StudioJob.DONE }.forEach { append("\n- sub-task «${it.title}»: ${it.result?.take(300)}") }
                append("\nGo on from there.")
            }
        }
        return format.user(listOf(textBlock(TurnText.notebook(nb)), textBlock(TurnText.now(now(), zone(), where())), textBlock(head)))
    }

    private fun subFirst(job: StudioJob, title: String, brief: String, format: ThreadFormat): JsonObject = format.user(
        listOf(
            textBlock(TurnText.now(now(), zone(), null)),
            textBlock("<studio_subtask job=\"${job.id}\" conversation_id=\"${job.conversationId}\">\nThe job: ${job.title}\nYour part: $title\n$brief\n</studio_subtask>\nDo this part only, publish it, and end with one or two lines on what you published (no reply to the learner: the job's main thread tells them)."),
        ),
    )

    private suspend fun finish(job: StudioJob, status: String, why: String?) {
        val done = save(job.copy(status = status, endedAt = now(), why = why ?: job.why, steps = job.steps + StudioStep(now(), status, why ?: "")))
        line("studio: ${done.id} $status${why?.let { " ($it)" } ?: ""}: ${done.published.size} published, ${done.allCalls} call(s), \$${"%.4f".format(done.cost)}")
        onFinished(done)
    }

    private suspend fun failed(job: StudioJob, e: ApiError) {
        onApiError(job, e)
        val error = Redact.of(e.message ?: e.kind)
        val retry = Failures.retryAt(e, job.attempts, now())
        when {
            e is ApiError.Auth || e is ApiError.LowCredit -> save(job.copy(status = StudioJob.QUEUED, error = error))
            retry != null -> save(job.copy(status = StudioJob.QUEUED, error = error, notBefore = retry))
            else -> finish(job.copy(error = error), StudioJob.FAILED, error.take(200))
        }
    }

    /** The notebook a job's first message carries (read when the job starts; empty when it can't be read). */
    private suspend fun readNotebook(): NotebookState = try {
        notebook()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        NotebookState()
    }

    // --- batches ---------------------------------------------------------------------------------------------------------

    /**
     * A provider whose calls go through the Message Batches API (a job that can wait, on Anthropic): the slot's call is
     * sent as a batch of one and the run stops ([StudioSignal.Waiting]); once [poll] found its result, the resumed run's
     * same call gets it as its step.
     */
    private inner class Batching(private val base: TutorProvider, private val cell: Cell, private val slot: String) : TutorProvider by base {
        private fun batchOf(j: StudioJob): StudioBatch? = if (slot == "m") j.batch else j.subtasks.getOrNull(slot.drop(1).toIntOrNull() ?: -1)?.batch
        private fun callsOf(j: StudioJob): Int = if (slot == "m") j.calls else j.subtasks.getOrNull(slot.drop(1).toIntOrNull() ?: -1)?.calls ?: 0
        private fun put(j: StudioJob, b: StudioBatch?): StudioJob =
            if (slot == "m") j.copy(batch = b) else j.copy(subtasks = j.subtasks.mapIndexed { i, st -> if ("s$i" == slot) st.copy(batch = b) else st })

        override suspend fun call(body: JsonObject, onDelta: () -> Unit): Step {
            val j = cell.job
            val calls = callsOf(j)
            val b = batchOf(j)
            if (b != null && b.call == calls) {
                b.result?.let { m ->
                    cell.job = put(j, null)
                    return stepOf(m)
                }
                b.error?.let { why ->
                    cell.job = put(j, null)
                    // expired, canceled or a server's error: sent again; a request the API refused: the loop's error
                    throw if (why.startsWith("invalid_request")) ApiError.BadRequest(400, "batch: $why") else ApiError.Overloaded(0, "batch: $why")
                }
                throw StudioSignal.Waiting(b.id)
            }
            val api = batchesFor(j.model) ?: return base.call(body, onDelta)
            val custom = "${j.id}-$slot-$calls".take(64)
            val sent = api.create(listOf(custom to body))
            cell.job = save(put(j, StudioBatch(sent.id, custom, calls, now(), now(), sent.status)))
            throw StudioSignal.Waiting(sent.id)
        }

        override suspend fun callTools(body: JsonObject, onDelta: () -> Unit, onTool: (ToolInputDelta) -> Unit): Step = call(body, onDelta)
    }

    /** Asks the batches the waiting jobs sent: an ended one's result into its job, which runs again. */
    suspend fun poll() {
        for (light in jobs.filter { it.status == StudioJob.BATCH }) {
            val j = fetch(light.id) ?: continue
            val api = batchesFor(j.model) ?: continue
            var next = j
            var changed = false
            val slots = listOfNotNull(j.batch?.let { "m" to it }) + j.subtasks.mapIndexedNotNull { i, st -> st.batch?.let { "s$i" to it } }
            for ((slot, b) in slots) {
                if (b.result != null || b.error != null) continue
                val batch = try {
                    api.get(b.id)
                } catch (e: ApiError) {
                    line("studio: the batch ${b.id} wasn't asked (${e.kind})")
                    continue
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    line("studio: the batch ${b.id} wasn't asked (${e.message})")
                    continue
                }
                var nb = b.copy(polledAt = now(), status = batch.status)
                if (batch.status == "ended") {
                    val result = runCatching { api.results(batch) }.getOrElse { line("studio: the batch ${b.id}'s results weren't read (${it.message})"); null }
                    if (result != null) {
                        val mine = result.firstOrNull { it.customId == b.customId }
                        nb = when {
                            mine == null -> nb.copy(error = "expired: no result")
                            mine.type == "succeeded" && mine.message != null -> nb.copy(result = mine.message)
                            mine.type == "errored" -> nb.copy(error = errorType(mine.error))
                            else -> nb.copy(error = mine.type)
                        }
                    }
                }
                next = if (slot == "m") next.copy(batch = nb) else next.copy(subtasks = next.subtasks.mapIndexed { i, st -> if ("s$i" == slot) st.copy(batch = nb) else st })
                changed = true
            }
            if (!changed) continue
            val ready = (listOfNotNull(next.batch) + next.subtasks.mapNotNull { it.batch }).all { it.result != null || it.error != null }
            if (ready) next = next.copy(status = StudioJob.QUEUED, steps = next.steps + StudioStep(now(), "batch", "ended"))
            save(next)
            if (ready) line("studio: ${next.id}'s batch ended: it goes on")
        }
    }

    private fun errorType(e: JsonObject?): String {
        val inner = (e?.get("error") as? JsonObject) ?: e
        return "${inner?.str("type") ?: "error"}: ${inner?.str("message").orEmpty().take(300)}"
    }

    // --- the tests' ----------------------------------------------------------------------------------------------------

    /** Waits until the worker stopped (the tests'). */
    internal suspend fun awaitIdle(timeoutMs: Long = 10_000) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            withContext(io) {}
            if (worker?.isActive != true) return
            kotlinx.coroutines.delay(5)
        }
        error("the studio didn't get idle")
    }

    /** Job [id] as this process holds it (the tests'). */
    internal fun fullJob(id: String): StudioJob? = full[id]

    companion object {
        const val MAX_CALLS = 24
        const val MAX_SUB_CALLS = 12
        const val MAX_SUBTASKS = 6
        const val MAX_OPEN = 5
        const val STEPS_KEPT = 120
        const val DRAFT_MAX = 30_000

        /** A message of the Messages API (a batch's result) as the loop's step. */
        fun stepOf(m: JsonObject): Step {
            val content = (m["content"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val usage = Usage.merge(Usage(), m["usage"] as? JsonObject)
            val msg = AssistantMessage(m.str("id"), m.str("model"), content, m.str("stop_reason"), m["stop_details"] as? JsonObject, usage)
            return AnthropicProvider.step(StreamResult(msg, null, 0))
        }
    }
}
