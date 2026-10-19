package si.lanisce.lani.tutor.live

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.tutor.Answering
import si.lanisce.lani.tutor.obj
import java.net.InetAddress

/**
 * The reports' GitHub discussions (plan 5, "Reports: GitHub discussions") against a fake GraphQL API: looked up first
 * and upvoted when there, created in its category when not; the body's parts and nothing personal; the errors; the
 * token only to the API's host.
 */
class DiscussionsTest {
    private val api = FakeGraphQL()
    private val base = "http://127.0.0.1:$PORT"
    private var now = 1_000_000L

    @Before fun start() = api.server.start(InetAddress.getByName("127.0.0.1"), PORT)

    @After fun stop() = api.server.shutdown()

    private fun discussions(token: String? = TOKEN, http: OkHttpClient = OkHttpClient(), at: String = base) =
        Discussions({ token }, api = at, http = http, clock = { now })

    private fun fix(title: String = "Fix: pack praznik-trgatev, word škarje") = ReportDraft(
        kind = "fix",
        title = title,
        whatIsWrong = "\"škarje\" is glossed as knife; it means scissors.",
        suggestedFix = "scissors",
        item = buildJsonObject { put("kind", "pack"); put("pack_id", "praznik-trgatev"); put("word_id", "skarje") },
        nativeCheck = true,
    )

    private suspend fun <T> error(kind: DiscussionsError.Kind, block: suspend () -> T): DiscussionsError {
        try {
            block()
        } catch (e: DiscussionsError) {
            assertEquals(e.message, kind, e.kind)
            assertFalse("the token stays out of the message", e.message!!.contains(TOKEN))
            return e
        }
        fail("no $kind")
        throw AssertionError()
    }

    @Test fun `a report that is there is found and upvoted`() = runBlocking {
        api.add("Fix: pack praznik-trgatev, word škarje (the plural)", upvotes = 9) // contains the title: not it
        val there = api.add("Fix: pack praznik-trgatev, word škarje", upvotes = 3)
        val d = discussions().find("  fix: pack  praznik-trgatev,\tword ŠKARJE ")
        assertNotNull(d)
        assertEquals(there.id, d!!.id)
        assertEquals(3, d.upvotes)
        assertFalse(d.upvoted)
        assertEquals("Content fixes", d.category)
        assertEquals(4, discussions().upvote(d))
        assertEquals(listOf("FindDiscussion", "Upvote"), api.calls.map { it.op })
        val search = api.calls.first()
        assertEquals("repo:fentas/lani in:title \"fix: pack praznik-trgatev, word ŠKARJE\"", search.variables.s("q"))
        assertFalse("the title is a variable, not part of the query", search.query.contains("praznik", ignoreCase = true))
        assertEquals(there.id, api.calls[1].variables.s("id"))
        assertTrue(api.calls.all { it.auth == "Bearer $TOKEN" })
        assertTrue(api.calls.all { it.path == "/graphql" && it.method == "POST" })
    }

    @Test fun `one the learner upvoted already isn't upvoted again`() = runBlocking {
        api.add("Fix: pack praznik-trgatev, word škarje", upvotes = 5, upvoted = true)
        val d = discussions().find("Fix: pack praznik-trgatev, word škarje")!!
        assertTrue(d.upvoted)
        assertEquals(5, discussions().upvote(d))
        assertEquals(listOf("FindDiscussion"), api.calls.map { it.op })
    }

