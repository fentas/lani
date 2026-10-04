package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.Season
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ob potoku: the stream below the village, seen from its bank. It comes out of the woods under the hills, runs past
 * Mlinar France's mill (an overshot wheel fed by a wooden flume) and under a plank footbridge (brv), over a row of
 * stepping stones and on past us: clear water over pebbles, white riffles round the stones, a trout holding in the
 * current, watercress in the shallows, a dragonfly darting over the water. A weeping willow leans out over the left
 * bank; below it Micka's washing stone and her basket of laundry; a sheep drinks at the right bank. At night the mill's
 * window glows and the moon shimmers on the water; in winter ice grows out from the banks and snow lies on the stones.
 *
 * A dialog can send a trout leaping out of the current ("trout") and set the mill wheel's water ("wheel": 0 the flume
 * dry and the wheel still, 1 a full gush). In the rain the stream runs fuller and greyer, spilling over its banks;
 * fog lies thickest on the water.
 *
 * Its wild animals ([WildPainter]): butterflies over the cress and the bank by day, a fox beyond the mill at dawn and
 * dusk, a hedgehog nosing along the near bank and bats low over the water at dusk and in the night ("bats" brings them).
 * The evening bell (the ave) comes down from the village round sunset ("bell").
 *
 * Closer up ([detail] 2 and 3) the same view is drawn finer: thin ripple lines and the pebbles of the bed in the
 * shallows, blades of grass on the banks, planks and nails on the footbridge, logs, shingles and the wheel's paddles at
 * the mill, the willow's leaves along its strands, the trout's spots.
 */
internal class StreamPainter : WildPainter() {
    override val art = "stream"

    /** Up the stream, east-south-east, where the stars rise. */
    override val skyFacing = 120.0

    // the plan, in metres: the stream's middle and half its width at each depth, the woods it comes out of
    private fun course(d: Float) = -1.1f + 0.075f * (d - 9f) + 0.5f * sin((d - 9f) * 0.2f)
    private fun half(d: Float) = 1.45f + 0.12f * sin(d * 0.37f) - max(0f, d - 30f) * 0.012f

    /** Half the water's width at side [x], depth [d]: the banks a little ragged. */
    private fun reach(x: Float, d: Float) = half(d) + (Noise.v2(x * 2.2f, d * 1.6f, 305) - 0.5f) * 0.22f

    private val woods = 56f
    private val stonesD = 13.6f
    private val stones = floatArrayOf(1.2f, 0.45f, -0.3f, -1.05f, -1.75f)
    private val bridgeD = 19.5f
    private val bridgeL = -2.1f; private val bridgeR = 2.5f

    // the mill on the right bank: its front (a gable end) at millD, the wheel on its left facing us
    private val millD = 22.8f; private val millBack = 25.8f
    private val millL = 3.5f; private val millR = 7.3f
    private val wheelX = 2.55f; private val wheelD = 22.5f; private val wheelR = 1.25f; private val axleH = 1.05f

    private val willowX = -6.9f; private val willowD = 17.2f

    private val items = ArrayList<Pair<Float, () -> Unit>>()

    /** The water's object id this frame: the weather and the rings keep to it. */
    private var waterId = -1

    /** Whether France's mill grinds (the village project "mlin" finished): its water runs, its window glows. */
    private var millDone = true

    /** Whether the cart bridge is finished (the village project "most"): a cart road runs over it. */
    private var roadDone = true

    // the cart bridge upstream (the village project "most"): stone piers on both banks, logs across, a plank deck
    private val cartD = 35f
    private val cartD0 = 34.3f; private val cartD1 = 35.7f
    private val cartL = -2.6f; private val cartR = 3.3f
    private val pierH = 1.05f

    /** Picture pixels per scene canvas pixel: every size and offset in pixels is multiplied by it. */
    private val z: Int get() = detail

    override fun paint() {
        setup()
        millDone = stage(MILL) >= stages(MILL)
        roadDone = stage(BRIDGE) >= stages(BRIDGE)
        val z = z
        vistaSky(4, 21, moonX = ox + MOON_X * z, moonY = oy + 12 * z)
        hills()
        ground { x, d, px, py -> land(x, d, px, py) }
        edgeWoods()
        thing("stream", outline = BANK) { waterId = c.penId; water() }

        items.clear()
        // alders and a spruce or two along the banks, far off
        for (k in 0 until 9) {
            val side = if (k % 2 == 0) -1f else 1f
            val d = 30f + Noise.rnd(k, 311) * 22f
            val x = course(d) + side * (half(d) + 2.5f + Noise.rnd(k, 312) * 9f)
            if (side > 0 && d < millBack + 3f && x < millR + 2f) continue
            if (abs(d - cartD) < 2.6f) continue // the cart road's way over the stream
            val spruce = Noise.rnd(k, 313) < 0.3f
            val hgt = if (spruce) 10f + Noise.rnd(k, 314) * 5f else 7f + Noise.rnd(k, 314) * 4f
            items.add(d to { prop(Pal.OUTLINE_TREE) { if (spruce) spruce(x, d, hgt, k) else leafyTree(x, d, hgt, k * 5 + 3) } })
        }
        millSite()
        cartBridge()
        items.add(bridgeD to { thing("bridge") { footbridge() } })
        items.add(willowD to { thing("willow", outline = Pal.OUTLINE_TREE) { willow(willowX, willowD) } })
        items.add(17.3f to { groundShadow(2.25f, 17.3f, 0.6f); thing("sheep", slop = 1) { sheep(2.25f, 17.3f) } })
        items.add(stonesD to { thing("stones") { steppingStones() } })
        items.add(12.2f to { thing("trout", outline = 0, slop = 2) { trout(course(12.2f) + 0.2f, 12.2f) } })
        items.add(11.3f to { thing("laundry") { laundry() } })
        items.add(10.9f to { thing("cress", outline = CRESS_D, slop = 1) { cress(course(10.9f) + half(10.9f) - 0.35f, 10.9f) } })
        items.add(11f to { prop(0) { rushes() } })
        for (p in peopleAt("mill")) items.add(12.3f to { groundShadow(6.0f, 12.3f, 0.5f); person(p, gx(6.0f, 12.3f).toInt(), gy(12.3f).toInt(), flip = true) })
        for (p in peopleAt("ford")) items.add(13.1f to { groundShadow(2.75f, 13.1f, 0.5f); person(p, gx(2.75f, 13.1f).toInt(), gy(13.1f).toInt(), flip = true) })
        for (p in peopleAt("bank")) items.add(10.45f to { groundShadow(-3.45f, 10.45f, 0.5f); person(p, gx(-3.45f, 10.45f).toInt(), gy(10.45f).toInt(), flip = false) })
        wildlife()
        items.sortByDescending { it.first }
        for ((_, draw) in items) draw()

        foregroundGrass(0, (w * 0.2f).toInt(), 23)
        foregroundGrass((w * 0.64f).toInt(), w, 24)
        prop(FLY_OUTLINE) { leaps(fxOn("trout")) }
        thing("dragonfly", outline = FLY_OUTLINE, slop = 3) { dragonfly() }
        val nBats = batCount(fx("bats"), out("bat"))
        if (nBats > 0) creature("bat", slop = 3) { batsOver(ox + 50f * z, hz - 36f * z, ox + 190f * z, gy(24f), nBats, 449, 1.1f) }
        // the evening bell from the village up behind us, its rings coming down over the bank on the left
        farBell(ox + 12f * z, hz - 26f * z, fx("bell") ?: if (aveNow()) 1f else 0f, 3)
        fireflies(0, hz + 14 * z, w, h, 14, 29)
        s.fx { s.effects.leaves(env.season, env.month) }
    }

    /** The stream's wild animals of the frame ([out]): the fox on the far bank, the hedgehog on the near one, butterflies. */
    private fun wildlife() {
        val z = z
        if (out("fox")) {
            val u = spot("fox")
            // on the far bank beyond the mill, where the cart track goes off
            val d = 28f + u * 3f
            val x = 8.6f + u * 3f
            items.add(d to { creature("fox") { fox(gx(x, d), gy(d), 40f / d, left = true, sit = true) } })
        }
        if (out("hedgehog")) {
            val u = spot("hedgehog")
            val x = -5.4f - u * 1.6f; val d = 12.6f + u * 0.8f
            items.add(d to { creature("hedgehog") { hedgehog(gx(x, d), gy(d), 1.45f, left = u > 0.5f) } })
        }
        if (out("butterfly")) for ((j, at) in listOf(-5.6f to 13.2f, 2.2f to 11.2f).withIndex()) {
            val (bx, bd) = at
            items.add(bd - 0.5f to {
                creature("butterfly", slop = 3) {
                    butterfly(gx(bx, bd) + sin(t * 0.8 + j * 2).toFloat() * 8f * z, gy(bd) - (16f + 5f * sin(t * 1.2 + j).toFloat()) * z, 1.6f, j + 2 + (spot("butterfly") * 3).toInt())
                }
            })
        }
    }

    // ------------------------------------------------------------------ far away

    /** The plateau's peaks in the haze, then the wooded hills the stream comes down from. */
    private fun hills() {
        val z = z
        val season = env.season
        val rock = if (season == Season.WINTER) Col.hex(0xB8C6DA) else Col.hex(0x7684A8)
        ridge(hz - 13f * z, 24f * z, 0.012f, 321, rock, 0.36f, snowLine = if (season == Season.SUMMER) 0.8f else 0.45f, sharp = true)
        val midCol = when (season) { Season.WINTER -> Col.hex(0xC6D2E2); Season.AUTUMN -> Col.hex(0x7E7A42); else -> Col.hex(0x4E7A48) }
        val mid = ridge(hz - 4f * z, 10f * z, 0.017f, 323, midCol, 0.22f)
        treeline(mid, 6 * z, 2.4f * z, if (season == Season.WINTER) Col.hex(0x6A7A8A) else Col.hex(0x2E5A36), 0.26f, 327)
    }

