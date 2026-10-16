package si.lanisce.lani.tutor

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** One local day's shadow log: the events seen, the turns run, the calls made, the real tutor's replies, the guide's topics fetched. */
@Serializable
data class DayLog(
    val day: String,
    val events: List<ShadowEvent> = emptyList(),
    val turns: List<TurnRecord> = emptyList(),
    val calls: List<CallRecord> = emptyList(),
    val real: List<RealReply> = emptyList(),
    val guide: Map<String, Int> = emptyMap(),
    /** The thread's size each time it was restarted that day. */
    val restarts: List<Long> = emptyList(),
)

/**
 * The shadow tutor's files, in the app's private storage (files/shadow-tutor): its settings, queue, day's thread,
 * notebook, the pending digest, one log per day, a stateless turn's messages while it runs. Each written whole through
 * a temporary file, so a write cut off by Android leaves the last one.
 */
class ShadowStore(val dir: File) {
    init {
        dir.mkdirs()
    }

    private fun <T> read(name: String, s: KSerializer<T>): T? =
        runCatching { File(dir, name).takeIf { it.isFile }?.readText()?.let { tutorJson.decodeFromString(s, it) } }.getOrNull()

    private fun <T> write(name: String, s: KSerializer<T>, value: T) {
        val f = File(dir, name)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(tutorJson.encodeToString(s, value))
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }

    fun settings(): ShadowSettings = read("settings.json", ShadowSettings.serializer()) ?: ShadowSettings()
    fun saveSettings(s: ShadowSettings) = write("settings.json", ShadowSettings.serializer(), s)

    /** The turns waiting or running, in the order they came. */
    fun queue(): List<TurnRecord> = read("queue.json", ListSerializer(TurnRecord.serializer())).orEmpty()
    fun saveQueue(q: List<TurnRecord>) = write("queue.json", ListSerializer(TurnRecord.serializer()), q)

    fun thread(): ThreadState? = read("thread.json", ThreadState.serializer())
    fun saveThread(t: ThreadState?) = if (t == null) { File(dir, "thread.json").delete(); Unit } else write("thread.json", ThreadState.serializer(), t)

    /** A closed day's thread, kept (the export has it; the next day's first message has its lines in brief). */
    fun saveThreadArchive(t: ThreadState) = write("threads/${t.day}.json", ThreadState.serializer(), t)
    /** The last closed day's thread. */
    fun threadArchive(): ThreadState? = File(dir, "threads").listFiles { f -> f.name.endsWith(".json") }.orEmpty().maxByOrNull { it.name }
        ?.let { read("threads/${it.name}", ThreadState.serializer()) }

    fun notebook(): NotebookState = read("notebook.json", NotebookState.serializer()) ?: NotebookState()
    fun saveNotebook(n: NotebookState) = write("notebook.json", NotebookState.serializer(), n)

    /** The events waiting for the day's digest (their ids). */
    fun digest(): List<String> = read("digest.json", ListSerializer(String.serializer())).orEmpty()
    fun saveDigest(ids: List<String>) = write("digest.json", ListSerializer(String.serializer()), ids)

    fun day(day: String): DayLog = read("days/$day.json", DayLog.serializer()) ?: DayLog(day)
    fun saveDay(log: DayLog) = write("days/${log.day}.json", DayLog.serializer(), log)
    /** The days with a log, oldest first. */
    fun days(): List<String> = File(dir, "days").listFiles { f -> f.name.endsWith(".json") }.orEmpty().map { it.name.removeSuffix(".json") }.sorted()

    /** A stateless turn's messages so far (its steps saved), for its resume. */
    fun turnMessages(id: String): List<JsonObject>? = read("turns/$id.json", ListSerializer(JsonObject.serializer()))
    fun saveTurnMessages(id: String, m: List<JsonObject>) = write("turns/$id.json", ListSerializer(JsonObject.serializer()), m)
    fun dropTurnMessages(id: String) { File(dir, "turns/$id.json").delete() }

    /** When the app was last seen leaving the screen (epoch ms), for "the app opens after two hours or more". */
    fun lastAway(): Long = File(dir, "away").takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull() ?: 0
    fun saveLastAway(at: Long) = File(dir, "away").writeText(at.toString())

    companion object {
        /** The tutor's local day: from 04:00 to 04:00 (plan 5, "The day's thread"). */
        fun dayOf(ms: Long, zone: ZoneId): String = Instant.ofEpochMilli(ms).atZone(zone).minusHours(4).toLocalDate().toString()
    }
}

