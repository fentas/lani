package si.lanisce.lani.tutor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * A lane of the matrix (docs/plans/05-phase-0.md, "The matrix"): one model family at one effort, beside the primary.
 * [Lanes.plan] resolves the family to the model the key lists.
 */
@Serializable
data class LaneSpec(val family: Family, val effort: String)

/**
 * The matrix's settings ([ShadowSettings.matrix]): off by default, the default lanes preselected (Jan asked for Haiku at
 * xhigh and max). No budget of its own: nothing stops the lanes but the switch, a lane's toggle, a model the key doesn't
 * list, and the key refused or out of credit (Jan, 2026-10-18: "we have $200 for this month").
 */
@Serializable
data class MatrixSettings(
    val enabled: Boolean = false,
    val lanes: List<LaneSpec> = DEFAULT_LANES,
) {
    companion object {
        val DEFAULT_LANES = listOf(
            LaneSpec(Family.HAIKU, Effort.MEDIUM), LaneSpec(Family.HAIKU, Effort.HIGH), LaneSpec(Family.HAIKU, Effort.XHIGH), LaneSpec(Family.HAIKU, Effort.MAX),
            LaneSpec(Family.SONNET, Effort.LOW), LaneSpec(Family.SONNET, Effort.MEDIUM), LaneSpec(Family.SONNET, Effort.HIGH),
            LaneSpec(Family.OPUS, Effort.LOW), LaneSpec(Family.OPUS, Effort.MEDIUM),
        )
    }
}

/** A lane as the key's models resolve it: its [model], or why it's [skipped] (the panel says so). */
data class LanePlan(val family: Family, val effort: String, val model: String? = null, val skipped: String? = null) {
    /** "claude-sonnet-5-5@low"; null when skipped. */
    val id: String? get() = model?.takeIf { skipped == null }?.let { Lanes.id(it, effort) }
}

object Lanes {
    fun id(model: String, effort: String) = "$model@$effort"

    /** A lane's thread restarts past this many tokens: its family's preset's limit (Haiku 20k, Sonnet 30k, Opus 60k). */
    fun limit(f: Family): Int = when (f) {
        Family.HAIKU -> Preset.ECONOMY.threadLimit
        Family.SONNET -> Preset.BALANCED.threadLimit
        Family.OPUS -> Preset.BEST.threadLimit
    }

    /** The family of a lane's [model] (its id names it). */
    fun familyOf(model: String): Family = Family.entries.firstOrNull { model.contains(it.name.lowercase()) } ?: Family.HAIKU

    /**
     * The lanes of [m] for the key's [listed] models (ModelCatalog.own: the family's own model, never another family's),
     * in the grid's order; a lane whose model the key doesn't list, or that doesn't take the effort, is skipped.
     */
    fun plan(m: MatrixSettings, listed: List<ModelInfo>): List<LanePlan> {
        val catalog = ModelCatalog(listed)
        return m.lanes.distinct().sortedWith(compareBy({ it.family.ordinal }, { Effort.LEVELS.indexOf(it.effort) })).map { spec ->
            val model = catalog.own(spec.family)
            when {
                model == null -> LanePlan(spec.family, spec.effort, skipped = "the key lists no ${spec.family.name.lowercase()} model (${spec.family.preferred.first()})")
                spec.effort !in ModelCatalog.efforts(model) -> LanePlan(spec.family, spec.effort, model, "$model takes no ${spec.effort}")
                else -> LanePlan(spec.family, spec.effort, model)
            }
        }
    }

    /**
     * A tool call as a lane keeps it, small for the export (nine lanes multiply it): the input in brief (strings cut,
     * arrays shortened; `reply`'s text left out: it is the turn's reply), the result's start; a notebook call marked as
     * the dry run it is (a lane reads the primary's notebook and never changes it).
     */
    fun brief(r: ToolCallRecord): ToolCallRecord {
        val input = if (r.name == "reply") JsonObject(r.input - "text") else r.input
        return r.copy(
            input = cut(input) as JsonObject,
            result = r.result.take(RESULT),
            dryRun = r.dryRun || r.name == "notebook_note" || r.name == "notebook_write",
        )
    }

