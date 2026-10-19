package si.lanisce.lani.phone

import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bridge key's public point (HostCrypto: Q = d·G in Jacobian coordinates, the phone's bridge reads its key at every
 * boot): the same point Java made with the key, for many keys; quick enough to do at a boot.
 */
class HostCryptoPointTest {
    @Test
    fun thePublicPointIsJavas() {
        val gen = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        repeat(40) {
            val kp = gen.generateKeyPair()
            val d = (kp.private as ECPrivateKey)
            val q = HostCrypto.multiply(d.params, d.s)
            assertEquals((kp.public as ECPublicKey).w, q)
            val pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(kp.private.encoded) + "\n-----END PRIVATE KEY-----\n"
            assertTrue(HostCrypto.ecPublic(pem).contentEquals(kp.public.encoded))
        }
    }

    @Test
    fun itIsQuick() {
        val kp = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val d = kp.private as ECPrivateKey
        repeat(5) { HostCrypto.multiply(d.params, d.s) } // warmed up
        val t0 = System.nanoTime()
        repeat(20) { HostCrypto.multiply(d.params, d.s) }
        val ms = (System.nanoTime() - t0) / 1e6 / 20
        println("Q = d·G: ${"%.2f".format(ms)} ms")
        assertTrue("Q = d·G took $ms ms", ms < 50 * (System.getProperty("lani.perfScale")?.toDoubleOrNull() ?: 1.0))
    }
}
