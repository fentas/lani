package si.lanisce.lani.hosted

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.BridgeConfig
import si.lanisce.lani.data.BridgeEvent
import si.lanisce.lani.phone.HostFs
import si.lanisce.lani.phone.PhoneBridge
import si.lanisce.lani.phone.PhoneTransport

/**
 * The app's own bridge client ([Bridge]: data/Http.kt's client, unchanged) against the phone's bridge in-process
 * (docs/plans/05-built/hosted-mode.md): the bundle in QuickJS on the JVM, the repository as its project, a new learner's
 * data made by the onboarding's [PhoneBridge.initLearner] in a temp directory, behind [HostedRuntime.transport]. A GET,
 * the database routes (read-db and update-db in TypeScript), a write's notice, the event stream as a streaming body and
 * its end when the app lets go.
 */
class HostedTransportTest {
    companion object {
        val repo = File(System.getProperty("lani.repo") ?: "../../..").canonicalFile
        val bundle = File(System.getProperty("lani.phoneBundle") ?: "build/generated/phoneBridge/phone/bridge-phone.js")
        val json = Json { ignoreUnknownKeys = true }
        lateinit var tmp: File
        lateinit var bridge: PhoneBridge
        lateinit var app: Bridge
        lateinit var created: String
        val written = AtomicInteger()
        val dbWrites = AtomicInteger()
        const val TOKEN = "test-hosted-token-0123456789"

        fun phoneBridge(home: File, data: File, onDataWritten: (String) -> Unit = {}) = PhoneBridge(
            PhoneBridge.Config(
                projectDir = repo, dataDir = data, home = home,
                roots = listOf(HostFs.Root(home, writable = true), HostFs.Root(repo, writable = false)),
                bundle = { bundle.readText() },
                env = mapOf("LANI_RHYTHM" to "off", "LANI_VARIANTS" to "off"),
                token = TOKEN,
                onDataWritten = onDataWritten,
            ),
        )

        @BeforeClass
        @JvmStatic
        fun boot() {
            check(bundle.isFile) { "no bundle at $bundle: ./gradlew :app:phoneBridgeBundle" }
            tmp = Files.createTempDirectory("lani-hosted-test").toFile()
            bridge = phoneBridge(tmp, File(tmp, "data")) { dbWrites.incrementAndGet() }
            runBlocking {
                // the onboarding's answers, as the setup screen sends them: the data first, then the boot
                created = bridge.initLearner("""{"name":"Ana","gender":"female","target":"sl","base":"de","level":"A2","goal":20}""")
                HostedRuntime.onWrite = { written.incrementAndGet() }
                HostedRuntime.adopt(HostedRuntime.Made(bridge, 0.0, false, System.nanoTime()))
            }
            app = Bridge(BridgeConfig(PhoneTransport.BASE_URL, TOKEN))
        }

        @AfterClass
        @JvmStatic
        fun done() {
            runBlocking { HostedRuntime.stop() }
            HostedRuntime.onWrite = {}
            tmp.deleteRecursively()
        }
    }

    @Test
    fun theOnboardingMadeTheLearnersData() {
        assertEquals("""{"culture":"primorska","language":"sl"}""", created)
        val data = File(tmp, "data")
        for (f in listOf("learner-profile.json", "progress-db.json", "mistakes-db.json", "mastery-db.json", "spaced-repetition.json", "session-log.json")) {
            assertTrue(f, File(data, f).isFile)
        }
        assertTrue(File(data, "app").isDirectory)
        assertTrue(File(data, "results").isDirectory)
        val learner = json.parseToJsonElement(File(data, "learner-profile.json").readText()).jsonObject["learner"]!!.jsonObject
        assertEquals("Ana", learner["name"]!!.jsonPrimitive.content)
        assertEquals("female", learner["gender"]!!.jsonPrimitive.content)
        assertEquals("sl", learner["target_language_code"]!!.jsonPrimitive.content)
        assertEquals("de", learner["base_language_code"]!!.jsonPrimitive.content)
        assertEquals("A2", learner["current_level"]!!.jsonPrimitive.content)
        assertEquals(20, learner["daily_goal_minutes"]!!.jsonPrimitive.content.toInt())
        // a booted bridge takes no new learner (its stores read the profile when they were built)
        val again = runCatching { runBlocking { bridge.initLearner("""{"name":"Bo","target":"sl","base":"en"}""") } }
        assertTrue(again.isFailure)
    }

