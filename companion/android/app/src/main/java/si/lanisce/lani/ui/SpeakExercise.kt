package si.lanisce.lani.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.Hints
import si.lanisce.lani.data.Recognizer
import si.lanisce.lani.data.SpeechGrading
import si.lanisce.lani.data.SttResult
import si.lanisce.lani.data.SttSpeech
import si.lanisce.lani.ui.stage.LocalStage
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget

internal const val MAX_ATTEMPTS = 3
private val NODE_INSTEAD: String get() = "📵 ${bi("speakExercise.phoneCantRecognizeSlovene")}"

/** Why the learner rates themselves instead of the recognizer grading them. */
private enum class NoMic { CHOSEN, NO_RECOGNIZER, NO_SLOVENE, NO_PERMISSION }

/**
 * One speak exercise's course, pure (the screen keeps it in [SpeakState.round]): attempts until one is
 * right or [MAX_ATTEMPTS] are used, or the learner gives up ([end]) or rates themselves ([rate]); 💡 hints
 * on the way; then [result], fixed.
 *
 * 💡 (only while the model sentence is hidden) reveal [Exercise.Speak.say] step by step ([Hints.spoken]):
 * its shape, then its words from the start, until the whole sentence can simply be read aloud. They cost
 * what hints cost elsewhere, one per level ([Outcome.hints]): the verdict stays, the village pays a right
 * answer reached with hints as almost ([Hints.paid]), and its review quality drops one per hint, never
 * below 3 ([Hints.penalize]). So reading the fully revealed sentence aloud correctly still passes.
 *
 * After a recognized miss ([sayAgain]), what the recognizer hears goes to [practice], "Povej pravilno ·
 * Say it correctly": the model said again, as often as the learner likes. It never changes [result].
 */
internal data class SpeakRound(
    val ex: Exercise.Speak,
    val hints: Int = 0,
    val attempts: List<SpeechGrading.Heard> = emptyList(),
    val result: Outcome? = null,
    /** The learner rated themselves: no recognizer, so no say-it-again either. */
    val rated: Boolean = false,
    val practice: List<SpeechGrading.Heard> = emptyList(),
) {
    /** 💡 levels the model sentence has; 0: nothing to hint. */
    val levels: Int get() = Hints.spokenLevels(ex.say)

    /** The model sentence as the hints so far show it; null before the first. */
    val hintLine: String? get() = Hints.spoken(ex.say, hints)

    /** Every hint taken: the whole sentence shows. */
    val revealedAll: Boolean get() = levels in 1..hints

    /** Ended short of right, heard by a recognizer: "Povej pravilno · Say it correctly" is on offer. */
    val sayAgain: Boolean get() = result != null && !rated && result.verdict != Verdict.CORRECT

    /** The last say-it-again matched. */
    val saidRight: Boolean get() = practice.lastOrNull()?.result?.verdict == Verdict.CORRECT

    /** The next 💡; none past the last level, nor once the result is in. */
    fun hint(): SpeakRound = if (result != null || hints >= levels) this else copy(hints = hints + 1)

    /** What the recognizer heard: an attempt (ending the round when right or the last), after a miss a practice. */
    fun heard(h: SpeechGrading.Heard): SpeakRound = when {
        rated -> this
        result != null -> copy(practice = practice + h)
        else -> copy(attempts = attempts + h).let { if (h.result.verdict == Verdict.CORRECT || it.attempts.size >= MAX_ATTEMPTS) it.end() else it }
    }

    /** Ends with the best attempt ("Pokaži rešitev · Show answer"); unchanged with nothing heard yet. */
    fun end(): SpeakRound {
        if (result != null) return this
        val b = SpeechGrading.bestOf(attempts) ?: return this
        val r = b.result
        val marks = if (r.verdict == Verdict.CORRECT) emptyList() else r.marks.ifEmpty { SpeechGrading.differingWords(r.expected, b.text) }
        return copy(result = Outcome(r.verdict, b.text, r.expected, ex.explain, marks = marks, hint = r.hint, spoken = true, hints = hints))
    }

    /** No recognizer: the learner [said] it right, or is still practising. */
    fun rate(said: Boolean): SpeakRound = if (result != null) this else copy(
        rated = true,
        result = if (said) Outcome(Verdict.CORRECT, "self-rated: said it", ex.say, ex.explain, hints = hints)
        else Outcome(Verdict.WRONG, "self-rated: still practising", ex.say, ex.explain, hints = hints),
    )
}

