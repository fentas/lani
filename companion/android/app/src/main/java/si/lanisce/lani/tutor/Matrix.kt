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
import java.time.ZoneId
import java.util.UUID

/**
 * A lane of the matrix (docs/plans/05-phase-0.md, "The matrix"): one model family at one effort, beside the primary.
 * [Lanes.plan] resolves the family to the model the key lists. An OpenRouter lane names its [model] (the learner picked
 * it from OpenRouter's list; its family is [Family.ROUTED]); its [effort] is "none" for a model that takes no effort.
 */
@Serializable
data class LaneSpec(val family: Family, val effort: String, val model: String? = null) {
    companion object {
        /** An OpenRouter lane on [model] at [effort]. */
        fun routed(model: String, effort: String) = LaneSpec(Family.ROUTED, effort, model)
    }
}

/**
 * The matrix's settings ([ShadowSettings.matrix]): off by default, the default lanes preselected (Jan asked for Haiku at
 * xhigh and max). No budget of its own: nothing stops the lanes but the switch, a lane's toggle, a model the key doesn't
 * list, and the key refused or out of credit (Jan, 2026-10-18: "we have $200 for this month").
 */
@Serializable
data class MatrixSettings(
    val enabled: Boolean = false,
    val lanes: List<LaneSpec> = DEFAULT_LANES,
    /**
     * OpenAI's default lanes ([OPENAI_LANES]) were added once, when an OpenAI credential was first set: a lane toggled
     * off afterwards stays off.
     */
    val openaiOffered: Boolean = false,
    /** Gemini's default lanes ([GEMINI_LANES]) were added once, when a Gemini key was first set. */
    val geminiOffered: Boolean = false,
    /** OpenRouter's default lanes ([OPENROUTER_LANES]) were added once, at the first OpenRouter sign-in. */
    val openrouterOffered: Boolean = false,
) {
    companion object {
        val DEFAULT_LANES = listOf(
            LaneSpec(Family.HAIKU, Effort.MEDIUM), LaneSpec(Family.HAIKU, Effort.HIGH), LaneSpec(Family.HAIKU, Effort.XHIGH), LaneSpec(Family.HAIKU, Effort.MAX),
            LaneSpec(Family.SONNET, Effort.LOW), LaneSpec(Family.SONNET, Effort.MEDIUM), LaneSpec(Family.SONNET, Effort.HIGH),
            LaneSpec(Family.OPUS, Effort.LOW), LaneSpec(Family.OPUS, Effort.MEDIUM),
        )

        /**
         * OpenAI's lanes, added when an OpenAI credential is first set: the same places in the grid as Anthropic's nine
         * (the small family at medium to max, the mid at low to high, the top at low and medium), so the providers
         * compare tier by tier.
         */
        val OPENAI_LANES = listOf(
            LaneSpec(Family.LUNA, Effort.MEDIUM), LaneSpec(Family.LUNA, Effort.HIGH), LaneSpec(Family.LUNA, Effort.XHIGH), LaneSpec(Family.LUNA, Effort.MAX),
            LaneSpec(Family.SOL, Effort.LOW), LaneSpec(Family.SOL, Effort.MEDIUM), LaneSpec(Family.SOL, Effort.HIGH),
            LaneSpec(Family.ASTRA, Effort.LOW), LaneSpec(Family.ASTRA, Effort.MEDIUM),
        )

        /**
         * Gemini's lanes, added when a Gemini key is first set: 4 Argon at each of its thinking levels (low, medium, high),
         * and 3.8 Flash at its default (medium). On the free tier Argon's are skipped (no free tier).
         */
        val GEMINI_LANES = listOf(
            LaneSpec(Family.FLASH, Effort.MEDIUM),
            LaneSpec(Family.ARGON, Effort.LOW), LaneSpec(Family.ARGON, Effort.MEDIUM), LaneSpec(Family.ARGON, Effort.HIGH),
        )

        /** OpenRouter's lanes, added at the first sign-in: its tier defaults ([OpenRouterModels.DEFAULTS]) at medium. */
        val OPENROUTER_LANES = OpenRouterModels.DEFAULTS.map { LaneSpec.routed(it, Effort.MEDIUM) }
    }
}

/** A lane as the credential's models resolve it: its [model], or why it's [skipped] (the panel says so). */
data class LanePlan(val family: Family, val effort: String, val model: String? = null, val skipped: String? = null) {
    /** "claude-sonnet-5-5@low", "openai:gpt-6.1-sol@medium"; null when skipped. */
    val id: String? get() = model?.takeIf { skipped == null }?.let { Lanes.id(it, effort) }
}

