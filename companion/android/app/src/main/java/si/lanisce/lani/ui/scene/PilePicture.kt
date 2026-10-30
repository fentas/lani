package si.lanisce.lani.ui.scene

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.render.Foreground
import si.lanisce.lani.game.render.Frame
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.Moon
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.render.SceneFit
import si.lanisce.lani.game.render.VillageRenderer
import si.lanisce.lani.game.scene.ActiveKeeper
import si.lanisce.lani.game.scene.Keepers
import si.lanisce.lani.game.scene.SpotLook
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.scene.TownSpots
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * Where the pile is on the village's tall canvas (the phone's, as the village fits it), for the picture over a keeper's
 * talk; null when the village has no woods that deep there (a short canvas: the woods' backdrop then).
 */
fun pileSpot(state: GameState, screenW: Int, screenH: Int): Pair<SceneFit, Foreground>? {
    val fit = SceneFit.town(screenW, screenH)
    val fg = Foreground.of(state, fit.width, fit.height)?.takeIf { it.kopa } ?: return null
    return fit to fg
}

/**
 * The charcoal pile as it is today, close up, over the charcoal burner's talk (companion/SCENES.md, "The charcoal pile"):
 * the village's own picture of the woods round it at its finest detail, at the hour it is, the pile as its cycle has it
 * ([look]: the logs, the stack, the smoke's colour …), and him sitting by it. The smoke drifts.
 */
@Composable
fun PilePicture(state: GameState, keeper: ActiveKeeper, look: SpotLook?, modifier: Modifier = Modifier, fire: Float = 0f) {
    val config = LocalConfiguration.current
    val density = LocalDensity.current.density
    val spot = remember(state.age, state.land, config.screenWidthDp, config.screenHeightDp) {
        pileSpot(state, (config.screenWidthDp * density).roundToInt(), (config.screenHeightDp * density).roundToInt())
    } ?: return
    val (fit, fg) = spot
    val canvas = remember { PixelCanvas(W, H) }
    val bitmap = remember { Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val renderer = remember { VillageRenderer() }
    val him = remember(keeper.keeper) { Keepers.villager(keeper) }
    var t by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) { delay(FRAME_MS); t = (System.nanoTime() - start) / 1e9 }
    }
    Canvas(modifier) {
        val now = si.lanisce.lani.app.QaHooks.sceneNow(LocalDateTime.now())
        // the pile a little right of the middle, its burner left of it, the smoke above
        val lens = Lens(K, (fg.kopaX * K - W * 0.56f).roundToInt(), (fg.kopaY * K - H * 0.66f).roundToInt(), fit.width, fit.height)
        val frame = Frame(
            time = t, hour = now.hour + now.minute / 60f, month = now.monthValue, lens = lens, people = listOf(him),
            standing = mapOf(him.id to TownPlace.Spot(TownSpots.KOPA)), today = now.toLocalDate().toString(), moon = Moon.now(),
            kopa = look, culture = si.lanisce.lani.game.culture.Cultures.current.id,
        )
        runCatching { renderer.render(canvas, state, frame) }
        if (fire > 0f) flames(canvas, t, fire)
        bitmap.setPixels(canvas.pixels, 0, W, 0, 0, W, H)
        // as wide as the panel, the middle of it showing
        val scale = size.width / W
        val shownH = (H * scale).roundToInt()
        drawImage(
            image, srcOffset = IntOffset.Zero, srcSize = IntSize(W, H),
            dstOffset = IntOffset(0, ((size.height - shownH) / 2f).roundToInt()), dstSize = IntSize(size.width.roundToInt(), shownH),
            filterQuality = FilterQuality.None,
        )
    }
}

/**
 * The pile broken out on the night watch (Miha's "Hitro! Glej, ogenj! Kopa se je odprla!", [fire] 0.5): flames licking out of
 * the gap in its crown, sparks going up, a red glow on the earth; lost, the whole pile ablaze ([fire] 1). Drawn over the
 * village's picture at the pile (the canvas has it at [PILE_X], [PILE_Y]), flickering with [t].
 */
private fun flames(c: PixelCanvas, t: Double, fire: Float) {
    val cx = PILE_X; val top = PILE_Y - 6.5f * K
    val wide = (4f + 14f * fire) * K * 0.5f
    val tall = (5f + 9f * fire) * K
    // the glow on the dome and the ground round it
    for (y in (top - tall).toInt().coerceAtLeast(0) until (PILE_Y + K).toInt().coerceAtMost(H)) for (x in (cx - wide * 2f).toInt().coerceAtLeast(0) until (cx + wide * 2f).toInt().coerceAtMost(W)) {
        val d = ((x - cx) / (wide * 2f)).let { it * it } + ((y - top) / (tall * 1.4f)).let { it * it }
        if (d < 1f) c.blend(x, y, 0xFFFF7A2A.toInt(), 0.35f * (1f - d) * fire.coerceAtLeast(0.5f))
    }
    // tongues of flame, each its own height and flicker
    val n = (5 + 8 * fire).toInt()
    for (k in 0 until n) {
        val u = if (n == 1) 0.5f else k / (n - 1f)
        val fx = cx - wide + 2f * wide * u
        val h = tall * (0.45f + 0.55f * kotlin.math.abs(kotlin.math.sin(t * (5.0 + k * 0.7) + k * 1.9)).toFloat()) * (1f - 0.5f * kotlin.math.abs(u - 0.5f))
        val base = top + (if (fire >= 0.9f) 3f * K * kotlin.math.abs(u - 0.5f) else 0f)
        for (j in 0 until h.toInt()) {
            val f = j / h
            val half = (1f - f) * K * 0.9f
            val col = when { f < 0.25f -> 0xFFFFF4C2.toInt(); f < 0.55f -> 0xFFFFC23A.toInt(); f < 0.8f -> 0xFFFF7A2A.toInt(); else -> 0xFFD03A1A.toInt() }
            val sway = kotlin.math.sin(t * 7.0 + k + j * 0.3).toFloat() * f * K * 0.6f
            for (x in (fx - half + sway).toInt()..(fx + half + sway).toInt()) if (x in 0 until W && (base - j).toInt() in 0 until H) c.set(x, (base - j).toInt(), col)
        }
    }
    // sparks going up
    for (k in 0 until (6 + 10 * fire).toInt()) {
        val ph = ((t * 0.7 + k * 0.37) % 1.0).toFloat()
        val sx = cx + (kotlin.math.sin(k * 2.3 + t * 0.9).toFloat()) * wide * 1.2f
        val sy = top - tall * 0.5f - ph * tall * 1.6f
        if (sx.toInt() in 0 until W && sy.toInt() in 0 until H) c.blend(sx.toInt(), sy.toInt(), 0xFFFFD77A.toInt(), 1f - ph)
    }
}

/** The picture's canvas (at the village's finest detail [K]: 80 × 40 of its pixels round the pile), and its pace. */
private const val K = 3
private const val W = 240
private const val H = 120

/** Where the pile's middle stands on the picture's canvas: the lens puts it there ([PilePicture]). */
private const val PILE_X = W * 0.56f
private const val PILE_Y = H * 0.66f
private const val FRAME_MS = 140L
