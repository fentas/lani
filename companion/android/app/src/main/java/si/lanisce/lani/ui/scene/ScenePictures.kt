package si.lanisce.lani.ui.scene

import android.util.Log
import si.lanisce.lani.BuildConfig
import si.lanisce.lani.game.render.Camera
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.Picture
import si.lanisce.lani.game.render.PictureSlots
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.render.RenderThread
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneHit
import si.lanisce.lani.game.scene.ScenePainter

/**
 * A scene's pictures, rendered on a thread of their own as the village's are (si.lanisce.lani.game.render.TownPictures,
 * docs/plans/05-built/zoom-frames.md): the screen's frames only draw the newest ones through the camera, so a pinch or a
 * pan of a scene stays smooth however long its painter takes. [base]: the scene's canvas ([w] × [h]) by [painter],
 * every tick while it shows, closer up every [BASE_EVERY_NANOS] (its hits are the overlays' anchors) or when what it
 * shows changed; [fine]: the closer look by [closer], a window of the picture at detail 2 or 3 a third bigger than the
 * canvas round what the camera sees, every tick, and at once when the camera moved past it. The painters keep what they
 * drew last to hit-test it: what reads them (a tap, TalkBack's sky) does so holding [lock], which the render thread holds
 * while it draws. Nothing is drawn on the main thread, the first picture neither (2026-10-28,
 * docs/plans/05-built/home-return.md): the scene's backdrop shows until it is there, and it fades in.
 */
internal class ScenePictures(val w: Int, val h: Int, private val painter: ScenePainter, private val closer: ScenePainter, private val art: String) {
    private class Ask(val frame: SceneFrame, val base: Boolean, val k: Int, val fine: Lens?)

    /**
     * Held while a painter draws, and by what reads a painter's last frame (a tap, the sky's list): the painter itself, so
     * the pictures of one painter never draw on two threads. The view resized (a tablet turned, the window's size
     * changed) makes new pictures of the same painters while the old ones' thread may still be drawing, and a painter
     * keeps what it draws on in itself (a frame drawn on two canvases at once read past one's end, QA 2026-10-28).
     */
    val lock: Any = painter

    private val thread = RenderThread<Ask>("SceneRender", "SceneView", merge = { p, a ->
        Ask(a.frame, a.base || p.base, a.k, a.fine ?: p.fine?.takeIf { it.k == a.k })
    }) { draw(it) }

    /** Bumped on the main thread for every picture handed over: the draw phase reads it. */
    val version: Long get() = thread.version

    @Volatile var base: Picture? = null
        private set
    @Volatile var fine: Picture? = null
        private set

    /** The scene canvas's things and people (the overlays' anchors), its backdrop and whether it drew the real sky: the newest base's. */
    @Volatile var hits: List<SceneHit> = emptyList()
        private set
    @Volatile var backdrop = 0
        private set
    @Volatile var showsSky = false
        private set

    /** The closer look can't be drawn (its painter failed): the scene stays as it is, magnified. */
    @Volatile var fineFailed = false
        private set

    /** The canvases the painters drew last, and the closer look's lens: what a tap reads, holding [lock]. */
    val baseCanvas = PixelCanvas(w, h)
    val fineCanvas = PixelCanvas(w * 4 / 3 + 4, h * 4 / 3 + 4)
    var fineLens = Lens()
        private set

    @Volatile private var drewBase: Picture? = null
    @Volatile private var drewFine: Picture? = null
    private val bases = PictureSlots()
    private val fines = PictureSlots()

    // the main thread's: what it asked for last
    private var baseKey = -1L
    private var fineTick = -1L
    private var asked: Lens? = null

