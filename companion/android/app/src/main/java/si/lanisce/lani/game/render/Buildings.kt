package si.lanisce.lani.game.render

import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Sprite height of [t] above its plot (px): hit rectangles, the construction reveal, bubble anchors. */
internal fun spriteHeight(t: BuildingType): Float = when (t) {
    BuildingType.TENT -> 11f
    BuildingType.FIELD -> 9f
    BuildingType.WELL -> 16f
    BuildingType.HUT -> 14f
    BuildingType.KOZOLEC -> 19f
    BuildingType.BEEHIVE -> 11f
    BuildingType.PALISADE -> 14f
    BuildingType.WATCHTOWER -> 37f
    BuildingType.SMITHY -> 20f
    BuildingType.LIPA -> 32f
    BuildingType.HOUSE -> 22f
    BuildingType.CHURCH -> 38f
    BuildingType.SCHOOL -> 26f
    BuildingType.MARKET -> 10f
}

/**
 * Voxel-ish sprites for every [BuildingType]. Each building sits on a 3x3-cell plot whose top corner is
 * (x, y) in world cells. All drawing goes through [Iso], so parts are ordered back to front by hand.
 *
 * At the nominal detail the sprites are what they always were. A closer look ([fine], k ≥ 2; [finest], k = 3)
 * adds what the extra pixels allow: rows of tiles and shingles, mortar between the stones, window frames with
 * a mullion and curtains, planked doors with a handle, flower boxes, fence slats.
 */
internal class BuildingPainter(private val s: SceneCtx) {
    private val i get() = s.iso
    private val c get() = s.canvas
    private val env get() = s.env
    private val k get() = s.k
    private val fine get() = s.k >= 2
    private val finest get() = s.k >= 3

    /** Sprite height above the plot (px), for hit rectangles and the construction reveal. */
    fun height(t: BuildingType): Float = spriteHeight(t)

    fun draw(b: Building, x: Float, y: Float) {
        current = b
        when (b.type) {
            BuildingType.TENT -> tent(b, x, y)
            BuildingType.FIELD -> field(b, x, y)
            BuildingType.WELL -> well(b, x, y)
            BuildingType.HUT -> hut(b, x, y)
            BuildingType.KOZOLEC -> kozolec(b, x, y)
            BuildingType.BEEHIVE -> beehive(b, x, y)
            BuildingType.PALISADE -> Unit // no plot: it stands round the clearing (see PalisadePainter)
            BuildingType.WATCHTOWER -> watchtower(b, x, y)
            BuildingType.SMITHY -> smithy(b, x, y)
            BuildingType.LIPA -> lipa(b, x, y)
            BuildingType.HOUSE -> house(b, x, y)
            BuildingType.CHURCH -> church(b, x, y)
            BuildingType.SCHOOL -> school(b, x, y)
            BuildingType.MARKET -> market(b, x, y)
        }
        if (b.level >= 3) banner(b.level, x, y)
    }

    /**
     * Levels 3 to 5 (the upgrades after the first): a pennant on a pole at the plot's left corner,
     * taller each level, blue at 3, red at 4 and gold with a finial at 5. (Plain colours: a tricolour
     * this small would read as the wrong country's flag.)
     */
    private fun banner(level: Int, x: Float, y: Float) = pennant(level, x + 0.3f, y + 2.7f, 0f)

    /** The pennant of [banner] on a pole standing at ([px], [py]) from height [z0], [pole] px tall at level 3 (a gate post's is shorter). */
    internal fun pennant(level: Int, px: Float, py: Float, z0: Float, pole: Float = 9f) {
        val h = z0 + pole + (level - 3) * 2f
        i.post(px, py, z0, h, Pal.WOOD_D)
        val sx = i.ix(px, py) + k; val sy = i.iy(px, py, h)
        val flutter = (s.time * 3).toInt() and 1
        val (light, dark) = when (level) {
            3 -> Pal.FLAG_BLUE to Col.scale(Pal.FLAG_BLUE, 0.7f)
            4 -> Pal.FLAG_RED to Col.scale(Pal.FLAG_RED, 0.7f)
            else -> Pal.GOLD_L to Pal.GOLD
        }
        // A triangle pointing away from the pole, its tip fluttering.
        s.sprite(sx, sy) {
            for (row in 0 until 3) {
                val len = 4 - row
                for (j in 0 until len) c.set(sx + j, sy + row + if (j == len - 1 && j > 0) flutter else 0, if (row == 0) light else dark)
            }
            if (level >= 5) c.set(sx - 1, sy - 1, Pal.GOLD_L)
        }
    }

    // ---------------------------------------------------------------- helpers

    internal class Roof(val back: Int, val front: Int, val edge: Int, val rows: Boolean = true, val stagger: Boolean = false)

    internal fun tiles() = if (env.snow) Roof(Pal.SNOW_L, Pal.SNOW_M, Pal.ROOF_D, rows = false) else Roof(Pal.ROOF_L, Pal.ROOF_D, Col.hex(0x55200F), stagger = true)
    internal fun tilesSideLit() = if (env.snow) Roof(Pal.SNOW_L, Pal.SNOW_M, Pal.ROOF_D, rows = false) else Roof(Pal.ROOF_L, Pal.ROOF_M, Col.hex(0x55200F), stagger = true)
    internal fun shingles() = if (env.snow) Roof(Pal.SNOW_L, Pal.SNOW_M, Pal.SHINGLE_D, rows = false) else Roof(Pal.SHINGLE_L, Pal.SHINGLE_M, Pal.SHINGLE_D, stagger = true)
    internal fun slate() = if (env.snow) Roof(Pal.SNOW_L, Pal.SNOW_M, Pal.SLATE_D, rows = false) else Roof(Pal.SLATE_L, Pal.SLATE_M, Pal.SLATE_D)

    /** The building being drawn ([draw]): its windows are dark once everyone in it is asleep ([SceneCtx.darkWindows]). */
    private var current: Building? = null

    private val lit get() = env.windows > 0.35f && current?.id?.let { it in s.darkWindows } != true

    /** A gable roof with the ridge along x, and at a closer look its rows of tiles or shingles. */
    internal fun roofX(x: Float, y: Float, z: Float, w: Float, d: Float, rise: Float, r: Roof, gable: Int) {
        i.gableX(x, y, z, w, d, rise, r.back, r.front, gable, r.edge)
        if (!fine || !r.rows) return
        val n = (rise * k / 3f).roundToInt().coerceAtLeast(2)
        val backRow = Col.scale(r.back, 0.86f); val frontRow = Col.scale(r.front, 0.84f)
        for (j in 1 until n) {
            val t = j / n.toFloat(); val zz = z + t * rise
            val yb = y + t * d / 2; val yf = y + d - t * d / 2
            i.line(x, yb, zz, x + w, yb, zz, backRow)
            i.line(x, yf, zz, x + w, yf, zz, frontRow)
            if (r.stagger && finest) {
                var u = 0.12f + (j % 2) * 0.16f
                while (u < w) { c.set(i.ix(x + u, yb), i.iy(x + u, yb, zz) + 1, backRow); c.set(i.ix(x + u, yf), i.iy(x + u, yf, zz) + 1, frontRow); u += 0.32f }
            }
        }
    }

    /** A gable roof with the ridge along y, and at a closer look its rows of tiles or shingles. */
    internal fun roofY(x: Float, y: Float, z: Float, w: Float, d: Float, rise: Float, r: Roof, gable: Int) {
        i.gableY(x, y, z, w, d, rise, r.back, r.front, gable, r.edge)
        if (!fine || !r.rows) return
        val n = (rise * k / 3f).roundToInt().coerceAtLeast(2)
        val backRow = Col.scale(r.back, 0.86f); val frontRow = Col.scale(r.front, 0.84f)
        for (j in 1 until n) {
            val t = j / n.toFloat(); val zz = z + t * rise
            val xb = x + t * w / 2; val xf = x + w - t * w / 2
            i.line(xb, y, zz, xb, y + d, zz, backRow)
            i.line(xf, y, zz, xf, y + d, zz, frontRow)
            if (r.stagger && finest) {
                var u = 0.12f + (j % 2) * 0.16f
                while (u < d) { c.set(i.ix(xb, y + u), i.iy(xb, y + u, zz) + 1, backRow); c.set(i.ix(xf, y + u), i.iy(xf, y + u, zz) + 1, frontRow); u += 0.32f }
            }
        }
    }

