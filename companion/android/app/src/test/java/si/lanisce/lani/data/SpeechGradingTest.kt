package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Hint
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.Res
import si.lanisce.lani.l10n.Lang

class SpeechGradingTest {
    private val accept = listOf("Dober dan, jaz sem Jan.", "Dober dan, sem Jan.")

    @Test fun `a correct alternative beats a more likely wrong one`() {
        val h = SpeechGrading.best(listOf("dober dan jaz sem jana", "dober dan jaz sem jan"), floatArrayOf(0.9f, 0.6f), accept)!!
        assertEquals(Verdict.CORRECT, h.result.verdict)
        assertEquals("dober dan jaz sem jan", h.text)
        assertEquals(0.6f, h.confidence!!, 0.001f)
    }

    @Test fun `accept alternatives count too`() =
        assertEquals(Verdict.CORRECT, SpeechGrading.best(listOf("Dober dan sem Jan"), null, accept)!!.result.verdict)

    @Test fun `ending rule applies to what was heard`() {
        val h = SpeechGrading.best(listOf("moj partnerka je iz gorice"), null, listOf("Moja partnerka je iz Gorice."))!!
        assertEquals(Verdict.WRONG, h.result.verdict)
        assertEquals(Hint.ENDING, h.result.hint)
    }

    @Test fun `stem typo in what was heard is almost`() =
        assertEquals(Verdict.ALMOST, SpeechGrading.best(listOf("hvlaa lepa"), null, listOf("Hvala lepa."))!!.result.verdict)

    @Test fun `among equal verdicts the closest wins, then the recognizer's order`() {
        val h = SpeechGrading.best(listOf("kruh", "en kruh prosi", "en kruh prosimo"), null, listOf("En kruh, prosim."))!!
        assertEquals("en kruh prosi", h.text)
        val tie = SpeechGrading.best(listOf("a b", "c d"), null, listOf("x y"))!!
        assertEquals("a b", tie.text)
    }

    @Test fun `unreported confidence is null and blank alternatives are skipped`() {
        val h = SpeechGrading.best(listOf(" ", "hvala"), floatArrayOf(0.8f, 0f), listOf("hvala"))!!
        assertEquals("hvala", h.text)
        assertNull(h.confidence)
        assertNull(SpeechGrading.best(emptyList(), null, listOf("hvala")))
    }

    @Test fun `best attempt is kept across attempts`() {
        val a = SpeechGrading.best(listOf("dober"), null, accept)!!
        val b = SpeechGrading.best(listOf("dober dan jaz sem jan"), null, accept)!!
        assertEquals(b, SpeechGrading.bestOf(listOf(a, b, a)))
    }

    @Test fun `differing words are marked in what was heard`() {
        val heard = "Dober dan, jaz sem Jana"
        val marks = SpeechGrading.differingWords(heard, "Dober dan, jaz sem Jan.")
        assertEquals(listOf("Jana"), marks.map { heard.substring(it) })
    }

    @Test fun `extra and missing words are found`() {
        val heard = "dober dan ja jaz sem Jan"
        assertEquals(listOf("ja"), SpeechGrading.differingWords(heard, "Dober dan, jaz sem Jan.").map { heard.substring(it) })
        val expected = "Dober dan, jaz sem Jan."
        assertEquals(listOf("jaz"), SpeechGrading.differingWords(expected, "dober dan sem jan").map { expected.substring(it) })
    }

    @Test fun `diacritics and punctuation don't count as different words`() =
        assertTrue(SpeechGrading.differingWords("Dober vecer!", "Dober večer.").isEmpty())

    @Test fun `speak parses with defaults and earns wisdom`() {
        val m = parseModule(
            """{"id":"s","version":1,"title":"t","level":"A1","exercises":[
              {"type":"speak","say":"Dober dan.","prompt":"Greet."},
              {"type":"speak","say":"Kruh, prosim.","prompt":"Ask for bread.","accept":["En kruh, prosim."],"show":false}]}""",
        )
        val (a, b) = m.exercises.map { it as Exercise.Speak }
        assertTrue(a.show)
        assertFalse(b.show)
        assertEquals(listOf("Kruh, prosim.", "En kruh, prosim."), b.answers())
        assertEquals(Res.WISDOM, GameEngine.resourceOf(a))
    }

    @Test fun `slovene language tags`() {
        assertTrue(Recognizer.speaks("sl-SI", Lang.SL))
        assertTrue(Recognizer.speaks("sl", Lang.SL))
        assertFalse(Recognizer.speaks("sk-SK", Lang.SL))
        assertFalse(Recognizer.speaks("slk", Lang.SL))
    }

    @Test fun `the recognizer's language tags follow the target`() {
        assertTrue(Recognizer.speaks("it-IT", Lang.IT))
        assertTrue(Recognizer.speaks("it_CH", Lang.IT))
        assertFalse(Recognizer.speaks("sl-SI", Lang.IT))
        assertTrue(Recognizer.speaks("de-AT", Lang.DE))
        assertTrue(Recognizer.speaks("en-US", Lang.EN))
        // the locale each target is recognized (and spoken) in
        assertEquals(listOf("sl-SI", "it-IT", "de-DE", "en-GB"), listOf(Lang.SL, Lang.IT, Lang.DE, Lang.EN).map { it.locale })
    }
}
