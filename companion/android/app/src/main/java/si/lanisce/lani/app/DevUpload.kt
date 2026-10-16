package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import si.lanisce.lani.tutor.Redact
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * The developer's data, on the node by itself (companion/README.md "Developer data on the node"): the shadow tutor's
 * export (POST /dev/shadow, the share sheet's `lani.shadow-export/v0`) and the app's own log ([AppLog], POST /dev/logs),
 * both cleaned of secrets ([Redact]: the API key, the device token, anything shaped like one). The node keeps them under
 * <data>/app/dev/, which its data repository never commits, and the developer reads them there as files.
 *
 * Only while the developer setting is on (the version line tapped seven times) and the app is paired, and only to the
 * paired node over the app's own authenticated client ([Node]): after the shadow's day closed ([onClosing]: its
 * notebook rewritten), when the app goes to the background ([onBackground], at most every [BACKGROUND_EVERY_MS]) and by
 * the shadow panel's "📤 Pošlji vozlišču · Send to the node" ([sendNow]). Otherwise nothing is read and nothing is sent.
 */
class DevUpload(
    private val scope: CoroutineScope,
    private val stateFile: File,
    /** The developer setting is on (ShadowSettings.developer). */
    private val developer: () -> Boolean,
    /** The paired node; null when the app isn't paired. */
    private val node: suspend () -> Node?,
    /** The shadow's export as JSON, cleaned of the secrets given, at most the bytes given (ShadowTutor.exportText). */
    private val export: suspend (secrets: List<String>, maxBytes: Int) -> String,
    /** This process's log lines ([AppLog.read]). */
    private val readLog: () -> AppLog.Read,
    /** The app and the phone, for the log's header ("si.lanisce.lani 0.2.9 (9), Android 16 (API 36), pid 1234"). */
    private val about: () -> String = { "" },
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    /** A log line of its own (tag [TAG]): what was sent, never a secret. */
    private val logLine: (String) -> Unit = {},
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** The paired node, as far as the uploads need it (data/Bridge.kt devUpload). */
    interface Node {
        /** What its requests carry (the device token): never in what is sent. */
        val secrets: List<String>
        /** POSTs [body] as [type] to [path]: the status code; throws when the node can't be reached. */
        suspend fun post(path: String, body: String, type: String): Int
    }

    /** What an upload did: [sent] when it reached the node; [summary] for the panel and the log. */
    data class Outcome(val sent: Boolean, val summary: String)

    /** What the uploads remember (files/dev-upload.json). */
    @Serializable
    data class State(
        /** When the last upload began (the background's throttle). */
        val lastAttemptAt: Long = 0,
        /** The newest log line the node took (its time, epoch ms): the next upload sends the lines after it. */
        val logSince: Long = 0,
        /** The last upload's summary. */
        val last: String? = null,
    )

    private val mutex = Mutex()
    private var state = load()

    /** The last upload's summary (the panel shows it). */
    var last by mutableStateOf(state.last)
        private set

    private fun load(): State = runCatching { stateFile.takeIf { it.isFile }?.readText()?.let { JSON.decodeFromString(State.serializer(), it) } }.getOrNull() ?: State()

    private fun save(s: State) {
        state = s
        runCatching {
            stateFile.parentFile?.mkdirs()
            val tmp = File(stateFile.parentFile, stateFile.name + ".tmp")
            tmp.writeText(JSON.encodeToString(State.serializer(), s))
            if (!tmp.renameTo(stateFile)) {
                stateFile.delete()
                tmp.renameTo(stateFile)
            }
        }
    }

    /** The shadow's day closed (the notebook rewritten): its export and the log go to the node. */
    fun onClosing(): Job = scope.launch { send("the day closed", throttled = false) }

    /** The app went to the background: an upload, unless one began less than [BACKGROUND_EVERY_MS] ago. */
    fun onBackground(): Job = scope.launch { send("the app went to the background", throttled = true) }

    /** The panel's "📤 Pošlji vozlišču · Send to the node": an upload now. */
    suspend fun sendNow(): Outcome = send("asked in the panel", throttled = false)

    /** QA's hook "dev:again": the next background upload doesn't wait for the throttle. */
    fun forgetThrottle() = scope.launch(io) { save(state.copy(lastAttemptAt = 0)) }

    private suspend fun send(why: String, throttled: Boolean): Outcome = withContext(io) {
        // the developer's setting and the pairing first: for every other learner nothing is read, nothing is sent
        if (!developer()) return@withContext Outcome(false, "not sent: the developer setting is off").also { quiet(it) }
        val n = node() ?: return@withContext Outcome(false, "not sent: the app isn't paired").also { quiet(it) }
        val now = clock()
        if (throttled && now - state.lastAttemptAt in 0 until BACKGROUND_EVERY_MS) {
            return@withContext Outcome(false, "not sent: the last upload began ${(now - state.lastAttemptAt) / 60_000} min ago").also { quiet(it) }
        }
        if (!mutex.tryLock()) return@withContext Outcome(false, "not sent: an upload is under way")
        try {
            save(state.copy(lastAttemptAt = now))
            val secrets = n.secrets
            val shadow = post(n, SHADOW) { export(secrets, MAX_BYTES) to JSON_TYPE }
            val read = readLog()
            val picked = AppLog.select(read.lines, now, state.logSince, zone())
            val log = if (picked.lines.isEmpty() && read.error == null) "no new log lines" else {
                var lines = picked.lines.map { Redact.all(it, secrets) }
                var text = logText(now, picked, read, lines)
                while (text.toByteArray().size > MAX_BYTES && lines.isNotEmpty()) {
                    lines = lines.drop(lines.size / 10 + 1)
                    text = logText(now, picked, read, lines)
                }
                post(n, LOGS) { text to TEXT_TYPE }.also { if (it.startsWith("200")) save(state.copy(logSince = picked.newest)) } +
                    " (${lines.size} line(s)${read.error?.let { e -> "; $e" } ?: ""})"
            }
            val summary = "${at(now)} ($why): the shadow's export $shadow; the log $log"
            save(state.copy(last = summary))
            last = summary
            logLine(summary)
            Outcome(shadow.startsWith("200"), summary)
        } finally {
            mutex.unlock()
        }
    }

    /** A not-sent outcome: only in a debug or qa build's log (QA checks it; nobody else needs the line). */
    private fun quiet(o: Outcome) {
        if (si.lanisce.lani.BuildConfig.QA) logLine(o.summary)
    }

    /** POSTs what [body] makes to [path]: "200 (12 KB)", "HTTP 413", "failed: …". */
    private suspend fun post(n: Node, path: String, body: suspend () -> Pair<String, String>): String = try {
        val (text, type) = body()
        val code = n.post(path, text, type)
        if (code == 200) "200 (${text.toByteArray().size / 1024} KB)" else "HTTP $code"
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        "failed: ${Redact.all(e.message ?: e.javaClass.simpleName, n.secrets).take(200)}"
    }

    private fun at(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone()).toLocalDateTime().withNano(0).toString()

    /** The log upload: a header (when, the app and the phone, what it holds), then the lines. */
    private fun logText(now: Long, picked: AppLog.Selected, read: AppLog.Read, lines: List<String>): String = buildString {
        append("=== ").append(Instant.ofEpochMilli(now)).append(' ').append(about())
        append(": ").append(lines.size).append(" line(s) of ").append(read.source)
        append(", after ").append(if (picked.since > 0) Instant.ofEpochMilli(picked.since).toString() else "the start")
        read.error?.let { append("; ").append(it) }
        append(" ===\n")
        lines.forEach { append(it).append('\n') }
    }

    companion object {
        const val TAG = "DevUpload"
        const val SHADOW = "/dev/shadow"
        const val LOGS = "/dev/logs"
        /** The node takes at most 5 MB a request (companion/bridge/src/features/dev.ts DEV_MAX): a little less. */
        const val MAX_BYTES = 5_000_000
        /** The background's uploads: at most one in 30 minutes. */
        const val BACKGROUND_EVERY_MS = 30 * 60_000L
        const val JSON_TYPE = "application/json; charset=utf-8"
        const val TEXT_TYPE = "text/plain; charset=utf-8"
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}

/**
 * The app's own log (this process's lines in logcat: an app reads its own without a permission): its warnings and
 * errors, and the info lines of its own tags that matter to the developer ([TAGS]: the shadow's calls and transport
 * errors, Clips' "no clip of a line", the speaker's route lines in QA builds …); the last [MAX_LINES] of the last
 * [MAX_AGE_MS], after what was sent before.
 */
object AppLog {
    const val MAX_LINES = 2_000
    const val MAX_AGE_MS = 24 * 3_600_000L

    /** The app's tags whose info lines go too (warnings and errors go from every tag). */
    val TAGS = listOf(
        "ShadowTutor", DevUpload.TAG, "Clips", "Speaker", "IntroVoice", "Piper", "Prefetch", "OfflineAudio", "OfflineVoice",
        "Ambience", "Cultures", "RoadPrep", "RoadCar", "LaniPrint",
    )

    /** What was read: the lines, where from ("logcat"), and why nothing could be (null: read). */
    data class Read(val lines: List<String>, val source: String = "logcat", val error: String? = null)

    /** The lines to send, and the newest one's time (the next upload's [since]); [since] the time they come after. */
    data class Selected(val lines: List<String>, val newest: Long, val since: Long)

    /** logcat's dump of process [pid]'s lines, each with its year ("2026-10-16 18:03:12.345 …"), as [TAGS] and W pick. */
    fun command(pid: Int): List<String> =
        listOf("logcat", "-d", "-v", "threadtime", "-v", "year", "--pid=$pid") + TAGS.map { "$it:I" } + "*:W"

    private val time = Regex("""^(\d{4}-\d{2}-\d{2}) (\d{2}:\d{2}:\d{2}\.\d{3})""")

    /** When [line] was logged (logcat's local time, in [zone]); null for a line without one ("--------- beginning of main"). */
    fun timeOf(line: String, zone: ZoneId): Long? = time.find(line)?.let { m ->
        runCatching { LocalDateTime.parse("${m.groupValues[1]}T${m.groupValues[2]}").atZone(zone).toInstant().toEpochMilli() }.getOrNull()
    }

    /**
     * The lines of [lines] logged after [since] and within [maxAgeMs] of [now], the last [max] of them, with the newest
     * one's time (else [since] again).
     */
    fun select(lines: List<String>, now: Long, since: Long, zone: ZoneId, max: Int = MAX_LINES, maxAgeMs: Long = MAX_AGE_MS): Selected {
        val from = maxOf(since, now - maxAgeMs)
        val kept = lines.mapNotNull { l -> timeOf(l, zone)?.takeIf { it > from }?.let { it to l } }.takeLast(max)
        return Selected(kept.map { it.second }, kept.maxOfOrNull { it.first } ?: since, since)
    }

    /** Process [pid]'s lines from logcat (no permission needed for its own); an [AppLog.Read.error] when it can't run. */
    fun read(pid: Int, timeoutMs: Long = 5_000): Read = try {
        val p = ProcessBuilder(command(pid)).redirectErrorStream(true).start()
        // logcat -d ends by itself; a stuck one is stopped
        val watchdog = Thread {
            try {
                if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) p.destroy()
            } catch (_: InterruptedException) {
            }
        }.apply { isDaemon = true; start() }
        val lines = p.inputStream.bufferedReader().readLines()
        val code = p.waitFor()
        watchdog.interrupt()
        if (code != 0 && lines.none { time.containsMatchIn(it) }) Read(emptyList(), error = "logcat exited $code: ${lines.take(2).joinToString(" / ").take(200)}")
        else Read(lines)
    } catch (e: Exception) {
        Read(emptyList(), error = "logcat can't run: ${e.message ?: e.javaClass.simpleName}")
    }
}
