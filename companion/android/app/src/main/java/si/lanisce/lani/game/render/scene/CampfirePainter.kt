package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Moon
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.Season
import si.lanisce.lani.game.scene.Poke
import si.lanisce.lani.game.scene.SceneSights
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ob ognju: a camp in a clearing in the woods, as a diorama on its own plot. The fire burns in the middle in
 * its ring of stones, the kettle hanging over it from a tripod; two logs to sit on by it, the stump in front
 * (the seat that is free), the water bucket by the left log. At the back the pup tent with its door tied
 * open toward the fire (the lantern in it glows through the canvas at night) and the axe in the chopping block
 * by its corner, the woodpile along the edge, spruces, hazels and ferns all along the back edges. Above, in
 * the dark, the moon (a pale ghost by day) and the stars and the Milky Way that come out at night; the fire
 * lights everything then, and fireflies drift on summer nights.
 *
 * Closer up ([detail] 2 and 3) the same camp comes out finer: the cut ends of the firewood, the chopping block and
 * the stump show their rings, the bark its furrows, the hearth stones their grit, glowing coals lie in the ash
 * under the flames, the kettle has its seam and rivets, the bucket its riveted hoops and a ripple on the water,
 * the chain its links, the lashings their twisted strands, the axe a bright edge, the ferns their leaflets, the
 * tent's canvas its weave and stitches, and the moon its craters over fainter stars and the Milky Way's dust.
 *
 * Tapped ([pokes]), the fire leaps up in a burst of sparks; tapped again, higher, a shower of them.
 */
internal class CampfirePainter : DioramaPainter() {
    override val art = "campfire"
    override val pokes = listOf(Poke("fire", listOf(1.6, 2.4), rest = 3.0))
    override val plotW = 12f
    override val plotD = 12f
    override val rise = 26f

    // the fire in its ring of stones, the tripod's top high over it
    private val fireX = 5.8f; private val fireY = 7.2f; private val fireR = 0.95f
    private val apexZ = 24f
    // the pup tent at the back on the right: its ridge along y over tentR, the door in the near gable toward the fire
    private val tentX0 = 7.4f; private val tentX1 = 11.1f; private val tentY0 = 0.5f; private val tentY1 = 4.3f
    private val tentR = (tentX0 + tentX1) / 2f
    private val ridge = 16f
    private val doorZ = 12f; private val doorW = 1.1f
    // the woodpile along the back left edge; the chopping block by the tent's corner
    private val pileX = 0.8f; private val pileY0 = 4.6f; private val pileY1 = 8.2f
    private val blockX = 11.5f; private val blockY = 5.4f
    // the people: left and right on the logs by the fire, back between the fire and the tent
    private val leftX = 5.0f; private val leftY = 10.6f
    private val rightX = 10.2f; private val rightY = 7.3f
    private val backX = 5.5f; private val backY = 3.25f
    // the bucket by the left log, the stump in front
    private val bucketX = 3.2f; private val bucketY = 11.4f
    private val stumpX = 9.6f; private val stumpY = 10.9f

    override fun paint() {
        fit()
        vignette()
        val lit = env.windows > 0.35f
        nightSky()
        distantBell()
        // the smoke and the sparks over the fire: whatever is drawn in front of them answers first
        val cx = iso.ix(fireX, fireY); val cy = iso.iy(fireX, fireY, 0f)
        val q = detail
        thingRect("smoke", cx - 8 * q, cy - 100 * q, cx + 26 * q, cy - 50 * q)
        thingRect("sparks", cx - 16 * q, cy - 50 * q, cx + 20 * q, cy - 16 * q)
        plot { x, y, px, py -> ground(x, y, px, py) }
        // tufts first: they lie flat, and whatever stands in front of them covers them
        tufts(96, 13) { x, y -> bare(x, y) }
        groundShadows()

        // the woods along the back edges, back to front
        for (t in TREES) prop(Pal.OUTLINE_TREE) { spruce(t[0], t[1], t[2], t[3].toInt()) }
        for (b in BACK_BUSHES) prop(Pal.OUTLINE_TREE) { bush(b[0], b[1], b[2], b[3].toInt()) }
        undergrowth()

        // the woodpile
        thing("logs") { woodpile() }

        // the tent (once the village has pitched it: TENT), its guy ropes and stakes; the chopping block and its axe by its corner
        val tent = there("tent")
        if (tent) {
            thing("tent") { tent(lit) }
            prop(outline = 0) {
                rope(tentR + 0.25f, tentY1 + 0.03f, ridge + 2f, 8.3f, 5.9f, 0f, sag = 0.6f)
                for (gy in floatArrayOf(tentY0 + 0.45f, tentY1 - 0.45f)) rope(tentX1, gy, 1.6f, tentX1 + 0.55f, gy, 0f, sag = 0.2f)
            }
            prop { stake(8.3f, 5.9f); for (gy in floatArrayOf(tentY0 + 0.45f, tentY1 - 0.45f)) stake(tentX1 + 0.55f, gy) }
        }
        prop { choppingBlock() }
        thing("axe", slop = 1) { axe() }

        // behind the fire, near the tent
        for (p in peopleAt("back")) { footShadow(backX, backY); personAt(p, backX, backY, flip = true) }

        // the hearth: the tripod's back leg, the stones behind, the fire, the kettle, the stones in front, the front legs
        prop { tripodLeg(-1.14f, -1.14f) }
        thing("stones") { hearth(front = false) }
        thing("fire") { blaze() }
        prop(outline = 0) { chain() }
        thing("kettle", slop = 1) { kettle(lit) }
        thing("stones") { hearth(front = true) }
        prop { tripodLeg(-0.42f, 1.57f); tripodLeg(1.57f, -0.42f); lashing() }

        // the bucket, the logs by the fire and the people on them, the stump in front
        thing("bucket", slop = 1) { bucket(lit) }
        prop { seat(leftX, leftY, 0.45f, 71) }
        for (p in peopleAt("left")) { footShadow(leftX, leftY); personAt(p, leftX, leftY, flip = false, seated = true) }
        prop { seat(rightX, rightY, 0.55f, 73) }
        for (p in peopleAt("right")) { footShadow(rightX, rightY); personAt(p, rightX, rightY, flip = true, seated = true) }
        thing("stump") { stump() }

        fireflies(iso.ix(0f, 9f), iso.iy(0f, 3f, 20f), iso.ix(12f, 3f), iso.iy(12f, 9f, 0f), 14, 21)
        drift()
        if (lit && tent) {
            // the lantern in the tent: out of the door onto the grass, and through the canvas
            s.light(iso.sx(tentR, tentY1 + 0.5f), iso.sy(tentR, tentY1 + 0.5f, 3f), 26f, 0.5f * env.windows)
            s.light(iso.sx(tentR + 0.5f, (tentY0 + tentY1) / 2f), iso.sy(tentR + 0.5f, (tentY0 + tentY1) / 2f, 8f), 24f, 0.3f * env.windows)
        }
    }

    /** A touch lighter at night, so the woods at the back don't sink into black. */
    override fun ambient(): FloatArray {
        val lift = env.dark * 0.04f
        return floatArrayOf(env.ambR + lift, env.ambG + lift, env.ambB + lift)
    }

    /** The campfire is out in the open: the weather falls on all of it, and the grey sky darkens it all. */
    override fun outdoors(id: Int): Boolean = true
    override val gloomShare: Float = 1f

    // ------------------------------------------------------------------ the sky over the diorama

    /**
     * The dark room above the diorama is the night sky: the Milky Way and the stars come out after dusk; the moon, while
     * it is up, crosses it from the left to the right (lower as it rises and sets, behind the trees), by day a pale ghost.
     */
    private fun nightSky() {
        val q = detail
        val bottom = iso.oy.toInt() + (plotW * 2f * K).toInt()
        milkyWay(bottom)
        // the same stars at every detail (their count and places are the scene canvas's); closer up, fainter ones between
        stars(0, bottom, max(40, (w / q) * (bottom / q) / 320), 3)
        if (q >= 2) faintStars(bottom)
        // only tappable when they show (SceneSights: the words panel's chips go by it too)
        thingRect("stars", 0, 0, w, max(16 * q, iso.oy.toInt() - 8 * q), visible = SceneSights.starsOut(env))
        val top = max(14 * q, (iso.oy.toInt() - (rise * K).toInt()) / 2 + 20 * q)
        val mx = iso.ox.toInt() + ((env.moonArc - 0.5f) * MOON_SPAN).roundToInt() * q
        thing("moon", outline = 0) { moon(mx, moonRow(top, top + MOON_DROP * q)) }
    }

    /**
     * A distant bell (a story's "bell": the wishing bell of Bled, a church far off): no bell to see, only its rings going
     * out in the sky over the woods at the back, from somewhere beyond them, opposite the moon, as long as it rings.
     */
    private fun distantBell() {
        val ring = fxOn("bell")
        if (ring <= 0.02f) return
        val q = detail
        val x = iso.ox.toInt() - 84 * q
        val y = max(18 * q, (iso.oy.toInt() - (rise * K).toInt()) / 2 + 28 * q)
        s.fx {
            c.penEmissive = true
            for (j in 0 until 3) {
                val ph = ((t * 0.8 + j / 3.0) % 1.0).toFloat()
                val r = (4f + ph * 16f) * q
                val a = (1f - ph) * ring * 0.85f
                for (side in intArrayOf(-1, 1)) for (k in -7..7) {
                    val ang = k * 0.1f
                    for (th in 0 until q) {
                        val rr = r + th
                        c.blend((x + side * rr * cos(ang)).toInt(), (y + rr * sin(ang)).toInt(), Col.hex(0xFFD86A), a)
                    }
                }
            }
            c.penEmissive = false
        }
    }

    /** Closer up, the fainter stars the eye finds between the bright ones: single dim pixels, twinkling. */
    private fun faintStars(bottom: Int) {
        val a = ((env.dark - 0.3f) / 0.5f).coerceIn(0f, 1f)
        if (a <= 0f) return
        val n = (w / detail) * (bottom / detail) / 320 * (detail - 1) * 2
        c.penEmissive = true
        for (k in 0 until n) {
            val x = (Noise.rnd(k, 301) * w).toInt(); val y = (Noise.rnd(k, 302) * bottom).toInt()
            if (!c.inside(x, y)) continue
            val tw = 0.6f + 0.4f * sin(t * (1.1 + Noise.rnd(k, 303) * 2) + k).toFloat()
            c.blend(x, y, if (k % 5 == 0) Col.hex(0xD8E0FF) else Col.hex(0xFFF4DC), a * tw * (0.18f + Noise.rnd(k, 304) * 0.3f))
        }
        c.penEmissive = false
    }

    /** A faint band of light across the sky, thick with small stars, from the upper left down to the right. */
    private fun milkyWay(bottom: Int) {
        val a = ((env.dark - 0.4f) / 0.4f).coerceIn(0f, 1f)
        if (a <= 0f) return
        val haze = Col.hex(0x7A86BC)
        val nx = -0.34f; val ny = 0.94f   // the band's normal: it runs down to the right at a shallow slope
        val x0 = w * 0.1f; val y0 = bottom * 0.1f
        val width = bottom * 0.1f + 6f * detail
        // its clouds the same size at every detail; closer up its dust of stars finer and thicker
        val s1 = 0.07f / detail; val s2 = 0.15f / detail
        val dust = 0.012f / detail
        c.penEmissive = true
        for (y in max(0, c.top) until min(bottom, c.bottom)) for (x in max(0, c.left) until min(w, c.right)) {
            val d0 = ((x - x0) * nx + (y - y0) * ny) / width
            if (abs(d0) > 1.46f) continue
            val d = d0 + (Noise.v2(x * s1, y * s1, 91) - 0.5f) * 0.9f
            if (abs(d) > 1f) continue
            val f = (1f - d * d) * (0.5f + Noise.v2(x * s2, y * s2, 93))
            if (f * 0.14f * a > Dither.at(x, y) * 0.1f) c.blend(x, y, haze, (f * 0.1f * a).coerceAtMost(0.16f))
            if (Noise.rnd(x, y, 95) < dust * f) c.blend(x, y, Col.hex(0xE8ECFF), 0.5f * a)
        }
        c.penEmissive = false
    }