    /**
     * The woods the stream comes out of, along the far edge of the meadow: spruce spires and beech crowns, lower over
     * the stream's valley in the middle, shaded toward their feet and lit on the sun's side. Placed along the scene
     * canvas's columns, so every detail shows the same trees.
     */
    private fun edgeWoods() {
        val z = z
        val season = env.season
        val base = gy(woods).toInt() + z
        val sunLeft = (sunAt()?.first ?: vx) < vx
        val valley = gx(course(woods), woods) / z
        val sD = env.lit(Col.mix(Col.hex(0x1A3A28), env.skyHorizon, 0.18f)); val sM = env.lit(Col.mix(Col.hex(0x24503A), env.skyHorizon, 0.16f))
        val sL = env.lit(Col.mix(Col.hex(0x3A6E4A), env.skyHorizon, 0.14f))
        val (bD, bM, bL) = when (season) {
            Season.AUTUMN -> Triple(Col.hex(0x6A3A1A), Col.hex(0xA8602A), Col.hex(0xD8983A))
            Season.WINTER -> Triple(Col.hex(0x6A625A), Col.hex(0x8A8278), Col.hex(0xA8A096))
            Season.SPRING -> Triple(Col.hex(0x3E6E30), Col.hex(0x5E9A44), Col.hex(0x8CC25A))
            else -> Triple(Col.hex(0x2E5A2E), Col.hex(0x44803E), Col.hex(0x68A450))
        }.let { (a, b, c2) -> Triple(env.lit(Col.mix(a, env.skyHorizon, 0.16f)), env.lit(Col.mix(b, env.skyHorizon, 0.14f)), env.lit(Col.mix(c2, env.skyHorizon, 0.12f))) }
        c.penEmissive = true
        for (px in max(0, c.left) until min(w, c.right)) {
            val xb = (px + 0.5f) / z
            val top = woodsTop(xb, valley, base)
            val cell = floor(xb / 7f).toInt()
            val spruce = Noise.rnd(cell, 303) < 0.55f
            val slope = woodsTop(xb + 1f, valley, base) - woodsTop(xb - 1f, valley, base)
            val lit = if (sunLeft) slope > 0 else slope < 0
            for (py in max(max(0, top), c.top) until min(min(h, base), c.bottom)) {
                val down = (py - top).toFloat() / max(1f, (base - top).toFloat())
                val n = Noise.v2(px * 0.5f / z, py * 0.6f / z, 304)
                val col = if (spruce) {
                    if (py - top < z && lit) sL else if (down > 0.5f + (Dither.at(px, py) - 0.5f) * 0.4f || n < 0.25f) sD else sM
                } else {
                    if (py - top < 2 * z && lit) bL else if (down > 0.55f + (Dither.at(px, py) - 0.5f) * 0.4f || n < 0.3f) bD else if (n > 0.66f) bL else bM
                }
                val snowy = season == Season.WINTER && Noise.v2(px * 0.5f / z, py * 0.9f / z, 99) > 0.55f && Dither.at(px, py) < 0.7f
                c.set(px, py, if (snowy) env.lit(if (lit) Pal.SNOW_L else Pal.SNOW_M) else col)
            }
        }
        c.penEmissive = false
    }

    /** The woods' top row over scene column [xb]: a spire or a crown per cell of 7, lower over the valley. */
    private fun woodsTop(xb: Float, valley: Float, base: Int): Int {
        val gap = (abs(xb - valley) / 70f).coerceIn(0.62f, 1f)
        val cell = floor(xb / 7f).toInt(); val u = xb / 7f - cell
        val spruce = Noise.rnd(cell, 303) < 0.55f
        val tall = 18f + 12f * Noise.v1(xb * 0.05f, 301) + 6f * Noise.rnd(cell, 302)
        val crown = if (spruce) (1f - abs(u - 0.5f) * 2f) * 9f else sqrt(max(0f, 1f - (u * 2f - 1f) * (u * 2f - 1f))) * 5f
        return base - ((tall + crown) * gap * detail).toInt()
    }

    // ------------------------------------------------------------------ the meadow and the banks

    private fun land(x: Float, d: Float, px: Int, py: Int): Int {
        val g = env.grass
        val off = abs(x - course(d)) - reach(x, d) // metres from the water's edge
        val z = z
        // beyond the woods' edge: more woods, dark under the trees
        if (d > woods) return Col.mix(if (env.snow) Pal.SNOW_D else Col.scale(env.forestFloor[2], 0.75f), env.skyHorizon, 0.2f)
        // the cart road over the bridge, once carts cross it: ruts in the dirt
        if (roadDone) roadAt(x, d, px, py)?.let { return hazy(it, d) }
        if (env.snow) {
            val n = Noise.v2(x * 0.6f, d * 0.6f, 41) + (Dither.at(px, py) - 0.5f) * 0.2f
            var col = if (n < 0.25f) Pal.SNOW_M else Pal.SNOW_L
            if (off < 0.28f) col = if (Noise.rnd(px / z, py / z, 7) < 0.3f) Pal.STONE_M else Pal.SNOW_M // the bank's wet stones show
            else if (abs(x - track(d)) < 0.45f && d < bridgeD + 0.5f) col = Pal.SNOW_M // the trodden track
            return hazy(col, d)
        }
        val n = Noise.v2(x * 0.7f, d * 0.7f, 3) + (Dither.at(px, py) - 0.5f) * 0.25f
        var col = if (n > 0.62f) g[0] else if (n > 0.32f) g[1] else g[2]
        // the floor darkens toward the woods
        if (d > woods - 9f && (d - woods + 9f) / 9f > Dither.at(px, py) + 0.15f) col = env.forestFloor[if (n > 0.5f) 0 else 1]
        if (z == 1) {
            val r = Noise.rnd(px, py, 5)
            if (d < 24f && r < 0.05f) col = g[2]
            if (d < 24f && r > 0.985f) col = g[3]
            if (env.month in 4..8 && d < 30f && off > 1.2f && Noise.rnd(px, py, 9) < 0.012f) col = Pal.PAINTED[(px * 7 + py) % 6]
        } else col = grassCloser(col, d, off, px, py)
        // the track to the footbridge
        val tr = abs(x - track(d)) + (Noise.rnd(px / z, py / z, 44) - 0.5f) * 0.12f
        if (d < bridgeD + 0.6f && tr < 0.5f) col = if (tr > 0.4f) Pal.DIRT_D else if (Noise.rnd(px / z, py / z, 45) < 0.15f) Pal.DIRT_L else Pal.DIRT_M
        // the bank: gravel at the water's edge, damp grass above it
        if (off < 0.3f) col = gravel(x, d, px, py) else if (off < 0.95f) col = Col.scale(col, 0.84f)
        return hazy(col, d)
    }

    /** The cart road's middle at side [x]: off the bridge's right end toward the village (nearer us), off its left end away into the woods. */
    private fun roadMid(x: Float): Float = when {
        x > cartR -> cartD - (x - cartR) * 0.35f
        x < cartL -> cartD + (cartL - x) * 1.1f
        else -> cartD
    }

    /** The cart road's ground at ([x], [d]), or null off it: packed dirt with two ruts, snow in winter. */
    private fun roadAt(x: Float, d: Float, px: Int, py: Int): Int? {
        if (d > woods || x in cartL..cartR) return null
        val off = abs(d - roadMid(x)) / (if (x < cartL) 3.2f else 2f)
        if (off > 0.75f + (Noise.rnd(px / z, py / z, 47) - 0.5f) * 0.12f) return null
        if (env.snow) return if (abs(off - 0.35f) < 0.1f) Pal.SNOW_M else Pal.SNOW_L
        return when {
            abs(off - 0.35f) < 0.09f -> Pal.DIRT_D // the ruts
            off > 0.62f -> Col.mix(Pal.DIRT_D, env.grass[2], 0.4f)
            Noise.rnd(px / z, py / z, 48) < 0.14f -> Pal.DIRT_L
            else -> Pal.DIRT_M
        }
    }

    /** The track along the right bank, from our feet to the footbridge's end. */
    private fun track(d: Float) = 4.3f - 0.18f * (d - 9f) + 0.25f * sin(d * 0.5f)

    /** The banks' gravel: pebbles, lighter on top, at every detail the same stones. */
    private fun gravel(x: Float, d: Float, px: Int, py: Int): Int {
        val v = Noise.v2(x * 8f, d * 8f, 331)
        val top = Noise.v2(x * 8f, (d + 0.04f) * 8f, 331)
        return when {
            v > 0.66f -> if (top <= 0.66f && detail > 1) Pal.STONE_L else Pal.STONE_M
            v > 0.4f -> Col.hex(0x8C8468)
            else -> if (Dither.at(px, py) < 0.5f) Col.hex(0x6E6850) else Col.hex(0x7A7458)
        }
    }

    /** The meadow closer up: blades of grass, taller near us, and flowers with petals round a heart. */
    private fun grassCloser(col0: Int, d: Float, off: Float, px: Int, py: Int): Int {
        val g = env.grass
        val z = z
        var col = col0
        if (d < 24f) {
            val len = (pm(d) * 0.075f).toInt().coerceIn(1, 2 * z)
            val dark = 0.07f / len; val light = 1f - 0.022f / len
            for (j in 0 until len) {
                val r = Noise.rnd(px, py + j, 5)
                if (r < dark) { col = if (j == len - 1 && len > 2) g[1] else g[2]; break }
                if (r > light) { col = g[3]; break }
            }
        }
        if (env.month in 4..8 && d < 30f && off > 1.2f) {
            val cx = px / z; val cy = py / z
            if (Noise.rnd(cx, cy, 9) < 0.012f) {
                val petal = Pal.PAINTED[(cx * 7 + cy) % 6]
                val lx = px - cx * z; val ly = py - cy * z
                col = if (z == 2) (if (lx == 0 && ly == 0) petal else if (lx == 0) g[2] else col)
                else (if (lx == 1 && ly == 1) Pal.GOLD else if (lx == 1 || ly == 1) petal else col)
            }
        }
        return col
    }

    // ------------------------------------------------------------------ the water

    /** The stream from the woods to our feet: the sky in it, the current's ripples, the bed in the shallows. */
    private fun water() {
        val top = gy(woods).toInt()
        val winter = env.season == Season.WINTER
        for (py in max(max(hz + 1, top), c.top) until min(h, c.bottom)) {
            val d = depthAt(py)
            val cx = course(d); val hw = half(d) + 0.12f
            val xl = gx(cx - hw, d); val xr = gx(cx + hw, d)
            for (px in max(max(0, floor(xl).toInt()), c.left)..min(min(w - 1, ceil(xr).toInt()), c.right - 1)) {
                val x = sideAt(px, d)
                val rch = reach(x, d)
                val off = x - cx
                if (abs(off) > rch) continue
                c.set(px, py, waterAt(px, py, x, d, (off + rch) / (2f * rch), winter))
            }
        }
    }

