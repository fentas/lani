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

    // --- d·G on a prime curve (affine, double and add): once per key, so plain BigInteger arithmetic does ---------------

    private fun multiply(params: ECParameterSpec, d: BigInteger): ECPoint {
        val p = (params.curve.field as ECFieldFp).p
        val a = params.curve.a
        var result: ECPoint = ECPoint.POINT_INFINITY
        var addend = params.generator
        for (i in 0 until d.bitLength()) {
            if (d.testBit(i)) result = add(result, addend, a, p)
            addend = add(addend, addend, a, p)
        }
        return result
    }

    private fun add(s: ECPoint, t: ECPoint, a: BigInteger, p: BigInteger): ECPoint {
        if (s == ECPoint.POINT_INFINITY) return t
        if (t == ECPoint.POINT_INFINITY) return s
        val (x1, y1) = s.affineX to s.affineY
        val (x2, y2) = t.affineX to t.affineY
        val lambda = if (x1 == x2) {
            if ((y1 + y2).mod(p) == BigInteger.ZERO) return ECPoint.POINT_INFINITY
            (x1.pow(2).multiply(BigInteger.valueOf(3)) + a).multiply((y1.shiftLeft(1)).modInverse(p)).mod(p)
        } else {
            (y2 - y1).multiply((x2 - x1).modInverse(p)).mod(p)
        }
        val x3 = (lambda.pow(2) - x1 - x2).mod(p)
        val y3 = (lambda.multiply(x1 - x3) - y1).mod(p)
        return ECPoint(x3, y3)
    }
}
