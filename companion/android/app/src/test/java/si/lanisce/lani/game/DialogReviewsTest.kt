package si.lanisce.lani.game

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.game.DialogReviews.Kind

/**
 * What a dialog's answers on the learner's words do to their cards (companion/GAME.md, "Your words in the dialogs"): a
 * right one reviews a card due or nearly, a slip about the word lowers it gently, a wrong form leaves it, once a card a day.
 */
class DialogReviewsTest {
    private val today = LocalDate.of(2026, 10, 7)
    private fun card(id: String, due: LocalDate?, interval: Int = 6, last: LocalDate? = today.minusDays(6)) =
        ReviewCard(id, id, "meaning", repetitions = 2, lastQuality = 4, due = due, interval = interval, lastReviewed = last)

    private fun answer(id: String, right: Boolean, how: WordTest = WordTest.MEANING, produced: Boolean = false) =
        WordAnswer(TurnWord(MyWord(id, id, "meaning", setOf(id)), id, 0), right, how, produced, turn = 1, said = "x", expected = "y")

    @Test fun `the window is a tenth of the interval, one to three days`() {
        assertEquals(1, DialogReviews.window(1))
        assertEquals(1, DialogReviews.window(6))
        assertEquals(1, DialogReviews.window(14))
        assertEquals(2, DialogReviews.window(15))
        assertEquals(2, DialogReviews.window(24))
        assertEquals(3, DialogReviews.window(25))
        assertEquals(3, DialogReviews.window(90))
        assertEquals(1, DialogReviews.window(0))
    }

    @Test fun `a right answer reviews a card due or nearly, not one reviewed today nor one far from due`() {
        assertTrue(DialogReviews.counts(card("a", today), today))
        assertTrue(DialogReviews.counts(card("a", today.minusDays(4)), today)) // overdue
        assertTrue(DialogReviews.counts(card("a", today.plusDays(1)), today)) // within a day of its 6
        assertFalse(DialogReviews.counts(card("a", today.plusDays(2)), today))
        assertTrue(DialogReviews.counts(card("a", today.plusDays(3), interval = 30), today)) // within 3 of its 30
        assertFalse(DialogReviews.counts(card("a", today.plusDays(4), interval = 30), today))
        // reviewed today already, or added today (due tomorrow): no
        assertFalse(DialogReviews.counts(card("a", today, last = today), today))
        assertFalse(DialogReviews.counts(card("a", today.plusDays(1), interval = 1, last = today), today))
        // a card that doesn't say when it is due is due
        assertTrue(DialogReviews.counts(card("a", null, last = null), today))
    }

    @Test fun `what a dialog's answers come to`() {
        val cards = mapOf(
            "due" to card("due", today), "typed" to card("typed", today.minusDays(1)), "later" to card("later", today.plusDays(10)),
            "slip" to card("slip", today.plusDays(10)), "form" to card("form", today),
        )
        val (out, led) = DialogReviews.settle(
            listOf(
                answer("due", true), answer("typed", true, produced = true), answer("later", true),
                answer("slip", false), answer("form", false, WordTest.FORM), answer("nobody", true),
            ),
            cards::get, DialogReviews.Ledger(), today,
        )
        assertEquals(listOf(Kind.REVIEW, Kind.REVIEW, Kind.MET, Kind.LOWERED, Kind.FORM), out.map { it.kind })
        // 4 chosen, 5 typed or said: the easiness never drops on a right answer
        assertEquals(listOf(4, 5, null, null, null), out.map { it.quality })
        assertEquals(today.toString(), led.day)
        assertEquals(setOf("due", "typed", "later", "slip", "form"), led.cards.keys)
    }

    @Test fun `a card counts once a day, the same dialog again or another counts nothing more`() {
        val cards = mapOf("a" to card("a", today), "b" to card("b", today))
        val (first, led) = DialogReviews.settle(listOf(answer("a", true)), cards::get, DialogReviews.Ledger(), today)
        assertEquals(Kind.REVIEW, first.single().kind)
        val (again, led2) = DialogReviews.settle(listOf(answer("a", true), answer("a", false), answer("b", false)), cards::get, led, today)
        assertEquals(listOf(Kind.AGAIN, Kind.AGAIN, Kind.LOWERED), again.map { it.kind })
        // within one dialog too: the first answer on a card decides
        val (twice, _) = DialogReviews.settle(listOf(answer("b", true), answer("b", false)), cards::get, DialogReviews.Ledger(), today)
        assertEquals(listOf(Kind.REVIEW, Kind.AGAIN), twice.map { it.kind })
        // the next day it counts again
        val (tomorrow, led3) = DialogReviews.settle(listOf(answer("a", true)), mapOf("a" to card("a", today.plusDays(1)))::get, led2, today.plusDays(1))
        assertEquals(Kind.REVIEW, tomorrow.single().kind)
        assertEquals(setOf("a"), led3.cards.keys)
    }

    @Test fun `a word not among the learner's cards counts nothing`() {
        val (out, led) = DialogReviews.settle(listOf(answer("x", true)), { null }, DialogReviews.Ledger(), today)
        assertTrue(out.isEmpty())
        assertTrue(led.cards.isEmpty())
        assertNull(out.firstOrNull())
    }
}
