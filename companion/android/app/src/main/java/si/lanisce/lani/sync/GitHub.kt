package si.lanisce.lani.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit

/** An answer of GitHub's that isn't what was asked for: [code] and GitHub's message. */
open class GitHubError(val code: Int, message: String) : IOException(message)

/** GitHub's rate limit is used up until [until] (the hourly limit, or a secondary limit's Retry-After). */
class RateLimited(val until: Instant, message: String) : GitHubError(429, message)

/** A repository on GitHub and the branch the data lives on. */
data class RepoRef(val owner: String, val name: String, val branch: String = "main") {
    val full: String get() = "$owner/$name"

    companion object {
        private val PART = Regex("^[A-Za-z0-9_.-]{1,100}$")

        /** "owner/name" (or a github.com URL) → a ref; null when it isn't one. */
        fun parse(s: String, branch: String = "main"): RepoRef? {
            val t = s.trim().removePrefix("https://github.com/").removePrefix("git@github.com:").removeSuffix("/").removeSuffix(".git")
            val parts = t.split('/')
            if (parts.size != 2 || parts.any { !PART.matches(it) }) return null
            return RepoRef(parts[0], parts[1], branch)
        }
    }
}

/** A repository the token reaches: whether it is private, its default branch, whether the token may push. */
data class GitHubRepo(val owner: String, val name: String, val private: Boolean, val defaultBranch: String, val canPush: Boolean) {
    val full: String get() = "$owner/$name"
}

/** A file of a commit's tree. */
data class TreeFile(val path: String, val sha: String, val size: Long)

data class GitCommit(val sha: String, val tree: String, val parents: List<String>)

/** One change of a new tree: [path] gets the blob [sha], or the text [content], or (both null) goes. */
data class TreeEdit(val path: String, val sha: String? = null, val content: String? = null)

/** Where a branch is: at a commit, missing, in a repository without any commit yet, or (with an ETag) where it was. */
sealed interface RefState {
    data class At(val sha: String, val etag: String?) : RefState
    data object Missing : RefState
    data object EmptyRepository : RefState
    data object NotModified : RefState
}

/**
 * GitHub's REST API for the data's sync (sync/SyncEngine.kt): the Git Data API (blobs, trees, commits, refs), no git
 * library. [token] is the learner's (the device flow's or a pasted one); [api] is https://api.github.com (a fake in the
 * tests).
 *
 * Rate limits (5,000 requests an hour with a token): each answer's X-RateLimit-Remaining and -Reset are kept, and once
 * the limit is used up every call throws [RateLimited] until the reset, without asking GitHub. A secondary limit's 403
 * or 429 with Retry-After does the same. The branch is asked with its ETag, so an unchanged branch (304) costs nothing.
 */
