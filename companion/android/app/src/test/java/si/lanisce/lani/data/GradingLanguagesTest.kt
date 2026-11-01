package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun `ci and ne not elided before e are almost, as an article is - the A2 units ci-ne and imperfetto`() {
        assertGraded(Verdict.ALMOST, Hint.APOSTROPHE, it("ce ne è", "ce n'è"))
        assertGraded(Verdict.ALMOST, Hint.APOSTROPHE, it("Sì, ce ne è!", "Sì, ce n'è!"))
        assertGraded(Verdict.ALMOST, Hint.APOSTROPHE, it("ci è un gatto", "C'è un gatto."))
        assertGraded(Verdict.ALMOST, Hint.APOSTROPHE, it("Ci era una volta", "C'era una volta"))
        // ne before another word, and ci before ne, keep their vowel: right as they are, and ce n'è is not ce ne sono
        assertGraded(Verdict.CORRECT, Hint.NONE, it("Ne ho tre.", "Ne ho tre."))
        assertGraded(Verdict.CORRECT, Hint.NONE, it("ce ne sono sei", "Ce ne sono sei."))
        assertEquals(Verdict.WRONG, it("ce ne sono", "ce n'è").verdict)
        assertEquals(Verdict.WRONG, it("ci ne è", "ce n'è").verdict)
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

    // --- Italian A2: the participle's agreement, the future's and the participles' own stems -----------------------

    @Test fun `a participle that doesn't agree is wrong, with essere and with lo, la, li, le`() {
        assertGraded(Verdict.WRONG, Hint.ENDING, it("Giulia è andato al mare", "Giulia è andata al mare."))
        assertGraded(Verdict.WRONG, Hint.ENDING, it("siamo arrivate", "siamo arrivati"))
        assertGraded(Verdict.WRONG, Hint.ENDING, it("ci siamo divertito", "ci siamo divertiti"))
        assertGraded(Verdict.WRONG, Hint.ENDING, it("l'ho visto", "L'ho vista."))
        assertGraded(Verdict.WRONG, Hint.ENDING, it("li ho preso", "Li ho presi."))
        assertEquals(Verdict.WRONG, it("l'ho visti", "Li ho visti.").verdict)
        // without its accent the essere is almost, as in A1
        assertGraded(Verdict.ALMOST, Hint.ACCENT, it("Giulia e andata al mare", "Giulia è andata al mare."))
    }

    @Test fun `lo or la not elided before avere is almost only where the participle agrees`() {
        assertGraded(Verdict.ALMOST, Hint.APOSTROPHE, it("la ho vista", "L'ho vista."))
        assertGraded(Verdict.ALMOST, Hint.APOSTROPHE, it("lo ho visto", "L'ho visto."))
        assertGraded(Verdict.ALMOST, Hint.APOSTROPHE, it("la abbiamo presa", "L'abbiamo presa."))
        assertGraded(Verdict.ALMOST, Hint.APOSTROPHE, it("lo ho già visto", "L'ho già visto."))
        // the pronoun's gender against its participle: a mistake, not an apostrophe
        assertEquals(Verdict.WRONG, it("lo ho vista", "L'ho vista.").verdict)
        assertEquals(Verdict.WRONG, it("la ho visto", "L'ho visto.").verdict)
        assertEquals(Verdict.WRONG, it("la ho già visto", "L'ho già visto.").verdict)
        assertEquals(Verdict.WRONG, it("lo abbiamo vista", "L'abbiamo vista.").verdict)
        // the article as before
        assertGraded(Verdict.ALMOST, Hint.APOSTROPHE, it("la acqua", "l'acqua"))
        assertGraded(Verdict.ALMOST, Hint.APOSTROPHE, it("lo hotel", "l'hotel"))
    }

    @Test fun `the regular stem where a form has its own is wrong, never a slip`() {
        for ((answer, right) in listOf(
            "anderò" to "andrò", "averò" to "avrò", "vederai" to "vedrai", "poterò" to "potrò", "anderemo" to "andremo",
            "ferò" to "farò", "sterai" to "starai", "mangierò" to "mangerò", "comincierà" to "comincerà", "anderei" to "andrei",
            "venito" to "venuto", "è venita" to "è venuta", "bevito" to "bevuto",
        )) assertEquals("«$answer» for «$right»", Verdict.WRONG, it(answer, right).verdict)
        // the accent forgotten stays almost, a slip in the stem of another word stays forgiven
        assertGraded(Verdict.ALMOST, Hint.ACCENT, it("andro", "andrò"))
        assertGraded(Verdict.ALMOST, Hint.ACCENT, it("mangero", "mangerò"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, it("giocerò", "giocherò"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, it("bandira", "bandiera"))
        assertGraded(Verdict.CORRECT, Hint.NONE, it("Venite qui", "Venite qui!"))
    }

    // --- Italian B1: the combined pronouns and the gerund -------------------------------------------------------------

    @Test fun `the combined pronouns - me lo and glielo, their e and their gender kept`() {
        // mi, ti, ci, vi turn into me, te, ce, ve before lo, la, li, le, ne: the i kept is the mistake, never a slip
        assertGraded(Verdict.WRONG, Hint.ENDING, it("mi lo dai", "Me lo dai?"))
        assertGraded(Verdict.WRONG, Hint.ENDING, it("ti la porto", "Te la porto."))
        assertGraded(Verdict.WRONG, Hint.ENDING, it("gliela do", "Glielo do."))
        // gli + lo is one word with an e between: glilo and gli lo are wrong
        assertEquals(Verdict.WRONG, it("glilo do", "Glielo do.").verdict)
        assertEquals(Verdict.WRONG, it("gli lo do", "Glielo do.").verdict)
        assertEquals(Verdict.WRONG, it("portaglilo", "Portaglielo!").verdict)
        // on the end of a verb the same: dammilo, portamilo, darmilo are the mistake
        for ((answer, right) in listOf("dammilo" to "Dammelo!", "dimmila" to "Dimmela!", "portamilo" to "Portamelo!", "Puoi darmilo?" to "Puoi darmelo?", "prestatilo" to "Prestatelo!")) {
            assertEquals("«$answer» for «$right»", Verdict.WRONG, it(answer, right).verdict)
        }
        assertGraded(Verdict.CORRECT, Hint.NONE, it("glielo porto", "Glielo porto."))
        // a slip in another letter stays forgiven, and duemila, facile are no pronouns
        assertGraded(Verdict.ALMOST, Hint.TYPO, it("portagielo", "Portaglielo!"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, it("duemla", "duemila"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, it("fucile", "facile"))
    }

    @Test fun `glielo or gliela not elided before avere is almost only where the participle agrees`() {
        assertGraded(Verdict.ALMOST, Hint.APOSTROPHE, it("glielo ho dato", "Gliel'ho dato."))
        assertGraded(Verdict.ALMOST, Hint.APOSTROPHE, it("gliela ho data", "Gliel'ho data."))
        assertGraded(Verdict.ALMOST, Hint.APOSTROPHE, it("glielo ho già detto", "Gliel'ho già detto."))
        assertGraded(Verdict.ALMOST, Hint.APOSTROPHE, it("gliel ho dato", "Gliel'ho dato."))
        assertEquals(Verdict.WRONG, it("glielo ho data", "Gliel'ho data.").verdict)
        assertEquals(Verdict.WRONG, it("gliela ho dato", "Gliel'ho dato.").verdict)
        // me lo, te la: lo and la as before
        assertGraded(Verdict.ALMOST, Hint.APOSTROPHE, it("me lo ha detto", "Me l'ha detto."))
        assertGraded(Verdict.ALMOST, Hint.APOSTROPHE, it("te la ho portata", "Te l'ho portata."))
    }

    @Test fun `a gerund with the other verbs' ending is wrong, never a slip`() {
        for ((answer, right) in listOf(
            "leggando" to "leggendo", "dormando" to "dormendo", "bevando" to "bevendo", "parlendo" to "parlando",
            "camminendo" to "camminando", "guardendolo" to "guardandolo", "farendo" to "facendo", "direndo" to "dicendo",
            "berendo" to "bevendo", "avvicinendosi" to "avvicinandosi", "giochendo" to "giocando", "spingando" to "spingendo",
            // li for gli on its end: another pronoun, never a slip
            "dicendoli" to "dicendogli",
        )) assertEquals("«$answer» for «$right»", Verdict.WRONG, it(answer, right).verdict)
        assertGraded(Verdict.CORRECT, Hint.NONE, it("dicendoli", "dicendoli"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, it("gaurdandoli", "guardandoli"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, it("legendo", "leggendo"))
        assertGraded(Verdict.CORRECT, Hint.NONE, it("camminando", "Camminando"))
    }

    // --- Slovene ----------------------------------------------------------------------------------------------------

    private fun sl(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.SL)

    @Test fun `a prefixed verb one letter off another prefix is another verb, never a slip`() {
        // B2's predpone: pri- here, pre- across; na- done, za- noted down; od- away, ob- round (grading/sl.json wrong)
        for ((answer, right) in listOf(
            "prenesel" to "prinesel", "Tine je prenesel drva." to "Tine je prinesel drva.", "prešel" to "prišel",
            "prešla" to "prišla", "prinaša" to "prenaša", "prepisal" to "pripisal", "zapisal" to "napisal",
            "napisala" to "zapisala", "obšel" to "odšel", "odide" to "obide", "pride" to "prede",
        )) assertEquals("«$answer» for «$right»", Verdict.WRONG, sl(answer, right).verdict)
        // a slip inside the root stays forgiven, č, š, ž forgotten stay almost, and the right form is right
        assertGraded(Verdict.ALMOST, Hint.TYPO, sl("prinsel", "prinesel"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, sl("prepisval", "prepisoval"))
        assertGraded(Verdict.ALMOST, Hint.DIACRITICS, sl("prisel", "prišel"))
        assertGraded(Verdict.ALMOST, Hint.DIACRITICS, sl("odsel", "odšel"))
        assertGraded(Verdict.CORRECT, Hint.NONE, sl("Prinesel ga je poštar.", "Prinesel ga je poštar."))
        assertGraded(Verdict.CORRECT, Hint.NONE, sl("prenesel", "prenesel"))
    }

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
        assertTrue("Italian's known wrong forms", Spelling.of(Lang.IT).wrong.isNotEmpty())
        assertTrue("Slovene's: a prefix one letter off another", Spelling.of(Lang.SL).wrong.isNotEmpty())
        assertEquals(Hint.UMLAUT, Spelling.of(Lang.DE).foldHint)
        assertEquals(Hint.NONE, Spelling.of(Lang.EN).foldHint)
    }
}
