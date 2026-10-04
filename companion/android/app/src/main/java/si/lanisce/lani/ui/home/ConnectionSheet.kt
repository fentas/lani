package si.lanisce.lani.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.ui.AmbienceSettings
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.bi

/**
 * "🔗 Povezava · Connection", from the tutor's status dot on Home: which tutor and learner this phone talks
 * to, and pairing again by QR code (a new code after the phone was unpaired, or instead of a typed token); and this
 * phone's settings: the languages, the background sounds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSheet(vm: AppViewModel, onScan: () -> Unit, onDismiss: () -> Unit) {
    val p = vm.paired
    val learner = p?.learner?.takeIf { it.isNotBlank() } ?: vm.content.dashboard?.name
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
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
        }
    }
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
