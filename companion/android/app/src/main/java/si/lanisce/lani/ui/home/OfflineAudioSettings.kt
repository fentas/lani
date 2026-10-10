package si.lanisce.lani.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import si.lanisce.lani.app.OfflineAudioController
import si.lanisce.lani.audio.AudioCap
import si.lanisce.lani.audio.MB
import si.lanisce.lani.audio.OfflineVoiceInfo
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * "🔊 Zvok brez povezave · Offline audio", in the connection sheet with the phone's other settings (companion/README.md,
 * "Voice": "Offline on the phone"): the cap (one number for the day's clips, the clips heard lately and the car's library),
 * "only while charging", the offline voice (its download, size and removal, its credit), what's on the phone now and
 * "⬇️ Prenesi zdaj · Download now".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OfflineAudioSettings(c: OfflineAudioController, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) {
        c.load()
        c.loadOffer()
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("🔊 ${bi("offlineAudio.title")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(bi("offlineAudio.what"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text(bi("offlineAudio.cap"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (cap in AudioCap.entries) {
                FilterChip(selected = c.cap == cap, onClick = { c.choose(cap) }, label = { Text(capLabel(cap)) })
            }
        }
        Text(
            if (c.cap == AudioCap.OFF) bi("offlineAudio.capOffHint") else bi("offlineAudio.capHint"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (c.cap.prefetch) {
            Row(
                Modifier.fillMaxWidth().toggleable(value = c.chargingOnly, role = Role.Switch, onValueChange = c::chargeOnly).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🔌 ${bi("offlineAudio.charging")}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = c.chargingOnly, onCheckedChange = null)
            }
        }

        OnPhone(c)

        OfflineVoiceRow(c)
    }
}

/** What's on the phone now, how the day's lines stand, and "⬇️ Prenesi zdaj". */
@Composable
private fun OnPhone(c: OfflineAudioController) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    c.usage?.let { u ->
        Text("📱 ${bi("offlineAudio.onPhone", "mb" to mb(u.total), "clips" to u.clips)}", style = MaterialTheme.typography.bodyMedium)
        if (u.road > 0 || u.day > 0) Text(bi("offlineAudio.split", "day" to mb(u.day), "road" to mb(u.road)), style = MaterialTheme.typography.bodySmall, color = muted)
    }
    if (c.cap.prefetch) {
        val s = c.status
        when {
            s == null || s.at == 0L -> Text(bi("offlineAudio.never"), style = MaterialTheme.typography.bodySmall, color = muted)
            else -> {
                if (s.wants > 0) Text("🗓️ ${bi("offlineAudio.today", "ready" to s.wanted + s.other, "all" to s.wants)}", style = MaterialTheme.typography.bodySmall)
                Text(bi("offlineAudio.updated", "time" to time(s.at)), style = MaterialTheme.typography.bodySmall, color = muted)
                if (s.full) Text("⚠️ ${bi("offlineAudio.full")}", style = MaterialTheme.typography.bodySmall, color = muted)
                s.problem?.let { Text("⚠️ ${bi("offlineAudio.problem", "why" to it)}", style = MaterialTheme.typography.bodySmall, color = muted) }
            }
        }
        if (c.prefetching) {
            Text(bi("offlineAudio.downloading"), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            OutlinedButton(onClick = c::downloadNow) { Text("⬇️ ${bi("offlineAudio.downloadNow")}") }
        }
    }
}

