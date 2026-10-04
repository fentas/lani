package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.Season
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pri ribniku: the pond in the woods below the village, seen from its near shore. Still water holds the sky and the
 * dark wall of spruces and beeches round it; water lilies float on the left with a frog on the biggest pad, a bank of
 * reeds stands at our left, a wooden jetty runs out on the right with a boat tied at its end. A duck leads her
 * ducklings across, a carp's back shows under the surface, a grey heron waits in the shallows by the far right shore,
 * a dragonfly hovers over the lilies. At night the moon lies on the water; in winter the pond freezes but for a hole
 * where the ducks swim.
 *
 * A dialog can send wild ducks up out of the reeds and away over the trees ("ducks", the level is how far they have
 * flown), the frog off its pad into the water ("frog", 1: in the water, just its eyes out) and a fish jumping again and
 * again ("fish"). Rain rings the whole pond; fog lies thick on the water.
 *
 * Its wild animals ([WildPainter]): butterflies over the reeds by day, a roe deer drinking at the far shore at dawn and
 * dusk, a fox at dawn, bats hunting low over the water at dusk and in the night ("bats" brings them, how many).
 *
 * Closer up ([detail] 2 and 3) the same view is drawn finer: blades and plumes on the reeds, veins and a notch on
 * every pad, petals on the lilies, planks, nails and posts on the jetty, the boat's ribs and seats, feathers on the
 * ducks and the heron, the carp's scales.
 */
internal class PondPainter : WildPainter() {
    override val art = "pond"

    /** Over the pond to the north-east. */
    override val skyFacing = 60.0

    // the plan, in metres: the pond an ellipse round (pondX, pondD), the woods behind it
    private val pondX = 0.5f; private val pondD = 22f; private val pondRx = 14f; private val pondRd = 9.5f
    private val wallD = 36f

    /** How far ([x], [d]) is from the pond's middle: 1 at its shore, a little ragged. */
    private fun inPond(x: Float, d: Float): Float {
        val u = (x - pondX) / pondRx; val v = (d - pondD) / pondRd
        return sqrt(u * u + v * v) + (Noise.v2(x * 0.3f, d * 0.45f, 505) - 0.5f) * 0.1f
    }

    private val jettyX = 4.2f; private val jettyNear = 11f; private val jettyFar = 17.4f; private val jettyHalf = 0.62f; private val deckH = 0.42f
    private val boatX = 5.95f; private val boatD = 14.8f
    private val frogPad = floatArrayOf(-1.8f, 14.3f)
    private val frogWater = floatArrayOf(-1.1f, 13.6f)
    private val heronX = 9.4f; private val heronD = 21f

    private val items = ArrayList<Pair<Float, () -> Unit>>()

    /** The water's object id this frame: the reflections, the rings and the weather keep to it. */
    private var waterId = -1

    /** The woods' top row over each column of the picture, for the woods and their reflection. */
    private var wallTop = IntArray(0)
    private val skyLut = IntArray(SKY_STEPS + 1)
    /** The beeches' shade and middle colour this season, for their picture in the water. */
    private var beechD = 0
    private var beechM = 0

    /** Picture pixels per scene canvas pixel: every size and offset in pixels is multiplied by it. */
    private val z: Int get() = detail

    override fun paint() {
        setup()
        val z = z
        for (i in 0..SKY_STEPS) skyLut[i] = skyAt(i / SKY_STEPS.toFloat())
        vistaSky(3, 41, moonX = ox + MOON_X * z, moonY = oy + 10 * z)
        ground { x, d, px, py -> shore(x, d, px, py) }
        woods()
        thing("pond", outline = 0) { waterId = c.penId; water() }

        items.clear()
        // the woods come down to the water at both sides
        for (k in 0 until 10) {
            val side = if (k % 2 == 0) -1f else 1f
            val d = 16f + k * 1.9f + Noise.rnd(k, 511) * 2f
            val x = side * (15.5f + Noise.rnd(k, 512) * 4f + (d - 16f) * 0.25f)
            val spruce = Noise.rnd(k, 513) < 0.6f
            val hgt = if (spruce) 13f + Noise.rnd(k, 514) * 5f else 10f + Noise.rnd(k, 514) * 4f
            items.add(d to { prop(Pal.OUTLINE_TREE) { if (spruce) spruce(x, d, hgt, k + 20) else leafyTree(x, d, hgt, k * 3 + 11) } })
        }
        items.add(heronD to { thing("heron", slop = 2) { heron(heronX, heronD) } })
        items.add(20.8f to { thing("ducks", slop = 2) { ducks() } })
        items.add(17.5f to { thing("lilies", outline = PAD_D) { lilies() } })
        items.add(boatD + 1.6f to { thing("boat") { boat() } })
        items.add(jettyFar to { thing("jetty") { jetty() } })
        items.add(15.4f to { thing("fish", outline = 0, slop = 2) { carp(1.7f, 15.4f) } })
        items.add(14.2f to { thing("frog", slop = 2) { frog(fx("frog") ?: 0f) } })
        items.add(12.6f to { thing("reeds", outline = Pal.OUTLINE_TREE) { reeds(-7f, 13.4f, 2.4f, 1.6f, 36, big = true) } })
        items.add(28f to { prop(Pal.OUTLINE_TREE) { reeds(10.5f, 28f, 3f, 1.2f, 14, big = false) } })
        items.add(26f to { prop(Pal.OUTLINE_TREE) { reeds(-11.5f, 25.5f, 2.2f, 1.4f, 12, big = false) } })
        for (p in peopleAt("jetty")) items.add(16.9f to { person(p, gx(jettyX, 16.9f).toInt(), (gy(16.9f) - deckH * pm(16.9f)).toInt(), flip = true) })
        for (p in peopleAt("reeds")) items.add(11.2f to { groundShadow(-4.6f, 11.2f, 0.5f); person(p, gx(-4.6f, 11.2f).toInt(), gy(11.2f).toInt(), flip = false) })
        for (p in peopleAt("shore")) items.add(10.6f to { groundShadow(1.8f, 10.6f, 0.5f); person(p, gx(1.8f, 10.6f).toInt(), gy(10.6f).toInt(), flip = false) })
        wildlife()
        items.sortByDescending { it.first }
        for ((_, draw) in items) draw()

        nearEdge()
        foregroundGrass(0, (w * 0.3f).toInt(), 31)
        foregroundGrass((w * 0.72f).toInt(), w, 32)
        if (!env.snow && gy(9.6f) < h) prop(0) { ferns() }
        prop(Pal.OUTLINE) { jumps(fxOn("fish")) }
        prop(0) { flight(fxOn("ducks")) }
        thing("dragonfly", outline = FLY_OUTLINE, slop = 3) { dragonfly() }
        val nBats = batCount(fx("bats"), out("bat"))
        if (nBats > 0) creature("bat", slop = 3) { batsOver(ox + 40f * z, hz - 34f * z, ox + 200f * z, gy(26f), nBats, 447, 1.1f) }
        fireflies(0, hz + 30 * z, w, h, 18, 37)
        s.fx { s.effects.leaves(env.season, env.month) }
        if (!env.snow) s.fx { dimples() }
    }

    /** The pond's wild animals of the frame ([out]): the deer drinking and the fox at the far shore, butterflies by the reeds. */
    private fun wildlife() {
        val z = z
        if (out("deer")) {
            val u = spot("deer")
            val x = if (u < 0.5f) -7.5f + u * 8f else 2.5f + (u - 0.5f) * 8f
            val d = 32.2f
            items.add(d to { creature("deer") { roe(gx(x, d), gy(d), 44f / d, left = u > 0.5f, graze = true, buck = u > 0.75f) } })
        }
        if (out("fox")) {
            val u = spot("fox")
            val x = -12f + u * 3f; val d = 27f + u * 2f
            items.add(d to { creature("fox") { fox(gx(x, d), gy(d), 40f / d, left = false, sit = true) } })
        }
        if (out("butterfly")) for ((j, at) in listOf(-6.2f to 12.8f, 2.6f to 11.2f).withIndex()) {
            val (bx, bd) = at
            items.add(bd - 0.5f to {
                creature("butterfly", slop = 3) {
                    butterfly(gx(bx, bd) + sin(t * 0.8 + j * 2).toFloat() * 8f * z, gy(bd) - (22f + 5f * sin(t * 1.2 + j).toFloat()) * z, 1.6f, j + 1 + (spot("butterfly") * 3).toInt())
                }
            })
        }
    }

