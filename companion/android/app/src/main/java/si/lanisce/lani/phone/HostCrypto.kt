package si.lanisce.lani.phone

import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The crypto node:crypto gives the bridge (companion/bridge/phone/shims/crypto.ts), in Java's: digests and HMACs,
 * random bytes, and the bridge's EC P-256 key (pairing.ts, towns.ts): made as PKCS#8 PEM, its public key as
 * SubjectPublicKeyInfo DER, ECDSA signatures with SHA-256 in DER (node's sign('sha256', …), Java's SHA256withECDSA:
 * the same format, so the app and other towns verify either). A key read once is kept (by its PEM).
 */
object HostCrypto {
    private val random = SecureRandom()
    private val privates = ConcurrentHashMap<String, PrivateKey>()
    private val publics = ConcurrentHashMap<String, ByteArray>()

    private val DIGESTS = mapOf("sha256" to "SHA-256", "sha1" to "SHA-1", "sha512" to "SHA-512", "md5" to "MD5")
    private val HMACS = mapOf("sha256" to "HmacSHA256", "sha1" to "HmacSHA1", "sha512" to "HmacSHA512", "md5" to "HmacMD5")

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }

    fun hash(alg: String, data: ByteArray): ByteArray =
        MessageDigest.getInstance(DIGESTS[alg] ?: throw IllegalArgumentException("Digest method not supported: $alg")).digest(data)

    fun hmac(alg: String, key: ByteArray, data: ByteArray): ByteArray {
        val name = HMACS[alg] ?: throw IllegalArgumentException("Digest method not supported: $alg")
        // an empty key is a valid HMAC key in node; Java's SecretKeySpec refuses one: a zero byte hashes the same
        val k = if (key.isEmpty()) ByteArray(1) else key
        return Mac.getInstance(name).apply { init(SecretKeySpec(k, name)) }.doFinal(data)
    }

    /** A new P-256 private key, PKCS#8 PEM. */
    fun ecGenerate(): String {
        val g = KeyPairGenerator.getInstance("EC")
        g.initialize(ECGenParameterSpec("secp256r1"), random)
        val kp = g.generateKeyPair()
        val pem = pem("PRIVATE KEY", kp.private.encoded)
        privates[pem] = kp.private
        publics[pem] = kp.public.encoded
        return pem
    }

    private fun pem(label: String, der: ByteArray): String =
        "-----BEGIN $label-----\n" + Base64.getEncoder().encodeToString(der).chunked(64).joinToString("\n") + "\n-----END $label-----\n"

    private fun derOf(pem: String): ByteArray =
        Base64.getDecoder().decode(pem.replace(Regex("-----[^-]+-----"), "").replace(Regex("\\s+"), ""))

    private fun privateOf(pem: String): PrivateKey = privates.getOrPut(pem) {
        if (!pem.contains("-----BEGIN PRIVATE KEY-----")) throw IllegalArgumentException("only PKCS#8 private keys are read")
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(derOf(pem)))
    }

    /** The public key of [pem], SPKI DER: Q = d·G on its curve (Java reads a private key without its public point). */
    fun ecPublic(pem: String): ByteArray = publics.getOrPut(pem) {
        val key = privateOf(pem) as ECPrivateKey
        val q = multiply(key.params, key.s)
        KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(q, key.params)).encoded
    }

    fun ecSign(pem: String, data: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(privateOf(pem), random)
            update(data)
            sign()
        }

    fun ecVerify(spki: ByteArray, data: ByteArray, signature: ByteArray): Boolean = try {
        val key: PublicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spki))
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(data)
            verify(signature)
        }
    } catch (_: Exception) {
        false // a malformed key or signature verifies nothing, as node's verify answers false
    }

    // --- d·G on a prime curve: once per key, in Jacobian coordinates (no inverse per step: the one at the end) ---------
    // Affine double-and-add took one modular inverse per step, about 2 s for a P-256 key on the emulator (the phone's
    // bridge reads its key at every boot); this takes a few milliseconds.

    /** A point (X/Z², Y/Z³); Z = 0 is the point at infinity. */
    private class Jacobian(val x: BigInteger, val y: BigInteger, val z: BigInteger) {
        val infinite: Boolean get() = z.signum() == 0
    }

    private val INFINITY = Jacobian(BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)
    private val TWO = BigInteger.valueOf(2)
    private val THREE = BigInteger.valueOf(3)
    private val EIGHT = BigInteger.valueOf(8)

    internal fun multiply(params: ECParameterSpec, d: BigInteger): ECPoint {
        val p = (params.curve.field as ECFieldFp).p
        val a = params.curve.a.mod(p)
        val g = params.generator
        var result = INFINITY
        var addend = Jacobian(g.affineX, g.affineY, BigInteger.ONE)
        for (i in 0 until d.bitLength()) {
            if (d.testBit(i)) result = add(result, addend, a, p)
            addend = double(addend, a, p)
        }
        if (result.infinite) return ECPoint.POINT_INFINITY
        val zInv = result.z.modInverse(p)
        val zInv2 = zInv.multiply(zInv).mod(p)
        return ECPoint(result.x.multiply(zInv2).mod(p), result.y.multiply(zInv2).multiply(zInv).mod(p))
    }

    private fun double(s: Jacobian, a: BigInteger, p: BigInteger): Jacobian {
        if (s.infinite || s.y.signum() == 0) return INFINITY
        val y2 = s.y.multiply(s.y).mod(p)
        val sv = s.x.multiply(y2).shiftLeft(2).mod(p) // 4·X·Y²
        val z2 = s.z.multiply(s.z).mod(p)
        val m = s.x.multiply(s.x).multiply(THREE).add(a.multiply(z2.multiply(z2))).mod(p) // 3·X² + a·Z⁴
        val x3 = m.multiply(m).subtract(sv.multiply(TWO)).mod(p)
        val y3 = m.multiply(sv.subtract(x3)).subtract(y2.multiply(y2).multiply(EIGHT)).mod(p)
        val z3 = s.y.multiply(s.z).shiftLeft(1).mod(p)
        return Jacobian(x3, y3, z3)
    }

    private fun add(s: Jacobian, t: Jacobian, a: BigInteger, p: BigInteger): Jacobian {
        if (s.infinite) return t
        if (t.infinite) return s
        val z1s = s.z.multiply(s.z).mod(p)
        val z2s = t.z.multiply(t.z).mod(p)
        val u1 = s.x.multiply(z2s).mod(p)
        val u2 = t.x.multiply(z1s).mod(p)
        val s1 = s.y.multiply(z2s).multiply(t.z).mod(p)
        val s2 = t.y.multiply(z1s).multiply(s.z).mod(p)
        if (u1 == u2) return if (s1 == s2) double(s, a, p) else INFINITY
        val h = u2.subtract(u1).mod(p)
        val r = s2.subtract(s1).mod(p)
        val h2 = h.multiply(h).mod(p)
        val h3 = h2.multiply(h).mod(p)
        val u1h2 = u1.multiply(h2).mod(p)
        val x3 = r.multiply(r).subtract(h3).subtract(u1h2.multiply(TWO)).mod(p)
        val y3 = r.multiply(u1h2.subtract(x3)).subtract(s1.multiply(h3)).mod(p)
        val z3 = h.multiply(s.z).multiply(t.z).mod(p)
        return Jacobian(x3, y3, z3)
    }
}
