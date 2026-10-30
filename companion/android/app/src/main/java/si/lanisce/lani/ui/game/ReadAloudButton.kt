package si.lanisce.lani.ui.game

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import si.lanisce.lani.data.HintVoice
import si.lanisce.lani.game.HintPiece
import si.lanisce.lani.l10n.bi

/**
 * A screen's 🔊 (the founding, the news sheet: [si.lanisce.lani.game.ReadAloud]), as the village's first words have it:
 * a tap reads [speech] ([HintVoice]: the language learned in the narrator's voice, the base by the phone's), a tap again
 * stops it; it spins while the pieces are got ready and shows ⏹ while they play. Leaving the screen stops it. TalkBack
 * hears "Preberi na glas · Read aloud". Nothing without a [voice] (a preview).
 */
@Composable
fun ReadAloudButton(voice: HintVoice?, speech: List<HintPiece>, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    if (voice == null || speech.isEmpty()) return
    val mine = voice.current == speech
    // the base's pieces rendered ahead, so a tap plays at once (nothing is asked of the node before the tap)
    LaunchedEffect(speech) { voice.warm(speech) }
    DisposableEffect(speech) { onDispose { if (voice.current == speech) voice.stop() } }
    TextButton(
        onClick = { voice.toggle(speech, needsBase = false) },
        modifier = modifier.semantics { contentDescription = if (mine) bi("components.stop") else bi("gameIntro.readAloud") },
    ) {
        if (mine && voice.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color)
        else Text(if (mine) "⏹" else "🔊", style = MaterialTheme.typography.titleLarge, color = color)
    }
}
