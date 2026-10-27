package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.l10n.Lang

/**
 * A correction at its item (data/AnswerDiff.kt): what was typed set against what is right, word by word and within a word
 * letter by letter, compared as the grading compares. Written here as ~~struck~~ (typed, not right), **bold** (right
 * instead) and (quiet) (may be left out).
 */
class AnswerDiffTest {
    private fun shown(typed: String, expected: String, optional: Int = 0, lang: Lang = Lang.SL): String =
        AnswerDiff.of(typed, expected, optional, lang).joinToString("") { p ->
            when (p.kind) {
                AnswerDiff.Kind.TYPED -> "~~${p.text}~~"
                AnswerDiff.Kind.RIGHT -> "**${p.text}**"
                AnswerDiff.Kind.GIVEN -> "(${p.text})"
                AnswerDiff.Kind.SAME, AnswerDiff.Kind.GAP -> p.text
            }
        }

    @Test fun `Jan's line - the wrong ending struck, the right one in, the rest as written`() {
        assertEquals("Spi~~ve~~**ta** v šotoru.", shown("spive v šotoru", "Spita v šotoru."))
        // a č, š, ž left out: a small difference of its own
        assertEquals("Spi~~ve~~**ta** v ~~s~~**š**otoru.", shown("spive v sotoru", "Spita v šotoru."))
        assertEquals("Bere~~t~~**v**a pismo.", shown("bereta pismo", "Bereva pismo."))
        assertEquals("Kuha~~s~~te kosilo.", shown("kuhaste kosilo", "Kuhate kosilo."))
    }

    @Test fun `the person in brackets left out shows quietly, the verb compared`() {
        assertEquals("(Luka) (in) (Tine) spi~~ve~~**ta** v šotoru.", shown("spive v šotoru", "Luka in Tine spita v šotoru.", optional = 3))
        assertEquals("(Babica) peč**e** kruh.", shown("peč kruh", "Babica peče kruh.", optional = 1))
        // typed, the person is compared as any word
        assertEquals("Babica peč**e** kruh.", shown("babica peč kruh", "Babica peče kruh.", optional = 1))
        // not to be left out: the missing words are what is right
        assertEquals("**Luka** **in** **Tine** spita v šotoru.", shown("spita v šotoru", "Luka in Tine spita v šotoru."))
    }

    @Test fun `Slovene's letters - c for č, s for š, z for ž, each where it is`() {
        assertEquals("Gleda~~s~~**š** ogenj.", shown("Gledas ogenj", "Gledaš ogenj."))
        assertEquals("~~z~~**ž**enska", shown("zenska", "ženska"))
        assertEquals("~~pjie~~ **Pijejo** ~~c~~**č**aj.", shown("pjie caj", "Pijejo čaj."))
        // the other way round: a č typed where there is none
        assertEquals("Pe~~č~~**c**a", shown("Peča", "Peca"))
    }

    @Test fun `a word left out goes in, a word too many is struck`() {
        assertEquals("Spita **v** šotoru.", shown("spita šotoru", "Spita v šotoru."))
        assertEquals("~~on~~ Spita v šotoru.", shown("on spita v šotoru", "Spita v šotoru."))
        assertEquals("Babica ~~že~~ peče kruh.", shown("Babica že peče kruh", "Babica peče kruh."))
    }

    @Test fun `case, punctuation and the kind of apostrophe don't count, as in the grading`() {
        val same = AnswerDiff.of("SPITA V ŠOTORU!!", "Spita v šotoru.", lang = Lang.SL)
        assertFalse(AnswerDiff.differs(same))
        assertEquals("Spita v šotoru.", shown("SPITA V ŠOTORU!!", "Spita v šotoru."))
        assertEquals("»Kje je babica?«", shown("kje je babica", "»Kje je babica?«"))
        assertFalse(AnswerDiff.differs(AnswerDiff.of("l’acqua", "l'acqua", lang = Lang.IT)))
        // a list's parts with or without its middle dots
        assertEquals("midva sva · vidva sta", shown("midva sva, vidva sta", "midva sva · vidva sta"))
    }

    @Test fun `another word whole - struck, then the right one`() {
        assertEquals("~~so~~ → **je**", shown("so", "je"))
        assertEquals("Pijejo ~~kavo~~ **čaj**.", shown("Pijejo kavo", "Pijejo čaj."))
        // mostly not what is right: no patchwork, what was typed, then what is right
        assertEquals("~~jaz ne vem~~ → **Luka in Tine spita v šotoru.**", shown("jaz ne vem", "Luka in Tine spita v šotoru."))
    }

    @Test fun `nothing typed - what is right, in full`() {
        assertEquals("**Bereva** **pismo**.", shown("", "Bereva pismo."))
        assertEquals("(Babica) **peče** **kruh**.", shown("  ", "Babica peče kruh.", optional = 1))
        assertTrue(AnswerDiff.differs(AnswerDiff.of("", "je", lang = Lang.SL)))
    }

    @Test fun `the other languages' letters - an umlaut typed out, an accent left out`() {
        assertEquals("M~~ae~~**ä**dchen", shown("Maedchen", "Mädchen", lang = Lang.DE))
        assertEquals("perch~~e~~**é**", shown("perche", "perché", lang = Lang.IT))
    }
}
