package si.lanisce.lani.game

import kotlinx.serialization.Serializable
import si.lanisce.lani.data.ReviewCard
import java.time.LocalDate

/** The cards a word met in play counted a review for on day [on] ([PlayReviews]). */
@Serializable
data class PlayReviewDay(val on: String, val cards: List<String> = emptyList())

/**
 * A word met in play counts as a review of its card (companion/GAME.md, "Words met in play"): «Vidim, vidim»'s quick find
 * now, a dialog's word later. Not every time: a card is reviewed in play at most once a day, only when it is due or nearly
 * due (a review then is one the deck would ask soon anyway), and only with a good answer, never worse than nothing: no
 * failed review (a wrong tap or a word shown counts none), no quality that lowers the card's ease. Pure; the village state
 * keeps today's ([GameState.playReviews]), so a restart or another phone doesn't count a card twice.
 */
object PlayReviews {
    /** How many days before it is due a card may be reviewed in play: due today (or before), or tomorrow. */
    const val EARLY_DAYS = 1L

    /**
     * The SM-2 quality of a word recognised in play: 4, a right answer with some thought, which leaves the card's ease as
     * it is and lengthens its interval (5 would raise the ease for what was only recognised; 3 would lower it).
     */
    const val QUALITY = 4

    /**
     * Whether [card] is due by [today] or within [EARLY_DAYS] after it. A card without a due date isn't: one the phone made
     * from a pack's run before the node has it was reviewed today ([si.lanisce.lani.data.Dashboard.learnedWords]).
     */
    fun due(card: ReviewCard, today: LocalDate): Boolean = card.due?.let { !it.isAfter(today.plusDays(EARLY_DAYS)) } ?: false

    /** The cards counted in play today in [s]. */
    fun counted(s: GameState?, today: LocalDate): Set<String> =
        s?.playReviews?.takeIf { it.on == today.toString() }?.cards.orEmpty().toSet()

    /**
     * Whether a good answer on [card] in play counts as its review [today]: a vocabulary card, due or nearly due ([due]),
     * not reviewed today already (by the deck: [ReviewCard.lastReviewed]; in play: [counted]).
     */
    fun counts(card: ReviewCard?, today: LocalDate, counted: Set<String>): Boolean =
        card != null && card.kind == "vocabulary" && card.id !in counted && card.lastReviewed != today && due(card, today)

    /** The reviews of [cards] (each counts: [counts]) as the deck sends them (item id → quality), once each. */
    fun results(cards: List<ReviewCard>): List<Pair<String, Int>> = cards.distinctBy { it.id }.map { it.id to QUALITY }

    /** [s] with [ids] counted in play [today] (yesterday's list goes). */
    fun record(s: GameState, ids: Collection<String>, today: LocalDate): GameState {
        if (ids.isEmpty()) return s
        val before = counted(s, today)
        if (before.containsAll(ids)) return s
        return s.copy(playReviews = PlayReviewDay(today.toString(), (before + ids).toList()))
    }
}
