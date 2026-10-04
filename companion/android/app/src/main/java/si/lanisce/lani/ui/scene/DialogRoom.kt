package si.lanisce.lani.ui.scene

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * Room for a dialog's turn while the keyboard is up (a typed turn, companion/SCENES.md "Adaptive turns"). The app draws
 * edge to edge, so the window doesn't shrink for the keyboard: [DialogPanel] keeps above it itself (imePadding), and what
 * is drawn over the panel gives way. A scene's picture (the scene, a visit's place) lets the panel rise to just under the
 * status bar, its top bar out of the way ([panelTop]); a sheet's portraits (a keeper's talk, an arrival) fold away
 * ([DialogStage]). Once the keyboard is down again, the picture comes back. Within the panel the turn takes the room it
 * needs and the lines keep what's left, at least the last line said ([turnMax]); where even that is too little (a small
 * screen, large fonts), the turn scrolls, kept down at the field, the letter chips and "Preveri · Check"
 * ([KeepAtEnd]). Pure but for the composables.
 */
object DialogRoom {
    /** What shows of the picture under the status bar while the keyboard is up: a sliver over the panel's rounded top. */
    const val STRIP_DP = 8

    /** What the lines keep at least while the turn fits beside them: the last line said, its translation under it. */
    const val LINES_MIN_DP = 72

    /**
     * What the turn keeps at least, lines or not: a typed turn's end, the sentence with the gap, the field, the letter chips
     * and "Check" (about 242 dp at the normal font size; the turn scrolled down to it).
     */
    const val TURN_MIN_DP = 248

    /** How long the picture takes to give way, or to come back. */
    const val MOVE_MS = 220

    /**
     * Where a dialog panel starts on a screen with a picture [picture] high over it (px from the top): at the picture's foot,
     * or while the keyboard is up ([keyboard]) just under the status bar ([statusBar]) and a [strip] of the picture.
     */
    fun panelTop(picture: Int, statusBar: Int, strip: Int, keyboard: Boolean): Int =
        if (keyboard) min(picture, statusBar + strip) else picture

    /**
     * The most of the panel's [body] (below its header: the lines over the turn) the turn may take: all but [linesMin] for
     * the lines, never less than [turnMin] (or the whole body, when that's smaller still). What the turn doesn't need goes
     * to the lines; past it, the turn scrolls.
     */
    fun turnMax(body: Int, linesMin: Int, turnMin: Int): Int = max(body - linesMin, min(turnMin, body)).coerceAtLeast(0)

    /** The lines' height under a turn [turn] high in a [body]: the rest. */
    fun linesHeight(body: Int, turn: Int): Int = (body - turn).coerceAtLeast(0)
}

/** Whether the soft keyboard is up now. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun keyboardUp(): Boolean = WindowInsets.isImeVisible

/**
 * Where a dialog's panel starts under a scene's picture [picture] high ([DialogRoom.panelTop]): while [lifted] (a dialog
 * open with the keyboard up), just under the status bar, the picture out of the way; it rises and comes back down in
 * [DialogRoom.MOVE_MS].
 */
@Composable
fun rememberPanelTop(picture: Dp, lifted: Boolean): Dp {
    val density = LocalDensity.current
    val target = with(density) {
        DialogRoom.panelTop(picture.roundToPx(), WindowInsets.statusBars.getTop(density), DialogRoom.STRIP_DP.dp.roundToPx(), lifted).toDp()
    }
    val top by animateDpAsState(target, tween(DialogRoom.MOVE_MS), label = "panelTop")
    return top
}

/**
 * The portraits over a dialog in a sheet (a keeper's talk, an arrival): folded away while the keyboard is up, so the
 * typed turn has the room; back once it's down.
 */
@Composable
fun DialogStage(content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = !keyboardUp(),
        enter = expandVertically(tween(DialogRoom.MOVE_MS)) + fadeIn(tween(DialogRoom.MOVE_MS)),
        exit = shrinkVertically(tween(DialogRoom.MOVE_MS)) + fadeOut(tween(DialogRoom.MOVE_MS)),
        label = "dialogStage",
    ) { content() }
}

/**
 * The panel's body (a bounded height: the panel's room under its header): the [lines] over the [turn], the turn as high
 * as it needs up to [DialogRoom.turnMax], the lines the rest. The turn scrolls past that (it is measured with at most it).
 */
@Composable
internal fun LinesOverTurn(lines: @Composable () -> Unit, turn: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Layout(contents = listOf(lines, turn), modifier = modifier) { (l, t), c ->
        val body = if (c.hasBoundedHeight) c.maxHeight else DialogRoom.TURN_MIN_DP.dp.roundToPx() * 2
        val width = if (c.hasBoundedWidth) c.maxWidth else c.minWidth
        val most = DialogRoom.turnMax(body, DialogRoom.LINES_MIN_DP.dp.roundToPx(), DialogRoom.TURN_MIN_DP.dp.roundToPx())
        val tp = t.map { it.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = most)) }
        val lh = DialogRoom.linesHeight(body, tp.maxOfOrNull { it.height } ?: 0)
        val lp = l.map { it.measure(Constraints.fixed(width, lh)) }
        layout(width, body) {
            lp.forEach { it.place(0, 0) }
            tp.forEach { it.place(0, lh) }
        }
    }
}

/**
 * While [focused] (a typed turn's field), the turn's [scroll] keeps its end in view each time its view shrinks (the
 * keyboard sliding up, the picture giving way): the field, the letter chips and "Check", which end the turn. A view
 * growing (the hint opened) leaves it where the learner has it.
 */
@Composable
internal fun KeepAtEnd(scroll: ScrollState, focused: Boolean) {
    LaunchedEffect(scroll, focused) {
        if (!focused) return@LaunchedEffect
        var was = Int.MAX_VALUE
        snapshotFlow { scroll.viewportSize }.collect { now ->
            if (now < was) scroll.scrollTo(scroll.maxValue)
            was = now
        }
    }
}
