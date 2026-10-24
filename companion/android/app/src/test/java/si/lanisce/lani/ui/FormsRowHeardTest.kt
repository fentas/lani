package si.lanisce.lani.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.FormsCell
import si.lanisce.lani.data.FormsCellState
import si.lanisce.lani.data.FormsRow
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.ItemMark

/** A forms card's row gets its 🔊 during the card only where hearing it gives no answer away ([heard]). */
class FormsRowHeardTest {
    private fun cell(key: String, form: String, state: FormsCellState) = FormsCell(key, form, state)
    private val gapSem = cell("pres.1sg", "sem", FormsCellState.GAP)
    private val row = FormsRow("jaz / midva / mi", listOf(gapSem, cell("pres.1du", "sva", FormsCellState.PREVIEW), cell("pres.1pl", "smo", FormsCellState.TESTED)), "jaz sem, midva sva, mi smo")

    @Test fun `a row with a gap still to answer stays silent`() {
        assertFalse(heard(row, emptyMap()))
    }

    @Test fun `once its gap is answered, right or wrong, the row can be heard`() {
        assertTrue(heard(row, mapOf("pres.1sg" to ItemMark(Verdict.CORRECT, "sem", "sem"))))
        assertTrue(heard(row, mapOf("pres.1sg" to ItemMark(Verdict.WRONG, "si", "sem"))))
    }

    @Test fun `a row with no gap (known and read-ahead forms) can be heard from the start, an empty row never`() {
        val known = FormsRow("ti", listOf(cell("pres.2sg", "si", FormsCellState.TESTED), null, cell("pres.2pl", "ste", FormsCellState.PREVIEW)), "ti si, vi ste")
        assertTrue(heard(known, emptyMap()))
        assertFalse(heard(known.copy(said = ""), emptyMap()))
    }
}
