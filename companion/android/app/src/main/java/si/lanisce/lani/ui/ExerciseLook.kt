package si.lanisce.lani.ui

import android.util.Log
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.BuildConfig
import si.lanisce.lani.app.WordQuery
import si.lanisce.lani.data.DialogPrefs
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.VisitWorld
import si.lanisce.lani.data.WordFormsWire
import si.lanisce.lani.data.Words
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.words.ExerciseTaps
import si.lanisce.lani.ui.words.KnownWords
import si.lanisce.lani.ui.words.TextParts
import si.lanisce.lani.ui.words.WordText
import si.lanisce.lani.ui.words.WordToken

/**
 * The words of the exercise on screen, to look up (ExerciseRun): which ones ([taps]: never what it tests while it's
 * open), the learner's own ([known]: the others are dotted), their card ([open]), and the tip about them the first time
 * ([tip]: this exercise has words to look up; shown under its first prompt until closed, a word looked up, or the next
 * exercise).
 */
@Stable
internal class ExerciseLook(
    val taps: ExerciseTaps,
    val known: KnownWords?,
    private val tip: Boolean,
    private val prefs: DialogPrefs,
    /** A sentence with a gap filled with its answer (what a word added from the card keeps), else null. */
    private val filled: String?,
    private val open: (word: WordToken, line: String, example: String?) -> Unit,
) {
    private var claimed = false

    /** Whether the tip goes here: under the first prompt that asks (once per exercise), while it isn't seen yet. */
    fun claimTip(): Boolean {
        if (!tip || claimed) return false
        claimed = true
        return true
    }

    val tipShows: Boolean get() = !prefs.exerciseWordHintSeen

    fun tipSeen() = prefs.sawExerciseWordHint()

    /** A tap on [word] of [line]: its card; the line's gap filled is what a word added from it keeps. */
    fun look(word: WordToken, line: String) = open(word, line, filled?.takeIf { ExerciseTaps.GAP.containsMatchIn(line) })
}

internal val LocalLook = compositionLocalOf<ExerciseLook?> { null }

/** The learner's words in [language]'s deck (null: the world's), with the forms the phone has of the home deck's. */
@Composable
internal fun rememberKnownWords(vm: AppViewModel, language: String?): KnownWords? {
    val lang = language ?: VisitWorld.language
    val pool = vm.content.deck(lang)?.pool
    val cache = if (lang == null) vm.forms.cache else emptyMap()
    return remember(pool, cache) { pool?.let { p -> KnownWords.ofDeck(p) { l -> cache[WordFormsWire.key(l)]?.entries } } }
}

/** What can be looked up in [ex] (the [index]th of the run), before its answer and after ([answered]). */
@Composable
internal fun rememberLook(vm: AppViewModel, ex: Exercise?, index: Int, answered: Boolean, known: KnownWords?, language: String?): ExerciseLook? {
    val pair = L10n.pair
    val reads = remember(known, pair) { ExerciseTaps.reader(pair.target, pair.base, known, vm.speaker::isSlovene) }
    return remember(ex, index, answered, reads) {
        ex ?: return@remember null
        val due = !vm.dialogPrefs.exerciseWordHintSeen && ExerciseTaps(ex, false, reads).any
        ExerciseLook(
            ExerciseTaps(ex, answered, reads), known, tip = due, prefs = vm.dialogPrefs,
            filled = saidOf(ex, vm.speaker::isSlovene)?.takeIf { !ExerciseTaps.GAP.containsMatchIn(it) },
        ) { word, line, example ->
            vm.dialogPrefs.sawExerciseWordHint()
            // while it's open, a word the learner has shows no table of its forms (one may be the answer)
            vm.words.open(WordQuery(word.text, line, null, Words.SCENE, example = example, forms = answered, language = language))
        }
    }
}

/**
 * [text] of the exercise on screen with [words] of it to look up (ExerciseLook's taps), those new to the learner dotted;
 * [hold]: by a long press (an answer: a tap chooses it; a long press on one of its words that can't be looked up does
 * nothing); [line]: the line a word's card shows ([TextParts.lineOf] when null). A debug build logs where its words are
 * (tag ExWords, [tag]: what the text is; "!" a word that can't be looked up, "*" one new to the learner), for QA.
 */
@Composable
internal fun ExText(
    text: AnnotatedString,
    words: List<WordToken>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    hold: Boolean = false,
    line: ((WordToken) -> String)? = null,
    tag: String = "text",
) {
    val look = LocalLook.current
    if (look == null) {
        Text(text, modifier, style = style, color = color, fontWeight = fontWeight, fontStyle = fontStyle)
        return
    }
    val unknown = remember(text, words, look.known) { look.known?.unknown(text.text, words).orEmpty() }
    var laid by remember { mutableStateOf<TextLayoutResult?>(null) }
    var at by remember { mutableStateOf<Offset?>(null) }
    val logs = BuildConfig.DEBUG
    WordText(
        text,
        // an answer that can't be looked up yet still takes the long press: nothing opens, nothing is chosen
        onWord = if (words.isEmpty() && !hold) null else ({ t -> look.look(t, line?.invoke(t) ?: TextParts.lineOf(text.text, t)) }),
        modifier = if (logs) modifier.onGloballyPositioned { at = it.positionInWindow() } else modifier,
        style = style,
        color = color,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        longPress = hold,
        words = words,
        unknown = unknown,
        onLayout = if (logs) ({ laid = it }) else null,
    )
    if (logs) LaunchedEffect(laid, at, words, hold) {
        val l = laid ?: return@LaunchedEffect
        val o = at ?: return@LaunchedEffect
        val how = if (words.isEmpty()) "none" else if (hold) "hold" else "tap"
        // every word, those that can't be looked up marked "!", those new to the learner "*"
        val where = si.lanisce.lani.ui.words.WordTokens.of(text.text).joinToString(" ") { t ->
            val box = l.getBoundingBox(t.start).let { b -> b.copy(right = l.getBoundingBox(t.end - 1).right) }
            "${t.text}@${(o.x + box.center.x).toInt()},${(o.y + box.center.y).toInt()}" + (if (t in words) "" else "!") + if (t in unknown) "*" else ""
        }
        Log.d("ExWords", "$tag $how «${text.text.replace('\n', ' ')}» $where")
    }
}

internal fun AnnotatedString.emphasized(): List<IntRange> =
    spanStyles.filter { it.item.fontWeight == FontWeight.Bold || it.item.fontStyle == FontStyle.Italic }.map { it.start until it.end }

/**
 * The first time an exercise has words to look up: "👆 Tapni besedo … · Tap a word …" under its prompt, until closed, a
 * word looked up, or the next exercise.
 */
@Composable
internal fun WordTip(look: ExerciseLook) {
    val mine = remember { look.claimTip() }
    if (!mine || !look.tipShows) return
    DisposableEffect(Unit) { onDispose(look::tipSeen) }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    ) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "👆 ${bi("words.tapInExercise")}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = look::tipSeen) { EmojiLabel("✕", Labels.CLOSE) }
        }
    }
}
