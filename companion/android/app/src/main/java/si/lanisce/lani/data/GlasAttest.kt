package si.lanisce.lani.data

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.UUID

/**
 * The voice service's proof that this is our app on a real phone (docs/plans/05-built/attest.md; the voice service's README,
 * "Attestation"): before the audio trial starts, the app asks glas for a challenge, makes an EC P-256 key in the phone's secure
 * hardware with the challenge attached (Android key attestation: a certificate chain from the phone to Google's roots that
 * names our app, its signer and the phone's boot state), and enrolls it, the request signed by the key itself. From then on
 * every request on the voice service's app routes is signed by that key ([GlasAuth]). Where the phone can't attest (an
 * emulator, an unlocked or old phone, glas refusing), the app goes on with the library only and the phone's voice for the
 * rest, silently, as when the trial's budget is used up. Nothing here runs without a voice service (lani.glasUrl empty).
 *
 * The key is AndroidKeyStore's own, made for this and never exportable (sync/P256.kt and phone/HostCrypto.kt make exportable
 * keys for other things: not these). In the TEE: StrongBox is slower and its resources are few, and glas accepts either.
 */
@Serializable
data class AttestState(
    /** The key's alias in AndroidKeyStore, and its id (SHA-256 of its public key, hex) once it is enrolled. */
    val alias: String = "",
    val keyId: String = "",
    /** The install as the voice service knows it: its first key's id. */
    val install: String = "",
    /** When the enrollment ends and when to make the next key, ms. */
    val expiresAt: Long = 0,
    val renewAfter: Long = 0,
    /** [NONE], [ENROLLED], [REFUSED] (final: this phone can't), as the last try ended. */
    val status: String = NONE,
    /** The voice service's reason code for a refusal (`boot_unverified`, `root_unknown`…), or a transient failure's. */
    val reason: String = "",
    /** Tries that failed for a passing reason in a row, and when the next may be. */
    val failures: Int = 0,
    val nextTry: Long = 0,
    /** The voice service asked for a new key (X-Glas-Renew). */
    val renew: Boolean = false,
    /**
     * A key sent to be enrolled whose answer never came (a dropped connection, a proxy's 502): the service may have enrolled it
     * (and ended the old key, for a renewal). Kept until a definite answer; the next try asks the service about it first.
     */
    val pendingAlias: String = "",
    val pendingKeyId: String = "",
) {
    /** Enrolled, and the key still counts. */
    fun attested(now: Long): Boolean = status == ENROLLED && keyId.isNotEmpty() && now < expiresAt

    /** What to do now: enroll a first key, a new key for this install, or nothing. */
    fun due(now: Long): Due? = when {
        now < nextTry -> null
        status == ENROLLED && (renew || now >= renewAfter) -> Due.RENEW
        status == ENROLLED -> null
        // refused, or never enrolled: once its wait is over (a refusal's is a month)
        else -> Due.ENROLL
    }

    /** It failed for a passing reason (no network, keys not provisioned yet, the service busy): tried again later, more slowly. */
    fun failedForNow(reason: String, now: Long): AttestState {
        val n = failures + 1
        val wait = minOf(BACKOFF_MS shl minOf(n - 1, 10), MAX_BACKOFF_MS)
        return copy(reason = reason, failures = n, nextTry = now + wait)
    }

    /** Refused for good: this phone hears the library only, asked again after [retry]. A renewal refused keeps nothing of the old key. */
    fun refused(reason: String, now: Long, retry: Long = REFUSED_RETRY_MS): AttestState = AttestState(status = REFUSED, reason = reason, nextTry = now + retry)

    fun encode(): String = json.encodeToString(serializer(), this)

    enum class Due { ENROLL, RENEW }

    companion object {
        const val NONE = "none"
        const val ENROLLED = "enrolled"
        const val REFUSED = "refused"
        const val BACKOFF_MS = 60_000L
        const val MAX_BACKOFF_MS = 12 * 3_600_000L
        /** A refusal is asked again after this long: an update of the app or of the service may change it. */
        const val REFUSED_RETRY_MS = 30L * 86_400_000L

        fun decode(s: String?): AttestState = s?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: AttestState()
    }
}

