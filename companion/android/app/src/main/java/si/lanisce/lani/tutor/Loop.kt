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
        // a turn at its limit has text only from the loop's last word, asked for with the tools off (a turn [Owed] a reply)
        if (replied || kind == TurnKind.CLOSING || (result.end != LoopEnd.Done && result.end != LoopEnd.Limit)) return null
        val said = result.endText?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return ShadowReply(conversation, said, at)
    }
}

/**
 * The reply a turn owes the learner (the matrix, 2026-10: four turns looped on a card fix to the call limit and ended
 * with no reply; three stopped on `tool_use` with no tool call and ended with nothing): [replied] whether one went out in
 * this turn; [required] the turn must answer (a chat, an answer, a session's end, a role-play: [REQUIRED]), so ending it
 * without a reply asks for the last word too; a digest may end silent, but not at its limit.
 */
class Owed(val replied: () -> Boolean, val required: Boolean) {
    companion object {
        /** The kinds whose turn must reply: the learner waits for it, or the app takes its data. */
        val REQUIRED = setOf("chat", "answer", "session_end", "roleplay", "roleplay_end", "town_check")

        /** What a turn for an event of [eventKind] owes, with [replied] its replies so far; null: nothing (a closing). */
        fun of(eventKind: String, kind: TurnKind, replied: () -> Boolean): Owed? =
            if (kind == TurnKind.CLOSING) null else Owed(replied, eventKind in REQUIRED)
    }
}

/** One call's outcome for the meter: a response ([step]), or the error of an attempt ([retried]: another attempt follows). */
data class CallOutcome(val step: Step?, val error: ApiError?, val retried: Boolean = false)

/**
 * The tool loop (plan 5, "The tool loop"), the same on every provider ([TutorProvider]): a manual loop, each step saved
 * (the model's answer, then the tools' results), so a turn resumes where it stopped after Android ended the app;
 * parallel tool calls run together and answer in one step; at most [maxCalls] calls a turn; the budget checked before
 * each; `max_tokens` retried once with more room, `refusal` never; 429 after its `retry-after`, 529/5xx/timeouts with
 * backoff (a wait longer than [maxWaitMs] goes back to the queue), a network error while the phone is offline
 * ([online]) not retried at all (the turn goes back to the queue, which waits for the network). What the model sent goes
 * back unchanged (thinking blocks with their signatures, reasoning items with their encrypted content): the thread is
 * only ever appended to.
 *
 * **Never a silent turn** (a turn that [Owed] a reply):
 * - with one call left and no reply yet, the tools' results say so ([LAST_CALL]): answer now with `reply`;
 * - at the limit with still none, one more call with the tools off ([TutorProvider.toolsOff]; forced tool use is a 400 on
 *   the 5.5 models, `none` isn't) after the results say so ([TOOLS_OFF]): its text is the reply ([ReplyFallback]);
 * - a response that stops on `tool_use` with no tool call in it is asked once more (not kept: nothing to answer); the
 *   second time its text is the reply, or, with none, the tools-off call gives one;
 * - a required turn ([Owed.required]) that ends with neither a reply nor text gets the tools-off call too.
 */
