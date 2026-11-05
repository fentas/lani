package si.lanisce.lani.data

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * A joined line that can't be had: the voice service keeps no finished file (`/voice/line/<id>.mp3` is a 404 there), so a join that
 * fails on the phone ends with no clip, which is the phone's voice ([Speaker.say], `otherwise`): no error, and no asking again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LineFallbackTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val glas = MockWebServer()
    private val id = "a".repeat(40)
    private val seen = java.util.Collections.synchronizedList(mutableListOf<String>())
    private var manifest: MockResponse = MockResponse().setResponseCode(404)
    private val text = "Hvala, Jan. Ti si zlat."

    private class Plan(val to: BridgeConfig) : VoicePlan {
        override fun voices(connection: BridgeConfig) = to
        override fun keepsHeard(connection: BridgeConfig) = false
        override fun ended() = Unit
    }

    @Before fun start() {
        glas.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                seen += path
                return when {
                    path.endsWith("/voice/say") -> MockResponse().setBody("""{"url":"/voice/line/$id.mp3","voice":"grandma","engine":"glas"}""")
                    path.endsWith("/voice/line/$id.json") -> manifest
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        glas.start()
    }

    @After fun stop() {
        glas.shutdown()
        File(ctx.filesDir, "voice-clips").deleteRecursively()
    }

    /** What [Clips.prepare] hands on for the line, once it has made up its mind; "none": the phone's voice says it. */
    private fun asked(c: Clips): List<File>? {
        var got: List<File>? = emptyList()
        var done = false
        c.prepare(text, listOf("grandma"), waitMs = 5_000) { got = it; done = true }
        val end = System.currentTimeMillis() + 8_000
        while (!done && System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("no answer", done)
        return got
    }

    private fun clips(): Clips {
        val trial = BridgeConfig(glas.url("/").toString().trimEnd('/') + "/v1/app/sl", "lani_0123")
        return Clips(ctx, MainScope(), plan = Plan(trial)).also { it.connect(BridgeConfig("http://127.0.0.1:9", "phone-token")) }
    }

    @Test fun `no manifest and no finished file leave no clip and no error, and the phone's voice from then on`() {
        val c = clips()
        assertEquals(null, asked(c))
        assertTrue(seen.any { it.endsWith("/voice/line/$id.json") })
        assertTrue("the finished file was tried", seen.any { it.endsWith("/voice/line/$id.mp3") })
        assertTrue("not asked again at every play", c.missedRecently(text, listOf("grandma")))
    }

    @Test fun `a manifest whose part can't be had ends the same`() {
        manifest = MockResponse().setBody("""{"v":1,"id":"$id","text":"x","voice":"grandma","parts":[{"kind":"name","url":"/voice/file/${"b".repeat(40)}.mp3","ms":500,"gain":0}]}""")
        val c = clips()
        assertEquals(null, asked(c))
        assertTrue(c.missedRecently(text, listOf("grandma")))
    }

    @Test fun `a part the phone can't decode ends the same`() {
        manifest = MockResponse().setBody("""{"v":1,"id":"$id","text":"x","voice":"grandma","parts":[{"kind":"name","url":"/voice/file/${"b".repeat(40)}.mp3","ms":500,"gain":0}]}""")
        val inner = glas.dispatcher
        glas.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().endsWith("${"b".repeat(40)}.mp3")) MockResponse().setBody("not sound") else inner.dispatch(request)
        }
        val c = clips()
        assertEquals(null, asked(c))
        assertFalse("nothing kept under the line's name", File(ctx.filesDir, "voice-clips/$id.mp3").exists())
    }
}
