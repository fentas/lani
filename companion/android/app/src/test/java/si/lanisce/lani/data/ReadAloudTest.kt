package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.ReadAloud.Mark
import si.lanisce.lani.l10n.Lang

/**
 * Reading aloud, graded word by word (data/ReadAloud.kt). The Whisper transcripts below are the node's (large-v3-turbo on
 * 127.0.0.1:8796, 2026-09-27), of three voice clips of the voice store: each heard with the text as the prompt and without
 * it, and with a text that differs from what the clip says as the prompt (a word misread, words skipped, endings), as
 * companion/stt-local/README.md ("Reading aloud") tells.
 */
class ReadAloudTest {
    private fun heard(vararg words: Pair<String, Double>) =
        SttResult(words.joinToString(" ") { it.first }, listOf(SttSegment(0.0, 7.0, "", words.map { SttWord(it.first, prob = it.second) })), duration = 7.0)

    private fun grade(text: String, prompted: SttResult, plain: SttResult?) = ReadAloud.grade(text, prompted, plain, Lang.SL)

    private fun marks(t: ReadAloud.Take) = t.words.map { (SttSpeech.core(it.text) ?: it.text) to it.mark }
    private fun wrong(t: ReadAloud.Take) = t.words.filter { it.mark != Mark.RIGHT }.map { (SttSpeech.core(it.text) ?: it.text) to it.heard }

    // --- the clips: what they say, and Whisper's transcripts ----------------------------------------------------------

    /** A Gepard voice (male): "Zlatorog je bel gams z zlatimi rogovi. Živi visoko v gorah, pod Triglavom." */
    private val c1 = "Zlatorog je bel gams z zlatimi rogovi. Živi visoko v gorah, pod Triglavom."
    private val c1Plain = heard(
        "Zlatero" to 0.99, "Gebel" to 0.63, "Gams" to 0.85, "z" to 0.3, "Zlatimi" to 0.75, "Rogavi" to 0.7, "Živi" to 0.76, "visoko" to 0.98,
        "v" to 0.99, "gorah," to 0.94, "pod" to 0.93, "Triglavom" to 0.85,
    )
    private val c1Said = heard(
        "Zlatorog" to 0.5, "je" to 0.98, "bel" to 0.47, "gams" to 0.44, "z" to 0.57, "zlatimi" to 0.78, "rogovi." to 0.69, "Živi" to 1.0,
        "visoko" to 1.0, "v" to 1.0, "gorah," to 0.98, "pod" to 0.94, "Triglavom." to 0.76,
    )

    /** An ElevenLabs voice (the storyteller): "Lovec ga ustreli. Kjer pade kri, zrastejo rože. Zlatorog jih poje in spet oživi." */
    private val c2 = "Lovec ga ustreli. Kjer pade kri, zrastejo rože. Zlatorog jih poje in spet oživi."
    private val c2Plain = heard(
        "Lovec" to 0.75, "ga" to 1.0, "ustreli," to 0.8, "kjer" to 0.85, "pade" to 0.99, "kri," to 0.98, "zrastejo" to 0.9, "rože." to 1.0,
        "Zlatorok" to 0.95, "jih" to 1.0, "poje" to 0.98, "in" to 0.98, "speto" to 0.85, "živi." to 1.0,
    )
    private val c2Said = heard(
        "Lovec" to 0.67, "ga" to 1.0, "ustreli." to 0.79, "Kjer" to 0.84, "pade" to 0.99, "kri," to 0.97, "zrastejo" to 0.9, "rože." to 1.0,
        "Zlatorog" to 0.73, "jih" to 0.99, "poje" to 0.98, "in" to 0.96, "spet" to 0.63, "oživi." to 0.98,
    )

    /** An ElevenLabs voice (a grandfather): "Jan, veš, tu sem hodil v šolo. Pred sedemdesetimi leti. Ta klop je bila moja." */
    private val c3Plain = heard(
        "Jan," to 0.98, "veš," to 0.99, "tu" to 1.0, "sem" to 1.0, "hodil" to 1.0, "v" to 1.0, "šolo" to 1.0, "pred" to 0.39,
        "sedemdesetimi" to 0.98, "leti." to 1.0, "Ta" to 0.99, "klop" to 0.96, "je" to 1.0, "bila" to 1.0, "moja." to 1.0,
    )
    /** What Whisper wrote for clip 3 with each prompt tried: the clip as it is, whatever the prompt said. */
    private val c3Heard = heard(
        "Jan," to 0.98, "veš," to 0.99, "tu" to 1.0, "sem" to 1.0, "hodil" to 1.0, "v" to 1.0, "šolo." to 1.0, "Pred" to 0.99,
        "sedemdesetimi" to 0.98, "leti." to 1.0, "Ta" to 0.99, "klop" to 0.96, "je" to 1.0, "bila" to 1.0, "moja." to 1.0,
    )

    // --- correct reading ----------------------------------------------------------------------------------------------

