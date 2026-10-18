package si.lanisce.lani.ui.data

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.sync.DataSync
import si.lanisce.lani.sync.Handover
import si.lanisce.lani.sync.HandoverCode
import si.lanisce.lani.sync.HandoverOpen
import si.lanisce.lani.sync.HandoverParse
import si.lanisce.lani.sync.HandoverProblem
import si.lanisce.lani.sync.HandoverSecrets
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.QrCamera
import si.lanisce.lani.ui.cameraGranted
import si.lanisce.lani.ui.theme.TriglavRed

/** Why a handover code can't be used, "target · base". */
internal fun handoverText(p: HandoverProblem): String = when (p) {
    HandoverProblem.NOT_HANDOVER -> "🤔 ${bi("dataSync.handoverNotOne")}"
    HandoverProblem.NEWER_VERSION -> "⬆️ ${bi("dataSync.handoverNewer")}"
    HandoverProblem.INCOMPLETE -> "🧩 ${bi("dataSync.handoverIncomplete")}"
    HandoverProblem.EXPIRED -> "⌛ ${bi("dataSync.handoverExpired")}"
    HandoverProblem.WRONG_CODE -> "❌ ${bi("dataSync.handoverWrongCode")}"
    HandoverProblem.BROKEN -> "⚠️ ${bi("dataSync.handoverBroken")}"
    HandoverProblem.NOT_THAT_TOWN -> "🔒 ${bi("dataSync.handoverNotThatTown")}"
    HandoverProblem.OTHER_TOWN -> "🏘️ ${bi("dataSync.handoverOtherTown")}"
}

/**
 * "📷 Skeniraj kodo računalnika · Scan the computer's code": the QR code `lani handover` shows (the town's key and the
 * family token, sync/Handover.kt). With --code it asks for the code the computer printed. The secrets are kept where
 * the phone's bridge reads them; [onDone] gets the line to show (null: nothing happened).
 */
@Composable
fun HandoverScanDialog(d: DataSync, onDone: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(cameraGranted(context)) }
    var hint by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf<HandoverCode?>(null) }
    var typed by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf<HandoverSecrets?>(null) }
    var working by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    fun keep(s: HandoverSecrets, force: Boolean) = scope.launch {
        working = true
        when (val p = d.receive(s, replace = force)) {
            null -> {
                val town = s.townKey.fingerprint.chunked(4).take(4).joinToString(" ")
                val repo = s.repo?.full
                onDone("✅ ${bi("dataSync.received", "town" to town)}" + if (repo != null && d.settings.repo != repo) " ${bi("dataSync.receivedRepo", "repo" to repo)}" else "")
            }
            HandoverProblem.OTHER_TOWN -> replace = s
            else -> hint = handoverText(p)
        }
        working = false
    }

    // a typed code's key takes PBKDF2's 200,000 rounds: off the main thread
    fun open(c: HandoverCode, typedCode: String?) = scope.launch {
        working = true
        val o = withContext(Dispatchers.Default) { c.open(typedCode) }
        working = false
        when (o) {
            is HandoverOpen.Opened -> keep(o.secrets, force = false)
            is HandoverOpen.Failed -> hint = handoverText(o.problem)
        }
    }

    fun onScan(text: String) {
        if (code != null || working) return
        when (val p = Handover.parse(text)) {
            is HandoverParse.Invalid -> hint = handoverText(p.problem) // someone else's QR code: keep looking
            is HandoverParse.Ok -> {
                code = p.code
                hint = null
                if (!p.code.needsCode) open(p.code, null)
            }
        }
    }

    Dialog(onDismissRequest = { onDone(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("📷 ${bi("dataSync.scanHandover")}", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(bi("dataSync.scanWhat"), color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            val c = code
            if (c == null) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (granted) QrCamera(onCode = ::onScan, modifier = Modifier.fillMaxSize())
                    else Text("📷 ${bi("pairScan.needsCamera")}. ${bi("pairScan.onlyReadsCode")}.", color = Color(0xFFFFD1D8), textAlign = TextAlign.Center, modifier = Modifier.padding(20.dp))
                }
            } else if (c.needsCode) {
                Text(bi("dataSync.codePrompt"), color = Color.White, textAlign = TextAlign.Center)
                OutlinedTextField(
                    typed, { typed = it.uppercase().take(16) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                )
                BigButton("🔓 ${bi("dataSync.openCode")}", onClick = { open(c, typed) }, enabled = typed.length >= 12 && !working, color = TriglavRed)
            }
            if (working) CircularProgressIndicator(color = Color.White)
            hint?.let { Text(it, color = Color(0xFFFFD1D8), textAlign = TextAlign.Center) }
            if (hint != null && c != null && !c.needsCode) TextButton(onClick = { code = null; hint = null }) { Text(bi("pairScan.tryAgain"), color = Color.White) }
            TextButton(onClick = { onDone(null) }) { Text(bi("common.close"), color = Color.White) }
        }
    }
    replace?.let { s ->
        Confirm(bi("dataSync.handoverOtherTown"), bi("dataSync.replaceTownWhat"), bi("dataSync.replaceTown"), onCancel = { replace = null; onDone(null) }) {
            replace = null
            keep(s, force = true)
        }
    }
}
