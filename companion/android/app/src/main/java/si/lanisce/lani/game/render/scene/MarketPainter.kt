package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.Season
import si.lanisce.lani.game.scene.Poke
import si.lanisce.lani.game.scene.SceneSights
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Na tržnici: market day at the edge of the village square. A lane of cobbles runs between two rows of stalls under
 * striped awnings to the square, where the linden stands and the houses and the church's tower close the view. The near
 * stall on the left is the village's own: its counter with a linen cloth, the scale with its brass weights, cheese
 * wheels, loaves, a tray of eggs and jars of honey, strings of garlic and peppers under the awning, crates of apples and
 * vegetables in front of it (by season), a basket, a hen in a wicker coop. On the right Vinar Marko's cart with its barrel
 * and a crate of bottles, stacked crates, the market bell on its post; pigeons pecking in the lane. Farther stalls sell
 * fruit, embroidery and wool socks, flowers and pottery. Snow lies on the awnings in winter, lanterns burn after dusk.
 *
 * A dialog can make it a busy day ("bustle": shoppers crowding the lane), ring the market bell ("bell") and send the
 * pigeons up ("pigeons": how far they have flown). Taps: the scale's beam tips and swings (then the other way, a weight
 * hopping), an apple rolls off its crate over the cobbles (then two), the hen flaps in its coop (then squawks, feathers
 * flying).
 *
 * The stall's seller stands by it ("stall"), a buyer in the lane before it ("buyer"), someone by the cart ("cart"): the
 * place where a trade could be bargained over.
 *
 * Closer up ([detail] 2 and 3) the same market is drawn finer: the cloth's check, the scale's pans and chains, the rings
 * on the weights, the eggs one by one, the cheese's rind and holes, the jars' caps, the wicker, the wheel's spokes.
 */
internal class MarketPainter : VistaPainter() {
    override val art = "market"
    override val horizon = 58
    override val pokes = listOf(
        Poke("scale", listOf(2.2, 2.6), rest = 2.0), Poke("apples", listOf(3.0, 3.4), rest = 2.0), Poke("hen", listOf(1.6, 2.4), rest = 2.0),
    )

    /** Picture pixels per scene canvas pixel. */
    private val z: Int get() = detail

    // ------------------------------------------------------------------ the plan, in metres
    /** A stall along the lane: from depth [d0] to [d1], its counter's front toward the lane at side [front] (the back [BACK_W] m farther out). */
    private class Stall(val d0: Float, val d1: Float, val left: Boolean, val front: Float, val awn: Int, val goods: Int)

    private val stalls = listOf(
        Stall(15.4f, 18.2f, true, -3.3f, Col.hex(0x3E8A3A), 1),
        Stall(16.8f, 19.6f, false, 3.4f, Pal.FLAG_BLUE, 0),
        Stall(19.6f, 22.4f, true, -3.3f, Col.hex(0xE8B830), 2),
        Stall(21.0f, 23.8f, false, 3.4f, Pal.FLAG_RED, 3),
        Stall(23.8f, 26.4f, true, -3.3f, Pal.FLAG_BLUE, 0),
        Stall(25.2f, 27.8f, false, 3.4f, Col.hex(0x3E8A3A), 1),
    )
    private val lindenX = -7.5f; private val lindenD = 26f

    private val items = ArrayList<Pair<Float, () -> Unit>>()

    override fun paint() {
        setup()
        val z = z
        val lit = env.windows > 0.35f
        vistaSky(3, 71, moonX = ox + 190 * z, moonY = oy + 16 * z)
        hills()
        ground { x, d, px, py -> cobbles(x, d, px, py) }

        items.clear()
        // the square's end: the church's tower over the roofs, the houses, the linden; a house along the right
        items.add(62f to { prop { church(lit) } })
        for ((i, hs) in HOUSES.withIndex()) items.add(hs[1] to { prop { houseFront(hs[0], hs[2], hs[1], i, lit) } })
        items.add(SIDE_D1 to { prop { sideHouse(lit) } })
        items.add(lindenD to { groundShadow(lindenX + 0.8f, lindenD + 0.5f, 3.5f, 0.8f); prop(Pal.OUTLINE_TREE) { linden() } })
        // the stalls along the lane, far to near
        for ((i, st) in stalls.withIndex()) items.add(st.d1 to { prop { stall(st, i + 1, lit) } })
        items.add(15.6f to { thing("bell", slop = 1) { bellPost() } })
        items.add(11.7f to { groundShadow(6.95f, 11.9f, 0.6f); thing("crate") { crateStack(6.75f, 11.5f) } })
        items.add(15.4f to { if (fx("pigeons") == null || fxOn("pigeons") < 0.02f) thing("pigeon", slop = 2) { pigeons(0.9f, 15.4f, 0f) } })
        // the village's own stall, facing us on the left, and what is on it
        items.add(MAIN_BACK to {
            thing("stall") { frontStall(lit) }
            thing("stall", outline = 0) { awning(lit) }
            thing("honey", slop = 1) { honey(-6.75f, GOODS_D) }
            thing("eggs", slop = 1) { eggs(-5.95f, GOODS_D) }
            thing("bread", slop = 1) { bread(-5.15f, GOODS_D) }
            thing("cheese", slop = 1) { cheese(-4.35f, GOODS_D) }
            thing("weights", slop = 1) { weights(-3.72f, GOODS_D) }
            thing("scale", slop = 1) { scale(-3.08f, GOODS_D) }
        })
        // Marko's cart on the right, side on
        items.add(CART_D + 1.3f to { cartBody() })
        // in front of the stall: a basket, the crates of apples and vegetables, the hen in its coop
        items.add(11.4f to { groundShadow(-6.6f, 11.4f, 0.35f); thing("basket", slop = 1) { basket(-6.6f, 11.4f) } })
        items.add(11.15f to { thing("apples") { appleCrate(-5.6f, 11.0f) } })
        items.add(11.0f to { thing("vegetables") { vegCrate(-4.55f, 10.85f) } })
        items.add(11.2f to { groundShadow(-3.55f, 11.2f, 0.35f); thing("hen", slop = 1) { henCoop(-3.55f, 11.2f) } })
        for (p in peopleAt("stall")) items.add(12.0f to { groundShadow(-2.0f, 12.0f, 0.45f); person(p, gx(-2.0f, 12.0f).toInt(), gy(12.0f).toInt(), flip = false) })
        for (p in peopleAt("buyer")) items.add(11.2f to { groundShadow(-0.55f, 11.2f, 0.45f); person(p, gx(-0.55f, 11.2f).toInt(), gy(11.2f).toInt(), flip = true) })
        for (p in peopleAt("cart")) items.add(11.0f to { groundShadow(1.9f, 11.0f, 0.45f); person(p, gx(1.9f, 11.0f).toInt(), gy(11.0f).toInt(), flip = true) })
        nearEdge()
        bustle(fxOn("bustle"))
        items.sortByDescending { it.first }
        for ((_, draw) in items) draw()

        // the pigeons up in the air, over everything
        val flown = fx("pigeons")
        if (flown != null && flown >= 0.02f) thing("pigeon", slop = 2) { pigeons(0.9f, 15.4f, flown) }
        apples()
        bellRing()
        s.fx { s.effects.leaves(env.season, env.month) }
    }

    // ------------------------------------------------------------------ far away and the ground

    private fun hills() {
        val z = z
        val season = env.season
        ridge(hz - 8f * z, 18f * z, 0.012f, 711, if (season == Season.WINTER) Col.hex(0xB0BED6) else Col.hex(0x6A78A2), 0.36f, snowLine = 0.5f, sharp = true)
        val mid = ridge(hz - z.toFloat(), 10f * z, 0.017f, 713, when (season) { Season.WINTER -> Col.hex(0xC6D2E2); Season.AUTUMN -> Col.hex(0x7E7A46); else -> Col.hex(0x4E7A48) }, 0.24f)
        treeline(mid, 6 * z, 2.2f * z, if (season == Season.WINTER) Col.hex(0x6A7A8A) else Col.hex(0x2E5A36), 0.22f, 715)
    }

    /** The cobbles of the lane and the square: rounded stones in rows, joints, moss by the stalls; snow trampled in winter. */
    private fun cobbles(x: Float, d: Float, px: Int, py: Int): Int {
        val size = 0.26f
        val rowV = d / size; val r = floor(rowV).toInt()
        val u = (x + (r and 1) * size * 0.5f) / size; val ci = floor(u).toInt()
        val stonePx = size * pm(d); val rowPx = F * EYE / (d * d) * size
        val seed = Noise.hash(ci, r, 17)
        var col = when ((seed ushr 4) % 5) { 0 -> Pal.COBBLE_L; 1, 2 -> Pal.COBBLE_M; 3 -> Col.mix(Pal.COBBLE_M, Pal.DIRT_L, 0.3f); else -> Col.mix(Pal.COBBLE_D, Pal.COBBLE_M, 0.5f) }
        if (stonePx >= 2.5f && rowPx >= 2f) {
            val fu = (u - ci) * stonePx; val fv = (rowV - r) * rowPx
            val gv = rowPx - fv
            col = when {
                fu < 1f || fv < 1f -> Pal.COBBLE_D
                gv < 1.6f && stonePx > 5f -> Col.mix(col, Col.hex(0xFFFFFF), 0.22f)
                fv < 2f && stonePx > 5f -> Col.mix(col, Pal.COBBLE_D, 0.3f)
                Noise.rnd(px, py, 18) < 0.06f -> Col.mix(col, Pal.COBBLE_D, 0.3f)
                else -> col
            }
        } else if (Dither.at(px, py) < 0.3f) col = Pal.COBBLE_D
        // a little straw and a cabbage leaf here and there on market day; moss by the stalls
        if (abs(x) > 2.2f && !env.snow && Noise.rnd(px / z, py / z, 19) < 0.05f) col = env.grass[1]
        if (!env.snow && d < 30f && Noise.rnd(px / z, py / z, 20) < 0.006f) col = Pal.HAY_L
        if (env.snow) {
            // trampled down the middle of the lane, white by the stalls
            val trodden = abs(x - 0.3f) < 1.4f + Noise.v1(d * 0.8f, 21) * 0.6f
            col = if (trodden) (if (Noise.v2(x * 2f, d * 2f, 22) < 0.45f) col else Pal.SNOW_M) else if (Noise.rnd(px, py, 23) < 0.15f) Pal.SNOW_M else Pal.SNOW_L
        }
        return hazy(col, d)
    }

    // ------------------------------------------------------------------ the square's end