/** Mutable screen state, shared with the recognizer's callbacks. [server]: the node's Whisper listens (NodeMic). */
private class SpeakState(ex: Exercise.Speak, noMic: NoMic?, server: Boolean) {
    var server by mutableStateOf(server)
    var listening by mutableStateOf(false)
    var partial by mutableStateOf("")
    var level by mutableFloatStateOf(0f)
    var message by mutableStateOf<String?>(null)
    var asking by mutableStateOf(false)
    var denied by mutableStateOf(false)
    var noMic by mutableStateOf(noMic)
    var revealed by mutableStateOf(false)
    var round by mutableStateOf(SpeakRound(ex))
}

/**
 * Say it out loud: the learner reads the prompt, may hear the model, taps 🎤 and speaks.
 * In-app [SpeechRecognizer] first (needs the microphone permission), then the system's voice
 * dialog. When the phone can't take Slovene, or the learner picks "Natančneje · More accurate",
 * the app records and the node's Whisper listens ([NodeMic]); with neither, the learner rates
 * themselves, so it never dead-ends. A hidden model sentence can be 💡 hinted until it can be read
 * aloud; after a miss, the learner may say it again correctly, just for practice ([SpeakRound]).
 */
@Composable
fun SpeakEx(vm: AppViewModel, ex: Exercise.Speak, locked: Boolean, done: (Outcome) -> Unit) {
    val context = LocalContext.current
    val rec = vm.recognizer
    val node = rememberNodeMic(vm)
    val stage = LocalStage.current
    LaunchedEffect(Unit) { vm.stt.refresh() } // the node's recognizer may have started or stopped since
    val phone = (rec.inApp || rec.dialog) && rec.support != Recognizer.Support.NO
    val st = remember {
        val server = vm.stt.available == true && (vm.stt.accurate || !phone)
        SpeakState(
            ex,
            when {
                server -> null
                !rec.inApp && !rec.dialog -> NoMic.NO_RECOGNIZER
                rec.support == Recognizer.Support.NO -> NoMic.NO_SLOVENE
                else -> null
            },
            server,
        )
    }
    val finish by rememberUpdatedState(done)
    MicHold(st.listening) // the background sounds keep quiet while it listens

    /** The round moves on. Its result is reported once, when it first comes; nothing after changes it. */
    fun update(next: SpeakRound) {
        val first = st.round.result == null
        st.round = next
        if (first) next.result?.let(finish)
    }

    fun complete() = update(st.round.end())

    fun heard(alternatives: List<String>, confidences: FloatArray?) {
        st.listening = false
        st.partial = ""
        val h = SpeechGrading.best(alternatives, confidences, ex.answers())
        if (h == null) {
            st.message = "🤔 ${bi("common.iDidntCatchTap")}"
            return
        }
        // Only Slovene that matches proves the language works; some services silently fall back to another.
        if (h.result.verdict != Verdict.WRONG) rec.confirm()
        update(st.round.heard(h))
    }

    /** What the node's Whisper heard: graded the same way, with its unsure words as a pronunciation hint. */
    fun nodeHeard(r: SttResult?, failed: String?) {
        st.listening = false
        val h = r?.let { SttSpeech.heard(it, ex.answers()) }
        if (h == null) {
            st.message = failed ?: NodeMic.NOT_HEARD
            if (vm.stt.available == false && phone) st.server = false // node down: back to the phone
            return
        }
        update(st.round.heard(h))
    }

    /** The phone can't do Slovene after all: let the node listen when it can. */
    fun toNode(): Boolean {
        if (vm.stt.available != true) return false
        st.server = true
        return true
    }

    val sr = remember { if (rec.inApp) runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull() else null }
    DisposableEffect(sr) {
        sr?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) { st.level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { st.level = 0f }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { st.partial = it }
            }
            override fun onResults(results: Bundle?) = heard(
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty(),
                results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES),
            )
            override fun onError(error: Int) {
                st.listening = false
                st.level = 0f
                st.message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_CLIENT ->
                        "🤔 ${bi("common.iDidntCatchTap")}"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { st.denied = true; null }
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> {
                        rec.reject()
                        if (toNode()) NODE_INSTEAD else { st.noMic = NoMic.NO_SLOVENE; null }
                    }
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> if (toNode()) NODE_INSTEAD else
                        "📥 ${bi("speakExercise.sloveneIsntDownloadedSpeech")}"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER,
                    SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                        "📶 ${bi("speakExercise.speechServiceCantReached")}"
                    else -> "⚠️ ${bi("talkInput.recognitionFailedErrorTry", "error" to error)}"
                }
            }
        })
        onDispose {
            sr?.destroy()
            vm.speaker.stop()
        }
    }

    val dialog = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) heard(
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty(),
            r.data?.getFloatArrayExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES),
        )
    }

    fun listen() {
        vm.speaker.stop() // or the recognizer hears the model
        st.message = null
        when {
            st.server && micAllowed(context) -> {
                st.listening = true
                node.start(ex.say, ::nodeHeard)
            }
            st.server && !st.denied -> st.asking = true
            st.server -> st.noMic = NoMic.NO_PERMISSION
            sr != null && micAllowed(context) -> {
                st.partial = ""
                st.listening = true
                sr.startListening(rec.intent())
            }
            sr != null && !st.denied -> st.asking = true
            rec.dialog -> runCatching { dialog.launch(rec.intent(bi("common.saySlovene"))) }
                .onFailure { st.noMic = NoMic.NO_RECOGNIZER }
            else -> st.noMic = if (st.denied) NoMic.NO_PERMISSION else NoMic.NO_RECOGNIZER
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        st.asking = false
        if (ok) listen() else {
            st.denied = true
            if (st.server && rec.dialog && phone) st.server = false // the system dialog needs no permission of ours
            if (!rec.dialog || st.server) st.noMic = NoMic.NO_PERMISSION
        }
    }

    fun mic() {
        when {
            node.thinking -> Unit
            st.listening && st.server -> node.stop()
            st.listening -> sr?.stopListening()
            else -> listen()
        }
    }

    val rationale: @Composable () -> Unit = {
        Rationale(
            node = st.server,
            onAllow = { permission.launch(Manifest.permission.RECORD_AUDIO) },
            onNotNow = { st.asking = false; st.noMic = NoMic.CHOSEN },
        )
    }

    // --- layout ---
    val round = st.round
    ex.instruction?.let {
        Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
    Text(inlineMarkdown(ex.prompt), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    if (ex.show || st.revealed || locked) Model(vm, ex.say, autoplay = !ex.show) {
        // After a miss, saying it again right under the model: optional, the feedback's "Naprej" moves on.
        if (locked && round.sayAgain && st.noMic == null) {
            if (st.asking) rationale()
            else SayAgain(round, st.listening && !node.thinking, node.thinking, st.partial, st.message, ::mic)
        }
    } else SpokenHint(round, onHint = { st.round = st.round.hint(); stage?.hint() })
    Spacer(Modifier.height(16.dp))

    round.attempts.lastOrNull()?.let { Heard(it, round.attempts.size) }
    if (locked) return

    val noMic = st.noMic
    when {
        noMic != null -> SelfRate(vm, ex, noMic, st) { said -> update(st.round.rate(said)) }
        st.asking -> rationale()
        else -> {
            MicButton(st.listening && !node.thinking, if (st.server) node.level else st.level, onClick = ::mic)
            Text(
                when {
                    node.thinking -> "⏳ ${bi("speakExercise.recognizing")}"
                    st.listening -> bi("speakExercise.listeningTapStop")
                    else -> bi("speakExercise.tapSpeak")
                },
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            if (st.partial.isNotBlank()) Text(
                st.partial,
                style = MaterialTheme.typography.titleMedium,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            st.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp)) }
            if (st.message?.startsWith("📥") == true) TextButton(onClick = { rec.openSettings() }) { Text(bi("common.settings")) }
            if (st.denied) Text(
                "🎤 ${bi("speakExercise.microphoneNotAllowedSo")}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (vm.stt.available == true && !st.listening) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // "More accurate": the node's Whisper instead of the phone's recognizer, remembered.
                if (!st.server) TextButton(onClick = { st.server = true; vm.stt.choose(true) }) { Text("🎯 ${bi("speakExercise.moreAccurate")}") }
                else if (phone) TextButton(onClick = { st.server = false; vm.stt.choose(false) }) { Text("📱 ${bi("speakExercise.phoneRecognizer")}") }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { st.noMic = NoMic.CHOSEN }) { Text(bi("speakExercise.cantSpeakNow")) }
                if (round.attempts.isNotEmpty()) TextButton(onClick = ::complete) { Text(bi("speakExercise.showAnswer")) }
            }
        }
    }
}

