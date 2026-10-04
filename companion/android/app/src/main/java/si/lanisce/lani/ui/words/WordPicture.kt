package si.lanisce.lani.ui.words

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import si.lanisce.lani.BuildConfig
import si.lanisce.lani.game.render.scene.Stickers
import si.lanisce.lani.game.scene.StickerSpot
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A word's picture: [spot]'s sticker (the thing cut out of its scene, see [si.lanisce.lani.game.scene.WordStickers])
 * in a [size] box, else [fallback] (the emoji). The sticker is scaled by whole pixels, crisp (down by a whole divisor
 * when it is bigger than the box); while it is cut the box stays empty, and a sticker that can't be made shows the
 * fallback. It is decoration: TalkBack reads the word next to it.
 */
@Composable
fun StickerOr(spot: StickerSpot?, size: Dp, modifier: Modifier = Modifier, fallback: @Composable () -> Unit) {
    if (spot == null) return fallback()
    val cache = LocalContext.current.cacheDir
    val pic by produceState(StickerImages.cached(spot)?.let(Pic::Ready) ?: Pic.Loading, spot) {
        value = StickerImages.load(cache, spot)?.let(Pic::Ready) ?: Pic.None
    }
    when (val p = pic) {
        Pic.Loading -> Box(modifier.size(size))
        Pic.None -> fallback()
        is Pic.Ready -> Canvas(modifier.size(size)) {
            val img = p.image
            val fit = min(this.size.width / img.width, this.size.height / img.height)
            val scale = if (fit >= 1f) floor(fit) else 1f / ceil(1f / fit)
            val w = (img.width * scale).roundToInt(); val h = (img.height * scale).roundToInt()
            drawImage(
                img,
                dstOffset = IntOffset(((this.size.width - w) / 2).roundToInt(), ((this.size.height - h) / 2).roundToInt()),
                dstSize = IntSize(w, h),
                filterQuality = FilterQuality.None,
            )
        }
    }
}

private sealed interface Pic {
    data object Loading : Pic
    data object None : Pic
    class Ready(val image: ImageBitmap) : Pic
}

/**
 * The stickers' bitmaps: each cut once ([Stickers.cut], off the main thread, one at a time), then kept in memory and as
 * a PNG in the app's cache, in a folder of this app version (the painters change with it); older folders go.
 */
object StickerImages {
    private val memory = LruCache<StickerSpot, ImageBitmap>(64)
    private val cutting = Dispatchers.Default.limitedParallelism(1)
    /** Cut to nothing (touched on [cutting] only). */
    private val none = HashSet<StickerSpot>()
    private var swept = false

    fun cached(spot: StickerSpot): ImageBitmap? = memory.get(spot)

    /** [spot]'s sticker, from memory, the cache folder under [cacheDir] or cut now; null when the scene draws nothing there. */
    suspend fun load(cacheDir: File, spot: StickerSpot): ImageBitmap? = memory.get(spot) ?: withContext(cutting) {
        memory.get(spot) ?: if (spot in none) null else run {
            val root = File(cacheDir, "stickers")
            val dir = File(root, "v${Stickers.VERSION}-${BuildConfig.VERSION_CODE}")
            if (!swept) {
                swept = true
                root.listFiles()?.filter { it != dir }?.forEach { it.deleteRecursively() }
            }
            val file = File(dir, "${spot.art}-${spot.slot}.png")
            val bitmap = runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
                ?: runCatching { Stickers.cut(spot.art, spot.slot) }.getOrNull()?.let { s ->
                    Bitmap.createBitmap(s.argb, s.width, s.height, Bitmap.Config.ARGB_8888).also { b -> save(b, dir, file) }
                }
            if (bitmap == null) none += spot
            bitmap?.asImageBitmap()?.also { memory.put(spot, it) }
        }
    }

    private fun save(b: Bitmap, dir: File, file: File) = runCatching {
        dir.mkdirs()
        val tmp = File(dir, file.name + ".tmp")
        tmp.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        tmp.renameTo(file)
    }
}
