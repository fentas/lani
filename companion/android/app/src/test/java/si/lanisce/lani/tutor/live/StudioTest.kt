package si.lanisce.lani.tutor.live

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
import si.lanisce.lani.tutor.SoftCipher
import si.lanisce.lani.tutor.TurnStatus
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
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The studio end to end (docs/plans/05-built/studio.md): over the real Messages API client against a scripted API and a
 * played bridge (the tutor session's routes with the studio's: the jobs, the file tools, a publishing tool that refuses
 * a bad spec as the validator does, the notebook's versions, the reports): a chat's request_content becomes a job on
 * Opus, its publish refused then accepted, its reply; the file tool refused then accepted; a killed app's job resumed by
 * a new process from the bridge's copy; a sub-task on its own thread; the night's job through the Message Batches API
 * at half price; a ✕; an event's job acknowledged; the monthly cap and "answer anyway"; the notebook's versions; a report
 * posted to the discussions after the learner's yes.
 */
class StudioTest {
    private val zone = ZoneId.of("Europe/Ljubljana")
    private val dir: File = Files.createTempDirectory("studio-test").toFile()
    private var now = LocalDateTime.of(2026, 10, 19, 10, 0).atZone(zone).toInstant().toEpochMilli()
    private val lines = Collections.synchronizedList(mutableListOf<String>())
    private val notes = Collections.synchronizedList(mutableListOf<String>())
    private val vault = KeyVault(SoftCipher(), MemoryStore())
    private val scopes = mutableListOf<CoroutineScope>()

    // --- the API: the chat's turns and the studio's, streamed or through a batch ---------------------------------------

    /** What the chat asks for in its request_content (urgent or for the night) and the studio's marker in the brief. */
    private var marker = "[t:module]"
    private var urgent = true
    /** Called before the API answers a streamed call (its [n] and request body: the kill test cancels the process there). */
    private var before: (Int, JsonObject) -> Unit = { _, _ -> }
    private var calls = 0
    private val usage = Usage(input = 100, cacheWrite1h = 4_000, output = 80)

    private fun isStudio(body: JsonObject) = body["system"].toString().contains("studio of Lani")

    /** The chat: request_content (or a report) with the reply beside it, then done. */
    private fun chat(body: JsonObject, n: Int): AssistantMessage {
        val messages = body["messages"]!!.jsonArray.map { it.jsonObject }
        if (messages.last().blocks().any { it.str("type") == "tool_result" }) return message("end_turn", text("Opravljeno."))
        val first = messages.last { m -> m.role() == "user" && m.blocks().none { it.str("type") == "tool_result" } }.toString()
        if (first.contains("[t:report]")) return message(
            "tool_use",
            toolUse("rp$n", "report_problem", obj("kind" to "wish", "title" to "Idea: a map of the village's paths", "what_is_wrong" to "A map would help to find the scenes.")),
            toolUse("r$n", "reply", obj("conversation_id" to "main", "text" to "Vprašal sem te, ali naj pošljem.")),
        )
        return message(
            "tool_use",
            toolUse("rc$n", "request_content", obj("title" to "Vaje: rodilnik · Genitive drill", "brief" to "A drill on the genitive plural (pattern genitive_plural). $marker", "kinds" to JsonArray(listOf(JsonPrimitive("module"))), "urgent" to urgent)),
            toolUse("r$n", "reply", obj("conversation_id" to "main", "text" to "Naročeno, vaje so na poti.")),
        )
    }

    /** The studio: what the thread holds decides the next step (as the bun mock plays it). */
    private fun studio(body: JsonObject, n: Int): AssistantMessage {
        val messages = body["messages"]!!.jsonArray.map { it.jsonObject }
        val first = messages.first().toString()
        val sub = first.contains("<studio_subtask")
        val results = messages.last().blocks().filter { it.str("type") == "tool_result" }
        val lastId = results.lastOrNull()?.str("tool_use_id").orEmpty()
        val lastError = results.lastOrNull()?.get("is_error")?.jsonPrimitive?.content == "true"
        val file = !sub && first.contains("[t:file]")
        val subtask = !sub && first.contains("[t:subtask]")
        val conv = Regex("""conversation_id=\\"([\w-]+)""").find(first)?.groupValues?.get(1) ?: "main"
        return when {
            results.isEmpty() && subtask -> message("tool_use", toolUse("st$n", "studio_subtask", obj("title" to "Mock subtask", "brief" to "Publish the drill. [t:module]")))
            lastId.startsWith("st") -> message("tool_use", toolUse("rp$n", "reply", obj("conversation_id" to conv, "text" to "**Studio** · the sub-task published the drill.")))
            results.isEmpty() && file -> message("tool_use", toolUse("fb$n", "str_replace_based_edit_tool", obj("command" to "create", "path" to "/app/stories/mock.json", "file_text" to "{ not json")))
            lastId.startsWith("fb") -> message("tool_use", toolUse("fg$n", "str_replace_based_edit_tool", obj("command" to "create", "path" to "/app/stories/mock.json", "file_text" to "{\"schema\": \"lani.story/v0\", \"id\": \"mock\"}")))
            lastId.startsWith("fg") -> message("tool_use", toolUse("rp$n", "reply", obj("conversation_id" to conv, "text" to "**Studio** · the story is written.")))
            results.isEmpty() -> message("tool_use", text("Najprej učenec."), toolUse("ls$n", "learner_state"))
            lastId.startsWith("ls") -> message("tool_use", toolUse("pb$n", "publish_module", obj("spec" to spec(answer = 4), "note" to "machine-written (test)")))
            lastId.startsWith("pb") && lastError -> message("tool_use", toolUse("pg$n", "publish_module", obj("spec" to spec(answer = 0), "note" to "machine-written (test)")))
            // the publish's answer lost on the way (the node applied it): the model tries the same publish again
            lastId.startsWith("pg") && lastError && results.last().toString().contains("didn't reach") ->
                message("tool_use", toolUse("pg$n", "publish_module", obj("spec" to spec(answer = 0), "note" to "machine-written (test)")))
            lastId.startsWith("pg") && !sub -> message("tool_use", toolUse("rp$n", "reply", obj("conversation_id" to conv, "text" to "**Studio** · vaje so objavljene · the drill is published.")))
            else -> message("end_turn", text("Published module:mock-studio-drill."))
        }
    }

    private fun spec(answer: Int) = obj(
        "schema" to "lani.module/v0", "id" to "mock-studio-drill", "title" to "Rodilnik (strojno · machine-written)",
        "exercises" to JsonArray(listOf(obj("type" to "choice", "prompt" to "Nimam ____.", "options" to JsonArray(listOf(JsonPrimitive("vžigalic"), JsonPrimitive("vžigalice"))), "answer" to answer))),
    )

    private fun script(body: JsonObject, n: Int): AssistantMessage = if (isStudio(body)) studio(body, n) else chat(body, n)

    /** The batches the fake API holds: their requests' messages by custom id, and how often each was asked. */
    private val batches = Collections.synchronizedMap(mutableMapOf<String, MutableList<Pair<String, JsonObject>>>())
    private val asked = Collections.synchronizedMap(mutableMapOf<String, Int>())
    private val batchBodies = Collections.synchronizedList(mutableListOf<JsonObject>())

    private fun messageJson(m: AssistantMessage, model: String): JsonObject = obj(
        "id" to "msg_b", "type" to "message", "role" to "assistant", "model" to model, "content" to JsonArray(m.content), "stop_reason" to m.stopReason,
        "usage" to obj("input_tokens" to usage.input, "cache_creation_input_tokens" to usage.cacheWrite, "cache_read_input_tokens" to 0, "cache_creation" to obj("ephemeral_5m_input_tokens" to 0, "ephemeral_1h_input_tokens" to usage.cacheWrite1h), "output_tokens" to usage.output),
    )

    private val api = Answering { r ->
        val path = r.url.encodedPath
        when {
            path == "/v1/models" -> Triple(200, MODELS, "application/json")
            path == "/v1/messages/batches" -> {
                val o = tutorJson.parseToJsonElement(bodyOf(r)) as JsonObject
                batchBodies += o
                val id = "msgbatch_t${batches.size + 1}"
                batches[id] = o["requests"]!!.jsonArray.map { e ->
                    val q = e.jsonObject
                    val params = q["params"]!!.jsonObject
                    calls++
                    q["custom_id"]!!.jsonPrimitive.content to messageJson(script(params, calls), params["model"]!!.jsonPrimitive.content)
                }.toMutableList()
                Triple(200, batchJson(id, ended = false).toString(), "application/json")
            }
            path.endsWith("/results") -> {
                val id = path.removePrefix("/v1/messages/batches/").removeSuffix("/results")
                Triple(200, batches[id].orEmpty().joinToString("\n") { (c, m) -> obj("custom_id" to c, "result" to obj("type" to "succeeded", "message" to m)).toString() }, "application/x-jsonl")
            }
            path.startsWith("/v1/messages/batches/") -> {
                val id = path.removePrefix("/v1/messages/batches/")
                val n = (asked[id] ?: 0) + 1
                asked[id] = n
                Triple(200, batchJson(id, ended = n >= 2).toString(), "application/json")
            }
            else -> {
                val o = tutorJson.parseToJsonElement(bodyOf(r)) as JsonObject
                calls++
                before(calls, o)
                val m = script(o, calls)
                Triple(200, sseOf(m.copy(usage = usage), o["model"]!!.jsonPrimitive.content), "text/event-stream")
            }
        }
    }

    private fun batchJson(id: String, ended: Boolean) = obj(
        "id" to id, "type" to "message_batch", "processing_status" to if (ended) "ended" else "in_progress",
        "request_counts" to obj("processing" to if (ended) 0 else 1, "succeeded" to if (ended) 1 else 0, "errored" to 0, "canceled" to 0, "expired" to 0),
        "results_url" to if (ended) "https://api.anthropic.com/v1/messages/batches/$id/results" else null,
        "created_at" to "2026-10-19T08:00:00Z",
    )

    private fun posts(): List<JsonObject> = synchronized(api.requests) { api.requests.toList() }
        .filter { it.method == "POST" && it.url.encodedPath == "/v1/messages" }.map { tutorJson.parseToJsonElement(bodyOf(it)) as JsonObject }

    // --- the bridge, played --------------------------------------------------------------------------------------------

    private val node = StudioNode()

    /** GitHub's GraphQL, played: nothing found, then a discussion made in its category. */
    private val graphql = Collections.synchronizedList(mutableListOf<JsonObject>())
    private val github = Interceptor { chain ->
        val r = chain.request()
        val o = tutorJson.parseToJsonElement(bodyOf(r)) as JsonObject
        graphql += o
        val q = o["query"]!!.jsonPrimitive.content
        val data = when {
            q.contains("search(") -> obj("search" to obj("nodes" to JsonArray(emptyList())))
            q.contains("repository(") -> obj("repository" to obj("id" to "R_1", "discussionCategories" to obj("nodes" to JsonArray(listOf(obj("id" to "C_1", "name" to "Content fixes", "slug" to "content-fixes"), obj("id" to "C_2", "name" to "Ideas", "slug" to "ideas"))))))
            q.contains("createDiscussion") -> obj("createDiscussion" to obj("discussion" to obj("id" to "D_1", "number" to 42, "title" to "Idea: a map of the village's paths", "url" to "https://github.com/fentas/lani/discussions/42", "upvoteCount" to 1, "viewerHasUpvoted" to true, "closed" to false, "category" to obj("name" to "Ideas"))))
            else -> obj()
        }
        Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(200).message("ok").body(obj("data" to data).toString().toResponseBody("application/json".toMediaType())).build()
    }

    private var token: String? = null

    private fun tutor(scope: CoroutineScope = CoroutineScope(SupervisorJob()).also { scopes += it }, pre: Interceptor? = null): LiveTutor = LiveTutor(
        scope, LiveStore(dir), vault, openai = null,
        bridgeConfig = { BridgeConfig("http://node.test:8790", "bridge-token-studio-test-0123456789") },
        debug = false,
        transportFor = { base, key -> AnthropicTransport(base, key, OkHttpClient.Builder().addInterceptor(api).build(), clock = { now }) },
        bridgeFor = { c -> LiveBridge(c, OkHttpClient.Builder().apply { pre?.let(::addInterceptor) }.addInterceptor(node).build()) },
        fran = null,
        clock = { now }, zone = { zone }, device = "test phone",
        logLine = { lines += it }, sleep = {},
        io = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
        texts = { key, _ -> key },
        githubToken = { token },
        discussionsFor = { t -> Discussions(t, http = OkHttpClient.Builder().addInterceptor(github).build()) },
        batchesFor = { base, key -> AnthropicBatches(base, key, OkHttpClient.Builder().addInterceptor(api).build()) },
        studioPollMs = 20,
    ).also { t -> t.onNote = { notes += it } }

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

    private suspend fun settle(t: LiveTutor) {
        t.awaitIdle()
        t.studio.awaitIdle(15_000)
        t.awaitIdle()
    }

    /**
     * Waits until [cond] holds, for at most [timeoutMs]: what comes late on a slow runner (CI's two cores) is waited for,
     * not missed; only a condition that never comes fails, and says which.
     */
    private suspend fun awaitUntil(what: String, timeoutMs: Long = 30_000, cond: () -> Boolean) {
        val end = System.nanoTime() + timeoutMs * 1_000_000
        while (!cond()) {
            if (System.nanoTime() > end) throw AssertionError("waited $timeoutMs ms for $what")
            delay(10)
        }
    }

    /** The process of [scope] is gone: cancelled, and all it ran has ended (its last saves tried), before the next one starts. */
    private suspend fun gone(scope: CoroutineScope) {
        scope.cancel()
        scope.coroutineContext.job.join()
    }

    // --- the tests -----------------------------------------------------------------------------------------------------

    @Test fun `a chat asks for a module, a studio job writes it on Opus, the validator's refusal fixed, the job saved on the bridge`() = runBlocking {
        val t = ready()
        node.push("chat", "main", "Make me exercises for the genitive plural, please.")
        t.backgroundCheck()
        settle(t)
        val chatReq = posts().first { !isStudio(it) }
        val chatTools = chatReq["tools"]!!.jsonArray.map { it.jsonObject.str("name") }
        assertEquals("phase A's tools, then request_content", "request_content", chatTools.last())
        val job = node.jobs.values.single()
        assertEquals(StudioJob.DONE, job.str("status"))
        assertEquals("request", job.str("origin"))
        // the studio on the content model (Opus at the preset's effort), its own prompt, the text editor by its type
        val studioReqs = posts().filter(::isStudio)
        assertTrue(studioReqs.all { it.str("model") == "claude-opus-5-5" })
        assertEquals("medium", studioReqs.first()["output_config"]!!.jsonObject.str("effort"))
        val editor = studioReqs.first()["tools"]!!.jsonArray.map { it.jsonObject }.first { it.str("name") == "str_replace_based_edit_tool" }
        assertEquals("text_editor_20250728", editor.str("type"))
        assertEquals("the text editor has no schema of its own on the Messages API", null, editor["input_schema"])
        // publish refused (the validator's message), then accepted, then the reply
        val publishes = node.calls.filter { it.first == "publish_module" }
        assertEquals(2, publishes.size)
        val studioCallsOnNode = node.calls.filter { (name, body) -> name != "reply" || body.toString().contains("Studio") }.map { it.first }
        assertEquals(listOf("learner_state", "publish_module", "publish_module", "reply"), studioCallsOnNode)
        val refusal = studioReqs.first { it.toString().contains("out of range") }
        assertTrue("the refusal went back to the model as the tool's error", refusal.toString().contains("is_error"))
        val drafts = job["drafts"]!!.jsonObject
        assertEquals("published", drafts.values.single().jsonObject.str("state"))
        assertEquals(1, job["published"]!!.jsonArray.size)
        assertTrue("saved after each step", node.puts >= 4)
        // the studio's calls in the day's log, priced; the job's turn in it
        val studioCalls = t.today.calls.filter { it.thread == "studio" }
        assertEquals("learner_state, the refused publish, the fixed one, the reply, the end", 5, studioCalls.size)
        assertTrue(studioCalls.all { (it.cost ?: 0.0) > 0 })
        assertTrue(t.today.turns.any { it.eventKind == "studio" && it.why!!.contains("published") })
        // the job's thread starts with the notebook and the brief
        val first = studioReqs.first()["messages"]!!.jsonArray.first().toString()
        assertTrue(first.contains("Jan likes tables") && first.contains("<studio_job") && first.contains("genitive_plural"))
    }

    @Test fun `a file tool write refused by the validator, then accepted`() = runBlocking {
        marker = "[t:file]"
        val t = ready()
        node.push("chat", "main", "A story, please.")
        t.backgroundCheck()
        settle(t)
        val writes = node.calls.filter { it.first == "str_replace_based_edit_tool" }
        assertEquals(2, writes.size)
        assertEquals("create", writes.first().second["args"]!!.jsonObject.str("command"))
        val job = node.jobs.values.single()
        assertEquals(StudioJob.DONE, job.str("status"))
        assertEquals("published", job["drafts"]!!.jsonObject.values.single().jsonObject.str("state"))
        assertTrue(posts().filter(::isStudio).any { it.toString().contains("doesn't validate as a story") })
    }

    @Test fun `a killed app's job resumes in a new process from the bridge's copy, nothing published twice`() = runBlocking {
        val scopeA = CoroutineScope(SupervisorJob())
        before = { _, body ->
            // the studio's call after the validator's refusal: the process is gone before it answers
            if (isStudio(body) && body["messages"]!!.jsonArray.last().toString().contains("out of range")) scopeA.cancel()
        }
        val a = ready(tutor(scopeA))
        node.push("chat", "main", "Exercises, please.")
        a.backgroundCheck()
        // A is gone: its job was saved after the refused publish
        awaitUntil("A's call after the refusal") { !scopeA.isActive }
        gone(scopeA)
        val saved = node.jobs.values.single()
        assertEquals(StudioJob.QUEUED, saved.str("status"))
        assertEquals(1, node.calls.count { it.first == "publish_module" })
        before = { _, _ -> }
        // a new process: the same data, the job read from the bridge and run on
        val b = tutor()
        awaitUntil("the job done in the new process") { node.jobs.values.single().str("status") == StudioJob.DONE }
        settle(b)
        val done = node.jobs.values.single()
        assertEquals(StudioJob.DONE, done.str("status"))
        assertEquals("the refused one and the fixed one: no publish twice", 2, node.calls.count { it.first == "publish_module" })
        assertEquals(1, node.calls.count { it.first == "reply" && it.second.toString().contains("Studio") })
        assertTrue(done["attempts"]!!.jsonPrimitive.content.toInt() >= 2)
    }

    /** The valid publish's step (the model's answer with it, the call not answered yet) as a job's save carries it. */
    private fun withValidPublish(job: JsonObject): Boolean =
        job["messages"]?.jsonArray?.lastOrNull()?.jsonObject?.blocks()?.any { it.str("type") == "tool_use" && it.str("id").orEmpty().startsWith("pg") } == true

    @Test fun `a job killed after the publish but before the step was saved resumes without a second version`() = runBlocking {
        val scopeA = CoroutineScope(SupervisorJob())
        // the step with the valid publish isn't saved (lost on the way), the publish goes through, and the process is
        // killed before anything after it is saved: the bridge's copy is the job as it was before that call
        var dropped = 0
        node.dropPut = { job -> withValidPublish(job) && job["intents"]?.jsonArray.orEmpty().none { it.jsonObject.str("state") == "sent" } && dropped++ == 0 }
        node.afterTool = { name, body ->
            if (name == "publish_module" && body["args"].toString().contains("\"answer\":0")) {
                node.frozen = true
                scopeA.cancel()
            }
        }
        val a = ready(tutor(scopeA))
        node.push("chat", "main", "Exercises, please.")
        a.backgroundCheck()
        awaitUntil("the valid publish") { node.frozen }
        gone(scopeA)
        assertEquals("the refused publish and the valid one", 2, node.calls.count { it.first == "publish_module" })
        val saved = node.jobs.values.single()
        val intent = saved["intents"]!!.jsonArray.map { it.jsonObject }.last()
        assertEquals("the publish was recorded in the job before it was sent", "sent", intent.str("state"))
        assertEquals("publish_module", intent.str("tool"))
        node.frozen = false
        node.afterTool = { _, _ -> }
        // a new process: the job from the bridge's copy, run on
        val b = tutor()
        awaitUntil("the job done in the new process") { node.jobs.values.single().str("status") == StudioJob.DONE }
        settle(b)
        val done = node.jobs.values.single()
        assertEquals(StudioJob.DONE, done.str("status"))
        assertEquals("no second version: the publish isn't applied again", 2, node.calls.count { it.first == "publish_module" })
        assertEquals(1, done["published"]!!.jsonArray.size)
        assertEquals("published", done["intents"]!!.jsonArray.map { it.jsonObject }.last().str("state"))
        assertEquals(1, node.calls.count { it.first == "reply" && it.second.toString().contains("Studio") })
    }

    @Test fun `a publish whose answer was lost and is sent again is answered once, not applied twice`() = runBlocking {
        var lost = 0
        node.loseAnswer = { name, body -> name == "publish_module" && body["args"].toString().contains("\"answer\":0") && lost++ == 0 }
        val t = ready()
        node.push("chat", "main", "Exercises, please.")
        t.backgroundCheck()
        settle(t)
        val job = node.jobs.values.single()
        assertEquals(StudioJob.DONE, job.str("status"))
        val sent = node.requests.filter { it.url.encodedPath == "/tutor/tools/publish_module" }.map { tutorJson.parseToJsonElement(bodyOf(it)).jsonObject }
        assertEquals("the refused one, the valid one, the valid one again", 3, sent.size)
        assertEquals("the same write went again with the client id it went with first", sent[1].str("client_id"), sent[2].str("client_id"))
        assertEquals("the node applied it once", 2, node.calls.count { it.first == "publish_module" })
        assertEquals(1, job["published"]!!.jsonArray.size)
    }

    @Test fun `the background's run and the open app's worker never run a job at once`() = runBlocking {
        // A queues the job and is gone at the studio's first call (its saves from then on don't reach the bridge)
        val scopeA = CoroutineScope(SupervisorJob())
        before = { _, body ->
            if (isStudio(body) && !node.frozen) {
                node.frozen = true
                scopeA.cancel()
            }
        }
        val a = ready(tutor(scopeA))
        node.push("chat", "main", "Exercises, please.")
        a.backgroundCheck()
        awaitUntil("A's first studio call") { node.frozen }
        gone(scopeA)
        val studioCallsA = posts().count(::isStudio)
        assertEquals("A's one call, not answered", 1, studioCallsA)
        node.frozen = false
        // B: the WorkManager's run starts while the process's start-up still reads the session; the open app's worker
        // starts once the session is read (StudioWork's TutorRuntime.live). The session's answer is held until the
        // background's run is inside the job, and each call takes a moment: before the fix both ran the job, each
        // making its calls, two at once
        val inJob = CountDownLatch(1)
        val inFlight = AtomicInteger()
        val most = AtomicInteger()
        before = { _, body ->
            if (isStudio(body)) {
                most.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
                inJob.countDown()
                Thread.sleep(150)
                inFlight.decrementAndGet()
            }
        }
        val heldSession = Interceptor { chain ->
            if (chain.request().url.encodedPath == "/tutor/session") inJob.await(30, TimeUnit.SECONDS)
            chain.proceed(chain.request())
        }
        val b = tutor(pre = heldSession)
        b.studio.background(20_000)
        settle(b)
        assertEquals("the start-up's session read while the background's run was in the job", 0, inJob.count)
        assertEquals("one job", 1, node.jobs.size)
        val done = node.jobs.values.single()
        assertEquals(StudioJob.DONE, done.str("status"))
        assertEquals("learner_state, the refused publish, the fixed one, the reply, the end: once each", 5, posts().count(::isStudio) - studioCallsA)
        assertEquals("never two of the job's calls at once", 1, most.get())
        assertEquals(2, node.calls.count { it.first == "publish_module" })
        assertEquals(1, node.calls.count { it.first == "reply" && it.second.toString().contains("Studio") })
    }

    @Test fun `a chat turn resumed after the app was killed doesn't queue its job a second time`() = runBlocking {
        // A: the chat's request_content queues the job, saved on the bridge, and the process is gone before the app has
        // the save's answer: the turn's step (the calls' results) isn't saved, so the next process resumes the turn and
        // runs its request_content again
        val scopeA = CoroutineScope(SupervisorJob())
        node.afterPut = { _ ->
            node.afterPut = {}
            node.frozen = true
            scopeA.cancel()
        }
        val a = ready(tutor(scopeA))
        node.push("chat", "main", "Exercises, please.")
        a.backgroundCheck()
        awaitUntil("the job's first save") { node.frozen }
        gone(scopeA)
        assertEquals(StudioJob.QUEUED, node.jobs.values.single().str("status"))
        assertEquals("A made no studio call", 0, posts().count(::isStudio))
        node.frozen = false
        // B: the turn goes on from its saved step (request_content and reply called, not answered); the job runs once
        val b = tutor()
        awaitUntil("the studio's jobs ended") { node.jobs.values.none { it.str("status") in setOf(StudioJob.QUEUED, StudioJob.RUNNING) } }
        settle(b)
        assertEquals("the resumed request_content answered from the job it had queued", 1, node.jobs.size)
        assertEquals(StudioJob.DONE, node.jobs.values.single().str("status"))
        assertTrue(lines.any { it.contains("not queued twice") })
        assertEquals("learner_state, the refused publish, the fixed one, the reply, the end: once each", 5, posts().count(::isStudio))
        assertEquals(2, node.calls.count { it.first == "publish_module" })
        assertEquals(1, node.calls.count { it.first == "reply" && it.second.toString().contains("Studio") })
        assertTrue("the chat's turn ended", b.today.turns.any { it.eventKind == "chat" && it.status == TurnStatus.DONE })
    }

    @Test fun `a sub-task runs on its own thread and its result goes back to the job`() = runBlocking {
        marker = "[t:subtask]"
        val t = ready()
        node.push("chat", "main", "A unit's drills.")
        t.backgroundCheck()
        settle(t)
        val job = node.jobs.values.single()
        assertEquals(StudioJob.DONE, job.str("status"))
        val sub = job["subtasks"]!!.jsonArray.single().jsonObject
        assertEquals(StudioJob.DONE, sub.str("status"))
        assertTrue(sub["messages"]!!.jsonArray.first().toString().contains("<studio_subtask"))
        val subReqs = posts().filter { isStudio(it) && it["messages"]!!.jsonArray.first().toString().contains("<studio_subtask") }
        assertTrue("the sub-task's calls start from its own brief, not the job's thread", subReqs.isNotEmpty() && subReqs.none { it["messages"]!!.jsonArray.first().toString().contains("<studio_job") })
        assertEquals(2, node.calls.count { it.first == "publish_module" })
    }

    @Test fun `what can wait goes through the Message Batches API, metered at half price`() = runBlocking {
        urgent = false
        val t = ready()
        node.push("chat", "main", "Some drills for next week.")
        t.backgroundCheck()
        settle(t)
        val job = node.jobs.values.single()
        assertEquals(StudioJob.DONE, job.str("status"))
        assertEquals(true, job["batched"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("no studio call streamed", 0, posts().count(::isStudio))
        assertEquals("each step a batch", 5, batchBodies.size)
        val params = batchBodies.first()["requests"]!!.jsonArray.first().jsonObject["params"]!!.jsonObject
        assertEquals(null, params["stream"])
        assertTrue(params["tools"]!!.jsonArray.none { it.jsonObject.containsKey("eager_input_streaming") })
        val batched = t.today.calls.filter { it.thread == "studio-batch" }
        assertEquals(5, batched.size)
        // half the streamed price of the same usage
        val full = si.lanisce.lani.tutor.Prices.parse(si.lanisce.lani.tutor.Bundle.text("prices.json")!!).cost("claude-opus-5-5", usage)!!
        assertEquals(full / 2, batched.first().cost!!, 1e-9)
        assertEquals(2, node.calls.count { it.first == "publish_module" })
    }

    @Test fun `a cancelled job stops, its calls end, the strip shows it cancelled`() = runBlocking {
        marker = "[t:module]"
        var t: LiveTutor? = null
        before = { _, body -> if (isStudio(body) && body["messages"]!!.jsonArray.last().toString().contains("out of range")) runBlocking { t!!.studio.cancel(t!!.studio.jobs.first().id) } }
        t = ready()
        node.push("chat", "main", "Exercises.")
        t.backgroundCheck()
        settle(t)
        val job = node.jobs.values.single()
        assertEquals(StudioJob.CANCELLED, job.str("status"))
        assertTrue(node.calls.none { it.first == "publish_module" && it.second.toString().contains("\"answer\":0") })
        assertEquals(StudioJob.CANCELLED, t.studio.jobs.single().status)
    }

    @Test fun `an event that asks for content becomes the studio's job, acknowledged when it ends`() = runBlocking {
        val t = ready()
        val id = node.push("villager_arrived", "main", "Someone moved in: Nina, the potter. [t:module]")
        t.backgroundCheck()
        // the event's job is made on the tutor's thread after the event is taken: wait for it, then for its run
        awaitUntil("the event's job") { node.jobs.isNotEmpty() }
        settle(t)
        awaitUntil("the event's job done") { node.jobs.values.singleOrNull()?.str("status") == StudioJob.DONE }
        val job = node.jobs.values.single()
        assertEquals("event", job.str("origin"))
        assertEquals("villager_arrived", job.str("event_kind"))
        assertEquals(StudioJob.SOON, job.str("urgency"))
        assertEquals(StudioJob.DONE, job.str("status"))
        awaitUntil("the event acknowledged") { id in node.acked }
        assertTrue("acknowledged once done", id in node.acked)
    }

    @Test fun `the monthly cap stops all but the learner's own chats, after they said so`() = runBlocking {
        val t = ready()
        node.push("chat", "main", "Živjo [t:none]")
        marker = "[t:none]"
        t.backgroundCheck()
        settle(t)
        t.update { it.copy(monthlyCap = 0.0001) }.join()
        val before = posts().size
        node.push("chat", "main", "Še eno vprašanje.")
        t.backgroundCheck()
        assertEquals("nothing called at the cap", before, posts().size)
        assertTrue(notes.contains("tutorLive.note.month"))
        assertEquals(LiveState.PAUSED_MONTH, t.state)
        assertTrue(t.monthReached)
        t.allowMonth().join()
        t.backgroundCheck()
        settle(t)
        assertTrue("answered once allowed", posts().size > before)
    }

    @Test fun `the notebook's versions are read and one is restored`() = runBlocking {
        val t = ready()
        val versions = t.notebookVersions()
        assertEquals(listOf("2026-10-18T20:00:00.000Z", "2026-10-17T20:00:00.000Z"), versions.map { it.at })
        assertEquals(null, t.restoreNotebook(versions.last().at))
        assertEquals("# Notebook\n- Jan liked lists", t.notebook.text)
        assertEquals("2026-10-17T20:00:00.000Z", node.restored)
        assertNotNull(t.restoreNotebook("1999-01-01T00:00:00.000Z"))
    }

    @Test fun `report_problem with a GitHub sign-in asks the learner first, then posts and keeps the link`() = runBlocking {
        token = "gho_studio_test_token_0123456789"
        marker = "[t:report]"
        val t = ready()
        node.push("chat", "main", "An idea [t:report]")
        t.backgroundCheck()
        settle(t)
        assertTrue("nothing posted before the learner's yes", graphql.none { it["query"]!!.jsonPrimitive.content.contains("createDiscussion") })
        assertTrue("not in the wishlist either", node.calls.none { it.first == "report_problem" })
        val p = t.reports.single()
        assertEquals(null, p.foundNumber)
        t.answerReport(p.id, true).join()
        assertTrue(graphql.any { it["query"]!!.jsonPrimitive.content.contains("createDiscussion") })
        val kept = node.reports.single()
        assertEquals("https://github.com/fentas/lani/discussions/42", kept.str("url"))
        assertEquals("posted", kept.str("state"))
        assertTrue(notes.contains("studio.report.posted"))
        assertTrue(t.reports.isEmpty())
        assertFalse("the token never in a log line", lines.any { it.contains(token!!) })
    }

    // --- helpers --------------------------------------------------------------------------------------------------------

    /** The paired bridge with the studio's side (companion/bridge/src/features/tutor.ts and tutor-studio.ts), played. */
    private inner class StudioNode : Interceptor {
        val requests: MutableList<Request> = Collections.synchronizedList(mutableListOf())
        private val inbox = mutableListOf<JsonObject>()
        private var next = 2_000L
        @Volatile var attached = false
        val acked: MutableList<Long> = Collections.synchronizedList(mutableListOf())
        val calls: MutableList<Pair<String, JsonObject>> = Collections.synchronizedList(mutableListOf())
        val jobs: MutableMap<String, JsonObject> = Collections.synchronizedMap(linkedMapOf())
        val reports: MutableList<JsonObject> = Collections.synchronizedList(mutableListOf())
        private val once = Collections.synchronizedMap(mutableMapOf<String, String>())
        @Volatile var puts = 0
        /** The app's process is gone: its saves don't arrive. */
        @Volatile var frozen = false
        /** A job's save that is lost on the way (answered 500, not kept). */
        var dropPut: (JsonObject) -> Boolean = { false }
        /** After a job's save was kept, before the app has the answer. */
        @Volatile var afterPut: (JsonObject) -> Unit = {}
        /** After a tool ran on the node (its answer kept for its client id), before the app has the answer. */
        var afterTool: (String, JsonObject) -> Unit = { _, _ -> }
        /** A tool's answer lost on the way back (it ran on the node). */
        var loseAnswer: (String, JsonObject) -> Boolean = { _, _ -> false }
        @Volatile var restored: String? = null

        @Synchronized fun push(kind: String, conversation: String, content: String): Long {
            val id = next++
            inbox += obj("id" to id, "content" to content, "meta" to obj("kind" to kind, "conversation_id" to conversation, "msg_id" to "m$id"), "at" to now)
            return id
        }

        private val TOOLS = listOf(
            "channel_guide", "reply", "get_village", "recent_answers", "list_packs", "list_grammar", "get_grammar", "publish_gloss", "publish_sentence_note",
            "set_rhythm", "list_modules", "get_module", "publish_module", "publish_story", "publish_scene", "publish_dialog_variant", "publish_arrival",
        )
        private val APP_TOOLS = listOf("learner_state", "record_session", "card", "notebook_note", "notebook_write", "report_problem", "str_replace_based_edit_tool", "search")

        @Synchronized override fun intercept(chain: Interceptor.Chain): Response {
            val r = chain.request()
            requests += r
            val path = r.url.encodedPath
            fun ok(body: String) = Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(200).message("ok").body(body.toResponseBody("application/json".toMediaType())).build()
            fun code(c: Int) = Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(c).message("x").body("""{"error":"x"}""".toResponseBody("application/json".toMediaType())).build()
            val status = { obj("attached" to if (attached) obj("who" to "app", "since" to "2026-10-19T08:00:00Z") else null, "inbox" to obj("size" to inbox.size, "last_id" to next - 1)) }
            fun names(l: List<String>) = JsonArray(l.map { obj("name" to it, "description" to "", "inputSchema" to obj("type" to "object")) })
            val raw = r.body?.let { bodyOf(r) }.orEmpty()
            val body = raw.takeIf { it.isNotBlank() }?.let { tutorJson.parseToJsonElement(it) as JsonObject }
            fun result(text: String, error: Boolean = false) = ok(obj("content" to JsonArray(listOf(obj("type" to "text", "text" to text))), "isError" to error).toString())
            return when {
                path == "/tutor/session" -> ok(JsonObject(status() + mapOf("protocol" to JsonPrimitive(1), "instructions" to JsonPrimitive("rules"), "tools" to names(TOOLS), "app_tools" to names(APP_TOOLS))).toString())
                path == "/tutor/attach" -> { attached = true; ok(JsonObject(status() + ("ok" to JsonPrimitive(true))).toString()) }
                path == "/tutor/detach" -> { attached = false; inbox.clear(); ok(obj("ok" to true, "returned" to 0).toString()) }
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
                path == "/tutor/jobs" -> ok(obj("jobs" to JsonArray(jobs.values.map { j -> JsonObject(j.filterKeys { it !in setOf("messages", "subtasks", "drafts", "steps") }) })).toString())
                path.startsWith("/tutor/jobs/") -> {
                    val id = path.removePrefix("/tutor/jobs/")
                    when (r.method) {
                        // a process gone can't save any more; a save lost on the way ([dropPut])
                        "PUT" -> if (frozen || dropPut(body!!)) code(500) else {
                            jobs[id] = body
                            puts++
                            afterPut(body)
                            ok(obj("ok" to true, "updated_at" to now).toString())
                        }
                        "DELETE" -> { jobs.remove(id); ok(obj("ok" to true, "deleted" to true).toString()) }
                        else -> jobs[id]?.let { ok(it.toString()) } ?: code(404)
                    }
                }
                path.startsWith("/tutor/tools/") -> {
                    val name = path.removePrefix("/tutor/tools/")
                    val clientId = body!!.str("client_id")
                    // the outbox's once: a repeat of a call id gets the first answer, the tool doesn't run again
                    if (clientId != null && once.containsKey(clientId)) return ok(once[clientId]!!)
                    calls += name to body
                    val args = body["args"]!!.jsonObject
                    val answer = when (name) {
                        "learner_state" -> result("""{"computed":{"next_session_id":"session-042"},"learner":{"name":"Jan"},"top_mistakes":{"genitive_plural":{"frequency":3}}}""")
                        "publish_module" -> {
                            val answerIndex = args["spec"]!!.jsonObject["exercises"]!!.jsonArray.first().jsonObject["answer"]!!.jsonPrimitive.content.toInt()
                            if (answerIndex > 1) result("publish_module: Nothing written. exercises[0].answer: 4 is out of range (2 options)", true)
                            else result("published mock-studio-drill v1")
                        }
                        "str_replace_based_edit_tool" -> {
                            val text = args.str("file_text").orEmpty()
                            if (runCatching { tutorJson.parseToJsonElement(text) }.isFailure) result("Nothing written: app/stories/mock.json doesn't validate as a story:\nUnexpected token", true)
                            else result("created app/stories/mock.json: a story (mock), checked by its validator; the app has it")
                        }
                        else -> result("done")
                    }
                    clientId?.let { once[it] = answer.peekBody(1_000_000).string() }
                    afterTool(name, body)
                    if (loseAnswer(name, body)) throw java.io.IOException("connection reset: the answer was lost")
                    answer
                }
                path == "/tutor/notebook" -> ok(obj("text" to "# Notebook\n- Jan likes tables", "updated_at" to "2026-10-18T20:00:00Z").toString())
                path == "/tutor/notebook/versions" -> ok(obj("versions" to JsonArray(listOf(
                    obj("at" to "2026-10-18T20:00:00.000Z", "why" to "notebook_write", "chars" to 20, "text" to "# Notebook\n- Jan likes tables"),
                    obj("at" to "2026-10-17T20:00:00.000Z", "why" to "notebook_write", "chars" to 20, "text" to "# Notebook\n- Jan liked lists"),
                ))).toString())
                path == "/tutor/notebook/restore" -> {
                    val at = body!!.str("at")
                    if (at != "2026-10-17T20:00:00.000Z" && at != "2026-10-18T20:00:00.000Z") code(404)
                    else { restored = at; ok(obj("text" to "# Notebook\n- Jan liked lists", "updated_at" to "2026-10-19T08:00:00Z").toString()) }
                }
                path == "/tutor/reports" -> { reports += body!!; ok(obj("ok" to true).toString()) }
                path == "/lookup" -> ok("""{"word":"peč"}""")
                else -> code(404)
            }
        }
    }
}