    private fun cut(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.mapValues { (_, v) -> cut(v) })
        is JsonArray -> JsonArray(e.take(8).map(::cut) + if (e.size > 8) listOf(JsonPrimitive("… ${e.size - 8} more")) else emptyList())
        is JsonPrimitive -> if (e.isString && e.content.length > INPUT) JsonPrimitive(e.content.take(INPUT) + " …") else e
    }

    const val INPUT = 300
    const val RESULT = 300
}

/**
 * The read tools shared by the lanes ([ShadowMatrix]): the same read within [ttlMs] (nine lanes at the same event) is
 * asked of the paired node once, and every lane gets the same answer, so the lanes compare on the same data and the
 * node isn't asked nine times. The primary keeps its own reads.
 */
class SharedReads(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long,
    private val ttlMs: Long = 120_000,
    private val source: suspend (String, JsonObject) -> ReadResult,
) : ReadSource {
    private class Entry(val at: Long, val result: Deferred<ReadResult>)

    private val cache = mutableMapOf<String, Entry>()

    override suspend fun read(name: String, input: JsonObject): ReadResult = withContext(io) {
        val key = name + "\u0000" + input
        val now = clock()
        cache.values.removeAll { now - it.at > ttlMs }
        val e = cache[key] ?: Entry(
            now,
            scope.async(io) {
                try {
                    source(name, input)
                } catch (c: CancellationException) {
                    throw c
                } catch (x: Exception) {
                    ReadResult("$name failed: ${x.message ?: x.javaClass.simpleName}", true)
                }
            },
        ).also { cache[key] = it }
        val r = e.result.await()
        if (r.isError && cache[key] === e) cache.remove(key)
        r
    }
}

/**
 * The matrix test (docs/plans/05-phase-0.md, "The matrix"): lanes of one model at one effort, each handling every event
 * the primary gets (chats, answers, digests, session ends; role-play recorded only, as the primary does), side by side
 * for a few days, compared afterwards (companion/bin/shadow-compare).
 *
 * Each lane has its own day thread on its model with its effort as the top-level effort of every turn (no per-task model
 * or effort, no effort messages), restarted as text past its family's limit and archived at the day's end without a
 * closing call (its lines its own "yesterday in brief"); it reads the primary's notebook and never changes it (its
 * notebook calls are dry runs, as all its writes); the real tutor's replies go into its thread as into the primary's.
 * Each lane has its own queue and worker, concurrent with the primary and each other: a lane never waits for the
 * primary's turns nor the primary for a lane's. All state changes happen on [io] (the primary's single thread); the
 * calls suspend. Its files are in [dir] (files/shadow-tutor/matrix/<lane>/), its calls and turns in a day log per lane,
 * carrying the lane's id, never in the primary's (so the primary's numbers and budget stay its own).
 */
