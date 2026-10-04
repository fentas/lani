package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.Season
import si.lanisce.lani.game.scene.Poke
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Na stolpu: the view from the village's watchtower, framed by its corner posts, the underside of its shingle roof and
 * the railing of its platform. Below, the village: roofs of tiles and shingles, smoke from the chimneys, the linden on the
 * little square, paths running to the gate; round it the palisade of pointed stakes, the gate with its roof and two
 * torches, the road running out over the fields of the plateau to where the land drops away. On the left the forest
 * climbs the hills; on the right the plateau ends and far below lies the valley with its fields and the river, and across
 * it wooded hills, a big mountain and the far ranges at the horizon.
 *
 * On the tower: the horn on its peg on the left post, the torch in its iron bracket on the right one, the flag on a pole,
 * the spyglass lying on the railing, a crow perched on it. At night the windows and the gate's torches are lit, and the
 * watch's torch burns from dusk. A dialog can light or put out the torch ("torch"), blow the horn ("horn"), send a flock up
 * off the forest ("birds") and show the wolves ("wolves": their eyes in the dark forest at night, grey shapes along its
 * edge by day); when the village's wolves event is on, their eyes shine in the forest at night by themselves. Fog lies in
 * the valley like a lake; rain veils the far hills first, lightning strikes over the mountains. Taps: the horn swings and
 * sounds, then longer; the crow caws, then flies off round the village and comes back.
 *
 * Closer up ([detail] 2 and 3) the same view is drawn finer: the stakes of the palisade one by one, windows, doors and
 * tiles, the trees' crowns, the grain of the posts and the boards, the brass of the spyglass.
 */
internal class WatchtowerPainter : VistaPainter() {
    override val art = "watchtower"
    override val horizon = 48

    /** The watch looks north-north-east over the land: the Plough, the Pole Star and Cassiopeia at night. */
    override val skyFacing = 20.0
    override val pokes = listOf(Poke("horn", listOf(2.4, 3.4), rest = 3.0), Poke("crow", listOf(1.8, 6.2), rest = 3.0))

    /** Picture pixels per scene canvas pixel. */
    private val z: Int get() = detail

    /** A taller view keeps the roof at its top and gives the rest to the sky and the platform in equal shares. */
    override fun stageTop(height: Int): Int = (height - STAGE_H) / 2

    // ------------------------------------------------------------------ the land below, seen from the platform
    // The platform is the vista's ground (people on it 10-11 m away, as everywhere); the village's ground lies [DROP] m
    // below it, so the eye is [LAND_EYE] m above the land: a point [d] m away on it lies F × LAND_EYE / d rows under the
    // horizon. The valley lies [VALLEY_DROP] m lower still, beyond the plateau's edge.

    private fun ly(d: Float): Float = hz + F * LAND_EYE / d
    private fun lyh(d: Float, hgt: Float): Float = hz + F * (LAND_EYE - hgt) / d
    private fun lx(x: Float, d: Float): Float = vx + F * x / d
    private fun lpm(d: Float): Float = F / d
    private fun ldepth(py: Int): Float = F * LAND_EYE / max(0.35f, py + 0.5f - hz)
    private fun lside(px: Int, d: Float): Float = (px + 0.5f - vx) * d / F

    /** The scene canvas's column of picture column [x]. */
    private fun ux(x: Int): Float = (x + 0.5f - ox) / z

    /** Inside the palisade: its ring round the village, in metres (<1 inside). */
    private fun ring(x: Float, d: Float): Float { val a = x / RING_RX; val b = (d - RING_D) / RING_RD; return a * a + b * b }

    /** Where the road runs, from the gate out to the plateau's edge (side at depth [d]). */
    private fun roadX(d: Float): Float = GATE_X + (d - GATE_D) * 0.38f + 3.5f * sin((d - GATE_D) * 0.045f)

    /** The plateau's edge (m) at scene column [u]: the land drops to the valley beyond it. */
    private fun edgeD(u: Float): Float = 262f + 34f * Noise.v1(u * 0.03f, 911) + 0.2f * max(0f, u - 200f)

    // ------------------------------------------------------------------ per frame, per column
    private var edgeRow = IntArray(0)
    private var sunLeft = true
    private var lit = false
    /** Object ids from here on are the tower's and the people's: no weather under the roof. */
    private var towerFrom = Int.MAX_VALUE

    /** The platform: the railing stands [RAIL_D] m away (its foot's row), the floor runs from it to us. */
    private val railFoot: Float get() = gy(RAIL_D)
    private val railTop: Float get() = hy(RAIL_D, 1.1f)

    private val items = ArrayList<Pair<Float, () -> Unit>>()

    override fun paint() {
        setup()
        val z = z
        sunLeft = (sunAt()?.first ?: vx) < vx
        lit = env.windows > 0.35f
        towerFrom = Int.MAX_VALUE
        if (edgeRow.size != w) edgeRow = IntArray(w)
        for (x in 0 until w) edgeRow[x] = ly(edgeD(ux(x))).toInt()

        vistaSky(3, 41, moonX = ox + 60 * z, moonY = oy + 24 * z)
        // far away: the ranges at the horizon, the big mountain, the hills across the valley, the valley and its river
        thing("horizon", outline = 0) { farRange() }
        thing("mountains", outline = 0) { massif() }
        prop(0) { acrossHills() }
        // the valley's floor behind the forest is scenery; what shows of it to the right is the valley
        prop(0) { valley(-1000f, VALLEY_U) }
        thing("valley", outline = 0) { valley(VALLEY_U, 1000f) }
        thing("river", outline = 0) { river() }
        prop(0) { flock(fxOn("birds")) }
        // the plateau: fields and meadows, inside the palisade the village's ground
        plateau()
        prop(0) { edgeRim() }
        thing("road", outline = 0) { road() }
        thing("forest", outline = Pal.OUTLINE_TREE) { forest() }
        wolves()

        items.clear()
        items.add(RING_D + RING_RD to { thing("palisade") { palisade() } })
        items.add(GATE_D + 0.1f to { thing("gate") { gate() } })
        for ((i, hs) in HOUSES.withIndex()) items.add(hs.d + hs.dep / 2f to { house(hs, i) })
        for ((i, tr) in TREES.withIndex()) items.add(tr[1] to { prop(Pal.OUTLINE_TREE) { tree(tr[0], tr[1], tr[2], i, linden = i == 0) } })
        items.sortByDescending { it.first }
        for ((_, draw) in items) draw()

        // the tower: its roof over us, the corner posts, the railing and the platform, what hangs and lies on them
        towerFrom = s.newObject(Pal.OUTLINE); c.penId = 0
        prop { roofUnderside() }
        prop { post(w - 9 * z, false) }
        thing("tower") { post(0, true) }
        thing("flag", slop = 1) { flag() }
        thing("horn", slop = 1) { horn() }
        thing("torch", slop = 1) { torch() }
        prop { platform() }
        onThePlatform()
        thing("railing") { railing() }
        for (p in peopleAt("watch")) person(p, gx(-4.3f, 10.5f).toInt(), gy(10.5f).toInt(), flip = false)
        for (p in peopleAt("rail")) person(p, gx(2.3f, 10.7f).toInt(), gy(10.7f).toInt(), flip = true)
        for (p in peopleAt("hatch")) person(p, gx(5.1f, 10.1f).toInt(), gy(10.1f).toInt(), flip = true)
        thing("spyglass", slop = 1) { spyglass() }
        thing("crow", slop = 2) { crow() }
        hornCall()
    }

    // ------------------------------------------------------------------ far away

    /**
     * The far ranges at the horizon (obzorje): pale blue ridged peaks across the left of the view, sinking behind the big
     * mountain on the right; lit on the sun's side, snow on their tops but in summer.
     */
    private fun farRange() {
        val z = z
        val snowy = env.month !in 6..9
        val body = if (env.season == Season.WINTER) Col.hex(0xB6C2DA) else Col.hex(0x8A98BE)
        val shade = Col.scale(body, 0.84f)
        c.penEmissive = true
        for (x in max(0, c.left) until min(w, c.right)) {
            val u = ux(x)
            val fade = ((178f - u) / 44f).coerceIn(0f, 1f)
            if (fade <= 0f) continue
            fun top(uu: Float): Float {
                val a = 1f - abs(Noise.v1(uu * 0.012f * 1f, 921) * 2f - 1f)
                val b = 1f - abs(Noise.v1(uu * 0.035f, 922) * 2f - 1f)
                return (a * a * 0.75f + b * 0.25f) * 17f
            }
            val th = top(u) * fade
            if (th < 1f) continue
            val lit = (top(u + 1f) - top(u - 1f)).let { if (sunLeft) it < 0f else it > 0f }
            for (y in max((hz + z - th * z).toInt(), c.top) until min(hz + z, c.bottom)) {
                val up = (hz + z - (y + 0.5f)) / z
                var col = if (lit) body else shade
                if (snowy && up > th * 0.62f + (Noise.rnd(x / z, 923) - 0.5f) * 1.5f) col = if (lit) Pal.SNOW_L else Pal.SNOW_D
                c.set(x, y, env.lit(Col.mix(col, env.skyHorizon, 0.5f)))
            }
        }
        c.penEmissive = false
    }

    /** How far down (a share of its height) the snow reaches on the big mountain this month. */
    private fun snowLine(): Float = when (env.month) { 12, 1, 2 -> 0.05f; 3 -> 0.25f; 4 -> 0.45f; 5 -> 0.7f; 6, 7, 8, 9 -> 2f; 10 -> 0.72f; else -> 0.4f }

