package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/**
 * What the first launch says ([SetupSpeech]; docs/plans/05-built/setup-refresh.md): Teo's Italian from German on a
 * German tablet and Jan's Slovene from English, each step's title in the language learned first, then the base; the
 * welcome before a choice in the phone's language; a language's name in itself; every piece with its language, from that
 * language's table, without an emoji or an ICU brace.
 */
class SetupSpeechTest {
    private val itDe = LangPair(Lang.IT, Lang.DE)
    private val pairs = Lang.entries.flatMap { t -> Lang.entries.filter { it != t }.map { LangPair(t, it) } }

    /** [pieces] are [pair]'s language learned first, then its base, each piece saying so and from its table. */
    private fun ordered(pair: LangPair, pieces: List<HintPiece>, what: String) {
        assertTrue("$pair $what: something said", pieces.isNotEmpty())
        val firstBase = pieces.indexOfFirst { !it.target }
        assertTrue("$pair $what: the language learned first", firstBase > 0)
        assertTrue("$pair $what: then the base only", pieces.drop(firstBase).none { it.target })
        for (p in pieces) {
            assertEquals("$pair $what: ${p.text}", if (p.target) pair.target else pair.base, p.lang)
            assertFalse("$pair $what: ${p.text}", '{' in p.text || '}' in p.text)
            assertEquals("$pair $what: no emoji in ${p.text}", ReadAloud.spoken(p.text), p.text)
        }
    }

    @Test fun `the welcome before a language is chosen is the phone's language alone`() {
        val de = SetupSpeech.welcome(Lang.DE)
        assertEquals(listOf("Willkommen!", L10n.text(Lang.DE, "setup.what"), L10n.text(Lang.DE, "setup.learnWhich")), de.map { it.text })
        assertTrue(de.all { !it.target && it.lang == Lang.DE })
        assertEquals("Dobrodošli!", SetupSpeech.welcome(Lang.SL)[0].text)
        assertEquals(Lang.EN, SetupSpeech.welcome(Lang.EN)[0].lang)
    }

    @Test fun `a language's tile says its name in it, so a child hears what they chose`() {
        assertEquals(listOf(HintPiece("italiano", false, Lang.IT)), SetupSpeech.name(Lang.IT))
        assertEquals(listOf(HintPiece("slovenščina", false, Lang.SL)), SetupSpeech.name(Lang.SL))
        for (l in Lang.entries) assertEquals(l, SetupSpeech.name(l).single().lang)
    }

    @Test fun `the languages' step says the language's name, the welcome in it, then in the base`() {
        val s = SetupSpeech.languages(itDe)
        assertEquals(listOf("italiano", "Benvenuti!", L10n.text(Lang.IT, "setup.what", target = Lang.IT)), s.filter { it.target }.map { it.text })
        assertEquals(listOf("Willkommen!", L10n.text(Lang.DE, "setup.what", target = Lang.IT), L10n.text(Lang.DE, "setup.explainIn", target = Lang.IT)), s.filter { !it.target }.map { it.text })
        ordered(itDe, s, "languages")
    }

    @Test fun `the ways, the computer and typing it in, the title learned and the rest explained`() {
        val ways = SetupSpeech.ways(LangPair.DEFAULT)
        assertEquals("Kako želiš uporabljati Lani?", ways[0].text)
        assertEquals(listOf("How do you want to use Lani?", "Just this device"), ways.drop(1).take(2).map { it.text })
        assertTrue(ways.any { it.text == L10n.text(Lang.EN, "setup.computerWhat") })
        assertEquals(L10n.text(Lang.IT, "setup.computerTitle"), SetupSpeech.computer(itDe)[0].text)
        assertEquals(L10n.text(Lang.DE, "setup.scanHow"), SetupSpeech.computer(itDe).last().text)
        assertEquals(L10n.text(Lang.DE, "setup.manualHint"), SetupSpeech.manual(itDe).last().text)
    }

    @Test fun `just this device's questions name the aim, and a child's voice step says a parent sets up the tutor`() {
        val q = SetupSpeech.questions(itDe)
        assertEquals(L10n.text(Lang.IT, "hosted.title"), q[0].text)
        assertTrue(q.any { it.text == L10n.text(Lang.DE, "setup.aim") })
        assertTrue(q.any { it.text == L10n.text(Lang.DE, "hosted.goal") })
        val teo = SetupSpeech.voiceAndTutor(itDe, child = true)
        assertEquals(L10n.text(Lang.IT, "myVoice.title"), teo[0].text)
        assertEquals(L10n.text(Lang.DE, "setup.parentSets"), teo.last().text)
        assertEquals(L10n.text(Lang.EN, "hosted.tutorWhat"), SetupSpeech.voiceAndTutor(LangPair.DEFAULT, child = false).last().text)
    }

    @Test fun `paired, the about step says the aim and what it changes, then the voice`() {
        val what = L10n.text(Lang.EN, "setup.aimWhat", mapOf("words" to 2000, "aim" to "B2"), Lang.SL)
        val s = SetupSpeech.about(LangPair.DEFAULT, what)
        assertEquals("Še o tebi", s[0].text)
        assertTrue(s.map { it.text }.containsAll(listOf("Aim", what, "My voice")))
        assertTrue("2000" in what && "B2" in what)
        // an older node without the aim: its heading said, not what it changes
        assertFalse(SetupSpeech.about(LangPair.DEFAULT, null).any { it.text == what || it.text == "Aim" })
        ordered(LangPair.DEFAULT, s, "about")
    }

    @Test fun `every step says something in all 12 pairs, each piece from its language's table`() {
        for (pair in pairs) {
            val steps = mapOf(
                "languages" to SetupSpeech.languages(pair), "ways" to SetupSpeech.ways(pair), "computer" to SetupSpeech.computer(pair),
                "manual" to SetupSpeech.manual(pair), "start" to SetupSpeech.deviceStart(pair), "questions" to SetupSpeech.questions(pair),
                "voice" to SetupSpeech.voiceAndTutor(pair, child = false), "voice-child" to SetupSpeech.voiceAndTutor(pair, child = true),
                "about" to SetupSpeech.about(pair, null),
            )
            for ((what, pieces) in steps) {
                ordered(pair, pieces, what)
                // a piece the same as the one before is said once
                assertTrue("$pair $what", pieces.zipWithNext().none { (a, b) -> a.text == b.text })
            }
        }
        for (l in Lang.entries) assertEquals(3, SetupSpeech.welcome(l).size)
    }
}
