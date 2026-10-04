package si.lanisce.lani.ui.road

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.road.Kind
import si.lanisce.lani.road.RoadProgress
import si.lanisce.lani.road.RoadRemote
import si.lanisce.lani.road.RoadService
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.theme.TriglavRed
import java.time.LocalDate

/**
 * "🚗 Za pot · For the road" (companion/README.md, "Im Auto · In the car"): getting the car's sessions ready on the phone,
 * with its progress (the work goes on in the background, road/RoadPrepService; the sessions play once the first block is
 * there), and a plain player for Bluetooth without Android Auto (the sessions, ⏮ ⏯ ⏭, ✓ and ✗). No exercises:
 * while driving, the steering wheel or Android Auto does it all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoadSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val prep = vm.road
    val remote = vm.roadRemote
    DisposableEffect(remote) {
        remote.connect()
        onDispose { remote.release() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("🚗 ${bi("road.title")}", style = MaterialTheme.typography.titleLarge)
            Text(bi("road.howItWorks"), style = MaterialTheme.typography.bodyMedium)
            val lib = prep.library?.takeIf { it.items.isNotEmpty() }
            val files = prep.progress as? RoadProgress.Files
            if (lib != null) {
                when {
                    prep.done -> Text("✅ ${bi("road.done", "time" to (prep.time ?: ""))}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    // while it gets ready, the progress below says what plays already
                    files?.playable != true -> Text("✅ ${bi("road.ready", "time" to (prep.time ?: ""))}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "🔁 ${lib.due(LocalDate.now().toString()).size} · 🆕 ${lib.of(Kind.WORD).size} · 💬 ${lib.of(Kind.DIALOG).size} · 📖 ${lib.of(Kind.STORY).size} · 🗣️ ${lib.of(Kind.PHRASE).size}" +
                        " · 🔄 ${lib.of(Kind.TRANSFORM).size} · ⚡ ${lib.of(Kind.RAPID).size} · 🧱 ${lib.of(Kind.BUILD).size} · 🕵️ ${lib.of(Kind.RIDDLE).size}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(bi("road.notReady"), style = MaterialTheme.typography.bodyMedium)
            }
            when (val p = prep.progress) {
                null -> Unit
                RoadProgress.Gathering -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(bi("road.gathering"), style = MaterialTheme.typography.bodySmall)
                }
                is RoadProgress.Files -> {
                    // in the background (road/RoadPrepService): what plays first first, playable once the first block is there
                    LinearProgressIndicator(progress = { p.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("🚗 ${bi("road.preparing")} … ${p.percent} %", style = MaterialTheme.typography.bodySmall)
                    Text(bi("road.clips", "done" to p.clips, "total" to p.clipsTotal), style = MaterialTheme.typography.bodySmall)
                    Text(bi("road.prompts", "done" to p.prompts, "total" to p.promptsTotal), style = MaterialTheme.typography.bodySmall)
                    if (p.playable) Text("▶️ ${bi("road.playableNow", "time" to (prep.time ?: ""))}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(bi("road.inBackground"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            prep.problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TriglavRed) }
            BigButton(
                "🚗 ${if (lib == null) bi("road.prepare") else bi("road.prepareAgain")}",
                onClick = vm::prepareRoad,
                enabled = !prep.running,
                color = if (lib == null) TriglavRed else MaterialTheme.colorScheme.secondary,
            )
            if (lib != null) Player(remote, due = lib.due(LocalDate.now().toString()).size)
            Text(bi("road.safety"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The sessions, and big buttons for what plays. */
@Composable
private fun Player(remote: RoadRemote, due: Int) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val sessions = listOf(
            RoadService.MIX to "🚗 ${bi("road.title")}",
            RoadService.EASY to "🌙 ${bi("road.easy")}",
            RoadService.SHADOW to "🗣️ ${bi("road.shadow")}",
            RoadService.REVIEWS to "🔁 ${bi("road.reviews", "count" to due)}",
            RoadService.WORDS to "🆕 ${bi("road.words")}",
            RoadService.DIALOGS to "💬 ${bi("road.dialogs")}",
            RoadService.STORIES to "📖 ${bi("road.stories")}",
            RoadService.TRANSFORMS to "🔄 ${bi("road.transform")}",
            RoadService.RAPID to "⚡ ${bi("road.rapid")}",
            RoadService.BUILDS to "🧱 ${bi("road.build")}",
            RoadService.RIDDLES to "🕵️ ${bi("road.riddles")}",
        )
        for ((id, label) in sessions) {
            OutlinedButton(onClick = { remote.play(id) }, enabled = remote.connected, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
        Text(
            remote.title?.let { t -> listOfNotNull(t, remote.subtitle).joinToString(" · ") } ?: bi("road.nothingPlaying"),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Big("⏮", Modifier.weight(1f), remote.connected) { remote.again() }
            Big(if (remote.playing) "⏸" else "▶", Modifier.weight(1f), remote.connected) { remote.toggle() }
            Big("⏭", Modifier.weight(1f), remote.connected) { remote.next() }
        }
        if (remote.rateable) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton("✓ ${bi("road.knew")}", onClick = { remote.rate(true) }, modifier = Modifier.weight(1f))
            BigButton("✗ ${bi("road.didnt")}", onClick = { remote.rate(false) }, modifier = Modifier.weight(1f), color = TriglavRed)
        }
    }
}

@Composable
private fun Big(label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 64.dp)) {
        Text(label, fontSize = 28.sp)
    }
}
