package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.audio.OfflineVoice
import si.lanisce.lani.data.Speaker.Fallback as F
import java.io.File

class SpeakerFallbackTest {
    @Test fun `without the wanted clip, another of their voices on the phone, then Piper, then the phone's TTS`() {
        // the order of the layers (companion/README.md, "Voice"): clips on the phone first, then the offline voice
        assertEquals(F.CLIP, Speaker.fallback(clip = true, offline = "sl", target = "sl", tts = true))
        assertEquals(F.OFFLINE, Speaker.fallback(clip = false, offline = "sl", target = "sl", tts = true))
        assertEquals(F.TTS, Speaker.fallback(clip = false, offline = null, target = "sl", tts = true))
        assertEquals(F.NONE, Speaker.fallback(clip = false, offline = null, target = "sl", tts = false))
        // Piper speaks only its own language: an Italian town's lines on a visit keep Android's TTS
        assertEquals(F.TTS, Speaker.fallback(clip = false, offline = "sl", target = "it", tts = true))
        assertEquals(F.OFFLINE, Speaker.fallback(clip = false, offline = "sl", target = "sl", tts = false))
    }

    @Test fun `a fake offline voice speaks only its own language`() {
        val piper = object : OfflineVoice {
            override val language: String? = "sl"
            override suspend fun render(text: String, speed: Float): File? = null
        }
        assertTrue(piper.speaks("sl"))
        assertFalse(piper.speaks("it"))
        val none = object : OfflineVoice {
            override val language: String? = null
            override suspend fun render(text: String, speed: Float): File? = null
        }
        assertFalse(none.speaks("sl"))
    }

    @Test fun `the voices for a line, their own voice first, their archetype's played offline`() {
        val own = VoiceProfile(speaker = "@micka", archetype = "grandma", status = "own")
        val v = Speaker.voices("grandma", "female", own)
        assertEquals(listOf("@micka"), v.voices)
        assertEquals(listOf("grandma", "female"), v.shared)
        assertEquals(listOf("@micka", "grandma", "female"), v.all)
        val shared = VoiceProfile(speaker = "grandma", archetype = "grandma", pitch = 1.05f)
        assertEquals(listOf("grandma", "female"), Speaker.voices("female", null, shared).voices)
        assertEquals(listOf("young-man", "male", "female"), Speaker.voices("young-man", "male").all)
        assertEquals(listOf("female"), Speaker.voices().all)
    }
}

/** [Speaker.say] with a fake offline voice: what it asks of it, in which order, and when not. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class SpeakerOfflineTest {
    private class FakeVoice(override val language: String?) : OfflineVoice {
        val asked = mutableListOf<Pair<String, Float>>()
        override suspend fun render(text: String, speed: Float): File? {
            asked += text to speed
            return null
        }
    }

    private fun speaker(fake: FakeVoice): Speaker {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        return Speaker(ctx).apply {
            voice = Voice(ctx)
            offline = fake
        }
    }

    private fun idle() = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

    @Test fun `no clip and no node, the offline voice is asked, 🐢 slows the speech itself`() {
        si.lanisce.lani.l10n.L10n.pair = si.lanisce.lani.l10n.LangPair.DEFAULT
        val fake = FakeVoice(si.lanisce.lani.l10n.L10n.pair.target.code)
        val s = speaker(fake)
        assertTrue(s.canSay) // the offline voice alone can speak
        s.say("Dober dan!")
        idle()
        s.say("Lahko noč!", slow = true)
        idle()
        assertEquals(listOf("Dober dan!" to 1f, "Lahko noč!" to si.lanisce.lani.audio.Piper.SLOW), fake.asked)
    }

    @Test fun `an offline voice of another language isn't asked`() {
        si.lanisce.lani.l10n.L10n.pair = si.lanisce.lani.l10n.LangPair.DEFAULT
        val fake = FakeVoice("it")
        val s = speaker(fake)
        s.say("Dober dan!")
        idle()
        assertTrue(fake.asked.isEmpty())
    }

    @Test fun `a family recording or a clip on the phone plays first, the offline voice isn't asked`() {
        si.lanisce.lani.l10n.L10n.pair = si.lanisce.lani.l10n.LangPair.DEFAULT
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val fake = FakeVoice("sl")
        val s = speaker(fake)
        val clips = Clips(ctx, kotlinx.coroutines.MainScope())
        s.clips = clips
        idle()
        // a clip of the line on the phone (the index says so and its file is there)
        val f = clips.file("/voice/file/abc.mp3").apply { parentFile?.mkdirs(); writeBytes(ByteArray(64)) }
        org.robolectric.shadows.ShadowMediaPlayer.addMediaInfo(
            org.robolectric.shadows.util.DataSource.toDataSource(f.path), org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(800, 0),
        )
        val field = Clips::class.java.getDeclaredField("index\$delegate").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (field.get(clips) as androidx.compose.runtime.MutableState<ClipIndex>).value = mapOf("dober dan" to mapOf(Clips.FEMALE to "/voice/file/abc.mp3"))
        assertTrue(clips.onDevice("Dober dan!", listOf(Clips.FEMALE)) != null)
        s.say("Dober dan!")
        idle()
        assertTrue(fake.asked.isEmpty())
        f.delete()
    }
}
