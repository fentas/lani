package si.lanisce.lani.tutor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Closeable
import java.io.IOException
import java.math.BigInteger
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * "Sign in with ChatGPT" for the tutor (docs/plans/05-built/providers.md), as OpenAI's guide for open-source apps has it
 * (developers.openai.com/siwc/token-sharing-open-source, checked 2026-10-18): OAuth 2.0 with PKCE and OpenID Connect
 * against auth.openai.com, a client registered at the first sign-in (no client secret), the learner's ChatGPT plan paying
 * for the Responses API's calls.
 *
 * - **The host:** a stable `ext_agent_host_id` (`urn:uuid:…`) per installation, made before the first sign-in and kept
 *   through sign-outs.
 * - **The browser:** the system browser opens the authorize URL; it comes back to a loopback listener on 127.0.0.1
 *   ([Loopback]: the guide allows "an HTTP loopback callback on 127.0.0.1" only, the port free to vary, the path fixed),
 *   whose page then sends the browser on to the app (`lani://tutor/signed-in`, an intent filter of MainActivity).
 * - **The tokens:** the issued `client_id` (`oaiapp_…`) is kept at once; the code is exchanged with the PKCE verifier; the
 *   ID token is checked (RS256 against OpenAI's JWKS, issuer, audience, expiry, nonce); the session ([ChatGPTSession])
 *   is kept encrypted in a [KeyVault]. Access tokens last an hour, refresh tokens 30 days and rotate: each refresh's
 *   replacement is kept before it is used, refreshes are serialized ([accessToken]).
 * - **Sign out:** the refresh token revoked (the discovery's revocation_endpoint), the tokens cleared, the client and
 *   the host kept for a later sign-in.
 *
 * Tokens go to auth.openai.com only (the access token to api.openai.com only: [OpenAIGuard]), never into a log, the
 * export or a URL.
 */
object ChatGPTOAuth {
    const val ISSUER = "https://auth.openai.com"
    const val AUTHORIZE = "https://auth.openai.com/api/accounts/authorize"
    const val TOKEN = "https://auth.openai.com/api/accounts/oauth/token"
    /** From https://auth.openai.com/.well-known/openid-configuration (fetched 2026-10-18), as the jwks_uri. */
    const val REVOKE = "https://auth.openai.com/api/accounts/oauth/revoke"
    const val JWKS = "https://auth.openai.com/.well-known/jwks.json"
    const val RESOURCE = "https://api.openai.com/v1"
    /** The identity scopes and the plan-usage ones ("offline_access resource.invoke chatgpt.tokens.use.direct"). */
    const val SCOPES = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
    const val PLAN_SCOPE = "chatgpt.tokens.use.direct"
    /** The first registration's client id: an entry point, never kept. */
    const val DYNAMIC_CLIENT = "dynamic_agent_client"
    const val CALLBACK_PATH = "/auth/callback"
    /** Where the callback page sends the browser on: back to the app. */
    const val BACK = "lani://tutor/signed-in"
    /** ChatGPT Settings → Usage: where the learner sees and limits what the app uses of their plan. */
    const val MANAGE_USAGE = "https://chatgpt.com/settings/usage"

    /** The refresh errors after which the tokens can't be used again ("Clear unusable tokens and repeat OAuth"). */
    val UNUSABLE = setOf(
        "invalid_grant", "invalid_refresh_token", "token_expired", "refresh_token_expired", "refresh_token_invalidated",
        "refresh_token_reused", "invalid_client",
    )
}

/**
 * A ChatGPT sign-in as kept (encrypted, in a [KeyVault]): the installation's [hostId], the issued [clientId] and the
 * account ([subject], [email]) kept through sign-outs; the tokens ([accessToken] till [expiresAt], the rotating
 * [refreshToken], the [idToken] for a later `id_token_hint`), the granted [scopes], [earliestRefreshAt].
 */
@Serializable
data class ChatGPTSession(
    val hostId: String,
    val clientId: String? = null,
    val subject: String? = null,
    val email: String? = null,
    val idToken: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAt: Long = 0,
    val earliestRefreshAt: Long = 0,
    val scopes: List<String> = emptyList(),
    val savedAt: Long = 0,
) {
    /** Signed in: tokens kept. */
    val connected: Boolean get() = accessToken != null && refreshToken != null

    /** The learner let the app use their ChatGPT plan (the scope was granted): calls may be made. */
    val planUsage: Boolean get() = connected && ChatGPTOAuth.PLAN_SCOPE in scopes

    /** Signed out: the tokens gone, the client, the host and the account kept. */
    fun signedOut(now: Long): ChatGPTSession = copy(accessToken = null, refreshToken = null, idToken = null, expiresAt = 0, earliestRefreshAt = 0, scopes = emptyList(), savedAt = now)

    override fun toString(): String = "ChatGPTSession(host, client=${clientId != null}, connected=$connected, plan=$planUsage)"
}

/** Why a sign-in or a refresh failed: OpenAI's code ([code]: access_denied, invalid_grant, …) and what to tell the learner. */
class ChatGPTError(val code: String, message: String) : Exception(message)

/**
 * The ChatGPT sign-in's state machine (see [ChatGPTOAuth]). [vault] keeps the [ChatGPTSession] as JSON; [http] reaches
 * auth.openai.com only; [port] the loopback listener's (0: any free one).
 */
class ChatGPTAuth(
    private val vault: KeyVault,
    private val http: OkHttpClient = defaultClient,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
    private val appName: String = "Lani",
) {
    private val lock = Mutex()
    private var keys: JsonObject? = null

    /** The kept session, or null (never signed in, or not decryptable any more). */
    suspend fun session(): ChatGPTSession? = vault.load()?.let { runCatching { tutorJson.decodeFromString(ChatGPTSession.serializer(), it) }.getOrNull() }

    private suspend fun save(s: ChatGPTSession) = vault.save(tutorJson.encodeToString(ChatGPTSession.serializer(), s))

    /** The installation's session, made with its host id before the first sign-in. */
    private suspend fun host(): ChatGPTSession = session() ?: ChatGPTSession(hostId = "urn:uuid:" + UUID.randomUUID(), savedAt = clock()).also { save(it) }

    private fun randomValue(): String = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    /**
     * Starts a sign-in: the loopback listener on [port] (0: a free one), the authorize URL with a fresh state, nonce and
     * PKCE verifier. Open [SignIn.url] in the browser, then [SignIn.await] it.
     */
    suspend fun begin(port: Int = 0): SignIn {
        val s = host()
        val state = randomValue()
        val nonce = randomValue()
        val verifier = randomValue()
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val listener = withContext(Dispatchers.IO) { Loopback(port, state, s.clientId) }
        val url = ChatGPTOAuth.AUTHORIZE.toHttpUrl().newBuilder()
            .addQueryParameter("client_id", s.clientId ?: ChatGPTOAuth.DYNAMIC_CLIENT)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("redirect_uri", listener.redirectUri)
            .addQueryParameter("scope", ChatGPTOAuth.SCOPES)
            .addQueryParameter("resource", ChatGPTOAuth.RESOURCE)
            .addQueryParameter("state", state)
            .addQueryParameter("nonce", nonce)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("code_challenge", challenge)
            .addQueryParameter("ext_agent_host_id", s.hostId)
            .apply {
                // the app's name on the first registration only; the account's email to pre-select it on a later one
                // (no id_token_hint: a token never goes into a URL)
                if (s.clientId == null) addQueryParameter("agent_name_hint", appName)
                s.email?.let { addQueryParameter("login_hint", it) }
            }
            .build().toString()
        return SignIn(url, listener) { cb -> complete(cb, verifier, nonce, listener.redirectUri) }
    }

    /** A sign-in in progress: [url] to open in the browser; [await] the session it made (throws [ChatGPTError]). */
    class SignIn internal constructor(val url: String, private val listener: Loopback, private val finish: suspend (Loopback.Callback) -> ChatGPTSession) {
        val redirectUri: String get() = listener.redirectUri

        suspend fun await(timeoutMs: Long = 10 * 60_000L): ChatGPTSession = try {
            val cb = try {
                withTimeout(timeoutMs) { listener.result.await() }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                throw ChatGPTError("timeout", "the sign-in took too long: try again")
            }
            finish(cb)
        } finally {
            listener.close()
        }

        fun cancel() = listener.close()
    }

    private suspend fun complete(cb: Loopback.Callback, verifier: String, nonce: String, redirectUri: String): ChatGPTSession = lock.withLock {
        val before = host()
        // the issued client is kept at once: a code that fails later doesn't register another app at the next attempt
        if (before.clientId != cb.clientId) save(before.copy(clientId = cb.clientId, savedAt = clock()))
        val data = token(
            "grant_type" to "authorization_code", "client_id" to cb.clientId, "code" to cb.code, "code_verifier" to verifier,
            "redirect_uri" to redirectUri, "resource" to ChatGPTOAuth.RESOURCE,
        )
        val idToken = data.str("id_token") ?: throw ChatGPTError("invalid_id_token", "ChatGPT returned no identity: try again")
        val claims = verify(idToken, cb.clientId, nonce)
        if (before.subject != null && claims.sub != before.subject) {
            throw ChatGPTError("account_mismatch", "this is another ChatGPT account than the one kept: sign out first to switch")
        }
        val s = tokens(before.copy(clientId = cb.clientId), data, requireRefresh = true).copy(idToken = idToken, subject = claims.sub, email = claims.email ?: before.email)
        save(s)
        s
    }

    /** The session with [data]'s tokens (a token endpoint's answer): rotation, expiry, scopes (kept when omitted). */
    private fun tokens(s: ChatGPTSession, data: JsonObject, requireRefresh: Boolean): ChatGPTSession {
        val access = data.str("access_token") ?: throw ChatGPTError("invalid_token_response", "ChatGPT returned no access token: sign in again")
        if (data.str("token_type")?.lowercase() != "bearer") throw ChatGPTError("invalid_token_response", "ChatGPT returned an unknown token type: sign in again")
        val expiresIn = (data["expires_in"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 } ?: throw ChatGPTError("invalid_token_response", "ChatGPT returned no expiry: sign in again")
        val scopes = data.str("scope")?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: s.scopes
        val refresh = data.str("refresh_token")
        if (refresh == null && (requireRefresh || "offline_access" in scopes)) throw ChatGPTError("invalid_token_response", "ChatGPT returned no refresh token: sign in again")
        val now = clock()
        return s.copy(
            accessToken = access, refreshToken = refresh ?: s.refreshToken, expiresAt = now + expiresIn * 1000, scopes = scopes,
            earliestRefreshAt = earliest(data["earliest_refresh_at"]), savedAt = now,
        )
    }

    /** `earliest_refresh_at` as epoch ms (Unix seconds or an ISO instant); 0 when absent. */
    private fun earliest(e: kotlinx.serialization.json.JsonElement?): Long {
        val p = e as? JsonPrimitive ?: return 0
        if (p.isString) return runCatching { Instant.parse(p.content).toEpochMilli() }.getOrDefault(0)
        return p.doubleOrNull?.let { (it * 1000).toLong() } ?: 0
    }

    /**
     * An access token for a call: the kept one while it has a minute left, else a refreshed one (the replacement refresh
     * token kept before the access token is used; one refresh at a time). [force]: refresh now (a 401). Null when not
     * signed in. Throws [ApiError.Auth] when the sign-in can't be renewed (signed in again then), [ApiError.Network] or
     * [ApiError.Overloaded] for a passing failure (nothing cleared).
     */
    suspend fun accessToken(force: Boolean = false): String? = lock.withLock {
        val s = session()?.takeIf { it.connected } ?: return@withLock null
        val now = clock()
        if (!force && s.expiresAt - 60_000 > now) return@withLock s.accessToken
        if (s.earliestRefreshAt > now) {
            if (s.expiresAt > now) return@withLock s.accessToken
            throw ApiError.Overloaded(0, "the ChatGPT sign-in can't be renewed yet")
        }
        val data = try {
            token("grant_type" to "refresh_token", "client_id" to s.clientId.orEmpty(), "refresh_token" to s.refreshToken.orEmpty(), "resource" to ChatGPTOAuth.RESOURCE)
        } catch (e: ChatGPTError) {
            if (e.code in ChatGPTOAuth.UNUSABLE) {
                save(s.signedOut(clock()))
                throw ApiError.Auth("the ChatGPT sign-in ended (${e.code}): sign in again")
            }
            throw ApiError.Overloaded(0, "the ChatGPT sign-in couldn't be renewed (${e.code})")
        }
        val next = try {
            tokens(s, data, requireRefresh = true)
        } catch (e: ChatGPTError) {
            throw ApiError.Overloaded(0, e.message.orEmpty())
        }
        // kept before anything else can fail: the old refresh token is spent
        save(next)
        data.str("id_token")?.let { id ->
            val claims = try {
                verify(id, s.clientId.orEmpty(), null)
            } catch (e: ChatGPTError) {
                // the keys unreachable: the tokens came from the token endpoint itself; checked at the next refresh
                if (e.code == "keys_unavailable") null else {
                    save(next.signedOut(clock()))
                    throw ApiError.Auth("the renewed ChatGPT identity didn't verify: sign in again")
                }
            }
            if (claims != null && s.subject != null && claims.sub != s.subject) {
                save(next.signedOut(clock()))
                throw ApiError.Auth("the renewed ChatGPT identity is another account: sign in again")
            }
            save(next.copy(idToken = id))
        }
        next.accessToken
    }

    /**
     * Signs out: the refresh token revoked (form POST to the revocation endpoint: token, token_type_hint, client_id; an
     * empty 200 is success, a 5xx or the network tried once more), then the tokens cleared. Null when the revocation was
     * confirmed, else what to tell the learner (the tokens are gone from the phone either way).
     */
    suspend fun signOut(): String? = lock.withLock {
        val s = session() ?: return@withLock null
        val token = s.refreshToken
        var confirmed = token == null
        if (token != null) {
            for (attempt in 0..1) {
                val code = try {
                    post(ChatGPTOAuth.REVOKE, "token" to token, "token_type_hint" to "refresh_token", "client_id" to s.clientId.orEmpty()).first
                } catch (e: IOException) {
                    -1
                }
                if (code == 200) {
                    confirmed = true
                    break
                }
                if (code in 400..499) break
            }
        }
        save(s.signedOut(clock()))
        if (confirmed) null else "signed out on the phone; OpenAI didn't confirm it: disconnect Lani in ChatGPT's settings"
    }

    /** A form POST to the token endpoint; its JSON, or a [ChatGPTError] with OpenAI's code. */
    private suspend fun token(vararg form: Pair<String, String>): JsonObject {
        val (code, body) = try {
            post(ChatGPTOAuth.TOKEN, *form)
        } catch (e: IOException) {
            throw ApiError.Network(e.message ?: "the sign-in couldn't reach OpenAI")
        }
        val o = runCatching { tutorJson.parseToJsonElement(body) as JsonObject }.getOrNull()
        if (code !in 200..299 || o == null) {
            val err = o?.get("error")
            val c = (err as? JsonPrimitive)?.content ?: (err as? JsonObject)?.str("code") ?: (err as? JsonObject)?.str("type") ?: "http_$code"
            if (code >= 500) throw ApiError.Overloaded(code, "the sign-in's token endpoint: HTTP $code")
            throw ChatGPTError(c, o?.str("error_description") ?: (err as? JsonObject)?.str("message") ?: "the sign-in failed ($c)")
        }
        return o
    }

    private suspend fun post(url: String, vararg form: Pair<String, String>): Pair<Int, String> = withContext(Dispatchers.IO) {
        val u = url.toHttpUrl()
        check(OpenAIGuard.mayCarryOAuth(u)) { "the sign-in's tokens go only to auth.openai.com" }
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        http.newCall(Request.Builder().url(u).header("accept", "application/json").post(body).build()).execute().use { it.code to it.body?.string().orEmpty() }
    }

    /** OpenAI's signing keys (the JWKS), fetched once and again for a key id they lack. */
    private suspend fun jwks(refetch: Boolean): JsonObject {
        keys?.takeIf { !refetch }?.let { return it }
        val u = ChatGPTOAuth.JWKS.toHttpUrl()
        check(OpenAIGuard.mayCarryOAuth(u))
        val (code, body) = try {
            withContext(Dispatchers.IO) { http.newCall(Request.Builder().url(u).get().build()).execute().use { it.code to it.body?.string().orEmpty() } }
        } catch (e: IOException) {
            throw ChatGPTError("keys_unavailable", "OpenAI's signing keys couldn't be fetched: try again")
        }
        val o = runCatching { tutorJson.parseToJsonElement(body) as JsonObject }.getOrNull()
        if (code != 200 || o?.get("keys") !is JsonArray) throw ChatGPTError("keys_unavailable", "OpenAI's signing keys couldn't be fetched: try again")
        keys = o
        return o
    }

    private suspend fun verify(idToken: String, clientId: String, nonce: String?): IdToken.Claims {
        val kid = IdToken.kid(idToken)
        var set = jwks(refetch = false)
        if (kid != null && IdToken.key(set, kid) == null) set = jwks(refetch = true)
        return IdToken.verify(idToken, set, ChatGPTOAuth.ISSUER, clientId, nonce, clock())
    }

    companion object {
        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).followRedirects(false).build()
        }
    }
}

/**
 * An OpenID Connect ID token's check (the sign-in guide's "Validate the ID token"): its RS256 signature against a key
 * of the JWKS (by `kid`), the issuer, the audience (the issued client id; `azp` too when there are several), the expiry
 * (5 s of tolerance), the nonce of the attempt, a subject.
 */
object IdToken {
    data class Claims(val sub: String, val email: String?, val exp: Long)

    private val b64 = Base64.getUrlDecoder()

    private fun part(jwt: String, i: Int): JsonObject? =
        runCatching { tutorJson.parseToJsonElement(String(b64.decode(jwt.split('.')[i]), Charsets.UTF_8)) as JsonObject }.getOrNull()

    fun kid(jwt: String): String? = part(jwt, 0)?.str("kid")

    /** The RSA key [kid] names (or the only RSA key when none is named). */
    fun key(jwks: JsonObject, kid: String?): JsonObject? {
        val ks = (jwks["keys"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.filter { it.str("kty") == "RSA" }
        return if (kid == null) ks.singleOrNull() else ks.firstOrNull { it.str("kid") == kid }
    }

    fun verify(jwt: String, jwks: JsonObject, issuer: String, audience: String, nonce: String?, now: Long): Claims {
        fun bad(why: String): Nothing = throw ChatGPTError("invalid_id_token", "the ChatGPT identity didn't verify ($why): sign in again")
        val parts = jwt.split('.')
        if (parts.size != 3) bad("not a JWT")
        val header = part(jwt, 0) ?: bad("its header")
        if (header.str("alg") != "RS256") bad("alg ${header.str("alg")}")
        val jwk = key(jwks, header.str("kid")) ?: bad("no key ${header.str("kid")}")
        val pub = runCatching {
            KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(BigInteger(1, b64.decode(jwk.str("n"))), BigInteger(1, b64.decode(jwk.str("e")))))
        }.getOrNull() ?: bad("the key")
        val ok = runCatching {
            Signature.getInstance("SHA256withRSA").run {
                initVerify(pub)
                update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
                verify(b64.decode(parts[2]))
            }
        }.getOrDefault(false)
        if (!ok) bad("its signature")
        val c = part(jwt, 1) ?: bad("its claims")
        if (c.str("iss") != issuer) bad("issuer")
        val aud = when (val a = c["aud"]) {
            is JsonPrimitive -> listOf(a.content)
            is JsonArray -> a.mapNotNull { (it as? JsonPrimitive)?.content }
            else -> emptyList()
        }
        if (audience !in aud) bad("audience")
        c.str("azp")?.let { if (it != audience) bad("azp") }
        if (aud.size > 1 && c.str("azp") == null) bad("azp")
        val exp = (c["exp"] as? JsonPrimitive)?.longOrNull ?: bad("no expiry")
        if (exp * 1000 + 5_000 < now) bad("expired")
        if (nonce != null && c.str("nonce") != nonce) bad("nonce")
        val sub = c.str("sub")?.takeIf { it.isNotBlank() } ?: bad("no subject")
        return Claims(sub, c.str("email"), exp)
    }
}

/**
 * The loopback listener the browser comes back to (the sign-in guide: "an HTTP loopback callback on 127.0.0.1 …
 * Start the listener before opening the browser … only the port may vary", the path `/auth/callback`). It answers one
 * GET with the attempt's `state` (others: 404, or 400 for another state, so a stray request never ends the attempt),
 * with a page that sends the browser back to the app ([ChatGPTOAuth.BACK]).
 */
class Loopback(port: Int, private val state: String, private val savedClientId: String?) : Closeable {
    data class Callback(val code: String, val clientId: String)

    private val server = ServerSocket(port, 8, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
    val port: Int = server.localPort
    val redirectUri = "http://127.0.0.1:$port${ChatGPTOAuth.CALLBACK_PATH}"
    val result = CompletableDeferred<Callback>()

    init {
        Thread({
            while (!server.isClosed && !result.isCompleted) {
                val socket = try {
                    server.accept()
                } catch (e: IOException) {
                    break
                }
                runCatching { socket.use(::handle) }
            }
        }, "chatgpt-sign-in").apply { isDaemon = true }.start()
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 10_000
        val input = socket.getInputStream().bufferedReader(Charsets.UTF_8)
        val line = input.readLine() ?: return
        val headers = generateSequence { input.readLine()?.takeIf { it.isNotEmpty() } }.take(100).toList()
        val host = headers.firstOrNull { it.startsWith("host:", ignoreCase = true) }?.substringAfter(':')?.trim()
        val (method, target) = line.split(' ').let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
        val path = target.substringBefore('?')
        val out = socket.getOutputStream()
        fun respond(code: Int, html: String) {
            val bytes = html.toByteArray(Charsets.UTF_8)
            out.write(
                ("HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
                    "Cache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: $CSP\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII),
            )
            out.write(bytes)
            out.flush()
        }
        if (method != "GET" || path != ChatGPTOAuth.CALLBACK_PATH || host != "127.0.0.1:$port" || result.isCompleted) return respond(404, page("Not found", ""))
        val params = target.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.map {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }
        fun all(k: String) = params.filter { it.first == k }.map { it.second }
        val states = all("state")
        if (states.size != 1 || !MessageDigest.isEqual(states[0].toByteArray(), state.toByteArray())) {
            return respond(400, page("Prijava ni veljavna · Invalid sign-in", "Vrni se v zavihek, ki je začel prijavo. · Return to the tab that started the sign-in."))
        }
        all("error").firstOrNull()?.let { e ->
            respond(200, page("Prijava preklicana · Sign-in declined", BACK_LINK))
            result.completeExceptionally(ChatGPTError(e, if (e == "access_denied") "the sign-in was declined" else "the sign-in failed ($e)"))
            return
        }
        val codes = all("code")
        val ids = all("client_id")
        val clientId = ids.firstOrNull() ?: savedClientId
        val okId = clientId != null && clientId != ChatGPTOAuth.DYNAMIC_CLIENT && Regex("^[A-Za-z0-9_-]{1,200}$").matches(clientId) &&
            ids.size <= 1 && (savedClientId == null || ids.isEmpty() || ids[0] == savedClientId)
        if (codes.size != 1 || !okId) {
            respond(200, page("Prijava ni uspela · Sign-in failed", BACK_LINK))
            result.completeExceptionally(ChatGPTError("registration_incomplete", "ChatGPT didn't complete the app's registration: try again"))
            return
        }
        respond(200, page("✓ Prijava uspela · Signed in", "<p>Lani zdaj preveri povezavo s ChatGPT. · Lani is checking the ChatGPT connection now.</p>$BACK_LINK<script>$SCRIPT</script>"))
        result.complete(Callback(codes[0], clientId!!))
    }

    override fun close() {
        runCatching { server.close() }
        if (!result.isCompleted) result.completeExceptionally(ChatGPTError("cancelled", "the sign-in was cancelled"))
    }

    private companion object {
        const val SCRIPT = "setTimeout(function(){location.href='${ChatGPTOAuth.BACK}'},400);"
        val CSP = "default-src 'none'; style-src 'unsafe-inline'; script-src 'sha256-" +
            Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(SCRIPT.toByteArray())) + "'; base-uri 'none'; frame-ancestors 'none'"
        const val BACK_LINK = "<p><a href=\"${ChatGPTOAuth.BACK}\">↩ Nazaj v Lani · Back to Lani</a></p>"

        fun page(title: String, body: String) =
            "<!doctype html><html lang=\"sl\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Lani</title>" +
                "<style>body{font:17px system-ui,sans-serif;max-width:32rem;margin:15vh auto;padding:24px;color:#202123}h1{font-size:24px}" +
                "a{display:inline-block;margin-top:12px;padding:12px 18px;border-radius:12px;background:#1f6f43;color:#fff;text-decoration:none}</style>" +
                "<h1>$title</h1>$body</html>"
    }
}

/**
 * The tutor's OpenAI credentials: an API key ([apiKey], encrypted as the Claude key is) and/or the ChatGPT sign-in
 * ([chatgpt]). [bearer] gives a call's credential by its billing.
 */
class OpenAICredentials(val apiKey: KeyVault, val chatgpt: ChatGPTAuth) {
    /** What is set: [Billing.CHATGPT_PLAN] when signed in with the plan's use granted, [Billing.API_KEY] when a key is kept. */
    suspend fun available(): Set<String> = buildSet {
        if (chatgpt.session()?.planUsage == true) add(Billing.CHATGPT_PLAN)
        if (apiKey.has()) add(Billing.API_KEY)
    }

    /** The bearer for a call paid by [billing] (a fresh access token for the plan), or null when it isn't set. */
    suspend fun bearer(billing: String): OpenAIBearer? = when (billing) {
        Billing.CHATGPT_PLAN -> chatgpt.accessToken()?.let { OpenAIBearer(it, Billing.CHATGPT_PLAN) }
        else -> apiKey.load()?.let { OpenAIBearer(it, Billing.API_KEY) }
    }

    /** Every secret kept, for cleaning what goes out ([Redact.all]). */
    suspend fun secrets(): List<String?> {
        val s = chatgpt.session()
        return listOf(apiKey.load(), s?.accessToken, s?.refreshToken, s?.idToken)
    }

    companion object {
        /** The phone's: Keystore keys and DataStore entries of their own, beside the Claude key's. */
        fun android(context: android.content.Context): OpenAICredentials = OpenAICredentials(
            KeyVault(KeystoreCipher("lani-tutor-openai-key"), DataStoreSecretStore(context, "openai_key_sealed")),
            ChatGPTAuth(KeyVault(KeystoreCipher("lani-tutor-chatgpt"), DataStoreSecretStore(context, "chatgpt_session_sealed"))),
        )
    }
}
