package si.lanisce.lani.ui.towns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.VisitVoicePolicy
import si.lanisce.lani.audio.MB
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.Labels

/**
 * On a visit, once, when a line can't be heard (companion/README.md, "Voices on a visit"; app/VisitVoiceController): the
 * offer of the town language's offline voice ("🗣️ Prenesi slovenski glas za obiske (61 MB)? · Download the Slovene voice
 * for visits?") as a dialog, or the small note "🔇 Ta naprava nima glasu za slovenščino · No Slovene voice on this
 * device" over the screen, with the download when the node has it, its progress, and the voice on the phone once it came.
 * The line's text stays on screen meanwhile: nothing covers the dialog's lines. At home, the same for a word of another
 * language nothing could say (its card: [si.lanisce.lani.data.Speaker.sayIn]), named in that language ("targetLang").
 */
@Composable
fun VisitVoiceNote(vm: AppViewModel, modifier: Modifier = Modifier) {
    val v = vm.visitVoice
    val p = v.prompt ?: return
    val audio = vm.offlineAudio
    if (p.kind == VisitVoicePolicy.Kind.OFFER) {
        AlertDialog(
            onDismissRequest = v::dismiss,
            title = { Text("🗣️ ${bi("visitVoice.offerTitle", "mb" to mb(p.bytes), "targetLang" to p.language)}") },
            text = { Text(bi("visitVoice.offerText", "name" to p.name.ifBlank { "Piper" }, "targetLang" to p.language)) },
            confirmButton = { Button(onClick = v::download) { Text("⬇️ ${bi("offlineAudio.offerYes")}") } },
            dismissButton = { TextButton(onClick = v::decline) { Text(bi("offlineAudio.offerNo")) } },
        )
        return
    }
    val progress = audio.visitProgress[p.language]
    val ready = audio.visitVoices.any { it.language == p.language }
    val problem = audio.visitProblem[p.language]
    // the voice came: said for a moment, then the note goes
    if (ready) LaunchedEffect(p.language) {
        delay(READY_MS)
        v.dismiss()
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 8.dp,
        modifier = modifier.statusBarsPadding().padding(top = 72.dp, start = 12.dp, end = 12.dp).widthIn(max = 520.dp).fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (ready) "✅ ${bi("visitVoice.ready", "targetLang" to p.language)}" else "🔇 ${bi("visitVoice.none", "targetLang" to p.language)}",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = v::dismiss) { EmojiLabel("✕", Labels.CLOSE) }
            }
            when {
                ready -> Unit
                progress != null -> {
                    val percent = if (progress.second > 0) (progress.first * 100 / progress.second).toInt() else 0
                    Text(bi("visitVoice.downloading", "percent" to percent), style = MaterialTheme.typography.bodySmall)
                    if (progress.second > 0) LinearProgressIndicator(progress = { progress.first.toFloat() / progress.second }, modifier = Modifier.fillMaxWidth().padding(end = 10.dp))
                    else LinearProgressIndicator(Modifier.fillMaxWidth().padding(end = 10.dp))
                }
                p.kind == VisitVoicePolicy.Kind.NOTICE_DOWNLOAD -> {
                    problem?.let { Text("⚠️ ${bi("visitVoice.failed", "why" to it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    FilledTonalButton(onClick = v::download, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("⬇️ ${bi("visitVoice.download", "mb" to mb(p.bytes))}")
                    }
                }
                else -> Unit
            }
        }
    }
}

/** How long "✅ the voice is on the phone" shows before the note goes. */
private const val READY_MS = 4_000L

/** "61" (MB, rounded), "0.4" below one; "?" when the node didn't say. */
private fun mb(bytes: Long): String = when {
    bytes <= 0 -> "?"
    bytes < MB -> "%.1f".format(bytes.toDouble() / MB)
    else -> ((bytes + MB / 2) / MB).toString()
}