/** The canonical request every attested request signs (glas's src/attest/request.ts): the same for every kind of key. */
object GlasRequest {
    const val PROTOCOL = "glas-request/1"
    const val KEY = "X-Glas-Key"
    const val TIME = "X-Glas-Time"
    const val NONCE = "X-Glas-Nonce"
    const val SIGNATURE = "X-Glas-Signature"
    const val PREV_KEY = "X-Glas-Prev-Key"
    const val PREV_SIGNATURE = "X-Glas-Prev-Signature"
    const val RENEW = "X-Glas-Renew"
    const val IDEMPOTENCY = "Idempotency-Key"

    fun canonical(audience: String, keyId: String, method: String, pathWithQuery: String, time: Long, nonce: String, bodySha256: String): String =
        listOf(PROTOCOL, audience, keyId, method.uppercase(), pathWithQuery, time.toString(), nonce, bodySha256).joinToString("\n")

    /** The audience: the voice service's host, with its port when it isn't the scheme's (as glas reads GLAS_PUBLIC_URL). */
    fun audience(glas: String): String {
        val u = glas.toHttpUrlOrNull() ?: return ""
        return if (u.port == HttpUrl.defaultPort(u.scheme)) u.host else "${u.host}:${u.port}"
    }

    fun pathWithQuery(url: HttpUrl): String = url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")

    fun sha256hex(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private val random = SecureRandom()

    fun nonce(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(16).also(random::nextBytes))

    /** The X-Glas-* headers of a request signed by [key] at [time]. */
    fun headers(audience: String, key: AttestKey, method: String, pathWithQuery: String, body: ByteArray, time: Long, nonce: String = nonce()): Map<String, String> {
        val message = canonical(audience, key.keyId, method, pathWithQuery, time, nonce, sha256hex(body))
        return mapOf(KEY to key.keyId, TIME to time.toString(), NONCE to nonce, SIGNATURE to Base64.getEncoder().encodeToString(key.sign(message.toByteArray())))
    }
}

/** A key that signs requests: its id, and ECDSA P-256 over SHA-256 in DER, as glas checks. */
interface AttestKey {
    val keyId: String
    fun sign(message: ByteArray): ByteArray
}

/** Where attested keys are made and kept: AndroidKeyStore on a phone ([AndroidAttestKeys]); a test has its own. */
interface AttestKeys {
    /** A new key for [challenge] under [alias]: its certificate chain, the key's own first (DER). */
    fun create(alias: String, challenge: ByteArray): List<ByteArray>
    /** The key under [alias], or null when it is gone (a backup restored onto a new phone has none). */
    fun key(alias: String): AttestKey?
    fun delete(alias: String)
}

/** A key generation that failed for a passing reason: Android's remote keys not provisioned yet, the keystore busy. */
class TransientAttestFailure(message: String) : Exception(message)

/** AndroidKeyStore: a non-exportable EC P-256 key in the TEE, for signing with SHA-256, with the challenge attached. */
object AndroidAttestKeys : AttestKeys {
    private const val STORE = "AndroidKeyStore"

    override fun create(alias: String, challenge: ByteArray): List<ByteArray> {
        try {
            val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAttestationChallenge(challenge)
                .build()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, STORE).apply { initialize(spec) }.generateKeyPair()
        } catch (e: Exception) {
            delete(alias)
            if (transient(e)) throw TransientAttestFailure(e.javaClass.simpleName)
            throw e
        }
        val chain = KeyStore.getInstance(STORE).apply { load(null) }.getCertificateChain(alias) ?: throw IllegalStateException("no chain")
        return chain.map { it.encoded }
    }

    /**
     * Whether [e] passes: ERROR_ATTESTATION_KEYS_UNAVAILABLE while remote keys are being provisioned (Android 12+), or a
     * failure the keystore says is transient (Android 13+).
     */
    private fun transient(e: Throwable): Boolean {
        var c: Throwable? = e
        while (c != null) {
            if (Build.VERSION.SDK_INT >= 33 && c is android.security.KeyStoreException) {
                if (c.isTransientFailure || c.numericErrorCode == android.security.KeyStoreException.ERROR_ATTESTATION_KEYS_UNAVAILABLE) return true
            }
            if (c.message?.contains("ATTESTATION_KEYS_UNAVAILABLE", ignoreCase = true) == true) return true
            c = c.cause
        }
        return false
    }

    override fun key(alias: String): AttestKey? {
        val ks = KeyStore.getInstance(STORE).apply { load(null) }
        val private = ks.getKey(alias, null) as? PrivateKey ?: return null
        val public = ks.getCertificate(alias)?.publicKey ?: return null
        val id = GlasRequest.sha256hex(public.encoded)
        return object : AttestKey {
            override val keyId = id
            override fun sign(message: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
                initSign(private)
                update(message)
                sign()
            }
        }
    }

