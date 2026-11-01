package si.lanisce.lani.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.lanisce.lani.audio.OfflineVoice
import si.lanisce.lani.game.HintPiece
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.io.File

/**
 * The hint's 🔊 ([HintVoice], companion/SCENES.md "The hint"): its base-language pieces by the offline voice of the base when
 * it is on the phone (Piper speaks any of its voices), its Slovene ones without a clip by the offline voice of the language
 * learned; at home and on a visit (the town's language learned, the visitor's base explaining).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HintVoiceOfflineTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    /** The phone's offline voices of [langs]: each piece it says becomes a file of its own. */
    private class FakeVoices(private vararg val langs: String) : OfflineVoice {
        val asked = mutableListOf<Pair<String, String>>()
        override val language: String? get() = langs.firstOrNull { it == L10n.pair.target.code }
        override fun speaksIn(lang: String): Boolean = lang in langs
        override suspend fun render(text: String, speed: Float): File? = language?.let { renderIn(it, text, speed) }
        override suspend fun renderIn(lang: String, text: String, speed: Float): File? {
            if (lang !in langs) return null
            asked += lang to text
            return File.createTempFile("piper-$lang-", ".wav")
        }
    }

    @After fun back() {
        L10n.pair = LangPair.DEFAULT
    }

    private fun hint(voices: OfflineVoice): HintVoice {
        val s = Speaker(ctx).apply { voice = Voice(ctx); offline = voices; qaNoTts = true }
        return HintVoice(ctx, CoroutineScope(SupervisorJob() + Dispatchers.IO), s)
    }

    @Test fun `at home the explanation is said by the English offline voice, the Slovene without a clip by the Slovene one`() {
        L10n.pair = LangPair.DEFAULT
        val voices = FakeVoices("sl", "en")
        val got = runBlocking { hint(voices).sourced(listOf(HintPiece("Here you say good day:", target = false), HintPiece("Dober dan", target = true))) }
        assertEquals(listOf("offline:en", "offline:sl"), got.map { it?.source })
        assertEquals(listOf("en" to "Here you say good day:", "sl" to "Dober dan"), voices.asked.sortedBy { it.first })
    }

    @Test fun `on a visit to a Slovene town from German, the German pieces by the German offline voice`() {
        // Teo's tablet in a Slovene town: the labels Slovene (the town's) and German (the base); no Android voice at all
        L10n.pair = LangPair(Lang.SL, Lang.DE)
        val voices = FakeVoices("de")
        val got = runBlocking { hint(voices).sourced(listOf(HintPiece("So sagst du guten Tag:", target = false), HintPiece("Dober dan", target = true))) }
        assertEquals("offline:de", got[0]?.source)
        // no clip, no Slovene voice on the tablet, no Android voice: that piece is left out (the German still plays)
        assertNull(got[1])
        assertEquals(listOf("de" to "So sagst du guten Tag:"), voices.asked)
        assertEquals(1, HintVoice.playable(listOf(HintPiece("a", false), HintPiece("b", true)), got.map { it?.files }, needsBase = true).size)
    }
}