    /** Mortar between the stones of a stone box, at a closer look: courses on both faces, staggered joints at the finest. */
    internal fun mortar(x: Float, y: Float, z: Float, w: Float, d: Float, h: Float, left: Int, right: Int) {
        if (!fine) return
        var zz = 1f
        var row = 0
        while (zz < h) {
            i.line(x, y + d, z + zz, x + w, y + d, z + zz, left)
            i.line(x + w, y, z + zz, x + w, y + d, z + zz, right)
            if (finest) {
                var u = 0.2f + (row % 2) * 0.25f
                while (u < w) { i.line(x + u, y + d, z + zz - 1f, x + u, y + d, z + zz - 0.5f, left); u += 0.5f }
                u = 0.2f + (row % 2) * 0.25f
                while (u < d) { i.line(x + w, y + u, z + zz - 1f, x + w, y + u, z + zz - 0.5f, right); u += 0.5f }
            }
            zz += 1f; row++
        }
    }

    /** A door on a +y face from height [z]: planks at a closer look, and a handle [handleU] cells in at [handleZ] (none when negative). */
    private fun doorY(x: Float, y: Float, z: Float, w: Float, h: Float, handleU: Float = -1f, handleZ: Float = 0f) {
        i.panelY(x, y, z, w, h, Pal.DOOR)
        if (fine) {
            val plank = Col.scale(Pal.DOOR, 0.78f)
            var u = w / 3f
            while (u < w - 0.05f) { i.line(x + u, y, z, x + u, y, z + h, plank); u += w / 3f }
            i.line(x, y, z + h - 0.5f, x + w, y, z + h - 0.5f, Col.scale(Pal.DOOR, 1.25f))
            if (handleU >= 0f) c.block(i.ix(x + handleU, y), i.iy(x + handleU, y, handleZ), k - 1, Pal.GOLD)
        } else if (handleU >= 0f) i.px(x + handleU, y, handleZ, Pal.GOLD)
    }

    /** A door on a +x face from height [z]: planks at a closer look, and a handle [handleU] cells in at [handleZ] (none when negative). */
    private fun doorX(x: Float, y: Float, z: Float, d: Float, h: Float, handleU: Float = -1f, handleZ: Float = 0f) {
        i.panelX(x, y, z, d, h, Pal.DOOR)
        if (fine) {
            val plank = Col.scale(Pal.DOOR, 0.78f)
            var u = d / 3f
            while (u < d - 0.05f) { i.line(x, y + u, z, x, y + u, z + h, plank); u += d / 3f }
            i.line(x, y, z + h - 0.5f, x, y + d, z + h - 0.5f, Col.scale(Pal.DOOR, 1.25f))
            if (handleU >= 0f) c.block(i.ix(x, y + handleU), i.iy(x, y + handleU, handleZ), k - 1, Pal.GOLD)
        } else if (handleU >= 0f) i.px(x, y + handleU, handleZ, Pal.GOLD)
    }

    /** Window on a +y (left) face. */
    private fun winY(x: Float, y: Float, z: Float, w: Float = 0.5f, h: Float = 3f, shutters: Boolean = false) {
        if (shutters) { i.panelY(x - 0.25f, y, z, 0.25f, h, Pal.SHUTTER); i.panelY(x + w, y, z, 0.25f, h, Pal.SHUTTER) }
        if (fine) i.panelY(x - FRAME, y, z - 1f / k, w + 2 * FRAME, h + 2f / k, Pal.WOOD_X)
        if (lit) {
            glow { i.panelY(x, y, z, w, h, Pal.WINDOW_LIT); i.px(x, y, z + h - 1, Pal.WINDOW_LIT_HI) }
            s.light(i.sx(x + w / 2, y), i.sy(x + w / 2, y, z + h / 2) + 3 * k, 11f, 0.55f * env.windows)
        } else {
            i.panelY(x, y, z, w, h, Pal.GLASS); i.px(x, y, z + h - 1, Pal.GLASS_HI)
            if (finest) { i.panelY(x + 0.02f, y, z, w * 0.2f, h, Pal.CANVAS_L); i.panelY(x + w * 0.78f, y, z, w * 0.2f, h, Pal.CANVAS_L) }
        }
        if (fine) {
            i.line(x + w / 2, y, z, x + w / 2, y, z + h, Pal.WOOD_X)
            if (finest) i.line(x, y, z + h / 2, x + w, y, z + h / 2, Pal.WOOD_X)
        }
    }

    /** Window on a +x (right) face. */
    private fun winX(x: Float, y: Float, z: Float, d: Float = 0.5f, h: Float = 3f, shutters: Boolean = false) {
        if (shutters) { i.panelX(x, y - 0.25f, z, 0.25f, h, Pal.SHUTTER); i.panelX(x, y + d, z, 0.25f, h, Pal.SHUTTER) }
        if (fine) i.panelX(x, y - FRAME, z - 1f / k, d + 2 * FRAME, h + 2f / k, Pal.WOOD_X)
        if (lit) {
            glow { i.panelX(x, y, z, d, h, Pal.WINDOW_LIT); i.px(x, y, z + h - 1, Pal.WINDOW_LIT_HI) }
            s.light(i.sx(x, y + d / 2), i.sy(x, y + d / 2, z + h / 2) + 3 * k, 11f, 0.55f * env.windows)
        } else {
            i.panelX(x, y, z, d, h, Col.scale(Pal.GLASS, 0.85f)); i.px(x, y + d - 0.1f, z + h - 1, Pal.GLASS_HI)
            if (finest) { i.panelX(x, y + 0.02f, z, d * 0.2f, h, Pal.CANVAS_M); i.panelX(x, y + d * 0.78f, z, d * 0.2f, h, Pal.CANVAS_M) }
        }
        if (fine) {
            i.line(x, y + d / 2, z, x, y + d / 2, z + h, Pal.WOOD_X)
            if (finest) i.line(x, y, z + h / 2, x, y + d, z + h / 2, Pal.WOOD_X)
        }
    }

    /** A flower box under a window on a +y face (a closer look, in the warm months). */
    private fun flowerBoxY(x: Float, y: Float, z: Float) {
        if (!fine || env.snow || env.month !in 5..10) return
        i.box(x - 0.05f, y, z - 0.9f, 0.6f, 0.15f, 0.7f, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_D)
        for ((j, u) in floatArrayOf(0.02f, 0.2f, 0.38f).withIndex()) {
            i.px(x + u, y + 0.05f, z - 0.1f, if (j == 1) Pal.LEAF else Pal.GERANIUM)
            if (finest) c.set(i.ix(x + u + 0.08f, y + 0.05f), i.iy(x + u + 0.08f, y + 0.05f, z + 0.1f), if (j == 1) Pal.GERANIUM_D else Pal.LEAF)
        }
    }

    private inline fun glow(block: () -> Unit) {
        val was = c.penEmissive; c.penEmissive = true; block(); c.penEmissive = was
    }

    /** Scattered darker "stones" on the visible faces of a box. */
    private fun speckle(x: Float, y: Float, z: Float, w: Float, d: Float, h: Float, seed: Int, cl: Int, cr: Int) {
        val n = ((w + d) * h / 3).toInt()
        for (j in 0 until n) {
            val u = Noise.rnd(seed, j, 1); val v = Noise.rnd(seed, j, 2)
            if (j % 2 == 0) i.px(x + u * w, y + d, z + 1 + v * (h - 2), cl) else i.px(x + w, y + u * d, z + 1 + v * (h - 2), cr)
        }
    }

