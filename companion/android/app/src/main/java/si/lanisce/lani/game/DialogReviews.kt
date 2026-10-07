package si.lanisce.lani.game

import java.time.LocalDate
import si.lanisce.lani.data.ReviewCard

/**
 * What a dialog's answers on the learner's words ([WordAnswer]: only the words a turn tests, [DialogWords]) do to their
 * review cards, kept honest for SM-2 (companion/GAME.md, "Your words in the dialogs"): words met in play, as «Vidim,
 * vidim»'s are ([PlayReviews]: the window, once a card a day, the village state's record of it). Pure.
 *
 * - **A right first answer is a review** of a card due or nearly ([PlayReviews.counts]: due within a tenth of its interval,
 *   one to three days, and not reviewed today, by the deck or in play), of quality 4 chosen or tapped
 *   ([PlayReviews.QUALITY]), 5 typed or said (produced, as the deck's typed answer): its easiness never drops, so it is
 *   never worse than doing nothing. A card not due yet is only met ([Kind.MET]): reviewing it early would stretch its
 *   interval on too little.
 * - **A wrong answer about the word** (another word picked in its place, the wrong thing tapped, another word typed) lowers
 *   its card gently ([Kind.LOWERED]): the bridge brings it back tomorrow, halves its interval and takes a little of its
 *   easiness, without resetting it, since it may have been the sentence's grammar, not the word. Its card shows after the
 *   turn. A wrong form of it ([WordTest.FORM]) leaves the card alone ([Kind.FORM]): the grammar book's rule counts it.
 * - **A card's schedule changes once a day by play**: a card reviewed or lowered today, in a dialog or in «Vidim, vidim»,
 *   counts nothing more ([Kind.AGAIN]); in one dialog the first answer on a card decides.
 */
object DialogReviews {
    /** The SM-2 quality of a right answer: chosen or tapped (recognised) as play's, typed or said (produced) 5. */
    fun quality(a: WordAnswer): Int = if (a.produced) 5 else PlayReviews.QUALITY

    enum class Kind {
        /** A review of its card ([Outcome.quality]). */
        REVIEW,
        /** Right, its card not due yet: met, nothing changes. */
        MET,
        /** Got wrong about the word: its card lowered gently. */
        LOWERED,
        /** Its form got wrong: the card as it was (the rule counts the slip). */
        FORM,
        /** Its card's schedule changed today already (or the dialog answered it before): nothing more. */
        AGAIN,
    }

    data class Outcome(val answer: WordAnswer, val kind: Kind, val quality: Int? = null) {
        /** It changes its card's schedule today: what the village state records ([PlayReviews.record]). */
        val changes: Boolean get() = kind == Kind.REVIEW || kind == Kind.LOWERED
    }

    /**
     * What [answers] (in order) come to [today], with each card as the learner has it ([cardOf]; null: not one of theirs,
     * nothing), the cards whose schedule play changed today already ([counted], [PlayReviews.counted]) and those this
     * dialog answered before ([answered]: the first answer decides).
     */
    fun settle(answers: List<WordAnswer>, cardOf: (String) -> ReviewCard?, counted: Set<String>, today: LocalDate, answered: Set<String> = emptySet()): List<Outcome> {
        val seen = answered.toMutableSet()
        return answers.mapNotNull { a ->
            val card = cardOf(a.card.id) ?: return@mapNotNull null
            if (!seen.add(card.id) || card.id in counted) return@mapNotNull Outcome(a, Kind.AGAIN)
            when {
                a.right && PlayReviews.counts(card, today, counted) -> Outcome(a, Kind.REVIEW, quality(a))
                a.right -> Outcome(a, Kind.MET)
                a.how == WordTest.FORM -> Outcome(a, Kind.FORM)
                else -> Outcome(a, Kind.LOWERED)
            }
        }
    }
}
