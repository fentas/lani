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
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.BridgeConfig
import si.lanisce.lani.tutor.AnthropicTransport
import si.lanisce.lani.tutor.AssistantMessage
import si.lanisce.lani.tutor.FAKE_KEY
import si.lanisce.lani.tutor.KeyVault
import si.lanisce.lani.tutor.MemoryStore
import si.lanisce.lani.tutor.ProviderId
import si.lanisce.lani.tutor.SoftCipher
import si.lanisce.lani.tutor.Task
import si.lanisce.lani.tutor.TurnKind
import si.lanisce.lani.tutor.TurnStatus
import si.lanisce.lani.tutor.Usage
import si.lanisce.lani.tutor.blocks
import si.lanisce.lani.tutor.message
import si.lanisce.lani.tutor.obj
import si.lanisce.lani.tutor.role
import si.lanisce.lani.tutor.sse
import si.lanisce.lani.tutor.str
import si.lanisce.lani.tutor.text
import si.lanisce.lani.tutor.thinking
import si.lanisce.lani.tutor.toolUse
import si.lanisce.lani.tutor.tutorJson
import si.lanisce.lani.tutor.live.LiveFakes.Answering
import si.lanisce.lani.tutor.live.LiveFakes.MODELS
import si.lanisce.lani.tutor.live.LiveFakes.REPLY
import si.lanisce.lani.tutor.live.LiveFakes.bodyOf
import si.lanisce.lani.tutor.live.LiveFakes.sseOf
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Collections
import java.util.concurrent.Executors

/**
 * The live tutor end to end (docs/plans/05-built/tutor-live.md): over the real Messages API client (its SSE parsing, the
 * reply's streamed input) against a scripted API, and a fake paired bridge playing the tutor session's routes
 * (companion/bridge/src/features/tutor.ts): attach, the inbox, the tools for real through the bridge, the reply
 * streamed into the chat, the acks, the digest, the cap, the key refused, the credit out, the role-play's own thread,
 * the text taken as the reply, detach giving back; and the guards: the key only to the API, the bridge token only to
 * the bridge.
 */
class LiveTutorTest {
    private val zone = ZoneId.of("Europe/Ljubljana")
    private val dir: File = Files.createTempDirectory("live-test").toFile()
    private var now = LocalDateTime.of(2026, 10, 19, 10, 0).atZone(zone).toInstant().toEpochMilli()
    private val lines = Collections.synchronizedList(mutableListOf<String>())
    private val vault = KeyVault(SoftCipher(), MemoryStore())
    private val scopes = mutableListOf<CoroutineScope>()
    private val streamed = Collections.synchronizedList(mutableListOf<String?>())
    private val notes = Collections.synchronizedList(mutableListOf<String>())

    // --- the API -------------------------------------------------------------------------------------------------------

    private var script: (JsonObject, Int) -> AssistantMessage = LiveFakes::tutorTurn
    private var usage = Usage(input = 100, cacheWrite1h = 4_000, output = 80)
    private var failOn: (Int) -> Triple<Int, String, String>? = { null }
    private var calls = 0
    private val api = Answering { r ->
        if (r.url.encodedPath == "/v1/models") return@Answering Triple(200, MODELS, "application/json")
        val o = tutorJson.parseToJsonElement(bodyOf(r)) as JsonObject
        calls++
        failOn(calls)?.let { return@Answering it }
        val m = script(o, calls)
        Triple(200, sseOf(m.copy(usage = usage), (o["model"] as JsonPrimitive).content), "text/event-stream")
    }

    private fun posts(): List<JsonObject> = synchronized(api.requests) { api.requests.toList() }.filter { it.method == "POST" }.map { tutorJson.parseToJsonElement(bodyOf(it)) as JsonObject }

    // --- the paired bridge's tutor session -----------------------------------------------------------------------------

    private val node = FakeNode()

    /** The phone's network, as the app's callback reports it (tutor/live/AndroidOnline). */
    private val net = si.lanisce.lani.tutor.OnlineState(true)

    // --- Gemini and OpenRouter, scripted --------------------------------------------------------------------------------

    private val gem = si.lanisce.lani.tutor.ScriptedGemini { body, n -> si.lanisce.lani.tutor.geminiTurn(body, n) }
    /** The phone is where Google allows only the Gemini API's paid services (the EEA, Switzerland, the UK). */
    private var paidOnly = false
    private val geminiCreds = si.lanisce.lani.tutor.GeminiCredentials(KeyVault(SoftCipher(), MemoryStore()), MemoryStore(), si.lanisce.lani.tutor.GeminiPacer(clock = { now }, sleep = {}))
    private val router = si.lanisce.lani.tutor.ScriptedRouter { body, n -> si.lanisce.lani.tutor.routerTurn(body, n) }
    private val routerCreds = si.lanisce.lani.tutor.OpenRouterCredentials(si.lanisce.lani.tutor.OpenRouterAuth(KeyVault(SoftCipher(), MemoryStore()), router.client))

