package si.lanisce.lani.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * The voice service's attestation on the app's side (data/GlasAttest.kt): the canonical request as glas has it, what is
 * signed and when, the enrollment's outcomes, the backoff, and what is never signed.
 */
class GlasAttestTest {
    private lateinit var server: MockWebServer
    private val keys = SoftAttestKeys()

    @Before fun up() {
        server = MockWebServer().apply { start() }
        GlasAuth.offset = 0
    }

    @After fun down() {
        server.shutdown()
        GlasAuth.key = null
        GlasAuth.glas = ""
        GlasAuth.offset = 0
    }

    private val glas get() = server.url("/").toString().trimEnd('/')

    private fun verifies(k: KeyPair, message: String, sig: String) = Signature.getInstance("SHA256withECDSA").run {
        initVerify(k.public)
        update(message.toByteArray())
        verify(Base64.getDecoder().decode(sig))
    }

    @Test fun `the canonical request is glas's, line for line`() {
        // the same vector as glas's test/attest.test.ts would sign (src/attest/request.ts)
        val c = GlasRequest.canonical("glas.example", "ab".repeat(32), "post", "/v1/app/sl/voice/say?x=1", 1_762_430_000_000, "nonce-0123456789ab", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        assertEquals(
            "glas-request/1\nglas.example\n${"ab".repeat(32)}\nPOST\n/v1/app/sl/voice/say?x=1\n1762430000000\nnonce-0123456789ab\ne3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            c,
        )
        assertEquals("glas.example", GlasRequest.audience("https://glas.example"))
        assertEquals("10.0.2.2:11340", GlasRequest.audience("http://10.0.2.2:11340"))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", GlasRequest.sha256hex(ByteArray(0)))
    }

    @Test fun `an enrollment, the challenge's key, its chain, signed by itself`() {
        server.enqueue(MockResponse().setBody("""{"challenge":"${Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 7 })}","expires_at":"2026-11-06T12:05:00Z"}""").addHeader("X-Glas-Time", "1762430400000"))
        server.enqueue(MockResponse().setBody("""{"status":"enrolled","key_id":"k","install":"i","expires_at":"2026-12-06T12:00:00Z","renew_after":"2026-11-29T12:00:00Z","level":"tee","renewed":false}"""))
        val r = GlasEnroll(glas, keys, { 1_762_430_400_000 }).enroll(AttestState(), renew = false)
        assertTrue(r is Enrolled.Yes)
        val s = (r as Enrolled.Yes).state
        assertEquals(AttestState.ENROLLED, s.status)
        assertEquals("tee", r.level)
        assertEquals(java.time.Instant.parse("2026-12-06T12:00:00Z").toEpochMilli(), s.expiresAt)
        server.takeRequest()
        val enroll = server.takeRequest()
        assertEquals("/v1/app/attest", enroll.path)
        val body = enroll.body.readUtf8()
        val o = json.parseToJsonElement(body) as JsonObject
        assertEquals("android-key", o["kind"]!!.jsonPrimitive.content)
        assertEquals(2, o["chain"]!!.jsonArray.size)
        val pair = keys.pairs.values.single()
        val keyId = GlasRequest.sha256hex(pair.public.encoded)
        assertEquals(keyId, enroll.getHeader("X-Glas-Key"))
        val message = GlasRequest.canonical(GlasRequest.audience(glas), keyId, "POST", "/v1/app/attest", 1_762_430_400_000, enroll.getHeader("X-Glas-Nonce")!!, GlasRequest.sha256hex(body.toByteArray()))
        assertTrue("signed by the new key", verifies(pair, message, enroll.getHeader("X-Glas-Signature")!!))
        assertNull(enroll.getHeader("X-Glas-Prev-Key"))
    }

    @Test fun `a renewal, the old key signs the same request, and goes`() {
        val old = SoftAttestKeys().also { it.create("old", ByteArray(1)) }
        keys.pairs["old"] = old.pairs["old"]!!
        val oldId = keys.key("old")!!.keyId
        server.enqueue(MockResponse().setBody("""{"challenge":"${Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32))}"}"""))
        server.enqueue(MockResponse().setBody("""{"status":"enrolled","key_id":"k2","install":"$oldId","expires_at":"2026-12-06T12:00:00Z","renew_after":"2026-11-29T12:00:00Z","level":"tee","renewed":true}"""))
        val r = GlasEnroll(glas, keys, { 1_000 }).enroll(AttestState(alias = "old", keyId = oldId, status = AttestState.ENROLLED), renew = true)
        assertTrue(r is Enrolled.Yes)
        assertEquals(oldId, (r as Enrolled.Yes).state.install)
        server.takeRequest()
        val enroll = server.takeRequest()
        val body = enroll.body.readUtf8()
        assertTrue(body.contains("\"previous\":\"$oldId\""))
        assertEquals(oldId, enroll.getHeader("X-Glas-Prev-Key"))
        val message = GlasRequest.canonical(GlasRequest.audience(glas), enroll.getHeader("X-Glas-Key")!!, "POST", "/v1/app/attest", 1_000, enroll.getHeader("X-Glas-Nonce")!!, GlasRequest.sha256hex(body.toByteArray()))
        assertTrue("signed by the old key too", verifies(old.pairs["old"]!!, message, enroll.getHeader("X-Glas-Prev-Signature")!!))
        assertNull("the old key gone", keys.key("old"))
    }

    @Test fun `refused for good, or not now, a 403 is this phone's answer, the rest passes`() {
        fun challenge() = server.enqueue(MockResponse().setBody("""{"challenge":"${Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32))}"}"""))
        challenge()
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"code":"attestation_refused","reason":"bootloader_unlocked"}}"""))
        assertEquals(Enrolled.Refused("bootloader_unlocked"), GlasEnroll(glas, keys).enroll(AttestState(), false))
        assertTrue("a refused key is not kept", keys.pairs.isEmpty())
        challenge()
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":{"code":"unavailable","reason":"status_unavailable"}}"""))
        assertEquals("a 5xx: the key kept, asked about next time", Enrolled.NotNow("pending:unavailable"), GlasEnroll(glas, keys).enroll(AttestState(), false))
        assertEquals(1, keys.pairs.size)
        keys.pairs.clear()
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":{"code":"unavailable","reason":"not_configured"}}"""))
        assertEquals("a service that attests no app: the library's only", Enrolled.Refused("not_configured"), GlasEnroll(glas, keys).enroll(AttestState(), false))
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"code":"rate_limited"}}"""))
        assertEquals(Enrolled.NotNow("challenge:rate_limited"), GlasEnroll(glas, keys).enroll(AttestState(), false))
        challenge()
        keys.fail = TransientAttestFailure("ATTESTATION_KEYS_UNAVAILABLE")
        assertTrue(GlasEnroll(glas, keys).enroll(AttestState(), false) is Enrolled.NotNow)
        challenge()
        keys.fail = UnsupportedOperationException("no attestation")
        assertEquals(Enrolled.Refused("keystore"), GlasEnroll(glas, keys).enroll(AttestState(), false))
        val gone = glas
        server.shutdown()
        keys.fail = null
        assertTrue("no network", GlasEnroll(gone, keys).enroll(AttestState(), false) is Enrolled.NotNow)
    }

    @Test fun `a passing failure is tried again later, more slowly, a refusal waits a month, a renewal when due or asked`() {
        val a = AttestState().failedForNow("network", 0)
        assertEquals(AttestState.BACKOFF_MS, a.nextTry)
        assertNull(a.due(a.nextTry - 1))
        assertEquals(AttestState.Due.ENROLL, a.due(a.nextTry))
        val b = a.failedForNow("network", 0).failedForNow("network", 0)
        assertEquals(4 * AttestState.BACKOFF_MS, b.nextTry)
        var c = b
        repeat(20) { c = c.failedForNow("network", 0) }
        assertEquals(AttestState.MAX_BACKOFF_MS, c.nextTry)
        val refused = c.refused("boot_unverified", 0)
        assertNull(refused.due(AttestState.REFUSED_RETRY_MS - 1))
        assertEquals(AttestState.Due.ENROLL, refused.due(AttestState.REFUSED_RETRY_MS))
        val enrolled = AttestState(alias = "a", keyId = "k", status = AttestState.ENROLLED, expiresAt = 100, renewAfter = 50)
        assertTrue(enrolled.attested(49))
        assertNull(enrolled.due(49))
        assertEquals(AttestState.Due.RENEW, enrolled.due(50))
        assertEquals(AttestState.Due.RENEW, enrolled.copy(renew = true).due(10))
        assertFalse(enrolled.attested(100))
        assertEquals(enrolled, AttestState.decode(enrolled.encode()))
        assertEquals(AttestState(), AttestState.decode("nonsense"))
    }

    @Test fun `requests to the voice service's app routes are signed, per attempt, with the server's time, nothing else is`() {
        keys.create("k", ByteArray(1))
        val key = keys.key("k")!!
        GlasAuth.glas = glas
        GlasAuth.key = key
        val http = Http.client(connectSeconds = 5, readSeconds = 5)
        var renewed = false
        GlasAuth.onRenew = { renewed = true }
        // a stale clock: the 401 says the server's time, the call is tried once more with it, under one Idempotency-Key
        val serverTime = System.currentTimeMillis() + 3_600_000
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"stale"},"server_time":$serverTime}""").addHeader("X-Glas-Time", serverTime.toString()))
        server.enqueue(MockResponse().setBody("{}").addHeader("X-Glas-Time", serverTime.toString()).addHeader("X-Glas-Renew", "1"))
        val body = """{"text":"Dober dan","voice":"grandma"}"""
        val r = http.newCall(Request.Builder().url("$glas/v1/app/sl/voice/say").header("Authorization", "Bearer lani_0123").post(body.toRequestBody(Http.jsonType)).build()).execute()
        assertEquals(200, r.code)
        r.close()
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertNull("no token with a signature", second.getHeader("Authorization"))
        assertEquals(first.getHeader("Idempotency-Key"), second.getHeader("Idempotency-Key"))
        assertNotNull(second.getHeader("Idempotency-Key"))
        assertTrue("a new nonce for the second attempt", first.getHeader("X-Glas-Nonce") != second.getHeader("X-Glas-Nonce"))
        val time = second.getHeader("X-Glas-Time")!!.toLong()
        assertTrue("signed at the server's time", kotlin.math.abs(time - serverTime) < 60_000)
        val message = GlasRequest.canonical(GlasRequest.audience(glas), key.keyId, "POST", "/v1/app/sl/voice/say", time, second.getHeader("X-Glas-Nonce")!!, GlasRequest.sha256hex(body.toByteArray()))
        assertTrue(verifies(keys.pairs["k"]!!, message, second.getHeader("X-Glas-Signature")!!))
        assertTrue("X-Glas-Renew asks for a new key", renewed)
        // a GET's query is signed as sent, with an empty body's hash and no Idempotency-Key
        server.enqueue(MockResponse().setBody("{}"))
        http.newCall(Request.Builder().url("$glas/v1/app/sl/voice/index?since=1").build()).execute().close()
        val get = server.takeRequest()
        assertNull(get.getHeader("Idempotency-Key"))
        val m2 = GlasRequest.canonical(GlasRequest.audience(glas), key.keyId, "GET", "/v1/app/sl/voice/index?since=1", get.getHeader("X-Glas-Time")!!.toLong(), get.getHeader("X-Glas-Nonce")!!, GlasRequest.sha256hex(ByteArray(0)))
        assertTrue(verifies(keys.pairs["k"]!!, m2, get.getHeader("X-Glas-Signature")!!))
        // not signed: a key of its own (QA's), the attestation routes, another host
        server.enqueue(MockResponse().setBody("{}"))
        http.newCall(Request.Builder().url("$glas/v1/app/sl/voice/index").header("Authorization", "Bearer glas_qa_x").build()).execute().close()
        val qa = server.takeRequest()
        assertNull(qa.getHeader("X-Glas-Key"))
        assertEquals("Bearer glas_qa_x", qa.getHeader("Authorization"))
        server.enqueue(MockResponse().setBody("{}"))
        http.newCall(Request.Builder().url("$glas/v1/app/attest/challenge").post(ByteArray(0).toRequestBody(Http.jsonType)).build()).execute().close()
        assertNull(server.takeRequest().getHeader("X-Glas-Key"))
        GlasAuth.glas = "https://elsewhere.example"
        server.enqueue(MockResponse().setBody("{}"))
        http.newCall(Request.Builder().url("$glas/v1/app/sl/voice/index").build()).execute().close()
        assertNull(server.takeRequest().getHeader("X-Glas-Key"))
        GlasAuth.onRenew = {}
    }

    @Test fun `with no key nothing is signed, an install's token goes as it is`() {
        GlasAuth.glas = glas
        GlasAuth.key = null
        server.enqueue(MockResponse().setBody("{}"))
        Http.client(5, 5).newCall(Request.Builder().url("$glas/v1/trial").header("Authorization", "Bearer lani_0123").build()).execute().close()
        val r = server.takeRequest()
        assertEquals("Bearer lani_0123", r.getHeader("Authorization"))
        assertNull(r.getHeader("X-Glas-Key"))
    }

    private fun challengeAnswer() = MockResponse().setBody("""{"challenge":"${Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 3 })}"}""")

    private fun enrolledAnswer(keyId: String, install: String) =
        MockResponse().setBody("""{"status":"enrolled","key_id":"$keyId","install":"$install","expires_at":"2026-12-06T12:00:00Z","renew_after":"2026-11-29T12:00:00Z","level":"tee"}""")

    @Test fun `a renewal whose answer was lost, the key kept, the next run takes on the successor and keeps the install`() {
        keys.create("old", ByteArray(1))
        val old = keys.key("old")!!
        val state = AttestState(alias = "old", keyId = old.keyId, install = "the-install", status = AttestState.ENROLLED, expiresAt = Long.MAX_VALUE, renewAfter = 0)
        var kept = "" to ""
        val enroll = GlasEnroll(glas, keys, { 1_000 }) { alias, keyId -> kept = alias to keyId }
        // the service: it commits every enrollment it is sent, and drops the answer to it; it knows the keys it took
        val took = java.util.Collections.synchronizedList(mutableListOf<String>())
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse = when {
                request.path == "/v1/app/attest/challenge" -> challengeAnswer()
                request.method == "POST" -> MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST).also { took += request.getHeader("X-Glas-Key")!! }
                request.getHeader("X-Glas-Key") in took -> enrolledAnswer(request.getHeader("X-Glas-Key")!!, "the-install")
                else -> MockResponse().setResponseCode(401).setBody("""{"error":{"code":"unknown_key"}}""")
            }
        }
        val first = enroll.enroll(state, renew = true)
        assertTrue(first is Enrolled.NotNow)
        assertEquals("sent once, never again by the client itself", 1, took.size)
        assertTrue("the new key kept", kept.first.isNotEmpty() && keys.key(kept.first) != null)
        assertEquals(kept.second, took.single())
        assertNotNull("the old key kept too, until it is known", keys.key("old"))
        server.takeRequest()
        server.takeRequest()
        // the next run asks the service about the kept key first: enrolled, with the old install
        val again = enroll.enroll(state.copy(pendingAlias = kept.first, pendingKeyId = kept.second), renew = true)
        assertTrue(again is Enrolled.Yes)
        val s2 = (again as Enrolled.Yes).state
        assertEquals(kept.first, s2.alias)
        assertEquals(kept.second, s2.keyId)
        assertEquals("the install kept", "the-install", s2.install)
        assertEquals(java.time.Instant.parse("2026-12-06T12:00:00Z").toEpochMilli(), s2.expiresAt)
        assertEquals("", s2.pendingAlias)
        assertNull("the old key gone", keys.key("old"))
        val asked = server.takeRequest()
        assertEquals("GET /v1/app/attest HTTP/1.1", asked.requestLine)
        val message = GlasRequest.canonical(GlasRequest.audience(glas), kept.second, "GET", "/v1/app/attest", 1_000, asked.getHeader("X-Glas-Nonce")!!, GlasRequest.sha256hex(ByteArray(0)))
        assertTrue("asked signed by the kept key", verifies(keys.pairs[kept.first]!!, message, asked.getHeader("X-Glas-Signature")!!))
    }

    @Test fun `a kept key the service never took is forgotten, and the renewal goes on with the old key`() {
        keys.create("old", ByteArray(1))
        keys.create("lost", ByteArray(1))
        val old = keys.key("old")!!
        val lost = keys.key("lost")!!
        val state = AttestState(alias = "old", keyId = old.keyId, install = "i", status = AttestState.ENROLLED, expiresAt = Long.MAX_VALUE, pendingAlias = "lost", pendingKeyId = lost.keyId)
        var kept = "x" to "x"
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"unknown_key","message":"no such key"}}"""))
        server.enqueue(challengeAnswer())
        server.enqueue(enrolledAnswer("k2", "i"))
        val r = GlasEnroll(glas, keys, { 1_000 }) { alias, keyId -> kept = alias to keyId }.enroll(state, renew = true)
        assertTrue(r is Enrolled.Yes)
        assertNull("the lost key gone", keys.key("lost"))
        server.takeRequest()
        server.takeRequest()
        assertEquals("vouched for by the old key", old.keyId, server.takeRequest().getHeader("X-Glas-Prev-Key"))
        // and with no answer to the question either: nothing changes, nothing is made
        val again = AttestState(alias = "x", keyId = "y", status = AttestState.ENROLLED, pendingAlias = kept.first, pendingKeyId = kept.second)
        server.enqueue(MockResponse().setResponseCode(502))
        assertEquals(Enrolled.NotNow("pending:http:502"), GlasEnroll(glas, keys, { 1_000 }).enroll(again, renew = true))
        assertNotNull(keys.key(kept.first))
    }

    @Test fun `an old key that vouched already, its successor lost, starts again as a new install, not refused`() {
        keys.create("old", ByteArray(1))
        val old = keys.key("old")!!
        val state = AttestState(alias = "old", keyId = old.keyId, install = "i", status = AttestState.ENROLLED, expiresAt = Long.MAX_VALUE)
        server.enqueue(challengeAnswer())
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"code":"attestation_refused","reason":"previous_renewed"}}"""))
        server.enqueue(challengeAnswer())
        server.enqueue(enrolledAnswer("k3", "k3"))
        val r = GlasEnroll(glas, keys, { 1_000 }).enroll(state, renew = true)
        assertTrue(r is Enrolled.Yes)
        assertEquals("a new install", "k3", (r as Enrolled.Yes).state.install)
        assertNull(keys.key("old"))
        server.takeRequest()
        assertEquals(old.keyId, server.takeRequest().getHeader("X-Glas-Prev-Key"))
        server.takeRequest()
        assertNull("the second try names no old key", server.takeRequest().getHeader("X-Glas-Prev-Key"))
    }
}