    /** The church behind the houses: its white tower with the belfry and the clock, the spire, a gold cross; the nave's roof. */
    private fun church(lit: Boolean) {
        val d = 62f; val x = 6f
        val p = pm(d)
        val wall = hazy(Pal.WALL_L, d); val wallD = hazy(Pal.WALL_D, d)
        val tw = 3.6f
        val tl = gx(x - tw / 2f, d); val tr = gx(x + tw / 2f, d)
        val top = hy(d, 19f)
        // the nave's roof behind, to the right
        c.polyBegin(); c.polyAdd(tr, hy(d, 7f)); c.polyAdd(gx(x + 9f, d), hy(d, 7f)); c.polyAdd(gx(x + 8f, d + 4f), hy(d + 4f, 11f)); c.polyAdd(tr, hy(d + 4f, 11f))
        c.polyFill(hazy(if (env.snow) Pal.SNOW_M else Pal.ROOF_M, d))
        for (y in max(top.toInt(), c.top) until min(gy(d).toInt() + 1, c.bottom)) for (xx in max(tl.toInt(), c.left) until min(tr.toInt() + 1, c.right)) {
            val hm = (gy(d) - y) / p
            c.set(xx, y, if (xx >= tr.toInt() - max(1, (0.5f * p).toInt())) wallD else if (abs(hm - 13.5f) < 0.15f) wallD else wall)
        }
        // the belfry, its bell, the clock face
        val bx = gx(x, d)
        c.fillRect((bx - 0.6f * p).toInt(), hy(d, 17.8f).toInt(), max(1, (1.2f * p).toInt()), max(1, (1.6f * p).toInt()), hazy(Col.hex(0x2A2226), d))
        c.set(bx.toInt(), hy(d, 16.8f).toInt(), Pal.GOLD)
        c.fillCircle(bx, hy(d, 15f), max(1f, 0.7f * p), Pal.GOLD)
        if (p > 2.5f) c.fillCircle(bx, hy(d, 15f), 0.5f * p, if (lit) Pal.WINDOW_LIT else Col.hex(0xF4F2EA))
        // the spire
        val sh = 6f
        val ys = hy(d, 19f)
        for (j in 0 until (sh * p).toInt()) {
            val f = j / (sh * p)
            val half = (tw / 2f + 0.2f) * p * (1f - f)
            c.hline((bx - half).toInt(), (bx + half).toInt(), (ys - j).toInt(), if (env.snow && j % 3 == 0) Pal.SNOW_M else hazy(Pal.SLATE_M, d))
        }
        val tip = (ys - sh * p).toInt()
        c.vline(bx.toInt(), tip - (1.2f * p).toInt(), tip, Pal.GOLD)
        c.hline((bx - 0.4f * p).toInt(), (bx + 0.4f * p).toInt(), tip - (0.8f * p).toInt(), Pal.GOLD)
    }

    /** A house front closing the square, [w] m wide from side [x0] at depth [d]: two floors, windows with green shutters, a door, its tiled roof; lit windows at night. */
    private fun houseFront(x0: Float, hw: Float, d: Float, seed: Int, lit: Boolean) {
        val p = pm(d)
        val wallCol = when (seed % 3) { 0 -> Pal.WALL_L; 1 -> Pal.OCHRE_L; else -> Col.hex(0xF4E2DA) }
        val wall = hazy(wallCol, d); val wallD = hazy(Col.scale(wallCol, 0.86f), d)
        val eave = 6.2f
        val xl = gx(x0, d); val xr = gx(x0 + hw, d)
        for (y in max(hy(d, eave).toInt(), c.top) until min(gy(d).toInt() + 1, c.bottom)) for (x in max(xl.toInt(), c.left) until min(xr.toInt() + 1, c.right)) {
            val hm = (gy(d) - y) / p
            val xm = sideAt(x, d) - x0
            var col = if (hm < 0.5f) hazy(Pal.STONE_M, d) else if (x == xr.toInt()) wallD else wall
            // windows in bays, shutters, the door in the middle bay
            val bay = xm / 2.3f; val bi = floor(bay).toInt(); val bf = (bay - bi) * 2.3f
            val door = bi == 1 && bf in 0.7f..1.6f && hm < 2.2f
            val win = !door && bf in 0.8f..1.5f && (hm in 1.1f..2.3f || hm in 3.9f..5.1f) && xm < hw - 0.5f
            val shutter = !door && (bf in 0.55f..0.8f || bf in 1.5f..1.75f) && (hm in 1.1f..2.3f || hm in 3.9f..5.1f) && xm < hw - 0.5f
            val on = lit && Noise.rnd(bi, (hm / 2.8f).toInt(), seed + 31) < 0.7f
            c.penEmissive = win && on
            col = when {
                door -> hazy(Pal.DOOR, d)
                win -> if (on) Pal.WINDOW_LIT else hazy(if (bf < 1f) Pal.GLASS_HI else Pal.GLASS, d)
                shutter -> hazy(Pal.SHUTTER, d)
                else -> col
            }
            c.set(x, y, col)
            c.penEmissive = false
        }
        if (lit) s.light((xl + xr) / 2f, hy(d, 3f), 30f, 0.3f)
        // the roof: its front slope rising away, courses of tiles, snow in winter; a chimney
        val roof = hazy(if (env.snow) Pal.SNOW_L else Pal.ROOF_L, d); val roofD = hazy(if (env.snow) Pal.SNOW_D else Pal.ROOF_M, d)
        val ey = hy(d - 0.4f, eave - 0.2f); val ry = hy(d + 4f, eave + 3.4f)
        c.polyBegin()
        c.polyAdd(gx(x0 - 0.4f, d - 0.4f), ey); c.polyAdd(gx(x0 + hw + 0.4f, d - 0.4f), ey)
        c.polyAdd(gx(x0 + hw + 0.4f, d + 4f), ry); c.polyAdd(gx(x0 - 0.4f, d + 4f), ry)
        c.polyFill { _, py -> if (((py - ry) / max(1f, 0.5f * z)).toInt() % 2 == 0) roof else roofD }
        c.hline(gx(x0 - 0.4f, d - 0.4f).toInt(), gx(x0 + hw + 0.4f, d - 0.4f).toInt(), ey.toInt(), hazy(Pal.ROOF_D, d))
        val cx = gx(x0 + hw * 0.7f, d + 3f)
        c.fillRect(cx.toInt(), (ry - 0.8f * p).toInt(), max(1, (0.5f * p).toInt()), max(2, (1.6f * p).toInt()), hazy(Pal.STONE_M, d))
        if (s.hour in 6f..10f || s.hour in 17f..22f || env.season == Season.WINTER) s.smoke(cx + 0.25f * p, ry - 0.9f * p, 0.35f, seed + 41)
    }

    /**
     * A house along the right of the square, its front toward the lane in perspective: plaster over a stone socle, windows
     * with green shutters on two floors, geraniums in summer, a door, the eaves and the tiled roof rising away from the
     * square; lit windows at night.
     */
    private fun sideHouse(lit: Boolean) {
        val xw = SIDE_X; val d0 = SIDE_D0; val d1 = SIDE_D1
        val top = 6.2f
        val summer = env.month in 5..10
        val wall = Pal.OCHRE_L; val wallD = Pal.OCHRE_M
        // the gable end toward us
        val pg = pm(d0); val by = gy(d0)
        c.polyBegin(); c.polyAdd(gx(xw, d0), by); c.polyAdd(gx(xw + 5f, d0), by); c.polyAdd(gx(xw + 5f, d0), by - top * pg)
        c.polyAdd(gx(xw + 2.5f, d0), by - (top + 3.2f) * pg); c.polyAdd(gx(xw, d0), by - top * pg)
        c.polyFill { px, py -> if (py > by - 0.5f * pg) hazy(Pal.STONE_M, d0) else hazy(if (Noise.rnd(px, py, 141) < 0.06f) wallD else Col.mix(wall, wallD, 0.35f), d0) }
        // its windows: two on each floor, one up in the gable, shutters, lit at night
        for ((wxm, whm) in listOf(1.0f to 1.1f, 3.1f to 1.1f, 1.0f to 3.9f, 3.1f to 3.9f, 2.05f to 6.6f)) {
            val x0 = gx(xw + wxm, d0); val x1 = gx(xw + wxm + 0.85f, d0)
            val y0 = by - (whm + 1.2f) * pg; val y1 = by - whm * pg
            val on = lit && (wxm + whm).toInt() % 3 != 0
            if (on) glow { c.fillRect(x0.toInt(), y0.toInt(), max(1, (x1 - x0).toInt()), max(1, (y1 - y0).toInt()), Pal.WINDOW_LIT) }
            else c.fillRect(x0.toInt(), y0.toInt(), max(1, (x1 - x0).toInt()), max(1, (y1 - y0).toInt()), hazy(Pal.GLASS, d0))
            if (whm < 6f) {
                c.fillRect((x0 - 0.3f * pg).toInt(), y0.toInt(), max(1, (0.3f * pg).toInt()), max(1, (y1 - y0).toInt()), hazy(Pal.SHUTTER, d0))
                c.fillRect(x1.toInt(), y0.toInt(), max(1, (0.3f * pg).toInt()), max(1, (y1 - y0).toInt()), hazy(Pal.SHUTTER, d0))
                if (env.month in 5..10) c.fillRect((x0 - 0.1f * pg).toInt(), y1.toInt(), max(1, (x1 - x0 + 0.2f * pg).toInt()), max(1, (0.2f * pg).toInt()), Pal.GERANIUM)
            }
        }
        // the front along the square
        c.polyBegin()
        c.polyAdd(gx(xw, d0), gy(d0)); c.polyAdd(gx(xw, d1), gy(d1)); c.polyAdd(gx(xw, d1), hy(d1, top)); c.polyAdd(gx(xw, d0), hy(d0, top))
        c.polyFill { px, py ->
            val d = F * xw / max(1f, px + 0.5f - vx)
            val hm = (gy(d) - (py + 0.5f)) / pm(d)
            val along = d - d0
            val bay = (along - 0.8f) / 2.7f; val bi = floor(bay).toInt(); val bf = (bay - bi) * 2.7f
            val inBays = bay >= 0f && along < d1 - d0 - 0.6f
            val door = inBays && bi == 1 && bf in 0.7f..1.6f && hm < 2.3f
            val winZ = hm in 1.0f..2.3f || hm in 3.8f..5.1f
            val win = inBays && !door && bf in 0.8f..1.5f && winZ
            val shutter = inBays && !door && (bf in 0.52f..0.8f || bf in 1.5f..1.78f) && winZ
            val sill = inBays && !door && bf in 0.7f..1.6f && (abs(hm - 0.95f) < 0.12f || abs(hm - 3.75f) < 0.12f)
            val on = lit && Noise.rnd(bi, (hm / 2.8f).toInt(), 142) < 0.7f
            c.penEmissive = win && on
            val col = when {
                hm < 0.45f -> if (Noise.rnd(px, py, 143) < 0.3f) Pal.STONE_D else Pal.STONE_M
                door -> if (bf < 0.78f || bf > 1.52f || hm > 2.2f) Pal.WOOD_X else Pal.DOOR
                sill && summer -> if (Noise.rnd(px, py, 144) < 0.6f) Pal.GERANIUM else Pal.LEAF
                sill -> Pal.WOOD_M
                win -> if (on) Pal.WINDOW_LIT else if (bf < 1.0f) Pal.GLASS_HI else Pal.GLASS
                shutter -> if (((hm * 6f).toInt() and 1) == 0) Pal.SHUTTER else Col.scale(Pal.SHUTTER, 0.8f)
                hm > top - 0.3f -> wallD
                else -> if (Noise.rnd(px, py, 145) < 0.05f) wallD else wall
            }
            val out = hazy(col, d)
            if (win && on) Pal.WINDOW_LIT else out
        }
        c.penEmissive = false
        // the roof rising away from the square, courses of tiles, snow in winter
        val roof = if (env.snow) Pal.SNOW_L else Pal.ROOF_L; val roofD = if (env.snow) Pal.SNOW_D else Pal.ROOF_M
        c.polyBegin()
        c.polyAdd(gx(xw - 0.4f, d0 - 0.4f), hy(d0 - 0.4f, top - 0.1f)); c.polyAdd(gx(xw - 0.4f, d1), hy(d1, top - 0.1f))
        c.polyAdd(gx(xw + 2.5f, d1), hy(d1, top + 3.2f)); c.polyAdd(gx(xw + 2.5f, d0 - 0.4f), hy(d0 - 0.4f, top + 3.2f))
        c.polyFill { px, py -> hazy(if (((px - gx(xw, d0).toInt()) / max(1, 2 * detail)) % 2 == 0) roof else Col.mix(roof, roofD, 0.5f), 20f) }
        if (lit) s.light(gx(xw, (d0 + d1) / 2f), hy((d0 + d1) / 2f, 3f), 40f, 0.35f)
    }

