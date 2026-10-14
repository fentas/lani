package si.lanisce.lani.tutor

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A key shaped like a real one, for the guards: it must never show up anywhere but the x-api-key header. */
const val FAKE_KEY = "sk-ant-api03-SHADOWTESTKEY0123456789abcdefXYZ"

/** The Messages API played by a script: each call's body is kept; [respond] answers it (or throws an [ApiError]). */
class ScriptedTransport(
    private val models: List<ModelInfo> = listOf(ModelInfo("claude-haiku-5-5"), ModelInfo("claude-sonnet-5-5"), ModelInfo("claude-opus-5-5")),
    private val respond: (JsonObject, Int) -> AssistantMessage,
) : TutorTransport {
    val bodies = mutableListOf<JsonObject>()
    override suspend fun models(): List<ModelInfo> = models
    override suspend fun stream(body: JsonObject, onDelta: () -> Unit): StreamResult {
        bodies += body
        val m = respond(body, bodies.size)
        return StreamResult(m.copy(model = m.model ?: (body["model"] as? kotlinx.serialization.json.JsonPrimitive)?.content), 120, 900)
    }
}

fun text(t: String) = buildJsonObject { put("type", "text"); put("text", t) }
fun thinking(sig: String = "sig-abc") = buildJsonObject { put("type", "thinking"); put("thinking", ""); put("signature", sig) }
fun toolUse(id: String, name: String, input: JsonObject = JsonObject(emptyMap())) = buildJsonObject {
    put("type", "tool_use"); put("id", id); put("name", name); put("input", input)
}
fun obj(vararg kv: Pair<String, Any?>): JsonObject = buildJsonObject {
    kv.forEach { (k, v) ->
        when (v) {
            null -> Unit
            is String -> put(k, v)
            is Number -> put(k, v)
            is Boolean -> put(k, v)
            is JsonElement -> put(k, v)
            else -> put(k, v.toString())
        }
    }
}

fun message(stop: String, vararg blocks: JsonObject, usage: Usage = Usage(input = 50, cacheWrite1h = 3000, output = 80)) =
    AssistantMessage("msg_t", null, blocks.toList(), stop, null, usage)

/** The last user message's text and tool results of a request [body], as one string (what a script reacts to). */
fun lastUser(body: JsonObject): String =
    (body["messages"] as kotlinx.serialization.json.JsonArray).last().toString()

fun hasToolResult(body: JsonObject): Boolean = lastUser(body).contains("tool_result")

/** AES-GCM with a plain key: the Keystore's cipher, minus the Keystore (which a JVM test has none of). */
class SoftCipher : SecretCipher {
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    override fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        return c.iv + c.doFinal(plain)
    }
    override fun decrypt(sealed: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, 12)) }
        return c.doFinal(sealed, 12, sealed.size - 12)
    }
}

class MemoryStore : SecretStore {
    var value: String? = null
    override suspend fun read() = value
    override suspend fun write(value: String?) { this.value = value }
}

/** An OkHttp interceptor that answers every request itself ([answer]: status, body, content type) and keeps them. */
class Answering(private val answer: (Request) -> Triple<Int, String, String>) : Interceptor {
    val requests = mutableListOf<Request>()
    override fun intercept(chain: Interceptor.Chain): Response {
        val r = chain.request()
        requests += r
        val (code, body, type) = answer(r)
        return Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .apply { if (code == 429) header("retry-after", "2") }
            .body(body.toResponseBody(type.toMediaType())).build()
    }
}

/** A server-sent events body of [events] (type to data). */
fun sse(vararg events: Pair<String, String>): String = events.joinToString("") { (t, d) -> "event: $t\ndata: $d\n\n" }
