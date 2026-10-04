package si.lanisce.lani.ui.scene

import android.Manifest
import android.app.Activity
import android.os.Bundle
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.Recognizer
import si.lanisce.lani.data.SttResult
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.MicHold
import si.lanisce.lani.ui.NodeMic
import si.lanisce.lani.ui.micAllowed
import si.lanisce.lani.ui.rememberNodeMic

/**
 * The 🎤 of a dialog turn, as screen state: listening, recognizing, the level for the animation, and the permission
 * question. Made by [rememberDialogMic].
 */
@Stable
class DialogMic internal constructor(private val node: NodeMic) {
    var listening by mutableStateOf(false)
        internal set
    /** What the phone's recognizer has heard so far, while listening. */
    var partial by mutableStateOf("")
        internal set
    /** The microphone is asked for: the explanation shows before the system prompt. */
    var asking by mutableStateOf(false)
        internal set
    /** The microphone was refused. */
    var denied by mutableStateOf(false)
        internal set
    /** The node's Whisper listens this time (else the phone). */
    var server by mutableStateOf(false)
        internal set
    internal var phoneLevel by mutableFloatStateOf(0f)

    /** The take is on its way to the node. */
    val thinking: Boolean get() = node.thinking

    /** 0..1, for the animation. */
    val level: Float get() = if (server) node.level else phoneLevel

    internal var start: () -> Unit = {}
    internal var finish: () -> Unit = {}
    internal var permit: () -> Unit = {}
    internal var decline: () -> Unit = {}

    /** Starts listening, or stops and recognizes what was said so far. */
    fun toggle() {
        when {
            thinking -> Unit
            listening -> finish()
            else -> start()
        }
    }

    /** The explanation's "Allow": the system asks for the microphone. */
    fun allow() = permit()

    /** The explanation's "Not now". */
    fun notNow() = decline()
}

/** Some recognizer can listen: the node's, or the phone's (unless it's known not to take Slovene). */
fun canListen(vm: AppViewModel, phoneMic: Boolean): Boolean =
    vm.stt.available == true || (phoneMic && vm.recognizer.support != Recognizer.Support.NO)

/**
 * A 🎤 for saying one of a dialog turn's choices. Like the speak exercise, the node's Whisper listens when it is up and
 * chosen ("more accurate") or the phone can't take Slovene; else the phone's recognizer, in the app (the microphone
 * allowed) or its system voice dialog. [expected] steers the node's spelling (the choices). What was heard goes to
 * [onHeard], the alternatives most likely first ([phone]: from the phone's recognizer); a take that failed (nothing
 * heard, the service or node unreachable, the microphone refused) to [onFailed] as a message for the learner.
 * [onStart]: a take begins.
 */
@Composable
fun rememberDialogMic(
    vm: AppViewModel,
    expected: () -> String,
    onHeard: (alternatives: List<String>, phone: Boolean) -> Unit,
    onFailed: (String) -> Unit,
    onStart: () -> Unit = {},
): DialogMic {
    val context = LocalContext.current
    val rec = vm.recognizer
    val node = rememberNodeMic(vm)
    val mic = remember { DialogMic(node) }
    // the background sounds keep quiet while it listens (the node's recorder holds the microphone too)
    MicHold(mic.listening)
    val heard by rememberUpdatedState(onHeard)
    val failed by rememberUpdatedState(onFailed)
    val prompt by rememberUpdatedState(expected)
    val started by rememberUpdatedState(onStart)
    LaunchedEffect(Unit) { vm.stt.refresh() } // the node's recognizer may have started or stopped since

    fun phoneHeard(alternatives: List<String>) {
        mic.listening = false
        mic.phoneLevel = 0f
        mic.partial = ""
        if (alternatives.any { it.isNotBlank() }) heard(alternatives, true) else failed(NodeMic.NOT_HEARD)
    }

    fun nodeHeard(r: SttResult?, why: String?) {
        mic.listening = false
        if (r != null && r.text.isNotBlank()) heard(listOf(r.text), false) else failed(why ?: NodeMic.NOT_HEARD)
    }

    val sr = remember { if (rec.inApp) runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull() else null }
    DisposableEffect(sr) {
        sr?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) { mic.phoneLevel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { mic.phoneLevel = 0f }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { mic.partial = it }
            }
            override fun onResults(results: Bundle?) = phoneHeard(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
            override fun onError(error: Int) {
                // Stopped before any result: what was heard so far counts.
                if (error == SpeechRecognizer.ERROR_CLIENT && mic.partial.isNotBlank()) return phoneHeard(listOf(mic.partial))
                mic.listening = false
                mic.phoneLevel = 0f
                mic.partial = ""
                failed(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_CLIENT -> NodeMic.NOT_HEARD
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            mic.denied = true
                            "🎤 ${bi("speakExercise.microphoneIsntAllowedLani")}"
                        }
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> {
                            rec.reject() // next time the node listens, when it can
                            "📵 ${bi("talkInput.speechRecognizerCantDo")}"
                        }
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "📥 ${bi("speakExercise.sloveneIsntDownloadedSpeech")}"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER,
                        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "📶 ${bi("speakExercise.speechServiceCantReached")}"
                        else -> "⚠️ ${bi("talkInput.recognitionFailedErrorTry", "error" to error)}"
                    },
                )
            }
        })
        onDispose { sr?.destroy() }
    }

    val dialog = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) phoneHeard(r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty())
    }

    fun listen() {
        started()
        vm.speaker.stop() // or the recognizer hears the person
        val phone = (rec.inApp || rec.dialog) && rec.support != Recognizer.Support.NO
        mic.server = vm.stt.available == true && (vm.stt.accurate || !phone)
        val recorder = mic.server || sr != null // records in the app: needs the microphone
        when {
            recorder && micAllowed(context) && mic.server -> {
                mic.listening = true
                node.start(prompt()) { r, why -> nodeHeard(r, why) }
            }
            recorder && micAllowed(context) && sr != null -> {
                mic.partial = ""
                mic.listening = true
                sr.startListening(rec.intent())
            }
            recorder && !mic.denied -> mic.asking = true
            // The system's voice dialog needs no permission of ours.
            rec.dialog -> runCatching { dialog.launch(rec.intent(bi("sceneDialog.sayYourAnswer"))) }
                .onFailure { failed("📵 ${bi("speakExercise.phoneHasNoSpeech")}") }
            mic.denied -> failed("🎤 ${bi("speakExercise.microphoneIsntAllowedLani")}")
            else -> failed("📵 ${bi("speakExercise.phoneHasNoSpeech")}")
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        mic.asking = false
        if (!ok) mic.denied = true
        if (ok || rec.dialog) listen() else failed("🎤 ${bi("speakExercise.microphoneIsntAllowedLani")}")
    }

    // Leaving the app while listening: drop the take rather than keep the microphone open in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (mic.listening && !mic.server) {
            sr?.cancel()
            mic.listening = false
            mic.phoneLevel = 0f
            mic.partial = ""
        }
    }

    SideEffect {
        mic.start = ::listen
        mic.finish = { if (mic.server) node.stop() else sr?.stopListening() }
        mic.permit = { permission.launch(Manifest.permission.RECORD_AUDIO) }
        mic.decline = {
            mic.asking = false
            mic.denied = true
            failed("🎤 ${bi("speakExercise.microphoneIsntAllowedLani")}")
        }
    }
    return mic
}
