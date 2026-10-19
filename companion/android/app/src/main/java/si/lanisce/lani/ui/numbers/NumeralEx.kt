package si.lanisce.lani.ui.numbers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.NumbersPractice
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.AudioPrompt
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.Outcome
import si.lanisce.lani.ui.TypedEx
import si.lanisce.lani.ui.theme.AlpineGreen

/** [answer] to [ex] as the run's outcome: graded by the numbers' rules ([NumbersPractice.grade]), with its why. */
internal fun numeralOutcome(ex: Exercise.Numeral, answer: String): Outcome {
    val g = NumbersPractice.grade(ex, answer)
    return Outcome(g.verdict, answer.trim(), g.expected, g.why, marks = g.marks, hint = g.hint)
}

/**
 * A task of the numbers module (companion/README.md, "Numbers"): heard (🔊, 🐢) or read, then typed on the number pad; or
 * its figures, a date's sentence or a clock time shown and the word written, as any typed answer (the č, š, ž chips,
 * "Preveri · Check" above the keyboard, 💡).
 */
@Composable
fun NumeralEx(ex: Exercise.Numeral, locked: Boolean, done: (Outcome) -> Unit) {
    if (!ex.pad) {
        TypedEx(ex.prompt, null, locked, done, ex.instruction, accept = ex.accept) { a -> numeralOutcome(ex, a) }
        return
    }
    var typed by remember(ex) { mutableStateOf("") }
    ex.instruction?.let {
        Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
    if (ex.audio != null) AudioPrompt(ex.audio)
    else {
        // the word to read, large; no 🔊 (hearing it would be another task) and nothing to look up (it is what's asked)
        Text(
            ex.prompt, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
    }
    NumberPad(typed, { typed = it }, colon = ex.colon, enabled = !locked) { done(numeralOutcome(ex, typed)) }
}

/** The figures a pad holds at most. */
private const val MAX_FIGURES = 7

/**
 * The number pad: what is typed so far (large, read out as it changes), the keys 1–9, 0, ⌫ and (for a clock time) ":",
 * big enough for a thumb, and "Preveri · Check" ([onCheck]) once something is typed.
 */
@Composable
fun NumberPad(value: String, onChange: (String) -> Unit, colon: Boolean, enabled: Boolean, onCheck: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    fun press(k: String) {
        if (!enabled) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        when (k) {
            "⌫" -> onChange(value.dropLast(1))
            ":" -> if (':' !in value && value.isNotEmpty()) onChange("$value:")
            else -> if (value.count(Char::isDigit) < MAX_FIGURES) onChange(value + k)
        }
    }
    val nothing = bi("numbers.pad.nothing")
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp)
            .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = bi("numbers.pad.typed", "typed" to value.ifEmpty { nothing }) },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(12.dp)) {
            Text(
                value.ifEmpty { if (colon) "_:__" else "_" },
                style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                color = if (value.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(if (colon) ":" else "", "0", "⌫"))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in rows) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (k in row) {
                if (k.isEmpty()) Spacer(Modifier.weight(1f))
                else PadKey(k, enabled, Modifier.weight(1f)) { press(k) }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    if (enabled) BigButton(bi("playerScreen.check"), onClick = onCheck, enabled = value.any(Char::isDigit), color = AlpineGreen)
}

@Composable
private fun PadKey(k: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val label = when (k) {
        "⌫" -> bi("numbers.pad.delete")
        ":" -> bi("numbers.pad.colon")
        else -> k
    }
    Surface(
        onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.large,
        color = if (k == "⌫" || k == ":") MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (k == "⌫" || k == ":") MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.heightIn(min = 60.dp).semantics { role = Role.Button; contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.heightIn(min = 60.dp)) {
            Text(k, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}
