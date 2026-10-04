package si.lanisce.lani.ui.words

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.l10n.bi

/**
 * A line in the learner's target language whose words can be looked up: a tap on a word calls [onWord], and the
 * word shows marked while pressed. Punctuation, and the space around the text, don't count.
 *
 * [longPress]: only a long press looks a word up; a tap goes on to what the text is in (a choice picks itself).
 * [shown]: the characters typed in so far (a line typing in); the rest is laid out invisible and can't be tapped.
 * [marks]: words shown bold (what sets a choice apart from the others, see ChoiceDiff).
 * [onElse]: a tap that isn't on a word (beside it, on punctuation, not typed in yet), when a tap looks words up: a
 * dialog's line shows its hidden translation.
 * [onLongPress]: a long press anywhere on the text, when a tap looks words up: a dialog's line opens its grammar.
 * Null [onWord] is a plain [Text].
 */
@Composable
fun WordText(
    text: String,
    onWord: ((WordToken) -> Unit)?,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    shown: Int = text.length,
    longPress: Boolean = false,
    marks: List<WordToken> = emptyList(),
    onElse: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val tokens = remember(text) { WordTokens.of(text) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var pressed by remember(text) { mutableStateOf<WordToken?>(null) }
    val typed by rememberUpdatedState(shown)
    val look by rememberUpdatedState(onWord)
    val miss by rememberUpdatedState(onElse)
    val held by rememberUpdatedState(onLongPress)
    val holds = onLongPress != null
    val haptics = LocalHapticFeedback.current
    val mark = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val slop = with(LocalDensity.current) { 6.dp.toPx() }

    val bold = MaterialTheme.colorScheme.primary
    val shownText = remember(text, shown, pressed, mark, marks, bold) {
        buildAnnotatedString {
            append(text)
            marks.forEach { addStyle(SpanStyle(fontWeight = FontWeight.Black, color = bold), it.start, it.end) }
            pressed?.let { addStyle(SpanStyle(background = mark), it.start, it.end) }
            if (shown < text.length) addStyle(SpanStyle(color = Color.Transparent), shown.coerceAtLeast(0), text.length)
        }
    }

    /** The word under [pos], typed in already; a tap beside the text or on punctuation is none. */
    fun hit(pos: Offset): WordToken? {
        val l = layout ?: return null
        val t = WordTokens.at(tokens, l.getOffsetForPosition(pos)) ?: return null
        if (t.end > typed) return null
        return t.takeIf { l.getPathForRange(t.start, t.end).getBounds().inflate(slop).contains(pos) }
    }

    val gestures = when {
        onWord == null || tokens.isEmpty() -> Modifier
        !longPress -> Modifier.pointerInput(tokens, holds) {
            detectTapGestures(
                onPress = { pos ->
                    pressed = hit(pos)
                    tryAwaitRelease()
                    pressed = null
                },
                onTap = { pos ->
                    val word = hit(pos)
                    if (word != null) look?.invoke(word) else miss?.invoke()
                },
                onLongPress = if (holds) { _ -> held?.invoke() } else null,
            )
        }
        // A long press: watched without taking the touch, so a tap still reaches the choice around the text; once
        // it fires, the rest of the touch is swallowed, so lifting the finger doesn't pick the choice too.
        else -> Modifier.pointerInput(tokens) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val word = hit(down.position) ?: return@awaitEachGesture
                val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var over = false
                    while (!over) {
                        val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        // lifted, taken by a scroll, or moved away: not a long press
                        over = c == null || !c.pressed || c.isConsumed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop
                    }
                    true
                }
                if (ended != null) return@awaitEachGesture
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                pressed = word
                look?.invoke(word)
                do {
                    val e = awaitPointerEvent()
                    e.changes.forEach { it.consume() }
                } while (e.changes.any { it.pressed })
                pressed = null
            }
        }
    }

    Text(
        shownText,
        style = style,
        color = color,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        onTextLayout = { layout = it },
        modifier = modifier
            .then(if (onWord != null) Modifier.semantics { lookUpWords(text, onWord) } else Modifier)
            .then(gestures),
    )
}

/**
 * TalkBack's actions for [text]'s words, "Poišči «gozdu» · Look up «gozdu»" (each word once, the first [max]), after
 * [first] (a dialog's line: "Slovnica stavka · The sentence's grammar").
 * For a line whose semantics are set by what it's in (clearAndSetSemantics), there.
 */
fun SemanticsPropertyReceiver.lookUpWords(text: String, onWord: (WordToken) -> Unit, max: Int = 12, first: List<CustomAccessibilityAction> = emptyList()) {
    val words = WordTokens.of(text).distinctBy { it.text.lowercase() }.take(max)
    if (words.isEmpty() && first.isEmpty()) return
    customActions = first + words.map { t -> CustomAccessibilityAction(bi("words.lookUp", "word" to t.text)) { onWord(t); true } }
}