    override fun delete(alias: String) {
        runCatching { KeyStore.getInstance(STORE).apply { load(null) }.deleteEntry(alias) }
    }
}

/** What an enrollment came to: enrolled, refused for good (with glas's reason code), or not now. */
sealed interface Enrolled {
    data class Yes(val state: AttestState, val level: String) : Enrolled
    /** [root]: for a debug build's log, the chain's root as "<subject> <pin>" (shared by every phone of its kind), or null. */
    data class Refused(val reason: String, val root: String? = null) : Enrolled
    data class NotNow(val reason: String) : Enrolled
}

/**
 * Enrollment with the voice service ([glas]): a challenge, a new key for it, the chain sent signed by the key (and by the
 * old one, renewing [state]'s). [clock]: the server's time as the phone knows it ([GlasAuth.now]).
 */
class GlasEnroll(
    private val glas: String,
    private val keys: AttestKeys = AndroidAttestKeys,
    private val clock: () -> Long = GlasAuth::now,
    /** Keeps a key that is about to be sent ([AttestState.pendingAlias]); ("", "") forgets it. Before the request goes. */
    private val pending: (alias: String, keyId: String) -> Unit = { _, _ -> },
) {
    /**
     * Never sent twice by OkHttp itself on a broken connection: a copy of an enrollment the service took would come back as
     * a refusal (its challenge used) and the key would be dropped. A lost answer is an IOException here, the key kept.
     */
    private val http = Http.client(connectSeconds = 5, readSeconds = 20).newBuilder().retryOnConnectionFailure(false).build()
    private val base = glas.trimEnd('/')

    fun enroll(state: AttestState, renew: Boolean): Enrolled {
        // a key from a try whose answer never came: the service is asked about it before anything new is made
        if (state.pendingAlias.isNotEmpty()) {
            val kept = keys.key(state.pendingAlias)?.takeIf { it.keyId == state.pendingKeyId }
            val known = kept?.let { mine(it) }
            when {
                known is Mine.Enrolled -> {
                    // it was enrolled: taken on, with the service's own dates; the old key ended with it
                    if (state.alias.isNotEmpty() && state.alias != state.pendingAlias) keys.delete(state.alias)
                    return Enrolled.Yes(known.state.copy(alias = state.pendingAlias), known.level)
                }
                known is Mine.NoAnswer -> return Enrolled.NotNow("pending:${known.why}")
                else -> {
                    // never enrolled, ended, or gone from the keystore: forgotten, and the try goes on as it would have
                    keys.delete(state.pendingAlias)
                    pending("", "")
                }
            }
        }
        val (code, raw) = call(Request.Builder().url("$base/v1/app/attest/challenge").post(ByteArray(0).toRequestBody(Http.jsonType)).build())
        // a service that attests no app at all: its library only, asked again the next day
        if (code == 503 && errorOf(raw, "reason") == NOT_CONFIGURED) return Enrolled.Refused(NOT_CONFIGURED)
        if (code != 200) return Enrolled.NotNow(errorOf(raw)?.let { "challenge:$it" } ?: "challenge:$code")
        val challengeText = (json.parseToJsonElement(raw) as JsonObject)["challenge"]?.jsonPrimitive?.contentOrNull ?: return Enrolled.NotNow("challenge")
        val challenge = Base64.getUrlDecoder().decode(challengeText)
        val alias = "glas-attest-${UUID.randomUUID()}"
        val chain = try {
            keys.create(alias, challenge)
        } catch (e: TransientAttestFailure) {
            return Enrolled.NotNow("keystore:${e.message}")
        } catch (e: Exception) {
            // no attestation on this phone at all
            return Enrolled.Refused("keystore")
        }
        val key = keys.key(alias) ?: return Enrolled.NotNow("keystore")
        val previous = if (renew) keys.key(state.alias)?.takeIf { it.keyId == state.keyId } else null
        val body = buildJsonObject {
            put("kind", "android-key")
            put("challenge", challengeText)
            putJsonArray("chain") { chain.forEach { add(kotlinx.serialization.json.JsonPrimitive(Base64.getEncoder().encodeToString(it))) } }
            if (previous != null) put("previous", previous.keyId)
        }.toString().toByteArray()
        val url = "$base/v1/app/attest".toHttpUrlOrNull()!!
        val audience = GlasRequest.audience(glas)
        val time = clock()
        val nonce = GlasRequest.nonce()
        val h = GlasRequest.headers(audience, key, "POST", GlasRequest.pathWithQuery(url), body, time, nonce).toMutableMap()
        if (previous != null) {
            // the old key signs the same request: the canonical string names the new key
            val message = GlasRequest.canonical(audience, key.keyId, "POST", GlasRequest.pathWithQuery(url), time, nonce, GlasRequest.sha256hex(body))
            h[GlasRequest.PREV_KEY] = previous.keyId
            h[GlasRequest.PREV_SIGNATURE] = Base64.getEncoder().encodeToString(previous.sign(message.toByteArray()))
        }
        // kept before it goes: if the answer is lost, the next try finds out whether the service took it
        pending(alias, key.keyId)
        val req = Request.Builder().url(url).post(body.toRequestBody(Http.jsonType)).apply { h.forEach { (k, v) -> header(k, v) } }.build()
        val (status, answer) = call(req)
        val o = runCatching { json.parseToJsonElement(answer) as JsonObject }.getOrNull()
        fun field(k: String) = o?.get(k)?.jsonPrimitive?.contentOrNull
        val code2 = errorOf(answer)
        return when {
            status == 200 && field("status") == "enrolled" -> {
                if (previous != null) keys.delete(state.alias)
                Enrolled.Yes(enrolled(alias, key.keyId, o!!), field("level") ?: "")
            }
            // a definite answer (a 4xx with its code, not 429): the key goes
            status in 400..499 && status != 429 && code2 != null -> {
                keys.delete(alias)
                pending("", "")
                val reason = errorOf(answer, "reason") ?: code2
                when {
                    // the old key may not vouch (it vouched already, and that answer was lost before this version kept keys;
                    // or it ended): this phone is not refused, it starts again as a new install, once
                    status == 403 && previous != null && (reason == "previous_renewed" || reason == "previous_invalid") -> {
                        keys.delete(state.alias)
                        enroll(AttestState(), renew = false)
                    }
                    // 403 attestation_refused is this phone's answer; anything else (a stale clock, a challenge gone) passes
                    status == 403 -> Enrolled.Refused(reason, rootOf(chain))
                    else -> Enrolled.NotNow(reason)
                }
            }
            // no answer, a 5xx, a 429: the service may have taken it; the key stays, asked about next time
            else -> Enrolled.NotNow(code2?.let { "pending:$it" } ?: "pending:http:$status")
        }
    }

    /** What the service says of [key]: its enrollment ([Mine.Enrolled]), none ([Mine.Unknown]), ended, or no answer. */
    sealed interface Mine {
        data class Enrolled(val state: AttestState, val level: String) : Mine
        data object Unknown : Mine
        data object Ended : Mine
        data class NoAnswer(val why: String) : Mine
    }

    /** GET /v1/app/attest signed by [key]: the key's own enrollment, as the service answered (or would have) its enrollment. */
    fun mine(key: AttestKey): Mine {
        val url = "$base/v1/app/attest".toHttpUrlOrNull()!!
        val h = GlasRequest.headers(GlasRequest.audience(glas), key, "GET", GlasRequest.pathWithQuery(url), ByteArray(0), clock())
        val (status, raw) = call(Request.Builder().url(url).apply { h.forEach { (k, v) -> header(k, v) } }.build())
        val o = runCatching { json.parseToJsonElement(raw) as JsonObject }.getOrNull()
        return when {
            status == 200 && o?.get("status")?.jsonPrimitive?.contentOrNull == "enrolled" -> Mine.Enrolled(enrolled("", key.keyId, o), o["level"]?.jsonPrimitive?.contentOrNull ?: "")
            status == 200 -> Mine.Ended
            status == 401 && errorOf(raw) == "unknown_key" -> Mine.Unknown
            else -> Mine.NoAnswer(errorOf(raw) ?: "http:$status")
        }
    }

    /** The enrolled state an answer [o] says, for the key under [alias]. */
    private fun enrolled(alias: String, keyId: String, o: JsonObject): AttestState {
        fun field(k: String) = o[k]?.jsonPrimitive?.contentOrNull
        return AttestState(
            alias = alias,
            keyId = field("key_id") ?: keyId,
            install = field("install") ?: keyId,
            expiresAt = field("expires_at")?.let { java.time.Instant.parse(it).toEpochMilli() } ?: 0,
            renewAfter = field("renew_after")?.let { java.time.Instant.parse(it).toEpochMilli() } ?: 0,
            status = AttestState.ENROLLED,
        )
    }

    private fun call(req: Request): Pair<Int, String> = try {
        http.newCall(req).execute().use { r ->
            GlasAuth.heard(r)
            r.code to (r.body?.string().orEmpty())
        }
    } catch (e: java.io.IOException) {
        -1 to ""
    }

    companion object {
        /** The service attests no app (neither GLAS_APP_SIGNERS nor GLAS_IOS_APP_IDS set). */
        const val NOT_CONFIGURED = "not_configured"

        /**
         * The chain's root, "<subject> <SHA-256 of its public key, hex>": the pin a root is known by on the service (its
         * `glas attest-pin`). Every phone of its kind has the same; for a debug build's log only.
         */
        fun rootOf(chain: List<ByteArray>): String? = runCatching {
            val c = java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(chain.last().inputStream()) as java.security.cert.X509Certificate
            "${c.subjectX500Principal.name} ${GlasRequest.sha256hex(c.publicKey.encoded)}"
        }.getOrNull()
    }

    private fun errorOf(raw: String, field: String = "code"): String? = runCatching {
        ((json.parseToJsonElement(raw) as JsonObject)["error"] as JsonObject)[field]?.jsonPrimitive?.contentOrNull
    }.getOrNull()
}

