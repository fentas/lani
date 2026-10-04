package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceTest {
    private fun rec(file: String, speaker: String = "👵 Micka") = Recording(file, speaker)

    // Keep these vectors in sync with the bridge's normalizeText (companion/bridge/test/smoke.ts).
    @Test fun `normalize lowercases, strips punctuation and collapses space`() {
        assertEquals("dober dan", Voice.normalize("  Dober   DAN! "))
        assertEquals("hvala lepa", Voice.normalize("Hvala (lepa)."))
        assertEquals("živjo", Voice.normalize("„Živjo“"))
        assertEquals("imenujem se", Voice.normalize("Imenujem se ..."))
        assertEquals("kako si", Voice.normalize("Kako si?"))
        assertEquals("dober dan", Voice.normalize("dober dan"))
    }

    @Test fun `decomposed diacritics match composed ones`() =
        assertEquals(Voice.normalize("večer"), Voice.normalize("večer"))

    @Test fun `phrases split slashes and arrows`() {
        assertEquals(listOf("Kako ste?", "Kako si?"), Voice.phrases("Kako ste? / Kako si?"))
        assertEquals(listOf("Nemčija", "iz Nemčije"), Voice.phrases("Nemčija → iz Nemčije"))
        assertEquals(listOf("Imenujem se"), Voice.phrases("Imenujem se ..."))
    }

    @Test fun `exact text finds its recording`() {
        val index = mapOf("dober dan" to listOf(rec("a.m4a"), rec("b.m4a")))
        assertEquals(listOf(rec("b.m4a")), Voice.resolve(index, "Dober dan!") { it.last() })
    }

    @Test fun `no recording means TTS`() {
        assertNull(Voice.resolve(mapOf("dober dan" to listOf(rec("a.m4a"))), "Dober večer"))
        assertNull(Voice.resolve(mapOf("dober dan" to emptyList()), "Dober dan"))
    }

    @Test fun `a slash text plays its phrases only when all are recorded`() {
        val index = mapOf("kako ste" to listOf(rec("s.m4a")), "kako si" to listOf(rec("i.m4a")))
        assertEquals(listOf(rec("s.m4a"), rec("i.m4a")), Voice.resolve(index, "Kako ste? / Kako si?") { it.first() })
        assertNull(Voice.resolve(index - "kako si", "Kako ste? / Kako si?"))
    }

    @Test fun `index parses the bridge format`() {
        val raw = """{"dober dan":[{"file":"0a.m4a","speaker":"👵 Micka","recorded_at":"2026-09-23T10:00:00Z","text":"Dober dan!"}]}"""
        val r = Voice.parseIndex(raw)["dober dan"]!!.single()
        assertEquals("0a.m4a", r.file)
        assertEquals("Dober dan!", r.text)
    }

    @Test fun `badge keeps an emoji and adds one to a bare name`() {
        assertEquals("👵 Micka", Voice.badge("👵 Micka"))
        assertEquals("🗣️ Maja", Voice.badge("Maja"))
        assertEquals("🗣️", Voice.badge(" "))
    }
}