    /**
     * The big mountain across the valley: grey limestone faces either side of its crest, lit on the sun's side, gullies
     * down them, forest up its foot; snow down to the month's line; pink in the low sun of dawn and dusk.
     */
    private fun massif() {
        val z = z
        val glow = if (env.dark < 0.6f && frame.sky.gloom < 0.5f) (1f - abs(env.sun - 0.07f) / 0.2f).coerceIn(0f, 1f) else 0f
        val line = snowLine()
        c.penEmissive = true
        for (x in max(0, c.left) until min(w, c.right)) {
            val u = ux(x)
            val env0 = (1f - abs(u - 168f) / 92f).coerceIn(0f, 1f)
            if (env0 <= 0f) continue
            val a = 1f - abs(Noise.v1(u * 0.045f, 931) * 2f - 1f)
            val b = 1f - abs(Noise.v1(u * 0.11f, 932) * 2f - 1f)
            val top = (30f * env0.toDouble().pow(1.2).toFloat() * (0.62f + 0.38f * (a * 0.7f + b * 0.3f))).coerceAtLeast(0f)
            if (top < 1.5f) continue
            val crest = hz - top * z
            val slope = run { val ul = ux(x - z); val ur = ux(x + z); (1f - abs(ul - 168f) / 92f) - (1f - abs(ur - 168f) / 92f) }
            val litSide = if (sunLeft) slope < 0f || u < 168f else slope > 0f || u > 168f
            for (y in max(crest.toInt(), c.top) until min(hz + z, c.bottom)) {
                val up = (hz - (y + 0.5f)) / z
                val rel = up / 30f
                val gully = Noise.v2(u * 0.35f, up * 0.12f, 933) > 0.66f
                val snowy = rel > line + (Noise.v2(u * 0.2f, up * 0.3f, 934) - 0.5f) * 0.25f || (gully && rel > line - 0.2f)
                val forest = !snowy && up < 7f + 3f * Noise.v1(u * 0.2f, 935)
                var col = when {
                    snowy -> if (litSide) Col.hex(0xF4F7FF) else Col.hex(0xB8C4DE)
                    forest -> if (Noise.rnd(x / z, y / z, 936) < 0.5f) Col.hex(0x2E4E3C) else Col.hex(0x3A5E46)
                    gully -> Col.hex(0x6C6A7E)
                    litSide -> if (Noise.v2(u * 0.6f, up * 0.25f, 937) > 0.7f) Col.hex(0xB6B2BC) else Col.hex(0xC8C4CA)
                    else -> Col.hex(0x86849A)
                }
                if (glow > 0f && !forest && litSide) col = Col.mix(col, if (snowy) Col.hex(0xFFC0A8) else Col.hex(0xF09A7E), 0.45f * glow)
                c.set(x, y, env.lit(Col.mix(col, env.skyHorizon, 0.2f + 0.2f * (1f - rel).coerceIn(0f, 1f))))
            }
        }
        c.penEmissive = false
    }

    /** The wooded hills across the valley, a line of treetops along their crest, vineyards on their sunny foot. */
    private fun acrossHills() {
        val z = z
        val col = when (env.season) { Season.WINTER -> Col.hex(0x9AA6B8); Season.AUTUMN -> Col.hex(0x7E6E3E); else -> Col.hex(0x4A7446) }
        val crest = ridge(hz + 2f * z, 13f * z, 0.017f, 941, col, 0.32f, bottom = hz + 2 * z)
        treeline(crest, 5 * z, 1.8f * z, if (env.season == Season.WINTER) Col.hex(0x5E6A78) else Col.hex(0x2C5234), 0.3f, 943)
    }

    /**
     * The valley floor far below, beyond the plateau's edge: a patchwork of fields in the haze, hedges, a village or two of
     * white walls and red roofs, the fog lying in it on a misty morning (see [weather]); snow in winter.
     */
    private fun valley(u0: Float, u1: Float) {
        val z = z
        val maxRow = edgeRow.max()
        c.penEmissive = true
        for (py in max(hz + z, c.top) until min(maxRow, c.bottom)) {
            val dv = F * (LAND_EYE + VALLEY_DROP) / (py + 0.5f - hz)
            val haze = (0.35f + dv / 30000f).coerceAtMost(0.75f)
            for (px in max(max(0, (ox + u0 * z).toInt()), c.left) until min(min(w, (ox + u1 * z).toInt()), c.right)) {
                if (py >= edgeRow[px]) continue
                val xv = (px + 0.5f - vx) * dv / F
                // fields: cells about 250 m across, each its own crop
                val cx = floor(xv / 260f + Noise.v1(dv / 900f, 951) * 0.6f).toInt(); val cd = floor(dv / 420f).toInt()
                val kind = (Noise.rnd(cx, cd, 952) * 5f).toInt()
                var col = when (env.season) {
                    Season.WINTER -> if (kind == 2) Col.hex(0xD8E0EA) else Pal.SNOW_L
                    Season.SPRING -> when (kind) { 0 -> Col.hex(0x8CC45A); 1 -> Col.hex(0x6AA84A); 2 -> Col.hex(0xB8D06A); 3 -> Col.hex(0x9A7A52); else -> Col.hex(0x7AB658) }
                    Season.SUMMER -> when (kind) { 0 -> Col.hex(0xD8C060); 1 -> Col.hex(0x5E9A42); 2 -> Col.hex(0x8AB450); 3 -> Col.hex(0xC8A858); else -> Col.hex(0x6CA44C) }
                    Season.AUTUMN -> when (kind) { 0 -> Col.hex(0x9A7A4E); 1 -> Col.hex(0x7E8E48); 2 -> Col.hex(0xB08A4A); 3 -> Col.hex(0x8A6A44); else -> Col.hex(0x6E8A46) }
                }
                // hedges between the fields, darker
                val fx = xv / 260f + Noise.v1(dv / 900f, 951) * 0.6f
                if (fx - floor(fx) < 0.06f && env.season != Season.WINTER) col = Col.scale(col, 0.72f)
                // a village now and then: white walls, red roofs
                if (Noise.v2(xv / 700f, dv / 900f, 953) > 0.8f && Noise.rnd(px / z, py / z, 954) < 0.45f) col = if (Noise.rnd(px / z, py / z, 955) < 0.5f) Col.hex(0xF2EEE4) else Col.hex(0xB65A3E)
                if (Dither.at(px, py) < 0.18f) col = Col.scale(col, 0.93f)
                c.set(px, py, env.lit(Col.mix(col, env.skyHorizon, haze)))
            }
        }
        c.penEmissive = false
    }

    /** The river winding down the valley: grey-blue, a glint of the sky on it; frozen along its banks in winter. */
    private fun river() {
        val z = z
        val maxRow = edgeRow.max()
        c.penEmissive = true
        for (py in max(hz + z, c.top) until min(maxRow, c.bottom)) {
            val dv = F * (LAND_EYE + VALLEY_DROP) / (py + 0.5f - hz)
            val center = -200f + 900f * sin(dv / 2400f + 0.3f) + dv * 0.15f
            val half = max(28f, 0.75f * dv / F)
            val haze = (0.3f + dv / 30000f).coerceAtMost(0.7f)
            val x0 = (vx + F * (center - half) / dv).toInt(); val x1 = (vx + F * (center + half) / dv).toInt()
            for (px in max(max(0, x0), c.left)..min(min(w - 1, x1), c.right - 1)) {
                if (py >= edgeRow[px]) continue
                val glint = Noise.rnd(px / z, (py / z + (t * 1.5).toInt()), 961) > 0.8f
                var col = if (glint) Col.mix(skyAt(0.15f), Col.hex(0xFFFFFF), 0.3f) else Col.hex(0x5E86A8)
                if (env.snow) col = if (glint) Pal.ICE_L else Pal.ICE_M
                c.set(px, py, Col.mix(env.lit(col), env.skyHorizon, haze))
            }
        }
        c.penEmissive = false
    }

    // ------------------------------------------------------------------ the plateau

    /** Everything below the plateau's edge: the fields outside the palisade, the village's ground inside it (plain ground). */
    private fun plateau() {
        c.penId = 0
        val minRow = edgeRow.min()
        for (py in max(minRow, c.top) until min(h, c.bottom)) {
            val d = ldepth(py)
            for (px in max(0, c.left) until min(w, c.right)) {
                if (py < edgeRow[px]) continue
                val x = lside(px, d)
                c.set(px, py, if (ring(x, d) < 1f) villageGround(x, d, px, py) else fields(x, d, px, py))
            }
        }
    }

    /** The fields and meadows of the plateau outside the palisade: strips of crops and grass, hedges, the season's colours. */
    private fun fields(x: Float, d: Float, px: Int, py: Int): Int {
        val g = env.grass
        // strips running away from the village, a few across
        val sx = (x + 0.12f * d) / 11f
        val strip = floor(sx).toInt(); val band = floor((d - 60f) / 38f).toInt()
        val kind = (Noise.rnd(strip, band, 971) * 6f).toInt()
        val edge = sx - strip < 0.07f
        var col = if (env.snow) {
            if (edge && Dither.at(px, py) < 0.5f) Pal.SNOW_D else if (Noise.rnd(px, py, 972) < 0.15f) Pal.SNOW_M else Pal.SNOW_L
        } else when (kind) {
            0, 1 -> { val n = Noise.v2(x * 0.3f, d * 0.2f, 973) + (Dither.at(px, py) - 0.5f) * 0.3f; if (n > 0.6f) g[0] else if (n > 0.3f) g[1] else g[2] }
            2 -> when (env.season) { Season.SPRING -> Col.hex(0x8AC458); Season.SUMMER -> Pal.HAY_M; Season.AUTUMN -> Pal.SOIL_M; else -> Pal.SOIL_M }
            3 -> when (env.season) { Season.SPRING -> Pal.SOIL_L; Season.SUMMER -> Col.hex(0x6E9E42); Season.AUTUMN -> Col.hex(0xA88A4A); else -> Pal.SOIL_L }
            4 -> if (env.season == Season.SUMMER) Pal.HAY_L else Col.hex(0x96B85A)
            else -> Col.mix(g[1], Pal.SOIL_M, 0.35f)
        }
        if (!env.snow && edge) col = Col.scale(g[2], 0.85f)
        // furrows closer up
        if (!env.snow && (kind == 2 || kind == 3) && z > 1 && ((x + 0.12f * d) * 2f).let { it - floor(it) } < 0.3f) col = Col.scale(col, 0.88f)
        return hazy(col, d * 0.6f)
    }

