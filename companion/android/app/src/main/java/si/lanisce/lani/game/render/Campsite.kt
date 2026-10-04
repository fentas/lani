package si.lanisce.lani.game.render

import si.lanisce.lani.game.Building
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.TentMove
import kotlin.math.abs

/**
 * The campsite by the pond in the woods below the village (see [Foreground]), where the tent stands once it moved out of
 * the village (game/TentMove): as the shepherd described it, on the pond's right bank, under a big spruce, near a plank
 * jetty; a ring of stones with a small fire in front of its door, a stump to sit on by the fire, the firewood stacked
 * behind it. Laid out from the pond in nominal canvas px, so it sits the same way at every age. Pure: the overlays can
 * use it without a frame.
 */
internal class Campsite(val fg: Foreground) {
    /** The ground under the tent's middle, nominal canvas px. */
    val x: Float = fg.pondX + fg.pondRx + 17f
    val y: Float = fg.pondY + 1f

    /** Where the jetty leaves the bank, and how far it reaches into the water (world cells, up and to the left). */
    val jettyX: Float = fg.pondX + fg.pondRx - 1f
    val jettyY: Float = fg.pondY + 3f
    val jettyLength = 2.6f

    /** World cells of a nominal canvas point (the fire in the middle, see [Iso]). */
    fun worldX(px: Float, py: Float): Float = ((px - fg.comp.fireX) / 4f + (py - fg.comp.fireY) / 2f) / 2f
    fun worldY(px: Float, py: Float): Float = ((py - fg.comp.fireY) / 2f - (px - fg.comp.fireX) / 4f) / 2f

    /** The tent's middle in world cells. */
    val wx: Float = worldX(x, y)
    val wy: Float = worldY(x, y)

    /** Nominal canvas px of a world point on the ground. */
    private fun canvasX(wx: Float, wy: Float) = fg.comp.fireX + (wx - wy) * 4f
    private fun canvasY(wx: Float, wy: Float) = fg.comp.fireY + (wx + wy) * 2f

    /** The big spruce the tent stands under, just behind it (world cells). */
    val spruceX: Float = wx + 0.4f
    val spruceY: Float = wy - 2.1f

    /** The fire ring in front of the tent's door, and the stump beside it (world cells). */
    val fireX: Float = wx - 0.3f
    val fireY: Float = wy + 2.7f
    val stumpX: Float = wx + 1.1f
    val stumpY: Float = wy + 3.1f

    /** The top of the tent, where a bubble points (nominal canvas px). */
    val top: CanvasPoint get() = CanvasPoint(x, y - TENT_HEIGHT - 4f)

    /** Where someone who waits at the tent stands: beside the fire, on the pond's side (world cells). */
    val visitor: FloatArray get() = floatArrayOf(fireX - 1.1f, fireY + 0.3f)

    /** The middle of the tent's body, where a tap hits it (nominal canvas px). */
    val body: CanvasPoint get() = CanvasPoint(x - 1f, y - TENT_HEIGHT / 2f)

    /**
     * Whether a tree with its trunk at world ([tx], [ty]) stands where the campsite needs room: on it, or so close in front
     * that its crown would hide the tent, or where the big spruce stands. It isn't drawn.
     */
    fun clears(tx: Float, ty: Float): Boolean {
        val px = canvasX(tx, ty); val py = canvasY(tx, ty)
        val dx = px - x; val dy = py - y
        return abs(dx) < 17f && dy > -24f && dy < 20f || abs(dx + 9f) < 9f && dy > -4f && dy < 12f
    }

    companion object {
        /** The tent sprite's height above the ground (px): [spriteHeight] of the tent, its poles and pennant. */
        const val TENT_HEIGHT = 13f

        /** The campsite on a [w] × [h] canvas, when the tent stands at the pond and the canvas has the woods; else null. */
        fun of(state: GameState, w: Int, h: Int): Campsite? =
            if (TentMove.moved(state)) Foreground.of(state, w, h)?.let(::Campsite) else null
    }
}

/**
 * Draws the [Campsite]: the tent itself (the building's sprite, damage and level included), the big spruce, the jetty,
 * the fire ring with its fire, the stump and the firewood, each in painter's order with the woods ([add]: depth and how
 * to draw); every part of it is the tent's to tap ([tag] gets each part's object id).
 */
internal class CampsitePainter(private val s: SceneCtx, private val buildings: BuildingPainter) {
    private val c get() = s.canvas
    private val i get() = s.iso
    private val env get() = s.env
    private val k get() = s.k

