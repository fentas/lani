package si.lanisce.lani.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import si.lanisce.lani.data.BridgeEvent
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase

/** Relayed Claude Code tool approval. Whichever answer arrives first (here or in the terminal) wins. */
@Composable
fun PermissionDialog(req: BridgeEvent.PermissionRequest, onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("🔐 ${inBase("setupAndPermission.claudeWantsToUse", "tool" to req.tool)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(req.description)
                if (req.preview.isNotBlank()) {
                    Text(
                        req.preview,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                            .padding(10.dp),
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { onAnswer(true) }) { Text(bi("common.allow")) } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text(bi("setupAndPermission.deny")) } },
    )
}
