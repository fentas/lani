package si.lanisce.lani.data

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import si.lanisce.lani.audio.AudioFiles
import si.lanisce.lani.audio.PreviousClips
import java.io.File

/**
 * The trial's end on the phone: the connection's empty index forgets nothing that is here ([PreviousClips.heard]), an ETag of
 * the trial's isn't sent to the connection, and what is asked on the trial's routes is asked in a voice they know.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrialEndClipsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val files = AudioFiles(ctx.filesDir)
    private val dir: File get() = files.clips
    private val phone = MockWebServer()
    private val glas = MockWebServer()
    private val seen = java.util.Collections.synchronizedList(mutableListOf<String>())
    private var phoneIndex = "{}"
    private var glasIndex = "{}"
    private lateinit var connection: BridgeConfig
    private lateinit var trial: BridgeConfig
    private val said = java.util.Collections.synchronizedList(mutableListOf<String>())

    private class Plan(var to: BridgeConfig?, var keeps: Boolean) : VoicePlan {
        override fun voices(connection: BridgeConfig) = to ?: connection
        override fun keepsHeard(connection: BridgeConfig) = keeps && to == null
        override fun ended() = Unit
    }

    private fun answers(index: () -> String, tag: String?, name: String) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path.orEmpty()
            seen += "$name ${request.method} $path If-None-Match=${request.getHeader("If-None-Match")}"
            return when {
                path.endsWith("/voice/profiles") -> MockResponse().setBody("""{"people":{},"player":"male"}""")
                path.endsWith("/voice/index") -> MockResponse().setBody(index()).apply { tag?.let { setHeader("ETag", it) } }
                path.endsWith("/voice/say") -> {
                    said += request.body.readUtf8()
                    MockResponse().setResponseCode(204)
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
    }

    @Before fun start() {
        phone.dispatcher = answers({ phoneIndex }, null, "phone")
        glas.dispatcher = answers({ glasIndex }, "\"glas-1\"", "glas")
        phone.start()
        glas.start()
        connection = BridgeConfig(phone.url("/").toString().trimEnd('/'), "phone-token")
        trial = BridgeConfig(glas.url("/").toString().trimEnd('/') + "/v1/app/sl", "lani_0123")
        dir.mkdirs()
    }

    @After fun stop() {
        phone.shutdown()
        glas.shutdown()
        dir.listFiles()?.forEach { it.delete() }
        PreviousClips.update(dir) { emptyMap<String, Map<String, String>>() to Unit }
    }

    private fun until(what: String, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + 8_000
        while (!ok()) {
            assertTrue(what, System.currentTimeMillis() < end)
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private fun clip(name: String) = File(dir, name).writeText("clip")

    /** The index on disk as a trial left it: two lines heard, one in the index only. */
    private fun left() {
        File(dir, Clips.INDEX).writeText(
            """{"dober dan":{"female":"/voice/file/here.mp3","grandma":"/voice/file/gone.mp3"},"hvala":{"female":"/voice/file/gone2.mp3"}}""",
        )
        clip("here.mp3")
    }

    private fun refreshed(c: Clips, asked: Int) {
        c.refresh()
        until("the index read") { seen.count { "voice/index" in it } >= asked }
        // the answer's parsed and written off the main thread, then index is set on it
        repeat(40) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
    }

    @Test fun `after the end the clips on the phone stay, the rest goes, and they play`() {
        left()
        val c = Clips(ctx, MainScope(), plan = Plan(null, keeps = true))
        c.connect(connection)
        refreshed(c, 1)
        assertEquals(mapOf("dober dan" to mapOf("female" to "/voice/file/here.mp3")), c.index)
        assertNotNull("it still plays", c.onDevice("Dober dan!", listOf("female")))
        assertNull("nothing of a line never heard", c.onDevice("Hvala!", listOf("female")))
        assertEquals("the copy on disk too", c.index, Clips.parseIndex(File(dir, Clips.INDEX).readText()))
        assertTrue("not a re-voice", PreviousClips.read(dir).isEmpty())
        assertTrue(File(dir, "here.mp3").isFile)
    }

    @Test fun `the next read keeps them too`() {
        left()
        val c = Clips(ctx, MainScope(), plan = Plan(null, keeps = true))
        c.connect(connection)
        refreshed(c, 1)
        refreshed(c, 2)
        val again = Clips(ctx, MainScope(), plan = Plan(null, keeps = true))
        again.connect(connection)
        refreshed(again, 3)
        assertEquals(mapOf("dober dan" to mapOf("female" to "/voice/file/here.mp3")), again.index)
    }

    @Test fun `a node's own index takes over as it does today`() {
        left()
        phoneIndex = """{"guten tag":{"female":"/voice/file/node.mp3"}}"""
        val c = Clips(ctx, MainScope(), plan = Plan(null, keeps = false))
        c.connect(connection)
        refreshed(c, 1)
        assertEquals(mapOf("guten tag" to mapOf("female" to "/voice/file/node.mp3")), c.index)
        assertEquals(phoneIndex, File(dir, Clips.INDEX).readText())
    }

    @Test fun `an empty index on a device that never had a trial is as it was`() {
        File(dir, Clips.INDEX).writeText("""{"dober dan":{"female":"/voice/file/here.mp3"}}""")
        clip("here.mp3")
        val c = Clips(ctx, MainScope(), plan = Plan(null, keeps = false))
        c.connect(connection)
        refreshed(c, 1)
        assertTrue(c.index.isEmpty())
        assertEquals("{}", File(dir, Clips.INDEX).readText())
    }

    @Test fun `while the trial answers its index is the index`() {
        left()
        glasIndex = """{"nova vrstica":{"female":"/voice/file/new.mp3"}}"""
        val c = Clips(ctx, MainScope(), plan = Plan(trial, keeps = false))
        c.connect(connection)
        refreshed(c, 1)
        assertEquals(setOf("nova vrstica"), c.index.keys)
        assertTrue(seen.any { it.startsWith("glas GET /v1/app/sl/voice/index") })
    }

    @Test fun `the trial's ETag isn't sent to the connection, and its own is sent back to it`() {
        glasIndex = """{"nova vrstica":{"female":"/voice/file/new.mp3"}}"""
        val plan = Plan(trial, keeps = true)
        val c = Clips(ctx, MainScope(), plan = plan)
        c.connect(connection)
        refreshed(c, 1)
        refreshed(c, 2)
        plan.to = null
        refreshed(c, 3)
        val asks = seen.filter { "voice/index" in it }
        assertEquals(asks.toString(), "If-None-Match=null", asks[0].substringAfterLast(' '))
        assertEquals(asks.toString(), "If-None-Match=\"glas-1\"", asks[1].substringAfterLast(' '))
        assertTrue(asks.toString(), asks[2].startsWith("phone") && asks[2].endsWith("If-None-Match=null"))
    }

    @Test fun `the voice profiles stay on the connection during the trial`() {
        val c = Clips(ctx, MainScope(), plan = Plan(trial, keeps = false))
        c.connect(connection)
        refreshed(c, 1)
        until("the profiles read") { c.playerVoice == "male" }
        assertTrue(seen.toString(), seen.any { it.startsWith("phone GET /voice/profiles") })
        assertFalse(seen.toString(), seen.any { it.startsWith("glas") && "profiles" in it })
    }

    private fun asked(c: Clips, voices: List<String>) {
        var done = false
        MainScope().launch {
            c.load("Dober dan", voices)
            done = true
        }
        until("the line asked") { done }
    }

    @Test fun `a line of the learner's is asked in their voice on the trial's routes`() {
        val c = Clips(ctx, MainScope(), plan = Plan(trial, keeps = false))
        c.connect(connection)
        c.profilesSaid("""{"people":{},"player":"player-male"}""")
        asked(c, listOf(Clips.PLAYER))
        assertEquals(1, said.size)
        assertTrue(said[0], "\"voice\":\"player-male\"" in said[0])
    }

    @Test fun `a learner without a voice is not asked for`() {
        val c = Clips(ctx, MainScope(), plan = Plan(trial, keeps = false))
        c.connect(connection)
        c.profilesSaid("""{"people":{},"player":null}""")
        asked(c, listOf(Clips.PLAYER))
        assertTrue(said.toString(), said.isEmpty())
    }

    @Test fun `on the connection a line of the learner's is asked as player, as it always was`() {
        val c = Clips(ctx, MainScope(), plan = Plan(null, keeps = false))
        c.connect(connection)
        c.profilesSaid("""{"people":{},"player":"male"}""")
        asked(c, listOf(Clips.PLAYER))
        assertTrue(said.toString(), "\"voice\":\"player\"" in said[0])
    }
}
