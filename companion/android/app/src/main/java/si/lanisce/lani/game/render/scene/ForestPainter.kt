package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.Season
import si.lanisce.lani.game.scene.Poke
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** The high seat's floor and the top of its boards, m above the meadow. */
private const val FLOOR_H = 3.0f
private const val RAIL_H = 3.9f

/**
 * V gozdu: a clearing in a beech and spruce forest, seen from its edge. The path runs through the meadow, over
 * the brook on a plank bridge and into the trees; the forest closes round the clearing and parts in the middle
 * where the mountains show. A big beech on the left with a squirrel on its trunk and fallen leaves under it, a
 * tall spruce on the right with an owl in it; a deer across the brook, a mossy boulder on its bank, a lost sheep,
 * fly agarics, a blueberry bush, wildflowers, a bird in the air. Morning light slants between the trunks; at night
 * the owl's eyes shine and fireflies drift over the meadow.
 *
 * Closer up ([detail] 2 and 3) the same clearing is drawn finer: the far forest's spruces in tiers and its beeches
 * in leafy clusters, needles on the big spruce, leaves, bark and lichen on the beech, blades of grass and little
 * flowers in the meadow, pebbles on the path, planks with gaps on the bridge, thin ripples and stones in the brook,
 * dust in the sunbeams, fronds on the ferns, grain and rings on the log; and the creatures and small things drawn
 * again at the finer pixel: the deer, the sheep's curls, the owl's face, the squirrel, the mushrooms' gills and warts,
 * the flowers' petals, the berries, the leaves.
 *
 * Tapped ([pokes]), the squirrel scurries up the trunk, peeks back down and comes down again, and the next time
 * ducks into its hollow and peeks out of it; the owl opens its eyes wide (or blinks, at night), then spreads its wings.
 */
internal class ForestPainter : WildPainter() {
    override val art = "forest"
    override val pokes = listOf(
        Poke("squirrel", listOf(2.6, 3.4), rest = 3.0), Poke("owl", listOf(1.8, 2.4, 2.6), rest = 3.0),
        // the wild animals, while they are out: the hare hops off and back, the hedgehog rolls up, the bear's eyes go
        Poke("hare", listOf(2.4), rest = 3.0), Poke("hedgehog", listOf(3.2), rest = 3.0), Poke("bear", listOf(4.0), rest = 3.0),
    )

    // the plan, in metres: the path, the brook across the clearing, the forest's far edge
    private fun path(d: Float) = 0.4f + 0.9f * sin(d * 0.11f) - max(0f, d - 40f) * 0.03f
    private val pathHalf = 0.75f
    private fun brook(x: Float) = 18.5f + 1.4f * sin(x * 0.32f + 1f)
    private val brookHalf = 0.95f
    private val edge = 62f

    private val beechX = -7.2f; private val beechD = 16.5f
    private val spruceX = 7.4f; private val spruceD = 19f

    private val items = ArrayList<Pair<Float, () -> Unit>>()

    /** Picture pixels per scene canvas pixel: every size and offset in pixels is multiplied by it. */
    private val z: Int get() = detail

    override fun paint() {
        setup()
        val z = z
        vistaSky(3, 5, moonX = ox + 46 * z, moonY = oy + 12 * z)
        mountains()
        ground { x, d, px, py -> clearing(x, d, px, py) }
        forestWall()
        rays()

        thing("stream") { brookPixels() }
        thing("path") { pathPixels() }

        items.clear()
        // trees along the clearing's edges, then the two big ones
        for (k in 0 until 14) {
            val side = if (k % 2 == 0) -1f else 1f
            val d = 26f + Noise.rnd(k, 91) * 34f
            val x = side * (7f + Noise.rnd(k, 92) * 16f + (d - 26f) * 0.15f)
            val spruce = Noise.rnd(k, 93) < 0.55f
            val hgt = if (spruce) 11f + Noise.rnd(k, 94) * 6f else 9f + Noise.rnd(k, 94) * 5f
            items.add(d to { prop(Pal.OUTLINE_TREE) { if (spruce) spruce(x, d, hgt, k) else leafyTree(x, d, hgt, k * 7) } })
        }
        items.add(bridgeD() to { thing("path") { bridge() } })
        items.add(22f to { thing("deer") { deer(gx(4.4f, 22f).toInt(), gy(22f).toInt()) } })
        items.add(spruceD to {
            thing("spruce", outline = Pal.OUTLINE_TREE) { bigSpruce(spruceX, spruceD) }
            thing("owl", slop = 2) { owl(gx(spruceX - 0.55f, spruceD).toInt(), (gy(spruceD) - 5.6f * pm(spruceD)).toInt()) }
        })
        items.add(beechD to {
            thing("tree", outline = Pal.OUTLINE_TREE) { beech(beechX, beechD) }
            thing("squirrel", slop = 2) { squirrel(gx(beechX + 0.45f, beechD).toInt(), (gy(beechD) - 2.4f * pm(beechD)).toInt()) }
        })
        items.add(beechD - 1.5f to { thing("leaves") { fallenLeaves(gx(beechX + 1.4f, beechD - 1.5f).toInt(), gy(beechD - 1.5f).toInt()) } })
        items.add(15.6f to { thing("stone") { boulder(gx(-2.9f, 15.6f).toInt(), gy(15.6f).toInt()) } })
        items.add(13f to { groundShadow(2.6f, 13f, 0.7f); thing("sheep", slop = 1) { sheep(gx(2.6f, 13f).toInt(), gy(13f).toInt()) } })
        items.add(11.2f to { thing("berries", slop = 1) { blueberries(gx(-4.6f, 11.2f).toInt(), gy(11.2f).toInt()) } })
        items.add(10.8f to { thing("flowers", slop = 2) { flowers(gx(5.4f, 10.8f).toInt(), gy(10.8f).toInt()) } })
        items.add(10.4f to { thing("mushroom", slop = 2) { mushrooms(gx(-2.4f, 10.4f).toInt(), gy(10.4f).toInt()) } })
        for (p in peopleAt("clearing")) items.add(13.4f to { groundShadow(-3.7f, 13.4f, 0.5f); person(p, gx(-3.7f, 13.4f).toInt(), gy(13.4f).toInt(), flip = false) })
        for (p in peopleAt("path")) items.add(10.9f to { val x = path(10.9f); groundShadow(x, 10.9f, 0.5f); person(p, gx(x, 10.9f).toInt(), gy(10.9f).toInt(), flip = true) })
        // the hunter's high seat across the brook, and whoever sits up in it, the front boards over their lap
        items.add(seatD to {
            groundShadow(seatX, seatD + 0.4f, 1.2f)
            thing("highseat", slop = 1) { highSeat(seatX, seatD, back = true) }
            for (p in peopleAt("highseat")) {
                val px = gx(seatX + 0.1f, seatD + 0.3f).toInt(); val py = hy(seatD + 0.3f, FLOOR_H).toInt()
                person(p, px, py, flip = false, seated = true)
                // watching, the binoculars up to his eyes; he lowers them to talk
                if (!p.talking && p.pose == si.lanisce.lani.game.scene.Pose.IDLE && p.art == "hunter") prop { binocularsUp(px, py) }
            }
            thing("highseat", slop = 1) { highSeat(seatX, seatD, back = false) }
        })
        wildlife()
        items.sortByDescending { it.first }
        for ((_, draw) in items) draw()

        nearEdge()
        ferns()
        foregroundGrass(0, w, 17)
        thing("bird", outline = 0, slop = 4) { bird() }
        prop(outline = 0) { flock(fxOn("birds")) }
        // the bats flitting over the clearing at dusk and in the night
        val nBats = batCount(fx("bats"), out("bat"))
        if (nBats > 0) creature("bat", slop = 3) { batsOver(ox + 30f * z, hz - 44f * z, ox + 210f * z, hz - 8f * z, nBats, 441, 1.1f) }
        fireflies(0, hz + 12 * z, w, h, 16, 33)
        s.fx { s.effects.leaves(env.season, env.month) }
        if (env.snow) s.fx {
            for (q in 0 until 20) {
                val x = (Noise.rnd(q, 71) * w).toInt(); val y = hz + 30 * z + (Noise.rnd(q, 72) * (h - hz - 30 * z)).toInt()
                if (sin(t * 3 + q * 1.7) > 0.7) {
                    c.blend(x, y, Col.hex(0xFFFFFF), 0.8f)
                    // closer up a glint is a little star
                    if (z > 1) for (j in 1 until z) { c.blend(x - j, y, Col.hex(0xFFFFFF), 0.4f); c.blend(x + j, y, Col.hex(0xFFFFFF), 0.4f); c.blend(x, y - j, Col.hex(0xFFFFFF), 0.4f); c.blend(x, y + j, Col.hex(0xFFFFFF), 0.4f) }
                }
            }
        }
    }

    // ------------------------------------------------------------------ far away

    /** The Julian Alps through the gap in the trees: three peaks, snow on them most of the year. */
    private fun mountains() {
        val z = z
        val rock = if (env.season == Season.WINTER) Col.hex(0xB8C6DA) else Col.hex(0x7482A6)
        val crest = ridge(hz - 16f * z, 30f * z, 0.013f, 97, rock, 0.34f, snowLine = if (env.season == Season.SUMMER) 0.8f else 0.45f, sharp = true)
        if (z > 1) rockFaces(crest)
    }

    /**
     * Closer up: gullies running down the mountains' faces from their crest, falling away from the peaks, each a
     * thin line of shade with a lit rib beside it; bluish in the snow.
     */
    private fun rockFaces(crest: IntArray) {
        val z = z
        val bottom = hz + 1
        val sunLeft = (sunAt()?.first ?: vx) < vx
        val span = w / z
        for (k in 0 until span / 5) {
            var x = ((Noise.rnd(k, 131) * span).toInt() * z + (Noise.rnd(k, 132) * z).toInt()).toFloat()
            val x0 = x.toInt()
            if (x0 < 0 || x0 >= w || crest[x0] >= bottom - 4 * z) continue
            val slope = crest[min(w - 1, x0 + 5 * z)] - crest[max(0, x0 - 5 * z)]
            var lean = (if (slope > 0) 1f else -1f) * (0.15f + Noise.rnd(k, 133) * 0.3f)
            val len = (4f + Noise.rnd(k, 134) * 10f) * z
            var y = crest[x0] + (1 + Noise.rnd(k, 136) * 3f).toInt() * z
            var j = 0
            while (j < len && y < bottom) {
                val xi = x.toInt()
                if (xi !in 0 until w || y < crest[xi] + z) break
                shadeRock(xi, y, if (j > len * 0.75f) 0.93f else 0.87f)
                if (j < len * 0.6f) shadeRock(xi + (if (sunLeft) -1 else 1), y, 1.1f)
                lean += (Noise.rnd(k, j / z, 135) - 0.5f) * 0.2f / z
                x += lean
                y++; j++
            }
        }
    }

    /** Darkens (or lightens, [f] > 1) one pixel of the mountains, if it is on this canvas; snow turns bluish instead. */
    private fun shadeRock(x: Int, y: Int, f: Float) {
        if (!c.inside(x, y)) return
        val i = c.index(x, y)
        val p = c.pixels[i]
        c.pixels[i] = if (Col.lum(p) > 0.75f) (if (f < 1f) Col.mix(p, Col.hex(0x8A9CC0), 0.3f) else p) else Col.scale(p, f)
    }

    /**
     * The forest's far edge all round the clearing: spruce spires and beech crowns side by side, lower in the
     * middle where the path goes in, darker toward their feet, lit on the sun's side. Closer up the spires come in
     * tiers of drooping branches and the crowns in leafy clusters with a lumpy outline.
     */
    private fun forestWall() {
        val z = z
        val fine = z > 1
        val season = env.season
        val base = gy(edge).toInt() + 1
        val sunLeft = (sunAt()?.first ?: vx) < vx
        val spruceD = env.lit(Col.mix(Col.hex(0x1A3A28), env.skyHorizon, 0.16f)); val spruceM = env.lit(Col.mix(Col.hex(0x24503A), env.skyHorizon, 0.14f))
        val spruceL = env.lit(Col.mix(Col.hex(0x3A6E4A), env.skyHorizon, 0.12f))
        val (bd, bm, bl) = when (season) {
            Season.AUTUMN -> Triple(Col.hex(0x6A3A1A), Col.hex(0xA8602A), Col.hex(0xD8983A))
            Season.WINTER -> Triple(Col.hex(0x6A625A), Col.hex(0x8A8278), Col.hex(0xA8A096))
            Season.SPRING -> Triple(Col.hex(0x3E6E30), Col.hex(0x5E9A44), Col.hex(0x8CC25A))
            else -> Triple(Col.hex(0x2E5A2E), Col.hex(0x44803E), Col.hex(0x68A450))
        }.let { (a, b, c2) -> Triple(env.lit(Col.mix(a, env.skyHorizon, 0.14f)), env.lit(Col.mix(b, env.skyHorizon, 0.12f)), env.lit(Col.mix(c2, env.skyHorizon, 0.1f))) }
        val tops = IntArray(w)
        val kinds = IntArray(w)
        // closer up each column also knows its tree's top row and middle, for the tiers
        val apex = if (fine) IntArray(w) else null
        val mids = if (fine) FloatArray(w) else null
        val tier = 4.5f * z
        // the crowns: walk along the edge (in the scene canvas's pixels) placing trees
        tops.fill(base)
        val mid = vx / z
        var x = -10f; var i = 0
        while (x < w / z + 10) {
            val gap = (abs(x - mid) / 80f).coerceIn(0.5f, 1f)
            val spruce = Noise.rnd(i, 95) < 0.6f
            val hh = (34f + Noise.rnd(i, 96) * 18f) * gap
            val half = if (spruce) hh * 0.24f else hh * 0.36f
            val hp = hh * z
            val phase = Noise.rnd(i, 94)
            for (cx in ((x - half) * z).toInt()..((x + half) * z).toInt()) {
                if (cx !in 0 until w) continue
                val u = abs((cx + 0.5f) / z - x) / half
                var top = if (spruce) base - hp * (1f - u) else base - hp * 0.72f - hp * 0.28f * sqrt(max(0f, 1f - u * u))
                if (fine) top += if (spruce) {
                    // tiers: going out from the middle, each ends in a drooping tip under the next one up
                    val along = hp * u
                    ((along / tier + phase) % 1f) * tier * 0.6f * min(1f, along / tier)
                } else (Noise.v1(cx * 0.6f / z, 400 + i) - 0.5f) * 2.4f * z
                if (top < tops[cx]) {
                    tops[cx] = top.toInt(); kinds[cx] = if (spruce) 1 else 2
                    if (apex != null && mids != null) { apex[cx] = (base - hp).toInt(); mids[cx] = x * z }
                }
            }
            x += half * (0.9f + Noise.rnd(i, 97) * 0.5f); i++
        }
        c.penEmissive = true
        for (px in max(0, c.left) until min(w, c.right)) {
            val top = tops[px]
            val slope = tops[min(w - 1, px + z)] - tops[max(0, px - z)]
            val lit = if (sunLeft) slope > 0 else slope < 0
            for (py in max(max(0, top), c.top) until min(min(h, base + 2 * z), c.bottom)) {
                val down = (py - top).toFloat() / max(1f, (base - top).toFloat())
                val n = Noise.rnd(px, py, 98)
                val col = if (apex != null && mids != null) {
                    val dx = px + 0.5f - mids[px]
                    if (kinds[px] == 1) {
                        // under each tier's branches a band of shade, drooping toward the tips
                        val band = ((py - apex[px] - abs(dx) * 0.45f) / tier + 10f) % 1f
                        if (py - top < z && lit) spruceL
                        else if (down > 0.5f + (Dither.at(px, py) - 0.5f) * 0.4f || band > 0.74f || n < 0.08f) spruceD
                        else if (band < 0.18f && n > 0.6f) spruceL else spruceM
                    } else {
                        // leafy clusters, lit on top
                        val cl = Noise.v2(px * 0.55f / z, py * 0.7f / z, 98) + (n - 0.5f) * 0.25f
                        if (py - top < 2 * z && lit && cl > 0.35f) bl
                        else if (down > 0.55f + (Dither.at(px, py) - 0.5f) * 0.4f || cl < 0.3f) bd
                        else if (cl > 0.62f) bl else bm
                    }
                } else if (kinds[px] == 1) {
                    if (py - top < 2 && lit) spruceL else if (down > 0.5f + (Dither.at(px, py) - 0.5f) * 0.4f || n < 0.1f) spruceD else spruceM
                } else {
                    if (py - top < 3 && lit) bl else if (down > 0.55f + (Dither.at(px, py) - 0.5f) * 0.4f) bd else if (n < 0.25f) bl else bm
                }
                val snowy = season == Season.WINTER && Noise.v2(px * 0.5f / z, py * 0.9f / z, 99) > 0.55f && Dither.at(px, py) < 0.7f
                c.set(px, py, if (snowy) env.lit(if (lit) Pal.SNOW_L else Pal.SNOW_M) else col)
            }
        }
        c.penEmissive = false
    }

