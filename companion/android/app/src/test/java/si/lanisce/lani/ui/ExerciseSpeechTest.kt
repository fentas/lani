package si.lanisce.lani.ui

import android.content.Context
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.MainScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import si.lanisce.lani.audio.OfflineVoice
import si.lanisce.lani.data.BridgeConfig
import si.lanisce.lani.data.ClipRoutes
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.Speaker
import si.lanisce.lani.data.Voice
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.LangPair
import java.io.File

/**
 * Another language's exercises at home (its deck reviewed there: ui/ReviewScreen) are said in their own language
 * ([LocalSpeechLang], [Speaker.sayIn]: a linked town of it through the node, its offline voice, Android's voice for it), never
 * in the village's voices; nothing of them is recorded again. Jan's pair (Slovene from English), an Italian deck.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExerciseSpeechTest {
    @get:Rule val compose = createComposeRule()

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    /** An offline voice of [langs] (the first is the labels' when it is theirs) that keeps what it was asked to say. */
    private class FakeVoice(private vararg val langs: String) : OfflineVoice {
        val asked = mutableListOf<Pair<String, String>>()
        override val language: String? get() = langs.firstOrNull { it == L10n.pair.target.code }
        override fun speaksIn(lang: String): Boolean = lang in langs
        override suspend fun render(text: String, speed: Float): File? = language?.let { renderIn(it, text, speed) }
        override suspend fun renderIn(lang: String, text: String, speed: Float): File? {
            if (lang !in langs) return null
            asked += lang to text
            return null
        }
    }

    @Before fun pair() {
        L10n.pair = LangPair.DEFAULT
    }

    @After fun back() {
        L10n.pair = LangPair.DEFAULT
    }

    private fun speaker(offline: OfflineVoice?, foreign: (String) -> Clips? = { null }) = Speaker(ctx).apply {
        voice = Voice(ctx)
        this.offline = offline
        qaNoTts = true
        foreignClips = foreign
    }

    private fun waitFor(what: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!what() && System.currentTimeMillis() < until) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    @Test fun `an Italian exercise's 🔊 says it in Italian, by the Italian voice, never the village's`() {
        val voices = FakeVoice("sl", "it")
        val s = speaker(voices)
        compose.setContent {
            CompositionLocalProvider(LocalSpeaker provides s, LocalSpeechLang provides "it") { SpeakButton(s, "il cane", voiceName = "grandma") }
        }
        compose.onNodeWithContentDescription(Labels.LISTEN).performClick()
        waitFor { voices.asked.isNotEmpty() }
        assertEquals(listOf("it" to "il cane"), voices.asked)
    }

    @Test fun `in the labels' language the 🔊 is the village's, as before`() {
        val voices = FakeVoice("sl", "it")
        val s = speaker(voices)
        compose.setContent {
            CompositionLocalProvider(LocalSpeaker provides s, LocalSpeechLang provides null) { SpeakButton(s, "Dober dan") }
        }
        compose.onNodeWithContentDescription(Labels.LISTEN).performClick()
        waitFor { voices.asked.isNotEmpty() }
        assertEquals(listOf("sl" to "Dober dan"), voices.asked)
    }

    @Test fun `an Italian listening question plays in Italian at once, and the town of Italian is asked first`() {
        val voices = FakeVoice("it")
        var towns = 0
        val s = speaker(voices) { towns++; null }
        compose.setContent {
            CompositionLocalProvider(LocalSpeaker provides s, LocalSpeechLang provides "it") { AudioPrompt("la casa") }
        }
        waitFor { voices.asked.isNotEmpty() }
        assertEquals(listOf("it" to "la casa"), voices.asked)
        assertTrue(towns > 0)
        // the listening buttons are there, not the note that no voice is installed
        compose.onNodeWithContentDescription(Labels.SLOW).assertExists()
    }

    @Test fun `nothing of another language is recorded again, nor its tip shown`() {
        val s = speaker(null).apply { clips = Clips(ctx, MainScope()).apply { connect(BridgeConfig("http://127.0.0.1:9", "t")) } }
        assertTrue(s.canReRecord)
        assertTrue(ReRecording(s, "Dober dan", Clips.FEMALE, null, null).can)
        assertFalse(ReRecording(s, "il cane", Clips.FEMALE, null, null, foreign = true).can)
    }

    @Test fun `what can say another language - a town of it through the node, its offline voice - and what a listening question waits for`() {
        val connected = { _: String -> Clips(ctx, MainScope(), visit = true).apply { connect(BridgeConfig("http://127.0.0.1:9", "t"), ClipRoutes.visit("c".repeat(32))) } }
        // the home deck: as the speaker can say the village's language
        assertEquals(ReviewSpeech(listen = false, said = true), ReviewSpeech.of(speaker(null), null))
        assertEquals(ReviewSpeech(listen = true, said = true), ReviewSpeech.of(speaker(FakeVoice("sl")), null))
        // Italian: its offline voice on the phone, listening questions too
        assertEquals(ReviewSpeech(listen = true, said = true), ReviewSpeech.of(speaker(FakeVoice("it")), "it"))
        // only a town of Italian through the node: read aloud, but no question that is only a sound
        assertEquals(ReviewSpeech(listen = false, said = true), ReviewSpeech.of(speaker(null, connected), "it"))
        // nothing for German: silent, as before
        assertEquals(ReviewSpeech(listen = false, said = false), ReviewSpeech.of(speaker(FakeVoice("it")), "de"))
        val s = speaker(null)
        assertFalse(s.foreign(null))
        assertFalse(s.foreign("sl"))
        assertTrue(s.foreign("it"))
    }

    @Test fun `a 🔊 by another language's text - Italian reads as Italian, English doesn't`() {
        val s = speaker(null)
        assertTrue(s.looksIn("Il cane è nero e la casa è grande", "it"))
        assertFalse(s.looksIn("The dog is black and the house is big", "it"))
        // in the labels' language as before
        assertTrue(s.looksIn("Dober dan, kako ste?", null))
    }
}