    private fun tutor(): LiveTutor {
        val scope = CoroutineScope(SupervisorJob())
        scopes += scope
        return LiveTutor(
            scope, LiveStore(dir), vault, openai = null,
            bridgeConfig = { BridgeConfig("http://node.test:8790", BRIDGE_TOKEN) },
            debug = false,
            transportFor = { base, key -> AnthropicTransport(base, key, OkHttpClient.Builder().addInterceptor(api).build(), clock = { now }) },
            bridgeFor = { c -> LiveBridge(c, OkHttpClient.Builder().addInterceptor(node).build()) },
            fran = null,
            clock = { now }, zone = { zone }, device = "test phone",
            logLine = { lines += it }, sleep = {},
            io = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
            texts = { key, _ -> key },
            gemini = geminiCreds,
            paidOnlyRegion = { paidOnly },
            geminiTransportFor = { k, p, f -> si.lanisce.lani.tutor.GeminiTransport(k, gem.client, clock = { now }, pacer = p, onFreeTier = f) },
            openrouter = routerCreds,
            openrouterTransportFor = { si.lanisce.lani.tutor.OpenRouterTransport(it, router.client, clock = { now }) },
            online = net,
        ).also { t ->
            t.onStreaming = { conv, text -> if (conv == "main") streamed += text }
            t.onNote = { notes += it }
        }
    }

    @After fun stop() {
        scopes.forEach { it.cancel() }
        dir.deleteRecursively()
    }

    private suspend fun ready(t: LiveTutor = tutor()): LiveTutor {
        t.refreshCredentials()
        assertEquals(null, t.setKey(FAKE_KEY))
        assertEquals(null, t.setOn(true))
        return t
    }

    // --- the tests -----------------------------------------------------------------------------------------------------

    @Test fun `on, attached to the bridge's tutor session, a chat is answered for real, the reply streamed, the event acknowledged`() = runBlocking {
        val t = ready()
        assertTrue("attached", node.attached)
        assertEquals("test phone", node.device)
        assertEquals(LiveState.IDLE, t.state)
        val id = node.push("chat", "main", "Kako se reče stove?")
        t.backgroundCheck()
        // three calls: the guide and learner_state, then the reply, then done
        assertEquals(3, posts().size)
        val toolNames = node.tools.map { it.first }
        assertEquals("learner_state and reply through the bridge (the guide is the app's own)", listOf("learner_state", "reply"), toolNames)
        val reply = node.tools.first { it.first == "reply" }.second
        assertEquals("main", reply["args"]!!.jsonObject["conversation_id"]!!.jsonPrimitive.content)
        val replyId = posts().last()["messages"]!!.jsonArray.map { it.jsonObject }.flatMap { it.blocks() }.first { it.str("type") == "tool_use" && it.str("name") == "reply" }.str("id")!!
        // the bridge's once takes [\w-]{8,64}: a short id (the script's "r2") goes as its fingerprint, the same each time
        assertEquals("the tool call's id as the client id", LiveBridge.clientId(replyId), reply["client_id"]!!.jsonPrimitive.content)
        assertEquals("toolu_01ABCdef-89_x", LiveBridge.clientId("toolu_01ABCdef-89_x"))
        assertTrue(Regex("^[\\w-]{8,64}$").matches(LiveBridge.clientId("call.1/x")))
        // the reply streamed as it was written: growing prefixes of the final text
        val texts = streamed.filterNotNull()
        assertTrue("streamed in pieces: $texts", texts.size >= 3)
        assertTrue("each piece grows", texts.zipWithNext().all { (a, b) -> b.startsWith(a) })
        assertEquals(REPLY, texts.last())
        assertEquals("acknowledged once done", listOf(id), node.acked)
        val turn = t.today.turns.single()
        assertEquals(TurnStatus.DONE, turn.status)
        assertEquals(TurnKind.DAY, turn.kind)
        assertEquals(REPLY, turn.replies.single().text)
        // the first message of the day's thread: the notebook from the node, the event in the channel's shape
        val first = posts().first()["messages"]!!.jsonArray.first().toString()
        assertTrue(first.contains("Jan likes tables") && first.contains("<event kind=\\\"chat\\\" conversation_id=\\\"main\\\""))
        // the guards: the key only in x-api-key to the API, the bridge token only to the bridge
        assertTrue(api.requests.all { it.url.host == "api.anthropic.com" && it.header("x-api-key") == FAKE_KEY && it.header("Authorization") == null })
        assertTrue(node.requests.all { it.url.host == "node.test" && it.header("x-api-key") == null && it.header("Authorization") == "Bearer $BRIDGE_TOKEN" })
        assertFalse(node.requests.any { bodyOf(it).contains(FAKE_KEY) })
        assertFalse(api.requests.any { bodyOf(it).contains(BRIDGE_TOKEN) })
        assertFalse(lines.any { it.contains(FAKE_KEY) || it.contains(BRIDGE_TOKEN) })
        assertTrue("priced", t.spent() > 0)
    }

