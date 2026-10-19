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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import si.lanisce.lani.data.BridgeConfig
import si.lanisce.lani.tutor.Redact
import java.io.IOException
import java.util.concurrent.TimeUnit

/** What the paired bridge answered that the live tutor can't use: its HTTP [code] (0: no answer at all). */
class LiveBridgeError(val code: Int, message: String) : IOException(message) {
    /** No answer: the phone is offline, or the node is down. */
    val offline: Boolean get() = code == 0
}

/**
 * The paired bridge's tutor session (companion/bridge/src/features/tutor.ts, docs/plans/05-built/tutor-live.md): the
 * app's tutor as a client of the channel, with the app's token. The same routes on a node over Tailscale and, later,
 * on the phone's own bridge in-process.
 */
class LiveBridge(private val config: BridgeConfig, base: OkHttpClient = defaultClient) {
    /** The long poll waits up to 25 s on the bridge: the client's read timeout is longer. */
    private val client = base.newBuilder().readTimeout(45, TimeUnit.SECONDS).build()

    /** The session: the protocol, the channel's tools (Claude Code's list) and the app session's own, who is attached. */
    data class Session(val protocol: Int, val tools: List<String>, val appTools: List<String>, val attached: Boolean, val instructions: String)

    /** A notification of the channel, as the inbox keeps it. */
    data class Item(val id: Long, val content: String, val meta: Map<String, String>, val at: Long)

    data class Inbox(val items: List<Item>, val attached: Boolean, val size: Int, val lastId: Long)

    /** A tool's result as the channel gives it: its text, and whether it is an error. */
    data class ToolResult(val text: String, val isError: Boolean)

    data class Notebook(val text: String, val updatedAt: String?)

    suspend fun session(): Session {
        val o = obj(call("GET", "/tutor/session"))
        fun names(k: String) = (o[k] as? JsonArray).orEmpty().mapNotNull { ((it as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull }
        return Session(
            protocol = (o["protocol"] as? JsonPrimitive)?.intOrNull ?: 0,
            tools = names("tools"), appTools = names("app_tools"),
            attached = o["attached"] is JsonObject,
            instructions = (o["instructions"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
        )
    }

    /** The app's tutor takes the channel; true when it has it. */
    suspend fun attach(device: String): Boolean {
        val o = obj(call("POST", "/tutor/attach", buildJsonObject { put("who", "app"); put("device", device.take(80)) }))
        return o["attached"] is JsonObject
    }

    /** The channel back to the Claude Code session; how many events the inbox gave back to it. */
    suspend fun detach(): Int = ((obj(call("POST", "/tutor/detach", JsonObject(emptyMap())))["returned"]) as? JsonPrimitive)?.intOrNull ?: 0

    /** The notifications after [after], waiting up to [wait] seconds for one. */
    suspend fun inbox(after: Long, wait: Int): Inbox {
        val o = obj(call("GET", "/tutor/inbox?after=$after&wait=${wait.coerceIn(0, 25)}"))
        val items = (o["items"] as? JsonArray).orEmpty().mapNotNull { e ->
            val i = e as? JsonObject ?: return@mapNotNull null
            Item(
                id = (i["id"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null,
                content = (i["content"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                meta = (i["meta"] as? JsonObject).orEmpty().mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull.orEmpty() },
                at = (i["at"] as? JsonPrimitive)?.longOrNull ?: 0,
            )
        }
        val inbox = o["inbox"] as? JsonObject
        return Inbox(items, o["attached"] is JsonObject, (inbox?.get("size") as? JsonPrimitive)?.intOrNull ?: items.size, (inbox?.get("last_id") as? JsonPrimitive)?.longOrNull ?: 0)
    }

    /** Done with the notifications [ids]. */
    suspend fun ack(ids: List<Long>) {
        if (ids.isEmpty()) return
        call("POST", "/tutor/inbox/ack", buildJsonObject { put("ids", JsonArray(ids.map { JsonPrimitive(it) })) })
    }

    /**
     * Tool [name] with [args], through the channel's handlers; [clientId] (the tool call's id) makes a retry safe: the
     * bridge answers a repeat with the first answer. A service restarting (503) is tried once more after a second.
     */
    suspend fun tool(name: String, args: JsonObject, clientId: String?): ToolResult {
        val body = buildJsonObject {
            put("args", args)
            clientId?.let { put("client_id", it.take(80)) }
        }
        val raw = try {
            call("POST", "/tutor/tools/$name", body)
        } catch (e: LiveBridgeError) {
            if (e.code != 503) throw e
            kotlinx.coroutines.delay(1_000)
            call("POST", "/tutor/tools/$name", body)
        }
        val o = obj(raw)
        val text = (o["content"] as? JsonArray).orEmpty().mapNotNull { ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull }.joinToString("\n")
        return ToolResult(text, (o["isError"] as? JsonPrimitive)?.booleanOrNull == true)
    }

    suspend fun notebook(): Notebook {
        val o = obj(call("GET", "/tutor/notebook"))
        return Notebook((o["text"] as? JsonPrimitive)?.contentOrNull.orEmpty(), (o["updated_at"] as? JsonPrimitive)?.contentOrNull)
    }

    /** The learner's own correction of the notebook ("What my tutor knows"). */
    suspend fun saveNotebook(text: String): Notebook {
        val o = obj(call("PUT", "/tutor/notebook", buildJsonObject { put("text", text) }))
        return Notebook((o["text"] as? JsonPrimitive)?.contentOrNull.orEmpty(), (o["updated_at"] as? JsonPrimitive)?.contentOrNull)
    }

    /** GET /lookup: the app's dictionary on the node (the word, the sentence it was in); the body as it came. */
    suspend fun lookup(word: String, line: String?): String {
        val url = (config.baseUrl.trimEnd('/') + "/lookup").toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("w", word.take(40))?.apply { line?.let { addQueryParameter("line", it.take(300)) } }?.build()
            ?: throw LiveBridgeError(0, "bad node address")
        return execute(Request.Builder().url(url).header("Authorization", "Bearer ${config.token}").get().build())
    }

    private suspend fun call(method: String, path: String, body: JsonObject? = null): String {
        val url = (config.baseUrl.trimEnd('/') + path).toHttpUrlOrNull() ?: throw LiveBridgeError(0, "bad node address")
        val req = Request.Builder().url(url).header("Authorization", "Bearer ${config.token}")
            .method(method, body?.toString()?.toRequestBody(JSON))
            .build()
        return execute(req)
    }

    private suspend fun execute(req: Request): String = withContext(Dispatchers.IO) {
        val call = client.newCall(req)
        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { if (it != null) call.cancel() }
        try {
            call.execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw LiveBridgeError(r.code, "HTTP ${r.code}: ${Redact.all(text.take(200), listOf(config.token))}")
                text
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LiveBridgeError) {
            throw e
        } catch (e: IOException) {
            throw LiveBridgeError(0, Redact.all(e.message ?: e.javaClass.simpleName, listOf(config.token)))
        } finally {
            handle?.dispose()
        }
    }

    private fun obj(raw: String): JsonObject =
        runCatching { si.lanisce.lani.tutor.tutorJson.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: throw LiveBridgeError(502, "the node's answer isn't JSON")

    companion object {
        private val JSON = "application/json".toMediaType()
        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
        }
    }
}
