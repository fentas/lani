package si.lanisce.lani.tutor

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A tool call the model made: [badJson] is its input as streamed when that wasn't valid JSON. */
data class ToolUse(val id: String, val name: String, val input: JsonObject, val badJson: String? = null)

/** What a tool call gave the model ([content], [isError]) and what the shadow log keeps of it. */
data class ToolOutcome(val content: String, val isError: Boolean, val record: ToolCallRecord)

/** Carries out one tool call (in shadow mode: reads through GET, writes as dry runs, `reply` into the panel). */
fun interface ToolRunner {
    suspend fun run(use: ToolUse): ToolOutcome
}

/** How a turn's loop ended. */
sealed interface LoopEnd {
    /** The model ended its turn. */
    data object Done : LoopEnd
    /** The turn used its calls ([TutorLoop.maxCalls]). */
    data object Limit : LoopEnd
    /** `stop_reason: refusal`: a neutral note, no retry ([details] its stop_details). */
    data class Refused(val details: JsonObject?) : LoopEnd
    /** `max_tokens` again after one retry with more room. */
    data object Truncated : LoopEnd
    /** An error the loop doesn't retry, or one still failing after its retries. */
    data class Failed(val error: ApiError) : LoopEnd
    /** The day's budget is spent: the turn waits. */
    data object Paused : LoopEnd
}

/**
 * The reply of a turn that never called `reply` (Haiku 5.5 at `low` in Jan's shadow, 2026-10-16: the whole answer as
 * plain text, then end_turn): the text that ended the turn ([TutorLoop.Result.endText]) is the reply, to the event's
 * [conversation]. None when the turn replied through the tool, didn't end (a limit, a failure …), is a closing (it
 * writes the notebook and says nothing to the learner), or ended without text.
 */
object ReplyFallback {
    fun of(result: TutorLoop.Result, kind: TurnKind, replied: Boolean, conversation: String, at: Long): ShadowReply? {
        if (replied || kind == TurnKind.CLOSING || result.end != LoopEnd.Done) return null
        val said = result.endText?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return ShadowReply(conversation, said, at)
    }
}

/** One call's outcome for the meter: a response ([step]), or the error of an attempt ([retried]: another attempt follows). */
data class CallOutcome(val step: Step?, val error: ApiError?, val retried: Boolean = false)

/**
 * The tool loop (plan 5, "The tool loop"), the same on every provider ([TutorProvider]): a manual loop, each step saved
 * (the model's answer, then the tools' results), so a turn resumes where it stopped after Android ended the app;
 * parallel tool calls run together and answer in one step; at most [maxCalls] calls a turn; the budget checked before
 * each; `max_tokens` retried once with more room, `refusal` never; 429 after its `retry-after`, 529/5xx/timeouts with
 * backoff (a wait longer than [maxWaitMs] goes back to the queue). What the model sent goes back unchanged (thinking
 * blocks with their signatures, reasoning items with their encrypted content): the thread is only ever appended to.
 */
