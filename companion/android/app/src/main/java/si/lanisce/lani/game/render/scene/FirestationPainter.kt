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
 * V gasilskem domu: the village's fire station (the project `gasilski_dom`, Luka's), its hall cut open on its plot like the
 * rooms of the houses, and beside it outside the tall hose tower with the alarm bell in its belfry (companion/SCENES.md
 * "Arts"). The hall's walls are whitewashed over an oxblood dado on a stone footing, its floor of bricks; in the back wall
 * the big plank doors, red with a white brace, the brigade's sign over them (PGD: prostovoljno gasilsko društvo); before them
 * the old hand pump from Gorica on its four wheels, red, its two brass cylinders and air chamber shining, the long handles
 * either side, the drawbar toward the doors. Along the left wall a row of pegs with the brass helmets over the dark blue
 * jackets, two axes crossed on their brackets, the signal horn on its nail, the picture of St Florian pouring his bucket on a
 * burning house, the leather buckets on their shelf, the bench under the jackets, the hose wound on its reel by the wall,
 * its end run across the floor to the pump; in the back corner the brigade's banner on its pole. On the back wall the window
 * and the ladder hung on brackets over the doors, the lantern on its hook by them. Outside on the right the tower: plastered
 * like the hall over its stone footing, a small door, a slit window, the belfry with the bell in its arches under a pyramid
 * roof of shingles, the bell rope down the tower's front to beside the door. Gravel round it, a few tufts.
 *
 * By day the doors stand open on the village (the yard, a fence, the meadow and the hills, the sky); at night they are shut
 * and the lantern lights the hall. In winter snow on the tower's roof, on the walls' tops and in the yard.
 *
 * Its effects ([si.lanisce.lani.game.scene.SceneArt.effects]):
 * - `bell`: the bell swinging in the belfry and ringing (how hard), the rope's end going up and down, its rings going out.
 * - `pump`: the pump worked (how hard): the handles see-sawing, the hose from its outlet out to the nozzle by the front, a
 *   jet arcing from it over the front wall into the yard, spray and a wet patch where it lands.
 * - `doors`: the big doors: 0 shut, 1 wide open (into the hall, against the jambs). Not set: open by day, shut at night.
 * - `lantern`: 0 dark, 1 lit. Not set: lit from dusk.
 * - `horn`: the signal horn blown, its calls going out from its bell (how loud), the horn trembling on its nail.
 *
 * Tapped ([pokes]): the bell swings and rings, then twice; a helmet gleams, a glint running over the brass, then all four.
 *
 * Closer up ([detail] 2 and 3): the bricks' joints and wear, the doors' planks and hinges, the pump's rivets and the brass's
 * glints, the helmets' combs, the jackets' buttons, the tower's courses and shingles, the picture's flames.
 *
 * Friuli's fire station ([look]; companion/SCENES.md "Culture looks"): the tower and the walls' outsides of river cobbles in
 * courses with bands of brick, its roof of coppi; the sign VVF (vigili del fuoco); the helmets of polished steel; the
 * picture of Santa Barbara, the patron of the Italian firemen, with her tower; the banner the tricolore.
 */
internal class FirestationPainter : RoomPainter() {
    override val art = "firestation"
    override val pokes = listOf(Poke("bell", listOf(2.2, 3.0), rest = 3.0), Poke("helmet", listOf(1.2, 1.8), rest = 2.5))

    override val plotW = 13.5f
    override val plotD = 10.5f
    override val rise = 27f

    // the window on the back wall (y = Y0), left of the doors
    override val winA = 1.55f; override val winB = 2.85f; override val winZ0 = fl + 11f; override val winZ1 = fl + 19f

    // the big doors in the back wall: the opening from DA to DB, its top at DZ (a flat arch rising DZA more in its middle)
    private val da = 3.4f; private val db = 8.0f; private val dz = fl + 18.5f; private val dza = 1.6f
    private val leafL: Float get() = (db - da) / 2f

    // the pump on the floor before the doors: its body over PX0..PX1 × PY0..PY1, on wheels, the handles along y at either side
    private val px0 = 4.55f; private val px1 = 6.35f; private val py0 = 3.55f; private val py1 = 6.15f
    private val bodyZ0 = fl + 3.4f; private val bodyZ1 = fl + 8.6f

    // the tower outside on the right: TX0..TX1 × TY0..TY1, its walls to TZ, the belfry to TB, the roof's apex TR
    private val tx0 = 11.0f; private val tx1 = 13.1f; private val ty0 = 0.45f; private val ty1 = 2.55f
    private val twZ = 34f; private val twB = 43f; private val twR = 53f

    // along the left wall (x = X0): the pegs, the axes, the picture, the horn, the buckets' shelf, the bench, the reel
    private val pegZ = fl + 16.5f
    private val pegYs = floatArrayOf(2.25f, 3.15f, 4.05f, 4.95f)
    private val axeY = 5.85f
    private val picA = 6.85f; private val picB = 8.25f; private val picZ0 = fl + 12.5f; private val picZ1 = fl + 20f
    private val hornY = 6.0f; private val hornZ = fl + 9.5f
    private val shelfA = 6.7f; private val shelfB = 8.6f; private val shelfZ = fl + 6.5f
    private val benchY0 = 2.0f; private val benchY1 = 5.4f; private val benchZ = fl + 4.4f
    private val reelX = 2.35f; private val reelY = 7.35f

    private val lanternX = 8.75f; private val lanternZ = fl + 16f

    // the people: by the pump's right handle, by the reel, in the doorway
    private val pumpPX = 7.35f; private val pumpPY = 5.6f
    private val hosesPX = 2.45f; private val hosesPY = 6.7f
    private val doorPX = 4.6f; private val doorPY = 1.35f

    private val fire = Col.hex(0xC23A2C); private val fireL = Col.hex(0xE0604A); private val fireD = Col.hex(0x8A2420); private val fireK = Col.hex(0x5E1A16)
    private val doorL = Col.hex(0xB0402E); private val doorM = Col.hex(0x96322A); private val doorD = Col.hex(0x6E2420)
    private val cream = Col.hex(0xF2E8D2)
    private val oxL = Col.hex(0x9A4A3A); private val oxM = Col.hex(0x843C30); private val oxD = Col.hex(0x6A2E26)
    private val brickL = Col.hex(0xB8684A); private val brickM = Col.hex(0xA25A40); private val brickD = Col.hex(0x884A36); private val brickJ = Col.hex(0x6E5A4E)
    private val blue = Col.hex(0x2E3E6A); private val blueL = Col.hex(0x46598E); private val blueD = Col.hex(0x1E2848)
    private val leather = Col.hex(0x6A4228); private val leatherL = Col.hex(0x8E5E38); private val leatherD = Col.hex(0x48291A)
    private val hoseC = Col.hex(0xD8CCA8); private val hoseL = Col.hex(0xF0E6C8); private val hoseD = Col.hex(0xA89A74)
    private val steel = Col.hex(0xC8CCD4); private val steelL = Col.hex(0xF0F2F6); private val steelD = Col.hex(0x8A909C)
    private val bellC = Col.hex(0xC8962E); private val bellL = Col.hex(0xF0CA62); private val bellD = Col.hex(0x8A6420)
    private val gravelL = Col.hex(0xB8AE9C); private val gravelM = Col.hex(0x9C927E); private val gravelD = Col.hex(0x7A7262)
    private val waterC = Col.hex(0xBFE0F4); private val waterL = Col.hex(0xF0FAFF)

    /** What the culture's look paints otherwise ([readStation]): the outsides of cobbles and brick, coppi, the sign, the steel helmets, the picture, the banner. */
    private var cobbles = false
    private var coppi = false
    private var sign = "PGD"
    private var chrome = false
    private var barbara = false
    private var tricolore = false

    private var bellAngle = 0f

    /** Closer up (detail 2 or 3): the finer touches the scene's own canvas has no room for. */
    private val fine: Boolean get() = detail > 1

    /** The hall is dim inside, the doors' and the window's light falling in; at night the lantern. */
    override fun ambient(): FloatArray {
        val dim = 0.92f
        val warm = env.dark * 0.06f
        return floatArrayOf(env.ambR * dim + warm, env.ambG * dim + warm * 0.75f, env.ambB * dim + warm * 0.4f)
    }

    /** The yard round the tower and before the hall is out under the sky. */
    override fun outdoors(id: Int): Boolean = id == 0 || id == groundId

    private fun readStation() {
        cobbles = material("walls", "plaster") == "cobbles"
        coppi = material("roof", "shingles") == "coppi"
        sign = if (variant("sign") == "vvf") "VVF" else "PGD"
        chrome = variant("helmet") == "chrome"
        barbara = variant("picture") == "barbara"
        tricolore = variant("flag") == "it"
    }