    @Test fun `none found - a fix goes to Content fixes, a wish to Ideas`() = runBlocking {
        api.add("Fix: pack praznik-trgatev, word škarje (the plural)")
        val ds = discussions()
        assertNull(ds.find("Fix: pack praznik-trgatev, word škarje"))
        val created = ds.create(fix())
        assertEquals("Fix: pack praznik-trgatev, word škarje", created.title)
        assertEquals("Content fixes", created.category)
        assertEquals("https://github.com/fentas/lani/discussions/${created.number}", created.url)
        assertEquals(0, created.upvotes)
        val post = api.calls.last { it.op == "CreateDiscussion" }
        assertEquals("R_lani", post.variables.s("repositoryId"))
        assertEquals("DIC_fixes", post.variables.s("categoryId"))

        val wish = ds.create(ReportDraft("wish", "Idea: a dark mode for the village", "The village is bright at night.", null, null, false))
        assertEquals("Ideas", wish.category)
        assertEquals("DIC_ideas", api.calls.last().variables.s("categoryId"))
        val repo = api.calls.first { it.op == "ReportRepository" }
        assertEquals("fentas", repo.variables.s("owner"))
        assertEquals("lani", repo.variables.s("name"))
    }

    @Test fun `the body - the item, what is wrong, the fix, the native check and nothing else`() = runBlocking {
        val body = Discussions.body(fix())
        assertEquals(
            "### Item\n\n`{\"kind\":\"pack\",\"pack_id\":\"praznik-trgatev\",\"word_id\":\"skarje\"}`\n\n" +
                "### What is wrong\n\n\"škarje\" is glossed as knife; it means scissors.\n\n" +
                "### Suggested fix\n\nscissors\n\n" +
                "native check: a native speaker should check this Slovene (machine-written)\n\n" +
                "Reported from the Lani app's tutor.",
            body,
        )
        discussions().create(fix())
        val post = api.calls.last()
        // only the four inputs: no name, no chat, no learner
        assertEquals(setOf("repositoryId", "categoryId", "title", "body"), post.variables.keys)
        assertEquals(body, post.variables.s("body"))

        // a wish without an item, a fix or a native check: only what is wished for and the last line
        assertEquals(
            "### What is wished for\n\nA dark mode.\n\nReported from the Lani app's tutor.",
            Discussions.body(ReportDraft("wish", "Idea: dark mode", " A dark mode. ", "  ", null, false)),
        )
        // each part cut at 2,000 characters; an item with backticks keeps its code span
        val long = Discussions.body(ReportDraft("fix", "Fix: x", "a".repeat(5_000), "b".repeat(3_000), obj("note" to "a `tick`"), false))
        val parts = long.split("\n\n")
        assertEquals("``{\"note\":\"a `tick`\"}``", parts[1])
        assertEquals(2_000, parts[3].length)
        assertTrue(parts[3].endsWith("…"))
        assertEquals(2_000, parts[5].length)
    }

    @Test fun `a title with quotes and Slovene letters round-trips`() = runBlocking {
        val title = "Fix: pack zimnica, word \"čaj\" → \"šipek\", žlica"
        val ds = discussions()
        assertNull(ds.find(title))
        assertEquals("repo:fentas/lani in:title \"Fix: pack zimnica, word čaj → šipek, žlica\"", api.calls.last().variables.s("q"))
        val created = ds.create(fix(title))
        assertEquals(title, created.title)
        assertEquals(title, api.calls.last().variables.s("title"))
        val again = ds.find(title)
        assertEquals(created.id, again?.id)
        assertEquals(title, again?.title)
    }

    @Test fun `no sign-in - nothing is asked`() = runBlocking {
        error(DiscussionsError.Kind.NO_TOKEN) { discussions(token = null).find("Fix: x") }
        error(DiscussionsError.Kind.NO_TOKEN) { discussions(token = " ").create(fix()) }
        assertEquals(0, api.server.requestCount)
    }

    @Test fun `a refused sign-in - 401`() = runBlocking {
        api.status = 401
        error(DiscussionsError.Kind.AUTH) { discussions().find("Fix: x") }
        Unit
    }

