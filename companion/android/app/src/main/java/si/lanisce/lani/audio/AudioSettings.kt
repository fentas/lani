package si.lanisce.lani.audio

import android.content.Context

/**
 * "🔊 Zvok brez povezave · Offline audio": how much of the phone Lani's audio may take, one number for all of it (the day's
 * clips got ready on Wi-Fi, the clips played lately, the car's library: companion/README.md, "Voice", "Offline on the
 * phone"). [OFF]: nothing is got ready ahead; the clips played stay as before (the last [AudioBudget.OFF_CACHE]) and the car's
 * library as big as it gets. [UNLIMITED]: no limit.
 */
enum class AudioCap(val key: String, val bytes: Long?) {
    OFF("off", null),
    MB100("100", 100L * MB),
    MB250("250", 250L * MB),
    MB500("500", 500L * MB),
    UNLIMITED("unlimited", null),
    ;

    /** The day's clips are got ready ahead ([Prefetch]). */
    val prefetch: Boolean get() = this != OFF

    companion object {
        val DEFAULT = MB250

        fun of(key: String?): AudioCap = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

const val MB = 1024L * 1024L

/**
 * This phone's offline audio settings (SharedPreferences "offline-audio", read without waiting: the clip cache trims by them
 * as it plays): the cap, whether the day's clips are got ready only while charging, whether the offline voice is wanted (the
 * prefetch gets it on Wi-Fi then), and whether the offer after pairing was answered.
 */
class AudioSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("offline-audio", Context.MODE_PRIVATE)

    var cap: AudioCap
        get() = AudioCap.of(prefs.getString(CAP, null))
        set(value) = prefs.edit().putString(CAP, value.key).apply()

    var chargingOnly: Boolean
        get() = prefs.getBoolean(CHARGING, false)
        set(value) = prefs.edit().putBoolean(CHARGING, value).apply()

    /** The learner wants the offline voice on the phone: the prefetch gets it (again) on Wi-Fi when it's missing. */
    var voiceWanted: Boolean
        get() = prefs.getBoolean(VOICE, false)
        set(value) = prefs.edit().putBoolean(VOICE, value).apply()

    /** "Prenesi glas brez povezave?" after pairing was answered (either way): it isn't asked again. */
    var voiceOffered: Boolean
        get() = prefs.getBoolean(OFFERED, false)
        set(value) = prefs.edit().putBoolean(OFFERED, value).apply()

    private companion object {
        const val CAP = "cap"
        const val CHARGING = "charging_only"
        const val VOICE = "voice_wanted"
        const val OFFERED = "voice_offered"
    }
}
