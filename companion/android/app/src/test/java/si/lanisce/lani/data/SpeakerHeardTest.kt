package si.lanisce.lani.data

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How [Speaker.sayThrough] knows a line has been heard ([Speaker.heard]): while it loads from the node and plays, it waits;
 * a later line or a stop cuts it; nothing that could say it, it doesn't wait.
 */
class SpeakerHeardTest {
    @Test
    fun `it waits while the line is on its way and while it plays`() = runBlocking {
        var loading = true
        var playing = false
        val heard = async { Speaker.heard(1, { 1 }, { loading || playing }, { playing }, pollMs = 5) }
        delay(40)
        assertFalse("still on its way from the node", heard.isCompleted)
        // the clip arrives and plays (one step on the main thread: no moment between them)
        loading = false
        playing = true
        delay(40)
        assertFalse("playing", heard.isCompleted)
        playing = false
        assertTrue("heard to its end", heard.await())
    }

    @Test
    fun `a later line or a stop cuts it`() = runBlocking {
        var turn = 1
        val heard = async { Speaker.heard(1, { turn }, { true }, { true }, pollMs = 5) }
        delay(30)
        assertFalse(heard.isCompleted)
        turn = 2 // a tap on another 🔊
        assertTrue("it was heard, until cut", heard.await())
    }

    @Test
    fun `a player that never ends holds the next line only so long`() = runBlocking {
        val heard = async { Speaker.heard(1, { 1 }, { true }, { true }, pollMs = 5, longestMs = 60) }
        assertTrue("given up after its longest wait", withTimeoutOrNull(5_000) { heard.await() } == true)
    }

    @Test
    fun `nothing could say it, or it was cut while still on its way`() = runBlocking {
        assertFalse("no voice at all: not busy at once", Speaker.heard(1, { 1 }, { false }, { false }, pollMs = 5))
        var turn = 1
        val loading = async { Speaker.heard(1, { turn }, { true }, { false }, pollMs = 5) }
        delay(30)
        turn = 2
        assertFalse("cut while it loaded: nothing heard", loading.await())
    }
}