    /** The linden on the square: a thick trunk, a big round crown in clumps; flowering in June, gold in autumn, bare in winter. */
    private fun linden() {
        val d = lindenD; val p = pm(d)
        val bx = gx(lindenX, d); val by = gy(d)
        val tw = 0.9f * p
        c.fillRect((bx - tw / 2).toInt(), hy(d, 4.5f).toInt(), max(1, tw.toInt()), (by - hy(d, 4.5f)).toInt() + 1, hazy(Pal.LOG_D, d))
        c.vline((bx - tw / 2).toInt(), hy(d, 4.5f).toInt(), by.toInt(), hazy(Pal.LOG_M, d))
        val (dk, md, lt) = when (env.season) {
            Season.AUTUMN -> Triple(Col.hex(0x9A6A1E), Col.hex(0xC8902E), Col.hex(0xE8C050))
            Season.WINTER -> Triple(Col.hex(0x5A4E46), Col.hex(0x6E6258), Col.hex(0x8A7E70))
            Season.SPRING -> Triple(Col.hex(0x3E7A34), Col.hex(0x62A248), Col.hex(0x9ACC66))
            else -> Triple(Col.hex(0x2A5E30), Col.hex(0x3E7E3A), Col.hex(0x68A44C))
        }
        val sunLeft = (sunAt()?.first ?: vx) < bx
        for (k in 0 until 9) {
            val a = k * 2.4f
            val cx = bx + sin(a) * 2.6f * p; val cy = hy(d, 8.5f + cos(a) * 1.6f + (if (k == 8) 2f else 0f))
            val rr = (2.6f + Noise.rnd(k, 51) * 0.8f) * p
            for (y in max((cy - rr).toInt(), c.top)..min((cy + rr).toInt(), c.bottom - 1)) for (x in max((cx - rr).toInt(), c.left)..min((cx + rr).toInt(), c.right - 1)) {
                val dx = (x + 0.5f - cx) / rr; val dy = (y + 0.5f - cy) / rr
                val q = dx * dx + dy * dy
                if (q > 1f || (q > 0.8f && Dither.at(x, y) < 0.35f)) continue
                if (env.season == Season.WINTER && Noise.rnd(x / z, y / z, 52) < 0.5f) continue
                val l = (if (sunLeft) -dx else dx) * 0.5f - dy * 0.7f + (Noise.rnd(x / z, y / z, 53) - 0.5f) * 0.35f
                var col = if (l > 0.4f) lt else if (l > -0.2f) md else dk
                if (env.month == 6 && Noise.rnd(x / z, y / z, 54) < 0.05f) col = Col.hex(0xF0E08A)
                if (env.snow && dy < -0.3f && Noise.rnd(x, y, 55) < 0.4f) col = Pal.SNOW_L
                c.set(x, y, hazy(col, d))
            }
        }
    }

    // ------------------------------------------------------------------ the stalls

    /**
     * A stall along the lane: its back cloth, the counter of bare boards with its end toward us and its front toward the
     * lane, the goods on it, the posts, the striped awning sloping down to the lane with its scalloped edge, a lantern lit
     * after dusk.
     */
    private fun stall(st: Stall, seed: Int, lit: Boolean) {
        val z = z
        val sgn = if (st.left) -1f else 1f
        val xf = st.front; val xb = st.front + sgn * BACK_W
        val top = 0.95f
        val d0 = st.d0; val d1 = st.d1
        val wood = Pal.WOOD_M; val woodD = Pal.WOOD_D; val woodL = Pal.WOOD_L
        // the back cloth hanging from the awning's back rail
        val cloth = Col.mix(st.awn, Pal.CANVAS_L, 0.55f)
        quad(xb + sgn * 0.3f, d0, 0.2f, xb + sgn * 0.3f, d1, 0.2f, xb + sgn * 0.3f, d1, 2.9f, xb + sgn * 0.3f, d0, 2.9f) { px, py ->
            if (((px / max(1, 2 * z)) % 3) == 0) Col.scale(cloth, 0.86f) else cloth
        }
        // the back posts
        post(xb + sgn * 0.3f, d0, 3.0f); post(xb + sgn * 0.3f, d1, 3.0f)
        // the counter: its top, the front toward the lane, the end toward us
        quad(xb, d0, top, xf, d0, top, xf, d1, top, xb, d1, top) { px, py -> plank(px, py, woodL, wood) }
        quad(xf, d0, 0f, xf, d1, 0f, xf, d1, top, xf, d0, top) { px, py -> plank(px, py, wood, woodD) }
        quad(xb, d0, 0f, xf, d0, 0f, xf, d0, top, xb, d0, top) { _, _ -> Col.scale(woodD, 0.9f) }
        // the goods on the counter
        goods(st, seed)
        // the front posts
        post(xf - sgn * 0.25f, d0, 2.55f); post(xf - sgn * 0.25f, d1, 2.55f)
        // the awning: stripes down the slope, the scalloped edge over the lane; snow on it in winter
        val ex = xf - sgn * 0.35f; val bxw = xb + sgn * 0.5f
        val n = max(4, ((d1 - d0 + 0.4f) / 0.36f).toInt())
        val white = Pal.FLAG_WHITE
        for (k in 0 until n) {
            val da = d0 - 0.2f + (d1 - d0 + 0.4f) * k / n; val db = d0 - 0.2f + (d1 - d0 + 0.4f) * (k + 1) / n
            val col = if (k % 2 == 0) st.awn else white
            quad(ex, da, 2.55f, bxw, da, 3.05f, bxw, db, 3.05f, ex, db, 2.55f) { px, py ->
                var cc = col
                val out = abs(sideAt(px, (da + db) / 2f) - ex) / BACK_W
                if (env.snow && out > 0.35f + 0.3f * Noise.v1(py * 0.3f / z, seed)) cc = if (Noise.rnd(px, py, seed) < 0.12f) Pal.SNOW_M else Pal.SNOW_L
                else if (Dither.at(px, py) < 0.12f) cc = Col.scale(cc, 0.94f)
                cc
            }
            // the scallop
            val mid = (da + db) / 2f
            val sx0 = gx(ex, da); val sx1 = gx(ex, db); val sy = hy(mid, 2.55f)
            val drop = max(1.5f, 0.2f * pm(mid))
            for (x in min(sx0, sx1).toInt()..max(sx0, sx1).toInt()) {
                val f = (x - min(sx0, sx1)) / max(1f, abs(sx1 - sx0))
                val dh = drop * (0.55f + 0.45f * sin(f * PI.toFloat()))
                for (y in sy.toInt()..(sy + dh).toInt()) c.set(x, y, if (y >= (sy + dh).toInt()) Col.scale(col, 0.75f) else Col.scale(col, 0.9f))
            }
        }
        // the lantern on the near front post, lit after dusk
        val lx0 = gx(xf - sgn * 0.25f, d0); val ly0 = hy(d0, 2.3f)
        val lp = pm(d0)
        c.fillRect((lx0 - 0.08f * lp).toInt(), ly0.toInt(), max(1, (0.16f * lp).toInt()), max(2, (0.25f * lp).toInt()), Col.hex(0x3A3432))
        if (lit) {
            glow { c.fillRect((lx0 - 0.05f * lp).toInt(), (ly0 + 0.05f * lp).toInt(), max(1, (0.1f * lp).toInt()), max(1, (0.14f * lp).toInt()), Pal.WINDOW_LIT) }
            s.light(lx0, ly0, 34f, 0.7f)
        }
    }

    /**
     * The village's own stall, facing us on the left: the canvas at its back with strings of garlic and red peppers hung
     * before it, the posts, the counter with its linen cloth (a red check along the hem) and its end toward the lane; the
     * goods on it are things of their own, the awning over it is [awning].
     */
    private fun frontStall(lit: Boolean) {
        val z = z
        val xl = MAIN_L; val xr = MAIN_R; val d0 = MAIN_FRONT; val d1 = MAIN_TOP_BACK
        // the canvas at the back, folds in it
        val cloth = Col.mix(Pal.FLAG_RED, Pal.CANVAS_L, 0.62f)
        quad(xl + 0.1f, MAIN_BACK, 0.3f, xr - 0.1f, MAIN_BACK, 0.3f, xr - 0.1f, MAIN_BACK, 2.9f, xl + 0.1f, MAIN_BACK, 2.9f) { px, _ ->
            val f = (sideAt(px, MAIN_BACK) * 3f).let { it - floor(it) }
            if (f < 0.2f) Col.scale(cloth, 0.84f) else if (f > 0.8f) Col.mix(cloth, Col.hex(0xFFFFFF), 0.12f) else cloth
        }
        // strings of garlic, red peppers, onions hanging from the back beam
        for ((k, sx) in floatArrayOf(-6.9f, -6.1f, -4.6f, -3.4f).withIndex()) {
            val dd = MAIN_BACK - 0.1f
            val pp = pm(dd)
            val bx = gx(sx, dd)
            var y = hy(dd, 2.45f); val y1 = hy(dd, 1.2f + 0.15f * (k % 2))
            c.vline(bx.toInt(), y.toInt(), y1.toInt(), Col.hex(0xB8A070))
            var j = 0
            while (y < y1) {
                val col = when (k) {
                    1 -> if (j % 2 == 0) Col.hex(0xC8281E) else Col.hex(0xA81E18)
                    2 -> if (j % 2 == 0) Col.hex(0xC88A3A) else Col.hex(0xA86A2A)
                    else -> if (j % 2 == 0) Col.hex(0xF0EAD8) else Col.hex(0xD8CCB0)
                }
                c.fillEllipse(bx + (if (j % 2 == 0) -0.5f else 0.5f) * z, y, max(0.9f, 0.07f * pp), max(0.9f, 0.08f * pp), col)
                y += max(1.4f, 0.12f * pp); j++
            }
        }
        // the back posts
        post(xl + 0.1f, MAIN_BACK, 2.95f); post(xr - 0.1f, MAIN_BACK, 2.95f)
        // the counter: its top, the end toward the lane, the front with the cloth hanging down
        quad(xl, d0, 0.95f, xr, d0, 0.95f, xr, d1, 0.95f, xl, d1, 0.95f) { px, py -> linen(px, py, true) }
        quad(xr, d0, 0f, xr, d1, 0f, xr, d1, 0.95f, xr, d0, 0.95f) { _, py -> if (((py / (2 * z)) % 3) == 0) Col.scale(Pal.WOOD_M, 0.8f) else Col.scale(Pal.WOOD_M, 0.9f) }
        quad(xl, d0, 0.05f, xr, d0, 0.05f, xr, d0, 0.95f, xl, d0, 0.95f) { px, py ->
            val hm = (gy(d0) - (py + 0.5f)) / pm(d0)
            linen(px, py, false, hm)
        }
        // the front posts
        post(xl + 0.1f, d0 - 0.15f, 2.45f); post(xr - 0.1f, d0 - 0.15f, 2.45f)
        if (lit) s.light(gx(xr - 0.3f, d0), hy(d0, 2.2f), 40f, 0.75f)
    }

