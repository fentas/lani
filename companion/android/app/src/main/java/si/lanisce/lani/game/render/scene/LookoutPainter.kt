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
import kotlin.math.sqrt

/**
 * Na razglednem stolpu: the view from the top of the village's lookout tower on its hill (the project `razgledni_stolp`,
 * Gostilničarka Vida's), a painted vista (companion/SCENES.md "Arts"). We stand on its platform: the plank floor, the railing of
 * peeled logs across the front, the corner posts and their braces either side, the shingle roof overhead with the flag on its
 * pole; on the railing in the middle the board with the mountains drawn on it and their names, on the right the telescope on its
 * post, on the left post the little box with the visitors' book and the stamp, the bench along the left side, the stairs coming
 * up through their hatch in the floor at the right.
 *
 * The view: far below the village in its clearing in the woods, its red roofs and the church's spire; the valley beyond it, its
 * fields and the river winding; green hills, the forest; on the left the Julian Alps along the sky with Triglav's three heads
 * in the middle of them, snow on it most of the year; on the right the plain going down to the sea, a glint at the horizon. The
 * haze of the day (summer's milky, autumn's and winter's clear) hides the far land or shows it; the peaks glow pink at sunrise
 * and sunset; the village's windows are lit at night and the moon lies on the sea.
 *
 * Its effects ([si.lanisce.lani.game.scene.SceneArt.effects]):
 * - `clear`: how clear the air is: 0 a white haze over the far land, the Alps and the sea gone in it; 1 clear to the sea and
 *   every peak sharp. Not set: as the day is (hazy in summer, clear in autumn and winter, clearer after rain).
 * - `glow`: the alpenglow on the peaks (how pink). Not set: at sunrise and sunset.
 * - `hawk`: a buzzard circling over the valley below us, its wings spread (how many rounds; 0 none).
 * - `bell`: the village's church bell far below, its rings coming up (how hard).
 *
 * Tapped ([pokes]): the telescope turns on its post to the left, then to the right and back.
 *
 * Closer up ([detail] 2 and 3): the shingles, the logs' grain, the board's drawing and its little names, the telescope's
 * brass, the village's houses, the fields' furrows and hedges.
 *
 * Friuli's look ([look]; companion/SCENES.md "Culture looks"): the view of Friuli: the Collio's hills striped with vines, the
 * plain with its rows of poplars, the village on the hill as Friuli builds it with its campanile, the lagoon and the sea at
 * Grado on the right; the flag on the roof the golden eagle of Friuli.
 *
 * The Lake District's viewing station ([lakes]; At the viewing station, Mrs Birkett's, as the Claife Station above Windermere
 * was): we stand inside the little stone station on its rock above the lake, at its big window. The corner posts are the
 * window's jambs of slate stone, the ceiling whitewashed over the slate eave, and across the window's top a row of panes of
 * coloured glass, green for spring, yellow for summer, orange for autumn and blue for winter ([panes], the `tower`'s); the
 * flag St George's cross. The view: the fells round the dale, rounded, green and brown with crags, bracken russet in autumn,
 * snow on their tops in winter ([fellTop]); among them a pike, a peaked fell with its two knobbly tops (the `peak`,
 * [pikeTop]); far below the lake winding along the dale with its wooded island (the `sea`, [lake]); the village of grey stone
 * and whitewash under slate round its battlemented church tower; the dale's fields walled with grey dry-stone walls, green
 * pasture most of them; the oak woods.
 *
 * Carinthia's look (`view` "kaernten"; kaernten-projects-b): the view east down the lower Gailtal, the Dobratsch on the left
 * with its long back, its steep south face and the transmitter mast on top, the Karawanken's jagged ridge along the sky on
 * the right and the Mittagskogel's pyramid with its summit cross among them (the `peak`); the Faaker See turquoise in the
 * valley under it (the art's `sea`, its glints); the village below white with timbered upper storeys under dark shingles
 * round its onion-domed church, Villach far off where the valley opens; Austria's red, white and red on the roof.
 */
internal class LookoutPainter : VistaPainter() {
    override val art = "lookout"
    override val horizon = 52
    override val pokes = listOf(Poke("telescope", listOf(1.8, 2.6), rest = 2.5))

    /** North over the land toward the Alps: the Plough at night. */
    override val skyFacing = 350.0

    // the land far below: the eye LAND_EYE m above it, a point d m away F × LAND_EYE / d rows under the horizon
    private fun ly(d: Float): Float = hz + F * LAND_EYE / d
    private fun lx(x: Float, d: Float): Float = vx + F * x / d
    private fun ldepth(py: Int): Float = F * LAND_EYE / max(0.35f, py + 0.5f - hz)
    private fun lside(px: Int, d: Float): Float = (px + 0.5f - vx) * d / F

    // the platform: the railing stands RAIL_D m away (its foot's row), the floor runs from it to us
    private val railD = 11.6f
    private val railH = 1.15f

    // the people: at the railing, by the board, by the stairs
    private val railPX = -1.6f; private val railPD = 10.7f
    private val boardPX = 1.25f; private val boardPD = 10.8f
    private val stairsPX = 3.7f; private val stairsPD = 10.1f

    // the village far below and its church; the river; the sea's band on the right
    private val villageX = -120f; private val villageD = 640f

    private val logL = Col.hex(0xC49A68); private val logM = Col.hex(0xA27848); private val logD = Col.hex(0x7A5632)
    private val plankL = Col.hex(0xB88A58); private val plankM = Col.hex(0x9A7046); private val plankD = Col.hex(0x6E4E30)

    /** Friuli's view ([readLook2]). */
    private var friuli = false

    /** Carinthia's view ([readLook2]): the Dobratsch and the Karawanken with the Mittagskogel, the Faaker See, a Carinthian village. */
    private var kaernten = false

    private val z: Int get() = detail
    private var haze = 0.5f
    private val winter: Boolean get() = env.season == Season.WINTER

    private fun readLook2() {
        friuli = variant("view") == "friuli"
        kaernten = variant("view") == "kaernten"
        lakes = variant("view") == "lakeland"
    }

    /** The Lake District's viewing station and its view ([readLook2]). */
    private var lakes = false

    /** How clear the air is: `clear`, else by the season and the weather (hazy summer, clear autumn and winter). */
    private fun clarity(): Float {
        fx("clear")?.let { return it.coerceIn(0f, 1f) }
        val base = when (env.month) { 6, 7, 8 -> 0.45f; 9, 10, 11 -> 0.85f; 12, 1, 2 -> 0.9f; else -> 0.65f }
        return (base - frame.sky.gloom * 0.4f - frame.sky.fog * 0.6f).coerceIn(0f, 1f)
    }

    /** [col] far off, through the haze: more of the horizon's colour the farther ([far] 0..1) and the hazier the day. */
    private fun distant(col: Int, far: Float): Int = Col.mix(col, Col.mix(env.skyHorizon, Col.hex(0xF0F2F4), 0.4f * (1f - env.dark)), (far * (0.35f + 0.6f * haze)).coerceIn(0f, 0.95f))

