package si.lanisce.lani.ui.talk

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.SttResult
import si.lanisce.lani.data.SttSegment
import si.lanisce.lani.data.SttUnavailable
import si.lanisce.lani.data.SttWord
import si.lanisce.lani.ui.talk.TalkMic.Engine
import java.io.File

/**
 * The talk's 🎤: tap to start and stop, a long pause ends it and short ones don't, "Next sentence", transcripts in
 * the order they were said, unclear words, saying a word again, and the phone's recognizer listening sentence after
 * sentence. What was heard only ever lands in the draft.
 */
class TalkMicTest {
    /** The recognizers, by hand: every node upload waits until the test answers it. */
    private class Ears : TalkEars {
        val calls = ArrayList<String>()
        val uploads = ArrayList<CompletableDeferred<SttResult>>()
        val expected = ArrayList<String?>()
        private var n = 0

        override fun record(limitMs: Int) {
            calls += "record"
        }

        override fun stopRecording(): File = File("never-written-${n++}.m4a")

        override suspend fun transcribe(take: File, expected: String?): SttResult {
            val d = CompletableDeferred<SttResult>()
            uploads += d
            this.expected += expected
            return d.await()
        }

        override fun listen(again: Boolean) {
            calls += if (again) "listen again" else "listen"
        }

        override fun stopListening() {
            calls += "stop listening"
        }

        override fun cancelListening() {
            calls += "cancel"
        }
    }

    private val ears = Ears()
    private val draft = TalkDraft()
    private val mic = TalkMic(CoroutineScope(Dispatchers.Unconfined), ears, draft)
    private var t = 0L

    /** [ms] of the recorder's ticks at [amplitude]. */
    private fun sound(ms: Long, amplitude: Int) {
        val until = t + ms
        while (t < until && mic.recording) {
            t += 100
            mic.tick(t, amplitude)
        }
    }

    private fun speak(ms: Long) = sound(ms, 6_000)
    private fun quiet(ms: Long) = sound(ms, 300)

    private fun heard(i: Int, text: String, words: List<SttWord> = emptyList()) =
        ears.uploads[i].complete(SttResult(text, segments = listOf(SttSegment(text = text, words = words))))

    // --- the node's Whisper ----------------------------------------------------------------------

    @Test fun `short pauses between sentences keep recording, a long one ends it - the text waits in the draft`() {
        mic.start(Engine.NODE, t)
        assertTrue(mic.recording)
        speak(2_000)
        quiet(1_500) // between two sentences
        assertTrue(mic.recording)
        speak(1_500)
        quiet(2_900)
        assertTrue(mic.recording)
        quiet(200) // 3 s of quiet
        assertFalse(mic.recording)
        assertEquals(1, ears.uploads.size)
        assertTrue(mic.transcribing)
        assertFalse("nothing to send while it is transcribed", mic.canSend(enabled = true))

        heard(0, " Dober dan. Rad bi kavo.")
        assertFalse(mic.busy)
        assertEquals("Dober dan. Rad bi kavo.", draft.text)
        assertTrue(mic.canSend(enabled = true))
        assertFalse("not while the character answers", mic.canSend(enabled = false))
        assertEquals(Said("Dober dan. Rad bi kavo.", emptyList(), typed = false), draft.take())
        assertEquals("", draft.text)
    }

    @Test fun `a tap stops it - what was said so far is transcribed`() {
        mic.start(Engine.NODE, t)
        speak(1_200)
        mic.stop(t)
        assertFalse(mic.recording)
        heard(0, "Živjo!")
        assertEquals("Živjo!", draft.text)
        assertNull(mic.message)
    }

    @Test fun `a soft voice is sent on a tap, a tap on silence isn't`() {
        mic.start(Engine.NODE, t)
        sound(1_000, 900) // under the speech level, but not the room
        mic.stop(t)
        assertEquals(1, ears.uploads.size)
        mic.start(Engine.NODE, t)
        quiet(1_000)
        mic.stop(t)
        assertEquals(1, ears.uploads.size)
    }

    @Test fun `silence gives up and isn't sent`() {
        mic.start(Engine.NODE, t)
        quiet(12_000)
        assertFalse(mic.recording)
        assertTrue(ears.uploads.isEmpty())
        assertEquals(TalkMic.NOT_HEARD, mic.message)
        assertEquals("", draft.text)
    }

