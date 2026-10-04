package si.lanisce.lani.game.render

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.GameState
import java.time.LocalDateTime

internal const val FRAME_NANOS = 83_000_000L // ~12 fps
internal const val BUILD_SECONDS = 1.5
internal const val CELEBRATE_SECONDS = 2.6

/**
 * Animated isometric pixel-art view of the village (renderer contract; implementation by the renderer).
 *
 * @param compact small header version for the home screen: no tap targets, fewer effects
 * @param highlightPlot plot to outline, e.g. where a building is about to go
 * @param justBuilt building id that plays the construction animation
 * @param celebrate bumps to play a one-off celebration (fireworks/confetti in pixels), e.g. on a win
 */
@Composable
fun VillageScene(
    state: GameState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    highlightPlot: Int? = null,
    justBuilt: String? = null,
    celebrate: Int = 0,
    onBuildingTap: ((Building) -> Unit)? = null,
    onFireTap: (() -> Unit)? = null,
) {
    val renderer = remember { VillageRenderer() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val clock = remember { mutableLongStateOf(0L) }
    val anim = remember { AnimMarks(celebrateSeen = celebrate) }
    val onBuilding by rememberUpdatedState(onBuildingTap)
    val onFire by rememberUpdatedState(onFireTap)

    // Uniform whole-number upscale; more world (not a stretch) fills the view, and the canvas covers it (see SceneFit).
    val fit = SceneFit.of(size.width, size.height, compact)
    val scale = fit.scale
    val iw = fit.width
    val ih = fit.height
    val canvas = remember(iw, ih) { PixelCanvas(iw, ih) }
    val bitmap = remember(iw, ih) { Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val offX = fit.offX
    val offY = fit.offY

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        var last = -FRAME_NANOS
        while (true) {
            withFrameNanos { now ->
                val t = now - start
                if (t - last >= FRAME_NANOS) { last = t; clock.longValue = t }
            }
        }
    }

    val tappable = !compact && (onBuildingTap != null || onFireTap != null)
    val tapModifier = if (!tappable) Modifier else
        Modifier.pointerInput(scale, offX, offY) {
            detectTapGestures { p ->
                val x = ((p.x - offX) / scale).toInt(); val y = ((p.y - offY) / scale).toInt()
                when (val hit = renderer.hitTest(x, y)) {
                    is VillageHit.OnBuilding -> onBuilding?.invoke(hit.building)
                    VillageHit.OnFire -> onFire?.invoke()
                    else -> Unit
                }
            }
        }

    Canvas(modifier.onSizeChanged { size = it }.then(tapModifier)) {
        if (size.width == 0 || size.height == 0) return@Canvas
        val t = clock.longValue / 1e9 // reading the clock here re-runs only the draw phase
        if (justBuilt != anim.builtId) { anim.builtId = justBuilt; anim.builtAt = t }
        if (celebrate != anim.celebrateSeen) { anim.celebrateSeen = celebrate; anim.celebrateAt = t }
        val now = LocalDateTime.now()
        val celebrateP = if (anim.celebrateAt < 0) -1f else ((t - anim.celebrateAt) / CELEBRATE_SECONDS).toFloat()
        val frame = Frame(
            time = t,
            hour = now.hour + now.minute / 60f + now.second / 3600f,
            month = now.monthValue,
            compact = compact,
            highlightPlot = highlightPlot,
            justBuilt = justBuilt,
            justBuiltProgress = if (justBuilt == null) 1f else ((t - anim.builtAt) / BUILD_SECONDS).toFloat().coerceIn(0f, 1f),
            celebrate = if (celebrateP > 1f) -1f else celebrateP,
            sea = si.lanisce.lani.game.culture.Cultures.current.world.backdrop?.sea == true,
            moon = Moon.now(),
            skyNow = si.lanisce.lani.game.sky.SkyCards.now(),
            wall = System.currentTimeMillis(),
        )
        renderer.render(canvas, state, frame)
        bitmap.setPixels(canvas.pixels, 0, iw, 0, 0, iw, ih)
        drawRect(Color(renderer.skyColor))
        drawImage(
            image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(iw, ih),
            dstOffset = IntOffset(offX, offY),
            dstSize = IntSize(iw * scale, ih * scale),
            filterQuality = FilterQuality.None,
        )
    }
}

/** Animation start marks, mutated from the draw phase (plain fields: they must not trigger recomposition). */
internal class AnimMarks(var celebrateSeen: Int) {
    var builtId: String? = null
    var builtAt = 0.0
    var celebrateAt = -1.0
}
