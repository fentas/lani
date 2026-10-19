package si.lanisce.lani.tutor

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64

/**
 * "Sign in with ChatGPT" against a scripted auth.openai.com (no network but the loopback on 127.0.0.1, in this agent's
 * port block 19400–19499): the first registration with PKCE, the browser's return to the loopback, the code exchange,
 * the ID token's check, the session kept encrypted; refresh rotation, one refresh at a time; an unusable refresh token;
 * sign out with revocation, and the next sign-in with the kept client.
 */
class ChatGPTAuthTest {
    private var now = 1_790_000_000_000L
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val store = MemoryStore()
    private val vault = KeyVault(SoftCipher(), store)

    /** The nonce of the attempt (from the authorize URL), to put in the ID token. */
    private var nonce: String? = null
    private var tokenScope = "chatgpt.tokens.use.direct email offline_access openid profile resource.invoke"
    private var refreshAnswer: ((Map<String, String>) -> Triple<Int, String, String>)? = null
    private var issued = 0
    private val forms = mutableListOf<Pair<String, Map<String, String>>>()

    private fun b64(b: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    private fun unsigned(i: java.math.BigInteger) = i.toByteArray().let { if (it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }

    private fun jwt(claims: JsonObject, kid: String = "k1", signer: java.security.PrivateKey = keys.private): String {
        val head = b64(buildJsonObject { put("alg", "RS256"); put("kid", kid); put("typ", "JWT") }.toString().toByteArray())
        val body = b64(claims.toString().toByteArray())
        val sig = Signature.getInstance("SHA256withRSA").run { initSign(signer); update("$head.$body".toByteArray()); sign() }
        return "$head.$body.${b64(sig)}"
    }

    private fun idToken(aud: String = "oaiapp_1", nonce: String? = this.nonce, exp: Long = now / 1000 + 3600, sub: String = "user-123") = jwt(
        obj("iss" to "https://auth.openai.com", "aud" to aud, "sub" to sub, "email" to "jan@example.com", "exp" to exp, "iat" to now / 1000, "nonce" to nonce),
    )

    private val jwks: String
        get() {
            val pub = keys.public as RSAPublicKey
            return """{"keys":[{"kty":"RSA","kid":"k1","use":"sig","alg":"RS256","n":"${b64(unsigned(pub.modulus))}","e":"${b64(unsigned(pub.publicExponent))}"}]}"""
        }

    private fun form(r: Request): Map<String, String> = (r.body as FormBody).let { f -> (0 until f.size).associate { f.name(it) to f.value(it) } }

    private val auth = Answering { r ->
        when (r.url.encodedPath) {
            "/.well-known/jwks.json" -> Triple(200, jwks, "application/json")
            "/api/accounts/oauth/token" -> {
                val f = form(r)
                synchronized(forms) { forms += "token" to f }
                if (f["grant_type"] == "refresh_token") refreshAnswer?.let { return@Answering it(f) }
                val n = synchronized(this) { ++issued }
                Triple(
                    200,
                    obj(
                        "access_token" to "access-$n", "refresh_token" to "refresh-$n", "id_token" to (if (f["grant_type"] == "authorization_code") idToken() else idToken(nonce = null)),
                        "token_type" to "Bearer", "expires_in" to 3600, "scope" to tokenScope,
                    ).toString(),
                    "application/json",
                )
            }
            "/api/accounts/oauth/revoke" -> {
                synchronized(forms) { forms += "revoke" to form(r) }
                Triple(200, "", "text/plain")
            }
            else -> Triple(404, "{}", "application/json")
        }
    }

    private fun chatgpt() = ChatGPTAuth(vault, OkHttpClient.Builder().addInterceptor(auth).build(), clock = { now })

    /** The browser coming back: a GET of [target] at the loopback, its status line and body. */
    private fun browser(port: Int, target: String, host: String = "127.0.0.1:$port"): Pair<String, String> = Socket("127.0.0.1", port).use { s ->
        s.getOutputStream().write("GET $target HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray())
        val text = s.getInputStream().readBytes().decodeToString()
        text.substringBefore("\r\n") to text.substringAfter("\r\n\r\n")
    }

    private suspend fun signIn(c: ChatGPTAuth, port: Int, clientId: String? = "oaiapp_1"): ChatGPTSession {
        val pending = c.begin(port)
        val url = pending.url.toHttpUrl()
        nonce = url.queryParameter("nonce")
        val state = url.queryParameter("state")!!
        val (status, body) = browser(port, "/auth/callback?code=code-1&state=$state" + (clientId?.let { "&client_id=$it&scope=chatgpt.tokens.use.direct+email" } ?: ""))
        assertEquals("HTTP/1.1 200 OK", status)
        assertTrue(body.contains(ChatGPTOAuth.BACK))
        return pending.await(5_000)
    }

    @Test fun `the first sign-in - registration with PKCE, the loopback, the exchange, the identity, kept encrypted`() = runBlocking {
        val c = chatgpt()
        val pending = c.begin(19401)
        val url = pending.url.toHttpUrl()
        assertEquals("https://auth.openai.com/api/accounts/authorize", url.toString().substringBefore('?'))
        assertEquals("dynamic_agent_client", url.queryParameter("client_id"))
        assertEquals("Lani", url.queryParameter("agent_name_hint"))
        assertEquals("code", url.queryParameter("response_type"))
        assertEquals("http://127.0.0.1:19401/auth/callback", url.queryParameter("redirect_uri"))
        assertEquals("openid profile email offline_access resource.invoke chatgpt.tokens.use.direct", url.queryParameter("scope"))
        assertEquals("https://api.openai.com/v1", url.queryParameter("resource"))
        assertEquals("S256", url.queryParameter("code_challenge_method"))
        assertTrue(url.queryParameter("ext_agent_host_id")!!.matches(Regex("urn:uuid:[0-9a-f-]{36}")))
        assertNull(url.queryParameter("id_token_hint"))
        val state = url.queryParameter("state")!!
        nonce = url.queryParameter("nonce")
        assertTrue(state.length >= 43 && nonce!!.length >= 43 && state != nonce)
        // the host id is kept before the first sign-in
        assertEquals(url.queryParameter("ext_agent_host_id"), c.session()!!.hostId)

        // a stray request and another attempt's state leave the attempt waiting
        assertEquals("HTTP/1.1 404 Error", browser(19401, "/favicon.ico").first)
        assertEquals("HTTP/1.1 400 Error", browser(19401, "/auth/callback?code=x&state=not-it&client_id=oaiapp_1").first)
        assertEquals("HTTP/1.1 404 Error", browser(19401, "/auth/callback?code=x&state=$state&client_id=oaiapp_1", host = "localhost:19401").first)
        assertFalse(pending.redirectUri.isEmpty())
        val (status, page) = browser(19401, "/auth/callback?code=code-1&state=$state&scope=chatgpt.tokens.use.direct+email+offline_access+openid+profile+resource.invoke&client_id=oaiapp_1")
        assertEquals("HTTP/1.1 200 OK", status)
        assertTrue(page.contains("lani://tutor/signed-in") && page.contains("Nazaj v Lani"))
        val session = pending.await(5_000)

        // the exchange: the issued client, the code, the verifier whose S256 is the challenge, the same redirect
        val (kind, f) = forms.single()
        assertEquals("token", kind)
        assertEquals("authorization_code", f["grant_type"])
        assertEquals("oaiapp_1", f["client_id"])
        assertEquals("code-1", f["code"])
        assertEquals("http://127.0.0.1:19401/auth/callback", f["redirect_uri"])
        assertEquals("https://api.openai.com/v1", f["resource"])
        assertEquals(url.queryParameter("code_challenge"), b64(MessageDigest.getInstance("SHA-256").digest(f["code_verifier"]!!.toByteArray())))
        assertNull(f["client_secret"])
        // the session: the client, the account, the plan's use, the tokens; encrypted in its store
        assertEquals("oaiapp_1", session.clientId)
        assertEquals("user-123", session.subject)
        assertEquals("jan@example.com", session.email)
        assertTrue(session.planUsage)
        assertEquals(now + 3_600_000, session.expiresAt)
        assertEquals("access-1", c.accessToken())
        assertFalse(store.value!!.contains("access-1") || store.value!!.contains("refresh-1") || store.value!!.contains("jan@example.com"))
        assertFalse(session.toString().contains("access-1"))
    }

    @Test fun `the redirect names the port the listener got - the app asks for 0, the system's pick`() = runBlocking {
        // the app begins with port 0: the URL once said 127.0.0.1:0 (the constructor's argument, not the bound port),
        // and auth.openai.com refused it (invalid_authorize_request, param redirect_uri)
        val pending = chatgpt().begin(0)
        val redirect = pending.url.toHttpUrl().queryParameter("redirect_uri")!!.toHttpUrl()
        assertEquals("127.0.0.1", redirect.host)
        assertTrue("a real port: $redirect", redirect.port in 1..65535)
        assertEquals(pending.redirectUri, redirect.toString())
        val (status, _) = browser(redirect.port, "/auth/callback?code=x&state=not-this-attempt")
        assertTrue("the listener on that port answers: $status", status.startsWith("HTTP/1.1 400"))
        pending.cancel()
    }

    @Test fun `the exchange waits for the network - Lani in the background while the browser comes back`() = runBlocking {
        // Android holds a background app's network (battery saver): the first tries fail to resolve auth.openai.com
        var offline = 3
        val waits = mutableListOf<Long>()
        val flaky = okhttp3.Interceptor { chain ->
            if (offline > 0 && chain.request().url.encodedPath == "/api/accounts/oauth/token") {
                offline--
                throw java.net.UnknownHostException("Unable to resolve host \"auth.openai.com\": No address associated with hostname")
            }
            chain.proceed(chain.request())
        }
        val c = ChatGPTAuth(vault, OkHttpClient.Builder().addInterceptor(flaky).addInterceptor(auth).build(), clock = { now }, pause = { waits += it })
        val s = signIn(c, 19403)
        assertTrue(s.connected)
        assertEquals(ChatGPTAuth.OFFLINE_WAITS.take(3), waits)
    }

    @Test fun `offline for good - told plainly, nothing kept but the client`() = runBlocking {
        val down = okhttp3.Interceptor { chain ->
            if (chain.request().url.encodedPath == "/api/accounts/oauth/token") throw java.net.UnknownHostException("Unable to resolve host")
            chain.proceed(chain.request())
        }
        val c = ChatGPTAuth(vault, OkHttpClient.Builder().addInterceptor(down).addInterceptor(auth).build(), clock = { now }, pause = { })
        val e = try {
            signIn(c, 19404)
            null
        } catch (e: ChatGPTError) {
            e
        }
        assertEquals("offline", e?.code)
        assertTrue(e!!.message!!.contains("in the background"))
        assertEquals(null, c.session()?.accessToken)
    }

    @Test fun `the access token is renewed near its end, the refresh token rotating, one refresh at a time`() = runBlocking {
        val c = chatgpt()
        signIn(c, 19402)
        forms.clear()
        now += 3_000_000 // 50 minutes: still valid
        assertEquals("access-1", c.accessToken())
        assertTrue(forms.isEmpty())
        now += 560_000 // under a minute left: renewed
        assertEquals("access-2", c.accessToken())
        val (_, f) = forms.single()
        assertEquals("refresh_token", f["grant_type"])
        assertEquals("oaiapp_1", f["client_id"])
        assertEquals("refresh-1", f["refresh_token"])
        assertEquals("https://api.openai.com/v1", f["resource"])
        assertNull(f["scope"])
        assertEquals("refresh-2", c.session()!!.refreshToken)
        // the next renewal uses the replacement; two calls at once make one refresh
        now += 3_600_000
        forms.clear()
        val tokens = coroutineScope { (1..2).map { async { c.accessToken() } }.awaitAll() }
        assertEquals(listOf("access-3", "access-3"), tokens)
        assertEquals(listOf("refresh-2"), forms.map { it.second["refresh_token"] })
        // a 401: renewed at once
        assertEquals("access-4", c.accessToken(force = true))
    }

    @Test fun `a refresh token that can't be used ends the sign-in, the client and the host kept`() = runBlocking {
        val c = chatgpt()
        val first = signIn(c, 19403)
        refreshAnswer = { Triple(400, """{"error":"invalid_grant","error_description":"refresh token reused"}""", "application/json") }
        now += 3_600_000
        try {
            c.accessToken()
            fail("no error")
        } catch (e: ApiError.Auth) {
            assertTrue(e.message!!.contains("invalid_grant"))
        }
        val s = c.session()!!
        assertFalse(s.connected)
        assertEquals("oaiapp_1", s.clientId)
        assertEquals(first.hostId, s.hostId)
        assertNull(c.accessToken())
        // a passing failure keeps everything
        val c2 = chatgpt()
        refreshAnswer = null
        signIn(c2, 19404)
        refreshAnswer = { Triple(503, "busy", "text/plain") }
        now += 3_600_000
        try {
            c2.accessToken()
            fail("no error")
        } catch (e: ApiError.Overloaded) {
            assertTrue(c2.session()!!.connected)
        }
    }

    @Test fun `sign out revokes the refresh token, and the next sign-in reuses the client and the host`() = runBlocking {
        val c = chatgpt()
        val first = signIn(c, 19405)
        forms.clear()
        assertNull(c.signOut())
        val (kind, f) = forms.single()
        assertEquals("revoke", kind)
        assertEquals(mapOf("token" to "refresh-1", "token_type_hint" to "refresh_token", "client_id" to "oaiapp_1"), f)
        val out = c.session()!!
        assertFalse(out.connected)
        assertNull(out.idToken)
        assertEquals("oaiapp_1", out.clientId)
        assertEquals(first.hostId, out.hostId)
        // signing in again: the issued client, no name hint, the email as login_hint, the same host
        val again = c.begin(19406)
        val url = again.url.toHttpUrl()
        assertEquals("oaiapp_1", url.queryParameter("client_id"))
        assertNull(url.queryParameter("agent_name_hint"))
        assertEquals("jan@example.com", url.queryParameter("login_hint"))
        assertEquals(first.hostId, url.queryParameter("ext_agent_host_id"))
        nonce = url.queryParameter("nonce")
        // a reauthorization's callback may leave the client out: the kept one is used
        browser(19406, "/auth/callback?code=code-2&state=${url.queryParameter("state")}")
        assertEquals("oaiapp_1", again.await(5_000).clientId)
    }

    @Test fun `declined, another client, the plan's use not granted`() = runBlocking {
        val c = chatgpt()
        val p = c.begin(19407)
        browser(19407, "/auth/callback?error=access_denied&state=${p.url.toHttpUrl().queryParameter("state")}")
        try {
            p.await(5_000)
            fail("no error")
        } catch (e: ChatGPTError) {
            assertEquals("access_denied", e.code)
        }
        // a callback with the dynamic client as the issued one: registration incomplete
        val q = c.begin(19408)
        browser(19408, "/auth/callback?code=c&state=${q.url.toHttpUrl().queryParameter("state")}&client_id=dynamic_agent_client")
        try {
            q.await(5_000)
            fail("no error")
        } catch (e: ChatGPTError) {
            assertEquals("registration_incomplete", e.code)
        }
        // signed in without the plan's scope: kept, but not usable for calls
        tokenScope = "email offline_access openid profile"
        val s = signIn(c, 19409)
        assertTrue(s.connected)
        assertFalse(s.planUsage)
        val creds = OpenAICredentials(KeyVault(SoftCipher(), MemoryStore()), c)
        assertEquals(emptySet<String>(), creds.available())
    }

    @Test fun `an ID token that isn't right is refused`() {
        val set = tutorJson.parseToJsonElement(jwks) as JsonObject
        fun refused(token: String, nonce: String? = "n1") = try {
            IdToken.verify(token, set, "https://auth.openai.com", "oaiapp_1", nonce, now)
            false
        } catch (e: ChatGPTError) {
            e.code == "invalid_id_token"
        }
        assertEquals("user-123", IdToken.verify(idToken(nonce = "n1"), set, "https://auth.openai.com", "oaiapp_1", "n1", now).sub)
        assertTrue(refused(idToken(nonce = "other")))
        assertTrue(refused(idToken(aud = "oaiapp_2", nonce = "n1")))
        assertTrue(refused(idToken(exp = now / 1000 - 60, nonce = "n1")))
        assertTrue(refused(jwt(obj("iss" to "https://auth.openai.com", "aud" to "oaiapp_1", "sub" to "x", "exp" to now / 1000 + 60, "nonce" to "n1"), signer = other.private)))
        assertTrue(refused(jwt(obj("iss" to "https://evil.example", "aud" to "oaiapp_1", "sub" to "x", "exp" to now / 1000 + 60, "nonce" to "n1"))))
        assertTrue(refused(jwt(obj("iss" to "https://auth.openai.com", "aud" to "oaiapp_1", "exp" to now / 1000 + 60, "nonce" to "n1"))))
        assertTrue(refused("not.a.jwt"))
    }

    @Test fun `the sign-in's tokens go to auth_openai_com only, and never into a log line`() {
        assertTrue(OpenAIGuard.mayCarryOAuth(ChatGPTOAuth.TOKEN.toHttpUrl()) && OpenAIGuard.mayCarryOAuth(ChatGPTOAuth.REVOKE.toHttpUrl()) && OpenAIGuard.mayCarryOAuth(ChatGPTOAuth.JWKS.toHttpUrl()))
        val token = idToken(nonce = "n")
        assertEquals("refused: ‹token›", Redact.of("refused: $token"))
        assertEquals("""{"access_token":"‹…›","refresh_token":"‹…›"}""", Redact.all("""{"access_token":"abc12345","refresh_token":"def67890"}"""))
    }
}