    // ------------------------------------------------------------------ the woods round the pond

    /**
     * The woods behind the pond: a dark wall of spruce spires and beech crowns standing at the far shore, lit on the sun's
     * side, snow in their branches in winter. Their tops are kept per column ([wallTop]) for their picture in the water.
     */
    private fun woods() {
        val z = z
        val season = env.season
        val base = gy(wallD).toInt() + z
        if (wallTop.size != w) wallTop = IntArray(w)
        for (px in 0 until w) wallTop[px] = topOf((px + 0.5f) / z, base)
        val sunLeft = (sunAt()?.first ?: vx) < vx
        val sD = env.lit(Col.mix(SPRUCE_D, env.skyHorizon, 0.1f)); val sM = env.lit(Col.mix(SPRUCE_M, env.skyHorizon, 0.08f))
        val sL = env.lit(Col.mix(Col.hex(0x38684A), env.skyHorizon, 0.06f))
        val beeches = beech()
        beechD = beeches.first; beechM = beeches.second
        val (bD, bM, bL) = beeches.let { (a, b, c2) -> Triple(env.lit(a), env.lit(b), env.lit(c2)) }
        c.penEmissive = true
        for (px in max(0, c.left) until min(w, c.right)) {
            val top = wallTop[px]
            val xb = (px + 0.5f) / z
            val spruce = Noise.rnd(floor(xb / 8f).toInt(), 503) < 0.62f
            val slope = wallTop[min(w - 1, px + z)] - wallTop[max(0, px - z)]
            val lit = if (sunLeft) slope > 0 else slope < 0
            for (py in max(max(0, top), c.top) until min(min(h, base + z), c.bottom)) {
                val down = (py - top).toFloat() / max(1f, (base - top).toFloat())
                val n = Noise.v2(px * 0.45f / z, py * 0.55f / z, 504)
                val col = if (spruce) {
                    // tiers of branches: a shade band under each
                    val band = ((py - top) / (4f * z) + Noise.rnd(floor(xb / 8f).toInt(), 506)) % 1f
                    if (py - top < z && lit) sL else if (down > 0.55f + (Dither.at(px, py) - 0.5f) * 0.4f || band > 0.75f || n < 0.2f) sD else sM
                } else {
                    if (py - top < 2 * z && lit) bL else if (down > 0.5f + (Dither.at(px, py) - 0.5f) * 0.4f || n < 0.32f) bD else if (n > 0.66f) bL else bM
                }
                val snowy = season == Season.WINTER && Noise.v2(px * 0.5f / z, py * 0.9f / z, 99) > 0.55f && Dither.at(px, py) < 0.7f
                c.set(px, py, if (snowy) env.lit(if (lit) Pal.SNOW_L else Pal.SNOW_M) else col)
            }
        }
        c.penEmissive = false
    }

    /** The woods' top row over scene column [xb]: a spire or a crown every 8 columns, a little lower in the middle. */
    private fun topOf(xb: Float, base: Int): Int {
        val cell = floor(xb / 8f).toInt(); val u = xb / 8f - cell
        val spruce = Noise.rnd(cell, 503) < 0.62f
        val mid = (abs(xb - vx / detail) / 110f).coerceIn(0.7f, 1f)
        val tall = 22f + 16f * Noise.v1(xb * 0.035f, 501) + 12f * Noise.rnd(cell, 502)
        val crown = if (spruce) (1f - abs(u - 0.5f) * 2f) * (9f + 6f * Noise.rnd(cell, 507)) else sqrt(max(0f, 1f - (u * 2f - 1f) * (u * 2f - 1f))) * 7f
        return base - ((tall * mid + crown) * detail).toInt()
    }

    /** The beeches' colours by the season. */
    private fun beech(): Triple<Int, Int, Int> = when (env.season) {
        Season.AUTUMN -> Triple(Col.hex(0x6A3A1A), Col.hex(0xA8602A), Col.hex(0xD8983A))
        Season.WINTER -> Triple(Col.hex(0x5E5850), Col.hex(0x7E776E), Col.hex(0x9C958A))
        Season.SPRING -> Triple(Col.hex(0x3A6A2E), Col.hex(0x5A9642), Col.hex(0x88BE58))
        else -> Triple(Col.hex(0x2A542A), Col.hex(0x3E763A), Col.hex(0x60984A))
    }

    // ------------------------------------------------------------------ the shore

    private fun shore(x: Float, d: Float, px: Int, py: Int): Int {
        val z = z
        if (d > wallD - 1f) return Col.scale(if (env.snow) Pal.SNOW_D else env.forestFloor[2], 0.7f)
        val q = inPond(x, d)
        if (env.snow) {
            val n = Noise.v2(x * 0.6f, d * 0.6f, 41) + (Dither.at(px, py) - 0.5f) * 0.2f
            return hazy(if (q < 1.06f) Pal.SNOW_D else if (n < 0.25f) Pal.SNOW_M else Pal.SNOW_L, d)
        }
        val g = env.grass
        val n = Noise.v2(x * 0.7f, d * 0.7f, 3) + (Dither.at(px, py) - 0.5f) * 0.25f
        var col = if (n > 0.62f) g[0] else if (n > 0.32f) g[1] else g[2]
        // the forest floor under the trees at the sides and the far shore
        val under = ((abs(x) - 13f) / 3f).coerceIn(0f, 1f) + ((d - 28f) / 5f).coerceIn(0f, 1f)
        if (under > 0.5f + (Dither.at(px, py) - 0.5f) * 0.6f) col = env.forestFloor[if (n > 0.5f) 0 else 1]
        if (z == 1) {
            val r = Noise.rnd(px, py, 5)
            if (d < 20f && r < 0.05f) col = g[2]
            if (d < 20f && r > 0.985f) col = g[3]
        } else if (d < 20f) {
            val len = (pm(d) * 0.075f).toInt().coerceIn(1, 2 * z)
            for (j in 0 until len) {
                val r = Noise.rnd(px, py + j, 5)
                if (r < 0.07f / len) { col = g[2]; break }
                if (r > 1f - 0.022f / len) { col = g[3]; break }
            }
        }
        // flowers in the meadow by the water, spring to summer: a dot, or petals round a heart closer up
        if (env.month in 4..8 && d < 14f && q > 1.25f) {
            val cx = px / z; val cy = py / z
            if (Noise.rnd(cx, cy, 9) < 0.012f) {
                val petal = Pal.PAINTED[(cx * 7 + cy) % 6]
                val lx = px - cx * z; val ly = py - cy * z
                col = when (z) {
                    1 -> petal
                    2 -> if (lx == 0 && ly == 0) petal else if (lx == 0) g[2] else col
                    else -> if (lx == 1 && ly == 1) Pal.GOLD else if (lx == 1 || ly == 1) petal else col
                }
            }
        }
        // the muddy margin at the water, damp grass above it
        if (q < 1.05f) col = if (Noise.v2(x * 6f, d * 6f, 521) > 0.6f) Col.hex(0x6E6044) else Col.hex(0x5A4E36)
        else if (q < 1.12f) col = Col.scale(col, 0.82f)
        return hazy(col, d)
    }