    /**
     * The draw at clock [tick], detail [k], camera [cam] on a [viewW] × [viewH] screen: asks for what the screen needs
     * that it hasn't asked for yet. [changed]: who is there, what is highlighted or tappable changed.
     */
    fun ask(tick: Long, k: Int, cam: Camera, viewW: Int, viewH: Int, frame: SceneFrame, changed: Boolean) {
        val key = if (k == 1) tick else tick / BASE_EVERY_NANOS
        val wantBase = base == null || changed || key != baseKey
        var lens: Lens? = null
        if (k > 1 && !fineFailed) {
            val have = asked?.takeIf { it.k == k }
            val shown = have != null && covers(have, cam, viewW, viewH)
            if (tick != fineTick || changed || !shown) lens = Lens.of(k, cam, viewW, viewH, w, h, fineCanvas.width, fineCanvas.height)
        }
        if (!wantBase && lens == null) return
        if (wantBase) baseKey = key
        if (lens != null) { fineTick = tick; asked = lens }
        val ask = Ask(frame, wantBase, k, lens)
        thread.post(ask)
    }

    /** Whether the closer look through [lens] covers what camera [c] shows (nominal pixels). */
    private fun covers(lens: Lens, c: Camera, viewW: Int, viewH: Int): Boolean =
        lens.bx(0f) <= maxOf(0f, c.toCanvasX(0f)) && lens.by(0f) <= maxOf(0f, c.toCanvasY(0f)) &&
            lens.bx(fineCanvas.width.toFloat()) >= minOf(w.toFloat(), c.toCanvasX(viewW.toFloat())) &&
            lens.by(fineCanvas.height.toFloat()) >= minOf(h.toFloat(), c.toCanvasY(viewH.toFloat()))

    /** The screen drew [base] and [fine] (the newest closer look there was, drawn or not). */
    fun drew(base: Picture?, fine: Picture?) { drewBase = base; drewFine = fine }

    fun close() = thread.close()

    /** The thread that drew the last picture (tests: never the one that asked). */
    val drewOn: Thread? get() = thread.drewOn

    private fun draw(ask: Ask) {
        if (ask.base) {
            val t0 = System.nanoTime()
            val got = synchronized(lock) {
                val hits = painter.render(baseCanvas, ask.frame)
                Triple(hits, painter.backdrop(ask.frame), painter.showsSky)
            }
            if (BuildConfig.QA) timedBase(System.nanoTime() - t0)
            hits = got.first; backdrop = got.second; showsSky = got.third
            base = bases.upload(baseCanvas, Lens(1, 0, 0, w, h), drewBase, base)
        }
        val lens = ask.fine
        if (lens != null && !fineFailed) {
            try {
                val t0 = System.nanoTime()
                synchronized(lock) {
                    closer.render(fineCanvas, ask.frame, lens)
                    fineLens = lens
                }
                if (BuildConfig.QA) timed(lens.k, System.nanoTime() - t0)
                // the painter drew its window of the picture at the lens's corner (its canvas's origin): the lens says where
                fine = fines.upload(fineCanvas, lens, drewFine, fine).also { it.ox = 0; it.oy = 0 }
            } catch (e: RuntimeException) {
                Log.w("SceneView", "$art can't be drawn at detail ${lens.k}", e)
                fineFailed = true
            }
        }
    }

    private var timedNanos = 0L
    private var timedFrames = 0
    private var baseNanos = 0L
    private var baseFrames = 0

    /** Debug builds: the scene canvas's render time, logged every 24 frames. */
    private fun timedBase(nanos: Long) {
        baseNanos += nanos
        if (++baseFrames < 24) return
        Log.d("SceneView", "%s at detail 1: %.1f ms per frame (%d × %d)".format(art, baseNanos / 1e6 / baseFrames, w, h))
        baseNanos = 0L; baseFrames = 0
    }

    /** Debug builds: the closer look's render time, logged every 24 frames, about 2 s (QA reads it). */
    private fun timed(k: Int, nanos: Long) {
        timedNanos += nanos
        if (++timedFrames < 24) return
        Log.d("SceneView", "%s at detail %d: %.1f ms per frame (%d × %d)".format(art, k, timedNanos / 1e6 / timedFrames, fineCanvas.width, fineCanvas.height))
        timedNanos = 0L; timedFrames = 0
    }

    companion object {
        /** Closer up, the scene's own canvas under the finer picture is drawn again this often (ns). */
        const val BASE_EVERY_NANOS = 500_000_000L
    }
}
