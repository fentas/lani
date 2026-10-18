package si.lanisce.lani.sync

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.security.MessageDigest
import java.util.Base64

/**
 * GitHub's REST API as the sync uses it, over an in-memory git: blobs (named as git names them), trees, commits, branches
 * (moved fast-forward only), compare, the contents API's first commit of an empty repository, the repositories of a user,
 * rate limits and ETags. [nodePushes] plays the node (or any other side) pushing a commit.
 */
class FakeGitHub(val owner: String = "ana", val repoName: String = "lani-data", val token: String = "gho_testtoken") : Dispatcher() {
    val server = MockWebServer().also { it.dispatcher = this }
    val url: String get() = server.url("/").toString().trimEnd('/')
    val ref = RepoRef(owner, repoName)

    val blobs = HashMap<String, ByteArray>()
    private val trees = HashMap<String, Map<String, String>>()
    private class Commit(val tree: String, val parents: List<String>, val message: String)
    private val commits = HashMap<String, Commit>()
    val branches = LinkedHashMap<String, String>()
    val requests = mutableListOf<String>()
    var repoPrivate = true
    var counter = 0

    /** Requests left before the hourly limit is used up (null: no limit). */
    var remaining: Int? = null
    var resetAt: Long = 4_000_000_000L

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun putTree(files: Map<String, String>): String {
        val sha = sha1("tree " + files.toSortedMap().entries.joinToString("\n") { "${it.key} ${it.value}" })
        trees[sha] = files.toMap()
        return sha
    }

    private fun putCommit(tree: String, parents: List<String>, message: String): String {
        val sha = sha1("commit $tree ${parents.joinToString()} $message ${counter++}")
        commits[sha] = Commit(tree, parents, message)
        return sha
    }

    /** Whether [old] is [new] or one of its ancestors. */
    fun ancestor(old: String, new: String): Boolean {
        if (old == new) return true
        val seen = HashSet<String>()
        val todo = ArrayDeque(listOf(new))
        while (todo.isNotEmpty()) {
            val c = todo.removeFirst()
            if (!seen.add(c)) continue
            if (c == old) return true
            commits[c]?.parents?.let(todo::addAll)
        }
        return false
    }

    /** Another side pushes [files] (path → text; null removes it) on top of [branch]. Returns the commit. */
    fun nodePushes(files: Map<String, String?>, message: String = "node: a session", branch: String = "main"): String {
        val head = branches[branch]
        val now = head?.let { trees.getValue(commits.getValue(it).tree) }.orEmpty().toMutableMap()
        for ((p, text) in files) {
            if (text == null) now.remove(p) else {
                val b = text.toByteArray()
                val s = blobSha(b)
                blobs[s] = b
                now[p] = s
            }
        }
        val c = putCommit(putTree(now), listOfNotNull(head), message)
        branches[branch] = c
        return c
    }

    /** Rewrites [branch] (a force-push elsewhere): a commit that doesn't descend from the one before. */
    fun rewrite(files: Map<String, String>, branch: String = "main"): String {
        val tree = files.mapValues { (_, t) -> val b = t.toByteArray(); blobSha(b).also { blobs[it] = b } }
        val c = putCommit(putTree(tree), emptyList(), "rewritten")
        branches[branch] = c
        return c
    }

    /** The files of [branch] (path → text). */
    fun files(branch: String = "main"): Map<String, String> =
        branches[branch]?.let { trees.getValue(commits.getValue(it).tree) }.orEmpty().mapValues { blobs.getValue(it.value).decodeToString() }

    fun message(branch: String = "main"): String = commits.getValue(branches.getValue(branch)).message

    fun commitCount(branch: String = "main"): Int {
        var n = 0
        var c = branches[branch]
        while (c != null) { n++; c = commits[c]?.parents?.firstOrNull() }
        return n
    }

