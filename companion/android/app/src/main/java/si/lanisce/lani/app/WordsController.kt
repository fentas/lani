package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.PackSession
import si.lanisce.lani.data.PackWord
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.BridgeEvent
import si.lanisce.lani.data.VisitWorld
import si.lanisce.lani.data.WaitingWord
import si.lanisce.lani.data.WordEntry
import si.lanisce.lani.data.WordLookup
import si.lanisce.lani.data.WordSuggestion
import si.lanisce.lani.data.Words
import si.lanisce.lani.data.Writes
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi

/**
 * A word tapped in a line: the form as written ([word]), the line it's in and its translation ([en], when there is
 * one), and where it was tapped ([from]: [Words.SCENE], [Words.ROLEPLAY] …).
 *
 * An exercise's word: [example], the line a word added from the card keeps when it isn't [line] (a sentence's gap filled,
 * which the card doesn't show while the exercise is open); [forms] false while it is open (a word the learner has shows
 * no table of its forms: one of them may be the answer); [language], the language of the exercise's words when it isn't
 * the world's (another language's review at home; null: the visited town's, else the home language).
 */
data class WordQuery(
    val word: String,
    val line: String,
    val en: String?,
    val from: String,
    val example: String? = null,
    val forms: Boolean = true,
    val language: String? = null,
) {
    /** The language the word is in: [language], else the visited town's (null at home). */
    val lang: String? get() = language ?: VisitWorld.language
}

/** How looking a word up went. */
sealed interface WordState {
    data object Loading : WordState
    data class Found(val lookup: WordLookup) : WordState
    /**
     * The dictionary doesn't know it: the words it may be instead ([WordLookup.suggestions]), "➕ Add to my words" (the
     * tutor writes its meaning: [WordsController.request]; [WordLookup.requested]: asked for already), and the tutor to ask.
     */
    data class NotFound(val lookup: WordLookup) : WordState
    /** The node is older than lookups (404). */
    data object Unavailable : WordState
    /** The node can't be reached. */
    data object Offline : WordState
}

/** An entry being added to the learner's words: on its way, on the node, or waiting in the outbox (offline). */
enum class Adding { SENDING, ADDED, QUEUED }

/**
 * A word the dictionary doesn't know, asked for from its card ([WordsController.request]): on its way, waiting on the
 * node for the tutor's meaning, waiting in the outbox (offline), or done (its meaning came: the card looks again).
 */
enum class Requesting { SENDING, WAITING, QUEUED, DONE }

/**
 * The word card on screen: what was tapped, what's known about it, which entries were added (by [WordEntry.key]), and,
 * for a word the dictionary didn't know, how asking for it goes ([request]; [requestedItem]: the review item it became).
 */
data class WordCard(
    val query: WordQuery,
    val state: WordState = WordState.Loading,
    val added: Map<String, Adding> = emptyMap(),
    val request: Requesting? = null,
    val requestedItem: String? = null,
)