    // ------------------------------------------------------------------ the clearing

    private fun clearing(x: Float, d: Float, px: Int, py: Int): Int {
        val g = env.grass
        if (env.snow) {
            val n = Noise.v2(x * 0.6f, d * 0.6f, 41) + (Dither.at(px, py) - 0.5f) * 0.2f
            return hazy(if (n < 0.25f) Pal.SNOW_M else Pal.SNOW_L, d)
        }
        // the meadow: tufts and flowers near us, the forest floor darker under the trees at the edges
        val shade = ((abs(x) - (6f + d * 0.25f)) / 4f).coerceIn(0f, 1f) + ((d - edge + 10f) / 10f).coerceIn(0f, 1f)
        val n = Noise.v2(x * 0.7f, d * 0.7f, 3) + (Dither.at(px, py) - 0.5f) * 0.25f
        var col = if (n > 0.62f) g[0] else if (n > 0.32f) g[1] else g[2]
        if (shade > 0.5f + (Dither.at(px, py) - 0.5f) * 0.5f) col = env.forestFloor[if (n > 0.5f) 0 else 1]
        if (z == 1) {
            val r = Noise.rnd(px, py, 5)
            if (d < 22f && r < 0.05f) col = g[2]
            if (d < 22f && r > 0.985f) col = g[3]
            if (env.month in 4..8 && d < 30f && shade < 0.3f && Noise.rnd(px, py, 9) < 0.014f) col = Pal.PAINTED[(px * 7 + py) % 6]
        } else col = meadowCloser(col, d, shade, px, py)
        // the brook's banks: damp, darker
        if (abs(d - brook(x)) < brookHalf + 0.5f) col = Col.scale(col, 0.82f)
        return hazy(col, d)
    }

    /** The meadow closer up: blades of grass instead of dots, taller near us, and the flowers with petals round a heart. */
    private fun meadowCloser(col0: Int, d: Float, shade: Float, px: Int, py: Int): Int {
        val g = env.grass
        val z = z
        var col = col0
        if (d < 22f) {
            val len = (pm(d) * 0.075f).toInt().coerceIn(1, 2 * z)
            val dark = 0.07f / len; val light = 1f - 0.022f / len
            for (j in 0 until len) {
                val r = Noise.rnd(px, py + j, 5)
                if (r < dark) { col = if (j == len - 1 && len > 2) g[1] else g[2]; break }
                if (r > light) { col = g[3]; break }
            }
        }
        // a flower where the scene canvas has one: at 3, petals round a heart; at 2, a head on a stalk
        if (env.month in 4..8 && d < 30f && shade < 0.3f) {
            val cx = px / z; val cy = py / z
            if (Noise.rnd(cx, cy, 9) < 0.014f) {
                val petal = Pal.PAINTED[(cx * 7 + cy) % 6]
                val heart = if (petal == Pal.PAINTED[1] || petal == Pal.PAINTED[5]) Col.hex(0xB0561A) else Pal.GOLD
                val lx = px - cx * z; val ly = py - cy * z
                col = if (z == 2) {
                    if (lx == 0 && ly == 0) petal else if (lx == 0) g[2] else col
                } else {
                    if (lx == 1 && ly == 1) heart else if (lx == 1 || ly == 1) petal else col
                }
            }
        }
        return col
    }

    /** The brook across the clearing: the sky in it, ripples drifting, the far bank's shadow; ice in winter. */
    private fun brookPixels() {
        val z = z
        val top = gy(21f).toInt() - z; val bottom = gy(15.5f).toInt() + z
        val winter = env.season == Season.WINTER
        for (py in max(max(hz + 1, top), c.top)..min(min(h - 1, bottom), c.bottom - 1)) {
            val d = depthAt(py)
            for (px in max(0, c.left) until min(w, c.right)) {
                val x = sideAt(px, d)
                val off = d - brook(x)
                if (abs(off) > brookHalf) continue
                val u = (off + brookHalf) / (2 * brookHalf) // 0 at the far bank, 1 at the near one
                if (z > 1) { c.set(px, py, brookCloser(px, py, x, d, u, winter)); continue }
                val ripple = sin(px * 0.35f + py * 2.1f - t.toFloat() * 3f) > 0.86f
                val col = when {
                    winter -> if (Noise.rnd(px, py, 61) < 0.1f) Pal.ICE_M else if (u < 0.25f) Pal.SNOW_M else Pal.ICE_L
                    u < 0.18f -> Col.mix(Pal.WATER_D, Col.hex(0x1E3A28), 0.4f)       // the far bank's shadow
                    ripple -> Col.mix(skyAt(0.9f), Col.hex(0xFFFFFF), 0.45f)
                    else -> Col.mix(Pal.WATER_M, skyAt(0.7f + u * 0.3f), 0.45f + (Dither.at(px, py) - 0.5f) * 0.15f)
                }
                c.set(px, py, col)
            }
        }
    }

    /**
     * The brook closer up: the same ripples, now thin lines with a brighter middle; stones on the bed showing
     * through the shallows, a wet glint along the near bank; cracks in the ice in winter.
     */
    private fun brookCloser(px: Int, py: Int, x: Float, d: Float, u: Float, winter: Boolean): Int {
        val z = z
        if (winter) {
            val crack = abs(Noise.v2(x * 1.6f, d * 4f, 62) - 0.5f) < 0.035f
            return when {
                crack && u > 0.2f -> Col.mix(Pal.ICE_M, Pal.WATER_L, 0.45f)
                Noise.rnd(px, py, 61) < 0.08f -> Pal.ICE_M
                u < 0.25f -> Pal.SNOW_M
                Noise.v2(x * 0.9f, d * 2f, 63) > 0.7f -> Col.mix(Pal.ICE_L, Col.hex(0xFFFFFF), 0.5f)
                else -> Pal.ICE_L
            }
        }
        if (u < 0.18f) return if (u > 0.15f && Noise.rnd(px, py, 64) < 0.3f) Col.mix(Pal.WATER_D, skyAt(0.8f), 0.3f) else Col.mix(Pal.WATER_D, Col.hex(0x1E3A28), 0.4f)
        val row = py / z; val sub = py - row * z
        val ph = sin(px * 0.35f / z + row * 2.1f - t.toFloat() * 3f)
        val water = Col.mix(Pal.WATER_M, skyAt(0.7f + u * 0.3f), 0.45f + (Dither.at(px, py) - 0.5f) * 0.15f)
        return when {
            u > 0.95f -> Col.mix(skyAt(0.95f), Col.hex(0xFFFFFF), 0.25f)                // the wet edge of the near bank
            ph > 0.86f && sub == 0 -> if (ph > 0.975f) Col.hex(0xFFFFFF) else Col.mix(skyAt(0.9f), Col.hex(0xFFFFFF), 0.45f)
            ph > 0.93f && sub == 1 -> Col.mix(water, Col.hex(0xFFFFFF), 0.3f)
            u > 0.45f && Noise.v2(x * 9f, d * 7f, 65) > 0.76f -> {
                // a stone on the bed, the water over it
                val top = Noise.v2(x * 9f, (d + 0.03f) * 7f, 65) <= 0.76f
                Col.mix(if (top) Pal.STONE_L else Pal.STONE_M, water, 0.5f)
            }
            else -> water
        }
    }

    /** The path through the meadow into the trees; under the bridge the brook runs on. */
    private fun pathPixels() {
        val z = z
        val dirt = if (env.snow) intArrayOf(Pal.SNOW_L, Pal.SNOW_M, Pal.SNOW_D) else intArrayOf(Pal.DIRT_L, Pal.DIRT_M, Pal.DIRT_D)
        for (py in max(hz + 1, c.top) until min(h, c.bottom)) {
            val d = depthAt(py)
            if (d > edge + 4f) continue
            val cx = gx(path(d), d); val half = pathHalf * pm(d)
            val fade = ((d - edge + 12f) / 16f).coerceIn(0f, 1f)
            for (px in max((cx - half - z).toInt(), c.left)..min((cx + half + z).toInt(), c.right - 1)) {
                if (px !in 0 until w) continue
                if (abs(d - brook(sideAt(px, d))) < brookHalf) continue
                val e = abs(px + 0.5f - cx) / half + (Noise.rnd(px, py, 43) - 0.5f) * 0.3f
                if (e > 1f || (fade > 0f && Dither.at(px, py) < fade)) continue
                val r = Noise.rnd(px, py, 45)
                val col = if (z == 1) (if (r < 0.14f) dirt[0] else if (r > 0.88f || e > 0.85f) dirt[2] else dirt[1])
                else pathCloser(px, py, d, e, r, dirt)
                c.set(px, py, hazy(col, d))
            }
        }
        if (z > 1) pebbles()
    }

    /** The path's earth closer up: trodden lighter along the middle, clods, grass creeping in at the edges. */
    private fun pathCloser(px: Int, py: Int, d: Float, e: Float, r: Float, dirt: IntArray): Int {
        val lump = Noise.v2(sideAt(px, d) * 16f, d * 16f, 44)
        return when {
            !env.snow && e > 0.8f && Noise.rnd(px, py, 46) < (e - 0.8f) * 2.5f -> env.grass[2]
            e > 0.86f -> dirt[2]
            r < 0.07f || lump > 0.72f || (e < 0.3f && r < 0.2f) -> dirt[0]
            r > 0.93f || lump < 0.2f -> dirt[2]
            else -> dirt[1]
        }
    }

    /** Closer up: pebbles on the path, rounded, lit from above. */
    private fun pebbles() {
        for (k in 0 until 80) {
            val d = 7f + Noise.rnd(k, 141) * 34f
            if (abs(d - bridgeD()) < brookHalf + 0.7f) continue
            val x = path(d) + (Noise.rnd(k, 142) - 0.5f) * pathHalf * 1.4f
            val p = pm(d); val r = (0.03f + Noise.rnd(k, 143) * 0.045f) * p
            if (r < 0.9f) continue
            val px = gx(x, d); val py = gy(d)
            if (py + r < c.top || py - r > c.bottom || px + r < c.left || px - r > c.right) continue
            val snowy = env.snow
            c.fillEllipse(px, py - r * 0.35f, r, r * 0.62f, hazy(if (snowy) Pal.SNOW_D else Pal.STONE_D, d))
            c.fillEllipse(px - r * 0.18f, py - r * 0.5f, r * 0.72f, r * 0.42f, hazy(if (snowy) Pal.SNOW_L else Pal.STONE_M, d))
            c.set((px - r * 0.4f).toInt(), (py - r * 0.7f).toInt(), hazy(if (snowy) Col.hex(0xFFFFFF) else Pal.STONE_L, d))
        }
    }

    private fun bridgeD() = brook(path(18.5f))

    /** A plank bridge over the brook where the path crosses, with a rail on either side. */
    private fun bridge() {
        val z = z
        val x0 = path(18.5f)
        val d0 = bridgeD() + brookHalf + 0.5f; val d1 = bridgeD() - brookHalf - 0.5f
        val half = 0.8f
        if (z == 1) {
            var d = d0
            while (d >= d1) {
                val p = pm(d); val y = gy(d) - 0.25f * p
                val xl = gx(x0 - half, d); val xr = gx(x0 + half, d)
                c.hline(xl.toInt(), xr.toInt(), y.toInt(), if (env.snow) Pal.SNOW_L else Pal.WOOD_L)
                c.hline(xl.toInt(), xr.toInt(), y.toInt() + 1, Pal.WOOD_M)
                if (p > 9f) c.hline(xl.toInt(), xr.toInt(), y.toInt() + 2, Pal.WOOD_D)
                d -= 0.28f
            }
        } else deck(x0, d0, d1, half)
        // the rails and their posts
        for (side in floatArrayOf(-half, half)) {
            val ax = gx(x0 + side, d0); val ay = gy(d0) - 1.05f * pm(d0)
            val bx = gx(x0 + side, d1); val by = gy(d1) - 1.05f * pm(d1)
            for (j in 0 until z) c.line(ax.toInt(), ay.toInt() + j, bx.toInt(), by.toInt() + j, if (j == 0) Pal.WOOD_L else if (j == z - 1) Pal.WOOD_D else Pal.WOOD_M)
            for (dd in floatArrayOf(d0, (d0 + d1) / 2f, d1)) {
                val px = gx(x0 + side, dd); val p = pm(dd)
                for (j in 0 until z) c.vline(px.toInt() + j, (gy(dd) - 1.05f * p).toInt(), (gy(dd) - 0.2f * p).toInt(), if (j == 0 && z > 1) Pal.WOOD_M else Pal.WOOD_D)
                if (z > 1) c.hline(px.toInt(), px.toInt() + z - 1, (gy(dd) - 1.05f * p).toInt(), Pal.WOOD_L) // the post's cut top
            }
        }
    }

    /** The bridge's deck closer up: planks in perspective with dark gaps between them, grain, nails, snow on them. */
    private fun deck(x0: Float, d0: Float, d1: Float, half: Float) {
        val z = z
        val lift = EYE - 0.25f
        val yTop = (hz + F * lift / d0).toInt(); val yBot = (hz + F * lift / d1).toInt()
        val snow = env.snow
        var xl = 0; var xr = 0
        for (py in yTop..yBot) {
            val d = F * lift / (py + 0.5f - hz)
            val plank = (d0 - d) / 0.28f
            val next = (d0 - F * lift / (py + 1.5f - hz)) / 0.28f
            val prev = (d0 - F * lift / (py - 0.5f - hz)) / 0.28f
            xl = gx(x0 - half, d).toInt(); xr = gx(x0 + half, d).toInt()
            if (py < c.top || py >= c.bottom) continue
            val k = floor(plank).toInt()
            val gap = py < yBot && floor(next).toInt() != k
            val edgeLit = floor(prev).toInt() != k
            for (px in max(xl, c.left)..min(xr, c.right - 1)) {
                val grain = Noise.rnd(px / (2 * z), k, 31)
                val col = when {
                    gap -> if (z >= 3) Pal.WOOD_X else Pal.WOOD_D
                    snow -> if (Noise.rnd(px, py, 32) < 0.1f) Pal.SNOW_M else Pal.SNOW_L
                    edgeLit -> Col.mix(Pal.WOOD_L, Col.hex(0xFFE8C0), 0.25f)
                    (px == xl + z / 2 || px == xr - z / 2) && z >= 3 -> Pal.WOOD_X // the nails
                    grain < 0.3f -> Pal.WOOD_M
                    else -> Pal.WOOD_L
                }
                c.set(px, py, col)
            }
        }
        // the nearest plank's edge
        for (j in 1..2 * z) c.hline(xl, xr, yBot + j, if (j <= z) Pal.WOOD_M else Pal.WOOD_D)
    }

    // ------------------------------------------------------------------ the two big trees

