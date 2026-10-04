package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import si.lanisce.lani.data.BridgeEvent
import si.lanisce.lani.data.FileStorage
import si.lanisce.lani.data.Prefs
import si.lanisce.lani.data.Writes
import si.lanisce.lani.data.json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * A message of the tutor chat. [about] is the one-line label of the exercise or page a question refers to, shown on the
 * bubble; [at] when it was written or arrived (epoch ms, the phone's clock; null in a history kept before the archive,
 * which had no times); [data] what a learner's message went to the tutor with (`{"about_exercise": {…}}`, a lookup), for
 * a reply that quotes it; [bookmark] set when the learner bookmarked it; [replyTo] the message it answers, quoted.
 */
@Serializable
data class ChatMessage(
    val fromMe: Boolean,
    val text: String,
    val about: String? = null,
    val id: String = UUID.randomUUID().toString(),
    val at: Long? = null,
    val data: JsonObject? = null,
    val bookmark: ChatBookmark? = null,
    val replyTo: ChatQuote? = null,
)

/** A bookmarked message: when it was bookmarked, and the learner's note on why ("zakaj"), if any. */
@Serializable
data class ChatBookmark(val at: Long, val note: String? = null)

/** The message a reply answers, as the reply's bubble quotes it: its [id] (a tap goes there), who wrote it, its start. */
@Serializable
data class ChatQuote(val id: String, val fromMe: Boolean, val text: String, val at: Long? = null)

/** A list of messages as a file keeps it (JSON); a damaged file reads as none. */
object ChatHistory {
    private val LIST = ListSerializer(ChatMessage.serializer())

    fun encode(all: List<ChatMessage>): String = json.encodeToString(LIST, all)
    fun decode(raw: String): List<ChatMessage> = runCatching { json.decodeFromString(LIST, raw) }.getOrDefault(emptyList())
}

/**
 * Where the chat is kept on the phone (the app's private files; the node keeps no chat history): a file a month in
 * [dir] (`chat/2026-09.json`, by each message's [ChatMessage.at] in UTC), and [legacy] (`chat.json`), the conversation
 * kept before the archive (its last 100 messages, without times), read as it is and written again only when one of
 * its messages changes (a bookmark). A message's file is rewritten when it changes, so a write is at most a month.
 * Months older than [keepMonths] are let go when the chat loads, except their bookmarked messages.
 */
class ChatStore(private val dir: File, private val legacy: File, private val keepMonths: Int = KEEP_MONTHS) {
    /** Everything kept, oldest first: the undated messages, then the months in order. */
    fun load(now: Long): List<ChatMessage> {
        val old = FileStorage(legacy).read()?.let(ChatHistory::decode).orEmpty()
        val oldest = month(now).minusMonths(keepMonths - 1L)
        val months = dir.listFiles { f -> MONTH_FILE.matches(f.name) }.orEmpty().sortedBy { it.name }
        val dated = months.flatMap { f ->
            val all = FileStorage(f).read()?.let(ChatHistory::decode).orEmpty()
            if (YearMonth.parse(f.name.removeSuffix(".json")) >= oldest) all
            else all.filter { it.bookmark != null }.also { kept ->
                runCatching { if (kept.isEmpty()) f.delete() else if (kept.size < all.size) FileStorage(f).write(ChatHistory.encode(kept)) }
            }
        }
        return old + dated
    }

    /** Writes the file of [part] ([partOf]) with its messages of [all]. */
    fun write(part: String, all: List<ChatMessage>) {
        val mine = all.filter { partOf(it) == part }
        if (part == LEGACY) return FileStorage(legacy).write(ChatHistory.encode(mine))
        dir.mkdirs()
        FileStorage(File(dir, "$part.json")).write(ChatHistory.encode(mine))
    }

    companion object {
        /** The part of the undated messages: [legacy]. */
        const val LEGACY = "legacy"
        /** A year of chat; bookmarked messages stay. */
        const val KEEP_MONTHS = 12
        private val MONTH_FILE = Regex("""\d{4}-\d{2}\.json""")

        private fun month(at: Long): YearMonth = YearMonth.from(Instant.ofEpochMilli(at).atZone(ZoneOffset.UTC))

        /** The file [m] is kept in: its month ("2026-09"), or [LEGACY] when it has no time. */
        fun partOf(m: ChatMessage): String = m.at?.let { month(it).toString() } ?: LEGACY
    }
}

