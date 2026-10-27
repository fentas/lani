package si.lanisce.lani.game.render

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.villagers.Villager
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
        var prev = start
        var t = 0L
        while (true) {
            withFrameNanos { now ->
                t += if (rate == 1f) now - prev else ((now - prev) * rate).toLong()
                prev = now
                if (frames) frame = t
                if (t - last >= FRAME_NANOS) { last = t; tick = t }
            }
        }
    }

    companion object {
        /**
         * How fast the towns' and scenes' time runs against the wall's: 1, or less while a debug build's QA asks for it
         * (app/QaHooks "clock:<f>"), so a slow emulator renders every frame of a recording that is sped up again later.
         */
        @Volatile var rate = 1f
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
 * nearest-neighbour scaling (pixels stay crisp at any zoom), plus, at a closer look ([detail] 2 or 3), a window of the
 * picture at k × k pixels per nominal pixel covering what the camera sees (see [Lens], [TownFrames]): the geometry
 * scales, the detail passes fill in, villagers get faces. The nominal picture stays underneath, so a pan between frames
 * never shows a hole; a detail switch crossfades over the picture that showed before.
 *
 * The pictures are rendered on a thread of their own ([TownPictures]): the screen's frames only draw the newest ones
 * through the camera, so pinching and panning stay smooth however long a picture takes, and the closer look's ground is
 * kept in cells of its picture ([GroundCache]), so a pan draws only the ground newly in view. The caller owns
 * [renderer] to hit-test taps (in the last frame's pixels, through its [VillageRenderer.lens]) and to anchor bubbles on
 * the people. [at]: the time to show instead of the clock's (QA's "hour:<h>"), or null. [growth] and [festive]: an
 * age-up's moment and a celebration's decorations ([si.lanisce.lani.game.AgeUps]), read each frame, or null.
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
    at: () -> LocalDateTime? = { null },
    growth: () -> si.lanisce.lani.game.Growth? = { null },
    festive: () -> si.lanisce.lani.game.Festive? = { null },
) {
    val anim = remember { AnimMarks(celebrateSeen = celebrate) }
    val iw = fit.width; val ih = fit.height
    val pics = remember(iw, ih, renderer) { TownPictures(iw, ih, renderer) }
    DisposableEffect(pics) { onDispose { pics.close() } }
    val inputs = remember(iw, ih) { Inputs() }
    val prev = remember(iw, ih) { Held() }
    val switch = remember(iw, ih) { Switch() }

    // the system's animations off: no flash of lightning over the village
    val calm = si.lanisce.lani.ui.stage.rememberReducedMotion()
    Canvas(modifier) {
        val tick = clock.tick // reading the clock here re-runs only the draw phase
        pics.version // and so does a picture the render thread hands over
        val t = tick / 1e9
        val cam = camera()
        val k = detail.coerceIn(1, Lens.MAX_DETAIL)
        // a detail switch: keep what shows now, to fade the new picture in over it once it's rendered
        if (k != switch.wantK) {
            if (switch.shownK > 0) prev.take(if (switch.shownK > 1) pics.fine else pics.base)
            switch.wantK = k
        }
        // the clock's time, or the time QA asks the map to be seen at (its "hour:<h>", app/QaHooks), without the real sky
        val forced = at()
        val now = forced ?: LocalDateTime.now()
        val today = now.toLocalDate().toString()
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
            skyNow = if (forced != null) null else si.lanisce.lani.game.sky.SkyCards.now(),
            wall = System.currentTimeMillis(),
            skyMark = skyMark,
            plots = plots,
            day = day,
            // the charcoal pile as its cycle has it today (QA's day, when it asks for one)
            kopa = si.lanisce.lani.game.scene.SpotCycles.look(state, si.lanisce.lani.game.scene.TownSpots.KOPA, now.toLocalDate()),
            // an age-up's moment (the village growing, its decorations going up), a celebration's decorations on the map
            growth = growth(),
            festive = festive(),
            calm = calm,
        )
        // what the pictures show besides the clock's time: both rendered anew when it changes
        val changed = inputs.stale(state, highlightPlot, justBuilt, celebrate, visitors, people, today, standing, skyMark, plots, day, frame.kopa, frame.growth, frame.festive)
        pics.ask(tick, k, cam, size.width.roundToInt(), size.height.roundToInt(), state, frame, changed)

        val base = pics.base
        val latest = pics.fine
        // what the screen draws now (and its sync uploads): the render thread draws into neither
        pics.drew(base, latest)
        val fine = latest?.takeIf { k > 1 && it.lens.k == k }
        // the new picture is there: it is what shows, fading in over the one before
        val ready = if (k > 1) fine != null else base != null
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
        base?.let { drawPicture(it.image, it.lens, it.ox, it.oy, it.w, it.h, cam) }
        when {
            !ready -> if (prev.valid) prev.draw(this, cam) // the picture before, until the new detail is rendered
            fine != null && fading -> { prev.draw(this, cam); drawPicture(fine.image, fine.lens, fine.ox, fine.oy, fine.w, fine.h, cam, alpha) }
            fine != null -> drawPicture(fine.image, fine.lens, fine.ox, fine.oy, fine.w, fine.h, cam)
            fading -> prev.draw(this, cam, 1f - alpha)
        }
    }
}

