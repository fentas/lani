package si.lanisce.lani.tutor.live

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.BridgeConfig
import si.lanisce.lani.phone.PhoneBridge
import si.lanisce.lani.phone.PhoneBridgeTest
import si.lanisce.lani.tutor.AnthropicTransport
import si.lanisce.lani.tutor.AssistantMessage
import si.lanisce.lani.tutor.FAKE_KEY
import si.lanisce.lani.tutor.KeyVault
import si.lanisce.lani.tutor.MemoryStore
import si.lanisce.lani.tutor.SoftCipher
import si.lanisce.lani.tutor.Usage
import si.lanisce.lani.tutor.blocks
import si.lanisce.lani.tutor.message
import si.lanisce.lani.tutor.obj
import si.lanisce.lani.tutor.role
import si.lanisce.lani.tutor.str
import si.lanisce.lani.tutor.text
import si.lanisce.lani.tutor.toolUse
import si.lanisce.lani.tutor.tutorJson
import si.lanisce.lani.tutor.live.LiveFakes.Answering
import si.lanisce.lani.tutor.live.LiveFakes.MODELS
import si.lanisce.lani.tutor.live.LiveFakes.bodyOf
import si.lanisce.lani.tutor.live.LiveFakes.sseOf
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors

/**
 * The studio on the phone's own bridge, in-process (the hosted mode: the real bundle in QuickJS on the JVM,
 * PhoneBridgeTest's harness, [PhoneLink] behind [LiveBridge]): a chat's request_content becomes a job whose publish the
 * bundle's own validateModule refuses, then takes (the module served by GET /modules); the file tool's write refused by
 * the bundle's validator, then written; the job kept in the phone's data (app/tutor/jobs), and a job the process lost
 * mid-way resumed by a new one from there.
 */
class StudioPhoneTest {
    private val tmp: File = Files.createTempDirectory("studio-phone").toFile()
    private val data = PhoneBridgeTest.learnerData(File(tmp, "data"))
    private val bridge: PhoneBridge = PhoneBridgeTest.bridgeOver(tmp, data)
    private val scopes = mutableListOf<CoroutineScope>()
    private var marker = "[t:module]"
    private var before: (JsonObject) -> Unit = {}
    private var calls = 0
    private val api = Answering { r ->
        if (r.url.encodedPath == "/v1/models") return@Answering Triple(200, MODELS, "application/json")
        val o = tutorJson.parseToJsonElement(bodyOf(r)) as JsonObject
        before(o)
        val m = script(o, ++calls)
        Triple(200, sseOf(m.copy(usage = Usage(input = 100, cacheWrite1h = 4_000, output = 80)), (o["model"] as JsonPrimitive).content), "text/event-stream")
    }

    private fun isStudio(body: JsonObject) = body["system"].toString().contains("studio of Lani")

    private fun script(body: JsonObject, n: Int): AssistantMessage {
        val messages = body["messages"]!!.jsonArray.map { it.jsonObject }
        val results = messages.last().blocks().filter { it.str("type") == "tool_result" }
        if (!isStudio(body)) {
            if (results.isNotEmpty()) return message("end_turn", text("Opravljeno."))
            return message(
                "tool_use",
                toolUse("rc$n", "request_content", obj("title" to "Vaje: pozdravi (strojno) · Greetings drill", "brief" to "A short drill on greetings. $marker", "urgent" to true)),
                toolUse("r$n", "reply", obj("conversation_id" to "main", "text" to "Naročeno.")),
            )
        }
        val first = messages.first().toString()
        val lastId = results.lastOrNull()?.str("tool_use_id").orEmpty()
        val lastError = results.lastOrNull()?.get("is_error")?.jsonPrimitive?.content == "true"
        val file = first.contains("[t:file]")
        return when {
            results.isEmpty() -> message("tool_use", toolUse("ls$n", "learner_state"))
            lastId.startsWith("ls") && file -> message("tool_use", toolUse("fb$n", "str_replace_based_edit_tool", obj("command" to "create", "path" to "/app/modules/phone-file-drill/v1.json", "file_text" to spec("phone-file-drill", 4, version = 1).toString())))
            lastId.startsWith("fb") && lastError -> message("tool_use", toolUse("fg$n", "str_replace_based_edit_tool", obj("command" to "create", "path" to "/app/modules/phone-file-drill/v1.json", "file_text" to spec("phone-file-drill", 1, version = 1).toString())))
            lastId.startsWith("ls") -> message("tool_use", toolUse("pb$n", "publish_module", obj("spec" to spec("phone-studio-drill", 4), "note" to "machine-written (test)")))
            lastId.startsWith("pb") && lastError -> message("tool_use", toolUse("pg$n", "publish_module", obj("spec" to spec("phone-studio-drill", 1), "note" to "machine-written (test)")))
            (lastId.startsWith("pg") || lastId.startsWith("fg")) && !lastError -> message("tool_use", toolUse("rp$n", "reply", obj("conversation_id" to "main", "text" to "**Studio** · objavljeno · published.")))
            else -> message("end_turn", text("Done."))
        }
    }