/** The Slovene model sentence with normal and 🐢 slow playback; [below] goes under it, in the same card. */
@Composable
private fun Model(vm: AppViewModel, say: String, autoplay: Boolean, below: @Composable () -> Unit = {}) {
    val speaker = vm.speaker
    if (autoplay) LaunchedEffect(say) { speaker.say(say) }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(say, style = MaterialTheme.typography.headlineSmall)
            if (speaker.available) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SayButton(speaker, say, Modifier.weight(1f)) { Text("🔊 ${bi("common.listen")}") }
                SayButton(speaker, say, slow = true, tonal = false) { EmojiLabel("🐢", Labels.SLOW) }
            }
            below()
        }
    }
}

/**
 * 💡 for a hidden model sentence ([SpeakRound.hint]): the sentence as revealed so far, its shape then its
 * words, and the button for the next part. With every hint taken it shows whole, to read aloud.
 */
@Composable
private fun SpokenHint(round: SpeakRound, onHint: () -> Unit) {
    if (round.levels == 0) return
    round.hintLine?.let { line ->
        Surface(color = XpGold.copy(alpha = 0.16f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "💡 ${bi("hints.hintLevel", "level" to round.hints, "max" to round.levels)} (${bi("hints.countsLittleLess")})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Letters and gaps line up while parts are hidden; whole, it reads like the model.
                if (round.revealedAll) Text(line, style = MaterialTheme.typography.headlineSmall)
                else Text(line, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                if (round.revealedAll) Text("🗣️ ${bi("speakExercise.readItAloud")}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    HintButton(round.hints, onClick = onHint, enabled = !round.revealedAll, of = round.levels)
}

/**
 * After a miss: "🎤 Povej pravilno · Say it correctly", the recognizer listens as for an attempt, as often as
 * the learner likes, and says ✓ when it matches, else what it heard. Only practice: the result stays.
 */
@Composable
private fun SayAgain(round: SpeakRound, listening: Boolean, thinking: Boolean, partial: String, message: String?, onMic: () -> Unit) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
            onClick = onMic,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = if (listening) TriglavRed else MaterialTheme.colorScheme.primary),
        ) {
            Text(
                when {
                    thinking -> "⏳ ${bi("speakExercise.recognizing")}"
                    listening -> "⏹ ${bi("speakExercise.listeningTapStop")}"
                    else -> "🎤 ${bi("speakExercise.sayItCorrectly")}"
                },
                textAlign = TextAlign.Center,
            )
        }
        val last = round.practice.lastOrNull()
        when {
            listening || thinking -> if (partial.isNotBlank()) Text(partial, style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic)
            message != null -> Text(message, style = MaterialTheme.typography.bodyMedium)
            last == null -> Text(bi("speakExercise.justPractice"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            round.saidRight -> Text(
                "✓ ${bi("playerScreen.correct")}",
                style = MaterialTheme.typography.titleMedium,
                color = AlpineGreen,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            else -> Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Text("${bi("speakExercise.iHeard")}:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(underlined(last.text, SpeechGrading.differingWords(last.text, last.result.expected), TriglavRed), style = MaterialTheme.typography.titleMedium)
                Text("🔁 ${bi("speakExercise.listenTryAgain")}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** What the recognizer heard, with the words that differ from the expected answer underlined. */
@Composable
private fun Heard(h: SpeechGrading.Heard, attempt: Int) {
    val right = h.result.verdict == Verdict.CORRECT
    val marks = if (right) emptyList() else SpeechGrading.differingWords(h.text, h.result.expected)
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${bi("speakExercise.iHeard")}  ·  ${inTarget("speakExercise.attempt", "attempt" to attempt, "max" to MAX_ATTEMPTS)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(underlined(h.text, marks, TriglavRed), style = MaterialTheme.typography.titleLarge)
            h.confidence?.let {
                Text("🎯 ${inTarget("speakExercise.confidence", "pct" to (it * 100).toInt())}", style = MaterialTheme.typography.labelMedium)
            }
            SttSpeech.hint(h.unclear)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
    Spacer(Modifier.height(16.dp))
}

private fun underlined(text: String, marks: List<IntRange>, color: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    for (r in marks) if (r.first >= 0 && r.last < text.length) {
        addStyle(SpanStyle(color = color, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold), r.first, r.last + 1)
    }
}

@Composable
private fun MicButton(listening: Boolean, level: Float, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (listening) 1f + level * 0.25f else 1f, label = "mic")
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Button(
            onClick = onClick,
            shape = CircleShape,
            modifier = Modifier.size(112.dp).scale(scale),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (listening) TriglavRed else MaterialTheme.colorScheme.primary,
            ),
        ) { Text(if (listening) "⏹" else "🎤", style = MaterialTheme.typography.displaySmall) }
    }
}

/** Shown before the system permission prompt, so the request isn't a surprise. [node]: the recording goes to the node. */
@Composable
private fun Rationale(node: Boolean, onAllow: () -> Unit, onNotNow: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🎤 ${bi("common.microphone")}", style = MaterialTheme.typography.titleMedium)
            Text(
                if (node) {
                    bi("speakExercise.hearSpeakLaniNeeds")
                } else {
                    bi("speakExercise.hearSpeakLaniNeedsMicrophone")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onNotNow, modifier = Modifier.weight(1f)) { Text(bi("common.notNow")) }
                Button(onClick = onAllow, modifier = Modifier.weight(1f)) { Text(bi("common.allow")) }
            }
        }
    }
}

/** No usable recognizer: say it anyway, compare with the model, and rate yourself ([rate]: said it right). */
@Composable
private fun SelfRate(vm: AppViewModel, ex: Exercise.Speak, why: NoMic, st: SpeakState, rate: (Boolean) -> Unit) {
    val context = LocalContext.current
    val reason = when (why) {
        NoMic.CHOSEN -> null
        NoMic.NO_RECOGNIZER -> "📵 ${bi("speakExercise.phoneHasNoSpeech")}"
        NoMic.NO_SLOVENE -> "📵 ${bi("speakExercise.phonesSpeechRecognizerDoesnt")}"
        NoMic.NO_PERMISSION -> "🎤 ${bi("speakExercise.microphoneIsntAllowedLani")}"
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            reason?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(bi("speakExercise.sayOutLoudThen"), style = MaterialTheme.typography.titleSmall)
            Row {
                when (why) {
                    NoMic.NO_SLOVENE -> {
                        TextButton(onClick = { vm.recognizer.openSettings() }) { Text(bi("common.settings")) }
                        TextButton(onClick = { st.noMic = null }) { Text(bi("speakExercise.tryAnyway")) }
                    }
                    NoMic.NO_PERMISSION -> TextButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        st.denied = false
                        st.noMic = null
                    }) { Text(bi("common.settings")) }
                    NoMic.CHOSEN -> if (vm.recognizer.inApp || vm.recognizer.dialog || st.server) {
                        TextButton(onClick = { st.noMic = null }) { Text("🎤 ${bi("speakExercise.useMic")}") }
                    }
                    NoMic.NO_RECOGNIZER -> Unit
                }
            }
            if (why != NoMic.NO_PERMISSION && !st.server && vm.stt.available == true) {
                TextButton(onClick = { st.server = true; st.noMic = null }) { Text("🎤 ${bi("speakExercise.letNodeListen")}") }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    if (!ex.show && !st.revealed) {
        BigButton(bi("speakExercise.reveal"), onClick = { st.revealed = true })
    } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { rate(false) }, Modifier.weight(1f).height(56.dp)) {
            Text("✗ ${bi("speakExercise.stillPractising")}", textAlign = TextAlign.Center)
        }
        OutlinedButton(onClick = { rate(true) }, Modifier.weight(1f).height(56.dp)) {
            Text("✓ ${bi("speakExercise.iSaidRight")}", textAlign = TextAlign.Center)
        }
    }
}
