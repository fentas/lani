package si.lanisce.lani.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.util.UUID
import si.lanisce.lani.l10n.bi

/**
 * A write waiting for the node. [clientId] also travels in the body as `client_id`,
 * so the bridge can answer a repeat with the original result instead of applying it twice.
 */
@Serializable
data class OutboxEntry(
    val clientId: String,
    val method: String,
    val path: String,
    val body: String,
    /** Short bilingual label for errors, e.g. "Ponovitev · Review". */
    val label: String,
    val createdAt: Long,
    val attempts: Int = 0,
    /** Failed answers from a reachable node (5xx); offline attempts don't count. */
    val serverErrors: Int = 0,
    val nextAttemptAt: Long = 0,
    /**
     * What goes instead when the node doesn't know [path] (404: a node from before it): another route and body, the same
     * client id ([Outbox.flush]). The level change is a `session_end` for an older node's tutor to persist.
     */
    val fallbackPath: String? = null,
    val fallbackBody: String? = null,
)

/** How one delivery attempt ended. */
sealed interface Delivery {
    data object Done : Delivery
    /** Offline, timeout, 5xx: keep it and try again later. [server] = the node answered with an error. */
    data class Retry(val reason: String, val server: Boolean = false) : Delivery
    /** Rejected for good (4xx): drop it and tell the learner. */
    data class Rejected(val code: Int, val reason: String) : Delivery
}

/** [fellBack]: writes an older node didn't know, sent the other way instead (their fallback, [OutboxEntry.fallbackPath]). */
data class FlushReport(
    val sent: List<OutboxEntry>, val dropped: List<Pair<OutboxEntry, String>>, val remaining: Int, val fellBack: List<OutboxEntry> = emptyList(),
)

/** Where the queue lives; a file in the app, memory in tests. */
interface OutboxStorage {
    fun read(): String?
    fun write(raw: String)
}

class FileStorage(private val file: File) : OutboxStorage {
    override fun read(): String? = runCatching { file.readText() }.getOrNull()
    override fun write(raw: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(raw)
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }
}

/**
 * Persistent FIFO of writes made while the node was unreachable. Entries go out in order;
 * the first one that can't be delivered stops the flush so later writes don't overtake it.
 */
class Outbox(private val storage: OutboxStorage, private val clock: () -> Long = System::currentTimeMillis) {
    constructor(context: Context) : this(FileStorage(File(context.filesDir, "outbox.json")))

    fun entries(): List<OutboxEntry> = synchronized(lock) { load() }

    val size: Int get() = entries().size

    /** Adds [e] at the end; an entry with the same client id is kept only once. */
    fun enqueue(e: OutboxEntry) = synchronized(lock) {
        val all = load()
        if (all.none { it.clientId == e.clientId }) save(all + e)
    }

    fun remove(clientId: String) = synchronized(lock) { save(load().filterNot { it.clientId == clientId }) }

    private fun update(clientId: String, f: (OutboxEntry) -> OutboxEntry) = synchronized(lock) {
        save(load().map { if (it.clientId == clientId) f(it) else it })
    }

    /**
     * Milliseconds until a retry can send something, or null when empty. That is the head's time:
     * a flush stops at the first write that isn't due, so a newer write behind it has to wait too
     * (the earliest time of any entry would be 0 for a never-tried write and spin the retry loop).
     */
    fun nextRetryIn(): Long? = entries().firstOrNull()?.let { (it.nextAttemptAt - clock()).coerceAtLeast(0) }