    @Test fun `on Gemini's free tier - a chat answered on Flash for nothing with its API-equivalent, no Argon, the free tier told once in the chat`() = runBlocking {
        val t = tutor()
        t.refreshCredentials()
        t.update { it.copy(provider = ProviderId.GEMINI) }.join()
        assertEquals(LiveState.OFF, t.state)
        assertEquals(null, t.setGeminiKey(si.lanisce.lani.tutor.FAKE_GEMINI_KEY, si.lanisce.lani.tutor.GeminiTerms.FREE))
        assertEquals(null, t.setOn(true))
        assertTrue(t.geminiFree)
        assertEquals("gemini-3.8-flash", t.modelFor(Task.CHAT))
        assertEquals("the free tier has no Argon: content on Flash", "gemini-3.8-flash", t.modelFor(Task.CONTENT))
        assertEquals("gemini-3.5-flash-lite", t.modelFor(Task.ROUTINE))
        val id = node.push("chat", "main", "Kako se reče stove?")
        t.backgroundCheck()
        val sent = gem.posts()
        assertEquals("guide and learner_state, the reply, done", 3, sent.size)
        assertTrue(gem.http.requests.filter { it.method == "POST" }.all { it.url.encodedPath == "/v1beta/models/gemini-3.8-flash:streamGenerateContent" && it.header("x-goog-api-key") == si.lanisce.lani.tutor.FAKE_GEMINI_KEY })
        assertEquals(listOf("learner_state", "reply"), node.tools.map { it.first })
        assertEquals(listOf(id), node.acked)
        assertEquals(TurnStatus.DONE, t.today.turns.single().status)
        val calls = t.today.calls
        assertTrue(calls.all { it.billing == si.lanisce.lani.tutor.Billing.FREE_TIER && it.cost == 0.0 && it.apiEquivalent!! > 0 && it.provider == "gemini" })
        assertEquals(0.0, t.spent(), 0.0)
        // the free tier said once in the chat, plainly; the Tutor section says it while the key is on it
        assertEquals(listOf("tutorLive.note.geminiFree"), notes.filter { it.contains("gemini") })
        t.setGeminiTier(si.lanisce.lani.tutor.GeminiTerms.FREE).join()
        assertEquals(1, notes.count { it.contains("gemini") })
        assertFalse(lines.any { it.contains(si.lanisce.lani.tutor.FAKE_GEMINI_KEY) })
        // paid: the table's price, Argon for content again
        t.setGeminiTier(si.lanisce.lani.tutor.GeminiTerms.PAID).join()
        assertEquals("gemini-4-argon", t.modelFor(Task.CONTENT))
    }

    @Test fun `where only Google's paid API is allowed, a Gemini key marked free is treated as paid, its privacy note kept`() = runBlocking {
        paidOnly = true
        val t = tutor()
        t.refreshCredentials()
        t.update { it.copy(provider = ProviderId.GEMINI) }.join()
        assertEquals(null, t.setGeminiKey(si.lanisce.lani.tutor.FAKE_GEMINI_KEY, si.lanisce.lani.tutor.GeminiTerms.FREE))
        assertEquals(null, t.setOn(true))
        assertTrue(t.geminiPaidOnly)
        assertFalse("not priced as free", t.geminiFree)
        assertTrue("the privacy note stays", t.geminiMarkedFree)
        assertEquals("paid: Argon for content", "gemini-4-argon", t.modelFor(Task.CONTENT))
        node.push("chat", "main", "Kako se reče stove?")
        t.backgroundCheck()
        val calls = t.today.calls
        assertTrue(calls.isNotEmpty() && calls.all { it.billing == si.lanisce.lani.tutor.Billing.API_KEY && it.cost!! > 0 && it.apiEquivalent == null })
        assertTrue(t.spent() > 0)
        assertEquals(listOf("tutorLive.note.geminiFree"), notes.filter { it.contains("gemini") })
    }

