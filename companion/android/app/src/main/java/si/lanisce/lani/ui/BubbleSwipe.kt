package si.lanisce.lani.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import si.lanisce.lani.ui.stage.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * What a sideways swipe on a chat bubble does, as messengers do: to the right [reply] ("↩️"), to the left [bookmark]
 * ("🔖", or on a bookmarked message its bookmark taken away). Null: no swipe that way. A shortcut for the long press's
 * actions, which stay (and are TalkBack's).
 */
internal class BubbleSwipes(val reply: (() -> Unit)?, val bookmark: (() -> Unit)?)

/** The swipe's decisions, apart from the screen (ChatSwipeTest). Distances in px. */
internal object BubbleSwipe {
    enum class Way { REPLY, BOOKMARK }

    /** How far the finger goes sideways, past the touch slop, for the action. */
    const val THRESHOLD_DP = 64

    /** The bubble's resistance: half as far as the finger at [LIMIT_DP], never past [LIMIT_DP]. */
    const val LIMIT_DP = 160

    /** The steepest a drag may start and still be a swipe: tan 30°, its up and down at most 0.58 of its sideways. */
    const val SLOPE = 0.577f

    /**
     * A drag of [total] so far: true once it is clearly sideways (past the touch [slop], within 30° of the horizontal),
     * false once it went past the slop any other way (the list's scroll, the sheet's drag: theirs), null while it is too
     * short to tell.
     */
    fun decide(total: Offset, slop: Float): Boolean? {
        val x = abs(total.x)
        val y = abs(total.y)
        return when {
            x >= slop && y <= x * SLOPE -> true
            hypot(x, y) >= slop && y > x * SLOPE -> false
            else -> null
        }
    }

    /** [drag] kept to the ways there is an action for: to the right only with [reply], to the left only with [bookmark]. */
    fun allowed(drag: Float, reply: Boolean, bookmark: Boolean): Float = when {
        drag > 0f && !reply -> 0f
        drag < 0f && !bookmark -> 0f
        else -> drag
    }

    /** Where the bubble is with the finger [drag] sideways: with it at first, slower and slower, never past [limit]. */
    fun offset(drag: Float, limit: Float): Float = drag * limit / (abs(drag) + limit)

    /** What letting go after [drag] does: past [threshold] to the right a reply, to the left the bookmark; else nothing. */
    fun action(drag: Float, threshold: Float): Way? = when {
        drag >= threshold -> Way.REPLY
        drag <= -threshold -> Way.BOOKMARK
        else -> null
    }
}

/**
 * [content] (a bubble and its 🔖) in a row that a sideways [swipe] moves: it follows the finger with resistance, the
 * action's icon fades in on the side it uncovers and grows toward the threshold, a light tick there; let go past it, the
 * action runs and the row springs back (with the system's animations removed, it is back at once); before it, it only
 * springs back. A drag that starts up or down is the list's or the sheet's, a long press the menu's; a swipe that starts
 * on a link or a quote in the bubble is a swipe all the same, one on a table wider than the bubble scrolls the table.
 */
@Composable
internal fun SwipeRow(swipe: BubbleSwipes?, bookmarked: Boolean, content: @Composable RowScope.() -> Unit) {
    if (swipe == null) {
        Row(verticalAlignment = Alignment.Top, content = content)
        return
    }
    val current by rememberUpdatedState(swipe)
    val x = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val reduced = rememberReducedMotion()
    val atThreshold = with(LocalDensity.current) { BubbleSwipe.offset(BubbleSwipe.THRESHOLD_DP.dp.toPx(), BubbleSwipe.LIMIT_DP.dp.toPx()) }
    val side by remember { derivedStateOf { sign(x.value) } }
    val pop by animateFloatAsState(if (armed) 1.2f else 1f, if (reduced) snap() else spring(Spring.DampingRatioMediumBouncy), label = "pop")
    Box {
        if (side != 0f) {
            // behind the row, at the edge it uncovers: ↩️ on the left, 🔖 on the right
            val reply = side > 0f
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .align(if (reply) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = 8.dp)
                        .graphicsLayer {
                            val p = (abs(x.value) / atThreshold).coerceIn(0f, 1f)
                            alpha = p
                            val s = (0.5f + 0.5f * p) * pop
                            scaleX = s
                            scaleY = s
                        }
                        .clearAndSetSemantics { },
                ) {
                    Text(if (reply) "↩️" else "🔖", style = MaterialTheme.typography.titleMedium)
                    if (!reply && bookmarked) {
                        Text(
                            "✕", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                }
            }
        }
        Row(
            Modifier
                .offset { IntOffset(x.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    val threshold = BubbleSwipe.THRESHOLD_DP.dp.toPx()
                    val limit = BubbleSwipe.LIMIT_DP.dp.toPx()
                    val slop = viewConfiguration.touchSlop
                    coroutineScope {
                        awaitEachGesture {
                            // the bubble's long press took the down already: it still counts
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var total = Offset.Zero
                            var drag: Float? = null
                            while (drag == null) {
                                val event = awaitPointerEvent()
                                val c = event.changes.firstOrNull { it.id == down.id } ?: continue
                                // a tap; or a long press, a table's own scroll
                                if (!c.pressed || c.isConsumed) return@awaitEachGesture
                                total += c.positionChange()
                                when (BubbleSwipe.decide(total, slop)) {
                                    true -> {
                                        c.consume()
                                        drag = total.x - sign(total.x) * slop
                                    }
                                    false -> return@awaitEachGesture
                                    null -> {
                                        // the list or the sheet may take it meanwhile
                                        awaitPointerEvent(PointerEventPass.Final)
                                        if (c.isConsumed) return@awaitEachGesture
                                    }
                                }
                            }
                            val s = current
                            var d = BubbleSwipe.allowed(drag, s.reply != null, s.bookmark != null)
                            launch { x.snapTo(BubbleSwipe.offset(d, limit)) }
                            var released = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val c = event.changes.firstOrNull { it.id == down.id } ?: continue
                                if (!c.pressed) {
                                    // a cancel comes consumed (the window took the touch away): no action
                                    released = !c.isConsumed
                                    c.consume()
                                    break
                                }
                                d = BubbleSwipe.allowed(d + c.positionChange().x, s.reply != null, s.bookmark != null)
                                c.consume()
                                val past = BubbleSwipe.action(d, threshold) != null
                                if (past && !armed) haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                armed = past
                                launch { x.snapTo(BubbleSwipe.offset(d, limit)) }
                            }
                            armed = false
                            launch { if (reduced) x.snapTo(0f) else x.animateTo(0f, spring(0.75f, Spring.StiffnessMediumLow)) }
                            if (released) when (BubbleSwipe.action(d, threshold)) {
                                BubbleSwipe.Way.REPLY -> current.reply?.invoke()
                                BubbleSwipe.Way.BOOKMARK -> current.bookmark?.invoke()
                                null -> Unit
                            }
                        }
                    }
                },
            verticalAlignment = Alignment.Top,
            content = content,
        )
    }
}
