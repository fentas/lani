package si.lanisce.lani.data

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.io.File

class SttTest {
    private fun result(vararg words: Pair<String, Double?>) =
        SttResult(words.joinToString(" ") { it.first }, listOf(SttSegment(0.0, 2.0, "", words.map { SttWord(it.first, prob = it.second) })))

    // --- request ------------------------------------------------------------------------

    @Test fun `url carries the language and the expected sentence as prompt`() {
        val u = SttSpeech.url("http://node:8790/", "Dober dan, jaz sem Jan.")
        assertEquals("/stt", u.encodedPath)
        assertEquals("sl", u.queryParameter("language"))
        assertEquals("Dober dan, jaz sem Jan.", u.queryParameter("prompt"))
        assertTrue(u.toString().startsWith("http://node:8790/stt?language=sl&prompt=Dober%20dan"))
    }

    @Test fun `the request carries the pair's target, and the status asks for it`() {
        assertEquals("it", SttSpeech.url("http://n", "Ciao", language = "it").queryParameter("language"))
        val s = SttSpeech.statusUrl("http://node:8790/", "it")
        assertEquals("/stt/status", s.encodedPath)
        assertEquals("it", s.queryParameter("language"))
        val before = L10n.pair
        try {
            L10n.pair = LangPair(Lang.IT, Lang.SL)
            assertEquals("it", SttSpeech.url("http://n", "Dov'è l'acqua?").queryParameter("language"))
            L10n.pair = LangPair.DEFAULT
            assertEquals("sl", SttSpeech.url("http://n", null).queryParameter("language"))
        } finally {
            L10n.pair = before
        }
    }

    @Test fun `blank prompt is left out and a long one is cut`() {
        assertNull(SttSpeech.url("http://n", "  ").queryParameter("prompt"))
        assertNull(SttSpeech.url("http://n", null).queryParameter("prompt"))
        assertEquals(300, SttSpeech.url("http://n", "a".repeat(500)).queryParameter("prompt")!!.length)
        assertEquals("de", SttSpeech.url("http://n", null, language = "de").queryParameter("language"))
    }

    @Test fun `request posts the recording with the app token`() {
        val f = File.createTempFile("take", ".m4a").apply { writeBytes(byteArrayOf(0, 0, 0, 0x18, 'f'.code.toByte(), 't'.code.toByte())) }
        try {
            val r = SttSpeech.request(BridgeConfig("http://node:8790", "tok"), f, "Kruh, prosim.")
            assertEquals("POST", r.method)
            assertEquals("Bearer tok", r.header("Authorization"))
            assertEquals("audio/mp4", r.body!!.contentType().toString())
            assertEquals(6L, r.body!!.contentLength())
            assertEquals("Kruh, prosim.", r.url.queryParameter("prompt"))
            val sent = Buffer().also { r.body!!.writeTo(it) }.readByteArray()
            assertEquals(f.readBytes().toList(), sent.toList())
        } finally {
            f.delete()
        }
    }

    // --- response -----------------------------------------------------------------------

    @Test fun `bridge response parses, extra fields ignored`() {
        val raw = """{"text":"Dober dan.","segments":[{"start":0.0,"end":1.2,"text":"Dober dan.","avg_logprob":-0.2,
            "words":[{"word":"Dober","start":0.0,"end":0.5,"prob":0.93},{"word":"dan.","start":0.5,"end":1.2,"prob":0.41}]}],
            "language":"sl","duration":1.3,"took":0.21,"model":"large-v3-turbo"}"""
        val r = SttSpeech.parse(raw)
        assertEquals("Dober dan.", r.text)
        assertEquals(listOf("Dober", "dan."), r.words.map { it.word })
        assertEquals(0.41, r.words[1].prob!!, 1e-9)
        assertEquals("large-v3-turbo", r.model)
        assertEquals(0.67f, SttSpeech.confidence(r)!!, 0.001f)
    }

    @Test fun `minimal response parses and has no confidence`() {
        val r = SttSpeech.parse("""{"text":"","segments":[]}""")
        assertEquals("", r.text)
        assertTrue(r.words.isEmpty())
        assertNull(SttSpeech.confidence(r))
        assertNull(SttSpeech.heard(r, listOf("Hvala.")))
    }

    // --- grading and hints ----------------------------------------------------------------

    @Test fun `heard text is graded like any recognizer result`() {
        val h = SttSpeech.heard(result("Dober" to 0.95, "dan," to 0.9, "jaz" to 0.97, "sem" to 0.99, "Jan." to 0.92), listOf("Dober dan, jaz sem Jan."))!!
        assertEquals(Verdict.CORRECT, h.result.verdict)
        assertTrue(h.unclear.isEmpty())
        assertEquals(0.946f, h.confidence!!, 0.001f)
    }

