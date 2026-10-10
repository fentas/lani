package si.lanisce.lani.audio

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * The phone's own voice for the target language when no clip can be had (offline, the node can't voice the line now): it
 * speaks after the clips and before Android's text-to-speech ([si.lanisce.lani.data.Speaker]), only in its own [language].
 */
interface OfflineVoice {
    /** The language of the voice on the phone (null: none). */
    val language: String?

    /** It can speak [lang] now (its voice is on the phone). */
    fun speaks(lang: String): Boolean = language == lang

    /**
     * [text] spoken into a sound file at [speed] (1 normal; 0.75 for 🐢, which slows the speech itself rather than the
     * playback), or null when it can't. Takes a moment (the model loads the first time): call it off the main thread.
     */
    suspend fun render(text: String, speed: Float): File?
}

/**
 * Piper voices run on the phone by sherpa-onnx (Apache-2.0; its VITS engine with espeak-ng's phonemes): the voice of the
 * target language as [OfflineVoices] keeps it (got through the node, never in the APK). The model loads on the first line
 * (a second or two) and is let go after [IDLE_MS] without one, so its memory (about 100 MB) isn't held all day. What it says
 * is kept as WAV files in the cache (cache/piper-wav, the newest [WAV_BYTES]): a line said again plays at once.
 */
class Piper(context: Context, private val voices: OfflineVoices = OfflineVoices(context.filesDir)) : OfflineVoice {
    private val wavs = File(context.cacheDir, "piper-wav")
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tts: OfflineTts? = null
    private var loaded: String? = null
    private var idle: Job? = null

    /**
     * The language whose voice is wanted: the learner's target language as the app shows it now (a visited town's during a
     * visit: then it speaks only if that one's voice is on the phone), unless set (the car's getting ready: its library's).
     */
    @Volatile
    var target: String? = null

    /** Which language's voice is on the phone, checked again when [refresh] is called (after a download or a removal). */
    @Volatile
    private var installed: Map<String, OfflineVoiceInfo> = emptyMap()

    init {
        refresh() // a few small files: which voice is on the phone
    }

    /** Reads which voices are on the phone again (after a download or a removal). */
    fun refresh() {
        installed = voices.dir.listFiles().orEmpty().filter { it.isDirectory && !it.name.endsWith(".part") }
            .mapNotNull { d -> voices.installed(d.name)?.let { d.name to it } }.toMap()
    }

    override val language: String? get() = (target ?: si.lanisce.lani.l10n.L10n.pair.target.code).takeIf { it in installed }

    override suspend fun render(text: String, speed: Float): File? = withContext(Dispatchers.IO) {
        val lang = language ?: return@withContext null
        val info = installed[lang] ?: return@withContext null
        val out = File(wavs, "${key(info.id, speed, text)}.wav")
        if (out.isFile) {
            out.setLastModified(System.currentTimeMillis())
            return@withContext out
        }
        lock.withLock {
            val engine = engine(lang, info) ?: return@withLock null
            idle?.cancel()
            idle = scope.launch {
                delay(IDLE_MS)
                release()
            }
            try {
                val started = System.currentTimeMillis()
                val audio = engine.generate(text, 0, speed)
                if (audio.samples.isEmpty()) return@withLock null
                val secs = audio.samples.size.toDouble() / audio.sampleRate
                Log.i(TAG, "Piper said ${text.length} characters (%.1f s of speech, speed $speed) in ${System.currentTimeMillis() - started} ms".format(secs))
                wavs.mkdirs()
                val part = File(wavs, "${out.nameWithoutExtension}.part")
                part.writeBytes(wav(louder(audio.samples), audio.sampleRate))
                if (!part.renameTo(out)) return@withLock null
                trim()
                out
            } catch (e: Throwable) {
                Log.w(TAG, "Piper couldn't say it", e)
                null
            }
        }
    }

    /** The engine for [lang], loaded now when another or none is. */
    private fun engine(lang: String, info: OfflineVoiceInfo): OfflineTts? {
        if (loaded == lang) tts?.let { return it }
        tts?.release()
        tts = null
        loaded = null
        val home = voices.folder(lang)
        val inf = info.inference ?: OfflineVoiceInference()
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = File(home, "model.onnx").path,
                    tokens = File(home, "tokens.txt").path,
                    dataDir = File(home, "espeak-ng-data").path,
                    noiseScale = inf.noiseScale,
                    noiseScaleW = inf.noiseW,
                    lengthScale = inf.lengthScale,
                ),
                numThreads = THREADS,
                debug = false,
                provider = "cpu",
            ),
            maxNumSentences = 1,
        )
        return try {
            val started = System.currentTimeMillis()
            OfflineTts(assetManager = null, config = config).also {
                tts = it
                loaded = lang
                Log.i(TAG, "Piper ${info.id} loaded in ${System.currentTimeMillis() - started} ms")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Piper ${info.id} doesn't load", e)
            null
        }
    }

    /** Lets the model go (its memory); the next line loads it again. */
    fun release() {
        scope.launch {
            lock.withLock {
                tts?.release()
                tts = null
                loaded = null
            }
        }
    }

    private fun trim() {
        val files = wavs.listFiles().orEmpty().filter { it.name.endsWith(".wav") }
        val drop = si.lanisce.lani.data.Clips.evict(files.map { Triple(it.name, it.length(), it.lastModified()) }, WAV_BYTES)
        files.filter { it.name in drop }.forEach { it.delete() }
    }

    companion object {
        private const val TAG = "Piper"

        /** The model is let go after this long without a line. */
        const val IDLE_MS = 5 * 60_000L

        /** The lines it said, kept for saying again. */
        const val WAV_BYTES = 20L * MB

        /**
         * Two threads: a line takes a fraction of its length (the x86_64 emulator: 2.5 s of speech in 0.17 s, the model loaded
         * in 0.9 s), and the app stays smooth.
         */
        const val THREADS = 2

        /** 🐢: the speech itself at three quarters, through the voice's length scale, as the clips play at 0.75. */
        const val SLOW = 0.75f

        /**
         * Piper's speech comes out about 3.5 dB under the clips (measured: −21.8 LUFS, the clips' level −18): this much
         * louder, so the offline voice doesn't drop when it takes over a line.
         */
        const val GAIN = 1.5f

        /** The peak it may reach, −1 dBFS. */
        const val CEILING = 0.89f

        /** [samples] (−1 to 1) [GAIN] times louder, less where the peak would go over [CEILING]. */
        fun louder(samples: FloatArray, gain: Float = GAIN): FloatArray {
            val peak = samples.maxOfOrNull { kotlin.math.abs(it) } ?: return samples
            val g = if (peak <= 0f) 1f else minOf(gain, CEILING / peak).coerceAtLeast(minOf(1f, CEILING / peak))
            return if (g == 1f) samples else FloatArray(samples.size) { samples[it] * g }
        }

        /** [samples] as a WAV file: 16-bit PCM, mono, at [rate]. */
        fun wav(samples: FloatArray, rate: Int): ByteArray {
            val data = samples.size * 2
            val b = java.nio.ByteBuffer.allocate(44 + data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
            b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
            b.put("data".toByteArray()).putInt(data)
            for (x in samples) b.putShort((x.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
            return b.array()
        }

        fun key(voice: String, speed: Float, text: String): String =
            MessageDigest.getInstance("SHA-1").digest("$voice|$speed|$text".toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