    /** The village's ground inside the palisade: grass, trampled earth by the houses, the paths to the gate and across. */
    private fun villageGround(x: Float, d: Float, px: Int, py: Int): Int {
        val g = env.grass
        val n = Noise.v2(x * 0.25f, d * 0.25f, 981) + (Dither.at(px, py) - 0.5f) * 0.25f
        var col = if (n > 0.62f) g[0] else if (n > 0.3f) g[1] else g[2]
        // the paths: from the gate to the square, across the village, down to us
        val toGate = abs(x - GATE_X * (d - SQUARE_D) / (GATE_D - SQUARE_D))
        val street = abs(d - (SQUARE_D + x * 0.08f + 2f * sin(x * 0.12f)))
        val down = abs(x - 1.5f - (d - SQUARE_D) * 0.05f)
        val sq = sqrt(x * x + ((d - SQUARE_D) * 1.1f).let { it * it })
        val pathW = 1.5f + (Noise.v1((x + d) * 0.3f, 982) - 0.5f) * 0.6f
        val onPath = (toGate < pathW && d > SQUARE_D) || street < pathW || (down < pathW && d < SQUARE_D) || sq < 7f
        if (onPath) {
            val r = Noise.rnd(px, py, 983)
            col = if (env.snow) (if (r < 0.3f) Pal.SNOW_D else Pal.SNOW_M) else if (r < 0.2f) Pal.DIRT_D else if (r < 0.85f) Pal.DIRT_M else Pal.DIRT_L
        } else if (env.snow) col = if (Noise.rnd(px, py, 984) < 0.12f) Pal.SNOW_M else Pal.SNOW_L
        return hazy(col, d * 0.4f)
    }

    /** Scrub along the plateau's edge where the land drops to the valley: a dark rim of bushes, bare and snowy in winter. */
    private fun edgeRim() {
        val z = z
        for (x in max(0, c.left) until min(w, c.right)) {
            val u = ux(x)
            val bump = (0.6f + 1.8f * Noise.v1(u * 0.35f, 1013) + (if (Noise.rnd(x / z, 1014) < 0.08f) 1.5f else 0f)) * z
            val e = edgeRow[x]
            for (y in max((e - bump).toInt(), c.top) until min(e + z, c.bottom)) {
                val top = y < e - bump + z
                val col = when {
                    env.snow -> if (top) Pal.SNOW_L else Col.hex(0x7A7068)
                    env.season == Season.AUTUMN -> if (top) Col.hex(0xB0782E) else Col.hex(0x6A4A26)
                    else -> if (top) Col.hex(0x4E7A3A) else Col.hex(0x2A4A2A)
                }
                c.set(x, y, hazy(col, edgeD(u) * 0.6f))
            }
        }
    }

    /** The road from the gate out over the plateau to its edge, where it drops to the valley. */
    private fun road() {
        val minRow = edgeRow.min()
        val top = ly(GATE_D).toInt()
        for (py in max(minRow, c.top) until min(top + 1, c.bottom)) {
            val d = ldepth(py)
            if (d < GATE_D) continue
            val cx = roadX(d)
            val half = max(2.2f, 0.7f * d / F)
            val x0 = lx(cx - half, d).toInt(); val x1 = lx(cx + half, d).toInt()
            for (px in max(max(0, x0), c.left)..min(min(w - 1, x1), c.right - 1)) {
                if (py < edgeRow[px]) continue
                val r = Noise.rnd(px, py, 991)
                val col = if (env.snow) (if (r < 0.4f) Pal.SNOW_D else Pal.SNOW_M) else if (r < 0.25f) Pal.DIRT_D else Pal.DIRT_L
                c.set(px, py, hazy(col, d * 0.6f))
            }
        }
    }

    // ------------------------------------------------------------------ the forest

    /**
     * The forest on the left, climbing the hills: tiers of beeches and spruces, far to near, each crown lit on the sun's
     * side; beeches gold and red in autumn and bare in winter, snow on the spruces; a few trees at the plateau's edge on the
     * right.
     */
    private fun forest() {
        for (tier in 5 downTo 0) {
            val dT = FOREST_D + tier * 24f
            val rise = tier * 3.2f
            val p = lpm(dT)
            // the forest's edge on the right steps back tier by tier, curving away
            val end = (ox + (114f - tier * 5f + 8f * Noise.rnd(tier, 1009)) * z).toInt()
            val xa = lside(-8 * z, dT); val xb = lside(end, dT)
            var i = floor(xa / 5.2f).toInt()
            while (i * 5.2f < xb) {
                val x = i * 5.2f + (Noise.rnd(i, tier, 1001) - 0.5f) * 3f
                val dd = dT + (Noise.rnd(i, tier, 1002) - 0.5f) * 8f
                val hgt = 15f + Noise.rnd(i, tier, 1003) * 6f
                val hill = 9f * Noise.v1(x * 0.022f, 1008) * tier / 5f
                if (lx(x, dd) < end) {
                    if (tier == 0) shadow(lx(x, dd), ly(dd), max(1.5f, 3.4f * p), max(1f, 0.9f * p), 0.62f)
                    crown(x, dd, rise + hill + (dd - dT) * 0.13f, hgt, Noise.rnd(i, tier, 1004) < 0.55f, i * 7 + tier, low = tier == 0)
                }
                i++
            }
            if (tier == 0) for (j in 0 until 6) {
                // a few at the plateau's edge on the right
                val xx = 72f + j * 11f + Noise.rnd(j, 1005) * 5f; val dd = 244f + Noise.rnd(j, 1006) * 14f
                shadow(lx(xx, dd), ly(dd), max(1.5f, 2.6f * lpm(dd)), max(1f, 0.8f * lpm(dd)), 0.66f)
                crown(xx, dd, 0f, 13f + Noise.rnd(j, 1007) * 5f, j % 3 != 1, 300 + j, low = true)
            }
        }
    }

    /** One tree seen from afar at side [x], depth [d] on ground [ground] m up, [hgt] m tall: a spruce's cone or a beech's crown. */
    private fun crown(x: Float, d: Float, ground: Float, hgt: Float, spruce: Boolean, seed: Int, low: Boolean = false) {
        val p = lpm(d)
        val bx = lx(x, d)
        val base = lyh(d, ground)
        val top = lyh(d, ground + hgt)
        val season = env.season
        val sunL = sunLeft
        if (spruce) {
            val dark = Col.hex(0x1C3E2A); val mid = Col.hex(0x2A5A38); val light = Col.hex(0x3E7648)
            val half = hgt * 0.2f * p
            val y0 = top; val y1 = lyh(d, ground + if (low) 0.5f else 2f)
            for (y in max(y0.toInt(), c.top) until min(y1.toInt() + 1, c.bottom)) {
                val v = (y + 0.5f - y0) / max(1f, y1 - y0)
                val ww = half * v + 0.5f
                for (xx in max((bx - ww).toInt(), c.left)..min((bx + ww).toInt(), c.right - 1)) {
                    val u = (xx + 0.5f - bx) / max(1f, ww)
                    val tierLine = ((v * 5f) - floor(v * 5f)) > 0.8f
                    val l = (if (sunL) -u else u) * 0.7f - (if (tierLine) 0.5f else 0f)
                    var col = if (l > 0.3f) light else if (l > -0.25f) mid else dark
                    if (env.snow && (tierLine || Noise.rnd(xx, y, seed) < 0.25f)) col = if (l > -0.2f) Pal.SNOW_L else Pal.SNOW_M
                    c.set(xx, y, col)
                }
            }
            return
        }
        val (dk, md, lt) = when (season) {
            Season.AUTUMN -> if (seed % 3 == 0) Triple(Col.hex(0x8A3A1A), Col.hex(0xB8582A), Col.hex(0xE0923A)) else Triple(Col.hex(0x9A6A1E), Col.hex(0xC8902E), Col.hex(0xE8C050))
            Season.SPRING -> Triple(Col.hex(0x4A8A34), Col.hex(0x6EAE48), Col.hex(0xA2D46A))
            Season.WINTER -> Triple(Col.hex(0x5A4E46), Col.hex(0x746658), Col.hex(0x928474))
            else -> Triple(Col.hex(0x2A5A2E), Col.hex(0x3E7A3A), Col.hex(0x62A04A))
        }
        val cy = top + (base - top) * (if (low) 0.47f else 0.38f)
        val rx = hgt * 0.24f * p + 0.5f; val ry = (base - top) * (if (low) 0.5f else 0.42f)
        for (y in max((cy - ry).toInt(), c.top)..min((cy + ry).toInt(), c.bottom - 1)) for (xx in max((bx - rx).toInt(), c.left)..min((bx + rx).toInt(), c.right - 1)) {
            val dx = (xx + 0.5f - bx) / rx; val dy = (y + 0.5f - cy) / ry
            val q = dx * dx + dy * dy + (Noise.v2(xx * 0.5f / z, y * 0.5f / z, seed) - 0.5f) * 0.35f
            if (q > 1f) continue
            if (season == Season.WINTER && Noise.rnd(xx / z, y / z, seed + 1) < 0.35f) continue
            val l = (if (sunL) -dx else dx) * 0.55f - dy * 0.7f + (Noise.rnd(xx / z, y / z, seed + 2) - 0.5f) * 0.4f
            var col = if (l > 0.35f) lt else if (l > -0.2f) md else dk
            if (env.snow && dy < -0.3f && Noise.rnd(xx, y, seed + 3) < 0.4f) col = Pal.SNOW_L
            c.set(xx, y, col)
        }
    }

