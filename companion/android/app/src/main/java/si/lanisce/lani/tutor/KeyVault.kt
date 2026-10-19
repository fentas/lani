package si.lanisce.lani.tutor

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts and decrypts the key's bytes (AES-GCM; the Keystore's key on the phone, a plain one in the tests). */
interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(sealed: ByteArray): ByteArray
}

/** Where the ciphertext is kept (DataStore on the phone, memory in the tests). */
interface SecretStore {
    suspend fun read(): String?
    suspend fun write(value: String?)
}

/**
 * AES-GCM with a key in the Android Keystore that never leaves it (plan 5, "The API key"; Jetpack Security's
 * EncryptedSharedPreferences is deprecated, so the Keystore directly). The sealed bytes are the 12-byte IV, then the
 * ciphertext with its tag.
 */
class KeystoreCipher(private val alias: String = ALIAS) : SecretCipher {
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return c.iv + c.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV)) }
        return c.doFinal(sealed, IV, sealed.size - IV)
    }

    /** Takes the Keystore's key away too (the key removed in the panel). */
    fun forget() = runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias) }

    companion object {
        const val ALIAS = "lani-tutor-api-key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV = 12
    }
}

private val Context.tutorSecrets by preferencesDataStore("tutor_secrets")

/**
 * The ciphertext in a DataStore of its own (the app's `allowBackup` is false), under [name]: the Claude key's
 * "api_key_sealed", the OpenAI key's and the ChatGPT sign-in's their own ([OpenAICredentials.android]).
 */
class DataStoreSecretStore(private val context: Context, name: String = "api_key_sealed") : SecretStore {
    private val sealed = stringPreferencesKey(name)
    override suspend fun read(): String? = context.tutorSecrets.data.first()[sealed]
    override suspend fun write(value: String?) {
        context.tutorSecrets.edit { if (value == null) it.remove(sealed) else it[sealed] = value }
    }
}

/**
 * A secret of the learner's, stored encrypted ([cipher], [store]); decrypted in memory for a call only: the Claude API
 * key, the OpenAI API key, the ChatGPT sign-in's tokens ([ChatGPTAuth], as JSON). Never in a log, an export, a prompt
 * or a tool result ([Redact] cleans what could carry it); removable.
 */
class KeyVault(private val cipher: SecretCipher, private val store: SecretStore) {
    suspend fun save(key: String) {
        val clean = key.trim()
        require(clean.isNotEmpty()) { "an empty key" }
        store.write(Base64.getEncoder().encodeToString(cipher.encrypt(clean.toByteArray())))
    }

    /** The key, or null when none is kept (or it can't be decrypted any more: a new phone, a reset Keystore). */
    suspend fun load(): String? = store.read()?.let { s ->
        runCatching { cipher.decrypt(Base64.getDecoder().decode(s)).decodeToString() }.getOrNull()
    }

    suspend fun has(): Boolean = store.read() != null

    suspend fun remove() {
        store.write(null)
        (cipher as? KeystoreCipher)?.forget()
    }
}

/**
 * Takes anything shaped like an API key out of a text bound for a log line, the panel or the export ([of]); and, for
 * what goes to the node (the export, the app's log: app/DevUpload), anything that could carry a secret ([all], [json]).
 */
object Redact {
    private val keyLike = Regex("""sk-ant-[A-Za-z0-9_\-]{6,}""")
    /** An OpenAI API key (sk-proj-…, sk-svcacct-…, sk-…) or OpenRouter's (sk-or-v1-…), after Anthropic's are out. */
    private val openAIKey = Regex("""\bsk-(?!ant-)[A-Za-z0-9_\-]{16,}""")
    /** A Google API key (a Gemini key from Google AI Studio: AIza and 35 more). */
    private val googleKey = Regex("""\bAIza[0-9A-Za-z_\-]{35}""")
    /** A JSON Web Token (the ChatGPT sign-in's access and ID tokens): three base64url parts, the first a JSON header. */
    private val jwt = Regex("""\beyJ[A-Za-z0-9_\-]{6,}\.[A-Za-z0-9_\-]{6,}\.[A-Za-z0-9_\-]{6,}""")
    /** A name that says a secret is in its value: a token, an API key, a password (not a count of tokens: input_tokens). */
    private const val SECRET_NAME = """[\w-]*(?:token(?!s)|api[_-]?key|secret|passw(?:or)?d|authorization|cookie)[\w-]*"""
    /** A secret's name, then its value: token=…, "api_key": "…", Authorization: Bearer …, x-api-key: …. */
    private val named = Regex("""\b($SECRET_NAME)(\s*["']?\s*[=:]\s*["']?\s*)(?:(?:Bearer|Basic|Token)\s+)?([^\s&#"',;<>()\[\]\{\}]+)""", RegexOption.IGNORE_CASE)
    private val secretKey = Regex("^$SECRET_NAME$", RegexOption.IGNORE_CASE)
    private val bearer = Regex("""\bBearer\s+[A-Za-z0-9._~+/=\-]{6,}""", RegexOption.IGNORE_CASE)
    /** A URL's key, signature or code (a token's own name is [named]'s). */
    private val query = Regex("""([?&](?:key|sig|signature|code|auth)=)[^&\s#"']+""", RegexOption.IGNORE_CASE)

    fun of(text: String, key: String? = null): String {
        var t = text
        if (!key.isNullOrBlank() && key.length >= 8) t = t.replace(key, "‹key›")
        t = keyLike.replace(t, "sk-ant-‹…›")
        t = openAIKey.replace(t, "sk-‹…›")
        t = googleKey.replace(t, "AIza‹…›")
        return jwt.replace(t, "‹token›")
    }

    /**
     * [text] cleaned of what could carry a secret: the [known] values (the API keys, the sign-in's tokens, the device
     * token), API keys (sk-ant-…, OpenAI's and OpenRouter's sk-…, Google's AIza…), JSON Web Tokens (eyJ….….…), Bearer tokens, the value after a secret's name (token=…, x-api-key: …, Authorization: …, "api_key": "…")
     * and a URL's key, sig or code. Counts of tokens (input_tokens: 12) stay.
     */
    fun all(text: String, known: Collection<String?> = emptyList()): String {
        var t = text
        for (k in known) if (!k.isNullOrBlank() && k.length >= 8) t = t.replace(k, "‹secret›")
        t = keyLike.replace(t, "sk-ant-‹…›")
        t = openAIKey.replace(t, "sk-‹…›")
        t = googleKey.replace(t, "AIza‹…›")
        t = jwt.replace(t, "‹token›")
        t = bearer.replace(t, "Bearer ‹…›")
        t = named.replace(t) { m -> m.groupValues[1] + m.groupValues[2] + "‹…›" }
        return query.replace(t) { m -> m.groupValues[1] + "‹…›" }
    }

    /** [e] with every string cleaned ([all]) and the string of a key named like a secret ("token", "api_key") replaced; numbers stay. */
    fun json(e: JsonElement, known: Collection<String?> = emptyList()): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.mapValues { (k, v) -> if (v is JsonPrimitive && v.isString && secretKey.matches(k)) JsonPrimitive("‹…›") else json(v, known) })
        is JsonArray -> JsonArray(e.map { json(it, known) })
        is JsonPrimitive -> if (e.isString) JsonPrimitive(all(e.content, known)) else e
    }
}
