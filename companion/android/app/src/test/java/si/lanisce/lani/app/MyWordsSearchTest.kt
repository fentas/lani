package si.lanisce.lani.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.MyWord
import si.lanisce.lani.data.WordEntry
import si.lanisce.lani.data.WordLookup
import java.io.IOException

/**
 * The search of "📚 Moje besede · My words" (app/MyWordsSearch.kt): the dictionary asked a moment after the typing stops
 * for a word that isn't one of the learner's (GET /lookup), what it found, nothing found, offline or an older node; a
 * newer search drops the one under way; none for one of theirs.
 *
 * On the test scheduler's virtual time (runTest): the debounce and the test's waits are on one clock that only the
 * test moves. With runBlocking and real delays, a thread stalled for more than 120 ms (a garbage collection or a page
 * fault under load, in the full suite) between the test's wait and the debounce's start let the wait end first, and
 * the first test found nothing asked.
 */
class MyWordsSearchTest {
    private val words = listOf(MyWord("v_hisa", "hiša", "house", true), MyWord("v_pes", "pes", "dog", true))
    private val entry = { lemma: String, known: Boolean -> WordEntry(lemma, "noun", listOf("dog"), known = known) }

    @Test fun `a word not theirs is looked up after the typing stops, what the dictionary has shown`() = runTest {
        val asked = mutableListOf<String>()
        val s = MyWordsSearch(this, { w -> asked += w; WordLookup(w, listOf(entry("mačka", false))) }, debounceMs = 30)
        s.type("ma", words)
        s.type("mač", words)
        s.type("mačka", words)
        assertTrue(s.look is DictionaryLook.Looking)
        // not before the typing has rested for the debounce, then once, for the last word
        advanceTimeBy(29)
        runCurrent()
        assertEquals(emptyList<String>(), asked)
        assertTrue(s.look is DictionaryLook.Looking)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("mačka"), asked)
        val found = s.look as DictionaryLook.Found
        assertEquals("mačka", found.word)
        assertEquals(listOf("mačka"), found.entries.map { it.lemma })
        assertEquals("mačka", s.query)
        // a space after it: the same word, not asked again
        s.type("mačka ", words)
        delay(80)
        assertEquals(1, asked.size)
        assertTrue(s.look is DictionaryLook.Found)
    }

    @Test fun `one of theirs is not looked up - the list has it - a form of theirs is (the dictionary names its word)`() = runTest {
        val asked = mutableListOf<String>()
        val s = MyWordsSearch(this, { w -> asked += w; WordLookup(w, listOf(entry("hiša", true))) }, debounceMs = 0)
        s.type("hisa", words)
        delay(40)
        assertEquals(DictionaryLook.Idle, s.look)
        s.type("hiše", words)
        delay(40)
        assertEquals(listOf("hiše"), asked)
        assertTrue((s.look as DictionaryLook.Found).entries.single().known)
        s.clear()
        assertEquals(DictionaryLook.Idle, s.look)
        assertEquals("", s.query)
    }

    @Test fun `nothing found, offline, an older node - each said as it is`() = runTest {
        val none = MyWordsSearch(this, { w -> WordLookup(w) }, debounceMs = 0)
        none.type("xyzzy", words)
        delay(40)
        assertEquals(DictionaryLook.NotFound("xyzzy"), none.look)
        val offline = MyWordsSearch(this, { throw IOException("not connected") }, debounceMs = 0)
        offline.type("mačka", words)
        delay(40)
        assertEquals(DictionaryLook.Offline("mačka"), offline.look)
        val older = MyWordsSearch(this, { null }, debounceMs = 0)
        older.type("mačka", words)
        delay(40)
        assertEquals(DictionaryLook.Unavailable, older.look)
    }

    @Test fun `a newer search drops the one under way - what shows is the last word's`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val s = MyWordsSearch(this, { w ->
            if (w == "kruh") gate.await()
            WordLookup(w, listOf(entry(w, false)))
        }, debounceMs = 0)
        s.type("kruh", words)
        delay(20) // kruh's lookup under way
        s.type("mleko", words)
        delay(20)
        gate.complete(Unit)
        delay(20)
        assertEquals("mleko", (s.look as DictionaryLook.Found).word)
        // emptied while one is under way: nothing shows
        s.type("voda", words)
        s.type("", words)
        delay(40)
        assertEquals(DictionaryLook.Idle, s.look)
    }
}
