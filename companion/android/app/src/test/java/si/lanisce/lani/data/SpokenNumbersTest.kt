package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Test
import si.lanisce.lani.l10n.Lang

class SpokenNumbersTest {
    @Test fun `a digit stuck to a dual pronoun joins it as one word`() {
        assertEquals("midva sva doma", SpokenNumbers.inWords("mi2 sva doma", Lang.SL))
        assertEquals("medve sva doma", SpokenNumbers.inWords("me2 sva doma", Lang.SL))
        assertEquals("vidva sta", SpokenNumbers.inWords("vi2 sta", Lang.SL))
        assertEquals("onadva sta tukaj", SpokenNumbers.inWords("ona2 sta tukaj", Lang.SL))
        assertEquals("Pridem dvakrat.", SpokenNumbers.inWords("Pridem 2krat.", Lang.SL, "Pridem dvakrat."))
    }

    @Test fun `a free number takes the form the expected text has`() {
        assertEquals("Imam dve jabolki.", SpokenNumbers.inWords("Imam 2 jabolki.", Lang.SL, "Imam dve jabolki."))
        assertEquals("Imam dva brata.", SpokenNumbers.inWords("Imam 2 brata.", Lang.SL, "Imam dva brata."))
        assertEquals("Stara sem osem let.", SpokenNumbers.inWords("Stara sem 8 let.", Lang.SL))
        assertEquals("enaindvajset", SpokenNumbers.inWords("21", Lang.SL))
        assertEquals("Ho una sorella.", SpokenNumbers.inWords("Ho 1 sorella.", Lang.IT, "Ho una sorella."))
        assertEquals("einundzwanzig", SpokenNumbers.inWords("21", Lang.DE))
        assertEquals("ventotto", SpokenNumbers.inWords("28", Lang.IT))
        assertEquals("twenty-one", SpokenNumbers.inWords("21", Lang.EN))
    }

    @Test fun `text without digits, and long numbers, stay as they are`() {
        assertEquals("midva sva doma", SpokenNumbers.inWords("midva sva doma", Lang.SL))
        assertEquals("leta 2026", SpokenNumbers.inWords("leta 2026", Lang.SL))
    }

    @Test fun `a spoken answer with the digit is graded right`() {
        val heard = SpeechGrading.best(listOf("mi2 sva doma"), null, listOf("Midva sva doma."))
        assertEquals(Grading.Verdict.CORRECT, heard?.result?.verdict)
    }
}
