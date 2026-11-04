package si.lanisce.lani.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.ItemMark
import si.lanisce.lani.data.Sheets

/**
 * What a sheet counts (companion/COURSE.md, "How they count"): every item; in a test not a speaking item the learner rated
 * themselves (no microphone or recognizer), which is practice, so a missing microphone neither passes nor fails anybody.
 */
class SheetCountTest {
    private fun answer(v: Verdict, rated: Boolean = false) = SheetAnswer(v, v, "x", "y", rated = rated)
    private val gaps = SheetAnswer(Verdict.ALMOST, Verdict.ALMOST, "", null, items = List(8) { ItemMark(Verdict.CORRECT, "a", "a") })

    @Test fun `a worksheet counts a rated speaking item as it was rated`() {
        assertEquals(listOf(Verdict.CORRECT, Verdict.WRONG) + List(8) { Verdict.CORRECT }, counted(listOf(answer(Verdict.CORRECT, rated = true), answer(Verdict.WRONG), gaps), test = false))
    }

    @Test fun `a test leaves a rated one out - its pass is the share of the rest`() {
        val items = counted(listOf(answer(Verdict.CORRECT, rated = true), answer(Verdict.CORRECT), gaps), test = true)
        assertEquals(9, items.size)
        assertTrue(Sheets.passed(items, 80))
        // rated "still practising" doesn't fail it either
        val failing = counted(listOf(answer(Verdict.WRONG, rated = true), answer(Verdict.WRONG), gaps), test = true)
        assertEquals(9, failing.size)
        assertTrue(Sheets.passed(failing, 80))
    }

    @Test fun `a speaking item the recognizer heard counts in the test, right, almost or wrong`() {
        val items = counted(listOf(answer(Verdict.WRONG), answer(Verdict.WRONG), gaps), test = true)
        assertEquals(10, items.size)
        assertFalse(Sheets.passed(items, 85))
    }
}
