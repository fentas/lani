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
 * English's contractions, British and American spellings and the plural's and numbers' known mistakes, with answers a learner
 * really types.
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

    @Test fun `a combined pronoun's mistake only for a combined pronoun - a noun's slip stays one`() {
        // the patterns of glilo and dammilo, portamilo are paired with the right word's glie- and -melo forms
        assertEquals(Verdict.WRONG, it("dammilo", "dammelo").verdict)
        assertEquals(Verdict.WRONG, it("portamilo", "portamelo").verdict)
        assertEquals(Verdict.WRONG, it("glilo", "glielo").verdict)
        assertEquals(Verdict.WRONG, it("portaglilo", "portaglielo").verdict)
        assertGraded(Verdict.ALMOST, Hint.TYPO, it("cavilo", "cavolo"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, it("articilo", "articolo"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, it("gagline", "galline"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, it("pogline", "polline"))
        // matila for matita: its t is in the plain two-letter ending, wrong by that as before B1
        assertGraded(Verdict.WRONG, Hint.ENDING, it("matila", "matita"))
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

    @Test fun `one preposition with the article for another is wrong, never a slip - the B1 passive's da`() {
        for ((answer, right) in listOf(
            "Il miele è fatto delle api." to "Il miele è fatto dalle api.", "è scritta della maestra" to "è scritta dalla maestra",
            "dagli amici" to "degli amici", "la casa dalla nonna" to "la casa della nonna", "dello zio" to "dallo zio",
            "è fatta alla nonna" to "è fatta dalla nonna", "nella nonna" to "della nonna", "agli alberi" to "dagli alberi",
        )) assertEquals("«$answer» for «$right»", Verdict.WRONG, it(answer, right).verdict)
        // a slip in the word itself, or in another word beside it, stays forgiven
        assertGraded(Verdict.ALMOST, Hint.TYPO, it("dlla maestra", "dalla maestra"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, it("Gioca con la plla", "Gioca con la palla."))
        // one of them typed for another word is a slip as before: della for bella, alla for palla
        assertGraded(Verdict.ALMOST, Hint.TYPO, it("una della casa", "una bella casa"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, it("alla", "palla"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, it("è scritta dalla mestra", "è scritta dalla maestra"))
        assertGraded(Verdict.CORRECT, Hint.NONE, it("Il miele è fatto dalle api", "Il miele è fatto dalle api."))
    }

    // --- Italian B1: the conditional, the subjunctive, the remote past --------------------------------------------

    @Test fun `another tense or mood one letter off is wrong, never a slip`() {
        for ((answer, right) in listOf(
            // the future for the conditional and the other way (Al posto tuo sarei …, Domani sarai …)
            "sarai" to "sarei", "avrai" to "avrei", "farai" to "farei", "darai" to "darei", "sarei" to "sarai",
            // the indicative for the subjunctive (Penso che siano stanchi, Spero che stia bene)
            "siamo" to "siano", "sta" to "stia",
            // the remote past for the subjunctive and the other way (Se fossi un uccello …), andò for the future
            "fosti" to "fossi", "fossi" to "fosti", "andò" to "andrò",
            // already wrong by their ending: -i for -a, the future's -emo for the conditional's -emmo, -ai for -ei
            "vadi" to "vada", "saremo" to "saremmo", "mangerai" to "mangerei", "capisce" to "capisca",
        )) assertEquals("«$answer» for «$right»", Verdict.WRONG, it(answer, right).verdict)
        // what stays as it was: an accent forgotten, siano for siamo (m and n side by side), a slip in another stem
        assertGraded(Verdict.ALMOST, Hint.ACCENT, it("ando", "andò"))
        assertGraded(Verdict.ALMOST, Hint.ACCENT, it("parlo", "parlò"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, it("siano", "siamo"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, it("sapia", "sappia"))
        assertGraded(Verdict.CORRECT, Hint.NONE, it("Penso che sia vero", "Penso che sia vero."))
    }

    @Test fun `one ending typed for another on the same stem is wrong - the subjunctive's and the conditional's`() {
        // the table's ending_swaps: the right word's ending, the typed one's, the stem the same
        assertGraded(Verdict.WRONG, Hint.ENDING, it("dormono", "dormano"))
        assertGraded(Verdict.WRONG, Hint.ENDING, it("dormano", "dormono"))
        assertGraded(Verdict.WRONG, Hint.ENDING, it("che loro vengono", "che loro vengano"))
        assertGraded(Verdict.WRONG, Hint.ENDING, it("parlano", "parlino"))
        assertGraded(Verdict.WRONG, Hint.ENDING, it("parlate", "parliate"))
        assertGraded(Verdict.WRONG, Hint.ENDING, it("sarete", "sareste"))
        assertEquals(Verdict.WRONG, it("siete", "siate").verdict)
        // a noun's slip stays one: no swap, the plain ending as before
        assertGraded(Verdict.ALMOST, Hint.TYPO, it("bambeno", "bambino"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, it("giardeno", "giardino"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, it("vicono", "vicino"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, it("venhano", "vengano"))
        // as before B1: bambimo's n is in the plain two-letter ending of a word of seven letters
        assertGraded(Verdict.WRONG, Hint.ENDING, it("bambimo", "bambino"))
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

    @Test fun `an english long word's ending is its last letter, so a slip just before it is a slip`() {
        // English's ending_letters 1: a slip in the last two letters of a word over five letters is forgiven
        assertGraded(Verdict.ALMOST, Hint.TYPO, en("usualy", "usually"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, en("walkd", "walked"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, en("thirten", "thirteen"))
        // the ending itself stays the ending: -s, -ed, -ing, a plural
        assertGraded(Verdict.WRONG, Hint.ENDING, en("dogs", "dog"))
        assertGraded(Verdict.WRONG, Hint.ENDING, en("dog", "dogs"))
        assertGraded(Verdict.WRONG, Hint.ENDING, en("walk", "walks"))
        assertGraded(Verdict.WRONG, Hint.ENDING, en("walks", "walked"))
        assertGraded(Verdict.WRONG, Hint.ENDING, en("walked", "walks"))
        assertGraded(Verdict.WRONG, Hint.ENDING, en("listen", "listens"))
        assertGraded(Verdict.WRONG, Hint.ENDING, en("horse", "horses"))
        assertGraded(Verdict.WRONG, Hint.ENDING, en("sittin", "sitting"))
        // the paired known mistakes stay wrong, potatos and tomatos too (one letter before the s)
        for ((typed, right) in listOf("potatos" to "potatoes", "tomatos" to "tomatoes", "siting" to "sitting", "makeing" to "making", "nineth" to "ninth", "where" to "were", "fourty" to "forty", "familys" to "families")) {
            assertEquals("$typed for $right", Verdict.WRONG, en(typed, right).verdict)
        }
        // the other languages keep two letters: Slovene's -ega, Italian's -ano
        assertEquals(1, Spelling.of(Lang.EN).endingLetters)
        assertEquals(2, Spelling.of(Lang.SL).endingLetters)
        assertEquals(2, Spelling.of(Lang.IT).endingLetters)
        assertEquals(2, Spelling.of(Lang.DE).endingLetters)
    }

    @Test fun `an english contraction without its apostrophe is almost, a word of its own stays wrong`() {
        // a phone keyboard hides the apostrophe: almost, with the hint, never right
        for ((typed, right) in listOf("cant" to "can't", "isnt" to "isn't", "I dont know" to "I don't know.", "Im here" to "I'm here.", "youre late" to "you're late",
            "theyve got a dog" to "They've got a dog.", "wont" to "won't", "Jack wasnt there" to "Jack wasn't there.", "hes got a cat" to "He's got a cat.", "wheres Meg" to "Where's Meg?")) {
            val r = en(typed, right)
            assertEquals("$typed for $right", Verdict.ALMOST to Hint.APOSTROPHE, r.verdict to r.hint)
        }
        // a word of its own is another word: were and we're, its and it's, each wrong for the other
        assertEquals(Verdict.WRONG, en("were", "we're").verdict)
        assertEquals(Verdict.WRONG, en("We're at home.", "We were at home.").verdict)
        assertEquals(Verdict.WRONG, en("Were at home", "We're at home.").verdict)
        assertEquals(Verdict.WRONG, en("its", "it's").verdict)
        assertEquals(Verdict.WRONG, en("Its raining", "It's raining.").verdict)
        assertEquals(Verdict.WRONG, en("lets go", "Let's go.").verdict)
        // with the apostrophe, or in full, it stays right
        assertGraded(Verdict.CORRECT, Hint.NONE, en("can't", "can't"))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("cannot", "can't"))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("can not", "can't"))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("The dog wags its tail", "The dog wags its tail."))
        assertEquals(Hint.APOSTROPHE, Spelling.of(Lang.EN).looseHint)
    }

    @Test fun `english has no letters to fold`() {
        assertEquals(Verdict.WRONG, en("I do know", "I don't know.").verdict)
        assertEquals("café", Grading.fold("Café", Lang.EN))
    }

    @Test fun `english have got's 's is has, a name's 's before a place is is, and a possessive stays one`() {
        assertGraded(Verdict.CORRECT, Hint.NONE, en("He's got a dog", "He has got a dog."))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("she has got two sheep", "She's got two sheep."))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("Jack's got a dog called Meg", "Jack has got a dog called Meg."))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("It's got four legs", "It has got four legs."))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("I've got a cold", "I have got a cold."))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("They haven't got a car", "They have not got a car."))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("Mrs Birkett hasn't got time", "Mrs Birkett has not got time."))
        // has and is apart: she's got is never she is got
        assertEquals(Verdict.WRONG, en("She is got a cat", "She's got a cat.").verdict)
        assertGraded(Verdict.CORRECT, Hint.NONE, en("She's a teacher", "She is a teacher."))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("Jack's at home", "Jack is at home."))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("Mrs Hartley's not here", "Mrs Hartley is not here."))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("Edna's my aunt", "Edna is my aunt."))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("How's Granny Maggie", "How is Granny Maggie?"))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("I'd like a cup of tea", "I would like a cup of tea."))
        // a possessive before its noun stays a possessive: Jack's dog is never Jack is dog
        assertEquals(Verdict.WRONG, en("Jack is dog", "Jack's dog.").verdict)
        assertGraded(Verdict.CORRECT, Hint.NONE, en("Jack's dog is Meg", "Jack's dog is Meg."))
    }

    @Test fun `english numbers with or without the hyphen, and the american hundred without its and`() {
        assertGraded(Verdict.CORRECT, Hint.NONE, en("twenty one", "twenty-one"))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("Ninety-nine sheep", "ninety nine sheep"))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("a hundred five", "a hundred and five"))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("one hundred twenty-three", "one hundred and twenty-three"))
        // the words themselves stay apart
        assertEquals(Verdict.WRONG, en("thirteen", "thirty").verdict)
        assertEquals(Verdict.WRONG, en("fifty", "fifteen").verdict)
        assertEquals(Verdict.WRONG, en("twenty", "twenty-one").verdict)
    }

    @Test fun `the american spelling is right beside the british`() {
        for ((us, uk) in listOf(
            "color" to "colour", "colors" to "colours", "colorful" to "colourful", "favorite" to "favourite",
            "my neighbor" to "my neighbour", "the neighborhood" to "the neighbourhood", "flavor" to "flavour",
            "harbor" to "harbour", "behavior" to "behaviour", "the center" to "the centre", "a hundred meters" to "a hundred metres",
            "a liter of milk" to "a litre of milk", "the theater" to "the theatre", "gray" to "grey", "my mom" to "my mum",
            "traveling" to "travelling", "a traveler" to "a traveller", "pajamas" to "pyjamas", "jewelry" to "jewellery",
            "cozy" to "cosy", "the plow" to "the plough", "math" to "maths", "a dialog" to "a dialogue",
            "a TV program" to "a TV programme", "I realize" to "I realise", "organized" to "organised", "practicing" to "practising",
        )) {
            assertGraded(Verdict.CORRECT, Hint.NONE, en(us, uk))
            assertGraded(Verdict.CORRECT, Hint.NONE, en(uk, uk))
        }
        // what only looks like one stays as it is: for, door, laboratory, humorous, error, major, mommy's own word
        assertEquals("for the door", Grading.canonical("for the door", Lang.EN))
        assertEquals("the laboratory is humorous", Grading.canonical("the laboratory is humorous", Lang.EN))
        assertEquals("an error a major a litter", Grading.canonical("an error a major a litter", Lang.EN))
        assertEquals("a moment", Grading.canonical("a moment", Lang.EN))
    }

    @Test fun `an english plural's known mistakes are wrong, only for a noun of their group`() {
        // -es after x, z, ch, sh, ss; -ves; women; else forgiven as a slip in the stem
        assertEquals(Verdict.WRONG, en("boxs", "boxes").verdict)
        assertEquals(Verdict.WRONG, en("two churchs", "two churches").verdict)
        assertEquals(Verdict.WRONG, en("the glasss", "the glasses").verdict)
        assertEquals(Verdict.WRONG, en("buss", "buses").verdict)
        assertEquals(Verdict.WRONG, en("knifes", "knives").verdict)
        assertEquals(Verdict.WRONG, en("wifes", "wives").verdict)
        assertEquals(Verdict.WRONG, en("two woman", "two women").verdict)
        assertEquals(Verdict.WRONG, en("one women", "one woman").verdict)
        // the irregular ones were wrong already: an ending, or too far
        for ((typed, right) in listOf("sheeps" to "sheep", "mans" to "men", "childs" to "children", "childrens" to "children", "foots" to "feet", "mouses" to "mice", "tooths" to "teeth", "potatos" to "potatoes", "familys" to "families", "leafs" to "leaves", "calfs" to "calves", "dog" to "dogs")) {
            assertEquals("$typed for $right", Verdict.WRONG, en(typed, right).verdict)
        }
        // a slip in the stem stays a slip, on these nouns and on the others: boxes, horses, houses, gloves, the shepherd
        assertGraded(Verdict.ALMOST, Hint.TYPO, en("bxoes", "boxes"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, en("hroses", "horses"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, en("hosues", "houses"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, en("glofes", "gloves"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, en("the shepehrd", "the shepherd"))
    }

    @Test fun `an english number's spelling mistakes are wrong, its slips forgiven`() {
        assertEquals(Verdict.WRONG, en("fourty", "forty").verdict)
        assertEquals(Verdict.WRONG, en("forteen", "fourteen").verdict)
        assertEquals(Verdict.WRONG, en("ninty", "ninety").verdict)
        assertEquals(Verdict.WRONG, en("ninteen", "nineteen").verdict)
        assertEquals(Verdict.WRONG, en("for sheep", "four sheep").verdict)
        assertGraded(Verdict.ALMOST, Hint.TYPO, en("sevan", "seven"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, en("thrity", "thirty"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, en("sevneteen", "seventeen"))
        // for stays for where four isn't the answer
        assertGraded(Verdict.CORRECT, Hint.NONE, en("a present for you", "a present for you"))
    }

    @Test fun `this and that's plurals and the present simple's spelling mistakes are wrong, their slips forgiven`() {
        // these and those one letter apart: near or far, never a slip
        assertEquals(Verdict.WRONG, en("those sheep", "these sheep").verdict)
        assertEquals(Verdict.WRONG, en("These are my boots", "Those are my boots.").verdict)
        assertEquals(Verdict.WRONG, en("this", "these").verdict)
        assertEquals(Verdict.WRONG, en("that", "this").verdict)
        assertGraded(Verdict.ALMOST, Hint.TYPO, en("thsee", "these"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, en("tohse", "those"))
        // -es after o, -ies after a consonant: the rule the present simple teaches (and a plural's babies)
        for ((typed, right) in listOf("she gos" to "she goes", "Jack dos" to "Jack does", "it flyes" to "it flies", "he carryes" to "he carries", "Poppy studyes" to "Poppy studies", "two babyes" to "two babies", "she tryes" to "she tries")) {
            assertEquals("$typed for $right", Verdict.WRONG, en(typed, right).verdict)
        }
        // the third person's -s and -es were wrong already: an ending, or the -es rule after ch, sh, x
        for ((typed, right) in listOf("Jack walk" to "Jack walks", "I walks" to "I walk", "she watchs" to "she watches", "he fixs" to "he fixes", "it flys" to "it flies", "she haves" to "she has", "he playes" to "he plays")) {
            assertEquals("$typed for $right", Verdict.WRONG, en(typed, right).verdict)
        }
        // a slip in the stem stays a slip
        assertGraded(Verdict.ALMOST, Hint.TYPO, en("Jack wlaks", "Jack walks"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, en("she wtaches", "she watches"))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("He doesn't know", "He does not know."))
    }

    @Test fun `an english -ing form's spelling mistakes, an ordinal's and were for where are wrong, their slips forgiven`() {
        // the consonant not doubled, the silent e kept, a consonant doubled that isn't: the rule, never a slip
        for ((typed, right) in listOf("siting" to "sitting", "runing" to "running", "swiming" to "swimming", "geting" to "getting", "shoping" to "shopping",
            "makeing" to "making", "comeing" to "coming", "writeing" to "writing", "takeing" to "taking", "haveing" to "having", "writting" to "writing", "comming" to "coming")) {
            assertEquals("$typed for $right", Verdict.WRONG, en("Jack is $typed", "Jack is $right").verdict)
        }
        // the ordinals' and were, where: each only for its own word
        for ((typed, right) in listOf("nineth" to "ninth", "eigth" to "eighth", "twelth" to "twelfth", "fith" to "fifth", "fivth" to "fifth", "twentyth" to "twentieth", "thirtyth" to "thirtieth", "fourtieth" to "fortieth", "where" to "were", "were" to "where")) {
            assertEquals("$typed for $right", Verdict.WRONG, en(typed, right).verdict)
        }
        // an ordinal from 21 to 99 with or without its hyphen, as a number
        assertGraded(Verdict.CORRECT, Hint.NONE, en("on the twenty first of May", "on the twenty-first of May"))
        assertGraded(Verdict.CORRECT, Hint.NONE, en("the thirty-first", "the thirty first"))
        // a slip elsewhere in the word stays one, on these verbs and on the others
        assertGraded(Verdict.ALMOST, Hint.TYPO, en("sittting", "sitting"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, en("siwmming", "swimming"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, en("wlaking", "walking"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, en("eihgth", "eighth"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, en("Wendesday", "Wednesday"))
        // a right word with the same letters is right (seeing); hoping for hopping is the rule's mistake too
        assertGraded(Verdict.CORRECT, Hint.NONE, en("We are seeing Jack", "We are seeing Jack"))
        assertGraded(Verdict.WRONG, Hint.NONE, en("hoping", "hopping"))
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
