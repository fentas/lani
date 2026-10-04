package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.Season
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.Poke
import si.lanisce.lani.game.scene.SceneSights
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Na vasi: the village square, seen from under the big linden tree. The cobbles run away to the church at the
 * far end, its bell tower with the clock; a row of houses lines the right, whitewashed and ochre, green shutters
 * and geraniums in the windows, the nearest one with a bicycle against its wall; a garden fence on the left
 * with fruit trees and a cottage behind it. Under the linden the bench with the cat asleep on one end, a flower
 * bed, the well with its little roof and the lamp post, the dog, pigeons, a ball and, when the children are out,
 * a game of catch across the square. The sun crosses the sky by day; the moon stands in for it at night.
 *
 * Closer up ([detail] 2 and 3) the same square is drawn finer: rounded cobbles with their joints and moss, the
 * linden's single leaves and furrowed bark, window frames, slatted shutters, flower boxes, stone socles, panelled
 * doors and rows of tiles on the houses, the church's rose window, clock, bell and cross, the well's curved
 * stones, rope and bucket, the bench's slats and screws, the lamp's lantern, the bicycle's spokes, the flowers.
 * The cat, the dog and the pigeons stay the sprites they are, like the people.
 *
 * Tapped ([pokes]), the pigeons scatter up and away and come back to land a few seconds later; the bell swings and
 * rings in the tower; the dog wags hard with its tongue out, and the next time jumps up and barks.
 */
internal class SquarePainter : VistaPainter() {
    override val art = "square"
    override val pokes = listOf(
        Poke("pigeons", listOf(6.5), rest = 3.0), Poke("tower", listOf(3.2), rest = 2.0), Poke("dog", listOf(1.6, 2.0), rest = 2.0),
    )

    // the plan, in metres: the house fronts on the right, the garden fence on the left, the church at the end
    private val row = 7.6f
    private val fenceX = -7.6f
    private val churchD = 58f
    private val houses = listOf(
        House(13f, 23f, Pal.WALL_L, Pal.WALL_M, 2),
        House(24.2f, 33f, Pal.OCHRE_L, Pal.OCHRE_M, 2),
        House(34.2f, 44f, Col.hex(0xF4E2DA), Col.hex(0xDCC8BE), 2),
        House(45.2f, 55f, Pal.WALL_L, Pal.WALL_M, 1),
    )
    private data class House(val d0: Float, val d1: Float, val wall: Int, val wallD: Int, val floors: Int)

    private val lindenX = -4.3f; private val lindenD = 12.6f

    private companion object {
        /** The village projects the square shows as they're built: the town clock in the tower, the maypole. */
        const val CLOCK = "mestna_ura"
        const val MAYPOLE = "mlaj"
    }

    private val items = ArrayList<Pair<Float, () -> Unit>>()

    override fun paint() {
        setup()
        val k = detail
        val lit = env.windows > 0.35f
        // ---- sky: the sun is a thing by day; at night the moon stands in its place as scenery
        sky(0, hz + 2 * k)
        // the real stars over the square (its moon is the sun's word's, where the sun stands by day)
        if (nightSky(skyFacing, hz, hz - 6 * k) == null) stars(0, hz - 6 * k, 80, 9)
        sunGlow()
        vistaClouds(4, 9)
        hills()
        // the sun keeps above the ridges, so it always shows while it is up (SceneSights: the words panel's chips go by it too)
        val day = SceneSights.sunUp(env)
        thing("sun", outline = 0, slop = 2, visible = day) { if (day) sun(ox + 152 * k, ox + 180 * k, hz - 26 * k) else moon(ox + 214 * k, moonRow(oy + 14 * k, hz - 26 * k), 7 * k, skyAt(0.22f)) }
        ground { x, d, px, py -> square(x, d, px, py) }
        groundShadow(lindenX + 0.8f, lindenD + 0.6f, 3.4f, 0.8f)

        items.clear()
        // what the village has built stands here only once it's there (the church, the well), the maypole as far as it's come
        if (there("tower")) items.add(churchD to { thing("tower") { church() } })
        maypole()
        items.add(40f to { prop { cottage(-13.5f, 40f) } })
        for ((i, tr) in listOf(Triple(-11f, 30f, 5f), Triple(-15f, 26f, 5.5f), Triple(-10f, 21f, 4.5f)).withIndex())
            items.add(tr.second to { prop(Pal.OUTLINE_TREE) { leafyTree(tr.first, tr.second, tr.third, 31 + i * 5, blossom = true, fruit = Col.hex(0xE8C040)) } })
        items.add(38f to { thing("fence") { fence(38f, 13.4f) } })
        for ((i, hs) in houses.withIndex().reversed()) {
            items.add(hs.d1 to { if (i == 0) thing("house") { house(hs, lit, near = true) } else prop { house(hs, lit, near = false) } })
        }
        items.add(15f to { thing("bicycle", slop = 1) { bicycle(gx(6.9f, 15f).toInt(), gy(15f).toInt()) } })
        items.add(14.5f to { thing("flowers", slop = 1) { flowerBed(gx(-1.3f, 14.5f).toInt(), gy(14.5f).toInt()) } })
        items.add(14f to { if (env.dark < 0.5f) pokeable("pigeons") { pigeons(gx(-0.2f, 14f).toInt(), gy(14f).toInt()) } })
        if (there("well")) items.add(12.9f to { groundShadow(4.2f, 12.9f, 1.2f); thing("well") { well(gx(4.2f, 12.9f).toInt(), gy(12.9f).toInt()) } })
        items.add(12.3f to { lampPost(gx(6.5f, 12.3f).toInt(), gy(12.3f).toInt(), lit) })
        items.add(lindenD to { thing("linden", outline = Pal.OUTLINE_TREE) { lindenTrunk() } })
        // the bench under the linden with the cat, whoever sits on it
        val bx = gx(-4.8f, 11.8f).toInt(); val bby = gy(11.8f).toInt() - 7 * k
        items.add(11.8f to {
            thing("bench") { bench(bx, bby) }
            for (p in peopleAt("bench")) person(p, bx - 8 * k, bby + 12 * k, flip = false, seated = true)
            thing("cat", slop = 2) { cat(bx + 15 * k, bby - 2 * k) }
        })
        items.add(11.3f to { thing("dog", slop = 1) { dog(gx(1.9f, 11.3f).toInt(), gy(11.3f).toInt()) } })
        for (p in peopleAt("well")) items.add(11.9f to { groundShadow(5.4f, 11.9f, 0.5f); person(p, gx(5.4f, 11.9f).toInt(), gy(11.9f).toInt(), flip = true) })
        // the children and the ball, by their feet
        val playing = frame.people.filter { it.slot.startsWith("play") }
        val spots = mapOf(
            "play1" to (gx(-2.2f, 10.9f).toInt() to gy(10.9f).toInt()),
            "play2" to (gx(0.6f, 12.4f).toInt() to gy(12.4f).toInt()),
            "play3" to (gx(3.0f, 10.6f).toInt() to gy(10.6f).toInt()),
        )
        val ballAt = ballPosition(playing, spots)
        for (p in playing) {
            val (sx, sy) = spots[p.slot] ?: continue
            val n = p.slot.last() - '0'
            val ph = t * (1.1 + n * 0.15) + n * 2.1
            val dx = (sin(ph) * 14 * k).toInt(); val dy = (sin(ph * 2) * 2 * k).toInt()
            val moving = abs(cos(ph)) > 0.25
            val flip = cos(ph) < 0
            items.add(depthAt(sy + dy) to { shadow((sx + dx).toFloat(), (sy + dy).toFloat(), 6f * k, 2f * k); person(p, sx + dx, sy + dy, flip = flip, walking = moving) })
        }
        // the ball in front of the players, so it never hides behind whoever is kicking it
        items.add(depthAt(ballAt[1]) - 0.6f to { thing("ball", slop = 2) { ball(ballAt[0], ballAt[1], ballAt[2]) } })
        items.sortByDescending { it.first }
        for ((_, draw) in items) draw()

        thing("linden", outline = Pal.OUTLINE_TREE) { lindenCrown() }
        fireflies(0, hz + 10 * k, w, h, 10, 23)
        s.fx { s.effects.leaves(env.season, env.month) }
    }

    /** Where the ball is: kicked between the players in a low arc, or lying by the first play spot. [x, y, height]. */
    private fun ballPosition(playing: List<PersonInScene>, spots: Map<String, Pair<Int, Int>>): IntArray {
        if (playing.size < 2) return intArrayOf(gx(-1.2f, 11.4f).toInt(), gy(11.4f).toInt(), 0)
        val pts = playing.mapNotNull { spots[it.slot] }
        val cycle = 2.2
        val n = pts.size
        val leg = ((t / cycle) % n).toInt(); val u = ((t / cycle) % 1.0).toFloat()
        val a = pts[leg]; val b = pts[(leg + 1) % n]
        val x = a.first + ((b.first - a.first) * u).toInt(); val y = a.second + ((b.second - a.second) * u).toInt()
        val hgt = (sin(u * PI) * 14 * detail).toInt()
        return intArrayOf(x, y, hgt)
    }

    // ------------------------------------------------------------------ helpers

    /** A line [th] px thick, stacked across its steeper direction. */
    private fun thick(x0: Float, y0: Float, x1: Float, y1: Float, th: Int, col: Int) {
        if (th <= 1) { c.line(x0.toInt(), y0.toInt(), x1.toInt(), y1.toInt(), col); return }
        val steep = abs(y1 - y0) > abs(x1 - x0)
        for (i in 0 until th) {
            val o = i - th / 2
            if (steep) c.line(x0.toInt() + o, y0.toInt(), x1.toInt() + o, y1.toInt(), col)
            else c.line(x0.toInt(), y0.toInt() + o, x1.toInt(), y1.toInt() + o, col)
        }
    }

    /** A plank [pw] × [ph] px at ([x], [y]): lit along its top, shaded along its foot, the grain in dashes, its ends darker. */
    private fun plank(x: Int, y: Int, pw: Int, ph: Int, base: Int, lite: Int, dark: Int, seed: Int) {
        c.fillRect(x, y, pw, ph, base)
        val grain = Col.mix(base, dark, 0.45f)
        for (yy in max(y + 1, c.top) until min(y + ph - 1, c.bottom)) for (xx in max(x + 1, c.left) until min(x + pw - 1, c.right)) if (Noise.rnd(xx / 6, yy, seed) < 0.22f) c.set(xx, yy, grain)
        c.hline(x, x + pw - 1, y, lite)
        if (ph >= 3) c.hline(x, x + pw - 1, y + ph - 1, dark)
        c.vline(x, y + 1, y + ph - 2, Col.mix(base, dark, 0.3f)); c.vline(x + pw - 1, y + 1, y + ph - 2, dark)
    }

    // ------------------------------------------------------------------ far away

    private fun hills() {
        val season = env.season
        val k = detail
        val farCol = if (season == Season.WINTER) Col.hex(0xB0BED6) else Col.hex(0x6474A0)
        ridge(hz - 10f * k, 22f * k, 0.011f, 31, farCol, 0.34f, snowLine = 0.5f, sharp = true)
        val midCol = when (season) { Season.WINTER -> Col.hex(0xC6D2E2); Season.AUTUMN -> Col.hex(0x7A7A44); else -> Col.hex(0x4E7A48) }
        val mid = ridge(hz - 2f * k, 12f * k, 0.016f, 33, midCol, 0.22f)
        treeline(mid, 6 * k, 2.2f * k, if (season == Season.WINTER) Col.hex(0x6A7A8A) else Col.hex(0x2E5A36), 0.22f, 35)
    }

    // ------------------------------------------------------------------ the square

    /** Cobbles between the houses and the garden, grass beyond the fence, the road past the church. */
    private fun square(x: Float, d: Float, px: Int, py: Int): Int {
        val g = env.grass
        val k = detail
        if (x < fenceX - 0.2f || d > churchD + 14f) {
            if (env.snow) return hazy(if (Noise.v2(x * 0.5f, d * 0.5f, 3) < 0.3f) Pal.SNOW_M else Pal.SNOW_L, d)
            val n = Noise.v2(x * 0.6f, d * 0.6f, 3) + (Dither.at(px, py) - 0.5f) * 0.25f
            var col = if (n > 0.6f) g[0] else if (n > 0.3f) g[1] else g[2]
            if (Noise.rnd(px, py, 5) < 0.05f) col = g[2]
            if (k > 1 && d < 30f) {
                // blades closer up
                val b = Noise.rnd(px, (py + (px and 1)) / 3, 6)
                if (b < 0.07f) col = g[2] else if (b > 0.95f) col = g[0]
            }
            return hazy(col, d)
        }
        val size = 0.24f
        val rowV = d / size; val r = floor(rowV).toInt()
        val u = (x + (r and 1) * size * 0.5f) / size; val cI = floor(u).toInt()
        val stonePx = size * pm(d); val rowPx = F * EYE / (d * d) * size
        val seed = Noise.hash(cI, r, 7)
        var col = when ((seed ushr 4) % 5) { 0 -> Pal.COBBLE_L; 1, 2 -> Pal.COBBLE_M; 3 -> Col.mix(Pal.COBBLE_M, Pal.DIRT_L, 0.3f); else -> Col.mix(Pal.COBBLE_D, Pal.COBBLE_M, 0.5f) }
        var joint = false
        if (stonePx >= 2.5f && rowPx >= 2f) {
            val fu = (u - cI) * stonePx; val fv = (rowV - r) * rowPx
            if (k == 1 || stonePx < 6f || rowPx < 4f) {
                if (fu < 1f || fv < 1f) col = Pal.COBBLE_D
                else if (fu < 2f && fv < 2f && stonePx > 4f) col = Col.mix(col, Col.hex(0xFFFFFF), 0.25f)
            } else {
                // a rounded stone: its joints, the corners worn round, lit along its far edge, its near edge in shade, speckled
                val gu = stonePx - fu; val gv = rowPx - fv
                val eu = min(fu, gu); val ev = min(fv, gv)
                joint = fu < 1f || fv < 1f || (eu < 2.2f && ev < 2.2f && eu + ev < 3f)
                col = when {
                    joint -> Pal.COBBLE_D
                    gv < 1.8f -> Col.mix(col, Col.hex(0xFFFFFF), 0.25f)
                    fv < 2.2f -> Col.mix(col, Pal.COBBLE_D, 0.35f)
                    fu < 2f -> Col.mix(col, Col.hex(0xFFFFFF), 0.1f)
                    gu < 1.6f -> Col.mix(col, Pal.COBBLE_D, 0.2f)
                    Noise.rnd(px, py, 8) < 0.07f -> Col.mix(col, Pal.COBBLE_D, 0.3f)
                    else -> col
                }
            }
        } else if (Dither.at(px, py) < 0.3f) col = Pal.COBBLE_D
        if (env.snow) col = if (Noise.v2(x * 1.3f, d * 1.3f, 11) < 0.3f && Dither.at(px, py) < 0.5f) col else if (Noise.rnd(px, py, 12) < 0.2f) Pal.SNOW_M else Pal.SNOW_L
        // grass between the stones by the fence and the house walls (in the joints, closer up)
        if (!env.snow && (x < fenceX + 0.5f || x > row - 0.4f) && Noise.rnd(px, py, 13) < (if (joint) 0.8f else 0.3f)) col = g[1]
        return hazy(col, d)
    }

