package si.lanisce.lani.game.render

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.culture.VillageCulture
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

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
    var size by remember { mutableStateOf(IntSize.Zero) }
    // the picture's clock (ns), advanced at ~12 fps by the ticks below; not a state: the screen's draw shows the newest
    // picture and never reads it (docs/plans/05-built/changes-areas.md)
    val clock = remember { LongArray(1) }
    val anim = remember { AnimMarks(celebrateSeen = celebrate) }
    val onBuilding by rememberUpdatedState(onBuildingTap)
    val onFire by rememberUpdatedState(onFireTap)

    // Uniform whole-number upscale; more world (not a stretch) fills the view, and the canvas covers it (see SceneFit).
    val fit = SceneFit.of(size.width, size.height, compact)
    val scale = fit.scale
    val iw = fit.width
    val ih = fit.height
    // rendered on a thread of its own (as the town's, see TownPictures), and kept for when Home comes back (Retained): the
    // screen's frames only draw the newest picture, the last one at once when Home is composed again
    // (none before the view has its size: a canvas of no view's size would be kept for nothing)
    val context = androidx.compose.ui.platform.LocalContext.current
    val sized = size.width > 0 && size.height > 0
    val pics = remember(iw, ih, sized) { if (sized) { Retained.attach(context); Retained.strip(iw, ih) } else null }
    DisposableEffect(pics) {
        pics?.let { Retained.use(it, true) }
        onDispose { pics?.let { Retained.use(it, false) } }
    }
    // nothing kept (the app's first Home): the sky until the first picture is there, then it fades in
    val fade = remember(pics) { Animatable(if (pics?.picture == null) 0f else 1f) }
    LaunchedEffect(pics) {
        if (pics != null && fade.value < 1f) {
            snapshotFlow { pics.version }.first { pics.picture != null }
            fade.animateTo(1f, tween(FADE_MS.toInt()))
        }
    }
    val offX = fit.offX
    val offY = fit.offY

    // the system's animations off: no flash of lightning over the village
    val calm = si.lanisce.lani.ui.stage.rememberReducedMotion()
    // what the next picture is of, read when it is asked for
    val wanted by rememberUpdatedState(Wanted(state, highlightPlot, justBuilt, celebrate, calm))
    // the picture's clock, at ~12 fps: each tick asks the render thread for that moment's picture, and the screen draws a
    // picture once it is there (one draw a picture, before: one for the tick and one for its picture); between the ticks
    // the app's main thread sleeps (before: every frame). Stopped while the screen is covered (Home under another:
    // ui/Covered.kt), and on from where it stopped when it shows again (the construction's and the celebration's marks go
    // by it)
    val covered = si.lanisce.lani.ui.LocalCovered.current
    // a celebration while covered (a win in the village) played where it happened: not again when Home shows (as a Home
    // composed anew didn't)
    if (covered) anim.celebrateSeen = celebrate
    LaunchedEffect(covered, pics) {
        if (covered || pics == null) return@LaunchedEffect
        val start = withFrameNanos { it } - clock[0]
        var last = clock[0] - FRAME_NANOS
        while (true) {
            val t = withFrameNanos { it } - start
            if (t - last >= FRAME_NANOS) {
                last = t
                clock[0] = t
                askPicture(pics, anim, clock[0], wanted, compact)
            } else delay(((FRAME_NANOS - (t - last)) / 1_000_000L).coerceAtLeast(1L))
        }
    }
    // a new state of the village (a building, a move) asked for at once, not at the next tick
    LaunchedEffect(pics, state) {
        if (!covered && pics != null) askPicture(pics, anim, clock[0], wanted, compact)
    }

    val tappable = !compact && (onBuildingTap != null || onFireTap != null)
    val tapModifier = if (!tappable) Modifier else
        Modifier.pointerInput(scale, offX, offY, pics) {
            detectTapGestures { p ->
                val x = ((p.x - offX) / scale).toInt(); val y = ((p.y - offY) / scale).toInt()
                when (val hit = pics?.renderer?.hitTest(x, y)) {
                    is VillageHit.OnBuilding -> onBuilding?.invoke(hit.building)
                    VillageHit.OnFire -> onFire?.invoke()
                    else -> Unit
                }
            }
        }

    // in a layer of its own: a new picture draws it again, nothing else of the screen around it
    Canvas(modifier.onSizeChanged { size = it }.graphicsLayer().then(tapModifier)) {
        if (pics == null) return@Canvas
        pics.version // a picture the render thread hands over: drawn
        val p = pics.picture
        pics.drew(p)
        // before the first picture the hour's sky (the renderer's is the last picture's)
        drawRect(Color(if (p == null) LocalDateTime.now().let { Env(it.hour + it.minute / 60f, it.monthValue, false).skyTop } else pics.renderer.skyColor))
        if (p != null) drawImage(
            p.image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(p.w, p.h),
            dstOffset = IntOffset(offX, offY),
            dstSize = IntSize(p.w * scale, p.h * scale),
            alpha = fade.value,
            filterQuality = FilterQuality.None,
        )
    }
}

/** What the header village's next picture is of ([VillageScene]'s inputs, read when a picture is asked for). */
private class Wanted(val state: GameState, val highlightPlot: Int?, val justBuilt: String?, val celebrate: Int, val calm: Boolean)

/** The picture of [w] at [clock] (ns) asked of [pics]' render thread, the construction's and the celebration's marks by it. */
private fun askPicture(pics: StripPictures, anim: AnimMarks, clock: Long, w: Wanted, compact: Boolean) {
    val t = clock / 1e9
    if (w.justBuilt != anim.builtId) { anim.builtId = w.justBuilt; anim.builtAt = t }
    if (w.celebrate != anim.celebrateSeen) { anim.celebrateSeen = w.celebrate; anim.celebrateAt = t }
    val now = LocalDateTime.now()
    val celebrateP = if (anim.celebrateAt < 0) -1f else ((t - anim.celebrateAt) / CELEBRATE_SECONDS).toFloat()
    val frame = Frame(
        time = t,
        hour = now.hour + now.minute / 60f + now.second / 3600f,
        month = now.monthValue,
        compact = compact,
        highlightPlot = w.highlightPlot,
        justBuilt = w.justBuilt,
        justBuiltProgress = if (w.justBuilt == null) 1f else ((t - anim.builtAt) / BUILD_SECONDS).toFloat().coerceIn(0f, 1f),
        celebrate = if (celebrateP > 1f) -1f else celebrateP,
        // the village on screen in its culture, as its scenes have it (VillageCulture: one source for both)
        sea = VillageCulture.sea(VillageCulture.shown),
        culture = VillageCulture.shown.id,
        moon = Moon.now(),
        skyNow = si.lanisce.lani.game.sky.SkyCards.now(),
        wall = System.currentTimeMillis(),
        calm = w.calm,
    )
    pics.ask(clock, w.state, frame)
}

/** Animation start marks, set as each picture is asked for (plain fields: they must not trigger recomposition). */
internal class AnimMarks(var celebrateSeen: Int) {
    var builtId: String? = null
    var builtAt = 0.0
    var celebrateAt = -1.0
}
