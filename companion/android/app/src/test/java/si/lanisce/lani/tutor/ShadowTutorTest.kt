package si.lanisce.lani.tutor

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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.BridgeConfig
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.Executors

/**
 * The shadow tutor end to end, over the real client (its SSE parsing) and a scripted Messages API, and a fake paired
 * bridge: events to turns, the digest, the thread and its restart, the day's end, the queue's resume, the budget, the
 * export; and its guards: only GETs reach the bridge, the key only the API's x-api-key, never a log line or the export.
 */
class ShadowTutorTest {
    private val zone = ZoneId.of("Europe/Ljubljana")
    private val dir: File = Files.createTempDirectory("shadow-test").toFile()
    private var now = LocalDateTime.of(2026, 10, 14, 18, 0).atZone(zone).toInstant().toEpochMilli()
    private val lines = mutableListOf<String>()
    private val vault = KeyVault(SoftCipher(), MemoryStore())
    private val scopes = mutableListOf<CoroutineScope>()

    /** What the scripted API answers; by default a small tutor (as the bun mock: guide + learner_state, then reply, then done). */
    private var script: (JsonObject, Int) -> AssistantMessage = ::tutorTurn
    private var usage = Usage(input = 100, cacheWrite1h = 4_000, output = 80)
    /** A call answered with this (status, body, type) instead of the script's, by its number. */
    private var failOn: ((Int) -> Triple<Int, String, String>?) = { null }
    private var calls = 0
    /** The chat's photos on the phone, by id (ChatPhotoStore's files), for the shadow's image blocks. */
    private val photos = mutableMapOf<String, File>()
    /** What GET /v1/models lists. */
    private var modelsJson = """{"data":[{"id":"claude-opus-5-5","created_at":"2026-10-01"},{"id":"claude-sonnet-5-5","created_at":"2026-10-01"},{"id":"claude-haiku-5-5","created_at":"2026-10-07"}],"has_more":false}"""

    /** A Messages API request as sent: its body and its anthropic-beta header. */
    private data class Sent(val body: JsonObject, val beta: String?) {
        val effort: String? get() = (body["output_config"] as? JsonObject)?.get("effort")?.jsonPrimitive?.content
        val messages: List<JsonObject> get() = body["messages"]!!.jsonArray.map { it.jsonObject }
    }

    private fun posts(): List<Sent> = api.requests.filter { it.method == "POST" }.map { r ->
        Sent(tutorJson.parseToJsonElement(Buffer().also { r.body!!.writeTo(it) }.readUtf8()) as JsonObject, r.header("anthropic-beta"))
    }

    private val api = Answering { r -> answerApi(r) }

    /** The matrix's lanes' API (their own client): what it answers, its usage, a gate that holds every lane's call. */
    private var laneScript: (JsonObject, Int) -> AssistantMessage = ::tutorTurn
    private var laneUsage = Usage(input = 100, cacheWrite1h = 4_000, output = 80)
    @Volatile private var laneGate: java.util.concurrent.CountDownLatch? = null
    private val laneCalls = java.util.concurrent.atomic.AtomicInteger()
    private val laneApi = Answering { r ->
        if (r.url.encodedPath == "/v1/models") return@Answering Triple(200, modelsJson, "application/json")
        laneGate?.await(20, java.util.concurrent.TimeUnit.SECONDS)
        val o = tutorJson.parseToJsonElement(Buffer().also { r.body!!.writeTo(it) }.readUtf8()) as JsonObject
        val m = laneScript(o, laneCalls.incrementAndGet())
        Triple(200, sseOf(m.copy(usage = laneUsage), (o["model"] as JsonPrimitive).content), "text/event-stream")
    }

    private fun lanePosts(): List<Sent> = synchronized(laneApi.requests) { laneApi.requests.toList() }.filter { it.method == "POST" }.map { r ->
        Sent(tutorJson.parseToJsonElement(Buffer().also { r.body!!.writeTo(it) }.readUtf8()) as JsonObject, r.header("anthropic-beta"))
    }
    private val bridgeHttp = Answering { r ->
        when (r.url.encodedPath) {
            "/state" -> Triple(200, STATE, "application/json")
            "/game" -> Triple(200, """{"rev":3,"state":{"age":"ZASELEK","resources":{"food":12},"buildings":[{"type":"LIPA","level":1}]}}""", "application/json")
            else -> Triple(404, """{"error":"not found"}""", "application/json")
        }
    }

    private fun answerApi(r: Request): Triple<Int, String, String> {
        if (r.url.encodedPath == "/v1/models") return Triple(200, modelsJson, "application/json")
        val body = Buffer().also { r.body!!.writeTo(it) }.readUtf8()
        val o = tutorJson.parseToJsonElement(body) as JsonObject
        calls++
        failOn(calls)?.let { return it }
        val m = script(o, calls)
        return Triple(200, sseOf(m.copy(usage = if (m.usage == Usage()) Usage() else usage), (o["model"] as JsonPrimitive).content), "text/event-stream")
    }

    // --- OpenAI: an API key and the ChatGPT sign-in against scripted servers ----------------------------------------

    /** What the scripted Responses API answers its lanes and "also run on" turns. */
    private var openaiScript: (JsonObject, Int) -> List<JsonObject> = ::openaiTurn
    private var openaiUsage = openAIUsage(input = 5_000, cached = 0, written = 4_800, output = 120, reasoning = 60)
    /** An OpenAI POST answered with this (status, body, type) instead of the script's, by its number. */
    private var openaiFailOn: ((Int) -> Triple<Int, String, String>?) = { null }
    private val oai = ScriptedResponses(usage = { openaiUsage }, answer = { _, n -> openaiFailOn(n) }) { body, n -> openaiScript(body, n) }
    private val authApi = Answering { r ->
        when (r.url.encodedPath) {
            "/api/accounts/oauth/token" -> Triple(
                200,
                obj(
                    "access_token" to "chatgpt-access-token-1", "refresh_token" to "chatgpt-refresh-token-1", "id_token" to chatgptIdToken(),
                    "token_type" to "Bearer", "expires_in" to 3600, "scope" to "chatgpt.tokens.use.direct email offline_access openid profile resource.invoke",
                ).toString(),
                "application/json",
            )
            "/.well-known/jwks.json" -> Triple(200, signingJwks(), "application/json")
            "/api/accounts/oauth/revoke" -> Triple(200, "", "text/plain")
            else -> Triple(404, "{}", "application/json")
        }
    }
    private val openaiCreds = OpenAICredentials(
        KeyVault(SoftCipher(), MemoryStore()),
        ChatGPTAuth(KeyVault(SoftCipher(), MemoryStore()), OkHttpClient.Builder().addInterceptor(authApi).build(), clock = { now }),
    )
    private val signingKey = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private var chatgptNonce: String? = null