    /** The village's stall's awning: red and white stripes from the front edge back up to the canvas, the scalloped valance, snow on it in winter; the lantern on the right post. */
    private fun awning(lit: Boolean) {
        val z = z
        val xl = MAIN_L - 0.3f; val xr = MAIN_R + 0.25f
        val df = MAIN_FRONT - 0.35f; val db = MAIN_BACK + 0.2f
        val n = 13
        for (k in 0 until n) {
            val xa = xl + (xr - xl) * k / n; val xb = xl + (xr - xl) * (k + 1) / n
            val col = if (k % 2 == 0) Pal.FLAG_RED else Pal.FLAG_WHITE
            val front = hy(df, 2.45f); val back = hy(db, 2.95f)
            quad(xa, df, 2.45f, xb, df, 2.45f, xb, db, 2.95f, xa, db, 2.95f) { px, py ->
                val v = (front - (py + 0.5f)) / max(1f, front - back) // 0 at the front edge .. 1 at the back
                if (env.snow && v > 0.3f + 0.25f * Noise.v1(px * 0.2f / z, 64)) (if (Noise.rnd(px, py, 63) < 0.12f) Pal.SNOW_M else Pal.SNOW_L)
                else if (Dither.at(px, py) < 0.1f) Col.scale(col, 0.93f) else col
            }
            // the valance: a scallop under each stripe
            val sx0 = gx(xa, df); val sx1 = gx(xb, df); val sy = hy(df, 2.45f)
            val drop = max(2f, 0.24f * pm(df))
            for (x in sx0.toInt() until sx1.toInt().coerceAtLeast(sx0.toInt() + 1)) {
                val f = (x + 0.5f - sx0) / max(1f, sx1 - sx0)
                val dh = drop * (0.5f + 0.5f * sin(f * PI.toFloat()))
                for (y in sy.toInt()..(sy + dh).toInt()) c.set(x, y, if (y >= (sy + dh).toInt()) Col.scale(col, 0.72f) else Col.scale(col, 0.92f))
            }
        }
        // the lantern hanging from the right front post
        val lx0 = gx(MAIN_R - 0.1f, MAIN_FRONT - 0.15f) + 2 * z; val ly0 = hy(MAIN_FRONT, 2.2f)
        val lp = pm(MAIN_FRONT)
        c.vline(lx0.toInt(), (ly0 - 0.2f * lp).toInt(), ly0.toInt(), Col.hex(0x3A3432))
        c.fillRect((lx0 - 0.08f * lp).toInt(), ly0.toInt(), max(2, (0.16f * lp).toInt()), max(3, (0.26f * lp).toInt()), Col.hex(0x3A3432))
        if (lit) glow { c.fillRect((lx0 - 0.05f * lp).toInt(), (ly0 + 0.05f * lp).toInt(), max(1, (0.1f * lp).toInt()), max(2, (0.16f * lp).toInt()), Pal.WINDOW_LIT) }
        else c.fillRect((lx0 - 0.05f * lp).toInt(), (ly0 + 0.05f * lp).toInt(), max(1, (0.1f * lp).toInt()), max(2, (0.16f * lp).toInt()), Pal.GLASS_HI)
    }

    /** A wooden post [h] m tall at ([x], [d]). */
    private fun post(x: Float, d: Float, h: Float) {
        val p = pm(d)
        val pw = max(detail.toFloat(), 0.1f * p)
        val bx = gx(x, d)
        val top = hy(d, h); val foot = gy(d)
        c.fillRect((bx - pw / 2).toInt(), top.toInt(), max(1, pw.toInt()), (foot - top).toInt() + 1, Pal.WOOD_D)
        if (pw >= 2f) c.vline((bx - pw / 2).toInt(), top.toInt(), foot.toInt(), Pal.WOOD_M)
    }

    /** Fills the quad of four points on the ground plane's world (side, depth, height) with [shader]. */
    private inline fun quad(
        x1: Float, d1: Float, h1: Float, x2: Float, d2: Float, h2: Float, x3: Float, d3: Float, h3: Float, x4: Float, d4: Float, h4: Float,
        crossinline shader: (Int, Int) -> Int,
    ) {
        c.polyBegin()
        c.polyAdd(gx(x1, d1), hy(d1, h1)); c.polyAdd(gx(x2, d2), hy(d2, h2)); c.polyAdd(gx(x3, d3), hy(d3, h3)); c.polyAdd(gx(x4, d4), hy(d4, h4))
        c.polyFill(shader)
    }

    /**
     * The linen cloth on the village's counter: [top], the part lying on it; else the part hanging down its front, [hm] m
     * above the cobbles: a band of red check along the hem, folds, finer closer up.
     */
    private fun linen(px: Int, py: Int, top: Boolean, hm: Float = 0f): Int {
        val z = z
        val base = if (top) Pal.CANVAS_L else Col.mix(Pal.CANVAS_L, Pal.CANVAS_M, 0.3f)
        if (top) return if (Noise.rnd(px, py, 61) < 0.05f) Pal.CANVAS_M else base
        val fold = (px / (5 * z)) % 4 == 0
        if (hm < 0.32f && hm > 0.08f) {
            val cx = (px / max(1, z + z / 2)) and 1; val cy = (py / max(1, z + z / 2)) and 1
            return if ((cx xor cy) == 0) Pal.GERANIUM else Col.mix(Pal.FLAG_WHITE, Pal.GERANIUM, 0.35f)
        }
        if (abs(hm - 0.38f) < 0.03f) return Pal.GERANIUM_D
        return if (fold) Col.scale(base, 0.9f) else base
    }

    /** Boards of a plain counter: seams every few pixels, a lit top edge. */
    private fun plank(px: Int, py: Int, light: Int, mid: Int): Int = if ((py / (2 * detail)) % 3 == 0) Col.mix(mid, Pal.WOOD_D, 0.3f) else if (Noise.rnd(px / 3, py, 62) < 0.1f) light else mid

    /** The goods of another stall, seen from afar: 0 fruit and vegetables, 1 embroidery and wool socks, 2 flowers and herbs, 3 pottery. */
    private fun goods(st: Stall, seed: Int) {
        val z = z
        val sgn = if (st.left) -1f else 1f
        val n = 6
        for (k in 0 until n) {
            val dd = st.d0 + 0.3f + (st.d1 - st.d0 - 0.6f) * k / (n - 1)
            val xx = st.front + sgn * 0.45f
            val p = pm(dd)
            val bx = gx(xx, dd); val by = hy(dd, 0.95f)
            val r = max(1f, 0.2f * p)
            when (st.goods) {
                0 -> {
                    val cols = fruitColors()
                    for (j in 0 until 5) c.fillEllipse(bx + (Noise.rnd(j, k, seed) - 0.5f) * 0.5f * p, by - r * 0.4f - (j / 3) * r * 0.6f, max(0.8f, r * 0.45f), max(0.8f, r * 0.4f), cols[(j + k) % cols.size])
                }
                1 -> {
                    val cloth = intArrayOf(Pal.FLAG_WHITE, Col.hex(0xC8392F), Col.hex(0x3A62A8), Col.hex(0xE8D8B0))
                    c.fillRect((bx - r).toInt(), (by - r * 0.6f).toInt(), max(1, (2 * r).toInt()), max(1, (r * 0.7f).toInt()), cloth[k % cloth.size])
                    if (z > 1) c.hline((bx - r).toInt(), (bx + r).toInt(), (by - r * 0.3f).toInt(), Pal.GERANIUM)
                }
                2 -> {
                    val bloom = intArrayOf(Col.hex(0xE8303A), Col.hex(0xF0D060), Col.hex(0xB08AD8), Col.hex(0xF4F0FA))
                    c.fillRect((bx - r * 0.5f).toInt(), (by - r).toInt(), max(1, r.toInt()), max(1, r.toInt()), Col.hex(0x8A5A32))
                    c.fillEllipse(bx, by - r * 1.3f, max(0.8f, r * 0.7f), max(0.8f, r * 0.5f), if (env.season == Season.WINTER) Col.hex(0x3A6A3A) else bloom[k % bloom.size])
                }
                else -> {
                    val clay = if (k % 2 == 0) Col.hex(0xB8683A) else Col.hex(0xC88A5A)
                    c.fillEllipse(bx, by - r * 0.8f, max(0.8f, r * 0.6f), max(1f, r * 0.8f), clay)
                    c.set(bx.toInt(), (by - r * 1.6f).toInt(), Col.scale(clay, 0.7f))
                }
            }
        }
        // crates under the counter, at its end
        val cx = gx(st.front + sgn * 0.4f, st.d0 - 0.2f); val cy = gy(st.d0 - 0.2f)
        val p = pm(st.d0)
        c.fillRect((cx - 0.3f * p).toInt(), (cy - 0.35f * p).toInt(), max(2, (0.6f * p).toInt()), max(2, (0.35f * p).toInt()), Pal.WOOD_M)
        c.hline((cx - 0.3f * p).toInt(), (cx + 0.3f * p).toInt() - 1, (cy - 0.35f * p).toInt(), Pal.WOOD_L)
    }

    /** The season's fruit and vegetables, as colours. */
    private fun fruitColors(): IntArray = when (env.month) {
        12, 1, 2 -> intArrayOf(Col.hex(0xC8392F), Col.hex(0xE8C040), Col.hex(0x8A5A32), Col.hex(0xE8E0C8))
        3, 4 -> intArrayOf(Col.hex(0x7AB452), Col.hex(0xC8392F), Col.hex(0xE8E0C8), Col.hex(0x5DAE3E))
        5, 6 -> intArrayOf(Col.hex(0xB01E28), Col.hex(0xE84A3A), Col.hex(0x5DAE3E), Col.hex(0xF0D060))
        7, 8 -> intArrayOf(Col.hex(0xE84A3A), Col.hex(0x6A3A8A), Col.hex(0xF0D060), Col.hex(0x5DAE3E))
        else -> intArrayOf(Col.hex(0xE07A1E), Col.hex(0x6A3A8A), Col.hex(0xE8C040), Col.hex(0xC8392F))
    }