    @Test fun `correct reading is right in every word, though Whisper without the prompt spelled it as Croatian or Czech`() {
        // without the prompt: "Zlatero Gebel Gams … Rogavi" (4 of 13 words wrong); graded on it alone, a third would be misread
        val t1 = grade(c1, c1Said, c1Plain)
        assertEquals(wrong(t1).toString(), 13, t1.right)
        assertTrue(t1.perfect)
        // "Zlatorok": the final g said voiceless, as Slovene says it; "speto živi" for "spet oživi": split elsewhere
        val t2 = grade(c2, c2Said, c2Plain)
        assertTrue(wrong(t2).toString(), t2.perfect)
        val t3 = grade("Jan, veš, tu sem hodil v šolo. Pred sedemdesetimi leti. Ta klop je bila moja.", c3Heard, c3Plain)
        assertTrue(t3.perfect)
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 2, 2, 2, 2, 2), t3.words.map { it.sentence })
    }

    // --- the text differs from what was read --------------------------------------------------------------------------

    @Test fun `a word misread shows what was heard`() {
        // the page says "v hribih", the clip says "v gorah": with the page as the prompt, Whisper still wrote "gorah"
        val t = grade(c1.replace("v gorah", "v hribih"), c1Said, c1Plain)
        assertEquals(listOf("hribih" to "gorah"), wrong(t))
        assertEquals(Mark.MISREAD, t.words.first { it.text.startsWith("hribih") }.mark)
        assertEquals(12, t.right)
    }

    @Test fun `words skipped are skipped, and the prompt doesn't put them in`() {
        val t = grade(c2.replace("Lovec ga ustreli.", "Lovec ga ustreli s puško."), c2Said, c2Plain)
        assertEquals(listOf("s" to null, "puško" to null), wrong(t))
        assertTrue(t.words.filter { it.mark != Mark.RIGHT }.all { it.mark == Mark.SKIPPED })
    }

    @Test fun `an ending read wrong is misread, in its ending`() {
        // the page says "hodila", "bil moj"; the clip "hodil", "bila moja"
        val t = grade("Jan, veš, tu sem hodila v šolo. Pred sedemdesetimi leti. Ta klop je bil moj.", c3Heard, c3Plain)
        assertEquals(listOf("hodila" to "hodil", "bil" to "bila", "moj" to "moja"), wrong(t))
        assertTrue(t.words.filter { it.mark == Mark.MISREAD }.all { it.ending })
    }

    @Test fun `what the prompt mended the plain transcript still heard`() {
        // the page says "bela … zlatim", the (less clear) clip "bel … zlatimi": with the page as its prompt Whisper wrote
        // "bela" (probability 0.0) and "zlatim" (0.49); without it, "Gebel" and "Zlatimi"
        val prompted = heard(
            "Zlatorog" to 0.5, "je" to 0.98, "bela" to 0.0, "gams" to 0.32, "z" to 0.55, "zlatim" to 0.49, "rogovi." to 0.43, "Živi" to 1.0,
            "visoko" to 1.0, "v" to 1.0, "gorah," to 0.98, "pod" to 0.94, "Triglavom." to 0.76,
        )
        val page = "Zlatorog je bela gams z zlatim rogovi. Živi visoko v gorah, pod Triglavom."
        val t = grade(page, prompted, c1Plain)
        assertEquals(listOf("bela" to null, "zlatim" to "Zlatimi"), wrong(t))
        assertTrue(t.words.first { it.text == "zlatim" }.ending)
        // with the prompted transcript alone, both would pass
        assertTrue(grade(page, prompted, null).perfect)
    }

    // --- the alignment: Slovene-aware ---------------------------------------------------------------------------------

    @Test fun `case, punctuation and č-š-ž don't count`() {
        val t = ReadAloud.gradePhone("»Dober dan!« je rekla Špela.", listOf("dober dan je rekla spela"), lang = Lang.SL)!!
        assertTrue(t.perfect)
        assertEquals(listOf("»Dober", "dan!«", "je", "rekla", "Špela."), t.words.map { it.text })
    }

    @Test fun `a clitic run into the next word is read, a clitic first is fine, a clitic left out is skipped`() {
        assertTrue(ReadAloud.gradePhone("Zlatorog je bel gams.", listOf("zlatorog jebel gams"), lang = Lang.SL)!!.perfect)
        assertTrue(ReadAloud.gradePhone("Se vidimo jutri.", listOf("Se vidimo jutri"), lang = Lang.SL)!!.perfect)
        val t = ReadAloud.gradePhone("Jutri se vidimo.", listOf("jutri vidimo"), lang = Lang.SL)!!
        assertEquals(listOf("se" to null), wrong(t))
        assertEquals(Mark.SKIPPED, t.words[1].mark)
    }

    @Test fun `a one-letter preposition run into its word is read, unclear`() {
        val t = ReadAloud.gradePhone("Grem v šolo.", listOf("grem šolo"), lang = Lang.SL)!!
        assertTrue(t.perfect)
        assertTrue(t.words[1].unclear)
        // … but not when the word after it wasn't read either
        val u = ReadAloud.gradePhone("Grem v šolo.", listOf("grem"), lang = Lang.SL)!!
        assertEquals(listOf(Mark.RIGHT, Mark.SKIPPED, Mark.SKIPPED), u.words.map { it.mark })
    }

    @Test fun `a number is read by the word for it`() {
        assertTrue(ReadAloud.gradePhone("Za 4 osebe potrebuješ 8 jajc.", listOf("za štiri osebe potrebuješ osem jajc"), lang = Lang.SL)!!.perfect)
        val t = ReadAloud.gradePhone("Potrebuješ 8 jajc.", listOf("potrebuješ jajc"), lang = Lang.SL)!!
        assertEquals(listOf("8" to null), wrong(t))
    }

    @Test fun `words said that the text doesn't have are extra, not wrong`() {
        val t = ReadAloud.gradePhone("Dober dan.", listOf("eee dober dan"), lang = Lang.SL)!!
        assertTrue(t.perfect)
        assertEquals(listOf("eee"), t.extra)
    }

    @Test fun `the phone's best alternative counts`() {
        val t = ReadAloud.gradePhone("Kje je pošta?", listOf("kje je posta", "kje je pošta", "tje je pošta"), lang = Lang.SL)!!
        assertTrue(t.perfect)
        assertEquals(ReadAloud.By.PHONE, t.by)
        assertNull(ReadAloud.gradePhone("Kje je pošta?", listOf(" "), lang = Lang.SL))
    }

    @Test fun `an ending slip is the stem's word with other last letters, not a final consonant said voiceless`() {
        assertTrue(ReadAloud.endingSlip("zlatim", "zlatimi"))
        assertTrue(ReadAloud.endingSlip("moja", "moj"))
        assertTrue(ReadAloud.endingSlip("trgovino", "trgovina"))
        assertFalse(ReadAloud.endingSlip("zlatorog", "zlatorok"))
        assertFalse(ReadAloud.endingSlip("rogovi", "rogavi")) // the stem
        assertFalse(ReadAloud.endingSlip("je", "ja")) // too short to tell
        assertFalse(ReadAloud.endingSlip("hiša", "hiša"))
    }

    @Test fun `unclear words are right, but Whisper was unsure of them`() {
        val t = grade("Na vrtu je čebelnjak.", heard("Na" to 0.99, "vrtu" to 0.97, "je" to 0.98, "čebelnjak." to 0.21), null)
        assertTrue(t.perfect)
        assertEquals(listOf("čebelnjak."), t.words.filter { it.unclear }.map { it.text })
    }

    // --- the parts, the score -----------------------------------------------------------------------------------------

    @Test fun `a reading is read in parts of whole sentences`() {
        assertEquals(listOf("Sveti Matija led razbija; če ga ni, ga pa naredi.«", "Dober dan."), ReadAloud.sentences("Sveti Matija led razbija; če ga ni, ga pa naredi.« Dober dan."))
        val lines = listOf("Ena dva tri štiri pet.", "Šest sedem osem devet deset.", "Enajst dvanajst trinajst.")
        assertEquals(listOf(2, 1), ReadAloud.passages(lines, max = 10).map { it.sentences.size })
        assertEquals(1, ReadAloud.passages(lines, max = 100).size)
        // a sentence longer than a part is a part of its own
        assertEquals(3, ReadAloud.passages(lines, max = 2).size)
        assertEquals(13, ReadAloud.passages(lines).single().words)
    }

    @Test fun `the score, words right, the pace, the sentences read well`() {
        val timed = SttResult(
            "Dober dan. Kako si?",
            listOf(SttSegment(0.0, 3.0, "", listOf(SttWord("Dober", 0.5, 0.9, 0.9), SttWord("dan.", 1.0, 1.4, 0.9), SttWord("Kako", 1.8, 2.2, 0.9), SttWord("si?", 2.3, 2.9, 0.9)))),
        )
        val t = grade("Dober dan. Kako si?", timed, null)
        assertEquals(2.4, t.seconds!!, 0.001)
        val s = ReadAloud.score(listOf(t))
        assertEquals(4 to 4, s.right to s.words)
        assertEquals(100, s.wpm) // 4 words in 2.4 s
        assertEquals(2 to 2, s.readWell to s.sentences)
        // one word of five wrong keeps a sentence read well (80 %); two don't
        val u = ReadAloud.gradePhone("Ena dva tri štiri pet. Šest sedem osem devet deset.", listOf("ena dva tri štiri šest sedem osem"), seconds = null, lang = Lang.SL)!!
        val su = ReadAloud.score(listOf(u))
        assertEquals(1 to 2, su.readWell to su.sentences)
        assertNull(su.wpm) // the phone didn't say how long
        assertEquals(listOf("Ena dva tri štiri pet.", "Šest sedem osem devet deset."), ReadAloud.toHearAgain(u))
        assertEquals(listOf("pet" to null, "devet" to null, "deset" to null), ReadAloud.misses(listOf(u)))
    }
}