class TutorLoop(
    private val provider: TutorProvider,
    private val runner: ToolRunner,
    val maxCalls: Int = 8,
    private val budgetOk: () -> Boolean = { true },
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val maxRetries: Int = 3,
    private val maxWaitMs: Long = 30_000,
    /** Whether the phone is online now ([Online.now]): a network error while it isn't goes back to the queue at once. */
    private val online: () -> Boolean = { true },
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
        online: () -> Boolean = { true },
    ) : this(AnthropicProvider(transport), runner, maxCalls, budgetOk, sleep, maxRetries, maxWaitMs, online)

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
        /** A tool call's input as it streams (the live tutor's typing bubble); null: the provider's plain call. */
        onToolInput: ((ToolInputDelta) -> Unit)? = null,
        /** The reply the turn owes the learner ([Owed]); null: none (a closing, the studio's jobs). */
        owed: Owed? = null,
    ): Result {
        val format = provider.format
        var calls = callsDone
        var maxTokens = maxTokens
        var widened = false
        var badInput: Map<String, String> = emptyMap()
        var askedAgain = false
        val silent = { owed != null && !owed.replied() }
        while (true) {
            if (messages.isEmpty()) return Result(LoopEnd.Failed(ApiError.BadRequest(0, "no messages")), calls)
            val open = format.pending(messages)
            if (open != null) {
                if (open.isEmpty()) return Result(LoopEnd.Done, calls)
                // a message's calls run together, its `reply` after the rest (a session persisted in the same message
                // counts before the reply goes: ReplyData.persistFirst); the results in the calls' order
                fun fixed(u: ToolUse) = if (u.badJson == null && u.id in badInput) u.copy(badJson = badInput[u.id]) else u
                val (first, last) = open.indices.partition { open[it].name != "reply" }
                val done = arrayOfNulls<ToolOutcome>(open.size)
                for (group in listOf(first, last)) coroutineScope { group.map { i -> async { done[i] = runner.run(fixed(open[i])) } }.awaitAll() }
                var outcomes = done.map { it!! }
                outcomes.forEach { onTool(it.record) }
                // nothing for the learner yet with the calls running out: the results say so (never a silent turn)
                val note = when {
                    !silent() -> null
                    calls == maxCalls - 1 -> LAST_CALL
                    calls >= maxCalls -> TOOLS_OFF
                    else -> null
                }
                if (note != null) outcomes = outcomes.dropLast(1) + outcomes.last().let { it.copy(content = it.content + "\n\n" + note) }
                messages += format.results(open, outcomes)
                save(messages)
                continue
            }
            if (calls >= maxCalls) {
                if (!silent() || calls > maxCalls) return Result(LoopEnd.Limit, calls)
                if (!budgetOk()) return Result(LoopEnd.Paused, calls)
                return lastWord(prefix, model, task, messages, maxTokens, effort, calls, save, onCall, LoopEnd.Limit)
            }
            if (!budgetOk()) return Result(LoopEnd.Paused, calls)
            val step = try {
                call(provider.request(prefix, model, task, messages, maxTokens, effort), task, onCall, onToolInput)
            } catch (e: ApiError) {
                onCall(CallOutcome(null, e))
                return Result(LoopEnd.Failed(e), calls)
            }
            calls++
            onCall(CallOutcome(step, null))
            // `tool_use` with no tool call in it (Sonnet in the matrix, three times): nothing to answer, so not kept, and
            // asked once more; the second time its text is the reply, or the tools-off call gives one
            if (step.stopReason == Stop.TOOL_USE && step.toolCalls.isEmpty()) {
                if (!askedAgain) {
                    askedAgain = true
                    continue
                }
                if (step.items.isNotEmpty()) {
                    messages += step.items
                    save(messages)
                }
                val said = step.text.takeIf { it.isNotBlank() }
                if (said != null || !silent() || calls > maxCalls) return Result(LoopEnd.Done, calls, said)
                if (!budgetOk()) return Result(LoopEnd.Paused, calls)
                return lastWord(prefix, model, task, messages, maxTokens, effort, calls, save, onCall, LoopEnd.Done)
            }
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
                    if (step.items.isNotEmpty()) {
                        messages += step.items
                        save(messages)
                    }
                    if (step.stopReason != Stop.TOOL_USE || step.toolCalls.isEmpty()) {
                        val said = step.text.takeIf { step.stopReason == Stop.END_TURN && step.toolCalls.isEmpty() && it.isNotBlank() }
                        // a required turn that ended with neither a reply nor text: the tools-off call gives one
                        if (said == null && owed?.required == true && silent() && calls <= maxCalls && budgetOk()) {
                            return lastWord(prefix, model, task, messages, maxTokens, effort, calls, save, onCall, LoopEnd.Done)
                        }
                        return Result(LoopEnd.Done, calls, said)
                    }
                    badInput = step.toolCalls.mapNotNull { u -> u.badJson?.let { u.id to it } }.toMap()
                }
            }
        }
    }

    /**
     * The turn's last word: one call with the tools off ([TutorProvider.toolsOff]), whose text is the reply the turn owes
     * ([ReplyFallback]); the turn ends as [end] says (the limit, or done), with that text. The thread keeps the answer.
     */
    private suspend fun lastWord(
        prefix: Prefix, model: String, task: Task, messages: MutableList<JsonObject>, maxTokens: Int, effort: String?, calls0: Int,
        save: suspend (List<JsonObject>) -> Unit, onCall: suspend (CallOutcome) -> Unit, end: LoopEnd,
    ): Result {
        var calls = calls0
        // a call needs the thread to end with input: after an answer without tool calls, the reason as a user line
        if (provider.format.pending(messages)?.isEmpty() == true) {
            val next = provider.format.append(messages.toList(), listOf(textBlock(NO_REPLY)), null, null)
            messages.clear()
            messages += next
            save(messages)
        }
        val step = try {
            call(provider.toolsOff(provider.request(prefix, model, task, messages, maxTokens, effort)), task, onCall)
        } catch (e: ApiError) {
            onCall(CallOutcome(null, e))
            return Result(LoopEnd.Failed(e), calls)
        }
        calls++
        onCall(CallOutcome(step, null))
        if (step.stopReason == Stop.REFUSAL) return Result(LoopEnd.Refused(step.stopDetails), calls)
        val kept = if (step.stopReason == Stop.MAX_TOKENS) step.kept else step.items
        if (kept.isNotEmpty()) {
            messages += kept
            save(messages)
        }
        return Result(end, calls, step.text.trim().takeIf { it.isNotEmpty() })
    }

    /**
     * One call with its retries (the plan's table): a 429 after its retry-after, 529/5xx/network with backoff; a network
     * error while the phone is offline ([online]) at once (the queue waits for the network: no retries burned).
     */
    private suspend fun call(body: JsonObject, task: Task, onCall: suspend (CallOutcome) -> Unit, onToolInput: ((ToolInputDelta) -> Unit)? = null): Step {
        var attempt = 0
        while (true) {
            try {
                return if (onToolInput == null) provider.call(body) else provider.callTools(body, {}, onToolInput)
            } catch (e: ApiError) {
                if (!e.retryable || attempt >= maxRetries || (e is ApiError.Network && !online())) throw e
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
        /** What the tools' results add when one call is left and nothing reached the learner yet. */
        const val LAST_CALL = "[Lani] One call is left in this turn and nothing has reached the learner yet: answer them now " +
            "with `reply` (what you found, fixed or couldn't do), without more lookups or edits."

        /** What the tools' results add before the tools-off call: the turn's calls are used. */
        const val TOOLS_OFF = "[Lani] This turn's calls are used and nothing has reached the learner yet. The tools are off " +
            "now: write your answer to the learner as plain text (it is sent as your reply): what you did and what is left."

        /** The user line before the tools-off call when the turn ended with neither a reply nor text. */
        const val NO_REPLY = "[Lani] Nothing reached the learner in this turn: the `reply` tool wasn't called. The tools are off " +
            "now: write your answer to the learner as plain text (it is sent as your reply)."

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