    // ------------------------------------------------------------------ buildings

    /**
     * A house along the right side of the square, its front toward us in perspective: plaster, a stone socle,
     * windows with green shutters in bays, geraniums on the sills in summer, a door, a cornice, the tiled roof
     * rising away from the square, a chimney; the near one also shows its gable end. Lit windows at night.
     */
    private fun house(hs: House, lit: Boolean, near: Boolean) {
        val k = detail
        val top = if (hs.floors == 2) 6.3f else 3.4f
        val xw = row
        val summer = env.month in 5..10
        // the gable end facing us (only the nearest house shows it)
        if (near) {
            val p = pm(hs.d0); val by = gy(hs.d0)
            val x0 = gx(xw, hs.d0); val x1 = gx(xw + 4.6f, hs.d0); val xm = gx(xw + 2.3f, hs.d0)
            if (k > 1) {
                // closer up: the underside of the roof where it overhangs the gable (a sliver of sky otherwise)
                val dn = hs.d0 - 0.4f
                c.polyBegin()
                c.polyAdd(gx(xw - 0.4f, dn), gy(dn) - (top + 0.1f) * pm(dn)); c.polyAdd(gx(xw + 2.3f, dn), gy(dn) - (top + 3.2f) * pm(dn))
                c.polyAdd(xm, by - (top + 3.2f) * p); c.polyAdd(x0, by - top * p)
                c.polyFill(Col.mix(Pal.WOOD_D, Pal.ROOF_D, 0.5f))
            }
            c.polyBegin(); c.polyAdd(x0, by); c.polyAdd(x1, by); c.polyAdd(x1, by - top * p); c.polyAdd(xm, by - (top + 3.2f) * p); c.polyAdd(x0, by - top * p)
            if (k == 1) c.polyFill { px, py -> if (py > by - 0.5f * p) Pal.STONE_M else if (Noise.rnd(px, py, 17) < 0.06f) hs.wallD else Col.mix(hs.wall, hs.wallD, 0.35f) }
            else {
                val shade = Col.mix(hs.wall, hs.wallD, 0.35f)
                c.polyFill { px, py ->
                    if (py > by - 0.5f * p) {
                        val z = (by - py - 0.5f) / p; val rw = floor(z / 0.15f); val sx = ((px - x0) / p + rw * 0.17f) / 0.34f
                        if (z - rw * 0.15f < 1f / p || (sx - floor(sx)) * 0.34f * p < 1f) Pal.STONE_D else if (Noise.rnd(px, py, 21) < 0.15f) Col.mix(Pal.STONE_M, Pal.STONE_D, 0.5f) else Pal.STONE_M
                    } else if (Noise.rnd(px, py, 17) < 0.06f) hs.wallD else shade
                }
            }
            val wx = gx(xw + 1.8f, hs.d0); val wy = by - 4.8f * p
            window(wx.toInt(), wy.toInt(), (1.0f * p).toInt(), (1.3f * p).toInt(), lit, 3 * k)
            if (k > 1 && summer && !lit) windowBox(wx.toInt(), (wy + 1.3f * p).toInt(), (1.0f * p).toInt(), p)
        }
        // the front
        val p0 = pm(hs.d0); val p1 = pm(hs.d1)
        c.polyBegin()
        c.polyAdd(gx(xw, hs.d0), gy(hs.d0)); c.polyAdd(gx(xw, hs.d1), gy(hs.d1))
        c.polyAdd(gx(xw, hs.d1), gy(hs.d1) - top * p1); c.polyAdd(gx(xw, hs.d0), gy(hs.d0) - top * p0)
        val lintelC = Col.mix(hs.wall, hs.wallD, 0.7f); val frameC = Col.mix(hs.wallD, Pal.WOOD_D, 0.3f)
        val litFrame = Col.mix(Pal.WINDOW_LIT, Pal.WOOD_D, 0.55f)
        val corniceL = Col.mix(hs.wall, Col.hex(0xFFFFFF), 0.3f)
        c.polyFill { px, py ->
            val d = F * xw / max(1f, px + 0.5f - vx)
            val z = (gy(d) - (py + 0.5f)) / pm(d)
            val along = d - hs.d0
            val bay = (along - 0.8f) / 2.7f; val bi = floor(bay).toInt(); val bf = (bay - bi) * 2.7f
            val inBays = bay >= 0f && along < hs.d1 - hs.d0 - 0.6f
            val door = inBays && bi == 1 && bf in 0.7f..1.6f && z < 2.3f
            val winZ = (z in 1.0f..2.3f) || (hs.floors == 2 && z in 3.8f..5.1f)
            val win = inBays && !door && bf in 0.8f..1.5f && winZ
            val shutter = inBays && !door && (bf in 0.52f..0.8f || bf in 1.5f..1.78f) && winZ
            val sill = inBays && !door && bf in 0.7f..1.6f && (abs(z - 0.95f) < 0.12f || (hs.floors == 2 && abs(z - 3.75f) < 0.12f))
            val flower = sill && summer && Noise.rnd(px, py, 19) < 0.7f
            c.penEmissive = win && lit
            if (k == 1) when {
                z < 0.45f -> if (Noise.rnd(px, py, 21) < 0.3f) Pal.STONE_D else Pal.STONE_M
                door -> if (bf < 0.78f || bf > 1.52f || z > 2.2f) Pal.WOOD_X else if (abs(bf - 1.15f) < 0.04f) Col.scale(Pal.DOOR, 0.8f) else Pal.DOOR
                flower -> if (Noise.rnd(px, py, 23) < 0.6f) Pal.GERANIUM else Pal.LEAF
                sill -> Pal.WOOD_M
                win -> if (lit) (if (bf < 1.0f && (z % 2.8f) > 1.8f) Pal.WINDOW_LIT_HI else Pal.WINDOW_LIT) else if (abs(bf - 1.15f) < 0.05f || abs(z - 1.65f) < 0.06f || abs(z - 4.45f) < 0.06f) hs.wallD else if (bf < 1.0f) Pal.GLASS_HI else Pal.GLASS
                shutter -> if (((z * 6f).toInt() and 1) == 0) Pal.SHUTTER else Col.scale(Pal.SHUTTER, 0.8f)
                z > top - 0.3f -> hs.wallD
                else -> if (Noise.rnd(px, py, 25) < 0.05f) hs.wallD else hs.wall
            } else {
                // closer up: a picture pixel is [one] m here
                val one = 1f / pm(d)
                val zs = if (z < 2.5f) 0.95f else 3.75f
                val wz0 = if (z < 2.5f) 1.0f else 3.8f; val wz1 = wz0 + 1.3f
                when {
                    z < 0.45f -> {
                        // the socle: dressed stones in courses
                        val rw = floor(z / 0.15f); val sx = (along + rw * 0.17f) / 0.34f
                        if (z - rw * 0.15f < one || (sx - floor(sx)) * 0.34f < one) Pal.STONE_D
                        else if (z > 0.45f - one) Pal.STONE_L
                        else if (Noise.rnd(px, py, 21) < 0.15f) Col.mix(Pal.STONE_M, Pal.STONE_D, 0.5f) else Pal.STONE_M
                    }
                    door -> when {
                        bf < 0.78f || bf > 1.52f || z > 2.2f -> Pal.WOOD_X
                        abs(bf - 1.15f) < 0.04f -> Col.scale(Pal.DOOR, 0.8f)
                        abs(bf - 1.06f) < 1.2f * one && abs(z - 1.05f) < 1.2f * one -> Pal.GOLD
                        abs(z - 0.55f) < one || abs(z - 1.45f) < one || abs(z - 2.05f) < one -> Col.scale(Pal.DOOR, 0.72f)
                        (abs(bf - 0.86f) < one || abs(bf - 1.44f) < one) && z > 0.45f -> Col.scale(Pal.DOOR, 0.72f)
                        abs(bf - 0.9f) < one && z > 0.55f && z < 2.05f -> Col.mix(Pal.DOOR, Pal.WOOD_M, 0.4f)
                        else -> Pal.DOOR
                    }
                    sill && summer -> when {
                        // the flower box on the sill, geraniums over it
                        z < zs - 0.02f -> if (z > zs - 0.02f - one) Pal.WOOD_L else if (z < zs - 0.12f + one) Pal.WOOD_D else Pal.WOOD_M
                        Noise.rnd(px / 2, py / 2, 23) < 0.55f -> if ((px + py) % 3 == 0) Pal.GERANIUM_D else Pal.GERANIUM
                        Noise.rnd(px, py, 24) < 0.3f -> Col.hex(0x2E6A2C)
                        else -> Pal.LEAF
                    }
                    sill -> if (z > zs + 0.12f - one) Pal.WOOD_L else if (z < zs - 0.12f + one) Pal.WOOD_D else Pal.WOOD_M
                    win -> when {
                        // the frame round the glass, the mullion and the transom, panes lit or reflecting the sky
                        bf < 0.8f + one || bf > 1.5f - one || z < wz0 + one || z > wz1 - one -> if (lit) litFrame else frameC
                        abs(bf - 1.15f) < 0.05f || abs(z - (wz0 + 0.65f)) < 0.06f -> if (lit) litFrame else hs.wallD
                        lit -> if (bf < 1.0f && (z % 2.8f) > 1.8f) Pal.WINDOW_LIT_HI else Pal.WINDOW_LIT
                        (bf - 0.8f) + (wz1 - z) * 0.5f < 0.28f -> Col.mix(Pal.GLASS_HI, Col.hex(0xFFFFFF), 0.25f)
                        bf < 1.0f -> Pal.GLASS_HI
                        else -> Pal.GLASS
                    }
                    shutter -> when {
                        bf < 0.52f + one || bf > 1.78f - one || (bf > 0.8f - one && bf < 1.5f + one) -> Col.scale(Pal.SHUTTER, 0.7f)
                        z < wz0 + one || z > wz1 - one -> Col.scale(Pal.SHUTTER, 0.7f)
                        ((z * 14f).toInt() and 1) == 0 -> Pal.SHUTTER
                        else -> Col.scale(Pal.SHUTTER, 0.82f)
                    }
                    // a stone lintel over each window
                    inBays && !door && bf in 0.72f..1.58f && winZ.not() && (abs(z - 2.36f) < 0.06f || (hs.floors == 2 && abs(z - 5.16f) < 0.06f)) -> lintelC
                    z > top - 0.3f -> {
                        val cz = z - (top - 0.3f)
                        if (cz < 0.07f) hs.wallD else if (cz < 0.15f) corniceL else if (cz < 0.21f) hs.wallD else Col.mix(hs.wall, hs.wallD, 0.5f)
                    }
                    else -> if (Noise.rnd(px, py, 25) < 0.05f) hs.wallD else hs.wall
                }
            }
        }
        c.penEmissive = false
        if (lit) for (d in listOf(hs.d0 + 2.0f, hs.d0 + 4.7f)) if (d < hs.d1 - 0.6f) s.light(gx(xw, d), gy(d) - 1.8f * pm(d), 16f * pm(d) / 12f / k, 0.45f * env.windows)
        roof(xw, hs.d0, hs.d1, top)
        // a chimney
        val cd = (hs.d0 + hs.d1) / 2f + 1.2f
        val cxp = gx(xw + 1.6f, cd); val cp = pm(cd); val cy = gy(cd) - (top + 2.6f) * cp
        c.fillRect((cxp - 0.3f * cp).toInt(), (cy - 1.4f * cp).toInt(), max(2, (0.6f * cp).toInt()), (1.4f * cp).toInt(), Pal.STONE_M)
        if (k > 1) {
            // its bricks in courses, lit on the left, a cap over it
            val x0 = (cxp - 0.3f * cp).toInt(); val cw = max(2, (0.6f * cp).toInt())
            val course = max(2f, 0.14f * cp)
            var y = cy - course
            var n = 0
            while (y > cy - 1.4f * cp) {
                c.hline(x0, x0 + cw - 1, y.toInt(), Pal.STONE_D)
                val off = if (n % 2 == 0) cw / 3 else 2 * cw / 3
                c.vline(x0 + off, y.toInt() + 1, (y + course).toInt() - 1, Pal.STONE_D)
                y -= course; n++
            }
            c.vline(x0, (cy - 1.4f * cp).toInt(), cy.toInt() - 1, Pal.STONE_L)
            c.vline(x0 + cw - 1, (cy - 1.4f * cp).toInt(), cy.toInt() - 1, Pal.STONE_D)
            val capH = max(1, (0.1f * cp).toInt())
            c.fillRect((cxp - 0.4f * cp).toInt(), (cy - 1.4f * cp).toInt() - capH, (0.8f * cp).toInt() + 1, capH, Pal.STONE_X)
            c.hline((cxp - 0.4f * cp).toInt(), (cxp + 0.4f * cp).toInt(), (cy - 1.4f * cp).toInt() - capH, Pal.STONE_M)
        }
        c.hline((cxp - 0.4f * cp).toInt(), (cxp + 0.4f * cp).toInt(), (cy - 1.4f * cp).toInt(), Pal.STONE_X)
        if (env.month !in 6..8 || env.dark > 0.5f) s.smoke(cxp, cy - 1.6f * cp, 0.6f, (hs.d0 * 3).toInt())
    }