/** "🗣️ Glas brez povezave · Offline voice": on the phone (its size, remove), on its way, or offered (download, size). */
@Composable
private fun OfflineVoiceRow(c: OfflineAudioController) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val v = c.voice
    val o = c.offered
    Text("🗣️ ${bi("offlineAudio.voice")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
    Text(bi("offlineAudio.voiceWhat", "name" to (v ?: o)?.name.orEmpty().ifBlank { "Piper" }), style = MaterialTheme.typography.bodySmall, color = muted)
    val p = c.voiceProgress
    when {
        p != null -> {
            val percent = if (p.second > 0) (p.first * 100 / p.second).toInt() else 0
            Text(bi("offlineAudio.voiceProgress", "percent" to percent), style = MaterialTheme.typography.bodySmall)
            if (p.second > 0) LinearProgressIndicator(progress = { p.first.toFloat() / p.second }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        v != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("✅ ${bi("offlineAudio.voiceOn", "name" to v.name, "mb" to mb(v.files.sumOf { it.bytes }))}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = c::removeVoice) { Text("🗑️ ${bi("offlineAudio.voiceRemove")}") }
        }
        o != null -> {
            Button(onClick = c::downloadVoice) { Text("⬇️ ${bi("offlineAudio.voiceDownload", "mb" to mb(o.bytes))}") }
            Text(bi("offlineAudio.wifi"), style = MaterialTheme.typography.bodySmall, color = muted)
        }
        else -> Text(bi("offlineAudio.voiceNone"), style = MaterialTheme.typography.bodySmall, color = muted)
    }
    c.voiceProblem?.takeIf { p == null && v == null }?.let { Text("⚠️ ${bi("offlineAudio.voiceFailed", "why" to it)}", style = MaterialTheme.typography.bodySmall, color = muted) }
    // the attribution is the voice's own (English): the credit once, in the learner's base language
    (v ?: o)?.let(::credit)?.let { Text(inBase("offlineAudio.voiceCredit", "attribution" to it), style = MaterialTheme.typography.bodySmall, color = muted) }
}

/**
 * After pairing, once: "Prenesi glas brez povezave (61 MB)? · Download the offline voice (61 MB)?" (Wi-Fi recommended); the
 * settings have it too.
 */
@Composable
fun OfflineVoiceOffer(c: OfflineAudioController) {
    val o = c.offered ?: return
    if (!c.offer) return
    AlertDialog(
        onDismissRequest = c::declineOffer,
        title = { Text("🗣️ ${bi("offlineAudio.offerTitle", "mb" to mb(o.bytes))}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(bi("offlineAudio.offerText", "name" to o.name.ifBlank { "Piper" }))
                credit(o)?.let { Text(inBase("offlineAudio.voiceCredit", "attribution" to it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        confirmButton = { Button(onClick = c::downloadVoice) { Text("⬇️ ${bi("offlineAudio.offerYes")}") } },
        dismissButton = { TextButton(onClick = c::declineOffer) { Text(bi("offlineAudio.offerNo")) } },
    )
}

private fun capLabel(cap: AudioCap): String = when (cap) {
    AudioCap.OFF -> bi("offlineAudio.capOff")
    AudioCap.UNLIMITED -> bi("offlineAudio.capUnlimited")
    else -> inTarget("offlineAudio.capMb", "mb" to (cap.bytes!! / MB).toString()) // "250 MB" in either language: once
}

/** "61" (MB, rounded), "0.4" below one: a size as the settings show it. */
private fun mb(bytes: Long): String = if (bytes in 1 until MB) "%.1f".format(bytes.toDouble() / MB) else ((bytes + MB / 2) / MB).toString()

/** "14:05" today, else "8. 10. 14:05". */
private fun time(at: Long): String {
    val t = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    return if (t.toLocalDate() == LocalDate.now()) t.format(DateTimeFormatter.ofPattern("HH:mm")) else t.format(DateTimeFormatter.ofPattern("d. M. HH:mm"))
}

/** The voice's credit, as its licence asks: "Artur, ARTUR studio TTS, CC BY 4.0". */
private fun credit(v: OfflineVoiceInfo): String? =
    listOfNotNull(v.attribution?.takeIf { it.isNotBlank() }, v.licence?.takeIf { it.isNotBlank() && v.attribution?.contains(it) != true })
        .joinToString(", ").ifBlank { null }
