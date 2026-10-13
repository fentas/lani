package si.lanisce.lani.ui.school

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.Portraits
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.StagePose
import java.time.LocalTime
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** The near hand on the screen (root coordinates, px) and the size of one of the sprite's pixels there. */
data class Hand(val at: Offset, val pixel: Float)

/**
 * The teacher on the school's stage: the portrait's sprite ([Portraits.painter], its bust as on the dialogs' stage),
 * breathing and blinking while [animate], in [pose], turned toward [aim] (a point on the screen, root px) with the near
 * arm reaching that way, the book back in hand without one; [walkIn]: she walks in from the side into her place first
 * (about a second). [onHand]: where her hand is on the screen, for the pointer drawn from it ([PointerStick]).
 */
@Composable
fun TeacherStage(
    art: String, seed: Int, px: Int, pose: Pose, aim: Offset?, walkIn: Boolean, animate: Boolean, modifier: Modifier = Modifier,
    onHand: (Hand?) -> Unit = {},
) {
    val canvas = remember(px) { PixelCanvas(px, px) }
    val bitmap = remember(px) { Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val clock = remember { mutableLongStateOf(0L) }
    if (animate) LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        var last = -FRAME_NANOS
        while (true) withFrameNanos { now -> if (now - start - last >= FRAME_NANOS) { last = now - start; clock.longValue = last } }
    }
    // the walk-in, once, as she first shows: from beyond the left edge into her place
    val walks = remember { walkIn && animate }
    val walk = remember { Animatable(if (walks) 0f else 1f) }
    LaunchedEffect(Unit) { if (walks) walk.animateTo(1f, tween(WALK_MS, easing = LinearEasing)) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    // the hand last told (not state: it's kept as she's drawn)
    val told = remember { arrayOfNulls<Hand>(1) }
    Canvas(modifier.onGloballyPositioned { origin = it.positionInRoot() }) {
        val t = clock.longValue / 1e9
        val now = LocalTime.now()
        val scale = max(1, min(size.width.toInt(), size.height.toInt()) / px)
        val w = px * scale
        val ox = ((size.width - w) / 2).toInt(); val oy = ((size.height - w) / 2).toInt()
        val p = walk.value
        val stage = StagePose(
            aim = aim?.let { a -> (a.x - origin.x - ox) / scale to (a.y - origin.y - oy) / scale }?.takeIf { p >= 1f },
            shift = ((p - 1f) * px).toInt(),
            walking = p < 1f,
        )
        val hand = Portraits.painter.renderStage(canvas, art, if (p < 1f) Pose.IDLE else pose, t, now.hour + now.minute / 60f, seed, stage)
        bitmap.setPixels(canvas.pixels, 0, px, 0, 0, px, px)
        drawImage(image, srcOffset = IntOffset.Zero, srcSize = IntSize(px, px), dstOffset = IntOffset(ox, oy), dstSize = IntSize(w, w), filterQuality = FilterQuality.None)
        // where the hand is: told when it moved (the pointer is drawn from it)
        val at = hand?.takeIf { stage.aim != null }?.let { Hand(Offset(origin.x + ox + it.x * scale, origin.y + oy + it.y * scale), scale.toFloat()) }
        val before = told[0]
        if ((at == null) != (before == null) || (at != null && before != null && hypot(at.at.x - before.at.x, at.at.y - before.at.y) > 0.5f)) {
            told[0] = at
            onHand(at)
        }
    }
}

/**
 * The kazalka, the teacher's wooden pointer: from her hand ([hand], root px) to [tip] (root px), as thick as one of her
 * sprite's pixels, its tip chalk-white; [alpha] fades it in and out. Drawn over the stage and the content, in [origin]'s
 * coordinates (the overlay's place on the screen).
 */
@Composable
fun PointerStick(hand: Hand?, tip: Offset?, visible: Boolean, origin: Offset, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(if (visible && hand != null && tip != null) 1f else 0f, tween(FADE_MS), label = "pointer")
    // where it last was, so it fades out there (not state: kept as it's drawn)
    val kept = remember { arrayOfNulls<Any>(2) }
    if (tip != null) kept[0] = tip
    if (hand != null) kept[1] = hand
    val h = hand ?: kept[1] as Hand?
    val end = tip ?: kept[0] as Offset?
    if (alpha <= 0.01f || h == null || end == null) return
    Canvas(modifier) {
        val a = h.at - origin
        val b = end - origin
        val len = hypot(b.x - a.x, b.y - a.y)
        if (len < h.pixel * 2) return@Canvas
        // it stops just short of what it points at
        val ux = (b.x - a.x) / len; val uy = (b.y - a.y) / len
        val stop = Offset(b.x - ux * h.pixel * 0.6f, b.y - uy * h.pixel * 0.6f)
        val chalk = Offset(stop.x - ux * h.pixel * 2.5f, stop.y - uy * h.pixel * 2.5f)
        // the shaft over the content is see-through (what it crosses stays readable), its end solid
        val solid = Offset(stop.x - ux * min(len * 0.3f, h.pixel * 8f), stop.y - uy * min(len * 0.3f, h.pixel * 8f))
        val wide = h.pixel
        fun stick(p0: Offset, p1: Offset, a0: Float) {
            drawLine(OUTLINE.copy(alpha = a0), p0, p1, strokeWidth = wide + 2f * density, cap = StrokeCap.Butt)
            drawLine(WOOD.copy(alpha = a0), p0, p1, strokeWidth = wide, cap = StrokeCap.Butt)
            drawLine(WOOD_LIGHT.copy(alpha = a0), Offset(p0.x - uy * wide * 0.25f, p0.y + ux * wide * 0.25f), Offset(p1.x - uy * wide * 0.25f, p1.y + ux * wide * 0.25f), strokeWidth = wide * 0.3f, cap = StrokeCap.Butt)
        }
        stick(a, solid, alpha * SHAFT)
        stick(solid, chalk, alpha)
        drawLine(OUTLINE.copy(alpha = alpha), chalk, stop, strokeWidth = wide + 2f * density, cap = StrokeCap.Square)
        drawLine(CHALK.copy(alpha = alpha), chalk, stop, strokeWidth = wide, cap = StrokeCap.Square)
    }
}

/** Animates [move] from 0 to 1: the pointer's tip from its last target to the next, eased, a quarter of a second. */
suspend fun moveTip(move: Animatable<Float, *>) {
    move.snapTo(0f)
    move.animateTo(1f, tween(MOVE_MS, easing = FastOutSlowInEasing))
}

private const val FRAME_NANOS = 83_000_000L // ~12 fps, as the portraits
/** The walk-in's length: a reading waits for it ([WALK_MS] and a breath). */
const val WALK_MS = 1_000
private const val FADE_MS = 180

/** How much of the pointer's shaft shows over the content it crosses. */
private const val SHAFT = 0.5f
private const val MOVE_MS = 260

private val WOOD = Color(0xFF8A5A2E)
private val WOOD_LIGHT = Color(0xFFC0884E)
private val OUTLINE = Color(0xFF3A2414)
private val CHALK = Color(0xFFF7F3E8)

