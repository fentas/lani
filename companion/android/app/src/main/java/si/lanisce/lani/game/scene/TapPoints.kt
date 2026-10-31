package si.lanisce.lani.game.scene

/**
 * Where to tap a thing or a person of a scene's picture so the tap lands on it (QA's taps, logged by a debug build: I spy's
 * things, a tap turn's places): a pixel it painted itself, not the middle of its hit area, which may be another thing's
 * (the stream under Il ponte's deck, Aldo's sign standing in Il prato delle api's meadow). Pure.
 */
object TapPoints {
    /** How far round a pixel its neighbours count ([of]): a tap a little off still lands on the thing. */
    private const val R = 2

    /**
     * A canvas pixel of [target] (in the picture's pixels at detail 1) that a tap there gets: one the painter says [target]
     * painted ([owner], to the pixel, as `ScenePainter.targetAt` with no slop), the one with the most of its neighbours
     * within [R] px painted by it too (the inside of the thing, away from its edges), of those the nearest to the middle of
     * its hit area. With no pixel of its own (the stars, a few specks), a pixel nothing painted where [target]'s area is the
     * front-most of [hits] (the last), as a tap falls back to the areas there. Null: [target] isn't drawn, or no pixel of its
     * area is its. [w] × [h]: the canvas.
     */
    fun of(target: SceneTarget, hits: List<SceneHit>, w: Int, h: Int, owner: (Int, Int) -> SceneTarget?): Pair<Int, Int>? {
        val hit = hits.lastOrNull { it.target == target } ?: return null
        val l = hit.left.coerceAtLeast(0); val t = hit.top.coerceAtLeast(0)
        val r = hit.right.coerceAtMost(w); val b = hit.bottom.coerceAtMost(h)
        if (r <= l || b <= t) return null
        val bw = r - l; val bh = b - t
        val owners = arrayOfNulls<SceneTarget>(bw * bh)
        for (y in t until b) for (x in l until r) owners[(y - t) * bw + (x - l)] = owner(x, y)
        val own = BooleanArray(bw * bh) { owners[it] == target }
        if (own.none { it }) {
            // nothing of its own: the pixels nothing painted where its area is the front-most, as SceneAnchors.at
            for (y in t until b) for (x in l until r) {
                val i = (y - t) * bw + (x - l)
                if (owners[i] == null && hits.lastOrNull { x >= it.left && x < it.right && y >= it.top && y < it.bottom }?.target == target) own[i] = true
            }
        }
        if (own.none { it }) return null
        val cx = (hit.left + hit.right - 1) / 2f; val cy = (hit.top + hit.bottom - 1) / 2f
        var best: Pair<Int, Int>? = null
        var bestN = -1; var bestD = Float.MAX_VALUE
        for (y in 0 until bh) for (x in 0 until bw) {
            if (!own[y * bw + x]) continue
            var n = 0
            for (dy in -R..R) for (dx in -R..R) {
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until bw && yy in 0 until bh && own[yy * bw + xx]) n++
            }
            val d = (l + x - cx) * (l + x - cx) + (t + y - cy) * (t + y - cy)
            if (n > bestN || (n == bestN && d < bestD)) { best = (l + x) to (t + y); bestN = n; bestD = d }
        }
        return best
    }
}