    @Test fun `on OpenRouter - signed in through the browser, a chat answered on the tier's default or the model picked, priced by OpenRouter's cost`() = runBlocking {
        val t = tutor()
        t.refreshCredentials()
        t.update { it.copy(provider = ProviderId.OPENROUTER) }.join()
        val signedIn = t.signInWithOpenRouter { url ->
            val u = url.toHttpUrl()
            val cb = u.queryParameter("callback_url")!!.toHttpUrl()
            Thread {
                java.net.Socket("127.0.0.1", cb.port).use { sock ->
                    sock.getOutputStream().write("GET /auth/openrouter?code=or1&state=${u.queryParameter("state")} HTTP/1.1\r\nHost: 127.0.0.1:${cb.port}\r\n\r\n".toByteArray())
                    sock.getInputStream().readBytes()
                }
            }.start()
        }
        assertEquals(null, signedIn)
        assertTrue(t.openrouterReady)
        assertEquals(17.5, t.openrouterKey!!.remaining!!, 0.0)
        assertEquals("the list read: its models", "openrouter", t.settings.listedWith)
        assertEquals(null, t.setOn(true))
        assertEquals("the mid tier's default", "google/gemini-3.8-flash", t.modelFor(Task.CHAT))
        assertEquals("google/gemini-4-argon", t.modelFor(Task.CONTENT))
        node.push("chat", "main", "Kako se reče stove?")
        t.backgroundCheck()
        assertEquals(3, router.posts().size)
        assertTrue(router.posts().all { it.str("model") == "google/gemini-3.8-flash" && it["reasoning"] != null })
        val calls = t.today.calls
        assertTrue(calls.all { it.billing == si.lanisce.lani.tutor.Billing.OPENROUTER && it.cost == 0.0123 && it.provider == "openrouter" })
        assertEquals(3 * 0.0123, t.spent(), 1e-9)
        assertTrue(router.http.requests.filter { it.url.encodedPath == "/api/v1/chat/completions" }.all { it.header("Authorization") == "Bearer ${si.lanisce.lani.tutor.FAKE_ROUTER_KEY}" })
        // a model picked for the chat under "advanced"
        t.update { it.copy(models = it.models + (Task.CHAT to "anthropic/claude-sonnet-5.5")) }.join()
        assertEquals("anthropic/claude-sonnet-5.5", t.modelFor(Task.CHAT))
        // across providers: the content on Claude directly while its key is kept, the rest on OpenRouter
        t.update { it.copy(models = it.models + (Task.CONTENT to "claude-opus-5-5")) }.join()
        assertEquals("no Claude key: the preset's", "google/gemini-4-argon", t.modelFor(Task.CONTENT))
        assertEquals(null, t.setKey(FAKE_KEY))
        assertEquals("claude-opus-5-5", t.modelFor(Task.CONTENT))
        assertEquals("the small tier's default: Sol, never Luna (the matrix)", "openai/gpt-6.1-sol", t.modelFor(Task.ROUTINE))
        assertFalse(lines.any { it.contains(si.lanisce.lani.tutor.FAKE_ROUTER_KEY) })
        // signed out: no credential, the tutor waits
        t.signOutOfOpenRouter()
        assertEquals(LiveState.NO_CREDENTIAL, t.state)
    }

    @Test fun `session_end at once (react at once), record_session goes through the bridge with learner_state's next session id`() = runBlocking {
        val t = ready()
        t.update { it.copy(reactAtOnce = true) }.join()
        script = { body, n ->
            val last = body["messages"]!!.jsonArray.last().jsonObject
            val results = last.blocks().any { it.str("type") == "tool_result" }
            when {
                !results && !body.toString().contains("tool_result") -> message("tool_use", toolUse("s$n", "learner_state"))
                node.tools.none { it.first == "record_session" } -> message(
                    "tool_use",
                    toolUse("rec$n", "record_session", obj("session_id" to "session-042", "date" to "2026-10-19", "duration_minutes" to 5)),
                    toolUse("r$n", "reply", obj("conversation_id" to "main", "text" to "Shranjeno.")),
                )
                else -> message("end_turn", text("Opravljeno."))
            }
        }
        val id = node.push("session_end", "main", "Session done.\n\n```json\n{\"results\": []}\n```")
        t.backgroundCheck()
        val rec = node.tools.firstOrNull { it.first == "record_session" }?.second
        assertNotNull("record_session ran on the bridge", rec)
        assertEquals("session-042", rec!!["args"]!!.jsonObject["session_id"]!!.jsonPrimitive.content)
        assertEquals(listOf(id), node.acked)
        assertEquals("grading's model (Haiku), a stateless call", TurnKind.STATELESS, t.today.turns.single().kind)
    }

    @Test fun `the digest, nothing worth saying is acknowledged without a call, a wrong answer is a turn`() = runBlocking {
        val t = ready()
        val quiet = node.push("review_done", "main", "App review finished (already persisted as session-010): 5/5 recalled in 2 min.")
        t.backgroundCheck()
        assertEquals("into the digest: no call yet", 0, posts().size)
        assertTrue(node.acked.isEmpty())
        t.onChatOpened().join()
        t.awaitIdle()
        assertEquals("all right, five: nothing worth a call", 0, posts().size)
        t.backgroundCheck() // the acks go out
        assertEquals(listOf(quiet), node.acked)
        val wrong = node.push("review_done", "main", "App review finished (already persisted as session-011): 3/5 recalled in 2 min. Struggled with: vocab_kurim.")
        t.backgroundCheck()
        t.onChatOpened().join()
        t.awaitIdle()
        assertTrue("a turn for the digest", posts().isNotEmpty())
        t.backgroundCheck()
        assertTrue(wrong in node.acked)
        assertEquals("digest", t.today.turns.last().eventKind)
    }

