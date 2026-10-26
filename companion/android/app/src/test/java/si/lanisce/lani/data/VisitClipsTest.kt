package si.lanisce.lani.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * A visit's voices through the node (companion/README.md, "Voices on a visit"): the app asks its own bridge at the visited
 * town's routes (/towns/<id>/voice/…), which has the town's clips or voices the line in the town's cast voice; no
 * re-record and no prepare on a visit.
 */
class VisitClipsTest {
    private val town = "a".repeat(32)

    @Test fun `the visit's routes are the town's, through the node`() {
        val r = ClipRoutes.visit(town)
        assertEquals("/towns/$town/voice/index", r.index)
        assertEquals("/towns/$town/voice/say", r.say)
        assertEquals("/towns/$town/voice/profiles", r.profiles)
        assertNull(r.redo)
        assertNull(r.prepare)
        assertEquals("/voice/say", ClipRoutes.OWN.say)
    }

    @Test fun `a line is asked at the town's route, with the app token, and the voice it came in is taken`() {
        val node = OneAnswer(200, """{"url":"/towns/voice/file/${"b".repeat(40)}.mp3","voice":"grandma","engine":"elevenlabs","from":"host"}""")
        val got = runBlocking { ClipsApi(BridgeConfig(node.url, "app-token"), ClipRoutes.visit(town)).say("Dober dan!", "grandma") }
        assertEquals("/towns/voice/file/${"b".repeat(40)}.mp3" to "grandma", got)
        val req = node.request()
        assertEquals("POST /towns/$town/voice/say HTTP/1.1", req.line)
        assertEquals("Bearer app-token", req.headers["authorization"])
        assertTrue(req.body, req.body.contains("\"voice\":\"grandma\"") && req.body.contains("Dober dan!"))
    }

    @Test fun `nothing can say it (204), no clip and no error, so the phone speaks it`() {
        val node = OneAnswer(204, "")
        assertNull(runBlocking { ClipsApi(BridgeConfig(node.url, "t"), ClipRoutes.visit(town)).say("Lepo vas je videti.", "player") })
        assertEquals("POST /towns/$town/voice/say HTTP/1.1", node.request().line)
    }

    @Test fun `no re-record and no prepare on a visit, nothing sent`() {
        val api = ClipsApi(BridgeConfig("http://127.0.0.1:9", "t"), ClipRoutes.visit(town))
        assertNull(runBlocking { api.prepareAll(listOf(ClipAsk("Dober dan!", "grandma"))) })
        val e = runCatching { runBlocking { api.redo("Dober dan!", "grandma") } }.exceptionOrNull()
        assertTrue(e.toString(), e is IOException)
    }

    /** A node that answers one request with [code] and [body], and keeps what it got. */
    private class OneAnswer(code: Int, body: String) {
        data class Request(val line: String, val headers: Map<String, String>, val body: String)

        private val socket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val url = "http://127.0.0.1:${socket.localPort}"
        private var got: Request? = null
        private val worker = thread {
            socket.use { s ->
                s.accept().use { c ->
                    val input = BufferedReader(InputStreamReader(c.getInputStream(), Charsets.UTF_8))
                    val line = input.readLine()
                    val headers = generateSequence { input.readLine()?.takeIf { it.isNotEmpty() } }
                        .associate { h -> h.substringBefore(':').trim().lowercase() to h.substringAfter(':').trim() }
                    val length = headers["content-length"]?.toInt() ?: 0
                    val chars = CharArray(length)
                    var read = 0
                    while (read < length) {
                        val n = input.read(chars, read, length - read)
                        if (n < 0) break
                        read += n
                    }
                    got = Request(line, headers, String(chars, 0, read))
                    val bytes = body.toByteArray()
                    val status = if (code == 204) "204 No Content" else "$code OK"
                    val head = "HTTP/1.1 $status\r\nContent-Type: application/json\r\n" + (if (code == 204) "" else "Content-Length: ${bytes.size}\r\n") + "Connection: close\r\n\r\n"
                    c.getOutputStream().apply {
                        write(head.toByteArray())
                        if (code != 204) write(bytes)
                        flush()
                    }
                }
            }
        }

        fun request(): Request {
            worker.join(5_000)
            return checkNotNull(got) { "the node got no request" }
        }
    }
}

/** A visit's [Clips] and the [Speaker] on it: the node asked even before any clip is known, a line nothing can say told. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class VisitSpeakerTest {
    private val ctx get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun idle() = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

    @Test fun `a visit's clips can speak once the node can be asked, keep nothing of the learner's own, and record nothing again`() {
        val own = Clips(ctx, kotlinx.coroutines.MainScope())
        idle()
        val before = si.lanisce.lani.audio.PreviousClips.appIndex
        val visit = Clips(ctx, kotlinx.coroutines.MainScope(), visit = true)
        idle()
        // the learner's own index stays the one the phone keeps its earlier clips by
        assertTrue(si.lanisce.lani.audio.PreviousClips.appIndex === before)
        assertFalse(visit.enabled)
        visit.connect(BridgeConfig("http://127.0.0.1:9", "t"), ClipRoutes.visit("c".repeat(32)))
        assertTrue(visit.enabled)
        assertFalse(own.enabled)
        var r: ReRecord? = null
        visit.reRecord("Dober dan!", listOf(Clips.FEMALE)) { r = it }
        assertEquals(ReRecord.Offline, r)
    }

    @Test fun `on a visit, a line no voice can say is told, once per line, with its text`() {
        si.lanisce.lani.l10n.L10n.pair = si.lanisce.lani.l10n.LangPair.DEFAULT
        val s = Speaker(ctx).apply {
            voice = Voice(ctx)
            offline = object : si.lanisce.lani.audio.OfflineVoice {
                override val language: String? = null
                override suspend fun render(text: String, speed: Float): java.io.File? = null
            }
            qaNoTts = true
        }
        val silent = mutableListOf<String>()
        s.onSilent = { silent += it }
        // a visit's clips at a node nobody answers on: asked, nothing comes, no offline voice, no Android voice
        s.clips = Clips(ctx, kotlinx.coroutines.MainScope(), visit = true).apply { connect(BridgeConfig("http://127.0.0.1:9", "t"), ClipRoutes.visit("c".repeat(32))) }
        assertTrue(s.canSay)
        s.say("Dober dan, kako ste?", voiceName = "grandma", fallback = Clips.FEMALE)
        val until = System.currentTimeMillis() + 15_000
        while (silent.isEmpty() && System.currentTimeMillis() < until) {
            idle()
            Thread.sleep(50)
        }
        assertEquals(listOf("Dober dan, kako ste?"), silent)
    }
}