    override fun paint() {
        setup()
        readLook2()
        haze = 1f - clarity()
        val z = z
        vistaSky(3, 81, moonX = ox + 196 * z, moonY = oy + 14 * z)
        thing("mountains", outline = 0) { if (kaernten) karawanken() else alps() }
        thing("peak", outline = 0) { if (kaernten) mittagskogel() else triglav() }
        if (kaernten) prop(0) { summits() }
        farHills(near = false)
        if (!kaernten && !lakes) thing("sea", outline = 0) { sea() }
        farHills(near = true)
        thing("valley", outline = 0) { valley() }
        // the lake winding along the dale below, its island: over the valley's fields
        if (lakes) thing("sea", outline = 0) { lake() }
        prop(0) { river() }
        // Carinthia's lake lies in the valley under the Mittagskogel, and Villach where the valley opens
        if (kaernten) {
            thing("sea", outline = 0) { faakerSee() }
            prop(0) { villach() }
        }
        thing("forest", outline = Pal.OUTLINE_TREE) { woods() }
        thing("village") { village() }
        hawk()
        churchBell()
        // the tower: the floor, the bench, the hatch and its stairs, the railing with the board, the telescope, the box, the posts and the roof
        prop { floor() }
        thing("bench") { bench() }
        thing("railing") { railing() }
        thing("board") { board() }
        thing("telescope", slop = 1) { telescope() }
        thing("book", slop = 1) { boxAndBook() }
        thing("stamp", slop = 2) { stamp() }
        for (p in peopleAt("rail")) person(p, gx(railPX, railPD).toInt(), gy(railPD).toInt(), flip = false)
        for (p in peopleAt("board")) person(p, gx(boardPX, boardPD).toInt(), gy(boardPD).toInt(), flip = true)
        // whoever has come up the stairs stands before the railing, just past the hatch and its rail
        for (p in peopleAt("stairs")) person(p, gx(stairsPX, stairsPD).toInt(), gy(stairsPD).toInt(), flip = true)
        thing("stairs") { stairs() }
        // the corner posts: the left one scenery, the right one (the flag's) the tower to tap, its box no wider than a post
        prop { post(-1) }
        thing("tower") { post(1); if (lakes) panes() }
        thing("roof") { roof() }
        thing("flag", slop = 1) { flag() }
    }

    // ------------------------------------------------------------------ the far land

    /** The sea on the right at the horizon: a band over the plain, its glints drifting (the moon's at night), gone in a haze. */
    private fun sea() {
        val z = z
        val x0 = ox + 150 * z
        val body = env.lit(distant(if (friuli) Col.hex(0x4A90C4) else Col.hex(0x3F84C4), 0.6f))
        val line = distant(Col.mix(env.skyHorizon, Col.hex(0xFFFFFF), 0.4f), 0.4f)
        c.penEmissive = true
        for (x in max(x0, c.left) until min(w, c.right)) {
            val u = (x - x0) / (60f * z)
            val rows = ((1.5f + 3f * min(1f, u)) * z * (if (friuli) 1.4f else 1f)).toInt()
            val top = hz - rows
            for (y in max(top, c.top) until min(hz + z, c.bottom)) {
                val g = Noise.rnd(floor((x - ox) / (3f * z) + t * 0.3).toInt(), (y - top) / z, 811)
                c.set(x, y, when {
                    y < top + z -> line
                    g > 0.9f && haze < 0.7f -> if (env.dark > 0.5f) Col.mix(body, Col.hex(0xD8D2B0), 0.5f) else Col.mix(line, Col.hex(0xFFFFFF), 0.6f)
                    else -> body
                })
            }
        }
        c.penEmissive = false
    }

    /**
     * The lake far below ([lakes]): winding along the dale from the right toward the far end, its water the sky's colour darker,
     * glints drifting on it, its shores a little lighter; a wooded island in it; gone grey in a haze.
     */
    private fun lake() {
        val z = z
        val railTop = hy(railD, 0f).toInt()
        val top = (hz + 12 * z).toInt()
        val water = Col.mix(Col.hex(0x3E6A8E), env.skyHorizon, 0.25f)
        val shine = Col.mix(env.skyHorizon, Col.hex(0xFFFFFF), 0.4f)
        c.penEmissive = true
        for (py in max(top, c.top) until min(railTop + 2 * z, c.bottom)) {
            val d = ldepth(py)
            if (d < 420f || d > 2600f) continue
            val far = ((d - 300f) / 3000f).coerceIn(0f, 1f)
            val mid = 260f + 120f * sin(d / 600f); val half = 140f + d * 0.05f
            for (px in max(0, c.left) until min(w, c.right)) {
                val x = lside(px, d)
                val e = half - abs(x - mid)
                if (e < 0f) continue
                // the island: a round of oaks in the water
                val isle = (x - 300f) * (x - 300f) / (70f * 70f) + (d - 1150f) * (d - 1150f) / (120f * 120f)
                val col = when {
                    isle < 1f -> if (isle < 0.5f && Noise.rnd(px / max(1, z), py / max(1, z), 881) < 0.5f) Col.hex(0x3E6E3E) else Col.hex(0x2A5230)
                    env.season == Season.WINTER && e < 18f -> Pal.SNOW_L
                    e < 12f -> Col.mix(water, Col.hex(0xB8C8B0), 0.4f)
                    Noise.rnd(floor((px - ox) / (3f * z) + t * 0.25).toInt(), (py - top) / z, 883) > 0.93f && haze < 0.7f -> shine
                    else -> water
                }
                c.set(px, py, env.lit(distant(col, 0.15f + far * 0.6f)))
            }
        }
        c.penEmissive = false
    }

    /** How high the Alps stand at scene column [u] (0 … 240), in scene px over the horizon: tall on the left, sinking to the south. */
    private fun alpsTop(u: Float): Float {
        val tall = (1f - (u - 20f) / 150f).coerceIn(0.15f, 1f)
        val a = 1f - abs(Noise.v1(u * 0.05f, 821) * 2f - 1f)
        val b = 1f - abs(Noise.v1(u * 0.13f, 822) * 2f - 1f)
        return 6f + 22f * tall * (a * a * 0.7f + b * 0.3f)
    }

    /** The glow on the peaks (0 … 1): `glow`, else round sunrise and sunset. */
    private fun glow(): Float = fx("glow") ?: if (env.dark < 0.6f && frame.sky.gloom < 0.5f) (1f - abs(env.sun - 0.06f) / 0.18f).coerceIn(0f, 1f) else 0f

    /** The Julian Alps along the sky on the left: grey rock lit on the sun's side, snow on the tops (down the slopes in winter). */
    private fun alps() = if (lakes) range(0f, 240f) { u -> fellTop(u) } else range(0f, 175f) { u -> alpsTop(u) }

    /** How high the fells stand at scene column [u] ([lakes]): rounded tops along the whole dale, higher on the left. */
    private fun fellTop(u: Float): Float {
        val tall = (1f - (u - 30f) / 260f).coerceIn(0.45f, 1f)
        return 5f + 17f * tall * (Noise.v1(u * 0.028f, 825) * 0.75f + Noise.v1(u * 0.09f, 826) * 0.25f)
    }

