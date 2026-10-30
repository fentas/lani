package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.Season
import si.lanisce.lani.game.scene.Poke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Pri vratih: the palisade's gate from inside the village, a painted vista (companion/SCENES.md "Arts"). The palisade of
 * pointed stakes runs left and right across the view, the bark on them, bound with withies; in the middle the gate between two
 * stout posts under its lintel, its two wings standing open by day and shut at night, barred with a heavy beam (zapah); the
 * road runs out through it over the meadow into the forest, the hills beyond, and over the stakes' tops the meadow and the
 * forest's edge show. Inside by the gate a bench, the water trough of a hollowed log, a new stake leaning ready. Its people
 * stand in the gate, on the bench and out on the road; with the gate shut the one from the road is in for the night, just
 * inside it.
 *
 * It grows with the palisade's level, as the map's palisade does ([si.lanisce.lani.game.scene.SceneFixtures]): at the first
 * the wings are poles lashed together; from the second a torch burns on each post (lit from dusk) and the wings are of
 * planks; from the third a little shingle roof over the gate and a pennant on its ridge (blue, red at the fourth, gold at the
 * fifth, as the map's), the stakes' points fire-hardened, charred black; from the fourth a walkway along the inside of the
 * stakes with its ladder, iron bands on the wings; at the fifth a footing of dry stones along the stakes' feet, a bell under
 * the gate's roof and the lintel carved with a sun.
 *
 * The seasons: the meadow's flowers in spring and summer, the forest gold in October and bare and white in winter, snow on the
 * stakes' points; the wolves' eyes shine in the forest at night while the village's wolves event is on.
 *
 * Its effects ([si.lanisce.lani.game.scene.SceneArt.effects]):
 * - `gate`: the wings: 0 shut and barred, 1 wide open (swung in against the inside). Not set: open by day, shut at night.
 * - `torch`: the torches on the posts: 0 out, 1 lit (from the second level). Not set: lit from dusk.
 * - `wolves`: eyes shining at the forest's edge at night, grey wolves slinking out of the trees onto the meadow before
 *   them by day (how many: up to five).
 * - `flock`: Luka's flock (as many as the dialog counts, else twelve): to 0.5 they go out through the gate and down the road
 *   to the meadow, from 0.5 they come home through it, 1 all in.
 * - `pedlar`: the pedlar under his krošnja coming up the road out of the forest to the gate (how far: 1 in at the gate).
 * - `bell`: the bell under the gate's roof swinging and ringing (how hard, at the fifth level).
 *
 * Tapped ([pokes]): the bell rings once, then twice (at the fifth level; a knock on the gate's post before it).
 *
 * Closer up ([detail] 2 and 3): the bark and the withies, the planks' grain and the iron bands, the shingles, the torches'
 * flames, the grass of the meadow, the sheep's wool.
 *
 * Friuli's gate ([look]; companion/SCENES.md "Culture looks"): the land beyond is Friuli's: Lombardy poplars along the road,
 * the vines strung between pollarded mulberries over the meadow, the campanile of a village on the far hill.
 */
internal class PalisadePainter : VistaPainter() {
    override val art = "palisade"
    override val pokes = listOf(Poke("bell", listOf(2.0, 2.8), rest = 3.0))
    override val horizon = 52

    /** North over the gate toward the forest: the Plough over it at night. */
    override val skyFacing = 10.0

    // the palisade's plane at depth PD (m), its stakes ST wide and SH tall; the gate between its posts at ±GX, the lintel LZ
    private val pd = 14f; private val st = 0.34f; private val sh = 3.15f
    private val gxp = 1.75f; private val postH = 4.3f; private val lz = 3.85f
    private val wingH = 2.95f
    // the forest's edge, the road's far end
    private val forestD = 125f

    // the bench and the trough inside on the left, the spare stake on the right, the walkway and its ladder (level 4)
    private val benchX0 = -6.6f; private val benchX1 = -4.8f; private val benchD = 13.3f
    private val troughX0 = -4.2f; private val troughX1 = -2.6f; private val troughD = 12.4f
    private val stakeX = 3.0f; private val stakeD = 13.6f
    private val walkH = 2.0f; private val walkD = 13.6f
    private val ladderX = 5.2f

    // the people: in the gateway, by the bench, out on the road
    private val gatePX = -0.85f; private val gatePD = 13.1f
    private val benchPX = -5.0f; private val benchPD = 12.0f
    private val roadPX = 0.95f; private val roadPD = 16.4f
    /** The road's spot with the gate shut: just inside it, right of the way in. */
    private val roadInPX = 1.45f; private val roadInPD = 12.2f

    private val barkL = Col.hex(0x8A6A4C); private val barkM = Col.hex(0x6E5238); private val barkD = Col.hex(0x4E3A28)
    private val woodL = Col.hex(0xC49464); private val woodM = Col.hex(0xA27646); private val woodD = Col.hex(0x7A5430)
    private val withy = Col.hex(0xA89060)
    private val ironC = Col.hex(0x2E2A2C); private val ironL = Col.hex(0x5E5A5C)

    /** The palisade's level (1 … 5): the one the scene is seen in ([si.lanisce.lani.game.scene.SceneWorld.level]). */
    private var lv = 5

    /** Friuli's land beyond ([readLook2]). */
    private var friuli = false

    private val z: Int get() = detail
    private val items = ArrayList<Pair<Float, () -> Unit>>()

    private fun readLook2() { friuli = variant("land") == "friuli" }

    private fun gateOpen(): Float = fx("gate") ?: if (env.dark > 0.55f) 0f else 1f
    private fun torchLit(): Boolean = lv >= 2 && (fx("torch")?.let { it > 0.5f } ?: (env.windows > 0.35f))

    /** How hard the bell swings: the effect, else a tap's ring. */
    private fun bellSwing(): Float {
        val e = fxOn("bell")
        val pk = poked("bell") ?: return e
        val a = pk.age(t)
        val dur = if (pk.step == 0) 2.0f else 2.8f
        return max(e, (1f - a / dur).coerceIn(0f, 1f) * (if (pk.step == 0) 0.7f else 1f))
    }

    /** The road's middle at depth [d] beyond the gate (m): straight out, then bending left into the forest. */
    private fun road(d: Float): Float = if (d < 22f) 0f else -(d - 22f) * 0.12f - 2.5f * sin((d - 22f) * 0.03f)

    override fun paint() {
        setup()
        // the level of the palisade the scene is seen in, as the fixtures go by it ([si.lanisce.lani.game.scene.SceneNeeds.level])
        lv = frame.world.hereLevel.coerceIn(1, 5)
        readLook2()
        val z = z
        vistaSky(4, 71, moonX = ox + 190 * z, moonY = oy + 12 * z)
        val far = ridge(hz.toFloat() - 6f * z, 16f * z, 0.011f, 711, Col.hex(0x6A86A2), 0.55f, snowLine = 0.7f)
        treeline(far, 5 * z, 2.4f * z, Col.hex(0x56728E), 0.5f, 712)
        thing("hills", outline = 0) {
            val mid = ridge(hz.toFloat() - 1f * z, 9f * z, 0.02f, 713, Col.hex(0x587E52), 0.32f)
            if (friuli) farVillage(mid)
        }
        // the land from the forest to our feet: the meadow beyond the stakes, the trodden ground inside
        c.penId = 0
        for (py in max(hz, c.top) until min(h, c.bottom)) {
            val d = depthAt(py)
            for (px in max(0, c.left) until min(w, c.right)) c.set(px, py, land(sideAt(px, d), d, px, py))
        }
        thing("meadow") { meadow() }
        thing("road") { roadPixels() }
        thing("forest", outline = Pal.OUTLINE_TREE) { forest() }
        wolves()

        items.clear()
        if (friuli) items.add(46f to { prop(Pal.OUTLINE_TREE) { friuliTrees() } })
        flock(outside = true)
        fx("pedlar")?.let { p -> if (p > 0f && p < 1f) { val d = forestD - 4f - (forestD - 4f - pd - 0.6f) * p; items.add(d to { prop { pedlar(d) } }) } }
        // whoever is on the road stands out there while the gate is open; shut (at night, or barred), they're in for the
        // night just inside it
        val out = gateOpen() >= 0.35f
        if (out) for (p in peopleAt("road")) items.add(roadPD to { groundShadow(roadPX, roadPD, 0.5f); person(p, gx(roadPX, roadPD).toInt(), gy(roadPD).toInt(), flip = true) })
        items.sortByDescending { it.first }
        for ((_, draw) in items) draw()

        // the palisade and its gate, then what is inside: the walkway, the bench, the trough, the stake, the people, the flock in
        palisadeAndGate()
        items.clear()
        if (lv >= 4) items.add(walkD - 0.1f to { thing("ladder") { walkway() } })
        items.add(stakeD to { thing("stake") { spareStake() } })
        items.add(benchD to { groundShadow((benchX0 + benchX1) / 2f, benchD, 1f); thing("bench") { bench() } })
        items.add(troughD to { groundShadow((troughX0 + troughX1) / 2f, troughD, 0.9f); thing("trough") { trough() } })
        for (p in peopleAt("gate")) items.add(gatePD to { groundShadow(gatePX, gatePD, 0.5f); person(p, gx(gatePX, gatePD).toInt(), gy(gatePD).toInt(), flip = false) })
        for (p in peopleAt("bench")) items.add(benchPD to { groundShadow(benchPX, benchPD, 0.5f); person(p, gx(benchPX, benchPD).toInt(), gy(benchPD).toInt(), flip = false) })
        if (!out) for (p in peopleAt("road")) items.add(roadInPD to { groundShadow(roadInPX, roadInPD, 0.5f); person(p, gx(roadInPX, roadInPD).toInt(), gy(roadInPD).toInt(), flip = true) })
        items.sortByDescending { it.first }
        for ((_, draw) in items) draw()
        flock(outside = false)

        foregroundGrass(0, w, 71)
        bellRings(bellSwing())
        fireflies(0, hz + 10 * z, w, h, 10, 73)
        s.fx { s.effects.leaves(env.season, env.month) }
        lights()
    }

    // ------------------------------------------------------------------ far away

    /** Friuli's village on the far hill: coppi houses and the campanile. */
    private fun farVillage(crest: IntArray) {
        val z = z
        val st = FireStyle.of(frame.world.culture, frame.world.landscape)
        val lit = env.windows > 0.35f
        for (i in 0 until 4) {
            val hx = ox + (30 + i * 9) * z
            if (hx < 0 || hx >= w) continue
            farCoppiHouse(hx, crest[hx] + 5 * z + (i % 2) * z, 5 * z, 3 * z, st, i, lit, 0.32f)
        }
        val cx = ox + 50 * z
        if (cx in 0 until w) farCampanile(cx, crest[cx] + 4 * z, st, lit, 0.32f, tall = 12)
    }

    /** Lombardy poplars along the road beyond the gate, mulberries with the vines between them on the meadow (Friuli's look). */
    private fun friuliTrees() {
        for (k in 0 until 4) poplar(road(30f + k * 10f) + 3.2f, 30f + k * 10f, 15f, 721 + k)
        for (k in 0 until 3) poplar(road(34f + k * 10f) - 3.4f, 34f + k * 10f, 14f, 731 + k)
        mulberry(-14f, 44f, 6f, 741); mulberry(-8.5f, 46f, 6f, 742)
        vineGarland(-14f, -8.5f, 45f, 3.6f, 743)
        mulberry(9f, 40f, 6f, 744); mulberry(15f, 42f, 6f, 745)
        vineGarland(9f, 15f, 41f, 3.6f, 746)
    }

    // ------------------------------------------------------------------ the ground

    /** Inside the palisade: trodden earth and grass by the gate; beyond it the meadow (its own thing), the road. */
    private fun land(x: Float, d: Float, px: Int, py: Int): Int {
        val g = env.grass
        val n = Noise.v2(x * 0.6f, d * 0.6f, 751) + (Dither.at(px, py) - 0.5f) * 0.25f
        var col = if (n > 0.62f) g[0] else if (n > 0.32f) g[1] else g[2]
        if (d < pd) {
            // the ground inside, trodden bare before the gate and round the trough
            val worn = (1f - abs(x) / 3.2f).coerceIn(0f, 1f) + (if (x in troughX0 - 0.8f..troughX1 + 0.8f) 0.5f else 0f)
            if (worn + (Noise.v2(x * 1.4f, d * 1.4f, 753) - 0.5f) * 0.5f > 0.45f) col = if (env.snow) Pal.SNOW_M else if (Noise.rnd(px / max(1, z), py / max(1, z), 755) < 0.3f) Pal.DIRT_D else Pal.DIRT_M
        }
        return hazy(col, d)
    }

    /** The meadow beyond the palisade, to the forest's edge: grass, flowers in spring and summer, snow in winter. */
    private fun meadow() {
        for (py in max(hz, c.top) until min(h, c.bottom)) {
            val d = depthAt(py)
            if (d < pd + 0.3f || d > forestD + 2f) continue
            for (px in max(0, c.left) until min(w, c.right)) {
                val x = sideAt(px, d)
                if (abs(x - road(d)) < roadHalf(d)) continue
                val g = env.grass
                val n = Noise.v2(x * 0.3f, d * 0.3f, 761) + (Dither.at(px, py) - 0.5f) * 0.3f
                var col = if (n > 0.6f) g[0] else if (n > 0.3f) g[1] else g[2]
                if (env.snow) col = if (n > 0.55f) Pal.SNOW_M else Pal.SNOW_L
                else if (env.month in 4..8 && Noise.rnd(px, py, 763) < 0.02f) col = Pal.PAINTED[(px + py * 3) % 6]
                c.set(px, py, hazy(col, d))
            }
        }
    }

    private fun roadHalf(d: Float): Float = 1.4f + d * 0.004f

    /** The road: from inside the gate out over the meadow into the forest, its wheel ruts and the grass down its middle. */
    private fun roadPixels() {
        for (py in max(hz, c.top) until min(h, c.bottom)) {
            val d = depthAt(py)
            if (d > forestD + 2f) continue
            for (px in max(0, c.left) until min(w, c.right)) {
                val x = sideAt(px, d)
                val u = (x - road(d)) / roadHalf(d)
                if (abs(u) > 1f) continue
                val n = Noise.rnd(px / max(1, z), py / max(1, z), 765)
                val col = when {
                    env.snow -> if (abs(abs(u) - 0.5f) < 0.18f) Pal.SNOW_D else Pal.SNOW_L
                    abs(u) < 0.14f -> env.grass[if (n < 0.5f) 1 else 2]
                    abs(abs(u) - 0.5f) < 0.2f -> if (n < 0.3f) Pal.DIRT_D else Pal.DIRT_M
                    else -> if (n < 0.15f) Pal.STONE_M else Pal.DIRT_L
                }
                c.set(px, py, hazy(col, d))
            }
        }
    }

    /** The forest's edge beyond the meadow: spruces and beeches, gold and red in October, white in winter, a gap where the road goes in. */
    private fun forest() {
        // the far row first, then the edge's own trees, a gap where the road goes in
        for (row in 1 downTo 0) for (k in 0 until 34) {
            val x = -80f + k * 4.9f + (Noise.rnd(k, 771 + row) - 0.5f) * 2.4f
            val d = forestD + row * 14f + Noise.rnd(k, 772 + row) * 8f
            if (abs(x - road(d)) < 3.2f) continue
            val ht = 15f + Noise.rnd(k, 773 + row) * 9f
            if (Noise.rnd(k, 774 + row) < 0.55f) spruce(x, d, ht, 775 + k + row * 40) else leafyTree(x, d, ht * 0.85f, 776 + k + row * 40)
        }
    }

    /** The wolves: eyes shining at the forest's edge at night, grey shapes slinking along it on the meadow by day (`wolves`, or the village's wolves). */
    private fun wolves() {
        val n = fx("wolves")?.let { (it * 5f + 0.01f).toInt() } ?: if (frame.world.wolves && env.dark > 0.5f) 3 else 0
        if (n <= 0) return
        for (k in 0 until n) {
            // at night their eyes among the trees at the forest's edge; by day out of the trees onto the meadow before them,
            // off the road (a little bigger than life, to be made out so far off)
            val night = env.dark > 0.5f
            // either side of the gate's roof (which hides the meadow behind it), never on the road
            val x = (floatArrayOf(-30f, 34f, -47f, 52f, -62f)[k % 5] + Noise.rnd(k, 781) * 3f) * (if (night) 1.4f else 1f)
            val d = if (night) forestD - 2f else 82f + k * 4f
            val bx = gx(x, d); val by = gy(d)
            val p = pm(d) * (if (night) 1f else 1.3f)
            if (night) {
                prop(outline = 0) { glow {
                    val ey = by - 0.55f * p; val blink = sin(t * 0.7 + k * 1.9) > -0.95
                    if (blink) { c.set(bx.toInt(), ey.toInt(), Col.hex(0xF8E070)); c.set((bx + max(1f, 0.25f * p)).toInt(), ey.toInt(), Col.hex(0xF8E070)) }
                } }
            } else prop {
                // a grey wolf slinking along the edge, head low: its back, the paler belly, the legs stepping, the head
                // with its ears pricked, the tail hanging
                val walk = ((t * 0.4 + k * 0.3) % 1.0).toFloat()
                val wx = bx + (walk - 0.5f) * 8f * p
                val grey = env.lit(Col.hex(0x6A6A70)); val dark = env.lit(Col.hex(0x4A4A52)); val pale = env.lit(Col.hex(0x9A968E))
                val step = sin(t * 5.0 + k).toFloat()
                for ((j, lx) in floatArrayOf(-0.45f, -0.25f, 0.3f, 0.48f).withIndex()) {
                    val sw = (if (j % 2 == 0) step else -step) * 0.08f * p
                    c.line((wx + lx * p).toInt(), (by - 0.35f * p).toInt(), (wx + lx * p + sw).toInt(), by.toInt(), dark)
                }
                c.fillEllipse(wx, by - 0.5f * p, max(1.5f, 0.62f * p), max(1f, 0.2f * p), grey)
                c.hline((wx - 0.4f * p).toInt(), (wx + 0.4f * p).toInt(), (by - 0.36f * p).toInt(), pale)
                c.hline((wx - 0.5f * p).toInt(), (wx + 0.45f * p).toInt(), (by - 0.68f * p).toInt(), dark)
                c.fillEllipse(wx + 0.72f * p, by - 0.5f * p, max(1f, 0.2f * p), max(1f, 0.15f * p), grey)
                c.fillRect((wx + 0.82f * p).toInt(), (by - 0.48f * p).toInt(), max(1, (0.18f * p).toInt()), max(1, (0.1f * p).toInt()), dark)
                c.set((wx + 0.66f * p).toInt(), (by - 0.68f * p).toInt(), dark)
                c.set((wx + 0.76f * p).toInt(), (by - 0.7f * p).toInt(), dark)
                c.line((wx - 0.6f * p).toInt(), (by - 0.55f * p).toInt(), (wx - 0.95f * p).toInt(), (by - 0.22f * p).toInt(), dark)
            }
        }
    }

    // ------------------------------------------------------------------ the palisade and the gate

    /** A stake's top at side [x]: its point, and how tall it is (each a little different). */
    private fun stakeTop(k: Int): Float = sh + (Noise.rnd(k, 791) - 0.5f) * 0.35f

    /** The palisade, the posts and the lintel, the wings, the bar, and from their levels the torches, the roof and its pennant, the bell. */
    private fun palisadeAndGate() {
        val open = gateOpen().coerceIn(0f, 1f)
        thing("palisade") { stakes(); if (lv >= 5) footing() }
        thing("gate") { posts(); wings(open) }
        thing("bar", slop = 1) { bar(open) }
        if (lv >= 3) {
            thing("roof") { gateRoof() }
            thing("flag", slop = 1) { pennant() }
        }
        if (lv >= 2) thing("torch", slop = 1) { torches() }
        if (lv >= 5) thing("bell", slop = 1) { bell() }
    }

    /** The stakes from the far left to the far right, every one its own, the gate's opening left between the posts. */
    private fun stakes() {
        val p = pm(pd)
        val ground = gy(pd)
        val x0 = sideAt(c.left, pd) - 1f; val x1 = sideAt(c.right, pd) + 1f
        var k = floor(x0 / st).toInt()
        while (k * st < x1) {
            val xa = k * st; val xb = xa + st
            val mid = (xa + xb) / 2f
            if (abs(mid) < gxp + 0.15f) { k++; continue }
            val top = stakeTop(k)
            val pa = gx(xa, pd); val pb = gx(xb, pd)
            for (py in max(hy(pd, top + 0.45f).toInt(), c.top) until min(ground.toInt() + 1, c.bottom)) {
                val hm = (ground - py - 0.5f) / p
                for (px in max(pa.toInt(), c.left)..min(pb.toInt(), c.right - 1)) {
                    val u = ((px + 0.5f - pa) / max(1f, pb - pa))
                    // the point: narrowing over the last 0.45 m
                    val tip = if (hm > top) (1f - (hm - top) / 0.45f) * 0.5f else 0.5f
                    if (abs(u - 0.5f) > tip) continue
                    c.set(px, py, env.lit(stakeCol(k, u, hm, top, px, py)))
                }
            }
            k++
        }
    }

    private fun stakeCol(k: Int, u: Float, hm: Float, top: Float, px: Int, py: Int): Int {
        val rough = lv <= 1
        if (env.snow && hm > top - 0.08f) return Pal.SNOW_L
        // the bark on the stakes (a palisade of the first level is rough logs, later ones barked smooth and weathered grey-brown)
        val n = Noise.rnd(px / max(1, z), py / max(1, 2 * z), 793 + k)
        val base = if (rough) barkM else Col.hex(0x7E6A54)
        val lit = if (u < 0.3f) Col.mix(base, Col.hex(0xC8A880), 0.3f) else if (u > 0.75f) Col.scale(base, 0.7f) else base
        // the withies binding them, two bands across
        val band = min(abs(hm - 1.0f), abs(hm - 2.6f))
        // from the third level the points are fire-hardened: charred black at the tip, browning down their last half metre
        val char = if (lv >= 3) ((hm - (top - 0.5f)) / 0.95f).coerceIn(0f, 1f) else 0f
        val coal = Col.hex(0x2A2420)
        return when {
            hm > top -> Col.mix(if (u < 0.45f) Col.mix(woodL, base, 0.3f) else woodD, coal, 0.75f * char)
            band < 0.05f + 0.5f / pm(pd) -> if (band < 0.25f / pm(pd)) withy else Col.scale(withy, 0.8f)
            rough && n < 0.12f -> barkD
            n < 0.05f -> Col.mix(Col.scale(lit, 0.85f), coal, 0.75f * char)
            else -> Col.mix(lit, coal, 0.75f * char)
        }
    }

    /** The palisade's footing of dry stones at the fifth level, along the stakes' feet either side of the gate. */
    private fun footing() {
        val d = pd - 0.15f
        dryWall(sideAt(0, d) - 1f, -gxp - 0.3f, d, 0.55f, 797, thick = 0.4f)
        dryWall(gxp + 0.3f, sideAt(w, d) + 1f, d, 0.55f, 798, thick = 0.4f)
    }

    /** The gate's two stout posts and the lintel across them (carved with a sun at the fifth level). */
    private fun posts() {
        val p = pm(pd)
        for (sx in floatArrayOf(-gxp - 0.25f, gxp)) {
            val pa = gx(sx, pd); val pb = gx(sx + 0.25f, pd)
            c.fillRect(pa.toInt(), hy(pd, postH).toInt(), max(2, (pb - pa).toInt()), (gy(pd) - hy(pd, postH)).toInt() + 1, env.lit(barkM))
            c.vline(pa.toInt(), hy(pd, postH).toInt(), gy(pd).toInt(), env.lit(barkL))
            c.vline(pb.toInt(), hy(pd, postH).toInt(), gy(pd).toInt(), env.lit(barkD))
            // the post's side, as it goes back a little (its depth)
            val back = gx(if (sx < 0) sx + 0.25f else sx, pd + 0.3f)
            if (sx > 0) c.fillRect(pa.toInt() - 1, hy(pd, postH).toInt(), 1, (gy(pd) - hy(pd, postH)).toInt(), env.lit(barkD))
        }
        // the lintel
        val la = gx(-gxp - 0.45f, pd); val lb = gx(gxp + 0.45f, pd)
        val lt = hy(pd, lz + 0.35f); val lbot = hy(pd, lz)
        c.fillRect(la.toInt(), lt.toInt(), (lb - la).toInt(), max(2, (lbot - lt).toInt()), env.lit(barkM))
        c.hline(la.toInt(), lb.toInt(), lt.toInt(), env.lit(barkL))
        if (lv >= 5) {
            // the sun carved in the lintel's middle, painted gold
            val cx = gx(0f, pd); val cy = (lt + lbot) / 2f
            c.fillCircle(cx, cy, max(1.5f, 0.12f * p), env.lit(Pal.GOLD))
            for (k in 0 until 8) {
                val a = k * PI.toFloat() / 4f
                c.set((cx + cos(a) * 0.22f * p).toInt(), (cy + sin(a) * 0.14f * p).toInt(), env.lit(Pal.GOLD_L))
            }
        }
        // the gate's opening beyond: nothing drawn there, the road and the meadow show through it
    }

    /**
     * The two wings, hinged at the posts: at [open] 0 shut across the opening, swinging in toward us, at 1 against the inside
     * of the stakes at right angles to them. Poles lashed together at the first level, planks from the second, iron bands from
     * the fourth.
     */
    private fun wings(open: Float) {
        val ang = open * PI.toFloat() * 0.5f
        val l = gxp
        for (side in intArrayOf(-1, 1)) {
            val hx = side * gxp
            // the free edge: shut at the middle, open back toward us along the hinge's side
            val ex = hx - side * l * cos(ang); val ed = pd - l * sin(ang)
            wing(hx, pd, ex, ed, side)
        }
    }

    /** One wing from its hinge ([hx], [hd]) to its free edge ([ex], [ed]), [wingH] tall. */
    private fun wing(hx: Float, hd: Float, ex: Float, ed: Float, side: Int) {
        val ha = gx(hx, hd); val ea = gx(ex, ed)
        c.polyBegin()
        c.polyAdd(ha, gy(hd)); c.polyAdd(ea, gy(ed)); c.polyAdd(ea, hy(ed, wingH)); c.polyAdd(ha, hy(hd, wingH))
        val span = ea - ha
        c.polyScan { px, py ->
            val a = if (abs(span) < 0.5f) 0.5f else ((px + 0.5f - ha) / span).coerceIn(0f, 1f)
            // the point on the wing under this column, and the height there
            val wx = hx + (ex - hx) * a; val wd = hd + (ed - hd) * a
            val hm = (gy(wd) - py - 0.5f) / pm(wd)
            c.set(px, py, env.lit(wingCol(a, hm, abs(ed - hd) > 0.3f)))
        }
    }

    private fun wingCol(a: Float, hm: Float, turned: Boolean): Int {
        val dim = if (turned) 0.82f else 1f
        val col = if (lv <= 1) {
            // poles lashed with withies
            val pole = (a * 6f) % 1f
            when {
                abs(hm - 0.7f) < 0.09f || abs(hm - 2.5f) < 0.09f -> withy
                pole < 0.12f -> barkD
                pole < 0.4f -> barkL
                else -> barkM
            }
        } else {
            val board = (a * 7f) % 1f
            when {
                lv >= 4 && (abs(hm - 0.6f) < 0.08f || abs(hm - 2.7f) < 0.08f) -> if (a > 0.4f) ironL else ironC
                abs(hm - 0.6f) < 0.12f || abs(hm - 2.7f) < 0.12f -> woodD
                abs((hm - 0.6f) / 2.1f - a) < 0.07f && hm in 0.6f..2.7f -> woodD
                board < 0.1f -> woodD
                else -> if (((a * 7f).toInt()) % 2 == 0) woodM else Col.mix(woodM, woodL, 0.3f)
            }
        }
        return Col.scale(col, dim)
    }

    /** The bar: across the shut wings in its iron brackets, or leaning against the right post while they stand open. */
    private fun bar(open: Float) {
        val p = pm(pd)
        if (open < 0.08f) {
            val y = hy(pd - 0.15f, 1.55f)
            val la = gx(-gxp - 0.2f, pd - 0.15f); val lb = gx(gxp + 0.2f, pd - 0.15f)
            c.fillRect(la.toInt(), y.toInt(), (lb - la).toInt(), max(2, (0.18f * p).toInt()), env.lit(woodM))
            c.hline(la.toInt(), lb.toInt(), y.toInt(), env.lit(woodL))
            for (bx in floatArrayOf(-gxp + 0.1f, gxp - 0.1f)) c.fillRect(gx(bx, pd - 0.15f).toInt(), (y - 0.08f * p).toInt(), max(1, (0.08f * p).toInt()), max(2, (0.32f * p).toInt()), env.lit(ironC))
        } else {
            // leaning on the post's inside, its foot on the ground before it
            val fx = gx(gxp + 0.75f, pd - 0.9f); val fy = gy(pd - 0.9f)
            val tx = gx(gxp + 0.3f, pd - 0.25f); val ty = hy(pd - 0.25f, 2.6f)
            for (o in 0 until max(2, (0.16f * p).toInt())) c.line(fx.toInt() + o, fy.toInt(), tx.toInt() + o, ty.toInt(), env.lit(if (o == 0) woodL else woodM))
        }
    }

    /** The little roof over the gate (level 3): shingles on a gable across the lintel, snow on it in winter. */
    private fun gateRoof() {
        val p = pm(pd)
        val la = gx(-gxp - 0.8f, pd); val lb = gx(gxp + 0.8f, pd)
        val base = hy(pd, lz + 0.35f); val apex = hy(pd, lz + 1.5f)
        val mid = gx(0f, pd)
        for (py in max(apex.toInt(), c.top) until min(base.toInt(), c.bottom)) {
            val f = (py - apex) / max(1f, base - apex)
            val half = (lb - la) / 2f * f
            for (px in max((mid - half).toInt(), c.left)..min((mid + half).toInt(), c.right - 1)) {
                val course = ((py - apex) / max(1f, 0.18f * p)).toInt()
                val col = if (env.snow) (if (f < 0.85f) Pal.SNOW_L else Pal.SNOW_M) else if (course % 2 == 0) Pal.SHINGLE_M else Pal.SHINGLE_L
                c.set(px, py, env.lit(if (px == (mid - half).toInt() || px == (mid + half).toInt()) Pal.SHINGLE_D else col))
            }
        }
    }

    /** The pennant on a pole on the gate's roof: blue at the third level, red at the fourth, gold at the fifth (as the map's). */
    private fun pennant() {
        val p = pm(pd)
        val px = gx(0f, pd); val base = hy(pd, lz + 1.5f); val top = hy(pd, lz + 3.4f)
        c.vline(px.toInt(), top.toInt(), base.toInt(), env.lit(Pal.WOOD_D))
        val (light, dark) = when (lv) {
            3 -> Pal.FLAG_BLUE to Col.scale(Pal.FLAG_BLUE, 0.7f)
            4 -> Pal.FLAG_RED to Col.scale(Pal.FLAG_RED, 0.7f)
            else -> Pal.GOLD_L to Pal.GOLD
        }
        val len = 0.9f * p; val hh = 0.45f * p
        for (i in 0 until len.toInt()) {
            val f = i / len
            val wave = sin(t * 4.0 * gust + f * 5f).toFloat() * f * 0.12f * p
            val half = hh * (1f - f) / 2f
            for (yy in (top + hh / 2f - half + wave).toInt()..(top + hh / 2f + half + wave).toInt()) c.set(px.toInt() + 1 + i, yy, if (yy < top + hh / 2f + wave) light else dark)
        }
    }

    /** The torches on the posts' inside faces (level 2): iron baskets on brackets, flames from dusk or as `torch` has it. */
    private fun torches() {
        val p = pm(pd)
        val lit = torchLit()
        for (sx in floatArrayOf(-gxp - 0.4f, gxp + 0.35f)) {
            val bx = gx(sx, pd - 0.1f); val by = hy(pd - 0.1f, 2.7f)
            c.fillRect((bx - 0.12f * p).toInt(), by.toInt(), max(2, (0.24f * p).toInt()), max(2, (0.22f * p).toInt()), env.lit(ironC))
            c.fillRect((bx - 0.03f * p).toInt(), (by + 0.2f * p).toInt(), max(1, (0.06f * p).toInt()), max(2, (0.4f * p).toInt()), env.lit(ironC))
            if (lit) {
                val fl = 0.8f + 0.2f * sin(t * 11 + sx).toFloat()
                glow {
                    for (k in 0 until (0.55f * p * fl).toInt()) {
                        val wv = sin(t * 9 + k * 0.6 + sx).toFloat() * 0.04f * p
                        val half = max(0.5f, 0.11f * p * (1f - k / (0.55f * p)))
                        c.hline((bx - half + wv).toInt(), (bx + half + wv).toInt(), (by - k).toInt(), if (k < 0.2f * p) Pal.FLAME[1] else Pal.FLAME[2])
                    }
                }
                s.light(bx, by - 0.2f * p, 46f * z, 0.9f)
                s.smoke(bx, by - 0.7f * p, 0.25f, 77 + sx.toInt())
            }
        }
    }

    /** The bell under the gate's roof (level 5), swinging as `bell` (or a tap) has it. */
    private fun bell() {
        val p = pm(pd)
        val cx = gx(0f, pd); val hang = hy(pd, lz + 0.35f)
        val a = sin(t * 4.6).toFloat() * 0.6f * bellSwing()
        val len = max(3f, 0.36f * p)
        c.vline(cx.toInt(), (hang - 0.1f * p).toInt(), hang.toInt(), env.lit(ironC))
        val n = len.toInt()
        for (i in 0..n) {
            val f = i / n.toFloat()
            val half = max(0.5f, (0.35f + 0.65f * f * f) * len * 0.55f)
            val mx = cx + sin(a) * len * f; val my = hang + cos(a) * len * f
            for (xx in (mx - half).toInt()..(mx + half).toInt()) {
                val u = (xx + 0.5f - mx) / half
                c.set(xx, my.toInt(), env.lit(if (u < -0.4f) Col.hex(0xF0CA62) else if (u > 0.5f) Col.hex(0x8A6420) else Col.hex(0xC8962E)))
            }
        }
    }

    private fun bellRings(swing: Float) {
        if (swing < 0.05f || lv < 5) return
        val cx = gx(0f, pd); val cy = hy(pd, lz)
        val u = ((t * 2.0) % 1.0).toFloat()
        s.fx { PokeArt.rings(c, cx, cy, u, 20f * z, Col.hex(0xFFF0C0), max(1, z), 0.55f * swing, 2) }
    }

    // ------------------------------------------------------------------ inside

    /** The walkway along the inside of the stakes on its posts, the ladder up to it right of the gate (level 4). */
    private fun walkway() {
        val p = pm(walkD)
        val y = hy(walkD, walkH)
        for (side in intArrayOf(-1, 1)) {
            val xa = if (side < 0) sideAt(c.left, walkD) - 1f else gxp + 0.3f
            val xb = if (side < 0) -gxp - 0.3f else sideAt(c.right, walkD) + 1f
            val pa = gx(xa, walkD); val pb = gx(xb, walkD)
            c.fillRect(pa.toInt(), y.toInt(), (pb - pa).toInt(), max(2, (0.14f * p).toInt()), env.lit(woodL))
            c.hline(pa.toInt(), pb.toInt(), (y + 0.14f * p).toInt(), env.lit(woodD))
            var x = if (side < 0) -gxp - 0.4f else gxp + 0.4f
            while (if (side < 0) x > xa else x < xb) {
                c.fillRect(gx(x, walkD).toInt(), y.toInt(), max(1, (0.12f * p).toInt()), (gy(walkD) - y).toInt(), env.lit(woodM))
                x += side * 2.2f
            }
        }
        // the ladder: its two rails from the ground up to the walkway, the rungs
        val fd = walkD - 1.2f
        for (o in floatArrayOf(0f, 0.45f)) c.line(gx(ladderX + o, fd).toInt(), gy(fd).toInt(), gx(ladderX + o, walkD).toInt(), (y - 0.5f * p).toInt(), env.lit(woodD))
        for (k in 1..5) {
            val f = k / 6f
            val dd = fd + (walkD - fd) * f
            val yy = gy(dd) + (y - 0.5f * p - gy(dd)) * f
            c.hline(gx(ladderX, dd).toInt(), gx(ladderX + 0.45f, dd).toInt(), yy.toInt(), env.lit(woodL))
        }
    }

    /** A new stake leaning ready against the palisade right of the gate, freshly cut and pale, its point sharpened. */
    private fun spareStake() {
        val p = pm(stakeD)
        val fx = gx(stakeX + 0.5f, stakeD - 0.4f); val fy = gy(stakeD - 0.4f)
        val tx = gx(stakeX, stakeD); val ty = hy(stakeD, 3.2f)
        val wpx = max(2, (0.3f * p).toInt())
        for (o in 0 until wpx) c.line(fx.toInt() + o, fy.toInt(), tx.toInt() + o, ty.toInt(), env.lit(if (o < wpx / 3) Col.hex(0xE8D0A8) else if (o < 2 * wpx / 3) woodL else woodM))
        c.fillRect(tx.toInt(), (ty - 0.3f * p).toInt(), max(1, wpx / 2), max(1, (0.3f * p).toInt()), env.lit(Col.hex(0xF0DCB8)))
    }

    /** The bench against the stakes left of the gate: a plank on two blocks; snow on it in winter. */
    private fun bench() {
        val p = pm(benchD)
        val xa = gx(benchX0, benchD); val xb = gx(benchX1, benchD)
        val seat = hy(benchD, 0.45f)
        c.fillRect(xa.toInt(), seat.toInt(), (xb - xa).toInt(), max(2, (0.1f * p).toInt()), if (env.snow) Pal.SNOW_L else env.lit(woodL))
        c.hline(xa.toInt(), xb.toInt(), (seat + 0.1f * p).toInt(), env.lit(woodD))
        for (lx in floatArrayOf(xa + 0.1f * p, xb - 0.3f * p)) c.fillRect(lx.toInt(), (seat + 0.1f * p).toInt(), max(2, (0.2f * p).toInt()), (gy(benchD) - seat - 0.1f * p).toInt(), env.lit(barkM))
    }

    /** The water trough: a hollowed log on two stones, water in it (ice in winter), a wooden spout over its end. */
    private fun trough() {
        val p = pm(troughD)
        val xa = gx(troughX0, troughD); val xb = gx(troughX1, troughD)
        val top = hy(troughD, 0.62f); val bot = hy(troughD, 0.08f)
        // the log: rounded at its ends, its bark, the hollow on top with the water in it (ice in winter)
        val rim = max(2f, 0.16f * p)
        for (py in top.toInt()..bot.toInt()) {
            val v = (py - top) / max(1f, bot - top)
            val inset = (abs(v - 0.5f) * 2f).let { it * it } * 0.12f * p
            for (px in (xa + inset).toInt()..(xb - inset).toInt()) {
                val col = when {
                    py < top + rim -> if (env.snow) Pal.ICE_L else Col.hex(0x4A7EA8)
                    v < 0.35f -> barkL
                    v > 0.8f -> barkD
                    Noise.rnd(px / max(1, z), py / max(1, z), 757) < 0.15f -> barkD
                    else -> barkM
                }
                c.set(px, py, env.lit(col))
            }
        }
        c.hline(xa.toInt(), xb.toInt(), top.toInt(), env.lit(Col.hex(0xC8A880)))
        if (!env.snow) c.hline((xa + 0.2f * p).toInt(), (xa + 0.6f * p).toInt(), (top + 1).toInt(), Col.hex(0xB8D8F0))
        // the wooden spout over its far end, dripping
        c.fillRect((xb - 0.1f * p).toInt(), hy(troughD, 1.0f).toInt(), max(1, (0.08f * p).toInt()), (top - hy(troughD, 1.0f)).toInt(), env.lit(barkD))
        c.fillRect((xb - 0.5f * p).toInt(), hy(troughD, 1.0f).toInt(), (0.45f * p).toInt(), max(1, (0.07f * p).toInt()), env.lit(barkM))
        if (!env.snow && (t * 2).toInt() % 3 != 0) c.set((xb - 0.48f * p).toInt(), (hy(troughD, 0.9f) + ((t * 30) % (0.2f * p))).toInt(), Col.hex(0xC8E4F4))
    }

    // ------------------------------------------------------------------ the flock and the pedlar

    /**
     * Luka's flock (`flock`): to 0.5 going out through the gate and down the road to the meadow, from 0.5 coming home;
     * [outside] draws those beyond the palisade (before it: seen through the gate and over the stakes far off), else those inside.
     */
    private fun flock(outside: Boolean) {
        val f = fx("flock") ?: return
        if (f <= 0f || f >= 1f) return
        val n = (frame.count ?: 12).coerceIn(1, 30)
        // how far along the way out each one is (0 inside the gate, 1 spread on the meadow), going out and then back
        val phase = if (f < 0.5f) f / 0.5f else (1f - f) / 0.5f
        for (k in 0 until n) {
            val lag = k / n.toFloat() * 0.6f
            val u = ((phase * 1.6f - lag)).coerceIn(0f, 1f)
            if (u <= 0f && f < 0.5f) continue
            val d = 11f + u * 34f + Noise.rnd(k, 801) * u * 8f
            val x = road(d) + (Noise.rnd(k, 802) - 0.5f) * (0.8f + u * 16f)
            if ((d > pd) != outside) continue
            val draw = { sheep(x, d, k) }
            if (outside) items.add(d to { prop { draw() } }) else prop { draw() }
        }
        if (outside) { items.sortByDescending { it.first } }
    }

    /** A sheep at side [x], depth [d]: a woolly body, its dark head and legs, as big as the distance makes it. */
    private fun sheep(x: Float, d: Float, k: Int) {
        val p = pm(d)
        val bx = gx(x, d); val by = gy(d)
        val bw = max(2f, 0.9f * p); val bhh = max(1.5f, 0.55f * p)
        val wool = env.lit(if (k % 7 == 3) Col.hex(0x4A4440) else Col.hex(0xEEE8DA))
        c.fillEllipse(bx, by - 0.5f * p, bw / 2f, bhh / 2f, wool)
        c.fillRect((bx + bw * 0.38f).toInt(), (by - 0.62f * p).toInt(), max(1, (0.25f * p).toInt()), max(1, (0.22f * p).toInt()), env.lit(Col.hex(0x2E2A28)))
        if (p > 6f) for (lx in floatArrayOf(-0.25f, 0.25f)) c.vline((bx + lx * p).toInt(), (by - 0.25f * p).toInt(), by.toInt(), env.lit(Col.hex(0x2E2A28)))
    }

    /** The pedlar coming up the road at depth [d]: a little man in a black hat under his krošnja, its frame higher than his hat. */
    private fun pedlar(d: Float) {
        val p = pm(d) * 1.25f
        val x = road(d) + 0.4f
        val bx = gx(x, d); val by = gy(d)
        val step = sin(t * 6.0).toFloat()
        val hgt = 1.7f * p
        c.line(bx.toInt(), (by - hgt * 0.42f).toInt(), (bx + step * 0.12f * p).toInt(), by.toInt(), env.lit(Col.hex(0x2A2A2E)))
        c.line(bx.toInt(), (by - hgt * 0.42f).toInt(), (bx - step * 0.12f * p).toInt(), by.toInt(), env.lit(Col.hex(0x1E1E22)))
        // the krošnja on his back: a wooden frame stacked with sieves and spoons, higher than his head
        c.fillRect((bx - 0.35f * p).toInt(), (by - hgt * 1.25f).toInt(), max(2, (0.5f * p).toInt()), max(2, (hgt * 0.8f).toInt()), env.lit(Col.hex(0xB08A5A)))
        c.fillEllipse(bx - 0.1f * p, by - hgt * 1.28f, max(1f, 0.3f * p), max(1f, 0.1f * p), env.lit(Col.hex(0xE0C898)))
        c.fillRect((bx - 0.12f * p).toInt(), (by - hgt * 0.85f).toInt(), max(2, (0.28f * p).toInt()), max(1, (hgt * 0.45f).toInt()), env.lit(Col.hex(0xF0EEE6)))
        c.fillCircle(bx, by - hgt * 0.9f, max(0.6f, 0.13f * p), env.lit(Pal.SKIN))
        c.hline((bx - 0.2f * p).toInt(), (bx + 0.2f * p).toInt(), (by - hgt * 0.97f).toInt(), env.lit(Col.hex(0x1A1A1E)))
        c.fillRect((bx - 0.1f * p).toInt(), (by - hgt * 1.05f).toInt(), max(1, (0.2f * p).toInt()), max(1, (0.08f * p).toInt()), env.lit(Col.hex(0x1A1A1E)))
    }

    private fun lights() {
        // the torches give their own light ([torches]); the moon's on the snow is the sky's
    }
}
