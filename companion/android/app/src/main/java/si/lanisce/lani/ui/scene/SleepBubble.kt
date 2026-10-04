package si.lanisce.lani.ui.scene

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget

/** What a sleeper mumbles, tapped twice ([si.lanisce.lani.game.scene.Sleep]), in the language they speak (the village's). */
fun sleepyMumble(): String = inTarget("sceneScreen.letMeSleep")

/**
 * Over someone asleep, tapped again soon after they turned over: "Pusti me spati …" in the village's language ("Lasciami
 * dormire …" in the Italian village), what it means in the learner's base under it. Keyed by [key], so a new mumble pops in.
 */
@Composable
fun MumbleBubble(color: Color, key: Any?) {
    val said = sleepyMumble()
    val meant = inBase("sceneScreen.letMeSleep")
    Surface(color = color, shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 300.dp).popIn(key)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("💤", fontSize = 24.sp, modifier = Modifier.clearAndSetSemantics { })
            Spacer(Modifier.width(8.dp))
            Column {
                Text(said, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                if (meant != said) Text(meant, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