    /**
     * The beech: a smooth grey trunk that forks into the crown, a hollow with a nest, roots gripping the ground;
     * a crown of clumps lit from the sun's side, gold in autumn and bare in winter.
     */
    private fun beech(x: Float, d: Float) {
        val z = z
        val p = pm(d); val bx = gx(x, d); val by = gy(d)
        val barkL = Col.hex(0xB0A694); val bark = Col.hex(0x8A8070); val barkD = Col.hex(0x5E5648)
        val trunkTop = by - 5.2f * p; val half = 0.38f * p
        for (py in max(trunkTop.toInt(), c.top) until min(by.toInt(), c.bottom)) {
            val u = (by - py) / (by - trunkTop)
            val flare = if (u < 0.12f) (0.12f - u) * 4f * half else 0f
            val hw = half * (1f - u * 0.2f) + flare
            val xl = (bx - hw).toInt(); val xr = (bx + hw).toInt()
            if (z > 1) { barkRow(xl, xr, py, hw, u, bx); continue }
            c.hline(xl, xr, py, bark)
            c.hline(xl, xl + max(1, (hw * 0.45f).toInt()), py, barkL)
            c.hline(xr - max(1, (hw * 0.35f).toInt()), xr, py, barkD)
            if (Noise.rnd(py, 11) < 0.12f) c.set(xl + max(1, (hw * 0.9f).toInt()), py, barkD)
        }
        // the fork and main limbs into the crown
        for (a in floatArrayOf(-0.6f, 0.15f, 0.7f)) {
            val ex = bx + sin(a) * 2.2f * p; val ey = trunkTop - cos(a) * 2.4f * p
            val n = max(2 * z, (0.22f * p).toInt())
            for (tk in 0 until n) c.line((bx + tk - z).toInt(), trunkTop.toInt() + 4 * z, (ex + tk).toInt(), ey.toInt(), if (tk == 0) barkL else if (z > 1 && tk == n - 1) barkD else bark)
        }
        if (z > 1) {
            // the hollow's lip, and a nest's twigs in it
            c.fillEllipse(bx + 0.1f * p, by - 2.9f * p + 0.04f * p, 0.17f * p, 0.25f * p, barkD)
            c.fillEllipse(bx + 0.1f * p - z * 0.5f, by - 2.9f * p + 0.02f * p, 0.16f * p, 0.24f * p, barkL)
        }
        c.fillEllipse(bx + 0.1f * p, by - 2.9f * p, 0.13f * p, 0.2f * p, Col.hex(0x2A2018)) // the hollow
        if (z > 1) for (k in 0 until 5) {
            val hx = bx + 0.1f * p + (Noise.rnd(k, 16) - 0.5f) * 0.22f * p; val hy = by - 2.9f * p + 0.12f * p
            c.line(hx.toInt(), hy.toInt(), (hx + (Noise.rnd(k, 17) - 0.5f) * 0.2f * p).toInt(), (hy - Noise.rnd(k, 18) * 0.06f * p).toInt(), if (k % 2 == 0) Pal.HAY_D else Pal.HAY_M)
        }
        if (env.snow) for (r in 1..z) c.hline((bx - half * 1.6f).toInt(), (bx + half * 1.6f).toInt(), by.toInt() - r, Pal.SNOW_L)
        if (env.season == Season.WINTER) {
            fun branch(x0: Float, y0: Float, a: Float, len: Float, depth: Int) {
                val x1 = x0 + sin(a) * len; val y1 = y0 - cos(a) * len
                if (z == 1) {
                    c.line(x0.toInt(), y0.toInt(), x1.toInt(), y1.toInt(), bark)
                    c.set(((x0 + x1) / 2).toInt(), ((y0 + y1) / 2).toInt() - 1, Pal.SNOW_L)
                } else {
                    // closer up the limbs taper to twigs, snow lying along them
                    val th = max(1, (z * (depth + 1) + 2) / 4)
                    for (j in 0 until th) c.line(x0.toInt() + j, y0.toInt(), x1.toInt() + j, y1.toInt(), if (j == 0 && th > 1) barkL else bark)
                    val mx = ((x0 + x1) / 2).toInt(); val my = ((y0 + y1) / 2).toInt()
                    c.hline(mx - z / 2, mx + z / 2 + th - 1, my - 1 - (th + 1) / 2, Pal.SNOW_L)
                    if (depth == 0) for (s2 in floatArrayOf(-0.5f, 0.45f)) c.line(x1.toInt(), y1.toInt(), (x1 + sin(a + s2) * len * 0.4f).toInt(), (y1 - cos(a + s2) * len * 0.4f).toInt(), bark)
                }
                if (depth > 0) { branch(x1, y1, a - 0.4f, len * 0.66f, depth - 1); branch(x1, y1, a + 0.45f, len * 0.62f, depth - 1) }
            }
            for (a in floatArrayOf(-0.6f, 0.15f, 0.7f)) branch(bx + sin(a) * 2.2f * p, trunkTop - cos(a) * 2.4f * p, a, 2.2f * p, 3)
            return
        }
        val (dark, mid, light) = when (env.season) {
            Season.AUTUMN -> Triple(Col.hex(0x8A4A1A), Col.hex(0xC87A2A), Col.hex(0xF0B044))
            Season.SPRING -> Triple(Col.hex(0x3E7A2E), Col.hex(0x6AAA44), Col.hex(0xA8D870))
            else -> Triple(Col.hex(0x2A5A2A), Col.hex(0x3E7A36), Col.hex(0x66A24A))
        }
        val sunLeft = (sunAt()?.first ?: vx) < bx
        val lx = if (sunLeft) -1f else 1f
        val cy = trunkTop - 3.4f * p; val cr = 4.6f * p
        if (z > 1) { val n = c.width * c.height; if (leafBuf.size != n) leafBuf = FloatArray(n); leafBuf.fill(Float.NaN) }
        for (k in 0 until 11) {
            val a = k * 2.4f + 0.7f
            val rr = cr * (0.34f + Noise.rnd(k, 71) * 0.14f)
            val px = bx + sin(a) * cr * 0.62f; val py = cy + cos(a) * cr * 0.42f - (if (k >= 9) cr * 0.4f else 0f)
            for (yy in max((py - rr).toInt(), c.top)..min((py + rr).toInt(), c.bottom - 1)) for (xx in max((px - rr).toInt(), c.left)..min((px + rr).toInt(), c.right - 1)) {
                val dx = (xx + 0.5f - px) / rr; val dy = (yy + 0.5f - py) / (rr * 0.85f)
                val q = dx * dx + dy * dy
                if (z > 1) {
                    // closer up: leaves in clusters, and a leafy edge
                    if (q > 1.2f) continue
                    val leaf = crownLeaf(xx, yy)
                    if (q > 1f + leaf * 0.35f) continue
                    val lit = -dx * lx * 0.5f - dy * 0.8f + leaf * 0.9f + (Noise.rnd(xx, yy, 72) - 0.5f) * 0.1f
                    c.set(xx, yy, if (lit > 0.45f) light else if (lit > -0.25f) mid else dark)
                    continue
                }
                if (q > 1f || (q > 0.8f && Dither.at(xx, yy) < 0.4f)) continue
                val lit = -dx * lx * 0.5f - dy * 0.8f + (Noise.rnd(xx, yy, 72) - 0.5f) * 0.4f
                c.set(xx, yy, if (lit > 0.45f) light else if (lit > -0.25f) mid else dark)
            }
        }
    }

    /**
     * Foliage closer up, as a lighting offset: staggered rows of round leaf clusters [r] scene pixels in radius,
     * each lit on its upper side and rimmed dark at its foot, each row hanging over the one below (so the rims
     * show as dark scallops); −0.5 in the gaps.
     */
    private fun clusters(x: Int, y: Int, r: Float, seed: Int): Float {
        val rad = r * z
        val rh = rad * 1.15f; val cw = rad * 1.6f
        val row0 = floor((y + 0.5f) / rh).toInt()
        for (dr in -1..1) {
            val row = row0 + dr
            val off = if ((row and 1) != 0) cw * 0.5f else 0f
            val cell = floor((x + 0.5f + off) / cw).toInt()
            val hsh = Noise.hash(cell, row, seed)
            val cx = (cell + 0.5f) * cw - off + ((hsh and 255) / 255f - 0.5f) * cw * 0.35f
            val cy = (row + 0.5f) * rh + (((hsh ushr 8) and 255) / 255f - 0.5f) * rh * 0.35f
            val dx = (x + 0.5f - cx) / rad; val dy = (y + 0.5f - cy) / rad
            val q = dx * dx + dy * dy
            if (q > 1f) continue
            if (q > 0.62f && dy > 0.15f) return -0.35f
            return 0.26f - dy * 0.32f - dx * 0.08f + (((hsh ushr 16) and 255) / 255f - 0.5f) * 0.18f
        }
        return -0.5f
    }

    /** The beech crown's [clusters], worked out once a frame per pixel of the canvas (NaN: not yet). */
    private var leafBuf = FloatArray(0)

    private fun crownLeaf(x: Int, y: Int): Float {
        val i = c.index(x, y)
        val v = leafBuf[i]
        if (!v.isNaN()) return v
        return clusters(x, y, 1.35f, 72).also { leafBuf[i] = it }
    }

    /** One row of the beech's trunk closer up: smooth grey bark, lichen patches, the dark marks, moss on the foot. */
    private fun barkRow(xl: Int, xr: Int, py: Int, hw: Float, u: Float, bx: Float) {
        val z = z
        val barkL = Col.hex(0xB0A694); val bark = Col.hex(0x8A8070); val barkD = Col.hex(0x5E5648)
        val lichen = Col.hex(0xA8B498); val moss = if (env.snow) Pal.SNOW_L else Col.hex(0x4E8A3E); val mossL = if (env.snow) Pal.SNOW_M else Col.hex(0x6EAA50)
        val lEnd = xl + max(z, (hw * 0.45f).toInt()); val dStart = xr - max(z, (hw * 0.35f).toInt())
        for (xx in max(xl, c.left)..min(xr, c.right - 1)) {
            val dth = (Dither.at(xx, py) - 0.5f) * 2f
            var col = if (xx + dth < lEnd) barkL else if (xx + dth > dStart) barkD else bark
            if (xx == xl) col = Col.mix(barkL, Col.hex(0xFFFFFF), 0.2f)
            val m = Noise.v2(xx * 0.45f / z, py * 0.2f / z, 14)
            if (m > 0.7f) col = Col.mix(col, lichen, 0.5f) else if (m < 0.22f) col = Col.scale(col, 0.92f)
            // moss up the shaded side of the foot
            if (u < 0.25f && xx > bx - hw * 0.25f && Noise.v2(xx * 0.5f / z, py * 0.3f / z, 15) + (0.25f - u) * 2.4f > 0.95f)
                col = if (Noise.rnd(xx, py, 16) < 0.25f) mossL else moss
            c.set(xx, py, col)
        }
        // the beech's dark marks across the trunk, thin now, where the scene canvas has its dots
        val row = py / z
        if (py - row * z == z / 2 && Noise.rnd(row, 11) < 0.12f) {
            val mx = xl + max(z, (hw * 0.9f).toInt())
            c.hline(mx - z, mx + z / 2, py, barkD)
        }
    }

    /** The big spruce: tier on tier of drooping branches, lit edges on the sun's side, snow on them in winter. */
    private fun bigSpruce(x: Float, d: Float) {
        val z = z
        val p = pm(d); val bx = gx(x, d); val by = gy(d)
        val hh = 10.5f * p; val half = 2.3f * p
        val dark = Col.hex(0x1A3E2A); val mid = Col.hex(0x285A3A); val light = Col.hex(0x3E7A4E); val tip = Col.hex(0x5A9A62)
        val sunLeft = (sunAt()?.first ?: vx) < bx
        val trunk = Col.hex(0x4A3428)
        val tw = max(2 * z, (0.28f * p).toInt())
        c.fillRect((bx - 0.14f * p).toInt(), (by - 1.2f * p).toInt(), tw, (1.2f * p).toInt() + z, trunk)
        if (z > 1) {
            c.vline((bx - 0.14f * p).toInt(), (by - 1.2f * p).toInt(), by.toInt() + z - 1, Col.hex(0x6A4C38))
            c.vline((bx - 0.14f * p).toInt() + tw - 1, (by - 1.2f * p).toInt(), by.toInt() + z - 1, Col.hex(0x34241C))
        }
        val tiers = 9
        for (i in 0 until tiers) {
            val f = (i + 1) / tiers.toFloat()                       // 0 top .. 1 bottom
            val ty = by - 0.9f * p - (hh - 0.9f * p) * (1f - f)
            val tw2 = half * (0.18f + 0.82f * f)
            val th = (hh / tiers) * 1.7f
            val rows = th.toInt().coerceAtLeast(2 * z)
            for (yy in 0 until rows) {
                val v = yy / th
                // each tier droops: wide at its bottom edge, its ends hanging lower
                val ww = tw2 * (0.3f + 0.7f * v)
                val y = (ty - th + yy).toInt()
                if (y < c.top - 2 * z || y >= c.bottom) continue
                for (xx in max((bx - ww).toInt(), c.left)..min((bx + ww).toInt(), c.right - 1)) {
                    val u = (xx + 0.5f - bx) / max(1f, ww)
                    if (abs(u) > 0.92f && v < 0.8f && Dither.at(xx, y) < 0.5f) continue
                    val grain = if (z == 1) (Noise.rnd(xx, y, 81) - 0.5f) * 0.5f else needles(xx - bx.toInt(), y, u)
                    val lit = (if (sunLeft) -u else u) * 0.6f - (1f - v) * 0.4f + grain
                    val col = when {
                        env.snow && v < 0.35f && Noise.rnd(xx, y, 82) < 0.8f -> Pal.SNOW_L
                        lit > 0.35f -> light
                        lit > -0.15f -> mid
                        else -> dark
                    }
                    c.set(xx, y, col)
                }
                if (v > 0.85f) {
                    c.set((bx - ww).toInt(), y + 1, tip); c.set((bx + ww).toInt(), y + 1, tip)
                    // closer up the tips droop on down
                    if (z > 1 && yy == rows - 1) for (j in 1 until z) { c.set((bx - ww).toInt() - (j + 1) / 2, y + 1 + j, tip); c.set((bx + ww).toInt() + (j + 1) / 2, y + 1 + j, tip) }
                }
            }
        }
        // the leader shoot
        for (r in 1..2 * z) c.set(bx.toInt(), (by - hh).toInt() - r, if (r > z) dark else mid)
    }

    /**
     * Needles closer up, as a lighting offset: short strokes slanting down and out from the trunk (two pixels a row,
     * so they read as lines), catching the light, with shade between them.
     */
    private fun needles(dx: Int, y: Int, u: Float): Float {
        val out = if (u < 0f) -dx else dx
        val band = Math.floorDiv(y, 7)
        val s = out - 2 * y + (Noise.rnd(band, out / 9, 84) * 5).toInt()
        if (Math.floorMod(s, 5) >= 2) return -0.13f + (Noise.rnd(dx, y, 81) - 0.5f) * 0.12f
        val on = Noise.rnd(Math.floorDiv(s, 5), Math.floorDiv(y, 3), 83) < 0.56f
        return if (on) 0.36f else -0.04f
    }

    // ------------------------------------------------------------------ light and the near edge

    /** Shafts of low sun slanting between the trees in the morning and the evening; closer up, dust drifting in them. */
    private fun rays() {
        val (sx, _) = sunAt() ?: return
        val low = (1f - env.sun * 2f).coerceIn(0f, 1f)
        if (low < 0.2f || env.snow) return
        val dir = if (sx < vx) 1f else -1f
        val warm = Col.hex(0xFFE8B0)
        val z = z
        s.fx {
            for (k in 0 until 4) {
                val x0 = ox + 40f * z + k * 52f * z + sin(k * 1.7f) * 12f * z
                val width = (5f + (k % 2) * 4f) * z
                for (py in max(hz - 10 * z, c.top) until min(h, c.bottom)) {
                    val xc = x0 + (py - hz) * 0.55f * dir
                    for (px in max((xc - width).toInt(), c.left)..min((xc + width).toInt(), c.right - 1)) {
                        if (px !in 0 until w || py !in 0 until h) continue
                        val a = (1f - abs(px + 0.5f - xc) / width) * 0.16f * low * (0.6f + 0.4f * sin(t * 0.4 + k).toFloat())
                        if (a > Dither.at(px, py) * 0.3f) c.blend(px, py, warm, a)
                    }
                }
                if (z > 1) for (m in 0 until 14) {
                    // motes: rising slowly, twinkling
                    val along = ((Noise.rnd(m, k, 151) * (h - hz) - t * 3.0 * z) % (h - hz) + (h - hz)) % (h - hz)
                    val py = (hz + along).toInt()
                    val px = (x0 + (py - hz) * 0.55f * dir + (Noise.rnd(m, k, 152) - 0.5f) * width * 1.4f + sin(t * 0.7 + m).toFloat() * z).toInt()
                    val tw = 0.5f + 0.5f * sin(t * 2.3 + m * 1.9 + k).toFloat()
                    c.blend(px, py, Col.hex(0xFFF6DA), 0.55f * low * tw)
                }
            }
        }
    }