    /** The pike among the fells ([lakes]): a steep fell with two knobbly tops, the higher on the right. */
    private fun pikeTop(u: Float): Float {
        val dx = (u - PIKE_U) / 16f
        val body = max(0f, 1f - dx * dx) * 24f
        val knobs = 4f * max(0f, 1f - abs(dx - 0.18f) * 6f) + 3f * max(0f, 1f - abs(dx + 0.3f) * 7f)
        return max(fellTop(u), body + knobs)
    }

    /** Triglav among them: its broad shoulders and the three heads, the highest in the middle, whiter than the rest. */
    private fun triglav() = if (lakes) range(PIKE_U - 16f, PIKE_U + 16f) { u -> pikeTop(u) } else range(TRIGLAV_U - 18f, TRIGLAV_U + 18f) { u ->
        val dx = (u - TRIGLAV_U) / 18f
        val body = (1f - dx * dx) * 30f
        val heads = 3.2f * (max(0f, 1f - abs(dx) * 7f) + 0.6f * max(0f, 1f - abs(dx + 0.38f) * 8f) + 0.5f * max(0f, 1f - abs(dx - 0.42f) * 8f))
        max(alpsTop(u), body + heads)
    }

    // ------------------------------------------------------------------ Carinthia's view (kaernten-projects-b)

    /**
     * How high Carinthia's mountains stand at scene column [u] (scene px over the horizon), the view east down the lower
     * Gailtal: on the left the Dobratsch (the Villacher Alpe), rising to its long gentle back and falling at its east end in
     * the steep south face; on the right the Karawanken's jagged ridge along the sky.
     */
    private fun kaerntenTop(u: Float): Float {
        val dobratsch = when {
            u < 28f -> 13f + 12f * (u / 28f)
            u < 78f -> 25f - 2.5f * ((u - 28f) / 50f) + 1.4f * (Noise.v1(u * 0.25f, 861) - 0.5f)
            u < 96f -> (22.5f - 15f * ((u - 78f) / 18f)) * (1f - 0.08f * Noise.v1(u * 0.9f, 862))
            else -> 0f
        }
        val a = 1f - abs(Noise.v1(u * 0.09f, 863) * 2f - 1f)
        val b = 1f - abs(Noise.v1(u * 0.23f, 864) * 2f - 1f)
        val karawanken = if (u > 90f) (11f + 11f * (a * a * 0.7f + b * 0.3f)) * ((u - 90f) / 10f).coerceIn(0f, 1f) else 0f
        return max(max(dobratsch, karawanken), 6f)
    }

    /** The Dobratsch and the Karawanken (`mountains`): limestone grey lit on the sun's side, snow on the tops. */
    private fun karawanken() = range(0f, 240f) { u -> kaerntenTop(u) }

    /** The Mittagskogel among the Karawanken (`peak`): its pyramid, higher and sharper than the ridge round it. */
    private fun mittagskogel() = range(MITTAGSKOGEL_U - 17f, MITTAGSKOGEL_U + 17f) { u ->
        val dx = abs(u - MITTAGSKOGEL_U) / 17f
        max(kaerntenTop(u), 35f * (1f - dx) + 2f * max(0f, 1f - dx * 6f))
    }

    /** The Dobratsch's transmitter mast on its back, red and white, and the Mittagskogel's summit cross, dark against the sky. */
    private fun summits() {
        val z = z
        val mx = (ox + DOBRATSCH_MAST_U * z).toInt()
        val foot = (hz + z - kaerntenTop(DOBRATSCH_MAST_U) * z).toInt()
        for (j in 0 until 6 * z) c.set(mx, foot - 1 - j, env.lit(distant(if ((j / max(1, z)) % 2 == 0) Pal.FLAG_RED else Pal.FLAG_WHITE, 0.6f)))
        val cx = (ox + MITTAGSKOGEL_U * z).toInt()
        val top = (hz + z - 37f * z).toInt()
        val dark = env.lit(distant(Col.hex(0x3A3440), 0.5f))
        for (j in 0 until 3 * z) c.set(cx, top - j, dark)
        c.hline(cx - max(1, z / 2 + 1) + 1, cx + max(1, z / 2 + 1) - 1, top - 2 * z + max(1, z / 2), dark)
    }

    /**
     * The Faaker See (the art's `sea` in Carinthia's look): a turquoise lake in the valley under the Mittagskogel, its far
     * shore wooded, the sun's glints on it by day (the moon's at night), grey in a haze.
     */
    private fun faakerSee() {
        val z = z
        val cx = ox + (MITTAGSKOGEL_U - 6f) * z; val cy = hz + 16.5f * z
        val rx = 21f * z; val ry = 2.6f * z
        val body = env.lit(distant(if (winter) Col.hex(0x7EA8B4) else Col.hex(0x3EB0B8), 0.35f))
        val deep = env.lit(distant(if (winter) Col.hex(0x6A92A0) else Col.hex(0x2A8C9A), 0.35f))
        val shine = env.lit(distant(Col.hex(0xE8FAF6), 0.3f))
        c.penEmissive = true
        for (y in max((cy - ry).toInt(), c.top)..min((cy + ry).toInt(), c.bottom - 1)) {
            val v = (y + 0.5f - cy) / ry
            val half = rx * sqrt(max(0f, 1f - v * v)) * (1f + 0.12f * (Noise.v1(y * 0.7f, 871) - 0.5f))
            for (x in max((cx - half).toInt(), c.left)..min((cx + half).toInt(), c.right - 1)) {
                val g = Noise.rnd(floor((x - ox) / (3f * z) + t * 0.25).toInt(), y / max(1, z), 872)
                c.set(x, y, when {
                    v < -0.55f -> deep
                    g > 0.92f && haze < 0.7f -> if (env.dark > 0.5f) Col.mix(body, Col.hex(0xD8D2B0), 0.5f) else shine
                    else -> body
                })
            }
        }
        c.penEmissive = false
    }

    /** Villach far off where the valley opens: a grey huddle of roofs and the tall tower of its church, in the haze. */
    private fun villach() {
        val z = z
        val bx = (ox + 118f * z).toInt(); val base = hz + 13 * z
        val roof = env.lit(distant(Col.hex(0x8A7A70), 0.7f)); val wall = env.lit(distant(Col.hex(0xE6E2DA), 0.7f))
        for (i in 0 until 6) {
            val hx = bx + i * 2 * z; val hh = (1 + (i % 2)) * z
            c.fillRect(hx, base - hh, 2 * z - 1, hh, if (i % 2 == 0) wall else roof)
        }
        c.fillRect(bx + 5 * z, base - 5 * z, max(1, z), 5 * z, wall)
        if (env.windows > 0.35f) for (i in 0 until 4) c.set(bx + i * 3 * z, base - z, Pal.WINDOW_LIT)
    }

