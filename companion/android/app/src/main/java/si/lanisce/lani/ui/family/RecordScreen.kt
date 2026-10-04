package si.lanisce.lani.ui.family

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.FamilyRecorder
import si.lanisce.lani.data.Recording
import si.lanisce.lani.data.Voice
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.micAllowed
import si.lanisce.lani.ui.openAppSettings
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import java.io.File

private val emojis = listOf("👵", "👴", "👩", "👨", "🧑", "👧", "👦", "💌")

/** One row of the recording list: what to record, and its recordings so far. */
private data class Line(val text: String, val sub: String, val recordings: List<Recording>)

/**
 * "Družina posname": a family member records words and phrases from Jan's packs and cards
 * on Jan's phone, so Jan hears their voice instead of TTS.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordScreen(vm: AppViewModel) {
    val f = vm.family
    var showRecorded by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<String?>(null) }
    var custom by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { f.loadWords() }.onFailure { failed = true } }

    val index = f.voice.index
    val words = f.words.orEmpty()
    val missing = words.filter { Voice.normalize(it.text) !in index }.map { Line(it.text, listOfNotNull(it.en, it.source.ifBlank { null }).joinToString(" · "), emptyList()) }
    val recorded = index.values.filter { it.isNotEmpty() }.map { Line(it.last().text, "", it) }.sortedBy { it.text.lowercase() }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::home) { EmojiLabel("✕", Labels.CLOSE) }
            Text("⏺ ${bi("common.familyRecords")}", style = MaterialTheme.typography.titleMedium)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            bi("recordScreen.recordWordsOwnVoice", "learnerName" to f.settings.learnerName),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        WhoSpeaks(vm)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it.take(200) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(bi("recordScreen.anyOtherPhrase")) },
                        shape = MaterialTheme.shapes.medium,
                    )
                    Spacer(Modifier.width(8.dp))
                    RecordButton(enabled = custom.isNotBlank()) { recording = custom.trim() }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    FilterChip(selected = !showRecorded, onClick = { showRecorded = false }, label = { Text(bi("recordScreen.toRecord", "missingSize" to missing.size)) })
                    FilterChip(selected = showRecorded, onClick = { showRecorded = true }, label = { Text(bi("recordScreen.recorded", "recordedSize" to recorded.size)) })
                }
            }
            val lines = if (showRecorded) recorded else missing
            if (lines.isEmpty()) item {
                Text(
                    when {
                        failed -> "⚠️ ${bi("recordScreen.couldntLoadList")}"
                        f.words == null && !showRecorded -> bi("common.loading")
                        showRecorded -> bi("recordScreen.nothingRecordedYet")
                        else -> "🎉 ${bi("recordScreen.everythingRecorded")}"
                    },
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(lines) { line ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(line.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (line.sub.isNotBlank()) Text(line.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (line.recordings.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (r in line.recordings) Recorded(vm, r)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    RecordButton { recording = line.text }
                }
                HorizontalDivider()
            }
        }
    }
    recording?.let { text -> RecordDialog(vm, text, onClose = { recording = null }) }
}

/** Who is speaking: an emoji and a name, remembered on this phone. */
@Composable
private fun WhoSpeaks(vm: AppViewModel) {
    val f = vm.family
    val current = f.recordAs
    val emoji = emojis.firstOrNull { current.startsWith(it) } ?: "👵"
    var name by remember { mutableStateOf(current.removePrefix(emoji).trim().ifBlank { f.settings.partnerName }) }
    fun save(e: String = emoji, n: String = name) = f.saveRecordAs("$e ${n.trim()}".trim())
    Text(bi("recordScreen.whosSpeaking"), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (e in emojis) {
            TextButton(onClick = { save(e = e) }, contentPadding = PaddingValues(4.dp)) {
                Text(e, fontSize = if (e == emoji) 28.sp else 20.sp)
            }
        }
    }
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(30); save(n = name) },
        singleLine = true,
        label = { Text(bi("recordScreen.name")) },
        placeholder = { Text("Micka") },
        modifier = Modifier.fillMaxWidth(),
    )
    Text("${bi("recordScreen.recordingsLabelled")}: ${Voice.badge(f.recordAs.ifBlank { "Družina" })}", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Recorded(vm: AppViewModel, r: Recording) {
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        AssistChip(onClick = { scope.launch { runCatching { vm.family.play(r) }.onFailure { vm.notices.error = it.message } } }, label = { Text("▶ ${r.speaker}") })
        TextButton(onClick = { confirm = true }, contentPadding = PaddingValues(4.dp)) { EmojiLabel("🗑", "${Labels.DELETE}: ${r.speaker}") }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(bi("recordScreen.deleteRecording")) },
        text = { Text("„${r.text}“ · ${r.speaker}") },
        confirmButton = {
            TextButton(onClick = {
                confirm = false
                scope.launch { runCatching { vm.family.delete(r) }.onFailure { vm.notices.error = it.message } }
            }) { Text(bi("common.delete"), color = TriglavRed) }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(bi("recordScreen.cancel")) } },
    )
}