    @Test fun `the daily cap stops it and says so in the chat`() = runBlocking {
        val t = ready()
        t.update { it.copy(dailyCap = 0.0001) }.join()
        node.push("chat", "main", "Živjo")
        t.backgroundCheck()
        assertEquals("one call, then the cap", 1, posts().size)
        assertEquals(LiveState.PAUSED_BUDGET, t.state)
        assertEquals(listOf("tutorLive.note.budget"), notes)
        assertTrue("the turn waits", t.queue().isNotEmpty())
        assertTrue("not acknowledged: it waits", node.acked.isEmpty())
        t.update { it.copy(dailyCap = 5.0) }.join()
        t.awaitIdle()
        assertEquals(TurnStatus.DONE, t.today.turns.single().status)
    }

    @Test fun `a refused key pauses it and says so, a new key goes on`() = runBlocking {
        val t = ready()
        failOn = { n -> if (n == 1) Triple(401, """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}""", "application/json") else null }
        node.push("chat", "main", "Živjo")
        t.backgroundCheck()
        assertEquals(LiveState.PAUSED_KEY, t.state)
        assertEquals(listOf("tutorLive.note.key"), notes)
        assertEquals(null, t.setKey(FAKE_KEY))
        t.awaitIdle()
        assertEquals(TurnStatus.DONE, t.today.turns.single().status)
    }

    @Test fun `the credit out, a chat says why it can't answer, the routine waits`() = runBlocking {
        val t = ready()
        failOn = { Triple(400, """{"type":"error","error":{"type":"invalid_request_error","message":"Your credit balance is too low to access the Anthropic API."}}""", "application/json") }
        val id = node.push("chat", "main", "Živjo")
        t.backgroundCheck()
        assertEquals(LiveState.PAUSED_CREDIT, t.state)
        assertEquals(listOf("tutorLive.note.credit"), notes)
        assertEquals(TurnStatus.FAILED, t.today.turns.single().status)
        assertEquals("told, so done with", listOf(id), node.acked)
    }

    @Test fun `off, detached, what the app hadn't answered goes back to Claude Code`() = runBlocking {
        val t = ready()
        failOn = { Triple(401, """{"type":"error","error":{"type":"authentication_error","message":"no"}}""", "application/json") }
        node.push("chat", "main", "Živjo")
        t.backgroundCheck()
        assertTrue("the turn waits for a key", t.queue().isNotEmpty())
        assertEquals(null, t.setOn(false))
        assertFalse(node.attached)
        assertEquals("the inbox held it: given back", 1, node.returned)
        assertTrue("the queue let it go", t.queue().isEmpty())
        assertEquals(TurnStatus.SKIPPED, t.today.turns.single().status)
        assertEquals(LiveState.OFF, t.state)
    }

    @Test fun `what writes content is left for Claude Code, never run, never acknowledged`() = runBlocking {
        val t = ready()
        node.push("villager_arrived", "main", "Someone moved in.")
        t.backgroundCheck()
        assertEquals(0, posts().size)
        assertTrue(node.acked.isEmpty())
        assertEquals("villager_arrived", t.today.events.single().kind)
    }

    @Test fun `a role-play's turns on their own short thread, its end lets the thread go`() = runBlocking {
        val t = ready()
        script = { body, n ->
            val last = body["messages"]!!.jsonArray.last().jsonObject
            if (last.blocks().any { it.str("type") == "tool_result" }) message("end_turn", text("…"))
            else {
                val conv = Regex("""conversation_id=\\"([\w-]+)""").find(last.toString())?.groupValues?.get(1) ?: "rp-x"
                message("tool_use", toolUse("rp$n", "reply", obj("conversation_id" to conv, "text" to "Me veseli! ($n)")))
            }
        }
        node.push("roleplay", "rp-cafe", "Dober dan!\n\n```json\n{\"scenario_id\": \"kavarna\", \"turn\": 1, \"heard\": \"Dober dan\"}\n```")
        t.backgroundCheck()
        node.push("roleplay", "rp-cafe", "Eno kavo, prosim.\n\n```json\n{\"scenario_id\": \"kavarna\", \"turn\": 2, \"heard\": \"Eno kavo, prosim\"}\n```")
        t.backgroundCheck()
        val second = posts().first { it.toString().contains("Eno kavo") }
        assertTrue("the second turn's request carries the first", second.toString().contains("Dober dan"))
        assertTrue(t.today.turns.all { it.kind == TurnKind.ROLEPLAY })
        assertNotNull(t.store.roleplay("rp-cafe"))
        node.push("roleplay_end", "rp-cafe", "Role-play finished: Kavarna\n\n```json\n{\"scenario_id\": \"kavarna\", \"turns\": 2}\n```")
        t.backgroundCheck()
        assertEquals(null, t.store.roleplay("rp-cafe"))
        assertEquals(3, node.tools.count { it.first == "reply" })
    }

