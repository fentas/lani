package si.lanisce.lani.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Records one spoken answer for the node's recognizer: AAC in an .m4a file, mono, 16 kHz
 * (what Whisper uses), at most [MAX_MS] (or the caller's limit). The file lives in the cache only
 * until it is sent ([Stt.transcribe] deletes it); one that never got sent goes with a later take.
 */
class SpeechRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    /** Keeps the background sounds quiet while it records. */
    private var mic: MicOpen.Hold? = null

    val recording: Boolean get() = recorder != null

    /**
     * Starts recording; [onLimit] runs when the maximum length ([maxMs]) stops it. Throws when the mic can't open.
     * Takes still on their way to the node (a talk sends one sentence while it records the next) are left alone.
     */
    fun start(onLimit: () -> Unit, maxMs: Int = MAX_MS) {
        release()
        // Leftovers of takes that never got sent (the app was closed mid-upload).
        val stale = System.currentTimeMillis() - STALE_MS
        context.cacheDir.listFiles { x -> x.name.startsWith("speech-") && x.name.endsWith(".m4a") && x.lastModified() < stale }?.forEach { it.delete() }
        val f = File(context.cacheDir, "speech-${System.currentTimeMillis()}.m4a")
        val hold = MicOpen.hold() // before it opens: the background sounds are quiet by the first word
        recorder = try {
            try {
                open(f, SAMPLE_RATE, maxMs, onLimit)
            } catch (e: Exception) {
                // Some devices refuse 16 kHz AAC; 44.1 kHz always works (Whisper resamples).
                f.delete()
                open(f, 44_100, maxMs, onLimit)
            }
        } catch (e: Exception) {
            hold.release()
            throw e
        }
        mic = hold
        file = f
    }

    private fun open(f: File, rate: Int, maxMs: Int, onLimit: () -> Unit): MediaRecorder {
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC) // as FamilyRecorder: AGC keeps the levels the gate expects
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(rate)
            r.setAudioEncodingBitRate(if (rate == SAMPLE_RATE) 32_000 else 64_000)
            r.setMaxDuration(maxMs)
            r.setOutputFile(f.path)
            r.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onLimit() }
            r.prepare()
            r.start()
            return r
        } catch (e: Exception) {
            r.release()
            throw e
        }
    }

    /** Loudest sample since the last call (0..32767), for the mic animation and the [SilenceGate]. */
    fun amplitude(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    /** Stops; the take, or null when nothing usable was recorded (it is deleted then). */
    fun stop(): File? {
        val r = recorder ?: return null
        recorder = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        mic?.release()
        mic = null
        val f = file
        file = null
        if (f == null || !ok || f.length() == 0L) {
            f?.delete()
            return null
        }
        return f
    }

    /** Stops without keeping anything. */
    fun release() {
        recorder?.let { runCatching { it.stop() }; it.release() }
        recorder = null
        mic?.release()
        mic = null
        file?.delete()
        file = null
    }

    companion object {
        const val MAX_MS = 20_000
        const val SAMPLE_RATE = 16_000
        /** A take older than this never got sent (an upload gives up within a minute). */
        private const val STALE_MS = 3 * 60_000L
    }
}
