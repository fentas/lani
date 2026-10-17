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
fun PilePicture(state: GameState, keeper: ActiveKeeper, look: SpotLook?, modifier: Modifier = Modifier) {
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
            kopa = look,
        )
        runCatching { renderer.render(canvas, state, frame) }
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

/** The picture's canvas (at the village's finest detail [K]: 80 × 40 of its pixels round the pile), and its pace. */
private const val K = 3
private const val W = 240
private const val H = 120
private const val FRAME_MS = 140L