    /** A range of peaks between scene columns [u0] and [u1], its crest at [top] (scene px over the horizon). */
    private inline fun range(u0: Float, u1: Float, top: (Float) -> Float) {
        val z = z
        val snowLine = when (env.season) { Season.SUMMER -> 0.75f; Season.AUTUMN -> 0.55f; Season.WINTER -> 0.12f; else -> 0.4f }
        val g = glow()
        val sunLeft = (sunAt()?.first ?: vx) < vx
        c.penEmissive = true
        for (x in max(max(0, (ox + u0 * z).toInt()), c.left) until min(min(w, (ox + u1 * z).toInt()), c.right)) {
            val u = (x + 0.5f - ox) / z
            val tt = top(u)
            val facing = (top(u + 1.5f) - top(u - 1.5f)).let { if (sunLeft) it < 0f else it > 0f }
            val crest = hz + z - tt * z
            for (y in max(crest.toInt(), c.top) until min(hz + z, c.bottom)) {
                val up = (hz + z - y - 0.5f) / z
                val rel = up / max(1f, tt)
                val snowy = rel > (if (lakes) (if (env.season == Season.WINTER) 0.45f else 2f) else snowLine) + (Noise.v2(u * 0.2f, up * 0.4f, 823) - 0.5f) * 0.3f
                val gully = Noise.v2(u * 0.4f + up * 0.2f, up * 0.3f, 824) > 0.7f
                var col = if (lakes) fellColour(u, up, rel, snowy, gully, facing) else when {
                    snowy -> if (facing) Col.hex(0xF4F7FF) else Col.hex(0xC0CCE4)
                    gully -> Col.hex(0x6E7088)
                    facing -> Col.hex(0xA8A8BC)
                    else -> Col.hex(0x7E8098)
                }
                if (g > 0f && facing) col = Col.mix(col, if (snowy) Col.hex(0xFFC4AC) else Col.hex(0xE89A80), 0.5f * g)
                c.set(x, y, env.lit(distant(col, 0.75f)))
            }
        }
        c.penEmissive = false
    }

    /** A fell's colour ([lakes]) at scene column [u], [up] scene px over the horizon ([rel] of its height): grass, bracken, crags, snow. */
    private fun fellColour(u: Float, up: Float, rel: Float, snowy: Boolean, gully: Boolean, facing: Boolean): Int {
        val autumn = env.season == Season.AUTUMN
        val bracken = Noise.v2(u * 0.15f, up * 0.35f, 827) > 0.55f
        val crag = rel > 0.6f && Noise.v2(u * 0.3f, up * 0.5f, 828) > 0.62f
        return when {
            snowy -> if (facing) Col.hex(0xF4F7FF) else Col.hex(0xC8D2E6)
            crag || gully -> if (facing) Col.hex(0x8A9098) else Col.hex(0x6A7078)
            bracken && autumn -> if (facing) Col.hex(0xB8683A) else Col.hex(0x96522E)
            bracken -> if (facing) Col.hex(0x6E9A4A) else Col.hex(0x56803E)
            autumn -> if (facing) Col.hex(0xA8A060) else Col.hex(0x8A8450)
            else -> if (facing) Col.hex(0x88A862) else Col.hex(0x6A8A4E)
        }
    }

    /** The hills between the Alps and the valley: blue far ones, green near ones with woods (vines in Friuli's Collio). */
    private fun farHills(near: Boolean) {
        val z = z
        if (!near) {
            // the far hills under the Alps, greyed green in the distance, sinking toward the plain and the sea on the right
            val farCol = distant(if (env.season == Season.WINTER) Col.hex(0xA8B4C2) else Col.hex(0x6E8E78), 0.55f)
            val crest = ridge(hz.toFloat() + 2f * z, 7f * z, 0.016f, 831, farCol, 0f, bottom = hz + 13 * z)
            treeline(crest, 4 * z, 1.4f * z, distant(Col.hex(0x4E6E58), 0.55f), 0f, 832, 0, ox + 150 * z)
            return
        }
        val nearCol = distant(when (env.season) { Season.WINTER -> Col.hex(0xC8D2DE); Season.AUTUMN -> Col.hex(0x8A7A44); else -> Col.hex(0x54804A) }, 0.3f)
        val crest = if (friuli) ridge(hz.toFloat() + 9f * z, 9f * z, 0.022f, 833, nearCol, 0f, bottom = hz + 14 * z, texture = { x, y, below -> collioSlope(x, y, below, 835) })
        else ridge(hz.toFloat() + 9f * z, 9f * z, 0.022f, 833, nearCol, 0f, bottom = hz + 14 * z)
        treeline(crest, 4 * z, 1.6f * z, distant(if (env.season == Season.WINTER) Col.hex(0x6E7A86) else Col.hex(0x2E5A36), 0.3f), 0f, 837)
    }

    /** The valley floor far below: a patchwork of fields with hedges between them, in the season's colours, in the haze. */
    private fun valley() {
        // down to the platform's floor: the land shows between the railing's rails too
        val railTop = hy(railD, 0f).toInt()
        val top = (hz + 12 * z).toInt()
        c.penEmissive = true
        for (py in max(top, c.top) until min(railTop + 2 * z, c.bottom)) {
            val d = ldepth(py)
            val far = ((d - 300f) / 3000f).coerceIn(0f, 1f)
            for (px in max(0, c.left) until min(w, c.right)) {
                val x = lside(px, d)
                val cx = floor(x / 140f + Noise.v1(d / 500f, 841) * 0.6f).toInt(); val cd = floor(d / 220f).toInt()
                val kind = (Noise.rnd(cx, cd, 842) * 5f).toInt()
                var col = when (env.season) {
                    Season.WINTER -> if (kind == 2) Col.hex(0xD8E0EA) else Pal.SNOW_L
                    Season.SPRING -> when (kind) { 0 -> Col.hex(0x8CC45A); 1 -> Col.hex(0x6AA84A); 2 -> Col.hex(0xB8D06A); 3 -> Col.hex(0x9A7A52); else -> Col.hex(0x7AB658) }
                    Season.SUMMER -> when (kind) { 0 -> Col.hex(0xD8C060); 1 -> Col.hex(0x5E9A42); 2 -> Col.hex(0x8AB450); 3 -> Col.hex(0xC8A858); else -> Col.hex(0x6CA44C) }
                    Season.AUTUMN -> when (kind) { 0 -> Col.hex(0x9A7A4E); 1 -> Col.hex(0x7E8E48); 2 -> Col.hex(0xB08A4A); 3 -> Col.hex(0x8A6A44); else -> Col.hex(0x6E8A46) }
                }
                if (lakes && env.season != Season.WINTER) col = when (kind) { 0 -> if (env.season == Season.SUMMER) Col.hex(0xC8C068) else Col.hex(0x7AA858); 3 -> Col.hex(0x5E8A46); else -> if (env.season == Season.AUTUMN) Col.hex(0x7E9A50) else Col.hex(0x6E9E52) }
                val fxv = x / 140f + Noise.v1(d / 500f, 841) * 0.6f
                if (lakes && (fxv - floor(fxv) < 0.05f || (d / 220f) - floor(d / 220f) < 0.06f) && env.season != Season.WINTER) col = Col.hex(0x9AA0A4)
                else if (fxv - floor(fxv) < 0.06f && env.season != Season.WINTER) col = Col.scale(col, 0.72f)
                if (Dither.at(px, py) < 0.15f) col = Col.scale(col, 0.94f)
                c.set(px, py, env.lit(distant(col, 0.2f + far * 0.6f)))
            }
        }
        c.penEmissive = false
        // Friuli's plain: rows of poplars along its ditches
        if (friuli) for (k in 0 until 9) {
            val d = 900f + k * 260f; val x = -700f + Noise.rnd(k, 845) * 1400f
            val bx = lx(x, d).toInt(); val by = ly(d).toInt()
            for (j in 0 until 6) {
                val px = bx + (j * 3 - 8) * z; val hh = (2 + (j % 2)) * z
                c.fillRect(px, by - hh, max(1, z), hh, env.lit(distant(Col.hex(0x3E6A36), 0.4f)))
            }
        }
    }

