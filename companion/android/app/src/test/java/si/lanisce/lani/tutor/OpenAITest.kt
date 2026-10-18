package si.lanisce.lani.tutor

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.Base64

/**
 * OpenAI's Responses API over the real client and a scripted server (no network): the stream put back together, its
 * usage, each error class, reasoning items carried across tool calls and turns, store: false, the request's shape with
 * an API key and with the ChatGPT plan, the tools' schemas, images, effort.
 */
class OpenAITest {
    private val specs = PhaseA.load()
    private val prefix = Prefix.bundled(specs, Guide(), Bundle::text)

    private fun transport(api: Answering, billing: String = Billing.API_KEY, token: String = FAKE_OPENAI_KEY) =
        OpenAITransport({ OpenAIBearer(token, billing) }, OkHttpClient.Builder().addInterceptor(api).build())

    private fun streamed(sse: String, type: String? = "text/event-stream"): Pair<Step, Answering> = runBlocking {
        val api = Answering { Triple(200, sse, type ?: "application/octet-stream") }
        val step = transport(api).stream(obj("model" to "gpt-6.1-sol", "input" to JsonArray(emptyList())))
        step to api
    }

    // --- the stream -------------------------------------------------------------------------------------------------

    @Test fun `a stream with reasoning and two tool calls - the items as the done events have them, the calls, usage`() {
        val (step, api) = streamed(
            responseSse(
                listOf(
                    reasoningItem("rs_1"),
                    functionCall("fc_1", "call_a", "guide", obj("topic" to "feedback")),
                    functionCall("fc_2", "call_b", "learner_state"),
                ),
                usage = openAIUsage(input = 13_000, cached = 12_000, written = 800, output = 300, reasoning = 220),
            ),
        )
        assertEquals(Stop.TOOL_USE, step.stopReason)
        assertEquals(listOf("call_a" to "guide", "call_b" to "learner_state"), step.toolCalls.map { it.id to it.name })
        assertEquals(obj("topic" to "feedback"), step.toolCalls[0].input)
        assertTrue(step.toolCalls.all { it.badJson == null })
        // the reasoning item with its encrypted content (the added event's had none), passed back as it came
        assertEquals(listOf("reasoning", "function_call", "function_call"), step.items.map { it.str("type") })
        assertEquals("enc-rs_1", step.items[0].str("encrypted_content"))
        // input counts the cached and written tokens: the rest is 200
        assertEquals(Usage(input = 200, cacheWrite5m = 800, cacheRead = 12_000, output = 300, reasoning = 220), step.usage)
        assertEquals("gpt-6.1-sol", step.model)
        assertEquals("resp_1", step.id)
        assertTrue(step.firstTokenMs != null)
        val r = api.requests.single()
        assertEquals("https://api.openai.com/v1/responses", r.url.toString())
        assertEquals("Bearer $FAKE_OPENAI_KEY", r.header("Authorization"))
        assertNull(r.header("x-api-key"))
        assertEquals("text/event-stream", r.header("accept"))
        assertEquals(true, bodyOf(r)["stream"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test fun `a reply as text ends the turn, its phase kept`() {
        val (step, _) = streamed(responseSse(listOf(reasoningItem("rs_2"), messageItem("msg_1", "Peč je stove."))))
        assertEquals(Stop.END_TURN, step.stopReason)
        assertEquals("Peč je stove.", step.text)
        assertEquals("final_answer", step.items[1].str("phase"))
        assertTrue(step.toolCalls.isEmpty())
    }

    @Test fun `a stream without a content type still counts, by its events`() {
        val (step, _) = streamed(responseSse(listOf(messageItem("m", "ok"))), type = null)
        assertEquals("ok", step.text)
    }

    @Test fun `arguments that aren't JSON are kept as bad input, never run`() {
        val (step, _) = streamed(responseSse(listOf(functionCall("fc", "call_x", "reply", raw = "{\"text\": \"cut"))))
        assertEquals("{\"text\": \"cut", step.toolCalls.single().badJson)
    }

    @Test fun `a reasoning item without its encrypted content isn't kept (it can't be replayed with store false)`() {
        val (step, _) = streamed(responseSse(listOf(reasoningItem("rs_x", encrypted = null), messageItem("m", "hi"))))
        assertEquals(listOf("message"), step.items.map { it.str("type") })
    }

    @Test fun `cut off at max_output_tokens - max_tokens, its text kept`() {
        val (step, _) = streamed(responseSse(listOf(reasoningItem("rs"), messageItem("m", "Ker je …")), status = "incomplete", incomplete = "max_output_tokens"))
        assertEquals(Stop.MAX_TOKENS, step.stopReason)
        assertEquals(listOf("message"), step.kept.map { it.str("type") })
    }

    @Test fun `refusals - a refusal part, the content filter, a policy failure`() {
        val (a, _) = streamed(responseSse(listOf(refusalItem("m", "I can't help with that."))))
        assertEquals(Stop.REFUSAL, a.stopReason)
        assertEquals("I can't help with that.", a.stopDetails!!.str("refusal"))
        val (b, _) = streamed(responseSse(listOf(messageItem("m", "…")), status = "incomplete", incomplete = "content_filter"))
        assertEquals(Stop.REFUSAL, b.stopReason)
        assertEquals("content_filter", b.stopDetails!!.str("category"))
        val failed = "event: response.failed\ndata: " + obj("type" to "response.failed", "response" to obj("id" to "r", "status" to "failed", "error" to obj("code" to "bio_policy", "message" to "no"))) + "\n\n"
        val (c, _) = streamed(failed)
        assertEquals(Stop.REFUSAL, c.stopReason)
        assertEquals("bio_policy", c.stopDetails!!.str("category"))
    }

    @Test fun `errors in the stream and a stream that stops early`() {
        fun fails(sse: String): ApiError = try {
            streamed(sse)
            fail("no error"); throw IllegalStateException()
        } catch (e: ApiError) {
            e
        }
        val error = { code: String, type: String? -> "event: error\ndata: " + obj("type" to "error", "code" to code, "message" to "m", "param" to null).let { if (type != null) JsonObject(it + ("error" to obj("type" to type, "code" to code, "message" to "m"))) else it } + "\n\n" }
        assertTrue(fails(error("rate_limit_exceeded", null)) is ApiError.RateLimited)
        assertTrue(fails(error("server_is_overloaded", null)) is ApiError.Overloaded)
        val allowance = fails(error("subscription_sharing_usage_limit_exceeded", null))
        assertTrue(allowance is ApiError.LowCredit && allowance.allowance && allowance.kind == "allowance")
        assertTrue(fails(error("insufficient_quota", "insufficient_quota")) is ApiError.LowCredit)
        assertTrue(fails(error("subscription_sharing_invalid_user", null)) is ApiError.Auth)
        val failed = "event: response.failed\ndata: " + obj("type" to "response.failed", "response" to obj("status" to "failed", "error" to obj("code" to "server_error", "message" to "x"))) + "\n\n"
        assertTrue(fails(failed) is ApiError.Overloaded)
        // created, then nothing: the network
        val early = responseSse(listOf(messageItem("m", "x"))).substringBefore("event: response.completed")
        assertTrue(fails(early) is ApiError.Network)
    }

    @Test fun `each HTTP error class`() {
        fun e(code: Int, body: String, retryAfter: String? = null) = OpenAIErrors.of(code, body, retryAfter)
        fun err(code: String?, type: String? = null) = obj("error" to obj("message" to "m", "type" to type, "param" to null, "code" to code)).toString()
        assertTrue(e(401, err("invalid_api_key", "invalid_request_error")) is ApiError.Auth)
        assertTrue(e(403, err("subscription_sharing_user_not_eligible")) is ApiError.Auth)
        assertTrue(e(403, """{"detail":"region not permitted"}""") is ApiError.Auth)
        assertTrue(e(403, err("subscription_sharing_route_not_supported")) is ApiError.BadRequest)
        val credit = e(429, err("credit_balance_exhausted", "insufficient_quota"))
        assertTrue(credit is ApiError.LowCredit && !credit.limit && !credit.retryable && credit.kind == "low_credit")
        assertTrue((e(429, err(null, "insufficient_quota")) as ApiError.LowCredit).kind == "low_credit")
        for (c in listOf("organization_spend_limit_exceeded", "project_spend_limit_exceeded", "organization_usage_limit_exceeded")) {
            assertEquals(c, "spend_limit", e(429, err(c)).kind)
        }
        assertEquals("allowance", e(429, err("subscription_sharing_usage_limit_exceeded")).kind)
        val rate = e(429, err("rate_limit_exceeded", "requests"), "3")
        assertTrue(rate is ApiError.RateLimited && rate.retryAfterMs == 3_000L)
        assertTrue(e(429, err("slow_down", "rate_limit_error")) is ApiError.RateLimited)
        assertTrue(e(503, err("server_is_overloaded", "service_unavailable_error")) is ApiError.Overloaded)
        assertTrue(e(503, err("subscription_sharing_usage_unavailable")).retryable)
        assertTrue(e(500, "oops") is ApiError.Overloaded)
        assertTrue(e(400, err("subscription_sharing_unsupported_capability", "invalid_request_error")) is ApiError.BadRequest)
        assertTrue(e(400, err("context_length_exceeded", "invalid_request_error")) is ApiError.BadRequest)
    }

    @Test fun `an HTTP error before the stream - classified, never parsed as one`() = runBlocking {
        val api = Answering { Triple(429, """{"error":{"message":"You exceeded your current quota","type":"insufficient_quota","code":"insufficient_quota"}}""", "application/json") }
        try {
            transport(api).stream(obj("model" to "gpt-6-luna"))
            fail("no error")
        } catch (e: ApiError.LowCredit) {
            assertTrue(e.message!!.contains("insufficient_quota"))
        }
    }

    @Test fun `the models of an API key and of the ChatGPT plan`() = runBlocking {
        val key = transport(Answering { Triple(200, """{"object":"list","data":[{"id":"gpt-6-astra","created":1790000000,"owned_by":"openai"}]}""", "application/json") })
        assertEquals(listOf(ModelInfo("gpt-6-astra", createdAt = "2026-09-21T14:13:20Z")), key.models())
        val plan = transport(
            Answering { Triple(200, """{"models":[{"slug":"gpt-6.1-sol","display_name":"GPT-6.1 Sol","visibility":"list"},{"slug":"internal","display_name":"x","visibility":"hide"}]}""", "application/json") },
            Billing.CHATGPT_PLAN, "access-token-xyz",
        )
        assertEquals(listOf(ModelInfo("gpt-6.1-sol", displayName = "GPT-6.1 Sol")), plan.models())
    }

    // --- the request and the loop -----------------------------------------------------------------------------------

    /** guide first (with reasoning), then reply, then a closing text; the next turn ends at once. */
    private val script = ScriptedResponses { body, n ->
        val input = inputOf(body)
        val last = input.last()
        when {
            last.str("type") == "message" -> listOf(reasoningItem("rs_$n"), functionCall("fc_$n", "call_$n", "guide", obj("topic" to "feedback")))
            input.none { it.str("type") == "function_call" && it.str("name") == "reply" } ->
                listOf(reasoningItem("rs_$n"), messageItem("m_$n", "Pogledam.", "commentary"), functionCall("fc_$n", "call_$n", "reply", obj("conversation_id" to "main", "text" to "Peč = stove")))
            else -> listOf(messageItem("m_$n", "Opravljeno."))
        }
    }

    private fun runner(log: MutableList<ToolUse>) = ToolRunner { u ->
        log += u
        ToolOutcome(if (u.name == "guide") "the guide's text" else "delivered", u.name == "learner_state", ToolCallRecord(u.id, u.name, u.input, "read", "ok"))
    }

    @Test fun `a turn over the loop - reasoning items and calls go back untouched, store false, the prefix stable`() = runBlocking {
        val provider = OpenAIProvider(OpenAITransport({ OpenAIBearer(FAKE_OPENAI_KEY, Billing.API_KEY) }, script.client), Billing.API_KEY)
        val ran = mutableListOf<ToolUse>()
        val thread = OpenAIFormat.append(emptyList(), listOf(textBlock("<notebook>…</notebook>"), textBlock("<event kind=\"chat\" conversation_id=\"main\">Kako se reče stove?</event>")), "medium", null).toMutableList()
        val r = TutorLoop(provider, runner(ran)).run(prefix, "gpt-6.1-sol", Task.CHAT, thread, effort = "medium")
        assertEquals(LoopEnd.Done, r.end)
        assertEquals(3, r.calls)
        assertEquals("Opravljeno.", r.endText)
        assertEquals(listOf("guide", "reply"), ran.map { it.name })
        val posts = script.posts()
        // every request: store false, streamed, instructions (no system message), the same tools, the effort, room
        for (b in posts) {
            assertEquals(false, b["store"]!!.jsonPrimitive.content.toBoolean())
            assertEquals(true, b["stream"]!!.jsonPrimitive.content.toBoolean())
            assertEquals(prefix.system, b["instructions"]!!.jsonPrimitive.content)
            assertEquals("medium", b["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
            assertEquals(16_000, b["max_output_tokens"]!!.jsonPrimitive.content.toInt())
            assertEquals(listOf("reasoning.encrypted_content"), b["include"]!!.jsonArray.map { it.jsonPrimitive.content })
            assertEquals("auto", b["tool_choice"]!!.jsonPrimitive.content)
            assertTrue(inputOf(b).none { it.role() == "system" || it.role() == "developer" })
            for (k in listOf("temperature", "top_p", "previous_response_id", "metadata", "truncation", "user", "safety_identifier", "prompt_cache_retention")) assertNull(k, b[k])
        }
        assertEquals(1, posts.map { it["tools"].toString() + it["prompt_cache_key"] }.toSet().size)
        // the second request: the first's output as received, then the result by call_id
        val second = inputOf(posts[1])
        assertEquals(listOf("message", "reasoning", "function_call", "function_call_output"), second.map { it.str("type") })
        assertEquals("enc-rs_1", second[1].str("encrypted_content"))
        assertEquals("call_1", second[3].str("call_id"))
        assertEquals("the guide's text", second[3].str("output"))
        // the third: everything before, the commentary message with its phase, the reply's call and its output
        val third = inputOf(posts[2])
        assertEquals(second, third.take(4))
        assertEquals("commentary", third[5].str("phase"))
        assertEquals(listOf("reasoning", "message", "function_call", "function_call_output"), third.drop(4).map { it.str("type") })
        // the thread kept every item, in order
        assertEquals(third + messageItem("m_3", "Opravljeno."), thread)
    }

    @Test fun `reasoning items round-trip across turns, and a failed tool says so`() = runBlocking {
        val provider = OpenAIProvider(OpenAITransport({ OpenAIBearer(FAKE_OPENAI_KEY, Billing.API_KEY) }, script.client), Billing.API_KEY)
        val ran = mutableListOf<ToolUse>()
        val thread = OpenAIFormat.append(emptyList(), listOf(textBlock("<event conversation_id=\"main\">Ena</event>")), "medium", null).toMutableList()
        TutorLoop(provider, runner(ran)).run(prefix, "gpt-6.1-sol", Task.CHAT, thread, effort = "medium")
        val after = thread.toList()
        // the next turn: the whole history, then its user message
        thread.clear()
        thread += OpenAIFormat.append(after, listOf(textBlock("<event conversation_id=\"main\">Dva</event>")), "medium", null)
        TutorLoop(provider, runner(ran)).run(prefix, "gpt-6.1-sol", Task.CHAT, thread, effort = "medium")
        val next = inputOf(script.posts()[3])
        assertEquals(after, next.take(after.size))
        assertTrue(next.take(after.size).filter { it.str("type") == "reasoning" }.all { it.str("encrypted_content")!!.startsWith("enc-") })
        assertEquals("Dva", next[after.size].blocks().single().str("text")!!.substringAfter(">").substringBefore("<"))
        // an error result: the Responses API has no flag, so it says so
        val results = OpenAIFormat.results(listOf(ToolUse("c", "learner_state", JsonObject(emptyMap()))), listOf(ToolOutcome("no bridge", true, ToolCallRecord("c", "learner_state", JsonObject(emptyMap()), "read", "x"))))
        assertEquals("Error: no bridge", results.single().str("output"))
    }

    @Test fun `with the ChatGPT plan - the tools in a namespace, no max_output_tokens, the bearer the access token`() = runBlocking {
        val access = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ4In0.c2lnbmF0dXJl"
        val provider = OpenAIProvider(OpenAITransport({ OpenAIBearer(access, Billing.CHATGPT_PLAN) }, script.client), Billing.CHATGPT_PLAN)
        val thread = mutableListOf(OpenAIFormat.user(listOf(textBlock("<event conversation_id=\"main\">Živjo</event>"))))
        TutorLoop(provider, runner(mutableListOf())).run(prefix, "gpt-6-luna", Task.ROUTINE, thread, effort = "none")
        val b = script.posts().first()
        assertNull(b["max_output_tokens"])
        val tools = b["tools"]!!.jsonArray
        assertEquals(1, tools.size)
        assertEquals("namespace", tools[0].jsonObject.str("type"))
        assertEquals("lani", tools[0].jsonObject.str("name"))
        assertEquals(PhaseA.TOOLS.map { it.first }, tools[0].jsonObject["tools"]!!.jsonArray.map { it.jsonObject.str("name") })
        assertEquals("none", b["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertEquals("Bearer $access", script.http.requests.first().header("Authorization"))
        // the plan's prefix differs from the key's: a thread made with one restarts with the other
        assertFalse(provider.prefixHash(prefix) == OpenAIProvider(OpenAITransport({ null }), Billing.API_KEY).prefixHash(prefix))
    }

    @Test fun `the plan's 401 renews the sign-in once and tries again`() = runBlocking {
        var renewed = 0
        val api = ScriptedResponses(answer = { _, n -> if (n == 1) Triple(401, """{"error":{"code":"subscription_sharing_invalid_user","message":"x"}}""", "application/json") else null }) { _, _ -> listOf(messageItem("m", "ok")) }
        val provider = OpenAIProvider(OpenAITransport({ OpenAIBearer("t", Billing.CHATGPT_PLAN) }, api.client), Billing.CHATGPT_PLAN, renew = { renewed++; true })
        val step = provider.call(provider.request(prefix, "gpt-6.1-sol", Task.CHAT, listOf(OpenAIFormat.user(listOf(textBlock("x")))), 1000, "low"))
        assertEquals("ok", step.text)
        assertEquals(1, renewed)
        assertEquals(2, api.calls)
    }

    @Test fun `the bearer goes only to the Responses API`() {
        assertTrue(OpenAIGuard.mayCarryBearer("https://api.openai.com/v1/responses".toHttpUrl()))
        assertFalse(OpenAIGuard.mayCarryBearer("http://api.openai.com/v1/responses".toHttpUrl()))
        assertFalse(OpenAIGuard.mayCarryBearer("https://api.openai.com.evil.example/v1".toHttpUrl()))
        assertFalse(OpenAIGuard.mayCarryBearer("https://auth.openai.com/api/accounts/oauth/token".toHttpUrl()))
        assertTrue(OpenAIGuard.mayCarryOAuth("https://auth.openai.com/api/accounts/oauth/token".toHttpUrl()))
        assertFalse(OpenAIGuard.mayCarryOAuth("https://api.openai.com/v1/models".toHttpUrl()))
        assertEquals("OpenAIBearer(‹…›, api_key)", OpenAIBearer(FAKE_OPENAI_KEY, Billing.API_KEY).toString())
        assertEquals("key sk-‹…› and ‹token›", Redact.of("key $FAKE_OPENAI_KEY and eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ4In0.c2lnbmF0dXJl"))
    }

    // --- the format -------------------------------------------------------------------------------------------------

    @Test fun `a chat's photo goes as input_image with a data URL`() {
        val f = File.createTempFile("photo", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)); deleteOnExit() }
        val user = OpenAIFormat.user(listOf(ShadowImages.block(f), textBlock("<event>photo</event>")))
        val parts = user.blocks()
        assertEquals("input_image", parts[0].str("type"))
        assertEquals("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4)), parts[0].str("image_url"))
        assertEquals("auto", parts[0].str("detail"))
        assertEquals("input_text", parts[1].str("type"))
        assertEquals(1, ShadowImages.count(listOf(user)))
        // the export keeps a note of its size, never the photo
        assertFalse(ShadowImages.omit(user).toString().contains(Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4))))
        assertTrue(ShadowImages.omit(reasoningItem("rs")).toString().contains("encrypted reasoning"))
    }

    @Test fun `an effort change is a configuration_update before the next user message, never two side by side`() {
        val answered = listOf(OpenAIFormat.user(listOf(textBlock("a"))), messageItem("m", "b"))
        val up = OpenAIFormat.append(answered, listOf(textBlock("c")), "low", "high")
        assertEquals(listOf("message", "message", "configuration_update", "message"), up.map { it.str("type") })
        assertEquals("high", Effort.of(up[2]))
        assertEquals("high", Effort.inEffect(up, "low"))
        // the same level again: nothing new; a turn that stopped before its answer: its update placed again, not stacked
        assertEquals(answered + OpenAIFormat.user(listOf(textBlock("c"))), OpenAIFormat.append(answered, listOf(textBlock("c")), "low", "low"))
        val again = OpenAIFormat.append(up, listOf(textBlock("d")), "low", "medium")
        assertEquals(listOf("message", "message", "configuration_update", "message"), again.map { it.str("type") })
        assertEquals("medium", Effort.of(again[2]))
        assertEquals(listOf("c", "d"), again[3].blocks().map { it.str("text") })
        // after tool results (a turn at its call limit): a new user message after them, the update before it
        val atLimit = answered + functionCall("fc", "call", "guide") + obj("type" to "function_call_output", "call_id" to "call", "output" to "x")
        assertEquals(listOf("function_call_output", "configuration_update", "message"), OpenAIFormat.append(atLimit, listOf(textBlock("e")), "low", "high").takeLast(3).map { it.str("type") })
        assertTrue(ModelCatalog.perMessageEffort("gpt-6.1-sol") && ModelCatalog.perMessageEffort("gpt-6-astra") && !ModelCatalog.perMessageEffort("gpt-5.6-sol"))
    }

    @Test fun `pending calls - the trailing answer's function calls, none after results`() {
        val user = OpenAIFormat.user(listOf(textBlock("a")))
        assertNull(OpenAIFormat.pending(listOf(user)))
        assertEquals(emptyList<ToolUse>(), OpenAIFormat.pending(listOf(user, reasoningItem("r"), messageItem("m", "done"))))
        val calls = OpenAIFormat.pending(listOf(user, reasoningItem("r"), functionCall("f1", "c1", "guide", obj("topic" to "t")), functionCall("f2", "c2", "reply")))!!
        assertEquals(listOf("c1", "c2"), calls.map { it.id })
        assertNull(OpenAIFormat.pending(listOf(user, functionCall("f1", "c1", "guide"), obj("type" to "function_call_output", "call_id" to "c1", "output" to "x"))))
        assertTrue(Threads.answered(listOf(user, reasoningItem("r"))) && !Threads.answered(listOf(user)))
        assertEquals("done", Threads.text(listOf(user, messageItem("m", "done"))))
    }

    // --- the tools' schemas -----------------------------------------------------------------------------------------

    @Test fun `the tools go as function tools with strict false, their schemas as they are`() {
        val functions = OpenAIProvider.functions(prefix)
        assertEquals(PhaseA.TOOLS.map { it.first }, functions.map { it.str("name") })
        val bySpec = specs.associateBy { it.name }
        for (f in functions) {
            val name = f.str("name")!!
            assertEquals(name, "function", f.str("type"))
            assertEquals(name, false, f["strict"]!!.jsonPrimitive.content.toBoolean())
            assertEquals(name, bySpec.getValue(name).inputSchema, f["parameters"])
            assertEquals(name, "object", (f["parameters"] as JsonObject).str("type"))
            assertFalse(name, f.toString().contains("eager_input_streaming"))
            // what neither mode takes (the structured-outputs guide's unsupported keywords)
            for (k in listOf("\"allOf\"", "\"not\"", "\"if\"", "\"then\"", "\"dependentRequired\"")) assertFalse("$name has $k", f["parameters"].toString().contains(k))
        }
        // strict mode would need every property required and additionalProperties false: most of them aren't
        val unfit = specs.filter { OpenAIProvider.strictFits(it.inputSchema).isNotEmpty() }.map { it.name }
        assertTrue("strict fits all: $unfit", unfit.size >= specs.size / 2)
        assertTrue(OpenAIProvider.strictFits(obj("type" to "object", "properties" to obj("a" to obj("type" to "string")), "required" to JsonArray(listOf(JsonPrimitive("a"))), "additionalProperties" to false)).isEmpty())
    }

    @Test fun `the efforts per model, as the model pages list them`() {
        assertEquals(listOf("low", "medium", "high", "xhigh", "max"), ModelCatalog.efforts("gpt-6-astra"))
        assertEquals(listOf("low", "medium", "high", "xhigh", "max"), ModelCatalog.efforts("gpt-6.1-sol"))
        assertEquals(listOf("none", "low", "medium", "high", "xhigh", "max"), ModelCatalog.efforts("gpt-6-luna"))
        assertEquals(listOf("none", "low", "medium", "high", "xhigh"), ModelCatalog.efforts("gpt-5.5"))
        assertEquals(emptyList<String>(), ModelCatalog.efforts("gpt-4.1"))
        // a level a model doesn't take: the nearest below it
        assertEquals("low", ModelCatalog.fit("none", "gpt-6.1-sol"))
        assertEquals("none", ModelCatalog.fit("none", "gpt-6-luna"))
        assertEquals("xhigh", ModelCatalog.fit("max", "gpt-5.5"))
        // the Claude models as before
        assertEquals(Effort.LEVELS, ModelCatalog.efforts("claude-haiku-5-5"))
        assertEquals("high", ModelCatalog.fit("high", "claude-sonnet-5-5"))
        assertEquals(ProviderId.OPENAI, ProviderId.of("gpt-6-luna"))
        assertEquals(ProviderId.ANTHROPIC, ProviderId.of("claude-opus-5-5"))
    }

    @Test fun `the families - each provider's own, a lane id with its provider`() {
        val catalog = ModelCatalog(listOf(ModelInfo("gpt-6-astra"), ModelInfo("gpt-6.1-sol"), ModelInfo("gpt-6-luna")))
        assertEquals(listOf("gpt-6-luna", "gpt-6.1-sol", "gpt-6-astra"), Family.of(ProviderId.OPENAI).map { catalog.own(it) })
        assertEquals("gpt-6-sol", ModelCatalog(listOf(ModelInfo("gpt-6-sol"))).own(Family.SOL))
        assertNull(ModelCatalog(listOf(ModelInfo("gpt-6-luna"))).own(Family.ASTRA))
        assertEquals("openai:gpt-6.1-sol@medium", Lanes.id("gpt-6.1-sol", "medium"))
        assertEquals("claude-sonnet-5-5@low", Lanes.id("claude-sonnet-5-5", "low"))
        assertEquals("gpt-6.1-sol" to "medium", Lanes.parse("openai:gpt-6.1-sol@medium"))
        assertEquals("claude-sonnet-5-5" to "low", Lanes.parse("claude-sonnet-5-5@low"))
        assertEquals(Family.SOL, Lanes.familyOf("gpt-6.1-sol"))
        assertEquals(30_000, Lanes.limit(Family.SOL))
        // the Claude families never fall back to OpenAI's
        assertEquals("claude-opus-5-5", ModelCatalog(listOf(ModelInfo("claude-opus-5-5"))).resolve(Family.HAIKU))
    }
}
