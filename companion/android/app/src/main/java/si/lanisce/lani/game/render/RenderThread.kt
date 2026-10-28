package si.lanisce.lani.game.render

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * A picture a render thread made: its [bitmap] (of which [w] × [h] px are used), the [lens] it was drawn through, and
 * where its canvas lay in that lens's picture ([ox], [oy]: the canvas's [PixelCanvas.originX], originY). Written before
 * it is handed over, read-only after.
 */
internal class Picture(val bitmap: Bitmap) {
    val image: ImageBitmap = bitmap.asImageBitmap()
    var lens = Lens()
    var ox = 0
    var oy = 0
    var w = 0
    var h = 0
}

/**
 * The three bitmaps of one layer of pictures (the nominal one, the closer look): the render thread draws into one that
 * is neither on the screen nor the one the screen draws next, so a frame never shows a picture half drawn over.
 */
internal class PictureSlots {
    private val slots = arrayOfNulls<Picture>(3)

    /**
     * [canvas]'s pixels as the picture of [lens] from the canvas's corner, in a slot that is neither [drawn] (what the
     * screen drew last) nor [shown] (the newest handed over, which the screen draws next).
     */
    fun upload(canvas: PixelCanvas, lens: Lens, drawn: Picture?, shown: Picture?): Picture {
        val pw = canvas.width; val ph = canvas.height
        // three slots, at most two of them taken
        val i = slots.indices.first { slots[it] == null || (slots[it] !== drawn && slots[it] !== shown) }
        val old = slots[i]
        val p = if (old != null && old.bitmap.width >= pw && old.bitmap.height >= ph) old
        else Picture(Bitmap.createBitmap(roundUp(maxOf(pw, old?.bitmap?.width ?: 0)), roundUp(maxOf(ph, old?.bitmap?.height ?: 0)), Bitmap.Config.ARGB_8888)).also { slots[i] = it }
        p.bitmap.setPixels(canvas.pixels, 0, pw, 0, 0, pw, ph)
        // its texture made on the screen's render thread now, not when it is first drawn
        p.bitmap.prepareToDraw()
        p.lens = lens; p.ox = canvas.originX; p.oy = canvas.originY; p.w = pw; p.h = ph
        return p
    }

    private fun roundUp(v: Int) = (v + 63) / 64 * 64
}

/**
 * A thread of its own that renders what the main thread asks for ([post]), so the screen's frames never wait for a
 * picture, the first one neither (2026-10-27/28, docs/plans/05-built/zoom-frames.md, home-return.md): what is asked while
 * it renders waits as one ask ([merge]: the newest frame, everything asked for), so it never falls behind; [draw] renders
 * an ask and hands its pictures over, and [version] is bumped on the main thread for each, which the screen's draw reads.
 * The thread starts with an ask and ends after [IDLE_MS] without one (a view kept for when it comes back, off the screen,
 * holds no thread), and a new ask starts it again; one thread at a time. [close] ends it for good. A failed picture is
 * logged ([tag]); a debug or qa build crashes then, as the main thread's render did, which is what QA looks for.
 */
internal class RenderThread<A : Any>(private val name: String, private val tag: String, private val merge: (A, A) -> A, private val draw: (A) -> Unit) {
    /** Bumped on the main thread for every ask drawn: the draw phase reads it. */
    var version by mutableLongStateOf(0L)
        private set

    private val main = Handler(Looper.getMainLooper())
    private val lock = Object()
    // guarded by [lock]
    private var pending: A? = null
    private var closed = false
    private var thread: Thread? = null

    /** The thread that drew the last ask (tests: never the one that asked). */
    @Volatile var drewOn: Thread? = null
        private set

    /** Whether the thread is alive now (tests: it ends when idle). */
    val alive: Boolean get() = synchronized(lock) { thread != null }

    /** Asks for [a]: drawn next on the render thread, with what still waits. Never draws on the calling thread. */
    fun post(a: A) {
        synchronized(lock) {
            if (closed) return
            pending = pending?.let { merge(it, a) } ?: a
            if (thread == null) thread = Thread(::loop, name).apply { isDaemon = true; start() }
            else lock.notifyAll()
        }
    }

    /** The view is gone for good: the thread ends after the picture it draws, and nothing more is drawn. */
    fun close() {
        synchronized(lock) {
            closed = true
            pending = null
            lock.notifyAll()
        }
    }

    private fun loop() {
        // as the screen's own drawing does (a background thread's priority could keep it to the little cores); a system
        // that refuses it renders at the default
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY) }
        while (true) {
            val a = synchronized(lock) {
                if (pending == null && !closed) lock.wait(IDLE_MS)
                val next = pending
                // idle so long, or closed: the thread ends (a new ask starts another)
                if (next == null || closed) { thread = null; return }
                pending = null
                next
            }
            try {
                draw(a)
                drewOn = Thread.currentThread()
                main.post { version++ }
            } catch (e: RuntimeException) {
                Log.e(tag, "a picture failed", e)
                if (si.lanisce.lani.BuildConfig.QA) main.post { throw e }
            }
        }
    }

    companion object {
        /** The thread ends after this long without an ask (ms). */
        const val IDLE_MS = 2_000L
    }
}