    private fun waterAt(px: Int, py: Int, x: Float, d: Float, u: Float, winter: Boolean): Int {
        val z = z
        val mid = 1f - abs(u - 0.5f) * 2f // 1 in the middle, 0 at the banks
        if (winter && abs(u - 0.5f) > 0.27f + (Noise.v2(x * 2f, d * 2f, 71) - 0.5f) * 0.18f) {
            // shelf ice growing out from the banks, snow on it toward the bank
            val crack = z > 1 && abs(Noise.v2(x * 1.6f, d * 4f, 62) - 0.5f) < 0.03f
            val col = when {
                crack -> Col.mix(Pal.ICE_M, Pal.WATER_L, 0.45f)
                abs(u - 0.5f) > 0.42f -> if (Noise.rnd(px / z, py / z, 61) < 0.25f) Pal.SNOW_M else Pal.SNOW_L
                Noise.rnd(px / z, py / z, 63) < 0.1f -> Pal.ICE_M
                else -> Pal.ICE_L
            }
            return hazy(col, d)
        }
        val sky = skyAt(0.7f + 0.3f * mid)
        var col = Col.mix(Pal.WATER_M, sky, 0.42f + (Dither.at(px, py) - 0.5f) * 0.12f)
        col = Col.mix(col, Pal.WATER_D, 0.38f * mid * mid)
        // the shallows: the pebbles of the bed show through
        if (mid < 0.5f) {
            val bed = Noise.v2(x * 7f, d * 7f, 73)
            val pebble = if (bed > 0.64f) (if (z > 1 && Noise.v2(x * 7f, (d + 0.03f) * 7f, 73) <= 0.64f) Pal.STONE_L else Col.hex(0xA09A80)) else if (bed > 0.36f) Col.hex(0x847E60) else Col.hex(0x625E46)
            col = Col.mix(col, pebble, (0.5f - mid) * 1.1f)
        }
        // the current: ripples drifting down toward us, bright where they catch the sky; thin lines closer up
        val band = F * EYE / (d * d) * 1.9f // rows per ripple
        var ph = 0f
        if (band >= 2f) {
            ph = ripple(x, d)
            val fade = ((band - 2f) / 3f).coerceIn(0f, 1f)
            if (ph > 0.62f) {
                // near us and closer up only the top row of each bright band: a thin line
                val edge = (z == 1 && band < 5f) || ripple(x, depthAt(py - 1)) <= 0.62f
                if (edge) col = Col.mix(col, if (ph > 0.95f && env.sun > 0f) Col.hex(0xFFFFFF) else Col.mix(sky, Col.hex(0xFFFFFF), 0.5f), 0.5f * fade)
            } else if (ph < -0.72f) col = Col.mix(col, Pal.WATER_D, 0.2f * fade)
        }
        // riffles round the stepping stones and where the mill's water comes back
        if (abs(d - stonesD) < 1.1f) {
            for (sx in stones) {
                val dx = (x - sx) / 0.42f; val dd = (d - stonesD + 0.25f) / 0.7f
                val q = dx * dx + dd * dd
                if (q < 1f && Noise.rnd((x * 30f).toInt(), ((d + t.toFloat() * 1.3f) * 22f).toInt(), 75) < (1f - q) * 0.7f) {
                    col = Col.mix(col, Col.hex(0xF2F6FA), 0.8f); break
                }
            }
        }
        if (abs(d - wheelD) < 1.2f && x > course(d) + half(d) - 0.9f) {
            val q = ((d - wheelD + 0.3f) / 0.9f).let { it * it } + ((x - course(d) - half(d)) / 0.9f).let { it * it }
            if (q < 1f && Noise.rnd((x * 30f).toInt(), ((d + t.toFloat()) * 20f).toInt(), 76) < (1f - q) * 0.6f * gushOf()) col = Col.mix(col, Col.hex(0xF2F6FA), 0.7f)
        }
        // by day a glint now and then on a ripple's crest; by night the moon shimmers under its place in the sky
        if (env.sun > 0.05f && ph > 0.4f && Noise.rnd(px, py, (t * 5).toInt()) < 0.05f / z) col = Col.mix(col, Col.hex(0xFFFFFF), 0.8f)
        if (env.dark > 0.3f && frame.sky.gloom < 0.55f && env.moonShows) {
            val mx = ox + MOON_X * z
            val spread = (2f + (py - hz) / z * 0.07f) * z
            // short dashes of light across the water, flickering
            if (abs(px - mx) < spread && Noise.rnd(Math.floorDiv(px, 3 * z), py / z, (t * 3).toInt()) < 0.5f * (1f - abs(px - mx) / spread)) col = Col.mix(col, Col.hex(0xF4EED0), 0.55f * env.dark)
        }
        return hazy(col, d)
    }

    /**
     * The current's ripples at ([x], [d]): wavy bands across the stream drifting down toward us, a cross-chop, and
     * patches of broken water, about −1.3 … 1.3.
     */
    private fun ripple(x: Float, d: Float): Float {
        val f = d + t.toFloat() * 1.3f
        val a = sin(f * 3.3f + sin(x * 2.6f + d * 0.4f) * 1.1f)
        val b = sin((d + t.toFloat() * 0.9f) * 5.3f - x * 3.1f + 1.3f)
        return a * 0.45f + b * 0.3f + (Noise.v2(x * 2.2f, f * 2.4f, 77) - 0.5f) * 1.1f
    }

    /** How much water pours onto the mill wheel: set by a dialog ("wheel"), else a steady run; none before the mill grinds. */
    private fun gushOf(): Float = if (!millDone) 0f else fx("wheel") ?: 0.45f

    // ------------------------------------------------------------------ the mill

    /**
     * France's mill as far as the village has built it (the project "mlin", [stage]): nothing before the first step;
     * then pegs and a string round its footprint (the place chosen), the stone footing with the timber waiting beside it
     * (the foundations), the timber walls and the gable under bare rafters (the walls), the shingled roof with its door and
     * window (the roof), the wheel and the flume, still and dry (the wheel); finished, the water runs, the wheel turns, a
     * sack of flour waits by the door and the window glows at night. Only the finished mill is the word "mlin".
     */
    private fun millSite() {
        val st = stage(MILL)
        if (st <= 0) return
        if (millDone) items.add(millD to { thing("mill") { mill(st) } })
        else items.add(millD to { prop { mill(st) } })
    }

    /** Pegs at the corners of the mill's footprint and a string between them, a hand above the grass. */
    private fun millPegs() {
        val z = z
        val corners = arrayOf(floatArrayOf(millL, millD), floatArrayOf(millR, millD), floatArrayOf(millR, millBack), floatArrayOf(millL, millBack))
        val string = if (env.snow) Pal.WOOD_X else Col.hex(0xE8E0CC)
        for (k in 0 until 4) {
            val a = corners[k]; val b = corners[(k + 1) % 4]
            c.line(gx(a[0], a[1]).toInt(), (gy(a[1]) - 0.4f * pm(a[1])).toInt(), gx(b[0], b[1]).toInt(), (gy(b[1]) - 0.4f * pm(b[1])).toInt(), string)
        }
        for (a in corners) {
            val px = gx(a[0], a[1]).toInt(); val p = pm(a[1])
            for (j in 0 until max(1, z)) c.vline(px + j, (gy(a[1]) - 0.55f * p).toInt(), gy(a[1]).toInt(), if (j == 0) Pal.WOOD_L else Pal.WOOD_D)
        }
    }

    /** Hewn beams stacked on the bank beside the footing, waiting for the walls. */
    private fun millTimber() {
        val z = z
        val d = millD - 0.9f; val p = pm(d)
        for (row in 0 until 3) for (k in 0 until 3 - row) {
            val x0 = gx(millR - 2.2f + row * 0.18f, d); val x1 = gx(millR + 0.4f - row * 0.18f, d)
            val y = gy(d) - (row + 0.5f) * 0.26f * p - k * 0.02f * p
            val th = max(z, (0.24f * p).toInt())
            for (j in 0 until th) c.hline(x0.toInt(), x1.toInt(), y.toInt() - j, if (j == th - 1) Pal.WOOD_L else if (j == 0) Pal.WOOD_D else Pal.WOOD_M)
            c.fillRect(x1.toInt() - z, y.toInt() - th + 1, z, th, Pal.WOOD_X)
        }
        if (env.snow) c.hline(gx(millR - 1.9f, d).toInt(), gx(millR + 0.1f, d).toInt(), (gy(d) - 3 * 0.26f * p).toInt() - z, Pal.SNOW_L)
    }

    /** France's mill at stage [s] of the project: timber walls on a stone footing, a shingled roof, a window and a door; the wheel, the flume. */
    private fun mill(st: Int) {
        if (st <= 1) { millPegs(); return }
        val z = z
        val d0 = millD; val d1 = millBack
        val ridgeX = (millL + millR) / 2f
        // the footing alone at first, then the walls and the gable over it
        val wallH = if (st == 2) 0.6f else 2.3f; val ridgeH = if (st == 2) 0.6f else 4.2f
        val p0 = pm(d0); val p1 = pm(d1)
        val snow = env.snow
        if (st == 3) {
            // no roof yet: the back gable and the right wall show inside, in their shade
            c.polyBegin()
            c.polyAdd(gx(millL, d1), gy(d1)); c.polyAdd(gx(millR, d1), gy(d1)); c.polyAdd(gx(millR, d1), gy(d1) - wallH * p1)
            c.polyAdd(gx(ridgeX, d1), gy(d1) - ridgeH * p1); c.polyAdd(gx(millL, d1), gy(d1) - wallH * p1)
            c.polyFill { x, y -> wallTone(y, gy(d1), p1, shade = 0.55f, x) }
            c.polyBegin()
            c.polyAdd(gx(millR, d0), gy(d0)); c.polyAdd(gx(millR, d1), gy(d1)); c.polyAdd(gx(millR, d1), gy(d1) - wallH * p1); c.polyAdd(gx(millR, d0), gy(d0) - wallH * p0)
            c.polyFill { x, y -> val d = F * millR / max(0.5f, x + 0.5f - vx); wallTone(y, gy(d), pm(d), shade = 0.62f, x) }
        }
        // the left wall, facing the stream, going back: each column at its own depth
        c.polyBegin()
        c.polyAdd(gx(millL, d0), gy(d0)); c.polyAdd(gx(millL, d1), gy(d1)); c.polyAdd(gx(millL, d1), gy(d1) - wallH * p1); c.polyAdd(gx(millL, d0), gy(d0) - wallH * p0)
        c.polyFill { x, y -> val d = F * millL / max(0.5f, x + 0.5f - vx); wallTone(y, gy(d), pm(d), shade = 0.78f, x) }
        // the front: a gable end, planks above a stone footing
        c.polyBegin()
        c.polyAdd(gx(millL, d0), gy(d0)); c.polyAdd(gx(millR, d0), gy(d0)); c.polyAdd(gx(millR, d0), gy(d0) - wallH * p0)
        c.polyAdd(gx(ridgeX, d0), gy(d0) - ridgeH * p0); c.polyAdd(gx(millL, d0), gy(d0) - wallH * p0)
        c.polyFill { x, y -> wallTone(y, gy(d0), p0, shade = 1f, x) }
        if (st == 2) {
            // the footing's top, the timber for the walls waiting beside it
            c.polyBegin()
            c.polyAdd(gx(millL, d0), gy(d0) - wallH * p0); c.polyAdd(gx(millR, d0), gy(d0) - wallH * p0)
            c.polyAdd(gx(millR, d1), gy(d1) - wallH * p1); c.polyAdd(gx(millL, d1), gy(d1) - wallH * p1)
            c.polyFill { x, y -> if (snow) Pal.SNOW_L else if (Noise.rnd(x / (3 * z), y / z, 343) < 0.3f) Pal.STONE_L else Pal.STONE_M }
            millTimber()
            return
        }
        val eaveH = wallH - 0.1f; val over = 0.35f
        if (st == 3) { millRafters(d0, d1, ridgeX, ridgeH, eaveH, over); return }
        // the roof over them: both slopes seen from above, shingles in rows, snow in winter
        for (right in booleanArrayOf(true, false)) {
            val ex = if (right) millR + over else millL - over
            c.polyBegin()
            c.polyAdd(gx(ridgeX, d0 - over), gy(d0 - over) - ridgeH * pm(d0 - over)); c.polyAdd(gx(ridgeX, d1 + over), gy(d1 + over) - ridgeH * pm(d1 + over))
            c.polyAdd(gx(ex, d1 + over), gy(d1 + over) - eaveH * pm(d1 + over)); c.polyAdd(gx(ex, d0 - over), gy(d0 - over) - eaveH * pm(d0 - over))
            val lightSide = !right
            c.polyFill { x, y -> roofTone(x, y, lightSide, snow) }
        }
        // the barge boards along the front of the roof
        val rx = gx(ridgeX, d0 - over); val ry = gy(d0 - over) - ridgeH * pm(d0 - over)
        for (side in floatArrayOf(millL - over, millR + over)) {
            val ex = gx(side, d0 - over); val ey = gy(d0 - over) - eaveH * pm(d0 - over)
            for (j in 0 until z) c.line(rx.toInt(), ry.toInt() + j, ex.toInt(), ey.toInt() + j, if (j == 0) Pal.WOOD_L else Pal.WOOD_D)
        }
        // the door and the window; the window lit at night
        val fy = gy(d0)
        box(gx(5.55f, d0).toInt(), (fy - 1.8f * p0).toInt(), max(2, (0.8f * p0).toInt()), (1.8f * p0).toInt(), Pal.WOOD_M, Pal.DOOR, Pal.WOOD_X)
        if (z > 1) for (k in 1..2) c.vline(gx(5.55f + k * 0.27f, d0).toInt(), (fy - 1.75f * p0).toInt(), (fy - 0.05f * p0).toInt(), Pal.WOOD_X)
        val wx = gx(4.05f, d0).toInt(); val wy = (fy - 1.65f * p0).toInt(); val ww = max(2, (0.6f * p0).toInt()); val wh = max(2, (0.5f * p0).toInt())
        val lit = if (millDone) env.windows else 0f // nobody works there before it grinds
        if (lit > 0.2f) {
            glow { c.fillRect(wx, wy, ww, wh, Col.mix(Pal.GLASS, Pal.WINDOW_LIT, lit)); if (z > 1) c.fillRect(wx + z, wy + z, max(1, ww / 3), max(1, wh / 3), Pal.WINDOW_LIT_HI) }
            s.light(wx + ww / 2f, wy + wh / 2f, 34f * z, 0.7f * lit)
        } else c.fillRect(wx, wy, ww, wh, Pal.GLASS)
        c.hline(wx - z, wx + ww, wy + wh, Pal.WOOD_L)
        if (z > 1) { c.vline(wx + ww / 2, wy, wy + wh - 1, Pal.WOOD_D); c.hline(wx, wx + ww - 1, wy + wh / 2, Pal.WOOD_D) }
        if (st < 5) return
        // a sack of flour by the door
        if (millDone) {
            val sx = gx(6.85f, d0 - 0.3f); val sy = gy(d0 - 0.3f); val sp = pm(d0 - 0.3f)
            c.fillEllipse(sx, sy - 0.3f * sp, 0.24f * sp, 0.32f * sp, Col.hex(0xE8E0CC))
            c.fillEllipse(sx - 0.06f * sp, sy - 0.38f * sp, 0.13f * sp, 0.16f * sp, Col.hex(0xF8F4EA))
            c.fillRect((sx - 0.08f * sp).toInt(), (sy - 0.68f * sp).toInt(), max(1, (0.16f * sp).toInt()), max(1, (0.1f * sp).toInt()), Col.hex(0xC8BCA0))
        }

        flume()
        wheel()
    }

