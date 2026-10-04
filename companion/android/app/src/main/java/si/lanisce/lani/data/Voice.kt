package si.lanisce.lani.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.text.Normalizer
import java.util.Locale

/** One family recording (GET /audio/index). */
@Serializable
data class Recording(
    val file: String,
    val speaker: String,
    @SerialName("recorded_at") val recordedAt: String = "",
    val text: String = "",
)

/**
 * Family voices: plays a family member's recording of a Slovene text when one is on the device.
 * [Speaker.say] asks here first, then node clips ([Clips], played here too), then text-to-speech.
 */
class Voice(context: Context) {
    val dir: File = File(context.cacheDir, "family-voices").apply { mkdirs() }

    /**
     * Normalized text → recordings, from the node (cached on disk for offline starts). Compose state,
     * written on the main thread only; the disk copy arrives through [loadCached].
     */
    var index by mutableStateOf<Map<String, List<Recording>>>(emptyMap())
        private set
    /** The node's index arrived, so the (older) disk copy must not overwrite it. */
    private var fresh = false

    /** The recording playing now, for the "👵 Micka" badge. */
    var playing by mutableStateOf<Recording?>(null)
        private set

    /** Asked to fetch recordings that are in the index but not on the device yet. */
    var onMissing: (() -> Unit)? = null

    private var player: MediaPlayer? = null

    /** A recording or a clip is playing (or about to): the background sounds duck under it. Read from any thread. */
    @Volatile
    var sounding = false
        private set

    /** Reads the index cached on disk, off the main thread, unless the node's came first. */
    suspend fun loadCached() {
        val cached = withContext(Dispatchers.IO) { loadIndex() }
        if (!fresh) index = cached
    }

    /** Takes the node's index; call it on the main thread. Throws on a malformed index, which is then ignored. */
    suspend fun updateIndex(raw: String) {
        index = withContext(Dispatchers.Default) { parseIndex(raw) }
        fresh = true
        withContext(Dispatchers.IO) { runCatching { File(dir, INDEX).writeText(raw) } }
    }

    private fun loadIndex(): Map<String, List<Recording>> =
        runCatching { parseIndex(File(dir, INDEX).readText()) }.getOrDefault(emptyMap())

    fun file(r: Recording) = File(dir, r.file)
    fun has(text: String) = resolve(index, text) != null

    /** A recording for [text] is on the device; asks for missing downloads otherwise. */
    fun ready(text: String): Boolean {
        val recs = resolve(index, text) ?: return false
        if (recs.all { file(it).exists() }) return true
        onMissing?.invoke()
        return false
    }

    /**
     * Plays the family recording(s) for [text]. False when there is none on the device, so the caller
     * speaks it with TTS; [fallback] runs if a recording turns out unplayable.
     */
    fun play(text: String, slow: Boolean = false, fallback: () -> Unit = {}): Boolean {
        val recs = resolve(index, text) ?: return false
        if (!recs.all { file(it).exists() }) {
            onMissing?.invoke()
            return false
        }
        stop()
        playAt(recs, 0, slow, fallback)
        return true
    }

    /**
     * Plays node clips one after another (no badge); slow is [CLIP_SLOW]× speed. [pitch] and [rate] are someone's
     * own for a shared voice (see [VoiceProfile]); 1 leaves the clip as it is.
     */
    fun playClips(files: List<File>, slow: Boolean, pitch: Float = 1f, rate: Float = 1f, fallback: () -> Unit) {
        stop()
        val speed = (if (slow) CLIP_SLOW else 1f) * rate
        fun at(i: Int) {
            start(files[i], null, speed.takeIf { it != 1f }, onDone = { if (i + 1 < files.size) at(i + 1) }, onError = fallback, pitch = pitch)
        }
        if (files.isNotEmpty()) at(0)
    }

    /** Plays one recording or a fresh take (not in the index yet). */
    fun playFile(f: File, rec: Recording? = null, onError: () -> Unit = {}) {
        stop()
        start(f, rec, speed = null, onDone = { playing = null }, onError = onError)
    }

    private fun playAt(recs: List<Recording>, i: Int, slow: Boolean, fallback: () -> Unit) {
        start(file(recs[i]), recs[i], if (slow) FAMILY_SLOW else null, onDone = {
            if (i + 1 < recs.size) playAt(recs, i + 1, slow, fallback) else playing = null
        }, onError = fallback)
    }

    private fun start(f: File, rec: Recording?, speed: Float?, onDone: () -> Unit, onError: () -> Unit, pitch: Float = 1f) {
        val mp = MediaPlayer()
        player = mp
        playing = rec
        sounding = true
        mp.setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
        )
        mp.setOnPreparedListener {
            // Setting a speed (or a pitch) on a prepared player starts it.
            if ((speed == null && pitch == 1f) || runCatching { it.playbackParams = it.playbackParams.setSpeed(speed ?: 1f).setPitch(pitch) }.isFailure) it.start()
        }
        mp.setOnCompletionListener {
            it.release()
            if (player === it) {
                player = null
                sounding = false // the next clip of a line sets it again at once
                onDone()
            }
        }
        mp.setOnErrorListener { p, _, _ ->
            p.release()
            if (player === p) {
                player = null
                playing = null
                sounding = false
                onError()
            }
            true
        }
        try {
            mp.setDataSource(f.path)
            mp.prepareAsync()
        } catch (e: Exception) {
            mp.release()
            player = null
            playing = null
            sounding = false
            onError()
        }
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        playing = null
        sounding = false
    }

    companion object {
        private const val INDEX = "index.json"
        private const val FAMILY_SLOW = 0.7f
        private const val CLIP_SLOW = 0.75f
        private val sl = Locale.forLanguageTag("sl")
        private val notWord = Regex("[^\\p{L}\\p{N}\\p{Z}\\s]")
        private val space = Regex("[\\p{Z}\\s]+")
        private val splitter = Regex("\\s*(?:/|→)\\s*")

        /** "Dober dan!" → "dober dan". Same rule as the bridge's normalizeText (family.ts). */
        fun normalize(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFC).lowercase(sl).replace(notWord, "").replace(space, " ").trim()

        /** "Kako ste? / Kako si?" → both phrases, as the bridge lists them for recording. */
        fun phrases(s: String): List<String> =
            s.split(splitter).map { it.trim().removeSuffix("...").removeSuffix("…").trim() }.filter { normalize(it).isNotEmpty() }

        /**
         * The recordings to play for [text]: one for the whole text, or one per phrase when every
         * phrase of "a / b" has one. Null when there is no family voice for it.
         */
        fun resolve(
            index: Map<String, List<Recording>>,
            text: String,
            pick: (List<Recording>) -> Recording = { it.random() },
        ): List<Recording>? {
            index[normalize(text)]?.takeIf { it.isNotEmpty() }?.let { return listOf(pick(it)) }
            val parts = phrases(text)
            if (parts.size < 2) return null
            return parts.map { p -> index[normalize(p)]?.takeIf { it.isNotEmpty() }?.let(pick) ?: return null }
        }

        fun parseIndex(raw: String): Map<String, List<Recording>> = json.decodeFromString(raw)

        /** "👵 Micka" stays as it is; a bare name gets a speaking head. */
        fun badge(speaker: String): String {
            val s = speaker.trim()
            if (s.isEmpty()) return "🗣️"
            return if (Character.isLetterOrDigit(s.codePointAt(0))) "🗣️ $s" else s
        }
    }
}
