package si.lanisce.lani.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.audio.DayAudio
import si.lanisce.lani.audio.DayWant
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * A school intro's explanation as the app asks the node for it (POST /voice/say with its lang, [ClipsApi.say]) and looks
 * its clip up (the index's "en|…" key, [IntroSpeech.key]): the same text and key the node keeps it under. The keys below
 * are the ones Jan's node stored on 2026-10-14 for biti's second step, and the bridge's smoke has the fourth's.
 */
class IntroVoiceRequestTest {
    /** biti's second and fourth steps in English (companion/course/sl/units/biti.json). */
    private val three = "Three or more: **mi smo** (we are), **vi ste** (you are: several of you, or one person you say vi to), **oni so** (they are). " +
        "Three words to learn: **smo, ste, so**."
    private val no = "To say **no**, *ne* and the form melt into one word: **nisem, nisi, ni**; **nismo, niste, niso**; for two **nisva, nista**. Never *ne sem*."

    @Test fun `the explanation is sent as the node keeps it, and its key is the node's`() {
        val plain = IntroSpeech.plain(three)
        assertEquals("Three or more: mi smo (we are), vi ste (you are: several of you, or one person you say vi to), oni so (they are). Three words to learn: smo, ste, so.", plain)
        // as Jan's node keeps it (voice.db, the clip "teacher:en|three or more …")
        assertEquals("en|three or more mi smo we are vi ste you are several of you or one person you say vi to oni so they are three words to learn smo ste so", IntroSpeech.key("en", plain))
        val step4 = IntroSpeech.plain(no)
        assertEquals("To say no, ne and the form melt into one word: nisem, nisi, ni; nismo, niste, niso; for two nisva, nista. Never ne sem.", step4)
        // the bridge's smoke asks with this text and finds it under this key in GET /voice/index
        assertEquals("en|to say no ne and the form melt into one word nisem nisi ni nismo niste niso for two nisva nista never ne sem", IntroSpeech.key("en", step4))
    }

    @Test fun `POST voice say carries the text, the teacher's voice and the lang, with the app token`() {
        val plain = IntroSpeech.plain(no)
        val node = OneRequest(200, """{"url":"/voice/file/abc.mp3","voice":"teacher","engine":"elevenlabs"}""")
        val got = runBlocking { ClipsApi(BridgeConfig(node.url, "app-token")).say(plain, "teacher", "en") }
        assertEquals("/voice/file/abc.mp3" to "teacher", got)
        val r = node.request()
        assertEquals("POST /voice/say HTTP/1.1", r.line)
        assertEquals("Bearer app-token", r.headers["authorization"])
        assertTrue(r.headers["content-type"].orEmpty(), r.headers["content-type"].orEmpty().startsWith("application/json"))
        // the bridge's sayIn: text, voice, lang (two small letters); nothing else
        val body = Json.parseToJsonElement(r.body) as JsonObject
        assertEquals(mapOf("text" to JsonPrimitive(plain), "voice" to JsonPrimitive("teacher"), "lang" to JsonPrimitive("en")), body.toMap())
        // a line has no lang
        assertEquals(setOf("text", "voice"), ClipsApi.sayBody("nisem, nisi, ni", "teacher").keys)
    }

    @Test fun `the node can't now (204), no clip and no error`() {
        val node = OneRequest(204, "")
        assertNull(runBlocking { ClipsApi(BridgeConfig(node.url, "t")).say(IntroSpeech.plain(three), "teacher", "en") })
        assertEquals("POST /voice/say HTTP/1.1", node.request().line)
    }

    @Test fun `the clip is found under the node's key in its index, in her voice, as the day's audio finds it too`() {
        val plain = IntroSpeech.plain(three)
        // GET /voice/index as Jan's node answers it: the explanation beside a Slovene line of the same words
        val index = Clips.parseIndex(
            """{"en|three or more mi smo we are vi ste you are several of you or one person you say vi to oni so they are three words to learn smo ste so": {"teacher": "/voice/file/e1.mp3"},
               "mi smo": {"teacher": "/voice/file/l1.mp3"}}""",
        )
        assertEquals(listOf("/voice/file/e1.mp3"), Clips.resolve(index, plain, "teacher", lang = "en"))
        assertEquals("teacher" to listOf("/voice/file/e1.mp3"), Clips.resolveFirst(index, plain, listOf("teacher", "female"), lang = "en"))
        // not in another voice, nor as a line
        assertNull(Clips.resolve(index, plain, "female", lang = "en"))
        assertNull(Clips.resolve(index, plain, "teacher"))
        // the day's audio (its prepare) keys it the same
        val want = DayWant(plain, listOf("teacher", "female"), "school:biti", "en")
        assertEquals(IntroSpeech.key("en", plain), DayAudio.storeKey(want))
        assertEquals(emptyList<String>(), DayAudio.missing(index, want))
    }

    /** A node answering one request with [code] and [body] on a port of its own; [request] is what it was sent. */
    private class OneRequest(code: Int, body: String) {
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
