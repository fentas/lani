package si.lanisce.lani.game

import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import si.lanisce.lani.data.ReviewCard

/**
 * What a dialog's answers on the learner's words ([WordAnswer]: only the words a turn tests, [DialogWords]) do to their
 * review cards, kept honest for SM-2 (companion/GAME.md, "Your words in the dialogs"). Pure.
 *
 * - **A right answer is a review** of a card that is due or nearly ([counts]: due within a tenth of its interval, one to
 *   three days, and not reviewed or added today), of quality 4 chosen or tapped, 5 typed or said: its easiness never
 *   drops, so it is never worse than doing nothing. A card not due yet is only met ([Kind.MET]): reviewing it early would
 *   stretch its interval on too little.
 * - **A wrong answer about the word** (another word picked in its place, the wrong thing tapped, another word typed) lowers
 *   its card gently ([Kind.LOWERED]): the bridge brings it back tomorrow, halves its interval and takes a little of its
 *   easiness, without resetting it, since it may have been the sentence's grammar, not the word. Its card shows after the
 *   turn. A wrong form of it ([WordTest.FORM]) leaves the card alone ([Kind.FORM]): the grammar book's rule counts it.
 * - **Once a card a day**, by the first answer on it that day ([Ledger]): the same dialog again, or another, counts
 *   nothing more ([Kind.AGAIN]).
 */
object DialogReviews {
    /** The most days before its due date a dialog's answer still reviews a card. */
    const val MAX_WINDOW = 3

    /** How many days before its due date a card with [interval] days may be reviewed in a dialog: a tenth of it, 1 to 3. */
    fun window(interval: Int): Int = (interval.coerceAtLeast(1) / 10.0).roundToInt().coerceIn(1, MAX_WINDOW)

    /**
     * Whether a right answer [today] reviews [card]: due today or before, or within its [window], and not reviewed today
     * (a card added today was too). A card that doesn't say when it is due is due.
     */
    fun counts(card: ReviewCard, today: LocalDate): Boolean {
        if (card.lastReviewed?.isBefore(today) == false) return false
        val due = card.due ?: return true
        return !due.isAfter(today.plusDays(window(card.interval).toLong()))
    }

    /** The SM-2 quality of a right answer: 4 chosen or tapped (recognised), 5 typed or said (produced). */
    fun quality(a: WordAnswer): Int = if (a.produced) 5 else 4

    enum class Kind {
        /** A review of its card ([Outcome.quality]). */
        REVIEW,
        /** Right, its card not due yet: met, nothing changes. */
        MET,
        /** Got wrong about the word: its card lowered gently. */
        LOWERED,
        /** Its form got wrong: the card as it was (the rule counts the slip). */
        FORM,
        /** Its card counted today already: nothing more. */
        AGAIN,
    }

    data class Outcome(val answer: WordAnswer, val kind: Kind, val quality: Int? = null)

    /** Which cards a dialog counted on [day] and how ([Kind] names): each at most once a day. */
    @Serializable
    data class Ledger(val day: String = "", val cards: Map<String, String> = emptyMap()) {
        /** The ledger of [today]: this one, or a fresh one the day after. */
        fun on(today: LocalDate): Ledger = if (day == today.toString()) this else Ledger(today.toString())
    }

    /**
     * What [answers] (in order) come to [today], with each card as the learner has it ([cardOf]; null: not one of theirs,
     * nothing), and the [ledger] after them.
     */
    fun settle(answers: List<WordAnswer>, cardOf: (String) -> ReviewCard?, ledger: Ledger, today: LocalDate): Pair<List<Outcome>, Ledger> {
        var led = ledger.on(today)
        val out = answers.mapNotNull { a ->
            val card = cardOf(a.card.id) ?: return@mapNotNull null
            if (card.id in led.cards) return@mapNotNull Outcome(a, Kind.AGAIN)
            val o = when {
                a.right && counts(card, today) -> Outcome(a, Kind.REVIEW, quality(a))
                a.right -> Outcome(a, Kind.MET)
                a.how == WordTest.FORM -> Outcome(a, Kind.FORM)
                else -> Outcome(a, Kind.LOWERED)
            }
            led = led.copy(cards = led.cards + (card.id to o.kind.name.lowercase()))
            o
        }
        return out to led
    }
}