    /**
     * Wolves ([fx] "wolves", or the village's wolves event at night): pairs of eyes glowing at the edge of the dark forest,
     * blinking, drifting; by day grey shapes slinking along its edge.
     */
    private fun wolves() {
        val lvl = max(fxOn("wolves"), if (frame.world.wolves && env.dark > 0.4f) 1f else 0f)
        if (lvl <= 0.02f) return
        val z = z
        val n = (lvl * 5f + 0.99f).toInt()
        val night = env.dark > 0.4f
        if (night) s.fx {
            c.penEmissive = true
            for (j in 0 until n) {
                val per = 3.2 + Noise.rnd(j, 1021) * 2.0
                val ph = ((t + Noise.rnd(j, 1022) * per) % per)
                if (ph < 0.18) continue // a blink
                val u = 12f + j * 16f + Noise.rnd(j, 1023) * 8f + sin(t * 0.2 + j).toFloat() * 3f
                val x = (ox + u * z).toInt()
                val y = (ly(FOREST_D + 2f) - (4f + Noise.rnd(j, 1024) * 5f) * z).toInt()
                val col = if (j % 2 == 0) Col.hex(0xE8F070) else Col.hex(0xC8F088)
                c.fillRect(x, y, z, z, col); c.fillRect(x + 2 * z, y, z, z, col)
                c.blend(x - z, y, col, 0.25f); c.blend(x + 3 * z, y, col, 0.25f)
            }
            c.penEmissive = false
        } else prop(0) {
            // by day: grey wolves slinking out of the forest over the fields, one behind the other, nose down
            val grey = Col.hex(0x8A847C); val greyD = Col.hex(0x5E5852); val pale = Col.hex(0xC8C0B4)
            for (j in 0 until n) {
                val d = FOREST_D - 30f - j * 9f
                val x = -40f + j * 7f + ((t * 0.7 + j * 2.3) % 20.0).toFloat()
                val bx = lx(x, d).toInt(); val by = ly(d).toInt()
                val step = ((t * 5).toInt() + j) and 1
                sprite(bx, by) {
                    // tail, body, the pale belly, the head held low, ears; legs trotting
                    c.fillRect(bx - 6, by - 4, 2, 1, grey); c.set(bx - 7, by - 3, greyD)
                    c.fillRect(bx - 4, by - 5, 7, 3, grey); c.hline(bx - 4, bx + 2, by - 5, greyD)
                    c.hline(bx - 3, bx + 1, by - 3, pale)
                    c.fillRect(bx + 3, by - 4, 3, 2, grey); c.set(bx + 6, by - 3, greyD); c.set(bx + 3, by - 5, greyD)
                    c.set(bx - 3 + step, by - 2, greyD); c.set(bx - 3 + step, by - 1, greyD)
                    c.set(bx + 1 - step, by - 2, greyD); c.set(bx + 1 - step, by - 1, greyD)
                }
            }
        }
    }

    /** A flock ([fx] "birds": how far it has flown) rising off the forest, wheeling over the valley and away to the right. */
    private fun flock(level: Float) {
        if (level <= 0.01f || level >= 0.995f) return
        val z = z
        val col = env.lit(Col.hex(0x2A2628))
        val e = level * level * (3f - 2f * level)
        val cx = 50f + 220f * e + sin(e * 8f) * 14f
        val cy = (horizon - 8f) - 20f * sin(e * 3.14f) + cos(e * 8f) * 3f
        for (j in 0 until 14) {
            val a = Noise.rnd(j, 1031) * 6.28f + t.toFloat() * (0.8f + Noise.rnd(j, 1032))
            val r = (3f + 9f * sin(e * 3.14f)) * sqrt(Noise.rnd(j, 1033))
            val x = ox + ((cx + cos(a) * r * 1.6f) * z).toInt(); val y = oy + ((cy + sin(a) * r * 0.6f) * z).toInt()
            val up = ((t * 9).toInt() + j) and 1
            sprite(x, y) { PokeArt.wings(c, x, y, up == 0, 1, col) }
        }
    }

    // ------------------------------------------------------------------ the palisade and the gate

    /**
     * The palisade round the village: pointed stakes of split logs, one beside the other along its ring, their inner face
     * toward us, lit on the sun's side, a walkway's rail along the top closer up; snow capping them in winter.
     */
    private fun palisade() {
        val z = z
        val step = 0.42f
        val arc = 2.3f
        var th = -arc / 2f
        var k = 0
        val bark = intArrayOf(Pal.LOG_L, Pal.LOG_M, Col.hex(0x7A5230), Pal.LOG_D)
        while (th < arc / 2f) {
            val x = RING_RX * sin(th); val d = RING_D + RING_RD * cos(th)
            val gap = abs(x - GATE_X) < 2.6f && d > RING_D
            if (!gap) {
                val p = lpm(d)
                val bx = lx(x, d)
                if (bx > c.left - 4 * z && bx < c.right + 4 * z) {
                    val sw = max(1f, 0.36f * p)
                    val hgt = 4.2f + 0.3f * Noise.rnd(k, 1041)
                    val foot = ly(d); val tip = lyh(d, hgt)
                    val point = max(1f, 0.3f * p)
                    val tone = bark[(Noise.rnd(k, 1042) * 4f).toInt()]
                    for (y in max(tip.toInt(), c.top)..min(foot.toInt(), c.bottom - 1)) {
                        val v = (y + 0.5f - tip) / point
                        val ww = if (v < 1f) sw * v * 0.5f else sw * 0.5f
                        for (xx in max((bx - ww).toInt(), c.left)..min((bx + ww - 0.01f).toInt(), c.right - 1)) {
                            val u = (xx + 0.5f - bx) / max(0.5f, ww)
                            var col = if ((u < 0f) == sunLeft) Col.mix(tone, Col.hex(0xFFF0C8), 0.18f) else Col.scale(tone, 0.8f)
                            if (env.snow && v < 1.6f) col = Pal.SNOW_L
                            if (z > 1 && Noise.rnd(xx / 2, y, k) < 0.12f) col = Col.scale(col, 0.85f)
                            c.set(xx, y, col)
                        }
                    }
                }
            }
            th += step / max(1f, sqrt((RING_RX * cos(th)).let { it * it } + (RING_RD * sin(th)).let { it * it }))
            k++
        }
        // closer up: the rail of the walkway behind the stakes' tops
        if (z > 1) {
            var th2 = -arc / 2f
            var px0 = -1f; var py0 = -1f
            while (th2 <= arc / 2f) {
                val x = RING_RX * sin(th2); val d = RING_D + RING_RD * cos(th2)
                val nx = lx(x, d); val ny = lyh(d, 2.4f)
                if (px0 >= 0f && !(abs(x - GATE_X) < 2.6f && d > RING_D)) c.line(px0.toInt(), py0.toInt(), nx.toInt(), ny.toInt(), Col.scale(Pal.LOG_D, 0.8f))
                px0 = nx; py0 = ny
                th2 += 0.02f
            }
        }
    }

    /** The gate: two tall posts and a lintel under a small shingle roof, the doors open by day and shut at night, a torch on each post lit after dusk. */
    private fun gate() {
        val z = z
        val d = GATE_D
        val p = lpm(d)
        for (side in intArrayOf(-1, 1)) {
            val x = GATE_X + side * 2.5f
            val bx = lx(x, d)
            val pw = max(z.toFloat(), 0.5f * p)
            c.fillRect((bx - pw / 2).toInt(), lyh(d, 4.6f).toInt(), max(1, pw.toInt()), (ly(d) - lyh(d, 4.6f)).toInt() + 1, Pal.LOG_D)
            // the torches
            val tx = bx + side * pw
            val ty = lyh(d, 3.4f)
            c.fillRect(tx.toInt(), ty.toInt(), max(1, z / 2), max(1, (0.8f * p).toInt()), Pal.WOOD_X)
            if (lit || fxOn("wolves") > 0.3f) {
                glow {
                    val fl = 0.8f + 0.2f * sin(t * 13 + side).toFloat()
                    c.fillEllipse(tx, ty - 0.3f * p, max(0.8f, 0.25f * p), max(1f, 0.45f * p * fl), Pal.FLAME[2])
                    c.set(tx.toInt(), (ty - 0.3f * p).toInt(), Pal.FLAME[0])
                }
                s.light(tx, ty, 16f, 0.55f)
            }
        }
        // the lintel and its little roof
        val xl = lx(GATE_X - 3f, d); val xr = lx(GATE_X + 3f, d)
        val ly0 = lyh(d, 4.6f)
        c.fillRect(xl.toInt(), ly0.toInt(), (xr - xl).toInt(), max(1, (0.35f * p).toInt()), Pal.LOG_M)
        val roofC = if (env.snow) Pal.SNOW_L else Pal.SHINGLE_M
        val rh = max(2f, 0.9f * p)
        for (j in 0 until rh.toInt()) {
            val f = j / rh
            c.hline((xl - 0.4f * p * (1f - f)).toInt(), (xr + 0.4f * p * (1f - f)).toInt(), (ly0 - 1 - j).toInt(), if (j == 0) Pal.SHINGLE_D else roofC)
        }
        // the doors: swung open inward by day, shut at night
        val open = !lit
        val plank = Col.mix(Pal.WOOD_M, Pal.LOG_D, 0.3f)
        for (side in intArrayOf(-1, 1)) {
            val hinge = GATE_X + side * 2.3f
            val tipX = if (open) hinge - side * 0.4f else GATE_X
            val tipD = if (open) d - 2.1f else d
            val steps = 8
            for (q in 0..steps) {
                val f = q / steps.toFloat()
                val xx = hinge + (tipX - hinge) * f; val dd = d + (tipD - d) * f
                val x0 = lx(xx, dd); val y0 = lyh(dd, 3.6f); val y1 = ly(dd)
                c.fillRect(x0.toInt(), y0.toInt(), max(1, (0.5f * lpm(dd)).toInt()), (y1 - y0).toInt(), if (q % 3 == 0) Col.scale(plank, 0.8f) else plank)
            }
        }
    }

    // ------------------------------------------------------------------ the village

    private class House(val x: Float, val d: Float, val w: Float, val dep: Float, val hut: Boolean, val ochre: Boolean = false)

