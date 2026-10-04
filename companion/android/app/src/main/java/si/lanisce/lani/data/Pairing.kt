package si.lanisce.lani.data

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import si.lanisce.lani.l10n.bi

/**
 * The schemes of a pairing code or a town's invitation: lani://, and fluent:// as the bridges printed them before the
 * project was renamed (a code or an invitation made then still works).
 */
internal val LINK_SCHEMES = listOf("lani", "fluent")

internal fun isLinkScheme(scheme: String?) = LINK_SCHEMES.any { it.equals(scheme, ignoreCase = true) }

/** Whether [text] begins like a link of ours to [host] ("lani://pair?…"), though it may not parse. */
internal fun startsLink(text: String, host: String) = LINK_SCHEMES.any { text.startsWith("$it://$host?", ignoreCase = true) }

/**
 * Pairing with a tutor by QR code (companion/README.md, "Pair a phone"). `companion/bin/lani-pair` shows
 * `lani://pair?v=1&u=<bridge URL>&c=<one-time code>&p=<learner profile>&f=<bridge key fingerprint>`; the app
 * trades the code for a device token (POST /pair) and checks that the answer is signed by the key the
 * fingerprint names. No camera here: [PairPayload.parse] takes the scanned text.
 */
data class PairPayload(val version: Int, val url: String, val code: String, val profile: String, val fingerprint: String) {
    companion object {
        /** The payload version this app understands. */
        const val VERSION = 1
        private val CODE = Regex("^[A-Z0-9]{6,32}$")
        private val PROFILE = Regex("^[a-z][a-z0-9-]{0,23}$")
        private val FINGERPRINT = Regex("^[0-9a-f]{32}$")

        fun parse(scanned: String): PairParse {
            val text = scanned.trim()
            // A Lani code the URI parser refuses (a broken escape) is a broken code, not someone else's.
            val uri = runCatching { URI(text) }.getOrNull()
                ?: return PairParse.Invalid(if (startsLink(text, "pair")) PairProblem.INCOMPLETE else PairProblem.NOT_PAIRING)
            if (!isLinkScheme(uri.scheme) || !uri.host.equals("pair", ignoreCase = true) ||
                !(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")
            ) return PairParse.Invalid(PairProblem.NOT_PAIRING)
            val q = query(uri.rawQuery.orEmpty())
            val version = q["v"]?.toIntOrNull() ?: return PairParse.Invalid(PairProblem.INCOMPLETE)
            if (version > VERSION) return PairParse.Invalid(PairProblem.NEWER_VERSION)
            if (version < 1) return PairParse.Invalid(PairProblem.INCOMPLETE)
            val url = bridgeUrl(q["u"] ?: return PairParse.Invalid(PairProblem.INCOMPLETE)) ?: return PairParse.Invalid(PairProblem.BAD_URL)
            val code = q["c"]?.uppercase()?.replace(Regex("[\\s-]"), "")?.takeIf { CODE.matches(it) } ?: return PairParse.Invalid(PairProblem.INCOMPLETE)
            val profile = q["p"]?.takeIf { PROFILE.matches(it) } ?: return PairParse.Invalid(PairProblem.INCOMPLETE)
            val fingerprint = q["f"]?.lowercase()?.takeIf { FINGERPRINT.matches(it) } ?: return PairParse.Invalid(PairProblem.INCOMPLETE)
            return PairParse.Ok(PairPayload(version, url, code, profile, fingerprint))
        }

        /** The query's parameters, decoded; one that can't be decoded is left out. */
        private fun query(raw: String): Map<String, String> = raw.split('&').mapNotNull { part ->
            runCatching { URLDecoder.decode(part.substringBefore('='), "UTF-8") to URLDecoder.decode(part.substringAfter('=', ""), "UTF-8") }.getOrNull()
        }.toMap()

        /** An http(s) URL with a host and nothing after its path, without a trailing slash; null otherwise. */
        fun bridgeUrl(s: String): String? {
            val t = s.trim()
            if (t.length > 300 || t.any { it.isWhitespace() }) return null
            val u = runCatching { URI(t) }.getOrNull() ?: return null
            if (u.scheme?.lowercase() !in setOf("http", "https")) return null
            if (u.host.isNullOrEmpty() || u.rawUserInfo != null || u.rawQuery != null || u.rawFragment != null) return null
            return t.trimEnd('/')
        }
    }
}

sealed interface PairParse {
    data class Ok(val payload: PairPayload) : PairParse
    data class Invalid(val problem: PairProblem) : PairParse
}

/** Why a scanned code can't be used, "target · base". */
enum class PairProblem(private val emoji: String, private val key: String) {
    NOT_PAIRING("🤔", "pairing.isntLaniPairingCode"),
    NEWER_VERSION("⬆️", "pairing.codeNeedsNewerLani"),
    INCOMPLETE("🧩", "pairing.codeIncompleteMakeNew"),
    BAD_URL("🔗", "pairing.addressCodeIsntValid");