/**
 * Signs the app's requests to the voice service (in [Http]'s client, for everything that asks it: the app, the day's
 * prefetch, the car's library). For a request to [glas]'s app routes or its trial, while [key] counts: the bearer token goes,
 * the X-Glas-* headers come, per attempt (a retried connection signs anew, so it is never a replay), with an Idempotency-Key
 * per call for a POST (a retry is charged nothing more). Every answer's X-Glas-Time corrects the phone's clock for the next
 * signature, and a 401 "stale" is tried once more with it; X-Glas-Renew asks for a new key.
 */
object GlasAuth {
    /** The voice service's base URL and the key that signs for it now; null: nothing is signed. */
    @Volatile var glas: String = ""
    @Volatile var key: AttestKey? = null
    /** The server's time minus the phone's, as the last answer said. */
    @Volatile var offset: Long = 0
    /** The voice service asked for a new key: AudioPlan takes it. */
    @Volatile var onRenew: () -> Unit = {}

    fun now(): Long = System.currentTimeMillis() + offset

    /** A request of the voice service's app routes or its trial, not one with a key of its own (QA's, the add-on's). */
    private fun ours(req: Request): Boolean {
        if (req.header("Authorization")?.startsWith("Bearer glas_") == true) return false
        val url = req.url
        val base = glas.trimEnd('/').toHttpUrlOrNull() ?: return false
        if (url.host != base.host || url.port != base.port || url.scheme != base.scheme) return false
        val p = url.encodedPath
        return (p.startsWith("/v1/app/") && !p.startsWith("/v1/app/attest")) || p == "/v1/trial"
    }