    /** A module the bundle's validateModule takes ([answer] 0 or 1) or refuses (an answer out of range); machine-written. */
    private fun spec(id: String, answer: Int, version: Int? = null) = obj(
        "schema" to "lani.module/v0", "id" to id, "title" to "Pozdravi (strojno · machine-written)", "level" to "A1",
        "targets" to JsonArray(listOf(JsonPrimitive("greetings"))), "tags" to JsonArray(listOf(JsonPrimitive("machine-written"))),
        "version" to version,
        "exercises" to JsonArray(
            listOf(
                obj("type" to "choice", "prompt" to "Kateri pozdrav je vljuden? · Which greeting is polite?", "options" to JsonArray(listOf(JsonPrimitive("Živjo"), JsonPrimitive("Dober dan"))), "answer" to answer, "explain" to "**Dober dan** is polite."),
                obj("type" to "cloze", "text" to "Ona ___ doma. · She is at home.", "accept" to JsonArray(listOf(JsonPrimitive("je")))),
                obj("type" to "reorder", "prompt" to "Good day!", "tokens" to JsonArray(listOf(JsonPrimitive("Dober"), JsonPrimitive("dan"))), "solutions" to JsonArray(listOf(JsonArray(listOf(JsonPrimitive("Dober"), JsonPrimitive("dan")))))),
                obj("type" to "translate", "prompt" to "Thank you very much.", "accept" to JsonArray(listOf(JsonPrimitive("Hvala lepa.")))),
                obj("type" to "flashcard", "front" to "hvala", "back" to "thank you"),
            ),
        ),
    )

    private fun tutor(scope: CoroutineScope = CoroutineScope(SupervisorJob()).also { scopes += it }): LiveTutor = LiveTutor(
        scope, LiveStore(File(tmp, "live")), vault, openai = null,
        bridgeConfig = { BridgeConfig("http://lani.phone", "in-process") },
        debug = false,
        transportFor = { base, key -> AnthropicTransport(base, key, OkHttpClient.Builder().addInterceptor(api).build()) },
        bridgeFor = { c -> LiveBridge(c, PhoneLink.client { bridge }) },
        fran = null, device = "phone test", sleep = {},
        io = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
        studioPollMs = 20,
    )

    private val vault = KeyVault(SoftCipher(), MemoryStore())

    @After fun stop() {
        scopes.forEach { it.cancel() }
        bridge.stop()
        tmp.deleteRecursively()
    }

    private fun send(method: String, path: String, body: String) = runBlocking { bridge.handle(PhoneBridge.Request.json(method, path, body)) }
    private fun get(path: String) = runBlocking { tutorJson.parseToJsonElement(bridge.handle(PhoneBridge.Request.get(path)).body.decodeToString()) }

    private suspend fun on(t: LiveTutor) {
        t.refreshCredentials()
        assertEquals(null, t.setKey(FAKE_KEY))
        assertEquals(null, t.setOn(true))
    }

    private fun jobs(): List<JsonObject> = get("/tutor/jobs").jsonObject["jobs"]!!.jsonArray.map { it.jsonObject }

    @Test fun `on the phone's bridge a chat's request becomes a job, the bundle's validator refuses its publish, then takes it`() = runBlocking {
        bridge.start()
        val t = tutor()
        on(t)
        assertEquals(200, send("POST", "/message", """{"kind":"chat","conversation_id":"main","text":"Vaje, prosim."}""").status)
        t.backgroundCheck()
        t.studio.awaitIdle(30_000)
        val job = jobs().single()
        assertEquals(StudioJob.DONE, job.str("status"))
        assertTrue("the job in the phone's data", File(data, "app/tutor/jobs/${job.str("id")}.json").isFile)
        val full = tutorJson.parseToJsonElement(File(data, "app/tutor/jobs/${job.str("id")}.json").readText()).jsonObject
        val refusal = full["messages"]!!.jsonArray.map { it.toString() }.firstOrNull { it.contains("is_error") }
        assertTrue("the bundle's validateModule refused the bad spec: $refusal", refusal != null && refusal.contains("answer"))
        val modules = get("/modules").toString()
        assertTrue("the module is served by the phone's bridge", modules.contains("phone-studio-drill"))
        assertEquals("published", full["drafts"]!!.jsonObject.values.single().jsonObject.str("state"))
    }

    @Test fun `the file tool's write on the phone, refused by the bundle's validator, then written`() = runBlocking {
        marker = "[t:file]"
        bridge.start()
        val t = tutor()
        on(t)
        send("POST", "/message", """{"kind":"chat","conversation_id":"main","text":"A drill as a file."}""")
        t.backgroundCheck()
        t.studio.awaitIdle(30_000)
        val job = jobs().single()
        assertEquals(StudioJob.DONE, job.str("status"))
        val drafts = tutorJson.parseToJsonElement(File(data, "app/tutor/jobs/${job.str("id")}.json").readText()).jsonObject["drafts"]!!.jsonObject
        assertEquals("published", drafts.values.single().jsonObject.str("state"))
        assertTrue(File(data, "app/modules/phone-file-drill/v1.json").isFile)
    }

    @Test fun `a job the process lost mid-way is resumed by a new one from the phone's data`() = runBlocking {
        bridge.start()
        val scopeA = CoroutineScope(SupervisorJob())
        before = { body ->
            if (isStudio(body) && body["messages"]!!.jsonArray.last().toString().contains("is_error")) {
                scopeA.cancel()
                Thread.sleep(200)
            }
        }
        val a = tutor(scopeA)
        on(a)
        send("POST", "/message", """{"kind":"chat","conversation_id":"main","text":"Vaje, prosim."}""")
        a.backgroundCheck()
        for (i in 0 until 200) {
            if (jobs().singleOrNull()?.str("status") == StudioJob.QUEUED) break
            delay(25)
        }
        assertEquals("saved when the process went", StudioJob.QUEUED, jobs().single().str("status"))
        before = {}
        val b = tutor()
        for (i in 0 until 400) {
            if (jobs().single().str("status") == StudioJob.DONE) break
            delay(25)
        }
        b.studio.awaitIdle(30_000)
        val job = jobs().single()
        assertEquals(StudioJob.DONE, job.str("status"))
        assertTrue(job["attempts"]!!.jsonPrimitive.content.toInt() >= 2)
        assertTrue(get("/modules").toString().contains("phone-studio-drill"))
    }
}