    private fun b64(b: ByteArray) = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    private fun signingJwks(): String {
        val pub = signingKey.public as java.security.interfaces.RSAPublicKey
        fun u(i: java.math.BigInteger) = i.toByteArray().let { if (it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
        return """{"keys":[{"kty":"RSA","kid":"k","n":"${b64(u(pub.modulus))}","e":"${b64(u(pub.publicExponent))}"}]}"""
    }
    private fun chatgptIdToken(): String {
        val head = b64("""{"alg":"RS256","kid":"k"}""".toByteArray())
        val body = b64(obj("iss" to "https://auth.openai.com", "aud" to "oaiapp_9", "sub" to "jan", "email" to "jan@example.com", "exp" to now / 1000 + 3600, "nonce" to chatgptNonce).toString().toByteArray())
        val sig = java.security.Signature.getInstance("SHA256withRSA").run { initSign(signingKey.private); update("$head.$body".toByteArray()); sign() }
        return "$head.$body.${b64(sig)}"
    }

    // --- Gemini and OpenRouter against scripted servers --------------------------------------------------------------

    private var geminiFailOn: ((Int) -> Triple<Int, String, String>?) = { null }
    private val gem = ScriptedGemini(answer = { _, n -> geminiFailOn(n) }) { body, n -> geminiTurn(body, n) }
    private val geminiCreds = GeminiCredentials(KeyVault(SoftCipher(), MemoryStore()), MemoryStore(), GeminiPacer(clock = { now }, sleep = {}))
    private val router = ScriptedRouter { body, n -> routerTurn(body, n) }
    private val routerCreds = OpenRouterCredentials(OpenRouterAuth(KeyVault(SoftCipher(), MemoryStore()), router.client))

    /** "Continue with OpenRouter" through the panel's call, the browser played by a GET to the loopback. */
    private suspend fun signInOpenRouter(t: ShadowTutor): String? = t.signInWithOpenRouter { url ->
        val u = url.toHttpUrl()
        val cb = u.queryParameter("callback_url")!!.toHttpUrl()
        Thread {
            java.net.Socket("127.0.0.1", cb.port).use { sock ->
                sock.getOutputStream().write("GET /auth/openrouter?code=or1&state=${u.queryParameter("state")} HTTP/1.1\r\nHost: 127.0.0.1:${cb.port}\r\n\r\n".toByteArray())
                sock.getInputStream().readBytes()
            }
        }.start()
    }

    /** "Sign in with ChatGPT" through the panel's call, the browser played by a GET to the loopback. */
    private suspend fun signInChatGPT(t: ShadowTutor): String? = t.signInWithChatGPT { url ->
        val u = url.toHttpUrl()
        chatgptNonce = u.queryParameter("nonce")
        Thread {
            java.net.Socket("127.0.0.1", SIGN_IN_PORT).use { sock ->
                sock.getOutputStream().write("GET /auth/callback?code=c1&state=${u.queryParameter("state")}&client_id=oaiapp_9 HTTP/1.1\r\nHost: 127.0.0.1:$SIGN_IN_PORT\r\n\r\n".toByteArray())
                sock.getInputStream().readBytes()
            }
        }.start()
    }

    private fun tutor(): ShadowTutor {
        val scope = CoroutineScope(SupervisorJob())
        scopes += scope
        return ShadowTutor(
            scope, ShadowStore(dir), vault,
            bridgeConfig = { BridgeConfig("http://10.0.2.2:8791", "bridge-token") },
            debug = false,
            transportFor = { base, key -> AnthropicTransport(base, key, OkHttpClient.Builder().addInterceptor(api).build(), clock = { now }) },
            bridgeFor = { c -> ShadowBridge(c, OkHttpClient.Builder().addInterceptor(bridgeHttp).build()) },
            clock = { now },
            zone = { zone },
            learner = { "Jan" },
            where = { "Home" },
            logLine = { lines += it },
            sleep = {},
            io = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
            photo = { photos[it] },
            laneTransportFor = { base, key -> AnthropicTransport(base, key, OkHttpClient.Builder().addInterceptor(laneApi).build(), clock = { now }) },
            openai = openaiCreds,
            openaiTransportFor = { OpenAITransport(it, oai.client, clock = { now }) },
            openaiLaneTransportFor = { OpenAITransport(it, oai.client, clock = { now }) },
            signInPort = SIGN_IN_PORT,
            gemini = geminiCreds,
            geminiTransportFor = { k, p, f -> GeminiTransport(k, gem.client, clock = { now }, pacer = p, onFreeTier = f) },
            geminiLaneTransportFor = { k, p, f -> GeminiTransport(k, gem.client, clock = { now }, pacer = p, onFreeTier = f) },
            openrouter = routerCreds,
            openrouterTransportFor = { OpenRouterTransport(it, router.client, clock = { now }) },
            openrouterLaneTransportFor = { OpenRouterTransport(it, router.client, clock = { now }) },
        )
    }

    @After fun stop() {
        laneGate?.countDown()
        scopes.forEach { it.cancel() }
        dir.deleteRecursively()
    }

    private suspend fun ready(t: ShadowTutor = tutor()): ShadowTutor {
        assertEquals(null, t.setKey(FAKE_KEY))
        t.setEnabled(true).join()
        t.awaitIdle()
        return t
    }

    private fun chat(id: String, text: String) = """{"kind":"chat","conversation_id":"main","text":"$text","client_id":"$id"}"""
    private fun review(id: String, quality: Int) =
        """{"results":[{"item_id":"vocab_kurim","quality":$quality}],"duration_minutes":2,"answers":[{"item_id":"vocab_kurim","asked":"to heat","typed":"kurim"}],"from":"review","client_id":"$id"}"""

    @Test fun `a chat's turn runs at once, its reply stays in the panel, the bridge sees only GETs, the key only the API`() = runBlocking {
        val t = ready()
        t.onDelivered("c1", "/message", chat("c1", "Kako se reče stove?")).join()
        t.awaitIdle()

        val day = t.today
        val e = day.events.single()
        assertEquals("chat", e.kind)
        assertEquals(Handling.AT_ONCE, e.handling)
        val turn = day.turns.single()
        assertEquals(TurnStatus.DONE, turn.status)
        assertEquals(TurnKind.DAY, turn.kind)
        assertEquals("claude-sonnet-5-5", turn.model)
        assertEquals("main", turn.replies.single().conversationId)
        assertEquals(listOf("guide", "learner_state", "reply"), turn.toolCalls.map { it.name })
        assertEquals(3, day.calls.size)
        assertEquals(Usage(input = 100, cacheWrite1h = 4_000, output = 80), day.calls[0].usage)
        assertEquals(0.017, day.calls[0].cost!!, 1e-9)
        assertEquals(mapOf("feedback" to 1), day.guide)

        // the thread: one per day, the notebook first
        val th = t.thread!!
        assertEquals("2026-10-14", th.day)
        assertTrue(th.messages.first().toString().contains("<notebook>"))
        assertTrue(th.lines.single().contains("chat (claude-sonnet-5-5)"))

        // the bridge: GETs only (learner_state's /state), with the app's token, never the key
        assertTrue(bridgeHttp.requests.isNotEmpty())
        assertTrue(bridgeHttp.requests.all { it.method == "GET" && it.header("x-api-key") == null && it.url.encodedPath == "/state" })
        // the API: every request with the key in x-api-key, never in its URL or body
        assertTrue(api.requests.all { it.url.host == "api.anthropic.com" && it.header("x-api-key") == FAKE_KEY })
        assertTrue(api.requests.none { r -> r.url.toString().contains(FAKE_KEY) || (r.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8().contains("SHADOWTESTKEY") } ?: false) })
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.none { it.contains("SHADOWTESTKEY") })

        // the real tutor's reply, matched to the event
        t.onRealReply("main", "Peč · stove", null).join()
        assertEquals(e.id, t.today.real.single().eventId)

        // the export: everything, without the key or the bridge's token
        val f = t.export(File(dir, "shared"), "test")
        val text = f.readText()
        assertTrue(text.contains("lani.shadow-export/v0"))
        assertTrue(text.contains("Kako se reče stove?"))
        assertTrue(text.contains("Peč · stove"))
        assertFalse(text.contains("SHADOWTESTKEY"))
        assertFalse(text.contains("bridge-token"))
    }

    @Test fun `routine events wait for the digest, and a digest with nothing to say makes no call`() = runBlocking {
        val t = ready()
        t.onDelivered("r1", "/reviews", review("r1", 5)).join()
        t.onDelivered("w1", "/words", """{"sl":"peč","en":"stove","client_id":"w1"}""").join()
        t.awaitIdle()
        assertEquals(0, calls)
        assertEquals(listOf("review_done", "words_added"), t.today.events.map { it.kind })
        t.onChatOpened().join()
        t.awaitIdle()
        assertEquals(0, calls)
        assertEquals(TurnStatus.SKIPPED, t.today.turns.single().status)

        t.onDelivered("r2", "/reviews", review("r2", 1)).join()
        t.onChatOpened().join()
        t.awaitIdle()
        val digest = t.today.turns.last()
        assertEquals("digest", digest.eventKind)
        assertEquals(lines.joinToString("\n"), TurnStatus.DONE, digest.status)
        assertEquals(Task.ROUTINE, digest.task)
        // Balanced: Haiku for routine, a stateless call (the day's thread is Sonnet's)
        assertEquals("claude-haiku-5-5", digest.model)
        assertEquals(TurnKind.STATELESS, digest.kind)
        assertTrue(t.today.calls.all { it.thread == "stateless" })
        // its reply is a line in the day's thread, for the main tutor's next message
        assertTrue(t.thread!!.pending.single().contains("digest (claude-haiku-5-5)"))
        // recent_answers reads the reviews the shadow saw
        val answers = Summaries.recentAnswers(t.today.events, obj("wrong_only" to true))
        assertTrue(answers, answers.contains("vocab_kurim") && answers.startsWith("1 answer"))
    }

    @Test fun `react at once gives reviews a turn of their own`() = runBlocking {
        val t = ready()
        t.update { it.copy(reactAtOnce = true) }.join()
        t.onDelivered("r1", "/reviews", review("r1", 5)).join()
        t.awaitIdle()
        assertEquals(Handling.AT_ONCE, t.today.events.single().handling)
        assertEquals("review_done", t.today.turns.single().eventKind)
    }

    @Test fun `the queue survives the app being killed, and the turn resumes where it stopped`() = runBlocking {
        val first = ready()
        // the second call: Android ends the app (the process dies mid-turn)
        failOn = { n -> if (n == 2) { scopes.first().cancel(); Thread.sleep(300); Triple(500, "{}", "application/json") } else null }
        first.onDelivered("c1", "/message", chat("c1", "Zakaj?")).join()
        val until = System.currentTimeMillis() + 5_000
        while (calls < 2 && System.currentTimeMillis() < until) delay(10)
        delay(500)
        scopes.forEach { it.cancel() }
        val queued = ShadowStore(dir).queue().single()
        assertEquals(TurnStatus.RUNNING, queued.status)
        assertEquals(1, queued.callIds.size)
        val saved = ShadowStore(dir).thread()!!.messages
        assertEquals(listOf("user", "assistant", "user"), saved.map { it.role() }) // the first step, its tools' results

        failOn = { null }
        val bodiesBefore = calls
        val second = tutor()
        second.awaitIdle()
        delay(100)
        second.awaitIdle()
        val turn = second.today.turns.single()
        assertEquals(TurnStatus.DONE, turn.status)
        assertEquals("main", turn.replies.single().conversationId)
        // the saved step wasn't asked again: the resumed call sent it back as it was
        assertTrue(calls - bodiesBefore <= 2)
        assertEquals(listOf("user", "assistant", "user", "assistant", "user", "assistant"), second.thread!!.messages.map { it.role() })
    }

    @Test fun `past its limit the thread restarts, the notebook written, a new thread with it first`() = runBlocking {
        val t = ready()
        t.update { it.copy(threadLimit = 3_000) }.join()
        t.onDelivered("c1", "/message", chat("c1", "Živjo!")).join()
        t.awaitIdle()
        val closing = t.today.turns.single { it.kind == TurnKind.CLOSING }
        assertEquals("restart", closing.eventKind)
        assertEquals(TurnStatus.DONE, closing.status)
        assertTrue(t.notebook.text.startsWith("# Notebook"))
        assertEquals(listOf(4_100L), t.today.restarts)
        // a closing writes the notebook and says nothing: its text is never taken as a reply
        assertTrue(closing.replies.isEmpty() && !closing.replyFromText)
        val th = t.thread!!
        assertEquals(1, th.messages.size)
        val first = th.messages.single().toString()
        assertTrue(first.contains("# Notebook") && first.contains("restarted at 4100") && first.contains("Živjo!"))
        assertEquals(listOf(4_100L), th.restarts)

        // the next event joins the restarted thread's first message (it ends with the learner's turn)
        t.onDelivered("c2", "/message", chat("c2", "Kaj pa peč?")).join()
        t.awaitIdle()
        val sent = (api.requests.last().let { r -> tutorJson.parseToJsonElement(Buffer().also { r.body!!.writeTo(it) }.readUtf8()) } as JsonObject)
        val firstSent = sent["messages"]!!.jsonArray.first().toString()
        assertTrue(firstSent.contains("restarted at") && firstSent.contains("Kaj pa peč?"))
    }

    @Test fun `a new day closes yesterday's thread with the notebook, then starts today's with it`() = runBlocking {
        val t = ready()
        t.onDelivered("c1", "/message", chat("c1", "Dober večer")).join()
        t.awaitIdle()
        now = LocalDateTime.of(2026, 10, 15, 9, 0).atZone(zone).toInstant().toEpochMilli()
        t.onDelivered("c2", "/message", chat("c2", "Dobro jutro")).join()
        t.awaitIdle()
        val closing = t.dayLog("2026-10-15").turns.single { it.kind == TurnKind.CLOSING }
        assertEquals("closing", closing.eventKind)
        assertTrue(File(dir, "threads/2026-10-14.json").isFile)
        val th = t.thread!!
        assertEquals("2026-10-15", th.day)
        val first = th.messages.first().toString()
        assertTrue(first.contains("# Notebook") && first.contains("Yesterday in brief") && first.contains("Dobro jutro"))
    }