    /** Read when shown, so it is in the pair of the moment (an enum's arguments are built once). */
    val message: String get() = "$emoji ${bi(key)}"
}

/** Who a phone paired with: kept next to the connection, shown in the connection sheet. */
data class PairedInfo(
    val profile: String,
    val learner: String,
    val fingerprint: String,
    val deviceId: String,
    val deviceName: String,
    val child: Boolean = false,
)

@Serializable
data class PairResponse(
    val v: Int = 0,
    val token: String,
    val device: PairDevice,
    val profile: String,
    val learner: PairLearner = PairLearner(),
    val fingerprint: String = "",
    @SerialName("public_key") val publicKey: String,
    val signature: String,
)

@Serializable
data class PairDevice(val id: String, val name: String)

@Serializable
data class PairLearner(val name: String = "", val target: String = "", val base: String = "", val child: Boolean = false)

sealed interface PairOutcome {
    data class Paired(val config: BridgeConfig, val info: PairedInfo) : PairOutcome
    data class Failed(val message: String) : PairOutcome
}

object Pairing {
    val WRONG_CODE: String get() = "❌ ${bi("pairing.codeWrongOrAlready")}"
    val EXPIRED: String get() = "⌛ ${bi("pairing.codeHasExpiredLasts")}"
    val TOO_MANY: String get() = "⏳ ${bi("pairing.tooManyWrongTries")}"
    val OLD_TUTOR: String get() = "🧰 ${bi("pairing.tutorCantPairBy")}"
    val UNREACHABLE: String get() = "📡 ${bi("pairing.cantReachTutorPhone")}"
    val NOT_THAT_TUTOR: String get() = "🔒 ${bi("pairing.differentTutorAnsweredThan")}"
    val GARBLED: String get() = "⚠️ ${bi("pairing.tutorsAnswerWasntUnderstood")}"

    fun failed(status: Int) = "⚠️ ${bi("pairing.pairingFailedHttpStatus", "status" to status)}"

    /** This phone holds another learner's village, chat and queue: pairing it with someone else needs a fresh start. */
    fun otherLearner(name: String?) = (name?.takeIf { it.isNotBlank() } ?: bi("pairing.anotherLearner")).let { who ->
        "🔀 ${bi("pairing.phoneBelongsWhoPair", "who" to who)}"
    }

    /** "✅ Povezano: Luka · Paired with Luka's tutor". */
    fun pairedBanner(info: PairedInfo) = info.learner.ifBlank { info.profile }.let { "✅ ${bi("pairing.pairedWithTutor", "it" to it)}" }

    /**
     * What the bridge signs (companion/bridge/src/pairing.ts, pairMessage): "lani-pair/1", the profile, the code and the
     * token; a bridge from before the rename signs "fluent-pair/1" ([domain] "fluent-pair").
     */
    fun message(profile: String, code: String, token: String, domain: String = "lani-pair") = "$domain/${PairPayload.VERSION}\n$profile\n$code\n$token"

    /** The domains a bridge signs with: today's, and the one from before the rename. */
    private val DOMAINS = listOf("lani-pair", "fluent-pair")