    /** The tiled roof over a house front: from the eave above the wall up and away to the ridge, rows of tiles. */
    private fun roof(xw: Float, d0: Float, d1: Float, top: Float) {
        val k = detail
        val e0 = floatArrayOf(gx(xw - 0.4f, d0 - 0.4f), gy(d0 - 0.4f) - (top + 0.1f) * pm(d0 - 0.4f))
        val e1 = floatArrayOf(gx(xw - 0.4f, d1 + 0.4f), gy(d1 + 0.4f) - (top + 0.1f) * pm(d1 + 0.4f))
        val r0 = floatArrayOf(gx(xw + 2.3f, d0 - 0.4f), gy(d0 - 0.4f) - (top + 3.2f) * pm(d0 - 0.4f))
        val r1 = floatArrayOf(gx(xw + 2.3f, d1 + 0.4f), gy(d1 + 0.4f) - (top + 3.2f) * pm(d1 + 0.4f))
        val snow = env.snow
        val tileL = Col.mix(Pal.ROOF_M, Pal.ROOF_L, 0.6f); val tileS = Col.mix(Pal.ROOF_M, Pal.ROOF_D, 0.4f)
        c.polyBegin(); c.polyAdd(e0[0], e0[1]); c.polyAdd(e1[0], e1[1]); c.polyAdd(r1[0], r1[1]); c.polyAdd(r0[0], r0[1])
        c.polyFill { px, py ->
            // how far up from the eave line this pixel is, in rows of tiles that shrink with depth
            val f = ((px - e0[0]) / (e1[0] - e0[0])).coerceIn(0f, 1f)
            val ey = e0[1] + (e1[1] - e0[1]) * f
            val d = d0 + (d1 - d0) * f
            val rowPx = max(1.5f, 0.32f * pm(d) * 0.7f)
            val up = (ey - py) / rowPx
            val rowI = floor(up).toInt()
            val tf = ((d / 0.3f + rowI * 0.5f) % 1f)
            val joint = tf * 0.3f * pm(d) < 1f && 0.3f * pm(d) >= 3f
            if (k == 1) when {
                snow -> if (up - rowI < 0.2f && Dither.at(px, py) < 0.4f) Pal.SNOW_M else Pal.SNOW_L
                up - rowI < 0.22f -> Pal.ROOF_D
                joint -> Pal.ROOF_D
                up - rowI > 0.75f -> Pal.ROOF_L
                else -> Pal.ROOF_M
            } else {
                // closer up each tile is round: a lit crown, a shaded side, its lip lit, some weathered darker
                val tilePx = 0.3f * pm(d)
                val base = if (Noise.rnd(floor(d / 0.3f + rowI * 0.5f).toInt(), rowI, 26) < 0.2f) Col.mix(Pal.ROOF_M, Pal.ROOF_D, 0.3f) else Pal.ROOF_M
                when {
                    snow -> if (up - rowI < 0.2f && Dither.at(px, py) < 0.4f) Pal.SNOW_M else Pal.SNOW_L
                    up - rowI < 0.22f -> Pal.ROOF_D
                    joint -> Pal.ROOF_D
                    up - rowI > 0.75f -> Pal.ROOF_L
                    tilePx >= 4f && tf > 0.12f && tf < 0.42f -> if (base == Pal.ROOF_M) tileL else Col.mix(tileL, Pal.ROOF_D, 0.25f)
                    tilePx >= 4f && tf > 0.82f -> tileS
                    else -> base
                }
            }
        }
        val th = if (k == 1) 1 else k - 1
        for (i in 0 until th) {
            c.line(e0[0].toInt(), e0[1].toInt() + 1 + i, e1[0].toInt(), e1[1].toInt() + 1 + i, if (i == 0) Pal.WOOD_X else Pal.WOOD_D)
            c.line(r0[0].toInt(), r0[1].toInt() - 1 - i, r1[0].toInt(), r1[1].toInt() - 1 - i, if (snow) Pal.SNOW_L else Col.hex(0x55200F))
        }
    }

    /** A window with shutters, front on: [ww] × [hh] px at ([x], [y]) (its top left). */
    private fun window(x: Int, y: Int, ww: Int, hh: Int, lit: Boolean, sh: Int) {
        if (ww < 2 || hh < 2) return
        val k = detail
        c.fillRect(x - sh, y, sh, hh, Pal.SHUTTER); c.fillRect(x + ww, y, sh, hh, Pal.SHUTTER)
        if (lit) glow { c.fillRect(x, y, ww, hh, Pal.WINDOW_LIT) } else { c.fillRect(x, y, ww, hh, Pal.GLASS); c.fillRect(x, y, max(1, ww / 3), max(1, hh / 3), Pal.GLASS_HI) }
        c.vline(x + ww / 2, y, y + hh - 1, Pal.WALL_D)
        c.hline(x - 1, x + ww, y + hh, Pal.WOOD_M)
        if (k == 1) return
        // the shutters' slats and frames, the transom and frame of the window, the sill's shade
        val slat = Col.scale(Pal.SHUTTER, 0.78f); val frame = Col.scale(Pal.SHUTTER, 0.68f)
        for (yy in y + 1 until y + hh - 1) if ((yy - y) % 3 == 2) { c.hline(x - sh + 1, x - 2, yy, slat); c.hline(x + ww + 1, x + ww + sh - 2, yy, slat) }
        c.vline(x - sh, y, y + hh - 1, frame); c.vline(x - 1, y, y + hh - 1, frame)
        c.vline(x + ww, y, y + hh - 1, frame); c.vline(x + ww + sh - 1, y, y + hh - 1, frame)
        c.hline(x - sh, x - 1, y, frame); c.hline(x + ww, x + ww + sh - 1, y, frame)
        c.hline(x, x + ww - 1, y + hh / 2, Pal.WALL_D); c.hline(x, x + ww - 1, y, Pal.WALL_D)
        if (!lit) c.line(x + 1, y + hh / 2 - 2, x + ww / 3, y + 1, Col.mix(Pal.GLASS_HI, Col.hex(0xFFFFFF), 0.3f))
        c.hline(x - 1, x + ww, y + hh + 1, Col.scale(Pal.WOOD_M, 0.7f))
    }

    /** A flower box under a window of [ww] px whose sill is at row [y]: a plank box, geraniums spilling over it. */
    private fun windowBox(x: Int, y: Int, ww: Int, p: Float) {
        val bh = max(2, (0.18f * p).toInt())
        val bx = x - 1; val bw = ww + 2
        plank(bx, y - bh + 1, bw, bh, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_D, 94)
        val fh = max(2, (0.2f * p).toInt())
        for (yy in y - bh - fh + 1..y - bh) for (xx in bx..bx + bw - 1) {
            val n = Noise.rnd(xx / 2, yy / 2, 95)
            val edge = yy < y - bh - fh + 3 && Noise.rnd(xx, yy, 96) < 0.5f
            if (edge) continue
            c.set(xx, yy, if (n < 0.5f) (if ((xx + yy) % 3 == 0) Pal.GERANIUM_D else Pal.GERANIUM) else if (n < 0.75f) Pal.LEAF else Col.hex(0x2E6A2C))
        }
    }

    /** The bell ringing ([level] 0..1): rings of its sound spreading from the tower on both sides, fading as they go. */
    private fun ringing(x: Float, y: Float, p: Float, level: Float) {
        if (level <= 0.05f) return
        val ring = Pal.GOLD_L
        val th = max(1, detail / 2)
        s.fx {
            c.penEmissive = true
            for (i in 0 until 3) {
                val u = ((t * 0.8 + i / 3.0) % 1.0).toFloat()
                val r = (1.2f + 3f * u) * p
                val a = (1f - u) * level
                val steps = max(6, (r * 1.6f).toInt())
                for (side in intArrayOf(-1, 1)) for (q in 0..steps) {
                    val ang = (q.toFloat() / steps - 0.5f) * 1.1f
                    val px = (x + side * cos(ang) * r).toInt(); val py = (y + sin(ang) * r).toInt()
                    for (o in 0 until th) c.blend(px + side * o, py, ring, a)
                }
            }
            c.penEmissive = false
        }
    }

