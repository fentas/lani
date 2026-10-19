package si.lanisce.lani.tutor

import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * What the in-app tutors share (docs/plans/05-built/tutor-live.md): the shadow ([ShadowTutor]), its matrix's lanes
 * ([ShadowMatrix]) and the live tutor (tutor/live/LiveTutor). The tool loop is [TutorLoop] on a [TutorProvider], the
 * presets and the models per task [Preset] and [ModelCatalog], the effort [Effort], the prices [Prices]; here:
 * - the day's thread: a new one, its restart as text, the line a turn leaves in it ([DayThreads]: plan 5, "The day's
 *   thread", "Compaction: our own");
 * - the notebook's edits ([Notebooks]: plan 5, "The notebook");
 * - a call's record and price ([Meter]: plan 5, "Budgets and the spend shown");
 * - when a failed turn goes again ([Failures]: the plan's error table).
 */
object DayThreads {
    /** A new day's thread on [model], under the prefix [prefix], its top-level [effort] fixed for its life. */
    fun fresh(day: String, model: String, now: Long, yesterday: List<String>, prefix: String, effort: String?): ThreadState =
        ThreadState(day = day, model = model, startedAt = now, yesterday = yesterday, prefix = prefix, effort = effort)

    /**
     * A restart's first user message (plan 5, "Compaction: our own"): the notebook, [why] with the size it restarted at
     * and the day so far ([lines]), the last exchanges as plain text, never as assistant turns, so no history is edited.
     */
    fun restartBlocks(notebook: NotebookState, why: String, tokens: Long, limit: Int, lines: List<String>, exchanges: List<String>): List<JsonObject> =
        listOf(
            TurnText.notebook(notebook),
            "$why at $tokens tokens (the limit is $limit). " + TurnText.lines("The day so far:", lines.takeLast(20)),
            TurnText.lines("The last exchanges (as text):", exchanges),
        ).filter { it.isNotBlank() }.map(::textBlock)

    /**
     * A fresh thread after [th], its first message [blocks] ([restartBlocks]), under [prefix] at the top-level [effort]
     * (the same as before: the prefix's cache may be bound to it).
     */
    fun restarted(th: ThreadState, blocks: List<JsonObject>, now: Long, prefix: String, effort: String?): ThreadState =
        ThreadState(
            day = th.day, model = th.model, messages = listOf(ThreadFormat.of(th.model).user(blocks)),
            restarts = th.restarts + th.tokens, startedAt = now, lines = th.lines, yesterday = th.yesterday, prefix = prefix,
            effort = effort, shownUntil = th.shownUntil,
        )

    /**
     * The level a turn at [want] sets on thread [th] by an effort message, or null (the thread's level in effect holds)
     * when its model has no per-message effort: changing the top-level effort instead would clear the thread's cache.
     */
    fun onThread(th: ThreadState, want: String?): String? = want.takeIf { ModelCatalog.perMessageEffort(th.model) }

    /** The line a finished turn [t] leaves in its day's thread: "18:03 chat (claude-sonnet-5-5): «…»". */
    fun line(t: TurnRecord, endedAt: Long, zone: ZoneId, model: String = t.model): String {
        val at = Instant.ofEpochMilli(endedAt).atZone(zone).toLocalTime().withNano(0).withSecond(0)
        val said = t.replies.firstOrNull()?.text?.replace(Regex("\\s+"), " ")?.take(140)?.let { "«$it»" } ?: "no reply"
        return "$at ${t.eventKind} ($model): $said"
    }

    /**
     * The last exchanges of [turns] (the day's, done) as text, for a restart: the event, the reply, and (the shadow's)
     * what the learner was shown when another tutor answered ([real]).
     */
    fun exchanges(turns: List<TurnRecord>, event: (String) -> ShadowEvent?, real: List<RealReply> = emptyList()): List<String> =
        turns.takeLast(3).map { t ->
            val e = t.eventIds.firstOrNull()?.let(event)
            val shown = real.filter { it.eventId != null && it.eventId in t.eventIds }.joinToString(" / ") { it.reply.text.take(600) }
            "${t.eventKind}: ${e?.text?.take(400).orEmpty()} → you replied: ${t.replies.joinToString(" / ") { it.text.take(600) }.ifBlank { "(nothing)" }}" +
                (if (shown.isNotBlank()) " → the learner was shown (another tutor answered): $shown" else "")
        }
}

/** The notebook's edits (plan 5, "The notebook"): a line added during the day, the whole rewritten (the old kept). */
object Notebooks {
    /** [nb] with the line [line] added ("- …"). */
    fun noted(nb: NotebookState, line: String, now: Long): NotebookState = nb.copy(text = (nb.text.trimEnd() + "\n- " + line).trim(), updatedAt = now)

    /** [nb] rewritten to [text] ([why]: notebook_write, by hand); the old version kept (the last 30). */
    fun written(nb: NotebookState, text: String, why: String, now: Long): NotebookState =
        NotebookState(text, (nb.versions + NotebookVersion(nb.updatedAt, nb.text, why)).filter { it.text.isNotBlank() }.takeLast(30), now)
}

/** A call's record (plan 5, "What the prototype measures"; the live tutor's spend): its tokens, latency, price. */
object Meter {
    fun id(): String = "c_" + UUID.randomUUID().toString().replace("-", "").take(12)

    /**
     * The record of call [c] of turn [t] at [at] on [thread] (day, stateless, also, closing, roleplay), priced by
     * [priced]; [raw] its usage as the API sent it when kept; [provider] and [billing] when not Anthropic's.
     */
    fun record(
        t: TurnRecord, c: CallOutcome, at: Long, thread: String, priced: Priced?, effort: String?, model: String = t.model,
        effortByMessage: Boolean = false, images: Int = 0, raw: RawUsage? = null, lane: String? = null,
        provider: ProviderId = ProviderId.of(model), billing: String = Billing.API_KEY, transformationsMax: Int = 2000,
    ): CallRecord {
        val r = c.step
        val usage = r?.usage ?: Usage()
        val other = provider.takeIf { it != ProviderId.ANTHROPIC }
        return CallRecord(
            effort = effort, effortByMessage = effortByMessage, visibleChars = r?.visibleChars,
            id = id(), turnId = t.id, model = r?.model ?: model, eventKind = t.eventKind, task = t.task, at = at, usage = usage,
            firstTokenMs = r?.firstTokenMs, totalMs = r?.totalMs ?: 0, stopReason = r?.stopReason,
            cost = priced?.cost, threadAfter = usage.prompt, thread = thread,
            error = c.error?.let { "${it.kind}${if (c.retried) " (retried)" else ""}: ${Redact.of(it.message.orEmpty()).take(300)}" },
            inputTransformations = r?.inputTransformations?.take(transformationsMax), cacheReport = r?.rawUsage?.cacheReport,
            usageRaw = raw, images = images, lane = lane,
            provider = other?.id, billing = billing.takeIf { other != null }, apiEquivalent = priced?.apiEquivalent,
        )
    }

    /** The spend of [calls] in dollars (what the learner pays: $0 on the ChatGPT plan). */
    fun spend(calls: List<CallRecord>): Double = calls.sumOf { it.cost ?: 0.0 }
}

/** How a failed turn goes on (the plan's error table): the same rules for every tutor. */
object Failures {
    /** A turn that keeps failing (the network, an overloaded API) is given up after this many tries. */
    const val MAX_ATTEMPTS = 5

    /**
     * When a turn that failed with [e] on its [attempts]th try may go again: after a 429's retry-after, else 30 s
     * doubling (at most 16 min); null when [e] isn't retried or the tries are used.
     */
    fun retryAt(e: ApiError, attempts: Int, now: Long, max: Int = MAX_ATTEMPTS): Long? =
        if (e.retryable && attempts < max) now + ((e as? ApiError.RateLimited)?.retryAfterMs ?: (30_000L shl (attempts - 1).coerceIn(0, 5))) else null

    /**
     * Why money stops the calls ([e]): the ChatGPT plan's usage limit, a spend limit (Gemini: a quota or billing that
     * isn't on; OpenRouter: the key's limit), or the credit out.
     */
    fun creditWhy(e: ApiError.LowCredit, provider: ProviderId): String = when {
        e.allowance -> "the ChatGPT plan's usage limit was reached (ChatGPT settings → Usage)"
        e.limit -> when (provider) {
            ProviderId.OPENAI -> "an OpenAI spend limit was reached"
            ProviderId.GEMINI -> "a Gemini API quota or billing limit was reached (${e.message.orEmpty().substringAfterLast(": ").take(160)})"
            ProviderId.OPENROUTER -> "the OpenRouter key's credit limit was reached (openrouter.ai/settings/keys)"
            ProviderId.ANTHROPIC -> "a spend limit was reached"
        }
        else -> when (provider) {
            ProviderId.OPENAI -> "the OpenAI credit is out"
            ProviderId.GEMINI -> "the Gemini API's prepaid credit is out (Google AI Studio → Billing)"
            ProviderId.OPENROUTER -> "the OpenRouter credits are out (openrouter.ai/settings/credits)"
            ProviderId.ANTHROPIC -> "the credit balance is too low"
        }
    }
}