    /** The river winding through the valley, catching the sky. */
    private fun river() {
        val z = z
        c.penEmissive = true
        val col = env.lit(distant(Col.mix(skyAt(0.5f), Col.hex(0x6FA6D0), 0.4f), 0.3f))
        for (py in max((hz + 13 * z), c.top) until min(hy(railD, 0f).toInt() + z, c.bottom)) {
            val d = ldepth(py)
            val xr = 250f + 300f * sin(d / 900f) - d * 0.08f
            val half = max(0.5f, 22f * F / d / 2f)
            val cx = lx(xr, d)
            for (px in max((cx - half).toInt(), c.left)..min((cx + half).toInt(), c.right - 1)) c.set(px, py, col)
        }
        c.penEmissive = false
    }

    /** The woods round the village's clearing and along the hills' foot: dark round crowns, gold in autumn, white in winter. */
    private fun woods() {
        val z = z
        val (d0, l0) = when (env.season) {
            Season.WINTER -> Col.hex(0x6E7884) to Pal.SNOW_L
            Season.AUTUMN -> Col.hex(0x8A5A22) to Col.hex(0xC89238)
            else -> Col.hex(0x264E2E) to Col.hex(0x3E6E3E)
        }
        c.penEmissive = true
        for (k in 0 until 420) {
            val d = 330f + Noise.rnd(k, 851) * 1100f
            val x = -1000f + Noise.rnd(k, 852) * 2000f
            // woods in patches, thick round the village's clearing (but not in it)
            val near = abs(x - villageX) < 260f && abs(d - villageD) < 260f
            if (Noise.v2(x / 380f, d / 300f, 853) < (if (near) 0.35f else 0.56f)) continue
            if (abs(x - villageX) < 110f && abs(d - villageD) < 130f) continue
            val bx = lx(x, d); val by = ly(d)
            if (by > hy(railD, 0f) + 2 * z) continue
            val r = max(1f, 24f * F / d / 2f)
            c.fillEllipse(bx, by - r * 0.6f, r * 1.2f, r * 0.8f, env.lit(distant(d0, ((d - 300f) / 3000f).coerceIn(0f, 1f))))
            c.fillEllipse(bx - r * 0.3f, by - r * 0.85f, r * 0.6f, r * 0.4f, env.lit(distant(l0, ((d - 300f) / 3000f).coerceIn(0f, 1f))))
        }
        c.penEmissive = false
    }

    /** The village far below in its clearing: white houses with red roofs (Friuli's coppi), the church with its spire (Friuli's campanile). */
    private fun village() {
        val z = z
        val lit = env.windows > 0.35f
        val p = F / villageD
        val cx = lx(villageX, villageD); val base = ly(villageD)
        if (lakes) {
            val st = FireStyle.of(frame.world.culture, frame.world.landscape)
            c.penEmissive = true
            for (i in 0 until 7) farSlateHouse((cx - 18f * z + i * 5f * z).toInt(), (base + (i % 3) * z).toInt(), 4 * z, 2 * z, st, i, lit, 0.2f)
            farTowerChurch((cx + 3f * z).toInt(), base.toInt() - z, st, lit, 0.2f, tall = 9)
            c.penEmissive = false
            return
        }
        if (friuli) {
            val st = FireStyle.of(frame.world.culture, frame.world.landscape)
            for (i in 0 until 7) farCoppiHouse((cx - 18f * z + i * 5f * z).toInt(), (base + (i % 3) * z).toInt(), 4 * z, 3 * z, st, i, lit, 0.2f)
            farCampanile((cx + 2f * z).toInt(), base.toInt() - z, st, lit, 0.2f, tall = 11)
            return
        }
        // Carinthia's: white houses with their timbered upper storeys under dark shingles, the onion-domed church among them
        if (kaernten) {
            val st = FireStyle.of(frame.world.culture, frame.world.landscape)
            for (i in 0 until 7) farAlpineHouse((cx - 18f * z + i * 5f * z).toInt(), (base + (i % 3) * z).toInt(), 4 * z, 3 * z, st, i, lit, 0.2f)
            farOnionChurch((cx + 2f * z).toInt(), base.toInt() - z, st, lit, 0.2f, tall = 11)
            return
        }
        val wall = env.lit(Col.hex(0xF2EEE4)); val wallD = env.lit(Col.hex(0xC8C0B0))
        val roof = env.lit(Pal.ROOF_M); val roofD = env.lit(Pal.ROOF_D)
        c.penEmissive = true
        for (i in 0 until 8) {
            val hx = (cx - 20f * z + i * 5f * z + (if (i % 2 == 0) 0 else z)).toInt()
            val hb = (base + ((i * 7) % 3) * z).toInt()
            c.fillRect(hx, hb - 2 * z, 4 * z, 2 * z, wall)
            c.fillRect(hx + 3 * z, hb - 2 * z, z, 2 * z, wallD)
            for (r in 0 until 2 * z) c.hline(hx - z / 2 + r / 2, hx + 4 * z + z / 2 - 1 - r / 2, hb - 2 * z - 1 - r, if (r < z / 2 + 1) roofD else roof)
            if (lit && i % 3 != 1) c.fillRect(hx + z, hb - z - z / 2, max(1, z / 2 + 1), max(1, z / 2 + 1), Pal.WINDOW_LIT)
        }
        // the church: its nave and the tower with the spire
        val tx = (cx + 4f * z).toInt(); val tb = (base - z).toInt()
        c.fillRect(tx, tb - 3 * z, 5 * z, 3 * z, wall)
        for (r in 0 until 2 * z) c.hline(tx - z / 2 + r / 2, tx + 5 * z + z / 2 - 1 - r / 2, tb - 3 * z - 1 - r, if (r < z / 2 + 1) roofD else roof)
        c.fillRect(tx - 2 * z, tb - 8 * z, 2 * z, 8 * z, wall)
        for (r in 0 until 4 * z) {
            val half = (z + z * 0.2f) * (1f - r / (4f * z))
            c.hline((tx - z - half).toInt(), (tx - z + half).toInt() - 1, tb - 8 * z - 1 - r, if (r % (2 * z) < z) roofD else roof)
        }
        c.penEmissive = false
    }