    /**
     * Sends the queue in order through [send]. [force] ignores backoff (the network just came back).
     * Only one flush runs at a time across the process.
     */
    suspend fun flush(force: Boolean = true, send: suspend (OutboxEntry) -> Delivery): FlushReport = flushLock.withLock {
        val sent = mutableListOf<OutboxEntry>()
        val dropped = mutableListOf<Pair<OutboxEntry, String>>()
        val fellBack = mutableListOf<OutboxEntry>()
        suspend fun attempt(e: OutboxEntry): Delivery = try { send(e) } catch (x: CancellationException) { throw x } catch (x: Exception) { Delivery.Retry(x.message ?: "error") }
        for (queued in entries()) {
            if (!force && queued.nextAttemptAt > clock()) break
            var e = queued
            var r = attempt(e)
            // a node from before this route: its fallback goes instead, in the same place in the queue
            val fb = e.fallbackPath
            if (r is Delivery.Rejected && r.code == 404 && fb != null) {
                e = e.copy(path = fb, body = e.fallbackBody ?: e.body, fallbackPath = null, fallbackBody = null)
                update(e.clientId) { e }
                fellBack += e
                r = attempt(e)
            }
            when (r) {
                Delivery.Done -> { remove(e.clientId); sent += e }
                is Delivery.Rejected -> { remove(e.clientId); dropped += e to "HTTP ${r.code}: ${r.reason}" }
                is Delivery.Retry -> {
                    val attempts = e.attempts + 1
                    val serverErrors = e.serverErrors + if (r.server) 1 else 0
                    if (serverErrors >= MAX_SERVER_ERRORS) {
                        remove(e.clientId)
                        dropped += e to r.reason
                        continue
                    }
                    update(e.clientId) { it.copy(attempts = attempts, serverErrors = serverErrors, nextAttemptAt = clock() + backoff(attempts)) }
                    break
                }
            }
        }
        FlushReport(sent, dropped, entries().size, fellBack)
    }

    private fun load(): List<OutboxEntry> =
        storage.read()?.let { raw -> runCatching { json.decodeFromString(LIST, raw) }.getOrNull() }.orEmpty()

    private fun save(all: List<OutboxEntry>) = storage.write(json.encodeToString(LIST, all))

    companion object {
        private val LIST = ListSerializer(OutboxEntry.serializer())
        private val lock = Any()
        private val flushLock = Mutex()
        /** A write the node keeps failing (5xx) is given up after this many answers; offline never gives up. */
        const val MAX_SERVER_ERRORS = 5

        /** 5 s, 10 s, 20 s … capped at 15 minutes. */
        fun backoff(attempts: Int): Long = (5_000L shl (attempts - 1).coerceIn(0, 20)).coerceAtMost(15 * 60_000L)

        /** Status code → outcome. 401/408/429 and 5xx are worth retrying; other 4xx are final. */
        fun classify(code: Int, body: String): Delivery = when {
            code in 200..299 -> Delivery.Done
            code == 401 || code == 408 || code == 429 -> Delivery.Retry("HTTP $code")
            code >= 500 -> Delivery.Retry("HTTP $code: ${errorText(body)}", server = true)
            else -> Delivery.Rejected(code, errorText(body))
        }

        private fun errorText(body: String): String =
            runCatching { (json.parseToJsonElement(body) as JsonObject)["error"]?.toString()?.trim('"') }.getOrNull() ?: body.take(160)

        fun newId(): String = UUID.randomUUID().toString().replace("-", "")
    }
}

/** The queueable writes, each with its own client id. */
object Writes {
    private fun entry(path: String, label: String, body: JsonObject, id: String, now: Long) = OutboxEntry(
        clientId = id,
        method = "POST",
        path = path,
        body = JsonObject(body + ("client_id" to JsonPrimitive(id))).toString(),
        label = label,
        createdAt = now,
    )

    /** `{"results": [{<key>: id, "quality": q}, …], "duration_minutes": m}`: the body of both SR writes. */
    private fun graded(key: String, results: List<Pair<String, Int>>, minutes: Int) = buildJsonObject {
        put("results", buildJsonArray { results.forEach { (i, q) -> add(buildJsonObject { put(key, i); put("quality", q) }) } })
        put("duration_minutes", minutes.coerceIn(1, 240))
    }

