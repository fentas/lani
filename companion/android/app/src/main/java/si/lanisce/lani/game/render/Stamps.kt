package si.lanisce.lani.game.render

/**
 * What a tree, a bush or a linden's crown leaves on a canvas, kept from frame to frame ([TreePainter]; 2026-10-27,
 * docs/plans/05-built/zoom-frames.md): they stand still, and their pixels are theirs alone (each written, never read or
 * blended), so a painting is the same pixels wherever and whenever it is drawn again at the same place, detail and season.
 * Its first painting goes into a scratch canvas ([make]), and what it wrote, row by row, is the stamp; the frames after
 * write the stamp's pixels instead of painting them ([Stamp.put]): with the pen's object id and glow, clipped to the
 * canvas and its [PixelCanvas.clipTop], as each of its writes would have been. The canvas's coordinates are the lens's
 * picture's, so a stamp is the same in a window of the picture closer up as in the whole of it.
 *
 * The trees were most of a frame's objects (a third of its time at rest, closer up: the crowns' blobs and their noise,
 * pixel by pixel). The stamps keep under [capPixels] (about 5 bytes each); past it they all go, and come again as drawn.
 */
internal class Stamps(var capPixels: Int = DEFAULT_CAP) {
    /** What a painting is of: the painter's [what], its place and size (raw bits), its [seed], its [look] (the detail, the season). */
    data class Key(val what: Int, val a: Int, val b: Int, val c: Int, val seed: Int, val look: Int)

    /** A painting's pixels: rows of runs (y, x0, n, offset into [cols]), and their colours. */
    class Stamp(private val runs: IntArray, private val cols: IntArray) {
        val pixels: Int get() = cols.size

        /** The pixels onto [c] with its pen's id and glow, as [PixelCanvas.set] would have put each (not zoomed). */
        fun put(c: PixelCanvas) {
            val left = c.left; val right = c.right; val top = maxOf(c.top, c.clipTop); val bottom = c.bottom
            val px = c.pixels; val ids = c.ids; val em = c.emissive
            val id = c.penId; val glow = c.penEmissive
            val w = c.width; val ox = c.originX; val oy = c.originY
            var r = 0
            while (r < runs.size) {
                val y = runs[r]; val x0 = runs[r + 1]; val n = runs[r + 2]; val off = runs[r + 3]
                r += 4
                if (y < top || y >= bottom) continue
                val a = maxOf(x0, left); val b = minOf(x0 + n, right)
                if (a >= b) continue
                var i = (y - oy) * w + (a - ox)
                var j = off + (a - x0)
                for (x in a until b) { px[i] = cols[j]; ids[i] = id; em[i] = glow; i++; j++ }
            }
        }
    }

    private val kept = HashMap<Key, Stamp>()
    private var pixels = 0
    /** Scratch canvases by size (rounded up to [SCRATCH_STEP]): a painting's bounds fill one, its ring included. */
    private val scratches = LinkedHashMap<Long, PixelCanvas>(8, 0.75f, true)

    /** How many stamps are kept, and their pixels (tests). */
    val size: Int get() = kept.size
    val keptPixels: Int get() = pixels

    operator fun get(key: Key): Stamp? = kept[key]

    /**
     * The stamp of [key]: [paint] painted into a scratch canvas holding canvas pixels [x0] until [x1], [y0] until [y1]
     * (and one more all round, to see it kept inside), what it wrote kept. Null when it wrote outside them (then it isn't
     * kept: the caller paints it as ever).
     */
    fun make(key: Key, x0: Int, y0: Int, x1: Int, y1: Int, paint: (PixelCanvas) -> Unit): Stamp? {
        if (x1 <= x0 || y1 <= y0) return null
        // the bounds and a ring round them, all of the scratch canvas: whatever lands on it outside the bounds is in the ring
        val w = (x1 - x0 + 2 + SCRATCH_STEP - 1) / SCRATCH_STEP * SCRATCH_STEP
        val h = (y1 - y0 + 2 + SCRATCH_STEP - 1) / SCRATCH_STEP * SCRATCH_STEP
        if (w * h > MAX_SCRATCH) return null
        val sk = (w.toLong() shl 32) or h.toLong()
        val sc = scratches[sk] ?: PixelCanvas(w, h).also {
            if (scratches.size >= SCRATCHES) scratches.remove(scratches.keys.first())
            scratches[sk] = it
        }
        sc.originX = x0 - 1; sc.originY = y0 - 1
        sc.ids.fill(0)
        sc.zoomOff()
        sc.clipTop = Int.MIN_VALUE
        sc.penId = MARK; sc.penEmissive = false
        paint(sc)
        val sw = w
        // its pixels, row by row; one in the ring round the scratch canvas: it may reach past it
        val runs = ArrayList<Int>(64)
        var cols = IntArray(256)
        var n = 0
        for (yy in 0 until h) {
            var x = 0
            val row = yy * sw
            while (x < w) {
                if (sc.ids[row + x] != MARK) { x++; continue }
                if (yy == 0 || yy == h - 1 || x == 0) return null
                val start = x
                while (x < w && sc.ids[row + x] == MARK) x++
                if (x == w) return null
                val len = x - start
                if (n + len > cols.size) cols = cols.copyOf(maxOf(cols.size * 2, n + len))
                System.arraycopy(sc.pixels, row + start, cols, n, len)
                runs.add(sc.originY + yy); runs.add(sc.originX + start); runs.add(len); runs.add(n)
                n += len
            }
        }
        val st = Stamp(runs.toIntArray(), cols.copyOf(n))
        if (pixels + n > capPixels) { kept.clear(); pixels = 0 }
        kept[key] = st
        pixels += n
        return st
    }

    fun clear() { kept.clear(); pixels = 0 }

    companion object {
        /** The stamps' pixels by default: every tree of a phone's closest look, about 8 MB. */
        const val DEFAULT_CAP = 1_600_000

        /** A painting bigger than this (px of its bounds) is painted as ever. */
        const val MAX_SCRATCH = 400_000

        /** Scratch canvases' sizes are multiples of this, and this many are kept. */
        private const val SCRATCH_STEP = 16
        private const val SCRATCHES = 12

        /** The scratch canvas's pen while a stamp is made. */
        private const val MARK = 1

        /**
         * Whether the trees are stamped ([TreePainter]); off, every frame paints them as before 2026-10-27 (the tests compare
         * the two; QA's hook "render:full" turns both this and the ground's cells off).
         */
        @Volatile @JvmStatic var on = true
    }
}
