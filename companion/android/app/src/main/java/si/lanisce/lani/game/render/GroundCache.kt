package si.lanisce.lani.game.render

import java.util.Arrays

/**
 * The ground's still picture, kept from frame to frame (see [VillageRenderer]'s ground pass): of all it draws, only the
 * water moves (the sea's surf and glints, the stream's, the pond's and a lake's twinkle, a torrent breaking white), and the
 * rest is the same until the land, the canvas, the lens, the age, the paths, the season, the weather or the sky's colour
 * at the horizon change ([key]). So the pass draws the ground once and keeps it with where the water moves, each such
 * pixel's two colours and what decides between them ([add]); the next frames take the picture back ([restore]) and draw
 * only the water anew ([animate]), the same pixels the pass would draw ([shade], shared with it).
 *
 * The ground pass was the frame's biggest cost (two thirds of it on a tablet's and a phone's canvas, a little more on a
 * generated land than on the classic one: the stream's two waves, the clearing's ragged rim); the sea itself cost no more
 * than the land it covers. Measured 2026-10-26 (docs/plans/05-built/coast-horizon.md).
 */
internal class GroundCache {
    /** What the picture kept was drawn for; null: none kept. */
    var key: Any? = null

    /** The first row kept, the picture's pixels and ground kinds from it to the canvas's bottom. */
    private var top = 0
    private var pixels = IntArray(0)
    private var kinds = ByteArray(0)

    /** The water that moves: its pixels, how each moves ([SURF] …), its nominal point, its two colours. */
    private var n = 0
    private var idx = IntArray(0)
    private var cls = ByteArray(0)
    private var bxs = IntArray(0)
    private var bys = IntArray(0)
    private var ons = IntArray(0)
    private var offs = IntArray(0)

    /** Whether the picture being drawn is kept ([begin]). */
    private var keeping = false

    /** How many pixels of water move. */
    val moving: Int get() = n

    /** A new picture is being drawn, to keep or ([keep] false) not: no water kept yet. */
    fun begin(keep: Boolean) { key = null; n = 0; keeping = keep }

    /** Pixel [i]'s water moves as [c] says ([shade]), at nominal ([bx], [by]), [on] or [off]. */
    fun add(i: Int, c: Byte, bx: Int, by: Int, on: Int, off: Int) {
        if (!keeping) return
        if (n == idx.size) {
            val m = maxOf(1024, n * 2)
            idx = idx.copyOf(m); cls = cls.copyOf(m); bxs = bxs.copyOf(m); bys = bys.copyOf(m); ons = ons.copyOf(m); offs = offs.copyOf(m)
        }
        idx[n] = i; cls[n] = c; bxs[n] = bx; bys[n] = by; ons[n] = on; offs[n] = off
        n++
    }

    /** Keeps [canvas]'s rows from [from] down and their [ground] kinds, as the picture drawn for [key]. */
    fun store(canvas: PixelCanvas, ground: ByteArray, from: Int, key: Any?) {
        val start = from * canvas.width
        val len = canvas.pixels.size - start
        if (len < 0) { this.key = null; return }
        if (pixels.size != len) { pixels = IntArray(len); kinds = ByteArray(len) }
        System.arraycopy(canvas.pixels, start, pixels, 0, len)
        System.arraycopy(ground, start, kinds, 0, len)
        top = from
        this.key = key
    }

    /** The kept picture back on [canvas] (its pixels, plain ground: no object's, not glowing) and its kinds in [ground]. */
    fun restore(canvas: PixelCanvas, ground: ByteArray) {
        val start = top * canvas.width
        val len = pixels.size
        System.arraycopy(pixels, 0, canvas.pixels, start, len)
        System.arraycopy(kinds, 0, ground, start, len)
        Arrays.fill(canvas.ids, start, start + len, 0)
        Arrays.fill(canvas.emissive, start, start + len, false)
    }

    /** The water as it is at the water's tick [t] ([shade]), on [canvas]. */
    fun animate(canvas: PixelCanvas, t: Int) {
        val px = canvas.pixels
        for (j in 0 until n) px[idx[j]] = shade(cls[j], bxs[j], bys[j], ons[j], offs[j], t)
    }

    companion object {
        /** Water that doesn't move (the sea's shallows, a shallow stream, ice). */
        const val STILL: Byte = 0

        /** The sea's surf at the beach: white foam that comes and goes over the shallows, row by row. */
        const val SURF: Byte = 1

        /** The sea's glints ([Sea.glint]). */
        const val GLINT: Byte = 2

        /** The twinkle of the stream, the pond and a lake (a little more often than the sea's glints). */
        const val TWINKLE: Byte = 3

        /** A torrent's stream: breaking white over its stones, else twinkling as [TWINKLE]. */
        const val TORRENT: Byte = 4

        /** How often the stream, the pond and a lake twinkle (the sea's: [Sea.GLINT]). */
        const val FRESH_GLINT = 0.1f

        /**
         * The colour of water that moves as [c] says, at nominal ([bx], [by]), at the water's tick [t] (three a second):
         * [on] (the glint, the foam) or [off]. The ground pass draws its water with it, and [animate] the frames after.
         */
        fun shade(c: Byte, bx: Int, by: Int, on: Int, off: Int, t: Int): Int = when (c) {
            SURF -> if (Noise.rnd(by, t / 2, 23) < 0.7f) on else off
            GLINT -> if (Sea.glint(bx, by, t)) on else off
            TWINKLE -> if (Sea.glint(bx, by, t, FRESH_GLINT)) on else off
            TORRENT -> if (Noise.rnd(bx, by / 2, t * 3 + 17) < 0.2f) VillageRenderer.SURF else if (Sea.glint(bx, by, t, FRESH_GLINT)) on else off
            else -> off
        }
    }
}