    /** A buzzard circling over the valley below us (`hawk`): its wings spread, banking round. */
    private fun hawk() {
        val k = fxOn("hawk")
        if (k <= 0.02f) return
        val z = z
        val a = (t * 0.6).toFloat()
        val cx = ox + (150f + cos(a) * 26f * k) * z; val cy = hz + (30f + sin(a) * 9f * k) * z
        prop(0) {
            val dark = env.lit(Col.hex(0x4A3424)); val light = env.lit(Col.hex(0xC8A878))
            val span = 5 * z
            for (i in -span..span) {
                val f = abs(i) / span.toFloat()
                val yy = cy - f * 1.5f * z * sin(a * 2f + 1f).coerceAtLeast(0.2f)
                c.set((cx + i).toInt(), yy.toInt(), if (f > 0.75f) dark else light)
                if (z > 1) c.set((cx + i).toInt(), yy.toInt() + 1, dark)
            }
            c.fillRect(cx.toInt() - z / 2, cy.toInt(), max(1, z), max(2, 2 * z), dark)
        }
    }

    /** The village's church bell far below, its rings coming up from its tower (`bell`). */
    private fun churchBell() {
        val b = fxOn("bell")
        if (b <= 0.02f) return
        val z = z
        val cx = lx(villageX, villageD) + 3f * z; val cy = ly(villageD) - 7f * z
        val u = ((t * 1.5) % 1.0).toFloat()
        s.fx { PokeArt.rings(c, cx, cy, u, 16f * z, Col.hex(0xFFF0C0), max(1, z), 0.55f * b, 3) }
    }

    // ------------------------------------------------------------------ the tower's top

    /** The platform's floor: planks running across, from the railing to our feet; snow on them in winter. */
    private fun floor() {
        val top = hy(railD, 0f).toInt()
        for (py in max(top, c.top) until min(h, c.bottom)) {
            val d = depthAt(py)
            val band = bandPhase(d, 0.32f)
            for (px in max(0, c.left) until min(w, c.right)) {
                val x = sideAt(px, d)
                var col = if (band >= 0f && band < 0.12f) plankD else if (Noise.rnd(floor(d / 0.32f).toInt(), floor(x / 2.3f).toInt(), 861) < 0.4f) plankL else plankM
                if (winter && Noise.v2(x * 1.5f, d * 2f, 863) > 0.35f) col = if (Dither.at(px, py) < 0.3f) Pal.SNOW_M else Pal.SNOW_L
                c.set(px, py, env.lit(col))
            }
        }
    }

    /** The bench along the left side of the platform. */
    private fun bench() {
        val xa = -5.2f
        for (d in floatArrayOf(10.0f, 11.3f)) {
            val p = pm(d)
            c.fillRect(gx(xa, d).toInt(), hy(d, 0.45f).toInt(), max(2, (0.12f * p).toInt()), (gy(d) - hy(d, 0.45f)).toInt(), env.lit(logD))
        }
        c.polyBegin()
        c.polyAdd(gx(xa - 0.25f, 9.8f), hy(9.8f, 0.48f)); c.polyAdd(gx(xa + 0.35f, 9.8f), hy(9.8f, 0.48f))
        c.polyAdd(gx(xa + 0.35f, 11.5f), hy(11.5f, 0.48f)); c.polyAdd(gx(xa - 0.25f, 11.5f), hy(11.5f, 0.48f))
        c.polyFill(if (winter) Pal.SNOW_L else env.lit(logL))
        c.line(gx(xa + 0.35f, 9.8f).toInt(), hy(9.8f, 0.48f).toInt(), gx(xa + 0.35f, 11.5f).toInt(), hy(11.5f, 0.48f).toInt(), env.lit(logD))
    }

    /** The stairs coming up through their hatch in the floor at the right: the opening, the top steps going down, a rail round it. */
    private fun stairs() {
        val xa = 1.9f; val xb = 3.1f; val da = 9.1f; val db = 9.9f
        // the dark opening
        c.polyBegin()
        c.polyAdd(gx(xa, da), gy(da)); c.polyAdd(gx(xb, da), gy(da)); c.polyAdd(gx(xb, db), gy(db)); c.polyAdd(gx(xa, db), gy(db))
        c.polyFill(env.lit(Col.hex(0x2A2018)))
        // the steps going down into it, lit at their edges
        for (k in 1..3) {
            val d = da + (db - da) * (k / 4f)
            c.hline(gx(xa + 0.08f, d).toInt(), gx(xb - 0.08f, d).toInt(), (gy(d) + k * z).toInt(), env.lit(plankL))
        }
        // the rail round it: posts at its far corners and a rail along its far side and its left
        val p = pm(db)
        for (sx in floatArrayOf(xa, xb)) c.fillRect(gx(sx, db).toInt(), hy(db, 0.95f).toInt(), max(1, (0.08f * p).toInt()), (gy(db) - hy(db, 0.95f)).toInt(), env.lit(logD))
        c.line(gx(xa, db).toInt(), hy(db, 0.95f).toInt(), gx(xb, db).toInt(), hy(db, 0.95f).toInt(), env.lit(logM))
    }

    /** The railing of peeled logs across the front: posts, a top rail and a middle one; snow on the top rail in winter. */
    private fun railing() {
        val p = pm(railD)
        val top = hy(railD, railH); val mid = hy(railD, 0.6f); val foot = gy(railD)
        val th = max(2, (0.12f * p).toInt())
        c.fillRect(0, top.toInt(), w, th, env.lit(logM))
        c.hline(0, w - 1, top.toInt(), if (winter) Pal.SNOW_L else env.lit(logL))
        c.hline(0, w - 1, top.toInt() + th, env.lit(logD))
        c.fillRect(0, mid.toInt(), w, max(1, th - z), env.lit(logM))
        c.hline(0, w - 1, mid.toInt() + max(1, th - z), env.lit(logD))
        var x = -12f
        while (x < 12f) {
            val px = gx(x, railD)
            c.fillRect(px.toInt(), top.toInt(), max(2, (0.14f * p).toInt()), (foot - top).toInt(), env.lit(logM))
            c.vline(px.toInt(), top.toInt(), foot.toInt(), env.lit(logL))
            x += 1.6f
        }
    }

