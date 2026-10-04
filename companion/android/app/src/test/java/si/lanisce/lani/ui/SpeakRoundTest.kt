package si.lanisce.lani.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.Hints
import si.lanisce.lani.data.ReviewPlanner
import si.lanisce.lani.data.SpeechGrading

class SpeakRoundTest {
    private val ex = Exercise.Speak(say = "Moj priimek je Novak.", prompt = "Say your surname.", show = false, explain = "priimek = surname")
    private fun heard(text: String) = SpeechGrading.best(listOf(text), null, ex.answers())!!
    private val right get() = heard("moj priimek je novak")
    private val wrong get() = heard("moja primek je nova")

    private fun hinted(n: Int) = (1..n).fold(SpeakRound(ex)) { r, _ -> r.hint() }

    // --- 💡 ---------------------------------------------------------------------------------

    @Test fun `hints reveal the model step by step and stop at the whole sentence`() {
        val r = SpeakRound(ex)
        assertEquals(5, r.levels)
        assertNull(r.hintLine)
        assertEquals("M__ p______ j_ N____.", r.hint().hintLine)
        assertEquals("Moj p______ j_ N____.", hinted(2).hintLine)
        val all = hinted(5)
        assertTrue(all.revealedAll)
        assertEquals(ex.say, all.hintLine)
        assertEquals(5, hinted(9).hints) // capped at the last level
        assertFalse(hinted(4).revealedAll)
    }

    @Test fun `a right answer with hints stays right but pays and reviews as less`() {
        val r = hinted(1).heard(right)
        val o = r.result!!
        assertEquals(Verdict.CORRECT, o.verdict)
        assertEquals(1, o.hints)
        assertEquals(Verdict.ALMOST, o.paid)
        assertEquals(4, Hints.penalize(ReviewPlanner.quality(ReviewPlanner.Variant.SPEAK, o.verdict), o.hints))
    }

    @Test fun `reading the fully revealed sentence aloud still passes, with the lowest passing grade`() {
        val o = hinted(5).heard(right).result!!
        assertEquals(Verdict.CORRECT, o.verdict)
        assertEquals(5, o.hints)
        assertEquals(Verdict.ALMOST, o.paid)
        assertEquals(3, Hints.penalize(ReviewPlanner.quality(ReviewPlanner.Variant.SPEAK, o.verdict), o.hints))
    }

    @Test fun `without hints nothing is taken off`() {
        val o = SpeakRound(ex).heard(right).result!!
        assertEquals(0, o.hints)
        assertEquals(Verdict.CORRECT, o.paid)
        assertTrue(o.spoken)
    }

    @Test fun `hints can't be taken once the result is in`() {
        val done = SpeakRound(ex).heard(right)
        assertSame(done, done.hint())
    }

    @Test fun `a sentence without words offers no hints`() {
        val r = SpeakRound(Exercise.Speak(say = "…", prompt = "Hesitate.", show = false))
        assertEquals(0, r.levels)
        assertSame(r, r.hint())
    }

    // --- attempts ---------------------------------------------------------------------------

    @Test fun `the round ends on the first right attempt or the last`() {
        assertNull(SpeakRound(ex).heard(wrong).heard(wrong).result)
        val out = SpeakRound(ex).heard(wrong).heard(wrong).heard(wrong)
        assertEquals(MAX_ATTEMPTS, out.attempts.size)
        assertEquals(Verdict.WRONG, out.result!!.verdict)
        assertEquals(Verdict.CORRECT, SpeakRound(ex).heard(wrong).heard(right).result!!.verdict)
    }

    @Test fun `giving up grades the best attempt, and needs one`() {
        val none = SpeakRound(ex)
        assertSame(none, none.end())
        val gaveUp = hinted(2).heard(wrong).end()
        assertEquals(Verdict.WRONG, gaveUp.result!!.verdict)
        assertEquals(2, gaveUp.result!!.hints)
        assertTrue(gaveUp.sayAgain)
    }

    // --- say it again ---------------------------------------------------------------------------

    @Test fun `saying it again after a miss is practice and never changes the result`() {
        val missed = SpeakRound(ex).heard(wrong).heard(wrong).heard(wrong)
        val result = missed.result!!
        assertTrue(missed.sayAgain)
        assertFalse(missed.saidRight)

        val once = missed.heard(right)
        assertTrue(once.saidRight)
        assertEquals(result, once.result)
        assertEquals(MAX_ATTEMPTS, once.attempts.size)

        val twice = once.heard(wrong) // repeatable, and the last try is what shows
        assertFalse(twice.saidRight)
        assertEquals(2, twice.practice.size)
        assertEquals(result, twice.result)
        assertEquals(result, twice.end().result)
        assertEquals(result, twice.rate(true).result)
        assertSame(twice, twice.hint())
    }

    @Test fun `a right answer offers no say-it-again`() =
        assertFalse(SpeakRound(ex).heard(right).sayAgain)

    @Test fun `an almost also offers saying it again`() {
        val almost = SpeakRound(ex).heard(heard("moj primek je novak")).end() // a slip in the stem
        assertEquals(Verdict.ALMOST, almost.result!!.verdict)
        assertTrue(almost.sayAgain)
    }

    // --- rating yourself --------------------------------------------------------------------------

    @Test fun `rating yourself carries the hints and offers no say-it-again`() {
        val said = hinted(2).rate(true)
        assertEquals(Verdict.CORRECT, said.result!!.verdict)
        assertEquals(2, said.result!!.hints)
        assertEquals(Verdict.ALMOST, said.result!!.paid)
        val practising = SpeakRound(ex).rate(false)
        assertEquals(Verdict.WRONG, practising.result!!.verdict)
        assertFalse(practising.sayAgain)
        assertSame(practising, practising.heard(right)) // nothing listens after a self-rating
    }
}