    /**
     * The church closing the square: the nave's front with a round window and the arched door, the bell tower
     * beside it with the clock (lit at night), the bell in its arches and the spire with a gold cross.
     */
    private fun church() {
        val k = detail
        val d = churchD; val p = pm(d); val by = gy(d)
        val lit = env.windows > 0.35f
        val wall = Pal.WALL_L; val wallD = Pal.WALL_M
        // the nave: front wall and gable, the roof's edge
        val nx0 = gx(-3.4f, d); val nx1 = gx(4.6f, d); val nm = gx(0.6f, d)
        c.polyBegin(); c.polyAdd(nx0, by); c.polyAdd(nx1, by); c.polyAdd(nx1, by - 8.5f * p); c.polyAdd(nm, by - 13f * p); c.polyAdd(nx0, by - 8.5f * p)
        if (k == 1) c.polyFill { px, py -> if (py > by - 0.5f * p) Pal.STONE_M else if (px > nm + (nx1 - nm) * 0.8f) wallD else wall }
        else c.polyFill { px, py ->
            if (py > by - 0.5f * p) (if (((by - py) / p) % 0.25f < 1f / p || ((px - nx0) / p + (((by - py) / p / 0.25f).toInt() and 1) * 0.2f) % 0.4f < 1f / p) Pal.STONE_D else Pal.STONE_M)
            else if (px > nm + (nx1 - nm) * 0.8f) wallD else if (Noise.rnd(px, py, 27) < 0.04f) wallD else wall
        }
        val roofEdge = if (env.snow) Pal.SNOW_L else Pal.ROOF_D
        for (i in 0 until (if (k == 1) 1 else max(2, (0.3f * p).toInt()))) {
            val col = if (i == 0) roofEdge else if (env.snow) Pal.SNOW_M else Pal.ROOF_M
            c.line(nx0.toInt() - 1, (by - 8.5f * p).toInt() - i, nm.toInt(), (by - 13.2f * p).toInt() - 1 - i, col)
            c.line(nm.toInt(), (by - 13.2f * p).toInt() - 1 - i, nx1.toInt() + 1, (by - 8.5f * p).toInt() - i, col)
        }
        // the round window and the door
        val rwy = by - 9.5f * p
        if (k > 1) c.fillCircle(nm, rwy, 0.9f * p + max(1f, 0.15f * p), Pal.STONE_L)
        c.fillCircle(nm, rwy, 0.9f * p, if (lit) Pal.WINDOW_LIT else Pal.GLASS)
        if (lit) glow { c.fillCircle(nm, rwy, 0.7f * p, Pal.WINDOW_LIT) }
        if (k > 1) {
            // the rose's tracery
            val tr = if (lit) Col.mix(Pal.WINDOW_LIT, Pal.WOOD_D, 0.6f) else Pal.STONE_M
            for (a in 0 until 8) { val ang = a * PI / 4; c.line(nm.toInt(), rwy.toInt(), (nm + cos(ang) * 0.9f * p).toInt(), (rwy + sin(ang) * 0.9f * p).toInt(), tr) }
            c.fillCircle(nm, rwy, max(1f, 0.25f * p), tr)
            if (!lit) c.set((nm - 0.45f * p).toInt(), (rwy - 0.3f * p).toInt(), Pal.GLASS_HI)
            // the portal's stones round the door
            c.fillRect((nm - 0.8f * p).toInt() - 1, (by - 3f * p).toInt(), (1.6f * p).toInt() + 2, (3f * p).toInt(), Pal.STONE_L)
            c.fillCircle(nm, by - 3f * p, 0.8f * p + 1f, Pal.STONE_L)
        }
        c.fillRect((nm - 0.8f * p).toInt(), (by - 3f * p).toInt(), (1.6f * p).toInt(), (3f * p).toInt(), Pal.DOOR)
        c.fillCircle(nm, by - 3f * p, 0.8f * p, Pal.DOOR)
        if (k > 1) {
            // planks, iron hinges, a ring
            val plankC = Col.scale(Pal.DOOR, 0.75f)
            var px = nm - 0.8f * p + 0.4f * p
            while (px < nm + 0.8f * p - 1) { c.vline(px.toInt(), (by - 3.6f * p).toInt(), by.toInt() - 1, plankC); px += 0.4f * p }
            for (hy in floatArrayOf(0.9f, 2.2f)) c.hline((nm - 0.8f * p).toInt(), (nm - 0.2f * p).toInt(), (by - hy * p).toInt(), Pal.STONE_X)
            c.set((nm + 0.3f * p).toInt(), (by - 1.5f * p).toInt(), Pal.GOLD)
        }
        for (i in 0 until k) c.hline((nm - 1.2f * p).toInt(), (nm + 1.2f * p).toInt(), by.toInt() - 1 - i, if (i == k - 1) Pal.STONE_L else Pal.STONE_M)
        // the tower
        val tx0 = gx(4.6f, d); val tx1 = gx(7.4f, d); val tm = (tx0 + tx1) / 2f
        val shaft = 14.5f * p
        c.fillRect(tx0.toInt(), (by - shaft).toInt(), (tx1 - tx0).toInt(), shaft.toInt(), wall)
        c.fillRect((tx1 - 0.25f * (tx1 - tx0)).toInt(), (by - shaft).toInt(), max(1, (0.25f * (tx1 - tx0)).toInt()), shaft.toInt(), wallD)
        c.hline(tx0.toInt(), tx1.toInt(), (by - 0.5f * p).toInt(), Pal.STONE_M)
        c.hline(tx0.toInt() - 1, tx1.toInt(), (by - 11f * p).toInt(), Pal.STONE_D)
        if (k > 1) {
            // the socle, the string course's lit top, corner stones up the tower
            c.fillRect(tx0.toInt(), (by - 0.5f * p).toInt(), (tx1 - tx0).toInt(), (0.5f * p).toInt() + 1, Pal.STONE_M)
            c.hline(tx0.toInt(), tx1.toInt(), (by - 0.5f * p).toInt(), Pal.STONE_L)
            c.hline(tx0.toInt() - 1, tx1.toInt(), (by - 11f * p).toInt() - 1, Pal.STONE_L)
            var qy = by - 1.2f * p; var n = 0
            while (qy > by - shaft + 0.5f * p) {
                c.fillRect(tx0.toInt(), qy.toInt(), max(2, (0.35f * p * (if (n % 2 == 0) 1f else 0.6f)).toInt()), max(1, (0.3f * p).toInt()), Col.mix(wall, Pal.STONE_L, 0.5f))
                c.hline(tx0.toInt(), tx0.toInt() + max(2, (0.35f * p).toInt()) - 1, qy.toInt() + max(1, (0.3f * p).toInt()), wallD)
                qy -= 0.6f * p; n++
            }
        }
        // the clock, as far as the town clock (the project "mestna_ura") has come: a round window made in the tower for
        // it, then the face lifted in (stopped at twelve), then running and lit at night
        val cy = by - 9.3f * p; val cr = 0.85f * p
        val ura = stage(CLOCK); val uraDone = ura >= stages(CLOCK)
        if (ura < 2) {
            // a plain tower: a narrow slit window
            c.fillRect((tm - max(1f, 0.12f * p)).toInt(), (cy - 0.6f * p).toInt(), max(1, (0.24f * p).toInt()), max(2, (1.2f * p).toInt()), Col.hex(0x2A2226))
        } else if (ura < stages(CLOCK) - 1) {
            // the round window, dark inside, its stone ring closer up
            if (k > 1) c.fillCircle(tm, cy, cr + max(1f, 0.15f * p), Pal.STONE_L)
            c.fillCircle(tm, cy, cr, Col.hex(0x2A2226))
        } else clockFace(tm, cy, cr, p, lit, running = uraDone)
        // the bell chamber; the bell swings when it rings (a dialog's "bell")
        val bw = max(2f, 0.9f * p)
        // tapped ([pokes]), it swings and rings a few times, dying down
        val tolled = poked("tower")?.age(t)?.let { a -> PokeArt.ease(a, 0f, 0.25f) * (1f - PokeArt.ease(a, 2f, 3.2f)) } ?: 0f
        val bell = max(fxOn("bell"), tolled)
        val swing = sin(t * 5.5).toFloat() * bell
        ringing(tm, by - 12.9f * p, p, bell)
        if (k == 1) {
            c.fillRect((tm - bw / 2).toInt(), (by - 13.8f * p).toInt(), bw.toInt(), (1.6f * p).toInt(), Col.hex(0x2A2226))
            c.set(tm.toInt() + (if (swing > 0.45f) 1 else if (swing < -0.45f) -1 else 0), (by - 12.8f * p).toInt(), Pal.GOLD)
        } else {
            // an arched opening with its stone frame, the bell hanging in it, lit on its left
            val ot = by - 13.8f * p; val oh = 1.6f * p
            c.fillRect((tm - bw / 2).toInt() - 1, (ot + bw / 2).toInt(), bw.toInt() + 2, (oh - bw / 2).toInt(), Pal.STONE_L)
            c.fillCircle(tm, ot + bw / 2, bw / 2 + 1f, Pal.STONE_L)
            c.fillRect((tm - bw / 2).toInt(), (ot + bw / 2).toInt(), bw.toInt(), (oh - bw / 2).toInt(), Col.hex(0x2A2226))
            c.fillCircle(tm, ot + bw / 2, bw / 2, Col.hex(0x2A2226))
            val bt = ot + 0.35f * p; val bb = ot + 1.15f * p
            for (y in bt.toInt()..bb.toInt()) {
                val u = (y - bt) / max(1f, bb - bt)
                val half = (0.16f + 0.2f * u * u) * p
                val bx = tm + swing * 0.2f * p * u // swinging from its top
                c.hline((bx - half).toInt(), (bx + half).toInt(), y, Pal.GOLD)
                c.set((bx - half).toInt(), y, Pal.GOLD_L)
            }
            val lip = tm + swing * 0.2f * p
            c.hline((lip - 0.36f * p).toInt(), (lip + 0.36f * p).toInt(), bb.toInt(), Col.scale(Pal.GOLD, 0.7f))
            c.set((tm - swing * 0.12f * p).toInt(), bb.toInt() + 1, Col.scale(Pal.GOLD, 0.6f))
            c.hline((tm - bw / 2).toInt() - 1, (tm + bw / 2).toInt() + 1, (ot + oh).toInt(), Pal.STONE_M)
        }
        // the spire, a ball and a cross
        val sTop = by - 20.5f * p
        val slate = Col.hex(0x5C5560); val slateD = Col.hex(0x3E3842)
        for (y in sTop.toInt()..(by - shaft).toInt()) {
            val u = (y - sTop) / max(1f, by - shaft - sTop)
            val half = (tx1 - tx0) / 2f * u + 0.5f
            val sn = env.snow && u < 0.4f
            c.hline((tm - half).toInt(), (tm + half).toInt(), y, if (sn) Pal.SNOW_M else Col.hex(0x5C5560))
            c.hline(tm.toInt() + 1, (tm + half).toInt(), y, if (sn) Pal.SNOW_D else Col.hex(0x3E3842))
            if (k > 1 && !sn) {
                // courses of slates, the lit edge
                if (((y - sTop) / max(2f, 0.45f * p)).let { it - floor(it) } < 1f / max(2f, 0.45f * p)) c.hline((tm - half).toInt(), (tm + half).toInt(), y, Col.mix(slate, slateD, 0.6f))
                c.set((tm - half).toInt(), y, Col.hex(0x7C7480))
            }
        }
        if (k == 1) {
            c.vline(tm.toInt(), sTop.toInt() - max(3, (1.2f * p).toInt()), sTop.toInt() - 1, Pal.GOLD)
            c.hline(tm.toInt() - 1, tm.toInt() + 1, sTop.toInt() - max(2, (0.8f * p).toInt()), Pal.GOLD)
        } else {
            // a gold ball, the cross over it, lit on its left
            val th = k - 1
            val ct = sTop.toInt() - (1.3f * p).toInt()
            c.fillRect(tm.toInt() - th / 2, ct, th, sTop.toInt() - ct, Pal.GOLD)
            c.fillRect(tm.toInt() - (0.45f * p).toInt(), ct + (0.35f * p).toInt(), (0.9f * p).toInt() + 1, th, Pal.GOLD)
            c.vline(tm.toInt() - th / 2, ct, sTop.toInt() - 1, Pal.GOLD_L)
            c.fillCircle(tm + 0.5f, sTop - 0.5f, max(1.2f, 0.22f * p), Pal.GOLD)
            c.set(tm.toInt() - 1, sTop.toInt() - 1, Pal.GOLD_L)
        }
    }

    /** The tower's clock face at ([tm], [cy]), [cr] px round: [running], the hands at the hour and lit at night; else stopped at twelve. */
    private fun clockFace(tm: Float, cy: Float, cr: Float, p: Float, lit: Boolean, running: Boolean) {
        val k = detail
        if (k > 1) c.fillCircle(tm, cy, cr + max(1f, 0.15f * p), Pal.GOLD)
        c.fillCircle(tm, cy, cr, if (lit && running) Pal.WINDOW_LIT else Col.hex(0xF7F5EE))
        if (lit && running) { glow { c.fillCircle(tm, cy, cr, Pal.WINDOW_LIT) }; s.light(tm, cy, 10f, 0.4f) }
        if (cr >= 2.5f) {
            val hour = if (running) s.hour else 0f
            val hr = ((hour % 12f) / 12f) * 2 * PI; val mn = ((hour % 1f)) * 2 * PI
            if (k > 1) for (a in 0 until 12) { val ang = a * PI / 6; c.set((tm + sin(ang) * cr * 0.78f).toInt(), (cy - cos(ang) * cr * 0.78f).toInt(), if (a % 3 == 0) Pal.OUTLINE else Pal.STONE_D) }
            if (k > 1) thick(tm, cy, (tm + sin(hr) * cr * 0.5f).toFloat(), (cy - cos(hr) * cr * 0.5f).toFloat(), 2, Pal.OUTLINE)
            else c.line(tm.toInt(), cy.toInt(), (tm + sin(hr) * cr * 0.5f).toInt(), (cy - cos(hr) * cr * 0.5f).toInt(), Pal.OUTLINE)
            c.line(tm.toInt(), cy.toInt(), (tm + sin(mn) * cr * 0.8f).toInt(), (cy - cos(mn) * cr * 0.8f).toInt(), Pal.OUTLINE)
        } else c.set(tm.toInt(), cy.toInt(), Pal.OUTLINE)
    }

    // ------------------------------------------------------------------ the maypole

    /**
     * The maypole (mlaj) in the square before the church, as far as the village has put it up (the project "mlaj"):
     * a peg with a ribbon where it will stand (the spruce chosen in the forest), the spruce lying on trestles (brought
     * in), peeled but for its green top (peeled), the wreath and the ribbons ready beside it (woven); raised, it stands
     * with the wreath below its crown and the ribbons fluttering. Scenery: no word of its own here.
     */
    private fun maypole() {
        val st = stage(MAYPOLE)
        if (st <= 0) return
        val done = st >= stages(MAYPOLE)
        if (done) items.add(poleD to { prop { mlajStanding() } })
        else items.add(poleD - 2f to { prop { mlajLying(st) } })
    }

    private val poleX = -1.2f; private val poleD = 26f

    /** Wood stripped of its bark, as on the village map's maypole. */
    private val stripped = Col.hex(0xE8D4A8); private val strippedD = Col.hex(0xC2A472)

    private fun mlajStanding() {
        val k = detail
        val p = pm(poleD); val bx = gx(poleX, poleD); val by = gy(poleD)
        val top = by - 10.5f * p
        val half = max(1f, 0.13f * p)
        // a ring of stones round its foot
        for (j in 0 until 6) c.fillEllipse(bx - 0.6f * p + j * 0.24f * p, by - 0.05f * p, max(1f, 0.13f * p), max(1f, 0.08f * p), if (j % 2 == 0) Pal.STONE_L else Pal.STONE_M)
        // the pole, lit on its left, thinner toward the top
        for (y in max(top.toInt(), c.top)..min(by.toInt(), c.bottom - 1)) {
            val u = (by - y) / (by - top)
            val hw = half * (1f - 0.45f * u)
            for (x in (bx - hw).toInt()..(bx + hw).toInt()) c.set(x, y, if (x < bx - hw * 0.2f) stripped else strippedD)
        }
        // the green crown: a young spruce's top, snowy in winter
        spruceTop(bx, top, 2.3f * p)
        // the wreath below it, the ribbons fluttering from it
        val wy = top + 2.6f * p; val wr = 0.6f * p
        c.fillEllipse(bx, wy, wr, max(1f, wr * 0.35f), Pal.LEAF)
        c.fillEllipse(bx, wy - max(1f, wr * 0.12f), wr * 0.7f, max(1f, wr * 0.2f), Col.hex(0x2E6A2A))
        c.fillRect((bx - half).toInt(), (wy - wr * 0.4f).toInt(), max(1, (2 * half).toInt()), max(1, (wr * 0.35f).toInt()), stripped)
        if (!env.snow) for (j in 0 until 4) c.set((bx - wr + j * wr * 0.66f).toInt(), wy.toInt(), Pal.PAINTED[j % 6])
        val cols = intArrayOf(Pal.FLAG_RED, Pal.FLAG_WHITE, Pal.FLAG_BLUE, Pal.GOLD)
        for (j in 0 until 4) {
            val ax = bx - wr * 0.8f + j * wr * 0.53f
            val len = (1.6f + 0.3f * (j % 2)) * p
            val n = max(3, len.toInt())
            for (q in 0..n) {
                val f = q / n.toFloat()
                val sway = sin(t * 2.2 + j * 1.3 + f * 3.0).toFloat() * f * 0.35f * p * gust
                c.fillRect((ax + sway).toInt(), (wy + f * len).toInt(), max(1, k), max(1, k), cols[j])
            }
        }
    }

    /** A young spruce's top: tiers of branches narrowing up to a point, from [top] down [len] px. */
    private fun spruceTop(bx: Float, top: Float, len: Float) {
        val pine = env.pine
        for (y in max(top.toInt(), c.top)..min((top + len).toInt(), c.bottom - 1)) {
            val u = (y - top) / len
            val tier = (u * 4f) - floor(u * 4f)
            val hw = (0.5f + u * 0.5f) * len * 0.38f * (0.55f + 0.45f * tier)
            for (x in (bx - hw).toInt()..(bx + hw).toInt()) {
                val lit = x < bx - hw * 0.3f
                c.set(x, y, if (env.snow && tier < 0.3f) Pal.SNOW_L else if (lit) pine[3] else if (tier > 0.8f) pine[0] else pine[1])
            }
        }
    }