    @Test fun `next sentence sends at once with a full stop and records on - transcripts land in the order said`() {
        mic.start(Engine.NODE, t)
        speak(1_500)
        mic.next(t)
        assertTrue(mic.recording)
        speak(1_500)
        mic.next(t)
        speak(1_000)
        mic.stop(t)
        assertEquals(listOf("record", "record", "record"), ears.calls)
        assertEquals(3, ears.uploads.size)

        heard(2, "In čaj")
        assertEquals("", draft.text) // waits for the first two
        heard(0, "Rad bi kavo")
        assertEquals("Rad bi kavo.", draft.text)
        assertTrue(mic.transcribing)
        heard(1, "Z mlekom!")
        assertEquals("Rad bi kavo. Z mlekom! In čaj", draft.text)
        assertFalse(mic.busy)
    }

    @Test fun `after next sentence a pause to think is fine`() {
        mic.start(Engine.NODE, t)
        speak(1_500)
        mic.next(t)
        quiet(5_000) // thinking of the next sentence: a new stretch waits longer before it gives up
        assertTrue(mic.recording)
        speak(1_000)
        quiet(3_100) // 3 s after the first quiet tick
        assertFalse(mic.recording)
        assertEquals(2, ears.uploads.size)
    }

    @Test fun `a long stretch is cut at a pause for the node, and goes on`() {
        mic.start(Engine.NODE, t)
        speak(19_000)
        assertEquals(1, ears.calls.size)
        quiet(400) // a breath after 18 s
        assertEquals(listOf("record", "record"), ears.calls)
        assertTrue(mic.recording)
        speak(2_000)
        quiet(3_100) // 3 s after the first quiet tick
        assertFalse(mic.recording)
        heard(1, "in potem domov.")
        heard(0, "Najprej v trgovino")
        assertEquals("Najprej v trgovino in potem domov.", draft.text) // a cut is no full stop

        // without a pause: at the node's limit
        mic.start(Engine.NODE, t)
        speak(28_500)
        assertEquals(4, ears.calls.size)
    }

    @Test fun `the node down - said so, and the next sentence still arrives in order`() {
        mic.start(Engine.NODE, t)
        speak(1_000)
        mic.next(t)
        speak(1_000)
        mic.stop(t)
        ears.uploads[0].completeExceptionally(SttUnavailable("HTTP 503"))
        heard(1, "Drugi.")
        assertEquals("Drugi.", draft.text)
        assertTrue(mic.message!!.startsWith("📶"))
    }

    @Test fun `unclear words are marked, three at most a take`() {
        mic.start(Engine.NODE, t)
        speak(1_000)
        mic.next(t)
        speak(1_000)
        mic.stop(t)
        heard(
            0, " Dober dan, poštal prinaša pismo za sosedo.",
            listOf(
                SttWord(" Dober", prob = 0.9), SttWord(" dan,", prob = 0.95), SttWord(" poštal", prob = 0.2),
                SttWord(" prinaša", prob = 0.25), SttWord(" pismo", prob = 0.1), SttWord(" za", prob = 0.9),
                SttWord(" sosedo.", prob = 0.28), SttWord(" v", prob = 0.01),
            ),
        )
        assertEquals(listOf("poštal", "prinaša", "pismo"), draft.marks.map { it.word }) // the three least sure, in order
        assertEquals("poštal", draft.text.substring(draft.marks[0].start, draft.marks[0].end))
        heard(1, "Hvala.", listOf(SttWord(" Hvala.", prob = 0.05)))
        assertEquals(3, draft.marks.size) // the take has its three
    }

    @Test fun `a marked word said again replaces it`() {
        mic.start(Engine.NODE, t)
        speak(1_000)
        mic.stop(t)
        heard(0, "Poštal prinaša pismo.", listOf(SttWord("Poštal", prob = 0.1), SttWord(" prinaša", prob = 0.9), SttWord(" pismo.", prob = 0.2)))
        val (first, second) = draft.marks
        assertEquals("Poštal" to "pismo", first.word to second.word)

        mic.sayAgain(first, Engine.NODE, t)
        assertEquals(first, mic.redo)
        speak(600)
        quiet(1_000) // a single word ends at a short pause
        assertFalse(mic.recording)
        assertEquals("Poštal", ears.expected.last()) // steers Whisper's spelling
        heard(1, "poštar.")
        assertEquals("Poštar prinaša pismo.", draft.text)
        assertNull(mic.redo)
        assertEquals(listOf("pismo"), draft.marks.map { it.word })
        assertEquals("pismo", draft.text.substring(draft.marks[0].start, draft.marks[0].end))
        assertFalse("still spoken", draft.take()!!.typed)
    }

    // --- the phone's recognizer --------------------------------------------------------------

