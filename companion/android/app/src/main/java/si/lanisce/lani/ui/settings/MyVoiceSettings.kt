package si.lanisce.lani.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.MyVoiceController
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.MyVoiceOption
import si.lanisce.lani.data.MyVoices
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.LaniSheet

/**
 * "🗣️ Moj glas · My voice", at the top of the settings' "🔊 Zvok in glas · Sound and voice" (companion/SCENES.md, "The
 * learner's voice"; docs/plans/05-built/my-voice.md): the voices the learner's own lines may be said in (2–3 of their
 * gender and age, the default first), each its name and a ▶ that says its sample in it ("Ciao! Sono Teo."), and "🔇 Brez
 * glasu · No voice". A tap on a row chooses it: their next line is in it.
 */
@Composable
fun MyVoiceSettings(c: MyVoiceController, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) { c.load() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("🗣️ ${bi("myVoice.title")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(bi("myVoice.what"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MyVoiceChoices(c) { c.choose(it) }
    }
}

/**
 * "🗣️ Moj glas · My voice" offered once, at the first line of the learner's a dialog says ([MyVoiceController.offerAtLine]):
 * a sheet over the dialog, which goes on behind it. A tap on a voice chooses it and closes the sheet; ▶ only listens;
 * "✓ V redu · OK", or a tap beside the sheet, keeps the default, which the node then remembers as seen.
 */
@Composable
fun MyVoiceOffer(vm: AppViewModel) {
    val c = vm.myVoice
    LaniSheet(onDismissRequest = { c.closeOffer() }) {
        Column(
            Modifier.fillMaxWidth().wrapContentWidth().widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("🗣️ ${bi("myVoice.offerTitle")}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(bi("myVoice.offerText"), style = MaterialTheme.typography.bodyMedium)
            MyVoiceChoices(c) { id ->
                c.choose(id)
                c.closeOffer(picked = true)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(bi("myVoice.later"), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Button(onClick = { c.closeOffer() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("✓ ${bi("myVoice.keep")}") }
            }
        }
    }
}

/**
 * The voices to choose from and "🔇 Brez glasu · No voice", the one in effect marked; [onPick] gets the id chosen ("off"
 * for none). While the node's answer is on its way, a line says so; offline or from an older node, why there is none.
 */
@Composable
private fun MyVoiceChoices(c: MyVoiceController, onPick: (String) -> Unit) {
    val s = c.state
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        s == null && c.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(bi("myVoice.loading"), style = MaterialTheme.typography.bodySmall, color = muted)
        }
        s == null -> Text(
            bi(if (c.problem == MyVoiceController.Problem.OLD_NODE) "myVoice.oldNode" else "myVoice.offline"),
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
        s.options.isEmpty() -> Text(bi("myVoice.none"), style = MaterialTheme.typography.bodySmall, color = muted)
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (o in s.options) {
                VoiceRow(
                    emoji = faceOf(o),
                    name = o.name,
                    note = if (o.default) bi("myVoice.default") else null,
                    selected = !s.off && s.voice == o.id,
                    busy = c.saving == o.id,
                    onPick = { onPick(o.id) },
                    playing = c.playing == o.id,
                    onPlay = { c.sample(o) },
                )
            }
            VoiceRow(
                emoji = "🔇", name = bi("myVoice.off"), note = bi("myVoice.offWhat"), selected = s.off, busy = c.saving == MyVoices.OFF,
                onPick = { onPick(MyVoices.OFF) },
            )
        }
    }
    if (s != null && c.problem == MyVoiceController.Problem.NOT_KEPT) {
        Text("⚠️ ${bi("myVoice.notKept")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/** A voice's face: a boy's, a girl's, a man's, a woman's. */
private fun faceOf(o: MyVoiceOption): String = when {
    o.child && o.gender == Clips.MALE -> "👦"
    o.child -> "👧"
    o.gender == Clips.MALE -> "👨"
    else -> "👩"
}

/**
 * One voice: chosen with a tap anywhere on the row (a radio button's, for TalkBack), its face, its [name] and [note] (the
 * default; what "no voice" means), and its ▶ ([onPlay]; ⏹ while it is [playing]). At least 64 dp high: a child's finger on a
 * tablet, a thumb on a phone.
 */
@Composable
private fun VoiceRow(
    emoji: String,
    name: String,
    note: String?,
    selected: Boolean,
    busy: Boolean,
    onPick: () -> Unit,
    playing: Boolean = false,
    onPlay: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) scheme.primaryContainer else scheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.heightIn(min = 64.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).heightIn(min = 64.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onPick).padding(start = 4.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (busy) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                else RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(horizontal = 12.dp))
                Text(emoji, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clearAndSetSemantics { })
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
                }
            }
            if (onPlay != null) {
                val label = if (playing) bi("myVoice.stop") else bi("myVoice.listen", "name" to name)
                FilledTonalIconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(52.dp).semantics { contentDescription = label },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(),
                ) {
                    Text(if (playing) "⏹" else "▶", style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics { })
                }
            }
        }
    }
}