    /**
     * A house seen from above: its front wall toward us, the side facing the middle with its gable, the front slope of its
     * roof (tiles on a house, shingles on a hut, snow in winter) with a chimney; windows lit at night, smoke from the chimney
     * in the morning and the evening.
     */
    private fun house(hs: House, seed: Int) {
        val z = z
        val x0 = hs.x - hs.w / 2f; val x1 = hs.x + hs.w / 2f
        val d0 = hs.d; val d1 = hs.d + hs.dep; val dm = (d0 + d1) / 2f
        val e = if (hs.hut) 2.6f else 4.6f
        val r = e + hs.dep * (if (hs.hut) 0.55f else 0.45f)
        val o = 0.45f
        val wall = when { hs.hut -> Pal.LOG_M; hs.ochre -> Pal.OCHRE_L; else -> Pal.WALL_L }
        val wallD = when { hs.hut -> Pal.LOG_D; hs.ochre -> Pal.OCHRE_M; else -> Pal.WALL_M }
        val roof = when { env.snow -> Pal.SNOW_L; hs.hut -> Pal.SHINGLE_M; else -> Pal.ROOF_L }
        val roofD = when { env.snow -> Pal.SNOW_D; hs.hut -> Pal.SHINGLE_D; else -> Pal.ROOF_M }
        // which side shows: the one toward the middle
        val side = when { hs.x > hs.w / 2f -> x0; hs.x < -hs.w / 2f -> x1; else -> Float.NaN }
        prop {
            if (!side.isNaN()) {
                c.polyBegin()
                c.polyAdd(lx(side, d0), ly(d0)); c.polyAdd(lx(side, d1), ly(d1)); c.polyAdd(lx(side, d1), lyh(d1, e))
                c.polyAdd(lx(side, dm), lyh(dm, r)); c.polyAdd(lx(side, d0), lyh(d0, e))
                c.polyFill { px, py ->
                    val shade = Col.scale(wallD, 0.9f)
                    if (hs.hut && ((py - lyh(dm, 0f)) / max(1f, lpm(dm) * 0.3f)).toInt() % 2 == 0) Col.scale(shade, 0.88f) else shade
                }
                // a window in the side wall
                val wp = lpm(dm)
                val wa = lx(side, dm - 0.45f); val wb = lx(side, dm + 0.45f)
                if (abs(wb - wa) >= 1f) winAt(min(wa, wb), lyh(dm - 0.45f, if (hs.hut) 1.8f else 3.4f), abs(wb - wa), max(1f, 0.9f * wp), seed * 3 + 1)
                // the back verge of the roof beyond the gable
                c.line(lx(side, dm).toInt(), lyh(dm, r + 0.2f).toInt(), lx(side + (if (side > 0) -o else o), d1 + o).toInt(), lyh(d1 + o, e - 0.2f).toInt(), roofD)
            }
            // the front wall
            val fx0 = lx(x0, d0); val fx1 = lx(x1, d0); val top = lyh(d0, e); val foot = ly(d0)
            val p = lpm(d0)
            for (y in max(top.toInt(), c.top) until min(foot.toInt() + 1, c.bottom)) for (x in max(fx0.toInt(), c.left) until min(fx1.toInt() + 1, c.right)) {
                val hm = (foot - (y + 0.5f)) / p
                var col = if (hm < 0.5f && !hs.hut) Pal.STONE_M else wall
                if (hs.hut && ((hm / 0.3f).toInt() % 2 == 1)) col = wallD
                if (x == fx1.toInt()) col = wallD
                c.set(x, y, col)
            }
            // the door and the windows
            val nWin = if (hs.hut) 1 else 2
            val doorX = hs.x + (if (seed % 2 == 0) -0.25f else 0.25f) * hs.w
            c.fillRect(lx(doorX - 0.5f, d0).toInt(), lyh(d0, 2.1f).toInt(), max(1, (1.0f * p).toInt()), max(1, (2.1f * p).toInt()), Pal.DOOR)
            for (k in 0 until nWin) {
                val wx = if (nWin == 1) hs.x - (doorX - hs.x) else hs.x + (k - 0.5f) * hs.w * 0.45f - (doorX - hs.x) * 0.3f
                winAt(lx(wx - 0.5f, d0), lyh(d0, if (hs.hut) 1.9f else 1.9f), max(1f, 1.0f * p), max(1f, 1.1f * p), seed * 5 + k)
                if (!hs.hut) winAt(lx(wx - 0.5f, d0), lyh(d0, 4.0f), max(1f, 1.0f * p), max(1f, 1.1f * p), seed * 5 + k + 2)
            }
        }
        // the roof's front slope, the chimney on it
        thing("roofs") {
            c.polyBegin()
            c.polyAdd(lx(x0 - o, d0 - o), lyh(d0 - o, e - 0.3f)); c.polyAdd(lx(x1 + o, d0 - o), lyh(d0 - o, e - 0.3f))
            c.polyAdd(lx(x1 + o, dm), lyh(dm, r)); c.polyAdd(lx(x0 - o, dm), lyh(dm, r))
            val eave = lyh(d0 - o, e - 0.3f); val ridgeY = lyh(dm, r)
            val courses = max(3f, (eave - ridgeY) / max(1f, 0.9f * z))
            c.polyFill { px, py ->
                val v = (eave - (py + 0.5f)) / max(1f, eave - ridgeY) // 0 at the eave .. 1 at the ridge
                val row = floor(v * courses).toInt()
                var col = if (row % 2 == 0) roof else Col.mix(roof, roofD, 0.4f)
                if (v > 0.93f) col = Col.mix(roof, Col.hex(0xFFF0C8), 0.2f)
                if (v < 0.08f) col = roofD
                if (!env.snow && z > 1 && ((px + row * 2) % (3 * z)) == 0) col = roofD
                if (env.snow && Noise.rnd(px, py, seed) < 0.08f) col = Pal.SNOW_M
                col
            }
            // the chimney
            val chX = hs.x + (if (seed % 2 == 0) 0.22f else -0.26f) * hs.w
            val chD = d0 + hs.dep * 0.34f
            val cp = lpm(chD)
            val chTop = lyh(chD, r - 0.2f)
            if (!hs.hut || seed % 2 == 0) {
                c.fillRect(lx(chX, chD).toInt(), chTop.toInt(), max(1, (0.6f * cp).toInt()), max(2, (1.3f * cp).toInt()), Pal.STONE_M)
                c.hline(lx(chX, chD).toInt(), lx(chX, chD).toInt() + max(1, (0.6f * cp).toInt()) - 1, chTop.toInt(), Pal.STONE_X)
                val hr = s.hour
                if (hr in 5.5f..10f || hr in 16.5f..22.5f || env.season == Season.WINTER) s.smoke(lx(chX + 0.3f, chD), chTop - z, 0.45f, seed + 11)
            }
        }
    }

    /** A window [ww] × [hh] px at ([x], [y]): glass by day, lit at night (some dark). */
    private fun winAt(x: Float, y: Float, ww: Float, hh: Float, seed: Int) {
        val on = lit && Noise.rnd(seed, 1051) < 0.75f
        val xi = x.toInt(); val yi = y.toInt(); val wi = max(1, ww.toInt()); val hi = max(1, hh.toInt())
        if (on) {
            glow { c.fillRect(xi, yi, wi, hi, Pal.WINDOW_LIT) }
            s.light(x + ww / 2f, y + hh / 2f, 8f, 0.35f)
        } else {
            c.fillRect(xi, yi, wi, hi, Pal.GLASS)
            if (wi >= 2) c.set(xi, yi, Pal.GLASS_HI)
        }
    }

    /** A tree in the village seen from above: its trunk, a round crown lit on the sun's side; [linden]: the big one on the square. */
    private fun tree(x: Float, d: Float, hgt: Float, seed: Int, linden: Boolean) {
        val p = lpm(d)
        val bx = lx(x, d); val by = ly(d)
        c.fillRect((bx - max(1f, 0.3f * p) / 2).toInt(), lyh(d, hgt * 0.45f).toInt(), max(1, (0.3f * p).toInt()), (by - lyh(d, hgt * 0.45f)).toInt() + 1, Pal.LOG_D)
        val (dk, md, lt) = when (env.season) {
            Season.AUTUMN -> if (linden) Triple(Col.hex(0x9A6A1E), Col.hex(0xC8902E), Col.hex(0xE8C050)) else Triple(Col.hex(0x8A3E1A), Col.hex(0xC0682A), Col.hex(0xE8A844))
            Season.SPRING -> Triple(Col.hex(0x3E7A34), Col.hex(0x62A248), Col.hex(0x9ACC66))
            Season.WINTER -> Triple(Col.hex(0x5A4E46), Col.hex(0x6E6258), Col.hex(0x8A7E70))
            else -> Triple(Col.hex(0x2A5E30), Col.hex(0x3E7E3A), Col.hex(0x68A44C))
        }
        val cy = lyh(d, hgt * 0.68f)
        val rx = hgt * 0.34f * p; val ry = hgt * 0.3f * p
        for (y in max((cy - ry).toInt(), c.top)..min((cy + ry).toInt(), c.bottom - 1)) for (xx in max((bx - rx).toInt(), c.left)..min((bx + rx).toInt(), c.right - 1)) {
            val dx = (xx + 0.5f - bx) / rx; val dy = (y + 0.5f - cy) / ry
            val q = dx * dx + dy * dy + (Noise.v2(xx * 0.6f / z, y * 0.6f / z, seed + 1061) - 0.5f) * 0.3f
            if (q > 1f) continue
            if (env.season == Season.WINTER && Noise.rnd(xx / z, y / z, seed + 1062) < 0.45f) continue
            val l = (if (sunLeft) -dx else dx) * 0.5f - dy * 0.75f + (Noise.rnd(xx / z, y / z, seed + 1063) - 0.5f) * 0.35f
            var col = if (l > 0.35f) lt else if (l > -0.2f) md else dk
            if (env.snow && dy < -0.2f && Noise.rnd(xx, y, seed) < 0.5f) col = Pal.SNOW_L
            if (linden && env.month == 6 && Noise.rnd(xx / z, y / z, seed + 1064) < 0.06f) col = Col.hex(0xF0E08A)
            c.set(xx, y, col)
        }
    }

    // ------------------------------------------------------------------ the tower

