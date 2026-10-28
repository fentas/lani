package si.lanisce.lani.game.render

import android.util.Log
import si.lanisce.lani.game.GameState

/**
 * The town's pictures, rendered on a thread of their own so the screen's frames never wait for them (2026-10-27,
 * docs/plans/05-built/zoom-frames.md; [RenderThread]). The main thread's draw asks for what it needs ([ask]): the nominal
 * picture [base] every tick while it is what shows, else when what it shows changed; the closer look [fine], a window of
 * the picture at detail 2 or 3 covering the screen ([TownFrames]), every tick, and at once when the camera has moved past
 * what the one asked for covers. It draws the newest ones there are, through the camera, on the GPU. The render thread
 * draws them with [renderer] into canvases of its own and hands each one over in one of three bitmaps a layer
 * ([PictureSlots]), never the one the screen drew last ([drew]) nor the one it draws next. Nothing is ever drawn on the
 * main thread, the first picture neither (2026-10-28, docs/plans/05-built/home-return.md): the village's own pictures are
 * kept for when it comes back ([Retained]), so a Back into it shows the last one at once; a cold one shows the sky until
 * the first is there, and fades it in.
 */
internal class TownPictures(val w: Int, val h: Int, val renderer: VillageRenderer) {
    /** What the main thread asks of the render thread: [frame] of [state], the nominal picture and/or the closer look's [window] at detail [k]. */
    private class Ask(val state: GameState, val frame: Frame, val base: Boolean, val k: Int, val window: PixelRect?, val screen: PixelRect?)

    private val thread = RenderThread<Ask>("TownRender", "TownScene", merge = { p, a ->
        // with what still waits: the newest frame, every layer asked for
        Ask(a.state, a.frame, a.base || p.base, a.k, a.window ?: p.window?.takeIf { p.k == a.k }, a.screen ?: p.screen)
    }) { draw(it) }

    /** Bumped on the main thread for every picture handed over: the draw phase reads it. */
    val version: Long get() = thread.version

    /** The newest nominal picture, and the newest closer look (its lens's detail may not be the one wanted now). */
    @Volatile var base: Picture? = null
        private set
    @Volatile var fine: Picture? = null
        private set

    /** What the screen drew last (written by the main thread): never drawn into while it may be on the screen. */
    @Volatile private var drewBase: Picture? = null
    @Volatile private var drewFine: Picture? = null

    private val bases = PictureSlots()
    private val fines = PictureSlots()
    private val baseCanvas = PixelCanvas(w, h)
    /** The closer look's canvases by size (a few: a pinch goes through sizes in steps of [TownFrames.SIZE_STEP]). */
    private val canvases = LinkedHashMap<Long, PixelCanvas>(4, 0.75f, true)

    // the main thread's: what it asked for last
    private var baseTick = -1L
    private var fineTick = -1L
    private var asked: PixelRect? = null
    private var askedK = 0
    private var lastCam: Camera? = null
    private var movedAt = 0L

    /**
     * The draw at clock [tick], detail [k], camera [cam] on a [viewW] × [viewH] screen: asks for what the screen needs
     * that it hasn't asked for yet. [changed]: what the pictures show changed (not just the clock's time).
     */
    fun ask(tick: Long, k: Int, cam: Camera, viewW: Int, viewH: Int, state: GameState, frame: Frame, changed: Boolean) {
        val now = System.nanoTime()
        if (cam != lastCam) { lastCam = cam; movedAt = now }
        val moving = now - movedAt < MOVING_NANOS
        val wantBase = base == null || changed || (k == 1 && tick != baseTick)
        var window: PixelRect? = null
        var screen: PixelRect? = null
        if (k > 1) {
            val s = TownFrames.screen(k, cam, viewW, viewH, w, h)
            screen = s
            val have = asked?.takeIf { askedK == k }
            if (tick != fineTick || changed || have == null || !have.covers(TownFrames.needed(k, s, w, h))) window = TownFrames.window(k, s, moving, w, h)
        }
        if (!wantBase && window == null) return
        if (wantBase) baseTick = tick
        if (window != null) { fineTick = tick; asked = window; askedK = k }
        thread.post(Ask(state, frame, wantBase, k, window, screen))
    }