    // ------------------------------------------------------------------ on the village's counter

    /**
     * The scale (tehtnica): a brass balance on the counter, its beam on a post, the two pans on their chains; poked, the
     * beam tips and swings back to level, then the other way with a weight hopping in its pan.
     */
    private fun scale(x: Float, d: Float) {
        val p = pm(d)
        val pk = poked("scale"); val a = pk?.age(t) ?: 0f
        val tilt = when {
            pk == null -> sin(t * 0.7).toFloat() * 0.02f
            pk.step == 0 -> 0.45f * exp(-a * 1.5f) * cos(a * 6.5f)
            else -> -0.6f * exp(-a * 1.2f) * cos(a * 5.5f)
        }
        val brass = Col.hex(0xC8962E); val brassL = Col.hex(0xF0D070); val brassD = Col.hex(0x7A5A1E)
        val bx = gx(x, d); val base = hy(d, 0.95f)
        // the foot and the post
        c.fillEllipse(bx, base - 0.02f * p, max(1.2f, 0.12f * p), max(0.6f, 0.04f * p), brassD)
        val pw = max(detail.toFloat(), 0.04f * p)
        val ptop = hy(d, 1.45f)
        c.fillRect((bx - pw / 2).toInt(), ptop.toInt(), max(1, pw.toInt()), (base - ptop).toInt(), brass)
        // the beam, tipped
        val half = 0.3f * p
        val lx = bx - half * cos(tilt); val ly = ptop - half * sin(tilt)
        val rx = bx + half * cos(tilt); val ry = ptop + half * sin(tilt)
        for (o in 0 until max(1, (0.03f * p).toInt())) c.line(lx.toInt(), ly.toInt() + o, rx.toInt(), ry.toInt() + o, brass)
        c.set(bx.toInt(), ptop.toInt() - 1, brassL)
        // the pans on their chains
        for ((ex, ey) in listOf(lx to ly, rx to ry)) {
            val py = ey + 0.2f * p
            c.line(ex.toInt(), ey.toInt(), (ex - 0.07f * p).toInt(), py.toInt(), brassD)
            c.line(ex.toInt(), ey.toInt(), (ex + 0.07f * p).toInt(), py.toInt(), brassD)
            c.fillEllipse(ex, py, max(1.2f, 0.11f * p), max(0.6f, 0.035f * p), brass)
            c.hline((ex - 0.1f * p).toInt(), (ex + 0.1f * p).toInt(), py.toInt(), brassL)
        }
        // a weight hopping in the right pan (the second tap)
        if (pk != null && pk.step == 1) {
            val hop = max(0f, sin(a * 7f)) * 0.08f * p * exp(-a)
            c.fillRect((rx - 0.03f * p).toInt(), (ry + 0.2f * p - 0.06f * p - hop).toInt(), max(1, (0.06f * p).toInt()), max(1, (0.05f * p).toInt()), brassD)
        }
    }

    /** The brass weights (uteži) in a row beside the scale, the biggest first: cylinders with a knob, rings on them closer up. */
    private fun weights(x: Float, d: Float) {
        val brass = Col.hex(0xB8862A); val brassL = Col.hex(0xF0D070); val brassD = Col.hex(0x6A4A1A)
        for (k in 0 until 4) {
            val dd = d + (k % 2) * 0.12f
            val p = pm(dd)
            val bx = gx(x + (k - 1.5f) * 0.14f, dd); val by = hy(dd, 0.95f)
            val r = max(1f, (0.07f - k * 0.01f) * p); val hh = max(1.5f, (0.12f - k * 0.018f) * p)
            c.fillRect((bx - r).toInt(), (by - hh).toInt(), max(1, (2 * r).toInt()), max(1, hh.toInt()), brass)
            c.vline((bx - r).toInt(), (by - hh).toInt(), by.toInt() - 1, brassL)
            c.vline((bx + r).toInt() - 1, (by - hh).toInt(), by.toInt() - 1, brassD)
            c.fillRect((bx - r * 0.4f).toInt(), (by - hh - max(1f, 0.03f * p)).toInt(), max(1, (0.8f * r).toInt()), max(1, (0.03f * p).toInt()), brassD)
        }
    }

    /** Two wheels of cheese (tolminc) on the counter, the top one cut: pale yellow inside with holes, a rind. */
    private fun cheese(x: Float, d: Float) {
        val p = pm(d)
        val bx = gx(x, d); val by = hy(d, 0.95f)
        val rind = Col.hex(0xC8A050); val rindD = Col.hex(0x9A7A3A); val inside = Col.hex(0xF4E08A)
        val r = 0.2f * p; val th = max(2f, 0.1f * p)
        for (k in 0 until 2) {
            val cy = by - th * (k + 0.5f)
            c.fillEllipse(bx, cy + th * 0.3f, r, max(1f, r * 0.35f), rindD)
            c.fillRect((bx - r).toInt(), (cy - th * 0.5f).toInt(), (2 * r).toInt(), max(1, th.toInt()), rind)
            c.fillEllipse(bx, cy - th * 0.5f, r, max(1f, r * 0.35f), if (k == 1) inside else Col.mix(rind, Col.hex(0xFFF0C8), 0.3f))
        }
        // the cut: a wedge out of the top wheel, holes closer up
        val ty = by - th * 2f
        c.fillRect(bx.toInt(), (ty - r * 0.3f).toInt(), max(1, (r * 0.7f).toInt()), max(1, (th + r * 0.3f).toInt()), inside)
        if (detail > 1) for (j in 0 until 4) c.set((bx + (0.2f + 0.15f * j) * r).toInt(), (ty + (j % 2) * th * 0.4f).toInt(), Col.hex(0xC8A850))
    }

    /** Round loaves of bread, golden brown, scored across the top, flour on them. */
    private fun bread(x: Float, d: Float) {
        for (k in 0 until 3) {
            val dd = d + (k % 2) * 0.14f - 0.05f; val xx = x + (k - 1) * 0.22f
            val p = pm(dd)
            val bx = gx(xx, dd); val by = hy(dd, 0.95f)
            val r = 0.14f * p
            c.fillEllipse(bx, by - r * 0.55f, max(1.2f, r), max(1f, r * 0.62f), Col.hex(0x9A5A26))
            c.fillEllipse(bx - r * 0.15f, by - r * 0.7f, max(1f, r * 0.8f), max(0.8f, r * 0.45f), Col.hex(0xC88A44))
            if (r > 2f) {
                c.line((bx - r * 0.4f).toInt(), (by - r * 0.8f).toInt(), (bx + r * 0.3f).toInt(), (by - r * 0.6f).toInt(), Col.hex(0x7A4420))
                c.set((bx - r * 0.3f).toInt(), (by - r * 0.95f).toInt(), Col.hex(0xF4E8D0))
            }
        }
    }

    /** A flat basket of eggs on straw: white and brown eggs one by one closer up. */
    private fun eggs(x: Float, d: Float) {
        val p = pm(d)
        val bx = gx(x, d); val by = hy(d, 0.95f)
        val w0 = 0.26f * p; val h0 = max(2f, 0.07f * p)
        c.fillEllipse(bx, by - h0 * 0.4f, w0, max(1f, h0), Col.hex(0x9A7040))
        c.fillEllipse(bx, by - h0, w0 * 0.9f, max(1f, h0 * 0.6f), Pal.HAY_M)
        for (j in 0 until 9) {
            val ex = bx + (Noise.rnd(j, 71) - 0.5f) * w0 * 1.4f; val ey = by - h0 - Noise.rnd(j, 72) * h0 * 0.8f
            val er = max(0.7f, 0.035f * p)
            c.fillEllipse(ex, ey, er, er * 1.25f, if (j % 3 == 0) Col.hex(0xD8A878) else Col.hex(0xF6F0E4))
        }
    }

    /** Jars of honey in a row, golden, their caps of checked cloth tied with string; the light through them. */
    private fun honey(x: Float, d: Float) {
        for (k in 0 until 4) {
            val dd = d + (k % 2) * 0.14f - 0.05f
            val p = pm(dd)
            val bx = gx(x + (k - 1.5f) * 0.15f, dd); val by = hy(dd, 0.95f)
            val jw = max(1.5f, 0.08f * p); val jh = max(2f, 0.2f * p)
            c.fillRect((bx - jw).toInt(), (by - jh).toInt(), max(1, (2 * jw).toInt()), max(1, jh.toInt()), Col.hex(0xD89020))
            c.vline((bx - jw).toInt(), (by - jh).toInt(), by.toInt() - 1, Col.hex(0xF4C850))
            c.fillRect((bx - jw - 0.3f).toInt(), (by - jh - max(1f, 0.04f * p)).toInt(), max(1, (2 * jw + 0.6f).toInt()), max(1, (0.05f * p).toInt()), if (k % 2 == 0) Pal.GERANIUM else Pal.FLAG_WHITE)
        }
    }

    // ------------------------------------------------------------------ on the ground by the stall

    /** A crate (zaboj) at ([x], [d]), [w] × [dp] m, [h] m tall: slats, lit on top and toward us. */
    private fun crate(x: Float, d: Float, w: Float, dp: Float, h: Float, base: Float = 0f) {
        val z = z
        val x0 = x - w / 2f; val x1 = x + w / 2f
        // the side toward the lane, the end toward us, the rim on top
        val side = if (x < 0f) x1 else x0
        quad(side, d, base, side, d + dp, base, side, d + dp, base + h, side, d, base + h) { _, py -> if (((py / max(1, z)) % 3) == 0) Pal.WOOD_D else Col.scale(Pal.WOOD_M, 0.88f) }
        quad(x0, d, base, x1, d, base, x1, d, base + h, x0, d, base + h) { _, py -> if (((py / max(1, z)) % 3) == 0) Pal.WOOD_D else Pal.WOOD_M }
        quad(x0, d, base + h, x1, d, base + h, x1, d + dp, base + h, x0, d + dp, base + h) { _, _ -> Col.hex(0x5A3A22) }
    }

    /**
     * A crate of apples in front of the counter, red and yellow, heaped; poked, an apple rolls off over the cobbles (the
     * rolling one is drawn by [apples]).
     */
    private fun appleCrate(x: Float, d: Float) {
        crate(x, d, 0.55f, 0.45f, 0.38f)
        heap(x, d + 0.22f, 0.38f, 0.26f, intArrayOf(Col.hex(0xC8281E), Col.hex(0xE84A2A), Col.hex(0xE8C040), Col.hex(0x9A1E18)), 81, 0.06f)
    }

