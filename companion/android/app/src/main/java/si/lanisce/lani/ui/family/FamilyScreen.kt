package si.lanisce.lani.ui.family

import android.app.Activity
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.Family
import si.lanisce.lani.data.FamilyChallenge
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.DiacriticChips
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.Markdown
import si.lanisce.lani.ui.game.RewardSummary
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget

/** Family mode: a partner challenge to answer, or (no id) recording words for Jan. */
@Composable
fun FamilyScreen(vm: AppViewModel, challengeId: String?) {
    BackHandler { vm.home() }
    if (challengeId == null) {
        RecordScreen(vm)
        return
    }
    val c = vm.family.challenge(challengeId)
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::home) { EmojiLabel("✕", Labels.CLOSE) }
            Text(c?.title ?: "💌 ${bi("familyScreen.fromFamily")}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        if (c == null) {
            Text(bi("common.loading"), Modifier.padding(24.dp))
            return@Column
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ChallengeBubble(vm, c)
            if (c.answer == null) AnswerBox(vm, c) else Thread(vm, c)
        }
        if (c.answer != null) {
            BigButton(bi("common.continue"), onClick = vm::home)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ChallengeBubble(vm: AppViewModel, c: FamilyChallenge) {
    var hint by remember(c.id) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
            Text(c.emoji, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.width(10.dp))
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(c.from.ifBlank { inTarget("homeScreen.family") }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    if (vm.speaker.available || vm.family.voice.has(c.text)) {
                        FilledTonalIconButton(onClick = { vm.speaker.say(c.text) }) { EmojiLabel("🔊", Labels.LISTEN) }
                    }
                }
                val h = c.hint
                if (h != null) {
                    if (hint) Text("💡 $h", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                    else TextButton(onClick = { hint = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("💡 ${bi("common.hint")}")
                    }
                }
            }
        }
    }
    Text(Family.instruction(c.type), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AnswerBox(vm: AppViewModel, c: FamilyChallenge) {
    var value by remember(c.id) { mutableStateOf(TextFieldValue("")) }
    var recognized by remember(c.id) { mutableStateOf<String?>(null) }
    var sending by remember(c.id) { mutableStateOf(false) }
    val listen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val heard = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && !heard.isNullOrBlank()) {
            recognized = heard
            value = TextFieldValue(heard, androidx.compose.ui.text.TextRange(heard.length))
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
        enabled = !sending,
        placeholder = { Text(if (c.type == "translate") bi("familyScreen.translation") else bi("familyScreen.typeOrSay")) },
        shape = MaterialTheme.shapes.medium,
    )
    DiacriticChips(value, { value = it })
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (vm.recognizer.dialog) {
            FilledTonalButton(
                onClick = {
                    vm.speaker.stop()
                    runCatching { listen.launch(vm.recognizer.intent(bi("common.saySlovene"))) }
                },
                enabled = !sending,
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text("🎤 ${bi("familyScreen.speak")}") }
        }
        BigButton(
            if (sending) bi("familyScreen.sending") else bi("common.send"),
            onClick = {
                sending = true
                val text = value.text.trim()
                vm.answerFamily(c.id, text, spoken = text == recognized?.trim()) { ok -> if (!ok) sending = false }
            },
            enabled = value.text.isNotBlank() && !sending,
            color = AlpineGreen,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Thread(vm: AppViewModel, c: FamilyChallenge) {
    val a = c.answer ?: return
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(if (a.spoken) bi("familyScreen.youMic") else bi("familyScreen.you"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(a.text, style = MaterialTheme.typography.titleMedium)
        }
    }
    vm.game.lastReward?.let { RewardSummary(it, onVillage = vm::openVillage) }
    val f = c.feedback
    if (f == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(bi("familyScreen.tutorLookingAt"), style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(onClick = { vm.family.refresh() }) { Text("↻ ${bi("familyScreen.refresh")}") }
        return
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text("🧑‍🏫 ${bi("common.tutor")}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                f.score?.let { Text("${"%.0f".format(it)}/10", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            }
            Markdown(f.text)
        }
    }
    f.partnerNote?.let {
        Text(bi("familyScreen.sees", "cEmoji" to c.emoji, "from" to c.from, "it" to it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