/**
 * Looking up words tapped in dialogs, role-plays and villagers' lines (GET /lookup), adding them to the learner's
 * words (POST /words, through the outbox), and a word the dictionary doesn't know: one it may be instead ("Did you
 * mean …?"), asked for with one tap (POST /words/requests: the tutor writes its meaning and the node adds it), or the
 * tutor asked in the chat. One [card] at a time; the sheet shows it over any screen.
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

    /**
     * Pack words just learned on this phone, by language (null: the home language's), as the cards the node will make
     * of them ([PackSession.card]): a pack run's words once introduced, which its practice asks right away. They are the
     * learner's at once ("packs count at once"), while the node has their cards only once the run ends and the outbox
     * sends it: the word card ([Words.yours]) and an exercise's dots (KnownWords) count them.
     */
    var fresh by mutableStateOf<Map<String?, List<ReviewCard>>>(emptyMap())
        private set

    /** The words of pack [packId] a run has just introduced ([words]) are the learner's from now on, in [language]. */
    fun learning(packId: String, words: List<PackWord>, language: String? = VisitWorld.language) {
        val had = fresh[language].orEmpty()
        val cards = words.map { PackSession.card(packId, it) }.filter { c -> had.none { it.id == c.id } }
        if (cards.isNotEmpty()) fresh = fresh + (language to had + cards)
    }

    /** The learner's words in [language] (null: the home language's) for [Words.yours]: [deck]'s cards and the fresh ones. */
    fun mine(language: String?, deck: List<ReviewCard>?): Words.Mine =
        Words.Mine.of(deck.orEmpty() + fresh[language].orEmpty(), language ?: L10n.ownPair.target.code)

    /** The words asked for that wait for their meaning ("📚 Moje besede"): [loadWaiting] fills it. */
    var waiting by mutableStateOf<List<WaitingWord>>(emptyList())
        private set

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

    /**
     * [word] looked up in the home language's dictionary, as a tapped word is (GET /lookup, explained in the pair's base):
     * null from a node older than lookups; an exception when no node can be reached ("📚 Moje besede"'s search).
     */
    suspend fun lookUp(word: String): WordLookup? {
        val b = bridge() ?: throw java.io.IOException("not connected")
        val pair = L10n.pair
        return b.lookup(word, null, null, meaningIn = pair.base.code.takeIf { pair.base != pair.target })
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
                when (val l = b.lookup(q.word, q.line, q.en, language = q.lang, meaningIn = pair.base.code.takeIf { pair.base != pair.target })) {
                    null -> WordState.Unavailable
                    else -> if (l.entries.isEmpty()) WordState.NotFound(l) else WordState.Found(l)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (quiet) return@launch
                WordState.Offline
            }
            card?.takeIf { it.query == q }?.let { c ->
                // the word asked for came back as an entry: it shows added (the node added it with its meaning)
                val asked = c.requestedItem?.let { id -> (state as? WordState.Found)?.lookup?.entries?.filter { it.itemId == id } }.orEmpty()
                card = c.copy(state = state, added = c.added + asked.associate { it.key to Adding.ADDED })
            }
        }
    }

    /** Adds [e] to the learner's words; it shows ✓ at once, and whether it reached the node or waits for it. */
    fun add(e: WordEntry) {
        val c = card ?: return
        if (e.known || e.key in c.added) return
        val q = c.query
        // on a visit: the host town's language (its own profile, plan 2, §3.4); another deck's review: its language
        val language = q.lang
        card = c.copy(added = c.added + (e.key to Adding.SENDING))
        scope.launchSafely(notices::fail) {
            val write = Writes.addWord(
                sl = e.lemma,
                en = Words.meaning(e),
                pos = e.pos,
                itemId = e.itemId,
                example = Words.example(q.example ?: q.line, q.en.takeIf { q.example == null }, e),
                from = sourceFor(q.from),
                language = language,
            )
            val sent = sync.submit(write)
            card?.takeIf { it.query == q }?.let { card = it.copy(added = it.added + (e.key to if (sent) Adding.ADDED else Adding.QUEUED)) }
        }
    }

    /**
     * "➕ Add to my words" on a word the dictionary doesn't know: the node asks the tutor for its meaning (POST
     * /words/requests, through the outbox: queued offline, kept across restarts) and adds the word when it comes. The card
     * stays open, saying the tutor is writing it, and fills in then ([onWordRequestDone], [onLexiconUpdated]); closed
     * meanwhile, a quiet notice says so. On a visit the word is the town's language's, its meaning in the visit's base.
     */
    fun request() {
        val c = card ?: return
        if (c.state !is WordState.NotFound || c.request != null || c.state.lookup.requested) return
        val q = c.query
        val word = Words.requestable(q.word) ?: return
        card = c.copy(request = Requesting.SENDING)
        val back = { card?.takeIf { it.query == q && it.request == Requesting.SENDING }?.let { card = it.copy(request = null) } }
        scope.launchSafely({ e -> back(); notices.fail(e) }) {
            val pair = L10n.pair
            val body = Words.requestData(word, q.line, q.en, sourceFor(q.from), q.lang, pair.base.code.takeIf { pair.base != pair.target })
            val e = Writes.requestWord(body, Words.addText(word, q.line), Words.askData(word, q.line, q.en, q.lang, add = true))
            val sent = sync.submit(e)
            // not sent and not waiting either: the node refused it (the snackbar says why), the button is back
            val state = when {
                sent -> Requesting.WAITING
                sync.queued(e.path).any { it.clientId == e.clientId } -> Requesting.QUEUED
                else -> null
            }
            card?.takeIf { it.query == q && it.request == Requesting.SENDING }?.let { card = it.copy(request = state) }
            loadWaiting()
        }
    }

    /** A word the dictionary may mean instead ("Did you mean …?"): its card, in the same line. */
    fun openSuggestion(s: WordSuggestion) {
        val q = card?.query ?: return
        open(q.copy(word = s.word))
    }

    /**
     * A word asked for got its meaning (word_request_done): the card open on it shows it added and looks again (its entry
     * is the tutor's now); else a quiet notice says it is among the learner's words, with its meaning.
     */
    fun onWordRequestDone(ev: BridgeEvent.WordRequestDone) {
        val c = card
        val word = c?.query?.word?.let(Words::requestable)
        val onCard = c != null && word != null && word.equals(ev.word, ignoreCase = true) && (ev.language == null || c.query.lang == null || ev.language == c.query.lang)
        if (onCard) {
            card = c!!.copy(request = Requesting.DONE, requestedItem = ev.itemId)
            fetch(c.query, quiet = true)
        } else if (ev.added) {
            val meaning = ev.gloss.map { it.trim() }.filter { it.isNotEmpty() }.take(2).joinToString(", ")
            notices.banner = "📚 ${bi("words.nowInYourWords")}: «${ev.lemma}»" + if (meaning.isNotEmpty()) " — $meaning" else ""
        }
        // QA reads where it showed (a debug or qa build)
        if (si.lanisce.lani.BuildConfig.QA) runCatching { android.util.Log.i("WordRequest", "done «${ev.word}» ${if (onCard) "card" else if (ev.added) "notice" else "quiet"}") }
        scope.launchSafely({}) { loadWaiting() }
    }

    /** The words waiting for their meaning: the node's (GET /words/requests) and those still in the outbox. */
    suspend fun loadWaiting() {
        val node = try {
            bridge()?.wordRequests()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // offline: the outbox's at least
        }
        val queued = sync.queued("/words/requests").map { it.body }
        waiting = Words.waiting(node ?: waiting.filter { !it.queued }, queued)
    }

    /** Asks the tutor what the word means in its line; the chat opens for the answer. */
    fun ask() {
        val q = card?.query ?: return
        close()
        chat.ask(Words.askText(q.word, q.line), Words.askData(q.word, q.line, q.en, q.lang))
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
