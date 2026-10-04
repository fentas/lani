package si.lanisce.lani.game.render

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.villagers.Villager
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** A detail switch fades the new picture in over the old one this long (ms). */
internal const val FADE_MS = 220.0

/**
 * The town's clock: [tick] advances at ~12 fps (the renderer's frame rate) and drives every re-render; [frame]
 * advances every screen frame, but only while something asks for it ([frames]: a fade), so a resting village
 * costs nothing. Both are state: what reads them in a draw or layout phase re-runs when they change.
 */
class TownClock {
    var tick by mutableLongStateOf(0L)
        private set
    var frame by mutableLongStateOf(0L)
        private set
    /** Wanted by a fade; set from the draw phase, read by the clock. */
    @Volatile var frames = false

    /**
     * When the clock's second 0 was, in System.nanoTime (a frame's time is on that clock), or -1 before its first frame:
     * the background sounds time what the picture shows from it (the thunder after a flash, a bell's strikes).
     */
    @Volatile var origin = -1L
        private set

    suspend fun run() {
        val start = withFrameNanos { it }
        origin = start
        var last = -FRAME_NANOS
        while (true) {
            withFrameNanos { now ->
                val t = now - start
                if (frames) frame = t
                if (t - last >= FRAME_NANOS) { last = t; tick = t }
            }
        }
    }
}

@Composable
fun rememberTownClock(): TownClock {
    val clock = remember { TownClock() }
    LaunchedEffect(clock) { clock.run() }
    return clock
}

/**
 * The full-screen village: the nominal canvas of [fit] rendered at ~12 fps and drawn through [camera] with
 * nearest-neighbour scaling (pixels stay crisp at any zoom), plus, at a closer look ([detail] 2 or 3), a second
 * canvas of the same size showing just what the camera sees at k × k pixels per nominal pixel (see [Lens]): the
 * geometry scales, the detail passes fill in, villagers get faces. The nominal picture stays underneath, so a
 * pan between frames never shows a hole; a detail switch crossfades over the picture that showed before.
 *
 * Moving the camera just redraws the bitmaps, so pinching stays smooth. The caller owns [renderer] to hit-test
 * taps (in the last frame's pixels, through its [VillageRenderer.lens]) and to anchor bubbles on the people.
 */
@Composable
fun TownScene(
    state: GameState,
    fit: SceneFit,
    camera: () -> Camera,
    renderer: VillageRenderer,
    modifier: Modifier = Modifier,
    highlightPlot: Int? = null,
    justBuilt: String? = null,
    celebrate: Int = 0,
    visitors: List<TownPlace> = emptyList(),
    detail: Int = 1,
    people: List<Villager> = emptyList(),
    clock: TownClock = rememberTownClock(),
    standing: Map<String, TownPlace> = emptyMap(),
    skyMark: String? = null,
    plots: PlotMarks? = null,
    day: si.lanisce.lani.game.villagers.Routine.Day = si.lanisce.lani.game.villagers.Routine.Day(),
) {
    val anim = remember { AnimMarks(celebrateSeen = celebrate) }
    val iw = fit.width; val ih = fit.height
    val base = remember(iw, ih) { Layer(iw, ih) }
    val fine = remember(iw, ih) { Layer(iw, ih) }
    val prev = remember(iw, ih) { Layer(iw, ih) }
    val drawnBase = remember(iw, ih) { Drawn() }
    val drawnFine = remember(iw, ih) { Drawn() }
    val switch = remember(iw, ih) { Switch() }

    Canvas(modifier) {
        val tick = clock.tick // reading the clock here re-runs only the draw phase
        val t = tick / 1e9
        val cam = camera()
        val k = detail.coerceIn(1, Lens.MAX_DETAIL)
        // a detail switch: keep what shows now, to fade the new picture in over it once it's rendered
        if (k != switch.wantK) {
            if (switch.shownK > 0) prev.copyFrom(if (switch.shownK > 1) fine else base)
            switch.wantK = k
        }
        val now = LocalDateTime.now()
        val today = LocalDate.now().toString()
        if (justBuilt != anim.builtId) { anim.builtId = justBuilt; anim.builtAt = t }
        if (celebrate != anim.celebrateSeen) { anim.celebrateSeen = celebrate; anim.celebrateAt = t }
        val celebrateP = if (anim.celebrateAt < 0) -1f else ((t - anim.celebrateAt) / CELEBRATE_SECONDS).toFloat()
        val frame = Frame(
            time = t,
            hour = now.hour + now.minute / 60f + now.second / 3600f,
            month = now.monthValue,
            highlightPlot = highlightPlot,
            justBuilt = justBuilt,
            justBuiltProgress = if (justBuilt == null) 1f else ((t - anim.builtAt) / BUILD_SECONDS).toFloat().coerceIn(0f, 1f),
            celebrate = if (celebrateP > 1f) -1f else celebrateP,
            visitors = visitors,
            people = people,
            standing = standing,
            today = today,
            moon = Moon.now(),
            sea = si.lanisce.lani.game.culture.Cultures.current.world.backdrop?.sea == true,
            // the real sky over the village this minute, the shooting stars by the clock
            skyNow = si.lanisce.lani.game.sky.SkyCards.now(),
            wall = System.currentTimeMillis(),
            skyMark = skyMark,
            plots = plots,
            day = day,
        )
        // the nominal canvas: every tick while it is the picture, else only when its inputs change
        if (drawnBase.stale(if (k == 1) tick else 0L, state, highlightPlot, justBuilt, celebrate, visitors, people, today, standing, skyMark, plots, day)) {
            renderer.render(base.canvas, state, frame)
            base.lens = Lens(1, 0, 0, iw, ih)
            base.upload()
        }
        // the closer look: every tick, at the window the camera sees now
        if (k > 1 && drawnFine.stale(tick, k, state, highlightPlot, justBuilt, celebrate, visitors, people, today, standing, skyMark, plots, day)) {
            val lens = Lens.of(k, cam, size.width.roundToInt(), size.height.roundToInt(), iw, ih, iw, ih)
            renderer.render(fine.canvas, state, frame.copy(lens = lens))
            fine.lens = lens
            fine.upload()
        }
        // the new picture is there: it is what shows, fading in over the one before
        val ready = if (k > 1) fine.valid && fine.lens.k == k else base.valid
        if (ready && switch.shownK != k) {
            if (switch.shownK > 0 && prev.valid) { switch.fadeFrom = tick; clock.frames = true }
            switch.shownK = k
        }
        var alpha = 1f
        if (switch.fadeFrom >= 0) {
            alpha = ((clock.frame - switch.fadeFrom) / 1e6 / FADE_MS).toFloat().coerceIn(0f, 1f)
            if (alpha >= 1f) { switch.fadeFrom = -1L; clock.frames = false }
        }
        val fading = switch.fadeFrom >= 0 && prev.valid

        drawRect(Color(renderer.skyColor))
        drawLayer(base, cam)
        when {
            !ready -> if (prev.valid) drawLayer(prev, cam) // the picture before, until the new detail is rendered
            k > 1 && fading -> { drawLayer(prev, cam); drawLayer(fine, cam, alpha) }
            k > 1 -> drawLayer(fine, cam)
            fading -> drawLayer(prev, cam, 1f - alpha)
        }
    }
}