    @Test fun `the phone listens again after each sentence and appends them until a long pause`() {
        mic.start(Engine.PHONE, 0)
        mic.phonePartial("dober", 500)
        assertEquals("dober", mic.partial)
        mic.phoneResults(listOf("dober dan", "dober dam"), 1_200)
        assertEquals("dober dan", draft.text)
        assertEquals("listen again", ears.calls.last())
        mic.tick(2_000, 0)
        mic.phoneNothing(2_300) // the restart heard nothing yet: it listens on
        assertEquals("listen again", ears.calls.last())
        mic.phonePartial("rad bi", 2_600) // 1.4 s after the first sentence
        mic.phoneResults(listOf("rad bi kavo"), 3_500)
        assertEquals("dober dan rad bi kavo", draft.text)
        assertTrue(mic.recording)
        mic.tick(6_400, 0)
        assertTrue(mic.recording)
        mic.tick(6_600, 0) // 3.1 s quiet
        assertEquals("stop listening", ears.calls.last())
        mic.phoneNothing(6_700)
        assertFalse(mic.recording)
        assertEquals("dober dan rad bi kavo", draft.text)
        assertNull(mic.message)
        assertEquals(Said("dober dan rad bi kavo", emptyList(), typed = false), draft.take())
    }

    @Test fun `a tap ends the phone's take with the sentence being said`() {
        mic.start(Engine.PHONE, 0)
        mic.phoneResults(listOf("živjo"), 1_000)
        mic.phonePartial("kako", 1_500)
        mic.stop(1_600)
        assertTrue(mic.recording) // its last result comes first
        mic.phoneResults(listOf("kako si"), 1_800)
        assertFalse(mic.recording)
        assertEquals("živjo kako si", draft.text)
    }

    @Test fun `a phone that doesn't answer the stop keeps what it heard`() {
        mic.start(Engine.PHONE, 0)
        mic.phonePartial("hvala lepa", 800)
        mic.stop(1_000)
        mic.tick(1_000 + TalkMic.STOP_WAIT_MS, 0)
        assertFalse(mic.recording)
        assertEquals("hvala lepa", draft.text)
    }

    @Test fun `next sentence on the phone marks where one ends`() {
        mic.start(Engine.PHONE, 0)
        mic.phonePartial("dober dan", 700)
        mic.next(900)
        assertEquals("stop listening", ears.calls.last())
        mic.phoneResults(listOf("dober dan"), 1_000)
        assertEquals("dober dan.", draft.text)
        assertEquals("listen again", ears.calls.last())
        mic.phoneResults(listOf("kako si"), 2_000)
        mic.next(2_100) // between sentences: the full stop goes straight in
        assertEquals("dober dan. kako si.", draft.text)
        mic.tick(2_100 + 9_000, 0) // a new stretch waits longer for the next sentence
        assertTrue(mic.recording)
    }

    @Test fun `nothing heard on the phone says so`() {
        mic.start(Engine.PHONE, 0)
        mic.tick(10_100, 0)
        mic.phoneNothing(10_200)
        assertFalse(mic.recording)
        assertEquals(TalkMic.NOT_HEARD, mic.message)
    }

    @Test fun `one phone sentence as it came keeps its other readings`() {
        mic.start(Engine.PHONE, 0)
        mic.phoneResults(listOf("rad bi kavo", "rad bi kafo"), 1_000)
        mic.stop(1_100)
        mic.phoneNothing(1_200)
        assertEquals(Said("rad bi kavo", listOf("rad bi kafo"), typed = false), draft.take())
    }

    @Test fun `leaving drops the take on now`() {
        mic.start(Engine.NODE, t)
        speak(1_000)
        mic.cancel()
        assertFalse(mic.busy)
        assertTrue(ears.uploads.isEmpty())
        mic.start(Engine.NODE, t) // and the next take still lands
        speak(1_000)
        mic.stop(t)
        heard(0, "Spet.")
        assertEquals("Spet.", draft.text)
    }
}

/** Transcripts handed on in the order they were asked for. */
class InOrderTest {
    @Test fun `later results wait for earlier ones - an empty one doesn't hold them up`() {
        val o = InOrder<String>()
        val (a, b, c) = List(3) { o.open() }
        assertEquals(emptyList<String>(), o.fill(c, "c"))
        assertEquals(emptyList<String>(), o.fill(b, "b"))
        assertEquals(3, o.pending)
        assertEquals(listOf("a", "b", "c"), o.fill(a, "a"))
        assertEquals(0, o.pending)
        val d = o.open()
        val e = o.open()
        assertEquals(emptyList<String>(), o.fill(e, "e"))
        assertEquals(listOf("e"), o.fill(d, null))
        assertEquals(emptyList<String>(), o.fill(d, "again")) // once only
    }
}
