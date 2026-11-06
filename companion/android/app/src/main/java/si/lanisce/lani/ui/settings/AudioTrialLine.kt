package si.lanisce.lani.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import si.lanisce.lani.data.AudioTrial
import si.lanisce.lani.l10n.Dates
import si.lanisce.lani.l10n.bi

/**
 * The audio trial's quiet line at the end of the settings' "🔊 Zvok in glas · Sound and voice", on this device's learner (data/
 * AudioTrial.kt): until when every voice is free, or that it ended, and the name library's rule in plain words. On a phone the
 * voice service can't check (its trial is the library's only), that the library's voices speak and why, with no date.
 */
@Composable
fun AudioTrialLine(t: AudioTrial, modifier: Modifier = Modifier) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("🔊 ${bi("audioTrial.title")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        val date = Dates.dayMonth(t.endDay())
        val line = when {
            // a phone the voice service can't check: what it has, quietly, once, and why (never sold, never nagged)
            t.tier == AudioTrial.LIBRARY -> bi("audioTrial.library")
            t.status == AudioTrial.ACTIVE -> bi("audioTrial.until", "date" to date)
            else -> bi("audioTrial.over", "date" to date)
        }
        Text(line, style = MaterialTheme.typography.bodySmall, color = quiet)
        Text(bi("audioTrial.names"), style = MaterialTheme.typography.bodySmall, color = quiet)
    }
}
