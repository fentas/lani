package si.lanisce.lani.ui.towns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.Sharing
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.theme.AlpineGreen

/**
 * A friend's card's part of "Progress between towns" (companion/README.md): what this learner shares with that town
 * ("👁️ Kaj vidi Teo · What Teo sees", a tap picks another level), what it shows this one, the child's note on a parent's
 * link ("👪 Tvoj starš vidi tvoj napredek"), and ([progressButton]) "📈 Napredek · Progress", which opens what the town
 * shares.
 */
@Composable
fun TownShareRow(vm: AppViewModel, id: String, learner: String, listed: Sharing?, progressButton: Boolean = true) {
    val s = vm.townProgress.sharingOf(id, listed) ?: return // an older bridge: nothing of progress
    var picking by remember { mutableStateOf(false) }
    val choices = TownProgressLogic.choices(s)
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TownProgressLogic.guardianNote(s)?.let { note ->
            Surface(color = AlpineGreen.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Text(note, Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        Box {
            TextButton(
                onClick = { if (choices.size > 1) picking = true },
                enabled = !vm.townProgress.saving,
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                Text(TownProgressLogic.shareLine(s, learner) + if (choices.size > 1) " ▾" else "", style = MaterialTheme.typography.bodyMedium)
            }
            DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                for (level in choices) DropdownMenuItem(
                    text = {
                        Column {
                            Text(TownProgressLogic.levelName(level) + if (level == s.share) "  ✓" else "", fontWeight = FontWeight.SemiBold)
                            Text(TownProgressLogic.levelAbout(level), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = {
                        picking = false
                        if (level != s.share || s.shareDefault) vm.townProgress.setShare(id, level, learner)
                    },
                )
            }
        }
        TownProgressLogic.theirLine(s, learner)?.let {
            Text(it, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (progressButton && TownProgressLogic.offersProgress(s)) FilledTonalButton(onClick = { vm.openTownProgress(id) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("📈 ${bi("townProgress.title")}")
        }
    }
}