    /**
     * What lies close to us below the stage (a tall view shows it): an old mossy log on the right, stones, tufts of
     * rushes, and marsh marigolds in spring.
     */
    private fun nearEdge() {
        val z = z
        if (gy(9.6f) >= h) return
        prop(Pal.OUTLINE_TREE) {
            val d = 8.3f; val p = pm(d); val y = gy(d)
            val x0 = gx(3.4f, d); val x1 = gx(5.9f, d); val r = 0.2f * p
            val moss = if (env.snow) Pal.SNOW_L else Col.hex(0x5C9A48)
            for (py in max((y - 2 * r).toInt(), c.top) until min(y.toInt(), c.bottom)) {
                val v = (py + 0.5f - (y - 2 * r)) / (2 * r)
                for (px in max(x0.toInt(), c.left) until min(x1.toInt(), c.right)) {
                    var col = if (v < 0.22f) Pal.LOG_L else if (v > 0.78f) Pal.LOG_D else Pal.LOG_M
                    if (abs(Noise.v2(px * 0.04f / z, py * 0.5f / z, 651) - 0.5f) < 0.04f) col = Pal.LOG_D // fissures along it
                    if (v < 0.3f && Noise.v2(px * 0.2f / z, py * 0.3f / z, 652) > 0.62f) col = moss
                    c.set(px, py, col)
                }
            }
            c.fillEllipse(x0, y - r, r * 0.7f, r, Col.hex(0xC8A070)); c.fillEllipse(x0, y - r, r * 0.4f, r * 0.55f, Col.hex(0xB08858)); c.fillEllipse(x0, y - r, r * 0.12f, r * 0.16f, Col.hex(0x8A6038))
        }
        val g = env.grass
        for (k in 0 until 12) {
            val d = 6.4f + Noise.rnd(k, 653) * 3f; val x = (Noise.rnd(k, 654) - 0.5f) * 10f
            if (x > 3f && x < 6.5f && d > 7.6f && d < 9f) continue
            val p = pm(d); val bx = gx(x, d); val by = gy(d)
            if (k % 4 == 0) prop {
                val r = 0.2f * p
                c.fillEllipse(bx, by - r * 0.5f, r, r * 0.6f, Pal.STONE_D)
                c.fillEllipse(bx - r * 0.15f, by - r * 0.65f, r * 0.8f, r * 0.45f, if (env.snow) Pal.SNOW_L else Pal.STONE_M)
                c.fillEllipse(bx - r * 0.35f, by - r * 0.8f, r * 0.3f, r * 0.2f, if (env.snow) Col.hex(0xFFFFFF) else Pal.STONE_L)
            } else prop(0) {
                // a tuft of rushes, dry and snowy in winter; in spring a marsh marigold in it
                val n = 5 + (k % 3) * 2
                for (b in 0 until n) {
                    val u = b / (n - 1f) - 0.5f
                    val hgt = (0.5f + Noise.rnd(k, b, 655) * 0.45f) * p * (1f - abs(u) * 0.6f)
                    val lean = u * 0.45f * p + sin(t * 1.4 + k + b * 0.3).toFloat() * 0.03f * p * gust
                    val base = bx + u * 0.18f * p
                    c.line(base.toInt(), by.toInt(), (base + lean).toInt(), (by - hgt).toInt(), if (env.snow) Col.hex(0xB8A070) else if (b % 2 == 0) Col.scale(g[2], 0.78f) else Col.scale(g[1], 0.9f))
                    if (z > 1) c.line(base.toInt() + 1, by.toInt(), (base + lean).toInt() + 1, (by - hgt * 0.8f).toInt(), if (env.snow) Col.hex(0x9A8458) else Col.scale(g[2], 0.7f))
                }
                if (env.month in 3..5 && k % 2 == 0) {
                    val r = max(1.5f, 0.06f * p)
                    c.fillEllipse(bx + 0.1f * p, by - 0.25f * p, r, r * 0.8f, Pal.GOLD)
                    c.fillEllipse(bx + 0.1f * p, by - 0.25f * p, r * 0.4f, r * 0.35f, Col.hex(0xC87A1A))
                }
            }
        }
    }

    /** Fern fronds in the bottom corners: our own spot at the pond's edge. */
    private fun ferns() {
        val z = z
        val col = if (env.season == Season.AUTUMN) Col.hex(0xA8702A) else Col.scale(env.grass[2], 0.7f)
        val light = if (env.season == Season.AUTUMN) Col.hex(0xC89040) else Col.scale(env.grass[1], 0.82f)
        for ((i, root) in listOf(-4 * z to h + 2 * z, 12 * z to h + 5 * z, w + 4 * z to h + 2 * z, w - 14 * z to h + 5 * z).withIndex()) {
            val (rx, ry) = root
            val dir = if (rx < w / 2) 1f else -1f
            val len = (30f + (i % 2) * 10f) * z
            val sway = sin(t * 1.2 + i).toFloat() * 1.2f * z * gust
            for (s2 in 0 until len.toInt()) {
                val f = s2 / len
                val x = rx + dir * f * len * 0.8f + sway * f
                val y = ry - f * len * 0.9f + f * f * len * 0.5f
                c.set(x.toInt(), y.toInt(), col)
                if (s2 % (z + 2) == 0) {
                    val leaf = ((1f - f) * 6f + 1f) * z
                    c.line(x.toInt(), y.toInt(), (x - dir * leaf * 0.4f).toInt(), (y - leaf).toInt(), light)
                    c.line(x.toInt(), y.toInt(), (x + dir * leaf).toInt(), (y + leaf * 0.2f).toInt(), col)
                }
            }
        }
    }

    // ------------------------------------------------------------------ the water

    /** The pond: the woods and the sky upside down in it, dark and clear near us, breezes ruffling it; ice in winter. */
    private fun water() {
        val z = z
        val top = gy(pondD + pondRd + 1f).toInt(); val bottom = gy(pondD - pondRd - 1f).toInt() + z
        val mirror = 2 * (gy(wallD).toInt() + z)
        val winter = env.season == Season.WINTER
        for (py in max(max(hz + 1, top), c.top)..min(min(h - 1, bottom), c.bottom - 1)) {
            val d = depthAt(py)
            val span = pondRx * 1.1f
            for (px in max(max(0, gx(pondX - span, d).toInt()), c.left)..min(min(w - 1, gx(pondX + span, d).toInt()), c.right - 1)) {
                val x = sideAt(px, d)
                val q = inPond(x, d)
                if (q > 1f) continue
                c.set(px, py, if (winter) ice(px, py, x, d, q) else waterAt(px, py, x, d, q, mirror))
            }
        }
    }

    private fun waterAt(px: Int, py: Int, x: Float, d: Float, q: Float, mirror: Int): Int {
        val z = z
        // a breeze now and then ruffles a patch: there the picture in the water breaks up
        val breeze = Noise.v2(x * 0.35f + t.toFloat() * 0.05f, d * 0.5f, 531)
        val wobble = (sin(py * 0.8f / z + t.toFloat() * 1.6f) * (0.6f + breeze) * z).toInt()
        val sx = (px + wobble).coerceIn(0, w - 1)
        val sr = mirror - py
        val refl = if (sr < wallTop[sx]) {
            val tt = (sr / (hz + 2f * z)).coerceIn(0f, 1f)
            skyTone(tt, px, py)
        } else {
            val n = Noise.v2(sx * 0.45f / z, sr * 0.55f / z, 504)
            if (Noise.rnd(floor((sx + 0.5f) / z / 8f).toInt(), 503) < 0.62f) (if (n < 0.3f) SPRUCE_D else SPRUCE_M) else (if (n < 0.4f) beechD else beechM)
        }
        // near us the water is dark and clear, farther off it mirrors more
        val far = ((pondD + pondRd - d) / (2f * pondRd)).coerceIn(0f, 1f)
        val mirrorShare = 0.78f - 0.38f * far
        var col = Col.mix(POND_DEEP, refl, mirrorShare)
        // the shallows at the shore, brown-green
        if (q > 0.9f) col = Col.mix(col, Col.hex(0x4A5A3A), (q - 0.9f) * 4f)
        // ripples: fine light streaks where the breeze touches
        val band = F * EYE / (d * d) * 0.5f
        if (band > 1.2f) {
            val ph = sin((d * 9f + sin(x * 0.9f + t.toFloat() * 0.7f) * 1.5f) + t.toFloat() * 1.1f)
            if (ph > 0.9f - breeze * 0.25f && breeze > 0.45f) {
                val edge = z == 1 || sin((depthAt(py - 1) * 9f + sin(x * 0.9f + t.toFloat() * 0.7f) * 1.5f) + t.toFloat() * 1.1f) <= 0.9f - breeze * 0.25f
                if (edge) col = Col.mix(col, skyTone(0.3f, px, py), 0.45f)
            }
        }
        // the moon lying on the water under its place in the sky, in short flickering dashes
        if (env.dark > 0.3f && frame.sky.gloom < 0.55f && env.moonShows) {
            val mx = ox + MOON_X * z
            val spread = (1.5f + (py - hz) / z * 0.05f) * z
            if (abs(px - mx) < spread && Noise.rnd(Math.floorDiv(px, 2 * z), py / z, (t * 2).toInt()) < 0.6f * (1f - abs(px - mx) / spread)) col = Col.mix(col, Col.hex(0xF4EED0), 0.6f * env.dark)
        }
        return hazy(col, d)
    }

