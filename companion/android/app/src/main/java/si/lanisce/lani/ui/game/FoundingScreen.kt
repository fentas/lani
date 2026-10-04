package si.lanisce.lani.ui.game

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.game.Founding
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.render.Frame
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.render.SceneFit
import si.lanisce.lani.game.render.VillageRenderer
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.theme.SloBlue
import si.lanisce.lani.ui.theme.SloBlueDeep
import si.lanisce.lani.ui.theme.TriglavRed
import java.time.LocalDate
import kotlin.random.Random

/**
 * "Kje bo tvoja vas? · Where will your village be?": the first start's last step (plan 2, "Jan's decisions"; GAME.md,
 * "The choice at setup"), shown once the node has no village and the phone none either. The learner sees the region (the
 * culture packs of the village's language; the node plays one of them, `lani-profile add --culture`), picks a landscape
 * (the region's choices, the node's `--landscape` first), sees the village drawn where it would be, at its first age, and
 * rolls another place ("🎲 Nov kraj · Another place") until they like it; confirming founds the village there, for good.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoundingScreen(vm: AppViewModel) {
    // the choice is the step: back doesn't skip it
    BackHandler {}
    val culture = vm.culture
    var founding by remember(culture) { mutableStateOf(Founding.start(culture, vm.nodeLandscape, Random.nextLong())) }
    val preview = remember(founding) { founding.preview() }
    val choices = remember(culture) { Founding.choices(culture) }
    // the regions of the village's language: the one the node plays, and the others there are (chosen on the node)
    val language = remember(culture) { Cultures.manifest(culture)?.language }
    val regions = remember(culture, language) {
        Cultures.ids.mapNotNull { id -> Cultures.manifest(id)?.takeIf { it.language == language && it.status == "complete" }?.let { id to it } }
    }
    val white = Color.White
    val chipColors = FilterChipDefaults.filterChipColors(
        labelColor = white, selectedLabelColor = SloBlueDeep, selectedContainerColor = white,
        disabledLabelColor = white.copy(alpha = 0.45f),
    )

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SloBlueDeep, SloBlue))).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("🏡", style = MaterialTheme.typography.displayMedium)
        Text(inTarget("founding.title"), style = MaterialTheme.typography.headlineMedium, color = white)
        Text(inBase("founding.about"), color = white.copy(alpha = 0.85f))

        Text(bi("founding.region"), style = MaterialTheme.typography.titleSmall, color = white)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((id, m) in regions) {
                FilterChip(selected = id == culture, onClick = {}, enabled = id == culture, label = { Text(m.region.bi()) }, colors = chipColors)
            }
        }
        if (regions.size > 1) Text(inBase("founding.regionOnNode"), style = MaterialTheme.typography.bodySmall, color = white.copy(alpha = 0.75f))

        Text(bi("founding.landscape"), style = MaterialTheme.typography.titleSmall, color = white)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (l in choices) {
                FilterChip(
                    selected = l == founding.landscape, onClick = { founding = founding.on(l) },
                    label = { Text("${l.emoji} ${bi(l.key)}") }, colors = chipColors,
                )
            }
        }

        LandPreview(
            preview,
            Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(12.dp))
                .semantics { contentDescription = "${founding.landscape.emoji} ${bi(founding.landscape.key)}" },
        )

        OutlinedButton(onClick = { founding = founding.reroll() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("🎲 ${bi("founding.anotherPlace")}", color = white)
        }
        BigButton("✓ ${bi("founding.here")}", onClick = { vm.game.found(founding) }, color = TriglavRed)
        Text(inBase("founding.once"), style = MaterialTheme.typography.bodySmall, color = white.copy(alpha = 0.75f))
    }
}

/**
 * The village [state] drawn as it would stand, by day in this month, the whole land round it (the town's framing,
 * [SceneFit.town]): the fire flickers, nothing to tap.
 */
@Composable
private fun LandPreview(state: GameState, modifier: Modifier) {
    val renderer = remember { VillageRenderer() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val clock = remember { mutableLongStateOf(0L) }
    val fit = SceneFit.town(size.width, size.height)
    val canvas = remember(fit.width, fit.height) { PixelCanvas(fit.width, fit.height) }
    val bitmap = remember(fit.width, fit.height) { Bitmap.createBitmap(fit.width, fit.height, Bitmap.Config.ARGB_8888) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val month = remember { LocalDate.now().monthValue }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        var last = -PREVIEW_FRAME
        while (true) withFrameNanos { now -> if (now - start - last >= PREVIEW_FRAME) { last = now - start; clock.longValue = last } }
    }
    Box(modifier.onSizeChanged { size = it }) {
        Canvas(Modifier.fillMaxSize()) {
            if (size.width == 0 || size.height == 0) return@Canvas
            val t = clock.longValue / 1e9
            renderer.render(canvas, state, Frame(time = t, hour = 11f, month = month, sea = Cultures.current.world.backdrop?.sea == true))
            bitmap.setPixels(canvas.pixels, 0, fit.width, 0, 0, fit.width, fit.height)
            drawRect(Color(renderer.skyColor))
            drawImage(
                image, srcOffset = IntOffset.Zero, srcSize = IntSize(fit.width, fit.height),
                dstOffset = IntOffset(fit.offX, fit.offY), dstSize = IntSize(fit.width * fit.scale, fit.height * fit.scale),
                filterQuality = FilterQuality.None,
            )
        }
    }
}

/** The preview's frame time: about 8 a second (the fire's flicker). */
private const val PREVIEW_FRAME = 125_000_000L