/**
 * The picture shown before a detail switch, to fade from: a copy of it (the render thread reuses its bitmaps), with
 * where it lay in its lens's picture.
 */
internal class Held {
    private var bitmap: Bitmap? = null
    private var image: ImageBitmap? = null
    private var lens = Lens()
    private var ox = 0
    private var oy = 0
    private var w = 0
    private var h = 0
    var valid = false
        private set

    /** Keeps a copy of [p] (none: the one before stays). */
    fun take(p: Picture?) {
        if (p == null) return
        val b = bitmap?.takeIf { it.width >= p.w && it.height >= p.h }
            ?: Bitmap.createBitmap(maxOf(p.w, bitmap?.width ?: 0), maxOf(p.h, bitmap?.height ?: 0), Bitmap.Config.ARGB_8888).also { bitmap = it; image = it.asImageBitmap() }
        val r = android.graphics.Rect(0, 0, p.w, p.h)
        android.graphics.Canvas(b).drawBitmap(p.bitmap, r, r, COPY)
        lens = p.lens; ox = p.ox; oy = p.oy; w = p.w; h = p.h
        valid = true
    }

    fun draw(scope: DrawScope, cam: Camera, alpha: Float = 1f) {
        val img = image ?: return
        if (valid) scope.drawPicture(img, lens, ox, oy, w, h, cam, alpha)
    }

    private companion object {
        /** A copy, pixel for pixel. */
        val COPY = android.graphics.Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC) }
    }
}

/** What the town's pictures show besides the clock: rendered anew only for a change (compared by identity, cheaply). */
private class Inputs {
    private var inputs: Array<Any?> = emptyArray()

    fun stale(vararg now: Any?): Boolean {
        val same = now.size == inputs.size && now.indices.all { now[it] === inputs[it] || now[it] == inputs[it] && now[it] !is GameState }
        if (same) return false
        inputs = arrayOf(*now)
        return true
    }
}

/** Which detail level is wanted and which is on screen, and the fade from the picture before it. */
internal class Switch {
    var wantK = 0
    var shownK = 0
    var fadeFrom = -1L
}

/**
 * Draws the part of a picture the camera sees: [image]'s [w] × [h] px are the canvas pixels of [lens]'s picture from
 * ([ox], [oy]) (a window of it, see [PixelCanvas.originX]), so its pixel i shows nominal point (i + ox + x0) / k, which
 * the camera puts at that × scale + t. Only the part that shows, in whole canvas pixels, nearest-neighbour.
 */
internal fun DrawScope.drawPicture(image: ImageBitmap, lens: Lens, ox: Int, oy: Int, w: Int, h: Int, cam: Camera, alpha: Float = 1f) {
    if (alpha <= 0f || w <= 0 || h <= 0) return
    val s = cam.scale / lens.k
    // pixel i shows nominal (i + ox + x0) / k, which the camera puts at ((i + ox + x0) / k) × scale + t = i × s + (ox + x0) × s + t
    val tx = cam.tx + (lens.x0 + ox) * s; val ty = cam.ty + (lens.y0 + oy) * s
    val x0 = floor((0f - tx) / s).toInt().coerceIn(0, w)
    val y0 = floor((0f - ty) / s).toInt().coerceIn(0, h)
    val x1 = ceil((size.width - tx) / s).toInt().coerceIn(x0, w)
    val y1 = ceil((size.height - ty) / s).toInt().coerceIn(y0, h)
    if (x1 <= x0 || y1 <= y0) return
    val dx0 = (x0 * s + tx).roundToInt(); val dy0 = (y0 * s + ty).roundToInt()
    val dx1 = (x1 * s + tx).roundToInt(); val dy1 = (y1 * s + ty).roundToInt()
    drawImage(
        image,
        srcOffset = IntOffset(x0, y0),
        srcSize = IntSize(x1 - x0, y1 - y0),
        dstOffset = IntOffset(dx0, dy0),
        dstSize = IntSize(dx1 - dx0, dy1 - dy0),
        alpha = alpha,
        filterQuality = FilterQuality.None,
    )
}