    /** The underside of the tower's shingle roof along the top: boards between the rafters, the eave beam; icicles in winter. */
    private fun roofUnderside() {
        val z = z
        val rb = ROOF_B * z
        val board = intArrayOf(Col.hex(0x5A3E2A), Col.hex(0x4E3424), Col.hex(0x654630))
        for (y in max(0, c.top) until min(rb, c.bottom)) for (x in max(0, c.left) until min(w, c.right)) {
            val row = (y / (3 * z))
            var col = board[Math.floorMod(row + (x / (37 * z)), 3)]
            if (y % (3 * z) == 0) col = Col.hex(0x2E2018)
            // the rafters, fanning out toward us
            val u = (x - w / 2f) / (18f * z) * (1f + (rb - y) / (rb * 1.4f))
            if (u - floor(u) < 0.14f) col = Col.hex(0x3A281C)
            if (y >= rb - 3 * z) col = if (y == rb - 3 * z) Col.hex(0xA07448) else if (y >= rb - z) Col.hex(0x5E4028) else Col.hex(0x8A6038)
            c.set(x, y, col)
        }
        if (env.season == Season.WINTER) for (k in 0 until w / (5 * z) + 1) {
            val x = k * 5 * z + (Noise.rnd(k, 1071) * 3 * z).toInt()
            val len = ((1 + Noise.rnd(k, 1072) * 5) * z).toInt()
            if (Noise.rnd(k, 1073) < 0.4f) continue
            for (j in 0 until len) {
                val ww = max(1, (z * (1f - j / len.toFloat())).toInt())
                c.fillRect(x, rb + j, ww, 1, if (j == 0) Pal.SNOW_L else Pal.ICE_L)
            }
        }
    }

    /** A corner post of the tower from the roof down to the platform, at picture column [x], its knee brace up to the eave beam. */
    private fun post(x: Int, left: Boolean) {
        val z = z
        val pw = 9 * z
        val foot = railFoot.toInt()
        for (y in max(0, c.top) until min(foot + 1, c.bottom)) for (xx in max(x, c.left) until min(x + pw, c.right)) {
            val u = (xx - x) / pw.toFloat()
            val inner = if (left) u > 0.72f else u < 0.28f
            val outer = if (left) u < 0.14f else u > 0.86f
            var col = if (inner) Pal.WOOD_L else if (outer) Pal.WOOD_D else Pal.WOOD_M
            val grain = Noise.v2(xx * 0.9f / z, y * 0.04f / z, if (left) 1081 else 1082)
            if (!inner && grain > 0.72f) col = Col.scale(col, 0.86f)
            if (z > 1 && Noise.rnd(xx, y / (4 * z), 1083) < 0.06f) col = Col.scale(col, 0.8f)
            c.set(xx, y, col)
        }
        // the knee brace: a beam from the post up to the eave
        val rb = ROOF_B * z
        val len = 22 * z
        for (j in 0 until len) for (q in 0 until 4 * z) {
            val px = if (left) x + pw + j - q / 2 else x - j + q / 2
            val py = rb + len - j - q
            c.set(px, py, if (q < z) Pal.WOOD_L else if (q >= 3 * z) Pal.WOOD_D else Pal.WOOD_M)
        }
    }

    /**
     * The flag on its pole leaning out from the right post: white, blue and red, the coat of arms at the hoist (Triglav
     * white on a blue shield with a red border, the stars above it closer up), flying and waving in the wind.
     */
    private fun flag() {
        val z = z
        val bx = w - 9 * z; val by = oy + 46 * z
        val tx = w - 22 * z; val ty = oy + 17 * z
        // the pole, a gold knob on its top
        val steps = by - ty
        for (j in 0..steps) {
            val xx = bx + (tx - bx) * j / steps
            c.fillRect(xx - z / 2, by - j, max(1, z + z / 2), 1, if (j % (4 * z) == 0) Pal.WOOD_D else Pal.WOOD_X)
        }
        c.fillRect(tx - z, ty - 2 * z, 2 * z + 1, 2 * z, Pal.GOLD)
        val fw = 17 * z; val fh = 11 * z
        val gust = 1f + 1.5f * frame.sky.wind
        val stripes = intArrayOf(Pal.FLAG_WHITE, Pal.FLAG_BLUE, Pal.FLAG_RED)
        for (i in 0 until fw) {
            // i: from the hoist at the pole out to the fly, to the left
            val f = i / fw.toFloat()
            val wave = (sin(t * 4.0 * gust - i * 0.33 / z) * (0.4f + f * 2.2f) * z * (0.5f + 0.5f * gust)).toFloat()
            val droop = f * f * 2f * z * (1.6f - frame.sky.wind)
            for (j in 0 until fh) {
                val hoistX = tx + (bx - tx) * j / steps
                val xx = hoistX - z - i
                val yy = ty + j + (wave + droop).toInt()
                var col = stripes[(j * 3) / fh]
                // the coat of arms at the hoist, over the white and the blue
                val sx = i - z; val sy = j - fh / 6
                val sw = 4 * z; val sh = 5 * z
                if (sx in 0 until sw && sy in 0 until sh) {
                    val edge = sx < max(1, z / 2 + 1) || sx >= sw - max(1, z / 2 + 1) || sy >= sh - max(1, z / 2 + 1)
                    val mid = sw / 2f
                    val peak = sh * 0.3f + abs(sx + 0.5f - mid) * 0.9f + (if (abs(sx + 0.5f - mid) > sw * 0.22f) -z * 0.6f else 0f)
                    col = when {
                        edge -> Pal.FLAG_RED
                        sy > peak && sy < sh - 2 * z -> Pal.FLAG_WHITE
                        z > 1 && sy < 2 * z && (sx == 2 * z || sx == 3 * z - 1 || (sy == z && sx == (sw - 1) / 2)) -> Pal.GOLD
                        else -> Pal.FLAG_BLUE
                    }
                    if (z > 2 && !edge && sy >= sh - 2 * z && ((sx + sy) % 3 == 0)) col = Pal.FLAG_WHITE
                }
                if (((i / z + (t * 3).toInt()) % 7) == 0) col = Col.scale(col, 0.88f)
                c.set(xx, yy, col)
            }
        }
    }

    /** Where the horn hangs: its peg on the left post. */
    private val hornX: Int get() = 9 * z + 3 * z
    private val hornY: Int get() = oy + 74 * z

    /**
     * The horn (rog): a cow's horn on a leather strap from its peg on the left post, pale at the mouth and dark at the tip,
     * with brass bands; poked, it swings and sounds; blown ([fx] "horn"), it shakes with the call.
     */
    private fun horn() {
        val z = z
        val pk = poked("horn"); val a = pk?.age(t) ?: 0f
        val sounding = fxOn("horn")
        var swing = sin(t * 0.8).toFloat() * 0.05f
        if (pk != null) swing += sin(a * 7f) * 0.5f * (1f - PokeArt.ease(a, 0f, if (pk.step == 0) 2.4f else 3.4f)) * (if (pk.step == 1) 1.4f else 1f)
        if (sounding > 0.02f) swing += sin(t * 30.0).toFloat() * 0.04f * sounding
        val px = hornX; val py = hornY
        // the peg
        c.fillRect(px - z, py - z, 3 * z, 2 * z, Pal.WOOD_X)
        // the strap down to the horn
        val sx = px + 4 * z + (swing * 14f * z).toInt(); val sy = py + 11 * z
        val strap = Col.hex(0x5A3A22)
        for (o in 0 until max(1, z / 2 + 1)) { c.line(px + o, py, sx - 6 * z + o, sy + z, strap); c.line(px + z + o, py, sx + 6 * z + o, sy - z, strap) }
        // the horn: a curve from the wide mouth (left) to the dark tip (right, curling up)
        val n = 22 * z
        for (i in 0 until n) {
            val f = i / n.toFloat()
            val cx = sx - 8f * z + f * 17f * z
            val cy = sy + 3f * z + sin(f * 2.6f) * 3f * z - f * f * 7f * z
            val r = (3.4f - 2.8f * f) * z
            val col = when {
                f < 0.1f -> Col.hex(0xF6ECD2)
                f > 0.84f -> Col.hex(0x3A2E26)
                f > 0.62f -> Col.hex(0x9A7A58)
                else -> Col.hex(0xE0CCA2)
            }
            c.fillEllipse(cx, cy, max(0.6f, r * 0.55f), max(0.6f, r), col)
            // the lit edge along its top
            if (r > 1.5f) c.set(cx.toInt(), (cy - r + 0.5f).toInt(), Col.mix(col, Col.hex(0xFFFFFF), 0.4f))
            if (abs(f - 0.28f) < 0.025f || abs(f - 0.68f) < 0.025f) c.fillEllipse(cx, cy, max(0.6f, r * 0.55f), max(0.6f, r + 0.3f), Pal.GOLD)
        }
        // the mouth's dark inside, the brass mouthpiece at the tip
        c.fillEllipse(sx - 8f * z, sy + 3f * z, max(0.6f, 1.2f * z), max(0.6f, 2.4f * z), Col.hex(0x4A3A2A))
        c.fillRect((sx + 9f * z).toInt(), (sy - 4f * z).toInt(), max(1, z), max(1, z), Pal.GOLD)
        mouthX = sx - 8f * z; mouthY = sy + 3f * z
    }

    /** Where the horn's mouth was drawn this frame, for its call. */
    private var mouthX = 0f
    private var mouthY = 0f

    /** The horn's call: rings going out from its mouth and a wide one over the valley, while it sounds (poked or [fx] "horn"). */
    private fun hornCall() {
        val z = z
        val pk = poked("horn"); val a = pk?.age(t) ?: 0f
        val level = fxOn("horn")
        val mx = mouthX; val my = mouthY
        if (pk != null) {
            val dur = if (pk.step == 0) 2.2f else 3.2f
            if (a in 0.3f..dur) s.fx {
                val u = ((a - 0.3f) / (dur - 0.3f)).coerceIn(0f, 1f)
                PokeArt.rings(c, mx, my, u, (if (pk.step == 0) 30f else 48f) * z, Col.hex(0xFFE08A), z + 1, 0.95f, n = if (pk.step == 0) 2 else 3)
            }
        }
        if (level > 0.02f) s.fx {
            val u = ((t * 0.45) % 1.0).toFloat()
            PokeArt.rings(c, mx, my, u, 40f * z, Col.hex(0xFFE08A), z + 1, 0.9f * level, n = 3)
            // the call rolling out over the land, a wide soft arc
            val v = ((t * 0.3 + 0.4) % 1.0).toFloat()
            PokeArt.rings(c, vx, (hz + 20 * z).toFloat(), v, 120f * z, Col.hex(0xFFF4DA), z, 0.4f * level, n = 1)
        }
    }