    /** A heap of round things (apples, potatoes, tomatoes) on a crate, [r] m each. */
    private fun heap(x: Float, d: Float, top: Float, w: Float, cols: IntArray, seed: Int, r: Float) {
        for (j in 0 until 16) {
            val dd = d + (Noise.rnd(j, seed) - 0.5f) * 0.3f
            val xx = x + (Noise.rnd(j, seed + 1) - 0.5f) * w * 1.6f
            val p = pm(dd)
            val bx = gx(xx, dd); val by = hy(dd, top + (j / 6) * r * 0.8f)
            val rr = max(0.8f, r * p)
            val col = cols[(Noise.rnd(j, seed + 2) * cols.size).toInt()]
            c.fillEllipse(bx, by - rr * 0.5f, rr, rr, col)
            if (rr >= 1.5f) c.set((bx - rr * 0.35f).toInt(), (by - rr * 0.9f).toInt(), Col.mix(col, Col.hex(0xFFFFFF), 0.5f))
        }
    }

    /** A crate of the season's vegetables: lettuce and radishes in spring, tomatoes and peppers in summer, cabbages and pumpkins in autumn, cabbages and turnips in winter. */
    private fun vegCrate(x: Float, d: Float) {
        crate(x, d, 0.5f, 0.45f, 0.34f)
        val p = pm(d + 0.2f)
        when (env.month) {
            3, 4, 5 -> {
                heap(x, d + 0.22f, 0.34f, 0.22f, intArrayOf(Col.hex(0x7AC452), Col.hex(0x5AA43A), Col.hex(0xA8D878)), 91, 0.08f)
                for (j in 0 until 3) c.fillEllipse(gx(x - 0.1f + j * 0.1f, d + 0.1f), hy(d + 0.1f, 0.42f), max(0.8f, 0.03f * p), max(0.8f, 0.03f * p), Col.hex(0xD83A5A))
            }
            6, 7, 8 -> heap(x, d + 0.22f, 0.34f, 0.22f, intArrayOf(Col.hex(0xE0301E), Col.hex(0xC8281E), Col.hex(0xE8B020), Col.hex(0x3E8A2E)), 92, 0.055f)
            9, 10, 11 -> {
                heap(x, d + 0.22f, 0.34f, 0.22f, intArrayOf(Col.hex(0x9ACB6A), Col.hex(0x7AAE52), Col.hex(0xB8D890)), 93, 0.09f)
                c.fillEllipse(gx(x + 0.05f, d + 0.25f), hy(d + 0.25f, 0.5f), max(1f, 0.14f * p), max(1f, 0.1f * p), Col.hex(0xE07A1E))
            }
            else -> heap(x, d + 0.22f, 0.34f, 0.22f, intArrayOf(Col.hex(0xB8D890), Col.hex(0xE8E0D8), Col.hex(0xA05AA0), Col.hex(0x9ACB6A)), 94, 0.07f)
        }
    }

    /** A wicker basket with a handle, filled with the season's fruit: cherries, plums, pears, grapes, walnuts. */
    private fun basket(x: Float, d: Float) {
        val z = z
        val p = pm(d)
        val bx = gx(x, d); val by = gy(d)
        val rw = 0.24f * p; val rh = 0.28f * p
        val wick = Col.hex(0xB8864A); val wickD = Col.hex(0x8A5E30)
        for (y in (by - rh).toInt()..by.toInt()) {
            val v = (by - y) / rh
            val half = rw * (0.8f + 0.2f * v)
            for (xx in (bx - half).toInt()..(bx + half).toInt()) c.set(xx, y, if (((xx / max(1, z)) + (y / max(1, z))) % 2 == 0) wick else wickD)
        }
        val fruit = when (env.month) {
            5, 6 -> intArrayOf(Col.hex(0xB01E28), Col.hex(0x8A1420))
            7, 8 -> intArrayOf(Col.hex(0x5A2A7A), Col.hex(0x7A3A9A))
            9, 10 -> intArrayOf(Col.hex(0x6A3A8A), Col.hex(0x8AB450), Col.hex(0xD8C050))
            else -> intArrayOf(Col.hex(0x9A7048), Col.hex(0x7A5230))
        }
        for (j in 0 until 7) c.fillEllipse(bx + (Noise.rnd(j, 101) - 0.5f) * rw * 1.4f, by - rh - Noise.rnd(j, 102) * 0.06f * p, max(0.7f, 0.045f * p), max(0.7f, 0.045f * p), fruit[j % fruit.size])
        // the handle
        val hr = rw * 0.9f
        for (a in 0..16) {
            val f = a / 16f * PI.toFloat()
            c.set((bx - cos(f) * hr).toInt(), (by - rh - sin(f) * 0.28f * p).toInt(), wickD)
        }
    }

    /**
     * A hen (kokoš) in a wicker coop, brown with a red comb, looking about; poked, it flaps (a feather drifting off), then
     * squawks and flaps hard, feathers flying.
     */
    private fun henCoop(x: Float, d: Float) {
        val z = z
        val p = pm(d)
        val bx = gx(x, d); val by = gy(d)
        val rw = 0.3f * p; val rh = 0.42f * p
        val pk = poked("hen"); val a = pk?.age(t) ?: 0f
        val flap = if (pk != null) (if ((a * (if (pk.step == 0) 9f else 14f)).toInt() % 2 == 0) 1 else 0) else 0
        // the hen inside, then the wicker bars over it
        sprite(bx.toInt(), by.toInt()) {
            val hx = bx.toInt(); val hy = by.toInt()
            val brown = Col.hex(0xA05A26); val brownD = Col.hex(0x6E3A18)
            val look = if (pk == null && sin(t * 1.3) > 0.3) 1 else 0
            c.fillRect(hx - 3, hy - 4, 6, 3, brown)
            c.fillRect(hx - 4, hy - 5, 2, 2, brownD)
            if (flap == 1) { c.fillRect(hx - 3, hy - 7, 3, 2, brown); c.fillRect(hx + 1, hy - 7, 2, 2, brown) }
            c.fillRect(hx + 2, hy - 7 + look, 2, 3, brown)
            c.set(hx + 2, hy - 8 + look, Pal.GERANIUM); c.set(hx + 3, hy - 8 + look, Pal.GERANIUM)
            c.set(hx + 4, hy - 6 + look, Pal.GOLD)
            if (pk != null && pk.step == 1 && flap == 1) c.set(hx + 4, hy - 5 + look, Pal.GOLD)
            c.set(hx + 3, hy - 6 + look, Col.hex(0x1E1A18))
        }
        val wick = Col.hex(0xC89A5A); val wickD = Col.hex(0x8A6030)
        for (k in 0..6) {
            val f = k / 6f
            val xx = bx - rw + f * 2f * rw
            val topY = by - rh * sqrt(max(0f, 1f - (2f * f - 1f) * (2f * f - 1f))).coerceAtLeast(0.25f)
            c.vline(xx.toInt(), topY.toInt(), by.toInt(), if (k % 2 == 0) wick else wickD)
        }
        for (y in intArrayOf((by - rh * 0.5f).toInt(), (by - 1).toInt())) c.hline((bx - rw).toInt(), (bx + rw).toInt(), y, wickD)
        c.fillEllipse(bx, by - rh, max(1f, rw * 0.5f), max(1f, 0.05f * p), wick)
        // feathers
        if (pk != null) s.fx {
            val nf = if (pk.step == 0) 2 else 5
            for (j in 0 until nf) {
                val u = ((a + j * 0.3f) / (if (pk.step == 0) 1.6f else 2.4f)).coerceIn(0f, 1f)
                val fxp = bx + (Noise.rnd(j, 111) - 0.5f) * 1.2f * p * u + sin(a * 5f + j) * 0.05f * p
                val fyp = by - rh - u * 0.6f * p + u * u * 0.8f * p
                c.blend(fxp.toInt(), fyp.toInt(), Col.hex(0xE8C8A0), 1f - u)
                if (z > 1) c.blend(fxp.toInt() + 1, fyp.toInt(), Col.hex(0xC89A6A), 1f - u)
            }
            if (pk.step == 1 && a in 0.2f..1.6f) PokeArt.rings(c, bx + rw, by - rh * 0.7f, ((a - 0.2f) / 1.4f), 0.6f * p, Col.hex(0xFFFFFF), max(1, z / 2 + 1), 0.8f, n = 2)
        }
    }

    // ------------------------------------------------------------------ the right side

    /**
     * Vinar Marko's cart, side on to us on the right: the far wheels under it, the bed of boards with a barrel standing on
     * it, the near wheels with their spokes and iron tyres, the shafts sloping to the cobbles toward the lane; the crate of
     * bottles on it is its own thing.
     */
    private fun cartBody() {
        val z = z
        val x0 = CART_X0; val x1 = CART_X1
        val d0 = CART_D; val d1 = CART_D + 1.2f
        val bedLo = 0.78f; val bedHi = 1.18f
        groundShadow((x0 + x1) / 2f, d0 + 0.6f, 2.2f, 0.8f)
        thing("cart") {
            // the far wheels
            for (wx in floatArrayOf(x0 + 0.75f, x1 - 0.7f)) wheelFront(wx, d1 + 0.05f, 0.52f, far = true)
            // the shafts, from the front of the bed down to the cobbles by the lane
            for (sd in floatArrayOf(d0 + 0.25f, d1 - 0.25f)) {
                val a = gx(x0, sd); val ay = hy(sd, bedLo + 0.05f); val b = gx(x0 - 1.9f, sd); val by = gy(sd)
                for (o in 0 until max(1, (0.07f * pm(sd)).toInt())) c.line(a.toInt(), ay.toInt() + o, b.toInt(), by.toInt() + o, if (o == 0) Pal.WOOD_L else Pal.WOOD_D)
            }
            // the bed: the inside of its far side, its floor, its near side toward us, the end boards
            quad(x0, d1, bedLo, x1, d1, bedLo, x1, d1, bedHi, x0, d1, bedHi) { _, py -> if (((py / max(1, z)) % 3) == 0) Pal.WOOD_D else Col.scale(Pal.WOOD_M, 0.8f) }
            quad(x0, d0, bedLo, x1, d0, bedLo, x1, d1, bedLo, x0, d1, bedLo) { _, _ -> Col.hex(0x6E4A2C) }
            quad(x0, d0, bedLo - 0.08f, x0, d1, bedLo - 0.08f, x0, d1, bedHi, x0, d0, bedHi) { _, py -> if (((py / max(1, z)) % 3) == 0) Pal.WOOD_D else Pal.WOOD_M }
            // the barrel standing on it
            barrel(x1 - 0.65f, d0 + 0.65f, bedLo)
            quad(x0, d0, bedLo - 0.1f, x1, d0, bedLo - 0.1f, x1, d0, bedHi, x0, d0, bedHi) { px, py ->
                val hm = (gy(d0) - (py + 0.5f)) / pm(d0)
                if (abs(hm - bedHi) < 0.04f) Pal.WOOD_L else if (((hm - bedLo) / 0.13f).toInt() % 2 == 0) Pal.WOOD_M else Col.mix(Pal.WOOD_M, Pal.WOOD_L, 0.4f)
            }
            // the near wheels
            for (wx in floatArrayOf(x0 + 0.75f, x1 - 0.7f)) wheelFront(wx, d0 - 0.08f, 0.55f, far = false)
        }
        thing("wine", slop = 1) { bottles(x0 + 1.15f, d0 + 0.6f, bedLo) }
    }