    /** The thread that drew the last picture (tests: never the one that asked). */
    val drewOn: Thread? get() = thread.drewOn

    /** Whether its render thread is alive (tests: it ends when nothing is asked). */
    val rendering: Boolean get() = thread.alive

    /** The screen drew [base] and [fine] (the newest closer look there was, drawn or not). */
    fun drew(base: Picture?, fine: Picture?) { drewBase = base; drewFine = fine }

    /** Not kept any more ([Retained]): the render thread ends after the picture it draws, and draws no more. */
    fun close() = thread.close()

    /** Renders what [ask] asks for and hands it over. */
    private fun draw(ask: Ask) {
        if (ask.base) {
            val t0 = System.nanoTime()
            renderer.render(baseCanvas, ask.state, ask.frame)
            timed(ask.state, 1, w, h, System.nanoTime() - t0)
            base = bases.upload(baseCanvas, Lens(1, 0, 0, w, h), drewBase, base)
        }
        val win = ask.window
        if (win != null && ask.k > 1) {
            val c = canvasFor(win, w * ask.k, h * ask.k)
            val lens = TownFrames.lens(ask.k, w, h)
            val t0 = System.nanoTime()
            renderer.render(c, ask.state, ask.frame.copy(lens = lens, screen = ask.screen))
            timed(ask.state, ask.k, win.w, win.h, System.nanoTime() - t0)
            fine = fines.upload(c, lens, drewFine, fine)
        }
    }

    /**
     * A canvas for window [win] of a [pw] × [ph] picture, placed on it: one kept a little bigger serves (the window grows
     * round its middle, inside the picture), so a pinch, whose windows change size every few frames, doesn't make a new
     * canvas (and its drawing state) each time.
     */
    private fun canvasFor(win: PixelRect, pw: Int, ph: Int): PixelCanvas {
        val key = (win.w.toLong() shl 32) or win.h.toLong()
        val c = canvases[key] ?: canvases.entries.firstOrNull { (_, c) ->
            c.width >= win.w && c.height >= win.h && c.width <= minOf(pw, win.w + SLACK) && c.height <= minOf(ph, win.h + SLACK)
        }?.value ?: run {
            if (canvases.size >= MAX_CANVASES) canvases.remove(canvases.keys.first())
            PixelCanvas(win.w, win.h).also { canvases[key] = it }
        }
        c.originX = (win.x0 - (c.width - win.w) / 2).coerceIn(0, pw - c.width)
        c.originY = (win.y0 - (c.height - win.h) / 2).coerceIn(0, ph - c.height)
        return c
    }

    /** By detail level: the render time of the pictures since the last log, and how many. */
    private val nanos = LongArray(Lens.MAX_DETAIL + 1)
    private val frames = IntArray(Lens.MAX_DETAIL + 1)

    /** Debug and qa builds: the render time at each detail, logged every 24 pictures (tag TownScene; QA reads it). */
    private fun timed(state: GameState, k: Int, cw: Int, ch: Int, took: Long) {
        if (!si.lanisce.lani.BuildConfig.QA) return
        nanos[k] += took
        if (++frames[k] < 24) return
        Log.d(
            "TownScene",
            "%s at detail %d: %.1f ms per frame (%d × %d, ground %s)".format(
                Terrain.of(state).key, k, nanos[k] / 1e6 / frames[k], cw, ch, if (VillageRenderer.groundKept) "kept" else "whole",
            ),
        )
        nanos[k] = 0L; frames[k] = 0
    }

    companion object {
        /** The camera counts as moving this long after it last moved: the closer look gets [TownFrames.margin] round the screen. */
        const val MOVING_NANOS = 400_000_000L

        /** The closer look's canvases kept (by size). */
        const val MAX_CANVASES = 3

        /** A kept canvas this many pixels bigger than a window each way still serves it. */
        const val SLACK = 64
    }
}
