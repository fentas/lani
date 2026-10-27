package si.lanisce.lani.game.render

import java.util.Arrays

/**
 * The ground's still picture, kept from frame to frame in cells of the picture (see [VillageRenderer]'s ground pass): of
 * all it draws only the water moves (the sea's surf and glints, the stream's, the pond's and a lake's twinkle, a torrent
 * breaking white), and the rest stays the same until the land, the age, the paths, the season, the weather or the sky's
 * colour at the horizon change (the key the renderer gives). So the pass draws each cell of [CELL] × [CELL] px once and
 * keeps it with where its water moves, each such pixel's two colours and what decides between them ([Cell.add]); the next
 * frames copy the cells the canvas shows back ([fill]) and draw only their water anew ([GroundCache.shade], the colour
 * the pass draws it in too).
 *
 * The cells are the picture's, not the canvas's: [Lens] says which picture (its detail and its offset), and a cell is the
 * same wherever a canvas holding a window of that picture ([PixelCanvas.originX]) lies. So a pan draws only the cells
 * newly in view, a zoom within a detail level none, and going back to a detail level the cells it kept (until the
 * memory's cap, [capBytes]: the cells not shown for longest go first).
 *
 * The ground pass was the frame's biggest cost (two thirds of it on a tablet's and a phone's canvas), the whole of a pan
 * closer up while the cache was the canvas's (2026-10-26 to 27; docs/plans/05-built/zoom-frames.md).
 */
internal class GroundCache(var capBytes: Long = DEFAULT_CAP) {
    /**
     * One cell of the picture of [lens]: the canvas pixels [x0] until [x0] + [w], [y0] until [y0] + [h] (lens canvas
     * coordinates), its ground drawn for [key]: its pixels, ground kinds ([Ground]) and the water that moves (by index in
     * the cell, how it moves ([SURF] …), its two colours).
     */
    class Cell(val lens: Lens, val x0: Int, val y0: Int, val w: Int, val h: Int) {
        var key: Any? = null
        val pixels = IntArray(w * h)
        val kinds = ByteArray(w * h)
        var n = 0
            private set
        private var idx = ShortArray(0)
        private var cls = ByteArray(0)
        private var ons = IntArray(0)
        private var offs = IntArray(0)

        /** About how much memory the cell takes. */
        val bytes: Long get() = 96L + 5L * w * h + 11L * idx.size

        /** Drawn anew: no water kept. */
        fun reset() { key = null; n = 0 }

        /** The cell's pixel at canvas ([x], [y]) is water that moves as [c] says ([shade]), [on] or [off]. */
        fun add(x: Int, y: Int, c: Byte, on: Int, off: Int) {
            if (n == idx.size) {
                val m = maxOf(64, n * 2).coerceAtMost(w * h)
                idx = idx.copyOf(m); cls = cls.copyOf(m); ons = ons.copyOf(m); offs = offs.copyOf(m)
            }
            idx[n] = ((y - y0) * w + (x - x0)).toShort(); cls[n] = c; ons[n] = on; offs[n] = off
            n++
        }

        /**
         * The cell's part in canvas pixels [ax] until [bx], [ay] until [by] onto [canvas] (and its kinds into [ground], the
         * canvas's own), plain ground (no object's, not glowing), its water as it is at the water's tick [t].
         */
        fun copy(canvas: PixelCanvas, ground: ByteArray, ax: Int, ay: Int, bx: Int, by: Int, t: Int) {
            val cw = canvas.width
            val len = bx - ax
            for (y in ay until by) {
                val from = (y - y0) * w + (ax - x0)
                val to = (y - canvas.originY) * cw + (ax - canvas.originX)
                System.arraycopy(pixels, from, canvas.pixels, to, len)
                System.arraycopy(kinds, from, ground, to, len)
                Arrays.fill(canvas.ids, to, to + len, 0)
                Arrays.fill(canvas.emissive, to, to + len, false)
            }
            val px = canvas.pixels
            val nominal = lens.nominal
            for (j in 0 until n) {
                val i = idx[j].toInt()
                val x = x0 + i % w; val y = y0 + i / w
                if (x < ax || x >= bx || y < ay || y >= by) continue
                // the nominal point the pass shaded it at (see [VillageRenderer]'s ground pass)
                val bxi = if (nominal) x else kotlin.math.floor(lens.bx(x + 0.5f)).toInt()
                val byi = if (nominal) y else kotlin.math.floor(lens.by(y + 0.5f)).toInt()
                px[(y - canvas.originY) * cw + (x - canvas.originX)] = shade(cls[j], bxi, byi, ons[j], offs[j], t)
            }
        }
    }

