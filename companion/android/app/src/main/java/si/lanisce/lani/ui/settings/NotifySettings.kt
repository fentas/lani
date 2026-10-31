package si.lanisce.lani.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.l10n.bi

/**
 * "🔔 Obvestila · Notifications" (companion/README.md, "Notifications"): "🔔 Večerni opomnik · Evening reminder" on or off
 * (on for an adult, off for a child until a parent turns it on: the page is a parent's on a child's device), and the way to
 * the phone's own settings of the app's notifications, where each kind (messages, the village, school …) can be silenced,
 * turned off or kept off the icon's dot.
 */
@Composable
internal fun NotifySettings(vm: AppViewModel) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("🔔 ${bi("notify.eveningReminder")}", style = MaterialTheme.typography.bodyLarge)
            Text(bi(if (vm.child) "notify.eveningReminderChild" else "notify.eveningReminderAbout"), style = MaterialTheme.typography.bodySmall, color = muted)
        }
        Switch(checked = vm.notify.reminderOn(vm.child), onCheckedChange = vm.notify::setReminder)
    }
    OutlinedButton(
        onClick = {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
        modifier = Modifier.heightIn(min = 48.dp),
    ) { Text("⚙️ ${bi("notify.phoneSettings")}") }
    Text(bi("notify.phoneSettingsAbout"), style = MaterialTheme.typography.bodySmall, color = muted)
}