    /** The first 16 bytes of SHA-256 of the public key (SubjectPublicKeyInfo DER), hex. */
    fun fingerprintOf(spki: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(spki).take(16).joinToString("") { "%02x".format(it) }

    /** The answer comes from the bridge the QR code names: its key has the fingerprint, and it signed this code, token and profile. */
    fun verified(r: PairResponse, p: PairPayload): Boolean = runCatching {
        val spki = Base64.getDecoder().decode(r.publicKey)
        if (fingerprintOf(spki) != p.fingerprint || r.profile != p.profile) return false
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spki))
        val signature = Base64.getDecoder().decode(r.signature)
        DOMAINS.any { domain ->
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(key)
                update(message(r.profile, p.code, r.token, domain).toByteArray())
                verify(signature)
            }
        }
    }.getOrDefault(false)

    /**
     * Whether pairing with [p] keeps this phone with the learner it has: first pairing (nothing stored yet),
     * the same profile on the same bridge key or at the same address (a bridge whose key was made anew), or
     * (connected by a typed token) the same address.
     */
    fun sameLearner(currentUrl: String?, current: PairedInfo?, p: PairPayload): Boolean = when {
        currentUrl == null -> true
        current != null -> current.profile == p.profile && (current.fingerprint == p.fingerprint || origin(currentUrl) == origin(p.url))
        else -> origin(currentUrl) == origin(p.url)
    }

    /** "https://Node.ts.net/" → "https://node.ts.net:443". */
    fun origin(url: String): String? = runCatching {
        val u = URI(url.trim())
        val scheme = u.scheme.lowercase()
        val port = if (u.port != -1) u.port else if (scheme == "https") 443 else 80
        "$scheme://${u.host.lowercase()}:$port"
    }.getOrNull()
}

/**
 * The pairing call, without the camera: POST /pair with [post] (the real one is [PairingApi.post]), then the
 * checks. Every failure is a message for the learner.
 */
class PairFlow(private val post: suspend (url: String, body: String) -> Pair<Int, String>) {
    suspend fun pair(p: PairPayload, deviceName: String): PairOutcome {
        val body = buildJsonObject { put("code", p.code); put("device", deviceName.take(60)) }.toString()
        val (status, raw) = try {
            post(p.url + "/pair", body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return PairOutcome.Failed(Pairing.UNREACHABLE)
        } catch (e: IllegalArgumentException) { // an address OkHttp won't take
            return PairOutcome.Failed(PairProblem.BAD_URL.message)
        }
        return when (status) {
            200 -> {
                val r = runCatching { json.decodeFromString(PairResponse.serializer(), raw) }.getOrNull()
                    ?: return PairOutcome.Failed(Pairing.GARBLED)
                if (!r.token.startsWith("fd_")) return PairOutcome.Failed(Pairing.GARBLED)
                if (!Pairing.verified(r, p)) return PairOutcome.Failed(Pairing.NOT_THAT_TUTOR)
                PairOutcome.Paired(
                    BridgeConfig(p.url, r.token),
                    PairedInfo(r.profile, r.learner.name, p.fingerprint, r.device.id, r.device.name, r.learner.child),
                )
            }
            403 -> PairOutcome.Failed(Pairing.WRONG_CODE)
            410 -> PairOutcome.Failed(Pairing.EXPIRED)
            429 -> PairOutcome.Failed(Pairing.TOO_MANY)
            401, 404, 405 -> PairOutcome.Failed(Pairing.OLD_TUTOR) // an older bridge: /pair isn't a route, so it asks for a token
            else -> PairOutcome.Failed(Pairing.failed(status))
        }
    }
}

/** POST /pair over the network: no token, the code is the credential. */
object PairingApi {
    private val http = Http.client(connectSeconds = 10, readSeconds = 20)

    suspend fun post(url: String, body: String): Pair<Int, String> =
        http.exchange(Request.Builder().url(url).post(body.toRequestBody(Http.jsonType)).build())
}
