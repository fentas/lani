package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Test

class ChipLabelsTest {
    private val names = setOf("micka", "micko", "jan", "gorica", "gorici")

    @Test fun `a chip shows its bare word, so neither the full stop nor the capital gives its place away`() {
        val tokens = listOf("Jutri", "grem", "na", "posto.")
        val distractors = listOf("posti", "Grem")
        val labels = (tokens + distractors).map { ChipLabels.label(it, names) }
        assertEquals(listOf("jutri", "grem", "na", "posto", "posti", "grem"), labels)
    }

    @Test fun `names keep their capital, first or not, and so do words in capitals`() {
        assertEquals("Micka", ChipLabels.label("Micka", names))
        assertEquals("Gorici", ChipLabels.label("Gorici,", names))
        assertEquals("Jan", ChipLabels.label("Jan!", names))
        assertEquals("EU", ChipLabels.label("EU.", names))
        assertEquals("»pojdi", ChipLabels.label("»Pojdi«", names).let { "»$it" })
    }

    @Test fun `the sentence's closing mark stands after the answer`() {
        assertEquals(".", ChipLabels.ending(listOf("Grem", "na", "pošto.")))
        assertEquals("?", ChipLabels.ending(listOf("Kje", "je", "Micka?")))
        assertEquals("!", ChipLabels.ending(listOf("Pridi", "sem!")))
        assertEquals("", ChipLabels.ending(listOf("dober", "dan")))
    }
}
