package si.lanisce.lani.ui.scene

import org.junit.Assert.assertEquals
import org.junit.Test

class ChoiceDiffTest {
    private fun bold(vararg choices: String) = ChoiceDiff.marks(choices.toList()).map { ms -> ms.map { it.text } }
    private val none = emptyList<String>()

    @Test fun `each choice shows bold the word it differs in`() {
        assertEquals(
            listOf(listOf("jajc"), listOf("jajce"), listOf("jajca")),
            bold("Deset jajc, prosim.", "Deset jajce, prosim.", "Deset jajca, prosim."),
        )
    }

    @Test fun `where the choices differ in two places, every choice shows both, so the bold never tells the right one`() {
        assertEquals(
            listOf(listOf("Kje", "je"), listOf("Kje", "jo"), listOf("Kam", "je")),
            bold("Kje je? Ne vidim je.", "Kje je? Ne vidim jo.", "Kam je? Ne vidim je."),
        )
    }

    @Test fun `case and punctuation aside, a moved word counts`() {
        assertEquals(listOf(none, none), bold("Dober dan!", "dober dan."))
        assertEquals(listOf(listOf("Se"), listOf("se")), bold("Se vidimo jutri.", "Vidimo se jutri."))
    }

    @Test fun `choices that say different things get no bold`() {
        assertEquals(listOf(none, none), bold("Adijo!", "Lahko noč!"))
        assertEquals(listOf(none, none, none), bold("Ja, zelo.", "Dobro jutro!", "Na zdravje!"))
    }
}
