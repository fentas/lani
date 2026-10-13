package si.lanisce.lani.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.MainScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "🔊 Beri razlago · Read the explanations": on by default, kept on the phone, off stops what reads. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IntroVoiceSettingTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun voice(): IntroVoice {
        val speaker = Speaker(ctx)
        return IntroVoice(ctx, MainScope(), speaker).also { speaker.intro = it }
    }

    @Test fun `on by default, off kept, on again`() {
        assertTrue(voice().auto)
        voice().readAuto(false)
        assertFalse(voice().auto)
        voice().readAuto(true)
        assertTrue(voice().auto)
    }

    @Test fun `turned off, nothing reads, the speaker stops it too`() {
        val v = voice()
        v.readAuto(false)
        assertNull(v.reading)
        assertFalse(v.speaking)
        // nothing to read: the reading ends at once, nothing marked
        v.play(IntroSpeech.Script("biti/0", null, emptyList()), IntroVoice.Reader("teacher", "female", "mojca"))
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNull(v.reading)
        assertNull(v.now)
        v.readAuto(true)
    }
}