    /** Each call to the voice service's app routes: its Idempotency-Key, and one more try when the clock was off. */
    val calls = Interceptor { chain ->
        val req = chain.request()
        if (key == null || !ours(req)) return@Interceptor chain.proceed(req)
        val withKey = if (req.method == "POST" && req.header(GlasRequest.IDEMPOTENCY) == null) req.newBuilder().header(GlasRequest.IDEMPOTENCY, UUID.randomUUID().toString()).build() else req
        val r = chain.proceed(withKey)
        if (r.code == 401 && r.peekBody(1024).string().contains("\"stale\"")) {
            r.close()
            return@Interceptor chain.proceed(withKey)
        }
        r
    }

    /** Each attempt on the wire: signed now. */
    val attempts = Interceptor { chain ->
        val req = chain.request()
        val k = key
        if (k == null || !ours(req)) return@Interceptor chain.proceed(req)
        val body = req.body?.let { b -> Buffer().also { b.writeTo(it) }.readByteArray() } ?: ByteArray(0)
        val signed = req.newBuilder().removeHeader("Authorization").apply {
            GlasRequest.headers(GlasRequest.audience(glas), k, req.method, GlasRequest.pathWithQuery(req.url), body, now()).forEach { (h, v) -> header(h, v) }
        }.build()
        val r = chain.proceed(signed)
        heard(r)
        r
    }

    /** What every answer of the voice service says: its time, and whether a new key is due. */
    fun heard(r: Response) {
        r.header(GlasRequest.TIME)?.toLongOrNull()?.let { offset = it - System.currentTimeMillis() }
        if (r.header(GlasRequest.RENEW) == "1") onRenew()
    }

}
