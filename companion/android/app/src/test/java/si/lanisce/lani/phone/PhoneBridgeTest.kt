package si.lanisce.lani.phone

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * The bridge in QuickJS on the JVM (quickjs-kt's JVM build: the same QuickJS as on the phone), the real bundle
 * (app/build.gradle.kts phoneBridgeBundle) over a learner data directory made from data-examples' templates, the
 * repository as its project (read only): the app's routes, the tutor's channel and tools as a node's bridge serves them
 * (docs/plans/05-built/phone-runtime.md). The node's bridge on the same data: PhoneBridgeNodeTest.
 */
class PhoneBridgeTest {
    companion object {
        val repo = File(System.getProperty("lani.repo") ?: "../../..").canonicalFile
        val bundle = File(System.getProperty("lani.phoneBundle") ?: "build/generated/phoneBridge/phone/bridge-phone.js")
        lateinit var tmp: File
        lateinit var data: File
        lateinit var bridge: PhoneBridge
        val logs = mutableListOf<String>()
        val json = Json { ignoreUnknownKeys = true }

        /** A data directory as a new learner's: the six databases from data-examples' templates. */
        fun learnerData(into: File): File {
            into.mkdirs()
            File(repo, "data-examples").listFiles { f -> f.name.endsWith("-template.json") }!!.forEach { it.copyTo(File(into, it.name.replace("-template", ""))) }
            return into
        }

        fun bridgeOver(home: File, data: File, env: Map<String, String> = emptyMap(), bytecode: File? = null) = PhoneBridge(
            PhoneBridge.Config(
                projectDir = repo,
                dataDir = data,
                home = home,
                roots = listOf(HostFs.Root(home, writable = true), HostFs.Root(repo, writable = false)),
                bundle = { bundle.readText() },
                // no routine timers in a test (the rhythm's minute), no scene variants asked for
                env = mapOf("LANI_RHYTHM" to "off", "LANI_VARIANTS" to "off") + env,
                log = { level, text -> synchronized(logs) { logs += "$level $text" } },
                bytecodeDir = bytecode,
            ),
        )

        @BeforeClass
        @JvmStatic
        fun boot() {
            check(bundle.isFile) { "no bundle at $bundle: ./gradlew :app:phoneBridgeBundle" }
            tmp = Files.createTempDirectory("lani-phone-test").toFile()
            data = learnerData(File(tmp, "data"))
            bridge = bridgeOver(tmp, data)
            runBlocking { bridge.start() }
        }

        @AfterClass
        @JvmStatic
        fun done() {
            bridge.stop()
            // what the bridge said (its log, as the app's logcat has it): in the test's report
            synchronized(logs) { logs.forEach { println("bridge: ${it.take(600)}") } }
            tmp.deleteRecursively()
        }
    }

    private fun get(path: String) = runBlocking { bridge.handle(PhoneBridge.Request.get(path)) }
    private fun send(method: String, path: String, body: String) = runBlocking { bridge.handle(PhoneBridge.Request.json(method, path, body)) }
    private fun PhoneBridge.Response.json(): JsonElement = json.parseToJsonElement(text)
    private fun ok(r: PhoneBridge.Response): PhoneBridge.Response {
        assertEquals("${r.status}: ${r.text.take(500)}", 200, r.status)
        return r
    }

    @Test
    fun boots() {
        val s = json.parseToJsonElement(runBlocking { bridge.start() }.summary).jsonObject
        assertEquals("primorska", s["culture"]!!.jsonPrimitive.content)
        assertEquals("sl", s["language"]!!.jsonPrimitive.content)
        // the node's features, the tutor session's (features/tutor.ts: the app's own tutor's routes) among them
        assertEquals(38, s["features"]!!.jsonArray.size)
        assertTrue(s["features"]!!.jsonArray.any { it.jsonPrimitive.content == "tutor" })
        // a child's links that wait for a parent (features/town-requests.ts): a phone's town takes them too
        assertTrue(s["features"]!!.jsonArray.any { it.jsonPrimitive.content == "town-requests" })
        // progress between linked towns (features/progress.ts) runs on the phone too
        assertTrue(s["features"]!!.jsonArray.any { it.jsonPrimitive.content == "progress" })
        // a visited town's voices (features/visit-voices.ts): its clips handed on through the relay, none made here
        assertTrue(s["features"]!!.jsonArray.any { it.jsonPrimitive.content == "visit-voices" })
        assertTrue(s["tools"]!!.jsonPrimitive.content.toInt() > 40)
        assertEquals("""{"ok":true}""", ok(get("/health")).text)
    }

    @Test
    fun everyGetRouteAnswers() {
        // the app's GET routes (the event stream aside): none fails in the phone's runtime (a polyfill or a host call
        // missing would be a 500)
        val routes = listOf(
            "/health", "/arrivals", "/cards", "/drills", "/events/backlog", "/grammar", "/modules", "/readings", "/scenarios",
            "/stories", "/stt/status", "/villagers", "/voice/index", "/voice/offline", "/voice/profiles", "/voice/status", "/words",
            "/course", "/culture", "/cultures", "/game", "/lexicon/status", "/packs", "/scenes", "/app/latest", "/towns",
            "/towns/guests", "/towns/questions", "/towns/friendship", "/audio/index", "/family/api/settings", "/family/api/words",
            "/family/api/challenges", "/counting", "/forms-cards", "/level", "/cases",
        )
        val failed = routes.map { it to get(it) }.filter { (_, r) -> r.status >= 500 }
        assertEquals(failed.joinToString("\n") { (p, r) -> "$p: ${r.status} ${r.text.take(200)}" }, 0, failed.size)
    }

    @Test
    fun theTokenIsTheApps() {
        val r = runBlocking { bridge.handle(PhoneBridge.Request("GET", "/packs", listOf("authorization" to "Bearer wrong"))) }
        assertEquals(401, r.status)
    }

    @Test
    fun packs() {
        val packs = ok(get("/packs")).json().jsonArray
        assertTrue("packs: ${packs.size}", packs.size > 10)
        val id = packs.first().jsonObject["id"]!!.jsonPrimitive.content
        val pack = ok(get("/packs/$id")).json().jsonObject
        assertEquals(id, pack["id"]!!.jsonPrimitive.content)
        assertTrue(pack["words"]!!.jsonArray.isNotEmpty())
    }

    @Test
    fun learnAPack() {
        // the learner-data scripts in TypeScript (src/db): the pack's words become review cards (update-db)
        val pack = ok(get("/packs")).json().jsonArray.first().jsonObject
        val id = pack["id"]!!.jsonPrimitive.content
        val word = ok(get("/packs/$id")).json().jsonObject["words"]!!.jsonArray.first().jsonObject["id"]!!.jsonPrimitive.content
        ok(send("POST", "/packs/$id/learn", """{"client_id":"test-learn-00001","results":[{"word_id":"$word","quality":4}],"duration_minutes":2}"""))
        val items = ok(get("/state")).json().jsonObject["databases"]!!.jsonObject["spaced_repetition"]!!.jsonObject["items"]!!.jsonObject
        assertTrue(items.keys.toString(), items.keys.any { it.contains(word.replace('-', '_')) || it.contains(word) })
    }

    @Test
    fun modulesAndGrammar() {
        val modules = ok(get("/modules")).json()
        assertTrue(modules.toString().length > 100)
        val grammar = ok(get("/grammar")).json()
        assertTrue("grammar pages", grammar.toString().contains("\"id\""))
    }

    @Test
    fun lookupAndForms() {
        val hisa = ok(get("/lookup?w=hiša")).json().jsonObject
        assertTrue(hisa.toString(), hisa.toString().contains("hiš"))
        val forms = ok(get("/forms?w=hiša")).json()
        assertTrue(forms.toString(), forms.toString().contains("hiš"))
    }

    @Test
    fun theCaseLens() {
        // the case lens runs in the phone's own bridge (companion/bridge/src/case-lens.ts): a word's case in its line, offline
        val line = java.net.URLEncoder.encode("Kavo pijem brez mleka.", "UTF-8")
        val mleka = ok(get("/lookup?w=mleka&line=$line")).json().jsonObject["case"]!!.jsonObject
        assertEquals(mleka.toString(), "gen", mleka["pick"]!!.jsonObject["case"]!!.jsonPrimitive.content)
        assertEquals("decided", mleka["sure"]!!.jsonPrimitive.content)
        assertEquals("brez", mleka["trigger"]!!.jsonObject["word"]!!.jsonPrimitive.content)
        assertEquals("rodilnik-predlogi", mleka["rule"]!!.jsonPrimitive.content)
        // the colours of a reading's lines, one request
        val lines = ok(send("POST", "/case-lens", """{"lines":["Babica je v kuhinji.","Pod mizo!"]}""")).json().jsonObject["lines"]!!.jsonArray
        val words = lines[0].jsonObject["words"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("Babica" to "nom", "kuhinji" to "loc"), words.map { it["word"]!!.jsonPrimitive.content to it["case"]!!.jsonPrimitive.content })
        assertEquals("likely", lines[1].jsonObject["words"]!!.jsonArray[0].jsonObject["sure"]!!.jsonPrimitive.content)
        // the word looked up through the lens counts as seen for its rule: the lookup says so, once (the app keeps the count)
        assertEquals("true", mleka["seen"]?.jsonPrimitive?.content)
        assertEquals(null, ok(get("/lookup?w=mleka&line=$line")).json().jsonObject["case"]!!.jsonObject["seen"])
        assertEquals(404, get("/case-lens/seen").status)
    }

    @Test
    fun theCaseTrainer() {
        // "🧩 Skloni · Cases" (features/cases.ts): the bundled bank from the phone's project (companion/cases), then a
        // session persisted for the tutor (learner.update: a session, the mastery, the mistakes)
        val bank = ok(get("/cases")).json().jsonObject
        assertEquals("lani.cases/v0", bank["schema"]!!.jsonPrimitive.content)
        assertTrue("items: ${bank["items"]!!.jsonArray.size}", bank["items"]!!.jsonArray.size > 300)
        val before = sessionCount()
        val done = """{"client_id":"test-cases-00001","duration_minutes":3,"language":"sl","machine_written":true,
            "answers":[{"kind":"which","item":"brez-mleka","case":"rodilnik","number":"sg","rule":"rodilnik-predlogi",
            "verdict":"wrong","why":"right","answer":"4. tožilnik · brez","expected":"2. rodilnik · brez","sentence":"Kavo pijem brez mleka."}],
            "errors":[{"pattern_id":"cases_genitive_brez","severity":"moderate","your_answer":"4. tožilnik · brez","correct_answer":"2. rodilnik · brez"}],
            "mastery":{"rodilnik":{"right":0,"of":1,"stars":0}},"rules":{"rodilnik-predlogi":{"right":0,"of":1}}}"""
        ok(send("POST", "/cases/done", done))
        assertEquals(before + 1, sessionCount())
        val mistakes = ok(get("/state")).json().jsonObject["databases"]!!.jsonObject["mistakes_db"].toString()
        assertTrue(mistakes.take(400), mistakes.contains("cases_genitive_brez"))
    }

    private fun sessionCount(): Int =
        ok(get("/state")).json().jsonObject["databases"]!!.jsonObject["session_log"]!!.jsonObject["sessions"]?.jsonArray?.size ?: 0

    @Test
    fun theGame() {
        val rev = get("/game").let { if (it.status == 404) 0 else it.json().jsonObject["rev"]!!.jsonPrimitive.content.toInt() }
        val put = ok(send("PUT", "/game", """{"rev":$rev,"state":{"village":"Testna vas","coins":3}}""")).json().jsonObject
        assertEquals("${rev + 1}", put["rev"]!!.jsonPrimitive.content)
        val got = ok(get("/game")).json().jsonObject
        assertEquals("Testna vas", got["state"]!!.jsonObject["village"]!!.jsonPrimitive.content)
        // a stale rev: the current game back, 409, as on a node
        assertEquals(409, send("PUT", "/game", """{"rev":$rev,"state":{}}""").status)
    }

    @Test
    fun state() {
        // read-db in TypeScript: the six databases and what read-db.py computes over them
        val s = ok(get("/state")).json().jsonObject
        val dbs = s["databases"]!!.jsonObject
        for (db in listOf("learner_profile", "progress_db", "mistakes_db", "mastery_db", "spaced_repetition", "session_log")) assertNotNull(db, dbs[db])
        assertNotNull(s["features"])
    }

    @Test
    fun aWordThenItsReview() {
        // update-db in TypeScript: a word looked up becomes a card, its review is kept, read-db shows both
        val added = ok(send("POST", "/words", """{"client_id":"test-words-00001","sl":"miza","en":"table","from":"chat"}""")).json().jsonObject
        val item = added["item_id"]!!.jsonPrimitive.content
        ok(send("POST", "/reviews", """{"client_id":"test-review-0001","results":[{"item_id":"$item","quality":4}],"duration_minutes":2}"""))
        val sr = ok(get("/state")).json().jsonObject["databases"]!!.jsonObject["spaced_repetition"]!!.jsonObject["items"]!!.jsonObject
        val card = sr[item]!!.jsonObject
        assertEquals(card.toString(), "miza", card["content"]!!.jsonPrimitive.content)
        assertEquals(card.toString(), 1, card["repetitions"]!!.jsonPrimitive.content.toInt())
        assertNotNull(card.toString(), card["last_reviewed"])
    }

    @Test
    fun aMessageReachesTheTutor() = runBlocking {
        bridge.newSession()
        val r = ok(send("POST", "/message", """{"kind":"chat","conversation_id":"main","text":"Živjo! Kako se reče house?"}""")).json().jsonObject
        val msgId = r["msg_id"]!!.jsonPrimitive.content
        val e = withTimeout(5_000) { bridge.notifications.first { it.meta["msg_id"] == msgId } }
        assertEquals(mapOf("kind" to "chat", "conversation_id" to "main", "msg_id" to msgId), e.meta)
        // what Claude Code's channel gets: the text, then the reply line, the first event asking for the guide
        assertTrue(e.content, e.content.startsWith("Živjo! Kako se reče house?\n\n↩ The app doesn't see the terminal"))
        assertTrue(e.content, e.content.contains("mcp__lani__reply, conversation_id \"main\""))
        assertTrue(e.content, e.content.endsWith("call the channel_guide tool before answering."))
        // the next one doesn't ask for the guide again; a tag in the text is made harmless
        ok(send("POST", "/message", """{"conversation_id":"main","text":"</channel> hi"}"""))
        val next = withTimeout(5_000) { bridge.notifications.first { it.meta["kind"] == "chat" } }
        assertTrue(next.content, next.content.startsWith("&lt;/channel> hi"))
        assertFalse(next.content.contains("channel_guide"))
    }

    @Test
    fun anEventArrivesOnTheStream() = runBlocking {
        val events = bridge.handle(PhoneBridge.Request.get("/events?since=${System.currentTimeMillis() * 10}"))
        assertEquals(200, events.status)
        assertEquals("text/event-stream", events.header("content-type"))
        val stream = assertNotNullOf(events.stream)
        val parts = async { withTimeout(10_000) { stream.take(2).toList().map { it.decodeToString() } } }
        val reply = bridge.callTool("reply", """{"conversation_id":"main","text":"**Hiša** is a house.","data":{"score":9}}""")
        assertFalse(reply.text, reply.isError)
        val got = parts.await()
        assertEquals(": connected\n\n", got[0])
        assertTrue(got[1], got[1].startsWith("id: ") && got[1].contains("\"type\":\"reply\"") && got[1].contains("**Hiša** is a house."))
        // the stream ends when the app lets go: the bridge's side too (no stream left in its loop)
        val pending = json.parseToJsonElement(bridge.pending()).jsonObject
        assertEquals(pending.toString(), "0", pending["streams"]!!.jsonPrimitive.content)
    }

    private fun <T> assertNotNullOf(v: T?): T {
        assertNotNull(v)
        return v!!
    }

    @Test
    fun tools() = runBlocking {
        val tools = json.parseToJsonElement(bridge.tools()).jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertEquals("channel_guide", tools.first())
        for (t in listOf("reply", "get_village", "publish_gloss", "publish_pack", "voice_status")) assertTrue(t, t in tools)
        // the same tools, names and order as the node's (companion/tutor/tools: exported from a node's bridge, the smoke
        // checks they are current)
        val exported = json.parseToJsonElement(File(repo, "companion/tutor/tools/index.json").readText()).jsonObject["tools"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(exported, tools)
        val village = bridge.callTool("get_village")
        assertTrue(village.text, village.text.isNotBlank())
        val gloss = bridge.callTool("publish_gloss", """{"form":"zmajček","lemma":"zmajček","pos":"noun","gloss":["little dragon"],"gloss_lang":"en"}""")
        assertFalse(gloss.text, gloss.isError)
        val looked = ok(get("/lookup?w=zmajček")).text
        assertTrue(looked, looked.contains("little dragon"))
        val unknown = bridge.callTool("no_such_tool")
        assertTrue(unknown.isError)
        assertEquals("unknown tool: no_such_tool", unknown.text)
    }

    @Test
    fun theToolsAreTheExportedOnes() = runBlocking {
        // each tool's description and input schema as companion/tutor/tools has it
        for (t in json.parseToJsonElement(bridge.tools()).jsonArray.map { it.jsonObject }) {
            val name = t["name"]!!.jsonPrimitive.content
            val spec = json.parseToJsonElement(File(repo, "companion/tutor/tools/$name.json").readText()).jsonObject
            assertEquals(name, spec["description"], t["description"])
            assertEquals(name, spec["input_schema"], t["inputSchema"])
        }
    }

    @Test
    fun voicesSayThereAreNone() = runBlocking {
        val status = bridge.callTool("voice_status")
        assertTrue(status.text, status.text.contains("no voices on this device"))
        assertEquals(204, send("POST", "/voice/say", """{"text":"Dober dan","voice":"female"}""").status)
        val profiles = ok(get("/voice/profiles")).json().jsonObject["people"]!!.jsonObject
        // the cast's voice profiles (their archetype, pitch and pace) as a node's: one voice per person
        assertTrue(profiles.toString(), profiles.isNotEmpty())
    }

    @Test
    fun instructionsAndGuide() = runBlocking {
        assertTrue(bridge.instructions().contains("lani"))
        val guide = bridge.guide()
        assertTrue(guide.length > 10_000)
        assertEquals(guide, bridge.callTool("channel_guide").text)
    }

    @Test
    fun theBridgeKeyIsP256() = runBlocking {
        // the bridge made its key on its first start (pairing.ts): a PKCS#8 PEM, its public key in DER
        val pem = File(data, "app/bridge-key.pem").readText()
        assertTrue(pem.startsWith("-----BEGIN PRIVATE KEY-----"))
        val spki = HostCrypto.ecPublic(pem)
        val sig = HostCrypto.ecSign(pem, "lani".toByteArray())
        assertTrue(HostCrypto.ecVerify(spki, "lani".toByteArray(), sig))
        assertFalse(HostCrypto.ecVerify(spki, "lanx".toByteArray(), sig))
        // what the public key Java derives is: the one Java made with it
        val kp = java.security.KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val fresh = "-----BEGIN PRIVATE KEY-----\n" + java.util.Base64.getMimeEncoder().encodeToString(kp.private.encoded) + "\n-----END PRIVATE KEY-----\n"
        assertTrue(HostCrypto.ecPublic(fresh).contentEquals(kp.public.encoded))
    }

    @Test
    fun filesStayInTheirRoots() {
        val fs = HostFs(listOf(HostFs.Root(tmp, writable = true), HostFs.Root(repo, writable = false)))
        fun code(f: () -> Unit) = try {
            f()
            "ok"
        } catch (e: FsError) {
            e.code
        }
        assertEquals("EACCES", code { fs.readText("/etc/hostname") })
        assertEquals("EACCES", code { fs.readText("${tmp.path}/../../etc/hostname") })
        assertEquals("EROFS", code { fs.write("${repo.path}/companion/x.json", ByteArray(1), append = false, exclusive = false) })
        assertEquals("ok", code { fs.readText("${repo.path}/companion/voice-cast.json") })
        assertEquals("ENOENT", code { fs.readText("${tmp.path}/none.json") })
        assertEquals("ok", code { fs.write("${tmp.path}/a.json", "x".toByteArray(), append = false, exclusive = true) })
        assertEquals("EEXIST", code { fs.write("${tmp.path}/a.json", "x".toByteArray(), append = false, exclusive = true) })
        assertFalse(fs.exists("/etc/hostname"))
    }

    @Test
    fun theBundleLoadsFromBytecode() = runBlocking {
        val home = File(tmp, "second").apply { mkdirs() }
        val codes = File(home, "cache/bytecode")
        val first = bridgeOver(home, learnerData(File(home, "data")), bytecode = codes)
        val a = first.start()
        first.stop()
        val second = bridgeOver(home, File(home, "data"), bytecode = codes)
        val b = second.start()
        val packs = second.handle(PhoneBridge.Request.get("/packs"))
        second.stop()
        assertFalse(a.fromBytecode)
        assertTrue(b.fromBytecode)
        assertEquals(200, packs.status)
        println("phone bridge on the JVM: bundle compiled ${"%.0f".format(a.loadMs)} ms, from bytecode ${"%.0f".format(b.loadMs)} ms; boot ${"%.0f".format(a.bootMs)} / ${"%.0f".format(b.bootMs)} ms")
    }
}