/** A day's numbers (plan 5, "What the prototype measures"). */
@Serializable
data class DayStats(
    val calls: Int,
    val turns: Int,
    /** Calls after more than 5 and more than 60 minutes without one on the same model (the cache's lifetimes). */
    val coldStarts5m: Int,
    val coldStarts60m: Int,
    val usage: Usage,
    val spend: Double,
    val byModel: Map<String, Double>,
    val byKind: Map<String, Double>,
    val errors: Int,
    val refusals: Int,
    val restarts: List<Long>,
    val notebookChars: Int,
    val guide: Map<String, Int>,
    /** The median time to the first token, and to the whole response (ms). */
    val firstTokenMedianMs: Long?,
    val totalMedianMs: Long?,
    val repliesShadow: Int,
    val repliesReal: Int,
    /**
     * The calls whose usage gave the cache writes by lifetime (usage.cache_creation), and those with only their total
     * (cache_creation_input_tokens, filed as 5-minute writes): the cache question.
     */
    val cacheBreakdown: Int = 0,
    val cacheTotalOnly: Int = 0,
    /** Turns that never called `reply` and whose text became the reply ([TurnRecord.replyFromText]). */
    val repliesFromText: Int = 0,
    /** The numbers by model and effort ([Effort.key]: "claude-haiku-5-5 · high"), for the effort comparison. */
    val byModelEffort: Map<String, RunStats> = emptyMap(),
) {
    companion object {
        fun of(log: DayLog, notebookChars: Int, before: Map<String, Long> = emptyMap()): DayStats {
            val ok = log.calls.filter { it.error == null }.sortedBy { it.at }
            val last = before.toMutableMap()
            var cold5 = 0
            var cold60 = 0
            for (c in ok) {
                val gap = last[c.model]?.let { c.at - it }
                if (gap == null || gap > 60 * 60_000L) cold60++
                if (gap == null || gap > 5 * 60_000L) cold5++
                last[c.model] = c.at
            }
            val sum = ok.fold(Usage()) { a, c -> Usage(a.input + c.usage.input, a.cacheWrite5m + c.usage.cacheWrite5m, a.cacheWrite1h + c.usage.cacheWrite1h, a.cacheRead + c.usage.cacheRead, a.output + c.usage.output) }
            fun median(xs: List<Long>) = xs.sorted().takeIf { it.isNotEmpty() }?.let { it[it.size / 2] }
            return DayStats(
                calls = ok.size,
                turns = log.turns.count { it.callIds.isNotEmpty() },
                coldStarts5m = cold5,
                coldStarts60m = cold60,
                usage = sum,
                spend = ok.sumOf { it.cost ?: 0.0 },
                byModel = ok.groupBy { it.model }.mapValues { (_, cs) -> cs.sumOf { it.cost ?: 0.0 } },
                byKind = ok.groupBy { it.eventKind }.mapValues { (_, cs) -> cs.sumOf { it.cost ?: 0.0 } },
                errors = log.calls.count { it.error != null },
                refusals = ok.count { it.stopReason == "refusal" },
                restarts = log.restarts,
                notebookChars = notebookChars,
                guide = log.guide,
                firstTokenMedianMs = median(ok.mapNotNull { it.firstTokenMs }),
                totalMedianMs = median(ok.map { it.totalMs }),
                repliesShadow = log.turns.sumOf { it.replies.size },
                repliesReal = log.real.size,
                cacheBreakdown = ok.count { it.cacheReport == RawUsage.BREAKDOWN },
                cacheTotalOnly = ok.count { it.cacheReport == RawUsage.TOTAL },
                repliesFromText = log.turns.count { it.replyFromText },
                byModelEffort = RunStats.of(ok, log.turns),
            )
        }
    }
}

/**
 * One model at one effort on a day: its [calls] and [turns] (with a call), [spend], [output] tokens (thinking counted
 * in), of them [thinking] an estimate ([CallRecord.thinkingEstimate]); how its turns ended: a reply through the tool
 * ([repliesByTool]), the text taken as the reply ([repliesFromText]), done without either ([noReply]; a digest may say
 * nothing). Closings don't reply by design: counted in calls, not in the replies.
 */
@Serializable
data class RunStats(
    val calls: Int = 0,
    val turns: Int = 0,
    val spend: Double = 0.0,
    val output: Long = 0,
    val thinking: Long = 0,
    val repliesByTool: Int = 0,
    val repliesFromText: Int = 0,
    val noReply: Int = 0,
) {
    companion object {
        /** [calls] (the day's answered ones) and [turns] by [Effort.key]. */
        fun of(calls: List<CallRecord>, turns: List<TurnRecord>): Map<String, RunStats> {
            val byCalls = calls.groupBy { Effort.key(it.model, it.effort) }
            val ran = turns.filter { it.callIds.isNotEmpty() }
            val byTurns = ran.groupBy { Effort.key(it.model, it.effort) }
            return (byCalls.keys + byTurns.keys).sorted().associateWith { k ->
                val cs = byCalls[k].orEmpty()
                val ts = byTurns[k].orEmpty().filter { it.kind != TurnKind.CLOSING }
                RunStats(
                    calls = cs.size,
                    turns = byTurns[k].orEmpty().size,
                    spend = cs.sumOf { it.cost ?: 0.0 },
                    output = cs.sumOf { it.usage.output },
                    thinking = cs.sumOf { it.thinkingEstimate ?: 0 },
                    repliesByTool = ts.count { it.replies.isNotEmpty() && !it.replyFromText },
                    repliesFromText = ts.count { it.replyFromText },
                    noReply = ts.count { it.status == TurnStatus.DONE && it.replies.isEmpty() },
                )
            }
        }
    }
}