    /** What lies close to us below the stage (a tall view shows it): a fallen log, stones, tufts of grass. */
    private fun nearEdge() {
        val z = z
        if (gy(9.6f) >= h) return
        prop(Pal.OUTLINE_TREE) {
            // the log, lying across our way on the left, moss on it
            val d = 7.6f; val p = pm(d); val y = gy(d)
            val x0 = gx(-5.8f, d); val x1 = gx(-1.6f, d); val r = 0.28f * p
            c.fillRect(x0.toInt(), (y - 2 * r).toInt(), (x1 - x0).toInt(), (2 * r).toInt(), Pal.LOG_M)
            val moss = if (env.snow) Pal.SNOW_L else Col.hex(0x5C9A48)
            if (z > 1) logCloser(x0, x1, y, r, moss)
            else {
                c.hline(x0.toInt(), x1.toInt(), (y - 2 * r).toInt(), Pal.LOG_L); c.hline(x0.toInt(), x1.toInt(), y.toInt() - 1, Pal.LOG_D)
                c.fillEllipse(x1, y - r, r * 0.7f, r, Col.hex(0xC8A070)); c.fillEllipse(x1, y - r, r * 0.35f, r * 0.5f, Col.hex(0xA07A4A))
                for (x in x0.toInt()..(x1 - r).toInt()) if (Noise.v1(x * 0.2f, 5) > 0.45f) c.set(x, (y - 2 * r).toInt() - 1, moss)
            }
        }
        for (k in 0 until 7) {
            val d = 6f + Noise.rnd(k, 101) * 3.4f; val x = (Noise.rnd(k, 102) - 0.5f) * 10f
            if (abs(x - path(d)) < pathHalf + 0.4f) continue
            val p = pm(d)
            if (k % 3 == 0) prop { stone(gx(x, d), gy(d), 0.25f * p, k) } else tuft(gx(x, d), gy(d), p)
        }
    }

    /** The log closer up: rounded, fissured bark along it, a knot; rings and a crack on its cut end; tufts of moss on top. */
    private fun logCloser(x0: Float, x1: Float, y: Float, r: Float, moss: Int) {
        val z = z
        val yt = (y - 2 * r).toInt(); val yb = y.toInt()
        for (py in max(yt, c.top) until min(yb, c.bottom)) {
            val v = (py + 0.5f - yt) / (yb - yt)
            for (px in max(x0.toInt(), c.left) until min((x1 - r * 0.2f).toInt(), c.right)) {
                val dth = (Dither.at(px, py) - 0.5f) * 0.12f
                var col = if (v + dth < 0.2f) Pal.LOG_L else if (v + dth > 0.78f) Pal.LOG_D else Pal.LOG_M
                // fissures: thin winding lines along the log
                if (abs(Noise.v2(px * 0.035f / z, py * 0.5f / z, 55) - 0.5f) < 0.035f) col = if (v < 0.2f) Pal.LOG_M else Col.scale(Pal.LOG_D, 0.85f)
                c.set(px, py, col)
            }
        }
        // a knot
        val kx = x0 + (x1 - x0) * 0.62f; val ky = y - r * 1.15f
        c.fillEllipse(kx, ky, r * 0.28f, r * 0.2f, Pal.LOG_L); c.fillEllipse(kx + z * 0.3f, ky + z * 0.2f, r * 0.17f, r * 0.12f, Pal.LOG_D)
        // the cut end: bark round it, pale wood, rings, a crack
        c.fillEllipse(x1, y - r, r * 0.76f, r * 1.04f, Pal.LOG_D)
        c.fillEllipse(x1, y - r, r * 0.7f, r, Col.hex(0xC8A070))
        for (f in floatArrayOf(0.72f, 0.5f)) {
            c.fillEllipse(x1, y - r, r * 0.7f * f, r * f, Col.hex(0xB48A5A))
            c.fillEllipse(x1, y - r, r * 0.7f * f - 1f, r * f - 1f, Col.hex(0xC8A070))
        }
        c.fillEllipse(x1, y - r, r * 0.35f, r * 0.5f, Col.hex(0xA07A4A))
        c.fillEllipse(x1, y - r, r * 0.35f - 1.2f, r * 0.5f - 1.2f, Col.hex(0xB08858))
        c.fillEllipse(x1, y - r, r * 0.1f, r * 0.14f, Col.hex(0x8A6038))
        c.line(x1.toInt(), (y - r).toInt(), (x1 + r * 0.45f).toInt(), (y - r * 1.75f).toInt(), Col.hex(0x7A5230))
        // tufts of moss along the top, lighter at their tips
        val mossL = if (env.snow) Col.hex(0xFFFFFF) else Col.hex(0x86C060)
        for (x in max(x0.toInt(), c.left - z)..min((x1 - r).toInt(), c.right + z)) {
            val m = Noise.v1(x * 0.2f / z, 5)
            if (m <= 0.45f) continue
            val tall = 1 + ((m - 0.45f) * 2.5f * z + Noise.rnd(x, 57) * z * 0.7f).toInt()
            for (j in 0 until tall) c.set(x, yt - 1 - j + z / 2, if (j == tall - 1) mossL else moss)
            if (Noise.rnd(x, 58) < 0.3f) c.set(x, yt + z / 2 + 1, moss) // hanging over the edge
        }
    }

    private fun stone(x: Float, y: Float, r: Float, seed: Int) {
        c.fillEllipse(x, y - r * 0.5f, r, r * 0.6f, Pal.STONE_D)
        c.fillEllipse(x - r * 0.15f, y - r * 0.65f, r * 0.8f, r * 0.45f, Pal.STONE_M)
        c.fillEllipse(x - r * 0.35f, y - r * 0.8f, r * 0.35f, r * 0.2f, if (env.snow) Pal.SNOW_L else Pal.STONE_L)
        if (z > 1) {
            // closer up: a glint, a crack, a fleck of lichen
            c.set((x - r * 0.45f).toInt(), (y - r * 0.9f).toInt(), if (env.snow) Col.hex(0xFFFFFF) else Col.mix(Pal.STONE_L, Col.hex(0xFFFFFF), 0.5f))
            c.line((x + r * 0.1f).toInt(), (y - r * 0.95f).toInt(), (x + r * 0.3f).toInt(), (y - r * 0.4f).toInt(), Pal.STONE_X)
            if (!env.snow && seed % 2 == 0) c.fillEllipse(x + r * 0.45f, y - r * 0.6f, r * 0.14f, r * 0.1f, Col.hex(0xB8B878))
        }
    }

    private fun tuft(x: Float, y: Float, p: Float) {
        if (env.snow) return
        val g = env.grass
        val z = z
        if (z == 1) {
            for (b in -3..3) {
                val hgt = 0.3f * p * (1f - abs(b) * 0.15f)
                val lean = b * 0.06f * p + sin(t * 1.4 + x * 0.1).toFloat() * 0.03f * p
                c.line((x + b).toInt(), y.toInt(), (x + b + lean).toInt(), (y - hgt).toInt(), if (b % 2 == 0) g[2] else g[1])
            }
            return
        }
        // closer up: more, thinner blades of different heights, lighter at their tips
        for (b in -3 * z..3 * z step 2) {
            val bs = b.toFloat() / z
            val hgt = 0.3f * p * (1f - abs(bs) * 0.15f) * (0.75f + Noise.rnd(b, x.toInt(), 7) * 0.45f)
            val lean = bs * 0.06f * p + sin(t * 1.4 + x * 0.1 / z).toFloat() * 0.03f * p
            val col = if (Math.floorMod(b / 2, 3) == 0) g[2] else g[1]
            c.line((x + b).toInt(), y.toInt(), (x + b + lean).toInt(), (y - hgt).toInt(), col)
            c.set((x + b + lean).toInt(), (y - hgt).toInt(), g[0])
        }
    }

    /** Fern fronds in the bottom corners, the viewer's own spot at the forest's edge. */
    private fun ferns() {
        if (env.snow) return
        val z = z
        val col = if (env.season == Season.AUTUMN) Col.hex(0xA8702A) else Col.scale(env.grass[2], 0.7f)
        val light = if (env.season == Season.AUTUMN) Col.hex(0xC89040) else Col.scale(env.grass[1], 0.8f)
        for ((i, root) in listOf(-4 * z to h + 2 * z, 10 * z to h + 4 * z, w + 4 * z to h + 2 * z, w - 12 * z to h + 5 * z).withIndex()) {
            val (rx, ry) = root
            val dir = if (rx < w / 2) 1f else -1f
            val len = (26f + (i % 2) * 8f) * z
            val sway = sin(t * 1.2 + i).toFloat() * 1.2f * z
            if (z > 1) { frond(rx, ry, dir, len, sway, col, light); continue }
            for (s2 in 0 until len.toInt()) {
                val f = s2 / len
                val x = rx + dir * f * len * 0.8f + sway * f
                val y = ry - f * len * 0.9f + f * f * len * 0.5f
                c.set(x.toInt(), y.toInt(), col)
                if (s2 % 3 == 0) {
                    val leaf = (1f - f) * 6f + 1f
                    c.line(x.toInt(), y.toInt(), (x - dir * leaf * 0.4f).toInt(), (y - leaf).toInt(), light)
                    c.line(x.toInt(), y.toInt(), (x + dir * leaf).toInt(), (y + leaf * 0.2f).toInt(), col)
                }
            }
        }
    }

    /** A fern frond closer up: a stalk, thicker at its root, pinnae closer together, each lobed with little pinnules. */
    private fun frond(rx: Int, ry: Int, dir: Float, len: Float, sway: Float, col: Int, light: Int) {
        val z = z
        val step = z + 1
        for (s2 in 0 until len.toInt()) {
            val f = s2 / len
            val x = rx + dir * f * len * 0.8f + sway * f
            val y = ry - f * len * 0.9f + f * f * len * 0.5f
            c.set(x.toInt(), y.toInt(), col)
            if (f < 0.45f) c.set(x.toInt(), y.toInt() + 1, col)
            if (s2 % step == 0) {
                val leaf = ((1f - f) * 6f + 1f) * z
                pinna(x, y, -dir * 0.4f, -1f, leaf, light, s2)
                pinna(x, y, dir, 0.2f, leaf, col, s2 + 1)
            }
        }
    }

    private fun pinna(x: Float, y: Float, dx: Float, dy: Float, len: Float, col: Int, seed: Int) {
        val n = sqrt(dx * dx + dy * dy); val ux = dx / n; val uy = dy / n
        val steps = (len * n).toInt()
        for (j in 0..steps) {
            val px = x + ux * j; val py = y + uy * j
            c.set(px.toInt(), py.toInt(), col)
            // pinnules: little lobes on both sides, smaller toward the tip
            if (j in 1 until steps - 1 && (j + seed) % 2 == 0) {
                val lobe = if (j < steps * 0.6f) 1.4f else 0.8f
                c.set((px - uy * lobe).toInt(), (py + ux * lobe).toInt(), col)
                c.set((px + uy * lobe).toInt(), (py - ux * lobe).toInt(), col)
            }
        }
    }

    /** A bird crossing the sky by day. */
    private fun bird() {
        val z = z
        val col = Col.hex(0x2A2A30)
        if (env.dark > 0.6f) {
            val bx = ox + 120 * z; val by = oy + 6 * z
            sprite(bx, by) { c.set(bx, by, env.lit(col)); c.set(bx + 1, by, env.lit(col)) }
            return
        }
        val flap = ((t * 8).toInt() and 1)
        if (z == 1) {
            val x = ox + 20 + ((t * 22) % 200).toInt(); val y = oy + 22 + (sin(t * 3) * 3).toInt()
            c.set(x, y, col); c.set(x + 1, y, col)
            if (flap == 0) { c.set(x - 1, y - 1, col); c.set(x - 2, y - 2, col); c.set(x + 2, y - 1, col); c.set(x + 3, y - 2, col) }
            else { c.set(x - 1, y, col); c.set(x - 2, y + 1, col); c.set(x + 2, y, col); c.set(x + 3, y + 1, col) }
            return
        }
        // closer up it glides smoothly: a body, a head, wings tapering to their tips, a forked tail
        ax = (ox + (20 + (t * 22) % 200) * z).toInt(); ay = (oy + (22 + sin(t * 3) * 3) * z).toInt()
        fe(1f, 0.5f, 1.15f, 0.5f, col)
        fe(-0.1f, 0.3f, 0.45f, 0.4f, col)
        fl(2f, 0.5f, 2.7f, 0.1f, 0.3f, col); fl(2f, 0.6f, 2.8f, 0.9f, 0.3f, col)
        if (flap == 0) { fl(0.6f, 0.3f, -0.6f, -0.8f, 0.6f, col); fl(-0.6f, -0.8f, -1.7f, -1.8f, 0.3f, col); fl(1.4f, 0.3f, 2.4f, -0.8f, 0.6f, col); fl(2.4f, -0.8f, 3.6f, -1.8f, 0.3f, col) }
        else { fl(0.6f, 0.6f, -0.6f, 1f, 0.6f, col); fl(-0.6f, 1f, -1.7f, 1.8f, 0.3f, col); fl(1.4f, 0.6f, 2.4f, 1f, 0.6f, col); fl(2.4f, 1f, 3.6f, 1.8f, 0.3f, col) }
    }

    /**
     * A flock startled out of the trees (a dialog's "birds"): the level is how far they have flown, from the treetops
     * behind the clearing (0) up over the sky and away (1), not all at once; going back to 0 they settle again.
     */
    private fun flock(level: Float) {
        if (level <= 0.01f) return
        val z = z
        val col = env.lit(Col.hex(0x2A2A30))
        for (j in 0 until 9) {
            val u = ((level - Noise.rnd(j, 65) * 0.2f) / 0.8f).coerceIn(0f, 1f)
            if (u <= 0f || u >= 1f) continue
            // off the tree line and up to the right, fast off the branches, slower up high (the scene canvas's px)
            val x0 = 40f + Noise.rnd(j, 61) * 170f; val y0 = horizon - 3f - Noise.rnd(j, 62) * 12f
            val x1 = x0 + 45f + Noise.rnd(j, 63) * 45f; val y1 = -12f - Noise.rnd(j, 64) * 12f
            val e = 1f - (1f - u) * (1f - u)
            val bx = ox + ((x0 + (x1 - x0) * u) * z).toInt(); val by = oy + ((y0 + (y1 - y0) * e) * z).toInt()
            val up = ((t * 10).toInt() + j) and 1
            sprite(bx, by) {
                c.set(bx, by, col); c.set(bx + 1, by, col)
                if (up == 0) { c.set(bx - 1, by - 1, col); c.set(bx - 2, by - 2, col); c.set(bx + 2, by - 1, col); c.set(bx + 3, by - 2, col) }
                else { c.set(bx - 1, by, col); c.set(bx - 2, by + 1, col); c.set(bx + 2, by, col); c.set(bx + 3, by + 1, col) }
            }
        }
    }

    // ------------------------------------------------------------------ the high seat and the wild animals

    private val seatX = -3.3f
    private val seatD = 26f

    /** A beam from ([x0], [y0]) to ([x1], [y1]) in picture px, [th] px thick (a line at the least). */
    private fun beam(x0: Float, y0: Float, x1: Float, y1: Float, th: Float, col: Int) {
        if (th <= 1.01f) { c.line(floor(x0).toInt(), floor(y0).toInt(), floor(x1).toInt(), floor(y1).toInt(), col); return }
        val dx = x1 - x0; val dy = y1 - y0
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        val nx = -dy / len * th / 2; val ny = dx / len * th / 2
        c.polyBegin(); c.polyAdd(x0 + nx, y0 + ny); c.polyAdd(x1 + nx, y1 + ny); c.polyAdd(x1 - nx, y1 - ny); c.polyAdd(x0 - nx, y0 - ny)
        c.polyFill(col)
    }