    /** The walls' top and the roof's bare rafters from the eaves up to the ridge beam, before the shingles go on. */
    private fun millRafters(d0: Float, d1: Float, ridgeX: Float, ridgeH: Float, eaveH: Float, over: Float) {
        val z = z
        // the ridge beam, front to back
        val rx0 = gx(ridgeX, d0 - over); val ry0 = gy(d0 - over) - ridgeH * pm(d0 - over)
        val rx1 = gx(ridgeX, d1 + over); val ry1 = gy(d1 + over) - ridgeH * pm(d1 + over)
        // the rafters in pairs along the roof, each from one eave up to the ridge and down to the other
        val n = 3
        for (k in 0..n) {
            val d = d0 - over + (d1 - d0 + 2 * over) * k / n
            val p = pm(d)
            val top = gx(ridgeX, d) to gy(d) - ridgeH * p
            for ((side, col) in listOf((millL - over) to Pal.WOOD_L, (millR + over) to Pal.WOOD_M)) {
                val ex = gx(side, d); val ey = gy(d) - eaveH * p
                for (j in 0 until max(1, z)) c.line(ex.toInt() + j, ey.toInt(), top.first.toInt() + j, top.second.toInt(), col)
            }
        }
        for (j in 0 until max(1, z)) c.line(rx0.toInt(), ry0.toInt() + j, rx1.toInt(), ry1.toInt() + j, if (j == 0) Pal.WOOD_L else Pal.WOOD_D)
        // the wall plates along the eaves
        for (side in floatArrayOf(millL, millR)) {
            val ax = gx(side, d0); val ay = gy(d0) - eaveH * pm(d0); val bx = gx(side, d1); val by = gy(d1) - eaveH * pm(d1)
            for (j in 0 until max(1, z)) c.line(ax.toInt(), ay.toInt() + j, bx.toInt(), by.toInt() + j, Pal.WOOD_D)
        }
    }

    /** The mill's walls: a stone footing, planks above it, darker on the side away from the sun. */
    private fun wallTone(y: Int, base: Float, p: Float, shade: Float, x: Int): Int {
        val hgt = (base - y - 0.5f) / p
        val col = if (hgt < 0.6f) {
            val row = floor(hgt / 0.2f).toInt()
            val joint = detail > 1 && ((hgt / 0.2f) - row < 0.18f || Noise.rnd((x / (5 * detail)) + row * 3, row, 341) < 0.12f)
            if (joint) Pal.STONE_X else if (Noise.rnd(x / (5 * detail) + row, row, 342) < 0.5f) Pal.STONE_M else Pal.STONE_L
        } else {
            val plank = floor((hgt - 0.6f) / 0.27f).toInt()
            val within = (hgt - 0.6f) / 0.27f - plank
            val limit = if (detail > 1) 0.14f else 0.2f
            val seam = within < limit
            if (seam) Pal.WOOD_D else if (plank % 2 == 0) Pal.WOOD_M else Col.mix(Pal.WOOD_M, Pal.WOOD_L, 0.4f)
        }
        return if (shade < 1f) Col.scale(col, shade) else col
    }

    private fun roofTone(x: Int, y: Int, lightSide: Boolean, snow: Boolean): Int {
        val row = y / (2 * detail)
        val stagger = (x / (3 * detail) + row) % 2 == 0
        if (snow) return if (y % (2 * detail) == 0 && !lightSide) Pal.SNOW_M else Pal.SNOW_L
        val base = if (lightSide) (if (stagger) Pal.SHINGLE_L else Pal.SHINGLE_M) else (if (stagger) Pal.SHINGLE_M else Pal.SHINGLE_D)
        return if (y % (2 * detail) == 0) Col.scale(base, 0.82f) else base
    }

    /** The flume: a wooden trough on posts bringing the water from upstream to the top of the wheel. */
    private fun flume() {
        val z = z
        val hgt = wheelR + axleH + 0.3f
        val dA = wheelD - 0.15f; val dB = 31f
        val xa = gx(wheelX, dA); val ya = gy(dA) - hgt * pm(dA)
        val xb = gx(wheelX, dB); val yb = gy(dB) - (hgt + 0.25f) * pm(dB)
        for (dd in floatArrayOf(25.5f, 28.5f)) {
            val px = gx(wheelX, dd); val p = pm(dd)
            for (j in 0 until max(2, (0.16f * p).toInt())) c.vline(px.toInt() + j, (gy(dd) - (hgt + 0.1f) * p).toInt(), gy(dd).toInt(), if (j == 0) Pal.WOOD_M else Pal.WOOD_D)
        }
        val th = max(2, (0.38f * pm(dA)).toInt())
        for (j in 0 until th) c.line(xa.toInt(), ya.toInt() + j, xb.toInt(), (yb + j * pm(dB) / pm(dA)).toInt(), if (j == 0) Pal.WOOD_L else if (j == th - 1) Pal.WOOD_D else Pal.WOOD_M)
        // the water in it, running
        val gush = gushOf()
        if (gush > 0.05f && !env.snow) c.line(xa.toInt(), ya.toInt(), xb.toInt(), yb.toInt(), Col.mix(Pal.WATER_L, Col.hex(0xFFFFFF), 0.3f))
    }

    /** The overshot wheel facing us: rim, spokes and paddles, turning as fast as its water runs; the tailrace under it. */
    private fun wheel() {
        val z = z
        val lv = fx("wheel")
        val spin = when {
            !millDone -> 0.0 // new, and no water on it yet
            lv == null -> 0.8
            lv < 0.12f -> 0.0
            lv < 0.5f -> 0.8
            else -> 1.9
        }
        val a0 = (t * spin + 0.4).toFloat()
        val p = pm(wheelD)
        val cx = gx(wheelX, wheelD); val cy = gy(wheelD) - axleH * p
        val r = wheelR * p
        val rim = max(1.5f, 0.13f * p)
        // the pit behind it, dark and wet
        c.fillEllipse(cx, gy(wheelD) - 0.05f * p, r * 0.95f, max(1.5f, 0.3f * p), Col.hex(0x2A3A3A))
        // paddles round the rim
        val n = 16
        for (k in 0 until n) {
            val a = a0 + k * (2f * PI.toFloat() / n)
            val x0 = cx + cos(a) * (r - rim); val y0 = cy + sin(a) * (r - rim)
            val x1 = cx + cos(a) * (r + 0.18f * p); val y1 = cy + sin(a) * (r + 0.18f * p)
            for (j in 0 until max(1, z - 1)) c.line(x0.toInt() + j, y0.toInt(), x1.toInt() + j, y1.toInt(), Pal.WOOD_D)
        }
        // the rim, a ring of boards
        val r2o = r * r; val r2i = (r - rim) * (r - rim)
        for (y in max((cy - r).toInt() - 1, c.top)..min((cy + r).toInt() + 1, c.bottom - 1)) for (x in max((cx - r).toInt() - 1, c.left)..min((cx + r).toInt() + 1, c.right - 1)) {
            val dx = x + 0.5f - cx; val dy = y + 0.5f - cy
            val q = dx * dx + dy * dy
            if (q > r2o || q < r2i) continue
            val lit = dy < -dx * 0.3f
            c.set(x, y, if (lit) Pal.WOOD_L else Pal.WOOD_M)
        }
        // spokes and the axle
        for (k in 0 until 8) {
            val a = a0 + k * (PI.toFloat() / 4f)
            val ex = cx + cos(a) * (r - rim); val ey = cy + sin(a) * (r - rim)
            for (j in 0 until max(1, z - 1)) c.line(cx.toInt() + j, cy.toInt(), ex.toInt() + j, ey.toInt(), Pal.WOOD_M)
        }
        c.fillCircle(cx, cy, max(1.5f, 0.16f * p), Pal.WOOD_X)
        c.fillRect(cx.toInt(), (cy - max(1f, 0.06f * p)).toInt(), (gx(millL, wheelD) - cx).toInt(), max(1, (0.12f * p).toInt()), Pal.WOOD_X)
        if (env.snow) for (x in (cx - r * 0.7f).toInt()..(cx + r * 0.7f).toInt()) c.set(x, (cy - sqrt(max(0f, r * r - (x - cx) * (x - cx)))).toInt() - 1, Pal.SNOW_L)
        // the water pouring off the flume onto the paddles, and its spray
        val gush = gushOf()
        if (gush > 0.05f && !env.snow) {
            val topY = cy - r - 0.3f * p
            val wx = cx + r * 0.15f
            val wide = max(1f, 0.22f * p * gush)
            for (y in topY.toInt()..(cy + r * 0.8f).toInt()) {
                val f = (y - topY) / (r * 1.8f + 0.3f * p)
                val bend = sqrt(max(0f, r * r - (y - cy) * (y - cy))) * 0.85f
                val xw = if (y < cy - r) wx else cx + max(bend, r * 0.15f)
                for (q in 0 until wide.toInt().coerceAtLeast(1)) {
                    val shine = Noise.rnd(q, y / z, (t * 9).toInt()) < 0.3f
                    c.set(xw.toInt() + q, y, if (shine) Col.hex(0xF2F6FA) else Col.mix(Pal.WATER_L, Col.hex(0xFFFFFF), 0.25f + f * 0.2f))
                }
            }
            val drops = (6 + 16 * gush).toInt()
            for (k in 0 until drops) {
                val ph = ((t * 1.7 + Noise.rnd(k, 351)) % 1.0).toFloat()
                val sx = cx + r * 0.9f + (Noise.rnd(k, 352) - 0.3f) * r * 0.9f * ph
                val sy = gy(wheelD) - 0.1f * p - sin(ph * PI.toFloat()) * r * 0.55f * Noise.rnd(k, 353)
                c.fillRect(sx.toInt(), sy.toInt(), z, z, Col.hex(0xF2F6FA))
            }
        }
    }