    /** The watch's torch in its iron bracket on the right post: lit from dusk, or as a dialog says ([fx] "torch"); its light. */
    private fun torch() {
        val z = z
        val bx = w - 9 * z; val by = oy + 66 * z
        val iron = Col.hex(0x3A3432); val ironL = Col.hex(0x6A625E)
        // the bracket: a plate on the post, an arm out, a ring at its end, a stay under it
        c.fillRect(bx - z, by + 2 * z, 2 * z, 12 * z, iron)
        c.fillRect(bx - 7 * z, by + 5 * z, 7 * z, z, iron); c.hline(bx - 7 * z, bx - z, by + 5 * z, ironL)
        c.fillRect(bx - 9 * z, by + 3 * z, 2 * z, 4 * z, iron)
        for (j in 0 until 6 * z) c.set(bx - z - j, by + 12 * z - j, iron)
        // the shaft, leaning out through the ring
        for (j in 0 until 18 * z) c.fillRect(bx - 5 * z - j / 3, by + 14 * z - j, 2 * z, 1, if (j % (4 * z) == 0) Pal.WOOD_D else Pal.WOOD_M)
        // the head, wrapped in tarred cloth
        val hx = bx - 12 * z; val hy = by - 8 * z
        c.fillRect(hx, hy, 4 * z, 5 * z, Col.hex(0x2E2622))
        c.hline(hx, hx + 4 * z - 1, hy + z, Col.hex(0x4A3E36)); c.hline(hx, hx + 4 * z - 1, hy + 3 * z, Col.hex(0x4A3E36))
        val on = fx("torch") ?: if (lit) 1f else 0f
        if (on > 0.05f) {
            val fl = 0.8f + 0.2f * sin(t * 12).toFloat() + 0.1f * sin(t * 7.3).toFloat()
            val hgt = (9f + 4f * on) * z * fl
            val cx = hx + 2f * z
            glow {
                c.fillEllipse(cx, hy - hgt * 0.32f, 3f * z * on, hgt * 0.5f, Pal.FLAME[3])
                c.fillEllipse(cx + sin(t * 9).toFloat() * 0.6f * z, hy - hgt * 0.28f, 2.1f * z * on, hgt * 0.38f, Pal.FLAME[2])
                c.fillEllipse(cx, hy - hgt * 0.16f, 1.3f * z, hgt * 0.22f, Pal.FLAME[1])
                c.fillEllipse(cx, hy - hgt * 0.08f, 0.8f * z, hgt * 0.1f, Pal.FLAME[0])
            }
            s.light(cx, hy - 3f * z, 60f * on, 0.9f * on)
            s.sparks(cx, hy - hgt * 0.8f, 1, 1091)
            s.smoke(cx, hy - hgt, 0.25f * on, 1092, dark = true)
        } else if (fx("torch") != null) wisps(hx + 2f * z, hy.toFloat(), 0.4f, 1093)
    }

    /** The platform's floor from the railing to us: boards running toward the view, their ends in rows; the hatch with the ladder's top. */
    private fun platform() {
        val z = z
        val top = railFoot.toInt()
        val tone = intArrayOf(Col.hex(0xA07448), Col.hex(0x946A40), Col.hex(0xAA7C4E), Col.hex(0x8C623A))
        for (py in max(top, c.top) until min(h, c.bottom)) {
            val d = depthAt(py)
            for (px in max(0, c.left) until min(w, c.right)) {
                val x = sideAt(px, d)
                val u = x / 0.32f; val b = floor(u).toInt()
                var col = tone[Math.floorMod(Noise.hash(b, 1101), 4)]
                val seamPx = (u - b) * 0.32f * pm(d)
                if (seamPx < 1f) col = Col.hex(0x5A3C24)
                val v = d / 1.4f + Noise.rnd(b, 1102) * 3f
                if ((v - floor(v)) * 1.4f * (F * EYE / (d * d)) < 1f) col = Col.scale(col, 0.8f)
                if (z > 1 && Noise.rnd(px / 3, py, 1103) < 0.08f) col = Col.scale(col, 0.9f)
                // the hatch: a dark square with the ladder's top in it
                if (x in 2.7f..4.0f && d in 9.55f..10.35f) {
                    val lu = (x - 2.7f) / 1.3f; val lv = (d - 9.55f) / 0.8f
                    col = if (lu < 0.05f || lu > 0.95f || lv > 0.94f) Col.hex(0x6E4A2C)
                    else if (abs(lu - 0.25f) < 0.05f || abs(lu - 0.75f) < 0.05f) Col.hex(0x7A5634)
                    else if ((lv * 4f).let { it - floor(it) } < 0.12f && lu > 0.25f && lu < 0.75f) Col.hex(0x6A4A30)
                    else Col.hex(0x1E1612)
                }
                if (env.snow && py > h - 6 * z && Noise.rnd(px, py, 1104) < 0.1f) col = Pal.SNOW_M
                c.set(px, py, col)
            }
        }
    }

    /**
     * What stands on the platform close to us (a taller view shows it): a bucket of water against fire, a coil of rope, a
     * basket of spare torches, a stool for the long nights.
     */
    private fun onThePlatform() {
        if (gy(9.6f) >= h) return
        val z = z
        // the bucket
        prop {
            val d = 8.3f; val p = pm(d); val bx = gx(-3.1f, d); val by = gy(d)
            val bw = 0.2f * p; val bh = 0.34f * p
            c.fillRect((bx - bw).toInt(), (by - bh).toInt(), (2 * bw).toInt(), bh.toInt(), Pal.WOOD_M)
            c.hline((bx - bw).toInt(), (bx + bw).toInt(), (by - bh * 0.3f).toInt(), Col.hex(0x3A3432)); c.hline((bx - bw).toInt(), (bx + bw).toInt(), (by - bh * 0.8f).toInt(), Col.hex(0x3A3432))
            c.fillEllipse(bx, by - bh, bw, max(1f, 0.06f * p), if (env.season == Season.WINTER) Pal.ICE_L else Col.hex(0x3E6A8A))
            for (a in 0..10) { val f = a / 10f * 3.14f; c.set((bx - cos(f) * bw).toInt(), (by - bh - sin(f) * 0.2f * p).toInt(), Col.hex(0x3A3432)) }
        }
        // the coil of rope
        prop {
            val d = 7.7f; val p = pm(d); val bx = gx(1.3f, d); val by = gy(d)
            for (r in 0 until 4) c.fillEllipse(bx, by - 0.05f * p - r * 0.035f * p, (0.3f - r * 0.05f) * p, (0.1f - r * 0.015f) * p, if (r % 2 == 0) Col.hex(0xC8A870) else Col.hex(0xA8884E))
            c.fillEllipse(bx, by - 0.2f * p, 0.08f * p, 0.03f * p + 0.5f, Col.hex(0x6A5030))
        }
        // the basket of spare torches
        prop {
            val d = 8.8f; val p = pm(d); val bx = gx(3.9f, d); val by = gy(d)
            for (k in 0 until 4) {
                val tx = bx + (k - 1.5f) * 0.08f * p
                c.line(tx.toInt(), (by - 0.3f * p).toInt(), (tx + (k - 1.5f) * 0.05f * p).toInt(), (by - 0.85f * p).toInt(), Pal.WOOD_M)
                c.fillRect((tx + (k - 1.5f) * 0.05f * p - 0.04f * p).toInt(), (by - 0.92f * p).toInt(), max(1, (0.08f * p).toInt()), max(1, (0.12f * p).toInt()), Col.hex(0x2E2622))
            }
            for (y in (by - 0.35f * p).toInt()..by.toInt()) for (xx in (bx - 0.2f * p).toInt()..(bx + 0.2f * p).toInt()) c.set(xx, y, if (((xx / max(1, z)) + (y / max(1, z))) % 2 == 0) Col.hex(0xB8864A) else Col.hex(0x8A5E30))
        }
        // the stool
        prop {
            val d = 9.1f; val p = pm(d); val bx = gx(-0.9f, d); val by = gy(d)
            for (lx in floatArrayOf(-0.15f, 0.15f)) c.fillRect((bx + lx * p).toInt(), (by - 0.42f * p).toInt(), max(1, (0.05f * p).toInt()), (0.42f * p).toInt(), Pal.WOOD_D)
            c.fillEllipse(bx, by - 0.44f * p, 0.22f * p, 0.06f * p + 0.5f, Pal.WOOD_L)
        }
    }

    /** The railing along the front of the platform: a top rail, a middle rail and the posts between, grain closer up; snow on the top rail in winter. */
    private fun railing() {
        val z = z
        val foot = railFoot; val top = railTop
        val p = pm(RAIL_D)
        val topH = max(3f * z, 0.2f * p)
        // the posts, every 1.6 m
        val x0 = sideAt(0, RAIL_D); val x1 = sideAt(w, RAIL_D)
        var k = floor(x0 / 1.6f).toInt()
        while (k * 1.6f < x1 + 1.6f) {
            val bx = gx(k * 1.6f, RAIL_D)
            val pw = max(2f * z, 0.16f * p)
            for (y in max(top.toInt(), c.top) until min(foot.toInt() + 1, c.bottom)) for (xx in max(bx.toInt(), c.left) until min((bx + pw).toInt(), c.right)) {
                val u = (xx - bx) / pw
                c.set(xx, y, if (u < 0.3f) Pal.WOOD_L else if (u > 0.75f) Pal.WOOD_D else Pal.WOOD_M)
            }
            k++
        }
        // the middle rail
        val mid = hy(RAIL_D, 0.55f)
        for (y in max(mid.toInt(), c.top) until min((mid + 2f * z).toInt(), c.bottom)) for (x in max(0, c.left) until min(w, c.right)) c.set(x, y, if (y == mid.toInt()) Pal.WOOD_L else Pal.WOOD_M)
        // the top rail: a thick beam, lit along its top
        for (y in max(top.toInt(), c.top) until min((top + topH).toInt(), c.bottom)) for (x in max(0, c.left) until min(w, c.right)) {
            val v = (y - top) / topH
            var col = if (v < 0.25f) Pal.WOOD_L else if (v > 0.8f) Pal.WOOD_D else Pal.WOOD_M
            if (z > 1 && v >= 0.25f && Noise.v2(x * 0.08f / z, y * 0.9f / z, 1111) > 0.7f) col = Col.scale(col, 0.88f)
            if (env.snow && v < 0.3f) col = Pal.SNOW_L
            c.set(x, y, col)
        }
    }