    /** A cart wheel facing us (its plane at depth [d]) with its hub at side [x]: an iron tyre, the felloes, spokes and the hub; [far]: in the shade under the cart. */
    private fun wheelFront(x: Float, d: Float, r: Float, far: Boolean) {
        val p = pm(d)
        val cx = gx(x, d); val cy = hy(d, r)
        val rr = r * p
        val tyre = if (far) Col.hex(0x2A2624) else Col.hex(0x3A3432); val fel = if (far) Col.scale(Pal.WOOD_D, 0.8f) else Pal.WOOD_D
        val spoke = if (far) Col.scale(Pal.WOOD_M, 0.75f) else Pal.WOOD_M
        val th = max(1f, 0.07f * p)
        for (y in (cy - rr).toInt()..(cy + rr).toInt()) for (xx in (cx - rr).toInt()..(cx + rr).toInt()) {
            val dx = xx + 0.5f - cx; val dy = y + 0.5f - cy
            val q = sqrt(dx * dx + dy * dy)
            if (q > rr) continue
            if (q > rr - th) c.set(xx, y, tyre) else if (q > rr - 2.4f * th) c.set(xx, y, fel)
        }
        val turn = 0.3f
        for (k in 0 until 10) {
            val a = k / 10f * 2f * PI.toFloat() + turn
            c.line(cx.toInt(), cy.toInt(), (cx + cos(a) * (rr - 2f * th)).toInt(), (cy + sin(a) * (rr - 2f * th)).toInt(), spoke)
        }
        c.fillCircle(cx, cy, max(1f, 0.1f * p), if (far) Col.hex(0x2E2622) else Col.hex(0x4A3A2A))
        if (!far && rr > 5f) c.set(cx.toInt() - 1, cy.toInt() - 1, Pal.WOOD_L)
    }

    /** A wine barrel standing at ([x], [d]) on [base] m: staves, iron hoops, lit on one side. */
    private fun barrel(x: Float, d: Float, base: Float) {
        val p = pm(d)
        val bx = gx(x, d); val by = hy(d, base)
        val r = 0.3f * p; val hh = 0.7f * p
        for (y in (by - hh).toInt()..by.toInt()) {
            val v = (by - y) / hh
            val bulge = 1f + 0.12f * sin(v * PI.toFloat())
            val half = r * bulge
            for (xx in (bx - half).toInt()..(bx + half).toInt()) {
                val u = (xx + 0.5f - bx) / max(1f, half)
                var col = if (u < -0.4f) Pal.WOOD_L else if (u > 0.5f) Pal.WOOD_D else Pal.WOOD_M
                if (abs(v - 0.15f) < 0.05f || abs(v - 0.85f) < 0.05f || abs(v - 0.5f) < 0.03f) col = Col.hex(0x3A3432)
                c.set(xx, y, col)
            }
        }
        c.fillEllipse(bx, by - hh, r, max(1f, r * 0.3f), Pal.WOOD_D)
        c.fillEllipse(bx, by - hh, r * 0.8f, max(0.8f, r * 0.22f), Pal.WOOD_M)
    }

    /** Marko's crate of bottles (steklenica) on the cart: dark green glass, their necks up, a glint on each. */
    private fun bottles(x: Float, d: Float, base: Float) {
        crate(x, d - 0.25f, 0.7f, 0.5f, 0.24f, base)
        for (k in 0 until 6) {
            val dd = d - 0.15f + (k / 3) * 0.2f; val xx = x - 0.22f + (k % 3) * 0.22f
            val p = pm(dd)
            val bx = gx(xx, dd); val by = hy(dd, base + 0.2f)
            val bw = max(1f, 0.05f * p); val bh = max(3f, 0.32f * p)
            val glass = if (k % 3 == 1) Col.hex(0x6A8A2A) else Col.hex(0x2E5A2E)
            c.fillRect((bx - bw).toInt(), (by - bh * 0.6f).toInt(), max(1, (2 * bw).toInt()), max(1, (bh * 0.6f).toInt()), glass)
            c.fillRect((bx - bw * 0.4f).toInt(), (by - bh).toInt(), max(1, (0.8f * bw).toInt()), max(1, (bh * 0.4f).toInt()), glass)
            c.set((bx - bw * 0.4f).toInt(), (by - bh).toInt(), Col.hex(0xD8C8A0))
            c.set((bx - bw + 0.5f).toInt(), (by - bh * 0.5f).toInt(), Col.hex(0xB8E0A8))
        }
    }

    /** Empty crates stacked by the right-hand stall, one on its side. */
    private fun crateStack(x: Float, d: Float) {
        crate(x, d, 0.55f, 0.4f, 0.3f)
        crate(x + 0.05f, d, 0.5f, 0.4f, 0.3f, 0.3f)
        crate(x - 0.05f, d - 0.5f, 0.45f, 0.4f, 0.28f)
    }

    /** The market bell on its post by the lane, a little roof over it and a rope from its clapper; rung ([fx] "bell"), it swings. */
    private fun bellPost() {
        val z = z
        val x = 2.4f; val d = 15.6f
        val p = pm(d)
        post(x, d, 2.7f)
        // the arm out over the lane and a small shingle roof
        val ax0 = gx(x, d); val ax1 = gx(x - 0.55f, d); val ay = hy(d, 2.55f)
        c.fillRect(ax1.toInt(), ay.toInt(), (ax0 - ax1).toInt() + 1, max(1, (0.08f * p).toInt()), Pal.WOOD_D)
        val ry = hy(d, 2.85f)
        for (j in 0 until max(2, (0.25f * p).toInt())) c.hline((ax1 - 0.1f * p + j).toInt(), (ax0 + 0.1f * p - j).toInt(), (ry + j - max(2, (0.25f * p).toInt())).toInt() + 2, if (env.snow) Pal.SNOW_L else Pal.SHINGLE_M)
        val ring = fxOn("bell")
        val swing = if (ring > 0.02f) sin(t * 6.0).toFloat() * ring else 0f
        val bx = ax1 + (ax0 - ax1) * 0.2f + swing * 0.12f * p
        val by = ay + 0.05f * p
        val bw = max(1.5f, 0.2f * p); val bh = max(2.5f, 0.36f * p)
        val bronze = Col.hex(0xB8862E); val bronzeL = Col.hex(0xF0C860); val bronzeD = Col.hex(0x6A4A1A)
        for (j in 0 until bh.toInt()) {
            val f = j / bh
            val half = bw * (0.45f + 0.55f * f * f)
            c.hline((bx - half).toInt(), (bx + half).toInt(), (by + j).toInt(), bronze)
            c.set((bx - half).toInt(), (by + j).toInt(), bronzeL); c.set((bx + half).toInt(), (by + j).toInt(), bronzeD)
        }
        // the rope hanging from the clapper
        c.vline((bx - swing * 0.05f * p).toInt(), (by + bh).toInt(), hy(d, 1.3f).toInt(), Col.hex(0xD8C49A))
        bellAt[0] = bx; bellAt[1] = by + bh * 0.5f; bellAt[2] = p
    }

    private val bellAt = FloatArray(3)

    /** The bell's ringing ([fx] "bell"): rings going out from it. */
    private fun bellRing() {
        val lvl = fxOn("bell")
        if (lvl <= 0.02f) return
        val z = z
        s.fx { PokeArt.rings(c, bellAt[0], bellAt[1], ((t * 0.8) % 1.0).toFloat(), 2.4f * bellAt[2], Col.hex(0xFFD040), z + 1, min(1f, 0.4f + lvl), n = 3) }
    }

    /** Pigeons pecking on the cobbles, or ([up] > 0) flown up over the market and wheeling away (gone at 1). */
    private fun pigeons(x: Float, d: Float, up: Float) {
        val z = z
        val x0 = gx(x, d).toInt(); val y0 = gy(d).toInt()
        if (up > 0f) {
            if (SceneSights.flownAway(up)) return // gone: no word to tap either (SceneSights)
            for (j in 0 until 4) {
                val e = up * up * (3f - 2f * up)
                val hx = x0 + (j * 8 - 12) * z; val hy = y0 - (j % 2) * 3 * z
                val fx = hx + (e * (60f + j * 25f) * z * (if (j % 2 == 0) 1f else -0.6f)).toInt()
                val fy = hy - (e * (90f + j * 10f) * z).toInt() + (sin(t * 3 + j) * 3 * z).toInt()
                PokeArt.wings(c, fx, fy, ((t * 11).toInt() + j) and 1 == 0, z, Pal.STONE_L, Pal.STONE_M)
                c.fillRect(fx + 2 * z, fy - z, z, z, Pal.STONE_L)
            }
            return
        }
        sprite(x0, y0) {
            for (k in 0..2) {
                val x = x0 + k * 8 - 8 + (sin(t * 0.7 + k * 3).toFloat() * 3).toInt(); val y = y0 + (k % 2) * 3
                val peck = sin(t * 5 + k * 2) > 0.6
                val dir = if (k == 1) -1 else 1
                c.fillRect(x - 3, y - 4, 6, 3, Pal.STONE_L); c.set(x - 3 * dir, y - 2, Pal.STONE_M); c.set(x + 3 * dir, y - 4, Pal.STONE_M)
                c.fillRect(x + (if (dir > 0) 2 else -4), y - 6 + (if (peck) 1 else 0), 2, 2, Pal.STONE_L)
                c.set(x + (if (dir > 0) 4 else -5), y - 5 + (if (peck) 1 else 0), Pal.GOLD)
                c.set(x + (if (dir > 0) 2 else -3), y - 6 + (if (peck) 1 else 0), Col.hex(0x5A8A6A))
                c.set(x - 1, y - 1, Pal.GERANIUM_D); c.set(x + 1, y - 1, Pal.GERANIUM_D)
            }
        }
    }

    /** An apple (or two) rolling off the crate over the cobbles and coming to rest (poke "apples"). */
    private fun apples() {
        val pk = poked("apples") ?: return
        val a = pk.age(t)
        val n = if (pk.step == 0) 1 else 2
        val z = z
        for (j in 0 until n) {
            val dur = if (pk.step == 0) 2.8f else 3.2f
            val u = PokeArt.ease(a - j * 0.25f, 0f, dur * 0.8f)
            if (a - j * 0.25f < 0f) continue
            // off the rim, a drop to the cobbles, then rolling out into the lane
            val x = -5.4f + u * (1.5f + j * 0.7f)
            val d = 11.15f - u * (0.45f + j * 0.2f)
            val drop = (1f - PokeArt.ease(a - j * 0.25f, 0f, 0.35f)) * 0.42f
            val bounce = if (a - j * 0.25f in 0.35f..0.8f) sin(((a - j * 0.25f) - 0.35f) / 0.45f * PI.toFloat()) * 0.08f else 0f
            val p = pm(d)
            val bx = gx(x, d); val by = hy(d, drop + bounce)
            val r = max(1f, 0.06f * p)
            prop {
                c.fillEllipse(bx, by - r, r, r, if (j == 0) Col.hex(0xC8281E) else Col.hex(0xE8C040))
                val spin = (u * 12f).toInt() % 4
                c.set((bx + (if (spin < 2) -1 else 1) * r * 0.4f).toInt(), (by - r * 1.5f).toInt(), Col.hex(0x5A3A1E))
                if (z > 1) c.set((bx - r * 0.4f).toInt(), (by - r * 1.3f).toInt(), Col.hex(0xFFFFFF))
            }
        }
    }