/** One rendered picture: its canvas, the bitmap it's uploaded to, and the lens it was rendered with. */
internal class Layer(val w: Int, val h: Int) {
    val canvas = PixelCanvas(w, h)
    val bitmap: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val image = bitmap.asImageBitmap()
    var lens = Lens(1, 0, 0, w, h)
    var valid = false

    fun upload() { bitmap.setPixels(canvas.pixels, 0, w, 0, 0, w, h); valid = true }

    /** Takes [o]'s picture (a layer of the same size). */
    fun copyFrom(o: Layer) {
        if (!o.valid || o.canvas.pixels.size != canvas.pixels.size) return
        System.arraycopy(o.canvas.pixels, 0, canvas.pixels, 0, canvas.pixels.size)
        lens = o.lens
        upload()
    }
}

/** Which detail level is wanted and which is on screen, and the fade from the picture before it. */
internal class Switch {
    var wantK = 0
    var shownK = 0
    var fadeFrom = -1L
}

/** Draws the part of [layer] the camera sees, through its lens: nominal point b shows at b × scale + t; canvas pixel i at ((i + x0) / k) × scale + t. */
internal fun DrawScope.drawLayer(layer: Layer, cam: Camera, alpha: Float = 1f) {
    if (!layer.valid || alpha <= 0f) return
    val lens = layer.lens
    val s = cam.scale / lens.k
    // canvas pixel i shows nominal (i + x0) / k, which the camera puts at ((i + x0) / k) × scale + t = i × s + x0 × s + t
    val tx = cam.tx + lens.x0 * s; val ty = cam.ty + lens.y0 * s
    // only the part of the canvas that shows, in whole canvas pixels
    val x0 = floor((0f - tx) / s).toInt().coerceIn(0, layer.w)
    val y0 = floor((0f - ty) / s).toInt().coerceIn(0, layer.h)
    val x1 = ceil((size.width - tx) / s).toInt().coerceIn(x0, layer.w)
    val y1 = ceil((size.height - ty) / s).toInt().coerceIn(y0, layer.h)
    if (x1 <= x0 || y1 <= y0) return
    val dx0 = (x0 * s + tx).roundToInt(); val dy0 = (y0 * s + ty).roundToInt()
    val dx1 = (x1 * s + tx).roundToInt(); val dy1 = (y1 * s + ty).roundToInt()
    drawImage(
        layer.image,
        srcOffset = IntOffset(x0, y0),
        srcSize = IntSize(x1 - x0, y1 - y0),
        dstOffset = IntOffset(dx0, dy0),
        dstSize = IntSize(dx1 - dx0, dy1 - dy0),
        alpha = alpha,
        filterQuality = FilterQuality.None,
    )
}

/** What the bitmap shows: re-render only for a new clock tick or new inputs (compared by identity, cheaply). */
private class Drawn {
    private var tick = -1L
    private var inputs: Array<Any?> = emptyArray()

    fun stale(tick: Long, vararg now: Any?): Boolean {
        val same = tick == this.tick && now.size == inputs.size && now.indices.all { now[it] === inputs[it] || now[it] == inputs[it] && now[it] !is GameState }
        if (same) return false
        this.tick = tick
        inputs = arrayOf(*now)
        return true
    }
}