    /**
     * The maypole before it stands: a peg with a red ribbon where it will stand; the spruce lying on two trestles, in its
     * bark with its branches; peeled but for the green top; the wreath and a heap of ribbons ready on the cobbles beside it.
     */
    private fun mlajLying(st: Int) {
        val k = detail
        val p = pm(poleD); val bx = gx(poleX, poleD); val by = gy(poleD)
        // the peg marking the spot, a red ribbon on it
        c.fillRect(bx.toInt(), (by - 0.8f * p).toInt(), max(1, k), (0.8f * p).toInt(), Pal.WOOD_L)
        c.fillRect(bx.toInt() + k, (by - 0.75f * p).toInt(), max(1, (0.3f * p).toInt()), max(1, k), Pal.FLAG_RED)
        if (st < 2) return
        // the tree on trestles across the square, in front of the spot, its foot to the left
        val d = poleD - 2f; val lp = pm(d); val ly = gy(d) - 0.7f * lp
        val x0 = gx(-6.2f, d); val x1 = gx(4.8f, d)
        for (tx in floatArrayOf(-4.6f, 1.6f)) {
            val tpx = gx(tx, d)
            c.line((tpx - 0.35f * lp).toInt(), gy(d).toInt(), (tpx + 0.35f * lp).toInt(), ly.toInt(), Pal.WOOD_D)
            c.line((tpx + 0.35f * lp).toInt(), gy(d).toInt(), (tpx - 0.35f * lp).toInt(), ly.toInt(), Pal.WOOD_M)
        }
        val peeled = st >= 3
        val bark = if (peeled) stripped else Col.hex(0x6A5040); val barkD = if (peeled) strippedD else Col.hex(0x4A382C)
        val crownFrom = x1 - 2.4f * lp
        for (x in max(x0.toInt(), c.left)..min(crownFrom.toInt(), c.right - 1)) {
            val u = (x - x0) / (x1 - x0)
            val hw = max(1f, 0.2f * lp * (1f - 0.55f * u))
            for (y in (ly - hw).toInt()..(ly + hw).toInt()) c.set(x, y, if (y < ly - hw * 0.2f) bark else barkD)
        }
        // in its bark it still has its branches along the trunk; peeled, only the top is green
        if (!peeled) for (j in 0 until 7) {
            val ax = x0 + (crownFrom - x0) * (0.25f + j * 0.1f)
            c.line(ax.toInt(), ly.toInt(), (ax + 0.5f * lp).toInt(), (ly - 0.6f * lp * (if (j % 2 == 0) 1f else -0.6f)).toInt(), env.pine[1])
        }
        // the green top, lying: tiers of branches toward the tip
        val pine = env.pine
        for (x in max(crownFrom.toInt(), c.left)..min(x1.toInt(), c.right - 1)) {
            val u = (x - crownFrom) / max(1f, x1 - crownFrom)
            val tier = (u * 4f) - floor(u * 4f)
            val hw = (1f - u * 0.8f) * 0.7f * lp * (0.55f + 0.45f * (1f - tier))
            for (y in (ly - hw).toInt()..(ly + hw).toInt()) c.set(x, y, if (env.snow && y < ly - hw * 0.4f) Pal.SNOW_L else if (y < ly - hw * 0.3f) pine[3] else pine[1])
        }
        if (st < 4) return
        // the wreath and the ribbons ready on the cobbles beside it
        val wx = gx(poleX + 1.6f, poleD - 3.4f); val wy = gy(poleD - 3.4f); val wp = pm(poleD - 3.4f)
        c.fillEllipse(wx, wy - 0.12f * wp, 0.55f * wp, max(1f, 0.2f * wp), Pal.LEAF)
        c.fillEllipse(wx, wy - 0.14f * wp, 0.3f * wp, max(1f, 0.1f * wp), Col.hex(0x6A7A5A))
        if (!env.snow) for (j in 0 until 5) c.fillRect((wx - 0.5f * wp + j * 0.25f * wp).toInt(), (wy - 0.1f * wp).toInt(), max(1, k), max(1, k), Pal.PAINTED[j % 6])
        val cols = intArrayOf(Pal.FLAG_RED, Pal.FLAG_WHITE, Pal.FLAG_BLUE, Pal.GOLD)
        for (j in 0 until 4) c.fillRect((wx + 0.8f * wp).toInt(), (wy - (0.05f + j * 0.06f) * wp).toInt(), max(2, (0.7f * wp).toInt()), max(1, k), cols[j])
    }

    /** A cottage behind the garden, its gable toward us. */
    private fun cottage(x: Float, d: Float) {
        val k = detail
        val p = pm(d); val by = gy(d)
        val x0 = gx(x - 2.4f, d); val x1 = gx(x + 2.4f, d); val xm = gx(x, d)
        c.polyBegin(); c.polyAdd(x0, by); c.polyAdd(x1, by); c.polyAdd(x1, by - 3.2f * p); c.polyAdd(xm, by - 6f * p); c.polyAdd(x0, by - 3.2f * p)
        if (k == 1) c.polyFill { px, _ -> if (px > xm + (x1 - xm) * 0.6f) Pal.OCHRE_M else Pal.OCHRE_L }
        else c.polyFill { px, py ->
            if (py > by - 0.45f * p) (if (Noise.rnd(px / 3, py / 2, 28) < 0.2f) Pal.STONE_D else Pal.STONE_M)
            else if (px > xm + (x1 - xm) * 0.6f) Pal.OCHRE_M else if (Noise.rnd(px, py, 29) < 0.06f) Pal.OCHRE_M else Pal.OCHRE_L
        }
        val rf = if (env.snow) Pal.SNOW_L else Pal.SHINGLE_M
        if (k == 1) {
            c.line(x0.toInt() - 2, (by - 3f * p).toInt(), xm.toInt(), (by - 6.3f * p).toInt(), rf); c.line(xm.toInt(), (by - 6.3f * p).toInt(), x1.toInt() + 2, (by - 3f * p).toInt(), rf)
            c.line(x0.toInt() - 2, (by - 3f * p).toInt() + 1, xm.toInt(), (by - 6.3f * p).toInt() + 1, Pal.SHINGLE_D); c.line(xm.toInt(), (by - 6.3f * p).toInt() + 1, x1.toInt() + 2, (by - 3f * p).toInt() + 1, Pal.SHINGLE_D)
        } else for (i in 0 until 2 * k) {
            // the roof's edge: its shingles lit on top, the shade under it
            val col = if (i == 0) (if (env.snow) Pal.SNOW_L else Pal.SHINGLE_L) else if (i < k) (if (i % 2 == 0) Pal.SHINGLE_D else rf) else Pal.SHINGLE_D
            c.line(x0.toInt() - 2 * k, (by - 3f * p).toInt() + i, xm.toInt(), (by - 6.3f * p).toInt() + i, col)
            c.line(xm.toInt(), (by - 6.3f * p).toInt() + i, x1.toInt() + 2 * k, (by - 3f * p).toInt() + i, col)
        }
        window((xm - 0.5f * p).toInt(), (by - 2.4f * p).toInt(), max(2, (0.9f * p).toInt()), max(2, (1.1f * p).toInt()), env.windows > 0.35f, max(1, (0.3f * p).toInt()))
    }

    /** The garden fence along the left of the square, pickets from depth [far] to [near], two rails. */
    private fun fence(far: Float, near: Float) {
        val k = detail
        var d = far
        var prev: FloatArray? = null
        while (d >= near) {
            val p = pm(d); val x = gx(fenceX, d); val by = gy(d)
            val hh = 1.1f * p
            if (k == 1) {
                c.vline(x.toInt(), (by - hh).toInt(), by.toInt(), Pal.WOOD_L)
                if (p > 12f) c.vline(x.toInt() + 1, (by - hh).toInt() + 1, by.toInt(), Pal.WOOD_M)
                c.set(x.toInt(), (by - hh).toInt() - 1, if (env.snow) Pal.SNOW_L else Pal.WOOD_L)
                prev?.let { q ->
                    for (hgt in floatArrayOf(0.35f, 0.8f)) c.line(q[0].toInt(), (q[1] - hgt * q[2]).toInt(), x.toInt(), (by - hgt * p).toInt(), Pal.WOOD_M)
                }
            } else {
                // a picket: lit on its left, shaded on its right, a pointed top
                val pw = max(1, (0.08f * p).toInt())
                val xi = x.toInt(); val top = (by - hh).toInt()
                c.fillRect(xi, top, pw, by.toInt() - top + 1, Pal.WOOD_L)
                if (pw >= 2) c.vline(xi + pw - 1, top + 1, by.toInt(), Pal.WOOD_M)
                if (pw >= 3) c.vline(xi + pw - 2, top + 1, by.toInt(), Col.mix(Pal.WOOD_L, Pal.WOOD_M, 0.5f))
                val tip = if (env.snow) Pal.SNOW_L else Pal.WOOD_L
                if (pw >= 3) { c.hline(xi + 1, xi + pw - 2, top - 1, tip); c.set(xi + pw / 2, top - 2, tip) } else c.set(xi, top - 1, tip)
                prev?.let { q ->
                    for (hgt in floatArrayOf(0.35f, 0.8f)) {
                        val ya = q[1] - hgt * q[2]; val yb = by - hgt * p
                        val ta = max(1.2f, 0.06f * q[2]); val tb = max(1.2f, 0.06f * p)
                        val qx = q[0]
                        c.polyBegin(); c.polyAdd(qx, ya - ta / 2); c.polyAdd(x, yb - tb / 2); c.polyAdd(x, yb + tb / 2); c.polyAdd(qx, ya + ta / 2)
                        c.polyFill { px, py ->
                            val f = ((px + 0.5f - qx) / (x - qx)).coerceIn(0f, 1f)
                            val th = ta + (tb - ta) * f
                            val v = (py + 0.5f - (ya + (yb - ya) * f - th / 2)) / th
                            if (v < 0.34f) Pal.WOOD_L else if (v > 0.7f) Pal.WOOD_D else Pal.WOOD_M
                        }
                    }
                }
            }
            prev = floatArrayOf(x, by, p)
            d -= max(0.14f, d * 0.03f)
        }
    }

    // ------------------------------------------------------------------ the linden

    /** The linden's trunk: thick, furrowed bark, roots in the cobbles, the limbs going up into the crown. */
    private fun lindenTrunk() {
        val k = detail
        val p = pm(lindenD); val bx = gx(lindenX, lindenD); val by = gy(lindenD)
        val half = 0.6f * p; val top = by - 4.4f * p
        val barkL = Col.hex(0x8A7458); val bark = Col.hex(0x6A5640); val barkD = Col.hex(0x46382A)
        val barkX = Col.hex(0x362A20); val ridgeL = Col.mix(bark, barkL, 0.5f)
        for (py in max(top.toInt(), c.top) until min(by.toInt(), c.bottom)) {
            val u = (by - py) / (by - top)
            val flare = if (u < 0.15f) (0.15f - u) * 5f * half else 0f
            val hw = half + flare
            for (px in max((bx - hw).toInt(), c.left)..min((bx + hw).toInt(), c.right - 1)) {
                val f = (px + 0.5f - bx) / hw
                val s1 = sin((px - bx) * 1.3f / k + Noise.v1(py * 0.2f / k, 3) * 3f + (if (k > 1) Noise.v1(py * 0.7f / k, 5) * 0.9f else 0f))
                val furrow = s1 > 0.6f
                c.set(px, py, if (k == 1) when { f < -0.55f -> barkL; f > 0.5f -> barkD; furrow -> barkD; else -> bark }
                else when {
                    // closer up: the furrows deep in their middles, the ridges lit along their crests, cracks across the plates
                    f < -0.55f -> if (s1 > 0.8f) bark else if (s1 < -0.8f) Col.mix(barkL, Col.hex(0xFFF0C8), 0.15f) else barkL
                    f > 0.5f -> if (s1 > 0.8f) barkX else barkD
                    s1 > 0.85f -> barkX
                    furrow -> barkD
                    s1 < -0.8f -> ridgeL
                    Noise.rnd(px, py / 3, 4) < 0.05f -> barkD
                    else -> bark
                })
            }
        }
        for ((a, len) in listOf(-0.7f to 3.2f, -0.1f to 3.6f, 0.35f to 2.4f)) {
            val ex = bx + sin(a) * len * p; val ey = top - cos(a) * len * p
            val th = max(2, (0.35f * p).toInt())
            for (i in 0 until th) c.line((bx - th / 2 + i).toInt(), top.toInt() + 2 * k, (ex - th / 3 + i * 0.6f).toInt(), ey.toInt(), if (i == 0) barkL else if (i == th - 1) barkD else bark)
        }
        if (env.snow) for (i in 0 until k) c.hline((bx - half * 1.8f).toInt(), (bx + half * 1.8f).toInt(), by.toInt() - 1 - i, Pal.SNOW_L)
    }

    /**
     * The linden's crown over the top left: big clumps of heart-shaped leaves lit from the sun's side, yellow in
     * autumn, bare branches with snow in winter, pale blossom in early summer.
     */
    private fun lindenCrown() {
        val k = detail
        val p = pm(lindenD); val bx = gx(lindenX, lindenD); val top = gy(lindenD) - 4.4f * p
        val season = env.season
        if (season == Season.WINTER) {
            val bark = Col.hex(0x5A4838)
            fun branch(x0: Float, y0: Float, a: Float, len: Float, depth: Int) {
                val x1 = x0 + sin(a) * len; val y1 = y0 - cos(a) * len
                if (k == 1) {
                    c.line(x0.toInt(), y0.toInt(), x1.toInt(), y1.toInt(), bark)
                    if (depth > 1) c.line(x0.toInt() + 1, y0.toInt(), x1.toInt() + 1, y1.toInt(), bark)
                    c.set(((x0 + x1) / 2).toInt(), ((y0 + y1) / 2).toInt() - 1, Pal.SNOW_L)
                } else {
                    // tapering limbs, snow lying along their tops
                    val th = max(1, ((depth + 1) * k) / 2)
                    thick(x0, y0, x1, y1, th, bark)
                    val n = (len * 0.5f).toInt()
                    for (j in 0 until n) { val f = 0.25f + j * 0.5f / max(1, n); c.set((x0 + (x1 - x0) * f).toInt(), (y0 + (y1 - y0) * f - th / 2f - 1).toInt(), Pal.SNOW_L) }
                }
                if (depth > 0) { branch(x1, y1, a - 0.42f, len * 0.68f, depth - 1); branch(x1, y1, a + 0.38f, len * 0.64f, depth - 1) }
            }
            for ((a, len) in listOf(-0.7f to 3.2f, -0.1f to 3.6f, 0.35f to 2.4f)) branch(bx + sin(a) * len * p, top - cos(a) * len * p, a, 2.4f * p, 3)
            return
        }
        val (dark, mid, light) = when (season) {
            Season.AUTUMN -> Triple(Col.hex(0x9A6A1E), Col.hex(0xD0A032), Col.hex(0xF0CC5A))
            Season.SPRING -> Triple(Col.hex(0x3E7A30), Col.hex(0x6AAA46), Col.hex(0xA8D870))
            else -> Triple(Col.hex(0x2A5A2C), Col.hex(0x3E7A38), Col.hex(0x68A44C))
        }
        val deep = Col.scale(dark, 0.78f); val shine = Col.mix(light, Col.hex(0xFFF0C8), 0.3f)
        val sunLeft = (sunAt()?.first ?: vx) < bx
        val lx = if (sunLeft) -1f else 1f
        val clumps = listOf(
            floatArrayOf(-4.8f, 6.2f, 3.0f), floatArrayOf(-1.6f, 7.4f, 3.0f), floatArrayOf(0.6f, 5.8f, 2.4f), floatArrayOf(-6.8f, 3.6f, 2.6f),
            floatArrayOf(1.4f, 3.6f, 1.9f), floatArrayOf(-3.0f, 3.4f, 2.6f), floatArrayOf(-0.4f, 3.8f, 2.2f), floatArrayOf(-2.6f, 9.6f, 2.8f),
            floatArrayOf(0.2f, 9.2f, 2.2f), floatArrayOf(-6.0f, 8.8f, 2.6f),
        )
        // closer up the front clumps go first and the ones behind fill only what is left (each pixel worked out once)
        val me = c.penId
        for (i in if (k == 1) clumps.indices else clumps.indices.reversed()) {
            val cl = clumps[i]
            val cx = bx + cl[0] * p; val cy = top - cl[1] * p; val rr = cl[2] * p
            for (yy in max(max(0, (cy - rr).toInt()), c.top)..min(min(h - 1, (cy + rr).toInt()), c.bottom - 1)) for (xx in max(max(0, (cx - rr).toInt()), c.left)..min(min(w - 1, (cx + rr).toInt()), c.right - 1)) {
                val dx = (xx + 0.5f - cx) / rr; val dy = (yy + 0.5f - cy) / (rr * 0.85f)
                val q = dx * dx + dy * dy
                if (q > 1f || (k > 1 && c.ids[c.index(xx, yy)] == me)) continue
                // closer up the leaves show one by one; at the clump's edge the gaps between them let the sky through
                val one = if (k > 1) leafLight(xx, yy, 40 + i) else 0f
                if (if (k == 1) q > 0.82f && Dither.at(xx, yy) < 0.45f else q > 0.86f && one < -0.2f) continue
                val leaf = Noise.v2(xx * 0.35f / k, yy * 0.35f / k, 40 + i)
                var lit = -dx * lx * 0.5f - dy * 0.75f + (leaf - 0.5f) * 0.7f
                lit += one
                var col = if (k > 1 && lit > 0.75f) shine else if (lit > 0.4f) light else if (lit > -0.25f) mid else if (k > 1 && lit < -0.7f) deep else dark
                if (season == Season.SUMMER && env.month == 6 && Noise.rnd(xx, yy, 41) < 0.05f && lit > 0f) col = Col.hex(0xF0E6A0)
                c.set(xx, yy, col)
            }
        }
    }

