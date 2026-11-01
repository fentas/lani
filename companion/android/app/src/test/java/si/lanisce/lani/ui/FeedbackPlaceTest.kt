package si.lanisce.lani.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where an answer's feedback card goes (ui/PlayerScreen, [FeedbackPlace]): beside the question on Teo's tablet on its side
 * (2560 × 1600 at 320 dpi: 1280 × 800 dp) and a phone turned, so the question stays whole; under it upright, as before.
 */
class FeedbackPlaceTest {
    @Test fun `beside the question on a wide screen on its side, under it upright`() {
        assertTrue("the tablet on its side", FeedbackPlace.beside(1280, 800))
        assertFalse("the tablet upright", FeedbackPlace.beside(800, 1280))
        assertTrue("a phone turned", FeedbackPlace.beside(851, 393))
        assertFalse("a phone upright", FeedbackPlace.beside(393, 851))
        assertFalse("a small phone turned: too narrow for two columns", FeedbackPlace.beside(640, 360))
        assertFalse("a square window", FeedbackPlace.beside(900, 900))
    }

    @Test fun `the card takes 40 percent beside, its buttons side by side, never more than 520 dp`() {
        assertEquals(512, FeedbackPlace.width(1280))
        assertEquals(FeedbackPlace.MIN_DP, FeedbackPlace.width(760))
        assertEquals(FeedbackPlace.MAX_DP, FeedbackPlace.width(2000))
        // the question keeps the larger part
        for (w in listOf(720, 851, 1024, 1280, 1600)) assertTrue("$w", w - FeedbackPlace.width(w) > w / 2)
    }

    @Test fun `a long card under the question folds the stage, keeps its height, the freed room the question's`() {
        val fold = CardFold()
        // a phone: 520 px of room under the stage band, the question 140 at least, the card 220 at least
        val max = fold.max(520, 140, 220)
        assertEquals(380, max)
        // a short card (a right answer and Next) leaves the stage as it is
        fold.measured(180, max, staged = true)
        assertFalse(fold.folded)
        // a long explanation needs all it may have: the stage folds
        fold.measured(380, max, staged = true)
        assertTrue(fold.folded)
        // the band folded, the room grows by what it freed (200 dp to 56): the card keeps 380, the question gets the rest
        assertEquals(380, fold.max(664, 140, 220))
        assertEquals(664 - 380, 664 - fold.max(664, 140, 220))
        // measured again at its height, it stays folded (no opening and folding as the card measures anew)
        fold.measured(380, 380, staged = true)
        assertTrue(fold.folded)
        // the next answer's card decides anew
        fold.reset()
        assertFalse(fold.folded)
        assertEquals(380, fold.max(520, 140, 220))
    }

    @Test fun `without a stage there is nothing to fold`() {
        val fold = CardFold()
        fold.measured(380, 380, staged = false)
        assertFalse(fold.folded)
        // the smallest room: the card's own least height
        assertEquals(220, fold.max(300, 140, 220))
    }
}
