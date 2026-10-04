package si.lanisce.lani.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.BridgeEvent
import si.lanisce.lani.ui.theme.SloBlue
import si.lanisce.lani.ui.theme.SloBlueDeep
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.l10n.inBase

@Composable
fun SetupScreen(vm: AppViewModel) {
    var url by remember { mutableStateOf("https://") }
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // centred while it fits; with the keyboard up (the address, the token) it scrolls above it instead of squeezing the
    // fields and the connect button together (safeDrawing has the keyboard in it)
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SloBlueDeep, SloBlue))).safeDrawingPadding()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text("🏔️", style = MaterialTheme.typography.displayLarge)
            Text(inTarget("setupAndPermission.welcome"), style = MaterialTheme.typography.displaySmall, color = Color.White)
            // No "Poveži" here: companion/bin/qa finds the connect button by that word.
            Text(
                bi("setupAndPermission.scanQrCodeFrom"),
                color = Color.White.copy(alpha = 0.85f),
            )
            BigButton("📷 ${bi("common.scanQrCode")}", onClick = { scanning = true }, color = Color.White.copy(alpha = 0.2f))
            // White on the blue gradient: the default field colours are dark-on-dark here.
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedLabelColor = Color.White, unfocusedLabelColor = Color.White.copy(alpha = 0.75f),
                focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = 0.6f),
                cursorColor = Color.White,
            )
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text(inBase("setupAndPermission.bridgeUrl")) }, singleLine = true, colors = fieldColors)
            OutlinedTextField(
                token, { token = it }, Modifier.fillMaxWidth(),
                label = { Text(inBase("setupAndPermission.token")) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), colors = fieldColors,
            )
            error?.let { Text("⚠️ $it", color = Color(0xFFFFD1D8)) }
            BigButton(
                if (busy) bi("setupAndPermission.connecting") else bi("setupAndPermission.connect"),
                enabled = !busy && url.length > 8 && token.isNotBlank(),
                onClick = {
                    busy = true
                    scope.launch {
                        error = vm.setup(url, token)
                        busy = false
                    }
                },
                color = TriglavRed,
                disabledColor = Color.White.copy(alpha = 0.2f),
            )
        }
    }
    // Paired: the app goes Home, and this screen with it.
    if (scanning) PairDialog(vm, onDismiss = { scanning = false })
}

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