    // ------------------------------------------------------------------ the footbridge, the stones

    /** The brv: planks across the stream on two logs, a handrail on posts along its far side. */
    private fun footbridge() {
        val z = z
        val lift = 0.55f
        val d0 = bridgeD - 0.45f; val d1 = bridgeD + 0.45f
        val snow = env.snow
        // the logs it lies on, along each bank
        for (xe in floatArrayOf(bridgeL + 0.25f, bridgeR - 0.25f)) {
            val p = pm(bridgeD)
            val bx = gx(xe, bridgeD)
            c.fillRect((bx - 0.2f * p).toInt(), (gy(d0) - lift * pm(d0)).toInt(), max(2, (0.4f * p).toInt()), (lift * pm(d0)).toInt() + z, Pal.LOG_D)
            if (z > 1) c.vline((bx - 0.2f * p).toInt(), (gy(d0) - lift * pm(d0)).toInt(), gy(d0).toInt(), Pal.LOG_M)
        }
        // the deck, row by row: planks along the bridge with dark seams between them, the front beam under its edge
        val yTop = (hz + F * (EYE - lift) / d1).toInt(); val yBot = (hz + F * (EYE - lift) / d0).toInt()
        for (py in max(yTop, c.top)..min(yBot, c.bottom - 1)) {
            val d = F * (EYE - lift) / (py + 0.5f - hz)
            val plank = ((d - d0) / 0.3f)
            val seam = z > 1 && plank - floor(plank) < 0.18f
            val xl = gx(bridgeL, d).toInt(); val xr = gx(bridgeR, d).toInt()
            for (px in max(xl, c.left)..min(xr, c.right - 1)) {
                val grain = Noise.rnd(px / (3 * z), floor(plank).toInt(), 361)
                val col = when {
                    snow -> if (Noise.rnd(px, py, 362) < 0.1f) Pal.SNOW_M else Pal.SNOW_L
                    seam -> Pal.WOOD_D
                    z >= 3 && (px - xl) % (18 * z / 3) == 2 -> Pal.WOOD_X // the nails
                    grain < 0.3f -> Pal.WOOD_M
                    else -> Pal.WOOD_L
                }
                c.set(px, py, col)
            }
        }
        val beam = max(1, (0.14f * pm(d0)).toInt())
        for (j in 1..beam) c.hline(gx(bridgeL, d0).toInt(), gx(bridgeR, d0).toInt(), yBot + j, if (j == 1) Pal.WOOD_M else Pal.WOOD_D)
        // posts and the handrail on the far side
        val railH = lift + 0.95f
        val posts = floatArrayOf(bridgeL + 0.2f, (bridgeL + bridgeR) / 2f, bridgeR - 0.2f)
        for (px in posts) {
            val x = gx(px, d1); val p = pm(d1)
            for (j in 0 until max(1, z)) c.vline(x.toInt() + j, (gy(d1) - railH * p).toInt(), (gy(d1) - lift * p).toInt(), if (j == 0) Pal.WOOD_M else Pal.WOOD_D)
        }
        val ax = gx(posts.first(), d1); val bx = gx(posts.last(), d1); val ry = gy(d1) - railH * pm(d1)
        for (j in 0 until max(1, z)) c.hline(ax.toInt(), bx.toInt() + z - 1, ry.toInt() + j, if (j == 0) (if (snow) Pal.SNOW_L else Pal.WOOD_L) else Pal.WOOD_D)
    }

    // ------------------------------------------------------------------ the cart bridge

    /**
     * The cart bridge upstream, as far as the village has built it (the project "most", [stage]): pegs on both banks and
     * a line over the water (measured), heaps of stones on the banks (gathered), the two stone piers (built), long logs
     * from pier to pier (brought), a deck of thick planks across them (laid); finished, a railing along both sides, and
     * the cart road runs over it from the village into the woods. Scenery: the scene's bridge word is the footbridge (brv).
     */
    private fun cartBridge() {
        val st = stage(BRIDGE)
        if (st <= 0) return
        items.add(cartD to { prop { cartBridge(st) } })
    }

    private fun cartBridge(st: Int) {
        val z = z
        val snow = env.snow
        val pierLi = -0.95f; val pierRi = 1.75f
        if (st <= 2) {
            // measured: pegs on both banks, a line over the water between them
            val line = if (snow) Pal.WOOD_X else Col.hex(0xE8E0CC)
            val a = cartL + 0.6f; val b = cartR - 0.6f
            c.line(gx(a, cartD).toInt(), (gy(cartD) - 0.45f * pm(cartD)).toInt(), gx(b, cartD).toInt(), (gy(cartD) - 0.45f * pm(cartD)).toInt(), line)
            for (x in floatArrayOf(a, b)) for (d in floatArrayOf(cartD0, cartD, cartD1)) {
                val p = pm(d)
                for (j in 0 until max(1, z)) c.vline(gx(x, d).toInt() + j, (gy(d) - 0.6f * p).toInt(), gy(d).toInt(), if (j == 0) Pal.WOOD_L else Pal.WOOD_D)
            }
            if (st == 2) { stoneHeap(cartL + 0.9f, cartD - 0.5f, 17); stoneHeap(cartR - 0.9f, cartD - 0.6f, 23) }
            return
        }
        // the piers, their inner faces toward the water, stone in courses
        pier(cartL, pierLi, pierLi); pier(pierRi, cartR, pierRi)
        if (st == 3) return
        // long logs from pier to pier, their cut ends toward us on the left
        val logs = floatArrayOf(cartD0 + 0.25f, cartD, cartD1 - 0.25f)
        for (d in logs.reversed()) {
            val p = pm(d); val th = max(z.toFloat(), 0.24f * p)
            val y = gy(d) - (pierH + 0.12f) * p
            val x0 = gx(cartL + 0.15f, d); val x1 = gx(cartR - 0.15f, d)
            for (j in 0 until th.toInt().coerceAtLeast(1)) c.hline(x0.toInt(), x1.toInt(), (y - th / 2f).toInt() + j, if (j == 0) Pal.LOG_M else Pal.LOG_D)
            c.fillEllipse(x0, y, max(1f, th * 0.5f), max(1f, th * 0.5f), Col.hex(0xC8A070))
            if (snow) c.hline(x0.toInt(), x1.toInt(), (y - th / 2f).toInt() - 1, Pal.SNOW_L)
        }
        if (st == 4) return
        // the deck: thick planks across the logs, dark seams between them
        val lift = pierH + 0.3f
        val yTop = (hz + F * (EYE - lift) / cartD1).toInt(); val yBot = (hz + F * (EYE - lift) / cartD0).toInt()
        for (py in max(yTop, c.top)..min(yBot, c.bottom - 1)) {
            val d = F * (EYE - lift) / (py + 0.5f - hz)
            val xl = gx(cartL, d).toInt(); val xr = gx(cartR, d).toInt()
            for (px in max(xl, c.left)..min(xr, c.right - 1)) {
                val x = sideAt(px, d)
                val plank = (x - cartL) / 0.32f
                val gap = if (z > 1) 0.14f else 0.2f
                val seam = plank - floor(plank) < gap
                c.set(px, py, when {
                    snow -> if (Noise.rnd(px, py, 363) < 0.1f) Pal.SNOW_M else Pal.SNOW_L
                    seam -> Pal.WOOD_D
                    Noise.rnd(floor(plank).toInt(), 0, 364) < 0.35f -> Pal.WOOD_M
                    else -> Pal.WOOD_L
                })
            }
        }
        // the deck's front edge, a beam under it
        val beam = max(z, (0.16f * pm(cartD0)).toInt())
        for (j in 1..beam) c.hline(gx(cartL, cartD0).toInt(), gx(cartR, cartD0).toInt(), yBot + j, if (j == 1) Pal.WOOD_M else Pal.WOOD_D)
        if (st < 6) return
        // finished: a railing on posts along both sides
        val railH = 0.85f
        for (d in floatArrayOf(cartD1 - 0.08f, cartD0 + 0.08f)) {
            val p = pm(d)
            val base = gy(d) - lift * p; val top = base - railH * p
            var x = cartL + 0.15f
            while (x < cartR) {
                val px = gx(x, d).toInt()
                for (j in 0 until max(1, z)) c.vline(px + j, top.toInt(), base.toInt(), if (j == 0) Pal.WOOD_M else Pal.WOOD_D)
                x += 1.18f
            }
            val ax = gx(cartL + 0.15f, d).toInt(); val bx = gx(cartR - 0.15f, d).toInt() + z
            for (j in 0 until max(1, z)) c.hline(ax, bx, top.toInt() + j, if (j == 0) (if (snow) Pal.SNOW_L else Pal.WOOD_L) else Pal.WOOD_D)
        }
    }

