package si.lanisce.lani.tutor

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * What the shadow tutor keeps (plan 5 phase 0, docs/plans/05-phase-0.md check 6): the learner's events it saw, its
 * turns with their calls, tool calls and replies, the real tutor's replies matched to them, and the settings. All
 * @Serializable (no reflection: R8's rules for kotlinx.serialization cover them).
 */

/** How an event becomes work (plan 5, "Events to turns"). */
@Serializable
enum class Handling {
    /** A turn of its own right away: a chat, an answer (and with "react at once" reviews and session ends). */
    AT_ONCE,
    /** Into the day's digest: reviews, words, readings, session ends. */
    DIGEST,
    /** Seen, not run: a role-play (phase B), an arrival (phase C). */
    RECORDED,
}

/**
 * An event the app sent the real tutor (the outbox's write, delivered), as the shadow saw it: [kind] the channel's
 * (chat, answer, session_end, review_done, words_added, words_learned, reading_done, dialog_words, level_up,
 * villager_arrived, roleplay, roleplay_end), [path] the bridge route it went to, [text] and [data] as sent.
 */
@Serializable
data class ShadowEvent(
    val id: String,
    val kind: String,
    val at: Long,
    val path: String,
    val conversationId: String? = null,
    val text: String = "",
    val data: JsonObject? = null,
    val handling: Handling = Handling.DIGEST,
    /** Why it's only recorded, or why its digest had nothing to say. */
    val note: String? = null,
)

/** A reply: the shadow's (through its `reply` tool, shown only in the panel) or the real tutor's (its SSE reply). */
@Serializable
data class ShadowReply(
    val conversationId: String,
    val text: String,
    val at: Long,
    val data: JsonObject? = null,
)

/** The real tutor's reply, matched to the shadow's event it answers ([eventId], null when none matched). */
@Serializable
data class RealReply(
    val reply: ShadowReply,
    val eventId: String? = null,
)

/** One tool call of a turn: its input as given, what it did and returned (cut short), and for a write its dry run. */
@Serializable
data class ToolCallRecord(
    val id: String,
    val name: String,
    val input: JsonObject,
    /** reply, read, write, local, unknown. */
    val kind: String,
    val result: String,
    val isError: Boolean = false,
    /** A write: validated and recorded, never sent anywhere. */
    val dryRun: Boolean = false,
    /** The input's schema errors (empty: valid). */
    val errors: List<String> = emptyList(),
    val at: Long = 0,
    val ms: Long = 0,
)

/**
 * One call to the Messages API (plan 5, "What the prototype measures"): the model, what for, the tokens by kind,
 * latency, the price; [threadAfter] the prompt's size after it (the restart's measure).
 */
@Serializable
data class CallRecord(
    val id: String,
    val turnId: String,
    val model: String,
    val eventKind: String,
    val task: Task,
    val at: Long,
    val usage: Usage,
    val firstTokenMs: Long? = null,
    val totalMs: Long = 0,
    val stopReason: String? = null,
    val cost: Double? = null,
    val threadAfter: Long = 0,
    /** day, stateless, also, closing. */
    val thread: String = "day",
    val error: String? = null,
    /** What the API reported it changed of the history (input_transformations): an append-only thread has none. */
    val inputTransformations: String? = null,
    /**
     * How the response reported its cache writes ([RawUsage.cacheReport]): "breakdown" (by lifetime), "total" (only
     * cache_creation_input_tokens, filed as 5-minute writes), null (neither, or no response).
     */
    val cacheReport: String? = null,
    /** The usage objects as the API sent them (message_start's, message_delta's): kept for a few calls ([UsageSample]). */
    val usageRaw: RawUsage? = null,
)

/** Where a turn ran: the day's thread, a stateless call (another model than the thread's), "also run on", closing. */
@Serializable
enum class TurnKind { DAY, STATELESS, ALSO, CLOSING }

@Serializable
enum class TurnStatus { QUEUED, RUNNING, DONE, LIMIT, REFUSED, TRUNCATED, FAILED, PAUSED, SKIPPED }

/** A turn: the events it answers, the model, how it went, its replies and tool calls, its calls' ids. */
@Serializable
data class TurnRecord(
    val id: String,
    val eventIds: List<String>,
    val kind: TurnKind,
    val task: Task,
    val model: String,
    val eventKind: String,
    val queuedAt: Long,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val status: TurnStatus = TurnStatus.QUEUED,
    val replies: List<ShadowReply> = emptyList(),
    val toolCalls: List<ToolCallRecord> = emptyList(),
    val callIds: List<String> = emptyList(),
    /** Text outside `reply` (logged for debugging, never shown as the tutor's). */
    val text: String = "",
    val error: String? = null,
    val attempts: Int = 0,
    /** Not before this time (a 429's retry-after, a backoff). */
    val notBefore: Long = 0,
    /** Why it runs (a digest's trigger and what's worth saying; "also run on"; a restart's size). */
    val why: String? = null,
)

/** The panel's settings for the shadow tutor (not the key: that is in the [KeyVault]). */
@Serializable
data class ShadowSettings(
    val enabled: Boolean = false,
    val preset: Preset = Preset.BALANCED,
    /** The model per task set in the panel ("Custom"); a task without one follows the preset. */
    val models: Map<Task, String> = emptyMap(),
    /** The day's thread restarts past this many tokens (null: the preset's). */
    val threadLimit: Int? = null,
    /** A day's spend in dollars that pauses the shadow. */
    val budgetPerDay: Double = 3.0,
    /** Reviews and session ends get their reaction right away instead of in the digest (plan 5, "react at once"). */
    val reactAtOnce: Boolean = false,
    /** Debug and qa builds only (BuildConfig.QA): the Messages API's base URL (QA's mock); ignored by a release build. */
    val debugBaseUrl: String? = null,
    /** The models the key listed at its check (GET /v1/models), and when. */
    val listed: List<ModelInfo> = emptyList(),
    val listedAt: Long = 0,
    /** The key was refused (401): the panel asks for a new one. */
    val keyRefused: Boolean = false,
    /** The credit balance was too low at the last call. */
    val lowCredit: Boolean = false,
    /** The day the evening's digest ran (the plan's triggers: the app opened after 2 h, the evening, the chat opened). */
    val eveningDigest: String? = null,
    /** The developer section is shown in the settings (revealed by tapping the version seven times). */
    val developer: Boolean = false,
) {
    val limit: Int get() = threadLimit ?: preset.threadLimit
}

/** A version of the notebook: its text, when and why it was written (note, day's end, restart, by hand). */
@Serializable
data class NotebookVersion(val at: Long, val text: String, val why: String)

/** The tutor's notebook (plan 5, "The notebook"): what it knows about the learner, and its older versions. */
@Serializable
data class NotebookState(
    val text: String = "",
    val versions: List<NotebookVersion> = emptyList(),
    val updatedAt: Long = 0,
)

/**
 * The day's thread (plan 5, "The day's thread"): one per local day from 04:00, only ever appended to, on one model
 * ([model]: the chat's); [messages] as sent; [pending] lines from stateless turns (another model's replies), added to
 * the next user message; [tokens] its size after the last call; [restarts] the sizes it was restarted at.
 */
@Serializable
data class ThreadState(
    val day: String,
    val model: String,
    val messages: List<JsonObject> = emptyList(),
    val pending: List<String> = emptyList(),
    val tokens: Long = 0,
    val restarts: List<Long> = emptyList(),
    val startedAt: Long = 0,
    /** The turns of the day so far, a line each ("18:03 chat: …"), for a restart's first message. */
    val lines: List<String> = emptyList(),
    /** The day before's lines ("yesterday in brief", in the first message with the notebook). */
    val yesterday: List<String> = emptyList(),
    /** The prefix it was made under ([Prefix.hash]): another one (an app update) restarts it. */
    val prefix: String = "",
)
