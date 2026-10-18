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
        )
    }

    @After fun stop() {
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