    @Test fun `a thread made under another prefix (an app update) restarts as text, its history never edited`() = runBlocking {
        val store = ShadowStore(dir)
        val old = ThreadState(
            day = "2026-10-14", model = "claude-sonnet-5-5", prefix = "0123456789abcdef", tokens = 9_000,
            messages = listOf(userText("<notebook/> <event>Prej</event>"), TutorLoop.assistant(listOf(thinking("old-sig"), text("Prej.")))),
            lines = listOf("17:00 chat (claude-sonnet-5-5): «Prej.»"),
        )
        store.saveThread(old)
        val t = ready()
        t.onDelivered("c1", "/message", chat("c1", "Po posodobitvi")).join()
        t.awaitIdle()
        val th = t.thread!!
        assertTrue(th.prefix != old.prefix)
        assertEquals(listOf(9_000L), t.today.restarts)
        // the old thread's turns aren't sent again: the first message is the restart's, as text
        val sent = tutorJson.parseToJsonElement(Buffer().also { api.requests.first { it.method == "POST" }.body!!.writeTo(it) }.readUtf8()) as JsonObject
        val first = sent["messages"]!!.jsonArray.first().toString()
        assertTrue(first.contains("The app was updated") && first.contains("Po posodobitvi"))
        assertFalse(sent.toString().contains("old-sig"))
    }

    @Test fun `another key restarts the thread as text (its thinking was made under the old one)`() = runBlocking {
        val t = ready()
        t.onDelivered("c1", "/message", chat("c1", "Prvi")).join()
        t.awaitIdle()
        assertEquals(null, t.setKey("sk-ant-api03-ANOTHERKEY0000000000"))
        t.onDelivered("c2", "/message", chat("c2", "Drugi")).join()
        t.awaitIdle()
        val first = t.thread!!.messages.first().toString()
        assertTrue(first, first.contains("The key changed") && first.contains("Drugi"))
        assertEquals(1, t.today.restarts.size)
    }

    @Test fun `the day's budget pauses the shadow`() = runBlocking {
        val t = ready()
        t.update { it.copy(budgetPerDay = 0.01) }.join()
        t.onDelivered("c1", "/message", chat("c1", "Ena")).join()
        t.awaitIdle()
        assertEquals(1, calls) // 0.017 after the first call: the next is never made
        assertTrue(t.status, t.status.startsWith("paused: today's budget"))
        assertEquals(1, t.queued.size)
    }

    @Test fun `also run on another model, its own call, metered`() = runBlocking {
        val t = ready()
        t.onDelivered("c1", "/message", chat("c1", "Kako si?")).join()
        t.awaitIdle()
        val e = t.today.events.single()
        t.alsoRun(e.id, "claude-haiku-5-5").join()
        t.awaitIdle()
        val also = t.today.turns.single { it.kind == TurnKind.ALSO }
        assertEquals("claude-haiku-5-5", also.model)
        assertEquals(TurnStatus.DONE, also.status)
        assertTrue(t.today.calls.any { it.thread == "also" && it.model == "claude-haiku-5-5" })
        assertEquals(2, t.today.turns.filter { e.id in it.eventIds }.size)
    }

    @Test fun `a refused key keeps the events queued until a new one`() = runBlocking {
        val t = ready()
        failOn = { _ -> Triple(401, """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}""", "application/json") }
        t.onDelivered("c1", "/message", chat("c1", "Halo")).join()
        t.awaitIdle()
        assertTrue(t.settings.keyRefused)
        assertEquals(TurnStatus.QUEUED, t.queued.single().status)
        failOn = { null }
        assertEquals(null, t.recheck())
        t.awaitIdle()
        assertTrue(t.queued.isEmpty())
        assertEquals(TurnStatus.DONE, t.today.turns.single().status)
    }

    @Test fun `off, it sees nothing`() = runBlocking {
        val t = tutor()
        t.onDelivered("c1", "/message", chat("c1", "Halo")).join()
        t.awaitIdle()
        assertTrue(t.today.events.isEmpty())
        assertEquals(0, calls)
    }

    @Test fun `with the developer setting on, the raw usage of a thread's first call and of every 20th is kept`() = runBlocking {
        val t = ready()
        t.showDeveloper(true).join()
        for (i in 1..7) {
            t.onDelivered("c$i", "/message", chat("c$i", "Vprašanje $i")).join()
            t.awaitIdle()
        }
        val day = t.today.calls
        assertEquals(21, day.size) // seven chats of three calls, all on the day's thread
        val kept = day.withIndex().filter { it.value.usageRaw != null }.map { it.index + 1 }
        assertEquals(listOf(1, 20), kept)
        // as the API sent them: message_start's usage with its breakdown by lifetime, message_delta's
        val raw = day[0].usageRaw!!
        assertEquals("4000", raw.start!!["cache_creation"]!!.jsonObject["ephemeral_1h_input_tokens"]!!.jsonPrimitive.content)
        assertEquals("100", raw.start!!["input_tokens"]!!.jsonPrimitive.content)
        assertEquals("80", raw.delta!!["output_tokens"]!!.jsonPrimitive.content)
        // every call says how its cache writes came, and the day counts them
        assertTrue(day.all { it.cacheReport == RawUsage.BREAKDOWN })
        val stats = t.stats()
        assertEquals(21, stats.cacheBreakdown)
        assertEquals(0, stats.cacheTotalOnly)
        // the export (the share sheet's, the node's) has them
        val text = t.exportText("test")
        assertTrue(text.contains("usageRaw") && text.contains("ephemeral_1h_input_tokens") && text.contains("\"cacheBreakdown\":21"))
    }

    @Test fun `without the developer setting no raw usage is kept`() = runBlocking {
        val t = ready()
        t.onDelivered("c1", "/message", chat("c1", "Halo")).join()
        t.awaitIdle()
        assertEquals(3, t.today.calls.size)
        assertTrue(t.today.calls.all { it.usageRaw == null && it.cacheReport == RawUsage.BREAKDOWN })
    }

    @Test fun `a stateless turn is a thread of its own, its first call keeps the raw usage`() = runBlocking {
        val t = ready()
        t.showDeveloper(true).join()
        t.onDelivered("c1", "/message", chat("c1", "Halo")).join()
        t.awaitIdle()
        t.alsoRun(t.today.events.single().id, "claude-haiku-5-5").join()
        t.awaitIdle()
        val also = t.today.calls.filter { it.thread == "also" }
        assertEquals(3, also.size)
        assertTrue(also[0].usageRaw != null && also.drop(1).all { it.usageRaw == null })
    }

    @Test fun `the day's closing tells the node's upload, a restart doesn't`() = runBlocking {
        val t = ready()
        var closed = 0
        t.onDayClosed = { closed++ }
        t.onDelivered("c1", "/message", chat("c1", "Dober večer")).join()
        t.awaitIdle()
        assertEquals(0, closed)
        now = LocalDateTime.of(2026, 10, 15, 9, 0).atZone(zone).toInstant().toEpochMilli()
        t.onDelivered("c2", "/message", chat("c2", "Dobro jutro")).join()
        t.awaitIdle()
        assertEquals(1, closed)
        // a thread past its limit restarts with a closing of its own: not the day's
        t.update { it.copy(threadLimit = 2_000) }.join()
        t.onDelivered("c3", "/message", chat("c3", "Še enkrat")).join()
        t.awaitIdle()
        assertTrue(t.today.turns.any { it.kind == TurnKind.CLOSING && it.eventKind == "restart" })
        assertEquals(1, closed)
    }

    @Test fun `the export for the node has no key, no token, no secret given, the oldest days left out to fit`() = runBlocking {
        val t = ready()
        t.onDelivered("c1", "/message", chat("c1", "Moj žeton je NODESECRET12345 in bridge-token")).join()
        t.awaitIdle()
        now = LocalDateTime.of(2026, 10, 15, 18, 0).atZone(zone).toInstant().toEpochMilli()
        t.onDelivered("c2", "/message", chat("c2", "Naslednji dan")).join()
        t.awaitIdle()
        val whole = t.exportText("test", listOf("NODESECRET12345"))
        assertTrue(whole.contains("\"day\":\"2026-10-14\"") && whole.contains("\"day\":\"2026-10-15\"") && whole.contains("Naslednji dan"))
        assertFalse(whole.contains("NODESECRET12345") || whole.contains("bridge-token") || whole.contains("SHADOWTESTKEY"))
        assertFalse(whole.contains("days_left_out"))
        // too large for the node: yesterday goes, today stays, and it says so
        val fit = t.exportText("test", maxBytes = whole.toByteArray().size - 1)
        val o = tutorJson.parseToJsonElement(fit).jsonObject
        assertEquals(listOf("2026-10-14"), o["days_left_out"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("2026-10-15"), o["days"]!!.jsonArray.map { it.jsonObject["day"]!!.jsonPrimitive.content })
        // the share sheet's file is the same content
        assertEquals(t.exportText("test"), t.export(File(dir, "shared"), "test").readText())
    }

    @Test fun `a chat's photo goes to the API as an image block before its event, counted on the call, never in the export`() = runBlocking {
        val jpeg = ByteArray(3_000) { (it * 7).toByte() }.also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte(); it[2] = 0xFF.toByte() }
        val id = "0123456789abcdef0123456789abcdef"
        photos[id] = File(dir, "$id.jpg").apply { writeBytes(jpeg) }
        val t = ready()
        t.onDelivered(id, "/message", """{"kind":"chat","conversation_id":"main","text":"Kaj piše tukaj?","image":{"id":"$id"},"client_id":"$id"}""").join()
        t.awaitIdle()

        val e = t.today.events.single()
        assertEquals(ShadowImage(id, 3_000), e.image)
        // the turn's user message: the image block (base64 JPEG), then the event, whose data says where the photo is
        val first = posts().first()
        val user = first.messages.last { it["role"]!!.jsonPrimitive.content == "user" }["content"]!!.jsonArray.map { it.jsonObject }
        val img = user.indexOfFirst { it["type"]!!.jsonPrimitive.content == "image" }
        assertTrue("an image block: $user", img >= 0)
        val source = user[img]["source"]!!.jsonObject
        assertEquals("base64" to "image/jpeg", source["type"]!!.jsonPrimitive.content to source["media_type"]!!.jsonPrimitive.content)
        val b64 = source["data"]!!.jsonPrimitive.content
        assertEquals(java.util.Base64.getEncoder().encodeToString(jpeg), b64)
        val event = user.drop(img + 1).first { it["type"]!!.jsonPrimitive.content == "text" }["text"]!!.jsonPrimitive.content
        assertTrue(event, event.startsWith("<event kind=\"chat\"") && event.contains("Kaj piše tukaj?") && event.contains("the image block just before this event"))
        // metered as any call; the calls say they carried it
        assertTrue(t.today.calls.isNotEmpty() && t.today.calls.all { it.images == 1 && it.cost != null })
        // the export (the share sheet's, the node's): the photo left out, a note of its size in its place
        val export = t.exportText("test")
        assertFalse(export.contains(b64))
        assertFalse(export.contains(b64.take(40)))
        assertTrue(export.contains("an image, 2 KB, left out"))
        assertTrue(export.contains("\"images\":1"))
    }