class ShadowMatrix(
    private val scope: CoroutineScope,
    private val dir: File,
    private val host: Host,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
    private val io: CoroutineDispatcher,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val logLine: (String) -> Unit = {},
) {
    /** What the lanes take from the shadow tutor. */
    interface Host {
        val settings: ShadowSettings
        val hasKey: Boolean
        val prefix: Prefix
        /** The primary's notebook (the lanes read it, never write it). */
        val notebook: NotebookState
        /** A transport for a lane's turn (the lanes' own client). */
        fun transport(): TutorTransport
        /** A lane's tool runner: the shared reads, the guide, [notebook] (read-only), [onReply], [onGuide]. */
        fun tools(notebook: ShadowTools.NotebookHost, onReply: suspend (ShadowReply) -> Unit, onGuide: (String) -> Unit): ToolRunner
        fun event(id: String): ShadowEvent?
        /** The real tutor's replies of [day] (the primary's log keeps them). */
        fun real(day: String): List<RealReply>
        /** A turn's event as the primary sends it: the photo's image block, the event's (or the digest's) text. */
        fun blocks(t: TurnRecord, events: List<ShadowEvent>): List<JsonObject>
        fun where(): String?
        fun cost(model: String, usage: Usage): Double?
        /** The primary's day closing is due or running: a lane's new day waits for the notebook it rewrites. */
        fun closingPending(): Boolean
    }

    private inner class Lane(val id: String, val model: String, val effort: String) {
        val family = Lanes.familyOf(model)
        val store = ShadowStore(File(dir, id))
        var q: List<TurnRecord> = store.queue()
        var th: ThreadState? = store.thread()
        var worker: Job? = null
        val wake = Channel<Unit>(Channel.CONFLATED)
        val logs = mutableMapOf<String, DayLog>()
        fun log(day: String): DayLog = logs.getOrPut(day) { store.day(day) }
    }

    private val lanes = mutableMapOf<String, Lane>()
    private var active: Set<String> = emptySet()
    /** Why the lanes stopped on their own (a 401, the credit) until the key is checked again. */
    private var paused: String? = null

    /** The lanes as resolved now (the panel's grid says which are skipped and why). */
    var plan by mutableStateOf<List<LanePlan>>(emptyList())
        private set
    /** Today's log of each lane (the panel's spend per lane, its replies per event). */
    var today by mutableStateOf<Map<String, DayLog>>(emptyMap())
        private set
    /** Each lane's queue. */
    var queued by mutableStateOf<Map<String, List<TurnRecord>>>(emptyMap())
        private set
    /** What the matrix does ("off", "on: 9 lane(s)", "paused: …"). */
    var status by mutableStateOf("off")
        private set

    private fun now() = clock()
    private fun dayNow() = ShadowStore.dayOf(now(), zone())
    private fun line(msg: String) = logLine(Redact.of("matrix: $msg"))

    /** The lanes may run: the matrix and the shadow on, a key that works. */
    private fun running(s: ShadowSettings = host.settings): Boolean =
        s.enabled && s.matrix.enabled && host.hasKey && !s.keyRefused && !s.lowCredit && paused == null

    private fun statusNow(): String {
        val s = host.settings
        return when {
            !s.matrix.enabled -> "off"
            !s.enabled -> "off (the shadow is off)"
            !host.hasKey -> "no key"
            s.keyRefused -> "paused: the key was refused (401)"
            s.lowCredit -> "paused: the credit balance is too low"
            paused != null -> "paused: $paused"
            else -> "on: ${active.size} lane(s)" + lanes.values.count { it.worker?.isActive == true && it.q.any { t -> t.status == TurnStatus.RUNNING } }
                .takeIf { it > 0 }?.let { ", $it running" }.orEmpty()
        }
    }

    private fun publish() {
        val day = dayNow()
        today = lanes.values.filter { it.id in active || it.log(day).calls.isNotEmpty() || it.log(day).turns.isNotEmpty() }.associate { it.id to it.log(day) }
        queued = lanes.values.associate { it.id to it.q }
        status = statusNow()
    }

    /** The lanes kept on the phone (their directories), then the settings' (on [io]). */
    fun start() {
        dir.listFiles { f -> f.isDirectory && f.name.contains('@') }.orEmpty().forEach { f ->
            val model = f.name.substringBefore('@')
            val effort = f.name.substringAfter('@')
            if (f.name !in lanes) lanes[f.name] = Lane(f.name, model, effort)
        }
        refresh()
    }

    /**
     * The settings changed (on [io]): the lanes resolved again; a lane toggled off (or the matrix off) stops, its waiting
     * turns skipped; the lanes on are woken.
     */
    fun refresh() {
        val s = host.settings
        val next = Lanes.plan(s.matrix, s.listed)
        plan = next
        val ids = if (s.matrix.enabled) next.mapNotNull { it.id }.toSet() else emptySet()
        for (l in lanes.values) if (l.id !in ids && (l.worker != null || l.q.isNotEmpty())) {
            stop(l, if (s.matrix.enabled) "the lane was turned off" else "the matrix was turned off")
        }
        for (p in next) {
            val id = p.id ?: continue
            if (id in ids && id !in lanes) lanes[id] = Lane(id, p.model!!, p.effort)
        }
        active = ids
        publish()
        wakeAll()
    }

    /** The key was checked again (a new one, "Check again", a turn queued again): a pause of the lanes' own ends. */
    fun resume() {
        paused = null
        wakeAll()
    }

    fun wakeAll() {
        for (id in active) lanes[id]?.let(::wakeUp)
        status = statusNow()
    }

    private fun stop(l: Lane, why: String) {
        l.worker?.cancel()
        l.worker = null
        for (t in l.q) finish(l, t.copy(why = listOfNotNull(t.why, why).joinToString("; ")), TurnStatus.SKIPPED)
    }

    /**
     * The primary queued a turn for [events] (a chat, an answer, a digest, a session end …): each lane on gets one of its
     * own, on its thread, at its effort.
     */
    fun enqueue(events: List<ShadowEvent>, eventKind: String, task: Task, why: String?) {
        if (!host.settings.matrix.enabled) return
        for (id in active) {
            val l = lanes[id] ?: continue
            val t = TurnRecord(
                id = "t_" + UUID.randomUUID().toString().replace("-", "").take(12), eventIds = events.map { it.id }, kind = TurnKind.DAY,
                task = task, model = l.model, eventKind = eventKind, queuedAt = now(), why = why, effort = l.effort, lane = l.id,
            )
            saveQueue(l, l.q + t)
            wakeUp(l)
        }
    }

    // --- a lane's worker ------------------------------------------------------------------------------------------

    private fun wakeUp(l: Lane) {
        if (!running()) return
        if (l.worker?.isActive == true) {
            l.wake.trySend(Unit)
            return
        }
        l.worker = scope.launch(io) {
            try {
                work(l)
            } finally {
                status = statusNow()
            }
        }
    }

    /** Runs the lane's turns one at a time: the one begun first, a chat ahead of the rest, then in order. */
    private suspend fun work(l: Lane) {
        while (true) {
            if (!running() || l.id !in active) return
            val started = l.q.firstOrNull { it.startedAt != null }
            if (started == null) rollover(l)
            val at = now()
            val ready = l.q.filter { it.notBefore <= at && (started == null || it.id == started.id) }
            val next = ready.sortedWith(compareBy<TurnRecord>({ priority(it) }, { it.queuedAt })).firstOrNull()
            if (next == null) {
                val wait = (if (started != null) started.notBefore else l.q.minOfOrNull { it.notBefore })?.minus(at) ?: return
                withTimeoutOrNull(wait.coerceIn(500, 60_000)) { l.wake.receive() }
                continue
            }
            // a new day's thread begins with the notebook: after the primary's day closing has rewritten it (a while at most)
            if (next.startedAt == null && l.th?.day != dayNow() && host.closingPending() && now() - next.queuedAt < CLOSING_WAIT_MS) {
                withTimeoutOrNull(2_000) { l.wake.receive() }
                continue
            }
            try {
                runTurn(l, next)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                line("${l.id}: turn ${next.id} broke: ${e.message}")
                finish(l, next.copy(error = Redact.of(e.message ?: e.javaClass.simpleName)), TurnStatus.FAILED)
            }
        }
    }

    private fun priority(t: TurnRecord): Int = when (t.eventKind) {
        "chat" -> 1
        "answer" -> 2
        else -> 3
    }

    /** A new day: yesterday's thread archived as it is (no closing call), its lines the new one's "yesterday in brief". */
    private fun rollover(l: Lane) {
        val th = l.th ?: return
        if (th.day == dayNow()) return
        if (th.messages.isNotEmpty()) l.store.saveThreadArchive(th)
        saveThread(l, null)
        // the newest two archives are enough (yesterday's lines); the export never carries a lane's thread
        File(l.store.dir, "threads").listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }.dropLast(2).forEach { it.delete() }
        l.logs.keys.filter { it < th.day }.forEach { l.logs.remove(it) }
    }

    private fun newThread(l: Lane): ThreadState = ThreadState(
        day = dayNow(), model = l.model, startedAt = now(), yesterday = l.store.threadArchive()?.lines.orEmpty(), prefix = host.prefix.hash, effort = l.effort,
    )

    private suspend fun runTurn(l: Lane, t0: TurnRecord) {
        var t = t0.copy(status = TurnStatus.RUNNING, startedAt = t0.startedAt ?: now(), attempts = t0.attempts + 1)
        val resumed = t0.startedAt != null
        replace(l, t)
        val events = t.eventIds.mapNotNull(host::event)
        val notebook = object : ShadowTools.NotebookHost {
            // the primary's notebook, read-only: the lanes share one memory and stay comparable
            override fun note(line: String) = Unit
            override fun write(text: String) = Unit
            override val size: Int get() = host.notebook.text.length
        }
        val tools = host.tools(
            notebook,
            onReply = { r -> t = t.copy(replies = t.replies + r); replace(l, t) },
            onGuide = { id -> val d = l.log(dayNow()); saveLog(l, d.copy(guide = d.guide + (id to (d.guide[id] ?: 0) + 1))) },
        )
        val loop = TutorLoop(host.transport(), tools, maxCalls = 8, sleep = sleep)

        // its thread: a turn begun goes on where it began (also past the day's end), else today's
        var th = (if (resumed) l.th else null) ?: l.th?.takeIf { it.day == dayNow() } ?: newThread(l)
        var append = !resumed || th.messages.isEmpty()
        if (th.prefix != host.prefix.hash && th.messages.isNotEmpty()) {
            th = restarted(l, th, "The app was updated (its tools or prompt changed), so the thread restarted")
            addRestart(l, th.restarts.last())
            append = true
        }
        if (append) {
            val fresh = th.messages.isEmpty()
            val shown = TurnText.shownOf(host.real(th.day), th.shownUntil, events.minOfOrNull { it.at } ?: now(), t.eventIds)
            val blocks = buildList {
                if (fresh) add(textBlock(TurnText.notebook(host.notebook)))
                if (fresh) TurnText.lines("Yesterday in brief:", th.yesterday.takeLast(12)).takeIf { it.isNotEmpty() }?.let { add(textBlock(it)) }
                TurnText.shown(shown, zone()).takeIf { it.isNotEmpty() }?.let { add(textBlock(it)) }
                add(textBlock(TurnText.now(now(), zone(), host.where())))
                addAll(host.blocks(t, events))
            }
            // the lane's effort is its thread's top-level effort, the same for every turn: no effort messages
            th = th.copy(messages = Effort.append(th.messages, blocks, l.effort, null), shownUntil = maxOf(th.shownUntil, shown.maxOfOrNull { it.reply.at } ?: 0))
        }
        saveThread(l, th)
        val messages = th.messages.toMutableList()
        val save: suspend (List<JsonObject>) -> Unit = { m -> th = th.copy(messages = m); saveThread(l, th) }
        val before = messages.size
        val r = loop.run(
            host.prefix, l.model, t.task, messages, callsDone = t.callIds.size, save = save,
            effort = l.effort, maxTokens = t.task.maxTokensAt(l.effort),
            onCall = { c ->
                val call = callRecord(l, t, c, ShadowImages.count(messages))
                if (c.result != null) {
                    t = t.copy(callIds = t.callIds + call.id)
                    th = th.copy(tokens = call.usage.prompt)
                    saveThread(l, th)
                }
                val d = l.log(dayNow())
                saveLog(l, d.copy(calls = d.calls + call))
                replace(l, t)
                line("${l.id} ${call.eventKind}: ${call.stopReason ?: call.error} in:${call.usage.input} w:${call.usage.cacheWrite} r:${call.usage.cacheRead} out:${call.usage.output} \$${call.cost}")
            },
            onTool = { rec -> t = t.copy(toolCalls = t.toolCalls + Lanes.brief(rec)); replace(l, t) },
        )
        t = t.copy(text = messages.drop(before).filter { it.role() == "assistant" }.flatMap { it.blocks() }.filter { it.str("type") == "text" }.joinToString("\n") { it.str("text").orEmpty() }.take(500))
        val conversation = if (t.eventKind == "digest") "main" else events.firstOrNull()?.conversationId ?: "main"
        ReplyFallback.of(r, TurnKind.DAY, t.replies.isNotEmpty(), conversation, now())?.let { reply -> t = t.copy(replies = listOf(reply), replyFromText = true) }
        when (val end = r.end) {
            LoopEnd.Done -> finish(l, t, TurnStatus.DONE, th)
            LoopEnd.Limit -> finish(l, t, TurnStatus.LIMIT, th)
            LoopEnd.Truncated -> finish(l, t, TurnStatus.TRUNCATED, th)
            is LoopEnd.Refused -> finish(l, t.copy(error = "the model declined (refusal${end.details?.str("category")?.let { ": $it" } ?: ""})"), TurnStatus.REFUSED, th)
            // no budget of its own: a pause can only be the loop's (never asked here), so the turn waits
            LoopEnd.Paused -> replace(l, t.copy(status = TurnStatus.QUEUED))
            is LoopEnd.Failed -> failed(l, t, end.error, th)
        }
    }

    private fun callRecord(l: Lane, t: TurnRecord, c: CallOutcome, images: Int): CallRecord {
        val r = c.result
        val usage = r?.message?.usage ?: Usage()
        return CallRecord(
            id = "c_" + UUID.randomUUID().toString().replace("-", "").take(12), turnId = t.id, model = r?.message?.model ?: l.model,
            eventKind = t.eventKind, task = t.task, at = now(), usage = usage, firstTokenMs = r?.firstTokenMs, totalMs = r?.totalMs ?: 0,
            stopReason = r?.message?.stopReason, cost = r?.let { host.cost(it.message.model ?: l.model, usage) }, threadAfter = usage.prompt,
            thread = "day", error = c.error?.let { "${it.kind}${if (c.retried) " (retried)" else ""}: ${Redact.of(it.message.orEmpty()).take(300)}" },
            inputTransformations = r?.message?.inputTransformations?.take(500), cacheReport = r?.message?.rawUsage?.cacheReport,
            effort = l.effort, visibleChars = r?.message?.visibleChars(), images = images, lane = l.id,
        )
    }

    private fun failed(l: Lane, t: TurnRecord, e: ApiError, th: ThreadState) {
        val error = Redact.of(e.message ?: e.kind)
        when {
            // the same key as the primary's: the lanes stop until it's checked again
            e is ApiError.Auth -> {
                paused = "the key was refused (401)"
                replace(l, t.copy(status = TurnStatus.QUEUED, error = error))
            }
            e is ApiError.LowCredit -> {
                paused = if (e.limit) "a spend limit was reached" else "the credit balance is too low"
                replace(l, t.copy(status = TurnStatus.QUEUED, error = error))
            }
            // 429 after its retry-after, 529/5xx/the network with backoff: as the primary's
            e.retryable && t.attempts < MAX_ATTEMPTS -> {
                val wait = (e as? ApiError.RateLimited)?.retryAfterMs ?: (30_000L shl (t.attempts - 1).coerceIn(0, 5))
                replace(l, t.copy(status = TurnStatus.QUEUED, error = error, notBefore = now() + wait))
            }
            else -> finish(l, t.copy(error = error), TurnStatus.FAILED, th)
        }
        status = statusNow()
    }

    /** The turn is over: into the lane's day log; its thread gets the turn's line, a restart (as text) past the limit. */
    private fun finish(l: Lane, t0: TurnRecord, status: TurnStatus, th0: ThreadState? = l.th) {
        val t = t0.copy(status = status, endedAt = now())
        saveQueue(l, l.q.filterNot { it.id == t.id })
        val day = t.eventIds.firstOrNull()?.let(host::event)?.let { ShadowStore.dayOf(it.at, zone()) } ?: dayNow()
        val d = l.log(day)
        saveLog(l, d.copy(turns = d.turns.filterNot { it.id == t.id } + t))
        val cur = th0?.takeIf { it.day == dayNow() && t.startedAt != null }
        if (cur != null) {
            val at = Instant.ofEpochMilli(t.endedAt ?: now()).atZone(zone()).toLocalTime().withNano(0).withSecond(0)
            val said = t.replies.firstOrNull()?.text?.replace(Regex("\\s+"), " ")?.take(140)?.let { "«$it»" } ?: "no reply"
            var next = cur.copy(lines = (cur.lines + "$at ${t.eventKind} (${l.model}): $said").takeLast(60))
            if (next.tokens > Lanes.limit(l.family)) {
                next = restarted(l, next, "The thread is long (${next.tokens} tokens) and restarted")
                addRestart(l, next.restarts.last())
            }
            saveThread(l, next)
        }
        line("${l.id}: turn ${t.id} ${t.eventKind}: $status${if (t.replyFromText) " (reply as text)" else ""}${t.error?.let { " ($it)" } ?: ""}")
    }

    /**
     * A fresh thread after [th] (as the primary's, plan 5 "Compaction: our own"), without a closing call: the notebook,
     * the day so far, the last exchanges as text.
     */
    private fun restarted(l: Lane, th: ThreadState, why: String): ThreadState {
        val first = listOf(
            TurnText.notebook(host.notebook),
            "$why at ${th.tokens} tokens (the limit is ${Lanes.limit(l.family)}). " + TurnText.lines("The day so far:", th.lines.takeLast(20)),
            TurnText.lines("The last exchanges (as text):", lastExchanges(l)),
        ).filter { it.isNotBlank() }.map(::textBlock)
        return ThreadState(
            day = th.day, model = th.model, messages = listOf(buildJsonObject { put("role", "user"); put("content", JsonArray(first)) }),
            restarts = th.restarts + th.tokens, startedAt = now(), lines = th.lines, yesterday = th.yesterday, prefix = host.prefix.hash,
            effort = l.effort, shownUntil = th.shownUntil,
        )
    }

    private fun lastExchanges(l: Lane): List<String> {
        val day = dayNow()
        val real = host.real(day)
        return l.log(day).turns.filter { it.status == TurnStatus.DONE }.takeLast(3).map { t ->
            val e = t.eventIds.firstOrNull()?.let(host::event)
            val shown = real.filter { it.eventId != null && it.eventId in t.eventIds }.joinToString(" / ") { it.reply.text.take(600) }
            "${t.eventKind}: ${e?.text?.take(400).orEmpty()} → you replied: ${t.replies.joinToString(" / ") { it.text.take(600) }.ifBlank { "(nothing)" }}" +
                (if (shown.isNotBlank()) " → the learner was shown (another tutor answered): $shown" else "")
        }
    }

    private fun addRestart(l: Lane, tokens: Long) {
        val d = l.log(dayNow())
        saveLog(l, d.copy(restarts = d.restarts + tokens))
    }

    private fun replace(l: Lane, t: TurnRecord) {
        if (l.q.none { it.id == t.id }) return
        saveQueue(l, l.q.map { if (it.id == t.id) t else it })
    }

    private fun saveQueue(l: Lane, q: List<TurnRecord>) {
        l.q = q
        l.store.saveQueue(q)
        queued = queued + (l.id to q)
        status = statusNow()
    }

    private fun saveThread(l: Lane, th: ThreadState?) {
        l.th = th
        l.store.saveThread(th)
    }

    private fun saveLog(l: Lane, d: DayLog) {
        l.logs[d.day] = d
        l.store.saveDay(d)
        if (d.day == dayNow()) today = today + (l.id to d)
    }

    // --- the numbers and the export --------------------------------------------------------------------------------

    /** The lanes' spend on [day] (dollars), all lanes. */
    fun spent(day: String = dayNow()): Double = lanes.values.sumOf { l -> (l.logs[day] ?: l.store.day(day)).calls.sumOf { it.cost ?: 0.0 } }

    /** Each lane's log of [day] (those with one), for the export. */
    fun logs(day: String): Map<String, DayLog> = lanes.values.sortedBy { it.id }.associate { l -> l.id to (l.logs[day] ?: l.store.day(day)) }
        .filterValues { it.turns.isNotEmpty() || it.calls.isNotEmpty() }

    /** The matrix in the export: its settings and the lanes as resolved (a skipped one with why). */
    fun json(): JsonObject = buildJsonObject {
        put("settings", tutorJson.encodeToJsonElement(MatrixSettings.serializer(), host.settings.matrix))
        put("status", statusNow())
        put(
            "lanes",
            JsonArray(plan.map { p ->
                buildJsonObject {
                    p.id?.let { put("id", it) }
                    put("family", p.family.name)
                    put("effort", p.effort)
                    p.model?.let { put("model", it) }
                    p.skipped?.let { put("skipped", it) }
                }
            }),
        )
    }

    /** The tests': waits until every lane's worker stopped. */
    internal suspend fun awaitIdle(timeoutMs: Long = 20_000) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            withContext(io) {}
            if (lanes.values.none { it.worker?.isActive == true }) {
                withContext(io) {}
                if (lanes.values.none { it.worker?.isActive == true }) return
            }
            delay(5)
        }
        error("the matrix didn't get idle: ${statusNow()}")
    }

    private companion object {
        /** A turn that keeps failing is given up after this many tries (as the primary's). */
        const val MAX_ATTEMPTS = 5
        /** How long a lane's new day waits for the primary's day closing at most. */
        const val CLOSING_WAIT_MS = 3 * 60_000L
    }
}
