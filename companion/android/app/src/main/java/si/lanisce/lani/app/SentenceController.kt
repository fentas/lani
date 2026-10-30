package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.SentenceWord
import si.lanisce.lani.data.Sentences
import si.lanisce.lani.data.VisitWorld
import si.lanisce.lani.data.WordLookup
import si.lanisce.lani.game.Forms
import si.lanisce.lani.game.SentenceGrammar
import si.lanisce.lani.game.WordReading
import si.lanisce.lani.game.WordRow
import si.lanisce.lani.l10n.L10n

/**
 * A line whose grammar the learner asked for (a long press on it): what is said ([sentence]) and meant ([en]), where
 * ([scene] and its [sceneTitle], [dialog], the [person] talking), and for the learner's own line the pages of its turn's
 * rule ([pages]).
 */
data class SentenceQuery(
    val sentence: String,
    val en: String,
    val scene: String? = null,
    val sceneTitle: String? = null,
    val dialog: String? = null,
    val person: String? = null,
    val pages: List<String> = emptyList(),
)

/**
 * The sheet of a line's grammar: its [rows] (each word, its form and why), whether the dictionary is still being asked
 * ([loading]) or couldn't be ([offline]: the words alone), and the tutor's explanation kept from an earlier question
 * ([note]).
 */
data class SentenceCard(val query: SentenceQuery, val rows: List<WordRow>, val loading: Boolean = true, val offline: Boolean = false, val note: String? = null)

/**
 * "🔍 Slovnica stavka · The sentence's grammar" (companion/SCENES.md, "The sentence's grammar"): a line's words looked up
 * (GET /lookup, each in the line, as a word card does: its reading and the case lens's answer, the same analysis the card
 * shows), explained by [SentenceGrammar], the tutor's kept explanation (GET /sentence), and asking the tutor about the
 * line (a chat with `about_sentence`). One [card] at a time; the sheet shows it over any screen. A look the lens counts
 * as seen for its rule goes to the case trainer ([caseSeen]), as the word card's does.
 */
class SentenceController(
    private val scope: CoroutineScope,
    private val bridge: () -> Bridge?,
    private val chat: ChatController,
    private val grammar: GrammarController,
    private val caseSeen: (String) -> Unit = {},
) {
    var card by mutableStateOf<SentenceCard?>(null)
        private set
    private var job: Job? = null

    private fun has(id: String): Boolean = grammar.page(id) != null

    fun open(q: SentenceQuery) {
        job?.cancel()
        // the words at once, without the dictionary: what a rule knows from the line alone
        card = SentenceCard(q, SentenceGrammar.analyze(q.sentence, emptyList(), ::has))
        job = scope.launch {
            val b = bridge()
            val pair = L10n.pair
            // on a visit: the town's language, explained in the visit's base; at home, in the pair's base
            val language = VisitWorld.language
            val meaningIn = pair.base.code.takeIf { pair.base != pair.target }
            var offline = b == null
            val readings: List<WordReading?> = if (b == null) emptyList() else {
                val words = Forms.spans(q.sentence).map { it.value }
                // each word's place among the same words (GET /lookup's n): the lens answers for that place
                val nth = words.mapIndexed { i, w -> words.take(i).count { it.equals(w, ignoreCase = true) } }
                suspend fun look(w: String, n: Int): WordLookup? = try {
                    b.lookup(w, q.sentence, q.en, language = language, meaningIn = meaningIn, nth = n)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    offline = true
                    null
                }
                // the first of each word, then the others of a word with a case (the lens may read them otherwise)
                val first = coroutineScope { words.distinctBy { it.lowercase() }.map { w -> async { w.lowercase() to look(w, 0) } }.awaitAll().toMap() }
                val more = coroutineScope {
                    words.indices.filter { nth[it] > 0 && first[words[it].lowercase()]?.lens != null }
                        .map { i -> async { i to look(words[i], nth[i]) } }.awaitAll().toMap()
                }
                words.indices.map { i ->
                    val found = if (nth[i] > 0) more[i] ?: first[words[i].lowercase()]?.takeIf { it.lens == null } else first[words[i].lowercase()]
                    found?.lens?.takeIf { it.seen }?.rule?.let(caseSeen)
                    // (a word the dictionary has no entry of may still have the lens's case: a name by its ending, Prešeren)
                    found?.entries?.firstOrNull()?.let { WordReading(it.lemma, it.pos, it.grammar, found.lens) }
                        ?: found?.lens?.let { WordReading(words[i], null, null, it) }
                }
            }
            val note = if (b == null) null else try {
                b.sentenceNote(q.sentence, language, meaningIn)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            card?.takeIf { it.query == q }?.let {
                card = it.copy(rows = SentenceGrammar.analyze(q.sentence, readings, ::has), loading = false, offline = offline, note = note)
            }
        }
    }

    fun close() {
        job?.cancel()
        card = null
    }

    /** The tutor explained [sentence] (sentence_explained): the sheet open on it shows the explanation. */
    fun onExplained(sentence: String) {
        val c = card ?: return
        if (!Sentences.same(c.query.sentence, sentence)) return
        scope.launch {
            val b = bridge() ?: return@launch
            val pair = L10n.pair
            val note = try {
                b.sentenceNote(c.query.sentence, VisitWorld.language, pair.base.code.takeIf { pair.base != pair.target })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch
            }
            card?.takeIf { it.query == c.query }?.let { card = it.copy(note = note ?: it.note) }
        }
    }

    /** "💬 Vprašaj učitelja · Ask your tutor": the line goes to the chat, with where it was said and its words as read here. */
    fun ask() {
        val c = card ?: return
        close()
        val q = c.query
        val words = c.rows.map { r -> SentenceWord(r.word, r.lemma, r.reading, r.why?.let(SentenceGrammar::why), r.page) }
        val language = VisitWorld.language ?: grammar.language
        chat.ask(Sentences.askText(q.sentence), Sentences.askData(q.sentence, q.en, language, q.scene, q.sceneTitle, q.dialog, q.person, q.pages, words))
    }
}