    /**
     * The hunter's high seat (preža) across the brook, weathered grey-brown: four poles splayed a little at the foot and
     * braced crosswise, a ladder leaning up to the front, a floor at 3 m with a bench, boards round its back and sides, open
     * at the front but for a rail. [back]: the poles, the ladder, the floor, the bench and the boards; else the floor's edge
     * and the rail, drawn over whoever sits up there.
     */
    private fun highSeat(x: Float, d: Float, back: Boolean) {
        val wood = Col.hex(0x8C7658); val woodL = Col.hex(0xB49E78); val woodD = Col.hex(0x5E4C3A); val woodX = Col.hex(0x46382A)
        val db = d + 1.2f // the back poles
        val p = pm(d)
        val pole = max(1f, 0.16f * p)
        val snow = env.snow
        if (back) {
            // the back poles and their braces, darker
            for (sx in floatArrayOf(-0.95f, 0.95f)) beam(gx(x + sx * 1.1f, db), gy(db), gx(x + sx * 0.8f, db), hy(db, RAIL_H + 0.1f), pole * 0.85f, woodD)
            beam(gx(x - 0.9f, db), hy(db, 0.5f), gx(x + 0.9f, db), hy(db, 2.6f), max(1f, pole * 0.5f), woodX)
            // the back and side boards up to the rail
            val bl = gx(x - 0.8f, db); val br = gx(x + 0.8f, db); val bt = hy(db, RAIL_H); val bb = hy(db, FLOOR_H)
            c.fillRect(bl.toInt(), bt.toInt(), (br - bl).toInt() + 1, (bb - bt).toInt() + 1, woodD)
            for (sx in floatArrayOf(-1f, 1f)) {
                val xf = gx(x + sx * 0.8f, d); val xb = gx(x + sx * 0.8f, db)
                c.polyBegin(); c.polyAdd(xb, hy(db, RAIL_H)); c.polyAdd(xf, hy(d, RAIL_H)); c.polyAdd(xf, hy(d, FLOOR_H)); c.polyAdd(xb, hy(db, FLOOR_H)); c.polyFill(wood)
                beam(xb, hy(db, RAIL_H), xf, hy(d, RAIL_H), max(1f, pole * 0.7f), woodL)
            }
            // the floor and the bench along the back
            c.polyBegin(); c.polyAdd(gx(x - 0.85f, db), hy(db, FLOOR_H)); c.polyAdd(gx(x + 0.85f, db), hy(db, FLOOR_H)); c.polyAdd(gx(x + 0.85f, d), hy(d, FLOOR_H)); c.polyAdd(gx(x - 0.85f, d), hy(d, FLOOR_H)); c.polyFill(woodL)
            beam(gx(x - 0.75f, db - 0.25f), hy(db - 0.25f, FLOOR_H + 0.45f), gx(x + 0.75f, db - 0.25f), hy(db - 0.25f, FLOOR_H + 0.45f), max(1f, pole * 0.8f), woodL)
            // the front poles, splayed at the foot, and their brace
            for (sx in floatArrayOf(-1f, 1f)) beam(gx(x + sx * 1.15f, d), gy(d), gx(x + sx * 0.82f, d), hy(d, RAIL_H + 0.15f), pole, wood)
            beam(gx(x - 1.05f, d), hy(d, 0.4f), gx(x + 0.88f, d), hy(d, 2.7f), max(1f, pole * 0.6f), woodD)
            beam(gx(x + 1.05f, d), hy(d, 0.4f), gx(x - 0.88f, d), hy(d, 2.7f), max(1f, pole * 0.6f), woodD)
            // the ladder leaning up from the meadow to the floor's edge, its rungs
            val lf = d - 1.3f
            for (sx in floatArrayOf(-0.28f, 0.28f)) beam(gx(x + sx, lf), gy(lf), gx(x + sx, d - 0.05f), hy(d - 0.05f, FLOOR_H + 0.1f), max(1f, pole * 0.7f), woodL)
            for (k in 1..7) {
                val f = k / 8f
                val dd = lf + (d - 0.05f - lf) * f; val hh = FLOOR_H * f
                beam(gx(x - 0.28f, dd), hy(dd, hh), gx(x + 0.28f, dd), hy(dd, hh), max(1f, pole * 0.5f), if (detail > 1) woodL else wood)
            }
            if (snow) for (sx in floatArrayOf(-0.8f, 0.8f)) c.set(gx(x + sx, d).toInt(), hy(d, RAIL_H).toInt() - 1, Pal.SNOW_L)
            return
        }
        // open at the front: the floor's edge, and the rail across to rest the elbows on
        val l = gx(x - 0.82f, d); val r = gx(x + 0.82f, d)
        beam(l, hy(d, FLOOR_H), r, hy(d, FLOOR_H), max(1f, pole * 0.8f), woodD)
        beam(l - pole * 0.3f, hy(d, RAIL_H), r + pole * 0.3f, hy(d, RAIL_H), max(1f, pole * 0.9f), if (snow) Pal.SNOW_L else woodL)
        if (detail > 1) c.hline(l.toInt(), r.toInt(), hy(d, RAIL_H).toInt() + detail / 2, woodD) // its shaded underside
    }

    /**
     * Binoculars held up to the eyes of the hunter sitting at ([bx], [by]) (his sprite's feet), his hands round them: drawn
     * at the people's scale, over his face.
     */
    private fun binocularsUp(bx: Int, by: Int) {
        val body = Col.hex(0x26262C); val lit = Col.hex(0x4A4A56); val glass = Col.hex(0x9CC4E4)
        sprite(bx, by) {
            val ey = by - 17 // his eyes' row, sitting
            c.fillRect(bx - 4, ey - 1, 3, 3, body); c.fillRect(bx + 1, ey - 1, 3, 3, body); c.hline(bx - 1, bx, ey, body)
            c.hline(bx - 4, bx - 2, ey - 1, lit); c.hline(bx + 1, bx + 3, ey - 1, lit)
            c.set(bx - 3, ey + 1, glass); c.set(bx + 2, ey + 1, glass)
            c.fillRect(bx - 5, ey + 1, 2, 2, Pal.SKIN); c.fillRect(bx + 3, ey + 1, 2, 2, Pal.SKIN) // his hands
        }
    }

    /**
     * The forest's wild animals of the frame ([out], or brought by a dialog's cue): the stag (and two hinds with a "herd")
     * at the far edge, the fox, the hare in the meadow, the hedgehog by the mushrooms, butterflies over the flowers, the
     * boar and her piglets rooting at the edge, the badger, the dormouse in the beech, the bear's eyes under the trees.
     */
    private fun wildlife() {
        val herd = fx("herd")
        if (out("stag") || (herd ?: 0f) > 0.02f) {
            val v = herd ?: 1f
            val u = spot("stag")
            // either side of the gap where the path goes in, clear of the high seat, the fox and the bear
            val sx = if (u < 0.35f) -13f + u * 10f else 5.5f + (u - 0.35f) * 7f
            val dd = 60f - 7f * v
            val walking = v in 0.03f..0.97f
            items.add(dd to {
                groundShadow(sx, dd, 1.2f)
                creature("stag") { redDeer(gx(sx, dd), gy(dd), 48f / dd, left = sx > 0f, walk = if (walking) (t * 1.1).toFloat() % 1f else 0f) }
            })
            if (herd != null && herd > 0.02f) for ((k, off) in listOf(-2.4f, 2.8f).withIndex()) {
                val hd = dd + 1.5f + k * 1.2f
                items.add(hd to { prop { redDeer(gx(sx + off, hd), gy(hd), 44f / hd, left = sx > 0f, stag = false, walk = if (walking) ((t * 1.1 + 0.3 * k) % 1.0).toFloat() else 0f) } })
            }
        }
        val foxCue = fx("fox")
        if (out("fox") || (foxCue ?: 0f) > 0.02f) {
            val u = spot("fox")
            // left of where the path goes in; a cue brings her trotting along the edge from the left
            val home = -2.8f + u * 1.4f
            val v = foxCue ?: 1f
            val fd0 = 36f + u * 4f
            val fx0 = -17f
            val x = fx0 + (home - fx0) * v
            val trotting = v < 0.97f
            items.add(fd0 to { creature("fox") { fox(gx(x, fd0), gy(fd0), 50f / fd0, left = false, sit = !trotting) } })
        }
        if (out("hare")) {
            val u = spot("hare")
            val hx = if (u < 0.5f) -8f + u * 5f else 2.4f + (u - 0.5f) * 3f
            val hd = 22f + u * 6f
            val pk = poked("hare")
            val a = pk?.age(t) ?: 0f
            // tapped: three hops off and three back
            val q = if (pk != null && a < 2.4f) a / 2.4f else 0f
            val off = sin(q * Math.PI.toFloat()) * 2.6f * (if (hx < 0f) -1f else 1f)
            val hop = if (q > 0f) ((q * 6f) % 1f) else 0f
            items.add(hd to { groundShadow(hx + off, hd, 0.35f); creature("hare") { hare(gx(hx + off, hd), gy(hd), 38f / hd, left = (q > 0.5f) == (hx < 0f), hop = hop) } })
        }
        if (out("hedgehog")) {
            val u = spot("hedgehog")
            val ex = if (u < 0.5f) -1.1f else 4.4f
            val ed = if (u < 0.5f) 12.2f + u else 11.6f
            val pk = poked("hedgehog")
            val a = pk?.age(t) ?: 0f
            val curl = if (pk != null && a in 0.2f..2.6f) 1f else 0f
            items.add(ed to { creature("hedgehog") { hedgehog(gx(ex, ed), gy(ed), 1.5f, left = u > 0.5f, curl = curl) } })
        }
        if (out("butterfly")) {
            // over the wildflowers and the blueberries, fluttering about
            for ((k, at) in listOf(5.4f to 10.8f, -4.6f to 11.2f).withIndex()) {
                val (bx, bd) = at
                val kind = (spot("butterfly") * 3).toInt() + k
                items.add(bd - 0.3f to {
                    creature("butterfly", slop = 3) {
                        val fx0 = gx(bx, bd) + sin(t * 0.9 + k * 2).toFloat() * 7f * z
                        val fy0 = gy(bd) - (13f + 5f * sin(t * 1.3 + k).toFloat()) * z
                        butterfly(fx0, fy0, 1.6f, kind)
                    }
                })
            }
        }
        if (out("boar")) {
            val u = spot("boar")
            val bx = -11.5f + u * 4.5f
            val bd = 37f + u * 5f
            items.add(bd to { groundShadow(bx, bd, 1f); creature("boar") { boars(gx(bx, bd), gy(bd), 46f / bd, left = u > 0.5f, piglets = 2 + (u * 7).toInt() % 2) } })
        }
        if (out("badger")) {
            val u = spot("badger")
            val bx = -7.4f + u * 1.8f; val bd = 30f + u * 2f
            items.add(bd to { creature("badger") { badger(gx(bx, bd), gy(bd), 40f / bd, left = u > 0.5f) } })
        }
        if (out("dormouse")) {
            // on the beech's lowest branch, over the squirrel's hollow
            items.add(beechD - 0.05f to { creature("dormouse") { dormouse(gx(beechX + 1.3f, beechD), hy(beechD, 5.3f), 0.9f) } })
        }
        if (out("bear")) {
            val u = spot("bear")
            // in the dark under the trees where the path goes in
            val bx = 1.6f + u * 2.2f
            val bd = 60f
            val pk = poked("bear")
            val a = pk?.age(t) ?: 0f
            // tapped: a blink, and it's gone back into the trees until the poke is over
            val gone = if (pk != null && a < 4f) PokeArt.ease(a, 0.3f, 0.9f) * (1f - PokeArt.ease(a, 3.4f, 4f)) else 0f
            items.add(bd to {
                pokeable("bear") { bearEyes(gx(bx, bd), gy(bd) + gone * 3f * z, 0.85f, gone) }
                // the undergrowth stirs where it slips back into the trees
                if (gone in 0.02f..0.98f) {
                    val px = gx(bx, bd); val py = gy(bd) - 3f * z
                    s.fx { PokeArt.puff(c, px, py, gone, 9f * z, 7, Col.hex(0x6A7A52), 91, 0.9f) }
                }
            })
        }
    }

    // ------------------------------------------------------------------ closer up: motifs drawn finer

    /** The anchor of a motif drawn finer; its offsets are in the scene canvas's pixels (a pixel i spans i until i + 1). */
    private var ax = 0
    private var ay = 0

    private fun fx(sx: Float): Float = ax + sx * z
    private fun fy(sy: Float): Float = ay + sy * z

    /** An ellipse [rx] × [ry] round ([cx], [cy]). */
    private fun fe(cx: Float, cy: Float, rx: Float, ry: Float, col: Int) = c.fillEllipse(fx(cx), fy(cy), rx * z, ry * z, col)

    /** The rectangle from ([x], [y]), [w] × [h]. */
    private fun fr(x: Float, y: Float, w: Float, h: Float, col: Int) {
        val x0 = floor(fx(x)).toInt(); val y0 = floor(fy(y)).toInt()
        c.fillRect(x0, y0, floor(fx(x + w)).toInt() - x0, floor(fy(y + h)).toInt() - y0, col)
    }

    /** One picture pixel at ([x], [y]). */
    private fun fd(x: Float, y: Float, col: Int) = c.set(floor(fx(x)).toInt(), floor(fy(y)).toInt(), col)

    /** A line from ([x0], [y0]) to ([x1], [y1]), [th] thick (one picture pixel at the least). */
    private fun fl(x0: Float, y0: Float, x1: Float, y1: Float, th: Float, col: Int) {
        val ax0 = fx(x0); val ay0 = fy(y0); val ax1 = fx(x1); val ay1 = fy(y1)
        val tt = th * z
        if (tt <= 1.01f) { c.line(floor(ax0).toInt(), floor(ay0).toInt(), floor(ax1).toInt(), floor(ay1).toInt(), col); return }
        val dx = ax1 - ax0; val dy = ay1 - ay0
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        val nx = -dy / len * tt / 2; val ny = dx / len * tt / 2
        c.polyBegin(); c.polyAdd(ax0 + nx, ay0 + ny); c.polyAdd(ax1 + nx, ay1 + ny); c.polyAdd(ax1 - nx, ay1 - ny); c.polyAdd(ax0 - nx, ay0 - ny)
        c.polyFill(col)
    }

    /** A convex polygon through the points (x, y, x, y, …). */
    private fun fp(col: Int, vararg pts: Float) {
        c.polyBegin()
        var i = 0
        while (i < pts.size) { c.polyAdd(fx(pts[i]), fy(pts[i + 1])); i += 2 }
        c.polyFill(col)
    }

    /** A leaf [len] long and [wid] wide round ([cx], [cy]), turned by [a], with a darker midrib and a stalk. */
    private fun leafAt(cx: Float, cy: Float, len: Float, wid: Float, a: Float, col: Int, rib: Int) {
        val ca = cos(a); val sa = sin(a)
        fun px(u: Float, v: Float) = cx + u * ca - v * sa
        fun py(u: Float, v: Float) = cy + u * sa + v * ca
        fp(col, px(-len, 0f), py(-len, 0f), px(-len * 0.4f, -wid), py(-len * 0.4f, -wid), px(len * 0.4f, -wid), py(len * 0.4f, -wid),
            px(len, 0f), py(len, 0f), px(len * 0.4f, wid), py(len * 0.4f, wid), px(-len * 0.4f, wid), py(-len * 0.4f, wid))
        if (z >= 3) fl(px(-len * 0.9f, 0f), py(-len * 0.9f, 0f), px(len * 0.6f, 0f), py(len * 0.6f, 0f), 0.2f, rib)
        fd(px(-len - 0.25f, 0f), py(-len - 0.25f, 0f), rib)
    }

    // ------------------------------------------------------------------ the creatures and small things

