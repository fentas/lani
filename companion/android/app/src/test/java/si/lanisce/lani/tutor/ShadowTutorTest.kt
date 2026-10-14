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

    private val api = Answering { r -> answerApi(r) }
    private val bridgeHttp = Answering { r ->
        when (r.url.encodedPath) {
            "/state" -> Triple(200, STATE, "application/json")
            "/game" -> Triple(200, """{"rev":3,"state":{"age":"ZASELEK","resources":{"food":12},"buildings":[{"type":"LIPA","level":1}]}}""", "application/json")
            else -> Triple(404, """{"error":"not found"}""", "application/json")
        }
    }

    private fun answerApi(r: Request): Triple<Int, String, String> {
        if (r.url.encodedPath == "/v1/models") return Triple(200, """{"data":[{"id":"claude-opus-5-5","created_at":"2026-10-01"},{"id":"claude-sonnet-5-5","created_at":"2026-10-01"},{"id":"claude-haiku-5-5","created_at":"2026-10-07"}],"has_more":false}""", "application/json")
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
