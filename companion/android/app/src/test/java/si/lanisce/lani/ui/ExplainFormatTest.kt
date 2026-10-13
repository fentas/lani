package si.lanisce.lani.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.ui.words.ExerciseTaps
import si.lanisce.lani.ui.words.KnownWords

/** An exercise's explanation in the feedback: a plain one in lines, its pairs a small table, markdown as written. */
class ExplainFormatTest {
    @Test fun `the dela explanation in lines, its subject and endings in bold, its pairs a table`() {
        assertEquals(
            "**dela**: no **-m** and no **-š**, so he or she.\n" +
                "| I work | delam |\n" +
                "| you work | delaš |",
            ExplainFormat.format("dela: no -m and no -š, so he or she. I work = delam, you work = delaš."),
        )
    }

    @Test fun `an explanation in markdown stays as written`() {
        val md = "**dela** → no **-m**, no **-š** → *on/ona*\n- delam · I work\n- delaš · you work\n- dela · he/she works"
        assertEquals(md, ExplainFormat.format(md))
        val table = "| delam | I work |\n| delaš | you work |"
        assertEquals(table, ExplainFormat.format(table))
    }

    @Test fun `a short plain one is unchanged`() {
        assertEquals("Slovene always needs the copula.", ExplainFormat.format("Slovene always needs the copula."))
        assertEquals("kava → kavo", ExplainFormat.format(" kava → kavo "))
        assertEquals("miza · table", ExplainFormat.format("miza · table"))
    }

    @Test fun `each sentence on its line, each subject in bold, an ending in brackets too`() {
        assertEquals(
            "**Masculine**: dva brata, dva sina (**-a**).\n**Feminine**: dve sestri (**-i**).",
            ExplainFormat.format("Masculine: dva brata, dva sina (-a). Feminine: dve sestri (-i)."),
        )
    }

    @Test fun `an abbreviation doesn't end a sentence, a hyphenated word isn't an ending`() {
        assertEquals(listOf("Some verbs, e.g. Grem, are irregular."), ExplainFormat.sentences("Some verbs, e.g. Grem, are irregular."))
        assertEquals("e-pošta is one word.", ExplainFormat.format("e-pošta is one word."))
    }

    @Test fun `one pair, or pairs with prose among them, aren't a table`() {
        assertNull(ExplainFormat.pairs("V šolo = to school (direction)."))
        assertNull(ExplainFormat.pairs("midva = we two, with the dual verb sva."))
        assertEquals(listOf("prosim" to "please", "štruca" to "a loaf"), ExplainFormat.pairs("prosim = please; štruca = a loaf"))
    }

    @Test fun `an explanation's Slovene words can be looked up after the answer, not its English nor its endings`() {
        val known = KnownWords.of(listOf("delam", "delaš", "dela"))
        val taps = ExerciseTaps(Exercise.Choice("Jaz ___.", listOf("delam", "delaš"), 0), answered = true, ExerciseTaps.reader(Lang.SL, Lang.EN, known))
        val line = "dela: no -m and no -š, so he or she."
        assertEquals(listOf("dela"), taps.explanation(line).map { it.text })
        // bold, as the formatter makes it (inlineMarkdown's spans): the subject; the endings never
        val shown = "dela: no -m and no -š, so he or she."
        assertEquals(listOf("dela"), taps.explanation(shown, emphasized = listOf(0..3, 9..10, 19..20)).map { it.text })
        assertEquals(listOf("delaš"), taps.explanation("you work").map { it.text } + taps.explanation("delaš").map { it.text })
        assertEquals(listOf("Pazi", "na", "končnico"), taps.explanation("Pazi na končnico. · Mind the ending.").map { it.text })
        // a form question's: the English term isn't Slovene for its č beside it
        assertEquals(listOf("česa"), taps.explanation("2. sklon · genitive: koga? česa? — ednina · singular").map { it.text })
        assertEquals(listOf("Grem", "v", "šolo"), taps.explanation("Grem v šolo.").map { it.text })
    }
}
