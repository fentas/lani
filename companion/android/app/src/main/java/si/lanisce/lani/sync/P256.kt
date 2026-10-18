package si.lanisce.lani.sync

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPrivateKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * An ECDSA P-256 key as the bridge has it (pairing.ts: a town's key; its fingerprint is the town's id): from its private
 * scalar alone, as the handover's QR code carries it, or derived from a secret (the backup's key, sync/Backup.kt). The
 * public point is computed here (JCA can't make it from the scalar), then the usual encodings: SubjectPublicKeyInfo DER
 * (the bridge's `spki`, base64 in X-Lani-Key) and PKCS#8 DER/PEM (what the bridge's createPrivateKey reads).
 */
class P256Key private constructor(val d: BigInteger, val x: BigInteger, val y: BigInteger) {
    /** SubjectPublicKeyInfo DER: the bridge's `spki`. */
    val spki: ByteArray get() = SPKI_PREFIX + point()

    /** The first 16 bytes of SHA-256(spki), hex: the town's id (pairing.ts fingerprintOf). */
    val fingerprint: String get() = MessageDigest.getInstance("SHA-256").digest(spki).take(16).joinToString("") { "%02x".format(it) }

    /** PKCS#8 DER with the public key, as Node exports an EC key (`export({ type: 'pkcs8', format: 'der' })`). */
    val pkcs8: ByteArray get() = PKCS8_PREFIX + fixed(d) + PKCS8_MIDDLE + point()

    /** The PEM the bridge reads (app/town-key.pem). */
    val pem: String get() = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(pkcs8) + "\n-----END PRIVATE KEY-----\n"

    val privateKey: PrivateKey get() = KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(d, PARAMS))
    val publicKey: PublicKey get() = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spki))

    /** An ECDSA signature (DER, SHA-256) of [message]: what Node's verify('sha256', …) takes. */
    fun sign(message: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        initSign(privateKey)
        update(message)
        sign()
    }

    private fun point() = byteArrayOf(4) + fixed(x) + fixed(y)

    companion object {
        val PARAMS: ECParameterSpec = AlgorithmParameters.getInstance("EC").run {
            init(ECGenParameterSpec("secp256r1"))
            getParameterSpec(ECParameterSpec::class.java)
        }
        private val P: BigInteger = (PARAMS.curve.field as java.security.spec.ECFieldFp).p
        private val A: BigInteger = PARAMS.curve.a
        val N: BigInteger = PARAMS.order

        private val SPKI_PREFIX = hex("3059301306072a8648ce3d020106082a8648ce3d030107034200")
        private val PKCS8_PREFIX = hex("308187020100301306072a8648ce3d020106082a8648ce3d030107046d306b0201010420")
        private val PKCS8_MIDDLE = hex("a144034200")

        /** The key of the private scalar [d] (32 bytes, big-endian: a JWK's "d"). */
        fun ofScalar(d: ByteArray): P256Key {
            val k = BigInteger(1, d)
            require(k.signum() > 0 && k < N) { "not a P-256 private key" }
            val (x, y) = multiply(k)
            return P256Key(k, x, y)
        }

        /**
         * A key derived from [seed] (any secret of 32 bytes or more), the same on every device: the scalar is
         * HMAC-SHA256 output stretched to 40 bytes, reduced into 1..n-1 (FIPS 186-4 B.4.1).
         */
        fun derive(seed: ByteArray, label: String): P256Key {
            val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(seed, "HmacSHA256")) }
            val a = mac.doFinal("$label\u0001".toByteArray())
            val b = mac.doFinal(a + "$label\u0002".toByteArray())
            val c = BigInteger(1, (a + b).copyOf(40))
            return ofScalar(fixed(c.mod(N.subtract(BigInteger.ONE)).add(BigInteger.ONE)))
        }

        /** The key of a PKCS#8 DER private key (Node's export, or [pkcs8]): its scalar read, its point computed. */
        fun ofPkcs8(der: ByteArray): P256Key {
            val ec = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(der)) as java.security.interfaces.ECPrivateKey
            return ofScalar(fixed(ec.s))
        }

        /** [k]·G, in affine coordinates (double and add; the scalar is no secret an attacker times here). */
        private fun multiply(k: BigInteger): Pair<BigInteger, BigInteger> {
            var rx: BigInteger? = null
            var ry: BigInteger? = null
            var ax = PARAMS.generator.affineX
            var ay = PARAMS.generator.affineY
            for (i in 0 until k.bitLength()) {
                if (k.testBit(i)) {
                    if (rx == null) { rx = ax; ry = ay } else {
                        val s = add(rx, ry!!, ax, ay)
                        rx = s.first; ry = s.second
                    }
                }
                val dbl = double(ax, ay)
                ax = dbl.first; ay = dbl.second
            }
            return rx!! to ry!!
        }

        private fun add(x1: BigInteger, y1: BigInteger, x2: BigInteger, y2: BigInteger): Pair<BigInteger, BigInteger> {
            if (x1 == x2) return if (y1 == y2) double(x1, y1) else throw ArithmeticException("the point at infinity")
            val l = y2.subtract(y1).multiply(x2.subtract(x1).modInverse(P)).mod(P)
            val x3 = l.multiply(l).subtract(x1).subtract(x2).mod(P)
            return x3 to l.multiply(x1.subtract(x3)).subtract(y1).mod(P)
        }

        private fun double(x: BigInteger, y: BigInteger): Pair<BigInteger, BigInteger> {
            val l = x.multiply(x).multiply(BigInteger.valueOf(3)).add(A).multiply(y.shiftLeft(1).modInverse(P)).mod(P)
            val x3 = l.multiply(l).subtract(x.shiftLeft(1)).mod(P)
            return x3 to l.multiply(x.subtract(x3)).subtract(y).mod(P)
        }

        /** [v] as exactly 32 bytes, big-endian. */
        private fun fixed(v: BigInteger): ByteArray {
            val b = v.toByteArray()
            return when {
                b.size == 32 -> b
                b.size > 32 -> b.copyOfRange(b.size - 32, b.size)
                else -> ByteArray(32 - b.size) + b
            }
        }

        private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