    @Test fun `a turn that answers as text, that text goes out through the bridge's reply`() = runBlocking {
        val t = ready()
        script = { _, _ -> message("end_turn", text("Kar takole: peč.")) }
        node.push("chat", "main", "Kako se reče stove?")
        t.backgroundCheck()
        val sent = node.tools.single()
        assertEquals("reply", sent.first)
        assertEquals("Kar takole: peč.", sent.second["args"]!!.jsonObject["text"]!!.jsonPrimitive.content)
        assertTrue(t.today.turns.single().replyFromText)
    }

    @Test fun `a node without the tutor session says it needs an update, and stays off`() = runBlocking {
        val t = tutor()
        t.refreshCredentials()
        assertEquals(null, t.setKey(FAKE_KEY))
        node.old = true
        val said = t.setOn(true)
        assertNotNull(said)
        assertTrue(said!!.contains("update"))
        assertEquals(LiveState.OFF, t.state)
        assertFalse(t.settings.on)
    }

    @Test fun `a tool the node lacks says so, nothing sent`() = runBlocking {
        val t = ready()
        node.lacking += "learner_state"
        // the session was read at attach: turn it off and on again for the new list
        t.setOn(false)
        t.setOn(true)
        node.push("chat", "main", "Živjo")
        t.backgroundCheck()
        assertFalse(node.tools.any { it.first == "learner_state" })
        val rec = t.today.turns.single().toolCalls.first { it.name == "learner_state" }
        assertTrue(rec.isError && rec.result.contains("needs an update"))
    }

    @Test fun `on a GPT-6 day thread a turn at another effort carries a configuration_update, the thread's top level kept`() = runBlocking {
        // the reasoning guide, "Change reasoning mid-conversation": {"type": "configuration_update", "reasoning": {"effort": …}}
        // before the next user message, GPT-6 models; the request-level reasoning stays (the prompt cache holds)
        val responses = si.lanisce.lani.tutor.ScriptedResponses { body, n ->
            val input = si.lanisce.lani.tutor.inputOf(body)
            if (input.last().str("type") == "function_call_output") listOf(si.lanisce.lani.tutor.messageItem("m$n", "Opravljeno."))
            else {
                val conv = if (input.toString().contains("session_end")) "main" else "main"
                listOf(si.lanisce.lani.tutor.functionCall("f$n", "call_$n", "reply", obj("conversation_id" to conv, "text" to "Odgovor $n")))
            }
        }
        val creds = si.lanisce.lani.tutor.OpenAICredentials(
            KeyVault(SoftCipher(), MemoryStore()),
            si.lanisce.lani.tutor.ChatGPTAuth(KeyVault(SoftCipher(), MemoryStore()), OkHttpClient(), clock = { now }),
        )
        val scope = CoroutineScope(SupervisorJob())
        scopes += scope
        val t = LiveTutor(
            scope, LiveStore(dir), vault, openai = creds,
            bridgeConfig = { BridgeConfig("http://node.test:8790", BRIDGE_TOKEN) },
            debug = false,
            openaiTransportFor = { bearer -> si.lanisce.lani.tutor.OpenAITransport(bearer, responses.client, clock = { now }) },
            bridgeFor = { c -> LiveBridge(c, OkHttpClient.Builder().addInterceptor(node).build()) },
            fran = null, clock = { now }, zone = { zone }, device = "test phone", logLine = { lines += it }, sleep = {},
            io = Executors.newSingleThreadExecutor().asCoroutineDispatcher(), texts = { key, _ -> key },
        )
        t.refreshCredentials()
        assertEquals(null, t.setOpenAIKey("sk-proj-livetest-0123456789abcdefABCDEF"))
        t.update { it.copy(provider = si.lanisce.lani.tutor.ProviderId.OPENAI, preset = si.lanisce.lani.tutor.Preset.BEST, efforts = mapOf(si.lanisce.lani.tutor.Task.GRADING to "high"), reactAtOnce = true) }.join()
        assertEquals(null, t.setOn(true))
        node.push("chat", "main", "Živjo")
        t.backgroundCheck()
        node.push("session_end", "main", "Session done.\n\n```json\n{\"results\": []}\n```")
        t.backgroundCheck()
        val posts = responses.posts()
        // Best on OpenAI: GPT-6.1 Sol for chat and grading (the matrix: Astra scored no higher), so one thread
        assertTrue(posts.all { it.str("model") == "gpt-6.1-sol" })
        val grading = posts.last { si.lanisce.lani.tutor.inputOf(it).toString().contains("session_end") }
        assertEquals("the thread's level stays at the top", "medium", (grading["reasoning"] as JsonObject).str("effort"))
        val input = si.lanisce.lani.tutor.inputOf(grading)
        val update = input.indexOfFirst { it.str("type") == "configuration_update" }
        assertTrue("a configuration_update in the input", update >= 0)
        assertEquals("high", (input[update]["reasoning"] as JsonObject).str("effort"))
        assertEquals("right before the turn's user message", "user", input[update + 1].str("role"))
        assertEquals(TurnKind.DAY, t.today.turns.last().kind)
        assertEquals("high", t.today.turns.last().effort)
    }