    /** The board on the railing's middle: the mountains drawn on it in a line, their peaks marked and named in little letters. */
    private fun board() {
        val p = pm(railD - 0.1f)
        val a0 = gx(-0.75f, railD - 0.1f); val a1 = gx(0.75f, railD - 0.1f)
        val t0 = hy(railD - 0.1f, railH + 0.62f); val t1 = hy(railD - 0.1f, railH + 0.05f)
        // its two legs on the rail
        for (sx in floatArrayOf(-0.6f, 0.55f)) c.fillRect(gx(sx, railD - 0.1f).toInt(), t1.toInt(), max(1, (0.06f * p).toInt()), (hy(railD, railH) - t1).toInt() + 1, env.lit(Pal.WOOD_D))
        for (py in max(t0.toInt(), c.top)..min(t1.toInt(), c.bottom - 1)) for (px in max(a0.toInt(), c.left)..min(a1.toInt(), c.right - 1)) {
            val u = (px + 0.5f - a0) / max(1f, a1 - a0); val v = (py + 0.5f - t0) / max(1f, t1 - t0)
            val frame = u < 0.04f || u > 0.96f || v < 0.08f || v > 0.92f
            // the mountains' line across it, the peaks' ticks and their names under them
            val line = 0.55f - 0.25f * (1f - abs(Noise.v1(u * 6f, 871) * 2f - 1f)) - 0.12f * max(0f, 1f - abs(u - 0.3f) * 10f)
            val col = when {
                frame -> Pal.WOOD_D
                abs(v - line) < 1.2f / max(1f, t1 - t0) -> Col.hex(0x2E3A5A)
                v > line && v < 0.75f -> Col.hex(0xE8EEF2)
                v in 0.78f..0.86f && (floor(u * 24f).toInt() % 3 != 2) && Noise.rnd(floor(u * 40f).toInt(), 873) < 0.7f -> Col.hex(0x4A4A50)
                else -> Col.hex(0xF4EED8)
            }
            c.set(px, py, env.lit(col))
        }
    }

    /** The telescope on its post at the right, its brass and green body, turning on a tap. */
    private fun telescope() {
        val d = railD - 0.6f; val sx = 3.0f
        val p = pm(d)
        val bx = gx(sx, d); val foot = gy(d); val headY = hy(d, 1.35f)
        c.fillRect((bx - 0.07f * p).toInt(), headY.toInt(), max(2, (0.14f * p).toInt()), (foot - headY).toInt(), env.lit(Col.hex(0x3A4A3E)))
        c.fillRect((bx - 0.22f * p).toInt(), (foot - 0.08f * p).toInt(), max(3, (0.44f * p).toInt()), max(1, (0.08f * p).toInt()), env.lit(Col.hex(0x2E3A30)))
        // the head: a body with two eyepieces toward us and its lenses toward the view, turned as a tap has it
        val pk = poked("telescope")
        val turn = if (pk == null) 0f else { val a = pk.age(t); val dur = if (pk.step == 0) 1.8f else 2.6f; if (a > dur) 0f else sin(a / dur * PI.toFloat() * (if (pk.step == 0) 1f else 2f)) * 0.35f * p }
        val hw = 0.3f * p; val hh = 0.2f * p
        c.fillRect((bx - hw + turn).toInt(), (headY - hh).toInt(), max(3, (2 * hw).toInt()), max(2, hh.toInt()), env.lit(Col.hex(0x4E7A5A)))
        c.hline((bx - hw + turn).toInt(), (bx + hw + turn).toInt(), (headY - hh).toInt(), env.lit(Col.hex(0x7AAA86)))
        for (o in floatArrayOf(-0.15f, 0.15f)) c.fillRect((bx + o * p + turn).toInt(), (headY - hh * 0.4f).toInt(), max(1, (0.08f * p).toInt()), max(1, (0.12f * p).toInt()), env.lit(Col.hex(0xC8962E)))
    }

    /** The little box on the left post with its door open: the visitors' book on its shelf, red. */
    private fun boxAndBook() {
        val d = railD - 0.05f; val sx = -3.6f
        val p = pm(d)
        val bx = gx(sx, d); val top = hy(d, railH + 0.55f); val bot = hy(d, railH + 0.05f)
        val bw = 0.42f * p
        c.fillRect(bx.toInt(), top.toInt(), bw.toInt(), (bot - top).toInt(), env.lit(Pal.WOOD_M))
        c.fillRect((bx + 0.04f * p).toInt(), (top + 0.08f * p).toInt(), (bw - 0.08f * p).toInt(), (bot - top - 0.12f * p).toInt(), env.lit(Col.hex(0x2A2018)))
        // its roof, the door open on its hinge
        c.hline((bx - 0.04f * p).toInt(), (bx + bw + 0.04f * p).toInt(), top.toInt(), if (winter) Pal.SNOW_L else env.lit(Pal.SHINGLE_D))
        c.fillRect((bx - 0.14f * p).toInt(), (top + 0.06f * p).toInt(), max(1, (0.1f * p).toInt()), (bot - top - 0.1f * p).toInt(), env.lit(Pal.WOOD_L))
        // the book lying on its shelf, its pages at the edge
        c.fillRect((bx + 0.06f * p).toInt(), (bot - 0.14f * p).toInt(), max(2, (0.22f * p).toInt()), max(1, (0.08f * p).toInt()), env.lit(Col.hex(0xB02A2A)))
        c.hline((bx + 0.06f * p).toInt(), (bx + 0.28f * p).toInt(), (bot - 0.14f * p).toInt(), env.lit(Col.hex(0xF4EED8)))
    }

    /** The stamp beside the book in the box, its wooden handle up, its pad by it. */
    private fun stamp() {
        val d = railD - 0.05f; val sx = -3.6f
        val p = pm(d)
        val bx = gx(sx, d); val bot = hy(d, railH + 0.05f)
        val sxp = bx + 0.3f * p
        c.fillRect(sxp.toInt(), (bot - 0.22f * p).toInt(), max(1, (0.06f * p).toInt()), max(2, (0.14f * p).toInt()), env.lit(Pal.WOOD_L))
        c.fillCircle(sxp + 0.03f * p, bot - 0.24f * p, max(0.8f, 0.05f * p), env.lit(Pal.WOOD_D))
        c.fillRect((sxp - 0.03f * p).toInt(), (bot - 0.08f * p).toInt(), max(1, (0.12f * p).toInt()), max(1, (0.04f * p).toInt()), env.lit(Col.hex(0x3A3A6A)))
    }

    /** The roof's eave over the view: a band at the top of the picture (the roof is over us, painted as the watch's is). */
    private val eaveRow: Int get() = oy + 9 * z

    /** The corner post on [side] (-1 the left, 1 the right), from the roof down to the floor, its brace up to the beam under the eave. */
    private fun post(side: Int) {
        val top = eaveRow
        run {
            val x = side * 5.6f
            val d = railD
            val p = pm(d)
            val px = gx(x, d)
            val pw = max(3, (0.26f * p).toInt())
            if (lakes) {
                // the window's jamb of slate stone, a whitewashed reveal on its inner side
                val jw = pw * 3
                val x0 = if (side < 0) (px - jw + pw / 2f).toInt() else (px - pw / 2f).toInt()
                for (yy in max(top, c.top)..min(gy(d).toInt(), c.bottom - 1)) for (xx in max(x0, c.left)..min(x0 + jw - 1, c.right - 1)) {
                    val inner = if (side < 0) xx > x0 + jw - 1 - pw / 2 else xx < x0 + pw / 2
                    c.set(xx, yy, env.lit(if (inner) Col.hex(0xE8E4DA) else Lakeland.slateStone((xx - ox) / (6f * z), (yy - oy) / z.toFloat(), xx, yy, z > 1, mortar = true)))
                }
                return
            }
            c.fillRect((px - pw / 2f).toInt(), top, pw, (gy(d) - top).toInt() + 1, env.lit(logM))
            c.vline((px - pw / 2f).toInt(), top, gy(d).toInt(), env.lit(if (side < 0) logD else logL))
            c.vline((px + pw / 2f).toInt() - 1, top, gy(d).toInt(), env.lit(logD))
            // the brace from the post up to the beam
            val bx = gx(x - side * 1.6f, d)
            for (o in 0 until max(2, z * 2)) c.line(px.toInt() + o - z, top + 22 * z, bx.toInt() + o - z, top + 2 * z, env.lit(logD))
        }
    }

