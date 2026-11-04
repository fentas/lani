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

    /**
     * The learner's level and aim ([level] to [target]); [next]: a raise waiting for the village's next age and [fromAge] the
     * age it starts with (companion/GAME.md "A target raised later"), null when none waits or from an older node; [change]
     * after a POST: what the node did ("now", "next_age" or "unchanged"), null from an older node or a GET.
     */
    data class Aim(val level: String, val target: String?, val next: String? = null, val fromAge: String? = null, val change: String? = null)

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

        /** GET or POST /learner/target's answer {level, target, next?: {level, from_age}, change?}; null when it isn't one. */
        fun parse(raw: String): Aim? = runCatching {
            val o = json.parseToJsonElement(raw).jsonObject
            val level = o["level"]?.jsonPrimitive?.contentOrNull ?: return null
            fun s(e: kotlinx.serialization.json.JsonElement?) = e?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
            val next = o["next"] as? kotlinx.serialization.json.JsonObject
            Aim(level, s(o["target"]), s(next?.get("level")), s(next?.get("from_age")), s(o["change"]))
        }.getOrNull()
    }
}

/**
 * What the first run left at its defaults, on the node (companion/bridge/src/features/level.ts; docs/plans/05-built/
 * first-minutes.md): how the village speaks to the learner, the minutes a day, the level. An older node answers 404: null.
 */
class AboutApi(private val config: BridgeConfig) {
    private val http = Http.client(connectSeconds = 3, readSeconds = 15)

    /** [gender]: "female", "male", null while not said; [goal]: the minutes a day, null when the profile has none. */
    data class About(val gender: String?, val goal: Int?, val level: String)

    suspend fun get(): About? {
        val (code, raw) = http.exchange(config.request(PATH).build())
        if (code == 404) return null
        if (code !in 200..299) throw IOException("HTTP $code")
        return parse(raw)
    }

    /** Keeps what isn't null; the node's answer. */
    suspend fun set(gender: String? = null, goal: Int? = null, level: String? = null): About? {
        val body = buildJsonObject {
            gender?.let { put("gender", it) }
            goal?.let { put("goal", it) }
            level?.let { put("level", it) }
        }.toString().toRequestBody(Http.jsonType)
        val (code, raw) = http.exchange(config.request(PATH).post(body).build())
        if (code == 404) return null
        if (code !in 200..299) throw IOException("HTTP $code: ${raw.take(200)}")
        return parse(raw)
    }

    companion object {
        const val PATH = "/learner/about"

        /** GET or POST /learner/about's answer {gender, goal, level}; null when it isn't one. */
        fun parse(raw: String): About? = runCatching {
            val o = json.parseToJsonElement(raw).jsonObject
            val level = o["level"]?.jsonPrimitive?.contentOrNull ?: return null
            fun s(k: String) = o[k]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
            About(s("gender"), s("goal")?.toIntOrNull(), level)
        }.getOrNull()
    }
}
