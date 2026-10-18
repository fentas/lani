package si.lanisce.lani.game.render

import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Decor
import si.lanisce.lani.game.Festive
import si.lanisce.lani.game.GameState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A celebration's decorations on the map ([Festive]; companion/GAME.md "Ages and their celebrations"), each going up as
 * the age's moment puts it up (its share 0..1, [Festive.upOf]) and in painter's order with everything else: torches round
 * the fire (the first camp's evening), the likof's little spruce with its ribbons on the newest roof, garlands of flags
 * round the open ground by the fire (lanterns in them after dusk), the veselica's dance floor with its harmonika player,
 * the semenj's stalls along the road, the town's banners along the road and its procession marching in under them; and
 * the village's sign at the road, turning to the new age ([Festive.sign]). Drawn only while a festive frame asks: the
 * village without a celebration is as it always was.
 */
internal class FestivePainter {
    /**
     * Adds [f]'s decorations of [state]'s village to the frame ([add]: a depth and how to draw it, as the renderer's
     * objects), round the fire of [comp] with the open ground of radius [plazaR] and the road [roadHalf] cells to either side,
     * the forest's edge at [edgeAge]'s. Returns the point over the sign's board (nominal canvas px), or null without a sign.
     */
    fun add(
        s: SceneCtx, state: GameState, f: Festive, comp: Composition, buildings: List<Building>,
        roadHalf: Float, plazaR: Float, edgeAge: Age, compact: Boolean, add: (Float, () -> Unit) -> Unit,
    ): CanvasPoint? {
        val land = s.land
        for (d in f.decor) {
            val up = f.upOf(d)
            if (up <= 0f) continue
            when (d) {
                Decor.TORCHES -> torches(s, plazaR, up, add)
                Decor.SPRUCE -> roof(state, buildings)?.let { b -> spruce(s, b, up, add) }
                Decor.BUNTING -> bunting(s, plazaR, up, add)
                Decor.FLOOR -> floor(s, up, add)
                Decor.STALLS -> stalls(s, roadHalf, up, add)
                Decor.BANNERS -> banners(s, roadHalf, edgeAge, up, add)
            }
        }
        f.march?.takeIf { !compact && it > 0f && it < 1f }?.let { march(s, state, roadHalf, edgeAge, it, add) }
        val sign = f.sign ?: return null
        if (compact) return null
        val x = land.roadOut(edgeAge, SIGN_IN)
        val y = land.roadY(x) - roadHalf - 0.7f
        add(x + y) { sign(s, x, y, sign) }
        return CanvasPoint(comp.fireX + (x - y) * 4f, comp.fireY + (x + y) * 2f - SIGN_TOP)
    }

    /** The building whose roof the likof's spruce stands on: the newest house, else the newest hut; null when there is none. */
    fun roof(state: GameState, buildings: List<Building>): Building? =
        buildings.filter { it.type == BuildingType.HOUSE }.maxByOrNull { it.builtAt }
            ?: buildings.filter { it.type == BuildingType.HUT }.maxByOrNull { it.builtAt }

    // --- the first camp's torches ---------------------------------------------------------------------------------

    /** Six torches on poles round the open ground, rising from the ground, their flames lit once up. */
    private fun torches(s: SceneCtx, plazaR: Float, up: Float, add: (Float, () -> Unit) -> Unit) {
        val r = plazaR + 0.55f
        for (j in 0 until 6) {
            val a = PI / 6 + j * PI / 3
            val x = (cos(a) * r).toFloat(); val y = (sin(a) * r).toFloat()
            add(x + y) {
                s.newObject(Pal.OUTLINE)
                val h = 10f * up
                s.iso.post(x, y, 0f, h, Pal.WOOD_D)
                if (up > 0.7f) flame(s, s.iso.ix(x, y), s.iso.iy(x, y, h + 1f), j)
            }
        }
    }

    /** A torch's flame (a nominal 3 px, flickering), lit, with its warm light round it. */
    private fun flame(s: SceneCtx, ax: Int, ay: Int, seed: Int) {
        val c = s.canvas
        val f = ((s.time * 9 + seed * 1.7) % 2.0).toFloat()
        c.penEmissive = true
        s.sprite(ax, ay) {
            c.set(ax, ay, Pal.FLAME[2]); c.set(ax, ay - 1, Pal.FLAME[1])
            c.set(ax + if (f < 1f) 0 else 1, ay - 2, Pal.FLAME[0])
            if (f > 0.6f) c.set(ax - 1, ay - 1, Pal.FLAME[3])
        }
        c.penEmissive = false
        s.light(ax.toFloat(), ay - 1f * s.k, 18f, 0.6f)
    }

    // --- the likof's spruce -----------------------------------------------------------------------------------------

    /**
     * The likof's little spruce on the ridge of [b] (a house's at its balcony gable, a hut's at its gable end), growing up
     * as it is set there, its ribbons (red, white, blue, gold) fluttering from its top.
     */
    private fun spruce(s: SceneCtx, b: Building, up: Float, add: (Float, () -> Unit) -> Unit) {
        val o = s.land.origin(b.plot)
        val (x, y, z) = if (b.type == BuildingType.HOUSE) Triple(o[0] + 0.25f + 1.25f, o[1] + 0.25f + 2.5f + 0.55f, 20f)
        else Triple(o[0] + 0.5f + 2.2f, o[1] + 0.5f + 1.0f, 12.5f)
        add(o[0] + o[1] + 3f + 0.02f) {
            s.newObject(Pal.OUTLINE_TREE)
            val ax = s.iso.ix(x, y); val ay = s.iso.iy(x, y, z)
            val c = s.canvas
            val rows = (SPRUCE_ROWS.size * up).roundToInt().coerceIn(1, SPRUCE_ROWS.size)
            s.sprite(ax, ay) {
                c.set(ax, ay, Pal.WOOD_D)
                // the tiers from the bottom up, as many as it has grown
                for (i in 0 until rows) {
                    val row = SPRUCE_ROWS.size - 1 - i
                    val half = SPRUCE_ROWS[row]
                    val yy = ay - 1 - i
                    for (dx in -half..half) c.set(ax + dx, yy, if (dx < 0) SPRUCE_L else SPRUCE_D)
                }
                if (up > 0.8f) {
                    val top = ay - 1 - SPRUCE_ROWS.size
                    c.set(ax, top, Pal.GOLD_L)
                    // the ribbons, from the top down to either side, fluttering
                    val wave = ((s.time * 4).toInt() and 1)
                    val cols = intArrayOf(Pal.FLAG_RED, Pal.FLAG_WHITE, Pal.FLAG_BLUE, Pal.GOLD)
                    for ((n, col) in cols.withIndex()) {
                        val side = if (n % 2 == 0) -1 else 1
                        val len = 2 + n / 2
                        for (q in 1..len) c.set(ax + side * (1 + q / 2) + if (q == len) side * wave else 0, top + q + n / 2, col)
                    }
                }
            }
        }
    }

    // --- the garlands --------------------------------------------------------------------------------------------

    /** Six poles round the open ground, rising; the flags strung between them once they're up (lanterns in them after dusk). */
    private fun bunting(s: SceneCtx, plazaR: Float, up: Float, add: (Float, () -> Unit) -> Unit) {
        val r = plazaR + 1.15f
        val poles = (0 until 6).map { j -> val a = j * PI / 3; floatArrayOf((cos(a) * r).toFloat(), (sin(a) * r).toFloat()) }
        for (p in poles) add(p[0] + p[1]) {
            s.newObject(Pal.OUTLINE)
            s.iso.post(p[0], p[1], 0f, POLE * up, Pal.WOOD_D)
            s.iso.px(p[0], p[1], POLE * up + 1f, Pal.FLAG_RED)
        }
        if (up < 0.75f) return
        s.fx {
            val lanterns = s.env.windows > 0.3f
            for (j in poles.indices) {
                val a = poles[j]; val b = poles[(j + 1) % poles.size]
                s.effects.bunting(s.iso.sx(a[0], a[1]), s.iso.sy(a[0], a[1], POLE), s.iso.sx(b[0], b[1]), s.iso.sy(b[0], b[1], POLE), j, lanterns)
            }
        }
    }

    // --- the veselica's dance floor ---------------------------------------------------------------------------------

    /**
     * The wooden dance floor in front of the fire, its boards laid one after another as it goes up; the harmonika player at
     * its far corner once it's down, his bellows opening and closing.
     */
    private fun floor(s: SceneCtx, up: Float, add: (Float, () -> Unit) -> Unit) {
        val x0 = FLOOR_X; val y0 = FLOOR_Y; val w = FLOOR_W; val d = FLOOR_D
        add(FLAT_DEPTH) {
            s.newObject(Pal.WOOD_X)
            val boards = (8 * up).roundToInt().coerceIn(1, 8)
            for (i in 0 until boards) {
                val bx = x0 + i * w / 8f
                s.iso.top(bx, y0, 0.8f, w / 8f, d, if (i % 2 == 0) Pal.WOOD_M else Col.mix(Pal.WOOD_M, Pal.WOOD_L, 0.45f))
                s.iso.line(bx, y0 + d, 0.8f, bx, y0, 0.8f, Pal.WOOD_D)
            }
            s.iso.panelY(x0, y0 + d, 0f, w * boards / 8f, 0.8f, Pal.WOOD_D)
            s.iso.panelX(x0 + w * boards / 8f, y0, 0f, d, 0.8f, Pal.WOOD_X)
        }
        if (up < 0.9f) return
        // the harmonika player at the floor's far corner
        val px = x0 + w + 0.25f; val py = y0 + 0.2f
        add(px + py) {
            s.newObject(Pal.OUTLINE)
            val ax = s.iso.ix(px, py); val ay = s.iso.iy(px, py, 0f)
            s.effects.villager(ax, ay, 7331, false, flip = true)
            val c = s.canvas
            val open = ((s.time * 2.2).toInt() and 1) == 0
            s.sprite(ax, ay) {
                // the harmonika in front of him: its red bellows between the two white keyboards
                c.set(ax - 2, ay - 3, Pal.FLAG_WHITE); c.set(ax - 2, ay - 4, Pal.FLAG_WHITE)
                c.set(ax - 3, ay - 3, Pal.FLAG_RED); c.set(ax - 3, ay - 4, Pal.FLAG_RED)
                if (open) { c.set(ax - 4, ay - 3, Pal.FLAG_RED); c.set(ax - 4, ay - 4, Pal.FLAG_RED) }
                c.set(ax - (if (open) 5 else 4), ay - 3, Pal.FLAG_WHITE); c.set(ax - (if (open) 5 else 4), ay - 4, Pal.FLAG_WHITE)
            }
        }
    }

    // --- the semenj's stalls ---------------------------------------------------------------------------------------

    /** Three stalls along the road east of the fire, two on its far side and one on the near: counter, posts, striped awning. */
    private fun stalls(s: SceneCtx, roadHalf: Float, up: Float, add: (Float, () -> Unit) -> Unit) {
        val land = s.land
        for ((j, sx) in STALL_X.withIndex()) {
            val far = j != 1
            val ry = land.roadY(sx)
            val y = if (far) ry - roadHalf - 1.0f else ry + roadHalf + 0.25f
            val x = sx
            add(x + y + 0.6f) {
                s.newObject(Pal.OUTLINE)
                val i = s.iso
                val h = 7f * up
                // the counter, its goods, the two back posts and the awning sloping to the front
                i.box(x, y, 0f, 1.3f, 0.6f, min(3f, h), Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
                i.post(x, y, 0f, h, Pal.WOOD_D); i.post(x + 1.3f, y, 0f, h, Pal.WOOD_D)
                if (up > 0.5f) {
                    val goods = GOODS[j % GOODS.size]
                    for (q in 0 until 4) i.px(x + 0.2f + q * 0.3f, y + 0.3f, 3.5f, goods[q % goods.size])
                }
                if (up > 0.7f) {
                    for (q in 0 until 4) {
                        val a = x + q * 0.325f
                        i.quad(a, y, h, a + 0.325f, y, h, a + 0.325f, y + 0.9f, h - 1.6f, a, y + 0.9f, h - 1.6f, if (q % 2 == 0) Pal.FLAG_RED else Pal.FLAG_WHITE)
                    }
                    i.line(x, y + 0.9f, h - 1.6f, x + 1.3f, y + 0.9f, h - 1.6f, Pal.FLAG_RED)
                }
            }
        }
    }

    // --- the town's banners and procession ---------------------------------------------------------------------------

    /** Tall poles along both sides of the road, each with its banner (white, blue, red) fluttering from its crossbar. */
    private fun banners(s: SceneCtx, roadHalf: Float, edgeAge: Age, up: Float, add: (Float, () -> Unit) -> Unit) {
        val land = s.land
        val last = land.roadOut(edgeAge, 22f)
        var x = 2.4f
        var j = 0
        while (x < last) {
            for (side in intArrayOf(-1, 1)) {
                val bx = x + (if (side > 0) 0.6f else 0f)
                val by = land.roadY(bx) + side * (roadHalf + 0.35f)
                val seed = j * 2 + side
                add(bx + by) {
                    s.newObject(Pal.OUTLINE)
                    val top = BANNER_POLE * up
                    s.iso.post(bx, by, 0f, top, Pal.WOOD_D)
                    if (up > 0.85f) banner(s, s.iso.ix(bx, by), s.iso.iy(bx, by, top), seed)
                }
            }
            x += 2.6f
            j++
        }
    }

    /** A banner hanging from its crossbar at ([ax], [ay]): white, blue and red stripes, its foot swinging a little. */
    private fun banner(s: SceneCtx, ax: Int, ay: Int, seed: Int) {
        val c = s.canvas
        val swing = if (((s.time * 2.5 + seed * 0.37).toInt() and 1) == 0) 0 else 1
        s.sprite(ax, ay) {
            c.set(ax - 1, ay, Pal.WOOD_D); c.set(ax + 1, ay, Pal.WOOD_D); c.set(ax + 2, ay, Pal.WOOD_D)
            for (row in 1..6) {
                val col = when { row <= 2 -> Pal.FLAG_WHITE; row <= 4 -> Pal.FLAG_BLUE; else -> Pal.FLAG_RED }
                val dx = if (row >= 5) swing else 0
                c.set(ax + 1 + dx, ay + row, col); c.set(ax + 2 + dx, ay + row, col)
            }
        }
    }

    /**
     * The procession marching in along the road from the forest's edge to the fire ([p]: how far along): the banner first,
     * then the band (their brass glinting), then the village behind them.
     */
    private fun march(s: SceneCtx, state: GameState, roadHalf: Float, edgeAge: Age, p: Float, add: (Float, () -> Unit) -> Unit) {
        val land = s.land
        val start = land.roadOut(edgeAge, 6f)
        val end = 3.2f
        val lead = start - (start - end) * p
        val n = min(10, 3 + state.villagers)
        for (q in 0 until n) {
            val x = lead + q * 0.85f
            if (x > start + 0.5f) continue
            val y = land.roadY(x) + (if (q % 2 == 0) -0.35f else 0.35f) * min(1f, roadHalf)
            add(x + y) {
                s.newObject(Pal.OUTLINE)
                val ax = s.iso.ix(x, y); val ay = s.iso.iy(x, y, 0f)
                val bob = ((s.time * 6).toInt() + q) and 1
                s.effects.villager(ax, ay, 9100 + q * 31, true, flip = true, bob = bob)
                val c = s.canvas
                s.sprite(ax, ay) {
                    when {
                        // the banner on its pole, carried before them
                        q == 0 -> {
                            c.vline(ax - 2, ay - 12, ay - 3, Pal.WOOD_D)
                            for (row in 0 until 4) {
                                val col = when { row < 1 -> Pal.FLAG_WHITE; row < 3 -> Pal.FLAG_BLUE; else -> Pal.FLAG_RED }
                                c.set(ax - 3, ay - 11 + row, col); c.set(ax - 4, ay - 11 + row, col)
                            }
                        }
                        // the band: a horn at the mouth, glinting
                        q in 1..3 -> { c.set(ax - 2, ay - 5, Pal.GOLD); c.set(ax - 3, ay - 5 - bob, Pal.GOLD_L) }
                        else -> Unit
                    }
                }
            }
        }
    }

    // --- the sign at the road -------------------------------------------------------------------------------------

    /**
     * The village's sign beside the road where it comes into the clearing: a white board with a dark rim on two posts; as it
     * turns ([turn] 0..1) its board narrows to its edge and opens again, and from halfway it is the new one, its rim gold.
     */
    private fun sign(s: SceneCtx, x: Float, y: Float, turn: Float) {
        s.newObject(Pal.OUTLINE)
        val i = s.iso
        i.post(x - 0.15f, y, 0f, 9f, Pal.WOOD_D); i.post(x + 0.15f, y, 0f, 9f, Pal.WOOD_D)
        val ax = i.ix(x, y); val ay = i.iy(x, y, SIGN_TOP)
        val c = s.canvas
        val open = abs(cos(turn * PI)).toFloat()
        val half = max(0, (4 * open).roundToInt())
        val fresh = turn >= 0.5f
        s.sprite(ax, ay) {
            val rim = if (fresh) Pal.GOLD else Col.hex(0x2A2A30)
            for (dy in 0..4) for (dx in -half..half) {
                val edge = dy == 0 || dy == 4 || abs(dx) == half
                c.set(ax + dx, ay + dy, if (edge) rim else Pal.FLAG_WHITE)
            }
            // its name, as dark strokes (the label over the map says it)
            if (half >= 3) for (dx in -half + 2..half - 2) if (dx % 3 != 2) c.set(ax + dx, ay + 2, Col.hex(0x1E2A44))
        }
        if (fresh && turn < 1f) s.sparks(ax.toFloat(), ay - 2f * s.k, 4, 21)
    }

    private companion object {
        /** The likof's spruce: each tier's half width, from the top down. */
        val SPRUCE_ROWS = intArrayOf(0, 1, 1, 2, 2, 3)
        val SPRUCE_L = Col.hex(0x3E8A4A)
        val SPRUCE_D = Col.hex(0x245E34)

        /** The garlands' poles (nominal px). */
        const val POLE = 15f

        /** The banners' poles (nominal px). */
        const val BANNER_POLE = 20f

        /** Where the dance floor lies (world cells from the fire): in front of it, off the road; how big it is. */
        const val FLOOR_X = -1.2f
        const val FLOOR_Y = 1.15f
        const val FLOOR_W = 2.8f
        const val FLOOR_D = 1.5f

        /** What lies flat on the ground: before anything that stands (the renderer's FLAT). */
        const val FLAT_DEPTH = -1e6f + 1f

        /** Where the stalls stand along the road (world x, east of the fire). */
        val STALL_X = floatArrayOf(3.6f, 5.4f, 7.4f)

        /** The goods on each stall: apples and pears, mandolat and red hearts, honey and bread. */
        val GOODS = arrayOf(
            intArrayOf(Col.hex(0xD8342A), Col.hex(0xE8C040), Col.hex(0x6AAA3A)),
            intArrayOf(Col.hex(0xC8202A), Col.hex(0xF4EEDD), Col.hex(0xC8202A)),
            intArrayOf(Col.hex(0xE0A030), Col.hex(0xB07A3A), Col.hex(0xF0C860)),
        )

        /** How far inside the forest's edge the sign stands (nominal px), and its board's top (nominal px up). */
        const val SIGN_IN = 20f
        const val SIGN_TOP = 13f
    }
}