    /**
     * The light on the single leaf at ([x], [y]), closer up: two staggered layers of leaves on a jittered grid, the
     * front ones first; each lit on its upper left, shaded at its foot, the back ones darker; -0.3 in the gaps.
     */
    private fun leafLight(x: Int, y: Int, seed: Int): Float {
        val sz = 2 * detail + 2
        for (layer in 0..1) {
            val o = layer * sz / 2
            val gx0 = Math.floorDiv(x + o, sz); val gy0 = Math.floorDiv(y + o, sz)
            val hh = Noise.hash(gx0, gy0, seed * 2 + layer)
            val jx = ((hh and 255) / 255f - 0.5f) * sz * 0.3f; val jy = (((hh ushr 8) and 255) / 255f - 0.5f) * sz * 0.3f
            val lxx = (x + o + 0.5f - (gx0 * sz + sz / 2f + jx)) / (sz * 0.46f)
            val lyy = (y + o + 0.5f - (gy0 * sz + sz / 2f + jy)) / (sz * 0.36f)
            if (lxx * lxx + lyy * lyy > 1f) continue
            val tone = (((hh ushr 16) and 255) / 255f - 0.5f) * 0.3f - layer * 0.14f
            return tone + if (lyy < -0.25f && lxx < 0.35f) 0.3f else if (lyy > 0.5f) -0.2f else 0.02f
        }
        return -0.3f
    }

    // ------------------------------------------------------------------ the small things (drawn at their own size, where they stand)

    private fun bench(cx: Int, by: Int) {
        val m = detail
        if (m == 1) {
            c.fillRect(cx - 21, by - 14, 42, 3, Pal.WOOD_L); c.fillRect(cx - 21, by - 10, 42, 3, Pal.WOOD_L)
            c.vline(cx - 18, by - 15, by - 4, Pal.WOOD_D); c.vline(cx + 17, by - 15, by - 4, Pal.WOOD_D)
            c.fillRect(cx - 22, by - 4, 44, 4, Pal.WOOD_L); c.hline(cx - 22, cx + 21, by - 1, Pal.WOOD_D)
            c.fillRect(cx - 19, by, 3, 8, Pal.WOOD_M); c.fillRect(cx + 16, by, 3, 8, Pal.WOOD_M)
            c.vline(cx - 17, by, by + 7, Pal.WOOD_D); c.vline(cx + 18, by, by + 7, Pal.WOOD_D)
            if (env.snow) c.hline(cx - 22, cx + 21, by - 5, Pal.SNOW_L)
            return
        }
        // closer up: the back's two slats and the seat's two planks, lit on top, their grain; the posts and legs; screws
        val lite = Col.mix(Pal.WOOD_L, Col.hex(0xFFF0C8), 0.3f)
        plank(cx - 21 * m, by - 14 * m, 42 * m, 3 * m, Pal.WOOD_L, lite, Pal.WOOD_M, 91)
        plank(cx - 21 * m, by - 10 * m, 42 * m, 3 * m, Pal.WOOD_L, lite, Pal.WOOD_M, 92)
        for (px in intArrayOf(cx - 18 * m, cx + 17 * m)) {
            c.fillRect(px, by - 15 * m, m, 11 * m, Pal.WOOD_D)
            c.vline(px, by - 15 * m, by - 4 * m - 1, Pal.WOOD_M)
            c.hline(px, px + m - 1, by - 15 * m, Pal.WOOD_M)
            for (sy in intArrayOf(by - 14 * m, by - 10 * m)) { c.set(px + m / 2, sy + 3 * m / 2, Col.hex(0x4A4A52)); c.set(px + m / 2 - 1, sy + 3 * m / 2 - 1, Col.hex(0xB8B8C0)) }
        }
        val sh = 3 * m / 2
        plank(cx - 22 * m, by - 4 * m, 44 * m, sh, Pal.WOOD_L, lite, Pal.WOOD_M, 93)
        plank(cx - 22 * m, by - 4 * m + sh + 1, 44 * m, 3 * m - sh - 1, Pal.WOOD_L, Col.mix(Pal.WOOD_L, Pal.WOOD_M, 0.3f), Pal.WOOD_M, 94)
        c.hline(cx - 22 * m, cx + 22 * m - 1, by - 4 * m + sh, Pal.WOOD_D)
        c.fillRect(cx - 22 * m, by - m, 44 * m, m, Pal.WOOD_M)
        c.hline(cx - 22 * m, cx + 22 * m - 1, by - 1, Pal.WOOD_D)
        for (lx in intArrayOf(cx - 19 * m, cx + 16 * m)) {
            c.fillRect(lx, by, 3 * m, 8 * m, Pal.WOOD_M)
            c.vline(lx, by, by + 8 * m - 1, Pal.WOOD_L)
            c.fillRect(lx + 2 * m, by, m, 8 * m, Pal.WOOD_D)
            c.hline(lx, lx + 3 * m - 1, by + 8 * m - 1, Pal.WOOD_X)
        }
        if (env.snow) for (i in 1..m) c.hline(cx - 22 * m, cx + 22 * m - 1, by - 4 * m - i, if (i == m) Pal.SNOW_L else Pal.SNOW_M)
    }

    /** The cat asleep on the bench: a sprite, like the people. */
    private fun cat(cx: Int, by: Int) = sprite(cx, by) {
        val fur = Col.hex(0xE0A050); val furD = Col.hex(0xB07830); val white = Col.hex(0xF8F4EC)
        // curled up asleep, tail round the body, an ear twitching now and then
        c.fillEllipse(cx + 0.5f, by - 3f, 6.5f, 3.4f, fur)
        for (k in 0..2) c.hline(cx - 3 + k * 3, cx - 2 + k * 3, by - 5 + (k % 2), furD)
        c.fillRect(cx + 3, by - 7, 5, 4, fur); c.set(cx + 3, by - 8, furD); c.set(cx + 7, by - 8 - (if (sin(t * 0.9) > 0.8) 1 else 0), furD)
        c.set(cx + 4, by - 6, Pal.OUTLINE); c.set(cx + 6, by - 6, Pal.OUTLINE); c.set(cx + 5, by - 5, Col.hex(0xE0A0A0))
        c.hline(cx - 6, cx - 1, by - 1, furD); c.set(cx - 7, by - 2, furD)
        c.fillRect(cx - 1, by - 2, 3, 2, white)
    }