    /** The doors' angle, 0 shut … 1 wide open: as `doors` has it, else open by day and shut at night. */
    private fun doorsOpen(): Float = fx("doors") ?: if (env.dark > 0.55f) 0f else 1f

    private fun lanternLit(): Boolean = fx("lantern")?.let { it > 0.5f } ?: (env.windows > 0.35f)

    /** How hard the bell swings: the effect, else a tap's ring ([pokes]). */
    private fun bellSwing(): Float {
        val e = fxOn("bell")
        val pk = poked("bell") ?: return e
        val a = pk.age(t)
        val dur = if (pk.step == 0) 2.2f else 3.0f
        return max(e, (1f - a / dur).coerceIn(0f, 1f) * (if (pk.step == 0) 0.7f else 1f))
    }

    override fun paint() {
        fit()
        vignette()
        lv = 1
        readLook()
        readStation()
        val swing = bellSwing()
        bellAngle = sin(t * 4.2).toFloat() * 0.55f * swing
        yard()
        tufts(60, 31) { x, y -> (x < x1 + tk + 0.4f && y < y1 + tk + 0.4f) || (x in tx0 - 0.4f..tx1 + 0.4f && y in ty0 - 0.4f..ty1 + 0.4f) }
        bricks()
        rugId = floorId
        // the doorway's view, then the doors, then the walls round them (the opening left in the back wall)
        doorway()
        stationWalls()
        thing("door") { doors(); signBoard() }
        thing("window") { window() }
        thing("ladder") { ladderOnWall() }
        thing("lantern", slop = 1) { lantern() }
        // the left wall: the banner in the corner, the pegs, the jackets and the helmets, the axes, the picture, the horn, the buckets
        thing("flag") { banner() }
        thing("uniform") { jackets() }
        thing("helmet", slop = 1) { helmets() }
        thing("axe", slop = 1) { axes() }
        thing("picture") { picture() }
        thing("horn", slop = 1) { horn() }
        thing("bucket", slop = 1) { buckets() }
        thing("bench") { bench() }
        for (p in peopleAt("door")) { footShadow(doorPX, doorPY); personAt(p, doorPX, doorPY, fl) }
        thing("hose", slop = 1) { reel(); hoseRun() }
        for (p in peopleAt("hoses")) { footShadow(hosesPX, hosesPY); personAt(p, hosesPX, hosesPY, fl) }
        // the pump: its shadow, its back wheels, its body, its front wheels and the handles
        thing("pump") { pump() }
        for (p in peopleAt("pump")) { footShadow(pumpPX, pumpPY); personAt(p, pumpPX, pumpPY, fl, flip = true) }
        jet()
        stationStubs()
        // outside: the tower, its bell and its rope
        thing("tower") { tower() }
        thing("bell", slop = 1) { bell() }
        prop(outline = 0) { rope() }
        prop { waterBarrel() }
        bellRings(swing)
        hornCalls()
        fireflies(iso.ix(0f, 10f), iso.iy(10f, 10f, 20f), iso.ix(13f, 0f), iso.iy(13f, 10f, 0f), 8, 13)
        lights()
    }

    // ------------------------------------------------------------------ the yard and the floor

    private fun yard() = plot { x, y, px, py -> yardGround(x, y, px, py) }

    /** Gravel round the tower and before the hall, trodden into the grass at its edges; snow in winter. */
    private fun yardGround(x: Float, y: Float, px: Int, py: Int): Int {
        val g = grassAt(x, y, px, py)
        val nearHall = x < x1 + tk + 1.1f && y < y1 + tk + 1.1f
        val nearTower = x > tx0 - 1.2f && y < ty1 + 2.4f
        val path = x > x1 - 1f && y in 3.2f..6.4f
        val f = when {
            nearHall -> 1f - max(max(0f, x - x1 - tk), max(0f, y - y1 - tk)) / 1.1f
            nearTower || path -> 0.9f
            else -> 0f
        }
        if (f <= 0f) return g
        if (winter) return dirtAt(px, py, f, g)
        val n = Noise.rnd(px / max(1, detail), py / max(1, detail), 33)
        val gravel = if (n < 0.2f) gravelD else if (n < 0.75f) gravelM else gravelL
        return if (f + (Dither.at(px, py) - 0.5f) * 0.6f > 0.45f) gravel else g
    }

    /** The brick floor: bricks laid across in running bond, worn, a darker damp patch round the pump's feet. */
    private fun bricks() {
        floorId = s.newObject(Pal.OUTLINE)
        onFlat(fl, x0, y0, x1, y1) { x, y, px, py -> brick(x, y, px, py) }
        c.penId = 0
        shade(iso.sx((px0 + px1) / 2f, (py0 + py1) / 2f), iso.sy((px0 + px1) / 2f, (py0 + py1) / 2f, fl), 7.5f * K, 3.2f * K, 0.78f, floorId)
        shade(iso.sx(1.6f, 3.6f), iso.sy(1.6f, 3.6f, fl), 5f * K, 2.6f * K, 0.84f, floorId)
    }

    private fun brick(x: Float, y: Float, px: Int, py: Int): Int {
        val bw = 0.62f; val bh = 0.31f
        val row = floor((y - y0) / bh).toInt()
        val u = (x - x0) / bw + (row and 1) * 0.5f
        val b = floor(u).toInt()
        val fa = (u - b) * bw; val fb = (y - y0) - row * bh
        val j = if (fine) 0.035f else 0.06f
        if (fa < j || fb < j) return brickJ
        val tone = Noise.rnd(b, row, 61)
        var col = if (tone < 0.3f) brickL else if (tone < 0.75f) brickM else brickD
        if (fine) {
            if (fb < j + 0.04f || fa < j + 0.05f) col = Col.mix(col, Col.hex(0xE0A07A), 0.25f)
            if (Noise.rnd(px, py, 63) < 0.05f) col = Col.scale(col, 0.9f)
        }
        // worn paler where the pump rolls out to the doors
        if (abs(x - (px0 + px1) / 2f) < 0.9f && y < py0 && Dither.at(px, py) < 0.3f) col = Col.mix(col, Col.hex(0xD8A888), 0.25f)
        return col
    }

    // ------------------------------------------------------------------ the walls

    /** The hall's walls inside: an oxblood dado with a dark line over it, whitewash above, the footing's stones low down. */
    private fun hallWall(along: Float, z: Float, px: Int, py: Int, dim: Float): Int {
        val h = z - fl
        val col = when {
            h < 5.2f -> if (Dither.at(px, py) < 0.18f || Noise.rnd(px, py, 71) < 0.06f) oxD else if (h > 4.8f) oxL else oxM
            h < 5.8f -> Col.hex(0x3A2420)
            else -> {
                val n = Noise.rnd(px, py, 73)
                if (n < 0.04f) limeL else if (n > 0.965f) limeD else lime
            }
        }
        return Col.scale(if (z > hw - 0.6f) limeL else col, dim)
    }

    /** The walls' outsides: whitewash over a stone plinth; in Friuli river cobbles in courses with bands of brick. */
    private fun outside(a: Float, z: Float, px: Int, py: Int, dim: Float): Int {
        if (cobbles) return Col.scale(cobble(a, z, px, py), dim)
        return outer(z, px, py, dim)
    }

    /** River cobbles laid in courses, a band of three brick courses every so often, the lime mortar between. */
    private fun cobble(a: Float, z: Float, px: Int, py: Int): Int {
        val band = ((z + 0.5f) % 9f)
        if (band < 2.1f) {
            val k = floor(band / 0.7f).toInt()
            val u = a / 0.55f + (k and 1) * 0.5f
            return if ((band - k * 0.7f) < 0.14f || (u - floor(u)) < 0.1f) Col.hex(0xC8BCA6) else if (Noise.rnd(floor(u).toInt(), k, 81) < 0.5f) brickL else brickM
        }
        val ch = 1.15f
        val k = floor(z / ch).toInt(); val fz = z - k * ch
        val u = a / 0.42f + Noise.rnd(k, 83) * 3f
        val b = floor(u).toInt(); val fa = u - b
        val r = (fa - 0.5f) * (fa - 0.5f) * 4f + ((fz / ch) - 0.5f) * ((fz / ch) - 0.5f) * 4f
        if (r > 0.85f) return Col.hex(0xD6CCB6)
        val tone = Noise.rnd(b, k, 85)
        return if (r < 0.25f && fz > ch * 0.55f) Col.hex(0xE0DAD0) else if (tone < 0.35f) Col.hex(0xB4ACA0) else if (tone < 0.7f) Col.hex(0x9C948A) else Col.hex(0xC2B49C)
    }

