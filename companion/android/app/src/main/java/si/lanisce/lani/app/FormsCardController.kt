package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.Course
import si.lanisce.lani.data.CourseUnit
import si.lanisce.lani.data.Dashboard
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.FormsCard
import si.lanisce.lani.data.FormsCardsAnswer
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.l10n.bi
import java.time.LocalDate
import kotlin.random.Random

/** How making a word's forms card on demand ("📋 Vse oblike · All forms" on its card) goes. */
sealed interface FormsAsk {
    data object Making : FormsAsk
    /** Made (or there already): its review card. */
    data class Made(val cardId: String) : FormsAsk
    /** The node made none: nothing of its table introduced yet ([early]), or the dictionary has too little of it. */
    data class None(val early: Boolean) : FormsAsk
    /** No node now, or an older one: try again later. */
    data object Offline : FormsAsk
}

/**
 * Forms cards ("📋 Oblike · Forms", data/FormsCard.kt; companion/README.md "Forms cards"): the node is asked for the
 * cards of the learner's learned words whose forms the phone has ([FormsController]), a few new a day ([FormsCard.room]),
 * the core verbs first, and of a word at once from its card ([make]); each request says the grammar pages introduced
 * ([introduced]), so the node knows what each card asks and brings back soon the cards a page just opened rows of (a
 * unit's intro done: [introDone]). A review builds each card's table from the same pages ([exercises]). A bridge without
 * them (an older one: GET /state's features lack "forms-cards") is never asked.
 */