    private fun well(cx: Int, by: Int) {
        val m = detail
        if (m == 1) {
            // stone drum
            for (y in by - 20 until by) {
                c.hline(cx - 14, cx + 14, y, Pal.STONE_M)
                for (x in cx - 14..cx + 14 step 5) c.set(x + ((y / 4) % 2) * 2, y, Pal.STONE_D)
                if (y % 4 == 0) c.hline(cx - 14, cx + 14, y, Pal.STONE_D)
            }
            c.fillEllipse(cx + 0.5f, by - 20f, 14.5f, 4f, Pal.STONE_L); c.fillEllipse(cx + 0.5f, by - 20f, 11f, 2.6f, Pal.STONE_X)
            c.fillEllipse(cx + 0.5f, by - 20f, 9.5f, 2f, if (env.snow) Pal.ICE_M else Pal.WATER_D); c.set(cx - 4, by - 21, if (env.snow) Pal.ICE_L else Pal.WATER_M)
            // posts, windlass, rope and bucket, the little roof
            c.fillRect(cx - 12, by - 44, 3, 24, Pal.WOOD_D); c.fillRect(cx + 10, by - 44, 3, 24, Pal.WOOD_D)
            c.vline(cx - 12, by - 44, by - 21, Pal.WOOD_M); c.vline(cx + 10, by - 44, by - 21, Pal.WOOD_M)
            c.fillRect(cx - 10, by - 38, 20, 3, Pal.WOOD_M); c.hline(cx - 10, cx + 9, by - 38, Pal.WOOD_L)
            c.fillRect(cx + 13, by - 40, 2, 5, Pal.WOOD_X); c.hline(cx + 13, cx + 17, by - 40, Pal.WOOD_X); c.vline(cx + 17, by - 40, by - 37, Pal.WOOD_X)
            for (y in by - 35..by - 27) c.set(cx, y, Pal.CANVAS_M)
            c.fillRect(cx - 3, by - 27, 7, 5, Pal.WOOD_M); c.hline(cx - 3, cx + 3, by - 27, Pal.STONE_D); c.vline(cx - 3, by - 27, by - 23, Pal.WOOD_L)
            val rl = if (env.snow) Pal.SNOW_L else Pal.ROOF_L; val rm = if (env.snow) Pal.SNOW_M else Pal.ROOF_M
            for (y in by - 54..by - 44) { val u = (y - (by - 54)).toFloat() / 10; val half = (2 + 16 * u).toInt(); c.hline(cx - half, cx + half, y, if (y % 3 == 0 && !env.snow) Pal.ROOF_D else if (u < 0.3f) rl else rm) }
            c.hline(cx - 18, cx + 18, by - 43, Pal.WOOD_D)
            return
        }
        // closer up: the drum's stones curving round it, narrower toward its sides and lit on the left
        val axis = cx + 0.5f * m; val rx = 14.5f * m
        val top = by - 20 * m
        val courseH = 20f * m / 7f
        val stoneA = 0.42f
        for (y in max(top, c.top) until min(by, c.bottom)) {
            val cy = (y - top) / courseH; val ci = cy.toInt(); val fy = (cy - ci) * courseH
            for (x in max(cx - 14 * m, c.left)..min(cx + 15 * m - 1, c.right - 1)) {
                val u = ((x + 0.5f - axis) / rx).coerceIn(-0.999f, 0.999f)
                val th = asin(u)
                val sa = th / stoneA + (ci and 1) * 0.5f + 10f
                val si = sa.toInt(); val fs = (sa - si) * stoneA * rx * sqrt(1f - u * u)
                val tone = Noise.rnd(si, ci, 92)
                var col = if (tone < 0.3f) Col.mix(Pal.STONE_M, Pal.STONE_L, 0.4f) else if (tone > 0.8f) Col.mix(Pal.STONE_M, Pal.STONE_D, 0.35f) else Pal.STONE_M
                col = when {
                    fy < 1f || fs < 1f -> Pal.STONE_X
                    fy < 2f -> Col.mix(col, Pal.STONE_L, 0.5f)
                    fy > courseH - 1.5f -> Col.mix(col, Pal.STONE_D, 0.4f)
                    else -> col
                }
                if (u < -0.6f) col = Col.mix(col, Pal.STONE_L, 0.3f) else if (u > 0.55f) col = Col.mix(col, Pal.STONE_X, (u - 0.55f) * 1.2f)
                c.set(x, y, col)
            }
        }
        // the rim: coping stones lit on top, the dark shaft, the water with the sky in it
        val rcy = by - 20f * m
        c.fillEllipse(axis, rcy, 14.5f * m, 4f * m, Pal.STONE_L)
        for (a in 0 until 12) {
            val ang = a * PI / 6 + 0.26
            c.line((axis + cos(ang) * 11f * m).toInt(), (rcy + sin(ang) * 2.6f * m).toInt(), (axis + cos(ang) * 14.3f * m).toInt(), (rcy + sin(ang) * 3.9f * m).toInt(), Pal.STONE_M)
        }
        c.fillEllipse(axis, rcy + 0.6f * m, 14.3f * m, 3.4f * m, Pal.STONE_M)
        c.fillEllipse(axis, rcy, 14.5f * m, 3.5f * m, Pal.STONE_L)
        for (a in 0 until 12) {
            val ang = a * PI / 6 + 0.26
            c.line((axis + cos(ang) * 11.2f * m).toInt(), (rcy + sin(ang) * 2.7f * m).toInt(), (axis + cos(ang) * 14.2f * m).toInt(), (rcy + sin(ang) * 3.4f * m).toInt(), Col.mix(Pal.STONE_L, Pal.STONE_D, 0.5f))
        }
        c.fillEllipse(axis, rcy, 11f * m, 2.6f * m, Pal.STONE_X)
        if (env.snow) {
            c.fillEllipse(axis, rcy, 9.5f * m, 2f * m, Pal.ICE_M)
            c.line((axis - 5f * m).toInt(), (rcy - 0.5f * m).toInt(), (axis + 1f * m).toInt(), (rcy - 1.2f * m).toInt(), Pal.ICE_L)
        } else {
            c.fillEllipse(axis, rcy, 9.5f * m, 2f * m, Pal.WATER_D)
            c.fillEllipse(axis - 2f * m, rcy - 0.4f * m, 5f * m, 0.9f * m, Pal.WATER_M)
            c.hline((axis - 5f * m).toInt(), (axis - 2f * m).toInt(), (rcy - 0.7f * m).toInt(), Pal.WATER_L)
            // a ring on the water, spreading
            val rp = ((t * 0.6) % 1.0).toFloat()
            for (a in 0 until 24) { val ang = a * PI / 12; c.set((axis + 1.5f * m + cos(ang) * (1f + rp * 5f) * m).toInt(), (rcy + 0.3f * m + sin(ang) * (0.25f + rp * 1.1f) * m).toInt(), if (rp < 0.6f) Pal.WATER_M else Pal.WATER_D) }
        }
        // the posts, their grain; the windlass with the rope wound round it; the crank
        val grain = Col.mix(Pal.WOOD_D, Pal.WOOD_X, 0.5f)
        for (px in intArrayOf(cx - 12 * m, cx + 10 * m)) {
            c.fillRect(px, by - 44 * m, 3 * m, 24 * m, Pal.WOOD_D)
            for (gc in px + 1 until px + 3 * m - 1) if (Noise.rnd(gc, 97) < 0.4f) for (yy in max(by - 44 * m, c.top) until min(by - 20 * m, c.bottom)) if (Noise.rnd(gc, yy / 4, 98) < 0.5f) c.set(gc, yy, grain)
            c.vline(px, by - 44 * m, by - 21 * m, Pal.WOOD_M)
            c.vline(px + 3 * m - 1, by - 44 * m, by - 21 * m, Pal.WOOD_X)
        }
        val wy = by - 38 * m
        c.fillRect(cx - 10 * m, wy, 20 * m, 3 * m, Pal.WOOD_M)
        c.hline(cx - 10 * m, cx + 10 * m - 1, wy, Pal.WOOD_L); c.hline(cx - 10 * m, cx + 10 * m - 1, wy + 1, Col.mix(Pal.WOOD_L, Pal.WOOD_M, 0.5f))
        c.hline(cx - 10 * m, cx + 10 * m - 1, wy + 3 * m - 1, Pal.WOOD_D)
        for (y in wy until wy + 3 * m) for (x in cx - 5 * m until cx + 5 * m) c.set(x, y, if ((x + y) % 3 == 0) Pal.CANVAS_D else if (y == wy) Pal.CANVAS_L else Pal.CANVAS_M)
        c.fillRect(cx + 13 * m, by - 40 * m, 2 * m, 5 * m, Pal.WOOD_X)
        c.fillRect(cx + 13 * m, by - 40 * m, 5 * m, m - 1, Col.hex(0x3A3A44))
        c.fillRect(cx + 17 * m, by - 40 * m, m, 4 * m, Pal.WOOD_M); c.vline(cx + 17 * m, by - 40 * m, by - 36 * m - 1, Pal.WOOD_L)
        // the rope, twisted, down to the bucket's handle
        val bt = by - 27 * m
        for (y in wy + 3 * m until bt - 2 * m) { c.set(cx, y, if (((y shr 1) and 1) == 0) Pal.CANVAS_M else Pal.CANVAS_L); if (m >= 3) c.set(cx + 1, y, if (((y shr 1) and 1) == 0) Pal.CANVAS_D else Pal.CANVAS_M) }
        // the bucket: staves, two iron hoops, a handle over it
        c.fillRect(cx - 3 * m, bt, 7 * m, 5 * m, Pal.WOOD_M)
        for (x in cx - 3 * m until cx + 4 * m) if ((x - cx) % 3 == 0) c.vline(x, bt + 1, bt + 5 * m - 1, Pal.WOOD_D)
        c.vline(cx - 3 * m, bt, bt + 5 * m - 1, Pal.WOOD_L)
        c.vline(cx + 4 * m - 1, bt, bt + 5 * m - 1, Pal.WOOD_D)
        for (hy in intArrayOf(bt, bt + m + 1, bt + 4 * m - 1)) c.hline(cx - 3 * m, cx + 4 * m - 1, hy, Pal.STONE_D)
        c.hline(cx - 3 * m, cx + 4 * m - 1, bt + 5 * m - 1, Pal.WOOD_X)
        for (a in 0..16) { val ang = PI * a / 16; c.set((cx + 0.5f * m + cos(ang) * 3.4f * m).toInt(), (bt - sin(ang) * 2f * m).toInt(), Pal.STONE_X) }
        // the little roof: courses of tiles, lit on its left, a ridge along its top; the eave board
        val rl = if (env.snow) Pal.SNOW_L else Pal.ROOF_L; val rm = if (env.snow) Pal.SNOW_M else Pal.ROOF_M
        val rt = by - 54 * m
        for (y in rt until by - 44 * m + m) {
            val u = (y - rt).toFloat() / (10 * m)
            val half = ((2 + 16 * u) * m).toInt()
            val cr = (y - rt) % (m + 1)
            for (x in max(cx - half, c.left)..min(cx + half, c.right - 1)) {
                val tc = Math.floorMod(x - cx + ((y - rt) / (m + 1)) * 2, 4)
                c.set(x, y, when {
                    env.snow -> if (cr == 0 && Dither.at(x, y) < 0.4f) Pal.SNOW_M else Pal.SNOW_L
                    y - rt < m -> Pal.ROOF_D
                    cr == 0 -> Pal.ROOF_D
                    tc == 0 -> Col.mix(rm, Pal.ROOF_D, 0.5f)
                    u < 0.3f || x < cx - half / 2 -> rl
                    else -> rm
                })
            }
        }
        c.fillRect(cx - 18 * m, by - 43 * m, 36 * m + 1, m, Pal.WOOD_D)
        c.hline(cx - 18 * m, cx + 18 * m, by - 43 * m, Pal.WOOD_M)
    }

    private fun lampPost(cx: Int, by: Int, lit: Boolean) {
        val m = detail
        s.newObject(Pal.OUTLINE)
        val iron = Col.hex(0x3A3A44); val ironL = Col.hex(0x5A5A66); val ironD = Col.hex(0x24242C)
        if (m == 1) {
            c.fillRect(cx - 1, by - 46, 3, 46, iron); c.vline(cx - 1, by - 45, by - 1, ironL)
            c.fillRect(cx - 3, by - 3, 7, 3, iron)
            c.fillRect(cx - 4, by - 56, 9, 10, iron); c.hline(cx - 5, cx + 5, by - 56, iron); c.set(cx, by - 57, ironL)
            if (lit) { glow { c.fillRect(cx - 3, by - 55, 7, 8, Pal.WINDOW_LIT); c.fillRect(cx - 1, by - 53, 3, 4, Pal.WINDOW_LIT_HI) }; s.light(cx.toFloat(), by - 50f, 44f, 0.75f * env.windows) }
            else c.fillRect(cx - 3, by - 55, 7, 8, Col.hex(0xC8D4E0))
            c.penId = 0
            return
        }
        // closer up: a fluted post with collars on a stepped foot; the lantern with its roof and finial, four panes in a frame, the flame
        c.fillRect(cx - m, by - 46 * m, 3 * m, 46 * m, iron)
        c.vline(cx - m, by - 45 * m, by - 1, ironL)
        c.vline(cx + m, by - 45 * m, by - 1, ironD)
        for (cy in intArrayOf(by - 13 * m, by - 31 * m, by - 45 * m)) { c.fillRect(cx - m - 1, cy, 3 * m + 2, max(2, m - 1), iron); c.hline(cx - m - 1, cx + 2 * m, cy, ironL) }
        c.fillRect(cx - 3 * m, by - 3 * m, 7 * m, 3 * m, iron); c.hline(cx - 3 * m, cx + 4 * m - 1, by - 3 * m, ironL)
        c.fillRect(cx - 2 * m, by - 4 * m, 5 * m, m, iron); c.hline(cx - 2 * m, cx + 3 * m - 1, by - 4 * m, ironL)
        c.fillRect(cx - 4 * m, by - 56 * m, 9 * m, 10 * m, iron)
        val rt = by - 56 * m
        for (j in 0 until 2 * m) { val half = (1 + j * 5 / (2 * m)) * m / 2 + j / 2; c.hline(cx + m / 2 - half, cx + m / 2 + half, rt - 2 * m + j, if (j == 0) ironL else iron) }
        c.hline(cx - 5 * m, cx + 5 * m + m - 1, rt, iron); c.hline(cx - 5 * m, cx + 5 * m + m - 1, rt + 1, ironL)
        c.fillCircle(cx + m / 2f, rt - 2 * m - 1f, max(1f, 0.6f * m), ironL)
        val gx0 = cx - 3 * m; val gy0 = by - 55 * m; val gw = 7 * m; val gh = 8 * m
        if (lit) {
            glow {
                c.fillRect(gx0, gy0, gw, gh, Pal.WINDOW_LIT)
                c.fillEllipse(cx + m / 2f, gy0 + gh * 0.55f, 1.6f * m, 2.2f * m, Pal.WINDOW_LIT_HI)
                c.fillEllipse(cx + m / 2f, gy0 + gh * 0.6f, 0.7f * m, 1.1f * m, Col.hex(0xFFFFF0))
            }
            s.light(cx.toFloat(), by - 50f * m, 44f, 0.75f * env.windows)
        } else {
            c.fillRect(gx0, gy0, gw, gh, Col.hex(0xC8D4E0))
            c.line(gx0 + 1, gy0 + gh / 2, gx0 + gw / 2, gy0 + 1, Col.hex(0xF0F4F8))
            c.fillEllipse(cx + m / 2f, gy0 + gh * 0.6f, 0.9f * m, 1.3f * m, Col.hex(0xE8E0C8))
        }
        val bar = if (lit) Col.mix(Pal.WINDOW_LIT, iron, 0.7f) else iron
        c.vline(cx + m / 2, gy0, gy0 + gh - 1, bar)
        c.hline(gx0, gx0 + gw - 1, gy0 + gh / 2 - (if (lit) gh / 4 else 0), bar)
        c.hline(gx0 - 1, gx0 + gw, gy0 + gh, ironL)
        c.penId = 0
    }

    private fun bicycle(cx: Int, by: Int) {
        val m = detail
        val frame = Col.hex(0x2E63B0); val tyre = Col.hex(0x2A2A30); val spoke = Col.hex(0x9AA0A8)
        if (m == 1) {
            for (wx in intArrayOf(cx - 10, cx + 10)) {
                for (a in 0 until 24) { val ang = a * PI / 12; c.set((wx + 0.5 + cos(ang) * 7).toInt(), (by - 7.5 + sin(ang) * 7).toInt(), tyre) }
                for (a in 0 until 8) { val ang = a * PI / 4 + t * 0; c.line(wx, by - 7, (wx + 0.5 + cos(ang) * 5.5).toInt(), (by - 7.5 + sin(ang) * 5.5).toInt(), spoke) }
                c.set(wx, by - 7, tyre)
            }
            c.line(cx - 10, by - 7, cx - 2, by - 17, frame); c.line(cx - 2, by - 17, cx + 7, by - 17, frame)
            c.line(cx - 2, by - 17, cx + 1, by - 7, frame); c.line(cx + 1, by - 7, cx + 7, by - 17, frame); c.line(cx + 7, by - 17, cx + 10, by - 7, frame)
            c.line(cx + 1, by - 7, cx + 10, by - 7, frame)
            c.hline(cx - 5, cx, by - 19, Pal.WOOD_X); c.set(cx - 3, by - 18, Pal.WOOD_X)
            c.hline(cx + 5, cx + 10, by - 21, Col.hex(0x3A3A44)); c.vline(cx + 7, by - 20, by - 18, Col.hex(0x3A3A44))
            c.fillRect(cx - 1, by - 8, 3, 2, Col.hex(0x3A3A44))
            c.fillRect(cx - 6, by - 14, 4, 3, Pal.WOOD_L) // a basket
            return
        }
        // closer up: tyres round silver rims, sixteen spokes, the hubs; a frame of tubes lit along their tops; the chain, the saddle, the bars
        fun at(v: Int) = v * m + m / 2f
        val frameL = Col.hex(0x6A9AE0); val rim = Col.hex(0xC8CCD4)
        for (wx in intArrayOf(-10, 10)) {
            val wcx = cx + at(wx); val wcy = by - 7.5f * m; val rr = 7f * m
            for (a in 0 until 16) { val ang = a * PI / 8 + 0.2; c.line(wcx.toInt(), wcy.toInt(), (wcx + cos(ang) * rr * 0.8f).toInt(), (wcy + sin(ang) * rr * 0.8f).toInt(), spoke) }
            val tw = max(1.5f, 0.9f * m)
            for (y in max((wcy - rr).toInt() - 1, c.top)..min((wcy + rr).toInt() + 1, c.bottom - 1)) for (x in max((wcx - rr).toInt() - 1, c.left)..min((wcx + rr).toInt() + 1, c.right - 1)) {
                val dx = x + 0.5f - wcx; val dy = y + 0.5f - wcy
                val r = sqrt(dx * dx + dy * dy)
                if (r > rr || r < rr - tw - 1f) continue
                c.set(x, y, if (r >= rr - tw) (if (dx + dy < -rr * 0.9f) Col.hex(0x4A4A52) else tyre) else rim)
            }
            c.fillCircle(wcx, wcy, max(1.2f, 0.8f * m), spoke); c.set(wcx.toInt(), wcy.toInt(), tyre)
        }
        val th = max(1, m - 1)
        val rear = floatArrayOf(cx + at(-10), by - 7.5f * m); val seat = floatArrayOf(cx + at(-2), by - at(17))
        val head = floatArrayOf(cx + at(7), by - at(17)); val bb = floatArrayOf(cx + at(1), by - 7.5f * m); val front = floatArrayOf(cx + at(10), by - 7.5f * m)
        // the chain from the chainring back to the rear hub
        c.line(bb[0].toInt(), (bb[1] - 1.2f * m).toInt(), rear[0].toInt(), (rear[1] - 0.6f * m).toInt(), Col.hex(0x4A4A52))
        c.line(bb[0].toInt(), (bb[1] + 1.2f * m).toInt(), rear[0].toInt(), (rear[1] + 0.6f * m).toInt(), Col.hex(0x4A4A52))
        for ((a, b) in listOf(rear to seat, seat to head, seat to bb, bb to head, head to front, bb to rear)) {
            thick(a[0], a[1], b[0], b[1], th, frame)
            if (m >= 3) c.line(a[0].toInt(), a[1].toInt() - 1, b[0].toInt(), b[1].toInt() - 1, frameL)
        }
        // the chainring and a pedal
        c.fillCircle(bb[0], bb[1], 1.5f * m, Col.hex(0x5A5A66)); c.fillCircle(bb[0], bb[1], 0.7f * m, Col.hex(0x3A3A44))
        thick(bb[0], bb[1], bb[0] + 1.5f * m, bb[1] + 1.8f * m, th, Col.hex(0x3A3A44))
        c.fillRect((bb[0] + 0.8f * m).toInt(), (bb[1] + 1.8f * m).toInt(), 2 * m, max(1, m - 1), Pal.WOOD_X)
        // the saddle on its post
        c.vline(seat[0].toInt(), (by - 19f * m).toInt(), seat[1].toInt(), Col.hex(0x5A5A66))
        c.fillEllipse(cx + at(-2) - 0.5f * m, by - 19f * m, 3f * m, 0.9f * m, Pal.WOOD_X)
        c.hline((cx + at(-2) - 2.8f * m).toInt(), (cx + at(-2) + 1.5f * m).toInt(), (by - 19.6f * m).toInt(), Col.hex(0x6A4A3A))
        // the bars and grips
        thick(cx + at(7), by - at(17), cx + at(7), by - at(20), th, Col.hex(0x3A3A44))
        thick(cx + at(5), by - at(21), cx + at(10), by - at(21), th, Col.hex(0x5A5A66))
        c.fillRect(cx + 10 * m, by - 21 * m, m, m, Pal.WOOD_X); c.fillRect(cx + 5 * m, by - 21 * m, m, m, Pal.WOOD_X)
        // the basket's weave
        val kx = cx - 6 * m; val ky = by - 14 * m
        for (y in ky until ky + 3 * m) for (x in kx until kx + 4 * m) c.set(x, y, if ((x - kx) % 3 == 0) Pal.WOOD_D else if ((((y - ky) / 2) + (x - kx) / 3) % 2 == 0) Pal.WOOD_L else Pal.WOOD_M)
        c.hline(kx, kx + 4 * m - 1, ky, Pal.WOOD_D)
    }