    /**
     * The moon as the date has it (see [Moon]): lit on the right while it waxes and on the left while it wanes, from a
     * sliver to half to full, soft along the terminator; its seas and craters keep their places on its face and show
     * where the sun lights them; a crescent's dark part faintly lit by the earthshine at night, and a halo round it, the
     * brighter the fuller it is. By day a pale ghost of it; nothing while it is down or new. Its shape is the scene
     * canvas's, [detail] times as big; closer up the craters get rims (shaded on the side away from the sun, lit on the
     * other) and the seas mottled edges, and it takes its colours from the sky just behind each of its pixels (a window
     * may not hold its middle).
     */
    private fun moon(cx: Int, cy: Int) {
        if (!SceneSights.moonUp(env)) return
        val q = detail
        val r0 = 8
        val r = r0 * q
        val night = ((env.dark - 0.2f) / 0.5f).coerceIn(0f, 1f)
        val a = 0.28f + 0.72f * night
        val age = env.moonAge
        val sun = if (Moon.waxing(age)) 1f else -1f
        val earth = earthshine() * (0.2f + 0.5f * night)
        val litC = Col.hex(0xFAF4DA); val midC = Col.hex(0xE2D9B2); val craterC = Col.hex(0xC6BD98); val shineC = Col.hex(0x39405E)
        val bg0 = c.get(cx, cy)
        c.penEmissive = true
        // the halo: two soft bands round it
        val halo = night * (0.4f + 0.6f * env.moonLit)
        if (halo > 0f) for (yy in max(-2 * r, c.top - cy)..min(2 * r, c.bottom - 1 - cy)) for (xx in max(-2 * r, c.left - cx)..min(2 * r, c.right - 1 - cx)) {
            val d = sqrt((xx + 0.5f) * (xx + 0.5f) + (yy + 0.5f) * (yy + 0.5f)) / r + (Dither.at(cx + xx, cy + yy) - 0.5f) * 0.12f
            if (d in 1f..1.45f) c.blend(cx + xx, cy + yy, Col.hex(0x8A96C8), 0.13f * halo)
            else if (d in 1.45f..1.95f) c.blend(cx + xx, cy + yy, Col.hex(0x8A96C8), 0.06f * halo)
        }
        for (yy in -r..r) for (xx in -r..r) {
            if (q > 1 && !c.inside(cx + xx, cy + yy)) continue
            // the scene canvas's coordinates of the pixel's middle
            val fx = (xx + 0.5f) / q; val fy = (yy + 0.5f) / q
            if (fx * fx + fy * fy > r0 * r0) continue
            // how far into the lit part, in the scene canvas's pixels (the dark part: 0 or less)
            val u = Moon.light(fx / r0, fy / r0, age) * r0
            if (u <= 0f && (earth <= 0f || night <= 0f)) continue
            val bg = if (q == 1) bg0 else c.get(cx + xx, cy + yy)
            // the craters and the seas, where the scene canvas's pixels (gx, gy) are: at detail 1 exactly its integer pixels
            val gx = fx - 0.5f; val gy = fy - 0.5f
            val col = when {
                u <= 0f -> Col.mix(bg, shineC, earth)
                u < 1.1f -> Col.mix(bg, midC, a)
                q == 1 -> {
                    val c1 = (gx - 3) * (gx - 3) + (gy + 3) * (gy + 3); val c2 = (gx - 5) * (gx - 5) + (gy - 2) * (gy - 2); val c3 = (gx - 2) * (gx - 2) + (gy - 5) * (gy - 5)
                    val c4 = (gx + 3) * (gx + 3) + (gy + 1) * (gy + 1); val c5 = (gx + 1) * (gx + 1) + (gy - 6) * (gy - 6)
                    val inCrater = c1 < 3 || c2 < 2 || c3 < 2 || c4 < 2 || c5 < 1.5f
                    Col.mix(bg, if (inCrater) craterC else if (sea(gx, gy, r0) > 0.3f) Col.mix(litC, midC, 0.8f) else litC, a)
                }
                else -> Col.mix(bg, moonFine(gx, gy, r0, sun, craterC, midC, litC), a)
            }
            c.set(cx + xx, cy + yy, col)
        }
        c.penEmissive = false
    }

    /**
     * A lit pixel of the moon closer up (at the scene canvas's ([gx], [gy]) from its middle, the disc [r0] px round): the
     * craters' floors, their inner walls lit where they face the sun (on the right while it waxes, [sun] 1; on the left
     * while it wanes, -1) and in shadow where they face away, their outer rims bright on the sunward side; the grey seas
     * with mottled edges on the rest.
     */
    private fun moonFine(gx: Float, gy: Float, r0: Int, sun: Float, crater: Int, mid: Int, lit: Int): Int {
        for (k in 0 until CRATERS.size / 3) {
            val ox = gx - CRATERS[k * 3]; val oy = gy - CRATERS[k * 3 + 1]; val r2 = CRATERS[k * 3 + 2]
            val cd = ox * ox + oy * oy
            if (cd >= r2 * 1.35f) continue
            val facing = sun * ox - 0.3f * oy // > 0: this side of the crater lies toward the sun
            return when {
                cd < r2 * 0.6f -> crater
                cd < r2 -> if (facing < 0f) Col.mix(lit, Col.hex(0xFFFFF0), 0.4f) else Col.mix(crater, Col.hex(0x8A8268), 0.55f)
                facing > 0f -> Col.mix(lit, Col.hex(0xFFFFF0), 0.5f)
                else -> lit
            }
        }
        val sea = sea(gx, gy, r0) + (Noise.v2(gx * 0.45f + 3f, gy * 0.45f, 97) - 0.5f) * 0.5f
        return if (sea > 0.42f) Col.mix(lit, mid, 0.75f) else if (sea > 0.26f) Col.mix(lit, mid, 0.4f) else lit
    }

    /**
     * How deep in one of the moon's seas (maria) its point ([gx], [gy]) lies (scene canvas px from its middle, the disc
     * [r0] px round): 0 outside, toward 1 in their middles. They lie where they do on the real moon's face: the Sea of
     * Rains up on the left, Serenity and Tranquillity right of the middle, the Ocean of Storms along the left, Crises
     * by the right edge, Clouds and Nectar below.
     */
    private fun sea(gx: Float, gy: Float, r0: Int): Float {
        var best = 0f
        for (k in 0 until MARIA.size / 3) {
            val dx = gx / r0 - MARIA[k * 3]; val dy = gy / r0 - MARIA[k * 3 + 1]; val mr = MARIA[k * 3 + 2]
            val v = 1f - (dx * dx + dy * dy) / (mr * mr)
            if (v > best) best = v
        }
        return best
    }

    // ------------------------------------------------------------------ ground

    private fun ground(x: Float, y: Float, px: Int, py: Int): Int {
        var col = grassAt(x, y, px, py)
        // needles and moss under the spruces along the back edges
        val under = min(x, y) - (Noise.v1((x - y) * 1.7f, 41) - 0.5f) * 0.6f
        if (under < 0.95f) {
            val ff = env.forestFloor
            val n = Noise.rnd(px, py, 43)
            col = if (n < 0.1f) ff[2] else if (Noise.v2(x * 3f, y * 3f, 45) < 0.5f) ff[0] else ff[1]
        }
        // trampled round the fire, on the way to the tent and out of the clearing
        val dFire = hypot(x - fireX, y - fireY) - fireR
        val path = min(segDist(x, y, fireX + 0.7f, fireY - 0.7f, tentR - 0.2f, tentY1 + 0.5f), segDist(x, y, fireX - 0.3f, fireY + 1.2f, 2.4f, 12.2f))
        val near = max(1f - dFire / 1.2f, 1f - path / 0.5f)
        if (near > 0f) col = dirtAt(px, py, (near * (0.7f + Noise.v2(x * 2f, y * 2f, 3) * 0.6f)).coerceIn(0f, 1f), col)
        // ash blown just past the stones; in winter the fire has melted a ring of wet earth
        if (winter && dFire < 0.6f + Noise.v2(x * 3f, y * 3f, 8) * 0.35f) {
            val n = Noise.rnd(px, py, 49)
            col = if (n < 0.3f) Pal.SOIL_D else if (n < 0.85f) Pal.SOIL_M else Pal.DIRT_D
        }
        if (dFire < 0.45f && Noise.rnd(px, py, 51) < 0.3f) col = if (Noise.rnd(px, py, 52) < 0.5f) Col.hex(0x5A5450) else Col.hex(0x7A726A)
        // chips round the chopping block
        if (hypot(x - blockX, y - blockY) < 1.05f && Noise.rnd(px, py, 47) < 0.07f) col = if (Noise.rnd(px, py, 48) < 0.5f) Col.hex(0xE8C890) else Col.hex(0xC89A62)
        return col
    }