class TutorLoop(
    private val provider: TutorProvider,
    private val runner: ToolRunner,
    val maxCalls: Int = 8,
    private val budgetOk: () -> Boolean = { true },
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val maxRetries: Int = 3,
    private val maxWaitMs: Long = 30_000,
) {
    /** The loop on the Messages API over [transport] ([AnthropicProvider]). */
    constructor(
        transport: TutorTransport,
        runner: ToolRunner,
        maxCalls: Int = 8,
        budgetOk: () -> Boolean = { true },
        sleep: suspend (Long) -> Unit = { delay(it) },
        maxRetries: Int = 3,
        maxWaitMs: Long = 30_000,
    ) : this(AnthropicProvider(transport), runner, maxCalls, budgetOk, sleep, maxRetries, maxWaitMs)

    /**
     * How the turn went: [end], its [calls]; [endText] the text of the response that ended it with `end_turn` and no
     * tool call (null otherwise, and for a resumed turn whose last answer was saved before: its stop reason isn't kept).
     */
    data class Result(val end: LoopEnd, val calls: Int, val endText: String? = null)

    /**
     * Runs the turn whose [messages] (the thread, in the provider's format) end with its user message (or, resumed, with
     * a step already saved): [callsDone] calls of it ran before. [save] gets the messages after each step, [onCall] each
     * call's outcome, [onTool] each tool call's record.
     */
    suspend fun run(
        prefix: Prefix,
        model: String,
        task: Task,
        messages: MutableList<JsonObject>,
        callsDone: Int = 0,
        save: suspend (List<JsonObject>) -> Unit = {},
        onCall: suspend (CallOutcome) -> Unit = {},
        onTool: suspend (ToolCallRecord) -> Unit = {},
        /**
         * The request's top-level effort: the thread's, the same for all its turns (a change clears the cache; a turn at
         * another level has an effort item in [messages]), or a stateless turn's own. Null: the model takes none.
         */
        effort: String? = task.effort,
        /** max_tokens to start with: the task's at the level the turn runs at (more at xhigh and max). */
        maxTokens: Int = task.maxTokensAt(effort),
    ): Result {
        val format = provider.format
        var calls = callsDone
        var maxTokens = maxTokens
        var widened = false
        var badInput: Map<String, String> = emptyMap()
        while (true) {
            if (messages.isEmpty()) return Result(LoopEnd.Failed(ApiError.BadRequest(0, "no messages")), calls)
            val open = format.pending(messages)
            if (open != null) {
                if (open.isEmpty()) return Result(LoopEnd.Done, calls)
                val outcomes = coroutineScope {
                    open.map { u -> async { runner.run(if (u.badJson == null && u.id in badInput) u.copy(badJson = badInput[u.id]) else u) } }.awaitAll()
                }
                outcomes.forEach { onTool(it.record) }
                messages += format.results(open, outcomes)
                save(messages)
                continue
            }
            if (calls >= maxCalls) return Result(LoopEnd.Limit, calls)
            if (!budgetOk()) return Result(LoopEnd.Paused, calls)
            val step = try {
                call(provider.request(prefix, model, task, messages, maxTokens, effort), task, onCall)
            } catch (e: ApiError) {
                onCall(CallOutcome(null, e))
                return Result(LoopEnd.Failed(e), calls)
            }
            calls++
            onCall(CallOutcome(step, null))
            when (step.stopReason) {
                Stop.REFUSAL -> return Result(LoopEnd.Refused(step.stopDetails), calls)
                Stop.MAX_TOKENS -> {
                    if (!widened) {
                        widened = true
                        maxTokens = (maxTokens * 2).coerceAtMost(64_000)
                        continue
                    }
                    // what was said stays (a tool call cut off never runs)
                    if (step.kept.isNotEmpty()) {
                        messages += step.kept
                        save(messages)
                    }
                    return Result(LoopEnd.Truncated, calls)
                }
                // pause_turn: a server tool's long turn paused. The tutor uses no server tools, so it can only be
                // unexpected: what came is kept and the turn ends (no crash, no made-up message to go on with).
                else -> {
                    if (step.items.isEmpty()) return Result(LoopEnd.Done, calls)
                    messages += step.items
                    save(messages)
                    if (step.stopReason != Stop.TOOL_USE || step.toolCalls.isEmpty()) {
                        val said = step.text.takeIf { step.stopReason == Stop.END_TURN && step.toolCalls.isEmpty() && it.isNotBlank() }
                        return Result(LoopEnd.Done, calls, said)
                    }
                    badInput = step.toolCalls.mapNotNull { u -> u.badJson?.let { u.id to it } }.toMap()
                }
            }
        }
    }

    /** One call with its retries (the plan's table): a 429 after its retry-after, 529/5xx/network with backoff. */
    private suspend fun call(body: JsonObject, task: Task, onCall: suspend (CallOutcome) -> Unit): Step {
        var attempt = 0
        while (true) {
            try {
                return provider.call(body)
            } catch (e: ApiError) {
                if (!e.retryable || attempt >= maxRetries) throw e
                val wait = when (e) {
                    is ApiError.RateLimited -> e.retryAfterMs ?: backoff(attempt, task)
                    else -> backoff(attempt, task)
                }
                if (wait > maxWaitMs) throw e
                onCall(CallOutcome(null, e, retried = true))
                sleep(wait)
                attempt++
            }
        }
    }

    companion object {
        /** 1, 2, 4 s; routine work backs off longer (×4). */
        fun backoff(attempt: Int, task: Task): Long = (1_000L shl attempt.coerceIn(0, 10)) * if (task == Task.ROUTINE) 4 else 1

        fun assistant(content: List<JsonObject>): JsonObject = buildJsonObject {
            put("role", "assistant")
            put("content", JsonArray(content))
        }

        /** The tools' results to the tool_use blocks [uses], in one user message, in the calls' order (the Messages API's). */
        fun toolResults(uses: List<JsonObject>, outcomes: List<ToolOutcome>): JsonObject =
            AnthropicFormat.results(uses.map { ToolUse(it.str("id").orEmpty(), it.str("name").orEmpty(), JsonObject(emptyMap())) }, outcomes).single()
    }
}
