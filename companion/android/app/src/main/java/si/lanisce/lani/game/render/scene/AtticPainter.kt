package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * V podstrešju: the attic bedroom under the roof (Vida and Nejc's, where Nejc sleeps and his old cradle still stands) as a
 * diorama, the kitchen's plot and floor ([RoomPainter]) under a roof. The left wall is the gable, its top following the roof
 * up to the ridge and down again, a small window in it; on the right the low knee wall under the eaves with the roof's
 * underside rising from it to the ridge beam over the room's middle, rafters and boards (the tiles between the battens
 * showing at level 1). Under the slope Nejc's bed with a patchwork quilt, the cradle at its foot; by the gable the chest
 * and the wardrobe, the mirror over the chest; the stairs coming up through the floor in the front corner, a rail round
 * their opening; the lamp hanging from the ridge beam.
 *
 * By the house's level ([si.lanisce.lani.game.scene.SceneFixtures]): at level 1 the bed with its pillow and blanket, the
 * cradle, a plain chest, the roof, the window and the stairs, rough boards and rough plaster; at level 2 the wardrobe, a
 * rug, the lamp, Nejc's ball, the roof lined with boards, the chest painted, whitewash; at level 3 the mirror and a teddy
 * bear on the chest. Its effects: `lamp` (0 dark, 1 lit; not set: lit from dusk), `cradle` (rocking, how far).
 *
 * At night ([beds]) Nejc sleeps in his own bed ("cot", a child's), his head on its pillow, under the patchwork quilt; Vida
 * on a straw mattress rolled out under the slope past the cradle ("mattress"), her head toward him, under a blue wool
 * blanket with a white check. The lamp is out unless a dialog lit it.
 */
internal class AtticPainter : RoomPainter() {
    override val art = "attic"

    override val clockY = 4.9f

    // the knee wall under the eaves KH high; the ridge at YM, RH high, nearer the back than the front: the back slope is
    // steeper than we look down at the room, so we see its underside over the knee wall (and it hides nothing in front of it)
    private val kh = fl + 6f; private val ym = 4.2f; private val rh = fl + 31f
    // the stairwell in the front corner
    private val sa = 6.8f; private val sb = 8.4f; private val sc = 6.4f; private val sd = 8.4f

    /** The height of the roof over the gable wall at [y]: up from the eaves at the back to the ridge, down to the front's. */
    private fun roofZ(y: Float): Float =
        if (y <= ym) kh + (rh - kh) * ((y - y0) / (ym - y0)).coerceIn(0f, 1f)
        else kh + (rh - kh) * ((y1 + tk - y) / (y1 + tk - ym)).coerceIn(0f, 1f)

    override fun soot(d: Float, z: Float): Float = 0f

    /** Plaster: rough and patchy at level 1, whitewashed from level 2. */
    override fun inner(along: Float, z: Float, px: Int, py: Int, dim: Float, sootF: Float): Int {
        val n = Noise.rnd(px, py, 7)
        val col = if (lv < 2) {
            val patch = Noise.v2(along * 1.6f, z * 0.2f, 9)
            if (patch > 0.68f && Dither.at(px, py) < 0.5f) Col.hex(0xB8A88E) else if (n < 0.05f) Col.hex(0xD8CCB4) else if (n > 0.93f) Col.hex(0xB0A28A) else Col.hex(0xCABDA4)
        } else if (n < 0.04f) limeL else if (n > 0.965f) limeD else lime
        return Col.scale(col, dim)
    }

    /** Boards: rough, dark and dusty at level 1, planed from level 2. */
    override fun floor() {
        floorId = s.newObject(Pal.OUTLINE)
        planks(x0, y0, x1, y1, fl, if (lv < 2) 0.7f else 0.5f)
        if (lv < 2) onFlat(fl, x0, y0, x1, y1) { _, _, px, py -> if (Noise.rnd(px, py, 11) < 0.22f) Col.hex(0x7A5838) else 0 }
        // the stairwell: a dark opening, the top of the stairs going down in it
        onFlat(fl + 0.02f, sa, sc, sb, sd) { x, y, _, _ ->
            val step = floor((y - sc) / 0.45f).toInt()
            when {
                x - sa < 0.08f || sb - x < 0.08f -> Col.hex(0x3A2616)
                step >= 3 -> Col.hex(0x1A120C)
                (y - sc) - step * 0.45f < 0.08f -> Col.scale(Pal.WOOD_L, 1f - step * 0.22f)
                else -> Col.scale(Pal.WOOD_M, 1f - step * 0.25f)
            }
        }
        c.penId = 0
    }

    /** At night Nejc sleeps in his own bed ("cot"), Vida on a straw mattress rolled out under the slope past the cradle. */
    override val beds = listOf(MATTRESS, COT)
    override val childBeds = setOf(COT)

    override fun paint() {
        fit()
        vignette()
        lv = frame.world.hereLevel.coerceIn(1, 3)
        // the lamp: as a dialog has it, else lit from dusk, and put out for anyone asleep
        val lit = (fx("lamp")?.let { it > 0.5f } ?: (env.windows > 0.35f && !asleepHere)) && there("lamp")
        plot { x, y, px, py -> ground(x, y, px, py) }
        tufts(80, 13) { x, y ->
            (x < x1 + tk + 0.3f && y < y1 + tk + 0.3f) || (x in 1.3f..6.5f && y in 9.7f..11.6f) ||
                (x in 9.6f..11.6f && y in 2f..11.4f) || hypot(x - henX, y - henY) < 0.9f
        }
        prop { path() }
        for (k in 0..7) {
            val a = k / 7f
            shade(iso.sx(x1 + tk + 0.25f, y0 + a * (y1 - y0)), iso.sy(x1 + tk + 0.25f, y0 + a * (y1 - y0), 0f), 9f * K, 1.4f * K, 0.86f, groundId)
            shade(iso.sx(x0 + a * (x1 - x0), y1 + tk + 0.25f), iso.sy(x0 + a * (x1 - x0), y1 + tk + 0.25f, 0f), 9f * K, 1.4f * K, 0.86f, groundId)
        }

        floor()
        if (there("rug")) thing("rug") { rug(2.6f, 5.2f, 2.9f, 4.3f) } else rugId = floorId
        // the walls under the roof, the roof's underside, the ridge beam
        kneeWall()
        thing("roof") { roof() }
        gable()
        thing("window") { gableWindow() }
        prop { ridge() }
        if (there("wardrobe")) thing("wardrobe") { wardrobe() }
        if (there("mirror")) thing("mirror", slop = 1) { mirror() }

        // under the slope: the bed and the cradle
        thing("bed") { bed() }
        thing("pillow", slop = 1) { pillow() }
        thing("blanket") { quilt() }
        // Nejc asleep in his bed, the head on its pillow, under the patchwork quilt
        peopleAt(COT).firstOrNull()?.let { p ->
            val my = (bedC + bedD) / 2f
            sleeperAt(p, bedA + 0.43f, my, nbZ + 1.1f + HEAD_Z, NEJC_FEET, my, nbZ + 2f, PATCHWORK)
        }
        thing("cradle") { cradle(fxOn("cradle")) }
        // Vida on her straw mattress under the slope past the cradle, her head toward Nejc, under the blue wool blanket
        peopleAt(MATTRESS).firstOrNull()?.let { p ->
            prop { mattress(MAT_A, MAT_C, MAT_B, MAT_D, alongY = false) }
            sleeperOn(p, MAT_A, MAT_C, MAT_B, MAT_D, fl + MAT_H, alongY = false, BLUE_WOOL)
        }
        for (p in peopleAt("bed")) { footShadow(4.0f, 4.4f); personAt(p, 4.0f, 4.4f, fl) }
        // by the gable: the chest, the toy on it
        thing("chest") { paintedChest(5.9f, 7.25f, painted = lv >= 2) }
        if (there("toy")) thing("toy", slop = 1) { teddy(x0 + 0.5f, 6.55f, fl + 4.9f) }
        if (there("ball")) thing("ball", slop = 2) { ball(4.9f, 5.9f) }
        for (p in peopleAt("chest")) { footShadow(2.75f, 6.9f); personAt(p, 2.75f, 6.9f, fl, flip = true) }
        if (there("lamp")) thing("lamp", slop = 1) { hangingLamp(lit) }
        thing("stairs") { railing() }
        for (p in peopleAt("stairs")) { footShadow(7.35f, 5.6f); personAt(p, 7.35f, 5.6f, fl, flip = true) }

        stubs()
        prop { gardenBench() }
        if (lv >= 2) prop { pots() }
        prop { vegetables() }
        if (env.dark < 0.5f) prop { hen(henX, henY) }
        fireflies(iso.ix(0f, 12f), iso.iy(12f, 12f, 20f), iso.ix(12f, 0f), iso.iy(12f, 12f, 0f), 12, 5)

        if (lit) s.light(iso.sx(4.2f, ym), iso.sy(4.2f, ym, fl + 18f), 48f, 1f * max(env.windows, 0.6f))
    }

    // ------------------------------------------------------------------ the walls and the roof

    /** The knee wall under the eaves: plastered, its end cut at the front, the wall plate on its top. */
    private fun kneeWall() {
        part(Pal.OUTLINE)
        quadFill(x0, y0, fl, x1 + tk, y0, fl, x1 + tk, y0, kh, x0, y0, kh) { px, py ->
            inner(wallYx(px, y0), wallYz(px, py, y0), px, py, 1f, 0f)
        }
        quadFill(x1 + tk, y0 - tk, 0f, x1 + tk, y0, 0f, x1 + tk, y0, kh, x1 + tk, y0 - tk, kh) { px, py -> outer(wallXz(px, py, x1 + tk), px, py, 0.82f) }
        c.penId = 0
        prop { iso.box(x0, y0 - tk, kh, x1 + tk - x0, tk + 0.12f, 0.9f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D) }
    }

    /**
     * The roof's underside from the knee wall up to the ridge: rafters up the slope, and between them boards (from level 2)
     * or the battens with the red tiles showing between them (level 1), a chink of light here and there.
     */
    private fun roof() {
        val lined = lv >= 2
        quadFill(x0, y0, kh + 0.9f, x1 + tk, y0, kh + 0.9f, x1 + tk, ym, rh, x0, ym, rh) { px, py ->
            // the slope's point under this pixel: u = x − y from the screen's column, b = 2·(x + y) − z from its row; on the
            // slope z = kh + 0.9 + m·(y − y0), m = (rh − kh − 0.9) / (ym − y0), so b = 2u + 4y − z: solve for y, then x
            val u = (px + 0.5f - iso.ox) / (4f * K)
            val b = (py + 0.5f - iso.oy) / K
            val m = (rh - kh - 0.9f) / (ym - y0)
            val y = (b - 2f * u + kh + 0.9f - m * y0) / (4f - m)
            val x = u + y
            val rf = (x - x0) / 1.6f
            val rafter = rf - floor(rf) < 0.12f
            val along = (y - y0) / (ym - y0)
            when {
                rafter -> if (rf - floor(rf) < 0.04f) Pal.WOOD_L else Pal.WOOD_D
                lined -> {
                    val board = floor(along * 9f).toInt()
                    if (along * 9f - board < 0.08f) Col.hex(0x6A4428) else if (Noise.rnd(board, floor(rf).toInt(), 13) < 0.5f) Pal.WOOD_M else Col.mix(Pal.WOOD_M, Pal.WOOD_L, 0.3f)
                }
                else -> {
                    val batten = along * 12f - floor(along * 12f) < 0.2f
                    val chink = Noise.rnd(floor(rf * 5f).toInt(), floor(along * 12f).toInt(), 15) < 0.04f && !batten && env.dark < 0.5f
                    when {
                        batten -> Col.hex(0x8A5A34)
                        chink -> Col.hex(0xFFF0C8)
                        else -> if (Dither.at(px, py) < 0.3f) Col.hex(0x8A3A28) else Col.hex(0xA0442E)
                    }
                }
            }
        }
    }

    /** The gable wall on the left: its inner face up to the roof line, its end at the front, the verge rafter along its top. */
    private fun gable() {
        part(Pal.OUTLINE)
        c.polyBegin()
        c.polyAdd(iso.sx(x0, y0), iso.sy(x0, y0, fl)); c.polyAdd(iso.sx(x0, y1 + tk), iso.sy(x0, y1 + tk, fl))
        c.polyAdd(iso.sx(x0, y1 + tk), iso.sy(x0, y1 + tk, roofZ(y1 + tk))); c.polyAdd(iso.sx(x0, ym), iso.sy(x0, ym, rh))
        c.polyAdd(iso.sx(x0, y0), iso.sy(x0, y0, kh + 0.9f))
        c.polyFill { px, py -> inner(wallXy(px, x0), wallXz(px, py, x0), px, py, 0.9f, 0f) }
        quadFill(x0 - tk, y1 + tk, 0f, x0, y1 + tk, 0f, x0, y1 + tk, roofZ(y1 + tk), x0 - tk, y1 + tk, roofZ(y1 + tk)) { px, py ->
            outer(wallYz(px, py, y1 + tk), px, py, 1f)
        }
        c.penId = 0
        // the verge rafter along its top
        prop {
            for ((ya, yb) in listOf(y0 to ym, ym to y1 + tk)) {
                iso.quad(x0 + 0.02f, ya, roofZ(ya) - 1.2f, x0 + 0.02f, yb, roofZ(yb) - 1.2f, x0 + 0.02f, yb, roofZ(yb), x0 + 0.02f, ya, roofZ(ya), Pal.WOOD_M)
                iso.line(x0 + 0.02f, ya, roofZ(ya), x0 + 0.02f, yb, roofZ(yb), Pal.WOOD_L)
            }
        }
    }

    /** The ridge beam over the room's middle, from the gable to the cut at the front, and the collar under it. */
    private fun ridge() {
        iso.box(x0, ym - 0.22f, rh - 1.4f, x1 + tk - x0, 0.44f, 1.4f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        if (P > 1) iso.line(x0 + 0.2f, ym + 0.22f, rh - 0.7f, x1, ym + 0.22f, rh - 0.7f, Col.mix(Pal.WOOD_M, Pal.WOOD_D, 0.5f))
    }

    /** The small window in the gable: a deep plaster reveal, four panes and their cross, the view out. */
    private fun gableWindow() {
        val ya = 4.35f; val yb = 5.65f; val za = fl + 13.5f; val zb = fl + 19.5f
        onX(x0 + 0.02f, ya - 0.12f, yb + 0.12f, za - 0.5f, zb + 0.4f) { y, z, px, py ->
            val u = (y - ya) / (yb - ya); val v = (z - za) / (zb - za)
            if (u < 0f || u > 1f || v < 0f || v > 1f) { c.penEmissive = false; Pal.WOOD_D }
            else if (abs(u - 0.5f) < 0.06f || abs(v - 0.5f) < 0.07f || u < 0.07f || u > 0.93f || v < 0.08f || v > 0.92f) { c.penEmissive = false; Pal.WOOD_M }
            else { c.penEmissive = true; view(1f - u, v, px, py) }
        }
        c.penEmissive = false
        iso.box(x0, ya - 0.15f, za - 0.9f, 0.4f, yb - ya + 0.3f, 0.5f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
    }

    // ------------------------------------------------------------------ the bed and the cradle

    private val bedA = 2.2f; private val bedB = 5.45f; private val bedC = y0 + 0.15f; private val bedD = 2.5f; private val nbZ = fl + 2.6f

    /** Nejc's bed under the slope: a wooden frame, a headboard at its left end and a lower foot, the white sheet. */
    private fun bed() {
        for ((lx, ly) in listOf(bedB - 0.1f to bedD - 0.1f, bedA + 0.1f to bedD - 0.1f)) iso.post(lx, ly, fl, nbZ, Pal.WOOD_D)
        iso.box(bedA, bedC, nbZ - 1f, bedB - bedA, bedD - bedC, 1f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        iso.box(bedA - 0.14f, bedC, fl, 0.14f, bedD - bedC, 7.4f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        iso.box(bedB, bedC, fl, 0.14f, bedD - bedC, 5f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        // a heart cut in the headboard, a painted flower on the foot
        iso.px(bedA - 0.07f, (bedC + bedD) / 2f, fl + 6f, red)
        iso.box(bedA + 0.05f, bedC + 0.05f, nbZ, bedB - bedA - 0.1f, bedD - bedC - 0.1f, 1.1f, linen, Col.hex(0xE6DCC6), Col.hex(0xC4B89E))
    }

    /** The pillow at the head of the bed. */
    private fun pillow() = iso.box(bedA + 0.12f, bedC + 0.2f, nbZ + 1.1f, 0.62f, bedD - bedC - 0.4f, 1.1f, Col.hex(0xFFFFFF), Col.hex(0xECE6DA), Col.hex(0xCFC6B4))

    /** The blanket over the rest of the bed: a patchwork quilt of squares in every colour. */
    private fun quilt() {
        val xa = bedA + 0.85f; val xb = bedB - 0.05f; val z = nbZ + 1.5f
        val cols = intArrayOf(Col.hex(0xC8423A), Col.hex(0x3E6A9E), Col.hex(0xE0A040), Col.hex(0x5E8A4A), Col.hex(0xEADFC4), Col.hex(0x8A4A7A))
        fun patch(a: Float, b: Float, px: Int, py: Int, dim: Float): Int {
            val i = floor(a / 0.42f).toInt(); val j = floor(b / 0.42f).toInt()
            val seam = a / 0.42f - i < 0.08f || b / 0.42f - j < 0.08f
            val col = cols[Math.floorMod(Noise.hash(i, j, 17), cols.size)]
            return Col.scale(if (seam) Col.scale(col, 0.75f) else if (P > 1 && (px + py) % 4 == 0) Col.mix(col, Col.hex(0xFFFFFF), 0.15f) else col, dim)
        }
        onFlat(z, xa, bedC + 0.02f, xb, bedD + 0.08f) { x, y, px, py -> patch(x - xa, y - bedC, px, py, 1f) }
        onY(bedD + 0.08f, xa, xb, nbZ - 0.6f, z) { x, zz, px, py -> patch(x - xa, (z - zz) * 0.2f + 1.3f, px, py, 0.9f) }
        onX(xb, bedC, bedD + 0.08f, nbZ + 0.2f, z) { y, zz, px, py -> patch(1.8f + (z - zz) * 0.2f, y - bedC, px, py, 0.78f) }
    }

    /**
     * The old cradle at the bed's foot, where Nejc slept as a baby: a wooden box on two curved rockers, a little pillow and
     * a blanket in it; it rocks ([rock]: how far) when someone rocks it.
     */
    private fun cradle(rock: Float) {
        val xa = 5.95f; val xb = 7.15f; val ya = 1.35f; val yb = 2.3f; val z0 = fl + 1.2f; val z1 = fl + 4.6f
        val sway = sin(t * 3.2).toFloat() * rock * 1.6f
        val p = P.toFloat()
        // the rockers: two curved runners under its ends
        for (ry in floatArrayOf(ya + 0.12f, yb - 0.12f)) {
            val cx = iso.sx((xa + xb) / 2f, ry); val cy = iso.sy((xa + xb) / 2f, ry, fl + 0.5f)
            arc(cx, cy - 3f * p, 8f * p, 3.4f * p, Pal.WOOD_D, 0.35f, PI.toFloat() - 0.35f)
            arc(cx, cy - 3f * p - 1f, 8f * p, 3.4f * p, Pal.WOOD_M, 0.35f, PI.toFloat() - 0.35f)
        }
        val dz = sway
        iso.box(xa, ya, z0 + dz * 0.2f, xb - xa, yb - ya, z1 - z0, Col.hex(0xD8B07A), Col.hex(0xB88A54), Col.hex(0x8A6038))
        // painted hearts on its side, the bedding in it
        iso.px((xa + xb) / 2f, yb, z0 + 2f + dz * 0.2f, red)
        onFlat(z1 + dz * 0.2f - 0.5f, xa + 0.08f, ya + 0.08f, xb - 0.08f, yb - 0.08f) { x, _, _, _ -> if (x < xa + 0.4f) Col.hex(0xFFFFFF) else Col.hex(0x9AB8D8) }
        iso.box(xa - 0.1f, ya, z1 + dz * 0.2f, 0.14f, yb - ya, 1.6f, Col.hex(0xD8B07A), Col.hex(0xB88A54), Col.hex(0x8A6038))
    }

    // ------------------------------------------------------------------ by the gable

    /** The wardrobe against the gable (level 2): two painted doors with knobs, a cornice, standing under the roof line. */
    private fun wardrobe() {
        val ya = 2.95f; val yb = 4.2f; val h = fl + 13.5f; val xb = x0 + 1f
        onY(yb, x0, xb, fl, h) { _, z, _, _ -> if (z < fl + 0.6f) paintD else paintM }
        onX(xb, ya, yb, fl, h) { y, z, px, py ->
            val a = y - ya; val hh = z - fl; val w = yb - ya
            when {
                hh < 0.6f -> paintD
                a < 0.07f || w - a < 0.07f -> paintL
                abs(a - w / 2f) < 0.04f -> paintD
                abs(abs(a - w / 2f) - 0.12f) < 0.05f && abs(hh - 6.5f) < 0.4f -> brass
                (a % (w / 2f)) in 0.14f..(w / 2f - 0.14f) && hh in 1.6f..11f -> if (hh > 10.8f || (a % (w / 2f)) < 0.18f) paintL else if (Noise.rnd(px, py, 19) < 0.04f) paintD else Col.mix(paintM, paintL, 0.3f)
                else -> paintM
            }
        }
        iso.box(x0, ya - 0.06f, h, xb - x0 + 0.1f, yb - ya + 0.12f, 1f, paintL, paintM, paintD)
    }

    /** The mirror over the chest (level 3): an oval in a carved wooden frame, the room's light in it. */
    private fun mirror() {
        val yc = 6.55f; val zc = fl + 10.8f; val ryy = 0.42f; val rzz = 2.6f
        onX(x0 + 0.04f, yc - ryy - 0.1f, yc + ryy + 0.1f, zc - rzz - 0.6f, zc + rzz + 0.6f) { y, z, px, py ->
            val d = hypot((y - yc) / ryy, (z - zc) / rzz)
            when {
                d > 1.22f -> 0
                d > 1f -> if ((y - yc) + (z - zc) * 0.2f < 0f) Pal.WOOD_L else Pal.WOOD_M
                abs((y - yc) / ryy + (z - zc) / rzz * 0.6f + 0.3f) < 0.12f -> Col.hex(0xF4FAFF)
                else -> Col.mix(Col.hex(0x9AB8CC), Col.hex(0xC8DCE8), ((z - zc) / rzz + 1f) / 2f + (Dither.at(px, py) - 0.5f) * 0.1f)
            }
        }
    }

    /** Nejc's old teddy bear sitting on the chest (level 3): brown, round ears, a button nose. */
    private fun teddy(x: Float, y: Float, z: Float) {
        val cx = iso.sx(x, y); val by = iso.sy(x, y, z)
        val p = P.toFloat()
        val b = Col.hex(0xA87444); val bl = Col.hex(0xC8945E); val bd = Col.hex(0x7A5230)
        c.fillEllipse(cx, by - 2.6f * p, 2.4f * p, 2.6f * p, bd)
        c.fillEllipse(cx - 0.3f, by - 2.8f * p, 2.1f * p, 2.3f * p, b)
        c.fillEllipse(cx, by - 2.4f * p, 1.2f * p, 1.4f * p, bl)
        c.fillEllipse(cx - 1.6f * p, by - 0.6f * p, 1f * p, 0.7f * p, b); c.fillEllipse(cx + 1.6f * p, by - 0.6f * p, 1f * p, 0.7f * p, b)
        c.fillEllipse(cx, by - 6.2f * p, 1.9f * p, 1.7f * p, b)
        c.fillCircle(cx - 1.5f * p, by - 7.6f * p, 0.7f * p, bd); c.fillCircle(cx + 1.5f * p, by - 7.6f * p, 0.7f * p, bd)
        c.fillEllipse(cx, by - 5.6f * p, 0.9f * p, 0.6f * p, bl)
        c.set(floor(cx).toInt(), floor(by - 5.8f * p).toInt(), Pal.OUTLINE)
        c.set(floor(cx - 0.8f * p).toInt(), floor(by - 6.7f * p).toInt(), Pal.OUTLINE); c.set(floor(cx + 0.8f * p).toInt(), floor(by - 6.7f * p).toInt(), Pal.OUTLINE)
        c.fillRect(floor(cx - 1.2f * p).toInt(), floor(by - 4.6f * p).toInt(), floor(2.4f * p).toInt(), max(1, P), red)
    }

    /** Nejc's ball on the floor (level 2): red and white. */
    private fun ball(x: Float, y: Float) {
        val cx = iso.sx(x, y); val cy = iso.sy(x, y, fl)
        val p = P.toFloat()
        val r = 2.2f * p
        for (yy in floor(cy - 2f * r).toInt()..(cy).toInt()) for (xx in floor(cx - r).toInt()..(cx + r).toInt()) {
            val dx = (xx + 0.5f - cx) / r; val dy = (yy + 0.5f - (cy - r)) / r
            val d = dx * dx + dy * dy
            if (d > 1f) continue
            val band = abs(dy + dx * 0.3f) < 0.3f
            val lit = dx + dy < -0.6f
            c.set(xx, yy, if (band) (if (lit) Col.hex(0xFFFFFF) else Col.hex(0xE8E4DA)) else if (lit) Col.hex(0xE85A4A) else if (dx + dy > 0.7f) Col.hex(0x9A2A22) else red)
        }
    }

    /** The lamp hanging from the ridge beam on a chain (level 2): a tin shade over a glass, lit from dusk or when someone lights it. */
    private fun hangingLamp(lit: Boolean) {
        val lx = 4.2f
        val cx = iso.ix(lx, ym); val hook = iso.iy(lx, ym, rh - 1.4f); val top = iso.iy(lx, ym, fl + 19.5f)
        val p = P
        if (p > 1) chain(cx + p / 2, hook, top, ironL, ironD) else for (y in hook until top) c.set(cx, y, if ((y - hook) % 2 == 0) ironD else ironL)
        val pf = p.toFloat()
        c.polyBegin(); c.polyAdd(cx - 1f * pf, top.toFloat()); c.polyAdd(cx + 2f * pf, top.toFloat()); c.polyAdd(cx + 5f * pf, top + 3f * pf); c.polyAdd(cx - 4f * pf, top + 3f * pf)
        c.polyFill(Col.hex(0x3E6A5A))
        c.hline(cx - 4 * p, cx + 5 * p, top + 3 * p, Col.hex(0x2E4E42))
        if (lit) glow {
            c.fillRect(cx - p, top + 3 * p + 1, 3 * p, 3 * p, Pal.FLAME[1])
            c.fillRect(cx, top + 4 * p, p, p + 1, Pal.FLAME[0])
            c.hline(cx - 3 * p, cx + 4 * p, top + 3 * p + 1, Pal.WINDOW_LIT)
        } else c.fillRect(cx - p, top + 3 * p + 1, 3 * p, 3 * p, Col.hex(0xC8D8E4))
        if (lit) s.fx {
            c.penEmissive = true
            c.ditherCircle(cx + 0.5f, top + 5f * pf, 8f * K, Pal.FLAME[1], 0.14f * max(env.windows, 0.6f))
            c.penEmissive = false
        }
    }

    /** The rail round the stairwell's back and far sides: posts and a handrail. */
    private fun railing() {
        val rz = fl + 8f
        for ((px, py) in listOf(sa to sc, sb to sc, sa to sd)) iso.post(px, py, fl, rz, Pal.WOOD_D)
        rod(sa, sc, rz, sb, sc, rz, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        rod(sa, sc, rz, sa, sd, rz, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        rod(sa, sc, fl + 4f, sb, sc, fl + 4f, Pal.WOOD_D, Pal.WOOD_M, Pal.WOOD_D, Pal.WOOD_X)
        // the top of the stairs' stringer showing at the opening's front edge
        iso.box(sb - 0.2f, sd - 0.1f, fl - 0.6f, 0.2f, 0.12f, 0.8f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        // the stairwell's floor opening is part of the floor; the steps' nosings show under the rail
        for (k in 0 until 3) {
            val y = sc + 0.45f * k + 0.1f
            iso.line(sa + 0.12f, y, fl - 0.05f - k * 0.1f, sb - 0.12f, y, fl - 0.05f - k * 0.1f, Col.scale(Pal.WOOD_L, 1f - k * 0.2f))
        }
        c.set(iso.ix(sa, sc), iso.iy(sa, sc, rz), Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.3f))
    }

    private companion object {
        /** Where Nejc's feet are in his bed; Vida's straw mattress on the floor under the slope, past the cradle, along x. */
        const val NEJC_FEET = 4.5f
        const val MAT_A = 5.75f; const val MAT_B = 8.45f; const val MAT_C = 3.05f; const val MAT_D = 4.05f

        /** Nejc's patchwork quilt over him (its hem red), its squares the quilt's colours. */
        val PATCHWORK = intArrayOf(
            Col.hex(0xE06A5A), Col.hex(0xC8423A), Col.hex(0x8A2A24),
            Col.hex(0xC8423A), Col.hex(0x3E6A9E), Col.hex(0xE0A040), Col.hex(0x5E8A4A), Col.hex(0xEADFC4), Col.hex(0x8A4A7A),
        )

        /** Vida's wool blanket: the wardrobe's painted blue, a white check. */
        val BLUE_WOOL = intArrayOf(Col.hex(0x7C9EC4), Col.hex(0x5A7EA8), Col.hex(0x3E5C82), Col.hex(0xF0ECE0))
    }
}