@Composable
private fun RecordButton(enabled: Boolean = true, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(52.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = TriglavRed),
    ) { EmojiLabel("⏺", Labels.RECORD, style = LocalTextStyle.current.copy(fontSize = 22.sp)) }
}

private enum class Take { IDLE, RECORDING, REVIEW, SAVING }

/** Record → listen → save or record again. Asks for the microphone with a plain explanation first. */
@Composable
private fun RecordDialog(vm: AppViewModel, text: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { FamilyRecorder(context) }
    var state by remember { mutableStateOf(Take.IDLE) }
    var take by remember { mutableStateOf<File?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var rationale by remember { mutableStateOf(!micAllowed(context)) }
    /** The microphone was refused; after "don't ask again" only the system settings can allow it. */
    var refused by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            recorder.release()
            vm.family.voice.stop()
        }
    }

    fun stop() {
        take = recorder.stop()
        state = if (take != null) Take.REVIEW else Take.IDLE
        if (take == null) message = "🤔 ${bi("recordScreen.nothingRecorded")}. ${bi("recordScreen.tryAgain")}."
    }

    fun start() {
        vm.speaker.stop()
        message = null
        runCatching { recorder.start(onLimit = { stop() }) }
            .onSuccess { state = Take.RECORDING }
            .onFailure { message = "⚠️ ${bi("recordScreen.micUnavailable", "message" to it.message)}" }
    }

    // Leaving the app mid-take: stop and keep what was recorded instead of recording on in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (state == Take.RECORDING) stop()
        vm.family.voice.stop()
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        refused = !ok
        if (ok) start() else message = "🎙️ ${bi("recordScreen.needsMicrophone")}. ${bi("recordScreen.allowInSettings")}."
    }

    if (rationale) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text("🎙️ ${bi("common.microphone")}") },
            text = {
                Text(
                    bi("recordScreen.laniListensOnlyWhile", "learnerName" to vm.family.settings.learnerName),
                )
            },
            confirmButton = { TextButton(onClick = { rationale = false; permission.launch(Manifest.permission.RECORD_AUDIO) }) { Text(bi("common.allow")) } },
            dismissButton = { TextButton(onClick = onClose) { Text(bi("common.notNow")) } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (state != Take.RECORDING && state != Take.SAVING) onClose() },
        title = { Text(bi("recordScreen.readAloud"), style = MaterialTheme.typography.labelLarge) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                when (state) {
                    Take.IDLE -> BigRound("⏺", Labels.RECORD, TriglavRed, enabled = true) { if (micAllowed(context)) start() else permission.launch(Manifest.permission.RECORD_AUDIO) }
                    Take.RECORDING -> {
                        BigRound("⏹", Labels.STOP, TriglavRed, enabled = true) { stop() }
                        Text("${bi("recordScreen.recording")} (max ${FamilyRecorder.MAX_MS / 1000} s)", style = MaterialTheme.typography.bodySmall)
                    }
                    Take.REVIEW, Take.SAVING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { take?.let { vm.family.voice.playFile(it) } }, enabled = state == Take.REVIEW) { Text("▶ ${inTarget("recordScreen.listen")}") }
                        OutlinedButton(onClick = { vm.family.voice.stop(); start() }, enabled = state == Take.REVIEW) { Text("🔁 ${inTarget("recordScreen.again")}") }
                    }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
                if (refused && !micAllowed(context)) TextButton(onClick = { openAppSettings(context) }) { Text("⚙️ ${bi("common.settings")}") }
            }
        },
        confirmButton = {
            if (state == Take.REVIEW || state == Take.SAVING) {
                Button(
                    onClick = {
                        val f = take ?: return@Button
                        state = Take.SAVING
                        vm.family.voice.stop()
                        scope.launch {
                            runCatching { vm.family.upload(text, f) }
                                .onSuccess { vm.notices.banner = "✅ ${bi("recordScreen.saved")}: „$text“"; onClose() }
                                .onFailure { state = Take.REVIEW; message = "⚠️ ${it.message}" }
                        }
                    },
                    enabled = state == Take.REVIEW,
                    colors = ButtonDefaults.buttonColors(containerColor = AlpineGreen),
                ) { Text(if (state == Take.SAVING) bi("recordScreen.saving") else "✅ ${bi("recordScreen.save")}") }
            }
        },
        dismissButton = {
            TextButton(onClick = onClose, enabled = state != Take.RECORDING && state != Take.SAVING) { Text(bi("common.close")) }
        },
    )
}

@Composable
private fun BigRound(label: String, description: String, color: androidx.compose.ui.graphics.Color, enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = Modifier.size(84.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = color),
    ) { EmojiLabel(label, description, style = LocalTextStyle.current.copy(fontSize = 34.sp)) }
    Spacer(Modifier.height(2.dp))
}
