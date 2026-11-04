package si.lanisce.lani.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.FamilyShareController.Phase
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.setup.HoldToOpen
import si.lanisce.lani.ui.towns.QrImage

/**
 * "👪 Dodaj člana družine · Add a family member" in the settings (docs/plans/05-built/first-minutes.md): what the new device gets
 * and who pays, the QR code (once, three minutes, the same Wi-Fi), then the device that scanned it and what it will get, and
 * only a parent's hold sends it. Leaving the page ends the code. Not on a child's device.
 */
@Composable
fun FamilyShare(vm: AppViewModel) {
    val c = vm.familyShare
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var what by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { what = vm.familyWhat() }
    DisposableEffect(Unit) { onDispose { c.close() } }
    // what it is before a code is shown; once someone claimed one, only what the parent decides on
    if (c.phase !is Phase.Asking && c.phase !is Phase.Sent) {
        Text(bi("family.what"), style = MaterialTheme.typography.bodyMedium)
        Text(bi("family.chatgptNot"), style = MaterialTheme.typography.bodySmall, color = muted)
        // a family's computer has a learner of its own for each of them: this code doesn't make one (lani-profile add, lani-pair)
        if (!vm.hosted.active) Text(bi("family.computerNot"), style = MaterialTheme.typography.bodySmall, color = muted)
    }
    when (val p = c.phase) {
        Phase.Idle, Phase.Over, Phase.NoWifi -> {
            if (p == Phase.NoWifi) Text("📶 ${bi("family.noWifi")}", color = MaterialTheme.colorScheme.error)
            if (p == Phase.Over) Text("⌛ ${bi("family.over")}", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = c::show, modifier = Modifier.fillMaxWidth()) { Text("📱 ${bi("family.show")}") }
        }
        is Phase.Showing -> {
            Text(bi("family.lasts"), style = MaterialTheme.typography.bodyMedium)
            // dark on white whatever the theme: a camera reads it
            Box(Modifier.fillMaxWidth().background(Color.White).padding(16.dp), contentAlignment = Alignment.Center) { QrImage(p.text, 240.dp) }
            OutlinedButton(onClick = c::refuse, modifier = Modifier.fillMaxWidth()) { Text(bi("family.cancel")) }
        }
        is Phase.Asking -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("👪 ${bi("family.confirmTitle", "device" to p.device)}", style = MaterialTheme.typography.titleMedium)
            Text(bi("family.willShare"), style = MaterialTheme.typography.titleSmall)
            for (w in what) Text("• $w", style = MaterialTheme.typography.bodyMedium)
            Text(bi("family.whoPays"), style = MaterialTheme.typography.bodySmall, color = muted)
            HoldToOpen("🔒 ${bi("family.hold")}", onLight = true, onOpen = c::confirm)
            OutlinedButton(onClick = c::refuse, modifier = Modifier.fillMaxWidth()) { Text(bi("family.no")) }
        }
        is Phase.Sent -> {
            Text("✅ ${bi("family.sent", "device" to p.device)}", style = MaterialTheme.typography.titleMedium)
            Text(bi("family.stopping"), style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}
