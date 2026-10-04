package si.lanisce.lani.ui.talk

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.Recognizer
import si.lanisce.lani.data.SpeechRecorder
import si.lanisce.lani.data.Stt
import si.lanisce.lani.data.SttResult
import si.lanisce.lani.data.SttUnavailable
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.micAllowed
import java.io.File
import java.io.IOException

/**
 * A [TalkMic] on this phone ([rememberTalkVoice]): the node's Whisper when it is up (the better one for Slovene, and it
 * takes several sentences), else the phone's recognizer in the app, else the system's voice dialog (one sentence); and
 * the microphone permission.
 */
@Stable
class TalkVoice internal constructor(val mic: TalkMic, private val listens: () -> Boolean) {
    internal var begin: (Unclear?) -> Unit = {}
    internal var permit: () -> Unit = {}
    internal var decline: () -> Unit = {}

    val draft: TalkDraft get() = mic.draft

    /** Some recognizer can listen (the node's, the phone's, or its voice dialog); else there is only typing. */
    fun canListen(): Boolean = listens()

    /** 🎤: a take begins, or the one on ends. */
    fun toggle() = if (mic.recording) mic.stop(clock()) else begin(null)

    fun stop() = mic.stop(clock())

    /** "Next sentence". */
    fun next() = mic.next(clock())

    /** The marked word [m], said again. */
    fun sayAgain(m: Unclear) = begin(m)

    /** The explanation's "Allow": the system asks for the microphone. */
    fun allow() = permit()

    /** The explanation's "Not now". */
    fun notNow() = decline()
}

private fun clock() = SystemClock.elapsedRealtime()

/** [TalkEars] on the phone: [SpeechRecorder] and the node's [Stt], and the in-app [SpeechRecognizer]. */
private class PhoneEars(
    context: Context,
    private val stt: Stt,
    private val sr: SpeechRecognizer?,
    private val scope: CoroutineScope,
    private val intent: () -> Intent,
) : TalkEars {
    private val recorder = SpeechRecorder(context)
    private var restart: Job? = null
    /** The take still wants the phone's recognizer: a restart after a sentence checks it. */
    var on: () -> Boolean = { false }

    fun amplitude(): Int = recorder.amplitude()

    override fun record(limitMs: Int) = recorder.start(onLimit = {}, maxMs = limitMs)

    override fun stopRecording(): File? = recorder.stop()

    override suspend fun transcribe(take: File, expected: String?): SttResult = try {
        stt.transcribe(take, expected)
    } catch (e: IOException) {
        if (e !is SttUnavailable) stt.refresh() // unreachable: the phone listens next time, unless the node is up
        throw e
    }

    override fun listen(again: Boolean) {
        restart?.cancel()
        if (!again) {
            sr?.startListening(intent())
            return
        }
        // Straight from a result some recognizers are still busy: a moment later.
        restart = scope.launch {
            delay(RESTART_MS)
            if (on()) sr?.startListening(intent())
        }
    }

    override fun stopListening() {
        sr?.stopListening()
    }

    override fun cancelListening() {
        restart?.cancel()
        sr?.cancel()
    }

    fun release() {
        restart?.cancel()
        recorder.release()
    }

    private companion object {
        const val RESTART_MS = 120L
    }
}

/**
 * The 🎤 of a talk's input, filling [draft]. The node's recognizer listens when it is up (checked when this shows, and
 * again when it can't be reached), else the phone's.
 */
