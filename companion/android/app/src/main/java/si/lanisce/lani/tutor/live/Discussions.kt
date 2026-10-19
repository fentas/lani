package si.lanisce.lani.tutor.live

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import si.lanisce.lani.tutor.str
import si.lanisce.lani.tutor.tutorJson
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Why a report didn't reach Lani's discussions. [kind] says what the app does about it: [Kind.NO_TOKEN] and [Kind.AUTH]
 * ask for the GitHub sign-in (again), [Kind.FORBIDDEN] says the sign-in may not post, [Kind.NO_CATEGORY] that the
 * repository's discussions aren't set up, [Kind.RATE_LIMITED] waits until [until] (milliseconds since the epoch), and
 * [Kind.NETWORK] and [Kind.BAD_ANSWER] try again later. The message never holds the token.
 */
class DiscussionsError(val kind: Kind, message: String, val until: Long? = null) : IOException(message) {
    enum class Kind { NO_TOKEN, AUTH, FORBIDDEN, NO_CATEGORY, RATE_LIMITED, NETWORK, BAD_ANSWER }
}

/** A discussion of Lani's repository, as the search and the mutations return it. */
data class Discussion(
    val id: String,
    val number: Int,
    val title: String,
    val url: String,
    val upvotes: Int,
    val upvoted: Boolean,
    val closed: Boolean,
    val category: String?,
)

/**
 * A report the tutor wants to post (the `report_problem` tool's input): [kind] "fix" (a wrong curated item) or "wish"
 * (an idea for the app); [title] names the item ("Fix: pack praznik-trgatev, word škarje"); [item] the item's source as
 * the app sent it (about_exercise.source); [nativeCheck]: Slovene a machine wrote, for a native speaker to check.
 * Nothing personal: the repository is public.
 */
data class ReportDraft(
    val kind: String,
    val title: String,
    val whatIsWrong: String,
    val suggestedFix: String?,
    val item: JsonObject?,
    val nativeCheck: Boolean,
)

/**
 * GitHub Discussions for the tutor's reports (docs/plans/05-in-app-tutor.md, "Reports: GitHub discussions"): [find]
 * looks the report up first, [upvote] votes for one that is there, [create] posts a new one in its category ("Content
 * fixes" for a fix, "Ideas" for a wish). GitHub's GraphQL API at POST {api}/graphql, as the learner: [token] is the
 * GitHub sign-in that git sync keeps. The app asks the learner before it calls [upvote] or [create].
 *
 * - **The token** goes only to [api]'s host ([mayCarryToken], as [si.lanisce.lani.tutor.KeyGuard] does for the key),
 *   never along a redirect (the client follows none), and never into an exception's message.
 * - **Variables, not string-built queries:** the title, the body and the search string go in `variables`; the search
 *   string is the title without its `"`, so a quote can't end the phrase.
 * - **Rate limits:** as [si.lanisce.lani.sync.GitHub] does, an answer's X-RateLimit-Remaining and -Reset are kept; once
 *   the limit is used up (or a 403/429 says so, or a Retry-After), every call throws [DiscussionsError.Kind.RATE_LIMITED]
 *   until then without asking GitHub.
 */