    // --- the matrix's fixes (2026-10-26) ---------------------------------------------------------------------------------

    /** The conversation of the turn's event (the last user message that isn't tools' results). */
    private fun conversationOf(body: JsonObject): String =
        Regex("""conversation_id=\\"([\w-]+)""").find(body["messages"]!!.jsonArray.map { it.jsonObject }.last { m -> m.role() == "user" && m.blocks().none { it.str("type") == "tool_result" } }.toString())?.groupValues?.get(1) ?: "main"

    private fun answered(body: JsonObject): Boolean = body["messages"]!!.jsonArray.last().jsonObject.blocks().any { it.str("type") == "tool_result" }

    private suspend fun until(what: String, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!ok()) {
            assertTrue("waited for: $what", System.currentTimeMillis() < end)
            kotlinx.coroutines.delay(20)
        }
    }

    @Test fun `offline the queue waits for the network - no call, no try counted - and answers once it's back`() = runBlocking {
        val t = ready()
        net.set(false)
        val id = node.push("chat", "main", "Kako se reče stove?")
        t.backgroundCheck()
        until("the offline state") { t.state == LiveState.OFFLINE }
        assertEquals("no call while offline", 0, posts().size)
        assertTrue("the chat says it waits", notes.contains("tutorLive.note.offline"))
        assertTrue(node.acked.isEmpty())
        net.set(true)
        t.awaitIdle()
        t.backgroundCheck()
        assertEquals(3, posts().size)
        val turn = t.today.turns.single()
        assertEquals(TurnStatus.DONE, turn.status)
        assertEquals(1, turn.attempts)
        assertEquals(listOf(id), node.acked)
    }

    @Test fun `a network error as the phone goes offline - back to the queue untried, run once it's back`() = runBlocking {
        val t = ready()
        failOn = { n -> if (n == 1) { net.set(false); throw java.io.IOException("Unable to resolve host \"api.anthropic.com\"") } else null }
        node.push("chat", "main", "Kako se reče stove?")
        t.backgroundCheck()
        until("the offline state") { t.state == LiveState.OFFLINE }
        assertEquals("the failed call, not retried while offline", 1, calls)
        assertTrue(t.today.turns.isEmpty())
        net.set(true)
        t.awaitIdle()
        val turn = t.today.turns.single()
        assertEquals(TurnStatus.DONE, turn.status)
        assertEquals("the offline try isn't counted", 1, turn.attempts)
        assertEquals(1 + 3, calls)
    }

    @Test fun `a score goes on an answer's reply only - dropped on a chat's, the model told`() = runBlocking {
        val t = ready()
        script = { body, n ->
            if (answered(body)) message("end_turn", text("ok"))
            else message("tool_use", toolUse("r$n", "reply", obj("conversation_id" to conversationOf(body), "text" to "Bravo!", "data" to obj("score" to 9, "severity" to "🟢", "plan" to true))))
        }
        node.push("chat", "main", "Kako se reče stove?")
        t.backgroundCheck()
        val chat = node.tools.single { it.first == "reply" }.second["args"]!!.jsonObject
        assertEquals(obj("plan" to true), chat["data"])
        assertTrue(t.today.turns.single().toolCalls.single { it.name == "reply" }.result.contains("data.score dropped"))
        node.push("answer", "ex-7", "Grade this.\n\n```json\n{\"prompt\": \"Opiši sobo\", \"answer\": \"Soba je velika.\"}\n```")
        t.backgroundCheck()
        val answer = node.tools.last { it.first == "reply" }.second["args"]!!.jsonObject
        assertEquals("ex-7", answer["conversation_id"]!!.jsonPrimitive.content)
        assertEquals("9", answer["data"]!!.jsonObject["score"]!!.jsonPrimitive.content)
    }

    @Test fun `a session's end replies only once record_session ran`() = runBlocking {
        val t = ready()
        t.update { it.copy(reactAtOnce = true) }.join()
        script = { _, n ->
            when {
                n == 1 -> message("tool_use", toolUse("r$n", "reply", obj("conversation_id" to "main", "text" to "Shranjeno.")))
                node.tools.none { it.first == "record_session" } -> message("tool_use", toolUse("rec$n", "record_session", obj("session_id" to "session-042", "date" to "2026-10-19", "duration_minutes" to 5)))
                node.tools.none { it.first == "reply" } -> message("tool_use", toolUse("r$n", "reply", obj("conversation_id" to "main", "text" to "Shranjeno.")))
                else -> message("end_turn", text("ok"))
            }
        }
        node.push("session_end", "main", "Session done.\n\n```json\n{\"results\": []}\n```")
        t.backgroundCheck()
        assertEquals("persisted, then the reply", listOf("record_session", "reply"), node.tools.map { it.first })
        val early = t.today.turns.single().toolCalls.first()
        assertTrue(early.isError && early.result.contains("record_session"))
    }

    // --- helpers ------------------------------------------------------------------------------------------------------

    /** The paired bridge's tutor session, played (companion/bridge/src/features/tutor.ts's routes). */
    private inner class FakeNode : Interceptor {
        val requests: MutableList<Request> = Collections.synchronizedList(mutableListOf())
        private val inbox = mutableListOf<JsonObject>()
        private var next = 1_000L
        @Volatile var attached = false
        @Volatile var device: String? = null
        @Volatile var old = false
        @Volatile var returned = 0
        val acked: MutableList<Long> = Collections.synchronizedList(mutableListOf())
        val tools: MutableList<Pair<String, JsonObject>> = Collections.synchronizedList(mutableListOf())
        val lacking = mutableSetOf<String>()

        @Synchronized fun push(kind: String, conversation: String, content: String): Long {
            val id = next++
            inbox += obj("id" to id, "content" to content, "meta" to obj("kind" to kind, "conversation_id" to conversation, "msg_id" to "m$id"), "at" to now)
            return id
        }

        private val TOOLS = listOf("channel_guide", "reply", "get_village", "recent_answers", "list_packs", "list_grammar", "get_grammar", "publish_gloss", "publish_sentence_note", "set_rhythm")
        private val APP_TOOLS = listOf("learner_state", "record_session", "card", "notebook_note", "notebook_write", "report_problem")

        @Synchronized override fun intercept(chain: Interceptor.Chain): Response {
            val r = chain.request()
            requests += r
            val path = r.url.encodedPath
            fun ok(body: String) = Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(200).message("ok").body(body.toResponseBody("application/json".toMediaType())).build()
            fun code(c: Int) = Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(c).message("x").body("""{"error":"x"}""".toResponseBody("application/json".toMediaType())).build()
            val status = { obj("attached" to if (attached) obj("who" to "app", "since" to "2026-10-19T08:00:00Z") else null, "inbox" to obj("size" to inbox.size, "last_id" to next - 1)) }
            fun names(l: List<String>) = JsonArray(l.filter { it !in lacking }.map { obj("name" to it, "description" to "", "inputSchema" to obj("type" to "object")) })
            val body = bodyOf(r).takeIf { it.isNotBlank() }?.let { tutorJson.parseToJsonElement(it) as JsonObject }
            return when {
                old && path.startsWith("/tutor/") -> code(404)
                path == "/tutor/session" -> ok(JsonObject(status() + mapOf("protocol" to JsonPrimitive(1), "instructions" to JsonPrimitive("the channel's rules"), "tools" to names(TOOLS), "app_tools" to names(APP_TOOLS))).toString())
                path == "/tutor/attach" -> {
                    attached = true
                    device = body?.str("device")
                    ok(JsonObject(status() + ("ok" to JsonPrimitive(true))).toString())
                }
                path == "/tutor/detach" -> {
                    attached = false
                    returned = inbox.size
                    inbox.clear()
                    ok(obj("ok" to true, "returned" to returned).toString())
                }
                path == "/tutor/inbox" -> {
                    val after = r.url.queryParameter("after")?.toLong() ?: 0
                    ok(JsonObject(status() + ("items" to JsonArray(if (attached) inbox.filter { it["id"]!!.jsonPrimitive.content.toLong() > after } else emptyList()))).toString())
                }
                path == "/tutor/inbox/ack" -> {
                    val ids = body!!["ids"]!!.jsonArray.map { it.jsonPrimitive.content.toLong() }
                    acked += ids.filter { it !in acked }
                    inbox.removeAll { it["id"]!!.jsonPrimitive.content.toLong() in ids }
                    ok(JsonObject(status() + ("ok" to JsonPrimitive(true))).toString())
                }
                path.startsWith("/tutor/tools/") -> {
                    val name = path.removePrefix("/tutor/tools/")
                    tools += name to body!!
                    val text = when (name) {
                        "reply" -> "delivered"
                        "learner_state" -> """{"computed":{"next_session_id":"session-042","today":"2026-10-19"},"learner":{"name":"Jan"}}"""
                        else -> "done"
                    }
                    ok(obj("content" to JsonArray(listOf(obj("type" to "text", "text" to text)))).toString())
                }
                path == "/tutor/notebook" -> ok(obj("text" to "# Notebook\n- Jan likes tables", "updated_at" to "2026-10-18T20:00:00Z").toString())
                path == "/lookup" -> ok("""{"word":"peč","meanings":["stove"]}""")
                else -> code(404)
            }
        }
    }

    private companion object {
        const val BRIDGE_TOKEN = "bridge-token-live-test-0123456789"
    }
}