    private fun flowerBed(cx: Int, by: Int) {
        val m = detail
        if (m == 1) {
            c.fillRect(cx - 16, by - 4, 32, 4, Pal.STONE_M); c.hline(cx - 16, cx + 15, by - 4, Pal.STONE_L); for (x in cx - 16..cx + 15 step 4) c.set(x, by - 2, Pal.STONE_D)
            if (env.snow) { c.hline(cx - 15, cx + 14, by - 5, Pal.SNOW_L); c.hline(cx - 12, cx + 10, by - 6, Pal.SNOW_L); return }
            for (y in by - 9 until by - 4) c.hline(cx - 14, cx + 13, y, if (y == by - 9) Pal.LEAF else Col.hex(0x2E6A2C))
            val cols = if (env.season == Season.AUTUMN) intArrayOf(Col.hex(0xC06A1A), Pal.GOLD, Col.hex(0x8A3A5A)) else intArrayOf(Pal.GERANIUM, Pal.GOLD, Col.hex(0xFFFFFF), Col.hex(0xE070B0))
            for (k in 0 until 12) { val x = cx - 13 + k * 2 + (k % 2); val y = by - 10 - (k % 3); c.set(x, y, cols[k % cols.size]); if (k % 3 == 0) c.set(x, y - 1, cols[k % cols.size]) }
            return
        }
        // closer up: the box's dressed stones, lit along its top; a bushy mass of leaves; flowers with golden hearts over it
        val x0 = cx - 16 * m; val bw = 32 * m; val y0 = by - 4 * m
        c.fillRect(x0, y0, bw, 4 * m, Pal.STONE_M)
        for (y in y0 until by) for (x in x0 until x0 + bw) {
            val course = if (y < y0 + 2 * m) 0 else 1
            val sx = (x - x0 + course * 3 * m)
            val col = when {
                y == y0 -> Pal.STONE_L
                y == y0 + 1 -> Col.mix(Pal.STONE_L, Pal.STONE_M, 0.5f)
                y == y0 + 2 * m || sx % (6 * m) == 0 -> Pal.STONE_D
                y == by - 1 -> Pal.STONE_D
                Noise.rnd(x, y, 99) < 0.08f -> Col.mix(Pal.STONE_M, Pal.STONE_D, 0.5f)
                else -> Pal.STONE_M
            }
            c.set(x, y, col)
        }
        if (env.snow) {
            for (i in 0 until m) { c.hline(cx - 15 * m, cx + 15 * m - 1, by - 5 * m + i, Pal.SNOW_L); c.hline(cx - 12 * m, cx + 11 * m - 1, by - 6 * m + i, if (i == 0) Pal.SNOW_L else Pal.SNOW_M) }
            return
        }
        val leafD = Col.hex(0x2E6A2C); val leafM = Col.hex(0x3E8A3A)
        for (y in by - 9 * m until y0) for (x in cx - 14 * m until cx + 14 * m) {
            val edge = y < by - 9 * m + m && Noise.rnd(x / 2, 100) < 0.4f
            if (edge) continue
            val n = Noise.rnd(x / 2, y / 2, 101)
            c.set(x, y, if (y < by - 8 * m && n < 0.6f) Pal.LEAF else if (n < 0.3f) Pal.LEAF else if (n < 0.65f) leafM else leafD)
        }
        val cols = if (env.season == Season.AUTUMN) intArrayOf(Col.hex(0xC06A1A), Pal.GOLD, Col.hex(0x8A3A5A)) else intArrayOf(Pal.GERANIUM, Pal.GOLD, Col.hex(0xFFFFFF), Col.hex(0xE070B0))
        for (i in 0 until 12) {
            val fx = cx + (-13 + i * 2 + (i % 2)) * m + m / 2; val fy = by + (-10 - (i % 3)) * m + m / 2
            val pc = cols[i % cols.size]
            val heart = if (pc == Pal.GOLD) Col.hex(0x8A5A20) else Pal.GOLD_L
            c.vline(fx, fy + 1, fy + m, leafD)
            c.set(fx - 1, fy, pc); c.set(fx + 1, fy, pc); c.set(fx, fy - 1, pc); c.set(fx, fy + 1, Col.mix(pc, Pal.OUTLINE, 0.25f))
            if (m >= 3) { c.set(fx - 1, fy - 1, Col.mix(pc, Col.hex(0xFFFFFF), 0.35f)); c.set(fx + 1, fy + 1, Col.mix(pc, Pal.OUTLINE, 0.25f)) }
            c.set(fx, fy, heart)
            if (i % 3 == 0) { c.set(fx + m / 2, fy - m, pc); c.set(fx + m / 2, fy - m + 1, Col.mix(pc, Pal.OUTLINE, 0.2f)) }
        }
    }

    /**
     * The dog sitting on the square: a sprite, like the people. Tapped ([pokes]) it wags hard with its tongue out; the
     * next time it jumps up twice and barks.
     */
    private fun dog(bx: Int, ground: Int) {
        val pk = poked("dog"); val a = pk?.age(t) ?: 0f
        val jumps = pk?.step == 1 && a < 1.3f
        val hop = if (jumps) (abs(sin(a * PI.toFloat() / 0.65f)) * 5f).toInt() else 0
        val by = ground - hop * detail
        if (jumps && ((a * 1.6f).toInt() and 1) == 0) s.fx {
            // the bark
            val u = ((a * 1.6f) % 1f) * 1.6f
            PokeArt.rings(c, bx - 13f * detail, by - 15f * detail, u.coerceAtMost(0.99f), 7f * detail, Col.hex(0xFFFFFF), detail, 0.8f, n = 2)
        }
        dogSprite(bx, by, excited = pk != null && a < (if (pk.step == 1) 2f else 1.6f))
    }

    private fun dogSprite(bx: Int, by: Int, excited: Boolean) = sprite(bx, by) {
        val coat = Col.hex(0xA87444); val coatL = Col.hex(0xD4A878); val dark = Col.hex(0x3A2418)
        val wag = ((t * (if (excited) 12 else 4)).toInt() and 1)
        // sitting, facing left: haunches, chest, head with floppy ears, a wagging tail
        c.fillRect(bx - 2, by - 10, 9, 10, coat); c.fillEllipse(bx + 3.5f, by - 5f, 4.5f, 5f, coat)
        c.fillRect(bx - 5, by - 12, 6, 12, coat); c.fillRect(bx - 4, by - 8, 3, 7, coatL)
        c.fillRect(bx - 6, by - 2, 3, 2, coat); c.fillRect(bx + 1, by - 2, 3, 2, coatL)
        c.fillRect(bx - 9, by - 18, 8, 7, coat); c.fillRect(bx - 12, by - 15, 4, 3, coat); c.set(bx - 12, by - 14, dark)
        c.fillRect(bx - 3, by - 17, 3, 5, Col.hex(0x7A5230)); c.set(bx - 2, by - 12, Col.hex(0x7A5230))
        c.set(bx - 8, by - 16, Pal.OUTLINE); c.set(bx - 5, by - 16, Pal.OUTLINE); c.set(bx - 10, by - 13, Col.hex(0xE0A0A0))
        c.fillRect(bx - 8, by - 12, 5, 2, Pal.FLAG_RED) // collar
        c.line(bx + 7, by - 4, bx + 11, by - 9 + wag * 3, coat); c.set(bx + 11, by - 9 + wag * 3, coatL)
        if (excited) { c.set(bx - 11, by - 12, Col.hex(0xE87A8A)); c.set(bx - 11, by - 11, Col.hex(0xC85A6A)) } // the tongue out
    }

    private fun ball(cx: Int, by: Int, hgt: Int) {
        val m = detail
        val y = by - hgt
        if (m == 1) {
            if (hgt > 0) shadow(cx.toFloat(), by.toFloat(), 3.5f, 1.5f)
            c.fillCircle(cx + 0.5f, y - 3.5f, 4f, Col.hex(0xF4EEDD))
            val spin = ((t * 6).toInt() % 4)
            for (k in -3..3) c.set(cx + k, y - 4 + (if ((k + spin) % 3 == 0) 1 else 0), Pal.FLAG_RED)
            c.set(cx - 2, y - 6, Col.hex(0xFFFFFF)); c.set(cx + 2, y - 1, Col.hex(0xD8D0BC))
            return
        }
        // closer up: a round ball lit from above, its red band turning as it rolls, a glint
        if (hgt > 0) shadow(cx.toFloat(), by.toFloat(), 3.5f * m, 1.5f * m)
        val bcx = cx + 0.5f * m; val bcy = y - 3.5f * m; val rr = 4f * m
        val spin = (t * 6).toFloat()
        for (yy in max((bcy - rr).toInt(), c.top)..min((bcy + rr).toInt(), c.bottom - 1)) for (xx in max((bcx - rr).toInt(), c.left)..min((bcx + rr).toInt(), c.right - 1)) {
            val nx = (xx + 0.5f - bcx) / rr; val ny = (yy + 0.5f - bcy) / rr
            if (nx * nx + ny * ny > 1f) continue
            val lit = -nx * 0.55f - ny * 0.75f
            val band = abs(ny + 0.05f - 0.3f * sin(nx * 2.4f + spin)) < 0.17f
            c.set(xx, yy, when {
                band -> if (lit < -0.3f) Pal.GERANIUM_D else Pal.FLAG_RED
                lit > 0.62f -> Col.hex(0xFFFFFF)
                lit < -0.4f -> Col.hex(0xD8D0BC)
                else -> Col.hex(0xF4EEDD)
            })
        }
    }

    /**
     * Two pigeons pecking about: sprites, like the people. Tapped ([pokes]) they burst up and away to either side, out
     * of sight, and a few seconds later glide back down and land where they were.
     */
    private fun pigeons(x0: Int, y0: Int) {
        val pk = poked("pigeons")
        val a = pk?.age(t) ?: 0f
        if (pk != null && a in 0.05f..6.3f) {
            val k = detail
            for (j in 0..1) {
                val hx = x0 + (j * 9 + (sin(10.0 + j * 3).toFloat() * 4).toInt()) * k; val hy = y0 + (j * 3 - 4) * k
                // off to either side and up out of the picture, back in a long glide
                val dir = if (j == 0) -1 else 1
                val away = if (a < 1.6f) PokeArt.ease(a, 0.05f + j * 0.1f, 1.6f) else 1f - PokeArt.ease(a, 4.6f + j * 0.2f, 6.3f)
                if (away >= 0.999f) continue
                val x = hx + (dir * 110f * away * k).toInt(); val y = hy - (130f * away * (2f - away) * k).toInt()
                PokeArt.wings(c, x, y, ((t * 11).toInt() + j) and 1 == 0, k, Pal.STONE_L, Pal.STONE_M)
                c.fillRect(x + dir * 2 * k, y - k, k, k, Pal.STONE_L)
            }
            return
        }
        sprite(x0, y0) {
            for (k in 0..1) {
                val x = x0 + k * 9 + (sin(t * 0.7 + k * 3).toFloat() * 4).toInt(); val y = y0 + k * 3
                val peck = sin(t * 5 + k * 2) > 0.6
                c.fillRect(x - 3, y - 4, 6, 3, Pal.STONE_L); c.set(x - 3, y - 2, Pal.STONE_M); c.set(x + 3, y - 4, Pal.STONE_M)
                c.fillRect(x + 2 + (if (k == 0) 0 else -6), y - 6 + (if (peck) 1 else 0), 2, 2, Pal.STONE_L)
                c.set(x + 4 + (if (k == 0) 0 else -9), y - 5 + (if (peck) 1 else 0), Pal.GOLD)
                c.set(x - 1, y - 1, Pal.GERANIUM_D); c.set(x + 1, y - 1, Pal.GERANIUM_D)
            }
        }
    }
}
