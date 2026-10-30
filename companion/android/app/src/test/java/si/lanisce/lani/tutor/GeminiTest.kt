package si.lanisce.lani.tutor

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
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
import java.net.InetAddress
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Collections

/**
 * Gemini direct (docs/plans/05-built/providers.md, "Gemini") over the real client and a fake API on MockWebServer
 * (a free port the system picks: suites run at once on one machine): the stream with a tool call (its thought signature) then the reply, the request's
 * shape, the usage with the cached and thinking tokens, each error class (the key, the quota and the free tier, the
 * rate limit, overload), the finish reasons, thinking levels, images, our ids for a call without one, the free tier's
 * pace, the guard, the model list, the prices by day.
 */
class GeminiTest {
    private val specs = PhaseA.load()
    private val prefix = Prefix.bundled(specs, Guide(), Bundle::text)
    private val server = MockWebServer()
    private val sent = Collections.synchronizedList(mutableListOf<RecordedRequest>())
    private var answer: (RecordedRequest, Int) -> MockResponse = { _, _ -> MockResponse().setResponseCode(500) }
    private var freeSeen = 0

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                sent += request
                return answer(request, sent.count { it.method == "POST" })
            }
        }
        server.start(InetAddress.getByName("127.0.0.1"), 0)
    }

    @After fun stop() = server.shutdown()

    private fun sse(text: String) = MockResponse().setHeader("content-type", "text/event-stream").setBody(text)
    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setHeader("content-type", "application/json").setBody(body)

    private fun transport(pacer: GeminiPacer? = null) = GeminiTransport({ FAKE_GEMINI_KEY }, reroutedClient(server), pacer = pacer, onFreeTier = { freeSeen++ })
    private fun provider(billing: String = Billing.API_KEY, pacer: GeminiPacer? = null) = GeminiProvider(transport(pacer), billing)
    private fun body(r: RecordedRequest) = tutorJson.parseToJsonElement(r.body.readUtf8()) as JsonObject

    private fun runner(results: MutableList<ToolUse>) = ToolRunner { u ->
        results += u
        ToolOutcome(if (u.name == "guide") "the guide's text" else "{\"learner\":\"Jan\"}", false, ToolCallRecord(u.id, u.name, u.input, "read", "ok"))
    }

    // --- a turn ---------------------------------------------------------------------------------------------------------

    @Test fun `a turn over the real client - two calls with their signature, then the reply - the request's shape - the usage`() = runBlocking {
        answer = { _, n ->
            when (n) {
                1 -> sse(geminiSse(listOf(gText("Pogledam, kaj že znaš."), gCall("guide", obj("topic" to "feedback"), "fc-1", signature = "SIG-A"), gCall("learner_state", id = "fc-2"))))
                else -> sse(geminiSse(listOf(gText("Peč je *stove*."), gText("", signature = "SIG-END")), usage = geminiUsage(prompt = 6_400, cached = 6_000, candidates = 30, thoughts = 20)))
            }
        }
        val p = provider()
        val ran = mutableListOf<ToolUse>()
        val messages = mutableListOf(GeminiFormat.user(listOf(textBlock("<notebook>n</notebook>"), textBlock("<event kind=\"chat\">Kako se reče stove?</event>"))))
        val steps = mutableListOf<Step>()
        val r = TutorLoop(p, runner(ran)).run(prefix, "gemini-3.8-flash", Task.CHAT, messages, effort = "medium", onCall = { c -> c.step?.let(steps::add) })
        assertEquals(LoopEnd.Done, r.end)
        assertEquals(2, r.calls)
        assertEquals("Peč je *stove*.", r.endText)
        assertEquals(listOf("fc-1" to "guide", "fc-2" to "learner_state"), ran.map { it.id to it.name })
        assertEquals(obj("topic" to "feedback"), ran[0].input)

        val posts = sent.filter { it.method == "POST" }
        val first = posts[0]
        val url = first.requestUrl!!
        assertEquals("/v1beta/models/gemini-3.8-flash:streamGenerateContent", url.encodedPath)
        assertEquals("sse", url.queryParameter("alt"))
        assertEquals("generativelanguage.googleapis.com", first.getHeader("x-test-host"))
        assertEquals(FAKE_GEMINI_KEY, first.getHeader("x-goog-api-key"))
        assertNull("the key never in the URL", url.queryParameter("key"))
        assertNull(first.getHeader("Authorization"))
        val b1 = body(first)
        assertNull("the model is the URL's, not the body's", b1["model"])
        assertEquals(prefix.system, b1["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject.str("text"))
        val decl = b1["tools"]!!.jsonArray.single().jsonObject["functionDeclarations"]!!.jsonArray.map { it.jsonObject }
        assertEquals("the tools in the prefix's order", specs.map { it.name }, decl.map { it.str("name") })
        assertTrue(decl.all { it["parametersJsonSchema"] is JsonObject && it["parameters"] == null })
        assertEquals("AUTO", b1["toolConfig"]!!.jsonObject["functionCallingConfig"]!!.jsonObject.str("mode"))
        val gen = b1["generationConfig"]!!.jsonObject
        assertEquals("16000", gen["maxOutputTokens"]!!.jsonPrimitive.content)
        assertEquals("medium", gen["thinkingConfig"]!!.jsonObject.str("thinkingLevel"))
        assertTrue("no sampling parameters", listOf("temperature", "topP", "topK").none { gen[it] != null })

        // the second call: the model's turn exactly as it came (the text joined, the signature on its call), the results by id
        val c2 = contentsOf(body(posts[1]))
        assertEquals(listOf("user", "model", "user"), c2.map { it.role() })
        assertEquals(
            """{"role":"model","parts":[{"text":"Pogledam, kaj že znaš."},{"functionCall":{"id":"fc-1","name":"guide","args":{"topic":"feedback"}},"thoughtSignature":"SIG-A"},{"functionCall":{"id":"fc-2","name":"learner_state","args":{}}}]}""",
            c2[1].toString(),
        )
        val responses = GeminiFormat.parts(c2[2]).map { it["functionResponse"]!!.jsonObject }
        assertEquals(listOf("fc-1", "fc-2"), responses.map { it.str("id") })
        assertEquals("the guide's text", responses[0]["response"]!!.jsonObject.str("output"))
        // the thread keeps the last turn's signature part too (the docs: send it back, not enforced)
        assertEquals("SIG-END", GeminiFormat.parts(messages.last()).last().str("thoughtSignature"))

        // the usage: the prompt counts the cached part, the thoughts are output beside the candidates
        assertEquals(Usage(input = 1_000, cacheRead = 5_000, output = 120, reasoning = 40), steps[0].usage)
        assertEquals(Usage(input = 400, cacheRead = 6_000, output = 50, reasoning = 20), steps[1].usage)
        assertEquals(Stop.TOOL_USE, steps[0].stopReason)
        assertEquals("resp-1", steps[0].id)
        assertEquals("gemini-3.8-flash", steps[0].model)
        assertTrue(steps[0].firstTokenMs != null)
    }

    @Test fun `a turn at a level the model lacks is fitted - no level, no thinkingConfig`() {
        val argon = provider().request(prefix, "gemini-4-argon", Task.CONTENT, listOf(GeminiFormat.user(listOf(textBlock("x")))), 32_000, "max")
        assertEquals("high", argon["generationConfig"]!!.jsonObject["thinkingConfig"]!!.jsonObject.str("thinkingLevel"))
        val none = provider().request(prefix, "gemini-4-argon", Task.CHAT, emptyList(), 16_000, null)
        assertNull(none["generationConfig"]!!.jsonObject["thinkingConfig"])
        val lite = provider().request(prefix, "gemini-3.5-flash-lite", Task.ROUTINE, emptyList(), 16_000, "minimal")
        assertEquals("minimal", lite["generationConfig"]!!.jsonObject["thinkingConfig"]!!.jsonObject.str("thinkingLevel"))
        // the levels per model (the thinking guide): Argon and 3.8 Flash low to high, 3.6 Flash and Flash-Lite from minimal
        assertEquals(listOf("low", "medium", "high"), ModelCatalog.efforts("gemini-4-argon"))
        assertEquals(listOf("low", "medium", "high"), ModelCatalog.efforts("gemini-3.8-flash"))
        assertEquals(listOf("minimal", "low", "medium", "high"), ModelCatalog.efforts("gemini-3.6-flash"))
        assertEquals(listOf("minimal", "low", "medium", "high"), ModelCatalog.efforts("gemini-3.5-flash-lite"))
        assertEquals("low", ModelCatalog.fit("minimal", "gemini-4-argon"))
        assertEquals("medium", ModelCatalog.defaultEffort("gemini-4-argon"))
        assertEquals("minimal", ModelCatalog.defaultEffort("gemini-3.5-flash-lite"))
        // a thread keeps its level: no per-message effort on Gemini (the docs don't say what a change does to the cache)
        assertFalse(ModelCatalog.perMessageEffort("gemini-4-argon"))
        val appended = GeminiFormat.append(listOf(GeminiFormat.user(listOf(textBlock("a")))), listOf(textBlock("b")), "medium", "high")
        assertEquals("the blocks join the unanswered user turn, no effort item", 1, appended.size)
        assertEquals(listOf("a", "b"), GeminiFormat.parts(appended.single()).map { it.str("text") })
    }

    @Test fun `a photo goes as inlineData - a call without the API's id gets ours, kept beside the parts and never sent`() = runBlocking {
        val photo = obj("type" to "image", "source" to obj("type" to "base64", "media_type" to "image/jpeg", "data" to "AAAA"))
        val u = GeminiFormat.user(listOf(textBlock("Kaj je to?"), photo))
        assertEquals(obj("inlineData" to obj("mimeType" to "image/jpeg", "data" to "AAAA")), GeminiFormat.parts(u)[1])
        assertEquals("counted on the call's record", 1, ShadowImages.count(listOf(u)))
        assertFalse("never in the export", ShadowImages.omit(u).toString().contains("AAAA"))

        answer = { _, _ -> sse(geminiSse(listOf(gCall("guide", obj("topic" to "x"), id = null, signature = "S1"), gCall("learner_state", id = null)), id = "r9")) }
        val step = transport().stream(provider().request(prefix, "gemini-3.8-flash", Task.CHAT, listOf(u), 16_000, "low"))
        val item = step.items.single()
        assertEquals(listOf("lani_gc_r9_0", "lani_gc_r9_1"), step.toolCalls.map { it.id })
        assertEquals(listOf("lani_gc_r9_0", "lani_gc_r9_1"), item[GeminiFormat.IDS]!!.jsonArray.map { it.jsonPrimitive.content })
        val thread = listOf(u, item) + GeminiFormat.results(step.toolCalls, step.toolCalls.map { ToolOutcome("ok", it.name == "guide", ToolCallRecord(it.id, it.name, it.input, "read", "ok")) })
        val wire = provider().request(prefix, "gemini-3.8-flash", Task.CHAT, thread, 16_000, "low")
        assertFalse("our ids never go to the API", wire.toString().contains("lani_"))
        val fr = GeminiFormat.parts(contentsOf(wire)[2]).map { it["functionResponse"]!!.jsonObject }
        assertTrue(fr.all { it["id"] == null })
        assertEquals("a failed result is the response's error", "ok", fr[0]["response"]!!.jsonObject.str("error"))
        assertEquals("ok", fr[1]["response"]!!.jsonObject.str("output"))
    }

    // --- errors, finish reasons ---------------------------------------------------------------------------------------

    private fun googleError(code: Int, status: String, message: String, vararg details: JsonObject) =
        obj("error" to obj("code" to code, "message" to message, "status" to status, "details" to JsonArray(details.toList()))).toString()

    private fun errorOf(code: Int, body: String, retryAfter: String? = null): ApiError = runBlocking {
        answer = { _, _ -> json(code, body).also { r -> retryAfter?.let { r.setHeader("retry-after", it) } } }
        try {
            transport().stream(provider().request(prefix, "gemini-3.8-flash", Task.CHAT, listOf(GeminiFormat.user(listOf(textBlock("x")))), 16_000, "low"))
            error("no error")
        } catch (e: ApiError) {
            e
        }
    }

    @Test fun `errors per class - the key, billing and the credit, the quota and the free tier, the rate limit, overload`() {
        val invalid = errorOf(
            400,
            googleError(
                400, "INVALID_ARGUMENT", "API key not valid. Please pass a valid API key.",
                obj("@type" to "type.googleapis.com/google.rpc.ErrorInfo", "reason" to "API_KEY_INVALID", "domain" to "googleapis.com"),
            ),
        )
        assertTrue(invalid is ApiError.Auth)
        assertTrue(errorOf(403, googleError(403, "PERMISSION_DENIED", "Your API key was reported as leaked. Please use another API key.")) is ApiError.Auth)
        val region = errorOf(400, googleError(400, "FAILED_PRECONDITION", "Gemini API free tier is not available in your country. Please enable billing on your project in Google AI Studio."))
        assertTrue(region is ApiError.LowCredit && region.limit)
        val prepaid = errorOf(402, googleError(402, "RESOURCE_EXHAUSTED", "Your Prepay credit balance is depleted."))
        assertTrue(prepaid is ApiError.LowCredit && !prepaid.limit)
        val rpm = errorOf(
            429,
            googleError(
                429, "RESOURCE_EXHAUSTED", "You exceeded your current quota.",
                obj("@type" to "type.googleapis.com/google.rpc.RetryInfo", "retryDelay" to "37s"),
            ),
        )
        assertTrue(rpm is ApiError.RateLimited)
        assertEquals(37_000L, (rpm as ApiError.RateLimited).retryAfterMs)
        assertTrue("retried after its delay", rpm.retryable)
        assertEquals(0, freeSeen)
        // the free tier's day used up: not retried, the key seen to be on the free tier
        val daily = errorOf(
            429,
            googleError(
                429, "RESOURCE_EXHAUSTED", "Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 20, model: gemini-3.8-flash",
                obj("@type" to "type.googleapis.com/google.rpc.QuotaFailure", "violations" to JsonArray(listOf(obj("quotaMetric" to "generativelanguage.googleapis.com/generate_content_free_tier_requests", "quotaId" to "GenerateRequestsPerDayPerProjectPerModel-FreeTier")))),
                obj("@type" to "type.googleapis.com/google.rpc.RetryInfo", "retryDelay" to "10h17m5.72s"),
            ),
        )
        assertTrue(daily is ApiError.LowCredit && daily.limit && !daily.retryable)
        assertEquals(1, freeSeen)
        // a model without free quota on this key (Argon on the free tier): "limit: 0"
        val zero = errorOf(429, googleError(429, "RESOURCE_EXHAUSTED", "Quota exceeded for metric: generate_content_free_tier_requests, limit: 0, model: gemini-4-argon"))
        assertTrue(zero is ApiError.LowCredit && zero.message!!.contains("no free tier"))
        // a 429 without details: the rate limit, after retry-after
        val bare = errorOf(429, """{"error":{"code":429,"message":"Resource has been exhausted (e.g. check quota).","status":"RESOURCE_EXHAUSTED"}}""", retryAfter = "3")
        assertEquals(3_000L, (bare as ApiError.RateLimited).retryAfterMs)
        assertTrue(errorOf(503, googleError(503, "UNAVAILABLE", "The model is overloaded. Please try again later.")) is ApiError.Overloaded)
        assertTrue(errorOf(500, googleError(500, "INTERNAL", "An internal error has occurred.")) is ApiError.Overloaded)
        assertTrue(errorOf(504, googleError(504, "DEADLINE_EXCEEDED", "Deadline expired.")) is ApiError.Overloaded)
        assertTrue(errorOf(404, googleError(404, "NOT_FOUND", "models/gemini-9 is not found")) is ApiError.BadRequest)
        // an array-wrapped body (the non-SSE stream's shape)
        assertTrue(errorOf(400, "[" + googleError(400, "INVALID_ARGUMENT", "API key not valid. Please pass a valid API key.") + "]") is ApiError.Auth)
        assertEquals(37_000L, GeminiErrors.duration("37s"))
        assertEquals(1_500L, GeminiErrors.duration("1.5s"))
        assertEquals(37_025_720L, GeminiErrors.duration("10h17m5.72s"))
    }

    @Test fun `finish reasons - a block is a refusal, a cut-off keeps its text, a botched call is made again, an early end is the network`() = runBlocking {
        fun stream(text: String): Step = runBlocking {
            answer = { _, _ -> sse(text) }
            transport().stream(provider().request(prefix, "gemini-3.8-flash", Task.CHAT, listOf(GeminiFormat.user(listOf(textBlock("x")))), 16_000, "low"))
        }
        val safety = stream(geminiSse(listOf(gText("Tega ne")), finish = "SAFETY"))
        assertEquals(Stop.REFUSAL, safety.stopReason)
        assertEquals("safety", safety.stopDetails!!.str("category"))
        val blocked = stream("data: " + obj("promptFeedback" to obj("blockReason" to "PROHIBITED_CONTENT"), "usageMetadata" to geminiUsage(candidates = 0, thoughts = 0)) + "\n\n")
        assertEquals(Stop.REFUSAL, blocked.stopReason)
        assertEquals("prohibited_content", blocked.stopDetails!!.str("category"))
        val cut = stream(geminiSse(listOf(gText("Ker je peč topla, ")), finish = "MAX_TOKENS"))
        assertEquals(Stop.MAX_TOKENS, cut.stopReason)
        assertEquals("Ker je peč topla, ", Threads.text(cut.kept))
        val malformed = try {
            stream(geminiSse(emptyList(), finish = "MALFORMED_FUNCTION_CALL"))
            null
        } catch (e: ApiError) {
            e
        }
        assertTrue("made again with the loop's backoff", malformed is ApiError.Overloaded && malformed.retryable)
        val early = try {
            stream("data: " + geminiChunk(listOf(gText("Ker"))) + "\n\n")
            null
        } catch (e: ApiError) {
            e
        }
        assertTrue(early is ApiError.Network)
        val inStream = try {
            stream("data: " + geminiChunk(listOf(gText("Ker"))) + "\n\ndata: {\"error\":{\"code\":503,\"message\":\"overloaded\",\"status\":\"UNAVAILABLE\"}}\n\n")
            null
        } catch (e: ApiError) {
            e
        }
        assertTrue(inStream is ApiError.Overloaded)
    }

    // --- the free tier, the guard, the list, the prices -------------------------------------------------------------

    @Test fun `the free tier is paced, slower after a 429, back after calls go through - a paid key isn't`() = runBlocking {
        var now = 0L
        val waits = mutableListOf<Long>()
        val pacer = GeminiPacer(clock = { now }, sleep = { waits += it; now += it })
        pacer.await()
        assertTrue("paid: no pace", waits.isEmpty())
        pacer.free = true
        repeat(3) { pacer.await() }
        assertEquals(listOf(6_000L, 6_000L), waits)
        pacer.limited(37_000)
        assertEquals(12_000L, pacer.gapMs)
        pacer.await()
        assertEquals("its retry delay", 37_000L, waits.last())
        pacer.ok()
        assertEquals(9_000L, pacer.gapMs)
        repeat(6) { pacer.ok() }
        assertEquals(GeminiPacer.BASE_GAP_MS, pacer.gapMs)
        // the transport tells the pacer of a 429
        val p2 = GeminiPacer(clock = { now }, sleep = { now += it }).also { it.free = true }
        answer = { _, _ -> json(429, """{"error":{"code":429,"message":"slow down","status":"RESOURCE_EXHAUSTED"}}""") }
        runCatching { transport(p2).stream(provider().request(prefix, "gemini-3.8-flash", Task.CHAT, listOf(GeminiFormat.user(listOf(textBlock("x")))), 16_000, "low")) }
        assertEquals(12_000L, p2.gapMs)
    }

    @Test fun `the key goes to the Gemini API only, and never into a log or the export`() {
        assertTrue(GeminiGuard.mayCarryKey("https://generativelanguage.googleapis.com/v1beta/models".toHttpUrl()))
        assertFalse(GeminiGuard.mayCarryKey("http://generativelanguage.googleapis.com/v1beta/models".toHttpUrl()))
        assertFalse(GeminiGuard.mayCarryKey("https://generativelanguage.googleapis.com.evil.example/v1beta".toHttpUrl()))
        assertFalse(GeminiGuard.mayCarryKey("https://aistudio.google.com/apikey".toHttpUrl()))
        assertFalse(Redact.of("the key $FAKE_GEMINI_KEY in a line").contains(FAKE_GEMINI_KEY))
        assertFalse(Redact.all("x-goog-api-key: $FAKE_GEMINI_KEY").contains(FAKE_GEMINI_KEY))
    }

    @Test fun `the model list - Gemini's that generate content`() = runBlocking {
        answer = { _, _ -> json(200, GEMINI_MODELS) }
        val models = transport().models()
        assertEquals(listOf("gemini-4-argon", "gemini-3.8-flash", "gemini-3.5-flash-lite"), models.map { it.id })
        assertEquals(65_536L, models[0].maxTokens)
        assertEquals("/v1beta/models", sent.last().requestUrl!!.encodedPath)
        assertEquals(FAKE_GEMINI_KEY, sent.last().getHeader("x-goog-api-key"))
        // the families resolve to the list's own (Flash never a Flash-Lite)
        val cat = ModelCatalog(models)
        assertEquals("gemini-3.8-flash", cat.own(Family.FLASH))
        assertEquals("gemini-3.5-flash-lite", cat.own(Family.FLASH_LITE))
        assertEquals("gemini-4-argon", cat.own(Family.ARGON))
        assertEquals(Family.FLASH, Family.ofModel("gemini-3.8-flash"))
        assertEquals(Family.FLASH_LITE, Family.ofModel("gemini-3.5-flash-lite"))
        assertEquals(ProviderId.GEMINI, ProviderId.of("gemini-4-argon"))
    }

    @Test fun `prices - Argon's introductory price through 2026-11-30, then the full one - the free tier costs nothing, its API-equivalent kept`() {
        fun at(day: String) = LocalDate.parse(day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() + 3_600_000
        val u = Usage(input = 1_000_000, cacheRead = 1_000_000, output = 1_000_000)
        val nov = Prices.parse(Bundle.text("prices.json")!!) { at("2026-11-30") }
        val dec = Prices.parse(Bundle.text("prices.json")!!) { at("2026-12-01") }
        assertEquals(2.0 + 0.10 + 10.0, nov.cost("gemini-4-argon", u)!!, 1e-9)
        assertEquals(4.0 + 0.20 + 20.0, dec.cost("gemini-4-argon", u)!!, 1e-9)
        assertEquals(0.75 + 0.075 + 3.75, nov.cost("gemini-3.8-flash", u)!!, 1e-9)
        assertEquals(0.75 + 0.075 + 3.75, dec.cost("gemini-3.8-flash", u)!!, 1e-9)
        val jan = Prices.parse(Bundle.text("prices.json")!!) { at("2027-01-01") }
        assertEquals(1.50 + 0.15 + 7.50, jan.cost("gemini-3.8-flash", u)!!, 1e-9)
        assertEquals(0.30 + 0.03 + 2.50, jan.cost("gemini-3.5-flash-lite", u)!!, 1e-9)
        val free = nov.priced("gemini-3.8-flash", u, Billing.FREE_TIER)
        assertEquals(0.0, free.cost!!, 0.0)
        assertEquals(0.75 + 0.075 + 3.75, free.apiEquivalent!!, 1e-9)
        // the rest as before: Claude's and OpenAI's rows
        assertEquals(2.0 + 0.10 + 10.0, nov.cost("claude-sonnet-5-5", u)!!, 1e-9)
    }

}