    /** The two back walls, the doors' opening left in the right one. */
    private fun stationWalls() {
        part(Pal.OUTLINE)
        quadFill(x0, y0, fl, x0, y1 + tk, fl, x0, y1 + tk, hw, x0, y0, hw) { px, py ->
            val y = wallXy(px, x0); val z = wallXz(px, py, x0)
            hallWall(y, z, px, py, 0.88f)
        }
        quadFill(x0 - tk, y1 + tk, 0f, x0, y1 + tk, 0f, x0, y1 + tk, hw, x0 - tk, y1 + tk, hw) { px, py -> outside(0f, wallYz(px, py, y1 + tk), px, py, 1f) }
        quadFill(x0 - tk, y0 - tk, hw, x0, y0 - tk, hw, x0, y1 + tk, hw, x0 - tk, y1 + tk, hw) { px, py -> cut(flatX(px, py, hw) - (x0 - tk), flatY(px, py, hw), px, py) }
        c.penId = 0
        part(Pal.OUTLINE)
        onY(y0, x0, x1 + tk, fl, hw) { x, z, px, py -> if (inOpening(x, z)) 0 else hallWall(x, z, px, py, 1f) }
        // the opening's reveal: the wall's thickness round the doors, its jambs and the arch's underside
        onX(da, y0 - tk, y0, fl, dz) { _, _, _, _ -> Col.scale(lime, 0.72f) }
        quadFill(x1 + tk, y0 - tk, 0f, x1 + tk, y0, 0f, x1 + tk, y0, hw, x1 + tk, y0 - tk, hw) { px, py -> outside(wallXy(px, x1 + tk), wallXz(px, py, x1 + tk), px, py, 0.8f) }
        quadFill(x0 - tk, y0 - tk, hw, x1 + tk, y0 - tk, hw, x1 + tk, y0, hw, x0 - tk, y0, hw) { px, py -> cut(flatY(px, py, hw) - (y0 - tk), flatX(px, py, hw), px, py) }
        c.penId = 0
    }

    /** Whether the back wall's point [x] along it, [z] up, is in the doors' opening (its flat arch over the top). */
    private fun inOpening(x: Float, z: Float): Boolean {
        if (x < da || x > db || z < fl) return false
        val u = (x - da) / (db - da) * 2f - 1f
        return z < dz + dza * (1f - u * u)
    }

    /** The front walls cut down to a stub: the footing and a band of whitewash, their tops. */
    private fun stationStubs() {
        part(Pal.OUTLINE)
        quadFill(x1 + tk, y0, 0f, x1 + tk, y1, 0f, x1 + tk, y1, hs, x1 + tk, y0, hs) { px, py -> outside(wallXy(px, x1 + tk), wallXz(px, py, x1 + tk), px, py, 0.82f) }
        quadFill(x1, y0, hs, x1 + tk, y0, hs, x1 + tk, y1, hs, x1, y1, hs) { px, py -> cut(flatX(px, py, hs) - x1, flatY(px, py, hs), px, py) }
        c.penId = 0
        part(Pal.OUTLINE)
        quadFill(x0, y1 + tk, 0f, x1 + tk, y1 + tk, 0f, x1 + tk, y1 + tk, hs, x0, y1 + tk, hs) { px, py -> outside(wallYx(px, y1 + tk), wallYz(px, py, y1 + tk), px, py, 1f) }
        quadFill(x1 + tk, y1, 0f, x1 + tk, y1 + tk, 0f, x1 + tk, y1 + tk, hs, x1 + tk, y1, hs) { px, py -> outside(5f, wallXz(px, py, x1 + tk), px, py, 0.82f) }
        quadFill(x0, y1, hs, x1 + tk, y1, hs, x1 + tk, y1 + tk, hs, x0, y1 + tk, hs) { px, py -> cut(flatY(px, py, hs) - y1, flatX(px, py, hs), px, py) }
        c.penId = 0
    }

    // ------------------------------------------------------------------ the doors and what is beyond them

    /**
     * What the open doors show of the village at [u] across the opening (0 its left jamb, 1 its right) and [v] up it (0 the
     * floor, 1 the top of the arch): the gravel of the yard, a fence and the meadow, the hills, the sky with the sun's or the
     * moon's light; a house's lit window far off at night.
     */
    private fun beyond(u: Float, v: Float, px: Int, py: Int): Int {
        val ww = (db - da) * 8f; val wh = (dz + dza - fl) * 2f
        val snow = winter
        val yardTop = 0.16f; val meadowTop = 0.3f + Noise.v1(u * 3f, 5) * 0.03f
        val hill = 0.42f + Noise.v1(u * 2.1f + 0.7f, 9) * 0.1f
        val house = u in 0.62f..0.86f && v < 0.5f
        val roof = u in 0.6f..0.88f && v in 0.5f..0.62f - abs(u - 0.74f) * 0.6f
        val col = when {
            v < yardTop -> if (snow) (if (Dither.at(px, py) < 0.3f) Pal.SNOW_M else Pal.SNOW_L) else if (Noise.rnd(px / P, py / P, 3) < 0.4f) gravelD else gravelM
            v < yardTop + 0.05f && (floor(u * ww / 3.2f).toInt() % 2 == 0 || abs(v - yardTop - 0.035f) < 0.012f) -> Pal.WOOD_D
            roof -> if (snow) Pal.SNOW_L else Pal.ROOF_M
            house -> if (env.windows > 0.4f && abs(u - 0.7f) * ww < 1.2f && abs(v - 0.38f) * wh < 1.4f) Pal.WINDOW_LIT else if (u > 0.8f) Pal.WALL_M else Pal.WALL_L
            v < meadowTop -> if (snow) Pal.SNOW_L else env.grass[if (Dither.at(px, py) < 0.4f) 1 else 0]
            v < hill -> if (snow) Col.hex(0xC8D4E4) else when (env.season) { Season.AUTUMN -> Col.hex(0x8A8A4A); else -> Col.hex(0x5E8A5A) }
            else -> skyAt((0.3f + (1f - v) * 0.6f + (Dither.at(px, py) - 0.5f) * 0.06f).coerceIn(0f, 1f))
        }
        val lit = if (house && env.windows > 0.4f && col == Pal.WINDOW_LIT) col else if (v >= hill && !house && !roof) col else env.lit(col)
        return Weather.glass(lit, u, v, ww, wh, t, frame.sky).let { if (frame.sky.clear) lit else it }
    }

