package si.lanisce.lani.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.Aim
import si.lanisce.lani.data.Dashboard
import si.lanisce.lani.l10n.bi

/**
 * After the first finished session, one light question on Home (docs/plans/05-built/first-minutes.md; app/FirstRun.pace): the
 * minutes a day and the aim, as they are marked; "✓ V redu" keeps what's marked, "Preskoči" leaves them. Either way it never
 * comes again: both stay in the settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaceCard(vm: AppViewModel, d: Dashboard) {
    var minutes by remember { mutableIntStateOf(d.goalMinutes.takeIf { it > 0 } ?: si.lanisce.lani.app.FirstRun.MINUTES) }
    val now = Aim.keep(d.targetLevel, d.level)
    var aim by remember { mutableStateOf(now) }
    // what's marked has to show on the card's own colour
    val marked = androidx.compose.material3.FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🌱 ${bi("firstRun.paceTitle")}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("⏱️ ${bi("hosted.goal")}", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (g in si.lanisce.lani.app.Aim.MINUTES) FilterChip(minutes == g, { minutes = g }, { Text("$g min") }, colors = marked)
            }
            Text("🎯 ${bi("setup.aim")}", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (a in Aim.near(d.level, now)) FilterChip(aim == a, { aim = a }, { Text(a) }, colors = marked)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.settlePace() }) { Text(bi("common.skip")) }
                Button(onClick = { vm.settlePace(minutes.takeIf { it != d.goalMinutes }, aim.takeIf { it != now }) }) { Text("✓ ${bi("myVoice.keep")}") }
            }
        }
    }
}
