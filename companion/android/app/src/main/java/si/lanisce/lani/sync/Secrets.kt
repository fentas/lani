package si.lanisce.lani.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import si.lanisce.lani.tutor.KeystoreCipher
import si.lanisce.lani.tutor.SecretCipher
import java.util.Base64

/**
 * The data's secrets on the phone, each sealed by a key in the Android Keystore (as the tutor's API key, tutor/KeyVault):
 * the GitHub token ([GIT_TOKEN]), the backup's master secret ([BACKUP_MASTER]), the town's secrets from a handover
 * ([TOWN]). Never in a log, the data directory's sync or a backup's clear text.
 */
interface SecretBox {
    suspend fun get(name: String): String?
    suspend fun put(name: String, value: String?)

    companion object {
        const val GIT_TOKEN = "git_token"
        const val BACKUP_MASTER = "backup_master"
        const val TOWN = "town"
    }
}

private val Context.dataSecrets by preferencesDataStore("data_secrets")

/** [SecretBox] on the phone: AES-GCM under a Keystore key that never leaves it, the ciphertext in a DataStore of its own. */
class KeystoreBox(private val context: Context, private val cipher: SecretCipher = KeystoreCipher(ALIAS)) : SecretBox {
    override suspend fun get(name: String): String? = context.dataSecrets.data.first()[stringPreferencesKey(name)]?.let { s ->
        runCatching { cipher.decrypt(Base64.getDecoder().decode(s)).decodeToString() }.getOrNull()
    }

    override suspend fun put(name: String, value: String?) {
        val key = stringPreferencesKey(name)
        context.dataSecrets.edit { if (value == null) it.remove(key) else it[key] = Base64.getEncoder().encodeToString(cipher.encrypt(value.toByteArray())) }
    }

    companion object {
        const val ALIAS = "lani-data-secrets"
    }
}

/** [SecretBox] in memory: the tests'. */
class MemoryBox : SecretBox {
    val values = HashMap<String, String>()
    override suspend fun get(name: String): String? = values[name]
    override suspend fun put(name: String, value: String?) {
        if (value == null) values.remove(name) else values[name] = value
    }
}
