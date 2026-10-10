package si.lanisce.lani.audio

import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrefetchRulesTest {
    @Test fun `the job waits for Wi-Fi, storage and battery, for the charger only when the learner says so`() {
        val r = Prefetch.rules(chargingOnly = false)
        assertTrue(r.unmetered && r.storageNotLow && r.batteryNotLow)
        assertFalse(r.charging)
        assertTrue(Prefetch.rules(chargingOnly = true).charging)
        // "Prenesi zdaj": the learner asked, any network, at once
        val now = Prefetch.rules(chargingOnly = true, now = true)
        assertFalse(now.unmetered || now.charging || now.batteryNotLow)
        assertTrue(now.storageNotLow)
    }

    @Test fun `the requests carry them, a few times a day on Wi-Fi, at once on any network`() {
        val settings = AudioSettings(ApplicationProvider.getApplicationContext())
        settings.chargingOnly = true
        val p = Prefetch.periodic(settings)
        assertEquals(NetworkType.UNMETERED, p.workSpec.constraints.requiredNetworkType)
        assertTrue(p.workSpec.constraints.requiresCharging())
        assertTrue(p.workSpec.constraints.requiresStorageNotLow())
        assertTrue(p.workSpec.constraints.requiresBatteryNotLow())
        assertEquals(Prefetch.EVERY_HOURS * 3_600_000L, p.workSpec.intervalDuration)
        val now = Prefetch.once(settings, now = true)
        assertEquals(NetworkType.CONNECTED, now.workSpec.constraints.requiredNetworkType)
        assertFalse(now.workSpec.constraints.requiresCharging())
        assertTrue(now.workSpec.input.getBoolean(PrefetchWorker.NOW, false))
    }

    @Test fun `nothing to do with the cap off and no offline voice wanted`() {
        val settings = AudioSettings(ApplicationProvider.getApplicationContext())
        settings.cap = AudioCap.OFF
        settings.voiceWanted = false
        assertFalse(Prefetch.wanted(settings))
        settings.voiceWanted = true
        assertTrue(Prefetch.wanted(settings))
        settings.voiceWanted = false
        settings.cap = AudioCap.MB100
        assertTrue(Prefetch.wanted(settings))
        // the default: 250 MB
        settings.cap = AudioCap.DEFAULT
        assertEquals(AudioCap.MB250, AudioSettings(ApplicationProvider.getApplicationContext()).cap)
    }
}
