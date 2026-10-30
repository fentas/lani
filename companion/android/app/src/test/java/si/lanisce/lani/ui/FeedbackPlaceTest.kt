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
}