    /** The sky at [tt] (0 the zenith, 1 the horizon), dithered between its steps like the sky above. */
    private fun skyTone(tt: Float, px: Int, py: Int): Int {
        val q = floor(tt * SKY_STEPS + Dither.at(px, py) - 0.5f).toInt().coerceIn(0, SKY_STEPS)
        return skyLut[q]
    }

    /** The frozen pond: grey-blue ice with cracks and drifts of snow; a hole of open water where the ducks swim. */
    private fun ice(px: Int, py: Int, x: Float, d: Float, q: Float): Int {
        val z = z
        val hx = (x - HOLE_X) / 2.4f; val hd = (d - HOLE_D) / 1.6f
        val hole = hx * hx + hd * hd + (Noise.v2(x * 2f, d * 2f, 541) - 0.5f) * 0.3f
        if (hole < 1f) return hazy(if (hole > 0.82f) Col.mix(Pal.ICE_M, Pal.WATER_M, 0.3f) else Col.mix(POND_DEEP, skyTone(0.6f, px, py), 0.3f), d)
        val crack = z > 1 && abs(Noise.v2(x * 0.9f, d * 2.6f, 542) - 0.5f) < 0.02f
        val drift = Noise.v2(x * 0.5f, d * 0.8f, 543) + (Dither.at(px, py) - 0.5f) * 0.15f
        val sheen = Noise.v2(x * 0.25f, d * 1.8f, 545)
        val col = when {
            crack -> Col.mix(Pal.ICE_M, Pal.WATER_M, 0.35f)
            q > 0.95f || drift > 0.76f -> Pal.SNOW_L
            drift > 0.66f -> Pal.SNOW_M
            sheen > 0.7f && Dither.at(px, py) < 0.6f -> Pal.ICE_L
            else -> Col.mix(Col.mix(Pal.ICE_M, Col.hex(0x9AB8CC), 0.4f), skyTone(0.5f, px, py), 0.2f)
        }
        return hazy(col, d)
    }

    /** A ring on the water, [r] m round ([x], [d]), blended over the water only. */
    private fun ring(x: Float, d: Float, r: Float, a: Float) {
        if (a <= 0.02f || r <= 0f) return
        val rx = r * pm(d); val ry = max(0.6f, r * F * EYE / (d * d))
        val cx = gx(x, d); val cy = gy(d)
        val steps = max(12, (rx * 4f).toInt())
        val col = Col.mix(skyTone(0.5f, 0, 0), Col.hex(0xFFFFFF), 0.5f)
        for (k in 0 until steps) {
            val ang = k * 2f * PI.toFloat() / steps
            val px = floor(cx + cos(ang) * rx).toInt(); val py = floor(cy + sin(ang) * ry).toInt()
            if (!c.inside(px, py) || c.ids[c.index(px, py)] != waterId) continue
            c.blend(px, py, col, a)
        }
    }

    /** Now and then a ring where an insect touches the water or a fish takes one. */
    private fun dimples() {
        for (j in 0 until 5) {
            val cyc = t * 0.35 + Noise.rnd(j, 551)
            val k = floor(cyc).toInt(); val ph = (cyc - k).toFloat()
            if (ph > 0.6f) continue
            val d = pondD - pondRd * 0.8f + Noise.rnd(j, k, 552) * pondRd * 1.4f
            val x = pondX + (Noise.rnd(j, k, 553) - 0.5f) * pondRx * 1.2f
            if (inPond(x, d) > 0.85f) continue
            ring(x, d, 0.08f + ph * 0.5f, 0.45f * (1f - ph / 0.6f))
        }
    }

    // ------------------------------------------------------------------ on the water

    /** Water lilies: round pads with a notch, a few white flowers in summer; brown and few in autumn, frozen in in winter. */
    private fun lilies() {
        val z = z
        val season = env.season
        if (season == Season.WINTER) {
            for (k in 0 until 5) pad(-3.6f + (Noise.rnd(k, 561) - 0.5f) * 4f, 17.2f + (Noise.rnd(k, 562) - 0.5f) * 3f, 0.3f, k, Col.hex(0x7A6A48), Col.hex(0x5A4E36))
            pad(frogPad[0], frogPad[1], 0.42f, 99, Col.hex(0x7A6A48), Col.hex(0x5A4E36))
            return
        }
        val (light, dark) = if (season == Season.AUTUMN) Col.hex(0x9A8A3A) to Col.hex(0x6E5E2A) else Col.hex(0x5E9A3E) to Col.hex(0x3A6E2A)
        for (k in 0 until 14) {
            val x = -3.6f + (Noise.rnd(k, 561) - 0.5f) * 5f
            val d = 17.3f + (Noise.rnd(k, 562) - 0.5f) * 3.4f
            pad(x, d, 0.24f + Noise.rnd(k, 563) * 0.16f, k, if (k % 4 == 0) dark else light, dark)
        }
        pad(frogPad[0], frogPad[1], 0.42f, 99, light, dark)
        pad(frogPad[0] + 0.7f, frogPad[1] + 0.5f, 0.26f, 98, light, dark)
        if (env.month in 6..8) for (k in 0 until 4) {
            val x = -3.6f + (Noise.rnd(k, 564) - 0.5f) * 4.4f; val d = 17f + (Noise.rnd(k, 565) - 0.5f) * 3f
            lily(x, d)
        }
    }

    /** One pad, [r] m across, lying flat: a notch toward us, closer up the veins from its middle. */
    private fun pad(x: Float, d: Float, r: Float, seed: Int, col: Int, dark: Int) {
        val z = z
        val cx = gx(x, d); val cy = gy(d)
        val rx = r * pm(d); val ry = max(1f, r * F * EYE / (d * d))
        val notch = Noise.rnd(seed, 566) * 2f * PI.toFloat()
        for (y in max(floor(cy - ry).toInt(), c.top)..min(floor(cy + ry).toInt(), c.bottom - 1)) for (xx in max(floor(cx - rx).toInt(), c.left)..min(floor(cx + rx).toInt(), c.right - 1)) {
            val dx = (xx + 0.5f - cx) / rx; val dy = (y + 0.5f - cy) / ry
            val q = dx * dx + dy * dy
            if (q > 1f) continue
            val ang = kotlin.math.atan2(dy, dx)
            val off = abs(((ang - notch + 3f * PI.toFloat()) % (2f * PI.toFloat())) - PI.toFloat())
            if (off < 0.3f && q > 0.05f) continue // the notch
            val rim = q > 0.72f && dy > 0f
            val vein = z > 1 && abs(sin(ang * 5f)) < 0.12f && q > 0.1f
            c.set(xx, y, if (rim || vein) dark else if (dy < -0.4f && dx < 0f) Col.mix(col, Col.hex(0xFFFFFF), 0.18f) else col)
        }
    }

