package si.lanisce.lani.ui.scene

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * A game's lines brought out one after another (companion/SCENES.md, "I spy"; Jan, 2026-10-19: "she gets cut off at «Vidim,
 * vidim nekaj, …» by the next sentence"): each of the child's lines said to its end before the next comes out and starts.
 */
class LinesInTurnTest {
    private fun his(s: String) = Said("zala", s, "")
    private fun mine(s: String) = Said(null, s, "")

    /** A voice that says a line until the test ends it (its clip played to its end), or until it is stopped. */
    private class FakeVoice {
        val events = mutableListOf<String>()
        private val playing = HashMap<String, CompletableDeferred<Unit>>()

        suspend fun say(l: Said) {
            val end = CompletableDeferred<Unit>()
            playing[l.sl] = end
            events += "start ${l.sl}"
            try {
                end.await()
                events += "end ${l.sl}"
            } catch (e: CancellationException) {
                events += "stopped ${l.sl}"
                throw e
            }
        }

        fun end(s: String) {
            playing.getValue(s).complete(Unit)
        }
    }

    private suspend fun until(what: String, f: () -> Boolean) {
        withTimeoutOrNull(3_000) { while (!f()) delay(2) } ?: fail("timed out waiting: $what")
    }

    @Test
    fun `the game's line is said to its end before the first clue starts`() = runBlocking {
        // ISpyRun.start: the game's line and the first clue come together
        val lines = MutableStateFlow(listOf(his("Vidim, vidim nekaj, česar ti ne vidiš …"), his("Je oster.")))
        val turn = LinesInTurn(first = 0, gap = 0)
        val v = FakeVoice()
        val job = launch { turn.follow(lines, v::say) }
        until("the game's line said") { v.events.isNotEmpty() }
        assertEquals(1, turn.out)
        assertTrue(turn.talking)
        delay(60)
        assertEquals("the clue waits, not out yet", 1, turn.out)
        assertEquals(listOf("start Vidim, vidim nekaj, česar ti ne vidiš …"), v.events)
        v.end("Vidim, vidim nekaj, česar ti ne vidiš …")
        until("the clue said") { v.events.size == 3 }
        assertEquals(listOf("start Vidim, vidim nekaj, česar ti ne vidiš …", "end Vidim, vidim nekaj, česar ti ne vidiš …", "start Je oster."), v.events)
        assertEquals(2, turn.out)
        v.end("Je oster.")
        until("quiet after the clue") { !turn.talking }
        assertEquals(2, turn.out)
        job.cancel()
    }

    @Test
    fun `the learner's guess comes out in its turn, unsaid, and lines added meanwhile wait`() = runBlocking {
        val lines = MutableStateFlow(listOf(his("Je oster.")))
        val turn = LinesInTurn(first = 0, gap = 0)
        val v = FakeVoice()
        val job = launch { turn.follow(lines, v::say) }
        until("the clue said") { v.events.isNotEmpty() }
        // a tap in the picture while the clue is still being said: the guess, the child's reaction and the next clue
        lines.value = lines.value + mine("Žlica?") + his("Ne, to ni to.") + his("Leži na mizi.")
        delay(60)
        assertEquals("nothing more out while the clue is said", 1, turn.out)
        v.end("Je oster.")
        until("the reaction said") { v.events.size == 3 }
        assertEquals("the guess out, then the reaction", 3, turn.out)
        assertEquals("start Ne, to ni to.", v.events.last())
        delay(60)
        assertEquals("the next clue waits for the reaction", 3, turn.out)
        v.end("Ne, to ni to.")
        until("the next clue said") { v.events.size == 5 }
        assertEquals(
            listOf("start Je oster.", "end Je oster.", "start Ne, to ni to.", "end Ne, to ni to.", "start Leži na mizi."),
            v.events,
        )
        assertEquals(4, turn.out)
        assertFalse("the learner's own line is never said", v.events.any { "Žlica" in it })
        job.cancel()
    }

    @Test
    fun `leaving the game stops the line being said at once`() = runBlocking {
        val lines = MutableStateFlow(listOf(his("Vidim, vidim nekaj, česar ti ne vidiš …"), his("Je oster.")))
        val turn = LinesInTurn(first = 0, gap = 0)
        val v = FakeVoice()
        val job = launch { turn.follow(lines, v::say) }
        until("the game's line said") { v.events.isNotEmpty() }
        job.cancel()
        job.join()
        assertEquals(listOf("start Vidim, vidim nekaj, česar ti ne vidiš …", "stopped Vidim, vidim nekaj, česar ti ne vidiš …"), v.events)
        assertFalse(turn.talking)
        assertEquals("the clue never came out", 1, turn.out)
    }

    @Test
    fun `the first line waits for the panel, and a line comes as soon as the run has one`() = runBlocking {
        val lines = MutableStateFlow(emptyList<Said>())
        val turn = LinesInTurn(first = 400, gap = 0)
        val v = FakeVoice()
        val job = launch { turn.follow(lines, v::say) }
        delay(30)
        assertEquals(0, turn.out)
        lines.value = listOf(his("Je oster."))
        delay(100)
        assertEquals("not before the first pause", 0, turn.out)
        until("the clue said after the pause") { v.events.isNotEmpty() }
        assertEquals(1, turn.out)
        job.cancel()
    }
}
