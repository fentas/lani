package si.lanisce.lani.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.BridgeConfig
import si.lanisce.lani.data.BridgeEvent
import si.lanisce.lani.data.Prefs
import si.lanisce.lani.data.Words
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A word the dictionary doesn't know, added from its card (companion/README.md "Word lookup", "A word the dictionary
 * doesn't know"): the card offers what it may be instead, "➕ Add to my words" asks the node (POST /words/requests) and
 * stays open waiting for the tutor's meaning, which fills it in (word_request_done, then the tutor's entry added); closed
 * meanwhile, a quiet notice. Offline the request waits in the outbox; a node from before the route gets a chat message.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WordsRequestTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val notices = Notices()

    /** The node: the lookup before and after the tutor's meaning, the requests it took, what else it was sent. */
    private inner class Node : Dispatcher() {
        val server = MockWebServer().also { it.dispatcher = this }
        @Volatile var glossed = false
        @Volatile var requested = false
        @Volatile var oldNode = false
        val posts = CopyOnWriteArrayList<Pair<String, String>>()

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.requestUrl!!.encodedPath
            val ok = { body: String -> MockResponse().setResponseCode(200).setBody(body) }
            return when {
                path == "/lookup" && request.requestUrl!!.queryParameter("w") == "zlica" ->
                    ok("""{"word":"zlica","entries":[],"attribution":null,"suggestions":[{"word":"žlica","lemma":"žlica","pos":"noun","gloss":["spoon"],"gloss_lang":"en"}]}""")
                path == "/lookup" && request.requestUrl!!.queryParameter("w") == "žlica" ->
                    ok("""{"word":"žlica","entries":[{"lemma":"žlica","pos":"noun","gloss":["spoon"],"source":"wiktionary","item_id":"vocab_word_zlica","known":false}]}""")
                path == "/lookup" && glossed ->
                    ok("""{"word":"vretenom","entries":[{"lemma":"vreteno","pos":"noun","gloss":["spindle"],"gloss_lang":"en","gloss_via":"claude","source":"tutor","item_id":"vocab_word_vreteno","known":true}]}""")
                path == "/lookup" -> ok("""{"word":"vretenom","entries":[],"attribution":null${if (requested) ",\"requested\":true" else ""}}""")
                path == "/words" && request.method == "GET" -> ok("""{"from":["scene","roleplay","villager","chat","reading","search"]}""")
                path == "/words/requests" && request.method == "GET" ->
                    ok(if (requested && !glossed) """{"requests":[{"word":"vretenom","language":"sl","base":"en","line":"Babica prede z vretenom.","at":"2026-10-26T10:00:00Z"}]}""" else """{"requests":[]}""")
                path == "/words/requests" && oldNode -> MockResponse().setResponseCode(404).setBody("""{"error":"not found"}""")
                request.method == "POST" -> {
                    posts += path to request.body.readUtf8()
                    if (path == "/words/requests") requested = true
                    ok(if (path == "/words/requests") """{"state":"waiting","word":"vretenom","id":"x"}""" else "{}")
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
    }

    private val node = Node()
    private var online = true
    private val bridge by lazy { Bridge(BridgeConfig(node.server.url("/").toString().trimEnd('/'), "t")) }
    private val sync by lazy { SyncController(context, scope, { if (online) bridge else null }, notices) {} }
    private val words by lazy {
        val dir = Files.createTempDirectory("chat").toFile()
        val chat = ChatController(scope, Prefs(context), sync, notices, ChatStore(File(dir, "chat"), File(dir, "chat.json")), clock = { 1_000L })
        WordsController(scope, { if (online) bridge else null }, sync, chat, notices)
    }

    @After fun done() {
        scope.cancel()
        node.server.shutdown()
    }

    private fun until(what: String, ms: Long = 5_000, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (!ok()) {
            if (System.currentTimeMillis() > end) throw AssertionError("timed out: $what (card ${words.card})")
            Thread.sleep(20)
        }
    }

    private val line = "Babica prede z vretenom."
    private fun openMissing() {
        words.open(WordQuery("vretenom,", line, "Grandma spins with a spindle.", Words.SCENE))
        until("not found") { words.card?.state is WordState.NotFound }
    }

    @Test fun `add on a missing word asks the node, waits on the card, and fills it in when the meaning comes`() {
        openMissing()
        words.request()
        until("waiting") { words.card?.request == Requesting.WAITING }
        val (path, body) = node.posts.single()
        assertEquals("/words/requests", path)
        val o = Json.parseToJsonElement(body).jsonObject
        assertEquals("vretenom", o["word"]!!.jsonPrimitive.content) // the punctuation around it left out
        assertEquals(line, o["line"]!!.jsonPrimitive.content)
        assertEquals("Grandma spins with a spindle.", o["en"]!!.jsonPrimitive.content)
        assertEquals("scene", o["from"]!!.jsonPrimitive.content)
        assertEquals("en", o["base"]!!.jsonPrimitive.content) // Jan's pair: sl · en
        assertFalse("language" in o) // the village's
        assertTrue(o["client_id"]!!.jsonPrimitive.content.length >= 8)
        // a second tap does nothing
        words.request()
        Thread.sleep(100)
        assertEquals(1, node.posts.size)

        // the tutor's meaning: the node added it, the card looks again and shows it added
        node.glossed = true
        words.onWordRequestDone(BridgeEvent.WordRequestDone("vretenom", "vreteno", listOf("spindle"), "vocab_word_vreteno", added = true))
        until("found") { words.card?.state is WordState.Found }
        val c = words.card!!
        assertEquals(Requesting.DONE, c.request)
        val e = (c.state as WordState.Found).lookup.entries.single()
        assertEquals("vreteno", e.lemma)
        assertEquals(Adding.ADDED, c.added[e.key])
        assertNull("no notice while the card shows it", notices.banner)
    }

    @Test fun `the card closed meanwhile - a quiet notice says the word is among theirs, with its meaning`() {
        openMissing()
        words.request()
        until("waiting") { words.card?.request == Requesting.WAITING }
        words.close()
        words.onWordRequestDone(BridgeEvent.WordRequestDone("vretenom", "vreteno", listOf("spindle", "distaff"), "vocab_word_vreteno", added = true))
        val banner = notices.banner.orEmpty()
        assertTrue(banner, banner.contains("«vreteno»") && banner.contains("spindle, distaff") && banner.contains("Zdaj med tvojimi besedami · Now in your words"))
        // a name (not added): nothing to say
        notices.banner = null
        words.onWordRequestDone(BridgeEvent.WordRequestDone("Zalka", "Zalka", listOf("a name"), null, added = false))
        assertNull(notices.banner)
    }

    @Test fun `offline the request waits in the outbox, is listed as waiting, and goes once the node is back`() = runBlocking {
        openMissing()
        online = false
        words.request()
        until("queued") { words.card?.request == Requesting.QUEUED }
        assertTrue(node.posts.isEmpty())
        val queued = sync.queued("/words/requests").single()
        assertEquals("vretenom", Json.parseToJsonElement(queued.body).jsonObject["word"]!!.jsonPrimitive.content)
        words.loadWaiting()
        assertEquals(listOf("vretenom" to true), words.waiting.map { it.word to it.queued })
        // online again: the outbox sends it (as it does when the network comes back), the node keeps it waiting
        online = true
        sync.flush()
        assertEquals("/words/requests", node.posts.single().first)
        words.loadWaiting()
        assertEquals(listOf("vretenom" to false), words.waiting.map { it.word to it.queued })
        // the card opened again: the node says it's asked for already, and Add isn't offered twice
        words.open(WordQuery("vretenom", line, null, Words.SCENE))
        until("not found") { words.card?.state is WordState.NotFound }
        assertTrue((words.card!!.state as WordState.NotFound).lookup.requested)
        words.request()
        Thread.sleep(100)
        assertEquals(1, node.posts.size)
    }

    @Test fun `a node from before requests gets it as a chat message to the tutor, with lookup add`() {
        node.oldNode = true
        openMissing()
        words.request()
        until("sent the old way") { node.posts.any { it.first == "/message" } }
        val o = Json.parseToJsonElement(node.posts.single { it.first == "/message" }.second).jsonObject
        assertTrue(o["text"]!!.jsonPrimitive.content.startsWith("➕ «vretenom» — Dodaj med moje besede · Add to my words"))
        val lookup = o["data"]!!.jsonObject["lookup"]!!.jsonObject
        assertEquals("vretenom", lookup["word"]!!.jsonPrimitive.content)
        assertTrue(lookup["add"]!!.jsonPrimitive.boolean)
    }

    @Test fun `a suggestion opens its own card in the same line`() {
        words.open(WordQuery("zlica", "Daj mi zlico.", null, Words.SCENE))
        until("not found") { words.card?.state is WordState.NotFound }
        val s = (words.card!!.state as WordState.NotFound).lookup.suggestions.single()
        assertEquals("žlica", s.word)
        words.openSuggestion(s)
        until("found") { words.card?.state is WordState.Found }
        assertEquals("žlica", words.card!!.query.word)
        assertEquals("Daj mi zlico.", words.card!!.query.line)
    }
}
