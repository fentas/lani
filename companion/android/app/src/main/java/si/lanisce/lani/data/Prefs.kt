package si.lanisce.lani.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.store by preferencesDataStore("lani")

class Prefs(private val context: Context) {
    private val url = stringPreferencesKey("bridge_url")
    private val token = stringPreferencesKey("bridge_token")
    private val lastEvent = longPreferencesKey("last_event_id")
    private val seen = stringSetPreferencesKey("seen_modules") // "id:version"
    private val notified = longPreferencesKey("notified_event_id") // highest event the user has seen or been notified about
    private val reminderDay = stringPreferencesKey("last_reminder_day")
    private val gameIntro = booleanPreferencesKey("game_intro_seen")
    private val gameEventNotified = stringPreferencesKey("game_event_notified")
    private val chestUnseen = stringSetPreferencesKey("chest_unseen") // what came into the chest since it was opened: "good:potica", "tool:luka"

    private val plan = stringPreferencesKey("today_plan") // "yyyy-MM-dd\n<markdown>"

    /** The tutor's plan for [today], if it was written today. */
    suspend fun todayPlan(today: String): String? =
        context.store.data.first()[plan]?.takeIf { it.startsWith("$today\n") }?.substringAfter('\n')

    suspend fun saveTodayPlan(today: String, text: String) = context.store.edit { it[plan] = "$today\n$text" }

    suspend fun config(): BridgeConfig? {
        val p = context.store.data.first()
        val u = p[url] ?: return null
        val t = p[token] ?: return null
        return BridgeConfig(u, t)
    }

    // Paired by QR code (data/Pairing.kt): who with, the bridge's key, this phone's device name.
    private val pairedProfile = stringPreferencesKey("paired_profile")
    private val pairedLearner = stringPreferencesKey("paired_learner")
    private val pairedFingerprint = stringPreferencesKey("paired_fingerprint")
    private val pairedDeviceId = stringPreferencesKey("paired_device_id")
    private val pairedDeviceName = stringPreferencesKey("paired_device_name")
    private val pairedChild = booleanPreferencesKey("paired_child")
    private val pairedKeys = listOf(pairedProfile, pairedLearner, pairedFingerprint, pairedDeviceId, pairedDeviceName)

    /** A typed URL and token: whatever was paired before no longer applies. */
    suspend fun saveConfig(c: BridgeConfig) = context.store.edit {
        it[url] = c.baseUrl
        it[token] = c.token
        pairedKeys.forEach(it::remove)
        it.remove(pairedChild)
    }

    /** The connection and who it pairs with, in one write. */
    suspend fun savePairing(c: BridgeConfig, info: PairedInfo) = context.store.edit {
        it[url] = c.baseUrl
        it[token] = c.token
        it[pairedProfile] = info.profile
        it[pairedLearner] = info.learner
        it[pairedFingerprint] = info.fingerprint
        it[pairedDeviceId] = info.deviceId
        it[pairedDeviceName] = info.deviceName
        it[pairedChild] = info.child
    }

    /** Who this phone paired with by QR code; null when it was set up with a typed token. */
    suspend fun pairing(): PairedInfo? {
        val p = context.store.data.first()
        return PairedInfo(
            profile = p[pairedProfile] ?: return null,
            learner = p[pairedLearner].orEmpty(),
            fingerprint = p[pairedFingerprint] ?: return null,
            deviceId = p[pairedDeviceId].orEmpty(),
            deviceName = p[pairedDeviceName].orEmpty(),
            child = p[pairedChild] ?: false,
        )
    }
    suspend fun lastEventId(): Long = context.store.data.first()[lastEvent] ?: 0
    suspend fun saveLastEventId(id: Long) = context.store.edit { it[lastEvent] = id }
    suspend fun seenModules(): Set<String> = context.store.data.first()[seen] ?: emptySet()
    suspend fun notifiedEventId(): Long = context.store.data.first()[notified] ?: 0
    suspend fun saveNotifiedEventId(id: Long) = context.store.edit { if (id > (it[notified] ?: 0)) it[notified] = id }
    suspend fun lastReminderDay(): String? = context.store.data.first()[reminderDay]
    suspend fun saveLastReminderDay(day: String) = context.store.edit { it[reminderDay] = day }
    suspend fun gameIntroSeen(): Boolean = context.store.data.first()[gameIntro] ?: false
    suspend fun saveGameIntroSeen() = context.store.edit { it[gameIntro] = true }
    /** Id of the last village event a notification was posted for. */
    suspend fun gameEventNotified(): String? = context.store.data.first()[gameEventNotified]
    suspend fun saveGameEventNotified(id: String) = context.store.edit { it[gameEventNotified] = id }
    suspend fun chestUnseen(): Set<String> = context.store.data.first()[chestUnseen] ?: emptySet()
    suspend fun saveChestUnseen(keys: Set<String>) = context.store.edit { it[chestUnseen] = keys }
    suspend fun markSeen(key: String) = context.store.edit { it[seen] = (it[seen] ?: emptySet()) + key }
}
