package si.lanisce.lani.sync

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64

/**
 * The handover's QR code round trip: what the node shows (companion/bridge/test/handover-fixture.ts, made by the bridge's
 * own handover.ts) opens on the phone, with the key in it or the typed code; it fails after its time, with a wrong code
 * or another town's fingerprint; the town's key comes out as the bridge has it (the same public key, PKCS#8 and
 * fingerprint), and is kept where the phone's bridge reads it.
 */
class HandoverTest {
    @get:Rule val tmp = TemporaryFolder()

    private val fixture = Json.parseToJsonElement(javaClass.getResourceAsStream("/sync/handover-fixture.json")!!.readBytes().decodeToString()).jsonObject
    private fun f(k: String) = fixture[k]!!.jsonPrimitive.content
    private val before = Instant.ofEpochMilli(fixture["expiresAt"]!!.jsonPrimitive.long).minusSeconds(60)

    private fun opened(uri: String, code: String? = null, now: Instant = before): HandoverOpen {
        val p = Handover.parse(uri)
        assertTrue(p.toString(), p is HandoverParse.Ok)
        return (p as HandoverParse.Ok).code.open(code, now)
    }

    @Test fun `the node's QR code opens with the key in it, and the town's key is the bridge's`() {
        val o = opened(f("uri"))
        assertTrue(o.toString(), o is HandoverOpen.Opened)
        val s = (o as HandoverOpen.Opened).secrets
        assertEquals("Ana", s.bundle.n)
        assertEquals("default", s.bundle.p)
        assertEquals("jan-desktop", s.bundle.h)
        assertEquals("fam-token-0123456789abcdef", s.familyToken)
        assertEquals(RepoRef("ana", "lani-data"), s.repo)
        assertEquals(f("fingerprint"), s.townKey.fingerprint)
        assertArrayEquals(Base64.getDecoder().decode(f("spki")), s.townKey.spki)
        assertArrayEquals(Base64.getDecoder().decode(f("pkcs8")), s.townKey.pkcs8)
    }

    @Test fun `with --code it needs the code the node printed, in any case and with dashes`() {
        val p = (Handover.parse(f("uriWithCode")) as HandoverParse.Ok).code
        assertTrue(p.needsCode)
        assertEquals(HandoverOpen.Failed(HandoverProblem.WRONG_CODE), p.open("AAAA-AAAA-AAAA", before))
        assertEquals(HandoverOpen.Failed(HandoverProblem.WRONG_CODE), p.open("nonsense", before))
        val o = p.open(f("code").lowercase(), before)
        assertTrue(o.toString(), o is HandoverOpen.Opened)
        assertEquals(f("fingerprint"), (o as HandoverOpen.Opened).secrets.townKey.fingerprint)
    }

    @Test fun `it fails after its time, with another fingerprint, or broken`() {
        assertEquals(HandoverOpen.Failed(HandoverProblem.EXPIRED), opened(f("uri"), now = before.plusSeconds(3600)))
        // another fingerprint: the additional data no longer matches
        val other = f("uri").replace("f=${f("fingerprint")}", "f=${"0".repeat(32)}")
        assertEquals(HandoverOpen.Failed(HandoverProblem.BROKEN), opened(other))
        assertEquals(HandoverParse.Invalid(HandoverProblem.NOT_HANDOVER), Handover.parse("lani://pair?v=1&u=x"))
        assertEquals(HandoverParse.Invalid(HandoverProblem.NOT_HANDOVER), Handover.parse("https://example.com"))
        assertEquals(HandoverParse.Invalid(HandoverProblem.NEWER_VERSION), Handover.parse(f("uri").replace("v=1", "v=2")))
        assertEquals(HandoverParse.Invalid(HandoverProblem.INCOMPLETE), Handover.parse("lani://handover?v=1&f=${f("fingerprint")}"))
        assertTrue(Handover.looksLike(f("uri")))
    }

    @Test fun `the secrets are kept where the phone's bridge reads them, never twice for two towns`() = runBlocking {
        val data = tmp.newFolder("data")
        val box = MemoryBox()
        val s = (opened(f("uri")) as HandoverOpen.Opened).secrets
        assertNull(Handover.keep(data, s, box))
        val pem = File(data, "app/town-key.pem").readText()
        assertTrue(pem.startsWith("-----BEGIN PRIVATE KEY-----\n") && pem.trimEnd().endsWith("-----END PRIVATE KEY-----"))
        assertArrayEquals(Base64.getDecoder().decode(f("pkcs8")), Handover.pemBytes(pem))
        assertEquals("fam-token-0123456789abcdef\n", File(data, "app/family-token").readText())
        assertEquals(f("fingerprint"), Handover.townOf(data))
        assertTrue(box.values.getValue(SecretBox.TOWN).contains("\"r\":\"ana/lani-data\""))
        // the sync never pushes them
        val filter = DataFilter.of(data)
        assertFalse(filter.pushes("app/town-key.pem"))
        assertFalse(filter.pushes("app/family-token"))
        // another town on top: refused unless the learner says so
        val other = HandoverSecrets(s.bundle, P256Key.derive(ByteArray(32) { 9 }, "another town"))
        assertEquals(HandoverProblem.OTHER_TOWN, Handover.keep(data, other, box))
        assertEquals(f("fingerprint"), Handover.townOf(data))
        assertNull(Handover.keep(data, other, box, replace = true))
        assertEquals(other.townKey.fingerprint, Handover.townOf(data))
        // the same town again is fine
        assertNull(Handover.keep(data, other, box))
    }

    @Test fun `P-256 from a scalar, the platform's own key pairs agree, and signatures verify`() {
        val gen = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        repeat(5) {
            val pair = gen.generateKeyPair()
            val d = (pair.private as ECPrivateKey).s.toByteArray().let { b -> if (b.size > 32) b.copyOfRange(b.size - 32, b.size) else ByteArray(32 - b.size) + b }
            val k = P256Key.ofScalar(d)
            assertArrayEquals(pair.public.encoded, k.spki)
            val sig = k.sign("hello".toByteArray())
            assertTrue(Signature.getInstance("SHA256withECDSA").run { initVerify(pair.public); update("hello".toByteArray()); verify(sig) })
            assertArrayEquals(k.spki, P256Key.ofPkcs8(k.pkcs8).spki)
        }
        // a derived key is the same every time, and differs by label
        val a = P256Key.derive(ByteArray(32) { 1 }, "x")
        assertArrayEquals(a.spki, P256Key.derive(ByteArray(32) { 1 }, "x").spki)
        assertFalse(a.spki.contentEquals(P256Key.derive(ByteArray(32) { 1 }, "y").spki))
    }

    @Test fun `PBKDF2 as the node computes it`() {
        // RFC 7914 §11's PBKDF2-HMAC-SHA256 vector: P="passwd", S="salt", c=1 (its first 32 bytes)
        val out = Handover.pbkdf2("passwd".toByteArray(), "salt".toByteArray(), 1)
        assertEquals("55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc", out.joinToString("") { "%02x".format(it) })
    }
}
