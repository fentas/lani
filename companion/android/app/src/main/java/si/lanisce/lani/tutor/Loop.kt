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

/** One call's outcome for the meter: a response, or the error of an attempt ([retried]: another attempt follows). */
data class CallOutcome(val result: StreamResult?, val error: ApiError?, val retried: Boolean = false)

/**
 * The tool loop (plan 5, "The tool loop"): a manual loop, each step saved (the assistant's message, then the tools'
 * results), so a turn resumes where it stopped after Android ended the app; parallel tool calls run together and
 * answer in one message; at most [maxCalls] calls a turn; the budget checked before each; `max_tokens` retried once with
 * more room, `refusal` never; 429 after its `retry-after`, 529/5xx/timeouts with backoff (a wait longer than
 * [maxWaitMs] goes back to the queue). Thinking blocks go back unchanged: the messages are only ever appended to.
 */
class TutorLoop(
    private val transport: TutorTransport,
    private val runner: ToolRunner,
    val maxCalls: Int = 8,
    private val budgetOk: () -> Boolean = { true },
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val maxRetries: Int = 3,
    private val maxWaitMs: Long = 30_000,
) {
    data class Result(val end: LoopEnd, val calls: Int)

    /**
     * Runs the turn whose [messages] end with its user message (or, resumed, with a step already saved): [callsDone]
     * calls of it ran before. [save] gets the messages after each step, [onCall] each call's outcome, [onTool] each tool
     * call's record.
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
        /** The thinking effort: the thread's, the same for all its turns (a change clears the cache), or the task's. */
        effort: String = task.effort,
    ): Result {
        var calls = callsDone
        var maxTokens = task.maxTokens
        var widened = false
        var badInput: Map<String, String> = emptyMap()
        while (true) {
            val last = messages.lastOrNull() ?: return Result(LoopEnd.Failed(ApiError.BadRequest(0, "no messages")), calls)
            if (last.role() == "assistant") {
                val uses = last.blocks().filter { it.str("type") == "tool_use" }
                if (uses.isEmpty()) return Result(LoopEnd.Done, calls)
                val outcomes = coroutineScope {
                    uses.map { u ->
                        async {
                            val id = u.str("id").orEmpty()
                            runner.run(ToolUse(id, u.str("name").orEmpty(), u["input"] as? JsonObject ?: JsonObject(emptyMap()), badInput[id]))
                        }
                    }.awaitAll()
                }
                outcomes.forEach { onTool(it.record) }
                messages += toolResults(uses, outcomes)
                save(messages)
                continue
            }
            if (calls >= maxCalls) return Result(LoopEnd.Limit, calls)
            if (!budgetOk()) return Result(LoopEnd.Paused, calls)
            val result = try {
                call(prefix.request(model, task, messages, maxTokens, effort), task, onCall)
            } catch (e: ApiError) {
                onCall(CallOutcome(null, e))
                return Result(LoopEnd.Failed(e), calls)
            }
            calls++
            onCall(CallOutcome(result, null))
            val m = result.message
            when (m.stopReason) {
                "refusal" -> return Result(LoopEnd.Refused(m.stopDetails), calls)
                "max_tokens" -> {
                    if (!widened) {
                        widened = true
                        maxTokens = (maxTokens * 2).coerceAtMost(64_000)
                        continue
                    }
                    // what was said stays (a tool call cut off never runs)
                    val kept = m.content.filter { it.str("type") == "text" && !it.str("text").isNullOrBlank() }
                    if (kept.isNotEmpty()) {
                        messages += assistant(kept)
                        save(messages)
                    }
                    return Result(LoopEnd.Truncated, calls)
                }
                // pause_turn: a server tool's long turn paused. The tutor uses no server tools, so it can only be
                // unexpected: what came is kept and the turn ends (no crash, no made-up message to go on with).
                else -> {
                    if (m.content.isEmpty()) return Result(LoopEnd.Done, calls)
                    messages += assistant(m.content)
                    save(messages)
                    if (m.stopReason != "tool_use" || m.toolUses.isEmpty()) return Result(LoopEnd.Done, calls)
                    badInput = m.badInput
                }
            }
        }
    }

    /** One call with its retries (the plan's table): a 429 after its retry-after, 529/5xx/network with backoff. */
    private suspend fun call(body: JsonObject, task: Task, onCall: suspend (CallOutcome) -> Unit): StreamResult {
        var attempt = 0
        while (true) {
            try {
                return transport.stream(body)
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

        /** The tools' results, in one user message, in the calls' order. */
        fun toolResults(uses: List<JsonObject>, outcomes: List<ToolOutcome>): JsonObject = buildJsonObject {
            put("role", "user")
            put(
                "content",
                JsonArray(
                    uses.zip(outcomes).map { (u, o) ->
                        buildJsonObject {
                            put("type", "tool_result")
                            put("tool_use_id", u.str("id").orEmpty())
                            put("content", o.content)
                            if (o.isError) put("is_error", true)
                        }
                    },
                ),
            )
        }
    }
}
