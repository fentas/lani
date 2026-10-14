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
import kotlin.math.max
import kotlin.math.min

/**
 * The teacher on the school's stage: the portrait's sprite ([Portraits.painter], its bust as on the dialogs' stage),
 * breathing and blinking while [animate], in [pose], turned toward [aim] (a point on the screen, root px: what she
 * explains) with the near arm held out that way, the book back in hand without one; [walkIn]: she walks in from the side
 * into her place first (about a second).
 */
@Composable
fun TeacherStage(
    art: String, seed: Int, px: Int, pose: Pose, aim: Offset?, walkIn: Boolean, animate: Boolean, modifier: Modifier = Modifier,
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
        Portraits.painter.renderStage(canvas, art, if (p < 1f) Pose.IDLE else pose, t, now.hour + now.minute / 60f, seed, stage)
        bitmap.setPixels(canvas.pixels, 0, px, 0, 0, px, px)
        drawImage(image, srcOffset = IntOffset.Zero, srcSize = IntSize(px, px), dstOffset = IntOffset(ox, oy), dstSize = IntSize(w, w), filterQuality = FilterQuality.None)
    }
}

/**
 * Her small pointing hand: a pixel hand of the stage's style (her sleeve's cuff), pointing left, its fingertip just inside
 * the right edge of what is read ([at], root px: that edge, at the height of what is read), the rest beyond it: never over
 * the text. [pixel] px a pixel of it; [visible] fades it in and out (where it last was), [origin] the overlay's place.
 */
@Composable
fun PointingHand(at: Offset?, visible: Boolean, origin: Offset, pixel: Float, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(if (visible && at != null) 1f else 0f, tween(FADE_MS), label = "hand")
    // where it last was, so it fades out there (not state: kept as it's drawn)
    val kept = remember { arrayOfNulls<Offset>(1) }
    if (at != null) kept[0] = at
    val p = at ?: kept[0]
    if (alpha <= 0.01f || p == null) return
    Canvas(modifier) {
        // the fingertip TIP pixels in from the edge, the finger level with what is read
        val x0 = p.x - origin.x - TIP * pixel
        val y0 = p.y - origin.y - (FINGER + 0.5f) * pixel
        HAND.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch ->
                val col = when (ch) {
                    'o' -> HAND_OUTLINE; 's' -> SKIN; 'd' -> SKIN_SHADE; 'c' -> CUFF; else -> null
                } ?: return@forEachIndexed
                drawRect(col.copy(alpha = alpha), Offset(x0 + c * pixel, y0 + r * pixel), androidx.compose.ui.geometry.Size(pixel + 0.5f, pixel + 0.5f))
            }
        }
    }
}

/** Animates [move] from 0 to 1: the hand's hop from where it pointed to where it points now. */
suspend fun hopHand(move: Animatable<Float, *>) {
    move.snapTo(0f)
    move.animateTo(1f, tween(HOP_MS, easing = FastOutSlowInEasing))
}

/** The hand, pointing left (o outline, s skin, d its shade, c the sleeve's cuff), [HAND_W] pixels wide. */
private val HAND = listOf(
    "....ooooo..",
    "ooooossssoc",
    "osssssssdoc",
    "ooooosssdoc",
    "....osssdoc",
    "....ossddoc",
    ".....oooo..",
)

/** How wide the hand is, in its pixels; its fingertip this many pixels in from the edge it points at. */
const val HAND_W = 11
private const val TIP = 2

/** The finger's middle row. */
private const val FINGER = 2

/** How high the hand hops to its next place, in its pixels. */
const val HOP_PIXELS = 4

private val HAND_OUTLINE = Color(0xFF3A2414)
private val SKIN = Color(0xFFF2C8A0)
private val SKIN_SHADE = Color(0xFFD9A57C)
private val CUFF = Color(0xFF7A4E9A)

private const val FRAME_NANOS = 83_000_000L // ~12 fps, as the portraits
/** The walk-in's length: a reading waits for it ([WALK_MS] and a breath). */
const val WALK_MS = 1_000
private const val FADE_MS = 180
private const val HOP_MS = 260

