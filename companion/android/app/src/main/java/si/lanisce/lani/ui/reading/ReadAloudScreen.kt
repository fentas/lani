package si.lanisce.lani.ui.reading

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.ReadAloud
import si.lanisce.lani.data.Recognizer
import si.lanisce.lani.data.ScreenClock
import si.lanisce.lani.data.SttResult
import si.lanisce.lani.game.culture.ReadingFile
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.GradientHeader
import si.lanisce.lani.ui.NodeMic
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.backLabel
import si.lanisce.lani.ui.game.ReadLine
import si.lanisce.lani.ui.game.ReadingLogic
import si.lanisce.lani.ui.micAllowed
import si.lanisce.lani.ui.minutesSince
import si.lanisce.lani.ui.rememberNodeMic
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.ui.villagers.VillagerLogic

/** The spoken lines of reading [r] in the target language, the text read aloud: its intro, steps and lines (not the ingredients). */
fun readAloudLines(r: ReadingFile): List<String> =
    ReadingLogic.lines(r).filter { it.spoken && it.kind != ReadLine.Kind.INGREDIENT }.map { it.target }

/**
 * "🎤 Beri na glas · Read aloud" (companion/GAME.md, "Reading aloud"): reading [reading] aloud, part by part
 * ([ReadAloud.passages]). Each part is recorded (a pause of 3 s or a tap ends it) and heard by the node's Whisper
 * twice (with the text as its prompt and without), else by the phone's recognizer; its words show read right, misread
 * (what was heard) or skipped, with the accuracy and the words a minute, and 🔊 on each sentence with a mistake. The
 * last take of each part counts: finishing (or leaving after a take) reports the reading aloud as reading and speaking
 * practice. Back leads to the reading corner ([fromCorner]; opened from the village, [fromVillage]), else to the village
 * (a chest's reading).
 */