    @Test
    fun aDirectoryWithALearnerIsntOverwritten() = runBlocking {
        val home = File(tmp, "second").apply { mkdirs() }
        val data = File(home, "data")
        val b = phoneBridge(home, data)
        try {
            b.initLearner("""{"name":"Bo","target":"sl","base":"en"}""")
            val second = runCatching { b.initLearner("""{"name":"Cene","target":"sl","base":"en"}""") }
            assertTrue(second.isFailure)
            assertTrue(second.exceptionOrNull()!!.message.orEmpty(), second.exceptionOrNull()!!.message.orEmpty().contains("already holds a learner profile"))
            // the same language twice, or one no village speaks: refused before anything is written
            val other = phoneBridge(File(tmp, "third").apply { mkdirs() }, File(tmp, "third/data"))
            try {
                assertTrue(runCatching { other.initLearner("""{"name":"D","target":"sl","base":"sl"}""") }.isFailure)
                assertTrue(runCatching { other.initLearner("""{"name":"D","target":"xx","base":"en"}""") }.isFailure)
                assertFalse(File(tmp, "third/data/learner-profile.json").exists())
            } finally {
                other.stop()
            }
        } finally {
            b.stop()
        }
    }

    @Test
    fun theAppsClientReadsThroughTheTransport() = runBlocking {
        val d = app.dashboard()
        assertEquals("Ana", d.name)
        assertTrue(app.packs().isNotEmpty())
        assertEquals("primorska", app.culture()?.id)
        val look = app.lookup("hiša", null, null)
        assertNotNull(look)
        // a 404 comes through as the status it is (no village yet: the founding comes next)
        assertEquals(null, app.game())
    }

    @Test
    fun aWriteIsHeard() = runBlocking {
        val before = written.get()
        val dbBefore = dbWrites.get()
        app.dashboard() // a GET writes nothing
        assertEquals(before, written.get())
        val (code, body) = app.send("POST", "/words", """{"client_id":"hosted-words-0001","sl":"miza","en":"table","from":"chat"}""")
        assertEquals(body, 200, code)
        assertTrue(written.get() > before)
        // the db port's own notice (update-db wrote the deck), where a node's Python commits the data repository
        assertTrue(dbWrites.get() > dbBefore)
        val item = json.parseToJsonElement(body).jsonObject["item_id"]!!.jsonPrimitive.content
        val (rc, rb) = app.send("POST", "/reviews", """{"client_id":"hosted-review-0001","results":[{"item_id":"$item","quality":4}],"duration_minutes":2}""")
        assertEquals(rb, 200, rc)
        assertTrue(app.dashboard().wordsTracked > 0)
    }

    @Test
    fun theEventStreamStreamsAndEnds() = runBlocking {
        val opened = CompletableDeferred<Unit>()
        val first = async {
            withTimeout(15_000) { app.events(System.currentTimeMillis() * 10, onOpen = { opened.complete(Unit) }).first { it.second is BridgeEvent.Reply } }
        }
        withTimeout(10_000) { opened.await() }
        val reply = bridge.callTool("reply", """{"conversation_id":"main","text":"Dobrodošla, Ana!"}""")
        assertFalse(reply.text, reply.isError)
        val (id, ev) = first.await()
        assertNotNull(id)
        assertEquals("Dobrodošla, Ana!", (ev as BridgeEvent.Reply).text)
        // the collector let go (first{}): the stream ends on the bridge's side too, within the transport's poll
        var streams = "?"
        for (i in 0 until 40) {
            streams = json.parseToJsonElement(bridge.pending()).jsonObject["streams"]!!.jsonPrimitive.content
            if (streams == "0") break
            delay(100)
        }
        assertEquals("0", streams)
    }

    @Test
    fun withoutABridgeItsAnIoError() {
        val client = OkHttpClient.Builder().addInterceptor(PhoneTransport({ null })).build()
        try {
            client.newCall(Request.Builder().url("${PhoneTransport.BASE_URL}/health").build()).execute()
            fail("no bridge, no answer")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("isn't running"))
        }
    }

    @Test
    fun theTokenIsChecked() {
        val wrong = Bridge(BridgeConfig(PhoneTransport.BASE_URL, "wrong"))
        val r = runCatching { runBlocking { wrong.dashboard() } }
        assertTrue(r.exceptionOrNull()?.message.orEmpty(), r.exceptionOrNull()?.message.orEmpty().startsWith("HTTP 401"))
    }
}
