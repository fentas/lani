package si.lanisce.lani.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.suspendCancellableCoroutine
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.render.scene.PortraitsPainter
import si.lanisce.lani.game.scene.Face
import si.lanisce.lani.game.scene.Framing
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.Portraits
import si.lanisce.lani.game.scene.face
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

private const val FRAME_NANOS = 83_000_000L // ~12 fps

/**
 * A villager's pixel portrait ([Portraits.painter]): [art] sprite in [pose] with [face] (by default the pose's own:
 * a cheer laughs, a shrug is sad; companion/SCENES.md "Faces"), [px]×[px] canvas pixels, framed as a bust or the head alone
 * ([framing]), scaled up by a whole number to fill the space, centred. Animates unless [animate] is false. [seed] varies
 * the village's own people (woman, man): pass the villager id's hash.
 *
 * Drawn off the main thread: an animated portrait's frames on the portraits' own thread ([PortraitThread], one for every
 * portrait on the screen, at its 12 fps), the main thread only drawing the picture it hands over (its first frame aside,
 * drawn at once so it never shows empty); a still one ([animate] false) comes from the stills' cache ([Stills]: by sprite,
 * pose, face, framing, size and the light of the quarter hour), drawn there the first time.
 */
@Composable
fun Portrait(
    art: String, pose: Pose = Pose.IDLE, modifier: Modifier = Modifier, px: Int = 64, animate: Boolean = true, seed: Int = 0,
    face: Face? = pose.face, framing: Framing = Framing.BUST,
) {
    if (!animate) {
        StillPortrait(Stills.Key(art, pose, face, framing, px, seed, Stills.quarter(LocalTime.now())), modifier)
        return
    }
    val slot = remember(px) { PortraitSlot(px) }
    DisposableEffect(slot) { onDispose(slot::close) }
    // since when the face shows: what it adds by the head pops in
    val faceFrom = remember(face) { SystemClock.uptimeMillis() }
    val asked = PortraitAsk(art, pose, face, framing, seed, faceFrom)
    val ask by rememberUpdatedState(asked)
    // the first frame, drawn here (main thread) once, so the portrait never shows empty before the thread's
    val first = remember(slot) { arrayOfNulls<ImageBitmap>(1) }
    // the portrait's clock (ns since it began), its ticks' and a change's alike
    val clock = remember(slot) { LongArray(1) }
    // a new pose or face asked for at once, not at the next tick
    LaunchedEffect(slot, asked) { PortraitThread.post(slot, asked.at(clock[0] / 1e9)) }
    LaunchedEffect(slot) {
        val start = withFrameNanos { it }
        var last = -FRAME_NANOS
        while (true) withFrameNanos { now ->
            if (now - start - last >= FRAME_NANOS) {
                last = now - start
                clock[0] = last
                PortraitThread.post(slot, ask.at(last / 1e9))
            }
        }
    }
    Canvas(modifier) {
        val shown = slot.shown
        if (shown == null) {
            val image = first[0] ?: slot.firstFrame(ask.at(0.0)).asImageBitmap().also { first[0] = it }
            drawPortrait(image, px)
            return@Canvas
        }
        slot.displayed(shown)
        drawPortrait(shown.image, px)
    }
}

/** [image]'s [px] × [px] pixels scaled up whole to fill the draw area, centred, crisp. */
private fun DrawScope.drawPortrait(image: ImageBitmap, px: Int) {
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

/** A still portrait from the cache ([Stills]), drawn on the portraits' thread the first time it's needed. */
@Composable
private fun StillPortrait(key: Stills.Key, modifier: Modifier) {
    val image by produceState(Stills.cached(key), key) { value = Stills.get(key) }
    Canvas(modifier) { image?.let { drawPortrait(it, key.px) } }
}

/**
 * One frame of a portrait asked for: who, how, at [time] (its clock, seconds) and [hour] (the light); [faceFrom] the
 * uptime the face began at, which its age is counted from.
 */
internal data class PortraitAsk(
    val art: String, val pose: Pose, val face: Face?, val framing: Framing, val seed: Int, val faceFrom: Long,
    val time: Double = 0.0, val hour: Float = 12f, val faceAge: Double = 9.0,
) {
    /** This portrait at [t] on its clock, the light now, the face's age now. */
    fun at(t: Double): PortraitAsk {
        val now = LocalTime.now()
        return copy(time = t, hour = now.hour + now.minute / 60f, faceAge = (SystemClock.uptimeMillis() - faceFrom) / 1000.0)
    }
}

/** A drawn frame: its bitmap and the image the screen draws. */
internal class PortraitFrame(val bitmap: Bitmap) {
    val image: ImageBitmap = bitmap.asImageBitmap()
}

/**
 * What one portrait on the screen has: three frames, so the thread draws into one that is neither on the screen nor
 * handed over to be drawn next ([PortraitThread]); [shown], the newest handed over (main thread).
 */
internal class PortraitSlot(val px: Int) {
    private val frames = Array(3) { PortraitFrame(Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)) }
    /** The thread's canvas. */
    val canvas = PixelCanvas(px, px)
    var shown by mutableStateOf<PortraitFrame?>(null)
        private set
    private val drawn = AtomicInteger(-1)
    private val handed = AtomicInteger(-1)
    @Volatile var closed = false
        private set

    /** The thread: a frame neither on the screen nor handed over, for [canvas]'s pixels. */
    fun free(): Int = (0 until 3).first { it != drawn.get() && it != handed.get() }

    /** The thread: frame [i] holds the newest picture now; the screen gets it ([main]). */
    fun hand(i: Int, main: Handler) {
        frames[i].bitmap.setPixels(canvas.pixels, 0, px, 0, 0, px, px)
        frames[i].bitmap.prepareToDraw()
        handed.set(i)
        main.post { if (!closed) shown = frames[i] }
    }

    /** The screen drew [f]. */
    fun displayed(f: PortraitFrame) {
        drawn.set(frames.indexOf(f))
    }

    /** The first frame, drawn on the calling thread (the main one) with the app's portrait painter. */
    fun firstFrame(a: PortraitAsk): Bitmap {
        val c = PixelCanvas(px, px)
        Portraits.painter.render(c, a.art, a.pose, a.time, a.hour, a.seed, a.face, a.faceAge, a.framing)
        return Bitmap.createBitmap(c.pixels, px, px, Bitmap.Config.ARGB_8888)
    }

    fun close() { closed = true }
}

