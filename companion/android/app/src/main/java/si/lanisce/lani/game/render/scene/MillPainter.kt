package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.Season
import si.lanisce.lani.game.scene.Poke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * V mlinu: inside the village's water mill, France's, cut open on its plot by the stream (the stream scene's mill, once it
 * grinds: companion/SCENES.md "Arts"). Plank walls on a stone footing, as the mill is built, the floor's boards dusted white.
 * By the back-right wall a timber platform carries the millstones: the bed stone, and on it the runner in its round
 * wooden vat, its grooved top turning in the vat's opening; over them on a frame the hopper (koš) full of golden grain, a
 * little shoe shaking it into the runner's eye. From the vat's front a wooden spout runs the flour into an open sack on the
 * floor, spilled flour round it. Under the platform the big cog wheel turns, half sunk in its pit, on the shaft that comes in
 * through the front-right wall from the waterwheel outside; its pegs drive a lantern pinion on the stones' spindle. Outside
 * on the right the overshot wheel turns in the mill race under the flume, the water falling on its buckets, and the race
 * pours off the plot's front edge. Along the right wall under the small window the miller's bench with the brass scale and
 * the measure (merica, the miller's toll); a sieve hangs high on its peg. Along the left wall the sacks of grain under the
 * loft, a gallery on knee braces with more sacks on it, the ladder up to it, and the plank door with the lantern on its
 * hook beside it. The cat crouches by the sacks, watching the mouse at its hole at the foot of the wall. An old millstone
 * lies in the grass in front.
 *
 * By day the window's light falls in a slanting beam full of flour dust; at night the lantern lights the room. Rain runs
 * down the window's glass and falls on the yard and the race; in winter snow lies outside, ice along the race's banks
 * and icicles hang from the flume's end and the wheel. The waterwheel, the cog wheel, the pinion and the runner turn
 * together, as one shaft drives them.
 *
 * Its effects ([si.lanisce.lani.game.scene.SceneArt.effects]), each drawn from its level, so every look agrees:
 * - `grind`: the stones grinding: below 0.12 stopped (the water off the flume, the wheel and the gears still, no flour
 *   running); below 0.6 an easy turn; from 0.6 hard, the wheel fast, the shoe shaking, the flour running thick. Not set: an
 *   easy turn.
 * - `pour`: grain poured into the hopper: how full it is, 0 empty to 1 full; while it fills (between them) a sack tipped
 *   over its rim pours a golden stream into it. Not set: half full.
 * - `sack`: the flour sack under the spout filling: 0 empty and slack, 1 full and round, flour heaped in its mouth. Not
 *   set: half full.
 * - `dust`: a puff of flour dust (how thick): a white cloud billowing up off the sack over whoever stands by it and
 *   drifting into the window's light.
 * - `cat`: the cat after the mouse: to 0.35 crouched and wiggling, ready; then the leap; from 0.75 landed at the hole, the
 *   mouse gone in (its tail last), the cat sitting there, looking foolish. Not set: crouched and watching, the mouse
 *   peeping out of its hole now and then.
 * - `lantern`: 0 dark, 1 lit. Not set: lit from dusk.
 * - `weigh`: a bag weighed on the scale: to 0.5 the bag on its left pan and the beam tipping down that way; then the
 *   weights on the right pan and the beam swinging back level. Not set, 0: empty, level.
 * - `toll`: the miller's toll in the measure: grain heaped in it, how full.
 *
 * Tapped ([pokes]), the cat lashes its tail and wiggles, then pounces at the hole and comes back; the scale's beam tips
 * and swings back, then the other way, a weight hopping in its pan.
 *
 * Closer up ([detail] 2 and 3) the same mill is drawn finer: the boards' grain and the footing's stones, the runner's
 * furrows, the vat's staves and hoops, the cog wheel's pegs, the buckets of the waterwheel, the sacks' weave, the scale's
 * chains, the cat's stripes and whiskers.
 *
 * Mugnaio Franco's mill in Friuli ([look]; companion/SCENES.md "Culture looks") grinds maize for the polenta: golden
 * kernels in the hopper, in the stream poured into it and heaped in the measure, the meal yellow in its sack, in its stream
 * from the spout, spilled round it, round the runner and on the scale's pan (the dust in the air stays white); bunches of
 * cobs hang up to dry from the gallery's edge and on a nail in the right wall; the window looks out on the Collio.
 */
internal class MillPainter : RoomPainter() {
    override val art = "mill"
    override val pokes = listOf(Poke("cat", listOf(1.6, 2.4), rest = 4.0), Poke("scale", listOf(1.4, 1.8), rest = 2.5))

    /** The loft's sacks stand over the walls' tops: room above them. */
    override val rise = 30f

    // the window over the bench on the right wall (y = Y0)
    override val winA = 2.4f; override val winB = 3.6f; override val winZ0 = fl + 10f; override val winZ1 = fl + 17f

    // the platform the stones stand on (its top PZ), by the right wall; the stones' axis at (SX, SY)
    private val pz = fl + 10f
    private val bedTop = pz + 1.2f
    private val vatTop = bedTop + 3f
    private val runTop = vatTop - 0.5f
    // the hopper over them, its bottom HZ0 and its top HZ1
    private val hz0 = pz + 6.8f
    private val hz1 = pz + 12f
    // the cog wheel under the platform, in the plane x = GX (its face toward us at GF), on the shaft at GZ
    private val gz = fl + 0.8f
    // the waterwheel outside, in the plane x = WX, its axle at WZ
    private val wz = 3f
    // the gallery (the loft's floor) along the left wall
    private val gzLoft = fl + 19f
    // the bench under the window
    private val bz = fl + 4.4f

    private val stoneHi = Col.hex(0xB4AEA4); private val stoneL = Col.hex(0x9C968C); private val stoneM = Col.hex(0x848078)
    private val stoneD = Col.hex(0x66625C); private val mortar = Col.hex(0x4E4A44)
    private val boardL = Col.hex(0xB07A48); private val boardM = Col.hex(0x96643A); private val boardD = Col.hex(0x7A5030); private val boardX = Col.hex(0x4E321C)
    private val flourC = Col.hex(0xF6F2EA); private val flourD = Col.hex(0xDCD6CA)
    /** The grain in the hopper, the measure and the stream, and the meal the stones grind of it: the culture's ([readGrain]). */
    private var grainL = GRAIN_L; private var grainM = GRAIN_M; private var grainD = GRAIN_D
    private var mealC = flourC; private var mealD = flourD
    /** Friuli's mill grinds maize ([look]: "grain" "maize"): golden kernels, yellow meal, cobs hanging to dry. */
    private var maize = false
    private val jute = Col.hex(0xC8A872); private val juteL = Col.hex(0xDCC08A); private val juteD = Col.hex(0x9A7E4E)
    private val ironC = Col.hex(0x3E3A40); private val ironHi = Col.hex(0x8A8690)
    private val water = Col.hex(0x5A8EB8); private val waterL = Col.hex(0x9CC4E2); private val waterD = Col.hex(0x3A6488)

    private var raceId = 0

    /** Closer up (detail 2 or 3): the finer touches the scene's own canvas has no room for. */
    private val fine: Boolean get() = detail > 1

    /** The mill is dim inside even by day, so the window's beam shows; at night the lantern is most of the light. */
    override fun ambient(): FloatArray {
        val dim = 0.88f
        val warm = env.dark * 0.06f
        return floatArrayOf(env.ambR * dim + warm, env.ambG * dim + warm * 0.75f, env.ambB * dim + warm * 0.4f)
    }

    /** The race's water and the yard are out under the sky: the rain falls on them. */
    override fun outdoors(id: Int): Boolean = id == 0 || id == groundId || id == raceId

    // ------------------------------------------------------------------ how it turns

    /** How fast the waterwheel and the cog wheel turn (rad/s), as `grind` has it: still, an easy turn, hard. */
    private fun spin(): Float {
        val g = fx("grind") ?: return EASY
        return when { g < 0.12f -> 0f; g < 0.6f -> EASY; else -> HARD }
    }

    /** The wheel's and the cog wheel's angle now; the pinion and the runner turn [GEARING] times as fast. */
    private fun angle(): Float = ((t * spin()) % (2.0 * PI * 12.0)).toFloat()

    override fun paint() {
        fit()
        vignette()
        // the mill's walls are its own; lv only keeps RoomPainter's window plain (no geranium on a mill's sill)
        lv = 1
        readLook()
        readGrain()
        val a = angle()
        val turning = spin() > 0f
        yard()
        tufts(70, 17) { x, y -> (x < x1 + tk + 0.35f && y < y1 + tk + 0.35f) || (x in RACE_A - 0.3f..RACE_B + 0.3f) || (x in 4.2f..6.9f && y in 9.7f..11.5f) }
        race(turning)
        floorBoards()
        rugId = floorId
        millWalls()
        // on the right wall: the window, the sieve high on its peg, the bench under the window with the scale and the measure;
        // the mouse at its hole at the wall's foot left of the bench
        thing("window") { window() }
        thing("sieve", slop = 1) { sieve() }
        thing("mouse", slop = 2) { mouse() }
        thing("bench") { bench() }
        thing("scale", slop = 1) { scale() }
        thing("measure", slop = 1) { measure() }
        // along the left wall: the gallery of the loft on its knee braces, the sacks under it and on it, the cat by them,
        // the ladder up to it, the door and the lantern on its hook by it
        prop { gallery() }
        thing("sack") { sacks() }
        // Friuli's maize cobs hanging to dry from the gallery's edge and on a nail in the right wall
        if (maize) prop { cobs(GAL_X - 0.08f, 4.8f, gzLoft - 0.9f, 7, 3); cobs(4.05f, y0 + 0.12f, fl + 20.5f, 5, 9) }
        thing("cat", slop = 1) { cat() }
        thing("ladder") { ladder() }
        thing("door") { millDoor() }
        thing("lantern", slop = 1) { lantern() }
        for (p in peopleAt("door")) { footShadow(1.9f, 7.5f); personAt(p, 1.9f, 7.5f, fl) }
        // the platform: its back posts, the cog wheel and the pinion under it, its front posts and its top, then the stones,
        // the hopper's frame, the hopper and its grain, the shoe, and the spout down to the flour sack
        prop { posts(back = true) }
        thing("gear") { gear(a) }
        prop { pinion(a * GEARING) }
        prop { posts(back = false); platformTop() }
        thing("millstone") { stones(a * GEARING, turning) }
        prop { horse() }
        thing("hopper") { hopperBack() }
        thing("grain", slop = 1) { grain(turning) }
        thing("hopper") { hopperFront() }
        prop { shoe(turning) }
        pouring()
        prop { spout() }
        for (p in peopleAt("stones")) { footShadow(5.4f, 4.8f); personAt(p, 5.4f, 4.8f, fl) }
        thing("flour", slop = 1) { flour(turning) }
        for (p in peopleAt("sacks")) { footShadow(8.2f, 4.4f); personAt(p, 8.2f, 4.4f, fl, flip = true) }
        // the shaft out through the front-right wall to the waterwheel; the front walls cut down; outside the wheel in its
        // race under the flume, the old millstone in the grass, the race pouring off the plot's front edge
        millStubs()
        thing("shaft") { shaft(a) }
        thing("wheel") { waterwheel(a) }
        if (turning) prop(outline = 0) { wheelWater() }
        prop { trestle() }
        thing("flume") { flume(turning) }
        prop { oldStone() }
        waterfall()
        fireflies(iso.ix(0f, 12f), iso.iy(12f, 12f, 20f), iso.ix(12f, 0f), iso.iy(12f, 12f, 0f), 10, 9)

        lights()
        beam()
        dust()
    }

    // ------------------------------------------------------------------ the yard and the race

    /** The plot and its ground (a method of its own, so its loop over every pixel of the plot compiles on its own). */
    private fun yard() = plot { x, y, px, py -> yardGround(x, y, px, py) }

    /** Grass round the mill, trodden bare along its open sides and on the way to the race; snow in winter. */
    private fun yardGround(x: Float, y: Float, px: Int, py: Int): Int {
        val g = grassAt(x, y, px, py)
        val apron = if (x < x1 + tk + 0.9f && y < y1 + tk + 0.9f) 1f - max(max(0f, x - x1 - tk), max(0f, y - y1 - tk)) / 0.9f else 0f
        val bank = if (x > RACE_A - 0.7f && x < RACE_A) (1f - (RACE_A - x) / 0.7f) * 0.8f else 0f
        val m = max(apron, bank)
        if (m <= 0f) return g
        return dirtAt(px, py, (m * (0.7f + Noise.v2(x * 2f, y * 2f, 3) * 0.5f)).coerceIn(0f, 1f), g)
    }

    /**
     * The mill race along the plot's right side: its far bank's stone wall, the water running toward us with ripples and
     * the light on it (iced along its banks in winter), and the near bank's grass drawn again over the water's edge.
     */
    private fun race(turning: Boolean) {
        raceId = s.newObject(Col.hex(0x203040))
        val wl = RACE_WATER
        // the far bank: dressed stones from the water up to the grass
        onX(RACE_A, 0f, plotD, wl - 0.6f, 0f) { y, z, px, py -> bankStone(y, z, px, py) }
        val flow = (t * (if (turning) 1.6 else 0.9)).toFloat()
        val iceA = if (winter) 0.32f else 0f
        onFlat(wl, RACE_A, 0f, RACE_B, plotD) { x, y, px, py ->
            val edge = min(x - RACE_A, RACE_B - x)
            when {
                winter && edge < iceA + (Noise.v1(y * 3f, 5) - 0.5f) * 0.12f -> if (Dither.at(px, py) < 0.3f) Pal.ICE_L else Pal.ICE_M
                else -> raceWater(x, y, flow, edge, px, py)
            }
        }
        c.penId = 0
        // the near bank's grass over the water's edge (the water lies lower than the bank)
        c.penId = groundId
        onFlat(0f, RACE_B, 0f, plotW, plotD) { x, y, px, py -> rim(yardGround(x, y, px, py), x, y, px, py) }
        c.penId = 0
    }

    /** The race's water at ([x], [y]): running toward us ([flow]), streaks of light along it, darker by the banks. */
    private fun raceWater(x: Float, y: Float, flow: Float, edge: Float, px: Int, py: Int): Int {
        val ripple = sin((y - flow) * 5.5f + x * 2.2f + Noise.v1(y * 0.8f, 9) * 3f)
        return when {
            ripple > 0.92f && Dither.at(px, py) < 0.7f -> env.lit(waterL)
            fine && ripple > 0.985f -> env.lit(Col.mix(waterL, Col.hex(0xFFFFFF), 0.4f))
            edge < 0.12f -> env.lit(waterD)
            ripple < -0.7f && Dither.at(px, py) < 0.4f -> env.lit(Col.mix(water, waterD, 0.5f))
            else -> env.lit(water)
        }
    }

    /** The race's far bank at [a] cells along and [z] px up: courses of grey stones, moss near the water. */
    private fun bankStone(a: Float, z: Float, px: Int, py: Int): Int {
        val course = floor((z + 3f) / 0.9f).toInt()
        val u = a / 0.55f + (course and 1) * 0.5f
        val fa = u - floor(u)
        val col = when {
            fa < 0.08f || (z + 3f) - course * 0.9f < 0.18f -> mortar
            z < RACE_WATER + 0.4f -> Col.mix(stoneM, Col.hex(0x5E7A48), 0.45f)
            Noise.rnd(floor(u).toInt(), course, 7) < 0.4f -> stoneL
            else -> stoneM
        }
        return Col.scale(if (winter && z > -0.3f) Pal.SNOW_M else col, 0.8f)
    }

    /** The race pouring off the plot's front edge, down the earth face, white where it falls, a spray at its foot. */
    private fun waterfall() {
        val fall = (t * 2.4).toFloat()
        prop(outline = 0) {
            onY(plotD, RACE_A, RACE_B, -depth, RACE_WATER) { x, z, px, py ->
                val streak = Noise.v2((x - RACE_A) * 9f, (z + fall * 3f) * 0.35f, 41)
                when {
                    winter && Noise.v1(x * 7f, 13) < 0.45f -> if (z > -2.5f) Pal.ICE_L else 0
                    streak > 0.62f -> env.lit(Col.hex(0xF0F6FA))
                    streak > 0.4f -> env.lit(waterL)
                    else -> env.lit(water)
                }
            }
        }
        if (winter) return
        // the spray where it falls, drifting
        s.fx {
            val cx = iso.sx((RACE_A + RACE_B) / 2f, plotD); val cy = iso.sy((RACE_A + RACE_B) / 2f, plotD, -depth)
            for (j in 0 until 6) {
                val ph = ((t * 0.8 + j / 6.0) % 1.0).toFloat()
                c.ditherCircle(cx + (Noise.rnd(j, 61) - 0.5f) * 8f * K, cy - ph * 4f * K, (1f + ph * 2f) * detail, Col.hex(0xEAF2F8), 0.5f * (1f - ph))
            }
        }
    }

    // ------------------------------------------------------------------ the room

    /** The plank floor, boards running along y, dusted with flour (thickest round the stones and the sack), the cog wheel's pit. */
    private fun floorBoards() {
        floorId = s.newObject(Pal.OUTLINE)
        onFlat(fl, x0, y0, x1, y1) { x, y, px, py -> board(x, y, px, py) }
        // the pit the cog wheel turns in: a dark slot along it, lit at its far lip
        onFlat(fl + 0.02f, GX - 0.24f, SY - GRY - 0.12f, GX + 0.26f, SY + GRY + 0.12f) { x, _, _, _ ->
            if (x < GX - 0.16f) Col.hex(0x6A4A2E) else Col.hex(0x1C1410)
        }
        c.penId = 0
        // the platform's and the sacks' shadows on the floor
        shade(iso.sx(7.45f, 2.6f), iso.sy(7.45f, 2.6f, fl), 15f * K, 4f * K, 0.78f, floorId)
        shade(iso.sx(1.6f, 3.3f), iso.sy(1.6f, 3.3f, fl), 6f * K, 3f * K, 0.75f, floorId)
        shade(iso.sx(3f, 1.5f), iso.sy(3f, 1.5f, fl), 8f * K, 1.6f * K, 0.8f, floorId)
    }

    /** A floor board at ([x], [y]): boards along y with their seams and butt joints, worn, and the flour over them. */
    private fun board(x: Float, y: Float, px: Int, py: Int): Int {
        val w = 0.6f
        val u = (x - x0) / w
        val b = floor(u).toInt(); val f = (u - b) * w
        val v = (y - y0) / 2.9f + Noise.rnd(b, 9) * 3f
        val fy = (v - floor(v)) * 2.9f
        val tone = Noise.rnd(b, 5)
        var col = when {
            f < 0.05f -> boardX
            fy < 0.06f -> boardD
            tone < 0.3f -> Col.mix(boardL, boardM, 0.4f)
            tone > 0.75f -> boardD
            else -> boardM
        }
        if (fine && col != boardX && col != boardD) {
            // the grain along the board, a knot here and there
            val g = Noise.v2(y * 2.4f, b * 3.1f + f * 7f, 31)
            if (abs(g - 0.5f) < 0.035f) col = Col.scale(col, 0.85f)
            if (Noise.rnd(b, floor(v).toInt(), 33) < 0.18f && abs(fy - 1.2f) < 0.12f && abs(f - 0.3f) < 0.08f) col = boardX
        }
        // flour: a haze over everything, thick round the stones and the sack, trodden along the way from the door
        val near = max(0f, 1f - hypot2(x - 7.1f, (y - 4.3f) * 1.2f) / 2.4f) + max(0f, 1f - hypot2(x - 7.4f, y - 2.5f) / 1.9f) * 0.8f
        val haze = 0.12f + 0.55f * near + (Noise.v2(x * 1.4f, y * 1.4f, 35) - 0.5f) * 0.25f
        if (haze + (Dither.at(px, py) - 0.5f) * 0.3f > 0.42f) col = Col.mix(col, flourC, (0.35f + haze * 0.5f).coerceAtMost(0.85f))
        else if (Noise.rnd(px, py, 37) < haze * 0.12f) col = Col.mix(col, flourC, 0.6f)
        return col
    }

    private fun hypot2(a: Float, b: Float): Float = sqrt(a * a + b * b)

    /** The walls: their inner faces (planks over a stone footing), their cut ends and their tops, the plates along them. */
    private fun millWalls() {
        part(Pal.OUTLINE)
        quadFill(x0, y0, fl, x0, y1 + tk, fl, x0, y1 + tk, hw, x0, y0, hw) { px, py ->
            val y = wallXy(px, x0); val z = wallXz(px, py, x0)
            plankWall(y, z, px, py, 0.82f)
        }
        quadFill(x0 - tk, y1 + tk, 0f, x0, y1 + tk, 0f, x0, y1 + tk, hw, x0 - tk, y1 + tk, hw) { px, py -> wallEnd(wallYz(px, py, y1 + tk), px, py, 1f) }
        quadFill(x0 - tk, y0 - tk, hw, x0, y0 - tk, hw, x0, y1 + tk, hw, x0 - tk, y1 + tk, hw) { px, py -> wallTop(flatX(px, py, hw) - (x0 - tk), px, py) }
        c.penId = 0
        part(Pal.OUTLINE)
        quadFill(x0, y0, fl, x1 + tk, y0, fl, x1 + tk, y0, hw, x0, y0, hw) { px, py ->
            val x = wallYx(px, y0); val z = wallYz(px, py, y0)
            plankWall(x + 13f, z, px, py, 1f)
        }
        quadFill(x1 + tk, y0 - tk, 0f, x1 + tk, y0, 0f, x1 + tk, y0, hw, x1 + tk, y0 - tk, hw) { px, py -> wallEnd(wallXz(px, py, x1 + tk), px, py, 0.8f) }
        quadFill(x0 - tk, y0 - tk, hw, x1 + tk, y0 - tk, hw, x1 + tk, y0, hw, x0 - tk, y0, hw) { px, py -> wallTop(flatY(px, py, hw) - (y0 - tk), px, py) }
        c.penId = 0
    }

    /**
     * The wall inside at [a] cells along and [z] px up: a footing of stones up to [FOOTING], then horizontal planks with a
     * dark seam between them, a batten every few cells; flour in the seams low down; [dim] shades the whole face.
     */
    private fun plankWall(a: Float, z: Float, px: Int, py: Int, dim: Float): Int {
        val h = z - fl
        if (h < FOOTING) return Col.scale(footingStone(a, h, px, py), dim)
        val ph = 1.6f
        val k = floor((h - FOOTING) / ph).toInt(); val fz = (h - FOOTING) - k * ph
        val tone = Noise.rnd(k, floor(a / 3.3f + Noise.rnd(k, 3) * 4f).toInt(), 11)
        val batten = abs(((a + 0.6f) / 2.8f) % 1f - 0.5f) * 2.8f < 0.14f
        var col = when {
            batten -> if (fz < 0.4f) boardX else Col.mix(boardM, boardD, 0.3f)
            fz < 0.28f -> boardX
            fz > ph - 0.3f -> Col.mix(boardL, Col.hex(0xE8C090), 0.25f)
            tone < 0.25f -> boardL
            tone > 0.78f -> boardD
            else -> boardM
        }
        if (fine && !batten && fz >= 0.28f) {
            val g = Noise.v2(a * 1.7f, k * 3.3f + fz * 2f, 45)
            if (abs(g - 0.5f) < 0.04f) col = Col.scale(col, 0.86f)
            // the batten's nails
            if (abs(((a + 0.6f) / 2.8f) % 1f - 0.5f) * 2.8f < 0.2f && abs(fz - ph / 2f) < 0.25f) col = ironC
        }
        // flour settled in the seams and on the boards near the floor, cobwebs high in the corner
        if (fz < 0.4f && h < 9f && Noise.rnd(px, py, 47) < 0.5f - h / 20f) col = Col.mix(col, flourC, 0.55f)
        if (h > hw - fl - 5f && a < 1.1f && Noise.rnd(px, py, 49) < 0.08f) col = Col.mix(col, Col.hex(0xD8D4CC), 0.6f)
        return Col.scale(col, dim)
    }

    /** The footing's stones at [a] cells along and [h] px up from the floor: rounded river stones in mortar. */
    private fun footingStone(a: Float, h: Float, px: Int, py: Int): Int {
        val ch = 1.5f
        val course = floor(h / ch).toInt(); val fz = h - course * ch
        val bl = 0.5f + Noise.rnd(course, 51) * 0.3f
        val u = a / bl + Noise.rnd(course, 52) * 3f
        val b = floor(u).toInt(); val fa = (u - b) * bl
        val tone = Noise.rnd(b, course, 53)
        return when {
            fz < 0.3f || fa < 0.07f -> mortar
            fz > ch - 0.35f -> stoneHi
            tone < 0.35f -> stoneL
            tone > 0.75f -> stoneD
            else -> stoneM
        }
    }

    /** A wall's cut end at [z] px up: the planks' ends over the footing's stones in section. */
    private fun wallEnd(z: Float, px: Int, py: Int, dim: Float): Int {
        val col = if (z < fl + FOOTING) (if (Noise.rnd(px / max(1, detail), py / max(1, detail), 55) < 0.5f) stoneM else stoneD)
        else if (((z - fl - FOOTING) / 1.6f) % 1f < 0.18f) boardX else Col.mix(boardL, Col.hex(0xD8A870), 0.3f)
        return Col.scale(col, dim)
    }

    /** A wall's top where it is cut: the planks' top edges, flour on them; snow in winter. */
    private fun wallTop(across: Float, px: Int, py: Int): Int {
        if (winter && Dither.at(px, py) < 0.85f) return Pal.SNOW_L
        return if (across < 0.08f || across > tk - 0.08f) boardL else if (Noise.rnd(px, py, 57) < 0.3f) flourD else boardM
    }

    /** The front walls, cut down: their footing and the bottom plank, the shaft's hole in the right one. */
    private fun millStubs() {
        val sh = FOOTING + 0.8f
        part(Pal.OUTLINE)
        quadFill(x1 + tk, y0, 0f, x1 + tk, y1, 0f, x1 + tk, y1, fl + sh, x1 + tk, y0, fl + sh) { px, py ->
            val y = wallXy(px, x1 + tk); val z = wallXz(px, py, x1 + tk)
            if (abs(y - SY) < 0.3f && abs(z - gz) < 1.6f) Col.hex(0x1E1610) else Col.scale(stub(y, z, px, py), 0.8f)
        }
        quadFill(x1, y0, fl + sh, x1 + tk, y0, fl + sh, x1 + tk, y1, fl + sh, x1, y1, fl + sh) { px, py -> wallTop(flatX(px, py, fl + sh) - x1, px, py) }
        c.penId = 0
        part(Pal.OUTLINE)
        quadFill(x0, y1 + tk, 0f, x1 + tk, y1 + tk, 0f, x1 + tk, y1 + tk, fl + sh, x0, y1 + tk, fl + sh) { px, py -> stub(wallYx(px, y1 + tk), wallYz(px, py, y1 + tk), px, py) }
        quadFill(x1 + tk, y1, 0f, x1 + tk, y1 + tk, 0f, x1 + tk, y1 + tk, fl + sh, x1 + tk, y1, fl + sh) { px, py -> Col.scale(stub(wallXy(px, x1 + tk) + 5f, wallXz(px, py, x1 + tk), px, py), 0.8f) }
        quadFill(x0, y1, fl + sh, x1 + tk, y1, fl + sh, x1 + tk, y1 + tk, fl + sh, x0, y1 + tk, fl + sh) { px, py -> wallTop(flatY(px, py, fl + sh) - y1, px, py) }
        c.penId = 0
    }

    /** A front wall's stub outside at [a] cells along and [z] px up: the stone footing from the ground, the bottom plank on it. */
    private fun stub(a: Float, z: Float, px: Int, py: Int): Int =
        if (z < fl + FOOTING) footingStone(a, z + 1.1f, px, py) else if (z > fl + FOOTING + 0.55f) Col.mix(boardL, Col.hex(0xE8C090), 0.2f) else boardM

    // ------------------------------------------------------------------ the right wall

    /** The sieve on its peg high on the right wall: a round wooden rim, the mesh inside it, the peg. */
    private fun sieve() {
        val yw = y0 + 0.06f
        val r = 0.42f; val zc = fl + 16.6f
        onY(yw, SIEVE_X - r - 0.05f, SIEVE_X + r + 0.05f, zc - r * 4.6f, zc + r * 4.6f) { x, z, px, py ->
            val d = hypot2((x - SIEVE_X) / r, (z - zc) / (r * 4.4f))
            when {
                d > 1f -> 0
                d > 0.8f -> if (z > zc) boardL else boardD
                fine && (Math.floorMod(px, 2) == 0 && Math.floorMod(py, 2) == 0) -> Col.hex(0x6E6A62)
                fine -> Col.hex(0x9E9888)
                Dither.at(px, py) < 0.5f -> Col.hex(0x8A847A)
                else -> Col.hex(0xA8A296)
            }
        }
        iso.px(SIEVE_X, yw + 0.02f, zc + r * 4.6f + 0.6f, boardX)
    }

    /** The mouse at its hole at the right wall's foot: the hole dark in the plank, the mouse grey with pink ears, its tail. */
    private fun mouse() {
        val yw = y0 + 0.03f
        onY(yw, MOUSE_X - 0.18f, MOUSE_X + 0.18f, fl, fl + 1.3f) { x, z, _, _ ->
            val u = (x - MOUSE_X) / 0.18f; val v = (z - fl) / 1.3f
            if (u * u + v * v < 1f) Col.hex(0x140E0A) else 0
        }
        // where the cat's pounce has it (it hides as the cat lands), and its peeping out by itself
        val out = mouseOut(fx("cat"))
        if (out <= 0.02f) return
        val mx = MOUSE_X + 0.12f; val my = y0 + 0.08f + 0.32f * out
        val cx = iso.sx(mx, my); val by = iso.sy(mx, my, fl)
        val p = detail.toFloat()
        val grey = Col.hex(0x8E8A90); val greyD = Col.hex(0x5E5A62); val pink = Col.hex(0xE8A0A8)
        // the tail curling behind it, the body, the head with its ears and the nose twitching
        val tw = sin(t * 9).toFloat() * 0.3f
        c.line(floor(cx - 1.5f * p).toInt(), floor(by - 0.6f * p).toInt(), floor(cx - 4f * p).toInt(), floor(by - (0.2f + tw) * p).toInt(), pink)
        c.fillEllipse(cx, by - 1.3f * p, 1.9f * p, 1.3f * p, greyD)
        c.fillEllipse(cx - 0.2f * p, by - 1.5f * p, 1.6f * p, 1f * p, grey)
        c.fillEllipse(cx + 1.6f * p, by - 1.6f * p, 1f * p, 0.85f * p, grey)
        c.fillRect(floor(cx + 1.1f * p).toInt(), floor(by - 3f * p).toInt(), max(1, detail), max(1, detail), pink)
        c.set(floor(cx + 2.6f * p).toInt(), floor(by - (1.6f + tw * 0.4f) * p).toInt(), pink)
        if (fine) {
            c.set(floor(cx + 1.8f * p).toInt(), floor(by - 2f * p).toInt(), Col.hex(0x101010))
            c.fillRect(floor(cx + 1.6f * p).toInt(), floor(by - 3.3f * p).toInt(), detail - 1, 1, greyD)
        }
    }

    /**
     * How far out of its hole the mouse is (0 in, 1 out by it): with the cat's pounce ([lv]) out until the cat leaps and in
     * before it lands; by itself peeping out and back now and then; tapped, the cat's own pounce sends it in.
     */
    private fun mouseOut(pounce: Float?): Float {
        if (pounce != null) return 1f - PokeArt.ease(pounce, 0.45f, 0.7f)
        val pk = poked("cat")
        if (pk?.step == 1) { val a = pk.age(t); return 1f - PokeArt.ease(a, 0.2f, 0.5f) * (1f - PokeArt.ease(a, 1.8f, 2.4f)) }
        val ph = ((t / 9.0) % 1.0).toFloat()
        return if (ph < 0.65f) 1f else 1f - PokeArt.ease(ph, 0.65f, 0.72f) * (1f - PokeArt.ease(ph, 0.9f, 0.97f))
    }

    /** The miller's bench along the right wall under the window: a thick plank on legs and a stretcher. */
    private fun bench() {
        val bc = y0 + 0.08f; val bd = y0 + 0.82f
        for (lx in floatArrayOf(BENCH_A + 0.15f, (BENCH_A + BENCH_B) / 2f, BENCH_B - 0.15f)) for (ly in floatArrayOf(bc + 0.15f, bd - 0.12f)) iso.post(lx, ly, fl, bz, Pal.WOOD_D)
        rod(BENCH_A + 0.15f, bd - 0.12f, fl + 1.5f, BENCH_B - 0.15f, bd - 0.12f, fl + 1.5f, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        iso.box(BENCH_A, bc, bz, BENCH_B - BENCH_A, bd - bc, 0.9f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        if (fine) grain(BENCH_A, bc, BENCH_B, bd, bz + 0.9f, 5)
        iso.line(BENCH_A, bd, bz + 0.9f, BENCH_B, bd, bz + 0.9f, Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.25f))
        // a dusting of flour on it
        onFlat(bz + 0.92f, BENCH_A + 0.1f, bc + 0.05f, BENCH_B - 0.1f, bd - 0.05f) { _, _, px, py -> if (Noise.rnd(px, py, 59) < 0.18f) flourD else 0 }
    }

    /**
     * The brass beam balance on the bench: a post on its foot, the beam over it, a pan on chains at each end. `weigh` sets a
     * bag of flour on the left pan (the beam tipping down to it), then weights on the right (back level); tapped it tips
     * and swings back, then the other way, a weight hopping.
     */
    private fun scale() {
        val sx0 = SCALE_X; val sy0 = y0 + 0.45f; val top = bz + 0.9f
        val w = fxOn("weigh")
        val pk = poked("scale")
        val pa = pk?.age(t) ?: 0f
        // the beam's tilt: positive drops its left end
        var tilt = when {
            w <= 0.02f -> 0f
            w < 0.5f -> 0.55f * PokeArt.ease(w, 0f, 0.4f)
            else -> 0.55f * (1f - PokeArt.ease(w, 0.5f, 0.95f)) + 0.08f * sin(t * 5).toFloat() * (1f - PokeArt.ease(w, 0.7f, 1f))
        }
        if (pk != null) {
            val d = if (pk.step == 0) 1.4f else 1.8f
            val s0 = if (pk.step == 0) 1f else -1f
            tilt += s0 * 0.5f * sin(pa * 7f) * (1f - pa / d).coerceIn(0f, 1f)
        }
        val brass = Col.hex(0xD9A441); val brassL = Col.hex(0xF6D27A); val brassD = Col.hex(0x96692A)
        // the foot and the post
        iso.box(sx0 - 0.18f, sy0 - 0.14f, top, 0.36f, 0.28f, 0.6f, brassL, brass, brassD)
        val postTop = top + 6f
        iso.post(sx0, sy0, top + 0.6f, postTop, brassD)
        if (fine) iso.line(sx0, sy0, top + 0.6f, sx0, sy0, postTop, brassL)
        // the beam, along x, tipping
        val half = 0.62f
        val lz = postTop + tilt * 2.2f; val rz = postTop - tilt * 2.2f
        rod(sx0 - half, sy0, lz, sx0 + half, sy0, rz, brass, brassL, brass, brassD)
        iso.px(sx0, sy0, postTop + 0.6f, brassL)
        // the pans on their chains
        for ((side, ez) in listOf(-1f to lz, 1f to rz)) {
            val ex = sx0 + side * half
            val panZ = ez - 3.3f
            val hx = iso.ix(ex, sy0); val hy0 = iso.iy(ex, sy0, ez); val hy1 = iso.iy(ex, sy0, panZ)
            if (fine) chain(hx, hy0, hy1, brassL, brassD) else c.vline(hx, hy0, hy1, brassD)
            val pcx = iso.sx(ex, sy0); val pcy = iso.sy(ex, sy0, panZ)
            c.fillEllipse(pcx, pcy, 2.6f * detail, 1.1f * detail, brassD)
            c.fillEllipse(pcx - 0.3f * detail, pcy - 0.3f * detail, 2.2f * detail, 0.8f * detail, brass)
            if (fine) c.hline(floor(pcx - 1.6f * detail).toInt(), floor(pcx).toInt(), floor(pcy - 0.6f * detail).toInt(), brassL)
            // what lies in it: the bag of flour on the left, the weights on the right
            if (side < 0f && w > 0.05f) {
                val bh = 2.6f * PokeArt.ease(w, 0.05f, 0.3f)
                c.fillEllipse(pcx, pcy - bh * detail, 1.8f * detail, bh * detail, mealD)
                c.fillEllipse(pcx - 0.4f * detail, pcy - bh * 1.2f * detail, 1.2f * detail, bh * 0.7f * detail, mealC)
                c.set(floor(pcx).toInt(), floor(pcy - bh * 2f * detail).toInt(), juteD)
            }
            if (side > 0f) {
                val n = if (w >= 0.5f) 1 + (PokeArt.ease(w, 0.55f, 0.85f) * 1.99f).toInt() else 0
                val hop = if (pk?.step == 1 && pa < 1.2f) (abs(sin(pa * 9f)) * 2f * (1f - pa / 1.2f)) else 0f
                for (k in 0 until n + (if (pk?.step == 1 && n == 0) 1 else 0)) {
                    val wy = pcy - (1f + k * 1.4f + hop) * detail
                    c.fillRect(floor(pcx - 1f * detail).toInt(), floor(wy - detail).toInt(), 2 * detail, detail + max(1, detail / 2), brassD)
                    c.fillRect(floor(pcx - 1f * detail).toInt(), floor(wy - detail).toInt(), 2 * detail, max(1, detail / 2), brassL)
                }
            }
        }
    }

    /** The miller's measure (merica) on the bench: a square wooden box with an iron band; `toll` heaps it with grain. */
    private fun measure() {
        val mx = MEASURE_X; val my = y0 + 0.45f; val top = bz + 0.9f
        val hw2 = 0.3f; val mh = 2.8f
        iso.box(mx - hw2, my - hw2, top, hw2 * 2f, hw2 * 2f, mh, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        iso.line(mx - hw2, my + hw2, top + mh * 0.6f, mx + hw2, my + hw2, top + mh * 0.6f, ironC)
        iso.line(mx + hw2, my - hw2, top + mh * 0.6f, mx + hw2, my + hw2, top + mh * 0.6f, ironC)
        if (fine) iso.line(mx - hw2, my + hw2, top + mh, mx + hw2, my + hw2, top + mh, Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.25f))
        // inside: dark, or the grain filling it and heaped over its rim
        val toll = fxOn("toll")
        iso.top(mx - hw2 + 0.05f, my - hw2 + 0.05f, top + mh, hw2 * 2f - 0.1f, hw2 * 2f - 0.1f, if (toll > 0.05f) grainM else Pal.WOOD_X)
        if (toll > 0.05f) {
            val cx = iso.sx(mx, my); val cy = iso.sy(mx, my, top + mh)
            val hh = (0.6f + 3f * toll) * detail
            val rx = 3.2f * detail
            c.fillEllipse(cx, cy - hh * 0.35f, rx, hh * 0.55f + detail, grainD)
            c.fillEllipse(cx - 0.3f * detail, cy - hh * 0.45f, rx - detail, hh * 0.5f, grainM)
            c.fillEllipse(cx - 0.8f * detail, cy - hh * 0.65f, rx * 0.55f, hh * 0.3f, grainL)
            if (fine) for (k in 0 until 8) c.set(floor(cx + (Noise.rnd(k, 63) - 0.5f) * 5f * detail).toInt(), floor(cy - Noise.rnd(k, 64) * hh).toInt(), grainD)
        }
    }

    // ------------------------------------------------------------------ the left wall

    /** The loft's gallery along the left wall: a deck of planks on joists, knee braces from the wall under it. */
    private fun gallery() {
        val xa = x0; val xb = GAL_X; val ya = y0; val yb = GAL_Y; val z = gzLoft
        // the knee braces from the wall to the deck's front edge
        for (by in floatArrayOf(2.0f, 4.6f)) {
            rod(x0 + 0.02f, by, z - 5f, xb - 0.1f, by, z - 0.2f, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        }
        // the joist under the front edge, the deck and its front faces
        iso.box(xa, ya, z - 0.9f, xb - xa, yb - ya, 1f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        onFlat(z + 0.1f, xa, ya, xb, yb) { x, y, px, py ->
            val k = floor((y - ya) / 0.55f).toInt()
            val f = (y - ya) - k * 0.55f
            when {
                f < 0.05f -> boardX
                Noise.rnd(px, py, 65) < 0.12f -> flourD
                Noise.rnd(k, 66) < 0.4f -> boardL
                else -> boardM
            }
        }
        iso.line(xb, ya, z + 0.1f, xb, yb, z + 0.1f, Col.mix(boardL, Col.hex(0xFFFFFF), 0.25f))
    }

    /**
     * The sacks of grain: three standing along the left wall under the gallery, a fourth lying across the back two; up on the
     * gallery two more lying along it.
     */
    private fun sacks() {
        // up on the gallery, lying along it
        for ((k, sy) in floatArrayOf(2.3f, 3.85f).withIndex()) lyingSack(1.55f, sy, gzLoft + 0.1f, 1.3f, k)
        for ((k, sy) in floatArrayOf(2.45f, 3.2f, 3.95f).withIndex()) grainSack(1.6f, sy, fl, 8.6f + (k % 2) * 0.8f, k)
        lyingSack(1.6f, 2.85f, fl + 8.8f, 1.4f, 7)
    }

    /**
     * A jute sack of grain standing at world ([x], [y]) on [z], [hgt] px tall: a soft bulging body, its neck tied with a
     * cord, the weave closer up; a little flour dust on its shoulder.
     */
    private fun grainSack(x: Float, y: Float, z: Float, hgt: Float, seed: Int) {
        val mx = iso.sx(x, y); val base = iso.sy(x, y, z)
        val p = detail.toFloat()
        val r = 0.36f
        val rx = r * 11.3f * detail
        lathe(mx, base, hgt, { h ->
            val f = h / hgt
            (rx / p) * when {
                f < 0.12f -> 0.82f + f * 1.5f
                f < 0.7f -> 1f
                f < 0.86f -> 1f - (f - 0.7f) * 2.6f
                else -> 0.5f
            }
        }) { u, du, h, xx, yy ->
            val f = h / hgt
            val col = when {
                f > 0.86f -> if (abs(f - 0.9f) < 0.03f) juteD else juteL
                u < -0.6f -> juteL
                u > 0.55f -> juteD
                else -> jute
            }
            when {
                fine && (((xx shr 1) + (yy shr 1)) and 1) == 0 -> Col.mix(col, juteL, 0.15f)
                fine && abs(u - 0.7f) < du && f in 0.1f..0.85f -> Col.scale(juteD, 0.85f)
                f in 0.62f..0.72f && Noise.rnd(xx, yy, 67 + seed) < 0.35f -> Col.mix(col, flourC, 0.5f)
                else -> col
            }
        }
        // the tied neck's tuft
        c.fillEllipse(mx, base - hgt * p - 0.6f * p, rx * 0.45f, 0.9f * p, juteL)
        c.set(floor(mx).toInt(), floor(base - hgt * p - 1.2f * p).toInt(), juteD)
    }

    /** A sack lying along y at world ([x], [y]) on [z]: a long rounded bundle, [len] cells long, tied at its far end. */
    private fun lyingSack(x: Float, y: Float, z: Float, len: Float, seed: Int) {
        val ya = y - len / 2f; val yb = y + len / 2f
        val hh = 2.6f
        onY(yb, x - 0.4f, x + 0.4f, z, z + hh) { xx, zz, px, py ->
            val u = (xx - x) / 0.4f; val v = (zz - z) / hh
            if (u * u + (v - 0.5f) * (v - 0.5f) * 4f > 1f) 0 else if (u > 0.4f) juteD else if (v > 0.7f) juteL else jute
        }
        onX(x + 0.4f, ya, yb, z, z + hh * 0.85f) { yy, zz, px, py ->
            val v = (zz - z) / (hh * 0.85f)
            if (fine && (((px shr 1) + (py shr 1)) and 1) == 0) Col.mix(juteD, jute, 0.4f) else if (v > 0.7f) jute else juteD
        }
        onFlat(z + hh * 0.85f, x - 0.35f, ya, x + 0.4f, yb) { _, yy, px, py ->
            if (Noise.rnd(px, py, 69 + seed) < 0.1f) Col.mix(juteL, flourC, 0.5f) else juteL
        }
        iso.px(x, ya - 0.05f, z + hh * 0.6f, juteD)
    }

    /**
     * The cat by the sacks: crouched, watching the mouse at its hole, its tail flicking; as `cat` has it, wiggling, leaping
     * and landed at the hole (sitting there, the mouse gone); tapped, it lashes its tail and wiggles, then pounces and comes
     * back.
     */
    private fun cat() {
        val pounce = fx("cat")
        val pk = poked("cat")
        // where it is between its crouch and the hole, how high up in its leap, and how much it wiggles
        var leap = 0f; var wiggle = 0f; var landed = false
        when {
            pounce != null -> {
                wiggle = if (pounce < 0.4f) PokeArt.ease(pounce, 0f, 0.35f) else 0f
                leap = PokeArt.ease(pounce, 0.38f, 0.75f)
                landed = pounce >= 0.75f
            }
            pk?.step == 0 -> wiggle = sin(pk.age(t) / 1.6f * PI.toFloat()).coerceAtLeast(0f)
            pk?.step == 1 -> {
                val a = pk.age(t)
                leap = if (a < 1.2f) PokeArt.ease(a, 0.1f, 0.55f) else 1f - PokeArt.ease(a, 1.3f, 2.2f)
                landed = a in 0.55f..1.3f
            }
        }
        val x = CAT_X + (CAT_TO_X - CAT_X) * leap; val y = CAT_Y + (CAT_TO_Y - CAT_Y) * leap
        val lift = sin(leap * PI.toFloat()) * 5f
        val cx = iso.sx(x, y); val by = iso.sy(x, y, fl + lift)
        if (lift < 0.3f) shade(cx, iso.sy(x, y, fl), 3.6f * detail, 1.1f * detail, 0.78f, floorId)
        when {
            landed -> catSitting(cx, by)
            leap > 0.02f -> catSide(cx, by, 1f, RUN)
            else -> catCrouch(cx, by, wiggle)
        }
    }

    /**
     * The cat crouched low, facing right toward the mouse, its belly near the floor, the rear up a little and wiggling as it
     * readies ([wiggle] 0..1), the tail flicking.
     */
    private fun catCrouch(cx: Float, by: Float, wiggle: Float) {
        val p = detail.toFloat()
        fun x(dx: Float) = cx + dx * p
        fun y(dy: Float) = by + dy * p
        // readying: the rear up and swaying, the tail lashing from side to side
        val shake = wiggle * sin(t * 22).toFloat() * 1.3f
        val rear = wiggle * 1.2f
        val lash = sin(t * (3.1 + 9.0 * wiggle)).toFloat() * (1f + 2.4f * wiggle)
        tail(x(-3.2f + shake), y(-2.4f - rear), x(-5.2f), y(-3.2f + lash * 0.6f - rear), x(-6.4f + lash * 0.4f), y(-1.6f + lash - rear))
        // the far legs, the body low and long, the near legs folded under
        limb(x(-2.2f + shake), y(-1.6f - rear), x(-2.6f), y(-0.1f), 0.95f * p, catD)
        limb(x(1.8f), y(-1.2f), x(2.6f), y(-0.1f), 0.95f * p, catD)
        c.fillEllipse(x(-0.4f + shake * 0.5f), y(-2.0f - rear * 0.6f), 3.4f * p, 1.35f * p, catO)
        c.fillEllipse(x(-1.8f + shake), y(-2.2f - rear), 1.6f * p, 1.2f * p, catO)
        c.fillEllipse(x(-0.2f), y(-1.2f), 2.6f * p, 0.7f * p, catL)
        if (fine) for (k in -1..1) limb(x(-0.6f + k * 1.1f), y(-3.2f - rear * 0.5f), x(-0.9f + k * 1.1f), y(-2.3f - rear * 0.5f), 1f, catD)
        else c.set(floor(x(-0.5f)).toInt(), floor(y(-3f)).toInt(), catD)
        limb(x(-1.6f + shake), y(-1.4f - rear), x(-1.2f), y(-0.1f), 0.95f * p, catO)
        limb(x(2.2f), y(-1f), x(3.2f), y(-0.1f), 0.95f * p, catO)
        c.fillEllipse(x(3.4f), y(-0.35f), 0.6f * p, 0.35f * p, catL)
        catHead(x(3.6f), y(-2.7f), 1f, 0)
    }

    /** The cat sitting up where it landed, by the hole, as the kitchen's cat sits on its bench. */
    private fun catSitting(cx: Float, by: Float) {
        if (fine) { catFine(cx, by, catO, catD, catL, shut = false, sit = true); return }
        val ix = floor(cx).toInt(); val iy = floor(by).toInt()
        val flick = (sin(t * 2.3) > 0.4).let { if (it) 1 else 0 }
        c.fillEllipse(ix + 0.5f, iy - 3f, 3.2f, 3f, catO)
        c.vline(ix - 2, iy - 5, iy - 1, catL); c.set(ix + 1, iy - 4, catD); c.set(ix + 2, iy - 2, catD)
        c.fillEllipse(ix + 0.5f, iy - 7.5f, 2.4f, 2f, catO)
        c.set(ix - 1, iy - 10, catO); c.set(ix + 2, iy - 10, catO); c.set(ix - 1, iy - 9, catD); c.set(ix + 2, iy - 9, catD)
        c.set(ix - 1, iy - 8, Col.hex(0x3A5A2A)); c.set(ix + 1, iy - 8, Col.hex(0x3A5A2A))
        c.line(ix + 3, iy - 1, ix + 6, iy - 2 - flick, catD); c.set(ix + 6, iy - 3 - flick, catO)
    }

    /** The ladder up to the gallery: two rails leaning from the floor to the gallery's front end, rungs between them. */
    private fun ladder() {
        val ya = LADDER_FOOT; val yb = GAL_Y + 0.06f; val za = fl; val zb = gzLoft + 1.8f
        for ((k, lx) in floatArrayOf(LADDER_A, LADDER_B).withIndex()) {
            rod(lx, ya, za, lx, yb, zb, if (k == 0) Pal.WOOD_L else Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
            if (fine) rod(lx + 0.06f, ya, za, lx + 0.06f, yb, zb, Pal.WOOD_D, Pal.WOOD_D, Pal.WOOD_D, Pal.WOOD_D)
        }
        val n = 7
        for (k in 1..n) {
            val f = k / (n + 1f)
            val ry = ya + (yb - ya) * f; val rz = za + (zb - za) * f
            rod(LADDER_A, ry, rz, LADDER_B, ry, rz, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
            if (Noise.rnd(k, 71) < 0.5f) iso.px(LADDER_A + 0.25f, ry, rz + 0.4f, flourD)
        }
    }

    /** The plank door in the left wall: boards, two ledges, iron strap hinges, a latch; a line of daylight under it by day. */
    private fun millDoor() {
        val xd = x0 + 0.03f
        onX(xd, DOOR_A - 0.15f, DOOR_B + 0.15f, fl, DOOR_Z + 0.9f) { y, z, px, py ->
            val fa = y - DOOR_A; val fb = DOOR_B - y; val ft = DOOR_Z - z
            if (fa < 0f || fb < 0f || ft < 0f) return@onX if (fa < -0.08f || fb < -0.08f || ft < -0.6f) Pal.WOOD_X else Pal.WOOD_D
            val h = z - fl
            val bw = 0.3f
            val board = floor(fa / bw).toInt(); val fbd = fa - board * bw
            val ledge = h in 3f..4.4f || h in 14f..15.4f
            val hinge = fb < 0.55f && (abs(h - 3.7f) < 0.35f || abs(h - 14.7f) < 0.35f)
            when {
                h < 0.5f && env.dark < 0.5f -> Col.hex(0xFFF0C0)
                hinge -> if (fine && abs(h - 3.7f) < 0.1f || fine && abs(h - 14.7f) < 0.1f) ironHi else ironC
                abs(fa - 0.22f) < 0.07f && abs(h - 9f) < 1f -> ironC
                ledge -> if (h < 3.3f || (h > 14f && h < 14.3f)) Pal.WOOD_X else Col.mix(Pal.WOOD_L, Pal.WOOD_M, 0.4f)
                fbd < 0.05f -> Pal.WOOD_X
                fine && Noise.v2(board * 3.1f, h * 0.5f, 73) > 0.72f -> Col.scale(Pal.WOOD_M, 0.88f)
                else -> if (Noise.rnd(board, 75) < 0.5f) Pal.WOOD_M else Col.mix(Pal.WOOD_M, Pal.WOOD_L, 0.25f)
            }
        }
        iso.box(xd, DOOR_A - 0.08f, fl, 0.28f, DOOR_B - DOOR_A + 0.16f, 0.45f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
    }

    /**
     * The lantern on its hook on the left wall by the door, where it is taken down to go out: an iron frame, glass panes, a
     * ring on top; lit (from dusk, or as `lantern` has it) the flame and its glow.
     */
    private fun lantern() {
        val lx = x0 + 0.12f; val ly = LANTERN_Y
        val hook = LANTERN_Z + 1.4f; val topZ = LANTERN_Z; val botZ = topZ - 3.6f
        val cx = iso.ix(lx, ly); val hy = iso.iy(lx, ly, hook); val ty = iso.iy(lx, ly, topZ); val byy = iso.iy(lx, ly, botZ)
        val lit = lanternLit()
        val d = detail
        // the hook in the wall, the ring
        iso.px(x0 + 0.03f, ly, hook + 0.4f, ironC)
        c.vline(cx, hy, ty, ironC)
        if (fine) c.vline(cx + 1, hy, ty, ironHi)
        // the cap and the ring, the frame, the panes, the base
        c.fillRect(cx - 2 * d, ty, 5 * d, d, ironC)
        c.fillRect(cx - d, ty - d, 3 * d, d, ironHi)
        val gTop = ty + d; val gBot = byy - d
        for (yy in gTop until gBot) for (xx in cx - 2 * d until cx + 3 * d) {
            val edge = xx < cx - d || xx >= cx + 2 * d
            val col = when {
                edge -> ironC
                lit -> if ((yy - gTop) * 2 > (gBot - gTop)) Pal.FLAME[1] else Pal.WINDOW_LIT
                else -> if (xx < cx) Col.hex(0xB8C8D4) else Col.hex(0x8EA0AE)
            }
            if (lit && !edge) glow { c.set(xx, yy, col) } else c.set(xx, yy, col)
        }
        if (lit) glow { c.fillRect(cx, (gTop + gBot) / 2, d, max(1, (gBot - gTop) / 3), Pal.FLAME[0]) }
        c.fillRect(cx - 2 * d, gBot, 5 * d, d, ironC)
        if (fine) c.fillRect(cx - 2 * d, gBot, 5 * d, 1, ironHi)
    }

    /** Whether the lantern burns: as `lantern` has it, else from dusk. */
    private fun lanternLit(): Boolean = fx("lantern")?.let { it > 0.5f } ?: (env.windows > 0.35f)

    // ------------------------------------------------------------------ the platform, the stones, the gears

    /** The platform's posts: the two at the back (against the wall), or the two at the front; a beam along the front's top. */
    private fun posts(back: Boolean) {
        val py0 = if (back) PLAT_C + 0.1f else PLAT_D - 0.1f
        for (px0 in floatArrayOf(PLAT_A + 0.1f, PLAT_B - 0.1f)) iso.box(px0 - 0.12f, py0 - 0.12f, fl, 0.24f, 0.24f, pz - 0.8f - fl, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        if (!back) {
            // a cross beam low between the front posts, where the spindle's bearing sits, and the beam along the top
            iso.box(PLAT_A, PLAT_D - 0.18f, pz - 1.8f, PLAT_B - PLAT_A, 0.18f, 1f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        }
    }

    /** The platform's top: thick planks along x, their front and right edges, flour over them. */
    private fun platformTop() {
        val z0 = pz - 0.8f
        iso.box(PLAT_A, PLAT_C, z0, PLAT_B - PLAT_A, PLAT_D - PLAT_C, 0.8f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        onFlat(pz + 0.01f, PLAT_A, PLAT_C, PLAT_B, PLAT_D) { x, y, px, py ->
            val k = floor((y - PLAT_C) / 0.56f).toInt(); val f = (y - PLAT_C) - k * 0.56f
            val flour = Noise.v2(x * 2f, y * 2f, 77) + (Dither.at(px, py) - 0.5f) * 0.3f
            when {
                f < 0.05f -> boardX
                flour > 0.55f -> flourD
                Noise.rnd(k, 78) < 0.5f -> boardL
                else -> boardM
            }
        }
        iso.line(PLAT_A, PLAT_D, pz, PLAT_B, PLAT_D, pz, Col.mix(boardL, Col.hex(0xFFFFFF), 0.25f))
    }

    /**
     * The cog wheel on the shaft under the platform, in the plane x = GX, its face toward us: a rim of boards, six arms, the
     * hub round the shaft, wooden pegs round the rim; turning by [a]; its lower half down in its pit (not drawn under the floor).
     */
    private fun gear(a: Float) {
        val gf = GX + 0.18f
        val rr = GR
        onX(gf, SY - GRY - 0.3f, SY + GRY + 0.3f, fl, gz + rr + 1.4f) { y, z, px, py ->
            val dy = (y - SY) * CELL_Z; val dz = z - gz
            val r = sqrt(dy * dy + dz * dz)
            val ang = atan2(dz, dy) - a
            when {
                r > rr + 1.2f -> 0
                r > rr -> {
                    // a peg: a short block standing out of the rim every 1/16 turn
                    val k = ((ang / (2f * PI.toFloat()) * PEGS) % 1f + 1f) % 1f
                    if (abs(k - 0.5f) < 0.2f) (if (dz > 0f) Pal.WOOD_L else Pal.WOOD_M) else 0
                }
                r > rr - 1.1f -> if (fine && abs(r - rr + 0.55f) < 0.12f) Pal.WOOD_X else if (dz + dy * 0.3f > 0f) boardL else boardM
                r < 0.9f -> if (r < 0.45f) Pal.WOOD_X else ironC
                else -> {
                    // the six arms
                    val k = ((ang / (2f * PI.toFloat()) * 6f) % 1f + 1f) % 1f
                    val w = abs(k - 0.5f) * (2f * PI.toFloat() / 6f) * r
                    if (w < 0.6f) (if (w < 0.25f && fine) boardL else boardM) else 0
                }
            }
        }
    }

    /** The lantern pinion on the stones' spindle, above the cog wheel's top: two discs and the staves between them, turning by [a]. */
    private fun pinion(a: Float) {
        val px0 = GX - 0.42f
        val z0 = gz + GR - 0.6f; val z1 = z0 + 2.4f
        val cx = iso.sx(px0, SY); val y0p = iso.sy(px0, SY, z0); val y1p = iso.sy(px0, SY, z1)
        val rx = 0.3f * 11.3f * detail; val ry = rx * 0.5f
        // the spindle up to the platform's underside
        iso.post(px0, SY, z1, pz - 0.8f, ironC)
        c.fillEllipse(cx, y0p, rx, ry, Pal.WOOD_D)
        for (k in 0 until 6) {
            val ang = a + k * PI.toFloat() / 3f
            val sxp = cx + cos(ang) * rx * 0.85f
            if (sin(ang) < -0.2f) continue
            c.vline(floor(sxp).toInt(), floor(y1p).toInt(), floor(y0p).toInt(), if (cos(ang) < 0f) Pal.WOOD_L else Pal.WOOD_M)
        }
        c.fillEllipse(cx, y1p, rx, ry, Pal.WOOD_M)
        c.fillEllipse(cx - 0.3f * detail, y1p - 0.2f * detail, rx * 0.7f, ry * 0.6f, Pal.WOOD_L)
    }

    /**
     * The millstones on the platform: the bed stone's grey rim under the vat, the vat's staves and iron hoops, its lid with the
     * opening where the runner's grooved top shows, turning by [a] while it grinds; the eye in its middle.
     */
    private fun stones(a: Float, turning: Boolean) {
        val cx = iso.sx(SX, SY); val p = detail.toFloat()
        val rb = 1.12f * 11.3f * detail
        // the bed stone
        drum(cx, iso.sy(SX, SY, pz), iso.sy(SX, SY, bedTop), rb, rb * 0.5f, stoneL, stoneM, stoneD, stoneHi)
        c.fillEllipse(cx, iso.sy(SX, SY, bedTop), rb, rb * 0.5f, stoneL)
        // the vat: staves round it, two hoops
        val rv = 1.0f * 11.3f * detail
        val vb = iso.sy(SX, SY, bedTop); val vt = iso.sy(SX, SY, vatTop)
        for (xx in floor(cx - rv).toInt()..ceil(cx + rv).toInt()) {
            val u = (xx + 0.5f - cx) / rv
            if (abs(u) > 1f) continue
            val e = sqrt(1f - u * u) * rv * 0.5f
            val stave = floor((u + 1f) * 4.5f).toInt()
            for (yy in floor(vt + e).toInt()..floor(vb + e).toInt()) {
                val h = (vb + e - yy) / (vb - vt)
                val col = when {
                    abs(h - 0.22f) < 0.08f || abs(h - 0.8f) < 0.08f -> if (u < -0.3f) ironHi else ironC
                    ((u + 1f) * 4.5f) % 1f < 0.12f -> boardX
                    u < -0.55f -> boardL
                    u > 0.5f -> boardD
                    stave % 2 == 0 -> boardM
                    else -> Col.mix(boardM, boardD, 0.35f)
                }
                c.set(xx, yy, col)
            }
        }
        // the lid: a ring of planks, the opening, the runner inside
        c.fillEllipse(cx, vt, rv, rv * 0.5f, boardL)
        if (fine) c.fillEllipse(cx, vt + 0.5f * p, rv - 0.6f * p, rv * 0.5f - 0.4f * p, boardM)
        val ro = 0.62f * 11.3f * detail
        val rt = iso.sy(SX, SY, runTop)
        c.fillEllipse(cx, vt + 0.3f * p, ro + 0.5f * p, ro * 0.5f + 0.3f * p, boardX)
        c.fillEllipse(cx, rt, ro, ro * 0.5f, stoneM)
        // the runner's furrows, turning
        val n = 8
        for (k in 0 until n) {
            val ang = a + k * 2f * PI.toFloat() / n
            val ex = cx + cos(ang) * ro * 0.95f; val ey = rt + sin(ang) * ro * 0.5f * 0.95f
            val sx0 = cx + cos(ang + 0.5f) * ro * 0.25f; val sy0 = rt + sin(ang + 0.5f) * ro * 0.125f
            c.line(floor(sx0).toInt(), floor(sy0).toInt(), floor(ex).toInt(), floor(ey).toInt(), if (sin(ang) < 0f) stoneD else Col.mix(stoneD, stoneM, 0.4f))
        }
        // flour round the runner's edge, the eye dark with grain going in
        if (turning) for (k in 0 until 10) {
            val ang = a * 0.5f + k * 0.63f
            c.blend(floor(cx + cos(ang) * ro * 0.98f).toInt(), floor(rt + sin(ang) * ro * 0.49f).toInt(), mealC, 0.8f)
        }
        c.fillEllipse(cx, rt, 1.2f * p, 0.6f * p, Col.hex(0x2E2A26))
        if (turning) c.set(floor(cx).toInt(), floor(rt).toInt(), grainM)
    }

    /** The hopper's frame (the horse) on the platform: four legs round the vat and a crossbar on each side under the hopper. */
    private fun horse() {
        val o = 0.86f
        for ((lx, ly) in listOf(SX - o to SY - o, SX + o to SY - o, SX - o to SY + o, SX + o to SY + o)) iso.post(lx, ly, pz, hz0 + 0.8f, Pal.WOOD_D)
        for (ly in floatArrayOf(SY - o, SY + o)) rod(SX - o, ly, hz0 + 0.5f, SX + o, ly, hz0 + 0.5f, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
    }

    /** The inside of the hopper's two back walls, in shadow, seen over its front ones: drawn before the grain lies in it. */
    private fun hopperBack() {
        val b = HOP_B; val tt = HOP_T
        val dark = Col.scale(boardD, 0.62f)
        iso.quad(SX - b, SY - b, hz0, SX + b, SY - b, hz0, SX + tt, SY - tt, hz1, SX - tt, SY - tt, hz1, dark)
        iso.quad(SX - b, SY - b, hz0, SX - b, SY + b, hz0, SX - tt, SY + tt, hz1, SX - tt, SY - tt, hz1, Col.scale(boardD, 0.5f))
    }

    /**
     * The hopper's front: the two walls toward us (the front, +y, and the right, +x), wider at the top, boards across them with
     * dark seams and a lit edge under each, and the rim round its top.
     */
    private fun hopperFront() {
        val b = HOP_B; val tt = HOP_T
        iso.quad(SX - b, SY + b, hz0, SX + b, SY + b, hz0, SX + tt, SY + tt, hz1, SX - tt, SY + tt, hz1, boardM)
        iso.quad(SX + b, SY - b, hz0, SX + b, SY + b, hz0, SX + tt, SY + tt, hz1, SX + tt, SY - tt, hz1, boardD)
        for (k in 1..3) {
            val f = k / 4f
            val z = hz0 + (hz1 - hz0) * f; val hh = b + (tt - b) * f
            iso.line(SX - hh, SY + hh, z, SX + hh, SY + hh, z, boardX)
            iso.line(SX + hh, SY - hh, z, SX + hh, SY + hh, z, Col.scale(boardX, 0.85f))
            if (fine) iso.line(SX - hh, SY + hh, z - 0.5f, SX + hh, SY + hh, z - 0.5f, boardL)
        }
        // the rim: the four top edges, the front ones lit
        iso.line(SX - tt, SY - tt, hz1, SX + tt, SY - tt, hz1, boardM)
        iso.line(SX - tt, SY - tt, hz1, SX - tt, SY + tt, hz1, boardM)
        iso.line(SX - tt, SY + tt, hz1, SX + tt, SY + tt, hz1, boardL)
        iso.line(SX + tt, SY - tt, hz1, SX + tt, SY + tt, hz1, boardL)
        if (fine) iso.line(SX - tt, SY + tt, hz1 - 0.5f, SX + tt, SY + tt, hz1 - 0.5f, Col.mix(boardL, Col.hex(0xFFFFFF), 0.2f))
    }

    /** How full the hopper is (0..1): as `pour` has it, else half full. */
    private fun hopperFull(): Float = fx("pour") ?: 0.5f

    /** The grain in the hopper, its golden surface at the level [hopperFull] has it, and the trickle down its middle while it grinds. */
    private fun grain(turning: Boolean) {
        val full = hopperFull()
        // a hint of grain even when it is near empty: the last of it round the hopper's throat
        val gl = hz0 + 1.2f + (hz1 - hz0 - 1.9f) * full.coerceIn(0.04f, 1f)
        val half = HOP_B + (HOP_T - HOP_B) * ((gl - hz0) / (hz1 - hz0)) - 0.06f
        val sink = if (turning) (t * 0.6).toFloat() else 0f
        onFlat(gl, SX - half, SY - half, SX + half, SY + half) { x, y, px, py ->
            val d = hypot2(x - SX, y - SY)
            val g = Noise.v2((x + sink * 0.1f) * (if (maize) 6f else 9f), (y - sink * 0.1f) * (if (maize) 6f else 9f), 81)
            when {
                // the dimple over the throat where it runs down
                turning && d < 0.16f -> grainD
                g > 0.68f -> grainL
                g < 0.3f -> grainD
                fine && Noise.rnd(px, py, 83) < 0.12f -> Col.hex(0xFFE08A)
                else -> grainM
            }
        }
    }

    /** The shoe under the hopper: a little trough shaking the grain down into the runner's eye while it grinds. */
    private fun shoe(turning: Boolean) {
        val shake = if (turning) sin(t * (if (spin() >= HARD) 30.0 else 18.0)).toFloat() * 0.06f else 0f
        val xa = SX + shake; val ya = SY + 0.28f; val za = hz0 - 0.3f
        val xb = SX + shake; val yb = SY + 0.02f; val zb = runTop + 0.7f
        rod(xa - 0.12f, ya, za, xb - 0.12f, yb, zb, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        rod(xa + 0.12f, ya, za, xb + 0.12f, yb, zb, Pal.WOOD_D, Pal.WOOD_M, Pal.WOOD_D, Pal.WOOD_D)
        if (turning && hopperFull() > 0.02f) {
            // the trickle of grain off its lip into the eye
            val gx = iso.ix(xb, yb); val gy0 = iso.iy(xb, yb, zb); val gy1 = iso.iy(SX, SY, runTop)
            for (yy in gy0..gy1) if (Math.floorMod(yy + (t * 20).toInt(), 3) != 0) c.set(gx, yy, grainL)
        }
    }

    /**
     * Grain poured into the hopper (`pour` between empty and full): a sack tipped over its rim on the right, its mouth down,
     * a golden stream falling from it into the hopper.
     */
    private fun pouring() {
        val v = fx("pour") ?: return
        if (v <= 0.04f || v >= 0.97f) return
        val mx = SX + 0.55f; val my = SY + 0.2f; val mz = hz1 + 1.2f
        prop {
            // the sack lying tipped over the rim, its bottom up and to the right
            val cx = iso.sx(mx + 0.55f, my); val cy = iso.sy(mx + 0.55f, my, mz + 2.2f)
            val p = detail.toFloat()
            c.fillEllipse(cx, cy, 3.6f * p, 2.4f * p, juteD)
            c.fillEllipse(cx - 0.4f * p, cy - 0.4f * p, 3.1f * p, 1.9f * p, jute)
            c.fillEllipse(cx - 1.2f * p, cy - 1f * p, 1.4f * p, 0.8f * p, juteL)
            // its mouth over the hopper
            val nx = iso.sx(mx, my); val ny = iso.sy(mx, my, mz)
            c.fillEllipse(nx, ny, 1.6f * p, 1f * p, juteD)
        }
        // the stream of grain into the hopper
        val sx0 = iso.ix(mx - 0.05f, my); val sy0 = iso.iy(mx, my, mz - 0.3f)
        val gl = hz0 + 1.2f + (hz1 - hz0 - 1.9f) * v
        val sy1 = iso.iy(mx - 0.05f, my, gl)
        prop(outline = 0) {
            for (yy in sy0..sy1) for (o in 0 until max(1, detail)) {
                val ph = (yy + (t * 40).toInt() + o * 2)
                c.set(sx0 + o - detail / 2, yy, if (Math.floorMod(ph, 4) == 0) grainL else grainM)
            }
        }
    }

    /** The wooden spout from the vat's front down to the flour sack. */
    private fun spout() {
        val xa = SX - 0.3f; val ya = SY + 0.95f; val za = bedTop + 0.9f
        val xb = FLOUR_X + 0.02f; val yb = FLOUR_Y - 0.25f; val zb = fl + 7.4f
        val w = 0.17f
        iso.quad(xa - w, ya, za, xa + w, ya, za, xb + w, yb, zb, xb - w, yb, zb, boardM)
        iso.quad(xa + w, ya, za, xa + w, ya, za + 0.7f, xb + w, yb, zb + 0.7f, xb + w, yb, zb, boardD)
        iso.line(xa - w, ya, za + 0.7f, xb - w, yb, zb + 0.7f, boardL)
        if (fine) iso.line(xa - w * 0.3f, ya, za + 0.1f, xb - w * 0.3f, yb, zb + 0.1f, flourD)
    }

    /** How full the flour sack is (0..1): as `sack` has it, else half full. */
    private fun sackFull(): Float = fx("sack") ?: 0.5f

    /**
     * The flour: the open white sack under the spout (slack and low when empty, round when full, flour heaped in its mouth),
     * the flour running from the spout into it while the stones turn, spilled flour round it on the floor.
     */
    private fun flour(turning: Boolean) {
        val full = sackFull()
        val cx = iso.sx(FLOUR_X, FLOUR_Y); val base = iso.sy(FLOUR_X, FLOUR_Y, fl)
        val p = detail.toFloat()
        // the spilled flour round its foot
        c.fillEllipse(cx + 0.5f * p, base, 7f * p, 2.6f * p, mealD)
        c.fillEllipse(cx - 0.5f * p, base - 0.3f * p, 5.4f * p, 1.8f * p, mealC)
        val hgt = 3.2f + 4.4f * full
        val rx = 0.42f * 11.3f * detail
        val sack = Col.hex(0xEDE6D8); val sackL = Col.hex(0xFAF6EE); val sackD = Col.hex(0xC4BCAC)
        lathe(cx, base, hgt, { h ->
            val f = h / hgt
            (rx / p) * when {
                f < 0.15f -> 0.85f + f
                f < 0.8f -> 1f - (1f - full) * 0.18f * sin((f - 0.15f) / 0.65f * PI.toFloat())
                else -> 0.95f + (f - 0.8f) * 0.4f
            }
        }) { u, du, h, xx, yy ->
            when {
                h > hgt - 0.6f -> sackD
                u < -0.6f -> sackL
                u > 0.55f -> sackD
                fine && (((xx shr 1) + (yy shr 1)) and 1) == 0 -> Col.mix(sack, sackL, 0.3f)
                else -> sack
            }
        }
        // its open mouth: the flour inside, heaped when it is full
        val my = base - hgt * p
        c.fillEllipse(cx, my, rx, rx * 0.45f, sackD)
        c.fillEllipse(cx, my + 0.3f * p, rx - p, rx * 0.45f - 0.6f * p, mealC)
        if (full > 0.75f) c.fillEllipse(cx - 0.3f * p, my - (full - 0.75f) * 6f * p, rx * 0.6f, (full - 0.75f) * 5f * p + 0.5f, mealC)
        // the flour running from the spout into it
        if (turning) {
            val sx0 = iso.ix(FLOUR_X + 0.02f, FLOUR_Y - 0.25f); val sy0 = iso.iy(FLOUR_X + 0.02f, FLOUR_Y - 0.25f, fl + 7.2f)
            val wide = if (spin() >= HARD) 2 * detail else detail
            for (yy in sy0..floor(my).toInt()) for (o in 0 until wide) {
                val ph = yy + (t * 30).toInt() + o
                c.set(sx0 + o - wide / 2, yy, if (Math.floorMod(ph, 5) == 0) mealD else mealC)
            }
        }
    }

    // ------------------------------------------------------------------ the shaft, the wheel, the flume

    /** The shaft from the cog wheel out through the front-right wall to the waterwheel: a thick round log, iron hoops on it, turning. */
    private fun shaft(a: Float) {
        val xa = GX + 0.18f; val xb = WX - 0.3f
        val r = 1.1f
        // its face toward us, round: lit on top, dark under it, the hoops and a knot going round with it
        onY(SY + 0.2f, xa, xb, gz - r, gz + r) { x, z, _, _ ->
            val v = (z - gz) / r
            val hoop = abs(x - GX - 0.6f) < 0.07f || abs(x - x1 - 0.2f) < 0.07f || abs(x - WX + 0.8f) < 0.07f
            val knot = ((x * 1.3f + a / (2f * PI.toFloat())) % 1f) < 0.04f && abs(v + 0.1f) < 0.4f
            when {
                hoop -> if (v > 0.3f) ironHi else ironC
                knot -> boardX
                v > 0.55f -> boardL
                v < -0.6f -> boardD
                fine && Noise.v2(x * 3f, v * 2f, 85) > 0.7f -> Col.mix(boardM, boardD, 0.4f)
                else -> boardM
            }
        }
        // its top, seen from above
        onFlat(gz + r, xa, SY - 0.2f, xb, SY + 0.2f) { x, _, _, _ ->
            if (abs(x - GX - 0.6f) < 0.07f || abs(x - x1 - 0.2f) < 0.07f || abs(x - WX + 0.8f) < 0.07f) ironHi else Col.mix(boardL, Col.hex(0xE8C090), 0.3f)
        }
    }

    /**
     * The overshot waterwheel outside in the race, in the plane x = WX, its outer face toward us: the shroud with its buckets
     * round the rim, eight arms, the hub on the shaft; turning by [a] (as the cog wheel does: they share the shaft); still in
     * winter, rime on its rim; under the race's water nothing shows.
     */
    private fun waterwheel(a: Float) {
        val r = WR
        // the inner shroud, darker, then the buckets between, then the outer one
        for ((k, wx) in floatArrayOf(WX - 0.25f, WX + 0.25f).withIndex()) {
            val outer = k == 1
            onX(wx, SY - WRY - 0.2f, SY + WRY + 0.2f, RACE_WATER, wz + r + 0.6f) { y, z, px, py ->
                val dy = (y - SY) * CELL_Z; val dz = z - wz
                val rr = sqrt(dy * dy + dz * dz)
                val ang = atan2(dz, dy) + a
                when {
                    rr > r -> 0
                    rr > r - 2.2f -> {
                        // the shroud's ring with the buckets' ends standing in it every 1/16 turn
                        val q = ((ang / (2f * PI.toFloat()) * BUCKETS) % 1f + 1f) % 1f
                        val bucket = q < 0.18f
                        val col = when {
                            bucket -> if (outer) boardX else Col.scale(boardX, 0.7f)
                            rr > r - 0.5f -> if (dz > 0f) boardL else boardM
                            else -> if (dz + dy * 0.2f > 0f) boardM else boardD
                        }
                        val c0 = if (outer) col else Col.scale(col, 0.7f)
                        // frozen still in winter: rime on its rim
                        if (winter && spin() == 0f && dz > -1f && Noise.rnd(px, py, 88) < 0.45f) Col.mix(c0, Pal.ICE_L, 0.6f) else c0
                    }
                    !outer -> 0
                    rr < 1.4f -> if (rr < 0.7f) Pal.WOOD_X else ironC
                    else -> {
                        val q = ((ang / (2f * PI.toFloat()) * 8f) % 1f + 1f) % 1f
                        val w = abs(q - 0.5f) * (2f * PI.toFloat() / 8f) * rr
                        if (w < 0.55f) (if (fine && w < 0.2f) boardL else boardM) else 0
                    }
                }
            }
        }
        // icicles hanging from the rim in winter
        if (winter) for (k in 0 until 5) {
            val ang = PI.toFloat() * (1.15f + k * 0.17f)
            val iy = SY + cos(ang) * WRY * 0.98f; val iz = wz + sin(ang) * r
            val ix = iso.ix(WX + 0.25f, iy); val itop = iso.iy(WX + 0.25f, iy, iz)
            for (j in 0 until (2 + k % 3) * detail) c.set(ix, itop + j, if (j % 2 == 0) Pal.ICE_L else Pal.ICE_M)
        }
    }

    /**
     * The water from the flume onto the waterwheel's buckets at its top, running down its back and spilling into the race,
     * while it turns (more as it grinds hard); its spray where it falls.
     */
    private fun wheelWater() {
        val r = WR
        val gush = if (spin() >= HARD) 1f else 0.55f
        val topY = SY - 0.55f
        onX(WX, topY - 0.25f, topY + 0.2f * gush, RACE_WATER, wz + r + 1f) { _, z, px, py ->
            val dz = z - wz
            if (dz > r + 0.8f) return@onX 0
            val shine = Noise.rnd(px / detail, (py / detail + (t * 30).toInt()), 87) < 0.3f
            if (shine) Col.hex(0xF2F6FA) else Col.mix(waterL, Col.hex(0xFFFFFF), 0.25f)
        }
        s.fx {
            val sx0 = iso.sx(WX, SY - WRY * 0.7f); val sy0 = iso.sy(WX, SY - WRY * 0.7f, RACE_WATER)
            for (j in 0 until (5 + 8 * gush).toInt()) {
                val ph = ((t * 1.7 + Noise.rnd(j, 89)) % 1.0).toFloat()
                val dx = (Noise.rnd(j, 90) - 0.5f) * 10f * detail; val dz = sin(ph * PI.toFloat()) * 5f * detail * Noise.rnd(j, 91)
                c.blend(floor(sx0 + dx).toInt(), floor(sy0 - dz).toInt(), Col.hex(0xF2F6FA), 0.8f * (1f - ph))
            }
        }
    }

    /** The flume: a wooden trough on a trestle from the plot's back edge to over the wheel's top, the water in it while it turns. */
    private fun flume(turning: Boolean) {
        val xa = WX - 0.24f; val xb = WX + 0.24f; val ya = 0f; val yb = SY - 0.45f
        val zb = wz + WR + 0.8f; val zt = zb + 1.6f
        // the trough: its front and right faces, the water in it (its trestle in the race is drawn first, scenery)
        iso.box(xa, ya, zb, xb - xa, yb - ya, zt - zb, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        val flow = turning
        onFlat(zt, xa + 0.07f, ya, xb - 0.07f, yb - 0.05f) { _, y, px, py ->
            when {
                winter && !flow -> Pal.SNOW_L
                flow -> if (sin((y - t.toFloat() * 3f) * 9f) > 0.7f) waterL else water
                else -> Pal.WOOD_X
            }
        }
        // icicles off its end in winter
        if (winter) for (k in 0 until 3) {
            val ix = iso.ix(xa + 0.1f + k * 0.15f, yb); val iy = iso.iy(xa + 0.1f + k * 0.15f, yb, zb)
            for (j in 0 until (2 + k) * detail) c.set(ix, iy + j, if (j % 2 == 0) Pal.ICE_L else Pal.ICE_M)
        }
    }

    /** The flume's trestle standing in the race: two posts from the water up to the trough. */
    private fun trestle() {
        val zb = wz + WR + 0.8f
        iso.post(WX, 0.45f, RACE_WATER, zb, Pal.WOOD_D)
        iso.post(WX - 0.2f, 0.45f, RACE_WATER, zb, Pal.WOOD_M)
    }

    /** An old millstone lying in the grass in front of the mill, worn out: a round flat stone with its square eye, moss on it. */
    private fun oldStone() {
        val x = 5.4f; val y = 10.55f
        val cx = iso.sx(x, y); val base = iso.sy(x, y, 0f)
        val r = 0.85f * 11.3f * detail
        drum(cx, base, iso.sy(x, y, 1.3f), r, r * 0.5f, stoneL, stoneM, stoneD, stoneHi)
        val top = iso.sy(x, y, 1.3f)
        c.fillEllipse(cx, top, r, r * 0.5f, if (winter) Pal.SNOW_L else stoneL)
        if (!winter) {
            c.fillEllipse(cx + r * 0.4f, top + r * 0.1f, r * 0.35f, r * 0.15f, Col.hex(0x6E8A4A))
            for (k in 0 until 6) {
                val ang = k * PI.toFloat() / 3f + 0.3f
                c.line(floor(cx + cos(ang) * r * 0.25f).toInt(), floor(top + sin(ang) * r * 0.12f).toInt(), floor(cx + cos(ang) * r * 0.9f).toInt(), floor(top + sin(ang) * r * 0.45f).toInt(), stoneM)
            }
        }
        c.fillRect(floor(cx - detail).toInt(), floor(top - detail / 2f).toInt(), 2 * detail, max(1, detail), Col.hex(0x3A3630))
    }

    // ------------------------------------------------------------------ light and dust

    /** The lantern's light from dusk (or as `lantern` has it), the window's by day. */
    private fun lights() {
        if (lanternLit()) {
            val lx = x0 + 0.5f; val ly = LANTERN_Y; val lz = LANTERN_Z - 2f
            s.light(iso.sx(lx, ly), iso.sy(lx, ly, lz), 86f, 1.1f * max(env.windows, if (fx("lantern") != null) 0.75f else 0f))
            s.fx {
                c.penEmissive = true
                c.ditherCircle(iso.sx(x0 + 0.12f, ly), iso.sy(x0 + 0.12f, ly, lz), 5f * K, Pal.FLAME[1], 0.09f * max(env.windows, 0.6f))
                c.penEmissive = false
            }
        }
        if (env.sun > 0f) s.light(iso.sx(3.0f, 3.2f), iso.sy(3.0f, 3.2f, fl + 2f), 28f, 0.5f * env.sun.coerceIn(0f, 1f) * (1f - frame.sky.gloom * 0.7f))
    }

    /**
     * By day the window's light: a slanting beam from the window down onto the floor in front of it, its patch of sun on the
     * boards, and the flour dust drifting and glinting in it.
     */
    private fun beam() {
        val a = 0.3f * env.sun.coerceIn(0f, 1f) * (1f - frame.sky.gloom * 0.8f)
        if (a <= 0.02f) return
        val shift = ((s.hour - 13f) / 6f).coerceIn(-1f, 1f) * 0.6f
        val q0 = y0 + 1.6f; val q1 = y0 + 3.6f
        val p0 = winA + 0.1f + shift * 0.3f; val p1 = winB - 0.1f + shift * 0.3f
        val id = floorId
        val warm = Col.hex(0xFFF4D0)
        s.fx {
            // the patch on the floor
            c.polyBegin()
            c.polyAdd(iso.sx(p0, q0), iso.sy(p0, q0, fl)); c.polyAdd(iso.sx(p1, q0), iso.sy(p1, q0, fl))
            c.polyAdd(iso.sx(p1 + shift, q1), iso.sy(p1 + shift, q1, fl)); c.polyAdd(iso.sx(p0 + shift, q1), iso.sy(p0 + shift, q1, fl))
            c.polyScan { px, py -> if (c.inside(px, py) && c.ids[c.index(px, py)] == id) c.blend(px, py, warm, a) }
            // the beam itself, faint, from the window's glass to the patch
            val wa = iso.sx(winA, y0); val wb = iso.sx(winB, y0)
            val wt = iso.sy(winA, y0, winZ1); val wbm = iso.sy(winB, y0, winZ0)
            c.polyBegin()
            c.polyAdd(wa, wt); c.polyAdd(wb, iso.sy(winB, y0, winZ1)); c.polyAdd(iso.sx(p1 + shift, q1), iso.sy(p1 + shift, q1, fl)); c.polyAdd(iso.sx(p0, q0), iso.sy(p0, q0, fl))
            c.polyScan { px, py -> if (Dither.at(px, py) < 0.5f) c.blend(px, py, warm, a * 0.35f) }
            // the motes: flour dust drifting in it, glinting
            val n = 16 + (24 * fxOn("dust")).toInt()
            for (j in 0 until n) {
                val ph = ((t * 0.05 + Noise.rnd(j, 93)) % 1.0).toFloat()
                val u = Noise.rnd(j, 94)
                val mx = wa + (wb - wa) * u + (iso.sx(p0 + shift, q1) - wa) * ph + sin(t * 0.7 + j).toFloat() * 2f * detail
                val my = wt + (wbm - wt) * Noise.rnd(j, 95) + (iso.sy(p0, q1, fl) - wt) * ph
                val tw = 0.5f + 0.5f * sin(t * 2.3 + j * 1.7).toFloat()
                c.blend(floor(mx).toInt(), floor(my).toInt(), Col.hex(0xFFFDF2), 0.8f * tw * min(1f, a * 4f))
            }
        }
    }

    /**
     * A puff of flour dust (`dust`): a white cloud billowing up off the flour sack and spreading over whoever stands by it,
     * drifting up and toward the window, thick as the level is.
     */
    private fun dust() {
        val v = fxOn("dust")
        if (v <= 0.02f) return
        val cx = iso.sx(FLOUR_X + 0.4f, FLOUR_Y); val cy = iso.sy(FLOUR_X + 0.4f, FLOUR_Y, fl + 9f)
        s.fx {
            for (j in 0 until 14) {
                val ph = ((t * 0.32 + j / 14.0) % 1.0).toFloat()
                val ang = Noise.rnd(j, 97) * 2f * PI.toFloat()
                val far = (0.3f + 0.7f * Noise.rnd(j, 98)) * (0.4f + 0.6f * ph)
                val px = cx + cos(ang) * far * 14f * K - ph * 6f * K
                val py = cy + sin(ang) * far * 6f * K - ph * 9f * K
                c.ditherCircle(px, py, (2.2f + ph * 3.6f) * K * (0.6f + 0.4f * Noise.rnd(j, 99)), flourC, (0.75f * v) * (1f - ph * 0.7f))
            }
        }
    }

    // ------------------------------------------------------------------ Friuli's maize (companion/SCENES.md "Culture looks")

    /** Reads the culture's grain and its meal once a frame: the default culture's golden wheat and white flour, Friuli's maize. */
    private fun readGrain() {
        maize = variant("grain") == "maize"
        grainL = colour("grain.l", GRAIN_L); grainM = colour("grain.m", GRAIN_M); grainD = colour("grain.d", GRAIN_D)
        mealC = colour("meal", flourC); mealD = colour("meal.d", flourD)
    }

    /**
     * A bunch of maize cobs hung up to dry (Friuli's): [n] cobs tied by their husks to a cord from a nail at world ([x], [y],
     * [z]), the pale husks fanning up over the knot, the golden cobs hanging under it in two staggered rows, their kernels in
     * rows closer up.
     */
    private fun cobs(x: Float, y: Float, z: Float, n: Int, seed: Int) {
        val p = detail.toFloat()
        val cx = iso.sx(x, y); val top = iso.sy(x, y, z)
        val cord = Col.hex(0x8A6A44); val husk = Col.hex(0xE6D7A0); val huskD = Col.hex(0xC0AC70)
        val l = Col.hex(0xFAD25A); val m = Col.hex(0xEAA632); val d = Col.hex(0xB8761E)
        c.vline(floor(cx).toInt(), floor(top).toInt(), floor(top + 2.5f * p).toInt(), cord)
        val ty = top + 3f * p
        // the husks pulled back, fanning up over the knot
        for (k in -2..2) {
            val hx = cx + k * 1.4f * p; val hy = ty - (3.2f - abs(k) * 0.5f) * p
            for (o in 0 until max(1, detail / 2 + 1)) c.line(floor(cx).toInt() + o, floor(ty).toInt(), floor(hx).toInt() + o, floor(hy).toInt(), if (k < 0) husk else huskD)
        }
        // the cobs, the back ones first
        for (i in n - 1 downTo 0) {
            val col = i % 2; val row = i / 2
            val ccx = cx + (col - 0.5f) * 3f * p + (if (row % 2 == 1) 0.7f * p else 0f)
            val ccy = ty + 4.5f * p + row * 3.2f * p + Noise.rnd(i, seed) * p
            // the cob, round at its top and narrowing to a blunt point
            c.fillEllipse(ccx, ccy - 0.8f * p, 1.5f * p, 3.2f * p, d)
            c.fillEllipse(ccx, ccy + 1.6f * p, 1.0f * p, 2.2f * p, d)
            c.fillEllipse(ccx - 0.25f * p, ccy - 1f * p, 1.2f * p, 2.9f * p, m)
            c.fillEllipse(ccx - 0.2f * p, ccy + 1.3f * p, 0.75f * p, 1.9f * p, m)
            c.fillEllipse(ccx - 0.6f * p, ccy - 1.4f * p, 0.45f * p, 2f * p, l)
            if (fine) for (r in -3..3) for (q in -1..1) {
                if ((r + q) and 1 != 0) continue
                c.set(floor(ccx + q * 0.7f * p).toInt(), floor(ccy + r * 1.05f * p).toInt(), if (q < 0) l else d)
            }
        }
    }

    private companion object {
        /** The default culture's grain: golden wheat. */
        val GRAIN_L = Col.hex(0xF2CC6A); val GRAIN_M = Col.hex(0xD8A846); val GRAIN_D = Col.hex(0xA87A2A)

        /** The shaft's turn by itself (rad/s), and grinding hard; the pinion and the runner turn this many times as fast. */
        const val EASY = 0.7f
        const val HARD = 1.7f
        const val GEARING = 3f

        /** Nominal px a cell is high in a wall's plane (the wheels are round in it). */
        const val CELL_Z = 4.5f

        /** The footing's height over the floor (nominal px). */
        const val FOOTING = 3.2f

        /** The window's bench, the scale and the measure on it, the sieve's peg and the mouse hole (x along the right wall). */
        const val BENCH_A = 2.05f; const val BENCH_B = 3.95f
        const val SCALE_X = 2.55f; const val MEASURE_X = 3.55f
        const val SIEVE_X = 4.6f; const val MOUSE_X = 1.4f

        /** The gallery's front edge (x) and end (y), the ladder's rails (x) and its foot (y), the door's sides along the left wall. */
        const val GAL_X = 2.3f; const val GAL_Y = 5.5f
        const val LADDER_A = 1.35f; const val LADDER_B = 1.95f; const val LADDER_FOOT = 6.6f
        const val DOOR_A = 6.85f; const val DOOR_B = 8.15f
        const val DOOR_Z = 21f
        /** The lantern on the left wall by the door: where along it (y), and its cap's height. */
        const val LANTERN_Y = 6.55f
        const val LANTERN_Z = 18f

        /** The cat's crouch and where its leap lands, by the mouse hole. */
        const val CAT_X = 2.05f; const val CAT_Y = 3.05f
        const val CAT_TO_X = 1.45f; const val CAT_TO_Y = 2.0f

        /** The platform (x, then y) and the stones' axis on it. */
        const val PLAT_A = 6.3f; const val PLAT_B = 8.6f; const val PLAT_C = 1.35f; const val PLAT_D = 3.6f
        const val SX = 7.45f; const val SY = 2.45f

        /** The hopper's half-width at its bottom and its top (cells). */
        const val HOP_B = 0.26f; const val HOP_T = 0.8f

        /** The flour sack under the spout. */
        const val FLOUR_X = 7.0f; const val FLOUR_Y = 4.6f

        /** The cog wheel's plane and its radius (px, and cells along y), its pegs; the waterwheel's plane, radius and buckets. */
        const val GX = 8.0f
        const val GR = 5.8f; const val GRY = GR / CELL_Z
        const val PEGS = 16f
        const val WX = 10.75f
        const val WR = 9f; const val WRY = WR / CELL_Z
        const val BUCKETS = 16f

        /** The mill race: its banks (x) and its water's level (px under the yard). */
        const val RACE_A = 10.05f; const val RACE_B = 11.45f
        const val RACE_WATER = -1.8f
    }
}
