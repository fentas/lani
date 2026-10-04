package si.lanisce.lani.game.render

import kotlin.math.roundToInt

/**
 * 2:1 isometric projection over a [PixelCanvas].
 *
 * World units: x and y in cells (one cell is an 8x4 px diamond at [k] = 1, 8k × 4k at higher detail),
 * z in nominal pixels (k canvas pixels each). +x runs to the lower right of the screen, +y to the lower
 * left. Visible box faces are the top (lit), the +y face (screen left, mid tone) and the +x face (screen
 * right, dark).
 *
 * At k > 1 the geometry scales: faces get k times as many pixels each way, posts are k px wide and a
 * [px] dot is a k × k block, so the nominal silhouette stays the same, only crisper; texture lines stay one
 * pixel thin, which is where the extra detail shows.
 */
class Iso(val c: PixelCanvas) {
    var ox = 0f
    var oy = 0f
    /** The lens's detail level: canvas pixels per nominal pixel. */
    var k = 1

    fun sx(x: Float, y: Float): Float = ox + (x - y) * 4f * k
    fun sy(x: Float, y: Float, z: Float): Float = oy + (x + y) * 2f * k - z * k

    fun ix(x: Float, y: Float): Int = sx(x, y).roundToInt()
    fun iy(x: Float, y: Float, z: Float): Int = sy(x, y, z).roundToInt()

    /** World position of a screen point on the ground plane. */
    fun worldX(sx: Float, sy: Float): Float { val a = (sx - ox) / (4f * k); val b = (sy - oy) / (2f * k); return (a + b) / 2f }
    fun worldY(sx: Float, sy: Float): Float { val a = (sx - ox) / (4f * k); val b = (sy - oy) / (2f * k); return (b - a) / 2f }

    private fun v(x: Float, y: Float, z: Float) = c.polyAdd(sx(x, y), sy(x, y, z))

    fun quad(
        x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float, x4: Float, y4: Float, z4: Float, col: Int,
    ) {
        c.polyBegin(); v(x1, y1, z1); v(x2, y2, z2); v(x3, y3, z3); v(x4, y4, z4); c.polyFill(col)
    }

    fun tri(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, x3: Float, y3: Float, z3: Float, col: Int) {
        c.polyBegin(); v(x1, y1, z1); v(x2, y2, z2); v(x3, y3, z3); c.polyFill(col)
    }

    /** Horizontal rectangle at height z. */
    fun top(x: Float, y: Float, z: Float, w: Float, d: Float, col: Int) =
        quad(x, y, z, x + w, y, z, x + w, y + d, z, x, y + d, z, col)

    /** Vertical panel in the plane y = const (a "left" face), spanning x..x+w and z..z+h. */
    fun panelY(x: Float, y: Float, z: Float, w: Float, h: Float, col: Int) =
        quad(x, y, z, x + w, y, z, x + w, y, z + h, x, y, z + h, col)

    /** Vertical panel in the plane x = const (a "right" face), spanning y..y+d and z..z+h. */
    fun panelX(x: Float, y: Float, z: Float, d: Float, h: Float, col: Int) =
        quad(x, y, z, x, y + d, z, x, y + d, z + h, x, y, z + h, col)

    fun box(x: Float, y: Float, z: Float, w: Float, d: Float, h: Float, top: Int, left: Int, right: Int) {
        panelY(x, y + d, z, w, h, left)
        panelX(x + w, y, z, d, h, right)
        top(x, y, z + h, w, d, top)
    }

    /** A one-pixel line (texture: seams, rows, edges; it stays thin at every detail level). */
    fun line(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, col: Int) =
        c.line(ix(x1, y1), iy(x1, y1, z1), ix(x2, y2), iy(x2, y2, z2), col)

    /** A nominal pixel: a k × k block. */
    fun px(x: Float, y: Float, z: Float, col: Int) {
        if (k == 1) c.set(ix(x, y), iy(x, y, z), col) else c.block(ix(x, y), iy(x, y, z), k, col)
    }

    /** Vertical post (a nominal pixel wide) from z0 to z1. */
    fun post(x: Float, y: Float, z0: Float, z1: Float, col: Int) {
        val sx = ix(x, y)
        for (dx in 0 until k) c.vline(sx + dx, iy(x, y, z1), iy(x, y, z0), col)
    }

    /**
     * Gable roof with the ridge along x. Eaves at height [z] around the rectangle (already including the
     * overhang), ridge [rise] px higher. The +x gable end is visible.
     */
    fun gableX(x: Float, y: Float, z: Float, w: Float, d: Float, rise: Float, back: Int, front: Int, gable: Int, edge: Int) {
        val ym = y + d / 2; val zr = z + rise
        quad(x, y, z, x + w, y, z, x + w, ym, zr, x, ym, zr, back)
        tri(x + w, y, z, x + w, y + d, z, x + w, ym, zr, gable)
        quad(x, ym, zr, x + w, ym, zr, x + w, y + d, z, x, y + d, z, front)
        line(x + w, y + d, z, x + w, ym, zr, edge)
        line(x, y + d, z, x + w, y + d, z, edge)
    }

    /** Gable roof with the ridge along y. The +y gable end is visible. */
    fun gableY(x: Float, y: Float, z: Float, w: Float, d: Float, rise: Float, back: Int, front: Int, gable: Int, edge: Int) {
        val xm = x + w / 2; val zr = z + rise
        quad(x, y, z, x, y + d, z, xm, y + d, zr, xm, y, zr, back)
        tri(x, y + d, z, x + w, y + d, z, xm, y + d, zr, gable)
        quad(xm, y, zr, xm, y + d, zr, x + w, y + d, z, x + w, y, z, front)
        line(x, y + d, z, xm, y + d, zr, edge)
        line(x + w, y, z, x + w, y + d, z, edge)
    }

    /** Pyramid (spire, hip roof) over a rectangle; apex at the centre. */
    fun pyramid(x: Float, y: Float, z: Float, w: Float, d: Float, h: Float, left: Int, right: Int) {
        val ax = x + w / 2; val ay = y + d / 2; val az = z + h
        tri(x, y + d, z, x + w, y + d, z, ax, ay, az, left)
        tri(x + w, y, z, x + w, y + d, z, ax, ay, az, right)
    }
}
