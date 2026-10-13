package si.lanisce.lani.ui.words

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.FormSlot
import si.lanisce.lani.data.LemmaForms
import si.lanisce.lani.data.ReviewCard

/** The words an exercise marks as new to the learner: not among their cards' words, nor the forms of their lemmas. */
class KnownWordsTest {
    private val miza = LemmaForms(
        "miza", "noun", gender = "f",
        slots = listOf(FormSlot("nom.sg", listOf("miza"), page = "p"), FormSlot("acc.sg", listOf("mizo"), page = "p"), FormSlot("loc.sg", listOf("mizi"), also = listOf("mízi"), page = "p")),
    )

    @Test fun `a card's words are known, a phrase's each, in any case`() {
        val k = KnownWords.of(listOf("Kako ste? / Kako si?", "hvala (lepa)", "dober dan"))
        for (w in listOf("kako", "Kako", "ste", "si", "hvala", "lepa", "Dober", "dan")) assertTrue(w, k.knows(w))
        assertFalse(k.knows("jutro"))
    }

    @Test fun `a lemma's forms the phone has are known, mizo is miza's`() {
        val k = KnownWords.ofDeck(listOf(ReviewCard("vocab_miza", "miza", "table"), ReviewCard("g", "Kje je …?", "where is", kind = "phrase"))) { lemma ->
            if (lemma == "miza") listOf(miza) else null
        }
        for (w in listOf("miza", "mizo", "mizi", "mízi", "kje", "je")) assertTrue(w, k.knows(w))
        assertFalse(k.knows("mize"))
    }

    @Test fun `the unknown words of a line are marked, names and known words not`() {
        val k = KnownWords.of(listOf("miza", "je", "na"))
        val line = "Knjiga je na mizi, pravi Luka. Danes Ana bere."
        val tokens = WordTokens.of(line)
        assertEquals(listOf("Knjiga", "mizi", "pravi", "Danes", "bere"), k.unknown(line, tokens).map { it.text })
        // with the forms of miza, mizi is known
        val forms = KnownWords.of(listOf("je", "na"), listOf(miza))
        assertFalse(forms.unknown(line, tokens).any { it.text == "mizi" })
    }

    @Test fun `only the words asked about are marked`() {
        val k = KnownWords.of(listOf("je"))
        val line = "Jaz ______ na klop."
        val tokens = WordTokens.of(line).filter { it.text != "klop" }
        assertEquals(listOf("Jaz", "na"), k.unknown(line, tokens).map { it.text })
    }

    @Test fun `known words tell a text that reads as either is Slovene`() {
        val k = KnownWords.of(listOf("miza", "stol"))
        val reads = ExerciseTaps.reader(si.lanisce.lani.l10n.Lang.SL, si.lanisce.lani.l10n.Lang.EN, k)
        assertEquals(Reads.TARGET, reads("miza"))
        assertEquals(Reads.UNSURE, reads("table"))
        assertEquals(Reads.BASE, reads("good day"))
        assertEquals(Reads.TARGET, reads("hiša"))
    }
}