@Composable
fun ReadAloudScreen(vm: AppViewModel, readingId: String, fromVillage: Boolean, fromCorner: Boolean) {
    val r = vm.readings.byId(readingId)
    if (r == null) {
        LaunchedEffect(Unit) { vm.leaveReadAloud(fromVillage, fromCorner) }
        return
    }
    val passages = remember(r) { ReadAloud.passages(readAloudLines(r)) }
    val reader = r.by?.let { vm.villagers.byId(it) }
    val started = remember { ScreenClock.app.now() }
    var at by remember { mutableIntStateOf(0) }
    var takes by remember { mutableStateOf<Map<Int, ReadAloud.Take>>(emptyMap()) }
    var finished by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    fun report() {
        if (reported || takes.isEmpty()) return
        reported = true
        vm.readings.readAloud(r, takes.toSortedMap().values.toList(), minutesSince(started))
    }
    fun leave() {
        report()
        vm.leaveReadAloud(fromVillage, fromCorner)
    }
    BackHandler { leave() }
    // leaving the screen any other way (a notification's link) still counts what was read
    val latest by rememberUpdatedState { report() }
    DisposableEffect(Unit) { onDispose { latest() } }

    val mic = rememberReadAloudMic(vm, onHeard = { take ->
        message = null
        takes = takes + (at to take)
    }, onFailed = { message = it })

    val speak: @Composable (String) -> Unit = { text ->
        SpeakButton(vm.speaker, text, voiceName = reader?.speakerVoice ?: Clips.FEMALE, fallback = reader?.let { VillagerLogic.voiceOf(it) }, person = reader?.id)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        GradientHeader(
            "🎤 ${bi("readAloud.title")}",
            "${r.title.target} · ${bi("readAloud.part", "n" to at + 1, "of" to passages.size)}",
            if (fromCorner) "← ${bi("readings.title")}" else backLabel(fromVillage),
            onBack = ::leave,
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (finished) {
                Summary(takes.toSortedMap().values.toList(), speak)
                Button(onClick = ::leave, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("✓ ${bi("readAloud.done")}") }
                return@Column
            }
            Text(bi("readAloud.how"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (passages.size > 1) Parts(passages.size, at, takes) { if (!mic.listening && !mic.thinking) at = it }
            val take = takes[at]
            Passage(passages[at], take, speak)
            take?.let { TakeResult(it, speak) }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            when {
                mic.unavailable -> Text("📵 ${bi("readAloud.noRecognizer")}", style = MaterialTheme.typography.bodyMedium)
                mic.asking -> Rationale(node = mic.server, onAllow = mic::allow, onNotNow = mic::notNow)
                else -> {
                    MicButton(mic.listening && !mic.thinking, mic.level, onClick = { mic.toggle(passages[at].text) })
                    Text(
                        when {
                            mic.thinking -> "⏳ ${bi("readAloud.listeningBack")}"
                            mic.listening -> bi("readAloud.readingTapStop")
                            take != null -> bi("readAloud.again")
                            else -> bi("readAloud.tapAndRead")
                        },
                        style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                    if (mic.partial.isNotBlank()) Text(mic.partial, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { report(); finished = true }, enabled = takes.isNotEmpty() && !mic.listening && !mic.thinking) { Text(bi("readAloud.finish")) }
                if (at < passages.lastIndex) {
                    Button(onClick = { at++; message = null }, enabled = !mic.listening && !mic.thinking) { Text("${bi("readAloud.next")} ›") }
                } else if (takes.isNotEmpty()) {
                    Button(onClick = { report(); finished = true }, enabled = !mic.listening && !mic.thinking) { Text("✓ ${bi("readAloud.finish")}") }
                }
            }
        }
    }
}

/** The parts to read, as chips: ✓ read well, • read, the number when not yet; tap to go to one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Parts(n: Int, at: Int, takes: Map<Int, ReadAloud.Take>, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until n) {
            val t = takes[i]
            val mark = when {
                t == null -> ""
                ReadAloud.sentencesOf(t).all(ReadAloud::readWell) -> "✓ "
                else -> "• "
            }
            FilterChip(selected = i == at, onClick = { onPick(i) }, label = { Text("$mark${i + 1}") })
        }
    }
}

/** The part to read, big; after a take, each word as it was read: right, misread (red), skipped (struck through), unclear (gold). */
@Composable
private fun Passage(p: ReadAloud.Passage, take: ReadAloud.Take?, speak: @Composable (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            val shown = take?.let { marked(it) } ?: AnnotatedString(p.text)
            Text(shown, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            speak(p.text)
        }
    }
}

/** [take]'s words as the text has them, each styled by how it was read. */
@Composable
private fun marked(take: ReadAloud.Take): AnnotatedString = buildAnnotatedString {
    val wrong = TriglavRed
    val gone = MaterialTheme.colorScheme.onSurfaceVariant
    take.words.forEachIndexed { i, w ->
        if (i > 0) append(" ")
        when {
            w.mark == ReadAloud.Mark.MISREAD -> withStyle(SpanStyle(color = wrong, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) { append(w.text) }
            w.mark == ReadAloud.Mark.SKIPPED -> withStyle(SpanStyle(color = gone, textDecoration = TextDecoration.LineThrough)) { append(w.text) }
            w.unclear -> withStyle(SpanStyle(background = XpGold.copy(alpha = 0.3f))) { append(w.text) }
            else -> withStyle(SpanStyle(color = AlpineGreen)) { append(w.text) }
        }
    }
}

/** A take's result: how many words were right, the pace, each word read wrong (what was heard), and 🔊 on the sentences to hear again. */
@Composable
private fun TakeResult(take: ReadAloud.Take, speak: @Composable (String) -> Unit) {
    val s = ReadAloud.score(listOf(take))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        Text(
            (if (take.perfect) "🎉 " else "✓ ") + bi("readAloud.score", "right" to s.right, "total" to s.words, "percent" to s.percent) +
                (s.wpm?.let { " · ${bi("readAloud.wpm", "n" to it)}" } ?: ""),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        )
        for (w in take.words.filter { it.mark != ReadAloud.Mark.RIGHT }) Text(missLine(w), style = MaterialTheme.typography.bodyMedium)
        val unclear = take.words.filter { it.mark == ReadAloud.Mark.RIGHT && it.unclear }
        if (unclear.isNotEmpty()) Text("🎯 ${bi("readAloud.unclear", "words" to unclear.joinToString(", ") { it.text.trim(',', '.', '!', '?', ';', ':', '»', '«') })}", style = MaterialTheme.typography.bodySmall)
        if (take.by == ReadAloud.By.PHONE) Text("📱 ${bi("readAloud.byPhone")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val again = ReadAloud.toHearAgain(take)
        if (again.isNotEmpty()) {
            Text("🔊 ${bi("readAloud.hearAgain")}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            for (sentence in again) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sentence, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                speak(sentence)
            }
        }
    }
}

/** "✗ hribih: slišano »gorah« · heard »gorah«", "– s: izpuščeno · skipped". */
private fun missLine(w: ReadAloud.WordMark): String {
    val word = w.text.trim(',', '.', '!', '?', ';', ':', '»', '«', '"', '„', '“', '”')
    return when (w.mark) {
        ReadAloud.Mark.SKIPPED -> "– $word: ${bi("readAloud.skipped")}"
        else -> "✗ $word: " + (w.heard?.let { bi("readAloud.heard", "heard" to it) } ?: bi("readAloud.notHeard")) +
            if (w.ending) " (${bi("readAloud.ending")})" else ""
    }
}

/** The whole reading aloud: the words right, the pace, the sentences read well, and those to hear again. */
@Composable
private fun Summary(takes: List<ReadAloud.Take>, speak: @Composable (String) -> Unit) {
    val s = ReadAloud.score(takes)
    Surface(color = XpGold.copy(alpha = 0.16f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🎤 ${bi("readAloud.summary")}", style = MaterialTheme.typography.titleLarge)
            Text(bi("readAloud.score", "right" to s.right, "total" to s.words, "percent" to s.percent), style = MaterialTheme.typography.titleMedium)
            s.wpm?.let { Text("⏱ ${bi("readAloud.wpm", "n" to it)}", style = MaterialTheme.typography.bodyMedium) }
            Text("📖 ${bi("readAloud.sentencesWell", "well" to s.readWell, "total" to s.sentences)}", style = MaterialTheme.typography.bodyMedium)
            if (s.misread + s.skipped > 0) Text(bi("readAloud.missedCount", "misread" to s.misread, "skipped" to s.skipped), style = MaterialTheme.typography.bodySmall)
        }
    }
    val again = takes.flatMap(ReadAloud::toHearAgain)
    if (again.isNotEmpty()) {
        Text("🔊 ${bi("readAloud.hearAgain")}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        for (sentence in again) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sentence, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            speak(sentence)
        }
    }
}

@Composable
private fun MicButton(listening: Boolean, level: Float, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (listening) 1f + level * 0.25f else 1f, label = "mic")
    val label = if (listening) bi("readAloud.readingTapStop") else bi("readAloud.tapAndRead")
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Button(
            onClick = onClick,
            shape = CircleShape,
            modifier = Modifier.size(104.dp).scale(scale).semantics { contentDescription = label },
            colors = ButtonDefaults.buttonColors(containerColor = if (listening) TriglavRed else MaterialTheme.colorScheme.primary),
        ) { Text(if (listening) "⏹" else "🎤", style = MaterialTheme.typography.displaySmall) }
    }
}

/** Shown before the system asks for the microphone. [node]: the recording goes to the node. */
@Composable
private fun Rationale(node: Boolean, onAllow: () -> Unit, onNotNow: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🎤 ${bi("common.microphone")}", style = MaterialTheme.typography.titleMedium)
            Text(if (node) bi("speakExercise.hearSpeakLaniNeeds") else bi("speakExercise.hearSpeakLaniNeedsMicrophone"), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onNotNow, modifier = Modifier.weight(1f)) { Text(bi("common.notNow")) }
                Button(onClick = onAllow, modifier = Modifier.weight(1f)) { Text(bi("common.allow")) }
            }
        }
    }
}