    @Test fun `a photo no longer on the phone - the event goes without it, and says so`() = runBlocking {
        val t = ready()
        t.onDelivered("p2", "/message", """{"kind":"chat","conversation_id":"main","text":"In to?","image":{"id":"gone-000000000001"},"client_id":"p2"}""").join()
        t.awaitIdle()
        val all = posts().first().messages.flatMap { it["content"]!!.jsonArray.map { b -> b.jsonObject } }
        assertTrue(all.none { it["type"]!!.jsonPrimitive.content == "image" })
        assertTrue(all.any { it.toString().contains("the photo isn't in this message") })
        assertTrue(t.today.calls.all { it.images == 0 })
    }

    @Test fun `a turn that never calls reply - its text is the reply, marked, counted by model and effort`() = runBlocking {
        // as Haiku 5.5 at low did in Jan's shadow: the answer as text, end_turn, no reply call
        script = { _, _ -> message("end_turn", thinking(), text("**Peč** · stove. Odlično!")) }
        val t = ready()
        t.onDelivered("c1", "/message", chat("c1", "Kako se reče stove?")).join()
        t.awaitIdle()
        val turn = t.today.turns.single()
        assertEquals(TurnStatus.DONE, turn.status)
        assertTrue(turn.replyFromText)
        assertTrue(turn.toolCalls.isEmpty())
        val reply = turn.replies.single()
        assertEquals("main" to "**Peč** · stove. Odlično!", reply.conversationId to reply.text)
        val stats = t.stats()
        assertEquals(1, stats.repliesFromText)
        assertEquals(1, stats.byModelEffort.getValue("claude-sonnet-5-5 · medium").repliesFromText)
        assertEquals(0, stats.byModelEffort.getValue("claude-sonnet-5-5 · medium").repliesByTool)
        // the thread's line has it, the log says so, the export carries the mark and the count
        assertTrue(t.thread!!.lines.single().contains("Peč"))
        assertTrue(lines.any { it.contains("no reply tool called") })
        val text = t.exportText("test")
        assertTrue(text.contains("\"replyFromText\":true") && text.contains("\"repliesFromText\":1") && text.contains("byModelEffort"))
    }

    @Test fun `Balanced - the routine digest on Haiku at high, with room for its thinking`() = runBlocking {
        val t = ready()
        t.onDelivered("r1", "/reviews", review("r1", 1)).join()
        t.onChatOpened().join()
        t.awaitIdle()
        val digest = t.today.turns.single()
        assertEquals("claude-haiku-5-5" to "high", digest.model to digest.effort)
        val sent = posts()
        assertTrue(sent.isNotEmpty())
        for (r in sent) {
            assertEquals("high", r.effort)
            assertEquals(16_000, r.body["max_tokens"]!!.jsonPrimitive.content.toInt())
            assertEquals("thinking-binding-controls-2026-08-01", r.beta)
        }
        assertTrue(t.today.calls.all { it.effort == "high" && !it.effortByMessage && it.visibleChars != null })
    }

    @Test fun `a turn at another level on the day's thread - an effort message, the beta, the history and the top-level effort as they were`() = runBlocking {
        val t = ready()
        // grading on the thread's model (Sonnet) at high, the chat at medium (Balanced's)
        t.update { it.copy(models = mapOf(Task.GRADING to "claude-sonnet-5-5"), efforts = mapOf(Task.GRADING to "high")) }.join()
        t.onDelivered("c1", "/message", chat("c1", "Živjo")).join()
        t.awaitIdle()
        assertEquals("medium", t.thread!!.effort)
        val chatSent = posts()
        assertEquals(3, chatSent.size)
        assertTrue(chatSent.all { it.beta == "thinking-binding-controls-2026-08-01" && it.effort == "medium" && it.messages.none { m -> Effort.of(m) != null } })
        val afterChat = t.thread!!.messages

        t.onDelivered("a1", "/message", """{"kind":"answer","conversation_id":"ex-1","text":"Jaz sem Jan.","client_id":"a1"}""").join()
        t.awaitIdle()
        val answer = t.today.turns.single { it.eventKind == "answer" }
        assertEquals(TurnKind.DAY, answer.kind)
        assertEquals(TurnStatus.DONE, answer.status)
        assertEquals("high", answer.effort)
        assertEquals("ex-1", answer.replies.single().conversationId)
        val answerSent = posts().drop(3)
        assertEquals(3, answerSent.size)
        for (r in answerSent) {
            assertEquals("medium", r.effort) // the thread's top-level effort: unchanged, so its cache holds
            assertEquals("thinking-binding-controls-2026-08-01,mid-conversation-output-config-2026-07-01", r.beta)
            assertEquals(afterChat, r.messages.take(afterChat.size)) // the history before it: as sent before
            assertEquals(Effort.message("high"), r.messages[afterChat.size])
            assertTrue(r.messages[afterChat.size + 1].toString().contains("Jaz sem Jan."))
        }
        assertTrue(t.today.calls.filter { it.eventKind == "answer" }.all { it.effort == "high" && it.effortByMessage })

        // the next chat: back at the chat's level, by an effort message of its own
        t.onDelivered("c2", "/message", chat("c2", "Hvala")).join()
        t.awaitIdle()
        val last = posts().last()
        assertEquals(listOf("high", "medium"), last.messages.mapNotNull(Effort::of))
        assertEquals("medium", last.effort)
        assertEquals(listOf("medium"), t.today.calls.takeLast(3).map { it.effort }.distinct())
        val stats = t.stats()
        assertEquals(setOf("claude-sonnet-5-5 · high", "claude-sonnet-5-5 · medium"), stats.byModelEffort.keys)
        assertEquals(3, stats.byModelEffort.getValue("claude-sonnet-5-5 · high").calls)
    }

    @Test fun `on a model without per-message effort a turn on the thread runs at the thread's level`() = runBlocking {
        modelsJson = """{"data":[{"id":"claude-sonnet-5","created_at":"2026-08-01"},{"id":"claude-haiku-5-5","created_at":"2026-10-07"}],"has_more":false}"""
        val t = ready()
        t.update { it.copy(models = mapOf(Task.CHAT to "claude-sonnet-5", Task.GRADING to "claude-sonnet-5"), efforts = mapOf(Task.GRADING to "high")) }.join()
        t.onDelivered("c1", "/message", chat("c1", "Živjo")).join()
        t.awaitIdle()
        t.onDelivered("a1", "/message", """{"kind":"answer","conversation_id":"ex-1","text":"Jaz sem Jan.","client_id":"a1"}""").join()
        t.awaitIdle()
        val answer = t.today.turns.single { it.eventKind == "answer" }
        assertEquals(TurnKind.DAY, answer.kind)
        assertEquals("medium", answer.effort) // asked for high, ran at the thread's
        assertTrue(posts().all { it.effort == "medium" && it.beta == "thinking-binding-controls-2026-08-01" && it.messages.none { m -> Effort.of(m) != null } })
    }

    @Test fun `also run on a model at an effort - its own call at that level, its numbers apart`() = runBlocking {
        val t = ready()
        t.onDelivered("c1", "/message", chat("c1", "Kako si?")).join()
        t.awaitIdle()
        val e = t.today.events.single()
        t.alsoRun(e.id, "claude-haiku-5-5", "low").join()
        t.awaitIdle()
        t.alsoRun(e.id, "claude-haiku-5-5", "xhigh").join()
        t.awaitIdle()
        val also = t.today.turns.filter { it.kind == TurnKind.ALSO }
        assertEquals(listOf("low", "xhigh"), also.map { it.effort })
        assertTrue(also[0].why!!.endsWith("claude-haiku-5-5 · low"))
        val haiku = posts().filter { it.body["model"]!!.jsonPrimitive.content == "claude-haiku-5-5" }
        assertEquals(listOf("low", "low", "low", "xhigh", "xhigh", "xhigh"), haiku.map { it.effort })
        assertEquals(32_000, haiku.last().body["max_tokens"]!!.jsonPrimitive.content.toInt())
        val stats = t.stats()
        assertEquals(setOf("claude-haiku-5-5 · low", "claude-haiku-5-5 · xhigh", "claude-sonnet-5-5 · medium"), stats.byModelEffort.keys)
        assertEquals(1, stats.byModelEffort.getValue("claude-haiku-5-5 · low").repliesByTool)
    }

    @Test fun `the effort per task is kept with the settings`() = runBlocking {
        val t = ready()
        t.update { it.copy(efforts = mapOf(Task.ROUTINE to "medium", Task.CONTENT to "max")) }.join()
        t.awaitIdle()
        assertEquals(mapOf(Task.ROUTINE to "medium", Task.CONTENT to "max"), ShadowStore(dir).settings().efforts)
        val again = tutor()
        again.awaitIdle()
        assertEquals(mapOf(Task.ROUTINE to "medium", Task.CONTENT to "max"), again.settings.efforts)
    }

    private fun minutes(n: Long) { now += n * 60_000 }

    /** The text of a request's last user message with new input (not tool results). */
    private fun turnText(s: Sent): String = s.messages.last { m -> m.role() == "user" && m.blocks().none { it.str("type") == "tool_result" } }.toString()