    /** A cell's place: its picture (the lens), its column and row, and the ground's first row (where a cell may begin). */
    private data class Id(val lens: Lens, val cx: Int, val cy: Int, val top: Int)

    /** The cells kept, the one shown longest ago first. */
    private val cells = LinkedHashMap<Id, Cell>(64, 0.75f, true)

    /** About how much memory the cells take. */
    var bytes = 0L
        private set

    /** How many cells are kept. */
    val size: Int get() = cells.size

    /** How many cells the last [fill] drew anew (tests, the debug build's log). */
    var drawn = 0
        private set

    /**
     * The ground of [lens]'s picture, as drawn for [key], on [canvas]'s pixels [ax] until [bx], [ay] until [by] (canvas
     * coordinates, inside it): the cells kept for [key] copied, the others drawn first ([draw]: the cell's ground into it,
     * its water [Cell.add]ed) and kept; their water as it is at the water's tick [t]. [extent]: the picture's pixels
     * (left, top, right, bottom; a cell never reaches past them, nor above [top], the ground's first row).
     */
    fun fill(
        lens: Lens, key: Any, canvas: PixelCanvas, ground: ByteArray, ax: Int, ay: Int, bx: Int, by: Int, t: Int,
        extent: IntArray, top: Int, draw: (Cell) -> Unit,
    ) {
        drawn = 0
        if (ax >= bx || ay >= by) return
        val cx0 = Math.floorDiv(ax, CELL); val cx1 = Math.floorDiv(bx - 1, CELL)
        val cy0 = Math.floorDiv(ay, CELL); val cy1 = Math.floorDiv(by - 1, CELL)
        for (cy in cy0..cy1) for (cx in cx0..cx1) {
            val id = Id(lens, cx, cy, top)
            var cell = cells[id]
            if (cell == null) {
                val x0 = maxOf(cx * CELL, extent[0]); val x1 = minOf((cx + 1) * CELL, extent[2])
                val y0 = maxOf(cy * CELL, extent[1], top); val y1 = minOf((cy + 1) * CELL, extent[3])
                if (x1 <= x0 || y1 <= y0) continue
                cell = Cell(lens, x0, y0, x1 - x0, y1 - y0)
                cells[id] = cell
                bytes += cell.bytes
            }
            if (cell.key != key) {
                bytes -= cell.bytes
                cell.reset()
                draw(cell)
                cell.key = key
                bytes += cell.bytes
                drawn++
            }
            val x0 = maxOf(ax, cell.x0); val x1 = minOf(bx, cell.x0 + cell.w)
            val y0 = maxOf(ay, cell.y0); val y1 = minOf(by, cell.y0 + cell.h)
            if (x1 > x0 && y1 > y0) cell.copy(canvas, ground, x0, y0, x1, y1, t)
        }
        trim()
    }

    /** The cells shown longest ago go until the rest fit under [capBytes]. */
    fun trim() {
        if (bytes <= capBytes) return
        val it = cells.values.iterator()
        while (bytes > capBytes && it.hasNext()) {
            val c = it.next()
            it.remove()
            bytes -= c.bytes
        }
    }

    /** Forgets every cell. */
    fun clear() { cells.clear(); bytes = 0L }

    companion object {
        /** A cell's side in canvas pixels. */
        const val CELL = 64

        /** The cells' memory by default: every cell of a phone's closest look fits (a 360 × 800 canvas at 3 ×: about 13 MB). */
        const val DEFAULT_CAP = 24L shl 20

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
         * [on] (the glint, the foam) or [off]. The ground pass draws its water with it, and the cells' copies the frames after.
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