    /**
     * The squirrel on the beech's trunk, sitting with a nut, its feet at ([bx], [by]). Tapped ([pokes]) it scurries up the
     * trunk, peeks back down head first and comes down again; the next time it ducks into the hollow behind it, peeks
     * out and comes back. While it is in there its place stays tappable for its word.
     */
    private fun squirrel(bx: Int, by: Int) {
        val pk = poked("squirrel")
        if (pk != null) {
            val a = pk.age(t)
            if (pk.step == 0 && a in 0.15f..2.4f) {
                // up (head up, feet going), a look back down from up there, down again head first
                val up = when {
                    a < 0.7f -> PokeArt.ease(a, 0.15f, 0.7f)
                    a < 1.9f -> 1f
                    else -> 1f - PokeArt.ease(a, 1.9f, 2.4f)
                }
                val moving = a < 0.7f || a >= 1.9f
                climbing(bx - 2, (by - up * 22f * z).toInt(), headUp = a < 1.3f, step = moving && ((a * 12).toInt() and 1) == 0, peek = !moving)
                return
            }
            if (pk.step == 1 && a in 0.3f..2.8f) {
                // in the hollow: its place stays tappable; after a while its head peeks out
                thingRect("squirrel", bx - 6 * z, by - 14 * z, bx + 7 * z, by + z)
                if (a > 1.6f) peeking(bx, by, a - 1.6f)
                return
            }
        }
        val red = Col.hex(0xC0602A); val redL = Col.hex(0xE08A4A); val cream = Col.hex(0xF4E4C8)
        val flick = ((t * 2).toInt() and 1)
        if (z > 1) { squirrelCloser(bx, by, flick.toFloat(), red, redL, cream); return }
        // tail curling up behind
        c.fillRect(bx + 3, by - 12 + flick, 3, 9, red); c.set(bx + 4, by - 13 + flick, red); c.vline(bx + 5, by - 11 + flick, by - 5, redL)
        c.set(bx + 3, by - 4, red)
        // body and head
        c.fillRect(bx - 3, by - 6, 6, 6, red); c.fillRect(bx - 2, by - 4, 3, 4, cream)
        c.fillRect(bx - 5, by - 9, 5, 4, red); c.set(bx - 5, by - 10, red); c.set(bx - 3, by - 10, red)
        c.set(bx - 4, by - 8, Pal.OUTLINE); c.set(bx - 6, by - 7, Col.hex(0x3A2018))
        c.set(bx - 1, by - 1, Col.hex(0x7A3A1A)); c.set(bx + 1, by - 1, Col.hex(0x7A3A1A))
        c.set(bx - 4, by - 5, Pal.HAY_M) // a nut
    }

    /**
     * The squirrel climbing the trunk, the bottom of it at ([bx], [by]): its back to us, head up (or down, its face to us),
     * the bushy tail out to the side, its feet reaching in turn ([step]); up there [peek]ing down, its tail flicks.
     */
    private fun climbing(bx: Int, by: Int, headUp: Boolean, step: Boolean, peek: Boolean) {
        ax = bx; ay = by
        val red = Col.hex(0xC0602A); val redL = Col.hex(0xE08A4A); val redD = Col.hex(0x9A4A1E); val cream = Col.hex(0xF4E4C8)
        fun y(v: Float) = if (headUp) v else -12f - v
        val sw = if (step) 0.8f else -0.8f
        val flick = if (peek && ((t * 4).toInt() and 1) == 0) 0.8f else 0f
        // the bushy tail out to the right, curling at its tip
        fe(2.5f + flick, y(-3.2f), 1.5f, 3f, red); fe(2.2f + flick, y(-6.6f), 1.1f, 1.1f, red)
        fl(3.5f + flick, y(-5.4f), 3.5f + flick, y(-1.4f), 0.4f, redL)
        // the feet gripping the bark
        fe(-1.5f, y(-7f + sw), 0.6f, 0.45f, redD); fe(1.4f, y(-7f - sw), 0.6f, 0.45f, redD)
        fe(-1.6f, y(-2.4f - sw), 0.7f, 0.5f, redD); fe(1.5f, y(-2.4f + sw), 0.7f, 0.5f, redD)
        // the body, lit on its left, the head and its ears
        fe(0f, y(-5f), 1.7f, 3.2f, red)
        fe(-0.6f, y(-5.2f), 0.6f, 2.2f, redL)
        fe(0f, y(-9.4f), 1.6f, 1.5f, red)
        fp(red, -1.4f, y(-10.1f), -0.2f, y(-10.4f), -0.9f, y(-11.9f))
        fp(red, 0.2f, y(-10.4f), 1.4f, y(-10.1f), 0.9f, y(-11.9f))
        if (!headUp) {
            // head down, peeking: its face, eyes and nose
            fe(0f, y(-8.6f), 0.8f, 0.5f, cream)
            fd(-0.7f, y(-9.7f), Pal.OUTLINE); fd(0.6f, y(-9.7f), Pal.OUTLINE)
            fd(0f, y(-8.2f), Col.hex(0x3A2018))
        }
    }

    /** The squirrel's head peeking out of the hollow above its place ([bx], [by]), [a] s after it first shows. */
    private fun peeking(bx: Int, by: Int, a: Float) {
        val p = pm(beechD)
        ax = (bx - 0.35f * p).toInt(); ay = (by - 0.5f * p).toInt()
        val red = Col.hex(0xC0602A); val cream = Col.hex(0xF4E4C8)
        val up = PokeArt.ease(a, 0f, 0.3f) * 1.2f + (if (((a * 2).toInt() and 1) == 1) 0.3f else 0f)
        fe(0f, 0.8f - up, 1.3f, 1.1f, red)
        fp(red, -1.2f, -0.2f - up, -0.3f, -0.4f - up, -0.9f, -1.8f - up)
        fp(red, 0.3f, -0.4f - up, 1.2f, -0.2f - up, 0.9f, -1.8f - up)
        fe(0f, 1.4f - up, 0.7f, 0.4f, cream)
        fd(-0.6f, 0.5f - up, Pal.OUTLINE); fd(0.5f, 0.5f - up, Pal.OUTLINE)
        fd(0f, 1.1f - up, Col.hex(0x3A2018))
    }

    /** The squirrel closer up: a bushy tail with stray hairs, tufted ears, an eye with a glint, a nut in its paws. */
    private fun squirrelCloser(bx: Int, by: Int, f: Float, red: Int, redL: Int, cream: Int) {
        ax = bx; ay = by
        val redD = Col.hex(0x9A4A1E)
        // the tail, curling up behind, lighter along its back
        fe(4.5f, -7.4f + f, 1.7f, 4.6f, red)
        fe(4.2f, -12f + f, 1.4f, 1.2f, red)
        fe(3.6f, -3.6f, 0.9f, 0.7f, red)
        fl(5.6f, -11.2f + f, 5.6f, -4.8f, 0.45f, redL)
        fd(6.3f, -10f + f, red); fd(6.2f, -7.5f + f, red); fd(6.3f, -5.4f + f, red); fd(3f, -12.2f + f, red); fd(4.8f, -13.4f + f, redL)
        // body, haunch, the cream belly
        fe(0f, -3f, 3f, 3f, red)
        fe(1.5f, -2f, 1.5f, 1.7f, redD); fe(1.3f, -2.3f, 1.2f, 1.4f, red)
        fe(-0.6f, -2f, 1.3f, 2f, cream)
        // the head, snout to the left, ears with tufts
        fe(-2.5f, -7f, 2.5f, 2f, red)
        fe(-4.5f, -6.7f, 1.3f, 1.05f, red)
        fp(red, -5f, -8.5f, -3.9f, -8.5f, -4.7f, -10.4f)
        fp(red, -3f, -8.5f, -1.9f, -8.5f, -2.6f, -10.4f)
        fd(-4.7f, -10.4f, redD); fd(-2.6f, -10.4f, redD)
        fe(-4.2f, -5.9f, 0.8f, 0.45f, cream)
        // the eye with a glint, the nose
        fe(-3.5f, -7.5f, 0.55f, 0.55f, Pal.OUTLINE); fd(-3.8f, -7.85f, Col.hex(0xFFFFFF))
        fd(-5.75f, -6.8f, Col.hex(0x3A2018))
        // the paws round a nut, the feet
        fe(-3.4f, -4.5f, 0.7f, 0.75f, Pal.HAY_M); fl(-3.95f, -5.05f, -2.85f, -5.05f, 0.3f, Pal.HAY_D)
        fe(-2.6f, -4.9f, 0.5f, 0.4f, red)
        fe(-0.5f, -0.45f, 0.8f, 0.45f, Col.hex(0x7A3A1A)); fe(1.5f, -0.45f, 0.8f, 0.45f, Col.hex(0x7A3A1A))
    }

    private fun fallenLeaves(cx: Int, cy: Int) {
        val autumn = env.season == Season.AUTUMN
        val cols = if (autumn) intArrayOf(Col.hex(0xF08C2C), Col.hex(0xD6452C), Col.hex(0xECC23E), Col.hex(0xC9651F)) else intArrayOf(Col.hex(0x9A6A3A), Col.hex(0x7A5230), Col.hex(0xB08048))
        val n = if (autumn) 34 else if (env.snow) 8 else 16
        for (k in 0 until n) {
            val lx = ((Noise.rnd(k, 51) - 0.5f) * 56).toInt(); val ly = ((Noise.rnd(k, 52) - 0.5f) * 14).toInt()
            val col = cols[k % cols.size]
            if (z > 1) {
                // closer up: a leaf with its midrib and stalk, each lying its own way
                ax = cx; ay = cy
                leafAt(lx + 1f, ly + 0.5f - (if (k % 3 == 0) 0.3f else 0f), 1.1f, 0.5f, (Noise.rnd(k, 53) - 0.5f) * 1.6f, col, Col.scale(col, 0.72f))
                continue
            }
            val x = cx + lx; val y = cy + ly
            c.set(x, y, col); c.set(x + 1, y, Col.scale(col, 0.8f))
            if (k % 3 == 0) c.set(x + 1, y - 1, col)
        }
        if (z > 1 && !env.snow) for (k in 0 until 10) {
            // beechnuts' husks among them
            ax = cx; ay = cy
            val hx = (Noise.rnd(k, 54) - 0.5f) * 50f; val hy = (Noise.rnd(k, 55) - 0.5f) * 12f
            fe(hx, hy, 0.45f, 0.35f, Col.hex(0x6A4A2A)); fd(hx - 0.2f, hy - 0.2f, Col.hex(0x9A7448))
        }
    }

    /**
     * The owl on its branch in the spruce: eyes shining at night, shut by day. Tapped ([pokes]) it opens them wide by day,
     * blinking (at night it blinks slowly); the next time it spreads its wings and folds them again; the third it turns its
     * head and hoots, huu-huu, the calls going out. A dialog's "hoot" has it hoot again and again, its head turned; by
     * itself it hoots now and then in the night.
     */
    private fun owl(bx: Int, by: Int) {
        val brown = Col.hex(0x8A6A48); val belly = Col.hex(0xC8A878); val dark = Col.hex(0x5A4430)
        val bob = if (sin(t * 0.8) > 0.6) 1 else 0
        val pk = poked("owl"); val a = pk?.age(t) ?: 0f
        val open = when {
            pk == null || pk.step == 2 -> env.dark > 0.3f || pk?.step == 2 || fxOn("hoot") > 0.05f
            pk.step == 1 -> a in 0.1f..2.1f || env.dark > 0.3f
            env.dark > 0.3f -> !(a in 0.25f..0.6f || a in 0.9f..1.05f)
            else -> a in 0.15f..1.6f && !(a in 0.55f..0.68f || a in 0.95f..1.08f)
        }
        val spread = if (pk?.step == 1) PokeArt.ease(a, 0.1f, 0.45f) * (1f - PokeArt.ease(a, 1.6f, 2.2f)) else 0f
        // the head turned (0..1) and the hoot's calls going out (0..1 through a call, or none)
        val cue = fxOn("hoot")
        val turn: Float
        val call: Float
        when {
            pk?.step == 2 -> { turn = PokeArt.ease(a, 0.1f, 0.5f) * (1f - PokeArt.ease(a, 2.1f, 2.5f)); call = if (a in 0.6f..2.2f) (a - 0.6f) / 1.6f else -1f }
            cue > 0.05f -> { turn = cue; call = ((t * 0.5) % 1.0).toFloat() }
            env.dark > 0.5f -> { turn = 0f; val ph = ((t / 9.0) % 1.0).toFloat(); call = if (ph < 0.18f) ph / 0.18f else -1f }
            else -> { turn = 0f; call = -1f }
        }
        val y0 = by + bob * z
        if (call in 0f..1f) {
            val bxf = bx + 0.5f * z; val byf = y0 - 7.5f * z
            s.fx { PokeArt.rings(c, bxf, byf, call, 16f * z, Col.hex(0xF4ECD8), z, 0.55f, n = 3) }
        }
        if (spread > 0.05f) owlWings(bx, by + bob * z, spread, brown, dark)
        if (z > 1) { owlCloser(bx, by + bob * z, brown, belly, dark, open, turn); return }
        val y = by + bob
        val f = (turn * 2f + 0.3f).toInt() // the face turned aside, px
        // a plump egg of a body, ear tufts, a pale face disc and a speckled belly
        c.fillEllipse(bx + 0.5f, y - 5.5f, 5f, 6f, brown)
        c.fillEllipse(bx + 0.5f, y - 8f, 4.6f, 3.2f, brown)
        c.set(bx - 4 + f, y - 12, dark); c.set(bx - 3 + f, y - 11, dark); c.set(bx + 4 + f, y - 12, dark); c.set(bx + 3 + f, y - 11, dark)
        c.fillEllipse(bx + 0.5f, y - 3.5f, 3f, 3.5f, belly)
        for (yy in y - 5..y - 1 step 2) for (xx in bx - 2..bx + 2 step 2) c.set(xx, yy, Col.scale(belly, 0.8f))
        c.fillRect(bx - 3 + f, y - 10, 3, 3, Col.hex(0xD8B890)); c.fillRect(bx + 1 + f, y - 10, 3, 3, Col.hex(0xD8B890))
        // eyes: wide open and glowing at night, shut by day
        if (open) glow { c.fillRect(bx - 3 + f, y - 10, 2, 2, Pal.GOLD_L); c.fillRect(bx + 2 + f, y - 10, 2, 2, Pal.GOLD_L); c.set(bx - 2 + f, y - 9, Pal.OUTLINE); c.set(bx + 2 + f, y - 9, Pal.OUTLINE) }
        else { c.hline(bx - 3 + f, bx - 2 + f, y - 9, dark); c.hline(bx + 2 + f, bx + 3 + f, y - 9, dark) }
        c.set(bx + f, y - 8, Pal.GOLD); c.set(bx + f, y - 7, if (call in 0f..1f) Pal.OUTLINE else Col.hex(0xC08A20)) // beak, open as it calls
        c.set(bx - 2, y, Pal.GOLD); c.set(bx + 2, y, Pal.GOLD) // feet
        c.hline(bx - 7, bx + 7, y + 1, Col.hex(0x5E4A30)) // the branch
    }

    /**
     * The owl closer up: folded wings with pale spots, rimmed face discs, round eyes, a hooked beak, a barred belly, talons;
     * its face turned aside by [turn] (0..1).
     */
    private fun owlCloser(bx: Int, y: Int, brown: Int, belly: Int, dark: Int, open: Boolean, turn: Float = 0f) {
        ax = bx; ay = y
        val brownD = Col.hex(0x6E5238); val disc = Col.hex(0xD8B890); val discD = Col.hex(0xA88A62)
        // the branch it sits on
        fr(-7f, 1f, 15f, 1f, Col.hex(0x5E4A30)); fl(-7f, 1f + 0.5f / z, 7.9f, 1f + 0.5f / z, 0.1f, Col.hex(0x7A6242))
        fl(5.5f, 1.5f, 7.6f, 0.2f, 0.35f, Col.hex(0x5E4A30))
        // a plump egg of a body, its wings folded down the sides
        fe(0.5f, -5.5f, 5f, 6f, brown)
        fe(0.5f, -8f, 4.6f, 3.2f, brown)
        fe(-3.1f, -4f, 1.6f, 3.8f, brownD); fe(4.1f, -4f, 1.6f, 3.8f, brownD)
        for (j in 0..2) { fd(-3.2f, -6f + j * 1.7f, belly); fd(4.2f, -5.2f + j * 1.7f, belly) }
        // the belly, barred with little chevrons
        fe(0.5f, -3.5f, 3f, 3.5f, belly)
        for (yy in -5..-1 step 2) for (xx in -2..2 step 2) chevron(xx + 0.5f, yy + 0.5f, Col.scale(belly, 0.78f))
        // the head's features, turned aside as it hoots
        ax = bx + (turn * 1.6f * z).toInt()
        // ear tufts
        fl(-2.4f, -10f, -3.6f, -12.3f, 0.7f, dark); fl(3.4f, -10f, 4.6f, -12.3f, 0.7f, dark)
        // the face discs, rimmed, a brow between them
        fe(-1.5f, -8.5f, 1.85f, 1.75f, discD); fe(2.5f, -8.5f, 1.85f, 1.75f, discD)
        fe(-1.5f, -8.5f, 1.55f, 1.45f, disc); fe(2.5f, -8.5f, 1.55f, 1.45f, disc)
        fl(0.5f, -10.2f, 0.5f, -8.4f, 0.35f, brownD)
        if (open) glow {
            fe(-2f, -9f, 1f, 1f, Pal.GOLD_L); fe(3f, -9f, 1f, 1f, Pal.GOLD_L)
            fe(-1.6f, -8.7f, 0.5f, 0.5f, Pal.OUTLINE); fe(2.6f, -8.7f, 0.5f, 0.5f, Pal.OUTLINE)
            fd(-2.2f, -9.4f, Col.hex(0xFFFFFF)); fd(2.8f, -9.4f, Col.hex(0xFFFFFF))
        } else {
            // shut: a curved lid each
            fl(-3f, -9f, -2f, -8.65f, 0.3f, dark); fl(-2f, -8.65f, -1f, -9f, 0.3f, dark)
            fl(2f, -9f, 3f, -8.65f, 0.3f, dark); fl(3f, -8.65f, 4f, -9f, 0.3f, dark)
        }
        fp(Pal.GOLD, 0.05f, -8.3f, 0.95f, -8.3f, 0.5f, -6.4f); fd(0.55f, -6.8f, Col.hex(0xC08A20))
        ax = bx
        // talons on the branch
        for (tx in floatArrayOf(-1.5f, 2.5f)) { fe(tx, 0.45f, 0.85f, 0.5f, Pal.GOLD); fd(tx - 0.6f, 0.8f, dark); fd(tx + 0.5f, 0.8f, dark) }
    }