    /** The roof overhead: its underside of boards on rafters at the top of the view, the eave's shingles and the beam under them; snow on its edge in winter. */
    private fun roof() {
        val eave = eaveRow
        if (lakes) {
            // the ceiling whitewashed on its beams, the slate eave's edge over the window
            for (py in max(0, c.top) until min(eave, c.bottom)) for (px in max(0, c.left) until min(w, c.right)) {
                val beam = ((px - ox) / max(1f, 24f * z) + 0.5f) % 1f < 0.08f
                c.set(px, py, env.lit(if (beam) Lakeland.OAK_D else if (Noise.rnd(px, py, 885) < 0.05f) Col.hex(0xDAD6CC) else Col.hex(0xEEEAE0)))
            }
            for (o in 0 until 2 * z) c.hline(0, w - 1, eave - 1 - o, if (winter && o >= z) Pal.SNOW_L else env.lit(if (o == 0) Lakeland.ROOF_D else Lakeland.ROOF_M))
            c.fillRect(0, eave, w, max(2, 2 * z), env.lit(Lakeland.OAK_D))
            c.hline(0, w - 1, eave, env.lit(Lakeland.OAK_M))
            return
        }
        for (py in max(0, c.top) until min(eave, c.bottom)) for (px in max(0, c.left) until min(w, c.right)) {
            val raft = ((px - ox) / max(1f, 18f * z) + 0.5f) % 1f < 0.1f
            val board = (((py - oy) / max(1, 3 * z)) % 2 == 0)
            c.set(px, py, env.lit(if (raft) logD else if (board) plankM else plankD))
        }
        for (o in 0 until 2 * z) c.hline(0, w - 1, eave - 1 - o, if (winter && o >= z) Pal.SNOW_L else env.lit(if (o == 0) Pal.SHINGLE_D else Pal.SHINGLE_M))
        // the beam across under the eave, on the posts
        c.fillRect(0, eave, w, max(2, 2 * z), env.lit(logD))
        c.hline(0, w - 1, eave, env.lit(logM))
    }

    /** The flag on its pole off the right post, angled up into the sky: Slovenia's (Friuli's golden eagle on blue). */
    private fun flag() {
        val p = pm(railD)
        val bx = gx(5.6f, railD); val base = hy(railD, 2.6f)
        val tx = bx - 0.5f * p; val top = eaveRow + 6f * z
        for (o in 0 until max(1, z)) c.line(bx.toInt() + o, base.toInt(), tx.toInt() + o, top.toInt(), env.lit(Pal.WOOD_D))
        // the pole's tip over the cloth with its knob: the flag in a gale never flies higher than it
        val tipX = tx - 0.045f * p; val tipY = top - 0.3f * p
        for (o in 0 until max(1, z)) c.line(tx.toInt() + o, top.toInt(), tipX.toInt() + o, tipY.toInt(), env.lit(Pal.WOOD_D))
        c.fillCircle(tipX + z * 0.5f, tipY, max(1f, 0.06f * p), env.lit(Pal.GOLD))
        val fw = 1.1f * p; val fh = 0.7f * p
        val top0 = top
        for (i in 0 until fw.toInt()) {
            val f = i / fw
            val wave = sin(t * 4.0 * gust + f * 5f).toFloat() * f * 0.08f * p
            for (j in 0 until fh.toInt()) {
                val v = j / fh
                val col = if (lakes) (if (abs(f - 0.5f) < 0.09f || abs(v - 0.5f) < 0.14f) Pal.FLAG_RED else Pal.FLAG_WHITE)
                else if (friuli) (if (abs(f - 0.45f) < 0.15f && abs(v - 0.5f) < 0.22f) Pal.GOLD else Col.hex(0x2A5AA8))
                else if (kaernten) (if (v < 0.33f || v >= 0.66f) Pal.FLAG_RED else Pal.FLAG_WHITE)
                else when { v < 0.33f -> Pal.FLAG_WHITE; v < 0.66f -> Pal.FLAG_BLUE; else -> Pal.FLAG_RED }.let { if (f < 0.32f && v in 0.15f..0.55f && (abs(v - 0.35f) < 0.12f)) Col.hex(0x1E4A9A) else it }
                c.set((tx - z - i).toInt(), (top0 + j + wave).toInt(), env.lit(col))
            }
        }
    }

    /**
     * The viewing station's coloured panes ([lakes]) across the window's top between its jambs, under the eave: four of them in
     * their oak glazing bars, green for spring, yellow for summer, orange for autumn and blue for winter, the view tinted
     * through them; lit from behind by the sky.
     */
    private fun panes() {
        val z = z
        val a = gx(-5.6f, railD).toInt(); val b = gx(5.6f, railD).toInt()
        val y0 = eaveRow + 2 * z; val y1 = eaveRow + 15 * z
        val tints = intArrayOf(Col.hex(0x5CC840), Col.hex(0xF8D820), Col.hex(0xFF7A10), Col.hex(0x3A80E8))
        val bar = env.lit(Lakeland.OAK_M)
        for (py in max(y0, c.top) until min(y1, c.bottom)) for (px in max(a, c.left) until min(b, c.right)) {
            val f = (px - a).toFloat() / max(1, b - a)
            val i = (f * 4f).toInt().coerceIn(0, 3)
            val edge = (f * 4f) - i
            if (edge < 0.03f || edge > 0.97f || py < y0 + z || py >= y1 - z) { c.set(px, py, bar); continue }
            // a thin bar across each pane's middle, closer up
            if (z > 1 && abs(py - (y0 + y1) / 2) < max(1, z / 2)) { c.set(px, py, bar); continue }
            c.blend(px, py, tints[i], 0.75f)
        }
    }

    companion object {
        /** The scene column of the pike's middle ([lakes]). */
        private const val PIKE_U = 66f

        /** The eye's height over the land far below (m), the tower on its hill. */
        private const val LAND_EYE = 130f

        /** The scene column of Triglav's middle head. */
        private const val TRIGLAV_U = 74f

        /** Carinthia's view: the scene columns of the Mittagskogel's top and of the Dobratsch's mast. */
        private const val MITTAGSKOGEL_U = 182f
        private const val DOBRATSCH_MAST_U = 52f
    }
}
