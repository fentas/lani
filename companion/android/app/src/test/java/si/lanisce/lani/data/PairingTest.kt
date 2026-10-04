package si.lanisce.lani.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.URLEncoder
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class PairingTest {
    /** The payload as companion/bridge/src/pairing.ts writes it (pairUri: encodeURIComponent for the URL). */
    private fun uri(
        v: String? = "1",
        u: String? = "https://node.tailnet.ts.net:8443",
        c: String? = "K7Q2M9XPA4TD",
        p: String? = "luka",
        f: String? = "20398153fcf896040daeefddca60749b",
    ) = "lani://pair?" + listOfNotNull(
        v?.let { "v=$it" },
        u?.let { "u=" + URLEncoder.encode(it, "UTF-8").replace("+", "%20") },
        c?.let { "c=$it" },
        p?.let { "p=$it" },
        f?.let { "f=$it" },
    ).joinToString("&")

    private fun problem(s: String) = (PairPayload.parse(s) as? PairParse.Invalid)?.problem
    private fun ok(s: String) = (PairPayload.parse(s) as PairParse.Ok).payload

    // --- the payload ---------------------------------------------------------------------------------

    @Test fun `the bridge's payload parses`() {
        assertEquals(
            PairPayload(1, "https://node.tailnet.ts.net:8443", "K7Q2M9XPA4TD", "luka", "20398153fcf896040daeefddca60749b"),
            ok(uri()),
        )
        // exactly what lani-pair printed in a run
        val printed = "lani://pair?v=1&u=https%3A%2F%2Fnode.example.ts.net%3A8443&c=9AHFBW8VUEU2&p=luka&f=6e8d0abb5c18fe79274eabf52c06d3a3"
        assertEquals("https://node.example.ts.net:8443", ok(printed).url)
        assertEquals("default", ok(uri(p = "default")).profile)
    }

    @Test fun `codes and fingerprints are compared in one case, whitespace around the text is ignored`() {
        val p = ok("  " + uri(c = "k7q2-m9xp-a4td", f = "20398153FCF896040DAEEFDDCA60749B") + "\n")
        assertEquals("K7Q2M9XPA4TD", p.code)
        assertEquals("20398153fcf896040daeefddca60749b", p.fingerprint)
    }

    @Test fun `version 1 is understood, a newer one asks for an update, none or junk is incomplete`() {
        assertEquals(1, ok(uri(v = "1")).version)
        assertEquals(PairProblem.NEWER_VERSION, problem(uri(v = "2")))
        assertEquals(PairProblem.INCOMPLETE, problem(uri(v = null)))
        assertEquals(PairProblem.INCOMPLETE, problem(uri(v = "0")))
        assertEquals(PairProblem.INCOMPLETE, problem(uri(v = "one")))
    }

    @Test fun `a missing or malformed field makes it incomplete`() {
        assertEquals(PairProblem.INCOMPLETE, problem(uri(u = null)))
        assertEquals(PairProblem.INCOMPLETE, problem(uri(c = null)))
        assertEquals(PairProblem.INCOMPLETE, problem(uri(p = null)))
        assertEquals(PairProblem.INCOMPLETE, problem(uri(f = null)))
        assertEquals(PairProblem.INCOMPLETE, problem(uri(c = "ABC")))
        assertEquals(PairProblem.INCOMPLETE, problem(uri(c = "K7Q2M9XPA4T!")))
        assertEquals(PairProblem.INCOMPLETE, problem(uri(p = "Luka")))
        assertEquals(PairProblem.INCOMPLETE, problem(uri(p = "../jan")))
        assertEquals(PairProblem.INCOMPLETE, problem(uri(f = "2039")))
        assertEquals(PairProblem.INCOMPLETE, problem(uri(f = "z0398153fcf896040daeefddca60749b")))
        assertEquals(PairProblem.INCOMPLETE, problem("lani://pair?v=1&u=%zz&c=K7Q2M9XPA4TD&p=luka&f=20398153fcf896040daeefddca60749b"))
    }

    @Test fun `the bridge URL must be plain http or https with a host`() {
        for (bad in listOf(
            "ftp://node.ts.net", "", "not a url", "https://", "https:///x", "javascript:alert(1)", "file:///etc/passwd",
            "https://user:pw@node.ts.net", "https://node.ts.net/?next=evil", "https://node.ts.net/#f", "https://node ts.net",
            "lani://pair", "https://" + "a".repeat(300) + ".ts.net",
        )) assertEquals(bad, PairProblem.BAD_URL, problem(uri(u = bad)))
        assertEquals("https://node.ts.net:8443", ok(uri(u = "https://node.ts.net:8443/")).url)
        assertEquals("http://10.0.2.2:8791", ok(uri(u = "http://10.0.2.2:8791")).url)
        assertEquals("https://node.ts.net/fluent", ok(uri(u = "https://node.ts.net/fluent")).url)
    }

    @Test fun `anything else is not a pairing code`() {
        for (s in listOf("", "hello", "https://example.com/?v=1", "lani://other?v=1", "fluent:pair?v=1", "LANI://PAIRING?v=1", "lani://pair/x?v=1", "WIFI:S:home;T:WPA;P:secret;;"))
            assertEquals(s, PairProblem.NOT_PAIRING, problem(s))
        assertTrue(PairPayload.parse(uri().replace("lani://pair", "LANI://PAIR")) is PairParse.Ok)
        for (s in listOf("lani:pair?v=1", "fluent://other?v=1", "FLUENT://PAIRING?v=1")) assertEquals(s, PairProblem.NOT_PAIRING, problem(s))
    }

    @Test fun `a code printed before the rename (fluent) still parses`() {
        assertEquals(ok(uri()), ok(uri().replace("lani://pair", "fluent://pair")))
        assertEquals(ok(uri()), ok(uri().replace("lani://pair", "FLUENT://PAIR")))
        assertEquals(PairProblem.INCOMPLETE, problem("fluent://pair?v=1&u=%zz&c=K7Q2M9XPA4TD&p=luka&f=20398153fcf896040daeefddca60749b"))
    }

    @Test fun `every problem says it in Slovene and English`() {
        for (p in PairProblem.entries) assertTrue(p.message, " · " in p.message)
        for (m in listOf(Pairing.WRONG_CODE, Pairing.EXPIRED, Pairing.TOO_MANY, Pairing.OLD_TUTOR, Pairing.UNREACHABLE, Pairing.NOT_THAT_TUTOR, Pairing.GARBLED, Pairing.failed(500), Pairing.otherLearner("Jan")))
            assertTrue(m, " · " in m)
    }

    // --- the bridge's signature ------------------------------------------------------------------------

    /** Signed by the bridge (Node's crypto.sign, ECDSA P-256, SHA-256, DER; "lani-pair/1"): Java must verify it the same way. */
    private val laniVector = PairResponse(
        v = 1,
        token = "fd_" + "q".repeat(43),
        device = PairDevice("d_1234abcd", "Pixel 8"),
        profile = "luka",
        learner = PairLearner("Luka", "Italian", "Slovene", child = true),
        fingerprint = "cf806eceeb6c49078f354b48ed93c614",
        publicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEr2S5s1r7HHgKkl7HY+p8tFRgwV51SVisr9XPGpZr8Yh/8gZt++1jqk0rYq70U86FEcfAbpVP/lEmDd9EuH5RWw==",
        signature = "MEUCIQDavqgmJMUSEJ2xqj1NQZHiXhUwZ0/7TvUNf3dmpN5slAIgD6zJhQ1EnWlSJjf6Ofj9B2Otb+MZ3omEasvSLT5iD0A=",
    )

    /** Signed by a bridge from before the rename ("fluent-pair/1"): still verifies. */
    private val bridgeVector = PairResponse(
        v = 1,
        token = "fd_" + "q".repeat(43),
        device = PairDevice("d_1234abcd", "Pixel 8"),
        profile = "luka",
        learner = PairLearner("Luka", "Italian", "Slovene", child = true),
        fingerprint = "20398153fcf896040daeefddca60749b",
        publicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAERG7uKnYV6tEC0JErDeyXNr8ooxJeDwmuLfKV+sLVxnyX5PerID0M3+bs1sKQrDh/fDFeVD0mbJ9RQ69zqS6r2w==",
        signature = "MEUCIE5FyD9Au+nhXkFHEuNSB5vRmYBbY+DnwBDQrEN5dihRAiEAsX+j/j37cyW7A3qUCjdBewODPxjp2yMmW+ZHIzhVTnQ=",
    )

    @Test fun `a signature made by the bridge verifies`() {
        val p = ok(uri(f = laniVector.fingerprint))
        assertEquals(laniVector.fingerprint, Pairing.fingerprintOf(Base64.getDecoder().decode(laniVector.publicKey)))
        assertTrue(Pairing.verified(laniVector, p))
        assertFalse(Pairing.verified(laniVector.copy(token = laniVector.token + "x"), p))
    }

    @Test fun `a signature made by a bridge from before the rename verifies`() {
        val p = ok(uri())
        assertEquals("20398153fcf896040daeefddca60749b", Pairing.fingerprintOf(Base64.getDecoder().decode(bridgeVector.publicKey)))
        assertTrue(Pairing.verified(bridgeVector, p))
    }

    @Test fun `a different key, profile, code or token does not`() {
        val p = ok(uri())
        assertFalse(Pairing.verified(bridgeVector, p.copy(fingerprint = "00398153fcf896040daeefddca60749b")))
        assertFalse(Pairing.verified(bridgeVector, p.copy(profile = "jan")))
        assertFalse(Pairing.verified(bridgeVector.copy(profile = "jan"), p.copy(profile = "jan")))
        assertFalse(Pairing.verified(bridgeVector, p.copy(code = "K7Q2M9XPA4TE")))
        assertFalse(Pairing.verified(bridgeVector.copy(token = bridgeVector.token + "x"), p))
        assertFalse(Pairing.verified(bridgeVector.copy(signature = "bm9wZQ=="), p))
        assertFalse(Pairing.verified(bridgeVector.copy(publicKey = "not base64!"), p))
    }

    // --- the pairing call, without the camera ------------------------------------------------------------

    /** A bridge of our own: a fresh key, answers like companion/bridge/src/features/pairing.ts. */
    private class FakeBridge(val profile: String = "luka") {
        private val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val spki: ByteArray = keys.public.encoded
        val fingerprint = Pairing.fingerprintOf(spki)
        var status = 200
        var fail: Exception? = null
        val calls = mutableListOf<Pair<String, String>>()

        fun answer(code: String, token: String = "fd_" + "t".repeat(43), signedProfile: String = profile): String {
            val sig = Signature.getInstance("SHA256withECDSA").apply {
                initSign(keys.private)
                update(Pairing.message(signedProfile, code, token).toByteArray())
            }.sign()
            return buildJsonObject {
                put("v", 1)
                put("token", token)
                putJsonObject("device") { put("id", "d_42"); put("name", "Pixel 8") }
                put("profile", profile)
                putJsonObject("learner") { put("name", "Luka"); put("target", "Italian"); put("base", "Slovene"); put("child", true) }
                put("fingerprint", fingerprint)
                put("public_key", Base64.getEncoder().encodeToString(spki))
                put("signature", Base64.getEncoder().encodeToString(sig))
                put("unknown_later_field", "ignored")
            }.toString()
        }

        suspend fun post(url: String, body: String): Pair<Int, String> {
            calls += url to body
            fail?.let { throw it }
            val code = Json.parseToJsonElement(body).jsonObject["code"]!!.jsonPrimitive.content
            return status to if (status == 200) answer(code) else """{"error":"x"}"""
        }
    }

    private fun payloadFor(b: FakeBridge, profile: String = b.profile) = ok(uri(u = "https://node.ts.net:8443", p = profile, f = b.fingerprint))

    @Test fun `pairing trades the code for a device token and says who it paired with`() = runBlocking {
        val b = FakeBridge()
        val out = PairFlow(b::post).pair(payloadFor(b), "Pixel 8")
        assertEquals(
            PairOutcome.Paired(BridgeConfig("https://node.ts.net:8443", "fd_" + "t".repeat(43)), PairedInfo("luka", "Luka", b.fingerprint, "d_42", "Pixel 8", child = true)),
            out,
        )
        val (url, body) = b.calls.single()
        assertEquals("https://node.ts.net:8443/pair", url)
        assertEquals("""{"code":"K7Q2M9XPA4TD","device":"Pixel 8"}""", body)
        assertEquals("✅ Povezano z učiteljem: Luka · Paired with Luka's tutor", Pairing.pairedBanner((out as PairOutcome.Paired).info))
    }

    @Test fun `a wrong, spent, expired or throttled code says so`() = runBlocking {
        val b = FakeBridge()
        val flow = PairFlow(b::post)
        suspend fun failed(status: Int): String {
            b.status = status
            return (flow.pair(payloadFor(b), "x") as PairOutcome.Failed).message
        }
        assertEquals(Pairing.WRONG_CODE, failed(403))
        assertEquals(Pairing.EXPIRED, failed(410))
        assertEquals(Pairing.TOO_MANY, failed(429))
        assertEquals(Pairing.OLD_TUTOR, failed(401))
        assertEquals(Pairing.OLD_TUTOR, failed(404))
        assertEquals(Pairing.failed(500), failed(500))
    }

    @Test fun `an unreachable tutor says to check Tailscale`() = runBlocking {
        val b = FakeBridge().apply { fail = IOException("timeout") }
        assertEquals(PairOutcome.Failed(Pairing.UNREACHABLE), PairFlow(b::post).pair(payloadFor(b), "x"))
    }

    @Test fun `an answer from another bridge or for another learner is refused`() = runBlocking {
        val b = FakeBridge()
        val other = FakeBridge()
        // the QR code names another bridge's key
        assertEquals(PairOutcome.Failed(Pairing.NOT_THAT_TUTOR), PairFlow(b::post).pair(payloadFor(other), "x"))
        // the QR code is for another learner on the same bridge
        assertEquals(PairOutcome.Failed(Pairing.NOT_THAT_TUTOR), PairFlow(b::post).pair(payloadFor(b, profile = "jan"), "x"))
    }

    @Test fun `a garbled answer is refused`() = runBlocking {
        val p = ok(uri())
        assertEquals(PairOutcome.Failed(Pairing.GARBLED), PairFlow { _, _ -> 200 to "<html>hi</html>" }.pair(p, "x"))
        assertEquals(PairOutcome.Failed(Pairing.GARBLED), PairFlow { _, _ -> 200 to """{"token":"t"}""" }.pair(p, "x"))
        val b = FakeBridge()
        val noPrefix = PairFlow { _, body -> 200 to b.answer(Json.parseToJsonElement(body).jsonObject["code"]!!.jsonPrimitive.content, token = "plain-token") }
        assertEquals(PairOutcome.Failed(Pairing.GARBLED), noPrefix.pair(payloadFor(b), "x"))
    }

    // --- one phone, one learner --------------------------------------------------------------------------

    @Test fun `a phone stays with its learner`() {
        val p = ok(uri(u = "https://node.ts.net:8443", p = "luka"))
        val luka = PairedInfo("luka", "Luka", p.fingerprint, "d_1", "Pixel 8")
        assertTrue("first pairing", Pairing.sameLearner(null, null, p))
        assertTrue("same bridge key and profile", Pairing.sameLearner("https://other.name:8443", luka, p))
        assertTrue("same address and profile, the bridge's key made anew", Pairing.sameLearner("https://node.ts.net:8443", luka.copy(fingerprint = "0".repeat(32)), p))
        assertFalse("another profile", Pairing.sameLearner("https://node.ts.net:8443", luka.copy(profile = "ana"), p))
        assertFalse("another bridge elsewhere", Pairing.sameLearner("https://other.name:8443", luka.copy(fingerprint = "0".repeat(32)), p))
        // a typed token: the same address is the same learner
        assertTrue(Pairing.sameLearner("https://NODE.ts.net:8443/", null, p))
        assertTrue(Pairing.sameLearner("https://node.ts.net", null, ok(uri(u = "https://node.ts.net:443"))))
        assertFalse(Pairing.sameLearner("https://node.ts.net", null, p))
        assertFalse(Pairing.sameLearner("http://node.ts.net:8443", null, p))
    }

    @Test fun `origin fills in default ports`() {
        assertEquals("https://node.ts.net:443", Pairing.origin("https://Node.ts.net/"))
        assertEquals("http://10.0.2.2:8791", Pairing.origin("http://10.0.2.2:8791"))
        assertNull(Pairing.origin("::"))
    }
}
