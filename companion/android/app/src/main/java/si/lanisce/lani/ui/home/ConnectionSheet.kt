package si.lanisce.lani.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.ui.AmbienceSettings
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.LaniSheet
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.bi

/**
 * "🔗 Povezava · Connection", from the tutor's status dot on Home: which tutor and learner this phone talks
 * to, and pairing again by QR code (a new code after the phone was unpaired, or instead of a typed token); and this
 * phone's settings: the languages, the background sounds, the school's explanations read aloud, the offline audio, and
 * (hidden until the version is tapped seven times) the developer's.
 */
@Composable
fun ConnectionSheet(vm: AppViewModel, onScan: () -> Unit, onDismiss: () -> Unit) {
    val p = vm.paired
    val learner = p?.learner?.takeIf { it.isNotBlank() } ?: vm.content.dashboard?.name
    LaniSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("🔗 ${bi("connectionSheet.connection")}", style = MaterialTheme.typography.titleLarge)
            Text(linkLabel(vm.link, vm.unpaired), style = MaterialTheme.typography.bodyMedium, color = if (vm.unpaired) TriglavRed else MaterialTheme.colorScheme.onSurface)
            learner?.let { InfoLine("🧒", bi("connectionSheet.learner"), it + if (p?.child == true) " · 🛡️" else "") }
            vm.bridgeUrl?.let { InfoLine("🖥️", bi("common.tutor"), it.removePrefix("https://").removePrefix("http://")) }
            InfoLine(
                "📱", bi("connectionSheet.phone"),
                if (p != null) "${p.deviceName} (${bi("connectionSheet.pairedByQrCode")})" else bi("connectionSheet.typedToken"),
            )
            Text(
                if (vm.unpaired) bi("connectionSheet.runLaniPairComputer")
                else bi("connectionSheet.pairPhoneAgainRun"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            BigButton("📷 ${bi("common.scanQrCode")}", onClick = onScan, color = TriglavRed)
            Languages(vm)
            AmbienceSettings(vm.ambience, Modifier.padding(top = 12.dp))
            si.lanisce.lani.ui.school.ReadSetting(vm, Modifier.padding(top = 4.dp))
            OfflineAudioSettings(vm.offlineAudio, Modifier.padding(top = 12.dp))
            Developer(vm)
        }
    }
}

/**
 * "🛠️ Razvijalec · Developer": hidden until the version line at the bottom is tapped seven times (in the release app
 * too), then the developer's settings: the shadow tutor (tutor/ShadowTutor, docs/plans/05-phase-0.md check 6), on or off,
 * and its debug panel. Turning it on without a key opens the panel, which asks for one. While they are shown, the app's
 * log and the shadow's export go to the paired node (app/DevUpload), and the section says so.
 */
@Composable
private fun Developer(vm: AppViewModel) {
    val shadow = vm.shadow
    val shown = shadow.settings.developer
    var taps by remember { mutableIntStateOf(0) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (shown) {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🛠️ ${bi("developer.title")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🕵️ ${bi("developer.shadowTutor")}", style = MaterialTheme.typography.bodyLarge)
                    Text(shadow.status, style = MaterialTheme.typography.bodySmall, color = muted)
                }
                Switch(
                    checked = shadow.settings.enabled,
                    onCheckedChange = { on ->
                        shadow.setEnabled(on)
                        if (on && !shadow.hasKey) shadow.panelOpen = true
                    },
                )
            }
            Text(bi("developer.shadowWhat"), style = MaterialTheme.typography.bodySmall, color = muted)
            // the developer's data on the node while these are on (app/DevUpload, companion/README.md "Developer data on the node")
            Text("📤 ${bi("developer.toNode")}", style = MaterialTheme.typography.bodySmall, color = muted)
            OutlinedButton(onClick = { shadow.panelOpen = true }) { Text("🔍 ${bi("developer.openPanel")}") }
            TextButton(onClick = { shadow.showDeveloper(false) }) { Text(bi("developer.hide")) }
        }
    }
    Text(
        "Lani ${si.lanisce.lani.BuildConfig.VERSION_NAME}", // a name and a number: the same in every language
        style = MaterialTheme.typography.bodySmall,
        color = muted,
        modifier = Modifier.padding(top = 16.dp).clickable {
            if (shown) return@clickable
            taps++
            if (taps >= 7) {
                taps = 0
                shadow.showDeveloper(true)
                vm.notices.banner = "🛠️ ${bi("developer.shown")}"
            }
        },
    )
}

@Composable
private fun InfoLine(emoji: String, label: String, value: String) {
    Column {
        Text("$emoji $label", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * "🌐 Jeziki · Languages": the pair the app shows, the learner profile's or one chosen here (for testing, and the second learner's
 * phone). Another pair builds every screen anew, this sheet too.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Languages(vm: AppViewModel) {
    val now = vm.langPair
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("🌐 ${bi("connectionSheet.languages")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        FilterChip(
            selected = vm.langPairChosen == null,
            onClick = { vm.chooseLangPair(null) },
            label = { Text(bi("connectionSheet.asInProfile", "pair" to vm.langPairOfProfile)) },
        )
        for ((label, pick) in listOf(
            bi("connectionSheet.target") to { l: Lang -> now.withTarget(l) },
            bi("connectionSheet.base") to { l: Lang -> now.withBase(l) },
        )) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (l in Lang.entries) {
                    val p = pick(l)
                    FilterChip(selected = vm.langPairChosen != null && p == now, onClick = { vm.chooseLangPair(p) }, label = { Text(l.ownName) })
                }
            }
        }
    }
}