@Composable
fun rememberTalkVoice(vm: AppViewModel, draft: TalkDraft, config: TalkMic.Config = TalkMic.Config()): TalkVoice {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rec = vm.recognizer
    val sr = remember { if (rec.inApp) runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull() else null }
    val ears = remember { PhoneEars(context, vm.stt, sr, scope) { rec.intent(silenceMs = config.longPauseMs) } }
    val mic = remember { TalkMic(scope, ears, draft, config) }
    val phone = { sr != null && rec.support != Recognizer.Support.NO }
    val voice = remember { TalkVoice(mic) { vm.stt.available == true || phone() || rec.dialog } }
    ears.on = { mic.engine == TalkMic.Engine.PHONE }
    val pending = remember { arrayOfNulls<Unclear>(1) } // the word the permission prompt or the voice dialog is for

    LaunchedEffect(Unit) { vm.stt.refresh() } // the node's recognizer may have started or stopped since

    DisposableEffect(sr) {
        sr?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = mic.phoneLevel(rmsdB)
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = mic.phoneLevel(-2f)
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { mic.phonePartial(it, clock()) }
            }
            override fun onResults(results: Bundle?) =
                mic.phoneResults(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty(), clock())
            override fun onError(error: Int) = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> mic.phoneNothing(clock())
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    mic.denied = true
                    mic.phoneFailed("🎤 ${bi("speakExercise.microphoneIsntAllowedLani")}")
                }
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> {
                    rec.reject() // next time the node listens, or Jan types
                    mic.phoneFailed("📵 ${bi("talkInput.speechRecognizerCantDo")}")
                }
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> mic.phoneFailed("📥 ${bi("speakExercise.sloveneIsntDownloadedSpeech")}")
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER,
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> mic.phoneFailed("📶 ${bi("talkInput.speechServiceCantReached")}")
                else -> mic.phoneFailed("⚠️ ${bi("talkInput.recognitionFailedErrorTry", "error" to error)}")
            }
        })
        onDispose {
            mic.cancel()
            ears.release()
            sr?.destroy()
        }
    }

    si.lanisce.lani.ui.MicHold(mic.recording) // the background sounds keep quiet while it listens

    // The level for the node's recording, and the pauses: every 100 ms while the microphone is on.
    LaunchedEffect(mic.recording) {
        while (mic.recording) {
            delay(100)
            mic.tick(clock(), if (mic.engine == TalkMic.Engine.NODE) ears.amplitude() else 0)
        }
    }

    val dialog = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) mic.dialogHeard(r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty(), pending[0])
        pending[0] = null
    }

    fun begin(mark: Unclear?) {
        vm.speaker.stop() // or the recognizer hears the character
        mic.message = null
        val engine = when {
            vm.stt.available == true -> TalkMic.Engine.NODE
            phone() -> TalkMic.Engine.PHONE
            else -> null
        }
        if (engine == TalkMic.Engine.PHONE) vm.stt.refresh() // back to the node as soon as it is up
        when {
            engine != null && micAllowed(context) ->
                if (mark != null) mic.sayAgain(mark, engine, clock()) else mic.start(engine, clock())
            engine != null && !mic.denied -> {
                pending[0] = mark
                mic.asking = true
            }
            // The system's voice dialog needs no permission of ours; it listens for one sentence.
            rec.dialog -> {
                pending[0] = mark
                runCatching { dialog.launch(rec.intent(bi("common.saySlovene"))) }
                    .onFailure { mic.message = "📵 ${bi("speakExercise.phoneHasNoSpeech")}" }
            }
            mic.denied -> mic.message = "🎤 ${bi("speakExercise.microphoneIsntAllowedLani")}"
            else -> mic.message = "📵 ${bi("speakExercise.phoneHasNoSpeech")}"
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        mic.asking = false
        if (!ok) mic.denied = true
        if (ok || rec.dialog) begin(pending[0]) else mic.message = "🎤 ${bi("speakExercise.microphoneIsntAllowedLani")}"
    }

    // Leaving the app while listening: drop the take rather than keep the microphone open in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { mic.cancel() }

    SideEffect {
        voice.begin = ::begin
        voice.permit = { permission.launch(Manifest.permission.RECORD_AUDIO) }
        voice.decline = {
            mic.asking = false
            mic.denied = true
        }
    }
    return voice
}