    @Test fun `what the learner was shown in reply goes into the day's thread before their next event, once, cut short`() = runBlocking {
        val t = ready()
        t.onDelivered("c1", "/message", chat("c1", "Kako se reče stove?")).join()
        t.awaitIdle()
        minutes(1)
        val long = "The -o rule (-gO): masculine nouns ending in -o take -a in the genitive. " + "x".repeat(3_000)
        t.onRealReply("main", long, null).join()
        t.onRealReply("rp-1", "A villager's line in a role-play", null).join()
        minutes(1)
        t.onDelivered("c2", "/message", chat("c2", "Kaj pa -gO?")).join()
        t.awaitIdle()

        val first = posts().first()
        assertFalse(first.toString().contains("What the learner was shown"))
        val second = posts().first { turnText(it).contains("Kaj pa -gO?") }
        val text = turnText(second)
        assertTrue(text, text.contains(TurnText.SHOWN.take(40)) && text.contains("The -o rule (-gO)") && text.contains("in_reply_to=\\\"ev_c1\\\""))
        // before the event, in the same message; cut at 1,500 characters; a role-play's line never
        assertTrue(text.indexOf("The -o rule") < text.indexOf("Kaj pa -gO?"))
        assertFalse(text.contains("x".repeat(1_501)))
        assertFalse(text.contains("villager's line"))
        // append-only: the history before it as sent before
        assertEquals(first.messages.take(1), second.messages.take(1))

        // once: the next turn doesn't repeat it
        minutes(1)
        t.onDelivered("c3", "/message", chat("c3", "Hvala")).join()
        t.awaitIdle()
        assertFalse(turnText(posts().first { turnText(it).contains("Hvala") }).contains("The -o rule"))
        assertTrue(t.thread!!.shownUntil > 0)
    }

    @Test fun `a stateless turn gets the day's real replies before its event, never the real answer to its own`() = runBlocking {
        val t = ready()
        t.onDelivered("c1", "/message", chat("c1", "Prvo vprašanje")).join()
        t.awaitIdle()
        minutes(1)
        t.onRealReply("main", "REAL-ONE: odgovor na prvo", null).join()
        minutes(1)
        t.onDelivered("c2", "/message", chat("c2", "Drugo vprašanje")).join()
        t.awaitIdle()
        minutes(1)
        t.onRealReply("main", "REAL-TWO: odgovor na drugo", null).join()
        assertEquals(listOf("ev_c1", "ev_c2"), t.today.real.map { it.eventId })

        val before = posts().size
        t.alsoRun("ev_c2", "claude-haiku-5-5", "high").join()
        t.awaitIdle()
        val also = posts().drop(before).first()
        val text = turnText(also)
        assertTrue(text, text.contains(TurnText.SHOWN_TODAY.take(40)) && text.contains("REAL-ONE") && text.indexOf("Today so far") < text.indexOf("REAL-ONE"))
        assertFalse(text.contains("REAL-TWO"))

        // a digest after both: both, as what the learner saw before it
        minutes(1)
        t.onDelivered("r1", "/reviews", review("r1", 1)).join()
        t.onChatOpened().join()
        t.awaitIdle()
        val digest = turnText(posts().last { it.body["model"]!!.jsonPrimitive.content == "claude-haiku-5-5" && turnText(it).contains("<digest") })
        assertTrue(digest.contains("REAL-ONE") && digest.contains("REAL-TWO"))
    }

    // --- the matrix -------------------------------------------------------------------------------------------------

    private val defaultLanes = listOf(
        "claude-haiku-5-5@medium", "claude-haiku-5-5@high", "claude-haiku-5-5@xhigh", "claude-haiku-5-5@max",
        "claude-sonnet-5-5@low", "claude-sonnet-5-5@medium", "claude-sonnet-5-5@high", "claude-opus-5-5@low", "claude-opus-5-5@medium",
    )

    private suspend fun withMatrix(): ShadowTutor {
        val t = ready()
        t.updateMatrix { it.copy(enabled = true) }.join()
        t.awaitMatrix()
        return t
    }

    private fun laneStore(id: String) = ShadowStore(File(dir, "matrix/$id"))

    private fun answer(id: String, text: String) = """{"kind":"answer","conversation_id":"ex-$id","text":"$text","client_id":"$id"}"""

    @Test fun `the matrix is off by default with the nine lanes preselected, and an old settings file loads`() {
        assertEquals(false, ShadowSettings().matrix.enabled)
        assertEquals(defaultLanes, Lanes.plan(ShadowSettings().matrix, emptyList()).map { it.id })
        // a settings file from before the matrix (and a thread and a turn from before their new fields)
        File(dir, "settings.json").writeText(
            """{"enabled":true,"preset":"BALANCED","models":{},"efforts":{"ROUTINE":"medium"},"budgetPerDay":3.0,"reactAtOnce":true,""" +
                """"listed":[{"id":"claude-sonnet-5-5"}],"listedAt":1,"keyRefused":false,"lowCredit":false,"eveningDigest":"2026-10-17","developer":true}""",
        )
        File(dir, "thread.json").writeText("""{"day":"2026-10-14","model":"claude-sonnet-5-5","messages":[],"pending":[],"tokens":5,"restarts":[],"startedAt":0,"lines":[],"yesterday":[],"prefix":"x","effort":"medium"}""")
        File(dir, "queue.json").writeText("""[{"id":"t_1","eventIds":["e"],"kind":"DAY","task":"CHAT","model":"claude-sonnet-5-5","eventKind":"chat","queuedAt":1}]""")
        val store = ShadowStore(dir)
        val s = store.settings()
        assertTrue(s.enabled && s.reactAtOnce && s.developer)
        assertEquals(mapOf(Task.ROUTINE to "medium"), s.efforts)
        assertEquals(MatrixSettings(), s.matrix)
        assertEquals(0L, store.thread()!!.shownUntil)
        assertEquals(null, store.queue().single().lane)
    }

    @Test fun `each lane gets every event the primary gets, on its own thread at its own model and effort`() = runBlocking {
        val t = withMatrix()
        t.onDelivered("c1", "/message", chat("c1", "Kako se reče stove?")).join()
        t.awaitMatrix()
        t.onDelivered("a1", "/message", answer("a1", "Jaz sem Jan.")).join()
        t.awaitMatrix()
        t.onDelivered("r1", "/reviews", review("r1", 1)).join()
        t.onDelivered("s1", "/message", """{"kind":"session_end","conversation_id":"main","text":"a run ended","client_id":"s1"}""").join()
        t.onChatOpened().join()
        t.awaitMatrix()

        val primary = t.today.turns.filter { it.status != TurnStatus.SKIPPED }
        assertEquals(listOf("chat", "answer", "digest"), primary.map { it.eventKind })
        assertTrue(primary.all { it.lane == null } && t.today.calls.all { it.lane == null })
        val lanes = t.matrix.today
        assertEquals(defaultLanes.toSet(), lanes.keys)
        for ((id, log) in lanes) {
            val (model, effort) = id.split('@')
            assertEquals(id, primary.map { it.eventKind to it.eventIds }, log.turns.map { it.eventKind to it.eventIds })
            assertTrue(id, log.turns.all { it.status == TurnStatus.DONE && it.kind == TurnKind.DAY && it.lane == id && it.model == model && it.effort == effort })
            assertTrue(id, log.turns.all { it.replies.isNotEmpty() })
            assertTrue(id, log.calls.isNotEmpty() && log.calls.all { it.lane == id && it.effort == effort && it.cost != null })
            // one thread for all of them, the notebook first
            val th = laneStore(id).thread()!!
            assertEquals(effort, th.effort)
            val all = th.messages.toString()
            assertTrue(id, th.messages.first().toString().contains("<notebook>") && all.contains("Kako se reče stove?") && all.contains("Jaz sem Jan.") && all.contains("<digest"))
        }
        // every request of a lane: its model, its effort as the top-level effort, room by its level, no effort message
        val sent = lanePosts().groupBy { it.body["model"]!!.jsonPrimitive.content + "@" + it.effort }
        assertEquals(defaultLanes.toSet(), sent.keys)
        for ((id, rs) in sent) {
            val room = when (id.substringAfter('@')) { "max" -> 64_000; "xhigh" -> 32_000; else -> 16_000 }
            assertTrue(id, rs.all { it.body["max_tokens"]!!.jsonPrimitive.content.toInt() == room })
            assertTrue(id, rs.all { it.beta == "thinking-binding-controls-2026-08-01" && it.messages.none { m -> Effort.of(m) != null } })
        }
        // the export carries each lane's log and numbers, and the matrix as resolved
        val o = tutorJson.parseToJsonElement(t.exportText("test")).jsonObject
        val day = o["days"]!!.jsonArray.single().jsonObject
        assertEquals(defaultLanes.toSet(), day["lanes"]!!.jsonObject.keys)
        val lane = day["lanes"]!!.jsonObject.getValue("claude-haiku-5-5@max").jsonObject
        assertEquals(3, lane["log"]!!.jsonObject["turns"]!!.jsonArray.size)
        assertTrue(lane["stats"]!!.jsonObject["spend"]!!.jsonPrimitive.content.toDouble() > 0)
        assertEquals(defaultLanes, o["matrix"]!!.jsonObject["lanes"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content })
        // the primary's own numbers don't count the lanes
        assertEquals(t.today.calls.size, t.stats().calls)
    }

    @Test fun `a lane's notebook calls are dry runs, and the notebook stays the primary's`() = runBlocking {
        laneScript = { body, n ->
            if (!hasToolResult(body)) message("tool_use", toolUse("n$n", "notebook_note", obj("text" to "LANE NOTE")), toolUse("w$n", "notebook_write", obj("text" to "LANE NOTEBOOK")))
            else tutorTurn(body, n)
        }
        val t = withMatrix()
        t.editNotebook("# Notebook\n- the primary's").join()
        t.onDelivered("c1", "/message", chat("c1", "Živjo")).join()
        t.awaitMatrix()
        assertEquals("# Notebook\n- the primary's", t.notebook.text)
        assertEquals("# Notebook\n- the primary's", ShadowStore(dir).notebook().text)
        for ((id, log) in t.matrix.today) {
            val nb = log.turns.single().toolCalls.filter { it.name.startsWith("notebook_") }
            assertEquals(id, listOf("notebook_note", "notebook_write"), nb.map { it.name })
            assertTrue(id, nb.all { it.dryRun })
        }
        // every lane read the primary's notebook
        assertTrue(lanePosts().all { it.messages.first().toString().contains("the primary's") })
    }

    @Test fun `the lanes have no budget of their own and never count toward the primary's`() = runBlocking {
        val t = withMatrix()
        // the primary's budget: two chats of three calls ($0.051 each), never the lanes' spend
        t.update { it.copy(budgetPerDay = 0.10) }.join()
        t.onDelivered("c1", "/message", chat("c1", "Ena")).join()
        t.awaitMatrix()
        t.onDelivered("c2", "/message", chat("c2", "Dva")).join()
        t.awaitMatrix()
        assertEquals(listOf(TurnStatus.DONE, TurnStatus.DONE), t.today.turns.map { it.status })
        assertEquals(6, t.today.calls.size)
        assertTrue(t.matrix.spent() > 0.10)
        // the primary's budget spent: it pauses, the lanes go on
        t.onDelivered("c3", "/message", chat("c3", "Tri")).join()
        t.awaitMatrix()
        assertTrue(t.status, t.status.startsWith("paused: today's budget"))
        assertTrue(t.matrix.today.values.all { l -> l.turns.size == 3 && l.turns.all { it.status == TurnStatus.DONE } })
        assertTrue(t.matrix.status, t.matrix.status.startsWith("on"))
    }