    fun gather(camp: Campsite, tent: Building, add: (Float, () -> Unit) -> Unit, tag: (Int) -> Unit) {
        // the big spruce behind the tent: a tree, not the tent's
        add(camp.spruceX + camp.spruceY) {
            s.newObject(Pal.OUTLINE_TREE)
            val bx = i.ix(camp.spruceX, camp.spruceY); val by = i.iy(camp.spruceX, camp.spruceY, 0f)
            shade(bx + 2f * k, by.toFloat(), 6f * k, 2f * k)
            s.trees.pine(bx, by, SPRUCE, 41)
        }
        // the jetty: planks on posts from the bank into the water, lying low (whoever stands on the bank is in front)
        val jx = camp.worldX(camp.jettyX, camp.jettyY); val jy = camp.worldY(camp.jettyX, camp.jettyY)
        add(jx + jy - 1.5f) { tag(s.newObject(Pal.OUTLINE)); jetty(jx, jy, camp.jettyLength) }
        // the firewood stacked behind the tent
        add(camp.wx + camp.wy - 1.2f) { tag(s.newObject(Pal.OUTLINE)); logs(camp.wx + 0.9f, camp.wy - 1.9f) }
        // the tent
        add(camp.wx + camp.wy + 1.2f) {
            val ox = camp.wx - 1.5f; val oy = camp.wy - 1.5f
            shade(i.sx(camp.wx + 0.1f, camp.wy + 0.1f), i.sy(camp.wx + 0.1f, camp.wy + 0.1f, 0f), 8f * k, 3f * k)
            tag(s.newObject(Pal.OUTLINE))
            buildings.draw(tent, ox, oy)
            if (tent.damaged) buildings.damage(tent, ox, oy)
        }
        // the fire ring in front of the door, a small fire in it; the stump to sit on
        add(camp.fireY + camp.fireX - 0.01f) {
            val fx = i.ix(camp.fireX, camp.fireY); val fy = i.iy(camp.fireX, camp.fireY, 0f)
            tag(s.newObject(Pal.OUTLINE))
            s.effects.hearth(fx, fy, RING, back = true)
            tag(s.newObject(0))
            val fh = s.effects.flames(fx, fy - k, if (env.dark > 0.3f) 0.4f else 0.15f, FLAME)
            tag(s.newObject(Pal.OUTLINE))
            s.effects.hearth(fx, fy, RING, back = false)
            s.smoke(fx + 0.5f * k, fy - fh - k.toFloat(), 0.35f, 9)
        }
        add(camp.stumpX + camp.stumpY) {
            tag(s.newObject(Pal.OUTLINE))
            stump(i.ix(camp.stumpX, camp.stumpY), i.iy(camp.stumpX, camp.stumpY, 0f))
        }
    }

    /** A soft shadow on the ground, an ellipse round ([cx], [cy]). */
    private fun shade(cx: Float, cy: Float, rx: Float, ry: Float) {
        for (y in (cy - ry).toInt()..(cy + ry).toInt()) for (x in (cx - rx).toInt()..(cx + rx).toInt()) {
            val dx = (x + 0.5f - cx) / rx; val dy = (y + 0.5f - cy) / ry
            if (dx * dx + dy * dy <= 1f) c.shadeGround(x, y, 0.82f)
        }
    }

    /**
     * The jetty (pomol): a deck of planks a little above the water, from the bank at ([x], [y]) [length] cells out along
     * −x (up and to the left, into the pond), on posts; a mooring post at its end. Frosted in the snow.
     */
    private fun jetty(x: Float, y: Float, length: Float) {
        val w = 0.8f; val z = 1.2f
        val x0 = x - length
        // the posts in the water, under the deck
        for (u in floatArrayOf(0.15f, length * 0.5f, length - 0.2f)) {
            i.post(x0 + u, y + w - 0.05f, -1f, z, Pal.WOOD_X)
            i.post(x0 + u, y + 0.05f, -1f, z, Pal.WOOD_X)
        }
        // the deck: its near edge, its top, the gaps between the planks
        i.panelY(x0, y + w, 0f, length, z, Pal.WOOD_D)
        i.top(x0, y, z, length, w, if (env.snow) Pal.SNOW_L else Pal.WOOD_L)
        if (!env.snow) {
            var u = 0.45f
            while (u < length) { i.line(x0 + u, y, z, x0 + u, y + w, z, Pal.WOOD_M); u += 0.45f }
        }
        i.post(x0 + 0.1f, y + 0.1f, z, z + 2.5f, Pal.WOOD_D) // the mooring post
    }

    /** Split logs stacked in a short pile (world cells), their cut ends to the right. */
    private fun logs(x: Float, y: Float) {
        val h = 3f
        i.box(x, y, 0f, 1.3f, 0.6f, h, Pal.LOG_M, Pal.LOG_L, Pal.LOG_D)
        for (zz in 0 until h.toInt()) for (j in 0 until 2) i.px(x + 1.3f, y + 0.15f + j * 0.3f, zz + 0.5f, if ((zz + j) % 2 == 0) Pal.WOOD_L else Pal.LOG_D)
        if (env.snow) i.top(x, y, h, 1.3f, 0.6f, Pal.SNOW_L)
    }

    /** A stump to sit on (canvas px, its foot): a short round of a trunk, rings on top. */
    private fun stump(bx: Int, by: Int) {
        s.sprite(bx, by) {
            c.fillRect(bx - 1, by - 2, 3, 2, Pal.LOG_M)
            c.set(bx + 1, by - 2, Pal.LOG_D); c.set(bx + 1, by - 1, Pal.LOG_D)
            c.hline(bx - 1, bx + 1, by - 3, if (env.snow) Pal.SNOW_L else Pal.WOOD_L)
            if (!env.snow) c.set(bx, by - 3, Pal.WOOD_M)
        }
    }

    private companion object {
        /** The fire ring's size, and the fire's, against the village's fire. */
        const val RING = 0.5f
        const val FLAME = 0.32f

        /** The big spruce, against the woods' trees (about 1). */
        const val SPRUCE = 1.9f
    }
}