class FormsCardController(
    private val scope: CoroutineScope,
    private val bridge: () -> Bridge?,
    private val game: GameController,
    private val content: ContentController,
    private val forms: FormsController,
    private val notices: Notices,
    private val course: () -> Course?,
) {
    /** The words the node made no card of today (and the day): not asked again until tomorrow. */
    private var refused: Pair<LocalDate, Set<String>> = LocalDate.now() to emptySet()

    /** The pages introduced the node last heard of in this run: asked again when they change. */
    private var told: Set<String>? = null

    /** On-demand cards being made or made, by [FormsCard.key] of the word. */
    var asks by mutableStateOf<Map<String, FormsAsk>>(emptyMap())
        private set

    /** Forms cards are on: the learner's bridge makes them, for their home language (Slovene). */
    fun on(d: Dashboard? = content.dashboard): Boolean =
        d != null && FormsCard.FEATURE in d.features && (d.language ?: d.pair.target.code) == FormsCard.LANGUAGE

    /** The grammar pages introduced to the learner now ([FormsCard.introduced]). */
    fun introduced(): Set<String> = FormsCard.introduced(game.state, course(), FormsCard.LANGUAGE)

    /** The learner's forms card of [lemma], if any. */
    fun cardOf(lemma: String): ReviewCard? = content.dashboard?.formsCards?.let { FormsCard.cardOf(it, lemma) }

    /**
     * The day's new forms cards asked of [b] (the learner's learned words the phone has forms of, core verbs first) while
     * today has room, and the pages introduced told when they changed: the node brings back soon the cards they opened
     * rows of. True when the node made or brought back any (the dashboard is loaded again then).
     */
    suspend fun reload(b: Bridge): Boolean {
        val d = content.dashboard ?: return false
        if (!on(d)) return false
        val today = LocalDate.now()
        val no = refused.takeIf { it.first == today }?.second.orEmpty()
        val want = if (FormsCard.room(d.formsCards, today) > 0) FormsCard.candidates(d.pool, d.learned, d.formsCards, forms::entriesOf, no) else emptyList()
        val pages = introduced()
        if (want.isEmpty() && pages == told) return false
        val answer = ask(b, want, pages, asked = false) ?: return false
        told = pages
        // what the node won't make (not learned there, too few forms) isn't asked again today; the day's cap ends it anyway
        refused = today to (no + answer.skipped.filter { !it.why.startsWith("today's") }.map { FormsCard.key(it.word) })
        if (answer.added.none { !it.existing } && answer.resurfaced.isEmpty()) return false
        reloadDashboard(b)
        return true
    }

    private suspend fun ask(b: Bridge, words: List<Pair<String, String?>>, pages: Set<String>, asked: Boolean): FormsCardsAnswer? = try {
        b.formsCards(words, pages, asked)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null // offline: the next reload asks again
    }

    private suspend fun reloadDashboard(b: Bridge) {
        try {
            content.reloadDashboard(b)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // nothing more: the cards come with the next reload (words_added asks for one too)
        }
    }

    /**
     * "📋 Vse oblike · All forms" on the card of [lemma] (its review item [itemId], if the learner has it): its forms card
     * made at once, whatever the day's count ([asks]: [FormsAsk.Made] with the card, which [play] reviews now).
     */
    fun make(lemma: String, itemId: String?) {
        val k = FormsCard.key(lemma)
        cardOf(lemma)?.let { asks = asks + (k to FormsAsk.Made(it.id)); return }
        val b = bridge() ?: run { asks = asks + (k to FormsAsk.Offline); return }
        if (asks[k] == FormsAsk.Making) return
        asks = asks + (k to FormsAsk.Making)
        scope.launch {
            val answer = ask(b, listOf(lemma to itemId), introduced(), asked = true)
            val made = answer?.added?.firstOrNull()
            asks = asks + (k to when {
                answer == null -> FormsAsk.Offline
                made == null -> FormsAsk.None(early = answer.skipped.any { "introduced" in it.why })
                else -> {
                    if (!made.existing || cardOf(lemma) == null) reloadDashboard(b)
                    cardOf(lemma)?.let { FormsAsk.Made(it.id) } ?: FormsAsk.Offline
                }
            })
        }
    }

    /**
     * The intro of [unit] ended (finished or skipped) for the first time: its pages are introduced now; the node hears of
     * them at once, brings back soon the cards they open rows of and makes the day's new ones; when it brought some back,
     * a note says so ("📋 Nove oblike v tabelah: delati, hiša · New forms in your tables"), after the pages' news.
     */
    fun introDone(unit: CourseUnit) {
        if (unit.language != FormsCard.LANGUAGE || !on()) return
        val b = bridge() ?: return
        scope.launch {
            val before = content.dashboard?.formsCards.orEmpty().associate { it.id to it.forms?.fresh.orEmpty() }
            runCatching { reload(b) }
            val back = content.dashboard?.formsCards.orEmpty().filter { c -> c.forms?.fresh.orEmpty().let { it.isNotEmpty() && it != before[c.id] } }
            if (back.isEmpty()) return@launch
            withTimeoutOrNull(NEWS_WAIT_MS) { while (notices.banner != null) delay(200) }
            notices.banner = "📋 ${bi("formsCard.back", "words" to back.take(4).joinToString(", ") { it.forms?.lemma ?: it.front })}"
        }
    }

    /**
     * Each forms card's exercise in this review ([FormsCard.exercise]): the pages introduced now, typed once the card is
     * secure (QA's "formscard:type": all of them), its options shuffled the same way through the day.
     */
    fun exercises(): (ReviewCard) -> Exercise.FormsTable? {
        val pages = introduced()
        val today = LocalDate.now()
        return { card ->
            card.forms?.let { d ->
                val typed = QaHooks.formsTyped || FormsCard.secure(card)
                FormsCard.exercise(card, d, pages, typed, today, Random(card.id.hashCode() * 31 + card.reviews))
            }
        }
    }

    /**
     * QA's "formscard" (app/QaHooks, a debug build): today's cards asked for now (the day's cap as ever), then up to
     * [FormsCard.PER_REVIEW] of the learner's forms cards that ask something, due or not ([lemma]: that word's, made on
     * demand first when it has none).
     */
    suspend fun forQa(b: Bridge, lemma: String?): List<ReviewCard> {
        runCatching { reload(b) }
        if (lemma != null && cardOf(lemma) == null) {
            ask(b, listOf(lemma to null), introduced(), asked = true)
            reloadDashboard(b)
        }
        val ex = exercises()
        val all = content.dashboard?.formsCards.orEmpty().filter { ex(it) != null }
        return if (lemma != null) all.filter { FormsCard.key(it.forms?.lemma ?: it.front) == FormsCard.key(lemma) }
        else all.sortedBy { it.due }.take(FormsCard.PER_REVIEW)
    }

    private companion object {
        /** The longest the note waits for the news on screen before it. */
        const val NEWS_WAIT_MS = 12_000L
    }
}