    @Test fun `a lane whose model the key doesn't list is skipped, and says so`() = runBlocking {
        modelsJson = """{"data":[{"id":"claude-sonnet-5-5","created_at":"2026-10-01"},{"id":"claude-haiku-5-5","created_at":"2026-10-07"}],"has_more":false}"""
        val t = withMatrix()
        t.onDelivered("c1", "/message", chat("c1", "Halo")).join()
        t.awaitMatrix()
        val skipped = t.matrix.plan.filter { it.skipped != null }
        assertEquals(listOf("low", "medium"), skipped.map { it.effort })
        assertTrue(skipped.all { it.family == Family.OPUS && it.id == null && it.skipped!!.contains("opus") })
        assertEquals(defaultLanes.filterNot { it.startsWith("claude-opus") }.toSet(), t.matrix.today.keys)
        assertTrue(lanePosts().none { it.body["model"]!!.jsonPrimitive.content.contains("opus") })
        val o = tutorJson.parseToJsonElement(t.exportText("test")).jsonObject
        assertEquals(2, o["matrix"]!!.jsonObject["lanes"]!!.jsonArray.count { it.jsonObject["skipped"] != null })
    }

    @Test fun `the primary's turns never wait behind the lanes`() = runBlocking {
        val t = withMatrix()
        laneGate = java.util.concurrent.CountDownLatch(1) // every lane's call hangs
        t.onDelivered("c1", "/message", chat("c1", "Prvo")).join()
        t.awaitIdle()
        t.onDelivered("c2", "/message", chat("c2", "Drugo")).join()
        t.awaitIdle()
        assertEquals(listOf(TurnStatus.DONE, TurnStatus.DONE), t.today.turns.map { it.status })
        assertEquals(6, t.today.calls.size)
        // the lanes are still on their first call
        assertTrue(t.matrix.today.values.all { it.turns.isEmpty() })
        assertTrue(t.matrix.queued.values.all { q -> q.size == 2 && q.count { it.status == TurnStatus.RUNNING } == 1 })
        laneGate!!.countDown()
        t.awaitMatrix()
        assertTrue(t.matrix.today.values.all { l -> l.turns.map { it.status } == listOf(TurnStatus.DONE, TurnStatus.DONE) })
    }

    @Test fun `what the learner was shown reaches every lane's thread too`() = runBlocking {
        val t = withMatrix()
        t.onDelivered("c1", "/message", chat("c1", "Kako se reče stove?")).join()
        t.awaitMatrix()
        minutes(1)
        t.onRealReply("main", "REAL: peč · stove; the -o rule", null).join()
        minutes(1)
        t.onDelivered("c2", "/message", chat("c2", "In -gO?")).join()
        t.awaitMatrix()
        val second = lanePosts().filter { turnText(it).contains("In -gO?") }.groupBy { it.body["model"]!!.jsonPrimitive.content + "@" + it.effort }
        assertEquals(defaultLanes.toSet(), second.keys)
        for ((id, rs) in second) {
            val text = turnText(rs.first())
            assertTrue(id, text.contains(TurnText.SHOWN.take(40)) && text.contains("REAL: peč") && text.indexOf("REAL: peč") < text.indexOf("In -gO?"))
        }
        assertTrue(lanePosts().filter { turnText(it).contains("Kako se reče stove?") }.none { it.toString().contains("REAL: peč") })
    }

    @Test fun `a lane's thread restarts as text at its family's limit, and a new day archives it without a call`() = runBlocking {
        laneUsage = Usage(input = 100, cacheWrite1h = 4_000, cacheRead = 21_000, output = 80) // 25,100: past Haiku's 20k, under Sonnet's 30k
        val t = withMatrix()
        t.onDelivered("c1", "/message", chat("c1", "Dober večer")).join()
        t.awaitMatrix()
        val callsBefore = laneCalls.get()
        for ((id, log) in t.matrix.today) {
            val th = laneStore(id).thread()!!
            if (id.startsWith("claude-haiku")) {
                assertEquals(id, listOf(25_100L), log.restarts)
                assertEquals(id, 1, th.messages.size)
                assertTrue(id, th.messages.single().toString().contains("restarted at 25100") && th.messages.single().toString().contains("Dober večer"))
            } else assertTrue(id, log.restarts.isEmpty() && th.messages.size > 1)
        }
        assertEquals(callsBefore, laneCalls.get()) // no call for a restart
        // the next day: yesterday's thread archived as it is, today's first message has its lines; no closing turn
        now = LocalDateTime.of(2026, 10, 15, 9, 0).atZone(zone).toInstant().toEpochMilli()
        laneUsage = Usage(input = 100, cacheWrite1h = 4_000, output = 80)
        t.onDelivered("c2", "/message", chat("c2", "Dobro jutro")).join()
        t.awaitMatrix()
        for (id in defaultLanes) {
            assertTrue(id, File(dir, "matrix/$id/threads/2026-10-14.json").isFile)
            val th = laneStore(id).thread()!!
            assertEquals("2026-10-15", th.day)
            assertTrue(id, th.messages.first().toString().contains("Yesterday in brief") && th.messages.first().toString().contains("Dobro jutro"))
            val turns = t.matrix.today.getValue(id).turns
            assertTrue(id, turns.none { it.kind == TurnKind.CLOSING } && turns.single().status == TurnStatus.DONE)
        }
        // the primary closed its day with its notebook first, and the lanes began after it
        assertTrue(t.dayLog("2026-10-15").turns.any { it.kind == TurnKind.CLOSING && it.status == TurnStatus.DONE })
        assertTrue(lanePosts().filter { turnText(it).contains("Dobro jutro") }.all { it.messages.first().toString().contains("# Notebook") })
    }

    @Test fun `a lane toggled off stops, the matrix off stops them all`() = runBlocking {
        val t = withMatrix()
        t.updateMatrix { m -> m.copy(lanes = m.lanes.filterNot { it.family == Family.OPUS }) }.join()
        t.onDelivered("c1", "/message", chat("c1", "Halo")).join()
        t.awaitMatrix()
        assertEquals(7, t.matrix.today.values.count { it.turns.isNotEmpty() })
        t.updateMatrix { it.copy(enabled = false) }.join()
        t.onDelivered("c2", "/message", chat("c2", "Še")).join()
        t.awaitMatrix()
        assertTrue(t.matrix.today.values.all { it.turns.size <= 1 })
        assertEquals("off", t.matrix.status)
        assertEquals(2, t.today.turns.size)
    }

    @Test fun `a restarted thread carries what the learner was shown in its last exchanges`() = runBlocking {
        val t = ready()
        t.update { it.copy(threadLimit = 3_000) }.join()
        t.onDelivered("c1", "/message", chat("c1", "Živjo!")).join()
        t.awaitIdle()
        minutes(1)
        t.onRealReply("main", "REAL: živjo, Jan", null).join()
        minutes(1)
        t.onDelivered("c2", "/message", chat("c2", "Kaj pa peč?")).join()
        t.awaitIdle()
        val first = t.thread!!.messages.first().toString()
        assertTrue(first, first.contains("the learner was shown (another tutor answered): REAL: živjo, Jan"))
    }

    // --- the matrix's OpenAI lanes ------------------------------------------------------------------------------------

    private val openaiLanes = listOf(
        "openai:gpt-6-luna@medium", "openai:gpt-6-luna@high", "openai:gpt-6-luna@xhigh", "openai:gpt-6-luna@max",
        "openai:gpt-6.1-sol@low", "openai:gpt-6.1-sol@medium", "openai:gpt-6.1-sol@high", "openai:gpt-6-astra@low", "openai:gpt-6-astra@medium",
    )

    @Test fun `OpenAI's lanes wait for a credential, then join the matrix once, in the same places as Anthropic's`() = runBlocking {
        val t = withMatrix()
        assertEquals(defaultLanes, t.matrix.plan.mapNotNull { it.id })
        assertFalse(t.settings.matrix.openaiOffered)
        assertEquals(null, t.setOpenAIKey(FAKE_OPENAI_KEY))
        t.awaitMatrix()
        assertEquals(setOf(Billing.API_KEY), t.openaiReady)
        assertEquals(defaultLanes + openaiLanes, t.matrix.plan.mapNotNull { it.id })
        assertTrue(t.settings.matrix.openaiOffered)
        assertEquals(listOf("gpt-6-astra", "gpt-6.1-sol", "gpt-6-luna", "text-embedding-3-small"), t.settings.openai.listed.map { it.id })
        // a lane toggled off stays off when the key is set again
        t.updateMatrix { m -> m.copy(lanes = m.lanes - LaneSpec(Family.ASTRA, "medium")) }.join()
        t.setOpenAIKey(FAKE_OPENAI_KEY)
        assertTrue(t.matrix.plan.none { it.id == "openai:gpt-6-astra@medium" })
        // without a credential they're skipped, and say so
        t.removeOpenAIKey().join()
        t.awaitMatrix()
        assertTrue(t.matrix.plan.filter { it.family.provider == ProviderId.OPENAI }.all { it.id == null && it.skipped!!.contains("no OpenAI credential") })
    }