class Discussions(
    private val token: suspend () -> String?,
    private val api: String = API,
    private val repo: String = REPO,
    http: OkHttpClient = DEFAULT_CLIENT,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The client as given, but following no redirect: a redirect would carry the Authorization header elsewhere. */
    private val http: OkHttpClient = http.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val owner: String
    private val name: String

    init {
        val parts = repo.split('/')
        require(parts.size == 2 && parts.all { it.isNotBlank() }) { "repo is owner/name: $repo" }
        owner = parts[0]
        name = parts[1]
    }

    private class Category(val id: String, val name: String, val slug: String?)
    private class Repository(val id: String, val categories: List<Category>)

    /** The repository's id and categories, asked once per instance (again when a report's category isn't among them). */
    @Volatile private var repository: Repository? = null
    @Volatile private var blockedUntil: Long? = null
    @Volatile private var remaining: Int? = null
    @Volatile private var resetAt: Long? = null

    /**
     * The discussion titled [title] (trimmed, case and runs of whitespace ignored), an open one before a closed one; null
     * when there is none. The search is GitHub's (`repo:<repo> in:title "<title>"`, the first 10); the title must then
     * match exactly, so a longer title that contains it is not it.
     */
    suspend fun find(title: String): Discussion? {
        val want = normal(title)
        require(want.isNotEmpty()) { "a report needs a title" }
        val phrase = title.replace("\"", "").trim().replace(WHITESPACE, " ")
        val data = graphql("FindDiscussion", FIND, buildJsonObject { put("q", "repo:$repo in:title \"$phrase\"") })
        val nodes = (data.obj("search")?.get("nodes") as? JsonArray ?: bad("FindDiscussion", "no search nodes"))
            .mapNotNull { it as? JsonObject }
            .filter { it.str("id") != null } // a node that isn't a Discussion comes back empty
            .map { discussionOf(it, "FindDiscussion") }
        val same = nodes.filter { normal(it.title) == want }
        return same.firstOrNull { !it.closed } ?: same.firstOrNull()
    }

    /** Upvotes [d] as the learner: its count afterwards. One the learner upvoted already isn't asked again: its count. */
    suspend fun upvote(d: Discussion): Int {
        if (d.upvoted) return d.upvotes
        val data = graphql("Upvote", UPVOTE, buildJsonObject { put("id", d.id) })
        val subject = data.obj("addUpvote")?.obj("subject") ?: bad("Upvote", "no subject")
        return subject.int("upvoteCount") ?: bad("Upvote", "no upvoteCount")
    }

    /**
     * A new discussion of [draft] in its category: "Content fixes" for a fix, "Ideas" for a wish (by name, ignoring case,
     * or by the slug content-fixes / ideas), its body [body]. [DiscussionsError.Kind.NO_CATEGORY] when the repository
     * has no such category (or no discussions at all).
     */
    suspend fun create(draft: ReportDraft): Discussion {
        val (categoryName, slug) = CATEGORIES[draft.kind] ?: throw IllegalArgumentException("a report's kind is fix or wish: ${draft.kind}")
        val title = draft.title.trim().replace(WHITESPACE, " ")
        require(title.isNotEmpty()) { "a report needs a title" }
        fun Repository.category() = categories.firstOrNull { it.name.equals(categoryName, ignoreCase = true) || it.slug.equals(slug, ignoreCase = true) }
        val cached = repository
        var r = cached ?: repositoryInfo().also { repository = it }
        var category = r.category()
        if (category == null && cached != null) {
            // not in what was asked before: set up meanwhile? asked again, once
            r = repositoryInfo().also { repository = it }
            category = r.category()
        }
        if (category == null) throw DiscussionsError(DiscussionsError.Kind.NO_CATEGORY, "$repo has no discussion category \"$categoryName\"")
        val data = graphql("CreateDiscussion", CREATE, buildJsonObject {
            put("repositoryId", r.id)
            put("categoryId", category.id)
            put("title", title)
            put("body", body(draft))
        })
        val d = data.obj("createDiscussion")?.obj("discussion") ?: bad("CreateDiscussion", "no discussion")
        return discussionOf(d, "CreateDiscussion")
    }

    private suspend fun repositoryInfo(): Repository {
        val data = graphql("ReportRepository", REPOSITORY, buildJsonObject { put("owner", owner); put("name", name) })
        val r = data.obj("repository") ?: bad("ReportRepository", "no repository $repo")
        val id = r.str("id") ?: bad("ReportRepository", "no repository id")
        val categories = (r.obj("discussionCategories")?.get("nodes") as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            Category(o.str("id") ?: return@mapNotNull null, o.str("name") ?: return@mapNotNull null, o.str("slug"))
        }
        return Repository(id, categories)
    }

    // --- the plumbing ----------------------------------------------------------------------------------

    private class Answer(val code: Int, val body: String)

    /** POSTs [query] named [op] with [variables]; the answer's `data`, or a [DiscussionsError] by the answer. */
    private suspend fun graphql(op: String, query: String, variables: JsonObject): JsonObject {
        blockedUntil?.let { if (clock() < it) throw limited(it) }
        val t = token()?.takeIf { it.isNotBlank() }
            ?: throw DiscussionsError(DiscussionsError.Kind.NO_TOKEN, "no GitHub sign-in: connect GitHub to send reports to $repo")
        val url = (api.trimEnd('/') + "/graphql").toHttpUrlOrNull() ?: throw IllegalStateException("bad GitHub API URL")
        check(mayCarryToken(url, api)) { "the GitHub token goes only to GitHub's API" }
        val sent = buildJsonObject {
            put("query", query)
            put("operationName", op)
            put("variables", variables)
        }
        val req = Request.Builder().url(url)
            .header("Authorization", "Bearer $t")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .post(sent.toString().toRequestBody(JSON))
            .build()
        val a = execute(req, t) { r -> answer(r) }
        when {
            a.code == 401 -> throw DiscussionsError(DiscussionsError.Kind.AUTH, "GitHub refused the sign-in (401): sign in again")
            a.code == 403 -> throw DiscussionsError(DiscussionsError.Kind.FORBIDDEN, "the GitHub sign-in may not post to $repo (403${message(a)?.let { ": ${scrub(it, t)}" } ?: ""})")
            a.code !in 200..299 -> bad(op, "HTTP ${a.code}${message(a)?.let { " ${scrub(it, t)}" } ?: ""}", t)
        }
        val o = runCatching { tutorJson.parseToJsonElement(a.body) as JsonObject }.getOrNull() ?: bad(op, "not JSON", t)
        val errors = (o["errors"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        if (errors.isNotEmpty()) {
            val type = errors.mapNotNull { it.str("type") }
            val said = errors.mapNotNull { it.str("message") }.joinToString("; ").take(300).let { scrub(it, t) }
            when {
                "RATE_LIMITED" in type -> throw block(resetAt ?: (clock() + MINUTE))
                type.any { it == "FORBIDDEN" || it == "INSUFFICIENT_SCOPES" } || FORBIDDEN_SAID.any { said.contains(it, ignoreCase = true) } ->
                    throw DiscussionsError(DiscussionsError.Kind.FORBIDDEN, "the GitHub sign-in may not post to $repo: $said")
                else -> bad(op, said.ifEmpty { type.joinToString() }, t)
            }
        }
        return o["data"] as? JsonObject ?: bad(op, "no data", t)
    }

    /** Reads [r], keeping the rate limit it reports; a used-up limit (403/429 with Retry-After or none left) blocks. */
    private fun answer(r: Response): Answer {
        r.header("X-RateLimit-Remaining")?.trim()?.toIntOrNull()?.let { remaining = it }
        r.header("X-RateLimit-Reset")?.trim()?.toLongOrNull()?.let { resetAt = it * 1000 }
        val retryAfter = r.header("Retry-After")?.trim()?.toLongOrNull()
        val text = r.body?.string().orEmpty()
        if (r.code == 429 || (r.code == 403 && (retryAfter != null || remaining == 0))) {
            throw block(retryAfter?.let { clock() + it * 1000 } ?: resetAt?.takeIf { remaining == 0 } ?: (clock() + MINUTE))
        }
        if (remaining == 0) blockedUntil = resetAt
        return Answer(r.code, text)
    }

    private fun block(until: Long): DiscussionsError {
        blockedUntil = until
        return limited(until)
    }

    private fun limited(until: Long) =
        DiscussionsError(DiscussionsError.Kind.RATE_LIMITED, "GitHub's rate limit: wait until ${Instant.ofEpochMilli(until)}", until)

    /** Runs [req] off the main thread, cancelled with the coroutine; an [IOException] is [DiscussionsError.Kind.NETWORK]. */
    private suspend fun <T> execute(req: Request, t: String, read: (Response) -> T): T = withContext(Dispatchers.IO) {
        val call = http.newCall(req)
        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { if (it != null) call.cancel() }
        try {
            call.execute().use(read)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DiscussionsError) {
            throw e
        } catch (e: IOException) {
            throw DiscussionsError(DiscussionsError.Kind.NETWORK, "GitHub can't be reached: ${scrub(e.message ?: e.javaClass.simpleName, t)}")
        } finally {
            handle?.dispose()
        }
    }

    private fun message(a: Answer): String? =
        runCatching { (tutorJson.parseToJsonElement(a.body) as JsonObject).str("message") }.getOrNull() ?: a.body.take(200).trim().ifEmpty { null }

    private fun bad(op: String, what: String, t: String? = null): Nothing =
        throw DiscussionsError(DiscussionsError.Kind.BAD_ANSWER, "GitHub's answer to $op: ${t?.let { scrub(what, it) } ?: what}")

    private fun discussionOf(o: JsonObject, op: String) = Discussion(
        id = o.str("id") ?: bad(op, "a discussion without id"),
        number = o.int("number") ?: bad(op, "a discussion without number"),
        title = o.str("title") ?: bad(op, "a discussion without title"),
        url = o.str("url") ?: bad(op, "a discussion without url"),
        upvotes = o.int("upvoteCount") ?: 0,
        upvoted = o.bool("viewerHasUpvoted") ?: false,
        closed = o.bool("closed") ?: false,
        category = o.obj("category")?.str("name"),
    )

    companion object {
        const val API = "https://api.github.com"
        const val REPO = "fentas/lani"

        /** Each part of a report's body is cut to this many characters. */
        const val PART_MAX = 2_000
        const val NATIVE_CHECK = "native check: a native speaker should check this Slovene (machine-written)"
        const val FOOTER = "Reported from the Lani app's tutor."

        /** A report's kind → its category's name and slug. */
        private val CATEGORIES = mapOf("fix" to ("Content fixes" to "content-fixes"), "wish" to ("Ideas" to "ideas"))
        private val FORBIDDEN_SAID = listOf("Resource not accessible", "insufficient scopes", "required scopes")
        private val WHITESPACE = Regex("\\s+")
        private const val MINUTE = 60_000L
        private val JSON = "application/json".toMediaType()
        val DEFAULT_CLIENT: OkHttpClient by lazy {
            OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
        }

        private const val FIELDS = "id number title url upvoteCount viewerHasUpvoted closed category { name }"
        private const val FIND =
            "query FindDiscussion(\$q: String!) { search(type: DISCUSSION, first: 10, query: \$q) { nodes { ... on Discussion { $FIELDS } } } }"
        private const val UPVOTE =
            "mutation Upvote(\$id: ID!) { addUpvote(input: {subjectId: \$id}) { subject { upvoteCount viewerHasUpvoted } } }"
        private const val REPOSITORY =
            "query ReportRepository(\$owner: String!, \$name: String!) { repository(owner: \$owner, name: \$name) { id discussionCategories(first: 25) { nodes { id name slug } } } }"
        private const val CREATE =
            "mutation CreateDiscussion(\$repositoryId: ID!, \$categoryId: ID!, \$title: String!, \$body: String!) { " +
                "createDiscussion(input: {repositoryId: \$repositoryId, categoryId: \$categoryId, title: \$title, body: \$body}) { discussion { $FIELDS } } }"

        /**
         * Where the token may go: GitHub's API over https, or (a test's fake) the same scheme, host and port as [api] when
         * that isn't GitHub's. Never plain http to GitHub, never another host.
         */
        fun mayCarryToken(url: HttpUrl, api: String): Boolean {
            val b = api.toHttpUrlOrNull() ?: return false
            if (url.host == "api.github.com" && url.scheme == "https") return true
            return b.host != "api.github.com" && url.scheme == b.scheme && url.host == b.host && url.port == b.port
        }

        /**
         * A report's Markdown body, as the category's form lays it out: the item's source (its JSON, in a code span), what
         * is wrong (a wish: what is wished for), the suggested fix, [NATIVE_CHECK] when asked, and [FOOTER] last. Nothing
         * else: no name, no chat. Each part is cut to [PART_MAX] characters.
         */
        fun body(d: ReportDraft): String = buildString {
            d.item?.let { append("### Item\n\n").append(codeSpan(cut(it.toString()))).append("\n\n") }
            append(if (d.kind == "wish") "### What is wished for\n\n" else "### What is wrong\n\n")
            append(cut(d.whatIsWrong.trim())).append("\n\n")
            d.suggestedFix?.trim()?.takeIf { it.isNotEmpty() }?.let { append("### Suggested fix\n\n").append(cut(it)).append("\n\n") }
            if (d.nativeCheck) append(NATIVE_CHECK).append("\n\n")
            append(FOOTER)
        }

        /** [s] cut to [PART_MAX] characters, an ellipsis last when cut, never in the middle of a surrogate pair. */
        internal fun cut(s: String): String {
            if (s.length <= PART_MAX) return s
            var n = PART_MAX - 1
            if (s[n - 1].isHighSurrogate()) n--
            return s.take(n) + "…"
        }

        /** [s] as a Markdown code span: fenced by one backtick more than its longest run of them. */
        internal fun codeSpan(s: String): String {
            val run = Regex("`+").findAll(s).maxOfOrNull { it.value.length } ?: 0
            val fence = "`".repeat(run + 1)
            val pad = if (s.startsWith("`") || s.endsWith("`")) " " else ""
            return "$fence$pad$s$pad$fence"
        }

        /** A title to compare: trimmed, runs of whitespace as one space, lower case. */
        internal fun normal(title: String): String = title.trim().replace(WHITESPACE, " ").lowercase()

        private fun scrub(s: String, t: String): String = if (t.isEmpty()) s else s.replace(t, "…")
    }
}

private fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject
private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
private fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
