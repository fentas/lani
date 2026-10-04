package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Test
import si.lanisce.lani.data.Grading.Hint
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/**
 * Grading per target language (resources/grading/<code>.json): Italian's accents and apostrophes, German's umlauts,
 * English's contractions, with answers a learner really types.
 */
class GradingLanguagesTest {
    private fun it(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.IT)
    private fun de(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.DE)
    private fun en(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.EN)

    private fun assertGraded(verdict: Verdict, hint: Hint, r: Grading.Result) {
        assertEquals("verdict of «${r.expected}»", verdict, r.verdict)
        assertEquals("hint of «${r.expected}»", hint, r.hint)
    }

    // --- Italian: accents ---------------------------------------------------------------------------------------

    @Test fun `italian exact answers are correct, whatever the case and punctuation`() {
        assertGraded(Verdict.CORRECT, Hint.NONE, it("è bello", "È bello!"))
        assertGraded(Verdict.CORRECT, Hint.NONE, it("  perché no ", "Perché no?"))
        assertGraded(Verdict.CORRECT, Hint.NONE, it("Vado in città.", "Vado in città."))
    }

    @Test fun `e for è is almost, with the accent hint`() {
        val r = it("e bello", "È bello!")
        assertGraded(Verdict.ALMOST, Hint.ACCENT, r)
        assertEquals("È", r.expected.substring(r.marks.single()))
        assertGraded(Verdict.ALMOST, Hint.ACCENT, it("Lui e italiano", "Lui è italiano."))
    }

    @Test fun `perché without its accent, with the other accent, or with an apostrophe is almost`() {
        assertGraded(Verdict.ALMOST, Hint.ACCENT, it("perche", "perché"))
        assertGraded(Verdict.ALMOST, Hint.ACCENT, it("perchè", "perché"))
        assertGraded(Verdict.ALMOST, Hint.ACCENT, it("perche'", "perché"))
        assertGraded(Verdict.ALMOST, Hint.ACCENT, it("Perche' no?", "Perché no?"))
    }

    @Test fun `città typed citta or citta' is almost`() {
        assertGraded(Verdict.ALMOST, Hint.ACCENT, it("vado in citta", "Vado in città."))
        assertGraded(Verdict.ALMOST, Hint.ACCENT, it("vado in citta'", "Vado in città."))
        assertGraded(Verdict.ALMOST, Hint.ACCENT, it("Si, grazie", "Sì, grazie."))
    }

    @Test fun `un po' keeps its apostrophe and is not joined to the next word`() {
        assertGraded(Verdict.CORRECT, Hint.NONE, it("un po' di pane", "Un po' di pane."))
        assertGraded(Verdict.CORRECT, Hint.NONE, it("un po’ di pane", "Un po' di pane."))
    }

    // --- Italian: apostrophes and articles ------------------------------------------------------------------------

    @Test fun `the apostrophe's kind and a space after it don't matter`() {
        assertGraded(Verdict.CORRECT, Hint.NONE, it("l’amico", "l'amico"))
        assertGraded(Verdict.CORRECT, Hint.NONE, it("l' amico", "l'amico"))
        assertGraded(Verdict.CORRECT, Hint.NONE, it("Dov'è l'acqua?", "Dov'è l'acqua?"))
    }

    @Test fun `a missing apostrophe or an article not elided is almost, with the apostrophe hint`() {
        assertGraded(Verdict.ALMOST, Hint.APOSTROPHE, it("l amico", "l'amico"))
        assertGraded(Verdict.ALMOST, Hint.APOSTROPHE, it("lo amico", "l'amico"))
        assertGraded(Verdict.ALMOST, Hint.APOSTROPHE, it("la acqua", "l'acqua"))
        assertGraded(Verdict.ALMOST, Hint.APOSTROPHE, it("un amica", "un'amica"))
        assertGraded(Verdict.ALMOST, Hint.APOSTROPHE, it("una amica", "un'amica"))
        assertGraded(Verdict.ALMOST, Hint.APOSTROPHE, it("un bicchiere della acqua", "un bicchiere dell'acqua"))
        // and the other way round: un amico takes no apostrophe
        assertGraded(Verdict.ALMOST, Hint.APOSTROPHE, it("un'amico", "un amico"))
    }

