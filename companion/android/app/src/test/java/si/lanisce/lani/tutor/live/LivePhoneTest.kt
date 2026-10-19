package si.lanisce.lani.tutor.live

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
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
import si.lanisce.lani.tutor.FAKE_KEY
import si.lanisce.lani.tutor.KeyVault
import si.lanisce.lani.tutor.MemoryStore
import si.lanisce.lani.tutor.SoftCipher
import si.lanisce.lani.tutor.TurnStatus
import si.lanisce.lani.tutor.Usage
import si.lanisce.lani.tutor.blocks
import si.lanisce.lani.tutor.message
import si.lanisce.lani.tutor.obj
import si.lanisce.lani.tutor.str
import si.lanisce.lani.tutor.text
import si.lanisce.lani.tutor.toolUse
import si.lanisce.lani.tutor.tutorJson
import si.lanisce.lani.tutor.live.LiveFakes.Answering
import si.lanisce.lani.tutor.live.LiveFakes.MODELS
import si.lanisce.lani.tutor.live.LiveFakes.REPLY
import si.lanisce.lani.tutor.live.LiveFakes.bodyOf
import si.lanisce.lani.tutor.live.LiveFakes.sseOf
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors

/**
 * The live tutor against the phone's own bridge, in-process (the real bundle in QuickJS on the JVM, PhoneBridgeTest's
 * harness): the same tutor session's routes as on a node ([PhoneLink] behind [LiveBridge]). Attached, a chat the app
 * sends reaches the app's tutor through the phone's inbox, its learner_state comes from the phone's db port, its reply
 * is the phone bridge's reply event; a session's end at once writes the learner's data through record_session (the db
 * port's update-db); detached, the phone's channel gives its events to the host again.
 */
class LivePhoneTest {
    private val tmp: File = Files.createTempDirectory("live-phone").toFile()
    private val data = PhoneBridgeTest.learnerData(File(tmp, "data"))
    private val bridge: PhoneBridge = PhoneBridgeTest.bridgeOver(tmp, data)
    private val scopes = mutableListOf<CoroutineScope>()
    private var script: (JsonObject, Int) -> si.lanisce.lani.tutor.AssistantMessage = LiveFakes::tutorTurn
    private var calls = 0
    private val api = Answering { r ->
        if (r.url.encodedPath == "/v1/models") return@Answering Triple(200, MODELS, "application/json")
        val o = tutorJson.parseToJsonElement(bodyOf(r)) as JsonObject
        val m = script(o, ++calls)
        Triple(200, sseOf(m.copy(usage = Usage(input = 100, cacheWrite1h = 4_000, output = 80)), (o["model"] as JsonPrimitive).content), "text/event-stream")
    }

    private fun tutor(): LiveTutor {
        val scope = CoroutineScope(SupervisorJob())
        scopes += scope
        return LiveTutor(
            scope, LiveStore(File(tmp, "live")), KeyVault(SoftCipher(), MemoryStore()), openai = null,
            bridgeConfig = { BridgeConfig("http://lani.phone", "in-process") },
            debug = false,
            transportFor = { base, key -> AnthropicTransport(base, key, OkHttpClient.Builder().addInterceptor(api).build()) },
            bridgeFor = { c -> LiveBridge(c, PhoneLink.client { bridge }) },
            fran = null, device = "phone test", sleep = {},
            io = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
        )
    }

    @After fun stop() {
        scopes.forEach { it.cancel() }
        bridge.stop()
        synchronized(PhoneBridgeTest.logs) { PhoneBridgeTest.logs.forEach { println("bridge: ${it.take(600)}") } }
        tmp.deleteRecursively()
    }

    private fun send(method: String, path: String, body: String) = runBlocking { bridge.handle(PhoneBridge.Request.json(method, path, body)) }

    @Test fun `on the phone's own bridge, attached in-process, a chat answered with the db port's state, a session recorded`() = runBlocking {
        bridge.start()
        val t = tutor()
        t.refreshCredentials()
        assertEquals(null, t.setKey(FAKE_KEY))
        assertEquals(null, t.setOn(true))
        val session = tutorJson.parseToJsonElement(bridge.handle(PhoneBridge.Request.get("/tutor/session")).body.decodeToString()).jsonObject
        assertEquals("the phone's channel is the app's tutor's", "app", session["attached"]!!.jsonObject["who"]!!.jsonPrimitive.content)

        // the app's chat, as the outbox sends it
        assertEquals(200, send("POST", "/message", """{"kind":"chat","conversation_id":"main","text":"Kako se reče stove?"}""").status)
        t.backgroundCheck()
        val turn = t.today.turns.single()
        assertEquals(TurnStatus.DONE, turn.status)
        val state = turn.toolCalls.first { it.name == "learner_state" }
        assertTrue("learner_state from the phone's db port: ${state.result.take(300)}", !state.isError && state.result.contains("next_session_id"))
        val events = tutorJson.parseToJsonElement(bridge.handle(PhoneBridge.Request.get("/events/backlog?since=0")).body.decodeToString()).jsonArray
        assertTrue("the reply is the phone bridge's event", events.any { it.jsonObject["event"]?.jsonObject?.get("text")?.jsonPrimitive?.content == REPLY })

        // a session's end at once: record_session through the db port's update-db
        t.update { it.copy(reactAtOnce = true) }.join()
        script = { body, n ->
            val last = body["messages"]!!.jsonArray.last().jsonObject
            if (last.blocks().none { it.str("type") == "tool_result" }) message("tool_use", toolUse("s$n", "learner_state"))
            else if (!body.toString().contains("record_session\",\"input")) {
                val id = Regex("""next_session_id[^s]*(session-\d+)""").find(body.toString())?.groupValues?.get(1) ?: "session-001"
                message(
                    "tool_use",
                    toolUse("rec$n", "record_session", obj("session_id" to id, "date" to java.time.LocalDate.now().toString(), "duration_minutes" to 4, "session_notes" to "recorded on the phone")),
                    toolUse("r$n", "reply", obj("conversation_id" to "main", "text" to "Shranjeno.")),
                )
            } else message("end_turn", text("Opravljeno."))
        }
        send("POST", "/message", """{"kind":"session_end","conversation_id":"main","text":"Practice finished","data":{"results":[]}}""")
        t.backgroundCheck()
        val log = tutorJson.parseToJsonElement(File(data, "session-log.json").readText()).jsonObject
        val ended = t.today.turns.last()
        assertTrue(
            "the session in the phone's data: ${ended.status} ${ended.toolCalls.map { "${it.name} ${it.isError} ${it.result.take(300)}" }}",
            (log["sessions"] as JsonArray).any { it.jsonObject["session_notes"]?.jsonPrimitive?.content == "recorded on the phone" },
        )

        assertEquals(null, t.setOn(false))
        val after = tutorJson.parseToJsonElement(bridge.handle(PhoneBridge.Request.get("/tutor/session")).body.decodeToString()).jsonObject
        assertTrue("detached", after["attached"] == null || after["attached"].toString() == "null")
    }
}
