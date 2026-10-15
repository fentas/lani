package si.lanisce.lani.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.Counting
import si.lanisce.lani.data.CourseUnit
import si.lanisce.lani.data.Dashboard
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.l10n.bi
import java.time.LocalDate

/**
 * Counting cards ("🔢 Štetje · Counting", data/Counting.kt; companion/README.md "Counting cards"): on once the intro of the
 * A1 unit stevila is done ([Counting.active]; the note when it ends: [introDone]); then the bridge is asked for the cards
 * of the learner's learned nouns (POST /counting), a few new a day ([Counting.room]), the nouns whose forms the phone has
 * ([FormsController]) first. A bridge without them (an older one: GET /state's features lack "counting") is never asked.
 */
class CountingController(
    private val scope: CoroutineScope,
    private val bridge: () -> Bridge?,
    private val game: GameController,
    private val content: ContentController,
    private val forms: FormsController,
    private val notices: Notices,
) {
    /** The nouns the bridge made no card of today (and the day): not asked again until tomorrow. */
    private var refused: Pair<LocalDate, Set<String>> = LocalDate.now() to emptySet()

    /** Counting is on for this learner, and their bridge makes the cards. */
    fun on(d: Dashboard? = content.dashboard): Boolean =
        d != null && Counting.active(game.state) && Counting.FEATURE in d.features && (d.language ?: d.pair.target.code) == Counting.LANGUAGE

    /**
     * The day's new counting cards asked of [b], when counting is on and today has room: the learner's learned nouns the
     * dictionary counts, best known first. True when the bridge made any (the dashboard is loaded again then: the review
     * has them at once).
     */
    suspend fun reload(b: Bridge): Boolean {
        val d = content.dashboard ?: return false
        if (!on(d)) return false
        val today = LocalDate.now()
        if (Counting.room(d.counting, today) <= 0) return false
        val no = refused.takeIf { it.first == today }?.second.orEmpty()
        val want = Counting.candidates(d.pool, d.learned, d.counting, forms::entriesOf, no)
        if (want.isEmpty()) return false
        val answer = try {
            b.counting(want)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false // offline: the next reload asks again
        } ?: return false
        // what the bridge won't make (not learned there, no form in its dictionary) isn't asked again today; the day's cap
        // ends the day's asking anyway
        refused = today to (no + answer.skipped.filter { !it.why.startsWith("today's") }.map { Counting.key(it.word) })
        if (answer.added.isEmpty()) return false
        content.reloadDashboard(b)
        return true
    }

    /**
     * The intro of [unit] ended (finished or skipped) for the first time: the unit stevila's turns counting on, said once
     * ("🔢 Kartice za štetje so odslej v tvojih ponovitvah. · Counting cards are in your reviews from now on."), and the
     * first cards are asked for.
     */
    fun introDone(unit: CourseUnit) {
        if (unit.id != Counting.UNIT || unit.language != Counting.LANGUAGE) return
        notices.banner = "🔢 ${bi("counting.on")}"
        val b = bridge() ?: return
        scope.launch { runCatching { reload(b) } }
    }

    /**
     * QA's "counting" (app/QaHooks, a debug build): today's cards asked for now (the day's cap as ever), then up to
     * [Counting.PER_REVIEW] of the learner's counting cards, due or not, for a review; none when there are none.
     */
    suspend fun forQa(b: Bridge): List<ReviewCard> {
        runCatching { reload(b) }
        return content.dashboard?.counting.orEmpty().sortedBy { it.due }.take(Counting.PER_REVIEW)
    }
}