    @Test fun `accents and apostrophes both off is still almost`() =
        assertEquals(Verdict.ALMOST, it("la acqua e fredda", "L'acqua è fredda.").verdict)

    @Test fun `a wrong italian word stays wrong`() {
        assertGraded(Verdict.WRONG, Hint.NONE, it("il cane", "il gatto"))
        assertEquals(Verdict.WRONG, it("l'amico", "l'amica").verdict)
        assertEquals(Hint.ENDING, it("l'amico", "l'amica").hint)
        assertEquals(Verdict.WRONG, it("le acque", "l'acqua").verdict)
    }

    @Test fun `italian typos in the stem are forgiven`() =
        assertGraded(Verdict.ALMOST, Hint.TYPO, it("Bongiorno", "Buongiorno!"))

    // --- German ---------------------------------------------------------------------------------------------------

    @Test fun `german ae oe ue ss for ä ö ü ß are almost, with the umlaut hint`() {
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("das Maedchen", "das Mädchen"))
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("die Strasse", "die Straße"))
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("schoen", "schön"))
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("Guten Morgen, wie geht's? Muede.", "Guten Morgen, wie geht's? Müde."))
    }

    @Test fun `german umlaut left out is a typo in the stem, and nouns may be lower case`() {
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("das madchen", "das Mädchen"))
        assertGraded(Verdict.CORRECT, Hint.NONE, de("das mädchen", "das Mädchen"))
    }

    @Test fun `a german ending stays grammar`() = assertEquals(Hint.ENDING, de("den Hund", "dem Hund").hint)

    @Test fun `a long word forgives two slips in its stem, a shorter one only one`() {
        assertEquals(2, Grading.typosAllowed("bürgermeister"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("der Burgermaister", "der Bürgermeister"))
        assertGraded(Verdict.WRONG, Hint.NONE, de("das Gaschank", "das Geschenk"))
    }

    // --- English ----------------------------------------------------------------------------------------------------

    @Test fun `english contractions are the same answer`() {
        assertGraded(Verdict.CORRECT, Hint.NONE, en("I don't know", "I do not know."))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("I do not know", "I don't know."))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("It's cold", "It is cold."))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("I’m Jan", "I am Jan."))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("We can't", "We cannot."))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("they won't come", "They will not come."))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("let's go", "Let us go."))
    }

    @Test fun `english has no letters to fold`() {
        assertEquals(Verdict.WRONG, en("I do know", "I don't know.").verdict)
        assertEquals("café", Grading.fold("Café", Lang.EN))
    }

    // --- the pair's target is the default -----------------------------------------------------------------------------

    @Test fun `without a language, the pair's target decides`() {
        val before = L10n.pair
        try {
            L10n.pair = LangPair(Lang.IT, Lang.SL)
            assertGraded(Verdict.ALMOST, Hint.ACCENT, Grading.check("perche", listOf("perché")))
            L10n.pair = LangPair.DEFAULT
            assertGraded(Verdict.ALMOST, Hint.DIACRITICS, Grading.check("dober vecer", listOf("Dober večer")))
            // Slovene's fold doesn't know Italian's accents: there, perche is a wrong ending, not an accent
            assertGraded(Verdict.WRONG, Hint.ENDING, Grading.check("perche", listOf("perché")))
        } finally {
            L10n.pair = before
        }
    }

    @Test fun `every language's table reads`() {
        for (l in Lang.entries) Spelling.of(l)
        assertEquals(Hint.DIACRITICS, Spelling.of(Lang.SL).foldHint)
        assertEquals(Hint.ACCENT, Spelling.of(Lang.IT).foldHint)
        assertEquals(Hint.APOSTROPHE, Spelling.of(Lang.IT).looseHint)
        assertEquals(Hint.UMLAUT, Spelling.of(Lang.DE).foldHint)
        assertEquals(Hint.NONE, Spelling.of(Lang.EN).foldHint)
    }
}
