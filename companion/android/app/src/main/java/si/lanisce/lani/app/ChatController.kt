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
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.BridgeEvent
import si.lanisce.lani.data.ChatPhoto
import si.lanisce.lani.data.ChatPhotoStore
import si.lanisce.lani.data.FileStorage
import si.lanisce.lani.data.Outbox
import si.lanisce.lani.data.PhotoPrep
import si.lanisce.lani.data.Prefs
import si.lanisce.lani.data.Writes
import si.lanisce.lani.data.json
import si.lanisce.lani.l10n.bi
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
 * a reply that quotes it; [bookmark] set when the learner bookmarked it; [replyTo] the message it answers, quoted;
 * [photo] the photo it was sent with (📷, the learner's), its file on the phone in [ChatPhotoStore].
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
    val photo: ChatPhoto? = null,
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
 * tutor-graded answers, the photo the next message carries, and the day's plan. Messages go out through the outbox, so
 * they survive being offline; the conversation is kept in [store], so it survives the app being closed, killed or
 * updated. Its photos are in [photos] (none without it), fetched from the [node] when the phone hasn't one.
 */
class ChatController(
    private val scope: CoroutineScope,
    private val prefs: Prefs,
    private val sync: SyncController,
    private val notices: Notices,
    private val store: ChatStore,
    private val clock: () -> Long = System::currentTimeMillis,
    val photos: ChatPhotoStore? = null,
    private val node: () -> Bridge? = { null },
) {
    /** The whole conversation kept ([ChatStore]: a year, and every bookmarked message), oldest first. */
    val messages = mutableStateListOf<ChatMessage>()
    var isOpen by mutableStateOf(false)
        private set
    /** The tutor is answering (a message or a finished module went out). */
    var typing by mutableStateOf(false)
        private set
    /**
     * The in-app tutor's reply as it's written (tutor/live/LiveTutor: the reply tool's streamed input), shown in the
     * "typing" bubble until the final message arrives ([onReply]); null: nothing streaming.
     */
    var streaming by mutableStateOf<String?>(null)
    var unread by mutableIntStateOf(0)
        private set
    /** The exercise the next message is about; the sheet can clear it. */
    var attached by mutableStateOf<ChatContext?>(null)
    /** The message the next one answers ("↩️ Odgovori · Reply"): quoted above the input, sent as `data.reply_to`. */
    var replyTo by mutableStateOf<ChatMessage?>(null)
    /** The photo the next message carries (📷: taken or picked, made ready, [attachPhoto]): shown above the input, ✕ lets it go. */
    var draftPhoto by mutableStateOf<ChatPhoto?>(null)
        private set
    /** A photo is being made ready ([PhotoPrep]): the composer shows it coming, and the message waits for it. */
    var preparingPhoto by mutableStateOf(false)
        private set
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
            sweepPhotos()
        }
    }

    /** The photos no message keeps any more (a draft let go, a month the chat let go: [ChatStore.load]) leave the phone. */
    private fun sweepPhotos() {
        val store = photos ?: return
        val keep = messages.mapNotNull { m -> m.photo?.takeIf { !it.deleted }?.id }.toSet() + setOfNotNull(draftPhoto?.id)
        scope.launch(io) { runCatching { store.sweep(keep, clock()) } }
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

    /** A swipe left on message [id]: bookmarks it, or takes its bookmark (and note) away; what it had, for an undo. */
    fun toggleBookmark(id: String): ChatBookmark? {
        val before = find(id)?.bookmark
        bookmark(id, on = before == null)
        return before
    }

    /** Message [id]'s bookmark as it was before a swipe ([bookmark], its time and note; null: none): the swipe's undo. */
    fun restoreBookmark(id: String, bookmark: ChatBookmark?) = update(id) { it.copy(bookmark = bookmark) }

    /** The bookmark's note on message [id] ("zakaj · why"), bookmarking it if it wasn't; blank takes the note away. */
    fun note(id: String, note: String) = update(id) { ChatArchive.noted(it, note, clock()) }

    /** The message [id], while it is kept. */
    fun find(id: String): ChatMessage? = messages.firstOrNull { it.id == id }

    fun open() {
        isOpen = true
        unread = 0
    }

    /** What a screen's 💬 attached ([openFrom]), while it is what's attached. */
    private var fromScreen: ChatContext? = null

    /**
     * 💬 in a screen's top bar: the chat opens with [ctx] attached (what's on screen: the exercise, the word met), shown
     * above the input as any attachment, which ✕ takes away; nothing is sent. Closed without a message, the chat lets it
     * go again, so the chat opened from elsewhere later has nothing of a screen left. Null: the chat opens as it is.
     */
    fun openFrom(ctx: ChatContext?) {
        if (ctx != null) {
            attached = ctx
            fromScreen = ctx
        }
        open()
    }

    fun close() {
        isOpen = false
        if (fromScreen != null && attached === fromScreen) attached = null
        fromScreen = null
    }

    /**
     * Sends [text] with the exercise [attached], the message [replyTo] quotes and the photo [draftPhoto], if any: the
     * exercise under its key (`about_exercise`, …), the quoted message as `reply_to` ([ChatArchive.replyTo]), which an
     * older node passes on to the tutor as it does any data; the photo first, then the message with it
     * ([Writes.chatPhoto]). A photo alone is a message too; one still being made ready waits.
     */
    fun send(text: String, zone: ZoneId = ZoneId.systemDefault()) {
        val photo = draftPhoto
        if (preparingPhoto || (text.isBlank() && photo == null)) return
        val ctx = attached
        attached = null
        val quoted = replyTo
        replyTo = null
        draftPhoto = null
        val about = ctx?.let { buildJsonObject { put(it.key, it.data) } }
        add(ChatMessage(fromMe = true, text = text, about = ctx?.label, data = about, replyTo = quoted?.let(ChatArchive::quote), photo = photo))
        typing = true
        val data = ChatArchive.outgoing(about, quoted, zone)
        val store = photos
        val write = if (photo != null && store != null) Writes.chatPhoto(text.trim(), data, photo, store.file(photo.id)) else Writes.message("chat", "main", text, data)
        scope.launchSafely(notices::fail) { if (!sync.submit(write)) typing = false }
    }

    /**
     * 📷: a photo taken or picked ([read] gives its bytes, off the main thread) made ready to send ([PhotoPrep]: upright,
     * scaled, no metadata), kept on the phone and shown above the input as [draftPhoto], replacing one there was. One that
     * can't be read says so.
     */
    fun attachPhoto(read: suspend () -> ByteArray?) {
        val store = photos ?: return
        preparingPhoto = true
        scope.launch {
            val ready = withContext(Dispatchers.Default) { runCatching { PhotoPrep.prepare(read() ?: error("nothing read")) } }
            val photo = ready.getOrNull()?.let { r ->
                val id = Outbox.newId()
                withContext(io) { runCatching { store.save(id, r.jpeg) }.isSuccess }.takeIf { it }?.let { ChatPhoto(id, r.width, r.height, r.jpeg.size.toLong()) }
            }
            preparingPhoto = false
            if (photo == null) {
                notices.error = "📷 ${bi("chatSheet.photoFailed")}"
                return@launch
            }
            draftPhoto?.let { old -> withContext(io) { store.delete(old.id) } }
            draftPhoto = photo
        }
    }

    /** The photo above the input let go (its ✕): it leaves the phone, nothing was sent. */
    fun dropDraftPhoto() {
        val p = draftPhoto ?: return
        draftPhoto = null
        photos?.let { s -> scope.launch(io) { s.delete(p.id) } }
    }

    /**
     * "🗑️ Izbriši fotografijo · Delete photo" on message [id]: its photo leaves the phone and the node (`DELETE
     * /chat/images/<id>`, through the outbox); the message and its text stay, marked. What the tutor saw of it, it saw.
     */
    fun deletePhoto(id: String) {
        val p = find(id)?.photo?.takeIf { !it.deleted } ?: return
        update(id) { it.copy(photo = p.copy(deleted = true)) }
        photos?.let { s -> scope.launch(io) { s.delete(p.id) } }
        sync.submitLater(Writes.deletePhoto(p.id))
    }

    /**
     * Photo [p] at most [maxPx] on its long side, for a bubble or the full screen: the phone's copy, else the node's
     * (fetched once and kept); null when neither has it.
     */
    suspend fun photoImage(p: ChatPhoto, maxPx: Int): android.graphics.Bitmap? {
        val store = photos ?: return null
        if (p.deleted) return null
        return withContext(Dispatchers.IO) {
            store.image(p.id, maxPx) ?: run {
                val b = node() ?: return@withContext null
                runCatching { b.chatImage(p.id, store.file(p.id)) }.onFailure { store.file(p.id).delete() }.getOrNull() ?: return@withContext null
                store.image(p.id, maxPx)
            }
        }
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
        fromScreen = null
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

    /**
     * A short note of the in-app tutor's own for the learner (its daily cap reached, the key refused, no connection:
     * tutor/live/LiveTutor), as a tutor message in the chat; nothing goes to the node.
     */
    fun note(text: String) {
        typing = false
        streaming = null
        add(ChatMessage(fromMe = false, text = text))
        if (!isOpen) unread++
    }

    /** A reply in the main conversation. */
    fun onReply(ev: BridgeEvent.Reply) {
        typing = false
        streaming = null
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
