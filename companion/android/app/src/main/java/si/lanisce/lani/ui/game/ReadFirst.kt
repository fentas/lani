package si.lanisce.lani.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.Words
import si.lanisce.lani.game.ReadFirst
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.ChatButton
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.SayButton
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.stage.StageBand
import si.lanisce.lani.ui.stage.StageState
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.ui.words.WordText
import si.lanisce.lani.ui.words.lookUpIn
import si.lanisce.lani.ui.words.lookUpWords

// "Read first, then answer" (companion/GAME.md): a text is read with its voice as long as the learner likes, then hidden
// while its questions come one at a time; "👁 Pokaži besedilo · Show the text" brings it back over the question.

/**
 * A text to read first ([ReadFirst]: the day's letter): "🔊 Poslušaj · Listen" for the whole of it, then its sentences,
 * each with its 🔊 and its words to tap for their cards. [voice]: who reads it (the narrator's, as its prompt was read).
 */
@Composable
fun ReadFirstText(vm: AppViewModel, text: ReadFirst, modifier: Modifier = Modifier, voice: String = Clips.FEMALE) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (vm.speaker.canSay) SayButton(vm.speaker, text.text, Modifier.fillMaxWidth(), voiceName = voice) {
            Text("🔊 ${bi("common.listen")}", style = MaterialTheme.typography.titleMedium)
        }
        for (p in text.paragraphs) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (s in p) Row(verticalAlignment = Alignment.CenterVertically) {
                val onWord = lookUpIn(vm, s, null, Words.READING)
                WordText(
                    s, onWord, Modifier.weight(1f).clearAndSetSemantics { contentDescription = s; lookUpWords(s, onWord) },
                    style = MaterialTheme.typography.titleMedium,
                )
                SpeakButton(vm.speaker, s, Modifier.padding(start = 4.dp), voiceName = voice)
            }
        }
    }
}

/** A text on paper: [content] on a warm sheet, set apart from the question around it. */
@Composable
fun Paper(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(color = XpGold.copy(alpha = 0.12f), shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

/**
 * "👁 Pokaži besedilo · Show the text", and once it shows, "🙈 Skrij besedilo · Hide the text": the text over the
 * question and back, as often as needed.
 */
@Composable
fun PeekButton(peeking: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val label = if (peeking) "🙈 ${bi("reading.hideText")}" else "👁 ${bi("reading.showText")}"
    if (peeking) FilledTonalButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
    else OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
}

/**
 * Under a text read first: that it hides once started and its questions come one at a time, and how looking back counts
 * ([rule]: [peekRuleText]).
 */
@Composable
fun ReadFirstNote(rule: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("💡 ${bi("reading.startAbout")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("👁 $rule", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "▶️ Začni · Start": the text hides and the first question comes. */
@Composable
fun StartReadingButton(onStart: () -> Unit) = BigButton("▶️ ${bi("reading.start")}", onClick = onStart)

/**
 * A run's text read first, on the stage (the day's letter, companion/GAME.md "The day's surprise"): the run's [title],
 * the text with its voice and words to look up, for as long as the learner likes; "▶️ Začni · Start" hides it and the
 * questions come ([onStart]). ✕ and back: [onExit]. [rule]: how looking back counts, at the learner's level.
 */
@Composable
fun ReadFirstRun(vm: AppViewModel, title: String, text: ReadFirst, stage: StageState?, rule: String, onStart: () -> Unit, onExit: () -> Unit) {
    BackHandler(onBack = onExit)
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onExit) { EmojiLabel("✕", Labels.CLOSE) }
            Spacer(Modifier.weight(1f))
            ChatButton(vm)
        }
        stage?.let { StageBand(it, Modifier.padding(top = 8.dp)) }
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(text.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Paper { ReadFirstText(vm, text) }
            ReadFirstNote(rule)
        }
        StartReadingButton(onStart)
    }
}

/** The result's line about looking back ([looksText]), read out when it shows. */
@Composable
fun LooksLine(text: String?, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    text ?: return
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite })
}