    /** A white water lily: pointed petals round a yellow heart, lifted a little off the water. */
    private fun lily(x: Float, d: Float) {
        val z = z
        val p = pm(d)
        val cx = gx(x, d); val cy = gy(d) - 0.05f * p
        val r = max(1.5f, 0.1f * p)
        for (k in 0 until 8) {
            val a = k * PI.toFloat() / 4f
            c.fillEllipse(cx + cos(a) * r * 0.9f, cy + sin(a) * r * 0.35f, r * 0.55f, r * 0.4f, if (k < 4) Col.hex(0xF4F2EA) else Col.hex(0xFFFFFF))
        }
        c.fillEllipse(cx, cy - r * 0.35f, r * 0.55f, r * 0.45f, Col.hex(0xFFFFFF))
        c.fillEllipse(cx, cy - r * 0.2f, max(0.8f, r * 0.3f), max(0.6f, r * 0.22f), Pal.GOLD)
        if (z > 1) c.set(cx.toInt(), (cy - r * 0.5f).toInt(), Col.hex(0xF8D8E0))
    }

    /**
     * The frog: on its pad, green with dark spots and a pale throat; with the "frog" effect ([jump] 0..1) it crouches,
     * leaps into the water and waits there with just its eyes out.
     */
    private fun frog(jump: Float) {
        val z = z
        val green = if (env.season == Season.AUTUMN || env.season == Season.WINTER) Col.hex(0x6A8A3A) else Col.hex(0x5AAA3A)
        val dark = Col.hex(0x2E5A22); val pale = Col.hex(0xE8E4B0)
        val sit = jump < 0.12f
        val u = ((jump - 0.12f) / 0.2f).coerceIn(0f, 1f)
        val x = frogPad[0] + (frogWater[0] - frogPad[0]) * u; val d = frogPad[1] + (frogWater[1] - frogPad[1]) * u
        val p = pm(d)
        if (jump >= 0.32f) {
            // in the water: two eyes and the top of its head; rings round it
            val bx = gx(x, d); val by = gy(d)
            c.fillEllipse(bx, by, max(2f, 0.08f * p), max(1f, 0.03f * p), green)
            for (s in floatArrayOf(-1f, 1f)) {
                val ex = bx + s * max(1.5f, 0.045f * p)
                c.fillEllipse(ex, by - max(1f, 0.025f * p), max(1f, 0.025f * p), max(1f, 0.025f * p), Col.mix(green, Col.hex(0xFFFFFF), 0.2f))
                c.set(ex.toInt(), (by - max(1f, 0.03f * p)).toInt(), Pal.OUTLINE)
            }
            val a = ((jump - 0.32f) / 0.5f).coerceIn(0f, 1f)
            s.fx {
                ring(x, d, 0.12f + a * 0.5f, 0.6f * (1f - a) + 0.12f)
                ring(x, d, 0.06f + ((t * 0.6) % 1.0).toFloat() * 0.25f, 0.25f)
            }
            return
        }
        val lift = if (sit) 0.04f else 0.04f + 0.3f * sin(u * PI.toFloat())
        val squash = if (sit) 1f - jump * 1.5f else 1.1f
        val bx = gx(x, d); val by = gy(d) - lift * p
        val bw = 0.1f * p; val bh = 0.07f * p * squash
        // the hind legs folded at its sides (stretched out behind in the leap)
        if (sit) {
            c.fillEllipse(bx - bw * 0.9f, by - bh * 0.2f, bw * 0.45f, bh * 0.45f, green)
            c.fillEllipse(bx + bw * 0.9f, by - bh * 0.2f, bw * 0.45f, bh * 0.45f, green)
        } else {
            c.fillEllipse(bx - bw * 1.1f, by + bh * 0.6f, bw * 0.6f, bh * 0.3f, green)
            c.fillEllipse(bx + bw * 1.1f, by + bh * 0.6f, bw * 0.6f, bh * 0.3f, green)
        }
        c.fillEllipse(bx, by - bh * 0.6f, max(1.5f, bw), max(1.2f, bh), green)
        c.fillEllipse(bx, by - bh * 0.25f, max(1f, bw * 0.6f), max(0.8f, bh * 0.4f), pale)
        // the eyes on top, a dark spot or two
        for (s in floatArrayOf(-1f, 1f)) {
            val ex = bx + s * bw * 0.5f; val ey = by - bh * 1.4f
            c.fillEllipse(ex, ey, max(0.8f, bw * 0.28f), max(0.8f, bw * 0.28f), green)
            c.set(ex.toInt(), ey.toInt(), Pal.OUTLINE)
            if (z > 1) c.set(ex.toInt() - 1, ey.toInt() - 1, Col.hex(0xFFFFFF))
        }
        if (z > 1) { c.fillEllipse(bx - bw * 0.3f, by - bh * 0.9f, bw * 0.15f, bh * 0.15f, dark); c.fillEllipse(bx + bw * 0.35f, by - bh * 0.6f, bw * 0.12f, bh * 0.12f, dark) }
        // its throat puffing now and then
        if (sit && sin(t * 2.3) > 0.7) c.fillEllipse(bx, by - bh * 0.1f, max(1f, bw * 0.35f), max(0.8f, bh * 0.35f), pale)
    }

    /** A carp just under the surface, its bronze back showing, its fins stirring the water. */
    private fun carp(x: Float, d: Float) {
        val z = z
        val drift = sin(t * 0.3).toFloat() * 0.25f
        val ax = gx(x - 0.3f + drift, d + 0.05f); val ay = gy(d + 0.05f)
        val bx = gx(x + 0.3f + drift, d - 0.05f); val by = gy(d - 0.05f)
        val back = Col.mix(Col.hex(0x9A7228), POND_DEEP, 0.18f); val side = Col.mix(Col.hex(0xE0AE4E), POND_DEEP, 0.22f)
        val wide = max(1.3f, 0.1f * pm(d))
        for (i in 0..12) {
            val f = i / 12f
            val px = ax + (bx - ax) * f; val py = ay + (by - ay) * f
            val r = wide * (if (f < 0.35f) 0.6f + f * 1.2f else 1.02f - (f - 0.35f) * 1.2f).coerceAtLeast(0.3f)
            c.fillEllipse(px, py, r + 0.2f, r * 0.6f + 0.2f, side)
            c.fillEllipse(px, py - r * 0.2f, r * 0.7f, max(0.5f, r * 0.3f), back)
        }
        // the tail, and closer up the scales
        val tw = sin(t * 3.0).toFloat() * wide * 0.4f
        c.fillEllipse(bx + (bx - ax) * 0.1f, by + tw * 0.3f, wide * 0.8f, wide * 0.5f, back)
        if (z > 1) for (k in 0 until 10) {
            val f = 0.15f + Noise.rnd(k, 571) * 0.6f
            c.set((ax + (bx - ax) * f).toInt(), (ay + (by - ay) * f + (Noise.rnd(k, 572) - 0.5f) * wide).toInt(), Col.mix(side, Col.hex(0xFFE8A0), 0.4f))
        }
        // now and then its back breaks the surface
        val ph = ((t * 0.25) % 1.0).toFloat()
        if (ph < 0.3f && !env.snow) s.fx { ring(x + drift, d, 0.15f + ph * 1.2f, 0.4f * (1f - ph / 0.3f)) }
    }