    /**
     * Flashcard reviews, persisted deterministically (update-db.py on the node). [language]: another language's deck
     * (its own data on the node); null for the home language's. [forms]: the word forms the review asked
     * ([si.lanisce.lani.app.FormsController.answered]), for the tutor's note; an older bridge leaves them out.
     */
    fun reviews(
        results: List<Pair<String, Int>>, minutes: Int, language: String? = null, forms: JsonArray? = null,
        id: String = Outbox.newId(), now: Long = System.currentTimeMillis(),
    ): OutboxEntry {
        var body = graded("item_id", results, minutes)
        if (language != null) body = JsonObject(body + ("language" to JsonPrimitive(language)))
        if (forms != null && forms.isNotEmpty()) body = JsonObject(body + ("forms" to forms))
        return entry("/reviews", bi("outbox.review"), body, id, now)
    }

    /** Pack words become SR vocabulary on the node; already known words are skipped there. */
    fun learnPack(packId: String, results: List<Pair<String, Int>>, minutes: Int, id: String = Outbox.newId(), now: Long = System.currentTimeMillis()) =
        entry("/packs/$packId/learn", bi("homeScreen.newWords"), graded("word_id", results, minutes), id, now)

    /**
     * A word the learner met becomes one of their words (POST /words): a review item on the node, or the pack word's
     * item when [itemId] names one. [example] is where they met it; [from] where they tapped it ([Words.SCENE] …).
     * [language]: another language's words (a visit's: the host town's), null for the home language's.
     */
    fun addWord(
        sl: String,
        en: String,
        pos: String? = null,
        itemId: String? = null,
        example: WordExample? = null,
        from: String? = null,
        language: String? = null,
        id: String = Outbox.newId(),
        now: Long = System.currentTimeMillis(),
    ) = entry("/words", bi("outbox.word"), buildJsonObject {
        language?.let { put("language", it) }
        put("sl", sl)
        put("en", en)
        pos?.let { put("pos", it) }
        itemId?.let { put("item_id", it) }
        example?.takeIf { it.sl.isNotBlank() }?.let {
            put("example_sl", it.sl)
            if (it.en.isNotBlank()) put("example_en", it.en)
        }
        from?.let { put("from", it) }
    }, id, now)

    /**
     * Reading practice ([ReadingPractice]: a reading's questions, a story heard to its end, a reading aloud), persisted as
     * the learner's reading (and speaking) practice on the node.
     */
    fun readingDone(body: JsonObject, id: String = Outbox.newId(), now: Long = System.currentTimeMillis()) =
        entry("/readings/done", bi("outbox.reading"), body, id, now)

    /**
     * The level the treasure brought ([LevelReport]): `POST /level` for the bridge to persist; for a node from before it
     * (404), a `session_end` with the report for the tutor to persist instead ([OutboxEntry.fallbackPath]), the same id.
     */
    fun levelUp(
        from: String, to: String, stations: List<StationResult>, languageName: String, language: String? = null,
        id: String = Outbox.newId(), now: Long = System.currentTimeMillis(),
    ): OutboxEntry {
        val body = LevelReport.body(from, to, stations, language)
        val (text, data) = LevelReport.sessionEnd(from, to, stations, languageName, body)
        val fallback = message("session_end", "main", text, if (language == null) data else JsonObject(data + ("language" to JsonPrimitive(language))), id, now)
        return entry("/level", bi("outbox.level"), body, id, now).copy(fallbackPath = fallback.path, fallbackBody = fallback.body)
    }

    fun message(kind: String, conversationId: String, text: String, data: JsonObject?, id: String = Outbox.newId(), now: Long = System.currentTimeMillis()) =
        entry("/message", if (kind == "chat") bi("outbox.message") else bi("outbox.exercise"), buildJsonObject {
            put("kind", kind)
            put("conversation_id", conversationId)
            put("text", text)
            if (data != null) put("data", data)
        }, id, now)
}

/** Delivers an entry through [bridge]: network errors retry, status codes are [Outbox.classify]'d. */
suspend fun Bridge.deliver(e: OutboxEntry): Delivery = try {
    val (code, body) = send(e.method, e.path, e.body)
    Outbox.classify(code, body)
} catch (x: IOException) {
    Delivery.Retry(x.message ?: "offline")
}