// --- the microphone -------------------------------------------------------------------------------------------------

/**
 * The read-aloud 🎤 as screen state (made by [rememberReadAloudMic]): the node's Whisper listens when it is up (two
 * passes, [NodeMic.startReading]); else the phone's recognizer, in the app or its voice dialog; with neither,
 * [unavailable].
 */
class ReadAloudMic internal constructor(private val node: NodeMic) {
    var listening by mutableStateOf(false)
        internal set
    var partial by mutableStateOf("")
        internal set
    var asking by mutableStateOf(false)
        internal set
    var denied by mutableStateOf(false)
        internal set
    var server by mutableStateOf(false)
        internal set
    var unavailable by mutableStateOf(false)
        internal set
    internal var phoneLevel by mutableFloatStateOf(0f)
    val thinking: Boolean get() = node.thinking
    val level: Float get() = if (server) node.level else phoneLevel

    internal var start: (String) -> Unit = {}
    internal var finish: () -> Unit = {}
    internal var permit: () -> Unit = {}
    internal var decline: () -> Unit = {}
    internal var text: String = ""

    /** Starts reading [passage] aloud, or stops and grades what was read so far. */
    fun toggle(passage: String) {
        when {
            thinking -> Unit
            listening -> finish()
            else -> start(passage)
        }
    }

    fun allow() = permit()

    fun notNow() = decline()
}

