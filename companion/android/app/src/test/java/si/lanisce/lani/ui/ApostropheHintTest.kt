package si.lanisce.lani.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.data.Grading
import si.lanisce.lani.data.Spelling
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/**
 * The apostrophe's hint (an answer almost right but for it) gives the examples of the language learned, its grading
 * table's (`loose_examples`), once after both halves: Italian's l'acqua, un'amica for Teo, English's can't, I'm.
 */
class ApostropheHintTest {
    private var was = L10n.pair

    @Before fun pair() {
        was = L10n.pair
    }

    @After fun back() {
        L10n.pair = was
    }

    @Test fun `the apostrophe's hint shows the examples of the language learned, once`() {
        L10n.pair = LangPair(Lang.IT, Lang.DE)
        assertEquals("Occhio all'apostrofo · Achte auf den Apostroph: l'acqua, un'amica", hintText(Grading.Hint.APOSTROPHE))
        L10n.pair = LangPair(Lang.EN, Lang.DE)
        assertEquals("Mind the apostrophe · Achte auf den Apostroph: can't, I'm", hintText(Grading.Hint.APOSTROPHE))
        L10n.pair = LangPair(Lang.EN, Lang.SL)
        assertEquals("Mind the apostrophe · Pazi na opuščaj: can't, I'm", hintText(Grading.Hint.APOSTROPHE))
        L10n.pair = LangPair(Lang.IT, Lang.EN)
        assertEquals("Occhio all'apostrofo · Mind the apostrophe: l'acqua, un'amica", hintText(Grading.Hint.APOSTROPHE))
    }

    @Test fun `a language without examples has the hint alone`() {
        assertNull(Spelling.of(Lang.SL).looseExamples)
        assertNull(Spelling.of(Lang.DE).looseExamples)
        assertEquals("l'acqua, un'amica", Spelling.of(Lang.IT).looseExamples)
        assertEquals("can't, I'm", Spelling.of(Lang.EN).looseExamples)
    }
}

/** What people say that writing doesn't want: its line in each language, in the pair. */
class NonstandardHintTest {
    private var was = L10n.pair

    @Before fun pair() {
        was = L10n.pair
    }

    @After fun back() {
        L10n.pair = was
    }

    @Test fun `the nonstandard hint has its line in every base`() {
        for ((base, words) in listOf(Lang.SL to "pisno", Lang.DE to "geschrieben", Lang.IT to "per iscritto")) {
            L10n.pair = LangPair(Lang.EN, base)
            val line = hintText(Grading.Hint.NONSTANDARD).orEmpty()
            assertEquals(line, true, line.contains("in writing") && line.contains(words))
        }
    }
}
