package si.lanisce.lani.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import si.lanisce.lani.data.AnswerDiff
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/**
 * A correction on screen (ui/AnswerDiffText.kt): the pieces as one text (struck, green and bold, quiet), what TalkBack
 * reads for it, and the count under a checked sheet in the four tables.
 */
class AnswerDiffTextTest {
    @After fun back() {
        L10n.pair = LangPair.DEFAULT
    }

    @Test fun `typed and wrong struck in the verdict's colour, right green and bold, the person quiet`() {
        val red = Color.Red
        val grey = Color.Gray
        val t = diffText(AnswerDiff.of("spive v šotoru", "Luka in Tine spita v šotoru.", optional = 3, lang = Lang.SL), red, grey)
        assertEquals("Luka in Tine spiveta v šotoru.", t.text)
        fun styleOf(s: String) = t.spanStyles.single { t.text.substring(it.start, it.end) == s }.item
        assertEquals(TextDecoration.LineThrough, styleOf("ve").textDecoration)
        assertEquals(red, styleOf("ve").color)
        assertEquals(FontWeight.Bold, styleOf("ta").fontWeight)
        assertEquals(grey, styleOf("Luka").color)
    }

    @Test fun `TalkBack reads the right answer, then what was typed, in words`() {
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        assertEquals(
            "Prav je · Right: Luka in Tine spita v šotoru. Tvoj odgovor · You wrote: spive v šotoru",
            correctionSaid("spive v šotoru ", "Luka in Tine spita v šotoru."),
        )
        assertEquals("Prav je · Right: je. Tvoj odgovor · You wrote: so", correctionSaid("so", "je"))
        assertEquals("Prav je · Right: je", correctionSaid(" ", "je"))
    }

    @Test fun `the count under a checked sheet, wrong and almost, in each table`() {
        fun count(lang: Lang, n: Int, almost: Int) = L10n.text(lang, "school.wrongOf", mapOf("n" to n, "of" to 8, "almost" to almost))
        assertEquals("3 od 8 narobe, 2 skoraj", count(Lang.SL, 3, 2))
        assertEquals("1 od 8 narobe", count(Lang.SL, 1, 0))
        assertEquals("nič narobe, 1 skoraj", count(Lang.SL, 0, 1))
        assertEquals("3 of 8 wrong, 2 almost", count(Lang.EN, 3, 2))
        assertEquals("3 von 8 falsch, 2 fast", count(Lang.DE, 3, 2))
        assertEquals("3 su 8 sbagliati, 2 quasi", count(Lang.IT, 3, 2))
    }
}