object Lanes {
    /**
     * A lane's id: "<model>@<effort>", with the provider in front when it isn't Anthropic ("openai:gpt-6.1-sol@medium",
     * "gemini:gemini-4-argon@high", "openrouter:google/gemini-4-argon@medium"). Anthropic's lanes keep the ids they had
     * before the provider layer ("claude-sonnet-5-5@low"), so the days recorded already compare with the new ones.
     */
    fun id(model: String, effort: String): String =
        ProviderId.of(model).let { p -> if (p == ProviderId.ANTHROPIC) "" else "${p.id}:" } + "$model@$effort"

    /** A lane id's model and effort ([id]'s inverse; a lane's directory name too: [dir]). */
    fun parse(id: String): Pair<String, String> {
        val name = id.replace(DIR_SLASH, '/')
        return name.substringBeforeLast('@').substringAfter(':') to name.substringAfterLast('@')
    }

    /** A lane's directory under the matrix's: its id, an OpenRouter model's "/" as "~" (one directory, not two). */
    fun dir(id: String): String = id.replace('/', DIR_SLASH)

    private const val DIR_SLASH = '~'

    /** A lane's thread restarts past this many tokens: its tier's preset's limit (small 20k, mid 30k, top 60k). */
    fun limit(f: Family): Int = when (f.tier) {
        0 -> Preset.ECONOMY.threadLimit
        1 -> Preset.BALANCED.threadLimit
        else -> Preset.BEST.threadLimit
    }

    /** The family of a lane's [model] (its id names it; an OpenRouter model's: [Family.ROUTED]). */
    fun familyOf(model: String): Family = Family.ofModel(model) ?: if (ProviderId.of(model) == ProviderId.OPENROUTER) Family.ROUTED else Family.HAIKU

    /** Why a provider's lanes can't run without a credential (the panel and the export say so). */
    private fun missing(p: ProviderId): String = when (p) {
        ProviderId.OPENAI -> "no OpenAI credential (sign in with ChatGPT or keep an API key)"
        ProviderId.GEMINI -> "no Gemini key (Google AI Studio)"
        ProviderId.OPENROUTER -> "not signed in with OpenRouter"
        ProviderId.ANTHROPIC -> "no key"
    }

