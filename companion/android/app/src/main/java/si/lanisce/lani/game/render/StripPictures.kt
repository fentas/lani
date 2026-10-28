package si.lanisce.lani.game.render

import si.lanisce.lani.game.GameState

/**
 * The Home header's village ([VillageScene]), rendered on a thread of its own ([RenderThread]) with its own [renderer]:
 * asked for each tick ([ask]), never drawn on the main thread, the first picture neither; handed over in three bitmaps
 * ([PictureSlots]), never the one the screen draws ([drew]). Kept while Home is away ([Retained]), so Home composed
 * again shows its last [picture] at once (2026-10-28, docs/plans/05-built/home-return.md).
 */
internal class StripPictures(val w: Int, val h: Int, val renderer: VillageRenderer) {
    private class Ask(val state: GameState, val frame: Frame)

    private val thread = RenderThread<Ask>("VillageRender", "VillageScene", merge = { _, a -> a }) { draw(it) }
    private val canvas = PixelCanvas(w, h)
    private val slots = PictureSlots()
    private var tick = -1L
    private var state: GameState? = null

    /** Bumped on the main thread for every picture handed over: the draw phase reads it. */
    val version: Long get() = thread.version

    /** The newest picture (the last one kept, when Home comes back), or null before the first. */
    @Volatile var picture: Picture? = null
        private set
    @Volatile private var drawn: Picture? = null

    /** The thread that drew the last picture (tests: never the one that asked). */
    val drewOn: Thread? get() = thread.drewOn

    /** A picture of [frame] of [state] at clock [tick] (once a tick, or for a new state): drawn on the render thread. */
    fun ask(tick: Long, state: GameState, frame: Frame) {
        if (tick == this.tick && state === this.state) return
        this.tick = tick; this.state = state
        thread.post(Ask(state, frame))
    }

    /** The screen drew [p] (or none). */
    fun drew(p: Picture?) { drawn = p }

    /** Not kept any more ([Retained]): its render thread ends after the picture it draws. */
    fun close() = thread.close()

    private fun draw(a: Ask) {
        renderer.render(canvas, a.state, a.frame)
        picture = slots.upload(canvas, Lens(1, 0, 0, w, h), drawn, picture)
    }
}