    private fun segDist(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val u = (((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        return hypot(x - ax - dx * u, y - ay - dy * u)
    }

    /** No tufts in the fire, on the paths or under what stands on the ground. */
    private fun bare(x: Float, y: Float): Boolean =
        hypot(x - fireX, y - fireY) < fireR + 1.3f ||
            (x in tentX0 - 0.2f..tentX1 + 0.3f && y in tentY0 - 0.2f..tentY1 + 0.3f) ||
            (x < pileX + 1.6f && y in pileY0 - 0.3f..pileY1 + 0.3f) ||
            hypot(x - blockX, y - blockY) < 0.9f || hypot(x - stumpX, y - stumpY) < 1.1f || hypot(x - bucketX, y - bucketY) < 0.7f ||
            segDist(x, y, fireX - 0.3f, fireY + 1.2f, 2.4f, 12.2f) < 0.45f

    /** Soft shadows on the grass: at the foot of the spruces and the woodpile, along the tent's eave. */
    private fun groundShadows() {
        for (t in TREES) shade(iso.sx(t[0] + 0.25f, t[1] + 0.25f), iso.sy(t[0] + 0.25f, t[1] + 0.25f, 0f), t[2] * 0.55f * K, t[2] * 0.2f * K, 0.78f, groundId)
        if (there("tent")) for (k in 0..3) {
            val yy = tentY0 + 0.3f + k * (tentY1 - tentY0 - 0.6f) / 3f
            shade(iso.sx(tentX1 + 0.35f, yy), iso.sy(tentX1 + 0.35f, yy, 0f), 3.2f * K, 1.4f * K, 0.8f, groundId)
        }
        shade(iso.sx(pileX + 1.5f, (pileY0 + pileY1) / 2f), iso.sy(pileX + 1.5f, (pileY0 + pileY1) / 2f, 0f), 10f * K, 3.2f * K, 0.8f, groundId)
    }

    private fun footShadow(x: Float, y: Float) = shade(iso.sx(x, y), iso.sy(x, y, 0f), 5f * K, 1.6f * K, 0.72f, groundId)

    // ------------------------------------------------------------------ the woods

    /**
     * A spruce with its foot at world ([x], [y]), [hgt] px tall: a stub of trunk under tiers of drooping
     * branches, each tier's tips hanging over the next, lit from the left; snow on the tiers in winter.
     */
    private fun spruce(x: Float, y: Float, hgt: Float, seed: Int) {
        val bx = iso.sx(x, y); val by = iso.sy(x, y, 0f)
        val p = env.pine
        val top = by - hgt * K; val foot = by - 2.5f * K
        val span = foot - top
        val hw = hgt * K * 0.25f
        val n = 5 + Math.floorMod(seed, 2)
        val droop = 0.42f
        val q = detail
        // the trunk under the lowest tier: [detail] px columns in its four tones; closer up, furrows in its bark
        val tx = floor(bx).toInt()
        val bark = intArrayOf(Pal.LOG_M, Pal.LOG_L, Pal.LOG_M, Pal.LOG_D)
        for (py in max(c.top, floor(foot - K).toInt())..min(c.bottom - 1, floor(by).toInt())) {
            if (q == 1) {
                c.set(tx - K, py, Pal.LOG_M); c.set(tx - K + 1, py, Pal.LOG_L)
                for (dx in 2 until 2 * K) c.set(tx - K + dx, py, if (dx == 2 * K - 1) Pal.LOG_D else Pal.LOG_M)
            } else for (dx in 0 until 2 * K) {
                var col = bark[min(3, dx / q)]
                if (dx % q == q - 1 && Noise.rnd(tx + dx, py / 2, seed) < 0.5f) col = Col.scale(col, 0.8f)
                c.set(tx - K + dx, py, col)
            }
        }
        // the rows and columns of the picture it may reach, kept to the window; the tiers' ragged ends per row
        for (py in max(c.top, floor(top).toInt())..min(c.bottom - 1, floor(foot + span / n * droop + 1f * q).toInt())) {
            val s = (py + 0.5f - top) / span * n
            val i = floor(s).toInt()
            val rough = (Noise.rnd(py, seed, 3) - 0.5f) * 1.4f * q
            for (px in max(c.left, floor(bx - hw - 2f * q).toInt())..min(c.right - 1, floor(bx + hw + 2f * q).toInt())) {
                val dx = abs(px + 0.5f - bx)
                var f = -1f; var wt = 1f
                // the tier above hangs its tips over this one's top at the sides
                if (i >= 1) {
                    val wa = hw * i / n + rough
                    val fa = s - (i - 1)
                    if (dx <= wa && fa - 1f < droop * (dx / wa).pow(2)) { f = fa; wt = wa }
                }
                if (f < 0f && i < n) {
                    val fi = s - i
                    val half = hw * (i + 1) / n * ((if (i == 0) 0.05f else 0.3f) + (if (i == 0) 0.95f else 0.7f) * fi) + rough * fi
                    if (dx <= half) { f = fi; wt = max(half, 1f) }
                }
                if (f < 0f) continue
                val rel = (px + 0.5f - bx) / wt
                c.set(px, py, needle(rel, f, px, py, seed, p))
            }
        }
    }

    /** One pixel of a spruce tier: [rel] -1 (left, lit) .. 1, [f] 0 at the tier's top .. 1 at its bottom (past 1 the hanging tips). */
    private fun needle(rel: Float, f: Float, px: Int, py: Int, seed: Int, p: IntArray): Int {
        var col = when {
            f > 1f -> if (rel < -0.2f) p[1] else p[2]
            f > 0.84f -> Col.scale(p[2], 0.82f)
            rel < -0.4f -> p[0]
            rel < 0.3f -> p[1]
            else -> p[2]
        }
        if (f <= 0.84f) {
            if (detail == 1) {
                val n = Noise.rnd(px, py, seed)
                if (rel < 0.1f && n < 0.1f) col = p[3]
                else if (n > 0.9f || ((px + 2 * py) % 4 == 0 && n < 0.3f)) col = Col.scale(col, 0.84f)
            } else {
                // closer up: needles, short strokes slanting down and out from the middle of the tier, the lit ones
                // light on the left, the rest a shade darker
                val len = detail + 1
                val diag = if (rel < 0f) px + py else px - py
                val seg = Math.floorDiv(py + (Noise.hash(diag, seed) and 7), len)
                val h = Noise.rnd(diag, seg, seed + 11)
                if (rel < 0.1f && h < 0.13f) col = p[3]
                else if (h > 0.86f) col = Col.scale(col, 0.82f)
                else if (h in 0.4f..0.47f) col = Col.mix(col, p[0], 0.5f)
            }
        }
        if (winter) {
            val sc = 0.5f / detail
            val cover = 0.5f - f + (Noise.v2(px * sc, py * sc, seed) - 0.5f) * 0.45f - rel * 0.3f
            if (cover > 0f && f <= 1f) col = if (rel < 0.15f) Pal.SNOW_L else Pal.SNOW_M
        }
        return col
    }

    /** Ferns at the foot of the woods (rusty in autumn, gone under the snow in winter), a few stones, fly agarics in autumn. */
    private fun undergrowth() {
        if (!winter) for (f in FERNS) prop(outline = 0) { fern(f[0], f[1], f[2].toInt()) }
        for (r in ROCKS) prop { stone(r[0], r[1], r[2], r[3].toInt()) }
        if (env.season == Season.AUTUMN) for (m in AGARICS) prop { agaric(m[0], m[1], m[2]) }
    }

    /** A fern at world ([x], [y]): a fan of fronds arching out and drooping at their tips, leaflets along them. */
    private fun fern(x: Float, y: Float, seed: Int) {
        val bx = iso.sx(x, y); val by = iso.sy(x, y, 0f)
        val autumn = env.season == Season.AUTUMN
        val l = if (autumn) Col.hex(0xC98A3A) else Col.hex(0x7CB85A)
        val m = if (autumn) Col.hex(0xA0602A) else Col.hex(0x5A9A44)
        val d = if (autumn) Col.hex(0x6E4020) else Col.hex(0x3E7432)
        val q = detail
        for (f in 0 until 5) {
            val a = (f - 2) * 0.5f + (Noise.rnd(f, seed) - 0.5f) * 0.25f
            val len = 7 + (2 - abs(f - 2)) * 2 + (Noise.rnd(f, seed, 2) * 2f).toInt()
            if (q == 1) for (i in 0 until len) {
                val tt = i / len.toFloat()
                val px = floor(bx + sin(a) * i * 1.15f).toInt()
                val py = floor(by - cos(a) * i * 0.8f + tt * tt * len * 0.45f).toInt()
                c.set(px, py, if (f < 2) m else d)
                if (i in 2 until len - 1 && i % 2 == 0) { c.set(px - 1, py - 1, l); c.set(px + 1, py - 1, if (f < 2) l else m) }
            } else {
                // closer up: the same frond, its stem a picture px thin, a leaflet either side at every step, longest
                // in the middle of the frond, each a short stroke up and out, lit on the side toward the light
                val steps = len * q
                for (i in 0 until steps) {
                    val tt = i / steps.toFloat()
                    val px = floor(bx + sin(a) * i * 1.15f).toInt()
                    val py = floor(by - cos(a) * i * 0.8f + tt * tt * len * 0.45f * q).toInt()
                    c.set(px, py, if (f < 2) m else d)
                    if (i >= 2 * q && i < steps - q / 2 && i % 2 == 0) {
                        val leaf = max(1, (q * 1.6f * sin(tt * 3.1f)).toInt())
                        for (s in 1..leaf) {
                            c.set(px - s, py - (s + 1) / 2, if (s == leaf) l else if (f < 2) l else m)
                            c.set(px + s, py - (s + 1) / 2, if (f < 2) l else if (s == leaf) m else d)
                        }
                    }
                }
            }
        }
    }

    /** A fly agaric: a red cap with white spots on a pale stem. */
    private fun agaric(x: Float, y: Float, size: Float) {
        val bx = iso.ix(x, y); val by = iso.iy(x, y, 0f)
        val q = detail
        val stem = ((3f * size).toInt() + 1) * q
        c.fillRect(bx - q, by - stem, 2 * q, stem, Col.hex(0xF0E8D6)); c.fillRect(bx, by - stem, q, q, Col.hex(0xC8BCA8))
        c.fillEllipse(bx + 0.0f, by - stem - 0.5f * q, (2.6f * size + 0.5f) * q, (1.6f * size + 0.3f) * q, Col.hex(0xD8321E))
        c.fillRect(bx - q, by - stem - q, q, q, Col.hex(0xFFFFFF)); c.fillRect(bx + q, by - stem, q, q, Col.hex(0xF4EEDD))
        c.fillRect(bx - q, by - stem - 2 * q, 2 * q, q, Col.hex(0xF0624A))
        if (q >= 2) {
            // closer up: more spots, the gills' shadow under the cap, a ring on the stem
            c.set(bx + (1.6f * size * q).toInt(), by - stem - q, Col.hex(0xFFFFFF))
            c.set(bx - (1.8f * size * q).toInt(), by - stem, Col.hex(0xF4EEDD))
            c.set(bx + q / 2, by - stem - (1.6f * q).toInt(), Col.hex(0xFFFFFF))
            c.hline(bx - q, bx + q - 1, by - stem + q, Col.hex(0xC8BCA8))
            c.hline(bx - q, bx + q - 1, by - stem + q + (stem - q) / 3, Col.hex(0xFFFFFF))
        }
    }

    /** A hazel bush at world ([x], [y]), [size] across: leafy and seasonal, bare twigs in winter. */
    private fun bush(x: Float, y: Float, size: Float, seed: Int) {
        val sc = size * K
        s.trees.canopy(iso.sx(x, y), iso.sy(x, y, 0f) - 4.3f * sc, sc, env.season, seed, false)
    }

    /** Snowflakes, falling leaves or blossom drifting down over the diorama, never over the dark round it. */
    private fun drift() {
        val n: Int; val cols: IntArray
        when (env.season) {
            Season.AUTUMN -> { n = 16; cols = intArrayOf(Col.hex(0xF08C2C), Col.hex(0xD6452C), Col.hex(0xECC23E)) }
            Season.WINTER -> { n = 70; cols = intArrayOf(Pal.SNOW_L, Col.hex(0xFFFFFF), Pal.SNOW_M) }
            Season.SPRING -> { n = 10; cols = intArrayOf(Col.hex(0xF4B8CC), Col.hex(0xFFFFFF)) }
            Season.SUMMER -> return
        }
        val x0 = iso.sx(0f, plotD); val x1 = iso.sx(plotW, 0f)
        val y0 = iso.sy(0f, 0f, rise); val y1 = iso.sy(plotW, plotD, 0f)
        val q = detail
        s.fx {
            for (j in 0 until n) {
                val sp = (if (winter) 5f + Noise.rnd(j, 51) * 5f else 8f + Noise.rnd(j, 51) * 6f) * K
                val y = y0 + ((Noise.rnd(j, 52) * (y1 - y0) + t * sp) % (y1 - y0)).toFloat()
                val x = x0 + Noise.rnd(j, 53) * (x1 - x0) + sin(t * (1.1 + Noise.rnd(j, 54)) + j).toFloat() * 3f * K
                val xi = floor(x).toInt(); val yi = floor(y).toInt()
                // over the diorama only: what lies under it in this window (the picture's own pixel)
                if (xi < 0 || yi < 0 || xi >= w - 1 || yi >= h - 1 || !c.inside(xi, yi) || c.ids[c.index(xi, yi)] == 0) continue
                val col = cols[j % cols.size]
                if (q == 1) {
                    c.blend(xi, yi, col, 0.95f)
                    if (!winter || j % 3 == 0) c.blend(xi + 1, yi, col, 0.95f)
                    if (env.season == Season.AUTUMN && ((t * 4).toInt() + j) % 2 == 0) c.blend(xi, yi + 1, Col.scale(col, 0.8f), 0.95f)
                } else flake(xi, yi, j, col)
            }
        }
    }

    /** One of the drift closer up, as big as on the scene's canvas and finer: a six-armed flake, a veined leaf turning, a petal. */
    private fun flake(x: Int, y: Int, j: Int, col: Int) {
        val q = detail
        when (env.season) {
            Season.WINTER -> {
                if (j % 3 == 0) {
                    // a big flake: a little star of arms
                    for (o in -q + 1 until q) { c.blend(x + o, y, col, 0.95f); c.blend(x, y + o, col, 0.8f) }
                    if (q >= 3) { c.blend(x - 1, y - 1, col, 0.6f); c.blend(x + 1, y + 1, col, 0.6f); c.blend(x + 1, y - 1, col, 0.6f); c.blend(x - 1, y + 1, col, 0.6f) }
                } else for (yy in y until y + q - 1) for (xx in x until x + q - 1) c.blend(xx, yy, col, 0.95f)
            }
            Season.AUTUMN -> {
                val flip = ((t * 4).toInt() + j) % 2 == 0
                val lw = if (flip) q else 2 * q
                for (yy in y until y + q) for (xx in x until x + lw) c.blend(xx, yy, if (yy == y + q - 1) Col.scale(col, 0.8f) else col, 0.95f)
                if (flip) for (yy in y + q until y + 2 * q) for (xx in x until x + q) c.blend(xx, yy, Col.scale(col, 0.8f), 0.95f)
                c.blend(x + lw / 2, y + q / 2, Col.scale(col, 0.62f), 0.95f) // the vein
            }
            else -> {
                for (yy in y until y + q) for (xx in x until x + 2 * q) if ((xx - x) + (yy - y) < 2 * q - 1) c.blend(xx, yy, col, 0.95f)
                c.blend(x, y, Col.hex(0xFFFFFF), 0.9f)
            }
        }
    }

    // ------------------------------------------------------------------ wood

    /**
     * A round log lying from world ([ax], [ay]) to ([bx], [by]) with its axis [z] px up and [r] px thick: bark
     * along it, lit from above, and at B its cut end, [cap] as wide as it is tall (0 for none). [snowTop] in winter.
     */
    private fun log(ax: Float, ay: Float, bx: Float, by: Float, z: Float, r: Float, cap: Float, seed: Int, snowTop: Boolean = true) {
        val x0 = iso.sx(ax, ay); val y0 = iso.sy(ax, ay, z)
        val x1 = iso.sx(bx, by); val y1 = iso.sy(bx, by, z)
        val rr = r * K
        val q = detail
        val n = max(1, abs(x1 - x0).toInt())
        for (i in 0..n) {
            val u = i / n.toFloat()
            val cx = x0 + (x1 - x0) * u; val cy = y0 + (y1 - y0) * u
            val px = floor(cx).toInt()
            if (px < c.left || px >= c.right) continue
            val top = floor(cy - rr + 0.5f).toInt(); val bot = floor(cy + rr - 0.5f).toInt()
            for (py in top..bot) {
                val v = (py + 0.5f - cy) / rr
                var col = when { v < -0.5f -> Pal.LOG_L; v < 0.35f -> Pal.LOG_M; else -> Pal.LOG_D }
                // dashes of darker bark along the log: as long at every detail, a picture row thin
                val row = py - top
                if (row in 1 until bot - top && (row + seed) % 3 == 0 && Noise.rnd(i / (3 * q), row, seed) < 0.55f) col = Col.scale(col, 0.8f)
                // closer up, a lit edge along its top and cracks in the bark here and there
                else if (q >= 2 && row == 1 && v < -0.5f) col = Col.mix(Pal.LOG_L, Col.hex(0xFFFFFF), 0.15f)
                if (winter && snowTop && py < top + q) col = Pal.SNOW_L
                c.set(px, py, col)
            }
        }
        if (cap <= 0f) return
        val cw = rr * cap
        c.fillEllipse(x1, y1, cw + 0.5f * q, rr, Pal.LOG_D)
        c.fillEllipse(x1 - 0.3f * q, y1 - 0.3f * q, cw - 0.6f * q, rr - 1f * q, Col.hex(0xE0BC86))
        if (q == 1) {
            if (rr > 2.5f) c.fillEllipse(x1 - 0.3f, y1 - 0.3f, max(0.8f, cw * 0.45f), rr * 0.45f, Col.hex(0xC89A62))
            c.set(floor(x1 - 0.3f).toInt(), floor(y1 - 0.3f).toInt(), Col.hex(0x8A6440))
        } else rings(x1 - 0.3f * q, y1 - 0.3f * q, cw - 0.6f * q, rr - 1f * q, seed)
    }

    /**
     * Closer up, the cut end of a round of wood (an ellipse [rx] × [ry] round ([cx], [cy])): pale sapwood, thin
     * darker growth rings, the heart a little off the middle, a check (a crack) running out from it.
     */
    private fun rings(cx: Float, cy: Float, rx: Float, ry: Float, seed: Int) {
        if (rx < 1f || ry < 1f) return
        val hx = cx + (Noise.rnd(seed, 3) - 0.5f) * rx * 0.25f; val hy = cy + (Noise.rnd(seed, 4) - 0.5f) * ry * 0.25f
        val ca = cos(seed * 1.7f); val sa = sin(seed * 1.7f)
        val nRings = 2.2f + detail * 0.9f
        for (py in max(c.top, floor(cy - ry).toInt())..min(c.bottom - 1, floor(cy + ry).toInt())) {
            for (px in max(c.left, floor(cx - rx).toInt())..min(c.right - 1, floor(cx + rx).toInt())) {
                val ex = (px + 0.5f - cx) / rx; val ey = (py + 0.5f - cy) / ry
                if (ex * ex + ey * ey > 1f) continue
                val dx = (px + 0.5f - hx) / rx; val dy = (py + 0.5f - hy) / ry
                val d = sqrt(dx * dx + dy * dy)
                val ring = d * nRings + Noise.v1(kotlin.math.atan2(dy, dx) * 1.3f + seed, seed) * 0.35f
                var col = when {
                    d < 0.1f -> Col.hex(0x9A7048)
                    ring - floor(ring) < 0.22f -> Col.hex(0xC89A62)
                    d < 0.45f -> Col.hex(0xD8B07A)
                    else -> Col.hex(0xE0BC86)
                }
                if (d > 0.12f && d < 0.8f && abs(-sa * dx + ca * dy) < 0.6f / rx && ca * dx + sa * dy > 0f) col = Col.hex(0x8A6440)
                if (winter && Noise.v2(ex * 2f + seed, ey * 2f, 5) > 0.35f && ey < 0.2f) col = Pal.SNOW_L
                c.set(px, py, col)
            }
        }
    }

    /** Firewood stacked along the back edge between two pairs of stakes, the cut ends toward the fire; snow on top in winter. */
    private fun woodpile() {
        val len = 1.25f; val d = 0.6f; val r = 1.45f
        val x0 = pileX; val x1 = pileX + len
        val rows = 4
        val stakeTop = r * 2f + (rows - 1) * r * 1.72f + 1f
        pileStake(x0 + 0.15f, pileY0 - 0.06f, stakeTop); pileStake(x1 - 0.12f, pileY0 - 0.06f, stakeTop)
        for (row in 0 until rows) {
            val odd = row % 2 == 1
            val count = if (odd) 5 else 6
            val z = r + row * r * 1.72f
            for (i in 0 until count) {
                part()
                val yc = pileY0 + d / 2f + (if (odd) d / 2f else 0f) + i * d
                val stick = (Noise.rnd(i, row, 7) - 0.3f) * 0.2f
                log(x0 + 0.05f, yc, x1 + stick, yc, z, r, 0.95f, 70 + row * 7 + i, snowTop = row == rows - 1 || (odd && (i == 0 || i == count - 1)))
            }
        }
        part()
        pileStake(x0 + 0.15f, pileY1 + 0.06f, stakeTop); pileStake(x1 - 0.12f, pileY1 + 0.06f, stakeTop)
    }

    /** A thin stake holding the end of the woodpile, [top] px high. */
    private fun pileStake(x: Float, y: Float, top: Float) {
        val px = iso.ix(x, y); val y0 = iso.iy(x, y, 0f); val y1 = iso.iy(x, y, top)
        val q = detail
        // [detail] px lit, [detail] px shaded; closer up the lit side in two tones and bark nicks down it
        for (o in 0 until q) c.vline(px + o, y1, y0, if (q >= 2 && o == 0) Col.mix(Pal.LOG_L, Col.hex(0xFFFFFF), 0.15f) else Pal.LOG_L)
        for (o in q until 2 * q) c.vline(px + o, y1, y0, if (q >= 2 && o == q) Pal.LOG_M else Pal.LOG_D)
        if (q >= 2) { var ny = y1 + 3 * q; while (ny < y0 - q) { c.set(px + q - 1, ny, Pal.LOG_M); ny += 5 * q + (ny % 3) } }
        if (winter) c.fillRect(px, y1, 2 * q, q, Pal.SNOW_L)
    }

    /**
     * An upright cylinder at world ([x], [y]), [r] cells in radius, from [z0] to [z1] px: its side coloured by
     * [side] (u −1 at the left edge .. 1 at the right, v 0 at the foot .. 1 at the top, the pixel).
     */
    private inline fun cylinder(x: Float, y: Float, r: Float, z0: Float, z1: Float, crossinline side: (Float, Float, Int, Int) -> Int) {
        val cx = iso.sx(x, y); val rx = r * 4f * K * 1.4142f; val ry = rx / 2f
        val yb = iso.sy(x, y, z0); val yt = iso.sy(x, y, z1)
        for (px in max(c.left, floor(cx - rx).toInt())..min(c.right - 1, floor(cx + rx).toInt())) {
            val u = (px + 0.5f - cx) / rx
            if (abs(u) > 1f) continue
            val e = ry * sqrt(1f - u * u)
            val top = yt + e; val bot = yb + e
            for (py in floor(top).toInt()..floor(bot - 0.01f).toInt()) c.set(px, py, side(u, ((bot - py - 0.5f) / (bot - top)).coerceIn(0f, 1f), px, py))
        }
    }

    /**
     * Bark round an upright round: lit on the left, furrows running up it (their dashes as long at every detail);
     * closer up, the ridges between the furrows catch a thin light.
     */
    private fun bark(u: Float, px: Int, py: Int, seed: Int): Int {
        var col = when { u < -0.55f -> Pal.LOG_L; u < 0.45f -> Pal.LOG_M; else -> Pal.LOG_D }
        val furrow = Noise.v1(u * 7f, seed)
        if (furrow > 0.62f && Noise.rnd(px, py / (3 * detail), seed) < 0.8f) col = Col.scale(col, 0.78f)
        else if (detail >= 2 && furrow in 0.3f..0.34f && u < 0.45f) col = Col.mix(col, Col.hex(0xE0BC86), 0.25f)
        return col
    }

    /** The flat top of a round of wood: bark round the rim, the rings, the heart and a crack from it; snow in winter. */
    private fun grain(cx: Float, cy: Float, rx: Float, ry: Float, seed: Int) {
        if (detail >= 2) {
            // closer up: the bark round the rim, then the rings of [rings] inside it
            c.fillEllipse(cx, cy, rx, ry, Pal.LOG_D)
            c.fillEllipse(cx, cy + ry * 0.08f, rx * 0.96f, ry * 0.9f, Pal.LOG_M)
            rings(cx, cy - ry * 0.02f, rx * 0.84f, ry * 0.84f, seed)
            return
        }
        val ca = cos(seed * 1.3f); val sa = sin(seed * 1.3f)
        for (py in floor(cy - ry).toInt()..floor(cy + ry).toInt()) for (px in floor(cx - rx).toInt()..floor(cx + rx).toInt()) {
            val dx = (px + 0.5f - cx) / rx; val dy = (py + 0.5f - cy) / ry
            val d = sqrt(dx * dx + dy * dy)
            if (d > 1f) continue
            var col = when {
                d > 0.84f -> if (dy > 0.2f) Pal.LOG_M else Pal.LOG_D
                ((d * 4.2f).toInt() % 2 == 0) -> Col.hex(0xE0BC86)
                else -> Col.hex(0xCDA26C)
            }
            if (d < 0.16f) col = Col.hex(0x9A7048)
            if (d in 0.12f..0.8f && abs(-sa * dx + ca * dy) < 0.07f && ca * dx + sa * dy > 0f) col = Col.hex(0x8A6440)
            if (winter && d < 0.86f && Noise.v2(dx * 2f + seed, dy * 2f, 5) > 0.25f) col = if (dy < 0.3f) Pal.SNOW_L else Pal.SNOW_M
            c.set(px, py, col)
        }
    }

    /** The chopping block: a thick round of spruce, its top hacked about. */
    private fun choppingBlock() {
        val r = 0.55f; val hgt = 4.5f
        cylinder(blockX, blockY, r, 0f, hgt) { u, _, px, py -> bark(u, px, py, 5) }
        val rx = r * 4f * K * 1.4142f
        grain(iso.sx(blockX, blockY), iso.sy(blockX, blockY, hgt), rx, rx / 2f, 5)
        // cut marks on the top (closer up, each with the pale fresh wood along its edge)
        val tx = iso.ix(blockX, blockY); val ty = iso.iy(blockX, blockY, hgt)
        val q = detail
        val cut = Col.hex(0x8A6440); val fresh = Col.hex(0xF0D4A0)
        c.line(tx - 4 * q, ty + q, tx - q, ty - q, cut); c.line(tx + 2 * q, ty + 2 * q, tx + 5 * q, ty, cut)
        if (q >= 2) {
            c.line(tx - 4 * q, ty + q - 1, tx - q, ty - q - 1, fresh); c.line(tx + 2 * q, ty + 2 * q - 1, tx + 5 * q, ty - 1, fresh)
            c.line(tx - 3 * q, ty + 2 * q, tx - 2 * q, ty + q + q / 2, cut)
        }
    }

    /** The axe, its bit buried in the chopping block, the pale ash handle up and away to the right. */
    private fun axe() {
        val q = detail
        val hx = iso.ix(blockX, blockY) - q; val hy = iso.iy(blockX, blockY, 4.5f) - 2 * q
        val ex = hx + 5 * K; val ey = hy - 9 * K
        val ash = Col.hex(0xE6C488); val ashM = Col.hex(0xC0955A); val ashD = Col.hex(0x7E5A34)
        val steel = Col.hex(0x6E7280); val steelL = Col.hex(0xB4B8C4); val steelD = Col.hex(0x44464F)
        if (q == 1) {
            c.line(hx, hy, ex, ey, ash); c.line(hx + 1, hy, ex + 1, ey, ashM); c.line(hx + 2, hy, ex + 2, ey, ashD)
            c.set(ex + 1, ey - 1, ash); c.set(ex + 2, ey - 1, ashM); c.set(ex + 3, ey, ashD)
            // the head: the cheek, the poll catching the light, the slit where the bit is buried
            c.fillRect(hx - 4, hy - 2, 8, 4, steel)
            c.hline(hx - 4, hx + 3, hy - 2, steelL); c.vline(hx + 3, hy - 2, hy + 1, steelD)
            c.set(hx - 4, hy - 1, Col.hex(0xE4E8F0)); c.set(hx - 3, hy - 2, Col.hex(0xF0F2F8))
            c.set(hx, hy - 1, steelD); c.set(hx + 1, hy - 1, steelD)
            c.hline(hx - 5, hx + 4, hy + 2, Col.hex(0x3A2A1C))
            return
        }
        // closer up: the handle 3 × [detail] px across, lit to shaded, with a streak of grain and a flared knob
        val tones = intArrayOf(ash, ashM, ashD)
        for (o in 0 until 3 * q) c.line(hx + o, hy, ex + o, ey, if (o == 0) Col.mix(ash, Col.hex(0xFFFFFF), 0.3f) else tones[o / q])
        c.line(hx + q + q / 2, hy - 3 * q, ex + q + q / 2, ey + 2 * q, Col.mix(ash, ashM, 0.6f))
        c.fillEllipse(ex + 1.9f * q, ey - 0.2f * q, 1.9f * q, 1.2f * q, ashM)
        c.fillEllipse(ex + 1.5f * q, ey - 0.5f * q, 1.3f * q, 0.8f * q, ash)
        c.set(ex + 3 * q, ey, ashD)
        // the head: the cheek with its bevel toward the bright edge, the poll, the eye round the handle, the slit
        c.fillRect(hx - 4 * q, hy - 2 * q, 8 * q, 4 * q, steel)
        c.fillRect(hx - 4 * q, hy - 2 * q, 2 * q, 4 * q, Col.mix(steel, steelL, 0.45f))
        c.vline(hx - 4 * q, hy - 2 * q, hy + 2 * q - 1, Col.hex(0xF0F2F8))
        c.hline(hx - 4 * q, hx + 4 * q - 1, hy - 2 * q, steelL)
        for (o in 1..q) c.vline(hx + 4 * q - o, hy - 2 * q, hy + 2 * q - 1, steelD)
        c.fillRect(hx, hy - q, 2 * q, q, steelD)
        c.hline(hx, hx + 2 * q - 1, hy - q - 1, ashD)
        c.set(hx - 2 * q, hy - q, Col.hex(0x8A8E9C)); c.set(hx - q, hy, Col.hex(0x8A8E9C)) // a scratch or two
        for (r in 0 until q) c.hline(hx - 5 * q, hx + 5 * q - 1, hy + 2 * q + r, if (r == 0) Col.hex(0x2A1E14) else Col.hex(0x3A2A1C))
    }

    /** A log to sit on, lying behind the person at world ([x], [y]), a little askew ([skew]); its cut end toward us on the right. */
    private fun seat(x: Float, y: Float, skew: Float, seed: Int) {
        val cx = x - 0.3f; val cy = y - 0.3f
        val len = sqrt(1f + skew * skew)
        val dx = 1.3f / len; val dy = -1.3f * skew / len
        shade(iso.sx(cx + 0.2f, cy + 0.2f), iso.sy(cx + 0.2f, cy + 0.2f, 0f), 13f * K, 2.6f * K, 0.78f, groundId)
        log(cx - dx, cy - dy, cx + dx, cy + dy, 1.45f, 1.45f, 0.55f, seed)
    }

    // ------------------------------------------------------------------ the tent

    /**
     * The pup tent: the canvas slope toward us on the right, from the ridge down to the eave, and the gable
     * toward the fire with its door tied open on a sleeping bag, an A of poles crossed over each end, a pennant
     * on the front one; the lantern inside glows at night.
     */
    private fun tent(lit: Boolean) {
        val hw = tentX1 - tentR
        // the back pair of poles, crossed over the ridge's far end
        poles(tentY0, 0.35f)
        part()
        quadFill(tentR, tentY0, ridge, tentR, tentY1, ridge, tentX1, tentY1, 0f, tentX1, tentY0, 0f) { px, py ->
            val a = ua(px); val b = vb(py)
            val x = (b + 2f * a + ridge * tentX1 / hw) / (4f + ridge / hw)
            slopeColor(px, py, ((x - tentR) / hw).coerceIn(0f, 1f), ((x - a - tentY0) / (tentY1 - tentY0)).coerceIn(0f, 1f))
        }
        // the loops the guy lines tie to, along the eave
        for (gy in floatArrayOf(tentY0 + 0.45f, tentY1 - 0.45f)) iso.px(tentX1 - 0.05f, gy, 1.8f, Pal.CANVAS_D)
        part()
        quadFill(tentX0, tentY1, 0f, tentX1, tentY1, 0f, tentR, tentY1, ridge, tentR, tentY1, ridge) { px, py ->
            gableColor(px, py, wallYx(px, tentY1), wallYz(px, py, tentY1))
        }
        // the doorway
        val y = tentY1 + 0.01f
        if (lit) glow { doorway(y, true) } else doorway(y, false)
        // the front pair of poles along the gable's edges, crossed over its peak, and the pennant
        part()
        poles(tentY1 + 0.04f, 0f)
        val over = 1.2f
        pennant(iso.ix(tentR + (tentR - tentX0) * (over - 1f), tentY1), iso.iy(tentR, tentY1, ridge * over))
    }

    /** A pair of poles in the plane y = [y] along the gable's edges, crossed over the peak; [hide] of their length from the foot is hidden. */
    private fun poles(y: Float, hide: Float) {
        val over = 1.2f
        for (side in 0..1) {
            val xb = if (side == 0) tentX0 + 0.08f else tentX1 - 0.08f
            val xt = tentR + (tentR - xb) * (over - 1f)
            val x0 = xb + (xt - xb) * hide; val z0 = ridge * over * hide
            val bx = iso.ix(x0, y); val by = iso.iy(x0, y, z0)
            val tx = iso.ix(xt, y); val ty = iso.iy(xt, y, ridge * over)
            pole(bx, by, tx, ty, if (side == 0) Pal.WOOD_L else Pal.WOOD_M, Pal.WOOD_D)
        }
        // the lashing where they cross
        val ax = iso.ix(tentR, y); val ay = iso.iy(tentR, y, ridge)
        val q = detail
        wrap(ax - q, ax + 3 * q - 1, ay - q, Col.hex(0xD8C49A)); wrap(ax - q, ax + 3 * q - 1, ay + q, Col.hex(0xD8C49A))
    }

    /** A pole from ([x0], [y0]) to ([x1], [y1]), [detail] px of [lit] then [detail] px of [dark]; closer up a thin highlight down its lit side. */
    private fun pole(x0: Int, y0: Int, x1: Int, y1: Int, lit: Int, dark: Int) {
        val q = detail
        for (o in 0 until 2 * q) c.line(x0 + o, y0, x1 + o, y1, when {
            o >= q -> if (q >= 2 && o == q) Col.mix(lit, dark, 0.5f) else dark
            q >= 2 && o == 0 -> Col.mix(lit, Col.hex(0xFFFFFF), 0.18f)
            else -> lit
        })
    }

    /**
     * A band of rope across [xa]..[xb] from row [y], [detail] rows thick: a lashing's wrap. Closer up it is round
     * (lit on top, shaded below) and twisted (a strand's shadow every third pixel along the diagonal).
     */
    private fun wrap(xa: Int, xb: Int, y: Int, col: Int) {
        if (detail == 1) { c.hline(xa, xb, y, col); return }
        val lit = Col.mix(col, Col.hex(0xFFFFFF), 0.3f); val dark = Col.scale(col, 0.7f)
        for (r in 0 until detail) for (x in min(xa, xb)..max(xa, xb)) {
            val base = when (r) { 0 -> lit; detail - 1 -> dark; else -> col }
            c.set(x, y + r, if ((x + r) % 3 == 0 && r < detail - 1) Col.scale(col, 0.82f) else base)
        }
    }

    private fun doorway(y: Float, lit: Boolean) {
        quadFill(tentR - doorW, y, 0f, tentR + doorW, y, 0f, tentR, y, doorZ, tentR, y, doorZ) { px, py ->
            val x = wallYx(px, y); val z = wallYz(px, py, y)
            val dx = (x - tentR) / doorW; val zz = (z / doorZ).coerceIn(0f, 1f)
            if (lit) {
                // a candle lantern on the floor at the back: brightest low in the middle
                val d = hypot(dx * 0.9f, zz * 1.25f) + (Dither.at(px, py) - 0.5f) * 0.25f
                when {
                    d < 0.3f -> Pal.WINDOW_LIT_HI
                    d < 0.62f -> Pal.WINDOW_LIT
                    d < 0.92f -> Col.hex(0xE8A048)
                    else -> Col.hex(0xB8683A)
                }
            } else when {
                // the dim inside: a sleeping bag rolled out along the floor with a red blanket on it, the far gable's canvas
                zz < 0.2f && abs(dx + 0.05f) < 0.62f - zz -> if (zz < 0.07f) Col.hex(0x2F5040) else if (dx < -0.25f) Col.hex(0x5E8A6A) else Col.hex(0x46705A)
                zz < 0.1f -> Col.hex(0x4A4632)
                zz < 0.3f && dx > 0.1f && dx < 0.5f - zz -> if (zz < 0.24f) Col.hex(0xB8452E) else Col.hex(0x8A3426)
                else -> Col.mix(Col.hex(0x6A5438), Col.hex(0x2E2419), zz * 1.1f + (Dither.at(px, py) - 0.5f) * 0.25f)
            }
        }
    }

    /** The slope: canvas in the shade, darker toward the eave, folds hanging down it, a seam, a muddy hem; snow in winter. */
    private fun slopeColor(px: Int, py: Int, u: Float, v: Float): Int {
        val d = u * 0.9f + (Dither.at(px, py) - 0.5f) * 0.3f
        var col = when {
            d < 0.28f -> Col.mix(Pal.CANVAS_L, Pal.CANVAS_M, 0.5f)
            d < 0.62f -> Pal.CANVAS_M
            else -> Col.mix(Pal.CANVAS_M, Pal.CANVAS_D, 0.6f)
        }
        val fold = Noise.v1(v * 7f + u * 1.1f, 23)
        if (fold > 0.7f) col = Col.mix(col, Pal.CANVAS_D, 0.55f)
        else if (fold > 0.62f && Dither.at(px, py) < 0.5f) col = Col.mix(col, Pal.CANVAS_D, 0.35f)
        else if (fold < 0.2f) col = Col.mix(col, Pal.CANVAS_L, 0.5f)
        if (detail >= 2) {
            // closer up: the weave, and a row of stitches either side of the seam
            col = weave(col, px, py)
            if (abs(abs(v - 0.5f) - 0.045f) < 0.22f / K && ((u * 10f * K).toInt() and 1) == 0) col = Col.mix(col, Pal.CANVAS_D, 0.65f)
        }
        if (abs(v - 0.5f) < 0.02f) col = Col.scale(Pal.CANVAS_D, 0.9f)
        if (u < 0.05f) col = Pal.CANVAS_L
        if (u > 0.9f) col = Col.mix(col, Col.hex(0x6A5A50), 0.55f)
        if (winter) {
            val cover = 0.62f - u + (Noise.v2(v * 6f, u * 4f, 3) - 0.5f) * 0.35f
            if (cover > 0f) col = if (cover > 0.12f || Dither.at(px, py) < 0.5f) Pal.SNOW_L else Pal.SNOW_M
        }
        return col
    }

    /** The gable: pale canvas with soft folds, the door flaps rolled back beside the doorway and tied, a muddy hem. */
    private fun gableColor(px: Int, py: Int, x: Float, z: Float): Int {
        val dx = abs(x - tentR)
        val open = doorW * (1f - z / doorZ)
        var col = if (Noise.v1(x * 5f, 29) > 0.66f) Col.mix(Pal.CANVAS_L, Pal.CANVAS_M, 0.55f) else Pal.CANVAS_L
        if (detail >= 2) col = weave(col, px, py)
        val flap = dx - max(open, 0f)
        if (z < doorZ && flap < 0.24f) {
            // the flap folded back beside the doorway and tied
            col = when {
                flap < 0.05f -> Col.scale(Pal.CANVAS_D, 0.85f)
                flap < 0.15f -> Pal.CANVAS_M
                else -> Col.mix(Pal.CANVAS_M, Pal.CANVAS_D, 0.45f)
            }
            if (abs(z - doorZ * 0.4f) < 0.45f) col = Col.hex(0x8A6A48)
        }
        if (z < 1.1f) col = Col.mix(col, Col.hex(0x6A5A50), 0.5f)
        if (winter && z < 0.8f + Noise.v1(x * 4f, 31) * 0.8f) col = Pal.SNOW_M
        return col
    }

    /** A little pennant in the flag's colours at the top of the front pole, fluttering. */
    private fun pennant(x: Int, y: Int) {
        val flap = sin(t * 4.0).toFloat()
        val q = detail
        // a column of the picture at a time: closer up the same pennant, its wave and its three stripes finer
        for (i in 0 until 8 * q) {
            val fi = i / q.toFloat()
            val half = (3.5f * q * (1f - fi / 8f)).toInt()
            val yy = y + q + ((sin(t * 4.0 - fi * 0.7).toFloat() * 0.8f * fi / 8f + flap * 0.3f) * q).toInt()
            val third = (half * 2 + 1) / 3f
            for (j in -half..half) c.set(x + i, yy + j, if (q == 1) {
                when { j < -half / 3 -> Pal.FLAG_WHITE; j <= half / 3 -> Pal.FLAG_BLUE; else -> Pal.FLAG_RED }
            } else when {
                j + half < third -> Pal.FLAG_WHITE
                j + half < 2 * third -> Pal.FLAG_BLUE
                else -> Pal.FLAG_RED
            })
        }
    }

    // ------------------------------------------------------------------ the hearth

    /** One leg of the tripod, from its foot at ([dx], [dy]) off the fire up past the top, where the legs cross. */
    private fun tripodLeg(dx: Float, dy: Float) {
        val ax = iso.sx(fireX, fireY); val ay = iso.sy(fireX, fireY, apexZ)
        val fx = iso.sx(fireX + dx, fireY + dy); val fy = iso.sy(fireX + dx, fireY + dy, 0f)
        val ex = (ax + (ax - fx) * 0.1f).toInt(); val ey = (ay + (ay - fy) * 0.1f).toInt()
        pole(fx.toInt(), fy.toInt(), ex, ey, Pal.WOOD_L, Pal.WOOD_D)
    }

    /** The rope lashing where the legs cross. */
    private fun lashing() {
        val ax = iso.ix(fireX, fireY); val ay = iso.iy(fireX, fireY, apexZ)
        val q = detail
        for (j in -1..1) wrap(ax - 2 * q, ax + 4 * q - 1, ay + j * 2 * q, Col.hex(0xD8C49A))
    }

    /** The chain from the top of the tripod down to the kettle's bail: alternating pixels, closer up its links. */
    private fun chain() {
        val ax = iso.ix(fireX, fireY); val top = iso.iy(fireX, fireY, apexZ) + 2 * detail; val bot = iso.iy(fireX, fireY, 17.5f)
        if (detail == 1) { for (y in top..bot) c.set(ax, y, if (y % 2 == 0) Pal.STONE_D else Pal.STONE_L); return }
        val q = detail
        var y = top
        var m = 0
        while (y <= bot) {
            val bottom = min(bot, y + 2 * q - 1)
            if (m % 2 == 0) {
                // face on: a ring, lit on the left
                for (yy in y..bottom) {
                    if (yy == y || yy == bottom) c.set(ax, yy, Pal.STONE_D)
                    else { c.set(ax - 1, yy, Pal.STONE_L); c.set(ax + 1, yy, Pal.STONE_D) }
                }
            } else c.vline(ax, y - 1, bottom + 1, Pal.STONE_M) // edge on, through the rings
            y += 2 * q - 1; m++
        }
    }

    /** Canvas closer up: the weave, a pixel a shade darker every other, and now and then a slub in the thread. */
    private fun weave(col: Int, px: Int, py: Int): Int = when {
        detail < 2 -> col
        ((px + py) and 1) == 0 -> Col.scale(col, 0.955f)
        Noise.rnd(px / 3, py, 5) < 0.025f -> Col.mix(col, Col.hex(0xFFF6DA), 0.3f)
        else -> col
    }

    /** A dot of the picture: one pixel on the scene's canvas, [detail] × [detail] closer up. */
    private fun dot(x: Int, y: Int, col: Int) = c.fillRect(x, y, detail, detail, col)

    /** The ring of stones round the fire: its back half, or its front half; each stone sooty on the side toward the flames. */
    private fun hearth(front: Boolean) {
        val n = 12
        for (i in 0 until n) {
            val a = (i + 0.5f) / n * 2f * PI.toFloat()
            if ((sin(a) + cos(a) > 0f) != front) continue
            part()
            hearthStone(fireX + cos(a) * fireR, fireY + sin(a) * fireR, 0.4f + Noise.rnd(i, 61) * 0.16f, 61 + i, cos(a), sin(a))
        }
    }

    private fun hearthStone(x: Float, y: Float, r: Float, seed: Int, ox: Float, oy: Float) {
        val cx = iso.sx(x, y); val cy = iso.sy(x, y, 0f)
        val rx = r * 4f * K; val ry = r * 2.8f * K
        val warm = Noise.rnd(seed, 1) < 0.5f
        val l = if (warm) Pal.STONE_L else Col.hex(0xA8A296)
        val m = if (warm) Pal.STONE_M else Col.hex(0x88826F)
        val d = if (warm) Pal.STONE_D else Col.hex(0x66615A)
        c.fillEllipse(cx, cy - ry * 0.45f, rx, ry, d)
        c.fillEllipse(cx - rx * 0.12f, cy - ry * 0.6f, rx * 0.85f, ry * 0.8f, m)
        c.fillEllipse(cx - rx * 0.35f, cy - ry * 0.85f, rx * 0.4f, ry * 0.35f, l)
        // soot on the side toward the fire
        val sx = (oy - ox); val sy = -(ox + oy) * 0.5f
        val sl = max(0.01f, hypot(sx, sy))
        val scx = cx + sx / sl * rx * 0.5f; val scy = cy - ry * 0.55f + sy / sl * ry * 0.45f
        c.fillEllipse(scx, scy, rx * 0.5f, ry * 0.45f, Col.scale(d, 0.62f))
        if (detail >= 2) {
            // closer up: grit and flecks over the stone, a crack, the soot feathering out
            val gx = cx - rx * 0.12f; val gy = cy - ry * 0.6f
            for (py in max(c.top, floor(gy - ry * 0.8f).toInt())..min(c.bottom - 1, floor(gy + ry * 0.8f).toInt())) {
                for (px in max(c.left, floor(gx - rx * 0.85f).toInt())..min(c.right - 1, floor(gx + rx * 0.85f).toInt())) {
                    val ex = (px + 0.5f - gx) / (rx * 0.85f); val ey = (py + 0.5f - gy) / (ry * 0.8f)
                    if (ex * ex + ey * ey > 1f) continue
                    val n = Noise.rnd(px, py, seed)
                    val tx = (px + 0.5f - scx) / (rx * 0.75f); val ty = (py + 0.5f - scy) / (ry * 0.7f)
                    if (tx * tx + ty * ty < 1f && Dither.at(px, py) < 0.5f) c.set(px, py, Col.scale(d, 0.75f))
                    else if (n < 0.06f) c.set(px, py, d)
                    else if (n > 0.95f) c.set(px, py, l)
                }
            }
            val a = Noise.rnd(seed, 9) * 3f
            c.line((cx - rx * 0.3f + cos(a) * rx * 0.1f).toInt(), (cy - ry * 0.7f).toInt(), (cx + rx * 0.1f + cos(a) * rx * 0.3f).toInt(), (cy - ry * 0.3f).toInt(), Col.scale(d, 0.85f))
        }
    }

    /**
     * The fire: ashes and crossed logs, flames licking up round the kettle's belly, embers winking, sparks and smoke
     * rising, and its light; flaring up (a dialog's "flare"), taller and wider, more sparks, a wider light.
     */
    private fun blaze() {
        logs(fireX, fireY, fireR)
        if (detail >= 2) coals()
        part(0)
        val cx = iso.sx(fireX, fireY); val cy = iso.sy(fireX, fireY, 0f)
        val flick = sin(t * 11).toFloat() * 0.5f + sin(t * 7.3 + 2).toFloat() * 0.5f
        // tapped ([pokes]): it leaps up at once and settles again, a burst of sparks flying
        val pk = poked("fire"); val a = pk?.age(t) ?: 0f
        val d = if (pk?.step == 1) 2.4f else 1.6f
        val leap = if (pk == null) 0f else (if (pk.step == 1) 1f else 0.7f) * PokeArt.ease(a, 0f, 0.12f) * (1f - PokeArt.ease(a, d * 0.35f, d))
        val flare = max(fxOn("flare"), leap)
        glow {
            val base = cy - 2f * K
            val hgt = (10.5f + flick * 1.6f) * K * (1f + 0.6f * flare)
            val wide = 1f + 0.3f * flare
            for (tongue in 0..4) {
                val off = (tongue - 2) * 1.9f * K * wide
                val th = hgt * (1f - abs(tongue - 2) * 0.16f) * (0.78f + 0.22f * sin(t * (9 + tongue) + tongue * 1.7).toFloat())
                val sway = sin(t * 6 + tongue).toFloat() * 0.8f * K
                c.fillEllipse(cx + off + sway * 0.5f, base - th * 0.42f, 1.9f * K * wide, th * 0.5f, Pal.FLAME[3])
                c.fillEllipse(cx + off * 0.8f + sway * 0.6f, base - th * 0.34f, 1.25f * K * wide, th * 0.36f, Pal.FLAME[2])
            }
            c.fillEllipse(cx, base - hgt * 0.2f, 2f * K, hgt * 0.24f, Pal.FLAME[1])
            c.fillEllipse(cx, base - hgt * 0.08f, 1.3f * K, hgt * 0.11f, Pal.FLAME[0])
            if (detail >= 2) licks(cx, base, hgt)
            for (e in 0 until 7) {
                val ex = (cx + (Noise.rnd(e, 35) - 0.5f) * fireR * 5f * K).toInt(); val ey = (cy - Noise.rnd(e, 36) * K * 1.5f).toInt()
                if (sin(t * 3 + e) > -0.3) dot(ex, ey, Pal.FLAME[if (e % 2 == 0) 2 else 3])
            }
        }
        val q = detail
        s.sparks(cx - 5f * q, cy - 16f * q, 3, 1)
        s.sparks(cx + 8f * q, cy - 18f * q, 2, 2)
        if (flare > 0.05f) s.sparks(cx + 1f * q, cy - 22f * q, (3 + 7 * flare).toInt(), 4)
        if (pk != null) s.fx {
            val big = pk.step == 1
            PokeArt.sparks(c, cx, cy - 5f * K, a / (d * 0.8f), if (big) 30 else 16, 17 + pk.step, if (big) 34f * K else 24f * K, if (big) 40f * K else 28f * K, detail + 1, fall = 1.1f)
        }
        s.smoke(cx + 2f * q, cy - 48f * q, 1.1f, 3)
        s.light(cx, cy - 7f * K, 44f + 26f * flare, 0.92f + flick * 0.06f + 0.3f * flare)
    }

    /** Closer up, the embers in the ash bed under the logs: coals glowing and dimming in turn, grey ash between. */
    private fun coals() {
        val cx = iso.sx(fireX, fireY); val cy = iso.sy(fireX, fireY, 0f) - K
        val rx = fireR * 2.2f * K; val ry = fireR * 1f * K
        glow {
            for (py in max(c.top, floor(cy - ry).toInt())..min(c.bottom - 1, floor(cy + ry).toInt())) {
                for (px in max(c.left, floor(cx - rx).toInt())..min(c.right - 1, floor(cx + rx).toInt())) {
                    val ex = (px + 0.5f - cx) / rx; val ey = (py + 0.5f - cy) / ry
                    if (ex * ex + ey * ey > 1f) continue
                    val n = Noise.rnd(px / 2, py / 2, 57)
                    if (n > 0.5f) continue
                    val pulse = 0.5f + 0.5f * sin(t * (1.5 + n * 3) + n * 40).toFloat()
                    c.set(px, py, when {
                        n < 0.12f && pulse > 0.6f -> Pal.FLAME[1]
                        n < 0.3f -> if (pulse > 0.35f) Pal.FLAME[2] else Pal.FLAME[3]
                        else -> Pal.FLAME[4]
                    })
                }
            }
        }
        c.penEmissive = false
    }

    /** Closer up, thin licks of flame between the big tongues and a bright core rising up the middle. */
    private fun licks(cx: Float, base: Float, hgt: Float) {
        for (l in 0 until 7) {
            val ph = (t * (2.3 + l * 0.37) + l * 1.9) % 1.0
            val lx = cx + (Noise.rnd(l, 43) - 0.5f) * 4.4f * K + sin(t * 7 + l).toFloat() * 0.4f * K
            val top = base - hgt * (0.45f + 0.5f * Noise.rnd(l, 44)) * (0.7f + 0.3f * ph.toFloat())
            var y = base - hgt * 0.15f
            var x = lx
            while (y > top) {
                c.set(x.toInt(), y.toInt(), if (y < top + (base - top) * 0.3f) Pal.FLAME[2] else Pal.FLAME[1])
                y -= 1f; x += sin(y * 0.35f + t * 9 + l).toFloat() * 0.45f
            }
        }
        // the white heart of the fire, low in the middle
        c.fillEllipse(cx, base - hgt * 0.06f, 0.6f * K, hgt * 0.06f, Col.hex(0xFFFDF0))
    }

    /** The kettle on its chain: a round iron belly with a lid and a bail, a spout, the fire's glow on its underside; steam in the morning and evening, and when a dialog says it's hot. */
    private fun kettle(lit: Boolean) {
        val cx = iso.sx(fireX, fireY)
        val top = iso.sy(fireX, fireY, 14.5f); val bot = iso.sy(fireX, fireY, 8f)
        val tones = intArrayOf(Col.hex(0x1A181E), Col.hex(0x2E2B34), Col.hex(0x4A4652), Col.hex(0x6E6A78), Col.hex(0xB4B0BE))
        val h = bot - top
        val k = detail
        // the bail, arching up to the chain: a wire a picture px thin
        for (i in 0..24 * k) {
            val a = i / (24f * k) * PI.toFloat()
            c.set(floor(cx + cos(a) * 6.5f * k).toInt(), floor(top + 1f * k - sin(a) * 7f * k).toInt(), if (i > 12 * k) tones[3] else tones[2])
        }
        // the spout, out to the right: three bands, [detail] rows each
        for (r in 0 until 3 * k) {
            val band = r / k
            val col = when (band) { 0 -> if (k >= 2 && r == 0) tones[4] else tones[3]; 1 -> tones[1]; else -> tones[0] }
            // the lowest band is a little shorter
            val endX = if (band == 2) 9f * k else 10f * k
            val endY = if (band == 2) k + r else r
            c.line(floor(cx + 5f * k).toInt(), floor(top + (5 * k + r)).toInt(), floor(cx + endX).toInt(), floor(top + endY).toInt(), col)
        }
        // the belly: round, lit from the upper left, the fire glowing on its underside
        val hw = 7.4f * k
        for (py in max(c.top, floor(top).toInt())..min(c.bottom - 1, floor(bot).toInt())) {
            val v = (py + 0.5f - top) / h
            val qq = abs((v - 0.48f) / 0.56f)
            val half = hw * (1f - qq * qq * qq).coerceAtLeast(0f).pow(1f / 3f)
            for (px in floor(cx - half).toInt()..floor(cx + half - 0.01f).toInt()) {
                val nx = (px + 0.5f - cx) / hw; val ny = (v - 0.48f) / 0.56f
                val nz = sqrt(max(0f, 1f - nx * nx - ny * ny))
                val b = -0.55f * nx - 0.55f * ny + 0.62f * nz + (Dither.at(px, py) - 0.5f) * 0.12f
                var col = when {
                    b > 0.86f -> tones[4]
                    b > 0.6f -> tones[3]
                    b > 0.28f -> tones[2]
                    b > -0.05f -> tones[1]
                    else -> tones[0]
                }
                if (k >= 2) {
                    // closer up: the seam round the belly with its rivets, soot patches low down
                    val seam = abs(v - 0.3f) < 0.6f / h
                    if (seam) col = if (Math.floorMod(px - floor(cx).toInt(), 2 * k + 1) == 0) tones[4] else tones[0]
                    else if (ny > 0.25f && Noise.rnd(px / 2, py / 2, 67) < 0.3f) col = Col.scale(col, 0.75f)
                }
                val glowing = ny > 0.45f
                if (glowing) col = Col.mix(col, Pal.FLAME[3], ((ny - 0.45f) * 1.3f).coerceIn(0f, 0.65f))
                c.penEmissive = glowing
                c.set(px, py, col)
            }
        }
        c.penEmissive = false
        // the rim and the lid with its knob
        c.fillEllipse(cx, top + 0.5f * k, 5.4f * k, 1.9f * k, tones[3])
        c.fillEllipse(cx, top + 0.2f * k, 4.6f * k, 1.4f * k, tones[1])
        c.hline(floor(cx - 3f * k).toInt(), floor(cx + 1f * k).toInt(), floor(top - 0.5f * k).toInt(), tones[2])
        c.fillRect(floor(cx - 1f * k).toInt(), floor(top - 2.5f * k).toInt(), 2 * k, 2 * k, tones[3])
        c.fillRect(floor(cx - 1f * k).toInt(), floor(top - 2.5f * k).toInt(), k, 1, tones[4])
        if (k >= 2) {
            // the lid's edge a thin bright line, its hinge and the bail's lugs
            c.hline(floor(cx - 4.2f * k).toInt(), floor(cx - 1.5f * k).toInt(), floor(top - 0.9f * k).toInt() + 1, tones[4])
            c.fillRect(floor(cx - 6.8f * k).toInt(), floor(top + 0.4f * k).toInt(), k, 2 * k, tones[2])
            c.fillRect(floor(cx + 6.1f * k).toInt(), floor(top + 0.4f * k).toInt(), k, 2 * k, tones[1])
        }
        val steam = fxOn("steam")
        if (s.hour in 6f..10f || lit || steam > 0.05f) s.smoke(cx + 10.5f * k, top - 1f * k, 0.35f + 0.45f * steam, 9)
        wisps(cx, top - 3f * k, steam, 17)
    }

    // ------------------------------------------------------------------ the bucket and the stump

    /** The water bucket: staves bound with two iron hoops, a rope handle, the water catching the firelight at night (ice in winter). */
    private fun bucket(lit: Boolean) {
        val r = 0.42f; val hgt = 5.5f
        val rx = r * 4f * K * 1.4142f; val ry = rx / 2f
        cylinder(bucketX, bucketY, r, 0f, hgt) { u, v, _, _ ->
            val stave = floor((u + 1f) * 3.5f).toInt()
            val gap = abs((u + 1f) * 3.5f - stave - 0.5f) > 0.42f
            var col = when {
                u < -0.55f -> Pal.WOOD_L
                u > 0.5f -> Pal.WOOD_D
                stave % 2 == 0 -> Pal.WOOD_M
                else -> Col.mix(Pal.WOOD_M, Pal.WOOD_L, 0.35f)
            }
            if (gap && abs(u) < 0.9f) col = Col.scale(col, 0.8f)
            val hoop = v in 0.16f..0.28f || v in 0.7f..0.82f
            if (hoop) col = if (u < -0.3f) Col.hex(0x6A6670) else Col.hex(0x44404A)
            // closer up: the hoops' rivets, a lit edge along each hoop's top
            if (detail >= 2 && hoop) {
                if (abs(v - 0.22f) < 0.03f && abs(abs(u) - 0.55f) < 0.07f || abs(v - 0.76f) < 0.03f && abs(u + 0.1f) < 0.07f) col = Col.hex(0xA8A4B0)
                else if ((v > 0.265f && v <= 0.28f || v > 0.805f && v <= 0.82f) && u < 0.3f) col = Col.hex(0x88848F)
            }
            col
        }
        val q = detail
        val cx = iso.sx(bucketX, bucketY); val ty = iso.sy(bucketX, bucketY, hgt)
        c.fillEllipse(cx, ty, rx, ry, Pal.WOOD_D)
        if (q >= 2) {
            // the staves' ends round the rim
            for (i in 0 until 14) {
                val a = i / 14f * 6.283f
                c.set((cx + cos(a) * (rx - 0.6f * q)).toInt(), (ty + sin(a) * (ry - 0.4f * q)).toInt(), Pal.WOOD_M)
            }
        }
        c.fillEllipse(cx, ty + 0.4f * q, rx - 1.4f * q, ry - 0.9f * q, if (winter) Pal.ICE_M else Pal.WATER_M)
        c.hline((cx - 2f * q).toInt(), (cx - 1f * q).toInt(), (ty - 0.2f * q).toInt(), if (winter) Pal.ICE_L else Pal.WATER_L)
        if (q >= 2) {
            // a ripple on the water (a crack in the ice)
            if (winter) c.line((cx - 1.5f * q).toInt(), (ty + 0.8f * q).toInt(), (cx + 2f * q).toInt(), (ty + 0.1f * q).toInt(), Pal.ICE_L)
            else for (i in 0 until 10 * q) {
                val a = i / (10f * q) * 6.283f
                if (sin(a) < 0f) c.set((cx + 0.5f * q + cos(a) * 1.8f * q).toInt(), (ty + 0.6f * q + sin(a) * 0.7f * q).toInt(), Pal.WATER_L)
            }
        }
        if (lit && !winter) glow { dot((cx + 1f * q).toInt(), (ty + 0.6f * q).toInt(), Pal.FLAME[1]); dot((cx + 2f * q).toInt(), (ty + 0.6f * q).toInt(), Pal.FLAME[2]) }
        // the rope handle (closer up, twisted)
        val rope = Col.hex(0xC8AC72)
        for (i in 0..16 * q) {
            val a = i / (16f * q) * PI.toFloat()
            c.set((cx - cos(a) * (rx - 0.5f * q)).toInt(), (ty - sin(a) * 5f * q).toInt(), if (q >= 2 && (i / 2) % 3 == 0) Col.scale(rope, 0.75f) else rope)
            if (q >= 3) c.set((cx - cos(a) * (rx - 0.5f * q)).toInt(), (ty - sin(a) * 5f * q).toInt() - 1, Col.mix(rope, Col.hex(0xFFFFFF), 0.25f))
        }
    }

    /**
     * The stump of a spruce felled long ago: roots gripping the ground, furrowed bark, the rings on its flat
     * top and a splinter left standing at the back; moss at its foot, mushrooms in autumn, snow in winter.
     */
    private fun stump() {
        val r = 0.66f; val hgt = 6.5f
        val rx = r * 4f * K * 1.4142f
        // the roots at the back, then the stump, then the roots in front
        for (a in floatArrayOf(3.6f, 4.9f)) root(a, r)
        cylinder(stumpX, stumpY, r, 0f, hgt) { u, v, px, py ->
            val moss = v < 0.3f - u * 0.15f && u < 0.1f && Noise.rnd(px, py, 55) < 0.6f && !winter
            if (moss) (if (Noise.rnd(px, py, 56) < 0.5f) Col.hex(0x5E8A3A) else Col.hex(0x46702E)) else bark(u, px, py, 9)
        }
        val cx = iso.sx(stumpX, stumpY); val ty = iso.sy(stumpX, stumpY, hgt)
        grain(cx, ty, rx, rx / 2f, 9)
        // the splinter left standing at the back of the cut (closer up, split along its grain)
        val sx = floor(cx + rx * 0.45f).toInt(); val sy = floor(ty - rx * 0.3f).toInt()
        sprite(sx, sy) {
            c.fillRect(sx - 2, sy - 4, 3, 4, Pal.LOG_M)
            c.vline(sx - 2, sy - 4, sy - 1, Pal.LOG_L); c.set(sx, sy - 5, Pal.LOG_L); c.set(sx - 1, sy - 5, Col.hex(0xE0BC86))
        }
        val q = detail
        if (q >= 2) {
            c.vline(sx - q, sy - 4 * q, sy - 1, Col.hex(0xC89A62)); c.vline(sx, sy - 5 * q + 1, sy - 1, Col.scale(Pal.LOG_M, 0.8f))
            c.set(sx - q - 1, sy - 5 * q + 1, Col.hex(0xF0D4A0)); c.set(sx + 1, sy - 5 * q + 2, Col.hex(0xE0BC86))
        }
        for (a in floatArrayOf(0.3f, 1.55f, 2.5f)) root(a, r)
        if (env.season == Season.AUTUMN) {
            // two mushrooms at its foot
            for ((mx, my, sz) in listOf(Triple(-rx - 1f * q, 1f * q, 1f), Triple(-rx + 3f * q, 3f * q, 0.8f))) {
                val bx = (cx + mx).toInt(); val by = (iso.sy(stumpX, stumpY, 0f) + my).toInt()
                c.fillRect(bx, by - (4 * sz * q).toInt(), 2 * q, (4 * sz * q).toInt(), Col.hex(0xEADFC8))
                c.fillEllipse(bx + 1f * q, by - 4f * sz * q - 1f * q, 3f * sz * q, 1.8f * sz * q, Col.hex(0x8A4A26))
                c.fillRect(bx, by - (4 * sz * q).toInt() - 2 * q, q, q, Col.hex(0xB06A3A))
                if (q >= 2) {
                    // the gills under the cap, a lit crown
                    c.hline(bx - q, bx + 3 * q - 1, by - (4 * sz * q).toInt() - 1, Col.hex(0xC8A888))
                    c.hline(bx, bx + 2 * q - 1, (by - 4f * sz * q - 1f * q - 1.8f * sz * q + 1f).toInt(), Col.hex(0xB06A3A))
                }
            }
        }
    }

    /** A root leaving the stump at angle [a] (world), reaching into the ground; [detail] times as thick, closer up with furrows. */
    private fun root(a: Float, r: Float) {
        val x0 = stumpX + cos(a) * r * 0.8f; val y0 = stumpY + sin(a) * r * 0.8f
        val x1 = stumpX + cos(a) * r * 1.6f; val y1 = stumpY + sin(a) * r * 1.6f
        val ax = iso.sx(x0, y0); val ay = iso.sy(x0, y0, 1.8f)
        val bx = iso.sx(x1, y1); val by = iso.sy(x1, y1, 0f)
        val q = detail
        val steps = 10 * q
        for (i in 0..steps) {
            val tt = i / steps.toFloat()
            val x = (ax + (bx - ax) * tt).toInt(); val y = (ay + (by - ay) * tt).toInt()
            val th = ((3.2f * (1f - tt) + 1f) * q).toInt()
            for (j in 0 until th) {
                var col = if (j == th - 1) Pal.LOG_L else if (j == 0) Pal.LOG_D else Pal.LOG_M
                if (q >= 2 && j in 1 until th - 1 && (j + i / q) % 3 == 0 && Noise.rnd(i / q, j, 13) < 0.5f) col = Col.scale(col, 0.82f)
                c.set(x, y - j, col)
            }
        }
        if (winter) dot(bx.toInt(), by.toInt() - q, Pal.SNOW_L)
    }

    companion object {
        /** The moon's craters: their middles (scene canvas px from the moon's) and squared radii, as at detail 1. */
        private val CRATERS = floatArrayOf(3f, -3f, 3f, 5f, 2f, 2f, 2f, 5f, 2f, -3f, -1f, 2f, -1f, 6f, 1.5f)

        /** The moon's seas: their middles and radii, in the moon's radii from its middle (x to the right, y down). */
        private val MARIA = floatArrayOf(
            -0.3f, -0.42f, 0.27f, // Imbrium
            0.2f, -0.36f, 0.17f, // Serenitatis
            0.34f, -0.05f, 0.21f, // Tranquillitatis
            0.68f, -0.28f, 0.11f, // Crisium
            -0.6f, 0.02f, 0.3f, // Procellarum
            -0.18f, 0.36f, 0.17f, // Nubium
            0.55f, 0.18f, 0.13f, // Fecunditatis
            0.34f, 0.32f, 0.09f, // Nectaris
        )

        /** The moon's way across the campfire's sky: this many scene canvas px from where it rises to where it sets, and down so far at either end. */
        private const val MOON_SPAN = 190f
        private const val MOON_DROP = 30

        /** The spruces along the back edges: x, y, height (px), seed; back to front. */
        private val TREES = arrayOf(
            floatArrayOf(0.4f, 0.45f, 24f, 3f), floatArrayOf(0.35f, 2.0f, 27f, 8f), floatArrayOf(1.8f, 0.35f, 28f, 5f),
            floatArrayOf(0.4f, 3.8f, 26f, 12f), floatArrayOf(3.6f, 0.4f, 30f, 7f), floatArrayOf(5.4f, 0.35f, 27f, 10f),
            floatArrayOf(0.35f, 5.9f, 24f, 15f), floatArrayOf(7.2f, 0.3f, 25f, 4f), floatArrayOf(0.4f, 7.9f, 22f, 9f),
            floatArrayOf(9.9f, 0.3f, 22f, 14f), floatArrayOf(0.5f, 10.1f, 24f, 11f),
        ).sortedBy { it[0] + it[1] }

        /** Ferns (x, y, seed), stones (x, y, size, seed) and fly agarics (x, y, size) at the foot of the woods. */
        private val FERNS = arrayOf(
            floatArrayOf(1.3f, 1.6f, 1f), floatArrayOf(2.9f, 1.25f, 2f), floatArrayOf(1.2f, 3.2f, 3f), floatArrayOf(4.7f, 1.1f, 4f),
            floatArrayOf(6.4f, 1.2f, 5f), floatArrayOf(1.3f, 9.3f, 6f),
        )
        private val ROCKS = arrayOf(floatArrayOf(3.3f, 2.2f, 0.55f, 3f), floatArrayOf(11.3f, 9.6f, 0.62f, 8f), floatArrayOf(11.6f, 10.3f, 0.4f, 5f))
        private val AGARICS = arrayOf(floatArrayOf(2.1f, 1.9f, 1f), floatArrayOf(2.4f, 2.2f, 0.7f), floatArrayOf(1.6f, 10.0f, 0.9f))

        /** Hazel bushes in the two side corners: x, y, size, seed. */
        private val BACK_BUSHES = arrayOf(floatArrayOf(11.4f, 0.7f, 0.9f, 3f), floatArrayOf(0.7f, 11.3f, 0.9f, 7f))
    }
}