    @Test fun `an unsure word becomes a pronunciation hint in the expected spelling`() {
        val r = result("Na" to 0.99, "vrtu" to 0.97, "je" to 0.98, "čebelniak." to 0.21)
        val h = SttSpeech.heard(r, listOf("Na vrtu je čebelnjak."))!!
        assertEquals(listOf("čebelnjak"), h.unclear)
        assertEquals("🎯 izgovorjava · pronunciation: 'čebelnjak' was unclear", SttSpeech.hint(h.unclear))
    }

    @Test fun `an unsure word that matches still gets the hint, a sure wrong one doesn't`() {
        // The prompt pulled "Moja" in, but Whisper wasn't sure: say it clearly.
        assertEquals(listOf("Moja"), SttSpeech.unclear(result("Moja" to 0.25, "partnerka" to 0.9, "je" to 0.99), "Moja partnerka je"))
        assertTrue(SttSpeech.unclear(result("Moj" to 0.95, "partnerka" to 0.9), "Moja partnerka").isEmpty())
    }

    @Test fun `extra words are not hints, missing ones don't shift the rest`() {
        // "eee" is filler (extra); "sem" is missing; the unsure "Jan" still maps to "Jan".
        val r = result("Dober" to 0.9, "eee" to 0.1, "dan" to 0.95, "jaz" to 0.9, "Jan" to 0.2)
        assertEquals(listOf("Jan"), SttSpeech.unclear(r, "Dober dan, jaz sem Jan."))
    }

    @Test fun `a garbled word maps to the word it stands for`() {
        val r = result("danes" to 0.7, "je" to 0.89, "dosh" to 0.25, "in" to 0.94, "vetr." to 0.2)
        assertEquals(listOf("dež", "veter"), SttSpeech.unclear(r, "Danes je dež in veter."))
    }

    @Test fun `hints are unique, capped and follow the threshold`() {
        val r = result("ena" to 0.1, "dva" to 0.2, "tri" to 0.25, "štiri" to 0.28, "pet" to 0.6, "ena" to 0.1)
        assertEquals(listOf("ena", "dva", "tri"), SttSpeech.unclear(r, "ena dva tri štiri pet ena"))
        assertEquals(listOf("ena", "dva"), SttSpeech.unclear(r, "ena dva tri štiri pet ena", threshold = 0.25))
        assertTrue(SttSpeech.unclear(result("hvala" to null), "hvala").isEmpty())
        assertTrue(SttSpeech.unclear(result("Grem" to 0.9, "v" to 0.05, "trgovino" to 0.8), "Grem v trgovino.").isEmpty()) // one letter: no hint
        assertNull(SttSpeech.hint(emptyList()))
        assertEquals("🎯 izgovorjava · pronunciation: 'dež', 'veter' were unclear", SttSpeech.hint(listOf("dež", "veter")))
    }

    @Test fun `alignment pairs heard with expected words`() {
        val pairs = SttSpeech.align(listOf("boljeme", "grl"), listOf("Boli", "me", "grlo"))
        assertEquals(2, pairs.size)
        assertEquals(2, pairs[1].second) // grl ~ grlo
        assertEquals(listOf(0 to null), SttSpeech.align(listOf("x"), emptyList()))
        assertTrue(SttSpeech.align(emptyList(), listOf("a")).isEmpty())
    }

    // --- auto-stop ---------------------------------------------------------------------------

    private fun run(gate: SilenceGate, levels: List<Int>, stepMs: Long = 100): Long? {
        levels.forEachIndexed { i, a -> if (gate.feed(a, i * stepMs)) return i * stepMs }
        return null
    }

    @Test fun `stops after a pause once speech started`() {
        val g = SilenceGate()
        val levels = List(5) { 300 } + List(12) { 6_000 } + List(20) { 400 }
        val at = run(g, levels)!!
        assertTrue(g.started)
        assertEquals((17 + 14) * 100L, at) // quiet from index 17, 1.4 s later
    }

    @Test fun `a short blip is not speech, and silence gives up`() {
        val g = SilenceGate()
        val levels = List(10) { 300 } + listOf(5_000) + List(90) { 300 }
        assertEquals(8_000L, run(g, levels))
        assertFalse(g.started)
    }

    @Test fun `quiet is relative to the loudest level`() {
        val g = SilenceGate()
        // Loud speech (12000), then softer speech (3000, above a sixth of the peak): keeps going.
        val levels = List(5) { 12_000 } + List(30) { 3_000 }
        assertNull(run(g, levels))
        assertTrue(SilenceGate().let { gg -> run(gg, List(5) { 12_000 } + List(30) { 1_500 }) } != null)
    }
}