    @Test fun `each OpenAI lane gets every event, on its own thread - the Responses API with store false, its effort, priced by the key`() = runBlocking {
        val t = withMatrix()
        t.setOpenAIKey(FAKE_OPENAI_KEY)
        t.updateMatrix { m -> m.copy(lanes = m.lanes.filter { it.family.provider == ProviderId.OPENAI }) }.join()
        t.onDelivered("c1", "/message", chat("c1", "Kako se reče stove?")).join()
        t.awaitMatrix()
        t.onDelivered("a1", "/message", answer("a1", "Jaz sem Jan.")).join()
        t.awaitMatrix()
        val lanes = t.matrix.today
        assertEquals(openaiLanes.toSet(), lanes.keys)
        for ((id, log) in lanes) {
            val (model, effort) = Lanes.parse(id)
            assertEquals(id, listOf("chat", "answer"), log.turns.map { it.eventKind })
            assertTrue(id, log.turns.all { it.status == TurnStatus.DONE && it.lane == id && it.model == model && it.effort == effort && it.replies.isNotEmpty() })
            assertTrue(id, log.calls.all { it.lane == id && it.provider == "openai" && it.billing == Billing.API_KEY && it.cost!! > 0 && it.apiEquivalent == null })
            // the reasoning tokens are the thinking, exactly
            assertTrue(id, log.calls.all { it.usage.reasoning == 60L && it.thinkingEstimate == 60L })
            val th = laneStore(id).thread()!!
            assertTrue(id, th.messages.first().str("type") == "message" && th.messages.first().toString().contains("<notebook>"))
            assertTrue(id, th.messages.any { it.str("type") == "reasoning" } && th.messages.any { it.str("type") == "function_call_output" })
        }
        // every request: the lane's model and effort, store false, the core prompt as instructions, no system message
        val sent = oai.posts().groupBy { "openai:" + it["model"]!!.jsonPrimitive.content + "@" + it["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content }
        assertEquals(openaiLanes.toSet(), sent.keys)
        for ((id, rs) in sent) {
            assertTrue(id, rs.all { it["store"]!!.jsonPrimitive.content == "false" && it["instructions"] != null && inputOf(it).none { m -> m.role() == "system" } })
            val room = when (id.substringAfter('@')) { "max" -> 64_000; "xhigh" -> 32_000; else -> 16_000 }
            assertTrue(id, rs.all { it["max_output_tokens"]!!.jsonPrimitive.content.toInt() == room })
            // the answer's turn carries the chat's: the reasoning items with their encrypted content
            val later = rs.last()
            assertTrue(id, inputOf(later).filter { it.str("type") == "reasoning" }.all { it.str("encrypted_content") != null } && inputOf(later).toString().contains("Kako se reče stove?"))
        }
        assertTrue(oai.http.requests.all { it.header("Authorization") == "Bearer $FAKE_OPENAI_KEY" && it.url.host == "api.openai.com" })
        // the export: each lane by its id, the matrix with the provider; never the key
        val text = t.exportText("test")
        assertFalse(text.contains(FAKE_OPENAI_KEY))
        val o = tutorJson.parseToJsonElement(text).jsonObject
        assertEquals(openaiLanes.toSet(), o["days"]!!.jsonArray.single().jsonObject["lanes"]!!.jsonObject.keys)
        assertTrue(o["matrix"]!!.jsonObject["lanes"]!!.jsonArray.filter { it.jsonObject["id"]?.jsonPrimitive?.content?.startsWith("openai:") == true }.all { it.jsonObject.str("provider") == "openai" })
        assertEquals("api_key", o["openai"]!!.jsonObject.str("use"))
        assertTrue(lines.none { it.contains(FAKE_OPENAI_KEY) })
    }

    @Test fun `with the ChatGPT plan - signed in through the browser, the lanes cost nothing, their API-equivalent recorded`() = runBlocking {
        val t = withMatrix()
        t.updateMatrix { m -> m.copy(lanes = listOf(LaneSpec(Family.SONNET, "medium"))) }.join()
        assertEquals(null, signInChatGPT(t))
        t.awaitMatrix()
        assertEquals("jan@example.com", t.chatgptAccount)
        assertEquals(Billing.CHATGPT_PLAN, t.openaiBilling())
        assertTrue(t.settings.openai.listed.isNotEmpty())
        t.updateMatrix { m -> m.copy(lanes = listOf(LaneSpec(Family.SONNET, "medium"), LaneSpec(Family.SOL, "medium"))) }.join()
        t.onDelivered("c1", "/message", chat("c1", "Živjo")).join()
        t.awaitMatrix()
        val sol = t.matrix.today.getValue("openai:gpt-6.1-sol@medium")
        assertTrue(sol.calls.isNotEmpty() && sol.calls.all { it.billing == Billing.CHATGPT_PLAN && it.cost == 0.0 && it.apiEquivalent!! > 0 })
        val stats = DayStats.of(sol, 0)
        assertEquals(0.0, stats.spend, 0.0)
        assertEquals(sol.calls.sumOf { it.apiEquivalent!! }, stats.apiEquivalent, 1e-12)
        assertEquals(sol.calls.size, stats.planCalls)
        // the plan's requests: the access token, the tools in a namespace, no max_output_tokens
        val sent = oai.posts()
        assertTrue(sent.all { it["max_output_tokens"] == null && it["tools"]!!.jsonArray.single().jsonObject.str("type") == "namespace" })
        assertTrue(oai.http.requests.all { it.header("Authorization") == "Bearer chatgpt-access-token-1" })
        // the Anthropic lane ran beside it, as before
        assertTrue(t.matrix.today.getValue("claude-sonnet-5-5@medium").turns.single().status == TurnStatus.DONE)
        // no token in the export or a log line
        val text = t.exportText("test")
        for (secret in listOf("chatgpt-access-token-1", "chatgpt-refresh-token-1", "jan@example.com")) {
            assertFalse(secret, text.contains(secret))
            assertTrue(secret, lines.none { it.contains(secret) })
        }
        // signing out: revoked, the lanes skipped
        assertEquals(null, t.signOutOfChatGPT())
        t.awaitMatrix()
        assertEquals(null, t.chatgptAccount)
        assertTrue(t.matrix.plan.single { it.family == Family.SOL }.skipped != null)
    }

    // --- the matrix's Gemini and OpenRouter lanes -------------------------------------------------------------------

    private val geminiLanes = listOf("gemini:gemini-3.8-flash@medium", "gemini:gemini-4-argon@low", "gemini:gemini-4-argon@medium", "gemini:gemini-4-argon@high")
    private val routerLanes = listOf("openrouter:google/gemini-3.8-flash@medium", "openrouter:google/gemini-4-argon@medium", "openrouter:openai/gpt-6-luna@medium")

    @Test fun `Gemini's and OpenRouter's lanes join once their credentials are set, each on its own thread, the export carrying their ids`() = runBlocking {
        val t = withMatrix()
        assertTrue("no credential: skipped", t.matrix.plan.none { it.id?.startsWith("gemini:") == true || it.id?.startsWith("openrouter:") == true })
        assertEquals(null, t.setGeminiKey(FAKE_GEMINI_KEY, GeminiTerms.PAID))
        t.awaitMatrix()
        assertTrue(t.settings.matrix.geminiOffered)
        assertEquals(listOf("gemini-4-argon", "gemini-3.8-flash", "gemini-3.5-flash-lite"), t.settings.gemini.listed.map { it.id })
        assertEquals(null, signInOpenRouter(t))
        t.awaitMatrix()
        assertTrue(t.openrouterReady && t.settings.matrix.openrouterOffered)
        assertEquals(defaultLanes.toSet() + geminiLanes + routerLanes, t.matrix.plan.mapNotNull { it.id }.toSet())
        // only theirs from here on
        t.updateMatrix { m -> m.copy(lanes = m.lanes.filter { it.family.provider == ProviderId.GEMINI || it.family.provider == ProviderId.OPENROUTER }) }.join()
        t.onDelivered("c1", "/message", chat("c1", "Kako se reče stove?")).join()
        t.awaitMatrix()
        val today = t.matrix.today
        assertEquals((geminiLanes + routerLanes).toSet(), today.keys)
        for ((id, log) in today) {
            val (model, effort) = Lanes.parse(id)
            val gemini = id.startsWith("gemini:")
            assertTrue(id, log.turns.single().let { it.status == TurnStatus.DONE && it.model == model && it.effort == effort && it.replies.single().text.contains("Mock") })
            assertTrue(id, log.calls.isNotEmpty() && log.calls.all { it.lane == id && it.provider == (if (gemini) "gemini" else "openrouter") })
            if (gemini) assertTrue(id, log.calls.all { it.billing == Billing.API_KEY && it.cost!! > 0 && it.apiEquivalent == null && it.usage.reasoning == 40L })
            else assertTrue("priced by OpenRouter's own cost: $id", log.calls.all { it.billing == Billing.OPENROUTER && it.cost == 0.0123 })
        }
        // each lane's thread in its own directory (an OpenRouter model's "/" as "~"), in its provider's format
        val argon = ShadowStore(File(dir, "matrix/gemini:gemini-4-argon@high")).thread()!!
        assertTrue(argon.messages.any { it.role() == "model" && it.toString().contains("thoughtSignature") })
        val routed = ShadowStore(File(dir, "matrix/openrouter:google~gemini-4-argon@medium")).thread()!!
        assertTrue(routed.messages.any { it.role() == "assistant" && it["reasoning_details"] != null } && routed.messages.any { it.role() == "tool" })
        // the requests: Gemini's level per lane, OpenRouter's effort, the keys only to their APIs
        val levels = gem.posts().map { it["generationConfig"]!!.jsonObject["thinkingConfig"]!!.jsonObject.str("thinkingLevel") }.toSet()
        assertEquals(setOf("low", "medium", "high"), levels)
        assertTrue(gem.http.requests.all { it.header("x-goog-api-key") == FAKE_GEMINI_KEY && it.url.host == "generativelanguage.googleapis.com" })
        assertTrue(router.posts().all { it["reasoning"]!!.jsonObject.str("effort") == "medium" })
        assertTrue(router.http.requests.filter { it.url.encodedPath == "/api/v1/chat/completions" }.all { it.header("Authorization") == "Bearer $FAKE_ROUTER_KEY" && it.url.host == "openrouter.ai" })
        // the export: the lanes by their ids, the providers, never a key
        val text = t.exportText("test")
        assertFalse(text.contains(FAKE_GEMINI_KEY))
        assertFalse(text.contains(FAKE_ROUTER_KEY))
        assertTrue(lines.none { it.contains(FAKE_GEMINI_KEY) || it.contains(FAKE_ROUTER_KEY) })
        val o = tutorJson.parseToJsonElement(text).jsonObject
        assertEquals((geminiLanes + routerLanes).toSet(), o["days"]!!.jsonArray.single().jsonObject["lanes"]!!.jsonObject.keys)
        val exported = o["matrix"]!!.jsonObject["lanes"]!!.jsonArray.map { it.jsonObject }.filter { it["id"] != null }
        assertTrue(exported.filter { it.str("id")!!.startsWith("gemini:") }.all { it.str("provider") == "gemini" })
        assertTrue(exported.filter { it.str("id")!!.startsWith("openrouter:") }.all { it.str("provider") == "openrouter" && it.str("model")!!.contains('/') })
        assertEquals("paid", o["gemini"]!!.jsonObject.str("tier"))
        assertEquals("true", o["openrouter"]!!.jsonObject["ready"]!!.jsonPrimitive.content)
        // a model picked from OpenRouter's list joins as a lane of its own; signed out, its lanes are skipped
        t.routedLane("anthropic/claude-sonnet-5.5", "low", on = true).join()
        assertTrue(t.matrix.plan.any { it.id == "openrouter:anthropic/claude-sonnet-5.5@low" })
        t.signOutOfOpenRouter()
        t.awaitMatrix()
        assertTrue(t.matrix.plan.filter { it.family == Family.ROUTED }.all { it.id == null && it.skipped!!.contains("OpenRouter") })
    }

    @Test fun `on the Gemini free tier Argon's lanes are skipped, Flash's cost nothing with their API-equivalent, and a 429 naming the free tier marks it`() = runBlocking {
        val t = withMatrix()
        assertEquals(null, t.setGeminiKey(FAKE_GEMINI_KEY, null))
        // the first call: the free tier's quota for the minute (retried after its delay), which tells the key's tier
        geminiFailOn = { n ->
            if (n == 1) Triple(429, """{"error":{"code":429,"message":"Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 10","status":"RESOURCE_EXHAUSTED","details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"2s"}]}}""", "application/json")
            else null
        }
        t.updateMatrix { m -> m.copy(lanes = listOf(LaneSpec(Family.FLASH, "medium"))) }.join()
        t.onDelivered("c1", "/message", chat("c1", "Živjo")).join()
        t.awaitMatrix()
        for (i in 0 until 100) {
            if (t.geminiTier == GeminiTerms.FREE_SEEN) break
            delay(10)
        }
        assertEquals(GeminiTerms.FREE_SEEN, t.geminiTier)
        assertTrue("the free tier is paced from then on", geminiCreds.pacer.free)
        t.updateMatrix { m -> m.copy(lanes = MatrixSettings.GEMINI_LANES) }.join()
        t.awaitMatrix()
        val plan = t.matrix.plan
        assertTrue(plan.filter { it.family == Family.ARGON }.all { it.id == null && it.skipped!!.contains("no free tier") })
        t.onDelivered("c2", "/message", chat("c2", "Kako se reče stove?")).join()
        t.awaitMatrix()
        val flash = t.matrix.today.getValue("gemini:gemini-3.8-flash@medium")
        val paid = flash.calls.filter { it.error == null }
        assertTrue(paid.isNotEmpty())
        assertTrue("the free tier: \$0, the API-equivalent kept", paid.takeLast(3).all { it.billing == Billing.FREE_TIER && it.cost == 0.0 && it.apiEquivalent!! > 0 })
    }

    @Test fun `an OpenAI credential refused or out pauses OpenAI's lanes only`() = runBlocking {
        val t = withMatrix()
        t.setOpenAIKey(FAKE_OPENAI_KEY)
        t.updateMatrix { it.copy(lanes = listOf(LaneSpec(Family.HAIKU, "high"), LaneSpec(Family.LUNA, "high"), LaneSpec(Family.SOL, "low"))) }.join()
        openaiFailOn = { Triple(429, """{"error":{"message":"You exceeded your current quota","type":"insufficient_quota","code":"insufficient_quota"}}""", "application/json") }
        t.onDelivered("c1", "/message", chat("c1", "Halo")).join()
        t.awaitMatrix()
        assertTrue(t.matrix.status, t.matrix.status.contains("openai paused: the credit balance is too low"))
        assertEquals(TurnStatus.DONE, t.matrix.today.getValue("claude-haiku-5-5@high").turns.single().status)
        assertTrue(t.matrix.queued.getValue("openai:gpt-6-luna@high").single().status == TurnStatus.QUEUED)
        // the next event: Anthropic's lane runs, OpenAI's wait
        t.onDelivered("c2", "/message", chat("c2", "Še")).join()
        t.awaitMatrix()
        assertEquals(2, t.matrix.today.getValue("claude-haiku-5-5@high").turns.size)
        assertEquals(2, t.matrix.queued.getValue("openai:gpt-6.1-sol@low").size)
        // checked again: they run
        openaiFailOn = { null }
        assertEquals(null, t.recheckOpenAI())
        t.awaitMatrix()
        assertTrue(t.matrix.today.getValue("openai:gpt-6-luna@high").turns.map { it.status } == listOf(TurnStatus.DONE, TurnStatus.DONE))
        assertTrue(t.matrix.status, t.matrix.status.startsWith("on: 3 lane(s)"))
    }

    @Test fun `also run on an OpenAI model - its own call, the provider's format, marked`() = runBlocking {
        val t = ready()
        t.setOpenAIKey(FAKE_OPENAI_KEY)
        t.onDelivered("c1", "/message", chat("c1", "Kako se reče stove?")).join()
        t.awaitIdle()
        t.alsoRun("ev_c1", "gpt-6-luna", "none").join()
        t.awaitIdle()
        val also = t.today.turns.single { it.kind == TurnKind.ALSO }
        assertEquals(TurnStatus.DONE, also.status)
        assertEquals("none", also.effort)
        assertTrue(also.replies.isNotEmpty())
        val calls = t.today.calls.filter { it.turnId == also.id }
        assertTrue(calls.isNotEmpty() && calls.all { it.provider == "openai" && it.thread == "also" && it.model == "gpt-6-luna" })
        val first = oai.posts().first()
        assertEquals("none", first["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertEquals(listOf("message"), inputOf(first).map { it.str("type") })
        // the primary's thread never saw it
        assertFalse(t.thread!!.messages.toString().contains("function_call"))
    }

    /** The scripted tutor on the Responses API: guide + learner_state with reasoning, then a reply, then done. */
    private fun openaiTurn(body: JsonObject, n: Int): List<JsonObject> {
        val input = inputOf(body)
        val lastUser = input.indexOfLast { it.role() == "user" }
        val since = input.drop(lastUser + 1)
        if (since.isEmpty()) return listOf(reasoningItem("rs_$n"), functionCall("fc_g$n", "g$n", "guide", obj("topic" to "feedback")), functionCall("fc_s$n", "s$n", "learner_state"))
        if (since.none { it.str("type") == "function_call" && it.str("name") == "reply" }) {
            val conv = Regex("""conversation_id=\"([\w-]+)""").find(input[lastUser].toString())?.groupValues?.get(1) ?: "main"
            return listOf(reasoningItem("rs_$n"), functionCall("fc_r$n", "r$n", "reply", obj("conversation_id" to conv, "text" to "**Mock GPT** · peč = stove")))
        }
        return listOf(messageItem("m_$n", "Opravljeno."))
    }

    /** The scripted tutor: guide + learner_state first, then a reply, then done; a closing writes the notebook. */
    private fun tutorTurn(body: JsonObject, n: Int): AssistantMessage {
        val messages = body["messages"]!!.jsonArray.map { it.jsonObject }
        val last = messages.last()
        val results = last.blocks().any { it.str("type") == "tool_result" }
        val prev = messages.getOrNull(messages.size - 2)
        val replied = prev?.blocks()?.any { it.str("type") == "tool_use" && it.str("name") in setOf("reply", "notebook_write") } == true
        if (!results && last.toString().contains("[notebook_write]")) return message("tool_use", toolUse("nb$n", "notebook_write", obj("text" to "# Notebook\n- Jan: A2, likes tables")))
        if (!results) return message("tool_use", thinking("sig$n"), text("Pogledam."), toolUse("g$n", "guide", obj("topic" to "feedback")), toolUse("s$n", "learner_state"))
        if (!replied && !messages.any { it.toString().contains("[notebook_write]") }) {
            val conv = Regex("""conversation_id=\\"([\w-]+)""").find(messages.last { m -> m.role() == "user" && m.blocks().none { it.str("type") == "tool_result" } }.toString())?.groupValues?.get(1) ?: "main"
            return message("tool_use", toolUse("r$n", "reply", obj("conversation_id" to conv, "text" to "**Mock** · peč = stove")))
        }
        return message("end_turn", text("Opravljeno."))
    }

    private companion object {
        /** The ChatGPT sign-in's loopback in the tests (this agent's port block, 19400–19499). */
        const val SIGN_IN_PORT = 19420
        const val STATE = """{"databases":{"learner_profile":{"learner":{"name":"Jan","current_level":"A2"},"current_streak_days":12},"mistakes_db":{"error_patterns":{"clitic_order":{"category":"grammar","frequency":7}}},"spaced_repetition":{"items":{"vocab_kurim":{"item_id":"vocab_kurim","front":"kurim","back":"I heat"}}}},"computed":{"due_reviews_count":4,"streak_active":true},"language":"sl"}"""

        /** [m] as the API streams it. */
        fun sseOf(m: AssistantMessage, model: String): String {
            val events = mutableListOf<Pair<String, String>>()
            fun usage(u: Usage, output: Long) = obj(
                "input_tokens" to u.input, "cache_creation_input_tokens" to u.cacheWrite, "cache_read_input_tokens" to u.cacheRead,
                "cache_creation" to obj("ephemeral_5m_input_tokens" to u.cacheWrite5m, "ephemeral_1h_input_tokens" to u.cacheWrite1h), "output_tokens" to output,
            )
            events += "message_start" to obj("type" to "message_start", "message" to obj("id" to "msg_x", "type" to "message", "role" to "assistant", "model" to model, "content" to JsonArray(emptyList()), "usage" to usage(m.usage, 1))).toString()
            m.content.forEachIndexed { i, b ->
                fun delta(d: JsonObject) { events += "content_block_delta" to obj("type" to "content_block_delta", "index" to i, "delta" to d).toString() }
                when (b.str("type")) {
                    "text" -> {
                        events += "content_block_start" to obj("type" to "content_block_start", "index" to i, "content_block" to obj("type" to "text", "text" to "")).toString()
                        delta(obj("type" to "text_delta", "text" to b.str("text")))
                    }
                    "thinking" -> {
                        events += "content_block_start" to obj("type" to "content_block_start", "index" to i, "content_block" to obj("type" to "thinking", "thinking" to "")).toString()
                        delta(obj("type" to "signature_delta", "signature" to b.str("signature")))
                    }
                    "tool_use" -> {
                        events += "content_block_start" to obj("type" to "content_block_start", "index" to i, "content_block" to obj("type" to "tool_use", "id" to b.str("id"), "name" to b.str("name"), "input" to JsonObject(emptyMap()))).toString()
                        b["input"].toString().chunked(7).forEach { delta(obj("type" to "input_json_delta", "partial_json" to it)) }
                    }
                }
                events += "content_block_stop" to obj("type" to "content_block_stop", "index" to i).toString()
            }
            events += "message_delta" to obj("type" to "message_delta", "delta" to obj("stop_reason" to m.stopReason), "usage" to obj("output_tokens" to m.usage.output)).toString()
            events += "message_stop" to obj("type" to "message_stop").toString()
            return sse(*events.toTypedArray())
        }
    }
}
