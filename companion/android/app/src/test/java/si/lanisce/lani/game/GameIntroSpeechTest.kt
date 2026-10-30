package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.HintVoice
import si.lanisce.lani.data.Speaker
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.util.Locale

/**
 * The village's first words read aloud (ui/game/GameIntro, [GameIntroSpeech]) in the learner's languages: Teo's Italian
 * from German as Jan's Slovene from English, each piece in the voice of its language, the language learned played
 * though the phone has no voice for the base.
 */
class GameIntroSpeechTest {
    private val itDe = LangPair(Lang.IT, Lang.DE)

    @Test fun `an Italian learner hears Italian, explained in German`() {
        val first = GameIntroSpeech.step(0, itDe)
        assertEquals(
            listOf(
                HintPiece(L10n.text(Lang.IT, "gameIntro.myVillage"), true),
                HintPiece(L10n.text(Lang.IT, "gameIntro.arrive"), true),
                HintPiece(L10n.text(Lang.DE, "gameIntro.arrive"), false),
            ),
            first,
        )
        assertEquals("Il mio villaggio", first[0].text)
        // every piece in its language's table: the learned in Italian, the explanation in German
        for (i in GameIntroSpeech.STEPS.indices) for (p in GameIntroSpeech.step(i, itDe)) {
            val lang = if (p.target) Lang.IT else Lang.DE
            assertTrue("${p.text} is ${lang.code}", L10n.table(lang).values.contains(p.text))
        }
    }

    @Test fun `Jan's Slovene from English, as the screen shows it`() {
        val p = LangPair.DEFAULT
        assertEquals(listOf("Moja vas", L10n.text(Lang.SL, "gameIntro.arrive"), L10n.text(Lang.EN, "gameIntro.arrive")), GameIntroSpeech.step(0, p).map { it.text })
        assertEquals(listOf(true, false), GameIntroSpeech.step(1, p).map { it.target })
        assertEquals(listOf(true, true, false), GameIntroSpeech.step(2, p).map { it.target })
        assertEquals(emptyList<HintPiece>(), GameIntroSpeech.step(3, p))
    }

    @Test fun `every step says something in every pair, the learned lines first`() {
        val pairs = Lang.entries.flatMap { t -> Lang.entries.filter { it != t }.map { LangPair(t, it) } }
        for (pair in pairs) for (i in GameIntroSpeech.STEPS.indices) {
            val s = GameIntroSpeech.step(i, pair)
            assertTrue("$pair step $i", s.isNotEmpty() && s.first().target && s.all { it.text.isNotBlank() && '{' !in it.text })
        }
        // the node voices the five learned lines ahead
        assertEquals(5, GameIntroSpeech.learned(itDe).size)
        assertTrue(GameIntroSpeech.learned(itDe).all { L10n.table(Lang.IT).values.contains(it) })
    }

    @Test fun `a culture's own words for where the learner arrives, else the app's`() {
        val collio = si.lanisce.lani.game.culture.CultureIntro(
            si.lanisce.lani.game.culture.Text(mapOf("it" to "Arrivi tra le colline del Collio.", "de" to "Du kommst in die Hügel des Collio.")), "🌄",
        )
        assertEquals(listOf("Il mio villaggio", "Arrivi tra le colline del Collio.", "Du kommst in die Hügel des Collio."), GameIntroSpeech.step(0, itDe, collio).map { it.text })
        assertTrue("Arrivi tra le colline del Collio." in GameIntroSpeech.learned(itDe, collio))
        assertEquals("🌄", GameIntroSpeech.emoji(collio))
        // none: the app's own line and the mountains
        for (l in Lang.entries) assertEquals(L10n.text(l, GameIntroSpeech.ARRIVE), GameIntroSpeech.arrive(l, LangPair.DEFAULT, null))
        assertEquals("🏔️", GameIntroSpeech.emoji(null))
    }

    @Test fun `each culture names its own place - the Collio for Teo, the Gailtal, the fells, Triglav for Primorska`() {
        val cultures = si.lanisce.lani.game.culture.Cultures
        val friuli = cultures.load("friuli").manifest.intro
        val first = GameIntroSpeech.step(0, itDe, friuli)
        assertEquals(friuli?.arrive?.of(Lang.IT), first[1].text)
        assertEquals(friuli?.arrive?.of(Lang.DE), first[2].text)
        assertTrue(first[1].text, "Collio" in first[1].text && "Triglav" !in first[1].text)
        assertTrue("Triglav" !in GameIntroSpeech.arrive(Lang.DE, itDe, friuli))
        assertTrue("Gailtal" in GameIntroSpeech.arrive(Lang.DE, LangPair(Lang.DE, Lang.SL), cultures.load("kaernten").manifest.intro))
        assertTrue("fells" in GameIntroSpeech.arrive(Lang.EN, LangPair(Lang.EN, Lang.SL), cultures.load("lakeland").manifest.intro))
        // Primorska's is the line it always had (the app's own), as is a culture without one
        val primorska = cultures.load("primorska").manifest.intro
        for (l in Lang.entries) {
            assertEquals(L10n.text(l, GameIntroSpeech.ARRIVE), GameIntroSpeech.arrive(l, LangPair.DEFAULT, primorska))
            assertEquals(L10n.text(l, GameIntroSpeech.ARRIVE), GameIntroSpeech.arrive(l, LangPair.DEFAULT, null))
        }
        // every culture's in every language the app has, with its picture
        for (id in listOf("primorska", "friuli", "kaernten", "lakeland")) {
            val intro = cultures.load(id).manifest.intro
            assertTrue(id, intro != null && Lang.entries.all { intro.arrive.has(it) } && GameIntroSpeech.emoji(intro).isNotBlank())
        }
    }

    @Test fun `each piece goes to the voice of its language`() {
        // the learned language: the node's narrator clip, else the phone's voice for it; the base: the phone's voice of the base
        assertEquals(Locale.forLanguageTag("it-IT"), Speaker.locale(itDe.target))
        assertEquals(Locale.forLanguageTag("de-DE"), Speaker.locale(itDe.base))
        assertEquals(Locale.forLanguageTag("sl-SI"), Speaker.locale(LangPair.DEFAULT.target))
    }

    @Test fun `the intro plays its language learned without a voice for the base`() {
        val script = GameIntroSpeech.step(0, itDe)
        // the tablet has no German voice: the German piece got nothing
        val got = listOf(listOf("il-mio-villaggio.mp3"), listOf("arrivi.mp3"), null)
        assertEquals(listOf("il-mio-villaggio.mp3", "arrivi.mp3"), HintVoice.playable(script, got, needsBase = false))
        // a hint still needs its explanation: words alone explain nothing
        assertEquals(emptyList<String>(), HintVoice.playable(script, got, needsBase = true))
        // with every voice: everything, in order
        val all = listOf(listOf("a"), listOf("b"), listOf("c"))
        assertEquals(listOf("a", "b", "c"), HintVoice.playable(script, all, needsBase = false))
        // nothing could say the Italian (offline, no it-IT voice): the German still explains
        assertEquals(listOf("c"), HintVoice.playable(script, listOf(null, null, listOf("c")), needsBase = false))
    }
}
