package si.lanisce.lani.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant

/**
 * "Sign in with GitHub" without a browser redirect: GitHub's OAuth device flow (docs/plans/05-built/git-sync.md, "What Jan
 * sets up"). The app asks for a code ([start]), shows it with github.com/login/device, and asks until the learner entered
 * it there ([await]). [clientId] is the OAuth app's (BuildConfig.GITHUB_CLIENT_ID; empty: no sign-in, a pasted token
 * only). A GitHub App's client id works the same way; its tokens must not expire (the app has no client secret to renew
 * them).
 */
class DeviceFlow(
    private val clientId: String,
    private val base: String = "https://github.com",
    private val http: OkHttpClient = GitHub.DEFAULT_CLIENT,
    private val clock: () -> Instant = Instant::now,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    /** The code to show ([userCode], typed at [verificationUri]), valid until [expiresAt]; asked again every [interval] s. */
    data class Code(val deviceCode: String, val userCode: String, val verificationUri: String, val expiresAt: Instant, val interval: Int)

    sealed interface Result {
        data class Token(val token: String, val scope: String) : Result
        data object Expired : Result
        data object Denied : Result
        data class Failed(val message: String) : Result
    }

    val available: Boolean get() = clientId.isNotBlank()

    /** A code for the learner to enter on GitHub. The OAuth app's scope [scope]: its private repositories (`repo`). */
    suspend fun start(scope: String = "repo"): Code {
        val o = post("/login/device/code", FormBody.Builder().add("client_id", clientId).add("scope", scope).build())
        o["error"]?.jsonPrimitive?.contentOrNull?.let { throw IOException("GitHub: $it (${o["error_description"]?.jsonPrimitive?.contentOrNull.orEmpty()})") }
        return Code(
            deviceCode = o.text("device_code"),
            userCode = o.text("user_code"),
            verificationUri = o["verification_uri"]?.jsonPrimitive?.contentOrNull ?: "https://github.com/login/device",
            expiresAt = clock().plusSeconds((o["expires_in"]?.jsonPrimitive?.intOrNull ?: 900).toLong()),
            interval = o["interval"]?.jsonPrimitive?.intOrNull ?: 5,
        )
    }

    /** Asks GitHub until the learner entered [code] (a token), refused, or the code ran out. [stop]: the learner gave up. */
    suspend fun await(code: Code, stop: () -> Boolean = { false }): Result {
        var interval = code.interval.coerceAtLeast(1)
        while (!stop()) {
            if (clock().isAfter(code.expiresAt)) return Result.Expired
            sleep(interval * 1000L)
            if (stop()) break
            val o = try {
                post("/login/oauth/access_token", FormBody.Builder().add("client_id", clientId).add("device_code", code.deviceCode)
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code").build())
            } catch (e: IOException) {
                continue // offline for a moment: ask again
            }
            o["access_token"]?.jsonPrimitive?.contentOrNull?.let { return Result.Token(it, o["scope"]?.jsonPrimitive?.contentOrNull.orEmpty()) }
            when (o["error"]?.jsonPrimitive?.contentOrNull) {
                "authorization_pending" -> Unit
                "slow_down" -> interval = o["interval"]?.jsonPrimitive?.intOrNull ?: (interval + 5)
                "expired_token" -> return Result.Expired
                "access_denied" -> return Result.Denied
                else -> return Result.Failed(o["error_description"]?.jsonPrimitive?.contentOrNull ?: o["error"]?.jsonPrimitive?.contentOrNull ?: "no token")
            }
        }
        return Result.Denied
    }

    private suspend fun post(path: String, form: FormBody): JsonObject = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(base.trimEnd('/') + path).header("Accept", "application/json").post(form).build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("GitHub ${r.code}: ${text.take(200)}")
            syncRead.parseToJsonElement(text) as? JsonObject ?: throw IOException("GitHub's answer isn't JSON")
        }
    }

    private fun JsonObject.text(k: String) = this[k]?.jsonPrimitive?.contentOrNull ?: throw IOException("GitHub's answer has no $k")
}
