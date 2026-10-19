package si.lanisce.lani.tutor

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
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
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.Collections

/**
 * OpenRouter (docs/plans/05-built/providers.md, "OpenRouter") over the real clients and a fake openrouter.ai on
 * MockWebServer (port 19450, this agent's block): "Continue with OpenRouter" (PKCE, the loopback on port 0: the
 * redirect names the port it got), the exchange tried again while offline, the stream with a tool call and its
 * reasoning details then the reply, the request's shape (Claude's cache_control), usage and its cost, each error class,
 * the model list and the key's credit, the guard.
 */
class OpenRouterTest {
    private val specs = PhaseA.load()
    private val prefix = Prefix.bundled(specs, Guide(), Bundle::text)
    private val server = MockWebServer()
    private val sent = Collections.synchronizedList(mutableListOf<RecordedRequest>())
    private var answer: (RecordedRequest, Int) -> MockResponse = { _, _ -> MockResponse().setResponseCode(500) }

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                sent += request
                return answer(request, sent.count { it.method == "POST" })
            }
        }
        server.start(InetAddress.getByName("127.0.0.1"), PORT)
    }

    @After fun stop() = server.shutdown()

    private fun sse(text: String) = MockResponse().setHeader("content-type", "text/event-stream").setBody(text)
    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setHeader("content-type", "application/json").setBody(body)
    private fun body(r: RecordedRequest) = tutorJson.parseToJsonElement(r.body.readUtf8()) as JsonObject

    private val vault = KeyVault(SoftCipher(), MemoryStore())
    private fun transport() = OpenRouterTransport({ FAKE_ROUTER_KEY }, reroutedClient(server))
    private fun provider() = OpenRouterProvider(transport())

    /** The browser coming back to the loopback (a GET with [query]), as Chrome does. */
    private fun browser(port: Int, target: String, host: String = "127.0.0.1:$port"): Pair<String, String> = Socket("127.0.0.1", port).use { s ->
        s.getOutputStream().write("GET $target HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray())
        val text = s.getInputStream().readBytes().decodeToString()
        text.substringBefore("\r\n") to text.substringAfter("\r\n\r\n")
    }

    // --- the sign-in ----------------------------------------------------------------------------------------------------

    @Test fun `Continue with OpenRouter - PKCE, the loopback on port 0 named by its real port, the code for the learner's key`() = runBlocking {
        answer = { _, _ -> json(200, """{"key":"$FAKE_ROUTER_KEY","user_id":"user_2abc"}""") }
        val auth = OpenRouterAuth(vault, reroutedClient(server))
        val pending = auth.begin(0)
        val url = pending.url.toHttpUrl()
        assertEquals("https://openrouter.ai/auth", url.toString().substringBefore('?'))
        val callback = url.queryParameter("callback_url")!!.toHttpUrl()
        // the app asks for port 0: the redirect names the port the listener really got (ChatGPT's sign-in once said :0)
        assertEquals("127.0.0.1", callback.host)
        assertTrue("a real port: $callback", callback.port in 1..65535)
        assertEquals("/auth/openrouter", callback.encodedPath)
        assertEquals(pending.redirectUri, callback.toString())
        assertEquals("S256", url.queryParameter("code_challenge_method"))
        assertEquals("Lani", url.queryParameter("key_label"))
        val state = url.queryParameter("state")!!
        val challenge = url.queryParameter("code_challenge")!!
        // a stray request and another attempt's state never end it
        assertEquals("HTTP/1.1 404 Error", browser(callback.port, "/favicon.ico").first)
        assertEquals("HTTP/1.1 400 Error", browser(callback.port, "/auth/openrouter?code=x&state=not-this-one").first)
        assertEquals("HTTP/1.1 404 Error", browser(callback.port, "/auth/openrouter?code=x&state=$state", host = "evil.example").first)
        val (status, page) = browser(callback.port, "/auth/openrouter?code=code-123&state=$state")
        assertEquals("HTTP/1.1 200 OK", status)
        assertTrue("back to the app", page.contains(LoopbackPage.BACK))
        assertEquals(FAKE_ROUTER_KEY, pending.await(5_000))
        // the exchange: the code and the verifier whose S256 is the challenge
        val ex = sent.single()
        assertEquals("/api/v1/auth/keys", ex.path)
        assertEquals("openrouter.ai", ex.getHeader("x-test-host"))
        assertNull("no key goes to the exchange", ex.getHeader("Authorization"))
        val b = body(ex)
        assertEquals("code-123", b.str("code"))
        assertEquals("S256", b.str("code_challenge_method"))
        val verifier = b.str("code_verifier")!!
        assertEquals(challenge, Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())))
        // kept encrypted; the listener is gone
        assertEquals(FAKE_ROUTER_KEY, auth.key())
        assertTrue(runCatching { browser(callback.port, "/auth/openrouter?code=y&state=$state") }.isFailure)
        // sign out: forgotten here, and the learner is told to delete it on OpenRouter
        assertTrue(auth.signOut().contains("openrouter.ai/settings/keys"))
        assertNull(auth.key())
    }

    @Test fun `the exchange waits for the network - Lani in the background while the browser comes back`() = runBlocking {
        answer = { _, _ -> json(200, """{"key":"$FAKE_ROUTER_KEY","user_id":"user_2abc"}""") }
        var offline = 3
        val waits = mutableListOf<Long>()
        val flaky = Interceptor { chain ->
            if (offline > 0 && chain.request().url.encodedPath == "/api/v1/auth/keys") {
                offline--
                throw java.net.UnknownHostException("Unable to resolve host \"openrouter.ai\": No address associated with hostname")
            }
            chain.proceed(chain.request())
        }
        val auth = OpenRouterAuth(vault, OkHttpClient.Builder().addInterceptor(flaky).addInterceptor(Reroute(server)).build(), pause = { waits += it })
        val pending = auth.begin(0)
        val url = pending.url.toHttpUrl()
        browser(url.queryParameter("callback_url")!!.toHttpUrl().port, "/auth/openrouter?code=c&state=${url.queryParameter("state")}")
        assertEquals(FAKE_ROUTER_KEY, pending.await(5_000))
        assertEquals(OpenRouterAuth.OFFLINE_WAITS.take(3), waits)
    }

    @Test fun `offline for good, declined, a code OpenRouter refuses - told plainly, nothing kept`() = runBlocking {
        val down = Interceptor { chain -> throw java.net.UnknownHostException("Unable to resolve host") }
        val a = OpenRouterAuth(vault, OkHttpClient.Builder().addInterceptor(down).build(), pause = { })
        val p = a.begin(0)
        var url = p.url.toHttpUrl()
        browser(url.queryParameter("callback_url")!!.toHttpUrl().port, "/auth/openrouter?code=c&state=${url.queryParameter("state")}")
        val offline = runCatching { p.await(5_000) }.exceptionOrNull() as OpenRouterError
        assertEquals("offline", offline.code)
        assertTrue(offline.message!!.contains("in the background"))
        assertNull(a.key())
        // declined: OpenRouter sends the browser back without a code (and without the state)
        val q = a.begin(0)
        url = q.url.toHttpUrl()
        val (status, _) = browser(url.queryParameter("callback_url")!!.toHttpUrl().port, "/auth/openrouter")
        assertEquals("HTTP/1.1 200 OK", status)
        assertEquals("declined", (runCatching { q.await(5_000) }.exceptionOrNull() as OpenRouterError).code)
        // a code that expired or doesn't match the verifier: 403
        answer = { _, _ -> json(403, """{"error":{"code":403,"message":"Invalid code or code_verifier"}}""") }
        val b = OpenRouterAuth(vault, reroutedClient(server))
        val r = b.begin(0)
        url = r.url.toHttpUrl()
        browser(url.queryParameter("callback_url")!!.toHttpUrl().port, "/auth/openrouter?code=old&state=${url.queryParameter("state")}")
        assertEquals("invalid_code", (runCatching { r.await(5_000) }.exceptionOrNull() as OpenRouterError).code)
        assertNull(b.key())
    }

    // --- a turn ---------------------------------------------------------------------------------------------------------

    @Test fun `a turn over the real client - a tool call with its reasoning details, then the reply - the request's shape - usage and cost`() = runBlocking {
        answer = { _, n ->
            when (n) {
                1 -> sse(routerSse("Pogledam.", listOf(Triple("call_g1", "guide", obj("topic" to "feedback")), Triple("call_s1", "learner_state", obj())), reasoning = "Najprej vodnik.", signature = "SIG-R"))
                else -> sse(routerSse("Peč je *stove*.", reasoning = null, usage = routerUsage(prompt = 6_400, cached = 6_000, completion = 50, reasoning = 20, cost = 0.0021)))
            }
        }
        val ran = mutableListOf<ToolUse>()
        val runner = ToolRunner { u ->
            ran += u
            ToolOutcome(if (u.name == "guide") "the guide's text" else "learner: Jan", u.name == "learner_state", ToolCallRecord(u.id, u.name, u.input, "read", "ok"))
        }
        val messages = mutableListOf(ChatFormat.user(listOf(textBlock("<notebook>n</notebook>"), textBlock("<event kind=\"chat\">Kako se reče stove?</event>"))))
        val steps = mutableListOf<Step>()
        val r = TutorLoop(provider(), runner).run(prefix, "google/gemini-3.8-flash", Task.CHAT, messages, effort = "medium", onCall = { c -> c.step?.let(steps::add) })
        assertEquals(LoopEnd.Done, r.end)
        assertEquals("Peč je *stove*.", r.endText)
        assertEquals(listOf("call_g1" to "guide", "call_s1" to "learner_state"), ran.map { it.id to it.name })
        assertEquals(obj("topic" to "feedback"), ran[0].input)

        val posts = sent.filter { it.method == "POST" }
        val first = posts[0]
        assertEquals("/api/v1/chat/completions", first.path)
        assertEquals("openrouter.ai", first.getHeader("x-test-host"))
        assertEquals("Bearer $FAKE_ROUTER_KEY", first.getHeader("Authorization"))
        assertEquals("https://github.com/fentas/lani", first.getHeader("HTTP-Referer"))
        assertEquals("Lani", first.getHeader("X-OpenRouter-Title"))
        val b1 = body(first)
        assertEquals("google/gemini-3.8-flash", b1.str("model"))
        val m1 = messagesOf(b1)
        assertEquals(obj("role" to "system", "content" to prefix.system), m1[0])
        assertEquals(listOf("system", "user"), m1.map { it.role() })
        val tools = b1["tools"]!!.jsonArray.map { it.jsonObject }
        assertEquals(specs.map { it.name }, tools.map { it["function"]!!.jsonObject.str("name") })
        assertTrue(tools.all { it.str("type") == "function" && it["function"]!!.jsonObject["parameters"] is JsonObject })
        assertEquals("auto", b1.str("tool_choice"))
        assertEquals("true", b1["stream"]!!.jsonPrimitive.content)
        assertEquals("16000", b1["max_tokens"]!!.jsonPrimitive.content)
        assertEquals("medium", b1["reasoning"]!!.jsonObject.str("effort"))
        assertNull("no cache_control for Gemini (its cache is implicit)", b1["cache_control"])
        assertTrue(b1.str("session_id")!!.startsWith("lani-tutor-"))

        // the second call: the assistant's message as it came, its reasoning details whole and unchanged in order
        val m2 = messagesOf(body(posts[1]))
        assertEquals(listOf("system", "user", "assistant", "tool", "tool"), m2.map { it.role() })
        assertEquals(
            """{"role":"assistant","content":"Pogledam.","tool_calls":[{"id":"call_g1","type":"function","function":{"name":"guide","arguments":"{\"topic\":\"feedback\"}"}},""" +
                """{"id":"call_s1","type":"function","function":{"name":"learner_state","arguments":"{}"}}],""" +
                """"reasoning_details":[{"type":"reasoning.text","text":"Najprej vodnik.","format":"google-gemini-v1","index":0,"signature":"SIG-R"}]}""",
            m2[2].toString(),
        )
        assertEquals(obj("role" to "tool", "tool_call_id" to "call_g1", "content" to "the guide's text"), m2[3])
        assertEquals("a failed result says so (no error flag)", "Error: learner: Jan", m2[4].str("content"))

        // usage: the prompt counts the cached tokens; the completion its reasoning; the cost as OpenRouter reported it
        assertEquals(Usage(input = 1_000, cacheRead = 5_000, output = 120, reasoning = 40), steps[0].usage)
        assertEquals(0.0123, steps[0].cost!!, 1e-12)
        assertEquals(0.0021, steps[1].cost!!, 1e-12)
        assertEquals(Stop.TOOL_USE, steps[0].stopReason)
        assertEquals("gen-1", steps[0].id)
        assertEquals(Priced(0.0123), Prices(emptyMap()).priced("google/gemini-3.8-flash", steps[0].usage, Billing.OPENROUTER, steps[0].cost))
    }

    @Test fun `Claude through OpenRouter - the prefix's 1-hour cache_control and the moving breakpoint - its efforts`() {
        val b = provider().request(prefix, "anthropic/claude-sonnet-5.5", Task.CHAT, listOf(ChatFormat.user(listOf(textBlock("x")))), 16_000, "medium")
        val system = messagesOf(b)[0]["content"]!!.jsonArray.single().jsonObject
        assertEquals(prefix.system, system.str("text"))
        assertEquals(obj("type" to "ephemeral", "ttl" to "1h"), system["cache_control"])
        assertEquals(obj("type" to "ephemeral"), b["cache_control"])
        assertEquals("medium", b["reasoning"]!!.jsonObject.str("effort"))
        assertEquals(Effort.LEVELS, ModelCatalog.efforts("anthropic/claude-sonnet-5.5"))
        assertFalse("no per-message effort through OpenRouter", ModelCatalog.perMessageEffort("anthropic/claude-sonnet-5.5"))
        // a model that takes no effort gets none
        RoutedModels.learn(OpenRouterTransport.parseModels(ROUTER_MODELS)!!)
        val gemma = provider().request(prefix, OpenRouterModels.GEMMA, Task.CHAT, emptyList(), 16_000, "medium")
        assertNull(gemma["reasoning"])
        assertEquals(emptyList<String>(), ModelCatalog.efforts(OpenRouterModels.GEMMA))
        // a photo as an image_url with its data URL
        val u = ChatFormat.user(listOf(obj("type" to "image", "source" to obj("type" to "base64", "media_type" to "image/jpeg", "data" to "AAAA"))))
        assertEquals(obj("type" to "image_url", "image_url" to obj("url" to "data:image/jpeg;base64,AAAA")), u.blocks().single())
        assertEquals(1, ShadowImages.count(listOf(u)))
    }

    @Test fun `a stream - keep-alives skipped, a refusal, a cut-off, an error chunk, the end before finish_reason`() = runBlocking {
        fun stream(text: String): Step = runBlocking {
            answer = { _, _ -> sse(text) }
            transport().stream(provider().request(prefix, "google/gemini-3.8-flash", Task.CHAT, listOf(ChatFormat.user(listOf(textBlock("x")))), 16_000, "low"))
        }
        assertEquals(Stop.MAX_TOKENS, stream(routerSse("Ker je peč", reasoning = null, finish = "length")).stopReason)
        val filtered = stream(routerSse("", reasoning = null, finish = "content_filter"))
        assertEquals(Stop.REFUSAL, filtered.stopReason)
        assertEquals("content_filter", filtered.stopDetails!!.str("category"))
        val err = runCatching {
            stream(""": OPENROUTER PROCESSING

data: {"id":"cmpl-abc123","object":"chat.completion.chunk","created":1234567890,"model":"openai/gpt-6-luna","provider":"openai","error":{"code":"server_error","message":"Provider disconnected unexpectedly"},"choices":[{"index":0,"delta":{"content":""},"finish_reason":"error"}]}

""")
        }.exceptionOrNull()
        assertTrue("$err", err is ApiError.Overloaded && err.retryable)
        val credits = runCatching { stream("data: {\"error\":{\"code\":402,\"message\":\"Insufficient credits\",\"metadata\":{\"limit_source\":\"openrouter_credits\"}},\"choices\":[]}\n\n") }.exceptionOrNull()
        assertTrue("$credits", credits is ApiError.LowCredit)
        val early = runCatching { stream("data: " + obj("choices" to JsonArray(listOf(obj("index" to 0, "delta" to obj("content" to "Ker"))))) + "\n\n") }.exceptionOrNull()
        assertTrue("$early", early is ApiError.Network)
    }

    // --- errors, the list, the key, the guard -----------------------------------------------------------------------

    private fun errorOf(code: Int, body: String, retryAfter: String? = null): ApiError = runBlocking {
        answer = { _, _ -> json(code, body).also { r -> retryAfter?.let { r.setHeader("retry-after", it) } } }
        runCatching { transport().stream(provider().request(prefix, "google/gemini-3.8-flash", Task.CHAT, listOf(ChatFormat.user(listOf(textBlock("x")))), 16_000, "low")) }.exceptionOrNull() as ApiError
    }

    private fun err(code: Int, message: String, metadata: JsonObject? = null) = obj("error" to obj("code" to code, "message" to message, "metadata" to metadata)).toString()

    @Test fun `errors per class - the key, the credits and the key's limit, moderation, the rate limit, overload`() {
        assertTrue(errorOf(401, err(401, "No auth credentials found")) is ApiError.Auth)
        val credits = errorOf(402, err(402, "Insufficient credits", obj("limit_source" to "openrouter_credits")))
        assertTrue(credits is ApiError.LowCredit && !credits.limit)
        val keyLimit = errorOf(402, err(402, "Key limit exceeded", obj("limit_source" to "openrouter_key_limit")))
        assertTrue(keyLimit is ApiError.LowCredit && keyLimit.limit)
        val inFlight = errorOf(402, err(402, "In-flight budget", obj("limit_source" to "openrouter_in_flight_budget")), retryAfter = "2")
        assertEquals(2_000L, (inFlight as ApiError.RateLimited).retryAfterMs)
        val budget = errorOf(403, err(403, "Budget limit exceeded (weekly limit)… \$52.00 of \$50.00 used", obj("limit_source" to "openrouter_key_limit")))
        assertTrue(budget is ApiError.LowCredit && budget.limit)
        val flagged = errorOf(403, err(403, "Input flagged", obj("reasons" to JsonArray(listOf(obj("x" to "y"))), "flagged_input" to "…")))
        assertTrue("not retried: the input was refused", flagged is ApiError.BadRequest && !flagged.retryable)
        assertTrue(errorOf(403, err(403, "Insufficient permissions")) is ApiError.Auth)
        val rate = errorOf(429, err(429, "Rate limit exceeded"), retryAfter = "5")
        assertEquals(5_000L, (rate as ApiError.RateLimited).retryAfterMs)
        assertTrue(errorOf(502, err(502, "Model down")) is ApiError.Overloaded)
        assertTrue(errorOf(503, err(503, "No available model provider")) is ApiError.Overloaded)
        assertTrue(errorOf(408, err(408, "Timeout")) is ApiError.Overloaded)
        assertTrue(errorOf(400, err(400, "Bad request")) is ApiError.BadRequest)
    }

    @Test fun `the model list (no key sent), tool-taking models with their efforts and prices - the key's credit`() = runBlocking {
        answer = { r, _ -> if (r.path!!.startsWith("/api/v1/key")) json(200, ROUTER_KEY) else json(200, ROUTER_MODELS) }
        val models = transport().models()
        val get = sent.last()
        assertEquals("/api/v1/models?supported_parameters=tools", get.path)
        assertNull("the list is public: no key", get.getHeader("Authorization"))
        assertEquals(listOf("google/gemini-4-argon", "google/gemini-3.8-flash", "openai/gpt-6-luna", "anthropic/claude-sonnet-5.5", "google/gemma-4-31b-it"), models.map { it.id })
        val argon = models[0]
        assertEquals(listOf("high", "medium", "low"), argon.efforts)
        assertEquals("medium", argon.defaultEffort)
        assertEquals(2.0, argon.inputPrice!!, 1e-9)
        assertEquals(10.0, argon.outputPrice!!, 1e-9)
        assertEquals(true, argon.images)
        assertEquals(1_048_576L, argon.maxInputTokens)
        assertEquals("not said: the vendor's own levels", null, models[3].efforts)
        assertEquals(emptyList<String>(), models[4].efforts)
        RoutedModels.learn(models)
        assertEquals(listOf("low", "medium", "high"), ModelCatalog.efforts("google/gemini-4-argon"))
        assertEquals(listOf("none", "low", "medium", "high", "xhigh", "max"), ModelCatalog.efforts("openai/gpt-6-luna"))
        assertEquals(ProviderId.OPENROUTER, ProviderId.of("google/gemini-4-argon"))
        val key = transport().key()
        assertEquals("Bearer $FAKE_ROUTER_KEY", sent.last().getHeader("Authorization"))
        assertEquals(OpenRouterKey("Lani", 20.0, 17.5, 2.5, 2.5, false), key)
    }

    @Test fun `the key goes to openrouter ai only, and never into a log or the export`() {
        assertTrue(OpenRouterGuard.mayCarryKey("https://openrouter.ai/api/v1/chat/completions".toHttpUrl()))
        assertFalse(OpenRouterGuard.mayCarryKey("http://openrouter.ai/api/v1/chat/completions".toHttpUrl()))
        assertFalse(OpenRouterGuard.mayCarryKey("https://openrouter.ai.evil.example/api".toHttpUrl()))
        assertFalse(OpenRouterGuard.mayCarryKey("https://api.openai.com/v1".toHttpUrl()))
        assertFalse(Redact.of("the key $FAKE_ROUTER_KEY in a line").contains(FAKE_ROUTER_KEY))
        assertFalse(Redact.all("Authorization: Bearer $FAKE_ROUTER_KEY").contains(FAKE_ROUTER_KEY))
    }

    private companion object {
        const val PORT = 19450
    }
}
