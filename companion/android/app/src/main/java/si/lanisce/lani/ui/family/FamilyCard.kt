package si.lanisce.lani.ui.family

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.FamilyChallenge
import si.lanisce.lani.data.Recording
import si.lanisce.lani.data.Voice
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.l10n.bi

/**
 * Home's "Družina · Family" tile opens this: the questions waiting for an answer, the latest answered ones
 * with the tutor's feedback, and the way into recording mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilySheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val f = vm.family
    val open = f.open
    val recent = f.challenges.filter { it.answer != null }.take(if (open.isEmpty()) 3 else 2)
    fun go(id: String?) {
        onDismiss()
        vm.openFamily(id)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💌 ${bi("homeScreen.family")}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                if (open.isNotEmpty()) Surface(color = TriglavRed, shape = RoundedCornerShape(50)) {
                    Text("${open.size}", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            if (open.isEmpty() && recent.isEmpty()) Text(
                bi("familyCard.familyCanSendQuestions"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (c in open.take(3)) ChallengeRow(c, status = "▸ ${bi("common.answer")}") { go(c.id) }
            for (c in recent) {
                val status = if (c.feedback != null) "🧑‍🏫 ${bi("common.feedback")}" else "⏳ ${bi("common.tutorReviewing")}"
                ChallengeRow(c, status) { go(c.id) }
            }
            OutlinedButton(onClick = { go(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("⏺ ${bi("common.familyRecords")}")
            }
        }
    }
}

@Composable
private fun ChallengeRow(c: FamilyChallenge, status: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
            Text(c.emoji)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            Text(c.text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clearAndSetSemantics {})
    }
}

/** "👵 Micka" while a family recording plays, anywhere in the app. */
@Composable
fun VoiceBadge(voice: Voice, modifier: Modifier = Modifier) {
    val now = voice.playing
    var last by remember { mutableStateOf<Recording?>(null) }
    if (now != null) last = now
    AnimatedVisibility(
        visible = now != null,
        modifier = modifier.statusBarsPadding().padding(top = 6.dp),
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        Surface(color = MaterialTheme.colorScheme.tertiary, shape = RoundedCornerShape(50), shadowElevation = 6.dp) {
            Text(
                "🔊 " + Voice.badge(last?.speaker.orEmpty()),
                color = MaterialTheme.colorScheme.onTertiary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}