    /** A fish jumping (the "fish" effect): out of the pond again and again, rings where it falls back. */
    private fun jumps(level: Float) {
        if (level <= 0.02f) return
        val z = z
        val period = 2.6 - 1.4 * level
        val n = floor(t / period).toInt()
        for (j in n - 1..n) {
            val t0 = j * period + Noise.rnd(j, 581) * 0.3
            val age = (t - t0).toFloat()
            if (age < 0f) continue
            val d0 = 16.5f + Noise.rnd(j, 583) * 7f
            val x0 = pondX + (Noise.rnd(j, 582) - 0.5f) * 8f
            val leap = 0.65f
            if (age < leap) {
                val u = age / leap
                val x = x0 + 0.4f * u; val d = d0; val hgt = 0.7f * sin(u * PI.toFloat())
                val p = pm(d)
                val bx = gx(x, d); val by = gy(d) - hgt * p
                val ang = (0.5f - u) * 1.8f
                val len = 0.55f * p
                val dx = cos(ang) * len / 2f; val dy = -sin(ang) * len / 2f
                val w2 = max(1.2f, 0.1f * p)
                for (i in 0..10) {
                    val f = i / 10f - 0.5f
                    val r = w2 * (1f - abs(f + 0.1f) * 1.3f).coerceAtLeast(0.4f)
                    c.fillEllipse(bx + dx * f * 2f, by + dy * f * 2f, r + 0.3f, r + 0.3f, Col.hex(0xB08A3A))
                    c.fillEllipse(bx + dx * f * 2f, by + dy * f * 2f + r * 0.4f, r * 0.8f, max(0.5f, r * 0.4f), Col.hex(0xE8C870))
                }
                c.fillEllipse(bx - dx * 1.12f, by - dy * 1.12f, w2 * 0.7f, w2 * 1.3f, Col.hex(0xB08A3A))
                for (k in 0 until 5) c.fillRect((bx - dx * (1.4f + k * 0.35f)).toInt(), (by - dy * (1.4f + k * 0.35f) + (k * k) * z * 0.5f).toInt(), z, z, Col.hex(0xE8F0F8))
                if (u < 0.25f) s.fx { ring(x0, d0, 0.1f + u, 0.5f) }
            } else {
                val a = age - leap
                val lx = x0 + 0.4f
                if (a < 0.3f) {
                    val p = pm(d0)
                    for (k in 0 until 8) {
                        val ang = k * 0.8f
                        val sx = gx(lx, d0) + cos(ang) * (0.1f + a) * p
                        val sy = gy(d0) - sin(a / 0.3f * PI.toFloat()) * 0.22f * p * (0.6f + Noise.rnd(k, 584) * 0.6f)
                        c.fillRect(sx.toInt(), sy.toInt(), z, z, Col.hex(0xF2F6FA))
                    }
                }
                if (a < 1.5f) s.fx {
                    ring(lx, d0, 0.12f + a * 0.5f, 0.7f * (1f - a / 1.5f))
                    if (a > 0.25f) ring(lx, d0, 0.06f + (a - 0.25f) * 0.4f, 0.5f * (1f - a / 1.5f))
                }
            }
        }
    }

    /**
     * A duck leading her ducklings across the pond, back and forth, a wake behind them; in winter they keep to the hole
     * in the ice. Drawn as small motifs, finer closer up.
     */
    private fun ducks() {
        val z = z
        val winter = env.season == Season.WINTER
        val sweep = if (winter) 1.2f else 3.2f
        val ph = t * 0.09
        val u = sin(ph).toFloat()
        val right = cos(ph) > 0
        val baseX = (if (winter) HOLE_X else 1.6f) + u * sweep
        val baseD = if (winter) HOLE_D else 20.8f
        for (k in 0..4) {
            val mother = k == 0
            val lag = k * 0.55f + (if (mother) 0f else 0.3f)
            val x = baseX - (if (right) lag else -lag)
            val d = baseD + (if (k % 2 == 0) 0f else 0.15f)
            val bx = gx(x, d).toInt(); val by = (gy(d) + sin(t * 2.0 + k).toFloat() * 0.3f).toInt()
            if (!winter) s.fx { wake(x, d, if (right) -1f else 1f, if (mother) 0.5f else 0.25f) }
            sprite(bx, by) { if (mother) duck(bx, by, right) else duckling(bx, by, right) }
        }
    }

    /** The mallard duck: a brown speckled body, a dark tail, the orange bill; closer up the blue wing patch. */
    private fun duck(bx: Int, by: Int, right: Boolean) {
        val s = if (right) 1 else -1
        val body = Col.hex(0x8A6A48); val bodyL = Col.hex(0xB08A60); val dark = Col.hex(0x4A3A2A)
        for (i in -3..3) c.set(bx + i, by, if (i % 2 == 0) body else bodyL)
        for (i in -3..2) c.set(bx + i * s, by - 1, if (i == -3) dark else bodyL)
        c.set(bx - 4 * s, by - 1, dark); c.set(bx - 3 * s, by - 2, dark)
        c.set(bx + 1 * s, by - 1, Col.hex(0x3A5AA8)) // the wing's blue patch
        // the head on its neck, the bill
        c.set(bx + 2 * s, by - 2, body); c.set(bx + 3 * s, by - 3, body); c.set(bx + 3 * s, by - 2, body); c.set(bx + 2 * s, by - 3, body)
        c.set(bx + 4 * s, by - 2, Col.hex(0xE89A2A))
        c.set(bx + 3 * s, by - 3, Pal.OUTLINE)
    }

    private fun duckling(bx: Int, by: Int, right: Boolean) {
        val s = if (right) 1 else -1
        val fluff = Col.hex(0xC8A84A); val fluffD = Col.hex(0x8A7038)
        c.set(bx - s, by, fluffD); c.set(bx, by, fluff); c.set(bx + s, by, fluff)
        c.set(bx, by - 1, fluffD); c.set(bx + s, by - 1, fluff)
        c.set(bx + 2 * s, by - 1, Col.hex(0x6A5A3A))
    }

    /** The V of ripples behind something swimming [dir] (−1 left, 1 right). */
    private fun wake(x: Float, d: Float, dir: Float, a: Float) {
        val p = pm(d); val cx = gx(x, d); val cy = gy(d)
        val col = Col.mix(skyTone(0.4f, 0, 0), Col.hex(0xFFFFFF), 0.5f)
        val n = (1.2f * p).toInt()
        for (i in 1..n) {
            val sx = (cx + dir * i).toInt()
            val spread = i * 0.25f
            for (s in floatArrayOf(-1f, 1f)) {
                val py = (cy + s * spread * 0.4f).toInt()
                if (c.inside(sx, py) && c.ids[c.index(sx, py)] == waterId && (i / max(1, detail)) % 2 == 0) c.blend(sx, py, col, a * (1f - i / n.toFloat()))
            }
        }
    }

    /** Wild ducks taking off (the "ducks" effect): up out of the reeds on the left, over the trees and away. */
    private fun flight(level: Float) {
        if (level <= 0.01f) return
        val z = z
        val col = env.lit(Col.hex(0x5A4A3A)); val head = env.lit(Col.hex(0x2E6A3A))
        for (j in 0 until 6) {
            val u = ((level - Noise.rnd(j, 591) * 0.25f) / 0.75f).coerceIn(0f, 1f)
            if (u <= 0f || u >= 1f) continue
            // off the water by the reeds, climbing to the right over the woods (the scene canvas's px)
            val x0 = gx(-6f + Noise.rnd(j, 592) * 2f, 16f) / z; val y0 = gy(16f) / z - 2f
            val x1 = x0 + 150f + Noise.rnd(j, 593) * 60f; val y1 = -14f - Noise.rnd(j, 594) * 10f
            val e = u * u * (3f - 2f * u)
            val bx = ((x0 + (x1 - x0) * u) * z).toInt(); val by = ((y0 + (y1 - y0) * e) * z).toInt()
            val up = ((t * 9).toInt() + j) and 1
            sprite(bx, by) {
                c.set(bx, by, col); c.set(bx + 1, by, col); c.set(bx + 2, by, head); c.set(bx + 3, by, Col.hex(0xE89A2A))
                if (up == 0) { c.set(bx, by - 1, col); c.set(bx - 1, by - 2, col); c.set(bx + 1, by - 1, col); c.set(bx + 1, by - 2, col) }
                else { c.set(bx, by + 1, col); c.set(bx - 1, by + 2, col); c.set(bx + 1, by + 1, col) }
            }
            if (u < 0.15f) s.fx { ring(-6f + Noise.rnd(j, 592) * 2f, 16f, 0.2f + u * 3f, 0.6f * (1f - u / 0.15f)) }
        }
    }

    // ------------------------------------------------------------------ the jetty and the boat

