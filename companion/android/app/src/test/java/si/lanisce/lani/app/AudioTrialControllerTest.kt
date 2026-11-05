package si.lanisce.lani.app

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.MainScope
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import si.lanisce.lani.data.AudioPlan
import si.lanisce.lani.data.AudioTrial
import java.time.Instant

/** When the trial is asked about: on this device with a voice service only, once, silently (data/AudioTrial.kt). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioTrialControllerTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val glas = MockWebServer()
    private val asks = java.util.Collections.synchronizedList(mutableListOf<RecordedRequest>())
    private var answer = MockResponse().setBody(active(14))
    private var hosted = true
    private var url = ""
    private var changed = 0

    private fun active(days: Long): String {
        val now = Instant.now()
        return """{"status":"active","started_at":"$now","ends_at":"${now.plusSeconds(days * 86_400)}","days":$days}"""
    }

    @Before fun start() {
        AudioPlan.change { AudioTrial() }
        glas.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asks += request
                return answer.clone()
            }
        }
        glas.start()
        url = glas.url("/").toString().trimEnd('/')
    }

    @After fun stop() {
        glas.shutdown()
        AudioPlan.change { AudioTrial() }
    }

    private fun controller() = AudioTrialController(ctx, MainScope(), hosted = { hosted }, glas = { url }) { changed++ }

    private fun until(what: String, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!ok()) {
            assertTrue(what, System.currentTimeMillis() < end)
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
    }

    /** Long enough for an ask that shouldn't come. */
    private fun settle() = repeat(30) {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(5)
    }

    @Test fun `on a node nothing is sent and no id is made`() {
        hosted = false
        val c = controller()
        c.check()
        c.check(again = true)
        settle()
        assertEquals(0, asks.size)
        assertEquals("", AudioPlan.trial.value.id)
        assertNull(c.card)
        assertTrue(!c.said)
    }

    @Test fun `with no voice service nothing is sent and no id is made`() {
        url = ""
        val c = controller()
        c.check(again = true)
        settle()
        assertEquals(0, asks.size)
        assertEquals("", AudioPlan.trial.value.id)
        assertNull(c.card)
    }

    @Test fun `on this device it starts, once, the voices read anew and the card waiting`() {
        val c = controller()
        c.check()
        until("started") { c.state.status == AudioTrial.ACTIVE }
        assertEquals(1, asks.size)
        val id = AudioPlan.trial.value.id
        assertTrue(id, Regex("[0-9a-f]{32}").matches(id))
        assertEquals("POST /v1/trial HTTP/1.1", asks[0].requestLine)
        assertEquals("Bearer lani_$id", asks[0].getHeader("Authorization"))
        assertEquals(1, changed)
        assertEquals(AudioTrial.Card.START, c.card)
        assertTrue(c.said)
        c.seen(AudioTrial.Card.START)
        until("closed") { c.card == null }
        c.check(again = true)
        settle()
        assertEquals("read today", 1, asks.size)
    }

    @Test fun `the card waits for this device, and leaves a node alone`() {
        val c = controller()
        c.check()
        until("started") { c.card == AudioTrial.Card.START }
        hosted = false
        assertNull(c.card)
        assertTrue(!c.said)
    }

    @Test fun `a failure changes nothing and waits, until the network is back`() {
        answer = MockResponse().setResponseCode(503)
        val c = controller()
        c.check()
        until("asked") { asks.size == 1 }
        settle()
        assertEquals(AudioTrial.NONE, AudioPlan.trial.value.status)
        assertEquals(0, changed)
        c.check()
        settle()
        assertEquals("not again soon", 1, asks.size)
        answer = MockResponse().setBody(active(14))
        c.check(again = true)
        until("started") { c.state.status == AudioTrial.ACTIVE }
    }

    @Test fun `closed says nothing, and tomorrow is asked again`() {
        answer = MockResponse().setBody("""{"status":"closed"}""")
        val c = controller()
        c.check()
        until("closed") { c.state.status == AudioTrial.CLOSED }
        assertNull(c.card)
        assertTrue(!c.said)
        c.check(again = true)
        settle()
        assertEquals(1, asks.size)
        AudioPlan.change { it.copy(askAfter = System.currentTimeMillis() - 1) }
        answer = MockResponse().setBody(active(14))
        c.check(again = true)
        until("started") { c.state.status == AudioTrial.ACTIVE }
        assertEquals("POST /v1/trial HTTP/1.1", asks[1].requestLine)
    }

    @Test fun `how it stands is read a day later, and the voice service's end ends it`() {
        val c = controller()
        c.check()
        until("started") { c.state.status == AudioTrial.ACTIVE }
        AudioPlan.change { it.copy(readAt = System.currentTimeMillis() - AudioTrial.DAY_MS) }
        val now = Instant.now()
        answer = MockResponse().setBody("""{"status":"ended","started_at":"$now","ends_at":"$now","days":14}""")
        c.check(again = true)
        until("ended") { c.state.status == AudioTrial.ENDED }
        assertEquals("GET /v1/trial HTTP/1.1", asks[1].requestLine)
        assertEquals("began, ended", 2, changed)
        assertEquals(AudioTrial.Card.END, c.card)
    }

    @Test fun `past its date it is over without a request, and nothing is sent after the end`() {
        val c = controller()
        c.check()
        until("started") { c.state.status == AudioTrial.ACTIVE }
        AudioPlan.change { it.copy(endsAt = System.currentTimeMillis() - 1) }
        c.check(again = true)
        until("ended") { c.state.status == AudioTrial.ENDED }
        settle()
        assertEquals(1, asks.size)
        assertEquals(2, changed)
    }

    @Test fun `a background job's 403 reaches the screens`() {
        val c = controller()
        c.check()
        until("started") { c.state.status == AudioTrial.ACTIVE }
        AudioPlan.ended()
        until("ended") { c.state.status == AudioTrial.ENDED }
        assertEquals(2, changed)
        assertEquals(AudioTrial.Card.END, c.card)
    }

    @Test fun `each card is closed for good`() {
        val c = controller()
        c.check()
        until("started") { c.card == AudioTrial.Card.START }
        c.seen(AudioTrial.Card.START)
        AudioPlan.ended()
        until("the end's card") { c.card == AudioTrial.Card.END }
        c.seen(AudioTrial.Card.END)
        until("closed") { c.card == null }
        assertTrue("the settings still say it", c.said)
    }
}
