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
    /** The photo a chat came with (its id, the phone's copy's size): the turn sends it as an image block, never the export. */
    val image: ShadowImage? = null,
)

/** A chat's photo as the shadow keeps it ([ShadowEvent.image]): its [id] (the phone's file, ChatPhotoStore) and [bytes]. */
@Serializable
data class ShadowImage(val id: String, val bytes: Long = 0)

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
    /** The effort the call ran at (null: the model takes none, or a call from before efforts were logged). */
    val effort: String? = null,
    /** [effort] was set by an effort message within the thread ([Effort]), not the request's top-level one. */
    val effortByMessage: Boolean = false,
    /** The response's visible characters: its text and its tool calls' input (not its thinking), for [thinkingEstimate]. */
    val visibleChars: Int? = null,
    /** How many images (a chat's photos) the request carried: the thread's so far, or a stateless call's own. */
    val images: Int = 0,
    /** The matrix's lane that made it ([ShadowMatrix]: "claude-sonnet-5-5@low", "openai:gpt-6.1-sol@medium"); null: the primary's. */
    val lane: String? = null,
    /** The provider when it isn't Anthropic ([ProviderId.id]: "openai"); null: Anthropic's, as every call before. */
    val provider: String? = null,
    /**
     * How it was paid when not by the Claude key ([Billing]: "api_key" for an OpenAI key, "chatgpt_plan"); with the
     * ChatGPT plan [cost] is 0 (the learner pays nothing per token) and [apiEquivalent] what the tokens cost on the API.
     */
    val billing: String? = null,
    /** With the ChatGPT plan: the API-equivalent price of its tokens (prices.json), labelled as such; else null. */
    val apiEquivalent: Double? = null,
) {
    /**
     * About how much of [Usage.output] was thinking: the reasoning tokens where the provider reports them (OpenAI's),
     * else the output less the visible part at about four characters a token (the Messages API doesn't report thinking
     * apart). An estimate on Anthropic's; null without either.
     */
    val thinkingEstimate: Long? get() = if (usage.reasoning > 0) usage.reasoning else visibleChars?.let { v -> (usage.output - (v + 3) / 4).coerceAtLeast(0) }

    /** What its tokens cost on the API: the API-equivalent price with the ChatGPT plan, else the cost. */
    val apiCost: Double? get() = apiEquivalent ?: cost
}

/**
 * Where a turn ran: the day's thread, a stateless call (another model than the thread's), "also run on", closing; the
 * live tutor's role-play turns on their own short thread ([ROLEPLAY]).
 */
@Serializable
enum class TurnKind { DAY, STATELESS, ALSO, CLOSING, ROLEPLAY }

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
    /** Text outside `reply` (logged for debugging; the tutor's only when the turn had no reply: [replyFromText]). */
    val text: String = "",
    val error: String? = null,
    val attempts: Int = 0,
    /** Not before this time (a 429's retry-after, a backoff). */
    val notBefore: Long = 0,
    /** Why it runs (a digest's trigger and what's worth saying; "also run on"; a restart's size). */
    val why: String? = null,
    /**
     * The effort it runs at: set when it's queued (the task's by the settings, or "also run on"'s), then the level it
     * ran at (a day's turn on a model without per-message effort runs at the thread's). Null: the model takes none.
     */
    val effort: String? = null,
    /**
     * The turn ended (end_turn) with text and no `reply` call: its last text became its reply ([replies]), to the
     * event's conversation (Haiku 5.5 at `low` did that in Jan's shadow). Counted per day, by model and effort.
     */
    val replyFromText: Boolean = false,
    /** The matrix's lane it ran in ([ShadowMatrix]: "claude-sonnet-5-5@low"); null: the primary's. */
    val lane: String? = null,
)

/** The panel's settings for the shadow tutor (not the key: that is in the [KeyVault]). */
@Serializable
data class ShadowSettings(
    val enabled: Boolean = false,
    val preset: Preset = Preset.BALANCED,
    /** The model per task set in the panel ("Custom"); a task without one follows the preset. */
    val models: Map<Task, String> = emptyMap(),
    /** The effort per task set in the panel ([Effort.LEVELS]); a task without one follows the preset. */
    val efforts: Map<Task, String> = emptyMap(),
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
    /** The matrix test: lanes of one model at one effort beside the primary ([ShadowMatrix]); off by default. */
    val matrix: MatrixSettings = MatrixSettings(),
    /** OpenAI (the matrix's OpenAI lanes, "also run on" an OpenAI model): what its credential lists, which one to use. */
    val openai: OpenAISettings = OpenAISettings(),
) {
    val limit: Int get() = threadLimit ?: preset.threadLimit
}

/**
 * The OpenAI side of the settings (never a credential: those are in their [KeyVault]s, [OpenAICredentials]): the models
 * the credential in use listed and when ([listed], [listedAt]); which credential to use when both are set ([use]: a
 * [Billing], null: the ChatGPT plan when signed in, else the key); why its calls stopped ([refused]: a 401, the sign-in
 * ended; [exhausted]: the credit, a spend limit or the plan's usage limit), until it's checked again.
 */
@Serializable
data class OpenAISettings(
    val listed: List<ModelInfo> = emptyList(),
    val listedAt: Long = 0,
    val listedWith: String? = null,
    val use: String? = null,
    val refused: Boolean = false,
    val exhausted: String? = null,
)

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
    /**
     * Its top-level effort, fixed for its life (the chat's when it began; a change would clear its cache): a turn at
     * another level changes it by an effort message ([Effort.append]). Null: a thread from before efforts were settings.
     */
    val effort: String? = null,
    /**
     * When the last of the real tutor's replies put in it came ([TurnText.shown]: what the learner was shown): a turn adds
     * those after it, in its own user message (append-only). 0: none yet.
     */
    val shownUntil: Long = 0,
) {
    /** The top-level effort its requests send: [effort]; a thread from before ran at `medium`; none on a model without effort. */
    val topEffort: String? get() = effort ?: Effort.MEDIUM.takeIf { ModelCatalog.adaptive(model) }
}