    /**
     * The lanes of [m] (ModelCatalog.own: the family's own model, never another family's), in the grid's order: an
     * Anthropic lane by the Claude key's [listed] models, another provider's by [others]' list (a provider missing from
     * it has no credential, so its lanes are skipped); an OpenRouter lane is its picked model, when OpenRouter's list has
     * it. A lane whose model the credential doesn't list, or that doesn't take the effort, is skipped; on the Gemini free
     * tier ([geminiFree]) Gemini 4 Argon's lanes are (it has no free tier).
     */
    fun plan(m: MatrixSettings, listed: List<ModelInfo>, others: Map<ProviderId, List<ModelInfo>> = emptyMap(), geminiFree: Boolean = false): List<LanePlan> {
        val catalogs = mapOf(ProviderId.ANTHROPIC to ModelCatalog(listed)) + others.mapValues { ModelCatalog(it.value) }
        return m.lanes.distinct().sortedWith(compareBy({ it.family.ordinal }, { it.model.orEmpty() }, { Effort.ALL.indexOf(it.effort) })).map { spec ->
            val p = spec.family.provider
            val catalog = catalogs[p]
            val model = if (spec.family == Family.ROUTED) spec.model?.takeIf { id -> catalog != null && (catalog.listed.isEmpty() || catalog.listed.any { it.id == id }) } else catalog?.own(spec.family)
            val levels = model?.let(ModelCatalog::efforts).orEmpty()
            when {
                catalog == null -> LanePlan(spec.family, spec.effort, spec.model, skipped = missing(p))
                spec.family == Family.ROUTED && spec.model == null -> LanePlan(spec.family, spec.effort, skipped = "an OpenRouter lane without a model")
                model == null && spec.family == Family.ROUTED -> LanePlan(spec.family, spec.effort, spec.model, "OpenRouter's list has no ${spec.model} (or it takes no tools)")
                model == null -> LanePlan(spec.family, spec.effort, skipped = "the ${if (p == ProviderId.ANTHROPIC) "key" else "${p.label} credential"} lists no ${spec.family.label.lowercase()} model (${spec.family.preferred.first()})")
                geminiFree && spec.family == Family.ARGON -> LanePlan(spec.family, spec.effort, model, "Gemini 4 Argon has no free tier (the key is on it)")
                spec.effort == Effort.NONE && levels.isEmpty() -> LanePlan(spec.family, spec.effort, model)
                spec.effort !in levels -> LanePlan(spec.family, spec.effort, model, "$model takes no ${spec.effort}")
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
    /** A read of the node, with a lane's dry-run card writes laid over the deck ([CardOverlay]). */
    private val source: suspend (String, JsonObject, List<JsonObject>) -> ReadResult,
) : ReadSource {
    private class Entry(val at: Long, val result: Deferred<ReadResult>)

    private val cache = mutableMapOf<String, Entry>()

    override suspend fun read(name: String, input: JsonObject): ReadResult = read(name, input, emptyList())

    /** The same read with the same writes (a lane's own: each lane's card fixes are its own) is shared. */
    override suspend fun read(name: String, input: JsonObject, writes: List<JsonObject>): ReadResult = withContext(io) {
        val key = name + "\u0000" + input + "\u0000" + writes
        val now = clock()
        cache.values.removeAll { now - it.at > ttlMs }
        val e = cache[key] ?: Entry(
            now,
            scope.async(io) {
                try {
                    source(name, input, writes)
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
        val prefix: Prefix
        /** The primary's notebook (the lanes read it, never write it). */
        val notebook: NotebookState
        /** Whether [provider] has a credential (Anthropic: the key; OpenAI: an API key or the ChatGPT sign-in). */
        fun ready(provider: ProviderId): Boolean
        /** Why [provider]'s calls are stopped (its credential refused, the money or the plan's allowance out), or null. */
        fun blocked(provider: ProviderId): String?
        /** The models [provider]'s credential lists (null: none set). */
        fun listed(provider: ProviderId): List<ModelInfo>?
        /** The Gemini key is on the free tier (Argon's lanes are skipped, the calls cost $0 and are paced). */
        fun geminiFree(): Boolean = false
        /** The provider for a lane's turn on [model] (the lanes' own client). */
        fun provider(model: String): TutorProvider
        /**
         * A lane's tool runner: the shared reads, the guide, [notebook] (read-only), [onReply], [onGuide]; [writes] the
         * lane's dry-run card writes of the day ([CardOverlay]), [events] the turn's (what a reply may carry: [ReplyData]).
         */
        fun tools(
            notebook: ShadowTools.NotebookHost, onReply: suspend (ShadowReply) -> Unit, onGuide: (String) -> Unit,
            writes: List<JsonObject> = emptyList(), events: List<ShadowEvent> = emptyList(),
        ): ToolRunner

        /** The phone's network: while it's down the lanes' queues wait for it ([Online]). */
        val online: Online get() = Online.ALWAYS
        fun event(id: String): ShadowEvent?
        /** The real tutor's replies of [day] (the primary's log keeps them). */
        fun real(day: String): List<RealReply>
        /** A turn's event as the primary sends it: the photo's image block, the event's (or the digest's) text. */
        fun blocks(t: TurnRecord, events: List<ShadowEvent>): List<JsonObject>
        fun where(): String?
        /**
         * What [usage] on [model] cost paid by [billing] ([Priced]: 0 and the API-equivalent with the ChatGPT plan or the
         * Gemini free tier; [reported] OpenRouter's own price of the call).
         */
        fun cost(model: String, usage: Usage, billing: String, reported: Double? = null): Priced
        /** The primary's day closing is due or running: a lane's new day waits for the notebook it rewrites. */
        fun closingPending(): Boolean
    }

    private inner class Lane(val id: String, val model: String, val effort: String) {
        val family = Lanes.familyOf(model)
        val provider = ProviderId.of(model)
        val store = ShadowStore(File(dir, Lanes.dir(id)))
        var q: List<TurnRecord> = store.queue()
        var th: ThreadState? = store.thread()
        var worker: Job? = null
        val wake = Channel<Unit>(Channel.CONFLATED)
        val logs = mutableMapOf<String, DayLog>()
        fun log(day: String): DayLog = logs.getOrPut(day) { store.day(day) }
    }

    private val lanes = mutableMapOf<String, Lane>()
    private var active: Set<String> = emptySet()
    /** Why a provider's lanes stopped on their own (a 401, the credit, the plan's allowance) until it's checked again. */
    private val paused = mutableMapOf<ProviderId, String>()

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

    /** Why [p]'s lanes can't run now, or null: no credential, the credential refused or out, a pause of the lanes' own. */
    private fun stopped(p: ProviderId): String? = when {
        !host.ready(p) -> when (p) {
            ProviderId.ANTHROPIC -> "no key"
            ProviderId.OPENAI -> "no OpenAI credential"
            ProviderId.GEMINI -> "no Gemini key"
            ProviderId.OPENROUTER -> "not signed in with OpenRouter"
        }
        else -> host.blocked(p) ?: paused[p]
    }

    /** Lane [l] may run: the matrix and the shadow on, its provider's credential works. */
    private fun running(l: Lane, s: ShadowSettings = host.settings): Boolean = s.enabled && s.matrix.enabled && stopped(l.provider) == null

    private fun statusNow(): String {
        val s = host.settings
        if (!s.matrix.enabled) return "off"
        if (!s.enabled) return "off (the shadow is off)"
        val providers = active.mapNotNull { lanes[it]?.provider }.toSet().ifEmpty { setOf(ProviderId.ANTHROPIC) }
        val why = providers.mapNotNull { p -> stopped(p)?.let { p to it } }
        val running = lanes.values.count { it.worker?.isActive == true && it.q.any { t -> t.status == TurnStatus.RUNNING } }
        val on = "on: ${active.count { id -> lanes[id]?.provider?.let { stopped(it) == null } == true }} lane(s)" +
            running.takeIf { it > 0 }?.let { ", $it running" }.orEmpty()
        return when {
            // as before the providers: "no key" alone, the rest as pauses
            why.size == 1 && providers.size == 1 && why.single().second == "no key" -> "no key"
            why.size == providers.size && providers.size == 1 -> "paused: ${why.single().second}"
            why.size == providers.size -> "paused: " + why.joinToString("; ") { "${it.first.id}: ${it.second}" }
            why.isNotEmpty() -> "$on; " + why.joinToString("; ") { "${it.first.id} paused: ${it.second}" }
            else -> on
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
            val (model, effort) = Lanes.parse(f.name)
            val id = Lanes.id(model, effort)
            if (id !in lanes) lanes[id] = Lane(id, model, effort)
        }
        refresh()
    }

    /**
     * The settings changed (on [io]): the lanes resolved again; a lane toggled off (or the matrix off) stops, its waiting
     * turns skipped; the lanes on are woken.
     */
    fun refresh() {
        val s = host.settings
        val others = listOf(ProviderId.OPENAI, ProviderId.GEMINI, ProviderId.OPENROUTER).mapNotNull { p -> host.listed(p)?.let { p to it } }.toMap()
        val next = Lanes.plan(s.matrix, s.listed, others, host.geminiFree())
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

    /**
     * A credential was checked again (a new one, "Check again", a sign-in, a turn queued again): a pause of the lanes'
     * own ends, [provider]'s (null: every provider's).
     */
    fun resume(provider: ProviderId? = null) {
        if (provider == null) paused.clear() else paused.remove(provider)
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
        if (!running(l)) return
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
            if (!running(l) || l.id !in active) return
            // the phone offline: the lane's queue waits for the network, no turn fails
            if (l.q.isNotEmpty() && !host.online.now()) {
                withTimeoutOrNull(60_000) { host.online.await() }
                continue
            }
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

    private fun newThread(l: Lane, provider: TutorProvider): ThreadState = ThreadState(
        day = dayNow(), model = l.model, startedAt = now(), yesterday = l.store.threadArchive()?.lines.orEmpty(), prefix = provider.prefixHash(host.prefix), effort = l.effort,
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
            // the lane's own dry-run card writes of the day (its other turns', this one's before a resume)
            writes = CardOverlay.writes(l.log(dayNow()).turns.filter { it.id != t0.id }.flatMap { it.toolCalls } + t0.toolCalls),
            events = events,
        )
        val provider = host.provider(l.model)
        val format = provider.format
        val hash = provider.prefixHash(host.prefix)
        val loop = TutorLoop(provider, tools, maxCalls = 8, sleep = sleep, online = host.online::now)

        // its thread: a turn begun goes on where it began (also past the day's end), else today's
        var th = (if (resumed) l.th else null) ?: l.th?.takeIf { it.day == dayNow() } ?: newThread(l, provider)
        var append = !resumed || th.messages.isEmpty()
        if (th.prefix != hash && th.messages.isNotEmpty()) {
            val why = if (l.provider == ProviderId.ANTHROPIC) "The app was updated (its tools or prompt changed)" else "The app was updated (its tools or prompt changed) or its credential changed"
            th = restarted(l, th, "$why, so the thread restarted", hash)
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
            th = th.copy(messages = format.append(th.messages, blocks, l.effort, null), shownUntil = maxOf(th.shownUntil, shown.maxOfOrNull { it.reply.at } ?: 0))
        }
        saveThread(l, th)
        val messages = th.messages.toMutableList()
        val save: suspend (List<JsonObject>) -> Unit = { m -> th = th.copy(messages = m); saveThread(l, th) }
        val before = messages.size
        val r = loop.run(
            host.prefix, l.model, t.task, messages, callsDone = t.callIds.size, save = save,
            effort = l.effort, maxTokens = t.task.maxTokensAt(l.effort),
            onCall = { c ->
                val call = callRecord(l, t, c, ShadowImages.count(messages), provider.billing)
                if (c.step != null) {
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
            owed = Owed.of(t.eventKind, t.kind) { t.replies.isNotEmpty() },
        )
        t = t.copy(text = Threads.text(messages.drop(before)).take(500))
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

    private fun callRecord(l: Lane, t: TurnRecord, c: CallOutcome, images: Int, billing: String): CallRecord {
        val priced = c.step?.let { host.cost(it.model ?: l.model, it.usage, billing, it.cost) }
        return Meter.record(t, c, now(), "day", priced, l.effort, model = l.model, images = images, lane = l.id, provider = l.provider, billing = billing, transformationsMax = 500)
    }

    private fun failed(l: Lane, t: TurnRecord, e: ApiError, th: ThreadState) {
        val error = Redact.of(e.message ?: e.kind)
        when {
            // offline: back to the queue, which waits for the network; the try isn't counted
            Failures.offline(e, host.online) -> replace(l, t.copy(status = TurnStatus.QUEUED, error = error, attempts = (t.attempts - 1).coerceAtLeast(0)))
            // the same credential as every lane of its provider's (Anthropic's: the primary's key): they stop until it's
            // checked again
            e is ApiError.Auth -> {
                paused[l.provider] = when (l.provider) {
                    ProviderId.ANTHROPIC -> "the key was refused (401)"
                    ProviderId.OPENAI -> "the OpenAI credential was refused (sign in or check again)"
                    ProviderId.GEMINI -> "the Gemini key was refused (check it in Google AI Studio)"
                    ProviderId.OPENROUTER -> "the OpenRouter key was refused (sign in again)"
                }
                replace(l, t.copy(status = TurnStatus.QUEUED, error = error))
            }
            e is ApiError.LowCredit -> {
                paused[l.provider] = if (l.provider == ProviderId.ANTHROPIC || l.provider == ProviderId.OPENAI) when {
                    e.allowance -> "the ChatGPT plan's usage limit was reached (ChatGPT settings → Usage)"
                    e.limit -> "a spend limit was reached"
                    else -> "the credit balance is too low"
                } else Failures.creditWhy(e, l.provider)
                replace(l, t.copy(status = TurnStatus.QUEUED, error = error))
            }
            // 429 after its retry-after, 529/5xx/the network with backoff: as the primary's
            Failures.retryAt(e, t.attempts, now()) != null -> replace(l, t.copy(status = TurnStatus.QUEUED, error = error, notBefore = Failures.retryAt(e, t.attempts, now())!!))
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
            var next = cur.copy(lines = (cur.lines + DayThreads.line(t, t.endedAt ?: now(), zone(), l.model)).takeLast(60))
            if (next.tokens > Lanes.limit(l.family)) {
                next = restarted(l, next, "The thread is long (${next.tokens} tokens) and restarted", next.prefix)
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
    private fun restarted(l: Lane, th: ThreadState, why: String, prefix: String): ThreadState =
        DayThreads.restarted(th, DayThreads.restartBlocks(host.notebook, why, th.tokens, Lanes.limit(l.family), th.lines, lastExchanges(l)), now(), prefix, l.effort)

    private fun lastExchanges(l: Lane): List<String> {
        val day = dayNow()
        return DayThreads.exchanges(l.log(day).turns.filter { it.status == TurnStatus.DONE }, host::event, host.real(day))
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
                    put("provider", p.family.provider.id)
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
        /** How long a lane's new day waits for the primary's day closing at most. */
        const val CLOSING_WAIT_MS = 3 * 60_000L
    }
}