    /**
     * A stone pier on the bank from [x0] to [x1] (m), as deep as the bridge: its top, its front toward us, and its face
     * toward the water at [inner].
     */
    private fun pier(x0: Float, x1: Float, inner: Float) {
        val snow = env.snow
        val d0 = cartD0; val d1 = cartD1; val h = pierH
        val p0 = pm(d0); val p1 = pm(d1)
        // the face toward the water, going back
        c.polyBegin()
        c.polyAdd(gx(inner, d0), gy(d0)); c.polyAdd(gx(inner, d1), gy(d1)); c.polyAdd(gx(inner, d1), gy(d1) - h * p1); c.polyAdd(gx(inner, d0), gy(d0) - h * p0)
        c.polyFill { x, y -> val d = F * inner / (x + 0.5f - vx).let { if (abs(it) < 0.5f) 0.5f else it }; pierTone(x, y, gy(d.coerceIn(d0, d1)), pm(d.coerceIn(d0, d1)), 0.72f) }
        // the top
        c.polyBegin()
        c.polyAdd(gx(x0, d0), gy(d0) - h * p0); c.polyAdd(gx(x1, d0), gy(d0) - h * p0); c.polyAdd(gx(x1, d1), gy(d1) - h * p1); c.polyAdd(gx(x0, d1), gy(d1) - h * p1)
        c.polyFill { x, y -> if (snow) Pal.SNOW_L else if (Noise.rnd(x / (3 * z), y / z, 367) < 0.35f) Pal.STONE_L else Col.mix(Pal.STONE_L, Pal.STONE_M, 0.5f) }
        // the front, toward us
        c.polyBegin()
        c.polyAdd(gx(x0, d0), gy(d0)); c.polyAdd(gx(x1, d0), gy(d0)); c.polyAdd(gx(x1, d0), gy(d0) - h * p0); c.polyAdd(gx(x0, d0), gy(d0) - h * p0)
        c.polyFill { x, y -> pierTone(x, y, gy(d0), p0, 1f) }
    }

    /** A pier's stone: courses of blocks, the joints between them showing closer up, wet and dark at the foot. */
    private fun pierTone(x: Int, y: Int, base: Float, p: Float, shade: Float): Int {
        val hgt = (base - y - 0.5f) / p
        val row = floor(hgt / 0.26f).toInt()
        val within = hgt / 0.26f - row
        val block = floor((x + row * 3 * z) / (5f * z)).toInt()
        val joint = if (detail > 1) 0.16f else 0.1f
        var col = when {
            within < joint -> Pal.STONE_X
            detail > 1 && (x + row * 3 * z) % (5 * z) == 0 -> Pal.STONE_X
            Noise.rnd(block, row, 368) < 0.45f -> Pal.STONE_M
            else -> Pal.STONE_L
        }
        if (hgt < 0.18f) col = Col.mix(col, Pal.STONE_X, 0.5f)
        return if (shade < 1f) Col.scale(col, shade) else col
    }

    /** A heap of gathered stones on the bank around ([x], [d]). */
    private fun stoneHeap(x: Float, d: Float, seed: Int) {
        val p = pm(d)
        for (k in 0 until 9) {
            val row = if (k < 5) 0 else if (k < 8) 1 else 2
            val u = (k - (if (row == 0) 2f else if (row == 1) 6f else 8f)) * 0.26f + (Noise.rnd(k, seed) - 0.5f) * 0.1f
            val sx = gx(x + u, d); val sy = gy(d) - (0.1f + row * 0.17f) * p
            val r = max(1.2f, (0.13f + Noise.rnd(k, seed + 1) * 0.05f) * p)
            c.fillEllipse(sx, sy, r, r * 0.75f, if (Noise.rnd(k, seed + 2) < 0.5f) Pal.STONE_M else Pal.STONE_D)
            c.fillEllipse(sx - r * 0.25f, sy - r * 0.25f, r * 0.5f, r * 0.35f, if (env.snow) Pal.SNOW_L else Pal.STONE_L)
        }
    }

    /** Five flat stones across the stream, wet at the waterline, lit on top; snow on them in winter. */
    private fun steppingStones() {
        val z = z
        for ((k, sx) in stones.withIndex()) {
            val sd = stonesD + (k % 2) * 0.2f - 0.1f
            val p = pm(sd)
            val bx = gx(sx, sd); val by = gy(sd)
            val r = 0.26f + Noise.rnd(k, 371) * 0.08f
            val top = 0.16f + Noise.rnd(k, 372) * 0.05f
            val rx = r * p; val ry = max(1.2f, r * F * (EYE - top) / (sd * sd))
            val ty = by - top * p
            // the wet side down to the water, then the dry top
            c.fillEllipse(bx, (by + ty) / 2f, rx, ry + (by - ty) / 2f, Pal.STONE_X)
            c.fillEllipse(bx, ty + ry * 0.3f, rx * 0.97f, ry, Pal.STONE_D)
            c.fillEllipse(bx - rx * 0.05f, ty, rx * 0.93f, ry * 0.9f, if (env.snow) Pal.SNOW_L else Pal.STONE_M)
            c.fillEllipse(bx - rx * 0.3f, ty - ry * 0.25f, rx * 0.45f, ry * 0.45f, if (env.snow) Col.hex(0xFFFFFF) else Pal.STONE_L)
            if (z > 1 && !env.snow) {
                // a crack, a fleck of moss, the wet sheen at the waterline
                c.line((bx + rx * 0.1f).toInt(), (ty - ry * 0.5f).toInt(), (bx + rx * 0.45f).toInt(), (ty + ry * 0.4f).toInt(), Pal.STONE_X)
                if (k % 2 == 0) c.fillEllipse(bx + rx * 0.5f, ty + ry * 0.1f, rx * 0.15f, ry * 0.3f, Col.hex(0x6E8A48))
                c.hline((bx - rx * 0.8f).toInt(), (bx + rx * 0.5f).toInt(), (by - z).toInt(), Col.mix(Pal.STONE_X, Pal.WATER_L, 0.4f))
            }
        }
    }

    // ------------------------------------------------------------------ the willow

    /**
     * A weeping willow on the left bank, its trunk leaning out over the water, its crown hanging in long strands that
     * sway in the wind: silvery green, bright in spring, yellow in autumn, bare golden twigs in winter.
     */
    private fun willow(x: Float, d: Float) {
        val z = z
        val p = pm(d); val bx = gx(x, d); val by = gy(d)
        val bark = Col.hex(0x5E5446); val barkL = Col.hex(0x7E7260); val barkD = Col.hex(0x3E362C)
        // the trunk, leaning toward the stream, furrowed
        val lean = 1.1f * p; val tall = 2.7f * p; val th = 0.24f * p
        for (y in max((by - tall).toInt(), c.top)..min(by.toInt(), c.bottom - 1)) {
            val f = (by - y) / tall
            val cx = bx + lean * f * f
            val hw = th * (1.15f - 0.35f * f) + if (f < 0.1f) (0.1f - f) * 3f * th else 0f
            for (xx in max((cx - hw).toInt(), c.left)..min((cx + hw).toInt(), c.right - 1)) {
                val u = (xx + 0.5f - cx) / hw
                var col = if (u < -0.4f) barkL else if (u > 0.45f) barkD else bark
                if (z > 1 && abs(Noise.v2(xx * 0.5f / z, y * 0.08f / z, 381) - 0.5f) < 0.06f) col = barkD
                c.set(xx, y, col)
            }
        }
        val topX = bx + lean; val topY = by - tall
        val season = env.season
        val cx = topX + 0.6f * p; val cy = topY - 1.3f * p
        val crx = 2.8f * p; val cry = 1.5f * p
        // the main limbs up into the crown
        for (a in floatArrayOf(-0.9f, -0.2f, 0.5f, 1.1f)) {
            val ex = cx + sin(a) * crx * 0.7f; val ey = cy - cos(a) * cry * 0.6f
            for (j in 0 until max(1, (0.1f * p).toInt())) c.line(topX.toInt() + j, topY.toInt(), ex.toInt() + j, ey.toInt(), bark)
        }
        val (dark, mid, light) = when (season) {
            Season.SPRING -> Triple(Col.hex(0x6A9A3A), Col.hex(0x96C04E), Col.hex(0xC8E27A))
            Season.AUTUMN -> Triple(Col.hex(0x9A7A2A), Col.hex(0xC8A63A), Col.hex(0xECD46A))
            Season.WINTER -> Triple(Col.hex(0x8A7A3A), Col.hex(0xB09A48), Col.hex(0xD0BA62))
            else -> Triple(Col.hex(0x4E7A3A), Col.hex(0x74A052), Col.hex(0xA8C88A))
        }
        val bare = season == Season.WINTER
        // the curtain: strands hanging from the crown nearly to the ground, each to its own length, swaying more toward
        // their ends; streaks of light and shade with gaps between them (more in winter, when only the golden twigs hang)
        val top = cy - cry * 0.3f; val hem = by - 0.3f * p
        val sway = sin(t * 1.1).toFloat() * 0.14f * p * gust
        val reachX = crx * 1.08f
        for (y in max(top.toInt(), c.top)..min(hem.toInt(), c.bottom - 1)) {
            val f = (y + 0.5f - top) / (hem - top)
            val shift = sway * f * f + sin(t * 1.7 + y * 0.02 / z).toFloat() * f * 0.3f * z * gust
            for (xx in max((cx - reachX + min(0f, shift)).toInt(), c.left)..min((cx + reachX + max(0f, shift)).toInt(), c.right - 1)) {
                val xs = xx + 0.5f - shift
                val u = (xs - cx) / reachX
                if (abs(u) >= 1f) continue
                val xb = xs / z
                val round = sqrt(1f - u * u)
                // how far down this strand hangs, and where the crown's underside lets it start
                if (f > 0.42f + 0.58f * round - 0.3f * Noise.v1(xb * 0.7f, 383)) continue
                if (f < 0.25f * (1f - round)) continue
                val streak = Noise.v1(xb * 1.2f, 384)
                val leaf = Noise.v2(xb * 1.2f, (y + 0.5f) / z * 0.3f, 385)
                if (streak < (if (bare) 0.55f else 0.18f) && leaf < 0.7f) continue
                if (bare && (xb.toInt() % 2 == 1)) continue
                val shade = streak * 0.9f + (leaf - 0.5f) * 0.5f - u * 0.25f - f * 0.2f
                c.set(xx, y, if (shade > 0.62f) light else if (shade > 0.3f) mid else dark)
            }
        }
        // the crown's top: a low dome of leaves over the strands (only twigs in winter)
        if (!bare) for (y in max((cy - cry).toInt(), c.top)..min((cy + cry * 0.4f).toInt(), c.bottom - 1)) for (xx in max((cx - crx).toInt(), c.left)..min((cx + crx).toInt(), c.right - 1)) {
            val dx = (xx + 0.5f - cx) / crx; val dy = (y + 0.5f - cy) / cry
            val q = dx * dx + dy * dy
            if (q > 1f || (q > 0.82f && Dither.at(xx, y) < 0.4f)) continue
            val n = Noise.v2(xx * 0.4f / z, y * 0.6f / z, 382)
            val lit = -dy * 0.8f - dx * 0.2f + (n - 0.5f) * 0.9f
            c.set(xx, y, if (lit > 0.35f) light else if (lit > -0.3f) mid else dark)
        }
    }