/**
 * The portraits' thread: draws every animated portrait's frames ([post]: the newest ask of each, what was asked meanwhile
 * dropped) and the stills ([Stills]), with a painter of its own, so the main thread draws no portrait (2026-10-29,
 * docs/plans/05-built/faces.md). It starts with an ask and ends after [IDLE_MS] without one. A failed portrait is logged;
 * a debug or qa build crashes then, as the main thread's drawing did.
 */
internal object PortraitThread {
    private const val TAG = "Portraits"
    const val IDLE_MS = 2_000L

    private val main = Handler(Looper.getMainLooper())
    private val lock = Object()
    // guarded by [lock]
    private val pending = LinkedHashMap<Any, () -> Unit>()
    private var thread: Thread? = null

    // the thread's own
    private val painter by lazy { PortraitsPainter() }

    /** The next frame of [slot]: drawn soon, instead of one asked before that's not drawn yet. */
    fun post(slot: PortraitSlot, a: PortraitAsk) = enqueue(slot) {
        if (slot.closed) return@enqueue
        painter.render(slot.canvas, a.art, a.pose, a.time, a.hour, a.seed, a.face, a.faceAge, a.framing)
        slot.hand(slot.free(), main)
    }

    /** Draws [a] on the thread into a new bitmap, handed to [done] on the main thread. */
    fun still(key: Any, a: PortraitAsk, px: Int, done: (Bitmap) -> Unit) = enqueue(key) {
        val c = PixelCanvas(px, px)
        painter.render(c, a.art, a.pose, a.time, a.hour, a.seed, a.face, a.faceAge, a.framing)
        val b = Bitmap.createBitmap(c.pixels, px, px, Bitmap.Config.ARGB_8888)
        b.prepareToDraw()
        main.post { done(b) }
    }

    private fun enqueue(key: Any, job: () -> Unit) {
        synchronized(lock) {
            pending[key] = job
            if (thread == null) thread = Thread(::loop, "portraits").apply { isDaemon = true; start() }
            else lock.notifyAll()
        }
    }

    private fun loop() {
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY) }
        while (true) {
            val jobs = synchronized(lock) {
                if (pending.isEmpty()) lock.wait(IDLE_MS)
                if (pending.isEmpty()) { thread = null; return }
                val all = pending.values.toList()
                pending.clear()
                all
            }
            for (job in jobs) try {
                job()
            } catch (e: RuntimeException) {
                Log.e(TAG, "a portrait failed", e)
                if (si.lanisce.lani.BuildConfig.QA) main.post { throw e }
            }
        }
    }
}

/**
 * The still portraits ([Portrait] with animate false, [si.lanisce.lani.ui.stage.FaceAvatar]): each drawn once on the
 * portraits' thread and kept ([MAX] of them, the least used going first), by sprite, pose, face, framing, canvas size,
 * seed and the light's quarter hour. A conversation's bubbles show the same few faces again and again: drawn once each.
 */
internal object Stills {
    const val MAX = 192

    data class Key(val art: String, val pose: Pose, val face: Face?, val framing: Framing, val px: Int, val seed: Int, val quarter: Int)

    private val cache = LruCache<Key, ImageBitmap>(MAX)

    /** The quarter hours of the day, 0..95: the light a still is drawn in. */
    fun quarter(t: LocalTime): Int = t.hour * 4 + t.minute / 15

    /** The time of a still on the portrait's clock: eyes open, whatever the seed (a blink's moment avoided). */
    fun stillTime(seed: Int): Double {
        val off = ((seed and 0xFF) * 0.031) % 3.7
        return ((1.2 - off) % 3.7 + 3.7) % 3.7
    }

    // who waits for a still being drawn (main thread)
    private val waiting = HashMap<Key, MutableList<(ImageBitmap) -> Unit>>()

    fun cached(key: Key): ImageBitmap? = cache.get(key)

    /** [key]'s still: kept, or drawn now on the portraits' thread (once, however many wait for it). Main thread. */
    suspend fun get(key: Key): ImageBitmap {
        cache.get(key)?.let { return it }
        return suspendCancellableCoroutine { cont ->
            val done: (ImageBitmap) -> Unit = { if (cont.isActive) cont.resume(it) }
            val queue = waiting[key]
            if (queue != null) { queue += done; return@suspendCancellableCoroutine }
            waiting[key] = mutableListOf(done)
            val a = PortraitAsk(key.art, key.pose, key.face, key.framing, key.seed, 0L, stillTime(key.seed), key.quarter / 4f, 9.0)
            PortraitThread.still(key, a, key.px) { b ->
                val image = b.asImageBitmap()
                cache.put(key, image)
                waiting.remove(key)?.forEach { it(image) }
            }
        }
    }

    fun clear() = cache.evictAll()
}
