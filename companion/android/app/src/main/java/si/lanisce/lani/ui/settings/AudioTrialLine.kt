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
 * AudioTrial.kt): until when every voice is free, or that it ended, and the name library's rule in plain words.
 */
@Composable
fun AudioTrialLine(t: AudioTrial, modifier: Modifier = Modifier) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("🔊 ${bi("audioTrial.title")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        val date = Dates.dayMonth(t.endDay())
        Text(bi(if (t.status == AudioTrial.ACTIVE) "audioTrial.until" else "audioTrial.over", "date" to date), style = MaterialTheme.typography.bodySmall, color = quiet)
        Text(bi("audioTrial.names"), style = MaterialTheme.typography.bodySmall, color = quiet)
    }
}