@Composable
private fun rememberReadAloudMic(vm: AppViewModel, onHeard: (ReadAloud.Take) -> Unit, onFailed: (String) -> Unit): ReadAloudMic {
    val context = LocalContext.current
    val rec = vm.recognizer
    val node = rememberNodeMic(vm)
    val mic = remember { ReadAloudMic(node) }
    si.lanisce.lani.ui.MicHold(mic.listening) // the background sounds keep quiet while it listens
    val heard by rememberUpdatedState(onHeard)
    val failed by rememberUpdatedState(onFailed)
    var spoke by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) { vm.stt.refresh() }

    val phone = (rec.inApp || rec.dialog) && rec.support != Recognizer.Support.NO
    LaunchedEffect(vm.stt.available, phone) { mic.unavailable = vm.stt.available == false && !phone }

    fun phoneHeard(alternatives: List<String>) {
        mic.listening = false
        mic.phoneLevel = 0f
        mic.partial = ""
        val secs = spoke.takeIf { it > 0 }?.let { (SystemClock.elapsedRealtime() - it) / 1000.0 }
        val take = ReadAloud.gradePhone(mic.text, alternatives, secs)
        if (take == null || take.right == 0 && take.words.none { it.mark == ReadAloud.Mark.MISREAD }) failed(NodeMic.NOT_HEARD) else heard(take)
    }

    fun nodeHeard(r: Pair<SttResult, SttResult?>?, why: String?) {
        mic.listening = false
        if (r == null || r.first.text.isBlank()) {
            failed(why ?: NodeMic.NOT_HEARD)
            return
        }
        heard(ReadAloud.grade(mic.text, r.first, r.second))
    }

    val sr = remember { if (rec.inApp) runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull() else null }
    DisposableEffect(sr) {
        sr?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() { spoke = SystemClock.elapsedRealtime() }
            override fun onRmsChanged(rmsdB: Float) { mic.phoneLevel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { mic.phoneLevel = 0f }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { mic.partial = it }
            }
            override fun onResults(results: Bundle?) = phoneHeard(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_CLIENT && mic.partial.isNotBlank()) return phoneHeard(listOf(mic.partial))
                mic.listening = false
                mic.phoneLevel = 0f
                mic.partial = ""
                failed(
                    when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            mic.denied = true
                            "🎤 ${bi("speakExercise.microphoneIsntAllowedLani")}"
                        }
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> {
                            rec.reject()
                            "📵 ${bi("readAloud.noRecognizer")}"
                        }
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER,
                        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "📶 ${bi("speakExercise.speechServiceCantReached")}"
                        else -> NodeMic.NOT_HEARD
                    },
                )
            }
        })
        onDispose { sr?.destroy() }
    }

    val dialog = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) phoneHeard(res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty())
        else mic.listening = false
    }

    fun listen(passage: String) {
        mic.text = passage
        vm.speaker.stop() // or the recognizer hears the model
        mic.server = vm.stt.available == true
        val recorder = mic.server || sr != null
        when {
            mic.server && micAllowed(context) -> {
                mic.listening = true
                node.startReading(passage, ::nodeHeard)
            }
            !mic.server && !phone -> {
                mic.unavailable = true
                failed("📵 ${bi("readAloud.noRecognizer")}")
            }
            sr != null && micAllowed(context) -> {
                mic.partial = ""
                spoke = 0L
                mic.listening = true
                sr.startListening(rec.intent(silenceMs = 3_000))
            }
            recorder && !mic.denied -> mic.asking = true
            rec.dialog -> {
                spoke = 0L
                runCatching { dialog.launch(rec.intent(bi("readAloud.tapAndRead"), silenceMs = 3_000)) }.onFailure { failed("📵 ${bi("readAloud.noRecognizer")}") }
            }
            else -> failed("🎤 ${bi("speakExercise.microphoneIsntAllowedLani")}")
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        mic.asking = false
        if (!ok) mic.denied = true
        if (ok || rec.dialog) listen(mic.text) else failed("🎤 ${bi("speakExercise.microphoneIsntAllowedLani")}")
    }

    // leaving the app while the phone listens: drop the take rather than keep the microphone open
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (mic.listening && !mic.server) {
            sr?.cancel()
            mic.listening = false
            mic.phoneLevel = 0f
            mic.partial = ""
        }
    }

    mic.start = { listen(it) }
    mic.finish = { if (mic.server) node.stop() else sr?.stopListening() }
    mic.permit = { permission.launch(Manifest.permission.RECORD_AUDIO) }
    mic.decline = {
        mic.asking = false
        mic.denied = true
        failed("🎤 ${bi("speakExercise.microphoneIsntAllowedLani")}")
    }
    return mic
}