    /** The doorway: the village beyond it, drawn on the opening's plane before the doors and the wall round them. */
    private fun doorway() {
        val yv = y0 - tk
        c.penEmissive = true
        onY(yv, da, db, fl, dz + dza) { x, z, px, py ->
            if (!inOpening(x, z)) 0 else beyond((x - da) / (db - da), (z - fl) / (dz + dza - fl), px, py)
        }
        c.penEmissive = false
        // the threshold: a stone sill across the opening
        iso.box(da, y0 - tk, fl - 0.4f, db - da, tk + 0.12f, 0.5f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
    }

    /**
     * The two leaves, hinged at the jambs, opening into the hall (0 shut in the wall's plane, 1 against the jambs at right
     * angles to it): red planks with a white Z brace and iron strap hinges, a latch where they meet.
     */
    private fun doors() {
        val f = doorsOpen().coerceIn(0f, 1f)
        val ang = f * PI.toFloat() * 0.5f
        val l = leafL
        val ca = cos(ang); val sa = sin(ang)
        // the left leaf from (DA, Y0) toward +x (shut) or +y (open); the right one from (DB, Y0) toward −x or +y
        leaf(da, y0, da + l * ca, y0 + l * sa, right = false)
        leaf(db, y0, db - l * ca, y0 + l * sa, right = true)
    }

    /** One leaf from its hinge ([hx], [hy]) to its free edge ([ex], [ey]), up to the flat arch's spring. */
    private fun leaf(hx: Float, hy: Float, ex: Float, ey: Float, right: Boolean) {
        val top = dz
        c.polyBegin()
        c.polyAdd(iso.sx(hx, hy), iso.sy(hx, hy, fl)); c.polyAdd(iso.sx(ex, ey), iso.sy(ex, ey, fl))
        c.polyAdd(iso.sx(ex, ey), iso.sy(ex, ey, top)); c.polyAdd(iso.sx(hx, hy), iso.sy(hx, hy, top))
        val sxh = iso.sx(hx, hy); val sxe = iso.sx(ex, ey)
        val span = sxe - sxh
        val turned = abs(ey - hy) > abs(ex - hx)
        c.polyScan { px, py ->
            // how far along the leaf (0 at the hinge, 1 at its free edge), and how high
            val a = if (abs(span) < 0.5f) 0.5f else ((px + 0.5f - sxh) / span).coerceIn(0f, 1f)
            val wx = hx + (ex - hx) * a; val wy = hy + (ey - hy) * a
            val z = (iso.sy(wx, wy, 0f) - (py + 0.5f)) / K
            c.set(px, py, leafPlank(a, z - fl, turned))
        }
    }

    /** A leaf's face at [a] along it (0 the hinge, 1 its free edge) and [h] up from the floor. */
    private fun leafPlank(a: Float, h: Float, turned: Boolean): Int {
        val hh = dz - fl
        val l = leafL
        val bw = 0.42f / l
        val board = floor(a / bw).toInt(); val fb = a - board * bw
        val edge = min(a, 1f - a) * l
        val dim = if (turned) 0.84f else 1f
        val col = when {
            edge < 0.08f || h > hh - 0.8f || h < 0.5f -> cream
            // the strap hinges from the hinge side, and the latch at the free edge
            a < 0.55f && (abs(h - 3.4f) < 0.4f || abs(h - hh + 3.6f) < 0.4f) -> if (fine && (abs(h - 3.6f) < 0.12f || abs(h - hh + 3.4f) < 0.12f)) ironL else iron
            a > 0.88f && abs(h - hh * 0.48f) < 1.2f && a < 0.95f -> iron
            // the white frame and Z brace
            abs(h - 2.2f) < 0.45f || abs(h - hh + 2.3f) < 0.45f -> cream
            abs((h - 2.2f) / (hh - 4.5f) - a) * l < 0.22f && h > 2.2f && h < hh - 2.3f -> cream
            fb < 0.03f -> doorD
            fine && Noise.v2(board * 3.1f, h * 0.4f, 7) > 0.72f -> Col.scale(doorM, 0.9f)
            Noise.rnd(board, 3) < 0.5f -> doorM
            else -> doorL
        }
        return Col.scale(col, dim)
    }

    /** The brigade's sign over the doors: a cream board, a red border, the letters PGD (in Friuli VVF). */
    private fun signBoard() {
        val ya = y0 + 0.04f
        val a0 = (da + db) / 2f - 1.45f; val a1 = (da + db) / 2f + 1.45f
        val z0 = dz + dza + 0.9f; val z1 = z0 + 4.2f
        onY(ya, a0, a1, z0, z1) { x, z, _, _ ->
            val e = min(min(x - a0, a1 - x) * 4f, min(z - z0, z1 - z))
            if (e < 0.3f) fireD else if (e < 0.55f) Pal.GOLD else cream
        }
        // the letters, three columns of five rows each (closer up the same letters, finer)
        val total = sign.length * 4 - 1
        val cw = 0.16f
        val start = (a0 + a1) / 2f - total * cw / 2f
        for ((k, ch) in sign.withIndex()) {
            val rows = GLYPHS[ch] ?: continue
            for ((r, line) in rows.withIndex()) for ((q, cc) in line.withIndex()) {
                if (cc != 'X') continue
                val x = start + (k * 4 + q) * cw
                val z = z1 - 1.0f - r * 0.55f
                iso.px(x, ya, z, fire)
            }
        }
    }

    // ------------------------------------------------------------------ the back wall's things

    /** The ladder hung on two brackets along the left wall, over the helmets and the picture. */
    private fun ladderOnWall() {
        val x = x0 + 0.18f
        val za = fl + 21.4f; val zb = fl + 24.2f
        val a = 3.0f; val b = 8.7f
        // the brackets
        for (by in floatArrayOf(a + 0.7f, b - 0.7f)) iso.box(x0, by - 0.08f, za - 1.2f, 0.3f, 0.16f, 1.2f, ironL, iron, ironD)
        rod(x, a, za, x, b, za, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        rod(x, a, zb, x, b, zb, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        var y = a + 0.3f
        while (y < b - 0.1f) {
            rod(x, y, za, x, y, zb, Pal.WOOD_L, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
            y += 0.42f
        }
    }

    /** The lantern on its hook right of the doors: an iron frame, glass, its flame lit from dusk. */
    private fun lantern() {
        val ly = y0 + 0.12f
        val cx = iso.ix(lanternX, ly); val hook = iso.iy(lanternX, ly, lanternZ + 1.4f)
        val ty = iso.iy(lanternX, ly, lanternZ); val byy = iso.iy(lanternX, ly, lanternZ - 3.6f)
        val lit = lanternLit()
        val d = detail
        iso.px(lanternX, y0 + 0.03f, lanternZ + 1.8f, iron)
        c.vline(cx, hook, ty, iron)
        c.fillRect(cx - 2 * d, ty, 5 * d, d, iron)
        c.fillRect(cx - d, ty - d, 3 * d, d, ironL)
        val gTop = ty + d; val gBot = byy - d
        for (yy in gTop until gBot) for (xx in cx - 2 * d until cx + 3 * d) {
            val edge = xx < cx - d || xx >= cx + 2 * d
            val col = when {
                edge -> iron
                lit -> if ((yy - gTop) * 2 > (gBot - gTop)) Pal.FLAME[1] else Pal.WINDOW_LIT
                else -> if (xx < cx) Col.hex(0xB8C8D4) else Col.hex(0x8EA0AE)
            }
            if (lit && !edge) glow { c.set(xx, yy, col) } else c.set(xx, yy, col)
        }
        if (lit) glow { c.fillRect(cx, (gTop + gBot) / 2, d, max(1, (gBot - gTop) / 3), Pal.FLAME[0]) }
        c.fillRect(cx - 2 * d, gBot, 5 * d, d, iron)
    }

    // ------------------------------------------------------------------ the left wall's things

    /** The brigade's banner on its pole in the back corner, its cloth hanging along the left wall: red, a gold fringe, a cross. */
    private fun banner() {
        val bx = x0 + 0.35f; val by = y0 + 0.4f
        // the pole, the cross bar and its gilded tip
        rod(bx, by, fl, bx, by, fl + 25f, Pal.WOOD_D, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        knob(bx, by, fl + 25.6f, Pal.GOLD, Pal.GOLD_L, bellD)
        val ya = by + 0.05f; val yb = by + 1.35f
        val za = fl + 11.5f; val zb = fl + 23.5f
        rod(x0 + 0.2f, ya, zb + 0.3f, x0 + 0.2f, yb + 0.1f, zb + 0.3f, Pal.WOOD_M)
        onX(x0 + 0.2f, ya, yb, za - 1.6f, zb) { y, z, px, py ->
            val u = (y - ya) / (yb - ya)
            // the hem in points at its foot, a gold fringe
            val hem = za - 0.9f * abs(sin(u * PI.toFloat() * 3f))
            if (z < hem) return@onX 0
            val e = min(min(u, 1f - u) * 12f, zb - z)
            when {
                z < hem + 0.7f -> if (Dither.at(px, py) < 0.5f) Pal.GOLD else bellD
                tricolore -> when { u < 0.33f -> Col.hex(0x2E8A44); u < 0.67f -> Pal.FLAG_WHITE; else -> Pal.FLAG_RED }.let { if (e < 0.6f) Col.scale(it, 0.85f) else it }
                e < 0.6f -> Pal.GOLD
                // a white cross in a ring on the red
                abs(u - 0.5f) * 1.3f < 0.07f && abs(z - (za + zb) / 2f - 0.5f) < 3f -> Col.hex(0xF6EEDC)
                abs(z - (za + zb) / 2f - 0.5f) < 0.45f && abs(u - 0.5f) < 0.24f -> Col.hex(0xF6EEDC)
                else -> if (Noise.v2(u * 6f, z * 0.3f, 91) > 0.68f) fireD else fire
            }
        }
    }

    /** The dark blue jackets hanging from the pegs, their brass buttons and red collars. */
    private fun jackets() {
        for ((k, py) in pegYs.withIndex()) {
            val xa = x0 + 0.06f
            val top = pegZ - 0.6f; val bot = pegZ - 9.5f - (k % 2) * 0.6f
            onX(xa, py - 0.36f, py + 0.36f, bot, top) { y, z, _, _ ->
                val u = (y - (py - 0.36f)) / 0.72f
                // the shoulders slope down from the peg, the sleeves hang at the sides
                val shoulder = top - abs(u - 0.5f) * 2.6f
                if (z > shoulder) return@onX 0
                when {
                    z > shoulder - 0.9f && abs(u - 0.5f) < 0.18f -> fire
                    abs(u - 0.5f) < 0.03f -> blueD
                    abs(u - 0.47f) < 0.04f && ((top - z) % 1.6f) < 0.5f && z < shoulder - 1.4f -> Pal.GOLD
                    u < 0.14f || u > 0.86f -> blueD
                    u < 0.3f -> blueL
                    else -> blue
                }
            }
        }
    }

    /** The brass helmets on the pegs over the jackets, a comb along each; in Friuli polished steel. A tap sets a glint running over them. */
    private fun helmets() {
        val pk = poked("helmet"); val age = pk?.age(t) ?: 9f
        val (l, m, d) = if (chrome) Triple(steelL, steel, steelD) else Triple(Col.hex(0x5A5660), Col.hex(0x2E2A32), Col.hex(0x1A171C))
        for ((k, py) in pegYs.withIndex()) {
            val hx = x0 + 0.35f
            // the peg
            rod(x0 + 0.02f, py, pegZ, hx, py, pegZ, Pal.WOOD_D)
            val cx = iso.ix(hx, py); val by = iso.iy(hx, py, pegZ + 0.4f)
            val rx = 4f * detail; val ry = 3f * detail
            // the helmet as a little sprite: the comb on top, the dome lit on the left, the brim round its foot
            sprite(cx, by) {
                for ((r, row) in HELMET.withIndex()) for ((q, ch) in row.withIndex()) {
                    val col = when (ch) {
                        'C' -> if (chrome) steelL else bellL; 'c' -> if (chrome) steelD else bellC; 'G' -> if (chrome) steelD else bellL
                        'L' -> l; 'M' -> m; 'D' -> d; 'B' -> m; 'b' -> d; else -> continue
                    }
                    c.set(cx - 5 + q, by - 6 + r, col)
                }
            }
            // a tap: a glint runs over the helmets one after another; the second time they rock on their pegs as it passes
            val step = pk?.step ?: -1
            val g = age - k * 0.22f
            if (step >= 0 && g in 0f..0.7f) {
                val gx = cx - rx + g / 0.7f * 2f * rx
                glow { c.fillEllipse(gx, by - ry * 1.2f, max(1f, rx * 0.3f), ry * 0.9f, steelL) }
                if (step == 1) sprite(cx, by) { c.set(cx - 5 + (if (sin(g * 20f) > 0f) 0 else 1), by - 7, l); c.set(cx + 4, by - 1, d) }
            }
        }
    }

    /** Two fire axes crossed on their brackets, red handles, steel heads. */
    private fun axes() {
        val xa = x0 + 0.1f
        for ((k, dir) in intArrayOf(1, -1).withIndex()) {
            val y0a = axeY - 0.55f * dir; val y1a = axeY + 0.55f * dir
            val z0a = fl + 13.4f; val z1a = fl + 20.2f
            rod(xa, y0a, z0a, xa, y1a, z1a, fireD, fire, fireD, fireK)
            // the head at the top end: the blade, a pick behind it
            val hy = y1a; val hz = z1a
            onX(xa + 0.01f, hy - 0.42f, hy + 0.42f, hz - 1.8f, hz + 0.6f) { y, z, _, _ ->
                val off = (y - hy) * dir
                when {
                    off > 0.05f && z > hz - 1.6f + (off - 0.05f) * 1.2f && z < hz + 0.5f -> if (off > 0.32f) steelL else steel
                    off < -0.05f && abs(z - hz + 0.5f) < 0.35f + (off + 0.42f) * 0.6f -> steelD
                    abs(off) <= 0.05f && z > hz - 1.4f -> steelD
                    else -> 0
                }
            }
            if (k == 0) iso.box(xa - 0.02f, axeY - 0.1f, fl + 16.4f, 0.2f, 0.2f, 0.7f, ironL, iron, ironD)
        }
    }

    /**
     * The picture of St Florian in its frame: the saint in his helmet and red cloak pouring a bucket of water onto a little
     * burning house; in Friuli Santa Barbara with her tower and a palm.
     */
    private fun picture() {
        val xa = x0 + 0.05f
        val ww = picB - picA; val hh = picZ1 - picZ0
        onX(xa, picA, picB, picZ0, picZ1) { y, z, px, py ->
            val u = (y - picA) / ww; val v = (z - picZ0) / hh
            val e = min(min(u, 1f - u) * ww * 4f, min(v, 1f - v) * hh)
            if (e < 0.45f) return@onX Pal.GOLD.let { if (Dither.at(px, py) < 0.4f) bellD else it }
            if (e < 0.75f) return@onX bellD
            if (barbara) barbaraAt(u, v, px, py) else florian(u, v, px, py)
        }
    }

    private fun florian(u: Float, v: Float, px: Int, py: Int): Int {
        // the house in the lower right, its roof in flames, the water falling on it from the saint's bucket
        val inHouse = u in 0.58f..0.9f && v in 0.12f..0.4f
        val roof = u in 0.54f..0.94f && v in 0.4f..0.52f - abs(u - 0.74f) * 0.3f
        val flame = u in 0.56f..0.92f && v in 0.48f..0.62f + 0.06f * sin(u * 30f + t.toFloat() * 4f) && Noise.rnd((u * 30).toInt(), (v * 30).toInt(), 93) < 0.8f
        val water = abs(u - (0.42f + (0.62f - v) * 0.55f)) < 0.04f && v in 0.4f..0.66f
        // the saint: his red cloak, his helmet's gold, his face, the bucket held out
        val body = u in 0.16f..0.42f && v in 0.1f..0.68f - abs(u - 0.29f) * 0.4f
        val head = (u - 0.29f) * (u - 0.29f) * 60f + (v - 0.76f) * (v - 0.76f) * 140f < 1f
        val helmet = head && v > 0.79f
        val bucket = u in 0.4f..0.5f && v in 0.62f..0.72f
        val halo = abs((u - 0.29f) * (u - 0.29f) * 40f + (v - 0.78f) * (v - 0.78f) * 90f - 1f) < 0.25f
        return when {
            bucket -> Pal.WOOD_D
            water -> waterC
            helmet -> Pal.GOLD
            head -> Pal.SKIN
            halo -> Pal.GOLD_L
            body -> if (u < 0.26f) fireD else fire
            flame -> if (v > 0.56f) Pal.FLAME[2] else Pal.FLAME[1]
            roof -> Pal.ROOF_D
            inHouse -> if (u in 0.68f..0.76f && v < 0.26f) Pal.DOOR else Pal.WALL_L
            v < 0.12f -> Col.hex(0x5E7A3E)
            else -> Col.mix(Col.hex(0x7AA6D2), Col.hex(0xD8E8F4), v)
        }
    }

    private fun barbaraAt(u: Float, v: Float, px: Int, py: Int): Int {
        // Santa Barbara: her blue cloak over a red gown, a crown, the palm in her hand, her tower with three windows beside her
        val tower = u in 0.6f..0.86f && v in 0.1f..0.82f
        val windows = tower && abs(u - 0.73f) < 0.04f && (abs(v - 0.3f) < 0.04f || abs(v - 0.46f) < 0.04f || abs(v - 0.62f) < 0.04f)
        val body = u in 0.14f..0.46f && v in 0.08f..0.66f - abs(u - 0.3f) * 0.3f
        val cloak = body && (u < 0.2f || u > 0.4f)
        val head = (u - 0.3f) * (u - 0.3f) * 60f + (v - 0.75f) * (v - 0.75f) * 140f < 1f
        val crown = head && v > 0.8f
        val palm = abs(u - (0.48f + (v - 0.4f) * 0.3f)) < 0.02f && v in 0.4f..0.8f || (v in 0.72f..0.84f && abs(u - 0.56f) < (0.84f - v) * 0.8f)
        return when {
            crown -> Pal.GOLD
            head -> Pal.SKIN
            palm -> Col.hex(0x3E7A3A)
            cloak -> Col.hex(0x3A5A9E)
            body -> fire
            windows -> Col.hex(0x2A2A3A)
            tower -> if (u > 0.8f) Pal.STONE_M else Pal.STONE_L
            v < 0.1f -> Col.hex(0x5E7A3E)
            else -> Col.mix(Col.hex(0xD8B87A), Col.hex(0xF4E4C0), v)
        }
    }

    /** The signal horn on its nail under the axes: a brass horn coiled once, its bell out to the right, a red cord. */
    private fun horn() {
        val blown = fxOn("horn")
        val shake = if (blown > 0.05f) (sin(t * 38.0).toFloat() * 0.25f * K) else 0f
        val hx = x0 + 0.12f
        val cx = iso.sx(hx, hornY) + shake; val cy = iso.sy(hx, hornY, hornZ)
        // the cord from the nail
        c.line(floor(cx).toInt(), floor(cy - 3.5f * K).toInt(), floor(cx - 1.5f * K).toInt(), floor(cy - 1.2f * K).toInt(), fire)
        c.line(floor(cx).toInt(), floor(cy - 3.5f * K).toInt(), floor(cx + 2f * K).toInt(), floor(cy - 0.9f * K).toInt(), fire)
        iso.px(hx - 0.06f, hornY, hornZ + 3.6f, iron)
        // the coil and the bell
        val r = 1.5f * K
        arc(cx, cy, r, r * 0.8f, bellD)
        arc(cx, cy, r - 1f, r * 0.8f - 1f, bellC)
        if (fine) arc(cx, cy - 0.5f, r - 2f, r * 0.8f - 2f, bellL, PI.toFloat(), 2f * PI.toFloat())
        val bx = cx + r + 0.5f * K
        for (i in 0 until (2.5f * K).toInt()) {
            val hgt = 0.6f * K + i * 0.45f
            c.vline(floor(bx + i).toInt(), floor(cy - hgt).toInt(), floor(cy + hgt * 0.6f).toInt(), if (i == 0) bellD else if (i % 2 == 0) bellL else bellC)
        }
        c.set(floor(cx - r).toInt(), floor(cy).toInt(), bellL)
    }

    /** The leather buckets on their shelf under the picture, a white number on each. */
    private fun buckets() {
        iso.box(x0, shelfA, shelfZ - 0.6f, 0.75f, shelfB - shelfA, 0.6f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        for (k in 0 until 4) {
            val by = shelfA + 0.3f + k * 0.45f
            val bx = x0 + 0.38f
            val cx = iso.sx(bx, by); val base = iso.sy(bx, by, shelfZ)
            val hh = 3.4f * K; val rTop = 1.25f * K; val rBot = 0.9f * K
            for (yy in 0 until hh.toInt()) {
                val f = yy / hh
                val r = rBot + (rTop - rBot) * f
                val row = floor(base - yy).toInt()
                for (xx in floor(cx - r).toInt()..floor(cx + r).toInt()) {
                    val uu = (xx + 0.5f - cx) / r
                    c.set(xx, row, if (uu < -0.5f) leatherL else if (uu > 0.5f) leatherD else if (abs(f - 0.6f) < 0.12f && abs(uu) < 0.25f) cream else leather)
                }
            }
            c.fillEllipse(cx, base - hh, rTop, rTop * 0.4f, leatherD)
            if (fine) c.fillEllipse(cx, base - hh, rTop - 1f, rTop * 0.4f - 1f, Col.hex(0x2E1A10))
            // the handle
            arc(cx, base - hh - 0.5f, rTop * 0.9f, 1.4f * K, leatherD, PI.toFloat(), 2f * PI.toFloat())
        }
    }

    /** The bench along the left wall under the jackets. */
    private fun bench() {
        val xa = x0 + 0.08f; val xb = x0 + 0.85f
        iso.box(xa, benchY0, benchZ - 0.7f, xb - xa, benchY1 - benchY0, 0.7f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        for (yy in floatArrayOf(benchY0 + 0.2f, benchY1 - 0.35f)) iso.box(xb - 0.25f, yy, fl, 0.15f, 0.15f, benchZ - 0.7f - fl, Pal.WOOD_M, Pal.WOOD_D, Pal.WOOD_X)
        if (fine) iso.line(xa, benchY0, benchZ, xb, benchY0, benchZ, Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.25f))
    }

    // ------------------------------------------------------------------ the hose and its reel

    /** The hose reel by the left wall: two spoked wheels, the drum between them with the canvas hose wound on it, the handle. */
    private fun reel() {
        val wy0 = reelY - 0.75f; val wy1 = reelY + 0.75f
        val axleZ = fl + 5.2f
        val r = 4.4f
        // the far wheel, the drum and its hose, the near wheel
        wheel(reelX, wy0, axleZ, r)
        val cxA = iso.sx(reelX, wy0); val cxB = iso.sx(reelX, wy1)
        val cyA = iso.sy(reelX, wy0, axleZ); val cyB = iso.sy(reelX, wy1, axleZ)
        val dr = 2.7f * K
        val n = max(6, (abs(cxB - cxA) + abs(cyB - cyA)).toInt())
        for (i in 0..n) {
            val f = i / n.toFloat()
            val cx = cxA + (cxB - cxA) * f; val cy = cyA + (cyB - cyA) * f
            c.fillEllipse(cx, cy, dr * 0.55f, dr, if (i % (2 * detail) < detail) hoseD else hoseC)
        }
        c.fillEllipse(cxB, cyB, dr * 0.55f, dr, hoseL)
        // the coils' rings on the near face, closer up
        if (fine) for (k in 1..3) arc(cxB, cyB, dr * 0.55f * k / 4f, dr * k / 4f, hoseD)
        wheel(reelX, wy1, axleZ, r)
        // the T-handle to push it
        rod(reelX, wy1, axleZ, reelX + 1.6f, wy1 + 0.2f, axleZ + 5f, Pal.WOOD_D, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
    }

    /** A spoked wheel in the plane y = [wy], its hub at ([wx], [wz]), [r] nominal px round, an iron tyre. */
    private fun wheel(wx: Float, wy: Float, wz: Float, r: Float) {
        val cx = iso.sx(wx, wy); val cy = iso.sy(wx, wy, wz)
        val rx = r * K * 0.9f; val ry = r * K
        c.fillEllipse(cx, cy, rx, ry, ironD)
        c.fillEllipse(cx, cy, rx - 0.8f * K * 0.5f - 0.5f, ry - 0.8f * K * 0.5f - 0.5f, Pal.WOOD_M)
        c.fillEllipse(cx, cy, rx - 1.6f * K * 0.5f - 0.5f, ry - 1.6f * K * 0.5f - 0.5f, Col.hex(0x2A1E16))
        for (k in 0 until 8) {
            val a = k * PI.toFloat() / 4f
            c.line(floor(cx).toInt(), floor(cy).toInt(), floor(cx + cos(a) * (rx - K)).toInt(), floor(cy + sin(a) * (ry - K)).toInt(), Pal.WOOD_L)
        }
        c.fillCircle(cx, cy, 0.7f * K, iron)
    }

    /** The hose's end run from the reel across the floor to the pump's outlet, and on to the nozzle at the front while it pumps. */
    private fun hoseRun() {
        val z = fl + 0.6f
        val pts = listOf(floatArrayOf(reelX + 0.1f, reelY + 0.6f), floatArrayOf(3.4f, 8.2f), floatArrayOf(4.6f, 7.4f), floatArrayOf(px0 + 0.3f, py1 - 0.2f))
        for (i in 0 until pts.size - 1) hoseLine(pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], z)
        if (fxOn("pump") > 0.02f) {
            // out to the nozzle on the front wall's stub, aimed into the yard
            hoseLine(px1, py1 - 0.3f, 7.4f, 7.6f, z)
            hoseLine(7.4f, 7.6f, NOZZLE_X, NOZZLE_Y, z)
            val nx = iso.sx(NOZZLE_X, NOZZLE_Y); val ny = iso.sy(NOZZLE_X, NOZZLE_Y, NOZZLE_Z)
            c.line(floor(nx).toInt(), floor(ny).toInt(), floor(nx + 2.2f * K).toInt(), floor(ny + 1f * K).toInt(), bellC)
            if (fine) c.line(floor(nx).toInt(), floor(ny).toInt() - 1, floor(nx + 2.2f * K).toInt(), floor(ny + 1f * K).toInt() - 1, bellL)
        }
    }

    private fun hoseLine(xa: Float, ya: Float, xb: Float, yb: Float, z: Float) {
        val ax = iso.ix(xa, ya); val ay = iso.iy(xa, ya, z); val bx = iso.ix(xb, yb); val by = iso.iy(xb, yb, z)
        for (o in 0 until max(1, detail)) c.line(ax, ay + o, bx, by + o, if (o == 0) hoseL else hoseC)
        c.line(ax, ay + max(1, detail), bx, by + max(1, detail), hoseD)
    }

    // ------------------------------------------------------------------ the pump

    /**
     * The hand pump from Gorica: a red box on four spoked wheels, the two brass cylinders and the air chamber between them
     * on top, the long handles along both sides on their pivot posts, see-sawing while it is worked; the drawbar out to the
     * doors.
     */
    private fun pump() {
        val work = fxOn("pump")
        val rock = if (work > 0.02f) sin(t * (5.0 + 5.0 * work)).toFloat() * (1.4f + 2.2f * work) else 0f
        val wz = fl + 3.1f
        val mx = (px0 + px1) / 2f; val my = (py0 + py1) / 2f
        // the drawbar out toward the doors, its crossbar to pull it by
        rod(mx, py0, fl + 3.2f, mx, py0 - 1.7f, fl + 1.4f, Pal.WOOD_D, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        rod(mx - 0.45f, py0 - 1.7f, fl + 1.4f, mx + 0.45f, py0 - 1.7f, fl + 1.4f, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        // the far beam (along the left side) behind the box, then the far wheels, the box, the brass on it
        beam(px0 - 0.3f, my, rock)
        wheel(px0 + 0.15f, py0 + 0.45f, wz, 3.1f)
        wheel(px1, py0 + 0.45f, wz, 3.1f)
        iso.box(px0, py0, bodyZ0, px1 - px0, py1 - py0, bodyZ1 - bodyZ0, fireL, fire, fireD)
        // a gilded line round the box, a little scroll on its side, its rivets closer up
        iso.line(px0, py1, bodyZ1 - 0.7f, px1, py1, bodyZ1 - 0.7f, Pal.GOLD)
        iso.line(px1, py0, bodyZ1 - 0.7f, px1, py1, bodyZ1 - 0.7f, bellD)
        iso.line(px0, py1, bodyZ0 + 0.7f, px1, py1, bodyZ0 + 0.7f, Pal.GOLD)
        if (fine) for (k in 0 until 6) iso.px(px0 + 0.15f + k * 0.3f, py1, bodyZ1 - 1.5f, fireK)
        val topZ = bodyZ1
        brassDrum(mx - 0.1f, py0 + 0.55f, topZ, 4.2f, 0.55f)
        brassDrum(mx - 0.1f, py1 - 0.55f, topZ, 4.2f, 0.55f)
        brassDrum(mx, my, topZ, 6.8f, 0.72f, dome = true)
        // the outlet on its side for the hose
        iso.box(px1, py1 - 0.5f, bodyZ0 + 1.2f, 0.35f, 0.3f, 0.8f, bellL, bellC, bellD)
        // the near wheels, then the near beam on its upright
        wheel(px0 + 0.15f, py1 - 0.35f, wz, 3.1f)
        wheel(px1, py1 - 0.35f, wz, 3.1f)
        beam(px1 + 0.3f, my, rock)
    }

    /**
     * A pump beam along y at [bx], pivoting on its iron upright at the middle [my], see-sawing by [rock] nominal px: a long
     * wooden bar with a crossbar at each end for the men to work it.
     */
    private fun beam(bx: Float, my: Float, rock: Float) {
        val pz = bodyZ1 + 3.6f
        rod(bx, my, bodyZ0 + 1f, bx, my, pz, ironD, ironL, iron, ironD)
        val ya = py0 - 0.6f; val yb = py1 + 0.6f
        val za = pz + rock; val zb = pz - rock
        rod(bx, ya, za, bx, yb, zb, Pal.WOOD_L, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        rod(bx - 0.3f, ya, za, bx + 0.3f, ya, za, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        rod(bx - 0.3f, yb, zb, bx + 0.3f, yb, zb, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        knob(bx, my, pz, iron, ironL, ironD)
    }

    /** A brass drum standing on the pump at ([x], [y], [z]), [hgt] nominal px tall, [r] cells round; [dome]: a round top. */
    private fun brassDrum(x: Float, y: Float, z: Float, hgt: Float, r: Float, dome: Boolean = false) {
        val cx = iso.sx(x, y); val base = iso.sy(x, y, z)
        val rx = r * 2f * K; val ry = rx * 0.5f
        drum(cx, base, base - hgt * K, rx, ry, bellL, bellC, bellD, steelL)
        c.fillEllipse(cx, base - hgt * K, rx, ry, if (dome) bellC else bellL)
        if (dome) {
            c.fillEllipse(cx, base - hgt * K - ry * 0.6f, rx * 0.8f, ry * 1.2f, bellC)
            c.fillEllipse(cx - rx * 0.3f, base - hgt * K - ry * 1.1f, rx * 0.25f, ry * 0.4f, bellL)
        }
    }

    /** The jet while the pump is worked: from the nozzle over the front wall in an arc into the yard, spray and a splash. */
    private fun jet() {
        val work = fxOn("pump")
        if (work < 0.02f) return
        val nx = iso.sx(NOZZLE_X, NOZZLE_Y) + 2.2f * K; val ny = iso.sy(NOZZLE_X, NOZZLE_Y, NOZZLE_Z) + K
        val reach = (3f + 6f * work) * 4f * K
        val lift = (5f + 7f * work) * K
        val pulse = 0.85f + 0.15f * sin(t * (5.0 + 5.0 * work)).toFloat()
        s.fx {
            val n = (reach / max(1, detail)).toInt()
            for (i in 0..n) {
                val f = i / n.toFloat()
                val x = nx + reach * f * pulse
                val y = ny - lift * 4f * f * (1f - f) + reach * f * 0.42f * pulse
                val w = max(1f, (1f - f * 0.5f) * 0.8f * K * work)
                for (o in 0 until w.toInt().coerceAtLeast(1)) {
                    c.blend(floor(x).toInt(), floor(y).toInt() + o, if (i % 3 == 0) waterL else waterC, 0.85f)
                }
            }
            // the spray and the splash where it lands
            val ex = nx + reach * pulse; val ey = ny + reach * 0.42f * pulse
            for (j in 0 until (10 * work).toInt() + 4) {
                val ph = ((t * 1.6 + Noise.rnd(j, 101)) % 1.0).toFloat()
                val ang = (Noise.rnd(j, 103) - 0.5f) * 2.4f - PI.toFloat() / 2f
                val dd = ph * (2f + 3f * work) * K
                c.blend(floor(ex + cos(ang) * dd).toInt(), floor(ey + sin(ang) * dd + ph * ph * 3f * K).toInt(), waterL, 0.8f * (1f - ph))
            }
            c.ditherCircle(ex, ey + K, (1.5f + 2f * work) * K, waterC, 0.35f)
        }
    }

    // ------------------------------------------------------------------ the tower outside

    /**
     * The hose tower: plastered over a stone footing (in Friuli cobbles with bands of brick), its small door and a slit window
     * on the front, the belfry's arches on both faces under the cornice, the pyramid roof of shingles (coppi in Friuli) with a
     * cross on its tip; snow on the roof in winter.
     */
    private fun tower() {
        val front = ty1; val right = tx1
        // the front face (y = TY1) and the right face (x = TX1), each with the belfry's arch open
        onY(front, tx0, tx1, 0f, twB) { x, z, px, py -> towerFace(x - tx0, tx1 - tx0, z, px, py, 1f) }
        onX(right, ty0, ty1, 0f, twB) { y, z, px, py -> towerFace(ty1 - y, ty1 - ty0, z, px, py, 0.8f) }
        // the bell's dark inside, seen through the arches (drawn as the faces leave it)
        // the cornice under the roof
        iso.box(tx0 - 0.15f, ty0 - 0.15f, twB, tx1 - tx0 + 0.3f, ty1 - ty0 + 0.3f, 0.9f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        // the roof
        val (rl, rr) = when {
            winter -> Pal.SNOW_L to Pal.SNOW_M
            coppi -> Pal.ROOF_L to Pal.ROOF_D
            else -> Pal.SHINGLE_L to Pal.SHINGLE_D
        }
        iso.pyramid(tx0 - 0.3f, ty0 - 0.3f, twB + 0.9f, tx1 - tx0 + 0.6f, ty1 - ty0 + 0.6f, twR - twB - 0.9f, rl, rr)
        roofCourses()
        // the cross on its tip
        val ax = (tx0 + tx1) / 2f; val ay = (ty0 + ty1) / 2f
        rod(ax, ay, twR, ax, ay, twR + 3.2f, ironD)
        rod(ax - 0.25f, ay + 0.25f, twR + 2.2f, ax + 0.25f, ay - 0.25f, twR + 2.2f, ironD)
        // the door and the slit window on the front
        onY(front + 0.02f, tx0 + 0.65f, tx1 - 0.65f, 0f, 12.5f) { x, z, _, _ ->
            val e = min(min(x - tx0 - 0.65f, tx1 - 0.65f - x) * 4f, 12.5f - z)
            if (e < 0.5f) Pal.STONE_L else if (Noise.rnd(floor((x - tx0) / 0.27f).toInt(), 0, 111) < 0.5f) doorM else doorD
        }
        onY(front + 0.02f, (tx0 + tx1) / 2f - 0.15f, (tx0 + tx1) / 2f + 0.15f, 21f, 26.5f) { _, _, _, _ -> Col.hex(0x2A2420) }
    }

    /** The tower's face at [a] across it (of [w]), [z] up: the footing, the plaster (or cobbles), the belfry's round arch open. */
    private fun towerFace(a: Float, w: Float, z: Float, px: Int, py: Int, dim: Float): Int {
        // the belfry's arch, open: the bell inside is drawn after it
        val u = (a / w) * 2f - 1f
        if (z > twZ + 1f && z < twB - 0.8f && abs(u) < 0.62f) {
            val archTop = twB - 0.8f - 2.2f * (1f - sqrt1(1f - (u / 0.62f) * (u / 0.62f)))
            if (z < archTop) return Col.scale(Col.hex(0x2A2026), dim)
        }
        if (cobbles) return Col.scale(cobble(a, z, px, py), dim)
        val col = when {
            z < 3f -> { val n = Noise.rnd(px / max(1, detail), py / max(1, detail), 113); if (n < 0.3f) Pal.STONE_D else if (n < 0.85f) Pal.STONE_M else Pal.STONE_L }
            z < 3.5f -> Pal.STONE_L
            abs(z - twZ) < 0.5f -> Col.hex(0xE0D6BE)
            else -> { val n = Noise.rnd(px, py, 115); if (n < 0.05f) limeL else if (n > 0.95f) limeD else Col.hex(0xF0E6CC) }
        }
        return Col.scale(col, dim)
    }

    private fun sqrt1(v: Float): Float = kotlin.math.sqrt(max(0f, v))

    /** The roof's courses of shingles (or the coppi's channels) on its two faces we see. */
    private fun roofCourses() {
        if (winter) return
        val ax = (tx0 + tx1) / 2f; val ay = (ty0 + ty1) / 2f
        val z0 = twB + 0.9f
        val line = if (coppi) Pal.ROOF_D else Pal.SHINGLE_D
        for (k in 1 until 6) {
            val f = k / 6f
            val zz = z0 + (twR - z0) * f
            val half = (1f - f)
            iso.line(ax - (ax - tx0 + 0.3f) * half, ty1 + 0.3f - (ty1 + 0.3f - ay) * f, zz, ax + (tx1 + 0.3f - ax) * half, ty1 + 0.3f - (ty1 + 0.3f - ay) * f, zz, line)
            iso.line(tx1 + 0.3f - (tx1 + 0.3f - ax) * f, ay - (ay - ty0 + 0.3f) * half, zz, tx1 + 0.3f - (tx1 + 0.3f - ax) * f, ay + (ty1 + 0.3f - ay) * half, zz, line)
        }
    }

    /** The bell in the belfry, swinging on its yoke as `bell` (or a tap) has it. */
    private fun bell() {
        val bx = (tx0 + tx1) / 2f; val by = ty1 - 0.6f
        val pivot = twB - 1.6f
        val cx = iso.sx(bx, by); val cy = iso.sy(bx, by, pivot)
        val len = 4.6f * K
        val a = bellAngle
        // the yoke
        c.fillRect(floor(cx - 1.6f * K).toInt(), floor(cy - 0.6f * K).toInt(), (3.2f * K).toInt(), max(1, K / 2 + 1), Pal.WOOD_D)
        // the bell: a flared shape hanging from the pivot, turned by the angle
        val n = (len).toInt()
        for (i in 0..n) {
            val f = i / n.toFloat()
            val half = (0.7f + 1.3f * f * f) * K
            val mx = cx + sin(a) * len * f; val my = cy + cos(a) * len * f
            val x0i = floor(mx - half).toInt(); val x1i = floor(mx + half).toInt()
            for (xx in x0i..x1i) {
                val uu = (xx + 0.5f - mx) / half
                c.set(xx, floor(my).toInt(), if (uu < -0.45f) bellL else if (uu > 0.5f) bellD else bellC)
            }
        }
        // its lip and the clapper under it
        val lx = cx + sin(a) * len; val ly = cy + cos(a) * len
        c.hline(floor(lx - 2f * K).toInt(), floor(lx + 2f * K).toInt(), floor(ly).toInt(), bellD)
        c.fillCircle(lx - sin(a) * 0.8f * K, ly + 0.7f * K, 0.55f * K, ironD)
    }

    /** The bell's rope from the belfry down the tower's front to beside its door, going up and down as the bell swings. */
    private fun rope() {
        val rx = tx1 - 0.35f; val ry = ty1 + 0.06f
        val lift = bellAngle * 3f
        val top = twB - 3f; val bot = 4.2f + lift
        val ax = iso.ix(rx, ry); val ay = iso.iy(rx, ry, top); val by = iso.iy(rx, ry, bot)
        c.vline(ax, ay, by, Col.hex(0xC8B488))
        if (fine) c.vline(ax + 1, ay, by, Col.hex(0x9A8660))
        c.fillRect(ax - detail / 2, by, max(1, detail), max(2, 2 * detail), Col.hex(0xC8B488))
    }

    /** A rain barrel on its trestle by the tower's door, catching the water off its roof: staves, iron hoops, water in it. */
    private fun waterBarrel() {
        val bx = tx0 + 0.75f; val by = ty1 + 0.85f
        iso.box(bx - 0.55f, by - 0.5f, 0f, 1.1f, 1.0f, 1.6f, Pal.WOOD_M, Pal.WOOD_D, Pal.WOOD_X)
        val cx = iso.sx(bx, by); val base = iso.sy(bx, by, 1.6f)
        val rx = 2.6f * K; val ry = rx * 0.5f; val hh = 8.5f * K
        drum(cx, base, base - hh, rx, ry, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D, Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.3f))
        for (f in floatArrayOf(0.18f, 0.5f, 0.82f)) {
            val yy = base - hh * f
            for (xx in floor(cx - rx).toInt()..floor(cx + rx).toInt()) {
                val u = (xx + 0.5f - cx) / rx
                if (abs(u) > 1f) continue
                c.set(xx, floor(yy + sqrt1(1f - u * u) * ry).toInt(), if (u < -0.4f) ironL else iron)
            }
        }
        c.fillEllipse(cx, base - hh, rx, ry, Pal.WOOD_D)
        c.fillEllipse(cx, base - hh + 0.5f, rx - K * 0.5f - 0.5f, ry - K * 0.25f - 0.5f, if (winter) Pal.ICE_M else env.lit(Col.hex(0x3A6488)))
    }

    private fun bellRings(swing: Float) {
        if (swing < 0.05f) return
        val bx = (tx0 + tx1) / 2f; val by = ty1 - 0.6f
        val cx = iso.sx(bx, by); val cy = iso.sy(bx, by, twB - 4f)
        val ring = ((t * 2.1) % 1.0).toFloat()
        s.fx { PokeArt.rings(c, cx, cy, ring, 16f * K, Col.hex(0xFFF0C0), max(1, detail), 0.55f * swing, 2) }
    }

    private fun hornCalls() {
        val blown = fxOn("horn")
        if (blown < 0.05f) return
        val cx = iso.sx(x0 + 0.12f, hornY) + 3.5f * K; val cy = iso.sy(x0 + 0.12f, hornY, hornZ)
        val u = ((t * 1.6) % 1.0).toFloat()
        s.fx { PokeArt.rings(c, cx, cy, u, (8f + 10f * blown) * K, Col.hex(0xFFE8A0), max(1, detail), 0.6f * blown, 3) }
    }

    // ------------------------------------------------------------------ the light

    private fun lights() {
        if (lanternLit()) {
            val lx = lanternX; val ly = y0 + 0.6f; val lz = lanternZ - 2f
            s.light(iso.sx(lx, ly), iso.sy(lx, ly, lz), 92f, 1.1f * max(env.windows, if (fx("lantern") != null) 0.75f else 0f))
            s.fx {
                c.penEmissive = true
                c.ditherCircle(iso.sx(lx, y0 + 0.12f), iso.sy(lx, y0 + 0.12f, lanternZ - 1.8f), 5f * K, Pal.FLAME[1], 0.09f * max(env.windows, 0.6f))
                c.penEmissive = false
            }
        }
        // the daylight through the open doors, a patch of it on the bricks
        val open = doorsOpen()
        if (env.sun > 0f && open > 0.3f) s.light(iso.sx((da + db) / 2f, y0 + 1.6f), iso.sy((da + db) / 2f, y0 + 1.6f, fl + 2f), 40f, 0.45f * open * env.sun.coerceIn(0f, 1f) * (1f - frame.sky.gloom * 0.7f))
    }

    companion object {
        /** The nozzle's end on the front wall's stub, where the jet leaves it while the pump is worked. */
        private const val NOZZLE_X = 9.2f
        private const val NOZZLE_Y = 8.4f
        private const val NOZZLE_Z = 5.6f

        /** A helmet on its peg (C the comb's light, L M D the dome lit to shaded, B b the brim). */
        private val HELMET = listOf(
            "...CCc...",
            "..LCcMD..",
            ".LMCcMMD.",
            ".LGMMMMD.",
            "LLMMMMMMD",
            "BBBBBBBBB",
            "..bbbbbbb",
        )

        /** The sign's letters, three columns of five rows. */
        private val GLYPHS = mapOf(
            'P' to listOf("XXX", "X.X", "XXX", "X..", "X.."),
            'G' to listOf("XXX", "X..", "X.X", "X.X", "XXX"),
            'D' to listOf("XX.", "X.X", "X.X", "X.X", "XX."),
            'V' to listOf("X.X", "X.X", "X.X", "X.X", ".X."),
            'F' to listOf("XXX", "X..", "XX.", "X..", "X.."),
        )
    }
}
