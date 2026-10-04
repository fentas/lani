package si.lanisce.lani.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.Portraits
import java.time.LocalTime
import kotlin.math.max
import kotlin.math.min

private const val FRAME_NANOS = 83_000_000L // ~12 fps

/**
 * A villager's pixel portrait ([Portraits.painter]): [art] sprite in [pose], [px]×[px] canvas pixels
 * scaled up by a whole number to fill the space, centred. Animates unless [animate] is false. [seed] varies the
 * village's own people (woman, man): pass the villager id's hash.
 */
@Composable
fun Portrait(art: String, pose: Pose = Pose.IDLE, modifier: Modifier = Modifier, px: Int = 64, animate: Boolean = true, seed: Int = 0) {
    val canvas = remember(px) { PixelCanvas(px, px) }
    val bitmap = remember(px) { Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val clock = remember { mutableLongStateOf(0L) }
    if (animate) LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        var last = -FRAME_NANOS
        while (true) withFrameNanos { now -> if (now - start - last >= FRAME_NANOS) { last = now - start; clock.longValue = last } }
    }
    Canvas(modifier) {
        val t = clock.longValue / 1e9
        val now = LocalTime.now()
        Portraits.painter.render(canvas, art, pose, t, now.hour + now.minute / 60f, seed)
        bitmap.setPixels(canvas.pixels, 0, px, 0, 0, px, px)
        val scale = max(1, min(size.width.toInt(), size.height.toInt()) / px)
        val w = px * scale
        drawImage(
            image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(px, px),
            dstOffset = IntOffset(((size.width - w) / 2).toInt(), ((size.height - w) / 2).toInt()),
            dstSize = IntSize(w, w),
            filterQuality = FilterQuality.None,
        )
    }
}