/**
 * The tutor chat: its messages and sheet, the exercise a question is about, the message a reply quotes, bookmarks,
 * tutor-graded answers, and the day's plan. Messages go out through the outbox, so they survive being offline; the
 * conversation is kept in [store], so it survives the app being closed, killed or updated.
 */
class ChatController(
    private val scope: CoroutineScope,
    private val prefs: Prefs,
    private val sync: SyncController,
    private val notices: Notices,
    private val store: ChatStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The whole conversation kept ([ChatStore]: a year, and every bookmarked message), oldest first. */
    val messages = mutableStateListOf<ChatMessage>()
    var isOpen by mutableStateOf(false)
        private set
    /** The tutor is answering (a message or a finished module went out). */
    var typing by mutableStateOf(false)
        private set
    var unread by mutableIntStateOf(0)
        private set
    /** The exercise the next message is about; the sheet can clear it. */
    var attached by mutableStateOf<ChatContext?>(null)
    /** The message the next one answers ("↩️ Odgovori · Reply"): quoted above the input, sent as `data.reply_to`. */
    var replyTo by mutableStateOf<ChatMessage?>(null)
    /** The tutor's morning plan (daily rhythm), shown on Home until the day ends. */
    var todayPlan by mutableStateOf<String?>(null)
        private set
    /** Replies to claude-graded exercises, keyed by their conversation id; the latest [MAX_GRADED]. */
    val gradedReplies = mutableStateMapOf<String, BridgeEvent.Reply>()
    private val gradedOrder = ArrayDeque<String>()
    private val io = Dispatchers.IO.limitedParallelism(1) // saves stay in order
    /** The saved conversation is in [messages]; saving earlier would overwrite it with less. */
    private var loaded = false

    init {
        scope.launch { todayPlan = prefs.todayPlan(LocalDate.now().toString()) }
        scope.launch {
            val saved = withContext(io) { runCatching { store.load(clock()) }.getOrDefault(emptyList()) }
            val arrived = messages.toList()
            messages.addAll(0, saved) // before anything that arrived meanwhile
            loaded = true
            if (arrived.isNotEmpty()) save(arrived)
        }
    }

    private fun add(m: ChatMessage) {
        val stamped = if (m.at == null) m.copy(at = clock()) else m
        messages += stamped
        if (loaded) save(listOf(stamped))
    }

    /** Writes the files [changed] are kept in (their months). */
    private fun save(changed: List<ChatMessage>) {
        val parts = changed.map(ChatStore::partOf).toSet()
        val snapshot = messages.toList()
        scope.launch(io) { for (p in parts) runCatching { store.write(p, snapshot) } }
    }

    /** Changes message [id] by [f] and keeps it. */
    private fun update(id: String, f: (ChatMessage) -> ChatMessage) {
        val i = messages.indexOfFirst { it.id == id }
        if (i < 0) return
        val m = f(messages[i])
        messages[i] = m
        if (loaded) save(listOf(m))
    }

    /** Bookmarks message [id] ([on]), or takes its bookmark (and note) away. */
    fun bookmark(id: String, on: Boolean) = update(id) { ChatArchive.bookmarked(it, on, clock()) }

    /** The bookmark's note on message [id] ("zakaj · why"), bookmarking it if it wasn't; blank takes the note away. */
    fun note(id: String, note: String) = update(id) { ChatArchive.noted(it, note, clock()) }

    /** The message [id], while it is kept. */
    fun find(id: String): ChatMessage? = messages.firstOrNull { it.id == id }

    fun open() {
        isOpen = true
        unread = 0
    }

    fun close() {
        isOpen = false
    }

    /**
     * Sends [text] with the exercise [attached] and the message [replyTo] quotes, if any: the exercise under its key
     * (`about_exercise`, …), the quoted message as `reply_to` ([ChatArchive.replyTo]), which an older node passes on
     * to the tutor as it does any data.
     */
    fun send(text: String, zone: ZoneId = ZoneId.systemDefault()) {
        if (text.isBlank()) return
        val ctx = attached
        attached = null
        val quoted = replyTo
        replyTo = null
        val about = ctx?.let { buildJsonObject { put(it.key, it.data) } }
        add(ChatMessage(fromMe = true, text = text, about = ctx?.label, data = about, replyTo = quoted?.let(ChatArchive::quote)))
        typing = true
        val data = ChatArchive.outgoing(about, quoted, zone)
        scope.launchSafely(notices::fail) { if (!sync.submit(Writes.message("chat", "main", text, data))) typing = false }
    }

    /**
     * Asks the tutor straight away, with [data] for the tutor to read along (e.g. `{"lookup": {…}}` for a word the
     * dictionary didn't know), and opens the chat so the answer shows there.
     */
    fun ask(text: String, data: JsonObject) {
        if (text.isBlank()) return
        add(ChatMessage(fromMe = true, text = text, data = data))
        typing = true
        open()
        scope.launchSafely(notices::fail) { if (!sync.submit(Writes.message("chat", "main", text, data))) typing = false }
    }

    /** Opens the chat with an exercise attached; the next message carries it as context. */
    fun askAbout(ctx: ChatContext) {
        attached = ctx
        open()
    }

    /**
     * A thread held elsewhere (the chat under a role-play's debrief) goes on here: its messages join the chat (the
     * first question labelled with [ctx]), and the next message carries [ctx].
     */
    fun continueThread(thread: List<ChatMessage>, ctx: ChatContext) {
        var labelled = false
        for (m in thread) {
            val first = m.fromMe && !labelled
            if (first) labelled = true
            add(
                ChatMessage(
                    fromMe = m.fromMe, text = m.text, about = ctx.label.takeIf { first },
                    data = if (first) buildJsonObject { put(ctx.key, ctx.data) } else null,
                ),
            )
        }
        attached = ctx
        open()
    }

    /** Sends an answer for tutor grading; the reply arrives in [gradedReplies] under the returned id. */
    fun requestGrading(text: String, data: JsonObject): String {
        val conv = "ex" + UUID.randomUUID().toString().replace("-", "").take(12)
        scope.launchSafely(notices::fail) { sync.submit(Writes.message("answer", conv, text, data)) }
        return conv
    }

    /**
     * What every session's report adds for the tutor besides its results ([sessionEnd]): the rules ripe to introduce
     * (`grammar_ripe`, [GrammarController.sessionExtras]). Set by the view model; a key the report has already stays its.
     */
    var sessionExtras: () -> Map<String, JsonElement> = { emptyMap() }

    /** A finished module's results, for the tutor's review (its answer shows up in the chat). */
    fun sessionEnd(summary: String, data: JsonObject): kotlinx.coroutines.Job {
        val extras = runCatching { sessionExtras() }.getOrDefault(emptyMap()).filterKeys { it !in data }
        val all = if (extras.isEmpty()) data else JsonObject(data + extras)
        return scope.launchSafely(notices::fail) {
            typing = true
            if (!sync.submit(Writes.message("session_end", "main", summary, all))) typing = false
        }
    }

    fun onGraded(ev: BridgeEvent.Reply) {
        if (ev.conversationId !in gradedReplies) gradedOrder.addLast(ev.conversationId)
        gradedReplies[ev.conversationId] = ev
        while (gradedOrder.size > MAX_GRADED) gradedReplies.remove(gradedOrder.removeFirst())
    }

    /** A reply in the main conversation. */
    fun onReply(ev: BridgeEvent.Reply) {
        typing = false
        add(ChatMessage(fromMe = false, text = ev.text))
        if (!isOpen) unread++
        if (ev.plan) {
            todayPlan = ev.text
            scope.launch { prefs.saveTodayPlan(LocalDate.now().toString(), ev.text) }
        }
    }

    private companion object {
        /** An exercise waits for its grade on screen; older grades are long shown (and in the chat). */
        const val MAX_GRADED = 20
    }
}
