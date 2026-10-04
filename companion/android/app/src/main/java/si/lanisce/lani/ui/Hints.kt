package si.lanisce.lani.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.Hints
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.l10n.bi

/** Known Slovene words for typing suggestions; empty turns them off. */
val LocalVocabulary = compositionLocalOf<List<String>> { emptyList() }

/** The words of every card Jan has (pack words once learned are cards too). */
@Composable
fun rememberVocabulary(vm: AppViewModel): List<String> {
    val pool = vm.content.dashboard?.pool
    return remember(pool) { Hints.vocabulary(pool.orEmpty().map { it.front }) }
}

/** [value] with [s] typed at the cursor (replacing a selection). */
internal fun insertAtCursor(value: TextFieldValue, s: String): TextFieldValue {
    val sel = value.selection
    return TextFieldValue(value.text.replaceRange(sel.min, sel.max, s), TextRange(sel.min + s.length))
}

/**
 * Up to four known words matching the word being typed; a tap replaces it. [reserve] keeps the
 * row's height when empty, so the input below doesn't jump.
 */
@Composable
fun Suggestions(
    value: TextFieldValue,
    vocabulary: List<String>,
    onChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    reserve: Boolean = false,
    onPicked: (String) -> Unit = {},
) {
    val cursor = value.selection.end
    val words = remember(value.text, cursor, vocabulary) {
        if (value.selection.collapsed) Hints.suggest(value.text, cursor, vocabulary) else emptyList()
    }
    if (words.isEmpty() && !reserve) return
    Box(modifier.fillMaxWidth().heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) {
        if (enabled) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (w in words) SuggestionChip(
                onClick = {
                    val (text, at) = Hints.replaceWord(value.text, cursor, w)
                    onChange(TextFieldValue(text, TextRange(at)))
                    onPicked(w)
                },
                label = { Text(w, fontSize = 16.sp) },
            )
        }
    }
}

/** 💡 next to Check; [used] hints so far change its label to a count. */
@Composable
fun HintButton(used: Int, onClick: () -> Unit, enabled: Boolean = true, of: Int? = null) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        if (used == 0) Text("💡 ${bi("common.hint")}")
        else EmojiLabel("💡 $used" + (of?.let { "/$it" } ?: ""), "${bi("common.hint")}: $used" + (of?.let { " / $it" } ?: ""))
    }
}

/** The soft strip under the input: the answer's shape and, at level 3, letters to tap in. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HintStrip(shown: Hints.Shown, enabled: Boolean, onLetter: (Char) -> Unit) {
    val used = remember(shown) { mutableStateListOf<Pair<Int, Int>>() }
    Surface(
        color = XpGold.copy(alpha = 0.16f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "💡 ${bi("hints.hintLevel", "level" to shown.level, "max" to Hints.MAX_LEVEL)} (${bi("hints.countsLittleLess")})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(shown.line, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
            // One run of letters per word, a wider gap between words; long words may wrap.
            if (shown.bank.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                shown.bank.forEachIndexed { g, group ->
                    if (g > 0) Spacer(Modifier.width(10.dp))
                    group.forEachIndexed { i, c ->
                        val gone = (g to i) in used
                        Surface(
                            onClick = { used += g to i; onLetter(c) },
                            enabled = enabled && !gone,
                            shape = MaterialTheme.shapes.small,
                            color = if (gone) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.size(width = 44.dp, height = 48.dp), // key-shaped, 48 dp tall to tap
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(if (gone) "" else c.toString(), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