    /** The owl's wings spread out from its sides, [w] of the way (0 folded, 1 wide), the owl's feet at ([bx], [y]); drawn before its body. */
    private fun owlWings(bx: Int, y: Int, w: Float, brown: Int, dark: Int) {
        ax = bx; ay = y
        for (s in intArrayOf(-1, 1)) {
            val sx = 0.5f + s * 3.5f; val tip = 0.5f + s * (4f + 7f * w)
            fp(dark, sx, -9f, tip, -10f - 3f * w, tip + s * 0.5f, -6f, 0.5f + s * (3.5f + 5f * w), -1.5f, sx, -2f)
            // the flight feathers, lighter bars fanning out
            for (k in 0..2) fl(sx + s * (1f + k * 1.7f) * w, -9f - k * 0.7f * w, sx + s * (0.8f + k * 1.5f) * w, -2.6f, 0.5f, brown)
        }
    }

    /** A little bar in a V round ([x], [y]), three picture pixels wide. */
    private fun chevron(x: Float, y: Float, col: Int) {
        val px = floor(fx(x)).toInt(); val py = floor(fy(y)).toInt()
        c.set(px - 1, py - 1, col); c.set(px, py, col); c.set(px + 1, py - 1, col)
        if (z >= 3) { c.set(px - 1, py, col); c.set(px + 1, py, col) }
    }

    private fun boulder(cx: Int, by: Int) {
        if (z > 1) { boulderCloser(cx, by); return }
        for (y in by - 14 until by) {
            val k = (y - (by - 14)).toFloat() / 14f
            val half = (12 * sqrt(1f - (1 - k * 1.15f).let { it * it }.coerceIn(0f, 1f)) + 1).toInt()
            c.hline(cx - half, cx + half, y, Pal.STONE_M)
            c.set(cx - half, y, Pal.STONE_L); c.set(cx + half, y, Pal.STONE_D); c.set(cx + half - 1, y, Pal.STONE_D)
            if (Noise.rnd(cx + y, 3) < 0.4f) c.set(cx - half + 2 + (Noise.rnd(y, 4) * 6).toInt(), y, Pal.STONE_L)
        }
        c.hline(cx - 8, cx + 8, by - 1, Pal.STONE_X)
        // moss on the shaded top and left
        val moss = if (env.snow) Pal.SNOW_L else Col.hex(0x5C9A48); val mossD = if (env.snow) Pal.SNOW_M else Col.hex(0x3E7A34)
        for (y in by - 14 until by - 6) for (x in cx - 10..cx + 4) {
            if (c.get(x, y) == Pal.STONE_M || c.get(x, y) == Pal.STONE_L) if (Noise.v2(x * 0.3f, y * 0.4f, 12) > 0.45f) c.set(x, y, if (Noise.rnd(x, y, 13) < 0.3f) mossD else moss)
        }
        c.hline(cx - 6, cx + 2, by - 14, moss)
    }

    /** The boulder closer up: rounded and lit from above, cracks and lichen on it, moss in tufts over its top. */
    private fun boulderCloser(cx: Int, by: Int) {
        val z = z
        val moss = if (env.snow) Pal.SNOW_L else Col.hex(0x5C9A48); val mossD = if (env.snow) Pal.SNOW_M else Col.hex(0x3E7A34)
        val mossL = if (env.snow) Col.hex(0xFFFFFF) else Col.hex(0x86C060)
        val top = by - 14 * z
        for (y in max(top, c.top) until min(by, c.bottom)) {
            val k = (y - top + 0.5f) / (14f * z)
            val half = 12f * z * sqrt(1f - (1 - k * 1.15f).let { it * it }.coerceIn(0f, 1f)) + z
            for (x in max((cx - half).toInt(), c.left)..min((cx + half).toInt(), c.right - 1)) {
                val nx = (x + 0.5f - cx) / half
                val lit = -nx * 0.55f - (1f - k) * 0.5f + 0.2f + (Noise.v2(x * 0.3f / z, y * 0.3f / z, 3) - 0.5f) * 0.5f + (Dither.at(x, y) - 0.5f) * 0.2f
                var col = if (nx > 0.86f) Pal.STONE_D else if (lit > 0.35f) Pal.STONE_L else if (lit > -0.3f) Pal.STONE_M else Pal.STONE_D
                if (k > 0.93f && abs(x + 0.5f - cx) < 8.5f * z) col = Pal.STONE_X
                // cracks and lichen
                if (abs(Noise.v2(x * 0.09f / z, y * 0.18f / z, 17) - 0.5f) < 0.02f && k > 0.3f) col = Pal.STONE_X
                else if (!env.snow && Noise.v2(x * 0.6f / z, y * 0.6f / z, 18) > 0.8f) col = Col.hex(0xB4B27A)
                // moss on the shaded top and left, the scene canvas's pattern
                if (y < by - 6 * z && x <= cx + 4 * z && x >= cx - 10 * z && Noise.v2(x * 0.3f / z, y * 0.4f / z, 12) > 0.45f)
                    col = if (Noise.v2(x * 0.8f / z, y * 0.8f / z, 13) < 0.35f) mossD else if (Noise.rnd(x, y, 19) < 0.12f) mossL else moss
                c.set(x, y, col)
            }
        }
        // tufts of moss over the top
        for (x in max(cx - 6 * z, c.left - z)..min(cx + 2 * z + z - 1, c.right + z)) {
            val tall = 1 + (Noise.rnd(x, 20) * (z + 1)).toInt()
            for (j in 0 until tall) c.set(x, top + z / 2 - j, if (j == tall - 1) mossL else moss)
        }
    }

    private fun blueberries(cx: Int, by: Int) {
        if (z > 1) { blueberriesCloser(cx, by); return }
        if (env.snow) {
            c.line(cx, by, cx - 5, by - 9, Col.hex(0x6A5244)); c.line(cx, by, cx + 5, by - 8, Col.hex(0x6A5244)); c.line(cx, by, cx + 1, by - 11, Col.hex(0x6A5244))
            c.set(cx - 5, by - 10, Pal.SNOW_L); c.set(cx + 5, by - 9, Pal.SNOW_L); c.set(cx + 1, by - 12, Pal.SNOW_L)
            return
        }
        val leafD = Col.hex(0x2E6A2C); val leaf = Col.hex(0x3E8A3A); val leafL = Col.hex(0x5AAA48)
        val autumn = env.season == Season.AUTUMN
        for (y in by - 13 until by) for (x in cx - 13..cx + 13) {
            val dx = (x + 0.5f - cx) / 13f; val dy = (y + 0.5f - by + 5) / 8f
            if (dx * dx + dy * dy > 1f) continue
            val v = -dx * 0.4f - dy * 0.6f + (Noise.rnd(x, y, 21) - 0.5f) * 0.7f
            var col = if (v > 0.5f) leafL else if (v > -0.1f) leaf else leafD
            if (autumn) col = Col.mix(col, Col.hex(0xC0402A), 0.55f)
            c.set(x, y, col)
        }
        for (k in 0 until 11) {
            val x = cx - 10 + (Noise.rnd(k, 22) * 20).toInt(); val y = by - 11 + (Noise.rnd(k, 23) * 8).toInt()
            c.set(x, y, Col.hex(0x3A4A9A)); c.set(x + 1, y, Col.hex(0x2A3A7A)); c.set(x, y - 1, Col.hex(0x7A88C8))
        }
    }

    /** The blueberry bush closer up: small oval leaves, round berries with their bloom and a little crown. */
    private fun blueberriesCloser(cx: Int, by: Int) {
        val z = z
        ax = cx; ay = by
        if (env.snow) {
            val twig = Col.hex(0x6A5244)
            fl(0.5f, 0f, -4.5f, -9f, 0.45f, twig); fl(0.5f, 0f, 5.5f, -8f, 0.45f, twig); fl(0.5f, 0f, 1.5f, -11f, 0.45f, twig)
            fl(-2f, -4.5f, -3.8f, -5.5f, 0.3f, twig); fl(3f, -4f, 4.8f, -4.6f, 0.3f, twig)
            fe(-4.5f, -9.6f, 0.9f, 0.5f, Pal.SNOW_L); fe(5.5f, -8.6f, 0.9f, 0.5f, Pal.SNOW_L); fe(1.5f, -11.6f, 0.9f, 0.5f, Pal.SNOW_L)
            return
        }
        val leafD = Col.hex(0x2E6A2C); val leaf = Col.hex(0x3E8A3A); val leafL = Col.hex(0x5AAA48)
        val autumn = env.season == Season.AUTUMN
        val ex = fx(0f); val ey = fy(-5f)
        for (y in max(floor(fy(-13f)).toInt(), c.top) until min(by, c.bottom)) for (x in max(floor(fx(-13f)).toInt(), c.left)..min(floor(fx(14f)).toInt(), c.right - 1)) {
            val dx = (x + 0.5f - ex) / (13f * z); val dy = (y + 0.5f - ey) / (8f * z)
            val q = dx * dx + dy * dy
            if (q > 1.2f) continue
            val lf = clusters(x, y, 0.8f, 24)
            if (q > 1f + lf * 0.3f) continue
            val v = -dx * 0.4f - dy * 0.6f + lf * 0.9f + (Noise.rnd(x, y, 21) - 0.5f) * 0.15f
            var col = if (v > 0.5f) leafL else if (v > -0.1f) leaf else leafD
            if (autumn) col = Col.mix(col, Col.hex(0xC0402A), 0.55f)
            c.set(x, y, col)
        }
        for (k in 0 until 11) {
            val x = -10 + (Noise.rnd(k, 22) * 20).toInt(); val y = -11 + (Noise.rnd(k, 23) * 8).toInt()
            berry(x + 1f, y.toFloat())
        }
    }

    /** A round berry centred on the corner ([x], [y]) of the scene's pixels: its bloom lit, its underside dark. */
    private fun berry(x: Float, y: Float) {
        fe(x, y, 0.64f, 0.64f, Col.hex(0x3A4A9A))
        fd(x + 0.4f, y + 0.4f, Col.hex(0x222E66)); fd(x - 0.1f, y + 0.4f, Col.hex(0x2A3A7A))
        fd(x - 0.3f, y - 0.3f, Col.hex(0x8A98D4))
    }

    private fun sheep(cx: Int, by: Int) {
        val wool = if (env.snow) Col.hex(0xECE6DA) else Col.hex(0xF4F0E4); val woolD = Col.hex(0xCFC8B8); val face = Col.hex(0x2E2A2C)
        val bob = if (sin(t * 1.9) > 0.3) 0 else 1
        if (z > 1) { sheepCloser(cx, by, bob.toFloat(), wool, woolD, face); return }
        c.fillRect(cx - 7, by - 10, 15, 8, wool)
        for (x in cx - 7..cx + 7 step 3) { c.set(x, by - 11, wool); c.set(x, by - 2, woolD) }
        for (y in by - 9..by - 4 step 3) { c.set(cx - 8, y, wool); c.set(cx + 8, y, woolD) }
        c.hline(cx - 6, cx + 6, by - 3, woolD); c.vline(cx + 7, by - 9, by - 4, woolD)
        c.fillRect(cx - 5, by - 2, 2, 2, face); c.fillRect(cx - 1, by - 2, 2, 2, face); c.fillRect(cx + 4, by - 2, 2, 2, face)
        // head
        c.fillRect(cx + 7, by - 12 + bob, 5, 6, face); c.set(cx + 8, by - 13 + bob, wool); c.set(cx + 11, by - 13 + bob, wool)
        c.set(cx + 10, by - 10 + bob, Col.hex(0xFFFFFF)); c.set(cx + 11, by - 8 + bob, Col.hex(0xE0A0A0))
    }

    /** The sheep closer up: a fleece of curls with a scalloped edge, thin black legs, a black face with ears and a woolly top-knot. */
    private fun sheepCloser(cx: Int, by: Int, bob: Float, wool: Int, woolD: Int, face: Int) {
        val z = z
        ax = cx; ay = by
        val woolX = Col.mix(woolD, Col.hex(0x9A9080), 0.4f)
        // legs, hooves
        for (lx in floatArrayOf(-4f, 0f, 5f)) { fr(lx - 0.8f, -2.5f, 1.4f, 2.5f, face); fr(lx - 0.8f, -0.5f, 1.4f, 0.5f, Col.hex(0x18161A)) }
        // the fleece: curls, lighter on top, a scalloped edge
        val ex = fx(0.5f); val ey = fy(-6f)
        for (y in max(floor(fy(-11.6f)).toInt(), c.top)..min(floor(fy(-1.4f)).toInt(), c.bottom - 1)) for (x in max(floor(fx(-8.6f)).toInt(), c.left)..min(floor(fx(9.4f)).toInt(), c.right - 1)) {
            val dx = (x + 0.5f - ex) / (8.2f * z); val dy = (y + 0.5f - ey) / (4.9f * z)
            val q = dx * dx + dy * dy
            val ang = kotlin.math.atan2(dy, dx)
            val scallop = 1f + 0.12f * abs(sin(ang * 7f))
            if (q > scallop * scallop * 0.86f) continue
            // curls: little rings, their lower right in shade
            val s = z + 2
            val gxc = Math.floorDiv(x + (Math.floorDiv(y, s) and 1) * (s / 2), s); val gyc = Math.floorDiv(y, s)
            val lx = x + (gyc and 1) * (s / 2) - gxc * s + 0.5f - s / 2f; val ly = y - gyc * s + 0.5f - s / 2f
            val ring = sqrt(lx * lx + ly * ly) / (s * 0.5f)
            val curl = ring in 0.55f..1.05f && (lx + ly) > -0.3f * s
            val lit = -dx * 0.35f - dy * 0.75f
            var col = if (lit > 0.1f) wool else if (lit > -0.45f) Col.mix(wool, woolD, 0.5f) else woolD
            if (curl) col = if (lit > 0.1f) woolD else woolX
            c.set(x, y, col)
        }
        // the head: a black face, ears out to the sides, a woolly top-knot, an eye, a pink nose
        fe(7.5f, -10.4f + bob, 1.2f, 0.55f, face) // ear, back
        fe(9.5f, -8.9f + bob, 2.3f, 2.9f, face)
        fe(10.1f, -6.6f + bob, 1.5f, 1.2f, face)
        fe(12.2f, -10.4f + bob, 1.2f, 0.55f, face) // ear, front
        fe(9.6f, -11.7f + bob, 1.6f, 0.9f, wool); fd(9f, -12.4f + bob, woolD); fd(10.3f, -12.2f + bob, woolD)
        fe(10.5f, -9.5f + bob, 0.55f, 0.5f, Col.hex(0xFFFFFF)); fd(10.6f, -9.5f + bob, Col.hex(0x18161A))
        fe(11.4f, -7.4f + bob, 0.45f, 0.35f, Col.hex(0xE0A0A0))
        fd(9.3f, -9.9f + bob, Col.hex(0x4A4448))
    }