    // ------------------------------------------------------------------ the creatures

    /** A sheep at the water's edge, its head down drinking. */
    private fun sheep(x: Float, d: Float) {
        val z = z
        val p = pm(d); val bx = gx(x, d); val by = gy(d)
        val wool = if (env.snow) Col.hex(0xECE6DA) else Col.hex(0xF4F0E4); val woolD = Col.hex(0xCFC8B8); val face = Col.hex(0x2E2A2C)
        val bob = (sin(t * 1.4) * 0.03f * p).toFloat()
        for (lx in floatArrayOf(-0.32f, -0.12f, 0.2f, 0.38f)) {
            val lxp = bx + lx * p
            for (j in 0 until max(1, (0.07f * p).toInt())) c.vline(lxp.toInt() + j, (by - 0.42f * p).toInt(), by.toInt() - 1, face)
        }
        // the fleece: curls on a round body, shaded underneath
        val ex = bx + 0.05f * p; val ey = by - 0.62f * p; val rx = 0.5f * p; val ry = 0.3f * p
        for (y in max((ey - ry).toInt() - 1, c.top)..min((ey + ry).toInt() + 1, c.bottom - 1)) for (xx in max((ex - rx).toInt() - 1, c.left)..min((ex + rx).toInt() + 1, c.right - 1)) {
            val dx = (xx + 0.5f - ex) / rx; val dy = (y + 0.5f - ey) / ry
            val q = dx * dx + dy * dy
            val curl = Noise.v2(xx * 0.9f / z, y * 0.9f / z, 391)
            if (q > 1f + (curl - 0.5f) * 0.3f) continue
            c.set(xx, y, if (dy > 0.35f || (z > 1 && curl > 0.72f)) woolD else wool)
        }
        // the head down to the water, ears out
        val hx = bx - 0.55f * p; val hy = by - 0.32f * p + bob
        c.fillEllipse(hx, hy, max(1.5f, 0.14f * p), max(1.2f, 0.12f * p), face)
        c.fillEllipse(bx - 0.4f * p, by - 0.5f * p + bob / 2f, max(1f, 0.13f * p), max(1f, 0.14f * p), face)
        c.set((hx + 0.1f * p).toInt(), (hy - 0.12f * p).toInt(), face)
        c.fillEllipse(bx - 0.42f * p, by - 0.62f * p, max(1f, 0.12f * p), max(1f, 0.09f * p), wool) // the woolly top-knot
        // rings where it drinks
        if (!env.snow) s.fx { ring(x - 0.95f, d - 0.05f, 0.12f + 0.18f * ((t * 0.9) % 1.0).toFloat(), 0.4f * (1f - ((t * 0.9) % 1.0).toFloat())) }
    }

    /** The trout holding in the current under the surface, head upstream, its tail beating; spotted closer up. */
    private fun trout(x: Float, d: Float) {
        val z = z
        val wag = sin(t * 7.0).toFloat() * 0.05f
        // head upstream (away from us, a little left), the tail toward us
        val hx = x - 0.3f; val hd = d + 0.4f
        val tx = x + 0.2f + wag; val td = d - 0.28f
        val ax = gx(hx, hd); val ay = gy(hd); val bxp = gx(tx, td); val byp = gy(td)
        val water = Col.mix(Pal.WATER_M, skyAt(0.85f), 0.35f)
        val back = Col.mix(Col.hex(0x34402A), water, 0.15f); val flank = Col.mix(Col.hex(0xB0A878), water, 0.2f)
        val n = 16
        val wide = max(1.5f, 0.13f * pm(d))
        // its shadow on the bed, off to the side
        val shadowCol = Col.mix(water, Pal.WATER_D, 0.55f)
        for (i in 0..n) {
            val f = i / n.toFloat()
            c.fillEllipse(ax + (bxp - ax) * f + 0.2f * pm(d), ay + (byp - ay) * f + 0.1f * pm(d), wide * (1f - abs(f - 0.3f)) * 0.8f, wide * 0.5f, shadowCol)
        }
        // the body: a blunt head, fullest a third of the way back, tapering to the tail
        for (i in 0..n) {
            val f = i / n.toFloat()
            val px = ax + (bxp - ax) * f; val py = ay + (byp - ay) * f
            val r = wide * (if (f < 0.3f) 0.55f + f * 1.5f else 1f - (f - 0.3f) * 1.1f).coerceAtLeast(0.25f)
            c.fillEllipse(px, py, r + 0.2f, r * 0.75f + 0.2f, back)
            c.fillEllipse(px - 0.25f * r, py + 0.3f * r, r * 0.6f, max(0.5f, r * 0.3f), flank)
        }
        // the tail fin, beating
        val fx = bxp + (bxp - ax) * 0.1f; val fy = byp + (byp - ay) * 0.1f
        c.fillEllipse(fx, fy, max(1.3f, wide * 1f), max(0.9f, wide * 0.45f), back)
        if (z > 1) for (k in 0 until 6) {
            // the spots, dark and a few red
            val f = 0.2f + Noise.rnd(k, 395) * 0.55f
            val sx = ax + (bxp - ax) * f + (Noise.rnd(k, 396) - 0.5f) * wide
            val sy = ay + (byp - ay) * f + (Noise.rnd(k, 397) - 0.5f) * wide * 0.5f
            c.set(sx.toInt(), sy.toInt(), if (k % 3 == 0) Col.mix(Col.hex(0xC8483A), water, 0.3f) else Col.mix(Col.hex(0x2A2A1E), water, 0.3f))
        }
    }

    /** A leaping trout (the "trout" effect): out of the current again and again, rings spreading where it falls back. */
    private fun leaps(level: Float) {
        if (level <= 0.02f) return
        val z = z
        val period = 2.4 - 1.3 * level
        val n = floor(t / period).toInt()
        for (j in n - 1..n) {
            val t0 = j * period + Noise.rnd(j, 401) * 0.3
            val age = (t - t0).toFloat()
            if (age < 0f) continue
            val d0 = 12.6f + Noise.rnd(j, 403) * 4.8f
            val x0 = course(d0) + (Noise.rnd(j, 402) - 0.5f) * half(d0) * 1.1f
            val leap = 0.6f
            if (age < leap) {
                val u = age / leap
                val x = x0 + 0.35f * u; val d = d0 - 0.45f * u; val hgt = 0.85f * sin(u * PI.toFloat())
                val p = pm(d)
                val bx = gx(x, d); val by = gy(d) - hgt * p
                val ang = (0.5f - u) * 1.8f // nose up rising, down falling
                val len = 0.6f * p
                // head forward (to the right), the tail behind
                val dx = cos(ang) * len / 2f; val dy = -sin(ang) * len / 2f
                val w2 = max(1.2f, 0.09f * p)
                for (i in 0..10) {
                    val f = i / 10f - 0.5f
                    val r = w2 * (1f - abs(f + 0.1f) * 1.3f).coerceAtLeast(0.4f)
                    c.fillEllipse(bx + dx * f * 2f, by + dy * f * 2f, r + 0.3f, r + 0.3f, LEAP_BACK)
                    c.fillEllipse(bx + dx * f * 2f, by + dy * f * 2f + r * 0.45f, r * 0.8f, max(0.5f, r * 0.45f), LEAP_BELLY)
                }
                // the tail fin, spread
                c.fillEllipse(bx - dx * 1.12f, by - dy * 1.12f, w2 * 0.7f, w2 * 1.3f, LEAP_BACK)
                // drops trailing off it
                for (k in 0 until 5) c.fillRect((bx - dx * (1.4f + k * 0.35f)).toInt(), (by - dy * (1.4f + k * 0.35f) + (k * k) * z * 0.5f).toInt(), z, z, Col.hex(0xE8F0F8))
            } else {
                val a = age - leap
                val lx = x0 + 0.35f; val ld = d0 - 0.45f
                if (a < 0.3f) {
                    // the splash
                    val p = pm(ld)
                    for (k in 0 until 7) {
                        val ang = k * 0.9f
                        val sx = gx(lx, ld) + cos(ang) * (0.1f + a) * p * 0.8f
                        val sy = gy(ld) - sin(a / 0.3f * PI.toFloat()) * 0.18f * p * (0.6f + Noise.rnd(k, 404) * 0.6f)
                        c.fillRect(sx.toInt(), sy.toInt(), z, z, Col.hex(0xF2F6FA))
                    }
                }
                if (a < 1.3f) s.fx {
                    ring(lx, ld, 0.1f + a * 0.45f, 0.7f * (1f - a / 1.3f))
                    if (a > 0.2f) ring(lx, ld, 0.05f + (a - 0.2f) * 0.35f, 0.5f * (1f - a / 1.3f))
                }
            }
        }
    }

    /** A ring on the water, [r] m round ([x], [d]), blended over the water only. */
    private fun ring(x: Float, d: Float, r: Float, a: Float) {
        if (a <= 0.02f || r <= 0f) return
        val rx = r * pm(d); val ry = max(0.6f, r * F * EYE / (d * d))
        val cx = gx(x, d); val cy = gy(d)
        val steps = max(12, (rx * 4f).toInt())
        val col = Col.mix(skyAt(0.8f), Col.hex(0xFFFFFF), 0.55f)
        for (k in 0 until steps) {
            val ang = k * 2f * PI.toFloat() / steps
            val px = floor(cx + cos(ang) * rx).toInt(); val py = floor(cy + sin(ang) * ry).toInt()
            if (!c.inside(px, py) || c.ids[c.index(px, py)] != waterId) continue
            c.blend(px, py, col, a)
        }
    }

    /** Watercress in the shallows by the right bank: round leaves heaped on the water, white flowers in summer; green all winter. */
    private fun cress(x: Float, d: Float) {
        val z = z
        val dark = Col.hex(0x2E6A2A); val mid = Col.hex(0x4A9038); val light = Col.hex(0x7CBC4E)
        for (k in 0 until 26) {
            val a = Noise.rnd(k, 411) * 2f * PI.toFloat(); val rr = sqrt(Noise.rnd(k, 412))
            val lx = x + cos(a) * 0.5f * rr; val ld = d + sin(a) * 0.32f * rr
            val lh = 0.03f + (1f - rr) * 0.12f + Noise.rnd(k, 413) * 0.05f
            val p = pm(ld)
            val px = gx(lx, ld); val py = gy(ld) - lh * p
            val r = max(1f, (0.055f + Noise.rnd(k, 414) * 0.03f) * p)
            val col = if (k % 3 == 0) dark else if (k % 3 == 1) mid else light
            c.fillEllipse(px, py, r, r * 0.75f, col)
            if (z > 1) c.set((px - r * 0.3f).toInt(), (py - r * 0.3f).toInt(), Col.mix(col, Col.hex(0xFFFFFF), 0.3f))
        }
        if (env.month in 5..8) for (k in 0 until 5) {
            val lx = x + (Noise.rnd(k, 415) - 0.5f) * 0.7f; val ld = d + (Noise.rnd(k, 416) - 0.5f) * 0.3f
            val p = pm(ld); val px = gx(lx, ld); val py = gy(ld) - 0.22f * p
            c.vline(px.toInt(), py.toInt(), (py + 0.1f * p).toInt(), mid)
            c.fillRect(px.toInt() - z / 2, py.toInt() - z, z, z, Col.hex(0xFFFFFF))
            if (z > 1) { c.set(px.toInt() - z, py.toInt() - z / 2, Col.hex(0xFFFFFF)); c.set(px.toInt() + z / 2, py.toInt() - z / 2, Col.hex(0xFFFFFF)) }
        }
    }

