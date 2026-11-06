package si.lanisce.lani.data

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * A test's AndroidKeyStore (data/GlasAttest.kt): keys made in software, the same ECDSA P-256 over SHA-256 in DER, and a
 * chain's place (the voice service checks the chain, not the app). [fail]: what the next key's making throws.
 */
class SoftAttestKeys : AttestKeys {
    val pairs = mutableMapOf<String, KeyPair>()
    var fail: Exception? = null

    override fun create(alias: String, challenge: ByteArray): List<ByteArray> {
        fail?.let { throw it }
        pairs[alias] = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        return listOf("leaf for ".toByteArray() + challenge, "root".toByteArray())
    }

    override fun key(alias: String): AttestKey? = pairs[alias]?.let { p ->
        object : AttestKey {
            override val keyId = GlasRequest.sha256hex(p.public.encoded)
            override fun sign(message: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
                initSign(p.private)
                update(message)
                sign()
            }
        }
    }

    override fun delete(alias: String) {
        pairs.remove(alias)
    }
}
