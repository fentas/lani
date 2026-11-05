package si.lanisce.lani.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.AudioTrial
import si.lanisce.lani.l10n.Dates
import si.lanisce.lani.l10n.bi

/**
 * The audio trial said on Home, once at its start and once after its end (data/AudioTrial.kt; Jan: "a learner sees what the
 * trial is and when it ends, plainly, once, and is never nagged"): what it is, the end date and what comes after, or that it
 * ended and what stays. One button; nothing to buy, no countdown, and it never comes again. A child's says the same without
 * what an account and a card are.
 */
@Composable
fun AudioTrialCard(vm: AppViewModel, card: AudioTrial.Card) {
    val t = vm.audioTrial.state
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().wrapContentWidth().widthIn(max = 720.dp).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val start = card == AudioTrial.Card.START
            val title = if (start) bi("audioTrial.startTitle", "days" to t.days) else bi("audioTrial.endTitle")
            Text("🔊 $title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val date = Dates.dayMonth(t.endDay())
            Text(
                when {
                    !start -> bi("audioTrial.endText")
                    vm.child -> bi("audioTrial.startTextChild", "date" to date)
                    else -> bi("audioTrial.startText", "date" to date)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = { vm.audioTrial.seen(card) }) { Text("✓ ${bi("myVoice.keep")}") }
            }
        }
    }
}