    private fun chimney(x: Float, y: Float, z: Float, h: Float, smoke: Boolean) {
        i.box(x, y, z, 0.5f, 0.5f, h, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        i.top(x, y, z + h, 0.5f, 0.5f, Pal.STONE_X)
        if (fine) { i.line(x, y + 0.5f, z + h - 1f, x + 0.5f, y + 0.5f, z + h - 1f, Pal.STONE_D); i.line(x + 0.5f, y, z + h - 1f, x + 0.5f, y + 0.5f, z + h - 1f, Pal.STONE_X) }
        if (smoke) s.smoke(i.sx(x + 0.25f, y + 0.25f), i.sy(x + 0.25f, y + 0.25f, z + h), 0.6f, x.toInt() * 31 + y.toInt())
    }

    // ---------------------------------------------------------------- buildings

    private fun tent(b: Building, x: Float, y: Float) {
        // A-frame with its door facing the viewer (+y); ridge along y
        val x0 = x + 0.7f; val y0 = y + 0.4f; val w = 1.6f; val d = 2.2f; val rise = 10f
        val accent = Pal.PAINTED[(Noise.hash(b.plot, 7) ushr 3) % 4]
        val back = if (env.snow) Pal.SNOW_L else Pal.CANVAS_L
        val front = if (env.snow) Pal.SNOW_D else Pal.CANVAS_D
        i.line(x0 + w / 2, y0 - 0.1f, rise, x0 + w / 2, y0 - 0.9f, 0f, Pal.WOOD_D)
        i.gableY(x0, y0, 0f, w, d, rise, back, front, Pal.CANVAS_M, Col.hex(0x8C7A50))
        // seams on the sunny slope, accent hem
        i.line(x0 + 0.4f, y0 + 0.3f, 5f, x0 + 0.4f, y0 + d, 5f, Pal.CANVAS_M)
        if (fine && !env.snow) { i.line(x0 + 0.2f, y0 + 0.3f, 2.5f, x0 + 0.2f, y0 + d, 2.5f, Pal.CANVAS_M); i.line(x0 + 0.6f, y0 + 0.3f, 7.5f, x0 + 0.6f, y0 + d, 7.5f, Pal.CANVAS_M) }
        i.line(x0, y0, 0.5f, x0, y0 + d, 0.5f, accent)
        i.line(x0 + w, y0, 0.5f, x0 + w, y0 + d, 0.5f, Col.scale(accent, 0.75f))
        // door flap on the gable
        val cx = x0 + w / 2
        if (lit) {
            glow { i.tri(cx - 0.4f, y0 + d, 0f, cx + 0.4f, y0 + d, 0f, cx, y0 + d, 6.5f, Pal.WINDOW_LIT) }
            s.light(i.sx(cx, y0 + d), i.sy(cx, y0 + d, 2f), 12f, 0.55f * env.windows)
        } else i.tri(cx - 0.4f, y0 + d, 0f, cx + 0.4f, y0 + d, 0f, cx, y0 + d, 6.5f, Pal.DOOR)
        i.line(cx, y0 + d, 6.5f, cx + 0.45f, y0 + d, 0f, Pal.CANVAS_L)
        i.line(x0, y0 + d, 0f, cx, y0 + d, rise, Col.hex(0x8C7A50))
        // pole tips + guy rope
        i.post(cx, y0 + d, rise, rise + 2.5f, Pal.WOOD_D)
        i.post(cx, y0, rise, rise + 2.5f, Pal.WOOD_D)
        i.line(cx, y0 + d, rise + 1, cx, y0 + d + 0.9f, 0f, Pal.WOOD_D)
        if (fine) i.px(cx, y0 + d + 0.9f, 0f, Pal.WOOD_X) // the peg
        if (b.level >= 2) { i.px(cx, y0 + d, rise + 3, Pal.FLAG_RED); i.px(cx + 0.25f, y0 + d - 0.25f, rise + 3, Pal.FLAG_RED) }
    }

    private fun field(b: Building, x: Float, y: Float) {
        val x0 = x + 0.25f; val y0 = y + 0.25f
        val snow = env.snow
        i.top(x0, y0, 0f, 2.5f, 2.5f, if (snow) Pal.SNOW_M else Pal.SOIL_M)
        for (j in 0..4) {
            val yy = y0 + 0.25f + j * 0.5f
            i.line(x0 + 0.1f, yy, 0f, x0 + 2.4f, yy, 0f, if (snow) Pal.SNOW_D else Pal.SOIL_D)
            i.line(x0 + 0.1f, yy + 0.25f, 0f, x0 + 2.4f, yy + 0.25f, 0f, if (snow) Pal.SNOW_L else Pal.SOIL_L)
        }
        if (b.level >= 2) fence(x0, y0)
        // scarecrow in the back corner
        scarecrow(x0 + 2.1f, y0 + 0.35f)
        val m = env.month
        // the rows are harvested (cut to stubble, the crop in sheaves) after a food run today
        val cut = s.harvested && !snow && m in 5..10
        for (j in 0..4) {
            val yy = y0 + 0.4f + j * 0.5f
            var xx = x0 + 0.2f
            var n = 0
            while (xx < x0 + 2.4f) {
                val r = Noise.rnd(b.plot, j, n)
                when {
                    snow -> if (r < 0.15f) i.px(xx, yy, 1f, Pal.SOIL_D)
                    cut -> if (n % 2 == 0) i.px(xx, yy, 1f, if (r < 0.5f) Pal.HAY_D else Pal.HAY_M)
                    m in 3..4 -> if (n % 2 == 0) i.px(xx, yy, 1f, if (r < 0.5f) Pal.LEAF else Col.hex(0x6DBF4A))
                    m in 5..6 -> { i.post(xx, yy, 1f, 3f, Pal.LEAF); i.px(xx, yy, 3f, Col.hex(0x8FD05A)) }
                    m in 7..8 -> { i.post(xx, yy, 1f, 3f, Pal.HAY_M); i.px(xx, yy, 4f, Pal.HAY_L) }
                    m == 9 -> if (xx < x0 + 1.3f) { i.post(xx, yy, 1f, 3f, Pal.HAY_M); i.px(xx, yy, 4f, if (r < 0.3f) Pal.GOLD_L else Pal.HAY_L) }
                    else if (r < 0.5f) i.px(xx, yy, 1f, Pal.HAY_D)
                    m == 10 -> { if (r < 0.5f) i.px(xx, yy, 1f, Pal.HAY_D); if (r > 0.93f) pumpkin(xx, yy) }
                    else -> if (r < 0.2f) i.px(xx, yy, 1f, Pal.HAY_D)
                }
                xx += 0.25f; n++
            }
        }
        if (cut) { sheaf(x0 + 0.7f, y0 + 0.9f); sheaf(x0 + 1.7f, y0 + 1.3f); sheaf(x0 + 1.1f, y0 + 2.0f) }
        else if (m in 8..10) { sheaf(x0 + 1.7f, y0 + 1.3f); sheaf(x0 + 2.1f, y0 + 2.0f) }
    }

    private fun pumpkin(x: Float, y: Float) {
        i.px(x, y, 1f, Col.hex(0xE07A1E)); i.px(x + 0.25f, y, 1f, Col.hex(0xC0601A)); i.px(x, y, 2f, Col.hex(0xF09A3A))
    }

    private fun sheaf(x: Float, y: Float) {
        val sx = i.ix(x, y); val sy = i.iy(x, y, 0f)
        s.sprite(sx, sy) {
            c.fillRect(sx - 1, sy - 5, 3, 5, Pal.HAY_M)
            c.set(sx - 1, sy - 5, Pal.HAY_L); c.set(sx, sy - 6, Pal.HAY_L); c.set(sx + 1, sy - 1, Pal.HAY_D); c.set(sx + 1, sy - 3, Pal.HAY_D)
            c.hline(sx - 1, sx + 1, sy - 3, Pal.WOOD_D)
        }
    }

    private fun scarecrow(x: Float, y: Float) {
        val sx = i.ix(x, y); val sy = i.iy(x, y, 0f)
        s.sprite(sx, sy) {
            c.vline(sx, sy - 9, sy, Pal.WOOD_D)
            c.hline(sx - 3, sx + 3, sy - 7, Pal.WOOD_D)
            c.fillRect(sx - 1, sy - 7, 3, 3, Col.hex(0xB0402E))
            c.set(sx - 3, sy - 6, Pal.HAY_M); c.set(sx + 3, sy - 6, Pal.HAY_M)
            c.fillRect(sx - 1, sy - 10, 2, 2, Pal.HAY_L)
            c.hline(sx - 2, sx + 1, sy - 11, Pal.WOOD_X); c.hline(sx - 1, sx, sy - 12, Pal.WOOD_X)
        }
        if (finest) { c.set(sx - 1, sy - 10 * k + 1, Col.hex(0x2A1D1A)); c.set(sx + 1, sy - 10 * k + 1, Col.hex(0x2A1D1A)) } // button eyes
    }

    private fun fence(x0: Float, y0: Float) {
        for (j in 0..5) i.post(x0 + j * 0.5f, y0 + 2.5f, 0f, 3f, Pal.WOOD_D)
        for (j in 0..5) i.post(x0 + 2.5f, y0 + j * 0.5f, 0f, 3f, Pal.WOOD_D)
        i.line(x0, y0 + 2.5f, 2f, x0 + 2.5f, y0 + 2.5f, 2f, Pal.WOOD_L)
        i.line(x0 + 2.5f, y0, 2f, x0 + 2.5f, y0 + 2.5f, 2f, Pal.WOOD_M)
        if (fine) {
            // slats between the posts, and a lower rail
            for (j in 0 until 5) { i.line(x0 + j * 0.5f + 0.25f, y0 + 2.5f, 0.4f, x0 + j * 0.5f + 0.25f, y0 + 2.5f, 2.6f, Pal.WOOD_M); i.line(x0 + 2.5f, y0 + j * 0.5f + 0.25f, 0.4f, x0 + 2.5f, y0 + j * 0.5f + 0.25f, 2.6f, Pal.WOOD_D) }
            i.line(x0, y0 + 2.5f, 0.8f, x0 + 2.5f, y0 + 2.5f, 0.8f, Pal.WOOD_L)
            i.line(x0 + 2.5f, y0, 0.8f, x0 + 2.5f, y0 + 2.5f, 0.8f, Pal.WOOD_M)
        }
    }

    private fun well(b: Building, x: Float, y: Float) {
        val x0 = x + 0.9f; val y0 = y + 0.9f
        if (b.level >= 2 && !env.snow) { flowers(x + 0.6f, y + 2.4f, b.plot); flowers(x + 2.4f, y + 0.8f, b.plot + 1) }
        i.box(x0, y0, 0f, 1.2f, 1.2f, 4f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        i.line(x0, y0 + 1.2f, 2f, x0 + 1.2f, y0 + 1.2f, 2f, Pal.STONE_D)
        i.line(x0 + 1.2f, y0, 2f, x0 + 1.2f, y0 + 1.2f, 2f, Pal.STONE_X)
        speckle(x0, y0, 0f, 1.2f, 1.2f, 4f, b.plot * 13, Pal.STONE_D, Pal.STONE_X)
        mortar(x0, y0, 0f, 1.2f, 1.2f, 4f, Pal.STONE_D, Pal.STONE_X)
        i.top(x0 + 0.2f, y0 + 0.2f, 4f, 0.8f, 0.8f, if (env.snow) Pal.ICE_M else Pal.WATER_D)
        // back post, windlass, rope + bucket, front post, roof
        i.post(x0 + 0.1f, y0 + 0.6f, 4f, 11f, Pal.WOOD_D)
        i.line(x0 + 0.1f, y0 + 0.6f, 9f, x0 + 1.1f, y0 + 0.6f, 9f, Pal.WOOD_M)
        i.post(x0 + 0.6f, y0 + 0.6f, 6f, 9f, Pal.CANVAS_M)
        i.box(x0 + 0.45f, y0 + 0.45f, 4.5f, 0.35f, 0.35f, 2f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        i.post(x0 + 1.1f, y0 + 0.6f, 4f, 11f, Pal.WOOD_D)
        roofX(x0 - 0.35f, y0 + 0.05f, 10.5f, 1.9f, 1.1f, 4.5f, tiles(), Pal.WOOD_D)
    }

    private fun flowers(x: Float, y: Float, seed: Int) {
        for (j in 0..4) {
            val u = Noise.rnd(seed, j, 3) - 0.5f; val v = Noise.rnd(seed, j, 4) - 0.5f
            i.px(x + u, y + v, 1f, Pal.LEAF)
            i.px(x + u, y + v, 2f, Pal.PAINTED[(Noise.hash(seed, j) ushr 4) % 6])
        }
    }

    private fun hut(b: Building, x: Float, y: Float) {
        val x0 = x + 0.5f; val y0 = y + 0.5f
        i.box(x0, y0, 0f, 2f, 2f, 7f, Pal.LOG_L, Pal.LOG_M, Pal.LOG_D)
        for (z in intArrayOf(2, 4, 6)) {
            i.line(x0, y0 + 2f, z.toFloat(), x0 + 2f, y0 + 2f, z.toFloat(), Pal.LOG_D)
            i.line(x0 + 2f, y0, z.toFloat(), x0 + 2f, y0 + 2f, z.toFloat(), Pal.WOOD_X)
            i.px(x0 + 2.15f, y0 + 2.15f, z.toFloat() - 0.5f, Pal.LOG_L)
        }
        if (fine) for (z in intArrayOf(1, 3, 5)) { // the round of each log catching the light
            i.line(x0, y0 + 2f, z + 0.5f, x0 + 2f, y0 + 2f, z + 0.5f, Pal.LOG_L)
            i.line(x0 + 2f, y0, z + 0.5f, x0 + 2f, y0 + 2f, z + 0.5f, Col.mix(Pal.LOG_D, Pal.LOG_M, 0.5f))
        }
        doorY(x0 + 0.7f, y0 + 2f, 0f, 0.6f, 5f, handleU = 0.5f, handleZ = 2.5f)
        winX(x0 + 2f, y0 + 0.7f, 3f, 0.5f, 2f)
        roofX(x0 - 0.3f, y0 - 0.4f, 6.5f, 2.6f, 2.8f, 6f, shingles(), Pal.LOG_D)
        if (!env.snow) {
            i.line(x0 - 0.3f, y0 + 1.9f, 8.5f, x0 + 2.3f, y0 + 1.9f, 8.5f, Pal.SHINGLE_D)
            i.line(x0 - 0.3f, y0 + 1.6f, 10.5f, x0 + 2.3f, y0 + 1.6f, 10.5f, Pal.SHINGLE_D)
        }
        if (b.level >= 2) chimney(x0 + 0.3f, y0 + 1.0f, 9f, 5f, true)
    }

    private fun kozolec(b: Building, x: Float, y: Float) {
        // "toplar": two wooden ladders under one roof; we look through the front ladder at the shaded back one
        val x0 = x + 0.25f; val yb = y + 0.75f; val yf = y + 2.25f; val top = 14f
        val hay = env.month in 6..11
        val interior = Col.hex(0x3A2A1E)
        i.panelY(x0, yb, 0.5f, 2.5f, top - 1, interior)
        for (z in 2..12 step 2) i.line(x0, yb, z.toFloat(), x0 + 2.5f, yb, z.toFloat(), Pal.WOOD_M)
        if (hay) for (j in 0 until 10) {
            val u = Noise.rnd(b.plot, j, 3) * 2.4f; val v = 2f + Noise.rnd(b.plot, j, 4) * 4f
            i.px(x0 + u, yb, v, Pal.HAY_D)
        }
        for (j in 0..2) postBox(x0 + j * 1.25f, yb, top)
        // floor beams and the side frame on the +x end
        i.line(x0 + 2.5f, yb, top - 0.5f, x0 + 2.5f, yf, top - 0.5f, Pal.WOOD_D)
        i.line(x0 + 2.5f, yb, 7f, x0 + 2.5f, yf, 7f, Pal.WOOD_D)
        i.line(x0 + 2.5f, yb, 1f, x0 + 2.5f, yf, 1f, Pal.WOOD_D)
        // front ladder: light slats, hay drying over the lower rungs
        for (z in 2..12 step 2) {
            i.line(x0, yf, z.toFloat(), x0 + 2.5f, yf, z.toFloat(), Pal.WOOD_L)
            i.line(x0, yf, z - 0.5f, x0 + 2.5f, yf, z - 0.5f, Pal.WOOD_D)
        }
        if (hay) {
            i.panelY(x0 + 0.1f, yf, 3f, 1.05f, 7f, Pal.HAY_M)
            i.panelY(x0 + 1.35f, yf, 3f, 1.05f, 3f, Pal.HAY_M)
            for (j in 0 until 16) {
                val u = Noise.rnd(b.plot, j, 5); val v = Noise.rnd(b.plot, j, 6)
                val xx = if (j % 3 == 0) x0 + 1.35f + u * 1.05f else x0 + 0.1f + u * 1.05f
                val zz = if (j % 3 == 0) 3f + v * 3f else 3f + v * 7f
                i.px(xx, yf, zz, if (j % 2 == 0) Pal.HAY_L else Pal.HAY_D)
            }
            for (j in 0..3) i.px(x0 + 0.2f + j * 0.3f, yf, 2.5f, Pal.HAY_L)
        }
        for (j in 0..2) postBox(x0 + j * 1.25f, yf, top)
        roofX(x0 - 0.45f, yb - 0.6f, top - 0.5f, 3.4f, 2.7f, 5f, tilesSideLit(), Pal.WOOD_D)
        if (!env.snow) i.line(x0 - 0.45f, yf + 0.1f, top + 1.5f, x0 + 2.95f, yf + 0.1f, top + 1.5f, Col.hex(0x7A3220))
    }

    private fun postBox(x: Float, y: Float, h: Float) =
        i.box(x - 0.125f, y - 0.125f, 0f, 0.25f, 0.25f, h, Pal.WOOD_L, Pal.WOOD_D, Pal.WOOD_X)

    private fun beehive(b: Building, x: Float, y: Float) {
        val x0 = x + 0.5f; val y0 = y + 0.9f
        if (!env.snow && env.month in 4..9) flowers(x + 1.5f, y + 2.7f, b.plot * 3)
        i.post(x0 + 0.1f, y0 + 1.3f, 0f, 1f, Pal.WOOD_X); i.post(x0 + 1.9f, y0 + 1.3f, 0f, 1f, Pal.WOOD_X)
        i.box(x0, y0, 1f, 2f, 1.3f, 6f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        for (row in 0..1) for (j in 0..3) {
            val col = Pal.PAINTED[(j + row * 2 + b.plot) % Pal.PAINTED.size]
            val xx = x0 + 0.12f + j * 0.47f; val zz = 1.6f + row * 2.6f
            i.panelY(xx, y0 + 1.3f, zz, 0.36f, 2f, col)
            i.px(xx + 0.12f, y0 + 1.3f, zz + 0.3f, Pal.WOOD_X)
            if (finest) { // the painted panels' pictures: a figure and a border
                i.line(xx, y0 + 1.3f, zz + 1.8f, xx + 0.36f, y0 + 1.3f, zz + 1.8f, Col.scale(col, 0.7f))
                c.set(i.ix(xx + 0.24f, y0 + 1.3f), i.iy(xx + 0.24f, y0 + 1.3f, zz + 1.1f), Pal.FLAG_WHITE)
            }
        }
        roofX(x0 - 0.3f, y0 - 0.3f, 6.8f, 2.6f, 1.9f, 3.5f, shingles(), Pal.WOOD_D)
        if (!env.snow && env.dark < 0.5f) {
            val cx = i.sx(x0 + 1f, y0 + 1.8f); val cy = i.sy(x0 + 1f, y0 + 1.8f, 4f)
            s.fx { bees(cx, cy, b.plot) }
        }
    }

    internal fun bees(cx: Float, cy: Float, seed: Int) {
        val t = s.time
        for (j in 0..4) {
            val a = t * (1.7 + j * 0.23) + j * 1.9 + seed
            val bx = cx + (cos(a) * (5 + j)).toFloat() * k; val by = cy + (sin(a * 1.3) * 3).toFloat() * k - j * 0.6f * k
            c.block(bx.toInt(), by.toInt(), k, Pal.GOLD); c.block(bx.toInt() + k, by.toInt(), k, Col.hex(0x2A2218))
        }
    }

    private fun watchtower(b: Building, x: Float, y: Float) {
        val x0 = x + 0.75f; val y0 = y + 0.75f; val w = 1.5f
        postBox(x0, y0, 17f)
        postBox(x0 + w, y0, 17f); postBox(x0, y0 + w, 17f)
        // cross bracing
        i.line(x0, y0 + w, 2f, x0 + w, y0 + w, 15f, Pal.WOOD_D); i.line(x0 + w, y0 + w, 2f, x0, y0 + w, 15f, Pal.WOOD_D)
        i.line(x0 + w, y0, 2f, x0 + w, y0 + w, 15f, Pal.WOOD_X); i.line(x0 + w, y0 + w, 2f, x0 + w, y0, 15f, Pal.WOOD_X)
        postBox(x0 + w, y0 + w, 17f)
        // platform + parapet
        i.box(x0 - 0.4f, y0 - 0.4f, 16f, w + 0.8f, w + 0.8f, 2f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        i.post(x0 - 0.3f, y0 - 0.3f, 18f, 24f, Pal.WOOD_X)
        i.panelY(x0 - 0.4f, y0 + w + 0.4f, 18f, w + 0.8f, 3f, Pal.WOOD_M)
        i.panelX(x0 + w + 0.4f, y0 - 0.4f, 18f, w + 0.8f, 3f, Pal.WOOD_D)
        if (fine) { // the parapet's boards
            for (u in 1..4) { i.line(x0 - 0.4f + u * (w + 0.8f) / 5f, y0 + w + 0.4f, 18f, x0 - 0.4f + u * (w + 0.8f) / 5f, y0 + w + 0.4f, 21f, Pal.WOOD_D); i.line(x0 + w + 0.4f, y0 - 0.4f + u * (w + 0.8f) / 5f, 18f, x0 + w + 0.4f, y0 - 0.4f + u * (w + 0.8f) / 5f, 21f, Pal.WOOD_X) }
        }
        i.line(x0 - 0.4f, y0 + w + 0.4f, 21f, x0 + w + 0.4f, y0 + w + 0.4f, 21f, Pal.WOOD_L)
        i.post(x0 + w + 0.3f, y0 - 0.3f, 21f, 24f, Pal.WOOD_X); i.post(x0 - 0.3f, y0 + w + 0.3f, 21f, 24f, Pal.WOOD_X)
        i.post(x0 + w + 0.3f, y0 + w + 0.3f, 21f, 24f, Pal.WOOD_X)
        if (lit) {
            glow { i.px(x0 + w / 2, y0 + w / 2, 21f, Pal.WINDOW_LIT) }
            s.light(i.sx(x0 + w / 2, y0 + w / 2), i.sy(x0 + w / 2, y0 + w / 2, 21f), 14f, 0.5f)
        }
        val r = shingles()
        i.pyramid(x0 - 0.6f, y0 - 0.6f, 24f, w + 1.2f, w + 1.2f, 7f, r.front, if (env.snow) Pal.SNOW_D else Pal.SHINGLE_D)
        if (fine && !env.snow) for (j in 1..3) { // shingle rows up the two visible faces
            val t = j / 4f; val zz = 24f + t * 7f
            i.line(x0 - 0.6f + t * (w + 1.2f) / 2, y0 + w + 0.6f - t * (w + 1.2f) / 2, zz, x0 + w + 0.6f - t * (w + 1.2f) / 2, y0 + w + 0.6f - t * (w + 1.2f) / 2, zz, Pal.SHINGLE_D)
            i.line(x0 + w + 0.6f - t * (w + 1.2f) / 2, y0 - 0.6f + t * (w + 1.2f) / 2, zz, x0 + w + 0.6f - t * (w + 1.2f) / 2, y0 + w + 0.6f - t * (w + 1.2f) / 2, zz, Col.scale(Pal.SHINGLE_D, 0.8f))
        }
        // flag: white, blue, red
        val ax = i.ix(x0 + w / 2, y0 + w / 2); val ay = i.iy(x0 + w / 2, y0 + w / 2, 31f)
        val wave = ((s.time * 4).toInt() % 2)
        s.sprite(ax, ay) {
            c.vline(ax, ay - 6, ay, Pal.WOOD_X)
            for (j in 0..4) {
                val dy = if ((j + wave) % 3 == 0) 1 else 0
                c.set(ax + 1 + j, ay - 6 + dy, Pal.FLAG_WHITE); c.set(ax + 1 + j, ay - 5 + dy, Pal.FLAG_BLUE); c.set(ax + 1 + j, ay - 4 + dy, Pal.FLAG_RED)
            }
        }
    }

    private fun smithy(b: Building, x: Float, y: Float) {
        val x0 = x + 0.5f; val y0 = y + 0.5f
        i.box(x0, y0, 0f, 2f, 2f, 8f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        for (z in intArrayOf(3, 6)) {
            i.line(x0, y0 + 2f, z.toFloat(), x0 + 2f, y0 + 2f, z.toFloat(), Pal.STONE_D)
            i.line(x0 + 2f, y0, z.toFloat(), x0 + 2f, y0 + 2f, z.toFloat(), Pal.STONE_X)
        }
        speckle(x0, y0, 0f, 2f, 2f, 8f, b.plot * 7, Pal.STONE_D, Pal.STONE_X)
        mortar(x0, y0, 0f, 2f, 2f, 8f, Pal.STONE_D, Pal.STONE_X)
        // open forge
        i.panelY(x0 + 0.3f, y0 + 2f, 0f, 1.1f, 6f, Col.hex(0x2A1E18))
        val flick = 0.8f + 0.2f * sin(s.time * 13).toFloat()
        glow {
            i.panelY(x0 + 0.5f, y0 + 2f, 0.5f, 0.6f, 2f, Pal.FLAME[2])
            i.px(x0 + 0.75f, y0 + 2f, 2f, Pal.FLAME[1])
        }
        s.light(i.sx(x0 + 0.8f, y0 + 2f), i.sy(x0 + 0.8f, y0 + 2f, 2f), 18f, 0.7f * flick)
        winX(x0 + 2f, y0 + 0.7f, 3f, 0.5f, 2f)
        // anvil
        val ax = i.ix(x0 + 1.6f, y0 + 2.5f); val ay = i.iy(x0 + 1.6f, y0 + 2.5f, 0f)
        s.sprite(ax, ay) {
            c.fillRect(ax - 1, ay - 2, 2, 2, Pal.STONE_X); c.hline(ax - 2, ax + 1, ay - 3, Col.hex(0x3C3C44))
            c.set(ax - 2, ay - 3, Col.hex(0x6A6A76))
        }
        roofX(x0 - 0.3f, y0 - 0.3f, 7.5f, 2.6f, 2.6f, 5f, slate(), Pal.STONE_M)
        // chimney with glowing mouth
        i.box(x0 + 1.4f, y0 + 0.2f, 6f, 0.6f, 0.6f, 11f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        glow { i.top(x0 + 1.5f, y0 + 0.3f, 17f, 0.4f, 0.4f, Pal.FLAME[2]) }
        val cx = i.sx(x0 + 1.7f, y0 + 0.5f); val cy = i.sy(x0 + 1.7f, y0 + 0.5f, 17f)
        s.smoke(cx, cy, 0.8f, b.plot * 5)
        s.sparks(cx, cy, 3, b.plot)
    }

    private fun lipa(b: Building, x: Float, y: Float) {
        val cx = x + 1.5f; val cy = y + 1.5f
        i.box(cx - 0.2f, cy - 0.2f, 0f, 0.4f, 0.4f, 10f, Pal.LOG_L, Pal.LOG_M, Pal.LOG_D)
        if (fine) i.line(cx - 0.1f, cy + 0.2f, 1f, cx - 0.1f, cy + 0.2f, 9f, Pal.LOG_D) // bark
        val sx = i.sx(cx, cy); val sy = i.sy(cx, cy, 19f)
        s.trees.canopy(sx, sy, 1.25f * k, env.season, b.plot * 11, big = true)
        // bench
        i.box(x + 0.8f, y + 2.45f, 1.5f, 1.4f, 0.4f, 0.5f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        i.post(x + 0.9f, y + 2.85f, 0f, 1.5f, Pal.WOOD_X); i.post(x + 2.1f, y + 2.85f, 0f, 1.5f, Pal.WOOD_X)
        i.panelY(x + 0.8f, y + 2.45f, 2f, 1.4f, 1.5f, Pal.WOOD_M)
        if (fine) i.line(x + 0.8f, y + 2.45f, 2.75f, x + 2.2f, y + 2.45f, 2.75f, Pal.WOOD_D) // the backrest's slats
        if (b.level >= 2 && !env.snow) flowers(x + 0.5f, y + 1.5f, b.plot * 5)
    }

    private fun house(b: Building, x: Float, y: Float) {
        val x0 = x + 0.25f; val y0 = y + 0.25f; val w = 2.5f
        i.box(x0, y0, 0f, w, w, 3f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        speckle(x0, y0, 0f, w, w, 3f, b.plot * 17, Pal.STONE_D, Pal.STONE_X)
        mortar(x0, y0, 0f, w, w, 3f, Pal.STONE_D, Pal.STONE_X)
        i.box(x0, y0, 3f, w, w, 9f, Pal.WALL_L, Pal.WALL_M, Pal.WALL_D)
        // +y gable side: two shuttered windows below the balcony
        winY(x0 + 0.5f, y0 + w, 4.5f, shutters = true)
        winY(x0 + 1.6f, y0 + w, 4.5f, shutters = true)
        flowerBoxY(x0 + 0.5f, y0 + w, 4.5f); flowerBoxY(x0 + 1.6f, y0 + w, 4.5f)
        // +x side: window, door, window
        winX(x0 + w, y0 + 0.35f, 5f, shutters = true)
        doorX(x0 + w, y0 + 1.15f, 0f, 0.6f, 7f, handleU = 0.1f, handleZ = 3.5f)
        winX(x0 + w, y0 + 2.0f, 5f, 0.35f)
        // wooden balcony across the gable end
        i.panelY(x0 + 0.6f, y0 + w, 9f, 0.5f, 3f, Pal.DOOR)
        i.box(x0 - 0.1f, y0 + w, 8.5f, w + 0.2f, 0.5f, 1f, Pal.WOOD_M, Pal.WOOD_D, Pal.WOOD_X)
        i.panelY(x0 - 0.1f, y0 + w + 0.5f, 9.5f, w + 0.2f, 2.5f, Pal.WOOD_D)
        var j = 0f
        while (j <= w + 0.2f) { i.post(x0 - 0.1f + j, y0 + w + 0.5f, 9.5f, 11.5f, Pal.WOOD_M); j += 0.5f }
        if (fine) { j = 0.25f; while (j < w + 0.2f) { i.line(x0 - 0.1f + j, y0 + w + 0.5f, 9.5f, x0 - 0.1f + j, y0 + w + 0.5f, 11.5f, Pal.WOOD_M); j += 0.5f } } // balusters between
        i.line(x0 - 0.1f, y0 + w + 0.5f, 12f, x0 + w + 0.1f, y0 + w + 0.5f, 12f, Pal.WOOD_L)
        if (env.season != Season.WINTER && env.month in 5..10) {
            // geraniums on the balcony rail: very Slovene
            var fx = x0 + 0.05f; var n = 0
            while (fx < x0 + w) {
                i.px(fx, y0 + w + 0.5f, 13f, if (n % 3 == 2) Pal.LEAF else if (n % 2 == 0) Pal.GERANIUM else Pal.GERANIUM_D)
                fx += 0.25f; n++
            }
        }
        roofY(x0 - 0.4f, y0 - 0.4f, 12f, w + 0.8f, w + 1.1f, 8f, tiles(), Pal.WOOD_D)
        // gable boards + tiny window
        i.line(x0 + w / 2, y0 + w + 0.7f, 19f, x0 + w / 2, y0 + w + 0.7f, 13f, Pal.WOOD_X)
        if (lit) glow { i.px(x0 + w / 2 - 0.35f, y0 + w + 0.7f, 15f, Pal.WINDOW_LIT) } else i.px(x0 + w / 2 - 0.35f, y0 + w + 0.7f, 15f, Pal.GLASS)
        if (!env.snow) {
            i.line(x0 + w * 0.75f + 0.2f, y0 - 0.4f, 16f, x0 + w * 0.75f + 0.2f, y0 + w + 0.7f, 16f, Col.hex(0x6A2616))
        }
        chimney(x0 + 1.85f, y0 + 0.8f, 15f, 5f, env.windows > 0.1f || env.season == Season.WINTER || b.level >= 2)
    }

    private fun church(b: Building, x: Float, y: Float) {
        val spireL = Col.hex(0x5C5560); val spireD = Col.hex(0x3E3842)
        // bell tower behind the nave
        val tx = x + 1.0f; val ty = y + 0.2f
        i.box(tx, ty, 0f, 1f, 1f, 24f, Pal.WALL_L, Pal.WALL_M, Pal.WALL_D)
        i.line(tx, ty + 1f, 17f, tx + 1f, ty + 1f, 17f, Pal.STONE_M); i.line(tx + 1f, ty, 17f, tx + 1f, ty + 1f, 17f, Pal.STONE_D)
        if (fine) { i.line(tx, ty + 1f, 8f, tx + 1f, ty + 1f, 8f, Pal.WALL_D); i.line(tx + 1f, ty, 8f, tx + 1f, ty + 1f, 8f, Pal.STONE_D) } // a string course
        i.panelY(tx + 0.3f, ty + 1f, 19f, 0.4f, 3f, Col.hex(0x2A2226))
        i.panelX(tx + 1f, ty + 0.3f, 19f, 0.4f, 3f, Col.hex(0x2A2226))
        if (fine) { i.line(tx + 0.5f, ty + 1f, 19f, tx + 0.5f, ty + 1f, 22f, Pal.WALL_D); i.line(tx + 1f, ty + 0.5f, 19f, tx + 1f, ty + 0.5f, 22f, Pal.WALL_D) } // the belfry's louvres
        i.px(tx + 1f, ty + 0.5f, 14f, Pal.GOLD_L); i.px(tx + 1f, ty + 0.5f, 13f, Pal.GOLD)
        i.pyramid(tx - 0.1f, ty - 0.1f, 24f, 1.2f, 1.2f, 13f, if (env.snow) Pal.SNOW_M else spireL, spireD)
        val ax = i.ix(tx + 0.5f, ty + 0.5f); val ay = i.iy(tx + 0.5f, ty + 0.5f, 37f)
        s.sprite(ax, ay) { c.vline(ax, ay - 3, ay, Pal.GOLD); c.set(ax - 1, ay - 2, Pal.GOLD); c.set(ax + 1, ay - 2, Pal.GOLD) }
        // nave
        val nx = x + 0.5f; val ny = y + 1.2f; val nw = 2f; val nd = 1.8f
        i.box(nx, ny, 0f, nw, nd, 11f, Pal.WALL_L, Pal.WALL_M, Pal.WALL_D)
        i.line(nx, ny + nd, 1f, nx + nw, ny + nd, 1f, Pal.STONE_M); i.line(nx + nw, ny, 1f, nx + nw, ny + nd, 1f, Pal.STONE_D)
        winX(nx + nw, ny + 0.3f, 4f, 0.35f, 5f); winX(nx + nw, ny + 1.15f, 4f, 0.35f, 5f)
        doorY(nx + 0.75f, ny + nd, 0f, 0.5f, 5f)
        i.px(nx + 1f, ny + nd, 5f, Pal.DOOR)
        roofY(nx - 0.2f, ny - 0.1f, 11f, nw + 0.4f, nd + 0.3f, 7f, tilesSideLit(), Pal.WALL_L)
        i.px(nx + 1f, ny + nd + 0.2f, 14f, if (lit) Pal.WINDOW_LIT else Pal.GLASS)
        i.px(nx + 1f, ny + nd + 0.2f, 13f, if (lit) Pal.WINDOW_LIT else Pal.GLASS)
        if (b.level >= 2 && !env.snow) flowers(x + 2.8f, y + 2.8f, b.plot)
    }

    private fun school(b: Building, x: Float, y: Float) {
        val x0 = x + 0.25f; val y0 = y + 0.25f; val w = 2.5f
        i.box(x0, y0, 0f, w, w, 2f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        mortar(x0, y0, 0f, w, w, 2f, Pal.STONE_D, Pal.STONE_X)
        i.box(x0, y0, 2f, w, w, 11f, Pal.OCHRE_L, Pal.OCHRE_M, Pal.OCHRE_D)
        i.line(x0, y0 + w, 7f, x0 + w, y0 + w, 7f, Pal.WALL_L); i.line(x0 + w, y0, 7f, x0 + w, y0 + w, 7f, Pal.WALL_M)
        for (z in floatArrayOf(3f, 8.5f)) {
            winY(x0 + 0.3f, y0 + w, z, 0.45f, 3f); winY(x0 + 1.75f, y0 + w, z, 0.45f, 3f)
            winX(x0 + w, y0 + 0.3f, z, 0.45f, 3f); winX(x0 + w, y0 + 1.05f, z, 0.45f, 3f); winX(x0 + w, y0 + 1.8f, z, 0.45f, 3f)
        }
        doorY(x0 + 1.0f, y0 + w, 2f, 0.55f, 5f)
        i.box(x0 + 0.85f, y0 + w, 0f, 0.85f, 0.35f, 2f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        i.panelY(x0 + 0.95f, y0 + w, 8.5f, 0.65f, 2f, Pal.FLAG_BLUE)
        if (fine) c.set(i.ix(x0 + 1.27f, y0 + w), i.iy(x0 + 1.27f, y0 + w, 9.5f), Pal.FLAG_WHITE) // the sign's lettering
        roofX(x0 - 0.35f, y0 - 0.35f, 13f, w + 0.7f, w + 0.7f, 6.5f, tilesSideLit(), Pal.OCHRE_D)
        // bell turret on the ridge
        val bx = x0 + 1.0f; val by = y0 + 1.0f
        i.box(bx, by, 18f, 0.5f, 0.5f, 3f, Pal.WALL_L, Pal.WALL_M, Pal.WALL_D)
        i.panelY(bx + 0.12f, by + 0.5f, 18.5f, 0.25f, 2f, Pal.GOLD)
        i.pyramid(bx - 0.15f, by - 0.15f, 21f, 0.8f, 0.8f, 4f, tilesSideLit().front, Pal.ROOF_D)
    }

    private fun market(b: Building, x: Float, y: Float) {
        stall(x + 0.2f, y + 0.3f, Pal.FLAG_RED, b.plot)
        stall(x + 1.75f, y + 0.3f, Pal.FLAG_BLUE, b.plot + 1)
        // crates + barrel between rows
        i.box(x + 2.35f, y + 1.7f, 0f, 0.5f, 0.5f, 2.5f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        i.box(x + 0.25f, y + 1.9f, 0f, 0.45f, 0.45f, 3f, Pal.LOG_L, Pal.LOG_M, Pal.LOG_D)
        i.line(x + 0.25f, y + 2.35f, 1.5f, x + 0.7f, y + 2.35f, 1.5f, Pal.STONE_X)
        if (fine) { i.line(x + 0.25f, y + 2.35f, 0.6f, x + 0.7f, y + 2.35f, 0.6f, Pal.STONE_X); i.line(x + 0.25f, y + 2.35f, 2.4f, x + 0.7f, y + 2.35f, 2.4f, Pal.STONE_X) } // the barrel's hoops
        stall(x + 1.0f, y + 1.9f, Pal.LEAF, b.plot + 2)
    }

    private fun stall(x: Float, y: Float, awn: Int, seed: Int) {
        val w = 1.15f; val d = 0.7f
        i.post(x, y, 0f, 8f, Pal.WOOD_D)
        i.post(x + w, y, 0f, 8f, Pal.WOOD_D)
        i.box(x, y, 0f, w, d, 3f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        if (fine) i.line(x, y + d, 1.5f, x + w, y + d, 1.5f, Pal.WOOD_D) // the counter's boards
        // goods: seasonal produce
        val goods = when (env.season) {
            Season.AUTUMN -> intArrayOf(Col.hex(0xE07A1E), Col.hex(0xC8392F), Col.hex(0xE8C040), Col.hex(0x8A5A32))
            Season.WINTER -> intArrayOf(Col.hex(0xC8A060), Col.hex(0xE8E0C8), Col.hex(0x8A5A32), Col.hex(0xC8392F))
            else -> intArrayOf(Col.hex(0xC8392F), Col.hex(0x5DAE3E), Col.hex(0xF0D060), Col.hex(0xE8E0C8))
        }
        for (j in 0..5) {
            val u = 0.1f + j * 0.17f
            i.px(x + u, y + 0.35f, 3f, goods[(j + seed) % goods.size])
            i.px(x + u, y + 0.6f, 3f, goods[(j + seed + 1) % goods.size])
        }
        i.post(x, y + d, 0f, 7f, Pal.WOOD_X); i.post(x + w, y + d, 0f, 7f, Pal.WOOD_X)
        // striped awning sloping to the front
        val n = 6
        for (j in 0 until n) {
            val xa = x - 0.1f + (w + 0.2f) * j / n; val xb = x - 0.1f + (w + 0.2f) * (j + 1) / n
            val col = if (j % 2 == 0) awn else Pal.FLAG_WHITE
            i.quad(xa, y - 0.15f, 9f, xb, y - 0.15f, 9f, xb, y + d + 0.2f, 7f, xa, y + d + 0.2f, 7f, col)
            i.px(xa + 0.08f, y + d + 0.2f, 6f, Col.scale(col, 0.8f))
        }
        if (env.snow) i.line(x - 0.1f, y - 0.15f, 9.5f, x + w + 0.1f, y - 0.15f, 9.5f, Pal.SNOW_L)
    }

    // ---------------------------------------------------------------- overlays

    /** Broken roof, scattered debris and rising dark smoke. */
    fun damage(b: Building, x: Float, y: Float) {
        // char the walls in a blotchy pattern
        val id = c.penId
        val x0 = i.sx(x, y + 3f).toInt(); val x1 = i.sx(x + 3f, y).toInt()
        val y0 = i.sy(x, y, height(b.type)).toInt(); val y1 = i.sy(x + 3f, y + 3f, 0f).toInt()
        for (yy in maxOf(0, y0)..minOf(c.height - 1, y1)) for (xx in maxOf(0, x0)..minOf(c.width - 1, x1)) {
            val j = yy * c.width + xx
            if (c.ids[j] != id) continue
            val n = Noise.v2(xx * 0.3f / k, yy * 0.3f / k, b.plot)
            if (n > 0.45f) c.pixels[j] = Col.scale(Col.mix(c.pixels[j], Col.hex(0x3A3230), 0.35f), 0.78f)
        }
        val h = height(b.type) * 0.72f
        val cx = i.sx(x + 1.5f, y + 1.5f); val cy = i.sy(x + 1.5f, y + 1.5f, h)
        val hole = Col.hex(0x1E1618)
        val ax = cx.toInt(); val ay = cy.toInt()
        s.sprite(ax, ay) {
            for (yy in -3..3) for (xx in -5..5) {
                val d = (xx * xx) / 25f + (yy * yy) / 9f + (Noise.rnd(b.plot, xx, yy) - 0.5f) * 0.6f
                if (d < 0.8f) c.set(ax + xx, ay + yy, hole)
            }
            // broken rafters sticking out of the hole
            c.line(ax - 5, ay - 3, ax + 1, ay + 1, Pal.WOOD_L)
            c.line(ax - 1, ay + 3, ax + 4, ay - 2, Pal.WOOD_M)
            c.line(ax + 2, ay + 2, ax + 5, ay + 1, Pal.WOOD_D)
        }
        for (j in 0 until 10) {
            val u = Noise.rnd(b.plot, j, 11) * 3f; val v = Noise.rnd(b.plot, j, 12)
            i.px(x + u, y + 2.9f + v * 0.5f, 0f, if (j % 2 == 0) Pal.WOOD_M else Pal.STONE_D)
        }
        s.smoke(cx, cy - 2 * k, 1.5f, b.plot * 97, dark = true)
        s.smoke(cx + 2 * k, cy, 1.1f, b.plot * 31 + 5, dark = true)
    }

    /** Wooden scaffolding for a building under construction; [reveal] is 0..1 of the build height. */
    fun scaffold(x: Float, y: Float, h: Float, alpha: Float) {
        if (alpha <= 0.05f) return
        val top = h + 2
        val col = Col.mix(Pal.WOOD_L, Pal.WOOD_M, 0.3f)
        val pts = arrayOf(floatArrayOf(0.2f, 0.2f), floatArrayOf(2.8f, 0.2f), floatArrayOf(0.2f, 2.8f), floatArrayOf(2.8f, 2.8f))
        for ((j, p) in pts.withIndex()) {
            if (j == 0 && alpha < 0.5f) continue
            i.post(x + p[0], y + p[1], 0f, top, Pal.WOOD_D)
        }
        var z = 4f
        while (z < top) {
            i.line(x + 0.2f, y + 2.8f, z, x + 2.8f, y + 2.8f, z, col)
            i.line(x + 2.8f, y + 0.2f, z, x + 2.8f, y + 2.8f, z, Pal.WOOD_M)
            z += 5f
        }
        i.line(x + 0.2f, y + 2.8f, 0f, x + 2.8f, y + 2.8f, top, Pal.WOOD_D)
    }

    private companion object {
        /** A window frame's width beyond the glass, in cells (about one pixel at k = 2). */
        const val FRAME = 0.125f
    }
}