    private fun json(code: Int, body: Any?) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json")
        .setHeader("X-RateLimit-Limit", "5000")
        .apply { remaining?.let { setHeader("X-RateLimit-Remaining", it.toString()); setHeader("X-RateLimit-Reset", resetAt.toString()) } }
        .setBody(body?.toString() ?: "")

    private fun error(code: Int, msg: String) = json(code, buildJsonObject { put("message", msg) })

    private fun repoJson(name: String = repoName) = buildJsonObject {
        put("name", name); put("full_name", "$owner/$name"); put("private", repoPrivate); put("default_branch", "main")
        put("owner", buildJsonObject { put("login", owner) }); put("permissions", buildJsonObject { put("push", true) })
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val method = request.method ?: "GET"
        val full = request.path ?: "/"
        val path = full.substringBefore('?')
        requests += "$method $path"
        if (request.getHeader("Authorization") != "Bearer $token") return error(401, "Bad credentials")
        remaining?.let {
            if (it <= 0) return error(403, "API rate limit exceeded")
            remaining = it - 1
        }
        val body = request.body.readUtf8().takeIf { it.isNotBlank() }?.let { syncRead.parseToJsonElement(it).jsonObject }
        val repoBase = "/repos/$owner/$repoName"
        return when {
            method == "GET" && path == "/user" -> json(200, buildJsonObject { put("login", owner) })
            method == "GET" && path == "/user/repos" -> json(200, buildJsonArray { add(repoJson()) })
            method == "POST" && path == "/user/repos" -> json(201, repoJson(body!!["name"]!!.jsonPrimitive.content))
            method == "GET" && path == repoBase -> json(200, repoJson())
            method == "GET" && path.startsWith("$repoBase/git/ref/heads/") -> {
                if (commits.isEmpty()) return error(409, "Git Repository is empty.")
                val sha = branches[path.removePrefix("$repoBase/git/ref/heads/")] ?: return error(404, "Not Found")
                val etag = "W/\"$sha\""
                if (request.getHeader("If-None-Match") == etag) MockResponse().setResponseCode(304)
                else json(200, buildJsonObject { put("ref", "refs/heads/main"); put("object", buildJsonObject { put("sha", sha); put("type", "commit") }) }).setHeader("ETag", etag)
            }
            method == "PATCH" && path.startsWith("$repoBase/git/refs/heads/") -> {
                val b = path.removePrefix("$repoBase/git/refs/heads/")
                val sha = body!!["sha"]!!.jsonPrimitive.content
                val now = branches[b] ?: return error(422, "Reference does not exist")
                if (body["force"]?.jsonPrimitive?.content == "true") error(400, "the sync never forces")
                else if (!ancestor(now, sha)) error(422, "Update is not a fast forward")
                else { branches[b] = sha; json(200, buildJsonObject { put("object", buildJsonObject { put("sha", sha) }) }) }
            }
            method == "POST" && path == "$repoBase/git/refs" -> {
                val b = body!!["ref"]!!.jsonPrimitive.content.removePrefix("refs/heads/")
                if (b in branches) error(422, "Reference already exists")
                else { branches[b] = body["sha"]!!.jsonPrimitive.content; json(201, buildJsonObject { put("ref", "refs/heads/$b") }) }
            }
            method == "GET" && path.startsWith("$repoBase/git/commits/") -> {
                val sha = path.substringAfterLast('/')
                val c = commits[sha] ?: return error(404, "No commit")
                json(200, buildJsonObject {
                    put("sha", sha); put("tree", buildJsonObject { put("sha", c.tree) }); put("message", c.message)
                    put("parents", buildJsonArray { c.parents.forEach { p -> add(buildJsonObject { put("sha", p) }) } })
                })
            }
            method == "POST" && path == "$repoBase/git/commits" -> {
                if (commits.isEmpty()) return error(409, "Git Repository is empty.")
                val tree = body!!["tree"]!!.jsonPrimitive.content
                if (tree !in trees) return error(422, "Tree not found")
                val parents = body["parents"]!!.jsonArray.map { it.jsonPrimitive.content }
                json(201, buildJsonObject { put("sha", putCommit(tree, parents, body["message"]!!.jsonPrimitive.content)) })
            }
            method == "GET" && path.startsWith("$repoBase/git/trees/") -> {
                val t = trees[path.substringAfterLast('/')] ?: return error(404, "No tree")
                json(200, buildJsonObject {
                    put("sha", path.substringAfterLast('/')); put("truncated", false)
                    put("tree", buildJsonArray {
                        // directories too, as GitHub lists them: the sync takes the blobs only
                        t.keys.flatMap { p -> p.split('/').dropLast(1).runningReduce { a, b -> "$a/$b" } }.toSortedSet().forEach { d ->
                            add(buildJsonObject { put("path", d); put("type", "tree"); put("mode", "040000"); put("sha", sha1(d)) })
                        }
                        t.toSortedMap().forEach { (p, s) -> add(buildJsonObject { put("path", p); put("type", "blob"); put("mode", "100644"); put("sha", s); put("size", blobs.getValue(s).size) }) }
                    })
                })
            }
            method == "POST" && path == "$repoBase/git/trees" -> {
                if (commits.isEmpty()) return error(409, "Git Repository is empty.")
                val base = body!!["base_tree"]?.jsonPrimitive?.contentOrNull
                val files = (base?.let { trees[it] ?: return error(422, "base_tree not found") }).orEmpty().toMutableMap()
                for (e in (body["tree"] as JsonArray).map { it.jsonObject }) {
                    val p = e["path"]!!.jsonPrimitive.content
                    val content = e["content"]?.jsonPrimitive?.contentOrNull
                    val sha = e["sha"]
                    when {
                        content != null -> { val b = content.toByteArray(); files[p] = blobSha(b).also { blobs[it] = b } }
                        sha == null || sha is JsonNull -> files.remove(p)
                        else -> { val s = sha.jsonPrimitive.content; if (s !in blobs) return error(422, "blob $s not found"); files[p] = s }
                    }
                }
                json(201, buildJsonObject { put("sha", putTree(files)) })
            }
            method == "GET" && path.startsWith("$repoBase/git/blobs/") -> {
                val b = blobs[path.substringAfterLast('/')] ?: return error(404, "No blob")
                // GitHub wraps its base64 in lines
                json(200, buildJsonObject { put("content", Base64.getMimeEncoder().encodeToString(b)); put("encoding", "base64"); put("size", b.size) })
            }
            method == "POST" && path == "$repoBase/git/blobs" -> {
                if (commits.isEmpty()) return error(409, "Git Repository is empty.")
                val b = Base64.getDecoder().decode(body!!["content"]!!.jsonPrimitive.content)
                json(201, buildJsonObject { put("sha", blobSha(b).also { blobs[it] = b }) })
            }
            method == "GET" && path.startsWith("$repoBase/compare/") -> {
                val (a, b) = path.removePrefix("$repoBase/compare/").split("...")
                val status = when {
                    a == b -> "identical"
                    ancestor(a, b) -> "ahead"
                    ancestor(b, a) -> "behind"
                    else -> "diverged"
                }
                json(200, buildJsonObject { put("status", status) })
            }
            method == "PUT" && path.startsWith("$repoBase/contents/") -> {
                if (commits.isNotEmpty()) return error(422, "the sync uses the contents API for an empty repository only")
                val p = path.removePrefix("$repoBase/contents/")
                val b = Base64.getDecoder().decode(body!!["content"]!!.jsonPrimitive.content)
                val s = blobSha(b).also { blobs[it] = b }
                val c = putCommit(putTree(mapOf(p to s)), emptyList(), body["message"]!!.jsonPrimitive.content)
                branches[body["branch"]?.jsonPrimitive?.contentOrNull ?: "main"] = c
                json(201, buildJsonObject { put("commit", buildJsonObject { put("sha", c) }) })
            }
            else -> error(404, "Not Found: $method $path")
        }
    }

    fun close() = server.shutdown()
}
