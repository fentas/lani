package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.VisitWorld
import si.lanisce.lani.data.WordEntry
import si.lanisce.lani.data.WordLookup
import si.lanisce.lani.data.Words
import si.lanisce.lani.data.Writes
import si.lanisce.lani.l10n.L10n

/**
 * A word tapped in a line: the form as written ([word]), the line it's in and its translation ([en], when there is
 * one), and where it was tapped ([from]: [Words.SCENE], [Words.ROLEPLAY] …).
 */
data class WordQuery(val word: String, val line: String, val en: String?, val from: String)

/** How looking a word up went. */
sealed interface WordState {
    data object Loading : WordState
    data class Found(val lookup: WordLookup) : WordState
    /** The dictionary doesn't know it: the tutor can say. */
    data class NotFound(val lookup: WordLookup) : WordState
    /** The node is older than lookups (404). */
    data object Unavailable : WordState
    /** The node can't be reached. */
    data object Offline : WordState
}

/** An entry being added to the learner's words: on its way, on the node, or waiting in the outbox (offline). */
enum class Adding { SENDING, ADDED, QUEUED }

/** The word card on screen: what was tapped, what's known about it, and which entries were added (by [WordEntry.key]). */
data class WordCard(val query: WordQuery, val state: WordState = WordState.Loading, val added: Map<String, Adding> = emptyMap())

/**
 * Looking up words tapped in dialogs, role-plays and villagers' lines (GET /lookup), adding them to the learner's
 * words (POST /words, through the outbox), and asking the tutor about a word the dictionary doesn't know.
 * One [card] at a time; the sheet shows it over any screen.
 */
class WordsController(
    private val scope: CoroutineScope,
    private val bridge: () -> Bridge?,
    private val sync: SyncController,
    private val chat: ChatController,
    private val notices: Notices,
) {
    var card by mutableStateOf<WordCard?>(null)
        private set
    private var job: Job? = null

    /** What the bridge [sources] were asked of takes as `from` (GET /words; null: an older bridge, the old four). */
    private var sources: Pair<Bridge, Set<String>?>? = null

    /**
     * The `from` to send for a word added in [from] ([Words.source]): a reading's says "reading" only to a bridge that
     * takes it, asked once per bridge; not connected, or the bridge not answering, "villager" as before.
     */
    suspend fun sourceFor(from: String): String {
        if (from in Words.OLD_SOURCES) return from
        val b = bridge() ?: return Words.VILLAGER
        val known = sources?.takeIf { it.first === b }
        val accepted = if (known != null) known.second else try {
            b.wordSources().also { sources = b to it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Words.VILLAGER
        }
        return Words.source(from, accepted)
    }

    fun open(q: WordQuery) {
        card = WordCard(q)
        fetch(q, quiet = false)
    }

    fun close() {
        job?.cancel()
        card = null
    }

    fun retry() {
        val c = card ?: return
        card = c.copy(state = WordState.Loading)
        fetch(c.query, quiet = false)
    }

    /** [quiet]: a card already showing keeps what it has when looking again fails. */
    private fun fetch(q: WordQuery, quiet: Boolean) {
        job?.cancel()
        job = scope.launch {
            val state = try {
                val b = bridge() ?: throw java.io.IOException("not connected")
                // on a visit: the town's language, explained in the visit's base; at home, in the pair's base
                val pair = L10n.pair
                when (val l = b.lookup(q.word, q.line, q.en, language = VisitWorld.language, meaningIn = pair.base.code.takeIf { pair.base != pair.target })) {
                    null -> WordState.Unavailable
                    else -> if (l.entries.isEmpty()) WordState.NotFound(l) else WordState.Found(l)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (quiet) return@launch
                WordState.Offline
            }
            card?.takeIf { it.query == q }?.let { card = it.copy(state = state) }
        }
    }

    /** Adds [e] to the learner's words; it shows ✓ at once, and whether it reached the node or waits for it. */
    fun add(e: WordEntry) {
        val c = card ?: return
        if (e.known || e.key in c.added) return
        val q = c.query
        // on a visit: the host town's language (its own profile, plan 2, §3.4)
        val language = VisitWorld.language
        card = c.copy(added = c.added + (e.key to Adding.SENDING))
        scope.launchSafely(notices::fail) {
            val write = Writes.addWord(
                sl = e.lemma,
                en = Words.meaning(e),
                pos = e.pos,
                itemId = e.itemId,
                example = Words.example(q.line, q.en, e),
                from = sourceFor(q.from),
                language = language,
            )
            val sent = sync.submit(write)
            card?.takeIf { it.query == q }?.let { card = it.copy(added = it.added + (e.key to if (sent) Adding.ADDED else Adding.QUEUED)) }
        }
    }

    /** Asks the tutor what the word means in its line; the chat opens for the answer. */
    fun ask() {
        val q = card?.query ?: return
        close()
        chat.ask(Words.askText(q.word, q.line), Words.askData(q.word, q.line, q.en, VisitWorld.language))
    }

    /** The tutor taught the node more about [word]: an open card about it looks again. */
    fun onLexiconUpdated(word: String) {
        val c = card ?: return
        val entries = (c.state as? WordState.Found)?.lookup?.entries.orEmpty()
        if (c.query.word.equals(word, ignoreCase = true) || entries.any { it.lemma.equals(word, ignoreCase = true) }) fetch(c.query, quiet = true)
    }

    /** Review items were added: entries of the open card that are among them are known now. */
    fun onWordsAdded(itemIds: List<String>) {
        val c = card ?: return
        val found = c.state as? WordState.Found ?: return
        if (found.lookup.entries.none { it.itemId in itemIds }) return
        val entries = found.lookup.entries.map { if (it.itemId != null && it.itemId in itemIds) it.copy(known = true) else it }
        card = c.copy(state = WordState.Found(found.lookup.copy(entries = entries)))
    }
}
