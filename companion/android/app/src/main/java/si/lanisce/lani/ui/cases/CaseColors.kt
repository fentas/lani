package si.lanisce.lani.ui.cases

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.data.CaseLook
import si.lanisce.lani.data.alsoAsk
import si.lanisce.lani.data.ask
import si.lanisce.lani.data.asks
import si.lanisce.lani.data.darkArgb
import si.lanisce.lani.data.lightArgb
import si.lanisce.lani.game.Case
import si.lanisce.lani.l10n.bi

// The cases' look (data/CaseLook.kt, the contract with the case lens): each case its colour everywhere, the light theme's
// or the same hue lighter for the dark one, its name through the string tables, its question words.

/** [k]'s colour on this theme. */
@Composable
fun Case.color(): Color = Color(if (isSystemInDarkTheme()) darkArgb else lightArgb)

/** "2. rodilnik · genitive": the case's number and its name in the learner's pair (the string tables' [Case.key]). */
fun Case.label(): String = "$n. ${bi(key)}"

/** "osebek · the subject" or "predmet · the object": what decides the case when no word does. */
fun roleLabel(role: String): String = bi(if (role == "subject") "cases.role.subject" else "cases.role.object")

/** "množina · plural" for a number ("sg", "pl", "du"). */
fun numberLabel(n: String): String = bi(CaseLook.numberKey(n))

/** What TalkBack reads for a case: "2. rodilnik · genitive, koga? česa?". */
fun Case.spoken(): String = listOfNotNull(label(), ask, alsoAsk).joinToString(", ")

/**
 * A case as a small pill: its colour as a bar on the left, its number and name, its question words ([question]); [filled]
 * in its colour (the right one after an answer).
 */
@Composable
fun CasePill(k: Case, modifier: Modifier = Modifier, question: Boolean = true, filled: Boolean = false) {
    val c = k.color()
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (filled) c.copy(alpha = 0.28f) else c.copy(alpha = 0.12f),
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = k.spoken() },
    ) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(c).clearAndSetSemantics {})
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(k.label(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                if (question) Text(k.asks, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