    @Test fun `a sign-in that may not post - FORBIDDEN, a missing scope, a plain 403`() = runBlocking {
        api.add("Fix: pack praznik-trgatev, word škarje")
        api.forbidden = "FORBIDDEN" to "Resource not accessible by personal access token"
        val e = error(DiscussionsError.Kind.FORBIDDEN) { discussions().create(fix("Fix: new")) }
        assertTrue(e.message, e.message!!.contains("may not post to fentas/lani"))
        api.forbidden = "INSUFFICIENT_SCOPES" to "Your token has not been granted the required scopes to execute this query."
        error(DiscussionsError.Kind.FORBIDDEN) { discussions().create(fix("Fix: new")) }
        api.forbidden = null
        api.status = 403
        error(DiscussionsError.Kind.FORBIDDEN) { discussions().find("Fix: x") }
        Unit
    }

    @Test fun `a used-up rate limit waits without asking GitHub`() = runBlocking {
        val ds = discussions()
        api.status = 403
        api.limitHeaders = mapOf("X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to "2000")
        val e = error(DiscussionsError.Kind.RATE_LIMITED) { ds.find("Fix: x") }
        assertEquals(2_000_000L, e.until)
        val asked = api.server.requestCount
        api.status = 200
        api.limitHeaders = emptyMap()
        now = 1_500_000L
        error(DiscussionsError.Kind.RATE_LIMITED) { ds.find("Fix: x") }
        assertEquals("not asked while it lasts", asked, api.server.requestCount)
        now = 2_000_001L
        assertNull(ds.find("Fix: x"))
        assertEquals(asked + 1, api.server.requestCount)

        // a secondary limit: 429 with Retry-After
        api.status = 429
        api.limitHeaders = mapOf("Retry-After" to "30")
        assertEquals(now + 30_000, error(DiscussionsError.Kind.RATE_LIMITED) { ds.find("Fix: y") }.until)
        // GraphQL's own: 200 with a RATE_LIMITED error, until the reset it reports
        val other = discussions()
        api.status = 200
        api.limitHeaders = mapOf("X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to "3000")
        api.rateLimitedError = true
        assertEquals(3_000_000L, error(DiscussionsError.Kind.RATE_LIMITED) { other.find("Fix: y") }.until)
    }

    @Test fun `no category - nothing is posted`() = runBlocking {
        api.categories.removeAll { it.second == "Content fixes" }
        val ds = discussions()
        val e = error(DiscussionsError.Kind.NO_CATEGORY) { ds.create(fix()) }
        assertTrue(e.message!!.contains("Content fixes"))
        assertTrue(api.calls.none { it.op == "CreateDiscussion" })
        // a wish still has its category
        assertEquals("Ideas", ds.create(ReportDraft("wish", "Idea: x", "y", null, null, false)).category)
        // set up meanwhile, under another name: found by its slug, the repository asked again
        api.categories += Triple("DIC_fix2", "🛠 Fixes", "content-fixes")
        assertEquals("🛠 Fixes", ds.create(fix()).category)
        assertEquals("DIC_fix2", api.calls.last().variables.s("categoryId"))
        assertEquals(2, api.calls.count { it.op == "ReportRepository" })
    }

    @Test fun `the repository is asked once per instance`() = runBlocking {
        val ds = discussions()
        ds.create(fix("Fix: one"))
        ds.create(fix("Fix: two"))
        ds.create(ReportDraft("wish", "Idea: three", "y", null, null, false))
        assertEquals(listOf("ReportRepository", "CreateDiscussion", "CreateDiscussion", "CreateDiscussion"), api.calls.map { it.op })
        // another instance asks again
        discussions().create(fix("Fix: four"))
        assertEquals(2, api.calls.count { it.op == "ReportRepository" })
    }

    @Test fun `the token goes only to the API's host`() = runBlocking {
        // GitHub over https, or the fake that is the API; never plain http to GitHub, never another host
        assertTrue(Discussions.mayCarryToken("https://api.github.com/graphql".toHttpUrl(), Discussions.API))
        assertFalse(Discussions.mayCarryToken("http://api.github.com/graphql".toHttpUrl(), "http://api.github.com"))
        assertFalse(Discussions.mayCarryToken("https://evil.example/graphql".toHttpUrl(), Discussions.API))
        assertTrue(Discussions.mayCarryToken("$base/graphql".toHttpUrl(), base))
        assertFalse(Discussions.mayCarryToken("http://127.0.0.1:$OTHER/graphql".toHttpUrl(), base))
        assertFalse(Discussions.mayCarryToken("http://localhost:$PORT/graphql".toHttpUrl(), base))

        // a redirect elsewhere is not followed: the other host never sees a request, let alone the token
        val other = MockWebServer().apply { start(InetAddress.getByName("127.0.0.1"), OTHER) }
        try {
            api.redirect = "http://127.0.0.1:$OTHER/graphql"
            error(DiscussionsError.Kind.BAD_ANSWER) { discussions().find("Fix: x") }
            assertEquals(1, api.server.requestCount)
            assertEquals(0, other.requestCount)
        } finally {
            other.shutdown()
        }

        // the real API's address: the token goes there (an interceptor answers: nothing leaves the machine)
        val answering = Answering { Triple(200, """{"data":{"search":{"nodes":[]}}}""", "application/json") }
        assertNull(discussions(http = OkHttpClient.Builder().addInterceptor(answering).build(), at = Discussions.API).find("Fix: x"))
        assertEquals("https://api.github.com/graphql", answering.requests.single().url.toString())
        assertEquals("Bearer $TOKEN", answering.requests.single().header("Authorization"))
        // plain http to GitHub: refused before any request
        try {
            discussions(http = OkHttpClient.Builder().addInterceptor(answering).build(), at = "http://api.github.com").find("Fix: x")
            fail("plain http went out")
        } catch (e: IllegalStateException) {
            assertFalse(e.message!!.contains(TOKEN))
        }
        assertEquals(1, answering.requests.size)
    }

    @Test fun `no answer is NETWORK, a garbled one BAD_ANSWER`() = runBlocking {
        error(DiscussionsError.Kind.NETWORK) { discussions(at = "http://127.0.0.1:$CLOSED").find("Fix: x") }
        api.raw = "<html>unicorn</html>"
        error(DiscussionsError.Kind.BAD_ANSWER) { discussions().find("Fix: x") }
        api.raw = """{"data":{"repository":null},"errors":[{"type":"NOT_FOUND","message":"Could not resolve to a Repository with the name 'fentas/lani'."}]}"""
        val e = error(DiscussionsError.Kind.BAD_ANSWER) { discussions().create(fix()) }
        assertTrue(e.message!!.contains("Could not resolve"))
    }

    @Test fun `a report's kind is fix or wish`() = runBlocking {
        try {
            discussions().create(fix().copy(kind = "bug"))
            fail("posted a bug")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(0, api.server.requestCount)
    }

    private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull

    companion object {
        const val PORT = 19620
        const val OTHER = 19621
        const val CLOSED = 19625
        const val TOKEN = "gho_DISCUSSIONSTESTTOKEN0123456789"
    }
}

/**
 * GitHub's GraphQL API as the reports use it: the search (by the phrase after `in:title`), addUpvote, the repository's
 * id and discussion categories, createDiscussion. Each call is kept by its operation name and variables.
 */
class FakeGraphQL : Dispatcher() {
    val server = MockWebServer().also { it.dispatcher = this }

    class Call(val op: String, val query: String, val variables: JsonObject, val auth: String?, val path: String?, val method: String?)
    class Item(val id: String, val number: Int, val title: String, var upvotes: Int, var upvoted: Boolean, val category: String, var closed: Boolean = false)

    val calls = java.util.Collections.synchronizedList(mutableListOf<Call>())
    val discussions = mutableListOf<Item>()
    /** The repository's discussion categories: id, name, slug. */
    val categories = mutableListOf(Triple("DIC_fixes", "Content fixes", "content-fixes"), Triple("DIC_ideas", "Ideas", "ideas"), Triple("DIC_qa", "Q&A", "q-a"))

    var status = 200
    var raw: String? = null
    var redirect: String? = null
    var limitHeaders: Map<String, String> = emptyMap()
    /** A GraphQL error (type to message) on createDiscussion and addUpvote. */
    var forbidden: Pair<String, String>? = null
    var rateLimitedError = false

    fun add(title: String, upvotes: Int = 0, upvoted: Boolean = false, category: String = "Content fixes"): Item =
        Item("D_${discussions.size + 1}", discussions.size + 1, title, upvotes, upvoted, category).also { discussions += it }

    private fun node(d: Item) = buildJsonObject {
        put("id", d.id); put("number", d.number); put("title", d.title); put("url", "https://github.com/fentas/lani/discussions/${d.number}")
        put("upvoteCount", d.upvotes); put("viewerHasUpvoted", d.upvoted); put("closed", d.closed)
        put("category", buildJsonObject { put("name", d.category) })
    }

    private fun answer(code: Int, body: String) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json")
        .apply { limitHeaders.forEach { (k, v) -> setHeader(k, v) } }
        .setBody(body)

    private fun data(d: JsonObject) = answer(200, buildJsonObject { put("data", d) }.toString())

    private fun error(type: String, message: String) =
        answer(200, buildJsonObject { put("data", kotlinx.serialization.json.JsonNull); put("errors", buildJsonArray { add(buildJsonObject { put("type", type); put("message", message) }) }) }.toString())

    override fun dispatch(request: RecordedRequest): MockResponse {
        val o = kotlinx.serialization.json.Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        val op = o["operationName"]?.jsonPrimitive?.contentOrNull ?: "?"
        val vars = o["variables"] as? JsonObject ?: JsonObject(emptyMap())
        calls += Call(op, o["query"]!!.jsonPrimitive.content, vars, request.getHeader("Authorization"), request.path, request.method)
        redirect?.let { return MockResponse().setResponseCode(307).setHeader("Location", it) }
        raw?.let { return answer(200, it) }
        if (status != 200) return answer(status, """{"message":"HTTP $status"}""")
        if (rateLimitedError) return error("RATE_LIMITED", "API rate limit exceeded for user ID 1.")
        fun v(k: String) = vars[k]?.jsonPrimitive?.content
        return when (op) {
            "FindDiscussion" -> {
                val q = v("q")!!
                check(q.startsWith("repo:fentas/lani in:title \"") && q.endsWith("\"")) { q }
                val phrase = q.removePrefix("repo:fentas/lani in:title \"").removeSuffix("\"").lowercase()
                data(buildJsonObject {
                    put("search", buildJsonObject {
                        put("nodes", JsonArray(discussions.filter { it.title.replace("\"", "").lowercase().contains(phrase) }.map(::node) + JsonObject(emptyMap())))
                    })
                })
            }
            "Upvote" -> {
                forbidden?.let { (t, m) -> return error(t, m) }
                val d = discussions.first { it.id == v("id") }
                if (!d.upvoted) { d.upvotes++; d.upvoted = true }
                data(buildJsonObject { put("addUpvote", buildJsonObject { put("subject", buildJsonObject { put("upvoteCount", d.upvotes); put("viewerHasUpvoted", true) }) }) })
            }
            "ReportRepository" -> {
                check(v("owner") == "fentas" && v("name") == "lani")
                data(buildJsonObject {
                    put("repository", buildJsonObject {
                        put("id", "R_lani")
                        put("discussionCategories", buildJsonObject {
                            put("nodes", JsonArray(categories.map { (id, name, slug) -> buildJsonObject { put("id", id); put("name", name); put("slug", slug) } }))
                        })
                    })
                })
            }
            "CreateDiscussion" -> {
                forbidden?.let { (t, m) -> return error(t, m) }
                check(v("repositoryId") == "R_lani")
                val category = categories.first { it.first == v("categoryId") }.second
                val d = add(v("title")!!, category = category)
                data(buildJsonObject { put("createDiscussion", buildJsonObject { put("discussion", node(d)) }) })
            }
            else -> answer(200, """{"errors":[{"message":"unknown operation $op"}]}""")
        }
    }
}