    /** The spyglass lying on the top rail: a brass tube in three sections, the lens glinting. */
    private fun spyglass() {
        val z = z
        val y = railTop.toInt() - 2 * z
        val x = ox + 92 * z
        val len = 18 * z
        for (i in 0 until len) {
            val f = i / len.toFloat()
            val r = if (f < 0.35f) 2 else if (f < 0.7f) 1.5f.toInt() + 1 else 1
            val hh = r * z
            val col = if (abs(f - 0.35f) < 0.04f || abs(f - 0.7f) < 0.04f) Col.hex(0x6A4A1E) else Col.hex(0xC8962E)
            c.fillRect(x + i, y - hh + 2 * z, 1, hh, col)
            c.set(x + i, y - hh + 2 * z, Col.hex(0xF0D070))
        }
        c.fillRect(x - z, y, z, 2 * z, Col.hex(0x9AC4E0))
        if (z > 1) c.set(x - z, y, Col.hex(0xFFFFFF))
    }

    /**
     * The crow on the railing: black, a grey bill, now and then turning its head; poked, it caws with its bill wide (the
     * sound going out), then it flies off, wheels over the village and lands again.
     */
    private fun crow() {
        val z = z
        val rx = ox + 188 * z; val ry = railTop.toInt()
        val pk = poked("crow"); val a = pk?.age(t) ?: 0f
        val black = Col.hex(0x1E1C22); val sheen = Col.hex(0x3A3A52); val bill = Col.hex(0x5A5A60)
        if (pk != null && pk.step == 1) {
            val out = PokeArt.ease(a, 0f, 0.6f); val back = PokeArt.ease(a, 4.8f, 6.0f)
            if (out > 0f && back < 1f) {
                // on the wing: off over the village in a wide loop, back to the rail
                val lp = ((a - 0.3f) / 4.8f).coerceIn(0f, 1f)
                val ang = lp * 6.28f
                val fx = rx + (sin(ang) * 60f * z * (1f - back)).toInt()
                val fy = ry - ((1f - cos(ang)) * 22f * z * (1f - back) + 4 * z * out * (1f - back)).toInt()
                PokeArt.wings(c, fx, fy - 2 * z, ((t * 10).toInt() and 1) == 0, z, black, sheen)
                c.fillRect(fx + 2 * z, fy - 3 * z, z, z, black)
                return
            }
        }
        val cawing = pk != null && pk.step == 0 && a < 1.7f
        val open = cawing && (a * 4f).toInt() % 2 == 0
        val turn = if (!cawing && sin(t * 0.9) > 0.7) -1 else 1
        sprite(rx, ry) {
            // tail, body, head
            c.fillRect(rx - 5, ry - 3, 3, 2, black)
            c.fillRect(rx - 3, ry - 5, 6, 4, black)
            c.fillRect(rx - 2, ry - 5, 4, 1, sheen)
            c.fillRect(rx + 2 * turn - (if (turn < 0) 2 else 0), ry - 8, 3, 3, black)
            c.set(rx + (if (turn > 0) 3 else -2), ry - 7, Col.hex(0xE8E8F0))
            c.set(rx + (if (turn > 0) 5 else -4), ry - 7, bill); c.set(rx + (if (turn > 0) 6 else -5), ry - 7, bill)
            if (open) c.set(rx + (if (turn > 0) 5 else -4), ry - 6, bill)
            // the legs on the rail
            c.set(rx - 1, ry - 1, bill); c.set(rx + 1, ry - 1, bill)
            if (cawing) { c.set(rx - 4, ry - 6, black); c.set(rx - 5, ry - 7, black) } // the wings a little up
        }
        if (cawing && a > 0.1f) s.fx { PokeArt.rings(c, (rx + 6 * z).toFloat(), (ry - 7 * z).toFloat(), ((a * 2f) % 1f), 7f * z, Col.hex(0xFFFFFF), max(1, z / 2 + 1), 0.8f, n = 2) }
    }

    // ------------------------------------------------------------------ weather

    /**
     * Fog lies in the valley like a lake and over the plateau's far fields, thin by the village; rain veils the far hills
     * and the mountain first; the rain and the snow fall beyond the tower, seen between its posts and over its railing;
     * lightning strikes over the mountains. None of it under the roof, on the posts, the railing or the people.
     */
    override fun weather() {
        val z = z
        val sky = frame.sky
        // we stand under the roof: the rain and the snow fall on the land alone, as the fog and the veils lie there
        val wx = Weather(c, w, h, detail, t, env, sky)
        val beyond = Weather(c, w, h, detail, t, env, sky) { id -> id < towerFrom }
        val rain = sky.rain
        if (rain > 0.02f) {
            val grey = env.lit(Col.hex(0x9AA2AE))
            for (py in max(0, c.top) until min(hz + 30 * z, c.bottom)) {
                val row = py / z.toFloat()
                val prof = if (py < hz) exp((row - hz / z.toFloat()) / 26f) else 1f - (py - hz) / (30f * z)
                if (prof <= 0.02f) continue
                for (px in max(0, c.left) until min(w, c.right)) {
                    val i = c.index(px, py)
                    if (c.ids[i] >= towerFrom) continue
                    val shaft = 0.55f + 0.45f * Noise.v1((px - ox) / z * 0.06f - row * 0.02f + t.toFloat() * 0.12f, 1121)
                    val a = rain * 0.6f * prof * shaft
                    if (a > Dither.at(px, py) * 0.5f) c.pixels[i] = Col.mix(c.pixels[i], grey, min(0.75f, a))
                }
            }
        }
        val fog = sky.fog
        if (fog > 0.02f) {
            // the valley brims with fog, the forest and the plateau's far edge sink into it; thinner toward the village
            val white = Col.mix(env.lit(Col.hex(0xE8ECF0)), Col.hex(0xE8ECF0), 0.3f)
            for (py in max(hz - 16 * z, c.top) until min(h, c.bottom)) {
                val row = (py - hz) / z.toFloat()
                for (px in max(0, c.left) until min(w, c.right)) {
                    val i = c.index(px, py)
                    if (c.ids[i] >= towerFrom) continue
                    // a lake of fog up to just over the horizon, its top rolling; thinning over the plateau toward us
                    val u = (px - ox) / z.toFloat()
                    val lake = -2.5f + 2.5f * Noise.v1(u * 0.05f + t.toFloat() * 0.04f, 1123)
                    val prof = when {
                        row < lake -> exp((row - lake) / 3.5f)
                        row < 18f -> 1f
                        else -> 0.55f * exp(-(row - 18f) / 26f)
                    }
                    val drift = 0.7f + 0.3f * Noise.v2(u * 0.035f + t.toFloat() * 0.05f, row * 0.1f, 1122)
                    val a = fog * prof * drift
                    if (a > Dither.at(px, py) * 0.6f) c.pixels[i] = Col.mix(c.pixels[i], white, min(0.88f, a))
                }
            }
        }
        beyond.rain()
        beyond.snow()
        val flash = wx.flash()
        if (flash > 0f) {
            wx.lighten(flash, 0.42f, 0.12f)
            if (flash >= 1f) wx.boltLine(ROOF_B * z + 4 * z, hz - 2 * z, wx.bolt())
        }
    }

    companion object {
        /** The eye above the village's ground (m): the tower stands on a knoll at the village's edge, its platform high up. */
        private const val LAND_EYE = 30f
        /** How far below the plateau the valley lies (m). */
        private const val VALLEY_DROP = 260f
        /** The platform's railing: how far away it stands (m, on the vista's ground). */
        private const val RAIL_D = 11.2f
        /** The roof's underside: rows at the picture's top (scene canvas px). */
        private const val ROOF_B = 13
        /** The palisade's ring: its middle's depth and radii (m); the gate on it; the little square in the village. */
        private const val RING_D = 70f
        private const val RING_RX = 70f
        private const val RING_RD = 45f
        private const val GATE_X = -16f
        private val GATE_D = RING_D + RING_RD * sqrt(1f - (GATE_X / RING_RX) * (GATE_X / RING_RX))
        private const val SQUARE_D = 92f
        /** The forest's near edge (m). */
        private const val FOREST_D = 188f
        /** The valley (the thing) begins right of this scene column, where the forest ends. */
        private const val VALLEY_U = 122f

        private val HOUSES = listOf(
            House(-38f, 78f, 9f, 7f, false), House(-19f, 84f, 7f, 5.5f, true), House(-3f, 76f, 10f, 7f, false, ochre = true),
            House(18f, 80f, 8f, 6f, true), House(37f, 85f, 10f, 7f, false), House(-31f, 96f, 9f, 6.5f, false),
            House(-13f, 101f, 7f, 5.5f, true), House(15f, 98f, 11f, 7.5f, false), House(31f, 103f, 7f, 5.5f, true),
            House(-47f, 90f, 7f, 5.5f, true), House(48f, 96f, 8f, 6f, true, ochre = false), House(8f, 108f, 7f, 5f, true),
        )

        /** The village's trees: side, depth, height; the first is the linden on the square. */
        private val TREES = listOf(
            floatArrayOf(6f, 90f, 13f), floatArrayOf(-27f, 88f, 8f), floatArrayOf(26f, 93f, 7f), floatArrayOf(-40f, 104f, 8f),
            floatArrayOf(28f, 76f, 7f), floatArrayOf(-12f, 80f, 6.5f), floatArrayOf(44f, 106f, 7f),
        )
    }
}