class GitHub(
    private val token: suspend () -> String?,
    private val api: String = "https://api.github.com",
    private val http: OkHttpClient = DEFAULT_CLIENT,
    private val clock: () -> Instant = Instant::now,
) {
    /** Requests left this hour, and when the count starts again, as GitHub said last. */
    @Volatile var remaining: Int? = null
        private set
    @Volatile var resetAt: Instant? = null
        private set
    @Volatile private var blockedUntil: Instant? = null

    suspend fun user(): String = obj(call("GET", "/user")).str("login")

    /** The repositories the token can see, recently pushed first (at most 100: the picker's list). */
    suspend fun repos(): List<GitHubRepo> =
        (syncRead.parseToJsonElement(call("GET", "/user/repos?per_page=100&sort=pushed").body) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::repoOf) }

    suspend fun repo(ref: RepoRef): GitHubRepo = repoOf(obj(call("GET", "/repos/${ref.full}")))

    /** A new private repository of the learner's, named [name] (an empty one: the first sync fills it). */
    suspend fun createRepo(name: String, description: String): GitHubRepo = repoOf(obj(call("POST", "/user/repos", buildJsonObject {
        put("name", name); put("private", true); put("description", description); put("auto_init", false)
        put("has_issues", false); put("has_wiki", false); put("has_projects", false)
    }, expect = setOf(201))))

    /** Where [ref]'s branch is; [etag]: its ETag of the last time (304: [RefState.NotModified]). */
    suspend fun ref(ref: RepoRef, etag: String? = null): RefState {
        val a = call("GET", "/repos/${ref.full}/git/ref/heads/${ref.branch}", expect = setOf(200, 304, 404, 409), headers = etag?.let { mapOf("If-None-Match" to it) }.orEmpty())
        return when (a.code) {
            304 -> RefState.NotModified
            404 -> RefState.Missing
            409 -> RefState.EmptyRepository // "Git Repository is empty."
            else -> RefState.At(obj(a).obj("object").str("sha"), a.etag)
        }
    }

    suspend fun commit(ref: RepoRef, sha: String): GitCommit {
        val o = obj(call("GET", "/repos/${ref.full}/git/commits/$sha"))
        return GitCommit(o.str("sha"), o.obj("tree").str("sha"), (o["parents"] as? JsonArray).orEmpty().map { it.jsonObject.str("sha") })
    }

    /** Every file of the tree [sha] (recursive). A tree GitHub cuts short (over 100,000 entries) is an error. */
    suspend fun tree(ref: RepoRef, sha: String): List<TreeFile> {
        val o = obj(call("GET", "/repos/${ref.full}/git/trees/$sha?recursive=1"))
        if (o["truncated"]?.jsonPrimitive?.booleanOrNull == true) throw GitHubError(413, "the repository's tree is too large to sync")
        return (o["tree"] as? JsonArray).orEmpty().map { it.jsonObject }.filter { it.str("type") == "blob" }
            .map { TreeFile(it.str("path"), it.str("sha"), it["size"]?.jsonPrimitive?.longOrNull ?: 0) }
    }

    suspend fun blob(ref: RepoRef, sha: String): ByteArray {
        val o = obj(call("GET", "/repos/${ref.full}/git/blobs/$sha"))
        val content = o.str("content")
        return if (o["encoding"]?.jsonPrimitive?.contentOrNull == "base64") Base64.getMimeDecoder().decode(content) else content.toByteArray()
    }

    suspend fun createBlob(ref: RepoRef, bytes: ByteArray): String = obj(call("POST", "/repos/${ref.full}/git/blobs", buildJsonObject {
        put("content", Base64.getEncoder().encodeToString(bytes)); put("encoding", "base64")
    }, expect = setOf(201))).str("sha")

    /** A new tree: [base] (a tree's sha) with [edits]; without a base, the edits are the whole tree. */
    suspend fun createTree(ref: RepoRef, base: String?, edits: List<TreeEdit>): String = obj(call("POST", "/repos/${ref.full}/git/trees", buildJsonObject {
        if (base != null) put("base_tree", base)
        put("tree", buildJsonArray {
            for (e in edits) add(buildJsonObject {
                put("path", e.path); put("mode", "100644"); put("type", "blob")
                when {
                    e.content != null -> put("content", e.content)
                    else -> put("sha", e.sha?.let(::JsonPrimitive) ?: JsonNull) // null: the file goes
                }
            })
        })
    }, expect = setOf(201))).str("sha")

    suspend fun createCommit(ref: RepoRef, message: String, tree: String, parents: List<String>): String = obj(call("POST", "/repos/${ref.full}/git/commits", buildJsonObject {
        put("message", message); put("tree", tree); put("parents", buildJsonArray { parents.forEach { add(JsonPrimitive(it)) } })
    }, expect = setOf(201))).str("sha")

    /** Moves the branch to [sha], fast-forward only: false when the branch went on meanwhile (GitHub's 422). */
    suspend fun moveBranch(ref: RepoRef, sha: String): Boolean =
        call("PATCH", "/repos/${ref.full}/git/refs/heads/${ref.branch}", buildJsonObject { put("sha", sha); put("force", false) }, expect = setOf(200, 422)).code == 200

    /** A new branch [branch] at [sha]: false when it is there already. */
    suspend fun createBranch(ref: RepoRef, branch: String, sha: String): Boolean =
        call("POST", "/repos/${ref.full}/git/refs", buildJsonObject { put("ref", "refs/heads/$branch"); put("sha", sha) }, expect = setOf(201, 422)).code == 201

    /** How [head] stands to [base]: "ahead" (head went on from base), "identical", "behind" or "diverged". */
    suspend fun compare(ref: RepoRef, base: String, head: String): String = obj(call("GET", "/repos/${ref.full}/compare/$base...$head")).str("status")

    /**
     * The first commit of an empty repository (the Git Data API refuses to work on one): [path] with [bytes], through the
     * contents API. Returns the commit's sha.
     */
    suspend fun firstFile(ref: RepoRef, path: String, bytes: ByteArray, message: String): String = obj(call("PUT", "/repos/${ref.full}/contents/$path", buildJsonObject {
        put("message", message); put("content", Base64.getEncoder().encodeToString(bytes)); put("branch", ref.branch)
    }, expect = setOf(200, 201))).obj("commit").str("sha")

    // --- the plumbing ----------------------------------------------------------------------------------

    private class Answer(val code: Int, val body: String, val etag: String?)

    private suspend fun call(method: String, path: String, body: JsonElement? = null, expect: Set<Int> = setOf(200), headers: Map<String, String> = emptyMap()): Answer {
        val now = clock()
        blockedUntil?.let { if (now.isBefore(it)) throw RateLimited(it, "GitHub's rate limit: wait until $it") }
        val t = token() ?: throw GitHubError(401, "no GitHub token")
        val req = Request.Builder().url(api.trimEnd('/') + path)
            .header("Authorization", "Bearer $t")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .method(method, body?.toString()?.toRequestBody(JSON) ?: if (method in setOf("POST", "PUT", "PATCH")) "{}".toRequestBody(JSON) else null)
            .build()
        val a = withContext(Dispatchers.IO) {
            http.newCall(req).execute().use { r ->
                r.header("X-RateLimit-Remaining")?.toIntOrNull()?.let { remaining = it }
                r.header("X-RateLimit-Reset")?.toLongOrNull()?.let { resetAt = Instant.ofEpochSecond(it) }
                val retryAfter = r.header("Retry-After")?.toLongOrNull()
                val text = r.body?.string().orEmpty()
                if ((r.code == 403 || r.code == 429) && (retryAfter != null || remaining == 0)) {
                    val until = retryAfter?.let { clock().plusSeconds(it) } ?: resetAt ?: clock().plusSeconds(60)
                    blockedUntil = until
                    throw RateLimited(until, "GitHub's rate limit: wait until $until")
                }
                Answer(r.code, text, r.header("ETag"))
            }
        }
        if (remaining == 0) blockedUntil = resetAt
        if (a.code !in expect) {
            val msg = runCatching { obj(a).str("message") }.getOrDefault(a.body.take(200))
            throw GitHubError(a.code, if (a.code == 401) "GitHub refused the token (401): sign in again" else "GitHub $method ${path.substringBefore('?')}: ${a.code} $msg")
        }
        return a
    }

    private fun obj(a: Answer): JsonObject = syncRead.parseToJsonElement(a.body).jsonObject

    private fun repoOf(o: JsonObject) = GitHubRepo(
        owner = o.obj("owner").str("login"),
        name = o.str("name"),
        private = o["private"]?.jsonPrimitive?.booleanOrNull ?: true,
        defaultBranch = o["default_branch"]?.jsonPrimitive?.contentOrNull ?: "main",
        canPush = (o["permissions"] as? JsonObject)?.get("push")?.jsonPrimitive?.booleanOrNull ?: true,
    )

    companion object {
        private val JSON = "application/json".toMediaType()
        val DEFAULT_CLIENT: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

        /** GitHub rejects files over 100 MB and warns from 50 MB: the sync leaves larger ones on the phone and says so. */
        const val MAX_FILE = 50L * 1024 * 1024
    }
}

private fun JsonObject.str(k: String): String = this[k]?.jsonPrimitive?.contentOrNull ?: throw GitHubError(502, "GitHub's answer has no $k")
private fun JsonObject.obj(k: String): JsonObject = this[k] as? JsonObject ?: throw GitHubError(502, "GitHub's answer has no $k")
private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
