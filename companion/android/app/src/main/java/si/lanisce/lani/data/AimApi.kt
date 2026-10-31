package si.lanisce.lani.data

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * "🎯 Cilj · Aim" on the node (companion/bridge/src/features/level.ts; docs/plans/05-built/setup-refresh.md): the
 * learner's level and the level the village's ages lead to (learner-profile.json target_level), read and set by the
 * first launch once paired with the family's computer. On the phone alone the aim comes with the new learner's answers
 * (hosted/HostedAnswers.targetLevel).
 */
class AimApi(private val config: BridgeConfig) {
    private val http = Http.client(connectSeconds = 3, readSeconds = 15)

    /** The learner's level and aim ([level] to [target]); null from an older node (404). */
    data class Aim(val level: String, val target: String?)

    suspend fun get(): Aim? {
        val (code, raw) = http.exchange(config.request(PATH).build())
        if (code == 404) return null
        if (code !in 200..299) throw IOException("HTTP $code")
        return parse(raw)
    }

    /** Keeps [level] as the aim; the node's answer. An older node (404) can't: null. */
    suspend fun set(level: String): Aim? {
        val body = buildJsonObject { put("level", level) }.toString().toRequestBody(Http.jsonType)
        val (code, raw) = http.exchange(config.request(PATH).post(body).build())
        if (code == 404) return null
        if (code !in 200..299) throw IOException("HTTP $code: ${raw.take(200)}")
        return parse(raw)
    }

    companion object {
        const val PATH = "/learner/target"

        /** GET or POST /learner/target's answer {level, target}; null when it isn't one. */
        fun parse(raw: String): Aim? = runCatching {
            val o = json.parseToJsonElement(raw).jsonObject
            val level = o["level"]?.jsonPrimitive?.contentOrNull ?: return null
            Aim(level, o["target"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() })
        }.getOrNull()
    }
}
