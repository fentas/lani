package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.phone.PhoneTransport

/** Who answers the voice routes: data/AudioPlan.kt, the one place. */
class AudioPlanTest {
    private val now = 1_000_000L
    private val glas = "https://glas.example"
    private val phone = BridgeConfig(PhoneTransport.BASE_URL, "phone-token")
    private val node = BridgeConfig("https://node.example", "app-token")
    private val id = "0123456789abcdef0123456789abcdef"
    private val active = AudioTrial(id = id, status = AudioTrial.ACTIVE, startedAt = now - 1, endsAt = now + 1, days = 14)

    private fun voices(c: BridgeConfig, t: AudioTrial = active, url: String = glas, lang: String = "sl", at: Long = now) = AudioPlan.voices(c, t, url, lang, at)
    private fun keeps(c: BridgeConfig, t: AudioTrial, url: String = glas, at: Long = now) = AudioPlan.keepsHeard(c, t, url, "sl", at)

    @Test fun `a node's connection is the node's, whatever the trial`() {
        assertEquals(node, voices(node))
        assertEquals(node, voices(node, active.copy(status = AudioTrial.ENDED)))
        assertEquals(node, voices(node, AudioTrial()))
    }

    @Test fun `no voice service, no trial`() {
        assertEquals(phone, voices(phone, url = ""))
        assertEquals(phone, voices(phone, AudioTrial(), url = ""))
    }

    @Test fun `while it lasts on this device the trial answers, in the language learned`() {
        assertEquals(BridgeConfig("https://glas.example/v1/app/sl", "lani_$id"), voices(phone))
        assertEquals(BridgeConfig("https://glas.example/v1/app/it", "lani_$id"), voices(phone, lang = "it"))
        assertEquals("a slash at the end of the build's address", BridgeConfig("https://glas.example/v1/app/de", "lani_$id"), voices(phone, url = "$glas/", lang = "de"))
    }

    @Test fun `ended, closed, none or past its date, the connection answers`() {
        assertEquals(phone, voices(phone, active.copy(status = AudioTrial.ENDED)))
        assertEquals(phone, voices(phone, active.copy(status = AudioTrial.CLOSED)))
        assertEquals(phone, voices(phone, AudioTrial()))
        assertEquals(phone, voices(phone, at = active.endsAt))
    }

    @Test fun `no id, no trial`() {
        assertEquals(phone, voices(phone, active.copy(id = "")))
    }

    @Test fun `the plan's own state is empty until a device has one, so a test sees the connection`() {
        assertEquals(phone, AudioPlan.voices(phone))
        assertEquals(node, AudioPlan.voices(node))
    }

    @Test fun `the clips stay on this device once a trial left some`() {
        assertTrue(keeps(phone, active.copy(status = AudioTrial.ENDED)))
        assertTrue("past its date, not told yet", keeps(phone, active, at = active.endsAt))
        assertTrue("the build has no voice service any more", keeps(phone, active, url = ""))
    }

    @Test fun `while the trial answers, its index is the index`() {
        assertFalse(keeps(phone, active))
    }

    @Test fun `a device that never had one is as it was, and a node's index takes over as it did`() {
        assertFalse(keeps(phone, AudioTrial()))
        assertFalse(keeps(phone, AudioTrial(status = AudioTrial.CLOSED)))
        assertFalse(keeps(node, active.copy(status = AudioTrial.ENDED)))
    }
}
