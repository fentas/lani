package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.Season
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Il mare: the Adriatic at Grado (Friuli's own place), seen from the end of the sand bar between the lagoon and the sea.
 * On the left the lagoon lies calm and green behind the bar, with its casoni (the fishermen's thatched huts) on the marsh
 * islands, lines of posts (bricole) marking the channel with gulls on them, and a wooden pier running out from the near
 * bank with a crate of fish and a net drying at its end; the batela (a flat lagoon boat) is moored beside it. On the
 * right the beach, a shell and a crab on the sand, the waves breaking white along the water's edge, and the open sea to
 * the horizon, a ship on it; Grado's bell tower far off on the right, the Alps faint over the lagoon.
 *
 * The sea takes the sky's colour and the sun's glitter (the moon's path by night); the marsh grass is green in summer
 * and straw in winter; beach umbrellas stand along the bar in summer. A dialog can raise the waves, send the gulls up
 * off the posts and hoist the batela's sail (the ochre sail of the Adriatic with its sun). Sea mist lies on the water;
 * a squall's rain hangs dark over the lagoon before it comes across; drops ring the water.
 *
 * Closer up ([detail] 2 and 3) the same view is drawn finer: strands in the thatch, the salt grass in tufts, a finer
 * mesh in the net, the fishes' eyes and gleam, grains in the sand, a sparkle on the water.
 */
internal class SeaPainter : VistaPainter() {
    override val art = "sea"

    // the plan, in metres: the sand bar between the lagoon (left) and the sea, the beach's waterline, the lagoon's bank
    private fun spit(d: Float) = -0.4f - 0.2f * (d - 10f) + 0.25f * sin(d * 0.55f) + 0.12f * sin(d * 1.7f + 1f)
    private fun shore(x: Float) = 13.6f + 0.9f * sin(x * 0.45f + 0.6f) + surge
    private fun bank(x: Float) = 11.0f + 0.5f * sin(x * 0.8f)
    private val pierA = -6.9f; private val pierB = -5.9f; private val pierNear = 10.4f; private val pierFar = 19f; private val deck = 0.55f
    private val boatL = -5.3f; private val boatR = -1.9f; private val boatD = 14.2f
    private val casone = floatArrayOf(-15f, 30f); private val casone2 = floatArrayOf(-26f, 70f)
    /** The bricole along the channel, curving away to the left: side and depth (m) of each group. */
    private val posts = arrayOf(floatArrayOf(-3.6f, 21f), floatArrayOf(-6.6f, 27f), floatArrayOf(-10.5f, 35f), floatArrayOf(-16f, 46f), floatArrayOf(-24f, 62f))

    /** How far up the beach the water runs now (m): a slow swash, more with the waves. */
    private var surge = 0f

    /** What each pixel of the ground is (see the constants), for the things that are regions of it. */
    private var region = ByteArray(0)

    private val items = ArrayList<Pair<Float, () -> Unit>>()

    private val z: Int get() = detail

    /**
     * The waves' height: a breeze's by itself, higher when a dialog raises them ("waves"). What can be tapped (the breakers,
     * the beach's edge, the boat) moves with this alone; the wind roughens the water's face ([rough]) and blows spray.
     */
    private val swell: Float get() = 0.3f + 1.2f * fxOn("waves")

    /** How rough the water's face is: the swell, and whitecaps in the wind. */
    private val rough: Float get() = swell + 0.5f * frame.sky.wind

    override fun paint() {
        setup()
        val z = z
        surge = sin(t * 0.9).toFloat() * (0.25f + 0.35f * swell)
        vistaSky(4, 41, moonX = ox + 190 * z, moonY = oy + 12 * z)
        farLand()
        val n = c.width * c.height
        if (region.size != n) region = ByteArray(n)
        region.fill(0)
        ground { x, d, px, py -> land(x, d, px, py) }
        stamp("lagoon", LAGOON)
        stamp("sea", SEA)
        thing("horizon", outline = 0) { stampPixels(FAR); ship() }
        stamp("beach", BEACH)
        thing("wave", outline = 0) { breakers() }

        items.clear()
        items.add(casone2[1] to { prop(Col.hex(0x3A2E1E)) { casone(casone2[0], casone2[1], 0.8f) } })
        items.add(casone[1] to { thing("casone", outline = Col.hex(0x3A2E1E)) { casone(casone[0], casone[1], 1f) } })
        items.add(posts[0][1] + 0.1f to { thing("posts", outline = Col.hex(0x2A2420)) { bricole() } })
        if (env.month in 6..8) items.add(80f to { prop(outline = 0) { umbrellas() } })
        items.add(pierFar to { thing("pier") { pier() } })
        items.add(pierFar - 0.5f to { thing("net", slop = 1) { net() } })
        items.add(pierFar - 0.2f to { thing("gull", slop = 3) { gullOnPost() } })
        items.add(boatD to {
            thing("boat") { boat() }
            prop(outline = Col.hex(0x5A2A14)) { sail(fxOn("sail")) }
            for (p in peopleAt("boat")) person(p, gx(boatR - 0.7f, boatD).toInt(), (hy(boatD, 0.5f) + rock(0.4f)).toInt(), flip = true)
        })
        items.add(11.8f to { thing("fish", slop = 2) { fishCrate() } })
        for (p in peopleAt("pier")) items.add(13.3f to { person(p, gx(pierB - 0.4f, 13.3f).toInt(), hy(13.3f, deck).toInt(), flip = false) })
        for (p in peopleAt("beach")) items.add(11.6f to { groundShadow(3.0f, 11.6f, 0.5f); person(p, gx(3.0f, 11.6f).toInt(), gy(11.6f).toInt(), flip = true) })
        items.add(10.7f to { thing("shell", slop = 3) { shell(gx(1.8f, 10.7f).toInt(), gy(10.7f).toInt()) } })
        items.add(11f to { thing("crab", slop = 3) { crab() } })
        items.add(10.6f to { reeds() })
        items.sortByDescending { it.first }
        for ((_, draw) in items) draw()

        prop(outline = 0) { flyingGulls() }
        prop(outline = 0) { flock(fxOn("gulls")) }
    }

    // ------------------------------------------------------------------ far away

    /** The Alps faint over the lagoon on the left (snowy the most of the year), and Grado on its island far on the right. */
    private fun farLand() {
        val z = z
        val snowy = env.season != Season.SUMMER
        val rock = env.lit(Col.mix(if (snowy) Col.hex(0xC8D2E2) else Col.hex(0x8A96B4), env.skyHorizon, 0.62f))
        val lit = env.lit(Col.mix(Col.hex(0xF0F4FA), env.skyHorizon, 0.55f))
        c.penEmissive = true
        for (x in max(0, c.left) until min(w, c.right)) {
            val u = (x - ox) / z.toFloat() / STAGE_W
            val tall = ((0.42f - u) / 0.3f).coerceIn(0f, 1f)
            if (tall <= 0f) continue
            val a = 1f - abs(Noise.v1(u * 11f, 401) * 2f - 1f)
            val top = hz - (2f + 18f * tall * a * a) * z
            for (y in max(top.toInt(), c.top) until min(hz, c.bottom)) c.set(x, y, if (snowy && (y - top) < 4f * z * a) lit else rock)
        }
        // Grado: a low line of houses on the right with the bell tower of its basilica, the lighthouse blinking at night
        val gx0 = ox + 196 * z
        val town = env.lit(Col.mix(Col.hex(0xC8B8A0), env.skyHorizon, 0.5f))
        for (x in max(gx0, c.left) until min(w, c.right)) {
            val u = (x - gx0) / z
            val hh = (1.5f + (Noise.rnd(u / 3, 402) * 2.5f).toInt()) * z
            for (y in max((hz - hh).toInt(), c.top) until min(hz, c.bottom)) c.set(x, y, town)
        }
        val tx = gx0 + 14 * z
        c.fillRect(tx, hz - 14 * z, 2 * z, 14 * z, town)
        for (r in 0 until 3 * z) c.hline(tx - z / 2 + r / 3, tx + 2 * z - 1 + z / 2 - r / 3, hz - 14 * z - 1 - r, town)
        if (env.windows > 0.3f) {
            for (k in 0 until 6) c.set(gx0 + (4 + k * 7) * z, hz - z, Pal.WINDOW_LIT)
            if (((t * 0.8).toInt() and 1) == 0) c.fillRect(ox + 234 * z, hz - 5 * z, z, z, Col.hex(0xFFF4C0))
        }
        c.penEmissive = false
    }

    /** A ship far out on the horizon, sailing slowly toward Trieste; its lights at night. */
    private fun ship() {
        val z = z
        val x = (ox + ((70 + (t * 0.8) % 140) * z)).toInt()
        val y = hz
        val hull = env.lit(Col.mix(Col.hex(0x3A3A44), env.skyHorizon, 0.4f)); val cab = env.lit(Col.mix(Col.hex(0xE8E4DC), env.skyHorizon, 0.4f))
        c.fillRect(x, y - 2 * z, 9 * z, z + z / 2, hull)
        c.fillRect(x + 6 * z, y - 4 * z, 2 * z, 2 * z, cab)
        if (env.windows > 0.3f) { c.set(x, y - 2 * z, Pal.WINDOW_LIT); c.set(x + 8 * z, y - 2 * z, Col.hex(0xFF6A5A)) }
    }

    // ------------------------------------------------------------------ the water and the sand

    /**
     * The ground and the water: the marsh of the near bank and the islands (barene), the lagoon, the sand of the bar and
     * the beach, the sea to the horizon. Records each pixel's [region] for the things that are regions of it.
     */
    private fun land(x: Float, d: Float, px: Int, py: Int): Int {
        val sp = spit(d)
        val i = c.index(px, py)
        return when {
            x < sp - 0.3f -> {
                val island = d > 20f && (Noise.v2(x * 0.09f, d * 0.07f, 411) > 0.64f || near(x, d, casone, 3.8f, 2.6f) || near(x, d, casone2, 4.5f, 3f))
                if (d < bank(x) || island) marsh(x, d, px, py) else { region[i] = LAGOON; lagoon(x, d, px, py) }
            }
            x < sp + 1.2f || d < shore(x) -> {
                if (d < 16f && x > sp - 0.3f) region[i] = BEACH
                sand(x, d, px, py)
            }
            else -> { region[i] = if (d > FAR_D) FAR else SEA; sea(x, d, px, py) }
        }
    }

    private fun near(x: Float, d: Float, at: FloatArray, rx: Float, rd: Float): Boolean {
        val dx = (x - at[0]) / rx; val dd = (d - at[1]) / rd
        return dx * dx + dd * dd < 1f
    }

    /** The marsh: salt grass, green in summer and straw the rest of the year, darker tufts, a wet edge. */
    private fun marsh(x: Float, d: Float, px: Int, py: Int): Int {
        val (a, b, cc) = when (env.season) {
            Season.SUMMER -> Triple(Col.hex(0x6A8A3E), Col.hex(0x587A34), Col.hex(0x88A450))
            Season.SPRING -> Triple(Col.hex(0x7A9A48), Col.hex(0x62843A), Col.hex(0x9AB860))
            else -> Triple(Col.hex(0xA89060), Col.hex(0x8A7648), Col.hex(0xC0A872))
        }
        val n = Noise.v2(x * 0.8f, d * 0.8f, 412) + (Dither.at(px, py) - 0.5f) * 0.3f
        var col = if (n > 0.64f) cc else if (n > 0.34f) a else b
        // tufts of salt grass: short upright strokes, lit at their tips; little pools of water between them
        val z = z
        val tuft = Noise.rnd(px / max(1, z), (py + (px and 1)) / (2 * max(1, z)), 417)
        if (tuft < 0.1f) col = b else if (tuft > 0.95f) col = cc
        if (Noise.v2(x * 1.6f, d * 2.4f, 418) > 0.8f && d < 30f) col = Col.mix(Col.hex(0x4E7A78), skyAt(0.8f), 0.4f)
        return hazy(col, d)
    }

    /** The lagoon: calm, green-grey, the sky in it; slow ripples, glinting where the sun or the moon is. */
    private fun lagoon(x: Float, d: Float, px: Int, py: Int): Int {
        val z = z
        val deep = Col.mix(Col.hex(0x3E6E6A), env.skyHorizon, 0.25f)
        var col = Col.mix(deep, skyAt(0.55f + min(0.4f, d / 120f)), 0.35f + min(0.3f, d / 150f))
        val row = py / z
        val rip = sin(x * 0.9f / max(0.2f, 12f / d) + row * 1.7f - t.toFloat() * 1.2f)
        if (rip > 0.92f) col = Col.mix(col, Col.hex(0xFFFFFF), 0.18f)
        if (rip < -0.95f) col = Col.scale(col, 0.92f)
        if (d > 16f) glitter(px, py, d)?.let { col = it }
        return hazy(col, d)
    }

    /** The sea: turquoise over the sand near the beach, deep blue farther out, the swell's crests rolling in, whitecaps in the wind. */
    private fun sea(x: Float, d: Float, px: Int, py: Int): Int {
        val z = z
        val shallow = Col.hex(0x3AA0A8); val blue = Col.hex(0x2F6FB4); val deep = Col.hex(0x245A96)
        val f = ((d - 14f) / 40f).coerceIn(0f, 1f)
        var col = if (f < 0.5f) Col.mix(shallow, blue, f * 2f) else Col.mix(blue, deep, (f - 0.5f) * 2f)
        col = Col.mix(col, skyAt(0.75f + min(0.25f, d / 400f)), 0.25f + 0.35f * min(1f, d / 200f))
        if (frame.sky.gloom > 0f) col = Col.mix(col, Col.hex(0x6A7684), frame.sky.gloom * 0.4f)
        // the swell: crests every few metres coming in toward the beach, dark troughs behind them
        val wl = 3.2f + rough
        val rows = F * EYE / (d * d) * wl
        if (rows > 2.2f) {
            val ph = (d + t.toFloat() * 1.6f) / wl + Noise.v1(x * 0.15f, 413) * 0.6f
            val fr = ph - floor(ph)
            if (fr < 0.12f * (0.6f + 0.4f * rough)) col = Col.mix(col, Col.hex(0xE8F4F8), 0.35f + 0.15f * rough)
            else if (fr < 0.3f) col = Col.scale(col, 0.9f)
        }
        // whitecaps flicker where the wind is up
        val caps = 0.02f + 0.08f * frame.sky.wind + 0.06f * fxOn("waves")
        if (Noise.rnd((x * 2f).toInt(), (d * 1.5f).toInt(), (t * 1.5).toInt() + 414) < caps && d < 90f && (py / z + px / z) % 2 == 0) col = Col.hex(0xF4FAFC)
        if (z > 1 && Noise.rnd(px, py, 415) < 0.02f) col = Col.mix(col, Col.hex(0xFFFFFF), 0.2f)
        glitter(px, py, d)?.let { col = it }
        return hazy(col, d)
    }

    /** The sun's glitter on the water under it (the moon's by night), twinkling; null where there is none. */
    private fun glitter(px: Int, py: Int, d: Float): Int? {
        if (frame.sky.gloom > 0.5f) return null
        val z = z
        val night = env.dark > 0.4f
        if (night && !env.moonShows) return null
        val sx = if (night) (ox + 190 * z).toFloat() else sunAt()?.first ?: return null
        val spread = (6f + d * 0.2f) * z
        val off = abs(px + 0.5f - sx) / spread
        if (off > 1f) return null
        val tw = Noise.rnd(px / z, py / z, (t * 3).toInt() + 416)
        return if (tw > 0.7f + off * 0.28f) (if (night) Col.hex(0xE8E4C8) else Col.hex(0xFFF6D8)) else null
    }

    /** Sand: pale and dry up the beach, darker and wet by the water, a line of shells and weed, footprints. */
    private fun sand(x: Float, d: Float, px: Int, py: Int): Int {
        val z = z
        val dry = if (env.season == Season.WINTER) Col.hex(0xC8BCA2) else Col.hex(0xE8D8B0)
        val mid = if (env.season == Season.WINTER) Col.hex(0xB0A488) else Col.hex(0xD4C294)
        val wet = Col.hex(0xA8987A)
        val toWater = shore(x) - d
        val n = Noise.rnd(px, py, 421)
        var col = if (toWater in 0f..0.9f && x > spit(d) + 1.2f) wet else if (n < 0.2f) mid else dry
        if (toWater in 0.9f..1.3f && x > spit(d) + 1.2f && Noise.rnd(px / z, 422) < 0.5f) col = Col.hex(0x8A7A5A) // the tide line
        if (z > 1 && n > 0.93f) col = Col.mix(col, Col.hex(0xFFFFFF), 0.3f)
        if (z > 1 && n < 0.04f) col = Col.scale(col, 0.88f)
        return hazy(col, d)
    }

    /** One of the regions of the ground as a thing: its pixels under a fresh object id, without an outline. */
    private fun stamp(slot: String, r: Byte) = thing(slot, outline = 0) { stampPixels(r) }

    private fun stampPixels(r: Byte) {
        for (py in max(hz + 1, c.top) until min(h, c.bottom)) for (px in max(0, c.left) until min(w, c.right)) {
            val i = c.index(px, py)
            if (region[i] == r) c.set(px, py, c.pixels[i])
        }
    }

    /**
     * The waves breaking along the beach: a crest curling over, white foam on it and running up the sand in a lace that
     * sinks back; higher and wider in the wind and when the dialog raises them, spray flying off the crest.
     */
    private fun breakers() {
        val z = z
        val foam = Col.hex(0xF4FAFA); val face = Col.hex(0x3E9CB0); val lace = Col.hex(0xE0EEF0)
        val hgt = 0.18f + 0.22f * swell
        for (py in max(hz + 1, c.top) until min(h, c.bottom)) {
            val d = depthAt(py)
            if (d > 22f) continue
            for (px in max(0, c.left) until min(w, c.right)) {
                val x = sideAt(px, d)
                if (x < spit(d) + 1.2f) continue
                val sh = shore(x)
                val off = d - sh
                // the lace of foam on the wet sand, ragged
                if (off in -0.35f..0.15f && Noise.rnd(px / z, py / z, 431) < 0.75f) { c.set(px, py, lace); continue }
                // the breaking crest a little out: its white top, the green-blue face under it
                val crest = off - (1.1f + 0.5f * swell)
                if (abs(crest) < 0.3f + 0.1f * swell) c.set(px, py, if (crest < 0f || Noise.rnd(px, py, 432) < 0.4f) foam else face)
            }
        }
        // the crest's lip, standing up off the water: a white row above the crest line
        for (px in max(0, c.left) until min(w, c.right)) {
            val d0 = depthAt(max(hz + 1, min(h - 1, (hz + F * EYE / 15f).toInt())))
            val x = sideAt(px, d0)
            if (x < spit(d0) + 1.2f) continue
            val dc = shore(x) + 1.1f + 0.5f * swell
            val y = gy(dc) - hgt * pm(dc)
            for (j in 0 until max(1, (hgt * pm(dc) * 0.5f).toInt())) c.set(px, y.toInt() + j, if (j == 0) foam else face)
        }
        val waves = fxOn("waves")
        if (waves > 0.05f || frame.sky.wind > 0.5f) s.fx {
            // spray off the crests
            val n = (40 * (waves + frame.sky.wind * 0.5f)).toInt()
            for (j in 0 until n) {
                val ph = ((t * 1.3 + Noise.rnd(j, 433)) % 1.0).toFloat()
                val sx = 0.5f + Noise.rnd(j, 434) * 12f
                val dc = shore(sx) + 1.1f + 0.5f * swell
                val x = gx(sx, dc); val y = gy(dc) - hgt * pm(dc) - ph * (4f + 6f * waves) * z
                c.blend(x.toInt() + (ph * 3 * z).toInt(), y.toInt(), foam, 0.8f * (1f - ph))
            }
        }
    }

    // ------------------------------------------------------------------ the lagoon's things

    /** A casone: low walls of reeds under a tall thatched roof, a dark doorway (a fire in it at night), smoke from the ridge. */
    private fun casone(x: Float, d: Float, scale: Float) {
        val z = z
        val p = pm(d) * scale; val bx = gx(x, d); val by = gy(d)
        val ww = 5.6f * p; val wallH = 0.7f * p; val roofH = 4.2f * p
        val thatch = when (env.season) { Season.SUMMER -> Col.hex(0xB09A62); else -> Col.hex(0x9A8A64) }
        val thatchD = Col.scale(thatch, 0.72f); val thatchL = Col.mix(thatch, Col.hex(0xFFF0C8), 0.3f)
        val sunLeft = (sunAt()?.first ?: vx) < bx
        // the reed walls
        c.fillRect((bx - ww * 0.42f).toInt(), (by - wallH).toInt(), (ww * 0.84f).toInt(), wallH.toInt() + 1, hazy(Col.hex(0x8A7A52), d))
        // the roof: a rounded gable, strands of reed running down it
        val top = by - wallH - roofH
        for (py in max(top.toInt(), c.top) until min((by - wallH * 0.6f).toInt(), c.bottom)) {
            val f = (py - top) / roofH
            val half = ww * 0.5f * min(1f, 0.15f + f * 1.05f)
            for (px in max((bx - half).toInt(), c.left)..min((bx + half).toInt(), c.right - 1)) {
                val u = (px + 0.5f - bx) / max(1f, half)
                val strand = Noise.rnd((px - bx.toInt() + 1000) / max(1, z), 441) - 0.5f
                val lit = (if (sunLeft) -u else u) * 0.5f + strand * 0.6f + (if (z > 1) (Noise.rnd(px, py / (2 * z), 442) - 0.5f) * 0.3f else 0f)
                c.set(px, py, hazy(if (lit > 0.25f) thatchL else if (lit < -0.25f) thatchD else thatch, d))
            }
        }
        // the doorway, a fire's glow in it at dusk
        val dw = max(z.toFloat(), 0.8f * p); val dh = 1.5f * p
        val lit = env.windows > 0.35f
        c.fillRect((bx - dw / 2).toInt(), (by - dh).toInt(), dw.toInt().coerceAtLeast(1), dh.toInt(), if (lit) Col.hex(0xE8903A) else Col.hex(0x2A2018))
        if (lit) s.light(bx, by - dh * 0.5f, 22f * scale, 0.7f)
        s.smoke(bx, top + z, 0.3f * scale, 11, dark = false)
        // a pole with a net by it
        c.vline((bx + ww * 0.6f).toInt(), (by - 2.2f * p).toInt(), by.toInt(), hazy(Pal.WOOD_D, d))
    }

    /** The bricole: groups of three posts leaning together, marking the channel through the lagoon; their reflections. */
    private fun bricole() {
        val z = z
        for ((g, at) in posts.withIndex()) {
            val x = at[0]; val d = at[1]
            val p = pm(d)
            val by = gy(d)
            for (k in -1..1) {
                val px = gx(x + k * 0.28f, d)
                val topX = gx(x + k * 0.1f, d)
                val th = max(z.toFloat(), 0.2f * p)
                for (j in 0 until th.toInt().coerceAtLeast(1)) c.line((px + j).toInt(), by.toInt(), (topX + j).toInt(), hy(d, 2.8f).toInt(), hazy(if (j == 0) Col.hex(0x6A5A48) else Col.hex(0x4A3E32), d))
            }
            // the rope round their tops
            c.hline((gx(x - 0.15f, d)).toInt(), (gx(x + 0.2f, d) + max(z.toFloat(), 0.2f * p)).toInt(), hy(d, 2.3f).toInt(), hazy(Col.hex(0x8A7A5A), d))
            // the reflection, wavering
            for (j in 1..(0.9f * p).toInt()) c.blend((gx(x, d) + sin(t * 2 + j * 0.8 + g).toFloat() * z).toInt(), by.toInt() + j, Col.hex(0x2A3A34), 0.45f)
            // gulls resting on the first ones, until something startles them
            if (g < 3) {
                val u = gullGone(g)
                if (u <= 0f) standingGull(gx(x, d).toInt(), hy(d, 2.8f).toInt(), g % 2 == 0)
            }
        }
    }

    /** How far the gull on post group [g] has flown (the "gulls" effect, not all at once): 0 still there. */
    private fun gullGone(g: Int): Float = ((fxOn("gulls") - g * 0.12f) / 0.7f).coerceIn(0f, 1f)

    /** The pier: planks across on posts, a taller post at its end; the water under it in shade. */
    private fun pier() {
        val z = z
        // the posts under it
        var d = pierFar
        while (d >= pierNear) {
            for (x in floatArrayOf(pierA, pierB)) {
                val px = gx(x, d)
                c.fillRect(px.toInt(), hy(d, deck).toInt(), max(z, (0.16f * pm(d)).toInt()), (gy(d) - hy(d, deck)).toInt() + 2 * z, Col.hex(0x4A3A2C))
            }
            d -= 1.6f
        }
        // the deck, row by row: a plank across every 0.3 m, gaps between them
        val yTop = hy(pierFar, deck).toInt(); val yBot = hy(pierNear, deck).toInt()
        for (py in max(yTop, c.top)..min(yBot, c.bottom - 1)) {
            val dd = F * (EYE - deck) / (py + 0.5f - hz)
            val plank = (pierFar - dd) / 0.3f
            val gap = floor(plank).toInt() != floor((pierFar - F * (EYE - deck) / (py + 1.5f - hz)) / 0.3f).toInt() && pm(dd) * 0.3f > 2.5f
            val xl = gx(pierA, dd); val xr = gx(pierB, dd)
            for (px in max(xl.toInt(), c.left)..min(xr.toInt(), c.right - 1)) {
                val grain = Noise.rnd(px / (2 * z), floor(plank).toInt(), 451)
                c.set(px, py, when {
                    gap -> Col.hex(0x3A2C22)
                    grain < 0.25f -> Pal.WOOD_M
                    grain > 0.85f -> Col.mix(Pal.WOOD_L, Col.hex(0xC8C0B0), 0.4f) // bleached by the salt
                    else -> Col.mix(Pal.WOOD_L, Col.hex(0xA8A090), 0.3f)
                })
            }
        }
        // the deck's near edge, and its side toward us on the right
        for (j in 0 until z) c.hline(gx(pierA, pierNear).toInt(), gx(pierB, pierNear).toInt(), yBot + 1 + j, Pal.WOOD_D)
        c.line(gx(pierB, pierNear).toInt(), yBot, gx(pierB, pierFar).toInt(), yTop, Pal.WOOD_D)
        // the end post, where the gull sits
        val ex = gx(pierA, pierFar)
        c.fillRect(ex.toInt(), hy(pierFar, 1.5f).toInt(), max(z, (0.18f * pm(pierFar)).toInt()), (hy(pierFar, deck) - hy(pierFar, 1.5f)).toInt() + 1, Col.hex(0x5A4636))
    }

    /** The gull that always sits on the pier's end post, turning its head now and then. */
    private fun gullOnPost() {
        standingGull(gx(pierA, pierFar).toInt() + z, hy(pierFar, 1.5f).toInt(), ((t * 0.4).toInt() and 1) == 0)
    }

    /** A gull standing at ([bx], [by]) (its feet): white, grey back, yellow bill; its eye closed at night. */
    private fun standingGull(bx: Int, by: Int, left: Boolean) {
        val white = env.lit(Col.hex(0xF4F6F8)); val grey = env.lit(Col.hex(0x9AA4B0)); val bill = Col.hex(0xF0C040)
        val dir = if (left) -1 else 1
        sprite(bx, by) {
            c.set(bx, by - 1, Col.hex(0xD88A3A))
            c.hline(bx - 2, bx + 1, by - 2, white); c.hline(bx - 2, bx + 2, by - 3, grey); c.hline(bx - 1, bx + 2, by - 4, grey)
            c.set(bx + dir, by - 5, white); c.set(bx + dir * 2, by - 5, white); c.set(bx + dir * 2, by - 6, white); c.set(bx + dir, by - 6, white)
            c.set(bx + dir * 3, by - 5, bill)
            c.set(bx - dir * 2, by - 3, Col.hex(0x2A2A30)) // the black wingtip
        }
    }

    /** A net hung to dry over two poles at the pier's end: the mesh, cork floats along its top. */
    private fun net() {
        val z = z
        val d0 = pierFar - 1.3f; val d1 = pierFar - 0.2f
        val x = pierA - 0.1f
        val top0 = hy(d0, 1.6f); val top1 = hy(d1, 1.6f)
        val p0 = pm(d0)
        c.vline(gx(x, d0).toInt(), top0.toInt(), hy(d0, deck).toInt(), Pal.WOOD_D)
        c.vline(gx(x, d1).toInt(), top1.toInt(), hy(d1, deck).toInt(), Pal.WOOD_D)
        val xa = gx(x, d0); val xb = gx(x, d1)
        val mesh = Col.hex(0x6A7A6E)
        for (px in min(xa, xb).toInt()..max(xa, xb).toInt()) {
            val f = (px - xa) / (xb - xa)
            val yt = top0 + (top1 - top0) * f + sin(f * 3.14f) * 0.15f * p0
            val yb = yt + 1.0f * p0 * (1f - 0.25f * sin(f * 3.14f))
            for (py in yt.toInt()..yb.toInt()) {
                val m = if (z > 1) (((px + py) / z) % 3 == 0 || ((px - py) / z) % 3 == 0) else (px + py) % 2 == 0
                if (m) c.set(px, py, mesh)
            }
            if (((px - xa.toInt()) / (2 * z)) % 2 == 0) c.set(px, yt.toInt(), Col.hex(0xE87A2A)) // the floats
        }
    }

    /** A crate of fish on the pier: silver sardines, a gleam on them; ice in it in summer. */
    private fun fishCrate() {
        val z = z
        val d = 11.8f
        val p = pm(d); val bx = gx(pierA + 0.55f, d); val by = hy(d, deck)
        val ww = 0.6f * p; val hh = 0.28f * p
        val x0 = (bx - ww / 2).toInt(); val y0 = (by - hh).toInt()
        // the fish heaped over the rim, heads and tails every way
        val silver = Col.hex(0xB8C4CC); val back = Col.hex(0x4A6A84); val gleam = Col.hex(0xF4F8FA)
        for (k in 0 until 6) {
            val fx = x0 + (k + 0.5f) * ww / 6f; val fy = y0 - 0.02f * p * (k % 2)
            val len = 0.14f * p
            c.hline((fx - len / 2).toInt(), (fx + len / 2).toInt(), fy.toInt(), silver)
            c.hline((fx - len / 2).toInt(), (fx + len / 2).toInt() - 1, fy.toInt() - max(1, z / 2), back)
            if (z > 1) { c.set((fx - len / 2).toInt(), fy.toInt(), Col.hex(0x2A2A30)); c.set((fx + len / 4).toInt(), fy.toInt(), gleam) }
        }
        box(x0, y0, ww.toInt().coerceAtLeast(3), hh.toInt().coerceAtLeast(2), Col.hex(0xC8B08A), Pal.WOOD_L, Pal.WOOD_D)
        if (z > 1) c.hline(x0 + 1, x0 + ww.toInt() - 2, y0 + hh.toInt() / 2, Pal.WOOD_M)
    }

    /** The boat's rise and fall on the water now (px), [f] of the swell. */
    private fun rock(f: Float): Float = sin(t * 1.3).toFloat() * (0.04f + 0.05f * swell) * f * pm(15f)

    /**
     * The batela moored between the pier and the bar, broadside to us, its bow to the left: a flat black hull with a red
     * stripe, rising to a pointed bow, the inside of pale wood seen over the near gunwale, a thwart, the mast with the sail
     * furled on its yard (hoisted by [sail]).
     */
    private fun boat() {
        val z = z
        val p = pm(boatD)
        val bob = rock(1f)
        val xl = gx(boatL, boatD); val xr = gx(boatR, boatD)
        val water = gy(boatD) + bob
        val gun = hy(boatD, 0.55f) + bob
        val far = hy(boatD + 1.3f, 0.55f) + bob
        val len = xr - xl
        // the bow rises in a curve at the left, the stern is square at the right
        fun rise(x: Float): Float { val u = ((x - xl) / len).coerceIn(0f, 1f); return if (u < 0.25f) (0.25f - u) * (0.25f - u) * 9f * p else 0f }
        // the inside: the floor boards over the far side, a thwart across
        for (px in max(xl.toInt(), c.left)..min(xr.toInt(), c.right - 1)) {
            val r = rise(px + 0.5f)
            val top = far - r * 0.9f; val bottom = gun - r
            for (py in max(top.toInt(), c.top)..min(bottom.toInt(), c.bottom - 1)) {
                val f = (py - top) / max(1f, bottom - top)
                c.set(px, py, if (f < 0.25f) Col.hex(0x2E2A30) else if ((py / max(1, z)) % 3 == 0) Col.hex(0x9A7A52) else Col.hex(0xB89468))
            }
        }
        val tx = xl + len * 0.66f
        c.fillRect(tx.toInt(), far.toInt(), max(z, (0.15f * p).toInt()), (gun - far).toInt(), Col.hex(0x7A5A3A))
        // the near side: black, a red stripe under the gunwale, the rise of the bow
        for (px in max(xl.toInt(), c.left)..min(xr.toInt(), c.right - 1)) {
            val r = rise(px + 0.5f)
            val top = gun - r
            val u = (px + 0.5f - xl) / len
            val keel = water - (if (u < 0.12f) (0.12f - u) * 3f * p else 0f)
            for (py in max(top.toInt(), c.top)..min(keel.toInt(), c.bottom - 1)) {
                val f = (py - top) / max(1f, keel - top)
                c.set(px, py, when {
                    py - top < z -> Col.hex(0x6A5A4A)          // the gunwale's cap
                    f < 0.28f -> Col.hex(0xB8352A)             // the red stripe
                    f > 0.8f -> Col.hex(0x1A181E)
                    else -> Col.hex(0x2A262E)
                })
            }
        }
        // the mast and, furled on its yard, the sail
        val mx = xl + len * 0.42f
        c.fillRect(mx.toInt(), (hy(boatD + 0.65f, 4.4f) + bob).toInt(), max(z, (0.1f * p).toInt()), (gun - hy(boatD + 0.65f, 4.4f)).toInt(), Col.hex(0x6A4A30))
        if (fxOn("sail") < 0.1f) {
            val yy = hy(boatD + 0.65f, 1.3f) + bob
            for (j in 0 until max(1, z)) c.line((mx - 1.2f * p).toInt(), yy.toInt() + j, (mx + 1.1f * p).toInt(), (yy - 0.12f * p).toInt() + j, if (j == 0) Col.hex(0xE0923A) else Col.hex(0xB06A24))
        }
        // its reflection, wavering
        for (j in 1..max(1, (0.5f * p).toInt())) {
            val o = (sin(t * 2 + j * 0.7).toFloat() * z).toInt()
            c.blend(xl.toInt() + o, water.toInt() + j, Col.hex(0x1E2A28), 0.35f); c.blend(xr.toInt() + o, water.toInt() + j, Col.hex(0x1E2A28), 0.35f)
            for (x in (xl + len * 0.1f).toInt() until (xr - len * 0.05f).toInt() step max(1, 2 * z)) c.blend(x + o, water.toInt() + j, Col.hex(0x1E2A28), 0.3f)
        }
    }

    /**
     * The sail ([fx] "sail": how far it is up): the lug sail of the Adriatic, ochre with a red sun painted on it, rising up
     * the mast on its yard and filling with the wind.
     */
    private fun sail(level: Float) {
        if (level < 0.1f) return
        val p = pm(boatD); val bob = rock(1f)
        val mx = gx(boatL, boatD) + (gx(boatR, boatD) - gx(boatL, boatD)) * 0.42f
        val up = level.coerceIn(0f, 1f)
        val footY = hy(boatD + 0.65f, 1.2f) + bob; val headY = hy(boatD + 0.65f, 1.5f + 2.8f * up) + bob
        val belly = sin(t * 1.7).toFloat() * 0.05f * p + 0.2f * p * up
        val ochre = Col.hex(0xD8862A); val ochreD = Col.hex(0xB0621E); val red = Col.hex(0xB02A1E)
        for (py in max(headY.toInt(), c.top)..min(footY.toInt(), c.bottom - 1)) {
            val f = (py - headY) / max(1f, footY - headY)
            val xl = mx - (0.5f + 0.2f * f) * p
            val xr = mx + (0.7f + 0.6f * f) * p + belly * sin(f * 3.14f)
            for (px in max(xl.toInt(), c.left)..min(xr.toInt(), c.right - 1)) {
                val u = (px - xl) / max(1f, xr - xl)
                c.set(px, py, if (u > 0.82f) ochreD else ochre)
            }
        }
        // the painted sun in its middle, the yard along the head
        if (up > 0.5f) c.fillCircle(mx + 0.25f * p, (headY + footY) / 2f, max(1f, 0.32f * p), red)
        c.line((mx - 0.6f * p).toInt(), headY.toInt() + detail, (mx + 0.8f * p).toInt(), headY.toInt(), Col.hex(0x6A4A30))
    }

    // ------------------------------------------------------------------ on the beach

    /** Beach umbrellas far along the bar in summer, striped, in a row. */
    private fun umbrellas() {
        val cols = intArrayOf(Col.hex(0xD8392E), Col.hex(0x2F6FC0), Col.hex(0xF0C23A), Col.hex(0x3E9A52))
        for ((k, d) in floatArrayOf(34f, 42f, 52f, 64f).withIndex()) {
            val x = spit(d) + 0.6f
            val p = pm(d); val bx = gx(x, d); val by = gy(d)
            c.vline(bx.toInt(), (by - 1.8f * p).toInt(), by.toInt(), Col.hex(0xE8E4DC))
            val r = max(1f, 0.9f * p)
            for (j in 0..max(1, (0.35f * p).toInt())) {
                val half = r * (j + 1) / max(1f, 0.35f * p + 1)
                c.hline((bx - half).toInt(), (bx + half).toInt(), (by - 2.2f * p).toInt() + j, if (j % 2 == 0) cols[k % cols.size] else Col.hex(0xF4F4F0))
            }
        }
    }

    /** A scallop shell on the sand: ribs fanning from its hinge, pale pink. */
    private fun shell(bx: Int, by: Int) {
        val pale = Col.hex(0xF4E4DA); val rib = Col.hex(0xD8B0A0); val hinge = Col.hex(0xC89888)
        sprite(bx, by) {
            c.hline(bx - 2, bx + 2, by - 1, pale); c.hline(bx - 2, bx + 2, by - 2, pale); c.hline(bx - 1, bx + 1, by - 3, pale)
            c.set(bx - 1, by - 2, rib); c.set(bx + 1, by - 2, rib); c.set(bx, by - 1, rib)
            c.set(bx, by, hinge)
        }
    }

    /** A little crab scuttling sideways on the wet sand, claws up. */
    private fun crab() {
        val d = 11f
        val x = 5.3f + sin(t * 0.7).toFloat() * 0.35f
        val bx = gx(x, d).toInt(); val by = gy(d).toInt()
        val shell = Col.hex(0xC8502A); val dark = Col.hex(0x8A3018); val step = ((t * 6).toInt() and 1)
        sprite(bx, by) {
            c.hline(bx - 2, bx + 2, by - 2, shell); c.hline(bx - 1, bx + 1, by - 3, shell)
            c.set(bx - 1, by - 3, Col.hex(0xE87A4A))
            c.set(bx - 3, by - 3, dark); c.set(bx + 3, by - 3, dark); c.set(bx - 3, by - 4, shell); c.set(bx + 3, by - 4, shell)
            c.set(bx - 2, by - 1 + step, dark); c.set(bx + 2, by - step, dark); c.set(bx - 1, by - step, dark); c.set(bx + 1, by - 1 + step, dark)
            c.set(bx - 1, by - 4, Pal.OUTLINE); c.set(bx + 1, by - 4, Pal.OUTLINE)
        }
    }

    /** Reeds on the near bank of the lagoon and marram grass on the bar, swaying; dry and pale in winter. */
    private fun reeds() {
        val z = z
        val green = env.season == Season.SUMMER || env.season == Season.SPRING
        val col = if (green) Col.hex(0x5A7A3A) else Col.hex(0xA89060)
        val tip = if (green) Col.hex(0x8AA852) else Col.hex(0xC8B080)
        for (k in 0 until 70) {
            val onBar = k >= 45
            val d = if (onBar) 10.5f + Noise.rnd(k, 461) * 14f else 6.5f + Noise.rnd(k, 461) * 4.5f
            val x = if (onBar) spit(d) + 0.1f + Noise.rnd(k, 462) * 0.8f else spit(d) - 0.8f - Noise.rnd(k, 462) * 9f
            if (!onBar && (d > bank(x) - 0.15f || x in pierA - 0.3f..pierB + 0.3f)) continue
            val p = pm(d); val bx = gx(x, d); val by = gy(d)
            val hh = (0.6f + Noise.rnd(k, 463) * 0.6f) * p
            val sway = sin(t * 1.4 + k).toFloat() * 0.08f * p * gust
            c.line(bx.toInt(), by.toInt(), (bx + sway).toInt(), (by - hh).toInt(), col)
            c.set((bx + sway).toInt(), (by - hh).toInt(), tip)
            if (z > 1) c.set((bx + sway).toInt(), (by - hh).toInt() - 1, tip)
        }
    }

    // ------------------------------------------------------------------ gulls in the air

    /** Two gulls gliding over the water by day, banking now and then. */
    private fun flyingGulls() {
        if (env.dark > 0.6f) return
        for (j in 0 until 2) {
            val u = ((t * 0.018 + j * 0.47) % 1.0).toFloat()
            val x = ox + ((20 + u * 220) * z).toInt(); val y = oy + ((20 + j * 10 + sin(t * 0.7 + j) * 5) * z).toInt()
            gullFlying(x, y, ((t * 3 + j).toInt() and 3) == 0)
        }
    }

    /**
     * The gulls off the bricole ([fx] "gulls": how far they have flown): each takes off from its post in turn, climbs
     * over the lagoon crying and wheels away to the open sea.
     */
    private fun flock(level: Float) {
        if (level <= 0.01f) return
        for ((g, at) in posts.withIndex()) {
            if (g >= 3) break
            val u = gullGone(g)
            if (u <= 0f || u >= 1f) continue
            val d = at[1]
            val x0 = gx(at[0], d); val y0 = hy(d, 2.8f)
            val e = 1f - (1f - u) * (1f - u)
            val x = x0 + (60f + g * 25f) * z * u
            val y = y0 - (y0 - (oy + 8 * z)) * e
            gullFlying(x.toInt(), y.toInt(), ((t * 5 + g).toInt() and 1) == 0)
        }
    }

    /** A gull in flight: a white body, wings bent in a flat M, dark tips; [up]: the wings raised. */
    private fun gullFlying(bx: Int, by: Int, up: Boolean) {
        val white = env.lit(Col.hex(0xF4F6F8)); val grey = env.lit(Col.hex(0xA8B0BC)); val tip = env.lit(Col.hex(0x2A2A30))
        sprite(bx, by) {
            c.set(bx, by, white); c.set(bx + 1, by, white)
            if (up) {
                c.set(bx - 1, by - 1, grey); c.set(bx - 2, by - 2, grey); c.set(bx - 3, by - 2, tip)
                c.set(bx + 2, by - 1, grey); c.set(bx + 3, by - 2, grey); c.set(bx + 4, by - 2, tip)
            } else {
                c.set(bx - 1, by - 1, grey); c.set(bx - 2, by, grey); c.set(bx - 3, by + 1, tip)
                c.set(bx + 2, by - 1, grey); c.set(bx + 3, by, grey); c.set(bx + 4, by + 1, tip)
            }
        }
    }

    // ------------------------------------------------------------------ weather

    /**
     * The sea's weather: mist lying on the water (thickest along the horizon, over the whole sea), a squall's rain hanging
     * dark over the lagoon before the drops reach us, the drops ringing the water, snow, lightning far out at sea.
     */
    override fun weather() {
        val sky = frame.sky
        squall(sky.rain, sky.gloom)
        val wx = Weather(c, w, h, detail, t, env, sky)
        val horizonRow = hz / detail.toFloat(); val bottom = h / detail.toFloat()
        wx.fog(sky.fog) { row -> if (row < horizonRow) exp((row - horizonRow) / 14f) else 1f - 0.45f * ((row - horizonRow) / max(1f, bottom - horizonRow)) }
        wx.rain()
        rings(sky.rain)
        wx.snow()
        val flash = wx.flash()
        if (flash > 0f) {
            wx.lighten(flash, 0.42f, 0.42f)
            if (flash >= 1f) wx.boltLine(8 * detail, hz, wx.bolt())
        }
    }

    /** A curtain of rain under the clouds over the lagoon on the left: dark shafts slanting in the wind. */
    private fun squall(rain: Float, gloom: Float) {
        val level = rain * (0.5f + 0.5f * gloom)
        if (level <= 0.03f) return
        val z = z
        val grey = env.lit(Col.hex(0x5A6270))
        val mid = ox + (46f + sin(t * 0.05).toFloat() * 14f) * z
        val half = 52f * z
        for (py in max(0, c.top) until min(hz + 8 * z, c.bottom)) {
            val row = py / z.toFloat()
            val prof = ((row - (hz / z - 60f)) / 25f).coerceIn(0f, 1f)
            if (prof <= 0f) continue
            for (px in max((mid - half).toInt(), c.left) until min((mid + half).toInt(), c.right)) {
                if (px !in 0 until w) continue
                val edge = 1f - abs(px + 0.5f - mid) / half
                val shaft = 0.5f + 0.5f * Noise.v1((px - ox) / z * 0.18f - row * 0.08f * (0.3f + frame.sky.wind) + t.toFloat() * 0.4f, 471)
                val a = level * 0.7f * prof * edge * shaft
                if (a > Dither.at(px, py) * 0.4f) { val i = c.index(px, py); c.pixels[i] = Col.mix(c.pixels[i], grey, min(0.8f, a)) }
            }
        }
    }

    /** Raindrops ringing the water: little rings spreading on the lagoon and the sea near by. */
    private fun rings(level: Float) {
        if (level <= 0.05f) return
        val z = z
        val col = Col.mix(env.lit(Col.hex(0xC8D4E0)), Col.hex(0xC8D4E0), 0.3f)
        val n = (70 * level).toInt()
        for (j in 0 until n) {
            val cycle = t * 1.8 + Noise.rnd(j, 481)
            val ph = (cycle % 1.0).toFloat()
            val k = cycle.toInt()
            val d = 12f + Noise.rnd(j, 482, k) * 30f
            val x = (Noise.rnd(j, 483, k) - 0.5f) * (w / F) * d
            val px = gx(x, d).toInt(); val py = gy(d).toInt()
            if (!c.inside(px, py)) continue
            val r = region[c.index(px, py)]
            if (r != LAGOON && r != SEA) continue
            val rr = (1f + ph * 2.5f) * z
            for (q in 0 until 8) {
                val a = q * 0.785f
                c.blend((px + cos(a) * rr).toInt(), (py + sin(a) * rr * 0.4f).toInt(), col, 0.6f * (1f - ph))
            }
        }
    }

    companion object {
        private const val LAGOON: Byte = 1
        private const val SEA: Byte = 2
        private const val FAR: Byte = 3
        private const val BEACH: Byte = 4

        /** Beyond this depth (m) the sea is the horizon's strip. */
        private const val FAR_D = 160f
    }
}