    /** A wooden jetty on posts, running out from the shore on the right: planks across it, a post at each corner of its end. */
    private fun jetty() {
        val z = z
        val snow = env.snow
        // the posts, their feet in the water
        for (dd in floatArrayOf(jettyFar, jettyFar - 2f, jettyFar - 4f)) for (side in floatArrayOf(-1f, 1f)) {
            val x = jettyX + side * (jettyHalf - 0.05f)
            val p = pm(dd); val px = gx(x, dd)
            val tall = if (dd == jettyFar) deckH + 0.55f else deckH
            for (j in 0 until max(1, (0.12f * p).toInt())) c.vline(px.toInt() + j, (gy(dd) - tall * p).toInt(), (gy(dd) + 0.05f * p).toInt(), if (j == 0) Pal.WOOD_M else Pal.WOOD_D)
            if (!snow) s.fx { ring(x, dd, 0.12f + 0.04f * sin(t * 1.5 + dd).toFloat(), 0.3f) }
        }
        // the deck, row by row: planks across with dark gaps, grain, nails closer up
        val yTop = (hz + F * (EYE - deckH) / jettyFar).toInt(); val yBot = (hz + F * (EYE - deckH) / jettyNear).toInt()
        for (py in max(yTop, c.top)..min(yBot, c.bottom - 1)) {
            val d = F * (EYE - deckH) / (py + 0.5f - hz)
            val plank = (jettyFar - d) / 0.25f
            val k = floor(plank).toInt()
            val gap = plank - k > 0.82f
            val xl = gx(jettyX - jettyHalf, d).toInt(); val xr = gx(jettyX + jettyHalf, d).toInt()
            for (px in max(xl, c.left)..min(xr, c.right - 1)) {
                val grain = Noise.rnd(px / (3 * z), k, 601)
                val col = when {
                    snow -> if (Noise.rnd(px, py, 602) < 0.1f) Pal.SNOW_M else Pal.SNOW_L
                    gap -> Pal.WOOD_X
                    z >= 3 && (px == xl + z || px == xr - z) -> Pal.WOOD_X
                    Noise.rnd(k, 603) < 0.2f -> Col.mix(Pal.WOOD_M, Col.hex(0x8A8A7A), 0.4f) // an old grey plank
                    grain < 0.3f -> Pal.WOOD_M
                    else -> Pal.WOOD_L
                }
                c.set(px, py, col)
            }
        }
        // the deck's edge along its left side and its front, in shade
        for (j in 1..max(1, (0.1f * pm(jettyNear)).toInt())) c.line(gx(jettyX - jettyHalf, jettyFar).toInt() - 1, yTop + j, gx(jettyX - jettyHalf, jettyNear).toInt() - 1, yBot + j, Pal.WOOD_D)
    }

    /** The rowing boat tied at the jetty's end: a pointed hull seen from above, its ribs and two seats, a rope to the post. */
    private fun boat() {
        val z = z
        val bob = sin(t * 1.2).toFloat() * 0.03f
        val len = 1.5f; val half = 0.55f; val rim = 0.32f + bob
        val pts = 14
        // the hull's outline in plan: pointed at the bow (away from us), rounded at the stern
        val xs = FloatArray(pts); val ds = FloatArray(pts)
        for (i in 0 until pts) {
            val a = i * 2f * PI.toFloat() / pts
            val v = cos(a)
            val wScale = if (v > 0f) 1f - v * v * 0.85f else 1f - v * v * 0.25f
            xs[i] = boatX + sin(a) * half * wScale; ds[i] = boatD + v * len
        }
        // its sides, from the gunwale down to the water, planked
        for (i in 0 until pts) {
            val j = (i + 1) % pts
            c.polyBegin()
            c.polyAdd(gx(xs[i], ds[i]), gy(ds[i]) - rim * pm(ds[i])); c.polyAdd(gx(xs[j], ds[j]), gy(ds[j]) - rim * pm(ds[j]))
            c.polyAdd(gx(xs[j], ds[j]), gy(ds[j])); c.polyAdd(gx(xs[i], ds[i]), gy(ds[i]))
            val base = gy(boatD)
            c.polyFill { _, y -> if (((base - y) / max(1f, 0.1f * pm(boatD))).toInt() % 2 == 0) Pal.WOOD_M else Col.mix(Pal.WOOD_M, Pal.WOOD_D, 0.5f) }
        }
        // the gunwale all round, then the inside: planks, two seats across
        c.polyBegin()
        for (i in 0 until pts) c.polyAdd(gx(xs[i], ds[i]), gy(ds[i]) - rim * pm(ds[i]))
        c.polyFill(Pal.WOOD_D)
        c.polyBegin()
        for (i in 0 until pts) {
            val x = boatX + (xs[i] - boatX) * 0.84f; val d = boatD + (ds[i] - boatD) * 0.88f
            c.polyAdd(gx(x, d), gy(d) - (rim - 0.02f) * pm(d))
        }
        c.polyFill { _, y -> if (env.snow) Pal.SNOW_L else if ((y / max(1, z)) % 4 == 0) Pal.WOOD_M else Pal.WOOD_L }
        for (sd in floatArrayOf(boatD - 0.45f, boatD + 0.5f)) {
            val p = pm(sd)
            val yy = gy(sd) - (rim - 0.05f) * p
            for (j in 0 until max(1, (0.12f * p).toInt())) c.hline(gx(boatX - half * 0.8f, sd).toInt(), gx(boatX + half * 0.8f, sd).toInt(), yy.toInt() + j, if (j == 0) Pal.WOOD_L else Pal.WOOD_D)
        }
        // the rope from its bow to the jetty's end post
        c.line(gx(xs[0], ds[0]).toInt(), (gy(ds[0]) - rim * pm(ds[0])).toInt(), gx(jettyX + jettyHalf, jettyFar - 2f).toInt(), (gy(jettyFar - 2f) - deckH * pm(jettyFar - 2f)).toInt(), Col.hex(0xC8B488))
        if (!env.snow) s.fx { ring(boatX, boatD - len * 0.6f, 0.9f + bob * 3f, 0.25f) }
    }

    // ------------------------------------------------------------------ the reeds, the heron, the dragonfly

    /**
     * A bank of reeds round ([x], [d]), [rx] × [rd] m: tall stalks with long leaves bending off them and feathery plumes
     * from late summer, swaying; green in spring and summer, straw in autumn and winter (snow on them).
     */
    private fun reeds(x: Float, d: Float, rx: Float, rd: Float, n: Int, big: Boolean) {
        val z = z
        val season = env.season
        val (green, greenL) = when (season) {
            Season.AUTUMN -> Col.hex(0xA8904A) to Col.hex(0xC8B060)
            Season.WINTER -> Col.hex(0xB8A070) to Col.hex(0xD8C490)
            Season.SPRING -> Col.hex(0x5E9A3E) to Col.hex(0x86BE56)
            else -> Col.hex(0x4E8A36) to Col.hex(0x74AE4A)
        }
        val plume = if (season == Season.WINTER) Col.hex(0xC8B8A0) else Col.hex(0x8A6A6A)
        val plumes = env.month in 8..12 || env.month <= 2
        // back to front, so the near stalks cover the far ones
        val order = (0 until n).sortedByDescending { Noise.rnd(it, if (big) 611 else 621) }
        for (k in order) {
            val kd = d + (Noise.rnd(k, if (big) 611 else 621) - 0.5f) * 2f * rd
            val kx = x + (Noise.rnd(k, if (big) 612 else 622) - 0.5f) * 2f * rx
            val p = pm(kd)
            val bx = gx(kx, kd); val by = gy(kd)
            val tall = (1.3f + Noise.rnd(k, 613) * 1.2f) * p
            val lean = (Noise.rnd(k, 618) - 0.5f) * 0.35f * p
            val sway = lean + (sin(t * 1.2 + k * 0.9).toFloat() * 0.06f + 0.03f) * p * gust
            val col = if (k % 3 == 0) greenL else green
            val th = max(1, (0.035f * p).toInt())
            for (j in 0 until th) c.line(bx.toInt() + j, by.toInt(), (bx + sway).toInt() + j, (by - tall).toInt(), col)
            // long leaves arching off the stalk and drooping at their tips
            for (l in 0 until 2) {
                val f = 0.25f + l * 0.3f + Noise.rnd(k, l, 614) * 0.12f
                val lx = bx + sway * f; val ly = by - tall * f
                val s = if ((k + l) % 2 == 0) 1f else -1f
                val ll = (0.4f + Noise.rnd(k, l, 615) * 0.25f) * p
                val mx = lx + s * ll * 0.45f; val my = ly - ll * 0.3f
                val ex = lx + s * ll * 0.8f; val ey = ly + ll * 0.15f
                val leaf = if (l == 0) greenL else green
                c.line(lx.toInt(), ly.toInt(), mx.toInt(), my.toInt(), leaf); c.line(mx.toInt(), my.toInt(), ex.toInt(), ey.toInt(), leaf)
                if (z > 1) c.line(lx.toInt(), ly.toInt() + 1, mx.toInt(), my.toInt() + 1, green)
            }
            if (plumes && k % 2 == 0) {
                val tx = bx + sway; val ty = by - tall
                c.fillEllipse(tx + sway * 0.3f, ty - 0.1f * p, max(1f, 0.06f * p), max(1.5f, 0.16f * p), plume)
                if (z > 1) for (q in 0 until 4) c.set((tx + sway * 0.3f + (Noise.rnd(k, q, 616) - 0.5f) * 0.12f * p).toInt(), (ty - 0.1f * p + (Noise.rnd(k, q, 617) - 0.5f) * 0.3f * p).toInt(), Col.mix(plume, Col.hex(0xFFFFFF), 0.3f))
            }
            if (env.snow && k % 2 == 0) c.fillRect((bx + sway).toInt() - z / 2, (by - tall).toInt(), z, z, Pal.SNOW_L)
        }
    }

