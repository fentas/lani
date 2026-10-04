package si.lanisce.lani.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.SilenceGate
import si.lanisce.lani.data.SpeechRecorder
import si.lanisce.lani.data.Stt
import si.lanisce.lani.data.SttResult
import si.lanisce.lani.data.SttUnavailable
import si.lanisce.lani.l10n.bi

/**
 * The node's recognizer as a microphone: records the answer (stops by itself after a pause, or
 * on tap), sends it to POST /stt with the expected sentence, and hands back what Whisper heard.
 * The recording is deleted once sent. Needs RECORD_AUDIO; the caller asks for it.
 */
class NodeMic(private val recorder: SpeechRecorder, private val stt: Stt, private val scope: CoroutineScope) {
    var recording by mutableStateOf(false)
        private set
    /** The take is on its way to the node. */
    var thinking by mutableStateOf(false)
        private set
    /** 0..1, for the mic animation. */
    var level by mutableFloatStateOf(0f)
        private set
    private var job: Job? = null
    private var tapped = false

    /** Starts recording; [done] gets the transcript, or null and a message for the learner. */
    fun start(expected: String?, done: (SttResult?, String?) -> Unit) =
        record(SilenceGate(), SpeechRecorder.MAX_MS, send = { stt.transcribe(it, expected) }, done = done)

    /**
     * Records [text] read aloud (data/ReadAloud.kt): a pause of 3 s ends it (a learner reading slowly pauses between
     * words), 28 s at most (the node takes 30); [done] gets what Whisper heard with the text as its prompt and without
     * (null when that second pass failed), or null and a message.
     */
    fun startReading(text: String, done: (Pair<SttResult, SttResult?>?, String?) -> Unit) =
        record(SilenceGate(quietMs = 3_000, giveUpMs = 10_000), READING_MS, send = { stt.transcribeTwice(it, text) }, done = done)

    private fun <T> record(gate: SilenceGate, maxMs: Int, send: suspend (java.io.File) -> T, done: (T?, String?) -> Unit) {
        if (recording || thinking) return
        try {
            recorder.start(onLimit = ::stop, maxMs = maxMs)
        } catch (e: Exception) {
            done(null, "🎤 ${bi("nodeMic.microphoneCantOpenedRight")}")
            return
        }
        recording = true
        tapped = false
        job = scope.launch {
            val t0 = SystemClock.elapsedRealtime()
            var elapsed = 0L
            while (recording) {
                delay(100)
                val a = recorder.amplitude()
                level = (a / 12_000f).coerceIn(0f, 1f)
                elapsed = SystemClock.elapsedRealtime() - t0
                if (gate.feed(a, elapsed)) break
            }
            recording = false
            level = 0f
            val take = recorder.stop()
            // A tap means "I said it" even when the level stayed low; a give-up means nothing came.
            if (take == null || !(gate.started || (tapped && elapsed >= 600))) {
                take?.delete()
                done(null, NOT_HEARD)
                return@launch
            }
            thinking = true
            try {
                done(send(take), null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SttUnavailable) {
                done(null, "📶 ${bi("nodeMic.nodesSpeechRecognitionIsnt")}")
            } catch (e: Exception) {
                done(null, "📶 ${bi("nodeMic.cantReachNodeTry")}")
            } finally {
                thinking = false
            }
        }
    }

    /** Stops recording; what was said so far is sent. */
    fun stop() {
        tapped = true
        recording = false
    }

    fun release() {
        job?.cancel()
        recorder.release()
        recording = false
        thinking = false
    }

    companion object {
        val NOT_HEARD: String get() = "🤔 ${bi("common.iDidntCatchTap")}"

        /** The longest reading aloud in one take: under the node's 30 s. */
        const val READING_MS = 28_000
    }
}

@Composable
fun rememberNodeMic(vm: AppViewModel): NodeMic {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mic = remember { NodeMic(SpeechRecorder(context), vm.stt, scope) }
    DisposableEffect(mic) { onDispose { mic.release() } }
    return mic
}