    /** Micka's washing stone at the water's edge with a wet sheet over it, and her basket of laundry on the bank. */
    private fun laundry() {
        val z = z
        // the stone slab, half in the water
        val sd = 11.1f; val sx = course(sd) - half(sd) + 0.1f
        val p = pm(sd); val bx = gx(sx, sd); val by = gy(sd)
        val rx = 0.42f * p; val ry = max(1.5f, 0.3f * F * EYE / (sd * sd)); val top = 0.2f * p
        c.fillEllipse(bx, by - top / 2f, rx, ry + top / 2f, Pal.STONE_X)
        c.fillEllipse(bx, by - top, rx * 0.96f, ry, if (env.snow) Pal.SNOW_L else Pal.STONE_M)
        c.fillEllipse(bx - rx * 0.3f, by - top - ry * 0.2f, rx * 0.4f, ry * 0.5f, if (env.snow) Col.hex(0xFFFFFF) else Pal.STONE_L)
        if (!env.snow) {
            // the wet sheet over its right half, trailing into the water
            val sheet = Col.hex(0xF2F0E8); val fold = Col.hex(0xC8CCD0)
            c.polyBegin()
            c.polyAdd(bx - rx * 0.1f, by - top - ry); c.polyAdd(bx + rx * 0.85f, by - top - ry * 0.6f)
            c.polyAdd(bx + rx * 1.25f, by + ry * 0.4f); c.polyAdd(bx + rx * 0.3f, by + ry * 0.7f); c.polyAdd(bx - rx * 0.15f, by - top + ry * 0.2f)
            c.polyFill { x, y -> if (((x - y * 2) / max(1, 2 * z)) % 3 == 0) fold else sheet }
        }
        // the basket, woven, heaped with laundry
        val kd = 11.5f; val kx = -4.75f
        val kp = pm(kd); val kbx = gx(kx, kd); val kby = gy(kd)
        val krx = 0.34f * kp; val kh = 0.34f * kp
        groundShadow(kx, kd, 0.4f)
        val wicker = Col.hex(0xB08850); val wickerD = Col.hex(0x7E5E34)
        for (y in max((kby - kh).toInt(), c.top)..min(kby.toInt(), c.bottom - 1)) {
            val f = (kby - y) / kh
            val hw = krx * (0.85f + 0.15f * f)
            for (xx in max((kbx - hw).toInt(), c.left)..min((kbx + hw).toInt(), c.right - 1)) {
                val weave = ((xx / max(1, z)) + (y / max(1, z))) % 2 == 0
                val side = (xx + 0.5f - kbx) / hw
                c.set(xx, y, if (side > 0.6f || !weave) wickerD else wicker)
            }
        }
        c.fillEllipse(kbx, kby - kh, krx, max(1f, krx * 0.3f), wickerD)
        if (env.snow) { c.fillEllipse(kbx, kby - kh - 0.05f * kp, krx * 0.9f, max(1f, krx * 0.35f), Pal.SNOW_L); return }
        // the heap: sheets, a blue shirt, a red kerchief
        c.fillEllipse(kbx, kby - kh - 0.06f * kp, krx * 0.92f, max(1.5f, 0.14f * kp), Col.hex(0xF2F0E8))
        c.fillEllipse(kbx + krx * 0.35f, kby - kh - 0.1f * kp, krx * 0.4f, max(1f, 0.09f * kp), Col.hex(0x6A8CC8))
        c.fillEllipse(kbx - krx * 0.4f, kby - kh - 0.11f * kp, krx * 0.3f, max(1f, 0.07f * kp), Col.hex(0xC84A3A))
        if (z > 1) c.hline((kbx - krx * 0.6f).toInt(), (kbx + krx * 0.1f).toInt(), (kby - kh - 0.02f * kp).toInt(), Col.hex(0xC8CCD0))
    }

    /** Rushes along the left bank near us, brown heads on them. */
    private fun rushes() {
        val z = z
        val green = if (env.snow) Col.hex(0x7A6A44) else Col.scale(env.grass[2], 0.85f); val dry = Col.hex(0x8A7A4A)
        for (k in 0 until 9) {
            val rd = 10.3f + Noise.rnd(k, 421) * 2.4f
            val rxm = course(rd) - half(rd) - 0.15f - Noise.rnd(k, 422) * 0.35f
            val p = pm(rd); val bx = gx(rxm, rd); val by = gy(rd)
            val hgt = (0.5f + Noise.rnd(k, 423) * 0.35f) * p
            val sway = sin(t * 1.3 + k).toFloat() * 0.04f * p * gust
            val col = if (env.season == Season.AUTUMN) dry else green
            c.line(bx.toInt(), by.toInt(), (bx + sway).toInt(), (by - hgt).toInt(), col)
            if (k % 3 == 0) c.fillRect((bx + sway).toInt() - (z - 1) / 2, (by - hgt).toInt() - z, max(1, z), 2 * z, Col.hex(0x6A4A2A))
        }
    }

    /**
     * The dragonfly: by day darting from one hover to the next over the water, wings a shimmer; at night resting on a
     * rush by the bank, wings spread.
     */
    private fun dragonfly() {
        val rest = env.dark > 0.5f || env.snow
        val x: Float; val d: Float; val hgt: Float
        if (rest) { x = course(11.6f) - half(11.6f) - 0.3f; d = 11.6f; hgt = 0.62f }
        else {
            val seg = 1.4
            val n = floor(t / seg).toInt()
            val e = (((t - n * seg) / 0.25).coerceIn(0.0, 1.0)).toFloat().let { it * it * (3f - 2f * it) }
            fun at(i: Int, k: Int) = Noise.rnd(i, k)
            // over the cress and the right bank, where it shows against the grass
            val xa = 1.0f + (at(n - 1, 431) - 0.5f) * 1.3f; val xb = 1.0f + (at(n, 431) - 0.5f) * 1.3f
            val da = 11.4f + (at(n - 1, 432) - 0.5f) * 1.0f; val db = 11.4f + (at(n, 432) - 0.5f) * 1.0f
            val ha = 0.95f + (at(n - 1, 433) - 0.5f) * 0.4f; val hb = 0.95f + (at(n, 433) - 0.5f) * 0.4f
            x = xa + (xb - xa) * e + sin(t * 9.0).toFloat() * 0.01f
            d = da + (db - da) * e; hgt = ha + (hb - ha) * e + sin(t * 6.0).toFloat() * 0.02f
        }
        val bx = gx(x, d).toInt(); val by = (gy(d) - hgt * pm(d)).toInt()
        val blue = Col.hex(0x2A7AD8); val blueL = Col.hex(0x6AB8F0); val eye = Col.hex(0x1E5A3A)
        val flick = !rest && ((t * 30).toInt() and 1) == 0
        sprite(bx, by) {
            // the long body, the thorax and big eyes at the front (left)
            for (i in 0..6) c.set(bx + i, by, if (i % 2 == 0) blue else blueL)
            c.set(bx - 1, by, Col.hex(0x3E8A5A)); c.set(bx - 2, by, eye); c.set(bx - 2, by - 1, eye)
            c.set(bx + 7, by + (if (rest) 0 else 1), blue)
            // the wings: glassy, a blur in flight
            val wing = Col.hex(0xDCEAF4)
            val a = if (flick) 0.35f else 0.6f
            c.blend(bx - 1, by - 1, wing, a); c.blend(bx, by - 2, wing, a); c.blend(bx + 1, by - 2, wing, a); c.blend(bx + 1, by - 1, wing, a)
            c.blend(bx - 1, by + 1, wing, a); c.blend(bx, by + 2, wing, a); c.blend(bx + 1, by + 2, wing, a); c.blend(bx + 1, by + 1, wing, a)
        }
    }

    // ------------------------------------------------------------------ weather on the water

    override fun weather() {
        val sky = frame.sky
        if (sky.rain > 0.02f) swollen(sky.rain)
        if (sky.fog > 0.02f) {
            // mist lying on the water, thicker than over the meadow
            Weather(c, w, h, detail, t, env, sky) { id -> id == waterId }.fog(sky.fog * 0.7f) { 1f }
        }
        super.weather()
    }

    /** In the rain the stream runs fuller and greyer: brown-grey water spilling a little over its banks, streaks of foam. */
    private fun swollen(rain: Float) {
        val z = z
        val muddy = env.lit(Col.hex(0x8C8A74))
        val spill = 0.4f * rain
        for (py in max(max(hz + 1, gy(woods).toInt()), c.top) until min(h, c.bottom)) {
            val d = depthAt(py)
            val cx = course(d); val hw = half(d) + 0.12f + spill
            for (px in max(max(0, gx(cx - hw, d).toInt()), c.left)..min(min(w - 1, gx(cx + hw, d).toInt() + 1), c.right - 1)) {
                val i = c.index(px, py)
                val id = c.ids[i]
                val x = sideAt(px, d)
                val off = abs(x - cx) - reach(x, d)
                if (id == waterId) {
                    var col = Col.mix(c.pixels[i], muddy, 0.4f * rain)
                    val streak = Noise.rnd((x * 12f).toInt(), ((d + t.toFloat() * 2.4f) * 6f).toInt(), 441)
                    if (streak < 0.06f * rain) col = Col.mix(col, env.lit(Col.hex(0xE8ECEE)), 0.6f)
                    c.pixels[i] = col
                } else if (id == 0 && off in 0f..spill && !c.emissive[i]) {
                    val a = (1f - off / max(0.01f, spill)) * 0.85f
                    if (a > Dither.at(px / z, py / z) * 0.9f) c.pixels[i] = Col.mix(c.pixels[i], muddy, 0.75f)
                }
            }
        }
    }

    companion object {
        /** The village projects this scene shows as they're built: France's mill, the cart bridge. */
        private const val MILL = "mlin"
        private const val BRIDGE = "most"

        /** The moon's column on the stage: over the stream's far reach, so it shimmers in the water. */
        private const val MOON_X = 128
        private val BANK = Col.hex(0x3A4A30)
        private val FLY_OUTLINE = Col.hex(0x1A2A3A)
        private val LEAP_BACK = Col.hex(0x4A5634)
        private val LEAP_BELLY = Col.hex(0xE4E6DC)
        private val CRESS_D = Col.hex(0x1E3A1E)
    }
}