    /** The grey heron standing in the shallows on one leg, its neck folded, now and then stretching to look. */
    private fun heron(x: Float, d: Float) {
        val z = z
        val bx = gx(x, d).toInt(); val by = gy(d).toInt()
        val grey = Col.hex(0x9AA0A8); val greyL = Col.hex(0xC8CCD0); val dark = Col.hex(0x3A3E48); val bill = Col.hex(0xE0B040)
        val stretch = sin(t * 0.4) > 0.6
        sprite(bx, by) {
            // the leg, the body, the neck in an S (or stretched up), the dagger bill; its reflection below
            c.vline(bx, by - 4, by, Col.hex(0x8A7A5A))
            for (i in -2..3) c.set(bx + i, by - 6, if (i > 1) dark else grey)
            for (i in -1..2) c.set(bx + i, by - 7, greyL)
            c.set(bx - 2, by - 5, grey); c.set(bx + 3, by - 5, dark); c.set(bx + 4, by - 5, dark)
            if (stretch) {
                for (j in 8..12) c.set(bx - 1, by - j, greyL)
                c.set(bx - 2, by - 13, greyL); c.set(bx - 1, by - 13, dark); c.set(bx - 3, by - 13, bill); c.set(bx - 4, by - 13, bill)
            } else {
                c.set(bx - 1, by - 8, greyL); c.set(bx - 2, by - 9, greyL); c.set(bx - 1, by - 10, greyL)
                c.set(bx - 1, by - 11, greyL); c.set(bx, by - 11, dark); c.set(bx - 2, by - 11, bill); c.set(bx - 3, by - 11, bill)
            }
        }
        if (!env.snow) s.fx { ring(x, d, 0.18f + 0.05f * sin(t * 0.8).toFloat(), 0.3f) }
    }

    /** The dragonfly: darting over the lilies by day, resting on a reed by night (and in winter). */
    private fun dragonfly() {
        val rest = env.dark > 0.5f || env.snow
        val x: Float; val d: Float; val hgt: Float
        if (rest) { x = -5.3f; d = 12.6f; hgt = 1.55f }
        else {
            val seg = 1.3
            val n = floor(t / seg).toInt()
            val e = (((t - n * seg) / 0.22).coerceIn(0.0, 1.0)).toFloat().let { it * it * (3f - 2f * it) }
            fun at(i: Int, k: Int) = Noise.rnd(i, k)
            val xa = -3.4f + (at(n - 1, 631) - 0.5f) * 1.6f; val xb = -3.4f + (at(n, 631) - 0.5f) * 1.6f
            val da = 13.2f + (at(n - 1, 632) - 0.5f) * 1.2f; val db = 13.2f + (at(n, 632) - 0.5f) * 1.2f
            val ha = 0.9f + (at(n - 1, 633) - 0.5f) * 0.4f; val hb = 0.9f + (at(n, 633) - 0.5f) * 0.4f
            x = xa + (xb - xa) * e; d = da + (db - da) * e; hgt = ha + (hb - ha) * e + sin(t * 6.0).toFloat() * 0.02f
        }
        val bx = gx(x, d).toInt(); val by = (gy(d) - hgt * pm(d)).toInt()
        // a red darter here, to tell it from the stream's blue one
        val red = Col.hex(0xD8402A); val redL = Col.hex(0xF07A4A); val eye = Col.hex(0x6A2A1A)
        val flick = !rest && ((t * 30).toInt() and 1) == 0
        sprite(bx, by) {
            for (i in 0..5) c.set(bx + i, by, if (i % 2 == 0) red else redL)
            c.set(bx - 1, by, Col.hex(0x9A4A2A)); c.set(bx - 2, by, eye); c.set(bx - 2, by - 1, eye)
            val wing = Col.hex(0xE4EEF4)
            val a = if (flick) 0.35f else 0.6f
            c.blend(bx - 1, by - 1, wing, a); c.blend(bx, by - 2, wing, a); c.blend(bx + 1, by - 2, wing, a); c.blend(bx + 1, by - 1, wing, a)
            c.blend(bx - 1, by + 1, wing, a); c.blend(bx, by + 2, wing, a); c.blend(bx + 1, by + 2, wing, a); c.blend(bx + 1, by + 1, wing, a)
        }
    }

    // ------------------------------------------------------------------ weather on the water

    override fun weather() {
        val sky = frame.sky
        if (sky.rain > 0.02f && env.season != Season.WINTER) rainOnPond(sky.rain)
        if (sky.fog > 0.02f) {
            // mist lying on the water, thicker than over the shore
            Weather(c, w, h, detail, t, env, sky) { id -> id == waterId }.fog(sky.fog * 0.75f) { 1f }
        }
        super.weather()
    }

    /** Rain on the pond: the picture in it greys over and rings open everywhere, more the harder it rains. */
    private fun rainOnPond(rain: Float) {
        val grey = env.lit(Col.hex(0x7A848A))
        for (py in max(hz + 1, c.top) until min(h, c.bottom)) for (px in max(0, c.left) until min(w, c.right)) {
            val i = c.index(px, py)
            if (c.ids[i] == waterId) c.pixels[i] = Col.mix(c.pixels[i], grey, 0.3f * rain)
        }
        val n = (70 * rain).toInt()
        for (j in 0 until n) {
            val cyc = t * 1.6 + Noise.rnd(j, 641)
            val k = floor(cyc).toInt(); val ph = (cyc - k).toFloat()
            val d = pondD - pondRd + Noise.rnd(j, k, 642) * pondRd * 2f
            val x = pondX + (Noise.rnd(j, k, 643) - 0.5f) * pondRx * 2f
            if (inPond(x, d) > 0.97f) continue
            ring(x, d, 0.04f + ph * 0.3f, 0.55f * (1f - ph))
        }
    }

    companion object {
        /** The moon's column on the stage: over the pond, so it lies on the water. */
        private const val MOON_X = 150
        private const val SKY_STEPS = 24
        private const val HOLE_X = 2f
        private const val HOLE_D = 20.6f
        private val POND_DEEP = Col.hex(0x1E3A34)
        private val SPRUCE_D = Col.hex(0x183626)
        private val SPRUCE_M = Col.hex(0x224C36)
        private val PAD_D = Col.hex(0x1E3A1A)
        private val FLY_OUTLINE = Col.hex(0x3A1A1A)
    }
}