    /**
     * Close to us below the stage (a taller view shows it): a sack of potatoes and a wheelbarrow with the season's load
     * (pumpkins in autumn, cabbages in winter, flowers in spring and summer), a crate, straw and a leaf on the cobbles.
     */
    private fun nearEdge() {
        if (gy(10f) >= h) return
        val z = z
        items.add(8.6f to { groundShadow(-3.2f, 8.6f, 0.45f); prop { sack(-3.2f, 8.6f) } })
        items.add(8.2f to { groundShadow(2.6f, 8.2f, 0.9f); prop { barrow(2.6f, 8.2f) } })
        items.add(9.3f to { prop { crate(-1.6f, 9.3f, 0.5f, 0.4f, 0.3f); heap(-1.6f, 9.5f, 0.3f, 0.2f, intArrayOf(Col.hex(0xC8A060), Col.hex(0xB08A4A), Col.hex(0xD8B878)), 151, 0.06f) } })
        for (k in 0 until 6) {
            val d = 6.5f + Noise.rnd(k, 152) * 3f; val x = (Noise.rnd(k, 153) - 0.5f) * 6f
            if (gy(d) >= h + 2 * z) continue
            items.add(d to { prop(0) { val bx = gx(x, d).toInt(); val by = gy(d).toInt(); c.line(bx, by, bx + 3 * z, by - z, if (k % 2 == 0) Pal.HAY_L else Col.hex(0x6AA84A)) } })
        }
    }

    /** A jute sack of potatoes, tied at the top, a few spilled beside it. */
    private fun sack(x: Float, d: Float) {
        val p = pm(d)
        val bx = gx(x, d); val by = gy(d)
        val jute = Col.hex(0xB89A6A); val juteD = Col.hex(0x8A7048)
        c.fillEllipse(bx, by - 0.3f * p, 0.26f * p, 0.32f * p, jute)
        c.fillEllipse(bx + 0.08f * p, by - 0.26f * p, 0.14f * p, 0.26f * p, juteD)
        c.fillRect((bx - 0.06f * p).toInt(), (by - 0.72f * p).toInt(), max(1, (0.12f * p).toInt()), max(1, (0.14f * p).toInt()), jute)
        c.hline((bx - 0.08f * p).toInt(), (bx + 0.08f * p).toInt(), (by - 0.6f * p).toInt(), Col.hex(0x5A3A22))
        for (j in 0 until 3) c.fillEllipse(bx + (0.3f + 0.1f * j) * p, by - 0.04f * p, max(1f, 0.05f * p), max(1f, 0.04f * p), Col.hex(0xB08A4A))
    }

    /** A wooden wheelbarrow with the season's load: flowers in spring and summer, pumpkins in autumn, cabbages in winter. */
    private fun barrow(x: Float, d: Float) {
        val z = z
        val p = pm(d)
        crate(x, d - 0.3f, 0.7f, 0.6f, 0.35f, 0.3f)
        wheelFront(x - 0.5f, d - 0.35f, 0.22f, far = false)
        c.line(gx(x + 0.35f, d - 0.3f).toInt(), hy(d - 0.3f, 0.45f).toInt(), gx(x + 0.9f, d - 0.4f).toInt(), hy(d - 0.4f, 0.55f).toInt(), Pal.WOOD_D)
        c.line(gx(x + 0.35f, d - 0.3f).toInt(), hy(d - 0.3f, 0.3f).toInt(), gx(x + 0.3f, d - 0.3f).toInt(), gy(d - 0.3f).toInt(), Pal.WOOD_D)
        val load = when (env.season) {
            Season.AUTUMN -> intArrayOf(Col.hex(0xE07A1E), Col.hex(0xC8621A), Col.hex(0xE8A040))
            Season.WINTER -> intArrayOf(Col.hex(0x9ACB6A), Col.hex(0xB8D890))
            else -> intArrayOf(Col.hex(0xE8303A), Col.hex(0xF0D060), Col.hex(0xB08AD8), Col.hex(0xF4F0FA))
        }
        val big = env.season == Season.AUTUMN || env.season == Season.WINTER
        heap(x, d - 0.05f, 0.65f, 0.25f, load, 161, if (big) 0.11f else 0.05f)
        if (!big) for (j in 0 until 5) c.set(gx(x - 0.2f + j * 0.1f, d).toInt(), hy(d, 0.8f).toInt() + (j % 2) * z, Pal.LEAF)
    }

    // ------------------------------------------------------------------ weather

    /** Rain wets the cobbles (darker, puddles in the lane catching the sky) and veils the far hills and roofs in grey. */
    override fun weather() {
        val rain = frame.sky.rain
        if (rain > 0.03f) {
            val z = z
            val skyC = env.lit(skyAt(0.35f))
            for (py in max(hz + 2 * z, c.top) until min(h, c.bottom)) {
                val d = depthAt(py)
                for (px in max(0, c.left) until min(w, c.right)) {
                    val i = c.index(px, py)
                    if (c.ids[i] != 0) continue
                    val x = sideAt(px, d)
                    var col = Col.scale(c.pixels[i], 1f - 0.2f * rain)
                    if (abs(x - 0.4f) < 3.2f && Noise.v2(x * 0.8f, d * 0.8f, 171) > 0.8f - 0.12f * rain) col = Col.mix(col, skyC, 0.3f + 0.25f * rain)
                    c.pixels[i] = col
                }
            }
            val grey = env.lit(Col.hex(0x9AA2AE))
            for (py in max(0, c.top) until min(hz + 12 * z, c.bottom)) {
                val prof = if (py < hz) kotlin.math.exp((py - hz) / (30f * z)) else 1f - (py - hz) / (12f * z)
                for (px in max(0, c.left) until min(w, c.right)) {
                    val i = c.index(px, py)
                    val a = rain * 0.45f * prof * (0.6f + 0.4f * Noise.v1((px - ox) / z * 0.05f + t.toFloat() * 0.1f, 172))
                    if (a > Dither.at(px, py) * 0.4f) c.pixels[i] = Col.mix(c.pixels[i], grey, min(0.6f, a))
                }
            }
        }
        super.weather()
    }

    // ------------------------------------------------------------------ what a dialog brings

    /**
     * A busy market day ([fx] "bustle": how busy): shoppers in the lane and on the square, small and far, walking between
     * the stalls with baskets, stopping at them; two are always about.
     */
    private fun bustle(level: Float) {
        val n = 2 + (level * 9f).toInt()
        for (j in 0 until n) {
            val per = 22.0 + Noise.rnd(j, 121) * 14.0
            val ph = ((t / per + Noise.rnd(j, 122)) % 1.0).toFloat()
            val back = j % 2 == 0
            val d = if (back) 30f - ph * 13f else 17f + ph * 14f
            val stop = sin(ph * 12f + j) > 0.8f
            val x = (Noise.rnd(j, 123) - 0.5f) * 3f + sin(ph * 4f + j) * 0.4f
            if (d < 16.5f) continue
            items.add(d to { groundShadow(x, d, 0.3f); prop { shopper(x, d, j, !stop, back) } })
        }
    }

    /** A shopper far off: a chibi figure scaled to its distance, a head of hair, a coloured shirt or dress, a basket on some. */
    private fun shopper(x: Float, d: Float, seed: Int, walking: Boolean, away: Boolean) {
        val p = pm(d)
        val bx = gx(x, d); val by = gy(d)
        val hgt = 1.6f * p
        val shirt = Pal.SHIRTS[Math.floorMod(Noise.hash(seed, 131), Pal.SHIRTS.size)]
        val hair = Pal.HAIR[Math.floorMod(Noise.hash(seed, 132), Pal.HAIR.size)]
        val step = if (walking) ((t * 4 + seed).toInt() and 1) else 0
        val lw = max(1f, 0.1f * p)
        // legs, the body, the head (big, as everyone's), a basket on the arm now and then
        c.fillRect((bx - lw * 1.5f).toInt(), (by - hgt * 0.2f).toInt() - step, max(1, lw.toInt()), max(1, (hgt * 0.2f).toInt()), Col.hex(0x3A3042))
        c.fillRect((bx + lw * 0.5f).toInt(), (by - hgt * 0.2f).toInt() - (1 - step) * (if (walking) 1 else 0), max(1, lw.toInt()), max(1, (hgt * 0.2f).toInt()), Col.hex(0x3A3042))
        c.fillRect((bx - hgt * 0.17f).toInt(), (by - hgt * 0.55f).toInt(), max(2, (hgt * 0.34f).toInt()), max(2, (hgt * 0.36f).toInt()), shirt)
        val hr = hgt * 0.22f
        c.fillEllipse(bx, by - hgt * 0.72f, max(1f, hr), max(1f, hr), Pal.SKIN)
        c.fillEllipse(bx, by - hgt * 0.78f - (if (away) 0f else hr * 0.2f), max(1f, hr * 1.05f), max(1f, hr * (if (away) 0.9f else 0.65f)), hair)
        if (seed % 3 == 0) c.fillRect((bx + hgt * 0.17f).toInt(), (by - hgt * 0.42f).toInt(), max(1, (hgt * 0.14f).toInt()), max(1, (hgt * 0.1f).toInt()), Col.hex(0xB8864A))
    }

    companion object {
        /** How deep a counter along the lane is (m). */
        private const val BACK_W = 1.0f

        /** The village's stall facing us: its counter from side [MAIN_L] to [MAIN_R], its front at depth [MAIN_FRONT], its top back to [MAIN_TOP_BACK], the canvas at [MAIN_BACK]; the goods on it at [GOODS_D]. */
        private const val MAIN_L = -7.5f
        private const val MAIN_R = -2.6f
        private const val MAIN_FRONT = 12.4f
        private const val MAIN_TOP_BACK = 13.2f
        private const val MAIN_BACK = 13.9f
        private const val GOODS_D = 12.8f

        /** Marko's cart, side on: from side [CART_X0] to [CART_X1], its near side at depth [CART_D]. */
        private const val CART_X0 = 2.9f
        private const val CART_X1 = 6.7f
        private const val CART_D = 12.5f

        /** The house along the right of the square: its front at side [SIDE_X], from depth [SIDE_D0] to [SIDE_D1]. */
        private const val SIDE_X = 8.2f
        private const val SIDE_D0 = 21f
        private const val SIDE_D1 = 37f

        /** The houses closing the square: side of the left end, depth, width. */
        private val HOUSES = listOf(
            floatArrayOf(-26f, 46f, 8f), floatArrayOf(-17.5f, 47f, 8f), floatArrayOf(-9f, 45.5f, 7.5f), floatArrayOf(-1f, 48f, 7f), floatArrayOf(10f, 46.5f, 8.5f),
        )
    }
}