    private fun flowers(cx: Int, by: Int) {
        if (z > 1) { flowersCloser(cx, by); return }
        if (env.snow) {
            for (k in 0..4) { val x = cx - 8 + k * 4; c.vline(x, by - 6 - (k % 2) * 2, by - 1, Col.hex(0x8A7A5A)); c.set(x, by - 7 - (k % 2) * 2, Pal.SNOW_L) }
            return
        }
        val heads = intArrayOf(Col.hex(0xFFFFFF), Pal.GOLD, Col.hex(0x6A7AD8), Col.hex(0xF4B8CC), Col.hex(0xFFFFFF))
        for (k in 0..4) {
            val x = cx - 10 + k * 5 + (k % 2); val hgt = 7 + (k * 3) % 5
            c.vline(x, by - hgt, by - 1, Pal.LEAF); c.set(x - 1, by - 3 - (k % 3), Pal.LEAF)
            val col = heads[k]
            c.set(x - 1, by - hgt - 1, col); c.set(x + 1, by - hgt - 1, col); c.set(x, by - hgt - 2, col); c.set(x, by - hgt, col)
            c.set(x, by - hgt - 1, if (col == Pal.GOLD) Col.hex(0xC06A1A) else Pal.GOLD)
        }
    }

    /** The wildflowers closer up: thin stalks with leaves, heads of five petals round a heart. */
    private fun flowersCloser(cx: Int, by: Int) {
        ax = cx; ay = by
        if (env.snow) {
            for (k in 0..4) { val x = -8f + k * 4 + 0.5f; val top = -6f - (k % 2) * 2; fl(x, 0f, x, top, 0.35f, Col.hex(0x8A7A5A)); fe(x, top - 0.5f, 0.6f, 0.45f, Pal.SNOW_L) }
            return
        }
        val heads = intArrayOf(Col.hex(0xFFFFFF), Pal.GOLD, Col.hex(0x6A7AD8), Col.hex(0xF4B8CC), Col.hex(0xFFFFFF))
        val stalk = Pal.LEAF; val stalkD = Col.scale(Pal.LEAF, 0.75f)
        for (k in 0..4) {
            val x = -10f + k * 5 + (k % 2) + 0.5f; val hgt = 7 + (k * 3) % 5
            val hx = x + (if (k % 2 == 0) 0.3f else -0.3f); val hy = -hgt - 0.5f
            fl(x, 0f, hx, hy, 0.35f, stalk)
            // a leaf or two on the stalk
            leafAt(x - 0.9f, -2.5f - (k % 3), 0.9f, 0.35f, -0.5f, stalk, stalkD)
            if (k % 2 == 1) leafAt(x + 0.9f, -4.5f, 0.8f, 0.3f, 0.5f, stalk, stalkD)
            val col = heads[k]
            val shade = Col.mix(col, Col.hex(0x6A5A60), 0.25f)
            for (p in 0 until 5) {
                val a = p * 1.2566f - 1.5708f + k
                fe(hx + cos(a) * 0.8f, hy + sin(a) * 0.8f, 0.6f, 0.55f, if (sin(a) > 0.3f) shade else col)
            }
            fe(hx, hy, 0.45f, 0.45f, if (col == Pal.GOLD) Col.hex(0xC06A1A) else Pal.GOLD)
        }
    }

    private fun mushrooms(cx: Int, by: Int) {
        if (z > 1) { mushroomsCloser(cx, by); return }
        fun agaric(x: Int, y: Int, r: Int) {
            c.fillRect(x - r / 3, y - r - 1, r * 2 / 3 + 1, r + 1, Col.hex(0xF4EAD8)); c.vline(x + r / 3, y - r, y - 1, Col.hex(0xD8CCB4))
            c.fillEllipse(x + 0.5f, y - r - 1.5f, r.toFloat() + 0.5f, r * 0.62f + 0.5f, Pal.FLAG_RED)
            c.fillEllipse(x + 0.5f, y - r - 1.5f, r.toFloat() + 0.5f, r * 0.62f + 0.5f, Pal.FLAG_RED)
            c.hline(x - r + 1, x + r - 1, y - r - 1 + (r * 0.62f).toInt(), Col.hex(0xB01E28))
            for (k in 0 until r) { val px = x - r + 1 + (Noise.rnd(k, x) * (2 * r - 2)).toInt(); val py = y - r - 3 + (Noise.rnd(k, y) * r * 0.9f).toInt(); c.set(px, py, Col.hex(0xFFFFFF)) }
            if (env.snow) c.hline(x - r / 2, x + r / 2, y - r - 1 - (r * 0.62f).toInt(), Pal.SNOW_L)
        }
        agaric(cx, by, 7)
        agaric(cx + 10, by + 2, 4)
        // a boletus
        c.fillRect(cx - 12, by - 5, 4, 5, Col.hex(0xE8D8B8)); c.fillEllipse(cx - 9.5f, by - 5.5f, 4.5f, 2.5f, Col.hex(0x8A5A32)); c.hline(cx - 12, cx - 8, by - 7, Col.hex(0xA87444))
    }

    /** The mushrooms closer up: fly agarics with a ringed stalk, gills under a domed cap and round white warts; a boletus with its sponge. */
    private fun mushroomsCloser(cx: Int, by: Int) {
        val z = z
        ax = cx; ay = by
        val stem = Col.hex(0xF4EAD8); val stemD = Col.hex(0xD8CCB4)
        fun agaric(x: Float, y: Float, r: Int, seed: Int) {
            val sl = x - r / 3; val sw = (r * 2 / 3 + 1).toFloat()
            // the stalk: a bulb at its foot, a ring under the cap, shade down its right side
            fe(sl + sw / 2f, y - 0.7f, sw / 2f + 0.5f, 0.8f, stem)
            fr(sl, y - r - 1, sw, (r + 1).toFloat(), stem)
            fr(sl + sw - 1f, y - r, 1f, r.toFloat(), stemD)
            fl(sl + 0.3f, y - r * 0.5f, sl + 0.3f, y - 1.2f, 0.2f, stemD)
            fe(sl + sw / 2f, y - r * 0.62f, sw / 2f + 0.6f, 0.45f, stem); fl(sl - 0.3f, y - r * 0.62f + 0.4f, sl + sw + 0.3f, y - r * 0.62f + 0.4f, 0.15f, stemD)
            // the cap, domed and lit, its rim darker; gills under it
            val cyc = y - r - 1.5f; val rx = r + 0.5f; val ry = r * 0.62f + 0.5f
            fe(x + 0.5f, cyc, rx, ry, Col.hex(0xB01E28))
            fe(x + 0.3f, cyc - 0.25f, rx - 0.35f, ry - 0.35f, Pal.FLAG_RED)
            fe(x - r * 0.25f, cyc - ry * 0.35f, rx * 0.45f, ry * 0.35f, Col.hex(0xFF4A4A))
            val gill = y - r - 1 + (r * 0.62f).toInt()
            fr(x - r + 1, gill, (2 * r - 1).toFloat(), 1f, Col.hex(0xB01E28))
            if (z >= 2) for (gxp in floor(fx(x - r + 1.5f)).toInt()..floor(fx(x + r - 0.5f)).toInt() step 2) c.vline(gxp, floor(fy(gill + 0.35f)).toInt(), floor(fy(gill + 0.99f)).toInt(), Col.hex(0xF0DCC8))
            // warts: round, white, a shadow under each
            for (k in 0 until r + 2) {
                val wx = x - r + 1.5f + Noise.rnd(k, seed) * (2 * r - 2); val wy = y - r - 2.5f + Noise.rnd(k, seed + 1) * r * 0.9f
                val ddx = (wx - x - 0.5f) / rx; val ddy = (wy - cyc) / ry
                if (ddx * ddx + ddy * ddy > 0.8f || wy > gill - 0.3f) continue
                fe(wx, wy + 0.25f, 0.42f, 0.32f, Col.hex(0xC01E2A)); fe(wx, wy, 0.4f, 0.32f, Col.hex(0xFFFFFF))
            }
            if (env.snow) fe(x + 0.5f, y - r - 1 - (r * 0.62f).toInt() + 0.3f, r / 2f + 0.5f, 0.6f, Pal.SNOW_L)
        }
        agaric(0f, 0f, 7, 57)
        agaric(10f, 2f, 4, 59)
        // a boletus: a stout netted stalk, a brown cap lit on top, the yellow sponge under it
        val brown = Col.hex(0x8A5A32)
        fe(-10f, -1.2f, 2.3f, 1.3f, Col.hex(0xE8D8B8))
        fr(-12f, -5f, 4f, 4f, Col.hex(0xE8D8B8)); fr(-9f, -4.5f, 1f, 4.3f, Col.hex(0xCDBB98))
        for (k in 0 until 6) fd(-11.6f + (k % 3) * 1.2f, -4.2f + (k / 3) * 1.5f, Col.hex(0xC8B08A))
        fe(-9.5f, -5.2f, 4.5f, 1f, Col.hex(0xD8C060))
        fe(-9.5f, -5.9f, 4.5f, 2.3f, brown)
        fe(-10.2f, -6.8f, 2.8f, 1.2f, Col.hex(0xA87444))
        fd(-11.2f, -7.3f, Col.hex(0xC89060))
    }

    private fun deer(bx: Int, by: Int) {
        val coat = Col.hex(0xA8784A); val coatL = Col.hex(0xC89A68); val coatD = Col.hex(0x7A5230); val dark = Col.hex(0x3A2418)
        val graze = sin(t * 0.6) > 0.2
        if (z > 1) { deerCloser(bx, by, graze, coat, coatL, coatD, dark); return }
        // legs
        for (lx in intArrayOf(bx - 8, bx - 5, bx + 4, bx + 7)) { c.vline(lx, by - 8, by - 1, coatD); c.set(lx, by - 1, dark) }
        // body
        c.fillRect(bx - 10, by - 16, 20, 9, coat); c.hline(bx - 9, bx + 8, by - 16, coatL); c.hline(bx - 9, bx + 8, by - 8, coatD)
        c.fillRect(bx - 6, by - 9, 10, 2, coatL)
        c.set(bx + 10, by - 14, Col.hex(0xF4EAD8)); c.set(bx + 10, by - 13, Col.hex(0xF4EAD8)) // tail
        // neck and head, down when grazing
        if (graze) {
            c.line(bx - 10, by - 15, bx - 16, by - 8, coat); c.line(bx - 9, by - 15, bx - 15, by - 7, coat); c.line(bx - 11, by - 14, bx - 17, by - 8, coatD)
            c.fillRect(bx - 20, by - 8, 6, 4, coat); c.set(bx - 20, by - 5, dark); c.set(bx - 17, by - 8, dark)
            c.set(bx - 16, by - 10, coatD); c.set(bx - 14, by - 10, coatD)
        } else {
            c.fillRect(bx - 13, by - 24, 4, 9, coat); c.vline(bx - 13, by - 24, by - 16, coatD)
            c.fillRect(bx - 18, by - 26, 8, 4, coat); c.set(bx - 18, by - 24, dark); c.set(bx - 14, by - 26, dark)
            c.set(bx - 12, by - 28, coatD); c.set(bx - 10, by - 28, coatD); c.set(bx - 11, by - 27, coatD)
            c.set(bx - 15, by - 25, Col.hex(0x2A1D1A))
        }
    }

    /**
     * The deer closer up (a roe doe): a rounded body lit along its back, a pale belly and the white rump, slender
     * legs with a bend at the hock and dark hooves, a long neck, big ears, an eye with a glint, the dark nose.
     */
    private fun deerCloser(bx: Int, by: Int, graze: Boolean, coat: Int, coatL: Int, coatD: Int, dark: Int) {
        ax = bx; ay = by
        val cream = Col.hex(0xF4EAD8)
        // the far legs first, darker
        leg(-5.5f, true, Col.scale(coatD, 0.85f), dark); leg(7.5f, false, Col.scale(coatD, 0.85f), dark)
        // the body: lit along the back, shaded under, a pale belly, the white rump
        fe(0f, -11.4f, 9.9f, 4.4f, coatD)
        fe(0f, -11.9f, 10.1f, 4.6f, coatL)
        fe(0f, -11.6f, 10f, 4.4f, coat)
        fe(-1f, -8.7f, 6f, 1.1f, coatL)
        fe(-6.6f, -12.4f, 2.4f, 3f, Col.mix(coat, coatD, 0.35f)); fe(-6.9f, -12.9f, 2.2f, 2.7f, coat) // the shoulder
        fe(7.2f, -9.6f, 2.6f, 2.6f, coat)
        fe(9.4f, -12.8f, 1.1f, 1.9f, cream)
        fd(10.1f, -14.2f, coatD)
        // the near legs
        leg(-7.5f, true, coatD, dark); leg(4.5f, false, coatD, dark)
        if (graze) {
            // the neck bent down to the grass, the head low
            fp(coat, -10.3f, -15.6f, -7.8f, -13.4f, -14.3f, -6.4f, -16.8f, -8.6f)
            fl(-10.6f, -14.3f, -16.3f, -7.9f, 0.3f, coatD)
            fe(-16.4f, -6.4f, 2.1f, 1.8f, coat)
            fp(coat, -17.6f, -7.6f, -17.2f, -5.2f, -20.4f, -4.2f, -20.4f, -5.6f)
            fe(-20f, -4.9f, 0.55f, 0.5f, dark); fd(-19.3f, -4.2f, cream)
            fe(-16.4f, -7.2f, 0.4f, 0.4f, dark); fd(-16.6f, -7.4f, Col.hex(0xFFFFFF))
            ear(-15.8f, -8f, -16.6f, -10.3f, coat, coatD); ear(-14.6f, -8f, -13.8f, -10.3f, coat, coatD)
        } else {
            // the neck up, the head raised and watching
            fp(coat, -10.4f, -15.6f, -8.4f, -14.8f, -9.4f, -23.4f, -12.6f, -24.2f)
            fl(-12.7f, -23.6f, -10.4f, -15.4f, 0.35f, coatD)
            fe(-14f, -24.1f, 2.3f, 1.8f, coat)
            fp(coat, -15.4f, -25.2f, -15.4f, -22.8f, -18.4f, -23.2f, -18.4f, -24.4f)
            fe(-18f, -23.8f, 0.55f, 0.5f, dark); fd(-17.4f, -23f, cream)
            fe(-14.6f, -24.6f, 0.4f, 0.4f, dark); fd(-14.8f, -24.8f, Col.hex(0xFFFFFF))
            ear(-12.4f, -25.4f, -12.2f, -28.1f, coat, coatD); ear(-10.9f, -25.2f, -9.7f, -27.8f, coat, coatD)
        }
    }

    /** A deer's leg at [x]: a front leg straight down, a hind leg with its hock bent back; a dark hoof. */
    private fun leg(x: Float, front: Boolean, col: Int, hoof: Int) {
        if (front) fp(col, x - 0.45f, -8.5f, x + 0.45f, -8.5f, x + 0.3f, -0.8f, x - 0.35f, -0.8f)
        else { fp(col, x - 0.6f, -8.8f, x + 0.6f, -8.8f, x + 0.55f, -4.2f, x - 0.25f, -4.2f); fp(col, x - 0.25f, -4.4f, x + 0.55f, -4.4f, x + 0.25f, -0.8f, x - 0.4f, -0.8f) }
        fr(x - 0.45f, -0.9f, 0.85f, 0.9f, hoof)
    }

    /** A deer's big ear from its base ([x0], [y0]) to its tip, pale inside. */
    private fun ear(x0: Float, y0: Float, x1: Float, y1: Float, col: Int, inner: Int) {
        fp(col, x0 - 0.55f, y0, x0 + 0.55f, y0, x1 + 0.2f, y1, x1 - 0.2f, y1)
        fl(x0, y0 - 0.2f, (x0 + x1) / 2f, (y0 + y1) / 2f, 0.2f, inner)
    }
}
