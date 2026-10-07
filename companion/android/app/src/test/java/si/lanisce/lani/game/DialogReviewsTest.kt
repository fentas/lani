package si.lanisce.lani.game

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.game.DialogReviews.Kind

/**
 * What a dialog's answers on the learner's words do to their cards (companion/GAME.md, "Your words in the dialogs"): a
 * right one reviews a card due or nearly, a slip about the word lowers it gently, a wrong form leaves it; a card's
 * schedule changes once a day by play, «Vidim, vidim» and the dialogs alike ([PlayReviews]).
 */
class DialogReviewsTest {
    private val today = LocalDate.of(2026, 10, 7)
    private fun card(id: String, due: LocalDate?, interval: Int = 6, last: LocalDate? = today.minusDays(6)) =
        ReviewCard(id, id, "meaning", repetitions = 2, lastQuality = 4, due = due, interval = interval, lastReviewed = last)

    private fun answer(id: String, right: Boolean, how: WordTest = WordTest.MEANING, produced: Boolean = false) =
        WordAnswer(TurnWord(MyWord(id, id, "meaning", setOf(id)), id, 0), right, how, produced, turn = 1, said = "x", expected = "y")

    @Test fun `the window is a tenth of the interval, one to three days`() {
        assertEquals(listOf(1L, 1L, 1L, 2L, 2L, 3L, 3L, 1L), listOf(1, 6, 14, 15, 24, 25, 90, 0).map(PlayReviews::window))
    }

    @Test fun `a right answer reviews a card due or nearly, not one reviewed today nor one far from due`() {
        fun counts(c: ReviewCard, counted: Set<String> = emptySet()) = PlayReviews.counts(c, today, counted)
        assertTrue(counts(card("a", today)))
        assertTrue(counts(card("a", today.minusDays(4)))) // overdue
        assertTrue(counts(card("a", today.plusDays(1)))) // within a day of its 6
        assertFalse(counts(card("a", today.plusDays(2))))
        assertTrue(counts(card("a", today.plusDays(3), interval = 30))) // within 3 of its 30
        assertFalse(counts(card("a", today.plusDays(4), interval = 30)))
        // reviewed today by the deck, counted in play today, or made on the phone today (no due date yet): no
        assertFalse(counts(card("a", today, last = today)))
        assertFalse(counts(card("a", today), setOf("a")))
        assertFalse(counts(card("a", null, last = null)))
    }

    @Test fun `what a dialog's answers come to`() {
        val cards = mapOf(
            "due" to card("due", today), "typed" to card("typed", today.minusDays(1)), "later" to card("later", today.plusDays(10)),
            "slip" to card("slip", today.plusDays(10)), "form" to card("form", today),
        )
        val out = DialogReviews.settle(
            listOf(
                answer("due", true), answer("typed", true, produced = true), answer("later", true),
                answer("slip", false), answer("form", false, WordTest.FORM), answer("nobody", true),
            ),
            cards::get, emptySet(), today,
        )
        assertEquals(listOf(Kind.REVIEW, Kind.REVIEW, Kind.MET, Kind.LOWERED, Kind.FORM), out.map { it.kind })
        // 4 chosen, 5 typed or said: the easiness never drops on a right answer
        assertEquals(listOf(4, 5, null, null, null), out.map { it.quality })
        // what changes a card's schedule today: the reviews and the slip, recorded in the village state
        assertEquals(listOf("due", "typed", "slip"), out.filter { it.changes }.map { it.answer.card.id })
    }

    @Test fun `a card's schedule changes once a day, the same dialog again or another, or «Vidim, vidim», counts nothing more`() {
        val cards = mapOf("a" to card("a", today), "b" to card("b", today))
        val first = DialogReviews.settle(listOf(answer("a", true)), cards::get, emptySet(), today)
        assertEquals(Kind.REVIEW, first.single().kind)
        val counted = PlayReviews.record(GameState(), first.filter { it.changes }.map { it.answer.card.id }, today).let { PlayReviews.counted(it, today) }
        val again = DialogReviews.settle(listOf(answer("a", true), answer("a", false), answer("b", false)), cards::get, counted, today)
        assertEquals(listOf(Kind.AGAIN, Kind.AGAIN, Kind.LOWERED), again.map { it.kind })
        // within one dialog: the first answer on a card decides, in a later turn too
        val twice = DialogReviews.settle(listOf(answer("b", true), answer("b", false)), cards::get, emptySet(), today)
        assertEquals(listOf(Kind.REVIEW, Kind.AGAIN), twice.map { it.kind })
        assertEquals(Kind.AGAIN, DialogReviews.settle(listOf(answer("b", false)), cards::get, emptySet(), today, answered = setOf("b")).single().kind)
        // the next day it counts again (the village state's record is the day's)
        assertTrue(PlayReviews.counted(PlayReviews.record(GameState(), listOf("a"), today), today.plusDays(1)).isEmpty())
    }

    @Test fun `a word not among the learner's cards counts nothing`() {
        assertTrue(DialogReviews.settle(listOf(answer("x", true)), { null }, emptySet(), today).isEmpty())
    }
}
